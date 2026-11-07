import XCTest
@testable import WidgetSandbox

final class RendererReuseTests: XCTestCase {
    @MainActor
    private func wait(until done: () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(15)
        while Date() < deadline, !done() { try await Task.sleep(nanoseconds: 20_000_000) }
        XCTAssertTrue(done())
    }

    @MainActor
    private func evaluate(_ host: WidgetHost, _ script: String) async -> String {
        await withCheckedContinuation { c in host.evaluateInRenderer(script) { c.resume(returning: $0) } }
    }

    @MainActor
    func testRecycledHostUsesSameViewWithFreshOriginAndDocument() async throws {
        let window = TestWindow.makeVisible()
        let pool = RendererPool.shared
        pool.targetSize = 0
        pool.canRefill = { false }
        defer {
            pool.targetSize = 0
            pool.canRefill = { true }
            window.close()
        }
        pool.prewarm(1)
        var options = WidgetHost.Options()
        options.sharedWebKit = true
        options.sharedValidatorThread = true
        options.asyncValidation = true
        options.startBeforeRendererReady = true
        options.pools = true
        options.recyclesRenderers = true
        let old = try WidgetHost(html: "<style>body { color: rgb(255, 0, 0) }</style><input id='old' value='private'><script>document.body.setAttribute('data-old','yes'); setInterval(function(){ document.body.setAttribute('data-tick','yes'); }, 5)</script>", width: 360, options: options)
        window.attach(old.view)
        try await wait { old.diagnostics.loaded && old.diagnostics.firstContentMs != nil }
        let renderer = try XCTUnwrap(old.rendererObject as? RendererWebView)
        let oldURL = await evaluate(old, "window.oldState = 'private'; document.getElementById('old').focus(); location.href")
        let recycled = pool.recycled
        old.destroy()
        XCTAssertNil(renderer.onMessage)
        try await wait { pool.recycled > recycled }
        XCTAssertEqual(pool.count, 1)
        let next = try WidgetHost(html: "<button id='next'>0</button><script>var n=0; document.getElementById('next').onclick=function(){this.textContent=String(++n)}</script>", width: 360, options: options)
        defer { next.destroy() }
        window.attach(next.view)
        try await wait { next.diagnostics.loaded && next.diagnostics.firstContentMs != nil }
        XCTAssertTrue(next.rendererObject === renderer)
        let newURL = await evaluate(next, "location.href")
        XCTAssertNotEqual(oldURL, newURL)
        let clean = await evaluate(next, "[typeof oldState,document.getElementById('old')===null,document.body.hasAttribute('data-old'),document.body.hasAttribute('data-tick'),getComputedStyle(document.body).color !== 'rgb(255, 0, 0)',document.activeElement===document.body].join('|')")
        XCTAssertEqual(clean, "undefined|true|false|false|true|true")
        _ = await evaluate(next, "document.getElementById('next').click(); 'clicked'")
        try await Task.sleep(nanoseconds: 200_000_000)
        let clicked = await evaluate(next, "document.getElementById('next').textContent")
        XCTAssertEqual(clicked, "1")
        XCTAssertNil(next.diagnostics.dead)
    }

    @MainActor
    func testRepeatedReuseBootsFreshWidgetsAndRecordsLatency() async throws {
        let window = TestWindow.makeVisible()
        let pool = RendererPool.shared
        pool.targetSize = 0
        pool.canRefill = { false }
        defer { pool.targetSize = 0; pool.canRefill = { true }; window.close() }
        pool.prewarm(1)
        var options = WidgetHost.Options()
        options.sharedWebKit = true
        options.sharedValidatorThread = true
        options.asyncValidation = true
        options.startBeforeRendererReady = true
        options.pools = true
        options.recyclesRenderers = true
        var pooled: [Double] = []
        var fresh: [Double] = []
        var reusedRenderer: RendererWebView?
        for index in 0..<8 {
            for reuse in [false, true] {
                let factory: RendererFactory? = reuse ? nil : { resources, theme, _ in
                    RendererWebView(rendererBundle: resources.rendererBundle, theme: theme, environment: .shared)
                }
                var sampleOptions = options
                sampleOptions.recyclesRenderers = reuse
                let host = try WidgetHost(html: "<div style='height:200px'>Widget \(index)</div>", width: 360, options: sampleOptions, rendererFactory: factory)
                window.attach(host.view)
                try await wait { host.diagnostics.firstContentMs != nil && host.diagnostics.loaded }
                XCTAssertNil(host.diagnostics.dead)
                if reuse {
                    let renderer = try XCTUnwrap(host.rendererObject as? RendererWebView)
                    if let reusedRenderer { XCTAssertTrue(renderer === reusedRenderer) }
                    reusedRenderer = renderer
                    XCTAssertTrue(renderer.webView.backForwardList.backList.isEmpty)
                    if index > 0 { pooled.append(host.diagnostics.firstContentMs!) }
                } else if index > 0 {
                    fresh.append(host.diagnostics.firstContentMs!)
                }
                host.destroy()
                if reuse { try await wait { pool.count == 1 } }
            }
        }
        let median: ([Double]) -> Double = { $0.sorted()[$0.count / 2] }
        print("WebKit first-content median: fresh=\(median(fresh))ms reused=\(median(pooled))ms; same WKWebView reused for 8 widgets")
    }

    @MainActor
    func testPoolIsBoundedAndDrainCancelsPendingReset() async throws {
        let pool = RendererPool.shared
        pool.targetSize = 0
        pool.canRefill = { false }
        defer { pool.targetSize = 0; pool.canRefill = { true } }
        pool.targetSize = 1
        let renderers = (0..<4).map { _ in RendererWebView(rendererBundle: SandboxResources.shared.rendererBundle, theme: "light", environment: .shared) }
        renderers.forEach { pool.recycle($0) }
        XCTAssertEqual(pool.resettingCount, 2)
        XCTAssertFalse(renderers[2].isUsable)
        XCTAssertFalse(renderers[3].isUsable)
        pool.drain()
        XCTAssertEqual(pool.resettingCount, 0)
        XCTAssertTrue(renderers.allSatisfy { !$0.isUsable })
        try await Task.sleep(nanoseconds: 300_000_000)
        XCTAssertEqual(pool.count, 0)
    }

    @MainActor
    func testMemoryPressureDrainsAndSuppressesRefill() async throws {
        let pool = RendererPool()
        pool.canRefill = { false }
        pool.prewarm(1)
        let renderer = try XCTUnwrap(pool.take())
        pool.recycle(renderer)
        pool.releaseForMemoryPressure()
        pool.prewarm(1)
        XCTAssertEqual(pool.count, 0)
        XCTAssertEqual(pool.resettingCount, 0)
        XCTAssertFalse(renderer.isUsable)
        try await Task.sleep(nanoseconds: 100_000_000)
        XCTAssertNil(pool.take())
        pool.targetSize = 0
    }

    @MainActor
    func testTerminatedIdleRendererIsNeverTaken() {
        let pool = RendererPool.shared
        pool.targetSize = 0
        pool.canRefill = { false }
        defer { pool.targetSize = 0; pool.canRefill = { true } }
        pool.prewarm(1)
        let renderer = pool.take()!
        renderer.webViewWebContentProcessDidTerminate(renderer.webView)
        pool.recycle(renderer)
        XCTAssertEqual(pool.count, 0)
        XCTAssertNil(pool.take())
        XCTAssertFalse(renderer.isUsable)
    }
}
