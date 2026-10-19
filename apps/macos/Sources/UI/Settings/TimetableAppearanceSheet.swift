import SwiftUI
import AppKit
import ClassFlowCore

/// Sheet replicating WakeUp's "课表外观" / 皮肤设置界面 (Images 3 & 4)
public struct TimetableAppearanceSheet: View {
    @Bindable public var appState: AppState
    @Binding public var isPresented: Bool

    public struct SkinPreset: Identifiable {
        public let id: String
        public let name: String
        public let colors: [Color]
    }

    private let presets: [SkinPreset] = [
        SkinPreset(id: "default", name: "默认设置", colors: [Color(hex: "#EEF2F6"), Color(hex: "#E5E9F0")]),
        SkinPreset(id: "aurora", name: "极光", colors: [Color(hex: "#1A2238"), Color(hex: "#283C63"), Color(hex: "#1B998B")]),
        SkinPreset(id: "midnight", name: "星空", colors: [Color(hex: "#0F172A"), Color(hex: "#1E293B")]),
        SkinPreset(id: "warm_cream", name: "暖阳", colors: [Color(hex: "#FFFDF7"), Color(hex: "#F9F3E5")]),
        SkinPreset(id: "mint", name: "薄荷", colors: [Color(hex: "#E8F5E9"), Color(hex: "#C8E6C9")]),
        SkinPreset(id: "sky", name: "天空", colors: [Color(hex: "#E0F2FE"), Color(hex: "#BAE6FD")])
    ]

    public init(appState: AppState, isPresented: Binding<Bool>) {
        self.appState = appState
        self._isPresented = isPresented
    }

    public var body: some View {
        VStack(spacing: 0) {
            headerView
            Divider()
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    skinSection
                    interfaceSection
                    cardSection
                }
                .padding(20)
            }
        }
        .frame(width: 520, height: 680)
        .onChange(of: appState.settings) { _, _ in
            appState.save()
        }
    }

    // MARK: - Subviews

    private var headerView: some View {
        HStack {
            Button(action: { isPresented = false }) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(.primary)
            }
            .buttonStyle(.plain)

            Spacer()

            Text("课表外观")
                .font(.system(size: 16, weight: .bold))

            Spacer()

            Button("完成") {
                isPresented = false
            }
            .buttonStyle(.plain)
            .font(.system(size: 15, weight: .semibold))
            .foregroundColor(.accentColor)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 14)
        .background(.bar)
    }

    private var skinSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("皮肤设置")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
                .padding(.horizontal, 4)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(presets) { preset in
                        let isSelected = (appState.settings.wallpaperPreset == preset.id && appState.settings.customBackgroundPath == nil && !appState.settings.usePureColorBackground)

                        VStack(alignment: .leading, spacing: 6) {
                            ZStack(alignment: .bottomLeading) {
                                LinearGradient(
                                    colors: preset.colors,
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                                .frame(width: 140, height: 210)
                                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))

                                if isSelected {
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .strokeBorder(Color.red, lineWidth: 2.5)

                                    ZStack {
                                        Circle()
                                            .fill(Color.red)
                                            .frame(width: 20, height: 20)
                                        Image(systemName: "checkmark")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundColor(.white)
                                    }
                                    .padding(8)
                                }
                            }

                            Text(preset.name)
                                .font(.system(size: 12.5, weight: isSelected ? .bold : .regular))
                                .foregroundColor(.primary)
                                .padding(.leading, 2)
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                appState.settings.wallpaperPreset = preset.id
                                appState.settings.customBackgroundPath = nil
                                appState.settings.usePureColorBackground = false
                                appState.save()
                            }
                        }
                    }
                }
                .padding(.vertical, 4)
            }
        }
    }

    private var interfaceSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("整体界面")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                settingToggle(title: "使用纯色背景", isOn: $appState.settings.usePureColorBackground)
                Divider().padding(.leading, 16)

                HStack {
                    Text("背景图片")
                        .font(.system(size: 14))
                    Spacer()
                    if appState.settings.customBackgroundPath != nil {
                        Button("恢复默认") {
                            appState.settings.customBackgroundPath = nil
                            appState.settings.wallpaperPreset = "default"
                            appState.settings.usePureColorBackground = false
                            WallpaperImageCache.shared.clearCache()
                            appState.save()
                        }
                        .font(.caption)
                        .buttonStyle(.plain)
                        .foregroundColor(.red)
                        .padding(.trailing, 8)
                    }
                    Button("选择壁纸...") {
                        pickCustomWallpaper()
                    }
                    .font(.caption)
                    .buttonStyle(.bordered)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)

                Divider().padding(.leading, 16)

                HStack {
                    Text("表头颜色")
                        .font(.system(size: 14))
                    Spacer()
                    ColorPicker("", selection: Binding(
                        get: { Color(hex: appState.settings.headerColorHex) },
                        set: { newCol in
                            if let hex = newCol.toHex() {
                                appState.settings.headerColorHex = hex
                                appState.save()
                            }
                        }
                    ))
                    .labelsHidden()
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                Divider().padding(.leading, 16)

                settingToggle(title: "显示网格辅助线", isOn: $appState.settings.showGridGuideLines)
                Divider().padding(.leading, 16)

                settingToggle(title: "节数栏显示时间", isOn: $appState.settings.showSectionTimeInAxis)
                Divider().padding(.leading, 16)

                settingToggle(title: "显示周六", isOn: $appState.settings.showSaturday)
                Divider().padding(.leading, 16)

                settingToggle(title: "显示周日", isOn: $appState.settings.showSunday)
                Divider().padding(.leading, 16)

                settingToggle(title: "显示非本周课程", isOn: $appState.settings.showNonCurrentWeekCourses)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)
        }
    }

    private var cardSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("课程格子")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.secondary)
                .padding(.horizontal, 4)

            VStack(spacing: 0) {
                HStack {
                    Text("文字颜色")
                        .font(.system(size: 14))
                    Spacer()
                    Picker("", selection: $appState.settings.cardTextColorMode) {
                        Text("自动").tag("auto")
                        Text("浅色").tag("light")
                        Text("深色").tag("dark")
                    }
                    .labelsHidden()
                    .pickerStyle(.segmented)
                    .frame(width: 160)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                Divider().padding(.leading, 16)

                HStack {
                    Text("边框颜色")
                        .font(.system(size: 14))
                    Spacer()
                    Picker("", selection: $appState.settings.cardBorderColorMode) {
                        Text("默认").tag("none")
                        Text("与格子同色").tag("cardColor")
                        Text("浅灰细线").tag("subtle")
                    }
                    .labelsHidden()
                    .pickerStyle(.segmented)
                    .frame(width: 190)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                Divider().padding(.leading, 16)

                sliderRow(title: "格子高度", value: $appState.settings.gridHeight, inRange: 48...96, step: 2, unit: "")
                Divider().padding(.leading, 16)

                sliderRow(title: "圆角半径", value: $appState.settings.cardCornerRadius, inRange: 0...16, step: 1, unit: "")
                Divider().padding(.leading, 16)

                sliderRow(title: "不透明度", value: $appState.settings.cardOpacity, inRange: 0.2...1.0, step: 0.05, unit: "%", multiplier: 100)
                Divider().padding(.leading, 16)

                sliderRow(title: "非本周课程不透明比", value: $appState.settings.nonCurrentWeekOpacityRatio, inRange: 0.1...1.0, step: 0.05, unit: "%", multiplier: 100)
                Divider().padding(.leading, 16)

                sliderRow(title: "字体大小", value: $appState.settings.cardFontSize, inRange: 10...18, step: 1, unit: "")
                Divider().padding(.leading, 16)

                settingToggle(title: "格子内显示上课时间", isOn: $appState.settings.showTimeInCard)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子内显示上课地点", isOn: $appState.settings.showLocationInCard)
                Divider().padding(.leading, 16)

                settingToggle(title: "教室前显示 @", isOn: $appState.settings.showAtPrefixForRoom)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子前显示授课老师", isOn: $appState.settings.showTeacherInCard)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子内显示备注", isOn: $appState.settings.showNotesInCard)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子文字水平居中", isOn: $appState.settings.cardTextHorizontalCenter)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子文字垂直居中", isOn: $appState.settings.cardTextVerticalCenter)
                Divider().padding(.leading, 16)

                settingToggle(title: "格子使用虚线边框", isOn: $appState.settings.cardUseDottedBorder)
                Divider().padding(.leading, 16)

                settingToggle(title: "边框使用格子颜色", isOn: $appState.settings.cardBorderUseCardColor)
                Divider().padding(.leading, 16)

                settingToggle(title: "课程文字颜色叠加格子颜色", isOn: $appState.settings.cardTextBlendCardColor)
            }
            .background(Color(nsColor: .controlBackgroundColor))
            .cornerRadius(12)
        }
    }

    private func sliderRow(title: String, value: Binding<Double>, inRange: ClosedRange<Double>, step: Double, unit: String, multiplier: Double = 1.0) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 14))
            Spacer()
            Slider(value: value, in: inRange, step: step)
                .frame(width: 140)
            Text("\(Int(value.wrappedValue * multiplier))\(unit)")
                .font(.system(size: 13, weight: .regular, design: .monospaced))
                .frame(width: 44, alignment: .trailing)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    private func settingToggle(title: String, isOn: Binding<Bool>) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 14))
            Spacer()
            Toggle("", isOn: isOn)
                .labelsHidden()
                .toggleStyle(.switch)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
    }

    private func pickCustomWallpaper() {
        let panel = NSOpenPanel()
        panel.canChooseFiles = true
        panel.canChooseDirectories = false
        panel.allowsMultipleSelection = false
        panel.allowedContentTypes = [.image]
        if panel.runModal() == .OK, let url = panel.url {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first ?? FileManager.default.temporaryDirectory
            let appDir = appSupport.appendingPathComponent("ClassFlow", isDirectory: true)
            try? FileManager.default.createDirectory(at: appDir, withIntermediateDirectories: true)
            let destUrl = appDir.appendingPathComponent("custom_wallpaper.\(url.pathExtension)")
            try? FileManager.default.removeItem(at: destUrl)
            do {
                try FileManager.default.copyItem(at: url, to: destUrl)
                WallpaperImageCache.shared.clearCache()
                appState.settings.customBackgroundPath = destUrl.path
            } catch {
                WallpaperImageCache.shared.clearCache()
                appState.settings.customBackgroundPath = url.path
            }
            appState.settings.wallpaperPreset = "custom"
            appState.settings.usePureColorBackground = false
            appState.save()
        }
    }
}
