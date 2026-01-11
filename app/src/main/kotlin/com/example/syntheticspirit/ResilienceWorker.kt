package com.example.syntheticspirit

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class ResilienceWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val shouldBeActive = prefs.getBoolean("protection_should_be_active", false)
        
        if (shouldBeActive && !DnsVpnService.isRunning.value) {
            Log.d("ResilienceWorker", "VPN should be active but isn't. Restarting from ResilienceWorker.")
            val intent = Intent(applicationContext, DnsVpnService::class.java)
            try {
                androidx.core.content.ContextCompat.startForegroundService(applicationContext, intent)
            } catch (e: Exception) {
                Log.e("ResilienceWorker", "Failed to restart service", e)
                return Result.retry()
            }
        }
        
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "vpn_resilience_check"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val request = PeriodicWorkRequestBuilder<ResilienceWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
