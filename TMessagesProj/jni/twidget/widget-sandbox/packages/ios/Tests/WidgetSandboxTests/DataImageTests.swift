import XCTest
@testable import WidgetSandbox

/// data: images on the native model (`URLPolicy.check`, the opt-in `binaryOps` path): their size read from their bytes
/// as a decoder would, past `maxImagePixels` refused, bytes of none of the profile's formats broken; the body read as
/// fetch decodes it. The cases of packages/core/tests/unit/url-policy-images.test.ts, each also checked against the
/// validator bundle's `checkUrl` (the script validator through `TrustedValidator`): one verdict for every URL.
final class DataImageTests: XCTestCase {
    typealias Rig = NativeValidatorDifferentialTests.Rig

    private static let repo = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()

    // MARK: images, as the TS test builds them

    static func base64(_ bytes: [UInt8]) -> String { Data(bytes).base64EncodedString() }
    static func latin1(_ text: String) -> [UInt8] { text.unicodeScalars.map { UInt8(truncatingIfNeeded: $0.value) } }
    static func u32be(_ n: Int) -> [UInt8] { [UInt8(n >> 24 & 255), UInt8(n >> 16 & 255), UInt8(n >> 8 & 255), UInt8(n & 255)] }
    static func u16le(_ n: Int) -> [UInt8] { [UInt8(n & 255), UInt8(n >> 8 & 255)] }
    /// The signature and IHDR of a PNG claiming `width` × `height`: all a decoder reads before it allocates.
    static func pngHeader(_ width: Int, _ height: Int) -> String {
        "data:image/png;base64," + base64([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52] + u32be(width) + u32be(height) + [8, 6, 0, 0, 0])
    }
    /// A GIF of a `screen` logical screen whose first frame is `frame` ([left, top, width, height]), after an extension.
    static func gif(_ screen: (Int, Int), _ frame: [Int]? = nil) -> String {
        var bytes = latin1("GIF89a") + u16le(screen.0) + u16le(screen.1) + [0, 0, 0]
        bytes += [0x21, 0xf9, 4, 0, 0, 0, 0, 0]
        if let frame { bytes += [0x2c] + frame.flatMap(u16le) + [0, 2, 2, 0x4c, 0x01, 0] }
        return "data:image/gif;base64," + base64(bytes + [0x3b])
    }
    /// A baseline JPEG's frame header of 16 × 16, percent-encoded.
    static let sof16 = "%FF%C0%00%11%08%00%10%00%10%03"
    /// A JPEG whose frame header (16 × 16) comes after APP1 segments of these lengths.
    static func jpegWithSegments(_ lengths: [Int]) -> [UInt8] {
        var bytes: [UInt8] = [0xff, 0xd8]
        for length in lengths {
            let marker: [UInt8] = [0xff, 0xe1, UInt8((length + 2) >> 8), UInt8((length + 2) & 255)]
            bytes += marker
            bytes += [UInt8](repeating: 0x41, count: length)
        }
        let frame: [UInt8] = [0xff, 0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03]
        return bytes + frame
    }
    /// A JPEG whose frame header (16 × 16) comes after `fill` fill bytes, a step each.
    static func jpegWithFill(_ fill: Int) -> [UInt8] {
        let start: [UInt8] = [0xff, 0xd8], frame: [UInt8] = [0xc0, 0x00, 0x11, 0x08, 0x00, 0x10, 0x00, 0x10, 0x03]
        return start + [UInt8](repeating: 0xff, count: fill) + frame
    }
    /// A 1 × 1 GIF whose frame comes after a comment of `count` sub-blocks of `size` bytes.
    static func gifWithBlocks(_ count: Int, _ size: Int) -> String {
        var bytes = latin1("GIF89a") + [1, 0, 1, 0, 0, 0, 0, 0x21, 0xfe]
        let block = [UInt8(size)] + [UInt8](repeating: 0x41, count: size)
        for _ in 0..<count { bytes += block }
        bytes += [0, 0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4c, 0x01, 0, 0x3b]
        return "data:image/gif;base64," + base64(bytes)
    }
    /// A WebP whose first chunk is `chunk` with these bytes from offset 16 on.
    static func webpChunk(_ chunk: String, _ bytes: [UInt8]) -> [UInt8] { latin1("RIFF\0\0\0\0WEBP" + chunk) + bytes }
    /// `text` with `insert` after every `n` characters (the TS `replace(/(.{n})/g, '$1 ')`).
    static func spaced(_ text: String, every n: Int, _ insert: String = " ") -> String {
        var out = "", count = 0
        for c in text {
            out.append(c)
            count += 1
            if count % n == 0 { out += insert }
        }
        return out
    }
    static func percentEncoded(_ bytes: [UInt8], pad: Bool = true) -> String {
        bytes.map { "%" + (pad && $0 < 16 ? "0" : "") + String($0, radix: 16) }.joined()
    }
    static func body(_ url: String) -> String { String(url[url.index(after: url.firstIndex(of: ",")!)...]) }

    private static func size(_ url: String) -> [Int]? { URLPolicy.dataImageSize(url).map { [$0.width, $0.height] } }
    /// The profile's image types, as the bundle gives them to the native model.
    private static let mime: Set<String> = NativeValidatorTables(json: Rig(native: true).validator.tablesJSON())?.mime ?? []
    private static func check(_ url: String) -> String? { URLPolicy.check(url, image: true, mime: mime) }
    private static func decoded(_ url: String) -> Int { URLPolicy.dataImageScan(url).decodedCharacters }

    /// How many times as long a scan of `url` takes as parsing the message that brings it (an `attr` op), which the host
    /// pays for every op anyway: the least of rounds that take turns, so that a busy machine slows both alike.
    private static func scanCost(_ url: String) -> Double {
        let message = try! JSONSerialization.data(withJSONObject: [["op": "attr", "id": 1, "name": "src", "value": url] as [String: Any]])
        var scan = Double.infinity, parse = Double.infinity, sink = 0
        for _ in 0..<9 {
            var start = CFAbsoluteTimeGetCurrent()
            for _ in 0..<20 { sink &+= URLPolicy.dataImageSize(url)?.width ?? 0 }
            scan = min(scan, CFAbsoluteTimeGetCurrent() - start)
            start = CFAbsoluteTimeGetCurrent()
            for _ in 0..<20 { sink &+= ((try? JSONSerialization.jsonObject(with: message)) as? [Any])?.count ?? 0 }
            parse = min(parse, CFAbsoluteTimeGetCurrent() - start)
        }
        XCTAssertGreaterThanOrEqual(sink, 0)
        print(String(format: "data: image scan of %@…: %.2f parses of its message", String(url.prefix(40)), scan / parse))
        return scan / parse
    }
    /// A loose bound for the native scan against a Foundation parse (the TS test holds the script to 4 parses).
    private static let costBound = 8.0

    // MARK: the same verdict as the validator bundle's checkUrl

    private var bundle: Rig!
    private var native: Rig!
    /// For a URL past strict's 8 KB attribute cap, which stops the widget before the URL is looked at
    /// (StrictLimitsTests): compat takes it, and the image check is the same under both policies.
    private var bundleCompat: Rig!
    private var nativeCompat: Rig!
    private var nextId = 10

    override func setUp() {
        super.setUp()
        bundle = Rig(native: false, policy: .strict)
        native = Rig(native: true, policy: .strict)
        bundleCompat = Rig(native: false, policy: .compat)
        nativeCompat = Rig(native: true, policy: .compat)
        let setup = "[{\"op\":\"el\",\"id\":2,\"tag\":\"html\",\"attrs\":{}},{\"op\":\"el\",\"id\":3,\"tag\":\"body\"},{\"op\":\"el\",\"id\":4,\"tag\":\"svg\",\"ns\":\"svg\"},{\"op\":\"ins\",\"parent\":1,\"id\":2,\"before\":null},{\"op\":\"ins\",\"parent\":2,\"id\":3,\"before\":null},{\"op\":\"ins\",\"parent\":3,\"id\":4,\"before\":null}]"
        for rig in [bundle, native, bundleCompat, nativeCompat] { _ = rig!.json(setup) }
    }

    override func tearDown() {
        bundle = nil
        native = nil
        bundleCompat = nil
        nativeCompat = nil
        super.tearDown()
    }

    /// `url` as an `<img src>`, set when the element is made and again by an `attr` op, and as an SVG `<image href>`,
    /// through the script validator and the native model (strict; compat for a URL past strict's attribute cap): the
    /// same ops and diagnostics out. Returns the `src` the native model gave the `<img>`.
    @discardableResult
    private func sameVerdict(_ url: String, _ label: String = "", file: StaticString = #filePath, line: UInt = #line) -> String? {
        let img = nextId, image = nextId + 1
        nextId += 2
        let ops: [[String: Any]] = [
            ["op": "el", "id": img, "tag": "img", "attrs": ["src": url]],
            ["op": "ins", "parent": 3, "id": img, "before": NSNull()],
            ["op": "attr", "id": img, "name": "src", "value": url],
            ["op": "el", "id": image, "tag": "image", "ns": "svg", "attrs": ["href": url]],
            ["op": "ins", "parent": 4, "id": image, "before": NSNull()],
        ]
        let batch = String(decoding: try! JSONSerialization.data(withJSONObject: ops), as: UTF8.self)
        let strict = url.utf16.count <= 8192
        let expected = (strict ? bundle : bundleCompat).json(batch), got = (strict ? native : nativeCompat).json(batch)
        XCTAssertNil(native.validator.stopped, file: file, line: line)
        let name = label.isEmpty ? String(url.prefix(60)) : label
        XCTAssertEqual(got.0, expected.0, "ops: \(name)", file: file, line: line)
        XCTAssertEqual(got.2, expected.2, "diagnostics: \(name)", file: file, line: line)
        // the element's attributes: [name, value] pairs or a flat list
        let attrs = (got.0.firstObject as? [String: Any])?["attrs"] as? [Any] ?? []
        if let pairs = attrs as? [[String]] { return pairs.first { $0.first == "src" }?.last }
        let flat = attrs.compactMap { $0 as? String }
        guard let k = stride(from: 0, to: flat.count - 1, by: 2).first(where: { flat[$0] == "src" }) else { return nil }
        return flat[k + 1]
    }

    // MARK: url-policy-images.test.ts, 'url policy: data: images'

    func testEveryImageOfTheCorporaAndTheConformanceSetAtItsSize() throws {
        // corpus/attacks has images past the cap on purpose: they get the bundle's verdict, the others are taken
        var urls = Set<String>(), attacks = Set<String>()
        let pattern = try NSRegularExpression(pattern: "data:image/(?:png|jpe?g|gif|webp);base64,[A-Za-z0-9+/=]+")
        for dir in ["corpus", "packages/conformance"] {
            let root = Self.repo.appendingPathComponent(dir)
            guard let walk = FileManager.default.enumerator(at: root, includingPropertiesForKeys: nil) else { continue }
            for case let file as URL in walk where file.pathExtension == "html" {
                guard let text = try? String(contentsOf: file, encoding: .utf8) else { continue }
                let attack = file.path.contains("/corpus/attacks/")
                for match in pattern.matches(in: text, range: NSRange(text.startIndex..., in: text)) {
                    let url = String(text[Range(match.range, in: text)!])
                    if attack { attacks.insert(url) } else { urls.insert(url) }
                }
            }
        }
        try XCTSkipIf(urls.isEmpty, "the repository's corpus is not readable from this test process")
        // the conformance set's broken image (auto/02-attributes-images-b), which has to fire error
        XCTAssertEqual(Self.check("data:image/png;base64,AAAA"), URLPolicy.brokenImage)
        urls.remove("data:image/png;base64,AAAA")
        // and its image one pixel past the cap (auto/19-limits), which has to be the placeholder: the bundle's verdict too
        let limits = try String(contentsOf: Self.repo.appendingPathComponent("packages/conformance/auto/19-limits.html"), encoding: .utf8)
        let big = try XCTUnwrap(try NSRegularExpression(pattern: "id=\"img-big\"[^>]*src=\"([^\"]+)\"").firstMatch(in: limits, range: NSRange(limits.startIndex..., in: limits))
            .flatMap { Range($0.range(at: 1), in: limits) }.map { String(limits[$0]) })
        XCTAssertEqual(Self.size(big), [1025, 1024])
        XCTAssertNil(Self.check(big))
        XCTAssertNotEqual(sameVerdict(big, "auto/19-limits img-big"), big)
        urls.remove(big)
        XCTAssertGreaterThan(urls.count, 3, "\(urls.count) images")
        for url in urls.sorted() {
            let size = Self.size(url)
            XCTAssertTrue(size.map { $0[0] * $0[1] <= URLPolicy.maxImagePixels } ?? false, "\(url.prefix(60)): \(String(describing: size))")
            XCTAssertEqual(Self.check(url), url)
            XCTAssertEqual(sameVerdict(url), url)
        }
        for url in attacks.subtracting(urls).sorted() { sameVerdict(url, "corpus/attacks: \(url.prefix(60))") }
        print("data: images of the corpora: \(urls.count) taken, \(attacks.subtracting(urls).count) of corpus/attacks compared with the bundle")
    }

    func testRefusesAnImageWhoseHeaderClaimsMorePixelsThanABitmapMayHold() {
        XCTAssertEqual(URLPolicy.maxImagePixels, 1024 * 1024)
        XCTAssertEqual(Self.size(Self.pngHeader(4096, 4096)), [4096, 4096])
        XCTAssertNotNil(Self.check(Self.pngHeader(1024, 1024)))
        XCTAssertNotNil(Self.check(Self.pngHeader(4096, 256)))
        XCTAssertNil(Self.check(Self.pngHeader(1025, 1024)))
        XCTAssertNil(Self.check(Self.pngHeader(4096, 4096)))
        XCTAssertNil(Self.check(Self.pngHeader(65535, 65535)))
        // no overflow: a header may claim 2³² - 1 on each side
        XCTAssertNil(Self.check(Self.pngHeader(0xffff_ffff, 0xffff_ffff)))
        // GIF's logical screen, which every frame is drawn in
        XCTAssertNil(Self.check(Self.gif((65535, 65535), [0, 0, 1, 1])))
        for url in [Self.pngHeader(1024, 1024), Self.pngHeader(4096, 256), Self.pngHeader(1025, 1024), Self.pngHeader(1024, 1025), Self.pngHeader(4096, 4096), Self.pngHeader(4097, 4096),
                    Self.pngHeader(65535, 65535), Self.pngHeader(0xffff_ffff, 0xffff_ffff), Self.pngHeader(0, 7), Self.gif((65535, 65535), [0, 0, 1, 1]), Self.gif((1024, 1024), [0, 0, 1, 1]), Self.gif((1, 1), [1000, 0, 25, 1024])] {
            sameVerdict(url)
        }
        XCTAssertEqual(sameVerdict(Self.pngHeader(1024, 1024)), Self.pngHeader(1024, 1024))
        XCTAssertNotEqual(sameVerdict(Self.pngHeader(1025, 1024)), Self.pngHeader(1025, 1024), "the placeholder, as for a refused URL")
    }

    func testBoundsAGIFByItsFirstFrameTooWhereThatFrameReachesPastTheLogicalScreen() {
        XCTAssertEqual(Self.size(Self.gif((100, 100), [10, 10, 5, 5])), [100, 100])
        XCTAssertEqual(Self.size(Self.gif((100, 100), [90, 0, 20, 10])), [110, 100])
        XCTAssertEqual(Self.size(Self.gif((1, 1), [0, 0, 8192, 8192])), [8192, 8192])
        XCTAssertNil(Self.check(Self.gif((1, 1), [0, 0, 8192, 8192])))
        // no frame at all: nothing a decoder draws
        XCTAssertEqual(Self.check(Self.gif((1, 1))), URLPolicy.brokenImage)
        // thousands of tiny extensions before the frame (a script's): no image has them, and reading them is the host's time
        var bytes = Self.latin1("GIF89a") + [1, 0, 1, 0, 0, 0, 0]
        for _ in 0..<20000 { bytes += [0x21, 0xfe, 0] }
        bytes += [0x2c, 0, 0, 0, 0, 1, 0, 1, 0, 0, 2, 2, 0x4c, 0x01, 0, 0x3b]
        let crowded = "data:image/gif;base64," + Self.base64(bytes)
        XCTAssertEqual(Self.check(crowded), URLPolicy.brokenImage)
        for url in [Self.gif((100, 100), [10, 10, 5, 5]), Self.gif((100, 100), [90, 0, 20, 10]), Self.gif((1, 1), [0, 0, 8192, 8192]), Self.gif((1, 1))] { sameVerdict(url) }
        sameVerdict(crowded, "20000 GIF extensions")
    }

    func testTakesAWholeLosslessWebPShorterThanTheOtherChunksNeed() {
        let webp = "data:image/webp;base64,UklGRhQAAABXRUJQVlA4TAgAAAAvAAAAAIiICA=="
        XCTAssertEqual(Self.size(webp), [1, 1])
        XCTAssertEqual(Self.check(webp), webp)
        XCTAssertEqual(sameVerdict(webp), webp)
    }

    func testLeavesTheURLsFragmentOutOfTheBodyAsFetchDoes() {
        let png = Self.pngHeader(2, 3)
        let body = Self.body(png)
        let spacedWithFragment = png.replacingOccurrences(of: body, with: Self.spaced(body, every: 4)) + "#part"
        XCTAssertEqual(Self.size(spacedWithFragment), [2, 3])
        let small = Self.gif((1, 1), [0, 0, 1, 1]) + "#x"
        XCTAssertEqual(Self.size(small), [1, 1])
        XCTAssertEqual(Self.check(small), small)
        sameVerdict(spacedWithFragment)
        XCTAssertEqual(sameVerdict(small), small)
        // a fragment before the comma: no body at all
        sameVerdict("data:image/png;base64#x," + body)
        XCTAssertEqual(Self.check("data:image/png;base64#x," + body), URLPolicy.brokenImage)
    }

    func testReadsTheBodyAsFetchDoesPercentEncodedBase64WithSpaces() {
        let png = Self.pngHeader(2, 3)
        let body = Self.body(png)
        let raw = [UInt8](Data(base64Encoded: body)!)
        let urls = [
            png.replacingOccurrences(of: body, with: Self.spaced(body, every: 4)),
            "data:image/png;base64," + body.replacingOccurrences(of: "+", with: "%2B").replacingOccurrences(of: "/", with: "%2F").replacingOccurrences(of: "=", with: "%3D"),
            "data:image/png," + Self.percentEncoded(raw),
            // the parameter's spelling and white space, as the bundle reads it
            "data:image/png;BASE64," + body,
            "data:image/png; \tbase64 \t," + body,
            "data:image/png;charset=x;base64," + body,
        ]
        for url in urls {
            XCTAssertEqual(Self.size(url), [2, 3], url)
            sameVerdict(url)
        }
        // not the base64 parameter: the body is the bytes as written
        for url in ["data:image/png;xbase64," + body, "data:image/png;base64x," + body, "data:image/png;base 64," + body] {
            XCTAssertNil(Self.size(url), url)
            sameVerdict(url)
        }
    }

    func testBreaksBytesOfAnotherFormatOrNoneBehindAnImageType() {
        // BMP, outside the profile
        let bmp = "data:image/png;base64," + Self.base64([0x42, 0x4d] + [UInt8](repeating: 0, count: 52))
        for url in [bmp, "data:image/png;base64,AAAA", "data:image/png;base64,!!", URLPolicy.brokenImage, "data:image/png;base64,AA==", "data:image/gif,x", " \u{1}data:image/gif,x "] {
            XCTAssertEqual(Self.check(url), URLPolicy.brokenImage, url)
            XCTAssertEqual(sameVerdict(url), URLPolicy.brokenImage, url)
        }
        // a type outside the profile, or a URL that is no data: image: refused before its bytes are read
        for url in ["data:image/svg+xml,<svg/>", "data:image/bmp;base64,Qk0=", "data:image/png", "data:image/png;x", "blob:x", "javascript:x"] {
            XCTAssertNil(Self.check(url), url)
            sameVerdict(url)
        }
    }

    func testFindsAJPEGsSizePastTheSegmentsBeforeItInEveryEncodingAndNoFurtherThan64KB() {
        let jpeg = Self.jpegWithSegments([30000, 32000])
        let body = Self.base64(jpeg)
        for url in [
            "data:image/jpeg;base64," + body,
            "data:image/jpeg;base64," + Self.spaced(body, every: 76),
            "data:image/jpeg;base64," + body.replacingOccurrences(of: "/", with: "%2F").replacingOccurrences(of: "+", with: "%2b"),
            "data:image/jpeg," + Self.percentEncoded(jpeg),
        ] {
            XCTAssertEqual(Self.size(url), [16, 16], String(url.prefix(40)))
            XCTAssertEqual(sameVerdict(url, String(url.prefix(40))), url)
        }
        let far = "data:image/jpeg;base64," + Self.base64(Self.jpegWithSegments([65533]))
        XCTAssertEqual(Self.check(far), URLPolicy.brokenImage)
        sameVerdict(far, "a frame header past 64 KB")
    }

    func testKeepsTheBytesWhereTheBrowserHasThemAPercentUIsNoEscapeAPairOfSurrogatesIsOneCharacter() {
        // the browser's bytes: the body as UTF-8, percent-decoded (%u is left alone)
        func app(_ payload: String, escapes: Int = 0, length: Int? = nil) -> String {
            // the segment's length in the browser's bytes: an escape `%XX` is one
            let length = length ?? payload.utf8.count - 2 * escapes + 2
            return "%FF%D8%FF%E1" + Self.percentEncoded([UInt8(length >> 8), UInt8(length & 255)]) + payload + Self.sof16
        }
        let escape = "data:image/jpeg," + app("x%u0041y")
        XCTAssertEqual(Self.size(escape), [16, 16])
        sameVerdict(escape)
        // the high surrogate on the last character the decoder reads at first
        let prefix = app("").range(of: "%FF%C0")!.lowerBound.utf16Offset(in: app(""))
        let pair = "data:image/jpeg," + app(String(repeating: "a", count: 4095 - prefix) + "😀" + String(repeating: "b", count: 100))
        XCTAssertEqual(Self.size(pair), [16, 16])
        sameVerdict(pair, "a surrogate pair across the decoder's first part")
        // an escape cut by the first part, and one at the very end
        for cut in [4094, 4095] {
            let url = "data:image/jpeg," + app(String(repeating: "a", count: cut - prefix) + "%41" + String(repeating: "b", count: 100), escapes: 1)
            XCTAssertEqual(Self.size(url), [16, 16], "escape at \(cut)")
            sameVerdict(url, "an escape at \(cut)")
        }
        sameVerdict("data:image/png,%89PNG%0D%0A%1A%0A%00%00%00%0DIHDR%00%00%00%02%00%00%00%03%0")
        // canonically equivalent characters are other bytes (é is two, e and U+0301 three), checked one after the other:
        // the last image remembered is the same characters, not an equivalent string
        let composed = "data:image/jpeg," + app("\u{e9}"), decomposed = "data:image/jpeg," + app("e\u{301}", length: 4)
        XCTAssertEqual(composed, decomposed, "Swift's == takes them for one string")
        XCTAssertEqual(Self.check(composed), composed)
        XCTAssertEqual(Self.check(decomposed), URLPolicy.brokenImage)
        XCTAssertEqual(sameVerdict(composed, "é"), composed)
        XCTAssertEqual(sameVerdict(decomposed, "e and U+0301"), URLPolicy.brokenImage)
    }

    // MARK: url-policy-images.test.ts, 'url policy: data: image scans at their worst'

    func testWalksAJPEGToItsFrameHeaderInAtMost1024StepsEachAReadInPlace() {
        XCTAssertEqual(Self.size("data:image/jpeg;base64," + Self.base64(Self.jpegWithFill(1024))), [16, 16])
        XCTAssertNil(Self.size("data:image/jpeg;base64," + Self.base64(Self.jpegWithFill(1025))))
        var restart: [UInt8] = [0xff, 0xd8], empty: [UInt8] = [0xff, 0xd8]
        for _ in 0..<32000 { restart += [0xff, 0xd0] }
        for _ in 0..<16000 { empty += [0xff, 0xe0, 0, 2] }
        let fill: [UInt8] = [0xff, 0xd8] + [UInt8](repeating: 0xff, count: 65000)
        for (name, bytes) in [("fill bytes", fill), ("restart markers", restart), ("empty segments", empty)] {
            let url = "data:image/jpeg;base64," + Self.base64(bytes)
            XCTAssertEqual(Self.check(url), URLPolicy.brokenImage, name)
            let decoded = Self.decoded(url)
            XCTAssertLessThanOrEqual(decoded, 2 * 4096, "\(name): \(decoded) characters decoded")
            let cost = Self.scanCost(url)
            XCTAssertLessThan(cost, Self.costBound, "\(name): \(cost) parses of its message")
            sameVerdict(url, name)
        }
        // percent-encoded, which is decoded from its start: as far as the steps go
        let percent = "data:image/jpeg," + Self.percentEncoded(fill, pad: false)
        XCTAssertEqual(Self.check(percent), URLPolicy.brokenImage)
        let cost = Self.scanCost(percent)
        XCTAssertLessThan(cost, Self.costBound, "percent-encoded fill bytes: \(cost) parses of its message")
        sameVerdict(percent, "percent-encoded fill bytes")
        // a frame header past 62 KB of segments: the windows the steps land in
        let far = "data:image/jpeg;base64," + Self.base64(Self.jpegWithSegments([30000, 32000]))
        XCTAssertEqual(Self.size(far), [16, 16])
        XCTAssertLessThanOrEqual(Self.decoded(far), 3 * 4096)
        for steps in [1024, 1025] { sameVerdict("data:image/jpeg;base64," + Self.base64(Self.jpegWithFill(steps)), "\(steps) fill bytes") }
    }

    func testWalksAGIFToItsFirstFrameInAtMost4096SubBlocksEachAReadInPlace() {
        XCTAssertEqual(Self.size(Self.gifWithBlocks(4095, 1)), [1, 1])
        XCTAssertNil(Self.size(Self.gifWithBlocks(4096, 1)))
        // the farthest the steps reach: 4095 sub-blocks to 61 KB in, decoded once
        let far = Self.gifWithBlocks(4095, 14)
        XCTAssertEqual(Self.size(far), [1, 1])
        var decoded = Self.decoded(far)
        XCTAssertLessThanOrEqual(decoded, Self.body(far).utf16.count + 1 + 4096, "\(decoded) characters decoded")
        var cost = Self.scanCost(far)
        XCTAssertLessThan(cost, Self.costBound, "\(cost) parses of its message")
        // one-byte sub-blocks to 64 KB
        let tiny = Self.gifWithBlocks(32000, 1)
        XCTAssertEqual(Self.check(tiny), URLPolicy.brokenImage)
        decoded = Self.decoded(tiny)
        XCTAssertLessThanOrEqual(decoded, 3 * 4096, "\(decoded) characters decoded")
        cost = Self.scanCost(tiny)
        XCTAssertLessThan(cost, Self.costBound, "\(cost) parses of its message")
        for (label, url) in [("4095 sub-blocks", Self.gifWithBlocks(4095, 1)), ("4096 sub-blocks", Self.gifWithBlocks(4096, 1)), ("far", far), ("tiny", tiny)] { sameVerdict(url, label) }
    }

    func testReadsAPNGsOrAWebPsSizeFromTheBodysFirstWindowWhateverFollows() {
        let tail = [UInt8](repeating: 0, count: 65000)
        let images: [(String, [UInt8], [Int])] = [
            ("png", [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52] + Self.u32be(2) + Self.u32be(3) + [8, 6, 0, 0, 0], [2, 3]),
            ("webp", Self.webpChunk("VP8X", [10, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 2, 0, 0]), [2, 3]),
            ("webp", Self.webpChunk("VP8L", [13, 0, 0, 0, 0x2f, 0x01, 0x80, 0, 0]), [2, 3]),
            ("webp", Self.webpChunk("VP8 ", [10, 0, 0, 0, 0, 0, 0, 0x9d, 0x01, 0x2a, 2, 0, 3, 0]), [2, 3]),
        ]
        for (type, header, size) in images {
            let url = "data:image/\(type);base64," + Self.base64(header + tail)
            XCTAssertEqual(Self.size(url), size, String(url.prefix(50)))
            let decoded = Self.decoded(url)
            XCTAssertLessThanOrEqual(decoded, 4096, "\(url.prefix(50)): \(decoded) characters decoded")
            let cost = Self.scanCost(url)
            XCTAssertLessThan(cost, Self.costBound, "\(url.prefix(50)): \(cost) parses of its message")
            XCTAssertEqual(sameVerdict(url, String(url.prefix(50))), url)
            // the header alone, too short for the chunk it names
            sameVerdict("data:image/\(type);base64," + Self.base64(Array(header.prefix(header.count - 1))), "\(type) cut short")
        }
    }

    func testTriesABodyThatIsNoBase64OnceHoweverManySignaturesAreLookedForAfter() {
        let valid = Self.base64([UInt8](repeating: 0x41, count: 48000))
        let broken = [
            ("a character no base64 has", "data:image/png;base64,!" + valid.dropFirst()),
            ("a padding in the middle", "data:image/png;base64,QQ==" + valid),
            ("with white space", "data:image/png;base64,! " + Self.spaced(valid, every: 76)),
            ("a character past ASCII", "data:image/png;base64,é" + valid.dropFirst()),
        ]
        for (name, url) in broken {
            XCTAssertEqual(Self.check(url), URLPolicy.brokenImage, name)
            let decoded = Self.decoded(url)
            XCTAssertLessThanOrEqual(decoded, 4096, "\(name): \(decoded) characters decoded")
            let cost = Self.scanCost(url)
            XCTAssertLessThan(cost, Self.costBound, "\(name): \(cost) parses of its message")
            sameVerdict(url, name)
        }
        // a short one, whose message costs next to nothing to parse: one try
        XCTAssertEqual(Self.decoded("data:image/png;base64,!!"), 2)
    }

    /// The forgiving base64 decoder `atob` is, case by case (V8 and QuickJS agree on each).
    func testForgivingBase64AsAtobDecodesIt() {
        let cases: [(String, [UInt8]?)] = [
            ("", []), ("QQ", [0x41]), ("QQ==", [0x41]), ("QUE", [0x41, 0x41]), ("QUE=", [0x41, 0x41]), ("QUFB", [0x41, 0x41, 0x41]),
            ("Q", nil), ("QQ=", nil), ("QUFB=", nil), ("QUFB==", nil), ("QQ==QUFB", nil), ("QQ===", nil), ("Q===", nil),
            (" Q Q = = ", [0x41]), ("QQ\n==\t", [0x41]), ("Q\u{0C}U\rFB", [0x41, 0x41, 0x41]), ("QU-B", nil), ("QU_B", nil), ("QUé", nil), ("QUFB\u{0B}", nil),
        ]
        for (text, bytes) in cases {
            XCTAssertEqual(URLPolicy.forgivingBase64(Array(text.utf16)), bytes, text.debugDescription)
        }
    }
}
