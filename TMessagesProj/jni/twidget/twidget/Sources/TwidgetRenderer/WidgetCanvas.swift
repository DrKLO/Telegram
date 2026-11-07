import AppKit
import CoreText

final class WidgetCanvas {
    private enum Paint {
        case color(CGColor)
        case gradient(Int)
    }

    private struct State {
        var fill: Paint = .color(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
        var stroke: Paint = .color(CGColor(red: 0, green: 0, blue: 0, alpha: 1))
        var font = "10px sans-serif"
        var align = "start"
        var baseline = "alphabetic"
        var dash: [CGFloat] = []
        var dashOffset: CGFloat = 0
        var lineWidth: CGFloat = 1
        var shadowBlur: CGFloat = 0
        var shadowColor = CGColor(red: 0, green: 0, blue: 0, alpha: 0)
        var shadowOffset = CGSize.zero
        var alpha: CGFloat = 1
        var composite = "source-over"
        var clipped = false
        var fillRGBA: SIMD4<Float>? = SIMD4(0, 0, 0, 1)
    }

    private struct Gradient {
        let kind: String
        let args: [CGFloat]
        var stops: [(CGFloat, CGColor)] = []
        var compiled: CGGradient?
        var lastColor: CGColor?
    }

    private struct TextKey: Hashable {
        let text: String
        let font: String
    }

    static let maxPixels = 4096 * 4096
    static let maxObjects = 4096
    static let maxStops = 256
    static let maxStack = 512
    static let maxText = 4096
    static let maxCachedTextLines = 1024

    private(set) var width = 300
    private(set) var height = 150
    private(set) var version = 0
    private var context: CGContext?
    private(set) var storage: WidgetCanvasSurface?
    var backend: WidgetCanvasBackend?
    var onDraw: (() -> Void)?
    private var colors: [String: (CGColor, SIMD4<Float>)] = [:]
    private var state = State()
    private var stack: [State] = []
    private var gradients: [Int: Gradient] = [:]
    private var paths: [Int: CGMutablePath] = [:]
    private var cachedImage: CGImage?
    private var textLines: [TextKey: CTLine] = [:]
    private var fontBaselines: [String: Baselines] = [:]
    private var textKeys: [TextKey] = []
    private var textCursor = 0
    private let space = CGColorSpace(name: CGColorSpace.sRGB)!
    var resolveImage: ((Int) -> CGImage?)?

    init(width: Int, height: Int, allowance: @escaping () -> Int) {
        self.allowance = allowance
        resize(width, height)
    }

    var pixelCount: Int {
        return width * height
    }

    var cachedTextLineCount: Int {
        return textLines.count
    }

    var image: CGImage? {
        if let cachedImage = cachedImage {
            return cachedImage
        }
        finishGPU()
        cachedImage = context?.makeImage()
        return cachedImage
    }

    func finishGPU() {
        backend?.finish()
    }

    var fillRGBA: SIMD4<Float>? {
        return state.fillRGBA
    }

    var globalAlpha: Float {
        return Float(state.alpha)
    }

    var composite: String {
        return state.composite
    }

    var isPlain: Bool {
        return WidgetCanvas.plain(state) && stack.allSatisfy(WidgetCanvas.plain)
    }

    private static func plain(_ state: State) -> Bool {
        return state.fillRGBA != nil && !state.clipped && !(state.shadowColor.alpha > 0 && (state.shadowBlur > 0 || state.shadowOffset != .zero))
    }

    private func color(_ text: String) -> (CGColor, SIMD4<Float>)? {
        if let known = colors[text] {
            return known
        }
        guard let color = CSSColor.parse(text) else {
            return nil
        }
        let srgb = color.colorSpace?.name == CGColorSpace.sRGB ? color : (color.converted(to: space, intent: .defaultIntent, options: nil) ?? color)
        let c = srgb.components ?? []
        let rgba: SIMD4<Float> = c.count >= 4 ? SIMD4(Float(c[0]), Float(c[1]), Float(c[2]), Float(c[3])) : c.count == 2 ? SIMD4(Float(c[0]), Float(c[0]), Float(c[0]), Float(c[1])) : SIMD4(0, 0, 0, 1)
        if colors.count >= 1024 {
            colors.removeAll(keepingCapacity: true)
        }
        colors[text] = (color, rgba)
        return (color, rgba)
    }

    var allowance: () -> Int = { WidgetCanvas.maxPixels }

    func resize(_ w: Int, _ h: Int) {
        width = max(0, min(w, 8192))
        height = max(0, min(h, 8192))
        let budget = max(0, min(WidgetCanvas.maxPixels, allowance()))
        if width * height > budget {
            width = 0
            height = 0
        }
        finishGPU()
        state = State()
        stack = []
        context = nil
        storage = nil
        cachedImage = nil
        textLines.removeAll(keepingCapacity: true)
        textKeys.removeAll(keepingCapacity: true)
        textCursor = 0
        version += 1
        backend?.resized(canvas: self)
        guard width > 0, height > 0 else {
            return
        }
        let made = WidgetCanvasSurface.useSurfaces ? WidgetCanvasSurface(width: width, height: height, space: space) : nil
        guard let ctx = made?.context ?? CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return
        }
        storage = made
        ctx.translateBy(x: 0, y: CGFloat(height))
        ctx.scaleBy(x: 1, y: -1)
        ctx.setShouldAntialias(true)
        ctx.interpolationQuality = .high
        ctx.setLineWidth(1)
        ctx.setMiterLimit(10)
        context = ctx
    }

    func run(_ commands: [Any]) {
        guard !commands.isEmpty else {
            return
        }
        execute(CanvasStream(commands: commands))
    }

    func execute(_ stream: CanvasStream) {
        guard !stream.isEmpty else {
            return
        }
        let accelerated = backend?.begin(stream, canvas: self) ?? false
        if !accelerated {
            backend?.finish()
            storage?.lock()
        }
        for op in stream.ops {
            let rest = stream.arguments(op)
            switch op.kind {
            case .call:
                if accelerated, let backend = backend, backend.draw(op.name, rest, canvas: self) {
                    continue
                }
                call(op.name, rest)
            case .set:
                if let value = rest.first {
                    set(op.name, value)
                }
            case .resize:
                storage?.unlock()
                resize(WidgetCanvas.int(op.ident), WidgetCanvas.int(rest.first.map(WidgetCanvas.value) ?? 0))
                storage?.lock()
            case .gradient:
                let id = WidgetCanvas.int(op.ident)
                if case let .string(gradientKind)? = rest.first, gradients[id] != nil || gradients.count < WidgetCanvas.maxObjects {
                    gradients[id] = Gradient(kind: gradientKind, args: rest.dropFirst().map { WidgetCanvas.value($0) })
                }
            case .gradientStop:
                let id = WidgetCanvas.int(op.ident)
                if rest.count >= 2, (gradients[id]?.stops.count ?? 0) < WidgetCanvas.maxStops, case let .string(text) = rest[rest.startIndex + 1], let color = CSSColor.parse(text) {
                    gradients[id]?.stops.append((WidgetCanvas.value(rest[rest.startIndex]), color))
                    gradients[id]?.compiled = nil
                }
            case .pathNew:
                let id = WidgetCanvas.int(op.ident)
                if paths[id] == nil && paths.count >= WidgetCanvas.maxObjects {
                    continue
                }
                let path = CGMutablePath()
                if case let .string(d)? = rest.first, d.utf8.count <= 1 << 20 {
                    SVGPath.append(d, to: path)
                }
                paths[id] = path
            case .pathCall:
                let id = WidgetCanvas.int(op.ident)
                if let path = paths[id] {
                    pathCall(path, op.name, rest)
                }
            }
        }
        if accelerated {
            backend?.commit(canvas: self)
        } else {
            storage?.unlock()
        }
        cachedImage = nil
        version += 1
        onDraw?()
    }

    typealias Value = CanvasArg
    typealias Args = ArraySlice<CanvasArg>

    static func value(_ arg: CanvasArg) -> CGFloat {
        if case let .number(v) = arg {
            return CGFloat(v)
        }
        return 0
    }

    static let directRects = ProcessInfo.processInfo.environment["TW_CANVAS_PATH_RECTS"] == nil

    static func int(_ value: Any) -> Int {
        let number = ((value as AnyObject) as? NSNumber)?.doubleValue ?? 0
        return number.isFinite ? Int(max(-1e9, min(1e9, number))) : 0
    }

    static func number(_ value: Any) -> CGFloat {
        return CGFloat(((value as AnyObject) as? NSNumber)?.doubleValue ?? 0)
    }

    private func n(_ args: Args, _ i: Int, _ fallback: CGFloat = 0) -> CGFloat {
        if i < args.count, case let .number(v) = args[args.startIndex + i], v.isFinite {
            return CGFloat(v)
        }
        return fallback
    }

    private func set(_ property: CanvasName, _ value: Value) {
        guard let ctx = context else {
            return
        }
        switch (property, value) {
        case (.fillStyle, .string(let s)):
            if let (color, rgba) = color(s) {
                state.fill = .color(color)
                state.fillRGBA = rgba
            }
        case (.fillStyle, .gradient(let id)):
            state.fill = .gradient(id)
            state.fillRGBA = nil
        case (.strokeStyle, .string(let s)):
            if let color = CSSColor.parse(s) {
                state.stroke = .color(color)
            }
        case (.strokeStyle, .gradient(let id)):
            state.stroke = .gradient(id)
        case (.lineWidth, .number(let v)) where v > 0:
            state.lineWidth = CGFloat(v)
            ctx.setLineWidth(CGFloat(v))
        case (.lineCap, .string(let s)):
            ctx.setLineCap(s == "round" ? .round : s == "square" ? .square : .butt)
        case (.lineJoin, .string(let s)):
            ctx.setLineJoin(s == "round" ? .round : s == "bevel" ? .bevel : .miter)
        case (.miterLimit, .number(let v)) where v > 0:
            ctx.setMiterLimit(CGFloat(v))
        case (.lineDashOffset, .number(let v)) where v.isFinite:
            state.dashOffset = CGFloat(v)
            ctx.setLineDash(phase: CGFloat(v), lengths: state.dash)
        case (.font, .string(let s)):
            state.font = s
        case (.textAlign, .string(let s)):
            state.align = s
        case (.textBaseline, .string(let s)):
            state.baseline = s
        case (.globalAlpha, .number(let v)) where v >= 0 && v <= 1:
            state.alpha = CGFloat(v)
            ctx.setAlpha(CGFloat(v))
        case (.globalCompositeOperation, .string(let s)):
            state.composite = s
            ctx.setBlendMode(WidgetCanvas.blendMode(s))
        case (.shadowBlur, .number(let v)) where v >= 0:
            state.shadowBlur = min(CGFloat(v), 100)
            applyShadow()
        case (.shadowColor, .string(let s)):
            if let color = CSSColor.parse(s) {
                state.shadowColor = color
                applyShadow()
            }
        case (.shadowOffsetX, .number(let v)) where v.isFinite:
            state.shadowOffset.width = CGFloat(v)
            applyShadow()
        case (.shadowOffsetY, .number(let v)) where v.isFinite:
            state.shadowOffset.height = CGFloat(v)
            applyShadow()
        case (.imageSmoothingEnabled, .number(let v)):
            ctx.interpolationQuality = v != 0 ? .high : .none
        default:
            break
        }
    }

    private func applyShadow() {
        guard let ctx = context else {
            return
        }
        if state.shadowColor.alpha > 0 && (state.shadowBlur > 0 || state.shadowOffset != .zero) {
            ctx.setShadow(offset: CGSize(width: state.shadowOffset.width, height: -state.shadowOffset.height), blur: state.shadowBlur, color: state.shadowColor)
        } else {
            ctx.setShadow(offset: .zero, blur: 0, color: nil)
        }
    }

    static func blendMode(_ name: String) -> CGBlendMode {
        switch name {
        case "source-in": return .sourceIn
        case "source-out": return .sourceOut
        case "source-atop": return .sourceAtop
        case "destination-over": return .destinationOver
        case "destination-in": return .destinationIn
        case "destination-out": return .destinationOut
        case "destination-atop": return .destinationAtop
        case "lighter": return .plusLighter
        case "copy": return .copy
        case "xor": return .xor
        case "multiply": return .multiply
        case "screen": return .screen
        case "overlay": return .overlay
        case "darken": return .darken
        case "lighten": return .lighten
        case "color-dodge": return .colorDodge
        case "color-burn": return .colorBurn
        case "hard-light": return .hardLight
        case "soft-light": return .softLight
        case "difference": return .difference
        case "exclusion": return .exclusion
        case "hue": return .hue
        case "saturation": return .saturation
        case "color": return .color
        case "luminosity": return .luminosity
        default: return .normal
        }
    }

    private func call(_ method: CanvasName, _ a: Args) {
        guard let ctx = context else {
            return
        }
        switch method {
        case .save:
            if stack.count >= WidgetCanvas.maxStack {
                return
            }
            stack.append(state)
            ctx.saveGState()
        case .restore:
            if let last = stack.popLast() {
                state = last
                ctx.restoreGState()
            }
        case .scale:
            ctx.scaleBy(x: n(a, 0, 1), y: n(a, 1, 1))
        case .rotate:
            ctx.rotate(by: n(a, 0))
        case .translate:
            ctx.translateBy(x: n(a, 0), y: n(a, 1))
        case .transform:
            ctx.concatenate(CGAffineTransform(a: n(a, 0, 1), b: n(a, 1), c: n(a, 2), d: n(a, 3, 1), tx: n(a, 4), ty: n(a, 5)))
        case .setTransform, .resetTransform:
            let target = method == .resetTransform ? CGAffineTransform.identity : CGAffineTransform(a: n(a, 0, 1), b: n(a, 1), c: n(a, 2), d: n(a, 3, 1), tx: n(a, 4), ty: n(a, 5))
            ctx.concatenate(ctx.ctm.inverted())
            ctx.concatenate(base)
            ctx.concatenate(target)
        case .clearRect:
            ctx.clear(CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3)))
        case .fillRect:
            let rect = CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3))
            if case let .color(color) = state.fill, WidgetCanvas.directRects, ctx.isPathEmpty {
                ctx.setFillColor(color)
                ctx.fill(rect)
                return
            }
            let saved = ctx.path
            ctx.beginPath()
            ctx.addRect(rect)
            fillCurrent(ctx, evenOdd: false)
            restorePath(ctx, saved)
        case .strokeRect:
            let rect = CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3))
            let saved = ctx.path
            ctx.beginPath()
            ctx.addRect(rect)
            strokeCurrent(ctx)
            restorePath(ctx, saved)
        case .beginPath:
            ctx.beginPath()
        case .closePath:
            ctx.closePath()
        case .moveTo:
            ctx.move(to: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .lineTo:
            ensureStart(ctx, CGPoint(x: n(a, 0), y: n(a, 1)))
            ctx.addLine(to: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .bezierCurveTo:
            ensureStart(ctx, CGPoint(x: n(a, 0), y: n(a, 1)))
            ctx.addCurve(to: CGPoint(x: n(a, 4), y: n(a, 5)), control1: CGPoint(x: n(a, 0), y: n(a, 1)), control2: CGPoint(x: n(a, 2), y: n(a, 3)))
        case .quadraticCurveTo:
            ensureStart(ctx, CGPoint(x: n(a, 0), y: n(a, 1)))
            ctx.addQuadCurve(to: CGPoint(x: n(a, 2), y: n(a, 3)), control: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .arc:
            ctx.addArc(center: CGPoint(x: n(a, 0), y: n(a, 1)), radius: max(0, n(a, 2)), startAngle: n(a, 3), endAngle: n(a, 4), clockwise: WidgetCanvas.truthy(a, 5))
        case .arcTo:
            ensureStart(ctx, CGPoint(x: n(a, 0), y: n(a, 1)))
            ctx.addArc(tangent1End: CGPoint(x: n(a, 0), y: n(a, 1)), tangent2End: CGPoint(x: n(a, 2), y: n(a, 3)), radius: max(0, n(a, 4)))
        case .ellipse:
            let path = CGMutablePath()
            WidgetCanvas.ellipse(path, a.map(WidgetCanvas.value), anticlockwise: WidgetCanvas.truthy(a, 7))
            ctx.addPath(path)
        case .rect:
            ctx.addRect(CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3)))
        case .roundRect:
            let rect = CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3)).standardized
            var radius: CGFloat = 0
            if a.count > 4 {
                switch a[a.startIndex + 4] {
                case let .number(v):
                    radius = v.isFinite ? CGFloat(v) : 0
                case let .list(values):
                    radius = values.first.map { $0.isFinite ? CGFloat($0) : 0 } ?? 0
                default:
                    break
                }
            }
            radius = min(radius, rect.width / 2, rect.height / 2)
            ctx.addPath(CGPath(roundedRect: rect, cornerWidth: max(0, radius), cornerHeight: max(0, radius), transform: nil))
        case .fill:
            if case let .path(id)? = a.first, let path = paths[id] {
                let saved = ctx.path
                ctx.beginPath()
                ctx.addPath(path)
                fillCurrent(ctx, evenOdd: WidgetCanvas.evenOdd(a, 1))
                restorePath(ctx, saved)
            } else {
                let saved = ctx.path
                fillCurrent(ctx, evenOdd: WidgetCanvas.evenOdd(a, 0))
                restorePath(ctx, saved)
            }
        case .stroke:
            let saved = ctx.path
            if case let .path(id)? = a.first, let path = paths[id] {
                ctx.beginPath()
                ctx.addPath(path)
            }
            strokeCurrent(ctx)
            restorePath(ctx, saved)
        case .clip:
            if case let .path(id)? = a.first, let path = paths[id] {
                let saved = ctx.path
                ctx.beginPath()
                ctx.addPath(path)
                ctx.twClip(using: WidgetCanvas.evenOdd(a, 1) ? .evenOdd : .winding)
                state.clipped = true
                restorePath(ctx, saved)
            } else {
                let saved = ctx.path
                state.clipped = true
                ctx.twClip(using: WidgetCanvas.evenOdd(a, 0) ? .evenOdd : .winding)
                restorePath(ctx, saved)
            }
        case .fillText, .strokeText:
            if case let .string(full)? = a.first {
                let text = String(full.prefix(WidgetCanvas.maxText))
                drawText(ctx, text, x: n(a, 1), y: n(a, 2), maxWidth: a.count > 3 ? n(a, 3) : nil, stroke: method == .strokeText)
            }
        case .setLineDash:
            if case let .list(raw)? = a.first, raw.count <= 64, raw.allSatisfy({ $0.isFinite && $0 >= 0 }) {
                let values = raw.map { CGFloat($0) }
                state.dash = values.count % 2 == 1 ? values + values : values
                ctx.setLineDash(phase: state.dashOffset, lengths: state.dash)
            }
        case .putImageData:
            if case let .pixels(data, w, h)? = a.first, let image = WidgetCanvas.image(rgba: data, width: w, height: h) {
                ctx.saveGState()
                ctx.concatenate(ctx.ctm.inverted())
                ctx.concatenate(base)
                ctx.setBlendMode(.copy)
                ctx.setAlpha(1)
                ctx.setShadow(offset: .zero, blur: 0, color: nil)
                drawImage(ctx, image, CGRect(x: n(a, 1), y: n(a, 2), width: CGFloat(w), height: CGFloat(h)))
                ctx.restoreGState()
            }
        case .drawImage:
            if case let .node(id)? = a.first, let image = resolveImage?(id) {
                let iw = CGFloat(image.width), ih = CGFloat(image.height)
                switch a.count {
                case 3:
                    drawImage(ctx, image, CGRect(x: n(a, 1), y: n(a, 2), width: iw, height: ih))
                case 5:
                    drawImage(ctx, image, CGRect(x: n(a, 1), y: n(a, 2), width: n(a, 3), height: n(a, 4)))
                case 9:
                    let source = CGRect(x: n(a, 1), y: n(a, 2), width: n(a, 3), height: n(a, 4)).intersection(CGRect(x: 0, y: 0, width: iw, height: ih))
                    if let cropped = image.cropping(to: source) {
                        drawImage(ctx, cropped, CGRect(x: n(a, 5), y: n(a, 6), width: n(a, 7), height: n(a, 8)))
                    }
                default:
                    break
                }
            }
        default:
            break
        }
    }

    var base: CGAffineTransform {
        return CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: 0, ty: CGFloat(height))
    }

    var currentTransform: CGAffineTransform {
        guard let ctx = context else {
            return .identity
        }
        return ctx.ctm.concatenating(base.inverted())
    }

    private func ensureStart(_ ctx: CGContext, _ point: CGPoint) {
        if ctx.isPathEmpty {
            ctx.move(to: point)
        }
    }

    private func restorePath(_ ctx: CGContext, _ saved: CGPath?) {
        ctx.beginPath()
        if let saved = saved, !saved.isEmpty {
            ctx.addPath(saved)
        }
    }

    private static func truthy(_ args: Args, _ index: Int) -> Bool {
        guard index < args.count else {
            return false
        }
        switch args[args.startIndex + index] {
        case let .number(v):
            return v != 0
        case let .string(s):
            return !s.isEmpty && s != "false"
        default:
            return false
        }
    }

    private static func evenOdd(_ args: Args, _ index: Int) -> Bool {
        if index < args.count, case let .string(rule) = args[args.startIndex + index] {
            return rule == "evenodd"
        }
        return false
    }

    private func fillCurrent(_ ctx: CGContext, evenOdd: Bool) {
        if ctx.isPathEmpty {
            return
        }
        switch state.fill {
        case let .color(color):
            ctx.setFillColor(color)
            ctx.fillPath(using: evenOdd ? .evenOdd : .winding)
        case let .gradient(id):
            ctx.saveGState()
            ctx.twClip(using: evenOdd ? .evenOdd : .winding)
            drawGradient(ctx, id)
            ctx.restoreGState()
        }
    }

    private func strokeCurrent(_ ctx: CGContext) {
        if ctx.isPathEmpty {
            return
        }
        switch state.stroke {
        case let .color(color):
            ctx.setStrokeColor(color)
            ctx.strokePath()
        case let .gradient(id):
            ctx.saveGState()
            ctx.replacePathWithStrokedPath()
            ctx.twClip()
            drawGradient(ctx, id)
            ctx.restoreGState()
        }
    }

    private func paintsNothing(_ gradient: Gradient) -> Bool {
        let g = gradient.args
        switch gradient.kind {
        case "linear":
            return g.count < 4 || (g[0] == g[2] && g[1] == g[3])
        case "radial":
            return g.count < 6 || (g[0] == g[3] && g[1] == g[4] && g[2] == g[5])
        default:
            return true
        }
    }

    private func drawGradient(_ ctx: CGContext, _ id: Int) {
        guard var gradient = gradients[id], !gradient.stops.isEmpty, !paintsNothing(gradient) else {
            return
        }
        if gradient.compiled == nil {
            let stops = gradient.stops.sorted { $0.0 < $1.0 }
            gradient.compiled = CGGradient(colorsSpace: space, colors: stops.map { $0.1 } as CFArray, locations: stops.map { min(1, max(0, $0.0)) })
            gradient.lastColor = stops.last?.1
            gradients[id] = gradient
        }
        guard let cg = gradient.compiled else {
            return
        }
        let g = gradient.args
        let options: CGGradientDrawingOptions = [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        switch gradient.kind {
        case "linear" where g.count >= 4:
            ctx.drawLinearGradient(cg, start: CGPoint(x: g[0], y: g[1]), end: CGPoint(x: g[2], y: g[3]), options: options)
        case "radial" where g.count >= 6:
            ctx.drawRadialGradient(cg, startCenter: CGPoint(x: g[0], y: g[1]), startRadius: g[2], endCenter: CGPoint(x: g[3], y: g[4]), endRadius: g[5], options: options)
        default:
            break
        }
    }

    private func drawImage(_ ctx: CGContext, _ image: CGImage, _ rect: CGRect) {
        ctx.saveGState()
        ctx.translateBy(x: rect.minX, y: rect.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: rect.width, height: rect.height))
        ctx.restoreGState()
    }

    private func line(_ text: String) -> CTLine {
        let key = TextKey(text: text, font: state.font)
        if let cached = textLines[key] {
            return cached
        }
        let attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): CSSFont.make(state.font),
            NSAttributedString.Key(kCTForegroundColorFromContextAttributeName as String): true
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
        if text.utf8.count + state.font.utf8.count <= 256 {
            if textKeys.count < WidgetCanvas.maxCachedTextLines {
                textKeys.append(key)
            } else {
                textLines.removeValue(forKey: textKeys[textCursor])
                textKeys[textCursor] = key
                textCursor = (textCursor + 1) % WidgetCanvas.maxCachedTextLines
            }
            textLines[key] = line
        }
        return line
    }

    private struct Baselines {
        let ascent: CGFloat
        let descent: CGFloat
        let em: (ascent: CGFloat, descent: CGFloat)
    }

    private func baselines(_ font: CTFont) -> Baselines {
        if let known = fontBaselines[state.font] {
            return known
        }
        var ascent = CTFontGetAscent(font)
        let descent = CTFontGetDescent(font)
        if ["times", "helvetica", "courier"].contains((CTFontCopyFamilyName(font) as String).lowercased()) {
            ascent += floor((ascent + descent) * 0.15 + 0.5)
        }
        let size = CTFontGetSize(font)
        var em = (ascent: ascent, descent: descent)
        if let table = CTFontCopyTable(font, CTFontTableTag(kCTFontTableOS2), []) as Data?, table.count >= 72 {
            let typoAscent = CGFloat(Int16(bitPattern: UInt16(table[68]) << 8 | UInt16(table[69])))
            let typoDescent = -CGFloat(Int16(bitPattern: UInt16(table[70]) << 8 | UInt16(table[71])))
            if typoAscent >= 0, typoAscent + typoDescent > 0, typoAscent <= typoAscent + typoDescent {
                em = (typoAscent, typoDescent)
            }
        }
        let height = em.ascent + em.descent
        let normalized = height > 0 ? (em.ascent * size / height * 64).rounded() / 64 : 0
        let result = Baselines(ascent: ascent, descent: descent, em: (normalized, height > 0 ? size - normalized : 0))
        if fontBaselines.count >= 256 {
            fontBaselines.removeAll(keepingCapacity: true)
        }
        fontBaselines[state.font] = result
        return result
    }

    func measure(_ text: String) -> CGFloat {
        return CGFloat(CTLineGetTypographicBounds(line(text), nil, nil, nil))
    }

    private func drawText(_ ctx: CGContext, _ text: String, x: CGFloat, y: CGFloat, maxWidth: CGFloat?, stroke: Bool) {
        let font = CSSFont.make(state.font)
        var paintColor: CGColor
        switch stroke ? state.stroke : state.fill {
        case let .color(color):
            paintColor = color
        case let .gradient(id):
            guard let gradient = gradients[id], !paintsNothing(gradient), let first = gradient.stops.first else {
                return
            }
            paintColor = first.1
        }
        let ctLine = line(text)
        var ascent: CGFloat = 0, descent: CGFloat = 0
        let lineWidth = CGFloat(CTLineGetTypographicBounds(ctLine, &ascent, &descent, nil))
        var dx: CGFloat = 0
        switch state.align {
        case "center":
            dx = -lineWidth / 2
        case "right", "end":
            dx = -lineWidth
        default:
            break
        }
        var dy: CGFloat = 0
        switch state.baseline {
        case "top":
            dy = baselines(font).em.ascent
        case "hanging":
            dy = baselines(font).ascent * 0.8
        case "middle":
            let em = baselines(font).em
            dy = (em.ascent - em.descent) / 2
        case "bottom":
            dy = -baselines(font).em.descent
        case "ideographic":
            dy = -baselines(font).descent
        default:
            break
        }
        ctx.saveGState()
        ctx.translateBy(x: x, y: y)
        if let maxWidth = maxWidth, maxWidth > 0, lineWidth > maxWidth {
            ctx.scaleBy(x: maxWidth / lineWidth, y: 1)
        }
        ctx.scaleBy(x: 1, y: -1)
        ctx.textMatrix = .identity
        ctx.textPosition = CGPoint(x: dx, y: -dy)
        ctx.setAllowsFontSubpixelPositioning(true)
        ctx.setShouldSubpixelPositionFonts(true)
        ctx.setShouldSubpixelQuantizeFonts(false)
        if stroke {
            ctx.setStrokeColor(paintColor)
            ctx.setTextDrawingMode(.stroke)
        } else {
            ctx.setFillColor(paintColor)
            ctx.setTextDrawingMode(.fill)
        }
        CTLineDraw(ctLine, ctx)
        ctx.restoreGState()
    }

    private func pathCall(_ path: CGMutablePath, _ method: CanvasName, _ a: Args) {
        let start = { (point: CGPoint) in
            if path.isEmpty {
                path.move(to: point)
            }
        }
        switch method {
        case .moveTo:
            path.move(to: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .lineTo:
            start(CGPoint(x: n(a, 0), y: n(a, 1)))
            path.addLine(to: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .bezierCurveTo:
            start(CGPoint(x: n(a, 0), y: n(a, 1)))
            path.addCurve(to: CGPoint(x: n(a, 4), y: n(a, 5)), control1: CGPoint(x: n(a, 0), y: n(a, 1)), control2: CGPoint(x: n(a, 2), y: n(a, 3)))
        case .quadraticCurveTo:
            start(CGPoint(x: n(a, 0), y: n(a, 1)))
            path.addQuadCurve(to: CGPoint(x: n(a, 2), y: n(a, 3)), control: CGPoint(x: n(a, 0), y: n(a, 1)))
        case .arc:
            path.addArc(center: CGPoint(x: n(a, 0), y: n(a, 1)), radius: max(0, n(a, 2)), startAngle: n(a, 3), endAngle: n(a, 4), clockwise: WidgetCanvas.truthy(a, 5))
        case .arcTo:
            start(CGPoint(x: n(a, 0), y: n(a, 1)))
            path.addArc(tangent1End: CGPoint(x: n(a, 0), y: n(a, 1)), tangent2End: CGPoint(x: n(a, 2), y: n(a, 3)), radius: max(0, n(a, 4)))
        case .ellipse:
            WidgetCanvas.ellipse(path, a.map(WidgetCanvas.value), anticlockwise: WidgetCanvas.truthy(a, 7))
        case .rect:
            path.addRect(CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3)))
        case .roundRect:
            let rect = CGRect(x: n(a, 0), y: n(a, 1), width: n(a, 2), height: n(a, 3)).standardized
            let radius = min(n(a, 4), rect.width / 2, rect.height / 2)
            path.addRoundedRect(in: rect, cornerWidth: max(0, radius), cornerHeight: max(0, radius))
        case .closePath:
            path.closeSubpath()
        case .addPath:
            if case let .path(id)? = a.first, let other = paths[id] {
                path.addPath(other)
            }
        default:
            break
        }
    }

    private static func ellipse(_ path: CGMutablePath, _ v: [CGFloat], anticlockwise: Bool) {
        guard v.count >= 7 else {
            return
        }
        let transform = CGAffineTransform(translationX: v[0], y: v[1]).rotated(by: v[4]).scaledBy(x: max(v[2], 0.0001), y: max(v[3], 0.0001))
        path.addArc(center: .zero, radius: 1, startAngle: v[5], endAngle: v[6], clockwise: anticlockwise, transform: transform)
    }

    func isPointInPath(_ args: [Any]) -> Bool {
        finishGPU()
        let values = ArraySlice(args.map(CanvasStream.resolve))
        var path: CGPath? = context?.path
        var offset = 0
        if case let .path(id)? = values.first {
            path = paths[id]
            offset = 1
        }
        guard let target = path else {
            return false
        }
        let point = CGPoint(x: n(values, offset), y: n(values, offset + 1))
        return target.contains(point, using: WidgetCanvas.evenOdd(values, offset + 2) ? .evenOdd : .winding)
    }

    func lineDash() -> [Double] {
        return state.dash.map(Double.init)
    }

    func pixels(x: Int, y: Int, w requestedW: Int, h requestedH: Int) -> Data {
        finishGPU()
        let w = max(0, min(requestedW, 8192)), h = max(0, min(requestedH, 8192))
        if w * h > WidgetCanvas.maxPixels {
            return Data()
        }
        var data = Data(count: w * h * 4)
        guard w > 0, h > 0, let image = image else {
            return data
        }
        data.withUnsafeMutableBytes { raw in
            guard let ctx = CGContext(data: raw.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
                return
            }
            ctx.draw(image, in: CGRect(x: -x, y: y + h - height, width: width, height: height))
        }
        data.withUnsafeMutableBytes { raw in
            let p = raw.bindMemory(to: UInt8.self)
            var i = 0
            while i < p.count {
                let a = Int(p[i + 3])
                if a > 0 && a < 255 {
                    p[i] = UInt8(min(255, Int(p[i]) * 255 / a))
                    p[i + 1] = UInt8(min(255, Int(p[i + 1]) * 255 / a))
                    p[i + 2] = UInt8(min(255, Int(p[i + 2]) * 255 / a))
                }
                i += 4
            }
        }
        return data
    }

    static func image(rgba: Data, width: Int, height: Int) -> CGImage? {
        guard width > 0, height > 0, width <= 8192, height <= 8192, width * height <= maxPixels, rgba.count >= width * height * 4, let provider = CGDataProvider(data: rgba as CFData) else {
            return nil
        }
        return CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.last.rawValue), provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }
}

enum CSSColor {
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    static func srgb(_ r: Double, green g: Double, blue b: Double, alpha a: Double) -> CGColor {
        return CGColor(colorSpace: space, components: [CGFloat(r), CGFloat(g), CGFloat(b), CGFloat(a)])!
    }

    private static let lock = NSLock()
    private static var cache: [String: CGColor?] = [:]
    static let cacheLimit = 512

    static func parse(_ text: String) -> CGColor? {
        lock.lock()
        if let cached = cache[text] {
            lock.unlock()
            return cached
        }
        lock.unlock()
        let color = resolve(text)
        lock.lock()
        if cache.count >= cacheLimit {
            cache.removeAll(keepingCapacity: true)
        }
        cache[text] = .some(color)
        lock.unlock()
        return color
    }

    static var cachedCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return cache.count
    }

    private static func resolve(_ text: String) -> CGColor? {
        let normalized = CSSInterpolate.normalizeColors(text.trimmingCharacters(in: .whitespaces))
        if normalized.hasPrefix("rgba(") {
            let values = normalized.dropFirst(5).dropLast().split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            if values.count == 4 {
                return CSSColor.srgb( values[0] / 255, green: values[1] / 255, blue: values[2] / 255, alpha: values[3])
            }
        }
        let lower = text.lowercased().trimmingCharacters(in: .whitespaces)
        if lower.hasPrefix("hsl") , let open = lower.firstIndex(of: "("), let close = lower.lastIndex(of: ")") {
            let parts = lower[lower.index(after: open)..<close].split(whereSeparator: { $0 == "," || $0 == " " || $0 == "/" }).map(String.init)
            let values = parts.map { part -> Double in
                var p = part
                if p.hasSuffix("%") { p.removeLast(); return (Double(p) ?? 0) / 100 }
                if p.hasSuffix("deg") { p.removeLast(3) }
                return Double(p) ?? 0
            }
            if values.count >= 3 {
                let h = values[0].truncatingRemainder(dividingBy: 360) / 360, s = values[1], l = values[2]
                let q = l < 0.5 ? l * (1 + s) : l + s - l * s, p = 2 * l - q
                func hue(_ t0: Double) -> Double {
                    var t = t0
                    if t < 0 { t += 1 }
                    if t > 1 { t -= 1 }
                    if t < 1 / 6 { return p + (q - p) * 6 * t }
                    if t < 1 / 2 { return q }
                    if t < 2 / 3 { return p + (q - p) * (2 / 3 - t) * 6 }
                    return p
                }
                return CSSColor.srgb( hue(h + 1 / 3), green: hue(h), blue: hue(h - 1 / 3), alpha: values.count > 3 ? values[3] : 1)
            }
        }
        if let named = NSColor(named: lower) {
            return named.cgColor
        }
        return extraNamed[lower].map { CSSColor.srgb( $0.0 / 255, green: $0.1 / 255, blue: $0.2 / 255, alpha: 1) }
    }

    private static let extraNamed: [String: (Double, Double, Double)] = [
        "silver": (192, 192, 192), "maroon": (128, 0, 0), "purple": (128, 0, 128), "fuchsia": (255, 0, 255), "lime": (0, 255, 0),
        "olive": (128, 128, 0), "navy": (0, 0, 128), "teal": (0, 128, 128), "aqua": (0, 255, 255), "darkgray": (169, 169, 169),
        "lightgray": (211, 211, 211), "lightgrey": (211, 211, 211), "darkgrey": (169, 169, 169), "dimgray": (105, 105, 105),
        "gold": (255, 215, 0), "pink": (255, 192, 203), "crimson": (220, 20, 60), "tomato": (255, 99, 71), "coral": (255, 127, 80),
        "steelblue": (70, 130, 180), "royalblue": (65, 105, 225), "dodgerblue": (30, 144, 255), "skyblue": (135, 206, 235),
        "indigo": (75, 0, 130), "violet": (238, 130, 238), "brown": (165, 42, 42), "cyan": (0, 255, 255), "magenta": (255, 0, 255),
        "whitesmoke": (245, 245, 245), "gainsboro": (220, 220, 220), "seagreen": (46, 139, 87), "forestgreen": (34, 139, 34),
        "darkorange": (255, 140, 0), "slategray": (112, 128, 144), "lightblue": (173, 216, 230), "salmon": (250, 128, 114),
    ]
}

enum SVGPath {
    static func append(_ d: String, to path: CGMutablePath) {
        var tokens: [String] = []
        var current = ""
        for ch in d {
            if ch.isLetter && ch != "e" && ch != "E" {
                if !current.isEmpty { tokens.append(current); current = "" }
                tokens.append(String(ch))
            } else if ch == "," || ch == " " || ch == "\n" || ch == "\t" {
                if !current.isEmpty { tokens.append(current); current = "" }
            } else if ch == "-" && !current.isEmpty && !current.hasSuffix("e") && !current.hasSuffix("E") {
                tokens.append(current)
                current = "-"
            } else if ch == "." && current.contains(".") {
                tokens.append(current)
                current = "."
            } else {
                current.append(ch)
            }
        }
        if !current.isEmpty { tokens.append(current) }
        var i = 0
        var command: Character = "M"
        var point = CGPoint.zero
        var startPoint = CGPoint.zero
        var lastControl: CGPoint?
        func next() -> CGFloat? {
            guard i < tokens.count, let value = Double(tokens[i]) else { return nil }
            i += 1
            return CGFloat(value)
        }
        while i < tokens.count {
            if let ch = tokens[i].first, tokens[i].count == 1, ch.isLetter {
                command = ch
                i += 1
                if ch == "Z" || ch == "z" {
                    path.closeSubpath()
                    point = startPoint
                    lastControl = nil
                    continue
                }
            }
            let relative = command.isLowercase
            let origin = relative ? point : .zero
            switch command.uppercased().first! {
            case "M":
                guard let x = next(), let y = next() else { return }
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.move(to: point)
                startPoint = point
                command = relative ? "l" : "L"
                lastControl = nil
            case "L":
                guard let x = next(), let y = next() else { return }
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addLine(to: point)
                lastControl = nil
            case "H":
                guard let x = next() else { return }
                point = CGPoint(x: (relative ? point.x : 0) + x, y: point.y)
                path.addLine(to: point)
                lastControl = nil
            case "V":
                guard let y = next() else { return }
                point = CGPoint(x: point.x, y: (relative ? point.y : 0) + y)
                path.addLine(to: point)
                lastControl = nil
            case "C":
                guard let x1 = next(), let y1 = next(), let x2 = next(), let y2 = next(), let x = next(), let y = next() else { return }
                let c2 = CGPoint(x: origin.x + x2, y: origin.y + y2)
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addCurve(to: point, control1: CGPoint(x: origin.x + x1, y: origin.y + y1), control2: c2)
                lastControl = c2
            case "S":
                guard let x2 = next(), let y2 = next(), let x = next(), let y = next() else { return }
                let c1 = lastControl.map { CGPoint(x: 2 * point.x - $0.x, y: 2 * point.y - $0.y) } ?? point
                let c2 = CGPoint(x: origin.x + x2, y: origin.y + y2)
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addCurve(to: point, control1: c1, control2: c2)
                lastControl = c2
            case "Q":
                guard let x1 = next(), let y1 = next(), let x = next(), let y = next() else { return }
                let c = CGPoint(x: origin.x + x1, y: origin.y + y1)
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addQuadCurve(to: point, control: c)
                lastControl = c
            case "T":
                guard let x = next(), let y = next() else { return }
                let c = lastControl.map { CGPoint(x: 2 * point.x - $0.x, y: 2 * point.y - $0.y) } ?? point
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addQuadCurve(to: point, control: c)
                lastControl = c
            case "A":
                guard let _ = next(), let _ = next(), let _ = next(), let _ = next(), let _ = next(), let x = next(), let y = next() else { return }
                point = CGPoint(x: origin.x + x, y: origin.y + y)
                path.addLine(to: point)
                lastControl = nil
            default:
                return
            }
        }
    }
}
