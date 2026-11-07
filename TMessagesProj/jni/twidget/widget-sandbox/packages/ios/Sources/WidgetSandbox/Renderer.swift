// The renderer as WidgetHost sees it: a view plus the JSON message contract of
// docs/protocol.md (host → renderer: ops / query / listen / preventDefault /
// requestFrame / theme / safeArea / policy / frozen; renderer → host: ready /
// event / gesture / result / size / resize / frame / media / limit /
// violation). RendererWebView is the product renderer; other implementations
// plug in through WidgetHost's `rendererFactory` and report the paint limit
// through `limitExceeded`. All members are main-thread.
// Mirrors Renderer.kt on Android.

import Foundation
import WidgetSandboxProtocol

public protocol Renderer: AnyObject {
    var view: PlatformView { get }
    /// Renderer → host messages (JSON), delivered on the main thread.
    var onMessage: ((String) -> Void)? { get set }
    var onDiag: ((String, String) -> Void)? { get set }

    /// Starts the renderer; it announces itself with `{type:'ready'}`.
    func load()
    /// Host → renderer message (JSON).
    func send(_ json: String)
    var acceptsTypedOps: Bool { get }
    func send(ops: [WidgetOp])
    /// The chat theme changed (`prefers-color-scheme` inside the renderer).
    func setTheme(_ theme: String)
    /// Trusted script evaluated in the renderer (tests / diagnostics only); renderers without script answer `nil`.
    func evaluate(_ script: String, completion: @escaping (String?) -> Void)
    /// The last render as an image (a bubble about to drop its runtime keeps it); nil when the renderer cannot.
    func snapshot(completion: @escaping (PlatformImage?) -> Void)
    func destroy()
}

public protocol TaskRenderer: AnyObject {
    func beginTask()
    func endTask()
}

public extension Renderer {
    func snapshot(completion: @escaping (PlatformImage?) -> Void) { completion(nil) }
    var acceptsTypedOps: Bool { false }
    func send(ops: [WidgetOp]) { send("{\"type\":\"ops\",\"ops\":\(WidgetOp.json(ops))}") }

    /// A renderer of the client's own that counts heavy boxes (docs/decisions.md "Heavy boxes", the rule and the cases
    /// in packages/core/tests/fixtures/paint-policy.json) reports a passed limit here, with the count, from where it
    /// delivers `onMessage`: before it draws that frame, and once. The host stops the widget as for memory or CPU
    /// (`onDead` / `WidgetDiagnostics.dead` with `WidgetPaintLimit.message`, the diagnostic `paint-limit` or
    /// `backdrop-limit`); the renderer draws the stopped widget without the effects (no blurred shadow, filter,
    /// backdrop-filter or transparency; animations stopped). Under `compat` the host ignores it. The WebView renderer
    /// sends the same `limit` message itself.
    func limitExceeded(_ code: WidgetPaintLimit, count: Int) {
        onMessage?("{\"type\":\"limit\",\"code\":\"\(code.rawValue)\",\"count\":\(max(0, count))}")
    }
}

/// The paint limits a renderer enforces (packages/core/src/shared/paint-policy.ts): at most 16 heavy boxes at once
/// (a blurred box-shadow, a filter, a backdrop-filter, opacity below 1, or an animation of one of them), of which at
/// most 2 with backdrop-filter.
public enum WidgetPaintLimit: String, CaseIterable {
    /// More than `maxHeavyBoxes` heavy boxes; the count is the heavy boxes'.
    case paint = "paint-limit"
    /// More than `maxBackdropBoxes` boxes with backdrop-filter; the count is theirs.
    case backdrop = "backdrop-limit"

    public static let maxHeavyBoxes = 16
    public static let maxBackdropBoxes = 2

    /// The reason the host stops the widget with, the same on every platform (`paintLimitMessage` in paint-policy.ts).
    public func message(count: Int) -> String {
        switch self {
        case .paint: return "Paint limit exceeded: \(count) boxes with shadows, filters or transparency (max \(Self.maxHeavyBoxes))"
        case .backdrop: return "Paint limit exceeded: \(count) boxes with backdrop-filter (max \(Self.maxBackdropBoxes))"
        }
    }
}

/// Builds a renderer for one widget: the shared resources, the chat theme and the width in CSS px (= points).
public typealias RendererFactory = (_ resources: SandboxResources, _ theme: String, _ width: CGFloat) -> Renderer

extension RendererWebView: Renderer {
    public var view: PlatformView { webView }

    public func evaluate(_ script: String, completion: @escaping (String?) -> Void) {
        webView.evaluateJavaScript(script) { value, _ in completion(value.map { "\($0)" }) }
    }
}

/// A renderer whose deferred work (re-renders, timers) runs where its host runs: on a background queue while the host is prepared off-screen, on the main thread once adopted.
public protocol ScheduledRenderer: AnyObject {
    var schedule: (@escaping () -> Void) -> Void { get set }
    var scheduleAfter: (TimeInterval, @escaping () -> Void) -> Void { get set }
    var isOnExecutor: () -> Bool { get set }
    var scheduleUrgent: (@escaping () -> Void) -> Void { get set }
    var executorIsMain: () -> Bool { get set }
}
