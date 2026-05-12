package com.runtime.skill

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultSkillPackageLoaderTest {

    private val loader = DefaultSkillPackageLoader()

    @Test
    fun loadsSingleMarkdownFile() = runBlocking {
        val file = Files.createTempFile("skill-", ".md").toFile()
        try {
            file.writeText("""
                ---
                name: translator
                description: Translate text
                tools: [http_fetch]
                ---
                
                You are a translator.
            """.trimIndent())
            val r = assertIs<AppResult.Success<LoadedSkillPackage>>(
                loader.load(SkillPackageSource.MarkdownFile(file.absolutePath))
            )
            assertEquals("translator", r.value.manifest.name)
            assertEquals(listOf("http_fetch"), r.value.manifest.tools)
            assertTrue(r.value.markdownBody.contains("translator"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun loadsDirectoryWithReferences() = runBlocking {
        val dir = Files.createTempDirectory("skill-dir-").toFile()
        try {
            File(dir, "SKILL.md").writeText("""
                ---
                name: reddit-commenter
                description: Generate Reddit comments
                ---
                
                You generate comments for Reddit posts.
            """.trimIndent())

            val refs = File(dir, "references")
            refs.mkdirs()
            File(refs, "guide.md").writeText("# Guide\nBe helpful.")
            File(refs, "examples.txt").writeText("Example comment: nice build!")

            val r = assertIs<AppResult.Success<LoadedSkillPackage>>(
                loader.load(SkillPackageSource.Directory(dir.absolutePath))
            )
            assertEquals("reddit-commenter", r.value.manifest.name)
            assertTrue(r.value.markdownBody.contains("You generate comments"))
            assertTrue(r.value.markdownBody.contains("Be helpful"))
            assertTrue(r.value.markdownBody.contains("nice build"))
            assertEquals(2, r.value.referenceFiles.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun directoryWithoutSkillMdFails() = runBlocking {
        val dir = Files.createTempDirectory("skill-empty-").toFile()
        try {
            File(dir, "random.txt").writeText("not a skill")
            val r = loader.load(SkillPackageSource.Directory(dir.absolutePath))
            assertTrue(r is AppResult.Failure)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFrontmatterFails() = runBlocking {
        val file = Files.createTempFile("skill-", ".md").toFile()
        try {
            file.writeText("No frontmatter here")
            val r = loader.load(SkillPackageSource.MarkdownFile(file.absolutePath))
            assertTrue(r is AppResult.Failure)
        } finally {
            file.delete()
        }
    }
}
