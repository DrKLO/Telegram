
import Foundation

enum CanvasBatchFastPath {
    // The validator's strict caps (validator.ts: 10 000 ops per batch, 4000 canvas commands per send, 32 768 characters
    // per text value; a UTF-8 byte count bounds the UTF-16 length): a batch past one takes the general path, which
    // applies the policy's caps and, under strict, stops the widget.
    static let maxOps = 10_000
    static let maxCmdsPerOp = 4000
    static let maxStringBytes = 32 * 1024
    static let maxDistinctIds = 16
    static let maxBatchBytes = 4 << 20

    // The profile's canvas calls and properties (`tables.canvas` in packages/core/profile.json). Anything else — and
    // `font`, whose families the validator reduces to the generic ones — sends the batch through the JS validator,
    // which drops what is outside the profile with a `profile` diagnostic.
    static let callMethods = WordSet("""
        save restore scale rotate translate transform setTransform resetTransform
        clearRect fillRect strokeRect beginPath closePath moveTo lineTo bezierCurveTo quadraticCurveTo arc arcTo
        ellipse rect roundRect fill stroke clip fillText strokeText setLineDash drawImage reset
        """)
    static let setProperties = WordSet("""
        fillStyle strokeStyle lineWidth lineCap lineJoin miterLimit lineDashOffset textAlign textBaseline
        direction globalAlpha imageSmoothingEnabled imageSmoothingQuality
        """)

    private static let commandKinds = ["c", "s", "r", "g", "gs", "pn", "pc"]
    static let pathMethods = WordSet("moveTo lineTo bezierCurveTo quadraticCurveTo arc arcTo ellipse rect roundRect closePath addPath")
    static let gradientTypes = WordSet("linear radial")

    struct WordSet {
        private var byLength: [[[UInt8]]] = Array(repeating: [], count: 33)

        init(_ s: String) {
            for w in s.split(whereSeparator: { $0 == " " || $0 == "\n" }) {
                let bytes = Array(w.utf8)
                byLength[bytes.count].append(bytes)
            }
        }

        func contains(_ s: String) -> Bool {
            if s.isEmpty { return false }
            var s = s
            return s.withUTF8 { contains($0[...]) }
        }

        func contains(_ s: Slice<UnsafeBufferPointer<UInt8>>) -> Bool {
            guard s.count < byLength.count else { return false }
            let base = s.base.baseAddress! + s.startIndex
            for w in byLength[s.count] where w.withUnsafeBufferPointer({ memcmp($0.baseAddress!, base, s.count) == 0 }) { return true }
            return false
        }
    }

    static func accept(_ json: String) -> [Int]? {
        json.utf8.withContiguousStorageIfAvailable { accept(bytes: $0) } ?? nil
    }

    static func accept(bytes b: UnsafeBufferPointer<UInt8>) -> [Int]? {
        if b.count > maxBatchBytes { return nil }
        var s = Scanner(b: b)
        var ids: [Int] = []
        guard s.eat(UInt8(ascii: "[")) else { return nil }
        var ops = 0
        repeat {
            ops += 1
            if ops > maxOps { return nil }
            guard let id = s.canvasOp() else { return nil }
            if !ids.contains(id) {
                if ids.count == maxDistinctIds { return nil }
                ids.append(id)
            }
        } while s.eat(UInt8(ascii: ","))
        guard s.eat(UInt8(ascii: "]")), s.i == b.count else { return nil }
        return ids
    }

    struct Scanner {
        let b: UnsafeBufferPointer<UInt8>
        var i = 0

        init(b: UnsafeBufferPointer<UInt8>) { self.b = b }

        mutating func eat(_ c: UInt8) -> Bool {
            if i < b.count, b[i] == c { i += 1; return true }
            return false
        }

        mutating func literal(_ s: StaticString) -> Bool {
            let n = s.utf8CodeUnitCount
            if b.count - i < n { return false }
            let p = s.utf8Start
            for k in 0..<n where b[i + k] != p[k] { return false }
            i += n
            return true
        }

        mutating func canvasOp() -> Int? {
            guard literal("{\"op\":\"canvas\",\"id\":"), let id = nodeId(), literal(",\"cmds\":[") else { return nil }
            var cmds = 0
            repeat {
                cmds += 1
                if cmds > CanvasBatchFastPath.maxCmdsPerOp { return nil }
                guard command() else { return nil }
            } while eat(UInt8(ascii: ","))
            guard literal("]}") else { return nil }
            return id
        }

        mutating func nodeId() -> Int? {
            let start = i
            var v = 0
            while i < b.count, b[i] >= 0x30, b[i] <= 0x39 {
                v = v * 10 + Int(b[i] - 0x30)
                i += 1
                if i - start > 10 { return nil }
            }
            if i == start || (b[start] == 0x30 && i - start > 1) { return nil }
            return v > 1 && v < 1 << 31 ? v : nil
        }

        mutating func command() -> Bool {
            guard eat(UInt8(ascii: "[")), let kindName = plainString(), let kind = CanvasBatchFastPath.commandKinds.first(where: { $0.utf8.elementsEqual(kindName) }) else { return false }
            switch kind {
            case "c":
                guard eat(UInt8(ascii: ",")), let name = plainString(), CanvasBatchFastPath.callMethods.contains(name) else { return false }
                return arguments(max: 14)
            case "s":
                guard eat(UInt8(ascii: ",")), let name = plainString(), CanvasBatchFastPath.setProperties.contains(name), eat(UInt8(ascii: ",")) else { return false }
                guard scalar() || objectReference() else { return false }
            case "r":
                guard eat(UInt8(ascii: ",")), let w = number(), w >= 0, w <= 16384, eat(UInt8(ascii: ",")), let h = number(), h >= 0, h <= 16384 else { return false }
            case "g":
                guard eat(UInt8(ascii: ",")), number() != nil, eat(UInt8(ascii: ",")), let type = plainString(), CanvasBatchFastPath.gradientTypes.contains(type) else { return false }
                return arguments(max: 13)
            case "gs":
                guard eat(UInt8(ascii: ",")), number() != nil, eat(UInt8(ascii: ",")), number() != nil, eat(UInt8(ascii: ",")), let color = plainString(), color.count < 256 else { return false }
            case "pn":
                guard eat(UInt8(ascii: ",")), number() != nil, eat(UInt8(ascii: ",")), literal("null") || plainString() != nil else { return false }
            case "pc":
                guard eat(UInt8(ascii: ",")), number() != nil, eat(UInt8(ascii: ",")), let name = plainString(), CanvasBatchFastPath.pathMethods.contains(name) else { return false }
                return arguments(max: 13)
            default:
                return false
            }
            return eat(UInt8(ascii: "]"))
        }

        mutating func arguments(max: Int) -> Bool {
            var args = 0
            while eat(UInt8(ascii: ",")) {
                args += 1
                if args > max || !(scalar() || numberArray() || objectReference()) { return false }
            }
            return eat(UInt8(ascii: "]"))
        }

        mutating func number() -> Double? {
            let start = i
            guard i < b.count, b[i] == UInt8(ascii: "-") || (b[i] >= 0x30 && b[i] <= 0x39), let end = JSNumberText.canonicalEnd(b, from: i) else { return nil }
            i = end
            return JSNumberText.value(b, start: start, end: end)
        }

        mutating func numberArray() -> Bool {
            guard eat(UInt8(ascii: "[")) else { return false }
            if eat(UInt8(ascii: "]")) { return true }
            var count = 0
            repeat {
                count += 1
                if count > 4096 { return false }
                guard i < b.count, b[i] == UInt8(ascii: "-") || (b[i] >= 0x30 && b[i] <= 0x39), let end = JSNumberText.canonicalEnd(b, from: i) else { return false }
                i = end
            } while eat(UInt8(ascii: ","))
            return eat(UInt8(ascii: "]"))
        }

        mutating func objectReference() -> Bool {
            guard literal("{\"g\":") || literal("{\"p\":") else { return false }
            let start = i
            if i < b.count, b[i] == UInt8(ascii: "-") { i += 1 }
            let digits = i
            while i < b.count, b[i] >= 0x30, b[i] <= 0x39 { i += 1 }
            guard i > digits, i - digits <= 15, b[digits] != 0x30 || i - digits == 1, !(b[digits] == 0x30 && digits > start) else { return false }
            return eat(UInt8(ascii: "}"))
        }

        mutating func plainString() -> Slice<UnsafeBufferPointer<UInt8>>? {
            guard eat(UInt8(ascii: "\"")) else { return nil }
            let start = i
            while i < b.count {
                let c = b[i]
                if c == UInt8(ascii: "\"") {
                    let s = b[start..<i]
                    i += 1
                    return s
                }
                if c == UInt8(ascii: "\\") || c < 0x20 { return nil }
                i += 1
                if i - start > CanvasBatchFastPath.maxStringBytes { return nil }
            }
            return nil
        }

        mutating func scalar() -> Bool {
            guard i < b.count else { return false }
            switch b[i] {
            case UInt8(ascii: "\""): return plainString() != nil
            case UInt8(ascii: "n"): return literal("null")
            case UInt8(ascii: "t"): return literal("true")
            case UInt8(ascii: "f"): return literal("false")
            default:
                guard let end = JSNumberText.canonicalEnd(b, from: i) else { return false }
                i = end
                return true
            }
        }
    }
}

enum JSNumberText {
    static func canonicalEnd(_ b: UnsafeBufferPointer<UInt8>, from start: Int) -> Int? {
        var i = start
        let n = b.count
        var negative = false
        if i < n, b[i] == UInt8(ascii: "-") { negative = true; i += 1 }
        let intStart = i
        while i < n, isDigit(b[i]) { i += 1 }
        let intEnd = i
        if intEnd == intStart { return nil }
        if b[intStart] == 0x30, intEnd - intStart > 1 { return nil }
        var fracStart = i, fracEnd = i
        if i < n, b[i] == UInt8(ascii: ".") {
            i += 1
            fracStart = i
            while i < n, isDigit(b[i]) { i += 1 }
            fracEnd = i
            if fracEnd == fracStart || b[fracEnd - 1] == 0x30 { return nil }
        }
        var hasExponent = false
        if i < n, b[i] == UInt8(ascii: "e") || b[i] == UInt8(ascii: "E") {
            hasExponent = true
            i += 1
            if i < n, b[i] == UInt8(ascii: "+") || b[i] == UInt8(ascii: "-") { i += 1 }
            let expStart = i
            while i < n, isDigit(b[i]) { i += 1 }
            if i == expStart { return nil }
        }
        if i < n, b[i] == UInt8(ascii: ".") || b[i] == UInt8(ascii: "e") || b[i] == UInt8(ascii: "E") || b[i] == UInt8(ascii: "+") || b[i] == UInt8(ascii: "-") || isDigit(b[i]) { return nil }
        if !hasExponent {
            if fastCanonical(b, negative: negative, intStart: intStart, intEnd: intEnd, fracStart: fracStart, fracEnd: fracEnd) { return i }
            if let plain = plainCanonical(b, start: start, end: i, intStart: intStart, intEnd: intEnd, fracStart: fracStart, fracEnd: fracEnd) { return plain ? i : nil }
        }
        return slowCanonical(b, start: start, end: i) ? i : nil
    }

    static func value(_ b: UnsafeBufferPointer<UInt8>, start: Int, end: Int) -> Double? {
        let length = end - start
        if length > 40 { return nil }
        var token = [CChar](repeating: 0, count: length + 1)
        for k in 0..<length { token[k] = CChar(bitPattern: b[start + k]) }
        let value = strtod(token, nil)
        return value.isFinite ? value : nil
    }

    @inline(__always) private static func isDigit(_ c: UInt8) -> Bool { c >= 0x30 && c <= 0x39 }

    private static func fastCanonical(_ b: UnsafeBufferPointer<UInt8>, negative: Bool, intStart: Int, intEnd: Int, fracStart: Int, fracEnd: Int) -> Bool {
        let intDigits = intEnd - intStart
        let hasFraction = fracEnd > fracStart
        if intDigits > 21 { return false }
        if b[intStart] == 0x30 {
            if !hasFraction { return !negative }
            var zeros = 0
            var k = fracStart
            while k < fracEnd, b[k] == 0x30 { zeros += 1; k += 1 }
            if zeros > 5 { return false }
            return fracEnd - k <= 15
        }
        if hasFraction { return intDigits + (fracEnd - fracStart) <= 15 }
        var last = intEnd
        while last > intStart + 1, b[last - 1] == 0x30 { last -= 1 }
        return last - intStart <= 15
    }

    private static func parse(_ b: UnsafeBufferPointer<UInt8>, start: Int, end: Int) -> Double {
        withUnsafeTemporaryAllocation(of: CChar.self, capacity: 48) { buffer in
            let length = min(end - start, 47)
            for k in 0..<length { buffer[k] = CChar(bitPattern: b[start + k]) }
            buffer[length] = 0
            return strtod(buffer.baseAddress!, nil)
        }
    }

    private static func plainCanonical(_ b: UnsafeBufferPointer<UInt8>, start: Int, end: Int, intStart: Int, intEnd: Int, fracStart: Int, fracEnd: Int) -> Bool? {
        let intDigits = intEnd - intStart
        if intDigits > 21 || end - start > 40 { return nil }
        var sigStart: Int
        var point: Int
        if b[intStart] == 0x30 {
            guard fracEnd > fracStart else { return nil }
            sigStart = fracStart
            while sigStart < fracEnd, b[sigStart] == 0x30 { sigStart += 1 }
            point = fracStart - sigStart
            if point < -5 { return nil }
        } else {
            sigStart = intStart
            point = intDigits
        }
        var sigEnd = fracEnd > fracStart ? fracEnd : intEnd
        if fracEnd == fracStart { while sigEnd > sigStart + 1, b[sigEnd - 1] == 0x30 { sigEnd -= 1 } }
        let tokenDigits = sigEnd - sigStart - (sigStart < intEnd && sigEnd > intEnd ? 1 : 0)
        if tokenDigits > 17 { return false }
        let value = parse(b, start: start, end: end)
        guard value.isFinite, value != 0 else { return nil }
        var text = abs(value).description
        return text.withUTF8 { d -> Bool? in
            var mantissaEnd = d.count
            var exponent = 0
            if let e = d.firstIndex(of: UInt8(ascii: "e")) {
                mantissaEnd = e
                var k = e + 1
                var negativeExponent = false
                if k < d.count, d[k] == UInt8(ascii: "-") { negativeExponent = true; k += 1 } else if k < d.count, d[k] == UInt8(ascii: "+") { k += 1 }
                while k < d.count { exponent = exponent * 10 + Int(d[k] - 0x30); k += 1 }
                if negativeExponent { exponent = -exponent }
            }
            var first = 0
            var seenDot = false
            var digitsBeforeDot = 0
            while first < mantissaEnd, d[first] == 0x30 || d[first] == UInt8(ascii: ".") {
                if d[first] == UInt8(ascii: ".") { seenDot = true } else if seenDot { digitsBeforeDot -= 1 }
                first += 1
            }
            var k = first
            var count = 0
            var scan = first
            while scan < mantissaEnd {
                if d[scan] == UInt8(ascii: ".") { seenDot = true } else if !seenDot { digitsBeforeDot += 1 }
                scan += 1
            }
            var last = mantissaEnd
            while last > first, d[last - 1] == 0x30 || d[last - 1] == UInt8(ascii: ".") { last -= 1 }
            let shortestPoint = digitsBeforeDot + exponent
            if shortestPoint != point { return false }
            var t = sigStart
            while k < last {
                if d[k] == UInt8(ascii: ".") { k += 1; continue }
                if t == intEnd, t < fracStart { t = fracStart }
                guard t < sigEnd, b[t] == d[k] else { return false }
                t += 1
                k += 1
                count += 1
            }
            if t == intEnd, t < fracStart { t = fracStart }
            return t == sigEnd && count == tokenDigits
        }
    }

    private static func slowCanonical(_ b: UnsafeBufferPointer<UInt8>, start: Int, end: Int) -> Bool {
        let length = end - start
        if length > 40 { return false }
        var token = [CChar](repeating: 0, count: length + 1)
        for k in 0..<length { token[k] = CChar(bitPattern: b[start + k]) }
        let value = strtod(token, nil)
        guard value.isFinite else { return false }
        let printed = bytes(value)
        guard printed.count == length else { return false }
        for k in 0..<length where b[start + k] != printed[k] { return false }
        return true
    }

    static func string(_ value: Double) -> String {
        String(decoding: bytes(value), as: UTF8.self)
    }

    static func bytes(_ value: Double) -> [UInt8] {
        if value == 0 { return [0x30] }
        if value < 0 { return [UInt8(ascii: "-")] + bytes(-value) }
        let (digits, n) = shortestDigits(value)
        let k = digits.count
        let zero = UInt8(ascii: "0")
        if k <= n && n <= 21 { return digits + [UInt8](repeating: zero, count: n - k) }
        if 0 < n && n <= 21 { return Array(digits[..<n]) + [UInt8(ascii: ".")] + Array(digits[n...]) }
        if -6 < n && n <= 0 { return [zero, UInt8(ascii: ".")] + [UInt8](repeating: zero, count: -n) + digits }
        let e = n - 1
        let exponent = Array((e < 0 ? "e-\(-e)" : "e+\(e)").utf8)
        if k == 1 { return digits + exponent }
        return [digits[0], UInt8(ascii: ".")] + Array(digits[1...]) + exponent
    }

    private static func shortestDigits(_ value: Double) -> ([UInt8], Int) {
        let text = Array(value.description.utf8)
        var mantissaEnd = text.count
        var exponent = 0
        if let e = text.firstIndex(where: { $0 == UInt8(ascii: "e") || $0 == UInt8(ascii: "E") }) {
            mantissaEnd = e
            exponent = Int(String(decoding: text[(e + 1)...], as: UTF8.self)) ?? 0
        }
        var digits: [UInt8] = []
        var point = 0
        var seenDot = false
        for c in text[..<mantissaEnd] {
            if c == UInt8(ascii: ".") { seenDot = true; continue }
            digits.append(c)
            if !seenDot { point += 1 }
        }
        point += exponent
        var lead = 0
        while lead < digits.count, digits[lead] == 0x30 { lead += 1 }
        point -= lead
        var trail = digits.count
        while trail > lead, digits[trail - 1] == 0x30 { trail -= 1 }
        return (Array(digits[lead..<trail]), point)
    }
}
