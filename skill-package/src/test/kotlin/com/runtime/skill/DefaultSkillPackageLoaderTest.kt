package com.runtime.skill

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultSkillPackageLoaderTest {

    private val loader = DefaultSkillPackageLoader()

    private val minimalSkillMd =
        """
        ---
        name: test_skill
        description: Test
        version: 1.0.0
        workflow: workflow.yaml
        tools: []
        permissions: []
        ---

        # Human docs only

        Not executable.
        """.trimIndent()

    private val minimalWorkflow =
        """
        id: test_skill
        name: Test
        version: 1.0.0
        steps: []
        """.trimIndent()

    @Test
    fun loadFromDirectory() = runBlocking {
        val dir = Files.createTempDirectory("skill-dir").toFile()
        try {
            File(dir, "skill.md").writeText(minimalSkillMd)
            File(dir, "workflow.yaml").writeText(minimalWorkflow)
            File(dir, "assets").mkdirs()
            File(File(dir, "assets"), "note.txt").writeText("asset")

            val r = assertIs<AppResult.Success<LoadedSkillPackage>>(
                loader.load(SkillPackageSource.Directory(dir.absolutePath))
            )
            assertEquals("test_skill", r.value.manifest.name)
            assertEquals("workflow.yaml", r.value.manifest.workflow)
            assertTrue(r.value.markdownBody.contains("Human docs"))
            assertTrue(File(r.value.workflowFilePath).exists())
            assertEquals(1, r.value.assetPaths.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun loadFromZip() = runBlocking {
        val zip = Files.createTempFile("skill", ".zip").toFile()
        try {
            ZipOutputStream(zip.outputStream()).use { zos ->
                fun put(name: String, content: String) {
                    zos.putNextEntry(ZipEntry(name))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
                put("nested/skill.md", minimalSkillMd)
                put("nested/workflow.yaml", minimalWorkflow)
            }

            val r = assertIs<AppResult.Success<LoadedSkillPackage>>(
                loader.load(SkillPackageSource.ZipFile(zip.absolutePath))
            )
            assertEquals("test_skill", r.value.manifest.name)
            assertTrue(File(r.value.workflowFilePath).exists())
        } finally {
            zip.delete()
        }
    }

    @Test
    fun missingSkillMdFails() = runBlocking {
        val dir = Files.createTempDirectory("skill-empty").toFile()
        try {
            val r = loader.load(SkillPackageSource.Directory(dir.absolutePath))
            assertTrue(r is AppResult.Failure)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingWorkflowFails() = runBlocking {
        val dir = Files.createTempDirectory("skill-nowf").toFile()
        try {
            File(dir, "skill.md").writeText(minimalSkillMd)
            val r = loader.load(SkillPackageSource.Directory(dir.absolutePath))
            assertTrue(r is AppResult.Failure)
        } finally {
            dir.deleteRecursively()
        }
    }
}
