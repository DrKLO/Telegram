import Foundation
import QuartzCore
import ImageIO
import LiteHTML

final class WidgetSVGLayers {
    static let enabled = ProcessInfo.processInfo.environment["TW_NO_SVG_LAYERS"] == nil
    static let streakNeeded = 3

    private(set) var ids: Set<Int> = []
    private(set) var frames: [Int: CGRect] = [:]
    private var streaks: [Int: Int] = [:]
    private var refused: Set<Int> = []
    private var containers: [Int: CALayer] = [:]
    private weak var host: CALayer?

    func observe(_ touched: Set<Int>) -> Bool {
        guard WidgetSVGLayers.enabled else {
            return false
        }
        var next: [Int: Int] = [:]
        for id in touched where !refused.contains(id) {
            next[id] = (streaks[id] ?? 0) + 1
        }
        for id in ids where next[id] == nil {
            next[id] = WidgetSVGLayers.streakNeeded
        }
        streaks = next
        let wanted = Set(streaks.filter { $0.value >= WidgetSVGLayers.streakNeeded }.keys)
        let added = !wanted.subtracting(ids).isEmpty
        ids.formUnion(wanted)
        return added
    }

    private var suspended = false

    func setSuspended(_ value: Bool) {
        guard value != suspended else {
            return
        }
        suspended = value
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for layer in containers.values {
            layer.isHidden = value
        }
        CATransaction.commit()
    }

    static let check = ProcessInfo.processInfo.environment["TW_SVGCHECK"] != nil
    static let tolerance = ProcessInfo.processInfo.environment["TW_SVGCHECK_T"].flatMap { Int($0) } ?? 64
    private(set) var checks = 0
    private(set) var mismatches = 0
    private(set) var worstFraction = 0.0

    func verify(document: LHDocument, width: CGFloat) {
        guard WidgetSVGLayers.check, !suspended else {
            return
        }
        let scale: CGFloat = 2
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        for (id, container) in containers {
            guard let frame = frames[id], frame.width >= 1, frame.height >= 1,
                  let raster = document.copyImage(maxWidth: width, rect: frame, scale: scale),
                  let base = document.copyImage(maxWidth: width, rect: frame, scale: scale, excluding: [String(id)], attribute: WidgetDOM.idAttribute),
                  let context = CGContext(data: nil, width: raster.width, height: raster.height, bitsPerComponent: 8, bytesPerRow: raster.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue),
                  let reference = CGContext(data: nil, width: raster.width, height: raster.height, bitsPerComponent: 8, bytesPerRow: raster.width * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
                continue
            }
            let full = CGRect(x: 0, y: 0, width: raster.width, height: raster.height)
            reference.draw(raster, in: full)
            context.draw(base, in: full)
            context.saveGState()
            context.translateBy(x: 0, y: CGFloat(raster.height))
            context.scaleBy(x: scale, y: -scale)
            let hidden = container.isHidden
            container.isHidden = false
            container.render(in: context)
            container.isHidden = hidden
            context.restoreGState()
            guard let a = reference.data, let b = context.data else {
                continue
            }
            let count = raster.width * raster.height
            let pa = a.bindMemory(to: UInt8.self, capacity: count * 4), pb = b.bindMemory(to: UInt8.self, capacity: count * 4)
            var off = 0
            for i in 0..<(count * 4) where abs(Int(pa[i]) - Int(pb[i])) > WidgetSVGLayers.tolerance {
                off += 1
            }
            let fraction = Double(off) / Double(count)
            checks += 1
            worstFraction = max(worstFraction, fraction)
            WidgetSVGLayers.tally(checks: 1, differ: fraction > 0.002 ? 1 : 0)
            if fraction > 0.002 {
                mismatches += 1
                if let dir = ProcessInfo.processInfo.environment["TW_SVGCHECK_DUMP"], mismatches <= 3 {
                    for (name, ctx) in [("raster", reference), ("layers", context)] {
                        if let image = ctx.makeImage(), let dest = CGImageDestinationCreateWithURL(URL(fileURLWithPath: dir + "/svg-\(name)\(mismatches).png") as CFURL, "public.png" as CFString, 1, nil) {
                            CGImageDestinationAddImage(dest, image, nil)
                            CGImageDestinationFinalize(dest)
                        }
                    }
                }
            }
        }
    }

    private static let totalsLock = NSLock()
    private static var totalChecks = 0, totalDiffer = 0, totalVerified = 0, totalMismatched = 0, totalLayered = 0, totalOffMain = 0

    static func tally(checks: Int = 0, differ: Int = 0, verified: Int = 0, mismatched: Int = 0, layered: Int = 0) {
        totalsLock.lock()
        totalChecks += checks
        totalDiffer += differ
        totalVerified += verified
        totalMismatched += mismatched
        totalLayered += layered
        if layered > 0 && !Thread.isMainThread {
            totalOffMain += layered
        }
        totalsLock.unlock()
    }

    static var totals: String {
        totalsLock.lock()
        defer { totalsLock.unlock() }
        return "totals: verify \(totalVerified) frames, \(totalMismatched) differ | svgcheck \(totalChecks) checks, \(totalDiffer) differ | svg layer renders \(totalLayered) (\(totalOffMain) off main)"
    }

    var report: String {
        return String(format: "svgcheck: %d checks, %d differ, worst %.4f", checks, mismatches, worstFraction)
    }

    func covers(_ rect: CGRect) -> Bool {
        if suspended {
            return false
        }
        return frames.values.contains { $0.contains(rect) }
    }

    func reset() {
        for layer in containers.values {
            layer.twRemoveFromSuperlayer()
        }
        containers.removeAll()
        frames.removeAll()
        ids.removeAll()
        streaks.removeAll()
    }

    func render(document: LHDocument, width: CGFloat, host: CALayer?, scale: CGFloat, dom: WidgetDOM, composited: Set<Int>) -> Bool {
        guard !ids.isEmpty else {
            return false
        }
        var dropped = false
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if self.host !== host {
            self.host = host
            for layer in containers.values {
                host?.twAddSublayer(layer)
            }
        }
        for id in ids {
            var nested = false
            var ancestor = dom.node(id)?.parent
            while let node = ancestor, !nested {
                nested = composited.contains(node.id)
                ancestor = node.parent
            }
            guard !nested, !composited.contains(id), let shapes = document.svgShapes(attribute: WidgetDOM.idAttribute, value: String(id), maxWidth: width),
                  WidgetCompositor.unobscured(document, id, shapes.frame, clip: nil, ignoring: []) else {
                refused.insert(id)
                ids.remove(id)
                frames[id] = nil
                containers.removeValue(forKey: id)?.twRemoveFromSuperlayer()
                dropped = true
                continue
            }
            let container: CALayer
            if let existing = containers[id] {
                container = existing
            } else {
                container = CALayer()
                container.masksToBounds = true
                host?.twAddSublayer(container)
                containers[id] = container
            }
            frames[id] = shapes.frame
            WidgetSVGLayers.tally(layered: 1)
            if container.frame != shapes.frame {
                container.frame = shapes.frame
            }
            update(container, shapes: shapes, scale: scale)
        }
        CATransaction.commit()
        return dropped
    }

    private func update(_ container: CALayer, shapes: LHSVGShapes, scale: CGFloat) {
        let origin = shapes.frame.origin
        var shift = CGAffineTransform(translationX: -origin.x, y: -origin.y)
        var layers: [CALayer] = []
        var existing = container.twSublayers ?? []
        func take<T: CALayer>(_ type: T.Type) -> T {
            if let index = existing.firstIndex(where: { Swift.type(of: $0) == type }) {
                return existing.remove(at: index) as! T
            }
            let layer = T()
            layer.contentsScale = scale
            return layer
        }
        for shape in shapes.shapes {
            guard let path = shape.path.copy(using: &shift) else {
                continue
            }
            if let colors = shape.gradientColors, let locations = shape.gradientLocations {
                let gradient = take(CAGradientLayer.self)
                gradient.frame = container.bounds
                gradient.colors = colors
                gradient.locations = locations
                let size = container.bounds.size
                gradient.startPoint = CGPoint(x: (shape.gradientStart.x - origin.x) / max(size.width, 1), y: (shape.gradientStart.y - origin.y) / max(size.height, 1))
                gradient.endPoint = CGPoint(x: (shape.gradientEnd.x - origin.x) / max(size.width, 1), y: (shape.gradientEnd.y - origin.y) / max(size.height, 1))
                let mask = (gradient.mask as? CAShapeLayer) ?? CAShapeLayer()
                mask.contentsScale = scale
                mask.frame = container.bounds
                mask.path = path
                mask.fillRule = shape.evenOdd ? .evenOdd : .nonZero
                gradient.mask = mask
                layers.append(gradient)
            } else if let fill = shape.fillColor {
                let layer = take(CAShapeLayer.self)
                layer.frame = container.bounds
                layer.path = path
                layer.fillColor = fill
                layer.fillRule = shape.evenOdd ? .evenOdd : .nonZero
                layer.strokeColor = nil
                layers.append(layer)
            }
            if let stroke = shape.strokeColor {
                let layer = take(CAShapeLayer.self)
                layer.frame = container.bounds
                layer.path = path
                layer.fillColor = nil
                layer.strokeColor = stroke
                layer.lineWidth = shape.lineWidth
                layer.lineCap = shape.lineCap == 1 ? .round : shape.lineCap == 2 ? .square : .butt
                layer.lineJoin = shape.lineJoin == 1 ? .round : shape.lineJoin == 2 ? .bevel : .miter
                layer.miterLimit = shape.miterLimit
                layers.append(layer)
            }
        }
        let current = container.twSublayers ?? []
        if current.count != layers.count || zip(current, layers).contains(where: { $0 !== $1 }) {
            WidgetLayerCommit.perform { container.sublayers = layers }
        }
    }
}
