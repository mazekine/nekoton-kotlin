package examples

import com.mazekine.nekoton.crypto.*
import com.mazekine.nekoton.models.*
import com.mazekine.nekoton.transport.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

/**
 * Basic wallet operations example.
 * 
 * Demonstrates:
 * - Key pair generation
 * - Address creation
 * - Balance checking
 * - Simple transfers
 * - Transaction monitoring
 */
class BasicWallet {
    private val transport = GqlTransport("https://rpc-testnet.tychoprotocol.com/proto")
    
    fun demonstrateBasicOperations() = runBlocking {
        println("=== Basic Wallet Operations ===")
        
        // 1. Generate wallet
        val wallet = generateWallet()
        println("Generated wallet address: ${wallet.address}")
        
        // 2. Check balance
        val balance = getBalance(wallet.address)
        println("Current balance: ${balance?.toTokens() ?: "0"} TYCHO")
        
        // 3. Generate recipient
        val recipient = generateWallet()
        println("Recipient address: ${recipient.address}")
        
        // 4. Send tokens (if balance available)
        if (balance != null && balance > Tokens.fromTokens("0.1")) {
            val txHash = sendTokens(
                from = wallet,
                to = recipient.address,
                amount = Tokens.fromTokens("0.05")
            )
            println("Transaction sent: $txHash")
            
            // 5. Wait for confirmation
            waitForTransaction(recipient.address, txHash)
        } else {
            println("Insufficient balance for transfer. Fund the wallet first.")
        }
        
        transport.close()
    }
    
    private fun generateWallet(): Wallet {
        val keyPair = KeyPair.generate()
        val address = Address.fromPublicKey(keyPair.publicKey, 0) // workchain 0
        return Wallet(keyPair, address)
    }
    
    private suspend fun getBalance(address: Address): Tokens? {
        return try {
            val accountState = transport.getAccountState(address)
            accountState?.balance
        } catch (e: Exception) {
            println("Error getting balance: ${e.message}")
            null
        }
    }
    
    private suspend fun sendTokens(from: Wallet, to: Address, amount: Tokens): String {
        // Create simple transfer message
        val message = UnsignedExternalMessage(
            dst = to,
            stateInit = null,
            body = createTransferBody(amount)
        )
        
        // Sign the message
        val signedMessage = message.sign(from.keyPair)
        
        // Send to blockchain
        return transport.sendExternalMessage(signedMessage)
    }
    
    private fun createTransferBody(amount: Tokens): Cell {
        // Simple transfer body (in real implementation, this would be more complex)
        val builder = CellBuilder()
        builder.storeUint(amount.nanoTokens, 128) // Amount in nano tokens
        return builder.build()
    }
    
    private suspend fun waitForTransaction(address: Address, expectedTxHash: String) {
        println("Waiting for transaction confirmation...")
        
        repeat(30) { // Wait up to 30 seconds
            delay(1000)
            
            val transactions = transport.getTransactions(address, fromLt = null, count = 1)
            if (transactions.isNotEmpty()) {
                val latestTx = transactions.first()
                println("Transaction confirmed: ${latestTx.id}")
                return
            }
        }
        
        println("Transaction not confirmed within timeout")
    }
    
    data class Wallet(
        val keyPair: KeyPair,
        val address: Address
    )
}

fun main() {
    BasicWallet().demonstrateBasicOperations()
}
