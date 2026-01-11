package com.example.syntheticspirit

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.BlockedDomain
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BlocklistImportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("vpn_prefs", Context.MODE_PRIVATE)
        val db = AppDatabase.getDatabase(applicationContext)

        try {
            val assetFileName = "blocked_domains.txt"
            val currentHash = calculateAssetHash(assetFileName)
            val lastImportedHash = prefs.getString("last_imported_hash", "")

            // Only import if hash changed or never imported
            if (currentHash != lastImportedHash) {
                Log.d("BlocklistWorker", "Importing new blocklist version. Hash: $currentHash")
                
                val domains = applicationContext.assets.open(assetFileName).bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith("#") }
                         .map { it.trim().lowercase() }
                         .map { domain ->
                            val category = when {
                                domain.contains("porn") || domain.contains("adult") || domain.contains("sex") || domain.contains("xxx") -> "Adult"
                                domain.contains("bet") || domain.contains("casino") || domain.contains("gamble") || domain.contains("lottery") || domain.contains("poker") -> "Gambling"
                                domain.contains("facebook") || domain.contains("instagram") || domain.contains("twitter") || domain.contains("tiktok") || domain.contains("reddit") || domain.contains("social") -> "Social Media"
                                else -> "General"
                            }
                            BlockedDomain(domain = domain, category = category)
                         }
                         .toList()
                }

                if (domains.isNotEmpty()) {
                    val totalChunks = (domains.size + 999) / 1000
                    var processedChunks = 0
                    
                    // Using chunked inserts to keep memory usage low and prevent SQLite bottlenecks
                    domains.chunked(1000).forEach { chunk ->
                        db.blockedDomainDao().insertAll(chunk)
                        processedChunks++
                        
                        // Report progress to UI
                        val progress = (processedChunks.toFloat() / totalChunks.toFloat())
                        androidx.work.Data.Builder()
                            .putFloat("progress", progress)
                            .build().let { setProgress(it) }
                    }
                    
                    prefs.edit {
                        putString("last_imported_hash", currentHash)
                        putBoolean("initial_import_done", true)
                    }
                    
                    Log.d("BlocklistWorker", "Successfully imported ${domains.size} domains.")
                    
                    // Notify VPN service to reload Bloom Filter
                    val intent = Intent(applicationContext, DnsVpnService::class.java).apply {
                        action = "RELOAD_BLOCKLIST"
                    }
                    applicationContext.startService(intent)
                }
            } else {
                Log.d("BlocklistWorker", "Blocklist integrity verified. No import needed.")
            }
            
            Result.success()
        } catch (e: Exception) {
            Log.e("BlocklistWorker", "Failed to import blocklist", e)
            Result.retry()
        }
    }

    private fun calculateAssetHash(fileName: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        applicationContext.assets.open(fileName).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead = input.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = input.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}