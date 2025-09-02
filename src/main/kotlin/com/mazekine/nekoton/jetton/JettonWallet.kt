package com.mazekine.nekoton.jetton

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.models.Address
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents a jetton wallet with basic helper methods.
 */
class JettonWallet(val root: Address, val owner: Address, var balance: Long) {
    /**
     * Builds a transfer payload to send jetton tokens.
     */
    fun transfer(to: Address, amount: Long): ByteArray {
        return try {
            Native.buildJettonTransfer(owner.address, to.address, amount)
        } catch (_: UnsatisfiedLinkError) {
            val buffer = ByteBuffer.allocate(8 + to.address.size).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putLong(amount)
            buffer.put(to.address)
            buffer.array()
        }
    }

    companion object {
        /**
         * Parses wallet state JSON and constructs a [JettonWallet].
         */
        fun parseState(stateJson: String): JettonWallet {
            val normalized = try {
                Native.parseJettonWalletState(stateJson)
            } catch (_: UnsatisfiedLinkError) {
                stateJson
            }
            val state = Json.decodeFromString<JettonWalletState>(normalized)
            return JettonWallet(
                Address(state.root),
                Address(state.owner),
                state.balance
            )
        }
    }
}

@Serializable
data class JettonWalletState(
    val balance: Long,
    val owner: String,
    val root: String
)
