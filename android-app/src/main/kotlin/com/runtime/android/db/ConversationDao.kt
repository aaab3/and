package com.runtime.android.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ConversationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: ConversationEntity)

    @Update
    suspend fun update(entity: ConversationEntity): Int

    @Query("SELECT * FROM conversations WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ConversationEntity?

    @Query(
        """
        SELECT * FROM conversations
        WHERE workspaceId = :workspaceId
        ORDER BY updatedAtEpochMs DESC
        LIMIT 1
        """
    )
    suspend fun latestForWorkspace(workspaceId: String): ConversationEntity?
}
