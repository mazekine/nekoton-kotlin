package com.mazekine.nekoton.abi

import com.mazekine.nekoton.models.Cell
import kotlinx.serialization.Serializable

/** Unsigned message body (payload + metadata). */
@Serializable
data class UnsignedBody(
    val abiVersion: AbiVersion,
    val payload: Cell,    // (FunctionID + Enc(args)); headers/signature are NOT embedded here
    val hash: ByteArray,
    val expireAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UnsignedBody
        return abiVersion == other.abiVersion &&
                payload == other.payload &&
                hash.contentEquals(other.hash) &&
                expireAt == other.expireAt
    }

    override fun hashCode(): Int {
        var result = abiVersion.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + expireAt.hashCode()
        return result
    }

    override fun toString(): String =
        "UnsignedBody(expireAt=$expireAt, hashHex=${hash.joinToString("") { "%02x".format(it) }})"
}