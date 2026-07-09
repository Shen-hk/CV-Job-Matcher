# TieLink 技术方案文档

## 项目概述

**TieLink** 是一款基于 Jetpack Compose 的 Android 求职助手应用。核心能力：AI 驱动的简历优化、多角色模拟面试、投递追踪、语义匹配分析、HTML/PDF 导出、求职信生成、Agent 记忆系统。支持中文内容处理，双 AI 后端（DeepSeek API + 本地 Ollama）。

| 属性 | 值 |
|------|-----|
| Min SDK | 24 |
| Target SDK | 35 |
| 架构 | MVVM + Clean Architecture |
| DI | Dagger Hilt + KSP |
| UI | Jetpack Compose + Material 3 |
| 导航 | Jetpack Navigation Compose |
| 语言 | Kotlin 2.2.10 |

---

## 1. 架构总览

```
┌─────────────────────────────────────────────────┐
│  UI Layer (Compose Screens + ViewModels)         │
│  ui/home/  ui/result/  ui/interview/  ...       │
├─────────────────────────────────────────────────┤
│  Domain Layer (Pure Kotlin)                      │
│  domain/model/  domain/nlp/  domain/usecase/     │
├─────────────────────────────────────────────────┤
│  Data Layer                                      │
│  data/repository/  data/remote/  data/local/     │
└─────────────────────────────────────────────────┘
```

### 数据流（以简历优化为例）

```
JdInputScreen → JdInputViewModel → JdRepository → DeepSeekApiService
       │
       ▼  JD JSON 存入 GlobalJdStateHolder
       │
ResumeInputScreen → FileParser (PDF/DOCX/OCR)
       │
       ▼
PolishScreen → PolishViewModel → PolishRepository → AiProviderManager
                                                         │
                                                         ▼
                                              DeepSeekProvider / OllamaProvider
       │
       ▼
PolishResult.fromLlmOutput() → SemanticMatcher.analyze()
       │
       ▼
HistoryEntity 写入 Room → 导航到 ResultScreen
```

### 数据流（Agent 记忆系统）

```
AgentWorkspace (file-based memory)
  ├── agent_workspace/memory/RESUME_MEMORY.md    ← appendResumeMemory()
  ├── agent_workspace/memory/JD_MEMORY.md        ← appendJdMemory()
  └── agent_workspace/memory/INTERVIEW_MEMORY.md ← appendInterviewMemory()

写入触发: 用户说"记住..." → extractExplicitMemory() → appendXxxMemory()
读取触发: AI 请求构建时 → buildResumeContext() / buildJdContext() / buildInterviewContext()
```

---

## 2. 导航与路由

**文件**: `navigation/NavGraph.kt`

### 新版并行工作台

| 路由 | 屏幕 | 说明 |
|------|------|------|
| `home` | HomeScreen | 主面板，三个功能入口卡 + JD优化区 |
| `resume_optimize` | ResumeOptimizeScreen | 简历编辑器 + 匹配分析 + AI润色触发 |
| `mock_interview` | InterviewScreen | AI模拟面试聊天，5种面试官角色 |
| `tracking` | TrackingScreen | 投递进度追踪，6阶段状态管道 |

### 旧版线性流程（兼容保留）

| 路由 | 屏幕 | 说明 |
|------|------|------|
| `jd_input` | JdInputScreen | JD文本输入 + OCR图片识别 |
| `resume_input/{...}` | ResumeInputScreen | 简历输入/文件上传 + 匹配分析弹窗 |
| `polish/{...}` | PolishScreen | 动画处理进度屏（8步动画） |
| `result/{sessionId}` | ResultScreen | 结果查看 + HTML预览 + 编辑 + AI聊天 + PDF导出 |
| `history` | HistoryScreen | 润色历史列表 |
| `settings` | SettingsScreen | API Key / 模型 / Base URL / Ollama 配置 |

### JD 优化流程

| 路由 | 屏幕 | 说明 |
|------|------|------|
| `jd_optimize_jd_input` | JdInputScreen | JD输入（flowMode=jd_optimize） |
| `jd_optimize_resume_input/{...}` | ResumeInputScreen | 简历选择 + 匹配确认（含历史版本选择器） |

**起始路由**: `HOME`

**全局状态**: `GlobalJdStateHolder`（Hilt @Singleton）注入到 MainActivity，通过 `CompositionLocalProvider` 暴露给所有屏幕。

---

## 3. 功能模块技术方案

### 3.1 简历优化（JD 靶向润色）

**涉及文件**: `PolishRepository`, `PolishViewModel`, `PolishResult`, `SemanticMatcher`

**流程**:
1. 用户输入 JD + 简历文本
2. `PolishRepository.polishResume()` 构建 `LlmRequest`，通过 `AiProviderManager.chatWithFallback()` 调用 AI
3. AI 返回原始文本 → `PolishResult.fromLlmOutput()` 解析（优先 JSON，降级文本标记解析）
4. `SemanticMatcher.analyze()` 计算混合匹配分
5. `HistoryEntity` 写入 Room，导航到 ResultScreen

**评分算法**:
- 60% 关键词匹配率 + 40% TF-IDF 余弦相似度（本地）
- 若有 LLM 评分：40% 本地 + 60% LLM 加权

### 3.2 JD 优化独立流程

**区别**: `flowMode = "jd_optimize"` 模式下，ResumeInputScreen 显示历史版本选择器 + 润色前匹配分析弹窗，用户可先评估再决定是否优化。

### 3.3 AI 模拟面试

**涉及文件**: `InterviewScreen`, `InterviewViewModel`, `InterviewRepository`, `InterviewPrompts`

**5种面试官角色**:
| 角色 | 说明 |
|------|------|
| 温和技术官 | 友好引导式提问 |
| 压力面考官 | 高压追问连击 |
| 外企HR | Behavioral / STAR 面试风格 |
| 国企结构化 | 标准化结构化面试 |
| 自定义 | 用户自定义面试偏好 |

**技术实现**:
- 聊天界面: `LazyColumn` + `ChatBubble` 组件，USER/AI/SYSTEM 三种角色
- 上下文窗口: 最近10条消息
- 提示/跳过: 工具按钮触发独立 AI 请求
- 结束面试: AI 评估生成 `InterviewResult`（总分 + 维度分 + 改进建议）
- 持久化: `InterviewSessionEntity` + `InterviewMessageEntity`（级联删除）

### 3.4 投递追踪

**涉及文件**: `TrackingScreen`, `TrackingViewModel`, `TrackingRepository`

**6阶段状态管道**: 已投 → 简历过筛 → 待面试 → 已面试 → 已offer → 已拒

**功能**:
- 状态筛选芯片
- FAB 新建投递（公司 + 职位 + 状态）
- 下拉切换状态
- 时间线可视化（`timeline` JSON 数组记录每次状态变更）
- 删除投递条目

### 3.5 AI 聊天助手（侧栏）

**涉及文件**: `ResultScreen.kt` → `SmartSidebar` → `AiChatPanel`, `ResultViewModel`

**技术实现**:
- 用户输入自然语言指令 → `sendAiChatMessage()`
- 将当前 `ResumeData` 序列化为 JSON 发送给 `PolishRepository.iterativePolish()`
- AI 返回更新后的 JSON → 解析为 `ResumeData` → 更新 UI
- 聊天消息列表 + 加载状态 + 发送按钮
- 完成后触发 Konfetti 撒花动画

**状态字段**: `aiChatMessages: List<ChatMessage>`, `isAiProcessing: Boolean`, `showCompletionAnimation: Boolean`

### 3.6 求职信生成

**涉及文件**: `CoverLetterRepository`, `ResultViewModel`

**技术实现**:
- `CoverLetterRepository.generateCoverLetter()` 构建中/英文求职信请求
- Prompt 配置: `cover_letter_zh` / `cover_letter_en`
- 模板建议: `generateTemplateSuggestions()` 根据 JD 关键词推荐模板风格
- 通过 `AiProviderManager.chatWithFallback()` 调用 AI

### 3.7 HTML 简历预览与 PDF 导出

**涉及文件**: `ResumePreviewWebView`, `HtmlPdfExporter`

**ResumePreviewWebView**:
- Compose WebView 实时渲染 HTML
- 两种模板风格: 经典 / Vibe
- 全屏 Dialog 预览
- 数据变化时自动重载（`remember(resumeData, useVibeTemplate)`）

**HtmlPdfExporter**:
- `buildHtml()`: 生成完整 HTML + 内嵌 CSS
- `buildVibeHtml()`: Vibe 模板专用，支持头像、联系图标、教育网格、经验高亮、证书标签
- `exportPdf()`: `WebView.createPrintDocumentAdapter()` + `PdfPrint` 输出 A4 彩色 PDF

**ResumeData 结构化模型**:
- `ResumeData` 包含: name, targetPosition, contact, summary, experiences, education, projects, skills, certifications, languages, photoBase64, links
- `ResumeData.fromPolishedText()`: 从 AI 润色文本解析结构化数据
- `ResumeData.fromJsonString()`: 从 JSON 反序列化
- `withAutoDetectedLinks()`: 自动检测个人链接（GitHub/LinkedIn/博客），跳过项目仓库地址

### 3.8 文件解析

**涉及文件**: `util/FileParser.kt`

| 格式 | 依赖 | 方式 |
|------|------|------|
| PDF | pdfbox-android | `PDDocument.load()` + `PDFTextStripper` |
| DOCX | 无 | 直接 ZIP 解压 + XML 标签剥离 + HTML 实体解码 |
| 图片 OCR | ML Kit Chinese | `InputImage.fromFilePath()` + `TextRecognition` |

### 3.9 NLP 语义匹配

**涉及文件**: `NlpEngine`, `EmbeddingEngine`, `SemanticMatcher`, `KeywordClassifier`

**双层匹配**:

| 层级 | 引擎 | 说明 |
|------|------|------|
| TF-IDF | NlpEngine（纯 Kotlin） | CJK 字符+双字分词，平滑 IDF，余弦相似度 |
| 神经网络 | EmbeddingEngine（TFLite） | 768 维中文 BERT 量化模型，128 token 序列 |

**关键词分类**: `KeywordClassifier` 加载 `skill_dict.json`，构建反向索引 O(1) 查找，支持模糊降级匹配。

**混合评分**: `MatchAnalysisUseCase` 编排 60% 关键词 + 40% TF-IDF，可选 LLM 评分加权（40% 本地 + 60% LLM）。

**分维度评分**: `MatchAnalysis` v2 支持 keywordCoverage / skillFit / experienceRelevance / educationMatch 四维度 + SkillGap 缺失技能详情。

### 3.10 Agent 记忆系统

**涉及文件**: `util/AgentWorkspace.kt`

**设计**: 基于文件的轻量记忆系统，为后续 Agent 对话框架提供上下文持久化。

**三个记忆域**:
| 记忆文件 | 写入场景 | 读取场景 |
|----------|---------|---------|
| `RESUME_MEMORY.md` | 用户说"记住我的工作经历..." | 简历润色时注入 prompt |
| `JD_MEMORY.md` | 用户说"记住我偏好XX职位" | 岗位匹配时优先考虑 |
| `INTERVIEW_MEMORY.md` | 用户说"记住我XX方面薄弱" | 模拟面试时针对性提问 |

**关键方法**:
- `extractExplicitMemory(userMessage)`: 从自然语言提取记忆内容，支持"记住/保存/以后都"等触发词，排除否定指令
- `appendXxxMemory(context, memory)`: 追加式写入，自动去重 + 时间戳
- `buildXxxContext(context)`: 读取记忆内容供 AI prompt 使用

---

## 4. AI 后端架构

### 4.1 抽象层

```
AiProvider (interface)
├── DeepSeekProvider  → DeepSeekApiService (Retrofit + SSE 流式)
├── OllamaProvider    → OkHttp (raw API calls)
└── LocalProvider     → EmbeddingEngine (TFLite, embed only)
```

### 4.2 AiProviderManager

- 优先级: `DEEPSEEK > OLLAMA > LOCAL`
- `chatWithFallback()`: 按优先级尝试，全部失败则抛异常
- `smartEmbed()`: 优先云端 embedding，降级本地 TFLite

### 4.3 API 认证

`ApiKeyInterceptor` 通过 OkHttp Interceptor 动态注入 `Authorization: Bearer <key>`，key 通过 `AppPreferences` 的 volatile 缓存同步读取。

### 4.4 Prompt 管理

`PromptRegistry` 从 `assets/prompts.json` 加载配置，包含所有 Prompt 模板的 system prompt、temperature、maxTokens。内置硬编码降级。

Prompt 类型: `polish_full`, `polish_partial`, `polish_iterative`, `cover_letter_zh/en`, `interview_*`（5种角色 + follow_up + evaluation）, `resume_quantify`, `resume_star_format`, `match_score_detail`

---

## 5. 数据持久化

### 5.1 Room 数据库 (v6)

| 表 | 说明 | 关键字段 |
|----|------|----------|
| `history` | 润色历史 | JD文本, 原始/润色简历, JSON, 技能, 匹配分数, 来源类型, 模板风格 |
| `resume_versions` | 简历版本 | 名称, 文本, 标签(JSON), 激活状态, 匹配分数 |
| `tracking` | 投递追踪 | 公司, 职位, 状态, 简历版本FK, JD文本, 时间线JSON |
| `interview_sessions` | 面试会话 | 角色类型, JD, 简历, 问题数, 总分, 维度分(JSON), 改进建议 |
| `interview_messages` | 面试消息 | 会话FK, 角色, 内容, 提示/评估标记, 级联删除 |

**Migration 历史**: 3→4 (history 加 resume_json 列), 4→5 (新增 4 张表 + 索引), 5→6 (fallbackToDestructiveMigration)

### 5.2 DataStore Preferences

| Key | 说明 |
|-----|------|
| `deepseek_api_key` | API Key（volatile 缓存） |
| `llm_model` | 模型名称 |
| `llm_base_url` | API 基础 URL |
| `ollama_base_url` | Ollama 服务地址 |
| `ollama_model` | Ollama 模型名称 |
| `ollama_embed_model` | Ollama Embedding 模型 |
| `ai_provider` | 当前 AI 后端选择 (deepseek/ollama/local) |
| `pdf_template` | PDF 导出模板 |
| `app_language` | 语言设置 (zh/en) |
| `cached_jd_raw/json/company` | JD 本地缓存 |
| `last_resume` | 最后使用的简历文本 |
| `has_seen_onboarding` | 引导页状态 |
| `last_interview_persona` | 上次面试官角色偏好 |

---

## 6. 关键技术决策

| 决策 | 选择 | 原因 |
|------|------|------|
| 注解处理 | KSP（非 KAPT） | 编译速度更快 |
~~| JSON | Moshi + KotlinJsonAdapterFactory | 反射模式，无需代码生成 |~~
| HTML 模板 | 原始 HTML + 占位符替换 | 无额外模板引擎依赖 |
| TF-IDF | 手写纯 Kotlin | 离线可用、可解释、无外部依赖 |
| Embedding | TFLite 量化中文 BERT | 本地推理，隐私保护 |
| Room 迁移 | `fallbackToDestructiveMigration(true)` | 开发阶段优先迭代速度 |
| 混淆 | 关闭 | 简化调试 |
| 动画 | Konfetti 库 | 成熟稳定的撒花粒子系统 |
| Agent 记忆 | 文件系统（非数据库） | 轻量、可读、易调试，后续可迁移至 Room |

---

## 7. 外部依赖

| 类别 | 库 | 版本 | 用途 |
|------|-----|------|------|
| UI | Compose BOM | 2026.02.01 | Material3 + IconsExtended |
| DI | Dagger Hilt | 2.59.2 | 依赖注入 |
| 网络 | Retrofit + OkHttp + Moshi | 2.11.0 / 4.12.0 / 1.15.1 | API 调用 |
| 数据库 | Room | 2.8.4 | 本地持久化 |
| 存储 | DataStore | 1.1.1 | Key-Value 配置 |
| 导航 | Navigation Compose | 2.8.4 | 屏幕导航 |
| AI/ML | TensorFlow Lite | 2.16.1 | 本地语义嵌入 |
| OCR | ML Kit Chinese | 16.0.1 | JD 图片文字识别 |
| PDF | PDFBox Android | 2.0.27.0 | PDF 文本提取 |
| 动画 | Konfetti | 2.0.5 | 撒花庆祝效果 |
| 协程 | kotlinx-coroutines | 1.9.0 | 异步操作 |
