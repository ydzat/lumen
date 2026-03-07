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

    private fun extractResource(resourcePath: String, target: File): String {
        if (!target.exists()) {
            val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
                ?: throw IllegalStateException(
                    "Model resource '$resourcePath' not found. Run scripts/download-model.sh first."
                )
            stream.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return target.absolutePath
    }
}
