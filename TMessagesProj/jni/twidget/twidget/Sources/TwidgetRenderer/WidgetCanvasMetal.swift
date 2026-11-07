#if canImport(Metal) && canImport(IOSurface)
import CoreGraphics
import Foundation
import IOSurface
import Metal

final class WidgetCanvasMetal: WidgetCanvasBackend {
    static let enabled = ProcessInfo.processInfo.environment["TW_CANVAS_GPU"] != "0"
    static let maxInstancesPerBuffer = 65_536
    static let maxAreaPerBuffer: Double = 536_870_912
    static let maxInFlight = 3

    private struct Instance {
        var rect: SIMD4<Float>
        var matrix: SIMD4<Float>
        var offset: SIMD4<Float>
        var color: SIMD4<Float>
    }

    private enum Blend: Int {
        case sourceOver = 0
        case lighter = 1
        case erase = 2
    }

    private final class Shared {
        let device: MTLDevice
        let queue: MTLCommandQueue
        private let lock = NSLock()
        private var pipelines: [MTLRenderPipelineState]?
        private var compiling = false

        init?() {
            guard let device = MTLCreateSystemDefaultDevice(), let queue = device.makeCommandQueue() else {
                return nil
            }
            self.device = device
            self.queue = queue
        }

        private let compiled = DispatchGroup()

        var ready: [MTLRenderPipelineState]? {
            lock.lock()
            defer { lock.unlock() }
            if pipelines == nil && !compiling {
                compiling = true
                compiled.enter()
                DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                    self?.compile()
                    self?.compiled.leave()
                }
            }
            return pipelines
        }

        func wait() {
            _ = ready
            compiled.wait()
        }

        private func compile() {
            var made: [MTLRenderPipelineState] = []
            if let library = try? device.makeLibrary(source: WidgetCanvasMetal.shaderSource, options: nil), let vertex = library.makeFunction(name: "twc_rect_vertex"), let rectFragment = library.makeFunction(name: "twc_rect_fragment"), let circleFragment = library.makeFunction(name: "twc_circle_fragment") {
                for (fragment, blend) in [rectFragment, circleFragment].flatMap({ f in [Blend.sourceOver, .lighter, .erase].map { (f, $0) } }) {
                    let descriptor = MTLRenderPipelineDescriptor()
                    descriptor.vertexFunction = vertex
                    descriptor.fragmentFunction = fragment
                    let attachment = descriptor.colorAttachments[0]!
                    attachment.pixelFormat = .bgra8Unorm
                    attachment.isBlendingEnabled = true
                    attachment.rgbBlendOperation = .add
                    attachment.alphaBlendOperation = .add
                    switch blend {
                    case .sourceOver:
                        attachment.sourceRGBBlendFactor = .one
                        attachment.sourceAlphaBlendFactor = .one
                        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
                        attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
                    case .lighter:
                        attachment.sourceRGBBlendFactor = .one
                        attachment.sourceAlphaBlendFactor = .one
                        attachment.destinationRGBBlendFactor = .one
                        attachment.destinationAlphaBlendFactor = .one
                    case .erase:
                        attachment.sourceRGBBlendFactor = .zero
                        attachment.sourceAlphaBlendFactor = .zero
                        attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
                        attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
                    }
                    guard let state = try? device.makeRenderPipelineState(descriptor: descriptor) else {
                        made = []
                        break
                    }
                    made.append(state)
                }
            }
            lock.lock()
            pipelines = made.count == 6 ? made : []
            lock.unlock()
        }
    }

    private static let shared: Shared? = enabled ? Shared() : nil

    static var available: Bool {
        return shared != nil
    }

    static func prepare(wait: Bool) {
        if wait {
            shared?.wait()
        } else {
            _ = shared?.ready
        }
    }

    private let shared: Shared
    private var texture: MTLTexture?
    private var textureSurface: IOSurfaceRef?
    private var instances: [Instance] = []
    private var runs: [(pipeline: Int, start: Int, count: Int)] = []
    private var shape = PathShape.other
    private var area: Double = 0
    private var transform = CGAffineTransform.identity
    private var transformValid = false
    private var buffers: [MTLBuffer] = []
    private var bufferIndex = 0
    private var last: MTLCommandBuffer?
    private let inFlight = DispatchSemaphore(value: WidgetCanvasMetal.maxInFlight)
    private(set) var gpuMilliseconds = 0.0
    private(set) var gpuFrames = 0
    private(set) var acceleratedBatches = 0
    private(set) var committedBuffers = 0
    private(set) var fallbackBatches = 0
    private var gpuLock = NSLock()

    init?() {
        guard let shared = WidgetCanvasMetal.shared else {
            return nil
        }
        self.shared = shared
    }

    deinit {
        finish()
    }

    func resized(canvas: WidgetCanvas) {
        finish()
        texture = nil
        textureSurface = nil
    }

    private func target(_ canvas: WidgetCanvas) -> MTLTexture? {
        guard let storage = canvas.storage else {
            return nil
        }
        if let texture = texture, textureSurface === storage.surface {
            return texture
        }
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: storage.width, height: storage.height, mipmapped: false)
        descriptor.usage = [.renderTarget, .shaderRead]
        if #available(macOS 10.15, *), shared.device.hasUnifiedMemory {
            descriptor.storageMode = .shared
        } else {
            descriptor.storageMode = .managed
        }
        texture = shared.device.makeTexture(descriptor: descriptor, iosurface: storage.surface, plane: 0)
        textureSurface = storage.surface
        return texture
    }

    private static let gpuCalls: Set<CanvasName> = [.save, .restore, .scale, .rotate, .translate, .transform, .setTransform, .resetTransform, .fillRect, .clearRect, .beginPath, .closePath, .moveTo, .lineTo, .bezierCurveTo, .quadraticCurveTo, .arc, .arcTo, .ellipse, .rect, .roundRect, .setLineDash]
    private static let gpuSets: Set<CanvasName> = [.fillStyle, .strokeStyle, .lineWidth, .lineCap, .lineJoin, .miterLimit, .lineDashOffset, .font, .textAlign, .textBaseline, .globalAlpha, .globalCompositeOperation, .imageSmoothingEnabled]
    private static let gpuComposites: Set<String> = ["source-over", "lighter", "destination-out"]

    enum PathShape {
        case empty
        case circle(center: CGPoint, radius: CGFloat, transform: CGAffineTransform)
        case other
    }

    static func fullCircle(_ a: ArraySlice<CanvasArg>) -> (center: CGPoint, radius: CGFloat)? {
        func n(_ i: Int) -> Double? {
            if i < a.count, case let .number(v) = a[a.startIndex + i], v.isFinite {
                return v
            }
            return nil
        }
        guard a.count >= 5, let x = n(0), let y = n(1), let r = n(2), let start = n(3), let end = n(4), r > 0, r < 1e6, abs(x) < 1e7, abs(y) < 1e7 else {
            return nil
        }
        var anticlockwise = false
        if a.count > 5 {
            switch a[a.startIndex + 5] {
            case let .number(v):
                anticlockwise = v != 0
            case let .string(text):
                anticlockwise = !text.isEmpty && text != "false"
            default:
                break
            }
        }
        let sweep = anticlockwise ? start - end : end - start
        guard sweep >= 2 * Double.pi, sweep < 1e6 else {
            return nil
        }
        return (CGPoint(x: x, y: y), CGFloat(r))
    }

    static func shape(after name: CanvasName, _ a: ArraySlice<CanvasArg>, from shape: PathShape, transform: CGAffineTransform) -> PathShape {
        switch name {
        case .beginPath:
            return .empty
        case .arc:
            if case .empty = shape, let circle = fullCircle(a) {
                return .circle(center: circle.center, radius: circle.radius, transform: transform)
            }
            return .other
        case .closePath:
            return shape
        case .moveTo, .lineTo, .bezierCurveTo, .quadraticCurveTo, .arcTo, .ellipse, .rect, .roundRect:
            return .other
        default:
            return shape
        }
    }

    static func plainFill(_ a: ArraySlice<CanvasArg>) -> Bool {
        guard let first = a.first else {
            return true
        }
        if case let .string(rule) = first, a.count == 1 {
            return rule == "nonzero" || rule == "evenodd"
        }
        return false
    }

    static func eligible(_ stream: CanvasStream) -> Bool {
        var draws = 0
        var shape = PathShape.other
        for op in stream.ops {
            switch op.kind {
            case .call:
                let args = stream.arguments(op)
                if op.name == .fill {
                    guard case .circle = shape, plainFill(args) else {
                        return false
                    }
                    draws += 1
                    continue
                }
                guard gpuCalls.contains(op.name) else {
                    return false
                }
                if op.name == .fillRect || op.name == .clearRect {
                    draws += 1
                }
                shape = WidgetCanvasMetal.shape(after: op.name, args, from: shape, transform: .identity)
            case .set:
                guard gpuSets.contains(op.name) else {
                    return false
                }
                let value = stream.args[Int(op.start)]
                switch op.name {
                case .fillStyle:
                    guard case .string = value else {
                        return false
                    }
                case .globalCompositeOperation:
                    guard case let .string(mode) = value, gpuComposites.contains(mode) else {
                        return false
                    }
                default:
                    break
                }
            case .resize:
                return false
            case .gradient, .gradientStop, .pathNew, .pathCall:
                break
            }
        }
        return draws > 0
    }

    func begin(_ stream: CanvasStream, canvas: WidgetCanvas) -> Bool {
        guard canvas.isPlain, WidgetCanvasMetal.gpuComposites.contains(canvas.composite), WidgetCanvasMetal.eligible(stream), shared.ready?.isEmpty == false, target(canvas) != nil else {
            fallbackBatches += 1
            return false
        }
        acceleratedBatches += 1
        instances.removeAll(keepingCapacity: true)
        runs.removeAll(keepingCapacity: true)
        area = 0
        shape = .other
        return true
    }

    func draw(_ name: CanvasName, _ a: ArraySlice<CanvasArg>, canvas: WidgetCanvas) -> Bool {
        let erase: Bool
        switch name {
        case .fillRect:
            erase = false
        case .clearRect:
            erase = true
        case .fill:
            if case let .circle(center, radius, transform) = shape {
                fillCircle(center, radius, transform, canvas: canvas)
                return true
            }
            return false
        default:
            shape = WidgetCanvasMetal.shape(after: name, a, from: shape, transform: canvas.currentTransform)
            return false
        }
        func n(_ i: Int) -> Double {
            if i < a.count, case let .number(v) = a[a.startIndex + i], v.isFinite {
                return v
            }
            return 0
        }
        var x = n(0), y = n(1), w = n(2), h = n(3)
        if w < 0 {
            x += w
            w = -w
        }
        if h < 0 {
            y += h
            h = -h
        }
        guard w > 0, h > 0, w.isFinite, h.isFinite else {
            return true
        }
        let t = canvas.currentTransform
        let det = Double(t.a * t.d - t.b * t.c)
        guard det.isFinite, abs(det) > 1e-12, t.tx.isFinite, t.ty.isFinite else {
            return true
        }
        let canvasBounds = CGRect(x: 0, y: 0, width: canvas.width, height: canvas.height)
        let visible = canvasBounds.insetBy(dx: -2, dy: -2).applying(t.inverted())
        let local = CGRect(x: x, y: y, width: w, height: h).intersection(visible)
        guard !local.isNull, local.width > 0, local.height > 0 else {
            return true
        }
        let sx = Float(abs(det) / Double((t.c * t.c + t.d * t.d).squareRoot())), sy = Float(abs(det) / Double((t.a * t.a + t.b * t.b).squareRoot()))
        guard sx.isFinite, sy.isFinite, sx > 1e-6, sy > 1e-6 else {
            return true
        }
        guard let (blend, color) = paint(erase: erase, canvas: canvas) else {
            return true
        }
        let bounds = local.applying(t).intersection(canvasBounds).insetBy(dx: -1, dy: -1)
        guard !bounds.isNull, !bounds.isEmpty else {
            return true
        }
        let cost = Double(bounds.width * bounds.height)
        if !instances.isEmpty && (area + cost > WidgetCanvasMetal.maxAreaPerBuffer || instances.count >= WidgetCanvasMetal.maxInstancesPerBuffer) {
            flushChunk(canvas)
        }
        area += cost
        let center = CGPoint(x: local.midX, y: local.midY).applying(t)
        let hw = Float(local.width / 2), hh = Float(local.height / 2)
        instances.append(Instance(rect: SIMD4(-hw, -hh, hw * 2, hh * 2), matrix: SIMD4(Float(t.a), Float(t.b), Float(t.c), Float(t.d)), offset: SIMD4(Float(center.x), Float(center.y), sx, sy), color: color))
        append(pipeline: blend.rawValue)
        return true
    }

    private func append(pipeline: Int) {
        if let lastRun = runs.last, lastRun.pipeline == pipeline {
            runs[runs.count - 1].count += 1
        } else {
            runs.append((pipeline, instances.count - 1, 1))
        }
    }

    private func paint(erase: Bool, canvas: WidgetCanvas) -> (Blend, SIMD4<Float>)? {
        if erase {
            return (.erase, SIMD4(0, 0, 0, 1))
        }
        guard let rgba = canvas.fillRGBA else {
            return nil
        }
        let alpha = rgba.w * canvas.globalAlpha
        guard alpha > 0 else {
            return nil
        }
        let color = SIMD4(rgba.x * alpha, rgba.y * alpha, rgba.z * alpha, alpha)
        switch canvas.composite {
        case "lighter":
            return (.lighter, color)
        case "destination-out":
            return (.erase, color)
        default:
            return (.sourceOver, color)
        }
    }

    private func fillCircle(_ center: CGPoint, _ radius: CGFloat, _ t: CGAffineTransform, canvas: WidgetCanvas) {
        let det = Double(t.a * t.d - t.b * t.c)
        guard det.isFinite, abs(det) > 1e-12, t.tx.isFinite, t.ty.isFinite, let (blend, color) = paint(erase: false, canvas: canvas) else {
            return
        }
        let canvasBounds = CGRect(x: 0, y: 0, width: canvas.width, height: canvas.height)
        let box = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
        let bounds = box.applying(t).intersection(canvasBounds).insetBy(dx: -1, dy: -1)
        guard !bounds.isNull, !bounds.isEmpty else {
            return
        }
        let sx = Float(abs(det) / Double((t.c * t.c + t.d * t.d).squareRoot())), sy = Float(abs(det) / Double((t.a * t.a + t.b * t.b).squareRoot()))
        guard sx.isFinite, sy.isFinite, sx > 1e-6, sy > 1e-6 else {
            return
        }
        let cost = Double(bounds.width * bounds.height)
        if !instances.isEmpty && (area + cost > WidgetCanvasMetal.maxAreaPerBuffer || instances.count >= WidgetCanvasMetal.maxInstancesPerBuffer) {
            flushChunk(canvas)
        }
        area += cost
        let device = center.applying(t)
        let r = Float(radius)
        instances.append(Instance(rect: SIMD4(-r, -r, r * 2, r * 2), matrix: SIMD4(Float(t.a), Float(t.b), Float(t.c), Float(t.d)), offset: SIMD4(Float(device.x), Float(device.y), sx, sy), color: color))
        append(pipeline: 3 + blend.rawValue)
    }

    private func buffer(bytes: Int) -> MTLBuffer? {
        if buffers.count < WidgetCanvasMetal.maxInFlight + 1 {
            let made = shared.device.makeBuffer(length: max(bytes, 64 * 1024), options: .storageModeShared)
            if let made = made {
                buffers.append(made)
            }
            return made
        }
        bufferIndex = (bufferIndex + 1) % buffers.count
        if buffers[bufferIndex].length < bytes {
            guard let made = shared.device.makeBuffer(length: bytes, options: .storageModeShared) else {
                return nil
            }
            buffers[bufferIndex] = made
        }
        return buffers[bufferIndex]
    }

    private func encodeChunk(_ canvas: WidgetCanvas, into commands: MTLCommandBuffer) {
        guard !instances.isEmpty, let pipelines = shared.ready, pipelines.count == 6, let texture = target(canvas) else {
            return
        }
        let bytes = instances.count * MemoryLayout<Instance>.stride
        guard let buffer = buffer(bytes: bytes) else {
            return
        }
        instances.withUnsafeBytes { raw in
            buffer.contents().copyMemory(from: raw.baseAddress!, byteCount: bytes)
        }
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = texture
        pass.colorAttachments[0].loadAction = .load
        pass.colorAttachments[0].storeAction = .store
        guard let encoder = commands.makeRenderCommandEncoder(descriptor: pass) else {
            return
        }
        var viewport = SIMD2<Float>(Float(texture.width), Float(texture.height))
        encoder.setVertexBuffer(buffer, offset: 0, index: 0)
        encoder.setVertexBytes(&viewport, length: MemoryLayout<SIMD2<Float>>.stride, index: 1)
        for run in runs {
            encoder.setRenderPipelineState(pipelines[run.pipeline])
            encoder.drawPrimitives(type: .triangleStrip, vertexStart: 0, vertexCount: 4, instanceCount: run.count, baseInstance: run.start)
        }
        encoder.endEncoding()
        instances.removeAll(keepingCapacity: true)
        runs.removeAll(keepingCapacity: true)
        area = 0
    }

    private func makeCommands() -> MTLCommandBuffer? {
        if inFlight.wait(timeout: .now() + 2) == .timedOut {
            return nil
        }
        guard let commands = shared.queue.makeCommandBuffer() else {
            inFlight.signal()
            return nil
        }
        let semaphore = inFlight
        commands.addCompletedHandler { [weak self] done in
            if #available(macOS 10.15, *), done.gpuEndTime > done.gpuStartTime, let self = self {
                self.gpuLock.lock()
                self.gpuMilliseconds += (done.gpuEndTime - done.gpuStartTime) * 1000
                self.gpuFrames += 1
                self.gpuLock.unlock()
            }
            semaphore.signal()
        }
        return commands
    }

    var gpuStats: (milliseconds: Double, frames: Int) {
        gpuLock.lock()
        defer { gpuLock.unlock() }
        return (gpuMilliseconds, gpuFrames)
    }

    private func flushChunk(_ canvas: WidgetCanvas) {
        guard let commands = makeCommands() else {
            instances.removeAll(keepingCapacity: true)
            runs.removeAll(keepingCapacity: true)
            area = 0
            return
        }
        encodeChunk(canvas, into: commands)
        commands.commit()
        committedBuffers += 1
        last = commands
    }

    func commit(canvas: WidgetCanvas) {
        guard !instances.isEmpty else {
            return
        }
        guard let commands = makeCommands() else {
            instances.removeAll(keepingCapacity: true)
            runs.removeAll(keepingCapacity: true)
            return
        }
        encodeChunk(canvas, into: commands)
        commands.commit()
        committedBuffers += 1
        last = commands
    }

    func present(canvas: WidgetCanvas, to destination: IOSurfaceRef, completion: @escaping () -> Void) -> Bool {
        guard let source = target(canvas) else {
            return false
        }
        guard let commands = makeCommands() else {
            return false
        }
        let descriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: IOSurfaceGetWidth(destination), height: IOSurfaceGetHeight(destination), mipmapped: false)
        descriptor.usage = [.shaderRead]
        if #available(macOS 10.15, *), shared.device.hasUnifiedMemory {
            descriptor.storageMode = .shared
        } else {
            descriptor.storageMode = .managed
        }
        guard source.width == descriptor.width, source.height == descriptor.height, let output = shared.device.makeTexture(descriptor: descriptor, iosurface: destination, plane: 0), let blit = commands.makeBlitCommandEncoder() else {
            commands.commit()
            last = commands
            return false
        }
        blit.copy(from: source, sourceSlice: 0, sourceLevel: 0, sourceOrigin: MTLOrigin(x: 0, y: 0, z: 0), sourceSize: MTLSize(width: source.width, height: source.height, depth: 1), to: output, destinationSlice: 0, destinationLevel: 0, destinationOrigin: MTLOrigin(x: 0, y: 0, z: 0))
        blit.endEncoding()
        commands.addCompletedHandler { _ in
            DispatchQueue.main.async(execute: completion)
        }
        commands.commit()
        last = commands
        return true
    }

    func finish() {
        if let commands = last {
            last = nil
            commands.waitUntilCompleted()
        }
    }

    static let shaderSource = """
    #include <metal_stdlib>
    using namespace metal;

    struct TWCInstance {
        float4 rect;
        float4 matrix;
        float4 offset;
        float4 color;
    };

    struct TWCVarying {
        float4 position [[position]];
        float2 local;
        float4 rect [[flat]];
        float2 scale [[flat]];
        float4 color [[flat]];
        float4 matrix [[flat]];
    };

    vertex TWCVarying twc_rect_vertex(uint vid [[vertex_id]], uint iid [[instance_id]], const device TWCInstance* items [[buffer(0)]], constant float2& viewport [[buffer(1)]]) {
        TWCInstance item = items[iid];
        float2 corner = float2(float(vid & 1), float((vid >> 1) & 1));
        float2 margin = float2(1.0 / item.offset.z, 1.0 / item.offset.w);
        float2 local = item.rect.xy - margin + corner * (item.rect.zw + 2.0 * margin);
        float2 pixel = float2(item.matrix.x * local.x + item.matrix.z * local.y + item.offset.x, item.matrix.y * local.x + item.matrix.w * local.y + item.offset.y);
        TWCVarying out;
        out.position = float4(pixel.x / viewport.x * 2.0 - 1.0, 1.0 - pixel.y / viewport.y * 2.0, 0.0, 1.0);
        out.local = local;
        out.rect = item.rect;
        out.scale = item.offset.zw;
        out.color = item.color;
        out.matrix = item.matrix;
        return out;
    }

    fragment float4 twc_circle_fragment(TWCVarying in [[stage_in]]) {
        float r = in.rect.z * 0.5;
        float2 l = in.local;
        float len = length(l);
        float2 n = len > 1e-6 ? l / len : float2(1.0, 0.0);
        float4 m = in.matrix;
        float det = m.x * m.w - m.y * m.z;
        float2 g = float2(m.w * n.x - m.y * n.y, m.x * n.y - m.z * n.x) / det;
        float unit = max(length(g), 1e-6);
        float edge = (len - r) / unit;
        float radius = r / unit;
        float coverage = clamp(0.5 - edge, 0.0, 1.0);
        coverage = min(coverage, 3.14159265 * radius * radius);
        return in.color * coverage;
    }

    fragment float4 twc_rect_fragment(TWCVarying in [[stage_in]]) {
        float2 half_size = in.rect.zw * 0.5 * in.scale;
        float2 d = (in.local - (in.rect.xy + in.rect.zw * 0.5)) * in.scale;
        float2 coverage = clamp(min(half_size, d + 0.5) - max(-half_size, d - 0.5), 0.0, 1.0);
        return in.color * (coverage.x * coverage.y);
    }
    """
}
#endif
