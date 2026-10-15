import SwiftUI
import AppKit

/// Google Calendar style color picker.
/// The main row displays clean circular swatches and an "Add custom color" (+) button.
/// Clicking the (+) button presents a Google Calendar style custom color popover
/// featuring a 2D Saturation-Brightness gradient box, rainbow Hue slider,
/// live preview circle, HEX text input, and Cancel/Save buttons.
public struct CompactColorPickerRow: View {
    @Binding public var selectedHex: String
    public let presets: [String]

    @State private var isShowingCustomDialog: Bool = false
    @State private var draftHue: Double = 0.58
    @State private var draftSat: Double = 0.65
    @State private var draftBri: Double = 0.85
    @State private var draftHexInput: String = "4F46E5"

    public static let defaultPresets: [String] = [
        "#FF5A5F", // Coral
        "#4F46E5", // Indigo
        "#0EA5E9", // Sky Blue
        "#10B981", // Emerald Green
        "#8B5CF6", // Purple
        "#F59E0B", // Amber
        "#EC4899", // Rose Pink
        "#64748B"  // Slate Blue
    ]

    public init(
        selectedHex: Binding<String>,
        presets: [String] = CompactColorPickerRow.defaultPresets
    ) {
        self._selectedHex = selectedHex
        self.presets = presets
    }

    private var normalizedSelected: String {
        selectedHex.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "").uppercased()
    }

    private var isCustomColor: Bool {
        let current = normalizedSelected
        return !presets.contains { $0.replacingOccurrences(of: "#", with: "").uppercased() == current }
    }

    private var draftColor: Color {
        Color(hue: draftHue, saturation: draftSat, brightness: draftBri)
    }

    private var currentDraftHex: String {
        let ns = NSColor(
            calibratedHue: CGFloat(draftHue),
            saturation: CGFloat(draftSat),
            brightness: CGFloat(draftBri),
            alpha: 1.0
        )
        if let rgb = ns.usingColorSpace(.sRGB) {
            let r = Int(round(rgb.redComponent * 255))
            let g = Int(round(rgb.greenComponent * 255))
            let b = Int(round(rgb.blueComponent * 255))
            return String(format: "#%02X%02X%02X", r, g, b)
        }
        return "#" + draftHexInput.uppercased()
    }

    public var body: some View {
        HStack(spacing: 8) {
            // 1. Preset Swatches (8 Circles)
            ForEach(presets, id: \.self) { presetHex in
                let cleanPreset = presetHex.replacingOccurrences(of: "#", with: "").uppercased()
                let isSelected = (normalizedSelected == cleanPreset)

                Circle()
                    .fill(Color(hex: presetHex))
                    .frame(width: 22, height: 22)
                    .overlay(
                        Circle()
                            .strokeBorder(Color.white, lineWidth: isSelected ? 2.5 : 0)
                    )
                    .shadow(color: Color.black.opacity(isSelected ? 0.25 : 0), radius: 2, y: 1)
                    .contentShape(Circle())
                    .onTapGesture {
                        selectedHex = presetHex
                    }
            }

            // 2. Custom Color Swatch (visible if a custom color is currently selected)
            if isCustomColor {
                Circle()
                    .fill(Color(hex: selectedHex))
                    .frame(width: 22, height: 22)
                    .overlay(
                        Circle()
                            .strokeBorder(Color.white, lineWidth: 2.5)
                    )
                    .shadow(color: Color.black.opacity(0.25), radius: 2, y: 1)
                    .help("当前自定义颜色 (\(selectedHex))")
            }

            // 3. Google Calendar "+" Add Custom Color Button
            Button {
                prepareDraftColor()
                isShowingCustomDialog = true
            } label: {
                Circle()
                    .fill(Color.primary.opacity(0.06))
                    .frame(width: 22, height: 22)
                    .overlay(
                        Circle()
                            .strokeBorder(Color.primary.opacity(0.2), lineWidth: 1)
                    )
                    .overlay(
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.secondary)
                    )
            }
            .buttonStyle(.plain)
            .help("添加自定义颜色 (Google Calendar 风格)")
            .popover(isPresented: $isShowingCustomDialog, arrowEdge: .top) {
                googleCalendarColorDialog
            }
        }
    }

    // MARK: - Google Calendar Style Custom Color Dialog

    private var googleCalendarColorDialog: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("选择自定义颜色")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.primary)

            // 1. 2D Saturation-Brightness Spectrum Box
            SaturationBrightnessPicker(
                hue: $draftHue,
                saturation: $draftSat,
                brightness: $draftBri
            )
            .onChange(of: draftSat) { _, _ in syncHexFromHSB() }
            .onChange(of: draftBri) { _, _ in syncHexFromHSB() }

            // 2. Rainbow Hue Slider
            HueSlider(hue: $draftHue)
                .onChange(of: draftHue) { _, _ in syncHexFromHSB() }

            // 3. Color Preview & HEX Code Input
            HStack(spacing: 8) {
                Circle()
                    .fill(draftColor)
                    .frame(width: 26, height: 26)
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1.5))
                    .shadow(color: .black.opacity(0.15), radius: 2)

                Text("HEX")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.secondary)

                HStack(spacing: 2) {
                    Text("#")
                        .font(.system(size: 11.5, weight: .bold, design: .monospaced))
                        .foregroundColor(.secondary)

                    TextField("4378DB", text: $draftHexInput)
                        .textFieldStyle(.plain)
                        .font(.system(size: 11.5, weight: .medium, design: .monospaced))
                        .frame(width: 62)
                        .padding(.horizontal, 4)
                        .padding(.vertical, 3)
                        .background(
                            RoundedRectangle(cornerRadius: 6, style: .continuous)
                                .fill(Color.primary.opacity(0.06))
                                .overlay(
                                    RoundedRectangle(cornerRadius: 6, style: .continuous)
                                        .strokeBorder(Color.white.opacity(0.15), lineWidth: 0.5)
                                )
                        )
                        .onChange(of: draftHexInput) { _, newValue in
                            syncHSBFromHex(newValue)
                        }
                }

                Spacer()
            }
            .padding(.vertical, 2)

            Divider()

            // 4. Cancel & Save Action Buttons
            HStack {
                Button("取消") {
                    isShowingCustomDialog = false
                }
                .buttonStyle(.plain)
                .font(.system(size: 12))
                .foregroundColor(.secondary)

                Spacer()

                Button("保存") {
                    selectedHex = currentDraftHex
                    isShowingCustomDialog = false
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.small)
            }
        }
        .padding(14)
        .frame(width: 220)
    }

    private func prepareDraftColor() {
        let clean = normalizedSelected
        if clean.count == 6 {
            draftHexInput = clean
            syncHSBFromHex(clean)
        } else {
            draftHexInput = "4F46E5"
            syncHSBFromHex("4F46E5")
        }
    }

    private func syncHexFromHSB() {
        let ns = NSColor(
            calibratedHue: CGFloat(draftHue),
            saturation: CGFloat(draftSat),
            brightness: CGFloat(draftBri),
            alpha: 1.0
        )
        if let rgb = ns.usingColorSpace(.sRGB) {
            let r = Int(round(rgb.redComponent * 255))
            let g = Int(round(rgb.greenComponent * 255))
            let b = Int(round(rgb.blueComponent * 255))
            let hex = String(format: "%02X%02X%02X", r, g, b)
            if draftHexInput.uppercased() != hex {
                draftHexInput = hex
            }
        }
    }

    private func syncHSBFromHex(_ hexStr: String) {
        let clean = hexStr.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "#", with: "")
        guard clean.count == 6, let rgb = UInt64(clean, radix: 16) else { return }

        let r = CGFloat((rgb & 0xFF0000) >> 16) / 255.0
        let g = CGFloat((rgb & 0x00FF00) >> 8) / 255.0
        let b = CGFloat(rgb & 0x0000FF) / 255.0

        let nsColor = NSColor(red: r, green: g, blue: b, alpha: 1.0)
        var h: CGFloat = 0, s: CGFloat = 0, br: CGFloat = 0, a: CGFloat = 0
        nsColor.getHue(&h, saturation: &s, brightness: &br, alpha: &a)

        self.draftHue = Double(h)
        self.draftSat = Double(s)
        self.draftBri = Double(br)
    }
}

/// 2D Saturation & Brightness gradient picker
private struct SaturationBrightnessPicker: View {
    @Binding var hue: Double
    @Binding var saturation: Double
    @Binding var brightness: Double

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            ZStack(alignment: .topLeading) {
                // Hue base
                Color(hue: hue, saturation: 1.0, brightness: 1.0)

                // White gradient (Saturation from left)
                LinearGradient(
                    gradient: Gradient(colors: [.white, .clear]),
                    startPoint: .leading,
                    endPoint: .trailing
                )

                // Black gradient (Brightness from top)
                LinearGradient(
                    gradient: Gradient(colors: [.clear, .black]),
                    startPoint: .top,
                    endPoint: .bottom
                )

                // Draggable indicator circle
                let thumbX = max(0, min(size.width, CGFloat(saturation) * size.width))
                let thumbY = max(0, min(size.height, CGFloat(1 - brightness) * size.height))

                Circle()
                    .strokeBorder(Color.white, lineWidth: 2)
                    .background(Circle().fill(Color(hue: hue, saturation: saturation, brightness: brightness)))
                    .frame(width: 14, height: 14)
                    .shadow(color: .black.opacity(0.4), radius: 2)
                    .position(x: thumbX, y: thumbY)
            }
            .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let s = max(0, min(1, value.location.x / size.width))
                        let b = max(0, min(1, 1 - (value.location.y / size.height)))
                        self.saturation = Double(s)
                        self.brightness = Double(b)
                    }
            )
        }
        .frame(height: 120)
    }
}

/// Rainbow Hue slider
private struct HueSlider: View {
    @Binding var hue: Double

    private let rainbowColors: [Color] = [
        Color(hue: 0.0, saturation: 1, brightness: 1),
        Color(hue: 0.17, saturation: 1, brightness: 1),
        Color(hue: 0.33, saturation: 1, brightness: 1),
        Color(hue: 0.5, saturation: 1, brightness: 1),
        Color(hue: 0.67, saturation: 1, brightness: 1),
        Color(hue: 0.83, saturation: 1, brightness: 1),
        Color(hue: 1.0, saturation: 1, brightness: 1)
    ]

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            ZStack(alignment: .leading) {
                LinearGradient(
                    gradient: Gradient(colors: rainbowColors),
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .clipShape(Capsule())

                let thumbX = max(6, min(size.width - 6, CGFloat(hue) * size.width))

                Circle()
                    .fill(Color.white)
                    .frame(width: 14, height: 14)
                    .shadow(color: .black.opacity(0.35), radius: 2)
                    .position(x: thumbX, y: size.height / 2)
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let h = max(0, min(1, value.location.x / size.width))
                        self.hue = Double(h)
                    }
            )
        }
        .frame(height: 12)
    }
}
