package com.runtime.skill

sealed interface SkillPackageSource {
    data class Directory(val path: String) : SkillPackageSource
    data class ZipFile(val path: String) : SkillPackageSource
}

data class SkillManifest(
    val name: String,
    val description: String,
    val version: String,
    val workflow: String,
    val inputs: Map<String, String> = emptyMap(),
    val defaults: Map<String, String> = emptyMap(),
    val tools: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
    val model: String? = null,
    val output: Map<String, String> = emptyMap()
)

data class LoadedSkillPackage(
    val manifest: SkillManifest,
    val markdownBody: String,
    val workflowFilePath: String,
    val assetPaths: List<String> = emptyList()
)
