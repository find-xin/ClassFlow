import SwiftUI
import ClassFlowCore

/// Modal sheet enabling users to create a brand-new timetable with custom name, date, weeks, and section preset.
public struct SemesterCreateSheet: View {
    @Bindable public var appState: AppState
    @Environment(\.dismiss) private var dismiss

    @State private var semesterName: String = ""
    @State private var startDate: Date = Date()
    @State private var totalWeeks: Int = 20
    @State private var selectedPreset: SectionPreset = .standard

    public enum SectionPreset: String, CaseIterable, Identifiable {
        case standard = "高校标准作息 (08:30开始)"
        case early = "早八作息 (08:00开始)"

        public var id: String { rawValue }
    }

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("新建课表")
                    .font(.headline)
                Spacer()
                Button("取消") {
                    dismiss()
                }
                .keyboardShortcut(.cancelAction)
            }
            .padding()
            .background(.bar)

            Divider()

            // Form
            Form {
                Section {
                    TextField("课表名称 (例如: 大三上课表 / 2026秋季学期)", text: $semesterName)
                        .textFieldStyle(.roundedBorder)

                    DatePicker("开学日期 (第一教学周周一)", selection: $startDate, displayedComponents: [.date])

                    Stepper("总教学周数: \(totalWeeks) 周", value: $totalWeeks, in: 1...35)

                    Picker("作息时间模板", selection: $selectedPreset) {
                        ForEach(SectionPreset.allCases) { preset in
                            Text(preset.rawValue).tag(preset)
                        }
                    }
                } footer: {
                    Text("创建后可随时通过「上课时间」自定义每节课的起始与结束时间。")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .formStyle(.grouped)
            .padding()

            Divider()

            // Bottom Actions
            HStack {
                Spacer()
                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Button("创建并使用") {
                    createAndApply()
                }
                .buttonStyle(.borderedProminent)
                .disabled(semesterName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding()
            .background(.bar)
        }
        .frame(width: 460, height: 360)
        .onAppear {
            if semesterName.isEmpty {
                semesterName = "新学期课表 \(appState.semesters.count + 1)"
            }
        }
    }

    private func createAndApply() {
        let trimmed = semesterName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        let dateStr = df.string(from: startDate)

        let sections: [SectionTime]
        switch selectedPreset {
        case .standard:
            sections = AppState.standardSectionTimes()
        case .early:
            sections = AppState.earlySectionTimes()
        }

        appState.createSemester(
            name: trimmed,
            startDate: dateStr,
            totalWeeks: totalWeeks,
            sections: sections
        )
        dismiss()
    }
}
