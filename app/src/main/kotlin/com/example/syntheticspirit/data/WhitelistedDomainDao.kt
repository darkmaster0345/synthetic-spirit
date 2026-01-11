package com.example.syntheticspirit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedDomainDao {
    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_domains WHERE domain = :domain)")
    suspend fun isWhitelisted(domain: String): Boolean

    @Query("SELECT domain FROM whitelisted_domains")
    fun getAllWhitelisted(): Flow<List<String>>

    @Query("SELECT domain FROM whitelisted_domains")
    suspend fun getAllWhitelistedSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: WhitelistedDomain)

    @Query("DELETE FROM whitelisted_domains WHERE domain = :domain")
    suspend fun delete(domain: String)

    @Query("DELETE FROM whitelisted_domains")
    suspend fun deleteAll()
}
