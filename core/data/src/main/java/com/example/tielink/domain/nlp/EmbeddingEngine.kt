package com.example.tielink.domain.nlp

import android.content.Context
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Tensor
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.sqrt

object EmbeddingEngine {
    private const val TAG = "EmbeddingEngine"
    private const val MODEL_FILE = "text2vec_base_chinese_quantized.tflite"
    private const val VOCAB_FILE = "vocab.txt"
    private const val DEFAULT_EMBEDDING_DIM = 768
    private const val DEFAULT_SEQUENCE_LENGTH = 128

    private var interpreter: Interpreter? = null
    private var tokenizer: BertTokenizer? = null
    private var contract: ModelContract? = null

    @Synchronized
    fun init(context: Context) {
        if (isReady()) return

        var candidate: Interpreter? = null
        try {
            candidate = Interpreter(
                loadModelFile(context),
                Interpreter.Options().apply { setNumThreads(4) }
            )
            val candidateContract = inspectContract(candidate)
            val vocabulary = context.assets.open(VOCAB_FILE).bufferedReader(Charsets.UTF_8).use { reader ->
                reader.readLines().mapIndexed { index, token ->
                    if (index == 0) token.removePrefix("\uFEFF") else token
                }
            }

            tokenizer = BertTokenizer(vocabulary)
            contract = candidateContract
            interpreter = candidate
            Log.i(
                TAG,
                "Embedding model ready: inputs=${candidateContract.inputSummary}, " +
                    "output=${candidateContract.outputSummary}, pooling=${candidateContract.pooling}"
            )
        } catch (exception: Exception) {
            candidate?.close()
            interpreter = null
            tokenizer = null
            contract = null
            Log.e(
                TAG,
                "Embedding initialization failed. Both $MODEL_FILE and its matching $VOCAB_FILE are required: " +
                    exception.message,
                exception
            )
        }
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context): MappedByteBuffer =
        context.assets.openFd(MODEL_FILE).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { inputStream ->
                inputStream.channel.use { channel ->
                    channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength
                    )
                }
            }
        }

    @Synchronized
    fun embed(text: String): FloatArray {
        val activeInterpreter = interpreter
        val activeTokenizer = tokenizer
        val activeContract = contract
        if (activeInterpreter == null || activeTokenizer == null || activeContract == null) {
            Log.w(TAG, "Embedding model is not initialized; returning a zero vector")
            return FloatArray(DEFAULT_EMBEDDING_DIM)
        }

        return try {
            val encoded = activeTokenizer.encode(text, activeContract.sequenceLength)
            val modelInputs = Array<Any>(activeInterpreter.inputTensorCount) { inputIndex ->
                val tensor = activeInterpreter.getInputTensor(inputIndex)
                val values = when (activeContract.inputRoles.getValue(inputIndex)) {
                    InputRole.IDS -> encoded.inputIds
                    InputRole.MASK -> encoded.attentionMask
                    InputRole.TOKEN_TYPES -> encoded.tokenTypeIds
                }
                values.toTensorInput(tensor.dataType())
            }

            val outputTensor = activeInterpreter.getOutputTensor(activeContract.outputIndex)
            val outputShape = outputTensor.shape()
            val modelOutputs = mutableMapOf<Int, Any>()
            val rawOutput: Any = when (outputShape.size) {
                2 -> Array(outputShape[0]) { FloatArray(outputShape[1]) }
                3 -> Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
                else -> error("Unsupported embedding output shape ${outputShape.contentToString()}")
            }
            modelOutputs[activeContract.outputIndex] = rawOutput
            activeInterpreter.runForMultipleInputsOutputs(modelInputs, modelOutputs)

            @Suppress("UNCHECKED_CAST")
            val embedding = when (activeContract.pooling) {
                Pooling.MODEL_OUTPUT -> (rawOutput as Array<FloatArray>)[0]
                Pooling.MEAN -> BertPooling.mean((rawOutput as Array<Array<FloatArray>>)[0], encoded.attentionMask)
            }
            BertPooling.l2Normalize(embedding)
        } catch (exception: Exception) {
            Log.e(TAG, "Embedding inference failed: ${exception.message}", exception)
            FloatArray(activeContract.embeddingDimension)
        }
    }

    fun cosineSimilarity(first: FloatArray, second: FloatArray): Float {
        if (first.size != second.size || first.isEmpty()) return 0f

        var dotProduct = 0.0
        var firstNorm = 0.0
        var secondNorm = 0.0
        first.indices.forEach { index ->
            dotProduct += first[index] * second[index]
            firstNorm += first[index] * first[index]
            secondNorm += second[index] * second[index]
        }
        val denominator = sqrt(firstNorm) * sqrt(secondNorm)
        return if (denominator > 0.0) (dotProduct / denominator).toFloat() else 0f
    }

    fun computeSemanticScore(jdText: String, resumeText: String): Double {
        val similarity = cosineSimilarity(embed(jdText), embed(resumeText)).toDouble()
        Log.d(TAG, "Semantic similarity: ${(similarity * 100).toInt()}%")
        return similarity
    }

    fun isReady(): Boolean = interpreter != null && tokenizer != null && contract != null

    @Synchronized
    fun close() {
        try {
            interpreter?.close()
        } catch (exception: Exception) {
            Log.e(TAG, "Failed to close embedding model: ${exception.message}", exception)
        } finally {
            interpreter = null
            tokenizer = null
            contract = null
        }
    }

    private fun inspectContract(model: Interpreter): ModelContract {
        val inputRoles = (0 until model.inputTensorCount).associateWith { index ->
            inferInputRole(index, model.getInputTensor(index), model.inputTensorCount)
        }
        require(inputRoles.values.count { it == InputRole.IDS } == 1) {
            "Expected exactly one input_ids tensor, found ${inputRoles.values}"
        }
        require(inputRoles.values.toSet().size == inputRoles.size) {
            "Could not uniquely map BERT inputs: ${inputRoles.values}"
        }
        require(model.inputTensorCount in 1..3) {
            "Expected one to three BERT inputs, found ${model.inputTensorCount}"
        }
        (0 until model.inputTensorCount).forEach { index -> validateInputTensor(model.getInputTensor(index)) }

        val idsIndex = inputRoles.entries.first { it.value == InputRole.IDS }.key
        val sequenceLength = model.getInputTensor(idsIndex).shape().lastOrNull()
            ?.takeIf { it > 0 } ?: DEFAULT_SEQUENCE_LENGTH
        val outputIndex = selectEmbeddingOutput(model)
        val outputTensor = model.getOutputTensor(outputIndex)
        val outputShape = outputTensor.shape()

        return ModelContract(
            inputRoles = inputRoles,
            outputIndex = outputIndex,
            sequenceLength = sequenceLength,
            embeddingDimension = outputShape.last(),
            pooling = if (outputShape.size == 2) Pooling.MODEL_OUTPUT else Pooling.MEAN,
            inputSummary = (0 until model.inputTensorCount).joinToString { index ->
                model.getInputTensor(index).describe()
            },
            outputSummary = outputTensor.describe()
        )
    }

    private fun inferInputRole(index: Int, tensor: Tensor, inputCount: Int): InputRole {
        val name = tensor.name().lowercase()
        return when {
            "mask" in name -> InputRole.MASK
            "token_type" in name || "segment" in name -> InputRole.TOKEN_TYPES
            "input_ids" in name || name == "ids" || inputCount == 1 -> InputRole.IDS
            else -> when (index) {
                0 -> InputRole.IDS
                1 -> InputRole.MASK
                2 -> InputRole.TOKEN_TYPES
                else -> error("Unrecognized BERT input tensor ${tensor.name()}")
            }
        }
    }

    private fun validateInputTensor(tensor: Tensor) {
        require(tensor.shape().size == 2 && tensor.shape()[0] == 1) {
            "BERT input ${tensor.name()} must have shape [1, sequence], found ${tensor.shape().contentToString()}"
        }
        require(tensor.dataType() == DataType.INT32 || tensor.dataType() == DataType.INT64) {
            "BERT input ${tensor.name()} must use INT32 or INT64, found ${tensor.dataType()}"
        }
    }

    private fun selectEmbeddingOutput(model: Interpreter): Int {
        val candidates = (0 until model.outputTensorCount).filter { index ->
            val tensor = model.getOutputTensor(index)
            tensor.dataType() == DataType.FLOAT32 && tensor.shape().size in 2..3 && tensor.shape().last() >= 64
        }
        require(candidates.isNotEmpty()) { "No FLOAT32 sentence or sequence embedding output found" }
        return candidates.maxBy { index ->
            val tensor = model.getOutputTensor(index)
            val name = tensor.name().lowercase()
            (if ("sentence" in name || "embedding" in name || "pooler" in name) 10 else 0) +
                (if (tensor.shape().size == 2) 2 else 1)
        }
    }

    private fun IntArray.toTensorInput(dataType: DataType): Any = when (dataType) {
        DataType.INT32 -> arrayOf(this)
        DataType.INT64 -> arrayOf(LongArray(size) { index -> this[index].toLong() })
        else -> error("Unsupported BERT input type $dataType")
    }

    private fun Tensor.describe(): String = "${name()}${shape().contentToString()}:${dataType()}"

    private enum class InputRole { IDS, MASK, TOKEN_TYPES }
    private enum class Pooling { MODEL_OUTPUT, MEAN }

    private data class ModelContract(
        val inputRoles: Map<Int, InputRole>,
        val outputIndex: Int,
        val sequenceLength: Int,
        val embeddingDimension: Int,
        val pooling: Pooling,
        val inputSummary: String,
        val outputSummary: String
    )
}
