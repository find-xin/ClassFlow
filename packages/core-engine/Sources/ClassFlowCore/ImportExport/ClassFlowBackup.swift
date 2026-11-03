import Foundation
import CryptoKit

/// Represents metadata stored in manifest.json within the .classflow ZIP container.
public struct BackupManifest: Codable, Sendable, Equatable {
    public var formatVersion: Int
    public var appVersion: String
    public var exportedAt: String
    public var checksum: String
    public var semestersCount: Int
    public var coursesCount: Int
    public var exceptionsCount: Int
    public var calendarEventsCount: Int?

    public init(
        formatVersion: Int = 1,
        appVersion: String = "1.0.0",
        exportedAt: String,
        checksum: String,
        semestersCount: Int,
        coursesCount: Int,
        exceptionsCount: Int,
        calendarEventsCount: Int? = nil
    ) {
        self.formatVersion = formatVersion
        self.appVersion = appVersion
        self.exportedAt = exportedAt
        self.checksum = checksum
        self.semestersCount = semestersCount
        self.coursesCount = coursesCount
        self.exceptionsCount = exceptionsCount
        self.calendarEventsCount = calendarEventsCount
    }
}

/// In-memory representation of an unpacked .classflow archive.
public struct ClassFlowPackage: Codable, Sendable, Equatable {
    public var manifest: BackupManifest
    public var semesters: [Semester]
    public var courses: [Course]
    public var exceptions: [ScheduleException]
    public var settings: [String: String]
    public var calendarEvents: [CalendarEvent]

    public init(
        manifest: BackupManifest,
        semesters: [Semester],
        courses: [Course],
        exceptions: [ScheduleException],
        settings: [String: String] = [:],
        calendarEvents: [CalendarEvent] = []
    ) {
        self.manifest = manifest
        self.semesters = semesters
        self.courses = courses
        self.exceptions = exceptions
        self.settings = settings
        self.calendarEvents = calendarEvents
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        manifest = try container.decode(BackupManifest.self, forKey: .manifest)
        semesters = try container.decode([Semester].self, forKey: .semesters)
        courses = try container.decode([Course].self, forKey: .courses)
        exceptions = try container.decode([ScheduleException].self, forKey: .exceptions)
        settings = try container.decodeIfPresent([String: String].self, forKey: .settings) ?? [:]
        calendarEvents = try container.decodeIfPresent([CalendarEvent].self, forKey: .calendarEvents) ?? []
    }

}

/// Summary report presented to the user prior to committing imported data.
public struct ImportPreview: Sendable, Equatable {
    public var semestersCount: Int
    public var semesterNames: [String]
    public var totalCoursesCount: Int
    public var newCoursesCount: Int
    public var conflictingCoursesCount: Int
    public var exceptionsCount: Int
    public var calendarEventsCount: Int

    public init(
        semestersCount: Int,
        semesterNames: [String],
        totalCoursesCount: Int,
        newCoursesCount: Int,
        conflictingCoursesCount: Int,
        exceptionsCount: Int,
        calendarEventsCount: Int = 0
    ) {
        self.semestersCount = semestersCount
        self.semesterNames = semesterNames
        self.totalCoursesCount = totalCoursesCount
        self.newCoursesCount = newCoursesCount
        self.conflictingCoursesCount = conflictingCoursesCount
        self.exceptionsCount = exceptionsCount
        self.calendarEventsCount = calendarEventsCount
    }
}

/// Core service for packing, unpacking, validating, and merging .classflow backup archives.
public struct ClassFlowBackupService: Sendable {
    public let zipArchive: ZipArchive

    public enum BackupError: Error, LocalizedError {
        case missingManifest
        case incompatibleFormatVersion(Int)
        case checksumMismatch(expected: String, actual: String)
        case missingComponent(String)
        case corruptedJSON(String)

        public var errorDescription: String? {
            switch self {
            case .missingManifest: return "Missing manifest.json in .classflow backup archive."
            case .incompatibleFormatVersion(let v): return "Unsupported backup format version: \(v)."
            case .checksumMismatch(let exp, let act): return "Integrity checksum mismatch (expected \(exp), got \(act))."
            case .missingComponent(let file): return "Missing required archive component: \(file)."
            case .corruptedJSON(let err): return "Failed to decode backup JSON: \(err)."
            }
        }
    }

    public init(zipArchive: ZipArchive = ZipArchive()) {
        self.zipArchive = zipArchive
    }

    // MARK: - Export

    /// Serializes timetable domain entities into a deterministic, versioned .classflow ZIP archive.
    public func exportBackup(
        semesters: [Semester],
        courses: [Course],
        exceptions: [ScheduleException],
        settings: [String: String] = [:],
        calendarEvents: [CalendarEvent] = []
    ) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        let semestersData = try encoder.encode(semesters)
        let coursesData = try encoder.encode(courses)
        let exceptionsData = try encoder.encode(exceptions)
        let settingsData = try encoder.encode(settings)
        let calendarEventsData = try encoder.encode(calendarEvents)

        // Compute SHA-256 over concatenated payload bytes
        var hasher = SHA256()
        hasher.update(data: semestersData)
        hasher.update(data: coursesData)
        hasher.update(data: exceptionsData)
        hasher.update(data: calendarEventsData)
        let digest = hasher.finalize()
        let checksumHex = digest.map { String(format: "%02x", $0) }.joined()

        let df = ISO8601DateFormatter()
        let nowStr = df.string(from: Date())

        let manifest = BackupManifest(
            formatVersion: 2,
            appVersion: "1.0.0",
            exportedAt: nowStr,
            checksum: checksumHex,
            semestersCount: semesters.count,
            coursesCount: courses.count,
            exceptionsCount: exceptions.count,
            calendarEventsCount: calendarEvents.count
        )
        let manifestData = try encoder.encode(manifest)

        let files: [String: Data] = [
            "manifest.json": manifestData,
            "semesters.json": semestersData,
            "courses.json": coursesData,
            "exceptions.json": exceptionsData,
            "settings.json": settingsData,
            "calendarEvents.json": calendarEventsData
        ]

        return zipArchive.createArchive(files: files)
    }

    // MARK: - Import & Validation

    /// Unpacks, verifies integrity, and decodes domain entities from a .classflow ZIP data buffer.
    public func importBackup(from data: Data) throws -> ClassFlowPackage {
        let files = try zipArchive.extractArchive(data: data)

        guard let manifestData = files["manifest.json"] else {
            throw BackupError.missingManifest
        }

        let decoder = JSONDecoder()
        let manifest: BackupManifest
        do {
            manifest = try decoder.decode(BackupManifest.self, from: manifestData)
        } catch {
            throw BackupError.corruptedJSON("manifest.json: \(error.localizedDescription)")
        }

        guard [1, 2].contains(manifest.formatVersion) else {
            throw BackupError.incompatibleFormatVersion(manifest.formatVersion)
        }

        guard let semestersData = files["semesters.json"] else { throw BackupError.missingComponent("semesters.json") }
        guard let coursesData = files["courses.json"] else { throw BackupError.missingComponent("courses.json") }
        guard let exceptionsData = files["exceptions.json"] else { throw BackupError.missingComponent("exceptions.json") }
        let settingsData = files["settings.json"] ?? Data("{}".utf8)
        if manifest.formatVersion == 2 && files["calendarEvents.json"] == nil { throw BackupError.missingComponent("calendarEvents.json") }
        let calendarEventsData = files["calendarEvents.json"] ?? Data("[]".utf8)

        // Verify SHA-256 checksum
        var hasher = SHA256()
        hasher.update(data: semestersData)
        hasher.update(data: coursesData)
        hasher.update(data: exceptionsData)
        if manifest.formatVersion == 2 { hasher.update(data: calendarEventsData) }
        let actualChecksum = hasher.finalize().map { String(format: "%02x", $0) }.joined()

        guard actualChecksum.lowercased() == manifest.checksum.lowercased() else {
            throw BackupError.checksumMismatch(expected: manifest.checksum, actual: actualChecksum)
        }

        let semesters = try decoder.decode([Semester].self, from: semestersData)
        let courses = try decoder.decode([Course].self, from: coursesData)
        let exceptions = try decoder.decode([ScheduleException].self, from: exceptionsData)
        let settings = (try? decoder.decode([String: String].self, from: settingsData)) ?? [:]
        let calendarEvents = try decoder.decode([CalendarEvent].self, from: calendarEventsData)
        if manifest.formatVersion == 2 && manifest.calendarEventsCount != calendarEvents.count {
            throw BackupError.corruptedJSON("calendarEventsCount does not match calendarEvents.json")
        }

        return ClassFlowPackage(
            manifest: manifest,
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: settings,
            calendarEvents: calendarEvents
        )
    }

    // MARK: - Preview Calculation

    /// Analyzes an imported package against existing local state to present a summary report.
    public func generateImportPreview(
        package: ClassFlowPackage,
        existingCourses: [Course]
    ) -> ImportPreview {
        let existingNames = Set(existingCourses.map { $0.name.lowercased() })
        var newCount = 0
        var conflictCount = 0

        for c in package.courses {
            if existingNames.contains(c.name.lowercased()) {
                conflictCount += 1
            } else {
                newCount += 1
            }
        }

        return ImportPreview(
            semestersCount: package.semesters.count,
            semesterNames: package.semesters.map { $0.name },
            totalCoursesCount: package.courses.count,
            newCoursesCount: newCount,
            conflictingCoursesCount: conflictCount,
            exceptionsCount: package.exceptions.count,
            calendarEventsCount: package.calendarEvents.count
        )
    }
}
