import XCTest
@testable import TwidgetRenderer

final class SurfaceCallbackTests: XCTestCase {
    func testSettingSurfaceCallbacksOffTheExecutorDefersPlacement() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 200)
        var scheduled: [() -> Void] = []
        renderer.isOnExecutor = { false }
        renderer.schedule = { scheduled.append($0) }
        renderer.surfaceLayer = { _ in
            XCTFail("surfaces must not be placed off the renderer's executor")
            return nil
        }
        XCTAssertEqual(scheduled.count, 1)
        renderer.surfaceLayout = { _, _, _, _ in }
        XCTAssertEqual(scheduled.count, 1)
        renderer.surfaceLayer = nil
        XCTAssertEqual(scheduled.count, 2)
    }

    func testPlacementAfterDestroyDoesNothing() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 200)
        renderer.destroy()
        renderer.surfaceLayer = { _ in
            XCTFail("a destroyed renderer places no surfaces")
            return nil
        }
        renderer.placeSurfaces()
    }

    func testSurfaceCallbacksSurviveConcurrentReadsAndWrites() {
        let renderer = LiteHTMLWidgetRenderer(theme: "light", width: 200)
        renderer.isOnExecutor = { false }
        renderer.schedule = { _ in }
        DispatchQueue.concurrentPerform(iterations: 8) { worker in
            for k in 0..<2000 {
                if (k + worker) % 2 == 0 {
                    renderer.surfaceLayout = k % 3 == 0 ? nil : { _, _, _, _ in }
                    renderer.surfaceLayer = k % 5 == 0 ? nil : { _ in nil }
                } else {
                    _ = renderer.surfaceLayout
                    _ = renderer.surfaceLayer
                }
            }
        }
    }
}
