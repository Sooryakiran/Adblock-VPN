package com.soorkie.adblockvpn.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether Android's system "Private DNS" (DoT) is currently active on the
 * active default network. When true, DNS queries bypass our local VPN's DNS
 * sink and blocking won't work for that traffic.
 */
data class PrivateDnsStatus(
    val active: Boolean,
    /** e.g. "dns.google" when the user picked a specific hostname; null otherwise. */
    val hostname: String?,
)

/**
 * Flow that emits the current [PrivateDnsStatus] and updates whenever the
 * system's private DNS state or active network changes.
 */
fun observePrivateDnsStatus(context: Context): Flow<PrivateDnsStatus> = callbackFlow {
    val cm = context.getSystemService(ConnectivityManager::class.java)
        ?: run {
            trySend(PrivateDnsStatus(active = false, hostname = null))
            awaitClose { }
            return@callbackFlow
        }

    fun publish(lp: LinkProperties?) {
        val status = if (lp == null) {
            PrivateDnsStatus(active = false, hostname = null)
        } else {
            PrivateDnsStatus(active = lp.isPrivateDnsActive, hostname = lp.privateDnsServerName)
        }
        trySend(status)
    }

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
            if (network == cm.activeNetwork) publish(lp)
        }

        override fun onAvailable(network: Network) {
            if (network == cm.activeNetwork) publish(cm.getLinkProperties(network))
        }

        override fun onLost(network: Network) {
            publish(cm.getLinkProperties(cm.activeNetwork))
        }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    cm.registerNetworkCallback(request, callback)

    // Seed with the current value.
    publish(cm.getLinkProperties(cm.activeNetwork))

    awaitClose { cm.unregisterNetworkCallback(callback) }
}.distinctUntilChanged()
