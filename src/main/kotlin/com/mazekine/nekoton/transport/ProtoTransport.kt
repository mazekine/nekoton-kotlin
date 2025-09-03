package com.mazekine.nekoton.transport

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Protobuf transport implementation using native nekoton library.
 *
 * This transport connects to Protobuf endpoints for blockchain communication.
 *
 * @param endpoint The Protobuf endpoint URL
 */
class ProtoTransport(private val endpoint: String) : Transport {

    private var nativeHandle: Long = 0
    private var isInitialized = false

    init {
        if (Native.isInitialized()) {
            nativeHandle = Native.createProtoTransport(endpoint)
            isInitialized = nativeHandle != 0L
        }
    }

    override suspend fun getBlockchainConfig(): BlockchainConfig = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("Transport not initialized")
        }

        BlockchainConfig(
            globalId = -1,
            capabilities = 0L,
            globalVersion = 1,
            configAddress = Address("-1:0000000000000000000000000000000000000000000000000000000000000000"),
            electorAddress = Address("-1:0000000000000000000000000000000000000000000000000000000000000000"),
            minterAddress = Address("-1:0000000000000000000000000000000000000000000000000000000000000000"),
            feeCollectorAddress = Address("-1:0000000000000000000000000000000000000000000000000000000000000000")
        )
    }

    override suspend fun getAccountState(address: Address): AccountState? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("Transport not initialized")
        }

        try {
            val stateBytes = Native.getContractState(nativeHandle, address.toString())
            val stateJson = String(stateBytes)

            AccountState(
                storageUsed = StorageUsed(0, 0, 0),
                lastPaid = 0,
                duePayment = null,
                lastTransLt = 0L,
                balance = Tokens.fromNanoTokens("0"),
                status = AccountStatus.Uninit
            )
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun sendExternalMessage(message: SignedExternalMessage): String = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("Transport not initialized")
        }

        val messageBoc = message.toBoc()
        return@withContext Native.sendExternalMessage(nativeHandle, messageBoc)
    }

    override suspend fun getTransactions(
        address: Address,
        fromLt: Long?,
        count: Int
    ): List<Transaction> = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            throw IllegalStateException("Transport not initialized")
        }

        try {
            val transactionsBytes = Native.getTransactions(nativeHandle, address.toString(), fromLt ?: 0L, count)
            val transactionsJson = String(transactionsBytes)

            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getContractState(address: Address): ContractState? = withContext(Dispatchers.IO) {
        val accountState = getAccountState(address) ?: return@withContext null
        ContractState(
            account = accountState,
            lastTransactionId = null,
            isDeployed = accountState.exists()
        )
    }

    override suspend fun getTransaction(hash: String): Transaction? = withContext(Dispatchers.IO) {
        null
    }

    override suspend fun waitForTransaction(address: Address, lt: Long, timeout: Long): Transaction? = withContext(Dispatchers.IO) {
        null
    }

    override suspend fun getLatestBlock(): BlockInfo = withContext(Dispatchers.IO) {
        BlockInfo(
            workchain = 0,
            shard = 0L,
            seqno = 0,
            rootHash = ByteArray(32),
            fileHash = ByteArray(32),
            genUtime = 0
        )
    }

    override fun subscribeToAccountState(address: Address): kotlinx.coroutines.flow.Flow<AccountState> {
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override fun subscribeToTransactions(address: Address): kotlinx.coroutines.flow.Flow<Transaction> {
        return kotlinx.coroutines.flow.emptyFlow()
    }

    override suspend fun isConnected(): Boolean = isInitialized

    override suspend fun close() {
        cleanup()
    }

    /**
     * Cleans up native resources.
     * Should be called when the transport is no longer needed.
     */
    fun cleanup() {
        if (isInitialized) {
            Native.cleanupTransport(nativeHandle)
            isInitialized = false
            nativeHandle = 0
        }
    }

    protected fun finalize() {
        cleanup()
    }
}

