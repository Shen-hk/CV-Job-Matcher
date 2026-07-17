# TieLink 开发文档

> 生成时间：2026-07-17  
> 适用范围：基于当前仓库代码的实现级说明，目标是帮助新开发者在读完后能像参与过完整开发一样理解项目。  
> 说明：仓库里已有部分设计文档，但其中有些更偏产品草案或历史阶段说明；本文以“当前代码真实实现”为准。

## 目录

1. [项目一句话说明](#1-项目一句话说明)
2. [项目解决的问题](#2-项目解决的问题)
3. [技术栈总览](#3-技术栈总览)
4. [为什么这样选型](#4-为什么这样选型)
5. [整体架构](#5-整体架构)
6. [启动流程与全局状态](#6-启动流程与全局状态)
7. [导航结构](#7-导航结构)
8. [核心数据模型](#8-核心数据模型)
9. [数据存储设计](#9-数据存储设计)
10. [AI 接入架构](#10-ai-接入架构)
11. [Prompt 体系](#11-prompt-体系)
12. [NLP 与匹配算法](#12-nlp-与匹配算法)
13. [Agent 对话系统](#13-agent-对话系统)
14. [JD 分析功能](#14-jd-分析功能)
15. [简历导入与版本库](#15-简历导入与版本库)
16. [简历优化工作台](#16-简历优化工作台)
17. [线性润色流程](#17-线性润色流程)
18. [结果页、结构化编辑与导出](#18-结果页结构化编辑与导出)
19. [模拟面试系统](#19-模拟面试系统)
20. [投递追踪系统](#20-投递追踪系统)
21. [JD 库、简历库与 BOSS 导入](#21-jd-库简历库与-boss-导入)
22. [文件解析与 OCR](#22-文件解析与-ocr)
23. [HTML/PDF 导出实现](#23-htmlpdf-导出实现)
24. [记忆系统与上下文沉淀](#24-记忆系统与上下文沉淀)
25. [设置与模型配置](#25-设置与模型配置)
26. [UI 组织方式](#26-ui-组织方式)
27. [测试与性能优化](#27-测试与性能优化)
28. [项目里值得注意的设计取舍](#28-项目里值得注意的设计取舍)
29. [当前已知不足与演进方向](#29-当前已知不足与演进方向)
30. [如果你要继续开发，建议先看哪里](#30-如果你要继续开发建议先看哪里)
31. [本地开发与首次运行](#31-本地开发与首次运行)
32. [功能调用链与扩展点地图](#32-功能调用链与扩展点地图)
33. [AI 接口、流式协议与降级行为](#33-ai-接口流式协议与降级行为)
34. [数据库迁移、数据生命周期与备份](#34-数据库迁移数据生命周期与备份)
35. [权限、隐私与安全边界](#35-权限隐私与安全边界)
36. [测试、验收与排障手册](#36-测试验收与排障手册)
37. [构建、发布与性能交付](#37-构建发布与性能交付)
38. [文档维护约定](#38-文档维护约定)

## 1. 项目一句话说明

TieLink 是一个面向求职场景的 Android 应用，它把 JD 分析、简历优化、模拟面试、投递跟踪和 Agent 对话整合成一个移动端求职工作台。

它不是“通用聊天机器人”，而是一个强约束的求职助手。

## 2. 项目解决的问题

这个项目解决的不是单点问题，而是求职流程被割裂的问题：

- JD 在一个地方看
- 简历在另一个地方改
- 面试准备靠临时记忆
- 投递状态靠手记
- 每次和 AI 重新解释上下文

TieLink 的核心思路是把这些动作串成闭环，并把上下文沉淀下来，让后续操作不是“重新开始”，而是“接着上一步继续推进”。

从代码上看，当前产品主轴已经明显收敛为：

- `AgentChat` 作为默认入口
- `JD / Resume / Interview / Tracking` 作为可被 Agent 调度或手动进入的能力模块
- 本地存储负责保留上下文、历史结果和可复用资产

## 3. 技术栈总览

### 客户端

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Android SDK 35，`minSdk 24`

### 架构

- MVVM
- 分层式 Clean Architecture
- Hilt 依赖注入
- KSP 代码生成

### 数据与网络

- Room
- DataStore Preferences
- Retrofit
- OkHttp
- Moshi

### AI / NLP

- DeepSeek API
- Ollama
- 本地 TensorFlow Lite Embedding
- 自定义 TF-IDF / 关键词匹配
- ML Kit 中文 OCR

### 文档处理

- pdfbox-android
- WebView + Android Print 导出 PDF
- DOCX 直接 ZIP + XML 解析

### 性能

- Baseline Profile
- ProfileInstaller

## 4. 为什么这样选型

### 为什么是 Compose

这个项目页面很多，而且 UI 交互状态重：聊天流、卡片流、配置流、版本切换、导出预览、面试状态。Compose 在这种“状态驱动界面”下比传统 XML 更自然，尤其适合：

- `StateFlow -> UI`
- 聊天气泡、动态卡片、底部输入框联动
- 多弹窗、多抽屉、多条件局部刷新

### 为什么是 MVVM + 分层

业务复杂度已经超过“一个页面一个接口”的规模。比如简历优化就同时涉及：

- JD
- 简历文本
- AI Provider
- 匹配评分
- 版本管理
- 导出

如果不分层，会很快把 ViewModel 变成巨型脚本。当前代码虽然仍有少数大文件，但整体方向是清晰的：

- `ui/` 管界面与状态
- `domain/` 管模型、NLP、用例、工具编排
- `data/` 管数据库、偏好、API、Repository

### 为什么同时保留 DeepSeek、Ollama、本地能力

这是一个很关键的产品策略：

- DeepSeek 负责更强的生成质量
- Ollama 负责本地/局域网部署与更低成本
- 本地 Embedding 负责最差情况下的离线语义匹配兜底

也就是说，这个项目从一开始就不是“单一云服务绑定”思路，而是“AI 能力可替换”思路。

### 为什么匹配算法不是纯 LLM

因为匹配评分需要两种东西同时成立：

- 结果可解释
- 没网时仍然能用

所以项目用“关键词 + TF-IDF / Embedding”的本地算法做基础分，再在可用时引入 LLM 分数作为增强，而不是完全交给大模型黑箱判断。

## 5. 整体架构

```text
UI Layer
  Compose Screens
  ViewModels
  Shared UI Components

Domain Layer
  Core Models
  NLP Engine
  Match / Resume / Agent UseCases
  Agent Tool Coordinator

Data Layer
  Room / DataStore
  Repositories
  DeepSeek / Ollama / Streaming API

Utility Layer
  File Parsing
  OCR
  HTML/PDF Export
  Agent Memory Workspace
```

更具体一点，可以把它理解成两条主线：

### 主线 A：页面驱动业务

例如传统线性流程：

`JdInput -> ResumeInput -> Polish -> Result`

### 主线 B：Agent 驱动业务

例如聊天入口：

`AgentChat -> 意图判断 -> 工具调用 -> 卡片结果 / 文本回答 -> 用户继续操作`

项目现在处于从 A 向 B 迁移但兼容 A 的阶段，所以你会看到新老两套入口同时存在。

## 6. 启动流程与全局状态

### 应用启动

启动入口在：

- `app/src/main/java/com/example/tielink/TieLinkApp.kt`
- `app/src/main/java/com/example/tielink/MainActivity.kt`

启动时做了两件重要事情：

1. 初始化 `FileParser`
2. 尝试初始化 `EmbeddingEngine`

Embedding 初始化失败不会阻断应用，因为匹配逻辑还能退化到 TF-IDF。

### 全局 JD 状态

项目里有一个很实用的设计：`GlobalJdStateHolder`

它不是 ViewModel，而是 Hilt 注入的 `@Singleton` 普通类，通过 `CompositionLocalProvider` 暴露给全局 UI。这么做的原因是：

- 避免把大量 JD 文本塞进导航参数
- 跨页面共享当前 JD
- 顺手持久化到 DataStore

这意味着“当前 JD”既有内存态，也有恢复能力。

## 7. 导航结构

导航定义集中在：

- `app/src/main/java/com/example/tielink/navigation/NavGraph.kt`

### 当前默认首页

`startDestination = Routes.AGENT_CHAT`

这说明产品已经把 Agent 聊天页当成主入口。

### 主要新入口

- `agent_chat`
- `resume_optimize`
- `mock_interview`
- `tracking`
- `interview_debrief`
- `jd_list`
- `resume_library`

### 仍保留的旧线性流程

- `jd_input`
- `resume_input/...`
- `polish/...`
- `result/{sessionId}`
- `history`
- `settings`

### 为什么保留旧流程

因为旧流程承担的不是“历史包袱”那么简单，它仍然是：

- 可控的从输入到产出流程
- 很多结果页、历史页、导出逻辑的基础来源
- Agent 工具之外的稳定落地通道

所以现在的代码策略是：新体验用 Agent，底层产出链仍部分依赖旧流程。

## 8. 核心数据模型

### JD

`JobDescription`

包含：

- `jobTitle`
- `requirements`
- `skills`
- `responsibilities`
- `niceToHave`
- `summary`

这是 JD 结构化后的标准模型。

### 简历

这里有三层简历表示：

1. 原始文本
2. `ResumeVersion`
3. `ResumeData`

#### `ResumeVersion`

偏“资产管理”：

- 名称
- 原始文本
- 清洗/优化文本
- 标签
- 是否激活
- 原文件路径
- MIME
- 是否已经润色

#### `ResumeData`

偏“结构化展示”：

- 姓名
- 目标岗位
- 联系方式
- summary
- experiences
- education
- projects
- skills
- certifications
- languages
- photo
- links

结果页编辑、HTML 渲染、PDF 导出都依赖它。

### 匹配分析

`MatchAnalysis`

包含：

- 总分
- 命中关键词
- 缺失关键词
- suggestions
- 新版分维度字段
- `SkillGap`

### Agent

重要模型有：

- `AgentMessage`
- `AgentChatUiState`
- `AgentContext`
- `AgentOutput`
- `UiCard`

其中 `UiCard` 是 Agent 系统的关键抽象，因为它把“工具结果”标准化为可以渲染的 UI 组件，而不是任由模型输出任意文本。

## 9. 数据存储设计

### Room

数据库定义在：

- `app/src/main/java/com/example/tielink/data/local/db/AppDatabase.kt`

当前版本：`version = 13`

主要表：

- `history`
- `resume_versions`
- `tracking`
- `interview_sessions`
- `interview_messages`
- `jd_library`
- `providers`
- `provider_models`

### 这些表分别承担什么

#### `history`

记录一次完整产出，尤其是润色结果或 Agent 会话归档。

它很像“项目产物历史库”。

#### `resume_versions`

记录可复用简历版本，是长期资产。

#### `tracking`

记录投递状态和时间线。

#### `interview_sessions` / `interview_messages`

记录模拟面试会话与逐条消息。

#### `jd_library`

保存岗位库，手输和 BOSS 自动导入都落这里。

#### `providers` / `provider_models`

这是后期扩展出来的“多 Provider 持久层”，说明项目在往更灵活的模型管理演进。

### DataStore

`AppPreferences` 负责轻量配置：

- API Key
- 模型名
- Base URL
- Ollama 配置
- 当前 AI Provider
- PDF 模板偏好
- 当前缓存 JD
- 最近简历
- Agent 上下文
- Agent 草稿
- 上次面试 persona

这类数据不用 Room，是合理的，因为它们更像应用设置而不是业务记录。

## 10. AI 接入架构

核心接口：

- `AiProvider`

实现类：

- `DeepSeekProvider`
- `OllamaProvider`
- `LocalProvider`

统一入口：

- `AiProviderManager`

### 它解决了什么问题

不是简单封装 API，而是统一处理：

- 当前使用哪个 Provider
- Provider 是否可用
- 失败后怎么回退
- 聊天与流式聊天
- embedding 获取策略

### 回退策略

大体是：

- 首选用户配置的 Provider
- 失败后尝试另一个网络 Provider
- embedding 场景再退到本地模型

这使得上层 UseCase 不需要关心 DeepSeek 和 Ollama 的差异。

## 11. Prompt 体系

Prompt 注册中心：

- `PromptRegistry`

配置文件：

- `app/src/main/assets/prompts.json`

### 为什么这个设计重要

很多项目把 Prompt 写死在 Repository 里，维护起来会越来越痛。TieLink 把 prompt 外置以后，获得了几个好处：

- 可以统一管理 system prompt、temperature、maxTokens
- 产品和算法调整时成本更低
- 有 fallback，配置文件损坏时应用仍能运行

### 当前主要 Prompt 类型

- `polish_full`
- `polish_partial`
- `polish_iterative`
- `resume_quantify`
- `resume_star_format`
- `match_score_detail`
- `cover_letter_zh`
- `cover_letter_en`
- `interview_*`
- `agent_chat`

这基本覆盖了项目的主要 AI 能力面。

## 12. NLP 与匹配算法

这一部分是项目最像“工程实现”而不是“纯调用模型”的地方。

### `NlpEngine`

手写了轻量级：

- 中文 / 拉丁 token 切分
- CJK 单字 + 双字
- TF-IDF
- 余弦相似度
- 关键词抽取

这说明作者很在意：

- 离线能力
- 可解释性
- 依赖尽量轻

### `KeywordClassifier`

从 `skill_dict.json` 建反向索引，把技能词映射到分类。

作用不是炫技，而是为了让“缺失技能”不只是一个平铺列表，而是能进一步分析：

- 属于编程语言
- 属于框架
- 属于工具
- 属于云平台

### `EmbeddingEngine`

本地 TFLite 模型，目标是做语义级相似度。

它当前实现比较轻量，甚至 token 化都比较简化，这说明它更像“够用型本地语义兜底”，不是追求学术级精度。

### 两套评分思路

#### 旧/基础分析

`MatchAnalysisUseCase`

- 60% 关键词覆盖
- 40% TF-IDF

#### 新/语义分析

`SemanticMatcher`

- 60% semantic
- 40% keyword

如果 LLM 给出分数，再做加权融合。

### 为什么会有两套

因为项目在演进：

- 一开始更偏传统 ATS 匹配
- 后来开始引入 embedding 和更细维度评分

现在代码里两条路径都还能看到。

## 13. Agent 对话系统

这是当前项目最核心的模块之一。

主要代码：

- `domain/usecase/AgentUseCase.kt`
- `domain/usecase/AgentToolCoordinator.kt`
- `ui/agent/AgentViewModel.kt`
- `ui/agent/*.kt`

### 整体流程

```text
用户输入
  -> AgentUseCase.buildTurnPolicy()
  -> 构建 system prompt + 历史消息 + 当前上下文
  -> 给模型提供有限工具集
  -> 模型选择直接回答 or 工具调用
  -> AgentToolCoordinator 执行工具
  -> 返回 ToolResult / StreamText / Done
  -> AgentViewModel 更新消息流与卡片流
```

### 这个系统最关键的设计点

#### 1. 本轮工具边界

不是任何时候都把所有工具喂给模型，而是根据用户输入先做“允许工具集合”裁剪。

这会降低：

- 无关卡片弹出
- 乱调用工具
- 聊天型问题被硬转成业务操作

#### 2. 工具调用轮次限制

有：

- 最大回合数
- 每轮最大工具调用数
- 重复调用签名去重

这是为防止模型陷入工具循环。

#### 3. 卡片协议化

模型不能直接生成 Compose 代码，只能通过工具返回标准卡片模型。

尤其 `render_card` 还被限制为：

- text
- metrics
- tags
- progress
- timeline
- steps
- table
- kanban
- decision

这是非常重要的安全设计。它保证模型只能“填数据”，不能“决定 UI 实现细节”。

#### 4. Agent 草稿与会话归档

`AgentViewModel` 会：

- 持久化当前草稿
- 序列化卡片快照
- 归档成 `history` 记录

所以聊天不是一次性状态，而是可恢复、可回看、可继续的。

## 14. JD 分析功能

主要代码：

- `JdInputViewModel`
- `JdRepository`

### 流程

1. 用户手输 JD 或从图片 OCR
2. `TextCleaner` 清洗
3. `JdRepository.extractJobDescription()`
4. 构造专门的 JD 提取 prompt
5. 调 DeepSeek 返回 JSON
6. 解析成 `JobDescription`
7. 保存到全局 JD 状态

### 为什么单独做结构化提取

因为后面很多功能都依赖 JD 不是一大段纯文本，而是有结构：

- 技能
- 要求
- 职责
- 加分项

否则匹配度、建议、面试准备都会变得很粗糙。

### 容错策略

如果结构化失败，不会阻断流程，而是用原文生成一个降级版 `JobDescription`。

这是偏产品化的选择，不让用户因为一次解析失败卡死。

## 15. 简历导入与版本库

主要代码：

- `ResumeInputViewModel`
- `ResumeVersionRepository`
- `OriginalResumeFileStore`
- `FileParser`

### 简历输入支持

- 文本粘贴
- PDF
- DOCX
- 图片 OCR

### 原始文件为什么要单独保存

项目不是只保留解析后的文本，还保留原文件路径和 MIME。

这样做的价值是：

- 后续可以重新提取文本
- 可以打开原文件
- 可以知道当前版本是否真的来自文件导入
- 在结果页支持“原文件重新润色”

### 版本库为什么重要

很多求职产品只保留“当前简历”，但这个项目显然支持“一个人多版本简历”：

- 技术岗版
- 产品岗版
- 外企版
- 管理岗版

这就是 `ResumeVersion` 存在的原因。

## 16. 简历优化工作台

主要代码：

- `ResumeOptimizeViewModel`

这是一个很重的工作台式页面，不只是“调用 AI 润色一下”。

它承载了多种并列能力：

- AI 整体润色
- 匹配度分析
- 四维评分
- 技能缺口分析
- 量化改写建议
- STAR 格式化
- 版本保存 / 切换 / 对比

### 为什么这是独立模块

因为它更像“编辑器 + 诊断工具台”，和聊天页的交互模式不同。

### 几个关键能力

#### 量化助手

`QuantifyAssistant`

先用规则找模糊描述，再逐条调用 AI 建议量化表达。

这个设计很聪明，因为它不是把整份简历再喂一次模型，而是只打击薄弱点。

#### STAR 格式器

`StarFormatter`

把原始经历改成 STAR 结构，适合用于面试场景准备。

#### 技能缺口

`SkillGapAnalyzer`

能标记：

- 必须项
- 加分项
- 普通项

这比简单列缺词更接近真实求职决策。

#### 版本管理

可以保存标签、切换激活、做版本对比。

这使得项目不只是一次性工具，而是简历资产管理器。

## 17. 线性润色流程

主要代码：

- `PolishViewModel`
- `PolishRepository`
- `PolishResult`

### 流程

1. 接收 JD + Resume + 来源信息
2. 启动分步动画
3. 调 `polishResume`
4. 解析 LLM 输出
5. 跑 `SemanticMatcher`
6. 组装 `HistoryEntity`
7. 存库
8. 跳结果页

### `PolishResult` 为什么重要

它是“模型输出”和“应用内部结构”的转换层。

它优先解析 JSON，如果失败，再退回文本标记解析。

这个设计非常现实，因为真实模型输出不会永远规整。

### 为什么要单独有 `history`

因为一次润色不仅是文本结果，还包含：

- JD
- 优化说明
- 匹配分
- 关键词命中与缺失
- 原始来源文件

它本质上是一次完整求职操作的快照。

## 18. 结果页、结构化编辑与导出

主要代码：

- `ResultViewModel`
- `ResumeData`
- `HtmlPdfExporter`
- `ResumePreviewWebView`

### 结果页不是只读页

这是很多人第一次看代码会低估的地方。结果页承担了很多职责：

- 展示优化后的结果
- 展示匹配信息
- 结构化编辑个人信息、经历、教育、项目、技能
- 迭代润色
- 重新生成 PDF
- 生成求职信
- 分享导出文件

### 为什么要把文本转成 `ResumeData`

因为导出和字段编辑必须依赖结构化模型，纯文本没法稳定支持：

- 字段级修改
- 模板渲染
- 增删项目
- 头像 / 链接

### 迭代润色怎么做

结果页会把当前 `ResumeData` 序列化成 JSON，再带上用户指令调用 `polish_iterative`。

这说明它不是“重新全量润色”，而是“基于当前结构做定向编辑”。

### 求职信生成

通过 `CoverLetterRepository` 完成，支持中文英文 prompt。

### 模板

当前支持至少两种导出风格：

- Classic
- Vibe

`useVibeTemplate` 会影响最终 HTML 生成。

## 19. 模拟面试系统

主要代码：

- `InterviewViewModel`
- `InterviewRepository`
- `InterviewPrompts`

### 当前实现状态

从代码看，这个模块已经具备会话系统，但还不是完全由 LLM 驱动的深度面试代理。

它目前更像：

- 结构完整
- 持久化完整
- UI 较成熟
- 问题推进部分仍偏规则/模板

### 它做了什么

- 选择面试 persona
- 选择简历和 JD 上下文
- 启动面试会话
- 写入系统消息、面试官问题、用户回答
- 实时维护消息流和计时器

### persona

包括：

- 温和技术面
- 压力面
- 外企 HR
- 国企结构化
- 自定义

### 这个模块的好设计

不是简单一问一答，而是把“面试”建模成会话对象：

- Session
- Messages
- Persona
- QuestionCount
- ActiveSession

这使它后续可以自然接入：

- 语音识别
- 面试复盘
- 多轮追问
- 行为评分

## 20. 投递追踪系统

主要代码：

- `TrackingRepository`
- `TrackingViewModel`

### 状态流

默认状态包括：

- 已投
- 简历过筛
- 待面试
- 已面试
- 已 offer
- 已拒

### 时间线设计

每次状态变更都会往 `timeline` JSON 里追加记录。

这比只保存一个当前状态强很多，因为它允许：

- 回看状态演进
- 后续做时间线可视化
- 做投递分析统计

### 为什么它适合被 Agent 调用

因为投递操作天然是结构化动作：

- 新建投递
- 查最近投递
- 修改状态

这类操作非常适合作为工具，而不是纯文本回答。

## 21. JD 库、简历库与 BOSS 导入

### JD 库

相关代码：

- `JdLibraryRepository`
- `JdListViewModel`

JD 库的存在说明这个应用不是只处理“当前一个岗位”，而是允许形成岗位池。

### 简历库

相关代码：

- `ResumeLibraryViewModel`

简历库负责管理不同版本并支持选择、预览和回跳。

### BOSS 导入

相关代码：

- `BossImportController`
- `BossImportAccessibilityService`

这是项目里一个很有特点的能力：通过无障碍自动化从 BOSS 直聘导入岗位。

### 导入流程本质

1. 开启无障碍服务
2. 拉起 BOSS App
3. 自动定位搜索框
4. 搜索关键字
5. 进入岗位详情
6. 抽取文本
7. 存入 `jd_library`

### 为什么这样做

因为官方 API 不可控，而产品又需要快速形成“岗位池”。

这是一个很典型的移动端工程折中方案：用 Accessibility 自动化补齐生态缺口。

## 22. 文件解析与 OCR

核心代码：

- `FileParser`

### PDF

用 `pdfbox-android` 提取文本。

### DOCX

没有引入重量级 Word 库，而是：

- 直接把 docx 当 ZIP
- 找 `word/document.xml`
- 去 XML tag

这是为了降低依赖体积和复杂度。

### 图片

用 ML Kit 中文识别。

### 为什么统一走 `TextCleaner`

因为解析出来的文本天然很脏。无论 PDF、DOCX 还是 OCR，都必须进入统一清洗链路，否则后续：

- prompt 输入会变差
- 匹配精度会波动
- 结构化解析更容易失败

## 23. HTML/PDF 导出实现

核心代码：

- `HtmlPdfExporter`
- `android/print/PdfPrint.kt`

### 导出路线

`ResumeData -> HTML -> WebView -> PrintDocumentAdapter -> PDF`

### 为什么不直接画 PDF

因为简历天然是版式内容。HTML + CSS 的优势是：

- 好调样式
- 容易做多模板
- 可以复用 WebView 预览
- A4 分页与字体布局交给系统打印能力

### Vibe 模板说明了什么

这不是简单“换颜色”，而是专门对：

- 头像
- 联系方式
- 社交链接
- 教育网格
- 技能标签

做了定制渲染，说明导出系统是有产品心智的，而不是临时拼接。

## 24. 记忆系统与上下文沉淀

这里有两种“记忆”。

### 1. 结构化上下文

`AgentContextRepository`

保存在 DataStore 中，适合当前会话态：

- 当前 JD
- 当前简历
- 当前面试 session

### 2. 文件型长期记忆

`AgentWorkspace`

保存在应用内部文件夹里：

- `RESUME_MEMORY.md`
- `JD_MEMORY.md`
- `INTERVIEW_MEMORY.md`

### 为什么不用数据库统一做

因为文件型记忆有几个好处：

- 可读
- 易调试
- 轻量
- Prompt 注入直接

这是一种工程上很务实的做法。

### 记忆触发方式

当用户说“记住……”“以后都……”这类话时，系统会做显式记忆抽取并写入对应文件。

## 25. 设置与模型配置

相关代码：

- `SettingsViewModel`
- `ModelConfigViewModel`
- `SettingsRepository`
- `ProviderRepository`

### 两类配置

#### 基础设置

- API Key
- Base URL
- Model

#### 模型配置

- 当前 AI Provider
- Ollama 地址
- Ollama chat model
- Ollama embed model

### 为什么拆两层

因为用户面对的设置体验和内部 Provider 系统不是一回事：

- 一层给普通用户快速配通
- 一层给进阶用户切换 Provider

## 26. UI 组织方式

### 目录组织

大体遵守“一屏一模块”：

- `ui/jdinput`
- `ui/resumeinput`
- `ui/resumeoptimize`
- `ui/polish`
- `ui/result`
- `ui/interview`
- `ui/tracking`
- `ui/agent`

### 通用组件

在 `ui/components`：

- `LoadingOverlay`
- `ErrorBanner`
- `SectionCard`
- `ResumePreviewWebView`
- `ScoreRingChart`

### Agent 专属组件

在 `ui/agent`：

- 聊天气泡
- 卡片渲染
- 输入框
- 抽屉
- 过程状态条
- Markdown 渲染

这说明 Agent 已经形成独立 UI 子系统，而不是普通页面上的一个聊天框。

## 27. 测试与性能优化

### 单元测试

当前比较有代表性的测试有：

- `ToolProtocolTest`
- `UiCardPersistenceTest`

### 这说明什么

团队重点在保两件事：

1. 工具调用协议兼容性
2. Agent 卡片持久化稳定性

这很合理，因为 Agent 聊天一旦不能恢复，用户体验会很差。

### Baseline Profile

有单独模块：

- `baselineprofile`

采集启动和首屏滚动等路径，说明项目已经开始关注：

- 启动性能
- 首帧性能
- Compose 热路径

这通常意味着项目从 demo 正在往可交付产品走。

## 28. 项目里值得注意的设计取舍

### 1. 新旧流程并存

这不是坏事，而是迁移中的现实状态。Agent 正在成为主入口，但旧线性流程仍是稳定产出链。

### 2. 本地算法和云模型并存

说明目标不是“尽量像聊天机器人”，而是“尽量稳定完成求职任务”。

### 3. Room + DataStore + 文件系统三套存储并用

不是重复设计，而是各司其职：

- Room：业务记录
- DataStore：配置与轻上下文
- 文件系统：长文本记忆与原文件资产

### 4. UI 卡片协议化

这是整个 Agent 体系里最成熟的工程约束之一，能有效防止模型输出失控。

### 5. 结构化简历模型是中枢

`ResumeData` 几乎把：

- AI 输出
- 编辑器
- 预览
- 导出

都串起来了。它是这个项目非常关键的“中间表示”。

## 29. 当前已知不足与演进方向

结合代码和仓库现有规划文档，当前明显还在演进中的点有：

- Agent 入口很强，但部分业务仍依赖旧流程落地
- 面试系统会话层已完整，深度 AI 追问与评估还可以继续增强
- Provider 持久层已出现，但完整的多 Provider UI 管理还在发展
- 一些页面文件较大，后续还可以继续拆分
- 文档体系有历史内容，但部分文档已滞后于当前实现

如果继续往前做，最自然的方向会是：

- 进一步统一 Agent 与页面业务链
- 把面试复盘做深
- 把岗位池与投递分析做成真正的决策系统
- 完善多模型 / 多 Provider 配置体验

## 30. 如果你要继续开发，建议先看哪里

### 第一轮阅读顺序

1. `MainActivity.kt`
2. `NavGraph.kt`
3. `AgentUseCase.kt`
4. `AgentToolCoordinator.kt`
5. `AgentViewModel.kt`
6. `ResumeOptimizeViewModel.kt`
7. `ResultViewModel.kt`
8. `AppDatabase.kt`
9. `AiProviderManager.kt`
10. `PromptRegistry.kt`

### 如果你要改 Agent

先看：

- `AgentUseCase`
- `AgentToolCoordinator`
- `AgentViewModel`
- `AgentOutput`
- `UiCardSnapshotCodec`

### 如果你要改简历链路

先看：

- `ResumeInputViewModel`
- `PolishViewModel`
- `ResultViewModel`
- `ResumeData`
- `HtmlPdfExporter`

### 如果你要改匹配算法

先看：

- `NlpEngine`
- `MatchAnalysisUseCase`
- `MatchScoreDetailUseCase`
- `SkillGapAnalyzer`
- `SemanticMatcher`

### 如果你要改面试

先看：

- `InterviewViewModel`
- `InterviewRepository`
- `InterviewPrompts`

### 如果你要改投递

先看：

- `TrackingRepository`
- `TrackingViewModel`
- `AgentToolCoordinator` 里与 tracking 相关的工具逻辑

---

## 31. 本地开发与首次运行

这一节的目标是让接手者能在不猜配置的前提下，把工程跑起来并判断“功能不可用”究竟是代码问题还是环境问题。

### 当前构建基线

以仓库配置为准：

- Gradle Wrapper：`9.5.0`
- Android Gradle Plugin：`9.2.1`
- Kotlin：`2.2.10`
- `compileSdk / targetSdk`：`35`
- `minSdk`：`24`
- Java 源码兼容级别：`11`
- 模块：`app` 与 `baselineprofile`

项目没有在 Gradle 中声明 Java Toolchain。因此“源码按 Java 11 编译”不等于团队机器一定只需安装 JDK 11；实际 IDE/JDK 组合必须同时满足当前 Android Gradle Plugin 的要求。接手时应先统一团队 Android Studio 与 JDK 版本，再处理业务问题，避免把构建环境错误误判成代码错误。

### 第一次启动的推荐顺序

1. 用 Android Studio 打开仓库根目录，让它使用仓库自带的 Gradle Wrapper 同步依赖。
2. 选择 `app` 的 debug 变体，在模拟器或真机安装运行。
3. 进入设置页，选择一种 AI 运行方式并完成配置。
4. 先输入一小段 JD 和简历文本，验证 Agent 或润色链路。
5. 最后再测试 PDF、DOCX、OCR、相机、语音和 BOSS 导入等依赖设备能力的功能。

常用命令（Windows PowerShell）如下：

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

第三条需要已连接且可用的 Android 设备或模拟器。构建产物默认位于 `app/build/outputs/apk/` 下，不要将该目录作为源码或长期交付物提交。

### 三种 AI 运行模式

| 模式 | 必需配置 | 能做什么 | 常见误区 |
| --- | --- | --- | --- |
| DeepSeek | API Key、Base URL、模型名 | 聊天、Agent、JD 提取、简历生成等云端能力 | 默认模型配置不等于已有可用 Key |
| Ollama | Ollama 地址、chat model，语义功能还需要 embedding model | 本地或局域网聊天与向量能力 | Android 模拟器访问电脑上的 Ollama 通常用 `10.0.2.2:11434`；真机需要可访问的局域网地址 |
| Local | 本地 TFLite embedding 模型 | 语义匹配和 embedding 兜底 | `LocalProvider` 不支持聊天，不能独立驱动 Agent |

默认配置是 DeepSeek；没有填写 API Key 时，流式聊天会尝试使用已配置的 Ollama 地址。若两者都不可用，应用会给出“没有可用的 AI 服务”类错误。测试离线能力时，应区分“匹配算法可离线”与“生成式聊天可离线”这两个概念。

### 真机功能前置条件

- 图片文字识别、面试相机等路径需要授予相机权限。
- 语音输入需要录音权限，并依赖系统语音识别服务。
- 文件导入依赖系统文件选择器；导出/分享通过 `FileProvider` 暴露临时文件 URI。
- BOSS 导入需要用户主动在系统设置中开启无障碍服务，并且手机已安装 BOSS 直聘；它对目标 App 的页面结构有依赖，不能按普通网络接口理解。

## 32. 功能调用链与扩展点地图

读源码时不要只从 Screen 往下翻。下面的地图按“想改什么”反向给出应该同时检查的边界，避免只改到 UI 或只改到数据层。

| 目标 | 主调用链 | 通常还要同步修改的点 |
| --- | --- | --- |
| 新增普通页面 | `NavGraph -> Screen -> ViewModel -> Repository / UseCase` | route 参数编码、返回栈、加载/错误/空态、Hilt 注入 |
| 新增 Agent 工具 | `AgentUseCase -> AgentToolCoordinator -> Repository / UseCase -> UiCard -> AgentViewModel` | 本轮可用工具策略、参数 schema、重复调用保护、卡片渲染、卡片快照编解码、协议测试 |
| 新增 AI Provider | `AiProvider -> Provider 实现 -> AiProviderManager -> 设置页` | 可用性判断、流式实现、认证头、模型列表、失败回退和隐私说明 |
| 扩展简历字段 | `ResumeData -> 解析/编辑 -> ResultViewModel -> HtmlPdfExporter` | JSON 向后兼容、预览、两套模板、历史快照与导出验证 |
| 新增持久化字段或表 | `Entity -> Dao -> Repository -> AppDatabase -> AppModule` | 数据库版本号、逐版本 Migration、旧数据默认值、升级测试 |
| 调整匹配评分 | `NlpEngine / SemanticMatcher -> MatchAnalysisUseCase -> ViewModel / UiCard` | 分数解释文案、离线结果、LLM 增强结果及回归样本 |

### Agent 新工具的最小闭环

Agent 是最容易出现“模型能说、应用做不了”问题的模块。新增能力时，应至少完成这条闭环：

```text
用户意图
  -> buildTurnPolicy 决定本轮是否可调用该工具
  -> 工具 schema 描述必填参数和可选参数
  -> AgentToolCoordinator 校验参数并执行领域动作
  -> 返回受控文本或 UiCard
  -> AgentViewModel 展示并保存草稿快照
  -> 单元测试覆盖协议与快照兼容性
```

不要让模型直接返回“某个 Compose 组件应该长什么样”。页面能力必须被编码为受控的 `UiCard` 类型和字段；否则模型输出变化会直接变成客户端兼容性事故。

## 33. AI 接口、流式协议与降级行为

### 接口边界

非流式请求通过 `AiProvider` 抽象访问，`DeepSeekProvider` 与 `OllamaProvider` 分别负责各自协议细节，`AiProviderManager` 负责选择与回退。上层业务应依赖这层抽象，而不应该在 ViewModel 中直接拼 HTTP 请求。

Agent 的流式路径有一处需要特别注意：`AiProviderManager.chatStream()` 会根据当前配置直接走 OpenAI 兼容流或 Ollama 流。新增 Provider 时，如果只实现了 `AiProvider.chatCompletion()` 而没有补上对应流式路径，普通请求可能可用，但 Agent 的逐字输出仍会失败。

### 模型输出契约

项目同时接受三类模型结果：

- 普通文本：用于解释、追问和非结构化建议。
- 工具调用：模型通过受限的 function schema 选择业务动作，参数由 `AgentToolCoordinator` 执行。
- 结构化 JSON：用于 JD 提取、简历润色、评分等需要转换成领域模型的结果。

`prompts.json` 负责 Prompt 模板和生成参数；调用方负责提供模板变量；领域模型负责验证/解析。修改 Prompt 时必须同时查看解析代码，尤其不要无意改掉 JSON 字段名、数组结构或代码块边界。

### 当前代码中的主要降级

| 场景 | 当前行为 | 接手时要注意 |
| --- | --- | --- |
| DeepSeek 不可用 | 非流式管理器尝试 Ollama | 需要确认 Ollama 已配置且可访问 |
| Ollama 不可用 | 非流式管理器尝试 DeepSeek | DeepSeek 的可用性取决于 API Key |
| 所有聊天 Provider 不可用 | 抛出异常并由上层展示错误 | 本地 embedding 不能替代聊天能力 |
| embedding 初始化或调用失败 | 匹配链路可退回传统 TF-IDF | 结果质量会下降，但不应阻塞核心评分 |
| 模型返回非预期 JSON | `PolishResult` 等解析层尝试文本型兼容解析或返回失败 | 不要假设 LLM 永远严格遵循 Prompt |
| Prompt 资源读取失败 | `PromptRegistry` 使用内置 fallback | fallback 是否仍符合当前领域模型需要人工核验 |

网络层的 OkHttp 日志级别是 `BASIC`，并且显式脱敏 `Authorization` 请求头，避免把完整 JD、简历正文和 Key 写进普通网络日志。这个约束应被保留；排障时优先使用脱敏摘要、请求 ID 或本地可控的测试数据。

## 34. 数据库迁移、数据生命周期与备份

### 数据关系的真实形态

数据库版本当前为 `13`。它既有 Room 外键（如面试消息关联面试会话、Provider 模型关联 Provider），也有很多“逻辑关联”（例如投递记录中的简历版本 ID）。因此删除或改造简历版本时，不能只看数据库约束，还要检查业务层是否仍引用该版本。

数据大致分为四类：

- 业务历史：`history`、`tracking`、`interview_sessions`、`interview_messages`、`jd_library`。
- 可复用资产：`resume_versions` 及其原始文件路径。
- 配置与短期上下文：DataStore 中的 Provider 配置、当前 JD、Agent 草稿、最近简历等。
- 文件型资产：原始简历文件与 Agent memory Markdown 文件。

这四类数据并不共享一套“统一删除”机制。若产品要提供“清除我的数据”或账号注销能力，必须分别清理 Room、DataStore、内部文件和通过 `FileProvider` 生成的临时导出文件。

### Migration 规则

每次改 `@Entity` 或新建表时，以下动作必须一起完成：

1. 增加 `AppDatabase` 版本号。
2. 编写从上一个已发布版本到新版本的 Migration，并给旧字段提供安全默认值。
3. 在 `AppModule.provideAppDatabase()` 的 `addMigrations()` 中注册它。
4. 用包含旧数据的测试库或真实升级包验证升级，而不是只验证全新安装。

当前代码注册了 `3->4`、`4->5`、`6->7`、`8->9`、`9->10`、`10->11`、`11->12`、`12->13` 等迁移，但没有看到 `5->6` 与 `7->8` 的 Migration，也没有启用 destructive migration。若这些中间版本曾正式发布，用户从对应版本直接升级可能触发 Room 的迁移链错误；在发布下一版前应以历史 APK/数据库进行专项验证，确认这些版本是否只存在于开发阶段。

### 备份和敏感数据

Manifest 当前开启了 `allowBackup`，但 `backup_rules.xml` 和 `data_extraction_rules.xml` 仍是 Android 模板，未明确排除 Key、简历、JD、数据库或 Agent 记忆。与此同时，API Key 同时存在于 DataStore 和 Provider 数据表的普通字符串字段中，代码里没有使用加密存储。

这不是文档措辞问题，而是当前实现的安全边界：不要对用户宣称“密钥已加密”或“简历不会被系统备份”。正式发布前应至少明确备份策略，排除 API Key 和敏感业务数据，评估 Android Keystore/Encrypted DataStore 等受保护存储方案，并补齐数据删除入口。

## 35. 权限、隐私与安全边界

### 权限清单

| 权限/能力 | 使用场景 | 用户可见影响 |
| --- | --- | --- |
| `INTERNET` | DeepSeek、Ollama 局域网调用、模型列表等 | 使用云端 Provider 时，简历/JD/对话内容会发送至用户选择的服务端 |
| `CAMERA` | 图片识别、面试相机相关 UI | 用户可拒绝；拒绝后应保留文本输入等替代路径 |
| `RECORD_AUDIO` | 语音输入 | 依赖系统语音识别服务，设备或 ROM 不支持时应允许继续手工输入 |
| 无障碍服务 | BOSS 岗位自动导入 | 用户需在系统设置中显式开启；该能力会读取目标 App 当前可访问页面的文本 |
| `FileProvider` | 分享导出的 PDF 等文件 | 应只授予临时 URI 访问权限，避免暴露内部文件路径 |

### 数据流说明

默认 DeepSeek 模式下，JD、简历、聊天内容或工具参数可能作为 Prompt 发送到配置的 Base URL。Ollama 模式下数据发送到用户填写的本地或局域网地址；“Local”模式仅提供本地 embedding，不承担聊天生成。用户切换 Base URL 时，数据处理方也随之改变，因此设置页和隐私说明应明确提示这一点。

### BOSS 导入的边界

BOSS 自动导入不是官方开放 API 集成，而是基于无障碍节点、文本特征、页面结构和手势的自动化。它天然容易受目标 App 版本、页面 A/B、登录状态和反自动化策略影响。维护时应做到：

- 只在用户明确发起导入后启动会话。
- 清楚展示导入进度、失败原因和停止入口。
- 不把目标 App 的 UI 结构当作稳定协议。
- 在发布前复核目标平台规则、用户授权提示及适用法律要求。

## 36. 测试、验收与排障手册

### 现有自动化测试覆盖

当前 unit test 的重点是 Agent 协议兼容性：

- `ToolProtocolTest`：验证 OpenAI 兼容工具调用响应的反序列化，以及工具定义和 `tool_choice=auto` 的序列化。
- `UiCardPersistenceTest`：验证全部主要卡片类型的快照往返、未知 schema/损坏数据的忽略，以及旧草稿 JSON 的兼容恢复。

instrumentation test 目前只有模板级 `ExampleInstrumentedTest`。这意味着导航、权限、Room 升级、真实文件解析、导出和 Provider 流式响应尚未被自动化端到端覆盖。不要把“单元测试通过”理解成“应用主流程已验收”。

### 每次重要改动后的手工验收

至少覆盖以下场景：

1. 新装应用：配置 DeepSeek 或 Ollama 后完成一次 JD + 简历分析。
2. 冷启动恢复：发送含卡片的 Agent 对话，杀进程后检查草稿和卡片是否恢复。
3. Provider 故障：故意填错 Key 或断网，确认错误信息可理解且不会卡在加载态。
4. 文件兼容：分别导入 PDF、DOCX、图片和纯文本，检查解析失败是否可继续编辑。
5. 数据升级：保留旧版本生成的数据库后升级安装，检查历史、简历、投递和面试记录。
6. 导出：编辑 `ResumeData` 字段后分别预览并导出 Classic/Vibe 模板，检查分页、中文、链接和分享。
7. 权限：拒绝相机/录音权限后，确认非授权功能仍可用并可再次请求。
8. BOSS 导入：在不同页面状态下测试开始、手动接管、停止、重复导入和失败提示。

### 排障入口

网络问题优先检查当前 Provider、Base URL、API Key、模拟器/真机到 Ollama 的可达性；数据问题优先检查 Entity、数据库版本和 Migration 注册；Agent 问题优先检查本轮工具策略、工具 schema、`AgentToolCoordinator` 与 `UiCardSnapshotCodec`。

仓库里有 `CrashLogger`，它能把未捕获异常保存到应用内部 `crash_logs` 目录并保留最近五份；但当前 `TieLinkApp` 没有调用 `CrashLogger.init()`。换言之，这个排障能力目前并未在应用启动时启用，不能指望线上自动留下该类日志。若启用它，还应同步评估崩溃日志中可能出现的个人信息和安全存储策略。

## 37. 构建、发布与性能交付

### 当前发布状态

目前只定义了标准 `debug` / `release` 构建类型；`release` 的 `minifyEnabled` 为 `false`。工程没有看到签名配置、环境变量注入、CI 工作流、Fastlane 或自动发布配置，版本号仍为 `versionCode = 1`、`versionName = "1.0"`。

因此当前仓库适合本地开发和手工构建，但还没有形成可重复的正式发布流水线。准备上架或分发前，至少要补齐：

- release keystore 的安全管理与签名配置。
- 版本号递增规则与变更记录。
- CI 中的编译、unit test、lint、产物归档和可选的 instrumentation test。
- 是否开启 R8/资源压缩及其回归验证。
- 生产环境的崩溃、性能和隐私合规观测方案。

### Baseline Profile

项目已有独立 `baselineprofile` 模块，并通过 `ProfileInstaller` 将基线配置随 APK 安装。这为启动和常用 Compose 路径优化提供了基础，但“模块存在”不代表 profile 永远新鲜。凡是改动冷启动入口、主导航、Agent 首屏或高频列表后，都应重新采集/验证对应任务；具体可用 Gradle 任务以本机 `:baselineprofile:tasks --all` 的输出为准。

## 38. 文档维护约定

这份文档以当前源码为准，而不是永久正确的产品说明。每次合并涉及下列内容的改动时，应同步更新相应章节：

- 新增/删除页面、route 或导航参数：更新第 7、26、32 节。
- 调整 Agent 工具、卡片或会话格式：更新第 13、32、33、36 节。
- 调整 Provider、Prompt 或模型协议：更新第 10、11、25、33、35 节。
- 修改 Entity、Migration、DataStore key、文件存储或备份规则：更新第 9、24、34、35 节。
- 修改构建、签名、测试、性能或发布流程：更新第 27、36、37 节。

建议每次发版前在文档顶部记录：核验日期、对应的 Git commit、APK 版本号，以及已知未验证的设备/Provider 组合。这样读者能区分“代码事实”“已验证行为”和“待办风险”，文档才不会随着项目演进重新变成历史材料。

---

## 结论

TieLink 当前已经不是一个简单的“AI 改简历 App”，而是一个以求职闭环为目标的 Android 工作台。

它最成熟的部分有三块：

- 简历结构化与结果导出链
- Agent 工具调用与卡片化交互链
- 本地存储与上下文沉淀体系

它最有潜力继续放大的部分也有三块：

- 面试深度能力
- 岗位池到投递决策闭环
- 多 Provider / 本地模型的灵活 AI 基础设施

如果把这个项目比作一个人，现在它已经不是原型期，而是进入了“架构已成型、能力在扩张、需要持续整理与收束”的阶段。对接手开发的人来说，最重要的不是再从零理解业务，而是先抓住这几个中枢：

- `AgentUseCase`
- `ResumeData`
- `AppDatabase`
- `AiProviderManager`
- `ResultViewModel`

抓住这几个点，整个项目基本就能顺下来。
