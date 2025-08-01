package com.mazekine.nekoton.models

import kotlin.test.*

class CellTest {
    
    @Test
    fun testCellBuilderBasicOperations() {
        val builder = CellBuilder()
        
        builder.writeUint(0L, 8)
        builder.writeUint(0xFL, 4)
        builder.writeUint(0x1234L, 16)
        
        val cell = builder.build()
        assertNotNull(cell)
        assertEquals(28, cell.bits)
    }
    
    @Test
    fun testCellBuilderWithBytes() {
        val builder = CellBuilder()
        val testBytes = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        
        builder.writeBytes(testBytes)
        val cell = builder.build()
        
        assertEquals(32, cell.bits)
        assertTrue(cell.data.contentEquals(testBytes))
    }
    
    @Test
    fun testCellBuilderWithReferences() {
        val refBuilder = CellBuilder()
        refBuilder.writeUint(0xABCDL, 16)
        val refCell = refBuilder.build()
        
        val mainBuilder = CellBuilder()
        mainBuilder.writeUint(0x1234L, 16)
        mainBuilder.writeRef(refCell)
        
        val mainCell = mainBuilder.build()
        assertEquals(1, mainCell.references.size)
        assertEquals(refCell, mainCell.references[0])
    }
    
    @Test
    fun testCellSliceBasics() {
        val builder = CellBuilder()
        builder.writeUint(0xFFL, 8)
        val cell = builder.build()
        
        val slice = cell.beginParse()
        assertNotNull(slice)
    }
    
    @Test
    fun testCellEquality() {
        val builder1 = CellBuilder()
        builder1.writeUint(0x1234L, 16)
        val cell1 = builder1.build()
        
        val builder2 = CellBuilder()
        builder2.writeUint(0x1234L, 16)
        val cell2 = builder2.build()
        
        assertEquals(cell1, cell2)
        assertEquals(cell1.hashCode(), cell2.hashCode())
    }
    
    @Test
    fun testCellBuilderOverflow() {
        val builder = CellBuilder()
        
        assertFailsWith<IllegalArgumentException> {
            repeat(1024) {
                builder.writeUint(0xFFL, 8)
            }
        }
    }
    
    @Test
    fun testCellBuilderRemainingBits() {
        val builder = CellBuilder()
        assertEquals(1023, builder.remainingBits)
        
        builder.writeUint(0xFFL, 8)
        assertEquals(1015, builder.remainingBits)
        
        builder.writeUint(0x1234L, 16)
        assertEquals(999, builder.remainingBits)
    }
    
    @Test
    fun testCellFromHex() {
        val hexData = "1234abcd"
        val cell = Cell(hexData)
        
        assertEquals(32, cell.bits)
        assertEquals(hexData, cell.toHex())
    }
    
    @Test
    fun testEmptyCell() {
        val cell = Cell.empty()
        assertEquals(0, cell.bits)
        assertEquals(0, cell.data.size)
        assertEquals(0, cell.references.size)
    }
    
    @Test
    fun testCellDepth() {
        val refCell = Cell.empty()
        val builder = CellBuilder()
        builder.writeRef(refCell)
        val cell = builder.build()
        
        assertEquals(1, cell.depth())
        assertEquals(0, refCell.depth())
    }
}
