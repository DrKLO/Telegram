import Foundation

indirect enum JSONValue {
    case null
    case bool(Bool)
    case number(Double)
    case string(String)
    case array([JSONValue])
    case object(JSONObject)

    var string: String? { if case .string(let s) = self { return s } else { return nil } }
    var number: Double? { if case .number(let n) = self { return n } else { return nil } }
    var object: JSONObject? { if case .object(let o) = self { return o } else { return nil } }
    var array: [JSONValue]? { if case .array(let a) = self { return a } else { return nil } }

    var json: String {
        var out: [UInt8] = []
        JSONText.write(self, to: &out)
        return String(decoding: out, as: UTF8.self)
    }
}

struct JSONObject {
    private(set) var keys: [String] = []
    private(set) var values: [JSONValue] = []
    private var index: [String: Int]?

    private func find(_ key: String) -> Int? {
        if let index { return index[key] }
        return keys.firstIndex(of: key)
    }

    subscript(key: String) -> JSONValue? {
        guard let i = find(key) else { return nil }
        return values[i]
    }

    var count: Int { keys.count }

    mutating func set(_ key: String, _ value: JSONValue) {
        if let i = find(key) {
            values[i] = value
            return
        }
        keys.append(key)
        values.append(value)
        if index != nil {
            index![key] = keys.count - 1
        } else if keys.count > 16 {
            var map: [String: Int] = [:]
            for (i, k) in keys.enumerated() { map[k] = i }
            index = map
        }
    }

    var entries: [(String, JSONValue)] {
        let order = JSONText.propertyOrder(keys)
        return order.map { (keys[$0], values[$0]) }
    }
}

enum JSONText {
    static let maxDepth = 512

    static func parse(_ text: String) -> JSONValue? {
        var text = text
        return text.withUTF8 { parse(bytes: $0) }
    }

    static func parse(bytes b: UnsafeBufferPointer<UInt8>) -> JSONValue? {
        var p = Parser(b: b)
        p.space()
        guard let v = p.value(0) else { return nil }
        p.space()
        return p.i == b.count ? v : nil
    }

    private struct Parser {
        let b: UnsafeBufferPointer<UInt8>
        var i = 0

        mutating func space() {
            while i < b.count, b[i] == 0x20 || b[i] == 0x09 || b[i] == 0x0a || b[i] == 0x0d { i += 1 }
        }

        mutating func literal(_ s: StaticString) -> Bool {
            let n = s.utf8CodeUnitCount
            guard b.count - i >= n, memcmp(b.baseAddress! + i, s.utf8Start, n) == 0 else { return false }
            i += n
            return true
        }

        mutating func value(_ depth: Int) -> JSONValue? {
            guard depth < JSONText.maxDepth, i < b.count else { return nil }
            switch b[i] {
            case 0x7b:
                i += 1
                var object = JSONObject()
                space()
                if i < b.count, b[i] == 0x7d { i += 1; return .object(object) }
                while true {
                    space()
                    guard i < b.count, b[i] == 0x22, let key = string() else { return nil }
                    space()
                    guard i < b.count, b[i] == 0x3a else { return nil }
                    i += 1
                    space()
                    guard let v = value(depth + 1) else { return nil }
                    object.set(key, v)
                    space()
                    guard i < b.count else { return nil }
                    if b[i] == 0x2c { i += 1; continue }
                    if b[i] == 0x7d { i += 1; return .object(object) }
                    return nil
                }
            case 0x5b:
                i += 1
                var list: [JSONValue] = []
                space()
                if i < b.count, b[i] == 0x5d { i += 1; return .array(list) }
                while true {
                    space()
                    guard let v = value(depth + 1) else { return nil }
                    list.append(v)
                    space()
                    guard i < b.count else { return nil }
                    if b[i] == 0x2c { i += 1; continue }
                    if b[i] == 0x5d { i += 1; return .array(list) }
                    return nil
                }
            case 0x22:
                return string().map(JSONValue.string)
            case 0x74:
                return literal("true") ? .bool(true) : nil
            case 0x66:
                return literal("false") ? .bool(false) : nil
            case 0x6e:
                return literal("null") ? .null : nil
            default:
                return number()
            }
        }

        mutating func number() -> JSONValue? {
            let start = i
            if i < b.count, b[i] == 0x2d { i += 1 }
            guard i < b.count, b[i] >= 0x30, b[i] <= 0x39 else { return nil }
            if b[i] == 0x30 { i += 1 } else { while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 } }
            if i < b.count, b[i] == 0x2e {
                i += 1
                let digits = i
                while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
                guard i > digits else { return nil }
            }
            if i < b.count, b[i] == 0x65 || b[i] == 0x45 {
                i += 1
                if i < b.count, b[i] == 0x2b || b[i] == 0x2d { i += 1 }
                let digits = i
                while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
                guard i > digits else { return nil }
            }
            let length = i - start
            var token = [CChar](repeating: 0, count: length + 1)
            for k in 0..<length { token[k] = CChar(bitPattern: b[start + k]) }
            return .number(strtod(token, nil))
        }

        mutating func hex4() -> UInt16? {
            guard b.count - i >= 4 else { return nil }
            var v: UInt16 = 0
            for k in 0..<4 {
                let c = b[i + k]
                let d: UInt16
                switch c {
                case 0x30...0x39: d = UInt16(c - 0x30)
                case 0x41...0x46: d = UInt16(c - 0x41 + 10)
                case 0x61...0x66: d = UInt16(c - 0x61 + 10)
                default: return nil
                }
                v = v << 4 | d
            }
            i += 4
            return v
        }

        mutating func string() -> String? {
            i += 1
            let start = i
            while i < b.count, b[i] != 0x22, b[i] != 0x5c, b[i] >= 0x20 { i += 1 }
            guard i < b.count else { return nil }
            if b[i] == 0x22 {
                let s = String(decoding: UnsafeBufferPointer(rebasing: b[start..<i]), as: UTF8.self)
                i += 1
                return s
            }
            var out = Array(b[start..<i])
            while i < b.count {
                let c = b[i]
                if c == 0x22 { i += 1; return String(decoding: out, as: UTF8.self) }
                if c < 0x20 { return nil }
                if c != 0x5c { out.append(c); i += 1; continue }
                guard i + 1 < b.count else { return nil }
                let e = b[i + 1]
                i += 2
                switch e {
                case 0x22: out.append(0x22)
                case 0x5c: out.append(0x5c)
                case 0x2f: out.append(0x2f)
                case 0x62: out.append(0x08)
                case 0x66: out.append(0x0c)
                case 0x6e: out.append(0x0a)
                case 0x72: out.append(0x0d)
                case 0x74: out.append(0x09)
                case 0x75:
                    guard let u = hex4() else { return nil }
                    var scalar: UInt32 = 0xfffd
                    if u >= 0xd800 && u < 0xdc00 {
                        let save = i
                        if b.count - i >= 6, b[i] == 0x5c, b[i + 1] == 0x75 {
                            i += 2
                            if let low = hex4(), low >= 0xdc00, low < 0xe000 {
                                scalar = 0x10000 + (UInt32(u - 0xd800) << 10) + UInt32(low - 0xdc00)
                            } else {
                                i = save
                            }
                        }
                    } else if u < 0xdc00 || u >= 0xe000 {
                        scalar = UInt32(u)
                    }
                    appendScalar(scalar, to: &out)
                default:
                    return nil
                }
            }
            return nil
        }
    }

    static func appendScalar(_ v: UInt32, to out: inout [UInt8]) {
        if v < 0x80 {
            out.append(UInt8(v))
        } else if v < 0x800 {
            out.append(UInt8(0xc0 | v >> 6)); out.append(UInt8(0x80 | v & 0x3f))
        } else if v < 0x10000 {
            out.append(UInt8(0xe0 | v >> 12)); out.append(UInt8(0x80 | v >> 6 & 0x3f)); out.append(UInt8(0x80 | v & 0x3f))
        } else {
            out.append(UInt8(0xf0 | v >> 18)); out.append(UInt8(0x80 | v >> 12 & 0x3f)); out.append(UInt8(0x80 | v >> 6 & 0x3f)); out.append(UInt8(0x80 | v & 0x3f))
        }
    }

    static func arrayIndex(_ key: String) -> UInt32? {
        let u = key.utf8
        guard let first = u.first, u.count <= 10, first >= 0x30, first <= 0x39, first != 0x30 || u.count == 1 else { return nil }
        var v: UInt64 = 0
        for c in u {
            guard c >= 0x30, c <= 0x39 else { return nil }
            v = v * 10 + UInt64(c - 0x30)
        }
        return v <= 4_294_967_294 ? UInt32(v) : nil
    }

    static func propertyOrder(_ keys: [String]) -> [Int] {
        var indices: [(UInt32, Int)] = []
        var named: [Int] = []
        for (i, key) in keys.enumerated() {
            if let index = arrayIndex(key) { indices.append((index, i)) } else { named.append(i) }
        }
        if indices.isEmpty { return named }
        return indices.sorted { $0.0 < $1.0 }.map { $0.1 } + named
    }

    static func write(_ v: JSONValue, to out: inout [UInt8]) {
        switch v {
        case .null: out += Array("null".utf8)
        case .bool(let b): out += Array((b ? "true" : "false").utf8)
        case .number(let n): out += n.isFinite ? JSNumberText.bytes(n) : Array("null".utf8)
        case .string(let s): writeString(s, to: &out)
        case .array(let list):
            out.append(0x5b)
            for (k, item) in list.enumerated() {
                if k > 0 { out.append(0x2c) }
                write(item, to: &out)
            }
            out.append(0x5d)
        case .object(let o):
            out.append(0x7b)
            for (k, (key, item)) in o.entries.enumerated() {
                if k > 0 { out.append(0x2c) }
                writeString(key, to: &out)
                out.append(0x3a)
                write(item, to: &out)
            }
            out.append(0x7d)
        }
    }

    static func writeString(_ s: String, to out: inout [UInt8]) {
        out.append(0x22)
        var s = s
        s.withUTF8 { b in
            var plain = 0
            for k in 0..<b.count {
                let c = b[k]
                if c >= 0x20, c != 0x22, c != 0x5c { continue }
                out.append(contentsOf: UnsafeBufferPointer(rebasing: b[plain..<k]))
                plain = k + 1
                switch c {
                case 0x22: out += [0x5c, 0x22]
                case 0x5c: out += [0x5c, 0x5c]
                case 0x08: out += [0x5c, 0x62]
                case 0x0c: out += [0x5c, 0x66]
                case 0x0a: out += [0x5c, 0x6e]
                case 0x0d: out += [0x5c, 0x72]
                case 0x09: out += [0x5c, 0x74]
                default:
                    let hex = Array("0123456789abcdef".utf8)
                    out += [0x5c, 0x75, 0x30, 0x30, hex[Int(c >> 4)], hex[Int(c & 15)]]
                }
            }
            out.append(contentsOf: UnsafeBufferPointer(rebasing: b[plain..<b.count]))
        }
        out.append(0x22)
    }

    static func safe(_ text: [UInt8]) -> String {
        let s = String(decoding: text, as: UTF8.self)
        return truncated(s, 160)
    }

    static func truncated(_ s: String, _ limit: Int) -> String {
        if s.utf8.count <= limit { return s }
        let units = s.utf16
        if units.count <= limit { return s }
        // Never half a surrogate pair (validator.ts `clip`).
        let last = units[units.index(units.startIndex, offsetBy: limit - 1)]
        return String(decoding: units.prefix(UTF16.isLeadSurrogate(last) ? limit - 1 : limit), as: UTF16.self) + "\u{2026}"
    }
}
