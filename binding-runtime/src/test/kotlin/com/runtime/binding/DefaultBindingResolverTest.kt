package com.runtime.binding

import com.runtime.binding.sqlite.BindingSqlitePersistence
import com.runtime.core.AppResult
import com.runtime.secret.sqlite.SqliteSecretProvider
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultBindingResolverTest {

    @Test
    fun scopePriorityConversationOverWorkspace() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            seedBase(persistence, secretAlias = "k1")
            assertIs<AppResult.Success<Unit>>(secrets.putSecret("k1", "secret-1"))
            insertBinding(persistence, "b-conv", "CONVERSATION", "conv-1", priority = 0)
            insertBinding(persistence, "b-ws", "WORKSPACE", "ws-1", priority = 99)

            val resolver = makeResolver(persistence, secrets)
            val r = assertIs<AppResult.Success<ResolvedModelBinding>>(
                resolver.resolve(
                    BindingResolveRequest(workspaceId = "ws-1", conversationId = "conv-1")
                )
            )
            assertEquals("b-conv", r.value.binding.id)
            assertEquals("secret-1", r.value.secretValue)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    @Test
    fun fallbackToGlobalWhenNoWorkspaceMatch() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            seedBase(persistence, secretAlias = "k1")
            assertIs<AppResult.Success<Unit>>(secrets.putSecret("k1", "g"))
            insertBinding(persistence, "b-global", "GLOBAL", null, priority = 0)

            val resolver = makeResolver(persistence, secrets)
            val r = assertIs<AppResult.Success<ResolvedModelBinding>>(
                resolver.resolve(BindingResolveRequest(workspaceId = "orphan-ws"))
            )
            assertEquals("b-global", r.value.binding.id)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    @Test
    fun skillScopeBetweenConversationAbsentAndWorkspace() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            seedBase(persistence, secretAlias = "k1")
            assertIs<AppResult.Success<Unit>>(secrets.putSecret("k1", "s"))
            insertBinding(persistence, "b-skill", "SKILL", "skill-9", priority = 0)
            insertBinding(persistence, "b-ws", "WORKSPACE", "ws-1", priority = 100)

            val resolver = makeResolver(persistence, secrets)
            val r = assertIs<AppResult.Success<ResolvedModelBinding>>(
                resolver.resolve(BindingResolveRequest(workspaceId = "ws-1", skillId = "skill-9"))
            )
            assertEquals("b-skill", r.value.binding.id)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    @Test
    fun higherPriorityBindingWinsWithinSameScope() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            seedBase(persistence, secretAlias = "k1")
            assertIs<AppResult.Success<Unit>>(secrets.putSecret("k1", "x"))
            insertBinding(persistence, "b-low", "WORKSPACE", "ws-1", priority = 1)
            insertBinding(persistence, "b-high", "WORKSPACE", "ws-1", priority = 50)

            val resolver = makeResolver(persistence, secrets)
            val r = assertIs<AppResult.Success<ResolvedModelBinding>>(
                resolver.resolve(BindingResolveRequest(workspaceId = "ws-1"))
            )
            assertEquals("b-high", r.value.binding.id)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    @Test
    fun missingSecretFails() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            seedBase(persistence, secretAlias = "missing-alias")
            insertBinding(persistence, "b1", "GLOBAL", null, priority = 0)

            val resolver = makeResolver(persistence, secrets)
            assertTrue(resolver.resolve(BindingResolveRequest(workspaceId = "ws-1")) is AppResult.Failure)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    @Test
    fun noBindingAnywhereFails() = runBlocking {
        val bindingDb = Files.createTempFile("br-binding", ".db").toAbsolutePath().toString()
        val secretDb = Files.createTempFile("br-secret", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(bindingDb)
        val secrets = SqliteSecretProvider(secretDb)
        try {
            val resolver = makeResolver(persistence, secrets)
            assertTrue(resolver.resolve(BindingResolveRequest(workspaceId = "ws-1")) is AppResult.Failure)
        } finally {
            persistence.close()
            secrets.close()
            Files.deleteIfExists(java.nio.file.Paths.get(bindingDb))
            Files.deleteIfExists(java.nio.file.Paths.get(secretDb))
        }
    }

    private fun makeResolver(
        persistence: BindingSqlitePersistence,
        secrets: SqliteSecretProvider
    ): DefaultBindingResolver =
        DefaultBindingResolver(
            persistence.modelBindingRepository,
            persistence.providerDefinitionRepository,
            persistence.modelProfileRepository,
            persistence.credentialRefRepository,
            secrets
        )

    private fun seedBase(persistence: BindingSqlitePersistence, secretAlias: String) {
        val now = 1L
        persistence.withConnection { conn ->
            conn.createStatement().use { st ->
                st.executeUpdate(
                    """
                    INSERT INTO provider_definitions
                    ("id","providerType","name","baseUrl","defaultHeadersJson","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('p1','OPENAI_COMPATIBLE','P','https://api.example','{}','[]',$now,$now)
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO credential_refs
                    ("id","providerId","displayName","secretAlias","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('c1','p1','C','$secretAlias',$now,$now)
                    """.trimIndent()
                )
                st.executeUpdate(
                    """
                    INSERT INTO model_profiles
                    ("id","providerId","modelId","displayName","contextWindow","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                    VALUES ('m1','p1','m','M',4096,'[]',$now,$now)
                    """.trimIndent()
                )
            }
        }
    }

    private fun insertBinding(
        persistence: BindingSqlitePersistence,
        id: String,
        targetType: String,
        targetId: String?,
        priority: Int
    ) {
        val now = 1L
        persistence.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO model_bindings
                ("id","targetType","targetId","providerId","modelProfileId","credentialRefId","priority","createdAtEpochMs","updatedAtEpochMs")
                VALUES (?,?,?,?,?,?,?,?,?)
                """.trimIndent()
            ).use { ps ->
                ps.setString(1, id)
                ps.setString(2, targetType)
                if (targetId == null) ps.setNull(3, java.sql.Types.VARCHAR) else ps.setString(3, targetId)
                ps.setString(4, "p1")
                ps.setString(5, "m1")
                ps.setString(6, "c1")
                ps.setInt(7, priority)
                ps.setLong(8, now)
                ps.setLong(9, now)
                ps.executeUpdate()
            }
        }
    }
}
