import SwiftUI
import ClassFlowCore

/// Editing sheet with support for temporal EditingScope (This only, This & future, All).
public struct CourseEditSheet: View {
    public let item: TimelineItem
    public let availableSections: [SectionTime]
    public let onSave: (EditingScope, String, String?, String, Int, Int, Int, String?, String?) -> Void
    public let onDismiss: () -> Void

    @State private var scope: EditingScope = .thisOnly
    @State private var courseName: String
    @State private var teacher: String
    @State private var colorHex: String
    @State private var dayOfWeek: Int
    @State private var startSection: Int
    @State private var endSection: Int
    @State private var classroom: String
    @State private var notes: String

    private let colors = [
        "#4F46E5", "#0D9488", "#0284C7", "#E11D48",
        "#D97706", "#059669", "#7C3AED", "#C026D3"
    ]

    public init(
        item: TimelineItem,
        availableSections: [SectionTime],
        onSave: @escaping (EditingScope, String, String?, String, Int, Int, Int, String?, String?) -> Void,
        onDismiss: @escaping () -> Void
    ) {
        self.item = item
        self.availableSections = availableSections
        self.onSave = onSave
        self.onDismiss = onDismiss

        _courseName = State(initialValue: item.title)
        _teacher = State(initialValue: item.teacher ?? "")
        _colorHex = State(initialValue: item.color)
        _dayOfWeek = State(initialValue: item.dayOfWeek)
        _startSection = State(initialValue: item.startSection)
        _endSection = State(initialValue: item.endSection)
        _classroom = State(initialValue: item.location ?? "")
        _notes = State(initialValue: item.notes ?? "")
    }

    public var body: some View {
        VStack(spacing: 0) {
            // Header
            HStack {
                Text("编辑课程安排")
                    .font(.headline)
                Spacer()
                Button("取消", action: onDismiss)
                    .keyboardShortcut(.cancelAction)
            }
            .padding()
            Divider()

            Form {
                // 1. Editing Scope Selection
                Section("修改生效范围") {
                    Picker("生效范围", selection: $scope) {
                        Text("仅修改本次 (\(item.date))").tag(EditingScope.thisOnly)
                        Text("修改本次及后续周次").tag(EditingScope.thisAndFuture)
                        Text("修改全部安排 (整学期)").tag(EditingScope.all)
                    }
                    .pickerStyle(.radioGroup)
                }

                // 2. Basic Information
                Section("课程基本信息") {
                    TextField("课程名称", text: $courseName)
                    TextField("任课教师", text: $teacher)
                    TextField("备注信息", text: $notes)

                    // Color Picker Palette
                    HStack(spacing: 8) {
                        Text("课程颜色")
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

                // 3. Schedule Time & Location
                Section("上课时间与教室") {
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

                    TextField("上课教室", text: $classroom)
                }
            }
            .formStyle(.grouped)
            .padding()

            Divider()

            // Footer
            HStack {
                Spacer()
                Button("保存修改") {
                    onSave(
                        scope,
                        courseName,
                        teacher.isEmpty ? nil : teacher,
                        colorHex,
                        dayOfWeek,
                        startSection,
                        endSection,
                        classroom.isEmpty ? nil : classroom,
                        notes.isEmpty ? nil : notes
                    )
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut(.defaultAction)
            }
            .padding()
        }
        .frame(width: 460, height: 500)
    }
}
