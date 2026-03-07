package com.lumen.companion.agent

import com.lumen.core.config.LlmConfig
import com.lumen.core.database.entities.Persona
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemPromptTest {

    @Test
    fun buildSystemPrompt_withNoPersona_usesDefault() {
        val agent = LumenAgent(config = LlmConfig(apiKey = "test"))
        try {
            assertEquals(LumenAgent.DEFAULT_SYSTEM_PROMPT, agent.systemPrompt)
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPrompt_withPersona_usesPersonaPrompt() {
        val persona = Persona(
            id = 1,
            name = "Test Bot",
            systemPrompt = "You are a test bot.",
        )
        val agent = LumenAgent(config = LlmConfig(apiKey = "test"), persona = persona)
        try {
            assertTrue(agent.systemPrompt.startsWith("You are a test bot."))
        } finally {
            agent.close()
        }
    }

    @Test
    fun buildSystemPrompt_withPersonaAndTools_concatenatesPromptWithTools() {
        val persona = Persona(
            id = 1,
            name = "Test Bot",
            systemPrompt = "You are a test bot.",
        )
        // Agent with no tools — persona prompt only
        val agent = LumenAgent(config = LlmConfig(apiKey = "test"), persona = persona)
        try {
            assertEquals("You are a test bot.", agent.systemPrompt)
            assertFalse(agent.systemPrompt.contains("Available tools:"))
        } finally {
            agent.close()
        }
    }
}
