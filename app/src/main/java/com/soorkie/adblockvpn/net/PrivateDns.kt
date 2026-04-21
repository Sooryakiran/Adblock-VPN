package com.soorkie.adblockvpn.net

import android.content.Context
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** User-configured Private DNS mode (from system settings). */
enum class PrivateDnsMode { Off, Automatic, Strict }

/**
 * Whether Android's system "Private DNS" (DoT) is currently active on the
 * active default network. When active, DNS queries bypass our local VPN's DNS
 * sink and blocking won't work for that traffic.
 */
data class PrivateDnsStatus(
    /** The user's configured mode in system settings. */
    val mode: PrivateDnsMode,
    /**
     * True if the platform reports Private DNS as actually in use on the
     * current underlying (non-VPN) default network. In Automatic mode this
     * depends on whether the upstream resolver supports DoT.
     */
    val active: Boolean,
    /** Strict-mode hostname, e.g. "dns.google". Null in Off/Automatic modes. */
    val hostname: String?,
)

/**
 * Flow that emits the current [PrivateDnsStatus] and updates whenever the
 * user's Private DNS preference or the underlying network changes.
 */
fun observePrivateDnsStatus(context: Context): Flow<PrivateDnsStatus> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val resolver = context.contentResolver
    val handler = Handler(Looper.getMainLooper())

    // Track the latest LinkProperties seen on a non-VPN default-ish network.
    var latestLp: LinkProperties? = null

    fun readMode(): Pair<PrivateDnsMode, String?> {
        val modeStr = Settings.Global.getString(resolver, "private_dns_mode")
        val specifier = Settings.Global.getString(resolver, "private_dns_specifier")
        val mode = when (modeStr) {
            // Values per AOSP ConnectivityManager.PRIVATE_DNS_MODE_*
            "off" -> PrivateDnsMode.Off
            "hostname" -> PrivateDnsMode.Strict
            "opportunistic" -> PrivateDnsMode.Automatic
            // Older devices default to opportunistic when the setting is unset.
            null -> PrivateDnsMode.Automatic
            else -> PrivateDnsMode.Automatic
        }
        val host = if (mode == PrivateDnsMode.Strict) specifier else null
        return mode to host
    }

    fun publish() {
        val (mode, strictHost) = readMode()
        val lp = latestLp
        val active = lp?.isPrivateDnsActive == true
        val hostname = strictHost ?: lp?.privateDnsServerName
        trySend(PrivateDnsStatus(mode = mode, active = active, hostname = hostname))
    }

    // --- ContentObserver for Settings.Global changes ------------------------
    val settingsObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) { publish() }
    }
    val modeUri = Settings.Global.getUriFor("private_dns_mode")
    val specUri = Settings.Global.getUriFor("private_dns_specifier")
    resolver.registerContentObserver(modeUri, false, settingsObserver)
    resolver.registerContentObserver(specUri, false, settingsObserver)

    // --- Network callback for LinkProperties ------------------------------
    var networkCallback: ConnectivityManager.NetworkCallback? = null
    if (cm != null) {
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                latestLp = lp
                publish()
            }

            override fun onAvailable(network: Network) {
                latestLp = cm.getLinkProperties(network)
                publish()
            }

            override fun onLost(network: Network) {
                // Fall back to whatever is still default.
                latestLp = cm.getLinkProperties(cm.activeNetwork)
                publish()
            }
        }
        // Watch non-VPN Internet networks so we can see the real upstream LP
        // even while our own VPN is the active default.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(request, cb)
        networkCallback = cb

        // Seed with whatever is currently default.
        latestLp = cm.getLinkProperties(cm.activeNetwork)
    }

    // Initial emission.
    publish()

    awaitClose {
        resolver.unregisterContentObserver(settingsObserver)
        networkCallback?.let { runCatching { cm?.unregisterNetworkCallback(it) } }
    }
}.distinctUntilChanged()

