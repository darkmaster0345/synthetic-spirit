package com.example.syntheticspirit

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResilienceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("was_running", false)
        val isServiceRunning = DnsVpnService.isRunning.value

        if (wasRunning && !isServiceRunning) {
            val vpnIntent = VpnService.prepare(applicationContext)
            if (vpnIntent == null) {
                val serviceIntent = Intent(applicationContext, DnsVpnService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(serviceIntent)
                } else {
                    applicationContext.startService(serviceIntent)
                }
            }
        }
        Result.success()
    }
}
