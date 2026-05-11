package com.runtime.conversation.sqlite

import com.runtime.conversation.ConversationRepository
import com.runtime.conversation.ConversationStore
import com.runtime.conversation.DefaultConversationStore
import com.runtime.conversation.MessageRepository

/**
 * Wires SQLite-backed repositories into a [ConversationStore] for Prompt 02.
 */
class SqliteConversationPersistence(databasePath: String) {
    private val dataSource = SqliteDataSource(databasePath)

    val conversationRepository: ConversationRepository = SqliteConversationRepository(dataSource)
    val messageRepository: MessageRepository = SqliteMessageRepository(dataSource)

    val store: ConversationStore = DefaultConversationStore(conversationRepository, messageRepository)

    fun close() {
        dataSource.close()
    }
}
