package com.broxus.nekoton.models

import com.ionspin.kotlin.bignum.integer.BigInteger

/**
 * Builder class for constructing TON/Everscale cells.
 * 
 * CellBuilder provides methods to write various data types to a cell
 * in a sequential manner, automatically managing bit alignment and references.
 * 
 * @property data The cell data being built
 * @property bits Current number of bits written
 * @property references List of cell references
 */
class CellBuilder {
    private val data = mutableListOf<Byte>()
    private var bits = 0
    private val references = mutableListOf<Cell>()
    private var currentByte = 0
    private var currentBitInByte = 7

    /**
     * Gets the number of bits written so far.
     */
    val bitsWritten: Int get() = bits

    /**
     * Gets the number of references added so far.
     */
    val referencesCount: Int get() = references.size

    /**
     * Gets the remaining space for bits.
     */
    val remainingBits: Int get() = Cell.MAX_BITS - bits

    /**
     * Gets the remaining space for references.
     */
    val remainingRefs: Int get() = Cell.MAX_REFERENCES - references.size

    /**
     * Writes a single bit to the cell.
     * 
     * @param bit The bit value to write
     * @return This builder for chaining
     * @throws IllegalStateException if no space remaining
     */
    fun writeBit(bit: Boolean): CellBuilder {
        require(remainingBits > 0) { "No space remaining for bits" }
        
        if (bit) {
            currentByte = currentByte or (1 shl currentBitInByte)
        }
        
        currentBitInByte--
        bits++
        
        if (currentBitInByte < 0) {
            data.add(currentByte.toByte())
            currentByte = 0
            currentBitInByte = 7
        }
        
        return this
    }

    /**
     * Writes multiple bits to the cell.
     * 
     * @param bits The bits to write as a byte array
     * @param bitCount Number of bits to write from the array
     * @return This builder for chaining
     */
    fun writeBits(bits: ByteArray, bitCount: Int = bits.size * 8): CellBuilder {
        require(bitCount >= 0) { "Bit count cannot be negative" }
        require(bitCount <= bits.size * 8) { "Bit count exceeds array size" }
        require(bitCount <= remainingBits) { "Not enough space remaining" }
        
        for (i in 0 until bitCount) {
            val byteIndex = i / 8
            val bitIndex = 7 - (i % 8)
            val bit = (bits[byteIndex].toInt() and (1 shl bitIndex)) != 0
            writeBit(bit)
        }
        
        return this
    }

    /**
     * Writes an unsigned integer to the cell.
     * 
     * @param value The unsigned integer value
     * @param bits Number of bits to use for encoding
     * @return This builder for chaining
     */
    fun writeUint(value: BigInteger, bits: Int): CellBuilder {
        require(bits in 1..256) { "Bits count must be between 1 and 256" }
        require(value >= BigInteger.ZERO) { "Value must be non-negative" }
        require(value < BigInteger.ONE.shl(bits)) { "Value too large for $bits bits" }
        require(bits <= remainingBits) { "Not enough space remaining" }
        
        for (i in bits - 1 downTo 0) {
            val bit = value.and(BigInteger.ONE.shl(i)) != BigInteger.ZERO
            writeBit(bit)
        }
        
        return this
    }

    /**
     * Writes an unsigned integer to the cell (Long version).
     * 
     * @param value The unsigned integer value
     * @param bits Number of bits to use for encoding
     * @return This builder for chaining
     */
    fun writeUint(value: Long, bits: Int): CellBuilder {
        return writeUint(BigInteger.fromLong(value), bits)
    }

    /**
     * Writes a signed integer to the cell.
     * 
     * @param value The signed integer value
     * @param bits Number of bits to use for encoding
     * @return This builder for chaining
     */
    fun writeInt(value: BigInteger, bits: Int): CellBuilder {
        require(bits in 1..256) { "Bits count must be between 1 and 256" }
        
        val minValue = -BigInteger.ONE.shl(bits - 1)
        val maxValue = BigInteger.ONE.shl(bits - 1) - BigInteger.ONE
        require(value >= minValue && value <= maxValue) { "Value out of range for $bits bits" }
        
        val unsignedValue = if (value < BigInteger.ZERO) {
            value + BigInteger.ONE.shl(bits)
        } else {
            value
        }
        
        return writeUint(unsignedValue, bits)
    }

    /**
     * Writes a signed integer to the cell (Long version).
     * 
     * @param value The signed integer value
     * @param bits Number of bits to use for encoding
     * @return This builder for chaining
     */
    fun writeInt(value: Long, bits: Int): CellBuilder {
        return writeInt(BigInteger.fromLong(value), bits)
    }

    /**
     * Writes a variable-length unsigned integer.
     * 
     * @param value The unsigned integer value
     * @param lengthBits Number of bits to use for encoding the length
     * @return This builder for chaining
     */
    fun writeVarUint(value: BigInteger, lengthBits: Int): CellBuilder {
        if (value == BigInteger.ZERO) {
            return writeUint(BigInteger.ZERO, lengthBits)
        }
        
        val byteLength = (value.bitLength() + 7) / 8
        writeUint(BigInteger.fromInt(byteLength), lengthBits)
        writeUint(value, byteLength * 8)
        
        return this
    }

    /**
     * Writes a variable-length signed integer.
     * 
     * @param value The signed integer value
     * @param lengthBits Number of bits to use for encoding the length
     * @return This builder for chaining
     */
    fun writeVarInt(value: BigInteger, lengthBits: Int): CellBuilder {
        if (value == BigInteger.ZERO) {
            return writeUint(BigInteger.ZERO, lengthBits)
        }
        
        val byteLength = (value.bitLength() + 8) / 8 // +1 for sign bit
        writeUint(BigInteger.fromInt(byteLength), lengthBits)
        writeInt(value, byteLength * 8)
        
        return this
    }

    /**
     * Writes bytes to the cell.
     * 
     * @param bytes The bytes to write
     * @return This builder for chaining
     */
    fun writeBytes(bytes: ByteArray): CellBuilder {
        return writeBits(bytes, bytes.size * 8)
    }

    /**
     * Writes an address to the cell.
     * 
     * @param address The address to write (null for addr_none)
     * @return This builder for chaining
     */
    fun writeAddress(address: Address?): CellBuilder {
        if (address == null) {
            // addr_none
            return writeUint(0, 2)
        }
        
        // addr_std
        writeUint(2, 2) // address type
        writeBit(false) // no anycast
        writeInt(address.workchain.toLong(), 8)
        writeBytes(address.address)
        
        return this
    }

    /**
     * Writes tokens (Grams) to the cell.
     * 
     * @param tokens The token amount to write
     * @return This builder for chaining
     */
    fun writeTokens(tokens: Tokens): CellBuilder {
        return writeVarUint(tokens.nanoTokens, 4)
    }

    /**
     * Adds a reference to another cell.
     * 
     * @param cell The cell to reference
     * @return This builder for chaining
     * @throws IllegalStateException if no space remaining for references
     */
    fun writeRef(cell: Cell): CellBuilder {
        require(remainingRefs > 0) { "No space remaining for references" }
        
        references.add(cell)
        return this
    }

    /**
     * Writes a string to the cell.
     * 
     * @param string The string to write (UTF-8 encoded)
     * @return This builder for chaining
     */
    fun writeString(string: String): CellBuilder {
        val bytes = string.toByteArray(Charsets.UTF_8)
        return writeBytes(bytes)
    }

    /**
     * Builds the final cell.
     * 
     * @return The constructed cell
     */
    fun build(): Cell {
        // Finalize the current byte if there are remaining bits
        val finalData = if (currentBitInByte < 7) {
            data + currentByte.toByte()
        } else {
            data
        }.toByteArray()
        
        return Cell(finalData, bits, references.toList())
    }

    /**
     * Clears the builder state.
     * 
     * @return This builder for chaining
     */
    fun clear(): CellBuilder {
        data.clear()
        bits = 0
        references.clear()
        currentByte = 0
        currentBitInByte = 7
        return this
    }

    override fun toString(): String {
        return "CellBuilder(bits=$bits, refs=${references.size})"
    }

    companion object {
        /**
         * Creates a new CellBuilder instance.
         * 
         * @return New CellBuilder
         */
        fun create(): CellBuilder = CellBuilder()
    }
}
