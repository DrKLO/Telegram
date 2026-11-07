import AppKit
import XCTest
import WidgetSandbox
import QuartzCore
@testable import TwidgetRenderer

final class CarouselMotionTests: XCTestCase {
    private final class Harness {
        let window: NSWindow
        let renderer: LiteHTMLWidgetRenderer
        let host: WidgetHost
        let queue = WidgetExecutor.makeQueue("carousel.motion.regression")
        let executor: WidgetExecutor

        init(_ file: String, html: String? = nil) throws {
            _ = NSApplication.shared
            WidgetRendererWarmup.prewarm(scale: 2, width: 420)
            window = NSWindow(contentRect: CGRect(x: 0, y: 0, width: 420, height: 700), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            renderer = LiteHTMLWidgetRenderer(theme: "light", width: 420)
            executor = WidgetExecutor(queue: queue)
            let path = URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox/corpus/\(file)")
            var options = WidgetHost.Options()
            options.asyncValidation = true
            options.sharedValidatorThread = true
            options.startBeforeRendererReady = true
            options.pools = true
            options.features = ["binaryOps", "canvasWords", "webAnimations", "morphUpdates"]
            options.policy = .strict
            let renderer = self.renderer
            host = try WidgetHost(html: html ?? String(contentsOf: path), theme: WidgetTheme(colorScheme: .light), width: 420, options: options, rendererFactory: { _, _, _ in renderer }, executor: executor)
            window.contentView?.addSubview(host.view)
            host.onChange = { [weak host] diagnostics in
                if diagnostics.height > 0 {
                    host?.view.setFrameSize(CGSize(width: 420, height: diagnostics.height))
                }
            }
        }

        deinit {
            host.destroy()
            window.close()
        }

        func node(_ id: String) -> Int {
            queue.sync { renderer.dom.publicId(renderer.dom.nodes.values.first { $0.attribute("id") == id }!) }
        }

        func event(_ type: String, target: Int, x: Double = 150) {
            executor.async { [renderer] in
                renderer.noteUserInput()
                renderer.onMessage?("{\"type\":\"event\",\"trusted\":true,\"ev\":{\"type\":\"\(type)\",\"targetId\":\(target),\"bubbles\":true,\"pointerId\":1,\"pointerType\":\"mouse\",\"isPrimary\":true,\"button\":0,\"buttons\":1,\"clientX\":\(x),\"clientY\":100}}")
            }
        }
    }

    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let end = Date().addingTimeInterval(seconds)
        while !done() && Date() < end {
            RunLoop.main.run(until: Date().addingTimeInterval(0.002))
        }
    }

    private func renderedPixels(_ h: Harness) throws -> Data {
        return try h.queue.sync {
            let root = try XCTUnwrap(h.renderer.documentView.contentLayer)
            let context = try XCTUnwrap(CGContext(data: nil, width: 260, height: 50, bitsPerComponent: 8, bytesPerRow: 260 * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue))
            context.translateBy(x: 0, y: 50)
            context.scaleBy(x: 1, y: -1)
            context.translateBy(x: -40, y: -90)
            root.render(in: context)
            return Data(bytes: try XCTUnwrap(context.data), count: 260 * 50 * 4)
        }
    }

    func testBannerArrowStartsAndRetargetsSlidingTransition() throws {
        let h = try Harness("htmlbubbles-ported/12-motion-lab/10-mo-bare.html")
        pump(3)
        XCTAssertNil(h.host.diagnostics.dead)
        let strip = h.node("strip")
        for arrow in ["arrow-r", "arrow-r", "arrow-l", "arrow-l"] {
            h.event("click", target: h.node(arrow))
            pump(0.2) { h.queue.sync { h.renderer.animatedElementIDs.contains(strip) } }
            h.queue.sync {
                XCTAssertTrue(h.renderer.animatedElementIDs.contains(strip), "\(arrow): the strip jumped without a CSS transition")
                XCTAssertNotNil(h.renderer.animationValues(strip)?.transform)
            }
            var frames: Set<Data> = []
            for _ in 0..<5 {
                frames.insert(try renderedPixels(h))
                pump(0.065)
            }
            XCTAssertGreaterThan(frames.count, 2, "The displayed banner did not advance through animation frames")
            pump(0.25)
            XCTAssertNil(h.host.diagnostics.dead)
        }
    }

    func testCarouselDragReleaseStartsSnapTransitionAfterFastPointerMoves() throws {
        let h = try Harness("gestures/2-horizontal/06-card-carousel-x.html")
        pump(0.5)
        XCTAssertNil(h.host.diagnostics.dead)
        let viewport = h.node("vp"), track = h.node("tr")
        for _ in 0..<3 {
            h.event("pointerdown", target: viewport, x: 280)
            pump(0.02)
            for x in [250.0, 220, 180, 140] {
                h.event("pointermove", target: viewport, x: x)
                pump(0.025)
            }
            h.event("pointerup", target: viewport, x: 140)
            pump(0.2) { h.queue.sync { h.renderer.animatedElementIDs.contains(track) } }
            h.queue.sync {
                XCTAssertTrue(h.renderer.animatedElementIDs.contains(track), "The carousel jumped to its snap point after dragging")
                XCTAssertNotNil(h.renderer.animationValues(track)?.transform)
            }
            var frames: Set<Data> = []
            for _ in 0..<5 {
                frames.insert(try renderedPixels(h))
                pump(0.04)
            }
            XCTAssertGreaterThan(frames.count, 2, "The displayed carousel did not advance through its snap animation")
            pump(0.2)
            XCTAssertNil(h.host.diagnostics.dead)
        }
    }

    func testRepeatedTransformUpdatesKeepTransitionsAfterScriptedPromotion() throws {
        let h = try Harness("", html: "<style>body{margin:0}#track{width:140px;height:30px;background:red;transform:translateX(0px);transition:transform .3s linear}</style><div id='track'></div>")
        pump(0.3)
        let track = h.node("track")
        for x in [30, 60, 90, 120, 150] {
            h.executor.async {
                h.renderer.beginTask()
                h.renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"style\",\"id\":\(track),\"name\":\"transform\",\"value\":\"translateX(\(x)px)\"}]}")
                h.renderer.endTask()
            }
            pump(0.2) { h.queue.sync { h.renderer.animatedElementIDs.contains(track) } }
            h.queue.sync {
                XCTAssertTrue(h.renderer.animatedElementIDs.contains(track), "update \(x) skipped the transition after promotion")
                XCTAssertGreaterThan(h.renderer.drivenCount, 0)
                XCTAssertNotEqual(h.renderer.animationValues(track)?.transform, "translateX(\(x)px)")
            }
            pump(0.33)
        }
    }

    func testPromotedDragStartsSnapFromItsLastVisiblePosition() throws {
        let h = try Harness("", html: "<style>body{margin:0}#track{width:140px;height:30px;background:red;transform:translateX(0px)}#track.snap{transition:transform 1s linear}</style><div id='track'></div>")
        pump(0.3)
        let track = h.node("track")
        for x in [20, 40, 60, 80, 100] {
            h.executor.async {
                h.renderer.beginTask()
                h.renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"style\",\"id\":\(track),\"name\":\"transform\",\"value\":\"translateX(\(x)px)\"}]}")
                h.renderer.endTask()
            }
            pump(0.05)
        }
        h.queue.sync {
            XCTAssertGreaterThan(h.renderer.stageMilliseconds["scripted-fast"] ?? 0, 0)
        }
        h.executor.async {
            h.renderer.beginTask()
            h.renderer.send("{\"type\":\"ops\",\"ops\":[{\"op\":\"attr\",\"id\":\(track),\"name\":\"class\",\"value\":\"snap\"},{\"op\":\"style\",\"id\":\(track),\"name\":\"transform\",\"value\":\"translateX(150px)\"}]}")
            h.renderer.endTask()
        }
        pump(0.2) { h.queue.sync { h.renderer.animatedElementIDs.contains(track) } }
        try h.queue.sync {
            let value = try XCTUnwrap(h.renderer.animationValues(track)?.transform)
            let transform = try XCTUnwrap(CSSTransform.parse(value, box: CGSize(width: 140, height: 30)))
            XCTAssertGreaterThanOrEqual(transform.tx, 100)
            XCTAssertLessThan(transform.tx, 140)
        }
    }
}
