import XCTest
@testable import WidgetSandbox
import WidgetSandboxProtocol

final class HostOpsFuzzTests: XCTestCase {
    struct RNG {
        var state: UInt64
        mutating func next() -> UInt64 {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return state >> 33
        }
        mutating func int(_ n: Int) -> Int { Int(next() % UInt64(max(n, 1))) }
    }

    func seed() -> BinaryOpsPayload {
        var e = BinaryOpsEncoder()
        e.begin(reset: true)
        e.element(2, tag: "html", svg: false, attributes: [])
        e.element(4, tag: "body", svg: false, attributes: [])
        e.element(10, tag: "a", svg: false, attributes: [("href", "javascript:alert(1)"), ("onclick", "x()")])
        e.element(11, tag: "img", svg: false, attributes: [("src", "http://127.0.0.1:9999/hit/fz"), ("srcset", "http://127.0.0.1:9999/hit/fz2 2x")])
        e.element(12, tag: "style", svg: false, attributes: [])
        e.text(13, "@import 'http://127.0.0.1:9999/hit/fz3'; p{background:url(http://127.0.0.1:9999/hit/fz4)}")
        e.insert(parent: 12, 13, before: 0)
        e.insert(parent: 4, 10, before: 0)
        e.insert(parent: 4, 11, before: 0)
        e.insert(parent: 4, 12, before: 0)
        e.style(10, "background", "url(http://127.0.0.1:9999/hit/fz5)", important: true)
        e.attribute(10, "style", "background:url(http://127.0.0.1:9999/hit/fz6)")
        e.element(14, tag: "image", svg: true, attributes: [("href", "http://127.0.0.1:9999/hit/fz7"), ("xlink:href", "http://127.0.0.1:9999/hit/fz8")])
        e.insert(parent: 4, 14, before: 0)
        e.json("{\"op\":\"attr\",\"id\":10,\"name\":\"href\",\"value\":\"http://127.0.0.1:9999/hit/fz9\"}")
        return e.finish()
    }

    func mutate(_ p: BinaryOpsPayload, _ r: inout RNG) -> BinaryOpsPayload {
        var w = p.words, s = p.strings
        for _ in 0..<(1 + r.int(6)) {
            switch r.int(9) {
            case 0 where w.count > 2: w[2 + r.int(w.count - 2)] = Int32(truncatingIfNeeded: Int64(r.next()) - (1 << 30))
            case 1 where w.count > 2: w[2 + r.int(w.count - 2)] = Int32(r.int(14))
            case 2 where w.count > 2: w[2 + r.int(w.count - 2)] = [Int32.max, Int32.min, -1, 0, 1, 4096, 65536][r.int(7)]
            case 3 where w.count > 3: w.removeSubrange((2 + r.int(w.count - 3))...)
            case 4: w.append(Int32(r.int(14))); w.append(Int32(r.int(300)))
            case 5 where !s.isEmpty: s[r.int(s.count)] = UInt8(r.int(256))
            case 6 where !s.isEmpty: s.removeLast(r.int(s.count))
            case 7: s += Array("\u{0}\u{FFFF}\u{1F600}\\68ttp://127.0.0.1:9999/hit/m".utf8)
            case 8: w[1] = Int32(r.int(1000)) - 10
            default: break
            }
        }
        return BinaryOpsPayload(words: w, strings: s)
    }

    static let fetchable = try! NSRegularExpression(pattern: ##"url\(\s*\\?['"]?\s*(https?|javascript|file|ftp):|@import|\["(href|src|srcset|xlink:href|action|poster|data|background)","[^"]*(https?:|javascript:|file:)|\["on[a-z]+","##)

    static func dangerous(_ text: String) -> Bool {
        fetchable.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    func testDetectorCatchesTheSeedPatterns() {
        XCTAssertTrue(Self.dangerous(#"{"value":"url(http://x)"}"#))
        XCTAssertTrue(Self.dangerous(#"[["href","javascript:alert(1)"]]"#))
        XCTAssertTrue(Self.dangerous(#"[["onclick","x()"]]"#))
        XCTAssertFalse(Self.dangerous(#"{"value":"u"l(http://x)""}"#))
    }

    func testMutatedBinaryOpStreamsNeverCrashOrLeak() {
        let validator = TrustedValidator.background(theme: "light", resources: .shared, thread: nil) { _, _ in }
        validator.useNativeModel(true)
        var r = RNG(state: 0x7EDBEEF)
        let base = seed()
        var leaks: [String] = []
        let iterations = Int(ProcessInfo.processInfo.environment["HOSTOPS_FUZZ"] ?? "") ?? 4000
        for i in 0..<iterations {
            let payload = i == 0 ? base : mutate(base, &r)
            _ = BinaryOps.decode(payload, table: BinaryInternTable())
            if i % 50 == 0 { validator.reset(theme: "light"); validator.useNativeModel(true) }
            let batch = validator.processBinary(payload, canvas: [:], typed: false)
            let out: String
            switch batch {
            case .empty: out = ""
            case .json(let json): out = json
            case .ops(let ops): out = WidgetOp.json(ops)
            }
            let lower = HostOpsAttackTests.withoutTextData("{\"ops\":" + (out.isEmpty ? "[]" : out) + "}").lowercased()
            if Self.dangerous(lower) {
                leaks.append("#\(i): " + String(out.prefix(1500)))
                if leaks.count > 5 { break }
            }
        }
        XCTAssertEqual(leaks, [], leaks.joined(separator: "\n"))
    }
}
