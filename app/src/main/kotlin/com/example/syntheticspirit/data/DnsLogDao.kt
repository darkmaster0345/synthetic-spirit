package com.example.syntheticspirit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsLogDao {
    @Query("SELECT * FROM dns_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<DnsLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: DnsLog)

    @Query("DELETE FROM dns_logs")
    suspend fun clear()
}
