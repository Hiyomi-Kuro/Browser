# AI Agent 架构说明

本文描述 Kaori Browser（`com.kaori.browser`）中 AI 系统的真实实现。所有类名、方法与行为均对应当前源码，不包含未实现的设想。

## 1. 总览

三层结构：

```
BrowserActivity (UI + 标签页编排)
        │  实现 BrowserHost（窄接口）
        ▼
ai/ui  AiPanelController          ← BottomSheet 面板：对话 / Agent / 深度研究
        │
        ├── ai/chat   AiChatService           V1 阅读问答（单轮请求）
        ├── ai/agent  AgentController         V2 自主操作（Observe→Plan→Act→Verify）
        └── ai/research ResearchEngine        V3 多网页研究（复用 AgentController）
                │
                ▼
        ai/agent/tools  ToolRegistry（28 个受控工具）
                │  JsBridge / PageObserver / PageLoadWaiter
                ▼
        NinjaWebView（当前标签页的真实 WebView）
```

`BrowserActivity` 只增加薄桥接方法，不承担任何 Agent 逻辑。

## 2. 包与职责

| 包 | 关键类 | 职责 |
| --- | --- | --- |
| `ai/config` | `AiSettings` | 配置读取、URL 归一化、上限裁剪 |
| `ai/security` | `ApiKeyStore` | Android Keystore AES/GCM 加密存储 API Key |
| `ai/api` | `AiClient`、`OpenAiCompatibleClient`、`AiResponseParser`、`StreamAccumulator`、`AiException`、`CancelToken`、`AiClientFactory` | 可替换的模型接入层 |
| `ai/model` | `AiMessage`、`ChatRequest`、`ChatResponse`、`ToolCall`、`SessionIds` | 与厂商无关的请求/响应模型 |
| `ai/reader` | `PageContentExtractor`、`PageSnapshot`、`ContentBudget` | 内存内网页读取与长度控制 |
| `ai/chat` | `AiChatSession`、`ChatMessage`、`AiChatPrompt`、`AiChatService`、`AiUsageStats` | 对话状态与阅读问答 |
| `ai/agent` | `AgentController`、`AgentSession`、`AgentStatus`、`BrowserHost`、`TabInfo`、`AgentMemory`、`AgentTaskLogger`、`AgentSafetyManager`、`ActionValidator`、`PromptGuard`、`UrlTools` | Agent 编排、安全与校验 |
| `ai/agent/observation` | `PageObserver`、`BrowserObservation`、`ElementInfo`、`FormInfo`、`LinkInfo`、`TableInfo` | DOM 观察与元素快照 |
| `ai/agent/planner` | `AgentPlanner`、`AgentDecision` | 提示词构建、原生工具调用 / JSON 协议解析 |
| `ai/agent/executor` | `AgentExecutor` | 校验 → 授权 → 执行 |
| `ai/agent/tools` | `ToolRegistry` 及 `NavigationTools`、`ObservationTools`、`InteractionTools`、`DataTools`、`FileTools`、`TabTools`、`ToolSchemas`、`JsBridge`、`PageReading`、`PageLoadWaiter`、`ElementScripts`、`ElementIds`、`ToolErrors` | 受控浏览器工具 |
| `ai/research` | `ResearchTask`、`ResearchOptions`、`ResearchEngine`、`ReportGenerator`、`StructuredTable`、`SourceRecord` | 深度研究与报告 |
| `ai/storage` | `AgentTaskStore`、`ExportWriter`、`FileNames` | 任务持久化与导出 |
| `ai/ui` | `AiPanelController`、`AiMessageAdapter` | AI 面板与消息渲染 |

大量改动没有塞进 `BrowserActivity`：该类只新增了 `BrowserHost` 的 18 个方法（约 200 行）与一个菜单入口。

## 3. V1：阅读问答流程

```
用户输入 / 点击 chip
   → (需要网页时) PageContentExtractor.extract(currentWebView)
        · ArticleExtractor 克隆完整 DOM（复用现有实现）
        · 一次 JS 取回 title/url/text/links/tables/meta；正文超 6 万字符时自动分段精读
        · 等待 document.readyState 连续两次 complete（最多 15s），确保页面加载完成
        · 与“保存为 Markdown”共用 MarkdownConverter 转 Markdown，全文保留（安全上限 40 万字符）
   → AiChatService.send()（后台线程）
        · AiChatPrompt.history 取最近 12 条 / 12000 字符
        · 页面块加到最后一条 user 消息前（不会重复拼接）
        · OpenAiCompatibleClient.chat()（SSE 或 JSON）
        · 增量文本回调到主线程渲染
   → 完成后写入 AiChatSession 并统计 token
```

- 只有在用户点击“读取网页/总结/翻译/解释/提取”或提问时才会发生读取。单纯打开页面不会上传任何内容。
- `PageSnapshot.toPromptBlock()` 把正文包在 `<untrusted_page_content>` 中，并在系统提示词里声明其为数据。

## 4. V2：Agent 循环

`AgentController.runLoop()` 在独立线程中执行：

```
while (true):
    检查终止条件（用户终止 / 步数上限 / 时间上限）
    awaitResume()                     ← 暂停时在此等待
    observation = PageObserver.observe()          · 主线程 evaluateJavascript + 等待
    memory.addObservation(observation)
    decision = AgentPlanner.decide()              · 网络请求（可取消、有界重试）
       ├─ final_answer → 结束（COMPLETED）
       ├─ protocol_error → 记为失败步骤，消耗 1 步（有限）
       └─ action(tool, arguments)
    observation.findElement(element_id) → 判定 sensitiveTarget
    AgentExecutor.prepare()                       · ActionValidator + AgentSafetyManager
       ├─ INVALID  → 记录失败，下一轮带上错误说明
       ├─ DENIED   → 记录拒绝
       ├─ REQUIRES_CONFIRMATION → 主线程弹窗，Agent 线程阻塞等待（120s 超时视为拒绝）
       └─ READY    → 执行
    result = tool.execute()                       · 工具内部通过 host.postAndWait 访问 WebView
    memory.addStep + logger.log + UI 回调
    if result.pageChanged → PageLoadWaiter.awaitSettle()
    重复相同失败动作 3 次 → 直接以 FAILED 结束
```

关键点：

1. **线程模型**：Agent 线程从不直接触碰 WebView；`JsBridge.evaluate()` 与 `BrowserHost.postAndWait()` 把工作投递到主线程并阻塞等待结果（带超时）。主线程始终可响应。
2. **终止保证**：`maxSteps`（设置项，默认 20）、时间上限、`stopped` 标志、模型调用重试上限 2 次、重复失败 3 次，四重约束，不存在无限循环。
3. **可取消**：`CancelToken` 持有 HttpURLConnection 的 abort hook，`stop()` 会立即断开正在读取的 socket。
4. **可暂停**：暂停在循环边界生效（当前工具执行完后立即停下），确认弹窗期间暂停也会保持。

## 5. DOM 观察与元素定位

`PageObserver` 注入一段固定的观察脚本：

- 候选选择器：`a[href],button,input,select,textarea,[role=*],[contenteditable],[onclick],[tabindex]`
- 过滤不可见元素（`getBoundingClientRect`、`visibility/display/opacity`）
- 每个元素写入 `data-kaori-ai-id="eN"`，并记录一个 4 级 `tag:nth-of-type(...)` CSS 路径
- 生成号 `window.__kaoriAiSnapshot` 每快照 +1

工具执行时：

```
el = document.querySelector('[data-kaori-ai-id="e12"]')   // 首选
  || document.querySelector('<快照记录的 CSS 路径>')        // DOM 重渲染后的兜底
```

找不到就返回 `element_not_found`，绝不会假装成功。`element_id` 只在对应快照有效。

## 6. V3：深度研究

```
ResearchEngine.start(topic, options)
  → 创建 AgentSession(research=true, maxSteps, deadline)
  → AgentController 使用研究提示词执行
      搜索 → 筛选 → 逐页打开 → extract_markdown（记录来源）→ 比较 → final_answer
  → ReportGenerator.build(task, session, answer)
       1 研究任务 / 2 实际打开的 URL / 3 真实读取的来源
       4 模型综合（标注为模型综合）/ 5 结构化对比表
       6 证据 / 7 局限性（步数、失败数、不可读页面）/ 8 未完成步骤
  → AgentTaskStore.save() 持久化，面板显示报告并自动保存 Markdown
```

报告中的来源只来自 `browser.extract_markdown`、`browser.extract_table`、`browser.download` 真实执行过的记录（`AgentMemory.recordSource`），不会凭空生成 DOI、标题或日期。

## 7. 多标签页

- `browser.new_tab` 调用 `BrowserHost.openUrl(url, true)`，`BrowserActivity` 把新 `NinjaWebView` 记入 `agentTabs`（IdentityHashMap 支持的身份集合）。
- `browser.close_tab` 只能关闭 `agentTabs` 中的标签页；关闭用户标签页需要确认（`AgentSafetyManager` 判为 CONFIRM）。
- 每次观察都针对 `currentAlbumController` 对应的 WebView，切换标签页后重新观察，不会混淆不同标签页的 DOM。
- 任务结束时 `AgentController.finish()` 调用 `closeAllAgentTabs()` 清理 Agent 自己创建的临时标签页。

## 8. 持久化

- `AgentTaskStore`：`filesDir/ai_tasks/<sessionId>.json`，字段包含 kind、task、status、steps、summary、error、report、log、sources、options。
- 最多保留 60 条，超出按创建时间淘汰。
- 不保存任何 API Key、Cookie、密码或令牌。
- 会话内聊天记录保存在内存（`AiChatSession`），随 Activity 生命周期存在；切换标签页/关闭面板不会丢失。

## 9. 与现有功能的复用关系

| 现有实现 | 复用方式 |
| --- | --- |
| `reader/ArticleExtractor` | 扩展为 `extractDocument()`，同时返回 title/url；原 `extract()` 签名保留 |
| `export/MarkdownExporter` | 保持原样（用户手动导出 Markdown）；AI 侧改为内存快照，不写临时文件 |
| `export/PdfExporter` | `browser.save_pdf` 直接调用 |
| `session/SessionManager` | 未改动；Agent 标签页与命名会话互不影响 |
| `view/NinjaWebView` | 未改动；所有操作通过其 `evaluateJavascript`/`loadUrl` |
| 下载机制 | `browser.download` 使用系统 `DownloadManager`，与浏览器内下载一致的 UA/Cookie/Referer |
| 设置体系 | 新增 `preference_ai.xml` + `Fragment_AiSettings`，沿用 `PreferenceFragmentCompat` 模式 |

## 10. 资源上限

| 项目 | 默认值 | 位置 |
| --- | --- | --- |
| 单次网页正文预算 | 24000 字符 | `ContentBudget.DEFAULT_MAX_CHARS` |
| 快照元素数 | 220 | `PageObserver.MAX_ELEMENTS` |
| 提示词观察文本 | 12000 字符 | `AgentController` / `AgentPlanner` |
| 对话历史 | 12 条 / 12000 字符 | `AiChatPrompt` |
| Agent 步数 | 20 | 设置项 |
| 研究步数 | 24 | `ResearchOptions` |
| 研究页数 | 6 | 设置项 |
| 请求超时 | 0 = 不限制 | 设置项 |
| 最大输出 Token | 8192（推理模型可再加） | 设置项；被截断时自动升档重试一次 |
