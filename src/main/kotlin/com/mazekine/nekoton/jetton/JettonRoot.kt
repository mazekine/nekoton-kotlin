package com.mazekine.nekoton.jetton

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.models.Address

/**
 * Represents a jetton root contract helper.
 */
class JettonRoot(val address: Address) {
    /**
     * Computes jetton wallet address for the specified owner.
     */
    fun walletAddress(owner: Address): Address {
        val wallet = try {
            Native.getJettonWalletAddress(address.address, owner.address)
        } catch (_: UnsatisfiedLinkError) {
            owner.address
        }
        return Address(owner.workchain, wallet)
    }
}
