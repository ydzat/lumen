package com.lumen.core.memory

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer
import com.lumen.core.database.entities.EMBEDDING_DIMENSIONS
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.LongBuffer
import java.nio.file.Path
import kotlin.math.min
import kotlin.math.sqrt

class OnnxEmbeddingClient(
    private val resourceLoader: ModelResourceLoader,
) : EmbeddingClient, Closeable {

    @Volatile
    private var available = true

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    private val session: OrtSession? by lazy {
        try {
            val opts = OrtSession.SessionOptions().apply {
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                try {
                    addCUDA(0)
                } catch (_: Exception) {
                    // CUDA not available, CPU fallback
                }
            }
            env.createSession(resourceLoader.getModelPath(), opts)
        } catch (e: Exception) {
            System.err.println("WARNING: ONNX model failed to load, memory features disabled: ${e.message}")
            available = false
            null
        }
    }

    private val tokenizer: HuggingFaceTokenizer? by lazy {
        try {
            HuggingFaceTokenizer.newInstance(Path.of(resourceLoader.getTokenizerPath()))
        } catch (e: Exception) {
            System.err.println("WARNING: Tokenizer failed to load, memory features disabled: ${e.message}")
            available = false
            null
        }
    }

    override suspend fun embed(text: String): FloatArray {
        return embedBatch(listOf(text)).first()
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray> {
        if (texts.isEmpty()) return emptyList()

        val tok = tokenizer
        val sess = session
        if (!available || tok == null || sess == null) {
            return texts.map { FloatArray(EMBEDDING_DIMENSIONS) }
        }

        // Process in chunks to bound memory usage
        if (texts.size > BATCH_CHUNK_SIZE) {
            return texts.chunked(BATCH_CHUNK_SIZE).flatMap { chunk ->
                embedBatchInternal(chunk, tok, sess)
            }
        }

        return embedBatchInternal(texts, tok, sess)
    }

    private fun embedBatchInternal(
        texts: List<String>,
        tok: HuggingFaceTokenizer,
        sess: OrtSession,
    ): List<FloatArray> {
        val encodings = texts.map { tok.encode(it) }
        // Cap sequence length to avoid excessive padding
        val maxLen = min(encodings.maxOf { it.ids.size }, MAX_SEQ_LEN)
        val batchSize = texts.size

        val inputIds = LongArray(batchSize * maxLen)
        val attentionMask = LongArray(batchSize * maxLen)
        val tokenTypeIds = LongArray(batchSize * maxLen)

        for (i in encodings.indices) {
            val enc = encodings[i]
            val len = min(enc.ids.size, maxLen)
            for (j in 0 until len) {
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
            val results = sess.run(inputs)
            try {
                @Suppress("UNCHECKED_CAST")
                val output = results[0].value as Array<Array<FloatArray>>

                (0 until batchSize).map { b ->
                    val effectiveLen = min(encodings[b].attentionMask.size, maxLen)
                    val truncatedMask = encodings[b].attentionMask.copyOf(effectiveLen)
                    meanPoolAndNormalize(output[b], truncatedMask, effectiveLen)
                }
            } finally {
                results.close()
            }
        } finally {
            inputIdsTensor.close()
            attMaskTensor.close()
            tokenTypeTensor.close()
        }
    }

    override fun close() {
        session?.close()
        tokenizer?.close()
    }

    companion object {
        internal const val MAX_SEQ_LEN = 128
        internal const val BATCH_CHUNK_SIZE = 64

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
