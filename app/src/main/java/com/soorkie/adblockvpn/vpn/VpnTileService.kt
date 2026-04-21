package com.soorkie.adblockvpn.vpn

import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.soorkie.adblockvpn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Quick Settings tile that mirrors and toggles [LocalVpnService].
 *
 * - When the VPN is off, tapping it must first obtain VPN consent. Since
 *   `VpnService.prepare()` may need an activity to show the consent dialog,
 *   we launch [MainActivity] in that case (via `startActivityAndCollapse`).
 * - When the VPN is on, tapping stops it directly via the foreground service.
 */
@RequiresApi(Build.VERSION_CODES.N)
class VpnTileService : TileService() {

    private var scope: CoroutineScope? = null
    private var observerJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Main).also { scope = it }
        observerJob = s.launch {
            LocalVpnService.isRunning.collect { running -> render(running) }
        }
    }

    override fun onStopListening() {
        observerJob?.cancel()
        observerJob = null
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        val running = LocalVpnService.isRunning.value
        if (running) {
            ContextCompat.startForegroundService(this, LocalVpnService.stopIntent(this))
            render(false)
        } else {
            val prepare: Intent? = VpnService.prepare(this)
            if (prepare == null) {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, LocalVpnService::class.java),
                )
                render(true)
            } else {
                // Consent dialog must be hosted by an Activity. Punt to the app
                // and collapse the shade so the user sees it.
                val launch = Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                @Suppress("DEPRECATION") // startActivityAndCollapse(Intent) is fine pre-34
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startActivityAndCollapse(
                        android.app.PendingIntent.getActivity(
                            this, 0, launch,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                                android.app.PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                } else {
                    startActivityAndCollapse(launch)
                }
            }
        }
    }

    private fun render(running: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Adblock VPN"
        tile.contentDescription = if (running) "Stop Adblock VPN" else "Start Adblock VPN"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (running) "On" else "Off"
        }
        tile.updateTile()
    }
}
