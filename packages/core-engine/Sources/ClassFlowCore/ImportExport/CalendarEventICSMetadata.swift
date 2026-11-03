import Foundation

/// Standard X-properties retain the native series on ClassFlow-to-ClassFlow ICS round trips.
/// Other calendar clients consume only the already-filtered VEVENT projections.
public enum CalendarEventICSMetadata {
    private static let prefix = "X-CLASSFLOW-CALENDAR-EVENT:"
    public static func lines(events: [CalendarEvent]) throws -> [String] {
        let encoder = JSONEncoder(); encoder.outputFormatting = [.sortedKeys]
        return try events.flatMap { event in
            let line = prefix + (try encoder.encode(event)).base64EncodedString()
            // ASCII base64: 72 characters also satisfy RFC 5545's octet limit.
            return stride(from: 0, to: line.count, by: 72).map { offset in
                (offset == 0 ? "" : " ") + String(line.dropFirst(offset).prefix(72))
            }
        }
    }
    public static func events(content: String) throws -> [CalendarEvent] {
        var unfolded: [String] = []
        for raw in content.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n") {
            let line = raw.hasSuffix("\r") ? String(raw.dropLast()) : raw
            if (line.hasPrefix(" ") || line.hasPrefix("\t")), !unfolded.isEmpty { unfolded[unfolded.count - 1] += line.dropFirst() }
            else { unfolded.append(line) }
        }
        var result: [CalendarEvent] = []
        for line in unfolded where line.hasPrefix(prefix) {
            guard let data = Data(base64Encoded: String(line.dropFirst(prefix.count))) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            let event = try JSONDecoder().decode(CalendarEvent.self, from: data)
            if let index = result.firstIndex(where: { $0.id == event.id }) { result[index] = result[index].preservingExclusions(from: event) }
            else { result.append(event) }
        }
        if result.isEmpty && unfolded.contains("X-CLASSFLOW-SOURCE:CALENDAR_EVENT") {
            throw CocoaError(.fileReadCorruptFile)
        }
        return result
    }
}
