package org.classflow.hyperos

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat

/**
 * HyperOS Super Island (超级岛 / 焦点通知) Adapter.
 *
 * Current Status: [BLOCKED]
 *
 * Xiaomi Super Island APIs (`miui.focus.param`) require an enterprise Xiaomi Developer
 * Platform App ID and approved scenario whitelisting. Open-source self-builds cannot
 * display official island capsules.
 *
 * CRITICAL RULE: Under no circumstances does this adapter use `TYPE_APPLICATION_OVERLAY`
 * floating windows to fake dynamic island UI. If unverified or blocked, it gracefully falls
 * back to standard Android ongoing notifications (`NotificationCompat`).
 */
class SuperIslandAdapter(
    private val context: Context,
    private val registeredAppId: String? = null
) {
    companion object {
        const val CHANNEL_ID = "classflow_ongoing_class"
        const val CHANNEL_NAME = "当前上课状态"

        // Xiaomi HyperOS Focus Notification Extra Keys
        private const val EXTRA_MIUI_FOCUS_PARAM = "miui.focus.param"
        private const val PROP_HYPEROS_VERSION = "ro.miui.ui.version.name"
    }

    enum class IslandState {
        BLOCKED_UNREGISTERED,
        UNVERIFIED,
        ACTIVE,
        FALLBACK_STANDARD
    }

    /**
     * Determines whether the current device is running Xiaomi HyperOS.
     */
    fun isHyperOS(): Boolean {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            val version = getMethod.invoke(null, PROP_HYPEROS_VERSION) as? String
            version != null && version.isNotBlank()
        } catch (e: Throwable) {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        }
    }

    /**
     * Evaluates Super Island integration readiness.
     */
    fun evaluateIslandCapability(): IslandState {
        if (!isHyperOS()) {
            return IslandState.FALLBACK_STANDARD
        }
        if (registeredAppId.isNullOrBlank()) {
            // [BLOCKED]: Requires official Xiaomi Developer Platform registered App ID
            return IslandState.BLOCKED_UNREGISTERED
        }
        return IslandState.UNVERIFIED
    }

    /**
     * Ensures the notification channel exists with high importance.
     */
    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "正在上课或即将上课的持续状态提醒"
                setShowBadge(true)
                enableVibration(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    /**
     * Builds a notification for an active or upcoming course.
     * When Super Island is verified and configured, injects `miui.focus.param` payload.
     * Otherwise, falls back gracefully to standard Android NotificationCompat.
     */
    fun buildClassNotification(
        courseTitle: String,
        classroom: String?,
        timeRange: String,
        isOngoing: Boolean
    ): Notification {
        ensureNotificationChannel()

        val statusText = if (isOngoing) "正在上课中" else "即将开始"
        val locationText = classroom?.let { "教室：$it | " } ?: ""
        val contentText = "$locationText$timeRange"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("【$statusText】$courseTitle")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(isOngoing)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(false)

        val state = evaluateIslandCapability()
        if (state == IslandState.ACTIVE && !registeredAppId.isNullOrBlank()) {
            // Inject official Xiaomi focus extras if enterprise credentials verify
            val focusBundle = Bundle().apply {
                putString("param_app_id", registeredAppId)
                putString("param_scene", "schedule_class")
                putString("param_title", courseTitle)
                putString("param_sub_title", classroom ?: timeRange)
                putInt("param_status", if (isOngoing) 1 else 0)
            }
            builder.addExtras(Bundle().apply {
                putBundle(EXTRA_MIUI_FOCUS_PARAM, focusBundle)
            })
        }

        return builder.build()
    }
}
