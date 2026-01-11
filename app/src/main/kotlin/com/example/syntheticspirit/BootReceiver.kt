package com.example.syntheticspirit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // We listen for both standard boot and "Quick Boot" (for some older devices)
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d("BootReceiver", "Device reboot detected. Checking shield status...")

            val prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
            val wasRunning = prefs.getBoolean("was_running", false)

            if (wasRunning) {
                Log.d("BootReceiver", "Shield was active before reboot. Restarting...")
                
                val serviceIntent = Intent(context, DnsVpnService::class.java)
                
                // For Android 8.0 (Oreo) and above, we must use startForegroundService
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("BootReceiver", "Shield was inactive. No action taken.")
            }
        }
    }
}
