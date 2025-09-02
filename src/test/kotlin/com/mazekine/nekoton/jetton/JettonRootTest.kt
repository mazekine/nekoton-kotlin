package com.mazekine.nekoton.jetton

import com.mazekine.nekoton.models.Address
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class JettonRootTest {
    private val root = Address("0:" + "f".repeat(64))
    private val owner = Address("0:" + "a".repeat(64))

    @Test
    fun walletAddressDerivation() {
        val rootContract = JettonRoot(root)
        val wallet = rootContract.walletAddress(owner)
        assertEquals(owner.workchain, wallet.workchain)
        assertTrue(wallet.address.contentEquals(owner.address))
    }
}
