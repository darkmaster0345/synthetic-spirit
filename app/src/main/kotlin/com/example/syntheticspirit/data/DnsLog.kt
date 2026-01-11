package com.example.syntheticspirit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_logs")
data class DnsLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val isBlocked: Boolean,
    val timestamp: Long
)
