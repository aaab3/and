package com.runtime.secret.sqlite

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import com.runtime.secret.SecretProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

/**
 * Prompt 04: narrow secret storage behind [SecretProvider]. Values never enter binding/conversation DBs.
 */
class SqliteSecretProvider(
    databasePath: String
) : SecretProvider {

    private val dataSource = SecretSqliteDataSource(databasePath)

    fun close() {
        dataSource.close()
    }

    override suspend fun putSecret(alias: String, value: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO secrets("alias","value") VALUES(?,?)
                    ON CONFLICT("alias") DO UPDATE SET "value" = excluded."value"
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, alias)
                    ps.setString(2, value)
                    ps.executeUpdate()
                }
            }
            AppResult.Success(Unit)
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to store secret", cause = e.message)
            )
        }
    }

    override suspend fun getSecret(alias: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement("""SELECT "value" FROM secrets WHERE "alias" = ?""").use { ps ->
                    ps.setString(1, alias)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) {
                            return@withConnection AppResult.Failure(
                                AppError(
                                    code = ErrorCodes.NOT_FOUND,
                                    message = "Secret not found",
                                    metadata = mapOf("alias" to alias)
                                )
                            )
                        }
                        AppResult.Success(rs.getString("value"))
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to read secret", cause = e.message)
            )
        }
    }

    override suspend fun deleteSecret(alias: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            val deleted = dataSource.withConnection { conn ->
                conn.prepareStatement("""DELETE FROM secrets WHERE "alias" = ?""").use { ps ->
                    ps.setString(1, alias)
                    ps.executeUpdate()
                }
            }
            if (deleted == 0) {
                AppResult.Failure(
                    AppError(
                        code = ErrorCodes.NOT_FOUND,
                        message = "Secret not found for delete",
                        metadata = mapOf("alias" to alias)
                    )
                )
            } else {
                AppResult.Success(Unit)
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to delete secret", cause = e.message)
            )
        }
    }
}
