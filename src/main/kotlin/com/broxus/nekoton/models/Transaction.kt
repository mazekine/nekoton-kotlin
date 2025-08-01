package com.broxus.nekoton.models

import kotlinx.serialization.Serializable

/**
 * Represents a blockchain transaction in the TON/Everscale network.
 * 
 * This class encapsulates all transaction data including hash, account information,
 * logical time, fees, messages, and various transaction phases.
 * 
 * @property hash The transaction hash
 * @property account The account address involved in the transaction
 * @property lt Logical time of the transaction
 * @property now Unix timestamp when the transaction was created
 * @property prevTransHash Hash of the previous transaction
 * @property prevTransLt Logical time of the previous transaction
 * @property origStatus Original account status before transaction
 * @property endStatus Final account status after transaction
 * @property totalFees Total fees paid for the transaction
 * @property inMsg Incoming message (if any)
 * @property outMsgs List of outgoing messages
 * @property transactionType Type of the transaction
 */
@Serializable
data class Transaction(
    val hash: ByteArray,
    val account: ByteArray,
    val lt: Long,
    val now: Int,
    val prevTransHash: ByteArray,
    val prevTransLt: Long,
    val origStatus: AccountStatus,
    val endStatus: AccountStatus,
    val totalFees: Tokens,
    val inMsg: Message? = null,
    val outMsgs: List<Message> = emptyList(),
    val transactionType: TransactionType = TransactionType.Ordinary,
    val creditFirst: Boolean = false,
    val aborted: Boolean = false,
    val destroyed: Boolean = false,
    val storagePhase: TransactionStoragePhase? = null,
    val creditPhase: TransactionCreditPhase? = null,
    val computePhase: TransactionComputePhase? = null,
    val actionPhase: TransactionActionPhase? = null,
    val bouncePhase: TransactionBouncePhase? = null
) {
    init {
        require(hash.size == 32) { "Transaction hash must be 32 bytes" }
        require(account.size == 32) { "Account address must be 32 bytes" }
        require(prevTransHash.size == 32) { "Previous transaction hash must be 32 bytes" }
        require(lt >= 0) { "Logical time cannot be negative" }
        require(prevTransLt >= 0) { "Previous logical time cannot be negative" }
        require(now >= 0) { "Timestamp cannot be negative" }
    }

    /**
     * Gets the transaction hash as a hex string.
     * 
     * @return Hex representation of the transaction hash
     */
    fun hashHex(): String = hash.joinToString("") { "%02x".format(it) }

    /**
     * Gets the account address as a hex string.
     * 
     * @return Hex representation of the account address
     */
    fun accountHex(): String = account.joinToString("") { "%02x".format(it) }

    /**
     * Checks if the transaction has an incoming message.
     * 
     * @return true if there is an incoming message
     */
    fun hasInMsg(): Boolean = inMsg != null

    /**
     * Checks if the transaction has outgoing messages.
     * 
     * @return true if there are outgoing messages
     */
    fun hasOutMsgs(): Boolean = outMsgs.isNotEmpty()

    /**
     * Gets the number of outgoing messages.
     * 
     * @return Number of outgoing messages
     */
    fun outMsgsCount(): Int = outMsgs.size

    /**
     * Gets the incoming message hash if present.
     * 
     * @return Incoming message hash or null
     */
    fun inMsgHash(): ByteArray? = inMsg?.hash

    /**
     * Checks if the transaction was successful (not aborted).
     * 
     * @return true if the transaction was successful
     */
    fun isSuccessful(): Boolean = !aborted

    /**
     * Encodes the transaction to BOC format.
     * 
     * @return BOC representation as byte array
     */
    fun toBoc(): ByteArray {
        // This would require the actual transaction serialization logic
        TODO("Transaction BOC encoding not yet implemented")
    }

    /**
     * Builds a cell representation of the transaction.
     * 
     * @return Cell containing the transaction data
     */
    fun buildCell(): Cell {
        // This would require the actual transaction cell building logic
        TODO("Transaction cell building not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Transaction

        if (!hash.contentEquals(other.hash)) return false
        if (!account.contentEquals(other.account)) return false
        if (lt != other.lt) return false
        if (now != other.now) return false
        if (!prevTransHash.contentEquals(other.prevTransHash)) return false
        if (prevTransLt != other.prevTransLt) return false
        if (origStatus != other.origStatus) return false
        if (endStatus != other.endStatus) return false
        if (totalFees != other.totalFees) return false
        if (inMsg != other.inMsg) return false
        if (outMsgs != other.outMsgs) return false
        if (transactionType != other.transactionType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = hash.contentHashCode()
        result = 31 * result + account.contentHashCode()
        result = 31 * result + lt.hashCode()
        result = 31 * result + now
        result = 31 * result + prevTransHash.contentHashCode()
        result = 31 * result + prevTransLt.hashCode()
        result = 31 * result + origStatus.hashCode()
        result = 31 * result + endStatus.hashCode()
        result = 31 * result + totalFees.hashCode()
        result = 31 * result + (inMsg?.hashCode() ?: 0)
        result = 31 * result + outMsgs.hashCode()
        result = 31 * result + transactionType.hashCode()
        return result
    }

    override fun toString(): String {
        return "Transaction(hash=${hashHex()}, type=$transactionType, successful=${isSuccessful()})"
    }

    companion object {
        /**
         * Creates a Transaction from BOC data.
         * 
         * @param boc The BOC data as byte array
         * @return Transaction instance
         */
        fun fromBoc(boc: ByteArray): Transaction {
            // This would require the actual transaction deserialization logic
            TODO("Transaction BOC decoding not yet implemented")
        }

        /**
         * Creates a Transaction from a base64-encoded BOC string.
         * 
         * @param base64Boc The BOC data as base64 string
         * @return Transaction instance
         */
        fun fromBase64(base64Boc: String): Transaction {
            val boc = java.util.Base64.getDecoder().decode(base64Boc)
            return fromBoc(boc)
        }

        /**
         * Creates a Transaction from a Cell.
         * 
         * @param cell The cell containing transaction data
         * @return Transaction instance
         */
        fun fromCell(cell: Cell): Transaction {
            // This would require the actual transaction parsing logic
            TODO("Transaction cell parsing not yet implemented")
        }
    }
}

/**
 * Represents the type of a transaction.
 */
@Serializable
enum class TransactionType {
    /** Ordinary transaction */
    Ordinary,
    /** Tick transaction (system) */
    Tick,
    /** Tock transaction (system) */
    Tock
}

/**
 * Represents account status in the blockchain.
 */
@Serializable
enum class AccountStatus {
    /** Account does not exist */
    Uninit,
    /** Account is active and can process transactions */
    Active,
    /** Account is frozen */
    Frozen
}

/**
 * Represents account status change during transaction.
 */
@Serializable
enum class AccountStatusChange {
    /** No change in status */
    Unchanged,
    /** Account was frozen */
    Frozen,
    /** Account was deleted */
    Deleted
}
