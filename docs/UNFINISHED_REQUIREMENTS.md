# 智简求职 — 未完成需求 & 待办清单

> 最后更新：2026-06-24 | 当前进度：Sprint 1 + Sprint 1.5 完成（基础架构 + 三大模块 + 面试 AI 对话 + Agent 记忆 + 求职信）

---

## Sprint 2: 简历优化深度 + 跨模块"改简历"按钮

### 2.1 JD-简历匹配度分维度评分
- [ ] `domain/usecase/MatchScoreDetailUseCase.kt` — 关键词覆盖度/技能契合度/经验相关度/学历匹配度 量化（`MatchAnalysis` v2 字段已就绪，需 UseCase 填充逻辑）
- [ ] `ResumeOptimizeScreen` 展示分维度环形图 + 进度条

### 2.2 缺失技能检测
- [ ] `domain/usecase/SkillGapAnalyzer.kt` — JD vs 简历技能差异分析，含重要度权重（`SkillGap` 数据类已就绪）
- [ ] `ResumeOptimizeScreen` 缺失技能列表 + 点选"添加到简历"

### 2.3 数据量化助手
- [ ] `domain/usecase/QuantifyAssistant.kt` — 正则检测模糊表述 → AI 改写为量化表达
- [ ] `ResumeOptimizeScreen` 高亮模糊短语，点击触发 AI 量化建议（Prompt `resume_quantify` 已就绪）

### 2.4 STAR 法则格式化
- [ ] `domain/usecase/StarFormatter.kt` — 流水账经历 → 情境-任务-行动-结果（Prompt `resume_star_format` 已就绪）
- [ ] `ResumeOptimizeScreen` 每条经历的"STAR 格式化"按钮

### 2.5 简历多版本管理完善
- [ ] `ResumeOptimizeScreen` 版本下拉选择器 + "新建版本"按钮（后端 `ResumeVersionRepository` 已就绪）
- [ ] 版本间对比功能
- [ ] 版本标签（技术岗/产品岗/外企岗）

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
6. **从 Sprint 2.1（匹配度分维度评分）开始**，按顺序推进
