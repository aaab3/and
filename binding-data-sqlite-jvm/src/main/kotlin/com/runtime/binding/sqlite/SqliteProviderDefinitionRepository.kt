package com.runtime.binding.sqlite

import com.runtime.binding.ProviderDefinition
import com.runtime.binding.ProviderDefinitionRepository
import com.runtime.binding.ProviderType
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteProviderDefinitionRepository(
    private val dataSource: BindingSqliteDataSource
) : ProviderDefinitionRepository {

    override suspend fun getProvider(providerId: String): AppResult<ProviderDefinition?> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerType","name","baseUrl","defaultHeadersJson","capabilitiesJson"
                    FROM provider_definitions WHERE "id" = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, providerId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@withConnection AppResult.Success(null)
                        mapRow(rs)
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to load provider", cause = e.message)
            )
        }
    }

    override suspend fun listProviders(): AppResult<List<ProviderDefinition>> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerType","name","baseUrl","defaultHeadersJson","capabilitiesJson"
                    FROM provider_definitions ORDER BY "id" ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<ProviderDefinition>()
                        while (rs.next()) {
                            when (val row = mapRow(rs)) {
                                is AppResult.Failure -> return@withConnection row
                                is AppResult.Success -> out.add(row.value)
                            }
                        }
                        AppResult.Success(out)
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to list providers", cause = e.message)
            )
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): AppResult<ProviderDefinition> {
        val headers = when (val h = decodeStringMap(rs.getString("defaultHeadersJson"))) {
            is AppResult.Failure -> return h
            is AppResult.Success -> h.value
        }
        val caps = when (val c = decodeStringSet(rs.getString("capabilitiesJson"))) {
            is AppResult.Failure -> return c
            is AppResult.Success -> c.value
        }
        return try {
            AppResult.Success(
                ProviderDefinition(
                    id = rs.getString("id"),
                    providerType = ProviderType.valueOf(rs.getString("providerType")),
                    name = rs.getString("name"),
                    baseUrl = rs.getString("baseUrl"),
                    defaultHeaders = headers,
                    capabilities = caps
                )
            )
        } catch (e: IllegalArgumentException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Invalid providerType in storage", cause = e.message)
            )
        }
    }
}
