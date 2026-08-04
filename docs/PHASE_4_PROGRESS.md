# Phase 4：Gradle 模块化进度

状态：进行中

## 已完成

- 新建 `:core:model`，承载稳定、无 UI/数据层依赖的岗位、历史记录、当前 JD 状态与面试计时模型。
- 新建 `:core:domain`，承载 Agent Gateway、提示词来源和当前 JD 上下文契约。
- `:app` 已通过项目依赖消费两个 core 模块；迁移后的包名保持不变，调用方不需要改名。
- 验证：`:core:model:assembleDebug`、`:core:domain:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 已将无 Android、UI 与持久化依赖的 Agent 上下文/意图、面试会话与结果、简历版本、简历结构及匹配分析模型迁移到 `:core:model`。
- 验证：`:core:model:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 已将 Room、DataStore、网络适配器、仓储及文件解析实现迁移至新建的 `:core:data`；包名保持不变，`app` 通过项目依赖消费该模块。
- 已将 TensorFlow 语义嵌入适配器迁入 `:core:data`，并修正跨模块访问可空模型属性时的 Kotlin 智能转换限制。
- 验证：`:core:data:compileDebugKotlin`、`:app:compileDebugKotlin` 通过。

## 后续顺序

1. 迁移剩余稳定模型与纯领域规则到 `:core:model` / `:core:domain`。当前保留在 `:app` 的聊天卡片、草稿快照、历史列表和 JSON 编解码模型仍依赖 UI 或持久化实现。
2. `:core:data` 已完成源码迁移；下一步执行完整编译验证并处理跨模块依赖。
3. 将设计系统提取为 `:core:ui`，再按 Agent、简历、面试、投递和设置建立 feature 模块。
4. 每次迁移后执行单测、Lint 与 Release 构建，保持应用可发布。
