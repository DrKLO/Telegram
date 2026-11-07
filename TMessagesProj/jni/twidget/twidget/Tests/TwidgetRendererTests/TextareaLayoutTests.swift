import XCTest
import LiteHTML

final class TextareaLayoutTests: XCTestCase {
    private func document(_ attributes: String, text: String = "a", style: String = "") -> LHDocument {
        let doc = LHDocument(html: """
        <style>body{margin:0}textarea{display:block;width:300px;font:16px/20px monospace;padding:6px 10px;border:2px solid #777;resize:none;\(style)}</style><textarea id=field \(attributes)>\(text)</textarea>
        """, css: nil)
        doc.layout(maxWidth: 420, preferredWidth: 420)
        return doc
    }

    func testRowsDetermineAutomaticHeightRegardlessOfContentAndBoxSizing() {
        for sizing in ["content-box", "border-box"] {
            for (attributes, rows) in [("", 2), ("rows=1", 1), ("rows=4", 4), ("rows=0", 2), ("rows=-3", 2), ("rows=invalid", 2)] {
                for text in ["", "a", "one\ntwo\nthree\nfour\nfive", String(repeating: "long text ", count: 20)] {
                    let doc = document(attributes, text: text, style: "height:auto;box-sizing:\(sizing)")
                    XCTAssertEqual(doc.bounds(matching: "#field").height, CGFloat(rows * 20 + 16), accuracy: 0.01, "\(sizing) \(attributes) \(text.debugDescription)")
                }
            }
        }
    }

    func testDefaultAndExplicitHeightsRespectPaddingAndConstraints() {
        for (style, expected): (String, CGFloat) in [("", 56), ("height:40px", 56), ("height:40px;box-sizing:border-box", 40), ("min-height:80px", 96), ("max-height:30px", 46), ("min-height:80px;box-sizing:border-box", 80), ("max-height:30px;box-sizing:border-box", 30)] {
            XCTAssertEqual(document("", style: style).bounds(matching: "#field").height, expected, accuracy: 0.01, style)
        }
    }

    func testScrollMetricsIncludeTextAndPaddingButExcludeBorders() throws {
        let cases: [(String, CGFloat)] = [("", 36), ("one", 36), ("one\n", 60), ("one\n\n", 84), ("\n", 60), ("\n\n", 84), ("one two three four five six seven eight nine ten", 84), ("     ", 36)]
        for (text, height) in cases {
            let doc = document("", text: text.hasPrefix("\n") ? "\n" + text : text, style: "width:200px;height:0;line-height:24px")
            let metrics = try XCTUnwrap(doc.textareaMetrics(attribute: "id", value: "field"))
            XCTAssertEqual(metrics["clientWidth"]?.doubleValue, 220)
            XCTAssertEqual(metrics["clientHeight"]?.doubleValue, 12)
            XCTAssertEqual(metrics["clientLeft"]?.doubleValue, 2)
            XCTAssertEqual(metrics["clientTop"]?.doubleValue, 2)
            XCTAssertEqual(metrics["scrollWidth"]?.doubleValue, 220, text.debugDescription)
            XCTAssertEqual(metrics["scrollHeight"]?.doubleValue, Double(height), text.debugDescription)
        }
        let doc = document("rows=4", text: "a", style: "box-sizing:border-box")
        let metrics = try XCTUnwrap(doc.textareaMetrics(attribute: "id", value: "field"))
        XCTAssertEqual(metrics["clientHeight"]?.doubleValue, 92)
        XCTAssertEqual(metrics["scrollHeight"]?.doubleValue, 92)
    }
}
