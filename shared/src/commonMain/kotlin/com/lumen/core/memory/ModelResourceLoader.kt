package com.lumen.core.memory

expect class ModelResourceLoader {
    fun getModelPath(): String
    fun getTokenizerPath(): String
    fun getVocabPath(): String
}
