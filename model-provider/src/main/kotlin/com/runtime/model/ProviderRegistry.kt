package com.runtime.model

import com.runtime.binding.ProviderType
import com.runtime.core.AppResult

interface ProviderRegistry {
    fun getProvider(providerType: ProviderType): AppResult<ModelProvider>
    fun listProviders(): List<ModelProvider>
}
