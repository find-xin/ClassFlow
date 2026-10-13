import SwiftUI
import ClassFlowCore

/// Vertical section timeline axis on the left of the timetable grid.
public struct SectionAxisView: View {
    public let sections: [SectionTime]
    public let periodHeight: CGFloat

    public init(sections: [SectionTime], periodHeight: CGFloat = 64) {
        self.sections = sections
        self.periodHeight = periodHeight
    }

    public var body: some View {
        VStack(spacing: 4) {
            ForEach(sections) { s in
                VStack(spacing: 2) {
                    Text("\(s.section)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.primary)

                    Text(s.startTime)
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.secondary)

                    Text(s.endTime)
                        .font(.system(size: 9, design: .monospaced))
                        .foregroundColor(.secondary.opacity(0.8))
                }
                .frame(width: 48, height: periodHeight)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(Color.primary.opacity(0.03))
                )
            }
        }
    }
}
