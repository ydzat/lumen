package com.lumen.companion.conversation

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.MyObjectBox
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationManagerTest {

    private lateinit var db: LumenDatabase
    private lateinit var tempDir: File
    private lateinit var manager: ConversationManager

    @BeforeTest
    fun setup() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "objectbox-test-${System.nanoTime()}")
        tempDir.mkdirs()
        val store = MyObjectBox.builder()
            .baseDirectory(tempDir)
            .build()
        db = LumenDatabase(store)
        manager = ConversationManager(db)
    }

    @AfterTest
    fun teardown() {
        db.close()
        tempDir.deleteRecursively()
    }

    @Test
    fun createConversation_returnsConversationWithId() {
        val conversation = manager.createConversation("Test Chat", personaId = 1, projectId = 2)

        assertNotEquals(0L, conversation.id)
        assertEquals("Test Chat", conversation.title)
        assertEquals(1L, conversation.personaId)
        assertEquals(2L, conversation.projectId)
        assertEquals(0, conversation.messageCount)
        assertTrue(conversation.createdAt > 0)
        assertTrue(conversation.updatedAt > 0)
    }

    @Test
    fun listConversations_returnsSortedByUpdatedAtDesc() {
        val c1 = manager.createConversation("First")
        Thread.sleep(10)
        val c2 = manager.createConversation("Second")
        Thread.sleep(10)
        val c3 = manager.createConversation("Third")

        val list = manager.listConversations()

        assertEquals(3, list.size)
        assertEquals(c3.id, list[0].id)
        assertEquals(c2.id, list[1].id)
        assertEquals(c1.id, list[2].id)
    }

    @Test
    fun getConversation_withExistingId_returnsConversation() {
        val created = manager.createConversation("Test")

        val retrieved = manager.getConversation(created.id)

        assertNotNull(retrieved)
        assertEquals("Test", retrieved.title)
    }

    @Test
    fun getConversation_withNonExistentId_returnsNull() {
        assertNull(manager.getConversation(99999L))
    }

    @Test
    fun deleteConversation_removesConversationAndMessages() {
        val conversation = manager.createConversation("To Delete")
        manager.addMessage(conversation.id, "user", "Hello")
        manager.addMessage(conversation.id, "assistant", "Hi there!")

        manager.deleteConversation(conversation.id)

        assertNull(manager.getConversation(conversation.id))
        assertEquals(0, manager.getMessages(conversation.id).size)
    }

    @Test
    fun addMessage_persistsMessageAndIncrementsCount() {
        val conversation = manager.createConversation("Chat")

        val message = manager.addMessage(conversation.id, "user", "Hello")

        assertNotEquals(0L, message.id)
        assertEquals(conversation.id, message.conversationId)
        assertEquals("user", message.role)
        assertEquals("Hello", message.content)
        assertTrue(message.createdAt > 0)

        val updated = manager.getConversation(conversation.id)
        assertNotNull(updated)
        assertEquals(1, updated.messageCount)
    }

    @Test
    fun addMessage_withToolCall_persistsToolFields() {
        val conversation = manager.createConversation("Tool Chat")

        val message = manager.addMessage(
            conversation.id, "tool_call", "",
            toolName = "search", toolArgs = """{"query": "test"}"""
        )

        assertEquals("tool_call", message.role)
        assertEquals("search", message.toolName)
        assertEquals("""{"query": "test"}""", message.toolArgs)
    }

    @Test
    fun getMessages_returnsSortedByCreatedAtAsc() {
        val conversation = manager.createConversation("Chat")
        manager.addMessage(conversation.id, "user", "First")
        Thread.sleep(10)
        manager.addMessage(conversation.id, "assistant", "Second")
        Thread.sleep(10)
        manager.addMessage(conversation.id, "user", "Third")

        val messages = manager.getMessages(conversation.id)

        assertEquals(3, messages.size)
        assertEquals("First", messages[0].content)
        assertEquals("Second", messages[1].content)
        assertEquals("Third", messages[2].content)
        assertTrue(messages[0].createdAt <= messages[1].createdAt)
        assertTrue(messages[1].createdAt <= messages[2].createdAt)
    }

    @Test
    fun getMessages_withNoMessages_returnsEmptyList() {
        val conversation = manager.createConversation("Empty Chat")

        val messages = manager.getMessages(conversation.id)

        assertTrue(messages.isEmpty())
    }

    @Test
    fun updateTitle_changesTitle() {
        val conversation = manager.createConversation("Old Title")

        manager.updateTitle(conversation.id, "New Title")

        val updated = manager.getConversation(conversation.id)
        assertNotNull(updated)
        assertEquals("New Title", updated.title)
    }

    @Test
    fun updateTitle_withNonExistentId_doesNotThrow() {
        manager.updateTitle(99999L, "No Effect")
    }

    @Test
    fun addMultipleMessages_incrementsCountCorrectly() {
        val conversation = manager.createConversation("Chat")
        manager.addMessage(conversation.id, "user", "One")
        manager.addMessage(conversation.id, "assistant", "Two")
        manager.addMessage(conversation.id, "user", "Three")

        val updated = manager.getConversation(conversation.id)
        assertNotNull(updated)
        assertEquals(3, updated.messageCount)
    }
}
