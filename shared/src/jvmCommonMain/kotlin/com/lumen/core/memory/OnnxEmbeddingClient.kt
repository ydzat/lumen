package com.lumen.core.memory

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.LongBuffer
import java.nio.file.Path
import kotlin.math.sqrt

class OnnxEmbeddingClient(
    private val resourceLoader: ModelResourceLoader,
) : EmbeddingClient {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val session: OrtSession by lazy {
        env.createSession(resourceLoader.getModelPath())
    }

    private val tokenizer: HuggingFaceTokenizer by lazy {
        HuggingFaceTokenizer.newInstance(Path.of(resourceLoader.getTokenizerPath()))
    }

    override suspend fun embed(text: String): FloatArray {
        return embedBatch(listOf(text)).first()
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        require(texts.isNotEmpty()) { "Input texts must not be empty" }

        val encodings = texts.map { tokenizer.encode(it) }
        val maxLen = encodings.maxOf { it.ids.size }
        val batchSize = texts.size

        val inputIds = LongArray(batchSize * maxLen)
        val attentionMask = LongArray(batchSize * maxLen)
        val tokenTypeIds = LongArray(batchSize * maxLen)

        for (i in encodings.indices) {
            val enc = encodings[i]
            for (j in enc.ids.indices) {
                inputIds[i * maxLen + j] = enc.ids[j]
                attentionMask[i * maxLen + j] = enc.attentionMask[j]
            }
        }

        val shape = longArrayOf(batchSize.toLong(), maxLen.toLong())

        val inputIdsTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(inputIds), shape)
        val attMaskTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(attentionMask), shape)
        val tokenTypeTensor = OnnxTensor.createTensor(env, LongBuffer.wrap(tokenTypeIds), shape)

        val inputs = mapOf(
            "input_ids" to inputIdsTensor,
            "attention_mask" to attMaskTensor,
            "token_type_ids" to tokenTypeTensor,
        )

        return try {
            val results = session.run(inputs)

            @Suppress("UNCHECKED_CAST")
            val output = results[0].value as Array<Array<FloatArray>>

            (0 until batchSize).map { b ->
                meanPoolAndNormalize(output[b], encodings[b].attentionMask, maxLen)
            }
        } finally {
            inputIdsTensor.close()
            attMaskTensor.close()
            tokenTypeTensor.close()
        }
    }

    fun close() {
        session.close()
    }

    companion object {
        internal fun meanPoolAndNormalize(
            tokenEmbeddings: Array<FloatArray>,
            attentionMask: LongArray,
            seqLen: Int,
        ): FloatArray {
            val dim = EMBEDDING_DIMENSIONS
            val result = FloatArray(dim)
            var maskSum = 0f

            for (t in 0 until seqLen) {
                val mask = attentionMask[t].toFloat()
                maskSum += mask
                for (d in 0 until dim) {
                    result[d] += tokenEmbeddings[t][d] * mask
                }
            }

            if (maskSum > 0f) {
                for (d in 0 until dim) {
                    result[d] /= maskSum
                }
            }

            var norm = 0f
            for (d in 0 until dim) {
                norm += result[d] * result[d]
            }
            norm = sqrt(norm)

            if (norm > 0f) {
                for (d in 0 until dim) {
                    result[d] /= norm
                }
            }

            return result
        }
    }
}
