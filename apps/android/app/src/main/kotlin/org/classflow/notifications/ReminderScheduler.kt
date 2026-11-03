package org.classflow.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.classflow.data.local.CourseDataManager
import org.classflow.ui.MainActivity
import java.time.ZoneId

object ReminderScheduler {
    private const val PREFS = "classflow_reminder_state"
    private const val CHANNEL = "classflow_reminders"
    private fun alarm(context: Context, key: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, ReminderReceiver::class.java).setAction("org.classflow.REMINDER")
            .setData(Uri.Builder().scheme("classflow").authority("reminder").appendPath(key).build()),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun rebuild(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = context.getSystemService(AlarmManager::class.java)
        prefs.getStringSet("keys", emptySet()).orEmpty().forEach { manager.cancel(alarm(context, it)) }
        val semesters = CourseDataManager.loadSemesters(context)
        val semester = semesters.firstOrNull { it.isCurrent } ?: semesters.firstOrNull()
        if (semester == null) { prefs.edit().putStringSet("keys", emptySet()).apply(); return }
        val courses = CourseDataManager.loadCourses(context).filter { it.semesterId == semester.id }
        val events = CourseDataManager.loadSchedules(context).filter { it.semesterId == semester.id || (it.semesterId == null && semesters.firstOrNull()?.id == semester.id) }
        val now = System.currentTimeMillis()
        val plans = ReminderPlan.build(semester, courses, events, CourseDataManager.loadSectionTimes(context),
            CourseDataManager.loadExceptions(context), prefs.getBoolean("courses", true),
            prefs.getInt("lead", 15), now, ZoneId.systemDefault())
        for (plan in plans) {
            val pending = alarm(context, plan.key)
            try {
                if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms())
                    manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.fireAt, pending)
                else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.fireAt, pending)
            } catch (_: SecurityException) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.fireAt, pending)
            }
        }
        // Refill the horizon even if no business occurrence exists in the next week.
        val refresh = "__refresh__"
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now + 24 * 60 * 60_000L, alarm(context, refresh))
        prefs.edit().putStringSet("keys", (plans.map { it.key } + refresh).toSet()).apply()
    }

    fun deliver(context: Context, key: String) {
        if (key == "__refresh__") { rebuild(context); return }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val semesters = CourseDataManager.loadSemesters(context)
        val semester = semesters.firstOrNull { it.isCurrent } ?: semesters.firstOrNull() ?: return
        val now = System.currentTimeMillis()
        val plan = ReminderPlan.build(semester,
            CourseDataManager.loadCourses(context).filter { it.semesterId == semester.id },
            CourseDataManager.loadSchedules(context).filter { it.semesterId == semester.id || (it.semesterId == null && semesters.firstOrNull()?.id == semester.id) },
            CourseDataManager.loadSectionTimes(context), CourseDataManager.loadExceptions(context),
            prefs.getBoolean("courses", true), prefs.getInt("lead", 15), now - 60 * 60_000L, ZoneId.systemDefault())
            .firstOrNull { it.key == key && it.fireAt <= now && now - it.fireAt < 60 * 60_000L }
        if (plan != null && (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "课程与日程提醒", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_my_calendar)
                .setContentTitle(plan.title).setContentText(plan.location ?: "日程即将开始")
                .setContentIntent(open).setAutoCancel(true).build()
            try { manager.notify(key, 0, notification) } catch (_: SecurityException) { /* permission changed */ }
        }
        rebuild(context)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                if (intent.action == "org.classflow.REMINDER") intent.data?.lastPathSegment?.let { ReminderScheduler.deliver(context, it) }
                else ReminderScheduler.rebuild(context)
            } finally { pending.finish() }
        }.start()
    }
}

class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val pending = goAsync()
        Thread { try { ReminderScheduler.rebuild(context) } finally { pending.finish() } }.start()
    }
}
