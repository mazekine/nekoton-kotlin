package examples

import com.mazekine.nekoton.crypto.*
import com.mazekine.nekoton.models.*
import com.mazekine.nekoton.transport.*
import com.mazekine.nekoton.abi.*
import kotlinx.coroutines.runBlocking

/**
 * Smart contract interaction example.
 * 
 * Demonstrates:
 * - Loading contract ABI
 * - Encoding function calls
 * - Calling contract methods
 * - Decoding return values
 * - Event monitoring
 */
class SmartContractInteraction {
    private val transport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/")
    
    // Sample TIP3 Token contract ABI
    private val tokenAbi = """
    {
        "ABI version": 2,
        "functions": [
            {
                "name": "transfer",
                "inputs": [
                    {"name": "to", "type": "address"},
                    {"name": "tokens", "type": "uint128"},
                    {"name": "grams", "type": "uint128"},
                    {"name": "send_gas_to", "type": "address"},
                    {"name": "notify", "type": "bool"},
                    {"name": "payload", "type": "cell"}
                ],
                "outputs": []
            },
            {
                "name": "balance",
                "inputs": [],
                "outputs": [
                    {"name": "value0", "type": "uint128"}
                ]
            },
            {
                "name": "details",
                "inputs": [],
                "outputs": [
                    {"name": "name", "type": "string"},
                    {"name": "symbol", "type": "string"},
                    {"name": "decimals", "type": "uint8"},
                    {"name": "root_public_key", "type": "uint256"},
                    {"name": "root_address", "type": "address"}
                ]
            }
        ],
        "events": [
            {
                "name": "Transfer",
                "inputs": [
                    {"name": "from", "type": "address"},
                    {"name": "to", "type": "address"},
                    {"name": "tokens", "type": "uint128"}
                ]
            }
        ]
    }
    """.trimIndent()
    
    fun demonstrateContractInteraction() = runBlocking {
        println("=== Smart Contract Interaction ===")
        
        // 1. Load contract ABI
        val contractAbi = ContractAbi.fromJson(tokenAbi)
        println("Loaded ABI with ${contractAbi.functions.size} functions")
        
        // 2. Contract and wallet setup
        val walletKeyPair = KeyPair.generate()
        val contractAddress = Address("0:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
        val recipientAddress = Address("0:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")
        
        // 3. Get token details
        getTokenDetails(contractAbi, contractAddress)
        
        // 4. Check token balance
        getTokenBalance(contractAbi, contractAddress)
        
        // 5. Transfer tokens
        transferTokens(
            contractAbi = contractAbi,
            contractAddress = contractAddress,
            senderKeyPair = walletKeyPair,
            recipientAddress = recipientAddress,
            amount = Tokens.fromTokens("10.5")
        )
        
        transport.close()
    }
    
    private suspend fun getTokenDetails(abi: ContractAbi, contractAddress: Address) {
        println("\n--- Getting Token Details ---")
        
        try {
            val detailsFunction = abi.getFunction("details")
                ?: throw IllegalStateException("Details function not found")
            
            // Create call message
            val callMessage = UnsignedExternalMessage(
                dst = contractAddress,
                stateInit = null,
                body = detailsFunction.encodeCall(emptyMap())
            )
            
            // For getter methods, we would typically use a different approach
            // This is a simplified example
            println("Token details call encoded successfully")
            
        } catch (e: Exception) {
            println("Error getting token details: ${e.message}")
        }
    }
    
    private suspend fun getTokenBalance(abi: ContractAbi, contractAddress: Address) {
        println("\n--- Getting Token Balance ---")
        
        try {
            val balanceFunction = abi.getFunction("balance")
                ?: throw IllegalStateException("Balance function not found")
            
            val callMessage = UnsignedExternalMessage(
                dst = contractAddress,
                stateInit = null,
                body = balanceFunction.encodeCall(emptyMap())
            )
            
            println("Balance call encoded successfully")
            
        } catch (e: Exception) {
            println("Error getting balance: ${e.message}")
        }
    }
    
    private suspend fun transferTokens(
        contractAbi: ContractAbi,
        contractAddress: Address,
        senderKeyPair: KeyPair,
        recipientAddress: Address,
        amount: Tokens
    ) {
        println("\n--- Transferring Tokens ---")
        
        try {
            val transferFunction = contractAbi.getFunction("transfer")
                ?: throw IllegalStateException("Transfer function not found")
            
            // Prepare function inputs
            val inputs = mapOf(
                "to" to recipientAddress.toString(),
                "tokens" to amount.nanoTokens.toString(),
                "grams" to "100000000", // 0.1 EVER for fees
                "send_gas_to" to senderKeyPair.publicKey.address.toString(),
                "notify" to "true",
                "payload" to Cell.empty().toBoc().let { "te6ccgEBAQEAAgAAAA==" } // Empty cell in base64
            )
            
            // Encode function call
            val functionCall = transferFunction.encodeCall(inputs)
            
            // Create external message
            val message = UnsignedExternalMessage(
                dst = contractAddress,
                stateInit = null,
                body = functionCall
            )
            
            // Sign and send
            val signedMessage = message.sign(senderKeyPair)
            val txHash = transport.sendExternalMessage(signedMessage)
            
            println("Token transfer sent!")
            println("Transaction hash: $txHash")
            println("Amount: ${amount.toTokens()} tokens")
            println("Recipient: $recipientAddress")
            
        } catch (e: Exception) {
            println("Error transferring tokens: ${e.message}")
        }
    }
    
    private suspend fun monitorTransferEvents(abi: ContractAbi, contractAddress: Address) {
        println("\n--- Monitoring Transfer Events ---")
        
        try {
            val transferEvent = abi.getEvent("Transfer")
                ?: throw IllegalStateException("Transfer event not found")
            
            // Subscribe to account state changes to detect new transactions
            transport.subscribeToTransactions(contractAddress).collect { transaction ->
                // In a real implementation, you would parse the transaction
                // to extract event data
                println("New transaction detected: ${transaction.id}")
                
                // Decode event data from transaction
                // val eventData = transferEvent.decodeData(eventCell)
                // println("Transfer: ${eventData}")
            }
            
        } catch (e: Exception) {
            println("Error monitoring events: ${e.message}")
        }
    }
}

fun main() {
    SmartContractInteraction().demonstrateContractInteraction()
}
