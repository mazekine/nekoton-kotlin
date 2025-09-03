package com.mazekine.nekoton.transport

import com.mazekine.nekoton.Native
import com.mazekine.nekoton.TestConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TransportTypesTest {

    @Test
    fun testGqlTransportConnection() = runTest {
        val transport = GqlTransport(TestConfig.EVERCLOUD_GQL_URL)
        assertEquals(Native.isInitialized(), transport.isConnected())
        transport.close()
    }

    @Test
    fun testJrpcTransportConnection() = runTest {
        val transport = JrpcTransport(TestConfig.TYCHO_JRPC_URL)
        assertEquals(Native.isInitialized(), transport.isConnected())
        transport.close()
    }

    @Test
    fun testProtoTransportConnection() = runTest {
        val transport = ProtoTransport(TestConfig.TYCHO_PROTO_URL)
        assertEquals(Native.isInitialized(), transport.isConnected())
        transport.close()
    }
}

