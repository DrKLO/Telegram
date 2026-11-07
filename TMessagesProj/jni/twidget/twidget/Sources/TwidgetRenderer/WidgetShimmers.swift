import Foundation
import QuartzCore
import ImageIO
import LiteHTML

final class WidgetShimmers {
    static let enabled = ProcessInfo.processInfo.environment["TW_NO_SHIMMER"] == nil
    static let properties: Set<String> = ["background-position", "background-position-x"]

    private struct Item {
        let container: CALayer
        let tile: CALayer
        let tileRect: CGRect
        let snap: CGFloat
        let area: CGRect
        let clip: CGRect
        let radius: CGFloat
        var x: CGFloat
    }

    private var items: [Int: Item] = [:]
    private var refused: Set<Int> = []
    private var rebuilds: [Int: [CFTimeInterval]] = [:]
    static let maxItems = 64
    static let maxRebuildsPerSecond = 4
    private weak var host: CALayer?
    private(set) var updates = 0
    private var values: [Int: String] = [:]
    private(set) var checks = 0
    private(set) var mismatches = 0
    private(set) var worst = 0
    static let tolerance = ProcessInfo.processInfo.environment["TW_SHIMMER_T"].flatMap { Int($0) } ?? 1

    var ids: Set<Int> {
        return Set(items.keys)
    }

    func reset() {
        for item in items.values {
            item.container.twRemoveFromSuperlayer()
        }
        items.removeAll()
    }

    func update(document: LHDocument, animator: WidgetAnimator, width: CGFloat, host: CALayer?, scale: CGFloat, composited: Set<Int>, suspended: Bool) -> Bool {
        guard WidgetShimmers.enabled else {
            return false
        }
        var wanted: Set<Int> = []
        if !suspended {
            for id in animator.animatedIDs() where !refused.contains(id) && !composited.contains(id) && animator.animatesOnly(id, WidgetShimmers.properties) {
                wanted.insert(id)
            }
        }
        var changed = false
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if self.host !== host {
            self.host = host
            for item in items.values {
                host?.twAddSublayer(item.container)
            }
        }
        let now = CACurrentMediaTime()
        for id in Set(items.keys).subtracting(wanted) {
            items.removeValue(forKey: id)?.container.twRemoveFromSuperlayer()
            values.removeValue(forKey: id)
            changed = true
            var recent = (rebuilds[id] ?? []).filter { now - $0 < 1 }
            recent.append(now)
            rebuilds[id] = recent
            if recent.count > WidgetShimmers.maxRebuildsPerSecond {
                refused.insert(id)
            }
        }
        for (id, item) in items {
            let geometry = document.backgroundTile(attribute: WidgetDOM.idAttribute, value: String(id), maxWidth: width, scale: 0)
            if let geometry = geometry, geometry.area == item.area, geometry.clip == item.clip, geometry.tile.size == item.tileRect.size, geometry.tile.minY == item.tileRect.minY, (0..<8).allSatisfy({ abs(geometry.radius($0) - item.radius) < 0.01 }) {
                continue
            }
            items.removeValue(forKey: id)?.container.twRemoveFromSuperlayer()
            changed = true
            var recent = (rebuilds[id] ?? []).filter { now - $0 < 1 }
            recent.append(now)
            rebuilds[id] = recent
            if geometry == nil || recent.count > WidgetShimmers.maxRebuildsPerSecond {
                refused.insert(id)
                wanted.remove(id)
            }
        }
        for id in wanted.sorted() where items[id] == nil {
            guard items.count < WidgetShimmers.maxItems else {
                break
            }
            guard let tile = document.backgroundTile(attribute: WidgetDOM.idAttribute, value: String(id), maxWidth: width, scale: scale) else {
                refused.insert(id)
                continue
            }
            let radii = (0..<8).map { tile.radius($0) }
            guard radii.allSatisfy({ abs($0 - radii[0]) < 0.01 }) else {
                refused.insert(id)
                continue
            }
            let container = CALayer()
            container.frame = tile.clip
            container.masksToBounds = true
            container.cornerRadius = radii[0]
            let layer = CALayer()
            layer.contents = tile.image
            layer.contentsScale = scale
            let pixels = CGSize(width: CGFloat(tile.image.width) / scale, height: CGFloat(tile.image.height) / scale)
            layer.frame = CGRect(origin: CGPoint(x: tile.tile.minX - tile.clip.minX, y: tile.tile.minY - tile.clip.minY), size: pixels)
            container.addSublayer(layer)
            host?.twAddSublayer(container)
            var item = Item(container: container, tile: layer, tileRect: tile.tile, snap: max(scale, 1), area: tile.area, clip: tile.clip, radius: radii[0], x: tile.tile.minX)
            if let value = values[id], let x = position(value, item: item) {
                layer.frame.origin.x = x - tile.clip.minX
                item.x = x
            }
            items[id] = item
            changed = true
        }
        CATransaction.commit()
        return changed
    }

    private(set) var applyCalls = 0
    private(set) var applyMilliseconds = 0.0

    func apply(_ overrides: inout [Int: [(String, String)]]) {
        guard !items.isEmpty else {
            return
        }
        let started = CACurrentMediaTime()
        defer {
            applyCalls += 1
            applyMilliseconds += (CACurrentMediaTime() - started) * 1000
        }
        var moved = false
        var failed: Set<Int> = []
        for (id, item) in items {
            guard let list = overrides[id] else {
                continue
            }
            var rest: [(String, String)] = []
            for (name, value) in list {
                if WidgetShimmers.properties.contains(name) {
                    guard let x = position(value, item: item) else {
                        failed.insert(id)
                        rest.append((name, value))
                        continue
                    }
                    if x != item.x {
                        if !moved {
                            CATransaction.begin()
                            CATransaction.setDisableActions(true)
                            moved = true
                        }
                        item.tile.frame.origin.x = x - item.clip.minX
                        items[id]?.x = x
                        values[id] = value
                        updates += 1
                    }
                } else {
                    rest.append((name, value))
                }
            }
            overrides[id] = rest.isEmpty ? nil : rest
        }
        if moved {
            CATransaction.commit()
        }
        if !failed.isEmpty {
            CATransaction.begin()
            CATransaction.setDisableActions(true)
            for id in failed {
                items.removeValue(forKey: id)?.container.twRemoveFromSuperlayer()
                refused.insert(id)
            }
            CATransaction.commit()
            dropped = true
        }
    }

    var dropped = false

    var report: String {
        return "shimmercheck: \(checks) checks, \(mismatches) differ, worst \(worst), restore failures \(restoreFailures), apply \(applyCalls) calls \(String(format: "%.4f", applyCalls > 0 ? applyMilliseconds / Double(applyCalls) : 0)) ms each, \(updates) moves"
    }

    private(set) var restoreFailures = 0

    func verify(document: LHDocument, width: CGFloat) {
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let scale: CGFloat = 2
        for (id, item) in items {
            guard let value = values[id], let parsed = number(value) else {
                continue
            }
            let mismatchesBefore = document.restoreMismatches
            let expectedImage = document.copyImage(backgroundPositionX: parsed.value, percent: parsed.percent, attribute: WidgetDOM.idAttribute, value: String(id), maxWidth: width, rect: item.clip, scale: scale)
            restoreFailures += document.restoreMismatches - mismatchesBefore
            guard let expected = expectedImage,
                  let base = document.copyTileImage(maxWidth: width, rect: item.clip, scale: scale),
                  expected.width == base.width, expected.height == base.height,
                  let a = CGContext(data: nil, width: base.width, height: base.height, bitsPerComponent: 8, bytesPerRow: base.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue),
                  let b = CGContext(data: nil, width: base.width, height: base.height, bitsPerComponent: 8, bytesPerRow: base.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
                continue
            }
            let full = CGRect(x: 0, y: 0, width: base.width, height: base.height)
            a.draw(expected, in: full)
            b.draw(base, in: full)
            b.saveGState()
            b.translateBy(x: 0, y: CGFloat(base.height))
            b.scaleBy(x: scale, y: -scale)
            item.container.render(in: b)
            b.restoreGState()
            guard let pa = a.data?.bindMemory(to: UInt8.self, capacity: base.width * base.height * 4), let pb = b.data?.bindMemory(to: UInt8.self, capacity: base.width * base.height * 4) else {
                continue
            }
            var local = 0, off = 0
            let w = base.width, h = base.height
            let edge = 2, corner = Int(ceil(item.container.cornerRadius * scale)) + 2
            for y in 0..<h {
                let nearY = y < edge || y >= h - edge
                let cornerY = y < corner || y >= h - corner
                for x in 0..<w {
                    if nearY || x < edge || x >= w - edge || (cornerY && (x < corner || x >= w - corner)) {
                        continue
                    }
                    let i = (y * w + x) * 4
                    for c in 0..<4 {
                        let d = abs(Int(pa[i + c]) - Int(pb[i + c]))
                        local = max(local, d)
                        if d > WidgetShimmers.tolerance {
                            off += 1
                        }
                    }
                }
            }
            checks += 1
            worst = max(worst, local)
            if off > 0 {
                mismatches += 1
                if let dir = ProcessInfo.processInfo.environment["TW_SHIMMER_DUMP"], mismatches <= 3 {
                    for (name, ctx) in [("expected", a), ("layers", b)] {
                        if let image = ctx.makeImage(), let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: dir + "/shimmer-\(name)\(mismatches).png") as CFURL, "public.png" as CFString, 1, nil) {
                            CGImageDestinationAddImage(dest, image, nil)
                            CGImageDestinationFinalize(dest)
                        }
                    }
                }
            }
        }
    }

    private func number(_ value: String) -> (value: Double, percent: Bool)? {
        let scalars = Array(value.unicodeScalars)
        var index = 0
        while index < scalars.count, scalars[index] == " " {
            index += 1
        }
        let start = index
        if index < scalars.count, scalars[index] == "-" || scalars[index] == "+" {
            index += 1
        }
        while index < scalars.count, ("0"..."9").contains(scalars[index]) || scalars[index] == "." {
            index += 1
        }
        guard index > start, let number = Double(String(String.UnicodeScalarView(scalars[start..<index]))) else {
            return nil
        }
        var unitEnd = index
        while unitEnd < scalars.count, ("a"..."z").contains(scalars[unitEnd]) || scalars[unitEnd] == "%" {
            unitEnd += 1
        }
        switch String(String.UnicodeScalarView(scalars[index..<unitEnd])) {
        case "%":
            return (number, true)
        case "px":
            return (number, false)
        case "" where number == 0:
            return (0, false)
        default:
            return nil
        }
    }

    private func position(_ value: String, item: Item) -> CGFloat? {
        guard let parsed = number(value) else {
            return nil
        }
        let x = parsed.percent ? item.area.minX + (item.area.width - item.tileRect.width) * CGFloat(parsed.value) / 100 : item.area.minX + CGFloat(parsed.value)
        return (x * item.snap).rounded() / item.snap
    }
}
