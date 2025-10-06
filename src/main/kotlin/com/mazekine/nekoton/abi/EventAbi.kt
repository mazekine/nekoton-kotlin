package com.mazekine.nekoton.abi

import com.mazekine.nekoton.abi.param.AbiParam
import com.mazekine.nekoton.abi.param.AbiParamType
import com.mazekine.nekoton.abi.param.AbiTypeUtils
import com.mazekine.nekoton.models.Cell
import com.mazekine.nekoton.models.CellSlice
import com.mazekine.nekoton.models.Message
import com.mazekine.nekoton.models.Tokens
import kotlinx.serialization.Serializable

/**
 * Represents an event ABI definition for TON/Tycho smart contracts.
 * 
 * Events are emitted by smart contracts to signal that certain actions
 * have occurred. This class provides functionality to decode event data
 * from blockchain messages.
 * 
 * @property abiVersion The ABI version this event uses
 * @property name The event name
 * @property id The event ID (used for identification in messages)
 * @property inputs List of event parameters
 */
@Serializable
data class EventAbi(
    val abiVersion: AbiVersion,
    val name: String,
    val id: Int,
    val inputs: List<AbiParam>
) {
    /**
     * Decodes event data from a message.
     * 
     * @param message The message containing the event
     * @return Decoded event parameters
     * @throws IllegalArgumentException if the message doesn't contain this event
     */
    fun decodeMessage(message: Message): Map<String, Any?> {
        val body = message.body ?: throw IllegalArgumentException("Message has no body")
        return decodeMessageBody(body)
    }

    /**
     * Decodes event data from a message body.
     * 
     * @param body The message body cell
     * @return Decoded event parameters
     * @throws IllegalArgumentException if the body doesn't contain this event
     */
    fun decodeMessageBody(body: Cell): Map<String, Any?> {
        val slice = body.beginParse()

        // Read and verify event ID
        val eventId = slice.readUint(32).intValue()
        require(eventId == id) { "Event ID mismatch: expected $id, got $eventId" }
        
        // Decode event parameters
        val decodedParams = mutableMapOf<String, Any?>()
        for (param in inputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }

        return decodedParams
    }

    /**
     * Decodes input parameters from a cell slice.
     * 
     * @param slice The cell slice to read from
     * @return Decoded event parameters
     */
    fun decodeInput(slice: CellSlice): Map<String, Any?> {
        val decodedParams = mutableMapOf<String, Any?>()
        for (param in inputs) {
            decodedParams[param.name] = decodeParam(slice, param)
        }
        return decodedParams
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EventAbi

        if (name != other.name) return false
        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + id
        return result
    }

    override fun toString(): String {
        return "EventAbi(name='$name', id=0x${id.toString(16)})"
    }

    companion object {
        /**
         * Decodes a parameter value from a cell slice.
         */
        private fun decodeParam(slice: CellSlice, param: AbiParam): Any? {
            val type = AbiParamType.fromString(param.type)
            return when (type) {
                AbiParamType.UINT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    slice.readUint(bits)
                }
                AbiParamType.INT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    slice.readInt(bits)
                }
                AbiParamType.BOOL -> slice.readBit()
                AbiParamType.BYTES -> {
                    val len = slice.readUint(32).intValue()
                    slice.readBytes(len)
                }
                AbiParamType.BYTES_FIXED -> {
                    val size = AbiTypeUtils.getFixedBytesSize(param.type)
                    slice.readBytes(size)
                }
                AbiParamType.STRING -> {
                    val len = slice.readUint(32).intValue()
                    String(slice.readBytes(len))
                }
                AbiParamType.ADDRESS -> slice.readAddress()
                AbiParamType.CELL -> slice.readRef()
                AbiParamType.GRAMS -> Tokens(slice.readVarUint(4))
                AbiParamType.TUPLE -> {
                    val map = mutableMapOf<String, Any?>()
                    param.components?.forEach { comp ->
                        map[comp.name] = decodeParam(slice, comp)
                    }
                    map
                }
                AbiParamType.ARRAY -> {
                    val length = slice.readUint(32).intValue()
                    val list = mutableListOf<Any?>()
                    val component = param.components?.firstOrNull() ?: AbiParam(param.name, param.getBaseType())
                    repeat(length) { list.add(decodeParam(slice, component)) }
                    list
                }
                AbiParamType.OPTIONAL -> {
                    val has = slice.readBit()
                    if (!has) null else {
                        val innerType = AbiParam(param.name, AbiTypeUtils.parseOptionalType(param.type), param.components)
                        decodeParam(slice, innerType)
                    }
                }
                AbiParamType.MAP -> {
                    val size = slice.readUint(32).intValue()
                    val result = mutableMapOf<Any?, Any?>()
                    val (keyType, valueType) = AbiTypeUtils.parseMapType(param.type)
                    val keyParam = AbiParam("key", keyType)
                    val valueParam = AbiParam("value", valueType)
                    repeat(size) {
                        val key = decodeParam(slice, keyParam)
                        val value = decodeParam(slice, valueParam)
                        result[key] = value
                    }
                    result
                }
            }
        }
    }
}

