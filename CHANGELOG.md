# 智简求职 — 修改日志

## Sprint 1.5: 增量完善 + Agent 基础设施 (2026-06-24)

### 架构变更

| 变更 | 说明 |
|------|------|
| Agent 记忆系统 | 新增 `AgentWorkspace` 基于文件的轻量记忆，三域（简历/JD/面试）独立存储 |
| 求职信生成 | 新增 `CoverLetterRepository`，支持中英文求职信 AI 生成 |
| 简历结构化模型 | `ResumeData` 完整实现：JSON 反序列化、文本解析、个人链接自动检测 |
| 数据库 v5→v6 | `fallbackToDestructiveMigration(true)` 开发阶段容错 |
| DataStore 扩展 | 新增 `app_language`、`cached_jd_company` 等 PrefKey |

### 新增文件

**Util 层：**
- `util/AgentWorkspace.kt` — Agent 记忆系统（文件持久化 + 自然语言提取 + 去重写入）

**Data 层：**
- `data/repository/CoverLetterRepository.kt` — 求职信生成（中/英文 + 模板建议）

### 修改文件

| 文件 | 修改内容 |
|------|----------|
| `domain/model/ResumeData.kt` | 完整结构化模型：Experience/Education/Project/SocialLink + fromPolishedText() + fromJsonString() + withAutoDetectedLinks() |
| `data/local/db/AppDatabase.kt` | version 5→6 |
| `data/local/AppPreferences.kt` | 新增 app_language、cached_jd_company PrefKey |
| `util/HtmlPdfExporter.kt` | 新增 buildVibeHtml() Vibe 模板导出支持 |

---

## Sprint 1: 基础架构 + 三大模块壳子 + 模拟面试 AI 对话 (2026-06-16)

### 架构变更

| 变更 | 说明 |
|------|------|
| 全局 JD 状态 | 新增 `GlobalJdState` + `GlobalJdViewModel`，通过 CompositionLocal 在 Activity 级别共享，一处输入全产品复用 |
| 导航重构 | `NavGraph.kt` — 新增 HOME/RESUME_OPTIMIZE/MOCK_INTERVIEW/TRACKING 路由，首页作为入口，保留旧路线兼容 |
| 数据库 v4→v5 | 新增 4 张表：`resume_versions`、`tracking`、`interview_sessions`、`interview_messages`，含完整 Migration 4→5 |
| DI 扩展 | `AppModule.kt` 绑定 3 个新 DAO + TextCleaner |

### 新增文件（27 个源文件）

**Domain 层（6 个）：**
- `domain/model/GlobalJdState.kt` — 全局 JD 状态数据类
- `domain/model/InterviewMessage.kt` — 面试消息（USER/INTERVIEWER/SYSTEM）
- `domain/model/InterviewSession.kt` — 面试会话 + InterviewPersona 枚举
- `domain/model/InterviewResult.kt` — 面试评估结果（分维度评分+关键时刻）
- `domain/model/ResumeVersion.kt` — 简历多版本数据类

**Data 层（11 个）：**
- `data/local/db/entity/ResumeVersionEntity.kt` — 简历版本 Room 实体
- `data/local/db/entity/TrackingEntity.kt` — 投递记录 Room 实体
- `data/local/db/entity/InterviewSessionEntity.kt` — 面试会话 Room 实体
- `data/local/db/entity/InterviewMessageEntity.kt` — 面试消息 Room 实体（外键关联会话）
- `data/local/db/dao/ResumeVersionDao.kt` — 版本 CRUD + 激活切换
- `data/local/db/dao/TrackingDao.kt` — 投递 CRUD + 状态筛选
- `data/local/db/dao/InterviewDao.kt` — 面试会话+消息 CRUD + 级联删除
- `data/repository/ResumeVersionRepository.kt` — 版本仓库（Moshi JSON 序列化 tags）
- `data/repository/TrackingRepository.kt` — 投递仓库（状态流转+时间轴）
- `data/repository/InterviewRepository.kt` — 面试仓库（会话+消息+结束评估）
- `data/remote/InterviewPrompts.kt` — 面试提示词管理（人格→prompt 映射）

**UI 层（10 个）：**
- `ui/GlobalJdViewModel.kt` — 全局 JD 状态持有者（Hilt ViewModel + CompositionLocal）
- `ui/home/HomeScreen.kt` — 首页：logo + 3 入口卡片 + JD 状态栏
- `ui/home/HomeViewModel.kt` — 首页数据（版本数、投递数）
- `ui/resumeoptimize/ResumeOptimizeScreen.kt` — 简历优化：JD 状态栏 + 文本输入 + AI 润色 + 匹配度 + 版本管理
- `ui/resumeoptimize/ResumeOptimizeViewModel.kt` — 润色逻辑（调用 PolishRepository + MatchAnalysisUseCase）
- `ui/interview/InterviewScreen.kt` — 模拟面试：人格选择 + 聊天气泡 + 语音/文字输入 + 工具栏（提示/跳过）+ 结束评估卡片
- `ui/interview/InterviewViewModel.kt` — 面试逻辑（AI 对话+追问+评估 JSON 解析）
- `ui/tracking/TrackingScreen.kt` — 投递管理：状态筛选芯片 + 投递列表 + 新增表单 + 状态流转
- `ui/tracking/TrackingViewModel.kt` — 投递 CRUD + 过滤器

### 修改文件（9 个）

| 文件 | 修改内容 |
|------|----------|
| `navigation/NavGraph.kt` | 新增 6 条路由，Home 作为 startDestination，JD 提交同步全局状态 |
| `MainActivity.kt` | 通过 CompositionLocalProvider 注入全局 GlobalJdViewModel |
| `di/AppModule.kt` | 绑定 4 个新 DAO + TextCleaner provider + Migration 4→5 |
| `data/local/db/AppDatabase.kt` | version 4→5，新增 4 个实体 + Migration_4_5 |
| `data/local/AppPreferences.kt` | 新增 JD 缓存、面试偏好等 5 个 PrefKey |
| `data/remote/PromptRegistry.kt` | 新增 10 个 fallback prompt（面试+量化+STAR+匹配度） |
| `assets/prompts.json` | 新增 6 个面试 + 3 个简历辅助 prompt 配置 |
| `domain/model/MatchAnalysis.kt` | 新增分维度评分字段 + SkillGap 数据类 |
| `domain/model/Resume.kt` | 新增 versionId、sections、tags 字段 + ResumeSections 数据类 |

### 新增 Prompt 配置

| Key | 用途 |
|-----|------|
| `interview_mild_tech` | 温和技术官（友好引导，关注技术深度） |
| `interview_pressure` | 压力面考官（高压追问，测试抗压） |
| `interview_foreign_hr` | 外企 HR（Behavioral 风格，STAR 法则） |
| `interview_state_structured` | 国企结构化（标准化流程，综合素质） |
| `interview_follow_up` | 追问判断（分析回答质量，决定是否深挖） |
| `interview_evaluation` | 面试评估（5 维度评分+关键时刻标记） |
| `resume_quantify` | 数据量化助手（模糊描述→量化表达） |
| `resume_star_format` | STAR 法则格式化 |
| `match_score_detail` | 简历匹配度分维度分析 |
