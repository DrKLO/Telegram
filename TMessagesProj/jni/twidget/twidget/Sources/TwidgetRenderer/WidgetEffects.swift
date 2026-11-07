import AppKit
import CoreImage
import QuartzCore

struct EffectColor: Equatable {
    var r: Double
    var g: Double
    var b: Double
    var a: Double

    static let black = EffectColor(r: 0, g: 0, b: 0, a: 1)
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    static func parse(_ text: String) -> EffectColor? {
        guard let color = CSSColor.parse(text), let srgb = color.converted(to: space, intent: .defaultIntent, options: nil), let c = srgb.components, c.count == 4 else {
            return nil
        }
        return EffectColor(r: Double(c[0]), g: Double(c[1]), b: Double(c[2]), a: Double(c[3]))
    }

    var cgColor: CGColor {
        return CSSColor.srgb(r, green: g, blue: b, alpha: a)
    }

    var components: [Double] {
        return [r, g, b, a]
    }
}

enum CSSEffectLength {
    static let maxBlur = 256.0
    static let maxOffset = 1024.0

    static func clampOffset(_ value: Double) -> Double {
        return max(-maxOffset, min(maxOffset, value))
    }

    static func parse(_ text: Substring) -> Double? {
        let token = text.trimmingCharacters(in: .whitespaces).lowercased()
        if token.hasSuffix("px"), let value = Double(token.dropLast(2)), value.isFinite {
            return value
        }
        if let value = Double(token), value == 0 {
            return 0
        }
        return nil
    }
}

struct CSSFilterStep: Equatable {
    enum Kind: String {
        case blur
        case brightness
        case contrast
        case grayscale
        case hueRotate = "hue-rotate"
        case invert
        case opacity
        case saturate
        case sepia
        case dropShadow = "drop-shadow"
    }

    var kind: Kind
    var amount: Double = 0
    var x: Double = 0
    var y: Double = 0
    var radius: Double = 0
    var color = EffectColor.black

    var isColor: Bool {
        return kind != .blur && kind != .dropShadow
    }

    var identity: CSSFilterStep {
        var step = CSSFilterStep(kind: kind)
        switch kind {
        case .brightness, .contrast, .opacity, .saturate:
            step.amount = 1
        case .dropShadow:
            step.color = EffectColor(r: color.r, g: color.g, b: color.b, a: 0)
        default:
            break
        }
        return step
    }

    static func parseList(_ text: String?) -> [CSSFilterStep]? {
        guard let text = text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty, text.lowercased() != "none" else {
            return []
        }
        var steps: [CSSFilterStep] = []
        var rest = Substring(text)
        while true {
            rest = rest.drop { $0 == " " || $0 == "\t" || $0 == "\n" }
            if rest.isEmpty {
                break
            }
            guard let open = rest.firstIndex(of: "(") else {
                return nil
            }
            var depth = 0
            var close: Substring.Index?
            for index in rest[open...].indices {
                if rest[index] == "(" {
                    depth += 1
                } else if rest[index] == ")" {
                    depth -= 1
                    if depth == 0 {
                        close = index
                        break
                    }
                }
            }
            guard let end = close, let kind = Kind(rawValue: rest[..<open].trimmingCharacters(in: .whitespaces).lowercased()) else {
                return nil
            }
            guard let step = parse(kind, rest[rest.index(after: open)..<end]) else {
                return nil
            }
            steps.append(step)
            rest = rest[rest.index(after: end)...]
            if steps.count > 16 {
                return nil
            }
        }
        if let index = steps.firstIndex(where: { $0.kind == .dropShadow }), index != steps.count - 1 {
            return nil
        }
        return steps
    }

    private static func parse(_ kind: Kind, _ body: Substring) -> CSSFilterStep? {
        let argument = body.trimmingCharacters(in: .whitespaces)
        var step = CSSFilterStep(kind: kind)
        switch kind {
        case .blur:
            guard let radius = argument.isEmpty ? 0 : CSSEffectLength.parse(Substring(argument)), radius >= 0 else {
                return nil
            }
            step.radius = min(radius, CSSEffectLength.maxBlur)
        case .hueRotate:
            guard let degrees = argument.isEmpty ? 0 : angle(argument) else {
                return nil
            }
            step.amount = degrees
        case .dropShadow:
            let parts = WidgetNode.splitTopLevel(argument, separator: " ").filter { !$0.isEmpty }
            var lengths: [Double] = []
            var color: EffectColor?
            for part in parts {
                if lengths.count < 3, let value = CSSEffectLength.parse(Substring(part)) {
                    lengths.append(value)
                } else if color == nil, let parsed = EffectColor.parse(part) {
                    color = parsed
                } else {
                    return nil
                }
            }
            guard lengths.count >= 2, (lengths.count < 3 || lengths[2] >= 0), let resolved = color else {
                return nil
            }
            step.x = CSSEffectLength.clampOffset(lengths[0])
            step.y = CSSEffectLength.clampOffset(lengths[1])
            step.radius = min(lengths.count > 2 ? lengths[2] : 0, CSSEffectLength.maxBlur)
            step.color = resolved
        default:
            var value = 1.0
            if !argument.isEmpty {
                if argument.hasSuffix("%"), let percent = Double(argument.dropLast()) {
                    value = percent / 100
                } else if let number = Double(argument) {
                    value = number
                } else {
                    return nil
                }
            }
            guard value >= 0, value.isFinite else {
                return nil
            }
            if kind == .grayscale || kind == .invert || kind == .opacity || kind == .sepia {
                value = min(value, 1)
            }
            step.amount = value
        }
        return step
    }

    private static func angle(_ text: String) -> Double? {
        let lower = text.lowercased()
        for (unit, factor) in [("deg", 1.0), ("grad", 0.9), ("rad", 180 / Double.pi), ("turn", 360.0)] where lower.hasSuffix(unit) {
            return Double(lower.dropLast(unit.count)).map { $0 * factor }
        }
        return Double(lower).flatMap { $0 == 0 ? 0 : nil }
    }

    var matrix: [Double] {
        var m = [Double](repeating: 0, count: 20)
        m[0] = 1
        m[6] = 1
        m[12] = 1
        m[18] = 1
        let a = amount
        func set(_ v: [Double]) {
            for r in 0..<3 {
                for c in 0..<3 {
                    m[r * 5 + c] = v[r * 3 + c]
                }
            }
        }
        switch kind {
        case .brightness:
            m[0] = a
            m[6] = a
            m[12] = a
        case .contrast:
            m[0] = a
            m[6] = a
            m[12] = a
            m[4] = 0.5 - 0.5 * a
            m[9] = 0.5 - 0.5 * a
            m[14] = 0.5 - 0.5 * a
        case .grayscale:
            let g = 1 - a
            set([0.2126 + 0.7874 * g, 0.7152 - 0.7152 * g, 0.0722 - 0.0722 * g, 0.2126 - 0.2126 * g, 0.7152 + 0.2848 * g, 0.0722 - 0.0722 * g, 0.2126 - 0.2126 * g, 0.7152 - 0.7152 * g, 0.0722 + 0.9278 * g])
        case .sepia:
            let g = 1 - a
            set([0.393 + 0.607 * g, 0.769 - 0.769 * g, 0.189 - 0.189 * g, 0.349 - 0.349 * g, 0.686 + 0.314 * g, 0.168 - 0.168 * g, 0.272 - 0.272 * g, 0.534 - 0.534 * g, 0.131 + 0.869 * g])
        case .saturate:
            set([0.213 + 0.787 * a, 0.715 - 0.715 * a, 0.072 - 0.072 * a, 0.213 - 0.213 * a, 0.715 + 0.285 * a, 0.072 - 0.072 * a, 0.213 - 0.213 * a, 0.715 - 0.715 * a, 0.072 + 0.928 * a])
        case .hueRotate:
            let rad = a * Double.pi / 180, c = cos(rad), s = sin(rad)
            set([0.213 + c * 0.787 - s * 0.213, 0.715 - c * 0.715 - s * 0.715, 0.072 - c * 0.072 + s * 0.928,
                 0.213 - c * 0.213 + s * 0.143, 0.715 + c * 0.285 + s * 0.140, 0.072 - c * 0.072 - s * 0.283,
                 0.213 - c * 0.213 - s * 0.787, 0.715 - c * 0.715 + s * 0.715, 0.072 + c * 0.928 + s * 0.072])
        case .invert:
            m[0] = 1 - 2 * a
            m[6] = 1 - 2 * a
            m[12] = 1 - 2 * a
            m[4] = a
            m[9] = a
            m[14] = a
        case .opacity:
            m[18] = a
        default:
            break
        }
        return m
    }

    var parameters: [Double] {
        switch kind {
        case .blur:
            return [radius]
        case .dropShadow:
            return [x, y, radius] + color.components
        default:
            return matrix
        }
    }

    var reach: Double {
        switch kind {
        case .blur:
            return ceil(radius * 3)
        case .dropShadow:
            return ceil(radius * 3) + max(abs(x), abs(y))
        default:
            return 0
        }
    }
}

struct CSSBoxShadow: Equatable {
    var x: Double
    var y: Double
    var blur: Double
    var spread: Double
    var color: EffectColor
    var inset: Bool

    static func parseList(_ text: String?) -> [CSSBoxShadow]? {
        guard let text = text?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty, text.lowercased() != "none" else {
            return []
        }
        var shadows: [CSSBoxShadow] = []
        for item in WidgetNode.splitTopLevel(text, separator: ",") {
            var lengths: [Double] = []
            var color: EffectColor?
            var inset = false
            for part in WidgetNode.splitTopLevel(item.trimmingCharacters(in: .whitespaces), separator: " ") where !part.isEmpty {
                if part.lowercased() == "inset" {
                    inset = true
                } else if lengths.count < 4, let value = CSSEffectLength.parse(Substring(part)) {
                    lengths.append(value)
                } else if color == nil, let parsed = EffectColor.parse(part) {
                    color = parsed
                } else {
                    return nil
                }
            }
            guard lengths.count >= 2, (lengths.count < 3 || lengths[2] >= 0), let resolved = color else {
                return nil
            }
            shadows.append(CSSBoxShadow(x: CSSEffectLength.clampOffset(lengths[0]), y: CSSEffectLength.clampOffset(lengths[1]), blur: min(lengths.count > 2 ? lengths[2] : 0, CSSEffectLength.maxBlur * 2), spread: CSSEffectLength.clampOffset(lengths.count > 3 ? lengths[3] : 0), color: resolved, inset: inset))
            if shadows.count > 8 {
                return nil
            }
        }
        return shadows
    }

    var parameters: [Double] {
        return [x, y, blur, spread] + color.components
    }

    var reach: Double {
        return ceil(blur) + abs(spread) + max(abs(x), abs(y))
    }
}

struct WidgetEffects: Equatable {
    var filter: [CSSFilterStep] = []
    var shadows: [CSSBoxShadow] = []

    var isEmpty: Bool {
        return filter.isEmpty && shadows.isEmpty
    }

    var structure: [String] {
        return filter.map { $0.kind.rawValue } + shadows.map { _ in "shadow" }
    }

    var parameters: [Double] {
        return filter.flatMap { $0.parameters } + shadows.flatMap { $0.parameters }
    }

    var reach: Double {
        return filter.reduce(0) { $0 + $1.reach } + (shadows.map { $0.reach }.max() ?? 0)
    }

    static func parse(filter: String?, shadow: String?) -> WidgetEffects? {
        guard let steps = CSSFilterStep.parseList(filter), let shadows = CSSBoxShadow.parseList(shadow) else {
            return nil
        }
        return WidgetEffects(filter: steps, shadows: shadows.filter { !$0.inset })
    }

    static func compatible(_ a: WidgetEffects, _ b: WidgetEffects) -> Bool {
        return a.structure == b.structure
    }

    func aligned(to template: WidgetEffects) -> WidgetEffects? {
        var out = self
        if out.filter.count < template.filter.count && out.filter.map({ $0.kind }) == template.filter.prefix(out.filter.count).map({ $0.kind }) {
            out.filter += template.filter.dropFirst(out.filter.count).map { $0.identity }
        }
        if out.shadows.isEmpty && !template.shadows.isEmpty {
            out.shadows = template.shadows.map { CSSBoxShadow(x: 0, y: 0, blur: 0, spread: 0, color: EffectColor(r: $0.color.r, g: $0.color.g, b: $0.color.b, a: 0), inset: false) }
        }
        return out.structure == template.structure ? out : nil
    }

    static func template(_ list: [WidgetEffects]) -> WidgetEffects? {
        guard let richest = list.max(by: { $0.structure.count < $1.structure.count }) else {
            return nil
        }
        var template = richest
        if let longest = list.max(by: { $0.filter.count < $1.filter.count }), longest.filter.count > template.filter.count {
            template.filter = longest.filter
        }
        if template.shadows.isEmpty, let shadowed = list.first(where: { !$0.shadows.isEmpty }) {
            template.shadows = shadowed.shadows
        }
        return list.allSatisfy({ $0.aligned(to: template) != nil }) ? template : nil
    }
}

final class WidgetEffectLayers {
    private(set) var effects = WidgetEffects()
    private(set) var content: CALayer?
    private var shadowLayers: [CALayer] = []
    private(set) var backdropLayer: CALayer?
    private var backdropSteps: [CSSFilterStep] = []
    private var structure: [String] = []
    private(set) var box = CGRect.zero
    private var radii = [CGFloat](repeating: 0, count: 8)
    private var maskReach: Double = 0

    static func filterName(_ index: Int) -> String {
        return "tw\(index)"
    }

    var needsContentLayer: Bool {
        return !shadowLayers.isEmpty
    }

    func configure(layer: CALayer, effects: WidgetEffects, backdrop: [CSSFilterStep], box: CGRect, radii: [CGFloat], maxReach: Double) {
        let structureChanged = effects.structure != structure || box != self.box || radii != self.radii || maxReach > maskReach || backdrop != backdropSteps
        self.box = box
        self.radii = radii
        if structureChanged {
            structure = effects.structure
            maskReach = max(maxReach, effects.reach) + 4
            layer.filters = WidgetEffectLayers.makeFilters(effects.filter)
            for shadow in shadowLayers {
                shadow.twRemoveFromSuperlayer()
            }
            shadowLayers = effects.shadows.reversed().map { _ in makeShadowLayer(bounds: layer.bounds) }
            backdropLayer?.twRemoveFromSuperlayer()
            backdropLayer = nil
            backdropSteps = backdrop
            if !backdrop.isEmpty {
                let backdropLayer = CALayer()
                backdropLayer.frame = box
                backdropLayer.masksToBounds = true
                let path = WidgetCompositor.roundedPath(CGRect(origin: .zero, size: box.size), radii)
                let mask = CAShapeLayer()
                mask.path = path
                mask.frame = backdropLayer.bounds
                backdropLayer.mask = mask
                backdropLayer.backgroundFilters = WidgetEffectLayers.makeFilters(backdrop)
                self.backdropLayer = backdropLayer
            }
            var index: UInt32 = 0
            for shadow in shadowLayers {
                layer.twInsertSublayer(shadow, at: index)
                index += 1
            }
            if needsContentLayer {
                if content == nil {
                    let content = CALayer()
                    content.name = "tw.content"
                    content.contentsGravity = .resize
                    self.content = content
                }
                content?.frame = layer.bounds
                if content?.superlayer !== layer {
                    layer.twInsertSublayer(content!, at: index)
                }
            } else {
                content?.twRemoveFromSuperlayer()
                content = nil
            }
        }
        content?.frame = layer.bounds
        for shadow in shadowLayers {
            shadow.frame = layer.bounds
        }
        apply(effects, to: layer)
    }

    static let shadowName = "tw.shadow"

    private func makeShadowLayer(bounds: CGRect) -> CALayer {
        let shadow = CALayer()
        shadow.name = WidgetEffectLayers.shadowName
        shadow.frame = bounds
        shadow.shadowOpacity = 1
        let mask = CAShapeLayer()
        mask.frame = shadow.bounds
        mask.fillRule = .evenOdd
        let outer = CGMutablePath()
        outer.addRect(shadow.bounds.insetBy(dx: -CGFloat(maskReach), dy: -CGFloat(maskReach)))
        outer.addPath(WidgetCompositor.roundedPath(box, radii))
        mask.path = outer
        shadow.mask = mask
        return shadow
    }

    static func makeFilters(_ steps: [CSSFilterStep]) -> [CIFilter]? {
        var filters: [CIFilter] = []
        var encoded = false
        for (index, step) in steps.enumerated() {
            if step.isColor && !encoded {
                let encode = CIFilter(name: "CILinearToSRGBToneCurve")!
                encode.name = "twE\(index)"
                filters.append(encode)
                encoded = true
            } else if !step.isColor && encoded {
                let decode = CIFilter(name: "CISRGBToneCurveToLinear")!
                decode.name = "twD\(index)"
                filters.append(decode)
                encoded = false
            }
            switch step.kind {
            case .blur:
                let blur = CIFilter(name: "CIGaussianBlur")!
                blur.name = filterName(index)
                blur.setValue(step.radius, forKey: kCIInputRadiusKey)
                filters.append(blur)
            case .dropShadow:
                break
            default:
                let matrix = CIFilter(name: "CIColorMatrix")!
                matrix.name = filterName(index)
                for (key, vector) in vectors(step.matrix) {
                    matrix.setValue(vector, forKey: key)
                }
                filters.append(matrix)
                let clamp = CIFilter(name: "CIColorClamp")!
                clamp.name = "twC\(index)"
                filters.append(clamp)
            }
        }
        if encoded {
            let decode = CIFilter(name: "CISRGBToneCurveToLinear")!
            decode.name = "twD\(steps.count)"
            filters.append(decode)
        }
        return filters.isEmpty ? nil : filters
    }

    static let vectorKeys = ["inputRVector", "inputGVector", "inputBVector", "inputAVector", "inputBiasVector"]

    static func vectors(_ m: [Double]) -> [(String, CIVector)] {
        return [
            ("inputRVector", CIVector(x: CGFloat(m[0]), y: CGFloat(m[1]), z: CGFloat(m[2]), w: CGFloat(m[3]))),
            ("inputGVector", CIVector(x: CGFloat(m[5]), y: CGFloat(m[6]), z: CGFloat(m[7]), w: CGFloat(m[8]))),
            ("inputBVector", CIVector(x: CGFloat(m[10]), y: CGFloat(m[11]), z: CGFloat(m[12]), w: CGFloat(m[13]))),
            ("inputAVector", CIVector(x: CGFloat(m[15]), y: CGFloat(m[16]), z: CGFloat(m[17]), w: CGFloat(m[18]))),
            ("inputBiasVector", CIVector(x: CGFloat(m[4]), y: CGFloat(m[9]), z: CGFloat(m[14]), w: CGFloat(m[19]))),
        ]
    }

    func shadowPath(_ shadow: CSSBoxShadow) -> CGPath {
        let spread = CGFloat(shadow.spread)
        let rect = box.insetBy(dx: -spread, dy: -spread)
        let adjusted = radii.contains(where: { $0 > 0 }) ? radii.map { max(0, $0 + spread) } : radii
        return WidgetCompositor.roundedPath(rect, adjusted)
    }

    struct Channel {
        let layer: CALayer
        let keyPath: String
        let value: (WidgetEffects) -> Any
    }

    func channels(of layer: CALayer) -> [Channel] {
        var out: [Channel] = []
        for (index, step) in effects.filter.enumerated() {
            switch step.kind {
            case .blur:
                out.append(Channel(layer: layer, keyPath: "filters.\(WidgetEffectLayers.filterName(index)).inputRadius", value: { NSNumber(value: $0.filter[index].radius) }))
            case .dropShadow:
                out.append(Channel(layer: layer, keyPath: "shadowRadius", value: { NSNumber(value: $0.filter[index].radius) }))
                out.append(Channel(layer: layer, keyPath: "shadowOffset", value: { NSValue(size: NSSize(width: $0.filter[index].x, height: $0.filter[index].y)) }))
                out.append(Channel(layer: layer, keyPath: "shadowColor", value: { $0.filter[index].color.cgColor }))
            default:
                for (slot, key) in WidgetEffectLayers.vectorKeys.enumerated() {
                    out.append(Channel(layer: layer, keyPath: "filters.\(WidgetEffectLayers.filterName(index)).\(key)", value: { WidgetEffectLayers.vectors($0.filter[index].matrix)[slot].1 }))
                }
            }
        }
        for (index, shadowLayer) in shadowLayers.enumerated() {
            let item = effects.shadows.count - 1 - index
            out.append(Channel(layer: shadowLayer, keyPath: "shadowRadius", value: { NSNumber(value: $0.shadows[item].blur / 2) }))
            out.append(Channel(layer: shadowLayer, keyPath: "shadowOffset", value: { NSValue(size: NSSize(width: $0.shadows[item].x, height: $0.shadows[item].y)) }))
            out.append(Channel(layer: shadowLayer, keyPath: "shadowColor", value: { $0.shadows[item].color.cgColor }))
            out.append(Channel(layer: shadowLayer, keyPath: "shadowPath", value: { [unowned self] in self.shadowPath($0.shadows[item]) }))
        }
        return out
    }

    func apply(_ effects: WidgetEffects, to layer: CALayer) {
        self.effects = effects
        if let drop = effects.filter.last, drop.kind == .dropShadow {
            layer.shadowOpacity = 1
        } else {
            layer.shadowOpacity = 0
        }
        for channel in channels(of: layer) {
            channel.layer.setValue(channel.value(effects), forKeyPath: channel.keyPath)
        }
    }

    private static let snapshotContext = CIContext(options: [.cacheIntermediates: false])

    static func deviceScale(_ ctx: CGContext) -> CGFloat {
        let m = ctx.ctm
        return max(1, sqrt(abs(m.a * m.d - m.b * m.c)))
    }

    static func paintShadow(_ shadow: CALayer, in ctx: CGContext) {
        guard shadow.shadowOpacity > 0, let path = shadow.shadowPath, let color = shadow.shadowColor, color.alpha > 0 else {
            return
        }
        let scale = deviceScale(ctx)
        ctx.saveGState()
        if let mask = (shadow.mask as? CAShapeLayer)?.path {
            ctx.addPath(mask)
            ctx.twClip(using: .evenOdd)
        }
        let far = path.boundingBox.width + 10_000
        let offset = CGSize(width: shadow.shadowOffset.width - far, height: shadow.shadowOffset.height)
        let device = offset.applying(ctx.ctm)
        ctx.setShadow(offset: CGSize(width: device.width, height: device.height), blur: shadow.shadowRadius * 2 * scale, color: color.copy(alpha: color.alpha * CGFloat(shadow.shadowOpacity)))
        ctx.translateBy(x: far, y: 0)
        ctx.addPath(path)
        ctx.setFillColor(CGColor(gray: 0, alpha: 1))
        ctx.fillPath()
        ctx.restoreGState()
    }

    static func applyLayerShadow(_ layer: CALayer, in ctx: CGContext) {
        guard layer.shadowOpacity > 0, let color = layer.shadowColor, color.alpha > 0 else {
            return
        }
        let device = layer.shadowOffset.applying(ctx.ctm)
        ctx.setShadow(offset: CGSize(width: device.width, height: device.height), blur: layer.shadowRadius * 2 * deviceScale(ctx), color: color.copy(alpha: color.alpha * CGFloat(layer.shadowOpacity)))
    }

    private static func radius(_ filter: CIFilter) -> CGFloat {
        guard filter.inputKeys.contains(kCIInputRadiusKey), let value = filter.value(forKey: kCIInputRadiusKey) as? NSNumber else {
            return 0
        }
        return CGFloat(value.doubleValue)
    }

    private static func scaled(_ filters: [CIFilter], _ scale: CGFloat) -> [CIFilter] {
        return filters.map { filter in
            let copy = (filter.copy() as? CIFilter) ?? filter
            let value = radius(copy)
            if value > 0 {
                copy.setValue(Double(value * scale), forKey: kCIInputRadiusKey)
            }
            return copy
        }
    }

    private static func run(_ filters: [CIFilter], on image: CIImage) -> CIImage {
        var output = image
        for filter in filters {
            guard let copy = filter.copy() as? CIFilter else {
                continue
            }
            copy.setValue(output, forKey: kCIInputImageKey)
            output = copy.outputImage ?? output
        }
        return output
    }

    static func paintFiltered(_ filters: [CIFilter], bounds: CGRect, in ctx: CGContext, body: (CGContext) -> Void) {
        let scale = deviceScale(ctx)
        let margin = 3 * filters.reduce(0) { $0 + radius($1) }
        let area = bounds.insetBy(dx: -ceil(margin), dy: -ceil(margin))
        let pw = Int(ceil(area.width * scale)), ph = Int(ceil(area.height * scale))
        guard pw > 0, ph > 0, pw * ph < 64 << 20, let offscreen = CGContext(data: nil, width: pw, height: ph, bitsPerComponent: 8, bytesPerRow: 0, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            body(ctx)
            return
        }
        offscreen.translateBy(x: 0, y: CGFloat(ph))
        offscreen.scaleBy(x: scale, y: -scale)
        offscreen.translateBy(x: -area.minX, y: -area.minY)
        body(offscreen)
        guard let source = offscreen.makeImage() else {
            return
        }
        let input = CIImage(cgImage: source)
        let output = run(scaled(filters, scale), on: input).cropped(to: input.extent)
        guard let image = snapshotContext.createCGImage(output, from: input.extent) else {
            return
        }
        ctx.saveGState()
        ctx.translateBy(x: area.minX, y: area.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(origin: .zero, size: area.size))
        ctx.restoreGState()
    }

    static func paintBackdrop(_ backdrop: CALayer, in ctx: CGContext) {
        guard let filters = backdrop.backgroundFilters as? [CIFilter], !filters.isEmpty, let snapshot = ctx.makeImage() else {
            return
        }
        let frame = backdrop.frame
        let device = frame.applying(ctx.ctm).integral
        let flipped = CGRect(x: device.minX, y: CGFloat(snapshot.height) - device.maxY, width: device.width, height: device.height)
        guard let region = snapshot.cropping(to: flipped.intersection(CGRect(x: 0, y: 0, width: snapshot.width, height: snapshot.height))) else {
            return
        }
        let input = CIImage(cgImage: region)
        let output = run(scaled(filters, deviceScale(ctx)), on: input.clampedToExtent()).cropped(to: input.extent)
        guard let image = snapshotContext.createCGImage(output, from: input.extent) else {
            return
        }
        ctx.saveGState()
        let path = (backdrop.mask as? CAShapeLayer)?.path ?? CGPath(rect: backdrop.bounds, transform: nil)
        ctx.translateBy(x: frame.minX, y: frame.minY)
        ctx.addPath(path)
        ctx.twClip()
        ctx.translateBy(x: 0, y: frame.height)
        ctx.scaleBy(x: 1, y: -1)
        ctx.setBlendMode(.copy)
        ctx.draw(image, in: CGRect(origin: .zero, size: frame.size))
        ctx.restoreGState()
    }

    func removeAnimations() {
        for layer in shadowLayers {
            layer.removeAllAnimations()
        }
    }

    func reset(_ layer: CALayer) {
        layer.filters = nil
        layer.shadowOpacity = 0
        for shadow in shadowLayers {
            shadow.twRemoveFromSuperlayer()
        }
        shadowLayers = []
        backdropLayer?.twRemoveFromSuperlayer()
        backdropLayer = nil
        content?.twRemoveFromSuperlayer()
        content = nil
        structure = []
        backdropSteps = []
    }
}
