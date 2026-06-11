# Assets 目录

本目录包含应用运行所需的资源文件。

## 📁 文件列表

### 必需文件

1. **skill_dict.json** ✅ (已包含)
   - 技能分类字典（12大领域，500+关键词）
   - 用于关键词分类展示

2. **text2vec_base_chinese_quantized.tflite** ❌ (需下载)
   - 中文Embedding模型
   - 用于语义匹配算法
   - 大小: ~50MB
   - 下载地址: 见 [EMBEDDING_MODEL_GUIDE.md](../../EMBEDDING_MODEL_GUIDE.md)

## ⚠️ 重要提示

**如果缺少Embedding模型文件，应用仍可正常运行！**

系统会自动降级到TF-IDF算法：
- 功能不受影响
- 匹配准确度略低（约60% vs 95%）
- 日志会显示降级提示

## 📥 快速开始

### 方式A：完整功能（推荐）

1. 下载 `text2vec_base_chinese_quantized.tflite`
2. 放置到此目录
3. 重新编译应用

### 方式B：基础使用（无需下载）

- 直接编译运行
- 使用TF-IDF算法
- 后续随时升级到Embedding

## 🔧 开发说明

如需自定义模型：

1. 转换为TFLite格式
2. 更新 [EmbeddingEngine.kt](../java/com/example/cv_jobmatcher/domain/nlp/EmbeddingEngine.kt) 中的常量:
   ```kotlin
   private const val MODEL_FILE = "your_model.tflite"
   private const val EMBEDDING_DIM = 768
   ```
3. 放置新模型文件到本目录

## 📊 模型对比

| 模型 | 大小 | 准确度 | 速度 | 推荐场景 |
|------|------|--------|------|---------|
| TF-IDF (无模型) | 0KB | ★★☆☆☆ | <10ms | 开发测试 |
| text2vec-base-chinese | ~50MB | ★★★★★ | <100ms | **生产环境** |
| paraphrase-multilingual | ~80MB | ★★★★☆ | <120ms | 多语言场景 |
| 自定义模型 | 可变 | 可变 | 可变 | 特殊需求 |

---

**最后更新**: 2026-06-05  
**版本**: v2.0
