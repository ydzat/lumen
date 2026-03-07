package com.lumen.companion.conversation

import com.lumen.core.database.LumenDatabase
import com.lumen.core.database.entities.Conversation
import com.lumen.core.database.entities.Message
import com.lumen.core.database.entities.Message_

class ConversationManager(private val db: LumenDatabase) {

    fun createConversation(
        title: String,
        personaId: Long = 0,
        projectId: Long = 0,
    ): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            title = title,
            personaId = personaId,
            projectId = projectId,
            messageCount = 0,
            createdAt = now,
            updatedAt = now,
        )
        val id = db.conversationBox.put(conversation)
        return db.conversationBox.get(id)
    }

    fun listConversations(): List<Conversation> {
        return db.conversationBox.all.sortedByDescending { it.updatedAt }
    }

    fun getConversation(id: Long): Conversation? {
        return db.conversationBox.get(id)
    }

    fun deleteConversation(id: Long) {
        db.store.runInTx {
            val query = db.messageBox.query()
                .equal(Message_.conversationId, id)
                .build()
            val messages = query.find()
            query.close()
            if (messages.isNotEmpty()) {
                db.messageBox.remove(messages)
            }
            db.conversationBox.remove(id)
        }
    }

    fun getMessages(conversationId: Long): List<Message> {
        val query = db.messageBox.query()
            .equal(Message_.conversationId, conversationId)
            .order(Message_.createdAt)
            .build()
        val messages = query.find()
        query.close()
        return messages
    }

    fun addMessage(
        conversationId: Long,
        role: String,
        content: String,
        toolName: String = "",
        toolArgs: String = "",
    ): Message {
        val now = System.currentTimeMillis()
        val message = Message(
            conversationId = conversationId,
            role = role,
            content = content,
            toolName = toolName,
            toolArgs = toolArgs,
            createdAt = now,
        )
        var id: Long = 0
        db.store.runInTx {
            id = db.messageBox.put(message)
            val conversation = db.conversationBox.get(conversationId)
            if (conversation != null) {
                db.conversationBox.put(
                    conversation.copy(
                        messageCount = conversation.messageCount + 1,
                        updatedAt = now,
                    )
                )
            }
        }

        return db.messageBox.get(id)
    }

    fun updateTitle(id: Long, title: String) {
        val conversation = db.conversationBox.get(id) ?: return
        db.conversationBox.put(
            conversation.copy(title = title, updatedAt = System.currentTimeMillis())
        )
    }

    fun updatePersona(id: Long, personaId: Long) {
        val conversation = db.conversationBox.get(id) ?: return
        db.conversationBox.put(
            conversation.copy(personaId = personaId, updatedAt = System.currentTimeMillis())
        )
    }
}
