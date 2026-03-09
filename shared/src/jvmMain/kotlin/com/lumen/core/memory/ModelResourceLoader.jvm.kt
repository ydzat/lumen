package com.lumen.core.memory

import java.io.File

actual class ModelResourceLoader {

    private val modelsDir: File by lazy {
        File(System.getProperty("user.home"), ".lumen/$MODEL_DIR").also { it.mkdirs() }
    }

    actual fun getModelPath(): String {
        return extractResource("$MODEL_DIR/$MODEL_FILE", File(modelsDir, MODEL_FILE))
    }

    actual fun getTokenizerPath(): String {
        return extractResource("$MODEL_DIR/$TOKENIZER_FILE", File(modelsDir, TOKENIZER_FILE))
    }

    actual fun getVocabPath(): String {
        return extractResource("$MODEL_DIR/$VOCAB_FILE", File(modelsDir, VOCAB_FILE))
    }

    private fun extractResource(resourcePath: String, target: File): String {
        if (!target.exists()) {
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException(
                    "Model resource '$resourcePath' not found. Run scripts/download-model.sh first."
                )
            val tmpFile = File(target.parentFile, "${target.name}.tmp")
            stream.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tmpFile.renameTo(target)
        }
        return target.absolutePath
    }
}
