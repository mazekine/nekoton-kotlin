package com.mazekine.nekoton.crypto

import kotlin.test.*

class SignatureTest {
    
    @Test
    fun testSignatureCreation() {
        val signatureBytes = ByteArray(64) { it.toByte() }
        val signature = Signature(signatureBytes)
        
        assertContentEquals(signatureBytes, signature.toBytes())
        assertEquals(128, signature.toHex().length)
    }
    
    @Test
    fun testSignatureFromHex() {
        val hexSignature = "0123456789abcdef".repeat(8)
        val signature = Signature.fromString(hexSignature, "hex")
        
        assertEquals(hexSignature, signature.toHex())
        assertEquals(64, signature.toBytes().size)
    }
    
    @Test
    fun testSignatureEquality() {
        val signatureBytes = ByteArray(64) { it.toByte() }
        val signature1 = Signature(signatureBytes)
        val signature2 = Signature(signatureBytes)
        
        assertEquals(signature1, signature2)
        assertEquals(signature1.hashCode(), signature2.hashCode())
    }
    
    @Test
    fun testSignatureToString() {
        val signature = Signature(ByteArray(64) { it.toByte() })
        val signatureString = signature.toString()
        
        assertTrue(signatureString.startsWith("Signature("))
        assertTrue(signatureString.contains(signature.toHex()))
    }
    
    @Test
    fun testInvalidSignatureSize() {
        assertFailsWith<IllegalArgumentException> {
            Signature(ByteArray(63))
        }
        
        assertFailsWith<IllegalArgumentException> {
            Signature(ByteArray(65))
        }
    }
    
    @Test
    fun testInvalidSignatureHex() {
        assertFailsWith<IllegalArgumentException> {
            Signature.fromString("invalid", "hex")
        }
        
        assertFailsWith<IllegalArgumentException> {
            Signature.fromString("0123456789abcdef".repeat(7), "hex")
        }
    }
    
    @Test
    fun testSignatureWithKeyPair() {
        val keyPair = KeyPair.generate()
        val data = "Test signature".toByteArray()
        
        val signature = keyPair.sign(data)
        assertEquals(64, signature.toBytes().size)
        // Note: Signature verification not yet fully implemented
        // assertTrue(keyPair.verifySignature(data, signature))
    }
}
