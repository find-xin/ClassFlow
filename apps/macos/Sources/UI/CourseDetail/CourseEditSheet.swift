import SwiftUI
import ClassFlowCore

/// Unified card-based editing sheet supporting both complete multi-schedule course updates (distinct teachers, rooms, times)
/// and temporary single-occurrence rescheduling, matching WakeUp's design language.
public struct CourseEditSheet: View {
    public let course: Course
    public let occurrence: TimelineItem?
    public let availableSections: [SectionTime]
    public let onSaveCourse: (Course) -> Void
    public let onApplyMutation: ((EditingScope, Int, Int, Int, String?) -> Void)?
    public let onDismiss: () -> Void

    public let courseNameSuggestions: [String]
    public let teacherSuggestions: [String]
    public let classroomSuggestions: [String]

    public enum EditMode: String, CaseIterable, Identifiable {
        case fullCourse = "全课程与所有时段"
        case singleOccurrence = "仅临时调课本次"

        public var id: String { rawValue }
    }

    @State private var editMode: EditMode = .fullCourse
    @State private var courseName: String
    @State private var defaultTeacher: String
    @State private var colorHex: String
    @State private var notes: String

    // Single occurrence mutation state
    @State private var mutationScope: EditingScope = .thisOnly
    @State private var mutationDayOfWeek: Int = 1
    @State private var mutationStartSection: Int = 1
    @State private var mutationEndSection: Int = 2
    @State private var mutationClassroom: String = ""

    public struct ScheduleDraft: Identifiable {
        public enum TimeMode: String, CaseIterable, Identifiable {
            case bySection = "按节次"
            case byCustomTime = "具体时间"
            public var id: String { rawValue }
        }

        public let id: UUID = UUID()
        public var originalId: UUID? = nil
        public var dayOfWeek: Int = 1
        public var timeMode: TimeMode = .bySection
        public var startSection: Int = 1
        public var endSection: Int = 2
        public var customStartTime: String = "08:00"
        public var customEndTime: String = "09:35"
        public var classroom: String = ""
        public var teacher: String = ""
        public var startWeek: Int = 1
        public var endWeek: Int = 16
        public var customWeeks: Set<Int> = Set(1...16)
    }

    @State private var schedules: [ScheduleDraft] = []

    private let colors = [
        "#5B7FB5", "#56A3A6", "#E37B75", "#8E7DBE",
        "#E8A87C", "#7BB38E", "#C38D9E", "#457B9D"
    ]

    public init(
        course: Course,
        occurrence: TimelineItem? = nil,
        initialMode: EditMode = .fullCourse,
        availableSections: [SectionTime],
        courseNameSuggestions: [String] = [],
        teacherSuggestions: [String] = [],
        classroomSuggestions: [String] = [],
        onSaveCourse: @escaping (Course) -> Void,
        onApplyMutation: ((EditingScope, Int, Int, Int, String?) -> Void)? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.course = course
        self.occurrence = occurrence
        self.availableSections = availableSections
        self.courseNameSuggestions = courseNameSuggestions
        self.teacherSuggestions = teacherSuggestions
        self.classroomSuggestions = classroomSuggestions
        self.onSaveCourse = onSaveCourse
        self.onApplyMutation = onApplyMutation
        self.onDismiss = onDismiss

        _editMode = State(initialValue: occurrence != nil ? initialMode : .fullCourse)
        _courseName = State(initialValue: course.name)
        _defaultTeacher = State(initialValue: course.teacher ?? "")
        _colorHex = State(initialValue: course.color)
        _notes = State(initialValue: course.notes ?? "")

        if let occ = occurrence {
            _mutationDayOfWeek = State(initialValue: occ.dayOfWeek)
            _mutationStartSection = State(initialValue: occ.startSection)
            _mutationEndSection = State(initialValue: occ.endSection)
            _mutationClassroom = State(initialValue: occ.location ?? "")
        }

        let drafts: [ScheduleDraft] = course.schedules.isEmpty ? [ScheduleDraft()] : course.schedules.map { s in
            let customSet: Set<Int>
            if let weeks = s.weekPattern.customWeeks, !weeks.isEmpty {
                customSet = Set(weeks)
            } else {
                let sW = max(1, s.weekPattern.startWeek)
                let eW = max(sW, s.weekPattern.endWeek)
                customSet = Set(sW...eW)
            }
            let sW = max(1, s.weekPattern.startWeek)
            let eW = max(sW, s.weekPattern.endWeek)

            return ScheduleDraft(
                originalId: s.id,
                dayOfWeek: s.dayOfWeek,
                timeMode: (s.customStartTime != nil) ? .byCustomTime : .bySection,
                startSection: s.startSection,
                endSection: s.endSection,
                customStartTime: s.customStartTime ?? "08:00",
                customEndTime: s.customEndTime ?? "09:35",
                classroom: s.classroom ?? "",
                teacher: s.teacher ?? "",
                startWeek: sW,
                endWeek: eW,
                customWeeks: customSet
            )
        }
        _schedules = State(initialValue: drafts)
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Button("取消", action: onDismiss)
                    .buttonStyle(.plain)
                    .font(.system(size: 15))
                    .foregroundColor(.secondary)

                Spacer()

                Text(editMode == .singleOccurrence ? "临时调课设置" : "编辑课程")
                    .font(.system(size: 16, weight: .bold))

                Spacer()

                Button("保存") {
                    handleSave()
                }
                .buttonStyle(.plain)
                .font(.system(size: 15, weight: .bold))
                .foregroundColor((editMode == .fullCourse && courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) ? .secondary.opacity(0.5) : Color(hex: "#FF5A5F"))
                .disabled(editMode == .fullCourse && courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(.bar)

            Divider()

            if occurrence != nil {
                Picker("", selection: $editMode) {
                    ForEach(EditMode.allCases) { mode in
                        Text(mode.rawValue).tag(mode)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .padding(.horizontal, 20)
                .padding(.top, 14)
            }

            ScrollView {
                VStack(spacing: 16) {
                    if editMode == .singleOccurrence, let occ = occurrence {
                        // 1. Single Occurrence Adjustment Card
                        VStack(alignment: .leading, spacing: 14) {
                            Text("本次课时 (\(occ.date) 第\(occ.startSection)-\(occ.endSection)节)")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.accentColor)

                            VStack(alignment: .leading, spacing: 6) {
                                Text("生效范围")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)

                                Picker("", selection: $mutationScope) {
                                    Text("仅修改本次 (\(occ.date))").tag(EditingScope.thisOnly)
                                    Text("修改本次及后续周次").tag(EditingScope.thisAndFuture)
                                    Text("修改整学期全部安排").tag(EditingScope.all)
                                }
                                .pickerStyle(.radioGroup)
                            }

                            Divider()

                            HStack(spacing: 12) {
                                Picker("调整至星期", selection: $mutationDayOfWeek) {
                                    Text("周一").tag(1)
                                    Text("周二").tag(2)
                                    Text("周三").tag(3)
                                    Text("周四").tag(4)
                                    Text("周五").tag(5)
                                    Text("周六").tag(6)
                                    Text("周日").tag(7)
                                }
                                .pickerStyle(.menu)
                                .frame(width: 140)

                                HStack(spacing: 4) {
                                    Picker("", selection: $mutationStartSection) {
                                        ForEach(availableSections) { s in
                                            Text("第 \(s.section) 节").tag(s.section)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                    .frame(width: 85)

                                    Text("至")
                                        .font(.caption)
                                        .foregroundColor(.secondary)

                                    Picker("", selection: $mutationEndSection) {
                                        ForEach(availableSections.filter { $0.section >= mutationStartSection }) { s in
                                            Text("第 \(s.section) 节").tag(s.section)
                                        }
                                    }
                                    .labelsHidden()
                                    .pickerStyle(.menu)
                                    .frame(width: 85)
                                }
                            }

                            HStack {
                                Text("调整后教室")
                                    .font(.system(size: 13))
                                    .foregroundColor(.secondary)
                                    .frame(width: 72, alignment: .leading)
                                SearchSuggestionTextField(
                                    placeholder: "例如：新主楼 A205 (选填)",
                                    text: $mutationClassroom,
                                    suggestions: classroomSuggestions,
                                    icon: "mappin.and.ellipse"
                                )
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 7)
                            .background(
                                RoundedRectangle(cornerRadius: 8)
                                    .fill(Color(nsColor: .textBackgroundColor).opacity(0.6))
                            )
                        }
                        .padding(16)
                        .background(
                            RoundedRectangle(cornerRadius: 14)
                                .fill(Color(nsColor: .controlBackgroundColor))
                        )
                        .zIndex(50)

                    } else {
                        // 2. Full Course Basic Info Card
                        VStack(alignment: .leading, spacing: 0) {
                            HStack {
                                Text("课程名称")
                                    .font(.system(size: 14, weight: .medium))
                                    .frame(width: 72, alignment: .leading)
                                SearchSuggestionTextField(
                                    placeholder: "课程名称",
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
                                Text("任课教师")
                                    .font(.system(size: 14, weight: .medium))
                                    .frame(width: 72, alignment: .leading)
                                SearchSuggestionTextField(
                                    placeholder: "例如：张教授 (选填)",
                                    text: $defaultTeacher,
                                    suggestions: teacherSuggestions,
                                    icon: "person"
                                )
                            }
                            .padding(.horizontal, 16)
                            .padding(.vertical, 12)
                            .zIndex(10)

                            Divider().padding(.leading, 16)

                            HStack {
                                Text("课程备注")
                                    .font(.system(size: 14, weight: .medium))
                                    .frame(width: 72, alignment: .leading)
                                TextField("课程备注 (选填)", text: $notes)
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

                        // 3. Schedules Header
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

                        // 4. Schedules Cards
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
                                            ForEach(1...25, id: \.self) { w in
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
                                            ForEach(1...25, id: \.self) { w in
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
                                        maxWeeks: 25,
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
        schedules.append(ScheduleDraft(dayOfWeek: nextDay, startSection: 1, endSection: 2))
    }

    private func handleSave() {
        if editMode == .singleOccurrence {
            onApplyMutation?(
                mutationScope,
                mutationDayOfWeek,
                mutationStartSection,
                max(mutationStartSection, mutationEndSection),
                mutationClassroom.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : mutationClassroom
            )
        } else {
            saveFullCourse()
        }
    }

    private func saveFullCourse() {
        let courseSchedules: [CourseSchedule] = schedules.map { draft in
            let teacherVal = draft.teacher.trimmingCharacters(in: .whitespacesAndNewlines)
            let weeks = Array(draft.customWeeks).sorted()
            let pattern = weeks.isEmpty ? .all : WeekPattern.custom(weeks)
            let customStart = (draft.timeMode == .byCustomTime) ? draft.customStartTime : nil
            let customEnd = (draft.timeMode == .byCustomTime) ? draft.customEndTime : nil

            return CourseSchedule(
                id: draft.originalId ?? UUID(),
                courseId: course.id,
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

        let mainTeacher = defaultTeacher.trimmingCharacters(in: .whitespacesAndNewlines)
        var updatedCourse = course
        updatedCourse.name = courseName.trimmingCharacters(in: .whitespacesAndNewlines)
        updatedCourse.teacher = mainTeacher.isEmpty ? nil : mainTeacher
        updatedCourse.color = colorHex
        updatedCourse.notes = notes.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : notes
        updatedCourse.schedules = courseSchedules
        onSaveCourse(updatedCourse)
    }
}
