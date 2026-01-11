package com.example.syntheticspirit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlocklistUrlDao {
    @Query("SELECT * FROM blocklist_urls")
    fun getAll(): Flow<List<BlocklistUrl>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(url: BlocklistUrl)

    @Delete
    suspend fun delete(url: BlocklistUrl)
}
