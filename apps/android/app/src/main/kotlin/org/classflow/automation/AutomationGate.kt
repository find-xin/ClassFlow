package org.classflow.automation

/** A sync snapshot must not overwrite a concurrently acknowledged automation mutation. */
object AutomationGate {
    private var syncing = 0
    @Synchronized fun beginSync() { syncing++ }
    @Synchronized fun endSync() { syncing-- }
    @Synchronized fun <T> execute(block: () -> T): T {
        require(syncing == 0) { "正在云同步，请稍后使用新的请求重试" }
        return block()
    }
}
