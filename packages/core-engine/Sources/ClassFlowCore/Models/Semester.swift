import Foundation

/// Represents an academic semester term governing teaching weeks and daily section times.
public struct Semester: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: UUID
    public var name: String
    /// ISO 8601 date string ("YYYY-MM-DD") representing the Monday of the first teaching week.
    public var startDate: String
    /// ISO 8601 date string ("YYYY-MM-DD") representing the end date of the semester term.
    public var endDate: String
    /// Total teaching weeks in this semester (typically 16-24 weeks).
    public var totalWeeks: Int
    /// Whether this is the currently active semester in the user's timetable view.
    public var isCurrent: Bool
    /// IANA TimeZone identifier (e.g. "Asia/Shanghai").
    public var timeZone: String
    /// Daily section time definitions.
    public var sections: [SectionTime]

    public init(
        id: UUID = UUID(),
        name: String,
        startDate: String,
        endDate: String,
        totalWeeks: Int = 20,
        isCurrent: Bool = false,
        timeZone: String = "Asia/Shanghai",
        sections: [SectionTime] = []
    ) {
        self.id = id
        self.name = name
        self.startDate = startDate
        self.endDate = endDate
        self.totalWeeks = totalWeeks
        self.isCurrent = isCurrent
        self.timeZone = timeZone
        self.sections = sections
    }

    /// Helper to lookup start and end times for a specific period number.
    public func sectionTime(for period: Int) -> SectionTime? {
        sections.first(where: { $0.section == period })
    }

    /// Safely parses a UUID string or derives a deterministic UUID for non-standard IDs (e.g. Android "sem_main").
    public static func parseOrDeriveUUID(_ str: String) -> UUID {
        if let direct = UUID(uuidString: str) {
            return direct
        }
        var hash = [UInt8](repeating: 0, count: 16)
        let utf8 = Array(str.utf8)
        for (idx, byte) in utf8.enumerated() {
            hash[idx % 16] ^= byte
            hash[(idx * 7 + 3) % 16] = hash[(idx * 7 + 3) % 16] &+ byte
        }
        hash[6] = (hash[6] & 0x0F) | 0x40
        hash[8] = (hash[8] & 0x3F) | 0x80
        return UUID(uuid: (
            hash[0], hash[1], hash[2], hash[3],
            hash[4], hash[5], hash[6], hash[7],
            hash[8], hash[9], hash[10], hash[11],
            hash[12], hash[13], hash[14], hash[15]
        ))
    }

    enum CodingKeys: String, CodingKey {
        case id, name, startDate, endDate, totalWeeks, isCurrent, timeZone, sections
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let directId = try? container.decode(UUID.self, forKey: .id) {
            self.id = directId
        } else if let strId = try? container.decode(String.self, forKey: .id) {
            self.id = Self.parseOrDeriveUUID(strId)
        } else {
            self.id = UUID()
        }

        self.name = (try? container.decode(String.self, forKey: .name)) ?? "未命名学期"
        self.startDate = (try? container.decodeIfPresent(String.self, forKey: .startDate)) ?? "2026-09-01"
        self.endDate = (try? container.decodeIfPresent(String.self, forKey: .endDate)) ?? "2027-01-20"
        self.totalWeeks = (try? container.decodeIfPresent(Int.self, forKey: .totalWeeks)) ?? 20
        self.isCurrent = (try? container.decodeIfPresent(Bool.self, forKey: .isCurrent)) ?? false
        self.timeZone = (try? container.decodeIfPresent(String.self, forKey: .timeZone)) ?? "Asia/Shanghai"
        self.sections = (try? container.decodeIfPresent([SectionTime].self, forKey: .sections)) ?? []
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(startDate, forKey: .startDate)
        try container.encode(endDate, forKey: .endDate)
        try container.encode(totalWeeks, forKey: .totalWeeks)
        try container.encode(isCurrent, forKey: .isCurrent)
        try container.encode(timeZone, forKey: .timeZone)
        try container.encode(sections, forKey: .sections)
    }
}
