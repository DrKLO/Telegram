import Foundation
import CoreGraphics
import QuartzCore
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Renderer views made ahead on the main thread, so a widget prepared on a
/// background queue never creates a view there. A shell is an empty renderer
/// (no document, no script) inside its container; it is used once.
public final class WidgetShellPool {
    public static let shared = WidgetShellPool()

    public struct Shell {
        let container: WidgetContainerView
        let renderer: Renderer
    }

    /// Builds the empty renderer for a shell (main thread); nil disables the pool.
    public var factory: ((CGFloat) -> Renderer)?
    public var targetSize = 6
    public private(set) var made = 0, taken = 0, misses = 0
    private let lock = NSLock()
    private var shells: [CGFloat: [Shell]] = [:]
    private var wantedWidth: CGFloat?
    private var refillScheduled = false

    static func makeContainer(_ renderer: Renderer, width: CGFloat) -> WidgetContainerView {
        let container = WidgetContainerView(frame: CGRect(x: 0, y: 0, width: width, height: 300))
        renderer.view.frame = container.bounds
        #if canImport(UIKit)
        renderer.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        container.clipsToBounds = true
        #else
        renderer.view.autoresizingMask = [.width, .height]
        container.wantsLayer = true
        container.layer?.masksToBounds = true
        #endif
        container.addSubview(renderer.view)
        return container
    }

    public var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return shells.values.reduce(0) { $0 + $1.count }
    }

    /// A shell of this width, waiting up to `wait` for the main thread to make one.
    public func take(width: CGFloat, wait: TimeInterval) -> Shell? {
        let deadline = CACurrentMediaTime() + wait
        while true {
            if let shell = take(width: width) {
                return shell
            }
            if Thread.isMainThread || CACurrentMediaTime() >= deadline {
                return nil
            }
            usleep(500)
        }
    }

    private func take(width: CGFloat) -> Shell? {
        guard factory != nil else {
            return nil
        }
        lock.lock()
        let shell = shells[width]?.popLast()
        if shell != nil {
            taken += 1
        } else {
            misses += 1
        }
        wantedWidth = width
        lock.unlock()
        scheduleRefill()
        return shell
    }

    public func available(width: CGFloat) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        wantedWidth = wantedWidth ?? width
        return !(shells[width]?.isEmpty ?? true)
    }

    public func prewarm(width: CGFloat) {
        lock.lock()
        wantedWidth = width
        lock.unlock()
        scheduleRefill()
    }

    private func scheduleRefill() {
        lock.lock()
        if refillScheduled {
            lock.unlock()
            return
        }
        refillScheduled = true
        lock.unlock()
        DispatchQueue.main.async { [weak self] in
            self?.refillOne()
        }
    }

    private func refillOne() {
        lock.lock()
        refillScheduled = false
        guard let width = wantedWidth, let factory = factory else {
            lock.unlock()
            return
        }
        let have = shells[width]?.count ?? 0
        lock.unlock()
        guard have < targetSize else {
            return
        }
        let t0 = CACurrentMediaTime()
        let renderer = factory(width)
        let shell = Shell(container: WidgetShellPool.makeContainer(renderer, width: width), renderer: renderer)
        WidgetWarmup.mainThreadWork?("shell-create", (CACurrentMediaTime() - t0) * 1000)
        lock.lock()
        shells[width, default: []].append(shell)
        made += 1
        for key in shells.keys where key != width {
            shells[key] = nil
        }
        lock.unlock()
        if have + 1 < targetSize {
            scheduleRefill()
        }
    }

    public func drain() {
        lock.lock()
        shells.removeAll()
        lock.unlock()
    }
}
