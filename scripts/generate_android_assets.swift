import AppKit
import CoreGraphics

func createCGContext(width: Int, height: Int) -> CGContext {
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue
    guard let ctx = CGContext(
        data: nil,
        width: width,
        height: height,
        bitsPerComponent: 8,
        bytesPerRow: width * 4,
        space: colorSpace,
        bitmapInfo: bitmapInfo
    ) else {
        fatalError("Failed to create CGContext")
    }
    return ctx
}

func savePNG(ctx: CGContext, to path: String) {
    guard let cgImage = ctx.makeImage() else {
        fatalError("Failed to make CGImage for \(path)")
    }
    let rep = NSBitmapImageRep(cgImage: cgImage)
    guard let pngData = rep.representation(using: .png, properties: [:]) else {
        fatalError("Failed to convert image to PNG for \(path)")
    }
    let url = URL(fileURLWithPath: path)
    try? FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
    try! pngData.write(to: url)
    print("Saved: \(path)")
}

// MARK: - 1. Generate App Icons (Square & Round)
func drawAppIcon(size: CGSize, isRound: Bool) -> CGContext {
    let width = Int(size.width)
    let height = Int(size.height)
    let ctx = createCGContext(width: width, height: height)

    let rect = CGRect(origin: .zero, size: size)

    // Clip to shape
    ctx.saveGState()
    if isRound {
        let circlePath = CGPath(ellipseIn: rect.insetBy(dx: size.width * 0.02, dy: size.height * 0.02), transform: nil)
        ctx.addPath(circlePath)
        ctx.clip()
    } else {
        let cornerRadius = size.width * 0.22
        let squirclePath = CGPath(roundedRect: rect, cornerWidth: cornerRadius, cornerHeight: cornerRadius, transform: nil)
        ctx.addPath(squirclePath)
        ctx.clip()
    }

    // Lush Coral-Purple Gradient Background
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let gradColors = [
        CGColor(red: 1.0, green: 0.35, blue: 0.38, alpha: 1.0), // Coral Red
        CGColor(red: 0.95, green: 0.28, blue: 0.45, alpha: 1.0),
        CGColor(red: 0.42, green: 0.35, blue: 0.82, alpha: 1.0), // Purple
        CGColor(red: 0.25, green: 0.48, blue: 0.85, alpha: 1.0)  // Indigo Blue
    ] as CFArray
    let gradLocations: [CGFloat] = [0.0, 0.35, 0.75, 1.0]
    if let gradient = CGGradient(colorsSpace: colorSpace, colors: gradColors, locations: gradLocations) {
        ctx.drawLinearGradient(gradient, start: CGPoint(x: 0, y: size.height), end: CGPoint(x: size.width, y: 0), options: [])
    }

    // Inner White Timetable Card
    let cardInset = size.width * 0.14
    let cardRect = rect.insetBy(dx: cardInset, dy: cardInset)
    let cardPath = CGPath(roundedRect: cardRect, cornerWidth: size.width * 0.08, cornerHeight: size.width * 0.08, transform: nil)
    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -size.height * 0.02), blur: size.width * 0.05, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.25))
    ctx.setFillColor(CGColor(red: 0.98, green: 0.98, blue: 1.0, alpha: 0.95))
    ctx.addPath(cardPath)
    ctx.fillPath()
    ctx.restoreGState()

    // Inside Card
    ctx.saveGState()
    ctx.addPath(cardPath)
    ctx.clip()

    // Card Header Bar
    let headerHeight = cardRect.height * 0.2
    let headerRect = CGRect(x: cardRect.minX, y: cardRect.maxY - headerHeight, width: cardRect.width, height: headerHeight)
    ctx.setFillColor(CGColor(red: 0.92, green: 0.93, blue: 0.96, alpha: 0.9))
    ctx.fill(headerRect)

    // Three traffic dots
    let dotColors: [(CGFloat, CGFloat, CGFloat)] = [
        (1.0, 0.38, 0.38),
        (1.0, 0.76, 0.28),
        (0.28, 0.82, 0.48)
    ]
    let dotRadius = size.width * 0.016
    for i in 0..<3 {
        let dotCenter = CGPoint(x: cardRect.minX + size.width * 0.05 + CGFloat(i) * size.width * 0.045, y: headerRect.midY)
        ctx.setFillColor(CGColor(red: dotColors[i].0, green: dotColors[i].1, blue: dotColors[i].2, alpha: 1.0))
        ctx.fillEllipse(in: CGRect(x: dotCenter.x - dotRadius, y: dotCenter.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2))
    }

    // Grid Columns
    let contentHeight = cardRect.height - headerHeight
    let colWidth = cardRect.width / 4.0
    ctx.setStrokeColor(CGColor(red: 0.88, green: 0.90, blue: 0.94, alpha: 0.8))
    ctx.setLineWidth(max(1.0, size.width * 0.003))
    for col in 1...3 {
        let x = cardRect.minX + CGFloat(col) * colWidth
        ctx.strokeLineSegments(between: [CGPoint(x: x, y: cardRect.minY), CGPoint(x: x, y: cardRect.minY + contentHeight)])
    }

    // Course Blocks
    struct Pill {
        let col: Int
        let yNorm: CGFloat
        let heightNorm: CGFloat
        let color: (CGFloat, CGFloat, CGFloat)
    }
    let pills = [
        Pill(col: 0, yNorm: 0.35, heightNorm: 0.48, color: (1.0, 0.36, 0.42)), // Coral
        Pill(col: 1, yNorm: 0.10, heightNorm: 0.35, color: (0.18, 0.78, 0.65)), // Mint
        Pill(col: 2, yNorm: 0.40, heightNorm: 0.45, color: (0.35, 0.52, 0.88)), // Blue
        Pill(col: 3, yNorm: 0.20, heightNorm: 0.38, color: (0.98, 0.65, 0.22))  // Orange
    ]

    for p in pills {
        let px = cardRect.minX + CGFloat(p.col) * colWidth + colWidth * 0.1
        let pw = colWidth * 0.8
        let py = cardRect.minY + p.yNorm * contentHeight
        let ph = p.heightNorm * contentHeight
        let pRect = CGRect(x: px, y: py, width: pw, height: ph)
        let corner = size.width * 0.02
        let path = CGPath(roundedRect: pRect, cornerWidth: corner, cornerHeight: corner, transform: nil)
        ctx.setFillColor(CGColor(red: p.color.0, green: p.color.1, blue: p.color.2, alpha: 0.95))
        ctx.addPath(path)
        ctx.fillPath()
    }

    ctx.restoreGState() // Card clip

    // Focus Clock Badge in bottom right
    let badgeRadius = size.width * 0.13
    let badgeCenter = CGPoint(x: rect.maxX - badgeRadius - size.width * 0.04, y: rect.minY + badgeRadius + size.height * 0.04)
    let badgeRect = CGRect(x: badgeCenter.x - badgeRadius, y: badgeCenter.y - badgeRadius, width: badgeRadius * 2, height: badgeRadius * 2)

    ctx.saveGState()
    ctx.setShadow(offset: CGSize(width: 0, height: -size.height * 0.015), blur: size.width * 0.03, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.3))
    ctx.setFillColor(CGColor(red: 1.0, green: 0.34, blue: 0.38, alpha: 1.0))
    ctx.fillEllipse(in: badgeRect)

    // Inner White Ring & Clock Hands
    ctx.setStrokeColor(CGColor(red: 1.0, green: 1.0, blue: 1.0, alpha: 1.0))
    ctx.setLineWidth(max(1.5, size.width * 0.018))
    ctx.setLineCap(.round)

    let innerClockRect = badgeRect.insetBy(dx: badgeRadius * 0.26, dy: badgeRadius * 0.26)
    ctx.strokeEllipse(in: innerClockRect)
    ctx.strokeLineSegments(between: [badgeCenter, CGPoint(x: badgeCenter.x - badgeRadius * 0.26, y: badgeCenter.y + badgeRadius * 0.28)])
    ctx.strokeLineSegments(between: [badgeCenter, CGPoint(x: badgeCenter.x + badgeRadius * 0.42, y: badgeCenter.y + badgeRadius * 0.22)])
    ctx.restoreGState()

    ctx.restoreGState() // Outer shape clip
    return ctx
}

func generateAllAndroidIcons() {
    let densities: [(String, CGFloat)] = [
        ("mipmap-mdpi", 48),
        ("mipmap-hdpi", 72),
        ("mipmap-xhdpi", 96),
        ("mipmap-xxhdpi", 144),
        ("mipmap-xxxhdpi", 192)
    ]

    let basePath = "apps/android/app/src/main/res"

    for (folder, px) in densities {
        let size = CGSize(width: px, height: px)
        // Square squircle icon
        let ctxSquare = drawAppIcon(size: size, isRound: false)
        savePNG(ctx: ctxSquare, to: "\(basePath)/\(folder)/ic_launcher.png")

        // Round icon
        let ctxRound = drawAppIcon(size: size, isRound: true)
        savePNG(ctx: ctxRound, to: "\(basePath)/\(folder)/ic_launcher_round.png")
    }
}

// MARK: - 2. Generate Widget Previews
func generateWidgetPreviews() {
    let basePath = "apps/android/app/src/main/res/drawable"

    // 1. Next Class Widget Preview (280x280)
    do {
        let w = 320, h = 320
        let ctx = createCGContext(width: w, height: h)
        let rect = CGRect(x: 10, y: 10, width: 300, height: 300)
        let path = CGPath(roundedRect: rect, cornerWidth: 28, cornerHeight: 28, transform: nil)

        // Card bg: sleek frosted dark glass
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: -4), blur: 16, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.3))
        ctx.setFillColor(CGColor(red: 0.10, green: 0.11, blue: 0.15, alpha: 0.96))
        ctx.addPath(path)
        ctx.fillPath()
        ctx.restoreGState()

        // Badge pill: 下节课
        let pillRect = CGRect(x: 32, y: 254, width: 62, height: 26)
        let pillPath = CGPath(roundedRect: pillRect, cornerWidth: 6, cornerHeight: 6, transform: nil)
        ctx.setFillColor(CGColor(red: 0.25, green: 0.32, blue: 0.71, alpha: 1.0))
        ctx.addPath(pillPath)
        ctx.fillPath()

        // Texts via NSGraphicsContext
        let nsCtx = NSGraphicsContext(cgContext: ctx, flipped: false)
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = nsCtx

        // "下节课"
        let pillText = NSAttributedString(string: "下节课", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 13),
            .foregroundColor: NSColor.white
        ])
        pillText.draw(at: CGPoint(x: 40, y: 258))

        // "10:00 开始"
        let timeText = NSAttributedString(string: "10:00 开始", attributes: [
            .font: NSFont.systemFont(ofSize: 13, weight: .medium),
            .foregroundColor: NSColor(red: 0.70, green: 0.72, blue: 0.80, alpha: 1.0)
        ])
        timeText.draw(at: CGPoint(x: 216, y: 258))

        // "高等数学"
        let titleText = NSAttributedString(string: "高等数学", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 22),
            .foregroundColor: NSColor.white
        ])
        titleText.draw(at: CGPoint(x: 32, y: 198))

        // "@东上 101 · 张教授"
        let classStr = NSMutableAttributedString()
        classStr.append(NSAttributedString(string: "@东上 101", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 15),
            .foregroundColor: NSColor(red: 0.51, green: 0.55, blue: 0.97, alpha: 1.0)
        ]))
        classStr.append(NSAttributedString(string: " · 张教授", attributes: [
            .font: NSFont.systemFont(ofSize: 14),
            .foregroundColor: NSColor(red: 0.70, green: 0.72, blue: 0.80, alpha: 1.0)
        ]))
        classStr.draw(at: CGPoint(x: 32, y: 160))

        // "第 3-4 节 · 距上课 25 分钟"
        let subText = NSAttributedString(string: "第 3-4 节 · 距上课 25 分钟", attributes: [
            .font: NSFont.systemFont(ofSize: 13),
            .foregroundColor: NSColor(red: 0.56, green: 0.58, blue: 0.65, alpha: 1.0)
        ])
        subText.draw(at: CGPoint(x: 32, y: 125))

        // Accent bottom wave
        let wavePill = CGRect(x: 32, y: 45, width: 256, height: 48)
        let wavePath = CGPath(roundedRect: wavePill, cornerWidth: 12, cornerHeight: 12, transform: nil)
        ctx.setFillColor(CGColor(red: 0.16, green: 0.18, blue: 0.25, alpha: 1.0))
        ctx.addPath(wavePath)
        ctx.fillPath()

        let waveText = NSAttributedString(string: "教室路线已规划 · 教学楼 A 区", attributes: [
            .font: NSFont.systemFont(ofSize: 12),
            .foregroundColor: NSColor(red: 0.60, green: 0.63, blue: 0.72, alpha: 1.0)
        ])
        waveText.draw(at: CGPoint(x: 48, y: 60))

        NSGraphicsContext.restoreGraphicsState()

        savePNG(ctx: ctx, to: "\(basePath)/widget_preview_next_class.png")
    }

    // 2. Today Classes Widget Preview (560x280)
    do {
        let w = 580, h = 300
        let ctx = createCGContext(width: w, height: h)
        let rect = CGRect(x: 10, y: 10, width: 560, height: 280)
        let path = CGPath(roundedRect: rect, cornerWidth: 28, cornerHeight: 28, transform: nil)

        // Card bg
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: -4), blur: 16, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.3))
        ctx.setFillColor(CGColor(red: 0.10, green: 0.11, blue: 0.15, alpha: 0.96))
        ctx.addPath(path)
        ctx.fillPath()
        ctx.restoreGState()

        let nsCtx = NSGraphicsContext(cgContext: ctx, flipped: false)
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = nsCtx

        // Header: 今日课表 · 周五 10/16
        let headerText = NSAttributedString(string: "今日课表 · 周五 10/16", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 17),
            .foregroundColor: NSColor.white
        ])
        headerText.draw(at: CGPoint(x: 32, y: 242))

        // Badge: 共 4 节
        let countPill = CGRect(x: 470, y: 238, width: 72, height: 26)
        let countPath = CGPath(roundedRect: countPill, cornerWidth: 6, cornerHeight: 6, transform: nil)
        ctx.setFillColor(CGColor(red: 0.16, green: 0.18, blue: 0.25, alpha: 1.0))
        ctx.addPath(countPath)
        ctx.fillPath()

        let countText = NSAttributedString(string: "共 4 节", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 13),
            .foregroundColor: NSColor(red: 0.51, green: 0.55, blue: 0.97, alpha: 1.0)
        ])
        countText.draw(at: CGPoint(x: 486, y: 242))

        // Course rows
        struct RowData {
            let color: (CGFloat, CGFloat, CGFloat)
            let title: String
            let time: String
            let loc: String
        }
        let rows = [
            RowData(color: (0.31, 0.27, 0.90), title: "高等数学", time: "08:00 - 09:35 (第 1-2 节)", loc: "@东上 101"),
            RowData(color: (0.04, 0.50, 0.26), title: "数据结构与算法", time: "10:00 - 11:35 (第 3-4 节)", loc: "@信息楼 302"),
            RowData(color: (0.85, 0.40, 0.10), title: "计算机网络", time: "14:00 - 15:35 (第 5-6 节)", loc: "@实验楼 204")
        ]

        var currentY: CGFloat = 175
        for r in rows {
            // Accent vertical bar
            let barRect = CGRect(x: 32, y: currentY - 4, width: 5, height: 44)
            let barPath = CGPath(roundedRect: barRect, cornerWidth: 2.5, cornerHeight: 2.5, transform: nil)
            ctx.setFillColor(CGColor(red: r.color.0, green: r.color.1, blue: r.color.2, alpha: 1.0))
            ctx.addPath(barPath)
            ctx.fillPath()

            // Title
            let rTitle = NSAttributedString(string: r.title, attributes: [
                .font: NSFont.boldSystemFont(ofSize: 15),
                .foregroundColor: NSColor.white
            ])
            rTitle.draw(at: CGPoint(x: 48, y: currentY + 18))

            // Time & Location
            let detail = NSMutableAttributedString()
            detail.append(NSAttributedString(string: r.time, attributes: [
                .font: NSFont.systemFont(ofSize: 12.5),
                .foregroundColor: NSColor(red: 0.70, green: 0.72, blue: 0.80, alpha: 1.0)
            ]))
            detail.append(NSAttributedString(string: "   \(r.loc)", attributes: [
                .font: NSFont.boldSystemFont(ofSize: 12.5),
                .foregroundColor: NSColor(red: 0.51, green: 0.55, blue: 0.97, alpha: 1.0)
            ]))
            detail.draw(at: CGPoint(x: 48, y: currentY - 2))

            currentY -= 65
        }

        NSGraphicsContext.restoreGraphicsState()

        savePNG(ctx: ctx, to: "\(basePath)/widget_preview_today_classes.png")
    }

    // 3. Week Timetable Widget Preview (560x560)
    do {
        let w = 580, h = 580
        let ctx = createCGContext(width: w, height: h)
        let rect = CGRect(x: 10, y: 10, width: 560, height: 560)
        let path = CGPath(roundedRect: rect, cornerWidth: 32, cornerHeight: 32, transform: nil)

        // Card bg: clean white
        ctx.saveGState()
        ctx.setShadow(offset: CGSize(width: 0, height: -4), blur: 16, color: CGColor(red: 0, green: 0, blue: 0, alpha: 0.15))
        ctx.setFillColor(CGColor(red: 0.98, green: 0.99, blue: 1.0, alpha: 1.0))
        ctx.addPath(path)
        ctx.fillPath()
        ctx.restoreGState()

        let nsCtx = NSGraphicsContext(cgContext: ctx, flipped: false)
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = nsCtx

        // Top Month: 10月
        let monthStr = NSMutableAttributedString()
        monthStr.append(NSAttributedString(string: "10\n", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 16),
            .foregroundColor: NSColor(red: 0.12, green: 0.16, blue: 0.22, alpha: 1.0)
        ]))
        monthStr.append(NSAttributedString(string: "月", attributes: [
            .font: NSFont.boldSystemFont(ofSize: 12),
            .foregroundColor: NSColor(red: 0.12, green: 0.16, blue: 0.22, alpha: 1.0)
        ]))
        monthStr.draw(at: CGPoint(x: 24, y: 508))

        // 7 Days
        let days = [("一", "12"), ("二", "13"), ("三", "14"), ("四", "15"), ("五", "16"), ("六", "17"), ("日", "18")]
        let startX: CGFloat = 80
        let dayColWidth: CGFloat = (540 - startX) / 7.0

        for (i, d) in days.enumerated() {
            let cx = startX + CGFloat(i) * dayColWidth + dayColWidth * 0.5
            let isToday = (i == 4)
            let color = isToday ? NSColor(red: 0.07, green: 0.10, blue: 0.15, alpha: 1.0) : NSColor(red: 0.42, green: 0.45, blue: 0.50, alpha: 1.0)
            let weight: NSFont.Weight = isToday ? .bold : .medium

            let dayText = NSAttributedString(string: "\(d.0)\n\(d.1)", attributes: [
                .font: NSFont.systemFont(ofSize: 13, weight: weight),
                .foregroundColor: color
            ])
            let size = dayText.size()
            dayText.draw(at: CGPoint(x: cx - size.width * 0.5, y: 512))
        }

        // Section Axis
        let sections = [
            ("1", "08:30"),
            ("2", "09:20"),
            ("3", "10:15"),
            ("4", "11:05"),
            ("5", "14:30")
        ]
        let startY: CGFloat = 430
        let rowH: CGFloat = 84

        for (i, sec) in sections.enumerated() {
            let cy = startY - CGFloat(i) * rowH
            let secText = NSAttributedString(string: sec.0, attributes: [
                .font: NSFont.boldSystemFont(ofSize: 15),
                .foregroundColor: NSColor(red: 0.12, green: 0.16, blue: 0.22, alpha: 1.0)
            ])
            secText.draw(at: CGPoint(x: 30, y: cy + 18))

            let timeText = NSAttributedString(string: sec.1, attributes: [
                .font: NSFont.systemFont(ofSize: 9),
                .foregroundColor: NSColor(red: 0.42, green: 0.45, blue: 0.50, alpha: 1.0)
            ])
            timeText.draw(at: CGPoint(x: 22, y: cy + 2))
        }

        // Pastel Course Cards
        // Card 1: Tue Sec 3-4 (Mint)
        do {
            let cx = startX + 1.0 * dayColWidth + 2
            let cy = startY - 3.0 * rowH
            let cRect = CGRect(x: cx, y: cy, width: dayColWidth - 4, height: rowH * 2 - 4)
            let cPath = CGPath(roundedRect: cRect, cornerWidth: 10, cornerHeight: 10, transform: nil)
            ctx.setFillColor(CGColor(red: 0.88, green: 0.97, blue: 0.94, alpha: 1.0)) // Mint
            ctx.addPath(cPath)
            ctx.fillPath()

            let barPath = CGPath(roundedRect: CGRect(x: cx, y: cRect.maxY - 4, width: cRect.width, height: 4), cornerWidth: 2, cornerHeight: 2, transform: nil)
            ctx.setFillColor(CGColor(red: 0.42, green: 0.82, blue: 0.70, alpha: 1.0))
            ctx.addPath(barPath)
            ctx.fillPath()

            let cText = NSAttributedString(string: "认证与访问\n崔永泉\n@A415", attributes: [
                .font: NSFont.boldSystemFont(ofSize: 10),
                .foregroundColor: NSColor(red: 0.06, green: 0.32, blue: 0.27, alpha: 1.0)
            ])
            cText.draw(at: CGPoint(x: cx + 4, y: cy + 90))
        }

        // Card 2: Thu Sec 3-4 (Peach)
        do {
            let cx = startX + 3.0 * dayColWidth + 2
            let cy = startY - 3.0 * rowH
            let cRect = CGRect(x: cx, y: cy, width: dayColWidth - 4, height: rowH * 2 - 4)
            let cPath = CGPath(roundedRect: cRect, cornerWidth: 10, cornerHeight: 10, transform: nil)
            ctx.setFillColor(CGColor(red: 1.0, green: 0.94, blue: 0.88, alpha: 1.0)) // Peach
            ctx.addPath(cPath)
            ctx.fillPath()

            let barPath = CGPath(roundedRect: CGRect(x: cx, y: cRect.maxY - 4, width: cRect.width, height: 4), cornerWidth: 2, cornerHeight: 2, transform: nil)
            ctx.setFillColor(CGColor(red: 1.0, green: 0.74, blue: 0.50, alpha: 1.0))
            ctx.addPath(barPath)
            ctx.fillPath()

            let cText = NSAttributedString(string: "程序分析\n文明\n@A415", attributes: [
                .font: NSFont.boldSystemFont(ofSize: 10),
                .foregroundColor: NSColor(red: 0.48, green: 0.24, blue: 0.0, alpha: 1.0)
            ])
            cText.draw(at: CGPoint(x: cx + 4, y: cy + 90))
        }

        // Card 3: Fri Sec 1-2 (Sky Blue)
        do {
            let cx = startX + 4.0 * dayColWidth + 2
            let cy = startY - 1.0 * rowH
            let cRect = CGRect(x: cx, y: cy, width: dayColWidth - 4, height: rowH * 2 - 4)
            let cPath = CGPath(roundedRect: cRect, cornerWidth: 10, cornerHeight: 10, transform: nil)
            ctx.setFillColor(CGColor(red: 0.90, green: 0.94, blue: 1.0, alpha: 1.0))
            ctx.addPath(cPath)
            ctx.fillPath()

            let barPath = CGPath(roundedRect: CGRect(x: cx, y: cRect.maxY - 4, width: cRect.width, height: 4), cornerWidth: 2, cornerHeight: 2, transform: nil)
            ctx.setFillColor(CGColor(red: 0.48, green: 0.65, blue: 0.98, alpha: 1.0))
            ctx.addPath(barPath)
            ctx.fillPath()

            let cText = NSAttributedString(string: "高等数学\n张教授\n@东上101", attributes: [
                .font: NSFont.boldSystemFont(ofSize: 10),
                .foregroundColor: NSColor(red: 0.12, green: 0.25, blue: 0.55, alpha: 1.0)
            ])
            cText.draw(at: CGPoint(x: cx + 4, y: cy + 90))
        }

        NSGraphicsContext.restoreGraphicsState()

        savePNG(ctx: ctx, to: "\(basePath)/widget_preview_week_timetable.png")
    }
}

print("Generating Android App Icons...")
generateAllAndroidIcons()
print("Generating Android Widget Previews...")
generateWidgetPreviews()
print("Done!")
