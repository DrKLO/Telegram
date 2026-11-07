// Shared WebKit infrastructure for many renderers at once (a chat with a
// widget in every bubble). One process pool, one non-persistent data store,
// one user content controller (hardening script, the `tw` message handler,
// the no-network rule list) and one scheme handler serving every renderer
// document by its own per-widget URL. Nothing about the trust model changes:
// every widget still gets its own opaque origin (tgwidget://w-<uuid>/), the
// handler routes messages by the web view that sent them, and the scheme
// handler refuses every URL it was not given.
//
// `RendererPool` keeps renderer documents loaded ahead of time so a bubble
// that scrolls in gets a web view whose page already announced `ready`.

import Foundation
import QuartzCore
import WebKit

public final class RendererEnvironment: NSObject, WKScriptMessageHandler {
    public static let shared = RendererEnvironment()

    let processPool = WKProcessPool()
    let dataStore = WKWebsiteDataStore.nonPersistent()
    let userContentController = WKUserContentController()
    let schemeHandler = SharedSchemeHandler()
    private var views: [ObjectIdentifier: WeakRenderer] = [:]
    private var ruleListReady = false
    private var ruleListWaiters: [() -> Void] = []
    private var ruleListRequested = false
    /// How the rule list was obtained the last time (`lookup` from the store's cache, `compiled`, or `unavailable`).
    public private(set) var ruleListSource = "none"

    override init() {
        super.init()
        userContentController.addUserScript(WKUserScript(source: RendererWebView.hardeningScript, injectionTime: .atDocumentStart, forMainFrameOnly: false))
        userContentController.add(self, name: "tw")
    }

    /// Compiles (or looks up) the no-network rule list and launches the web
    /// content process, so neither lands on the first bubble's critical path.
    public func prewarm(completion: (() -> Void)? = nil) {
        ensureRuleList {
            completion?()
        }
    }

    func makeConfiguration() -> WKWebViewConfiguration {
        let config = WKWebViewConfiguration()
        config.processPool = processPool
        config.websiteDataStore = dataStore
        config.userContentController = userContentController
        #if os(iOS)
        config.allowsInlineMediaPlayback = true
        #endif
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        if #available(macOS 11.0, iOS 14.0, *) {
            config.defaultWebpagePreferences.allowsContentJavaScript = true
        }
        config.setURLSchemeHandler(schemeHandler, forURLScheme: RendererWebView.scheme)
        return config
    }

    func register(_ renderer: RendererWebView) {
        views[ObjectIdentifier(renderer.webView)] = WeakRenderer(renderer)
    }

    func unregister(_ renderer: RendererWebView) {
        views.removeValue(forKey: ObjectIdentifier(renderer.webView))
        views = views.filter { $0.value.value != nil }
    }

    /// The rule list is added to the shared controller once; every renderer waits for it before loading its document.
    func ensureRuleList(_ completion: @escaping () -> Void) {
        if ruleListReady { return completion() }
        ruleListWaiters.append(completion)
        if ruleListRequested { return }
        ruleListRequested = true
        RendererWebView.ruleList { [self] list, error, source in
            if let list { userContentController.add(list) }
            ruleListSource = list == nil ? "unavailable (\(error ?? "?"))" : source
            ruleListReady = true
            let waiters = ruleListWaiters
            ruleListWaiters = []
            waiters.forEach { $0() }
        }
    }

    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let webView = message.webView, let renderer = views[ObjectIdentifier(webView)]?.value else { return }
        renderer.userContentController(userContentController, didReceive: message)
    }
}

private struct WeakRenderer {
    weak var value: RendererWebView?
    init(_ value: RendererWebView) { self.value = value }
}

/// Serves the renderer documents of every registered renderer (one URL each) and refuses everything else.
final class SharedSchemeHandler: NSObject, WKURLSchemeHandler {
    private struct Document { let html: Data; let csp: String; weak var owner: RendererWebView? }
    private var documents: [URL: Document] = [:]

    func register(url: URL, html: String, csp: String, owner: RendererWebView) {
        documents[url] = Document(html: Data(html.utf8), csp: csp, owner: owner)
    }

    func unregister(url: URL) {
        documents.removeValue(forKey: url)
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url, let doc = documents[url], doc.owner?.webView === webView else {
            let url = urlSchemeTask.request.url?.absoluteString ?? ""
            // The web view that asked is known; the owner of *that* view gets the diagnostic.
            RendererEnvironment.shared.rendererOf(webView)?.onDiag?("url-blocked", "renderer subresource request refused: \(url.prefix(120))")
            urlSchemeTask.didFailWithError(URLError(.resourceUnavailable))
            return
        }
        let headers = [
            "Content-Type": "text/html; charset=utf-8",
            "Content-Length": String(doc.html.count),
            "Content-Security-Policy": doc.csp,
            "X-DNS-Prefetch-Control": "off",
            "Cache-Control": "no-store",
        ]
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: headers)!
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(doc.html)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}
}

extension RendererEnvironment {
    func rendererOf(_ webView: WKWebView) -> RendererWebView? { views[ObjectIdentifier(webView)]?.value }
}

// MARK: - Renderer pool

/// Renderer documents loaded ahead of time (main thread only). `take()` hands
/// out a renderer whose page is loading or already `ready`; the pool refills
/// itself one web view at a time when the run loop is idle and `canRefill`
/// agrees (a scrolling list says no while a flick is in progress).
public final class RendererPool {
    public static let shared = RendererPool()

    /// Renderers to keep ready; 0 disables the pool.
    public var targetSize = 0 { didSet { trim(); scheduleRefill() } }
    /// Width the parked web views are laid out at (the bubble's widget width), so the first `size` report is right.
    public var width: CGFloat = 360
    public var theme = "light"
    /// Optional container inside the app's window: WebKit only runs rendering
    /// updates (ResizeObserver, hence `size`) for pages whose view is on screen.
    public weak var parkingView: PlatformView?
    public var canRefill: () -> Bool = { true }
    public var resources: SandboxResources = .shared
    public private(set) var created = 0, taken = 0, hits = 0, recycled = 0

    private var idle: [RendererWebView] = []
    private var resetting: [ObjectIdentifier: RendererWebView] = [:]
    public var resettingCount: Int { resetting.count }
    private var observer: CFRunLoopObserver?
    private var pressureSource: DispatchSourceMemoryPressure?
    private var pressureUntil: CFTimeInterval = 0

    public var count: Int { idle.count }

    public func prewarm(_ n: Int) {
        targetSize = max(targetSize, n)
        guard CACurrentMediaTime() >= pressureUntil else { return }
        while idle.count + resetting.count < n { makeOne() }
    }

    /// A pre-loaded renderer, or nil when the pool is empty.
    public func take() -> RendererWebView? {
        taken += 1
        while let last = idle.last, !last.isUsable { idle.removeLast().destroy() }
        guard let r = idle.popLast() else { scheduleRefill(); return nil }
        hits += 1
        r.webView.removeFromSuperview()
        scheduleRefill()
        return r
    }

    public func recycle(_ renderer: RendererWebView) {
        guard targetSize > 0, CACurrentMediaTime() >= pressureUntil, renderer.isUsable, idle.count + resetting.count < targetSize * 2 else {
            renderer.destroy()
            return
        }
        let key = ObjectIdentifier(renderer)
        guard resetting[key] == nil else { return }
        resetting[key] = renderer
        renderer.prepareForReuse { [weak self, weak renderer] success in
            guard let self, let renderer, self.resetting.removeValue(forKey: key) != nil else { return }
            if success, self.targetSize > 0, self.idle.count < self.targetSize * 2 {
                self.idle.append(renderer)
                self.recycled += 1
            } else {
                renderer.destroy()
            }
        }
    }

    private func trim() {
        while idle.count > max(0, targetSize * 2) { idle.removeLast().destroy() }
        if targetSize <= 0 { drain() }
    }

    private func makeOne() {
        let t0 = CACurrentMediaTime()
        let r = RendererWebView(rendererBundle: resources.rendererBundle, theme: theme, environment: .shared)
        r.webView.frame = CGRect(x: 0, y: 0, width: width, height: 300)
        parkingView?.addSubview(r.webView)
        r.load()
        idle.append(r)
        created += 1
        WidgetWarmup.mainThreadWork?("renderer-create", (CACurrentMediaTime() - t0) * 1000)
    }

    private func scheduleRefill() {
        if targetSize > 0, pressureSource == nil {
            let source = DispatchSource.makeMemoryPressureSource(eventMask: [.warning, .critical], queue: .main)
            source.setEventHandler { [weak self] in self?.releaseForMemoryPressure() }
            source.resume()
            pressureSource = source
        }
        guard CACurrentMediaTime() >= pressureUntil, idle.count + resetting.count < targetSize, observer == nil else { return }
        // One web view per idle turn of the main run loop: never a burst in the middle of a scroll frame.
        observer = CFRunLoopObserverCreateWithHandler(nil, CFRunLoopActivity.beforeWaiting.rawValue, true, 0) { [weak self] _, _ in
            guard let self else { return }
            if idle.count + resetting.count >= targetSize { stopObserving(); return }
            if canRefill() { makeOne() }
        }
        CFRunLoopAddObserver(CFRunLoopGetMain(), observer, .commonModes)
    }

    func releaseForMemoryPressure() {
        pressureUntil = CACurrentMediaTime() + 30
        drain()
    }

    private func stopObserving() {
        if let observer { CFRunLoopRemoveObserver(CFRunLoopGetMain(), observer, .commonModes) }
        observer = nil
    }

    /// Drops the parked renderers (tests, memory pressure).
    public func drain() {
        stopObserving()
        let renderers = idle + Array(resetting.values)
        idle.removeAll()
        resetting.removeAll()
        renderers.forEach { $0.destroy() }
    }
}
