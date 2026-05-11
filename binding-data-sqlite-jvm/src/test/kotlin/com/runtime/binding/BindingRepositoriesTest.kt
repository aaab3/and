package com.runtime.binding

import com.runtime.binding.sqlite.BindingSqlitePersistence
import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BindingRepositoriesTest {

    @Test
    fun seedAndQueryRepositories() = runBlocking {
        val db = Files.createTempFile("runtime-binding", ".db").toAbsolutePath().toString()
        val persistence = BindingSqlitePersistence(db)
        try {
            val now = 1L
            persistence.dataSource.withConnection { conn ->
                conn.createStatement().use { st ->
                    st.executeUpdate(
                        """
                        INSERT INTO provider_definitions
                        ("id","providerType","name","baseUrl","defaultHeadersJson","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                        VALUES ('p1','OPENAI_COMPATIBLE','OpenAI-ish','https://example.invalid','{}','[]',$now,$now)
                        """.trimIndent()
                    )
                    st.executeUpdate(
                        """
                        INSERT INTO credential_refs
                        ("id","providerId","displayName","secretAlias","createdAtEpochMs","updatedAtEpochMs")
                        VALUES ('c1','p1','Default','alias/key',$now,$now)
                        """.trimIndent()
                    )
                    st.executeUpdate(
                        """
                        INSERT INTO model_profiles
                        ("id","providerId","modelId","displayName","contextWindow","capabilitiesJson","createdAtEpochMs","updatedAtEpochMs")
                        VALUES ('m1','p1','gpt-test','Test',8192,'[]',$now,$now)
                        """.trimIndent()
                    )
                    st.executeUpdate(
                        """
                        INSERT INTO model_bindings
                        ("id","targetType","targetId","providerId","modelProfileId","credentialRefId","priority","createdAtEpochMs","updatedAtEpochMs")
                        VALUES ('b1','WORKSPACE','ws-1','p1','m1','c1',10,$now,$now)
                        """.trimIndent()
                    )
                }
            }

            val p = assertIs<AppResult.Success<ProviderDefinition?>>(
                persistence.providerDefinitionRepository.getProvider("p1")
            )
            assertEquals("https://example.invalid", p.value!!.baseUrl)

            val cred = assertIs<AppResult.Success<CredentialRef?>>(
                persistence.credentialRefRepository.getCredentialRef("c1")
            )
            assertEquals("alias/key", cred.value!!.secretAlias)

            val model = assertIs<AppResult.Success<ModelProfile?>>(
                persistence.modelProfileRepository.getModelProfile("m1")
            )
            assertEquals(8192, model.value!!.contextWindow)

            val binds = assertIs<AppResult.Success<List<ModelBinding>>>(
                persistence.modelBindingRepository.findBindings(BindingTargetType.WORKSPACE, "ws-1")
            )
            assertEquals(1, binds.value.size)
            assertEquals("b1", binds.value[0].id)
        } finally {
            persistence.close()
            Files.deleteIfExists(java.nio.file.Paths.get(db))
        }
    }
}
