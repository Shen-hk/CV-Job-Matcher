# APP Figma 设计源与场景清单

本目录记录 2026-11-25 的整套 APP 设计工作。主交付在原 Figma 文件的 **12 · APP 完整体验与场景 · 11.25** 页，入口与逐页链接见 [APP 设计覆盖](../../APP_DESIGN_COVERAGE.md)。

- `scene-manifest.mjs`：97 个手机页面/状态的分组、内容、入口、返回和分支。
- `build-in-figma.ts`：在 Figma 可见的 Scripter 插件中使用的构建正文。它使用原文件的字体、色彩变量、图标、按钮组件与工作台画板，并以原生图层和组件实例输出。
- `write-coverage.mjs`：把本轮实际 Figma 节点与验证记录整理为交接文档和 `output/tielink-app-design` 中的索引。

构建正文依赖运行器注入 `GROUPS`、`SCENES`、`PROOF_IDS` 和 `REBUILD_IDS`。它不是 Android 代码，也不是可以直接打开运行的 Web 原型。重建时必须先选择上述目标页；脚本会核对页面名称，只调整带本轮所有者标记的产物，原有设计页面继续保留。

Figma MCP 因 Starter 调用额度不可用，本轮通过浏览器中的 Figma 编辑器与正常插件界面完成设计。原生页面、文字、组件和原型连接均保存在原文件中；导出的 PNG 仅作预览，未贴回设计文件替代可编辑界面。
