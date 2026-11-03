package org.classflow.automation

import org.classflow.data.local.CourseDataManager
import org.classflow.data.local.CalendarCancellationCleanup
import org.classflow.data.local.TimelineProjection
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID

interface AutomationStore {
    /** Detached snapshot. Invalid storage must throw, never turn into an empty database. */
    fun read(): JSONObject
    /** Commit business data and replay receipts together, or throw. */
    fun applyCleanup(plan: CalendarCancellationCleanup.Plan): Unit = error("维护存储不可用")
    fun commit(snapshot: JSONObject)
}

/** Transport-independent executor; production invokes it serially on the Android main thread. */
class AndroidAutomation(private val store: AutomationStore, val contract: AutomationContract) {
    fun rpc(request: JSONObject): JSONObject? {
        val id = request.opt("id") ?: JSONObject.NULL
        if (!request.has("id")) return null
        val response = JSONObject().put("jsonrpc", "2.0").put("id", id)
        try {
            require(request.optString("jsonrpc") == "2.0") { "JSON-RPC 版本错误" }
            val result = when (request.optString("method")) {
                "initialize" -> JSONObject().put("protocolVersion", "2025-03-26")
                    .put("capabilities", JSONObject().put("tools", JSONObject()))
                    .put("serverInfo", JSONObject().put("name", "ClassFlow Android").put("version", "1.2.0-android.1"))
                    .put("instructions", "操作本 Android 设备；课程和日程批量编辑仅支持完整系列。Mac 和运行中专注会话不在此连接范围。")
                "ping" -> JSONObject()
                "tools/list" -> JSONObject().put("tools", contract.tools)
                "tools/call" -> {
                    val params = request.getJSONObject("params")
                    try {
                        require(!params.has("arguments") || params.opt("arguments") is JSONObject) { "arguments 必须是对象" }
                        val result = call(params.getString("name"), params.optJSONObject("arguments") ?: JSONObject())
                        JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", result.toString())))
                    } catch (e: IllegalArgumentException) {
                        toolError(e.message ?: "参数无效")
                    } catch (_: Exception) { toolError("本机存储或执行失败；请查询当前数据后再决定是否重试") }
                }
                else -> return response.put("error", JSONObject().put("code", -32601).put("message", "不支持此方法"))
            }
            response.put("result", result)
        } catch (_: Exception) { response.put("error", JSONObject().put("code", -32602).put("message", "请求格式无效")) }
        return response
    }

    private fun toolError(message: String) = JSONObject().put("isError", true)
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", message)))

    private data class CleanupPreview(val token: String, val started: Long, val plan: CalendarCancellationCleanup.Plan)
    private var cleanupPreview: CleanupPreview? = null

    fun call(name: String, args: JSONObject): JSONObject {
        contract.validate(args, contract.schema(name))
        val data = store.read()
        return when (name) {
            "preview_calendar_cancellation_cleanup" -> {
                val events = CourseDataManager.deserializeSchedules(data.getJSONArray("events").toString())
                val exceptions = CourseDataManager.deserializeExceptions(data.getJSONArray("exceptions").toString())
                val courses = CourseDataManager.deserializeCourses(data.getJSONArray("courses").toString())
                require(events.size == data.getJSONArray("events").length() && exceptions.size == data.getJSONArray("exceptions").length() &&
                    courses.size == data.getJSONArray("courses").length()) { "无法完整读取维护数据" }
                val plan = CalendarCancellationCleanup.preview(events, exceptions, courses.flatMap { it.schedules }.map { it.id }.toSet(),
                    removeRedundant = args.optBoolean("remove_redundant", true))
                val token = UUID.randomUUID().toString()
                cleanupPreview = CleanupPreview(token, System.nanoTime(), plan)
                val before = events.associateBy { it.id }
                val removed = plan.removed.groupBy { it.scheduleId.lowercase() }
                val series = JSONArray(plan.affectedSeries.map { event ->
                    val dates = (event.excludedDates - before[event.id]?.excludedDates.orEmpty().toSet()) +
                        removed[event.id.lowercase()].orEmpty().map { it.originalDate }
                    JSONObject().put("event_id", event.id).put("title", event.title).put("dates", JSONArray(dates.distinct().sorted()))
                })
                JSONObject().put("preview_token", token).put("expires_in_seconds", 600).put("series", series)
                    .put("migrated_dates", plan.migratedDates).put("removed_records", plan.removed.size)
                    .put("duplicate_records", plan.duplicateRecords).put("skipped_records", plan.skippedRecords)
                    .put("skip_policy", "保留课程、非重复或无归属、日期无效及存在冲突的取消记录。")
            }
            "apply_calendar_cancellation_cleanup" -> {
                val pending = cleanupPreview
                require(args.getBoolean("confirmed") && pending != null && args.getString("preview_token") == pending.token &&
                    System.nanoTime() - pending.started < 600_000_000_000L) { "需要用户确认及有效预览；请重新预览。" }
                cleanupPreview = null
                if (pending.plan.migratedDates > 0 || pending.plan.removed.isNotEmpty()) store.applyCleanup(pending.plan)
                JSONObject().put("applied", true).put("migrated_dates", pending.plan.migratedDates).put("removed_records", pending.plan.removed.size)
            }
            "get_status" -> JSONObject().put("platform", "android").put("executor", "this_device")
                .put("today", LocalDate.now().toString()).put("totals", totals(data)).put("tool_count", contract.tools.length())
            "get_editing_context" -> {
                val values = data.getJSONArray(args.getString("kind"))
                val offset = args.optInt("offset", 0)
                val end = minOf(values.length(), offset + args.optInt("limit", 50))
                val page = JSONArray()
                for (i in offset until end) page.put(values.get(i))
                JSONObject().put("items", page).put("totals", totals(data)).put("offset", offset)
                    .put("next_offset", if (end < values.length()) end else JSONObject.NULL)
            }
            "query_timetable" -> timetable(data, args)
            "batch_edit_items" -> batch(data, args)
            else -> throw IllegalArgumentException("未知工具")
        }
    }

    private fun totals(data: JSONObject): JSONObject = JSONObject().also { out ->
        for (kind in listOf("semesters", "sections", "courses", "events", "tasks", "exceptions")) out.put(kind, data.getJSONArray(kind).length())
    }

    private fun timetable(data: JSONObject, args: JSONObject): JSONObject {
        val semesters = CourseDataManager.deserializeSemesters(data.getJSONArray("semesters").toString())
        val sem = if (args.has("semester_id")) semesters.find { it.id == args.getString("semester_id") }
            else semesters.find { it.isCurrent } ?: semesters.firstOrNull()
        requireNotNull(sem) { "学期不存在，请先在 App 创建学期" }
        val currentWeek = TimelineProjection.calculateCurrentWeek(sem.startDate, sem.totalWeeks)
        val week = args.optInt("week", currentWeek)
        require(week in 1..sem.totalWeeks) { "周次超出学期范围" }
        val items = TimelineProjection.projectOccurrences(
            courses = CourseDataManager.deserializeCourses(data.getJSONArray("courses").toString()).filter { it.semesterId == sem.id },
            schedules = CourseDataManager.deserializeSchedules(data.getJSONArray("events").toString()).filter { it.semesterId == sem.id || it.semesterId.isNullOrBlank() },
            selectedWeek = week, currentWeek = currentWeek,
            sectionTimes = sem.sections.ifEmpty { CourseDataManager.deserializeSectionTimes(data.getJSONArray("sections").toString()) },
            totalWeeks = sem.totalWeeks, showNonCurrentWeekCourses = false, semesterStartDate = sem.startDate,
            exceptions = CourseDataManager.deserializeExceptions(data.getJSONArray("exceptions").toString())
        ).filter { !args.has("day_of_week") || it.dayOfWeek == args.getInt("day_of_week") }
        val out = JSONArray()
        items.forEach { item -> out.put(JSONObject().put("id", item.id).put("course_id", item.courseId)
            .put("event_id", item.calendarEventId).put("schedule_id", item.scheduleId).put("title", item.title)
            .put("date", item.date).put("day_of_week", item.dayOfWeek).put("start_time", item.startTime)
            .put("end_time", item.endTime).put("location", item.location).put("status", item.status.name.lowercase())) }
        return JSONObject().put("semester_id", sem.id).put("week", week).put("total", out.length()).put("items", out)
    }

    private fun batch(data: JSONObject, args: JSONObject): JSONObject {
        val batchId = args.getString("batch_id")
        require(batchId.isNotBlank()) { "batch_id 不能为空" }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical(args).toByteArray()).joinToString("") { "%02x".format(it) }
        val receipts = data.getJSONObject("receipts")
        receipts.optJSONObject(batchId)?.let {
            require(it.getString("digest") == digest) { "batch_id 已用于不同请求，请使用新 ID" }
            return JSONObject(it.getJSONObject("result").toString()).put("replayed", true)
        }
        require(receipts.length() < 10000) { "本机重放回执已达上限，停止写入以保留安全重试记录" }
        val working = JSONObject(data.toString())
        val results = JSONArray()
        val operations = args.getJSONArray("operations")
        for (i in 0 until operations.length()) results.put(edit(working, operations.getJSONObject(i)))
        val dryRun = args.optBoolean("dry_run", false)
        val result = JSONObject().put("batch_id", batchId).put("dry_run", dryRun).put("count", results.length()).put("items", results)
        if (!dryRun) {
            working.getJSONObject("receipts").put(batchId, JSONObject().put("digest", digest).put("result", result))
            store.commit(working)
        }
        return result
    }

    private fun edit(data: JSONObject, operation: JSONObject): JSONObject {
        val kind = operation.getString("kind")
        val action = operation.getString("action")
        val values = data.getJSONArray(kind)
        val id = if (action == "create") UUID.randomUUID().toString() else operation.optString("id")
        if (action == "create") require(!operation.has("id")) { "新建记录由本机生成 UUID" }
        val index = (0 until values.length()).firstOrNull { values.getJSONObject(it).getString("id") == id }
        require(action == "create" || index != null) { "目标 UUID 不存在" }
        val previous = index?.let { values.getJSONObject(it) }
        val changes = operation.optJSONObject("changes")
        if (action == "delete") {
            require(!operation.has("changes")) { "删除不能带 changes" }
            values.remove(index!!)
            tombstone(data, id, if (kind == "courses") "course" else "calendarEvent")
        } else {
            require(changes != null && changes.length() > 0) { "changes 不能为空" }
            contract.validate(changes, contract.fields(kind), "changes")
            val entity = previous?.let { JSONObject(it.toString()) } ?: JSONObject().put("id", id).put("color", if (kind == "courses") "#4F46E5" else "#FF5A5F")
            changes.keys().forEach { key -> entity.put(key, changes.get(key)) }
            require(entity.optString("semesterId").isNotBlank()) { "必须指定 semesterId" }
            val semesters = data.getJSONArray("semesters")
            val sem = (0 until semesters.length()).map { semesters.getJSONObject(it) }.find { it.getString("id") == entity.getString("semesterId") }
            requireNotNull(sem) { "学期不存在" }
            if (kind == "events" && (previous == null || changes.has("alertMinutesBefore"))) entity.put("reminderConfigured", true)
            if (kind == "courses") validateCourse(entity, previous, sem) else validateEvent(entity, sem)
            if (index == null) values.put(entity) else values.put(index, entity)
        }
        if (kind == "courses" && previous != null) {
            val oldSlots = previous.optJSONArray("schedules") ?: JSONArray()
            val newSlots = if (action == "delete") JSONArray() else values.getJSONObject(index!!).optJSONArray("schedules") ?: JSONArray()
            val newIds = (0 until newSlots.length()).map { newSlots.getJSONObject(it).getString("id") }.toSet()
            val removed = (0 until oldSlots.length()).map { oldSlots.getJSONObject(it).getString("id") }.filterNot { it in newIds }.toSet()
            removed.forEach { tombstone(data, it, "courseSchedule") }
            val exceptions = data.getJSONArray("exceptions")
            for (i in exceptions.length() - 1 downTo 0) if (exceptions.getJSONObject(i).optString("scheduleId") in removed) {
                tombstone(data, exceptions.getJSONObject(i).getString("id"), "scheduleException")
                exceptions.remove(i)
            }
        }
        return JSONObject().put("kind", kind).put("action", action).put("id", id)
    }

    private fun validateCourse(entity: JSONObject, previous: JSONObject?, sem: JSONObject) {
        require(entity.optString("name").isNotBlank()) { "课程名称不能为空" }
        val slots = entity.optJSONArray("schedules") ?: JSONArray().also { entity.put("schedules", it) }
        val prior = previous?.optJSONArray("schedules") ?: JSONArray()
        val priorIds = (0 until prior.length()).map { prior.getJSONObject(it).getString("id") }.toSet()
        val seen = mutableSetOf<String>()
        for (i in 0 until slots.length()) {
            val slot = slots.getJSONObject(i)
            if (!slot.has("id")) slot.put("id", UUID.randomUUID().toString())
            else require(slot.getString("id") in priorIds) { "时段 ID 必须属于当前课程；新时段请省略 ID" }
            require(seen.add(slot.getString("id"))) { "重复时段 ID" }
            require(slot.getInt("endSection") >= slot.getInt("startSection")) { "结束节次早于开始节次" }
            val customStart = slot.optString("customStartTime", "")
            val customEnd = slot.optString("customEndTime", "")
            require(customStart.isBlank() == customEnd.isBlank()) { "具体起止时间须同时填写" }
            if (customStart.isNotBlank()) require(customEnd > customStart) { "结束时间须晚于开始时间" }
            val rule = slot.optJSONObject("weekPattern") ?: continue
            require(rule.getInt("startWeek") <= rule.getInt("endWeek") && rule.getInt("endWeek") <= sem.getInt("totalWeeks")) { "时段周次超出学期" }
            if (rule.getString("type") == "custom") require((rule.optJSONArray("customWeeks")?.length() ?: 0) > 0) { "自选周次不能为空" }
            rule.optJSONArray("customWeeks")?.let { weeks -> for (w in 0 until weeks.length()) require(weeks.getInt(w) in 1..sem.getInt("totalWeeks")) { "周次超出学期" } }
        }
    }

    private fun validateEvent(entity: JSONObject, sem: JSONObject) {
        require(entity.optString("title").isNotBlank()) { "日程标题不能为空" }
        require(entity.has("startDate") && entity.has("startTime") && entity.has("endTime")) { "日程必须包含日期及起止时间" }
        val startDate = entity.getString("startDate")
        val endDate = entity.optString("endDate", startDate)
        require(endDate >= startDate) { "结束日期早于开始日期" }
        if (!entity.optBoolean("isAllDay") && endDate == startDate && entity.optString("timeMode") != "sections")
            require(entity.getString("endTime") > entity.getString("startTime")) { "结束时间须晚于开始时间" }
        if (entity.optString("timeMode") == "sections") require(entity.optInt("startSection", 0) > 0 && entity.optInt("endSection", 0) >= entity.getInt("startSection")) { "节次范围无效" }
        if (!entity.has("calendarId")) entity.put("calendarId", UUID.randomUUID().toString())
        when (entity.optString("frequency")) {
            "customDays" -> require((entity.optJSONArray("customDaysOfWeek")?.length() ?: 0) > 0) { "自选星期不能为空" }
            "customWeeks" -> require((entity.optJSONArray("customWeeks")?.length() ?: 0) > 0) { "自选周次不能为空" }
        }
        entity.optJSONArray("customWeeks")?.let { weeks -> for (i in 0 until weeks.length()) require(weeks.getInt(i) <= sem.getInt("totalWeeks")) { "周次超出学期" } }
        when (entity.optString("endRepeatType")) {
            "until" -> require(!entity.isNull("endRepeatUntil") && entity.getString("endRepeatUntil") >= startDate) { "重复截止日期无效" }
            "count" -> require(!entity.isNull("endRepeatCount") && entity.optInt("endRepeatCount") > 0) { "重复次数无效" }
        }
    }

    private fun tombstone(data: JSONObject, id: String, type: String) {
        data.getJSONArray("tombstones").put(JSONObject().put("entityId", id).put("entityType", type).put("deletedAt", System.currentTimeMillis()))
    }

    companion object {
        fun canonical(value: Any): String = when (value) {
            is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
            is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
            is String -> JSONObject.quote(value)
            else -> value.toString()
        }
    }
}
