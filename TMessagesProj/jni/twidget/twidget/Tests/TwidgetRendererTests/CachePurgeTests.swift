import XCTest
import LiteHTML

final class CachePurgeTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    func testRasterCacheIsPurgedOnRequestAndWhenTheLastDocumentGoes() {
        LHDocument.purgeCaches()
        XCTAssertEqual(LHDocument.rasterCacheBytes(), 0)
        autoreleasepool {
            let document = LHDocument(html: "<div style='width:300px;height:200px;background:linear-gradient(#f00,#00f)'></div><p style='font-size:17px'>purge me</p>", css: nil)
            _ = document.layout(maxWidth: 320, preferredWidth: 320)
            _ = document.copyImage(maxWidth: 320, rect: CGRect(x: 0, y: 0, width: 320, height: 260), scale: 2)
            XCTAssertGreaterThan(LHDocument.rasterCacheBytes(), 0)
            LHDocument.purgeCaches()
            XCTAssertEqual(LHDocument.rasterCacheBytes(), 0)
            let again = document.copyImage(maxWidth: 320, rect: CGRect(x: 0, y: 0, width: 320, height: 260), scale: 2)
            XCTAssertNotNil(again)
            XCTAssertGreaterThan(LHDocument.rasterCacheBytes(), 0)
        }
        pump(5, until: { LHDocument.rasterCacheBytes() == 0 })
        XCTAssertEqual(LHDocument.rasterCacheBytes(), 0)
    }
}
