package com.runtime.binding

import com.runtime.core.AppResult

interface BindingResolver {
    suspend fun resolve(
        request: BindingResolveRequest
    ): AppResult<ResolvedModelBinding>
}
