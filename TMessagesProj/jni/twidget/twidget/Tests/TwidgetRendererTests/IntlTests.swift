import XCTest
@testable import HTMLScript

final class IntlTests: XCTestCase {
    private func run(_ expression: String) throws -> String {
        let context = QuickJSScriptRuntime().makeContext(name: "test", callLimit: 1) as! QuickJSScriptContext
        return try context.context.evaluate(expression, filename: "t.js").string ?? ""
    }

    func testDateTimeFormatWithTimeZone() throws {
        let value = try run("new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23' }).format(Date.UTC(2026, 9, 12, 7, 5, 9))")
        XCTAssertEqual(value, "07:05:09")
        let day = try run("new Intl.DateTimeFormat('en-GB', { timeZone: 'UTC', weekday: 'long', day: 'numeric', month: 'short' }).format(Date.UTC(2026, 9, 12))")
        XCTAssertEqual(day, "Monday 12 Oct")
    }

    func testNumberFormatting() throws {
        XCTAssertEqual(try run("(1234567.891).toLocaleString('en-US', { maximumFractionDigits: 1 })"), "1,234,567.9")
        XCTAssertEqual(try run("new Intl.NumberFormat('en-US', { style: 'percent' }).format(0.42)"), "42%")
        XCTAssertEqual(try run("new Intl.PluralRules('en-US').select(1)"), "one")
        XCTAssertEqual(try run("['b', 'a', 'C'].sort(new Intl.Collator('en').compare).join('')"), "abC")
    }
}
