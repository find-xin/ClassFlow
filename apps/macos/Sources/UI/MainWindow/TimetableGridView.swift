import SwiftUI
import ClassFlowCore

/// The primary timetable canvas blending seamlessly with wallpaper, filling 100% of the window width,
/// handling multi-course overlapping conflict layouts side-by-side (like Google Calendar & WakeUp),
/// supporting both Week View (周视图) and Day View (日视图), and respecting user appearance settings.
public struct TimetableGridView: View {
    public let semester: Semester
    public let occurrences: [TimelineItem]
    public let dateStrings: [String]
    public let todayDateString: String
    public let displayMode: AppState.TimetableDisplayMode
    public let selectedDayOfWeek: Int
    public let settings: AppSettings
    public let onSelectOccurrence: (TimelineItem) -> Void
    public let onSelectDayOfWeek: ((Int) -> Void)?
    public let onAddCourseAt: ((Int, Int) -> Void)?
    public let onAddCalendarEventAt: ((Int, Int) -> Void)?
    public let onQuickImport: () -> Void
    public let onPreviousWeek: (() -> Void)?
    public let onNextWeek: (() -> Void)?
    public let onPreviousDay: (() -> Void)?
    public let onNextDay: (() -> Void)?
    public let onToday: (() -> Void)?

    private let periodSpacing: CGFloat = 4
    private let dayNames = ["一", "二", "三", "四", "五", "六", "日"]

    public init(
        semester: Semester,
        occurrences: [TimelineItem],
        dateStrings: [String],
        todayDateString: String,
        displayMode: AppState.TimetableDisplayMode = .week,
        selectedDayOfWeek: Int = 1,
        settings: AppSettings = AppSettings(),
        onSelectOccurrence: @escaping (TimelineItem) -> Void = { _ in },
        onSelectDayOfWeek: ((Int) -> Void)? = nil,
        onAddCourseAt: ((Int, Int) -> Void)? = nil,
        onAddCalendarEventAt: ((Int, Int) -> Void)? = nil,
        onQuickImport: (() -> Void)? = nil,
        onPreviousWeek: (() -> Void)? = nil,
        onNextWeek: (() -> Void)? = nil,
        onPreviousDay: (() -> Void)? = nil,
        onNextDay: (() -> Void)? = nil,
        onToday: (() -> Void)? = nil
    ) {
        self.semester = semester
        self.occurrences = occurrences
        self.dateStrings = dateStrings
        self.todayDateString = todayDateString
        self.displayMode = displayMode
        self.selectedDayOfWeek = selectedDayOfWeek
        self.settings = settings
        self.onSelectOccurrence = onSelectOccurrence
        self.onSelectDayOfWeek = onSelectDayOfWeek
        self.onAddCourseAt = onAddCourseAt
        self.onAddCalendarEventAt = onAddCalendarEventAt
        self.onQuickImport = onQuickImport ?? {}
        self.onPreviousWeek = onPreviousWeek
        self.onNextWeek = onNextWeek
        self.onPreviousDay = onPreviousDay
        self.onNextDay = onNextDay
        self.onToday = onToday
    }

    public var visibleDays: [Int] {
        var days = [1, 2, 3, 4, 5]
        if settings.showWeekends {
            if settings.showSaturday {
                days.append(6)
            }
            if settings.showSunday {
                days.append(7)
            }
        }
        return days
    }

    private var visibleDayCount: Int {
        if displayMode == .day {
            return 1
        }
        return visibleDays.count
    }

    private var currentMonthComponents: (String, String) {
        guard let first = dateStrings.first, let month = first.split(separator: "-").dropFirst().first else {
            return ("\(Calendar.current.component(.month, from: Date()))", "月")
        }
        let mInt = Int(month) ?? 1
        return ("\(mInt)", "月")
    }

    private var selectedDayDateString: String {
        let idx = selectedDayOfWeek - 1
        if idx >= 0 && idx < dateStrings.count {
            return dateStrings[idx]
        }
        return ""
    }

    /// Dynamically expands the section schedule to accommodate early morning, late night events, and custom section counts.
    private var effectiveSections: [SectionTime] {
        let base = semester.sections.isEmpty ? AppState.standardSectionTimes() : semester.sections

        var result = base
        let baseFirstStart = parseTimeToMinutes(base.first?.startTime ?? "08:00") ?? 480
        let baseLastEnd = parseTimeToMinutes(base.last?.endTime ?? "21:30") ?? 1290

        var earliestStart = baseFirstStart
        var latestEnd = baseLastEnd

        for item in occurrences {
            if item.isAllDay { continue }
            if let sStr = item.startTime, let sM = parseTimeToMinutes(sStr) {
                if sM < earliestStart {
                    earliestStart = sM
                }
            }
            if let eStr = item.endTime, let eM = parseTimeToMinutes(eStr) {
                if eM > latestEnd {
                    latestEnd = eM
                }
            }
        }

        if earliestStart < baseFirstStart {
            let h = earliestStart / 60
            let m = earliestStart % 60
            let earlyStartStr = String(format: "%02d:%02d", h, m)
            let earlySec = SectionTime(section: 0, startTime: earlyStartStr, endTime: base.first?.startTime ?? "08:00")
            result.insert(earlySec, at: 0)
        }

        // Accommodate any course whose endSection is beyond the base section count
        let maxOccurrenceSection = occurrences.filter { !$0.isAllDay }.map { $0.endSection }.max() ?? 0
        if let lastSec = result.filter({ $0.section < 90 }).last, maxOccurrenceSection > lastSec.section {
            var currentSec = lastSec.section
            var currentEnd = parseTimeToMinutes(lastSec.endTime) ?? 1290
            while currentSec < maxOccurrenceSection {
                currentSec += 1
                let nextStart = currentEnd + 10
                let nextEnd = nextStart + 45
                let sH = (nextStart / 60) % 24
                let sM = nextStart % 60
                let eH = (nextEnd / 60) % 24
                let eM = nextEnd % 60
                let sStr = String(format: "%02d:%02d", sH, sM)
                let eStr = String(format: "%02d:%02d", eH, eM)
                result.append(SectionTime(section: currentSec, startTime: sStr, endTime: eStr))
                currentEnd = nextEnd
            }
        }

        let updatedLastEnd = parseTimeToMinutes(result.last?.endTime ?? "21:30") ?? 1290
        if latestEnd > updatedLastEnd {
            let h = min(23, (latestEnd + 59) / 60)
            let lateEndStr = String(format: "%02d:00", h)
            let lateSec = SectionTime(section: 99, startTime: result.last?.endTime ?? "21:30", endTime: lateEndStr)
            result.append(lateSec)
        }

        return result
    }

    private func formatDayNumber(_ isoDate: String) -> String {
        let parts = isoDate.split(separator: "-")
        guard parts.count == 3 else { return isoDate }
        if let dInt = Int(parts[2]) {
            return "\(dInt)"
        }
        return String(parts[2])
    }

    static func parseTimeToMinutes(_ timeStr: String) -> Int? {
        let parts = timeStr.split(separator: ":")
        guard parts.count >= 2, let h = Int(parts[0]), let m = Int(parts[1]) else { return nil }
        return h * 60 + m
    }

    private func parseTimeToMinutes(_ timeStr: String) -> Int? {
        Self.parseTimeToMinutes(timeStr)
    }

    static func yOffset(forMinutes minutes: Int, sections: [SectionTime], periodHeight: CGFloat, periodSpacing: CGFloat) -> CGFloat {
        let secTimes = sections.compactMap { s -> (sec: SectionTime, start: Int, end: Int)? in
            guard let sM = parseTimeToMinutes(s.startTime), let eM = parseTimeToMinutes(s.endTime) else { return nil }
            return (s, sM, eM)
        }

        guard !secTimes.isEmpty else {
            return 0
        }

        let firstStart = secTimes.first!.start
        let lastEnd = secTimes.last!.end
        let totalSections = sections.count

        if minutes <= firstStart {
            return 0
        }

        if minutes >= lastEnd {
            let lastY = CGFloat(totalSections - 1) * (periodHeight + periodSpacing) + periodHeight
            return lastY
        }

        for (idx, st) in secTimes.enumerated() {
            let yTop = CGFloat(idx) * (periodHeight + periodSpacing)
            let yBottom = yTop + periodHeight

            if minutes >= st.start && minutes <= st.end {
                let span = CGFloat(max(1, st.end - st.start))
                let frac = CGFloat(minutes - st.start) / span
                return yTop + frac * periodHeight
            }

            if idx + 1 < secTimes.count {
                let nextSt = secTimes[idx + 1]
                if minutes > st.end && minutes < nextSt.start {
                    let breakSpan = CGFloat(max(1, nextSt.start - st.end))
                    let frac = CGFloat(minutes - st.end) / breakSpan
                    return yBottom + frac * periodSpacing
                }
            }
        }

        return CGFloat(totalSections - 1) * (periodHeight + periodSpacing)
    }

    private func yOffset(forMinutes minutes: Int, sections: [SectionTime], periodHeight: CGFloat, periodSpacing: CGFloat) -> CGFloat {
        Self.yOffset(forMinutes: minutes, sections: sections, periodHeight: periodHeight, periodSpacing: periodSpacing)
    }

    public var body: some View {
        GeometryReader { proxy in
            let availableWidth = proxy.size.width
            let availableHeight = proxy.size.height
            let horizontalPadding: CGFloat = 20
            let axisWidth: CGFloat = 44

            let columnWidth: CGFloat = {
                if displayMode == .day {
                    return max(240, availableWidth - horizontalPadding - axisWidth - 8)
                } else {
                    let spacingTotal = CGFloat(visibleDayCount) * 4
                    let remainingWidth = availableWidth - horizontalPadding - axisWidth - spacingTotal
                    return max(50, remainingWidth / CGFloat(visibleDayCount))
                }
            }()

            let totalContentWidth: CGFloat = {
                if displayMode == .day {
                    return availableWidth
                } else {
                    let spacingTotal = CGFloat(visibleDayCount) * 4
                    return axisWidth + spacingTotal + CGFloat(visibleDayCount) * columnWidth + horizontalPadding
                }
            }()

            let totalSections = max(1, effectiveSections.count)
            let totalSpacingY = CGFloat(max(0, totalSections - 1)) * periodSpacing
            let headerOverhead: CGFloat = displayMode == .day ? 64 : 58
            let gridAvailableHeight = max(0, availableHeight - headerOverhead - totalSpacingY)
            let computedPeriodHeight = (availableHeight > 100) ? floor(gridAvailableHeight / CGFloat(totalSections)) : CGFloat(settings.gridHeight)
            let dynamicPeriodHeight = max(CGFloat(settings.gridHeight), computedPeriodHeight)

            ScrollView([.vertical, .horizontal], showsIndicators: false) {
                VStack(alignment: .leading, spacing: 4) {
                    // 1. Header (Week View: Multi-day Row; Day View: Single-day Navigation Ribbon)
                    if displayMode == .day {
                        dayViewHeader(axisWidth: axisWidth, availableWidth: availableWidth)
                    } else {
                        dayHeaderRow(axisWidth: axisWidth, columnWidth: columnWidth)
                    }

                    // 1.5 All-Day Banner Row (Native Mac / Google Calendar style)
                    allDayRow(axisWidth: axisWidth, columnWidth: columnWidth)

                    // 2. Main Timetable Canvas
                    let itemsByDay = Dictionary(grouping: occurrences.filter { !$0.isAllDay }, by: { $0.dayOfWeek })

                    ZStack(alignment: .topLeading) {
                        HStack(alignment: .top, spacing: 4) {
                            // Left Section Axis
                            SectionAxisView(
                                sections: effectiveSections,
                                periodHeight: dynamicPeriodHeight,
                                showTime: settings.showSectionTimeInAxis
                            )
                            .frame(width: axisWidth)

                            if displayMode == .day {
                                // Single Day Column for Day View
                                let dayOfWeek = selectedDayOfWeek
                                let dayItems = itemsByDay[dayOfWeek] ?? []
                                let isToday = (dayOfWeek - 1 < dateStrings.count) && (dateStrings[dayOfWeek - 1] == todayDateString)

                                dayColumn(dayOfWeek: dayOfWeek, items: dayItems, isToday: isToday, columnWidth: columnWidth, periodHeight: dynamicPeriodHeight)
                                    .frame(width: columnWidth)
                            } else {
                                // Multi-day Columns for Week View
                                ForEach(visibleDays, id: \.self) { dayOfWeek in
                                    let dayItems = itemsByDay[dayOfWeek] ?? []
                                    let isToday = (dayOfWeek - 1 < dateStrings.count) && (dateStrings[dayOfWeek - 1] == todayDateString)

                                    dayColumn(dayOfWeek: dayOfWeek, items: dayItems, isToday: isToday, columnWidth: columnWidth, periodHeight: dynamicPeriodHeight)
                                        .frame(width: columnWidth)
                                }
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .frame(width: max(availableWidth, totalContentWidth), alignment: .leading)

                        // 3. Empty State matching WakeUp screenshot ("无课表，通过教务导课")
                        if occurrences.isEmpty {
                            VStack(spacing: 12) {
                                Text("无课表，通过教务导课")
                                    .font(.system(size: 14, weight: .medium))
                                    .foregroundColor(.primary.opacity(0.75))

                                Button(action: onQuickImport) {
                                    Text("一键导入")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.primary)
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 8)
                                        .background(
                                            Capsule()
                                                .fill(.ultraThinMaterial)
                                                .overlay(Capsule().strokeBorder(Color.white.opacity(0.3), lineWidth: 0.5))
                                                .shadow(color: Color.black.opacity(0.06), radius: 4, y: 2)
                                        )
                                }
                                .buttonStyle(.plain)
                            }
                            .frame(width: max(availableWidth, totalContentWidth), height: CGFloat(effectiveSections.count) * (dynamicPeriodHeight + periodSpacing))
                        }
                    }
                }
            }
            .background {
                TrackpadSwipeCaptureView(
                    onSwipeLeft: {
                        if displayMode == .day {
                            onNextDay?()
                        } else {
                            onNextWeek?()
                        }
                    },
                    onSwipeRight: {
                        if displayMode == .day {
                            onPreviousDay?()
                        } else {
                            onPreviousWeek?()
                        }
                    }
                )
            }
            .simultaneousGesture(
                DragGesture(minimumDistance: 35)
                    .onEnded { value in
                        let dx = value.translation.width
                        let dy = value.translation.height
                        if abs(dx) > abs(dy) * 1.4 {
                            if dx < -45 {
                                if displayMode == .day {
                                    onNextDay?()
                                } else {
                                    onNextWeek?()
                                }
                            } else if dx > 45 {
                                if displayMode == .day {
                                    onPreviousDay?()
                                } else {
                                    onPreviousWeek?()
                                }
                            }
                        }
                    }
            )
        }
    }

    // MARK: - Day View Header (Matching Week View Typography & Frosted Glass Aesthetics)

    private func dayViewHeader(axisWidth: CGFloat, availableWidth: CGFloat) -> some View {
        HStack(alignment: .center, spacing: 4) {
            // Left Month & Day Navigation aligned with the axis
            VStack(spacing: 2) {
                HStack(spacing: 4) {
                    Button(action: { onPreviousDay?() }) {
                        Image(systemName: "chevron.left")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.primary.opacity(0.8))
                            .frame(width: 18, height: 18)
                            .background(Circle().fill(Color.white.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .help("前一天")

                    Button(action: { onNextDay?() }) {
                        Image(systemName: "chevron.right")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.primary.opacity(0.8))
                            .frame(width: 18, height: 18)
                            .background(Circle().fill(Color.white.opacity(0.12)))
                    }
                    .buttonStyle(.plain)
                    .help("后一天")
                }

                if selectedDayDateString != todayDateString {
                    Button(action: { onToday?() }) {
                        Text("今")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.primary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 1.5)
                            .background(Capsule().fill(Color.white.opacity(0.18)))
                    }
                    .buttonStyle(.plain)
                    .help("回到今天")
                } else {
                    Text("\(currentMonthComponents.0)\(currentMonthComponents.1)")
                        .font(.system(size: 10.5, weight: .medium))
                        .foregroundColor(.secondary)
                }
            }
            .frame(width: axisWidth)

            // Visible days selector strip using identical stacked typography
            HStack(spacing: 4) {
                ForEach(visibleDays, id: \.self) { day in
                    let idx = day - 1
                    let dateStr = idx < dateStrings.count ? dateStrings[idx] : ""
                    let isSelected = (day == selectedDayOfWeek)
                    let isToday = (dateStr == todayDateString)
                    let dayNumber = formatDayNumber(dateStr)

                    Button(action: {
                        onSelectDayOfWeek?(day)
                    }) {
                        VStack(spacing: 2) {
                            Text(dayNames[idx])
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                .foregroundColor(isSelected ? .primary : (isToday ? .primary : .secondary.opacity(0.8)))

                            Text(dayNumber)
                                .font(.system(size: 11, weight: isSelected || isToday ? .bold : .regular))
                                .foregroundColor(isSelected ? .primary : .secondary)
                                .frame(width: 22, height: 22)
                                .background(
                                    Group {
                                        if isSelected {
                                            Circle()
                                                .fill(Color.white.opacity(0.24))
                                                .overlay(Circle().strokeBorder(Color.white.opacity(0.35), lineWidth: 0.5))
                                        } else if isToday {
                                            Circle()
                                                .strokeBorder(Color.white.opacity(0.4), lineWidth: 1)
                                        } else {
                                            Color.clear
                                        }
                                    }
                                )
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(isSelected ? Color.white.opacity(0.12) : Color.clear)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10, style: .continuous)
                                        .strokeBorder(isSelected ? Color.white.opacity(0.25) : Color.clear, lineWidth: 0.5)
                                )
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
            .frame(maxWidth: .infinity)
        }
        .padding(.horizontal, 10)
        .padding(.bottom, 4)
    }

    // MARK: - All-Day Banner Row (Native Mac / Google Calendar style)

    @ViewBuilder
    private func allDayRow(axisWidth: CGFloat, columnWidth: CGFloat) -> some View {
        let allDayList = occurrences.filter { $0.isAllDay }
        if !allDayList.isEmpty {
            let dayAllDayMap = Dictionary(grouping: allDayList, by: { $0.dayOfWeek })

            HStack(alignment: .center, spacing: 4) {
                Text("全天")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.secondary)
                    .frame(width: axisWidth)

                if displayMode == .day {
                    let items = dayAllDayMap[selectedDayOfWeek] ?? []
                    allDayItemsContainer(items: items, width: columnWidth)
                } else {
                    ForEach(visibleDays, id: \.self) { dow in
                        let items = dayAllDayMap[dow] ?? []
                        allDayItemsContainer(items: items, width: columnWidth)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 2)
        }
    }

    private func allDayItemsContainer(items: [TimelineItem], width: CGFloat) -> some View {
        VStack(spacing: 2) {
            if items.isEmpty {
                Color.clear.frame(height: 14)
            } else {
                ForEach(items) { it in
                    Button(action: { onSelectOccurrence(it) }) {
                        Text(it.title)
                            .font(.system(size: 10.5, weight: .semibold))
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2.5)
                            .background(
                                RoundedRectangle(cornerRadius: 5, style: .continuous)
                                    .fill(Color(hex: it.color, defaultColor: Color.accentColor))
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(width: width)
    }

    // MARK: - Day Header Row (for Week View Mode)

    private func dayHeaderRow(axisWidth: CGFloat, columnWidth: CGFloat) -> some View {
        HStack(alignment: .center, spacing: 4) {
            // Month indicator (WakeUp stacked layout)
            VStack(spacing: 0) {
                Text(currentMonthComponents.0)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.primary)
                Text(currentMonthComponents.1)
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(.secondary)
            }
            .frame(width: axisWidth)

            // Day columns
            ForEach(visibleDays, id: \.self) { dayOfWeek in
                let index = dayOfWeek - 1
                let dateStr = index < dateStrings.count ? dateStrings[index] : ""
                let isToday = (dateStr == todayDateString)
                let dayNumber = formatDayNumber(dateStr)

                VStack(spacing: 4) {
                    Text(dayNames[index])
                        .font(.system(size: 12.5, weight: isToday ? .bold : .medium))
                        .foregroundColor(isToday ? .primary : .secondary.opacity(0.85))

                    if isToday {
                        Text(dayNumber)
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 22, height: 22)
                            .background(
                                RoundedRectangle(cornerRadius: 6, style: .continuous)
                                    .fill(Color(hex: settings.headerColorHex, defaultColor: Color(hex: "#1F1F1F")))
                            )
                    } else {
                        Text(dayNumber)
                            .font(.system(size: 11.5, weight: .regular))
                            .foregroundColor(.secondary.opacity(0.8))
                            .frame(width: 22, height: 22)
                    }
                }
                .frame(width: columnWidth)
            }
        }
        .padding(.horizontal, 10)
        .padding(.bottom, 4)
    }

    struct CardLayoutInfo: Identifiable {
        let id: String
        let item: TimelineItem
        let topOffset: CGFloat
        let cardHeight: CGFloat
        let cardWidth: CGFloat
        let xOffset: CGFloat
    }

    private struct ItemMetric {
        let item: TimelineItem
        let yTop: CGFloat
        let yBottom: CGFloat
        let height: CGFloat
    }

    // MARK: - Accurate Google Calendar Style Interval Conflict Layout

    static func itemsOverlap(_ a: TimelineItem, _ b: TimelineItem, sections: [SectionTime] = []) -> Bool {
        func minutesRange(for item: TimelineItem) -> (start: Int, end: Int)? {
            if let s = item.startTime, let e = item.endTime,
               let sm = parseTimeToMinutes(s), let em = parseTimeToMinutes(e), em > sm {
                return (sm, em)
            }
            let matching = sections.filter { $0.section >= item.startSection && $0.section <= item.endSection }
            if let first = matching.first, let last = matching.last,
               let sm = parseTimeToMinutes(first.startTime), let em = parseTimeToMinutes(last.endTime), em > sm {
                return (sm, em)
            }
            if let s = item.startTime, let sm = parseTimeToMinutes(s) {
                return (sm, sm + 45)
            }
            return nil
        }

        if let aMin = minutesRange(for: a), let bMin = minutesRange(for: b) {
            return (aMin.start < bMin.end) && (bMin.start < aMin.end)
        }

        let aRange = min(a.startSection, a.endSection)...max(a.startSection, a.endSection)
        let bRange = min(b.startSection, b.endSection)...max(b.startSection, b.endSection)
        return aRange.overlaps(bRange)
    }

    static func computeDayLayout(
        items: [TimelineItem],
        sections: [SectionTime],
        columnWidth: CGFloat,
        periodHeight: CGFloat,
        periodSpacing: CGFloat
    ) -> [CardLayoutInfo] {
        guard !items.isEmpty else { return [] }

        // Filter items: Active courses and arrangements for the current week always take absolute precedence.
        // A non-current week course should only appear in an empty time slot where no active current-week arrangement runs.
        let activeItems = items.filter { $0.isCurrentWeek }
        let nonCurrentItems = items.filter { !$0.isCurrentWeek }

        var displayItems = activeItems
        for nonCurr in nonCurrentItems {
            let overlapsActive = activeItems.contains { act in
                itemsOverlap(nonCurr, act, sections: sections)
            }
            if !overlapsActive {
                // If multiple non-current week courses overlap in the same empty slot,
                // keep the first one so they don't split the column unnecessarily.
                let overlapsExistingNonCurr = displayItems.contains { disp in
                    !disp.isCurrentWeek && itemsOverlap(disp, nonCurr, sections: sections)
                }
                if !overlapsExistingNonCurr {
                    displayItems.append(nonCurr)
                }
            }
        }

        var metrics: [ItemMetric] = []
        for item in displayItems {
            var yTop: CGFloat = 0
            var yBottom: CGFloat = 0

            if let sTime = item.startTime, let eTime = item.endTime,
               let sM = parseTimeToMinutes(sTime), let eM = parseTimeToMinutes(eTime), eM > sM {
                yTop = yOffset(forMinutes: sM, sections: sections, periodHeight: periodHeight, periodSpacing: periodSpacing)
                yBottom = yOffset(forMinutes: eM, sections: sections, periodHeight: periodHeight, periodSpacing: periodSpacing)
                let minH: CGFloat = 28
                if yBottom - yTop < minH {
                    yBottom = yTop + minH
                }
            } else {
                let sIdx = sections.firstIndex(where: { $0.section == item.startSection }) ?? max(0, min(sections.count - 1, item.startSection - 1))
                let eIdx = sections.firstIndex(where: { $0.section == item.endSection }) ?? max(sIdx, min(sections.count - 1, item.endSection - 1))
                yTop = CGFloat(sIdx) * (periodHeight + periodSpacing)
                let span = CGFloat(eIdx - sIdx + 1)
                let h = span * periodHeight + (span - 1) * periodSpacing
                yBottom = yTop + h
            }

            metrics.append(ItemMetric(item: item, yTop: yTop, yBottom: yBottom, height: max(24, yBottom - yTop)))
        }

        let sorted = metrics.sorted {
            if abs($0.yTop - $1.yTop) > 1.0 {
                return $0.yTop < $1.yTop
            }
            return $0.height > $1.height
        }

        // Form connected overlapping clusters
        var clusters: [[ItemMetric]] = []
        var currentCluster: [ItemMetric] = []
        var currentClusterBottom: CGFloat = -1

        for m in sorted {
            if currentCluster.isEmpty {
                currentCluster.append(m)
                currentClusterBottom = m.yBottom
            } else {
                if m.yTop < currentClusterBottom - 1.0 {
                    currentCluster.append(m)
                    currentClusterBottom = max(currentClusterBottom, m.yBottom)
                } else {
                    clusters.append(currentCluster)
                    currentCluster = [m]
                    currentClusterBottom = m.yBottom
                }
            }
        }
        if !currentCluster.isEmpty {
            clusters.append(currentCluster)
        }

        // Greedy column lane assignment for each cluster (Google Calendar algorithm)
        var result: [CardLayoutInfo] = []
        for cluster in clusters {
            var colEnds: [CGFloat] = []
            var assignedCols: [String: Int] = [:]

            for m in cluster {
                var placedCol: Int? = nil
                for c in 0..<colEnds.count {
                    if m.yTop >= colEnds[c] - 1.0 {
                        placedCol = c
                        colEnds[c] = m.yBottom
                        break
                    }
                }
                if let c = placedCol {
                    assignedCols[m.item.id] = c
                } else {
                    let newCol = colEnds.count
                    colEnds.append(m.yBottom)
                    assignedCols[m.item.id] = newCol
                }
            }

            let clusterColCount = max(1, colEnds.count)
            let spacing: CGFloat = 2
            let totalSpacing = CGFloat(clusterColCount - 1) * spacing
            let cardWidth = max(20, (columnWidth - totalSpacing) / CGFloat(clusterColCount))

            for m in cluster {
                let col = assignedCols[m.item.id] ?? 0
                let xOffset = CGFloat(col) * (cardWidth + spacing)
                result.append(CardLayoutInfo(
                    id: m.item.id,
                    item: m.item,
                    topOffset: m.yTop,
                    cardHeight: m.height,
                    cardWidth: cardWidth,
                    xOffset: xOffset
                ))
            }
        }

        return result
    }

    private func computeDayLayout(items: [TimelineItem], columnWidth: CGFloat, periodHeight: CGFloat) -> [CardLayoutInfo] {
        Self.computeDayLayout(
            items: items,
            sections: effectiveSections,
            columnWidth: columnWidth,
            periodHeight: periodHeight,
            periodSpacing: periodSpacing
        )
    }

    @ViewBuilder
    private func dayColumn(dayOfWeek: Int, items: [TimelineItem], isToday: Bool, columnWidth: CGFloat, periodHeight: CGFloat) -> some View {
        let totalSections = effectiveSections.count
        let totalHeight = CGFloat(totalSections) * periodHeight + CGFloat(max(0, totalSections - 1)) * periodSpacing
        let layoutItems = computeDayLayout(items: items, columnWidth: columnWidth, periodHeight: periodHeight)

        ZStack(alignment: .topLeading) {
            // Distinct Today vertical column wash/highlight (WakeUp & Google Calendar style)
            if isToday {
                RoundedRectangle(cornerRadius: CGFloat(settings.cardCornerRadius), style: .continuous)
                    .fill(Color.white.opacity(0.06))
                    .overlay(
                        RoundedRectangle(cornerRadius: CGFloat(settings.cardCornerRadius), style: .continuous)
                            .strokeBorder(Color.white.opacity(0.14), lineWidth: 0.8)
                    )
                    .frame(width: columnWidth, height: totalHeight)
                    .allowsHitTesting(false)
            }

            // Subtle slot tracks so wallpaper shines through cleanly
            VStack(spacing: periodSpacing) {
                ForEach(effectiveSections) { sec in
                    EmptySlotButton(
                        dayOfWeek: dayOfWeek,
                        section: sec.section,
                        isToday: isToday,
                        cornerRadius: CGFloat(settings.cardCornerRadius),
                        showGridLines: settings.showGridGuideLines,
                        height: periodHeight,
                        dayName: dayNames[dayOfWeek - 1],
                        onAddCourse: {
                            onAddCourseAt?(dayOfWeek, sec.section)
                        },
                        onAddCalendarEvent: {
                            onAddCalendarEventAt?(dayOfWeek, sec.section)
                        }
                    )
                }
            }

            // Overlay Course & Calendar Event Cards (Side-by-side without collision)
            ForEach(layoutItems) { layout in
                CourseCardView(
                    item: layout.item,
                    height: layout.cardHeight,
                    settings: settings,
                    onTap: { onSelectOccurrence(layout.item) }
                )
                .frame(width: layout.cardWidth)
                .offset(x: layout.xOffset, y: layout.topOffset)
            }

            // Current Time Indicator (Accent line like Google Calendar if today)
            if isToday {
                currentTimeIndicator(periodHeight: periodHeight, totalWidth: columnWidth)
            }
        }
        .frame(width: columnWidth, height: totalHeight)
    }

    @ViewBuilder
    private func currentTimeIndicator(periodHeight: CGFloat, totalWidth: CGFloat) -> some View {
        let cal = Calendar.current
        let now = Date()
        let hour = cal.component(.hour, from: now)
        let min = cal.component(.minute, from: now)
        let currentMinutes = hour * 60 + min

        let yPos = yOffset(forMinutes: currentMinutes, sections: effectiveSections, periodHeight: periodHeight, periodSpacing: periodSpacing)

        HStack(spacing: 0) {
            Circle()
                .fill(Color.accentColor)
                .frame(width: 7, height: 7)
                .offset(x: -3.5)

            Rectangle()
                .fill(Color.accentColor.opacity(0.8))
                .frame(height: 1.5)
        }
        .frame(width: totalWidth)
        .offset(y: yPos - 3.5)
        .allowsHitTesting(false)
    }
}

/// Interactive empty timetable slot that allows the wallpaper to shine through cleanly without opaque boxes,
/// providing subtle hover feedback and a compact popover to add either a course or calendar event/task.
private struct EmptySlotButton: View {
    let dayOfWeek: Int
    let section: Int
    let isToday: Bool
    let cornerRadius: CGFloat
    let showGridLines: Bool
    let height: CGFloat
    let dayName: String
    let onAddCourse: () -> Void
    let onAddCalendarEvent: () -> Void

    @State private var isHovered: Bool = false
    @State private var isShowingPopover: Bool = false

    var body: some View {
        Button {
            isShowingPopover = true
        } label: {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(isHovered ? Color.white.opacity(0.18) : (isToday ? Color.white.opacity(0.04) : Color.white.opacity(0.001)))
                .overlay(
                    RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                        .strokeBorder(
                            isHovered ? Color.white.opacity(0.35) : (showGridLines ? Color.white.opacity(0.12) : Color.clear),
                            lineWidth: isHovered ? 1.0 : 0.5
                        )
                )
                .overlay {
                    if isHovered {
                        Image(systemName: "plus")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.primary.opacity(0.6))
                    }
                }
                .frame(height: height)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .popover(isPresented: $isShowingPopover, arrowEdge: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Button {
                    isShowingPopover = false
                    onAddCourse()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "book.pages")
                            .font(.system(size: 13))
                            .foregroundColor(.accentColor)
                            .frame(width: 18)
                        Text("添加课程")
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundColor(.primary)
                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.001)))
                }
                .buttonStyle(.plain)

                Divider()
                    .padding(.vertical, 1)

                Button {
                    isShowingPopover = false
                    onAddCalendarEvent()
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "calendar.badge.plus")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                            .frame(width: 18)
                        Text("添加日程")
                            .font(.system(size: 12.5, weight: .medium))
                            .foregroundColor(.primary)
                        Spacer()
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 6).fill(Color.primary.opacity(0.001)))
                }
                .buttonStyle(.plain)
            }
            .padding(6)
            .frame(width: 130)
        }
        .contextMenu {
            Button {
                onAddCourse()
            } label: {
                Label("添加课程", systemImage: "book.pages")
            }

            Button {
                onAddCalendarEvent()
            } label: {
                Label("添加日程", systemImage: "calendar.badge.plus")
            }
        }
        .onHover { hovering in
            withAnimation(.easeInOut(duration: 0.15)) {
                isHovered = hovering
            }
        }
        .help("点击添加课程或日程")
    }
}
