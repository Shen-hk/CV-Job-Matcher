# Phase 4 模块化设计

## 目标

将应用从单一 `:app` 模块拆分为可独立编译的 core 与 feature 模块，保持现有包名、导航行为和本地数据兼容。

## 已完成的基础层

- `:core:model`：稳定领域模型与纯编辑规则。
- `:core:domain`：Agent 契约、上下文与纯 NLP 规则。
- `:core:data`：Room、DataStore、网络、仓储、文件解析与 TensorFlow 嵌入适配器。
- `:core:ui`：Compose 主题、色彩、排版、间距和动效令牌。

## 后续模块边界

### core:ui

继续承载不含业务状态的可复用 Compose 组件：错误提示、加载层、基础卡片、分数图表和通用 UI 原语。组件只能依赖 Compose 与其他 core 模块，不得依赖导航、ViewModel 或 feature。

### feature:agent

承载 Agent 聊天页面、卡片渲染、聊天状态和 Agent ViewModel。依赖 `core:model`、`core:domain`、`core:data` 与 `core:ui`。

### feature:resume

承载 JD 输入、简历输入、润色结果、简历库与简历优化界面及其 ViewModel。依赖四个 core 模块；文件导入继续通过 `core:data`。

### feature:interview

承载模拟面试、复盘页面、语音交互和对应 ViewModel。依赖四个 core 模块。

### feature:tracking

承载投递记录列表、详情和 ViewModel。依赖 `core:model`、`core:data` 与 `core:ui`。

### feature:settings

承载设置及 AI Provider 配置界面和 ViewModel。依赖 `core:data` 与 `core:ui`。

### app

仅保留应用启动、Hilt 组装、顶层导航、自动化服务和不能归入 feature 的平台入口。app 不再直接拥有 core 或 feature 的业务实现。

## 迁移顺序与验证

1. 迁移 `core:ui` 通用组件并编译 `:core:ui` 与 `:app`。
2. 依次迁移 Agent、简历、面试、投递、设置 feature；每个 feature 独立提交并执行对应模块和 app 编译。
3. 收缩 app 到启动、导航和 DI；验证单测、Lint 与 Release 构建。

每次迁移保留 `com.example.tielink` 包名，避免大规模调用方改名。每次提交不得包含 `build/` 产物。
