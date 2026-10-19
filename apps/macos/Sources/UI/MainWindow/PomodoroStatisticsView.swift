import SwiftUI
import ClassFlowCore

/// Comprehensive Pomodoro ToDo (番茄ToDo) style Heatmap & Analytics View.
/// Features a full-semester GitHub-style contribution heatmap, multi-period trend analytics
/// (7-day, weekly, monthly combined Bar + Line charts), circular Donut chart for subject distribution,
/// and core streak metrics.
public struct PomodoroStatisticsView: View {
    @Bindable public var appState: AppState

    // 1. Trend Period Selection (近7天, 按周趋势, 按月趋势)
    public enum TrendPeriod: String, CaseIterable, Identifiable {
        case past7Days = "近7天"
        case weekly = "按周趋势"
        case monthly = "按月趋势"
        public var id: String { rawValue }
    }

    public enum DonutTimeRange: String, CaseIterable, Identifiable {
        case week = "近7天"
        case month = "近30天"
        case year = "按年查看"
        case all = "全部"
        public var id: String { rawValue }
    }

    @State private var selectedTrendPeriod: TrendPeriod = .past7Days
    @State private var selectedDonutRange: DonutTimeRange = .month
    @State private var hoveredDayInfo: (dateStr: String, minutes: Int, count: Int)? = nil
    @State private var hoveredDonutIndex: Int? = nil

    public init(appState: AppState) {
        self.appState = appState
    }

    // MARK: - Thread-Safe Cached DateFormatters
    private enum DateFormatters {
        static let yyyyMMdd: DateFormatter = {
            let df = DateFormatter()
            df.dateFormat = "yyyy-MM-dd"
            df.calendar = Calendar.current
            return df
        }()
        static let monthOnly: DateFormatter = {
            let df = DateFormatter()
            df.dateFormat = "M月"
            df.calendar = Calendar.current
            return df
        }()
        static let weekdayShort: DateFormatter = {
            let df = DateFormatter()
            df.locale = Locale(identifier: "zh_CN")
            df.dateFormat = "EEE"
            df.calendar = Calendar.current
            return df
        }()
    }

    private var calendar: Calendar {
        Calendar.current
    }

    private var todayDateString: String {
        DateFormatters.yyyyMMdd.string(from: Date())
    }

    // MARK: - Pre-Calculated Analytics Snapshot for 120Hz Fluid Scrolling

    public struct HeatmapDayItem: Identifiable {
        public var id: String { dateKey }
        public let dateKey: String
        public let minutes: Int
        public let count: Int
        public let isFuture: Bool
        public let isToday: Bool
    }

    public struct HeatmapWeekItem: Identifiable {
        public var id: Int { weekOffset }
        public let weekOffset: Int
        public let days: [HeatmapDayItem]
    }

    public struct AnalyticsSnapshot {
        public let dailyMinutes: [String: Int]
        public let dailyCounts: [String: Int]
        public let streakDays: Int
        public let totalActiveDays: Int
        public let totalMinutes: Int
        public let totalPomodoros: Int
        public let dailyAverageMinutes: Double
        public let completedTaskCount: Int
        public let totalTaskCount: Int
        public let completedPercentage: Int
        public let heatmapWeeks: [HeatmapWeekItem]
    }

    @State private var cachedAnalytics: AnalyticsSnapshot? = nil

    private var currentAnalytics: AnalyticsSnapshot {
        if let existing = cachedAnalytics {
            return existing
        }
        return computeAnalytics()
    }

    private func computeAnalytics() -> AnalyticsSnapshot {
        let records = appState.pomodoroRecords
        let tasks = appState.currentSemesterPomodoroTasks
        let df = DateFormatters.yyyyMMdd
        let now = Date()
        let todayKey = df.string(from: now)

        var minutesMap: [String: Int] = [:]
        var countsMap: [String: Int] = [:]
        minutesMap.reserveCapacity(records.count)
        countsMap.reserveCapacity(records.count)

        for rec in records {
            let key = df.string(from: rec.completedAt)
            minutesMap[key, default: 0] += rec.durationMinutes
            countsMap[key, default: 0] += 1
        }

        // Streak
        var streak = 0
        var checkDate = now
        let todayMinutes = minutesMap[todayKey] ?? 0
        if todayMinutes > 0 {
            streak += 1
            checkDate = calendar.date(byAdding: .day, value: -1, to: checkDate) ?? checkDate
        } else {
            let yesterday = calendar.date(byAdding: .day, value: -1, to: checkDate) ?? checkDate
            let yKey = df.string(from: yesterday)
            if (minutesMap[yKey] ?? 0) > 0 {
                streak += 1
                checkDate = calendar.date(byAdding: .day, value: -2, to: checkDate) ?? checkDate
            }
        }
        if streak > 0 {
            while true {
                let key = df.string(from: checkDate)
                if (minutesMap[key] ?? 0) > 0 {
                    streak += 1
                    checkDate = calendar.date(byAdding: .day, value: -1, to: checkDate) ?? checkDate
                } else {
                    break
                }
            }
        }

        let activeDays = minutesMap.filter { $0.value > 0 }.count
        let totMinutes = records.reduce(0) { $0 + $1.durationMinutes }
        let totPomodoros = records.count
        let avgMins = activeDays > 0 ? Double(totMinutes) / Double(activeDays) : 0

        let completed = tasks.filter { $0.isCompleted }.count
        let totTasks = max(1, tasks.count)
        let pct = Int(Double(completed) / Double(totTasks) * 100.0)

        // Precompute 24 weeks of Heatmap cells
        let totalWeeks = 24
        let currentDayOfWeek = calendar.component(.weekday, from: now)
        let mondayOffset = (currentDayOfWeek == 1 ? -6 : 2 - currentDayOfWeek)
        let thisMonday = calendar.date(byAdding: .day, value: mondayOffset, to: now) ?? now
        let startMonday = calendar.date(byAdding: .weekOfYear, value: -(totalWeeks - 1), to: thisMonday) ?? thisMonday

        var weeks: [HeatmapWeekItem] = []
        weeks.reserveCapacity(totalWeeks)

        for weekOffset in 0..<totalWeeks {
            guard let weekStartDate = calendar.date(byAdding: .weekOfYear, value: weekOffset, to: startMonday) else { continue }
            var days: [HeatmapDayItem] = []
            days.reserveCapacity(7)
            for dayOffset in 0..<7 {
                guard let date = calendar.date(byAdding: .day, value: dayOffset, to: weekStartDate) else { continue }
                let key = df.string(from: date)
                let mins = minutesMap[key] ?? 0
                let count = countsMap[key] ?? 0
                let isFuture = date > now
                let isToday = (key == todayKey)
                days.append(HeatmapDayItem(
                    dateKey: key,
                    minutes: mins,
                    count: count,
                    isFuture: isFuture,
                    isToday: isToday
                ))
            }
            weeks.append(HeatmapWeekItem(weekOffset: weekOffset, days: days))
        }

        return AnalyticsSnapshot(
            dailyMinutes: minutesMap,
            dailyCounts: countsMap,
            streakDays: streak,
            totalActiveDays: activeDays,
            totalMinutes: totMinutes,
            totalPomodoros: totPomodoros,
            dailyAverageMinutes: avgMins,
            completedTaskCount: completed,
            totalTaskCount: totTasks,
            completedPercentage: pct,
            heatmapWeeks: weeks
        )
    }

    // MARK: - Body

    public var body: some View {
        let stats = currentAnalytics

        return VStack(alignment: .leading, spacing: 20) {
            // 1. Metric Overview Cards
            HStack(spacing: 12) {
                metricBox(
                    title: "连续专注",
                    value: "\(stats.streakDays)",
                    unit: "天",
                    subtitle: stats.streakDays > 0 ? "心流保持中 🔥" : "今日待打卡",
                    color: Color(hex: "#FF5A5F")
                )
                metricBox(
                    title: "累计专注天数",
                    value: "\(stats.totalActiveDays)",
                    unit: "天",
                    subtitle: "日均 \(String(format: "%.0f", stats.dailyAverageMinutes)) 分钟",
                    color: Color(hex: "#E8A87C")
                )
                metricBox(
                    title: "累计专注时长",
                    value: String(format: "%.1f", Double(stats.totalMinutes) / 60.0),
                    unit: "小时",
                    subtitle: "共 \(stats.totalPomodoros) 个番茄钟",
                    color: Color(hex: "#5B7FB5")
                )
                metricBox(
                    title: "任务完成率",
                    value: "\(stats.completedPercentage)%",
                    unit: "",
                    subtitle: "已完成 \(stats.completedTaskCount)/\(stats.totalTaskCount) 项",
                    color: Color(hex: "#56A3A6")
                )
            }

            // 2. Full-Width Heatmap Card (专注热力图谱)
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    HStack(spacing: 6) {
                        Text("🧱")
                        Text("专注热力图谱")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.primary)
                    }

                    Spacer()

                    // Legend: 少 -> 多
                    HStack(spacing: 5) {
                        Text("少")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)

                        heatCell(level: 0)
                        heatCell(level: 1)
                        heatCell(level: 2)
                        heatCell(level: 3)
                        heatCell(level: 4)

                        Text("多")
                            .font(.system(size: 10))
                            .foregroundColor(.secondary)
                    }
                }

                // Balanced Heatmap Grid spanning full available container width
                heatmapGrid

                // Hovered Day Tooltip Bar
                if let info = hoveredDayInfo {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(heatColor(for: info.minutes))
                            .frame(width: 8, height: 8)
                        Text(info.dateStr)
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.primary)
                        Text("•")
                            .foregroundColor(.secondary)
                        Text("专注 \(info.minutes) 分钟（\(info.count) 次番茄专注）")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(
                        RoundedRectangle(cornerRadius: 8, style: .continuous)
                            .fill(Color.primary.opacity(0.04))
                    )
                }
            }
            .padding(18)
            .background(
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                    .overlay(
                        RoundedRectangle(cornerRadius: 18, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                    )
            )

            // 3. Multi-Period Trend (Bar + Line Chart) & Subject Distribution (Donut Chart)
            HStack(alignment: .top, spacing: 16) {
                // Left Column: Combined Bar + Line Chart
                focusTrendChartCard
                    .frame(maxWidth: .infinity)

                // Right Column: Circular Donut Chart
                subjectDonutChartCard
                    .frame(maxWidth: .infinity)
            }
        }
        .onAppear {
            if cachedAnalytics == nil {
                cachedAnalytics = computeAnalytics()
            }
        }
        .onChange(of: appState.pomodoroRecords) { _, _ in
            cachedAnalytics = computeAnalytics()
        }
        .onChange(of: appState.pomodoroTasks) { _, _ in
            cachedAnalytics = computeAnalytics()
        }
    }

    // MARK: - Metric Box Component

    private func metricBox(title: String, value: String, unit: String, subtitle: String, color: Color) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.system(size: 11.5, weight: .medium))
                .foregroundColor(.secondary)

            HStack(alignment: .firstTextBaseline, spacing: 3) {
                Text(value)
                    .font(.system(size: 26, weight: .bold, design: .rounded))
                    .foregroundColor(color)
                if !unit.isEmpty {
                    Text(unit)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(color.opacity(0.8))
                }
            }

            Text(subtitle)
                .font(.system(size: 10.5))
                .foregroundColor(.secondary.opacity(0.85))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5)
                )
        )
    }

    // MARK: - Heatmap Grid (Extended 24 Weeks with Month Headers)

    private var heatmapGrid: some View {
        let daysPerWeek = 7
        let weekDaysLabels = ["一", "二", "三", "四", "五", "六", "日"]
        let weeks = currentAnalytics.heatmapWeeks

        return HStack(alignment: .top, spacing: 8) {
            // Y-Axis Weekday Labels
            VStack(spacing: 5) {
                ForEach(0..<daysPerWeek, id: \.self) { dayIndex in
                    Text(weekDaysLabels[dayIndex])
                        .font(.system(size: 9.5, weight: .medium))
                        .foregroundColor(.secondary.opacity(0.75))
                        .frame(height: 14)
                }
            }
            .padding(.top, 2)

            // Grid of Columns (24 Weeks)
            ScrollView(.horizontal, showsIndicators: false) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        ForEach(weeks) { week in
                            VStack(spacing: 5) {
                                ForEach(week.days) { day in
                                    RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                                        .fill(day.isFuture ? Color.clear : heatColor(for: day.minutes))
                                        .frame(width: 15, height: 14)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                                                .strokeBorder(day.isToday ? Color(hex: "#FF5A5F") : Color.black.opacity(0.04), lineWidth: day.isToday ? 1.5 : 0.5)
                                        )
                                        .onHover { isHovered in
                                            if isHovered && !day.isFuture {
                                                hoveredDayInfo = (day.dateKey, day.minutes, day.count)
                                            }
                                        }
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
    }

    private func heatLevel(for minutes: Int) -> Int {
        if minutes == 0 { return 0 }
        if minutes <= 25 { return 1 }
        if minutes <= 50 { return 2 }
        if minutes <= 100 { return 3 }
        return 4
    }

    private func heatColor(for minutes: Int) -> Color {
        switch heatLevel(for: minutes) {
        case 0:
            return Color.primary.opacity(0.05)
        case 1:
            return Color(hex: "#FF5A5F").opacity(0.32)
        case 2:
            return Color(hex: "#FF5A5F").opacity(0.58)
        case 3:
            return Color(hex: "#FF5A5F").opacity(0.82)
        default:
            return Color(hex: "#FF5A5F")
        }
    }

    private func heatCell(level: Int) -> some View {
        RoundedRectangle(cornerRadius: 2, style: .continuous)
            .fill(heatColor(for: level == 0 ? 0 : (level == 1 ? 20 : (level == 2 ? 40 : (level == 3 ? 80 : 150)))))
            .frame(width: 10, height: 10)
    }

    // MARK: - Combined Bar + Line Trend Chart Card

    private struct TrendDataPoint: Identifiable {
        let id: String
        let label: String
        let minutes: Int
        let count: Int
        let isCurrent: Bool
    }

    private var focusTrendChartCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Header with Period Picker
            HStack {
                Text("专注趋势洞察")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                Picker("", selection: $selectedTrendPeriod) {
                    ForEach(TrendPeriod.allCases) { period in
                        Text(period.rawValue).tag(period)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 210)
            }

            let dataPoints = getTrendPoints(for: selectedTrendPeriod)
            let maxMinutes = max(60, dataPoints.map { $0.minutes }.max() ?? 60)
            let maxCount = max(4, dataPoints.map { $0.count }.max() ?? 4)

            // Combined Bar + Line Chart Canvas
            ZStack(alignment: .bottom) {
                // 1. Bars Canvas
                HStack(alignment: .bottom, spacing: selectedTrendPeriod == .past7Days ? 14 : 8) {
                    ForEach(dataPoints) { point in
                        VStack(spacing: 5) {
                            // Value top label
                            Text(point.minutes > 0 ? "\(point.minutes)m" : "")
                                .font(.system(size: 9, weight: .bold, design: .rounded))
                                .foregroundColor(point.isCurrent ? Color(hex: "#FF5A5F") : .secondary)
                                .frame(height: 12)

                            // Bar
                            GeometryReader { barProxy in
                                let barHeight = CGFloat(point.minutes) / CGFloat(maxMinutes) * (barProxy.size.height - 4)
                                VStack {
                                    Spacer(minLength: 0)
                                    RoundedRectangle(cornerRadius: 5, style: .continuous)
                                        .fill(
                                            point.isCurrent
                                                ? LinearGradient(colors: [Color(hex: "#FF5A5F"), Color(hex: "#FF8E53")], startPoint: .top, endPoint: .bottom)
                                                : LinearGradient(colors: [Color(hex: "#5B7FB5"), Color(hex: "#7A9FD3")], startPoint: .top, endPoint: .bottom)
                                        )
                                        .frame(height: max(4, barHeight))
                                }
                            }
                            .frame(height: 110)

                            // X-axis label
                            Text(point.label)
                                .font(.system(size: 10, weight: point.isCurrent ? .bold : .medium))
                                .foregroundColor(point.isCurrent ? Color(hex: "#FF5A5F") : .secondary)
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity)
                    }
                }

                // 2. Trend Line Overlay (Overlaying session count trend)
                GeometryReader { lineProxy in
                    let w = lineProxy.size.width
                    let h = lineProxy.size.height - 20
                    let count = dataPoints.count
                    if count > 1 {
                        let stepX = w / CGFloat(count)

                        Path { path in
                            for (index, point) in dataPoints.enumerated() {
                                let x = stepX * CGFloat(index) + stepX / 2
                                let ratio = CGFloat(point.count) / CGFloat(maxCount)
                                let y = h - (ratio * (h - 20))

                                if index == 0 {
                                    path.move(to: CGPoint(x: x, y: y))
                                } else {
                                    path.addLine(to: CGPoint(x: x, y: y))
                                }
                            }
                        }
                        .stroke(
                            Color(hex: "#E8A87C"),
                            style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round, dash: [4, 3])
                        )

                        // Data dots on line
                        ForEach(Array(dataPoints.enumerated()), id: \.offset) { index, point in
                            let x = stepX * CGFloat(index) + stepX / 2
                            let ratio = CGFloat(point.count) / CGFloat(maxCount)
                            let y = h - (ratio * (h - 20))

                            Circle()
                                .fill(Color(hex: "#E8A87C"))
                                .frame(width: 5, height: 5)
                                .position(x: x, y: y)
                        }
                    }
                }
                .allowsHitTesting(false)
            }
            .frame(height: 155)

            // Legend Footer
            HStack(spacing: 16) {
                HStack(spacing: 5) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(LinearGradient(colors: [Color(hex: "#5B7FB5"), Color(hex: "#7A9FD3")], startPoint: .top, endPoint: .bottom))
                        .frame(width: 10, height: 10)
                    Text("专注时长 (柱状)")
                        .font(.system(size: 10.5))
                        .foregroundColor(.secondary)
                }

                HStack(spacing: 5) {
                    Circle()
                        .stroke(Color(hex: "#E8A87C"), lineWidth: 2)
                        .frame(width: 8, height: 8)
                    Text("专注频次 (趋势折线)")
                        .font(.system(size: 10.5))
                        .foregroundColor(.secondary)
                }

                Spacer()
            }
            .padding(.top, 4)
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
        )
    }

    private func getTrendPoints(for period: TrendPeriod) -> [TrendDataPoint] {
        let dfDay = DateFormatters.yyyyMMdd
        let now = Date()
        let todayKey = todayDateString
        let data = currentAnalytics

        switch period {
        case .past7Days:
            var points: [TrendDataPoint] = []
            let dfLabel = DateFormatters.weekdayShort

            for i in (0..<7).reversed() {
                if let d = calendar.date(byAdding: .day, value: -i, to: now) {
                    let key = dfDay.string(from: d)
                    let mins = data.dailyMinutes[key] ?? 0
                    let count = data.dailyCounts[key] ?? 0
                    let label = dfLabel.string(from: d).replacingOccurrences(of: "周", with: "")
                    points.append(TrendDataPoint(
                        id: key,
                        label: label,
                        minutes: mins,
                        count: count,
                        isCurrent: (key == todayKey)
                    ))
                }
            }
            return points

        case .weekly:
            var points: [TrendDataPoint] = []
            // Past 8 Weeks
            for w in (0..<8).reversed() {
                if let weekDate = calendar.date(byAdding: .weekOfYear, value: -w, to: now) {
                    let weekNum = calendar.component(.weekOfYear, from: weekDate)
                    var totalMins = 0
                    var totalSessions = 0

                    // Sum the 7 days of this week
                    for dayOffset in 0..<7 {
                        if let d = calendar.date(byAdding: .day, value: dayOffset - 3, to: weekDate) {
                            let key = dfDay.string(from: d)
                            totalMins += data.dailyMinutes[key] ?? 0
                            totalSessions += data.dailyCounts[key] ?? 0
                        }
                    }

                    points.append(TrendDataPoint(
                        id: "w\(weekNum)",
                        label: "第\(weekNum)周",
                        minutes: totalMins,
                        count: totalSessions,
                        isCurrent: (w == 0)
                    ))
                }
            }
            return points

        case .monthly:
            var points: [TrendDataPoint] = []
            let dfMonth = DateFormatters.monthOnly

            // Past 6 Months
            for m in (0..<6).reversed() {
                if let monthDate = calendar.date(byAdding: .month, value: -m, to: now) {
                    let mLabel = dfMonth.string(from: monthDate)
                    let year = calendar.component(.year, from: monthDate)
                    let month = calendar.component(.month, from: monthDate)
                    let prefix = String(format: "%04d-%02d", year, month)

                    var totalMins = 0
                    var totalSessions = 0

                    for (dateStr, mins) in data.dailyMinutes {
                        if dateStr.hasPrefix(prefix) {
                            totalMins += mins
                            totalSessions += data.dailyCounts[dateStr] ?? 0
                        }
                    }

                    points.append(TrendDataPoint(
                        id: mLabel,
                        label: mLabel,
                        minutes: totalMins,
                        count: totalSessions,
                        isCurrent: (m == 0)
                    ))
                }
            }
            return points
        }
    }

    // MARK: - Circular Donut Chart Card (圆形环状饼图)

    private struct CourseSliceItem: Identifiable {
        let id: String
        let name: String
        let minutes: Int
        let percentage: Double
        let color: Color
        let startAngle: Angle
        let endAngle: Angle
    }

    private var subjectDonutChartCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("学科投入环形分布")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.primary)
                Spacer()

                Picker("", selection: $selectedDonutRange) {
                    ForEach(DonutTimeRange.allCases) { r in
                        Text(r.rawValue).tag(r)
                    }
                }
                .labelsHidden()
                .pickerStyle(.segmented)
                .frame(width: 230)
            }

            let slices = courseDonutSlices()
            let filteredRecords = filteredRecordsForDonut()
            let filteredTotalMinutes = filteredRecords.reduce(0) { $0 + $1.durationMinutes }

            if slices.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "chart.pie.fill")
                        .font(.system(size: 32))
                        .foregroundColor(.secondary.opacity(0.35))
                    Text("暂无学科专注数据")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(.secondary)
                    Text("在待办中关联课表课程即可生成环形投入图")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary.opacity(0.8))
                }
                .frame(maxWidth: .infinity, minHeight: 180)
            } else {
                HStack(spacing: 18) {
                    // 1. Donut Ring View
                    ZStack {
                        ForEach(Array(slices.enumerated()), id: \.element.id) { index, slice in
                            DonutSlice(
                                startAngle: slice.startAngle,
                                endAngle: slice.endAngle,
                                innerRadiusRatio: 0.62
                            )
                            .fill(slice.color)
                            .scaleEffect(hoveredDonutIndex == index ? 1.05 : 1.0)
                            .animation(.spring(response: 0.25, dampingFraction: 0.7), value: hoveredDonutIndex)
                        }

                        // Center Hole Information
                        VStack(spacing: 2) {
                            if let hIdx = hoveredDonutIndex, hIdx < slices.count {
                                let item = slices[hIdx]
                                Text(item.name)
                                    .font(.system(size: 11.5, weight: .bold))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)
                                Text("\(item.minutes)m")
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundColor(item.color)
                                Text("\(Int(round(item.percentage * 100)))%")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.secondary)
                            } else {
                                Text("总投入")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.secondary)
                                Text(String(format: "%.1f", Double(filteredTotalMinutes) / 60.0))
                                    .font(.system(size: 18, weight: .bold, design: .rounded))
                                    .foregroundColor(.primary)
                                Text("小时")
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                        }
                        .frame(width: 80)
                    }
                    .frame(width: 140, height: 140)
                    .contentShape(Circle())
                    .onContinuousHover { phase in
                        switch phase {
                        case .active(let loc):
                            updateHoveredSlice(at: loc, size: CGSize(width: 140, height: 140), slices: slices)
                        case .ended:
                            hoveredDonutIndex = nil
                        }
                    }

                    // 2. Legend List
                    VStack(alignment: .leading, spacing: 7) {
                        ForEach(Array(slices.prefix(5).enumerated()), id: \.element.id) { index, slice in
                            HStack(spacing: 7) {
                                Circle()
                                    .fill(slice.color)
                                    .frame(width: 8, height: 8)

                                Text(slice.name)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)

                                Spacer()

                                Text("\(Int(round(slice.percentage * 100)))%")
                                    .font(.system(size: 11, weight: .bold, design: .rounded))
                                    .foregroundColor(.secondary)

                                Text("\(slice.minutes)m")
                                    .font(.system(size: 10.5, design: .monospaced))
                                    .foregroundColor(.secondary.opacity(0.8))
                            }
                            .padding(.vertical, 2)
                            .contentShape(Rectangle())
                            .onHover { isHovered in
                                hoveredDonutIndex = isHovered ? index : nil
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .frame(minHeight: 155)
            }
        }
        .padding(18)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color(nsColor: .controlBackgroundColor).opacity(0.85))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                )
        )
    }

    private func updateHoveredSlice(at location: CGPoint, size: CGSize, slices: [CourseSliceItem]) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let dx = location.x - center.x
        let dy = location.y - center.y
        let radius = sqrt(dx * dx + dy * dy)
        let innerRadius = (min(size.width, size.height) / 2) * 0.62
        let outerRadius = (min(size.width, size.height) / 2) + 6

        // Must be within the donut ring
        guard radius >= innerRadius && radius <= outerRadius else {
            hoveredDonutIndex = nil
            return
        }

        // Angle in degrees (-180 to 180)
        let rad = atan2(dy, dx)
        var deg = rad * 180.0 / .pi
        // Normalize: top (12 o'clock) is -90 degrees
        if deg < -90.0 {
            deg += 360.0
        }

        for (index, slice) in slices.enumerated() {
            let s = slice.startAngle.degrees
            let e = slice.endAngle.degrees
            if deg >= s && deg < e {
                hoveredDonutIndex = index
                return
            }
        }
        hoveredDonutIndex = nil
    }

    private func filteredRecordsForDonut() -> [PomodoroRecord] {
        let now = Date()
        return appState.pomodoroRecords.filter { rec in
            let elapsed = now.timeIntervalSince(rec.completedAt)
            switch selectedDonutRange {
            case .week:
                return elapsed >= 0 && elapsed < 7 * 86400
            case .month:
                return elapsed >= 0 && elapsed < 30 * 86400
            case .year:
                return elapsed >= 0 && elapsed < 365 * 86400
            case .all:
                return true
            }
        }
    }

    private func courseDonutSlices() -> [CourseSliceItem] {
        let records = filteredRecordsForDonut()
        var map: [String: Int] = [:]
        for rec in records {
            let name = rec.courseName ?? rec.tag
            map[name, default: 0] += rec.durationMinutes
        }
        let total = max(1, records.reduce(0) { $0 + $1.durationMinutes })

        let colors: [Color] = [
            Color(hex: "#FF5A5F"),
            Color(hex: "#5B7FB5"),
            Color(hex: "#56A3A6"),
            Color(hex: "#E8A87C"),
            Color(hex: "#9B59B6"),
            Color(hex: "#3498DB")
        ]

        var slices: [CourseSliceItem] = []
        var currentAngle: Double = -90.0
        var idx = 0

        for (name, mins) in map.sorted(by: { $0.value > $1.value }) {
            let pct = Double(mins) / Double(total)
            let angleDelta = pct * 360.0
            let start = Angle(degrees: currentAngle)
            let end = Angle(degrees: currentAngle + angleDelta)
            currentAngle += angleDelta

            slices.append(CourseSliceItem(
                id: name,
                name: name,
                minutes: mins,
                percentage: pct,
                color: colors[idx % colors.count],
                startAngle: start,
                endAngle: end
            ))
            idx += 1
        }
        return slices
    }
}

/// Custom Donut Slice Shape in native SwiftUI
public struct DonutSlice: Shape {
    public var startAngle: Angle
    public var endAngle: Angle
    public var innerRadiusRatio: CGFloat = 0.6

    public func path(in rect: CGRect) -> Path {
        var path = Path()
        let center = CGPoint(x: rect.midX, y: rect.midY)
        let outerRadius = min(rect.width, rect.height) / 2
        let innerRadius = outerRadius * innerRadiusRatio

        path.addArc(center: center, radius: outerRadius, startAngle: startAngle, endAngle: endAngle, clockwise: false)
        path.addArc(center: center, radius: innerRadius, startAngle: endAngle, endAngle: startAngle, clockwise: true)
        path.closeSubpath()
        return path
    }
}
