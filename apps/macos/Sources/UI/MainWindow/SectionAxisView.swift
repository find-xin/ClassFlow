import SwiftUI
import ClassFlowCore

/// Vertical section timeline axis on the side of the timetable grid, matching WakeUp's typography.
public struct SectionAxisView: View {
    public let sections: [SectionTime]
    public let periodHeight: CGFloat
    public let showTime: Bool

    public init(sections: [SectionTime], periodHeight: CGFloat = 68, showTime: Bool = true) {
        self.sections = sections
        self.periodHeight = periodHeight
        self.showTime = showTime
    }

    public var body: some View {
        VStack(spacing: 4) {
            ForEach(sections) { s in
                VStack(spacing: 1) {
                    Text(s.section <= 0 ? "早" : (s.section >= 90 ? "晚" : "\(s.section)"))
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.primary.opacity(0.85))

                    if showTime {
                        Text(s.startTime)
                            .font(.system(size: 8.5, weight: .medium, design: .monospaced))
                            .foregroundColor(.secondary.opacity(0.85))

                        Text(s.endTime)
                            .font(.system(size: 8.5, weight: .regular, design: .monospaced))
                            .foregroundColor(.secondary.opacity(0.65))
                    }
                }
                .frame(width: 44, height: periodHeight)
            }
        }
    }
}
