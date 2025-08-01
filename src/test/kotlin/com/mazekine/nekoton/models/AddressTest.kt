package com.mazekine.nekoton.models

import com.mazekine.nekoton.TestConfig
import kotlin.test.*

class AddressTest {
    
    @Test
    fun testAddressCreationFromString() {
        val addressString = TestConfig.TEST_ADDRESS
        val address = Address(addressString)
        
        assertEquals(TestConfig.TEST_WORKCHAIN, address.workchain)
        assertEquals(32, address.address.size)
        assertEquals(addressString, address.toString())
    }
    
    @Test
    fun testAddressValidation() {
        // Valid address should not throw
        val address = Address(TestConfig.TEST_ADDRESS)
        assertNotNull(address)
        
        assertFailsWith<IllegalArgumentException> {
            Address("totally invalid address")
        }
        
        assertFailsWith<IllegalArgumentException> {
            Address("0:invalid")
        }
        
        assertFailsWith<IllegalArgumentException> {
            Address("999:d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        }
    }
    
    @Test
    fun testAddressFromHex() {
        val hexAddress = "d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf"
        val address = Address.fromHex(0, hexAddress)
        
        assertEquals(0, address.workchain)
        assertEquals(32, address.address.size)
        assertEquals("0:$hexAddress", address.toString())
    }
    
    @Test
    fun testAddressEquality() {
        val address1 = Address(TestConfig.TEST_ADDRESS)
        val address2 = Address(TestConfig.TEST_ADDRESS)
        val address3 = Address("-1:d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        
        assertEquals(address1, address2)
        assertNotEquals(address1, address3)
        assertEquals(address1.hashCode(), address2.hashCode())
    }
    
    @Test
    fun testAddressHashMap() {
        val address = Address(TestConfig.TEST_ADDRESS)
        val addressMap = hashMapOf(address to 123)
        
        assertEquals(123, addressMap[address])
        assertEquals(123, addressMap[Address(TestConfig.TEST_ADDRESS)])
    }
    
    @Test
    fun testWorkchainRange() {
        // Valid workchains should not throw
        val address1 = Address.fromHex(-128, "d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        val address2 = Address.fromHex(127, "d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        assertNotNull(address1)
        assertNotNull(address2)
        
        assertFailsWith<IllegalArgumentException> {
            Address.fromHex(-129, "d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        }
        assertFailsWith<IllegalArgumentException> {
            Address.fromHex(128, "d84e969feb02481933382c4544e9ff24a2f359847f8896baa86c501c3d1b00cf")
        }
    }
}
