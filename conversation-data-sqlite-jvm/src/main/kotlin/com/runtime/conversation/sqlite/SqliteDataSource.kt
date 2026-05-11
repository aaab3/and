package com.runtime.conversation.sqlite

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Single-connection SQLite access aligned with [conversations] / [messages] in database-sketch.md.
 */
class SqliteDataSource(databasePath: String) {
    private val lock = Any()

    private val connection: Connection =
        DriverManager.getConnection("jdbc:sqlite:${File(databasePath).absolutePath.replace('\\', '/')}").also { conn ->
            conn.createStatement().use { st ->
                st.execute("PRAGMA foreign_keys = ON")
            }
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS conversations (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "workspaceId" TEXT NOT NULL,
                      "title" TEXT,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "updatedAtEpochMs" INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS messages (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "conversationId" TEXT NOT NULL,
                      "role" TEXT NOT NULL,
                      "content" TEXT NOT NULL,
                      "toolCallId" TEXT,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "metadataJson" TEXT NOT NULL,
                      FOREIGN KEY ("conversationId") REFERENCES conversations("id") ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_messages_conv_created
                    ON messages("conversationId", "createdAtEpochMs")
                    """.trimIndent()
                )
            }
        }

    fun <T> withConnection(block: (Connection) -> T): T = synchronized(lock) {
        block(connection)
    }

    fun close() {
        synchronized(lock) {
            if (!connection.isClosed) {
                connection.close()
            }
        }
    }
}
