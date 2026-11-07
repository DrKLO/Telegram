import XCTest
@testable import WidgetSandbox

/// `WidgetHost.Options.nativeScrolling` is an experiment outside widget profile 1 (docs/decisions.md, "Native scrolling:
/// an experiment"): the validator keeps `overflow: auto` only when the host turns it on, which a release build never does.
final class NativeScrollingTests: XCTestCase {
    private static let skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}]"
    private static let scroller = "[{\"op\":\"el\",\"id\":10,\"tag\":\"div\",\"attrs\":{}},{\"op\":\"ins\",\"parent\":3,\"id\":10,\"before\":null},{\"op\":\"style\",\"id\":10,\"name\":\"overflow-y\",\"value\":\"auto\",\"important\":false}]"

    private func validator() throws -> TrustedValidator {
        let v = try TrustedValidator(theme: "light") { _, _ in }
        _ = v.processOps(Self.skeleton)
        return v
    }

    func testOverflowIsClippedByDefault() throws {
        let out = try validator().processOps(Self.scroller)
        XCTAssertFalse(out.contains("\"auto\""), out)
    }

    func testOverflowScrollsWhenTheHostOptsIn() throws {
        let v = try validator()
        v.setNativeScrolling(true)
        let out = v.processOps(Self.scroller)
        XCTAssertTrue(out.contains("\"auto\""), out)
    }
}
