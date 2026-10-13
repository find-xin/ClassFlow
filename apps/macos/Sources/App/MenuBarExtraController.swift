import SwiftUI
import ClassFlowCore

/// View content hosted in the macOS menu bar companion extra.
public struct MenuBarCompanionView: View {
    @Bindable public var appState: AppState

    public init(appState: AppState) {
        self.appState = appState
    }

    private var todayDateString: String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    private var todayOccurrences: [TimelineItem] {
        appState.currentWeekOccurrences.filter { $0.date == todayDateString && $0.status != .cancelled }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text("今日课程")
                    .font(.headline)
                Spacer()
                Text("第 \(appState.calculatedCurrentWeek) 周")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            Divider()

            if todayOccurrences.isEmpty {
                VStack(spacing: 4) {
                    Image(systemName: "cup.and.saucer.fill")
                        .font(.system(size: 20))
                        .foregroundColor(.secondary)
                    Text("今日无课")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            } else {
                ForEach(todayOccurrences) { item in
                    HStack(alignment: .center, spacing: 8) {
                        Circle()
                            .fill(Color(hex: item.color) ?? .accentColor)
                            .frame(width: 8, height: 8)

                        VStack(alignment: .leading, spacing: 1) {
                            Text(item.title)
                                .font(.system(size: 12, weight: .semibold))
                            Text("第 \(item.startSection)-\(item.endSection) 节 · \(item.location ?? "未指定教室")")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                        Spacer()

                        Text(item.startTime ?? "")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }

            Divider()

            // Footer
            HStack {
                Button("打开主课表") {
                    NSApp.activate(ignoringOtherApps: true)
                    if let window = NSApp.windows.first(where: { $0.canBecomeMain }) {
                        window.makeKeyAndOrderFront(nil)
                    }
                }
                .buttonStyle(.borderedProminent)
                .font(.caption)

                Spacer()

                Button("退出") {
                    NSApplication.shared.terminate(nil)
                }
                .buttonStyle(.bordered)
                .font(.caption)
            }
        }
        .padding(10)
        .frame(width: 260)
    }
}
