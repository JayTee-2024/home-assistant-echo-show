package net.badacid.homeassist.echoshow.platform

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** What Home Assistant announces itself as on the local network (its zeroconf integration). */
private const val SERVICE_TYPE = "_home-assistant._tcp."

/**
 * The address of a Home Assistant found on the network: the host and port the Echo itself resolved. What the
 * announcement says about itself (its `internal_url` and `base_url`) is not used: anything on the network can
 * announce anything, including an address somewhere else entirely. Null when there is nothing usable.
 */
fun addressFromService(host: String?, port: Int): String? {
    if (host == null || port <= 0) return null
    val shownHost = if (':' in host) "[$host]" else host
    return "http://$shownHost:$port"
}

/**
 * Looks for Home Assistant on the local network with Android's network service discovery, for up to
 * `timeoutMs`. Gives the address of the first one found, or null. Nothing is sent anywhere else.
 */
suspend fun findHomeAssistant(context: Context, timeoutMs: Long = 6_000): String? {
    val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return null
    val found = CompletableDeferred<String?>()
    // One service is resolved at a time (Android 11 refuses a second at once).
    val resolving = java.util.concurrent.atomic.AtomicBoolean(false)

    @Suppress("DEPRECATION") // resolveService is the way on Android 11, the Echo's version.
    val listener = object : NsdManager.DiscoveryListener {
        override fun onServiceFound(service: NsdServiceInfo) {
            if (found.isCompleted || !resolving.compareAndSet(false, true)) return
            nsd.resolveService(service, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) {
                    val address = addressFromService(info.host?.hostAddress, info.port)
                    if (address != null) found.complete(address) else resolving.set(false)
                }

                override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                    resolving.set(false)
                }
            })
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            found.complete(null)
        }

        override fun onDiscoveryStarted(serviceType: String) = Unit
        override fun onServiceLost(service: NsdServiceInfo) = Unit
        override fun onDiscoveryStopped(serviceType: String) = Unit
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
    }
    return try {
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        withTimeoutOrNull(timeoutMs) { found.await() }
    } finally {
        try {
            nsd.stopServiceDiscovery(listener)
        } catch (_: IllegalArgumentException) {
            // Discovery never started.
        }
    }
}
