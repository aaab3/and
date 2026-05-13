package com.runtime.android.db

import androidx.room.*

@Dao
interface ScheduledTaskDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduledTaskEntity)

    @Query("SELECT * FROM scheduled_tasks ORDER BY createdAtEpochMs DESC")
    suspend fun getAll(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduledTaskEntity>

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: String)
}
