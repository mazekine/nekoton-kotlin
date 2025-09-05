package com.mazekine.nekoton.crypto

import kotlin.test.*
import java.security.MessageDigest
import java.util.Base64
import com.ionspin.kotlin.bignum.integer.BigInteger

class PublicKeyTest {

    // --- helpers -----------------------------------------------------------------

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun hex(bytes: ByteArray): String =
        BytesCodec.hexEncode(bytes)

    // --- construction ------------------------------------------------------------

    @Test
    fun generate_yieldsValidPublicKey() {
        val kp = KeyPair.generate()
        val pk = kp.publicKey
        assertEquals(32, pk.toBytes().size)
        assertEquals(64, pk.toHex().length)
    }

    @Test
    fun fromString_hex_roundTrip() {
        val kp = KeyPair.generate()
        val hexKey = kp.publicKey.toHex()

        val parsed = PublicKey.fromString(hexKey, Encoding.HEX)
        assertEquals(hexKey, parsed.toHex())
        assertContentEquals(kp.publicKey.toBytes(), parsed.toBytes())
    }

    @Test
    fun fromString_base64_roundTrip() {
        val kp = KeyPair.generate()
        val b64 = Base64.getEncoder().encodeToString(kp.publicKey.toBytes())

        val parsed = PublicKey.fromString(b64, Encoding.BASE64)
        assertContentEquals(kp.publicKey.toBytes(), parsed.toBytes())
        assertEquals(b64, parsed.encode(Encoding.BASE64))
    }

    @Test
    fun fromBytes_defensiveCopyOfInput() {
        val original = ByteArray(32) { it.toByte() }
        val snapshot = original.copyOf()
        val pk = PublicKey.fromBytes(original)

        // mutate the source array; PublicKey must not change
        original[0] = (original[0].toInt() xor 0xFF).toByte()
        assertContentEquals(snapshot, pk.toBytes())
    }

    @Test
    fun toBytes_returnsDefensiveCopy() {
        val pk = PublicKey.fromBytes(ByteArray(32) { it.toByte() })
        val a = pk.toBytes()
        a[0] = (a[0].toInt() xor 0xFF).toByte()
        val b = pk.toBytes()
        assertNotEquals(a[0], b[0], "toBytes() must return a new copy each time")
    }

    @Test
    fun bigInteger_constructor_paddingAndTruncation() {
        // small value -> left-padded to 32 bytes
        val small = BigInteger.fromInt(1)
        val pkSmall = PublicKey(small)
        val bytesSmall = pkSmall.toBytes()
        assertEquals(32, bytesSmall.size)
        assertTrue(bytesSmall.dropLast(1).all { it == 0.toByte() })
        assertEquals(1.toByte(), bytesSmall.last())

        // large value (more than 32 bytes) -> truncated to last 32 bytes
        val largeBytes = ByteArray(40) { (it + 1).toByte() } // 0x01..0x28
        val large = BigInteger.fromByteArray(largeBytes, sign = com.ionspin.kotlin.bignum.integer.Sign.POSITIVE)
        val pkLarge = PublicKey(large)
        val bytesLarge = pkLarge.toBytes()
        assertEquals(32, bytesLarge.size)
        // expect last 32 of the source: 0x09..0x28
        assertContentEquals(largeBytes.copyOfRange(8, 40), bytesLarge)
    }

    // --- encoding API ------------------------------------------------------------

    @Test
    fun encode_enumMatchesHelpers() {
        val kp = KeyPair.generate()
        val pk = kp.publicKey
        assertEquals(pk.toHex(), pk.encode(Encoding.HEX))

        val b64 = Base64.getEncoder().encodeToString(pk.toBytes())
        assertEquals(b64, pk.encode(Encoding.BASE64))
    }

    // --- invalid inputs ----------------------------------------------------------

    @Test
    fun invalidSizes_throw() {
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { PublicKey(ByteArray(33)) }
        assertFailsWith<IllegalArgumentException> { PublicKey.fromBytes(ByteArray(0)) }
    }

    @Test
    fun invalidHexAndBase64_throw() {
        // bad hex chars
        assertFailsWith<IllegalArgumentException> {
            PublicKey.fromString("zz".repeat(32), Encoding.HEX)
        }
        // wrong hex length
        assertFailsWith<IllegalArgumentException> {
            PublicKey.fromString("ab".repeat(31), Encoding.HEX)
        }
        // bad base64
        assertFailsWith<IllegalArgumentException> {
            PublicKey.fromString("*not base64*", Encoding.BASE64)
        }
    }

    // --- equality / hash / string -----------------------------------------------

    @Test
    fun equalityAndHashCode_byContent() {
        val bytes = ByteArray(32) { (it * 7).toByte() }
        val a = PublicKey.fromBytes(bytes)
        val b = PublicKey.fromBytes(bytes)
        val c = PublicKey.fromBytes(bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun toString_isHex() {
        val pk = KeyPair.generate().publicKey
        assertEquals(pk.toHex(), pk.toString())
    }

    // --- verifySignature behavior ------------------------------------------------

    @Test
    fun verifySignature_signRaw_okAndRejectsWrongDataAndId() {
        val kp = KeyPair.generate()
        val pk = kp.publicKey
        val data = "raw-test".toByteArray()

        val sigNoId = kp.signRaw(data)
        assertTrue(pk.verifySignature(data, sigNoId))
        assertFalse(pk.verifySignature("other".toByteArray(), sigNoId))

        val sid = 2025
        val sigWithId = kp.signRaw(data, sid)
        assertTrue(pk.verifySignature(data, sigWithId, sid))
        assertFalse(pk.verifySignature(data, sigWithId, sid + 1))
    }

    @Test
    fun verifySignature_sign_requiresHashedData() {
        val kp = KeyPair.generate()
        val pk = kp.publicKey
        val data = "hash-test".toByteArray()
        val sid = 77

        val sig = kp.sign(data, sid)
        // Must verify against SHA-256(data) with same signatureId
        assertTrue(pk.verifySignature(sha256(data), sig, sid))

        // Wrong inputs must fail
        assertFalse(pk.verifySignature(data, sig, sid))          // unhashed
        assertFalse(pk.verifySignature(sha256(data), sig, sid+1)) // wrong signatureId
    }
}
