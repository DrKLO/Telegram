import Foundation
import WidgetSandboxProtocol

enum ElementBatchFastPath {
    static let maxCanvasIds = 16
    static let maxElementIds = 4096
    /// A style or class value: under the validator's 8 KB attribute cap, so nothing here can pass it.
    static let maxValueBytes = 4096

    struct Plan {
        var output: String
        var canvasIds: [Int]
        var elementIds: [Int]
        var ops: [WidgetOp]?
    }

    /// Properties whose value the profile judges only by its functions and units (CSSValueCanon checks both), so a
    /// canonical value is what the validator would give. `display`, `position`, `overflow`, `rotate`, `translate`,
    /// `scale`, `filter`, `backdrop-filter` and the font properties have value rules of their own (docs/profile-v1.md)
    /// and take the oracle.
    static let styleNames = CanvasBatchFastPath.WordSet("""
        transform transform-origin opacity visibility z-index
        left top right bottom inset width height min-width min-height max-width max-height
        margin margin-top margin-right margin-bottom margin-left padding padding-top padding-right padding-bottom padding-left
        background background-color background-position background-size color border-color border-width border-radius
        outline-color box-shadow font-size font-weight line-height letter-spacing text-align
        transition transition-delay transition-duration transition-property transition-timing-function
        animation-delay animation-duration animation-play-state fill stroke stroke-width stroke-dashoffset stroke-dasharray
        flex flex-basis flex-grow flex-shrink gap order justify-content align-items flex-wrap cursor pointer-events will-change
        """)

    static func accept(_ json: String, typed: Bool = false) -> Plan? {
        json.utf8.withContiguousStorageIfAvailable { accept(bytes: $0, typed: typed) } ?? nil
    }

    static func accept(bytes b: UnsafeBufferPointer<UInt8>, typed: Bool = false) -> Plan? {
        if b.count > CanvasBatchFastPath.maxBatchBytes { return nil }
        var s = CanvasBatchFastPath.Scanner(b: b)
        var out: [UInt8] = []
        out.reserveCapacity(b.count + b.count / 4 + 16)
        var canvasIds: [Int] = []
        var elementIds: [Int] = []
        var seenElements = Set<Int>()
        var typedOps: [WidgetOp] = []
        guard s.eat(UInt8(ascii: "[")) else { return nil }
        out.append(UInt8(ascii: "["))
        var ops = 0
        repeat {
            ops += 1
            if ops > CanvasBatchFastPath.maxOps { return nil }
            if ops > 1 { out.append(UInt8(ascii: ",")) }
            let start = s.i
            if s.literal("{\"op\":\"canvas\",") {
                s.i = start
                guard let id = s.canvasOp() else { return nil }
                out.append(contentsOf: b[start..<s.i])
                if typed {
                    guard let fields = (try? JSONSerialization.jsonObject(with: Data(b[start..<s.i]))) as? [String: Any] else { return nil }
                    typedOps.append(WidgetOp(fields))
                }
                if !canvasIds.contains(id) {
                    if canvasIds.count == maxCanvasIds { return nil }
                    canvasIds.append(id)
                }
            } else if s.literal("{\"op\":\"style\",\"id\":") {
                guard let id = s.nodeId(), s.literal(",\"name\":"), let name = s.plainString(), styleNames.contains(name),
                      s.literal(",\"value\":"), let value = s.plainString(), value.count <= maxValueBytes else { return nil }
                var important = false
                if s.literal(",\"important\":") {
                    if s.literal("true") { important = true } else if !s.literal("false") { return nil }
                }
                guard s.eat(UInt8(ascii: "}")) else { return nil }
                append(&out, "{\"op\":\"style\",\"id\":")
                out.append(contentsOf: String(id).utf8)
                append(&out, ",\"name\":\"")
                out.append(contentsOf: name)
                append(&out, "\",\"value\":\"")
                let valueStart = out.count
                if !value.isEmpty, !CSSValueCanon.append(value, to: &out) { return nil }
                if typed { typedOps.append(WidgetOp(style: id, name: String(decoding: name, as: UTF8.self), value: String(decoding: out[valueStart...], as: UTF8.self), important: important)) }
                append(&out, important ? "\",\"important\":true}" : "\",\"important\":false}")
                if seenElements.insert(id).inserted {
                    if elementIds.count == maxElementIds { return nil }
                    elementIds.append(id)
                }
            } else if s.literal("{\"op\":\"attr\",\"id\":") {
                guard let id = s.nodeId(), s.literal(",\"name\":\"class\",\"value\":"), let value = s.plainString(), value.count <= maxValueBytes, s.eat(UInt8(ascii: "}")) else { return nil }
                out.append(contentsOf: b[start..<s.i])
                if typed { typedOps.append(WidgetOp(attribute: id, name: "class", value: String(decoding: value, as: UTF8.self))) }
                if seenElements.insert(id).inserted {
                    if elementIds.count == maxElementIds { return nil }
                    elementIds.append(id)
                }
            } else {
                return nil
            }
        } while s.eat(UInt8(ascii: ","))
        guard s.eat(UInt8(ascii: "]")), s.i == b.count else { return nil }
        out.append(UInt8(ascii: "]"))
        return Plan(output: String(decoding: out, as: UTF8.self), canvasIds: canvasIds, elementIds: elementIds, ops: typed ? typedOps : nil)
    }

    @inline(__always) private static func append(_ out: inout [UInt8], _ s: StaticString) {
        out.append(contentsOf: UnsafeBufferPointer(start: s.utf8Start, count: s.utf8CodeUnitCount))
    }
}

enum CSSValueCanon {
    static let maxDepth = 8
    static let maxTokens = 1024

    /// The profile's functions (`tables.css.functions` in packages/core/profile.json), as widgets write them; a value with
    /// any other function (3D transforms, new colour spaces, filters beyond blur/brightness/drop-shadow) takes the oracle.
    static let functions = CanvasBatchFastPath.WordSet("""
        translate translateX translateY scale scaleX scaleY rotate skew skewX skewY matrix
        rgb rgba hsl hsla
        linear-gradient radial-gradient conic-gradient repeating-linear-gradient repeating-radial-gradient repeating-conic-gradient
        cubic-bezier steps linear blur brightness drop-shadow
        calc min max clamp minmax repeat fit-content
        """)

    /// The profile's units (`tables.css.units`), lowercase; a dimension in any other (`vh`, `cqw`, `dppx`…) takes the oracle.
    static let units: [[UInt8]] = """
        px em rem ch ex pt pc cm mm in q vw svw lvw dvw vi svi lvi dvi vmin svmin lvmin dvmin vmax svmax lvmax dvmax deg rad grad turn s ms fr
        """.split(separator: " ").map { Array($0.utf8) }

    @inline(__always) private static func isUnit(_ b: UnsafeBufferPointer<UInt8>, _ start: Int, _ end: Int) -> Bool {
        let n = end - start
        return units.contains { u in u.count == n && (0..<n).allSatisfy { b[start + $0] | 0x20 == u[$0] } }
    }

    private static let safePairs: Set<Int> = {
        let table = """
            1,1 1,2 1,7 1,8 1,- 1,10 1,11 1,12 1,15 1,21 3,1 3,2 3,7 3,8 3,- 3,10 3,11 3,12 3,15 4,1 4,2 4,7 4,8 4,- 4,10 4,11 4,12 4,15
            12,1 12,2 12,7 12,8 12,- 12,10 12,11 12,12 12,15 #,1 #,2 #,7 #,8 #,- #,10 #,11 #,12 #,15 -,1 -,2 -,7 -,8 -,- -,10 -,11 -,12 -,15
            10,1 10,2 10,7 10,8 10,10 10,11 10,12 10,% 10,15 @,1 @,2 @,7 @,8 @,- @,15 .,10 .,11 .,12 +,10 +,11 +,12 /,*
            1,4 12,4 4,4 3,21 3,5 3,16 11,11 11,12 11,2 11,- 22,1 22,2 22,11 22,12 22,4 22,-
            """
        func code(_ part: Substring) -> Int {
            if let type = Int(part) { return type << 1 }
            return Int(min(part.utf8.first!, 128)) << 7
        }
        var set = Set<Int>()
        for pair in table.split(whereSeparator: { $0 == " " || $0 == "\n" }) {
            let parts = pair.split(separator: ",", maxSplits: 1)
            set.insert(code(parts[0]) << 16 | code(parts[1]))
        }
        return set
    }()

    private static let ident = 1, function = 2, hash = 4, delim = 9, number = 10, percentage = 11, dimension = 12, comma = 18, rightParenthesis = 22

    @inline(__always) private static func isDigit(_ c: UInt8) -> Bool { c >= 0x30 && c <= 0x39 }
    @inline(__always) private static func isLetter(_ c: UInt8) -> Bool { (c | 0x20) >= 0x61 && (c | 0x20) <= 0x7a }
    @inline(__always) private static func isName(_ c: UInt8) -> Bool { isLetter(c) || isDigit(c) || c == 0x5f || c == 0x2d }
    @inline(__always) private static func ends(_ b: UnsafeBufferPointer<UInt8>, _ i: Int, _ end: Int) -> Bool {
        i == end || b[i] == 0x20 || b[i] == 0x2c || b[i] == 0x29 || b[i] == 0x2f
    }

    static func append(_ value: Slice<UnsafeBufferPointer<UInt8>>, to out: inout [UInt8]) -> Bool {
        let b = value.base
        var i = value.startIndex
        let end = value.endIndex
        var previous = 0
        var depth = 0
        var tokens = 0
        while i < end {
            let c = b[i]
            if c == 0x20 { i += 1; continue }
            tokens += 1
            if tokens > maxTokens { return false }
            let start = i
            let type: Int
            switch c {
            case 0x2c:
                type = comma
                i += 1
            case 0x2f:
                type = delim
                i += 1
            case 0x29:
                guard depth > 0 else { return false }
                depth -= 1
                type = rightParenthesis
                i += 1
            case 0x23:
                i += 1
                let name = i
                while i < end, isName(b[i]) { i += 1 }
                guard i > name, ends(b, i, end) else { return false }
                type = hash
            default:
                if isDigit(c) || c == 0x2e || (c == 0x2d && i + 1 < end && (isDigit(b[i + 1]) || b[i + 1] == 0x2e)) {
                    guard let (kind, next) = numeric(b, i, end) else { return false }
                    type = kind
                    i = next
                } else if isLetter(c) || c == 0x5f || (c == 0x2d && i + 1 < end && (isLetter(b[i + 1]) || b[i + 1] == 0x5f)) {
                    i += 1
                    while i < end, isName(b[i]) { i += 1 }
                    if i < end, b[i] == 0x28 {
                        guard depth < maxDepth, functions.contains(b[start..<i]) else { return false }
                        i += 1
                        depth += 1
                        type = function
                    } else {
                        guard ends(b, i, end) else { return false }
                        type = ident
                    }
                } else {
                    return false
                }
            }
            let first = b[start]
            let code = (type == delim ? Int(min(first, 128)) << 6 : type) << 1
            let space: Bool
            if (first == 0x2d && type != ident && type != function) || first == 0x2b {
                space = safePairs.contains((previous & 65534) << 16 | Int(first) << 7)
            } else {
                space = safePairs.contains((previous & 65534) << 16 | code)
            }
            if space { out.append(0x20) }
            out.append(contentsOf: b[start..<i])
            previous = code | (space ? 1 : 0)
        }
        return depth == 0 && tokens > 0
    }

    private static func numeric(_ b: UnsafeBufferPointer<UInt8>, _ start: Int, _ end: Int) -> (Int, Int)? {
        var j = start
        if b[j] == 0x2d { j += 1 }
        let integer = j
        while j < end, isDigit(b[j]) { j += 1 }
        if j < end, b[j] == 0x2e {
            guard j + 1 < end, isDigit(b[j + 1]) else { return nil }
            j += 1
            while j < end, isDigit(b[j]) { j += 1 }
        } else if j == integer {
            return nil
        }
        let type: Int
        if j < end, b[j] == 0x25 {
            j += 1
            type = percentage
        } else if j < end, isLetter(b[j]) {
            if b[j] | 0x20 == 0x65, j + 1 < end, isDigit(b[j + 1]) || ((b[j + 1] == 0x2b || b[j + 1] == 0x2d) && j + 2 < end && isDigit(b[j + 2])) { return nil }
            let unit = j
            while j < end, isLetter(b[j]) { j += 1 }
            guard isUnit(b, unit, j) else { return nil }
            type = dimension
        } else {
            type = number
        }
        guard ends(b, j, end) else { return nil }
        return (type, j)
    }
}

struct MixedStyleBatch {
    static let maxDepth = 64
    static let minimumPatches = 1

    struct Patch {
        var name: [UInt8]
        var value: [UInt8]
    }

    let blanked: String
    let diagnostics: [String: String]
    private let marker: [UInt8]
    private let patches: [Patch]

    init?(_ json: String, nonce: UInt64) {
        guard let made = (json.utf8.withContiguousStorageIfAvailable { Self.make($0, nonce: nonce) } ?? nil) else { return nil }
        (blanked, diagnostics, marker, patches) = made
    }

    private static func make(_ b: UnsafeBufferPointer<UInt8>, nonce: UInt64) -> (String, [String: String], [UInt8], [Patch])? {
        if b.count > CanvasBatchFastPath.maxBatchBytes || !containsStyleOp(b) { return nil }
        let marker = Array("tw\(String(nonce, radix: 16))x".utf8)
        var s = CanvasBatchFastPath.Scanner(b: b)
        var out: [UInt8] = []
        var patches: [Patch] = []
        var diagnostics: [String: String] = [:]
        var copied = 0
        s.skipSpace()
        guard s.eat(UInt8(ascii: "[")) else { return nil }
        s.skipSpace()
        if s.eat(UInt8(ascii: "]")) { return nil }
        var ops = 0
        repeat {
            ops += 1
            if ops > CanvasBatchFastPath.maxOps { return nil }
            s.skipSpace()
            let start = s.i
            if let style = s.strictStyleOp(), !style.value.isEmpty, s.atElementEnd() {
                var canonical: [UInt8] = []
                if CSSValueCanon.append(style.value, to: &canonical) {
                    let tag = marker + Array(String(patches.count).utf8)
                    out.append(contentsOf: b[copied..<style.nameStart])
                    out.append(contentsOf: tag)
                    out.append(contentsOf: b[style.nameEnd..<style.valueStart])
                    out.append(contentsOf: b[style.valueEnd..<s.i])
                    copied = s.i
                    var blankedOp = Array(b[start..<style.nameStart])
                    blankedOp += tag
                    blankedOp += b[style.nameEnd..<style.valueStart]
                    blankedOp += b[style.valueEnd..<s.i]
                    let original = b[start..<s.i]
                    let shown = original.count > 160 ? String(decoding: original.prefix(160), as: UTF8.self) + "\u{2026}" : String(decoding: original, as: UTF8.self)
                    diagnostics["rejected op " + String(decoding: blankedOp, as: UTF8.self)] = "rejected op " + shown
                    patches.append(Patch(name: Array(b[style.nameStart..<style.nameEnd]), value: canonical))
                }
            } else {
                s.i = start
                guard s.skipValue(depth: 0) else { return nil }
            }
            s.skipSpace()
        } while s.eat(UInt8(ascii: ","))
        guard s.eat(UInt8(ascii: "]")) else { return nil }
        s.skipSpace()
        guard s.i == b.count, patches.count >= minimumPatches else { return nil }
        out.append(contentsOf: b[copied..<b.count])
        return (String(decoding: out, as: UTF8.self), diagnostics, marker, patches)
    }

    private static func containsStyleOp(_ b: UnsafeBufferPointer<UInt8>) -> Bool {
        let needle: StaticString = "{\"op\":\"style\",\"id\":"
        guard let base = b.baseAddress else { return false }
        return memmem(base, b.count, needle.utf8Start, needle.utf8CodeUnitCount) != nil
    }

    func patch(_ output: String) -> String? {
        output.utf8.withContiguousStorageIfAvailable { patch(bytes: $0) } ?? nil
    }

    private func patch(bytes o: UnsafeBufferPointer<UInt8>) -> String? {
        let prefix = Array("\"name\":\"".utf8) + marker
        let blank = Array("\",\"value\":\"\"".utf8)
        var used = [Bool](repeating: false, count: patches.count)
        var out: [UInt8] = []
        out.reserveCapacity(o.count + patches.reduce(0) { $0 + $1.value.count + $1.name.count })
        var copied = 0
        var i = 0
        while i + prefix.count <= o.count {
            guard o[i] == prefix[0], prefix.withUnsafeBufferPointer({ memcmp($0.baseAddress!, o.baseAddress! + i, prefix.count) == 0 }) else { i += 1; continue }
            var j = i + prefix.count
            var k = 0
            let digits = j
            while j < o.count, o[j] >= 0x30, o[j] <= 0x39, j - digits < 7 { k = k * 10 + Int(o[j] - 0x30); j += 1 }
            guard j > digits, k < patches.count, !used[k], j + blank.count <= o.count,
                  blank.withUnsafeBufferPointer({ memcmp($0.baseAddress!, o.baseAddress! + j, blank.count) == 0 }) else { return nil }
            used[k] = true
            out.append(contentsOf: o[copied..<i])
            out.append(contentsOf: "\"name\":\"".utf8)
            out.append(contentsOf: patches[k].name)
            out.append(contentsOf: "\",\"value\":\"".utf8)
            out.append(contentsOf: patches[k].value)
            out.append(UInt8(ascii: "\""))
            i = j + blank.count
            copied = i
        }
        out.append(contentsOf: o[copied..<o.count])
        return String(decoding: out, as: UTF8.self)
    }
}

extension CanvasBatchFastPath.Scanner {
    struct StyleOp {
        var nameStart: Int
        var nameEnd: Int
        var valueStart: Int
        var valueEnd: Int
        var value: Slice<UnsafeBufferPointer<UInt8>>
    }

    mutating func strictStyleOp() -> StyleOp? {
        guard literal("{\"op\":\"style\",\"id\":"), nodeId() != nil, literal(",\"name\":") else { return nil }
        let nameStart = i + 1
        guard let name = plainString(), ElementBatchFastPath.styleNames.contains(name) else { return nil }
        let nameEnd = i - 1
        guard literal(",\"value\":") else { return nil }
        let valueStart = i + 1
        guard let value = plainString(), value.count <= ElementBatchFastPath.maxValueBytes else { return nil }
        let valueEnd = i - 1
        if literal(",\"important\":") {
            guard literal("true") || literal("false") else { return nil }
        }
        guard eat(UInt8(ascii: "}")) else { return nil }
        return StyleOp(nameStart: nameStart, nameEnd: nameEnd, valueStart: valueStart, valueEnd: valueEnd, value: value)
    }

    mutating func skipSpace() {
        while i < b.count, b[i] == 0x20 || b[i] == 0x09 || b[i] == 0x0a || b[i] == 0x0d { i += 1 }
    }

    mutating func atElementEnd() -> Bool {
        let saved = i
        skipSpace()
        let ok = i < b.count && (b[i] == UInt8(ascii: ",") || b[i] == UInt8(ascii: "]"))
        i = saved
        return ok
    }

    mutating func skipValue(depth: Int) -> Bool {
        if depth > MixedStyleBatch.maxDepth { return false }
        skipSpace()
        guard i < b.count else { return false }
        switch b[i] {
        case UInt8(ascii: "{"):
            i += 1
            skipSpace()
            if eat(UInt8(ascii: "}")) { return true }
            repeat {
                skipSpace()
                guard i < b.count, b[i] == UInt8(ascii: "\""), skipString() else { return false }
                skipSpace()
                guard eat(UInt8(ascii: ":")), skipValue(depth: depth + 1) else { return false }
                skipSpace()
            } while eat(UInt8(ascii: ","))
            return eat(UInt8(ascii: "}"))
        case UInt8(ascii: "["):
            i += 1
            skipSpace()
            if eat(UInt8(ascii: "]")) { return true }
            repeat {
                guard skipValue(depth: depth + 1) else { return false }
                skipSpace()
            } while eat(UInt8(ascii: ","))
            return eat(UInt8(ascii: "]"))
        case UInt8(ascii: "\""):
            return skipString()
        case UInt8(ascii: "t"): return literal("true")
        case UInt8(ascii: "f"): return literal("false")
        case UInt8(ascii: "n"): return literal("null")
        default:
            return skipNumber()
        }
    }

    mutating func skipString() -> Bool {
        guard eat(UInt8(ascii: "\"")) else { return false }
        while i < b.count {
            let c = b[i]
            if c == UInt8(ascii: "\"") { i += 1; return true }
            if c < 0x20 { return false }
            if c == UInt8(ascii: "\\") {
                guard i + 1 < b.count else { return false }
                switch b[i + 1] {
                case UInt8(ascii: "\""), UInt8(ascii: "\\"), UInt8(ascii: "/"), UInt8(ascii: "b"), UInt8(ascii: "f"), UInt8(ascii: "n"), UInt8(ascii: "r"), UInt8(ascii: "t"):
                    i += 2
                case UInt8(ascii: "u"):
                    guard i + 5 < b.count else { return false }
                    for k in 2...5 {
                        let h = b[i + k] | 0x20
                        guard (h >= 0x30 && h <= 0x39) || (h >= 0x61 && h <= 0x66) else { return false }
                    }
                    i += 6
                default:
                    return false
                }
                continue
            }
            i += 1
        }
        return false
    }

    mutating func skipNumber() -> Bool {
        let start = i
        if i < b.count, b[i] == UInt8(ascii: "-") { i += 1 }
        guard i < b.count, b[i] >= 0x30, b[i] <= 0x39 else { return false }
        if b[i] == 0x30 { i += 1 } else { while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 } }
        if i < b.count, b[i] == UInt8(ascii: ".") {
            i += 1
            let digits = i
            while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
            guard i > digits else { return false }
        }
        if i < b.count, b[i] == UInt8(ascii: "e") || b[i] == UInt8(ascii: "E") {
            i += 1
            if i < b.count, b[i] == UInt8(ascii: "+") || b[i] == UInt8(ascii: "-") { i += 1 }
            let digits = i
            while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
            guard i > digits else { return false }
        }
        return i > start
    }
}
