import XCTest
@testable import WidgetSandbox

final class IconExpansionScanTests: XCTestCase {
    func testByteScanAgreesWithFoundation() {
        let pieces = ["icon:", "ICON:", "Icon:", "iCoN:", "icon", "con:", "icon :", "i\u{307}con:", "\u{130}con:", "\u{131}con:", "\u{ff29}\u{ff23}\u{ff2f}\u{ff2e}\u{ff1a}", "ic\u{f3}n:", "icön:", "icon\u{ff1a}",
                      ":", "::", "i", "c", "o", "n", "I", "C", "O", "N", "\"", "\\", "é", "🙂", "Привет", "\u{2028}", "\u{0}", " ", "x", "{\"op\":\"attr\",\"name\":\"src\",\"value\":\"", "url(", "\u{212a}", "\u{17f}"]
        var samples = pieces
        for _ in 0..<100_000 {
            samples.append((0..<Int.random(in: 0...12)).map { _ in pieces.randomElement()! }.joined())
        }
        var mismatches: [String] = []
        for s in samples where IconExpansion.mayContainIcon(s) != (s.range(of: "icon:", options: .caseInsensitive) != nil) {
            mismatches.append(s)
        }
        XCTAssertEqual(mismatches.count, 0, mismatches.prefix(10).map { $0.unicodeScalars.map { String($0.value, radix: 16) }.joined(separator: " ") }.joined(separator: "\n"))
    }
}
