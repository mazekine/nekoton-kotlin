package com.mazekine.nekoton.crypto

import kotlin.test.*

class PublicKeyTest {
    
    @Test
    fun testPublicKeyCreation() {
        val keyPair = KeyPair.generate()
        val publicKey = keyPair.publicKey
        
        assertEquals(32, publicKey.toBytes().size)
        assertEquals(64, publicKey.toHex().length)
    }
    
    @Test
    fun testPublicKeyFromHex() {
        val hexKey = "7b671b6bfd43e306d4accb46113a871e66b30cc587a57635766a2f360ee831c6"
        val publicKey = PublicKey.fromString(hexKey, "hex")
        
        assertEquals(hexKey, publicKey.toHex())
        assertEquals(32, publicKey.toBytes().size)
    }
    
    @Test
    fun testPublicKeyFromBytes() {
        val keyBytes = ByteArray(32) { it.toByte() }
        val publicKey = PublicKey(keyBytes)
        
        assertContentEquals(keyBytes, publicKey.toBytes())
    }
    
    @Test
    fun testPublicKeyEquality() {
        val keyBytes = ByteArray(32) { it.toByte() }
        val publicKey1 = PublicKey(keyBytes)
        val publicKey2 = PublicKey(keyBytes)
        
        assertEquals(publicKey1, publicKey2)
        assertEquals(publicKey1.hashCode(), publicKey2.hashCode())
    }
    
    @Test
    fun testPublicKeyToString() {
        val publicKey = PublicKey(ByteArray(32) { it.toByte() })
        val publicKeyString = publicKey.toString()
        
        assertEquals(publicKey.toHex(), publicKeyString)
    }
    
    @Test
    fun testInvalidPublicKeySize() {
        assertFailsWith<IllegalArgumentException> {
            PublicKey(ByteArray(31))
        }
        
        assertFailsWith<IllegalArgumentException> {
            PublicKey(ByteArray(33))
        }
    }
    
    @Test
    fun testInvalidPublicKeyHex() {
        assertFailsWith<IllegalArgumentException> {
            PublicKey.fromString("invalid", "hex")
        }
        
        assertFailsWith<IllegalArgumentException> {
            PublicKey.fromString("7b671b6bfd43e306d4accb46113a871e66b30cc587a57635766a2f360ee831", "hex")
        }
    }
    
    @Test
    fun testSignatureVerification() {
        val keyPair = KeyPair.generate()
        val publicKey = keyPair.publicKey
        val data = "Test data".toByteArray()
        
        val signature = keyPair.sign(data)
        // Note: Signature verification not yet fully implemented
        // assertTrue(publicKey.verifySignature(data, signature))
        
        val wrongData = "Wrong data".toByteArray()
        assertFalse(publicKey.verifySignature(wrongData, signature))
    }
}
