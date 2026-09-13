package com.example.codebase.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkType { WIFI, ETHERNET, CELLULAR, VPN, OTHER, NONE }

/**
 * Connectivity checks backed by [ConnectivityManager]. Requires `ACCESS_NETWORK_STATE`, which is
 * declared in the manifest.
 *
 * "Connected" means the active network has the INTERNET capability. It doesn't guarantee that a server
 * is reachable. Repositories must not use these checks to skip a request: the data policy is
 * online-first, so the request itself decides and Room is the fallback. Use them for UI, such as an
 * offline banner.
 */
object NetworkUtils {

    fun isConnected(context: Context): Boolean =
        activeCapabilities(context)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** Transport of the active network, or [NetworkType.NONE] when there is none. */
    fun getNetworkType(context: Context): NetworkType {
        val capabilities = activeCapabilities(context) ?: return NetworkType.NONE
        return networkTypeOf(capabilities::hasTransport)
    }

    /** Whether large transfers should be avoided on the active network (typically cellular). */
    fun isMetered(context: Context): Boolean =
        context.getSystemService<ConnectivityManager>()?.isActiveNetworkMetered ?: true

    /**
     * Emits whether the default network has internet access: the current state first, then every change.
     *
     * The system callback stays registered while the Flow is collected. Collect it with
     * `collectWhenStarted` so it is released when the screen stops; an app can only register a limited
     * number of network callbacks (about 100).
     */
    fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService<ConnectivityManager>()
        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            private var currentNetwork: Network? = null

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                currentNetwork = network
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }

            override fun onLost(network: Network) {
                // When the default network switches, callbacks for the new network can arrive before
                // onLost of the old one, so only losing the network reported last means offline.
                if (network == currentNetwork) {
                    currentNetwork = null
                    trySend(false)
                }
            }
        }
        trySend(isConnected(context))
        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun activeCapabilities(context: Context): NetworkCapabilities? {
        val connectivityManager = context.getSystemService<ConnectivityManager>() ?: return null
        val network = connectivityManager.activeNetwork ?: return null
        return connectivityManager.getNetworkCapabilities(network)
    }
}

/**
 * Picks the most relevant transport of a network. A VPN also reports its underlying transport, so a VPN
 * over Wi-Fi is [NetworkType.WIFI]. A network without a known transport is [NetworkType.OTHER].
 */
internal fun networkTypeOf(hasTransport: (Int) -> Boolean): NetworkType = when {
    hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
    hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
    hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
    hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
    else -> NetworkType.OTHER
}
