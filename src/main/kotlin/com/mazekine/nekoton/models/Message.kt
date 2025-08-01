package com.mazekine.nekoton.models

import kotlinx.serialization.Serializable

/**
 * Represents a blockchain message in the TON/Everscale network.
 * 
 * Messages are the primary means of communication between accounts in the blockchain.
 * They can be internal (between accounts) or external (from/to external world).
 * 
 * @property hash The message hash
 * @property header The message header containing routing and type information
 * @property body The message body containing the actual data
 * @property stateInit Optional state initialization data for contract deployment
 */
@Serializable
data class Message(
    val hash: ByteArray,
    val header: MessageHeader,
    val body: Cell? = null,
    val stateInit: StateInit? = null
) {
    init {
        require(hash.size == 32) { "Message hash must be 32 bytes" }
    }

    /**
     * Gets the message hash as a hex string.
     * 
     * @return Hex representation of the message hash
     */
    fun hashHex(): String = hash.joinToString("") { "%02x".format(it) }

    /**
     * Gets the message type based on the header.
     * 
     * @return The message type
     */
    fun getType(): MessageType = header.getType()

    /**
     * Checks if this is an internal message.
     * 
     * @return true if the message is internal
     */
    fun isInternal(): Boolean = header is InternalMessageHeader

    /**
     * Checks if this is an external inbound message.
     * 
     * @return true if the message is external inbound
     */
    fun isExternalIn(): Boolean = header is ExternalInMessageHeader

    /**
     * Checks if this is an external outbound message.
     * 
     * @return true if the message is external outbound
     */
    fun isExternalOut(): Boolean = header is ExternalOutMessageHeader

    /**
     * Gets the destination address if available.
     * 
     * @return The destination address or null
     */
    fun getDestination(): Address? = when (header) {
        is InternalMessageHeader -> header.dst
        is ExternalInMessageHeader -> header.dst
        is ExternalOutMessageHeader -> header.src
    }

    /**
     * Gets the source address if available.
     * 
     * @return The source address or null
     */
    fun getSource(): Address? = when (header) {
        is InternalMessageHeader -> header.src
        is ExternalInMessageHeader -> null
        is ExternalOutMessageHeader -> header.src
    }

    /**
     * Gets the value being transferred (for internal messages).
     * 
     * @return The token amount or null for external messages
     */
    fun getValue(): Tokens? = when (header) {
        is InternalMessageHeader -> header.value
        else -> null
    }

    /**
     * Encodes the message to BOC format.
     * 
     * @return BOC representation as byte array
     */
    fun toBoc(): ByteArray {
        // This would require the actual message serialization logic
        TODO("Message BOC encoding not yet implemented")
    }

    /**
     * Builds a cell representation of the message.
     * 
     * @return Cell containing the message data
     */
    fun buildCell(): Cell {
        // This would require the actual message cell building logic
        TODO("Message cell building not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Message

        if (!hash.contentEquals(other.hash)) return false
        if (header != other.header) return false
        if (body != other.body) return false
        if (stateInit != other.stateInit) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + (body?.hashCode() ?: 0)
        result = 31 * result + (stateInit?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "Message(hash=${hashHex()}, type=${getType()})"
    }

    companion object {
        /**
         * Creates a Message from BOC data.
         * 
         * @param boc The BOC data as byte array
         * @return Message instance
         */
        fun fromBoc(boc: ByteArray): Message {
            // This would require the actual message deserialization logic
            TODO("Message BOC decoding not yet implemented")
        }

        /**
         * Creates a Message from a base64-encoded BOC string.
         * 
         * @param base64Boc The BOC data as base64 string
         * @return Message instance
         */
        fun fromBase64(base64Boc: String): Message {
            val boc = java.util.Base64.getDecoder().decode(base64Boc)
            return fromBoc(boc)
        }

        /**
         * Creates a Message from a Cell.
         * 
         * @param cell The cell containing message data
         * @return Message instance
         */
        fun fromCell(cell: Cell): Message {
            // This would require the actual message parsing logic
            TODO("Message cell parsing not yet implemented")
        }
    }
}

/**
 * Base class for message headers.
 */
@Serializable
sealed class MessageHeader {
    /**
     * Gets the message type for this header.
     * 
     * @return The message type
     */
    abstract fun getType(): MessageType
}

/**
 * Header for internal messages (between accounts).
 * 
 * @property ihrDisabled Whether IHR (Instant Hypercube Routing) is disabled
 * @property bounce Whether the message should bounce on failure
 * @property bounced Whether this message is a bounced message
 * @property src Source address (can be null for some system messages)
 * @property dst Destination address
 * @property value Amount of tokens being transferred
 * @property ihrFee IHR fee
 * @property fwdFee Forward fee
 * @property createdLt Logical time when the message was created
 * @property createdAt Unix timestamp when the message was created
 */
@Serializable
data class InternalMessageHeader(
    val ihrDisabled: Boolean,
    val bounce: Boolean,
    val bounced: Boolean,
    val src: Address?,
    val dst: Address,
    val value: Tokens,
    val ihrFee: Tokens,
    val fwdFee: Tokens,
    val createdLt: Long,
    val createdAt: Int
) : MessageHeader() {
    init {
        require(createdLt >= 0) { "Created LT cannot be negative" }
        require(createdAt >= 0) { "Created timestamp cannot be negative" }
    }

    override fun getType(): MessageType = MessageType.Internal

    override fun toString(): String {
        return "InternalHeader(src=$src, dst=$dst, value=$value, bounce=$bounce)"
    }
}

/**
 * Header for external inbound messages (from external world to blockchain).
 * 
 * @property src External source address (usually null)
 * @property dst Destination address on the blockchain
 * @property importFee Fee for importing the message
 */
@Serializable
data class ExternalInMessageHeader(
    val src: Address?,
    val dst: Address,
    val importFee: Tokens
) : MessageHeader() {
    override fun getType(): MessageType = MessageType.ExternalIn

    override fun toString(): String {
        return "ExternalInHeader(dst=$dst, importFee=$importFee)"
    }
}

/**
 * Header for external outbound messages (from blockchain to external world).
 * 
 * @property src Source address on the blockchain
 * @property dst External destination address (usually null)
 * @property createdLt Logical time when the message was created
 * @property createdAt Unix timestamp when the message was created
 */
@Serializable
data class ExternalOutMessageHeader(
    val src: Address,
    val dst: Address?,
    val createdLt: Long,
    val createdAt: Int
) : MessageHeader() {
    init {
        require(createdLt >= 0) { "Created LT cannot be negative" }
        require(createdAt >= 0) { "Created timestamp cannot be negative" }
    }

    override fun getType(): MessageType = MessageType.ExternalOut

    override fun toString(): String {
        return "ExternalOutHeader(src=$src, createdLt=$createdLt)"
    }
}

/**
 * Represents the type of a message.
 */
@Serializable
enum class MessageType {
    /** Internal message between accounts */
    Internal,
    /** External inbound message */
    ExternalIn,
    /** External outbound message */
    ExternalOut
}

/**
 * Represents state initialization data for contract deployment.
 * 
 * @property code The contract code
 * @property data The contract initial data
 * @property library Optional library references
 */
@Serializable
data class StateInit(
    val code: Cell?,
    val data: Cell?,
    val library: Cell? = null
) {
    /**
     * Checks if this StateInit is valid for deployment.
     * 
     * @return true if both code and data are present
     */
    fun isValid(): Boolean = code != null && data != null

    /**
     * Builds a cell representation of the StateInit.
     * 
     * @return Cell containing the StateInit data
     */
    fun buildCell(): Cell {
        // This would require the actual StateInit cell building logic
        TODO("StateInit cell building not yet implemented")
    }

    override fun toString(): String {
        return "StateInit(hasCode=${code != null}, hasData=${data != null}, hasLibrary=${library != null})"
    }

    companion object {
        /**
         * Creates a StateInit from code and data cells.
         * 
         * @param code The contract code cell
         * @param data The contract data cell
         * @param library Optional library cell
         * @return StateInit instance
         */
        fun create(code: Cell, data: Cell, library: Cell? = null): StateInit {
            return StateInit(code, data, library)
        }

        /**
         * Creates a StateInit from a cell.
         * 
         * @param cell The cell containing StateInit data
         * @return StateInit instance
         */
        fun fromCell(cell: Cell): StateInit {
            // This would require the actual StateInit parsing logic
            TODO("StateInit cell parsing not yet implemented")
        }
    }
}
