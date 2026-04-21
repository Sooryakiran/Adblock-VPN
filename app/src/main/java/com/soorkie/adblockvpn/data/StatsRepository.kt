package com.soorkie.adblockvpn.data

import kotlinx.coroutines.flow.Flow

class StatsRepository(
    private val statDao: DomainStatDao,
    private val blockDao: BlockedDomainDao,
) {

    fun observeStats(): Flow<List<DomainStat>> = statDao.observeAll()
    fun observeBlockedDomains(): Flow<List<String>> = blockDao.observeAll()
    fun observeDomainCount(): Flow<Int> = statDao.observeDomainCount()
    fun observeRequestTotal(): Flow<Long> = statDao.observeRequestTotal()

    suspend fun setBlocked(domain: String, blocked: Boolean) {
        val d = domain.lowercase()
        if (blocked) blockDao.add(d, System.currentTimeMillis()) else blockDao.remove(d)
    }

    suspend fun recordQuery(domain: String, appPackage: String?, appLabel: String?) {
        val pkg = appPackage ?: UNKNOWN_PKG
        val label = appLabel ?: UNKNOWN_LABEL
        statDao.increment(domain.lowercase(), pkg, label, System.currentTimeMillis())
    }

    /** Clears traffic stats only; the blocklist persists. */
    suspend fun clear() = statDao.clearStats()

    companion object {
        const val UNKNOWN_PKG = "?"
        const val UNKNOWN_LABEL = "Unknown"
    }
}
