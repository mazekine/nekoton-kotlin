package examples

import com.mazekine.nekoton.crypto.*
import com.mazekine.nekoton.models.*

/**
 * Hierarchical Deterministic (HD) Wallet example.
 * 
 * Demonstrates:
 * - BIP39 mnemonic generation
 * - HD key derivation
 * - Multiple account management
 * - Seed phrase backup and recovery
 */
class HDWallet {
    
    fun demonstrateHDWallet() {
        println("=== HD Wallet Operations ===")
        
        // 1. Generate new mnemonic
        val seed = generateNewWallet()
        
        // 2. Derive multiple accounts
        val accounts = deriveAccounts(seed, 5)
        
        // 3. Display account information
        displayAccounts(accounts)
        
        // 4. Demonstrate recovery
        demonstrateRecovery(seed.phrase)
        
        // 5. Show different derivation paths
        demonstrateDifferentPaths(seed)
    }
    
    private fun generateNewWallet(): Seed {
        println("\n--- Generating New HD Wallet ---")
        
        // Generate 12-word mnemonic (can also use 15, 18, 21, 24)
        val seed = Seed.generate(12)
        
        println("Generated mnemonic:")
        println("\"${seed.phrase}\"")
        println("\n⚠️  IMPORTANT: Store this mnemonic securely!")
        println("⚠️  Anyone with this phrase can access your funds!")
        
        return seed
    }
    
    private fun deriveAccounts(seed: Seed, count: Int): List<Account> {
        println("\n--- Deriving Accounts ---")
        
        val accounts = mutableListOf<Account>()
        
        repeat(count) { index ->
            // Standard derivation path: m/44'/396'/0'/0/{index}
            // 396 is the coin type for TON/Everscale
            val derivationPath = "m/44'/396'/0'/0/$index"
            val keyPair = seed.deriveKeyPair(derivationPath)
            val address = Address.fromPublicKey(keyPair.publicKey, 0)
            
            accounts.add(Account(
                index = index,
                derivationPath = derivationPath,
                keyPair = keyPair,
                address = address
            ))
        }
        
        return accounts
    }
    
    private fun displayAccounts(accounts: List<Account>) {
        println("\n--- Account Information ---")
        
        accounts.forEach { account ->
            println("Account ${account.index}:")
            println("  Path: ${account.derivationPath}")
            println("  Address: ${account.address}")
            println("  Public Key: ${account.keyPair.publicKey.toHex()}")
            println()
        }
    }
    
    private fun demonstrateRecovery(originalPhrase: String) {
        println("--- Wallet Recovery ---")
        
        try {
            // Recover wallet from mnemonic
            val recoveredSeed = Seed.fromPhrase(originalPhrase)
            
            // Derive the same first account
            val recoveredKeyPair = recoveredSeed.deriveKeyPair("m/44'/396'/0'/0/0")
            val recoveredAddress = Address.fromPublicKey(recoveredKeyPair.publicKey, 0)
            
            println("Wallet recovered successfully!")
            println("First account address: $recoveredAddress")
            
        } catch (e: Exception) {
            println("Recovery failed: ${e.message}")
        }
    }
    
    private fun demonstrateDifferentPaths(seed: Seed) {
        println("--- Different Derivation Paths ---")
        
        val paths = listOf(
            "m/44'/396'/0'/0/0" to "Standard account 0",
            "m/44'/396'/1'/0/0" to "Second wallet, account 0", 
            "m/44'/396'/0'/1/0" to "Change address 0",
            "m/44'/60'/0'/0/0"  to "Ethereum-style path",
            "m/0'/0'/0'"        to "Legacy path"
        )
        
        paths.forEach { (path, description) ->
            try {
                val keyPair = seed.deriveKeyPair(path)
                val address = Address.fromPublicKey(keyPair.publicKey, 0)
                
                println("$description:")
                println("  Path: $path")
                println("  Address: $address")
                println()
                
            } catch (e: Exception) {
                println("Failed to derive $path: ${e.message}")
            }
        }
    }
    
    fun demonstrateMultipleWallets() {
        println("\n=== Multiple Wallet Management ===")
        
        // Create multiple independent wallets
        val wallets = (1..3).map { index ->
            val seed = Seed.generate(12)
            val masterKeyPair = seed.deriveKeyPair("m/44'/396'/0'/0/0")
            val address = Address.fromPublicKey(masterKeyPair.publicKey, 0)
            
            WalletInfo(
                name = "Wallet $index",
                seed = seed,
                masterKeyPair = masterKeyPair,
                address = address
            )
        }
        
        wallets.forEach { wallet ->
            println("${wallet.name}:")
            println("  Mnemonic: ${wallet.seed.phrase}")
            println("  Address: ${wallet.address}")
            println()
        }
    }
    
    data class Account(
        val index: Int,
        val derivationPath: String,
        val keyPair: KeyPair,
        val address: Address
    )
    
    data class WalletInfo(
        val name: String,
        val seed: Seed,
        val masterKeyPair: KeyPair,
        val address: Address
    )
}

fun main() {
    val hdWallet = HDWallet()
    hdWallet.demonstrateHDWallet()
    hdWallet.demonstrateMultipleWallets()
}
