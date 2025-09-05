package com.mazekine.nekoton.crypto

import kotlin.test.*
import java.security.MessageDigest
import java.util.Random

class SignaturePropertyTest {

    // deterministic RNG for reproducible runs
    private val rnd = Random(0xC0FFEE_F00DL)

    private fun randBytes(n: Int): ByteArray {
        val b = ByteArray(n)
        for (i in 0 until n) b[i] = rnd.nextInt(256).toByte()
        return b
    }

    private fun randData(): ByteArray {
        // cover empty .. medium payloads
        val len = rnd.nextInt(513) // 0..512
        return randBytes(len)
    }

    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    @Test
    fun signRaw_verify_roundtrip_fuzz() {
        repeat(200) {
            val secret = randBytes(32)
            val data   = randData()
            val kp     = KeyPair.fromSecret(secret)

            // flip a coin: with or without signatureId
            val useId  = rnd.nextBoolean()
            val sid    = if (useId) rnd.nextInt() else null

            val sig1 = if (sid == null) kp.signRaw(data) else kp.signRaw(data, sid)

            // roundtrip must verify
            assertTrue(kp.publicKey.verifySignature(data, sig1, sid))

            // signing must be deterministic for same inputs
            val sig2 = if (sid == null) kp.signRaw(data) else kp.signRaw(data, sid)
            assertContentEquals(sig1.toBytes(), sig2.toBytes())

            // wrong data must fail
            val other = data.copyOf().also { if (it.isNotEmpty()) it[0] = (it[0].toInt() xor 1).toByte() }
            assertFalse(kp.publicKey.verifySignature(other, sig1, sid))

            // wrong signatureId must fail (only when applicable)
            if (sid != null) {
                assertFalse(kp.publicKey.verifySignature(data, sig1, sid + 1))
            }

            // signature length is always 64
            assertEquals(64, sig1.toBytes().size)
        }
    }

    @Test
    fun sign_hashesInput_equivalentTo_signRaw_onSha256_fuzz() {
        repeat(120) {
            val secret = randBytes(32)
            val data   = randData()
            val kp     = KeyPair.fromSecret(secret)

            val useId  = rnd.nextBoolean()
            val sid    = if (useId) rnd.nextInt() else null

            // sign() hashes input then appends signatureId;
            // signRaw() on SHA256(data) with same id must match bit-for-bit.
            val sigA = if (sid == null) kp.sign(data) else kp.sign(data, sid)
            val sigB = if (sid == null) kp.signRaw(sha256(data)) else kp.signRaw(sha256(data), sid)

            assertContentEquals(sigA.toBytes(), sigB.toBytes())

            // verification must be against SHA256(data) for sign()
            assertTrue(kp.publicKey.verifySignature(sha256(data), sigA, sid))
            assertFalse(kp.publicKey.verifySignature(data, sigA, sid)) // unhashed must fail
        }
    }

    @Test
    fun differentIds_produceDifferentSignatures_andDifferentSecretsToo() {
        val secret = randBytes(32)
        val data   = "fixed-payload-for-id-test".toByteArray()
        val kp     = KeyPair.fromSecret(secret)

        val s1 = kp.signRaw(data, 1)
        val s2 = kp.signRaw(data, 2)
        assertFalse(s1 == s2, "Different signatureId should change the signature")

        val s3 = kp.sign(data, 1)
        val s4 = kp.sign(data, 2)
        assertFalse(s3 == s4, "Different signatureId should change the signature (hashed path)")

        // different secret → different signature (overwhelmingly likely)
        val kp2 = KeyPair.fromSecret(randBytes(32))
        val t1 = kp2.signRaw(data, 1)
        assertFalse(s1 == t1, "Different secret should change the signature")
    }

    @Test
    fun encoding_roundtrip_hex_base64_randomSignatures() {
        repeat(40) {
            val secret = randBytes(32)
            val kp = KeyPair.fromSecret(secret)
            val sig = kp.signRaw(randData(), if (rnd.nextBoolean()) rnd.nextInt() else null)

            val hex = sig.encode(Encoding.HEX)
            val b64 = sig.encode(Encoding.BASE64)

            val fromHex = Signature.fromString(hex, Encoding.HEX)
            val fromB64 = Signature.fromString(b64, Encoding.BASE64)

            assertContentEquals(sig.toBytes(), fromHex.toBytes())
            assertContentEquals(sig.toBytes(), fromB64.toBytes())
        }
    }
}
