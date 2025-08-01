package com.mazekine.nekoton.models

import kotlinx.serialization.Serializable

/**
 * Represents the state of an account in the TON/Everscale blockchain.
 * 
 * This class contains all information about an account including its balance,
 * storage statistics, transaction history, and current state.
 * 
 * @property storageUsed Storage usage statistics
 * @property lastPaid Last time storage fees were paid
 * @property duePayment Amount of storage fees due (if any)
 * @property lastTransLt Logical time of the last transaction
 * @property balance Account balance in tokens
 * @property status Current account status
 * @property stateInit State initialization data (for active accounts)
 * @property frozenStateHash Hash of frozen state (for frozen accounts)
 */
@Serializable
data class AccountState(
    val storageUsed: StorageUsed,
    val lastPaid: Int,
    val duePayment: Tokens?,
    val lastTransLt: Long,
    val balance: Tokens,
    val status: AccountStatus,
    val stateInit: StateInit? = null,
    val frozenStateHash: ByteArray? = null
) {
    init {
        require(lastPaid >= 0) { "Last paid timestamp cannot be negative" }
        require(lastTransLt >= 0) { "Last transaction LT cannot be negative" }
        require(frozenStateHash?.size == 32 || frozenStateHash == null) { 
            "Frozen state hash must be 32 bytes if present" 
        }
        
        // Validate state consistency
        when (status) {
            AccountStatus.Active -> require(stateInit != null) { 
                "Active account must have state init" 
            }
            AccountStatus.Frozen -> require(frozenStateHash != null) { 
                "Frozen account must have frozen state hash" 
            }
            AccountStatus.Uninit -> {
                require(stateInit == null) { "Uninitialized account cannot have state init" }
                require(frozenStateHash == null) { "Uninitialized account cannot have frozen state hash" }
            }
        }
    }

    /**
     * Checks if the account exists (is not uninitialized).
     * 
     * @return true if the account exists
     */
    fun exists(): Boolean = status != AccountStatus.Uninit

    /**
     * Checks if the account is active and can process transactions.
     * 
     * @return true if the account is active
     */
    fun isActive(): Boolean = status == AccountStatus.Active

    /**
     * Checks if the account is frozen.
     * 
     * @return true if the account is frozen
     */
    fun isFrozen(): Boolean = status == AccountStatus.Frozen

    /**
     * Checks if the account is uninitialized.
     * 
     * @return true if the account is uninitialized
     */
    fun isUninit(): Boolean = status == AccountStatus.Uninit

    /**
     * Gets the account code if available.
     * 
     * @return The contract code or null
     */
    fun getCode(): Cell? = stateInit?.code

    /**
     * Gets the account data if available.
     * 
     * @return The contract data or null
     */
    fun getData(): Cell? = stateInit?.data

    /**
     * Checks if storage fees are due.
     * 
     * @return true if there are due payments
     */
    fun hasStorageFeesDue(): Boolean = duePayment?.isPositive() == true

    /**
     * Gets the total storage fees due.
     * 
     * @return The amount due or zero if none
     */
    fun getStorageFeesDue(): Tokens = duePayment ?: Tokens.ZERO

    /**
     * Gets the frozen state hash as hex string if available.
     * 
     * @return Hex representation of the frozen state hash or null
     */
    fun frozenStateHashHex(): String? = frozenStateHash?.joinToString("") { "%02x".format(it) }

    /**
     * Estimates the storage cost for the account.
     * 
     * @param pricePerCell Price per cell in nano-tokens
     * @param pricePerBit Price per bit in nano-tokens
     * @return Estimated storage cost
     */
    fun estimateStorageCost(pricePerCell: Long, pricePerBit: Long): Tokens {
        val cellCost = storageUsed.cells * pricePerCell
        val bitCost = storageUsed.bits * pricePerBit
        return Tokens(cellCost + bitCost)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AccountState

        if (storageUsed != other.storageUsed) return false
        if (lastPaid != other.lastPaid) return false
        if (duePayment != other.duePayment) return false
        if (lastTransLt != other.lastTransLt) return false
        if (balance != other.balance) return false
        if (status != other.status) return false
        if (stateInit != other.stateInit) return false
        if (frozenStateHash != null) {
            if (other.frozenStateHash == null) return false
            if (!frozenStateHash.contentEquals(other.frozenStateHash)) return false
        } else if (other.frozenStateHash != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = storageUsed.hashCode()
        result = 31 * result + lastPaid
        result = 31 * result + (duePayment?.hashCode() ?: 0)
        result = 31 * result + lastTransLt.hashCode()
        result = 31 * result + balance.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + (stateInit?.hashCode() ?: 0)
        result = 31 * result + (frozenStateHash?.contentHashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "AccountState(balance=$balance, status=$status, lastTransLt=$lastTransLt)"
    }

    companion object {
        /**
         * Creates an uninitialized account state.
         * 
         * @return AccountState representing an uninitialized account
         */
        fun uninit(): AccountState {
            return AccountState(
                storageUsed = StorageUsed(0, 0, 0),
                lastPaid = 0,
                duePayment = null,
                lastTransLt = 0,
                balance = Tokens.ZERO,
                status = AccountStatus.Uninit
            )
        }

        /**
         * Creates an active account state.
         * 
         * @param balance Account balance
         * @param stateInit State initialization data
         * @param storageUsed Storage usage statistics
         * @param lastTransLt Last transaction logical time
         * @param lastPaid Last payment timestamp
         * @param duePayment Due payment amount
         * @return AccountState representing an active account
         */
        fun active(
            balance: Tokens,
            stateInit: StateInit,
            storageUsed: StorageUsed = StorageUsed(0, 0, 0),
            lastTransLt: Long = 0,
            lastPaid: Int = 0,
            duePayment: Tokens? = null
        ): AccountState {
            return AccountState(
                storageUsed = storageUsed,
                lastPaid = lastPaid,
                duePayment = duePayment,
                lastTransLt = lastTransLt,
                balance = balance,
                status = AccountStatus.Active,
                stateInit = stateInit
            )
        }

        /**
         * Creates a frozen account state.
         * 
         * @param balance Account balance
         * @param frozenStateHash Hash of the frozen state
         * @param storageUsed Storage usage statistics
         * @param lastTransLt Last transaction logical time
         * @param lastPaid Last payment timestamp
         * @param duePayment Due payment amount
         * @return AccountState representing a frozen account
         */
        fun frozen(
            balance: Tokens,
            frozenStateHash: ByteArray,
            storageUsed: StorageUsed = StorageUsed(0, 0, 0),
            lastTransLt: Long = 0,
            lastPaid: Int = 0,
            duePayment: Tokens? = null
        ): AccountState {
            return AccountState(
                storageUsed = storageUsed,
                lastPaid = lastPaid,
                duePayment = duePayment,
                lastTransLt = lastTransLt,
                balance = balance,
                status = AccountStatus.Frozen,
                frozenStateHash = frozenStateHash
            )
        }
    }
}

/**
 * Represents blockchain configuration parameters.
 * 
 * This class contains various blockchain configuration parameters that affect
 * transaction processing, fees, and network behavior.
 * 
 * @property globalId Global network identifier
 * @property capabilities Network capabilities bitmask
 * @property globalVersion Global configuration version
 * @property configAddress Configuration contract address
 * @property electorAddress Elector contract address
 * @property minterAddress Minter contract address
 * @property feeCollectorAddress Fee collector contract address
 */
@Serializable
data class BlockchainConfig(
    val globalId: Int,
    val capabilities: Long,
    val globalVersion: Int,
    val configAddress: Address,
    val electorAddress: Address,
    val minterAddress: Address,
    val feeCollectorAddress: Address
) {
    /**
     * Gets the signature ID if signature with ID capability is enabled.
     * 
     * @return Signature ID or null if not supported
     */
    fun getSignatureId(): Int? {
        return if (hasCapability(CAPABILITY_SIGNATURE_WITH_ID)) {
            globalId
        } else {
            null
        }
    }

    /**
     * Checks if a specific capability is enabled.
     * 
     * @param capability The capability to check
     * @return true if the capability is enabled
     */
    fun hasCapability(capability: Long): Boolean {
        return (capabilities and capability) != 0L
    }

    /**
     * Checks if a configuration parameter exists.
     * 
     * @param index Parameter index
     * @return true if the parameter exists
     */
    fun containsParam(index: Int): Boolean {
        // This would require access to the actual config parameters
        TODO("Config parameter checking not yet implemented")
    }

    /**
     * Gets a raw configuration parameter.
     * 
     * @param index Parameter index
     * @return The parameter cell or null if not found
     */
    fun getRawParam(index: Int): Cell? {
        // This would require access to the actual config parameters
        TODO("Config parameter retrieval not yet implemented")
    }

    /**
     * Builds a cell containing all configuration parameters.
     * 
     * @return Cell with configuration parameters
     */
    fun buildParamsDictCell(): Cell {
        // This would require the actual config serialization logic
        TODO("Config parameters dict building not yet implemented")
    }

    override fun toString(): String {
        return "BlockchainConfig(globalId=$globalId, capabilities=0x${capabilities.toString(16)}, version=$globalVersion)"
    }

    companion object {
        /**
         * Capability flag for signature with ID support.
         */
        const val CAPABILITY_SIGNATURE_WITH_ID = 0x01L

        /**
         * Capability flag for fast storage statistics.
         */
        const val CAPABILITY_FAST_STORAGE_STAT = 0x02L

        /**
         * Creates a default blockchain configuration for testing.
         * 
         * @return Default BlockchainConfig instance
         */
        fun default(): BlockchainConfig {
            val defaultAddress = Address(-1, ByteArray(32))
            return BlockchainConfig(
                globalId = 42,
                capabilities = CAPABILITY_SIGNATURE_WITH_ID or CAPABILITY_FAST_STORAGE_STAT,
                globalVersion = 1,
                configAddress = defaultAddress,
                electorAddress = defaultAddress,
                minterAddress = defaultAddress,
                feeCollectorAddress = defaultAddress
            )
        }
    }
}
