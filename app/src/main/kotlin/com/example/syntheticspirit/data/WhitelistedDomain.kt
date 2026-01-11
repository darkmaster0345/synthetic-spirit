package com.example.syntheticspirit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_whitelist")
data class WhitelistedDomain(
    @PrimaryKey val domain: String
)
