# Phase 4 Feature 模块化实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 完成 `core:ui` 通用组件和五个 feature 模块的迁移，使 `:app` 成为启动、导航与 DI 组装层。

**Architecture:** 保持现有 `com.example.tielink` 包名不变，通过 Gradle 源集所属模块改变实现归属。feature 仅依赖 core 模块；`app` 依赖 feature 并保留顶层导航、应用入口和平台自动化服务。

**Tech Stack:** Android Gradle Plugin、Kotlin、Jetpack Compose、Hilt、Room、DataStore、Retrofit、JUnit。

## Global Constraints

- 每个迁移步骤必须执行 `:feature:<name>:compileDebugKotlin`（或 `:core:ui:compileDebugKotlin`）和 `:app:compileDebugKotlin --offline`。
- 保持现有包名和用户数据 Schema；不得改动 Room 表、DataStore key 或导航 route。
- 每笔提交前执行 `git diff --cached --check`，不得提交 `build/` 目录。
- 每个模块单独提交，提交信息使用中文。

---

### Task 1: 提取 core:ui 通用 Compose 组件

**Files:**
- Create: `core/ui/src/main/java/com/example/tielink/ui/components/*.kt`
- Modify: `core/ui/build.gradle.kts`
- Modify: `app/src/main/java/com/example/tielink/ui/components/*.kt`

- [ ] 将 `AppPrimitives.kt`、`ErrorBanner.kt`、`LoadingOverlay.kt`、`ScoreRingChart.kt` 和 `SectionCard.kt` 移至 `core/ui`，保持包名。
- [ ] 将仍依赖文件导出、WebView 或语音权限的组件暂留 app，避免 core:ui 依赖 feature。
- [ ] 为 core:ui 添加所需 Compose 图标、动画和 lifecycle 依赖。
- [ ] 运行 `./gradlew.bat :core:ui:compileDebugKotlin :app:compileDebugKotlin --offline --console=plain`。
- [ ] 提交：`重构：迁移核心通用组件`。

### Task 2: 建立并迁移 feature:agent

**Files:**
- Create: `feature/agent/build.gradle.kts`
- Modify: `settings.gradle.kts`, `app/build.gradle.kts`
- Move: `app/src/main/java/com/example/tielink/ui/agent/**`
- Move: `app/src/main/java/com/example/tielink/ui/history/**`
- Move: Agent 相关 use case 与 Agent 工具协调实现。

- [ ] 创建 `:feature:agent`，依赖 `core:model`、`core:domain`、`core:data`、`core:ui`、Compose 与 Hilt。
- [ ] 移动 Agent、历史会话 UI、ViewModel、Agent use case 与工具协调代码；保持现有包名。
- [ ] 将 app 导航保留为调用 feature 中同包名 screen 的入口。
- [ ] 运行 `./gradlew.bat :feature:agent:compileDebugKotlin :app:compileDebugKotlin --offline --console=plain`。
- [ ] 提交：`重构：拆分 Agent 功能模块`。

### Task 3: 建立并迁移 feature:resume

**Files:**
- Create: `feature/resume/build.gradle.kts`
- Move: `ui/jdinput/**`, `ui/jdlist/**`, `ui/polish/**`, `ui/result/**`, `ui/resumeinput/**`, `ui/resumelibrary/**`, `ui/resumeoptimize/**`
- Move: 简历、JD、匹配、STAR 与量化相关 use case。

- [ ] 创建 `:feature:resume`，声明 Compose、Hilt、Navigation、CameraX 与 core 依赖。
- [ ] 迁移简历/JD 各页面和 ViewModel，保持导航 route 与参数协议。
- [ ] 迁移仅由简历功能使用的领域 use case；共享模型与 data 保持在 core。
- [ ] 运行 `./gradlew.bat :feature:resume:compileDebugKotlin :app:compileDebugKotlin --offline --console=plain`。
- [ ] 提交：`重构：拆分简历功能模块`。

### Task 4: 建立并迁移 feature:interview

**Files:**
- Create: `feature/interview/build.gradle.kts`
- Move: `ui/interview/**`
- Move: 面试专属 use case。

- [ ] 创建 `:feature:interview`，声明 Compose、Hilt、core 与语音/媒体所需 AndroidX 依赖。
- [ ] 迁移模拟面试、复盘 UI 和 ViewModel，保留会话恢复与语音行为。
- [ ] 运行 `./gradlew.bat :feature:interview:compileDebugKotlin :app:compileDebugKotlin --offline --console=plain`。
- [ ] 提交：`重构：拆分面试功能模块`。

### Task 5: 建立并迁移 feature:tracking 与 feature:settings

**Files:**
- Create: `feature/tracking/build.gradle.kts`, `feature/settings/build.gradle.kts`
- Move: `ui/tracking/**`, `ui/settings/**`

- [ ] 分别创建投递与设置模块；两个模块只依赖实际使用的 core 模块。
- [ ] 迁移对应 screen、ViewModel 和专属 use case，保留 Provider 配置与投递状态行为。
- [ ] 运行 `./gradlew.bat :feature:tracking:compileDebugKotlin :feature:settings:compileDebugKotlin :app:compileDebugKotlin --offline --console=plain`。
- [ ] 分别提交：`重构：拆分投递功能模块` 与 `重构：拆分设置功能模块`。

### Task 6: 收缩 app 并执行发布验证

**Files:**
- Modify: `app/build.gradle.kts`, `app/src/main/java/com/example/tielink/navigation/NavGraph.kt`, `app/src/main/java/com/example/tielink/di/AppModule.kt`
- Modify: `docs/PHASE_4_PROGRESS.md`

- [ ] 移除 app 中已迁移的业务实现与不再需要的直接三方依赖。
- [ ] 保留 `TieLinkApp`、顶层导航、DI 组装和自动化平台入口。
- [ ] 运行 `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --offline --console=plain`。
- [ ] 更新 Phase 4 状态为已完成并提交：`重构：完成 Phase 4 模块化`。
