package com.soorkie.adblockvpn.data

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainStatDao {

    @Query("SELECT * FROM domain_stats ORDER BY count DESC, lastSeenMs DESC")
    fun observeAll(): Flow<List<DomainStat>>

    @Query("SELECT COUNT(DISTINCT domain) FROM domain_stats")
    fun observeDomainCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(count), 0) FROM domain_stats")
    fun observeRequestTotal(): Flow<Long>

    @Query(
        """
        INSERT INTO domain_stats(domain, appPackage, appLabel, count, lastSeenMs)
        VALUES(:domain, :appPackage, :appLabel, 1, :nowMs)
        ON CONFLICT(domain, appPackage) DO UPDATE SET
            count = count + 1,
            lastSeenMs = :nowMs,
            appLabel = :appLabel
        """
    )
    suspend fun increment(domain: String, appPackage: String, appLabel: String, nowMs: Long)

    @Query("DELETE FROM domain_stats")
    suspend fun clearStats()
}
