package com.example.syntheticspirit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BlocklistSettingsScreen(onBack: () -> Unit, viewModel: BlocklistViewModel = viewModel()) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredDomains by viewModel.filteredDomains.collectAsState()

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            TextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = { Text("Search Domains") }
            )
        }

        LazyColumn {
            items(filteredDomains) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = item.domain, modifier = Modifier.weight(1f))
                    Switch(
                        checked = !item.isWhitelisted,
                        onCheckedChange = { 
                            viewModel.onWhitelistClicked(item.domain, item.isWhitelisted)
                        }
                    )
                }
            }
        }
    }
}
