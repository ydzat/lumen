package com.lumen.companion.agent

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.executor.clients.LLMClient
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.RequestMetaInfo
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetryExecutorTest {

    private val testModel = LLModel(LLMProvider.OpenAI, "test-model", listOf(LLMCapability.Completion))
    private val testPrompt = Prompt(
        listOf(Message.User("hello", RequestMetaInfo.Empty)),
        "test",
        LLMParams(),
    )

    @Test
    fun isRetryable_with429_returnsTrue() {
        assertTrue(RetryExecutor.isRetryable(RuntimeException("HTTP 429 Too Many Requests")))
    }

    @Test
    fun isRetryable_with500_returnsTrue() {
        assertTrue(RetryExecutor.isRetryable(RuntimeException("HTTP 500 Internal Server Error")))
    }

    @Test
    fun isRetryable_with502_returnsTrue() {
        assertTrue(RetryExecutor.isRetryable(RuntimeException("HTTP 502 Bad Gateway")))
    }

    @Test
    fun isRetryable_with503_returnsTrue() {
        assertTrue(RetryExecutor.isRetryable(RuntimeException("HTTP 503 Service Unavailable")))
    }

    @Test
    fun isRetryable_withConnectException_returnsTrue() {
        val cause = java.net.ConnectException("Connection refused")
        assertTrue(RetryExecutor.isRetryable(RuntimeException("Network error", cause)))
    }

    @Test
    fun isRetryable_with401_returnsFalse() {
        assertFalse(RetryExecutor.isRetryable(RuntimeException("HTTP 401 Unauthorized")))
    }

    @Test
    fun isRetryable_with403_returnsFalse() {
        assertFalse(RetryExecutor.isRetryable(RuntimeException("HTTP 403 Forbidden")))
    }

    @Test
    fun execute_succeedsOnFirstAttempt() = runBlocking {
        val response = listOf<Message.Response>(Message.Assistant("ok", ResponseMetaInfo.Empty))
        val client = FakeLlmClient(response)

        val result = RetryExecutor.execute(client, testPrompt, testModel, emptyList())

        assertEquals(response, result)
        assertEquals(1, client.callCount)
    }

    @Test
    fun execute_retriesOnTransientError_thenSucceeds() = runBlocking {
        val response = listOf<Message.Response>(Message.Assistant("ok", ResponseMetaInfo.Empty))
        val client = FakeLlmClient(
            response,
            failuresBeforeSuccess = 2,
            failureException = RuntimeException("HTTP 503"),
        )

        val result = RetryExecutor.execute(client, testPrompt, testModel, emptyList())

        assertEquals(response, result)
        assertEquals(3, client.callCount)
    }

    @Test
    fun execute_throwsImmediately_onNonRetryableError() = runBlocking {
        val client = FakeLlmClient(
            emptyList(),
            failuresBeforeSuccess = 10,
            failureException = RuntimeException("HTTP 401 Unauthorized"),
        )

        assertFailsWith<RuntimeException> {
            RetryExecutor.execute(client, testPrompt, testModel, emptyList())
        }
        assertEquals(1, client.callCount)
    }

    @Test
    fun execute_throwsAfterMaxRetries() = runBlocking {
        val client = FakeLlmClient(
            emptyList(),
            failuresBeforeSuccess = 10,
            failureException = RuntimeException("HTTP 503"),
        )

        assertFailsWith<RuntimeException> {
            RetryExecutor.execute(client, testPrompt, testModel, emptyList())
        }
        assertEquals(3, client.callCount)
    }

    private class FakeLlmClient(
        private val successResponse: List<Message.Response>,
        private val failuresBeforeSuccess: Int = 0,
        private val failureException: Exception = RuntimeException("error"),
    ) : LLMClient {
        var callCount = 0

        override suspend fun execute(
            prompt: Prompt,
            model: LLModel,
            tools: List<ToolDescriptor>,
        ): List<Message.Response> {
            callCount++
            if (callCount <= failuresBeforeSuccess) {
                throw failureException
            }
            return successResponse
        }

        override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
            throw UnsupportedOperationException("Not used in tests")
        }

        override fun llmProvider(): LLMProvider = LLMProvider.OpenAI

        override fun close() {}
    }
}
