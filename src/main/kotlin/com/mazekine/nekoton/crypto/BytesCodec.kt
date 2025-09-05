package com.mazekine.nekoton.crypto

import java.util.Base64

/** Fast, allocation-lean helpers for hex/base64. */
object BytesCodec {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    /** Encode bytes as lowercase hex. */
    fun hexEncode(src: ByteArray): String {
        val out = CharArray(src.size * 2)
        var j = 0
        for (b in src) {
            val v = b.toInt() and 0xFF
            out[j++] = HEX_CHARS[v ushr 4]
            out[j++] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    /**
     * Decode hex string to bytes.
     * @param s string with or without 0x/0X prefix
     * @param expectedBytes optional safety check for exact byte length
     */
    fun hexDecode(s: String, expectedBytes: Int? = null): ByteArray {
        val str = s.removePrefix("0x").removePrefix("0X")
        require(str.length % 2 == 0) { "Hex length must be even" }
        if (expectedBytes != null) {
            require(str.length == expectedBytes * 2) {
                "Hex length must be ${expectedBytes * 2} chars (got ${str.length})"
            }
        }
        val out = ByteArray(str.length / 2)
        var i = 0
        var j = 0
        while (i < str.length) {
            val hi = hexNibble(str[i])
            val lo = hexNibble(str[i + 1])
            out[j++] = ((hi shl 4) or lo).toByte()
            i += 2
        }
        return out
    }

    /** Base64 encode/decode (JVM). */
    fun b64Encode(src: ByteArray): String = Base64.getEncoder().encodeToString(src)
    fun b64Decode(s: String): ByteArray = Base64.getDecoder().decode(s)

    /** Switch on enum. */
    fun encode(bytes: ByteArray, asEncoding: Encoding): String = when (asEncoding) {
        Encoding.HEX    -> hexEncode(bytes)
        Encoding.BASE64 -> b64Encode(bytes)
    }

    fun decode(s: String, fromEncoding: Encoding): ByteArray = when (fromEncoding) {
        Encoding.HEX    -> hexDecode(s)
        Encoding.BASE64 -> b64Decode(s)
    }

    /** Convert a hex char to its 0..15 value or throw. */
    private fun hexNibble(c: Char): Int = when (c) {
        in '0'..'9' -> c.code - '0'.code
        in 'a'..'f' -> c.code - 'a'.code + 10
        in 'A'..'F' -> c.code - 'A'.code + 10
        else -> throw IllegalArgumentException("Invalid hex character: '$c'")
    }
}
