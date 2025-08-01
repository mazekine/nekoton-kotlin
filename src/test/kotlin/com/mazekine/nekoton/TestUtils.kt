package com.mazekine.nekoton

import com.mazekine.nekoton.crypto.KeyPair
import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.models.CellBuilder
import kotlin.random.Random

object TestUtils {
    
    fun generateRandomAddress(workchain: Int = 0): Address {
        val addressBytes = ByteArray(32) { Random.nextInt(256).toByte() }
        return Address(workchain, addressBytes)
    }
    
    fun generateRandomKeyPair(): KeyPair {
        return KeyPair.generate()
    }
    
    fun createTestCell(data: ByteArray): com.mazekine.nekoton.models.Cell {
        val builder = CellBuilder()
        builder.writeBytes(data)
        return builder.build()
    }
    
    fun createTestCellWithReferences(
        data: ByteArray,
        references: List<com.mazekine.nekoton.models.Cell>
    ): com.mazekine.nekoton.models.Cell {
        val builder = CellBuilder()
        builder.writeBytes(data)
        references.forEach { builder.writeRef(it) }
        return builder.build()
    }
    
    fun generateRandomBytes(size: Int): ByteArray {
        return ByteArray(size) { Random.nextInt(256).toByte() }
    }
    
    fun assertBytesEqual(expected: ByteArray, actual: ByteArray, message: String = "") {
        if (!expected.contentEquals(actual)) {
            val expectedHex = expected.joinToString("") { "%02x".format(it) }
            val actualHex = actual.joinToString("") { "%02x".format(it) }
            throw AssertionError("$message\nExpected: $expectedHex\nActual: $actualHex")
        }
    }
    
    fun isNetworkAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("ping", "-c", "1", "8.8.8.8")
                .redirectErrorStream(true)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
