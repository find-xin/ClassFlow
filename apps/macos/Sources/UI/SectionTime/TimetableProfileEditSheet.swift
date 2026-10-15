import SwiftUI
import ClassFlowCore

/// Sheet replicating WakeUp's "编辑时间表" screen with fixed duration auto-linkage and per-section time editing.
public struct TimetableProfileEditSheet: View {
    public let profile: TimetableProfile
    public let onSave: (TimetableProfile) -> Void
    public let onDismiss: () -> Void

    @State private var name: String
    @State private var fixedDuration: Bool
    @State private var sectionDurationMinutes: Int

    public struct SectionDraft: Identifiable {
        public let id: UUID = UUID()
        public var section: Int
        public var startTime: String
        public var endTime: String
    }

    @State private var sections: [SectionDraft]

    private let availableDurations = [30, 35, 40, 45, 50, 55, 60]

    public init(
        profile: TimetableProfile,
        onSave: @escaping (TimetableProfile) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.profile = profile
        self.onSave = onSave
        self.onDismiss = onDismiss

        _name = State(initialValue: profile.name)
        _fixedDuration = State(initialValue: profile.fixedDuration)
        _sectionDurationMinutes = State(initialValue: profile.sectionDurationMinutes)
        _sections = State(initialValue: profile.sections.map {
            SectionDraft(section: $0.section, startTime: $0.startTime, endTime: $0.endTime)
        })
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header matching Image 2
            HStack {
                Button(action: onDismiss) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.primary)
                }
                .buttonStyle(.plain)

                Spacer()

                Text("编辑时间表")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("保存") {
                    saveProfile()
                }
                .buttonStyle(.plain)
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 14)
                .padding(.vertical, 5)
                .background(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? Color.secondary.opacity(0.4) : Color(hex: "#FF5A5F"))
                .clipShape(Capsule())
                .disabled(name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // 1. Top Card: Name
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("时间表名称")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.primary)

                            Spacer()

                            TextField("例如：默认", text: $name)
                                .textFieldStyle(.plain)
                                .multilineTextAlignment(.trailing)
                                .frame(width: 220)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 14)
                    }
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(12)

                    // 2. Middle Card: Fixed duration & per-section duration
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("固定上课时长")
                                    .font(.system(size: 14, weight: .medium))
                                Text("修改上课时间会跟随联动")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }

                            Spacer()

                            Toggle("", isOn: $fixedDuration)
                                .labelsHidden()
                                .toggleStyle(.switch)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)

                        Divider()
                            .padding(.leading, 16)

                        HStack {
                            Text("每节课上课时长")
                                .font(.system(size: 14, weight: .medium))

                            Spacer()

                            Picker("", selection: $sectionDurationMinutes) {
                                ForEach(availableDurations, id: \.self) { duration in
                                    Text("\(duration)分钟").tag(duration)
                                }
                            }
                            .labelsHidden()
                            .pickerStyle(.menu)
                            .onChange(of: sectionDurationMinutes) { _, newDuration in
                                if fixedDuration {
                                    recalculateAllEndTimes(duration: newDuration)
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                    }
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(12)

                    // 3. Sections Header
                    HStack {
                        Text("请注意是 24 小时制！")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)

                        Spacer()

                        Button(action: addSection) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                Text("添加节次")
                            }
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 4)
                    .padding(.top, 4)

                    // 4. Sections List
                    VStack(spacing: 0) {
                        ForEach($sections) { $draft in
                            HStack {
                                Text("第 \(draft.section) 节课")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.primary)

                                Spacer()

                                HStack(spacing: 6) {
                                    TextField("08:30", text: $draft.startTime)
                                        .textFieldStyle(.plain)
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .frame(width: 50)
                                        .multilineTextAlignment(.center)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 4)
                                        .background(Color(nsColor: .textBackgroundColor).opacity(0.8))
                                        .cornerRadius(6)
                                        .onChange(of: draft.startTime) { _, newStart in
                                            if fixedDuration {
                                                draft.endTime = computeEndTime(start: newStart, minutes: sectionDurationMinutes)
                                            }
                                        }

                                    Text("-")
                                        .foregroundColor(.secondary)

                                    TextField("09:15", text: $draft.endTime)
                                        .textFieldStyle(.plain)
                                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                                        .frame(width: 50)
                                        .multilineTextAlignment(.center)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 4)
                                        .background(Color(nsColor: .textBackgroundColor).opacity(0.8))
                                        .cornerRadius(6)
                                }

                                if sections.count > 1 {
                                    Button(role: .destructive, action: {
                                        removeSection(id: draft.id)
                                    }) {
                                        Image(systemName: "minus.circle.fill")
                                            .font(.system(size: 15))
                                            .foregroundColor(.red.opacity(0.8))
                                    }
                                    .buttonStyle(.plain)
                                    .padding(.leading, 6)
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 10)

                            if draft.section < sections.count {
                                Divider()
                                    .padding(.leading, 16)
                            }
                        }
                    }
                    .background(Color(nsColor: .controlBackgroundColor))
                    .cornerRadius(12)
                }
                .padding(20)
            }
        }
        .frame(width: 480, height: 600)
    }

    private func addSection() {
        let nextSecNum = sections.count + 1
        var newStart = "08:00"
        if let last = sections.last {
            newStart = computeEndTime(start: last.endTime, minutes: 10) // 10 min break
        }
        let newEnd = computeEndTime(start: newStart, minutes: sectionDurationMinutes)
        sections.append(SectionDraft(section: nextSecNum, startTime: newStart, endTime: newEnd))
    }

    private func removeSection(id: UUID) {
        sections.removeAll(where: { $0.id == id })
        for i in 0..<sections.count {
            sections[i].section = i + 1
        }
    }

    private func recalculateAllEndTimes(duration: Int) {
        for i in 0..<sections.count {
            sections[i].endTime = computeEndTime(start: sections[i].startTime, minutes: duration)
        }
    }

    private func computeEndTime(start: String, minutes: Int) -> String {
        let parts = start.split(separator: ":")
        guard parts.count == 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return start }
        let totalMinutes = (h * 60 + m + minutes) % (24 * 60)
        let newH = totalMinutes / 60
        let newM = totalMinutes % 60
        return String(format: "%02d:%02d", newH, newM)
    }

    private func saveProfile() {
        let cleanSections = sections.map {
            SectionTime(section: $0.section, startTime: $0.startTime.trimmingCharacters(in: .whitespaces), endTime: $0.endTime.trimmingCharacters(in: .whitespaces))
        }
        let updated = TimetableProfile(
            id: profile.id,
            name: name.trimmingCharacters(in: .whitespacesAndNewlines),
            sections: cleanSections,
            fixedDuration: fixedDuration,
            sectionDurationMinutes: sectionDurationMinutes
        )
        onSave(updated)
    }
}
