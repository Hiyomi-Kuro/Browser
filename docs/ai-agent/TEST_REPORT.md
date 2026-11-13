# 测试报告

测试时间：2026-11-10（Asia/Shanghai）
项目：`/storage/emulated/0/AndroidIDEProjects/browser`
构建：AGP 8.2.2 / Gradle 8.6 / JDK 17 / compileSdk 35 / minSdk 29

## 1. 构建结果

| 任务 | 结果 | 证据 |
| --- | --- | --- |
| `:app:compileDebugJavaWithJavac` | 通过 | `BUILD SUCCESSFUL` |
| `:app:assembleDebug` | 通过 | `BUILD SUCCESSFUL in 1m 25s` |
| `:app:testDebugUnitTest` | 通过 | 81 tests, 0 failures |
| APK 产物 | 存在 | `app/build/outputs/apk/debug/app-debug.apk`，17,491,962 字节 |

构建命令（与项目现有环境一致）：

```bash
JAVA_HOME=/data/data/com.tom.rv2ide/files/usr/lib/jvm/java-17-openjdk \
PATH=/data/data/com.tom.rv2ide/files/usr/lib/jvm/java-17-openjdk/bin:$PATH \
bash gradlew --no-daemon \
  -Pandroid.aapt2FromMavenOverride=/data/data/com.tom.rv2ide/files/home/.androidide/aapt2 \
  :app:assembleDebug :app:testDebugUnitTest
```

已有环境告警（AGP 8.2.2 对 compileSdk 35 的提示）为改动前既有，未做处理。

## 2. 单元测试（自动化，真实执行）

12 个测试类，81 个用例，全部通过。覆盖的是不依赖 Android 运行时、可完全本地判定的逻辑。

| 测试类 | 用例数 | 覆盖内容 |
| --- | --- | --- |
| `AiResponseParserTest` | 11 | OpenAI / Responses / Anthropic / Gemini 四种响应体、content parts、原生 tool_calls、错误信封、reasoning-only 空回复、模型列表三种形态、非法 JSON |
| `StreamAccumulatorTest` | 4 | SSE 文本增量合成、tool_calls 分片合并、keep-alive 忽略、Anthropic delta |
| `AgentProtocolTest` | 10 | JSON 动作协议、final_answer、done/summary、字符串式 action、代码块剥离、夹在散文中的 JSON、纯文本拒绝、空回复拒绝、字符串内大括号 |
| `ActionValidatorTest` | 9 | 未知工具、必填缺失、空字符串、类型错误、数字字符串宽容、枚举校验、工具自定义规则 |
| `AgentSafetyManagerTest` | 9 | 拒绝 javascript:/file:/content:、只读放行、ask_all 全确认、safe 放行滚动、submit/download/save_pdf 强制确认、Agent 标签页关闭、密码框输入确认 |
| `ContentBudgetTest` | 5 | 截断保留头尾、分段拼接、空输入、token 估算 |
| `StructuredTableTest` | 7 | Markdown 表格解析、缺失值标记、去重、CSV 转义、CSV/JSON 导出、转义竖线、无表格返回 null |
| `UrlToolsTest` | 5 | http/https 判定、危险协议、补全 scheme、去重键、同站点判定 |
| `PromptGuardTest` | 3 | 不可信内容包裹、注入文本识别（中英）、日志单行化 |
| `AiChatPromptTest` | 5 | 系统提示词规则、历史顺序与过滤、条数上限、长消息裁剪、各模式指令 |
| `StorageAndLimitsTest` | 5 | element_id 严格校验、文件名清洗、URL 取文件名、研究参数裁剪、参数 JSON 往返 |
| `SecretBlobTest` | 8 | 密钥密文打包：IV/密文往返、可变 IV 长度、过短/超长 IV、空载荷、空或非法 Base64、截断 blob、非法 IV 长度头 |

## 3. 真机验证（已执行的部分）

设备：Android 真机（经 adb `127.0.0.1:5555`）。

| 项目 | 方法 | 结果 |
| --- | --- | --- |
| APK 安装 | `cp` 到 `/data/local/tmp` 后 `pm install -r` | `Success`，覆盖安装成功 |
| 冷启动 | `am start -W BrowserActivity` | `Status: ok`，`TotalTime 2082ms`，`topResumedActivity` 为 BrowserActivity |
| 既有功能 | 截图检查 | 4 个既有标签页正常恢复（pubs.acs.org / id.elsevier.com / ClinicalTrials / Bing），底部工具栏、标签页浮层、图标均正常 |
| 崩溃检查 | `logcat -s AndroidRuntime:E` | 无 FATAL / 无 AndroidRuntime 异常 |
| 新增设置页 | `adb shell am start Settings_Ai` | 被系统拒绝（`SecurityException: not exported`），这是**测试入口限制**而非应用缺陷：该 Activity 无 intent-filter、不导出，只能从应用内“设置 → AI 设置”进入 |

按用户要求，AI 面板交互、设置页交互与模型真实调用交由用户自行验证。

## 4. 未验证 / 需要真机确认的项目

| 项目 | 原因 | 建议验证方式 |
| --- | --- | --- |
| 真实模型请求（401/403/429/超时） | 需要用户自己的 API Key | 设置 → AI 设置 → 测试 API 连通性 |
| 网页读取与总结/翻译/问答 | 需要有效 Key | 打开任意文章页 → AI 助手 → 总结 |
| Agent 端到端（搜索→点击→输入→翻页） | 需要 Key + 真实站点 | 输入“打开 Bing 搜索 Android WebView 文档并提取要点” |
| 高风险确认弹窗 | 需要 Agent 走到 submit/download | 让 Agent 填写并提交一个测试表单 |
| 深度研究与报告导出 | 需要 Key | 研究模式输入一个公开技术主题 |
| 下载落盘与 PDF 导出 | 需要系统确认 | Agent 下载一个公开 PDF；PDF 走系统打印对话框 |
| 中文/繁中 UI 文案 | 已加入资源，未逐条目视 | 切换系统语言查看 AI 面板与设置 |

## 5. 功能状态汇总

| 阶段 | 状态 | 说明 |
| --- | --- | --- |
| V1 配置与安全存储 | 已实现 + 已单测 | 14 项配置、Keystore 加密、模型列表、连通性测试、统计 |
| V1 网页阅读 | 已实现 | 内存内提取（复用 ArticleExtractor）、预算截断、总结/翻译/解释/提取 |
| V1 对话 | 已实现 | 流式渲染、停止、清空、复制、保存 |
| V2 Agent 引擎 | 已实现 | 28 工具、DOM 快照、确认策略、暂停/继续/终止、防打转、日志 |
| V2 安全策略 | 已实现 + 已单测 | 协议白名单、敏感输入屏蔽、注入防护 |
| V3 深度研究 | 已实现 | 研究提示词、来源记录、8 段报告、CSV/JSON/MD 导出、自动保存 |
| V3 任务管理 | 已实现 | 持久化、历史列表、查看/删除/恢复 |
| 真实模型回归 | 未执行 | 缺少 API Key，未伪造结果 |

## 6. 已知限制

1. 跨域 iframe、封闭 Shadow DOM、Canvas 内元素无法定位或操作，工具会明确失败。
2. 需要登录/验证码/付费墙的页面不处理（设计上禁止绕过）。
3. `browser.save_pdf` 复用系统打印对话框，需要用户手动确认。
4. 研究“深度”参数主要影响提示词与页数/步数预算，没有独立的分层爬取器。
5. 图片只提取 URL（Markdown 图片语法），不做视觉理解。
6. 首次 Gradle 冷启动（wrapper 下载）耗时约 25 分钟，之后增量构建约 1–2 分钟。

## 7. 修复记录（用户反馈）

用户反馈：设置里填写 API Key 后提示“加密失败”，无法保存。

### 根因

1. `Cipher.getInstance("AES/GCM/NoPadding")` 使用默认 provider，无法操作 AndroidKeyStore 中的密钥。
2. 加密时传入了调用方自造的 IV。AndroidKeyStore 密钥默认 `setRandomizedEncryptionRequired(true)`，框架要求 IV 由 Keystore 生成，因此 `Cipher.init` 抛 `InvalidAlgorithmParameterException: Caller-provided IV not permitted`，`ApiKeyStore.save()` 返回 false。

### 修复

| 改动 | 说明 |
| --- | --- |
| `Cipher.getInstance(TRANSFORMATION, "AndroidKeyStore")` | 显式使用 Keystore provider，失败才回退默认 provider |
| 加密不传 IV，改用 `cipher.getIV()` | 满足 randomized IV 要求，IV 由 Keystore 生成 |
| `SecretBlob` 新格式 `[IV 长度][IV][密文]` | 自描述，兼容不同 IV 长度；解密仍用 `GCMParameterSpec` |
| 保存失败时删除别名重试一次（256 → 128 位） | 处理别名中残留的不可用密钥 |
| 解密失败时清除不可用密文 | 界面不再出现“已保存”但实际无法使用的状态 |
| 保存失败弹窗显示真实异常链 | 便于定位设备侧问题（仅类名与消息，不含密钥） |
| 新增“检查密钥存储”自检 | 执行一次加解密往返并显示失败原因 |

### 验证

- 新增 `SecretBlobTest`：8 个用例，全部通过。
- 全量构建：`BUILD SUCCESSFUL in 1m 25s`，`:app:testDebugUnitTest` 81 tests / 0 failures。
- 真机保存与模型调用：按用户要求交由用户验证（本次未在设备上复测）。

## 8. 界面调整（2026-11-10，第二轮）

| 调整 | 实现 |
| --- | --- |
| AI 助手、保存为 Markdown 改为溢出菜单顶部图标行 | `dialog_menu_overflow.xml` 图标行由 RelativeLayout 改为等权 LinearLayout（7 个 0dp + weight=1 的按钮），`overflow_ai` / `overflow_markdown` 接入 `showOverflow()` |
| 从下拉列表移除重复入口 | 「其他」列表去掉 AI 项（3 项）、「保存」列表去掉 Markdown 项（5 项），点击索引同步调整 |
| 输入法遮挡 AI 输入框 | `BottomSheetDialog` 关闭了 decor fitting，`SOFT_INPUT_ADJUST_RESIZE` 无效；改为读取 `WindowInsetsCompat.Type.ime()` 作为面板底部内边距，窗口改为 `SOFT_INPUT_ADJUST_NOTHING` 避免重复位移；输入框获得焦点时自动滚动到最新消息 |
| 新增 Markdown 图标 | `drawable/icon_markdown.xml`（文档 + 下载箭头），与通用保存图标区分 |

验证：`BUILD SUCCESSFUL in 1m 19s`，`:app:testDebugUnitTest` 81 tests / 0 failures。
真机视觉与键盘行为按用户要求交由用户验证。

## 9. 闪退修复（2026-11-10，第三轮）

用户反馈：三个模式使用时会闪退。从设备 logcat / DropBox 取到三条真实堆栈，逐一修复。

### 9.1 证据

```
FATAL EXCEPTION: main
Process: com.kaori.browser
java.lang.IllegalStateException: Preference already has a SummaryProvider set.
    at com.kaori.browser.fragment.Fragment_AiSettings.lambda$showModelChooser$11
```

```
FATAL EXCEPTION: kaori-agent
java.lang.Throwable: A WebView method was called on thread 'kaori-agent'.
    at android.webkit.WebView.getUrl
    at com.kaori.browser.activity.BrowserActivity.currentUrl(BrowserActivity.java:2260)
    at com.kaori.browser.ai.agent.AgentController.finish(AgentController.java:408)
    at com.kaori.browser.ai.agent.AgentController.runLoop(AgentController.java:363)
```

```
W WebView: at com.kaori.browser.activity.BrowserActivity.currentUrl
           at com.kaori.browser.ai.agent.tools.ToolContext.currentUrl
           at com.kaori.browser.ai.agent.tools.NavigationTools$OpenUrl.execute
```

### 9.2 根因

| # | 位置 | 原因 | 影响 |
| --- | --- | --- | --- |
| 1 | `Fragment_AiSettings.showModelChooser` | 对声明了 `app:useSimpleSummaryProvider="true"` 的 `ai_model` 调用 `setSummary()`，AndroidX 直接抛 `IllegalStateException` | 主线程崩溃：AI 设置里“获取可用模型”选完即闪退 |
| 2 | `BrowserActivity.currentUrl()/currentTitle()/currentWebView()/listTabs()/canGoBack()/canGoForward()/tabCount()/currentTabIndex()/isAgentTab()` | 这些方法内部直接调用 `WebView.getUrl()` 等，却在 agent 线程被调用；`WebView.checkThread()` 抛异常 | 每个工具（`OpenUrl`、`Click`、`Type`…）与收尾的 `finish()` 都会崩 → Agent / 深度研究必闪退 |
| 3 | `AgentController` 线程体 + `finish()` | `runLoop` 的 catch 块再调用 `finish()`，而 `finish()` 又调用 `host.currentUrl()` → 二次抛出，逃出所有 catch → 后台线程未捕获异常直接杀进程 | 把可恢复的错误放大成进程级闪退 |

### 9.3 修复

1. **`BrowserHost` 全部读写主线程安全**：`currentUrl/currentTitle/currentWebView/listTabs/canGoBack/canGoForward/tabCount/currentTabIndex/isAgentTab` 在非主线程时用 `postAndWait` 跳主线程取值；`openUrl/goBack/goForward/reload/stopLoading/switchToTab/closeAgentTab` 统一经 `onMain()`。
2. **`postAndWait` / `onMain` 内部捕获 `RuntimeException`**：UI 线程上的动作失败只记日志，不再连带崩溃主线程。
3. **索引越界的竞态消除**：`switchToTab`/`closeAgentTab` 的边界检查移到主线程内执行。
4. **`AgentController` 收尾不再碰 WebView**：`finish()` 改用 `lastKnownUrl()`（来自最近一次观察），并把整个清理过程包在 `try/catch(Throwable)` 中。
5. **循环层不再读 WebView**：`urlAfter` 缺失时使用本次观察到的 URL。
6. **线程体最外层兜底**：`new Thread(...)` 里 `try { runLoop } catch (Throwable)`，任何意外只记为 FAILED，不再杀进程。
7. **设置页崩溃**：删除对 `ai_model` 的 `setSummary()` 调用，改为只写值（由 SummaryProvider 自动显示）。

### 9.4 验证

- `:app:assembleDebug` + `:app:testDebugUnitTest`：`BUILD SUCCESSFUL in 1m 19s`，81 tests / 0 failures。
- 静态审计：`ai` 包内所有 `host.currentUrl/currentTitle/currentWebView` 调用点均走线程安全桥；`evaluateJavascript` 仅出现在 `JsBridge`（postAndWait 内）、`PageContentExtractor`（文档标注必须主线程，实际调用方均为主线程）与面板 UI 回调中。
- 真机复测：按用户要求交由用户执行。

## 10. 界面与限制调整（2026-11-12，第四轮）

| 改动 | 实现 |
| --- | --- |
| 模式改名 对话 / 工作 / 研究 | `ai_mode_agent`、`ai_mode_research` 文案（3 套语言） |
| 对话模式六个按钮合并为「工具」 | `AiPanelController.rebuildChips()` + `showToolChooser()` + `runTool()` |
| 工具名称与提示词可编辑、可增删 | `ai/chat/ChatTool`、`ai/storage/ChatToolStore`、`Settings_AiTools`（新 Activity + 3 个布局） |
| 提示词占位符 | `{选中内容}`、`{语言}`（`ChatTool.resolvePrompt`） |
| 工作模式不限步数/时间 | `AgentSession` 支持 0 = 无上限；`AgentController` 跳过步数判断；面板传 0 |
| 研究模式不限结果/页数/深度/步数/时间 | `ResearchOptions` 改为 0 = 不限制；移除参数对话框；`ResearchEngine.start(topic)` |
| 报告语言默认简体中文、可改 | `AiSettings.reportLanguage()` + `ai_report_language` 设置项 |
| 两种模式都要求规划最省路径 | `AgentPlanner` 规则 9 重写 + `ResearchEngine.buildResearchPrompt` 前置要求 |
| 状态栏步数文案 | `ai_agent_status_running` 改为单步数占位符（无上限时显示“第 N 步”） |

验证：`:app:compileDebugJavaWithJavac` 通过；随后执行 `:app:assembleDebug :app:testDebugUnitTest`。
真机安装与体验测试按用户要求交由用户执行。

## 11. 网页读取改造（2026-11-12，第五轮）

用户要求：AI 读取网页应“完全加载后，用保存为 Markdown 的同一套流程读取全部内容”。

| 项目 | 改造前 | 改造后 |
| --- | --- | --- |
| 加载等待 | 无，立即抓 DOM | 轮询 `readyState`，连续两次 `complete` 才读（最多 15s，超时降级读取） |
| 转换实现 | `PageContentExtractor` 自己调 Flexmark | 与 Markdown 导出共用 `export/MarkdownConverter` |
| 内容长度 | 截断到 24000 字符（保留头尾） | 全文保留，安全上限 40 万字符 |
| 超长页面 | 中间内容被丢弃 | 超 6 万字符自动按 4 万字符分段精读，再综合 |
| Agent 侧读取等待 | 3s 起 | 20s，容纳页面加载时间 |

新增：`ai/reading_segments` 提示（“正在分 N 段精读…”）；`AiChatService.Listener.onProgress` 默认方法。

验证：`:app:compileDebugJavaWithJavac` 与 `:app:assembleDebug :app:testDebugUnitTest`。
真机安装与体验测试按用户要求交由用户执行。

## 12. 推理模型输出预算修复（2026-11-12，第六轮）

现象：`Model exhausted its output budget while reasoning ... (content=0 reasoning=12889 finish_reason=length)`，
模型把 `max_tokens` 全部消耗在 reasoning 上，没有产出可见回答。

| 改动 | 说明 |
| --- | --- |
| `AiException` 携带 `finish_reason` | 新增字段、`getFinishReason()`、`isLengthTruncated()`；解析器在空回复时填入 |
| 自动提高预算重试一次 | `OpenAiCompatibleClient.chat()`：EMPTY_RESPONSE + `length` 时，用 `max(8192, 配置值×2)`（上限 32768）重试一次；仍被截断则给出明确提示 |
| 默认最大输出 Token | 4096 → **8192**（`DEFAULT_MAX_TOKENS`、`preference_ai.xml` 默认值） |
| 单元测试 | `reportsReasoningOnlyLengthReply` 断言 `getFinishReason()=="length"` 与 `isLengthTruncated()` |

验证：`BUILD SUCCESSFUL in 50s`，`:app:testDebugUnitTest` 82 / 0。
真机安装与体验测试按用户要求交由用户执行。

## 13. 长回答分批续写（2026-11-12，第七轮）

用户反馈：不应让模型一次性输出到超上限，应该分批。已改为**自动续写，且不设轮数上限**。

| 项目 | 实现 |
| --- | --- |
| 触发条件 | 响应 `finish_reason` 为 `length`/`max_tokens` 且已经产出文本 |
| 续写方式 | 追加「助手已输出部分 + 继续指令」，再次请求，把新文本接在后面；面板同一条消息持续增长 |
| 轮数 | **不限制**：直到模型自然结束、用户终止，或某次续写没有新增文本（否则会用同样的请求无限重复） |
| 每次请求大小 | 保持设置里的 `max_tokens`（默认 8192），不再为了长答案拉大单次预算 |
| 分段精读的每段笔记 | 同样支持续写，不截断证据 |
| 进度提示 | 新增 `ai_continuing`（“回答较长 · 正在继续生成（第 N 段）…”） |
| 空回复（reasoning 吃光预算） | 仍保留一次升档重试（唯一的自动重试），成功后继续走续写 |

验证：`BUILD SUCCESSFUL in 1m 13s`，`:app:testDebugUnitTest` 82 / 0。
真机安装与体验测试按用户要求交由用户执行。

## 14. 长页面分段与流式空回复修复（2026-11-12，第八轮）

现场：读取 Nature 文章（158,529 字符）→ 翻译 → 报错
`Streaming response contained no content`。

### 三个根因

1. **分段步骤用错了任务**：分段精读时统一要求“提取事实、数字、引文”，对**翻译**任务而言，
   每段只返回摘要而不是译文，最终译文会变成转述。
2. **分段请求走了流式**：流式下若模型只产出 `reasoning` 而没有正文，`readStream` 只抛
   `Streaming response contained no content`，**没有携带 `finish_reason`**，因此上一轮的
   自动升档重试无法触发。
3. **空结果缺少明确报错**：`chatUntilComplete` 在无正文时直接返回空响应，界面只会显示“…” 。

### 修复

| 改动 | 说明 |
| --- | --- |
| 分段按真实任务处理 | 每段系统提示改为“只针对本段完成下面的任务：`<用户任务>`”，不再固定成“提取事实”；段结果按顺序拼接，不再做一次会改写译文的“综合” |
| 分段请求改为非流式 | 段落结果不逐字渲染，用 `stream = (listener != null)`；非流式走 `parseChat`，能拿到 `finish_reason` 并触发升档 |
| 流式空正文带诊断 | `emptyStreamError()` 输出 `finish_reason` 与 reasoning 字符数，并按 `finish_reason=length` 参与升档重试 |
| 空答案明确报错 | `chatUntilComplete` 在首轮无正文时抛 `EMPTY_RESPONSE`，界面显示具体原因而非空白气泡 |
| 段进度提示 | 新增 `ai_segment_done`（“已完成第 i/N 段…”） |

验证：`BUILD SUCCESSFUL in 1m 1s`，`:app:testDebugUnitTest` 82 / 0。
真机安装与体验测试按用户要求交由用户执行。

## 15. 超时限制移除与流式进度修复（2026-11-12，第九轮）

现场：总结（分段）成功，翻译同一篇长文报“请求超时 120 秒”。

| 项目 | 改动 |
| --- | --- |
| 读超时 | `ai_timeout_seconds` 支持 **0 = 不限制**（`HttpURLConnection.setReadTimeout(0)`），默认值 120 → **0**；标题改为“请求超时（秒，0 = 不限制）” |
| 存量配置迁移 | 已安装用户存的旧默认 `120` 一次性迁移为 `0`（`ai_timeout_migrated_v1`），用户主动改过的其它值不动 |
| 连接超时 | 仍保留 20 秒（仅建连，不影响长回答） |
| Agent 工具内部超时 | 超时为 0 时固定 30s（原先会退化成 8s，导致读页失败） |
| 分段结果改为流式 | 每段边生成边显示；面板用**固定索引**更新同一条回答，避免“段进度”消息覆盖回答气泡 |
| 中止 | 仍可随时“停止”：`CancelToken` 会断开连接 |

验证：`BUILD SUCCESSFUL in 1m 11s`，`:app:testDebugUnitTest` 82 / 0（随后因构建目录被清理，按同一份源码重新构建）。
真机安装与体验测试按用户要求交由用户执行。

## 16. 分段失败不再丢弃已完成内容（2026-11-12，第十轮）

现场：长文翻译时第 1 段已显示译文，准备翻第 2 段时出错，界面提示“模型返回为空”，
**之前翻译好的内容一起消失**。

### 根因

1. `segmentedRead` 中任何一段抛出 `AiException` 都会终止整个请求；`AiChatService` 进而回调
   `onError`。
2. 面板的 `onError` 用错误气泡**替换**了同一条回答消息 —— 而这条消息里已经流入了前面各段的译文。

### 修复

| 改动 | 说明 |
| --- | --- |
| 分段互相隔离 | 每段独立请求；失败时按 `isRetryable()` 重试一次，仍失败则**记录并继续下一段** |
| 保留已完成内容 | 只有“所有分段都无产出”才判定整体失败；否则返回已完成的译文 |
| 失败标注 | 结果末尾追加引用块“以下段落未能完成，结果可能不完整”并列出段号与原因 |
| 段大小 | 40000 → **20000** 字符/段，单段更快、更不容易耗尽推理预算，进度也更密 |
| 面板错误处理 | 若当前回答已有文本，则**保留文本**并另起一条错误消息；仅当回答为空时才替换 |
| 进度提示 | 新增 `ai_segment_failed`（“第 i/N 段失败 · 继续下一段…”） |

验证：`BUILD SUCCESSFUL in 1m 45s`，`:app:testDebugUnitTest` 82 / 0。
真机安装与体验测试按用户要求交由用户执行。
