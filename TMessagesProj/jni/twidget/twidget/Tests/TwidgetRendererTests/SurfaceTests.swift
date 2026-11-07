import XCTest
import AppKit
@testable import TwidgetRenderer

final class SurfaceTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private final class Provider {
        var layers: [String: CALayer] = [:]
        var requested: [String] = []
        var layouts: [(token: String, size: CGSize, active: Bool)] = []

        func attach(_ renderer: LiteHTMLWidgetRenderer) {
            renderer.surfaceLayout = { [unowned self] token, size, _, active in
                self.layouts.append((token, size, active))
            }
            renderer.surfaceLayer = { [unowned self] token in
                self.requested.append(token)
                let layer = CALayer()
                self.layers[token] = layer
                return layer
            }
        }
    }

    private func makeRenderer(height: CGFloat, ops: String) -> (LiteHTMLWidgetRenderer, NSWindow, Provider) {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 900), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 400)
        renderer.onMessage = { _ in }
        renderer.view.frame = NSRect(x: 0, y: 0, width: 400, height: height)
        window.contentView?.addSubview(renderer.view)
        let provider = Provider()
        provider.attach(renderer)
        renderer.load()
        renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":[],\"adopt\":\"html\"},{\"op\":\"el\",\"id\":7,\"tag\":\"body\",\"attrs\":[[\"style\",\"margin:0\"]],\"adopt\":\"body\"}," + ops + "]}")
        return (renderer, window, provider)
    }

    func testSurfaceFollowsItsElementAndTheClipFollowsTheViewHeight() {
        let (renderer, window, provider) = makeRenderer(height: 300, ops: "{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:500px;margin-top:20px\"],[\"data-tw3d\",\"1\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null}")
        defer { renderer.destroy(); window.orderOut(nil) }
        pump(3, until: { provider.layers["1"]?.superlayer != nil })
        let layer = provider.layers["1"]
        XCTAssertEqual(layer?.frame, CGRect(x: 0, y: 20, width: 400, height: 500))
        XCTAssertEqual(layer?.superlayer?.frame.size, CGSize(width: 400, height: 300))
        XCTAssertEqual(layer?.superlayer?.masksToBounds, true)
        XCTAssertEqual(provider.layouts.last?.size, CGSize(width: 400, height: 500))
        renderer.view.frame.size.height = 600
        XCTAssertEqual(layer?.superlayer?.frame.size, CGSize(width: 400, height: 600))
    }

    func testOnlyNumericTokensAreAskedForLayers() {
        let (renderer, window, provider) = makeRenderer(height: 300, ops: "{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:50px\"],[\"data-tw3d\",\"1\\\"],x[\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null},{\"op\":\"el\",\"id\":10,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:50px\"],[\"data-tw3d\",\"abc\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":10,\"before\":null},{\"op\":\"el\",\"id\":11,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:50px\"],[\"data-tw3d\",\"7\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":11,\"before\":null}")
        defer { renderer.destroy(); window.orderOut(nil) }
        pump(3, until: { !provider.requested.isEmpty })
        pump(0.2)
        XCTAssertEqual(Set(provider.requested), ["7"])
    }

    func testFrozenWidgetPausesItsSurfacesAndKeepsTheBadgeOnTop() {
        let (renderer, window, provider) = makeRenderer(height: 300, ops: "{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:200px\"],[\"data-tw3d\",\"1\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null}")
        defer { renderer.destroy(); window.orderOut(nil) }
        pump(3, until: { provider.layers["1"]?.superlayer != nil })
        XCTAssertEqual(provider.layouts.last?.active, true)
        renderer.send("{\"type\":\"frozen\",\"reason\":\"Widget stopped\",\"fatal\":true}")
        XCTAssertEqual(provider.layouts.last?.active, false)
        let container = provider.layers["1"]?.superlayer
        let badge = renderer.view.layer?.sublayers?.flatMap { [$0] + ($0.sublayers ?? []) }.first { $0 is CATextLayer }
        XCTAssertNotNil(badge)
        XCTAssertGreaterThan(badge?.zPosition ?? 0, container?.zPosition ?? 0)
    }

    func testDestroyReleasesSurfaces() {
        let (renderer, window, provider) = makeRenderer(height: 300, ops: "{\"op\":\"el\",\"id\":9,\"tag\":\"div\",\"attrs\":[[\"style\",\"height:200px\"],[\"data-tw3d\",\"1\"]]},{\"op\":\"ins\",\"parent\":7,\"id\":9,\"before\":null}")
        defer { window.orderOut(nil) }
        pump(3, until: { provider.layers["1"]?.superlayer != nil })
        renderer.destroy()
        XCTAssertEqual(provider.layouts.last?.active, false)
        XCTAssertNil(provider.layers["1"]?.superlayer?.superlayer)
    }
}
