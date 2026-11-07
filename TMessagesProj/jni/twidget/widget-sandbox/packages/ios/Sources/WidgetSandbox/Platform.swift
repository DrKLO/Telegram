// UIKit / AppKit seam. The sandbox (QuickJS, validator) is pure Foundation;
// only the renderer's container view and the screen scale differ per platform.

import Foundation
#if canImport(UIKit)
import UIKit

/// `UIView` on iOS, `NSView` on macOS — the type of `WidgetHost.view`.
public typealias PlatformView = UIView
/// `UIImage` on iOS, `NSImage` on macOS — a renderer snapshot.
public typealias PlatformImage = UIImage

enum PlatformScreen {
    static var scale: Double { Double(UIScreen.main.scale) }
}
#elseif canImport(AppKit)
import AppKit

/// `UIView` on iOS, `NSView` on macOS — the type of `WidgetHost.view`.
public typealias PlatformView = NSView
/// `UIImage` on iOS, `NSImage` on macOS — a renderer snapshot.
public typealias PlatformImage = NSImage

enum PlatformScreen {
    /// The widget is created before it is in a window; the main screen's scale
    /// is the best guess and the renderer reports the real `devicePixelRatio`
    /// with its first `size` message anyway.
    private static let lock = NSLock()
    private static var cached: Double = 2

    /// NSScreen is main-thread only; other threads get the last scale the main thread saw.
    static var scale: Double {
        if Thread.isMainThread {
            let scale = Double(NSScreen.main?.backingScaleFactor ?? 2)
            lock.lock()
            cached = scale
            lock.unlock()
            return scale
        }
        lock.lock()
        defer { lock.unlock() }
        return cached
    }
}
#endif
