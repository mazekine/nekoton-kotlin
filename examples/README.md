# Nekoton Kotlin Examples

This directory contains practical examples demonstrating how to use Nekoton Kotlin for real blockchain operations.

## Examples

- [`BasicWallet.kt`](BasicWallet.kt) - Simple wallet operations (send/receive)
- [`SmartContractInteraction.kt`](SmartContractInteraction.kt) - Calling smart contract methods
- [`TokenOperations.kt`](TokenOperations.kt) - Query jetton balances and transfer tokens using [Jetton API](../src/main/kotlin/com/mazekine/nekoton/jetton)
- [`EventMonitoring.kt`](EventMonitoring.kt) - Monitor account state and transactions in real time
- [`HDWallet.kt`](HDWallet.kt) - Hierarchical deterministic wallet

## Running Examples

```bash
# Compile and run examples
./gradlew run --args="examples.BasicWallet"
```

## Configuration

All examples use Tycho testnet by default:
- RPC: https://rpc-testnet.tychoprotocol.com/proto
- Currency: TYCHO (9 decimals)
- Explorer: https://testnet.tychoprotocol.com

To use mainnet, update the transport URLs in each example.
