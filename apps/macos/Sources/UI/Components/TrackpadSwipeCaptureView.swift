import SwiftUI
import AppKit

/// An NSViewRepresentable that captures two-finger horizontal trackpad flicks / swipes on macOS
/// and translates them to next/previous week navigation callbacks without blocking clicks on child elements.
public struct TrackpadSwipeCaptureView: NSViewRepresentable {
    public var onSwipeLeft: (() -> Void)?   // Flick left -> Next week
    public var onSwipeRight: (() -> Void)?  // Flick right -> Previous week

    public init(onSwipeLeft: (() -> Void)? = nil, onSwipeRight: (() -> Void)? = nil) {
        self.onSwipeLeft = onSwipeLeft
        self.onSwipeRight = onSwipeRight
    }

    public func makeNSView(context: Context) -> TrackpadSwipeNSView {
        let view = TrackpadSwipeNSView()
        view.onSwipeLeft = onSwipeLeft
        view.onSwipeRight = onSwipeRight
        return view
    }

    public func updateNSView(_ nsView: TrackpadSwipeNSView, context: Context) {
        nsView.onSwipeLeft = onSwipeLeft
        nsView.onSwipeRight = onSwipeRight
    }
}

public final class TrackpadSwipeNSView: NSView {
    public var onSwipeLeft: (() -> Void)?
    public var onSwipeRight: (() -> Void)?

    private var accumulatedX: CGFloat = 0
    private var isHandlingSwipe: Bool = false
    private var lastTriggerTime: TimeInterval = 0
    private var eventMonitor: Any?

    public override func hitTest(_ point: NSPoint) -> NSView? {
        // Return nil so mouse clicks, drags and selection pass through directly to child views
        return nil
    }

    public override func viewDidMoveToWindow() {
        super.viewDidMoveToWindow()
        if window != nil {
            setupMonitor()
        } else {
            removeMonitor()
        }
    }

    public override func viewWillMove(toWindow newWindow: NSWindow?) {
        super.viewWillMove(toWindow: newWindow)
        if newWindow == nil {
            removeMonitor()
        }
    }

    private func setupMonitor() {
        guard eventMonitor == nil else { return }
        eventMonitor = NSEvent.addLocalMonitorForEvents(matching: [.scrollWheel, .swipe]) { [weak self] event in
            guard let self = self, let win = self.window, win.isKeyWindow else {
                return event
            }

            // Verify cursor location is inside this timetable view's frame
            let loc = event.locationInWindow
            let viewRect = self.convert(self.bounds, to: nil)
            guard viewRect.contains(loc) else {
                return event
            }

            // 1. Direct gesture swipe event (available on some trackpads)
            if event.type == .swipe {
                let now = ProcessInfo.processInfo.systemUptime
                if now - self.lastTriggerTime > 0.4 {
                    if event.deltaX < -0.2 {
                        self.lastTriggerTime = now
                        DispatchQueue.main.async { self.onSwipeLeft?() }
                    } else if event.deltaX > 0.2 {
                        self.lastTriggerTime = now
                        DispatchQueue.main.async { self.onSwipeRight?() }
                    }
                }
                return event
            }

            // 2. Continuous two-finger scrollWheel event
            if event.type == .scrollWheel {
                let dx = event.scrollingDeltaX
                let dy = event.scrollingDeltaY

                // Horizontal movement must be dominant over vertical scrolling
                if abs(dx) > abs(dy) * 1.5 && abs(dx) > 1.2 {
                    if event.phase == .began {
                        self.accumulatedX = 0
                        self.isHandlingSwipe = false
                    } else if event.phase == .changed {
                        self.accumulatedX += dx
                        let threshold: CGFloat = 45
                        let now = ProcessInfo.processInfo.systemUptime
                        if !self.isHandlingSwipe && (now - self.lastTriggerTime > 0.4) {
                            if self.accumulatedX < -threshold {
                                self.isHandlingSwipe = true
                                self.lastTriggerTime = now
                                self.accumulatedX = 0
                                DispatchQueue.main.async { self.onSwipeLeft?() }
                            } else if self.accumulatedX > threshold {
                                self.isHandlingSwipe = true
                                self.lastTriggerTime = now
                                self.accumulatedX = 0
                                DispatchQueue.main.async { self.onSwipeRight?() }
                            }
                        }
                    } else if event.phase == .ended || event.phase == .cancelled {
                        self.accumulatedX = 0
                        self.isHandlingSwipe = false
                    }
                }
            }
            return event
        }
    }

    private func removeMonitor() {
        if let monitor = eventMonitor {
            NSEvent.removeMonitor(monitor)
            eventMonitor = nil
        }
    }
}
