import Foundation
import WidgetSandboxProtocol

struct CSSKeyframe {
    let offset: Double
    let declarations: [(name: String, value: String)]
    let timing: CSSTiming?
}

enum CSSKeyframes {
    static func parse(_ css: String, into rules: inout [String: [CSSKeyframe]]) {
        var rest = Substring(css)
        while let at = rest.range(of: "keyframes", options: .caseInsensitive) {
            let before = rest[..<at.lowerBound]
            guard before.hasSuffix("@") || before.hasSuffix("@-webkit-"), let open = rest[at.upperBound...].firstIndex(of: "{") else {
                rest = rest[at.upperBound...]
                continue
            }
            let name = rest[at.upperBound..<open].trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "\"'"))
            guard let close = matchingBrace(rest, open) else {
                return
            }
            let body = rest[rest.index(after: open)..<close]
            rules[name] = frames(body)
            rest = rest[rest.index(after: close)...]
        }
    }

    private static func matchingBrace(_ text: Substring, _ open: Substring.Index) -> Substring.Index? {
        var depth = 0
        var index = open
        while index < text.endIndex {
            let ch = text[index]
            if ch == "{" {
                depth += 1
            } else if ch == "}" {
                depth -= 1
                if depth == 0 {
                    return index
                }
            }
            index = text.index(after: index)
        }
        return nil
    }

    private static func frames(_ body: Substring) -> [CSSKeyframe] {
        var result: [CSSKeyframe] = []
        var rest = body
        while let open = rest.firstIndex(of: "{"), let close = rest[open...].firstIndex(of: "}") {
            let selectors = rest[..<open].split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
            var declarations: [(name: String, value: String)] = []
            var timing: CSSTiming?
            for declaration in WidgetNode.parseDeclarations(String(rest[rest.index(after: open)..<close])) {
                if declaration.name == "animation-timing-function" {
                    timing = CSSTiming.parse(declaration.value)
                } else {
                    declarations.append((declaration.name, declaration.value))
                }
            }
            for selector in selectors {
                let offset: Double?
                switch selector {
                case "from":
                    offset = 0
                case "to":
                    offset = 1
                default:
                    offset = selector.hasSuffix("%") ? Double(selector.dropLast()).map { $0 / 100 } : nil
                }
                if let offset = offset {
                    result.append(CSSKeyframe(offset: offset, declarations: declarations, timing: timing))
                }
            }
            rest = rest[rest.index(after: close)...]
        }
        return result.sorted { $0.offset < $1.offset }
    }
}

struct CSSTiming {
    var breakpoints: [Double] = []
    let function: (Double) -> Double

    static let linear = CSSTiming { $0 }
    static let ease = CSSTiming.bezier(0.25, 0.1, 0.25, 1)

    private static let parsedLock = NSLock()
    private static var parsed: [String: CSSTiming?] = [:]

    static func parse(_ text: String) -> CSSTiming? {
        parsedLock.lock()
        if let cached = parsed[text] {
            parsedLock.unlock()
            return cached
        }
        parsedLock.unlock()
        let timing = parseUncached(text)
        parsedLock.lock()
        if parsed.count >= 256 {
            parsed.removeAll(keepingCapacity: true)
        }
        parsed[text] = .some(timing)
        parsedLock.unlock()
        return timing
    }

    private static func parseUncached(_ text: String) -> CSSTiming? {
        let t = text.trimmingCharacters(in: .whitespaces).lowercased()
        switch t {
        case "linear":
            return .linear
        case "ease":
            return .ease
        case "ease-in":
            return bezier(0.42, 0, 1, 1)
        case "ease-out":
            return bezier(0, 0, 0.58, 1)
        case "ease-in-out":
            return bezier(0.42, 0, 0.58, 1)
        case "step-start":
            return steps(1, start: true)
        case "step-end":
            return steps(1, start: false)
        default:
            break
        }
        if t.hasPrefix("cubic-bezier("), t.hasSuffix(")") {
            let values = t.dropFirst(13).dropLast().split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            if values.count == 4 {
                return bezier(values[0], values[1], values[2], values[3])
            }
        }
        if t.hasPrefix("steps("), t.hasSuffix(")") {
            let parts = t.dropFirst(6).dropLast().split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            if let n = parts.first.flatMap({ Int($0) }), n > 0 {
                let position = parts.count > 1 ? parts[1] : "end"
                return steps(n, start: position == "start" || position == "jump-start")
            }
        }
        if t.hasPrefix("linear("), t.hasSuffix(")") {
            return linearFunction(String(t.dropFirst(7).dropLast()))
        }
        return nil
    }

    static func linearFunction(_ body: String) -> CSSTiming? {
        var points: [(output: Double, input: Double?)] = []
        for item in body.split(separator: ",") {
            let parts = item.split(separator: " ").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
            guard let first = parts.first, let output = Double(first) else {
                return nil
            }
            let inputs = parts.dropFirst().map { part -> Double? in
                part.hasSuffix("%") ? Double(part.dropLast()).map { $0 / 100 } : nil
            }
            if inputs.contains(where: { $0 == nil }) {
                return nil
            }
            if inputs.isEmpty {
                points.append((output, nil))
            } else {
                for input in inputs {
                    points.append((output, input))
                }
            }
            if points.count > 10_000 {
                return nil
            }
        }
        guard points.count >= 2 else {
            return points.first.map { value in CSSTiming { _ in value.output } }
        }
        if points[0].input == nil {
            points[0].input = 0
        }
        if points[points.count - 1].input == nil {
            points[points.count - 1].input = max(1, points.compactMap { $0.input }.max() ?? 1)
        }
        var largest = points[0].input!
        for index in points.indices {
            if let input = points[index].input {
                largest = max(largest, input)
                points[index].input = largest
            }
        }
        var index = 0
        while index < points.count {
            if points[index].input == nil {
                let start = index - 1
                var end = index
                while points[end].input == nil {
                    end += 1
                }
                let from = points[start].input!, to = points[end].input!
                for k in (start + 1)..<end {
                    points[k].input = from + (to - from) * Double(k - start) / Double(end - start)
                }
                index = end
            }
            index += 1
        }
        let resolved = points.map { (input: $0.input!, output: $0.output) }
        return CSSTiming(breakpoints: resolved.map(\.input)) { x in
            if x <= resolved[0].input {
                let a = resolved[0], b = resolved[1]
                return b.input == a.input ? a.output : a.output + (b.output - a.output) * (x - a.input) / (b.input - a.input)
            }
            for k in 1..<resolved.count where x <= resolved[k].input {
                let a = resolved[k - 1], b = resolved[k]
                if b.input == a.input {
                    return b.output
                }
                return a.output + (b.output - a.output) * (x - a.input) / (b.input - a.input)
            }
            let a = resolved[resolved.count - 2], b = resolved[resolved.count - 1]
            return b.input == a.input ? b.output : a.output + (b.output - a.output) * (x - a.input) / (b.input - a.input)
        }
    }

    static func steps(_ n: Int, start: Bool) -> CSSTiming {
        return CSSTiming { x in
            let step = start ? ceil(x * Double(n)) : floor(x * Double(n))
            return min(1, max(0, step / Double(n)))
        }
    }

    static func bezier(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> CSSTiming {
        let cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
        let cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
        func sampleX(_ t: Double) -> Double { return ((ax * t + bx) * t + cx) * t }
        func sampleY(_ t: Double) -> Double { return ((ay * t + by) * t + cy) * t }
        func derivativeX(_ t: Double) -> Double { return (3 * ax * t + 2 * bx) * t + cx }
        return CSSTiming { x in
            if x <= 0 {
                return 0
            }
            if x >= 1 {
                return 1
            }
            var t = x
            for _ in 0..<8 {
                let error = sampleX(t) - x
                if abs(error) < 1e-6 {
                    return sampleY(t)
                }
                let d = derivativeX(t)
                if abs(d) < 1e-6 {
                    break
                }
                t -= error / d
            }
            var lo = 0.0, hi = 1.0
            t = x
            for _ in 0..<30 {
                let value = sampleX(t)
                if abs(value - x) < 1e-6 {
                    break
                }
                if value < x {
                    lo = t
                } else {
                    hi = t
                }
                t = (lo + hi) / 2
            }
            return sampleY(t)
        }
    }
}

enum CSSTime {
    static func parse(_ text: String) -> Double? {
        let t = text.trimmingCharacters(in: .whitespaces).lowercased()
        if t.hasPrefix("calc(") {
            return calc(t)
        }
        if t.hasSuffix("ms"), let value = Double(t.dropLast(2)) {
            return value / 1000
        }
        if t.hasSuffix("s"), let value = Double(t.dropLast()) {
            return value
        }
        return nil
    }

    private struct Quantity {
        var value: Double
        var time: Bool
    }

    private static func calc(_ text: String) -> Double? {
        guard text.utf8.count <= 512 else {
            return nil
        }
        var scanner = Scanner(Array(text.utf8))
        guard let result = scanner.sum(depth: 0), scanner.atEnd, result.time, result.value.isFinite else {
            return nil
        }
        return result.value
    }

    private struct Scanner {
        let bytes: [UInt8]
        var index = 0

        init(_ bytes: [UInt8]) {
            self.bytes = bytes
        }

        var atEnd: Bool {
            mutating get {
                skipSpaces()
                return index == bytes.count
            }
        }

        mutating func skipSpaces() {
            while index < bytes.count, bytes[index] == 0x20 || bytes[index] == 0x09 || bytes[index] == 0x0A {
                index += 1
            }
        }

        mutating func take(_ byte: UInt8) -> Bool {
            skipSpaces()
            if index < bytes.count, bytes[index] == byte {
                index += 1
                return true
            }
            return false
        }

        mutating func sum(depth: Int) -> Quantity? {
            guard depth < 16, var left = product(depth: depth) else {
                return nil
            }
            while true {
                if take(0x2B) {
                    guard let right = product(depth: depth), right.time == left.time else {
                        return nil
                    }
                    left.value += right.value
                } else if take(0x2D) {
                    guard let right = product(depth: depth), right.time == left.time else {
                        return nil
                    }
                    left.value -= right.value
                } else {
                    return left
                }
            }
        }

        mutating func product(depth: Int) -> Quantity? {
            guard var left = factor(depth: depth) else {
                return nil
            }
            while true {
                if take(0x2A) {
                    guard let right = factor(depth: depth), !(left.time && right.time) else {
                        return nil
                    }
                    left = Quantity(value: left.value * right.value, time: left.time || right.time)
                } else if take(0x2F) {
                    guard let right = factor(depth: depth), !right.time, right.value != 0 else {
                        return nil
                    }
                    left.value /= right.value
                } else {
                    return left
                }
            }
        }

        mutating func factor(depth: Int) -> Quantity? {
            skipSpaces()
            if bytes[index...].starts(with: Array("calc(".utf8)) {
                index += 4
            }
            if take(0x28) {
                guard let inner = sum(depth: depth + 1), take(0x29) else {
                    return nil
                }
                return inner
            }
            let start = index
            if index < bytes.count, bytes[index] == 0x2D || bytes[index] == 0x2B {
                index += 1
            }
            while index < bytes.count, (bytes[index] >= 0x30 && bytes[index] <= 0x39) || bytes[index] == 0x2E {
                index += 1
            }
            guard index > start, let number = Double(String(decoding: bytes[start..<index], as: UTF8.self)) else {
                return nil
            }
            if bytes[index...].starts(with: Array("ms".utf8)) {
                index += 2
                return Quantity(value: number / 1000, time: true)
            }
            if index < bytes.count, bytes[index] == 0x73 {
                index += 1
                return Quantity(value: number, time: true)
            }
            return Quantity(value: number, time: false)
        }
    }
}

struct CSSAnimationSpec: Equatable {
    var name = "none"
    var duration = 0.0
    var timingText = "ease"
    var delay = 0.0
    var iterations = 1.0
    var direction = "normal"
    var fill = "none"
    var paused = false

    var timing: CSSTiming {
        return CSSTiming.parse(timingText) ?? .ease
    }

    static func list(_ declarations: [String: String]) -> [CSSAnimationSpec] {
        var specs: [CSSAnimationSpec] = []
        if let shorthand = declarations["animation"] {
            for item in WidgetNode.splitTopLevel(shorthand, separator: ",") {
                specs.append(parseShorthand(item))
            }
        }
        func longhand(_ key: String, _ apply: (inout CSSAnimationSpec, String) -> Void) {
            guard let value = declarations[key] else {
                return
            }
            let items = WidgetNode.splitTopLevel(value, separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            if specs.isEmpty {
                specs = items.map { _ in CSSAnimationSpec() }
            }
            for index in specs.indices where !items.isEmpty {
                apply(&specs[index], items[index % items.count])
            }
        }
        longhand("animation-name") { $0.name = $1 }
        longhand("animation-duration") { $0.duration = CSSTime.parse($1) ?? 0 }
        longhand("animation-timing-function") { $0.timingText = $1 }
        longhand("animation-delay") { $0.delay = CSSTime.parse($1) ?? 0 }
        longhand("animation-iteration-count") { $0.iterations = $1 == "infinite" ? .infinity : (Double($1) ?? 1) }
        longhand("animation-direction") { $0.direction = $1 }
        longhand("animation-fill-mode") { $0.fill = $1 }
        longhand("animation-play-state") { $0.paused = $1 == "paused" }
        return specs.filter { $0.name != "none" && !$0.name.isEmpty }
    }

    private static func parseShorthand(_ text: String) -> CSSAnimationSpec {
        var spec = CSSAnimationSpec()
        var sawDuration = false
        for token in WidgetNode.splitTopLevel(text.trimmingCharacters(in: .whitespaces), separator: " ") where !token.isEmpty {
            let t = token.lowercased()
            if let time = CSSTime.parse(t) {
                if sawDuration {
                    spec.delay = time
                } else {
                    spec.duration = time
                    sawDuration = true
                }
            } else if CSSTiming.parse(t) != nil {
                spec.timingText = t
            } else if t == "infinite" {
                spec.iterations = .infinity
            } else if let count = Double(t) {
                spec.iterations = count
            } else if ["normal", "reverse", "alternate", "alternate-reverse"].contains(t) {
                spec.direction = t
            } else if ["forwards", "backwards", "both"].contains(t) {
                spec.fill = t
            } else if t == "paused" || t == "running" {
                spec.paused = t == "paused"
            } else {
                spec.name = token
            }
        }
        return spec
    }
}

struct CSSTransitionSpec {
    var property: String
    var duration: Double
    var timing: CSSTiming
    var delay: Double
    var timingText = "ease"

    static func list(_ declarations: [String: String]) -> [CSSTransitionSpec] {
        var specs: [CSSTransitionSpec] = []
        if let shorthand = declarations["transition"] {
            for item in WidgetNode.splitTopLevel(shorthand, separator: ",") {
                var spec = CSSTransitionSpec(property: "all", duration: 0, timing: .ease, delay: 0)
                var sawDuration = false
                for token in WidgetNode.splitTopLevel(item.trimmingCharacters(in: .whitespaces), separator: " ") where !token.isEmpty {
                    if let time = CSSTime.parse(token) {
                        if sawDuration {
                            spec.delay = time
                        } else {
                            spec.duration = time
                            sawDuration = true
                        }
                    } else if let timing = CSSTiming.parse(token) {
                        spec.timing = timing
                        spec.timingText = token
                    } else {
                        spec.property = token.lowercased()
                    }
                }
                specs.append(spec)
            }
        }
        func items(_ key: String) -> [String] {
            return declarations[key].map { WidgetNode.splitTopLevel($0, separator: ",").map { $0.trimmingCharacters(in: .whitespaces) } } ?? []
        }
        let properties = items("transition-property")
        if !properties.isEmpty {
            let durations = items("transition-duration")
            let timings = items("transition-timing-function")
            let delays = items("transition-delay")
            specs = properties.enumerated().map { index, property in
                let base = index < specs.count ? specs[index] : CSSTransitionSpec(property: property, duration: 0, timing: .ease, delay: 0)
                return CSSTransitionSpec(
                    property: property.lowercased(),
                    duration: durations.isEmpty ? base.duration : (CSSTime.parse(durations[index % durations.count]) ?? 0),
                    timing: timings.isEmpty ? base.timing : (CSSTiming.parse(timings[index % timings.count]) ?? .ease),
                    delay: delays.isEmpty ? base.delay : (CSSTime.parse(delays[index % delays.count]) ?? 0),
                    timingText: timings.isEmpty ? base.timingText : (CSSTiming.parse(timings[index % timings.count]) != nil ? timings[index % timings.count] : "ease"))
            }
        } else {
            let durations = items("transition-duration")
            if !durations.isEmpty {
                for index in specs.indices {
                    specs[index].duration = CSSTime.parse(durations[index % durations.count]) ?? 0
                }
            }
        }
        return specs.filter { $0.duration > 0 && $0.property != "none" }
    }
}

enum CSSInterpolate {
    private static let identityValues: [String: String] = ["scale": "1", "scalex": "1", "scaley": "1", "scale3d": "1,1,1", "brightness": "1", "contrast": "1", "opacity": "1", "saturate": "1"]

    static func mix(_ a: String, _ b: String, _ t: Double, quantized: Bool = true) -> String {
        if t == 0 || !t.isFinite {
            return a
        }
        if t == 1 {
            return b
        }
        guard let (left, right) = pair(a, b) else {
            return t < 0.5 ? a : b
        }
        var out = ""
        for (x, y) in zip(left, right) {
            switch (x, y) {
            case let (.number(p, unitP), .number(q, unitQ)) where unitP == unitQ || p == 0 || q == 0:
                let value = p + (q - p) * t
                out += (quantized ? format(value) : String(value)) + (unitP.isEmpty ? unitQ : unitP)
            case let (.text(p), .text(q)) where p == q:
                out += p
            default:
                return translations(a, b, t, quantized: quantized) ?? (t < 0.5 ? a : b)
            }
        }
        return out
    }

    private static let translationList = try! NSRegularExpression(pattern: "^\\s*(translate[xy]?\\([^()]*\\)\\s*)+$", options: [.caseInsensitive])

    private static func translations(_ a: String, _ b: String, _ t: Double, quantized: Bool) -> String? {
        for text in [a, b] where translationList.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) == nil {
            return nil
        }
        func scaled(_ text: String, _ factor: Double) -> String {
            return tokenize(text).map { token -> String in
                switch token {
                case let .number(value, unit):
                    return (quantized ? format(value * factor) : String(value * factor)) + unit
                case let .text(text):
                    return text
                }
            }.joined()
        }
        return scaled(a.trimmingCharacters(in: .whitespaces), 1 - t) + " " + scaled(b.trimmingCharacters(in: .whitespaces), t)
    }

    private enum Token {
        case number(Double, String)
        case text(String)
    }

    private static let pairLock = NSLock()
    private static var pairs: [String: ([Token], [Token])?] = [:]

    private static func pair(_ a: String, _ b: String) -> ([Token], [Token])? {
        let key = a + "\u{1}" + b
        pairLock.lock()
        if let cached = pairs[key] {
            pairLock.unlock()
            return cached
        }
        pairLock.unlock()
        var from = normalizeColors(a)
        var to = normalizeColors(b)
        if from.lowercased() == "none", to.contains("(") {
            from = identity(of: to)
        } else if to.lowercased() == "none", from.contains("(") {
            to = identity(of: from)
        }
        var left = tokenize(from), right = tokenize(to)
        if left.count != right.count, let a = primitive(from), let b = primitive(to) {
            left = tokenize(a)
            right = tokenize(b)
            if left.count != right.count, let (x, y) = padded(a, b) {
                left = tokenize(x)
                right = tokenize(y)
            }
        }
        let result: ([Token], [Token])? = left.count == right.count ? (left, right) : nil
        pairLock.lock()
        if pairs.count >= 2048 {
            pairs.removeAll(keepingCapacity: true)
        }
        if key.utf8.count <= 4096 {
            pairs.updateValue(result, forKey: key)
        }
        pairLock.unlock()
        return result
    }

    private static func tokenize(_ text: String) -> [Token] {
        var tokens: [Token] = []
        let scalars = Array(text.unicodeScalars)
        var i = 0
        var pending = ""
        func flush() {
            if !pending.isEmpty {
                tokens.append(.text(pending))
                pending = ""
            }
        }
        while i < scalars.count {
            let c = scalars[i]
            let startsNumber = CharacterSet.decimalDigits.contains(c) || (c == "." && i + 1 < scalars.count && CharacterSet.decimalDigits.contains(scalars[i + 1])) || ((c == "-" || c == "+") && i + 1 < scalars.count && (CharacterSet.decimalDigits.contains(scalars[i + 1]) || scalars[i + 1] == "."))
            let previous: Unicode.Scalar? = i > 0 ? scalars[i - 1] : nil
            let identPart = previous.map { CharacterSet.letters.contains($0) || $0 == "-" && i > 1 && CharacterSet.letters.contains(scalars[i - 2]) } ?? false
            if startsNumber && !(identPart && c != "-") && !(previous == "#") {
                var j = i + 1
                while j < scalars.count, CharacterSet.decimalDigits.contains(scalars[j]) || scalars[j] == "." || ((scalars[j] == "e" || scalars[j] == "E") && j + 1 < scalars.count && (CharacterSet.decimalDigits.contains(scalars[j + 1]) || scalars[j + 1] == "-")) {
                    if scalars[j] == "e" || scalars[j] == "E" {
                        j += 1
                    }
                    j += 1
                }
                let number = String(String.UnicodeScalarView(scalars[i..<j]))
                var k = j
                while k < scalars.count, CharacterSet.letters.contains(scalars[k]) || scalars[k] == "%" {
                    k += 1
                }
                let unit = String(String.UnicodeScalarView(scalars[j..<k]))
                if let value = Double(number) {
                    flush()
                    tokens.append(.number(value, unit))
                    i = k
                    continue
                }
            }
            pending.unicodeScalars.append(c)
            i += 1
        }
        flush()
        return tokens
    }

    private static func format(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e9 {
            return String(Int(value))
        }
        if value.isFinite, abs(value) < 1e9 {
            let scaled = (abs(value) * 10000).rounded()
            let whole = Int(scaled / 10000)
            var fraction = Int(scaled - Double(whole) * 10000)
            let sign = value < 0 && scaled != 0 ? "-" : ""
            if fraction == 0 {
                return sign + String(whole)
            }
            var width = 4
            while fraction % 10 == 0 {
                fraction /= 10
                width -= 1
            }
            let digits = String(fraction)
            return sign + String(whole) + "." + String(repeating: "0", count: width - digits.count) + digits
        }
        let text = String(format: "%.4f", value).replacingOccurrences(of: "0+$", with: "", options: .regularExpression)
        return text.hasSuffix(".") ? String(text.dropLast()) : text
    }

    private static func primitive(_ transform: String) -> String? {
        var out: [String] = []
        var rest = Substring(transform)
        while let open = rest.firstIndex(of: "("), let close = rest[open...].firstIndex(of: ")") {
            let name = rest[..<open].trimmingCharacters(in: .whitespaces).lowercased()
            let args = WidgetNode.splitTopLevel(String(rest[rest.index(after: open)..<close]), separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            let one = args.count == 1 ? args[0] : nil
            switch (name, one) {
            case let ("translatex", a?):
                out.append("translate(\(a), 0)")
            case let ("translatey", a?):
                out.append("translate(0, \(a))")
            case let ("translate", a?):
                out.append("translate(\(a), 0)")
            case let ("scalex", a?):
                out.append("scale(\(a), 1)")
            case let ("scaley", a?):
                out.append("scale(1, \(a))")
            case let ("scale", a?):
                out.append("scale(\(a), \(a))")
            case let ("skewx", a?):
                out.append("skew(\(a), 0)")
            case let ("skewy", a?):
                out.append("skew(0, \(a))")
            case let ("skew", a?):
                out.append("skew(\(a), 0)")
            default:
                out.append(name + "(" + args.joined(separator: ", ") + ")")
            }
            rest = rest[rest.index(after: close)...]
        }
        return rest.trimmingCharacters(in: .whitespaces).isEmpty && !out.isEmpty ? out.joined(separator: " ") : nil
    }

    private static func functionNames(_ transform: String) -> [String] {
        var names: [String] = []
        var rest = Substring(transform)
        while let open = rest.firstIndex(of: "("), let close = rest[open...].firstIndex(of: ")") {
            names.append(rest[..<open].trimmingCharacters(in: .whitespaces).lowercased())
            rest = rest[rest.index(after: close)...]
        }
        return names
    }

    private static func functionList(_ transform: String) -> [String] {
        var items: [String] = []
        var rest = Substring(transform)
        while let open = rest.firstIndex(of: "("), let close = rest[open...].firstIndex(of: ")") {
            items.append(rest[..<rest.index(after: close)].trimmingCharacters(in: .whitespaces))
            rest = rest[rest.index(after: close)...]
        }
        return items
    }

    private static func padded(_ a: String, _ b: String) -> (String, String)? {
        let x = functionNames(a), y = functionNames(b)
        let (short, long) = x.count < y.count ? (x, y) : (y, x)
        guard !short.isEmpty || !long.isEmpty, Array(long.prefix(short.count)) == short else {
            return nil
        }
        let extra = functionList(x.count < y.count ? b : a).dropFirst(short.count).map { identity(of: $0) }.joined(separator: " ")
        return x.count < y.count ? (a + " " + extra, b) : (a, b + " " + extra)
    }

    private static func identity(of transform: String) -> String {
        var out = ""
        var rest = Substring(transform)
        while let open = rest.firstIndex(of: "("), let close = rest[open...].firstIndex(of: ")") {
            let name = rest[..<open].trimmingCharacters(in: .whitespaces)
            let args = WidgetNode.splitTopLevel(String(rest[rest.index(after: open)..<close]), separator: ",")
            let identity = args.map { arg -> String in
                let lead = arg.prefix { $0 == " " || $0 == "\t" }
                if let value = identityValues[name.lowercased()] {
                    return lead + value
                }
                let unit = arg.trimmingCharacters(in: .whitespaces).drop { "0123456789.-+".contains($0) }
                return lead + "0" + unit
            }.joined(separator: ",")
            out += (out.isEmpty ? "" : " ") + name + "(" + identity + ")"
            rest = rest[rest.index(after: close)...]
        }
        return out.isEmpty ? transform : out
    }

    private static let named: [String: (Double, Double, Double, Double)] = [
        "transparent": (0, 0, 0, 0), "white": (255, 255, 255, 1), "black": (0, 0, 0, 1), "red": (255, 0, 0, 1), "green": (0, 128, 0, 1),
        "blue": (0, 0, 255, 1), "gray": (128, 128, 128, 1), "grey": (128, 128, 128, 1), "orange": (255, 165, 0, 1), "yellow": (255, 255, 0, 1),
    ]

    static func normalizeColors(_ text: String) -> String {
        var out = ""
        let scalars = Array(text.unicodeScalars)
        var i = 0
        while i < scalars.count {
            if scalars[i] == "#" {
                var j = i + 1
                while j < scalars.count, CharacterSet(charactersIn: "0123456789abcdefABCDEF").contains(scalars[j]) {
                    j += 1
                }
                let hex = String(String.UnicodeScalarView(scalars[(i + 1)..<j]))
                if let rgba = parseHex(hex) {
                    out += rgbaString(rgba)
                    i = j
                    continue
                }
            }
            if CharacterSet.letters.contains(scalars[i]), i == 0 || !CharacterSet.alphanumerics.contains(scalars[i - 1]) && scalars[i - 1] != "-" {
                var j = i
                while j < scalars.count, CharacterSet.letters.contains(scalars[j]) || scalars[j] == "-" {
                    j += 1
                }
                let word = String(String.UnicodeScalarView(scalars[i..<j]))
                if j < scalars.count, scalars[j] == "(" {
                    let lower = word.lowercased()
                    if lower == "rgb" || lower == "rgba", let close = scalars[j...].firstIndex(of: ")") {
                        let inner = String(String.UnicodeScalarView(scalars[(j + 1)..<close]))
                        let parts = inner.split(whereSeparator: { $0 == "," || $0 == " " || $0 == "/" }).map(String.init)
                        let values = parts.compactMap { part -> Double? in
                            part.hasSuffix("%") ? Double(part.dropLast()).map { $0 / 100 } : Double(part)
                        }
                        if values.count >= 3 {
                            out += rgbaString((values[0], values[1], values[2], values.count > 3 ? values[3] : 1))
                            i = close + 1
                            continue
                        }
                    }
                } else if let rgba = named[word.lowercased()] {
                    out += rgbaString(rgba)
                    i = j
                    continue
                }
                out += word
                i = j
                continue
            }
            out.unicodeScalars.append(scalars[i])
            i += 1
        }
        return out
    }

    private static func parseHex(_ hex: String) -> (Double, Double, Double, Double)? {
        let chars = Array(hex)
        func value(_ s: String) -> Double? { return UInt8(s, radix: 16).map(Double.init) }
        switch chars.count {
        case 3, 4:
            let parts = chars.map { String([$0, $0]) }
            guard let r = value(parts[0]), let g = value(parts[1]), let b = value(parts[2]) else { return nil }
            return (r, g, b, chars.count == 4 ? (value(parts[3]) ?? 255) / 255 : 1)
        case 6, 8:
            let parts = stride(from: 0, to: chars.count, by: 2).map { String(chars[$0..<$0 + 2]) }
            guard let r = value(parts[0]), let g = value(parts[1]), let b = value(parts[2]) else { return nil }
            return (r, g, b, chars.count == 8 ? (value(parts[3]) ?? 255) / 255 : 1)
        default:
            return nil
        }
    }

    private static func rgbaString(_ c: (Double, Double, Double, Double)) -> String {
        return "rgba(\(format(c.0)), \(format(c.1)), \(format(c.2)), \(format(c.3)))"
    }
}

final class WidgetAnimator {
    struct Running {
        let spec: CSSAnimationSpec
        let start: CFTimeInterval
        var pausedAt: CFTimeInterval?
        var pausedDuration: CFTimeInterval = 0
        var iteration = 0
        var ended = false

        var timelineStart: CFTimeInterval { start + pausedDuration }

        func localTime(_ now: CFTimeInterval) -> CFTimeInterval {
            return (pausedAt ?? now) - timelineStart
        }
    }

    struct Transition {
        let from: String
        let to: String
        let start: CFTimeInterval
        let spec: CSSTransitionSpec
    }

    static let initialValues: [String: String] = ["opacity": "1", "transform": "none", "translate": "0px", "scale": "1", "rotate": "0deg", "filter": "none", "letter-spacing": "0px"]

    private(set) var keyframes: [String: [CSSKeyframe]] = [:]

    static let compositedProperties: Set<String> = ["transform", "opacity", "filter", "box-shadow"]

    let web = WidgetWebAnimations()
    var underlying: ((Int, String) -> String?)?

    func base(_ id: Int) -> [String: String] {
        return bases[id] ?? [:]
    }

    private func underlyingValue(_ id: Int, _ property: String) -> String {
        return bases[id]?[property] ?? underlying?(id, property) ?? WidgetAnimator.initialValues[property] ?? ""
    }

    func hasCSSMotion(_ id: Int) -> Bool {
        return transitions[id] != nil || running[id]?.isEmpty == false
    }

    private func hasLiveCSS(_ id: Int) -> Bool {
        return transitions[id] != nil || running[id]?.contains(where: { !$0.ended && keyframes[$0.spec.name] != nil }) == true
    }

    func webBakes(_ id: Int) -> [WidgetWebAnimations.Bake]? {
        if web.animations(id).isEmpty {
            return nil
        }
        var base = bases[id] ?? [:]
        for property in WebAnimation.compositable where base[property] == nil {
            base[property] = underlying?(id, property)
        }
        return web.bakes(id, base: base, hasOtherAnimations: hasLiveCSS(id))
    }

    func animatedIDs() -> Set<Int> {
        var ids = Set(transitions.keys).union(web.animatedIDs())
        for (id, list) in running where list.contains(where: { !$0.ended && keyframes[$0.spec.name] != nil }) {
            ids.insert(id)
        }
        return ids
    }

    func animatesOnly(_ id: Int, _ properties: Set<String>) -> Bool {
        if cssOverrides[id] != nil || transitions[id] != nil || !web.animations(id).isEmpty {
            return false
        }
        var any = false
        for item in running[id] ?? [] where !item.ended {
            guard let frames = keyframes[item.spec.name] else {
                continue
            }
            for frame in frames where frame.declarations.contains(where: { !properties.contains($0.name) }) {
                return false
            }
            any = true
        }
        return any
    }

    func isCompositable(_ id: Int) -> Bool {
        if cssOverrides[id] != nil {
            return false
        }
        if !web.animations(id).isEmpty {
            guard web.onlyCompositable(id) else {
                return false
            }
            if !hasLiveCSS(id) {
                return true
            }
        }
        if let list = transitions[id], list.keys.contains(where: { !WidgetAnimator.compositedProperties.contains($0) }) {
            return false
        }
        for item in running[id] ?? [] where !item.ended {
            guard let frames = keyframes[item.spec.name] else {
                continue
            }
            if item.spec.paused {
                return false
            }
            for frame in frames where frame.declarations.contains(where: { !WidgetAnimator.compositedProperties.contains($0.name) }) {
                return false
            }
        }
        return transitions[id] != nil || running[id]?.contains(where: { !$0.ended && keyframes[$0.spec.name] != nil }) == true
    }

    typealias Sample = (transform: String?, opacity: String?, filter: String?, shadow: String?)

    func reachableValues(_ id: Int, samples: Int = 48) -> [Sample] {
        var out: [Sample] = []
        let base = bases[id] ?? [:]
        out.append((base["transform"], base["opacity"], base["filter"], base["box-shadow"]))
        for item in running[id] ?? [] where !item.ended {
            guard let frames = keyframes[item.spec.name] else {
                continue
            }
            for step in 0...samples {
                let values = self.values(frames: frames, base: base, progress: Double(step) / Double(samples), spec: item.spec)
                out.append((values.first { $0.0 == "transform" }?.1, values.first { $0.0 == "opacity" }?.1, values.first { $0.0 == "filter" }?.1 ?? base["filter"], values.first { $0.0 == "box-shadow" }?.1 ?? base["box-shadow"]))
            }
        }
        for (property, transition) in transitions[id] ?? [:] {
            for step in 0...max(16, samples) {
                let progress = Double(step) / Double(max(16, samples))
                let value = CSSInterpolate.mix(transition.from, transition.to, transition.spec.timing.function(progress))
                switch property {
                case "transform":
                    out.append((value, nil, base["filter"], base["box-shadow"]))
                case "opacity":
                    out.append((nil, value, base["filter"], base["box-shadow"]))
                case "filter":
                    out.append((nil, nil, value, base["box-shadow"]))
                default:
                    out.append((nil, nil, base["filter"], value))
                }
            }
        }
        if !web.animations(id).isEmpty {
            var webBase = base
            for property in WebAnimation.compositable where webBase[property] == nil {
                webBase[property] = underlying?(id, property)
            }
            out.append(contentsOf: web.reachableValues(id, base: webBase).map { ($0.transform, $0.opacity, base["filter"], base["box-shadow"]) })
        }
        return out
    }

    func animatesEffects(_ id: Int) -> Bool {
        if let list = transitions[id], list.keys.contains(where: { $0 == "filter" || $0 == "box-shadow" }) {
            return true
        }
        for item in running[id] ?? [] where !item.ended {
            if let frames = keyframes[item.spec.name], frames.contains(where: { $0.declarations.contains { $0.name == "filter" || $0.name == "box-shadow" } }) {
                return true
            }
        }
        return false
    }

    struct Baked {
        let spec: CSSAnimationSpec
        let start: CFTimeInterval
        let value: (Double) -> Sample
        var geometryTolerance: CGFloat = 0.05
        var opacityTolerance: Float = 0.002
        var breakpoints: [Double] = []
    }

    func bakeable(_ id: Int) -> Baked? {
        if let list = transitions[id] {
            guard !list.isEmpty, list.keys.allSatisfy({ $0 == "transform" || $0 == "opacity" }),
                  web.animations(id).isEmpty, cssOverrides[id] == nil,
                  running[id]?.contains(where: { keyframes[$0.spec.name] != nil }) != true else {
                return nil
            }
            let base = bases[id] ?? [:]
            let start = list.values.map(\.start).min()!
            let begin = list.values.map { $0.start + $0.spec.delay }.min()!
            let end = list.values.map { $0.start + $0.spec.delay + $0.spec.duration }.max()!
            var spec = CSSAnimationSpec()
            spec.name = "transition|" + list.keys.sorted().map { property in
                let transition = list[property]!
                return "\(property)|\(transition.from)|\(transition.to)|\(transition.start)|\(transition.spec.duration)|\(transition.spec.delay)|\(transition.spec.timingText)"
            }.joined(separator: "|") + "|\(base["transform"] ?? "")|\(base["opacity"] ?? "")"
            spec.duration = end - begin
            spec.delay = begin - start
            spec.timingText = "linear"
            spec.fill = "both"
            let breakpoints = list.values.flatMap { transition in
                ([0, 1] + transition.spec.timing.breakpoints).map { progress in
                    (transition.start + transition.spec.delay + progress * transition.spec.duration - begin) / spec.duration
                }
            }
            return Baked(spec: spec, start: start, value: { progress in
                let now = begin + progress * (end - begin)
                func value(_ property: String) -> String? {
                    guard let transition = list[property] else { return base[property] }
                    let elapsed = now - transition.start - transition.spec.delay
                    if elapsed <= 0 { return transition.from }
                    if elapsed >= transition.spec.duration { return transition.to }
                    return CSSInterpolate.mix(transition.from, transition.to, transition.spec.timing.function(elapsed / transition.spec.duration), quantized: false)
                }
                return (value("transform"), value("opacity"), base["filter"], base["box-shadow"])
            }, geometryTolerance: 0.0001, opacityTolerance: 0.0001, breakpoints: breakpoints)
        }
        guard transitions[id] == nil, web.animations(id).isEmpty, cssOverrides[id] == nil, let list = running[id] else {
            return nil
        }
        let live = list.filter { keyframes[$0.spec.name] != nil }
        guard live.count == 1, let item = live.first, !item.ended, !item.spec.paused, let frames = keyframes[item.spec.name], !frames.isEmpty else {
            return nil
        }
        for frame in frames where frame.declarations.contains(where: { !WidgetAnimator.compositedProperties.contains($0.name) }) {
            return nil
        }
        let base = bases[id] ?? [:]
        let spec = item.spec
        return Baked(spec: spec, start: item.timelineStart, value: { [self] progress in
            let values = self.values(frames: frames, base: base, progress: progress, spec: spec)
            return (values.last { $0.0 == "transform" }?.1, values.last { $0.0 == "opacity" }?.1, values.last { $0.0 == "filter" }?.1 ?? base["filter"], values.last { $0.0 == "box-shadow" }?.1 ?? base["box-shadow"])
        })
    }

    private var lastSampleNow: CFTimeInterval = 0

    func peek(_ id: Int, at now: CFTimeInterval) -> (transform: String?, opacity: String?)? {
        if transitions[id] != nil, bakeable(id) != nil {
            return (currentTransitionValue(id, "transform", now) ?? bases[id]?["transform"], currentTransitionValue(id, "opacity", now) ?? bases[id]?["opacity"])
        }
        if !web.animations(id).isEmpty {
            let values = web.values(id, at: web.clock() + (now - lastSampleNow), underlying: { [self] in self.underlyingValue(id, $0) })
            let base = bases[id] ?? [:]
            return (values.last { $0.0 == "transform" }?.1 ?? base["transform"], values.last { $0.0 == "opacity" }?.1 ?? base["opacity"])
        }
        guard let item = running[id]?.first(where: { keyframes[$0.spec.name] != nil }), let frames = keyframes[item.spec.name] else {
            return nil
        }
        let spec = item.spec
        let base = bases[id] ?? [:]
        let local = item.localTime(now) - spec.delay
        let duration = max(spec.duration, 0.0001)
        var progress: Double
        if local < 0 {
            guard spec.fill == "backwards" || spec.fill == "both" else {
                return (base["transform"], base["opacity"])
            }
            progress = reversed(spec.direction, 0) ? 1 : 0
        } else if local >= spec.iterations * duration {
            guard spec.fill == "forwards" || spec.fill == "both" else {
                return (base["transform"], base["opacity"])
            }
            let last = max(0, Int(ceil(min(spec.iterations, 1e9))) - 1)
            progress = spec.iterations.truncatingRemainder(dividingBy: 1) == 0 ? 1 : spec.iterations.truncatingRemainder(dividingBy: 1)
            if reversed(spec.direction, last) {
                progress = 1 - progress
            }
        } else {
            let iteration = Int(min(local / duration, 1e12))
            progress = (local - Double(iteration) * duration) / duration
            if reversed(spec.direction, iteration) {
                progress = 1 - progress
            }
        }
        let values = self.values(frames: frames, base: base, progress: progress, spec: spec)
        return (values.last { $0.0 == "transform" }?.1 ?? base["transform"], values.last { $0.0 == "opacity" }?.1 ?? base["opacity"])
    }

    func isActive(excluding driven: Set<Int>) -> Bool {
        if transitions.keys.contains(where: { !driven.contains($0) }) || web.needsFrames(excluding: driven) {
            return true
        }
        return running.contains { id, list in !driven.contains(id) && list.contains { !$0.ended && !$0.spec.paused && keyframes[$0.spec.name] != nil } }
    }

    func nextIteration(_ id: Int, after now: CFTimeInterval) -> CFTimeInterval? {
        guard let item = running[id]?.first(where: { !$0.ended && !$0.spec.paused && keyframes[$0.spec.name] != nil }) else {
            return nil
        }
        let duration = max(item.spec.duration, 0.0001)
        let begin = item.timelineStart + item.spec.delay
        let k = max(1, floor((now - begin) / duration) + 1)
        let next = begin + k * duration
        if item.spec.iterations.isFinite && next >= begin + item.spec.iterations * duration {
            return nil
        }
        return next
    }

    func endTime(_ id: Int) -> CFTimeInterval? {
        if let list = transitions[id], !list.isEmpty {
            return list.values.map { $0.start + $0.spec.delay + $0.spec.duration }.min()
        }
        if !web.animations(id).isEmpty {
            return web.endTime(id)
        }
        guard let item = running[id]?.first(where: { !$0.ended && !$0.spec.paused && keyframes[$0.spec.name] != nil }), item.spec.iterations.isFinite else {
            return nil
        }
        return item.timelineStart + item.spec.delay + item.spec.iterations * max(item.spec.duration, 0.0001)
    }

    private(set) var running: [Int: [Running]] = [:]
    private(set) var transitions: [Int: [String: Transition]] = [:]
    var cssOverrides: [Int: [String: CSSOverride]] = [:]
    private var bases: [Int: [String: String]] = [:]
    private var configuredTransitions: [Int: [CSSTransitionSpec]] = [:]
    private(set) var lastOverrides: [Int: [(String, String)]] = [:]
    var onEvent: ((String, Int, String, Double) -> Void)?

    static let entryProperties = ["animation", "animation-name", "transition", "transition-property"]

    static let watched = [
        "animation", "animation-name", "animation-duration", "animation-timing-function", "animation-delay", "animation-iteration-count",
        "animation-direction", "animation-fill-mode", "animation-play-state", "transition", "transition-property", "transition-duration",
        "transition-timing-function", "transition-delay", "transform", "opacity", "color", "background-color", "background", "width", "height",
        "top", "left", "right", "bottom", "box-shadow", "border-color", "max-height", "stroke-dashoffset", "fill", "stroke", "translate",
        "scale", "rotate", "filter", "letter-spacing", "font-size", "margin-top", "margin-left", "flex-grow", "transform-origin",
        "backdrop-filter",
    ]

    var isActive: Bool {
        return !transitions.isEmpty || web.needsFrames(excluding: []) || running.values.contains { list in list.contains { !$0.ended && !$0.spec.paused && keyframes[$0.spec.name] != nil } }
    }

    func reset() {
        keyframes.removeAll()
        running.removeAll()
        transitions.removeAll()
        cssOverrides.removeAll()
        bases.removeAll()
        configuredTransitions.removeAll()
        lastOverrides.removeAll()
        web.removeAll()
    }

    func setStyleSheets(_ texts: [String]) {
        var rules: [String: [CSSKeyframe]] = [:]
        for text in texts {
            CSSKeyframes.parse(text, into: &rules)
        }
        keyframes = rules
    }

    func update(declarations: [Int: [String: String]], now: CFTimeInterval) {
        var nextRunning: [Int: [Running]] = [:]
        for (id, values) in declarations {
            let specs = CSSAnimationSpec.list(values)
            if !specs.isEmpty {
                let previous = running[id] ?? []
                nextRunning[id] = specs.map { spec in
                    if let existing = previous.first(where: { $0.spec.name == spec.name }) {
                        if existing.spec == spec {
                            return existing
                        }
                        let resumedDuration = !spec.paused ? existing.pausedAt.map { now - $0 } ?? 0 : 0
                        return Running(spec: spec, start: existing.start, pausedAt: spec.paused ? existing.pausedAt ?? now : nil, pausedDuration: existing.pausedDuration + resumedDuration, iteration: existing.iteration, ended: false)
                    }
                    if keyframes[spec.name] != nil {
                        onEvent?("animationstart", id, spec.name, 0)
                    }
                    return Running(spec: spec, start: now, pausedAt: spec.paused ? now : nil)
                }
            }
            let transitionSpecs = CSSTransitionSpec.list(values)
            configuredTransitions[id] = transitionSpecs.isEmpty ? nil : transitionSpecs
            var base = bases[id] ?? [:]
            for property in WidgetAnimator.watched where !property.hasPrefix("animation") && !property.hasPrefix("transition") {
                let newValue = values[property]
                let oldValue = base[property] ?? (bases[id] != nil && newValue != nil ? WidgetAnimator.initialValues[property] : nil)
                defer { base[property] = newValue }
                guard let old = oldValue, let new = newValue, old != new else {
                    if newValue == nil {
                        transitions[id]?[property] = nil
                    }
                    continue
                }
                guard let spec = transitionSpecs.last(where: { $0.property == property || $0.property == "all" }) else {
                    transitions[id]?[property] = nil
                    continue
                }
                let current = currentTransitionValue(id, property, now) ?? old
                transitions[id, default: [:]][property] = Transition(from: current, to: new, start: now, spec: spec)
            }
            bases[id] = base
            if transitions[id]?.isEmpty == true {
                transitions[id] = nil
            }
        }
        for id in bases.keys where declarations[id] == nil {
            bases[id] = nil
            transitions[id] = nil
            configuredTransitions[id] = nil
        }
        running = nextRunning
        pruneCSSOverrides()
    }

    func transitions(_ id: Int, property: String) -> Bool {
        return configuredTransitions[id]?.contains { $0.property == property || $0.property == "all" } == true
    }

    func recordScriptedValues(_ ops: [WidgetOp]) {
        for op in ops {
            bases[op.id, default: [:]][op.name.lowercased()] = op.value.isEmpty ? nil : op.value
        }
    }

    private func currentTransitionValue(_ id: Int, _ property: String, _ now: CFTimeInterval) -> String? {
        guard let transition = transitions[id]?[property] else {
            return nil
        }
        let elapsed = now - transition.start - transition.spec.delay
        if elapsed <= 0 {
            return transition.from
        }
        let progress = min(1, elapsed / transition.spec.duration)
        return CSSInterpolate.mix(transition.from, transition.to, transition.spec.timing.function(progress))
    }

    func sample(now: CFTimeInterval) -> [Int: [(String, String)]] {
        lastSampleNow = now
        var overrides: [Int: [(String, String)]] = [:]
        for (id, list) in running {
            var updated = list
            for index in updated.indices {
                let item = updated[index]
                guard let frames = keyframes[item.spec.name], !frames.isEmpty else {
                    continue
                }
                if let values = sampleAnimation(item, frames: frames, now: now, id: id, state: &updated[index]) {
                    for value in values {
                        overrides[id, default: []].removeAll { $0.0 == value.0 }
                        overrides[id, default: []].append(value)
                    }
                }
            }
            running[id] = updated
        }
        for (id, list) in transitions {
            for (property, transition) in list {
                var elapsed = now - transition.start - transition.spec.delay
                if let override = cssOverrides[id]?["transition:" + property] {
                    if override.cancelled {
                        transitions[id]?[property] = nil
                        continue
                    }
                    elapsed = override.local(now) - transition.spec.delay
                }
                if elapsed >= transition.spec.duration {
                    transitions[id]?[property] = nil
                    onEvent?("transitionend", id, property, transition.spec.duration)
                    continue
                }
                let progress = max(0, elapsed) / transition.spec.duration
                let value = elapsed <= 0 ? transition.from : CSSInterpolate.mix(transition.from, transition.to, transition.spec.timing.function(progress))
                overrides[id, default: []].removeAll { $0.0 == property }
                overrides[id, default: []].append((property, value))
            }
            if transitions[id]?.isEmpty == true {
                transitions[id] = nil
            }
        }
        if !web.isEmpty {
            web.sample(into: &overrides, underlying: { [self] in self.underlyingValue($0, $1) })
        }
        lastOverrides = overrides
        return overrides
    }

    private func sampleAnimation(_ item: Running, frames: [CSSKeyframe], now: CFTimeInterval, id: Int, state: inout Running) -> [(String, String)]? {
        let spec = item.spec
        var local = item.localTime(now) - spec.delay
        if let override = cssOverrides[id]?["animation:" + spec.name] {
            if override.cancelled {
                return nil
            }
            local = override.local(now) - spec.delay
        }
        if local < 0 {
            if spec.fill == "backwards" || spec.fill == "both" {
                return values(frames: frames, base: bases[id] ?? [:], progress: spec.direction.hasPrefix("reverse") || spec.direction == "alternate-reverse" ? 1 : 0, spec: spec)
            }
            return nil
        }
        let duration = max(spec.duration, 0.0001)
        let total = spec.iterations * duration
        if local >= total {
            if !state.ended {
                state.ended = true
                onEvent?("animationend", id, spec.name, total)
            }
            if spec.fill == "forwards" || spec.fill == "both" {
                let lastIteration = max(0, Int(ceil(min(spec.iterations, 1e9))) - 1)
                var progress = spec.iterations.truncatingRemainder(dividingBy: 1) == 0 ? 1.0 : spec.iterations.truncatingRemainder(dividingBy: 1)
                if reversed(spec.direction, lastIteration) {
                    progress = 1 - progress
                }
                return values(frames: frames, base: bases[id] ?? [:], progress: progress, spec: spec)
            }
            return nil
        }
        let iteration = Int(min(local / duration, 1e12))
        if iteration != state.iteration {
            state.iteration = iteration
            onEvent?("animationiteration", id, spec.name, local)
        }
        var progress = (local - Double(iteration) * duration) / duration
        if reversed(spec.direction, iteration) {
            progress = 1 - progress
        }
        return values(frames: frames, base: bases[id] ?? [:], progress: progress, spec: spec)
    }

    private func reversed(_ direction: String, _ iteration: Int) -> Bool {
        switch direction {
        case "reverse":
            return true
        case "alternate":
            return iteration % 2 == 1
        case "alternate-reverse":
            return iteration % 2 == 0
        default:
            return false
        }
    }

    private func values(frames: [CSSKeyframe], base: [String: String], progress: Double, spec: CSSAnimationSpec) -> [(String, String)] {
        var properties: [String] = []
        for frame in frames {
            for declaration in frame.declarations where !properties.contains(declaration.name) {
                properties.append(declaration.name)
            }
        }
        var result: [(String, String)] = []
        for property in properties {
            var stops = frames.compactMap { frame -> (Double, String, CSSTiming?)? in
                guard let value = frame.declarations.last(where: { $0.name == property })?.value else {
                    return nil
                }
                return (frame.offset, value, frame.timing)
            }
            if let own = base[property] ?? WidgetAnimator.initialValues[property] {
                if let first = stops.first, first.0 > 0 {
                    stops.insert((0, own, frames.first?.timing), at: 0)
                }
                if let last = stops.last, last.0 < 1 {
                    stops.append((1, own, nil))
                }
            }
            guard let first = stops.first, let last = stops.last else {
                continue
            }
            if progress <= first.0 {
                result.append((property, first.1))
                continue
            }
            if progress >= last.0 {
                result.append((property, last.1))
                continue
            }
            for index in 0..<(stops.count - 1) {
                let a = stops[index], b = stops[index + 1]
                if progress >= a.0 && progress <= b.0 {
                    let span = max(b.0 - a.0, 0.000001)
                    let local = (progress - a.0) / span
                    let timing = a.2 ?? spec.timing
                    result.append((property, CSSInterpolate.mix(a.1, b.1, timing.function(local))))
                    break
                }
            }
        }
        return result
    }
}
