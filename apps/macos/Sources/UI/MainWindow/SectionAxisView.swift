import SwiftUI
import ClassFlowCore

/// Vertical section timeline axis on the left of the timetable grid, displaying period numbers and start/end times.
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
                VStack(spacing: 1.5) {
                    Text("\(s.section)")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.primary)

                    Text(s.startTime)
                        .font(.system(size: 8.5, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary)

                    Text(s.endTime)
                        .font(.system(size: 8.5, weight: .regular, design: .monospaced))
                        .foregroundColor(.secondary.opacity(0.8))
                }
                .frame(width: 48, height: periodHeight)
                .background(
                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                        .fill(Color.primary.opacity(0.02))
                )
            }
        }
    }
}
