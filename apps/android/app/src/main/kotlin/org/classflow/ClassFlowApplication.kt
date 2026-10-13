package org.classflow

import android.app.Application
import androidx.room.Room
import org.classflow.data.local.ClassFlowDatabase

class ClassFlowApplication : Application() {
    lateinit var database: ClassFlowDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(
            applicationContext,
            ClassFlowDatabase::class.java,
            "classflow_database"
        ).fallbackToDestructiveMigration().build()
    }
}
