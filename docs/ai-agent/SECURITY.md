# 安全模型

## 1. 凭据

- API Key 只通过 `ApiKeyStore` 保存：Android Keystore（AES-256/GCM，alias `kaori_browser_ai_api_key_v1`），随机 IV，密文写入独立 preferences 文件 `ai_secrets`。
- 明文 Key 不会写入 `SharedPreferences`、源码、日志、任务记录或导出文件。
- 设置页输入框的 `onPreferenceChange` 返回 `false`，阻止 `EditTextPreference` 自动持久化明文。
- Key 只出现在 `Authorization: Bearer` 请求头中，不会进入提示词，也不会注入网页 JavaScript。
- `ApiKeyStore.maskedKey()` 只返回 `abcd********wxyz` 形式用于展示；当前实现的面板不展示 Key。

## 2. 网络

- 所有请求在 `AiExecutors` 的后台线程执行，UI 线程不阻塞。
- 只允许 `http`/`https`（`AiSettings.normalizeChatUrl` + `AiClientFactory`），非法 scheme 直接拒绝。
- 连接超时 20s，读取超时取设置值（默认 120s），响应体上限 8MB。
- 取消会 `disconnect()` 当前连接，不会留下僵尸请求。
- 不使用 WebView 发起模型请求，因此模型流量与网页 Cookie/凭据隔离。

## 3. 页面内容与提示注入

- 网页正文、DOM 摘要、链接、表格一律包在 `<untrusted_page_content>` 中。
- 系统提示词明确：该区域是数据，不能改变角色、工具集合、权限，不能要求泄露密钥。
- `PromptGuard.looksLikeInstruction()` 对可疑文本打标（仅记录，不替代上面更强的隔离），日志中只保存单行截断。
- 页面内容永远不能修改工具注册表、权限模式、API Key 或本地数据。
- Agent 只消费结构化工具结果，不执行模型输出的 JS/Shell/Intent。

## 4. 工具层

- 封闭工具集（28 个），未注册名称直接拒绝。
- 参数按 JSON Schema 校验（类型、必填、枚举、tool 自定义规则）。
- 只读工具不产生副作用，其余按风险分级。
- 高风险动作强制用户确认（submit/download/save_pdf/关闭用户标签页/向敏感输入框输入）。
- 关闭标签页只允许操作 Agent 自己创建的标签页（`agentTabs` 身份集合）。
- 拒绝绕过登录、验证码、付费墙、访问控制：提示词硬性禁止，工具也没有相应能力。

## 5. 敏感数据

- 观察脚本对 `password`、`hidden`、`email`、`tel`、`date`、`file` 等输入框**不返回 value**，只返回 `sensitive:true` 与 `has_value` 布尔值。
- 不读取 Cookie、LocalStorage、Token 或自动填充数据；`browser.download` 仅在系统下载请求中附带当前站点的 Cookie 头（与浏览器自身下载行为一致）。
- 任务持久化（`ai_tasks/*.json`）只包含任务文本、状态、日志、来源 URL、报告，不含任何凭据。
- 导出的 Markdown/CSV/JSON 只包含页面内容与来源，不含 API Key。

## 6. Android 权限

应用未新增任何权限。AI 功能使用的都是既有权限：

- `INTERNET` / `ACCESS_NETWORK_STATE`：模型请求与下载
- 既有存储权限：`Download/Browser` 导出（MediaStore，scoped storage 兼容）
- 不请求 Root、Shizuku、无障碍或设备管理器权限。

## 7. 失败与滥用防护

- 模型调用超时/网络错误最多重试 2 次，之后以 FAILED 结束并给出原因。
- 同一失败动作连续 3 次 → 立即终止，防止打转。
- 步数上限（默认 20）与时间上限（默认 max(5, steps) 分钟）双重终止。
- 无法观察页面连续 3 次 → 终止。
- 协议解析失败按步计费，受同一个步数上限约束。

## 8. 已知边界

- 无法操作跨域 iframe 内部、封闭 Shadow DOM、Canvas/WebGL 内容；这些情况如实返回失败。
- 无法读取需要登录/付费墙/验证码的页面内容；Agent 会说明而不是伪造。
- 系统下载/打印最终由用户确认，应用不会静默落盘。
- 提示注入只能降低风险、不能彻底消除：模型仍可能被页面文本误导，因此所有实际动作都经过本地工具与权限校验。
