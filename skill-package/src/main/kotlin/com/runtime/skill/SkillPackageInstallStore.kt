package com.runtime.skill

import com.runtime.core.AppResult

/**
 * Persistence boundary for installed skills. v2: single markdown source only.
 */
interface SkillPackageInstallStore {
    suspend fun persistLoadedPackage(loaded: LoadedSkillPackage): AppResult<Unit>

    suspend fun listInstalled(): AppResult<List<InstalledSkill>>

    suspend fun findByName(name: String): AppResult<InstalledSkill?>

    suspend fun deleteByName(name: String): AppResult<Unit>
}

data class InstalledSkill(
    val name: String,
    val description: String,
    val body: String,
    val tools: List<String>,
    val model: String?,
    val sourcePath: String,
    val referenceCount: Int,
    val installedAtEpochMs: Long
)
