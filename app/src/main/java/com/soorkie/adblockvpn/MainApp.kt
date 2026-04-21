package com.soorkie.adblockvpn

import android.app.Application
import com.soorkie.adblockvpn.data.AppDatabase
import com.soorkie.adblockvpn.data.StatsRepository

class MainApp : Application() {
    lateinit var repository: StatsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.get(this)
        repository = StatsRepository(db.domainStatDao(), db.blockedDomainDao())
    }

    companion object {
        @Volatile
        lateinit var instance: MainApp
            private set
    }
}
