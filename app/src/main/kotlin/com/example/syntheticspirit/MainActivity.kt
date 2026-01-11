package com.example.syntheticspirit

import android.app.Activity
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
import androidx.compose.animation.core.*
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
        // These rely on the Companion Object in DnsVpnService.kt
        val isRunning by DnsVpnService.isRunning 
        val serviceStartTime by DnsVpnService.serviceStartTime
        
        var sessionSeconds by remember { mutableStateOf(0L) }
        var showLogs by remember { mutableStateOf(false) }
        var showBlocklistSettings by remember { mutableStateOf(false) }
        var showBlocklistUrls by remember { mutableStateOf(false) }
        var showBatteryOptimizationDialog by remember { mutableStateOf(false) }

        if (showBatteryOptimizationDialog) {
            BatteryOptimizationDialog { showBatteryOptimizationDialog = false }
        }

        LaunchedEffect(isRunning) {
            if (isRunning) {
                while (true) {
                    val start = serviceStartTime
                    sessionSeconds = if (start > 0) (System.currentTimeMillis() - start) / 1000 else 0
                    delay(1000)
                }
            } else {
                sessionSeconds = 0
            }
        }

        when {
            showLogs -> QueryLogScreen(onBack = { showLogs = false })
            showBlocklistSettings -> BlocklistSettingsScreen(
                onBack = { showBlocklistSettings = false },
                onManageUrls = { showBlocklistUrls = true }
            )
            showBlocklistUrls -> BlocklistUrlScreen()
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    StatusOrb(isActive = isRunning)

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = if (isRunning) "Shield Active" else "Shield Inactive",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (isRunning) {
                        Text(
                            text = String.format("%02d:%02d:%02d", sessionSeconds / 3600, (sessionSeconds % 3600) / 60, sessionSeconds % 60),
                            fontSize = 20.sp,
                            color = Color(0xFF00FFFF),
                            style = MaterialTheme.typography.bodyLarge
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
                        modifier = Modifier.fillMaxWidth(0.75f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFB71C1C) else Color(0xFF1E40AF)
                        ),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = if (isRunning) "Deactivate Shield" else "Activate Shield",
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        TextButton(onClick = { showLogs = true }) {
                            Text("View Logs", color = Color.Gray)
                        }
                        TextButton(onClick = { showBlocklistSettings = true }) {
                            Text("Settings", color = Color.Gray)
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
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        startActivity(intent)
                        onDismiss()
                    }
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Later") }
            }
        )
    }

    @Composable
    fun StatusOrb(isActive: Boolean) {
        val infiniteTransition = rememberInfiniteTransition(label = "orbPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = if (isActive) 1.2f else 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseScale"
        )

        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val center = Offset(size.width / 2, size.height / 2)
                val radius = (size.width / 2) * pulseScale
                
                val brush = Brush.radialGradient(
                    colors = if (isActive) {
                        listOf(Color(0xFF00F2FE), Color(0xFF4FACFE))
                    } else {
                        listOf(Color(0xFF475569), Color(0xFF1E293B))
                    },
                    center = center,
                    radius = radius
                )
                drawCircle(brush = brush, radius = radius)
            }
        }
    }

    private fun isBatteryOptimizationIgnored(): Boolean {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun startVpnService() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, 0)
        } else {
            onActivityResult(0, Activity.RESULT_OK, null)
        }
    }

    private fun stopVpnService() {
        val intent = Intent(this, DnsVpnService::class.java).apply {
            action = "STOP"
        }
        // Always use startForegroundService to communicate with an active service
        startForegroundService(intent)
        Toast.makeText(this, "Shield Deactivated", Toast.LENGTH_SHORT).show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            val intent = Intent(this, DnsVpnService::class.java)
            // Use startForegroundService for Android 15 compliance
            startForegroundService(intent)
            Toast.makeText(this, "Synthetic Spirit Shield Activated", Toast.LENGTH_SHORT).show()
        } else if (requestCode == 0) {
            Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}
