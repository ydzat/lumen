package com.lumen.core.document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextChunkerTest {

    private val chunker = TextChunker()

    @Test
    fun chunk_emptyText_returnsEmptyList() {
        assertEquals(emptyList(), chunker.chunk(""))
        assertEquals(emptyList(), chunker.chunk("   "))
    }

    @Test
    fun chunk_shortText_returnsSingleChunk() {
        val text = "This is a short sentence. It has very few words."
        val chunks = chunker.chunk(text)

        assertEquals(1, chunks.size)
        assertEquals(0, chunks[0].chunkIndex)
        assertTrue(chunks[0].content.isNotBlank())
    }

    @Test
    fun chunk_longText_splitsIntoMultipleChunks() {
        val sentence = "This is a test sentence with several words in it. "
        // ~10 words per sentence, ~13 tokens → need ~40 sentences to exceed 512 tokens
        val text = sentence.repeat(50)
        val chunks = chunker.chunk(text)

        assertTrue(chunks.size > 1, "Expected multiple chunks, got ${chunks.size}")
        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
        }
    }

    @Test
    fun chunk_respectsSentenceBoundaries() {
        val sentence = "The quick brown fox jumps over the lazy dog. "
        val text = sentence.repeat(60)
        val chunks = chunker.chunk(text)

        assertTrue(chunks.size > 1)
        for (chunk in chunks) {
            // Each chunk should end at a sentence boundary (ends with a period and space or period)
            val trimmed = chunk.content.trimEnd()
            assertTrue(
                trimmed.endsWith(".") || chunk == chunks.last(),
                "Chunk should end at sentence boundary: '...${trimmed.takeLast(30)}'"
            )
        }
    }

    @Test
    fun chunk_hasOverlapBetweenChunks() {
        val sentences = (1..80).map { "Sentence number $it contains unique identifying text. " }
        val text = sentences.joinToString("")
        val chunks = chunker.chunk(text)

        assertTrue(chunks.size >= 2, "Need at least 2 chunks for overlap test")

        // Words at the end of chunk N should appear at the start of chunk N+1
        for (i in 0 until chunks.size - 1) {
            val currentWords = chunks[i].content.split(Regex("\\s+"))
            val nextWords = chunks[i + 1].content.split(Regex("\\s+"))
            val overlapTarget = (TextChunker.DEFAULT_OVERLAP_TOKENS / TextChunker.TOKENS_PER_WORD).toInt()

            val lastWords = currentWords.takeLast(overlapTarget)
            val firstWords = nextWords.take(overlapTarget)

            assertEquals(
                lastWords, firstWords,
                "Chunk ${i + 1} should start with overlap from chunk $i"
            )
        }
    }

    @Test
    fun chunk_chunkSizesAreApproximatelyCorrect() {
        val sentence = "Each sentence has about nine words in it here. "
        val text = sentence.repeat(100)
        val chunks = chunker.chunk(text)

        for (chunk in chunks.dropLast(1)) {
            val tokens = TextChunker.estimateTokens(chunk.content)
            assertTrue(
                tokens in 200..700,
                "Chunk token count $tokens should be roughly around target ${TextChunker.DEFAULT_TARGET_TOKENS}"
            )
        }
    }

    @Test
    fun chunk_indicesAreSequential() {
        val text = "Word. ".repeat(500)
        val chunks = chunker.chunk(text)

        chunks.forEachIndexed { index, chunk ->
            assertEquals(index, chunk.chunkIndex)
        }
    }

    @Test
    fun estimateTokens_calculatesCorrectly() {
        assertEquals(0, TextChunker.estimateTokens(""))
        assertEquals(0, TextChunker.estimateTokens("  "))
        val tokens = TextChunker.estimateTokens("one two three four five")
        assertEquals((5 * 1.3f).toInt(), tokens)
    }

    @Test
    fun splitSentences_splitsOnPeriodQuestionExclamation() {
        val text = "First sentence. Second sentence! Third sentence? Fourth sentence."
        val sentences = TextChunker.splitSentences(text)

        assertEquals(4, sentences.size)
        assertTrue(sentences[0].startsWith("First"))
        assertTrue(sentences[1].startsWith("Second"))
        assertTrue(sentences[2].startsWith("Third"))
        assertTrue(sentences[3].startsWith("Fourth"))
    }

    @Test
    fun chunk_customTargetAndOverlap() {
        val chunker = TextChunker(targetTokens = 50, overlapTokens = 10)
        val sentence = "This is a simple test sentence. "
        val text = sentence.repeat(20)
        val chunks = chunker.chunk(text)

        assertTrue(chunks.size > 1, "Should produce multiple chunks with small target")
    }
}
