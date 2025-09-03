package examples

import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.transport.JrpcTransport
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay

/**
 * Example that demonstrates how to monitor blockchain events using flows.
 *
 * Subscribes to account state changes and new transactions for a given address
 * and prints updates in real time.
 */
class EventMonitoring {
    private val transport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/")

    fun monitor(address: String) = runBlocking {
        val addr = Address(address)
        println("Monitoring account: $addr")

        // Subscribe to incoming transactions
        val txJob = launch {
            transport.subscribeToTransactions(addr).collect { tx ->
                println("New transaction: ${'$'}{tx.id}")
            }
        }

        // Subscribe to account state changes (e.g., balance updates)
        val stateJob = launch {
            transport.subscribeToAccountState(addr).collect { state ->
                println("New balance: ${'$'}{state.balance?.toTokens() ?: "0"}")
            }
        }

        // Monitor for 30 seconds
        delay(30_000)
        txJob.cancelAndJoin()
        stateJob.cancelAndJoin()
        transport.close()
    }
}

fun main() {
    val address = "0:0000000000000000000000000000000000000000000000000000000000000000"
    EventMonitoring().monitor(address)
}
