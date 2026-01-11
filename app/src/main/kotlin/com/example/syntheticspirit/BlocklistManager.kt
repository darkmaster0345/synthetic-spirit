package com.example.syntheticspirit

import android.content.Context
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.BlockedDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.net.URL

class BlocklistManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val blocklistUrlDao = db.blocklistUrlDao()
    private val blockedDomainDao = db.blockedDomainDao()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    init {
        blocklistUrlDao.getAll().onEach { urls ->
            val allDomains = mutableSetOf<String>()
            urls.forEach { url ->
                try {
                    val domains = downloadAndParseBlocklist(url.url)
                    allDomains.addAll(domains)
                } catch (e: Exception) {
                    // Log or handle the error
                }
            }
            updateBlockedDomains(allDomains)
        }.launchIn(coroutineScope)
    }

    private suspend fun downloadAndParseBlocklist(url: String): Set<String> = withContext(Dispatchers.IO) {
        val domains = mutableSetOf<String>()
        try {
            URL(url).openStream().bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmedLine = line.trim()
                    if (trimmedLine.isNotEmpty() && !trimmedLine.startsWith("#")) {
                        domains.add(trimmedLine)
                    }
                }
            }
        } catch (e: Exception) {
            // Log or handle the error
        }
        domains
    }

    private suspend fun updateBlockedDomains(domains: Set<String>) {
        blockedDomainDao.deleteAll()
        val blockedDomains = domains.map { BlockedDomain(domain = it) }
        blockedDomainDao.insertAll(blockedDomains)
    }
}
