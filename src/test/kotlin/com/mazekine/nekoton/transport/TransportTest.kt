package com.mazekine.nekoton.transport

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.TestConfig
import com.mazekine.nekoton.models.Address
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class TransportTest {
    
    @Test
    fun testTransportInterface() {
        // Test that Transport interface exists and has expected methods
        // Since Transport is an interface, we can't instantiate it directly
        // This test verifies the interface structure
        assertTrue(Transport::class.java.isInterface)
    }
    
    @Test
    fun testTransportConfig() {
        // Test Tycho testnet configuration constants
        assertEquals("https://rpc-testnet.tychoprotocol.com/", TestConfig.TYCHO_JRPC_URL)
        assertEquals("https://rpc-testnet.tychoprotocol.com/proto", TestConfig.TYCHO_PROTO_URL)
        assertEquals("TYCHO", TestConfig.TYCHO_CURRENCY_SYMBOL)
        assertEquals(9, TestConfig.TYCHO_DECIMALS)
        assertEquals("https://testnet.tychoprotocol.com", TestConfig.TYCHO_EXPLORER_URL)
        assertTrue(TestConfig.TYCHO_TOKEN_LIST_URL.contains("tychotestnet"))
    }
    
    @Test
    fun testTransportException() {
        val exception = TransportException(
            TransportException.NETWORK_ERROR,
            "Test network error"
        )
        
        assertEquals(TransportException.NETWORK_ERROR, exception.code)
        assertEquals("Test network error", exception.message)
    }
    
    @Test
    fun testTransactionId() {
        val hash = ByteArray(32) { it.toByte() }
        val txId = TransactionId(12345L, hash)
        
        assertEquals(12345L, txId.lt)
        assertTrue(txId.hash.contentEquals(hash))
        assertEquals(64, txId.hashHex().length)
    }
    
    @Test
    fun testBlockInfo() = runBlocking {
        val transport = ProtoTransport(TestConfig.TYCHO_PROTO_URL)
        assertEquals(Native.isInitialized(), true)
        assertEquals(transport.isConnected(), true)

        val blockId = "976bbd5a8c182b5c6ec0e60825dbed1d025bd2eac7fb4ad46285cc60d94986fb"
        //val blockInfo = transport.getLatestBlock()


        val rootHash = ByteArray(32) { 0x12 }
        val fileHash = ByteArray(32) { 0x34 }
        val blockInfo = BlockInfo(0, 1L, 100, rootHash, fileHash, 1234567890)
        
        assertEquals(0, blockInfo.workchain)
        assertEquals(1L, blockInfo.shard)
        assertEquals(100, blockInfo.seqno)
        assertEquals(64, blockInfo.rootHashHex().length)
        assertEquals(64, blockInfo.fileHashHex().length)
    }
}
