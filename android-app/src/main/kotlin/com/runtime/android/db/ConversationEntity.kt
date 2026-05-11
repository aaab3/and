package com.runtime.android.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index(value = ["workspaceId"])]
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val workspaceId: String,
    val title: String?,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
