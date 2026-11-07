import XCTest
import AppKit
import LiteHTML
@testable import TwidgetRenderer

final class ViewportLayoutTests: XCTestCase {
    func testViewportUnitFamiliesAndMixedMath() {
        for prefix in ["", "s", "l", "d"] {
            let html = """
            <style>body{margin:0}#box{width:calc(50\(prefix)vw - 10px);height:calc(min(80\(prefix)vh, 700px) - 20px);padding:1\(prefix)vmin 1\(prefix)vmax;box-sizing:content-box}</style><div id=box></div>
            """
            let doc = LHDocument(html: html, css: nil, previous: nil, viewportSize: CGSize(width: 420, height: 760))
            _ = doc.layout(maxWidth: 420, preferredWidth: 420)
            let box = doc.bounds(matching: "#box")
            XCTAssertEqual(box.width, 215.2, accuracy: 0.1, prefix)
            XCTAssertEqual(box.height, 596.4, accuracy: 0.1, prefix)
        }
    }

    func testViewportSurvivesPatchesRestorationAndResponsiveResize() throws {
        let html = """
        <style>body{margin:0}#box{height:calc(100dvh - 40px);width:50vw}@media(max-height:600px){#box{width:75vw}}</style><div id=box style='background:red'></div>
        """
        let original = LHDocument(html: html, css: nil, previous: nil, viewportSize: CGSize(width: 420, height: 760))
        _ = original.layout(maxWidth: 420, preferredWidth: 420)
        let changed = try XCTUnwrap(LHDocument(previous: original, styles: ["box": "background:blue"], attribute: "id", css: nil))
        XCTAssertTrue(changed.reusedPrevious)
        _ = changed.layout(maxWidth: 420, preferredWidth: 420)
        _ = original.layout(maxWidth: 420, preferredWidth: 420)
        XCTAssertEqual(original.bounds(matching: "#box").height, 720, accuracy: 0.1)
        XCTAssertEqual(changed.bounds(matching: "#box").height, 720, accuracy: 0.1)
        let resized = LHDocument(html: changed.html, css: nil, previous: changed, viewportSize: CGSize(width: 420, height: 540))
        XCTAssertFalse(resized.reusedPrevious)
        _ = resized.layout(maxWidth: 420, preferredWidth: 420)
        XCTAssertEqual(resized.bounds(matching: "#box").height, 500, accuracy: 0.1)
        XCTAssertEqual(resized.bounds(matching: "#box").width, 315, accuracy: 0.1)
    }

    func testViewportResizeReusesDocumentsThatDoNotDependOnIt() throws {
        let plain = "<style>body{margin:0}</style><div id=box style='height:84px;width:70%;background:red'></div><div style='position:fixed;bottom:0;height:10px;width:10px'></div>"
        let original = LHDocument(html: plain, css: nil, previous: nil, viewportSize: CGSize(width: 420, height: 760))
        _ = original.layout(maxWidth: 420, preferredWidth: 420)
        let resized = LHDocument(html: plain, css: nil, previous: original, viewportSize: CGSize(width: 300, height: 540))
        XCTAssertTrue(resized.reusedPrevious)
        XCTAssertFalse(resized.dependsOnViewport)
        _ = resized.layout(maxWidth: 300, preferredWidth: 300)
        let fresh = LHDocument(html: plain, css: nil, previous: nil, viewportSize: CGSize(width: 300, height: 540))
        _ = fresh.layout(maxWidth: 300, preferredWidth: 300)
        XCTAssertEqual(resized.bounds(matching: "#box"), fresh.bounds(matching: "#box"))
        XCTAssertEqual(resized.size, fresh.size)
        for dependent in ["<div style='height:10vh'></div>", "<div style='width:calc(2.5svmin + 1px)'></div>", "<style>@media(max-height:600px){div{color:red}}</style><div></div>"] {
            let first = LHDocument(html: dependent, css: nil, previous: nil, viewportSize: CGSize(width: 420, height: 760))
            XCTAssertTrue(first.dependsOnViewport, dependent)
            XCTAssertFalse(LHDocument(html: dependent, css: nil, previous: first, viewportSize: CGSize(width: 420, height: 540)).reusedPrevious, dependent)
        }
        for independent in ["<div>avwh 1vwx</div>", "<div style='width:12px'>review</div>"] {
            XCTAssertFalse(LHDocument(html: independent, css: nil).dependsOnViewport, independent)
        }
    }

    func testFixedViewportDoesNotStretchIntrinsicContent() {
        let html = "<style>body{margin:0}</style><div style='height:84px;background:red'></div>"
        let intrinsic = LHDocument(html: html, css: nil)
        let viewport = LHDocument(html: html, css: nil, previous: nil, viewportSize: CGSize(width: 420, height: 760))
        XCTAssertEqual(intrinsic.layout(maxWidth: 420, preferredWidth: 420).height, 84, accuracy: 0.1)
        XCTAssertEqual(viewport.layout(maxWidth: 420, preferredWidth: 420).height, 84, accuracy: 0.1)
    }

    func testFixedSheetUsesWindowViewportAndTracksHeightOnlyResize() throws {
        _ = NSApplication.shared
        let renderer = LiteHTMLWidgetRenderer(theme: "dark", width: 420)
        defer { renderer.destroy() }
        renderer.view.setFrameSize(CGSize(width: 420, height: 760))
        renderer.load()
        let ops: [[String: Any]] = [
            ["op": "el", "id": 2, "tag": "html", "attrs": [["style", "height:100%;--vh:100dvh"]], "adopt": "html"],
            ["op": "el", "id": 7, "tag": "body", "attrs": [["style", "margin:0;height:100%;overflow:hidden"]], "adopt": "body"],
            ["op": "el", "id": 10, "tag": "div", "attrs": [["style", "height:var(--vh);width:100vw"]]],
            ["op": "ins", "parent": 7, "id": 10],
            ["op": "el", "id": 11, "tag": "div", "attrs": [["style", "position:fixed;inset:0"]]],
            ["op": "ins", "parent": 7, "id": 11],
            ["op": "el", "id": 12, "tag": "div", "attrs": [["style", "position:absolute;left:0;right:0;bottom:0;height:calc(var(--vh) - 40px);background:red"]]],
            ["op": "ins", "parent": 11, "id": 12]
        ]
        let data = try JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(decoding: data, as: UTF8.self))
        wait { renderer.elementBounds()[12] != nil }
        for height: CGFloat in [760, 540, 820] {
            renderer.view.setFrameSize(CGSize(width: 420, height: height))
            wait { abs((renderer.elementBounds()[12]?.height ?? 0) - (height - 40)) < 0.1 }
            let bounds = renderer.elementBounds()
            XCTAssertEqual(bounds[10]?.width ?? 0, 420, accuracy: 0.1)
            XCTAssertEqual(bounds[10]?.height ?? 0, height, accuracy: 0.1)
            XCTAssertEqual(bounds[11]?.height ?? 0, height, accuracy: 0.1)
            XCTAssertEqual(bounds[12]?.minY ?? 0, 40, accuracy: 0.1)
            XCTAssertEqual(bounds[12]?.height ?? 0, height - 40, accuracy: 0.1)
        }
    }

    private func wait(until done: () -> Bool) {
        let deadline = Date().addingTimeInterval(1)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }
}
