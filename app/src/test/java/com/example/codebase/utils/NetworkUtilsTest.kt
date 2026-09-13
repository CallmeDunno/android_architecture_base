package com.example.codebase.utils

import android.net.NetworkCapabilities.TRANSPORT_BLUETOOTH
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_ETHERNET
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import org.junit.Assert.assertEquals
import org.junit.Test

// ConnectivityManager is stubbed in JVM tests, so the transport mapping is tested through networkTypeOf.
class NetworkUtilsTest {

    @Test
    fun `single transports map to their type`() {
        assertEquals(NetworkType.WIFI, typeOf(TRANSPORT_WIFI))
        assertEquals(NetworkType.ETHERNET, typeOf(TRANSPORT_ETHERNET))
        assertEquals(NetworkType.CELLULAR, typeOf(TRANSPORT_CELLULAR))
        assertEquals(NetworkType.VPN, typeOf(TRANSPORT_VPN))
    }

    @Test
    fun `a VPN reports its underlying transport`() {
        assertEquals(NetworkType.WIFI, typeOf(TRANSPORT_VPN, TRANSPORT_WIFI))
        assertEquals(NetworkType.CELLULAR, typeOf(TRANSPORT_VPN, TRANSPORT_CELLULAR))
    }

    @Test
    fun `wifi takes priority over cellular`() {
        assertEquals(NetworkType.WIFI, typeOf(TRANSPORT_CELLULAR, TRANSPORT_WIFI))
    }

    @Test
    fun `networks without a known transport are OTHER`() {
        assertEquals(NetworkType.OTHER, typeOf(TRANSPORT_BLUETOOTH))
        assertEquals(NetworkType.OTHER, typeOf())
    }

    private fun typeOf(vararg transports: Int): NetworkType = networkTypeOf { it in transports }
}
