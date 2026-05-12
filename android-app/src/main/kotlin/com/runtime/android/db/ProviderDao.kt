package com.runtime.android.db

import androidx.room.*

@Dao
interface ProviderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ProviderEntity)

    @Query("SELECT * FROM providers ORDER BY createdAtEpochMs ASC")
    suspend fun getAll(): List<ProviderEntity>

    @Query("SELECT * FROM providers WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProviderEntity?

    @Query("SELECT * FROM providers WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ProviderEntity?

    @Query("UPDATE providers SET isDefault = 0")
    suspend fun clearDefault()

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)
}
