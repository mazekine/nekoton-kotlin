package com.mazekine.nekoton.abi

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.crypto.PublicKey
import com.mazekine.nekoton.models.*
import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Represents a contract ABI (Application Binary Interface) for TON/Everscale smart contracts.
 * 
 * The ABI defines the interface for interacting with smart contracts, including
 * function signatures, parameter types, and event definitions.
 * 
 * @property abiVersion The ABI version
 * @property functions Map of function names to their ABI definitions
 * @property events Map of event names to their ABI definitions
 * @property data Map of data field names to their definitions
 */
@Serializable
data class ContractAbi(
    val abiVersion: AbiVersion,
    val functions: Map<String, FunctionAbi>,
    val events: Map<String, EventAbi>,
    val data: Map<String, AbiParam> = emptyMap()
) {
    private var nativeHandle: Long = 0
    private var isNativeInitialized = false

    /**
     * Creates a ContractAbi from JSON string.
     * 
     * @param abiJson The ABI definition as JSON string
     */
    constructor(abiJson: String) : this(
        parseAbiJson(abiJson).abiVersion,
        parseAbiJson(abiJson).functions,
        parseAbiJson(abiJson).events,
        parseAbiJson(abiJson).data
    ) {
        if (Native.isInitialized()) {
            try {
                nativeHandle = Native.parseAbi(abiJson)
                isNativeInitialized = nativeHandle != 0L
            } catch (e: Exception) {
                isNativeInitialized = false
            }
        }
    }

    /**
     * Gets a function ABI by name.
     * 
     * @param name The function name
     * @return FunctionAbi instance or null if not found
     */
    fun getFunction(name: String): FunctionAbi? {
        return if (isNativeInitialized) {
            try {
                val functionNamesBytes = Native.getAbiFunctionNames(nativeHandle)
                val functionNamesJson = String(functionNamesBytes)
                val functionNames = Json.decodeFromString<List<String>>(functionNamesJson)
                
                if (functionNames.contains(name)) {
                    // For now, return the regular function ABI since NativeFunctionAbi doesn't extend FunctionAbi
                    functions[name]
                } else {
                    null
                }
            } catch (e: Exception) {
                functions[name]
            }
        } else {
            functions[name]
        }
    }

    /**
     * Gets an event ABI by name.
     * 
     * @param name The event name
     * @return EventAbi instance or null if not found
     */
    fun getEvent(name: String): EventAbi? = events[name]

    /**
     * Encodes initial data for contract deployment.
     * 
     * @param data Map of parameter names to values
     * @param publicKey Optional public key to include
     * @param existingData Optional existing data cell to merge with
     * @return Cell containing the encoded initial data
     */
    fun encodeInitData(
        data: Map<String, Any>,
        publicKey: PublicKey? = null,
        existingData: Cell? = null
    ): Cell {
        val builder = CellBuilder.create()
        
        // Add public key if provided
        publicKey?.let { key ->
            builder.writeBytes(key.toBytes())
        }
        
        // Add data parameters
        for ((paramName, param) in this.data) {
            val value = data[paramName] ?: throw IllegalArgumentException("Parameter '$paramName' not found")
            encodeParam(builder, param, value)
        }
        
        return builder.build()
    }

    /**
     * Decodes initial data from a contract.
     * 
     * @param data The data cell to decode
     * @return Pair of optional public key and decoded parameters
     */
    fun decodeInitData(data: Cell): Pair<PublicKey?, Map<String, Any?>> {
        val slice = data.beginParse()
        
        // Try to read public key (first 32 bytes)
        val publicKey = if (slice.remainingBits >= 256) {
            val keyBytes = slice.readBytes(32)
            if (keyBytes.all { it == 0.toByte() }) {
                null
            } else {
                PublicKey.fromBytes(keyBytes)
            }
        } else {
            null
        }
        
        // Decode data parameters
        val decodedData = mutableMapOf<String, Any?>()
        for ((paramName, param) in this.data) {
            decodedData[paramName] = decodeParam(slice, param)
        }
        
        return Pair(publicKey, decodedData)
    }

    /**
     * Decodes fields from account state or data cell.
     * 
     * @param data The data source (Cell or AccountState)
     * @param allowPartial Whether to allow partial decoding
     * @return Map of decoded field values
     */
    fun decodeFields(data: Any, allowPartial: Boolean = false): Map<String, Any?> {
        val cell = when (data) {
            is Cell -> data
            is AccountState -> data.getData() ?: throw IllegalArgumentException("Account state without data")
            else -> throw IllegalArgumentException("Unsupported data type")
        }
        
        val slice = cell.beginParse()
        val decodedFields = mutableMapOf<String, Any?>()
        
        for ((fieldName, param) in this.data) {
            try {
                decodedFields[fieldName] = decodeParam(slice, param)
            } catch (e: Exception) {
                if (!allowPartial) throw e
                // Skip this field if partial decoding is allowed
            }
        }
        
        return decodedFields
    }

    /**
     * Decodes a transaction to extract function call information.
     * 
     * @param transaction The transaction to decode
     * @return FunctionCall instance or null if not a function call
     */
    fun decodeTransaction(transaction: Transaction): FunctionCall? {
        val inMsg = transaction.inMsg ?: return null
        val body = inMsg.body ?: return null
        
        // Try to identify the function by its signature
        val functionId = extractFunctionId(body) ?: return null
        val function = functions.values.find { it.inputId == functionId } ?: return null
        
        // Decode input parameters
        val input = function.decodeInput(body, inMsg.isInternal())
        
        // Look for output in outgoing messages
        val output = transaction.outMsgs.firstNotNullOfOrNull { msg ->
            msg.body?.let { body ->
                if (extractFunctionId(body) == function.outputId) {
                    function.decodeOutput(body, false)
                } else null
            }
        } ?: emptyMap()
        
        return FunctionCall(
            function = function,
            input = input,
            output = output
        )
    }

    /**
     * Decodes transaction events.
     * 
     * @param transaction The transaction to decode
     * @return List of decoded events
     */
    fun decodeTransactionEvents(transaction: Transaction): List<Pair<EventAbi, Map<String, Any>>> {
        val decodedEvents = mutableListOf<Pair<EventAbi, Map<String, Any>>>()
        
        for (outMsg in transaction.outMsgs) {
            if (!outMsg.isExternalOut()) continue
            
            val body = outMsg.body ?: continue
            val eventId = extractFunctionId(body) ?: continue
            
            val event = events.values.find { it.id == eventId } ?: continue
            val eventData = event.decodeInput(body.beginParse())
            
            decodedEvents.add(Pair(event, eventData))
        }
        
        return decodedEvents
    }

    /**
     * Provides a concise textual summary of the ABI.
     */
    override fun toString(): String {
        return "ContractAbi(version=$abiVersion, functions=${functions.size}, events=${events.size})"
    }

    companion object {
        /**
         * Creates a ContractAbi from a file.
         * 
         * @param file The ABI file
         * @return ContractAbi instance
         */
        fun fromFile(file: File): ContractAbi {
            val abiJson = file.readText()
            return ContractAbi(abiJson)
        }

        /**
         * Creates a ContractAbi from a file path.
         * 
         * @param path The path to the ABI file
         * @return ContractAbi instance
         */
        fun fromFile(path: String): ContractAbi = fromFile(File(path))

        /**
         * Parses ABI JSON string into a [ContractAbi] instance.
         *
         * @param abiJson ABI definition in JSON format
         * @return Parsed [ContractAbi]
         */
        private fun parseAbiJson(abiJson: String): ContractAbi {
            val json = Json.parseToJsonElement(abiJson).jsonObject
            
            // Parse ABI version
            val abiVersion = json["ABI version"]?.let { 
                AbiVersion(it.toString().toInt()) 
            } ?: AbiVersion(1)
            
            // Parse functions
            val functions = mutableMapOf<String, FunctionAbi>()
            json["functions"]?.jsonArray?.forEach { fnElem ->
                val fnObj = fnElem.jsonObject
                val name = fnObj["name"]?.jsonPrimitive?.content ?: return@forEach
                val inputs = fnObj["inputs"]?.jsonArray?.map { parseParam(it.jsonObject) } ?: emptyList()
                val outputs = fnObj["outputs"]?.jsonArray?.map { parseParam(it.jsonObject) } ?: emptyList()

                val idString = fnObj["id"]?.jsonPrimitive?.content
                val inputId = idString?.let { parseId(it) } ?: computeId(name)
                val outputId = fnObj["outputId"]?.jsonPrimitive?.content?.let { parseId(it) } ?: inputId

                val functionAbi = FunctionAbi(
                    abiVersion = abiVersion,
                    name = name,
                    inputId = inputId,
                    outputId = outputId,
                    inputs = inputs,
                    outputs = outputs
                )
                functions[name] = functionAbi
            }

            // Parse events
            val events = mutableMapOf<String, EventAbi>()
            json["events"]?.jsonArray?.forEach { evElem ->
                val evObj = evElem.jsonObject
                val name = evObj["name"]?.jsonPrimitive?.content ?: return@forEach
                val inputs = evObj["inputs"]?.jsonArray?.map { parseParam(it.jsonObject) } ?: emptyList()
                val id = evObj["id"]?.jsonPrimitive?.content?.let { parseId(it) } ?: computeId(name)

                val eventAbi = EventAbi(
                    abiVersion = abiVersion,
                    name = name,
                    id = id,
                    inputs = inputs
                )
                events[name] = eventAbi
            }

            // Parse data fields
            val data = mutableMapOf<String, AbiParam>()
            json["data"]?.jsonArray?.forEach { dataElem ->
                val obj = dataElem.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                val type = obj["type"]?.jsonPrimitive?.content ?: return@forEach
                val components = obj["components"]?.jsonArray?.map { parseParam(it.jsonObject) }
                data[name] = AbiParam(name, type, components)
            }

            return ContractAbi(abiVersion, functions, events, data)
        }

        /**
         * Encodes a parameter value into a [CellBuilder].
         *
         * @param builder Builder to write encoded data to
         * @param param ABI parameter description
         * @param value Value to encode; may be `null` for optional parameters
         */
        @Suppress("UNCHECKED_CAST")
        private fun encodeParam(builder: CellBuilder, param: AbiParam, value: Any?) {
            val type = AbiType.fromString(param.type)
            when (type) {
                AbiType.UINT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    val intValue = toBigInteger(value!!)
                    builder.writeUint(intValue, bits)
                }
                AbiType.INT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    val intValue = toBigInteger(value!!)
                    builder.writeInt(intValue, bits)
                }
                AbiType.BOOL -> builder.writeBit(value as Boolean)
                AbiType.BYTES -> {
                    val bytes = when (value) {
                        is ByteArray -> value
                        is String -> value.toByteArray()
                        else -> throw IllegalArgumentException("Unsupported bytes value for ${param.name}")
                    }
                    builder.writeUint(bytes.size.toLong(), 32)
                    builder.writeBytes(bytes)
                }
                AbiType.BYTES_FIXED -> {
                    val size = AbiTypeUtils.getFixedBytesSize(param.type)
                    val bytes = when (value) {
                        is ByteArray -> value
                        is String -> value.toByteArray()
                        else -> throw IllegalArgumentException("Unsupported bytes value for ${param.name}")
                    }
                    require(bytes.size == size) { "Invalid byte array size for ${param.name}" }
                    builder.writeBytes(bytes)
                }
                AbiType.STRING -> {
                    val bytes = value.toString().toByteArray()
                    // Strings are encoded with a 32-bit length prefix followed by UTF-8 bytes
                    builder.writeUint(bytes.size.toLong(), 32)
                    builder.writeBytes(bytes)
                }
                AbiType.ADDRESS -> {
                    val addr = when (value) {
                        is Address -> value
                        is String -> Address(value)
                        null -> null
                        else -> throw IllegalArgumentException("Unsupported address value for ${param.name}")
                    }
                    builder.writeAddress(addr)
                }
                AbiType.CELL -> builder.writeRef(value as Cell)
                AbiType.GRAMS -> {
                    val tokens = when (value) {
                        is Tokens -> value
                        is String -> Tokens(value)
                        is Long -> Tokens(value)
                        is Int -> Tokens(value)
                        else -> throw IllegalArgumentException("Unsupported tokens value for ${param.name}")
                    }
                    builder.writeTokens(tokens)
                }
                AbiType.TUPLE -> {
                    val map = when (value) {
                        is Map<*, *> -> value as Map<String, Any?>
                        else -> throw IllegalArgumentException("Tuple value must be a map")
                    }
                    param.components?.forEach { comp ->
                        val v = map[comp.name] ?: throw IllegalArgumentException("Missing tuple field ${comp.name}")
                        encodeParam(builder, comp, v)
                    }
                }
                AbiType.ARRAY -> {
                    val list = when (value) {
                        is List<*> -> value as List<Any?>
                        else -> throw IllegalArgumentException("Array value must be a list")
                    }
                    builder.writeUint(list.size.toLong(), 32)
                    val component = param.components?.firstOrNull() ?: AbiParam(param.name, param.getBaseType())
                    list.forEach { v -> encodeParam(builder, component, v) }
                }
                AbiType.OPTIONAL -> {
                    if (value == null) {
                        builder.writeBit(false)
                    } else {
                        builder.writeBit(true)
                        val innerType = AbiParam(param.name, AbiTypeUtils.parseOptionalType(param.type), param.components)
                        encodeParam(builder, innerType, value)
                    }
                }
                AbiType.MAP -> {
                    val map = value as Map<*, *>
                    builder.writeUint(map.size.toLong(), 32)
                    val (keyType, valueType) = AbiTypeUtils.parseMapType(param.type)
                    val keyParam = AbiParam("key", keyType)
                    val valueParam = AbiParam("value", valueType)
                    map.forEach { (k, v) ->
                        encodeParam(builder, keyParam, k!!)
                        encodeParam(builder, valueParam, v!!)
                    }
                }
            }
        }

        /**
         * Decodes a parameter value from a [CellSlice].
         *
         * @param slice Cell slice containing encoded data
         * @param param ABI parameter description
         * @return Decoded value
         */
        private fun decodeParam(slice: CellSlice, param: AbiParam): Any? {
            val type = AbiType.fromString(param.type)
            return when (type) {
                AbiType.UINT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    slice.readUint(bits)
                }
                AbiType.INT -> {
                    val bits = AbiTypeUtils.getIntegerBitSize(param.type)
                    slice.readInt(bits)
                }
                AbiType.BOOL -> slice.readBit()
                AbiType.BYTES -> {
                    val len = slice.readUint(32).intValue()
                    slice.readBytes(len)
                }
                AbiType.BYTES_FIXED -> {
                    val size = AbiTypeUtils.getFixedBytesSize(param.type)
                    slice.readBytes(size)
                }
                AbiType.STRING -> {
                    val len = slice.readUint(32).intValue()
                    String(slice.readBytes(len))
                }
                AbiType.ADDRESS -> slice.readAddress()
                AbiType.CELL -> slice.readRef()
                AbiType.GRAMS -> Tokens(slice.readVarUint(4))
                AbiType.TUPLE -> {
                    val map = mutableMapOf<String, Any?>()
                    param.components?.forEach { comp ->
                        map[comp.name] = decodeParam(slice, comp)
                    }
                    map
                }
                AbiType.ARRAY -> {
                    val length = slice.readUint(32).intValue()
                    val list = mutableListOf<Any?>()
                    val component = param.components?.firstOrNull() ?: AbiParam(param.name, param.getBaseType())
                    repeat(length) { list.add(decodeParam(slice, component)) }
                    list
                }
                AbiType.OPTIONAL -> {
                    val has = slice.readBit()
                    if (!has) null else {
                        val innerType = AbiParam(param.name, AbiTypeUtils.parseOptionalType(param.type), param.components)
                        decodeParam(slice, innerType)
                    }
                }
                AbiType.MAP -> {
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

        /**
         * Extracts the function identifier from a message body.
         *
         * The TON ABI stores the function ID in the first 32 bits of the
         * message body. If fewer than 32 bits are available the message is
         * considered not to contain a function call and `null` is returned.
         *
         * @param body Cell containing the message body
         * @return Function ID or `null` if the body is too short
         */
        private fun extractFunctionId(body: Cell): Int? {
            val slice = body.beginParse()
            // Function ID resides in the first 32 bits of the body
            return if (slice.remainingBits >= 32) slice.readUint(32).intValue() else null
        }

        // Helper functions

        /**
         * Converts a JSON object describing a parameter into an [AbiParam] instance.
         *
         * @param obj JSON object containing `name`, `type` and optional `components`
         * @return Parsed ABI parameter
         */
        private fun parseParam(obj: JsonObject): AbiParam {
            val name = obj["name"]?.jsonPrimitive?.content ?: ""
            val type = obj["type"]?.jsonPrimitive?.content ?: ""
            val components = obj["components"]?.jsonArray?.map { parseParam(it.jsonObject) }
            return AbiParam(name, type, components)
        }

        /**
         * Parses a hexadecimal identifier string into an integer.
         *
         * The `0x` prefix is optional.
         *
         * @param id Identifier string
         * @return Integer representation of the identifier
         */
        private fun parseId(id: String): Int = id.removePrefix("0x").toLong(16).toInt()

        /**
         * Computes an identifier for a function or event name using CRC32.
         *
         * @param name Function or event name
         * @return Calculated identifier
         */
        private fun computeId(name: String): Int {
            val crc = java.util.zip.CRC32()
            crc.update(name.toByteArray())
            return crc.value.toInt()
        }

        /**
         * Converts various numeric representations to [BigInteger].
         *
         * @param value Numeric value in one of the supported types
         * @return [BigInteger] representation of the value
         */
        private fun toBigInteger(value: Any): BigInteger = when (value) {
            is BigInteger -> value
            is Long -> BigInteger.fromLong(value)
            is Int -> BigInteger.fromInt(value)
            is String -> BigInteger.parseString(value)
            is Number -> BigInteger.fromLong(value.toLong())
            else -> throw IllegalArgumentException("Unsupported numeric value: $value")
        }
    }
}

/**
 * Represents an ABI version.
 * 
 * @property version The version number
 */
@Serializable
data class AbiVersion(val version: Int) {
    /**
     * Returns the version number as a string.
     */
    override fun toString(): String = version.toString()
}

/**
 * Represents a function call with input and output data.
 * 
 * @property function The function ABI
 * @property input Input parameters
 * @property output Output parameters
 */
@Serializable
data class FunctionCall(
    val function: FunctionAbi,
    val input: Map<String, @Contextual Any>,
    val output: Map<String, @Contextual Any>
){
    /**
     * Returns a human-readable representation of this call.
     */
    override fun toString(): String {
        return "FunctionCall(${function.name}, input=${input.keys}, output=${output.keys})"
    }
}
