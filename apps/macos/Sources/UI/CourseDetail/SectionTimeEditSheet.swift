import SwiftUI
import ClassFlowCore

/// Sheet enabling users to customize the start and end times for each section/period of a semester.
public struct SectionTimeEditSheet: View {
    @Bindable public var appState: AppState
    public let targetSemester: Semester
    @Environment(\.dismiss) private var dismiss

    @State private var sections: [EditableSection] = []
    @State private var autoStartHour: Int = 8
    @State private var autoStartMinute: Int = 30
    @State private var classDurationMinutes: Int = 45
    @State private var breakDurationMinutes: Int = 10

    public struct EditableSection: Identifiable {
        public let id: UUID = UUID()
        public var sectionNumber: Int
        public var startTime: String
        public var endTime: String
    }

    public init(appState: AppState, targetSemester: Semester? = nil) {
        self.appState = appState
        self.targetSemester = targetSemester ?? appState.currentSemester ?? Semester(name: "默认学期", startDate: "2026-09-01", endDate: "2027-01-31")
    }

    public var body: some View {
        VStack(spacing: 0) {
            // 1. Header
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("上课时间设置")
                        .font(.headline)
                    Text("当前课表: \(targetSemester.name) (共 \(sections.count) 节)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("取消") {
                    dismiss()
                }
                .keyboardShortcut(.cancelAction)
            }
            .padding()
            .background(.bar)

            Divider()

            // 2. Preset & Quick Auto-Generator Toolbar
            HStack(spacing: 12) {
                Text("快捷模板:")
                    .font(.system(size: 12, weight: .semibold))

                Button("标准作息 (08:30起)") {
                    applyPreset(AppState.standardSectionTimes())
                }
                .buttonStyle(.bordered)
                .font(.system(size: 11))

                Button("早八作息 (08:00起)") {
                    applyPreset(AppState.earlySectionTimes())
                }
                .buttonStyle(.bordered)
                .font(.system(size: 11))

                Spacer()

                Button(action: addSection) {
                    Label("添加节次", systemImage: "plus.circle")
                }
                .buttonStyle(.bordered)
                .font(.system(size: 11))
            }
            .padding(.horizontal)
            .padding(.vertical, 8)
            .background(Color.secondary.opacity(0.06))

            Divider()

            // 3. Section List
            List {
                ForEach($sections) { $item in
                    HStack(spacing: 14) {
                        Text("第 \(item.sectionNumber) 节")
                            .font(.system(size: 13, weight: .bold))
                            .frame(width: 60, alignment: .leading)

                        HStack(spacing: 4) {
                            Text("开始:")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("08:30", text: $item.startTime)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 75)
                                .font(.system(size: 12, design: .monospaced))
                        }

                        HStack(spacing: 4) {
                            Text("结束:")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            TextField("09:15", text: $item.endTime)
                                .textFieldStyle(.roundedBorder)
                                .frame(width: 75)
                                .font(.system(size: 12, design: .monospaced))
                        }

                        Spacer()

                        Button(action: {
                            deleteSection(id: item.id)
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 11))
                                .foregroundColor(.red)
                        }
                        .buttonStyle(.borderless)
                        .disabled(sections.count <= 4)
                        .help(sections.count <= 4 ? "至少保留4节课" : "删除此节次")
                    }
                    .padding(.vertical, 2)
                }
            }
            .listStyle(.inset(alternatesRowBackgrounds: true))

            Divider()

            // 4. Footer Actions
            HStack {
                Text("提示: 时间格式为 24小时制 HH:mm (如 08:30)。保存后即刻更新课表坐标轴。")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                Spacer()

                Button("取消") {
                    dismiss()
                }
                .buttonStyle(.bordered)

                Button("保存并应用") {
                    saveSections()
                }
                .buttonStyle(.borderedProminent)
            }
            .padding()
            .background(.bar)
        }
        .frame(width: 520, height: 480)
        .onAppear {
            loadExistingSections()
        }
    }

    private func loadExistingSections() {
        if targetSemester.sections.isEmpty {
            applyPreset(AppState.standardSectionTimes())
        } else {
            sections = targetSemester.sections.sorted(by: { $0.section < $1.section }).map { s in
                EditableSection(sectionNumber: s.section, startTime: s.startTime, endTime: s.endTime)
            }
        }
    }

    private func applyPreset(_ presetSections: [SectionTime]) {
        sections = presetSections.map { s in
            EditableSection(sectionNumber: s.section, startTime: s.startTime, endTime: s.endTime)
        }
    }

    private func addSection() {
        let nextIndex = (sections.last?.sectionNumber ?? 0) + 1
        var nextStart = "21:55"
        var nextEnd = "22:40"

        if let last = sections.last,
           let lastEndHour = Int(last.endTime.prefix(2)),
           let lastEndMin = Int(last.endTime.suffix(2)) {
            let totalEndMin = lastEndHour * 60 + lastEndMin + 10
            let startH = totalEndMin / 60
            let startM = totalEndMin % 60
            let totalNextEndMin = totalEndMin + 45
            let endH = totalNextEndMin / 60
            let endM = totalNextEndMin % 60
            nextStart = String(format: "%02d:%02d", min(23, startH), startM)
            nextEnd = String(format: "%02d:%02d", min(23, endH), endM)
        }

        sections.append(
            EditableSection(sectionNumber: nextIndex, startTime: nextStart, endTime: nextEnd)
        )
    }

    private func deleteSection(id: UUID) {
        sections.removeAll(where: { $0.id == id })
        // Renumber remaining sections sequentially
        for i in 0..<sections.count {
            sections[i].sectionNumber = i + 1
        }
    }

    private func saveSections() {
        let validSections: [SectionTime] = sections.enumerated().map { index, sec in
            let cleanStart = sec.startTime.trimmingCharacters(in: .whitespacesAndNewlines)
            let cleanEnd = sec.endTime.trimmingCharacters(in: .whitespacesAndNewlines)
            return SectionTime(
                section: index + 1,
                startTime: cleanStart.isEmpty ? "08:00" : cleanStart,
                endTime: cleanEnd.isEmpty ? "08:45" : cleanEnd
            )
        }

        appState.updateSectionTimes(semesterId: targetSemester.id, sections: validSections)
        dismiss()
    }
}
