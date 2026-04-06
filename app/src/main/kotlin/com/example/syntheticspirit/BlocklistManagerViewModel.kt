package com.example.syntheticspirit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syntheticspirit.data.AppDatabase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class BlocklistManagerViewModel(application: Application) : AndroidViewModel(application) {

    private val whitelistManager = WhitelistManager(application)
    private val db = AppDatabase.getDatabase(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery

    private val whitelistedDomains = MutableStateFlow<Set<String>>(emptySet())

    val filteredDomains = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            db.blockedDomainDao().searchDomains(query)
        }
        .combine(whitelistedDomains) { domains, whitelist ->
            domains.map { domain ->
                BlocklistItem(domain, whitelist.contains(domain))
            }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        loadWhitelistedDomains()
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
