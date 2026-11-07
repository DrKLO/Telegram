import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetDefaultsTests: XCTestCase {
    private func pump(_ seconds: TimeInterval, until done: () -> Bool = { false }) {
        let deadline = Date().addingTimeInterval(seconds)
        while !done() && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
    }

    private final class Harness {
        let window: NSWindow
        let host: WidgetHost
        let renderer: LiteHTMLWidgetRenderer

        init(_ test: WidgetDefaultsTests, html: String, theme: WidgetTheme, width: CGFloat = 320) throws {
            _ = NSApplication.shared
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: width, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            var made: LiteHTMLWidgetRenderer?
            host = try WidgetHost(html: html, theme: theme, width: width, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: width, height: 600)
            window.contentView?.addSubview(host.view)
            let host = self.host
            let renderer = self.renderer
            test.pump(10, until: { host.diagnostics.loaded && host.diagnostics.rendered && renderer.dom.nodes.values.contains { $0.attribute("id") == "done" } })
            test.pump(0.3)
        }

        func close() {
            host.destroy()
            window.close()
        }

        func element(_ id: String) -> WidgetNode? {
            return renderer.dom.nodes.values.first { $0.attribute("id") == id }
        }

        func bounds(_ id: String) throws -> CGRect {
            let node = try XCTUnwrap(element(id))
            return try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
        }

        func style(_ id: String) -> [String: String] {
            guard let node = element(id) else { return [:] }
            return renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(renderer.dom.publicId(node))) ?? [:]
        }

        var height: CGFloat {
            return ceil(renderer.document?.size.height ?? 0)
        }

        func pixel(_ x: CGFloat, _ y: CGFloat) throws -> NSColor {
            let image = try XCTUnwrap(renderer.snapshot())
            let pixels = NSBitmapImageRep(cgImage: try XCTUnwrap(image.cgImage(forProposedRect: nil, context: nil, hints: nil)))
            let scale = CGFloat(pixels.pixelsWide) / image.size.width
            return try XCTUnwrap(pixels.colorAt(x: Int(x * scale), y: Int(y * scale))?.usingColorSpace(.sRGB))
        }
    }

    private static let paragraph = #"<p id="p">Hello<i id="done"></i></p>"#

    func testUAStylesheetMatchesTheProfile() throws {
        let sandbox = ProcessInfo.processInfo.environment["TWIDGET_WIDGET_SANDBOX"] ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox").path
        let url = URL(fileURLWithPath: sandbox).appendingPathComponent("packages/core/profile.json")
        guard let data = try? Data(contentsOf: url) else {
            throw XCTSkip("no widget-sandbox profile at \(url.path)")
        }
        let profile = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])
        let tables = try XCTUnwrap(profile["tables"] as? [String: Any])
        XCTAssertEqual(WidgetThemeStyle.profileUAStylesheet, try XCTUnwrap(tables["uaStylesheet"] as? String))
    }

    func testTextTokensAreCheckedAndAlwaysSet() {
        func text(_ tokens: [String: Any]?) -> [String: String] {
            var theme: [String: Any] = ["colorScheme": "light"]
            if let tokens {
                theme["text"] = tokens
            }
            return Dictionary(uniqueKeysWithValues: WidgetThemeStyle.variables(theme).filter { $0.name.hasPrefix("--tg-text-") }.map { ($0.name, $0.value) })
        }
        XCTAssertEqual(text(nil), ["--tg-text-font-size": "15px", "--tg-text-line-height": "1.35", "--tg-text-padding": "8px 12px"])
        XCTAssertEqual(text(["fontSize": 17, "lineHeight": 1.3, "padding": "6px 10px"]), ["--tg-text-font-size": "17px", "--tg-text-line-height": "1.3", "--tg-text-padding": "6px 10px"])
        XCTAssertEqual(text(["fontSize": "13.5px", "lineHeight": "2", "padding": "1em"]), ["--tg-text-font-size": "13.5px", "--tg-text-line-height": "2", "--tg-text-padding": "1em"])
        XCTAssertEqual(text(["fontSize": 15.123]), ["--tg-text-font-size": "15.12px", "--tg-text-line-height": "1.35", "--tg-text-padding": "8px 12px"])
        XCTAssertEqual(text(["fontSize": 7, "lineHeight": 3.5, "padding": "1px 2px 3px 4px 5px"]), text(nil))
        XCTAssertEqual(text(["fontSize": "15", "lineHeight": "1.3px", "padding": "8%"]), text(nil))
        XCTAssertEqual(text(["fontSize": true, "lineHeight": NSNull(), "padding": 8]), text(nil))
        XCTAssertTrue(WidgetThemeStyle.variableNames.isSuperset(of: ["--tg-text-font-size", "--tg-text-line-height", "--tg-text-padding"]))
    }

    func testMasterCSSCarriesTheThemeValues() {
        let css = WidgetThemeStyle.masterCSS(WidgetThemeStyle.variables(["colorScheme": "dark", "params": ["text_color": "#102030"], "text": ["fontSize": 18, "padding": "5px"]]))
        XCTAssertTrue(css.contains("html{color:#102030;font:18px/1.35 system-ui,sans-serif;"), css)
        XCTAssertTrue(css.contains("body{margin:5px;padding:0}"), css)
        XCTAssertTrue(css.contains("button{appearance:none;"), css)
        XCTAssertFalse(css.contains(":where("), css)
        XCTAssertFalse(css.contains("var(--tg-text-"), css)
        XCTAssertTrue(css.contains("background:var(--tg-button-bg,rgba(51,144,236,.12))"), css)
        XCTAssertFalse(css.contains("!important"), css)
        XCTAssertTrue(WidgetThemeStyle.uaStylesheet.contains("font:15px/1.35 system-ui,sans-serif"))
    }

    func testBareWidgetTakesTheReferenceInsetAndReportsTheMargins() throws {
        let h = try Harness(self, html: WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        let p = try h.bounds("p")
        XCTAssertEqual(p.minX, 12)
        XCTAssertEqual(p.minY, 8)
        XCTAssertEqual(p.width, 320 - 24)
        XCTAssertEqual(h.style("p")["font-size"], "15px")
        XCTAssertEqual(h.height, ceil(p.maxY + 8))
    }

    func testBareWidgetTakesTheClientTextTokens() throws {
        let theme = WidgetTheme(colorScheme: .light, text: WidgetTextTokens(fontSize: 20, lineHeight: 1.5, padding: "4px 6px"))
        let h = try Harness(self, html: WidgetDefaultsTests.paragraph, theme: theme)
        defer { h.close() }
        let p = try h.bounds("p")
        XCTAssertEqual(p.minX, 6)
        XCTAssertEqual(p.minY, 4)
        XCTAssertEqual(p.height, 30)
        XCTAssertEqual(h.style("p")["font-size"], "20px")
        XCTAssertEqual(h.height, ceil(p.maxY + 4))
    }

    func testAuthorBodyMarginWinsOverTheLayer() throws {
        let h = try Harness(self, html: "<style>body{margin:0}</style>" + WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .light, text: WidgetTextTokens(padding: "20px")))
        defer { h.close() }
        let p = try h.bounds("p")
        XCTAssertEqual(p.minX, 0)
        XCTAssertEqual(p.minY, 0)
        XCTAssertEqual(h.height, ceil(p.maxY))
    }

    func testSetThemeMovesTheInset() throws {
        let h = try Harness(self, html: WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        XCTAssertEqual(try h.bounds("p").minX, 12)
        h.host.setTheme(WidgetTheme(colorScheme: .dark, text: WidgetTextTokens(fontSize: 18, padding: "10px")))
        pump(3, until: { (try? h.bounds("p").minX) == 10 })
        XCTAssertEqual(try h.bounds("p").minX, 10)
        XCTAssertEqual(try h.bounds("p").minY, 10)
        XCTAssertEqual(h.style("p")["font-size"], "18px")
        XCTAssertEqual(h.style("p")["color"], "rgb(255, 255, 255)")
    }

    func testTextElementsTakeTheLayerDefaults() throws {
        let html = ##"<h1 id="h1">Title</h1><p><a id="a" href="#x">link</a> <code id="code">c</code></p><ul><li>one<ul id="inner"><li>two</li></ul></li></ul><i id="done"></i>"##
        let theme = WidgetTheme(colorScheme: .light, params: ["link_color": "#00aa00"])
        let h = try Harness(self, html: html, theme: theme)
        defer { h.close() }
        XCTAssertEqual(h.style("h1")["font-size"], "24px")
        XCTAssertEqual(h.style("h1")["font-weight"], "600")
        XCTAssertEqual(try h.bounds("h1").minY, 8)
        XCTAssertEqual(h.style("a")["color"], "rgb(0, 170, 0)")
        XCTAssertEqual(h.style("a")["text-decoration-line"], "none")
        XCTAssertEqual(h.style("inner")["list-style-type"], "circle")
    }

    func testTextTokenVariablesReadBackWhole() throws {
        let h = try Harness(self, html: WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .light, text: WidgetTextTokens(padding: "6px 10px 4px")))
        defer { h.close() }
        let root = h.renderer.dom.publicId(h.renderer.dom.html)
        var answers: [String: String] = [:]
        let previous = h.renderer.onMessage
        h.renderer.onMessage = { json in
            for name in ["padding", "size", "height"] where json.contains("\"qid\":\"\(name)\"") {
                answers[name] = json
            }
            previous?(json)
        }
        for (qid, prop) in [("padding", "--tg-text-padding"), ("size", "--tg-text-font-size"), ("height", "--tg-text-line-height")] {
            h.renderer.send(##"{"type":"query","qid":"\##(qid)","query":{"q":"cs","id":\##(root),"prop":"\##(prop)"}}"##)
        }
        pump(1, until: { answers.count == 3 })
        XCTAssertTrue(answers["padding"]?.contains("\"6px 10px 4px\"") == true, answers["padding"] ?? "")
        XCTAssertTrue(answers["size"]?.contains("\"15px\"") == true, answers["size"] ?? "")
        XCTAssertTrue(answers["height"]?.contains("\"1.35\"") == true, answers["height"] ?? "")
    }

    func testSubAndSupShiftWithTheirText() throws {
        let h = try Harness(self, html: #"<p><span id="base">x</span><sub id="sub">2</sub><sup id="sup">3</sup><i id="done"></i></p>"#, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        let base = try h.bounds("base"), sub = try h.bounds("sub"), sup = try h.bounds("sup")
        XCTAssertGreaterThan(sub.maxY, base.maxY)
        XCTAssertLessThan(sup.minY, base.minY)
        XCTAssertEqual(h.style("sup")["vertical-align"], "baseline")
        XCTAssertEqual(h.style("sup")["position"], "relative")
    }

    func testSubAndSupShiftInAFixedLineHeight() throws {
        let html = #"<style>html,body{margin:0;padding:0}body{font:13px/1.35 system-ui,sans-serif}.probes{font:13px/16px sans-serif;margin:0 8px}.row{display:block;margin:0 0 2px;width:340px}</style><div class="probes"><div class="row"><span id="base">x</span><sub id="sub">2</sub><sup id="sup">2</sup></div></div><i id="done"></i>"#
        let h = try Harness(self, html: html, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        let base = try h.bounds("base"), sub = try h.bounds("sub"), sup = try h.bounds("sup")
        XCTAssertGreaterThan(sub.maxY, base.maxY, "base \(base) sub \(sub)")
        XCTAssertLessThan(sup.minY, base.minY, "base \(base) sup \(sup)")
        let lines = h.renderer.document?.idBounds(transformed: true, lineBoxes: true) ?? [:]
        let ids = ["base", "sub", "sup"].map { h.element($0).map { h.renderer.dom.publicId($0) } ?? -1 }
        let visual = try ids.map { try XCTUnwrap(lines[$0]) }
        XCTAssertGreaterThan(visual[1].maxY, visual[0].maxY, "\(visual)")
        XCTAssertLessThan(visual[2].minY, visual[0].minY, "\(visual)")
    }

    func testComputedStyleReportsSelectionLineHeightAndTables() throws {
        let html = #"<style>#on{user-select:text}#off{-webkit-user-select:none}</style><p id="p">p</p><div id="on"><span id="child">c</span><span id="off">o</span></div><table id="t"><tr><td>x</td></tr></table><h2 id="h2">h</h2><i id="done"></i>"#
        let h = try Harness(self, html: html, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        let root = h.renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(h.renderer.dom.publicId(h.renderer.dom.html))) ?? [:]
        XCTAssertEqual(root["user-select"], "none")
        XCTAssertEqual(root["line-height"], "20.25px")
        XCTAssertEqual(h.style("p")["user-select"], "auto")
        XCTAssertEqual(h.style("on")["user-select"], "text")
        XCTAssertEqual(h.style("child")["user-select"], "auto")
        XCTAssertEqual(h.style("off")["-webkit-user-select"], "none")
        XCTAssertEqual(h.style("t")["border-collapse"], "collapse")
        XCTAssertEqual(h.style("h2")["line-height"], "25.3125px")
    }

    func testRevertSkipsTheProfileLayer() throws {
        let h = try Harness(self, html: #"<style>p{margin:0}#r{margin:revert}.box{padding:1px}</style><div class="box"><p id="r">r<i id="done"></i></p><p id="plain">p</p></div>"#, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        XCTAssertEqual(h.style("r")["margin-bottom"], "15px")
        XCTAssertEqual(h.style("r")["margin-top"], "15px")
        XCTAssertEqual(h.style("plain")["margin-bottom"], "0px")
    }

    func testRendererPaintsNoBackgroundOfItsOwn() throws {
        let h = try Harness(self, html: WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .dark, params: ["bg_color": "#ff0000"]))
        defer { h.close() }
        XCTAssertLessThan(try h.pixel(2, 2).alphaComponent, 0.01)
        XCTAssertLessThan(try h.pixel(300, 4).alphaComponent, 0.01)
    }

    func testBodyBackgroundReachesTheWholeBubble() throws {
        let h = try Harness(self, html: "<style>body{background:#ff0000}</style>" + WidgetDefaultsTests.paragraph, theme: WidgetTheme(colorScheme: .light))
        defer { h.close() }
        for (x, y) in [(2, 2), (318, h.height - 2), (160, 3)] {
            let color = try h.pixel(CGFloat(x), CGFloat(y))
            XCTAssertGreaterThan(color.alphaComponent, 0.99, "\(x),\(y)")
            XCTAssertGreaterThan(color.redComponent - max(color.greenComponent, color.blueComponent), 0.6, "\(x),\(y)")
        }
    }
}
