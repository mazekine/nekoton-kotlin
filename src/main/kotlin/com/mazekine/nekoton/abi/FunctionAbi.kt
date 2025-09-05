package com.mazekine.nekoton.abi

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.crypto.PublicKey
import com.mazekine.nekoton.models.AccountState
import com.mazekine.nekoton.models.BlockchainConfig
import com.mazekine.nekoton.models.Cell
import com.mazekine.nekoton.models.CellBuilder
import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.models.StateInit
import com.mazekine.nekoton.models.Tokens
import com.mazekine.nekoton.models.Message
import com.mazekine.nekoton.models.MessageHeader
import com.mazekine.nekoton.models.InternalMessageHeader
import com.mazekine.nekoton.models.Transaction
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.double
import java.util.concurrent.TimeUnit

/**
 * Represents a function ABI definition for TON/Everscale smart contracts.
 *
 * Native wiring notes:
 * - For INTERNAL payloads we fully delegate to JNI: Native.encodeFunctionCall(...)
 * - For OUTPUT decoding we fully delegate to JNI: Native.decodeFunctionOutput(...)
 * - For EXTERNAL payloads, ABI v2.3 requires: Maybe(Signature) + Enc(Header) + FunctionID + Enc(args).
 *   JNI surface does not build that full structure yet; we return the encoded (FunctionID + Enc(args)) cell
 *   and propagate expireAt in metadata so the signing/sending layer can assemble v2.3 body correctly.
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
    // ---------------- Native bridge ----------------

    /**
     * Lazily-attached native function encoder/decoder. Call [bindNativeAbi] once
     * with an ABI handle produced by Native.parseAbi(abiJson).
     */
    @kotlinx.serialization.Transient
    private var nativeAbi: NativeFunctionAbi? = null

    /**
     * Attach a native ABI handle to this function. Safe to call once.
     */
    fun bindNativeAbi(abiHandle: Long): FunctionAbi = apply {
        require(abiHandle != 0L) { "abiHandle must be non-zero" }
        nativeAbi = NativeFunctionAbi(abiHandle, name)
    }

    // ---------------- High-level API ----------------

    /** Bind named arguments to this function. */
    fun withArgs(args: Map<String, Any>): FunctionAbiWithArgs =
        FunctionAbiWithArgs(this, args)

    /**
     * Calls the function locally on an account state (emulation).
     * (Left unimplemented; wire to your native/local VM executor.)
     */
    suspend fun call(
        accountState: AccountState,
        input: Map<String, Any>,
        responsible: Boolean = false,
        config: BlockchainConfig? = null
    ): ExecutionOutput {
        throw UnsupportedOperationException(
            "Local execution not wired. Use your TransactionExecutor / Native bridge."
        )
    }

    /** Encode an EXTERNAL message (see note in the class header) */
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
     * Encode EXTERNAL input body metadata + payload.
     *
     * We return:
     *  - payload = (FunctionID + Enc(args)) encoded by native (when available)
     *  - expireAt in metadata for the signing/sending layer to build v2.3 external body:
     *    Maybe(Signature) + Enc(Header) + FunctionID + Enc(args)
     */
    fun encodeExternalInput(
        input: Map<String, Any>,
        publicKey: PublicKey? = null,
        timeout: Int? = null,
        // kept for signature compatibility
        address: Address? = null
    ): UnsignedBody {
        val expireAt = nowSec() + (timeout ?: DEFAULT_TIMEOUT).toLong()

        val payload: Cell = nativeAbi?.let { nat ->
            // Delegate parameter packing (funcId + args) to native.
            nat.encodeCall(input)
        } ?: run {
            // If native is unavailable, keep the previous Kotlin path or throw.
            // Throwing is safer than producing malformed bodies.
            throw UnsupportedOperationException(
                "Native ABI is not bound. Call bindNativeAbi(parseAbi(json)) first."
            )
        }

        val hash = payload.hash()
        return UnsignedBody(
            abiVersion = abiVersion,
            payload = payload, // funcId + args (headers/signature are NOT embedded here)
            hash = hash,
            expireAt = expireAt
        )
    }

    /** Encode an INTERNAL message (full native path). */
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
            createdLt = 0,
            createdAt = nowSec().toInt()
        )
        val messageCell = buildMessageCell(header, body, stateInit)
        val hash = messageCell.hash()
        return Message(
            hash = hash,
            header = header,
            body = body,
            stateInit = stateInit
        )
    }

    /** Encode INTERNAL input body = (FunctionID + Enc(args)) using native when available. */
    fun encodeInternalInput(input: Map<String, Any>): Cell {
        nativeAbi?.let { return it.encodeCall(input) }
        throw UnsupportedOperationException(
            "Native ABI is not bound. Call bindNativeAbi(parseAbi(json)) first."
        )
    }

    /** Try to decode a transaction’s inbound body assuming it targets this function. */
    fun decodeTransaction(transaction: Transaction): Map<String, Any>? {
        val inMsg = transaction.inMsg ?: return null
        val body = inMsg.body ?: return null
        return decodeInput(body, internal = inMsg.isInternal())
    }

    /**
     * Decode input parameters from a message body.
     * JNI surface doesn’t provide input-decoding; keep Kotlin path or return empty.
     */
    fun decodeInput(body: Cell, internal: Boolean = false): Map<String, Any> {
        // If you need this, route through your executor or extend JNI with a decodeInput call.
        // Returning empty keeps current call sites safe and explicit.
        return emptyMap()
    }

    /**
     * Decode output parameters from a message body.
     * Fully native path via Native.decodeFunctionOutput (JSON) -> Map<String, Any>.
     */
    fun decodeOutput(body: Cell, internal: Boolean = false): Map<String, Any> {
        val nat = nativeAbi ?: throw UnsupportedOperationException(
            "Native ABI is not bound. Call bindNativeAbi(parseAbi(json)) first."
        )
        val json = nat.decodeOutputJson(body)
        if (json.isEmpty()) return emptyMap()
        return json.mapValues { (_, v) -> jsonToKotlin(v) }
    }

    /** True if the function has outputs. */
    fun hasOutput(): Boolean = outputs.isNotEmpty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as FunctionAbi
        return name == other.name && inputId == other.inputId && outputId == other.outputId
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + inputId
        result = 31 * result + outputId
        return result
    }

    override fun toString(): String =
        "FunctionAbi(name='$name', inputId=0x${inputId.toString(16)}, outputId=0x${outputId.toString(16)})"

    // ----------------------------- internals -----------------------------

    companion object {
        /** Default message timeout in seconds. */
        const val DEFAULT_TIMEOUT: Int = 60

        private fun nowSec(): Long =
            TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

        /**
         * Build a message cell from components (header/body/stateInit).
         * This is a placeholder: real on-chain envelope layout depends on message type.
         * If you need bit-accurate envelopes for hashing/signing, wire this to JNI, too.
         */
        internal fun buildMessageCell(header: MessageHeader, body: Cell?, stateInit: StateInit?): Cell {
            // Best-effort: if body exists, hash that (what matters for function calls)
            if (body != null) return body
            val b = CellBuilder.create()
            b.writeUint(1, 1) // non-empty marker
            return b.build()
        }

        /** Minimal JSON -> Kotlin conversion for output decoding. */
        private fun jsonToKotlin(e: JsonElement): Any = when (e) {
            is JsonNull -> Unit
            is JsonPrimitive -> when {
                e.isString -> e.content
                e.booleanOrNull != null -> e.boolean
                e.longOrNull != null -> e.long
                e.doubleOrNull != null -> e.double
                else -> e.toString()
            }
            is JsonArray -> e.map { jsonToKotlin(it) }
            is JsonObject -> e.mapValues { jsonToKotlin(it.value) }
        }
    }
}

/** A function ABI with bound arguments. */
@Serializable
data class FunctionAbiWithArgs(
    val abi: FunctionAbi,
    val args: Map<String, @Contextual Any>
) {
    suspend fun call(
        accountState: AccountState,
        responsible: Boolean = false,
        config: BlockchainConfig? = null
    ): ExecutionOutput = abi.call(accountState, args, responsible, config)

    fun encodeExternalMessage(
        dst: Address,
        publicKey: PublicKey? = null,
        stateInit: StateInit? = null,
        timeout: Int? = null
    ): UnsignedExternalMessage = abi.encodeExternalMessage(dst, args, publicKey, stateInit, timeout)

    fun encodeExternalInput(
        publicKey: PublicKey? = null,
        timeout: Int? = null,
        address: Address? = null
    ): UnsignedBody = abi.encodeExternalInput(args, publicKey, timeout, address)

    fun encodeInternalMessage(
        value: Tokens,
        bounce: Boolean,
        dst: Address,
        src: Address? = null,
        stateInit: StateInit? = null
    ): Message = abi.encodeInternalMessage(args, value, bounce, dst, src, stateInit)

    fun encodeInternalInput(): Cell = abi.encodeInternalInput(args)

    override fun toString(): String = "FunctionAbiWithArgs(${abi.name}, args=${args.keys})"
}

/** Result of a function emulation. */
@Serializable
data class ExecutionOutput(
    val exitCode: Int,
    val output: Map<String, @Contextual Any>?
) {
    fun isSuccess(): Boolean = exitCode == 0
    override fun toString(): String = "ExecutionOutput(exitCode=$exitCode, hasOutput=${output != null})"
}

/** Unsigned external message (ready to sign & send). */
@Serializable
data class UnsignedExternalMessage(
    val dst: Address,
    val stateInit: StateInit?,
    val body: UnsignedBody
) {
    fun expireAt(): Long = body.expireAt
    override fun toString(): String = "UnsignedExternalMessage(dst=$dst, expireAt=${expireAt()})"
}

/** Unsigned message body (payload + metadata). */
@Serializable
data class UnsignedBody(
    val abiVersion: AbiVersion,
    val payload: Cell,    // (FunctionID + Enc(args)); headers/signature are NOT embedded here
    val hash: ByteArray,
    val expireAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UnsignedBody
        return abiVersion == other.abiVersion &&
                payload == other.payload &&
                hash.contentEquals(other.hash) &&
                expireAt == other.expireAt
    }

    override fun hashCode(): Int {
        var result = abiVersion.hashCode()
        result = 31 * result + payload.hashCode()
        result = 31 * result + hash.contentHashCode()
        result = 31 * result + expireAt.hashCode()
        return result
    }

    override fun toString(): String =
        "UnsignedBody(expireAt=$expireAt, hashHex=${hash.joinToString("") { "%02x".format(it) }})"
}
