import XCTest
import AppKit
import WidgetSandbox
@testable import TwidgetRenderer

final class WidgetThemeTests: XCTestCase {
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

        init(_ test: WidgetThemeTests, html: String, theme: WidgetTheme) throws {
            _ = NSApplication.shared
            window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 400, height: 600), styleMask: [.borderless], backing: .buffered, defer: false)
            window.isReleasedWhenClosed = false
            window.orderFrontRegardless()
            var made: LiteHTMLWidgetRenderer?
            host = try WidgetHost(html: html, theme: theme, width: 400, rendererFactory: { _, theme, width in
                let renderer = LiteHTMLWidgetRenderer(theme: theme, width: width)
                made = renderer
                return renderer
            })
            renderer = try XCTUnwrap(made)
            host.view.frame = NSRect(x: 0, y: 0, width: 400, height: 600)
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

        func style(_ id: String) -> [String: String] {
            guard let node = element(id) else { return [:] }
            return renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(renderer.dom.publicId(node))) ?? [:]
        }

        var rootStyle: String {
            return renderer.dom.styleValue(renderer.dom.html, animated: nil, theme: "light") ?? ""
        }

        func center(_ id: String) throws -> CGPoint {
            let node = try XCTUnwrap(element(id))
            let box = try XCTUnwrap(renderer.elementBounds()[renderer.dom.publicId(node)])
            return CGPoint(x: box.midX, y: box.midY)
        }

        func send(_ type: NSEvent.EventType, _ point: CGPoint) {
            let view = renderer.view
            let location = view.convert(point, to: nil)
            let event = NSEvent.mouseEvent(with: type, location: location, modifierFlags: [], timestamp: ProcessInfo.processInfo.systemUptime, windowNumber: window.windowNumber, context: nil, eventNumber: 0, clickCount: 1, pressure: type == .leftMouseDown ? 1 : 0)!
            switch type {
            case .leftMouseDown: view.mouseDown(with: event)
            case .leftMouseUp: view.mouseUp(with: event)
            default: view.mouseMoved(with: event)
            }
        }
    }

    static let html = #"""
<!doctype html>
<html>
<head>
<style>
:root{--tg-theme-bg-color:#000000 !important;--tg-button-bg:#000000 !important;--tg-button-pressed-opacity:1 !important}
html{--tg-theme-text-color:#000000 !important}
body{margin:0}
.box{height:10px}
#bg{background:var(--tg-theme-bg-color)}
#text{background:var(--tg-theme-text-color)}
#link{background:var(--tg-theme-link-color)}
#hint{background:var(--tg-theme-hint-color)}
#secondary{background:var(--tg-theme-secondary-bg-color)}
#scheme{background:var(--tg-button-height,#010203)}
.styled{background-color:#ff00ff;padding:2px}
</style>
</head>
<body>
<div id="bg" class="box"></div>
<div id="text" class="box"></div>
<div id="link" class="box"></div>
<div id="hint" class="box"></div>
<div id="secondary" class="box"></div>
<div id="scheme" class="box"></div>
<button id="plain">Plain</button>
<button id="own" style="background:#00ff00;color:#0000ff">Own</button>
<button id="cls" class="styled">Class</button>
<button id="off" disabled>Off</button>
<script>
document.documentElement.style.setProperty('--tg-theme-link-color', '#000000', 'important');
document.documentElement.setAttribute('style', '--tg-theme-hint-color:#000000 !important');
var done = document.createElement('i');
done.id = 'done';
document.body.appendChild(done);
</script>
</body>
</html>
"""#

    static let theme = WidgetTheme(
        colorScheme: .light,
        params: ["bg_color": "#123456", "text_color": "#ABCDEF", "link_color": "#00aa00", "hint_color": "#aa0000", "secondary_bg_color": "#fff"],
        button: WidgetButtonTokens(bg: "#FF0000", text: "#00ff00", radius: "4px", font: "bold 14px Arial", padding: "3px 5px", height: "28", pressedOpacity: 0.25, disabledOpacity: 2))

    func testVariablesUseDefaultsForMissingParams() {
        let light = WidgetThemeStyle.variables(["colorScheme": "light"])
        XCTAssertEqual(light.count, 19)
        XCTAssertEqual(light.first?.name, "--tg-theme-bg-color")
        XCTAssertEqual(light.first?.value, "#ffffff")
        XCTAssertEqual(light[15].name, "--tg-color-scheme")
        XCTAssertEqual(light[15].value, "light")
        XCTAssertEqual(light.last?.name, "--tg-text-padding")
        let dark = Dictionary(uniqueKeysWithValues: WidgetThemeStyle.variables(["colorScheme": "dark", "params": ["bg_color": "#0A0B0C"]]).map { ($0.name, $0.value) })
        XCTAssertEqual(dark["--tg-theme-bg-color"], "#0a0b0c")
        XCTAssertEqual(dark["--tg-theme-text-color"], "#ffffff")
        XCTAssertEqual(dark["--tg-theme-secondary-bg-color"], "#181818")
        XCTAssertEqual(dark["--tg-theme-bottom-bar-bg-color"], "#212121")
        XCTAssertEqual(dark["--tg-color-scheme"], "dark")
        XCTAssertEqual(WidgetThemeStyle.variables(nil).count, 19)
        XCTAssertEqual(WidgetThemeStyle.variables(["colorScheme": "sepia"])[15].value, "light")
        XCTAssertEqual(WidgetThemeStyle.variableNames.count, 27)
    }

    func testInvalidTokensAreDropped() {
        let button: [String: Any] = [
            "bg": "#AbCdEf", "text": "#fff", "radius": "0", "font": "italic 600 13.5px/1.25 system-ui, sans-serif",
            "padding": "1px 2px 3px 4px", "height": "32.125px", "pressedOpacity": 0.7, "disabledOpacity": "1.000",
        ]
        let valid = Dictionary(uniqueKeysWithValues: WidgetThemeStyle.variables(["button": button]).map { ($0.name, $0.value) })
        XCTAssertEqual(valid["--tg-button-bg"], "#abcdef")
        XCTAssertNil(valid["--tg-button-text"])
        XCTAssertEqual(valid["--tg-button-radius"], "0")
        XCTAssertEqual(valid["--tg-button-font"], "italic 600 13.5px/1.25 system-ui, sans-serif")
        XCTAssertEqual(valid["--tg-button-padding"], "1px 2px 3px 4px")
        XCTAssertEqual(valid["--tg-button-height"], "32.125px")
        XCTAssertEqual(valid["--tg-button-pressed-opacity"], "0.7")
        XCTAssertEqual(valid["--tg-button-disabled-opacity"], "1.000")

        let bad: [String: Any] = [
            "bg": "#ffffff\n", "text": "red", "radius": "8", "font": "bold 14px Arial", "padding": "1px  2px",
            "height": "12345px", "pressedOpacity": true, "disabledOpacity": 2,
        ]
        let dropped = WidgetThemeStyle.variables(["button": bad, "params": ["bg_color": "#fff", "text_color": "#12345g", "link_color": 255]])
        XCTAssertEqual(dropped.count, 19)
        XCTAssertFalse(dropped.contains { $0.name.hasPrefix("--tg-button-") })
        XCTAssertEqual(dropped.first { $0.name == "--tg-theme-bg-color" }?.value, "#ffffff")
        XCTAssertEqual(dropped.first { $0.name == "--tg-theme-link-color" }?.value, "#3390ec")

        let more: [[String: Any]] = [
            ["font": "14px ui-rounded"], ["font": "normal normal 14px system-ui"], ["padding": "1px 2px 3px 4px 5px"],
            ["padding": "0 1em"], ["pressedOpacity": ".5"], ["pressedOpacity": 1], ["pressedOpacity": "1.5"], ["radius": "1.2345px"],
        ]
        let expected: [String?] = ["14px ui-rounded", "normal normal 14px system-ui", nil, "0 1em", ".5", "1", nil, nil]
        let names = ["--tg-button-font", "--tg-button-font", "--tg-button-padding", "--tg-button-padding", "--tg-button-pressed-opacity", "--tg-button-pressed-opacity", "--tg-button-pressed-opacity", "--tg-button-radius"]
        for (index, tokens) in more.enumerated() {
            let value = WidgetThemeStyle.variables(["button": tokens]).first { $0.name == names[index] }?.value
            XCTAssertEqual(value, expected[index], "\(tokens)")
        }
    }

    func testThemeParamsMatchTheSdkPolicyFixture() throws {
        let sandbox = ProcessInfo.processInfo.environment["TWIDGET_WIDGET_SANDBOX"] ?? URL(fileURLWithPath: #filePath).deletingLastPathComponent().appendingPathComponent("../../../widget-sandbox").path
        let url = URL(fileURLWithPath: sandbox).appendingPathComponent("packages/core/tests/fixtures/sdk-policy.json")
        guard let data = try? Data(contentsOf: url) else {
            throw XCTSkip("no widget-sandbox fixtures at \(url.path)")
        }
        let fixture = try XCTUnwrap(try JSONSerialization.jsonObject(with: data, options: [.fragmentsAllowed]) as? [String: Any])
        let cases = try XCTUnwrap(fixture["themeParams"] as? [[String: Any]])
        XCTAssertFalse(cases.isEmpty)
        for item in cases {
            let input = item["input"].flatMap { $0 is NSNull ? nil : $0 }
            XCTAssertEqual(WidgetThemeStyle.checkParams(input), try XCTUnwrap(item["expect"] as? [String: String]), "\(String(describing: input))")
        }
    }

    func testUAStylesheetKeepsTheProfileText() {
        XCTAssertTrue(WidgetThemeStyle.uaStylesheet.hasPrefix("@layer tw-ua{html{color:#000000;font:15px/1.35 system-ui,sans-serif;"))
        XCTAssertTrue(WidgetThemeStyle.uaStylesheet.contains("}button:not([disabled]):active{opacity:var(--tg-button-pressed-opacity,.7)}button[disabled]{opacity:var(--tg-button-disabled-opacity,.5);cursor:default}}"))
        XCTAssertTrue(WidgetThemeStyle.uaStylesheet.contains("html{color:#000000;font:15px/1.35 system-ui,sans-serif;-webkit-text-size-adjust:none;text-size-adjust:none;overflow-wrap:break-word;-webkit-user-select:none;user-select:none;-webkit-touch-callout:none}"))
        XCTAssertFalse(WidgetThemeStyle.uaStylesheet.contains(":where("))
        XCTAssertFalse(WidgetThemeStyle.uaStylesheet.contains("!important"))
    }

    func testRootVariablesWinOverWidgetRules() throws {
        let h = try Harness(self, html: WidgetThemeTests.html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        let root = h.rootStyle
        XCTAssertTrue(root.contains("--tg-theme-bg-color:#123456 !important;"), root)
        XCTAssertTrue(root.contains("--tg-theme-secondary-bg-color:#f4f4f5 !important;"), root)
        XCTAssertTrue(root.contains("--tg-color-scheme:light !important;"), root)
        XCTAssertTrue(root.contains("--tg-button-bg:#ff0000 !important;"), root)
        XCTAssertFalse(root.contains("#000000"), root)
        XCTAssertFalse(root.contains("--tg-button-font"), root)
        XCTAssertFalse(root.contains("--tg-button-height"), root)
        XCTAssertFalse(root.contains("--tg-button-disabled-opacity"), root)
        XCTAssertEqual(h.style("bg")["background-color"], "rgb(18, 52, 86)")
        XCTAssertEqual(h.style("text")["background-color"], "rgb(171, 205, 239)")
        XCTAssertEqual(h.style("link")["background-color"], "rgb(0, 170, 0)")
        XCTAssertEqual(h.style("hint")["background-color"], "rgb(170, 0, 0)")
        XCTAssertEqual(h.style("secondary")["background-color"], "rgb(244, 244, 245)")
        XCTAssertEqual(h.style("scheme")["background-color"], "rgb(1, 2, 3)")
    }

    func testButtonsTakeTheTokensUnlessStyled() throws {
        let h = try Harness(self, html: WidgetThemeTests.html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        let plain = h.style("plain")
        XCTAssertEqual(plain["background-color"], "rgb(255, 0, 0)")
        XCTAssertEqual(plain["color"], "rgb(0, 255, 0)")
        XCTAssertEqual(plain["padding-top"], "3px")
        XCTAssertEqual(plain["padding-left"], "5px")
        XCTAssertEqual(plain["border-top-width"], "0px")
        XCTAssertEqual(plain["font-size"], "14px")
        XCTAssertEqual(plain["font-weight"], "500")
        XCTAssertEqual(plain["opacity"], "1")

        let own = h.style("own")
        XCTAssertEqual(own["background-color"], "rgb(0, 255, 0)")
        XCTAssertEqual(own["color"], "rgb(0, 0, 255)")
        XCTAssertEqual(own["padding-top"], "3px")
        let styled = h.style("cls")
        XCTAssertEqual(styled["background-color"], "rgb(255, 0, 255)")
        XCTAssertEqual(styled["padding-top"], "2px")

        let off = h.style("off")
        XCTAssertEqual(off["background-color"], "rgb(255, 0, 0)")
        XCTAssertEqual(off["opacity"], "0.5")

        let point = try h.center("plain")
        h.send(.mouseMoved, point)
        h.send(.leftMouseDown, point)
        pump(0.5, until: { h.style("plain")["opacity"] == "0.25" })
        XCTAssertEqual(h.style("plain")["opacity"], "0.25")
        h.send(.leftMouseUp, point)
        pump(0.5, until: { h.style("plain")["opacity"] == "1" })
        XCTAssertEqual(h.style("plain")["opacity"], "1")
    }

    func testThemeBeforeTheDocumentApplies() {
        _ = NSApplication.shared
        let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 200), styleMask: [.borderless], backing: .buffered, defer: false)
        window.isReleasedWhenClosed = false
        window.orderFrontRegardless()
        defer { window.orderOut(nil) }
        let renderer = LiteHTMLWidgetRenderer(theme: "dark", width: 300)
        renderer.view.frame = NSRect(x: 0, y: 0, width: 300, height: 200)
        window.contentView?.addSubview(renderer.view)
        renderer.send(##"{"type":"theme","theme":{"colorScheme":"dark","params":{"bg_color":"#102030"},"button":{"bg":"#405060"}}}"##)
        XCTAssertNil(renderer.document)
        renderer.load()
        let ops: [[String: Any]] = [
            ["op": "el", "id": 1, "tag": "html", "attrs": [], "adopt": "html"],
            ["op": "el", "id": 3, "tag": "body", "attrs": [], "adopt": "body"],
            ["op": "el", "id": 4, "tag": "div", "attrs": [["id", "probe"]]],
            ["op": "style", "id": 4, "name": "height", "value": "10px"],
            ["op": "style", "id": 4, "name": "background", "value": "var(--tg-theme-bg-color)"],
            ["op": "ins", "id": 4, "parent": 3],
            ["op": "el", "id": 5, "tag": "div", "attrs": [["id", "text"]]],
            ["op": "style", "id": 5, "name": "height", "value": "10px"],
            ["op": "style", "id": 5, "name": "background", "value": "var(--tg-theme-text-color)"],
            ["op": "ins", "id": 5, "parent": 3],
            ["op": "el", "id": 6, "tag": "button", "attrs": []],
            ["op": "ins", "id": 6, "parent": 3],
            ["op": "style", "id": 1, "name": "--tg-theme-bg-color", "value": "#000000", "important": true],
        ]
        let data = try! JSONSerialization.data(withJSONObject: ["type": "ops", "ops": ops])
        renderer.send(String(data: data, encoding: .utf8)!)
        pump(5, until: { renderer.layoutCount > 0 && renderer.document != nil && !renderer.isPreparingTiles })
        func background(_ id: Int) -> String? {
            return renderer.document?.computedStyle(attribute: WidgetDOM.idAttribute, value: String(id))["background-color"]
        }
        XCTAssertEqual(background(4), "rgb(16, 32, 48)")
        XCTAssertEqual(background(5), "rgb(255, 255, 255)")
        XCTAssertEqual(background(6), "rgb(64, 80, 96)")
    }

    func testSetThemeUpdatesAndRemovesVariables() throws {
        let h = try Harness(self, html: WidgetThemeTests.html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        XCTAssertEqual(h.style("plain")["background-color"], "rgb(255, 0, 0)")
        h.host.setTheme(WidgetTheme(colorScheme: .dark, params: ["bg_color": "#0f0e0d"]))
        pump(3, until: { h.style("bg")["background-color"] == "rgb(15, 14, 13)" && h.style("plain")["background-color"] == "rgb(0, 0, 0)" })
        XCTAssertEqual(h.style("bg")["background-color"], "rgb(15, 14, 13)")
        XCTAssertEqual(h.style("text")["background-color"], "rgb(255, 255, 255)")
        XCTAssertEqual(h.style("secondary")["background-color"], "rgb(24, 24, 24)")
        XCTAssertEqual(h.style("plain")["background-color"], "rgb(0, 0, 0)")
        let root = h.rootStyle
        XCTAssertTrue(root.contains("--tg-color-scheme:dark !important;"), root)
        XCTAssertFalse(root.contains("--tg-button-"), root)
    }

    func testLinksTakeTheUserAgentLinkStyle() throws {
        let html = ##"<!doctype html><html><head><style>body{margin:0;color:#111}.own{color:#00aa00;text-decoration:none}</style></head><body><a id="frag" href="#t">fragment</a> <a id="ext" href="https://example.org/">external</a> <a id="own" class="own" href="#x">own</a> <a id="none">none</a><i id="done"></i></body></html>"##
        let h = try Harness(self, html: html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        XCTAssertEqual(h.style("frag")["color"], "rgb(0, 170, 0)")
        XCTAssertEqual(h.style("frag")["text-decoration-line"], "none")
        XCTAssertEqual(h.element("ext")?.attribute("href"), "#")
        XCTAssertEqual(h.style("ext")["color"], "rgb(0, 170, 0)")
        XCTAssertEqual(h.style("ext")["text-decoration-line"], "none")
        XCTAssertEqual(h.style("own")["color"], "rgb(0, 170, 0)")
        XCTAssertEqual(h.style("own")["text-decoration-line"], "none")
        XCTAssertEqual(h.style("none")["color"], "rgb(17, 17, 17)")
        XCTAssertEqual(h.style("none")["text-decoration-line"], "none")
    }

    func testCustomPropertyQueryFollowsTheThemeInTheSameTask() throws {
        let h = try Harness(self, html: WidgetThemeTests.html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        let root = h.renderer.dom.publicId(h.renderer.dom.html)
        var answers: [String] = []
        let previous = h.renderer.onMessage
        h.renderer.onMessage = { json in
            if json.contains("\"qid\":\"probe\"") { answers.append(json) }
            previous?(json)
        }
        h.renderer.send(##"{"type":"query","qid":"probe","query":{"q":"cs","id":\##(root),"prop":"--tg-theme-bg-color"}}"##)
        h.renderer.send(##"{"type":"theme","theme":{"colorScheme":"dark","params":{"bg_color":"#212121"}}}"##)
        h.renderer.send(##"{"type":"query","qid":"probe","query":{"q":"cs","id":\##(root),"prop":"--tg-theme-bg-color"}}"##)
        pump(1, until: { answers.count == 2 })
        XCTAssertEqual(answers.count, 2)
        XCTAssertTrue(answers.first?.contains("#123456") == true, answers.first ?? "")
        XCTAssertTrue(answers.last?.contains("#212121") == true, answers.last ?? "")
    }

    func testSetThemeInTheSameSchemeUpdates() throws {
        let h = try Harness(self, html: WidgetThemeTests.html, theme: WidgetThemeTests.theme)
        defer { h.close() }
        XCTAssertEqual(h.style("bg")["background-color"], "rgb(18, 52, 86)")
        h.host.setTheme(WidgetTheme(colorScheme: .light, params: ["bg_color": "#0f0e0d", "text_color": "#ABCDEF"], button: WidgetButtonTokens(bg: "#405060", padding: "3px 5px")))
        pump(3, until: { h.style("bg")["background-color"] == "rgb(15, 14, 13)" && h.style("plain")["background-color"] == "rgb(64, 80, 96)" })
        XCTAssertEqual(h.style("bg")["background-color"], "rgb(15, 14, 13)")
        XCTAssertEqual(h.style("text")["background-color"], "rgb(171, 205, 239)")
        XCTAssertEqual(h.style("link")["background-color"], "rgb(51, 144, 236)")
        XCTAssertEqual(h.style("plain")["background-color"], "rgb(64, 80, 96)")
        XCTAssertEqual(h.style("plain")["color"], "rgb(171, 205, 239)")
        XCTAssertEqual(h.style("plain")["padding-left"], "5px")
        XCTAssertEqual(h.renderer.styleFastMismatches, 0)
    }
}
