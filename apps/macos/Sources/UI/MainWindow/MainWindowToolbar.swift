import SwiftUI
import ClassFlowCore

/// Native top toolbar containing semester switcher, week navigator, and action shortcuts.
public struct MainWindowToolbar: View {
    @Bindable public var appState: AppState

    public init(appState: AppState) {
        self.appState = appState
    }

    public var body: some View {
        HStack(spacing: 12) {
            // 1. Semester Selector PopUp
            if !appState.semesters.isEmpty {
                Menu {
                    ForEach(appState.semesters) { sem in
                        Button(action: { appState.selectSemester(id: sem.id) }) {
                            HStack {
                                Text(sem.name)
                                if sem.id == appState.currentSemesterId {
                                    Image(systemName: "checkmark")
                                }
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "books.vertical.fill")
                            .font(.system(size: 11))
                        Text(appState.currentSemester?.name ?? "选择学期")
                            .font(.system(size: 13, weight: .semibold))
                    }
                }
                .menuStyle(.borderlessButton)
            }

            Spacer()

            // 2. Week Navigation Group: [<] 第 N 周 (本周) [>] [回到本周]
            HStack(spacing: 6) {
                Button(action: { appState.previousWeek() }) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 11, weight: .bold))
                }
                .buttonStyle(.bordered)
                .help("上一周 (Cmd+Left)")
                .keyboardShortcut(.leftArrow, modifiers: .command)

                // Week selector popup menu
                Menu {
                    if let sem = appState.currentSemester {
                        ForEach(1...sem.totalWeeks, id: \.self) { w in
                            Button("第 \(w) 周" + (w == appState.calculatedCurrentWeek ? " (本周)" : "")) {
                                appState.selectedWeek = w
                            }
                        }
                    }
                } label: {
                    HStack(spacing: 4) {
                        Text("第 \(appState.selectedWeek) 周")
                            .font(.system(size: 13, weight: .bold))
                        if appState.isViewingCurrentWeek {
                            Text("当前周")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(.accentColor)
                                .padding(.horizontal, 4)
                                .padding(.vertical, 1)
                                .background(Color.accentColor.opacity(0.12))
                                .clipShape(Capsule())
                        }
                    }
                }
                .menuStyle(.borderlessButton)

                Button(action: { appState.nextWeek() }) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 11, weight: .bold))
                }
                .buttonStyle(.bordered)
                .help("下一周 (Cmd+Right)")
                .keyboardShortcut(.rightArrow, modifiers: .command)

                if !appState.isViewingCurrentWeek {
                    Button(action: { appState.jumpToCurrentWeek() }) {
                        Text("回到本周")
                            .font(.system(size: 11, weight: .medium))
                    }
                    .buttonStyle(.bordered)
                    .help("返回当前教学周 (Cmd+T)")
                    .keyboardShortcut("t", modifiers: .command)
                }
            }

            Spacer()

            // 3. Actions: Add Course [+] and More Options [...]
            HStack(spacing: 8) {
                Button(action: { appState.isAddingCourse = true }) {
                    Label("添加课程", systemImage: "plus")
                        .font(.system(size: 12, weight: .medium))
                }
                .buttonStyle(.borderedProminent)
                .keyboardShortcut("n", modifiers: .command)

                Menu {
                    Toggle("显示周末", isOn: $appState.settings.showWeekends)
                    Divider()
                    Button("偏好设置...") {
                        appState.isShowingSettings = true
                    }
                    .keyboardShortcut(",", modifiers: .command)
                } label: {
                    Image(systemName: "ellipsis.circle")
                        .font(.system(size: 14))
                }
                .menuStyle(.borderlessButton)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.bar)
    }
}
