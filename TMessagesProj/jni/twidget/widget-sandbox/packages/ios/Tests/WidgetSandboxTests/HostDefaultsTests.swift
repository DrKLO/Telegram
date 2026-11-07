import XCTest
@testable import WidgetSandbox

/// Hosts that build `WidgetHost.Options()` keep the behaviour they had before
/// the executor and pacer work: validation on the main thread, a validator
/// thread per widget, the widget's scripts after the renderer's `ready`, and
/// bridge calls, logs and diagnostics after the ops that came before them.
final class HostDefaultsTests: XCTestCase {
    final class DeferredRenderer: Renderer {
        let inner = RecordingRenderer()
        var view: PlatformView { inner.view }
        var onMessage: ((String) -> Void)? { get { inner.onMessage } set { inner.onMessage = newValue } }
        var onDiag: ((String, String) -> Void)? { get { inner.onDiag } set { inner.onDiag = newValue } }
        var loaded = false
        func load() { loaded = true }
        func ready() { inner.onMessage?("{\"type\":\"ready\"}") }
        func send(_ json: String) { inner.send(json) }
        func setTheme(_ theme: String) {}
        func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
        func destroy() {}
        var opsBatches: Int { inner.messages.filter { $0.hasPrefix("{\"type\":\"ops\"") }.count }
    }

    private func pump(_ timeout: TimeInterval, _ condition: () -> Bool) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func engine(of host: WidgetHost) -> SandboxEngine {
        let fields = Dictionary(Mirror(reflecting: host).children.compactMap { c in c.label.map { ($0, c.value) } }, uniquingKeysWith: { a, _ in a })
        return fields["engine"] as! SandboxEngine
    }

    func testDefaults() {
        let options = WidgetHost.Options()
        XCTAssertFalse(options.asyncValidation)
        XCTAssertFalse(options.sharedValidatorThread)
        XCTAssertFalse(options.parallelBoot)
        XCTAssertFalse(options.startBeforeRendererReady)
        XCTAssertEqual(options.maximumHeight, 600)
    }

    func testReportedHeightsRespectTheConfiguredLimitAndCanShrink() throws {
        for (maximum, expected) in [(600.0, 600.0), (16_384.0, 2339.0)] {
            let renderer = DeferredRenderer()
            var options = WidgetHost.Options()
            options.maximumHeight = CGFloat(maximum)
            let host = try WidgetHost(html: "<p>article</p>", options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            for (reported, height) in [(2339.0, expected), (100_000.0, maximum), (320.0, 320.0), (-1.0, 50.0)] {
                renderer.onMessage?("{\"type\":\"size\",\"height\":\(reported)}")
                pump(1) { host.diagnostics.height == CGFloat(height) }
                XCTAssertEqual(host.diagnostics.height, CGFloat(height))
            }
        }
    }

    func testInvalidHeightLimitsRemainFiniteAndPositive() throws {
        let cases: [(CGFloat, CGFloat)] = [(.infinity, 600), (.nan, 600), (-1, 50), (0, 50)]
        for (maximum, expected) in cases {
            let renderer = DeferredRenderer()
            var options = WidgetHost.Options()
            options.maximumHeight = maximum
            let host = try WidgetHost(html: "<p>article</p>", options: options, rendererFactory: { _, _, _ in renderer })
            defer { host.destroy() }
            renderer.onMessage?("{\"type\":\"size\",\"height\":100000}")
            pump(1) { host.diagnostics.height == expected }
            XCTAssertEqual(host.diagnostics.height, expected)
        }
    }

    func testWidgetRunsAfterRendererReady() {
        let renderer = DeferredRenderer()
        let host = try! WidgetHost(html: "<p id='p'>x</p><script>document.getElementById('p').getBoundingClientRect();</script>", options: WidgetHost.Options(), rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        XCTAssertTrue(renderer.loaded)
        pump(0.5) { false }
        XCTAssertFalse(host.diagnostics.loaded)
        XCTAssertFalse(engine(of: host).isPrewarmed)
        XCTAssertEqual(renderer.opsBatches, 0)
        let readyAt = Date()
        renderer.ready()
        pump(5) { host.diagnostics.loaded }
        XCTAssertTrue(host.diagnostics.loaded)
        XCTAssertLessThan(Date().timeIntervalSince(readyAt), 2, "a layout query waited for the query timeout")
        XCTAssertGreaterThan(renderer.opsBatches, 0)
    }

    func testParallelBootPrewarmsWithoutRunningTheWidget() {
        let renderer = DeferredRenderer()
        var options = WidgetHost.Options()
        options.parallelBoot = true
        let host = try! WidgetHost(html: "<p>x</p>", options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let engine = engine(of: host)
        pump(5) { engine.isPrewarmed }
        XCTAssertTrue(engine.isPrewarmed)
        pump(0.3) { false }
        XCTAssertFalse(host.diagnostics.loaded)
        renderer.ready()
        pump(5) { host.diagnostics.loaded }
        XCTAssertTrue(host.diagnostics.loaded)
    }

    func testStartBeforeRendererReady() {
        let renderer = DeferredRenderer()
        var options = WidgetHost.Options()
        options.startBeforeRendererReady = true
        let host = try! WidgetHost(html: "<p>x</p>", options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        pump(5) { host.diagnostics.loaded }
        XCTAssertTrue(host.diagnostics.loaded)
        XCTAssertEqual(renderer.opsBatches, 0)
        renderer.ready()
        pump(5) { renderer.opsBatches > 0 }
        XCTAssertGreaterThan(renderer.opsBatches, 0)
    }

    private static let orderedWidget = """
        <div id='root'></div><script>
        var root = document.getElementById('root');
        for (var i = 0; i < 3000; i++) { var d = document.createElement('div'); d.className = 'c' + i; d.style.cssText = 'color: red; margin: 1px; padding: 2px'; d.textContent = 'item ' + i; root.appendChild(d); }
        setTimeout(function () { console.error('after ops'); TelegramWidget.ready(); }, 0);
        </script>
        """

    private func checkBridgeAndLogsFollowOps(asyncValidation: Bool) {
        let renderer = RecordingRenderer()
        var options = WidgetHost.Options()
        options.asyncValidation = asyncValidation
        options.sharedValidatorThread = asyncValidation
        // 3000 styled elements in one task (a batch that takes a while to validate) are past the strict caps (10 000 ops
        // per batch, the 8 MB heap); this test is about the order of what follows them.
        options.policy = .compat
        options.memoryLimitBytes = 64 << 20
        var opsAtBridge: Bool?
        var opsAtLog: Bool?
        let host = try! WidgetHost(html: Self.orderedWidget, options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        let opsSoFar = { renderer.messages.contains { $0.hasPrefix("{\"type\":\"ops\"") && $0.contains("item 2999") } }
        host.onAction = { action in if action == .ready { opsAtBridge = opsSoFar() } }
        host.onChange = { d in if opsAtLog == nil, d.logs.contains(where: { $0.text.contains("after ops") }) { opsAtLog = opsSoFar() } }
        pump(10) { opsAtBridge != nil && opsAtLog != nil }
        XCTAssertEqual(opsAtBridge, true, "the bridge call overtook the ops before it")
        XCTAssertEqual(opsAtLog, true, "the log overtook the ops before it")
        XCTAssertNil(host.diagnostics.dead)
    }

    func testBridgeAndLogsFollowEarlierOpsWithSyncValidation() {
        checkBridgeAndLogsFollowOps(asyncValidation: false)
    }

    func testBridgeAndLogsFollowEarlierOpsWithAsyncValidation() {
        checkBridgeAndLogsFollowOps(asyncValidation: true)
    }

    func testSyncValidationRunsOnTheExecutor() {
        let renderer = RecordingRenderer()
        var options = WidgetHost.Options()
        options.asyncValidation = false
        var mainOnly = true
        let host = try! WidgetHost(html: "<p>x</p><script>document.body.appendChild(document.createElement('b'));</script>", options: options, rendererFactory: { _, _, _ in renderer })
        defer { host.destroy() }
        host.onChangeOnExecutor = { _ in if !Thread.isMainThread { mainOnly = false } }
        pump(5) { host.diagnostics.rendered }
        XCTAssertTrue(host.diagnostics.rendered)
        XCTAssertTrue(mainOnly)
        XCTAssertGreaterThan(renderer.messages.filter { $0.hasPrefix("{\"type\":\"ops\"") }.count, 0)
    }
}
