package com.downly.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** NONE = no internet, UNMETERED = Wi-Fi / ethernet (no data charges), METERED = mobile data. */
enum class NetworkState { NONE, UNMETERED, METERED }

/**
 * Watches the device's default network and exposes it as a [StateFlow].
 * Created once inside DownloadRepository. Needs ACCESS_NETWORK_STATE (already in the manifest).
 */
class NetworkMonitor(context: Context) {

    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private var currentNetwork: Network? = null

    private val _state = MutableStateFlow(readNow())
    val state: StateFlow<NetworkState> = _state.asStateFlow()

    init {
        try {
            manager?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    currentNetwork = network
                    _state.value = classify(manager.getNetworkCapabilities(network))
                }

                override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                    if (network == currentNetwork) _state.value = classify(capabilities)
                }

                override fun onLost(network: Network) {
                    // Only the network that is currently in use counts (Wi-Fi -> mobile switch must not show "offline").
                    if (network == currentNetwork) {
                        currentNetwork = null
                        _state.value = NetworkState.NONE
                    }
                }
            })
        } catch (e: Exception) {
            // Could not register: keep the value read at start, downloads still work.
        }
    }

    private fun readNow(): NetworkState {
        val m = manager ?: return NetworkState.UNMETERED
        return try {
            classify(m.getNetworkCapabilities(m.activeNetwork))
        } catch (e: Exception) {
            NetworkState.UNMETERED
        }
    }

    private fun classify(caps: NetworkCapabilities?): NetworkState = when {
        caps == null -> NetworkState.NONE
        !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkState.NONE
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkState.UNMETERED
        else -> NetworkState.METERED
    }
}
