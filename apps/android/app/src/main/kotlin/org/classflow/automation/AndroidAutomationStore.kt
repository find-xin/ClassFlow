package org.classflow.automation

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class AndroidAutomationStore(context: Context) : AutomationStore {
    private val prefs = context.getSharedPreferences("classflow_courses_prefs", Context.MODE_PRIVATE)
    private val keys = mapOf("semesters" to "semesters_json", "sections" to "sections_json", "courses" to "courses_json",
        "events" to "schedules_json", "tasks" to "study_tasks_json", "exceptions" to "exceptions_json", "tombstones" to "tombstones_json")
    override fun read() = JSONObject().also { out ->
        keys.forEach { (kind, key) -> out.put(kind, JSONArray(prefs.getString(key, "[]"))) }
        out.put("receipts", JSONObject(prefs.getString("automation_receipts_json", "{}")!!))
    }
    override fun commit(snapshot: JSONObject) {
        val before = read()
        fun write(value: JSONObject): Boolean {
            val editor = prefs.edit()
            keys.forEach { (kind, key) -> editor.putString(key, value.getJSONArray(kind).toString()) }
            return editor.putString("automation_receipts_json", value.getJSONObject("receipts").toString()).commit()
        }
        if (!write(snapshot)) {
            write(before)
            error("本地持久化失败")
        }
    }
}
