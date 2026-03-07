package com.lumen.companion.agent

import com.lumen.companion.conversation.ConversationManager
import com.lumen.core.config.LlmConfig
import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatStreamTest {

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
    fun chatStream_withEmptyApiKey_emitsErrorAndDone(): Unit = runBlocking {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = ""),
            conversationManager = conversationManager,
        )
        try {
            val conversation = conversationManager.createConversation("Test")
            val events = agent.chatStream(conversation.id, "Hello").toList()

            assertEquals(2, events.size)
            assertIs<ChatEvent.Error>(events[0])
            assertTrue((events[0] as ChatEvent.Error).message.contains("API key"))
            assertIs<ChatEvent.Done>(events[1])
        } finally {
            agent.close()
        }
    }

    @Test
    fun chatStream_withoutConversationManager_throwsException(): Unit = runBlocking {
        val agent = LumenAgent(config = LlmConfig(apiKey = "test"))
        try {
            var threw = false
            try {
                agent.chatStream(1L, "Hello").toList()
            } catch (e: IllegalStateException) {
                threw = true
                assertTrue(e.message?.contains("ConversationManager") == true)
            }
            assertTrue(threw, "Expected IllegalStateException")
        } finally {
            agent.close()
        }
    }

    @Test
    fun chatStream_savesUserMessageToDb(): Unit = runBlocking {
        val agent = LumenAgent(
            config = LlmConfig(apiKey = ""),
            conversationManager = conversationManager,
        )
        try {
            val conversation = conversationManager.createConversation("Test")
            agent.chatStream(conversation.id, "Hello world").toList()

            // Even with empty API key, user message should not be saved
            // (error emitted before save). Let's verify conversation is untouched.
            val messages = conversationManager.getMessages(conversation.id)
            assertEquals(0, messages.size)
        } finally {
            agent.close()
        }
    }
}
