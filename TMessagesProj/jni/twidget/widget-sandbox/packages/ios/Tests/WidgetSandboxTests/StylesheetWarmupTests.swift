import XCTest
@testable import WidgetSandbox

final class StylesheetWarmupTests: XCTestCase {
    static let sheets = [
        "body { color: red; background: url(https://evil.example/x.png); } .a { behavior: url(x.htc) }",
        "@import url('https://evil.example/a.css'); @media (prefers-color-scheme: dark) { .b { color: white } } .c { width: calc(100% - 4px) }",
        ".d { background-image: image-set('https://x' 1x); -moz-binding: url(x) } .e::before { content: 'hi' } @font-face { src: url(x.woff) }",
        "this is { not css",
        ":root { --x: red } .f { color: var(--x); transition: opacity 0.2s ease }",
    ]

    final class Recorder {
        var diags: [String] = []
    }

    private func batch(_ css: String, id: Int) -> String {
        let text = try! JSONSerialization.data(withJSONObject: [css], options: [])
        let quoted = String(decoding: text, as: UTF8.self).dropFirst().dropLast()
        return """
            [{"op":"el","id":2,"tag":"html","attrs":{}},{"op":"el","id":3,"tag":"body","attrs":{}},{"op":"ins","parent":1,"id":2,"before":null},{"op":"ins","parent":2,"id":3,"before":null},\
            {"op":"el","id":\(id),"tag":"style","attrs":{}},{"op":"text","id":\(id + 1),"data":\(quoted)},{"op":"ins","parent":\(id),"id":\(id + 1),"before":null},{"op":"ins","parent":3,"id":\(id),"before":null}]
            """
    }

    func testWarmedStylesheetsValidateExactlyLikeColdOnes() throws {
        for theme in ["light", "dark"] {
            let warmDiags = Recorder(), coldDiags = Recorder()
            let warm = try TrustedValidator(theme: theme) { k, d in warmDiags.diags.append(k + "|" + d) }
            let cold = try TrustedValidator(theme: theme) { k, d in coldDiags.diags.append(k + "|" + d) }
            for (index, css) in Self.sheets.enumerated() {
                warm.warmStylesheets(Self.sheets + ["unused { color: blue }"])
                let json = batch(css, id: 10 + index * 2)
                let a = warm.processOps(json), b = cold.processOps(json)
                XCTAssertEqual(a, b, css)
                XCTAssertEqual(warm.thread.sync { warmDiags.diags }, cold.thread.sync { coldDiags.diags }, css)
                if index < Self.sheets.count - 1 {
                    warm.reset(theme: theme)
                    cold.reset(theme: theme)
                }
            }
            XCTAssertEqual(warm.setTheme(theme == "light" ? "dark" : "light"), cold.setTheme(theme == "light" ? "dark" : "light"))
            XCTAssertFalse(warm.thread.sync { warmDiags.diags }.isEmpty)
        }
    }

    func testStyleTextsAreFoundInWidgetSource() {
        XCTAssertEqual(WidgetHost.styleTexts(in: "<STYLE media=x>a{}</style><div></div><style>b{}</StyLe>"), ["a{}", "b{}"])
        XCTAssertEqual(WidgetHost.styleTexts(in: "<style>unterminated"), [])
        XCTAssertEqual(WidgetHost.styleTexts(in: String(repeating: "<style>x</style>", count: 10)).count, 4)
    }
}
