package com.runtime.binding.sqlite

import com.runtime.binding.ModelProfile
import com.runtime.binding.ModelProfileRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteModelProfileRepository(
    private val dataSource: BindingSqliteDataSource
) : ModelProfileRepository {

    override suspend fun getModelProfile(modelProfileId: String): AppResult<ModelProfile?> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerId","modelId","displayName","contextWindow","capabilitiesJson"
                    FROM model_profiles WHERE "id" = ?
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, modelProfileId)
                    ps.executeQuery().use { rs ->
                        if (!rs.next()) return@withConnection AppResult.Success(null)
                        mapRow(rs)
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to load model profile", cause = e.message)
            )
        }
    }

    override suspend fun listModelProfiles(providerId: String): AppResult<List<ModelProfile>> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                conn.prepareStatement(
                    """
                    SELECT "id","providerId","modelId","displayName","contextWindow","capabilitiesJson"
                    FROM model_profiles WHERE "providerId" = ? ORDER BY "id" ASC
                    """.trimIndent()
                ).use { ps ->
                    ps.setString(1, providerId)
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<ModelProfile>()
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
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to list model profiles", cause = e.message)
            )
        }
    }

    private fun mapRow(rs: java.sql.ResultSet): AppResult<ModelProfile> {
        val caps = when (val c = decodeStringSet(rs.getString("capabilitiesJson"))) {
            is AppResult.Failure -> return c
            is AppResult.Success -> c.value
        }
        val cw = if (rs.getObject("contextWindow") == null) null else rs.getInt("contextWindow")
        return AppResult.Success(
            ModelProfile(
                id = rs.getString("id"),
                providerId = rs.getString("providerId"),
                modelId = rs.getString("modelId"),
                displayName = rs.getString("displayName"),
                contextWindow = cw,
                capabilities = caps
            )
        )
    }
}
