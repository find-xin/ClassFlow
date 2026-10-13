import Foundation

/// Non-blocking warning indicating two or more active occurrences overlap in time or section.
public struct ConflictWarning: Codable, Sendable, Identifiable, Equatable, Hashable {
    public var id: String { "\(itemAId)_\(itemBId)_\(date)" }

    public var itemAId: String
    public var itemBId: String
    public var itemATitle: String
    public var itemBTitle: String
    public var date: String
    public var overlappingSections: [Int]
    public var message: String

    public init(
        itemAId: String,
        itemBId: String,
        itemATitle: String,
        itemBTitle: String,
        date: String,
        overlappingSections: [Int],
        message: String? = nil
    ) {
        self.itemAId = itemAId
        self.itemBId = itemBId
        self.itemATitle = itemATitle
        self.itemBTitle = itemBTitle
        self.date = date
        self.overlappingSections = overlappingSections
        if let message {
            self.message = message
        } else {
            let sectionStr = overlappingSections.map(String.init).joined(separator: ", ")
            self.message = "课程「\(itemATitle)」与「\(itemBTitle)」在 \(date) 第 \(sectionStr) 节存在时间冲突"
        }
    }
}
