import SwiftUI
import AppKit

@MainActor
final class WallpaperImageCache {
    static let shared = WallpaperImageCache()
    private let cache = NSCache<NSString, NSImage>()

    func image(for path: String) -> NSImage? {
        let key = path as NSString
        if let cached = cache.object(forKey: key) {
            return cached
        }
        guard FileManager.default.fileExists(atPath: path),
              let img = NSImage(contentsOfFile: path) else {
            return nil
        }
        cache.setObject(img, forKey: key)
        return img
    }
}

/// Renders seamless, aesthetic wallpapers inspired by WakeUp's signature palettes (Image 3).
/// Uses strict GeometryReader clipping to guarantee that custom wallpaper images never expand
/// the window's layout size or distort columns in small window mode.
public struct WallpaperBackgroundView: View {
    public let settings: AppSettings

    @Environment(\.colorScheme) private var colorScheme

    public init(settings: AppSettings) {
        self.settings = settings
    }

    private var isDarkMode: Bool {
        settings.themeMode == "dark" || (settings.themeMode == "system" && colorScheme == .dark)
    }

    public var body: some View {
        GeometryReader { proxy in
            let w = proxy.size.width
            let h = proxy.size.height

            ZStack {
                if settings.usePureColorBackground {
                    (isDarkMode ? Color(hex: "#0E1117") : Color(nsColor: .windowBackgroundColor))
                } else if let customPath = settings.customBackgroundPath,
                          let nsImage = WallpaperImageCache.shared.image(for: customPath) {
                    Image(nsImage: nsImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: w, height: h)
                        .clipped()
                        .overlay(Color.black.opacity(isDarkMode ? 0.55 : 0.12))
                } else {
                    presetGradient(for: settings.wallpaperPreset)
                        .frame(width: w, height: h)
                }

                // Safety baseboard: prevents vivid or busy wallpapers from ruining text contrast
                Rectangle()
                    .fill(
                        (isDarkMode ? Color.black : Color(nsColor: .windowBackgroundColor))
                            .opacity(settings.usePureColorBackground ? 0 : (isDarkMode ? 0.35 : 0.12))
                    )
                    .frame(width: w, height: h)
            }
            .frame(width: w, height: h)
            .clipped()
        }
        .ignoresSafeArea()
    }

    @ViewBuilder
    private func presetGradient(for preset: String) -> some View {
        if isDarkMode {
            darkPresetGradient(for: preset)
        } else {
            lightPresetGradient(for: preset)
        }
    }

    @ViewBuilder
    private func darkPresetGradient(for preset: String) -> some View {
        switch preset {
        case "aurora":
            LinearGradient(
                colors: [Color(hex: "#0A111C"), Color(hex: "#14223A"), Color(hex: "#0D332F")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "mint":
            LinearGradient(
                colors: [Color(hex: "#0A1412"), Color(hex: "#112620"), Color(hex: "#0E1D19")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "lavender":
            LinearGradient(
                colors: [Color(hex: "#120E1C"), Color(hex: "#1C152B"), Color(hex: "#151120")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "warm_cream":
            LinearGradient(
                colors: [Color(hex: "#161311"), Color(hex: "#221D18"), Color(hex: "#181512")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "midnight":
            LinearGradient(
                colors: [Color(hex: "#08090C"), Color(hex: "#0F1117"), Color(hex: "#060709")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        case "sky":
            LinearGradient(
                colors: [Color(hex: "#09131E"), Color(hex: "#0F1D2E"), Color(hex: "#0B1622")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        default:
            LinearGradient(
                colors: [Color(hex: "#0D1117"), Color(hex: "#161B22"), Color(hex: "#0A0D12")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        }
    }

    @ViewBuilder
    private func lightPresetGradient(for preset: String) -> some View {
        switch preset {
        case "aurora":
            LinearGradient(
                colors: [Color(hex: "#1A2238"), Color(hex: "#283C63"), Color(hex: "#1B998B")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
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
        case "sky":
            LinearGradient(
                colors: [Color(hex: "#E0F2FE"), Color(hex: "#BAE6FD"), Color(hex: "#7DD3FC")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        default:
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
