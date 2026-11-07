import XCTest
import LiteHTML

final class PositionedFlexTests: XCTestCase {
    private func document(parent: String = "block", position: String = "absolute", anchor: String = "left:12px", style: String = "", width: Double = 420, previous: LHDocument? = nil) -> LHDocument {
        LHDocument(html: "<body style='margin:0'><section style='position:fixed;inset:20px;display:\(parent)'><div id='toolbar' style='position:\(position);\(anchor);bottom:20px;display:flex;gap:6px;padding:4px;\(style)'><div id='a' style='width:40px;height:34px;flex:none'></div><div id='b' style='width:50px;height:34px;flex:none'></div><div id='c' style='width:60px;height:34px;flex:none'></div></div></section></body>", css: nil, previous: previous, viewportSize: CGSize(width: width, height: 720))
    }

    func testAutoWidthToolbarFitsFlexChildren() throws {
        for parent in ["block", "flex"] {
            for position in ["absolute", "fixed"] {
                for anchor in ["left:12px", "right:12px"] {
                    var document: LHDocument?
                    for width in [420.0, 360.0, 420.0] {
                        document = self.document(parent: parent, position: position, anchor: anchor, width: width, previous: document)
                        let current = try XCTUnwrap(document)
                        _ = current.layout(maxWidth: width, preferredWidth: width)
                        let bounds = current.bounds(byAttribute: "id")
                        let toolbar = try XCTUnwrap(bounds["toolbar"]).rectValue
                        let first = try XCTUnwrap(bounds["a"]).rectValue
                        let last = try XCTUnwrap(bounds["c"]).rectValue
                        XCTAssertEqual(toolbar.width, 170, accuracy: 0.01, "\(parent) \(position) \(anchor)")
                        XCTAssertEqual(toolbar.height, 42, accuracy: 0.01)
                        XCTAssertEqual(first.minX, toolbar.minX + 4, accuracy: 0.01)
                        XCTAssertEqual(last.maxX, toolbar.maxX - 4, accuracy: 0.01)
                        let inset = position == "fixed" ? 0.0 : 20.0
                        XCTAssertEqual(toolbar.maxY, 720 - inset - 20, accuracy: 0.01)
                        if anchor.hasPrefix("left") {
                            XCTAssertEqual(toolbar.minX, inset + 12, accuracy: 0.01)
                        } else {
                            XCTAssertEqual(toolbar.maxX, width - inset - 12, accuracy: 0.01)
                        }
                    }
                }
            }
        }
    }

    func testExplicitAndConstrainedWidths() throws {
        for parent in ["block", "flex"] {
            for (anchor, style, width) in [
                ("left:12px", "width:200px", 208.0),
                ("right:12px", "min-width:240px", 248.0),
                ("left:12px", "max-width:120px", 128.0),
                ("left:12px;right:12px", "", 356.0)
            ] {
                let document = document(parent: parent, anchor: anchor, style: style)
                _ = document.layout(maxWidth: 420, preferredWidth: 420)
                let toolbar = try XCTUnwrap(document.bounds(byAttribute: "id")["toolbar"]).rectValue
                XCTAssertEqual(toolbar.width, width, accuracy: 0.01, "\(parent) \(style)")
                XCTAssertEqual(toolbar.height, 42, accuracy: 0.01)
            }
        }
    }
}
