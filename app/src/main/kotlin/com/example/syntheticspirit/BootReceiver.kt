package com.example.syntheticspirit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Received action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_USER_PRESENT) {
            val prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
            val shouldBeActive = prefs.getBoolean("protection_should_be_active", false)
            val autoStart = prefs.getBoolean("auto_start", false)
            
            // On boot, check auto_start. On unlock, check if it should be active.
            val shouldStart = if (action == Intent.ACTION_BOOT_COMPLETED) autoStart else shouldBeActive
            
            if (shouldStart) {
                if (!DnsVpnService.isRunning.value) {
                    Log.d("BootReceiver", "VPN should be active but isn't. Restarting...")
                    if (VpnService.prepare(context) == null) {
                        val serviceIntent = Intent(context, DnsVpnService::class.java)
                        try {
                            androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                        } catch (e: Exception) {
                            Log.e("BootReceiver", "Failed to restart service", e)
                        }
                    }
                }
            }
        }
    }
}
