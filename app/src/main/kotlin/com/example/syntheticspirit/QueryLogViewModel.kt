package com.example.syntheticspirit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.DnsLog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class QueryLogViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val dnsLogDao = db.dnsLogDao()

    val logs: StateFlow<List<DnsLog>> = dnsLogDao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    fun clearLogs() {
        viewModelScope.launch {
            dnsLogDao.clear()
        }
    }
}
