import XCTest
import TwidgetIcons

final class TwidgetIconsTests: XCTestCase {
    func testKnownIconsBecomeInlineSVG() throws {
        let url = try XCTUnwrap(TwidgetIcons.resolver("icon:airplane"))
        XCTAssertTrue(url.hasPrefix("data:image/svg+xml,"))
        XCTAssertNil(url.range(of: "[^A-Za-z0-9._~,/:=%+;-]", options: .regularExpression), url)
        XCTAssertNotEqual(TwidgetIcons.dataURL("icon:airplane?weight=bold"), url)
    }

    func testHostileInputNeverBecomesAFetchableURL() {
        for icon in ["icon:../../etc/passwd", "icon:a\"b", "https://evil.example/x.png", "icon:" + String(repeating: "a", count: 4000), "icon:a\u{0}b", "", "icon:"] {
            if let url = TwidgetIcons.dataURL(icon) {
                XCTAssertTrue(url.hasPrefix("data:image/svg+xml,"), icon)
                XCTAssertNil(url.range(of: "[^A-Za-z0-9._~,/:=%+;-]", options: .regularExpression), icon)
            }
        }
    }
}
