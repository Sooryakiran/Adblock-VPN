package com.soorkie.adblockvpn.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedDomainDao {

    @Query("SELECT domain FROM blocked_domains")
    fun observeAll(): Flow<List<String>>

    @Query("INSERT OR REPLACE INTO blocked_domains(domain, createdMs) VALUES(:domain, :nowMs)")
    suspend fun add(domain: String, nowMs: Long)

    @Query("DELETE FROM blocked_domains WHERE domain = :domain")
    suspend fun remove(domain: String)
}
