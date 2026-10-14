import SwiftUI
import AppKit

/// Renders seamless, aesthetic wallpapers inspired by WakeUp's signature dreamy palettes.
public struct WallpaperBackgroundView: View {
    public let settings: AppSettings

    public init(settings: AppSettings) {
        self.settings = settings
    }

    public var body: some View {
        ZStack {
            if let customPath = settings.customBackgroundPath,
               let nsImage = NSImage(contentsOfFile: customPath) {
                Image(nsImage: nsImage)
                    .resizable()
                    .scaledToFill()
                    .overlay(Color.black.opacity(0.12))
            } else {
                presetGradient(for: settings.wallpaperPreset)
            }
        }
    }

    @ViewBuilder
    private func presetGradient(for preset: String) -> some View {
        switch preset {
        case "mint":
            LinearGradient(
                colors: [Color(hex: "#C6E8DB"), Color(hex: "#DDF4EC"), Color(hex: "#C3E3D6")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "lavender":
            LinearGradient(
                colors: [Color(hex: "#D6CAEC"), Color(hex: "#ECE4F7"), Color(hex: "#CFBFE8")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "warm_cream":
            LinearGradient(
                colors: [Color(hex: "#EFE0CE"), Color(hex: "#F9F2E7"), Color(hex: "#E8D5BF")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "midnight":
            LinearGradient(
                colors: [Color(hex: "#151821"), Color(hex: "#1E2433"), Color(hex: "#11141B")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        default:
            // "wakeup_sky": Exact soft sky-blue gradient sampled from WakeUp screenshot
            LinearGradient(
                colors: [
                    Color(hex: "#B7D2EC"),
                    Color(hex: "#CFE1F6"),
                    Color(hex: "#B9CCE6")
                ],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }
}
