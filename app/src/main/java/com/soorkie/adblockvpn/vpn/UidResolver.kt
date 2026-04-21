package com.soorkie.adblockvpn.vpn

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Process
import android.system.OsConstants
import android.util.LruCache
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * Resolves the owning UID (and human-readable app label) for a UDP/53 flow that
 * we just observed coming out of the TUN.
 *
 * Notes:
 *  - `ConnectivityManager.getConnectionOwnerUid` requires API 29+.
 *  - The kernel only knows the UID while the originating socket is still open.
 *    DNS sockets are short-lived, so call this from the TUN read thread (no
 *    coroutine hop) and cache the result.
 */
class UidResolver(context: Context) {

    private val appContext = context.applicationContext
    private val cm = appContext.getSystemService(ConnectivityManager::class.java)
    private val pm = appContext.packageManager

    private data class Flow(val srcIp: String, val srcPort: Int)
    data class App(val pkg: String, val label: String)

    /** (srcIp, srcPort) -> resolved app, valid for FLOW_TTL_MS. */
    private val flowCache = LruCache<Flow, Pair<App, Long>>(512)
    /** uid -> resolved app. UIDs are stable; cache aggressively. */
    private val uidCache = LruCache<Int, App>(256)

    private val sinkAddr = InetAddress.getByName("10.7.0.2")
    private val ownUid = Process.myUid()

    fun resolve(srcIp: ByteArray, srcPort: Int): App? {
        val key = Flow(ipString(srcIp), srcPort)
        val now = System.currentTimeMillis()
        flowCache.get(key)?.let { (app, ts) ->
            if (now - ts < FLOW_TTL_MS) return app
        }

        val uid = try {
            cm?.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(srcIp), srcPort),
                InetSocketAddress(sinkAddr, 53),
            ) ?: -1
        } catch (_: Throwable) {
            -1
        }
        if (uid <= 0 || uid == ownUid) return null

        val app = uidCache.get(uid) ?: lookupUid(uid)?.also { uidCache.put(uid, it) }
        if (app != null) flowCache.put(key, app to now)
        return app
    }

    private fun lookupUid(uid: Int): App? {
        val pkgs = pm.getPackagesForUid(uid) ?: return null
        val pkg = pkgs.firstOrNull() ?: return null
        val label = try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
        return App(pkg, label)
    }

    private fun ipString(b: ByteArray): String =
        "${b[0].toInt() and 0xFF}.${b[1].toInt() and 0xFF}.${b[2].toInt() and 0xFF}.${b[3].toInt() and 0xFF}"

    companion object {
        private const val FLOW_TTL_MS = 5_000L
    }
}
