package com.runtime.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prompt_templates")
data class PromptTemplateEntity(
    @PrimaryKey val id: String,
    val command: String,       // e.g. "翻译", "总结", "代码"
    val promptText: String,    // The template text, may contain {input} placeholder
    val createdAtEpochMs: Long
)
