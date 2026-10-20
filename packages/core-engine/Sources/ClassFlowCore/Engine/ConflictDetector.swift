import Foundation

/// Pure engine for non-blocking schedule conflict detection.
public struct ConflictDetector: Sendable {
    public init() {}

    /// Detects all pairwise time/section conflicts across a collection of occurrences.
    public func detectConflicts(in items: [TimelineItem]) -> [ConflictWarning] {
        var warnings: [ConflictWarning] = []
        let activeItems = items.filter { $0.status != .cancelled && $0.isCurrentWeek }

        guard activeItems.count >= 2 else { return [] }

        // Group by date first to avoid N^2 pairwise comparisons across different dates
        let itemsByDate = Dictionary(grouping: activeItems, by: { $0.date })

        for (date, dayItems) in itemsByDate {
            guard dayItems.count >= 2 else { continue }
            for i in 0..<dayItems.count {
                for j in (i + 1)..<dayItems.count {
                    let a = dayItems[i]
                    let b = dayItems[j]

                    // Check section overlap
                    let startOverlap = max(a.startSection, b.startSection)
                    let endOverlap = min(a.endSection, b.endSection)

                    if startOverlap <= endOverlap {
                        let overlappingSections = Array(startOverlap...endOverlap)
                        warnings.append(ConflictWarning(
                            itemAId: a.id,
                            itemBId: b.id,
                            itemATitle: a.title,
                            itemBTitle: b.title,
                            date: date,
                            overlappingSections: overlappingSections
                        ))
                    }
                }
            }
        }

        return warnings
    }

    /// Annotates occurrences in-place with `hasConflict` flags and `conflictingItemIds`.
    public func annotateConflicts(in items: inout [TimelineItem]) {
        let conflicts = detectConflicts(in: items)
        guard !conflicts.isEmpty else { return }

        var conflictMap: [String: Set<String>] = [:]
        for c in conflicts {
            conflictMap[c.itemAId, default: []].insert(c.itemBId)
            conflictMap[c.itemBId, default: []].insert(c.itemAId)
        }

        for i in 0..<items.count {
            let id = items[i].id
            if let peerIds = conflictMap[id], !peerIds.isEmpty {
                items[i].hasConflict = true
                items[i].conflictingItemIds = Array(peerIds).sorted()
            }
        }
    }
}
