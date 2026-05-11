package com.runtime.skill

import com.runtime.core.AppResult

/**
 * Persistence boundary for installed skills (see `installed_skills` in database-sketch.md).
 * [DefaultSkillPackageLoader] does not call this; callers load first, then persist.
 *
 * @param sourceType `DIRECTORY` or `ZIP` per database sketch.
 * @param sourceRef original path or internal reference string.
 */
interface SkillPackageInstallStore {
    suspend fun persistLoadedPackage(
        loaded: LoadedSkillPackage,
        sourceType: String,
        sourceRef: String
    ): AppResult<Unit>
}
