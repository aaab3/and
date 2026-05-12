package com.runtime.skill

/**
 * A skill source: either a single .md file or a directory containing SKILL.md + references/.
 */
sealed interface SkillPackageSource {
    data class MarkdownFile(val path: String) : SkillPackageSource
    data class Directory(val path: String) : SkillPackageSource
}

/**
 * YAML frontmatter fields for a skill.
 */
data class SkillManifest(
    val name: String,
    val description: String,
    val tools: List<String> = emptyList(),
    val model: String? = null
)

/**
 * A loaded skill: parsed frontmatter + full body (SKILL.md body + all reference files concatenated).
 */
data class LoadedSkillPackage(
    val manifest: SkillManifest,
    val markdownBody: String,
    val sourcePath: String,
    val referenceFiles: List<String> = emptyList()
)
