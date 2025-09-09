package com.mazekine.nekoton.abi

import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.models.StateInit
import kotlinx.serialization.Serializable

/** Unsigned external message (ready to sign & send). */
@Serializable
data class UnsignedExternalMessage(
    val dst: Address,
    val stateInit: StateInit?,
    val body: UnsignedBody
) {
    fun expireAt(): Long = body.expireAt
    override fun toString(): String = "UnsignedExternalMessage(dst=$dst, expireAt=${expireAt()})"
}