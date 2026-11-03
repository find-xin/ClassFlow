package org.classflow

import android.app.Application
import androidx.room.Room
import org.classflow.data.local.ClassFlowDatabase

class ClassFlowApplication : Application() {
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val refreshReminders = Runnable {
        Thread { org.classflow.notifications.ReminderScheduler.rebuild(this) }.start()
    }
    private val dataListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in setOf("courses_json", "schedules_json", "sections_json", "semesters_json", "exceptions_json")) {
            handler.removeCallbacks(refreshReminders)
            handler.postDelayed(refreshReminders, 500)
        }
    }
    lateinit var database: ClassFlowDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        getSharedPreferences("classflow_courses_prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(dataListener)
        handler.post(refreshReminders)
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
