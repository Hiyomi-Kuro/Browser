# 功能清单（按实际实现状态）

标记说明：**已实现** = 代码完成且可编译；**已测试** 指有单元测试或真实构建验证；真机体验仍需用户确认的部分单独标注。

## V1 AI 网页阅读

| 功能 | 状态 | 实现位置 |
| --- | --- | --- |
| AI 设置页面（14 项配置） | 已实现 | `res/xml/preference_ai.xml`、`Fragment_AiSettings` |
| 启用/关闭 AI | 已实现 | `ai_enabled`，关闭时入口引导到设置 |
| 服务商选择 / OpenCode Go 预设 | 已实现 | `ListPreference` + `AiSettings` 常量 |
| 自定义 OpenAI 兼容 Base URL | 已实现 | `ai_base_url` + URL 归一化（已测试） |
| API Key 加密存储 | 已实现 | `ApiKeyStore`（Keystore AES/GCM） |
| 密钥存储自检（加解密往返） | 已实现 | 设置 → AI 设置 → “检查密钥存储”，失败时显示真实原因 |
| 获取可用模型 | 已实现 | `listModels()` → 选择对话框 |
| 手填模型 ID | 已实现 | `ai_model` EditTextPreference |
| API 连通性测试 | 已实现 | `testConnection()`，区分 401/403/429/超时/网络 |
| 请求超时 / 最大输出 Token | 已实现 | 设置项 + 客户端裁剪 |
| 调用统计 | 已实现 | `AiUsageStats`（请求数、输入/输出 token） |
| Agent 最大步数 / 自动操作权限 | 已实现 | `ai_max_steps` / `ai_permission_mode` |
| 流式输出 | 已实现 | SSE 增量渲染 |
| 请求取消 | 已实现 | `CancelToken` + `disconnect()` |
| AI 面板入口 | 已实现 | 溢出菜单图标行第 1 个图标 |
| 对话列表 / 输入 / 停止 | 已实现 | `AiPanelController` |
| 读取当前网页（内存内） | 已实现 | `PageContentExtractor` + `ArticleExtractor` |
| 简洁总结 / 详细总结 | 已实现 | chip + `AiChatPrompt.summaryInstruction` |
| 翻译（中英对照术语表） | 已实现 | chip + `translateInstruction` |
| 解释选中文本 | 已实现 | `window.getSelection()` → `explainInstruction` |
| 信息提取（表格/链接/元数据） | 已实现 | chip + `extractInstruction` |
| 清空会话 / 复制 / 保存 | 已实现 | 面板 chip |
| 切换网页不丢会话 | 已实现 | 会话由 Activity 持有，面板复用 |

## V2 浏览器自主操作

| 功能 | 状态 | 实现位置 |
| --- | --- | --- |
| 工作模式 UI（任务/步骤/工具/结果/错误） | 已实现 | `AiPanelController` + `AiMessageAdapter` |
| 开始 / 暂停 / 继续 / 终止 | 已实现 | `AgentController` |
| 真实执行日志 | 已实现 | `AgentTaskLogger` 与实时 STEP/TOOL 消息 |
| Observe→Plan→Act→Verify 循环 | 已实现 | `AgentController.runLoop` |
| 28 个浏览器工具 | 已实现 | `ai/agent/tools/*` |
| 结构化 Schema 校验 | 已实现 | `ActionValidator`（已测试） |
| DOM 快照 + 稳定元素定位 | 已实现 | `PageObserver`（`data-kaori-ai-id` + CSS 路径兜底） |
| 点击/输入/清空/滚动/下拉/等待/提交 | 已实现 | `InteractionTools` |
| 表格/Markdown/元数据/查找 | 已实现 | `DataTools` |
| 下载 / 保存 Markdown / PDF | 已实现 | `FileTools`（下载与 PDF 需确认） |
| 标签页 新建/切换/列表/关闭 | 已实现 | `TabTools` + `BrowserActivity` 的 `agentTabs` |
| 高风险操作确认 | 已实现 | `AgentSafetyManager`（已测试） |
| 拒绝 `javascript:`/`file:`/`content:` | 已实现 | `UrlTools`（已测试） |
| 有限重试与防打转 | 已实现 | 重试 2 次 / 重复失败 3 次终止 |
| Prompt Injection 防护 | 已实现 | `PromptGuard` + 系统提示词（已测试） |

## V3 深度研究

| 功能 | 状态 | 实现位置 |
| --- | --- | --- |
| 研究模式入口 | 已实现 | 面板第三个 Tab |
| 研究参数（结果数/页数/深度/超时/语言） | 已实现 | `dialog_research_options.xml` + `ResearchOptions` |
| 搜索 → 筛选 → 逐页读取 → 比较 | 已实现 | 研究提示词 + Agent 工具循环 |
| 来源记录（URL/标题/摘录/状态） | 已实现 | `AgentMemory.recordSource` + `SourceRecord` |
| 去重 | 已实现 | `UrlTools.normalizeForDedupe`（已测试） |
| 报告 8 个固定章节 | 已实现 | `ReportGenerator` |
| 结构化对比表 | 已实现 | `StructuredTable.parseMarkdownTable`（已测试） |
| CSV / JSON / Markdown 导出 | 已实现 | `StructuredTable` + `ExportWriter` |
| 报告自动保存 | 已实现 | 完成时写入 `Download/Browser/AI research <时间>.md` |
| 多标签页研究 + 自动清理 | 已实现 | `agentTabs` + `closeAllAgentTabs` |
| 任务历史（查看/删除/恢复） | 已实现 | `AgentTaskStore` + 面板“任务” chip |
| 任务持久化（不含密钥） | 已实现 | `filesDir/ai_tasks/*.json` |
| 未完成页面如实标记 | 已实现 | 报告“局限性/未完成步骤”章节 |

## 明确未实现 / 部分实现

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 真实模型端到端回归 | 未验证 | 需要用户自己的 API Key；本次只做到编译 + 单元测试 + 真机安装启动 |
| 跨域 iframe / Shadow DOM / Canvas 操作 | 不支持 | 如实返回 `element_not_found` 或标记受限 |
| 需要登录/验证码/付费墙的页面 | 不支持 | 设计上禁止绕过 |
| 原生工具调用兼容性 | 尽力兼容 | 服务端拒绝 `tools` 时自动回退 JSON 协议 |
| PDF 静默导出 | 不支持 | 复用系统打印对话框，需用户确认 |
| 研究“最大研究深度”对流程的影响 | 部分 | 参数会传入提示词与任务描述，但深度主要表现为访问页数与步数上限，没有独立的分层爬取器 |
| 图片理解 | 不支持 | 只提取图片 URL，不上传图片 |
