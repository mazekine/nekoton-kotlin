package examples

import com.mazekine.nekoton.abi.ContractAbi
import com.mazekine.nekoton.crypto.KeyPair
import com.mazekine.nekoton.models.*
import com.mazekine.nekoton.transport.JrpcTransport
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest

/**
 * TIP-3 Jetton root deployment example.
 *
 * Demonstrates:
 * - Loading JettonRoot contract ABI and code
 * - Encoding StateInit and constructor call
 * - Building, signing and sending the deployment message
 *
 * Contract reference: https://github.com/broxus/tip3jetton
 */
class JettonDeployment {
    private val transport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/")

    fun deploy() = runBlocking {
        println("=== Jetton Root Deployment ===")

        // 1. Load contract artifacts (ABI + TVC)
        val abiJson = File("TokenRoot.abi.json").readText()
        val contractAbi = ContractAbi.fromJson(abiJson)
        val codeCell = Cell.fromBoc(File("TokenRoot.tvc").readBytes())
        val walletCode = Cell.fromBoc(File("TokenWallet.tvc").readBytes())

        // 2. Generate owner key pair and address
        val ownerKeyPair = KeyPair.generate()
        val ownerAddress = Address.fromPublicKey(ownerKeyPair.publicKey, 0)

        // 3. Encode initial data for deployment
        val dataCell = contractAbi.encodeInitData(
            data = mapOf(
                "name_" to "Example Jetton",
                "symbol_" to "EXJ",
                "decimals_" to 9,
                "rootOwner_" to ownerAddress.toString(),
                "walletCode_" to walletCode,
                "randomNonce_" to 1,
                "deployer_" to ownerAddress.toString()
            ),
            publicKey = ownerKeyPair.publicKey
        )
        val stateInit = StateInit.create(codeCell, dataCell)

        // Derive contract address from StateInit
        val contractAddress = deriveAddress(stateInit, 0)

        // 4. Build constructor payload
        val constructor = contractAbi.getFunction("constructor")
            ?: error("constructor not found")
        val body = constructor.encodeCall(
            mapOf(
                "initialSupplyTo" to ownerAddress.toString(),
                "initialSupply" to Tokens.fromTokens("1000").nanoTokens.toString(),
                "deployWalletValue" to "100000000",
                "mintDisabled" to false,
                "burnByRootDisabled" to false,
                "burnPaused" to false,
                "remainingGasTo" to ownerAddress.toString()
            )
        )

        // 5. Create, sign and send deployment message
        val unsigned = UnsignedExternalMessage(
            dst = contractAddress,
            stateInit = stateInit,
            body = body
        )
        val signed = unsigned.sign(ownerKeyPair)
        val txHash = transport.sendExternalMessage(signed)
        println("Deployment message sent: $txHash")

        transport.close()
    }

    private fun deriveAddress(stateInit: StateInit, workchain: Int): Address {
        fun cellHash(cell: Cell): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(cell.data)
            cell.references.forEach { digest.update(cellHash(it)) }
            return digest.digest()
        }

        val builder = CellBuilder()
        stateInit.code?.let {
            builder.writeBit(true)
            builder.writeRef(it)
        } ?: builder.writeBit(false)

        stateInit.data?.let {
            builder.writeBit(true)
            builder.writeRef(it)
        } ?: builder.writeBit(false)

        stateInit.library?.let {
            builder.writeBit(true)
            builder.writeRef(it)
        } ?: builder.writeBit(false)

        val initCell = builder.build()
        val hash = cellHash(initCell)
        return Address(workchain, hash)
    }
}

fun main() {
    JettonDeployment().deploy()
}
