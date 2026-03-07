package com.lumen.core.document

class TextChunker(
    private val targetTokens: Int = DEFAULT_TARGET_TOKENS,
    private val overlapTokens: Int = DEFAULT_OVERLAP_TOKENS,
) {

    fun chunk(text: String): List<ChunkResult> {
        if (text.isBlank()) return emptyList()

        val sentences = splitSentences(text)
        if (sentences.isEmpty()) return emptyList()

        val chunks = mutableListOf<ChunkResult>()
        var currentWords = mutableListOf<String>()
        var currentTokenCount = 0
        var chunkIndex = 0

        for (sentence in sentences) {
            val sentenceWords = sentence.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
            val sentenceTokens = estimateTokens(sentenceWords.size)

            if (currentTokenCount + sentenceTokens > targetTokens && currentWords.isNotEmpty()) {
                chunks.add(ChunkResult(chunkIndex, currentWords.joinToString(" ")))
                chunkIndex++

                val overlapWords = collectOverlapWords(currentWords)
                currentWords = overlapWords.toMutableList()
                currentTokenCount = estimateTokens(currentWords.size)
            }

            currentWords.addAll(sentenceWords)
            currentTokenCount += sentenceTokens
        }

        if (currentWords.isNotEmpty()) {
            chunks.add(ChunkResult(chunkIndex, currentWords.joinToString(" ")))
        }

        return chunks
    }

    private fun collectOverlapWords(words: List<String>): List<String> {
        val targetWordCount = (overlapTokens / TOKENS_PER_WORD).toInt()
        if (targetWordCount <= 0 || words.isEmpty()) return emptyList()
        return words.takeLast(targetWordCount.coerceAtMost(words.size))
    }

    companion object {
        const val DEFAULT_TARGET_TOKENS = 512
        const val DEFAULT_OVERLAP_TOKENS = 64
        const val TOKENS_PER_WORD = 1.3f

        private val WHITESPACE_REGEX = Regex("\\s+")
        private val SENTENCE_BOUNDARY_REGEX = Regex("(?<=[.!?])\\s+(?=[A-Z\"'])")

        fun estimateTokens(text: String): Int {
            if (text.isBlank()) return 0
            return estimateTokens(text.split(WHITESPACE_REGEX).count { it.isNotEmpty() })
        }

        internal fun estimateTokens(wordCount: Int): Int {
            return (wordCount * TOKENS_PER_WORD).toInt()
        }

        internal fun splitSentences(text: String): List<String> {
            return text.split(SENTENCE_BOUNDARY_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
    }
}

data class ChunkResult(
    val chunkIndex: Int,
    val content: String,
)
