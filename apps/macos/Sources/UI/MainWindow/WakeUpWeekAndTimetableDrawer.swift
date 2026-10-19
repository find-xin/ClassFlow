import SwiftUI
import ClassFlowCore

/// Floating bottom-left drawer replicating WakeUp's Week Slider and Timetable Switcher card.
public struct WakeUpWeekAndTimetableDrawer: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    public init(appState: AppState, isPresented: Binding<Bool>) {
        self.appState = appState
        self._isPresented = isPresented
    }

    private var totalWeeks: Int {
        max(1, appState.currentSemester?.totalWeeks ?? 20)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // 1. Header: "周数" & "分享当前课表" / "回到本周"
            HStack {
                Text("周数")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                if !appState.isViewingCurrentWeek {
                    Button(action: {
                        appState.jumpToCurrentWeek()
                    }) {
                        Text("回到本周")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                    }
                    .buttonStyle(.plain)
                } else {
                    Button(action: {
                        exportICSDialog()
                    }) {
                        Text("分享当前课表")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                    }
                    .buttonStyle(.plain)
                }
            }

            // 2. Custom Week Slider with Floating White Pill (Displaying Week Number inside)
            VStack(spacing: 4) {
                GeometryReader { geo in
                    let trackWidth = max(10, geo.size.width - 44)
                    let step = trackWidth / CGFloat(max(1, totalWeeks - 1))
                    let currentOffset = CGFloat(appState.selectedWeek - 1) * step

                    ZStack(alignment: .leading) {
                        // Track background
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.primary.opacity(0.04), Color.primary.opacity(0.08)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(height: 28)

                        // Floating white thumb with shadow
                        HStack {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Color(NSColor.controlBackgroundColor))
                                    .frame(width: 44, height: 26)
                                    .shadow(color: Color.black.opacity(0.16), radius: 4, x: 0, y: 1)

                                Text("\(appState.selectedWeek)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.primary)
                            }
                            .offset(x: min(max(0, currentOffset), trackWidth))
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let clampedX = max(0, min(trackWidth, value.location.x - 22))
                                let progress = clampedX / max(1, trackWidth)
                                let targetWeek = Int(round(progress * CGFloat(totalWeeks - 1))) + 1
                                appState.selectedWeek = max(1, min(totalWeeks, targetWeek))
                            }
                    )
                }
                .frame(height: 28)

                // Week Range Marks
                HStack {
                    Text("第1周")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("第\(totalWeeks)周")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }

            // 3. Switch Timetable Header & Actions
            HStack {
                Text("切换课表")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                HStack(spacing: 10) {
                    Button(action: {
                        isPresented = false
                        appState.isShowingCreateSemester = true
                    }) {
                        Text("新建课表")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        isPresented = false
                        appState.isShowingManageSemesters = true
                    }) {
                        Text("管理")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }

            // 4. Horizontal Scroll of Timetable Cards (Matching WakeUp card styles)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(appState.semesters) { sem in
                        let isSelected = (sem.id == appState.currentSemesterId)

                        Button(action: {
                            appState.selectSemester(id: sem.id)
                        }) {
                            VStack(spacing: 6) {
                                ZStack {
                                    if isSelected {
                                        // Dark card with white checkmark (Exact WakeUp style)
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(Color(hex: "#2C2C2E"))
                                            .frame(width: 68, height: 62)
                                            .shadow(color: Color.black.opacity(0.12), radius: 3, y: 1)

                                        Image(systemName: "checkmark")
                                            .font(.system(size: 22, weight: .bold))
                                            .foregroundColor(.white)
                                    } else {
                                        // Aesthetic gradient thumbnail for other timetables
                                        RoundedRectangle(cornerRadius: 12, style: .continuous)
                                            .fill(
                                                LinearGradient(
                                                    colors: [Color(hex: "#5B7FB5"), Color(hex: "#7A68A5")],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                )
                                            )
                                            .frame(width: 68, height: 62)
                                            .shadow(color: Color.black.opacity(0.08), radius: 2, y: 1)

                                        Image(systemName: "calendar")
                                            .font(.system(size: 18))
                                            .foregroundColor(.white.opacity(0.85))
                                    }
                                }

                                Text(sem.name)
                                    .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .primary : .secondary)
                                    .frame(width: 68)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            if !isSelected {
                                Button("设为主课表") {
                                    appState.selectSemester(id: sem.id)
                                }
                            }
                            Button("课表管理...") {
                                isPresented = false
                                appState.isShowingManageSemesters = true
                            }
                            if appState.semesters.count > 1 {
                                Button("删除此课表", role: .destructive) {
                                    appState.deleteSemester(id: sem.id)
                                }
                            }
                        }
                    }

                    // Add Timetable Card Button
                    Button(action: {
                        isPresented = false
                        appState.isShowingCreateSemester = true
                    }) {
                        VStack(spacing: 6) {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(Color.secondary.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [4, 4]))
                                    .frame(width: 68, height: 62)

                                Image(systemName: "plus")
                                    .font(.system(size: 18, weight: .medium))
                                    .foregroundColor(.secondary)
                            }

                            Text("新建")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .padding(.vertical, 2)
            }
        }
        .padding(16)
        .frame(width: 360)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(.ultraThinMaterial)
                .overlay(
                    RoundedRectangle(cornerRadius: 22, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.2), lineWidth: 0.5)
                )
                .shadow(color: Color.black.opacity(0.12), radius: 16, x: 0, y: 6)
        )
    }

    @MainActor
    private func exportICSDialog() {
        guard let icsText = appState.exportCurrentSemesterICS() else { return }
        let panel = NSSavePanel()
        panel.nameFieldStringValue = "\(appState.currentSemester?.name ?? "ClassFlow").ics"
        if panel.runModal() == .OK, let url = panel.url {
            try? icsText.write(to: url, atomically: true, encoding: .utf8)
        }
    }
}

/// In-place popover anchored to the top center week button, providing instant week slider,
/// quick week jump pills (1-20+), "回到本周", and semester timetable switching.
public struct WakeUpWeekPopoverView: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    public init(appState: AppState, isPresented: Binding<Bool>) {
        self.appState = appState
        self._isPresented = isPresented
    }

    private var totalWeeks: Int {
        max(1, appState.currentSemester?.totalWeeks ?? 20)
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // 1. Header: "周次选择" & "回到本周"
            HStack {
                Text("周次选择")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                if !appState.isViewingCurrentWeek {
                    Button(action: {
                        appState.jumpToCurrentWeek()
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.uturn.backward")
                            Text("回到本周")
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "#FF5A5F"))
                    }
                    .buttonStyle(.plain)
                } else {
                    Text("当前正是本周")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }

            // 2. Custom Week Slider with Floating Thumb
            VStack(spacing: 4) {
                GeometryReader { geo in
                    let trackWidth = max(10, geo.size.width - 44)
                    let step = trackWidth / CGFloat(max(1, totalWeeks - 1))
                    let currentOffset = CGFloat(appState.selectedWeek - 1) * step

                    ZStack(alignment: .leading) {
                        // Track background
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(
                                LinearGradient(
                                    colors: [Color.primary.opacity(0.04), Color.primary.opacity(0.08)],
                                    startPoint: .leading,
                                    endPoint: .trailing
                                )
                            )
                            .frame(height: 28)

                        // Floating thumb
                        HStack {
                            ZStack {
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Color(NSColor.controlBackgroundColor))
                                    .frame(width: 44, height: 26)
                                    .shadow(color: Color.black.opacity(0.16), radius: 4, x: 0, y: 1)

                                Text("\(appState.selectedWeek)")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.primary)
                            }
                            .offset(x: min(max(0, currentOffset), trackWidth))
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                let clampedX = max(0, min(trackWidth, value.location.x - 22))
                                let progress = clampedX / max(1, trackWidth)
                                let targetWeek = Int(round(progress * CGFloat(totalWeeks - 1))) + 1
                                appState.selectedWeek = max(1, min(totalWeeks, targetWeek))
                            }
                    )
                }
                .frame(height: 28)

                // Marks
                HStack {
                    Text("第1周")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.secondary)
                    Spacer()
                    Text("第\(totalWeeks)周")
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundColor(.secondary)
                }
            }

            // 3. Quick Week Jump Pills Grid
            LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: 6), count: 5), spacing: 6) {
                ForEach(1...max(1, totalWeeks), id: \.self) { w in
                    let isSel = (w == appState.selectedWeek)
                    let isCur = (w == appState.calculatedCurrentWeek)

                    Button(action: {
                        appState.selectedWeek = w
                    }) {
                        ZStack(alignment: .topTrailing) {
                            Text("\(w)")
                                .font(.system(size: 12, weight: isSel ? .bold : .medium))
                                .foregroundColor(isSel ? .white : .primary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 28)
                                .background(
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .fill(isSel ? Color(hex: "#FF5A5F") : Color.primary.opacity(0.05))
                                )

                            if isCur && !isSel {
                                Circle()
                                    .fill(Color(hex: "#FF5A5F"))
                                    .frame(width: 5, height: 5)
                                    .padding(4)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                }
            }

            Divider()

            // 4. Semester Timetable Switcher Header
            HStack {
                Text("切换课表")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.primary)

                Spacer()

                HStack(spacing: 8) {
                    Button(action: {
                        isPresented = false
                        appState.isShowingCreateSemester = true
                    }) {
                        Text("新建课表")
                            .font(.system(size: 11.5, weight: .semibold))
                            .foregroundColor(Color(hex: "#FF5A5F"))
                    }
                    .buttonStyle(.plain)

                    Button(action: {
                        isPresented = false
                        appState.isShowingManageSemesters = true
                    }) {
                        Text("管理")
                            .font(.system(size: 11.5, weight: .medium))
                            .foregroundColor(.secondary)
                    }
                    .buttonStyle(.plain)
                }
            }

            // 5. Horizontal Scroll of Semester Cards
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(appState.semesters) { sem in
                        let isSelected = (sem.id == appState.currentSemesterId)

                        Button(action: {
                            appState.selectSemester(id: sem.id)
                        }) {
                            VStack(spacing: 5) {
                                ZStack {
                                    if isSelected {
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .fill(Color(hex: "#2C2C2E"))
                                            .frame(width: 60, height: 52)
                                            .shadow(color: Color.black.opacity(0.12), radius: 3, y: 1)

                                        Image(systemName: "checkmark")
                                            .font(.system(size: 18, weight: .bold))
                                            .foregroundColor(.white)
                                    } else {
                                        RoundedRectangle(cornerRadius: 10, style: .continuous)
                                            .fill(
                                                LinearGradient(
                                                    colors: [Color(hex: "#5B7FB5"), Color(hex: "#7A68A5")],
                                                    startPoint: .topLeading,
                                                    endPoint: .bottomTrailing
                                                )
                                            )
                                            .frame(width: 60, height: 52)

                                        Image(systemName: "calendar")
                                            .font(.system(size: 16))
                                            .foregroundColor(.white.opacity(0.85))
                                    }
                                }

                                Text(sem.name)
                                    .font(.system(size: 10.5, weight: isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? .primary : .secondary)
                                    .frame(width: 60)
                                    .lineLimit(1)
                            }
                        }
                        .buttonStyle(.plain)
                        .contextMenu {
                            if !isSelected {
                                Button("设为主课表") {
                                    appState.selectSemester(id: sem.id)
                                }
                            }
                            Button("课表管理...") {
                                isPresented = false
                                appState.isShowingManageSemesters = true
                            }
                            if appState.semesters.count > 1 {
                                Button("删除此课表", role: .destructive) {
                                    appState.deleteSemester(id: sem.id)
                                }
                            }
                        }
                    }
                }
                .padding(.vertical, 2)
            }
        }
        .padding(16)
        .frame(width: 360)
    }
}
