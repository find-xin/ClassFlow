import SwiftUI
import WidgetKit
import ClassFlowCore

/// Unified WakeUp-style ultra-thin frosted glass background with safe contrast.
public struct WidgetFrostedBackground: View {
    @Environment(\.colorScheme) var colorScheme

    public init() {}

    public var body: some View {
        ZStack {
            Rectangle()
                .fill(.ultraThinMaterial)

            if colorScheme == .dark {
                LinearGradient(
                    colors: [
                        Color(hex: "#222226").opacity(0.85),
                        Color(hex: "#161618").opacity(0.92)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            } else {
                LinearGradient(
                    colors: [
                        Color.white.opacity(0.88),
                        Color.white.opacity(0.72)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            }
        }
    }
}

/// Widget view for Small family: Next upcoming class with countdown status.
public struct NextClassWidgetView: View {
    public let payload: WidgetTimelinePayload
    @Environment(\.colorScheme) var colorScheme

    public init(payload: WidgetTimelinePayload) {
        self.payload = payload
    }

    private var dayOfWeekName: String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "EEE"
        return df.string(from: Date())
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // 1. Top Header Row: Academic Week & Status Capsule
            HStack(alignment: .center) {
                Text("第\(payload.currentWeek)周 · \(dayOfWeekName)")
                    .font(.system(size: 11, weight: .semibold, design: .rounded))
                    .foregroundStyle(.secondary)

                Spacer()

                if let next = payload.nextClass {
                    if next.isOngoing {
                        HStack(spacing: 3.5) {
                            Circle()
                                .fill(Color(hex: "#34C759", defaultColor: .green))
                                .frame(width: 5.5, height: 5.5)
                            Text("上课中")
                                .font(.system(size: 9.5, weight: .bold))
                                .foregroundStyle(Color(hex: "#248A3D", defaultColor: .green))
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(Color(hex: "#34C759", defaultColor: .green).opacity(0.18)))
                    } else if let minutes = next.minutesUntilStart, minutes > 0 {
                        HStack(spacing: 3) {
                            Image(systemName: "timer")
                                .font(.system(size: 8.5, weight: .bold))
                            Text("\(minutes)分钟后")
                                .font(.system(size: 9.5, weight: .bold, design: .rounded))
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(Color(hex: "#FF9500", defaultColor: .orange).opacity(0.18)))
                        .foregroundStyle(Color(hex: "#D97706", defaultColor: .orange))
                    } else {
                        Text("下节课")
                            .font(.system(size: 9.5, weight: .semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2.5)
                            .background(Capsule().fill(Color.primary.opacity(0.08)))
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Text("已结课")
                        .font(.system(size: 9.5, weight: .medium))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(Color.primary.opacity(0.06)))
                        .foregroundStyle(.secondary)
                }
            }
            .padding(.bottom, 6)

            Spacer(minLength: 2)

            // 2. Course Body or Empty State
            if let next = payload.nextClass {
                VStack(alignment: .leading, spacing: 5) {
                    // Course Title with vertical accent color strip
                    HStack(alignment: .top, spacing: 6) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(Color(hex: next.colorHex, defaultColor: Color(hex: "#FF5A5F")))
                            .frame(width: 3.5, height: 18)
                            .padding(.top, 1)

                        Text(next.title)
                            .font(.system(size: 15.5, weight: .bold))
                            .foregroundStyle(.primary)
                            .lineLimit(2)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: false, vertical: true)
                    }

                    // Location & Teacher Pill Chips
                    HStack(spacing: 5) {
                        if let room = next.location, !room.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "mappin.and.ellipse")
                                    .font(.system(size: 8.5))
                                Text(room)
                                    .font(.system(size: 10, weight: .medium))
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 5.5)
                            .padding(.vertical, 2.5)
                            .background(
                                RoundedRectangle(cornerRadius: 5, style: .continuous)
                                    .fill(Color.primary.opacity(0.06))
                            )
                            .foregroundStyle(.secondary)
                        }

                        if let teacher = next.teacher, !teacher.isEmpty {
                            HStack(spacing: 3) {
                                Image(systemName: "person.fill")
                                    .font(.system(size: 8))
                                Text(teacher)
                                    .font(.system(size: 10, weight: .medium))
                                    .lineLimit(1)
                            }
                            .padding(.horizontal, 5.5)
                            .padding(.vertical, 2.5)
                            .background(
                                RoundedRectangle(cornerRadius: 5, style: .continuous)
                                    .fill(Color.primary.opacity(0.06))
                            )
                            .foregroundStyle(.secondary)
                        }
                    }
                }

                Spacer(minLength: 4)

                // 3. Footer: Section & Precise Time Span
                HStack(alignment: .firstTextBaseline) {
                    Text("第\(next.startSection)-\(next.endSection)节")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(.secondary)

                    Spacer()

                    Text("\(next.startTime) - \(next.endTime)")
                        .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                        .foregroundStyle(.primary.opacity(0.9))
                }
                .padding(.top, 4)
                .overlay(alignment: .top) {
                    Divider().opacity(0.4)
                }
            } else {
                // WakeUp signature Empty State
                VStack(alignment: .leading, spacing: 4) {
                    Image(systemName: "cup.and.saucer.fill")
                        .font(.system(size: 22))
                        .foregroundStyle(Color(hex: "#FF5A5F"))
                        .padding(.bottom, 2)

                    Text("今日课程已结束")
                        .font(.system(size: 13.5, weight: .bold))
                        .foregroundStyle(.primary)

                    Text("享受美好的空闲时光吧 ✨")
                        .font(.system(size: 10.5))
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Spacer(minLength: 2)
            }
        }
        .padding(13)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .containerBackground(for: .widget) {
            WidgetFrostedBackground()
        }
        .widgetURL(payload.nextClass != nil ? URL(string: "classflow://occurrence/\(payload.nextClass!.id)") : URL(string: "classflow://today"))
    }
}

/// Widget view for Medium family: Today's schedule list (WakeUp golden ratio layout).
public struct TodayClassesWidgetView: View {
    public let payload: WidgetTimelinePayload
    @Environment(\.colorScheme) var colorScheme

    public init(payload: WidgetTimelinePayload) {
        self.payload = payload
    }

    private var dayNumber: String {
        let df = DateFormatter()
        df.dateFormat = "d"
        return df.string(from: Date())
    }

    private var monthName: String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "M月"
        return df.string(from: Date())
    }

    private var weekdayFullName: String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "EEEE"
        return df.string(from: Date())
    }

    public var body: some View {
        HStack(spacing: 0) {
            // 1. Left Column (WakeUp Golden Ratio Date Summary, ~86pt)
            VStack(alignment: .leading, spacing: 0) {
                // Academic Week Pill
                Text("第\(payload.currentWeek)周")
                    .font(.system(size: 9.5, weight: .bold, design: .rounded))
                    .foregroundStyle(Color(hex: "#FF5A5F"))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2.5)
                    .background(
                        Capsule()
                            .fill(Color(hex: "#FF5A5F").opacity(0.12))
                    )

                Spacer()

                // Large Day Number
                Text(dayNumber)
                    .font(.system(size: 34, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)
                    .lineLimit(1)

                // Weekday & Month
                HStack(spacing: 3) {
                    Text(weekdayFullName)
                        .font(.system(size: 11.5, weight: .bold))
                        .foregroundStyle(.primary.opacity(0.85))
                    Text(monthName)
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundStyle(.secondary)
                }

                Spacer()

                // Today Course Counter Badge
                HStack(spacing: 3) {
                    Image(systemName: "book.closed.fill")
                        .font(.system(size: 8))
                    Text("今日\(payload.todayClasses.count)节")
                        .font(.system(size: 9.5, weight: .bold))
                }
                .foregroundStyle(.secondary)
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(
                    Capsule()
                        .fill(Color.primary.opacity(0.06))
                )
            }
            .frame(width: 86, alignment: .leading)

            // 2. Subtle Divider
            Rectangle()
                .fill(Color.primary.opacity(0.08))
                .frame(width: 1)
                .padding(.vertical, 4)
                .padding(.trailing, 10)

            // 3. Right Column: Course Cards Flow
            VStack(alignment: .leading, spacing: 5) {
                let dfTime = DateFormatter()
                let _ = { dfTime.dateFormat = "HH:mm" }()
                let nowTimeStr = dfTime.string(from: Date())
                let upcomingCourses = payload.todayClasses.filter { $0.endTime >= nowTimeStr || $0.isOngoing }

                if payload.todayClasses.isEmpty {
                    Spacer()
                    VStack(spacing: 5) {
                        Image(systemName: "sun.max.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(Color(hex: "#FF9500", defaultColor: .orange))
                        Text("今日无安排")
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(.primary.opacity(0.8))
                        Text("去图书馆或尽情放松吧 ☕")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    Spacer()
                } else if upcomingCourses.isEmpty {
                    Spacer()
                    VStack(spacing: 5) {
                        Image(systemName: "checkmark.seal.fill")
                            .font(.system(size: 22))
                            .foregroundStyle(Color(hex: "#34C759", defaultColor: .green))
                        Text("今日安排已结束")
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(.primary.opacity(0.8))
                        Text("早点休息，明天加油 ✨")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                    .frame(maxWidth: .infinity)
                    Spacer()
                } else {
                    let displayCourses = Array(upcomingCourses.prefix(3))
                    ForEach(displayCourses) { item in
                        Link(destination: URL(string: "classflow://occurrence/\(item.id)")!) {
                            HStack(spacing: 7) {
                                // Left colored bar
                                RoundedRectangle(cornerRadius: 2)
                                    .fill(Color(hex: item.colorHex))
                                    .frame(width: 3.5, height: 28)

                                // Course title & time/room
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack(alignment: .center, spacing: 5) {
                                        Text(item.title)
                                            .font(.system(size: 11.5, weight: .bold))
                                            .foregroundStyle(.primary)
                                            .lineLimit(1)

                                        if item.isOngoing {
                                            HStack(spacing: 2.5) {
                                                Circle()
                                                    .fill(Color(hex: "#34C759", defaultColor: .green))
                                                    .frame(width: 4.5, height: 4.5)
                                                Text("上课中")
                                                    .font(.system(size: 8, weight: .bold))
                                                    .foregroundStyle(Color(hex: "#248A3D", defaultColor: .green))
                                            }
                                            .padding(.horizontal, 4.5)
                                            .padding(.vertical, 1.5)
                                            .background(Capsule().fill(Color(hex: "#34C759", defaultColor: .green).opacity(0.18)))
                                        }
                                    }

                                    HStack(spacing: 4) {
                                        Text("\(item.startTime) - \(item.endTime)")
                                            .font(.system(size: 9, weight: .medium, design: .monospaced))
                                            .foregroundStyle(.secondary)

                                        Text("·")
                                            .font(.system(size: 8))
                                            .foregroundStyle(.secondary.opacity(0.6))

                                        Text("第\(item.startSection)-\(item.endSection)节")
                                            .font(.system(size: 9))
                                            .foregroundStyle(.secondary)

                                        if let room = item.location, !room.isEmpty {
                                            Text("·")
                                                .font(.system(size: 8))
                                                .foregroundStyle(.secondary.opacity(0.6))

                                            Text(room)
                                                .font(.system(size: 9, weight: .medium))
                                                .foregroundStyle(.secondary)
                                                .lineLimit(1)
                                        }
                                    }
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3.5)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(item.isOngoing ? Color(hex: item.colorHex).opacity(0.10) : Color.primary.opacity(0.04))
                            )
                        }
                        .buttonStyle(.plain)
                    }

                    if payload.todayClasses.count > 3 {
                        Text("+ 还有 \(payload.todayClasses.count - 3) 节课")
                            .font(.system(size: 9, weight: .medium))
                            .foregroundStyle(.secondary)
                            .padding(.leading, 8)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(12)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .containerBackground(for: .widget) {
            WidgetFrostedBackground()
        }
    }
}

/// Widget view for Large & Extra Large family: Week overview mini timetable.
public struct WeekOverviewWidgetView: View {
    public let payload: WidgetTimelinePayload
    public var explicitFamily: WidgetFamily? = nil
    @Environment(\.widgetFamily) var environmentFamily

    public init(payload: WidgetTimelinePayload, explicitFamily: WidgetFamily? = nil) {
        self.payload = payload
        self.explicitFamily = explicitFamily
    }

    private var activeFamily: WidgetFamily {
        explicitFamily ?? environmentFamily
    }

    private var todayDateString: String {
        let df = DateFormatter()
        df.dateFormat = "yyyy-MM-dd"
        return df.string(from: Date())
    }

    private var todayShortDateText: String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = "M月d日 EEE"
        return df.string(from: Date())
    }

    private func extractDayNumber(from dateStr: String) -> String {
        let parts = dateStr.split(separator: "-")
        if parts.count == 3, let d = Int(parts[2]) {
            return "\(d)"
        }
        return ""
    }

    public var body: some View {
        Group {
            if activeFamily == .systemExtraLarge {
                extraLargeView
            } else {
                largeView
            }
        }
        .containerBackground(for: .widget) {
            WidgetFrostedBackground()
        }
    }

    // MARK: - Large View (7-Day Compact Mini Timetable Grid)
    private var largeView: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header Row
            HStack {
                HStack(spacing: 5) {
                    Text(payload.semesterName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(.primary)

                    Text("第\(payload.currentWeek)周")
                        .font(.system(size: 10, weight: .bold, design: .rounded))
                        .foregroundStyle(Color(hex: "#FF5A5F"))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(Color(hex: "#FF5A5F").opacity(0.12)))
                }

                Spacer()

                Text(todayShortDateText)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            Divider().opacity(0.5)

            // 7 Columns Grid
            HStack(alignment: .top, spacing: 4) {
                ForEach(payload.weekSchedule) { day in
                    let isToday = (!day.dateString.isEmpty && day.dateString == todayDateString)
                    let dayNum = extractDayNumber(from: day.dateString)

                    VStack(spacing: 4) {
                        // Day Header
                        VStack(spacing: 2) {
                            Text(day.dayName.replacingOccurrences(of: "周", with: ""))
                                .font(.system(size: 9.5, weight: isToday ? .bold : .medium))
                                .foregroundStyle(isToday ? .primary : .secondary)

                            if isToday {
                                Text(dayNum.isEmpty ? "今" : dayNum)
                                    .font(.system(size: 9, weight: .bold, design: .rounded))
                                    .foregroundStyle(.white)
                                    .frame(width: 17, height: 17)
                                    .background(Circle().fill(Color(hex: "#FF5A5F")))
                            } else {
                                Text(dayNum)
                                    .font(.system(size: 9, weight: .regular, design: .rounded))
                                    .foregroundStyle(.secondary.opacity(0.8))
                                    .frame(height: 17)
                            }
                        }

                        // Courses in this Day
                        VStack(spacing: 3) {
                            if day.courses.isEmpty {
                                RoundedRectangle(cornerRadius: 3)
                                    .strokeBorder(Color.primary.opacity(0.06), style: StrokeStyle(lineWidth: 1, dash: [3]))
                                    .frame(height: 48)
                            } else {
                                ForEach(day.courses.prefix(5)) { course in
                                    Link(destination: URL(string: "classflow://occurrence/\(course.id)")!) {
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(course.title)
                                                .font(.system(size: 8, weight: .bold))
                                                .foregroundStyle(.white)
                                                .lineLimit(2)
                                                .minimumScaleFactor(0.8)

                                            if let room = course.location, !room.isEmpty {
                                                Text(room)
                                                    .font(.system(size: 7, weight: .medium))
                                                    .foregroundStyle(.white.opacity(0.85))
                                                    .lineLimit(1)
                                            }
                                        }
                                        .padding(.horizontal, 3)
                                        .padding(.vertical, 3)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .frame(minHeight: max(22, CGFloat(course.endSection - course.startSection + 1) * 11))
                                        .background(Color(hex: course.colorHex))
                                        .clipShape(RoundedRectangle(cornerRadius: 4, style: .continuous))
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }

            Spacer(minLength: 0)
        }
        .padding(12)
    }

    // MARK: - Extra Large View (Full 1~12 Timetable Grid for macOS Desktop)
    private var extraLargeView: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Header
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "calendar")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(Color(hex: "#FF5A5F"))

                    Text(payload.semesterName)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundStyle(.primary)

                    Text("第\(payload.currentWeek)周")
                        .font(.system(size: 11, weight: .bold, design: .rounded))
                        .foregroundStyle(.white)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2.5)
                        .background(Capsule().fill(Color(hex: "#FF5A5F")))
                }

                Spacer()

                Text(todayShortDateText)
                    .font(.system(size: 11.5, weight: .medium))
                    .foregroundStyle(.secondary)
            }

            Divider().opacity(0.5)

            // Full Timetable Grid with Section Axis
            HStack(alignment: .top, spacing: 5) {
                // Section Axis (1..10)
                VStack(spacing: 2) {
                    Text("节次")
                        .font(.system(size: 8, weight: .bold))
                        .foregroundStyle(.secondary)
                        .frame(height: 18)

                    ForEach(1...10, id: \.self) { sec in
                        Text("\(sec)")
                            .font(.system(size: 9, weight: .semibold, design: .rounded))
                            .foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity)
                            .frame(height: 18)
                            .background(
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(Color.primary.opacity(0.04))
                            )
                    }
                }
                .frame(width: 26)

                // 7 Days
                ForEach(payload.weekSchedule) { day in
                    let isToday = (!day.dateString.isEmpty && day.dateString == todayDateString)
                    let dayNum = extractDayNumber(from: day.dateString)

                    VStack(spacing: 2) {
                        // Header
                        HStack(spacing: 2) {
                            Text(day.dayName)
                                .font(.system(size: 10, weight: isToday ? .bold : .medium))
                                .foregroundStyle(isToday ? Color(hex: "#FF5A5F") : .secondary)

                            if !dayNum.isEmpty {
                                Text(dayNum)
                                    .font(.system(size: 9.5, weight: isToday ? .bold : .regular, design: .rounded))
                                    .foregroundStyle(isToday ? Color(hex: "#FF5A5F") : .secondary.opacity(0.8))
                            }
                        }
                        .frame(height: 18)

                        // Course Slots
                        VStack(spacing: 3) {
                            if day.courses.isEmpty {
                                RoundedRectangle(cornerRadius: 4)
                                    .strokeBorder(Color.primary.opacity(0.06), style: StrokeStyle(lineWidth: 1, dash: [4]))
                                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                            } else {
                                ForEach(day.courses) { course in
                                    Link(destination: URL(string: "classflow://occurrence/\(course.id)")!) {
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text(course.title)
                                                .font(.system(size: 9, weight: .bold))
                                                .foregroundStyle(.white)
                                                .lineLimit(1)

                                            if let room = course.location, !room.isEmpty {
                                                Text(room)
                                                    .font(.system(size: 7.5, weight: .medium))
                                                    .foregroundStyle(.white.opacity(0.9))
                                                    .lineLimit(1)
                                            }

                                            Text("\(course.startTime)-\(course.endTime)")
                                                .font(.system(size: 7, weight: .regular, design: .monospaced))
                                                .foregroundStyle(.white.opacity(0.8))
                                                .lineLimit(1)
                                        }
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 3)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .frame(minHeight: max(26, CGFloat(course.endSection - course.startSection + 1) * 16))
                                        .background(Color(hex: course.colorHex))
                                        .clipShape(RoundedRectangle(cornerRadius: 5, style: .continuous))
                                    }
                                    .buttonStyle(.plain)
                                }
                                Spacer(minLength: 0)
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(3)
                    .background(
                        RoundedRectangle(cornerRadius: 6, style: .continuous)
                            .fill(isToday ? Color(hex: "#FF5A5F").opacity(0.06) : Color.clear)
                    )
                }
            }

            Spacer(minLength: 0)
        }
        .padding(14)
    }
}
