package com.soorkie.adblockvpn.data

import androidx.room.Entity

@Entity(tableName = "domain_stats", primaryKeys = ["domain", "appPackage"])
data class DomainStat(
    val domain: String,
    val appPackage: String,
    val appLabel: String,
    val count: Long,
    val lastSeenMs: Long,
)
