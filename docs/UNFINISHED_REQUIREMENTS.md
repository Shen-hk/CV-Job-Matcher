# 智简求职 — 未完成需求 & 待办清单

> 最后更新：2026-06-25 | 当前进度：Sprint 1 + Sprint 1.5 完成；Sprint 2.1-2.5 完成；Sprint A-UI 完成（聊天界面重构 + Markdown 渲染 + 富卡片 Composable + 流式节流）；Sprint A-Logic 未完成（工具执行、UseCase 接入、卡片回调）

---

## Sprint A-UI：Agent 聊天系统重构（已完成）

> 来源：用户要求参考 `D:\Project\Android\mantou` 项目，移植其 API 系统和聊天系统，适配 TieLink 求职功能，UI 继续用 Compose。

### 已完成部分

- [x] `StreamEvent` 新增 `Thinking` 变体，接通 DeepSeek 推理模型 `reasoning_content` 字段
- [x] `AgentOutput` 新增 `Thinking` 变体，从 `StreamingApiService` → `AgentUseCase` → `AgentViewModel` 全链路打通
- [x] `AgentMessage` 扩展字段：`card: UiCard?`、`thinkingContent: String?`、`toolLoadingName: String?`
- [x] `AgentChatUiState` 新增 `thinkingBuffer` 字段
- [x] 新建 `MarkdownText.kt` — 纯 Compose Markdown 渲染器（无外部依赖），支持 H1-H4 标题、有序/无序列表、代码块、加粗/斜体/行内代码、分割线
- [x] 新建 `AgentCards.kt` — 7 种富卡片 Composable：`MatchCard`、`ResumeDiffCard`、`ResumePreviewCard`、`EvalCard`、`TrackingCard`、`GreetingCard`、`InterviewTurnCard`
- [x] 重写 `AgentViewModel`：120ms 流式节流（参考 mantou）、思考内容单独缓冲、ToolStart 插入 loading 气泡、ToolResult 替换为卡片
- [x] 重写 `AgentChatScreen`：Agent 气泡带头像 + Markdown 渲染 + 可折叠思考面板；用户气泡右对齐；三点等待动画；错误横幅淡入淡出

---

## Sprint A-Logic：Agent 工具执行接入（未完成，核心缺口）

> 当前状态：UI 层全部完成，但工具从不执行 — `AgentOutput.ToolResult` 永远不会 emit，所有富卡片永远不会出现。

### A-L1 AgentUseCase 工具分发（最高优先级）

- [ ] `AgentUseCase.kt:65` 处的 `TODO` — 根据 `intent.toolCall.toolName` 分发到对应 UseCase
  - `match_tool` → 调用 `MatchScoreDetailUseCase.enrich()` + `SkillGapAnalyzer.analyze()` → emit `ToolResult(UiCard.MatchCard(...))`
  - `resume_tool` → 调用 `QuantifyAssistant.analyzeAndSuggest()` / `StarFormatter.format()` → emit `ToolResult(UiCard.ResumeDiffCard(...))`
  - `interview_tool` → 读取最近面试会话 → emit `ToolResult(UiCard.InterviewTurnCard(...))`
  - `tracking_tool` → 读取投递记录 → emit `ToolResult(UiCard.TrackingCard(...))`
  - `greeting_tool` → AI 生成求职信 → emit `ToolResult(UiCard.GreetingCard(...))`
- [ ] 工具执行需要在 `AgentUseCase` 注入：`MatchScoreDetailUseCase`、`SkillGapAnalyzer`、`QuantifyAssistant`、`StarFormatter`、`TrackingRepository`

### A-L2 IntentClassifier 路由验证

- [ ] 确认 `IntentClassifier.classify()` 对常见求职意图能正确返回 `toolCall`（"帮我分析匹配度"、"优化这句话"、"准备面试"等）
- [ ] 补充缺失的意图识别规则（当前规则覆盖不全）

### A-L3 ResumeDiffCard 回调接入

- [ ] `UiCard.ResumeDiffCard.onAccept` 目前是空 lambda — 需接入 `ResumeVersionRepository` 将 `after` 文本写入当前激活版本
- [ ] `onRollback` 需恢复原文

### A-L4 Anthropic 协议支持（可选，低优先级）

- [ ] mantou 支持 OpenAI + Anthropic 双协议，TieLink `StreamingApiService` 目前只有 OpenAI 格式
- [ ] 若用户配置 Claude API，需在 `StreamingApiService` 增加 `streamAnthropicChat()` 并在 `AiProviderManager` 接入

---



### 2.1 JD-简历匹配度分维度评分
- [x] `domain/usecase/MatchScoreDetailUseCase.kt` — 关键词覆盖度/技能契合度/经验相关度/学历匹配度 量化（含 `enrich()` 方法填充 MatchAnalysis v2 字段）
- [x] `ResumeOptimizeScreen` 展示分维度环形图 + 进度条（4维度颜色区分：绿/橙/红）

### 2.2 缺失技能检测
- [x] `domain/usecase/SkillGapAnalyzer.kt` — JD vs 简历技能差异分析，含重要度权重（REQUIRED/PREFERRED/NORMAL）
- [x] `ResumeOptimizeScreen` 缺失技能列表 + 点选"添加到简历" AssistChip，按重要度分组显示

### 2.3 数据量化助手
- [x] `domain/usecase/QuantifyAssistant.kt` — 正则检测模糊表述 → AI 改写为量化表达（批量最多5条）
- [x] `ResumeOptimizeScreen` 量化建议卡片，每条可单独采用/忽略

### 2.4 STAR 法则格式化
- [x] `domain/usecase/StarFormatter.kt` — 经历文本 → 情境-任务-行动-结果（Prompt `resume_star_format` 已接入）
- [x] `ResumeOptimizeScreen` "STAR格式化"按钮 → 输入弹窗 → 结果对话框 → 追加到简历

### 2.5 简历多版本管理完善
- [x] `ResumeOptimizeScreen` 版本保存对话框加入标签选择（技术岗/产品岗/外企岗/管理岗/实习）
- [x] 版本间对比功能（DropdownMenu 内"对比"按钮 → 左右双列对比 Dialog）
- [x] 版本标签在下拉选择器中显示

### 2.6 面试中 → 改简历 跨模块跳转
- [ ] `InterviewScreen` 当 AI 检测到回答不足时显示"帮我改简历"操作 chip
- [ ] `ResumeOptimizeScreen` 支持"快速编辑模式"（从面试跳入，编辑后返回）
- [ ] `AgentOrchestrator.kt` 跨模块导航状态管理

---

## Sprint 3: 面试深度 + "投递"入口

### 3.1 面试官人格引擎
- [ ] `domain/interview/PersonaEngine.kt` — 人格 → prompt 模板 + 温度参数 + 追问策略
- [ ] `domain/interview/FollowUpStrategy.kt` — 回答太短→追问、缺失关键点→针对性追问、5 秒沉默→换问法

### 3.2 面试评估页完善
- [ ] `ui/interview/InterviewEndScreen.kt` — 独立页面：综合评分仪表盘 + 5 维度柱状图 + 关键时刻回放

### 3.3 语音输入
- [ ] 直接使用 `SpeechRecognizer` Intent 集成到 InterviewScreen

### 3.4 面试辅助工具栏
- [ ] 大纲展开（已考/未考标记）
- [ ] 提示（AI 给思路框架，不给直接答案）
- [ ] 跳过换题

### 3.5 面试结束 → 投递 流程
- [ ] `InterviewEndScreen` "投递这家公司"按钮 → 检查简历版本 → 推荐最佳 → 确认 → 创建投递记录

---

## Sprint 4: 投递管理完善 + 完整 Agent 闭环

### 4.1 投递管理完善
- [ ] 投递详情页（时间轴、备注、状态历史）
- [ ] 状态流转动画
- [ ] `domain/usecase/TrackingAnalytics.kt` — 转化率、平均响应时长

### 4.2 Agent 对话路由
- [ ] `domain/agent/IntentClassifier.kt` — 意图识别（"帮我改下这句"→简历技能，"投递这个"→投递技能）
- [ ] `domain/agent/AgentRouter.kt` — 意图 → 模块动作路由
- [ ] `ui/components/AgentFloatingButton.kt` — 全局 FAB，唤起迷你 Agent 聊天

### 4.3 跨模块上下文保持
- [ ] `AgentOrchestrator` 持久化关键变量（JD、简历版本、面试会话 ID）

### 4.4 通知 & 提醒
- [ ] `domain/reminder/ReminderScheduler.kt` — 面试提醒、投递跟进提醒
- [ ] `util/NotificationHelper.kt` — Android 通知渠道

### 4.5 打磨 & 集成测试
- [ ] 端到端流程测试（设置JD→输入简历→AI润色→保存版本→面试→改进简历→投递→追踪）
- [ ] 边界情况（无JD、无网络、空简历、AI 返回异常）
- [ ] UI 统一（品牌色、间距、暗黑模式）

---

## PRD_AGENT Sprint 映射

PRD_AGENT.md 定义了 Sprint A-F，与上述 Sprint 编号的对应关系：

| PRD Sprint | 内容 | 对应本文档 | 状态 |
|------------|------|-----------|------|
| Sprint A | 对话框架（AgentChatScreen + AgentViewModel + Context Bar） | Sprint 4.2 + 4.3 | 未开始 |
| Sprint B | 核心工具接入（IntentClassifier + jd_tool + match_tool + resume_tool） | Sprint 2 + Sprint 4.2 | 部分就绪 |
| Sprint C | 面试工具（interview_tool + InterviewTurnCard + EvalCard） | Sprint 3 | 未开始 |
| Sprint D | 投递工具 + 打磨（tracking_tool + 澄清对话 + 历史压缩） | Sprint 4 | 未开始 |
| Sprint E | 外部平台集成（Share Intent + parse_shared_jd + generate_greeting） | 新增 | 未开始 |
| Sprint F | 面试复盘 + 知识库（debrief_tool + knowledge_chunks） | 新增 | 未开始 |

---

## 长期扩展路线图（待排期）

### 简历优化层
- [ ] 简历"防歧视"检测（性别/年龄/籍贯等风险词）
- [ ] 多模板 HTML/PDF 导出优化

### 投递管理层
- [ ] 企业库 & 面经关联
- [ ] 投递时机建议（岗位发布时效、HR 活跃时段）
- [ ] 海投批量管理
- [ ] 内推码市场（用户互助交换）
- [ ] "幽灵投递"诊断（分析已读不回原因）

### 面试准备层
- [ ] 简历深挖预测（预判追问点）
- [ ] 公司题库结构化搜索
- [ ] 面试官背景查询
- [ ] 薪资谈判模拟

### 职业成长层
- [ ] 技能 Gap 分析（导流课程）
- [ ] 职业路径模拟
- [ ] 行业薪资情报（匿名 offer 分位值）
- [ ] 人脉网络图谱
- [ ] 离职风险评估
- [ ] 团队氛围预测（NLP 分析脉脉/看准网评论）

### Agent 对话增强
- [ ] 语音对话（Speech-to-Text + Text-to-Speech 全双工）
- [ ] 多轮上下文记忆
- [ ] 对话摘要生成
- [ ] 情绪感知（检测紧张/不自信，调整面试策略）

---

## 下次继续时

1. **阅读本文件**了解当前进度和待办
2. **参考 `CHANGELOG.md`**（项目根目录）了解已完成的架构和文件
3. **参考 `CLAUDE.md`**（项目根目录）了解项目技术栈和开发命令
4. **参考 `docs/TECHNICAL_DESIGN.md`** 了解完整技术方案
5. **参考 `docs/PRD_AGENT.md`** 了解 Agent 架构规划和 Sprint A-F
6. **从 Sprint A-Logic（Agent 工具执行接入）开始**，优先级：A-L1（工具分发）> A-L2（意图路由）> A-L3（卡片回调）> A-L4（Anthropic 协议），然后推进 Sprint 2.6 和 Sprint 3
