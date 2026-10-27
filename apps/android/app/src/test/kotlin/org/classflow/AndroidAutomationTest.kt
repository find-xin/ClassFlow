package org.classflow

import org.classflow.automation.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AndroidAutomationTest {
    private class Store : AutomationStore {
        var data = JSONObject("""{"semesters":[{"id":"sem","name":"Term","startDate":"2026-09-01","endDate":"2027-01-01","totalWeeks":20,"isCurrent":true}],"sections":[],"courses":[],"events":[],"tasks":[],"exceptions":[],"tombstones":[],"receipts":{}}""")
        var commits = 0
        override fun read() = JSONObject(data.toString())
        override fun commit(snapshot: JSONObject) { data = JSONObject(snapshot.toString()); commits++ }
    }
    private fun executor(store: Store) = AndroidAutomation(store, AutomationContract(File("src/main/assets/android_mcp_tools.json").readText()))
    private fun batch(id: String = "one", dry: Boolean = false) = JSONObject("""{"batch_id":"$id","dry_run":$dry,"operations":[{"kind":"events","action":"create","changes":{"semesterId":"sem","title":"Study","startDate":"2026-10-22","startTime":"09:00","endTime":"10:00","frequency":"daily"}}]}""")
    @Test fun atomicCommitReplayAndProjection() {
        val store = Store(); val executor = executor(store)
        executor.call("batch_edit_items", batch())
        assertEquals(1, store.commits)
        assertTrue(executor.call("batch_edit_items", batch()).getBoolean("replayed"))
        assertEquals(1, store.commits)
        val items = executor.call("query_timetable", JSONObject("""{"week":8,"day_of_week":4}"""))
        assertEquals(1, items.getInt("total"))
        assertEquals("Study", items.getJSONArray("items").getJSONObject(0).getString("title"))
    }
    @Test fun dryRunAndFailureDoNotWrite() {
        val store = Store(); val executor = executor(store)
        executor.call("batch_edit_items", batch(dry=true))
        val invalid = batch(); invalid.getJSONArray("operations").put(JSONObject("""{"kind":"events","action":"delete","id":"missing"}"""))
        assertThrows(IllegalArgumentException::class.java) { executor.call("batch_edit_items", invalid) }
        assertEquals(0, store.commits); assertEquals(0, store.data.getJSONArray("events").length())
    }
    @Test fun rejectUnknownFieldsBadDatesAndChangedReplay() {
        val store = Store(); val executor = executor(store)
        for ((field,value) in listOf("password" to "secret", "startDate" to "2026-02-30", "frequency" to "fake")) {
            val invalid = batch(); invalid.getJSONArray("operations").getJSONObject(0).getJSONObject("changes").put(field,value)
            assertThrows(IllegalArgumentException::class.java) { executor.call("batch_edit_items", invalid) }
        }
        executor.call("batch_edit_items", batch())
        val changed = batch(); changed.getJSONArray("operations").getJSONObject(0).getJSONObject("changes").put("title","Changed")
        assertThrows(IllegalArgumentException::class.java) { executor.call("batch_edit_items", changed) }
        assertEquals(1,store.commits)
    }
    @Test fun duplicatesKeepIndependentUUIDsAndDeletionReplaysSafely() {
        val store=Store(); val executor=executor(store)
        executor.call("batch_edit_items",batch("a")); executor.call("batch_edit_items",batch("b"))
        val id=store.data.getJSONArray("events").getJSONObject(0).getString("id")
        val delete=JSONObject("""{"batch_id":"delete","operations":[{"kind":"events","action":"delete","id":"$id"}]}""")
        executor.call("batch_edit_items",delete); executor.call("batch_edit_items",batch("c")); executor.call("batch_edit_items",delete)
        assertEquals(2,store.data.getJSONArray("events").length()); assertEquals(1,store.data.getJSONArray("tombstones").length())
    }
    @Test fun protocolDoesNotAdvertiseMacCapabilities() {
        val executor=executor(Store())
        val response=executor.rpc(JSONObject("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""))!!
        assertEquals(4,response.getJSONObject("result").getJSONArray("tools").length())
        assertNull(executor.rpc(JSONObject("""{"jsonrpc":"2.0","method":"notifications/initialized"}""")))
    }
    @Test fun syncGateRejectsAutomationDuringSync() {
        AutomationGate.beginSync()
        try { assertThrows(IllegalArgumentException::class.java) { AutomationGate.execute { error("must not execute") } } }
        finally { AutomationGate.endSync() }
        assertEquals(1,AutomationGate.execute { 1 })
    }
}
