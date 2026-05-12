package com.runtime.android.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * v2 simplified: combines provider definition + credential + model profile into one row.
 * Each row = one usable provider configuration (name + baseUrl + key alias + model).
 */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val secretAlias: String,
    val modelId: String,
    val isDefault: Boolean = false,
    val createdAtEpochMs: Long
)
