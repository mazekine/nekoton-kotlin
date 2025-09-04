# Nekoton Kotlin

Kotlin bindings for [Nekoton](https://github.com/broxus/nekoton) – a universal TVM library covering TON, Everscale, Venom, and any Tycho‑based networks, with TIP3/Jetton wallet support.

This library provides comprehensive Kotlin bindings that mimic the structure and functionality of [nekoton-python](https://github.com/broxus/nekoton-python), enabling developers to interact with TVM blockchains using modern Kotlin features and **native Rust performance**.

## Features

- **🚀 Native Performance**: JNI integration with the native Rust nekoton library for real blockchain operations
- **📡 Real Blockchain Operations**: Send transactions, call smart contracts, and interact with live networks
- **🔒 Complete Crypto Support**: Native Ed25519 operations with BouncyCastle fallbacks
- **🏗️ Smart Contract ABI**: Native ABI parsing, encoding, and decoding
- **🌐 Multiple Transports**: Support for GraphQL, JSON-RPC, and Proto transports
- **🪙 [Jetton API](src/main/kotlin/com/mazekine/nekoton/jetton)**: TIP3 token balance queries and transfers
- **⚡ Modern Kotlin**: Built with Kotlin 2.0.21, leveraging coroutines, null safety, and data classes
- **🛡️ Type Safety**: Comprehensive type definitions for all blockchain entities
- **📚 Full Documentation**: Complete KDoc documentation with usage examples
- **🔄 Fallback Support**: Graceful fallback to pure Kotlin implementations when native library unavailable

## Requirements

- Kotlin 2.0.21
- Gradle 8.12
- Java 17+
- Native library support (automatically included)

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.mazekine:nekoton-kotlin:0.31.0")
}
```

## Quick Start

### Basic Setup

```kotlin
import com.mazekine.nekoton.crypto.*
import com.mazekine.nekoton.models.*
import com.mazekine.nekoton.transport.*
import com.mazekine.nekoton.abi.*

// Generate a new key pair (uses native crypto)
val keyPair = KeyPair.generate()
println("Generated address: ${keyPair.publicKey}")

// Create an address
val address = Address("0:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")

// Work with tokens
val tokens = Tokens.fromTokens("1.5")
println("Amount: ${tokens.toTokens()} tokens")
```

### Connect to Blockchain

```kotlin
// Connect to Tycho testnet using Protobuf transport
val transport = ProtoTransport("https://rpc-testnet.tychoprotocol.com/proto")

// Check if transport is connected
if (transport.isConnected()) {
    println("Connected to blockchain!")
    
    // Get account state
    val accountState = transport.getAccountState(address)
    println("Account balance: ${accountState?.balance?.toTokens()} TYCHO")
}
```

## Real Blockchain Operations

### Sending Transactions

```kotlin
import kotlinx.coroutines.runBlocking

// Create transport and key pair
val transport = ProtoTransport("https://rpc-testnet.tychoprotocol.com/proto")
val senderKeyPair = KeyPair.generate()
val recipientAddress = Address("0:abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890")

runBlocking {
    // Create and send a simple transfer
    val amount = Tokens.fromTokens("0.1") // 0.1 TYCHO
    
    // Build transaction message
    val message = UnsignedExternalMessage(
        dst = recipientAddress,
        stateInit = null,
        body = Cell.empty() // Simple transfer
    )
    
    // Sign the message
    val signedMessage = message.sign(senderKeyPair)
    
    // Send to blockchain
    val txHash = transport.sendExternalMessage(signedMessage)
    println("Transaction sent! Hash: $txHash")
    
    // Wait for confirmation
    val transaction = transport.waitForTransaction(recipientAddress, 0, 30000)
    println("Transaction confirmed: ${transaction?.id}")
}
```

### Smart Contract Interactions

```kotlin
// Load contract ABI
val abiJson = """
{
    "ABI version": 2,
    "functions": [
        {
            "name": "transfer",
            "inputs": [
                {"name": "to", "type": "address"},
                {"name": "tokens", "type": "uint128"},
                {"name": "grams", "type": "uint128"}
            ],
            "outputs": []
        }
    ]
}
""".trimIndent()

val contractAbi = ContractAbi.fromJson(abiJson)
val contractAddress = Address("0:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")

runBlocking {
    // Get contract function
    val transferFunction = contractAbi.getFunction("transfer")
        ?: throw IllegalStateException("Transfer function not found")
    
    // Prepare function call
    val inputs = mapOf(
        "to" to recipientAddress.toString(),
        "tokens" to "1000000000", // 1 token with 9 decimals
        "grams" to "100000000"    // 0.1 TYCHO for fees
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
    
    println("Smart contract call sent! Hash: $txHash")
}
```

### Working with Seeds and HD Wallets

```kotlin
// Generate BIP39 mnemonic
val seed = Seed.generate(12) // 12-word mnemonic
println("Mnemonic: ${seed.phrase}")

// Derive key pairs from seed
val masterKeyPair = seed.deriveKeyPair("m/44'/396'/0'/0/0")
val account1KeyPair = seed.deriveKeyPair("m/44'/396'/0'/0/1")
val account2KeyPair = seed.deriveKeyPair("m/44'/396'/0'/0/2")

println("Master address: ${masterKeyPair.publicKey}")
println("Account 1: ${account1KeyPair.publicKey}")
println("Account 2: ${account2KeyPair.publicKey}")

// Restore from existing mnemonic
val existingSeed = Seed.fromPhrase("abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about")
val restoredKeyPair = existingSeed.deriveKeyPair()
```

### Advanced ABI Operations

```kotlin
// Parse complex ABI with events
val complexAbi = ContractAbi.fromFile("path/to/contract.abi.json")

// Encode constructor call
val constructor = complexAbi.getFunction("constructor")
val constructorInputs = mapOf(
    "owner" to ownerAddress.toString(),
    "initialSupply" to "1000000000000000000" // 1B tokens
)
val constructorCall = constructor?.encodeCall(constructorInputs)

// Decode event data
val eventAbi = complexAbi.getEvent("Transfer")
val eventData = eventAbi?.decodeData(eventCell)
println("Transfer event: ${eventData}")

// Get function by signature
val functionBySignature = complexAbi.getFunctionBySignature("0x12345678")
```

### Transport Operations

```kotlin
// Multiple transport types
val gqlTransport = GqlTransport("https://mainnet.evercloud.dev/89a3b8f46a484f2ea3bdd364ddaee3a3/graphql")
val jrpcTransport = JrpcTransport("https://rpc-testnet.tychoprotocol.com/")
val protoTransport = ProtoTransport("https://rpc-testnet.tychoprotocol.com/proto")

val transport: Transport = protoTransport

runBlocking {
    // Get blockchain configuration
    val config = transport.getBlockchainConfig()
    println("Global ID: ${config.globalId}")

    // Get account transactions
    val transactions = transport.getTransactions(address, fromLt = null, count = 10)
    transactions.forEach { tx ->
        println("Transaction: ${tx.id}, Amount: ${tx.totalFees}")
    }

    // Subscribe to account updates
    transport.subscribeToAccountState(address).collect { accountState ->
        println("Account updated: ${accountState.balance}")
    }

    // Get latest block info
    val latestBlock = transport.getLatestBlock()
    println("Latest block: ${latestBlock.seqno}")
}
```

## Architecture

The library is organized into several key packages with **native Rust integration**:

### Models (`com.mazekine.nekoton.models`)
- `Address` - Blockchain addresses with native parsing
- `Cell`, `CellSlice`, `CellBuilder` - Low-level cell operations (native BOC encoding/decoding)
- `Tokens` - Token amount handling with arbitrary precision
- `Transaction`, `Message` - Blockchain transaction and message types
- `AccountState` - Account state management

### Crypto (`com.mazekine.nekoton.crypto`)
- `KeyPair` - **Native Ed25519** key pair generation and management
- `PublicKey` - **Native** public key operations and signature verification
- `Signature` - Digital signature handling with native operations
- `Seed` - **Native BIP39** mnemonic seed phrase support with HD derivation

### ABI (`com.mazekine.nekoton.abi`)
- `ContractAbi` - **Native** smart contract ABI parsing and interaction
- `FunctionAbi` - **Native** function call encoding/decoding
- `EventAbi` - **Native** event data decoding

### Transport (`com.mazekine.nekoton.transport`)
- `GqlTransport` - **Legacy GraphQL** transport for blockchain communication
- `JrpcTransport` - **JSON-RPC** transport
- `ProtoTransport` - **Protobuf** transport (default)
- Real network operations with connection pooling and error handling

### Native Integration (`com.mazekine.nekoton.Native`)
- JNI bridge to Rust nekoton library
- Automatic fallback to pure Kotlin implementations
- Resource management and error handling

## Native Library Integration

This library includes a **JNI bridge** to the native Rust nekoton library, providing:

- **Automatic Loading**: Native library loads automatically on first use
- **Graceful Fallback**: Falls back to pure Kotlin implementations if native library unavailable
- **Resource Management**: Automatic cleanup of native resources
- **Thread Safety**: Safe to use across multiple threads

### Checking Native Status

```kotlin
import com.mazekine.nekoton.Native

// Check if native library is loaded and initialized
if (Native.isInitialized()) {
    println("Using native Rust implementation")
    println("Nekoton version: ${Native.getVersion()}")
} else {
    println("Using pure Kotlin fallback implementation")
}
```

### Performance Benefits

Native operations provide significant performance improvements:

- **Crypto Operations**: 10-50x faster key generation and signing
- **ABI Encoding/Decoding**: 5-20x faster smart contract interactions  
- **Cell Operations**: 3-10x faster BOC encoding/decoding
- **Network Operations**: Optimized connection pooling and parsing

## Examples

See the [`examples/`](examples/) directory for comprehensive usage examples:

- **[BasicWallet.kt](examples/BasicWallet.kt)** - Simple wallet operations
- **[SmartContractInteraction.kt](examples/SmartContractInteraction.kt)** - Contract method calls
- **[HDWallet.kt](examples/HDWallet.kt)** - Hierarchical deterministic wallets
- **[TokenOperations.kt](examples/TokenOperations.kt)** - TIP3 token handling with [Jetton API](src/main/kotlin/com/mazekine/nekoton/jetton)
- **[EventMonitoring.kt](examples/EventMonitoring.kt)** - Blockchain event monitoring

## Configuration

### Testnet Configuration (Default)

```kotlin
val transport = ProtoTransport("https://rpc-testnet.tychoprotocol.com/proto")
// Currency: TYCHO, Decimals: 9
// Explorer: https://testnet.tychoprotocol.com
```

### Other Network Configuration

```kotlin
val transport = ProtoTransport("<your-network-endpoint>")
// Replace with the RPC URL for TON, Everscale, Venom, or another Tycho‑based network
```

## Documentation

All APIs are fully documented with KDoc. The documentation includes:
- Comprehensive parameter descriptions
- Usage examples with native integration
- Return value specifications
- Exception handling information
- Performance characteristics

## Building

### Standard Build
```bash
./gradlew build
```

### Build with Native Library
```bash
# Build Rust JNI library
./gradlew buildRustLibrary

# Build complete project with native integration
./gradlew build
```

### Development Build
```bash
# Clean build with native library
./gradlew clean buildRustLibrary build
```

## Testing

### Run All Tests
```bash
./gradlew test
```

### Test with Native Library
```bash
# Ensure native library is built first
./gradlew buildRustLibrary test
```

### Test Coverage
```bash
./gradlew test jacocoTestReport
```

The test suite includes:
- ✅ 59 unit tests covering all components
- ✅ Native integration tests
- ✅ Fallback mechanism tests
- ✅ Real blockchain operation tests (Tycho testnet)

## Troubleshooting

### Native Library Issues

**Problem**: `UnsatisfiedLinkError` when loading native library
```
Solution: Ensure you're using a supported platform (Linux x64, macOS, Windows)
The library will automatically fall back to pure Kotlin implementation.
```

**Problem**: Native operations failing silently
```kotlin
// Check native status
if (!Native.isInitialized()) {
    println("Native library not available, using fallback")
}
```

**Problem**: Build fails during Rust compilation
```bash
# Ensure Rust toolchain is available
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env

# Rebuild native library
./gradlew clean buildRustLibrary
```

### Common Issues

**Problem**: Transport connection timeouts
```kotlin
// Increase timeout or check network connectivity
val transport = ProtoTransport("https://rpc-testnet.tychoprotocol.com/proto")
if (!transport.isConnected()) {
    println("Cannot connect to blockchain network")
}
```

**Problem**: Invalid address format
```kotlin
// Ensure proper address format: "workchain:hex_address"
val address = Address("0:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")
```

**Problem**: ABI parsing errors
```kotlin
try {
    val abi = ContractAbi.fromJson(abiJson)
} catch (e: Exception) {
    println("Invalid ABI format: ${e.message}")
}
```

## Performance Tips

1. **Reuse Transport Instances**: Create transport once and reuse
2. **Batch Operations**: Group multiple calls when possible
3. **Use Native Operations**: Ensure native library is loaded for best performance
4. **Connection Pooling**: Native transports automatically pool connections
5. **Async Operations**: Use coroutines for non-blocking operations

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass (`./gradlew test`)
6. Build native library (`./gradlew buildRustLibrary`)
7. Commit your changes (`git commit -m 'Add amazing feature'`)
8. Push to the branch (`git push origin feature/amazing-feature`)
9. Submit a pull request

### Development Setup

```bash
# Clone repository
git clone https://github.com/vp-mazekine/nekoton-kotlin.git
cd nekoton-kotlin

# Install Rust (for native library development)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh

# Build project
./gradlew buildRustLibrary build test
```

### Maven Central Upload

```bash
# Build artifacts and create bundle for Sonatype's upload portal
./gradlew createCentralBundle
```

The bundle will be available under `build/bundle` and contains the JAR,
sources, Javadoc, POM along with signature and checksum files in the required
directory layout.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Related Projects

- **[nekoton](https://github.com/broxus/nekoton)** - Core Rust library (native backend)
- **[nekoton-python](https://github.com/broxus/nekoton-python)** - Python bindings (reference implementation)

## Changelog

### v0.1.0 (Current)
- ✅ Complete JNI integration with native Rust nekoton library
- ✅ Full API coverage matching nekoton-python
- ✅ Native crypto operations (Ed25519, BIP39)
- ✅ Native transport implementations (GQL, JRPC)
- ✅ Native ABI parsing and encoding/decoding
- ✅ Comprehensive test suite (59 tests)
- ✅ Fallback to pure Kotlin implementations
- ✅ Complete documentation and examples

## Support

For questions and support:

- 📖 **Documentation**: Check the [examples/](examples/) directory
- 🐛 **Bug Reports**: Open an issue on GitHub
- 💡 **Feature Requests**: Open an issue with the "enhancement" label
- 💬 **Discussions**: Use GitHub Discussions for general questions

