import WidgetKit
import SwiftUI

/// Timeline entry holding snapshot data for WidgetKit rendering.
public struct ClassFlowWidgetEntry: TimelineEntry {
    public let date: Date
    public let payload: WidgetTimelinePayload

    public init(date: Date, payload: WidgetTimelinePayload) {
        self.date = date
        self.payload = payload
    }
}

/// Unified timeline provider reading synced schedule data from local container.
public struct ClassFlowTimelineProvider: TimelineProvider {
    private let dataStore: WidgetDataStore

    public init(dataStore: WidgetDataStore = WidgetDataStore()) {
        self.dataStore = dataStore
    }

    public func placeholder(in context: Context) -> ClassFlowWidgetEntry {
        ClassFlowWidgetEntry(date: Date(), payload: makeSamplePayload())
    }

    public func getSnapshot(in context: Context, completion: @escaping (ClassFlowWidgetEntry) -> Void) {
        let payload = dataStore.load()
        completion(ClassFlowWidgetEntry(date: Date(), payload: payload.todayDateString.isEmpty ? makeSamplePayload() : payload))
    }

    public func getTimeline(in context: Context, completion: @escaping (Timeline<ClassFlowWidgetEntry>) -> Void) {
        let payload = dataStore.load()
        let currentEntry = ClassFlowWidgetEntry(
            date: Date(),
            payload: payload.todayDateString.isEmpty ? makeSamplePayload() : payload
        )

        // Refresh every 15 minutes or at next class start time
        let nextUpdate = Calendar.current.date(byAdding: .minute, value: 15, to: Date()) ?? Date()
        let timeline = Timeline(entries: [currentEntry], policy: .after(nextUpdate))
        completion(timeline)
    }

    private func makeSamplePayload() -> WidgetTimelinePayload {
        WidgetTimelinePayload(
            semesterName: "",
            currentWeek: 1,
            todayDateString: "",
            nextClass: nil,
            todayClasses: []
        )
    }
}

/// Small widget: Countdown and next upcoming class.
public struct NextClassWidget: Widget {
    public let kind: String = "ClassFlowNextClassWidget"

    public init() {}

    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ClassFlowTimelineProvider()) { entry in
            NextClassWidgetView(payload: entry.payload)
        }
        .configurationDisplayName("下节课程")
        .description("显示即将开始的课程与倒计时")
        .supportedFamilies([.systemSmall])
    }
}

/// Medium widget: Today's chronological classes.
public struct TodayClassesWidget: Widget {
    public let kind: String = "ClassFlowTodayClassesWidget"

    public init() {}

    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ClassFlowTimelineProvider()) { entry in
            TodayClassesWidgetView(payload: entry.payload)
        }
        .configurationDisplayName("今日课程")
        .description("显示今日所有课程安排与上课地点")
        .supportedFamilies([.systemMedium])
    }
}

/// Large widget: Weekly timetable overview.
public struct WeekOverviewWidget: Widget {
    public let kind: String = "ClassFlowWeekOverviewWidget"

    public init() {}

    public var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ClassFlowTimelineProvider()) { entry in
            WeekOverviewWidgetView(payload: entry.payload)
        }
        .configurationDisplayName("周课程表")
        .description("概览本周所有课程与日程分布")
        .supportedFamilies([.systemLarge, .systemExtraLarge])
    }
}

/// WidgetBundle registering ClassFlow desktop widgets with macOS.
@main
public struct ClassFlowWidgetBundle: WidgetBundle {
    public init() {}

    public var body: some Widget {
        NextClassWidget()
        TodayClassesWidget()
        WeekOverviewWidget()
    }
}
