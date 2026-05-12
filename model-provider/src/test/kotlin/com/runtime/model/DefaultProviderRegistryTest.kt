package com.runtime.model

import com.runtime.binding.ProviderType
import com.runtime.core.AppResult
import com.runtime.model.openai.OpenAiCompatibleModelProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultProviderRegistryTest {

    @Test
    fun returnsOpenAiByType() {
        val openAi = OpenAiCompatibleModelProvider()
        val registry = DefaultProviderRegistry(listOf(openAi))
        val got = assertIs<AppResult.Success<ModelProvider>>(registry.getProvider(ProviderType.OPENAI_COMPATIBLE))
        assertEquals(ProviderType.OPENAI_COMPATIBLE, got.value.providerType)
    }

    @Test
    fun listProvidersIncludesAllRegistered() {
        val registry = DefaultProviderRegistry(
            listOf(OpenAiCompatibleModelProvider())
        )
        assertEquals(1, registry.listProviders().size)
    }
}
