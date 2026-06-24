# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
.\gradlew.bat assembleDebug         # Debug APK
.\gradlew.bat assembleRelease       # Release APK
.\gradlew.bat build                 # Full build (compile + test + lint)

# Testing
.\gradlew.bat test                  # Unit tests (JVM only)
.\gradlew.bat testDebugUnitTest     # Run a specific build variant's unit tests
.\gradlew.bat connectedAndroidTest  # Instrumentation tests (requires device/emulator)

# Lint & clean
.\gradlew.bat lint
.\gradlew.bat clean
```

To run a single test class: `.\gradlew.bat testDebugUnitTest --tests "com.example.cv_jobmatcher.ExampleUnitTest"`

## Architecture Overview

Single-module Android app (`com.example.cv_jobmatcher`) using **Clean Architecture + MVVM** with Jetpack Compose. Dependency injection via Hilt throughout.

### Layer Structure

**UI** (`ui/`) — Compose screens + ViewModels per feature. Each screen folder (e.g. `home/`, `resumeoptimize/`, `interview/`, `tracking/`, `result/`) contains a `*Screen.kt` and `*ViewModel.kt`. Shared components live in `ui/components/`. Global state via `GlobalJdViewModel` injected through `CompositionLocalProvider` in `MainActivity`.

**Domain** (`domain/`) — Pure Kotlin. Contains:
- `model/` — data classes: `Resume`, `ResumeData`, `ResumeVersion`, `JobDescription`, `MatchAnalysis`, `PolishResult`, `HistoryItem`, `InterviewSession`, `InterviewMessage`, `InterviewResult`
- `nlp/NlpEngine` — custom hand-rolled TF-IDF + cosine similarity (no external NLP libs; intentionally offline and explainable)
- `nlp/KeywordClassifier` — skill domain categorization
- `nlp/EmbeddingEngine` — TFLite 768-dim Chinese BERT quantized model for semantic embedding
- `nlp/SemanticMatcher` — orchestrates hybrid matching: 60% keyword + 40% TF-IDF (local); 40% local + 60% LLM (when LLM score available)
- `usecase/MatchAnalysisUseCase` — score blending pipeline

**Data** (`data/`) — Infrastructure only:
- `local/db/` — Room database (`cv_jobmatcher.db`, v6) with 5 tables: `history`, `resume_versions`, `tracking`, `interview_sessions`, `interview_messages`. Migrations 3→4 and 4→5 defined; uses `fallbackToDestructiveMigration(true)` for newer versions.
- `local/AppPreferences` — DataStore keys: `deepseek_api_key`, `llm_model`, `llm_base_url`, `last_resume`, `has_seen_onboarding`, `ollama_base_url`, `ollama_model`, `ollama_embed_model`, `ai_provider`, `pdf_template`, `app_language`, `cached_jd_raw/json/company`, `last_interview_persona`
- `remote/` — `DeepSeekApiService` (Retrofit), `StreamingApiService` (SSE), `OllamaProvider` (raw OkHttp), `AiProviderManager` (fallback chain: DeepSeek → Ollama → Local), `PromptRegistry` (loads from `assets/prompts.json` + hardcoded fallback), `InterviewPrompts` (persona → prompt mapping)
- `repository/` — one repository per domain concept: Resume, ResumeVersion, Jd, Polish, CoverLetter, Settings, History, Interview, Tracking

**Utils** (`util/`) — `FileParser` (PDF via PdfBox-Android, DOCX via raw ZIP+XML, OCR via ML Kit), `TextCleaner`, `HtmlPdfExporter` (HTML → A4 PDF via WebView Print), `AgentWorkspace` (file-based agent memory: resume/JD/interview context files). File I/O runs on the IO dispatcher.

### Navigation

Compose Navigation (`NavGraph.kt`). Entry route is `HOME`. Two route groups:
- **New parallel workbench**: `home`, `resume_optimize`, `mock_interview`, `tracking`
- **Legacy linear flow** (kept for compat): `jd_input`, `resume_input/{...}`, `polish/{...}`, `result/{sessionId}`
- **Other**: `history`, `settings`, `jd_optimize_jd_input`, `jd_optimize_resume_input/{...}`

Complex objects passed between screens via URL-encoded string parameters — decode back in the destination ViewModel or screen.

### NLP Engine Notes

The `NlpEngine` tokenizes both CJK (single chars + bigrams) and Latin/digit sequences, with English and Chinese stop word filtering. This is intentionally custom — no external NLP library dependency — so changes must maintain offline, lightweight behavior.

`EmbeddingEngine` loads a TFLite quantized Chinese BERT model (768-dim, 128 token sequence) for local semantic similarity. Used by `SemanticMatcher` as an alternative to TF-IDF when available.

### HTTP Client

OkHttp with 30s connect / 60s read / 30s write timeouts. Logging interceptor is set to `BODY` level (verbose). Moshi uses `KotlinJsonAdapterFactory` (reflective, not codegen).

### Agent Workspace

`AgentWorkspace` (singleton object) manages file-based memory for the Agent system. Three memory files under `agent_workspace/memory/`: `RESUME_MEMORY.md`, `JD_MEMORY.md`, `INTERVIEW_MEMORY.md`. Supports append-only writes with dedup and timestamp, plus `extractExplicitMemory()` for detecting user memory-save intent from natural language.

## Key Technical Decisions

- **KSP** (not KAPT) for annotation processing — affects Hilt and Room code generation
- **Compose BOM 2026.02.01** pins all Compose library versions
- Room `fallbackToDestructiveMigration(true)` — migrations defined for 3→4 and 4→5; versions 5+ rely on destructive fallback during development
- ProGuard is disabled in release builds (`minifyEnabled = false`)
- AI Provider fallback chain: DeepSeek API → Ollama (local) → Local TFLite (embed only)
- Prompt management: `assets/prompts.json` loaded at runtime with hardcoded fallback in `PromptRegistry`
- Resume export: HTML template + placeholder replacement → WebView Print → A4 PDF (two templates: Classic / Vibe)
