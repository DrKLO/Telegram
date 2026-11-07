import XCTest
import LiteHTML
@testable import TwidgetRenderer

final class OffMainTests: XCTestCase {
    func testSurfacePoolRefillsInBackground() {
        let pool = WidgetSurfacePool(observesMemoryPressure: false)
        pool.prewarm(width: 33, height: 17, count: 2)
        let deadline = Date().addingTimeInterval(5)
        while pool.count < 2 && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.01))
        }
        XCTAssertNotNil(pool.take(width: 33, height: 17))
        pool.purge()
        XCTAssertEqual(pool.count, 0)
    }

    func testImageCacheDecodesDataURLOnce() {
        let svg = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='8' height='4'%3E%3Crect width='8' height='4' fill='red'/%3E%3C/svg%3E"
        let done = expectation(description: "prefetch")
        LHImages.prefetchURLs([svg]) {
            done.fulfill()
        }
        wait(for: [done], timeout: 5)
        let image = LHImages.copyImage(forURL: svg)
        XCTAssertNotNil(image)
        XCTAssertNil(LHImages.copyImage(forURL: "https://example.com/a.png"))
    }

    func testTeardownReleasesOffMain() {
        final class Probe {
            let released: XCTestExpectation
            init(_ released: XCTestExpectation) { self.released = released }
            deinit {
                XCTAssertFalse(Thread.isMainThread)
                released.fulfill()
            }
        }
        let released = expectation(description: "released")
        WidgetTeardown.release([Probe(released)])
        wait(for: [released], timeout: 5)
    }
}
