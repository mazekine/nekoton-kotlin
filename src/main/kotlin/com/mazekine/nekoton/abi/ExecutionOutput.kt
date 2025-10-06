package com.mazekine.nekoton.abi

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionOutput(
    val exitCode: Int,
    val output: Map<String, @Contextual Any>?
) {
    fun isSuccess(): Boolean = exitCode == 0
    override fun toString(): String = "ExecutionOutput(exitCode=$exitCode, hasOutput=${output != null})"
}