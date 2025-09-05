package com.mazekine.nekoton.crypto

import kotlin.test.*
import java.util.Base64
import java.security.MessageDigest

class SignatureTest {

    // --- helpers -----------------------------------------------------------------

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    // --- construction / basics ---------------------------------------------------

    @Test
    fun construct_fromBytes_validLength() {
        val raw = ByteArray(Signature.SIGNATURE_SIZE) { it.toByte() }
        val sig = Signature.fromBytes(raw)
        assertEquals(Signature.SIGNATURE_SIZE, sig.toBytes().size)
        assertEquals(128, sig.toHex().length)
    }

    @Test
    fun construct_fromHex_roundTrip() {
        val hex = "0123456789abcdef".repeat(8) // 128 hex chars = 64 bytes
        val sig = Signature.fromString(hex, Encoding.HEX)
        assertEquals(hex, sig.toHex())
        assertEquals(Signature.SIGNATURE_SIZE, sig.toBytes().size)
    }

    @Test
    fun construct_fromBase64_roundTrip() {
        val bytes = ByteArray(Signature.SIGNATURE_SIZE) { (it * 3).toByte() }
        val b64 = Base64.getEncoder().encodeToString(bytes)
        val sig = Signature.fromString(b64, Encoding.BASE64)
        assertContentEquals(bytes, sig.toBytes())
        assertEquals(b64, sig.encode(Encoding.BASE64))
    }

    // --- defensive copies --------------------------------------------------------

    @Test
    fun fromBytes_defensiveCopyOfInput() {
        val src = ByteArray(Signature.SIGNATURE_SIZE) { it.toByte() }
        val snap = src.copyOf()
        val sig = Signature.fromBytes(src)

        // mutate input after construction; Signature must not change
        src[0] = (src[0].toInt() xor 0xFF).toByte()
        assertContentEquals(snap, sig.toBytes())
    }

    @Test
    fun toBytes_returnsFreshCopy() {
        val sig = Signature.fromBytes(ByteArray(Signature.SIGNATURE_SIZE) { it.toByte() })
        val a = sig.toBytes()
        a[0] = (a[0].toInt() xor 1).toByte()
        val b = sig.toBytes()
        assertNotEquals(a[0], b[0], "toBytes() must return a defensive copy each time")
    }

    // --- encoding API ------------------------------------------------------------

    @Test
    fun encode_enumMatchesExpected() {
        val bytes = ByteArray(Signature.SIGNATURE_SIZE) { (it * 7).toByte() }
        val sig = Signature.fromBytes(bytes)
        assertEquals(BytesCodec.hexEncode(bytes), sig.encode(Encoding.HEX))
        assertEquals(Base64.getEncoder().encodeToString(bytes), sig.encode(Encoding.BASE64))
    }

    // --- invalid inputs ----------------------------------------------------------

    @Test
    fun invalidSizes_throw() {
        assertFailsWith<IllegalArgumentException> { Signature(ByteArray(63)) }
        assertFailsWith<IllegalArgumentException> { Signature(ByteArray(65)) }
    }

    @Test
    fun invalidHex_throw() {
        // bad characters
        assertFailsWith<IllegalArgumentException> {
            Signature.fromString("zz".repeat(64), Encoding.HEX)
        }
        // wrong length
        assertFailsWith<IllegalArgumentException> {
            Signature.fromString("ab".repeat(63), Encoding.HEX) // 62 bytes, not 64
        }
    }

    @Test
    fun invalidBase64_throw() {
        assertFailsWith<IllegalArgumentException> {
            Signature.fromString("*not base64*", Encoding.BASE64)
        }
    }

    // --- equality / hash / string -----------------------------------------------

    @Test
    fun equalityAndHash_byContent() {
        val bytes = ByteArray(Signature.SIGNATURE_SIZE) { (it * 5).toByte() }
        val a = Signature.fromBytes(bytes)
        val b = Signature.fromBytes(bytes)
        val c = Signature.fromBytes(bytes.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun toString_containsHex() {
        val sig = Signature.fromBytes(ByteArray(Signature.SIGNATURE_SIZE) { it.toByte() })
        val s = sig.toString()
        assertTrue(s.startsWith("Signature("))
        assertTrue(s.contains(sig.toHex()))
    }

    // --- integration with KeyPair/PublicKey --------------------------------------

    @Test
    fun signRaw_verify_ok_andRejectsWrongDataOrId() {
        val kp = KeyPair.generate()
        val data = "raw-verify".toByteArray()

        val sigNoId = kp.signRaw(data)
        assertTrue(kp.publicKey.verifySignature(data, sigNoId))
        assertFalse(kp.publicKey.verifySignature("oops".toByteArray(), sigNoId))

        val sid = 2024
        val sigWithId = kp.signRaw(data, sid)
        assertTrue(kp.publicKey.verifySignature(data, sigWithId, sid))
        assertFalse(kp.publicKey.verifySignature(data, sigWithId, sid + 1))
    }

    @Test
    fun sign_hashesInput_requiresHashedDataForVerify() {
        val kp = KeyPair.generate()
        val data = "hash-verify".toByteArray()
        val sid = 99

        val sig = kp.sign(data, sid)
        assertTrue(kp.publicKey.verifySignature(sha256(data), sig, sid))

        // Wrong inputs must fail
        assertFalse(kp.publicKey.verifySignature(data, sig, sid))         // unhashed
        assertFalse(kp.publicKey.verifySignature(sha256(data), sig, 98))  // wrong id
    }
}
