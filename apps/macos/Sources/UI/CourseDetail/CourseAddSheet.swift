import SwiftUI
import ClassFlowCore

/// Clean, card-based modal sheet enabling creation of courses with multiple recurring schedules,
/// distinct instructors, classrooms, and custom recurrence patterns, unified with WakeUp's design system.
public struct CourseAddSheet: View {
    public let semesterId: UUID
    public let availableSections: [SectionTime]
    public let totalWeeks: Int
    public let onSave: (Course) -> Void
    public let onDismiss: () -> Void

    public let courseNameSuggestions: [String]
    public let teacherSuggestions: [String]
    public let classroomSuggestions: [String]

    @State private var courseName: String = ""
    @State private var creditString: String = ""
    @State private var colorHex: String = "#5B7FB5"
    @State private var notes: String = ""

    public struct ScheduleDraft: Identifiable {
        public enum TimeMode: String, CaseIterable, Identifiable {
            case bySection = "按节次"
            case byCustomTime = "具体时间"
            public var id: String { rawValue }
        }

        public let id: UUID = UUID()
        public var dayOfWeek: Int = 1
        public var timeMode: TimeMode = .bySection
        public var startSection: Int = 1
        public var endSection: Int = 2
        public var customStartTime: String = "08:00"
        public var customEndTime: String = "09:35"
        public var classroom: String = ""
        public var teacher: String = ""
        public var startWeek: Int = 1
        public var endWeek: Int = 20
        public var customWeeks: Set<Int> = Set(1...20)

        public init(
            dayOfWeek: Int = 1,
            timeMode: TimeMode = .bySection,
            startSection: Int = 1,
            endSection: Int = 2,
            customStartTime: String = "08:00",
            customEndTime: String = "09:35",
            classroom: String = "",
            teacher: String = "",
            startWeek: Int = 1,
            endWeek: Int = 20,
            customWeeks: Set<Int>? = nil
        ) {
            self.dayOfWeek = dayOfWeek
            self.timeMode = timeMode
            self.startSection = startSection
            self.endSection = endSection
            self.customStartTime = customStartTime
            self.customEndTime = customEndTime
            self.classroom = classroom
            self.teacher = teacher
            self.startWeek = startWeek
            self.endWeek = endWeek
            self.customWeeks = customWeeks ?? Set(startWeek...endWeek)
        }
    }

    @State private var schedules: [ScheduleDraft]

    private let colors = [
        "#5B7FB5", "#56A3A6", "#E37B75", "#8E7DBE",
        "#E8A87C", "#7BB38E", "#C38D9E", "#457B9D"
    ]

    public init(
        semesterId: UUID,
        availableSections: [SectionTime],
        totalWeeks: Int = 20,
        defaultDayOfWeek: Int = 1,
        initialSlot: (dayOfWeek: Int, startSection: Int, endSection: Int)? = nil,
        courseNameSuggestions: [String] = [],
        teacherSuggestions: [String] = [],
        classroomSuggestions: [String] = [],
        onSave: @escaping (Course) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.semesterId = semesterId
        self.availableSections = availableSections
        self.totalWeeks = max(1, totalWeeks)
        self.courseNameSuggestions = courseNameSuggestions
        self.teacherSuggestions = teacherSuggestions
        self.classroomSuggestions = classroomSuggestions
        self.onSave = onSave
        self.onDismiss = onDismiss

        let clampedWeeks = max(1, totalWeeks)
        if let slot = initialSlot {
            _schedules = State(initialValue: [
                ScheduleDraft(
                    dayOfWeek: slot.dayOfWeek,
                    startSection: slot.startSection,
                    endSection: slot.endSection,
                    startWeek: 1,
                    endWeek: clampedWeeks,
                    customWeeks: Set(1...clampedWeeks)
                )
            ])
        } else {
            _schedules = State(initialValue: [
                ScheduleDraft(
                    dayOfWeek: defaultDayOfWeek,
                    startSection: 1,
                    endSection: 2,
                    startWeek: 1,
                    endWeek: clampedWeeks,
                    customWeeks: Set(1...clampedWeeks)
                )
            ])
        }
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Unified WakeUp-Style Navigation Header
            HStack {
                Button("取消", action: onDismiss)
                    .buttonStyle(.plain)
                    .font(.system(size: 15))
                    .foregroundColor(.secondary)

                Spacer()

                Text("添加课程")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("保存", action: saveCourse)
                    .buttonStyle(.plain)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? .secondary.opacity(0.5) : Color(hex: "#FF5A5F"))
                    .disabled(courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            ScrollView {
                VStack(spacing: 16) {
                    // 1. Basic Info Card
                    VStack(alignment: .leading, spacing: 0) {
                        HStack {
                            Text("课程名称")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            SearchSuggestionTextField(
                                placeholder: "例如：高等数学",
                                text: $courseName,
                                suggestions: courseNameSuggestions,
                                icon: "book.closed"
                            )
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                        .zIndex(20)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("课程学分")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            TextField("例如：3.0 (选填)", text: $creditString)
                                .textFieldStyle(.plain)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("课程备注")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            TextField("例如：期末闭卷考 / 需带实验手册", text: $notes)
                                .textFieldStyle(.plain)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)

                        Divider().padding(.leading, 16)

                        HStack {
                            Text("卡片颜色")
                                .font(.system(size: 14, weight: .medium))
                                .frame(width: 72, alignment: .leading)
                            Spacer()
                            CompactColorPickerRow(selectedHex: $colorHex, presets: colors)
                        }
                        .padding(.horizontal, 16)
                        .padding(.vertical, 12)
                    }
                    .background(
                        RoundedRectangle(cornerRadius: 14)
                            .fill(Color(nsColor: .controlBackgroundColor))
                    )
                    .zIndex(50)

                    // 2. Schedule Cards Section Header
                    HStack {
                        Text("上课安排 (\(schedules.count) 个时段)")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(.secondary)

                        Spacer()

                        Button(action: addScheduleDraft) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                Text("添加时段")
                            }
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, 4)

                    // 3. Schedule Cards
                    ForEach($schedules) { $draft in
                        VStack(alignment: .leading, spacing: 12) {
                            HStack {
                                Text("时段 \(scheduleIndex(for: draft.id) + 1)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: colorHex))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(Color(hex: colorHex).opacity(0.12))
                                    .clipShape(Capsule())

                                Spacer()

                                if schedules.count > 1 {
                                    Button(role: .destructive, action: {
                                        schedules.removeAll(where: { $0.id == draft.id })
                                    }) {
                                        Image(systemName: "trash")
                                            .font(.system(size: 12))
                                            .foregroundColor(.red)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }

                            // Day of Week & Time Mode
                            HStack(spacing: 10) {
                                Picker("星期", selection: $draft.dayOfWeek) {
                                    Text("周一").tag(1)
                                    Text("周二").tag(2)
                                    Text("周三").tag(3)
                                    Text("周四").tag(4)
                                    Text("周五").tag(5)
                                    Text("周六").tag(6)
                                    Text("周日").tag(7)
                                }
                                .pickerStyle(.menu)
                                .frame(width: 95)

                                Picker("", selection: $draft.timeMode) {
                                    ForEach(ScheduleDraft.TimeMode.allCases) { mode in
                                        Text(mode.rawValue).tag(mode)
                                    }
                                }
                                .labelsHidden()
                                .pickerStyle(.segmented)
                                .frame(width: 150)
                            }

                            // Time Picker or Section Range
                            if draft.timeMode == .bySection {
                                HStack(spacing: 8) {
                                    Text("时段:")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)

                                    Picker("", selection: $draft.startSection) {
                                        ForEach(availableSections) { s in
                                            Text("第 \(s.section) 节 (\(s.startTime))").tag(s.section)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)

                                    Text("至")
                                        .font(.caption)
                                        .foregroundColor(.secondary)

                                    Picker("", selection: $draft.endSection) {
                                        ForEach(availableSections.filter { $0.section >= draft.startSection }) { s in
                                            Text("第 \(s.section) 节 (\(s.endTime))").tag(s.section)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                }
                            } else {
                                HStack(spacing: 8) {
                                    Text("时间:")
                                        .font(.system(size: 12))
                                        .foregroundColor(.secondary)

                                    TextField("08:00", text: $draft.customStartTime)
                                        .textFieldStyle(.roundedBorder)
                                        .frame(width: 72)

                                    Text("至")
                                        .font(.caption)
                                        .foregroundColor(.secondary)

                                    TextField("09:35", text: $draft.customEndTime)
                                        .textFieldStyle(.roundedBorder)
                                        .frame(width: 72)
                                }
                            }

                            // Classroom Location & Assigned Teacher
                            HStack(spacing: 10) {
                                SearchSuggestionTextField(
                                    placeholder: "教室 (如：理学楼 302)",
                                    text: $draft.classroom,
                                    suggestions: classroomSuggestions,
                                    icon: "mappin.and.ellipse",
                                    hasContainer: true
                                )
                                .zIndex(5)

                                SearchSuggestionTextField(
                                    placeholder: "任课教师 (可选)",
                                    text: $draft.teacher,
                                    suggestions: teacherSuggestions,
                                    icon: "person",
                                    hasContainer: true
                                )
                                .zIndex(4)
                            }
                            .zIndex(10)

                            // Teaching Weeks Matrix (周次范围 + 周次矩阵直选)
                            VStack(alignment: .leading, spacing: 8) {
                                HStack(spacing: 8) {
                                    Text("周次范围:")
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(.secondary)

                                    Picker("", selection: $draft.startWeek) {
                                        ForEach(1...totalWeeks, id: \.self) { w in
                                            Text("第 \(w) 周").tag(w)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                    .frame(width: 85)
                                    .onChange(of: draft.startWeek) { _, newStart in
                                        let low = min(newStart, draft.endWeek)
                                        let high = max(newStart, draft.endWeek)
                                        draft.customWeeks = Set(low...high)
                                    }

                                    Text("至")
                                        .font(.caption)
                                        .foregroundColor(.secondary)

                                    Picker("", selection: $draft.endWeek) {
                                        ForEach(1...totalWeeks, id: \.self) { w in
                                            Text("第 \(w) 周").tag(w)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                    .frame(width: 85)
                                    .onChange(of: draft.endWeek) { _, newEnd in
                                        let low = min(draft.startWeek, newEnd)
                                        let high = max(draft.startWeek, newEnd)
                                        draft.customWeeks = Set(low...high)
                                    }
                                }

                                WeekMatrixPickerView(
                                    selectedWeeks: $draft.customWeeks,
                                    maxWeeks: totalWeeks,
                                    accentColor: Color(hex: colorHex)
                                )
                            }
                        }
                        .padding(14)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color(nsColor: .controlBackgroundColor))
                        )
                        .zIndex(Double(40 - scheduleIndex(for: draft.id)))
                    }
                }
                .padding(20)
            }
        }
        .frame(width: 500, height: 600)
    }

    private func scheduleIndex(for id: UUID) -> Int {
        schedules.firstIndex(where: { $0.id == id }) ?? 0
    }

    private func addScheduleDraft() {
        let nextDay = min(7, (schedules.last?.dayOfWeek ?? 1) + 2)
        schedules.append(
            ScheduleDraft(
                dayOfWeek: nextDay,
                startSection: 1,
                endSection: 2,
                startWeek: 1,
                endWeek: totalWeeks,
                customWeeks: Set(1...totalWeeks)
            )
        )
    }

    private func saveCourse() {
        let courseId = UUID()
        let courseSchedules: [CourseSchedule] = schedules.map { draft in
            let teacherVal = draft.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
            let weeks = Array(draft.customWeeks).sorted()
            let pattern = weeks.isEmpty ? .all : WeekPattern.custom(weeks)
            let customStart = (draft.timeMode == .byCustomTime) ? draft.customStartTime : nil
            let customEnd = (draft.timeMode == .byCustomTime) ? draft.customEndTime : nil

            return CourseSchedule(
                id: UUID(),
                courseId: courseId,
                dayOfWeek: draft.dayOfWeek,
                startSection: draft.startSection,
                endSection: max(draft.startSection, draft.endSection),
                classroom: draft.classroom.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : draft.classroom,
                teacher: teacherVal.isEmpty ? nil : teacherVal,
                weekPattern: pattern,
                customStartTime: customStart,
                customEndTime: customEnd
            )
        }

        let firstTeacher = schedules.map { $0.teacher.trimmingCharacters(in: .whitespacesAndNewlines) }.first(where: { !$0.isEmpty })
        let parsedCredit = Double(creditString.trimmingCharacters(in: .whitespacesAndNewlines))
        let newCourse = Course(
            id: courseId,
            semesterId: semesterId,
            name: courseName.trimmingCharacters(in: .whitespacesAndNewlines),
            teacher: firstTeacher,
            credit: parsedCredit,
            color: colorHex,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes,
            schedules: courseSchedules
        )
        onSave(newCourse)
    }
}
