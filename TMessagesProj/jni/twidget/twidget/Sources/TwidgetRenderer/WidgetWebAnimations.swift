import Foundation
import QuartzCore

struct WebEasing: Equatable {
    enum Jump: Equatable {
        case start, end, both, none
    }

    let text: String
    let steps: (count: Int, jump: Jump)?
    let curve: CSSTiming?
    var points: (Double, Double, Double, Double)? = nil

    static func == (a: WebEasing, b: WebEasing) -> Bool {
        return a.text == b.text
    }

    static let linear = WebEasing(text: "linear", steps: nil, curve: nil)

    static func parse(_ raw: String) -> WebEasing? {
        let text = raw.trimmingCharacters(in: .whitespaces).lowercased()
        if text.isEmpty || text.utf8.count > 4096 {
            return nil
        }
        let named: [String: (Double, Double, Double, Double)] = ["ease": (0.25, 0.1, 0.25, 1), "ease-in": (0.42, 0, 1, 1), "ease-out": (0, 0, 0.58, 1), "ease-in-out": (0.42, 0, 0.58, 1)]
        if let p = named[text] {
            return WebEasing(text: text, steps: nil, curve: .bezier(p.0, p.1, p.2, p.3), points: p)
        }
        switch text {
        case "linear":
            return .linear
        case "step-start":
            return WebEasing(text: text, steps: (1, .start), curve: nil)
        case "step-end":
            return WebEasing(text: text, steps: (1, .end), curve: nil)
        default:
            break
        }
        if text.hasPrefix("steps("), text.hasSuffix(")") {
            let parts = text.dropFirst(6).dropLast().split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }
            guard (1...2).contains(parts.count), let count = Int(parts[0]), count > 0, count <= 100_000 else {
                return nil
            }
            let jump: Jump
            switch parts.count > 1 ? parts[1] : "end" {
            case "start", "jump-start":
                jump = .start
            case "end", "jump-end":
                jump = .end
            case "jump-both":
                jump = .both
            case "jump-none":
                guard count > 1 else {
                    return nil
                }
                jump = .none
            default:
                return nil
            }
            return WebEasing(text: text, steps: (count, jump), curve: nil)
        }
        if text.hasPrefix("cubic-bezier("), text.hasSuffix(")") {
            let values = text.dropFirst(13).dropLast().split(separator: ",").map { Double($0.trimmingCharacters(in: .whitespaces)) }
            guard values.count == 4, let x1 = values[0], let y1 = values[1], let x2 = values[2], let y2 = values[3], [x1, y1, x2, y2].allSatisfy({ $0.isFinite }), (0...1).contains(x1), (0...1).contains(x2) else {
                return nil
            }
            return WebEasing(text: text, steps: nil, curve: .bezier(x1, y1, x2, y2), points: (x1, y1, x2, y2))
        }
        if text.hasPrefix("linear("), text.split(separator: ",").count > 256 {
            return nil
        }
        guard let curve = CSSTiming.parse(text) else {
            return nil
        }
        return WebEasing(text: text, steps: nil, curve: curve)
    }

    func apply(_ x: Double, before: Bool) -> Double {
        if let (count, jump) = steps {
            let n = Double(count)
            var step = floor(x * n)
            if jump == .start || jump == .both {
                step += 1
            }
            if before && (x * n).truncatingRemainder(dividingBy: 1) == 0 {
                step -= 1
            }
            if x >= 0 && step < 0 {
                step = 0
            }
            let jumps: Double
            switch jump {
            case .both:
                jumps = n + 1
            case .none:
                jumps = n - 1
            default:
                jumps = n
            }
            if x <= 1 && step > jumps {
                step = jumps
            }
            return step / jumps
        }
        if let (x1, y1, x2, y2) = points {
            if x < 0 {
                let slope = x1 > 0 ? y1 / x1 : (y1 == 0 && x2 > 0 ? y2 / x2 : 0)
                return slope * x
            }
            if x > 1 {
                let slope = x2 < 1 ? (y2 - 1) / (x2 - 1) : (y2 == 1 && x1 < 1 ? (y1 - 1) / (x1 - 1) : 0)
                return 1 + slope * (x - 1)
            }
        }
        if let curve = curve {
            return curve.function(x)
        }
        return x
    }
}

struct WebTiming: Equatable {
    enum Phase {
        case before, active, after
    }

    struct Computed: Equatable {
        var phase: Phase
        var activeTime: Double?
        var overallProgress: Double?
        var simpleProgress: Double?
        var currentIteration: Double?
        var directedProgress: Double?
        var progress: Double?
    }

    var duration = 0.0
    var delay = 0.0
    var endDelay = 0.0
    var iterations = 1.0
    var iterationStart = 0.0
    var direction = "normal"
    var fill = "none"
    var easing = WebEasing.linear

    var activeDuration: Double {
        if duration == 0 || iterations == 0 {
            return 0
        }
        return duration * iterations
    }

    var endTime: Double {
        return max(delay + activeDuration + endDelay, 0)
    }

    var fillsBackwards: Bool {
        return fill == "backwards" || fill == "both"
    }

    var fillsForwards: Bool {
        return fill == "forwards" || fill == "both"
    }

    func compute(localTime: Double?, forwards: Bool = true) -> Computed? {
        guard let local = localTime, local.isFinite else {
            return nil
        }
        let active = activeDuration
        let end = endTime
        let beforeActive = max(min(delay, end), 0)
        let activeAfter = max(min(delay + active, end), 0)
        let phase: Phase
        if local < beforeActive || (!forwards && local == beforeActive) {
            phase = .before
        } else if local > activeAfter || (forwards && local == activeAfter) {
            phase = .after
        } else {
            phase = .active
        }
        var result = Computed(phase: phase)
        switch phase {
        case .before:
            result.activeTime = fillsBackwards ? max(local - delay, 0) : nil
        case .active:
            result.activeTime = local - delay
        case .after:
            result.activeTime = fillsForwards ? max(min(local - delay, active), 0) : nil
        }
        guard let activeTime = result.activeTime else {
            return result
        }
        var overall: Double
        if duration == 0 {
            overall = phase == .before ? 0 : iterations
        } else {
            overall = activeTime / duration
        }
        overall += iterationStart
        result.overallProgress = overall
        var simple = overall.isInfinite ? iterationStart.truncatingRemainder(dividingBy: 1) : overall.truncatingRemainder(dividingBy: 1)
        if simple == 0 && phase != .before && activeTime == active && iterations != 0 {
            simple = 1
        }
        result.simpleProgress = simple
        let iteration: Double
        if phase == .after && iterations.isInfinite {
            iteration = .infinity
        } else if simple == 1 {
            iteration = floor(overall) - 1
        } else {
            iteration = floor(overall)
        }
        result.currentIteration = iteration
        var forwardsDirection = true
        switch direction {
        case "reverse":
            forwardsDirection = false
        case "alternate", "alternate-reverse":
            if iteration.isFinite {
                var d = iteration
                if direction == "alternate-reverse" {
                    d += 1
                }
                forwardsDirection = d.truncatingRemainder(dividingBy: 2) == 0
            }
        default:
            break
        }
        let directed = forwardsDirection ? simple : 1 - simple
        result.directedProgress = directed
        let beforeFlag = (forwardsDirection && phase == .before) || (!forwardsDirection && phase == .after)
        result.progress = easing.apply(directed, before: beforeFlag)
        return result
    }
}

struct WebKeyframe {
    let offset: Double
    let easing: WebEasing
    let composite: String?
    let values: [(name: String, value: String)]
}

struct WebAnimation {
    let aid: Int
    let target: Int
    let keyframes: [WebKeyframe]
    let timing: WebTiming
    let composite: String
    let iterationComposite: String
    let startTime: CFTimeInterval?
    let holdTime: Double?
    let rate: Double
    let properties: [String]
    let signature: String

    static let compositable: Set<String> = ["transform", "opacity"]
    static let maxKeyframes = 64
    static let maxValues = 64
    static let maxTime = 86_400.0
    static let maxTimeMilliseconds = maxTime * 1000

    var isRunning: Bool {
        return holdTime == nil && startTime != nil && rate != 0
    }

    func localTime(_ now: CFTimeInterval) -> Double? {
        if let hold = holdTime {
            return hold
        }
        guard let start = startTime else {
            return nil
        }
        return (now * 1000 - start) * rate
    }

    func computed(_ now: CFTimeInterval) -> WebTiming.Computed? {
        return timing.compute(localTime: localTime(now), forwards: rate >= 0)
    }

    func inEffect(_ now: CFTimeInterval) -> Bool {
        return computed(now)?.activeTime != nil
    }

    func values(progress: Double, iteration: Double, underlying: (String) -> String) -> [(String, String)] {
        var out: [(String, String)] = []
        for property in properties {
            if let value = value(property, progress: progress, iteration: iteration, underlying: underlying) {
                out.append((property, value))
            }
        }
        return out
    }

    func values(at now: CFTimeInterval, underlying: (String) -> String) -> [(String, String)]? {
        guard let state = computed(now), let progress = state.progress else {
            return nil
        }
        return values(progress: progress, iteration: state.currentIteration ?? 0, underlying: underlying)
    }

    private func value(_ property: String, progress: Double, iteration: Double, underlying: (String) -> String) -> String? {
        var frames: [(offset: Double, value: String?, easing: WebEasing, composite: String)] = []
        for frame in keyframes {
            if let value = frame.values.last(where: { $0.name == property })?.value {
                frames.append((frame.offset, value, frame.easing, frame.composite ?? composite))
            }
        }
        if frames.isEmpty {
            return nil
        }
        if !frames.contains(where: { $0.offset == 0 }) {
            frames.insert((0, nil, .linear, "replace"), at: 0)
        }
        if !frames.contains(where: { $0.offset == 1 }) {
            frames.append((1, nil, .linear, "replace"))
        }
        let base = underlying(property)
        let last = frames.last { $0.offset == 1 }?.value
        func resolve(_ index: Int) -> String {
            let frame = frames[index]
            guard var value = frame.value else {
                return base
            }
            if iterationComposite == "accumulate", iteration > 0, iteration.isFinite, let last = last {
                value = WebComposite.accumulate(WebComposite.scaled(last, iteration), value, property: property)
            }
            switch frame.composite {
            case "add":
                return WebComposite.add(base, value, property: property)
            case "accumulate":
                return WebComposite.accumulate(base, value, property: property)
            default:
                return value
            }
        }
        let zeros = frames.indices.filter { frames[$0].offset == 0 }
        let ones = frames.indices.filter { frames[$0].offset == 1 }
        if progress < 0 && zeros.count > 1 {
            return resolve(zeros[0])
        }
        if progress >= 1 && ones.count > 1 {
            return resolve(ones[ones.count - 1])
        }
        let start: Int
        if progress < 0 {
            start = 0
        } else if progress >= 1 {
            start = frames.count - 2
        } else {
            start = frames.indices.last { frames[$0].offset <= progress && frames[$0].offset < 1 } ?? 0
        }
        let end = min(start + 1, frames.count - 1)
        if start == end {
            return resolve(start)
        }
        let a = frames[start], b = frames[end]
        let span = b.offset - a.offset
        let local = span == 0 ? 0 : (progress - a.offset) / span
        let eased = a.easing.apply(local, before: false)
        return CSSInterpolate.mix(resolve(start), resolve(end), eased)
    }

    static func parse(_ object: [String: Any]) -> WebAnimation? {
        guard let aid = (object["aid"] as? NSNumber)?.intValue, let target = (object["target"] as? NSNumber)?.intValue, let rawFrames = object["keyframes"] as? [[String: Any]], rawFrames.count <= maxKeyframes else {
            return nil
        }
        func milliseconds(_ value: Any?, _ fallback: Double) -> Double? {
            guard let value = value, !(value is NSNull) else {
                return fallback
            }
            guard let number = (value as? NSNumber)?.doubleValue, number.isFinite, abs(number) <= maxTime * 1000 else {
                return nil
            }
            return number
        }
        let timingObject = (object["timing"] as? [String: Any]) ?? [:]
        var timing = WebTiming()
        guard let duration = milliseconds(timingObject["duration"], 0), duration >= 0, let delay = milliseconds(timingObject["delay"], 0), let endDelay = milliseconds(timingObject["endDelay"], 0) else {
            return nil
        }
        timing.duration = duration
        timing.delay = delay
        timing.endDelay = endDelay
        if let iterations = timingObject["iterations"], !(iterations is NSNull) {
            guard let count = (iterations as? NSNumber)?.doubleValue, count >= 0, count <= 1e9 else {
                return nil
            }
            timing.iterations = count
        } else if timingObject.keys.contains("iterations") {
            timing.iterations = .infinity
        }
        if let start = (timingObject["iterationStart"] as? NSNumber)?.doubleValue {
            guard start >= 0, start <= 1e9 else {
                return nil
            }
            timing.iterationStart = start
        }
        let direction = (timingObject["direction"] as? String) ?? "normal"
        let fill = (timingObject["fill"] as? String) ?? "none"
        guard ["normal", "reverse", "alternate", "alternate-reverse"].contains(direction), ["none", "forwards", "backwards", "both", "auto"].contains(fill) else {
            return nil
        }
        timing.direction = direction
        timing.fill = fill == "auto" ? "none" : fill
        guard let easing = WebEasing.parse((timingObject["easing"] as? String) ?? "linear") else {
            return nil
        }
        timing.easing = easing
        var frames: [WebKeyframe] = []
        var properties: [String] = []
        var previous = 0.0
        for raw in rawFrames {
            guard let offset = (raw["offset"] as? NSNumber)?.doubleValue, offset >= previous, offset <= 1, let frameEasing = WebEasing.parse((raw["easing"] as? String) ?? "linear") else {
                return nil
            }
            previous = offset
            let composite = raw["composite"] as? String
            guard composite == nil || ["replace", "add", "accumulate"].contains(composite!) else {
                return nil
            }
            let rawValues = (raw["values"] as? [String: Any]) ?? [:]
            guard rawValues.count <= maxValues else {
                return nil
            }
            var values: [(name: String, value: String)] = []
            for name in rawValues.keys.sorted() {
                guard let value = rawValues[name] as? String else {
                    continue
                }
                values.append((name, value))
                if !properties.contains(name) {
                    properties.append(name)
                }
            }
            frames.append(WebKeyframe(offset: offset, easing: frameEasing, composite: composite, values: values))
        }
        let composite = (object["composite"] as? String) ?? "replace"
        let iterationComposite = (object["iterationComposite"] as? String) ?? "replace"
        guard ["replace", "add", "accumulate"].contains(composite), ["replace", "accumulate"].contains(iterationComposite) else {
            return nil
        }
        var startTime: CFTimeInterval?
        if let start = (object["startTime"] as? NSNumber)?.doubleValue {
            guard start.isFinite else {
                return nil
            }
            startTime = start
        }
        var holdTime: Double?
        if let hold = (object["holdTime"] as? NSNumber)?.doubleValue {
            guard hold.isFinite, abs(hold) <= 1e12 else {
                return nil
            }
            holdTime = hold
        }
        let rate = (object["playbackRate"] as? NSNumber)?.doubleValue ?? 1
        guard rate.isFinite, abs(rate) <= 1000, startTime != nil || holdTime != nil else {
            return nil
        }
        let signature = "\(aid)|\(target)|\(timing.duration)|\(timing.delay)|\(timing.endDelay)|\(timing.iterations)|\(timing.iterationStart)|\(timing.direction)|\(timing.fill)|\(timing.easing.text)|\(composite)|\(iterationComposite)|\(startTime ?? -1)|\(holdTime ?? -1)|\(rate)|" + frames.map { frame in "\(frame.offset)/\(frame.easing.text)/\(frame.composite ?? "")/" + frame.values.map { $0.name + ":" + $0.value }.joined(separator: ";") }.joined(separator: ",")
        return WebAnimation(aid: aid, target: target, keyframes: frames, timing: timing, composite: composite, iterationComposite: iterationComposite, startTime: startTime, holdTime: holdTime, rate: rate, properties: properties, signature: signature)
    }
}

enum WebComposite {
    private enum Token: Equatable {
        case number(Double, String)
        case text(String)
    }

    private static func tokenize(_ text: String) -> [Token] {
        var tokens: [Token] = []
        let scalars = Array(text.unicodeScalars)
        var pending = ""
        var i = 0
        func isDigit(_ c: Unicode.Scalar) -> Bool {
            return c.value >= 48 && c.value <= 57
        }
        func isLetter(_ c: Unicode.Scalar) -> Bool {
            return CharacterSet.letters.contains(c)
        }
        while i < scalars.count {
            let c = scalars[i]
            let next: Unicode.Scalar? = i + 1 < scalars.count ? scalars[i + 1] : nil
            let previous: Unicode.Scalar? = i > 0 ? scalars[i - 1] : nil
            let starts = isDigit(c) || (c == "." && next.map(isDigit) == true) || ((c == "-" || c == "+") && next.map { isDigit($0) || $0 == "." } == true)
            let inWord = previous.map { isLetter($0) || $0 == "#" || ($0 == "-" && c != "-") } ?? false
            if starts && !inWord {
                var j = i + 1
                while j < scalars.count, isDigit(scalars[j]) || scalars[j] == "." || ((scalars[j] == "e" || scalars[j] == "E") && j + 1 < scalars.count && (isDigit(scalars[j + 1]) || scalars[j + 1] == "-")) {
                    j += scalars[j] == "e" || scalars[j] == "E" ? 2 : 1
                }
                var k = j
                while k < scalars.count, isLetter(scalars[k]) || scalars[k] == "%" {
                    k += 1
                }
                if let value = Double(String(String.UnicodeScalarView(scalars[i..<j]))) {
                    if !pending.isEmpty {
                        tokens.append(.text(pending))
                        pending = ""
                    }
                    tokens.append(.number(value, String(String.UnicodeScalarView(scalars[j..<k]))))
                    i = k
                    continue
                }
            }
            pending.unicodeScalars.append(c)
            i += 1
        }
        if !pending.isEmpty {
            tokens.append(.text(pending))
        }
        return tokens
    }

    private static func format(_ value: Double) -> String {
        if value == value.rounded(), abs(value) < 1e9 {
            return String(Int(value))
        }
        let text = String(format: "%.6f", value).replacingOccurrences(of: "0+$", with: "", options: .regularExpression)
        return text.hasSuffix(".") ? String(text.dropLast()) : text
    }

    private static func render(_ tokens: [Token]) -> String {
        return tokens.map { token -> String in
            switch token {
            case let .number(value, unit):
                return format(value) + unit
            case let .text(text):
                return text
            }
        }.joined()
    }

    private static func isTransform(_ property: String) -> Bool {
        return property == "transform"
    }

    private static let unitScale: Set<String> = ["scale", "scalex", "scaley", "scale3d"]

    private static func combine(_ a: String, _ b: String, property: String, scaleAware: Bool) -> String? {
        let left = tokenize(CSSInterpolate.normalizeColors(a)), right = tokenize(CSSInterpolate.normalizeColors(b))
        guard left.count == right.count else {
            return nil
        }
        var out: [Token] = []
        var function = ""
        var colorChannel = -1
        for (x, y) in zip(left, right) {
            switch (x, y) {
            case let (.text(p), .text(q)) where p.lowercased() == q.lowercased():
                out.append(.text(p))
                if let open = p.lastIndex(of: "(") {
                    function = p[..<open].split(whereSeparator: { $0 == " " || $0 == "," }).last.map { String($0).lowercased() } ?? ""
                    colorChannel = function == "rgba" ? 0 : -1
                } else if colorChannel >= 0 {
                    colorChannel += 1
                }
            case let (.number(p, unitP), .number(q, unitQ)) where unitP == unitQ || p == 0 || q == 0:
                var value = p + q
                if scaleAware && unitScale.contains(function) {
                    value = p + q - 1
                }
                if colorChannel >= 0 {
                    value = colorChannel < 3 ? min(255, max(0, value)) : min(1, max(0, value))
                }
                out.append(.number(value, unitP.isEmpty ? unitQ : unitP))
            default:
                return nil
            }
        }
        return render(out)
    }

    static func add(_ underlying: String, _ value: String, property: String) -> String {
        if isTransform(property) {
            let u = underlying.trimmingCharacters(in: .whitespaces), v = value.trimmingCharacters(in: .whitespaces)
            if u.isEmpty || u.lowercased() == "none" {
                return v
            }
            if v.isEmpty || v.lowercased() == "none" {
                return u
            }
            return u + " " + v
        }
        return combine(underlying, value, property: property, scaleAware: false) ?? value
    }

    static func accumulate(_ underlying: String, _ value: String, property: String) -> String {
        if isTransform(property) {
            let u = underlying.trimmingCharacters(in: .whitespaces), v = value.trimmingCharacters(in: .whitespaces)
            if u.isEmpty || u.lowercased() == "none" {
                return v
            }
            if v.isEmpty || v.lowercased() == "none" {
                return u
            }
            return combine(u, v, property: property, scaleAware: true) ?? (u + " " + v)
        }
        return combine(underlying, value, property: property, scaleAware: false) ?? value
    }

    static func scaled(_ value: String, _ factor: Double) -> String {
        var function = ""
        return render(tokenize(CSSInterpolate.normalizeColors(value)).map { token in
            switch token {
            case let .text(text):
                if let open = text.lastIndex(of: "(") {
                    function = text[..<open].split(whereSeparator: { $0 == " " || $0 == "," }).last.map { String($0).lowercased() } ?? ""
                }
                return token
            case let .number(number, unit):
                if unitScale.contains(function) {
                    return .number((number - 1) * factor + 1, unit)
                }
                return .number(number * factor, unit)
            }
        })
    }
}

final class WidgetWebAnimations {
    static let maxAnimations = 1024

    private(set) var byAid: [Int: WebAnimation] = [:]
    private(set) var byTarget: [Int: [WebAnimation]] = [:]
    var clock: () -> CFTimeInterval = { CACurrentMediaTime() }
    var isAlive: (Int) -> Bool = { _ in true }

    var isEmpty: Bool {
        return byAid.isEmpty
    }

    var targets: Set<Int> {
        return Set(byTarget.keys)
    }

    func apply(_ message: [String: Any]) {
        guard let items = message["anims"] as? [[String: Any]] else {
            return
        }
        for item in items.prefix(4096) {
            guard let aid = (item["aid"] as? NSNumber)?.intValue else {
                continue
            }
            if item["css"] != nil {
                continue
            }
            if item["remove"] as? Bool == true {
                byAid[aid] = nil
                continue
            }
            guard let animation = WebAnimation.parse(item) else {
                byAid[aid] = nil
                continue
            }
            if byAid[aid] == nil && byAid.count >= WidgetWebAnimations.maxAnimations {
                continue
            }
            byAid[aid] = animation
        }
        rebuild()
    }

    func removeAll() {
        byAid.removeAll()
        byTarget.removeAll()
    }

    func prune(_ alive: (Int) -> Bool) {
        let before = byAid.count
        byAid = byAid.filter { alive($0.value.target) }
        if byAid.count != before {
            rebuild()
        }
    }

    private func rebuild() {
        var grouped: [Int: [WebAnimation]] = [:]
        for animation in byAid.values {
            grouped[animation.target, default: []].append(animation)
        }
        for key in grouped.keys {
            grouped[key]?.sort { $0.aid < $1.aid }
        }
        byTarget = grouped
    }

    func animations(_ id: Int) -> [WebAnimation] {
        return byTarget[id] ?? []
    }

    func sample(into overrides: inout [Int: [(String, String)]], underlying: (Int, String) -> String) {
        let now = clock()
        for (id, list) in byTarget where isAlive(id) {
            for animation in list {
                guard let values = animation.values(at: now, underlying: { property in
                    overrides[id]?.last { $0.0 == property }?.1 ?? underlying(id, property)
                }) else {
                    continue
                }
                for value in values {
                    overrides[id, default: []].removeAll { $0.0 == value.0 }
                    overrides[id, default: []].append(value)
                }
            }
        }
    }

    func values(_ id: Int, at now: CFTimeInterval, underlying: (String) -> String) -> [(String, String)] {
        var out: [(String, String)] = []
        for animation in animations(id) {
            guard let values = animation.values(at: now, underlying: { property in out.last { $0.0 == property }?.1 ?? underlying(property) }) else {
                continue
            }
            for value in values {
                out.removeAll { $0.0 == value.0 }
                out.append(value)
            }
        }
        return out
    }

    func animatedIDs() -> Set<Int> {
        let now = clock()
        var ids = Set<Int>()
        for (id, list) in byTarget where isAlive(id) && list.contains(where: { $0.inEffect(now) || $0.isRunning && WidgetWebAnimations.pendingEffect($0, now) }) {
            ids.insert(id)
        }
        return ids
    }

    private static func pendingEffect(_ animation: WebAnimation, _ now: CFTimeInterval) -> Bool {
        guard let state = animation.computed(now) else {
            return false
        }
        return animation.rate > 0 ? state.phase == .before : state.phase == .after
    }

    func needsFrames(excluding driven: Set<Int>) -> Bool {
        let now = clock()
        for (id, list) in byTarget where !driven.contains(id) && isAlive(id) {
            for animation in list where animation.isRunning {
                guard let state = animation.computed(now) else {
                    continue
                }
                if state.phase == .active || WidgetWebAnimations.pendingEffect(animation, now) {
                    return true
                }
            }
        }
        return false
    }

    func onlyCompositable(_ id: Int) -> Bool {
        return animations(id).allSatisfy { $0.properties.allSatisfy { WebAnimation.compositable.contains($0) } }
    }

    func reachableValues(_ id: Int, base: [String: String], samples: Int = 48) -> [(transform: String?, opacity: String?)] {
        var out: [(String?, String?)] = []
        let list = animations(id)
        let underlying: (String) -> String = { base[$0] ?? WidgetAnimator.initialValues[$0] ?? "" }
        for animation in list {
            let timing = animation.timing
            let iterations = animation.iterationComposite == "accumulate" ? max(1, min(ceil(timing.iterations + timing.iterationStart), 64)) : 1
            let steps = samples * Int(iterations)
            for step in 0...steps {
                let position = Double(step) / Double(samples)
                let iteration = floor(position)
                var progress = position - iteration
                if step == steps {
                    progress = 1
                }
                let eased = timing.easing.apply(progress, before: false)
                let values = animation.values(progress: eased, iteration: min(iteration, iterations - 1), underlying: underlying)
                out.append((values.last { $0.0 == "transform" }?.1, values.last { $0.0 == "opacity" }?.1))
            }
        }
        return out
    }

    func endTime(_ id: Int) -> CFTimeInterval? {
        let now = clock()
        var best: CFTimeInterval?
        for animation in animations(id) where animation.isRunning {
            guard let start = animation.startTime else {
                continue
            }
            let timing = animation.timing
            var marks = [timing.delay, timing.delay + timing.activeDuration, timing.endTime]
            if animation.rate < 0 {
                marks.append(0)
            }
            for mark in marks where mark.isFinite {
                let wall = (start + mark / animation.rate) / 1000
                if wall > now, best.map({ wall < $0 }) ?? true {
                    best = wall
                }
            }
        }
        return best
    }

    struct Bake {
        let properties: Set<String>
        let signature: String
        let value: (Double) -> (transform: String?, opacity: String?)
        let begin: CFTimeInterval?
        let cycle: Double
        let repeatCount: Float
        let speed: Float
        let caBackwards: Bool
        let caForwards: Bool
    }

    func bakes(_ id: Int, base: [String: String], hasOtherAnimations: Bool) -> [Bake]? {
        let list = animations(id)
        if list.isEmpty || hasOtherAnimations {
            return nil
        }
        var owners: [String: WebAnimation] = [:]
        for animation in list {
            for property in animation.properties {
                guard WebAnimation.compositable.contains(property), owners[property] == nil else {
                    return nil
                }
                owners[property] = animation
            }
        }
        var bakes: [Bake] = []
        for animation in list {
            guard let bake = WidgetWebAnimations.bake(animation, base: base) else {
                return nil
            }
            bakes.append(bake)
        }
        return bakes
    }

    static func bake(_ animation: WebAnimation, base: [String: String]) -> Bake? {
        guard animation.isRunning, let start = animation.startTime else {
            return nil
        }
        let underlying: (String) -> String = { base[$0] ?? WidgetAnimator.initialValues[$0] ?? "" }
        for frame in animation.keyframes {
            let composite = frame.composite ?? animation.composite
            if composite != "replace" && frame.values.contains(where: { underlying($0.name) != WidgetAnimator.initialValues[$0.name] }) {
                return nil
            }
        }
        var timing = animation.timing
        if timing.endDelay < 0 || timing.duration <= 0 {
            return nil
        }
        let fillsBackwards = timing.fillsBackwards
        let fillsForwards = timing.fillsForwards
        if let (_, jump) = timing.easing.steps, jump == .start || jump == .both, (fillsBackwards && animation.rate > 0) || (fillsForwards && animation.rate < 0) {
            return nil
        }
        timing.fill = "both"
        let rate = animation.rate
        let properties = Set(animation.properties)
        let active = timing.activeDuration
        func sample(_ timing: WebTiming, _ activeTime: Double) -> (transform: String?, opacity: String?) {
            let span = timing.activeDuration
            let clamped = span.isFinite ? min(max(activeTime, 0), span) : max(activeTime, 0)
            guard let state = timing.compute(localTime: timing.delay + clamped, forwards: true), let progress = state.progress else {
                return (nil, nil)
            }
            let values = animation.values(progress: progress, iteration: state.currentIteration ?? 0, underlying: underlying)
            return (values.last { $0.0 == "transform" }?.1, values.last { $0.0 == "opacity" }?.1)
        }
        let signature = animation.signature
        if rate > 0 && animation.iterationComposite == "replace" {
            var cycle = timing
            cycle.iterations = timing.direction.hasPrefix("alternate") ? 2 : 1
            let period = cycle.activeDuration
            let count = active.isFinite ? active / period : Double.infinity
            return Bake(properties: properties, signature: signature, value: { sample(cycle, $0 * period) }, begin: (start + timing.delay / rate) / 1000, cycle: period / 1000, repeatCount: count.isFinite ? Float(count) : .infinity, speed: Float(rate), caBackwards: fillsBackwards, caForwards: fillsForwards)
        }
        guard active.isFinite, active > 0, active / timing.duration <= 64 else {
            return nil
        }
        if rate > 0 {
            return Bake(properties: properties, signature: signature, value: { sample(timing, $0 * active) }, begin: (start + timing.delay / rate) / 1000, cycle: active / 1000, repeatCount: 1, speed: Float(rate), caBackwards: fillsBackwards, caForwards: fillsForwards)
        }
        return Bake(properties: properties, signature: signature, value: { sample(timing, active - $0 * active) }, begin: (start + (timing.delay + active) / rate) / 1000, cycle: active / 1000, repeatCount: 1, speed: Float(-rate), caBackwards: fillsForwards, caForwards: fillsBackwards)
    }
}

struct CSSOverride {
    let origin: CFTimeInterval
    var start: CFTimeInterval?
    var hold: Double?
    var rate: Double
    var cancelled: Bool

    func local(_ now: CFTimeInterval) -> Double {
        if let hold = hold {
            return hold
        }
        return (now - (start ?? now)) * rate
    }
}

extension WidgetAnimator {
    func pruneCSSOverrides() {
        if cssOverrides.isEmpty {
            return
        }
        for (id, list) in cssOverrides {
            var kept = list
            for (key, override) in list {
                let alive: Bool
                if key.hasPrefix("animation:") {
                    let name = String(key.dropFirst("animation:".count))
                    alive = running[id]?.contains { $0.spec.name == name && abs($0.start - override.origin) < 0.002 } ?? false
                } else {
                    let property = String(key.dropFirst("transition:".count))
                    alive = transitions[id]?[property].map { abs($0.start - override.origin) < 0.002 } ?? override.cancelled
                }
                if !alive {
                    kept[key] = nil
                }
            }
            cssOverrides[id] = kept.isEmpty ? nil : kept
        }
    }

    func cssAnimations(_ ids: [Int], now: CFTimeInterval) -> [[String: Any]] {
        var out: [[String: Any]] = []
        for id in ids {
            for (property, transition) in (transitions[id] ?? [:]).sorted(by: { $0.key < $1.key }) {
                let override = cssOverrides[id]?["transition:" + property]
                if override?.cancelled == true {
                    continue
                }
                out.append(["kind": "transition", "id": id, "name": property, "index": 0, "origin": transition.start * 1000, "currentTime": (override?.local(now) ?? now - transition.start) * 1000, "playbackRate": override?.rate ?? 1, "paused": override?.hold != nil, "duration": transition.spec.duration * 1000, "delay": transition.spec.delay * 1000, "iterations": 1, "direction": "normal", "fill": "backwards", "easing": transition.spec.timingText])
            }
            for (index, item) in (running[id] ?? []).enumerated() where keyframes[item.spec.name] != nil {
                let override = cssOverrides[id]?["animation:" + item.spec.name]
                if override?.cancelled == true {
                    continue
                }
                var entry: [String: Any] = ["kind": "animation", "id": id, "name": item.spec.name, "index": index, "origin": item.start * 1000, "currentTime": (override?.local(now) ?? item.localTime(now)) * 1000, "playbackRate": override?.rate ?? 1, "paused": override.map { $0.hold != nil } ?? item.spec.paused, "duration": item.spec.duration * 1000, "delay": item.spec.delay * 1000, "direction": item.spec.direction, "fill": item.spec.fill, "easing": item.spec.timingText]
                entry["iterations"] = item.spec.iterations.isFinite ? item.spec.iterations : NSNull()
                out.append(entry)
            }
        }
        return out
    }

    func applyCSSOverride(_ record: [String: Any], offset: CFTimeInterval) {
        guard let id = (record["target"] as? NSNumber)?.intValue, let css = record["css"] as? [String: Any], let kind = css["kind"] as? String, kind == "animation" || kind == "transition", let name = css["name"] as? String, let origin = (css["origin"] as? NSNumber)?.doubleValue else {
            return
        }
        let key = kind + ":" + name
        if record["remove"] as? Bool == true {
            cssOverrides[id]?[key] = nil
            if cssOverrides[id]?.isEmpty == true {
                cssOverrides[id] = nil
            }
            return
        }
        let rate = (record["playbackRate"] as? NSNumber)?.doubleValue ?? 1
        guard rate.isFinite, abs(rate) <= 1000 else {
            return
        }
        let start = (record["startTime"] as? NSNumber).map { $0.doubleValue / 1000 - offset }
        let hold = (record["holdTime"] as? NSNumber).map { $0.doubleValue / 1000 }
        let cancelled = record["cancelled"] as? Bool == true
        guard cancelled || start != nil || hold != nil, start?.isFinite ?? true, hold?.isFinite ?? true else {
            return
        }
        if cssOverrides[id] == nil && cssOverrides.count >= WidgetWebAnimations.maxAnimations {
            return
        }
        cssOverrides[id, default: [:]][key] = CSSOverride(origin: origin / 1000, start: start, hold: hold, rate: rate, cancelled: cancelled)
    }
}
