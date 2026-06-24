# 智简求职 Agent — 产品需求文档

> 维护方式：每次需求变更直接编辑本文件并提交 git。字段含义：🔴 阻塞 / 🟡 重要 / 🟢 锦上添花

---

## 1. 产品定位

**一句话**：面向应届生和在职跳槽者的 AI 求职伙伴，主界面是一个对话式智能体，用户通过自然语言完成从"看JD"到"拿offer"的全流程。

**核心转变**：从「多功能 App + 用户手动导航」→「一个会思考的助手 + 工具按需调用」

**整个 App 的定位就是求职智能体，不是"通用 AI 助手 + 求职技能包"。** 这个区别很重要：通用定位需要和微信 AI、豆包等竞争，但"求职 AI 伙伴"足够垂直——工具、上下文记忆、知识库全部围绕求职历史积累，用得越久越懂用户，这是通用助手做不到的护城河。

**架构开放，定位聚焦**：Agent Core（意图识别 + 路由 + 记忆）写成通用插件系统，求职工具集是第一个"插件包"。将来若拓展新域（如"租房助手""考研规划"），只需新增一批 Tool 注册进来，同一套框架运行，定位聚焦和架构扩展可以同时成立。

**不做什么**：不做岗位搜索/投递平台（只服务已有JD的场景）；不做社区/论坛；不联网抓取第三方数据。

---

## 2. 用户画像

| 画像 | 典型场景 | 核心痛点 |
|---|---|---|
| 应届生 A | 海投 20+ 个岗位，不知道为什么没有回复 | 简历和JD错位，不知道怎么改 |
| 在职跳槽者 B | 下班后偷偷练面试，精力有限 | 没有人陪练，自己不知道回答的质量 |
| 转行者 C | 从财务转产品，技能差距很大 | 不知道哪些经历可以迁移，简历怎么包装 |

---

## 3. 核心架构

```
┌──────────────────────────────────────────┐
│              对话主界面                   │  ← 唯一入口
│  消息列表  +  富卡片  +  输入栏           │
└──────────────────┬───────────────────────┘
                   │
┌──────────────────▼───────────────────────┐
│              Agent Core                  │
│  ┌──────────┐  ┌──────────┐  ┌────────┐ │
│  │意图识别  │→ │规划路由  │→ │上下文  │ │
│  │Intent    │  │Router    │  │Memory  │ │
│  └──────────┘  └──────────┘  └────────┘ │
└──────────────────┬───────────────────────┘
                   │ 调用
     ┌─────────────┼──────────────────┐
     ▼             ▼                  ▼
┌─────────┐  ┌──────────┐  ┌──────────────┐
│ JD工具  │  │ 简历工具  │  │  面试工具    │
│         │  │          │  │              │
└─────────┘  └──────────┘  └──────────────┘
     ┌─────────────┘
     ▼
┌──────────────┐
│  投递工具    │
└──────────────┘
                   │
┌──────────────────▼───────────────────────┐
│              Data Layer（不变）           │
│  Room DB  ·  DataStore  ·  AiProvider   │
└──────────────────────────────────────────┘
```

**原有独立页面（ResumeOptimizeScreen / InterviewScreen / TrackingScreen）降级为"深度编辑页"**，Agent 在对话里以富卡片呈现核心内容，用户点「展开」才进入完整页面。

### 可扩展性的三个层级

| 层级 | 扩展方式 | 示例 |
|---|---|---|
| **工具层** | 新增 Tool 实现接口并注册到 Router，Agent Core 不动 | 加"薪资谈判工具""offer对比工具" |
| **平台层** | 打招呼现在对接 BOSS，换解析逻辑即可接入猎聘/LinkedIn，接口不变 | `parse_shared_jd()` 内部实现替换 |
| **AI Provider 层** | 已有 `AiProviderManager` 支持多 provider，Agent LLM 调用走这一层，换模型零改动 | DeepSeek → Ollama → Claude 无缝切换 |

---

## 4. Agent 工具清单

每个工具对应现有模块的一段能力，Agent 的 LLM 可以通过 function call / 硬编码路由两种方式调用。

### 4.1 JD 工具 `jd_tool`

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `analyze_jd(text)` | 原始 JD 文本 | `JobDescription` | 提取岗位/公司/技能/学历要求 |
| `get_current_jd()` | — | `JobDescription?` | 读取当前会话绑定的 JD |

**触发语义**：「我看到一个JD」「帮我分析这个岗位」「粘贴JD」

### 4.2 简历工具 `resume_tool`

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `load_resume(versionId?)` | 版本ID（可选） | `Resume` | 加载当前/指定版本简历 |
| `edit_section(section, instruction)` | 段落标识 + 自然语言指令 | `Resume` | 定点修改某一条经历/技能 |
| `star_format(section)` | 段落标识 | `Resume` | STAR 法则格式化 |
| `quantify(section)` | 段落标识 | `EditSuggestion[]` | 数据量化建议（不自动改，给候选项） |
| `create_version(label)` | 版本标签 | `ResumeVersion` | 保存当前为新版本 |
| `diff_versions(v1, v2)` | 两个版本ID | `DiffResult` | 对比两版本差异 |
| `export_resume(format)` | `"pdf"` / `"html"` | `File` | 导出 |

**触发语义**：「帮我改」「优化一下」「换个说法」「保存这个版本」「导出简历」

### 4.3 匹配分析工具 `match_tool`

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `calculate_match(jd, resume)` | JD + 简历 | `MatchReport` | 综合评分 + 四维度分项 |
| `skill_gap(jd, resume)` | JD + 简历 | `SkillGap[]` | 缺失技能列表 + 重要度权重 |

`MatchReport` 字段：`overall(0-100)` / `keyword(0-100)` / `experience(0-100)` / `education(0-100)` / `skill(0-100)` / `missingSkills[]` / `highlights[]`

**触发语义**：「我匹配这个岗位吗」「我差什么」「胜率多少」

### 4.4 面试工具 `interview_tool`

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `start_interview(persona?, jd, resume)` | 人格（可选）/ JD / 简历 | `InterviewSession` | 开始面试，Agent 进入面试对话模式 |
| `submit_answer(sessionId, answer)` | 会话ID + 回答 | `InterviewTurn` | 提交一条回答，获取追问或下一题 |
| `end_interview(sessionId)` | 会话ID | `EvalReport` | 结束并生成五维度评估报告 |
| `get_hint(sessionId)` | 会话ID | `Hint` | 给出思路框架（不给直接答案） |

**面试对话模式**：Agent 进入该模式后，对话框变为面试专用 UI（题目气泡 + 回答输入 + 工具栏），结束后自动退出模式。

**触发语义**：「帮我练面试」「模拟一下」「我要准备明天的面试」

### 4.5 投递追踪工具 `tracking_tool`

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `create_application(company, jd, resumeVersionId?)` | 公司/JD/简历版本 | `TrackingEntry` | 新建一条投递记录 |
| `update_status(id, status, note?)` | ID + 新状态 + 备注 | `TrackingEntry` | 更新进度 |
| `list_applications(filter?)` | 状态筛选（可选） | `TrackingEntry[]` | 查看投递列表 |
| `set_reminder(id, datetime, note)` | 投递ID + 时间 + 备注 | `Reminder` | 设置跟进提醒 |

**触发语义**：「记录一下这个投递」「我投了xx公司」「帮我看看进展」「设个提醒」

### 4.6 外部平台集成工具 `platform_tool`

> **设计原则**：AI 负责"说什么"，用户负责"点发送"——不做全自动投递，规避平台封号风险和法律灰色地带。

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `parse_shared_jd(sharedText)` | Android Share Intent 接收的原始文本 | `JobDescription` | 解析从 BOSS/猎聘等 App 分享过来的 JD |
| `generate_greeting(jd, resume, style?)` | JD + 简历 + 风格偏好 | `GreetingDraft` | 生成针对该岗位的个性化打招呼话术（含多个候选版本） |
| `copy_to_clipboard(text)` | 话术文本 | `Unit` | 复制到剪贴板，用户回到 BOSS 粘贴发送 |

**`GreetingDraft` 字段**：`versions[]`（3个风格变体：简洁版/详细版/亮点突出版）/ `highlightedSkills[]`（本次打招呼重点强调的匹配项）/ `warningMissing[]`（JD要求但简历没有的关键项，避免被当场问倒）

**接入方式**：在 `AndroidManifest` 注册 `intent-filter` 接收 `ACTION_SEND` / `text/plain`，BOSS 岗位页"分享→其他应用→智简求职"触发。

**触发语义**：「帮我写个打招呼」「我要投这个岗位」「生成话术」/ 外部 Share 自动唤起

**🟡 后续考虑（不在当前 Sprint）**：

- 用户在系统剪贴板复制 BOSS 岗位链接 → App 内自动弹出"检测到新JD，要分析吗？"（监听剪贴板，需申请权限）
- 批量岗位管理：多个 JD 一键生成差异化话术，批量导出文本

### 4.7 面试复盘工具 `debrief_tool`（真实面试分析）

> 与 §4.4 模拟面试不同：这里处理用户上传的**真实面试录音/视频**，AI 做事后分析而非实时对话。

| 函数 | 输入 | 输出 | 说明 |
|---|---|---|---|
| `upload_recording(file, type)` | 音频/视频文件 + 类型标识 | `DebriefSession` | 创建复盘会话，开始转录 |
| `transcribe(sessionId)` | 会话ID | `Transcript` | 语音→文字（本地 STT 优先，降级云端） |
| `analyze_debrief(sessionId, jd?)` | 会话ID + 可选关联JD | `DebriefReport` | 分析答题质量、提炼问题列表、标记表现亮点/弱点 |
| `save_to_knowledge_base(sessionId)` | 会话ID | `Unit` | 将本次复盘结论写入知识库 |
| `query_knowledge_base(query)` | 自然语言查询 | `KnowledgeChunk[]` | 查询历史复盘积累的知识 |

**`DebriefReport` 字段**：
- `questions[]` — 面试官提问列表（自动识别）
- `answers[]` — 对应回答摘要
- `turnAnalysis[]` — 每题得分 + 亮点 + 改进点
- `patternInsights` — 跨题模式（如"你在系统设计题上经常忽略容灾方案"）
- `suggestedFollowUps[]` — 下次面试前建议重点准备的问题

**视频处理**：首版只提取音轨分析，视频画面（仪态/表情）列为长期项（需端侧或云端视觉模型）。

**触发语义**：「我刚面完试想复盘一下」「上传录音」「分析这次面试」「我的知识库里有什么」

---

## 5. 对话场景（核心 User Story）

### Scene 1：初次使用 — 看JD、测匹配

```
用户：[粘贴一段JD文本]
Agent：[调用 analyze_jd + load_resume + calculate_match]
       → 卡片：匹配度 76% · 环形图 · 缺失技能3项
       → 文字："你的后端经验很契合，但JD要求 Kubernetes 运维经验，
                你简历里没有体现。要帮你看看有没有可以迁移的经历？"
用户：有，我在上家公司用过Docker
Agent：[调用 edit_section("work_exp[1]", "补充容器化运维经验，提及Docker")]
       → 差异卡片：Before / After 对比
       → "改好了，顺便也把这条量化了一下，你看这个数字用得准确吗？"
```

### Scene 2：面试准备 — 从简历跳到面试

```
用户：简历改完了，帮我练练这个岗位的面试
Agent：[调用 start_interview(persona="pressure", jd=current, resume=current)]
       → 进入面试模式
       → "好，我是技术面试官，我们开始。请介绍一下你负责过最复杂的系统设计。"
用户：[回答]
Agent：[调用 submit_answer]
       → 追问："你刚才提到了分布式锁，当时具体是用什么实现的？"
...
用户：结束吧
Agent：[调用 end_interview]
       → 评估卡片：五维度雷达图 + 关键时刻复盘
       → "整体不错，系统设计部分很扎实。建议强化一下『冲突处理』场景的回答。
          要记录这次对字节跳动的投递吗？"
```

### Scene 3：投递管理 — 自然语言更新进度

```
用户：字节二面过了，约了三面
Agent：[调用 update_status(id=xx, status="二面通过")]
       [调用 set_reminder(id=xx, datetime="明天上午10点", note="三面准备")]
       → "已更新！三面前要不要再模拟一轮？"
```

### Scene 4：模糊意图 — Agent 主动澄清

```
用户：帮我改简历
Agent："你想改哪个方向的版本？我看你现在有『后端岗』和『技术管理岗』两个版本，
        还是针对刚才那个字节的JD来改？"
```

### Scene 5：外部平台 → 生成打招呼话术

```
[用户从 BOSS App 分享岗位到智简求职]
Agent：[调用 parse_shared_jd + load_resume + generate_greeting]
       → 卡片：「字节跳动·后端工程师」匹配 76%
       → "给你准备了3个版本的打招呼话术，点复制回 BOSS 粘贴就行。
          注意：JD 要求 K8s 经验，你简历里没有，打招呼时建议不要主动提，
          等对方问到再解释 Docker 背景。"
       → [简洁版] [详细版] [亮点突出版]  各自 [复制]
```

### Scene 6：真实面试复盘 + 知识库积累

```
用户：[上传一段45分钟的面试录音]
Agent：[调用 upload_recording + transcribe]
       → "转录完成，检测到 12 个面试官提问，正在分析..."
       [调用 analyze_debrief(jd=字节后端)]
       → DebriefCard：
         · 系统设计题（3道）：回答完整度 78%，遗漏了高可用设计
         · 行为题（4道）：STAR 结构不完整，结果部分经常缺数据
         · 算法题（5道）：思路清晰，复杂度分析偏弱
       → 模式洞察："这已经是你第2次在系统设计里忽略容灾方案了（上次蚂蚁面试也有）"
       → [存入知识库]  [查看上次蚂蚁的复盘]

---（两周后）---

用户：我要去准备下周的腾讯面试，我有什么弱点要补
Agent：[调用 query_knowledge_base("我的面试弱点")]
       → "根据你过去3次面试复盘，你的高频弱点是：
          1. 系统设计：容灾 & 降级方案（3次出现）
          2. 行为题：STAR 结尾缺量化数据（2次出现）
          3. 算法：空间复杂度分析经常忘提
          要针对这几个点来一轮模拟面试吗？"
```

---

## 6. UI 规范

### 6.1 主界面布局

```
┌─────────────────────────────┐
│  [当前JD：字节跳动·后端]  ✕ │  ← 上下文 Context Bar（可折叠）
│  [简历：技术岗v3]          │
├─────────────────────────────┤
│                             │
│   消息列表                  │
│   · 用户气泡（右）          │
│   · Agent气泡（左）         │
│   · 富卡片内嵌在气泡里      │
│                             │
├─────────────────────────────┤
│  [📎] [输入消息...] [发送]  │  ← 输入栏（支持粘贴长文本）
└─────────────────────────────┘
```

### 6.2 富卡片——形式说明

富卡片是**内嵌在 Agent 气泡里的 Jetpack Compose 原生组件**，不是跳转页面，也不是 WebView。类比 Slack 的消息附件或微信小程序卡片，但全部是原生 Android UI，支持交互（按钮、展开、折叠）。

**渲染时机**：工具调用结束后插入消息列表，流式文字在同一气泡里先行渲染，卡片在 `ToolResult` 事件到达后统一追加，避免内容残缺时渲染。

**交互原则**：
- 卡片内的按钮（接受/回滚/复制）直接触发 Tool 调用，不需要用户再输入
- 右下角统一有「全屏编辑 ↗」入口，跳转对应独立页面
- 卡片本身不滚动，超长内容折叠，用户点「展开」

**卡片清单**：

| 卡片 | 触发 Tool | 核心元素 |
|---|---|---|
| `MatchCard` | `calculate_match` | 总分环 + 四维进度条 + 缺失技能标签 |
| `ResumeDiffCard` | `edit_section` | Before/After 高亮行对比 + 接受/回滚按钮 |
| `ResumePreviewCard` | `load_resume` / `create_version` | 排版缩略图 + 版本标签 + 三个操作按钮 |
| `QuantifyCard` | `quantify` | 模糊短语高亮 + 候选量化方案（单选） |
| `EvalCard` | `end_interview` | 雷达图 + 关键时刻列表 |
| `DebriefCard` | `analyze_debrief` | 题目识别列表 + 逐题评分 + 模式洞察 |
| `TrackingCard` | `create_application` | 公司名 + 状态标签 + 快捷更新按钮 |
| `GreetingCard` | `generate_greeting` | 3版本话术切换 + 各自一键复制 |
| `InterviewTurnCard` | `submit_answer` | 题目 + 评语 + 追问（面试模式专用） |

### 6.3 面试模式

面试进行中：输入栏上方展示「面试中 · 字节后端 · 第3题/共10题」进度条，右上角提供「提示」「跳过」「结束」三个操作。结束后自动退出模式，返回普通对话，插入 `EvalCard`。

### 6.4 深度编辑入口

所有富卡片右下角有「展开完整编辑」按钮，点击跳转到对应独立页面（`ResumeOptimizeScreen` / `InterviewScreen` / `TrackingScreen`）。独立页面保留，不删除。

### 6.5 简历预览的三级层次

简历预览不是一个单一页面，而是根据用户意图提供三个层级，由浅到深：

```
第1级：ResumePreviewCard（对话内嵌）
  · 排版缩略图（线条模拟，等比例）
  · 版本标签 + 修改时间
  · 三个操作：[版本对比]  [导出 PDF]  [全屏预览 ↗]
        │
        └─ 点「全屏预览」↓

第2级：底部半屏弹出（BottomSheet）
  · HTML WebView 渲染完整简历（现有 ResumeHtmlPreviewScreen 的内容）
  · 顶部导航：[← 返回对话]  [版本 v3 ▾]  [导出]
  · 支持手势下滑关闭，不离开对话上下文
        │
        └─ 点「独立编辑」↓

第3级：全屏独立页面（ResumeOptimizeScreen）
  · 完整简历编辑器 + 字段级编辑
  · 返回时询问"是否保存为新版本"
```

**设计原则**：简单查看留在对话里，不打断上下文；需要仔细核对才进半屏；需要大幅修改才全屏。AI 改写后默认展示第1级卡片，用户主动说「预览一下」才到第2级。

---

## 7. 技术实现方案

### 7.1 Intent 识别

**方案：规则 + LLM 混合**

1. 先用关键词规则快速匹配高置信度意图（"开始面试"、"导出简历"等）
2. 不确定时调 LLM，system prompt 中列出所有可用 Tool 的描述，让模型输出结构化的 `{intent, tool, params}` JSON
3. 置信度低时触发澄清对话（见 Scene 4）

```kotlin
data class AgentIntent(
    val type: IntentType, // JD_ANALYZE / RESUME_EDIT / MATCH / INTERVIEW / TRACKING / PLATFORM / DEBRIEF / CHAT
    val toolCall: ToolCall?,
    val clarificationNeeded: Boolean,
    val clarificationPrompt: String?
)
```

### 7.2 Agent 上下文（AgentWorkspace）

持久化到 DataStore，跨会话保留：

```kotlin
data class AgentContext(
    val currentJd: JobDescription?,
    val currentResumeVersionId: Long?,
    val activeInterviewSessionId: Long?,
    val activeDebriefSessionId: Long?,        // 复盘会话
    val pendingActions: List<PendingAction>,
    val conversationSummary: String
)
```

### 7.3 知识库（KnowledgeBase）

存储跨会话积累的面试经验，供 Agent 在对话中随时检索。

**存储内容**：
- 每次模拟面试的评估报告摘要
- 每次真实面试复盘的 `patternInsights`
- 用户主动标记的"重要经验"片段

**技术方案**：
- 首版：Room 表 `knowledge_chunks(id, source, content, tags, createdAt)` + 关键词全文检索
- 后续：本地 embedding（`EmbeddingEngine.kt` 已有雏形）做语义检索

**写入时机**：`debrief_tool.save_to_knowledge_base` / 模拟面试结束用户确认保存时

**读取时机**：Agent 在面试准备相关对话中自动检索注入 context / 用户主动问"我有什么弱点"

### 7.4 对话历史管理

- 最近 20 条完整保存
- 超出后调 LLM 生成摘要，替换旧消息
- 摘要 + 最近消息一起作为下次请求的 context

### 7.5 流式渲染

已有 `StreamingApiService`，Agent 文字回复直接流式插入气泡；富卡片在流式结束后统一渲染（避免卡片内容残缺时渲染出来）。

### 7.6 Tool 调用与 UI 的解耦

```
ViewModel 层：AgentViewModel
  └─ 调用 AgentUseCase.process(userInput) → Flow<AgentOutput>

AgentOutput sealed class:
  · StreamText(chunk)         → 追加到当前气泡
  · ToolStart(toolName)       → 显示「正在...」loading chip
  · ToolResult(card: UiCard)  → 插入富卡片
  · ClarificationRequest(q)   → 显示澄清气泡
  · Error(msg)                → 显示错误气泡
```

---

## 8. Sprint 规划

> 前两个 Sprint 建立 Agent 骨架，后续 Sprint 逐步把现有工具迁入

### Sprint A：对话框架（2周）

- [ ] `AgentChatScreen.kt` — 消息列表 + 输入栏 + Context Bar
- [ ] `AgentViewModel.kt` — 基础对话流，直接透传到 LLM（无工具调用）
- [ ] `AgentContext` DataStore 持久化
- [ ] `StreamText` 流式渲染
- [ ] Home 页改为对话框架入口（旧三卡片入口降为侧边快捷按钮）

### Sprint B：核心工具接入（2周）

- [ ] `IntentClassifier` — 规则层（5个核心意图）
- [ ] `jd_tool.analyze_jd` 接入 + `MatchCard` 富卡片
- [ ] `match_tool.calculate_match` + `MatchCard` 已有 `MatchScoreDetailUseCase` 复用
- [ ] `resume_tool.edit_section` + `ResumeDiffCard`
- [ ] `AgentOutput` → `AgentChatScreen` 渲染管道打通

### Sprint C：面试工具（1.5周）

- [ ] `interview_tool.start_interview` → 面试模式 UI
- [ ] `submit_answer` + `InterviewTurnCard`
- [ ] `end_interview` + `EvalCard`
- [ ] 面试结束 → 投递建议 → `create_application` 串联

### Sprint D：投递工具 + 打磨（1.5周）

- [ ] `tracking_tool` 全部接入 + `TrackingCard`
- [ ] `QuantifyCard` + `ResumeDiffCard` 细节打磨
- [ ] 澄清对话（低置信度意图的处理）
- [ ] 对话历史压缩摘要
- [ ] 边界情况：无JD时、无简历时、AI超时时的降级提示

### Sprint E：外部平台集成（1周）

- [ ] `AndroidManifest` 注册 `ACTION_SEND text/plain` intent-filter
- [ ] `platform_tool.parse_shared_jd` — 解析 BOSS/猎聘分享文本
- [ ] `platform_tool.generate_greeting` — 多版本话术生成
- [ ] 话术对比卡片 `GreetingCard`（3版本 + 一键复制）
- [ ] 剪贴板 JD 检测（可选，需权限申请）

### Sprint F：面试复盘 + 知识库（2周）

- [ ] `debrief_tool.upload_recording` — 音频/视频文件选择器
- [ ] `debrief_tool.transcribe` — 集成 Android SpeechRecognizer（本地）+ DeepSeek Whisper 兜底（云端）
- [ ] `debrief_tool.analyze_debrief` — 问题识别 + 逐题分析 + 模式提炼
- [ ] `DebriefCard` — 转录进度 + 分析结果展示
- [ ] Room 表 `knowledge_chunks` + `debrief_sessions`
- [ ] `debrief_tool.save_to_knowledge_base` + `query_knowledge_base`
- [ ] Agent 在面试准备时自动检索知识库注入 context（"你上次在这类题目上..."）

---

## 9. 验收标准

| 场景 | 通过条件 |
|---|---|
| 粘贴JD | 3秒内展示 MatchCard，数据与手动计算一致 |
| 说"帮我改第二条经历" | 正确定位 section，展示 Before/After 卡片 |
| 面试流程完整跑一遍 | 能正常追问、跳过、结束并展示 EvalCard |
| 从 BOSS 分享岗位到 App | 自动解析JD，生成3版本话术，一键复制 |
| 上传面试录音 | 转录成功，识别出面试官提问，生成分析报告 |
| 第2次及以上面试准备 | Agent 主动引用知识库历史弱点 |
| 关闭App再打开 | AgentContext 恢复（JD + 简历版本不丢失） |
| 无网络时 | 本地 NLP 回退，明确提示"AI功能不可用" |
| 意图不明时 | 展示澄清问题，不误操作 |

---

## 10. 开放问题（待决策）

| # | 问题 | 选项 | 当前倾向 |
|---|---|---|---|
| Q1 | IntentClassifier 首版用规则还是 LLM | 规则快速 / LLM 准确 | 先规则，Sprint C 之后引入 LLM 兜底 |
| Q2 | 面试模式是全屏覆盖还是对话内嵌 | 全屏沉浸感好 / 对话连贯性好 | 待定，先做对话内嵌 |
| Q3 | 旧线性流程（JD→简历→润色→结果）是否保留 | 保留兼容 / 删除简化 | 保留，但不在新用户引导中展示 |
| Q4 | 对话历史是否跨JD保留 | 完全保留 / 切JD时清空 | 切JD时提示"开启新对话" |
| Q5 | 复盘转录用本地还是云端 STT | 本地（离线/隐私）/ 云端（准确率高）| 本地优先，云端降级，用户可选 |
| Q6 | 打招呼话术"自动发送"是否要做 | 不做（当前方案）/ 做（无障碍服务，有封号风险）| 暂不做，先验证话术质量的价值 |
| Q7 | 知识库长期检索用关键词还是向量 | 关键词（简单）/ 向量（语义准）| 先关键词，EmbeddingEngine 已有雏形，后续升级 |

---

*最后更新：2026-06-24 | 当前进度：Sprint 1 + 1.5 完成 | 下一步：Sprint 2（简历优化深度）→ Sprint A（对话框架）*
