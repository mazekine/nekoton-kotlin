package examples

import com.mazekine.nekoton.crypto.KeyPair
import com.mazekine.nekoton.crypto.PublicKey
import com.mazekine.nekoton.crypto.Signature
import com.mazekine.nekoton.abi.AbiVersion
import com.mazekine.nekoton.abi.UnsignedBody
import com.mazekine.nekoton.abi.UnsignedExternalMessage
import com.mazekine.nekoton.models.Address
import com.mazekine.nekoton.models.CellBuilder
import com.mazekine.nekoton.models.Tokens
import com.mazekine.nekoton.transport.JrpcTransport
import kotlinx.coroutines.runBlocking

/**
 * Multi-signature wallet operations example.
 *
 * Demonstrates:
 * - Creating custodians and threshold
 * - Building a multisig transfer message
 * - Collecting multiple signatures
 *
 * Note: This is a simplified example and does not deploy an actual multisig
 *       smart contract. It shows how signatures can be collected before
 *       sending a transaction to the blockchain.
 */
class MultiSigWallet {
    private val transport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/proto")

    fun demonstrateMultiSigWallet() = runBlocking {
        println("=== Multi-Signature Wallet ===")

        // 1. Generate three custodians for a 2-of-3 multisig wallet
        val custodians = List(3) { KeyPair.generate() }
        val threshold = 2

        // Derive a sample wallet address from the first custodian's public key
        val walletAddress = Address.fromHex(0, custodians.first().publicKey.toHex())
        val wallet = Wallet(walletAddress, custodians.map { it.publicKey }, threshold)

        println("Wallet address: ${wallet.address}")
        println("Custodian public keys:")
        custodians.forEachIndexed { index, keyPair ->
            println("  ${index + 1}. ${keyPair.publicKey.toHex()}")
        }

        // 2. Prepare a transfer message
        val recipient = Address.fromHex(0, KeyPair.generate().publicKey.toHex())
        val amount = Tokens.fromTokens(0.05)
        val message = UnsignedExternalMessage(
            dst = wallet.address,
            stateInit = null,
            body = createTransferBody(recipient, amount)
        )

        // 3. Collect the required number of signatures
        val signatures = custodians.take(wallet.threshold).map { it.sign(message.body.hash) }
        println("Collected ${signatures.size} signatures out of ${wallet.threshold}")

        // In a real application, the signatures would be embedded into the
        // message and then submitted:
        // transport.sendExternalMessage(multiSigMessage.toSignedMessage())

        transport.close()
    }

    private fun createTransferBody(to: Address, amount: Tokens): UnsignedBody {
        val builder = CellBuilder()
        builder.writeAddress(to)
        builder.writeTokens(amount)
        val payload = builder.build()

        return UnsignedBody(
            abiVersion = AbiVersion(2),
            payload = payload,
            hash = payload.hash(),
            expireAt = System.currentTimeMillis() / 1000 + 60
        )
    }

    data class Wallet(
        val address: Address,
        val custodians: List<PublicKey>,
        val threshold: Int
    )

    data class MultiSigMessage(
        val message: UnsignedExternalMessage,
        val signatures: List<Signature>
    )
}

fun main() {
    MultiSigWallet().demonstrateMultiSigWallet()
}
