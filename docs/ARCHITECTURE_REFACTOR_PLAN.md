# TieLink 架构优化与演进计划

状态：Phase 2 已完成

## 目标

在保留功能与用户数据兼容性的前提下，逐步将依赖方向收紧为：

```text
UI / feature -> domain contracts + use cases <- data implementations
                         ^
                      core models
```

UI 不直接感知 Room Entity、DAO、网络 DTO 或具体 AI Provider；领域层不直接感知具体远程协议。

## 已完成

### Phase 1：Agent 边界与工具注册

- 建立 `domain/agent` 聊天、流式响应、工具与提示词契约。
- 在 data 层实现协议适配，`AgentUseCase` 与 `AgentToolCoordinator` 不再引用远程 DTO。
- 抽出 `AgentToolRegistry` 并添加工具注册表单元测试。

### Phase 2：Presentation 与数据模型收口

- 新增领域模型：`SavedJobDescription`、`NewJobDescription`、`HistoryRecord`。
- 职位库与历史记录 Repository 对外不再暴露 Room Entity。
- JD 列表、面试、复盘、简历输入、简历库、结果、润色、Agent 和设置 UI 已移除 Room Entity/DAO、LLM DTO 与具体 Provider 的直接导入。
- JD 提取与保存下沉到 `JdLibraryUseCase`；设置连接测试下沉至 `SettingsRepository`。
- 用 `CurrentJobContext` + `CurrentJobContextStore` 替代 `GlobalJdStateHolder`，并通过 CompositionLocal/DI 消费接口。
- 新增 `AppContentStore`：JD 正文、简历正文与 Agent 草稿保存在应用私有文件；首次读取会迁移并清理旧 DataStore 值。DataStore 保留轻量偏好和受保护配置。
- 移除 Route 中的 JD、简历与 JSON 正文。路由只标识页面，目标页面通过 `SavedStateHandle` 恢复一次性流程数据。
- 已验证 Kotlin/Hilt 编译、单元测试与 Android Lint。

## 后续阶段

### Phase 3：功能内聚与测试

1. 拆分过大的 Agent、结果与面试页面文件，让状态、草稿、附件和卡片操作独立负责。
2. 按 JD、简历、面试、投递、机会分析与动态卡片执行器拆分 Agent 协调器。
3. 增加迁移、导航恢复和端到端测试。

### Phase 4：按稳定边界模块化

仅在前述边界稳定后迁移 Gradle 模块：

```text
:app
:core:model
:core:domain
:core:data
:core:ui
:feature:agent
:feature:resume
:feature:interview
:feature:tracking
:feature:settings
```

## 非目标

- 不删除历史页面、数据库表或迁移来简化结构。
- 不改变 Agent 工具名称、提示词语义或既有数据格式。
- 不在缺少可独立构建、测试和回滚能力时一次性迁移全部功能。
