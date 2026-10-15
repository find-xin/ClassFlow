import SwiftUI

extension Color {
    public init(hex: String, defaultColor: Color = .accentColor) {
        var hexSanitized = hex.trimmingCharacters(in: .whitespacesAndNewlines)
        if hexSanitized.hasPrefix("#") {
            hexSanitized.removeFirst()
        }

        guard hexSanitized.count == 6 || hexSanitized.count == 8 else {
            self = defaultColor
            return
        }

        var rgb: UInt64 = 0
        guard Scanner(string: hexSanitized).scanHexInt64(&rgb) else {
            self = defaultColor
            return
        }

        if hexSanitized.count == 6 {
            let r = Double((rgb & 0xFF0000) >> 16) / 255.0
            let g = Double((rgb & 0x00FF00) >> 8) / 255.0
            let b = Double(rgb & 0x0000FF) / 255.0
            self.init(red: r, green: g, blue: b)
        } else {
            let r = Double((rgb & 0xFF000000) >> 24) / 255.0
            let g = Double((rgb & 0x00FF0000) >> 16) / 255.0
            let b = Double((rgb & 0x0000FF00) >> 8) / 255.0
            let a = Double(rgb & 0x000000FF) / 255.0
            self.init(red: r, green: g, blue: b, opacity: a)
        }
    }

    public func toHex() -> String? {
        guard let components = NSColor(self).usingColorSpace(.sRGB) else { return nil }
        let r = Float(components.redComponent)
        let g = Float(components.greenComponent)
        let b = Float(components.blueComponent)
        return String(format: "#%02lX%02lX%02lX", lroundf(r * 255), lroundf(g * 255), lroundf(b * 255))
    }

    /// Evaluates whether the color has high relative luminance (light/pastel)
    public var isLight: Bool {
        guard let components = NSColor(self).usingColorSpace(.sRGB) else { return false }
        let luminance = 0.299 * components.redComponent + 0.587 * components.greenComponent + 0.114 * components.blueComponent
        return luminance > 0.62
    }

    /// Produces a darker variant suitable for high-contrast text on pastel backgrounds
    public var deepShade: Color {
        guard let components = NSColor(self).usingColorSpace(.sRGB) else { return self }
        let factor: CGFloat = 0.45
        return Color(
            red: Double(components.redComponent * factor),
            green: Double(components.greenComponent * factor),
            blue: Double(components.blueComponent * factor)
        )
    }
}
