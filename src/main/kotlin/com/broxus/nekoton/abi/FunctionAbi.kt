package com.broxus.nekoton.abi

import com.broxus.nekoton.crypto.PublicKey
import com.broxus.nekoton.models.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/**
 * Represents a function ABI definition for TON/Everscale smart contracts.
 * 
 * This class defines the interface for a specific contract function, including
 * its signature, input/output parameters, and encoding/decoding capabilities.
 * 
 * @property abiVersion The ABI version this function uses
 * @property name The function name
 * @property inputId The function input ID (used for message routing)
 * @property outputId The function output ID (used for response identification)
 * @property inputs List of input parameters
 * @property outputs List of output parameters
 */
@Serializable
data class FunctionAbi(
    val abiVersion: AbiVersion,
    val name: String,
    val inputId: Int,
    val outputId: Int,
    val inputs: List<AbiParam>,
    val outputs: List<AbiParam>
) {
    /**
     * Creates a function ABI with arguments bound to it.
     * 
     * @param args Map of argument names to values
     * @return FunctionAbiWithArgs instance
     */
    fun withArgs(args: Map<String, Any>): FunctionAbiWithArgs {
        return FunctionAbiWithArgs(this, args)
    }

    /**
     * Calls the function locally on an account state.
     * 
     * @param accountState The account state to execute against
     * @param input Input parameters
     * @param responsible Whether this is a responsible call
     * @param config Optional blockchain configuration
     * @return Execution output
     */
    suspend fun call(
        accountState: AccountState,
        input: Map<String, Any>,
        responsible: Boolean = false,
        config: BlockchainConfig? = null
    ): ExecutionOutput {
        // This would require the actual local execution logic
        TODO("Local function execution not yet implemented")
    }

    /**
     * Encodes an external message for this function.
     * 
     * @param dst Destination address
     * @param input Input parameters
     * @param publicKey Optional public key for signing
     * @param stateInit Optional state initialization
     * @param timeout Message timeout in seconds
     * @return Unsigned external message
     */
    fun encodeExternalMessage(
        dst: Address,
        input: Map<String, Any>,
        publicKey: PublicKey? = null,
        stateInit: StateInit? = null,
        timeout: Int? = null
    ): UnsignedExternalMessage {
        val body = encodeExternalInput(input, publicKey, timeout, dst)
        return UnsignedExternalMessage(
            dst = dst,
            stateInit = stateInit,
            body = body
        )
    }

    /**
     * Encodes external input for this function.
     * 
     * @param input Input parameters
     * @param publicKey Optional public key
     * @param timeout Message timeout in seconds
     * @param address Optional destination address
     * @return Unsigned message body
     */
    fun encodeExternalInput(
        input: Map<String, Any>,
        publicKey: PublicKey? = null,
        timeout: Int? = null,
        address: Address? = null
    ): UnsignedBody {
        val builder = CellBuilder.create()
        
        // Write function ID
        builder.writeUint(inputId.toLong(), 32)
        
        // Write timestamp and expire
        val now = System.currentTimeMillis() / 1000
        val expireAt = now + (timeout ?: DEFAULT_TIMEOUT)
        builder.writeUint(expireAt, 64)
        
        // Write public key if provided
        publicKey?.let { key ->
            builder.writeBytes(key.toBytes())
        }
        
        // Encode input parameters
        for ((param, value) in inputs.zip(extractValues(input))) {
            encodeParam(builder, param, value)
        }
        
        val payload = builder.build()
        val hash = payload.hash()
        
        return UnsignedBody(
            abiVersion = abiVersion,
            payload = payload,
            hash = hash,
            expireAt = expireAt
        )
    }

    /**
     * Encodes an internal message for this function.
     * 
     * @param input Input parameters
     * @param value Token amount to send
     * @param bounce Whether the message should bounce
     * @param dst Destination address
     * @param src Optional source address
     * @param stateInit Optional state initialization
     * @return Internal message
     */
    fun encodeInternalMessage(
        input: Map<String, Any>,
        value: Tokens,
        bounce: Boolean,
        dst: Address,
        src: Address? = null,
        stateInit: StateInit? = null
    ): Message {
        val body = encodeInternalInput(input)
        
        val header = InternalMessageHeader(
            ihrDisabled = true,
            bounce = bounce,
            bounced = false,
            src = src,
            dst = dst,
            value = value,
            ihrFee = Tokens.ZERO,
            fwdFee = Tokens.ZERO,
            createdLt = 0, // Would be set by the network
            createdAt = (System.currentTimeMillis() / 1000).toInt()
        )
        
        // Calculate message hash
        val messageCell = buildMessageCell(header, body, stateInit)
        val hash = messageCell.hash()
        
        return Message(
            hash = hash,
            header = header,
            body = body,
            stateInit = stateInit
        )
    }

    /**
     * Encodes internal input for this function.
     * 
     * @param input Input parameters
     * @return Cell containing the encoded input
     */
    fun encodeInternalInput(input: Map<String, Any>): Cell {
        val builder = CellBuilder.create()
        
        // Write function ID
        builder.writeUint(inputId.toLong(), 32)
        
        // Encode input parameters
        for ((param, value) in inputs.zip(extractValues(input))) {
            encodeParam(builder, param, value)
        }
        
        return builder.build()
    }

    /**
     * Decodes a transaction to extract function call information.
     * 
     * @param transaction The transaction to decode
     * @return Decoded input parameters or null if not this function
     */
    fun decodeTransaction(transaction: Transaction): Map<String, Any>? {
        val inMsg = transaction.inMsg ?: return null
        val body = inMsg.body ?: return null
        
        return decodeInput(body, inMsg.isInternal())
    }

    /**
     * Decodes input parameters from a message body.
     * 
     * @param body The message body cell
     * @param internal Whether this is an internal message
     * @return Decoded input parameters
     */
    fun decodeInput(body: Cell, internal: Boolean = false): Map<String, Any> {
        val slice = body.beginParse()
        
        // Read and verify function ID
        val functionId = slice.readUint(32).intValue()
        require(functionId == inputId) { "Function ID mismatch: expected $inputId, got $functionId" }
        
        if (!internal) {
            // Skip timestamp and expire for external messages
            slice.skipBits(64) // expire_at
            // Skip public key if present
            if (slice.remainingBits >= 256) {
                slice.skipBits(256) // public key
            }
        }
        
        // Decode input parameters
        val decodedParams = mutableMapOf<String, Any>()
        for (param in inputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }
        
        return decodedParams
    }

    /**
     * Decodes output parameters from a message body.
     * 
     * @param body The message body cell
     * @param internal Whether this is an internal message
     * @return Decoded output parameters
     */
    fun decodeOutput(body: Cell, internal: Boolean = false): Map<String, Any> {
        val slice = body.beginParse()
        
        // Read and verify function ID
        val functionId = slice.readUint(32).intValue()
        require(functionId == outputId) { "Output ID mismatch: expected $outputId, got $functionId" }
        
        // Decode output parameters
        val decodedParams = mutableMapOf<String, Any>()
        for (param in outputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }
        
        return decodedParams
    }

    /**
     * Checks if this function has output parameters.
     * 
     * @return true if the function has outputs
     */
    fun hasOutput(): Boolean = outputs.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FunctionAbi

        if (name != other.name) return false
        if (inputId != other.inputId) return false
        if (outputId != other.outputId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + inputId
        result = 31 * result + outputId
        return result
    }

    override fun toString(): String {
        return "FunctionAbi(name='$name', inputId=0x${inputId.toString(16)}, outputId=0x${outputId.toString(16)})"
    }

    companion object {
        /**
         * Default message timeout in seconds.
         */
        const val DEFAULT_TIMEOUT = 60

        /**
         * Extracts parameter values in the correct order.
         */
        private fun extractValues(input: Map<String, Any>): List<Any> {
            // This would extract values in the order defined by the ABI
            TODO("Parameter value extraction not yet implemented")
        }

        /**
         * Encodes a parameter value into a cell builder.
         */
        private fun encodeParam(builder: CellBuilder, param: AbiParam, value: Any) {
            // This would require the actual parameter encoding logic
            TODO("Parameter encoding not yet implemented")
        }

        /**
         * Decodes a parameter value from a cell slice.
         */
        private fun decodeParam(slice: CellSlice, param: AbiParam): Any {
            // This would require the actual parameter decoding logic
            TODO("Parameter decoding not yet implemented")
        }

        /**
         * Builds a message cell from components.
         */
        private fun buildMessageCell(header: MessageHeader, body: Cell?, stateInit: StateInit?): Cell {
            // This would require the actual message cell building logic
            TODO("Message cell building not yet implemented")
        }
    }
}

/**
 * Represents a function ABI with bound arguments.
 * 
 * @property abi The function ABI
 * @property args The bound arguments
 */
@Serializable
data class FunctionAbiWithArgs(
    val abi: FunctionAbi,
    val args: Map<String, @Contextual Any>
) {
    /**
     * Calls the function with the bound arguments.
     * 
     * @param accountState The account state to execute against
     * @param responsible Whether this is a responsible call
     * @param config Optional blockchain configuration
     * @return Execution output
     */
    suspend fun call(
        accountState: AccountState,
        responsible: Boolean = false,
        config: BlockchainConfig? = null
    ): ExecutionOutput {
        return abi.call(accountState, args, responsible, config)
    }

    /**
     * Encodes an external message with the bound arguments.
     * 
     * @param dst Destination address
     * @param publicKey Optional public key for signing
     * @param stateInit Optional state initialization
     * @param timeout Message timeout in seconds
     * @return Unsigned external message
     */
    fun encodeExternalMessage(
        dst: Address,
        publicKey: PublicKey? = null,
        stateInit: StateInit? = null,
        timeout: Int? = null
    ): UnsignedExternalMessage {
        return abi.encodeExternalMessage(dst, args, publicKey, stateInit, timeout)
    }

    /**
     * Encodes external input with the bound arguments.
     * 
     * @param publicKey Optional public key
     * @param timeout Message timeout in seconds
     * @param address Optional destination address
     * @return Unsigned message body
     */
    fun encodeExternalInput(
        publicKey: PublicKey? = null,
        timeout: Int? = null,
        address: Address? = null
    ): UnsignedBody {
        return abi.encodeExternalInput(args, publicKey, timeout, address)
    }

    /**
     * Encodes an internal message with the bound arguments.
     * 
     * @param value Token amount to send
     * @param bounce Whether the message should bounce
     * @param dst Destination address
     * @param src Optional source address
     * @param stateInit Optional state initialization
     * @return Internal message
     */
    fun encodeInternalMessage(
        value: Tokens,
        bounce: Boolean,
        dst: Address,
        src: Address? = null,
        stateInit: StateInit? = null
    ): Message {
        return abi.encodeInternalMessage(args, value, bounce, dst, src, stateInit)
    }

    /**
     * Encodes internal input with the bound arguments.
     * 
     * @return Cell containing the encoded input
     */
    fun encodeInternalInput(): Cell {
        return abi.encodeInternalInput(args)
    }

    override fun toString(): String {
        return "FunctionAbiWithArgs(${abi.name}, args=${args.keys})"
    }
}

/**
 * Represents the output of a function execution.
 * 
 * @property exitCode The exit code of the execution
 * @property output The output parameters (if any)
 */
@Serializable
data class ExecutionOutput(
    val exitCode: Int,
    val output: Map<String, @Contextual Any>?
) {
    /**
     * Checks if the execution was successful.
     * 
     * @return true if exit code is 0
     */
    fun isSuccess(): Boolean = exitCode == 0

    override fun toString(): String {
        return "ExecutionOutput(exitCode=$exitCode, hasOutput=${output != null})"
    }
}

/**
 * Represents an unsigned external message.
 * 
 * @property dst Destination address
 * @property stateInit Optional state initialization
 * @property body Message body
 */
@Serializable
data class UnsignedExternalMessage(
    val dst: Address,
    val stateInit: StateInit?,
    val body: UnsignedBody
) {
    /**
     * Gets the expiration time of the message.
     * 
     * @return Expiration timestamp
     */
    fun expireAt(): Long = body.expireAt

    override fun toString(): String {
        return "UnsignedExternalMessage(dst=$dst, expireAt=${expireAt()})"
    }
}

/**
 * Represents an unsigned message body.
 * 
 * @property abiVersion The ABI version used
 * @property payload The message payload
 * @property hash The payload hash
 * @property expireAt Expiration timestamp
 */
@Serializable
data class UnsignedBody(
    val abiVersion: AbiVersion,
    val payload: Cell,
    val hash: ByteArray,
    val expireAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UnsignedBody

        if (abiVersion != other.abiVersion) return false
        if (payload != other.payload) return false
        if (!hash.contentEquals(other.hash)) return false
        if (expireAt != other.expireAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = abiVersion.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + expireAt.hashCode()
        return result
    }

    override fun toString(): String {
        return "UnsignedBody(expireAt=$expireAt, hashHex=${hash.joinToString("") { "%02x".format(it) }})"
    }
}
