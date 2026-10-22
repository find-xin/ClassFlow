import Foundation
import ClassFlowCore

/// Manages local persistent JSON storage in Application Support.
public struct LocalDatabase: Sendable {
    public struct DatabasePayload: Codable, Sendable {
        public var semesters: [Semester]
        public var courses: [Course]
        public var exceptions: [ScheduleException]
        public var settings: AppSettings
        public var calendarMappings: [CalendarMapping]
        public var timetableProfiles: [TimetableProfile]
        public var activeProfileId: UUID?
        public var pomodoroTasks: [PomodoroTask]
        public var pomodoroRecords: [PomodoroRecord]
        public var calendarEvents: [CalendarEvent]
        public var tombstones: [SyncTombstone]
        public var syncDeviceId: String?
        public var syncSequence: Int?
        public var focusSession: FocusSession?
        public var calendarImportReceipts: [String: CalendarImportReceipt]?

        public init(
            semesters: [Semester] = [],
            courses: [Course] = [],
            exceptions: [ScheduleException] = [],
            settings: AppSettings = AppSettings(),
            calendarMappings: [CalendarMapping] = [],
            timetableProfiles: [TimetableProfile] = [TimetableProfile.standardDefault(), TimetableProfile.earlyDefault()],
            activeProfileId: UUID? = nil,
            pomodoroTasks: [PomodoroTask] = [],
            pomodoroRecords: [PomodoroRecord] = [],
            calendarEvents: [CalendarEvent] = [],
            tombstones: [SyncTombstone] = [],
            syncDeviceId: String? = nil,
            syncSequence: Int? = nil,
            focusSession: FocusSession? = nil,
            calendarImportReceipts: [String: CalendarImportReceipt]? = nil
        ) {
            self.semesters = semesters
            self.courses = courses
            self.exceptions = exceptions
            self.settings = settings
            self.calendarMappings = calendarMappings
            self.timetableProfiles = timetableProfiles
            self.activeProfileId = activeProfileId
            self.pomodoroTasks = pomodoroTasks
            self.pomodoroRecords = pomodoroRecords
            self.calendarEvents = calendarEvents
            self.tombstones = tombstones
            self.syncDeviceId = syncDeviceId
            self.syncSequence = syncSequence
            self.focusSession = focusSession
            self.calendarImportReceipts = calendarImportReceipts
        }

        public init(from decoder: Decoder) throws {
            let container = try decoder.container(keyedBy: CodingKeys.self)
            self.semesters = try container.decodeIfPresent([Semester].self, forKey: .semesters) ?? []
            self.courses = try container.decodeIfPresent([Course].self, forKey: .courses) ?? []
            self.exceptions = try container.decodeIfPresent([ScheduleException].self, forKey: .exceptions) ?? []
            self.settings = try container.decodeIfPresent(AppSettings.self, forKey: .settings) ?? AppSettings()
            self.calendarMappings = try container.decodeIfPresent([CalendarMapping].self, forKey: .calendarMappings) ?? []
            self.timetableProfiles = try container.decodeIfPresent([TimetableProfile].self, forKey: .timetableProfiles) ?? [TimetableProfile.standardDefault(), TimetableProfile.earlyDefault()]
            self.activeProfileId = try container.decodeIfPresent(UUID.self, forKey: .activeProfileId)
            self.pomodoroTasks = try container.decodeIfPresent([PomodoroTask].self, forKey: .pomodoroTasks) ?? []
            self.pomodoroRecords = try container.decodeIfPresent([PomodoroRecord].self, forKey: .pomodoroRecords) ?? []
            self.calendarEvents = try container.decodeIfPresent([CalendarEvent].self, forKey: .calendarEvents) ?? []
            self.tombstones = try container.decodeIfPresent([SyncTombstone].self, forKey: .tombstones) ?? []
            self.syncDeviceId = try container.decodeIfPresent(String.self, forKey: .syncDeviceId)
            self.syncSequence = try container.decodeIfPresent(Int.self, forKey: .syncSequence)
            self.focusSession = try container.decodeIfPresent(FocusSession.self, forKey: .focusSession)
            self.calendarImportReceipts = try container.decodeIfPresent([String: CalendarImportReceipt].self, forKey: .calendarImportReceipts)
        }
    }

    /// Private transaction workspace: shared only by handlers in one synchronous automation call.
    /// The lock preserves Sendable guarantees; durable state is committed explicitly by the caller.
    private final class Workspace: @unchecked Sendable {
        let lock = NSLock()
        var payload: DatabasePayload
        init(_ payload: DatabasePayload) { self.payload = payload }
        func load() -> DatabasePayload { lock.withLock { payload } }
        func save(_ value: DatabasePayload) { lock.withLock { payload = value } }
    }
    private let workspace: Workspace?
    public let fileURL: URL

    init(automationPayload: DatabasePayload) {
        workspace = Workspace(automationPayload)
        fileURL = URL(fileURLWithPath: "/__classflow_memory_transaction__/" + UUID().uuidString)
    }

    /// Decode once and fail closed; automation must never replace corrupted data with defaults.
    func loadForAutomation() throws -> DatabasePayload {
        if let workspace { return workspace.load() }
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return makeBootstrapData() }
        return try JSONDecoder().decode(DatabasePayload.self, from: Data(contentsOf: fileURL))
    }


    public init(customURL: URL? = nil) {
        self.workspace = nil
        if let customURL {
            self.fileURL = customURL
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
            let classFlowDir = appSupport.appendingPathComponent("ClassFlow", isDirectory: true)
            self.fileURL = classFlowDir.appendingPathComponent("database.json")
        }
    }

    /// Automation must fail closed on corruption rather than overwrite data with sample courses.
    public func validateForAutomation() throws {
        if workspace != nil { return }
        if FileManager.default.fileExists(atPath: fileURL.path) {
            _ = try JSONDecoder().decode(DatabasePayload.self, from: Data(contentsOf: fileURL))
        }
    }

    /// Loads the database payload, or generates sample bootstrap data if missing.
    public func load() -> DatabasePayload {
        if let workspace { return workspace.load() }
        guard FileManager.default.fileExists(atPath: fileURL.path) else {
            return makeBootstrapData()
        }

        do {
            let data = try Data(contentsOf: fileURL)
            let payload = try JSONDecoder().decode(DatabasePayload.self, from: data)
            return payload
        } catch {
            fputs("[LocalDatabase] Failed to decode existing database.\n", stderr)
            return makeBootstrapData()
        }
    }

    /// Persists payload to disk atomically.
    public func save(_ payload: DatabasePayload) throws {
        if let workspace { workspace.save(payload); return }
        let folder = fileURL.deletingLastPathComponent()
        if !FileManager.default.fileExists(atPath: folder.path) {
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        }
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(payload)
        try data.write(to: fileURL, options: .atomic)
    }

    /// Generates clean initial semester data with standard timetable grid on first launch.
    public func makeBootstrapData() -> DatabasePayload {
        let semesterId = UUID(uuidString: "e6740bfa-8935-4cb2-8e14-d035f2990a41")!
        let semester = Semester(
            id: semesterId,
            name: "2026-2027 秋季学期",
            startDate: "2026-09-07",
            endDate: "2027-01-24",
            totalWeeks: 20,
            isCurrent: true,
            sections: [
                SectionTime(section: 1, startTime: "08:00", endTime: "08:45"),
                SectionTime(section: 2, startTime: "08:50", endTime: "09:35"),
                SectionTime(section: 3, startTime: "10:05", endTime: "10:50"),
                SectionTime(section: 4, startTime: "10:55", endTime: "11:40"),
                SectionTime(section: 5, startTime: "14:00", endTime: "14:45"),
                SectionTime(section: 6, startTime: "14:50", endTime: "15:35"),
                SectionTime(section: 7, startTime: "15:55", endTime: "16:40"),
                SectionTime(section: 8, startTime: "16:45", endTime: "17:30"),
                SectionTime(section: 9, startTime: "18:30", endTime: "19:15"),
                SectionTime(section: 10, startTime: "19:20", endTime: "20:05"),
                SectionTime(section: 11, startTime: "20:10", endTime: "20:55"),
                SectionTime(section: 12, startTime: "21:00", endTime: "21:45")
            ]
        )

        return DatabasePayload(
            semesters: [semester],
            courses: [],
            exceptions: [],
            settings: AppSettings(),
            calendarMappings: [],
            timetableProfiles: [TimetableProfile.standardDefault(), TimetableProfile.earlyDefault()],
            activeProfileId: nil,
            pomodoroTasks: [],
            pomodoroRecords: [],
            calendarEvents: [],
            tombstones: [],
            syncDeviceId: nil,
            syncSequence: nil,
            focusSession: nil
        )
    }
}
