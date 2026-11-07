import Foundation

extension NativeValidator {
    static let maxAnimRecords = 4096
    static let maxAnimKeyframes = 64
    static let maxAnimValues = 64
    static let maxAnimTime = 86_400_000.0
    static let animDirections: Set<String> = ["normal", "reverse", "alternate", "alternate-reverse"]
    static let animFills: Set<String> = ["none", "forwards", "backwards", "both"]
    static let animComposites: Set<String> = ["replace", "add", "accumulate"]
    static let animIterationComposites: Set<String> = ["replace", "accumulate"]

    private static let animEasingPattern: NSRegularExpression = {
        let num = "[+-]?(?:[0-9]+\\.?[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"
        let space = "[\\t\\n\\f\\r \\u000b\\u00a0\\u1680\\u2000-\\u200a\\u2028\\u2029\\u202f\\u205f\\u3000\\ufeff]"
        let pattern = "\\A(?:linear|ease|ease-in|ease-out|ease-in-out|step-start|step-end|cubic-bezier\\(S*N S*,S*N S*,S*N S*,S*N S*\\)|steps\\(S*[0-9]{1,6}S*(?:,S*(?:start|end|jump-start|jump-end|jump-both|jump-none)S*)?\\)|linear\\((?:[0-9eE.%,+-]|S){1,8192}\\))\\z"
            .replacingOccurrences(of: "N ", with: num)
            .replacingOccurrences(of: "S", with: space)
        return try! NSRegularExpression(pattern: pattern)
    }()
    private static let animPropertyPattern = try! NSRegularExpression(pattern: "\\A(?:--[A-Za-z0-9_-]{1,128}|-?[a-z][a-z-]{0,63})\\z")
    private static let animBlockedPattern = try! NSRegularExpression(pattern: "\\A(?:-webkit-)?(?:animation|transition)(?:-|\\z)")
    /// Keyframe members, not CSS properties (validator.ts `KEYFRAME_MEMBERS`).
    private static let keyframeMembers: Set<String> = ["offset", "easing", "composite", "computed-offset"]
    private static let cssNamePattern = try! NSRegularExpression(pattern: "\\A[A-Za-z0-9_-]{1,128}\\z")

    private static func matches(_ re: NSRegularExpression, _ s: String) -> Bool {
        re.firstMatch(in: s, range: NSRange(location: 0, length: s.utf16.count)) != nil
    }

    static func animEasing(_ v: JSONValue?) -> String? {
        guard case .string(let e)? = v, e.utf16.count <= 8192, matches(animEasingPattern, e), e.split(separator: ",", omittingEmptySubsequences: false).count <= 257 else { return nil }
        return e
    }

    private static func finite(_ v: JSONValue?, _ max: Double) -> Double? {
        guard case .number(let n)? = v, n.isFinite, abs(n) <= max else { return nil }
        return n
    }

    private static func isNull(_ v: JSONValue?) -> Bool {
        if case .null? = v { return true }
        return false
    }

    private static func string(in set: Set<String>, _ v: JSONValue?) -> String? {
        guard case .string(let s)? = v, set.contains(s) else { return nil }
        return s
    }

    private static func object(_ pairs: [(String, JSONValue)]) -> JSONValue {
        var o = JSONObject()
        for (k, v) in pairs { o.set(k, v) }
        return .object(o)
    }

    private func renderedTarget(_ v: JSONValue?) -> Int? {
        guard case .number(let d)? = v, d.isFinite, d == d.rounded(.towardZero), d > Double(Self.documentId), d < Double(Self.maxId) else { return nil }
        let id = Int(d)
        return renderedTag(id) == nil ? nil : id
    }

    func validateAnim(json value: JSONValue) -> JSONValue {
        guard case .array(let list) = value, list.count <= Self.maxAnimRecords else {
            diag("protocol-violation", "anim batch is not an array or too large")
            return .array([])
        }
        var out: [JSONValue] = []
        func drop(_ aid: Double) {
            if liveAnims.remove(aid) != nil { out.append(Self.object([("aid", .number(aid)), ("remove", .bool(true))])) }
        }
        for item in list {
            guard case .object(let a) = item, case .number(let aid)? = a["aid"], aid.isFinite, aid == aid.rounded(.towardZero), aid >= 1, aid <= 2147483647 else {
                diag("protocol-violation", "rejected anim record")
                continue
            }
            if case .bool(true)? = a["remove"] {
                drop(aid)
                continue
            }
            if let css = a["css"] {
                guard let target = renderedTarget(a["target"]), case .object(let c) = css,
                      let kind = Self.string(in: ["animation", "transition"], c["kind"]),
                      case .string(let name)? = c["name"], Self.matches(Self.cssNamePattern, name),
                      let origin = Self.finite(c["origin"], 1e15),
                      Self.isNull(a["startTime"]) || Self.finite(a["startTime"], 1e15) != nil,
                      Self.isNull(a["holdTime"]) || Self.finite(a["holdTime"], 1e12) != nil,
                      let rate = Self.finite(a["playbackRate"], 1000),
                      case .bool(let cancelled)? = a["cancelled"] else {
                    diag("protocol-violation", "rejected css anim record")
                    continue
                }
                out.append(Self.object([
                    ("aid", .number(aid)), ("target", .number(Double(target))),
                    ("css", Self.object([("kind", .string(kind)), ("name", .string(name)), ("origin", .number(origin))])),
                    ("startTime", a["startTime"]!), ("holdTime", a["holdTime"]!), ("playbackRate", .number(rate)), ("cancelled", .bool(cancelled)),
                ]))
                continue
            }
            let target = renderedTarget(a["target"])
            var timing: [(String, JSONValue)] = []
            var record: (keyframes: [JSONValue], composite: String, iterationComposite: String, rate: Double)?
            if let target, case .array(let keyframes)? = a["keyframes"], keyframes.count <= Self.maxAnimKeyframes, case .object(let t)? = a["timing"],
               let duration = Self.finite(t["duration"], Self.maxAnimTime), duration >= 0,
               let delay = Self.finite(t["delay"], Self.maxAnimTime), let endDelay = Self.finite(t["endDelay"], Self.maxAnimTime),
               Self.isNull(t["iterations"]) || (Self.finite(t["iterations"], 1e9).map { $0 >= 0 } ?? false),
               let iterationStart = Self.finite(t["iterationStart"], 1e9), iterationStart >= 0,
               let direction = Self.string(in: Self.animDirections, t["direction"]), let fill = Self.string(in: Self.animFills, t["fill"]),
               let easing = Self.animEasing(t["easing"]),
               let composite = Self.string(in: Self.animComposites, a["composite"]), let iterationComposite = Self.string(in: Self.animIterationComposites, a["iterationComposite"]),
               Self.isNull(a["startTime"]) || Self.finite(a["startTime"], 1e15) != nil,
               Self.isNull(a["holdTime"]) || Self.finite(a["holdTime"], 1e12) != nil,
               !(Self.isNull(a["startTime"]) && Self.isNull(a["holdTime"])),
               let rate = Self.finite(a["playbackRate"], 1000) {
                _ = target
                timing = [
                    ("duration", .number(duration)), ("delay", .number(delay)), ("endDelay", .number(endDelay)), ("iterations", t["iterations"]!),
                    ("iterationStart", .number(iterationStart)), ("direction", .string(direction)), ("fill", .string(fill)), ("easing", .string(easing)),
                ]
                record = (keyframes, composite, iterationComposite, rate)
            }
            guard let target, let r = record else {
                if target != nil { diag("protocol-violation", "rejected anim record") }
                drop(aid)
                continue
            }
            var frames: [JSONValue] = []
            var last = 0.0
            var bad = false
            for f in r.keyframes {
                guard case .object(let frame) = f, let offset = Self.finite(frame["offset"], 1), offset >= last, offset <= 1,
                      let easing = Self.animEasing(frame["easing"]),
                      Self.isNull(frame["composite"]) || Self.string(in: Self.animComposites, frame["composite"]) != nil,
                      case .object(let values)? = frame["values"] else {
                    bad = true
                    break
                }
                last = offset
                if values.count > Self.maxAnimValues {
                    bad = true
                    break
                }
                var clean = JSONObject()
                for (name, value) in values.entries {
                    guard case .string(let text) = value, text.utf16.count <= 4096, Self.matches(Self.animPropertyPattern, name), !Self.matches(Self.animBlockedPattern, name), !Self.keyframeMembers.contains(name) else { continue }
                    if let v = oracle?.styleProperty(name, text), !v.isEmpty { clean.set(name, .string(v)) }
                }
                frames.append(Self.object([("offset", .number(offset)), ("easing", .string(easing)), ("composite", frame["composite"]!), ("values", .object(clean))]))
            }
            if bad {
                diag("protocol-violation", "rejected anim keyframes")
                drop(aid)
                continue
            }
            // The policy's cap (`tables.liveAnims`: 64 strict, 1024 compat): further ones are dropped, not a stop.
            if !liveAnims.contains(aid), liveAnims.count >= tables.maxLiveAnims {
                diag("animation-limit", "more than \(tables.maxLiveAnims) live script animations")
                continue
            }
            liveAnims.insert(aid)
            out.append(Self.object([
                ("aid", .number(aid)), ("target", .number(Double(target))), ("keyframes", .array(frames)), ("timing", Self.object(timing)),
                ("composite", .string(r.composite)), ("iterationComposite", .string(r.iterationComposite)),
                ("startTime", a["startTime"]!), ("holdTime", a["holdTime"]!), ("playbackRate", .number(r.rate)),
            ]))
        }
        return .array(out)
    }
}
