import SwiftUI
import ClassFlowCore

/// Unified card-based modal sheet enabling users to create a new timetable, matching WakeUp's design language.
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
                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.plain)
                .font(.system(size: 15))
                .foregroundColor(.secondary)

                Spacer()

                Text("新建课表")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("创建并使用") {
                    createAndApply()
                }
                .buttonStyle(.plain)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(semesterName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .secondary.opacity(0.5) : Color(hex: "#FF5A5F"))
                .disabled(semesterName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("课表名称")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 80, alignment: .leading)
                            TextField("例如：大三上课表", text: $semesterName)
                                .textFieldStyle(.plain)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("开学日期")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 80, alignment: .leading)
                            DatePicker("", selection: $startDate, displayedComponents: [.date])
                                .labelsHidden()
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("总周数")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 80, alignment: .leading)
                            Stepper("\(totalWeeks) 周", value: $totalWeeks, in: 1...35)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("作息模板")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 80, alignment: .leading)
                            Picker("", selection: $selectedPreset) {
                                ForEach(SectionPreset.allCases) { preset in
                                    Text(preset.rawValue).tag(preset)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                    }
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(14)

                    Text("创建后可随时通过「上课时间」进一步自定义每节课的起始与结束时间。")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 4)
                }
                .padding(20)
            }
        }
        .frame(width: 440, height: 340)
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
