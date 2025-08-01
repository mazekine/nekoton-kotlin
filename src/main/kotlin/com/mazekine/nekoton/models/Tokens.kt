package com.mazekine.nekoton.models

import com.ionspin.kotlin.bignum.decimal.BigDecimal
import com.ionspin.kotlin.bignum.decimal.DecimalMode
import com.ionspin.kotlin.bignum.decimal.RoundingMode
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Represents token amounts in the TON/Everscale network.
 * 
 * This class handles token amounts with proper precision, supporting both
 * integer nano-token representation and decimal token representation.
 * All calculations are performed using arbitrary precision arithmetic.
 * 
 * @property nanoTokens The amount in nano-tokens (1 token = 10^9 nano-tokens)
 */
@Serializable
data class Tokens(val nanoTokens: @Contextual BigInteger) : Comparable<Tokens> {

    /**
     * Creates Tokens from a decimal token amount.
     * 
     * @param tokens The token amount as a decimal
     */
    constructor(tokens: BigDecimal) : this(
        tokens.multiply(NANO_TOKENS_PER_TOKEN).toBigInteger()
    )

    /**
     * Creates Tokens from a string representation.
     * 
     * @param tokens The token amount as a string (e.g., "1.5", "1000000000")
     */
    constructor(tokens: String) : this(
        if (tokens.contains('.')) {
            BigDecimal.parseString(tokens).multiply(NANO_TOKENS_PER_TOKEN).toBigInteger()
        } else {
            BigInteger.parseString(tokens)
        }
    )

    /**
     * Creates Tokens from a long value (nano-tokens).
     * 
     * @param nanoTokens The amount in nano-tokens
     */
    constructor(nanoTokens: Long) : this(BigInteger.fromLong(nanoTokens))

    /**
     * Creates Tokens from an int value (nano-tokens).
     * 
     * @param nanoTokens The amount in nano-tokens
     */
    constructor(nanoTokens: Int) : this(BigInteger.fromInt(nanoTokens))

    /**
     * Converts nano-tokens to decimal token representation.
     * 
     * @return The token amount as a BigDecimal
     */
    fun toTokens(): BigDecimal {
        return BigDecimal.fromBigInteger(nanoTokens).divide(
            NANO_TOKENS_PER_TOKEN,
            DecimalMode(9, RoundingMode.ROUND_HALF_TOWARDS_ZERO)
        )
    }

    /**
     * Converts to a human-readable string representation.
     * 
     * @param precision The number of decimal places to show (default: 9)
     * @return String representation of the token amount
     */
    fun toTokenString(precision: Int = 9): String {
        val tokens = toTokens()
        return tokens.toStringExpanded().let { str ->
            if (precision < 9 && str.contains('.')) {
                val parts = str.split('.')
                val decimals = parts[1].take(precision).trimEnd('0')
                if (decimals.isEmpty()) parts[0] else "${parts[0]}.$decimals"
            } else {
                str
            }
        }
    }

    /**
     * Addition operator for Tokens.
     */
    operator fun plus(other: Tokens): Tokens = Tokens(nanoTokens + other.nanoTokens)

    /**
     * Subtraction operator for Tokens.
     */
    operator fun minus(other: Tokens): Tokens = Tokens(nanoTokens - other.nanoTokens)

    /**
     * Multiplication operator for Tokens.
     */
    operator fun times(multiplier: BigInteger): Tokens = Tokens(nanoTokens * multiplier)

    /**
     * Multiplication operator for Tokens with Long.
     */
    operator fun times(multiplier: Long): Tokens = Tokens(nanoTokens * BigInteger.fromLong(multiplier))

    /**
     * Division operator for Tokens.
     */
    operator fun div(divisor: BigInteger): Tokens = Tokens(nanoTokens / divisor)

    /**
     * Division operator for Tokens with Long.
     */
    operator fun div(divisor: Long): Tokens = Tokens(nanoTokens / BigInteger.fromLong(divisor))

    /**
     * Unary minus operator for Tokens.
     */
    operator fun unaryMinus(): Tokens = Tokens(-nanoTokens)

    /**
     * Compares this Tokens with another Tokens.
     */
    override fun compareTo(other: Tokens): Int = nanoTokens.compareTo(other.nanoTokens)

    /**
     * Checks if the token amount is zero.
     */
    fun isZero(): Boolean = nanoTokens.isZero()

    /**
     * Checks if the token amount is positive.
     */
    fun isPositive(): Boolean = nanoTokens > BigInteger.ZERO

    /**
     * Checks if the token amount is negative.
     */
    fun isNegative(): Boolean = nanoTokens < BigInteger.ZERO

    /**
     * Returns the absolute value of the token amount.
     */
    fun abs(): Tokens = if (isNegative()) -this else this

    /**
     * Returns the minimum of this and another Tokens.
     */
    fun min(other: Tokens): Tokens = if (this <= other) this else other

    /**
     * Returns the maximum of this and another Tokens.
     */
    fun max(other: Tokens): Tokens = if (this >= other) this else other

    override fun toString(): String = toTokenString()

    companion object {
        /**
         * Zero tokens constant.
         */
        val ZERO = Tokens(BigInteger.ZERO)

        /**
         * One token constant.
         */
        val ONE = Tokens(BigInteger.fromLong(1_000_000_000L))

        /**
         * Number of nano-tokens per token.
         */
        private val NANO_TOKENS_PER_TOKEN = BigDecimal.fromLong(1_000_000_000L)

        /**
         * Creates Tokens from a token amount.
         * 
         * @param tokens The token amount
         * @return Tokens instance
         */
        fun fromTokens(tokens: Double): Tokens = Tokens(BigDecimal.fromDouble(tokens))

        /**
         * Creates Tokens from nano-tokens.
         * 
         * @param nanoTokens The nano-token amount
         * @return Tokens instance
         */
        fun fromNanoTokens(nanoTokens: String): Tokens = Tokens(BigInteger.parseString(nanoTokens))
    }
}
