# TieLink 性能优化一日作战手册（2026-10-10 执行）

> 今晚已完成：代码侦察、benchmark 骨架、release 签名配置。明天照表执行即可。

---

## 0. 今晚侦察结论（优化点已实锤，带文件行号）

| # | 优化点 | 证据 | 预期收益 |
| :-- | :-- | :-- | :-- |
| 1 | **冷启动主线程加载模型**：`TieLinkApp.onCreate`（L10-18）同步调 `EmbeddingEngine.init()`——映射 50-80MB 量化 BERT + `readLines()` 读全量 vocab | `app/src/main/java/com/example/tielink/TieLinkApp.kt` | 冷启动 timeToInitialDisplay 显著下降；「隐性好炸弹」解除 |
| 2 | **流式高频重组**：`AgentViewModel` 的 `streamingContentBuf`（L95）高频 append → `_uiState` 更新 → `AgentChatScreen` LazyColumn 可能全量重组 | `feature/agent/.../AgentViewModel.kt` | 滚动 FrameTiming P90 下降 |
| 3 | **大文件内存**：`FileParser` PDFBox `PDDocument` 全量载入内存 | `core/data/.../FileParser.kt` | 大简历 PDF 不 OOM |
| 4 | **包体积**：模型内置 ~50MB（当前缺失待下载）vs 按需下载 | `app/src/main/assets/README.md` | 体积叙事 |
| 5 | **Room 索引**：InterviewMessage / ProviderModel 已有索引；tracking / resume_versions 顺手核查 | `core/data/.../entity/` | 小 |

关键背景：**embedding 模型文件当前不在仓库里**（assets/README.md 写明「text2vec_base_chinese_quantized.tflite ❌ 需下载」），所以现在启动时 init 快速失败降级 TF-IDF。这意味着：优化点 1 目前测不出大数字，除非先把模型放进去（见 3.1）。

## 1. 今晚已改好的东西

1. `baselineprofile/.../StartupBenchmark.kt`（新增）：冷启动 ×2 编译模式 + 聊天页滚动 FrameTiming，注释里有完整用法
2. `app/build.gradle.kts` release 块加了 `signingConfig = debug`——Macrobenchmark 必须装 release 包，原来 unsigned 装不上
3. `baselineprofile/build.gradle.kts` 加了 `testBuildType = "release"`——否则基准会装 debug 包被拒

⚠️ **未完成项**：编译验证被 `~/.gradle/caches/modules-2/modules-2.lock` 挡住（Android Studio 守护进程占锁）。风险很低（新文件用的依赖与已有 BaselineProfileGenerator 完全相同），明早第 0 步处理。

## 2. 时间表

### 上午 · 启动专项（9:00-12:00）

**第 0 步（9:00）解锁编译**
- 关闭/等待 Android Studio 后台同步结束，然后在项目目录跑：
  ```powershell
  .\gradlew.bat --stop
  .\gradlew.bat :baselineprofile:assembleRelease :app:assembleRelease
  ```
- 通过 = 骨架 OK。失败则看报错发我。

**第 1 步（9:15）下载模型（并行做，失败不阻塞）**
- `https://hf-mirror.com/shibing624/text2vec-base-chinese` 找 quantized tflite + vocab.txt，放入 `app/src/main/assets/`
- 拿不到 → 走方案 B：不放模型，启动故事改为「初始化编排正确性 + StrictMode 主线程零 IO + 防劣化设计」（依然成立，只是数字小）

**第 2 步（9:30）测基线**
```powershell
adb devices                      # 确认真机(API 28+)，解锁屏幕、电量>50%
.\gradlew.bat :baselineprofile:connectedAndroidTest
```
- 报告在 `baselineprofile/build/outputs/connected_android_test_additional_output/`
- 记录：`timeToInitialDisplayMs`（NoCompilation 与 BaselineProfile 两组）、`frameDurationCpuMs` P50/P90/P99

**第 3 步（10:30）改启动编排**
- `TieLinkApp.onCreate` 只留必须项；`EmbeddingEngine.init` 挪到后台线程（协程/Executors），首次 `embed()` 前用 latch/deferred 等待——**懒加载 + 后台初始化**二选一或组合
- 开 StrictMode（ThreadPolicy detectAll + penaltyLog）验证主线程零模型 IO
- 重跑 StartupBenchmark → **before/after 数字**

**第 4 步（11:30）生成 Baseline Profile（顺手收启动第二级收益）**
```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile
```

### 下午 · 流式重组 + 收尾（13:30-18:00）

**第 5 步（13:30）重组定位**：Android Studio → Layout Inspector → 开重组计数，让 AI 回一段长文，观察 AgentChatScreen 哪些节点疯狂重组

**第 6 步（14:00）优化三板斧**
- `derivedStateOf` 收敛高频读取；列表 item 用稳定 key + 不可变数据类
- chunk 攒批节拍渲染（每 ~66ms 合并提交一次，StockChat 的 TypewriterSmoother 同款思想，可参考 `D:/Project/StockChat/shared/.../TypewriterSmoother`）
- 重跑 `scrollAgentChat` → FrameTiming 前后对比

**第 7 步（15:30）包体积 + Room**：模型内置 vs 外置体积对比表；tracking/resume_versions 表补索引（如缺）

**第 8 步（16:30）稳定性加分项**：全局 Crash 捕获（Thread.setDefaultUncaughtExceptionHandler）+ 本地落盘 + 下次启动展示恢复提示，~1 小时

**第 9 步（17:30）整理数据 → 更新简历**（文案模板见 §5）

## 3. 命令速查

```powershell
# 编译验证
.\gradlew.bat --stop; .\gradlew.bat :baselineprofile:assembleRelease :app:assembleRelease

# 装被测包（connectedAndroidTest 会自动装，手动装用这条）
adb install -r app\build\outputs\apk\release\app-release.apk

# 跑全部基准 / 只跑启动
.\gradlew.bat :baselineprofile:connectedAndroidTest
.\gradlew.bat :baselineprofile:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.tielink.baselineprofile.StartupBenchmark

# 基准跑不动时：屏幕常亮解锁、关掉省电模式、`adb shell settings put global window_animation_scale 0` 不必须但稳
```

## 4. 风险与备选

| 风险 | 备选 |
| :-- | :-- |
| 模型下载失败（HF 量化版非官方产物） | 方案 B：启动故事 = 编排正确性 + StrictMode 零主线程 IO + 防劣化；或今晚/明早用 AI Edge Converter 自转换（耗时，慎选） |
| 明天没有真机 | 模拟器可跑（API 28+），数字标注「模拟器实测」，说服力打折 |
| release R8 裁掉 TFLite/ML Kit 反射类启动即崩 | 检查 `proguard-rules.pro` 补 keep；此前 release 构建产物存在，大概率没问题 |
| Gradle 锁再次被占 | 关 Android Studio 或等其同步完；`gradlew --stop` 重试 |

## 5. 简历文案模板（明天填数字即用）

- **启动**：「基于 Macrobenchmark（StartupTimingMetric × CompilationMode 对照）建立冷启动基线，将 50MB TFLite 模型与词表加载移出主线程，改为后台初始化 + 首次使用等待的启动任务分级编排，StrictMode 验证主线程零 IO，冷启动 timeToInitialDisplay 从 __ms 降至 __ms（__ 实测）」
- **流式**：「针对 AI 流式输出高频状态更新引发的 LazyColumn 全量重组，采用 derivedStateOf 收敛读取、稳定 key 与分片节拍提交，滚动帧耗时 P90 从 __ms 降至 __ms」
- **稳定性**：「实现全局 Crash 捕获、本地落盘与下次启动现场恢复，建立 ANR 观测埋点」
- **方法论文案（面试口述）**：「低端机 + 极端数据是客户端性能专项的标准打法：我用 __ 设备 + __ 条长会话 + __MB PDF 做压力场景，所有优化都有 Macrobenchmark before/after 数据」

## 6. 明日验收清单

- [ ] StartupBenchmark 基线报告 + 优化后报告（截图存档）
- [ ] FrameTimingMetric 前后对比
- [ ] StrictMode 日志证明主线程零模型 IO
- [ ] 包体积前后数字
- [ ] Crash 捕获演示录屏（可选）
- [ ] `outputs/resume-tielink-project.md` 更新为新版 bullet
