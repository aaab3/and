package com.runtime.secret.sqlite

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

internal class SecretSqliteDataSource(databasePath: String) {
    private val lock = Any()

    private val connection: Connection =
        DriverManager.getConnection("jdbc:sqlite:${File(databasePath).absolutePath.replace('\\', '/')}").also { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS secrets (
                      "alias" TEXT NOT NULL PRIMARY KEY,
                      "value" TEXT NOT NULL
                    )
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
