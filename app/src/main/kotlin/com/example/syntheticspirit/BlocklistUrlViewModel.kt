package com.example.syntheticspirit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.syntheticspirit.data.AppDatabase
import com.example.syntheticspirit.data.BlocklistUrl
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BlocklistUrlViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val blocklistUrlDao = db.blocklistUrlDao()

    val urls: StateFlow<List<BlocklistUrl>> = blocklistUrlDao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    fun addUrl(url: String) {
        viewModelScope.launch {
            blocklistUrlDao.insert(BlocklistUrl(url))
        }
    }

    fun deleteUrl(url: BlocklistUrl) {
        viewModelScope.launch {
            blocklistUrlDao.delete(url)
        }
    }
}
