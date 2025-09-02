package com.mazekine.nekoton

/**
 * Native interface to the Rust nekoton library via JNI.
 * 
 * This class provides the bridge between Kotlin and the native Rust implementation
 * of nekoton, enabling real blockchain operations.
 */
object Native {
    
    private var initialized = false
    
    init {
        try {
            System.loadLibrary("nekoton_jni")
            initialize()
            initialized = true
        } catch (e: UnsatisfiedLinkError) {
            initialized = false
        }
    }
    
    /**
     * Checks if the native library is properly initialized.
     * 
     * @return true if initialized successfully
     */
    fun isInitialized(): Boolean = initialized
    
    /**
     * Gets the version of the native nekoton library.
     * 
     * @return Version string
     */
    external fun getVersion(): String
    
    /**
     * Initializes the native library.
     * 
     * @return true if initialization was successful
     */
    private external fun initialize(): Boolean
    
    /**
     * Cleans up native resources.
     * Should be called when the application is shutting down.
     */
    external fun cleanup()
    
    // Transport native methods
    
    /**
     * Creates a native GQL transport instance.
     * 
     * @param endpoint The GraphQL endpoint URL
     * @return Native transport handle
     */
    external fun createGqlTransport(endpoint: String): Long
    
    /**
     * Creates a native JRPC transport instance.
     * 
     * @param endpoint The JSON-RPC endpoint URL
     * @return Native transport handle
     */
    external fun createJrpcTransport(endpoint: String): Long
    
    /**
     * Sends an external message through the transport.
     * 
     * @param transportHandle Native transport handle
     * @param messageBoc Message BOC bytes
     * @return Success message or throws exception on error
     */
    external fun sendExternalMessage(transportHandle: Long, messageBoc: ByteArray): String
    
    /**
     * Gets contract state from the transport.
     * 
     * @param transportHandle Native transport handle
     * @param address Contract address string
     * @return Contract state JSON bytes
     */
    external fun getContractState(transportHandle: Long, address: String): ByteArray
    
    /**
     * Gets transactions from the transport.
     * 
     * @param transportHandle Native transport handle
     * @param address Contract address string
     * @param fromLt Starting logical time
     * @param count Number of transactions to fetch
     * @return Transactions JSON bytes
     */
    external fun getTransactions(transportHandle: Long, address: String, fromLt: Long, count: Int): ByteArray
    
    /**
     * Cleans up a transport instance.
     * 
     * @param transportHandle Native transport handle
     */
    external fun cleanupTransport(transportHandle: Long)
    
    // Crypto native methods
    
    /**
     * Generates a new Ed25519 keypair.
     * 
     * @return Secret key bytes (32 bytes)
     */
    external fun generateKeyPair(): ByteArray
    
    /**
     * Creates a public key from secret key bytes.
     * 
     * @param secretBytes Secret key bytes (32 bytes)
     * @return Public key bytes (32 bytes)
     */
    external fun publicKeyFromSecret(secretBytes: ByteArray): ByteArray
    
    /**
     * Signs data with a keypair.
     * 
     * @param secretBytes Secret key bytes (32 bytes)
     * @param data Data to sign
     * @param signatureId Optional signature ID (0 for none)
     * @return Signature bytes (64 bytes)
     */
    external fun signData(secretBytes: ByteArray, data: ByteArray, signatureId: Long): ByteArray
    
    /**
     * Verifies a signature.
     * 
     * @param publicBytes Public key bytes (32 bytes)
     * @param data Original data
     * @param signatureBytes Signature bytes (64 bytes)
     * @param signatureId Optional signature ID (0 for none)
     * @return true if signature is valid
     */
    external fun verifySignature(publicBytes: ByteArray, data: ByteArray, signatureBytes: ByteArray, signatureId: Long): Boolean
    
    /**
     * Generates a BIP39 mnemonic phrase.
     * 
     * @param wordCount Number of words (12, 15, 18, 21, or 24)
     * @return Mnemonic phrase string
     */
    external fun generateBip39Mnemonic(wordCount: Long): String
    
    /**
     * Derives a keypair from BIP39 mnemonic.
     * 
     * @param phrase Mnemonic phrase
     * @param path Derivation path (e.g., "m/44'/396'/0'/0/0")
     * @return Secret key bytes (32 bytes)
     */
    external fun deriveBip39KeyPair(phrase: String, path: String): ByteArray
    
    // ABI native methods
    
    /**
     * Parses ABI from JSON string.
     * 
     * @param abiJson ABI JSON string
     * @return Native ABI handle
     */
    external fun parseAbi(abiJson: String): Long
    
    /**
     * Gets ABI version.
     * 
     * @param abiHandle Native ABI handle
     * @return ABI version string
     */
    external fun getAbiVersion(abiHandle: Long): String
    
    /**
     * Gets function names from ABI.
     * 
     * @param abiHandle Native ABI handle
     * @return Function names JSON bytes
     */
    external fun getAbiFunctionNames(abiHandle: Long): ByteArray
    
    /**
     * Encodes a function call.
     * 
     * @param abiHandle Native ABI handle
     * @param functionName Function name
     * @param inputsJson Function inputs as JSON string
     * @return Encoded function call BOC bytes
     */
    external fun encodeFunctionCall(abiHandle: Long, functionName: String, inputsJson: String): ByteArray
    
    /**
     * Decodes function output.
     * 
     * @param abiHandle Native ABI handle
     * @param functionName Function name
     * @param outputBoc Function output BOC bytes
     * @return Decoded output JSON string
     */
    external fun decodeFunctionOutput(abiHandle: Long, functionName: String, outputBoc: ByteArray): String
    
    /**
     * Cleans up an ABI instance.
     * 
     * @param abiHandle Native ABI handle
     */
    external fun cleanupAbi(abiHandle: Long)
    
    // Models native methods
    
    /**
     * Parses an address from string.
     * 
     * @param addressStr Address string
     * @return Address bytes
     */
    external fun parseAddress(addressStr: String): ByteArray
    
    /**
     * Formats an address to string.
     * 
     * @param addressBytes Address bytes
     * @param userFriendly Whether to use user-friendly format
     * @param urlSafe Whether to use URL-safe encoding
     * @param testOnly Whether this is for testnet
     * @param bounce Whether the address should bounce
     * @return Formatted address string
     */
    external fun formatAddress(addressBytes: ByteArray, userFriendly: Boolean, urlSafe: Boolean, testOnly: Boolean, bounce: Boolean): String
    
    /**
     * Creates a cell from BOC bytes.
     * 
     * @param bocBytes BOC bytes
     * @return Native cell handle
     */
    external fun cellFromBoc(bocBytes: ByteArray): Long
    
    /**
     * Serializes a cell to BOC bytes.
     * 
     * @param cellHandle Native cell handle
     * @return BOC bytes
     */
    external fun cellToBoc(cellHandle: Long): ByteArray
    
    /**
     * Gets cell hash.
     * 
     * @param cellHandle Native cell handle
     * @return Cell hash bytes (32 bytes)
     */
    external fun getCellHash(cellHandle: Long): ByteArray
    
    /**
     * Creates a new cell builder.
     * 
     * @return Native cell builder handle
     */
    external fun createCellBuilder(): Long
    
    /**
     * Stores bytes in cell builder.
     * 
     * @param builderHandle Native cell builder handle
     * @param data Bytes to store
     * @return true if successful
     */
    external fun cellBuilderStoreBytes(builderHandle: Long, data: ByteArray): Boolean
    
    /**
     * Builds a cell from cell builder.
     *
     * @param builderHandle Native cell builder handle
     * @return Native cell handle
     */
    external fun cellBuilderBuild(builderHandle: Long): Long

    // Jetton native methods

    /**
     * Calculates a jetton wallet address for the specified owner.
     *
     * @param rootAddress Root contract address bytes
     * @param ownerAddress Owner address bytes
     * @return Jetton wallet address bytes
     */
    @JvmStatic
    external fun getJettonWalletAddress(rootAddress: ByteArray, ownerAddress: ByteArray): ByteArray

    /**
     * Parses jetton wallet state JSON.
     *
     * @param stateJson Wallet state JSON string
     * @return Normalized JSON string
     */
    @JvmStatic
    external fun parseJettonWalletState(stateJson: String): String

    /**
     * Builds a jetton token transfer payload.
     *
     * @param walletAddress Sender wallet address bytes
     * @param toAddress Recipient address bytes
     * @param amount Amount of tokens to transfer
     * @return Payload bytes for transfer
     */
    @JvmStatic
    external fun buildJettonTransfer(walletAddress: ByteArray, toAddress: ByteArray, amount: Long): ByteArray
}
