package com.example.syntheticspirit.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedDomainDao {
    @Query("SELECT * FROM user_whitelist")
    fun getAll(): Flow<List<WhitelistedDomain>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(domain: WhitelistedDomain)

    @Delete
    suspend fun delete(domain: WhitelistedDomain)

    @Query("SELECT EXISTS(SELECT 1 FROM user_whitelist WHERE domain = :domain)")
    fun isWhitelisted(domain: String): Boolean
}
