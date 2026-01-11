package com.example.syntheticspirit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.syntheticspirit.ui.theme.SyntheticSpiritTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SyntheticSpiritTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F172A)
                ) {
                    MainScreen()
                }
            }
        }
    }

    @Composable
    fun MainScreen() {
        val isRunning by DnsVpnService.isRunning
        val blockedCount by DnsVpnService.blockedCount
        val serviceStartTime by DnsVpnService.serviceStartTime
        var sessionSeconds by remember { mutableStateOf(0L) }
        var showLogs by remember { mutableStateOf(false) }
        var showBlocklistManager by remember { mutableStateOf(false) }
        var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

        if (showBatteryOptimizationDialog) {
            BatteryOptimizationDialog { showBatteryOptimizationDialog = false }
        }

        LaunchedEffect(isRunning) {
            if (isRunning) {
                while (true) {
                    sessionSeconds = if (serviceStartTime > 0) (System.currentTimeMillis() - serviceStartTime) / 1000 else 0
                    delay(1000)
                }
            } else {
                sessionSeconds = 0
            }
        }

        when {
            showLogs -> QueryLogScreen(onBack = { showLogs = false })
            showBlocklistManager -> BlocklistManagerScreen()
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusOrb(isActive = isRunning)

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (isRunning) "Shield Active" else "Shield Inactive",
                        fontSize = 26.sp,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isRunning) {
                        Text(
                            text = "${sessionSeconds / 3600}h ${(sessionSeconds % 3600) / 60}m ${sessionSeconds % 60}s",
                            fontSize = 18.sp,
                            color = Color(0xFF00FFFF)
                        )
                    }

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = {
                            if (isRunning) {
                                stopVpnService()
                            } else {
                                if (isBatteryOptimizationIgnored()) {
                                    startVpnService()
                                } else {
                                    showBatteryOptimizationDialog = true
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.7f),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRunning) Color(0xFFB71C1C) else Color(0xFF1A237E))
                    ) {
                        Text(
                            text = if (isRunning) "Deactivate Shield" else "Activate Shield",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row {
                        TextButton(onClick = { showLogs = true }) {
                            Text("View Logs")
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        TextButton(onClick = { showBlocklistManager = true }) {
                            Text("Manage Blocklist")
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun BatteryOptimizationDialog(onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Background Operation") },
            text = { Text("To ensure the shield remains active, please allow the app to run in the background without restrictions.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        intent.data = Uri.parse("package:$packageName")
                        startActivity(intent)
                        onDismiss()
                    }
                ) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        )
    }

    @Composable
    fun StatusOrb(isActive: Boolean) {
        val pulse by animateFloatAsState(
            targetValue = if (isActive) 1.2f else 1f,
            animationSpec = tween(durationMillis = 1000),
            label = ""
        )

        Canvas(modifier = Modifier.size(200.dp)) { 
            val brush = Brush.radialGradient(
                colors = listOf(Color(0xFF00C6FF), Color(0xFF0072FF)),
                center = Offset(size.width / 2, size.height / 2),
                radius = size.width / 2 * pulse
            )
            drawCircle(brush)
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun startVpnService() {
        val prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_running", true).apply()

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 0)
        } else {
            val serviceIntent = Intent(this, DnsVpnService::class.java)
            startService(serviceIntent)
            Toast.makeText(this, "Synthetic Spirit Shield Activated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpnService() {
        val prefs = getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_running", false).apply()

        val serviceIntent = Intent(this, DnsVpnService::class.java)
        serviceIntent.action = "STOP"
        startService(serviceIntent)
        Toast.makeText(this, "Synthetic Spirit Shield Deactivated", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, DnsVpnService::class.java)
            startService(serviceIntent)
            Toast.makeText(this, "Synthetic Spirit Shield Activated", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
