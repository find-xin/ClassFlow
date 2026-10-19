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

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        org.classflow.data.local.WidgetDataManager.triggerWidgetUpdate(this)
    }
}
