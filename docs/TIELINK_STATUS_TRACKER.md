# TieLink 状态看板

> 目的：一眼看清“哪些已经改了、哪些还没改、当前能不能编译”。

## 当前结论

- 编译状态：已通过
- 主入口：聊天页
- 当前产品方向：求职领域的 agent
- 关键交互：聊天中发起，必要时用卡片确认，结果页作为中转预览页

## 已完成

| 状态 | 问题 | 已做什么 | 影响文件 |
|---|---|---|---|
| 已完成 | JD 列表进入跟进页时，上下文丢失 | 进入跟进页时带上公司名、岗位名 | `app/src/main/java/com/example/tielink/navigation/NavGraph.kt` |
| 已完成 | 跟进页新增表单没有业务预填 | 从 JD 进入时自动预填公司、岗位 | `app/src/main/java/com/example/tielink/ui/tracking/TrackingScreen.kt` |
| 已完成 | 跟进页“新增/关闭”状态混在一起 | 拆成 `openAddForm` / `closeAddForm` | `app/src/main/java/com/example/tielink/ui/tracking/TrackingViewModel.kt` |
| 已完成 | 保存投递过于直接 | 先确认卡片，再保存 | `app/src/main/java/com/example/tielink/ui/tracking/TrackingScreen.kt` |
| 已完成 | 状态修改、删除缺少确认 | 增加确认卡片 | `app/src/main/java/com/example/tielink/ui/tracking/TrackingScreen.kt` |
| 已完成 | 保存后没有明确反馈和撤销 | 增加提示与撤销入口 | `app/src/main/java/com/example/tielink/ui/tracking/TrackingScreen.kt` |
| 已完成 | result 页像普通结果页，不像预览中转 | 调整为“预览中转”页 | `app/src/main/java/com/example/tielink/ui/result/ResultScreen.kt` |
| 已完成 | 聊天入口和业务入口不统一 | 聊天页统一走跟进路由 helper | `app/src/main/java/com/example/tielink/navigation/NavGraph.kt` |
| 已完成 | 编译失败：缺少 `ModelConfigScreen` | 补齐设置页入口，恢复构建 | `app/src/main/java/com/example/tielink/ui/settings/ModelConfigScreen.kt` |

## 进行中

| 状态 | 问题 | 还差什么 | 优先级 |
|---|---|---|---|
| 进行中 | 业务上下文仍散落在局部页面 | 需要统一沉淀成共享状态中心 | 高 |

## 未开始

| 状态 | 问题 | 缺少什么 | 建议 |
|---|---|---|---|
| 未开始 | 统一状态中心 | 登录、加载、保存、提交、撤销等状态统一管理 | 先做公共状态层 |
| 未开始 | 异常链路不够完整 | 网络失败、权限拒绝、会话过期、空数据兜底 | 补齐异常分支与重试 |
| 未开始 | 业务规则还不够统一 | 不同页面对“能不能改、何时保存”规则不一致 | 输出统一业务规则表 |
| 未开始 | 流程评审机制缺失 | 新功能容易再次分散 | 新增业务必须过流程评审 |
| 未开始 | 合规与权限时机标准化不足 | 权限申请、隐私提示、操作记录策略未统一 | 建一份标准模板 |

## 下一步建议

1. 先收口“统一状态中心”，把登录、保存、撤销、加载这些公共状态做成统一入口。
2. 再补“异常链路兜底”，把网络失败、空数据、会话过期这些边缘情况补齐。
3. 最后把业务规则和评审机制固化，避免后面又散回去。

