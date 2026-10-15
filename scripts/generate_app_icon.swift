import AppKit
import CoreGraphics

func generateAppIcon() {
    let size = CGSize(width: 1024, height: 1024)
    let image = NSImage(size: size)

    image.lockFocus()
    guard let ctx = NSGraphicsContext.current?.cgContext else {
        fatalError("Failed to get CGContext")
    }

    // 1. Transparent canvas
    ctx.clear(CGRect(origin: .zero, size: size))

    // 2. macOS Squircle Bounds (Apple HIG standard: 824x824 centered inside 1024x1024)
    let iconRect = CGRect(x: 100, y: 100, width: 824, height: 824)
    let cornerRadius: CGFloat = 185
    let squirclePath = CGPath(roundedRect: iconRect, cornerWidth: cornerRadius, cornerHeight: cornerRadius, transform: nil)

    // Shadow underneath the squircle
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -28), blur: 48, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.28))
    ctx.addPath(squirclePath)
    ctx.setFillColor(CGColor(red: 0.1, green: 0.1, blue: 0.1, alpha: 0.1))
    ctx.fillPath()
    ctx.restoreGState()

    // 3. Squircle Background: Lush Vibrant Gradient
    ctx.saveGState()
    ctx.addPath(squirclePath)
    ctx.clip()

    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let gradColors = [
        CGColor(red: 1.0, green: 0.35, blue: 0.38, alpha: 1.0), // #FF5A61 Coral Red
        CGColor(red: 0.95, green: 0.28, blue: 0.45, alpha: 1.0),
        CGColor(red: 0.42, green: 0.35, blue: 0.82, alpha: 1.0), // Deep Purple
        CGColor(red: 0.25, green: 0.48, blue: 0.85, alpha: 1.0)  // Indigo Blue
    ] as CFArray
    let gradLocations: [CGFloat] = [0.0, 0.35, 0.75, 1.0]
    if let gradient = CGGradient(colorsSpace: colorSpace, colors: gradColors, locations: gradLocations) {
        ctx.drawLinearGradient(gradient, start: CGPoint(x: 150, y: 900), end: CGPoint(x: 850, y: 150), options: [])
    }

    // Subtle Radial Specular Highlight (top-left)
    if let radialGrad = CGGradient(colorsSpace: colorSpace, colors: [CGColor(red: 1, green: 1, blue: 1, alpha: 0.35), CGColor(red: 1, green: 1, blue: 1, alpha: 0.0)] as CFArray, locations: [0.0, 1.0]) {
        ctx.drawRadialGradient(radialGrad, startCenter: CGPoint(x: 300, y: 800), startRadius: 0, endCenter: CGPoint(x: 300, y: 800), endRadius: 400, options: [])
    }

    // 4. White Timetable Grid Canvas
    let cardRect = CGRect(x: 212, y: 212, width: 600, height: 600)
    let cardPath = CGPath(roundedRect: cardRect, cornerWidth: 54, cornerHeight: 54, transform: nil)

    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -12), blur: 32, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.22))
    ctx.addPath(cardPath)
    ctx.setFillColor(CGColor(red: 0.98, green: 0.98, blue: 1.0, alpha: 0.94))
    ctx.fillPath()
    ctx.restoreGState()

    // Inside Card Details
    ctx.saveGState()
    ctx.addPath(cardPath)
    ctx.clip()

    // Card Top Header Bar
    let headerRect = CGRect(x: 212, y: 700, width: 600, height: 112)
    ctx.setFillColor(CGColor(red: 0.92, green: 0.93, blue: 0.96, alpha: 0.8))
    ctx.fill(headerRect)

    // Three cute circular traffic/month indicators in header
    let dotColors: [(CGFloat, CGFloat, CGFloat)] = [
        (1.0, 0.38, 0.38), // Red
        (1.0, 0.76, 0.28), // Orange
        (0.28, 0.82, 0.48)  // Green
    ]
    for i in 0..<3 {
        let dotRect = CGRect(x: 256 + i * 36, y: 742, width: 18, height: 18)
        ctx.setFillColor(CGColor(red: dotColors[i].0, green: dotColors[i].1, blue: dotColors[i].2, alpha: 1.0))
        ctx.fillEllipse(in: dotRect)
    }

    // Subtle Grid Columns Divider Lines
    ctx.setStrokeColor(CGColor(red: 0.86, green: 0.88, blue: 0.92, alpha: 0.8))
    ctx.setLineWidth(2.5)
    for col in 1...4 {
        let x = 212 + CGFloat(col) * 120
        ctx.strokeLineSegments(between: [CGPoint(x: x, y: 212), CGPoint(x: x, y: 700)])
    }
    // Subtle Grid Horizontal Divider Lines
    for row in 1...3 {
        let y = 212 + CGFloat(row) * 122
        ctx.strokeLineSegments(between: [CGPoint(x: 212, y: y), CGPoint(x: 812, y: y)])
    }

    // 5. Stylized Course Blocks (Vibrant rounded pills in timetable)
    struct CoursePill {
        let rect: CGRect
        let color: (CGFloat, CGFloat, CGFloat)
    }
    let courses = [
        CoursePill(rect: CGRect(x: 232, y: 470, width: 100, height: 210), color: (1.0, 0.36, 0.42)), // Coral Red Course
        CoursePill(rect: CGRect(x: 352, y: 232, width: 100, height: 180), color: (0.18, 0.78, 0.65)), // Mint Green Course
        CoursePill(rect: CGRect(x: 472, y: 410, width: 100, height: 240), color: (0.35, 0.52, 0.88)), // Blue Course
        CoursePill(rect: CGRect(x: 592, y: 510, width: 100, height: 160), color: (0.98, 0.65, 0.22))  // Orange Course
    ]
    for c in courses {
        ctx.saveGState()
        let pillPath = CGPath(roundedRect: c.rect, cornerWidth: 16, cornerHeight: 16, transform: nil)
        ctx.setShadow(offset: CGSize(width: 0, height: -4), blur: 8, color: CGColor(red: c.color.0, green: c.color.1, blue: c.color.2, alpha: 0.35))
        ctx.addPath(pillPath)
        ctx.setFillColor(CGColor(red: c.color.0, green: c.color.1, blue: c.color.2, alpha: 0.95))
        ctx.fillPath()
        ctx.restoreGState()
    }

    ctx.restoreGState() // End card clip

    // 6. Focus / Pomodoro Badge in Bottom Right
    let badgeCenter = CGPoint(x: 750, y: 270)
    let badgeRadius: CGFloat = 86
    let badgeRect = CGRect(x: badgeCenter.x - badgeRadius, y: badgeCenter.y - badgeRadius, width: badgeRadius * 2, height: badgeRadius * 2)

    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -8), blur: 20, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.3))
    ctx.setFillColor(CGColor(red: 1.0, green: 0.34, blue: 0.38, alpha: 1.0))
    ctx.fillEllipse(in: badgeRect)

    // Inner White Ring & Clock Hands
    ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 1.0))
    ctx.setLineWidth(7.0)
    ctx.setLineCap(.round)

    // Clock Circle
    let innerClockRect = badgeRect.insetBy(dx: 22, dy: 22)
    ctx.strokeEllipse(in: innerClockRect)

    // Clock Hands (Pointing at 10:10 - dynamic time)
    ctx.strokeLineSegments(between: [badgeCenter, CGPoint(x: badgeCenter.x - 22, y: badgeCenter.y + 24)]) // Hour hand
    ctx.strokeLineSegments(between: [badgeCenter, CGPoint(x: badgeCenter.x + 36, y: badgeCenter.y + 20)]) // Minute hand
    ctx.restoreGState()

    // 7. Outer Squircle Specular Rim (Apple HIG aesthetic)
    ctx.saveGState()
    ctx.addPath(squirclePath)
    ctx.setLineWidth(2.5)
    ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 0.35))
    ctx.strokePath()
    ctx.restoreGState()

    ctx.restoreGState() // End squircle clip

    image.unlockFocus()

    // 8. Export Iconset PNGs
    guard let tiffData = image.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: tiffData) else {
        fatalError("Failed to convert image to bitmap")
    }

    let fileManager = FileManager.default
    let iconsetDir = URL(fileURLWithPath: "AppIcon.iconset")
    try? fileManager.removeItem(at: iconsetDir)
    try? fileManager.createDirectory(at: iconsetDir, withIntermediateDirectories: true)

    let sizes: [(Int, Int)] = [
        (16, 1), (16, 2),
        (32, 1), (32, 2),
        (128, 1), (128, 2),
        (256, 1), (256, 2),
        (512, 1), (512, 2)
    ]

    for (pt, scale) in sizes {
        let px = pt * scale
        let destSize = NSSize(width: px, height: px)
        let resized = NSImage(size: destSize)
        resized.lockFocus()
        image.draw(in: NSRect(origin: .zero, size: destSize),
                   from: NSRect(origin: .zero, size: size),
                   operation: .copy,
                   fraction: 1.0)
        resized.unlockFocus()

        if let rep = NSBitmapImageRep(data: resized.tiffRepresentation!),
           let pngData = rep.representation(using: .png, properties: [:]) {
            let filename = (scale == 1) ? "icon_\(pt)x\(pt).png" : "icon_\(pt)x\(pt)@\(scale)x.png"
            let targetURL = iconsetDir.appendingPathComponent(filename)
            try? pngData.write(to: targetURL)
        }
    }

    print("Successfully generated all icons in AppIcon.iconset")
}

generateAppIcon()
