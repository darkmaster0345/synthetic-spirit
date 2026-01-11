package com.example.syntheticspirit

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QueryLogScreen(onBack: () -> Unit) {
    val logs = DnsVpnService.dnsLogs
    val context = LocalContext.current

    fun shareLogs() {
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

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Row {
                IconButton(
                    onClick = { shareLogs() },
                    enabled = logs.isNotEmpty()
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share Logs")
                }
                TextButton(
                    onClick = { DnsVpnService.clearLogs() },
                    enabled = logs.isNotEmpty()
                ) {
                    Text("Clear")
                }
            }
        }

        LazyColumn {
            items(logs) { log ->
                val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val time = sdf.format(Date(log.timestamp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = log.domain)
                        Text(text = time, color = Color.Gray)
                    }
                    Text(
                        text = if (log.isBlocked) "Blocked" else "Allowed",
                        color = if (log.isBlocked) Color.Red else Color.Green
                    )
                }
            }
        }
    }
}
