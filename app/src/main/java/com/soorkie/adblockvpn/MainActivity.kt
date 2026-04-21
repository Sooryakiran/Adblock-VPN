package com.soorkie.adblockvpn

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soorkie.adblockvpn.ui.SkeuoColors
import com.soorkie.adblockvpn.ui.SkeuoTheme
import com.soorkie.adblockvpn.ui.StatsScreen
import com.soorkie.adblockvpn.vpn.LocalVpnService

class MainActivity : ComponentActivity() {

    private val prepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpn()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkeuoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize().background(SkeuoColors.Desktop),
                    color = SkeuoColors.Desktop,
                ) {
                    val running by LocalVpnService.isRunning.collectAsStateWithLifecycle()
                    StatsScreen(
                        isVpnRunning = running,
                        onStart = { requestVpn() },
                        onStop = {
                            ContextCompat.startForegroundService(
                                this,
                                LocalVpnService.stopIntent(this),
                            )
                        },
                    )
                }
            }
        }
    }

    private fun requestVpn() {
        val prepare: Intent? = VpnService.prepare(this)
        if (prepare != null) {
            prepareLauncher.launch(prepare)
        } else {
            startVpn()
        }
    }

    private fun startVpn() {
        ContextCompat.startForegroundService(
            this,
            Intent(this, LocalVpnService::class.java),
        )
    }
}
