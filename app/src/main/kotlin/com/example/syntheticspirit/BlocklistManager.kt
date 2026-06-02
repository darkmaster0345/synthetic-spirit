package com.example.syntheticspirit

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class BlocklistManager(private val context: Context) {
    fun triggerImport() {
        val workRequest = OneTimeWorkRequestBuilder<BlocklistImportWorker>().build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
}
