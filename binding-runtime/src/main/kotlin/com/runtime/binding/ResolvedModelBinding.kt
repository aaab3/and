package com.runtime.binding

data class ResolvedModelBinding(
    val binding: ModelBinding,
    val provider: ProviderDefinition,
    val model: ModelProfile,
    val credentialRef: CredentialRef,
    val secretValue: String
)
