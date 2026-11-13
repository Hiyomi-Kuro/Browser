# Agent 工具参考

工具定义在 `ai/agent/tools/`，由 `ToolRegistry` 统一注册，共 28 个。工具名是模型唯一可用的动作集合，注册表之外的任何名称都会被 `ActionValidator` 拒绝。

## 1. 输出协议

优先使用模型原生工具调用；若服务端拒绝 `tools` 字段，`AgentPlanner` 自动回退为严格 JSON 文本协议：

```json
{"thought_summary":"需要进入下一页","action":{"tool":"browser.click","arguments":{"element_id":"e12"}}}
```

任务结束时：

```json
{"final_answer":"……"}
```

解析规则（`AgentPlanner.parse`，已覆盖单元测试）：

- 允许包裹在 ```json 代码块中，或夹在自然语言里（取第一个配对的大括号）。
- 支持 `{"action":"browser.back","arguments":{}}` 简写。
- 支持 `{"done":true,"summary":"…"}`。
- 无 JSON、或既无 `final_answer` 也无 `action.tool` → 记为 `protocol_error`，消耗 1 步并要求模型修正。

`thought_summary` 仅用于 UI 展示和日志，不要求模型输出隐藏推理。

## 2. 工具清单

参数列中 `*` 为必填。风险等级由 `AgentSafetyManager` 使用。

### 导航

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `browser.open_url` | `url*` | MEDIUM | 仅 http/https；打开后等待加载完成 |
| `browser.back` | — | MEDIUM | 无历史时明确返回失败 |
| `browser.forward` | — | MEDIUM | 同上 |
| `browser.reload` | — | MEDIUM | 等待重新加载完成 |

### 观察（只读）

| 工具 | 参数 | 说明 |
| --- | --- | --- |
| `browser.get_page_info` | — | 标题、URL、readyState、滚动位置、元素/表单/表格数量 |
| `browser.get_dom` | `max_paragraphs` | 标题层级、段落摘要、图片/列表/表格/表单计数 |
| `browser.get_text` | `max_chars` | 正文 Markdown 摘录（默认 6000 字符） |
| `browser.get_links` | `max_links` | 页面链接列表 |
| `browser.get_forms` | — | 表单结构（字段名、类型、是否必填） |
| `browser.get_elements` | `max_elements` | **重新快照**，返回带 `element_id` 的可交互元素 |

### 交互

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `browser.click` | `element_id*` | MEDIUM | 校验存在/可见/未禁用后点击；返回“已派发”，页面效果由下一轮观察验证 |
| `browser.type` | `element_id*`, `text*` | MEDIUM | 使用原生 value setter + input/change 事件，读回长度校验 |
| `browser.clear` | `element_id*` | LOW | 清空并校验长度为 0 |
| `browser.scroll` | `direction*`, `amount` | LOW | up/down/top/bottom，返回前后 scrollY |
| `browser.select` | `element_id*`, `value*` | MEDIUM | 按 value 或可见文本匹配，返回实际选中值 |
| `browser.wait` | `ms`/`element_id`/`text` | LOW | 轮询等待，最长 15s，超时返回失败 |
| `browser.submit` | `element_id*` | HIGH | `requestSubmit()`，**必须用户确认** |

### 数据（只读）

| 工具 | 参数 | 说明 |
| --- | --- | --- |
| `browser.extract_table` | `table_index*`, `max_rows` | 输出 Markdown 表格，最多 200 行 × 30 列 |
| `browser.extract_markdown` | `max_chars` | 完整正文 Markdown，并记录为报告来源 |
| `browser.extract_metadata` | — | description/author/published/canonical 等 |
| `browser.find_text` | `text*` | 返回出现次数与上下文 |

### 文件

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `browser.download` | `url*`, `filename` | HIGH | 系统 `DownloadManager` 下载到 `Download/Browser`，**必须确认** |
| `browser.save_markdown` | `filename` | MEDIUM | 保存到 `Download/Browser/*.md` |
| `browser.save_pdf` | — | HIGH | 复用 `PdfExporter`，**必须确认**，由用户在系统打印对话框完成 |

### 标签页

| 工具 | 参数 | 风险 | 说明 |
| --- | --- | --- | --- |
| `browser.new_tab` | `url*` | MEDIUM | 新建标签页并记为 Agent 所有 |
| `browser.switch_tab` | `index*` | MEDIUM | 切换并等待加载 |
| `browser.list_tabs` | — | LOW | 列出 index/标题/URL/所有者 |
| `browser.close_tab` | `index*` | MEDIUM | Agent 创建的标签页直接关闭；用户标签页需确认 |

## 3. 元素定位规则

1. 模型只能使用 `e1`…`e9999` 形式的 `element_id`，格式由 `ElementIds` 正则校验。
2. id 来自最近一次快照（`browser.get_elements` 或每轮自动观察）。
3. 执行时先用 `data-kaori-ai-id` 精确查找，失败则用快照记录的 `tag:nth-of-type(...)` 路径兜底。
4. 找不到 / 已脱离 DOM / 不可见 / 被禁用 → 返回明确原因（`element_not_found`、`element_detached`、`not_visible`、`disabled`），并提示重新快照。
5. 跨域 iframe、封闭 Shadow DOM、Canvas 内部元素无法定位，观察结果会标记 iframe 数量，工具不会假装成功。

## 4. JavaScript 注入防护

- 工具脚本由应用自身生成，**不接受模型提供的任何 JS 或选择器**。
- 数字参数（生成号、表格索引、滚动量）先经类型校验再拼接。
- 字符串参数（输入文本、查找关键词、元素候选选择器）一律经 `JsBridge.quote()`（`JSONObject.quote`）转为 JS 字符串字面量。
- `element_id` 受正则约束，CSS 路径来自观察脚本自身而非模型。

## 5. 确认策略

`AgentSafetyManager.evaluate(tool, args, permissionMode, agentOwnedTab, sensitiveTarget)`：

| 情形 | 结果 |
| --- | --- |
| `javascript:` / `file:` / `content:` / `data:` / `intent:` 等协议 | **DENY** |
| 只读工具（`readOnly()==true`） | ALLOW |
| `ask_all` 模式下的所有写操作 | CONFIRM |
| `browser.submit` | CONFIRM（`auto` 模式也一样） |
| `browser.download` | CONFIRM |
| `browser.save_pdf` | CONFIRM |
| `browser.close_tab`（用户标签页） | CONFIRM |
| `browser.type` 目标是 password/email/tel 等敏感输入框 | CONFIRM |
| 其余有界操作（click/type/scroll/select/wait/new_tab…） | ALLOW |

确认弹窗 120 秒无响应视为拒绝；弹窗被取消同样视为拒绝。

## 6. 结果格式

每个工具返回 `ToolResult`：

```
OK|ERROR: <一句话结论>
Page changed: <before> → <after>     （发生跳转时）
<data>                               （截断到工具上限的结构化内容）
```

Agent 记忆只保留最近 3 次观察与全部已完成步骤（文本裁剪），不会无限增长。
