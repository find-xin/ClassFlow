import SwiftUI
import ClassFlowCore

/// Widget view for Small family: Next upcoming class with countdown status.
public struct NextClassWidgetView: View {
    public let payload: WidgetTimelinePayload

    public init(payload: WidgetTimelinePayload) {
        self.payload = payload
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Label("下一节课", systemImage: "clock.badge.fill")
                    .font(.caption2.bold())
                    .foregroundStyle(.secondary)
                Spacer()
                if let next = payload.nextClass {
                    if next.isOngoing {
                        Text("上课中")
                            .font(.system(size: 9, weight: .bold))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.green.opacity(0.2))
                            .foregroundStyle(.green)
                            .clipShape(Capsule())
                    } else if let minutes = next.minutesUntilStart {
                        Text("\(minutes)分钟后")
                            .font(.system(size: 9, weight: .semibold))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color.accentColor.opacity(0.15))
                            .foregroundStyle(Color.accentColor)
                            .clipShape(Capsule())
                    }
                }
            }

            Spacer()

            if let next = payload.nextClass {
                Text(next.title)
                    .font(.headline)
                    .lineLimit(2)
                    .minimumScaleFactor(0.85)

                if let room = next.location, !room.isEmpty {
                    Label(room, systemImage: "mappin.and.ellipse")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }

                Text("\(next.startTime) - \(next.endTime)（第\(next.startSection)-\(next.endSection)节）")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            } else {
                VStack(alignment: .leading, spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(.green)
                    Text("暂无即将开始的课程")
                        .font(.subheadline.bold())
                    Text("好好休息吧！")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer()
        }
        .padding()
        .containerBackground(for: .widget) {
            Color(nsColor: .windowBackgroundColor)
        }
    }
}

/// Widget view for Medium family: Today's schedule list.
public struct TodayClassesWidgetView: View {
    public let payload: WidgetTimelinePayload

    public init(payload: WidgetTimelinePayload) {
        self.payload = payload
    }

    public var body: some View {
        HStack(spacing: 12) {
            // Left sidebar: Date & summary
            VStack(alignment: .leading, spacing: 4) {
                Text("第 \(payload.currentWeek) 周")
                    .font(.caption2.bold())
                    .foregroundStyle(.secondary)

                Text(Date(), format: .dateTime.weekday(.wide))
                    .font(.headline.bold())

                Text(Date(), format: .dateTime.month().day())
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Spacer()

                Text("今日 \(payload.todayClasses.count) 节")
                    .font(.caption.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(Color.accentColor.opacity(0.12))
                    .foregroundStyle(Color.accentColor)
                    .clipShape(Capsule())
            }
            .frame(width: 80, alignment: .leading)

            Divider()

            // Right pane: Course list
            VStack(alignment: .leading, spacing: 6) {
                if payload.todayClasses.isEmpty {
                    Spacer()
                    HStack {
                        Spacer()
                        VStack(spacing: 4) {
                            Image(systemName: "cup.and.saucer.fill")
                                .font(.title3)
                                .foregroundStyle(.secondary)
                            Text("今日无课")
                                .font(.caption.bold())
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                    Spacer()
                } else {
                    ForEach(payload.todayClasses.prefix(3)) { item in
                        HStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 2)
                                .fill(Color(hex: item.colorHex) ?? .accentColor)
                                .frame(width: 3, height: 26)

                            VStack(alignment: .leading, spacing: 1) {
                                Text(item.title)
                                    .font(.caption.bold())
                                    .lineLimit(1)
                                HStack(spacing: 4) {
                                    Text("\(item.startTime) - \(item.endTime)")
                                        .font(.system(size: 9))
                                        .foregroundStyle(.secondary)
                                    if let room = item.location, !room.isEmpty {
                                        Text("• \(room)")
                                            .font(.system(size: 9))
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                    }
                                }
                            }
                            Spacer()
                        }
                    }
                    if payload.todayClasses.count > 3 {
                        Text("+ 还剩 \(payload.todayClasses.count - 3) 门课程")
                            .font(.system(size: 9))
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .padding()
        .containerBackground(for: .widget) {
            Color(nsColor: .windowBackgroundColor)
        }
    }
}

/// Widget view for Large family: Week overview mini timetable.
public struct WeekOverviewWidgetView: View {
    public let payload: WidgetTimelinePayload

    public init(payload: WidgetTimelinePayload) {
        self.payload = payload
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(payload.semesterName)
                    .font(.caption2.bold())
                    .foregroundStyle(.secondary)
                Spacer()
                Text("第 \(payload.currentWeek) 周")
                    .font(.caption.bold())
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.12))
                    .foregroundStyle(Color.accentColor)
                    .clipShape(Capsule())
            }

            Divider()

            HStack(alignment: .top, spacing: 4) {
                ForEach(payload.weekSchedule) { day in
                    VStack(spacing: 4) {
                        Text(day.dayName)
                            .font(.system(size: 9, weight: .semibold))
                            .foregroundStyle(.secondary)

                        VStack(spacing: 3) {
                            if day.courses.isEmpty {
                                RoundedRectangle(cornerRadius: 3)
                                    .fill(Color.secondary.opacity(0.08))
                                    .frame(height: 24)
                            } else {
                                ForEach(day.courses.prefix(4)) { course in
                                    Text(course.title)
                                        .font(.system(size: 8, weight: .medium))
                                        .foregroundStyle(.white)
                                        .lineLimit(1)
                                        .padding(.horizontal, 2)
                                        .frame(maxWidth: .infinity)
                                        .frame(height: 20)
                                        .background(Color(hex: course.colorHex) ?? .accentColor)
                                        .clipShape(RoundedRectangle(cornerRadius: 3))
                                }
                            }
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            Spacer()
        }
        .padding()
        .containerBackground(for: .widget) {
            Color(nsColor: .windowBackgroundColor)
        }
    }
}
