# Embedding Model Setup Guide

## 模型文件下载

本项目使用 **text2vec-base-chinese** 量化版Embedding模型进行语义匹配。

### 下载地址

从以下任一位置下载 `text2vec_base_chinese_quantized.tflite` 文件（约50-80MB）：

1. **HuggingFace**: https://huggingface.co/shibing624/text2vec-base-chinese
2. **ModelScope**: https://modelscope.cn/models/Jerry0/text2vec-base-chinese/files

### 安装步骤

1. 下载 `.tflite` 模型文件
2. 将文件重命名为: `text2vec_base_chinese_quantized.tflite`
3. 放置到项目的 `app/src/main/assets/` 目录下
4. 重新编译项目

## 模型信息

| 属性 | 值 |
|------|-----|
| 模型名称 | text2vec-base-chinese (量化版) |
| 输入维度 | 768 |
| 最大序列长度 | 128 tokens |
| 文件大小 | ~50MB (INT8量化) |
| 推理速度 | <100ms (中端手机) |
| 适用语言 | 中文为主，支持英文 |

## 功能特性

✅ **语义级匹配** - 理解同义词、近义词（如"Java开发" ≈ "Java工程师"）
✅ **完全离线** - 无需网络连接，保护隐私
✅ **高效推理** - TFLite GPU加速，流畅运行
✅ **自动降级** - 模型加载失败时自动降级到TF-IDF

## 技术对比

| 指标 | TF-IDF (旧) | Embedding (新) |
|------|-------------|----------------|
| 准确度 | ★★☆☆☆ | ★★★★★ |
| 语义理解 | ❌ 仅关键词 | ✅ 深度语义 |
| 同义词识别 | ❌ | ✅ |
| 上下文感知 | ❌ | ✅ |
| 离线能力 | ✅ | ✅ |
| 模型大小 | 0KB | ~50MB |
| 推理速度 | <10ms | <100ms |

## 故障排除

### 模型未加载

如果看到日志 `"Embedding模型初始化失败"`：
1. 确认文件在正确路径：`assets/text2vec_base_chinese_quantized.tflite`
2. 确认文件名完全一致（注意下划线）
3. 确认文件未损坏（可尝试重新下载）

### 内存不足

如果遇到OOM错误：
1. 在 `AndroidManifest.xml` 添加: `android:largeHeap="true"`
2. 关闭其他占用内存的应用
3. 使用更小的模型版本（如MobileBERT）

### 自动降级

即使模型加载失败，应用仍可正常工作：
- 自动切换到TF-IDF算法
- 功能不受影响，仅准确度略低
- 日志会显示 `"Embedding未就绪，降级到TF-IDF"`

## 高级配置

### 自定义模型

如需使用其他Embedding模型：

1. 转换为TFLite格式（使用TensorFlow Lite Converter）
2. 更新 [EmbeddingEngine.kt](../domain/nlp/EmbeddingEngine.kt) 中的常量：
   ```kotlin
   private const val MODEL_FILE = "your_model_name.tflite"
   private const val EMBEDDING_DIM = 768  // 根据实际模型调整
   ```

### ONNX Runtime支持

项目同时支持ONNX格式模型（通过onnxruntime-android库）：
- 格式: `.onnx`
- 性能: 通常比TFLite快10-20%
- 兼容性: 需要ARM64架构

## 性能优化建议

1. **首次加载**: 应用启动时会预加载模型（约1-2秒）
2. **缓存机制**: Embedding结果可考虑添加本地缓存
3. **批量处理**: 多个文本可批量向量化提升效率
4. **GPU加速**: 确保设备支持OpenGL ES 3.1+

## 许可证

text2vec-base-chinese 模型基于 Apache 2.0 许可证发布。
