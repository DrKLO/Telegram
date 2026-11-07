import XCTest
@testable import WidgetSandbox
import WidgetSandboxProtocol

final class ReviewRegressionTests: XCTestCase {
    private func batch(_ lengths: [Double], _ joined: String) -> WidgetCanvasBatch? {
        let words: [Double] = [0, 0, 0, 0] + lengths + [Double(lengths.count)]
        return words.withUnsafeBufferPointer { CanvasWords.batch(words: $0, strings: joined) }
    }

    func testCanvasWordStringLengthsMustFitTheBytesOneByOne() {
        XCTAssertNil(batch([1e300, -1e300], ""))
        XCTAssertNil(batch([65536, -65533], "abc"))
        XCTAssertNil(batch([4, -1], "abc"))
        XCTAssertNil(batch([2, 2], "abc"))
        XCTAssertNil(batch([1.5, 1.5], "abc"))
        XCTAssertNil(batch([.infinity, -.infinity], ""))
        XCTAssertNotNil(batch([1, 2], "abc"))
        XCTAssertNotNil(batch([1, 1], "aé"))
        XCTAssertNil(batch([1e300, -1e300], "é"))
    }

    func testNativeAnimValidatorRejectsTrailingNewlinesLikeTheJSValidator() throws {
        let js = try TrustedValidator(theme: "light") { _, _ in }
        let native = try TrustedValidator(theme: "light") { _, _ in }
        native.useNativeModel(true)
        let skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"el\",\"id\":7,\"tag\":\"div\"},{\"op\":\"ins\",\"parent\":3,\"id\":7,\"before\":null}]"
        _ = js.processOps(skeleton)
        _ = native.processOps(skeleton)
        let cases = [
            "[{\"aid\":1,\"target\":7,\"keyframes\":[{\"offset\":0,\"easing\":\"linear\\n\",\"composite\":null,\"values\":{}}],\"timing\":{\"duration\":1,\"delay\":0,\"endDelay\":0,\"iterations\":1,\"iterationStart\":0,\"direction\":\"normal\",\"fill\":\"none\",\"easing\":\"linear\"},\"composite\":\"replace\",\"iterationComposite\":\"replace\",\"startTime\":1,\"holdTime\":null,\"playbackRate\":1}]",
            "[{\"aid\":2,\"target\":7,\"keyframes\":[{\"offset\":0,\"easing\":\"linear\",\"composite\":null,\"values\":{\"color\\n\":\"red\",\"opacity\":\"1\"}}],\"timing\":{\"duration\":1,\"delay\":0,\"endDelay\":0,\"iterations\":1,\"iterationStart\":0,\"direction\":\"normal\",\"fill\":\"none\",\"easing\":\"ease\\n\"},\"composite\":\"replace\",\"iterationComposite\":\"replace\",\"startTime\":1,\"holdTime\":null,\"playbackRate\":1}]",
            "[{\"aid\":3,\"target\":7,\"css\":{\"kind\":\"animation\",\"name\":\"spin\\n\",\"origin\":1},\"startTime\":null,\"holdTime\":0,\"playbackRate\":1,\"cancelled\":false}]",
        ]
        for json in cases {
            XCTAssertEqual(js.validateAnim(json), "[]", json)
            XCTAssertEqual(native.validateAnim(json), js.validateAnim(json), json)
        }
    }
}
