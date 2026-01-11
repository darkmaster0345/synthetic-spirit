package com.example.syntheticspirit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun BlocklistUrlScreen(viewModel: BlocklistUrlViewModel = viewModel()) {
    val urls by viewModel.urls.collectAsState()
    var newUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = newUrl,
                onValueChange = { newUrl = it },
                label = { Text("Blocklist URL") },
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { 
                if (newUrl.isNotBlank()) {
                    viewModel.addUrl(newUrl)
                    newUrl = ""
                }
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add URL")
            }
        }

        LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
            items(urls) { url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = url.url, modifier = Modifier.weight(1f))
                    IconButton(onClick = { viewModel.deleteUrl(url) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete URL")
                    }
                }
            }
        }
    }
}
