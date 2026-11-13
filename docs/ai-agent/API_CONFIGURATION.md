# AI API 配置说明

## 1. 入口

设置 → **AI 设置**（`Settings_Ai` → `Fragment_AiSettings`，配置项定义在 `res/xml/preference_ai.xml`）。

## 2. 配置项

| 设置项 | key | 默认值 | 说明 |
| --- | --- | --- | --- |
| 启用 AI | `ai_enabled` | false | 关闭时浏览器菜单入口会引导到本页 |
| 服务商 | `ai_provider` | `opencode_go` | OpenCode Go 预设 / 自定义 OpenAI 兼容 |
| API Base URL | `ai_base_url` | `https://opencode.ai/inference/go/openai/v1` | 自动补 `/chat/completions` |
| API Key | Keystore | 空 | 不进入 SharedPreferences 明文 |
| 默认模型 | `ai_model` | `deepseek-v4.1-flash` | 可手填或从列表选择 |
| 请求超时 | `ai_timeout_seconds` | 0 | 0 = 不限制；>0 时为秒数（0–3600） |
| 最大输出 Token | `ai_max_tokens` | 8192 | 128–65536；推理模型耗尽预算时客户端会自动升档重试一次 |
| Temperature | `ai_temperature` | 0.7 | 0–2 |
| 流式输出 | `ai_stream` | true | SSE |
| 原生工具调用 | `ai_native_tools` | true | 不支持时自动回退 JSON 协议 |
| Agent 最大步数 | `ai_max_steps` | 20 | 1–60 |
| 自动操作权限 | `ai_permission_mode` | `safe` | `safe` / `ask_all` / `auto` |

“获取可用模型”与“测试 API 连通性”在后台线程执行，不阻塞界面。

## 3. OpenCode Go 预设

依据官方文档 <https://opencode.ai/docs/console/go/>：

- Chat 端点：`https://opencode.ai/inference/go/openai/v1/chat/completions`
- 模型列表：`https://opencode.ai/inference/go/v1/models`（与 chat 路径不同，因此单独常量 `AiSettings.OPENCODE_MODELS_URL`）
- 请求头要求：
  - `Authorization: Bearer <key>`
  - `Content-Type: application/json`
  - 客户端自报 `User-Agent`：`KaoriBrowser-AI/1.0 (Android; browser-agent)`
  - **稳定会话**：`x-opencode-session: <sessionId>`
- 每个会话（`AiChatSession` / `AgentSession`）在创建时生成一次 `sessionId`（`kaori-` + 24 位十六进制），发送消息不会重建；只有“清空会话”或新任务才产生新 id。
- 文档列出的模型 ID 形如 `glm-5.3-flash`、`kimi-k3`、`deepseek-v4.1-flash`、`mimo-v2.5`、`longcat-2.0`、`step-5-preview` 等，实际可用列表以 `/models` 返回为准。

### 服务政策

OpenCode Go 官方说明其面向编程 Agent 场景，并会监控滥用流量（traffic is monitored for abuse）。本应用**不绕过**任何限制：

- 不伪造客户端身份（明确声明自己的 User-Agent）。
- 不规避区域、额度或模型限制。
- 若家庭订阅条款不允许普通网页阅读/自动化用途，请在“服务商”中选择“自定义 OpenAI 兼容接口”，填入允许该用途的服务。

该提示也显示在 AI 设置页底部（`ai_opencode_notice`）。

## 4. 自定义 OpenAI 兼容接口

Base URL 填写到 `/v1` 级别即可，例如：

- `https://api.openai.com/v1`
- `https://openrouter.ai/api/v1`
- `http://127.0.0.1:11434/v1`（本地服务，需允许明文 HTTP —— 应用已开启 `usesCleartextTraffic`）

模型列表地址由 `AiSettings.modelsUrlFor()` 推导：去掉 `/chat/completions` 后加 `/models`。

## 5. 请求体

```json
{
  "model": "…",
  "stream": true,
  "stream_options": { "include_usage": true },
  "messages": [ { "role": "system", "content": "…" }, { "role": "user", "content": "…" } ],
  "max_tokens": 4096,
  "temperature": 0.7,
  "tools": [ { "type": "function", "function": { "name": "browser.click", "parameters": { … } } } ]
}
```

- `temperature` 可被省略：若服务端以 400 拒绝 temperature，客户端**自动重试一次**该请求（去掉 temperature）。
- Agent 规划请求固定使用 temperature=0.2 且 `stream=false`（保证可解析）。
- 原生 `tools` 若被拒绝（400 且错误信息含 tool/function），规划器自动切换到 JSON 协议模式并继续。

## 6. 响应兼容性

`AiResponseParser` 同时处理以下形态（见单元测试 `AiResponseParserTest`）：

| 形态 | 取值位置 |
| --- | --- |
| OpenAI Chat Completions | `choices[0].message.content`（字符串或 content 数组） |
| OpenAI Responses | `output_text` 或 `output[].content[].text` |
| Anthropic Messages | `content[].text`、`stop_reason`、`usage.input_tokens/output_tokens` |
| Gemini | `candidates[0].content.parts[].text` |
| 流式 SSE | `data:` 帧中的 `delta`，含 tool_calls 分片合并 |

错误处理：

- HTTP 401/403/429/5xx 映射到 `AiException.Kind`，并保留服务商 `error.message` 原文。
- HTTP 200 但 `content` 为空时不会假装成功：抛出 `EMPTY_RESPONSE`，并附带结构性提示（`content=`、`reasoning=`、`finish_reason=`、`tool_calls=`）。若 `finish_reason=length` 且只有 reasoning，提示“输出预算被推理耗尽”。

## 7. 错误提示对照

| Kind | UI 文案 |
| --- | --- |
| `AUTHENTICATION` | API Key 被拒绝 |
| `FORBIDDEN` | 服务商拒绝该请求 |
| `RATE_LIMIT` | 请求过于频繁 |
| `TIMEOUT` | 请求超时 |
| `NETWORK` | 网络异常 |
| `PARSE` | 无法解析的响应 |
| `EMPTY_RESPONSE` | 模型返回空回复 |
| `NO_API_KEY` | 请填写 API Key |
| `NOT_CONFIGURED` | 请选择模型 / 未启用 |

## 8. API Key 存储

- `ApiKeyStore` 在 `AndroidKeyStore` 中维护 AES-256/GCM 密钥（alias `kaori_browser_ai_api_key_v1`）。加密时由 Keystore 生成 IV 并通过 `Cipher.getIV()` 读取，与密文一起打包为 `[IV 长度][IV][密文]` 后 Base64 写入独立文件 `ai_secrets`；解密时把该 IV 交给 `GCMParameterSpec`。
- 密钥不可导出；解密失败时返回 null 并清除显示。
- 设置页输入框的 `OnPreferenceChangeListener` 始终返回 `false`，明文不会被 `EditTextPreference` 持久化。
- API Key 不进入日志、不进入 Agent 提示词、不注入网页 JavaScript。

### 8.1 加密实现要点（2026-11-10 修正）

早期实现有两个问题，会导致“API Key 加密失败”：

1. `Cipher.getInstance("AES/GCM/NoPadding")` 取的是默认 provider，它无法使用 AndroidKeyStore 的密钥；
2. 加密时传入了调用方自造的 IV。AndroidKeyStore 密钥默认 `setRandomizedEncryptionRequired(true)`，此时框架要求 IV 由 Keystore 生成，否则 `Cipher.init` 抛 `InvalidAlgorithmParameterException: Caller-provided IV not permitted`。

现在的实现：

- `Cipher.getInstance("AES/GCM/NoPadding", "AndroidKeyStore")`，显式指定 Keystore provider；
- 加密调用 `cipher.init(ENCRYPT_MODE, key)`（不传 IV），随后用 `cipher.getIV()` 取出 Keystore 生成的 IV；
- 解密仍使用 `GCMParameterSpec(128, iv)`（解密允许调用方提供 IV）；
- 保存失败时删除旧别名并重试一次（先 256 位，再退回 128 位）；
- 解密失败时清除不可用的密文，避免界面显示“已保存”但实际不可用；
- 设置页新增“检查密钥存储”，执行一次加解密往返并直接显示失败原因。
