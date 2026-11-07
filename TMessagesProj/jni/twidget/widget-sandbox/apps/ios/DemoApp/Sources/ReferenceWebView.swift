// Reference mode (dev tooling only, never a product path — see CLAUDE.md):
// the same corpus file rendered natively by WebKit, without the QuickJS
// sandbox, validator or renderer protocol, so the sandboxed result and its
// speed can be compared against what the engine does on its own. Mirrors the
// web prototype's `?reference=1` (vite.config.ts `/reference/` route +
// src/main.ts `updateReference`): the document gets a doctype and is served
// with `connect-src 'none'` from a private scheme that refuses every other URL.
// Lives in the demo, not in WidgetSandbox; the corpus browser hides it for
// `attacks/`.

import SwiftUI
import WebKit
import WidgetSandbox

final class ReferenceWebView: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler {
    static let scheme = "tgref"
    let webView: WKWebView
    /// `size` (height), `timing` (domContentLoaded, load, ms since navigation start), `error` (message).
    var onMessage: (([String: Any]) -> Void)?

    private let documentURL: URL
    private let handler: ReferenceSchemeHandler
    private let messageHandler = WeakHandler()
    private var documentLoaded = false

    init(html: String, theme: String, width: CGFloat) {
        documentURL = URL(string: "\(Self.scheme)://w-\(UUID().uuidString.lowercased())/")!
        handler = ReferenceSchemeHandler(documentURL: documentURL, html: Self.rewrite(html))
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        config.preferences.javaScriptCanOpenWindowsAutomatically = false
        config.defaultWebpagePreferences.allowsContentJavaScript = true
        config.setURLSchemeHandler(handler, forURLScheme: Self.scheme)
        config.userContentController.addUserScript(WKUserScript(source: Self.probeScript, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        config.userContentController.add(messageHandler, name: "ref")
        // A real initial size: WebKit skips layout for an empty view, and a widget
        // that measures at load time (`getBBox()` in d3-bar) would throw NotFoundError.
        webView = WKWebView(frame: CGRect(x: 0, y: 0, width: width, height: 300), configuration: config)
        super.init()
        messageHandler.target = self
        webView.navigationDelegate = self
        webView.uiDelegate = self
        #if os(iOS)
        webView.isOpaque = false
        webView.backgroundColor = .clear
        webView.scrollView.isScrollEnabled = false
        #elseif os(macOS)
        webView.setValue(false, forKey: "drawsBackground")
        #endif
        setTheme(theme)
    }

    private var started = false

    /// Starts the load once the web view is in the view hierarchy (called by
    /// the representable's make function): a page loaded into a never-attached
    /// WKWebView has no layout, and widgets that measure at load time
    /// (`getBBox()` in d3-bar) fail there. The web prototype's iframe is
    /// attached before it navigates for the same reason.
    func start() {
        if started { return }
        started = true
        Self.ruleList { [weak self] list in
            guard let self else { return }
            if let list { self.webView.configuration.userContentController.add(list) }
            self.webView.load(URLRequest(url: self.documentURL))
        }
    }

    /// Compiled once per process, like the sandbox renderer's list — otherwise the
    /// reference "loaded in" would carry a compilation the sandbox side only pays once.
    private static var cachedRuleList: WKContentRuleList?
    private static func ruleList(_ done: @escaping (WKContentRuleList?) -> Void) {
        if let cachedRuleList { return done(cachedRuleList) }
        WKContentRuleListStore.default().compileContentRuleList(forIdentifier: "tw-reference-no-network", encodedContentRuleList: ruleListJson) { list, _ in
            DispatchQueue.main.async { cachedRuleList = list; done(list) }
        }
    }

    func setTheme(_ theme: String) {
        #if os(iOS)
        webView.overrideUserInterfaceStyle = theme == "dark" ? .dark : .light
        #elseif os(macOS)
        webView.appearance = NSAppearance(named: theme == "dark" ? .darkAqua : .aqua)
        #endif
    }

    func destroy() {
        webView.configuration.userContentController.removeScriptMessageHandler(forName: "ref")
        webView.stopLoading()
        webView.removeFromSuperview()
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        if let body = message.body as? [String: Any] { onMessage?(body) }
    }

    // Only the document itself navigates.
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        if !documentLoaded, navigationAction.targetFrame?.isMainFrame == true, navigationAction.request.url == documentURL {
            documentLoaded = true
            return decisionHandler(.allow)
        }
        decisionHandler(.cancel)
    }
    func webView(_ webView: WKWebView, createWebViewWith configuration: WKWebViewConfiguration, for navigationAction: WKNavigationAction, windowFeatures: WKWindowFeatures) -> WKWebView? { nil }
    func webView(_ webView: WKWebView, runJavaScriptAlertPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping () -> Void) { completionHandler() }
    func webView(_ webView: WKWebView, runJavaScriptConfirmPanelWithMessage message: String, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (Bool) -> Void) { completionHandler(false) }
    func webView(_ webView: WKWebView, runJavaScriptTextInputPanelWithPrompt prompt: String, defaultText: String?, initiatedByFrame frame: WKFrameInfo, completionHandler: @escaping (String?) -> Void) { completionHandler(nil) }

    // Same as the web dev server's REFERENCE_CSP.
    static let csp = "default-src 'none'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'unsafe-inline'; img-src data: blob:; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; child-src 'none'; worker-src 'none'; form-action 'none'; base-uri 'none'"

    /// What the renderer adds to every widget, with the reference theme (`Corpus/reference.css`, written by
    /// `scripts/sync-demo-corpus.ts` from packages/core/src/shared/reference.ts): the profile's UA layer (the root and
    /// text defaults, the client-styled <button>) and the default palettes and reference text tokens as `--tg-*`.
    static let referenceCss: String = Bundle.main.url(forResource: "reference", withExtension: "css", subdirectory: "Corpus")
        .flatMap { try? String(contentsOf: $0, encoding: .utf8) } ?? ""

    /// = the web dev server's reference mode: a doctype (no library is bundled since profile 1, so a CDN
    /// script fails here as it does in the sandbox) and the renderer's own stylesheet with the reference theme
    /// right after it. On iOS also a viewport meta when the file has none:
    /// without it mobile WebKit lays the page out at 980 px and scales it down, while the sandbox renderer always declares one.
    static func rewrite(_ html: String) -> String {
        var out = html
        #if os(iOS)
        if out.range(of: #"<meta\s[^>]*name\s*=\s*["']?viewport"#, options: [.regularExpression, .caseInsensitive]) == nil {
            let meta = "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            // After <head>, else after <html>, else after the doctype (a tag before it would switch WebKit to quirks mode).
            let anchor = [#"<head\b[^>]*>"#, #"<html\b[^>]*>"#, #"^\s*<!doctype[^>]*>"#].lazy
                .compactMap { out.range(of: $0, options: [.regularExpression, .caseInsensitive]) }.first
            out.insert(contentsOf: meta, at: anchor?.upperBound ?? out.startIndex)
        }
        #endif
        if out.range(of: #"^\s*<!doctype"#, options: [.regularExpression, .caseInsensitive]) == nil { out = "<!DOCTYPE html>\n" + out }
        guard !referenceCss.isEmpty, let doctype = out.range(of: #"^\s*<!doctype[^>]*>"#, options: [.regularExpression, .caseInsensitive]) else { return out }
        out.insert(contentsOf: "<style data-tw-ua>\(referenceCss)\(DemoTheme.referenceCss)</style>", at: doctype.upperBound)
        return out
    }

    /// Reports the page's own timings, its height (like the web UI's ResizeObserver + poll) and uncaught errors.
    static let probeScript = """
    (function () {
      var post = function (m) { try { window.webkit.messageHandlers.ref.postMessage(m); } catch (e) {} };
      var last = -1;
      var size = function () {
        var h = Math.min(Math.max(Math.ceil(document.documentElement.getBoundingClientRect().height), 50), 600);
        if (h !== last) { last = h; post({ type: 'size', height: h }); }
      };
      window.addEventListener('error', function (e) { post({ type: 'error', message: String(e.message || e.error || 'error').slice(0, 300) }); });
      window.addEventListener('unhandledrejection', function (e) { post({ type: 'error', message: 'Unhandled rejection: ' + String(e.reason).slice(0, 280) }); });
      document.addEventListener('DOMContentLoaded', function () {
        size();
        try { new ResizeObserver(size).observe(document.documentElement); new ResizeObserver(size).observe(document.body); } catch (e) {}
        setInterval(size, 1000);
      });
      window.addEventListener('load', function () {
        var nav = performance.getEntriesByType('navigation')[0];
        var t0 = nav ? 0 : performance.timing.navigationStart;
        var dcl = nav ? nav.domContentLoadedEventStart : performance.timing.domContentLoadedEventStart - t0;
        post({ type: 'timing', domContentLoaded: dcl, load: nav ? nav.loadEventStart : performance.timing.loadEventStart - t0, now: performance.now() });
        size();
      });
    })();
    """

    private static let ruleListJson = """
    [{"trigger":{"url-filter":"^http://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^https://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^ws://.*"},"action":{"type":"block"}},
     {"trigger":{"url-filter":"^wss://.*"},"action":{"type":"block"}}]
    """
}

/// Serves the rewritten document; refuses everything else.
final class ReferenceSchemeHandler: NSObject, WKURLSchemeHandler {
    let documentURL: URL
    let html: String
    init(documentURL: URL, html: String) { self.documentURL = documentURL; self.html = html }

    func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
        guard let url = task.request.url, url.scheme == ReferenceWebView.scheme, url.host == documentURL.host else { return task.didFailWithError(URLError(.resourceUnavailable)) }
        let path = url.path
        let body: Data, type: String
        if url == documentURL || path == "/" || path.isEmpty {
            body = Data(html.utf8); type = "text/html; charset=utf-8"
        } else {
            return task.didFailWithError(URLError(.resourceUnavailable))
        }
        let headers = ["Content-Type": type, "Content-Length": String(body.count), "Content-Security-Policy": ReferenceWebView.csp, "Cache-Control": "no-store"]
        task.didReceive(HTTPURLResponse(url: url, statusCode: 200, httpVersion: "HTTP/1.1", headerFields: headers)!)
        task.didReceive(body)
        task.didFinish()
    }
    func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {}
}

private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        target?.userContentController(userContentController, didReceive: message)
    }
}

/// Observable façade for SwiftUI, like WidgetModel for the sandbox.
final class ReferenceModel: ObservableObject {
    @Published var height: CGFloat = 24
    /// The page's own clock: `loadEventStart` from navigation start (excludes creating the WKWebView and its process).
    @Published var loadMs: Double?
    @Published var domContentLoadedMs: Double?
    /// Wall clock from WKWebView creation to the `load` event — the number comparable with the
    /// sandbox's "loaded in N ms" (wall clock from WidgetHost creation to `loaded`).
    @Published var loadedAfterMs: Double?
    @Published var errors: [String] = []
    let view: ReferenceWebView
    let startedAt = Date()

    init(html: String, theme: String, width: CGFloat = 360) {
        view = ReferenceWebView(html: html, theme: theme, width: width)
        view.onMessage = { [weak self] m in
            guard let self else { return }
            switch m["type"] as? String {
            case "size": self.height = CGFloat((m["height"] as? NSNumber)?.doubleValue ?? 24)
            case "timing":
                if self.loadedAfterMs == nil { self.loadedAfterMs = Date().timeIntervalSince(self.startedAt) * 1000 }
                self.loadMs = (m["load"] as? NSNumber)?.doubleValue
                self.domContentLoadedMs = (m["domContentLoaded"] as? NSNumber)?.doubleValue
            case "error": if self.errors.count < 50 { self.errors.append(m["message"] as? String ?? "error") }
            default: break
            }
        }
    }

    var loaded: Bool { loadMs != nil }
    func setTheme(_ theme: String) { view.setTheme(theme) }
    deinit { view.destroy() }
}

#if canImport(UIKit)
struct ReferenceView: UIViewRepresentable {
    let model: ReferenceModel
    func makeUIView(context: Context) -> WKWebView { model.view.start(); return model.view.webView }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}
#else
struct ReferenceView: NSViewRepresentable {
    let model: ReferenceModel
    func makeNSView(context: Context) -> WKWebView { model.view.start(); return model.view.webView }
    func updateNSView(_ nsView: WKWebView, context: Context) {}
}
#endif
