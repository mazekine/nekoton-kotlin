package com.broxus.nekoton.models

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Represents a slice of a cell for reading data sequentially.
 * 
 * CellSlice provides methods to read various data types from a cell
 * in a sequential manner, keeping track of the current position.
 * 
 * @property cell The source cell
 * @property bitPosition Current bit position in the cell
 * @property refPosition Current reference position
 */
class CellSlice(
    private val cell: Cell,
    private var bitPosition: Int = 0,
    private var refPosition: Int = 0
) {
    /**
     * Gets the number of remaining bits that can be read.
     */
    val remainingBits: Int
        get() = cell.bits - bitPosition

    /**
     * Gets the number of remaining references that can be read.
     */
    val remainingRefs: Int
        get() = cell.references.size - refPosition

    /**
     * Checks if there are any remaining bits to read.
     */
    fun hasRemainingBits(): Boolean = remainingBits > 0

    /**
     * Checks if there are any remaining references to read.
     */
    fun hasRemainingRefs(): Boolean = remainingRefs > 0

    /**
     * Reads a single bit from the slice.
     * 
     * @return The bit value as Boolean
     * @throws IllegalStateException if no bits remaining
     */
    fun readBit(): Boolean {
        require(hasRemainingBits()) { "No remaining bits to read" }
        
        val byteIndex = bitPosition / 8
        val bitIndex = 7 - (bitPosition % 8)
        val bit = (cell.data[byteIndex].toInt() and (1 shl bitIndex)) != 0
        
        bitPosition++
        return bit
    }

    /**
     * Reads multiple bits from the slice.
     * 
     * @param count Number of bits to read
     * @return The bits as a byte array
     * @throws IllegalArgumentException if count is invalid
     * @throws IllegalStateException if not enough bits remaining
     */
    fun readBits(count: Int): ByteArray {
        require(count >= 0) { "Bit count cannot be negative" }
        require(count <= remainingBits) { "Not enough bits remaining" }
        
        if (count == 0) return ByteArray(0)
        
        val result = ByteArray((count + 7) / 8)
        
        for (i in 0 until count) {
            val bit = readBit()
            if (bit) {
                val byteIndex = i / 8
                val bitIndex = 7 - (i % 8)
                result[byteIndex] = (result[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        
        return result
    }

    /**
     * Reads an unsigned integer from the slice.
     * 
     * @param bits Number of bits to read (max 64)
     * @return The unsigned integer value
     * @throws IllegalArgumentException if bits count is invalid
     */
    fun readUint(bits: Int): BigInteger {
        require(bits in 1..256) { "Bits count must be between 1 and 256" }
        require(bits <= remainingBits) { "Not enough bits remaining" }
        
        var result = BigInteger.ZERO
        
        for (i in 0 until bits) {
            result = result.shl(1)
            if (readBit()) {
                result = result.or(BigInteger.ONE)
            }
        }
        
        return result
    }

    /**
     * Reads a signed integer from the slice.
     * 
     * @param bits Number of bits to read (max 64)
     * @return The signed integer value
     */
    fun readInt(bits: Int): BigInteger {
        require(bits in 1..256) { "Bits count must be between 1 and 256" }
        
        val value = readUint(bits)
        val signBit = BigInteger.ONE.shl(bits - 1)
        
        return if (value >= signBit) {
            value - BigInteger.ONE.shl(bits)
        } else {
            value
        }
    }

    /**
     * Reads a variable-length unsigned integer.
     * 
     * @param lengthBits Number of bits used to encode the length
     * @return The variable unsigned integer value
     */
    fun readVarUint(lengthBits: Int): BigInteger {
        val length = readUint(lengthBits).intValue()
        return if (length == 0) BigInteger.ZERO else readUint(length * 8)
    }

    /**
     * Reads a variable-length signed integer.
     * 
     * @param lengthBits Number of bits used to encode the length
     * @return The variable signed integer value
     */
    fun readVarInt(lengthBits: Int): BigInteger {
        val length = readUint(lengthBits).intValue()
        return if (length == 0) BigInteger.ZERO else readInt(length * 8)
    }

    /**
     * Reads bytes from the slice.
     * 
     * @param count Number of bytes to read
     * @return The bytes as a byte array
     */
    fun readBytes(count: Int): ByteArray {
        require(count >= 0) { "Byte count cannot be negative" }
        require(count * 8 <= remainingBits) { "Not enough bits remaining for $count bytes" }
        
        return readBits(count * 8)
    }

    /**
     * Reads an address from the slice.
     * 
     * @return The address
     * @throws IllegalStateException if address format is invalid
     */
    fun readAddress(): Address? {
        // Read address type (2 bits)
        val addressType = readUint(2).intValue()
        
        return when (addressType) {
            0 -> null // addr_none
            2 -> { // addr_std
                val anycast = readBit() // anycast flag
                val workchain = readInt(8).intValue()
                val address = readBytes(32)
                Address(workchain, address)
            }
            else -> throw IllegalStateException("Unsupported address type: $addressType")
        }
    }

    /**
     * Reads a reference to another cell.
     * 
     * @return The referenced cell
     * @throws IllegalStateException if no references remaining
     */
    fun readRef(): Cell {
        require(hasRemainingRefs()) { "No remaining references to read" }
        
        val ref = cell.references[refPosition]
        refPosition++
        return ref
    }

    /**
     * Reads remaining bits as bytes.
     * 
     * @return The remaining bits as a byte array
     */
    fun readRemainingBits(): ByteArray {
        return readBits(remainingBits)
    }

    /**
     * Skips the specified number of bits.
     * 
     * @param count Number of bits to skip
     */
    fun skipBits(count: Int) {
        require(count >= 0) { "Skip count cannot be negative" }
        require(count <= remainingBits) { "Cannot skip more bits than remaining" }
        
        bitPosition += count
    }

    /**
     * Skips the specified number of references.
     * 
     * @param count Number of references to skip
     */
    fun skipRefs(count: Int) {
        require(count >= 0) { "Skip count cannot be negative" }
        require(count <= remainingRefs) { "Cannot skip more references than remaining" }
        
        refPosition += count
    }

    /**
     * Creates a copy of this slice.
     * 
     * @return A new CellSlice instance with the same state
     */
    fun copy(): CellSlice {
        return CellSlice(cell, bitPosition, refPosition)
    }

    override fun toString(): String {
        return "CellSlice(remainingBits=$remainingBits, remainingRefs=$remainingRefs)"
    }
}
