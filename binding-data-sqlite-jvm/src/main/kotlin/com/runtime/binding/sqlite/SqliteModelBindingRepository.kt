package com.runtime.binding.sqlite

import com.runtime.binding.BindingTargetType
import com.runtime.binding.ModelBinding
import com.runtime.binding.ModelBindingRepository
import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.SQLException

class SqliteModelBindingRepository(
    private val dataSource: BindingSqliteDataSource
) : ModelBindingRepository {

    override suspend fun findBindings(
        targetType: BindingTargetType,
        targetId: String?
    ): AppResult<List<ModelBinding>> = withContext(Dispatchers.IO) {
        try {
            dataSource.withConnection { conn ->
                val sql =
                    if (targetId == null) {
                        """
                        SELECT "id","targetType","targetId","providerId","modelProfileId","credentialRefId","priority"
                        FROM model_bindings
                        WHERE "targetType" = ? AND "targetId" IS NULL
                        ORDER BY "priority" DESC, "id" ASC
                        """.trimIndent()
                    } else {
                        """
                        SELECT "id","targetType","targetId","providerId","modelProfileId","credentialRefId","priority"
                        FROM model_bindings
                        WHERE "targetType" = ? AND "targetId" = ?
                        ORDER BY "priority" DESC, "id" ASC
                        """.trimIndent()
                    }
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, targetType.name)
                    if (targetId != null) {
                        ps.setString(2, targetId)
                    }
                    ps.executeQuery().use { rs ->
                        val out = mutableListOf<ModelBinding>()
                        while (rs.next()) {
                            try {
                                out.add(
                                    ModelBinding(
                                        id = rs.getString("id"),
                                        targetType = BindingTargetType.valueOf(rs.getString("targetType")),
                                        targetId = rs.getString("targetId").takeUnless { rs.wasNull() },
                                        providerId = rs.getString("providerId"),
                                        modelProfileId = rs.getString("modelProfileId"),
                                        credentialRefId = rs.getString("credentialRefId"),
                                        priority = rs.getInt("priority")
                                    )
                                )
                            } catch (e: IllegalArgumentException) {
                                return@withConnection AppResult.Failure(
                                    AppError(ErrorCodes.STORAGE_ERROR, "Invalid binding targetType in storage", cause = e.message)
                                )
                            }
                        }
                        AppResult.Success(out)
                    }
                }
            }
        } catch (e: SQLException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, "Failed to find bindings", cause = e.message)
            )
        }
    }
}
