import XCTest
import AppKit
import QuartzCore
import Twidget3D
import Twidget3DSandbox
import WidgetSandbox
import WidgetSandboxProtocol

final class Twidget3DChannelTests: XCTestCase {
    override func setUpWithError() throws {
        try XCTSkipUnless(HTML3DSession.isAvailable, "Metal is not available")
    }

    private func wait(_ condition: () -> Bool, timeout: TimeInterval = 10) {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private func host(_ html: String) -> (WidgetHost, SurfaceRenderer, Widget3DChannel) {
        let renderer = SurfaceRenderer()
        let channel = Twidget3DBackend.channel()
        var options = WidgetHost.Options()
        options.sideChannels = [{ channel }]
        let host = try! WidgetHost(html: html, theme: "light", width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        return (host, renderer, channel)
    }

    private func logs(_ host: WidgetHost) -> String {
        host.diagnostics.logs.map { $0.text }.joined(separator: "\n")
    }

    private func stats(_ channel: Widget3DChannel, _ token: Int) -> HTML3DStats? {
        (channel.surface(token) as? Twidget3DSurface)?.session.stats
    }

    func testWebGLClearReachesTheSurfaceAndTheHostFunctionIsHidden() {
        let (host, renderer, channel) = host("""
        <canvas id="c" width="100" height="50"></canvas>
        <script>
        const gl = document.getElementById('c').getContext('webgl');
        console.log('gl:' + (gl ? 'yes' : 'no') + ' host:' + typeof __tw3d + ' 2d:' + (document.getElementById('c').getContext('2d') === null));
        gl.clearColor(0, 0.5, 1, 1);
        gl.clear(gl.COLOR_BUFFER_BIT);
        </script>
        """)
        defer { host.destroy() }
        wait { host.diagnostics.loaded && (stats(channel, 1)?.submitted ?? 0) > 0 }
        XCTAssertTrue(logs(host).contains("gl:yes host:undefined 2d:true"), logs(host))
        XCTAssertEqual(channel.surfaceCount, 1)
        wait { renderer.layers["1"] != nil }
        XCTAssertTrue(renderer.layers["1"] is CAMetalLayer)
    }

    func testSurfacesPerWidgetAreCappedAndForgedTokensGetNoLayer() {
        let (host, renderer, channel) = host("""
        <script>
        let made = 0;
        for (let i = 0; i < 40; i++) {
          const c = document.createElement('canvas');
          document.body.appendChild(c);
          const gl = c.getContext('webgl');
          if (gl) { gl.clearColor(1, 0, 0, 1); gl.clear(gl.COLOR_BUFFER_BIT); }
          made++;
        }
        const fake = document.createElement('div');
        fake.setAttribute('data-tw3d', '999');
        document.body.appendChild(fake);
        console.log('made:' + made);
        </script>
        """)
        defer { host.destroy() }
        wait { host.diagnostics.loaded && logs(host).contains("made:40") }
        XCTAssertEqual(channel.surfaceCount, channel.maxSurfaces)
        XCTAssertNil(renderer.surfaceLayer?("999"))
        XCTAssertNil(renderer.surfaceLayer?("0"))
        XCTAssertNil(renderer.surfaceLayer?("-1"))
    }

    func testTeardownStopsTheSessionAndReleasesTheRenderer() {
        let (host, renderer, channel) = host("""
        <canvas id="c" width="100" height="50"></canvas>
        <script>const gl = document.getElementById('c').getContext('webgl'); gl.clearColor(0, 0, 1, 1); gl.clear(gl.COLOR_BUFFER_BIT);</script>
        """)
        wait { (stats(channel, 1)?.submitted ?? 0) > 0 }
        let session = (channel.surface(1) as? Twidget3DSurface)?.session
        XCTAssertNotNil(session)
        host.destroy()
        wait { channel.isDestroyed && renderer.surfaceLayer == nil }
        XCTAssertTrue(channel.isDestroyed)
        XCTAssertEqual(session?.stopReason, "destroyed")
        XCTAssertNil(renderer.surfaceLayer)
        XCTAssertNil(renderer.surfaceLayout)
    }

    func testMalformedGeometryIsRejectedThroughTheSeam() {
        let backend = Twidget3DBackend()
        let surface = backend.makeSurface()
        var diags: [String] = []
        surface.onDiag = { k, d in diags.append(k + "|" + d) }
        let positions = [Float](repeating: 0, count: 9)
        let index: [UInt16] = [0, 1, 60000]
        positions.withUnsafeBytes { p in
            index.withUnsafeBytes { i in
                _ = surface.call("geometry", [.string("geometry"), .number(1), .number(7), .bytes(Widget3DBytes(borrowedPointer: p.baseAddress!, length: p.count, kind: .float32)), .none, .none, .number(0), .bytes(Widget3DBytes(borrowedPointer: i.baseAddress!, length: i.count, kind: .uint16))])
            }
        }
        XCTAssertEqual(diags, ["3d-rejected|geometry 7"])
        XCTAssertEqual((surface as? Twidget3DSurface)?.session.stats.rejected, 1)
        surface.stop("test")
    }
}

final class SurfaceRenderer: Renderer, SurfaceHostingRenderer {
    let view = NSView(frame: CGRect(x: 0, y: 0, width: 300, height: 100))
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

final class Twidget3DLibraryTests: XCTestCase {
    func testProvideThreeDeclinesUnderProfileOne() {
        XCTAssertFalse(Twidget3DLibrary.provideThree(to: SandboxResources()))
    }
}
