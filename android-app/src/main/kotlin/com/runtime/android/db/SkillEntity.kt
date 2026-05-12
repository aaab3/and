package com.runtime.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installed_skills")
data class SkillEntity(
    @PrimaryKey val name: String,
    val description: String,
    val body: String,
    val toolsJson: String,       // JSON array: ["http_fetch","current_time"]
    val model: String?,
    val sourcePath: String,
    val referenceCount: Int,
    val installedAtEpochMs: Long
)
