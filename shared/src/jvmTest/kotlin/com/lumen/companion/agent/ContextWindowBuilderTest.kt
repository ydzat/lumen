package com.lumen.companion.agent

import com.lumen.core.database.entities.Message
import com.lumen.core.memory.LlmCall
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContextWindowBuilderTest {

    private fun createMessages(count: Int): List<Message> {
        return (1..count).map { i ->
            Message(
                id = i.toLong(),
                conversationId = 1,
                role = if (i % 2 == 1) "user" else "assistant",
                content = "Message $i",
                createdAt = i.toLong() * 1000,
            )
        }
    }

    @Test
    fun buildContext_withinWindow_includesAllMessages() = runBlocking {
        val builder = ContextWindowBuilder()
        val messages = createMessages(5)

        val result = builder.buildContext(messages, "System prompt", windowSize = 10)

        // 1 system prompt + 5 messages
        assertEquals(6, result.size)
        assertEquals("System prompt", result[0].content)
        assertEquals("Message 1", result[1].content)
        assertEquals("Message 5", result[5].content)
    }

    @Test
    fun buildContext_exceedingWindow_truncatesToRecentMessages() = runBlocking {
        val builder = ContextWindowBuilder()
        val messages = createMessages(30)

        val result = builder.buildContext(messages, "System prompt", windowSize = 10)

        // 1 system prompt + 10 recent messages (no LLM, no summary)
        assertEquals(11, result.size)
        assertEquals("System prompt", result[0].content)
        assertEquals("Message 21", result[1].content)
        assertEquals("Message 30", result[10].content)
    }

    @Test
    fun buildContext_exceedingWindow_withLlm_includesSummary() = runBlocking {
        val fakeLlm = LlmCall { _, _ -> "Summary of earlier conversation" }
        val builder = ContextWindowBuilder(fakeLlm)
        val messages = createMessages(30)

        val result = builder.buildContext(messages, "System prompt", windowSize = 10)

        // 1 system prompt + 1 summary system message + 10 recent messages
        assertEquals(12, result.size)
        assertEquals("System prompt", result[0].content)
        assertTrue(result[1].content.contains("Summary of earlier conversation"))
        assertEquals("Message 21", result[2].content)
    }

    @Test
    fun buildContext_exceedingWindow_withLlmFailure_fallsBackToRecentOnly() = runBlocking {
        val failingLlm = LlmCall { _, _ -> throw RuntimeException("LLM unavailable") }
        val builder = ContextWindowBuilder(failingLlm)
        val messages = createMessages(30)

        val result = builder.buildContext(messages, "System prompt", windowSize = 10)

        // 1 system prompt + 10 recent messages (summary failed, omitted)
        assertEquals(11, result.size)
        assertEquals("System prompt", result[0].content)
        assertEquals("Message 21", result[1].content)
    }

    @Test
    fun buildContext_emptyMessages_returnsOnlySystemPrompt() = runBlocking {
        val builder = ContextWindowBuilder()

        val result = builder.buildContext(emptyList(), "System prompt")

        assertEquals(1, result.size)
        assertEquals("System prompt", result[0].content)
    }

    @Test
    fun buildContext_mapsRolesCorrectly() = runBlocking {
        val builder = ContextWindowBuilder()
        val messages = listOf(
            Message(id = 1, conversationId = 1, role = "user", content = "Hi", createdAt = 1000),
            Message(id = 2, conversationId = 1, role = "assistant", content = "Hello", createdAt = 2000),
            Message(id = 3, conversationId = 1, role = "system", content = "Note", createdAt = 3000),
        )

        val result = builder.buildContext(messages, "Prompt")

        // Check message types via content (Koog Message is sealed, check content)
        assertEquals("Prompt", result[0].content)
        assertEquals("Hi", result[1].content)
        assertEquals("Hello", result[2].content)
        assertEquals("Note", result[3].content)
    }

    @Test
    fun estimateTokens_estimatesCorrectly() {
        assertEquals(0, ContextWindowBuilder.estimateTokens(""))
        assertEquals(0, ContextWindowBuilder.estimateTokens("  "))
        val tokens = ContextWindowBuilder.estimateTokens("Hello world this is a test")
        // 6 words * 1.3 = 7.8 -> 7
        assertEquals(7, tokens)
    }
}
