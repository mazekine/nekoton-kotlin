package com.broxus.nekoton.models

import kotlinx.serialization.Serializable

/**
 * Represents the storage phase of a transaction.
 * 
 * This phase handles storage fee collection and account status changes
 * related to storage payments.
 * 
 * @property storageFeesCollected Amount of storage fees collected
 * @property storageFeesDue Amount of storage fees that are due (if any)
 * @property statusChange Account status change during this phase
 */
@Serializable
data class TransactionStoragePhase(
    val storageFeesCollected: Tokens,
    val storageFeesDue: Tokens? = null,
    val statusChange: AccountStatusChange
) {
    override fun toString(): String {
        return "StoragePhase(collected=$storageFeesCollected, due=$storageFeesDue, change=$statusChange)"
    }
}

/**
 * Represents the credit phase of a transaction.
 * 
 * This phase handles crediting tokens to the account from incoming messages.
 * 
 * @property dueFeesCollected Amount of due fees collected (if any)
 * @property credit Amount of tokens credited to the account
 */
@Serializable
data class TransactionCreditPhase(
    val dueFeesCollected: Tokens? = null,
    val credit: Tokens
) {
    override fun toString(): String {
        return "CreditPhase(credit=$credit, dueCollected=$dueFeesCollected)"
    }
}

/**
 * Represents the compute phase of a transaction.
 * 
 * This phase handles smart contract execution and gas consumption.
 * 
 * @property success Whether the computation was successful
 * @property msgStateUsed Whether message state was used
 * @property accountActivated Whether the account was activated
 * @property gasFees Gas fees paid for computation
 * @property gasUsed Amount of gas units used
 * @property gasLimit Gas limit for the computation
 * @property gasCredit Gas credit available
 * @property mode Computation mode
 * @property exitCode Exit code of the computation
 * @property exitArg Exit argument (if any)
 * @property vmSteps Number of VM steps executed
 * @property vmInitStateHash Initial VM state hash
 * @property vmFinalStateHash Final VM state hash
 */
@Serializable
data class TransactionComputePhase(
    val success: Boolean,
    val msgStateUsed: Boolean,
    val accountActivated: Boolean,
    val gasFees: Tokens,
    val gasUsed: Long,
    val gasLimit: Long,
    val gasCredit: Long? = null,
    val mode: Int,
    val exitCode: Int,
    val exitArg: Int? = null,
    val vmSteps: Long,
    val vmInitStateHash: ByteArray,
    val vmFinalStateHash: ByteArray
) {
    init {
        require(vmInitStateHash.size == 32) { "VM init state hash must be 32 bytes" }
        require(vmFinalStateHash.size == 32) { "VM final state hash must be 32 bytes" }
        require(gasUsed >= 0) { "Gas used cannot be negative" }
        require(gasLimit >= 0) { "Gas limit cannot be negative" }
        require(vmSteps >= 0) { "VM steps cannot be negative" }
    }

    /**
     * Checks if the computation was out of gas.
     * 
     * @return true if computation ran out of gas
     */
    fun isOutOfGas(): Boolean = exitCode == -14 // TVM exit code for out of gas

    /**
     * Checks if the computation had a stack underflow.
     * 
     * @return true if there was a stack underflow
     */
    fun hasStackUnderflow(): Boolean = exitCode == -3

    /**
     * Checks if the computation had a stack overflow.
     * 
     * @return true if there was a stack overflow
     */
    fun hasStackOverflow(): Boolean = exitCode == -4

    /**
     * Gets the VM init state hash as hex string.
     * 
     * @return Hex representation of the init state hash
     */
    fun vmInitStateHashHex(): String = vmInitStateHash.joinToString("") { "%02x".format(it) }

    /**
     * Gets the VM final state hash as hex string.
     * 
     * @return Hex representation of the final state hash
     */
    fun vmFinalStateHashHex(): String = vmFinalStateHash.joinToString("") { "%02x".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionComputePhase

        if (success != other.success) return false
        if (msgStateUsed != other.msgStateUsed) return false
        if (accountActivated != other.accountActivated) return false
        if (gasFees != other.gasFees) return false
        if (gasUsed != other.gasUsed) return false
        if (gasLimit != other.gasLimit) return false
        if (gasCredit != other.gasCredit) return false
        if (mode != other.mode) return false
        if (exitCode != other.exitCode) return false
        if (exitArg != other.exitArg) return false
        if (vmSteps != other.vmSteps) return false
        if (!vmInitStateHash.contentEquals(other.vmInitStateHash)) return false
        if (!vmFinalStateHash.contentEquals(other.vmFinalStateHash)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + msgStateUsed.hashCode()
        result = 31 * result + accountActivated.hashCode()
        result = 31 * result + gasFees.hashCode()
        result = 31 * result + gasUsed.hashCode()
        result = 31 * result + gasLimit.hashCode()
        result = 31 * result + (gasCredit?.hashCode() ?: 0)
        result = 31 * result + mode
        result = 31 * result + exitCode
        result = 31 * result + (exitArg ?: 0)
        result = 31 * result + vmSteps.hashCode()
        result = 31 * result + vmInitStateHash.contentHashCode()
        result = 31 * result + vmFinalStateHash.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "ComputePhase(success=$success, exitCode=$exitCode, gasUsed=$gasUsed, gasFees=$gasFees)"
    }
}

/**
 * Represents the action phase of a transaction.
 * 
 * This phase handles the execution of actions generated by smart contracts.
 * 
 * @property success Whether all actions were executed successfully
 * @property valid Whether the action list was valid
 * @property noFunds Whether execution failed due to insufficient funds
 * @property statusChange Account status change during this phase
 * @property totalFwdFees Total forward fees for outgoing messages
 * @property totalActionFees Total fees for actions
 * @property resultCode Result code of action execution
 * @property resultArg Result argument (if any)
 * @property totalActions Total number of actions
 * @property specActions Number of special actions
 * @property skippedActions Number of skipped actions
 * @property msgsCreated Number of messages created
 * @property actionListHash Hash of the action list
 * @property totalMsgSizeBits Total size of messages in bits
 * @property totalMsgSizeCells Total size of messages in cells
 */
@Serializable
data class TransactionActionPhase(
    val success: Boolean,
    val valid: Boolean,
    val noFunds: Boolean,
    val statusChange: AccountStatusChange,
    val totalFwdFees: Tokens? = null,
    val totalActionFees: Tokens? = null,
    val resultCode: Int,
    val resultArg: Int? = null,
    val totalActions: Int,
    val specActions: Int,
    val skippedActions: Int,
    val msgsCreated: Int,
    val actionListHash: ByteArray,
    val totalMsgSizeBits: Long,
    val totalMsgSizeCells: Long
) {
    init {
        require(actionListHash.size == 32) { "Action list hash must be 32 bytes" }
        require(totalActions >= 0) { "Total actions cannot be negative" }
        require(specActions >= 0) { "Special actions cannot be negative" }
        require(skippedActions >= 0) { "Skipped actions cannot be negative" }
        require(msgsCreated >= 0) { "Messages created cannot be negative" }
        require(totalMsgSizeBits >= 0) { "Message size in bits cannot be negative" }
        require(totalMsgSizeCells >= 0) { "Message size in cells cannot be negative" }
    }

    /**
     * Gets the action list hash as hex string.
     * 
     * @return Hex representation of the action list hash
     */
    fun actionListHashHex(): String = actionListHash.joinToString("") { "%02x".format(it) }

    /**
     * Checks if any actions were skipped.
     * 
     * @return true if actions were skipped
     */
    fun hasSkippedActions(): Boolean = skippedActions > 0

    /**
     * Gets the number of successfully executed actions.
     * 
     * @return Number of executed actions
     */
    fun executedActions(): Int = totalActions - skippedActions

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TransactionActionPhase

        if (success != other.success) return false
        if (valid != other.valid) return false
        if (noFunds != other.noFunds) return false
        if (statusChange != other.statusChange) return false
        if (totalFwdFees != other.totalFwdFees) return false
        if (totalActionFees != other.totalActionFees) return false
        if (resultCode != other.resultCode) return false
        if (resultArg != other.resultArg) return false
        if (totalActions != other.totalActions) return false
        if (specActions != other.specActions) return false
        if (skippedActions != other.skippedActions) return false
        if (msgsCreated != other.msgsCreated) return false
        if (!actionListHash.contentEquals(other.actionListHash)) return false
        if (totalMsgSizeBits != other.totalMsgSizeBits) return false
        if (totalMsgSizeCells != other.totalMsgSizeCells) return false

        return true
    }

    override fun hashCode(): Int {
        var result = success.hashCode()
        result = 31 * result + valid.hashCode()
        result = 31 * result + noFunds.hashCode()
        result = 31 * result + statusChange.hashCode()
        result = 31 * result + (totalFwdFees?.hashCode() ?: 0)
        result = 31 * result + (totalActionFees?.hashCode() ?: 0)
        result = 31 * result + resultCode
        result = 31 * result + (resultArg ?: 0)
        result = 31 * result + totalActions
        result = 31 * result + specActions
        result = 31 * result + skippedActions
        result = 31 * result + msgsCreated
        result = 31 * result + actionListHash.contentHashCode()
        result = 31 * result + totalMsgSizeBits.hashCode()
        result = 31 * result + totalMsgSizeCells.hashCode()
        return result
    }

    override fun toString(): String {
        return "ActionPhase(success=$success, actions=$totalActions, skipped=$skippedActions, msgs=$msgsCreated)"
    }
}

/**
 * Represents the bounce phase of a transaction.
 * 
 * This phase handles bouncing of messages that couldn't be delivered.
 * 
 * @property msgSize Size of the bounced message
 * @property reqFwdFees Required forward fees
 * @property msgFees Message fees
 * @property fwdFees Forward fees
 */
@Serializable
data class TransactionBouncePhase(
    val msgSize: StorageUsed,
    val reqFwdFees: Tokens,
    val msgFees: Tokens,
    val fwdFees: Tokens
) {
    override fun toString(): String {
        return "BouncePhase(msgFees=$msgFees, fwdFees=$fwdFees)"
    }
}

/**
 * Represents storage usage statistics.
 * 
 * @property cells Number of cells used
 * @property bits Number of bits used
 * @property publicCells Number of public cells used
 */
@Serializable
data class StorageUsed(
    val cells: Long,
    val bits: Long,
    val publicCells: Long
) {
    init {
        require(cells >= 0) { "Cells count cannot be negative" }
        require(bits >= 0) { "Bits count cannot be negative" }
        require(publicCells >= 0) { "Public cells count cannot be negative" }
    }

    override fun toString(): String {
        return "StorageUsed(cells=$cells, bits=$bits, publicCells=$publicCells)"
    }
}
