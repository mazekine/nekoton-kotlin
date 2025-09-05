package com.mazekine.nekoton.crypto

import kotlin.test.*
import java.security.MessageDigest

class KeyPairTest {

    // --- helpers -----------------------------------------------------------------

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun toHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    // --- construction ------------------------------------------------------------

    @Test
    fun generate_producesValidLengths() {
        val kp = KeyPair.generate()
        assertNotNull(kp.publicKey)
        assertEquals(32, kp.publicKey.toBytes().size)
        assertEquals(32, kp.getSecretKey().size)
    }

    @Test
    fun fromSecretBytes_roundTripAndDefensiveCopy() {
        val secret = ByteArray(32) { it.toByte() }
        val kp = KeyPair.fromSecret(secret)

        // round-trip
        val out = kp.getSecretKey()
        assertContentEquals(secret, out)

        // mutate returned bytes -> original must not change
        out[0] = (out[0].toInt() xor 0xFF).toByte()
        assertContentEquals(secret, kp.getSecretKey(), "getSecretKey() must return a defensive copy")
    }

    @Test
    fun fromSecretHex_roundTrip() {
        val hex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        val kp = KeyPair.fromSecretHex(hex)
        assertEquals(hex, toHex(kp.getSecretKey()))
    }

    @Test
    fun fromSecretHex_invalidInputs() {
        assertFailsWith<IllegalArgumentException> { KeyPair.fromSecretHex("invalid") }
        assertFailsWith<IllegalArgumentException> { KeyPair.fromSecretHex("0123456789abcdef") } // too short
    }

    // --- signing / verification --------------------------------------------------

    @Test
    fun signRaw_verify_okAndRejectsWrongData() {
        val kp = KeyPair.generate()
        val data = "Hello, Nekoton!".toByteArray()
        val sig = kp.signRaw(data)

        assertTrue(kp.publicKey.verifySignature(data, sig))
        assertFalse(kp.publicKey.verifySignature("Wrong data".toByteArray(), sig))
    }

    @Test
    fun sign_verify_usesHashedData() {
        val kp = KeyPair.generate()
        val data = "Hello, Nekoton!".toByteArray()
        val sig = kp.sign(data)

        // `sign` hashes input; verify against the hash:
        assertTrue(kp.publicKey.verifySignature(sha256(data), sig))

        // raw data (unhashed) should fail:
        assertFalse(kp.publicKey.verifySignature(data, sig))
        // convenience wrapper on KeyPair also verifies raw input (no hashing), so it should fail too:
        assertFalse(kp.verifySignature(data, sig))
    }

    @Test
    fun signRaw_withSignatureId_verify_okAndRejectsWrongId() {
        val kp = KeyPair.generate()
        val data = "id-test".toByteArray()
        val sid = 12345
        val sig = kp.signRaw(data, sid)

        assertTrue(kp.publicKey.verifySignature(data, sig, sid))
        assertFalse(kp.publicKey.verifySignature(data, sig, 54321))
        assertFalse(kp.publicKey.verifySignature("other".toByteArray(), sig, sid))
    }

    @Test
    fun sign_withSignatureId_verifyAgainstHashedPlusId() {
        val kp = KeyPair.generate()
        val data = "id-hash-test".toByteArray()
        val sid = 42
        val sig = kp.sign(data, sid) // sign( SHA256(data) || sid )

        // Verify with the hashed data + same signatureId:
        assertTrue(kp.publicKey.verifySignature(sha256(data), sig, sid))

        // Wrong id or wrong data must fail:
        assertFalse(kp.publicKey.verifySignature(sha256(data), sig, 41))
        assertFalse(kp.publicKey.verifySignature(data, sig, sid)) // not hashed
    }

    // --- equality / string -------------------------------------------------------

    @Test
    fun equality_hashCode_basedOnPublicKey() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val a = KeyPair.fromSecret(secret)
        val b = KeyPair.fromSecret(secret)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun toString_containsPublicKeyHex() {
        val kp = KeyPair.generate()
        val s = kp.toString()
        assertTrue(s.startsWith("KeyPair(publicKey="))
        assertTrue(s.contains(kp.publicKey.toHex()))
    }
}
