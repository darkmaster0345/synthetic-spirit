package com.example.syntheticspirit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "blocklist_urls")
data class BlocklistUrl(
    @PrimaryKey val url: String
)
