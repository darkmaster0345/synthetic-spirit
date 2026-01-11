package com.example.syntheticspirit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "whitelisted_domains")
data class WhitelistedDomain(
    @PrimaryKey
    val domain: String
)
