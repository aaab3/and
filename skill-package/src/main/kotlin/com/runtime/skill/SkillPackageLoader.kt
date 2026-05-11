package com.runtime.skill

import com.runtime.core.AppResult

interface SkillPackageLoader {
    suspend fun load(
        source: SkillPackageSource
    ): AppResult<LoadedSkillPackage>
}
