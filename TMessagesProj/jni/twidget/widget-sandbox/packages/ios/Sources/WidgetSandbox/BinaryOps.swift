import Foundation
import WidgetSandboxProtocol

public struct BinaryOpsPayload {
    public var words: [Int32]
    public var strings: [UInt8]

    public init(words: [Int32], strings: [UInt8]) {
        self.words = words
        self.strings = strings
    }
}

struct BinaryString {
    let s: String
    let units: Int
}

enum BinaryOp {
    case element(id: Int, tag: BinaryString, svg: Bool, attributes: [(name: BinaryString, value: BinaryString)])
    case text(id: Int, data: BinaryString)
    case comment(id: Int, data: BinaryString)
    case data(id: Int, data: BinaryString)
    case attribute(id: Int, name: BinaryString, value: BinaryString)
    case removeAttribute(id: Int, name: BinaryString)
    case insert(parent: Int, id: Int, before: Int)
    case remove(id: Int)
    case style(id: Int, name: BinaryString, value: BinaryString, important: Bool)
    case canvas(id: Int, key: Int)
    case json(JSONValue)
}

final class BinaryInternTable {
    static let maxEntries = 4096
    static let maxEntryUnits = 256
    static let maxBytes = 1 << 20

    private(set) var strings: [BinaryString] = []
    private(set) var bytes = 0

    func reset() {
        strings.removeAll(keepingCapacity: true)
        bytes = 0
    }

    func commit(reset: Bool, _ added: [BinaryString]) {
        if reset { self.reset() }
        strings += added
        bytes += added.reduce(0) { $0 + $1.s.utf8.count }
    }
}

enum BinaryOps {
    static let magic: Int32 = 0x5457_4F31
    static let maxWords = 8 << 20
    static let maxStringBytes = 64 << 20
    static let maxOps = 200_000

    enum Code: Int32 {
        case define = 0
        case element = 1
        case text = 2
        case comment = 3
        case data = 4
        case attribute = 5
        case removeAttribute = 6
        case insert = 7
        case remove = 8
        case style = 9
        case canvas = 10
        case json = 11
        case reset = 12
    }

    enum Failure: Error, Equatable {
        case header
        case truncated
        case opcode(Int32)
        case string
        case utf8
        case intern
        case count
        case flags
        case json
        /// A canvas payload key referenced twice: each payload stands for one canvas op, and a
        /// repeated key would make the validator and the splice redo the whole payload per reference.
        case canvasKey
    }

    struct Decoded {
        var ops: [BinaryOp]
        var tooMany: Bool
        /// Ops in the batch (`ops` is empty past `maxOps`): the validator's per-batch cap counts them.
        var count = 0
    }

    static func decode(_ payload: BinaryOpsPayload, table: BinaryInternTable, icons: IconExpansion? = nil) -> Result<Decoded, Failure> {
        payload.words.withUnsafeBufferPointer { w in
            payload.strings.withUnsafeBufferPointer { b in
                decode(words: w, bytes: b, table: table, icons: icons)
            }
        }
    }

    private struct Strings {
        let b: UnsafeBufferPointer<UInt8>
        let ascii: Bool
        var at = 0

        mutating func take(_ units: Int) -> (Range<Int>, Int)? {
            if ascii {
                guard units <= b.count - at else { return nil }
                defer { at += units }
                return (at..<(at + units), units)
            }
            let start = at
            var left = units
            while left > 0 {
                guard at < b.count else { return nil }
                let c = b[at]
                if c < 0x80 { at += 1; left -= 1; continue }
                let n: Int
                var v: UInt32
                if c >= 0xc2 && c <= 0xdf { n = 2; v = UInt32(c & 0x1f) }
                else if c >= 0xe0 && c <= 0xef { n = 3; v = UInt32(c & 0x0f) }
                else if c >= 0xf0 && c <= 0xf4 { n = 4; v = UInt32(c & 0x07) }
                else { return nil }
                guard b.count - at >= n else { return nil }
                for k in 1..<n {
                    let d = b[at + k]
                    guard d & 0xc0 == 0x80 else { return nil }
                    v = v << 6 | UInt32(d & 0x3f)
                }
                if n == 3 && (v < 0x800 || (v >= 0xd800 && v < 0xe000)) { return nil }
                if n == 4 && (v < 0x10000 || v > 0x10ffff) { return nil }
                let cost = n == 4 ? 2 : 1
                guard cost <= left else { return nil }
                left -= cost
                at += n
            }
            return (start..<at, units)
        }
    }

    static func decode(words w: UnsafeBufferPointer<Int32>, bytes b: UnsafeBufferPointer<UInt8>, table: BinaryInternTable, icons: IconExpansion? = nil) -> Result<Decoded, Failure> {
        guard w.count >= 2, w.count <= maxWords, b.count <= maxStringBytes, w[0] == magic, w[1] >= 0 else { return .failure(.header) }
        var ascii = true
        if let base = b.baseAddress {
            var k = 0
            while k + 8 <= b.count {
                if UnsafeRawPointer(base + k).loadUnaligned(as: UInt64.self) & 0x8080_8080_8080_8080 != 0 { ascii = false; break }
                k += 8
            }
            if ascii { while k < b.count { if b[k] >= 0x80 { ascii = false; break }; k += 1 } }
        }
        var strings = Strings(b: b, ascii: ascii)
        var interned = table.strings
        var added: [BinaryString] = []
        var reset = false
        var ops: [BinaryOp] = []
        ops.reserveCapacity(min(Int(w[1]), maxOps))
        var canvasKeys = Set<Int>()
        var i = 2
        var count = 0
        let iconExpansion = b.count >= 5 && IconExpansion.mayContainIcon(bytes: b) ? icons : nil

        func word() throws -> Int32 {
            guard i < w.count else { throw Failure.truncated }
            defer { i += 1 }
            return w[i]
        }
        func inline(_ units: Int32) throws -> BinaryString {
            guard units >= 0, let (range, n) = strings.take(Int(units)) else { throw Failure.string }
            return BinaryString(s: String(decoding: UnsafeBufferPointer(rebasing: b[range]), as: UTF8.self), units: n)
        }
        func string(_ key: String?) throws -> BinaryString {
            let v = try word()
            let s: BinaryString
            if v >= 0 {
                s = try inline(v)
            } else {
                let k = -(Int(v) + 1)
                guard k < interned.count else { throw Failure.intern }
                s = interned[k]
            }
            if let iconExpansion, let expanded = iconExpansion.expandField(s.s, key: key) {
                return BinaryString(s: expanded, units: expanded.utf16.count)
            }
            return s
        }
        func int() throws -> Int { Int(try word()) }

        do {
            while i < w.count {
                let code = w[i]
                i += 1
                switch Code(rawValue: code) {
                case .define:
                    let k = try word()
                    let units = try word()
                    guard units >= 0, units <= BinaryInternTable.maxEntryUnits, Int(k) == interned.count, interned.count < BinaryInternTable.maxEntries else { throw Failure.intern }
                    let s = try inline(units)
                    let total = (reset ? 0 : table.bytes) + added.reduce(0) { $0 + $1.s.utf8.count } + s.s.utf8.count
                    guard total <= BinaryInternTable.maxBytes else { throw Failure.intern }
                    interned.append(s)
                    added.append(s)
                    continue
                case .reset:
                    guard i == 3 else { throw Failure.intern }
                    reset = true
                    interned.removeAll()
                    continue
                case .element:
                    let id = try int()
                    let tag = try string("tag")
                    let flags = try word()
                    guard flags & ~1 == 0 else { throw Failure.flags }
                    let n = try word()
                    guard n >= 0, Int(n) <= (w.count - i) / 2 else { throw Failure.count }
                    var attributes: [(name: BinaryString, value: BinaryString)] = []
                    attributes.reserveCapacity(Int(n))
                    for _ in 0..<Int(n) {
                        let name = try string(nil)
                        let value = try string(name.s)
                        attributes.append((name, value))
                    }
                    ops.append(.element(id: id, tag: tag, svg: flags == 1, attributes: attributes))
                case .text:
                    let id = try int()
                    ops.append(.text(id: id, data: try string("data")))
                case .comment:
                    let id = try int()
                    ops.append(.comment(id: id, data: try string("data")))
                case .data:
                    let id = try int()
                    ops.append(.data(id: id, data: try string("data")))
                case .attribute:
                    let id = try int()
                    let name = try string("name")
                    ops.append(.attribute(id: id, name: name, value: try string("value")))
                case .removeAttribute:
                    let id = try int()
                    ops.append(.removeAttribute(id: id, name: try string("name")))
                case .insert:
                    let parent = try int()
                    let id = try int()
                    ops.append(.insert(parent: parent, id: id, before: try int()))
                case .remove:
                    ops.append(.remove(id: try int()))
                case .style:
                    let id = try int()
                    let name = try string("name")
                    let value = try string("value")
                    let important = try word()
                    guard important == 0 || important == 1 else { throw Failure.flags }
                    ops.append(.style(id: id, name: name, value: value, important: important == 1))
                case .canvas:
                    let id = try int()
                    let key = try int()
                    guard canvasKeys.insert(key).inserted else { throw Failure.canvasKey }
                    ops.append(.canvas(id: id, key: key))
                case .json:
                    let v = try word()
                    let text = try inline(v).s
                    guard let value = JSONText.parse(iconExpansion.map { $0.expand(text) } ?? text) else { throw Failure.json }
                    ops.append(.json(value))
                case nil:
                    throw Failure.opcode(code)
                }
                count += 1
                if count > maxOps { ops.removeAll() }
            }
        } catch let failure as Failure {
            return .failure(failure)
        } catch {
            return .failure(.truncated)
        }
        guard count == Int(w[1]) else { return .failure(.count) }
        guard strings.at == b.count else { return .failure(.string) }
        table.commit(reset: reset, added)
        return .success(Decoded(ops: count > maxOps ? [] : ops, tooMany: count > maxOps, count: count))
    }

    /// `placeholders`: canvas records stay `{"op":"canvas","id":N,"bin":K}` for `engine(_:ops:canvas:)` instead of being spliced.
    static func json(_ op: BinaryOp, canvas: [Int: WidgetCanvasBatch], placeholders: Bool = false) -> [UInt8] {
        var out: [UInt8] = []
        func key(_ k: StaticString) {
            out.append(contentsOf: UnsafeBufferPointer(start: k.utf8Start, count: k.utf8CodeUnitCount))
        }
        func str(_ s: BinaryString) { JSONText.writeString(s.s, to: &out) }
        func num(_ n: Int) { out += Array(String(n).utf8) }
        switch op {
        case .element(let id, let tag, let svg, let attributes):
            key("{\"op\":\"el\",\"id\":"); num(id); key(",\"tag\":"); str(tag)
            if svg { key(",\"ns\":\"svg\"") }
            if !attributes.isEmpty {
                key(",\"attrs\":{")
                for (k, (name, value)) in normalizedAttributes(attributes).enumerated() {
                    if k > 0 { out.append(0x2c) }
                    str(name); out.append(0x3a); str(value)
                }
                out.append(0x7d)
            }
            out.append(0x7d)
        case .text(let id, let data), .comment(let id, let data), .data(let id, let data):
            if case .text = op { key("{\"op\":\"text\",\"id\":") } else if case .comment = op { key("{\"op\":\"comment\",\"id\":") } else { key("{\"op\":\"data\",\"id\":") }
            num(id); key(",\"data\":"); str(data); out.append(0x7d)
        case .attribute(let id, let name, let value):
            key("{\"op\":\"attr\",\"id\":"); num(id); key(",\"name\":"); str(name); key(",\"value\":"); str(value); out.append(0x7d)
        case .removeAttribute(let id, let name):
            key("{\"op\":\"rattr\",\"id\":"); num(id); key(",\"name\":"); str(name); out.append(0x7d)
        case .insert(let parent, let id, let before):
            key("{\"op\":\"ins\",\"parent\":"); num(parent); key(",\"id\":"); num(id); key(",\"before\":")
            if before == 0 { key("null") } else { num(before) }
            out.append(0x7d)
        case .remove(let id):
            key("{\"op\":\"rm\",\"id\":"); num(id); out.append(0x7d)
        case .style(let id, let name, let value, let important):
            key("{\"op\":\"style\",\"id\":"); num(id); key(",\"name\":"); str(name); key(",\"value\":"); str(value)
            if important { key(",\"important\":true") }
            out.append(0x7d)
        case .canvas(let id, let k):
            key("{\"op\":\"canvas\",\"id\":"); num(id)
            if placeholders {
                key(",\"bin\":"); num(k)
            } else {
                key(",\"cmds\":")
                out += Array((canvas[k].flatMap(CanvasWords.commandsJSON) ?? "null").utf8)
            }
            out.append(0x7d)
        case .json(let value):
            JSONText.write(value, to: &out)
        }
        return out
    }

    static func json(_ ops: [BinaryOp], canvas: [Int: WidgetCanvasBatch], placeholders: Bool = false) -> String {
        var out: [UInt8] = [0x5b]
        for (k, op) in ops.enumerated() {
            if k > 0 { out.append(0x2c) }
            out += json(op, canvas: canvas, placeholders: placeholders)
        }
        out.append(0x5d)
        return String(decoding: out, as: UTF8.self)
    }

    static func normalizedAttributes(_ attributes: [(name: BinaryString, value: BinaryString)]) -> [(BinaryString, BinaryString)] {
        var needs = false
        var seen = Set<String>()
        for (name, _) in attributes {
            if !seen.insert(name.s).inserted || JSONText.arrayIndex(name.s) != nil { needs = true; break }
        }
        if !needs { return attributes.map { ($0.name, $0.value) } }
        var keys: [String] = []
        var values: [String: (BinaryString, BinaryString)] = [:]
        for (name, value) in attributes {
            if values[name.s] == nil { keys.append(name.s) }
            values[name.s] = (name, value)
        }
        return JSONText.propertyOrder(keys).map { values[keys[$0]]! }
    }
}

struct BinaryOpsEncoder {
    var words: [Int32] = [BinaryOps.magic, 0]
    var strings: [UInt8] = []
    var interned: [String: Int32] = [:]
    var internNext: Int32 = 0
    var ops: Int32 = 0

    mutating func begin(reset: Bool = false) {
        words = [BinaryOps.magic, 0]
        strings = []
        ops = 0
        if reset {
            words.append(BinaryOps.Code.reset.rawValue)
            interned.removeAll()
            internNext = 0
        }
    }

    private mutating func inline(_ s: String) -> Int32 {
        strings += Array(s.utf8)
        return Int32(s.utf16.count)
    }

    mutating func intern(_ s: String) -> Int32? {
        if let k = interned[s] { return -k - 1 }
        guard s.utf16.count <= BinaryInternTable.maxEntryUnits, internNext < BinaryInternTable.maxEntries else { return nil }
        let k = internNext
        internNext += 1
        interned[s] = k
        words += [BinaryOps.Code.define.rawValue, k]
        words.append(inline(s))
        return -k - 1
    }

    private mutating func field(_ s: String, _ ref: Int32?) {
        words.append(ref ?? inline(s))
    }

    private mutating func record(_ code: BinaryOps.Code) {
        words.append(code.rawValue)
        ops += 1
    }

    mutating func element(_ id: Int32, tag: String, svg: Bool, attributes: [(String, String)], intern: Bool = true) {
        let t = intern ? self.intern(tag) : nil
        let names = attributes.map { intern ? self.intern($0.0) : nil }
        record(.element)
        words.append(id)
        field(tag, t)
        words += [svg ? 1 : 0, Int32(attributes.count)]
        for (k, (name, value)) in attributes.enumerated() {
            field(name, names[k])
            words.append(inline(value))
        }
    }

    mutating func text(_ id: Int32, _ data: String, code: BinaryOps.Code = .text) {
        record(code)
        words.append(id)
        words.append(inline(data))
    }

    mutating func attribute(_ id: Int32, _ name: String, _ value: String, internValue: Bool = false) {
        let n = intern(name)
        let v = internValue ? intern(value) : nil
        record(.attribute)
        words.append(id)
        field(name, n)
        field(value, v)
    }

    mutating func removeAttribute(_ id: Int32, _ name: String) {
        let n = intern(name)
        record(.removeAttribute)
        words.append(id)
        field(name, n)
    }

    mutating func insert(parent: Int32, _ id: Int32, before: Int32) {
        record(.insert)
        words += [parent, id, before]
    }

    mutating func remove(_ id: Int32) {
        record(.remove)
        words.append(id)
    }

    mutating func style(_ id: Int32, _ name: String, _ value: String, important: Bool) {
        let n = intern(name)
        record(.style)
        words.append(id)
        field(name, n)
        words.append(inline(value))
        words.append(important ? 1 : 0)
    }

    mutating func canvas(_ id: Int32, key: Int32) {
        record(.canvas)
        words += [id, key]
    }

    mutating func json(_ text: String) {
        record(.json)
        words.append(inline(text))
    }

    mutating func finish() -> BinaryOpsPayload {
        words[1] = ops
        return BinaryOpsPayload(words: words, strings: strings)
    }
}
