package com.example.syntheticspirit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.WhitelistedDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class BlocklistViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val whitelistedDomainDao = db.whitelistedDomainDao()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery

    private val _allDomains = MutableStateFlow<List<String>>(emptyList())

    private val whitelistedDomains = whitelistedDomainDao.getAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredDomains = combine(_allDomains, _searchQuery, whitelistedDomains) { domains, query, whitelist ->
        domains
            .filter { it.contains(query, ignoreCase = true) }
            .map { domain ->
                BlocklistItem(domain, whitelist.any { it.domain == domain })
            }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadBlockedDomains()
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

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onWhitelistClicked(domain: String, isWhitelisted: Boolean) {
        viewModelScope.launch {
            if (isWhitelisted) {
                whitelistedDomainDao.delete(WhitelistedDomain(domain))
            } else {
                whitelistedDomainDao.insert(WhitelistedDomain(domain))
            }
        }
    }
}

data class BlocklistItem(val domain: String, val isWhitelisted: Boolean)
