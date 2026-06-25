---
name: vibe-resume-editor
description: 用于编辑和优化 Vibe 风格简历模板。支持 Demo 模式（mock 数据展示）和个人模式（真实简历数据填充）。通过 CSS 变量调优确保单页 A4 PDF 输出。
---

# VibeResume 模板 AI 编辑规范

## 核心规则

1. **不编造经历** — 只使用 `ResumeData` 中已有的数据，不添加虚构的工作/项目/技能
2. **保持一页 PDF** — 所有内容必须适配单页 A4（210mm × 297mm），通过 CSS 变量调优密度
3. **简洁正式措辞** — 工作描述用动词开头（负责/主导/参与/实现），避免口语化
4. **不修改模板结构** — 保持 HTML section 顺序和 class 命名不变，只替换 `{{placeholder}}` 内容
5. **样式分离** — CSS 变量统一在 `:root` 中管理，不在 HTML 元素上写内联样式
6. **Logo 可选** — 公司 Logo 和项目图标为可选字段，无数据时不渲染对应 `<img>` 元素

## Demo / 个人模式切换

- **Demo 模式**：模板使用 mock 数据展示效果，所有 `{{placeholder}}` 填充示例内容，数据标注 `(示例)` 前缀
- **个人模式**：从 `ResumeData` 对象填充真实简历数据，通过 `HtmlPdfExporter.buildVibeHtml()` 完成

### Demo 模式示例数据

```
{{name}}       → 张小明
{{eyebrow}}    → 求职意向: 高级 Android 开发工程师
{{identityLine}} → Android 开发 | 5 年经验 | 本科
{{summary}}    → 5 年 Android 开发经验，主导过多款百万级用户 App 的架构设计与性能优化...
```

## 编辑工作流

1. **检查模板** — 确认 `vibe_resume_template.html` 存在且 CSS 变量完整
2. **识别模式** — 判断是 Demo 还是个人简历模式
3. **更新 HTML** — 替换 `{{placeholder}}` 为实际内容
4. **调整 CSS** — 根据内容密度调整 `:root` 变量
5. **验证 PDF** — 导出后检查一页完整性、无裁切、预览一致

## CSS 变量对照表

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `--accent` | `#2563eb` | 主色调（标题图标、链接、时间轴圆点） |
| `--accent-soft` | `#eaf1ff` | 浅色强调（section 图标背景、时间轴竖线） |
| `--rust` | `#d97757` | 辅助色（技能列表圆点标记） |
| `--ink` | `#141414` | 正文颜色 |
| `--muted` | `#667085` | 次要文字颜色 |
| `--hairline` | `#dbe2ea` | 边框/分割线颜色 |
| `--panel` | `#f7f9fc` | 面板背景色（教育网格、技能列表、证书标签） |
| `--screen-width` | `1080px` | 屏幕预览宽度 |
| `--print-width` | `210mm` | 打印宽度（A4） |
| `--print-height` | `297mm` | 打印高度（A4） |

### 修改 CSS 变量方法

直接在 `:root` 中修改对应变量值，例如：

```css
:root {
  --accent: #1d4ed8;           /* 更深的蓝色 */
  --accent-soft: #dbeafe;      /* 更浅的背景 */
  --screen-width: 1000px;      /* 更窄的预览宽度 */
}
```

## 模板占位符说明

| 占位符 | 类型 | 说明 |
|--------|------|------|
| `{{avatarBlock}}` | HTML block | 头像区域，无头像时为空字符串 |
| `{{eyebrow}}` | 文本 | 眉题文字，通常为求职意向 |
| `{{name}}` | 文本 | 姓名 |
| `{{identityLine}}` | 文本 | 身份标识行，如"Android 开发 · 5 年" |
| `{{contact}}` | HTML block | 联系方式，带 SVG 图标的链接列表 |
| `{{summary}}` | HTML block | 个人总结段落 |
| `{{educationGrid}}` | HTML block | 教育背景三列网格 |
| `{{educationInfo}}` | HTML block | 教育附加信息（GPA、荣誉等） |
| `{{experiences}}` | HTML block | 工作经历条目列表 |
| `{{projects}}` | HTML block | 项目经历条目列表 |
| `{{skills}}` | HTML block | 技能列表项（`<li>` 元素） |
| `{{certs}}` | HTML block | 证书与资质标签列表 |

## Layout Patterns

### 头像区域
```html
<div class="photo-frame">
  <img class="profile-photo" src="data:image/jpeg;base64,..." alt="头像" />
</div>
```
无头像时 `{{avatarBlock}}` 为空字符串。

### 联系方式
```html
<div class="contact-line">
  <a href="mailto:user@example.com"><svg class="icon"><use href="#icon-mail"/></svg>user@example.com</a>
  <a href="tel:13800000000"><svg class="icon"><use href="#icon-phone"/></svg>138-0000-0000</a>
  <a href="https://github.com/username"><svg class="icon"><use href="#icon-github"/></svg>github.com/username</a>
</div>
```

### 教育背景（三列网格）
```html
<div class="education-grid">
  <div><strong>计算机科学与技术 本科</strong></div>
  <div>某某大学</div>
  <div>2016.09 - 2020.06</div>
</div>
```

### 工作经历（时间轴样式）
```html
<div class="experience">
  <div class="entry-head">
    <div class="company-title"><strong>高级 Android 工程师</strong></div>
    <span>某某科技有限公司</span>
    <span>2020.06 - 至今</span>
  </div>
  <ul>
    <li>主导公司核心 App 架构重构，<strong>性能提升 40%</strong></li>
    <li>搭建组件化框架，覆盖 10+ 业务模块</li>
  </ul>
</div>
```

### 项目经历
```html
<div class="experience">
  <div class="entry-head">
    <div class="project-title"><strong>电商平台 App</strong></div>
    <span></span>
    <span>2022.03 - 2022.09</span>
  </div>
  <p class="summary">从 MVC 迁移到 MVVM + Jetpack Compose，日均 UV 200 万</p>
</div>
```

### 技能列表（双栏）
```html
<ul class="skills-list">
  <li>Kotlin / Java</li>
  <li>Jetpack Compose</li>
  <li>MVVM / MVI 架构</li>
  <li>Coroutines / Flow</li>
</ul>
```

### 证书标签
```html
<div class="cert-list">
  <span class="cert-item">PMP 项目管理认证</span>
  <span class="cert-item">AWS Solutions Architect</span>
</div>
```

## 密度调优表

| 问题 | 调整方案 |
|------|----------|
| 内容溢出到第二页 | 减小 `@media print` 中的 `font-size`，减小 `.page` 的 `padding` |
| 底部留白过多 | 增大 `.section { margin-top }` 或 `.page { padding }` |
| 字号偏大 | 修改 `@media print` 中各元素的 `font-size` |
| 需要紧凑模式 | 减小 `@media print` 中 `.page { padding: 8mm 6mm 6mm }`，减小 `.section { margin-top: 8px }` |

## 导出验证清单

- [ ] 所有内容在一页 A4 内
- [ ] 6 个 section 完整显示（个人总结、教育背景、工作经历、项目经历、专业技能、证书与资质）
- [ ] 无文字裁切或重叠
- [ ] 颜色正确渲染（`print-color-adjust: exact`）
- [ ] 中文字体正常显示
- [ ] SVG 图标正确渲染
- [ ] 时间轴圆点和竖线样式生效
- [ ] 技能列表双栏布局正确
- [ ] 证书 section 有数据时显示，无数据时隐藏
- [ ] 头像区域无数据时不占用视觉空间

## 示例 Prompt

### 场景 1：Demo 模式预览
```
请用 Demo 模式填充 Vibe 简历模板，展示一个 Android 高级工程师的简历效果。
使用 mock 数据，所有占位符填充示例内容。
```

### 场景 2：个人简历填充
```
请根据以下 ResumeData 填充 Vibe 简历模板：
{ "name": "李四", "targetPosition": "Android 开发工程师", ... }
确保所有内容适配单页 A4，必要时调整 CSS 变量。
```

### 场景 3：密度调优
```
当前 Vibe 模板导出 PDF 时内容溢出到第二页，请调整 CSS 变量和打印样式，
压缩间距和字号，确保所有内容适配单页 A4。
```
