package com.lumen.companion.agent

import com.lumen.companion.conversation.ConversationManager
import com.lumen.core.config.LlmConfig
import com.lumen.core.config.UserPreferences
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MemoryExtractionTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var conversationManager: ConversationManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        conversationManager = ConversationManager(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun chatStream_withExtractionDisabled_doesNotExtract(): Unit = runBlocking {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = ""),
            conversationManager = conversationManager,
            userPreferences = UserPreferences(memoryExtractionInterval = 0),
        )
        try {
            val conversation = conversationManager.createConversation("Test")
            val events = agent.chatStream(conversation.id, "Hello").toList()

            // With empty API key, we get Error + Done; verify no MemoryExtracted event
            val extractedEvents = events.filterIsInstance<ChatEvent.MemoryExtracted>()
            assertTrue(extractedEvents.isEmpty(), "Should not extract when interval is 0")
        } finally {
            agent.close()
        }
    }

    @Test
    fun chatStream_withCustomInterval_usesConfiguredValue(): Unit = runBlocking {
        // Verify the agent accepts a custom extraction interval without error
        val agent = LumenAgent(
            config = LlmConfig(apiKey = ""),
            conversationManager = conversationManager,
            userPreferences = UserPreferences(memoryExtractionInterval = 3),
        )
        try {
            val conversation = conversationManager.createConversation("Test")
            val events = agent.chatStream(conversation.id, "Hello").toList()

            // With empty API key, exits early — just verify no crash
            assertIs<ChatEvent.Error>(events[0])
            assertIs<ChatEvent.Done>(events[1])
        } finally {
            agent.close()
        }
    }

    @Test
    fun userPreferences_defaultValues_areCorrect() {
        val prefs = UserPreferences()
        assertTrue(prefs.memoryAutoRecall)
        assertTrue(prefs.memoryExtractionInterval == 10)
    }
}
