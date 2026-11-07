import AppKit
import LiteHTML
import WidgetSandboxProtocol

final class WidgetMagicMove {
    struct Capture {
        let keyed: [String: Int]
        let ancestors: [String: Set<String>]
        let rects: [Int: CGRect]
        let presented: [String: CGRect]
        let signatures: [String: Int]
        var leaving: [MagicMoveStage.Snapshot] = []
        var previous: [String: MagicMoveStage.Snapshot] = [:]
        var changed: Set<String> = []
    }

    static let attribute = "data-transition"
    static let frozenTime = ProcessInfo.processInfo.environment["TW_MAGIC_TIME"].flatMap(Double.init)

    private let stage: MagicMoveStage
    private(set) var excluded: Set<Int> = []
    private(set) var transitions = 0
    private var changedAt: [String: CFTimeInterval] = [:]
    static let liveWindow: CFTimeInterval = 0.6

    var onFinish: (() -> Void)? {
        get { stage.onFinish }
        set { stage.onFinish = newValue }
    }

    init(host: CALayer?) {
        stage = MagicMoveStage(host: host)
    }

    var isActive: Bool {
        return stage.isActive
    }

    static func isStructural(_ ops: [WidgetOp], dom: WidgetDOM) -> Bool {
        func inside(_ id: Any?) -> Bool? {
            guard let id = (id as? NSNumber)?.intValue, var node = dom.node(id) else {
                return nil
            }
            while true {
                if node.attribute(attribute) == "move" {
                    return true
                }
                guard let parent = node.parent else {
                    return false
                }
                node = parent
            }
        }
        for op in ops {
            guard let kind = op["op"] as? String else {
                continue
            }
            switch kind {
            case "ins":
                if inside(op["id"]) == true || inside(op["parent"]) ?? true {
                    return true
                }
            case "rm", "data", "text":
                if inside(op["id"]) == true {
                    return true
                }
            case "attr", "rattr":
                if (op["name"] as? String) == "class", inside(op["id"]) == true {
                    return true
                }
            default:
                break
            }
        }
        return false
    }

    static func signature(_ node: WidgetNode) -> Int {
        var hasher = Hasher()
        func visit(_ node: WidgetNode) {
            hasher.combine(node.tag)
            hasher.combine(node.text)
            for attribute in node.attributes {
                hasher.combine(attribute.name)
                hasher.combine(attribute.value)
            }
            for declaration in node.declarations {
                hasher.combine(declaration.name)
                hasher.combine(declaration.value)
            }
            hasher.combine(node.children.count)
            for child in node.children {
                visit(child)
            }
        }
        visit(node)
        return hasher.finalize()
    }

    static func keyedNodes(_ dom: WidgetDOM, signatures: UnsafeMutablePointer<[String: Int]>? = nil) -> (keyed: [String: Int], ancestors: [String: Set<String>]) {
        var keyed: [String: Int] = [:]
        var ancestors: [String: Set<String>] = [:]
        var duplicates: Set<String> = []
        func key(_ node: WidgetNode) -> String? {
            if let value = node.attribute("data-key"), !value.isEmpty {
                return "k:" + value
            }
            if let value = node.attribute("id"), !value.isEmpty {
                return "i:" + value
            }
            return nil
        }
        func visit(_ node: WidgetNode, inside: Bool, chain: [String]) {
            if node.isText || node.svg && node.tag != "svg" {
                return
            }
            var chain = chain
            var inside = inside
            if inside, let k = key(node) {
                if keyed[k] != nil {
                    duplicates.insert(k)
                } else {
                    keyed[k] = dom.publicId(node)
                    ancestors[k] = Set(chain)
                    signatures?.pointee[k] = signature(node)
                }
                chain.append(k)
            }
            if node.attribute(attribute) == "move" {
                inside = true
            }
            for child in node.children {
                visit(child, inside: inside, chain: chain)
            }
        }
        visit(dom.body, inside: false, chain: [])
        for k in duplicates {
            keyed[k] = nil
            ancestors[k] = nil
        }
        return (keyed, ancestors)
    }

    private static func rects(_ document: LHDocument, of ids: [Int]) -> [Int: CGRect] {
        let wanted = Set(ids)
        return document.idBounds().filter { wanted.contains($0.key) }
    }

    private static func snapshot(_ document: LHDocument, id: Int, scale: CGFloat) -> MagicMoveStage.Snapshot? {
        var ink = CGRect.zero, border = CGRect.zero
        var radii = [CGFloat](repeating: 0, count: 8)
        guard let image = document.copyImageOfElement(attribute: WidgetDOM.idAttribute, value: String(id), scale: scale, inkBounds: &ink, borderBox: &border, radii: &radii) else {
            return nil
        }
        return MagicMoveStage.Snapshot(image: image, ink: ink, border: border, corners: MagicMoveCorners(radii: radii))
    }

    func capture(dom: WidgetDOM, document: LHDocument) -> Capture? {
        var signatures: [String: Int] = [:]
        let (keyed, ancestors) = WidgetMagicMove.keyedNodes(dom, signatures: &signatures)
        guard !keyed.isEmpty else {
            return nil
        }
        return Capture(keyed: keyed, ancestors: ancestors, rects: WidgetMagicMove.rects(document, of: Array(keyed.values)), presented: stage.presentedFrames(), signatures: signatures)
    }

    func collectLeaving(_ capture: inout Capture, dom: WidgetDOM, document: LHDocument, scale: CGFloat) {
        var signatures: [String: Int] = [:]
        let now = WidgetMagicMove.keyedNodes(dom, signatures: &signatures).keyed
        let gone = Set(capture.keyed.keys.filter { now[$0] == nil })
        capture.changed = Set(capture.signatures.compactMap { entry in signatures[entry.key].flatMap { $0 != entry.value ? entry.key : nil } })
        let moment = CACurrentMediaTime()
        changedAt = changedAt.filter { moment - $0.value < WidgetMagicMove.liveWindow }
        var live: Set<String> = []
        for key in capture.changed {
            if changedAt[key] != nil {
                live.insert(key)
            }
            changedAt[key] = moment
        }
        var pixels = 0
        var count = 0
        let ranked = capture.keyed.keys.sorted { a, b in
            let ga = gone.contains(a), gb = gone.contains(b)
            if ga != gb {
                return ga
            }
            let da = capture.ancestors[a]?.count ?? 0, db = capture.ancestors[b]?.count ?? 0
            return da != db ? da < db : a < b
        }
        for key in ranked {
            if gone.contains(key) ? !(capture.ancestors[key] ?? []).isDisjoint(with: gone) : !capture.changed.contains(key) || live.contains(key) {
                continue
            }
            guard let id = capture.keyed[key], let rect = capture.rects[id], rect.width > 0, rect.height > 0 else {
                continue
            }
            pixels += Int(rect.width * scale * rect.height * scale)
            count += 1
            if count > MagicMoveStage.maxLayers / 2 || pixels > MagicMoveStage.maxPixels / 2 {
                break
            }
            if stage.isInFlight(key) {
                if gone.contains(key), let presented = stage.presentedSnapshot(for: key, scale: scale) {
                    capture.leaving.append(presented)
                }
                continue
            }
            if let snapshot = WidgetMagicMove.snapshot(document, id: id, scale: scale) {
                if gone.contains(key) {
                    capture.leaving.append(snapshot)
                } else {
                    capture.previous[key] = snapshot
                }
            }
        }
    }

    func run(_ capture: Capture, dom: WidgetDOM, document: LHDocument, skip: Set<Int>, scale: CGFloat) -> Bool {
        let (keyed, ancestors) = WidgetMagicMove.keyedNodes(dom)
        let rects = WidgetMagicMove.rects(document, of: Array(keyed.values))
        var candidates: [String: (id: Int, from: CGRect?, rect: CGRect)] = [:]
        var inPlace: [String: (id: Int, from: CGRect?, rect: CGRect)] = [:]
        for (key, id) in keyed {
            guard !skip.contains(id), let rect = rects[id], rect.width > 0, rect.height > 0 else {
                continue
            }
            if let old = capture.presented[key] ?? capture.keyed[key].flatMap({ capture.rects[$0] }) {
                if MagicMoveStage.close(old, rect) {
                    if capture.changed.contains(key), capture.previous[key] != nil {
                        inPlace[key] = (id, old, rect)
                    }
                    continue
                }
                candidates[key] = (id, old, rect)
            } else {
                candidates[key] = (id, nil, rect)
            }
        }
        let chosen = Set(candidates.keys)
        candidates = candidates.filter { (ancestors[$0.key] ?? []).isDisjoint(with: chosen) }
        let riding = Set(chosen.flatMap { ancestors[$0] ?? [] })
        let enclosing = Set(inPlace.keys.flatMap { ancestors[$0] ?? [] })
        for (key, item) in inPlace where !riding.contains(key) && !enclosing.contains(key) && (ancestors[key] ?? []).isDisjoint(with: chosen) {
            candidates[key] = item
        }
        if candidates.isEmpty && capture.leaving.isEmpty {
            return false
        }
        var budget = MagicMoveStage.maxPixels
        var items: [MagicMoveStage.Item] = capture.leaving.map { .leave($0) }
        var exclusions: Set<Int> = []
        let paintOrder = document.paintOrder(attribute: WidgetDOM.idAttribute, values: Set(candidates.values.map { String($0.id) }))
        var moving: [(order: Int, item: MagicMoveStage.Item)] = []
        for (key, item) in candidates.sorted(by: { $0.value.rect.width * $0.value.rect.height > $1.value.rect.width * $1.value.rect.height }) {
            let pixels = Int((item.rect.width + 32) * scale * (item.rect.height + 32) * scale)
            guard items.count + moving.count < MagicMoveStage.maxLayers, pixels <= budget, let snapshot = WidgetMagicMove.snapshot(document, id: item.id, scale: scale) else {
                stage.cancel()
                excluded = []
                return true
            }
            budget -= pixels
            let order = paintOrder[String(item.id)]?.intValue ?? Int.max
            if let from = item.from {
                let old = capture.previous[key]
                let differs = capture.changed.contains(key) || old.map { MagicMoveGeometry.kind(from: $0.border, to: snapshot.border) == .resize } ?? false
                moving.append((order, .move(key: key, from: from, to: snapshot, previous: differs ? old : nil)))
            } else {
                moving.append((order, .enter(key: key, snapshot)))
            }
            exclusions.insert(item.id)
        }
        items += moving.enumerated().sorted { ($0.element.order, $0.offset) < ($1.element.order, $1.offset) }.map { $0.element.item }
        transitions += 1
        let begin = stage.clock()
        stage.play(items, scale: scale)
        if let frozen = WidgetMagicMove.frozenTime {
            stage.freeze(at: begin + frozen)
        }
        excluded = exclusions
        return true
    }

    func removeLayers() {
        stage.clear()
        excluded = []
    }

    func cancel() {
        stage.cancel()
        excluded = []
    }

    func draw(into ctx: CGContext) {
        stage.draw(into: ctx)
    }
}
