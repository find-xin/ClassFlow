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

    public init(
        formatVersion: Int = 1,
        appVersion: String = "1.0.0",
        exportedAt: String,
        checksum: String,
        semestersCount: Int,
        coursesCount: Int,
        exceptionsCount: Int
    ) {
        self.formatVersion = formatVersion
        self.appVersion = appVersion
        self.exportedAt = exportedAt
        self.checksum = checksum
        self.semestersCount = semestersCount
        self.coursesCount = coursesCount
        self.exceptionsCount = exceptionsCount
    }
}

/// In-memory representation of an unpacked .classflow archive.
public struct ClassFlowPackage: Codable, Sendable, Equatable {
    public var manifest: BackupManifest
    public var semesters: [Semester]
    public var courses: [Course]
    public var exceptions: [ScheduleException]
    public var settings: [String: String]

    public init(
        manifest: BackupManifest,
        semesters: [Semester],
        courses: [Course],
        exceptions: [ScheduleException],
        settings: [String: String] = [:]
    ) {
        self.manifest = manifest
        self.semesters = semesters
        self.courses = courses
        self.exceptions = exceptions
        self.settings = settings
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

    public init(
        semestersCount: Int,
        semesterNames: [String],
        totalCoursesCount: Int,
        newCoursesCount: Int,
        conflictingCoursesCount: Int,
        exceptionsCount: Int
    ) {
        self.semestersCount = semestersCount
        self.semesterNames = semesterNames
        self.totalCoursesCount = totalCoursesCount
        self.newCoursesCount = newCoursesCount
        self.conflictingCoursesCount = conflictingCoursesCount
        self.exceptionsCount = exceptionsCount
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
        settings: [String: String] = [:]
    ) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]

        let semestersData = try encoder.encode(semesters)
        let coursesData = try encoder.encode(courses)
        let exceptionsData = try encoder.encode(exceptions)
        let settingsData = try encoder.encode(settings)

        // Compute SHA-256 over concatenated payload bytes
        var hasher = SHA256()
        hasher.update(data: semestersData)
        hasher.update(data: coursesData)
        hasher.update(data: exceptionsData)
        let digest = hasher.finalize()
        let checksumHex = digest.map { String(format: "%02x", $0) }.joined()

        let df = ISO8601DateFormatter()
        let nowStr = df.string(from: Date())

        let manifest = BackupManifest(
            formatVersion: 1,
            appVersion: "1.0.0",
            exportedAt: nowStr,
            checksum: checksumHex,
            semestersCount: semesters.count,
            coursesCount: courses.count,
            exceptionsCount: exceptions.count
        )
        let manifestData = try encoder.encode(manifest)

        let files: [String: Data] = [
            "manifest.json": manifestData,
            "semesters.json": semestersData,
            "courses.json": coursesData,
            "exceptions.json": exceptionsData,
            "settings.json": settingsData
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

        guard manifest.formatVersion == 1 else {
            throw BackupError.incompatibleFormatVersion(manifest.formatVersion)
        }

        guard let semestersData = files["semesters.json"] else { throw BackupError.missingComponent("semesters.json") }
        guard let coursesData = files["courses.json"] else { throw BackupError.missingComponent("courses.json") }
        guard let exceptionsData = files["exceptions.json"] else { throw BackupError.missingComponent("exceptions.json") }
        let settingsData = files["settings.json"] ?? Data("{}".utf8)

        // Verify SHA-256 checksum
        var hasher = SHA256()
        hasher.update(data: semestersData)
        hasher.update(data: coursesData)
        hasher.update(data: exceptionsData)
        let actualChecksum = hasher.finalize().map { String(format: "%02x", $0) }.joined()

        guard actualChecksum.lowercased() == manifest.checksum.lowercased() else {
            throw BackupError.checksumMismatch(expected: manifest.checksum, actual: actualChecksum)
        }

        let semesters = try decoder.decode([Semester].self, from: semestersData)
        let courses = try decoder.decode([Course].self, from: coursesData)
        let exceptions = try decoder.decode([ScheduleException].self, from: exceptionsData)
        let settings = (try? decoder.decode([String: String].self, from: settingsData)) ?? [:]

        return ClassFlowPackage(
            manifest: manifest,
            semesters: semesters,
            courses: courses,
            exceptions: exceptions,
            settings: settings
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
            exceptionsCount: package.exceptions.count
        )
    }
}
