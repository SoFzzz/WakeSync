package com.wakesync.test

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.wakesync.core.model.SessionType
import com.wakesync.core.service.WakeSyncForegroundService
import com.wakesync.core.session.SessionManager

/**
 * Temporary debug Activity to test foreground service startup with real user interaction.
 *
 * NOTE: Lives strictly in src/debug/ so it is not bundled in release.
 * Must be removed or un-aliased before Phase 4 (wear-ui-architect) creates the real UI.
 */
class TestServiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = {
                            val sessionManager = SessionManager.getInstance(this@TestServiceActivity)
                            val started = sessionManager.requestStartSession(SessionType.NAP)
                            if (started) {
                                WakeSyncForegroundService.startService(
                                    this@TestServiceActivity,
                                    WakeSyncForegroundService.ACTION_START_NAP
                                )
                                Toast.makeText(this@TestServiceActivity, "Sesión Nap Iniciada", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@TestServiceActivity, "Conflicto: Sesión ya activa", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Iniciar Siesta (Test)")
                    }
                }
            }
        }
    }
}
