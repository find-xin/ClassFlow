import SwiftUI
import ClassFlowCore

/// Non-blocking warning pill indicating time overlaps on the timetable.
public struct ConflictBannerView: View {
    public let conflictCount: Int

    public init(conflictCount: Int) {
        self.conflictCount = conflictCount
    }

    public var body: some View {
        if conflictCount > 0 {
            HStack(spacing: 6) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.orange)
                    .imageScale(.small)
                Text("本周有 \(conflictCount) 门课程时间重叠")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.primary)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(.ultraThinMaterial)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(Color.orange.opacity(0.4), lineWidth: 1))
        }
    }
}
