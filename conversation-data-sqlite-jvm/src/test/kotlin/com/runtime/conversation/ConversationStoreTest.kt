package com.runtime.conversation

import com.runtime.conversation.sqlite.SqliteConversationPersistence
import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConversationStoreTest {

    @Test
    fun createAppendAndLoadHistory() = runBlocking {
        val db = Files.createTempFile("runtime-conv", ".db").toAbsolutePath().toString()
        val persistence = SqliteConversationPersistence(db)
        try {
            val store = persistence.store

            val created = assertIs<AppResult.Success<Conversation>>(store.createConversation("ws-1", "t"))
            val conv = created.value

            assertIs<AppResult.Success<ConversationMessage>>(store.appendUserMessage(conv.id, "hello"))
            assertIs<AppResult.Success<ConversationMessage>>(store.appendAssistantMessage(conv.id, "hi"))

            val history = assertIs<AppResult.Success<List<ConversationMessage>>>(store.loadMessageHistory(conv.id))
            assertEquals(2, history.value.size)
            assertEquals(MessageRole.USER, history.value[0].role)
            assertEquals("hello", history.value[0].content)
            assertEquals(MessageRole.ASSISTANT, history.value[1].role)
            assertEquals("hi", history.value[1].content)

            val missing = store.appendUserMessage("no-such-id", "x")
            assertTrue(missing is AppResult.Failure)
        } finally {
            persistence.close()
            Files.deleteIfExists(java.nio.file.Paths.get(db))
        }
    }
}
