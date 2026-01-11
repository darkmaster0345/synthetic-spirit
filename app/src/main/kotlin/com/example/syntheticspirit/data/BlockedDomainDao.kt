package com.example.syntheticspirit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {
    @Query("SELECT EXISTS(SELECT 1 FROM blocked_domains WHERE domain = :query)")
    suspend fun isBlocked(query: String): Boolean

    @Query("SELECT domain FROM blocked_domains WHERE domain LIKE '%' || :query || '%' LIMIT 100")
    fun searchDomains(query: String): Flow<List<String>>

    @Query("SELECT domain FROM blocked_domains WHERE category = :category LIMIT 100")
    fun getDomainsByCategory(category: String): Flow<List<String>>

    @Query("SELECT domain FROM blocked_domains LIMIT 100")
    fun getAllDomains(): Flow<List<String>>

    @Query("SELECT domain FROM blocked_domains")
    suspend fun getAllDomainsSync(): List<String>

    @Query("SELECT domain FROM blocked_domains")
    fun getAllDomainsCursor(): android.database.Cursor

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(domain: BlockedDomain)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(domains: List<BlockedDomain>)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun delete(domain: String)

    @Query("DELETE FROM blocked_domains")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM blocked_domains")
    suspend fun getCount(): Int
}
