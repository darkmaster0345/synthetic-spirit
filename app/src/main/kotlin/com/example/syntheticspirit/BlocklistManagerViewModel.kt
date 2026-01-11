package com.example.syntheticspirit

import android.app.Application
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class BlocklistManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val whitelistManager = WhitelistManager(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery

    private val _allDomains = MutableStateFlow<List<String>>(emptyList())

    private val whitelistedDomains = MutableStateFlow<Set<String>>(emptySet())

    val filteredDomains = combine(
        snapshotFlow { _searchQuery.value }.debounce(300),
        _allDomains,
        whitelistedDomains
    ) { query, domains, whitelist ->
        domains
            .filter { it.contains(query, ignoreCase = true) }
            .map { domain ->
                BlocklistItem(domain, whitelist.contains(domain))
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadBlockedDomains()
        loadWhitelistedDomains()
    }

    private fun loadBlockedDomains() {
        viewModelScope.launch {
            try {
                val inputStream = getApplication<Application>().assets.open("blocked_domains.txt")
                val reader = BufferedReader(InputStreamReader(inputStream))
                _allDomains.value = reader.readLines()
                reader.close()
            } catch (e: Exception) {
                // Handle error
            }
        }
    }

    private fun loadWhitelistedDomains() {
        whitelistedDomains.value = whitelistManager.getAllWhitelistedDomains()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onWhitelistClicked(domain: String, isCurrentlyWhitelisted: Boolean) {
        whitelistManager.setWhitelisted(domain, !isCurrentlyWhitelisted)
        loadWhitelistedDomains() // Refresh whitelist
    }
}
