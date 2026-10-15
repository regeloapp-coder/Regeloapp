package hu.regelo.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Az internetkapcsolat állapota, folyamatosan frissítve. */
class NetworkMonitor(context: Context) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _online = MutableStateFlow(currentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Meghívódik, amikor a kapcsolat visszaáll. */
    var onReconnect: (() -> Unit)? = null

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        runCatching {
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val wasOffline = !_online.value
                    _online.value = true
                    if (wasOffline) onReconnect?.invoke()
                }

                override fun onLost(network: Network) {
                    _online.value = currentlyOnline()
                }
            })
        }
    }

    fun currentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/** Hálózati hívások automatikus újpróbálkozása exponentiális várakozási idővel (Resilient API calls). */
suspend fun <T> retryWithBackoff(
    times: Int = 3,
    initialDelayMs: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            kotlinx.coroutines.delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    return block()
}

