import XCTest
@testable import WidgetSandbox

/// The chat-feed machinery: shared WebKit environment, prewarmed pools,
/// parallel boot and validation off the main thread — end to end on the
/// real stack, like WidgetHostTests.
final class PoolsTests: XCTestCase {
    private func wait(_ seconds: Double, until done: () -> Bool) async throws {
        let deadline = Date().addingTimeInterval(seconds)
        while Date() < deadline, !done() { try await Task.sleep(nanoseconds: 50_000_000) }
    }

    private static var everything: WidgetHost.Options {
        var o = WidgetHost.Options()
        o.sharedWebKit = true
        o.sharedValidatorThread = true
        o.pools = true
        o.parallelBoot = true
        o.asyncValidation = true
        return o
    }

    /// A host assembled from the pools loads, reports which parts were prewarmed, and gives its validator back.
    @MainActor
    func testPooledHostLoadsAndRecyclesItsValidator() async throws {
        let window = TestWindow.makeVisible()
        WidgetWarmup.prewarm(renderers: 1, engines: 1, validators: 1, width: 360, parkingView: nil)
        try await wait(10) { RendererPool.shared.count >= 1 && EnginePool.shared.count >= 1 && ValidatorPool.shared.count >= 1 }
        let recycledBefore = ValidatorPool.shared.recycled
        let host = try WidgetHost(html: "<p id=\"p\">pooled</p><script>document.getElementById('p').textContent += ' ok';</script>", theme: WidgetTheme(), width: 360, options: Self.everything)
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        window.attach(host.view)
        try await wait(15) { latest.loaded }
        XCTAssertTrue(latest.loaded, "\(latest.diags)")
        XCTAssertEqual(latest.prewarmed, "renderer+validator+engine")
        XCTAssertNil(latest.dead)
        XCTAssertTrue(latest.diags["boot"]?.details.first?.contains("prewarmed") == true, "\(latest.diags["boot"]?.details ?? [])")
        try await Task.sleep(nanoseconds: 200_000_000)
        let text = await withCheckedContinuation { c in host.evaluateInRenderer("document.body.textContent") { c.resume(returning: $0) } }
        XCTAssertEqual(text, "pooled ok")
        host.destroy()
        // The validator re-enters the pool from its own thread, after its reset.
        try await wait(5) { ValidatorPool.shared.recycled == recycledBefore + 1 }
        XCTAssertEqual(ValidatorPool.shared.recycled, recycledBefore + 1)
        RendererPool.shared.targetSize = 0; EnginePool.shared.targetSize = 0; ValidatorPool.shared.targetSize = 0
        RendererPool.shared.drain(); EnginePool.shared.drain(); ValidatorPool.shared.drain()
    }

    /// One user content controller and one scheme handler for every renderer: each widget still gets its own document and its own messages.
    @MainActor
    func testSharedEnvironmentKeepsWidgetsApart() async throws {
        let window = TestWindow.makeVisible()
        var options = WidgetHost.Options()
        options.sharedWebKit = true
        options.sharedValidatorThread = true
        options.parallelBoot = true
        let a = try WidgetHost(html: "<p>A</p><script>document.body.setAttribute('data-w', 'a')</script>", theme: WidgetTheme(), width: 300, options: options)
        let b = try WidgetHost(html: "<p>B</p><script>document.body.setAttribute('data-w', 'b')</script>", theme: WidgetTheme(), width: 300, options: options)
        var la = WidgetDiagnostics(), lb = WidgetDiagnostics()
        a.onChange = { la = $0 }
        b.onChange = { lb = $0 }
        window.attach(a.view)
        window.attach(b.view)
        try await wait(15) { la.loaded && lb.loaded }
        XCTAssertTrue(la.loaded && lb.loaded, "\(la.diags) \(lb.diags)")
        try await Task.sleep(nanoseconds: 200_000_000)
        let ta = await withCheckedContinuation { c in a.evaluateInRenderer("document.body.textContent + '|' + document.body.dataset.w + '|' + location.origin") { c.resume(returning: $0) } }
        let tb = await withCheckedContinuation { c in b.evaluateInRenderer("document.body.textContent + '|' + document.body.dataset.w + '|' + location.origin") { c.resume(returning: $0) } }
        XCTAssertTrue(ta.hasPrefix("A|a|"), ta)
        XCTAssertTrue(tb.hasPrefix("B|b|"), tb)
        XCTAssertNotEqual(ta.split(separator: "|").last, tb.split(separator: "|").last, "distinct origins")
        a.destroy()
        b.destroy()
    }

    /// Validation off the main thread keeps the order of ops and sync queries: a
    /// layout query after a mutation sees the mutation.
    @MainActor
    func testAsyncValidationKeepsOpsBeforeQueries() async throws {
        let window = TestWindow.makeVisible()
        var options = WidgetHost.Options()
        options.sharedValidatorThread = true
        options.asyncValidation = true
        options.parallelBoot = true
        let html = """
        <div id="x" style="height:50px"></div><p id="out"></p><script>
        var d = document.getElementById('x');
        d.style.height = '120px';
        document.getElementById('out').textContent = String(d.getBoundingClientRect().height);
        setTimeout(function () { d.style.height = '200px'; document.getElementById('out').textContent += ',' + d.getBoundingClientRect().height; }, 50);
        </script>
        """
        let host = try WidgetHost(html: html, theme: WidgetTheme(), width: 360, options: options)
        var latest = WidgetDiagnostics()
        host.onChange = { latest = $0 }
        window.attach(host.view)
        try await wait(15) { latest.loaded && latest.stats.tasks > 2 }
        try await Task.sleep(nanoseconds: 400_000_000)
        let text = await withCheckedContinuation { c in host.evaluateInRenderer("document.getElementById('out').textContent") { c.resume(returning: $0) } }
        XCTAssertEqual(text, "120,200")
        XCTAssertGreaterThanOrEqual(latest.stats.queries, 2)
        XCTAssertNil(latest.dead)
        host.destroy()
    }
}
