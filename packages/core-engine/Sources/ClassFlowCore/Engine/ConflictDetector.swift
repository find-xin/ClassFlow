import Foundation

/// Pure engine for non-blocking schedule conflict detection.
public struct ConflictDetector: Sendable {
    public init() {}

    /// Detects all pairwise time/section conflicts across a collection of occurrences.
    public func detectConflicts(in items: [TimelineItem]) -> [ConflictWarning] {
        var warnings: [ConflictWarning] = []
        let activeItems = items.filter { $0.status != .cancelled }

        guard activeItems.count >= 2 else { return [] }

        for i in 0..<activeItems.count {
            for j in (i + 1)..<activeItems.count {
                let a = activeItems[i]
                let b = activeItems[j]

                // Must be on the exact same date
                guard a.date == b.date else { continue }

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
                        date: a.date,
                        overlappingSections: overlappingSections
                    ))
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
