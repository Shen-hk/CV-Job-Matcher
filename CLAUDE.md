# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
# Build
./gradlew assembleDebug         # Debug APK
./gradlew assembleRelease       # Release APK
./gradlew build                 # Full build (compile + test + lint)

# Testing
./gradlew test                  # Unit tests (JVM only)
./gradlew testDebugUnitTest     # Run a specific build variant's unit tests
./gradlew connectedAndroidTest  # Instrumentation tests (requires device/emulator)

# Lint & clean
./gradlew lint
./gradlew clean
```

To run a single test class: `./gradlew testDebugUnitTest --tests "com.example.cv_jobmatcher.ExampleUnitTest"`

## Architecture Overview

Single-module Android app (`com.example.cv_jobmatcher`) using **Clean Architecture + MVVM** with Jetpack Compose. Dependency injection via Hilt throughout.

### Layer Structure

**UI** (`ui/`) — Compose screens + ViewModels per feature. Each screen folder (e.g. `resume_input/`, `jdinput/`, `result/`) contains a `*Screen.kt` and `*ViewModel.kt`. Shared components live in `ui/components/`.

**Domain** (`domain/`) — Pure Kotlin. Contains:
- `model/` — data classes: `Resume`, `JobDescription`, `MatchAnalysis`, `PolishResult`, `HistoryItem`
- `nlp/NlpEngine` — custom hand-rolled TF-IDF + cosine similarity (no external NLP libs; intentionally offline and explainable)
- `nlp/KeywordClassifier` — skill domain categorization
- `usecase/MatchAnalysisUseCase` — orchestrates score blending: **60% keyword match + 40% TF-IDF cosine similarity** for local scoring; when DeepSeek is available, blended as **40% local + 60% LLM**

**Data** (`data/`) — Infrastructure only:
- `local/db/` — Room database (`cv_jobmatcher.db`, v3) with `HistoryDao`/`HistoryEntity`. Uses `fallbackToDestructiveMigration` — schema upgrades wipe all history.
- `local/AppPreferences` — DataStore keys: `deepseek_api_key`, `llm_model`, `llm_base_url`, `last_resume`, `has_seen_onboarding`
- `remote/DeepSeekApiService` — Retrofit interface; `ApiKeyInterceptor` injects auth header dynamically
- `repository/` — one repository per domain concept (Resume, Jd, Polish, Settings, History)

**Utils** (`utils/`) — `FileParser` (PDF via PdfBox-Android, DOCX via raw ZIP+XML), `TextCleaner`, `DocxFormatter`, `PdfExporter`. File I/O runs on the IO dispatcher.

### Navigation

Compose Navigation (`NavGraph.kt`). Entry route is `JD_INPUT`. Complex objects passed between screens via URL-encoded string parameters — decode back in the destination ViewModel or screen.

### NLP Engine Notes

The `NlpEngine` tokenizes both CJK (single chars + bigrams) and Latin/digit sequences, with English and Chinese stop word filtering. This is intentionally custom — no external NLP library dependency — so changes must maintain offline, lightweight behavior.

### HTTP Client

OkHttp with 30s connect / 60s read / 30s write timeouts. Logging interceptor is set to `BODY` level (verbose). Moshi uses `KotlinJsonAdapterFactory` (reflective, not codegen).

## Key Technical Decisions

- **KSP** (not KAPT) for annotation processing — affects Hilt and Room code generation
- **Compose BOM 2026.02.01** pins all Compose library versions
- Room `fallbackToDestructiveMigration(false)` means any DB schema change at version bump wipes user history — handle migrations explicitly if history preservation matters
- ProGuard is disabled in release builds (`minifyEnabled = false`)
