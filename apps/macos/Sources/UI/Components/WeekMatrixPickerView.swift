import SwiftUI
import ClassFlowCore

/// Interactive 1..25 week matrix multi-selection grid matching AISchedule & WakeUp conventions,
/// allowing students to customize non-consecutive teaching weeks with one-click toggles and presets.
public struct WeekMatrixPickerView: View {
    @Binding public var selectedWeeks: Set<Int>
    public let maxWeeks: Int
    public let accentColor: Color

    public init(
        selectedWeeks: Binding<Set<Int>>,
        maxWeeks: Int = 25,
        accentColor: Color = Color(hex: "#FF5A5F")
    ) {
        self._selectedWeeks = selectedWeeks
        self.maxWeeks = maxWeeks
        self.accentColor = accentColor
    }

    private let columns = Array(repeating: GridItem(.flexible(), spacing: 6), count: 5)

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            // Quick preset action pills
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    presetButton("全选") {
                        selectedWeeks = Set(1...maxWeeks)
                    }
                    presetButton("单周") {
                        selectedWeeks = Set((1...maxWeeks).filter { $0 % 2 == 1 })
                    }
                    presetButton("双周") {
                        selectedWeeks = Set((1...maxWeeks).filter { $0 % 2 == 0 })
                    }
                    presetButton("前八周") {
                        selectedWeeks = Set(1...min(8, maxWeeks))
                    }
                    presetButton("后八周") {
                        let start = min(9, maxWeeks)
                        let end = min(16, maxWeeks)
                        selectedWeeks = (start <= end) ? Set(start...end) : []
                    }
                    presetButton("清空") {
                        selectedWeeks.removeAll()
                    }
                }
            }

            // 1..maxWeeks 5-column grid
            LazyVGrid(columns: columns, spacing: 6) {
                ForEach(1...maxWeeks, id: \.self) { week in
                    let isSelected = selectedWeeks.contains(week)
                    Button(action: {
                        if isSelected {
                            selectedWeeks.remove(week)
                        } else {
                            selectedWeeks.insert(week)
                        }
                    }) {
                        Text("\(week)")
                            .font(.system(size: 12, weight: isSelected ? .bold : .medium, design: .monospaced))
                            .foregroundColor(isSelected ? .white : .primary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 28)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(isSelected ? accentColor : Color(nsColor: .textBackgroundColor).opacity(0.6))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }

            // Selection Summary
            HStack {
                Text(summaryText)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                Spacer()
                Text("共 \(selectedWeeks.count) 周")
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .foregroundColor(selectedWeeks.isEmpty ? .secondary : accentColor)
            }
        }
        .padding(12)
        .background(Color(nsColor: .controlBackgroundColor).opacity(0.6))
        .cornerRadius(10)
    }

    private func presetButton(_ title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11, weight: .medium))
                .foregroundColor(.primary)
                .padding(.horizontal, 9)
                .padding(.vertical, 4)
                .background(Color(nsColor: .textBackgroundColor).opacity(0.8))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var summaryText: String {
        guard !selectedWeeks.isEmpty else { return "未选择任何周次" }
        let sorted = selectedWeeks.sorted()
        if sorted.count == maxWeeks {
            return "全学期每周上课 (1-\(maxWeeks)周)"
        }
        return "已选: \(formatRanges(sorted))"
    }

    private func formatRanges(_ sorted: [Int]) -> String {
        guard let first = sorted.first else { return "" }
        var ranges: [String] = []
        var start = first
        var prev = first

        for w in sorted.dropFirst() {
            if w == prev + 1 {
                prev = w
            } else {
                ranges.append(start == prev ? "\(start)周" : "\(start)-\(prev)周")
                start = w
                prev = w
            }
        }
        ranges.append(start == prev ? "\(start)周" : "\(start)-\(prev)周")
        return ranges.joined(separator: ", ")
    }
}
