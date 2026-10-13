import SwiftUI

/// Frosted translucent background material for course cards and panels.
public struct FrostedCardBackground: View {
    public let color: Color
    public let opacity: Double
    public let cornerRadius: CGFloat

    public init(
        color: Color,
        opacity: Double = 0.88,
        cornerRadius: CGFloat = 8
    ) {
        self.color = color
        self.opacity = opacity
        self.cornerRadius = cornerRadius
    }

    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(.ultraThinMaterial)
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(color.opacity(opacity))
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(Color.white.opacity(0.15), lineWidth: 1)
        }
    }
}
