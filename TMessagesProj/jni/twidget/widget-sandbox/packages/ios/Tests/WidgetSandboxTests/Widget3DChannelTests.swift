import XCTest
import QuartzCore
import WidgetSandboxProtocol
@testable import WidgetSandbox

final class Widget3DChannelTests: XCTestCase {
    final class FakeSurface: Widget3DSurface {
        var onDiag: ((String, String) -> Void)?
        let lock = NSLock()
        var calls: [String] = []
        var bytes: [Int] = []
        var paused = true
        var stopped: String?
        var laidOut: [CGSize] = []

        func call(_ op: String, _ args: [Widget3DArgument]) -> HostReturn {
            lock.withLock {
                calls.append(op)
                bytes.append(args.reduce(0) { $0 + ($1.bytes?.length ?? 0) })
            }
            if op == "geometry", args.count > 3, args[3].bytes == nil { onDiag?("3d-rejected", "geometry \(args.count > 2 ? args[2].int : 0)") }
            if op == "size" { return .string("100,50,2") }
            return .undefined
        }
        func makeLayer() -> CALayer { CALayer() }
        func layout(_ layer: CALayer, size: CGSize, scale: CGFloat) { lock.withLock { laidOut.append(size) } }
        func setPaused(_ paused: Bool) { lock.withLock { self.paused = paused } }
        func stop(_ reason: String) { lock.withLock { stopped = reason } }
    }

    final class FakeBackend: Widget3DBackend {
        var isAvailable = true
        var adapterSource: String? = """
        (function (h) {
          const keep = h;
          globalThis.gl3d = {
            bind: (t) => keep('bind', t),
            call: (...a) => keep(...a),
          };
        })
        """
        let lock = NSLock()
        var made: [FakeSurface] = []
        func makeSurface() -> Widget3DSurface {
            let s = FakeSurface()
            lock.withLock { made.append(s) }
            return s
        }
    }

    final class SurfaceRenderer: Renderer, SurfaceHostingRenderer {
        let view = PlatformView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
        var onMessage: ((String) -> Void)?
        var onDiag: ((String, String) -> Void)?
        var surfaceLayer: ((String) -> CALayer?)?
        var surfaceLayout: ((String, CGSize, CGFloat, Bool) -> Void)?
        var layers: [String: CALayer] = [:]

        func load() { onMessage?("{\"type\":\"ready\"}") }

        func send(_ json: String) {
            guard let data = json.data(using: .utf8), let message = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any], let type = message["type"] as? String else { return }
            if type == "ops" {
                for case let op as [String: Any] in message["ops"] as? [Any] ?? [] {
                    var tokens: [String] = []
                    if op["name"] as? String == "data-tw3d", let v = op["value"] as? String { tokens.append(v) }
                    for case let pair as [Any] in op["attrs"] as? [Any] ?? [] where pair.first as? String == "data-tw3d" {
                        if let v = pair.last as? String { tokens.append(v) }
                    }
                    for token in tokens where layers[token] == nil {
                        if let layer = surfaceLayer?(token) {
                            layers[token] = layer
                            surfaceLayout?(token, CGSize(width: 100, height: 50), 2, true)
                        }
                    }
                }
                onMessage?("{\"type\":\"size\",\"width\":300,\"height\":100}")
            } else if type == "query", let qid = message["qid"] as? Int {
                DispatchQueue.main.async { [weak self] in self?.onMessage?("{\"type\":\"result\",\"qid\":\(qid),\"result\":null}") }
            }
        }

        func setTheme(_ theme: String) {}
        func evaluate(_ script: String, completion: @escaping (String?) -> Void) { completion(nil) }
        func destroy() {}
    }

    private func wait(_ condition: () -> Bool, timeout: TimeInterval = 10) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func host(_ html: String, backend: FakeBackend = FakeBackend(), pools: Bool = false, maxBytesPerCall: Int = Widget3DChannel.defaultMaxBytesPerCall) -> (WidgetHost, SurfaceRenderer, Widget3DChannel) {
        let renderer = SurfaceRenderer()
        let channel = Widget3DChannel(backend: backend, maxBytesPerCall: maxBytesPerCall)
        var options = WidgetHost.Options()
        options.sideChannels = [{ channel }]
        options.pools = pools
        let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        return (host, renderer, channel)
    }

    private func logs(_ host: WidgetHost) -> String {
        host.diagnostics.logs.map { $0.text }.joined(separator: "\n")
    }

    func testSurfaceReachesTheRendererAndTheHostFunctionIsHidden() {
        let backend = FakeBackend()
        let (host, renderer, channel) = host("""
        <div id="s"></div>
        <script>
        const ok = gl3d.bind(1);
        document.getElementById('s').setAttribute('data-tw3d', '1');
        gl3d.call('frame', 1, new Float32Array(16), 1);
        console.log('bound:' + ok + ' host:' + typeof __tw3d + ' size:' + gl3d.call('size', 1) + ' keys:' + Object.getOwnPropertyNames(globalThis).filter(n => /tw3d/.test(n)).length);
        </script>
        """, backend: backend)
        defer { host.destroy() }
        wait { logs(host).contains("bound:") && renderer.layers["1"] != nil }
        XCTAssertTrue(logs(host).contains("bound:1 host:undefined size:100,50,2 keys:0"), logs(host))
        XCTAssertEqual(channel.surfaceCount, 1)
        XCTAssertNotNil(renderer.layers["1"])
        let surface = backend.lock.withLock { backend.made.first }
        XCTAssertEqual(surface?.lock.withLock { surface?.calls }, ["frame", "size"])
        XCTAssertEqual(surface?.lock.withLock { surface?.bytes.first }, 64)
        XCTAssertEqual(surface?.lock.withLock { surface?.laidOut }, [CGSize(width: 100, height: 50)])
        XCTAssertEqual(surface?.lock.withLock { surface?.paused }, false)
    }

    func testAdapterFailureStillHidesTheHostFunction() {
        let backend = FakeBackend()
        backend.adapterSource = "(function (h) { globalThis.leak = h; throw new Error('boom'); })"
        let (host, _, _) = host("<script>console.log('host:' + typeof __tw3d + ' leak:' + typeof leak)</script>", backend: backend)
        defer { host.destroy() }
        wait { logs(host).contains("host:") }
        XCTAssertTrue(logs(host).contains("host:undefined"), logs(host))
        XCTAssertTrue(host.diagnostics.diags["3d"] != nil, "\(host.diagnostics.diags)")
    }

    func testSurfacesAreCappedAndForgedTokensGetNoLayer() {
        let (host, renderer, channel) = host("""
        <script>
        let bound = 0;
        for (let i = 1; i <= 40; i++) bound += gl3d.bind(i);
        const ignored = [gl3d.bind(0), gl3d.bind(-1), gl3d.bind(2000000), gl3d.bind('1'), gl3d.bind(NaN), gl3d.bind(Infinity)];
        console.log('bound:' + bound + ' ignored:' + ignored.join(','));
        </script>
        """)
        defer { host.destroy() }
        wait { logs(host).contains("bound:") }
        XCTAssertTrue(logs(host).contains("bound:\(Widget3DChannel.defaultMaxSurfaces) ignored:,,,,,"), logs(host))
        XCTAssertEqual(channel.surfaceCount, Widget3DChannel.defaultMaxSurfaces)
        for forged in ["999", "0", "-1", "01", "1e0", " 1", "1 ", "+1", "", "99999999"] {
            XCTAssertNil(renderer.surfaceLayer?(forged), forged)
        }
        XCTAssertNotNil(renderer.surfaceLayer?("1"))
    }

    func testHugeBuffersAndTooManyArgumentsAreRejected() {
        let backend = FakeBackend()
        let (host, _, _) = host("""
        <script>
        gl3d.bind(1);
        const big = new Float32Array(1 << 18);
        gl3d.call('geometry', 1, 1, big, big);
        gl3d.call('geometry', 1, 2, new Float32Array(9));
        gl3d.call('frame', 1, ...Array(20).fill(1));
        gl3d.call('x'.repeat(100), 1);
        gl3d.call('geometry', 1, 3, 'not bytes');
        gl3d.call({ toString() { return 'frame' } }, 1);
        console.log('done');
        </script>
        """, backend: backend, maxBytesPerCall: 1 << 20)
        defer { host.destroy() }
        wait { logs(host).contains("done") && host.diagnostics.diags["3d-rejected"] != nil }
        let surface = backend.lock.withLock { backend.made.first }
        XCTAssertEqual(surface?.lock.withLock { surface?.calls }, ["geometry", "geometry"], "\(String(describing: surface?.calls))")
        XCTAssertEqual(surface?.lock.withLock { surface?.bytes }, [36, 0])
        let rejected = host.diagnostics.diags["3d-rejected"]?.details ?? []
        XCTAssertTrue(rejected.contains { $0.contains("2097152 bytes") }, "\(rejected)")
    }

    func testCallsAfterDestroyDoNothingAndTheRendererIsReleased() {
        let backend = FakeBackend()
        let (host, renderer, channel) = host("""
        <div data-tw3d="1"></div>
        <script>gl3d.bind(1); globalThis.later = () => [gl3d.bind(2), gl3d.call('frame', 1, new Float32Array(4), 1), gl3d.call('size', 1)].join('|');</script>
        """, backend: backend)
        wait { channel.surfaceCount == 1 }
        let surface = backend.lock.withLock { backend.made.first }
        channel.destroy()
        XCTAssertTrue(channel.isDestroyed)
        XCTAssertEqual(channel.surfaceCount, 0)
        XCTAssertEqual(surface?.lock.withLock { surface?.stopped }, "destroyed")
        XCTAssertNil(renderer.surfaceLayer)
        XCTAssertNil(renderer.surfaceLayout)
        let js = QuickJSContext(memoryLimitBytes: 8 << 20)
        channel.install(in: js)
        XCTAssertEqual((try? js.evaluate("typeof gl3d + typeof __tw3d", filename: "t.js"))?.string, "undefinedundefined")
        channel.connect(renderer)
        XCTAssertNil(renderer.surfaceLayer)
        host.destroy()
        wait { backend.lock.withLock { backend.made.count } == 1 }
        XCTAssertEqual(backend.lock.withLock { backend.made.count }, 1)
        XCTAssertEqual(surface?.lock.withLock { surface?.calls }, [])
    }

    func testPooledHostsInstallAFreshChannelEachTime() {
        EnginePool.shared.prewarm(2)
        defer { EnginePool.shared.targetSize = 0 }
        var previous: Widget3DChannel?
        for round in 0 ..< 3 {
            let (host, _, channel) = host("<script>console.log('r:' + gl3d.bind(1) + typeof __tw3d)</script>", pools: true)
            wait { logs(host).contains("r:") }
            XCTAssertTrue(logs(host).contains("r:1undefined"), "round \(round) \(logs(host))")
            XCTAssertFalse(channel === previous)
            host.destroy()
            wait { channel.isDestroyed }
            XCTAssertTrue(channel.isDestroyed)
            previous = channel
        }
    }

    func testHostTeardownDestroysTheChannel() {
        let (host, renderer, channel) = host("<script>gl3d.bind(1)</script>")
        wait { channel.surfaceCount == 1 }
        host.destroy()
        wait { channel.isDestroyed && renderer.surfaceLayer == nil }
        XCTAssertTrue(channel.isDestroyed)
        XCTAssertNil(renderer.surfaceLayer)
        XCTAssertEqual(channel.surfaceCount, 0)
    }

    func testCallsBeforeConnectWorkAndLayersComeAfterConnect() {
        let backend = FakeBackend()
        let channel = Widget3DChannel(backend: backend)
        let js = QuickJSContext(memoryLimitBytes: 8 << 20)
        channel.install(in: js)
        XCTAssertEqual((try? js.evaluate("String(gl3d.bind(3)) + gl3d.call('size', 3) + typeof __tw3d", filename: "t.js"))?.string, "1100,50,2undefined")
        XCTAssertEqual(channel.surfaceCount, 1)
        let renderer = SurfaceRenderer()
        XCTAssertNil(renderer.surfaceLayer?("3"))
        channel.connect(renderer)
        XCTAssertNotNil(renderer.surfaceLayer?("3"))
        renderer.surfaceLayout?("3", CGSize(width: CGFloat.infinity, height: 10), 2, true)
        renderer.surfaceLayout?("3", CGSize(width: 10, height: 10), CGFloat.nan, true)
        let surface = backend.made.first
        XCTAssertEqual(surface?.laidOut, [])
        XCTAssertEqual(surface?.paused, true)
        channel.setVisible(false)
        renderer.surfaceLayout?("3", CGSize(width: 10, height: 10), 2, true)
        XCTAssertEqual(surface?.paused, true)
        channel.setVisible(true)
        renderer.surfaceLayout?("3", CGSize(width: 10, height: 10), 2, true)
        XCTAssertEqual(surface?.paused, false)
        channel.destroy()
    }

    func testBorrowedBytesCopyAndPerChannelLimits() {
        let values: [UInt8] = [1, 2, 3, 4]
        let copied = values.withUnsafeBytes { Widget3DBytes(borrowedPointer: $0.baseAddress!, length: $0.count, kind: .other).copy() }
        XCTAssertEqual(Array(copied), values)
        let channel = Widget3DChannel(backend: FakeBackend(), maxSurfaces: 1000, maxBytesPerCall: Int.max)
        XCTAssertEqual(channel.maxSurfaces, Widget3DChannel.defaultMaxSurfaces * 4)
        XCTAssertEqual(channel.maxBytesPerCall, Widget3DChannel.defaultMaxBytesPerCall)
    }

    func testUnavailableBackendInstallsNothing() {
        let backend = FakeBackend()
        backend.isAvailable = false
        let channel = Widget3DChannel(backend: backend)
        let js = QuickJSContext(memoryLimitBytes: 8 << 20)
        channel.install(in: js)
        XCTAssertEqual((try? js.evaluate("typeof gl3d + typeof __tw3d", filename: "t.js"))?.string, "undefinedundefined")
    }
}
