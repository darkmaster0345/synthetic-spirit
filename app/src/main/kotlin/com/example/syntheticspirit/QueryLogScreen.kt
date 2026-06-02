package com.example.syntheticspirit

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueryLogScreen(onBack: () -> Unit, viewModel: QueryLogViewModel = viewModel()) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun shareLogs() {
        scope.launch(Dispatchers.IO) {
            val logText = logs.joinToString("\n") { log ->
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val time = sdf.format(Date(log.timestamp))
                val status = if (log.isBlocked) "Blocked" else "Allowed"
                "[$time] ${log.domain} - $status"
            }

            val sendIntent: Intent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, logText)
                type = "text/plain"
            }

            val shareIntent = Intent.createChooser(sendIntent, null)
            context.startActivity(shareIntent)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "Query Log",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
            Row {
                IconButton(
                    onClick = { shareLogs() },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = "Share Logs", tint = if (logs.isNotEmpty()) Color.White else Color.Gray)
                }
                TextButton(
                    onClick = { viewModel.clearLogs() },
                    enabled = logs.isNotEmpty()
                ) {
                    Text("Clear", color = if (logs.isNotEmpty()) Color.White else Color.Gray)
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val time = sdf.format(Date(log.timestamp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = log.domain, color = Color.White, style = MaterialTheme.typography.bodyLarge)
                        Text(text = time, color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(
                        text = if (log.isBlocked) "Blocked" else "Allowed",
                        color = if (log.isBlocked) Color(0xFFEF4444) else Color(0xFF10B981),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}
