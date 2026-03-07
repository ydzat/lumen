package com.lumen.core.memory

import android.content.Context
import java.io.File

actual class ModelResourceLoader(private val context: Context) {

    private val modelsDir: File by lazy {
        File(context.cacheDir, MODEL_DIR).also { it.mkdirs() }
    }

    actual fun getModelPath(): String {
        return extractAsset("$MODEL_DIR/$MODEL_FILE", File(modelsDir, MODEL_FILE))
    }

    actual fun getTokenizerPath(): String {
        return extractAsset("$MODEL_DIR/$TOKENIZER_FILE", File(modelsDir, TOKENIZER_FILE))
    }

    private fun extractAsset(assetPath: String, target: File): String {
        if (!target.exists()) {
            val tmpFile = File(target.parentFile, "${target.name}.tmp")
            context.assets.open(assetPath).use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tmpFile.renameTo(target)
        }
        return target.absolutePath
    }
}
