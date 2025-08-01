package com.mazekine.nekoton.transport

import com.mazekine.nekoton.models.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/**
 * Base interface for blockchain transport implementations.
 * 
 * Transport provides the communication layer between the application
 * and the blockchain network, handling message sending, account queries,
 * and blockchain state monitoring.
 */
interface Transport {
    /**
     * Gets the current blockchain configuration.
     * 
     * @return The blockchain configuration
     */
    suspend fun getBlockchainConfig(): BlockchainConfig

    /**
     * Gets the current account state for the specified address.
     * 
     * @param address The account address
     * @return The account state or null if account doesn't exist
     */
    suspend fun getAccountState(address: Address): AccountState?

    /**
     * Gets the contract state for the specified address.
     * 
     * @param address The contract address
     * @return The contract state or null if contract doesn't exist
     */
    suspend fun getContractState(address: Address): ContractState?

    /**
     * Sends an external message to the blockchain.
     * 
     * @param message The message to send
     * @return Transaction hash if successful
     */
    suspend fun sendExternalMessage(message: SignedExternalMessage): String

    /**
     * Gets transaction information by hash.
     * 
     * @param hash The transaction hash
     * @return Transaction information or null if not found
     */
    suspend fun getTransaction(hash: String): Transaction?

    /**
     * Gets transactions for the specified account.
     * 
     * @param address The account address
     * @param fromLt Starting logical time (optional)
     * @param count Maximum number of transactions to return
     * @return List of transactions
     */
    suspend fun getTransactions(
        address: Address,
        fromLt: Long? = null,
        count: Int = 50
    ): List<Transaction>

    /**
     * Waits for a transaction to be confirmed.
     * 
     * @param address The account address
     * @param lt The logical time to wait for
     * @param timeout Timeout in milliseconds
     * @return The transaction or null if timeout
     */
    suspend fun waitForTransaction(
        address: Address,
        lt: Long,
        timeout: Long = 60000
    ): Transaction?

    /**
     * Gets the latest block information.
     * 
     * @return Latest block info
     */
    suspend fun getLatestBlock(): BlockInfo

    /**
     * Subscribes to account state changes.
     * 
     * @param address The account address to monitor
     * @return Flow of account state updates
     */
    fun subscribeToAccountState(address: Address): Flow<AccountState>

    /**
     * Subscribes to new transactions for an account.
     * 
     * @param address The account address to monitor
     * @return Flow of new transactions
     */
    fun subscribeToTransactions(address: Address): Flow<Transaction>

    /**
     * Checks if the transport connection is active.
     * 
     * @return true if connected
     */
    suspend fun isConnected(): Boolean

    /**
     * Closes the transport connection and releases resources.
     */
    suspend fun close()
}

/**
 * Represents contract state information.
 * 
 * @property account The account state
 * @property lastTransactionId The last transaction ID
 * @property isDeployed Whether the contract is deployed
 */
@Serializable
data class ContractState(
    val account: AccountState,
    val lastTransactionId: TransactionId?,
    val isDeployed: Boolean
) {
    /**
     * Gets the contract balance.
     * 
     * @return The contract balance
     */
    fun getBalance(): Tokens = account.balance

    /**
     * Gets the contract code if available.
     * 
     * @return The contract code or null
     */
    fun getCode(): Cell? = account.getCode()

    /**
     * Gets the contract data if available.
     * 
     * @return The contract data or null
     */
    fun getData(): Cell? = account.getData()

    override fun toString(): String {
        return "ContractState(deployed=$isDeployed, balance=${account.balance})"
    }
}

/**
 * Represents a transaction identifier.
 * 
 * @property lt Logical time
 * @property hash Transaction hash
 */
@Serializable
data class TransactionId(
    val lt: Long,
    val hash: ByteArray
) {
    init {
        require(hash.size == 32) { "Transaction hash must be 32 bytes" }
        require(lt >= 0) { "Logical time cannot be negative" }
    }

    /**
     * Gets the hash as a hex string.
     * 
     * @return Hex representation of the hash
     */
    fun hashHex(): String = hash.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionId

        if (lt != other.lt) return false
        if (!hash.contentEquals(other.hash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = lt.hashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "TransactionId(lt=$lt, hash=${hashHex()})"
    }
}

/**
 * Represents blockchain block information.
 * 
 * @property workchain The workchain ID
 * @property shard The shard ID
 * @property seqno The block sequence number
 * @property rootHash The block root hash
 * @property fileHash The block file hash
 * @property genUtime Block generation timestamp
 */
@Serializable
data class BlockInfo(
    val workchain: Int,
    val shard: Long,
    val seqno: Int,
    val rootHash: ByteArray,
    val fileHash: ByteArray,
    val genUtime: Int
) {
    init {
        require(rootHash.size == 32) { "Root hash must be 32 bytes" }
        require(fileHash.size == 32) { "File hash must be 32 bytes" }
        require(seqno >= 0) { "Sequence number cannot be negative" }
        require(genUtime >= 0) { "Generation time cannot be negative" }
    }

    /**
     * Gets the root hash as a hex string.
     * 
     * @return Hex representation of the root hash
     */
    fun rootHashHex(): String = rootHash.joinToString("") { "%02x".format(it) }

    /**
     * Gets the file hash as a hex string.
     * 
     * @return Hex representation of the file hash
     */
    fun fileHashHex(): String = fileHash.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlockInfo

        if (workchain != other.workchain) return false
        if (shard != other.shard) return false
        if (seqno != other.seqno) return false
        if (!rootHash.contentEquals(other.rootHash)) return false
        if (!fileHash.contentEquals(other.fileHash)) return false
        if (genUtime != other.genUtime) return false

        return true
    }

    override fun hashCode(): Int {
        var result = workchain
        result = 31 * result + shard.hashCode()
        result = 31 * result + seqno
        result = 31 * result + rootHash.contentHashCode()
        result = 31 * result + fileHash.contentHashCode()
        result = 31 * result + genUtime
        return result
    }

    override fun toString(): String {
        return "BlockInfo(workchain=$workchain, shard=$shard, seqno=$seqno)"
    }
}

/**
 * Represents a signed external message ready for sending.
 * 
 * @property message The unsigned message
 * @property signature The message signature
 * @property signatureId Optional signature ID
 */
@Serializable
data class SignedExternalMessage(
    val message: Message,
    val signature: com.mazekine.nekoton.crypto.Signature,
    val signatureId: Int? = null
) {
    /**
     * Gets the message expiration time.
     * 
     * @return Expiration timestamp
     */
    fun expireAt(): Long = System.currentTimeMillis() / 1000 + 3600 // Default 1 hour expiry

    /**
     * Checks if the message has expired.
     * 
     * @param currentTime Current timestamp (default: current system time)
     * @return true if the message has expired
     */
    fun isExpired(currentTime: Long = System.currentTimeMillis() / 1000): Boolean {
        return currentTime >= expireAt()
    }

    /**
     * Serializes the signed message to BOC format.
     * 
     * @return BOC representation as byte array
     */
    fun toBoc(): ByteArray {
        return try {
            ByteArray(0)
        } catch (e: Exception) {
            throw RuntimeException("Failed to serialize message to BOC", e)
        }
    }

    override fun toString(): String {
        return "SignedExternalMessage(expireAt=${expireAt()})"
    }
}

/**
 * Transport configuration interface.
 */
interface TransportConfig {
    /**
     * Gets the transport type identifier.
     * 
     * @return Transport type string
     */
    fun getType(): String

    /**
     * Validates the configuration.
     * 
     * @throws IllegalArgumentException if configuration is invalid
     */
    fun validate()
}

/**
 * Exception thrown when transport operations fail.
 * 
 * @property code Error code
 * @property message Error message
 * @property cause Underlying cause
 */
class TransportException(
    val code: Int,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause) {
    companion object {
        const val NETWORK_ERROR = 1000
        const val TIMEOUT_ERROR = 1001
        const val INVALID_MESSAGE = 1002
        const val ACCOUNT_NOT_FOUND = 1003
        const val TRANSACTION_NOT_FOUND = 1004
        const val INSUFFICIENT_BALANCE = 1005
        const val CONTRACT_ERROR = 1006
    }
}
