package com.mazekine.nekoton.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.*

class TokensTest {
    
    @Test
    fun testTokensCreation() {
        val tokens1 = Tokens(BigInteger.fromLong(1_000_000_000L))
        val tokens2 = Tokens("1.0")
        val tokens3 = Tokens(BigDecimal.fromDouble(1.0))
        val tokens4 = Tokens(1_000_000_000L)
        
        assertEquals(tokens1, tokens2)
        assertEquals(tokens2, tokens3)
        assertEquals(tokens3, tokens4)
    }
    
    @Test
    fun testTokensToString() {
        val tokens = Tokens("1.5")
        assertEquals("1.5", tokens.toString())
        
        val zeroTokens = Tokens.ZERO
        assertEquals("0", zeroTokens.toString())
        
        val oneToken = Tokens.ONE
        assertEquals("1", oneToken.toString())
    }
    
    @Test
    fun testTokensArithmetic() {
        val tokens1 = Tokens("10.123456789")
        val tokens2 = Tokens("5.0")
        
        val sum = tokens1 + tokens2
        assertEquals(Tokens("15.123456789"), sum)
        
        val diff = tokens1 - tokens2
        assertEquals(Tokens("5.123456789"), diff)
        
        val product = tokens2 * BigInteger.fromLong(2L)
        assertEquals(Tokens("10.0"), product)
        
        val quotient = tokens2 / BigInteger.fromLong(2L)
        assertEquals(Tokens("2.5"), quotient)
        
        assertEquals(tokens1, (tokens1 * 2L) / 2L)
    }
    
    @Test
    fun testTokensComparison() {
        val tokens1 = Tokens("1.5")
        val tokens2 = Tokens("2.0")
        val tokens3 = Tokens("1.5")
        
        assertTrue(tokens1 < tokens2)
        assertTrue(tokens2 > tokens1)
        assertEquals(0, tokens1.compareTo(tokens3))
        
        assertTrue(tokens1.isPositive())
        assertFalse(tokens1.isZero())
        assertFalse(tokens1.isNegative())
        
        assertTrue(Tokens.ZERO.isZero())
        assertFalse(Tokens.ZERO.isPositive())
        assertFalse(Tokens.ZERO.isNegative())
    }
    
    @Test
    fun testTokensNegative() {
        val tokens = Tokens("5.0")
        val negativeTokens = -tokens
        
        assertTrue(negativeTokens.isNegative())
        assertEquals(tokens, negativeTokens.abs())
        assertEquals(tokens, -negativeTokens)
    }
    
    @Test
    fun testTokensMinMax() {
        val tokens1 = Tokens("1.5")
        val tokens2 = Tokens("2.0")
        
        assertEquals(tokens1, tokens1.min(tokens2))
        assertEquals(tokens2, tokens1.max(tokens2))
    }
    
    @Test
    fun testTokensPrecision() {
        val tokens = Tokens("1.123456789")
        
        assertTrue(tokens.toTokenString(9).startsWith("1.123456"))
        assertEquals("1.123", tokens.toTokenString(3))
        assertEquals("1", tokens.toTokenString(0))
    }
    
    @Test
    fun testTokensFromNanoTokens() {
        val nanoTokens = "1500000000"
        val tokens = Tokens.fromNanoTokens(nanoTokens)
        
        assertEquals(Tokens("1.5"), tokens)
        assertEquals(BigInteger.parseString(nanoTokens), tokens.nanoTokens)
    }
    
    @Test
    fun testTokensFromDouble() {
        val tokens = Tokens.fromTokens(1.5)
        assertEquals(Tokens("1.5"), tokens)
    }
}
