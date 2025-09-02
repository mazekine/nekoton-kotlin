package examples

import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.models.Tokens
import com.mazekine.nekoton.transport.JrpcTransport
import com.mazekine.nekoton.jetton.JettonApi
import kotlinx.coroutines.runBlocking

/**
 * Jetton token operations example.
 *
 * Demonstrates:
 * - Querying jetton wallet balances
 * - Transferring jettons using the Jetton API
 */
class TokenOperations {
    private val transport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/proto")
    private val jettonApi = JettonApi(transport)

    fun demonstrateTokenOperations() = runBlocking {
        println("=== Jetton Token Operations ===")

        // Replace with actual wallet address
        val owner = Address("0:yourwalletaddress")

        // Query jetton balance
        val balance = jettonApi.getBalance(owner)
        println("Jetton balance: ${balance.toTokens()} JETTON")

        // Transfer jettons to another address
        val recipient = Address("0:recipientaddress")
        val amount = Tokens.fromTokens("1") // 1 token

        val txHash = jettonApi.transfer(
            from = owner,
            to = recipient,
            amount = amount
        )
        println("Transfer sent: $txHash")

        transport.close()
    }
}

fun main() {
    TokenOperations().demonstrateTokenOperations()
}
