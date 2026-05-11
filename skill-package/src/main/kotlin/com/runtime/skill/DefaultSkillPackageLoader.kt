package com.runtime.skill

import com.runtime.core.AppError
import com.runtime.core.AppResult
import com.runtime.core.ErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.zip.ZipFile

class DefaultSkillPackageLoader : SkillPackageLoader {

    private val yaml = Yaml()

    override suspend fun load(source: SkillPackageSource): AppResult<LoadedSkillPackage> = withContext(Dispatchers.IO) {
        when (source) {
            is SkillPackageSource.Directory -> loadFromDirectoryRoot(File(source.path))
            is SkillPackageSource.ZipFile -> loadFromZip(File(source.path))
        }
    }

    private fun loadFromZip(zip: File): AppResult<LoadedSkillPackage> {
        if (!zip.isFile) {
            return AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, message = "ZIP path is not a file"))
        }
        val tempRoot = Files.createTempDirectory("skillpkg-zip-").toFile()
        tempRoot.deleteOnExit()
        return try {
            unzipSafe(zip, tempRoot)
            val skillRoot = findSkillPackageRoot(tempRoot)
                ?: return AppResult.Failure(
                    AppError(ErrorCodes.NOT_FOUND, message = "skill.md not found in ZIP archive")
                )
            loadFromSkillRoot(skillRoot)
        } catch (e: IOException) {
            AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to read ZIP package", cause = e.message)
            )
        }
    }

    private fun loadFromDirectoryRoot(dir: File): AppResult<LoadedSkillPackage> {
        if (!dir.exists() || !dir.isDirectory) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "Directory does not exist or is not a directory")
            )
        }
        val skillRoot = findSkillPackageRoot(dir)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.NOT_FOUND, message = "skill.md not found under directory")
            )
        return loadFromSkillRoot(skillRoot)
    }

    /**
     * Accepts either `root/skill.md` or `root/<subdir>/skill.md` (single nested folder).
     */
    private fun findSkillPackageRoot(root: File): File? {
        val direct = File(root, "skill.md")
        if (direct.isFile) return root
        root.listFiles()?.filter { it.isDirectory }?.forEach { sub ->
            if (File(sub, "skill.md").isFile) return sub
        }
        return null
    }

    private fun loadFromSkillRoot(skillRoot: File): AppResult<LoadedSkillPackage> {
        val skillFile = File(skillRoot, "skill.md")
        val raw = try {
            skillFile.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return AppResult.Failure(
                AppError(ErrorCodes.STORAGE_ERROR, message = "Failed to read skill.md", cause = e.message)
            )
        }

        val (frontmatter, body) = when (val split = splitFrontMatter(raw)) {
            is AppResult.Failure -> return split
            is AppResult.Success -> split.value
        }

        val yamlMap = when (val y = parseYamlMapping(frontmatter)) {
            is AppResult.Failure -> return y
            is AppResult.Success -> y.value
        }

        val manifest = when (val m = yamlToManifest(yamlMap)) {
            is AppResult.Failure -> return m
            is AppResult.Success -> m.value
        }

        if (manifest.workflow.contains("..") || manifest.workflow.startsWith(File.separator)) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.INVALID_INPUT,
                    message = "workflow path must be relative and must not contain '..'",
                    metadata = mapOf("workflow" to manifest.workflow)
                )
            )
        }

        val workflowFile = File(skillRoot, manifest.workflow).canonicalFile
        val rootCanonical = skillRoot.canonicalFile
        if (!workflowFile.path.startsWith(rootCanonical.path + File.separator) && workflowFile != rootCanonical) {
            return AppResult.Failure(
                AppError(ErrorCodes.INVALID_INPUT, message = "workflow path escapes package root")
            )
        }
        if (!workflowFile.isFile) {
            return AppResult.Failure(
                AppError(
                    code = ErrorCodes.NOT_FOUND,
                    message = "workflow file not found",
                    metadata = mapOf("workflow" to manifest.workflow)
                )
            )
        }
        if (workflowFile.length() == 0L) {
            return AppResult.Failure(
                AppError(ErrorCodes.VALIDATION_FAILED, message = "workflow file is empty")
            )
        }

        val assetsDir = File(skillRoot, "assets")
        val assetPaths = if (assetsDir.isDirectory) {
            assetsDir.walkTopDown().maxDepth(10).filter { it.isFile }.map { it.absolutePath }.toList()
        } else {
            emptyList()
        }

        return AppResult.Success(
            LoadedSkillPackage(
                manifest = manifest,
                markdownBody = body,
                workflowFilePath = workflowFile.absolutePath,
                assetPaths = assetPaths
            )
        )
    }

    private fun splitFrontMatter(raw: String): AppResult<Pair<String, String>> {
        val text = raw.trimStart()
        if (!text.startsWith("---")) {
            return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "skill.md must begin with YAML frontmatter delimited by ---")
            )
        }
        val afterFirst = text.removePrefix("---").trimStart()
        val endMarker = Regex("\r?\n---\r?\n").find(afterFirst)
            ?: return AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "skill.md frontmatter must end with a line --- before body")
            )
        val fm = afterFirst.substring(0, endMarker.range.first).trim()
        val body = afterFirst.substring(endMarker.range.last + 1).trim()
        return AppResult.Success(fm to body)
    }

    private fun parseYamlMapping(frontmatter: String): AppResult<Map<String, Any?>> =
        try {
            when (val root = yaml.load<Any?>(frontmatter)) {
                null -> AppResult.Failure(AppError(ErrorCodes.INVALID_INPUT, message = "Empty YAML frontmatter"))
                is Map<*, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val m = root.entries.associate { (k, v) -> k.toString() to v } as Map<String, Any?>
                    AppResult.Success(m)
                }
                else -> AppResult.Failure(
                    AppError(ErrorCodes.PARSE_ERROR, message = "YAML frontmatter must be a mapping (object) at root")
                )
            }
        } catch (e: Exception) {
            AppResult.Failure(
                AppError(ErrorCodes.PARSE_ERROR, message = "Invalid YAML in skill.md frontmatter", cause = e.message)
            )
        }

    private fun yamlToManifest(map: Map<String, Any?>): AppResult<SkillManifest> {
        fun req(key: String): AppResult<String> {
            val v = map[key]?.toString()?.trim()
            return if (v.isNullOrEmpty()) {
                AppResult.Failure(
                    AppError(ErrorCodes.VALIDATION_FAILED, message = "Missing required manifest field: $key")
                )
            } else {
                AppResult.Success(v)
            }
        }
        val name = when (val r = req("name")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val description = when (val r = req("description")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val version = when (val r = req("version")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }
        val workflow = when (val r = req("workflow")) {
            is AppResult.Failure -> return r
            is AppResult.Success -> r.value
        }

        return AppResult.Success(
            SkillManifest(
                name = name,
                description = description,
                version = version,
                workflow = workflow,
                inputs = stringMapOrEmpty(map["inputs"]),
                defaults = stringMapOrEmpty(map["defaults"]),
                tools = stringListOrEmpty(map["tools"]),
                permissions = stringListOrEmpty(map["permissions"]),
                model = map["model"]?.toString()?.trim()?.takeIf { it.isNotEmpty() },
                output = stringMapOrEmpty(map["output"])
            )
        )
    }

    private fun stringMapOrEmpty(obj: Any?): Map<String, String> {
        if (obj !is Map<*, *>) return emptyMap()
        return obj.entries.associate { (k, v) ->
            k.toString() to when (v) {
                null -> ""
                is Map<*, *> -> v.entries.joinToString(",") { "${it.key}=${it.value}" }
                is List<*> -> v.joinToString(",")
                else -> v.toString()
            }
        }
    }

    private fun stringListOrEmpty(obj: Any?): List<String> =
        when (obj) {
            null -> emptyList()
            is List<*> -> obj.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
            is String -> listOf(obj.trim()).filter { it.isNotEmpty() }
            else -> emptyList()
        }

    private fun unzipSafe(zipFile: File, destDir: File) {
        val destCanonicalBase = destDir.canonicalFile
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.isBlank()) continue
                val outFile = File(destDir, entry.name)
                val outCanonical = outFile.canonicalFile
                val basePath = destCanonicalBase.path + File.separator
                if (entry.isDirectory) {
                    if (!outCanonical.path.startsWith(destCanonicalBase.path) && outCanonical != destCanonicalBase) {
                        throw IOException("Bad zip entry: ${entry.name}")
                    }
                    outFile.mkdirs()
                    continue
                }
                if (!outCanonical.path.startsWith(basePath) && outCanonical != destCanonicalBase) {
                    throw IOException("Bad zip entry: ${entry.name}")
                }
                outFile.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    outCanonical.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}
