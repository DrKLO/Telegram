// The renderer: a WKWebView that executes only our renderer bundle (nonce CSP),
// served from a private custom scheme, with every navigation cancelled, a
// content rule list that blocks http(s)/ws(s) loads, a non-persistent data
// store and a document-start script that removes the network APIs the
// renderer never uses (defense in depth — invariant 6).
//
// Transport: renderer → host through the `tw` script message handler (JSON),
// host → renderer through `__twReceive(json)` evaluated on the page.
//
// The same class serves iOS (UIKit) and macOS (AppKit): WebKit's API is
// shared, only the view-level knobs (background, colour scheme, scrolling,
// context menu, file panels) differ per platform.

import Foundation
import Security
import WebKit
#if canImport(UIKit)
import UIKit
#endif

public final class RendererWebView: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    public static let scheme = "tgwidget"
    public let webView: WKWebView
    /// Renderer → host. Messages that arrive while nobody listens (a pooled
    /// renderer announces `ready` before it has a host) are kept and replayed
    /// in order as soon as a listener is set.
    public var onMessage: ((String) -> Void)? { didSet { flushBuffered() } }
    public var onDiag: ((String, String) -> Void)?
    /// Host → renderer transport: a JS string literal in `evaluateJavaScript`
    /// (escaped on the calling thread), or the JSON handed over as an argument
    /// of `callAsyncJavaScript` (WebKit serializes it, no escaping pass).
    public enum Transport { case evaluate, callAsync }
    public static var transport: Transport = .evaluate
    /// True once the page announced `ready` (a pooled renderer is usually ready before it is taken).
    public private(set) var isReady = false

    private var documentURL: URL
    private let rendererBundle: String
    private var reuseCompletion: ((Bool) -> Void)?
    private var reuseTimeout: DispatchWorkItem?
    private(set) var isUsable = true
    private let environment: RendererEnvironment?
    private var schemeHandler: RendererSchemeHandler?
    private let messageHandler = WeakMessageHandler()
    private var documentLoaded = false
    private var buffered: [String] = []
    private var destroyed = false

    /// `environment`: nil gives the web view its own configuration (data store,
    /// content controller, rule list, scheme handler); `.shared` reuses one set for every renderer.
    public init(rendererBundle: String, theme: String, environment: RendererEnvironment? = nil) {
        // Every widget gets its own origin: tgwidget://w-<uuid>/
        documentURL = URL(string: "\(Self.scheme)://w-\(UUID().uuidString.lowercased())/")!
        self.environment = environment
        self.rendererBundle = rendererBundle
        let nonce = Self.makeNonce()
        let csp = Self.csp(nonce: nonce)
        let html = Self.document(rendererJs: rendererBundle, nonce: nonce, csp: csp)

        // WKWebView copies its configuration at init: scheme and message
        // handlers must be registered BEFORE the web view exists.
        let config: WKWebViewConfiguration
        if let environment {
            config = environment.makeConfiguration()
        } else {
            config = WKWebViewConfiguration()
            config.websiteDataStore = .nonPersistent()
            #if os(iOS)
            config.allowsInlineMediaPlayback = true
            #endif
            config.preferences.javaScriptCanOpenWindowsAutomatically = false
            if #available(macOS 11.0, iOS 14.0, *) {
                config.defaultWebpagePreferences.allowsContentJavaScript = true
            }
            let handler = RendererSchemeHandler(documentURL: documentURL, html: html, csp: csp)
            schemeHandler = handler
            config.setURLSchemeHandler(handler, forURLScheme: Self.scheme)
            config.userContentController.addUserScript(WKUserScript(source: Self.hardeningScript, injectionTime: .atDocumentStart, forMainFrameOnly: false))
            config.userContentController.add(messageHandler, name: "tw")
        }
        #if os(macOS)
        webView = RendererWKWebView(frame: .zero, configuration: config)
        #else
        webView = WKWebView(frame: .zero, configuration: config)
        #endif
        super.init()
        messageHandler.target = self
        if let environment {
            environment.schemeHandler.register(url: documentURL, html: html, csp: csp, owner: self)
            environment.register(self)
        } else {
            schemeHandler?.onBlocked = { [weak self] url in self?.onDiag?("url-blocked", "renderer subresource request refused: \(url.prefix(120))") }
        }
        webView.navigationDelegate = self
        webView.uiDelegate = self
        #if os(iOS)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        #elseif os(macOS)
        // The bubble behind the view paints the background; WebKit's own white
        // page background would cover it. `drawsBackground` is the KVC-exposed
        // switch WebKit itself honours (public `underPageBackgroundColor` only
        // tints the area past the document's end).
        webView.setValue(false, forKey: "drawsBackground")
        #endif
        applyTheme(theme)
    }

    private func applyTheme(_ theme: String) {
        #if os(iOS)
        webView.overrideUserInterfaceStyle = theme == "dark" ? .dark : .light
        #elseif os(macOS)
        // Drives `prefers-color-scheme` inside the page, like overrideUserInterfaceStyle on iOS.
        if #available(macOS 10.14, *) {
            webView.appearance = NSAppearance(named: theme == "dark" ? .darkAqua : .aqua)
        }
        #endif
    }

    /// Compiles (once per process) the no-network rule list, then loads the renderer document.
    public func load() {
        loadDocument(replacingHistory: false)
    }

    private func loadDocument(replacingHistory: Bool) {
        guard isUsable else { return }
        let requestedURL = documentURL
        if let environment {
            environment.ensureRuleList { [weak self] in
                guard let self, self.isUsable, self.documentURL == requestedURL else { return }
                if environment.ruleListSource.hasPrefix("unavailable") { onDiag?("renderer", "content rule list \(environment.ruleListSource); relying on CSP + scheme handler") }
                navigate(to: requestedURL, replacingHistory: replacingHistory)
            }
            return
        }
        Self.ruleList { [weak self] list, error, _ in
            guard let self, self.isUsable, self.documentURL == requestedURL else { return }
            if let list { self.webView.configuration.userContentController.add(list) } else { self.onDiag?("renderer", "content rule list unavailable (\(error ?? "?")); relying on CSP + scheme handler") }
            self.navigate(to: requestedURL, replacingHistory: replacingHistory)
        }
    }

    private func navigate(to url: URL, replacingHistory: Bool) {
        if replacingHistory {
            webView.evaluateJavaScript("location.replace(\(Self.jsStringLiteral(url.absoluteString)));undefined") { [weak self] _, error in
                guard let self, error != nil, self.isUsable, self.documentURL == url, !self.documentLoaded else { return }
                self.webView.load(URLRequest(url: url))
            }
        } else {
            webView.load(URLRequest(url: url))
        }
    }

    public func setTheme(_ theme: String) {
        applyTheme(theme)
    }

    /// Host → renderer. Must be called on the main thread.
    public func send(_ json: String) {
        guard isUsable else { return }
        let requestedURL = documentURL
        if Self.transport == .callAsync, #available(macOS 11.0, iOS 14.0, *) {
            webView.callAsyncJavaScript("__twReceive(json)", arguments: ["json": json], in: nil, in: .page) { [weak self] result in
                if case .failure(let error) = result, self?.documentURL == requestedURL { self?.onDiag?("renderer", "callAsyncJavaScript failed: \(error.localizedDescription)") }
            }
        } else {
            webView.evaluateJavaScript("__twReceive(\(Self.jsStringLiteral(json)));undefined") { [weak self] _, error in
                if let error, self?.documentURL == requestedURL { self?.onDiag?("renderer", "evaluateJavaScript failed: \(error.localizedDescription)") }
            }
        }
    }

    /// The page as an image (the last render, for a bubble whose runtime is being dropped). nil when WebKit cannot snapshot.
    public func snapshot(completion: @escaping (PlatformImage?) -> Void) {
        let config = WKSnapshotConfiguration()
        if #available(macOS 10.15, iOS 13.0, *) {
            config.afterScreenUpdates = false
        }
        // Two pixels per point is enough for a still of a bubble and a third of the 3× bitmap's bytes.
        #if os(iOS)
        if UIScreen.main.scale > 2 { config.snapshotWidth = NSNumber(value: Double(webView.bounds.width) * 2 / Double(UIScreen.main.scale)) }
        #endif
        webView.takeSnapshot(with: config) { image, _ in completion(image) }
    }

    func prepareForReuse(completion: @escaping (Bool) -> Void) {
        guard isUsable, let environment, reuseCompletion == nil else {
            completion(false)
            return
        }
        onMessage = nil
        onDiag = nil
        buffered.removeAll()
        webView.stopLoading()
        webView.removeFromSuperview()
        environment.schemeHandler.unregister(url: documentURL)
        documentURL = URL(string: "\(Self.scheme)://w-\(UUID().uuidString.lowercased())/")!
        let nonce = Self.makeNonce()
        let csp = Self.csp(nonce: nonce)
        environment.schemeHandler.register(url: documentURL, html: Self.document(rendererJs: rendererBundle, nonce: nonce, csp: csp), csp: csp, owner: self)
        isReady = false
        documentLoaded = false
        reuseCompletion = completion
        let timeout = DispatchWorkItem { [weak self] in self?.finishReuse(false) }
        reuseTimeout = timeout
        DispatchQueue.main.asyncAfter(deadline: .now() + 5, execute: timeout)
        loadDocument(replacingHistory: true)
    }

    private func finishReuse(_ success: Bool) {
        let completion = reuseCompletion
        reuseCompletion = nil
        reuseTimeout?.cancel()
        reuseTimeout = nil
        if !success { isUsable = false }
        completion?(success)
    }

    public func destroy() {
        if destroyed { return }
        destroyed = true
        isUsable = false
        onMessage = nil
        onDiag = nil
        buffered.removeAll()
        finishReuse(false)
        if let environment {
            environment.schemeHandler.unregister(url: documentURL)
            environment.unregister(self)
        } else {
            webView.configuration.userContentController.removeScriptMessageHandler(forName: "tw")
        }
        webView.stopLoading()
        webView.removeFromSuperview()
    }

    // MARK: renderer → host

    public func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard isUsable, message.frameInfo.isMainFrame, message.frameInfo.request.url == documentURL, message.name == "tw", let body = message.body as? String else { return }
        if !isReady, body.hasPrefix("{\"type\":\"ready\""), body.count < 20 { isReady = true }
        if let onMessage { onMessage(body) } else { buffered.append(body) }
        if isReady { finishReuse(true) }
    }

    private func flushBuffered() {
        guard let onMessage, !buffered.isEmpty else { return }
        let queued = buffered
        buffered = []
        queued.forEach(onMessage)
    }

    // MARK: navigation — only the initial document load is allowed

    public func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        let url = navigationAction.request.url
        if !documentLoaded, navigationAction.targetFrame?.isMainFrame == true, url == documentURL {
            documentLoaded = true
            decisionHandler(.allow)
            return
        }
        onDiag?("navigation-blocked", "\(navigationAction.navigationType.rawValue) → \(url?.absoluteString.prefix(120) ?? "")")
        decisionHandler(.cancel)
    }

    public func webView(_ webView: WKWebView, decidePolicyFor navigationResponse: WKNavigationResponse, decisionHandler: @escaping (WKNavigationResponsePolicy) -> Void) {
        decisionHandler(navigationResponse.response.url == documentURL ? .allow : .cancel)
    }

    public func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        onDiag?("renderer", "document load failed: \(error.localizedDescription)")
    }
    public func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
        onDiag?("renderer", "navigation failed: \(error.localizedDescription)")
    }
    public func webViewWebContentProcessDidTerminate(_ webView: WKWebView) {
        onDiag?("renderer", "web content process terminated")
        finishReuse(false)
    }

    // MARK: UI delegate — no popups, no dialogs

    public func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? {
        onDiag?("navigation-blocked", "window.open")
        return nil
    }
    public func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) { completionHandler() }
    public func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) { completionHandler(false) }
    public func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) { completionHandler(nil) }
    #if os(macOS)
    /// `<input type=file>` on macOS would open an NSOpenPanel; the widget gets no files from the disk.
    public func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping ([URL]?) -> Void) {
        onDiag?("renderer", "file panel refused")
        completionHandler(nil)
    }
    #endif

    // MARK: document, CSP, rule list

    static func csp(nonce: String) -> String {
        [
            "default-src 'none'", "script-src 'nonce-\(nonce)'", "style-src 'unsafe-inline'", "img-src data: blob:", "font-src data:",
            "media-src data: blob:", "connect-src 'none'", "frame-src 'none'", "child-src 'none'", "worker-src 'none'", "form-action 'none'", "base-uri 'none'",
        ].joined(separator: "; ")
    }

    static func document(rendererJs: String, nonce: String, csp: String) -> String {
        let js = rendererJs.replacingOccurrences(of: "</script", with: "<\\/script", options: .caseInsensitive).replacingOccurrences(of: "<!--", with: "<\\!--")
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta http-equiv=\"Content-Security-Policy\" content=\"\(csp)\">"
            + "<meta http-equiv=\"x-dns-prefetch-control\" content=\"off\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<script nonce=\"\(nonce)\">\(js)</script></head><body></body></html>"
    }

    static func makeNonce() -> String {
        var bytes = [UInt8](repeating: 0, count: 18)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        return Data(bytes).base64EncodedString().replacingOccurrences(of: "[+/=]", with: "x", options: .regularExpression)
    }

    /// Removes network-capable APIs from the renderer window. Our renderer code uses none of them.
    static let hardeningScript = """
    (function () {
      for (const k of ['fetch', 'XMLHttpRequest', 'WebSocket', 'EventSource', 'RTCPeerConnection', 'webkitRTCPeerConnection', 'Worker', 'SharedWorker', 'importScripts', 'open']) {
        try { Object.defineProperty(window, k, { value: undefined, writable: false, configurable: false }); } catch (e) {}
      }
      try { Object.defineProperty(navigator, 'sendBeacon', { value: undefined, writable: false, configurable: false }); } catch (e) {}
    })();
    """

    // One simple rule per scheme: the content-blocker regex dialect is small.
    private static let ruleListJson = """
    [{"trigger":{"url-filter":"^http://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^https://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^ws://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^wss://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^ftp://.*"},"action":{"type":"block"}}]
    """
    private static var cachedRuleList: WKContentRuleList?
    private static var ruleListWaiters: [(WKContentRuleList?, String?, String) -> Void] = []
    private static var ruleListCompiling = false
    private static let ruleListId = "tw-no-network-v2"

    /// The compiled list, from the store's on-disk cache when a previous launch
    /// compiled it (a lookup is a file read; compiling is ~50 ms more), else compiled now.
    static func ruleList(_ completion: @escaping (WKContentRuleList?, String?, String) -> Void) {
        if let list = cachedRuleList { return completion(list, nil, "cached") }
        ruleListWaiters.append(completion)
        if ruleListCompiling { return }
        ruleListCompiling = true
        let store = WKContentRuleListStore.default()!
        let finish = { (list: WKContentRuleList?, error: Error?, source: String) in
            DispatchQueue.main.async {
                cachedRuleList = list
                ruleListCompiling = false
                let waiters = ruleListWaiters
                ruleListWaiters = []
                waiters.forEach { $0(list, error?.localizedDescription, source) }
            }
        }
        store.lookUpContentRuleList(forIdentifier: ruleListId) { list, _ in
            if let list { return finish(list, nil, "lookup") }
            store.compileContentRuleList(forIdentifier: ruleListId, encodedContentRuleList: ruleListJson) { list, error in finish(list, error, "compiled") }
        }
    }

    /// JSON string → JavaScript string literal (also escapes U+2028/2029 and `</script`).
    static func jsStringLiteral(_ s: String) -> String {
        var out = "\""
        out.reserveCapacity(s.utf8.count + 16)
        for scalar in s.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case "\r": out += "\\r"
            case "\u{2028}": out += "\\u2028"
            case "\u{2029}": out += "\\u2029"
            case "<": out += "\\x3c"
            default:
                if scalar.value < 0x20 { out += String(format: "\\u%04x", scalar.value) } else { out.unicodeScalars.append(scalar) }
            }
        }
        return out + "\""
    }
}

/// Serves exactly one document (the renderer) and refuses every other URL in the scheme.
final class RendererSchemeHandler: NSObject, WKURLSchemeHandler {
    let documentURL: URL
    let html: String
    let csp: String
    var onBlocked: ((String) -> Void)?

    init(documentURL: URL, html: String, csp: String) {
        self.documentURL = documentURL
        self.html = html
        self.csp = csp
    }

    func webView(_ webView: WKWebView, start urlSchemeTask: WKURLSchemeTask) {
        guard let url = urlSchemeTask.request.url, url == documentURL else {
            onBlocked?(urlSchemeTask.request.url?.absoluteString ?? "")
            urlSchemeTask.didFailWithError(URLError(.resourceUnavailable))
            return
        }
        let data = Data(html.utf8)
        let headers = [
            "Content-Type": "text/html; charset=utf-8",
            "Content-Length": String(data.count),
            "Content-Security-Policy": csp,
            "X-DNS-Prefetch-Control": "off",
            "Cache-Control": "no-store",
        ]
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: headers)!
        urlSchemeTask.didReceive(response)
        urlSchemeTask.didReceive(data)
        urlSchemeTask.didFinish()
    }

    func webView(_ webView: WKWebView, stop urlSchemeTask: WKURLSchemeTask) {}
}

/// Breaks the retain cycle WKUserContentController ↔ handler.
private final class WeakMessageHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}

#if os(macOS)
/// macOS WKWebView with its context menu removed: "Reload", "Open Link in New
/// Window", "Download Linked File", "Inspect Element" and friends are either
/// navigations (cancelled anyway) or tooling a chat bubble must not expose.
final class RendererWKWebView: WKWebView {
    override func willOpenMenu(_ menu: NSMenu, with event: NSEvent) {
        menu.removeAllItems()
    }
}
#endif
