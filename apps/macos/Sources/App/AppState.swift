import Foundation
import SwiftUI
import WidgetKit
import Observation
import ClassFlowCore

@Observable
@MainActor
public final class AppState {
    public var semesters: [Semester] = [] {
        didSet {
            invalidateOccurrencesCache()
        }
    }
    public var courses: [Course] = [] {
        didSet {
            invalidateOccurrencesCache()
        }
    }
    public var exceptions: [ScheduleException] = [] {
        didSet {
            invalidateOccurrencesCache()
        }
    }
    public var settings: AppSettings = AppSettings()
    public var calendarMappings: [CalendarMapping] = []

    public var currentSemesterId: UUID? {
        didSet {
            invalidateOccurrencesCache()
        }
    }
    public var selectedWeek: Int = 1 {
        didSet {
            invalidateOccurrencesCache()
        }
    }

    // UI Presentation state
    public var selectedOccurrence: TimelineItem? = nil
    public var editingCourse: Course? = nil
    public var editingOccurrence: TimelineItem? = nil
    public var editingCourseInitialMode: CourseEditSheet.EditMode = .fullCourse
    public var isAddingCourse: Bool = false
    public var prefilledSlotForAdd: (dayOfWeek: Int, startSection: Int, endSection: Int)? = nil
    public var isAddingCalendarEvent: Bool = false
    public var prefilledSlotForEvent: (dayOfWeek: Int, startSection: Int, endSection: Int)? = nil
    public var editingCalendarEvent: CalendarEvent? = nil
    public var editingPomodoroTask: PomodoroTask? = nil
    public var isEditingOccurrence: Bool = false
    public var isShowingSettings: Bool = false
    public var isShowingImportPreview: Bool = false
    public var stagedImportPackage: ClassFlowPackage? = nil
    public var stagedImportPreview: ImportPreview? = nil
    public var importErrorMessage: String? = nil

    public var isShowingCreateSemester: Bool = false
    public var isShowingManageSemesters: Bool = false
    public var isShowingEditSectionTimes: Bool = false
    public var editingSemesterForSections: Semester? = nil
    public var isShowingSectionProfiles: Bool = false
    public var editingProfile: TimetableProfile? = nil
    public var isShowingAppearance: Bool = false
    public var isShowingWidgets: Bool = false
    public var isShowingCourseList: Bool = false

    public var timetableProfiles: [TimetableProfile] = []
    public var activeProfileId: UUID? = nil
    public var focusSession: FocusSession?

    // Timetable Display Mode (Week vs Day View)
    public enum TimetableDisplayMode: String, CaseIterable, Identifiable {
        case week = "周"
        case day = "日"
        public var id: String { rawValue }
    }
    public var timetableDisplayMode: TimetableDisplayMode = .week
    public var selectedDayOfWeek: Int = {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return weekday == 1 ? 7 : (weekday - 1)
    }()

    // Calendar Events (通用日程) & Scheduling State
    public var calendarEvents: [CalendarEvent] = [] {
        didSet {
            invalidateOccurrencesCache()
        }
    }
    public var isShowingCreateScheduleItem: Bool = false
    public var selectedScheduleItemTab: String = "event" // "event" vs "task"

    // Pomodoro ToDo (番茄ToDo) State
    public var pomodoroTasks: [PomodoroTask] = []
    public var pomodoroRecords: [PomodoroRecord] = []
    public var tombstones: [SyncTombstone] = []
    public var activePomodoroTaskId: UUID? = nil
    public var isShowingNewTaskSheet: Bool = false

    private var automationReceiver: MCPDesktopCommandReceiver?
    private var calendarImportReceipts: [String: CalendarImportReceipt]?
    private let database: LocalDatabase
    public let timelineEngine: TimelineEngine
    private let mutationCoordinator: ScheduleMutationCoordinator
    public let backupService: ClassFlowBackupService
    public let icsExporter: ICSExporter
    public let icsImporter: ICSImporter

    // Calendar, Notifications & Widgets Services
    public let calendarService: EventKitCalendarService
    public let calendarPlanner: CalendarSyncPlanner
    public let notificationService: any NotificationServiceProtocol
    public let widgetDataStore: WidgetDataStore
    public let syncEngine: SyncEngine
    public private(set) var dataVersion: Int = 0
    private var autoSyncTask: Task<Void, Never>? = nil

    public init(
        database: LocalDatabase = LocalDatabase(),
        calendarService: EventKitCalendarService = EventKitCalendarService(),
        notificationService: any NotificationServiceProtocol = NotificationService(),
        widgetDataStore: WidgetDataStore = WidgetDataStore()
    ) {
        self.database = database
        self.timelineEngine = TimelineEngine()
        self.mutationCoordinator = ScheduleMutationCoordinator()
        self.backupService = ClassFlowBackupService()
        self.icsExporter = ICSExporter()
        self.icsImporter = ICSImporter()
        self.calendarService = calendarService
        self.calendarPlanner = CalendarSyncPlanner()
        self.notificationService = notificationService
        self.widgetDataStore = widgetDataStore

        let payload = database.load()
        let devId = payload.syncDeviceId ?? ("dev_mac_" + UUID().uuidString.prefix(8).lowercased())
        let seq = payload.syncSequence ?? 0
        self.syncEngine = SyncEngine(deviceId: devId, deviceName: Host.current().localizedName ?? "Apple Silicon Mac", initialSequence: seq)

        load(startup: true)
        setupDistributedObservers()
        if database.fileURL == LocalDatabase().fileURL { automationReceiver = MCPDesktopCommandReceiver(app: self) }
    }

    func executeAutomationTool(name: String, arguments: [String: Any]) async throws -> String {
        try database.validateForAutomation()
        if name == "trigger_cloud_sync" {
            guard let provider = createConfiguredStorageProvider() else {
                throw NSError(domain: "ClassFlowAutomation", code: 1, userInfo: [NSLocalizedDescriptionKey: "请先在 App 设置中配置云同步凭据。"])
            }
            let summary = try await executeCloudSync(storage: provider)
            return "云同步完成：推送 \(summary.uploadedOperationsCount) 项，拉取 \(summary.downloadedPeerOperationsCount) 项。"
        }
        let before = database.load()
        let handlers = MCPToolHandlers(database: database, enablesAppIntegration: false)
        let server = MCPServer(handlers: handlers)
        let result = try server.invokeTool(name: name, arguments: arguments)
        let reads: Set<String> = ["get_editing_context", "get_status", "query_timetable", "list_courses", "list_calendar_events", "list_tasks", "get_focus_statistics"]
        if reads.contains(name) || (["import_calendar_events", "batch_edit_items"].contains(name) && arguments["dry_run"] as? Bool == true) { return result }
        var after = database.load()
        recordAutomationChanges(before: before, after: after)
        after.syncDeviceId = syncEngine.deviceId
        after.syncSequence = syncEngine.latestSequence
        try database.save(after)
        load()
        if name == "switch_semester_or_week", let week = arguments["week"] as? Int { selectedWeek = week }
        updateWidgetData()
        await scheduleNotifications()
        if name == "open_app" {
            // Navigation must run in the main actor, with an acknowledgement to the caller.
            let tab = arguments["tab"] as? String ?? "courses"
            if tab == "settings" { isShowingSettings = true }
            else {
                settings.selectedTab = ["timetable": "courses", "focus": "study", "calendar": "schedule"][tab] ?? tab
                if settings.selectedTab == "study" { settings.enableStudyModule = true }
            }
            save()
            NSApplication.shared.activate(ignoringOtherApps: true)
        }
        return result
    }

    private func recordAutomationChanges(before: LocalDatabase.DatabasePayload, after: LocalDatabase.DatabasePayload) {
        func record<T: Encodable & Identifiable>(_ old: [T], _ new: [T], type: String) where T.ID == UUID {
            let encoder = JSONEncoder()
            encoder.outputFormatting = [.sortedKeys]
            for value in new {
                guard let data = try? encoder.encode(value) else { continue }
                let previous = old.first { $0.id == value.id }
                let previousData = previous.flatMap { try? encoder.encode($0) }
                if previousData != data {
                    _ = syncEngine.recordOperation(action: previous == nil ? "INSERT" : "UPDATE", entityType: type, entityId: value.id, payload: data)
                }
            }
            for value in old where !new.contains(where: { $0.id == value.id }) {
                _ = syncEngine.recordOperation(action: "DELETE", entityType: type, entityId: value.id)
            }
        }
        record(before.courses, after.courses, type: "course")
        record(before.semesters, after.semesters, type: "semester")
        record(before.exceptions, after.exceptions, type: "exception")
        record(before.calendarEvents, after.calendarEvents, type: "calendarEvent")
        record(before.pomodoroTasks, after.pomodoroTasks, type: "pomodoroTask")
        record(before.pomodoroRecords, after.pomodoroRecords, type: "pomodoroRecord")
    }

    private func setupDistributedObservers() {
        DistributedNotificationCenter.default().addObserver(
            forName: NSNotification.Name("org.classflow.databaseDidChange"),
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor [weak self] in
                self?.load()
            }
        }

        DistributedNotificationCenter.default().addObserver(
            forName: NSNotification.Name("org.classflow.commandTriggered"),
            object: nil,
            queue: .main
        ) { [weak self] notif in
            guard let urlStr = notif.userInfo?["url"] as? String, let url = URL(string: urlStr) else { return }
            Task { @MainActor [weak self] in
                self?.handleDeepLink(url)
            }
        }
    }

    public func load(startup: Bool = false) {
        let previousSemesterId = currentSemesterId
        let payload = database.load()
        self.semesters = payload.semesters
        self.courses = payload.courses
        self.exceptions = payload.exceptions
        self.settings = payload.settings
        self.calendarMappings = payload.calendarMappings
        self.timetableProfiles = payload.timetableProfiles
        self.activeProfileId = payload.activeProfileId ?? timetableProfiles.first?.id
        self.focusSession = payload.focusSession
        if let session = payload.focusSession { self.activePomodoroTaskId = session.taskId }
        self.pomodoroTasks = payload.pomodoroTasks
        self.pomodoroRecords = payload.pomodoroRecords
        self.calendarEvents = payload.calendarEvents
        self.calendarImportReceipts = payload.calendarImportReceipts
        self.tombstones = SyncEngine.garbageCollectTombstones(payload.tombstones)

        for idx in self.semesters.indices {
            if self.semesters[idx].sections.isEmpty {
                self.semesters[idx].sections = timetableProfiles.first?.sections ?? Semester.standardDefaultSections
            }
        }
        if activePomodoroTaskId == nil {
            activePomodoroTaskId = pomodoroTasks.first(where: { !$0.isCompleted })?.id
        }

        if let current = semesters.first(where: { $0.isCurrent }) ?? semesters.first {
            self.currentSemesterId = current.id
            if previousSemesterId != current.id { self.selectedWeek = calculateCurrentWeek(for: current) }
        }
        updateWidgetData()

        if startup && settings.syncOnStartup {
            if let provider = createConfiguredStorageProvider() {
                Task { [weak self] in
                    guard let self = self else { return }
                    _ = try? await self.executeCloudSync(storage: provider)
                }
            }
        }
        if startup { restartAutoSyncTimer() }
    }

    public func save() {
        for idx in self.semesters.indices {
            if self.semesters[idx].sections.isEmpty {
                self.semesters[idx].sections = timetableProfiles.first?.sections ?? Semester.standardDefaultSections
            }
        }
        self.courses = SyncEngine.consolidateCourses(courses)
        invalidateOccurrencesCache()
        self.tombstones = SyncEngine.garbageCollectTombstones(tombstones)
        let payload = LocalDatabase.DatabasePayload(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: settings,
            calendarMappings: calendarMappings,
            timetableProfiles: timetableProfiles,
            activeProfileId: activeProfileId,
            pomodoroTasks: pomodoroTasks,
            pomodoroRecords: pomodoroRecords,
            calendarEvents: calendarEvents,
            tombstones: tombstones,
            syncDeviceId: syncEngine.deviceId,
            syncSequence: syncEngine.latestSequence,
            focusSession: focusSession,
            calendarImportReceipts: calendarImportReceipts
        )
        do {
            try database.save(payload)
        } catch {
            print("[AppState] Error saving database: \(error)")
        }
        updateWidgetData()
        Task {
            await scheduleNotifications()
        }
    }

    // MARK: - BYOC Cloud Synchronization

    public func executeCloudSync(storage: StorageProvider) async throws -> SyncEngine.SyncSummary {
        syncEngine.queueInitialDataIfNeeded(
            courses: courses,
            exceptions: exceptions,
            semesters: semesters,
            calendarEvents: calendarEvents,
            pomodoroTasks: pomodoroTasks,
            pomodoroRecords: pomodoroRecords,
            timetableProfiles: timetableProfiles
        )
        var localCourses = courses
        var localExceptions = exceptions
        var localSemesters = semesters
        var localCalendarEvents = calendarEvents
        var localPomodoroTasks = pomodoroTasks
        var localPomodoroRecords = pomodoroRecords
        var localProfiles = timetableProfiles
        var localTombstones = tombstones

        let summary = try await syncEngine.executeSync(
            storage: storage,
            courses: &localCourses,
            exceptions: &localExceptions,
            semesters: &localSemesters,
            calendarEvents: &localCalendarEvents,
            pomodoroTasks: &localPomodoroTasks,
            pomodoroRecords: &localPomodoroRecords,
            timetableProfiles: &localProfiles,
            tombstones: &localTombstones
        )
        self.courses = SyncEngine.consolidateCourses(localCourses)
        self.exceptions = localExceptions
        self.semesters = localSemesters
        self.calendarEvents = localCalendarEvents
        self.pomodoroTasks = localPomodoroTasks
        self.pomodoroRecords = localPomodoroRecords
        self.timetableProfiles = localProfiles
        self.tombstones = localTombstones
        save()
        return summary
    }

    public func executeForceCloudPull(storage: StorageProvider) async throws -> SyncEngine.SyncSummary {
        var localCourses = courses
        var localExceptions = exceptions
        var localSemesters = semesters
        var localCalendarEvents = calendarEvents
        var localPomodoroTasks = pomodoroTasks
        var localPomodoroRecords = pomodoroRecords
        var localProfiles = timetableProfiles
        var localTombstones = tombstones

        let summary = try await syncEngine.executeForcePull(
            storage: storage,
            courses: &localCourses,
            exceptions: &localExceptions,
            semesters: &localSemesters,
            calendarEvents: &localCalendarEvents,
            pomodoroTasks: &localPomodoroTasks,
            pomodoroRecords: &localPomodoroRecords,
            timetableProfiles: &localProfiles,
            tombstones: &localTombstones
        )
        self.courses = SyncEngine.consolidateCourses(localCourses)
        self.exceptions = localExceptions
        self.semesters = localSemesters
        self.calendarEvents = localCalendarEvents
        self.pomodoroTasks = localPomodoroTasks
        self.pomodoroRecords = localPomodoroRecords
        self.timetableProfiles = localProfiles
        self.tombstones = localTombstones
        save()
        return summary
    }

    public func createConfiguredStorageProvider() -> (any StorageProvider)? {
        let providerType = CloudSettings.defaults.string(forKey: "sync_provider_type") ?? "webdav"
        if providerType == "webdav" {
            guard let url = CloudSettings.defaults.string(forKey: "webdav_url"), !url.isEmpty,
                  let user = CloudSettings.defaults.string(forKey: "webdav_user"), !user.isEmpty,
                  let pwd = CloudSettings.secret("webdav_pwd"), !pwd.isEmpty else {
                return nil
            }
            let prefix = CloudSettings.defaults.string(forKey: "webdav_path_prefix") ?? "ClassFlow/"
            let config = WebDAVStorageProvider.Configuration(
                serverURL: url,
                username: user,
                password: pwd,
                pathPrefix: prefix.isEmpty ? "ClassFlow/" : prefix
            )
            return WebDAVStorageProvider(config: config)
        } else {
            guard let ak = CloudSettings.secret("s3_access_key"), !ak.isEmpty,
                  let sk = CloudSettings.secret("s3_secret_key"), !sk.isEmpty,
                  let endpoint = CloudSettings.defaults.string(forKey: "s3_endpoint"), !endpoint.isEmpty,
                  let bucket = CloudSettings.defaults.string(forKey: "s3_bucket"), !bucket.isEmpty else {
                return nil
            }
            let region = CloudSettings.defaults.string(forKey: "s3_region") ?? ""
            let prefix = CloudSettings.defaults.string(forKey: "s3_path_prefix") ?? "classflow-sync/"
            let config = S3StorageProvider.Configuration(
                endpoint: endpoint,
                region: region,
                bucket: bucket,
                accessKey: ak,
                secretKey: sk,
                pathPrefix: prefix.isEmpty ? "classflow-sync/" : prefix
            )
            return S3StorageProvider(config: config)
        }
    }

    public func restartAutoSyncTimer() {
        autoSyncTask?.cancel()
        autoSyncTask = nil

        let intervalStr = settings.autoSyncInterval
        let seconds: Double
        switch intervalStr {
        case "15m": seconds = 15 * 60
        case "30m": seconds = 30 * 60
        case "1h": seconds = 60 * 60
        case "2h": seconds = 120 * 60
        case "6h": seconds = 360 * 60
        default: return
        }

        autoSyncTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: UInt64(seconds * 1_000_000_000))
                guard !Task.isCancelled, let self = self else { break }
                if let provider = self.createConfiguredStorageProvider() {
                    _ = try? await self.executeCloudSync(storage: provider)
                }
            }
        }
    }

    // MARK: - Pomodoro ToDo Management (番茄ToDo)

    public var currentSemesterPomodoroTasks: [PomodoroTask] {
        guard let current = currentSemester else { return [] }
        let defaultSemId = semesters.first?.id
        return pomodoroTasks.filter {
            $0.semesterId == current.id || ($0.semesterId == nil && current.id == defaultSemId)
        }
    }

    public func addPomodoroTask(_ task: PomodoroTask, setAsActive: Bool = true) {
        var t = task
        if t.semesterId == nil {
            t.semesterId = currentSemester?.id
        }
        pomodoroTasks.insert(t, at: 0)
        if setAsActive || activePomodoroTaskId == nil {
            activePomodoroTaskId = t.id
        }
        if let data = try? JSONEncoder().encode(t) {
            _ = syncEngine.recordOperation(action: "INSERT", entityType: "pomodoroTask", entityId: t.id, payload: data)
        }
        save()
    }

    public func startFocusForCourse(_ item: TimelineItem) {
        selectedOccurrence = nil
        let targetTasks = currentSemesterPomodoroTasks
        if let existing = targetTasks.first(where: { !$0.isCompleted && ($0.courseName == item.title || $0.courseId == item.courseId) }) {
            activePomodoroTaskId = existing.id
        } else {
            let newTask = PomodoroTask(
                semesterId: currentSemester?.id,
                title: "\(item.title) 课前预习与复习",
                courseId: item.courseId,
                courseName: item.title,
                estimatedPomodoros: 2,
                priority: .medium
            )
            addPomodoroTask(newTask, setAsActive: true)
        }
        settings.selectedTab = "study"
    }

    public func startFocusForTask(_ task: PomodoroTask) {
        activePomodoroTaskId = task.id
        settings.selectedTab = "study"
    }

    public func finishFocusSession() throws {
        let before = database.load()
        _ = try MCPToolHandlers(database: database).stopFocusTimer(arguments: [:])
        var after = database.load()
        recordAutomationChanges(before: before, after: after)
        after.syncDeviceId = syncEngine.deviceId
        after.syncSequence = syncEngine.latestSequence
        try database.save(after)
        load()
    }

    public func updatePomodoroTask(_ task: PomodoroTask) {
        if let idx = pomodoroTasks.firstIndex(where: { $0.id == task.id }) {
            pomodoroTasks[idx] = task
            if let data = try? JSONEncoder().encode(task) {
                _ = syncEngine.recordOperation(action: "UPDATE", entityType: "pomodoroTask", entityId: task.id, payload: data)
            }
            save()
        }
    }

    public func toggleTaskCompletion(id: UUID) {
        if let idx = pomodoroTasks.firstIndex(where: { $0.id == id }) {
            pomodoroTasks[idx].isCompleted.toggle()
            pomodoroTasks[idx].completedAt = pomodoroTasks[idx].isCompleted ? Date() : nil
            if pomodoroTasks[idx].isCompleted, let semester = semesters.first(where: { $0.id == pomodoroTasks[idx].semesterId }) ?? currentSemester,
               let next = TaskRecurrence.next(after: pomodoroTasks[idx], semester: semester), !pomodoroTasks.contains(where: { $0.id == next.id }) {
                pomodoroTasks.append(next)
                if let data = try? JSONEncoder().encode(next) { _ = syncEngine.recordOperation(action: "CREATE", entityType: "pomodoroTask", entityId: next.id, payload: data) }
            }
            if let data = try? JSONEncoder().encode(pomodoroTasks[idx]) {
                _ = syncEngine.recordOperation(action: "UPDATE", entityType: "pomodoroTask", entityId: id, payload: data)
            }
            save()
        }
    }

    public func deletePomodoroTask(id: UUID) {
        pomodoroTasks.removeAll(where: { $0.id == id })
        if activePomodoroTaskId == id {
            activePomodoroTaskId = pomodoroTasks.first(where: { !$0.isCompleted })?.id
        }
        tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "pomodoroTask"))
        _ = syncEngine.recordOperation(action: "DELETE", entityType: "pomodoroTask", entityId: id)
        save()
    }

    public func recordCompletedPomodoro(
        taskId: UUID?,
        taskTitle: String,
        courseName: String?,
        focusMode: FocusMode,
        durationMinutes: Int,
        tag: String = "日常自习"
    ) {
        let record = PomodoroRecord(
            taskId: taskId,
            taskTitle: taskTitle,
            courseName: courseName,
            focusMode: focusMode,
            durationMinutes: durationMinutes,
            completedAt: Date(),
            tag: tag
        )
        pomodoroRecords.insert(record, at: 0)
        if let data = try? JSONEncoder().encode(record) {
            _ = syncEngine.recordOperation(action: "INSERT", entityType: "pomodoroRecord", entityId: record.id, payload: data)
        }

        if let tId = taskId, let idx = pomodoroTasks.firstIndex(where: { $0.id == tId }) {
            pomodoroTasks[idx].completedPomodoros += 1
            pomodoroTasks[idx].totalFocusMinutes += durationMinutes
            if pomodoroTasks[idx].completedPomodoros >= pomodoroTasks[idx].estimatedPomodoros && !pomodoroTasks[idx].isCompleted {
                pomodoroTasks[idx].isCompleted = true
                pomodoroTasks[idx].completedAt = Date()
            }
            if let tData = try? JSONEncoder().encode(pomodoroTasks[idx]) {
                _ = syncEngine.recordOperation(action: "UPDATE", entityType: "pomodoroTask", entityId: tId, payload: tData)
            }
        }
        save()
    }

    public func clearPomodoroRecords() {
        pomodoroRecords.removeAll()
        save()
    }

    // MARK: - Timetable Profile Management (Images 1 & 2)

    public func selectTimetableProfile(id: UUID) {
        guard let profile = timetableProfiles.first(where: { $0.id == id }) else { return }
        self.activeProfileId = id
        if let currentSem = currentSemester {
            updateSectionTimes(semesterId: currentSem.id, sections: profile.sections)
        }
        save()
    }

    public func saveTimetableProfile(_ profile: TimetableProfile) {
        if let idx = timetableProfiles.firstIndex(where: { $0.id == profile.id }) {
            timetableProfiles[idx] = profile
        } else {
            timetableProfiles.append(profile)
        }
        if activeProfileId == profile.id || activeProfileId == nil {
            activeProfileId = profile.id
            if let currentSem = currentSemester {
                updateSectionTimes(semesterId: currentSem.id, sections: profile.sections)
            }
        }
        save()
    }

    public func deleteTimetableProfile(id: UUID) {
        guard timetableProfiles.count > 1 else { return }
        timetableProfiles.removeAll(where: { $0.id == id })
        if activeProfileId == id {
            activeProfileId = timetableProfiles.first?.id
            if let first = timetableProfiles.first, let currentSem = currentSemester {
                updateSectionTimes(semesterId: currentSem.id, sections: first.sections)
            }
        }
        save()
    }

    // MARK: - Computed Properties

    public var currentSemester: Semester? {
        guard let id = currentSemesterId else {
            if var first = semesters.first {
                if first.sections.isEmpty {
                    first.sections = timetableProfiles.first?.sections ?? Semester.standardDefaultSections
                }
                return first
            }
            return nil
        }
        if var sem = semesters.first(where: { $0.id == id }) ?? semesters.first {
            if sem.sections.isEmpty {
                sem.sections = timetableProfiles.first?.sections ?? Semester.standardDefaultSections
            }
            return sem
        }
        return nil
    }

    public var calculatedCurrentWeek: Int {
        guard let current = currentSemester else { return 1 }
        return calculateCurrentWeek(for: current)
    }

    public var isViewingCurrentWeek: Bool {
        selectedWeek == calculatedCurrentWeek
    }

    // MARK: - Performance Optimized Caching & Reusable Formatters

    private static let isoDateFormatter: DateFormatter = {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        df.locale = Locale(identifier: "en_US_POSIX")
        return df
    }()

    public nonisolated static func normalizeFrequency(_ freq: String?) -> String {
        guard let freq = freq?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(), !freq.isEmpty else {
            return "none"
        }
        if freq.contains("none") || freq.contains("不重复") || freq.contains("一次性") {
            return "none"
        } else if freq.contains("workday") || freq.contains("工作日") {
            return "workdays"
        } else if freq.contains("weekend") || freq.contains("周末") {
            return "weekends"
        } else if freq.contains("daily") || freq.contains("每天") || freq == "日" {
            return "daily"
        } else if freq.contains("biweekly") || freq.contains("双周") || freq.contains("隔周") {
            return "biweekly"
        } else if freq.contains("customdays") || freq.contains("自定义每周某几天") {
            return "customDays"
        } else if freq.contains("customweeks") || freq.contains("自定义周数") || freq.contains("周次") {
            return "customWeeks"
        } else if freq.contains("weekly") || freq.contains("每周") || freq == "周" {
            return "weekly"
        } else if freq.contains("monthly") || freq.contains("每月") || freq == "月" {
            return "monthly"
        }
        return "none"
    }

    private var _cachedOccurrences: [TimelineItem]? = nil
    private var _cachedWeekDates: [String]? = nil
    private var _cachedWeekNumber: Int? = nil
    private var _cachedSemesterId: UUID? = nil
    private var _cachedDataVersion: Int = -1
    private var _cachedShowNonCurrent: Bool = true

    public func invalidateOccurrencesCache() {
        dataVersion += 1
        _cachedOccurrences = nil
        _cachedWeekDates = nil
        _cachedWeekNumber = nil
        _cachedSemesterId = nil
    }

    public var currentWeekDates: [String] {
        guard let current = currentSemester else { return [] }
        if let cached = _cachedWeekDates, _cachedWeekNumber == selectedWeek, _cachedSemesterId == current.id {
            return cached
        }
        let dates = timelineEngine.weekCalculator.dateStringsInWeek(
            weekNumber: selectedWeek,
            semesterStartDate: current.startDate
        )
        _cachedWeekDates = dates
        return dates
    }

    public var currentWeekOccurrences: [TimelineItem] {
        guard let current = currentSemester else { return [] }
        let currentCourses = courses.filter { $0.semesterId == current.id }

        if let cached = _cachedOccurrences,
           _cachedDataVersion == dataVersion,
           _cachedWeekNumber == selectedWeek,
           _cachedSemesterId == current.id,
           _cachedShowNonCurrent == settings.showNonCurrentWeekCourses {
            return cached
        }

        var items = timelineEngine.generateOccurrences(
            semester: current,
            courses: currentCourses,
            exceptions: exceptions,
            forWeek: selectedWeek,
            activeWeek: calculatedCurrentWeek,
            showNonCurrentWeekCourses: settings.showNonCurrentWeekCourses
        )

        let weekDates = currentWeekDates
        let defaultSemId = semesters.first?.id
        let currentEvents = calendarEvents.filter {
            $0.semesterId == current.id || ($0.semesterId == nil && current.id == defaultSemId)
        }

        for event in currentEvents {
            let eventItems = projectCalendarEvent(event, weekDates: weekDates, semester: current)
            items.append(contentsOf: eventItems)
        }

        _cachedOccurrences = items
        _cachedWeekNumber = selectedWeek
        _cachedSemesterId = current.id
        _cachedDataVersion = dataVersion
        _cachedShowNonCurrent = settings.showNonCurrentWeekCourses
        return items
    }

    private func projectCalendarEvent(_ event: CalendarEvent, weekDates: [String], semester: Semester) -> [TimelineItem] {
        let week = weekDates.first.flatMap { timelineEngine.weekCalculator.weekIndex(for: $0, semesterStartDate: semester.startDate) } ?? selectedWeek
        return CalendarEventProjector().project(event, semester: semester, week: week, exceptions: exceptions)
    }

    // MARK: - Calendar Event CRUD

    public func addCalendarEvent(_ event: CalendarEvent) {
        var ev = event
        if ev.semesterId == nil {
            ev.semesterId = currentSemester?.id
        }
        calendarEvents.append(ev)
        if let data = try? JSONEncoder().encode(ev) {
            _ = syncEngine.recordOperation(action: "INSERT", entityType: "calendarEvent", entityId: ev.id, payload: data)
        }
        save()
    }

    public func updateCalendarEvent(_ event: CalendarEvent) {
        if let idx = calendarEvents.firstIndex(where: { $0.id == event.id }) {
            calendarEvents[idx] = event
            if let data = try? JSONEncoder().encode(event) {
                _ = syncEngine.recordOperation(action: "UPDATE", entityType: "calendarEvent", entityId: event.id, payload: data)
            }
            save()
        }
    }

    public func deleteCalendarEvent(id: UUID) {
        calendarEvents.removeAll(where: { $0.id == id })
        exceptions.removeAll(where: { $0.scheduleId == id })
        tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "calendarEvent"))
        _ = syncEngine.recordOperation(action: "DELETE", entityType: "calendarEvent", entityId: id)
        save()
    }

    public func deleteCalendarEvent(id: UUID, scope: EditingScope, occurrenceDate: String) {
        guard let event = calendarEvents.first(where: { $0.id == id }) else { return }
        switch scope {
        case .thisOnly:
            let ex = ScheduleException(
                id: UUID(),
                scheduleId: event.id,
                originalDate: occurrenceDate,
                exceptionType: .cancel,
                reason: "单次删除"
            )
            exceptions.append(ex)
            save()
        case .thisAndFuture:
            let df = Self.isoDateFormatter
            let startDStr = event.startDate ?? event.startTime.split(separator: "T").first.map(String.init) ?? event.startTime.split(separator: " ").first.map(String.init) ?? occurrenceDate
            if occurrenceDate <= startDStr {
                deleteCalendarEvent(id: id)
            } else {
                guard let curDate = df.date(from: occurrenceDate),
                      let prevDate = Calendar.current.date(byAdding: .day, value: -1, to: curDate) else {
                    deleteCalendarEvent(id: id)
                    return
                }
                var updated = event
                let prevDateStr = df.string(from: prevDate)
                updated.endRepeatType = "untilDate"
                updated.endRepeatUntil = prevDateStr
                updated.endDate = prevDateStr
                if let weeks = updated.customWeeks {
                    let curWeek = selectedWeek
                    let filtered = weeks.filter { $0 < curWeek }
                    if filtered.isEmpty {
                        deleteCalendarEvent(id: id)
                        return
                    }
                    updated.customWeeks = filtered
                }
                updateCalendarEvent(updated)
            }
        case .all:
            deleteCalendarEvent(id: id)
        }
    }

    public func cancelCalendarEvent(id: UUID, occurrenceDate: String) {
        let ex = ScheduleException(
            id: UUID(),
            scheduleId: id,
            originalDate: occurrenceDate,
            exceptionType: .cancel,
            reason: "停课"
        )
        exceptions.append(ex)
        save()
    }

    public func calculateCurrentWeek(for semester: Semester) -> Int {
        let todayStr = timelineEngine.weekCalculator.formatDate(Date())
        let week = timelineEngine.weekCalculator.weekIndex(for: todayStr, semesterStartDate: semester.startDate) ?? 1
        return max(1, min(week, semester.totalWeeks))
    }

    // MARK: - Navigation

    public func nextWeek() {
        guard let semester = currentSemester else { return }
        if selectedWeek < semester.totalWeeks {
            selectedWeek += 1
        }
    }

    public func previousWeek() {
        if selectedWeek > 1 {
            selectedWeek -= 1
        }
    }

    public func jumpToCurrentWeek() {
        selectedWeek = calculatedCurrentWeek
    }

    public func selectPreviousDay() {
        if selectedDayOfWeek > 1 {
            selectedDayOfWeek -= 1
        } else {
            previousWeek()
            selectedDayOfWeek = 7
        }
    }

    public func selectNextDay() {
        if selectedDayOfWeek < 7 {
            selectedDayOfWeek += 1
        } else {
            nextWeek()
            selectedDayOfWeek = 1
        }
    }

    public func selectToday() {
        jumpToCurrentWeek()
        let weekday = Calendar.current.component(.weekday, from: Date())
        selectedDayOfWeek = weekday == 1 ? 7 : (weekday - 1)
    }

    public func selectSemester(id: UUID) {
        guard let semester = semesters.first(where: { $0.id == id }) else { return }
        self.currentSemesterId = id
        for i in 0..<semesters.count {
            semesters[i].isCurrent = (semesters[i].id == id)
        }
        self.selectedWeek = calculateCurrentWeek(for: semester)
        save()
    }

    // MARK: - Semester Management

    @discardableResult
    public func createSemester(
        name: String,
        startDate: String,
        totalWeeks: Int = 20,
        sections: [SectionTime]? = nil
    ) -> Semester {
        let defaultSections = sections ?? defaultSectionTimes()
        let cal = Calendar.current
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let startD = df.date(from: startDate) ?? Date()
        let endD = cal.date(byAdding: .weekOfYear, value: totalWeeks, to: startD) ?? startD
        let endDateStr = df.string(from: endD)

        let newSemester = Semester(
            id: UUID(),
            name: name,
            startDate: startDate,
            endDate: endDateStr,
            totalWeeks: totalWeeks,
            isCurrent: true,
            sections: defaultSections
        )

        for i in 0..<semesters.count {
            semesters[i].isCurrent = false
        }
        semesters.append(newSemester)
        self.currentSemesterId = newSemester.id
        self.selectedWeek = calculateCurrentWeek(for: newSemester)
        save()
        return newSemester
    }

    public func updateSemester(_ semester: Semester) {
        if let idx = semesters.firstIndex(where: { $0.id == semester.id }) {
            semesters[idx] = semester
            save()
        }
    }

    public func deleteSemester(id: UUID) {
        guard semesters.count > 1 else { return }
        let defaultSemId = semesters.first?.id
        semesters.removeAll(where: { $0.id == id })
        courses.removeAll(where: { $0.semesterId == id })
        calendarEvents.removeAll(where: { $0.semesterId == id || ($0.semesterId == nil && id == defaultSemId) })
        pomodoroTasks.removeAll(where: { $0.semesterId == id || ($0.semesterId == nil && id == defaultSemId) })
        tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "semester"))
        _ = syncEngine.recordOperation(action: "DELETE", entityType: "semester", entityId: id)
        if currentSemesterId == id {
            if let first = semesters.first {
                selectSemester(id: first.id)
            }
        } else {
            save()
        }
    }

    public func updateSectionTimes(semesterId: UUID, sections: [SectionTime]) {
        if let idx = semesters.firstIndex(where: { $0.id == semesterId }) {
            semesters[idx].sections = sections.sorted(by: { $0.section < $1.section })
            save()
        }
    }

    public static func standardSectionTimes() -> [SectionTime] {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 30), (9, 15)),
            ((9, 20), (10, 05)),
            ((10, 15), (11, 00)),
            ((11, 05), (11, 50)),
            ((14, 30), (15, 15)),
            ((15, 20), (16, 05)),
            ((16, 15), (17, 00)),
            ((17, 05), (17, 50)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 05)),
            ((20, 15), (21, 00)),
            ((21, 05), (21, 50))
        ]
        return defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
    }

    public static func earlySectionTimes() -> [SectionTime] {
        let defaultSlots: [(start: (Int, Int), end: (Int, Int))] = [
            ((8, 00), (8, 45)),
            ((8, 50), (9, 35)),
            ((10, 05), (10, 50)),
            ((10, 55), (11, 40)),
            ((14, 00), (14, 45)),
            ((14, 50), (15, 35)),
            ((15, 55), (16, 40)),
            ((16, 45), (17, 30)),
            ((18, 30), (19, 15)),
            ((19, 20), (20, 05)),
            ((20, 10), (20, 55)),
            ((21, 00), (21, 45))
        ]
        return defaultSlots.enumerated().map { index, slot in
            let sStr = String(format: "%02d:%02d", slot.start.0, slot.start.1)
            let eStr = String(format: "%02d:%02d", slot.end.0, slot.end.1)
            return SectionTime(section: index + 1, startTime: sStr, endTime: eStr)
        }
    }

    public func defaultSectionTimes() -> [SectionTime] {
        Self.standardSectionTimes()
    }

    // MARK: - Course Management & Mutations

    public func startAddingCourse(dayOfWeek: Int? = nil, startSection: Int? = nil, endSection: Int? = nil) {
        if let day = dayOfWeek, let start = startSection {
            let maxSec = currentSemester?.sections.count ?? 12
            let end = endSection ?? max(start, min(start + 1, maxSec))
            self.prefilledSlotForAdd = (day, start, end)
        } else {
            self.prefilledSlotForAdd = nil
        }
        self.isAddingCourse = true
    }

    public func startAddingCalendarEvent(dayOfWeek: Int? = nil, section: Int? = nil) {
        if let day = dayOfWeek, let sec = section {
            self.prefilledSlotForEvent = (day, sec, sec)
        } else {
            self.prefilledSlotForEvent = nil
        }
        self.isAddingCalendarEvent = true
    }

    public func startEditingCalendarEvent(_ event: CalendarEvent) {
        self.editingCalendarEvent = event
    }

    public func startEditingPomodoroTask(_ task: PomodoroTask) {
        self.editingPomodoroTask = task
    }

    public func addCourse(_ course: Course) {
        courses.append(course)
        if let data = try? JSONEncoder().encode(course) {
            _ = syncEngine.recordOperation(action: "INSERT", entityType: "course", entityId: course.id, payload: data)
        }
        save()
    }

    public func updateCourse(_ course: Course) {
        if let idx = courses.firstIndex(where: { $0.id == course.id }) {
            courses[idx] = course
            if let data = try? JSONEncoder().encode(course) {
                _ = syncEngine.recordOperation(action: "UPDATE", entityType: "course", entityId: course.id, payload: data)
            }
            save()
        }
    }

    public func deleteCourse(id: UUID) {
        if let course = courses.first(where: { $0.id == id }) {
            for sched in course.schedules {
                tombstones.append(SyncTombstone(entityId: sched.id.uuidString, entityType: "schedule"))
                _ = syncEngine.recordOperation(action: "DELETE", entityType: "schedule", entityId: sched.id)
            }
        }
        courses.removeAll(where: { $0.id == id })
        tombstones.append(SyncTombstone(entityId: id.uuidString, entityType: "course"))
        _ = syncEngine.recordOperation(action: "DELETE", entityType: "course", entityId: id)
        save()
    }

    public func applyMutation(
        courseId: UUID,
        scheduleId: UUID,
        scope: EditingScope,
        occurrenceDate: String,
        updatedDayOfWeek: Int,
        updatedStartSection: Int,
        updatedEndSection: Int,
        updatedClassroom: String?
    ) {
        guard let course = courses.first(where: { $0.id == courseId }),
              let semester = currentSemester else { return }

        let result = mutationCoordinator.applyScheduleMutation(
            course: course,
            scheduleId: scheduleId,
            scope: scope,
            currentWeek: selectedWeek,
            totalWeeks: semester.totalWeeks,
            occurrenceDate: occurrenceDate,
            updatedDayOfWeek: updatedDayOfWeek,
            updatedStartSection: updatedStartSection,
            updatedEndSection: updatedEndSection,
            updatedClassroom: updatedClassroom
        )

        updateCourse(result.updatedCourse)
        exceptions.append(contentsOf: result.newExceptions)
        let encoder = JSONEncoder()
        for ex in result.newExceptions {
            if let data = try? encoder.encode(ex) {
                _ = syncEngine.recordOperation(action: "INSERT", entityType: "exception", entityId: ex.id, payload: data)
            }
        }
        save()
    }

    public func cancelSchedule(
        courseId: UUID,
        scheduleId: UUID,
        scope: EditingScope,
        occurrenceDate: String
    ) {
        guard let course = courses.first(where: { $0.id == courseId }),
              let semester = currentSemester else { return }

        let result = mutationCoordinator.cancelSchedule(
            course: course,
            scheduleId: scheduleId,
            scope: scope,
            currentWeek: selectedWeek,
            totalWeeks: semester.totalWeeks,
            occurrenceDate: occurrenceDate
        )

        if result.updatedCourse.schedules.isEmpty {
            deleteCourse(id: courseId)
        } else {
            updateCourse(result.updatedCourse)
        }
        exceptions.append(contentsOf: result.newExceptions)
        save()
    }

    public func restoreCancelledOccurrence(
        courseId: UUID,
        scheduleId: UUID,
        occurrenceDate: String
    ) {
        exceptions.removeAll { ex in
            ex.scheduleId == scheduleId && ex.originalDate == occurrenceDate && ex.exceptionType == .cancel
        }
        save()
    }

    // MARK: - Calendar Synchronization (EventKit)

    public func allSemesterOccurrences(for semester: Semester) -> [TimelineItem] {
        let semesterCourses = courses.filter { $0.semesterId == semester.id }
        var allItems: [TimelineItem] = []
        let currentWeek = calculatedCurrentWeek
        for week in 1...semester.totalWeeks {
            let weekItems = timelineEngine.generateOccurrences(
                semester: semester,
                courses: semesterCourses,
                exceptions: exceptions,
                forWeek: week,
                activeWeek: currentWeek,
                showNonCurrentWeekCourses: false
            )
            allItems.append(contentsOf: weekItems)
        }
        return allItems
    }

    public func syncToSystemCalendar() async throws -> CalendarSyncExecutionResult {
        guard let current = currentSemester else {
            throw NSError(domain: "ClassFlow", code: 400, userInfo: [NSLocalizedDescriptionKey: "未选择当前学期"])
        }

        let allOccurrences = allSemesterOccurrences(for: current)
        let options = CalendarSyncOptions(
            calendarName: settings.calendarName,
            reminderMinutes: settings.calendarReminderMinutes,
            includeTeacherAndNotes: settings.calendarIncludeNotes
        )

        let plan = calendarPlanner.generatePlan(
            occurrences: allOccurrences,
            semester: current,
            existingMappings: calendarMappings,
            options: options
        )

        let result = try await calendarService.executePlan(plan, inCalendarNamed: settings.calendarName)

        // Prune deleted mappings
        let deletedKeys = Set(plan.toDelete.map { $0.logicalKey })
        calendarMappings.removeAll(where: { deletedKeys.contains($0.logicalKey) })

        // Apply new/updated mappings
        let updatedDict = Dictionary(uniqueKeysWithValues: result.newMappings.map { ($0.logicalKey, $0) })
        for i in 0..<calendarMappings.count {
            if let updated = updatedDict[calendarMappings[i].logicalKey] {
                calendarMappings[i] = updated
            }
        }
        for newMapping in result.newMappings {
            if !calendarMappings.contains(where: { $0.logicalKey == newMapping.logicalKey }) {
                calendarMappings.append(newMapping)
            }
        }

        settings.lastCalendarSyncTime = ISO8601DateFormatter().string(from: Date())
        save()
        return result
    }

    public func clearSystemCalendar() async throws -> Int {
        let count = try calendarService.clearCalendar(named: settings.calendarName)
        calendarMappings.removeAll()
        settings.lastCalendarSyncTime = nil
        save()
        return count
    }

    // MARK: - Notifications

    public func scheduleNotifications() async {
        guard let current = currentSemester else { return }
        let currentWeek = calculatedCurrentWeek
        let weekOccurrences = timelineEngine.generateOccurrences(
            semester: current,
            courses: courses.filter { $0.semesterId == current.id },
            exceptions: exceptions,
            forWeek: currentWeek,
            activeWeek: currentWeek
        )

        if settings.enableClassAlerts {
            await notificationService.schedulePreClassAlerts(
                occurrences: weekOccurrences,
                offsetMinutes: settings.classAlertMinutes
            )
        } else {
            await notificationService.cancelClassAlerts()
        }

        await notificationService.scheduleCalendarAlerts(events: calendarEvents, semesters: semesters, exceptions: exceptions)
        let todayStr = timelineEngine.weekCalculator.formatDate(Date())
        let todayItems = weekOccurrences.filter { $0.date == todayStr }
        if settings.enableDailyBriefing {
            await notificationService.scheduleDailyBriefing(
                todayOccurrences: todayItems,
                hour: settings.dailyBriefingHour,
                minute: settings.dailyBriefingMinute
            )
        }
    }

    // MARK: - Desktop Widgets

    public func updateWidgetData() {
        guard let current = currentSemester else { return }
        let now = Date()
        let todayStr = timelineEngine.weekCalculator.formatDate(now)
        let weekNum = calculatedCurrentWeek

        var weekOccurrences = timelineEngine.generateOccurrences(
            semester: current,
            courses: courses.filter { $0.semesterId == current.id },
            exceptions: exceptions,
            forWeek: weekNum,
            activeWeek: weekNum
        )

        let weekDates = timelineEngine.weekCalculator.dateStringsInWeek(
            weekNumber: weekNum,
            semesterStartDate: current.startDate
        )
        let defaultSemId = semesters.first?.id
        let currentEvents = calendarEvents.filter {
            $0.semesterId == current.id || ($0.semesterId == nil && current.id == defaultSemId)
        }

        for event in currentEvents {
            let eventItems = projectCalendarEvent(event, weekDates: weekDates, semester: current)
            weekOccurrences.append(contentsOf: eventItems)
        }

        let dfTime = DateFormatter()
        dfTime.dateFormat = "HH:mm"
        dfTime.timeZone = TimeZone.current
        let currentTimeStr = dfTime.string(from: now)

        let todayItems = weekOccurrences.filter { $0.date == todayStr && $0.status != .cancelled }.sorted {
            ($0.startTime ?? "00:00") < ($1.startTime ?? "00:00")
        }

        let todayWidgetItems: [WidgetCourseItem] = todayItems.map { item in
            let start = item.startTime ?? "00:00"
            let end = item.endTime ?? "23:59"
            let isOngoing = currentTimeStr >= start && currentTimeStr <= end

            var minutesUntil: Int? = nil
            let dfFull = DateFormatter()
            dfFull.dateFormat = "yyyy-MM-dd HH:mm"
            if let startDate = dfFull.date(from: "\(todayStr) \(start)") {
                let diff = startDate.timeIntervalSince(now) / 60.0
                if diff > 0 {
                    minutesUntil = Int(diff)
                }
            }

            return WidgetCourseItem(
                id: item.id,
                title: item.title,
                location: item.location,
                teacher: item.teacher,
                startTime: start,
                endTime: end,
                startSection: item.startSection,
                endSection: item.endSection,
                colorHex: item.color,
                isOngoing: isOngoing,
                minutesUntilStart: minutesUntil
            )
        }

        let nextItem = todayWidgetItems.first(where: { $0.isOngoing || ($0.minutesUntilStart != nil && $0.minutesUntilStart! >= 0) })

        let dayNames = ["周一", "周二", "周三", "周四", "周五", "周六", "周日"]
        var weekSchedules: [WidgetDaySchedule] = []
        for dayIdx in 1...7 {
            let dateStr = (dayIdx <= weekDates.count) ? weekDates[dayIdx - 1] : ""
            let dayCourses = weekOccurrences.filter { $0.dayOfWeek == dayIdx && $0.status != .cancelled }.map { item in
                WidgetCourseItem(
                    id: item.id,
                    title: item.title,
                    location: item.location,
                    teacher: item.teacher,
                    startTime: item.startTime ?? "",
                    endTime: item.endTime ?? "",
                    startSection: item.startSection,
                    endSection: item.endSection,
                    colorHex: item.color
                )
            }
            weekSchedules.append(
                WidgetDaySchedule(
                    id: dayIdx,
                    dayName: dayNames[dayIdx - 1],
                    dateString: dateStr,
                    courses: dayCourses
                )
            )
        }

        let payload = WidgetTimelinePayload(
            semesterName: current.name,
            currentWeek: weekNum,
            todayDateString: todayStr,
            nextClass: nextItem,
            todayClasses: todayWidgetItems,
            weekSchedule: weekSchedules
        )

        try? widgetDataStore.save(payload)
        WidgetCenter.shared.reloadAllTimelines()
    }

    // MARK: - Deep Link Routing

    public func handleDeepLink(_ url: URL) {
        guard url.scheme == "classflow" else { return }
        let host = url.host ?? ""
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        var queryParams: [String: String] = [:]
        for item in components?.queryItems ?? [] {
            queryParams[item.name] = item.value
        }
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let targetId = queryParams["id"] ?? (!path.isEmpty ? path : "")

        if host == "occurrence", !targetId.isEmpty {
            // 1. Search in current week's occurrences
            if let occ = currentWeekOccurrences.first(where: { $0.id == targetId }) {
                selectedOccurrence = occ
                return
            }
            // 2. Search across entire semester
            if let semester = currentSemester {
                let currentCourses = courses.filter { $0.semesterId == semester.id }
                let defaultSemId = semesters.first?.id
                let currentEvents = calendarEvents.filter {
                    $0.semesterId == semester.id || ($0.semesterId == nil && semester.id == defaultSemId)
                }
                for w in 1...semester.totalWeeks {
                    var occs = timelineEngine.generateOccurrences(
                        semester: semester,
                        courses: currentCourses,
                        exceptions: exceptions,
                        forWeek: w,
                        activeWeek: calculatedCurrentWeek,
                        showNonCurrentWeekCourses: true
                    )
                    let wDates = timelineEngine.weekCalculator.dateStringsInWeek(weekNumber: w, semesterStartDate: semester.startDate)
                    for event in currentEvents {
                        occs.append(contentsOf: projectCalendarEvent(event, weekDates: wDates, semester: semester))
                    }
                    if let occ = occs.first(where: { $0.id == targetId }) {
                        selectedWeek = w
                        selectedOccurrence = occ
                        return
                    }
                }
            }
        } else if host == "add" {
            startAddingCourse()
        } else if host == "today" || host == "schedule" {
            settings.selectedTab = "schedule"
            jumpToCurrentWeek()
        } else if host == "tab" {
            let tabName = queryParams["name"] ?? queryParams["tab"] ?? "courses"
            switch tabName {
            case "study":
                settings.enableStudyModule = true
                settings.selectedTab = "study"
            case "schedule":
                settings.selectedTab = "schedule"
            case "timetable", "courses":
                settings.selectedTab = "courses"
            case "settings":
                isShowingSettings = true
            default:
                settings.selectedTab = tabName
            }
        } else if host == "week" {
            if let weekStr = queryParams["number"] ?? queryParams["week"], let w = Int(weekStr) {
                selectedWeek = max(1, min(w, currentSemester?.totalWeeks ?? 30))
            }
        } else if host == "focus" {
            let action = queryParams["action"] ?? "start"
            if action == "start" {
                settings.enableStudyModule = true
                settings.selectedTab = "study"
                let minutes = Int(queryParams["minutes"] ?? "25") ?? 25
                NotificationCenter.default.post(
                    name: NSNotification.Name("org.classflow.startFocusTimer"),
                    object: nil,
                    userInfo: ["minutes": minutes]
                )
            } else if action == "stop" {
                NotificationCenter.default.post(
                    name: NSNotification.Name("org.classflow.stopFocusTimer"),
                    object: nil
                )
            }
        } else if host == "sync" {
            if let provider = createConfiguredStorageProvider() {
                Task {
                    _ = try? await self.executeCloudSync(storage: provider)
                }
            }
        } else if host == "reload" {
            load()
        }

        // Generic query parameter fallbacks (e.g. ?tab=study or ?week=3)
        if let tab = queryParams["tab"], host != "tab" {
            if tab == "study" {
                settings.enableStudyModule = true
                settings.selectedTab = "study"
            } else if tab == "schedule" {
                settings.selectedTab = "schedule"
            } else if tab == "courses" || tab == "timetable" {
                settings.selectedTab = "courses"
            }
        }
        if let weekStr = queryParams["week"], host != "week", let w = Int(weekStr) {
            selectedWeek = max(1, min(w, currentSemester?.totalWeeks ?? 30))
        }
    }

    // MARK: - Import & Export Operations

    public func exportClassFlowBackup() throws -> Data {
        try backupService.exportBackup(
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: [
                "showWeekends": String(settings.showWeekends),
                "themeMode": settings.themeMode
            ]
        )
    }

    public func exportCurrentSemesterICS() -> String? {
        guard let current = currentSemester else { return nil }
        let currentCourses = courses.filter { $0.semesterId == current.id }
        return icsExporter.exportToICS(
            semester: current,
            courses: currentCourses,
            exceptions: exceptions
        )
    }

    public func stageClassFlowImport(from data: Data) {
        do {
            let package = try backupService.importBackup(from: data)
            let preview = backupService.generateImportPreview(package: package, existingCourses: courses)
            self.stagedImportPackage = package
            self.stagedImportPreview = preview
            self.isShowingImportPreview = true
        } catch {
            self.importErrorMessage = error.localizedDescription
        }
    }

    public func commitClassFlowImport(mode: ImportPreviewSheet.ImportMode) {
        guard let package = stagedImportPackage else { return }

        switch mode {
        case .merge:
            for s in package.semesters {
                if !semesters.contains(where: { $0.id == s.id }) {
                    semesters.append(s)
                }
            }
            for c in package.courses {
                if !courses.contains(where: { $0.id == c.id }) {
                    courses.append(c)
                }
            }
            exceptions.append(contentsOf: package.exceptions)

        case .replace:
            self.semesters = package.semesters
            self.courses = package.courses
            self.exceptions = package.exceptions
            if let firstSem = semesters.first {
                self.currentSemesterId = firstSem.id
                self.selectedWeek = calculateCurrentWeek(for: firstSem)
            }
        }

        save()
        self.stagedImportPackage = nil
        self.stagedImportPreview = nil
        self.isShowingImportPreview = false
    }

    public func importICS(content: String) {
        guard let current = currentSemester else { return }
        let events = icsImporter.parseICS(content: content)
        let candidateCourses = icsImporter.convertToCourses(events: events, semester: current)
        for c in candidateCourses {
            addCourse(c)
        }
    }

    public func importOpenFormatJSON(content: String) {
        guard let current = currentSemester else {
            self.importErrorMessage = "请先创建或选择一个学期后再导入课程"
            return
        }
        let importer = OpenFormatCourseImporter()
        do {
            let result = try importer.parse(jsonString: content, semesterId: current.id)
            for c in result.courses {
                addCourse(c)
            }
            if let sections = result.parsedSections, !sections.isEmpty {
                var updated = current
                updated.sections = sections
                updateSemester(updated)
            }
        } catch {
            self.importErrorMessage = "解析失败: \(error.localizedDescription)"
        }
    }
}
