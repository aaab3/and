package com.runtime.binding.sqlite

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class BindingSqliteDataSource(databasePath: String) {
    private val lock = Any()

    private val connection: Connection =
        DriverManager.getConnection("jdbc:sqlite:${File(databasePath).absolutePath.replace('\\', '/')}").also { conn ->
            conn.createStatement().use { st ->
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS provider_definitions (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "providerType" TEXT NOT NULL,
                      "name" TEXT NOT NULL,
                      "baseUrl" TEXT NOT NULL,
                      "defaultHeadersJson" TEXT NOT NULL,
                      "capabilitiesJson" TEXT NOT NULL,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "updatedAtEpochMs" INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS credential_refs (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "providerId" TEXT NOT NULL,
                      "displayName" TEXT NOT NULL,
                      "secretAlias" TEXT NOT NULL,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "updatedAtEpochMs" INTEGER NOT NULL,
                      FOREIGN KEY ("providerId") REFERENCES provider_definitions("id")
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS model_profiles (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "providerId" TEXT NOT NULL,
                      "modelId" TEXT NOT NULL,
                      "displayName" TEXT NOT NULL,
                      "contextWindow" INTEGER,
                      "capabilitiesJson" TEXT NOT NULL,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "updatedAtEpochMs" INTEGER NOT NULL,
                      FOREIGN KEY ("providerId") REFERENCES provider_definitions("id")
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE TABLE IF NOT EXISTS model_bindings (
                      "id" TEXT NOT NULL PRIMARY KEY,
                      "targetType" TEXT NOT NULL,
                      "targetId" TEXT,
                      "providerId" TEXT NOT NULL,
                      "modelProfileId" TEXT NOT NULL,
                      "credentialRefId" TEXT NOT NULL,
                      "priority" INTEGER NOT NULL,
                      "createdAtEpochMs" INTEGER NOT NULL,
                      "updatedAtEpochMs" INTEGER NOT NULL,
                      FOREIGN KEY ("providerId") REFERENCES provider_definitions("id"),
                      FOREIGN KEY ("modelProfileId") REFERENCES model_profiles("id"),
                      FOREIGN KEY ("credentialRefId") REFERENCES credential_refs("id")
                    )
                    """.trimIndent()
                )
                st.execute(
                    """
                    CREATE INDEX IF NOT EXISTS idx_model_bindings_target
                    ON model_bindings("targetType", "targetId")
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
