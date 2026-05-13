package com.runtime.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val skillName: String?,        // null = use default runtime
    val prompt: String,
    val cronExpression: String,    // simplified: "daily_09:00" / "weekly_mon_09:00" / custom
    val enabled: Boolean = true,
    val lastRunEpochMs: Long? = null,
    val createdAtEpochMs: Long
)
