package com.runtime.secret

import com.runtime.core.AppResult
import com.runtime.secret.sqlite.SqliteSecretProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SqliteSecretProviderTest {

    @Test
    fun putGetDelete() = runBlocking {
        val db = Files.createTempFile("runtime-secret", ".db").toAbsolutePath().toString()
        val provider = SqliteSecretProvider(db)
        try {
            assertIs<AppResult.Success<Unit>>(provider.putSecret("alias/key", "hunter2"))
            val got = assertIs<AppResult.Success<String>>(provider.getSecret("alias/key"))
            assertTrue(got.value.contains("hunter2"))

            val missing = provider.getSecret("nope")
            assertTrue(missing is AppResult.Failure)

            assertIs<AppResult.Success<Unit>>(provider.deleteSecret("alias/key"))
            assertTrue(provider.getSecret("alias/key") is AppResult.Failure)
        } finally {
            provider.close()
            Files.deleteIfExists(java.nio.file.Paths.get(db))
        }
    }
}
