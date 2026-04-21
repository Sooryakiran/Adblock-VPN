package com.soorkie.adblockvpn.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DomainStat::class, BlockedDomain::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun domainStatDao(): DomainStatDao
    abstract fun blockedDomainDao(): BlockedDomainDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "adblock_vpn.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
