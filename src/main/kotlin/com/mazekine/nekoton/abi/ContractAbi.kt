package com.mazekine.nekoton.abi

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.crypto.PublicKey
import com.mazekine.nekoton.models.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
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
    
    init {
        if (Native.isInitialized()) {
            try {
                // We would need the original JSON for native parsing
                // For now, just mark as not initialized
                isNativeInitialized = false
            } catch (e: Exception) {
                isNativeInitialized = false
            }
        }
    }
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
    fun decodeInitData(data: Cell): Pair<PublicKey?, Map<String, Any>> {
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
        val decodedData = mutableMapOf<String, Any>()
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
    fun decodeFields(data: Any, allowPartial: Boolean = false): Map<String, Any> {
        val cell = when (data) {
            is Cell -> data
            is AccountState -> data.getData() ?: throw IllegalArgumentException("Account state without data")
            else -> throw IllegalArgumentException("Unsupported data type")
        }
        
        val slice = cell.beginParse()
        val decodedFields = mutableMapOf<String, Any>()
        
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
        val output = transaction.outMsgs
            .mapNotNull { msg -> msg.body?.let { body -> 
                if (extractFunctionId(body) == function.outputId) {
                    function.decodeOutput(body, false)
                } else null
            }}
            .firstOrNull() ?: emptyMap()
        
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
         * Parses ABI JSON string into ContractAbi.
         */
        private fun parseAbiJson(abiJson: String): ContractAbi {
            val json = Json.parseToJsonElement(abiJson).jsonObject
            
            // Parse ABI version
            val abiVersion = json["ABI version"]?.let { 
                AbiVersion(it.toString().toInt()) 
            } ?: AbiVersion(1)
            
            // Parse functions
            val functions = mutableMapOf<String, FunctionAbi>()
            json["functions"]?.let { functionsJson ->
                // Parse function definitions
                TODO("Function parsing not yet implemented")
            }
            
            // Parse events
            val events = mutableMapOf<String, EventAbi>()
            json["events"]?.let { eventsJson ->
                // Parse event definitions
                TODO("Event parsing not yet implemented")
            }
            
            // Parse data fields
            val data = mutableMapOf<String, AbiParam>()
            json["data"]?.let { dataJson ->
                // Parse data field definitions
                TODO("Data field parsing not yet implemented")
            }
            
            return ContractAbi(abiVersion, functions, events, data)
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
         * Extracts function ID from message body.
         */
        private fun extractFunctionId(body: Cell): Int? {
            // This would require the actual function ID extraction logic
            TODO("Function ID extraction not yet implemented")
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
    override fun toString(): String {
        return "FunctionCall(${function.name}, input=${input.keys}, output=${output.keys})"
    }
}
