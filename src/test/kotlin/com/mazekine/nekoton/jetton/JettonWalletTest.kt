package com.mazekine.nekoton.jetton

import com.mazekine.nekoton.models.Address
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class JettonWalletTest {
    private val owner = "0:" + "a".repeat(64)
    private val root = "0:" + "b".repeat(64)
    private val dest = "0:" + "c".repeat(64)

    @Test
    fun parseWalletState() {
        val json = """{"balance":1000,"owner":"$owner","root":"$root"}"""
        val wallet = JettonWallet.parseState(json)
        assertEquals(1000L, wallet.balance)
        assertEquals(Address(owner), wallet.owner)
        assertEquals(Address(root), wallet.root)
    }

    @Test
    fun tokenTransferFlow() {
        val json = """{"balance":1000,"owner":"$owner","root":"$root"}"""
        val wallet = JettonWallet.parseState(json)
        val to = Address(dest)
        val payload = wallet.transfer(to, 50)
        assertEquals(8 + 32, payload.size)
        val amount = ByteBuffer.wrap(payload.copyOfRange(0, 8)).order(ByteOrder.LITTLE_ENDIAN).long
        assertEquals(50L, amount)
        val destBytes = payload.copyOfRange(8, 40)
        assertTrue(destBytes.contentEquals(to.address))
    }
}
