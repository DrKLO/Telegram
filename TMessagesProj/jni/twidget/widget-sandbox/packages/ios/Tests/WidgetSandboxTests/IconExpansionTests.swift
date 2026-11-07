import XCTest
@testable import WidgetSandbox
import WidgetSandboxProtocol

final class IconExpansionTests: XCTestCase {
    /// A raster icon: SVG images are outside widget profile 1 (docs/profile-v1.md), so a resolver answers with PNG, JPEG, GIF or WebP.
    static let airplane = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    /// A real PNG of about 20 KB: past the strict attribute cap (8 K) on its own, past a text value's (32 K) twice.
    static let big = pngIcon(bytes: 20_000)

    static let stubResolver: WidgetAssetResolver = { icon in
        icon.hasPrefix("icon:airplane") ? airplane : icon.hasPrefix("icon:big") ? big : nil
    }

    static let profileImages: () -> Set<String> = { ["image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp"] }

    /// The stand-in's 2 × 2 PNG with a `tEXt` chunk of padding before `IEND`, as a data: URL of at least `bytes` characters.
    static func pngIcon(bytes: Int) -> String {
        var png = [UInt8](Data(base64Encoded: String(IconExpansion.standInImage.dropFirst("data:image/png;base64,".count)))!)
        func be32(_ n: Int) -> [UInt8] { [UInt8(n >> 24 & 0xff), UInt8(n >> 16 & 0xff), UInt8(n >> 8 & 0xff), UInt8(n & 0xff)] }
        func crc32(_ bytes: ArraySlice<UInt8>) -> Int {
            var c: UInt32 = 0xffff_ffff
            for b in bytes {
                c ^= UInt32(b)
                for _ in 0..<8 { c = c & 1 != 0 ? 0xedb8_8320 ^ (c >> 1) : c >> 1 }
            }
            return Int(~c)
        }
        let text = Array("Comment\u{0}".utf8) + [UInt8](repeating: 0x61, count: bytes * 3 / 4)
        var chunk = be32(text.count) + Array("tEXt".utf8) + text
        chunk += be32(crc32(chunk[4...]))
        png.insert(contentsOf: chunk, at: png.count - 12)
        return "data:image/png;base64," + Data(png).base64EncodedString()
    }

    private final class Box {
        var diags: [String] = []
        var asked: [String] = []
    }

    private static let skeleton = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null}]"

    private func validator(native: Bool, box: Box, policy: SandboxPolicy = .compat, resolver: WidgetAssetResolver? = IconExpansionTests.stubResolver) throws -> TrustedValidator {
        let v = try TrustedValidator(theme: "light") { _, _ in }
        v.onDiag = { k, d in box.diags.append(k + "|" + d) }
        v.setPolicy(policy)
        if native { v.useNativeModel(true) }
        v.setAssetResolver(resolver.map { r in { icon in box.asked.append(icon); return r(icon) } })
        _ = v.processOps(Self.skeleton)
        return v
    }

    private static func quoted(_ s: String) -> String {
        String(decoding: try! JSONSerialization.data(withJSONObject: [s], options: [.fragmentsAllowed, .withoutEscapingSlashes]), as: UTF8.self).dropFirst().dropLast().description
    }

    private func image(_ id: Int, _ src: String) -> String {
        "{\"op\":\"el\",\"id\":\(id),\"tag\":\"img\",\"attrs\":{\"src\":\(Self.quoted(src))}},{\"op\":\"ins\",\"parent\":3,\"id\":\(id),\"before\":null}"
    }

    private static func count(_ needle: String, in text: String) -> Int {
        text.components(separatedBy: needle).count - 1
    }

    func testIconsReachTheRendererAsTheHostsURL() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box)
            let out = v.processOps("[\(image(10, "icon:airplane")),{\"op\":\"style\",\"id\":10,\"name\":\"background-image\",\"value\":\"url(icon:airplane)\",\"important\":false}]")
            XCTAssertTrue(out.contains(Self.airplane), "native \(native) \(out.prefix(300))")
            XCTAssertFalse(out.contains("icon:airplane"), "native \(native)")
            XCTAssertFalse(out.contains(IconExpansion.standInImage), "native \(native): no stand-in reaches the renderer")
        }
    }

    func testWithoutAResolverIconsStayBlocked() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box, resolver: nil)
            let out = v.processOps("[\(image(10, "icon:airplane"))]")
            XCTAssertFalse(out.contains(Self.airplane), "native \(native)")
            XCTAssertFalse(out.contains("icon:airplane"), "native \(native) \(out.prefix(300))")
        }
    }

    func testResolverOutputThatIsNotAnInlineImageIsIgnored() throws {
        let hostile = [
            "https://evil.example/x.png", "//evil.example/x.png", "javascript:alert(1)", "data:text/html,%3Cscript%3E", "DATA:image/png;base64,AAAA",
            "data:image/svg+xml,\"onload=alert(1)", "data:image/svg+xml,a)b", "data:image/svg+xml,a b", "data:image/svg+xml,\\u0022", "data:image/svg+xml,<svg/onload=1>",
            "data:image/png;base64," + String(repeating: "A", count: IconExpansion.maxURLBytes), "data:image/", "blob:null/1", "",
        ]
        for url in hostile {
            XCTAssertNil(IconExpansion.checked(url), url)
            for native in [false, true] {
                let box = Box()
                let v = try validator(native: native, box: box, resolver: { _ in url })
                let out = v.processOps("[\(image(10, "icon:airplane")),{\"op\":\"style\",\"id\":10,\"name\":\"background\",\"value\":\"url(icon:airplane)\",\"important\":false}]")
                XCTAssertFalse(out.contains("evil.example") || out.contains("javascript:") || out.contains("text/html") || out.contains("onload") || out.contains("blob:"), "native \(native) \(url): \(out.prefix(300))")
            }
        }
        XCTAssertEqual(IconExpansion.checked(Self.airplane), Self.airplane)
    }

    /// The validator no longer sees the host's URL, so the profile's image types are checked on the answer, as the
    /// validator refused any other type before.
    func testAnAnswerOutsideTheProfilesImageTypesIsIgnored() throws {
        let svg = "data:image/svg+xml,%3Csvg%20xmlns=%22http://www.w3.org/2000/svg%22%20width=%2210%22%20height=%2210%22/%3E"
        let avif = "data:image/avif;base64,AAAAHGZ0eXBhdmlm"
        for url in [svg, avif] {
            XCTAssertEqual(IconExpansion.checked(url), url, "passes the character check")
            for native in [false, true] {
                let box = Box()
                let v = try validator(native: native, box: box, resolver: { _ in url })
                let out = v.processOps("[\(image(10, "icon:x")),{\"op\":\"style\",\"id\":10,\"name\":\"background-image\",\"value\":\"url(icon:x)\",\"important\":false}]")
                XCTAssertFalse(out.contains(url), "native \(native) \(url): \(out.prefix(300))")
                XCTAssertFalse(out.contains(IconExpansion.standInImage), "native \(native): an unresolved token stays a blocked URL")
            }
        }
    }

    func testResolverOnlySeesTokensFromTheGrammar() throws {
        let box = Box()
        let v = try validator(native: false, box: box)
        let long = "icon:" + String(repeating: "a", count: 300)
        let tricky = ["icon:../../etc/passwd", "icon:a\"b", "icon:a<b>", "icon:a b", "icon:a?x=1&y=<", "icon:%2e%2e", "icon:a\\u0000b", long, "icon:a?" + String(repeating: "q", count: 600), "icon:airplane?weight=bold"]
        _ = v.processOps("[{\"op\":\"el\",\"id\":30,\"tag\":\"div\",\"attrs\":{}},{\"op\":\"style\",\"id\":30,\"name\":\"background\",\"value\":\"url(\(long)) url(icon:a?\(String(repeating: "q", count: 600)))\",\"important\":false}]")
        _ = v.processOps("[" + tricky.enumerated().map { image(10 + $0.offset, $0.element) }.joined(separator: ",") + "]")
        let grammar = try NSRegularExpression(pattern: "\\Aicon:[A-Za-z0-9_.-]{0,96}(?:\\?[A-Za-z0-9=&.%#_-]{0,400})?\\z", options: [.caseInsensitive])
        let asked = v.thread.sync { box.asked }
        XCTAssertFalse(asked.isEmpty)
        for icon in asked {
            XCTAssertNotNil(grammar.firstMatch(in: icon, range: NSRange(location: 0, length: (icon as NSString).length)), icon)
            XCTAssertLessThanOrEqual(icon.utf8.count, 5 + 96 + 1 + 400)
        }
    }

    func testExpansionIsCappedPerBatch() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box)
            let value = (0..<3000).map { _ in "url(icon:airplane)" }.joined(separator: ",")
            let batch = "[{\"op\":\"el\",\"id\":10,\"tag\":\"div\",\"attrs\":{}},{\"op\":\"ins\",\"parent\":3,\"id\":10,\"before\":null},{\"op\":\"style\",\"id\":10,\"name\":\"background-image\",\"value\":\"\(value)\",\"important\":false}]"
            _ = v.processOps(batch)
            let asked = v.thread.sync { box.asked.count }
            XCTAssertLessThanOrEqual(asked, IconExpansion.maxIconsPerBatch, "native \(native)")
            XCTAssertEqual(v.thread.sync { box.diags.filter { $0.hasPrefix("icon-limit|") }.count }, 1, "native \(native)")
            box.diags.removeAll()
            let out = v.processOps("[\(image(11, "icon:airplane"))]")
            XCTAssertTrue(out.contains(Self.airplane), "the budget is per batch")
        }
        let big = "data:image/png;base64," + String(repeating: "A", count: IconExpansion.maxURLBytes - 30)
        var diags: [String] = []
        let icons = IconExpansion(resolver: { _ in big }, imageTypes: Self.profileImages) { k, _ in diags.append(k) }
        icons.beginBatch()
        let text = "[\"" + (0..<100).map { "url(icon:n\($0))" }.joined(separator: " ") + "\"]"
        let out = icons.restore(icons.expand(text))
        XCTAssertLessThanOrEqual(out.utf8.count - text.utf8.count, IconExpansion.maxBytesPerBatch)
        XCTAssertEqual(Self.count(big, in: out), IconExpansion.maxBytesPerBatch / big.utf8.count)
        XCTAssertEqual(diags, ["icon-limit"])
    }

    func testTheIconPrefixIsExactlyLowercase() throws {
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box)
            let out = v.processOps("[\(image(10, "ICON:airplane")),\(image(11, "Icon:airplane")),{\"op\":\"el\",\"id\":12,\"tag\":\"div\",\"attrs\":{}},{\"op\":\"ins\",\"parent\":3,\"id\":12,\"before\":null},{\"op\":\"style\",\"id\":12,\"name\":\"background-image\",\"value\":\"url(ICON:airplane), url('Icon:airplane')\",\"important\":false}]")
            XCTAssertFalse(out.contains(Self.airplane), "native \(native) \(out.prefix(300))")
            XCTAssertEqual(v.thread.sync { box.asked }, [], "native \(native)")
            let css = v.processOps("[{\"op\":\"style\",\"id\":12,\"name\":\"background-image\",\"value\":\"URL(icon:airplane)\",\"important\":false}]")
            XCTAssertTrue(css.contains(Self.airplane), "the CSS function name stays case-insensitive; native \(native) \(css.prefix(300))")
        }
    }

    func testDistinctIconsAreLimitedPerWidget() {
        var asked: [String] = []
        var diags: [String] = []
        let icons = IconExpansion(resolver: { icon in asked.append(icon); return Self.airplane }, imageTypes: Self.profileImages) { k, _ in diags.append(k) }
        for batch in 0..<3 {
            icons.beginBatch()
            let text = "[\"" + (0..<200).map { "url(icon:n\(batch * 200 + $0))" }.joined(separator: " ") + "\"]"
            _ = icons.expand(text)
        }
        XCTAssertEqual(asked.count, IconExpansion.maxIconsPerWidget)
        XCTAssertEqual(diags, ["icon-limit"], "reported once per widget")
        icons.beginBatch()
        XCTAssertTrue(icons.restore(icons.expand("[\"url(icon:n0) url(icon:n599)\"]")).contains("url(" + Self.airplane + ") url(icon:n599)"), "known tokens keep working, new ones stay unresolved")
        XCTAssertEqual(asked.count, IconExpansion.maxIconsPerWidget)

        let big = "data:image/png;base64," + String(repeating: "A", count: 60_000)
        var bigAsked = 0
        var bigDiags: [String] = []
        let heavy = IconExpansion(resolver: { _ in bigAsked += 1; return big }, imageTypes: Self.profileImages) { k, _ in bigDiags.append(k) }
        var expanded = 0
        for batch in 0..<10 {
            heavy.beginBatch()
            let out = heavy.restore(heavy.expand("[\"" + (0..<10).map { "url(icon:b\(batch * 10 + $0))" }.joined(separator: " ") + "\"]"))
            expanded += Self.count(big, in: out)
        }
        XCTAssertEqual(expanded, IconExpansion.maxResolvedBytesPerWidget / big.utf8.count)
        XCTAssertLessThanOrEqual(bigAsked, expanded + 1)
        XCTAssertEqual(bigDiags, ["icon-limit"])
    }

    // MARK: the validator sees a stand-in, the renderer the host's URL

    func testTheStandInIsWhatTheValidatorSees() {
        let icons = IconExpansion(resolver: Self.stubResolver, imageTypes: Self.profileImages, nonce: 0xfeed_beef) { _, _ in }
        icons.beginBatch()
        let expanded = icons.expand("[{\"op\":\"attr\",\"id\":5,\"name\":\"src\",\"value\":\"icon:big\"},{\"op\":\"style\",\"id\":5,\"name\":\"background-image\",\"value\":\"url(\\\"icon:airplane\\\")\",\"important\":false}]")
        XCTAssertFalse(expanded.contains(Self.big) || expanded.contains(Self.airplane), expanded)
        XCTAssertEqual(icons.standInPrefix, IconExpansion.standInImage + "#tw00000000feedbeef")
        XCTAssertTrue(expanded.contains("\"value\":\"" + icons.standInPrefix + "000\""), expanded)
        XCTAssertTrue(expanded.contains("url(\\\"" + icons.standInPrefix + "001\\\")"), expanded)
        XCTAssertEqual(icons.hostURL(icons.standInPrefix + "000"), Self.big)
        XCTAssertEqual(icons.hostURL(icons.standInPrefix + "001"), Self.airplane)
        XCTAssertLessThan(icons.standInPrefix.utf8.count + IconExpansion.indexDigits, 160, "a token costs the caps about this much, never the host's bytes")
        // Only the stand-ins of this widget, by an index it has; a stand-in followed by a digit still reads back.
        let restored = icons.restore("[\"\(icons.standInPrefix)0007\",\"\(IconExpansion.standInImage)#tw00000000feedbeee000\",\"\(icons.standInPrefix)002\",\"\(icons.standInPrefix)0a1\",\"\(icons.standInPrefix)00\"]")
        XCTAssertEqual(restored, "[\"\(Self.big)7\",\"\(IconExpansion.standInImage)#tw00000000feedbeee000\",\"\(icons.standInPrefix)002\",\"\(icons.standInPrefix)0a1\",\"\(icons.standInPrefix)00\"]")
    }

    /// What the validator sends again (a theme change sends every stylesheet) is restored at most `maxBytesPerBatch` per
    /// batch; past it the stand-ins stay the 2 × 2 image.
    func testRestoringIsCappedPerBatch() {
        let big = "data:image/png;base64," + String(repeating: "A", count: 60_000)
        var diags: [String] = []
        let icons = IconExpansion(resolver: { _ in big }, imageTypes: Self.profileImages) { k, _ in diags.append(k) }
        icons.beginBatch()
        guard let standIn = icons.standIn("icon:b") else { return XCTFail("no stand-in") }
        let text = "[" + Array(repeating: "\"url(\(standIn))\"", count: 40).joined(separator: ",") + "]"
        let out = icons.restore(text)
        XCTAssertEqual(Self.count(big, in: out), IconExpansion.maxBytesPerBatch / big.utf8.count)
        XCTAssertEqual(Self.count(standIn, in: out), 40 - IconExpansion.maxBytesPerBatch / big.utf8.count)
        XCTAssertEqual(diags, ["icon-limit"])
        let ops = icons.restore((0..<40).map { WidgetOp(style: $0, name: "background-image", value: "url(\(standIn))", important: false) })
        XCTAssertEqual(ops.filter { $0.value.contains(big) }.count, IconExpansion.maxBytesPerBatch / big.utf8.count)
        XCTAssertEqual(diags, ["icon-limit", "icon-limit"])
    }

    /// The batches of a widget that shows a 20 KB host icon in each place an icon can be: under strict none of them is
    /// past a cap (the widget sent a few bytes), while the same URL written by the widget itself is. `copies`: the URLs in
    /// the validated batch (a stylesheet's text node goes out as text before its `<style>` takes it, then as the sheet).
    private func iconBatches(_ src: String) -> [(place: String, json: String, copies: Int)] {
        let q = Self.quoted
        return [
            ("img src", "[\(image(10, src))]", 1),
            ("inline style", "[{\"op\":\"el\",\"id\":11,\"tag\":\"div\",\"attrs\":{\"style\":\(q("background-image:url(\(src));color:red"))}},{\"op\":\"ins\",\"parent\":3,\"id\":11,\"before\":null}]", 1),
            ("style op", "[{\"op\":\"el\",\"id\":12,\"tag\":\"div\",\"attrs\":{}},{\"op\":\"ins\",\"parent\":3,\"id\":12,\"before\":null},{\"op\":\"style\",\"id\":12,\"name\":\"background-image\",\"value\":\(q("url(\(src))")),\"important\":false}]", 1),
            ("stylesheet", "[{\"op\":\"el\",\"id\":13,\"tag\":\"style\",\"attrs\":{}},{\"op\":\"text\",\"id\":14,\"data\":\(q(".a{background-image:url(\(src))}.b{background-image:url('\(src)')}@media (prefers-color-scheme: dark){.a{color:red}}"))},{\"op\":\"ins\",\"parent\":13,\"id\":14,\"before\":null},{\"op\":\"ins\",\"parent\":3,\"id\":13,\"before\":null}]", 4),
        ]
    }

    func testAHostIconIsNotChargedToTheWidget() throws {
        XCTAssertGreaterThanOrEqual(Self.big.utf8.count, 20_000)
        for policy in [SandboxPolicy.strict, .compat] {
            for native in [false, true] {
                let tag = "\(policy.rawValue) native \(native)"
                let box = Box()
                let v = try validator(native: native, box: box, policy: policy)
                for batch in iconBatches("icon:big") {
                    let out = v.processOps(batch.json)
                    XCTAssertNil(v.stopped, "\(tag) \(batch.place): \(box.diags.suffix(3))")
                    XCTAssertEqual(Self.count(Self.big, in: out), batch.copies, "\(tag) \(batch.place): \(out.replacingOccurrences(of: Self.big, with: "BIG").prefix(1500))")
                    XCTAssertFalse(out.contains(IconExpansion.standInImage) || out.contains("icon:big"), "\(tag) \(batch.place)")
                }
                let themed = v.setTheme("dark")
                XCTAssertGreaterThanOrEqual(Self.count(Self.big, in: themed), 2, "\(tag): the stylesheet sent again on a theme change: \(themed.prefix(300))")
                XCTAssertFalse(themed.contains(IconExpansion.standInImage), tag)
                XCTAssertFalse(box.diags.contains { $0.hasPrefix("limit|") || $0.hasPrefix("icon-limit|") }, "\(tag): \(box.diags)")

                // The caps are in force: the same URL written by the widget stops it under strict, each place on its own.
                for batch in iconBatches(Self.big) {
                    let own = try validator(native: native, box: Box(), policy: policy, resolver: nil)
                    let out = own.processOps(batch.json)
                    if policy == .strict {
                        XCTAssertNotNil(own.stopped, "\(tag) \(batch.place): the widget's own \(Self.big.utf8.count) characters")
                    } else {
                        XCTAssertNil(own.stopped, "\(tag) \(batch.place)")
                        XCTAssertEqual(Self.count(Self.big, in: out), batch.copies, "\(tag) \(batch.place)")
                    }
                }
            }
        }
    }

    func testTheWidgetsOwnOverCapAttributeStillStops() throws {
        let style = "background-image:url(icon:airplane);" + String(repeating: "color:red;", count: 820)
        XCTAssertGreaterThan(style.utf16.count, 8192)
        let batch = "[{\"op\":\"el\",\"id\":11,\"tag\":\"div\",\"attrs\":{\"style\":\(Self.quoted(style))}},{\"op\":\"ins\",\"parent\":3,\"id\":11,\"before\":null}]"
        for policy in [SandboxPolicy.strict, .compat] {
            for native in [false, true] {
                let box = Box()
                let v = try validator(native: native, box: box, policy: policy)
                let out = v.processOps(batch)
                if policy == .strict {
                    XCTAssertNotNil(v.stopped, "native \(native)")
                    XCTAssertEqual(out, "[]", "native \(native)")
                    XCTAssertTrue(box.diags.contains { $0.hasPrefix("limit|Attribute limit exceeded") }, "native \(native): \(box.diags)")
                } else {
                    XCTAssertNil(v.stopped, "native \(native)")
                    XCTAssertTrue(out.contains(Self.airplane), "native \(native): \(out.prefix(300))")
                }
            }
        }
    }

    func testAStandInTheWidgetWritesStaysTheStandIn() throws {
        let forged = IconExpansion.standInImage + "#tw0000000000000000000"
        for native in [false, true] {
            let box = Box()
            let v = try validator(native: native, box: box, policy: .strict)
            _ = v.processOps("[\(image(10, "icon:airplane"))]")
            let out = v.processOps("[\(image(11, forged)),{\"op\":\"style\",\"id\":11,\"name\":\"background-image\",\"value\":\(Self.quoted("url(\(forged))")),\"important\":false}]")
            XCTAssertEqual(Self.count(forged, in: out), 2, "native \(native): \(out.prefix(400))")
            XCTAssertFalse(out.contains(Self.airplane), "native \(native)")
        }
    }

    func testBinaryBatchesExpandIconsThroughTheNativeModel() throws {
        let box = Box()
        let v = try validator(native: true, box: box)
        var encoder = BinaryOpsEncoder()
        encoder.begin()
        encoder.element(10, tag: "img", svg: false, attributes: [("src", "icon:airplane")])
        encoder.insert(parent: 3, 10, before: 0)
        encoder.style(10, "background-image", "url(icon:airplane)", important: false)
        let batch = v.processBinary(encoder.finish(), typed: false)
        guard case .json(let out) = batch else { return XCTFail("\(batch)") }
        XCTAssertTrue(out.contains(Self.airplane), out)
        XCTAssertFalse(out.contains(IconExpansion.standInImage), out)
    }

    func testBinaryBatchesDoNotChargeAHostIconToTheWidget() throws {
        for policy in [SandboxPolicy.strict, .compat] {
            for typed in [false, true] {
                let tag = "\(policy.rawValue) typed \(typed)"
                let box = Box()
                let v = try validator(native: true, box: box, policy: policy)
                var encoder = BinaryOpsEncoder()
                encoder.begin()
                encoder.element(10, tag: "img", svg: false, attributes: [("src", "icon:big")])
                encoder.insert(parent: 3, 10, before: 0)
                encoder.element(11, tag: "div", svg: false, attributes: [("style", "background-image:url(icon:big)")])
                encoder.insert(parent: 3, 11, before: 0)
                encoder.element(12, tag: "div", svg: false, attributes: [])
                encoder.insert(parent: 3, 12, before: 0)
                encoder.style(12, "background-image", "url(icon:big)", important: false)
                encoder.element(13, tag: "style", svg: false, attributes: [])
                encoder.text(14, ".a{background-image:url(icon:big)}.b{background-image:url(\"icon:big\")}")
                encoder.insert(parent: 13, 14, before: 0)
                encoder.insert(parent: 3, 13, before: 0)
                let batch = v.processBinary(encoder.finish(), typed: typed)
                XCTAssertNil(v.stopped, "\(tag): \(box.diags.suffix(3))")
                let text: String
                switch batch {
                case .json(let json):
                    XCTAssertFalse(typed, tag)
                    text = json
                case .ops(let ops):
                    XCTAssertTrue(typed, tag)
                    XCTAssertEqual(ops.first { $0.kind == .element && $0.tag == "img" }?.attributes.first { $0.name == "src" }?.value, Self.big, tag)
                    XCTAssertEqual(ops.first { $0.kind == .style && $0.id == 12 }?.value.contains(Self.big), true, tag)
                    text = WidgetOp.json(ops)
                case .empty:
                    return XCTFail(tag)
                }
                // img, inline style, style property, and the stylesheet's two URLs twice (as text, then as the sheet)
                XCTAssertEqual(Self.count(Self.big, in: text), 7, "\(tag): \(text.replacingOccurrences(of: Self.big, with: "BIG").prefix(1500))")
                XCTAssertFalse(text.contains(IconExpansion.standInImage) || text.contains("icon:big"), tag)
                XCTAssertFalse(box.diags.contains { $0.hasPrefix("limit|") }, "\(tag): \(box.diags)")
            }
        }
    }

    func testTypedOpsOfTheBundleGetTheHostsURL() throws {
        let box = Box()
        let v = try validator(native: false, box: box, policy: .strict)
        let batch = v.processOpsForRenderer("[\(image(10, "icon:big"))]", typed: true)
        guard case .ops(let ops) = batch else { return XCTFail("\(batch)") }
        XCTAssertEqual(ops.first { $0.kind == .element }?.attributes.first { $0.name == "src" }?.value, Self.big)
        XCTAssertNil(v.stopped)
    }

    private func render(_ html: String, options: WidgetHost.Options) -> (RecordingRenderer, WidgetHost) {
        let renderer = RecordingRenderer()
        let host = try! WidgetHost(html: html, theme: WidgetTheme(), width: 300, options: options, rendererFactory: { _, _, _ in renderer })
        let deadline = Date().addingTimeInterval(10)
        while !(renderer.messages.joined().contains("\"img\"") || host.diagnostics.dead != nil) && Date() < deadline {
            RunLoop.main.run(until: Date().addingTimeInterval(0.01))
        }
        RunLoop.main.run(until: Date().addingTimeInterval(0.1))
        return (renderer, host)
    }

    func testTheHostResolverReachesARunningWidget() {
        let html = "<img id=a src=\"icon:airplane\"><div id=b></div><script>document.getElementById('b').style.backgroundImage = 'url(icon:airplane?weight=bold)'; const i = document.createElement('img'); i.src = 'icon:airplane'; document.body.appendChild(i);</script>"
        for binary in [false, true] {
            var asked: [String] = []
            let lock = NSLock()
            var options = WidgetHost.Options()
            if binary { options.features = ["binaryOps"] }
            options.assetResolver = { icon in lock.withLock { asked.append(icon) }; return Self.stubResolver(icon) }
            let (renderer, host) = render(html, options: options)
            let sent = renderer.messages.joined()
            XCTAssertTrue(sent.contains(Self.airplane), "binary \(binary): \(sent.prefix(400))")
            XCTAssertFalse(sent.contains("[\"src\",\"icon:") || sent.contains("\"value\":\"url(icon:"), "binary \(binary)")
            XCTAssertFalse(sent.contains(IconExpansion.standInImage), "binary \(binary)")
            XCTAssertTrue(sent.contains("\"name\":\"background-image\",\"value\":\"url(\"" + Self.airplane) || sent.contains("url(" + Self.airplane), "binary \(binary)")
            XCTAssertGreaterThanOrEqual(lock.withLock { asked.count }, 2, "binary \(binary)")
            host.destroy()

            var plain = WidgetHost.Options()
            if binary { plain.features = ["binaryOps"] }
            let (without, other) = render(html, options: plain)
            XCTAssertFalse(without.messages.joined().contains(Self.airplane), "binary \(binary)")
            other.destroy()
        }
    }

    /// A running widget under strict (the host's default) with a 20 KB host icon in an `<img src>`, an inline style, a
    /// style property and a stylesheet: it is not stopped, and the renderer gets the host's URL in each.
    func testARunningWidgetIsNotStoppedForTheHostsIconBytes() {
        let html = "<style>.s{background-image:url(icon:big)}.t{background-image:url('icon:big')}</style><div class=s style=\"background-image:url(icon:big)\"></div><div id=b></div><script>document.getElementById('b').style.backgroundImage = 'url(icon:big)'; const i = document.createElement('img'); i.src = 'icon:big'; document.body.appendChild(i);</script>"
        for binary in [false, true] {
            var options = WidgetHost.Options()
            XCTAssertEqual(options.policy, .strict)
            if binary { options.features = ["binaryOps"] }
            options.assetResolver = Self.stubResolver
            let (renderer, host) = render(html, options: options)
            let sent = renderer.messages.joined()
            XCTAssertNil(host.diagnostics.dead, "binary \(binary)")
            XCTAssertGreaterThanOrEqual(Self.count(Self.big, in: sent), 5, "binary \(binary): \(sent.prefix(400))")
            XCTAssertFalse(sent.contains(IconExpansion.standInImage) || sent.contains("icon:big"), "binary \(binary)")
            host.destroy()
        }
    }
}
