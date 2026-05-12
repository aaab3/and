package com.runtime.skill

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * v2 skill loader: supports both single .md files and directories.
 *
 * Directory structure:
 *   skill-folder/
 *   ├── SKILL.md          (required: frontmatter + body)
 *   └── references/       (optional: all .md/.txt files are appended to context)
 *       ├── guide-1.md
 *       └── guide-2.md
 *
 * Single file: just a .md with frontmatter.
 */
class DefaultSkillPackageLoader : SkillPackageLoader {

    private val yaml = Yaml()

    override suspend fun load(source: SkillPackageSource): AppResult<LoadedSkillPackage> = withContext(Dispatchers.IO) {
        when (source) {
            is SkillPackageSource.MarkdownFile -> loadFromFile(File(source.path))
            is SkillPackageSource.Directory -> loadFromDirectory(File(source.path))
        }
    }

    private fun loadFromDirectory(dir: File): AppResult<LoadedSkillPackage> {
        if (!dir.isDirectory) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "Not a directory"))
        }

        // Find the main skill file (SKILL.md or skill.md)
        val skillFile = dir.listFiles()?.firstOrNull {
            it.name.equals("SKILL.md", ignoreCase = true)
        } ?: dir.listFiles()?.firstOrNull {
            it.extension.equals("md", ignoreCase = true) && it.name.contains("skill", ignoreCase = true)
        } ?: return AppResult.Failure(
            AppError(ErrorCodes.NOT_FOUND, "No SKILL.md found in directory")
        )

        // Parse the main file
        val raw = readFileText(skillFile) ?: return AppResult.Failure(
            AppError(ErrorCodes.STORAGE_ERROR, "Failed to read ${skillFile.name}")
        )

        val (frontmatter, body) = splitFrontMatter(raw)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, "SKILL.md must begin with YAML frontmatter (---)")
            )

        val manifest = parseManifest(frontmatter)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.VALIDATION_FAILED, "Missing required field: name or description")
            )

        // Load reference files
        val refsDir = File(dir, "references")
        val refFiles = mutableListOf<String>()
        val refContent = StringBuilder()

        if (refsDir.isDirectory) {
            refsDir.listFiles()
                ?.filter { it.isFile && it.extension in listOf("md", "txt", "markdown") }
                ?.sortedBy { it.name }
                ?.forEach { refFile ->
                    val text = readFileText(refFile)
                    if (text != null) {
                        refFiles.add(refFile.name)
                        refContent.append("\n\n---\n# Reference: ${refFile.nameWithoutExtension}\n\n")
                        refContent.append(text)
                    }
                }
        }

        // Combine body + references into full system prompt
        val fullBody = if (refContent.isEmpty()) body
            else "$body\n\n$refContent"

        return AppResult.Success(
            LoadedSkillPackage(
                manifest = manifest,
                markdownBody = fullBody,
                sourcePath = dir.absolutePath,
                referenceFiles = refFiles
            )
        )
    }

    private fun loadFromFile(file: File): AppResult<LoadedSkillPackage> {
        if (!file.isFile) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, "File does not exist"))
        }

        val raw = readFileText(file) ?: return AppResult.Failure(
            AppError(ErrorCodes.STORAGE_ERROR, "Failed to read file")
        )

        val (frontmatter, body) = splitFrontMatter(raw)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, "File must begin with YAML frontmatter (---)")
            )

        val manifest = parseManifest(frontmatter)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.VALIDATION_FAILED, "Missing required field: name or description")
            )

        return AppResult.Success(
            LoadedSkillPackage(
                manifest = manifest,
                markdownBody = body,
                sourcePath = file.absolutePath
            )
        )
    }

    private fun readFileText(file: File): String? = try {
        file.readText(Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun splitFrontMatter(raw: String): Pair<String, String>? {
        val text = raw.trimStart()
        if (!text.startsWith("---")) return null
        val afterFirst = text.removePrefix("---").trimStart()
        val endMarker = Regex("\r?\n---\r?\n").find(afterFirst) ?: return null
        val fm = afterFirst.substring(0, endMarker.range.first).trim()
        val body = afterFirst.substring(endMarker.range.last + 1).trim()
        return fm to body
    }

    private fun parseManifest(frontmatter: String): SkillManifest? {
        val map = try {
            val root = yaml.load<Any?>(frontmatter)
            if (root is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                root.entries.associate { (k, v) -> k.toString() to v } as Map<String, Any?>
            } else null
        } catch (_: Exception) {
            null
        } ?: return null

        val name = map["name"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val description = map["description"]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return SkillManifest(
            name = name,
            description = description,
            tools = stringList(map["tools"]),
            model = map["model"]?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        )
    }

    private fun stringList(obj: Any?): List<String> = when (obj) {
        is List<*> -> obj.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
        is String -> obj.trim().takeIf { it.isNotEmpty() }?.let { listOf(it) } ?: emptyList()
        else -> emptyList()
    }
}
