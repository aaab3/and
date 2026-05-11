package com.runtime.permission

import com.runtime.core.AppResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultPermissionGateTest {

    @Test
    fun allowByDefault() = runBlocking {
        val gate = DefaultPermissionGate()
        val r = assertIs<AppResult.Success<PermissionDecision>>(
            gate.evaluate(PermissionRequest(toolName = "safe_tool"))
        )
        assertIs<PermissionDecision.Allow>(r.value)
    }

    @Test
    fun denyListedTool() = runBlocking {
        val gate = DefaultPermissionGate(deniedToolNames = setOf("danger"))
        val r = assertIs<AppResult.Success<PermissionDecision>>(
            gate.evaluate(PermissionRequest(toolName = "danger"))
        )
        assertIs<PermissionDecision.Deny>(r.value)
    }

    @Test
    fun denyOverridesConfirmationFlag() = runBlocking {
        val gate = DefaultPermissionGate(
            deniedToolNames = setOf("x"),
            confirmationRequiredToolNames = setOf("x")
        )
        val r = assertIs<AppResult.Success<PermissionDecision>>(
            gate.evaluate(
                PermissionRequest(toolName = "x", requiresUserConfirmation = true)
            )
        )
        assertIs<PermissionDecision.Deny>(r.value)
    }

    @Test
    fun requiresConfirmationFromRequestMetadata() = runBlocking {
        val gate = DefaultPermissionGate()
        val r = assertIs<AppResult.Success<PermissionDecision>>(
            gate.evaluate(
                PermissionRequest(toolName = "t", requiresUserConfirmation = true)
            )
        )
        assertIs<PermissionDecision.RequiresUserConfirmation>(r.value)
    }

    @Test
    fun requiresConfirmationFromPolicySet() = runBlocking {
        val gate = DefaultPermissionGate(confirmationRequiredToolNames = setOf("slow"))
        val r = assertIs<AppResult.Success<PermissionDecision>>(
            gate.evaluate(PermissionRequest(toolName = "slow"))
        )
        assertIs<PermissionDecision.RequiresUserConfirmation>(r.value)
    }

    @Test
    fun blankToolNameFails() = runBlocking {
        val gate = DefaultPermissionGate()
        assertTrue(gate.evaluate(PermissionRequest(toolName = "   ")) is AppResult.Failure)
    }
}
