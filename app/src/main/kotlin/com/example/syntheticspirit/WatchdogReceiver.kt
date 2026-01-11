package com.example.syntheticspirit

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val shouldBeActive = prefs.getBoolean("protection_should_be_active", false)
        val deactivationTarget = prefs.getLong("deactivation_target", 0L)
        
        // If it should be active and isn't currently running, and not in the middle of a countdown
        if (shouldBeActive && !DnsVpnService.isRunning.value && deactivationTarget == 0L) {
            sendShieldDownNotification(context)
        }
    }

    private fun sendShieldDownNotification(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, "watchdog_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Shield is Down")
            .setContentText("Your protection list is inactive. Tap to reactivate.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create channel if needed (Android O+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "watchdog_channel",
                "Watchdog Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when VPN protection is unexpectedly disabled"
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(2, notification)
    }
}
