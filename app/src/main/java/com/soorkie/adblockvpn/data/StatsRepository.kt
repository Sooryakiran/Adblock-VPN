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

    /** Returns the current blocklist as a sorted list of domains. */
    suspend fun snapshotBlocklist(): List<String> = blockDao.snapshot()

    /**
     * Imports [domains] into the blocklist.
     * @param replace when true, the existing blocklist is cleared first.
     * @return the number of domains added to the blocklist (after de-dup).
     */
    suspend fun importBlocklist(domains: Collection<String>, replace: Boolean): Int {
        val now = System.currentTimeMillis()
        val cleaned = domains
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { BlockedDomain(it, now) }
            .toList()
        if (replace) blockDao.clearAll()
        blockDao.insertAll(cleaned)
        return cleaned.size
    }

    companion object {
        const val UNKNOWN_PKG = "?"
        const val UNKNOWN_LABEL = "Unknown"
    }
}
