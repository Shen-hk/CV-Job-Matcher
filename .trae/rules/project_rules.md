# CVJobMatcher 项目规范

## 项目概述
Android 简历优化与职位匹配应用，使用 Kotlin + Jetpack Compose + Hilt + Moshi。

## 技术栈
- 语言: Kotlin
- UI: Jetpack Compose + Material3
- DI: Hilt
- JSON: Moshi
- 网络: Retrofit + OkHttp
- 本地存储: Room
- 构建: Gradle (Kotlin DSL) + Version Catalog (libs.versions.toml)

## 代码规范

### 命名
- 类名: PascalCase
- 函数/变量: camelCase
- 常量: UPPER_SNAKE_CASE
- 包名: 全小写，用点分隔

### 架构
- MVVM 架构
- ViewModel 使用 Hilt 注入
- Repository 模式访问数据层
- UseCase 封装业务逻辑

### 文件组织
- `ui/` - Compose UI 界面
- `domain/model/` - 领域模型
- `domain/usecase/` - 业务用例
- `domain/nlp/` - NLP 相关
- `data/repository/` - 数据仓库
- `data/remote/` - 远程 API
- `data/local/` - 本地存储
- `di/` - 依赖注入模块
- `util/` - 工具类
- `navigation/` - 导航图

### Compose 规范
- 使用 `@Composable` 函数
- State 通过 `StateFlow` + `collectAsStateWithLifecycle()` 管理
- 使用 Material3 组件
- 主题色定义在 `ui/theme/` 中

### 构建命令
- 编译: `.\gradlew.bat compileDebugKotlin`
- 完整构建: `.\gradlew.bat assembleDebug`
- 测试: `.\gradlew.bat test`

### 注意事项
- 不要添加不必要的注释
- 使用 `Log.d(TAG, ...)` 进行调试日志
- 异常处理使用 `try/catch` + `Result` 或 `fold`
- 协程使用 `viewModelScope` 或 `withContext(Dispatchers.IO)`
