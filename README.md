# Nekoton Kotlin

Kotlin bindings for [Nekoton](https://github.com/broxus/nekoton) - Broxus SDK with TIP3 wallets support.

This library provides comprehensive Kotlin bindings that mimic the structure and functionality of [nekoton-python](https://github.com/broxus/nekoton-python), enabling developers to interact with the TON/Everscale blockchain using modern Kotlin features.

## Features

- **Complete API Coverage**: Mirrors the functionality of nekoton-python
- **Modern Kotlin**: Built with Kotlin 2.0.21, leveraging coroutines, null safety, and data classes
- **Type Safety**: Comprehensive type definitions for all blockchain entities
- **Comprehensive Documentation**: Full KDoc documentation for all APIs
- **Efficient**: Optimized for performance using Kotlin's advanced features

## Requirements

- Kotlin 2.0.21
- Gradle 8.12
- Java 17+

## Installation

Add the following to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("com.broxus:nekoton-kotlin:0.1.0")
}
```

## Quick Start

```kotlin
import com.broxus.nekoton.crypto.KeyPair
import com.broxus.nekoton.models.Address
import com.broxus.nekoton.transport.Transport

// Generate a new key pair
val keyPair = KeyPair.generate()

// Create an address
val address = Address("0:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")

// Work with tokens
val tokens = Tokens.fromTokens("1.5")
```

## Architecture

The library is organized into several key packages:

### Models (`com.broxus.nekoton.models`)
- `Address` - Blockchain addresses
- `Cell`, `CellSlice`, `CellBuilder` - Low-level cell operations
- `Tokens` - Token amount handling with arbitrary precision
- `Transaction`, `Message` - Blockchain transaction and message types
- `AccountState` - Account state management

### Crypto (`com.broxus.nekoton.crypto`)
- `KeyPair` - Ed25519 key pair generation and management
- `PublicKey` - Public key operations and verification
- `Signature` - Digital signature handling
- `Seed` - Mnemonic seed phrase support (Legacy and BIP39)

### ABI (`com.broxus.nekoton.abi`)
- `ContractAbi` - Smart contract ABI parsing and interaction
- `FunctionAbi` - Function call encoding/decoding
- `EventAbi` - Event data decoding

### Transport (`com.broxus.nekoton.transport`)
- `Transport` - Blockchain communication interface
- Support for various transport protocols (GQL, JRPC, Proto)

## Documentation

All APIs are fully documented with KDoc. The documentation includes:
- Comprehensive parameter descriptions
- Usage examples
- Return value specifications
- Exception handling information

## Building

```bash
./gradlew build
```

## Testing

```bash
./gradlew test
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Related Projects

- [nekoton](https://github.com/broxus/nekoton) - Core Rust library
- [nekoton-python](https://github.com/broxus/nekoton-python) - Python bindings

## Support

For questions and support, please open an issue on GitHub.
