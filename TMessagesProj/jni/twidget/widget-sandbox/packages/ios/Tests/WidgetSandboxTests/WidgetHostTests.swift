import XCTest
@testable import WidgetSandbox

/// End-to-end on the real stack: engine thread → validator thread → WKWebView.
final class WidgetHostTests: XCTestCase {
    @MainActor
    func testValidatorDiagnosticsReachTheHost() async throws {
        // Two external URLs in a stylesheet the widget builds at runtime: the
        // validator must report them (url-blocked / css-dropped) and ship none.
        let html = """
        <p>x</p><script>
        var s = document.createElement('style');
        s.textContent = '@import url(http://127.0.0.1:9999/i); p { background: url(http://127.0.0.1:9999/b) }';
        document.head.appendChild(s);
        </script>
        """
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 360)
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        let window = TestWindow.makeVisible()
        window.attach(host.view)

        let deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !(latest.loaded && latest.diags.keys.contains { ["url-blocked", "css-dropped"].contains($0) }) {
            try await Task.sleep(nanoseconds: 50_000_000)
        }
        XCTAssertTrue(latest.loaded, "widget never loaded: \(latest.diags)")
        XCTAssertTrue(latest.diags.keys.contains { ["url-blocked", "css-dropped"].contains($0) }, "\(latest.diags)")
        XCTAssertNil(latest.dead)
        host.destroy()
    }

    /// A killed widget is not torn down: the WKWebView keeps the last render,
    /// the renderer marks the document frozen and shows the badge.
    @MainActor
    func testKilledWidgetKeepsLastRender() async throws {
        var options = WidgetHost.Options()
        options.cpuLimitMs = 300
        let host = try WidgetHost(html: "<p id=\"keep\">still here</p><script>for(;;){}</script>", theme: WidgetTheme(), width: 360, options: options)
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        let window = TestWindow.makeVisible()
        window.attach(host.view)
        let deadline = Date().addingTimeInterval(20)
        while Date() < deadline, latest.dead == nil { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(latest.dead?.contains("CPU limit") == true, latest.dead ?? "not dead")
        try await Task.sleep(nanoseconds: 300_000_000)
        let text = await withCheckedContinuation { c in host.evaluateInRenderer("document.body.textContent + '|' + document.documentElement.dataset.twFrozen + '|' + (document.querySelector('[data-tw-badge]') || {}).textContent") { c.resume(returning: $0) } }
        XCTAssertEqual(text, "still here" + "Widget stopped" + "|fatal|Widget stopped", text)
        XCTAssertNotNil(host.view.window, "the view is still in the window")
        host.destroy()
    }

    /// The UIKit trigger of the off-screen pause: taking the view out of the
    /// window stops frames and tells the widget it is hidden; putting it back resumes.
    /// iOS only: under `xcodebuild test` on macOS the xctest process never
    /// finishes launching NSApplication, so the window server never reports its
    /// windows visible and WebKit keeps the page `hidden` (no animation frames,
    /// timers at 1 Hz — verified with a bare WKWebView). The AppKit trigger is
    /// exercised in the macOS demo instead (see macos/README.md).
    #if os(iOS)
    @MainActor
    func testRemovingTheViewFromTheWindowPausesTheWidget() async throws {
        let html = "<p id=\"s\"></p><script>var n = 0; function step() { n++; requestAnimationFrame(step); } requestAnimationFrame(step);" +
            "function show() { document.getElementById('s').textContent += document.visibilityState + ','; } document.addEventListener('visibilitychange', show); show();</script>"
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 360)
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        let window = TestWindow.makeVisible()
        window.attach(host.view)
        var deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !(latest.loaded && latest.stats.tasks > 20) { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertTrue(latest.loaded)
        let runningTasks = latest.stats.tasks
        XCTAssertGreaterThan(runningTasks, 20, "frames should be flowing while in the window")

        host.view.removeFromSuperview()
        try await Task.sleep(nanoseconds: 400_000_000) // the visibility task and an in-flight frame
        let atHidden = latest.stats.tasks
        try await Task.sleep(nanoseconds: 1_000_000_000)
        XCTAssertEqual(latest.stats.tasks, atHidden, "no frames while out of the window")

        window.attach(host.view)
        deadline = Date().addingTimeInterval(5)
        while Date() < deadline, latest.stats.tasks < atHidden + 10 { try await Task.sleep(nanoseconds: 50_000_000) }
        XCTAssertGreaterThan(latest.stats.tasks, atHidden + 10, "frames resume back in the window")
        let seen = await withCheckedContinuation { c in host.evaluateInRenderer("document.getElementById('s').textContent") { c.resume(returning: $0) } }
        XCTAssertEqual(seen, "visible,hidden,visible,")
        XCTAssertNil(latest.dead)
        host.destroy()
    }
    #endif

    /// CSSOM attacks from the corpus: they are blocked inside the shim (which
    /// reports through the engine) and/or by the validator; both paths must
    /// land in `diagnostics.diags` within the attack runner's window.
    @MainActor
    func testCssomAttacksAreReportedAsBlocked() async throws {
        let cases: [(String, String)] = [
            ("api-css-adopted", "<p>adoptedStyleSheets</p><script>try { var s = new CSSStyleSheet(); s.replaceSync('body { background: url(http://127.0.0.1:9999/hit/api-adopted) } @import url(http://127.0.0.1:9999/hit/api-adopted-import);'); document.adoptedStyleSheets = [s]; } catch (e) { console.log('blocked:', e.name); }</script>"),
            ("api-css-insertrule", "<style>p { color: #333; }</style><p>insertRule</p><script>try { document.styleSheets[0].insertRule('body { background: url(http://127.0.0.1:9999/hit/api-insertrule) }', 0); } catch (e) { console.log('blocked:', e.name); } try { document.styleSheets[0].insertRule('@import url(http://127.0.0.1:9999/hit/api-insertrule-import);', 0); } catch (e) { console.log('blocked:', e.name); } try { document.querySelector('style').sheet.addRule('p', 'background: url(http://127.0.0.1:9999/hit/api-addrule)'); } catch (e) { console.log('blocked:', e.name); }</script>"),
        ]
        let window = TestWindow.makeVisible()
        let blockedKinds = ["url-blocked", "network-blocked", "navigation-blocked", "tag-dropped", "script-skipped", "css-dropped"]
        for (name, html) in cases {
            let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 360)
            var latest = WidgetDiagnostics()
            host.onChange = { latest = $0 }
            window.attach(host.view)
            // The first WKWebView of a cold test process can take seconds to start; poll like the Android twin does.
            let deadline = Date().addingTimeInterval(10)
            var blocked = 0
            while Date() < deadline {
                blocked = blockedKinds.reduce(0) { $0 + (latest.diags[$1]?.count ?? 0) }
                if latest.loaded, blocked > 0 { break }
                try await Task.sleep(nanoseconds: 50_000_000)
            }
            try await Task.sleep(nanoseconds: 300_000_000)
            blocked = blockedKinds.reduce(0) { $0 + (latest.diags[$1]?.count ?? 0) }
            print("\(name): loaded=\(latest.loaded) diags=\(latest.diags.mapValues { $0.count }) logs=\(latest.logs.map { $0.text })")
            XCTAssertTrue(latest.loaded, name)
            XCTAssertGreaterThan(blocked, 0, "\(name): \(latest.diags)")
            host.destroy()
            host.view.removeFromSuperview()
        }
    }
}
