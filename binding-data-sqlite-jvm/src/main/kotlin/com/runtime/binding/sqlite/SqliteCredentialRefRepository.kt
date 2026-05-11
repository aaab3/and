package com.runtime.binding.sqlite

import com.runtime.binding.CredentialRef
import com.runtime.binding.CredentialRefRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteCredentialRefRepository(
    private val dataSource: BindingSqliteDataSource
) : CredentialRefRepository {

    override suspend fun getCredentialRef(credentialRefId: String): AppResult<CredentialRef?> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerId","displayName","secretAlias"
                    FROM credential_refs WHERE "id" = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, credentialRefId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@withConnection AppResult.Success(null)
                        AppResult.Success(
                            CredentialRef(
                                id = rs.getString("id"),
                                providerId = rs.getString("providerId"),
                                displayName = rs.getString("displayName"),
                                secretAlias = rs.getString("secretAlias")
                            )
                        )
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to load credential ref", cause = e.message)
            )
        }
    }

    override suspend fun listCredentialRefs(providerId: String): AppResult<List<CredentialRef>> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerId","displayName","secretAlias"
                    FROM credential_refs WHERE "providerId" = ? ORDER BY "id" ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, providerId)
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<CredentialRef>()
                        while (rs.next()) {
                            out.add(
                                CredentialRef(
                                    id = rs.getString("id"),
                                    providerId = rs.getString("providerId"),
                                    displayName = rs.getString("displayName"),
                                    secretAlias = rs.getString("secretAlias")
                                )
                            )
                        }
                        AppResult.Success(out)
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to list credential refs", cause = e.message)
            )
        }
    }
}
