package com.runtime.android.db

import androidx.room.*

@Dao
interface PromptTemplateDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PromptTemplateEntity)

    @Query("SELECT * FROM prompt_templates ORDER BY command ASC")
    suspend fun getAll(): List<PromptTemplateEntity>

    @Query("DELETE FROM prompt_templates WHERE id = :id")
    suspend fun delete(id: String)
}
