package com.mazekine.nekoton.models

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.zip.CRC32C
import kotlinx.serialization.Serializable

/**
 * Represents a TON/Everscale blockchain cell.
 * 
 * A cell is the basic data structure in TON blockchain that can contain
 * up to 1023 bits of data and up to 4 references to other cells.
 * This creates a directed acyclic graph (DAG) structure.
 * 
 * @property data The cell data as a byte array
 * @property bits The number of bits used in the data
 * @property references List of references to other cells
 */
@Serializable
data class Cell(
    val data: ByteArray,
    val bits: Int,
    val references: List<Cell> = emptyList()
) {
    init {
        require(bits >= 0) { "Bits count cannot be negative" }
        require(bits <= MAX_BITS) { "Cell cannot contain more than $MAX_BITS bits" }
        require(references.size <= MAX_REFERENCES) { "Cell cannot have more than $MAX_REFERENCES references" }
        require(bits <= data.size * 8) { "Bits count cannot exceed data size in bits" }
    }

    /**
     * Creates an empty cell.
     */
    constructor() : this(ByteArray(0), 0, emptyList())

    /**
     * Creates a cell from a hex string.
     * 
     * @param hexData The cell data as a hex string
     * @param bits The number of bits used (if not specified, uses all bits in hex data)
     */
    constructor(hexData: String, bits: Int? = null) : this(
        hexData.removePrefix("0x").let { hex ->
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        },
        bits ?: (hexData.removePrefix("0x").length * 4),
        emptyList()
    )

    /**
     * Gets the hash of this cell.
     * 
     * @return The cell hash as a byte array
     */
    fun hash(): ByteArray {
        val hashCache = IdentityHashMap<Cell, ByteArray>()
        val depthCache = IdentityHashMap<Cell, Int>()
        return computeHash(this, hashCache, depthCache)
    }

    /**
     * Converts the cell data to a hex string.
     * 
     * @return Hex representation of the cell data
     */
    fun toHex(): String {
        return data.joinToString("") { "%02x".format(it) }
    }

    /**
     * Creates a cell slice for reading data from this cell.
     * 
     * @return CellSlice instance for reading
     */
    fun beginParse(): CellSlice {
        return CellSlice(this)
    }

    /**
     * Serializes the cell to BOC (Bag of Cells) format.
     * 
     * @return BOC representation as byte array
     */
    fun toBoc(): ByteArray {
        val hasIndex = true
        val hasCrc32 = true
        val hasCacheBits = false
        val flags = 0

        val cells = ArrayList<Cell>()
        val indices = IdentityHashMap<Cell, Int>()

        fun traverse(cell: Cell) {
            if (indices.containsKey(cell)) return
            indices[cell] = cells.size
            cells.add(cell)
            cell.references.forEach { traverse(it) }
        }

        traverse(this)

        val sizeBytes = maxOf(1, byteSizeForNumber(cells.size))
        val cellPayloads = cells.map { it.serializeForBoc(indices, sizeBytes) }
        val totalCellSize = cellPayloads.sumOf { it.size }
        val offsetBytes = maxOf(1, byteSizeForNumber(totalCellSize))

        val output = ByteArrayOutputStream()
        output.write(REACH_BOC_MAGIC)

        val flagsByte =
            ((if (hasIndex) 1 else 0) shl 7) or
                ((if (hasCrc32) 1 else 0) shl 6) or
                ((if (hasCacheBits) 1 else 0) shl 5) or
                ((flags and 0x03) shl 3) or
                (sizeBytes and 0x07)
        output.write(flagsByte)
        output.write(offsetBytes)
        output.write(encodeUint(cells.size, sizeBytes))
        output.write(encodeUint(1, sizeBytes)) // root count
        output.write(encodeUint(0, sizeBytes)) // absent
        output.write(encodeUint(totalCellSize, offsetBytes))
        output.write(encodeUint(indices[this] ?: 0, sizeBytes))

        if (hasIndex) {
            var currentOffset = 0
            cellPayloads.forEach { payload ->
                output.write(encodeUint(currentOffset, offsetBytes))
                currentOffset += payload.size
            }
        }

        cellPayloads.forEach { payload -> output.write(payload) }

        val base = output.toByteArray()
        if (!hasCrc32) {
            return base
        }

        val crc32 = CRC32C()
        crc32.update(base)
        val crcBytes = encodeUint(crc32.value.toInt(), 4)
        val finalOutput = ByteArrayOutputStream(base.size + crcBytes.size)
        finalOutput.write(base)
        finalOutput.write(crcBytes)
        return finalOutput.toByteArray()
    }

    /**
     * Gets the depth of this cell (maximum depth of reference tree).
     * 
     * @return The cell depth
     */
    fun depth(): Int {
        if (references.isEmpty()) return 0
        return 1 + references.maxOf { it.depth() }
    }

    /**
     * Checks if this cell equals another cell.
     * Custom equals implementation to handle ByteArray comparison.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Cell

        if (!data.contentEquals(other.data)) return false
        if (bits != other.bits) return false
        if (references != other.references) return false

        return true
    }

    /**
     * Generates hash code for the cell.
     * Custom hashCode implementation to handle ByteArray.
     */
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + bits
        result = 31 * result + references.hashCode()
        return result
    }

    override fun toString(): String {
        return "Cell(bits=$bits, refs=${references.size}, data=${toHex()})"
    }

    companion object {
        /**
         * Maximum number of bits in a cell.
         */
        const val MAX_BITS = 1023

        /**
         * Maximum number of references in a cell.
         */
        const val MAX_REFERENCES = 4

        /**
         * Creates a cell from BOC (Bag of Cells) data.
         * 
         * @param boc The BOC data as byte array
         * @return Cell instance
         */
        fun fromBoc(boc: ByteArray): Cell {
            val parser = BocParser(boc)
            val header = parser.parseHeader()

            require(header.rootsNum >= 1) { "BOC must contain at least one root" }
            require(header.rootList.size == header.rootsNum) { "Invalid roots list" }

            val cellsData = header.cellsData
            var offset = 0
            val parsedCells = ArrayList<ParsedCell>(header.cellsNum)
            repeat(header.cellsNum) {
                val cell = parseCellData(cellsData, offset, header.sizeBytes)
                offset = cell.offset
                parsedCells.add(cell)
            }

            require(offset == cellsData.size) { "Cell data size mismatch" }

            val constructed = arrayOfNulls<Cell>(header.cellsNum)
            for (index in header.cellsNum - 1 downTo 0) {
                val parsed = parsedCells[index]
                val refs = parsed.refs.map { refIndex ->
                    require(refIndex in constructed.indices) { "Reference index out of range" }
                    constructed[refIndex] ?: error("Reference cell not yet constructed")
                }
                constructed[index] = Cell(parsed.data, parsed.bits, refs)
            }

            val rootIndex = header.rootList.first()
            require(rootIndex in constructed.indices) { "Root index out of range" }
            return constructed[rootIndex] ?: error("Root cell not constructed")
        }

        /**
         * Creates a cell from a base64-encoded BOC string.
         * 
         * @param base64Boc The BOC data as base64 string
         * @return Cell instance
         */
        fun fromBase64(base64Boc: String): Cell {
            val boc = java.util.Base64.getDecoder().decode(base64Boc)
            return fromBoc(boc)
        }

        /**
         * Creates an empty cell.
         * 
         * @return Empty cell instance
         */
        fun empty(): Cell = Cell()

        private data class ParsedCell(
            val data: ByteArray,
            val bits: Int,
            val refs: List<Int>,
            val offset: Int
        )

        private data class BocHeader(
            val cellsNum: Int,
            val rootsNum: Int,
            val rootList: List<Int>,
            val sizeBytes: Int,
            val cellsData: ByteArray
        )

        private class BocParser(private val boc: ByteArray) {
            private var position = 0

            fun parseHeader(): BocHeader {
                require(boc.size >= 5) { "BOC too short" }
                val magic = readBytes(4)
                val magicInt = magic.toHexInt()
                val reach = REACH_BOC_MAGIC.toHexInt()
                val lean = LEAN_BOC_MAGIC.toHexInt()
                val leanCrc = LEAN_BOC_CRC_MAGIC.toHexInt()

                val hasIndex: Boolean
                val hasCrc: Boolean
                val sizeBytes: Int

                when (magicInt) {
                    reach -> {
                        val flagsByte = readByte().toInt() and 0xFF
                        hasIndex = (flagsByte and 0x80) != 0
                        hasCrc = (flagsByte and 0x40) != 0
                        sizeBytes = flagsByte and 0x07
                    }
                    lean -> {
                        hasIndex = true
                        hasCrc = false
                        sizeBytes = readByte().toInt() and 0xFF
                    }
                    leanCrc -> {
                        hasIndex = true
                        hasCrc = true
                        sizeBytes = readByte().toInt() and 0xFF
                    }
                    else -> error("Unsupported BOC magic prefix")
                }

                require(sizeBytes in 1..4) { "Invalid size bytes value" }

                val offsetBytes = readByte().toInt() and 0xFF
                val cellsNum = readUint(sizeBytes).toInt()
                val rootsNum = readUint(sizeBytes).toInt()
                val absentNum = readUint(sizeBytes)
                val totalCellSize = readUint(offsetBytes).toInt()

                require(absentNum == 0L) { "Unsupported absent cells" }

                val rootList = MutableList(rootsNum) { readUint(sizeBytes).toInt() }

                if (hasIndex) {
                    repeat(cellsNum) { readUint(offsetBytes) }
                }

                val cellsData = readBytes(totalCellSize)

                if (hasCrc) {
                    val crcBytes = readBytes(4)
                    val expected = CRC32C().apply { update(boc, 0, position - 4) }.value.toInt()
                    val provided = crcBytes.toIntValue()
                    require(expected == provided) { "CRC32C mismatch" }
                }

                require(position == boc.size) { "Unexpected extra data in BOC" }

                return BocHeader(cellsNum, rootsNum, rootList, sizeBytes, cellsData)
            }

            private fun readByte(): Byte {
                require(position < boc.size) { "Unexpected end of BOC" }
                return boc[position++]
            }

            private fun readBytes(length: Int): ByteArray {
                require(length >= 0) { "Negative length" }
                require(position + length <= boc.size) { "Unexpected end of BOC" }
                val slice = boc.copyOfRange(position, position + length)
                position += length
                return slice
            }

            private fun readUint(byteSize: Int): Long {
                require(byteSize >= 0) { "Negative byte size" }
                if (byteSize == 0) return 0
                var result = 0L
                repeat(byteSize) {
                    result = (result shl 8) or (readByte().toInt() and 0xFF).toLong()
                }
                return result
            }
        }

        private fun parseCellData(
            data: ByteArray,
            startOffset: Int,
            refSize: Int
        ): ParsedCell {
            var offset = startOffset
            require(offset + 2 <= data.size) { "Not enough data for cell descriptors" }
            val d1 = data[offset].toInt() and 0xFF
            val d2 = data[offset + 1].toInt() and 0xFF
            offset += 2

            val refsCount = d1 and 0x07
            val dataByteSize = (d2 + 1) / 2
            val fullfilled = d2 % 2 == 0

            require(offset + dataByteSize + refsCount * refSize <= data.size) { "Cell data exceeds bounds" }

            val cellData = data.copyOfRange(offset, offset + dataByteSize)
            offset += dataByteSize

            val refs = ArrayList<Int>(refsCount)
            repeat(refsCount) {
                var ref = 0
                repeat(refSize) {
                    ref = (ref shl 8) or (data[offset].toInt() and 0xFF)
                    offset += 1
                }
                refs.add(ref)
            }

            val (trimmedData, bits) = extractCellBits(cellData, fullfilled)

            return ParsedCell(trimmedData, bits, refs, offset)
        }

        private fun extractCellBits(data: ByteArray, fullfilled: Boolean): Pair<ByteArray, Int> {
            if (data.isEmpty()) return ByteArray(0) to 0

            val mutable = data.copyOf()
            var bits = mutable.size * 8

            if (!fullfilled) {
                var found = false
                for (i in 0 until 7) {
                    bits -= 1
                    val byteIndex = bits / 8
                    val bitIndex = bits % 8
                    val mask = 1 shl (7 - bitIndex)
                    if ((mutable[byteIndex].toInt() and mask) != 0) {
                        mutable[byteIndex] = (mutable[byteIndex].toInt() and mask.inv() and 0xFF).toByte()
                        found = true
                        break
                    }
                }
                require(found) { "Invalid padding in cell data" }
            }

            val byteSize = (bits + 7) / 8
            val trimmed = if (byteSize == 0) ByteArray(0) else mutable.copyOf(byteSize)
            return trimmed to bits
        }

        private val REACH_BOC_MAGIC = byteArrayOf(0xB5.toByte(), 0xEE.toByte(), 0x9C.toByte(), 0x72.toByte())
        private val LEAN_BOC_MAGIC = byteArrayOf(0x68, 0xFF.toByte(), 0x65, 0xF3.toByte())
        private val LEAN_BOC_CRC_MAGIC = byteArrayOf(0xAC.toByte(), 0xC3.toByte(), 0xA7.toByte(), 0x28)
    }

    private fun descriptorBytes(): ByteArray {
        val levelMask = 0
        val isExotic = false
        val refsDescriptor =
            (levelMask shl 5) or ((if (isExotic) 1 else 0) shl 3) or (references.size and 0x07)
        val fullBytes = bits / 8
        val dataBytes = (bits + 7) / 8
        val bitsDescriptor = fullBytes + dataBytes

        return byteArrayOf(refsDescriptor.toByte(), bitsDescriptor.toByte())
    }

    private fun topUppedData(): ByteArray {
        val dataBytes = (bits + 7) / 8
        if (dataBytes == 0) {
            return ByteArray(0)
        }

        val result = data.copyOf(dataBytes)
        val remainder = bits % 8
        if (remainder == 0) {
            return result
        }

        val mask = (-1 shl (8 - remainder)) and 0xFF
        val lastIndex = dataBytes - 1
        var last = result[lastIndex].toInt() and mask
        last = last or (1 shl (7 - remainder))
        result[lastIndex] = last.toByte()
        return result
    }

    private fun getRepresentation(
        hashCache: IdentityHashMap<Cell, ByteArray>,
        depthCache: IdentityHashMap<Cell, Int>
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        buffer.write(descriptorBytes())
        buffer.write(topUppedData())

        references.forEach { ref ->
            val depth = computeDepth(ref, depthCache)
            buffer.write(byteArrayOf((depth ushr 8).toByte(), depth.toByte()))
        }

        references.forEach { ref ->
            buffer.write(computeHash(ref, hashCache, depthCache))
        }

        return buffer.toByteArray()
    }

    private fun serializeForBoc(
        indices: IdentityHashMap<Cell, Int>,
        refSize: Int
    ): ByteArray {
        val buffer = ByteArrayOutputStream()
        buffer.write(descriptorBytes())
        buffer.write(topUppedData())
        references.forEach { ref ->
            val index = indices[ref] ?: error("Missing reference index")
            buffer.write(encodeUint(index, refSize))
        }
        return buffer.toByteArray()
    }

    private fun computeHash(
        cell: Cell,
        hashCache: IdentityHashMap<Cell, ByteArray>,
        depthCache: IdentityHashMap<Cell, Int>
    ): ByteArray {
        hashCache[cell]?.let { return it }
        val representation = cell.getRepresentation(hashCache, depthCache)
        val hash = MessageDigest.getInstance("SHA-256").digest(representation)
        hashCache[cell] = hash
        return hash
    }

    private fun computeDepth(cell: Cell, cache: IdentityHashMap<Cell, Int>): Int {
        cache[cell]?.let { return it }
        val depth = if (cell.references.isEmpty()) {
            0
        } else {
            1 + cell.references.maxOf { computeDepth(it, cache) }
        }
        cache[cell] = depth
        return depth
    }

}

private fun encodeUint(value: Int, size: Int): ByteArray {
    require(size >= 0) { "Negative size" }
    val result = ByteArray(size)
    for (i in size - 1 downTo 0) {
        result[i] = (value ushr (8 * (size - 1 - i)) and 0xFF).toByte()
    }
    return result
}

private fun byteSizeForNumber(value: Int): Int {
    if (value <= 0) return 1
    val bitLength = 32 - Integer.numberOfLeadingZeros(value)
    return (bitLength + 7) / 8
}

private fun ByteArray.toHexInt(): Int {
    var result = 0
    for (byte in this) {
        result = (result shl 8) or (byte.toInt() and 0xFF)
    }
    return result
}

private fun ByteArray.toIntValue(): Int {
    require(size == 4) { "Expected 4 bytes" }
    var result = 0
    for (byte in this) {
        result = (result shl 8) or (byte.toInt() and 0xFF)
    }
    return result
}
