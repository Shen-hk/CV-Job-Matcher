# TieLink 项目框架结构文档

> **生成日期**: 2026-06-25  
> **用途**: 供后续 AI 快速了解项目结构，避免重复读取大量文件  
> **更新方式**: 项目结构发生重大变更时重新生成

---

## 一、项目概览

| 属性 | 值 |
|------|-----|
| 应用名 | TieLink  |
| 包名 | `com.example.tielink` |
| 语言 | Kotlin |
| UI框架 | Jetpack Compose (无XML布局) |
| 架构 | Clean Architecture + MVVM |
| DI | Hilt (KSP, 非KAPT) |
| 数据库 | Room (v6, `tielink.db`) |
| 网络 | Retrofit + OkHttp (30s/60s/30s超时) |
| 序列化 | Moshi (reflective, KotlinJsonAdapterFactory) |
| 构建 | Gradle Kotlin DSL + Version Catalog (`libs.versions.toml`) |
| 最低SDK | 26 (估计) |

---

## 二、完整目录结构

```
D:\Project\Android\TieLink\
├── build.gradle.kts                    # 项目级构建
├── settings.gradle.kts
├── gradle.properties
├── local.properties
├── gradle/
│   ├── libs.versions.toml              # 版本目录
│   └── wrapper/
├── gradlew / gradlew.bat
├── CLAUDE.md                           # Claude Code 项目指南
├── CHANGELOG.md
├── docs/
│   ├── PROJECT_FRAMEWORK.md            # 【本文档】
│   ├── EMBEDDING_MODEL_GUIDE.md
│   ├── PRD_AGENT.md
│   ├── TECHNICAL_DESIGN.md
│   ├── UNFINISHED_REQUIREMENTS.md
│   └── UPGRADE_GUIDE.md
├── sample_classic_resume.html
├── sample_vibe_resume.html
└── app/
    ├── build.gradle.kts                # 模块级构建
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── assets/
        │   │   ├── prompts.json        # AI提示词配置
        │   │   ├── skill_dict.json     # 技能分类词典
        │   │   ├── resume_template.html # Classic简历模板
        │   │   └── vibe_resume_template.html
        │   ├── res/
        │   │   ├── values/ (colors.xml, strings.xml, themes.xml)
        │   │   ├── xml/ (backup_rules.xml, data_extraction_rules.xml, file_paths.xml)
        │   │   └── mipmap-*/ (启动图标)
        │   └── java/com/example/tielink/
        │       ├── MainActivity.kt
        │       ├── TieLinkApp.kt
        │       ├── navigation/NavGraph.kt
        │       ├── di/AppModule.kt
        │       ├── domain/
        │       │   ├── model/          (15个数据类)
        │       │   ├── nlp/            (5个NLP引擎)
        │       │   └── usecase/        (2个用例)
        │       ├── data/
        │       │   ├── local/
        │       │   │   ├── AppPreferences.kt
        │       │   │   └── db/ (AppDatabase, 4 DAO, 5 Entity)
        │       │   ├── remote/
        │       │   │   ├── dto/ (3个DTO)
        │       │   │   ├── interceptor/ (ApiKeyInterceptor)
        │       │   │   └── (10个API服务/提供者文件)
        │       │   └── repository/     (10个仓库)
        │       ├── ui/
        │       │   ├── GlobalJdViewModel.kt
        │       │   ├── theme/ (Color, Theme, Type)
        │       │   ├── components/ (5个共享组件)
        │       │   ├── home/
        │       │   ├── agent/
        │       │   ├── history/
        │       │   ├── jdinput/
        │       │   ├── resumeinput/
        │       │   ├── resumeoptimize/
        │       │   ├── polish/
        │       │   ├── result/
        │       │   ├── interview/
        │       │   ├── tracking/
        │       │   └── settings/
        │       └── util/ (4个工具类)
        ├── test/ (ExampleUnitTest.kt)
        └── androidTest/ (ExampleInstrumentedTest.kt)
```

---

## 三、架构分层详解

### 3.1 UI 层 (`ui/`)

**每个功能模块 = 1个Screen + 1个ViewModel**，ViewModel 通过 `@HiltViewModel` 注入依赖，UI 通过 `collectAsState()` 收集 StateFlow。

| 模块 | Screen | ViewModel | 核心功能 |
|------|--------|-----------|----------|
| **Agent** | `AgentChatScreen.kt` | `AgentViewModel.kt` | 智能对话主界面，流式响应，快捷功能入口 |
| **Home** | `HomeScreen.kt` | `HomeViewModel.kt` | 仪表盘，各功能入口卡片 |
| **ResumeOptimize** | `ResumeOptimizeScreen.kt` | `ResumeOptimizeViewModel.kt` | 简历优化工作台，版本管理，评分 |
| **Interview** | `InterviewScreen.kt` | `InterviewViewModel.kt` | AI模拟面试，多人格，流式对话 |
| **Tracking** | `TrackingScreen.kt` | `TrackingViewModel.kt` | 求职追踪看板，6种状态 |
| **JdInput** | `JdInputScreen.kt` | `JdInputViewModel.kt` | JD输入+OCR识别+AI结构化提取 |
| **ResumeInput** | `ResumeInputScreen.kt` | `ResumeInputViewModel.kt` | 简历输入+文件上传+匹配预分析 |
| **Polish** | `PolishScreen.kt` | `PolishViewModel.kt` | 简历润色进度动画(8步) |
| **Result** | `ResultScreen.kt` (1595行) | `ResultViewModel.kt` | 润色结果编辑，侧边栏，AI对话，导出 |
| **History** | `HistoryScreen.kt` | `HistoryViewModel.kt` | 历史记录列表 |
| **Settings** | `SettingsScreen.kt` | `SettingsViewModel.kt` | API Key/模型/BaseURL配置 |

**共享组件** (`ui/components/`):
- `ErrorBanner.kt` — 红色错误提示条+重试按钮
- `LoadingOverlay.kt` — 全屏半透明加载遮罩
- `ResumePreviewWebView.kt` — WebView简历HTML预览
- `ScoreRingChart.kt` — 动画环形评分图(0-100, 300°弧)
- `SectionCard.kt` — 通用标题卡片容器

**全局状态**: `GlobalJdStateHolder` (@Singleton) — 跨所有页面共享当前JD状态(rawText/structuredJson/companyName/positionName)，通过 `CompositionLocalProvider` 注入，持久化到 DataStore。

**主题** (`ui/theme/`):
- 主色: Deep Blue `#1565C0`
- 辅色: Teal `#00897B`
- 状态色: MatchGreen `#4CAF50`, MissRed `#E53935`, WarningOrange `#FFA726`
- 支持 Android 12+ Dynamic Color

---

### 3.2 领域层 (`domain/`)

纯Kotlin，无Android依赖。负责业务逻辑和NLP处理。

#### 领域模型 (`domain/model/`)

| 文件 | 核心类 | 关键字段/用途 |
|------|--------|--------------|
| `JobDescription.kt` | `JobDescription` | jobTitle, requirements, skills, responsibilities, niceToHave, summary — LLM结构化提取的JD |
| `Resume.kt` | `Resume`, `ResumeSections` | rawText, cleanedText, sections(个人信息/总结/经历/教育/技能/项目/其他) |
| `ResumeData.kt` | `ResumeData` | 完整结构化简历: name/targetPosition/contact/summary/experiences/education/projects/skills/certifications/languages/photoBase64/links |
| `ResumeVersion.kt` | `ResumeVersion` | 多版本简历管理: name/rawText/matchScore/tags/isActive |
| `MatchAnalysis.kt` | `MatchAnalysis`, `SkillGap` | ATS匹配分析: score(0-100)/matched/missing/suggestions/skillFit/experienceRelevance |
| `PolishResult.kt` | `PolishResult` | 润色结果: polishedResume/resumeJson/optimizationNote/matchAnalysis |
| `HistoryItem.kt` | `HistoryItem` | UI层历史记录模型 |
| `InterviewSession.kt` | `InterviewSession`, `InterviewPersona` | 面试会话: personaType(5种人格)/isActive/questionCount/overallScore |
| `InterviewMessage.kt` | `InterviewMessage`, `MessageRole` | 面试消息: role(USER/INTERVIEWER/SYSTEM)/content/isHint/isEvaluation |
| `InterviewResult.kt` | `InterviewResult`, `DimensionScore`, `KeyMoment` | 面试评估: overallScore/dimensionScores/improvements/highlights/keyMoments |
| `AgentMessage.kt` | `AgentMessage`, `AgentChatUiState`, `ContextBarState` | 智能体聊天: messages/inputText/isLoading/isStreaming/contextBar |
| `AgentIntent.kt` | `AgentIntent`, `IntentType`, `ToolCall` | 意图分类结果: 8种意图类型 + 工具调用 |
| `AgentOutput.kt` | `AgentOutput` (sealed) | 智能体输出流: StreamText/ToolStart/ToolResult/ClarificationRequest/Error/Done + 7种UI卡片 |
| `AgentContext.kt` | `AgentContext` | 智能体跨会话上下文: currentJd/currentResume/activeInterview/conversationSummary |
| `GlobalJdState.kt` | `GlobalJdState` | 全局JD状态 |

**InterviewPersona 枚举 (5种面试人格)**:
- `MILD_TECH` — 温和技术面
- `PRESSURE` — 压力面
- `FOREIGN_HR` — 外企HR面
- `STATE_STRUCTURED` — 国企结构化面试
- `CUSTOM` — 自定义

#### NLP 引擎 (`domain/nlp/`)

| 文件 | 类型 | 核心能力 |
|------|------|----------|
| `NlpEngine.kt` | Singleton | 手写TF-IDF+CJK分词器(单字+双字+拉丁词)+余弦相似度。完全离线。 |
| `EmbeddingEngine.kt` | Singleton | TFLite中文BERT量化模型(768维, 128 token序列)，语义嵌入+余弦相似度 |
| `KeywordClassifier.kt` | @Singleton | 加载`skill_dict.json`建立反向索引，O(1)技能分类，模糊回退 |
| `SemanticMatcher.kt` | Singleton | 混合匹配编排: 60%语义+40%关键词(本地)；40%本地+60%LLM(远程可用时) |
| `IntentClassifier.kt` | Singleton | 基于关键词的意图分类 → 8种IntentType + ToolCall |

**匹配得分计算流程**:
```
JD文本 + 简历文本
    ├── NlpEngine.matchKeywords() → 关键词匹配(匹配/缺失列表)
    ├── EmbeddingEngine.computeSemanticScore() → 语义相似度
    │   └── (fallback) NlpEngine.matchScore() → TF-IDF余弦相似度
    ├── 混合: 60%语义 + 40%关键词 = 本地分
    └── 有LLM分数时: 40%本地分 + 60%LLM分 = 最终分
```

#### 用例 (`domain/usecase/`)

| 文件 | 核心职责 |
|------|----------|
| `MatchAnalysisUseCase.kt` | 编排匹配分析管道: 关键词→TF-IDF→混合评分→分类→建议生成 |
| `AgentUseCase.kt` | 编排AI智能体管道: 意图分类→工具调用→LLM对话(流式)→记忆提取 |

---

### 3.3 数据层 (`data/`)

基础设施层，仅包含数据访问和网络通信代码。

#### Room 数据库 (`data/local/db/`)

**AppDatabase.kt** — @Database v6, 5张表, 4个DAO, 2个迁移(3→4, 4→5), destructive fallback

| Entity (表) | DAO | 主要字段 |
|-------------|-----|----------|
| `HistoryEntity` (history) | `HistoryDao` | id, jdRawText, polishedResume, resumeJson, matchScore, templateStyle... |
| `ResumeVersionEntity` (resume_versions) | `ResumeVersionDao` | id, name, rawText, matchScore, tags(JSON), isActive, timestamps |
| `TrackingEntity` (tracking) | `TrackingDao` | id, companyName, positionName, status, notes, timeline(JSON) |
| `InterviewSessionEntity` (interview_sessions) | `InterviewDao` | id, personaType, isActive, questionCount, overallScore, dimensionScores(JSON) |
| `InterviewMessageEntity` (interview_messages) | `InterviewDao` | id, sessionId(FK CASCADE), role, content, isHint, isEvaluation |

**DAO 关键方法**:
- `HistoryDao`: getAllFlow(), getById(), insert(), deleteById()
- `ResumeVersionDao`: getAllFlow(), getActive(), setActive(), deactivateAll()
- `TrackingDao`: getAllFlow(), getByStatus(), countFlow()
- `InterviewDao`: getActiveSession(), deactivateAllSessions(), getMessagesFlow(), endSession()(事务), insertMessages(batch)

#### DataStore 偏好 (`data/local/AppPreferences.kt`)

管理所有应用设置，keys: `deepseek_api_key`, `llm_model`, `llm_base_url`, `ollama_base_url`, `ollama_model`, `ollama_embed_model`, `ai_provider`, `pdf_template`, `app_language`, `last_resume`, `cached_jd_raw/json/company`, `has_seen_onboarding`, `last_interview_persona`, `agent_context_json`

#### 远程API (`data/remote/`)

**AI Provider 体系**:

```
AiProvider (接口)
├── DeepSeekProvider    → Retrofit DeepSeekApiService + StreamingApiService (SSE)
├── OllamaProvider      → OkHttp 原生调用 /api/chat + /api/embeddings
└── LocalProvider       → 仅embed (TFLite), chat抛异常

AiProviderManager (@Singleton)
├── getProvider()       → 根据偏好选择提供者
├── chatWithFallback()  → DeepSeek → Ollama 回退链
├── smartEmbed()        → 提供者embed → EmbeddingEngine 回退
└── chatWithFallbackStream() → 流式回退
```

**关键文件**:
| 文件 | 作用 |
|------|------|
| `AiProvider.kt` | 接口定义 + LlmRequest/LlmResponse/TokenUsage |
| `AiProviderManager.kt` | 提供者管理+回退链+流式支持 |
| `DeepSeekApiService.kt` | Retrofit接口 POST v1/chat/completions |
| `DeepSeekProvider.kt` | DeepSeek实现 |
| `OllamaProvider.kt` | Ollama实现(OkHttp原生JSON) |
| `StreamingApiService.kt` | SSE流式解析(OpenAI格式+Ollama JSON-lines) |
| `ApiEndpointResolver.kt` | URL规范化+端点解析 |
| `ApiKeyInterceptor.kt` | OkHttp拦截器注入API Key |
| `PromptRegistry.kt` | 加载`prompts.json`+硬编码回退 |
| `InterviewPrompts.kt` | 面试人格→提示词映射+开场上下文构建 |

**DTO**:
- `DeepSeekRequest.kt` — model, messages, temperature, max_tokens, stream
- `DeepSeekResponse.kt` — id, choices[].message
- `StreamChunk.kt` — choices[].delta.content/reasoningContent, finishReason

#### 仓库 (`data/repository/`)

每个仓库一个领域概念，封装数据源(DB+远程+偏好)：

| 仓库 | 数据源 | 核心职责 |
|------|--------|----------|
| `ResumeRepository.kt` | AppPreferences | 最近简历文本持久化+清洗 |
| `ResumeVersionRepository.kt` | ResumeVersionDao | 简历版本CRUD+domain映射 |
| `JdRepository.kt` | DeepSeekApiService | JD AI结构化提取(JSON解析) |
| `PolishRepository.kt` | AiProviderManager, PromptRegistry | 简历润色(完整/部分/迭代) |
| `HistoryRepository.kt` | HistoryDao | 历史记录包装 |
| `InterviewRepository.kt` | InterviewDao | 面试会话+消息，entity↔domain映射 |
| `TrackingRepository.kt` | TrackingDao | 追踪条目CRUD+timeline管理 |
| `CoverLetterRepository.kt` | AiProviderManager, PromptRegistry | 求职信生成 |
| `SettingsRepository.kt` | AppPreferences | 设置读写代理 |
| `AgentContextRepository.kt` | AppPreferences | Agent上下文序列化/反序列化(Moshi) |

---

### 3.4 工具层 (`util/`)

| 文件 | 功能 |
|------|------|
| `FileParser.kt` | PDF(PdfBox-Android) + DOCX(ZIP+XML手动解析) + ML Kit OCR |
| `TextCleaner.kt` | 文本规范化：换行/空白/项目符号清理 |
| `HtmlPdfExporter.kt` | 简历HTML模板 + WebView Print → A4 PDF 导出（支持Classic/Vibe两套模板） |
| `AgentWorkspace.kt` | 文件型AI记忆系统: RESUME_MEMORY.md/JD_MEMORY.md/INTERVIEW_MEMORY.md |

---

## 四、导航路由

**NavGraph.kt** — 单Activity + NavHost 驱动的 Compose 导航。

### 路由表

| 路由常量 | 路径模式 | 说明 |
|----------|----------|------|
| `AGENT_CHAT` | `agent_chat` | **启动页** — 智能体对话 |
| `HOME` | `home` | 首页仪表盘 |
| `RESUME_OPTIMIZE` | `resume_optimize` | 简历优化(新并行模块) |
| `MOCK_INTERVIEW` | `mock_interview` | AI模拟面试(新并行模块) |
| `TRACKING` | `tracking` | 求职追踪(新并行模块) |
| `JD_INPUT` | `jd_input` | JD输入(Legacy) |
| `RESUME_INPUT` | `resume_input/{jdRawText}/{jdStructuredJson}` | 简历输入(Legacy) |
| `POLISH` | `polish/{resumeText}/{jdRawText}/{jdStructuredJson}/{templatePath}/{sourceType}/{fullPolish}` | 润色进度(Legacy) |
| `RESULT` | `result/{sessionId}` | 润色结果(Legacy) |
| `HISTORY` | `history` | 历史记录 |
| `SETTINGS` | `settings` | 设置 |
| `JD_OPTIMIZE_JD_INPUT` | `jd_optimize_jd_input` | JD优化-JD输入 |
| `JD_OPTIMIZE_RESUME_INPUT` | `jd_optimize_resume_input/{jdRawText}/{jdStructuredJson}` | JD优化-简历输入 |

### 导航流程

**新并行工作台** (Agent → Home → 各模块独立):
```
AGENT_CHAT ←→ HOME ←→ RESUME_OPTIMIZE / MOCK_INTERVIEW / TRACKING / SETTINGS / JD_INPUT / JD_OPTIMIZE_JD_INPUT
```

**Legacy线性流**:
```
JD_INPUT → RESUME_INPUT → POLISH → RESULT (→ HISTORY / SETTINGS)
```

**JD优化流**:
```
JD_OPTIMIZE_JD_INPUT → JD_OPTIMIZE_RESUME_INPUT → POLISH → RESULT
```

---

## 五、数据库Schema

```sql
-- history (v3+)
CREATE TABLE history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at INTEGER NOT NULL,
    jd_raw_text TEXT NOT NULL,
    jd_title TEXT,
    original_resume TEXT NOT NULL,
    polished_resume TEXT NOT NULL,
    resume_json TEXT,         -- v4迁移添加
    jd_skills TEXT,           -- JSON array
    match_note TEXT,
    match_score REAL,
    matched_keywords TEXT,    -- JSON array
    missing_keywords TEXT,    -- JSON array
    suggestions TEXT,         -- JSON array
    original_file_path TEXT,
    source_type TEXT,
    template_style TEXT
);

-- resume_versions (v5+)
CREATE TABLE resume_versions (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    raw_text TEXT NOT NULL,
    cleaned_text TEXT NOT NULL,
    jd_matched_with TEXT,
    match_score REAL NOT NULL DEFAULT 0,
    tags TEXT NOT NULL DEFAULT '[]',   -- JSON array
    is_active INTEGER NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- tracking (v5+)
CREATE TABLE tracking (
    id TEXT PRIMARY KEY,
    company_name TEXT NOT NULL,
    position_name TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT '已投',
    resume_version_id TEXT,
    jd_raw_text TEXT,
    notes TEXT,
    timeline TEXT NOT NULL DEFAULT '[]',  -- JSON array of {status, timestamp}
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- interview_sessions (v5+)
CREATE TABLE interview_sessions (
    id TEXT PRIMARY KEY,
    persona_type TEXT NOT NULL,
    jd_raw_text TEXT,
    resume_version_id TEXT,
    resume_text TEXT,
    is_active INTEGER NOT NULL DEFAULT 0,
    question_count INTEGER NOT NULL DEFAULT 0,
    overall_score REAL,
    dimension_scores TEXT,    -- JSON map
    improvements TEXT,        -- JSON array
    created_at INTEGER NOT NULL
);

-- interview_messages (v5+)
CREATE TABLE interview_messages (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp INTEGER NOT NULL,
    is_hint INTEGER NOT NULL DEFAULT 0,
    is_evaluation INTEGER NOT NULL DEFAULT 0,
    FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE
);
```

---

## 六、AI Provider 体系

### 提示词配置 (`assets/prompts.json`)

运行时由 `PromptRegistry` 加载，内置硬编码回退。Key列表：

| Key | 用途 | temperature |
|-----|------|-------------|
| `polish_full` | 完整简历润色 | 0.3 |
| `polish_partial` | 部分简历润色 | 0.3 |
| `polish_iterative` | 迭代修改 | 0.3 |
| `interview_mild_tech` | 温和技术面开场 | 0.7 |
| `interview_pressure` | 压力面开场 | 0.7 |
| `interview_foreign_hr` | 外企HR面开场 | 0.7 |
| `interview_state_structured` | 国企结构化面试开场 | 0.7 |
| `interview_follow_up` | 面试追问 | 0.7 |
| `interview_evaluation` | 面试评估 | 0.3 |
| `resume_quantify` | 简历量化 | - |
| `resume_star_format` | STAR格式 | - |
| `match_score_detail` | 匹配评分详情 | - |
| `cover_letter_zh` | 中文求职信 | 0.7 |
| `cover_letter_en` | 英文求职信 | 0.7 |
| `agent_chat` | 智能体对话 | 0.7 |

### 流式SSE处理

`StreamingApiService.kt` 使用 OkHttp `callbackFlow`:
- OpenAI格式: 解析 `data: {...}` 行，`[DONE]` 终止标记
- Ollama格式: 逐行JSON，`done: true` 终止标记
- 发送 `StreamEvent`: Start → Content(text) → Done → Error

### API Key 注入

`ApiKeyInterceptor` 从 `AppPreferences`（@Volatile 缓存）同步读取 Key，添加 `Authorization: Bearer <key>` 请求头。

---

## 七、关键数据流

### 7.1 JD提取流程
```
用户输入/OCR → JdInputViewModel.submitJd()
  → JdRepository.extractJobDescription() → DeepSeekApiService (LLM提取)
  → JSON解析 → JobDescription
  → GlobalJdStateHolder.setJd() → 持久化DataStore → 所有页面可读
```

### 7.2 简历润色流程
```
用户输入简历 + JD → PolishRepository.polishResume()
  → AiProviderManager.chatWithFallback() → PromptRegistry("polish_full")
  → LLM返回结构化JSON → PolishResult (polishedResume + matchAnalysis + optimizationNote)
  → HistoryRepository.insert() 保存记录
  → ResultScreen 展示/编辑/导出
```

### 7.3 面试流程
```
选择Persona → InterviewViewModel.startInterview()
  → InterviewPrompts.buildOpeningContext() → 构建system prompt
  → AiProviderManager.chatWithFallbackStream() → 流式面试官开场
  → 用户回答 → sendAnswer() → 构建上下文(最近10条消息)
  → 流式追问/评估
  → endInterview() → AI评估 → InterviewResult (分数+维度+关键时刻)
  → InterviewRepository 持久化
```

### 7.4 Agent智能体流程
```
用户消息 → AgentUseCase.process()
  → IntentClassifier.classify() → AgentIntent (意图+工具调用)
  → ClarificationRequest / ToolStart / 直接LLM对话
  → AiProviderManager.chatWithFallbackStream()
  → AgentOutput 事件流 → AgentViewModel → UI更新
  → AgentWorkspace 持久化用户记忆
```

---

## 八、依赖注入 (Hilt)

### AppModule.kt 提供的主要绑定

| 类型 | 作用域 | 说明 |
|------|--------|------|
| `AppDatabase` | @Singleton | Room数据库实例 |
| 4个DAO | 通过AppDatabase获取 | @Provides |
| `DeepSeekApiService` | @Singleton | Retrofit创建(动态baseUrl) |
| `OkHttpClient` | @Singleton | 带ApiKeyInterceptor+日志拦截器 |
| `Moshi` | @Singleton | KotlinJsonAdapterFactory |
| `GlobalJdStateHolder` | @Singleton | 全局JD状态 |
| `KeywordClassifier` | @Singleton | 技能分类器 |
| 各类Repository | @Singleton | 数据仓库 |
| 各类UseCase | @Singleton | 业务用例 |
| ViewModel | @HiltViewModel | 通过@Inject constructor注入 |

---

## 九、文件清单 (共93个Kotlin源文件)

### 顶层 (2)
- `MainActivity.kt` — @AndroidEntryPoint, 全局状态注入, NavHost
- `TieLinkApp.kt` — @HiltAndroidApp, 初始化FileParser+EmbeddingEngine

### 导航 (1)
- `navigation/NavGraph.kt` — 所有路由+导航图

### DI (1)
- `di/AppModule.kt` — Hilt模块

### Domain (22)
- `domain/model/AgentContext.kt`
- `domain/model/AgentIntent.kt`
- `domain/model/AgentMessage.kt`
- `domain/model/AgentOutput.kt`
- `domain/model/GlobalJdState.kt`
- `domain/model/HistoryItem.kt`
- `domain/model/InterviewMessage.kt`
- `domain/model/InterviewResult.kt`
- `domain/model/InterviewSession.kt`
- `domain/model/JobDescription.kt`
- `domain/model/MatchAnalysis.kt`
- `domain/model/PolishResult.kt`
- `domain/model/Resume.kt`
- `domain/model/ResumeData.kt`
- `domain/model/ResumeVersion.kt`
- `domain/nlp/EmbeddingEngine.kt`
- `domain/nlp/IntentClassifier.kt`
- `domain/nlp/KeywordClassifier.kt`
- `domain/nlp/NlpEngine.kt`
- `domain/nlp/SemanticMatcher.kt`
- `domain/usecase/AgentUseCase.kt`
- `domain/usecase/MatchAnalysisUseCase.kt`

### Data (35)
- `data/local/AppPreferences.kt`
- `data/local/db/AppDatabase.kt`
- `data/local/db/dao/HistoryDao.kt`
- `data/local/db/dao/InterviewDao.kt`
- `data/local/db/dao/ResumeVersionDao.kt`
- `data/local/db/dao/TrackingDao.kt`
- `data/local/db/entity/HistoryEntity.kt`
- `data/local/db/entity/InterviewMessageEntity.kt`
- `data/local/db/entity/InterviewSessionEntity.kt`
- `data/local/db/entity/ResumeVersionEntity.kt`
- `data/local/db/entity/TrackingEntity.kt`
- `data/remote/AiProvider.kt`
- `data/remote/AiProviderManager.kt`
- `data/remote/ApiEndpointResolver.kt`
- `data/remote/DeepSeekApiService.kt`
- `data/remote/DeepSeekProvider.kt`
- `data/remote/InterviewPrompts.kt`
- `data/remote/OllamaProvider.kt`
- `data/remote/PromptRegistry.kt`
- `data/remote/StreamingApiService.kt`
- `data/remote/dto/DeepSeekRequest.kt`
- `data/remote/dto/DeepSeekResponse.kt`
- `data/remote/dto/StreamChunk.kt`
- `data/remote/interceptor/ApiKeyInterceptor.kt`
- `data/repository/AgentContextRepository.kt`
- `data/repository/CoverLetterRepository.kt`
- `data/repository/HistoryRepository.kt`
- `data/repository/InterviewRepository.kt`
- `data/repository/JdRepository.kt`
- `data/repository/PolishRepository.kt`
- `data/repository/ResumeRepository.kt`
- `data/repository/ResumeVersionRepository.kt`
- `data/repository/SettingsRepository.kt`
- `data/repository/TrackingRepository.kt`

### UI (30)
- `ui/GlobalJdViewModel.kt`
- `ui/theme/Color.kt`
- `ui/theme/Theme.kt`
- `ui/theme/Type.kt`
- `ui/components/ErrorBanner.kt`
- `ui/components/LoadingOverlay.kt`
- `ui/components/ResumePreviewWebView.kt`
- `ui/components/ScoreRingChart.kt`
- `ui/components/SectionCard.kt`
- `ui/agent/AgentChatScreen.kt`
- `ui/agent/AgentViewModel.kt`
- `ui/home/HomeScreen.kt`
- `ui/home/HomeViewModel.kt`
- `ui/history/HistoryScreen.kt`
- `ui/history/HistoryViewModel.kt`
- `ui/jdinput/JdInputScreen.kt`
- `ui/jdinput/JdInputViewModel.kt`
- `ui/resumeinput/ResumeInputScreen.kt`
- `ui/resumeinput/ResumeInputViewModel.kt`
- `ui/resumeoptimize/ResumeOptimizeScreen.kt`
- `ui/resumeoptimize/ResumeOptimizeViewModel.kt`
- `ui/polish/PolishScreen.kt`
- `ui/polish/PolishViewModel.kt`
- `ui/result/ResultScreen.kt`
- `ui/result/ResultViewModel.kt`
- `ui/interview/InterviewScreen.kt`
- `ui/interview/InterviewViewModel.kt`
- `ui/tracking/TrackingScreen.kt`
- `ui/tracking/TrackingViewModel.kt`
- `ui/settings/SettingsScreen.kt`
- `ui/settings/SettingsViewModel.kt`

### Util (4)
- `util/AgentWorkspace.kt`
- `util/FileParser.kt`
- `util/HtmlPdfExporter.kt`
- `util/TextCleaner.kt`

### 其他 (1)
- `android/print/PdfPrint.kt` — Android Print API辅助

---

## 十、关键技术决策

1. **KSP** (非KAPT) — 影响Hilt和Room代码生成
2. **Compose BOM 2026.02.01** — 统一管理Compose库版本
3. **Room fallbackToDestructiveMigration(true)** — v5+开发阶段用破坏性迁移
4. **ProGuard 关闭** — `minifyEnabled = false`
5. **AI回退链**: DeepSeek API → Ollama (本地) → Local TFLite (仅embed)
6. **Prompt管理**: `assets/prompts.json` 运行时加载 + `PromptRegistry` 硬编码回退
7. **简历导出**: HTML模板 + 占位符替换 → WebView Print → A4 PDF (Classic / Vibe 两套模板)
8. **自定义NLP**: 无外部NLP库依赖 — 手写TF-IDF + CJK分词 + TFLite BERT嵌入
9. **Moshi + 反射**: `KotlinJsonAdapterFactory` (非codegen)
10. **全局JD状态**: `@Singleton` + `CompositionLocalProvider` 共享，免去URL编码传参

---

## 十一、常用构建命令

```bash
.\gradlew.bat assembleDebug          # 调试APK
.\gradlew.bat assembleRelease        # 发布APK
.\gradlew.bat test                   # 单元测试
.\gradlew.bat testDebugUnitTest --tests "com.example.tielink.XXX"  # 单测
.\gradlew.bat connectedAndroidTest   # 仪器测试(需设备)
.\gradlew.bat lint                   # 代码检查
.\gradlew.bat clean                  # 清理
```

---

## 十二、外部依赖 (主要)

| 类别 | 库 |
|------|-----|
| UI | Jetpack Compose BOM 2026.02.01, Material 3, Navigation Compose |
| DI | Hilt (hilt-android, hilt-navigation-compose) |
| DB | Room (runtime, ktx, compiler via KSP) |
| 网络 | Retrofit, OkHttp, Moshi (KotlinJsonAdapterFactory) |
| 本地存储 | DataStore Preferences |
| NLP | TensorFlow Lite (TFLite) |
| 文档处理 | PDFBox-Android, ML Kit Text Recognition (Chinese) |
| 动画 | Konfetti (Confetti粒子效果) |
| 图像 | Coil (Compose图片加载) |
| 构建 | KSP, Android Gradle Plugin |

---

> **使用说明**: 后续AI交互时，优先阅读本文档了解项目结构。如需了解具体实现细节，再读取对应源文件。
