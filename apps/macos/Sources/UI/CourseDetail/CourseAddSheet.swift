import SwiftUI
import ClassFlowCore

/// Sheet allowing creation of a new course with initial schedule rules.
public struct CourseAddSheet: View {
    public let semesterId: UUID
    public let availableSections: [SectionTime]
    public let onSave: (Course) -> Void
    public let onDismiss: () -> Void

    @State private var courseName: String = ""
    @State private var teacher: String = ""
    @State private var colorHex: String = "#4F46E5"
    @State private var dayOfWeek: Int = 1
    @State private var startSection: Int = 1
    @State private var endSection: Int = 2
    @State private var classroom: String = ""
    @State private var weekPatternType: WeekPattern.PatternType = .all
    @State private var notes: String = ""

    private let colors = [
        "#4F46E5", "#0D9488", "#0284C7", "#E11D48",
        "#D97706", "#059669", "#7C3AED", "#C026D3"
    ]

    public init(
        semesterId: UUID,
        availableSections: [SectionTime],
        onSave: @escaping (Course) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.semesterId = semesterId
        self.availableSections = availableSections
        self.onSave = onSave
        self.onDismiss = onDismiss
    }

    public var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("添加新课程")
                    .font(.headline)
                Spacer()
                Button("取消", action: onDismiss)
                    .keyboardShortcut(.cancelAction)
            }
            .padding()
            Divider()

            Form {
                Section("课程基本信息") {
                    TextField("课程名称 (必填)", text: $courseName)
                    TextField("任课教师 (选填)", text: $teacher)
                    TextField("课程备注 (选填)", text: $notes)

                    HStack(spacing: 8) {
                        Text("卡片颜色")
                        Spacer()
                        ForEach(colors, id: \.self) { hex in
                            Circle()
                                .fill(Color(hex: hex) ?? .gray)
                                .frame(width: 20, height: 20)
                                .overlay(
                                    Circle()
                                        .strokeBorder(Color.white, lineWidth: colorHex == hex ? 2 : 0)
                                )
                                .onTapGesture {
                                    colorHex = hex
                                }
                        }
                    }
                }

                Section("上课规律与节次") {
                    Picker("星期", selection: $dayOfWeek) {
                        Text("周一").tag(1)
                        Text("周二").tag(2)
                        Text("周三").tag(3)
                        Text("周四").tag(4)
                        Text("周五").tag(5)
                        Text("周六").tag(6)
                        Text("周日").tag(7)
                    }

                    HStack {
                        Picker("开始节次", selection: $startSection) {
                            ForEach(availableSections) { s in
                                Text("第 \(s.section) 节 (\(s.startTime))").tag(s.section)
                            }
                        }
                        Picker("结束节次", selection: $endSection) {
                            ForEach(availableSections.filter { $0.section >= startSection }) { s in
                                Text("第 \(s.section) 节 (\(s.endTime))").tag(s.section)
                            }
                        }
                    }

                    TextField("上课教室 (例如: 东九楼 D102)", text: $classroom)

                    Picker("周次循环", selection: $weekPatternType) {
                        Text("每周").tag(WeekPattern.PatternType.all)
                        Text("单周").tag(WeekPattern.PatternType.odd)
                        Text("双周").tag(WeekPattern.PatternType.even)
                    }
                }
            }
            .formStyle(.grouped)
            .padding()

            Divider()

            HStack {
                Spacer()
                Button("创建课程") {
                    let courseId = UUID()
                    let schedule = CourseSchedule(
                        id: UUID(),
                        courseId: courseId,
                        dayOfWeek: dayOfWeek,
                        startSection: startSection,
                        endSection: max(startSection, endSection),
                        classroom: classroom.isEmpty ? nil : classroom,
                        weekPattern: WeekPattern(type: weekPatternType)
                    )
                    let newCourse = Course(
                        id: courseId,
                        semesterId: semesterId,
                        name: courseName.trimmingCharacters(in: .whitespacesAndNewlines),
                        teacher: teacher.isEmpty ? nil : teacher,
                        color: colorHex,
                        notes: notes.isEmpty ? nil : notes,
                        schedules: [schedule]
                    )
                    onSave(newCourse)
                }
                .disabled(courseName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
            }
            .padding()
        }
        .frame(width: 460, height: 480)
    }
}
