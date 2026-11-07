// A visible window for the WKWebView tests, on both UI frameworks. WebKit
// starts its web content process reliably only for a view that is on screen.

import Foundation
@testable import WidgetSandbox
#if canImport(UIKit)
import UIKit

typealias TestWindow = UIWindow

extension UIWindow {
    static func makeVisible() -> UIWindow {
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 400, height: 600))
        window.makeKeyAndVisible()
        return window
    }

    /// Adds the view, filling the window.
    func attach(_ view: PlatformView) {
        addSubview(view)
        view.frame = bounds
    }

    /// `NSWindow.close()` for the shared test code: the window leaves the screen.
    func close() {
        isHidden = true
    }
}
#elseif canImport(AppKit)
import AppKit

typealias TestWindow = NSWindow

extension NSWindow {
    static func makeVisible() -> NSWindow {
        // An xctest process is not an activated app: its windows are ordered
        // front but never become visible for `NSWindow.occlusionState`, which
        // WidgetContainerView uses for the off-screen pause. Activate it once.
        let app = NSApplication.shared
        if app.activationPolicy() != .regular { app.setActivationPolicy(.regular) }
        app.activate(ignoringOtherApps: true)
        let window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 400, height: 600), styleMask: [.titled], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.makeKeyAndOrderFront(nil)
        return window
    }

    /// Adds the view, filling the window's content view.
    func attach(_ view: PlatformView) {
        guard let content = contentView else { return }
        content.addSubview(view)
        view.frame = content.bounds
    }
}
#endif
