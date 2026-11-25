# TieLink：整套 APP 场景与 Figma 交接

> 2026-11-25。本轮交付以 Figma 的 APP 页面、场景状态和原型连接为主，未继续扩展 Android 功能。

[查看整套 APP 预览](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363750-10104) · [设计总览](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-7067) · [打开可点击原型](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1261&page-id=1519363719%3A505&starting-point-node-id=1519363723%3A1261&scaling=min-zoom&content-scaling=fixed)

设计只写入原文件的新页面 **12 · APP 完整体验与场景 · 11.25**。沿用原文件的 Noto Sans SC 中文字体、色彩变量、图标、按钮与专业工作台。原有页面保留，01b 的团队首页稿继续作为对照。

本页共 97 个手机页面与状态，其中 73 个新增场景、24 个复用的专业工作台状态；另有 APP 总览和八场景预览。主 Agent 保持默认入口；任务、团队、工具、资料与成果均有独立页面。

## 导航与用户旅程

- 一级入口：Agent、任务、团队、资料；复杂编辑、预览和专业工作台从相应上下文进入。
- 没有团队也能开始；创建、编辑和选用团队不会启动业务任务。
- 委托之后可以离开聊天；任务页与通知呈现动作、可靠阶段成果和处理入口。
- 文档修改、HTML/PDF 查看、版本关系、采用为正式资料和文件导出各有明确状态。
- 团队快照、资料引用、会话归属、输入保留、失败恢复和未保存修改都在场景中体现。

## 场景分组

| 分组 | 页面与状态 | 范围 |
| --- | ---: | --- |
| A · 首次使用 | 5 | 开始使用、配置模型与先浏览 |
| H · 主 Agent 与全局入口 | 4 | 对话承接需求，任务、团队、资料独立可达 |
| T · 团队与客制化 | 11 | 发现、创建、角色、工具、保存与版本 |
| U · 工具中心 | 4 | 单工具使用、能力说明与未接入状态 |
| L · 资料中心 | 7 | 导入、解析、引用、查找和删除 |
| B · 后台任务与系统通知 | 20 | 排队、执行、阶段成果、干预与恢复 |
| D · 成果工作台 | 14 | 文档、HTML、PDF、版本、采用与导出 |
| S · 历史、记忆与设置 | 11 | 跨会话管理、偏好、模型与全局异常 |
| J · 求职场景整合 | 21 | 沿用现有 JD、简历、面试与投递设计 |

## 可点击主流程起点

- [01 首次使用](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7592&page-id=1519363719%3A505&starting-point-node-id=1519363740%3A7592&scaling=min-zoom&content-scaling=fixed)
- [02 主 Agent 与全局导航](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1261&page-id=1519363719%3A505&starting-point-node-id=1519363723%3A1261&scaling=min-zoom&content-scaling=fixed)
- [03 创建与定制团队](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1358&page-id=1519363719%3A505&starting-point-node-id=1519363723%3A1358&scaling=min-zoom&content-scaling=fixed)
- [04 资料与工具](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1586&page-id=1519363719%3A505&starting-point-node-id=1519363723%3A1586&scaling=min-zoom&content-scaling=fixed)
- [05 后台任务与成果](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1731&page-id=1519363719%3A505&starting-point-node-id=1519363723%3A1731&scaling=min-zoom&content-scaling=fixed)
- [06 系统通知](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7821&page-id=1519363719%3A505&starting-point-node-id=1519363740%3A7821&scaling=min-zoom&content-scaling=fixed)
- [07 文档与版本](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8008&page-id=1519363719%3A505&starting-point-node-id=1519363740%3A8008&scaling=min-zoom&content-scaling=fixed)
- [08 简历工作台](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5834&page-id=1519363719%3A505&starting-point-node-id=1519363727%3A5834&scaling=min-zoom&content-scaling=fixed)
- [09 模拟面试](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6244&page-id=1519363719%3A505&starting-point-node-id=1519363727%3A6244&scaling=min-zoom&content-scaling=fixed)
- [10 投递记录](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8908&page-id=1519363719%3A505&starting-point-node-id=1519363740%3A8908&scaling=min-zoom&content-scaling=fixed)
- [11 历史与设置](https://www.figma.com/proto/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4651&page-id=1519363719%3A505&starting-point-node-id=1519363727%3A4651&scaling=min-zoom&content-scaling=fixed)

## 保留的专业分支

以下原页面完整保留；本轮把典型入口和主干状态接入新 APP 原型，不重复重画整套专业界面。

- [04 · 岗位 JD](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-8741)：23 个既有页面/状态（不含分区导览）。
- [05 · 简历库](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-11077)：11 个既有页面/状态（不含分区导览）。
- [06 · 简历优化](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-12312)：24 个既有页面/状态（不含分区导览）。
- [07 · 简历作品台](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-14453)：21 个既有页面/状态（不含分区导览）。
- [08 · 面试与复盘](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-16124)：37 个既有页面/状态（不含分区导览）。
- [09 · 投递节奏](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-19381)：18 个既有页面/状态（不含分区导览）。
- [10 · 设置与系统状态](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363703-21592)：17 个既有页面/状态（不含分区导览）。

## 验证与交付边界

已检查 97 个手机画板均为 390 × 844；画板中有 924 个组件实例和 1544 个可编辑文字图层。画面由原生图层与组件构成，没有将整屏截图贴回 Figma 充当设计。

检查到 470 项手机场景内交互，目标节点缺失 0 项、缺失字体 0 项、文本高度检查异常 0 项。画布总览入口另行提供导航，不把总览或预览副本计为新手机场景。

浏览器实际点通记录：team 9 步；task 8 步；resources 8 步；career 7 步；onboarding 4 步。明细见输出目录 verification.json。

Figma 原型使用示例内容与预设状态。它用于审阅页面、入口、返回路径和状态关系；文字输入、系统通知、模型调用、文件读写与 Android 生命周期不会因此成为已实现功能。

## 逐页索引

| 编号 | 页面 / 状态 | 设计位置 |
| --- | --- | --- |
| A01 | 让灵感成为成果 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7592) |
| A02 | 由你决定如何提醒 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-1901) |
| A03 | 先连接一个模型 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7643) |
| A04 | 首次连接模型 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7683) |
| A05 | 为当前任务连接模型 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7752) |
| H01 | TieLink | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1261) |
| H02 | 面试复盘助手方案 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-1989) |
| H03 | 新的对话 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2089) |
| H04 | 直接使用工具 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2186) |
| T01 | 我的团队 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1358) |
| T02 | 我的团队 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2266) |
| T03 | 简历团队 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2344) |
| T04 | 创建团队 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2401) |
| T05 | 团队配置 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2450) |
| T06 | 编辑角色 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2515) |
| T07 | 团队工具 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2568) |
| T08 | 工具依赖需要调整 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2629) |
| T09 | 团队已保存 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2666) |
| T10 | 运行中的团队版本 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2716) |
| T11 | 删除团队 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2771) |
| U01 | 工具中心 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1441) |
| U02 | HTML 生成 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2808) |
| U03 | PDF 查看 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2860) |
| U04 | 能力暂未接入 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2908) |
| L01 | 资料与成果 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1586) |
| L02 | 添加资料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-2956) |
| L03 | 正在读取资料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3015) |
| L04 | 资料没有读完整 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3057) |
| L05 | 选择本次资料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3097) |
| L06 | 资料详情 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3151) |
| L07 | 删除引用中的资料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3213) |
| B01 | 我的任务 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3250) |
| B02 | 我的任务 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3342) |
| B03 | 任务进度 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363723-1731) |
| B04 | 等待开始 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3411) |
| B05 | 阶段成果 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3464) |
| B06 | 补充任务要求 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3518) |
| B07 | 需要你补充资料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3566) |
| B08 | 等待网络 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3620) |
| B09 | 正在暂停 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3670) |
| B10 | 任务已暂停 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3713) |
| B11 | 取消这次任务 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3763) |
| B12 | 部分工作未完成 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3800) |
| B13 | 恢复上次任务 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3857) |
| B14 | 任务已完成 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-3914) |
| B15 | 通知中心 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7821) |
| B16 | 锁屏通知 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7868) |
| B17 | 未允许系统通知 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4037) |
| B18 | 等待顺序已调整 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4087) |
| B19 | 正在更新关联文件 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7903) |
| B20 | 本次修改已完成 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-7960) |
| D01 | 文档成果 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4132) |
| D02 | 一页方案 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8008) |
| D03 | 修改文档 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4228) |
| D04 | HTML 预览 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4269) |
| D05 | PDF 查看 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8051) |
| D06 | 版本与修改记录 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4357) |
| D07 | 内容已更新 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8095) |
| D08 | 采用为正式简历 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4464) |
| D09 | 导出成果 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8148) |
| D10 | 这次没有导出成功 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4555) |
| D11 | 这个版本暂不可用 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4603) |
| D12 | 文件已导出 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8195) |
| D13 | 一页方案 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8243) |
| D14 | HTML 预览 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8286) |
| S01 | 设置与偏好 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4651) |
| S02 | 模型配置 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8328) |
| S03 | 通知与锁屏隐私 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4783) |
| S04 | 记忆与偏好 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4839) |
| S05 | 编辑记忆偏好 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4894) |
| S06 | 会话历史 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4941) |
| S07 | 删除运行中的会话 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-4996) |
| S08 | 还没有保存的修改 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5033) |
| S09 | 当前连接不可用 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5070) |
| S10 | 成果保存未完成 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5127) |
| S11 | 还没有保存的文档 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5175) |
| J01 | 岗位 JD 库 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8397) |
| J02 | 添加岗位材料 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5330) |
| J03 | 岗位详情与要求 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5471) |
| J04 | 简历库与版本 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8515) |
| J05 | 新增与上传简历 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5677) |
| J06 | 简历优化工作台 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5834) |
| J07 | 岗位匹配与建议 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-5953) |
| J08 | 简历作品台 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6024) |
| J09 | 简历导出选项 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6116) |
| J10 | 模拟面试准备 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6244) |
| J11 | 模拟面试进行中 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8636) |
| J12 | 面试评估与改进 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8737) |
| J13 | 真实面试复盘 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6480) |
| J14 | 复盘报告与训练 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8799) |
| J15 | 投递节奏与记录 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-8908) |
| J16 | 新增投递记录 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363727-6843) |
| J17 | 投递详情与时间线 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-9106) |
| J18 | 系统保存位置 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-9203) |
| J19 | 结束面试确认 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-9264) |
| J20 | 面试转写记录 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-9382) |
| J21 | 修改投递状态 | [打开 Figma](https://www.figma.com/design/9Wp2MFkHvQhRoWPA1IQ2RN?node-id=1519363740-9444) |
