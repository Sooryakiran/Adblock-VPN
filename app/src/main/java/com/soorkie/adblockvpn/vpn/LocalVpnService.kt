package com.soorkie.adblockvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.soorkie.adblockvpn.MainActivity
import com.soorkie.adblockvpn.MainApp
import com.soorkie.adblockvpn.data.StatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A minimal local VPN that:
 *  1. Brings up a TUN owning a /24 with a fake DNS server address.
 *  2. Routes only that DNS address through the TUN (split tunnel).
 *  3. For every UDP/53 packet read off the TUN, parses the QNAME, records it
 *     in Room, then forwards the query verbatim to a real upstream resolver
 *     via a [DatagramSocket] that is `protect()`-ed (so the response doesn't
 *     loop back into the TUN), and writes the response back to the originating
 *     app.
 *
 * Limitations (acceptable for P0):
 *  - IPv4 only.
 *  - Apps that use DoH/DoT or hardcoded resolvers (e.g. Chrome secure DNS,
 *    "Private DNS" in system settings) bypass the system resolver and won't
 *    be observed.
 */
class LocalVpnService : VpnService() {

    private val running = AtomicBoolean(false)
    private var tun: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pumpJob: Job? = null
    private var blockedJob: Job? = null
    private lateinit var uidResolver: UidResolver
    /** Read on the TUN thread; updated by [blockedJob] from Room. */
    private val blockedDomains = AtomicReference<Set<String>>(emptySet())

    private val repository: StatsRepository
        get() = MainApp.instance.repository

    override fun onCreate() {
        super.onCreate()
        uidResolver = UidResolver(this)
    }

    /**
     * On many OEM ROMs (Xiaomi, Huawei, OnePlus, Samsung's "deep sleep"…)
     * swiping the app from Recents triggers `onTaskRemoved`, after which the
     * system reaps the process unless the service explicitly keeps itself
     * alive. Re-asserting foreground state here prevents that.
     *
     * On stock Android, `VpnService` + `startForeground` already survives
     * task removal; this is a no-op cost.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (running.get()) {
            startForegroundCompat()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForegroundCompat()
        if (running.compareAndSet(false, true)) {
            startVpn()
        }
        return START_STICKY
    }

    private fun startVpn() {
        val builder = Builder()
            .setSession("AdblockVPN")
            .setMtu(MTU)
            .addAddress(TUN_ADDRESS, 24)
            .addDnsServer(DNS_SINK)
            // Only DNS sink address is routed through the TUN. All other
            // traffic continues over the regular network unchanged.
            .addRoute(DNS_SINK, 32)
            .setBlocking(true)
            .allowBypass()

        val pfd = builder.establish() ?: run {
            Log.e(TAG, "VpnService.Builder.establish() returned null")
            stopSelf()
            return
        }
        tun = pfd

        blockedJob = scope.launch {
            repository.observeBlockedDomains().collectLatest { list ->
                blockedDomains.set(list.toHashSet())
            }
        }
        pumpJob = scope.launch { pump(pfd) }
        _isRunning.value = true
        requestTileUpdate()
    }

    private suspend fun pump(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buffer = ByteArray(MTU)

        while (running.get()) {
            val length = try {
                input.read(buffer)
            } catch (t: Throwable) {
                if (running.get()) Log.w(TAG, "TUN read failed", t)
                break
            }
            if (length <= 0) continue

            val parsed = IpPacket.parseUdpV4(buffer, length) ?: continue
            if (parsed.dstPort != 53) continue

            // Snapshot the payload bytes — `buffer` will be reused.
            val dnsBytes = ByteArray(parsed.payload.remaining()).also {
                parsed.payload.duplicate().get(it)
            }
            val srcIp = parsed.srcIp
            val dstIp = parsed.dstIp
            val srcPort = parsed.srcPort

            // Resolve UID synchronously: the originating socket can close
            // within milliseconds of the query, after which the kernel
            // forgets the mapping.
            val owner = uidResolver.resolve(srcIp, srcPort)

            scope.launch { handleQuery(srcIp, dstIp, srcPort, dnsBytes, owner, output) }
        }
    }

    private suspend fun handleQuery(
        appIp: ByteArray,
        sinkIp: ByteArray,
        appPort: Int,
        dnsBytes: ByteArray,
        owner: UidResolver.App?,
        output: FileOutputStream,
    ) {
        val qname = DnsPacket.extractFirstQName(ByteBuffer.wrap(dnsBytes))
        if (qname != null) {
            try {
                repository.recordQuery(qname, owner?.pkg, owner?.label)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to record $qname", t)
            }
        }

        val response = if (qname != null && isBlocked(qname)) {
            DnsPacket.synthesizeNxDomain(dnsBytes)
        } else {
            forwardToUpstream(dnsBytes) ?: return
        }

        val replyPacket = IpPacket.buildUdpV4(
            srcIp = sinkIp,    // pretend the answer came from the sink DNS
            dstIp = appIp,
            srcPort = 53,
            dstPort = appPort,
            payload = response,
        )
        synchronized(output) {
            try {
                output.write(replyPacket)
            } catch (t: Throwable) {
                Log.w(TAG, "TUN write failed", t)
            }
        }
    }

    private fun forwardToUpstream(query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket()
            // Critical: prevent our own forwarded query from being captured by
            // the VPN we just established.
            if (!protect(socket)) {
                Log.w(TAG, "protect() returned false")
            }
            socket.soTimeout = UPSTREAM_TIMEOUT_MS
            val upstream = InetSocketAddress(InetAddress.getByName(UPSTREAM_DNS), 53)
            socket.send(DatagramPacket(query, query.size, upstream))

            val buf = ByteArray(2048)
            val resp = DatagramPacket(buf, buf.size)
            socket.receive(resp)
            buf.copyOf(resp.length)
        } catch (t: Throwable) {
            Log.w(TAG, "Upstream DNS query failed", t)
            null
        } finally {
            socket?.close()
        }
    }

    private fun requestTileUpdate() {
        try {
            android.service.quicksettings.TileService.requestListeningState(
                this,
                android.content.ComponentName(this, VpnTileService::class.java),
            )
        } catch (_: Throwable) {
        }
    }

    private fun isBlocked(domain: String): Boolean {
        val set = blockedDomains.get()
        if (set.isEmpty()) return false
        val lower = domain.lowercase()
        if (lower in set) return true
        // Also block subdomains of any blocked entry (e.g. block "doubleclick.net"
        // also blocks "ads.g.doubleclick.net").
        var idx = lower.indexOf('.')
        while (idx >= 0 && idx < lower.length - 1) {
            if (lower.substring(idx + 1) in set) return true
            idx = lower.indexOf('.', idx + 1)
        }
        return false
    }

    private fun stopVpn() {
        if (!running.compareAndSet(true, false)) return
        blockedJob?.cancel()
        blockedJob = null
        pumpJob?.cancel()
        pumpJob = null
        try {
            tun?.close()
        } catch (_: Throwable) {
        }
        tun = null
        _isRunning.value = false
        requestTileUpdate()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        stopSelf()
        super.onRevoke()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "VPN", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Local VPN status"
                }
            )
        }
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LocalVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
            "Stop",
            stopIntent,
        ).build()
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Adblock VPN")
            .setContentText("Capturing DNS requests")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentIntent(openIntent)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notif)
    }

    companion object {
        private const val TAG = "LocalVpnService"
        private const val MTU = 1500
        private const val TUN_ADDRESS = "10.7.0.1"
        private const val DNS_SINK = "10.7.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
        private const val UPSTREAM_TIMEOUT_MS = 4000

        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1

        const val ACTION_STOP = "com.soorkie.adblockvpn.STOP"

        private val _isRunning = kotlinx.coroutines.flow.MutableStateFlow(false)
        /** Live state of the VPN service; survives Activity restarts. */
        val isRunning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isRunning

        fun stopIntent(context: Context): Intent =
            Intent(context, LocalVpnService::class.java).setAction(ACTION_STOP)
    }
}
