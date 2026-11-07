import AppKit
import LiteHTML
import WidgetSandboxProtocol

enum CSSTransform {
    private static let translatePrefix = Array("translate(".utf8)

    static func translation(_ text: String) -> CGAffineTransform? {
        let bytes = Array(text.utf8)
        let end = bytes.count - 1
        guard bytes.count < 96, end > translatePrefix.count, bytes.starts(with: translatePrefix), bytes[end] == 0x29 else {
            return nil
        }
        var values: [CGFloat] = []
        var index = translatePrefix.count
        while index < end && values.count < 2 {
            while index < end && bytes[index] == 0x20 {
                index += 1
            }
            let start = index
            while index < end, (0x30...0x39).contains(bytes[index]) || bytes[index] == 0x2E || bytes[index] == 0x2D || bytes[index] == 0x2B {
                index += 1
            }
            guard index > start, index + 1 < end, bytes[index] == 0x70, bytes[index + 1] == 0x78, let value = Double(String(decoding: bytes[start..<index], as: UTF8.self)) else {
                return nil
            }
            values.append(CGFloat(value))
            index += 2
            while index < end && bytes[index] == 0x20 {
                index += 1
            }
            if index < end {
                guard bytes[index] == 0x2C else {
                    return nil
                }
                index += 1
            }
        }
        guard index == end, !values.isEmpty, bytes[end - 1] != 0x2C else {
            return nil
        }
        return CGAffineTransform(translationX: values[0], y: values.count > 1 ? values[1] : 0)
    }

    static func needsResolving(_ text: String) -> Bool {
        var window: UInt32 = 0
        for byte in text.utf8 {
            if byte == 0x28 && (window & 0xFFFFFF == 0x766172 || window == 0x63616C63) {
                return true
            }
            window = window << 8 | UInt32(byte)
        }
        return false
    }

    static func parse(_ text: String?, box: CGSize) -> CGAffineTransform? {
        if let text = text, let translation = translation(text) {
            return translation
        }
        guard let text = text?.trimmingCharacters(in: .whitespaces), !text.isEmpty, text.lowercased() != "none" else {
            return .identity
        }
        var result = CGAffineTransform.identity
        var rest = Substring(text)
        while let open = rest.firstIndex(of: "(") {
            guard let close = rest[open...].firstIndex(of: ")") else {
                return nil
            }
            let name = rest[..<open].trimmingCharacters(in: .whitespaces).lowercased()
            let args = rest[rest.index(after: open)..<close].split(whereSeparator: { $0 == "," || $0 == " " }).map(String.init)
            guard let function = make(name, args, box: box) else {
                return nil
            }
            result = function.concatenating(result)
            rest = rest[rest.index(after: close)...]
        }
        if !rest.trimmingCharacters(in: .whitespaces).isEmpty {
            return nil
        }
        return result
    }

    private static func length(_ text: String, relative: CGFloat) -> CGFloat? {
        let t = text.lowercased()
        if t.hasSuffix("px"), let v = Double(t.dropLast(2)) {
            return CGFloat(v)
        }
        if t.hasSuffix("%"), let v = Double(t.dropLast()) {
            return CGFloat(v) / 100 * relative
        }
        if let v = Double(t), v == 0 {
            return 0
        }
        return nil
    }

    private static func angle(_ text: String) -> CGFloat? {
        let t = text.lowercased()
        if t.hasSuffix("deg"), let v = Double(t.dropLast(3)) {
            return CGFloat(v * .pi / 180)
        }
        if t.hasSuffix("grad"), let v = Double(t.dropLast(4)) {
            return CGFloat(v * .pi / 200)
        }
        if t.hasSuffix("rad"), let v = Double(t.dropLast(3)) {
            return CGFloat(v)
        }
        if t.hasSuffix("turn"), let v = Double(t.dropLast(4)) {
            return CGFloat(v * 2 * .pi)
        }
        if let v = Double(t), v == 0 {
            return 0
        }
        return nil
    }

    private static func make(_ name: String, _ args: [String], box: CGSize) -> CGAffineTransform? {
        func number(_ i: Int) -> CGFloat? {
            return i < args.count ? Double(args[i]).map { CGFloat($0) } : nil
        }
        switch name {
        case "translate":
            guard let x = args.first.flatMap({ length($0, relative: box.width) }) else {
                return nil
            }
            let y = args.count > 1 ? length(args[1], relative: box.height) : 0
            return y.map { CGAffineTransform(translationX: x, y: $0) }
        case "translate3d":
            guard args.count == 3, let x = length(args[0], relative: box.width), let y = length(args[1], relative: box.height), length(args[2], relative: 0) != nil else {
                return nil
            }
            return CGAffineTransform(translationX: x, y: y)
        case "translatez":
            return args.count == 1 && length(args[0], relative: 0) != nil ? .identity : nil
        case "scale3d":
            guard args.count == 3, let x = number(0), let y = number(1), number(2) != nil else {
                return nil
            }
            return CGAffineTransform(scaleX: x, y: y)
        case "translatex":
            return args.first.flatMap { length($0, relative: box.width) }.map { CGAffineTransform(translationX: $0, y: 0) }
        case "translatey":
            return args.first.flatMap { length($0, relative: box.height) }.map { CGAffineTransform(translationX: 0, y: $0) }
        case "scale":
            guard let x = number(0) else {
                return nil
            }
            return CGAffineTransform(scaleX: x, y: number(1) ?? x)
        case "scalex":
            return number(0).map { CGAffineTransform(scaleX: $0, y: 1) }
        case "scaley":
            return number(0).map { CGAffineTransform(scaleX: 1, y: $0) }
        case "rotate", "rotatez":
            return args.first.flatMap(angle).map { CGAffineTransform(rotationAngle: $0) }
        case "skew":
            guard let x = args.first.flatMap(angle) else {
                return nil
            }
            let y = args.count > 1 ? angle(args[1]) ?? 0 : 0
            return CGAffineTransform(a: 1, b: tan(y), c: tan(x), d: 1, tx: 0, ty: 0)
        case "skewx":
            return args.first.flatMap(angle).map { CGAffineTransform(a: 1, b: 0, c: tan($0), d: 1, tx: 0, ty: 0) }
        case "skewy":
            return args.first.flatMap(angle).map { CGAffineTransform(a: 1, b: tan($0), c: 0, d: 1, tx: 0, ty: 0) }
        case "matrix":
            let v = args.compactMap { Double($0).map { CGFloat($0) } }
            return v.count == 6 ? CGAffineTransform(a: v[0], b: v[1], c: v[2], d: v[3], tx: v[4], ty: v[5]) : nil
        default:
            return nil
        }
    }

    static func resolve(_ text: String?, variable: (String) -> String?) -> String? {
        guard var text = text else {
            return nil
        }
        var depth = 0
        while let start = text.range(of: "var(") {
            depth += 1
            guard depth <= 16, let close = matching(text, from: start.upperBound) else {
                return nil
            }
            let inner = String(text[start.upperBound..<close])
            let parts = WidgetNode.splitTopLevel(inner, separator: ",")
            let name = parts.first?.trimmingCharacters(in: .whitespaces) ?? ""
            guard name.hasPrefix("--"), let value = variable(name) ?? (parts.count > 1 ? parts.dropFirst().joined(separator: ",").trimmingCharacters(in: .whitespaces) : nil) else {
                return nil
            }
            text.replaceSubrange(start.lowerBound...close, with: value)
            if text.count > 4096 {
                return nil
            }
        }
        while let start = text.range(of: "calc(") {
            guard let close = matching(text, from: start.upperBound), let value = Calc.evaluate(String(text[start.upperBound..<close])) else {
                return nil
            }
            text.replaceSubrange(start.lowerBound...close, with: value)
        }
        return text
    }

    private static func matching(_ text: String, from index: String.Index) -> String.Index? {
        var depth = 1
        var i = index
        while i < text.endIndex {
            if text[i] == "(" {
                depth += 1
            } else if text[i] == ")" {
                depth -= 1
                if depth == 0 {
                    return i
                }
            }
            i = text.index(after: i)
        }
        return nil
    }

    static func origin(_ text: String?, box: CGSize) -> CGPoint {
        var x = box.width / 2, y = box.height / 2
        guard let parts = text?.split(separator: " ").map({ String($0).lowercased() }), !parts.isEmpty else {
            return CGPoint(x: x, y: y)
        }
        func resolve(_ part: String, horizontal: Bool) -> CGFloat? {
            let size = horizontal ? box.width : box.height
            switch part {
            case "left", "top":
                return 0
            case "right", "bottom":
                return size
            case "center":
                return size / 2
            default:
                return length(part, relative: size)
            }
        }
        if parts.count == 1 {
            if parts[0] == "top" || parts[0] == "bottom" {
                y = resolve(parts[0], horizontal: false) ?? y
            } else {
                x = resolve(parts[0], horizontal: true) ?? x
            }
        } else {
            let first = parts[0], second = parts[1]
            if first == "top" || first == "bottom" || second == "left" || second == "right" {
                y = resolve(first, horizontal: false) ?? y
                x = resolve(second, horizontal: true) ?? x
            } else {
                x = resolve(first, horizontal: true) ?? x
                y = resolve(second, horizontal: false) ?? y
            }
        }
        return CGPoint(x: x, y: y)
    }
}

enum Calc {
    private struct Value {
        var number: Double
        var unit: String
    }

    static func evaluate(_ text: String) -> String? {
        var tokens: [String] = []
        var current = ""
        for ch in text {
            if "+-*/() ".contains(ch) {
                if !current.isEmpty {
                    tokens.append(current)
                    current = ""
                }
                if ch != " " {
                    if (ch == "-" || ch == "+"), tokens.last.map({ "*/(+-".contains($0) }) ?? true {
                        current.append(ch)
                    } else {
                        tokens.append(String(ch))
                    }
                }
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty {
            tokens.append(current)
        }
        guard tokens.count <= 256 else {
            return nil
        }
        var index = 0
        func atom() -> Value? {
            guard index < tokens.count else {
                return nil
            }
            let token = tokens[index]
            index += 1
            if token == "(" {
                guard let value = sum(), index < tokens.count, tokens[index] == ")" else {
                    return nil
                }
                index += 1
                return value
            }
            let numeric = token.prefix { "0123456789.-+eE".contains($0) }
            guard let number = Double(numeric), number.isFinite else {
                return nil
            }
            let unit = String(token.dropFirst(numeric.count)).lowercased()
            guard ["", "px", "deg", "rad", "turn", "grad", "%"].contains(unit) else {
                return nil
            }
            return Value(number: number, unit: unit)
        }
        func product() -> Value? {
            guard var left = atom() else {
                return nil
            }
            while index < tokens.count, tokens[index] == "*" || tokens[index] == "/" {
                let op = tokens[index]
                index += 1
                guard let right = atom() else {
                    return nil
                }
                if op == "*" {
                    guard left.unit.isEmpty || right.unit.isEmpty else {
                        return nil
                    }
                    left = Value(number: left.number * right.number, unit: left.unit.isEmpty ? right.unit : left.unit)
                } else {
                    guard right.unit.isEmpty, right.number != 0 else {
                        return nil
                    }
                    left = Value(number: left.number / right.number, unit: left.unit)
                }
            }
            return left
        }
        func sum() -> Value? {
            guard var left = product() else {
                return nil
            }
            while index < tokens.count, tokens[index] == "+" || tokens[index] == "-" {
                let op = tokens[index]
                index += 1
                guard let right = product(), left.unit == right.unit || left.number == 0 || right.number == 0 else {
                    return nil
                }
                let unit = left.unit.isEmpty ? right.unit : left.unit
                left = Value(number: op == "+" ? left.number + right.number : left.number - right.number, unit: unit)
            }
            return left
        }
        guard let value = sum(), index == tokens.count, value.number.isFinite else {
            return nil
        }
        return String(format: "%.6f", value.number) + value.unit
    }
}

final class WidgetCompositor {
    private struct Promoted {
        let layer: CALayer
        var box: CGRect
        var origin: CGPoint
        var ink: CGRect
        var effects: WidgetEffectLayers? = nil
        var template: WidgetEffects? = nil
        var ownsEffects: Bool {
            return template != nil
        }
    }

    private var effectKeys: [Int: [(CALayer, String)]] = [:]

    private func removeEffectAnimations(_ id: Int) {
        for (layer, key) in effectKeys[id] ?? [] {
            layer.removeAnimation(forKey: key)
        }
        effectKeys[id] = nil
    }

    static let maxLayers = ProcessInfo.processInfo.environment["TW_MAX_LAYERS"].flatMap { Int($0) } ?? 256
    static let maxPixels = 4096 * 4096
    static let localPaintReuse = ProcessInfo.processInfo.environment["TW_LOCAL_PAINT_REUSE"] != "0"

    private var promoted: [Int: Promoted] = [:]
    private weak var host: CALayer?
    private(set) var promotions = 0
    private static let promotionLock = NSLock()
    private static var promotionTotal = 0
    private static var spaceChanges = 0

    static var totalSpaceChanges: Int {
        promotionLock.lock()
        defer { promotionLock.unlock() }
        return spaceChanges
    }

    static func countPromotion() {
        promotionLock.lock()
        promotionTotal += 1
        promotionLock.unlock()
    }

    static var totalPromotions: Int {
        promotionLock.lock()
        defer { promotionLock.unlock() }
        return promotionTotal
    }
    private var driven: [Int: String] = [:]
    private(set) var overlapIDs: Set<Int> = []

    var animatedPromotions: Set<Int> {
        return Set(promoted.keys).subtracting(scripted.keys).subtracting(overlapIDs)
    }
    private var drivenSince: [Int: CFTimeInterval] = [:]
    private(set) var bakes = 0
    var scriptedCandidates: Set<Int> = []
    private var promotedClips: [Int: Clip] = [:]
    private var ignoredIDs: Set<String> = [] {
        didSet {
            ignoredSet = NSSet(set: ignoredIDs)
        }
    }
    private var ignoredSet = NSSet()
    static let rereachEnabled = ProcessInfo.processInfo.environment["TW_NO_REREACH"] == nil
    private var scripted: [Int: (reach: CGRect, scale: CGFloat)] = [:]
    private var scriptedOrigins: (generation: UInt64, values: [String: [String: String]]) = (0, [:])

    var scriptedIDs: Set<Int> {
        return Set(scripted.keys)
    }

    static func style(_ node: WidgetNode) -> [String: String] {
        var style: [String: String] = [:]
        for declaration in node.declarations {
            style[declaration.name] = declaration.value
        }
        return style
    }

    private static func anchored(_ transform: CGAffineTransform, box: CGRect, origin: CGPoint) -> CGAffineTransform {
        return CGAffineTransform(translationX: -origin.x, y: -origin.y).concatenating(transform).concatenating(CGAffineTransform(translationX: origin.x + box.minX, y: origin.y + box.minY))
    }

    private(set) var rereaches = 0

    static func unobscured(_ document: LHDocument, _ id: Int, _ reach: CGRect, clip: Clip?, ignoring: NSSet) -> Bool {
        if let clip = clip {
            let clipped = reach.intersection(clip.rect)
            return !clipped.isNull && document.isElement(attribute: WidgetDOM.idAttribute, value: String(id), unobscuredIn: clipped, ignoringSet: ignoring, clipped: true)
        }
        return document.isElement(attribute: WidgetDOM.idAttribute, value: String(id), unobscuredIn: reach, ignoringSet: ignoring, clipped: false)
    }

    static let reachMargins: [CGFloat] = [192, 48, 16, 2]

    private func covers(_ reach: CGRect, _ moved: CGRect, container clip: Clip?) -> Bool {
        if let clip = clip {
            let visible = moved.intersection(clip.rect)
            return visible.isNull || reach.contains(visible)
        }
        return reach.contains(moved)
    }

    private func containerClip(_ id: Int) -> Clip? {
        guard let layer = promoted[id]?.layer, let clip = promotedClips[id], let host = host, layer.superlayer !== host else {
            return nil
        }
        return containers.values.contains { $0 === layer.superlayer } ? clip : nil
    }

    private func rereach(_ id: Int, item: Promoted, moved: CGRect, scale: CGFloat, document: LHDocument) -> CGRect? {
        if let clip = containerClip(id) {
            return ([clip.rect] + WidgetCompositor.reachMargins.map { moved.insetBy(dx: -$0, dy: -$0) }).first { covers($0, moved, container: clip) && WidgetCompositor.unobscured(document, id, $0, clip: clip, ignoring: ignoredSet) }
        }
        guard let clip = promotedClips[id] else {
            return WidgetCompositor.reachMargins.map { moved.insetBy(dx: -$0, dy: -$0) }.first { WidgetCompositor.unobscured(document, id, $0, clip: nil, ignoring: ignoredSet) }
        }
        guard item.layer.superlayer === host else {
            return nil
        }
        let bands = WidgetCompositor.cornerFreeBands(clip, containing: moved)
        let candidates = (bands + WidgetCompositor.reachMargins.map { moved.insetBy(dx: -$0, dy: -$0) }).compactMap { reach in bands.map { reach.intersection($0) }.max { $0.width * $0.height < $1.width * $1.height } }
        if let reach = candidates.first(where: { $0.contains(moved) && WidgetCompositor.clipIsInert(clip, reach: $0, box: item.box, ink: item.ink, scale: scale) && WidgetCompositor.unobscured(document, id, $0, clip: clip, ignoring: ignoredSet) }) {
            return reach
        }
        guard contain(clip) else {
            return nil
        }
        return rereach(id, item: item, moved: moved, scale: scale, document: document)
    }

    private func contain(_ clip: Clip) -> Bool {
        guard let host = host else {
            return false
        }
        let members = promoted.filter { promotedClips[$0.key] == clip && $0.value.layer.superlayer === host }
        if members.isEmpty || members.values.contains(where: { $0.layer.animationKeys()?.isEmpty == false }) {
            return false
        }
        let container = clipContainer(clip, host: host)
        for (id, item) in members {
            container.twAddSublayer(item.layer)
            item.layer.position = CGPoint(x: item.box.minX + item.origin.x - clip.rect.minX, y: item.box.minY + item.origin.y - clip.rect.minY)
            WidgetCompositor.setOrder(container, min(WidgetCompositor.order(container) == 0 ? WidgetCompositor.order(item.layer) : WidgetCompositor.order(container), WidgetCompositor.order(item.layer)))
            clipOf[id] = clip
        }
        let layers = Set(promoted.values.map { ObjectIdentifier($0.layer) }).union(containers.values.map { ObjectIdentifier($0) })
        WidgetCompositor.orderSublayers(of: container, members: layers)
        WidgetCompositor.orderSublayers(of: host, members: layers)
        return true
    }

    func canApplyScripted(_ ops: [WidgetOp], dom: WidgetDOM) -> Bool {
        var styles: [Int: [String: String]] = [:]
        for op in ops {
            guard let node = dom.node(op.id) else { return false }
            if styles[op.id] == nil { styles[op.id] = WidgetCompositor.style(node) }
            styles[op.id]?[op.name.lowercased()] = op.value
        }
        for (id, style) in styles {
            guard let item = promoted[id], let limits = scripted[id],
                  let transform = parse(style["transform"], box: item.box.size, id: id),
                  sqrt(abs(transform.a * transform.d - transform.b * transform.c)) <= limits.scale + 0.001 else {
                return false
            }
            if let text = style["opacity"], Double(text.trimmingCharacters(in: .whitespaces))?.isFinite != true {
                return false
            }
            let moved = CGRect(origin: .zero, size: item.box.size).applying(WidgetCompositor.anchored(transform, box: item.box, origin: item.origin))
            guard covers(limits.reach, moved, container: containerClip(id)) else { return false }
        }
        return true
    }

    func applyScripted(_ ids: Set<Int>, dom: WidgetDOM, document: LHDocument?) -> Bool {
        var updates: [(CALayer, CGAffineTransform, Float)] = []
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        defer {
            CATransaction.commit()
        }
        for id in ids {
            guard let item = promoted[id], let limits = scripted[id], let node = dom.node(id) else {
                return false
            }
            let style = WidgetCompositor.style(node)
            guard let transform = parse(style["transform"], box: item.box.size, id: id) else {
                return false
            }
            let opacity: Float
            if let text = style["opacity"] {
                guard let value = Double(text.trimmingCharacters(in: .whitespaces)), value.isFinite else {
                    return false
                }
                opacity = Float(max(0, min(1, value)))
            } else {
                opacity = 1
            }
            let moved = CGRect(origin: .zero, size: item.box.size).applying(WidgetCompositor.anchored(transform, box: item.box, origin: item.origin))
            if sqrt(abs(transform.a * transform.d - transform.b * transform.c)) > limits.scale + 0.001 {
                return false
            }
            if !covers(limits.reach, moved, container: containerClip(id)) {
                guard WidgetCompositor.rereachEnabled, let document = document, let reach = rereach(id, item: item, moved: moved, scale: limits.scale, document: document) else {
                    return false
                }
                scripted[id]?.reach = reach
                rereaches += 1
            }
            updates.append((item.layer, transform, opacity))
        }
        for (layer, transform, opacity) in updates {
            layer.setAffineTransform(transform)
            layer.opacity = opacity
        }
        return true
    }

    var drivenIDs: Set<Int> {
        return Set(driven.keys)
    }

    var ids: Set<Int> {
        return Set(promoted.keys)
    }

    func invalidateScroll(_ id: Int, dom: WidgetDOM) -> Bool {
        let affected = promoted.keys.filter { $0 == id || isAncestor($0, of: id, dom: dom) || isAncestor(id, of: $0, dom: dom) }
        for item in affected {
            rasterSignatures[item] = nil
            localRasters[item] = nil
        }
        return !affected.isEmpty
    }

    init(host: CALayer?) {
        self.host = host
    }

    func attach(_ host: CALayer?) {
        let previous = self.host
        if previous === host {
            return
        }
        self.host = host
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for container in containers.values {
            host?.twAddSublayer(container)
        }
        for item in promoted.values where item.layer.superlayer == nil || item.layer.superlayer === previous {
            host?.twAddSublayer(item.layer)
        }
        CATransaction.commit()
    }

    func reset() {
        for id in effectKeys.keys {
            removeEffectAnimations(id)
        }
        for item in promoted.values {
            item.effects?.backdropLayer?.twRemoveFromSuperlayer()
            item.layer.twRemoveFromSuperlayer()
        }
        promoted.removeAll()
        rasterSignatures.removeAll()
        localRasters.removeAll()
        localObservations.removeAll()
        driven.removeAll()
        for container in containers.values {
            container.twRemoveFromSuperlayer()
        }
        containers.removeAll()
    }

    func undrive() {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for id in driven.keys {
            if let layer = promoted[id]?.layer, let presentation = layer.presentation() {
                layer.transform = presentation.transform
                layer.opacity = presentation.opacity
            }
        }
        CATransaction.commit()
        for id in driven.keys {
            promoted[id]?.layer.removeAnimation(forKey: "tw.transform")
            promoted[id]?.layer.removeAnimation(forKey: "tw.opacity")
            removeEffectAnimations(id)
        }
        driven.removeAll()
    }

    func drive(animator: WidgetAnimator, now: CFTimeInterval, mediaOffset: CFTimeInterval, enabled: Bool) {
        for id in driven.keys where promoted[id] == nil || !enabled {
            promoted[id]?.layer.removeAnimation(forKey: "tw.transform")
            promoted[id]?.layer.removeAnimation(forKey: "tw.opacity")
            removeEffectAnimations(id)
            driven[id] = nil
        }
        guard enabled else {
            return
        }
        for (id, item) in promoted {
            if let bakes = animator.webBakes(id) {
                driveWeb(id, item: item, bakes: bakes)
                continue
            }
            guard let baked = animator.bakeable(id) else {
                if driven[id] != nil {
                    item.layer.removeAnimation(forKey: "tw.transform")
                    item.layer.removeAnimation(forKey: "tw.opacity")
                    removeEffectAnimations(id)
                    driven[id] = nil
                }
                continue
            }
            let spec = baked.spec
            let signature = "\(spec.name)|\(spec.duration)|\(spec.timingText)|\(spec.delay)|\(spec.iterations)|\(spec.direction)|\(spec.fill)|\(baked.start)|\(item.box)|\(item.origin)|\(Int((mediaOffset * 1000).rounded()))"
            if driven[id] == signature {
                continue
            }
            item.layer.removeAnimation(forKey: "tw.transform")
            item.layer.removeAnimation(forKey: "tw.opacity")
            removeEffectAnimations(id)
            driven[id] = nil
            let base = animator.base(id)
            guard let track = WidgetCompositor.bake(baked, base: base, box: item.box.size, origin: item.origin, effects: item.template, resolve: { [self] in self.resolved($0, id: id) }) else {
                continue
            }
            var begin = item.layer.convertTime(baked.start + spec.delay + mediaOffset, from: nil)
            if begin == 0 {
                begin = 1e-9
            }
            let reversedOrder = spec.direction == "reverse" || spec.direction == "alternate-reverse"
            let alternate = spec.direction.hasPrefix("alternate")
            func configure(_ animation: CAKeyframeAnimation, _ values: [Any]) {
                animation.values = reversedOrder ? values.reversed() : values
                animation.keyTimes = (reversedOrder ? track.times.reversed().map { 1 - $0 } : track.times).map { NSNumber(value: $0) }
                animation.calculationMode = .linear
                animation.duration = max(spec.duration, 0.0001)
                animation.beginTime = begin
                animation.autoreverses = alternate
                let count = alternate ? spec.iterations / 2 : spec.iterations
                animation.repeatCount = count.isFinite ? Float(count) : .infinity
                switch spec.fill {
                case "forwards":
                    animation.fillMode = .forwards
                case "backwards":
                    animation.fillMode = .backwards
                case "both":
                    animation.fillMode = .both
                default:
                    animation.fillMode = .removed
                }
                animation.isRemovedOnCompletion = !(spec.fill == "forwards" || spec.fill == "both")
            }
            let baseTransform = parse(base["transform"], box: item.box.size, id: id) ?? .identity
            let baseOpacity = Float(max(0, min(1, Double(base["opacity"] ?? "1") ?? 1)))
            if track.transforms.contains(where: { $0 != track.transforms[0] }) || track.transforms[0] != baseTransform {
                let animation = CAKeyframeAnimation(keyPath: "transform")
                configure(animation, track.transforms.map { NSValue(caTransform3D: CATransform3DMakeAffineTransform(WidgetCompositor.decomposable($0))) })
                item.layer.add(animation, forKey: "tw.transform")
            }
            if track.opacities.contains(where: { $0 != track.opacities[0] }) || track.opacities[0] != baseOpacity {
                let animation = CAKeyframeAnimation(keyPath: "opacity")
                configure(animation, track.opacities.map { NSNumber(value: $0) })
                item.layer.add(animation, forKey: "tw.opacity")
            }
            if let effects = item.effects, item.ownsEffects, !track.effects.isEmpty {
                var keys: [(CALayer, String)] = []
                let baseEffects = effects.effects
                for (index, channel) in effects.channels(of: item.layer).enumerated() {
                    if track.effects.allSatisfy({ $0 == baseEffects }) {
                        break
                    }
                    let animation = CAKeyframeAnimation(keyPath: channel.keyPath)
                    configure(animation, track.effects.map { channel.value($0) })
                    let key = "tw.fx.\(index)"
                    channel.layer.add(animation, forKey: key)
                    keys.append((channel.layer, key))
                }
                effectKeys[id] = keys
            }
            driven[id] = signature
            drivenSince[id] = CACurrentMediaTime()
            bakes += 1
        }
    }

    private func driveWeb(_ id: Int, item: Promoted, bakes: [WidgetWebAnimations.Bake]) {
        let signature = "web|" + bakes.map { $0.signature }.joined(separator: "#") + "|\(item.box)|\(item.origin)"
        if driven[id] == signature {
            return
        }
        item.layer.removeAnimation(forKey: "tw.transform")
        item.layer.removeAnimation(forKey: "tw.opacity")
        driven[id] = nil
        var added: [(String, CAKeyframeAnimation)] = []
        for bake in bakes {
            guard let begin = bake.begin, let track = WidgetCompositor.bake(WidgetAnimator.Baked(spec: CSSAnimationSpec(), start: 0, value: { progress in let v = bake.value(progress); return (v.transform, v.opacity, nil, nil) }), base: [:], box: item.box.size, origin: item.origin, resolve: { [self] in self.resolved($0, id: id) }) else {
                return
            }
            func configure(_ animation: CAKeyframeAnimation, _ values: [Any]) {
                animation.values = values
                animation.keyTimes = track.times.map { NSNumber(value: $0) }
                animation.calculationMode = .linear
                animation.duration = max(bake.cycle, 0.0001)
                var start = item.layer.convertTime(begin, from: nil)
                if start == 0 {
                    start = 1e-9
                }
                animation.beginTime = start
                animation.speed = bake.speed
                animation.repeatCount = bake.repeatCount
                switch (bake.caBackwards, bake.caForwards) {
                case (true, true):
                    animation.fillMode = .both
                case (true, false):
                    animation.fillMode = .backwards
                case (false, true):
                    animation.fillMode = .forwards
                default:
                    animation.fillMode = .removed
                }
                animation.isRemovedOnCompletion = !bake.caForwards
            }
            if bake.properties.contains("transform") {
                let animation = CAKeyframeAnimation(keyPath: "transform")
                configure(animation, track.transforms.map { NSValue(caTransform3D: CATransform3DMakeAffineTransform(WidgetCompositor.decomposable($0))) })
                added.append(("tw.transform", animation))
            }
            if bake.properties.contains("opacity") {
                let animation = CAKeyframeAnimation(keyPath: "opacity")
                configure(animation, track.opacities.map { NSNumber(value: $0) })
                added.append(("tw.opacity", animation))
            }
        }
        for (key, animation) in added {
            item.layer.add(animation, forKey: key)
        }
        driven[id] = signature
        drivenSince[id] = CACurrentMediaTime()
        self.bakes += 1
    }

    func handoffError(animator: WidgetAnimator, now: CFTimeInterval) -> (geometry: CGFloat, alpha: Float, offset: Double) {
        var samples: [(id: Int, item: Promoted, shown: CALayer)] = []
        for id in driven.keys {
            guard let item = promoted[id], let shown = item.layer.presentation(), CACurrentMediaTime() - (drivenSince[id] ?? 0) > 0.034 else {
                continue
            }
            samples.append((id, item, shown))
        }
        if samples.isEmpty {
            return (0, 0, 0)
        }
        func error(_ sample: (id: Int, item: Promoted, shown: CALayer), at time: CFTimeInterval) -> (CGFloat, Float)? {
            guard let value = animator.peek(sample.id, at: time) else {
                return nil
            }
            let item = sample.item
            let transform = parse(value.transform, box: item.box.size, id: sample.id) ?? .identity
            let actual = CATransform3DGetAffineTransform(sample.shown.transform)
            let opacity = Float(max(0, min(1, Double(value.opacity ?? "1") ?? 1)))
            var geometry: CGFloat = 0
            for corner in [CGPoint(x: -item.origin.x, y: -item.origin.y), CGPoint(x: item.box.width - item.origin.x, y: item.box.height - item.origin.y), CGPoint(x: item.box.width - item.origin.x, y: -item.origin.y)] {
                let a = corner.applying(transform), b = corner.applying(actual)
                geometry = max(geometry, hypot(a.x - b.x, a.y - b.y))
            }
            return (geometry, abs(opacity - sample.shown.opacity))
        }
        var best: (score: CGFloat, geometry: CGFloat, alpha: Float, offset: Double) = (.greatestFiniteMagnitude, 0, 0, 0)
        for step in stride(from: -100, through: 25, by: 1) {
            let offset = Double(step) / 1000
            var geometry: CGFloat = 0
            var alpha: Float = 0
            for sample in samples {
                guard let (g, a) = error(sample, at: now + offset) else {
                    continue
                }
                geometry = max(geometry, g)
                alpha = max(alpha, a)
            }
            let score = geometry + CGFloat(alpha) * 100
            if score < best.score {
                best = (score, geometry, alpha, offset)
            }
        }
        if best.geometry > 1, WidgetCompositor.log {
            for sample in samples {
                if let (g, _) = error(sample, at: now + best.offset), g > 1 {
                    print("HANDOFF \(sample.id) error=\(g) offset=\(best.offset) nested=\(sample.item.layer.superlayer !== host) transform=\(animator.peek(sample.id, at: now + best.offset)?.transform ?? "nil") presentation=\(CATransform3DGetAffineTransform(sample.shown.transform)) box=\(sample.item.box)")
                }
            }
        }
        return (best.geometry, best.alpha, best.offset)
    }

    static func decomposable(_ t: CGAffineTransform) -> CGAffineTransform {
        if abs(t.a * t.d - t.b * t.c) >= 1e-8 {
            return t
        }
        return CGAffineTransform(a: t.a >= 0 ? 1e-4 : -1e-4, b: 0, c: 0, d: t.d >= 0 ? 1e-4 : -1e-4, tx: t.tx, ty: t.ty)
    }

    struct Track {
        var times: [Double]
        var transforms: [CGAffineTransform]
        var opacities: [Float]
        var effects: [WidgetEffects] = []
    }

    static let log = ProcessInfo.processInfo.environment["TW_COMPOSITE_LOG"] != nil
    static let overlapPromotion = ProcessInfo.processInfo.environment["TW_NO_OVERLAP"] == nil
    static var handoffEnabled = ProcessInfo.processInfo.environment["TW_NO_HANDOFF"] == nil
    static let effectsEnabled = ProcessInfo.processInfo.environment["TW_NO_EFFECTS"] == nil
    static let bakeTolerance: CGFloat = 0.05
    static let effectTolerance = 0.004
    static let maxBakeDepth = 9
    static let maxBakeJumps = 64
    static let jumpWidth = 2e-6

    private struct BakeSample: Equatable {
        var transform: CGAffineTransform
        var opacity: Float
        var effects: WidgetEffects?
        var parameters: [Double]
    }

    static func bake(_ baked: WidgetAnimator.Baked, base: [String: String], box: CGSize, origin: CGPoint, effects template: WidgetEffects? = nil, resolve: (String?) -> String? = { $0 }) -> Track? {
        let effects = template != nil
        func eval(_ p: Double) -> BakeSample? {
            let value = baked.value(p)
            let raw = value.transform ?? base["transform"]
            let text = resolve(raw)
            guard raw == nil || text != nil, let transform = CSSTransform.parse(text, box: box) else {
                return nil
            }
            let opacityText = value.opacity ?? base["opacity"] ?? "1"
            guard let opacity = Double(opacityText.trimmingCharacters(in: .whitespaces)), opacity.isFinite else {
                return nil
            }
            var sample = BakeSample(transform: transform, opacity: Float(max(0, min(1, opacity))), effects: nil, parameters: [])
            if effects {
                guard let template = template, let parsed = WidgetEffects.parse(filter: resolve(value.filter), shadow: resolve(value.shadow))?.aligned(to: template) else {
                    return nil
                }
                sample.effects = parsed
                sample.parameters = parsed.parameters
            }
            return sample
        }
        let corners = [CGPoint(x: -origin.x, y: -origin.y), CGPoint(x: box.width - origin.x, y: -origin.y), CGPoint(x: -origin.x, y: box.height - origin.y), CGPoint(x: box.width - origin.x, y: box.height - origin.y)]
        func error(_ a: BakeSample, _ b: BakeSample, _ mid: BakeSample) -> (CGFloat, Float, Double) {
            var worst: CGFloat = 0
            for corner in corners {
                let pa = corner.applying(a.transform), pb = corner.applying(b.transform), pm = corner.applying(mid.transform)
                worst = max(worst, hypot(pm.x - (pa.x + pb.x) / 2, pm.y - (pa.y + pb.y) / 2))
            }
            var effect = 0.0
            for index in mid.parameters.indices where index < a.parameters.count && index < b.parameters.count {
                effect = max(effect, abs(mid.parameters[index] - (a.parameters[index] + b.parameters[index]) / 2))
            }
            return (worst, abs(mid.opacity - (a.opacity + b.opacity) / 2), effect)
        }
        func distance(_ a: BakeSample, _ b: BakeSample) -> CGFloat {
            var worst: CGFloat = 0
            for corner in corners {
                let pa = corner.applying(a.transform), pb = corner.applying(b.transform)
                worst = max(worst, hypot(pa.x - pb.x, pa.y - pb.y))
            }
            var effect = 0.0
            for index in a.parameters.indices where index < b.parameters.count {
                effect = max(effect, abs(a.parameters[index] - b.parameters[index]))
            }
            return worst + CGFloat(abs(a.opacity - b.opacity)) * 100 + CGFloat(effect) * 100
        }
        func locateJump(_ t0: Double, _ s0: BakeSample, _ t1: Double, _ s1: BakeSample) -> (before: Double, left: BakeSample, after: Double, right: BakeSample)? {
            var a = (t0, s0), b = (t1, s1)
            while b.0 - a.0 > jumpWidth {
                let t = (a.0 + b.0) / 2
                guard let value = eval(t) else {
                    return nil
                }
                if distance(a.1, value) >= distance(value, b.1) {
                    b = (t, value)
                } else {
                    a = (t, value)
                }
            }
            guard distance(a.1, b.1) > bakeTolerance else {
                return nil
            }
            return (a.0, a.1, b.0, b.1)
        }
        var times: [Double] = []
        var samples: [BakeSample] = []
        var failed = false
        var jumpBudget = maxBakeJumps
        func subdivide(_ t0: Double, _ s0: BakeSample, _ t1: Double, _ s1: BakeSample, _ depth: Int) {
            if failed || times.count >= 4096 {
                failed = true
                return
            }
            let tm = (t0 + t1) / 2
            guard let sm = eval(tm) else {
                failed = true
                return
            }
            let (geometry, alpha, effect) = error(s0, s1, sm)
            if geometry <= baked.geometryTolerance && alpha <= baked.opacityTolerance && effect <= effectTolerance {
                times.append(t1)
                samples.append(s1)
                return
            }
            if depth >= maxBakeDepth {
                guard jumpBudget > 0, let jump = locateJump(t0, s0, t1, s1) else {
                    failed = true
                    return
                }
                jumpBudget -= 1
                if jump.before > t0 {
                    subdivide(t0, s0, jump.before, jump.left, depth)
                }
                times.append(jump.after)
                samples.append(jump.right)
                if jump.after < t1 {
                    subdivide(jump.after, jump.right, t1, s1, depth)
                }
                return
            }
            subdivide(t0, s0, tm, sm, depth + 1)
            subdivide(tm, sm, t1, s1, depth + 1)
        }
        let initial = 8
        let boundaries = Set((0...initial).map { Double($0) / Double(initial) } + baked.breakpoints.filter { $0.isFinite && $0 > 0 && $0 < 1 }).sorted()
        guard boundaries.count <= 4096 else {
            return nil
        }
        guard var previous = eval(0) else {
            return nil
        }
        times.append(0)
        samples.append(previous)
        for step in 1..<boundaries.count {
            if failed {
                return nil
            }
            let t0 = boundaries[step - 1], t1 = boundaries[step]
            guard let next = eval(t1) else {
                return nil
            }
            subdivide(t0, previous, t1, next, 0)
            previous = next
        }
        if failed || times.count > 4096 {
            return nil
        }
        var keptTimes: [Double] = [times[0]]
        var kept: [BakeSample] = [samples[0]]
        for index in 1..<times.count {
            let last = kept.count - 1
            if index < times.count - 1, last > 0, kept[last - 1] == kept[last], kept[last] == samples[index] {
                keptTimes[last] = times[index]
                continue
            }
            keptTimes.append(times[index])
            kept.append(samples[index])
        }
        return Track(times: keptTimes, transforms: kept.map { $0.transform }, opacities: kept.map { $0.opacity }, effects: effects ? kept.map { $0.effects ?? WidgetEffects() } : [])
    }

    func update(document: LHDocument, animator: WidgetAnimator, dom: WidgetDOM, declarations: [Int: [String: String]], scale: CGFloat, space: CGColorSpace, extraExclusions: Set<Int> = [], blockedIDs: Set<Int> = []) {
        ancestorCache.removeAll(keepingCapacity: true)
        if let previous = displaySpace, !CFEqual(previous, space) {
            spaceGeneration += 1
            WidgetCompositor.promotionLock.lock()
            WidgetCompositor.spaceChanges += 1
            WidgetCompositor.promotionLock.unlock()
        }
        displaySpace = space
        guard let host = host else {
            reset()
            return
        }
        currentDOM = dom
        let animated = animator.animatedIDs()
        let independentBackgrounds = Set(animated.filter {
            animator.animatesOnly($0, ["background-color"]) && document.backgroundColorIsLocal(attribute: WidgetDOM.idAttribute, value: String($0))
        })
        var measured: [Int: CGRect]?
        var boxes: [Int: CGRect] {
            if let known = measured {
                return known
            }
            let made = document.idBounds()
            measured = made
            return made
        }
        var wanted: [Int: (box: CGRect, origin: CGPoint, maxScale: CGFloat)] = [:]
        var effectOwners: [Int: Double] = [:]
        var effectTemplates: [Int: WidgetEffects] = [:]
        var backdrops: [Int: [CSSFilterStep]] = [:]
        var reaches: [Int: [CGRect]] = [:]
        let log = WidgetCompositor.log
        for id in animated {
            guard animator.isCompositable(id) else {
                if log { print("COMPOSITE \(id) not compositable") }
                continue
            }
            guard let node = dom.node(id), !node.svg, let box = boxes[id], box.width > 0, box.height > 0, box.width * box.height < 2_000_000 else {
                if log { print("COMPOSITE \(id) no box") }
                continue
            }
            let origin = CSSTransform.origin(declarations[id]?["transform-origin"], box: box.size)
            var reach = box
            var maxScale: CGFloat = 1
            var ok = true
            let values = animator.reachableValues(id)
            var effectReach = 0.0
            if WidgetCompositor.effectsEnabled {
                var parsed: [WidgetEffects] = []
                let shadowTexts = Set(values.map { resolved($0.shadow, id: id) ?? "" })
                if shadowTexts.count > 1 && values.contains(where: { CSSBoxShadow.parseList(resolved($0.shadow, id: id))?.contains { $0.inset } != false }) {
                    if log { print("COMPOSITE \(id) inset shadow animates") }
                    continue
                }
                for value in values {
                    guard let effects = WidgetEffects.parse(filter: resolved(value.filter, id: id), shadow: resolved(value.shadow, id: id)) else {
                        ok = false
                        break
                    }
                    parsed.append(effects)
                    effectReach = max(effectReach, effects.reach)
                }
                let template = ok ? WidgetEffects.template(parsed) : nil
                if template == nil {
                    ok = false
                }
                if !ok {
                    let staticFilter = CSSFilterStep.parseList(resolved(declarations[id]?["filter"], id: id)).map { $0.isEmpty } ?? false
                    if animator.animatesEffects(id) || !staticFilter {
                        if log { print("COMPOSITE \(id) effects not compositable") }
                        continue
                    }
                    ok = true
                } else if let template = template, !template.isEmpty {
                    effectOwners[id] = effectReach
                    effectTemplates[id] = template
                }
            } else if animator.animatesEffects(id) || !(CSSFilterStep.parseList(resolved(declarations[id]?["filter"], id: id)).map { $0.isEmpty } ?? false) {
                continue
            }
            if let text = declarations[id]?["backdrop-filter"], let steps = CSSFilterStep.parseList(resolved(text, id: id)), !steps.isEmpty {
                if !WidgetCompositor.effectsEnabled || steps.contains(where: { $0.kind == .dropShadow }) || effectOwners[id].map({ _ in values.contains { CSSFilterStep.parseList(resolved($0.filter, id: id))?.isEmpty == false } }) == true {
                    continue
                }
                backdrops[id] = steps
            }
            for value in values {
                guard let transform = parse(value.transform, box: box.size, id: id) else {
                    if log { print("COMPOSITE \(id) unparsed \(value.transform ?? "nil") resolved \(resolved(value.transform, id: id) ?? "nil")") }
                    ok = false
                    break
                }
                let anchored = CGAffineTransform(translationX: -origin.x, y: -origin.y).concatenating(transform).concatenating(CGAffineTransform(translationX: origin.x + box.minX, y: origin.y + box.minY))
                reach = reach.union(CGRect(origin: .zero, size: box.size).applying(anchored))
                maxScale = max(maxScale, sqrt(abs(transform.a * transform.d - transform.b * transform.c)))
                if let opacity = value.opacity, Double(opacity) == nil || (backdrops[id] != nil && Double(opacity) != 1) {
                    ok = false
                    break
                }
            }
            if !ok {
                effectOwners[id] = nil
                backdrops[id] = nil
                continue
            }
            wanted[id] = (box, origin, min(maxScale, 3))
            reaches[id] = [reach.insetBy(dx: -CGFloat(effectReach), dy: -CGFloat(effectReach))]
        }
        if WidgetCompositor.effectsEnabled {
            for (id, values) in declarations where wanted[id] == nil && !animated.contains(id) {
                guard let text = values["backdrop-filter"], let steps = CSSFilterStep.parseList(resolved(text, id: id)), !steps.isEmpty, !steps.contains(where: { $0.kind == .dropShadow }) else {
                    continue
                }
                guard CSSFilterStep.parseList(resolved(values["filter"], id: id))?.isEmpty == true, (Double(values["opacity"] ?? "1") ?? 0) >= 1 else {
                    continue
                }
                guard let node = dom.node(id), !node.svg, let box = boxes[id], box.width > 0, box.height > 0, box.width * box.height < 2_000_000 else {
                    continue
                }
                wanted[id] = (box, CGPoint(x: box.width / 2, y: box.height / 2), 1)
                reaches[id] = [box]
                backdrops[id] = steps
            }
        }
        var scriptedWanted: [Int: (reach: CGRect, scale: CGFloat)] = [:]
        var scriptedBases: [Int: CGRect] = [:]
        scriptedCandidates = scriptedCandidates.filter { dom.node($0) != nil }
        var originGeneration: UInt64 = 0
        if !scriptedCandidates.isEmpty, let origins = document.declarations(["transform-origin"], byAttribute: WidgetDOM.idAttribute, requiringAny: [], changedSince: scriptedOrigins.generation, generation: &originGeneration) {
            scriptedOrigins = (originGeneration, origins)
        }
        var related: Set<Int> = []
        var relatedAncestors: Set<Int>?
        for id in scriptedCandidates where wanted[id] == nil && !animated.contains(id) && !animator.hasCSSMotion(id) {
            guard let node = dom.node(id), !node.svg, let box = boxes[id], box.width > 0, box.height > 0, box.width * box.height < 2_000_000 else {
                continue
            }
            if relatedAncestors == nil {
                related = Set(wanted.keys).union(scriptedCandidates)
                relatedAncestors = WidgetCompositor.ancestorUnion(related) { self.ancestors(of: $0, dom: dom) }
            }
            if WidgetCompositor.conflicts(id, related: related, relatedAncestors: relatedAncestors!, ancestors: ancestors(of: id, dom: dom)) {
                continue
            }
            let style = WidgetCompositor.style(node)
            guard let transform = parse(style["transform"], box: box.size, id: id), style["opacity"].map({ Double($0.trimmingCharacters(in: .whitespaces)) != nil }) ?? true else {
                continue
            }
            let origin = CSSTransform.origin(scriptedOrigins.values[String(id)]?["transform-origin"] ?? style["transform-origin"], box: box.size)
            let current = CGRect(origin: .zero, size: box.size).applying(WidgetCompositor.anchored(transform, box: box, origin: origin))
            let scale = WidgetCompositor.scriptedRasterMagnification(transform)
            wanted[id] = (box, origin, scale)
            reaches[id] = [max(48, max(box.width, box.height) / 2), 16, 2].map { current.insetBy(dx: -$0, dy: -$0) }
            scriptedBases[id] = current
            scriptedWanted[id] = (.null, scale)
        }
        let blockedScriptedIDs = blockedIDs.intersection(scriptedWanted.keys)
        for id in blockedScriptedIDs {
            wanted[id] = nil
            reaches[id] = nil
            scriptedWanted[id] = nil
        }
        var deferredLocalPaint = Set<Int>()
        if WidgetCompositor.localPaintReuse {
            for (id, want) in wanted where scriptedWanted[id] != nil && want.maxScale == 1 {
                guard let previous = localObservations[id], previous.origin != want.box.origin else {
                    continue
                }
                func phase(_ origin: CGPoint) -> CGPoint {
                    let x = origin.x * scale, y = origin.y * scale
                    return CGPoint(x: x - x.rounded(.down), y: y - y.rounded(.down))
                }
                if phase(previous.origin) != phase(want.box.origin) {
                    localObservations[id] = (previous.key, want.box.origin)
                    deferredLocalPaint.insert(id)
                    wanted[id] = nil
                    reaches[id] = nil
                    scriptedWanted[id] = nil
                }
            }
        }
        for id in wanted.keys {
            var dx: CGFloat = 0, dy: CGFloat = 0
            for other in ancestors(of: id, dom: dom) where other != id && wanted[other] != nil {
                if let box = wanted[other]?.box, let reach = reaches[other]?.first {
                    dx += max(box.minX - reach.minX, reach.maxX - box.maxX, 0)
                    dy += max(box.minY - reach.minY, reach.maxY - box.maxY, 0)
                }
            }
            if dx > 0 || dy > 0 {
                reaches[id] = reaches[id]?.map { $0.insetBy(dx: -dx, dy: -dy) }
            }
        }
        var clips: [Int: Clip] = [:]
        for id in wanted.keys {
            var rect = CGRect.zero
            var radii = [CGFloat](repeating: 0, count: 8)
            if document.clipOfElement(attribute: WidgetDOM.idAttribute, value: String(id), rect: &rect, radii: &radii) {
                clips[id] = Clip(rect: rect, radii: radii)
            }
        }
        if WidgetCompositor.inertClips {
            for (id, base) in scriptedBases {
                guard let clip = clips[id], let list = reaches[id] else {
                    continue
                }
                let bands = WidgetCompositor.cornerFreeBands(clip, containing: base)
                reaches[id] = list.compactMap { reach in bands.map { reach.intersection($0) }.max { $0.width * $0.height < $1.width * $1.height } } + list
            }
        }
        for id in wanted.keys {
            for other in ancestors(of: id, dom: dom) where other != id && wanted[other] != nil && clips[other] != clips[id] {
                wanted[id] = nil
            }
        }
        struct UnobscuredKey: Hashable {
            let id: Int
            let x: CGFloat, y: CGFloat, width: CGFloat, height: CGFloat
            let tag: Int
        }
        var unobscuredMemo: [UnobscuredKey: Bool] = [:]
        var ignoringTags: [(set: Set<String>, tag: Int)] = []
        var ignoringSets: [Int: NSSet] = [:]
        func tag(for ignoring: Set<String>) -> Int {
            if let known = ignoringTags.first(where: { $0.set == ignoring }) {
                return known.tag
            }
            ignoringTags.append((ignoring, ignoringTags.count + 1))
            return ignoringTags.count
        }
        func nsSet(_ ignoring: Set<String>, tag: Int) -> NSSet {
            if tag > 0, let known = ignoringSets[tag] {
                return known
            }
            let set = NSSet(set: ignoring)
            if tag > 0 {
                ignoringSets[tag] = set
            }
            return set
        }
        func unobscured(_ id: Int, _ reach: CGRect, _ ignoring: Set<String>, tag: Int) -> Bool {
            let key = UnobscuredKey(id: id, x: reach.minX, y: reach.minY, width: reach.width, height: reach.height, tag: tag)
            if let known = unobscuredMemo[key] {
                return known
            }
            let set = nsSet(ignoring, tag: tag)
            let result = WidgetCompositor.unobscured(document, id, reach, clip: clips[id], ignoring: set)
            unobscuredMemo[key] = result
            return result
        }
        func adoptStaticEffects(_ id: Int) -> Bool {
            guard effectTemplates[id] == nil, !animated.contains(id), let values = declarations[id], values["filter"] != nil || values["box-shadow"] != nil else {
                return true
            }
            let filterText = resolved(values["filter"], id: id)
            guard WidgetCompositor.effectsEnabled, let effects = WidgetEffects.parse(filter: filterText, shadow: resolved(values["box-shadow"], id: id)) else {
                return CSSFilterStep.parseList(filterText)?.isEmpty == true
            }
            if !effects.isEmpty && (backdrops[id] == nil || effects.filter.isEmpty) {
                effectOwners[id] = effects.reach
                effectTemplates[id] = effects
                reaches[id] = reaches[id]?.map { $0.insetBy(dx: -CGFloat(effects.reach), dy: -CGFloat(effects.reach)) }
                return true
            }
            return effects.filter.isEmpty
        }
        for id in Array(wanted.keys) where !adoptStaticEffects(id) {
            wanted[id] = nil
            reaches[id] = nil
        }
        var overlapAdded: Set<Int> = []
        if WidgetCompositor.overlapPromotion {
            var ignoringAll = Set(wanted.keys.map { String($0) })
            var allTag = tag(for: ignoringAll)
            var queue = wanted.keys.sorted()
            var budget = max(0, WidgetCompositor.maxLayers - wanted.count)
            while let id = queue.popLast(), budget > 0 {
                guard let reach = reaches[id]?.first, !unobscured(id, reach, ignoringAll, tag: allTag) else {
                    continue
                }
                let clipped = clips[id].map { reach.intersection($0.rect) } ?? reach
                guard !clipped.isNull, let obscuring = document.elementsObscuring(attribute: WidgetDOM.idAttribute, value: String(id), in: clipped, ignoringSet: nsSet(ignoringAll, tag: allTag), clipped: clips[id] != nil), !obscuring.isEmpty, obscuring.count <= 3 else {
                    continue
                }
                let others = obscuring.compactMap { Int($0) }
                guard others.count <= budget, others.count == obscuring.count, others.allSatisfy({ other in
                    wanted[other] == nil && !blockedScriptedIDs.contains(other) && !deferredLocalPaint.contains(other) && !animated.contains(other) && dom.node(other).map { !$0.svg || $0.tag == "svg" } == true && !isAncestor(other, of: id, dom: dom) && !isAncestor(id, of: other, dom: dom) && boxes[other] != nil
                }) else {
                    continue
                }
                for other in others {
                    let box = boxes[other]!
                    guard box.width > 0, box.height > 0, box.width * box.height < 2_000_000 else {
                        continue
                    }
                    wanted[other] = (box, CGPoint(x: box.width / 2, y: box.height / 2), 1)
                    reaches[other] = [box]
                    guard adoptStaticEffects(other) else {
                        wanted[other] = nil
                        reaches[other] = nil
                        continue
                    }
                    var rect = CGRect.zero
                    var radii = [CGFloat](repeating: 0, count: 8)
                    if document.clipOfElement(attribute: WidgetDOM.idAttribute, value: String(other), rect: &rect, radii: &radii) {
                        clips[other] = Clip(rect: rect, radii: radii)
                    }
                    overlapAdded.insert(other)
                    ignoringAll.insert(String(other))
                    allTag = tag(for: ignoringAll)
                    budget -= 1
                    if effectTemplates[other] != nil {
                        queue.append(other)
                    }
                }
            }
        }
        var localKeys: [Int: Data] = [:]
        var localCandidates = Set<Int>()
        var movingPaint = Set<Int>()
        if WidgetCompositor.localPaintReuse {
            var remainingBytes = 4 * 1024 * 1024 - deferredLocalPaint.reduce(0, { $0 + (localObservations[$1]?.key.count ?? 0) })
            var remainingCandidates = WidgetCompositor.maxLayers - deferredLocalPaint.count
            for (id, want) in wanted.sorted(by: { (promoted[$0.key] == nil ? 1 : 0, $0.key) < (promoted[$1.key] == nil ? 1 : 0, $1.key) }) where scriptedWanted[id] != nil && want.maxScale == 1 && effectOwners[id] == nil && backdrops[id] == nil {
                guard remainingCandidates > 0, remainingBytes > 0 else {
                    break
                }
                guard let node = dom.node(id), let transform = parse(WidgetCompositor.style(node)["transform"], box: want.box.size, id: id), transform.a == 1, transform.b == 0, transform.c == 0, transform.d == 1 else {
                    continue
                }
                remainingCandidates -= 1
                localKeys[id] = document.localPaintKey(attribute: WidgetDOM.idAttribute, value: String(id), scale: WidgetCompositor.rasterScale(scale, want.maxScale))
                if let key = localKeys[id] {
                    if key.count > remainingBytes {
                        localKeys[id] = nil
                        remainingBytes = 0
                    } else {
                        remainingBytes -= key.count
                    }
                }
                if localKeys[id] != nil || localRasters[id] != nil {
                    localCandidates.insert(id)
                }
                if let key = localKeys[id] {
                    var retainedKey = key
                    if let previous = localObservations[id] {
                        if previous.key == key {
                            retainedKey = previous.key
                        } else if previous.origin != want.box.origin {
                            movingPaint.insert(id)
                        }
                    }
                    localKeys[id] = retainedKey
                    localObservations[id] = (retainedKey, want.box.origin)
                }
            }
        }
        for id in localObservations.keys where localKeys[id] == nil && !deferredLocalPaint.contains(id) {
            localObservations[id] = nil
        }
        for id in localRasters.keys where localKeys[id] == nil && wanted[id] != nil {
            localCandidates.insert(id)
        }
        for id in movingPaint {
            wanted[id] = nil
        }
        func reusableLocalRaster(_ id: Int, box: CGRect) -> Bool {
            guard let key = localKeys[id], let cached = localRasters[id], let existing = promoted[id], existing.effects == nil, existing.layer.superlayer != nil, existing.box.size == box.size else {
                return false
            }
            return cached.space == spaceGeneration && cached.key == key
        }
        var measuredRasterBounds: [Int: CGRect] = [:]
        for id in wanted.keys {
            measuredRasterBounds[id] = document.rasterBoundsOfElement(attribute: WidgetDOM.idAttribute, value: String(id), bareShadows: effectOwners[id] != nil)
        }
        var accepted = wanted
        var chosenReach: [Int: CGRect] = [:]
        var paintRank: [Int: Int]?
        for _ in 0...wanted.count {
            let ignoring = Set(accepted.keys.map { String($0) })
            let ignoringTag = tag(for: ignoring)
            var next: [Int: (box: CGRect, origin: CGPoint, maxScale: CGFloat)] = [:]
            for (id, want) in accepted {
                if ancestors(of: id, dom: dom).contains(where: { accepted[$0] == nil && animated.contains($0) && !independentBackgrounds.contains($0) }) {
                    continue
                }
                if log, !(reaches[id]?.contains(where: { unobscured(id, $0, ignoring, tag: ignoringTag) }) ?? false) {
                    print("COMPOSITE \(id) obscured box=\(want.box) reach=\(reaches[id]?.first ?? .null) clip=\(clips[id].map { "\($0.rect)" } ?? "none")")
                }
                if let reach = reaches[id]?.first(where: { unobscured(id, $0, ignoring, tag: ignoringTag) }) {
                    next[id] = want
                    chosenReach[id] = reach
                    if scriptedWanted[id] != nil {
                        scriptedWanted[id]?.reach = reach
                    }
                }
            }
            func pixels(_ id: Int, _ want: (box: CGRect, origin: CGPoint, maxScale: CGFloat)) -> Int {
                if reusableLocalRaster(id, box: want.box), let cached = localRasters[id] {
                    return cached.pixels
                }
                let factor = WidgetCompositor.rasterScale(scale, want.maxScale)
                let bounds = measuredRasterBounds[id] ?? want.box.insetBy(dx: -48, dy: -48)
                guard !bounds.isNull, bounds.width.isFinite, bounds.height.isFinite, bounds.width * factor <= 8192, bounds.height * factor <= 8192 else { return WidgetCompositor.maxPixels + 1 }
                return Int((bounds.width * factor).rounded(.up) * (bounds.height * factor).rounded(.up))
            }
            var admitted: [Int: (box: CGRect, origin: CGPoint, maxScale: CGFloat)] = [:]
            if next.count <= WidgetCompositor.maxLayers && next.reduce(0, { $0 + pixels($1.key, $1.value) }) <= WidgetCompositor.maxPixels {
                admitted = next
            } else {
                if paintRank == nil {
                    let order = document.paintOrder(attribute: WidgetDOM.idAttribute, values: Set(wanted.keys.map { String($0) }), includingScrollContent: true)
                    paintRank = Dictionary(order.compactMap { key, value in Int(key).map { ($0, value.intValue) } }, uniquingKeysWith: { max($0, $1) })
                }
                let rank = paintRank ?? [:]
                var budget = WidgetCompositor.maxPixels
                for (id, want) in next.sorted(by: { (rank[$0.key] ?? -1, $0.key) > (rank[$1.key] ?? -1, $1.key) }) {
                    let cost = pixels(id, want)
                    if admitted.count >= WidgetCompositor.maxLayers || cost > budget {
                        break
                    }
                    budget -= cost
                    admitted[id] = want
                }
            }
            if log { print("COMPOSITE round wanted=\(wanted.count) unobscured=\(next.count) admitted=\(admitted.count)") }
            let stable = Set(admitted.keys) == Set(accepted.keys)
            accepted = admitted
            if stable {
                break
            }
        }
        while let orphan = accepted.keys.first(where: { id in ancestors(of: id, dom: dom).contains(where: { accepted[$0] == nil && animated.contains($0) && !independentBackgrounds.contains($0) }) }) {
            accepted[orphan] = nil
        }
        if accepted.count > 1 {
            while true {
                let ignoring = Set(accepted.keys.map { String($0) })
                let finalTag = tag(for: ignoring)
                let filtered = accepted.filter { entry in reaches[entry.key]?.contains(where: { unobscured(entry.key, $0, ignoring, tag: finalTag) }) ?? false }
                if filtered.count == accepted.count {
                    break
                }
                accepted = filtered
            }
        } else if let only = accepted.first, !(reaches[only.key]?.contains(where: { unobscured(only.key, $0, [], tag: 0) }) ?? false) {
            accepted = [:]
        }
        wanted = accepted
        scripted = scriptedWanted.filter { accepted[$0.key] != nil && !$0.value.reach.isNull }
        overlapIDs = overlapAdded.filter { accepted[$0] != nil }
        var order: [String: NSNumber] = [:]
        if wanted.count > 1 || Set(wanted.keys) != Set(promoted.keys) {
            order = document.paintOrder(attribute: WidgetDOM.idAttribute, values: Set(wanted.keys.map { String($0) }), includingScrollContent: true)
        }
        for (id, item) in promoted where wanted[id] == nil {
            removeEffectAnimations(id)
            item.effects?.backdropLayer?.twRemoveFromSuperlayer()
            item.layer.twRemoveFromSuperlayer()
            promoted[id] = nil
        }
        document.setPaintExclusions(Set(wanted.keys.map { String($0) }).union(extraExclusions.map { String($0) }), attribute: WidgetDOM.idAttribute)
        for (id, want) in wanted {
            let inner = wanted.keys.filter { $0 != id && isAncestor(id, of: $0, dom: dom) }.sorted()
            let owns = effectOwners[id] != nil
            let backdrop = backdrops[id] ?? []
            var effectKey = Hasher()
            effectKey.combine(owns)
            effectKey.combine(backdrop.map { $0.parameters })
            effectKey.combine(effectOwners[id] ?? 0)
            effectKey.combine(effectTemplates[id]?.parameters ?? [])
            effectKey.combine(spaceGeneration)
            let signature = WidgetCompositor.contentSignature(id, dom: dom, box: want.box, scale: WidgetCompositor.rasterScale(scale, want.maxScale), styleKey: styleKey ^ inner.hashValue ^ effectKey.finalize())
            let reuse = localCandidates.contains(id) ? inner.isEmpty && reusableLocalRaster(id, box: want.box) : signature != nil && rasterSignatures[id] == signature
            if let signature = signature, var existing = promoted[id], existing.layer.superlayer != nil, reuse {
                existing.ink = existing.ink.offsetBy(dx: want.box.minX - existing.box.minX, dy: want.box.minY - existing.box.minY)
                existing.box = want.box
                existing.origin = want.origin
                let anchor = CGPoint(x: want.box.minX + want.origin.x, y: want.box.minY + want.origin.y)
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                existing.layer.anchorPoint = CGPoint(x: (anchor.x - existing.ink.minX) / existing.ink.width, y: (anchor.y - existing.ink.minY) / existing.ink.height)
                if let index = order[String(id)] {
                    WidgetCompositor.setOrder(existing.layer, 1 + CGFloat(index.intValue) * 1e-6)
                }
                CATransaction.commit()
                promoted[id] = existing
                rasterSignatures[id] = signature
                continue
            }
            var ink = CGRect.zero, border = CGRect.zero
            var radii = [CGFloat](repeating: 0, count: 8)
            guard let image = document.copyImageOfElement(attribute: WidgetDOM.idAttribute, value: String(id), scale: WidgetCompositor.rasterScale(scale, want.maxScale), inkBounds: &ink, borderBox: &border, radii: &radii, bareShadows: owns) else {
                if let old = promoted[id] {
                    removeEffectAnimations(id)
                    old.effects?.backdropLayer?.twRemoveFromSuperlayer()
                    old.layer.twRemoveFromSuperlayer()
                }
                promoted[id] = nil
                continue
            }
            WidgetCompositor.countPromotion()
            let layer = promoted[id]?.layer ?? CALayer()
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            layer.bounds = CGRect(origin: .zero, size: ink.size)
            var effects = promoted[id]?.effects
            if owns || !backdrop.isEmpty {
                let fx = effects ?? WidgetEffectLayers()
                let base = animator.base(id)
                let template = effectTemplates[id] ?? WidgetEffects()
                let initial = owns ? WidgetEffects.parse(filter: resolved(base["filter"] ?? declarations[id]?["filter"], id: id), shadow: resolved(base["box-shadow"] ?? declarations[id]?["box-shadow"], id: id))?.aligned(to: template) ?? template : WidgetEffects()
                fx.configure(layer: layer, effects: initial, backdrop: backdrop, box: border.offsetBy(dx: -ink.minX, dy: -ink.minY), radii: radii, maxReach: effectOwners[id] ?? 0)
                effects = fx
            } else if let fx = effects {
                fx.reset(layer)
                effects = nil
            }
            let target = effects?.content ?? layer
            if target !== layer {
                layer.contents = nil
            }
            let installed = WidgetSurfaceImage.contents(image, display: displaySpace)
            target.contents = installed
            target.setValue(CFGetTypeID(installed as CFTypeRef) == CGImage.typeID ? nil : image, forKey: WidgetSurfaceImage.imageKey)
            target.contentsScale = WidgetCompositor.rasterScale(scale, want.maxScale)
            target.contentsGravity = .resize
            target.minificationFilter = want.maxScale > 1 && ProcessInfo.processInfo.environment["TW_LINEAR"] == nil ? .trilinear : .linear
            let anchor = CGPoint(x: border.minX + want.origin.x, y: border.minY + want.origin.y)
            layer.anchorPoint = CGPoint(x: (anchor.x - ink.minX) / ink.width, y: (anchor.y - ink.minY) / ink.height)
            layer.position = anchor
            if let index = order[String(id)] {
                WidgetCompositor.setOrder(layer, 1 + CGFloat(index.intValue) * 1e-6)
            } else if promoted[id] == nil {
                WidgetCompositor.setOrder(layer, 1)
            }
            CATransaction.commit()
            if promoted[id] == nil {
                promotions += 1
            }
            promoted[id] = Promoted(layer: layer, box: border, origin: want.origin, ink: ink, effects: effects, template: owns ? effectTemplates[id] : nil)
            rasterSignatures[id] = signature
            localRasters[id] = localKeys[id].map { LocalRaster(key: $0, space: spaceGeneration, pixels: image.width * image.height) }
        }
        for id in rasterSignatures.keys where promoted[id] == nil {
            rasterSignatures[id] = nil
        }
        for id in localRasters.keys where promoted[id] == nil {
            localRasters[id] = nil
        }
        var usedContainers: Set<ObjectIdentifier> = []
        for container in containers.values {
            WidgetCompositor.setOrder(container, 0)
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        var parents: [Int: Promoted] = [:]
        var containedClips: [Clip] = []
        for (id, item) in promoted {
            var walk = dom.node(id)?.parent
            while let node = walk, parents[id] == nil {
                parents[id] = promoted[dom.publicId(node)]
                walk = node.parent
            }
            if parents[id] == nil, let clip = clips[id], !containedClips.contains(clip), !WidgetCompositor.clipIsInert(clip, reach: chosenReach[id], box: item.box, ink: item.ink, scale: wanted[id]?.maxScale ?? 1) {
                containedClips.append(clip)
            }
        }
        for (id, item) in promoted.sorted(by: { WidgetDOM.depth(of: dom.node($0.key) ?? dom.body) < WidgetDOM.depth(of: dom.node($1.key) ?? dom.body) }) {
            let anchor = CGPoint(x: item.box.minX + item.origin.x, y: item.box.minY + item.origin.y)
            if let parent = parents[id] {
                if item.layer.superlayer !== parent.layer {
                    parent.layer.twAddSublayer(item.layer)
                }
                item.layer.position = CGPoint(x: anchor.x - parent.ink.minX, y: anchor.y - parent.ink.minY)
            } else if let clip = clips[id], containedClips.contains(clip) {
                let container = clipContainer(clip, host: host)
                usedContainers.insert(ObjectIdentifier(container))
                if item.layer.superlayer !== container {
                    container.twAddSublayer(item.layer)
                }
                item.layer.position = CGPoint(x: anchor.x - clip.rect.minX, y: anchor.y - clip.rect.minY)
                WidgetCompositor.setOrder(container, min(WidgetCompositor.order(container) == 0 ? WidgetCompositor.order(item.layer) : WidgetCompositor.order(container), WidgetCompositor.order(item.layer)))
            } else {
                if item.layer.superlayer !== host {
                    host.twAddSublayer(item.layer)
                }
                item.layer.position = anchor
            }
        }
        for item in promoted.values {
            guard let effects = item.effects, let backdrop = effects.backdropLayer else {
                continue
            }
            guard let parent = item.layer.superlayer else {
                backdrop.twRemoveFromSuperlayer()
                continue
            }
            if backdrop.superlayer !== parent {
                parent.twInsertSublayer(backdrop, below: item.layer)
            }
            backdrop.frame = item.layer.convert(effects.box, to: parent)
            WidgetCompositor.setOrder(backdrop, WidgetCompositor.order(item.layer) - 1e-7)
        }
        for (key, container) in containers where !usedContainers.contains(ObjectIdentifier(container)) {
            container.twRemoveFromSuperlayer()
            containers[key] = nil
        }
        clipOf = clips.filter { entry in promoted[entry.key].map { $0.layer.superlayer !== host } ?? false }
        promotedClips = clips.filter { promoted[$0.key] != nil }
        ignoredIDs = Set(promoted.keys.map { String($0) })
        let members = Set(promoted.values.map { ObjectIdentifier($0.layer) }).union(containers.values.map { ObjectIdentifier($0) }).union(promoted.values.compactMap { $0.effects?.backdropLayer.map { ObjectIdentifier($0) } })
        WidgetCompositor.orderSublayers(of: host, members: members)
        for container in containers.values {
            WidgetCompositor.orderSublayers(of: container, members: members)
        }
        for item in promoted.values where item.layer.twSublayers?.isEmpty == false {
            WidgetCompositor.orderSublayers(of: item.layer, members: members)
        }
        CATransaction.commit()
    }

    private var displaySpace: CGColorSpace?
    private(set) var spaceGeneration = 0

    static let orderKey = "twOrder"

    static func order(_ layer: CALayer) -> CGFloat {
        return (layer.value(forKey: orderKey) as? CGFloat) ?? layer.zPosition
    }

    static func flattens(_ layer: CALayer) -> Bool {
        if layer.filters?.isEmpty == false || layer.backgroundFilters?.isEmpty == false {
            return true
        }
        return layer.twSublayers?.contains { $0.backgroundFilters?.isEmpty == false || $0.filters?.isEmpty == false } == true
    }

    static let skipsUnchangedOrder = ProcessInfo.processInfo.environment["TW_ORDER_ALWAYS"] == nil

    static func setOrder(_ layer: CALayer, _ value: CGFloat) {
        if skipsUnchangedOrder, (layer.value(forKey: orderKey) as? CGFloat) == value {
            return
        }
        layer.setValue(value, forKey: orderKey)
    }

    static func orderSublayers(of parent: CALayer, members: Set<ObjectIdentifier>) {
        let children = (parent.twSublayers ?? []).filter { members.contains(ObjectIdentifier($0)) }
        let flat = children.contains { flattens($0) }
        for child in children {
            let z = flat ? 0 : order(child)
            if child.zPosition != z {
                child.zPosition = z
            }
        }
        var sorted = true
        for index in children.indices.dropFirst() where order(children[index - 1]) > order(children[index]) {
            sorted = false
            break
        }
        if sorted || children.contains(where: { $0.animationKeys()?.isEmpty == false }) && !children.contains(where: { $0.zPosition != order($0) }) {
            return
        }
        for child in children.enumerated().sorted(by: { (order($0.element), $0.offset) < (order($1.element), $1.offset) }).map({ $0.element }) {
            parent.twAddSublayer(child)
        }
    }

    static func scriptedRasterMagnification(_ transform: CGAffineTransform) -> CGFloat {
        if transform.a == 1 && transform.b == 0 && transform.c == 0 && transform.d == 1 {
            return 1
        }
        let x = transform.a * transform.a + transform.b * transform.b
        let y = transform.c * transform.c + transform.d * transform.d
        let cross = transform.a * transform.c + transform.b * transform.d
        let stretch = sqrt((x + y + hypot(x - y, 2 * cross)) / 2)
        return min(3, max(1, stretch * 1.25))
    }

    static func rasterScale(_ display: CGFloat, _ magnification: CGFloat) -> CGFloat {
        let exact = display * magnification
        guard magnification > 1, exact.isFinite else {
            return exact
        }
        return max(exact, ((exact * 4) - 1e-6).rounded(.up) / 4)
    }

    struct Clip: Equatable {
        let rect: CGRect
        let radii: [CGFloat]
    }

    private var containers: [String: CALayer] = [:]

    static func clipIsInert(_ clip: Clip, reach: CGRect?, box: CGRect, ink: CGRect, scale: CGFloat) -> Bool {
        guard WidgetCompositor.inertClips, let reach = reach, !reach.isNull, !ink.isNull, clip.radii.count == 8 else {
            return false
        }
        let outset = max(0, box.minX - ink.minX, box.minY - ink.minY, ink.maxX - box.maxX, ink.maxY - box.maxY)
        let pad = outset * max(1, scale) * 1.5 + 0.5
        let covered = reach.insetBy(dx: -pad, dy: -pad)
        guard clip.rect.contains(covered) else {
            return false
        }
        let r = clip.radii
        let corners = [
            CGRect(x: clip.rect.minX, y: clip.rect.minY, width: r[0], height: r[1]),
            CGRect(x: clip.rect.maxX - r[2], y: clip.rect.minY, width: r[2], height: r[3]),
            CGRect(x: clip.rect.maxX - r[4], y: clip.rect.maxY - r[5], width: r[4], height: r[5]),
            CGRect(x: clip.rect.minX, y: clip.rect.maxY - r[7], width: r[6], height: r[7]),
        ]
        return !corners.contains { $0.width > 0 && $0.height > 0 && $0.intersects(covered) }
    }

    static func cornerFreeBands(_ clip: Clip, containing base: CGRect) -> [CGRect] {
        guard clip.radii.count == 8 else {
            return []
        }
        let r = clip.radii.map { max(0, $0) }
        let top = max(r[1], r[3]), bottom = max(r[5], r[7]), left = max(r[0], r[6]), right = max(r[2], r[4])
        let bands = [
            CGRect(x: clip.rect.minX, y: clip.rect.minY + top, width: clip.rect.width, height: clip.rect.height - top - bottom),
            CGRect(x: clip.rect.minX + left, y: clip.rect.minY, width: clip.rect.width - left - right, height: clip.rect.height),
        ].map { $0.insetBy(dx: 0.5, dy: 0.5) }
        return bands.filter { $0.width > 0 && $0.height > 0 && $0.contains(base) }
    }

    static let inertClips = ProcessInfo.processInfo.environment["TW_KEEP_CLIPS"] == nil
    private var clipOf: [Int: Clip] = [:]

    private func clipContainer(_ clip: Clip, host: CALayer) -> CALayer {
        let key = "\(clip.rect)|\(clip.radii)"
        if let existing = containers[key] {
            return existing
        }
        let container = CALayer()
        container.anchorPoint = .zero
        container.frame = clip.rect
        container.masksToBounds = true
        if clip.radii.contains(where: { $0 > 0 }) {
            let mask = CAShapeLayer()
            mask.frame = CGRect(origin: .zero, size: clip.rect.size)
            mask.path = WidgetCompositor.roundedPath(CGRect(origin: .zero, size: clip.rect.size), clip.radii)
            container.mask = mask
        }
        host.twAddSublayer(container)
        containers[key] = container
        return container
    }

    static func roundedPath(_ rect: CGRect, _ r: [CGFloat]) -> CGPath {
        let path = CGMutablePath()
        guard rect.width > 0, rect.height > 0 else {
            return path
        }
        let k: CGFloat = 0.5522847498
        let hw = rect.width / 2, hh = rect.height / 2
        let tlx = min(max(r[0], 0), hw), tly = min(max(r[1], 0), hh)
        let trx = min(max(r[2], 0), hw), try_ = min(max(r[3], 0), hh)
        let brx = min(max(r[4], 0), hw), bry = min(max(r[5], 0), hh)
        let blx = min(max(r[6], 0), hw), bly = min(max(r[7], 0), hh)
        let x0 = rect.minX, y0 = rect.minY, x1 = rect.maxX, y1 = rect.maxY
        path.move(to: CGPoint(x: x0 + tlx, y: y0))
        path.addLine(to: CGPoint(x: x1 - trx, y: y0))
        if trx > 0 && try_ > 0 {
            path.addCurve(to: CGPoint(x: x1, y: y0 + try_), control1: CGPoint(x: x1 - trx + trx * k, y: y0), control2: CGPoint(x: x1, y: y0 + try_ - try_ * k))
        }
        path.addLine(to: CGPoint(x: x1, y: y1 - bry))
        if brx > 0 && bry > 0 {
            path.addCurve(to: CGPoint(x: x1 - brx, y: y1), control1: CGPoint(x: x1, y: y1 - bry + bry * k), control2: CGPoint(x: x1 - brx + brx * k, y: y1))
        }
        path.addLine(to: CGPoint(x: x0 + blx, y: y1))
        if blx > 0 && bly > 0 {
            path.addCurve(to: CGPoint(x: x0, y: y1 - bly), control1: CGPoint(x: x0 + blx - blx * k, y: y1), control2: CGPoint(x: x0, y: y1 - bly + bly * k))
        }
        path.addLine(to: CGPoint(x: x0, y: y0 + tly))
        if tlx > 0 && tly > 0 {
            path.addCurve(to: CGPoint(x: x0 + tlx, y: y0), control1: CGPoint(x: x0, y: y0 + tly - tly * k), control2: CGPoint(x: x0 + tlx - tlx * k, y: y0))
        }
        path.closeSubpath()
        return path
    }

    private weak var currentDOM: WidgetDOM?

    func resolved(_ text: String?, id: Int) -> String? {
        guard let text = text, CSSTransform.needsResolving(text) else {
            return text
        }
        let node = currentDOM?.node(id)
        return CSSTransform.resolve(text) { name in
            var walk = node
            while let current = walk {
                if let value = current.declarations.last(where: { $0.name == name })?.value {
                    return value
                }
                walk = current.parent
            }
            return nil
        }
    }

    func parse(_ text: String?, box: CGSize, id: Int) -> CGAffineTransform? {
        guard let text = text else {
            return CSSTransform.parse(nil, box: box)
        }
        guard let resolved = resolved(text, id: id) else {
            return nil
        }
        return cachedTransform(resolved, box: box)
    }

    private struct TransformKey: Hashable {
        let text: String
        let width: CGFloat
        let height: CGFloat
    }

    private var transformCache: [TransformKey: CGAffineTransform?] = [:]

    private func cachedTransform(_ text: String?, box: CGSize) -> CGAffineTransform? {
        guard let text = text else {
            return CSSTransform.parse(nil, box: box)
        }
        let key = TransformKey(text: text, width: box.width, height: box.height)
        if let known = transformCache[key] {
            return known
        }
        let parsed = CSSTransform.parse(text, box: box)
        if transformCache.count > 512 {
            transformCache.removeAll(keepingCapacity: true)
        }
        transformCache[key] = parsed
        return parsed
    }

    private var rasterSignatures: [Int: Int] = [:]
    private struct LocalRaster {
        let key: Data
        let space: Int
        let pixels: Int
    }
    private var localRasters: [Int: LocalRaster] = [:]
    private var localObservations: [Int: (key: Data, origin: CGPoint)] = [:]
    var styleKey = 0

    static func contentSignature(_ id: Int, dom: WidgetDOM, box: CGRect, scale: CGFloat, styleKey: Int) -> Int? {
        var hasher = Hasher()
        hasher.combine(styleKey)
        hasher.combine(box.width)
        hasher.combine(box.height)
        hasher.combine(scale)
        guard let root = dom.node(id) else {
            return nil
        }
        var cacheable = true
        func visit(_ node: WidgetNode, top: Bool) {
            if node.tag == "canvas" {
                cacheable = false
            }
            hasher.combine(node.tag)
            hasher.combine(node.text)
            for attribute in node.attributes {
                hasher.combine(attribute.name)
                hasher.combine(attribute.value)
            }
            for declaration in node.declarations where !(top && (declaration.name == "transform" || declaration.name == "opacity")) {
                hasher.combine(declaration.name)
                hasher.combine(declaration.value)
            }
            hasher.combine(node.children.count)
            for child in node.children {
                visit(child, top: false)
            }
        }
        visit(root, top: true)
        var node = root
        while let parent = node.parent {
            hasher.combine(parent.children.firstIndex { $0 === node } ?? -1)
            hasher.combine(parent.children.count)
            hasher.combine(parent.tag)
            for attribute in parent.attributes {
                hasher.combine(attribute.name)
                hasher.combine(attribute.value)
            }
            for declaration in parent.declarations {
                hasher.combine(declaration.name)
                hasher.combine(declaration.value)
            }
            node = parent
        }
        return cacheable ? hasher.finalize() : nil
    }

    func hitCandidates(for point: CGPoint) -> [(id: Int, point: CGPoint)] {
        var result: [(id: Int, point: CGPoint)] = []
        for (id, item) in promoted.sorted(by: { WidgetCompositor.order($0.value.layer) > WidgetCompositor.order($1.value.layer) }) {
            let shown = item.layer.presentation() ?? item.layer
            if shown.opacity <= 0 {
                continue
            }
            if let clip = clipOf[id], !clip.rect.contains(point) {
                continue
            }
            let local = shown.convert(point, from: host?.presentation() ?? host)
            if item.layer.bounds.contains(local) {
                let mapped = CGPoint(x: item.ink.minX + local.x, y: item.ink.minY + local.y)
                if item.box.contains(mapped) {
                    result.append((id, mapped))
                }
            }
        }
        return result
    }

    func draw(into ctx: CGContext) {
        let layers = Set(promoted.values.map { ObjectIdentifier($0.layer) })
        let backdrops = Dictionary(promoted.values.compactMap { item in item.effects?.backdropLayer.map { (ObjectIdentifier(item.layer), $0) } }, uniquingKeysWith: { a, _ in a })
        func body(_ model: CALayer, _ layer: CALayer, _ target: CGContext) {
            let size = layer.bounds.size
            for shadow in (model.twSublayers ?? []) where shadow.name == WidgetEffectLayers.shadowName {
                WidgetEffectLayers.paintShadow(shadow.presentation() ?? shadow, in: target)
            }
            if let contents = WidgetSurfaceImage.image(of: model) ?? model.twSublayers?.first(where: { $0.name == "tw.content" }).flatMap({ WidgetSurfaceImage.image(of: $0) }), CFGetTypeID(contents as CFTypeRef) == CGImage.typeID {
                target.saveGState()
                target.translateBy(x: 0, y: size.height)
                target.scaleBy(x: 1, y: -1)
                target.draw(contents as! CGImage, in: CGRect(origin: .zero, size: size))
                target.restoreGState()
            }
            for child in (model.twSublayers ?? []).filter({ layers.contains(ObjectIdentifier($0)) }).sorted(by: { WidgetCompositor.order($0) < WidgetCompositor.order($1) }) {
                paint(child, into: target)
            }
        }
        func paint(_ model: CALayer, into target: CGContext) {
            let layer = (model.animationKeys()?.isEmpty == false || model.twSublayers?.contains { $0.animationKeys()?.isEmpty == false } == true ? model.presentation() : nil) ?? model
            let size = layer.bounds.size
            if let backdrop = backdrops[ObjectIdentifier(model)], backdrop.superlayer != nil {
                WidgetEffectLayers.paintBackdrop(backdrop.presentation() ?? backdrop, in: target)
            }
            target.saveGState()
            target.translateBy(x: layer.position.x, y: layer.position.y)
            target.concatenate(CATransform3DGetAffineTransform(layer.transform))
            target.translateBy(x: -layer.anchorPoint.x * size.width, y: -layer.anchorPoint.y * size.height)
            target.setAlpha(CGFloat(layer.opacity))
            WidgetEffectLayers.applyLayerShadow(layer, in: target)
            target.beginTransparencyLayer(auxiliaryInfo: nil)
            if let filters = layer.filters as? [CIFilter], !filters.isEmpty {
                WidgetEffectLayers.paintFiltered(filters, bounds: layer.bounds, in: target) { offscreen in
                    body(model, layer, offscreen)
                }
            } else {
                body(model, layer, target)
            }
            target.endTransparencyLayer()
            target.restoreGState()
        }
        for item in promoted.values.filter({ $0.layer.superlayer === host }).sorted(by: { WidgetCompositor.order($0.layer) < WidgetCompositor.order($1.layer) }) {
            paint(item.layer, into: ctx)
        }
        for container in containers.values.sorted(by: { WidgetCompositor.order($0) < WidgetCompositor.order($1) }) {
            ctx.saveGState()
            ctx.translateBy(x: container.frame.minX, y: container.frame.minY)
            let clip = clipOf.values.first { $0.rect == container.frame }
            ctx.addPath(WidgetCompositor.roundedPath(CGRect(origin: .zero, size: container.bounds.size), clip?.radii ?? [CGFloat](repeating: 0, count: 8)))
            ctx.twClip()
            for child in (container.twSublayers ?? []).filter({ layers.contains(ObjectIdentifier($0)) }).sorted(by: { WidgetCompositor.order($0) < WidgetCompositor.order($1) }) {
                paint(child, into: ctx)
            }
            ctx.restoreGState()
        }
    }

    func apply(_ overrides: [Int: [(String, String)]], bases: (Int) -> [String: String]) -> [Int: [(String, String)]] {
        if promoted.isEmpty {
            return overrides
        }
        var rest = overrides
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (id, item) in promoted {
            rest[id] = nil
            if scripted[id] != nil {
                continue
            }
            let values = driven[id] != nil ? [] : overrides[id] ?? []
            let base = bases(id)
            let transformText = values.last { $0.0 == "transform" }?.1 ?? base["transform"]
            let opacityText = values.last { $0.0 == "opacity" }?.1 ?? base["opacity"]
            let transform = parse(transformText, box: item.box.size, id: id) ?? .identity
            item.layer.setAffineTransform(transform)
            item.layer.opacity = Float(opacityText.flatMap { Double($0) } ?? 1)
            if item.ownsEffects, let effects = item.effects {
                let filterText = values.last { $0.0 == "filter" }?.1 ?? base["filter"]
                let shadowText = values.last { $0.0 == "box-shadow" }?.1 ?? base["box-shadow"]
                if let template = item.template, let current = WidgetEffects.parse(filter: resolved(filterText, id: id), shadow: resolved(shadowText, id: id))?.aligned(to: template), current != effects.effects {
                    effects.apply(current, to: item.layer)
                }
            }
        }
        CATransaction.commit()
        return rest
    }

    private var ancestorCache: [Int: Set<Int>] = [:]

    static func ancestorUnion(_ ids: Set<Int>, ancestors: (Int) -> Set<Int>) -> Set<Int> {
        var union = Set<Int>()
        for id in ids {
            union.formUnion(ancestors(id))
        }
        return union
    }

    static func conflicts(_ id: Int, related: Set<Int>, relatedAncestors: Set<Int>, ancestors: Set<Int>) -> Bool {
        return relatedAncestors.contains(id) || !ancestors.isDisjoint(with: related)
    }

    private func ancestors(of id: Int, dom: WidgetDOM) -> Set<Int> {
        if let known = ancestorCache[id] {
            return known
        }
        _ = isAncestor(-1, of: id, dom: dom)
        return ancestorCache[id] ?? []
    }

    private func isAncestor(_ ancestor: Int, of id: Int, dom: WidgetDOM) -> Bool {
        if let known = ancestorCache[id] {
            return known.contains(ancestor)
        }
        var ancestors = Set<Int>()
        if var node = dom.node(id) {
            while let parent = node.parent {
                ancestors.insert(dom.publicId(parent))
                node = parent
            }
        }
        ancestorCache[id] = ancestors
        return ancestors.contains(ancestor)
    }
}
