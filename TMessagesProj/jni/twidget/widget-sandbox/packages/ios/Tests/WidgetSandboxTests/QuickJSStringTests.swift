import XCTest
@testable import WidgetSandbox

/// Strings cross the QuickJS boundary by length, so an embedded NUL neither truncates
/// an argument nor a result (a widget could otherwise hide the tail of a message).
final class QuickJSStringTests: XCTestCase {
    func testEmbeddedNulSurvivesArgumentsAndResults() throws {
        let js = QuickJSContext(memoryLimitBytes: 8 << 20)
        try js.evaluate("globalThis.echo = (s) => s + '|' + s.length;", filename: "t.js")
        guard case .string(let out) = try js.call("echo", ["a\u{0}b\u{0}é"]) else { return XCTFail("not a string") }
        XCTAssertEqual(out, "a\u{0}b\u{0}é|5")
        guard case .string(let made) = try js.evaluate("'x\\u0000y'", filename: "u.js") else { return XCTFail("not a string") }
        XCTAssertEqual(made, "x\u{0}y")
    }

    func testMemoryUsageReportsTheHeapAndShrinksAfterCollection() throws {
        let js = QuickJSContext(memoryLimitBytes: 32 << 20)
        try js.evaluate("globalThis.big = Array.from({length: 20000}, (_, i) => ({i, s: 'v' + i}));", filename: "m.js")
        let before = js.memoryUsage["used"] ?? 0
        XCTAssertGreaterThan(before, 1 << 20)
        try js.evaluate("globalThis.big = null;", filename: "n.js")
        js.collectGarbage()
        XCTAssertLessThan(js.memoryUsage["used"] ?? .max, before / 2)
    }
}
