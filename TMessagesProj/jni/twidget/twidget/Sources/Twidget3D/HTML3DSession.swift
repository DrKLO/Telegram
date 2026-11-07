import Foundation
import Metal
import QuartzCore
import simd

public enum HTML3DLayout {
    public static let header = 160
    public static let record = 40
    public static let maxLights = 4

    public static let view = 0
    public static let projection = 16
    public static let shadowViewProjection = 32
    public static let camera = 48
    public static let clear = 52
    public static let fog = 56
    public static let fogParams = 60
    public static let ambient = 64
    public static let hemiSky = 68
    public static let hemiGround = 72
    public static let hemiDirection = 76
    public static let directionalColor = 80
    public static let directionalDirection = 84
    public static let shadow = 88
    public static let shadowMapSize = 92
    public static let lightCount = 93
    public static let recordCount = 94
    public static let pixelRatio = 95
    public static let lights = 96

    public static let recordGeometry = 0
    public static let recordMaterial = 1
    public static let recordStart = 2
    public static let recordCount_ = 3
    public static let recordFlags = 4
    public static let recordInstances = 5
    public static let recordInstanceCount = 6
    public static let recordMatrix = 8
    public static let recordColor = 24
    public static let recordEmissive = 28
    public static let recordSpecular = 32
    public static let recordParams = 36
}

public struct HTML3DStats {
    public let frames: Int
    public let submitted: Int
    public let gpuSeconds: Double
    public let cpuSeconds: Double
    public let gpuTimed: Bool
    public let draws: Int
    public let triangles: Int
    public let memoryBytes: Int
    public let rejected: Int
    public let clipped: Int
}

struct HTML3DFrameUniforms {
    var view = matrix_identity_float4x4
    var projection = matrix_identity_float4x4
    var shadowViewProjection = matrix_identity_float4x4
    var cameraPosition = SIMD4<Float>(repeating: 0)
    var ambient = SIMD4<Float>(repeating: 0)
    var hemiSky = SIMD4<Float>(repeating: 0)
    var hemiGround = SIMD4<Float>(repeating: 0)
    var hemiDirection = SIMD4<Float>(repeating: 0)
    var directionalColor = SIMD4<Float>(repeating: 0)
    var directionalDirection = SIMD4<Float>(repeating: 0)
    var shadowParams = SIMD4<Float>(repeating: 0)
    var shadowSize = SIMD4<Float>(repeating: 0)
    var fogColor = SIMD4<Float>(repeating: 0)
    var fogParams = SIMD4<Float>(repeating: 0)
    var viewport = SIMD4<Float>(repeating: 0)
    var lightPosition = (SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0))
    var lightColor = (SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0))
    var lightDirection = (SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0))
    var lightCone = (SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0), SIMD4<Float>(repeating: 0))
    var counts = SIMD4<Int32>(repeating: 0)
}

struct HTML3DDrawUniforms {
    var base = matrix_identity_float4x4
    var color = SIMD4<Float>(repeating: 1)
    var emissive = SIMD4<Float>(repeating: 0)
    var specular = SIMD4<Float>(repeating: 0)
    var params = SIMD4<Float>(repeating: 0)
    var layout = SIMD4<UInt32>(repeating: 0)
}

final class HTML3DGPU {
    static let shared: HTML3DGPU? = HTML3DGPU()

    static let sampleCount = 4
    static let colorFormat: MTLPixelFormat = .bgra8Unorm_srgb
    static let depthFormat: MTLPixelFormat = .depth32Float

    let device: MTLDevice
    let commandQueue: MTLCommandQueue
    let dummyBuffer: MTLBuffer
    let dummyDepth: MTLTexture
    let shadowSampler: MTLSamplerState
    let memorylessTargets: Bool
    private let lock = NSLock()
    private var compiled: Pipelines?
    private var failed = false
    private var reserved = 0

    public static var processBudgetBytes = 512 << 20

    func reserve(_ bytes: Int) -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if bytes <= 0 {
            return true
        }
        if reserved + bytes > HTML3DGPU.processBudgetBytes {
            return false
        }
        reserved += bytes
        return true
    }

    func unreserve(_ bytes: Int) {
        lock.lock()
        reserved = max(0, reserved - bytes)
        lock.unlock()
    }

    var reservedBytes: Int {
        lock.lock()
        defer { lock.unlock() }
        return reserved
    }

    struct Pipelines {
        let mesh: MTLRenderPipelineState
        let meshBlend: MTLRenderPipelineState
        let points: MTLRenderPipelineState
        let pointsBlend: MTLRenderPipelineState
        let shadow: MTLRenderPipelineState
        let depthStates: [MTLDepthStencilState]
        let shadowDepth: MTLDepthStencilState
    }

    private init?() {
        guard let device = MTLCreateSystemDefaultDevice(), let commandQueue = device.makeCommandQueue() else {
            return nil
        }
        let ones = [Float](repeating: 1, count: 16)
        guard let dummyBuffer = device.makeBuffer(bytes: ones, length: ones.count * 4, options: .storageModeShared) else {
            return nil
        }
        let depthDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: HTML3DGPU.depthFormat, width: 1, height: 1, mipmapped: false)
        depthDesc.usage = [.shaderRead, .renderTarget]
        depthDesc.storageMode = .private
        guard let dummyDepth = device.makeTexture(descriptor: depthDesc) else {
            return nil
        }
        let samplerDesc = MTLSamplerDescriptor()
        samplerDesc.minFilter = .linear
        samplerDesc.magFilter = .linear
        samplerDesc.sAddressMode = .clampToEdge
        samplerDesc.tAddressMode = .clampToEdge
        samplerDesc.compareFunction = .lessEqual
        guard let shadowSampler = device.makeSamplerState(descriptor: samplerDesc) else {
            return nil
        }
        var memoryless = false
        if #available(macOS 11.0, *) {
            memoryless = device.supportsFamily(.apple7)
        }
        self.device = device
        self.commandQueue = commandQueue
        self.dummyBuffer = dummyBuffer
        self.dummyDepth = dummyDepth
        self.shadowSampler = shadowSampler
        self.memorylessTargets = memoryless
    }

    var pipelines: Pipelines? {
        lock.lock()
        defer { lock.unlock() }
        if compiled == nil, !failed {
            compiled = compile()
            failed = compiled == nil
        }
        return compiled
    }

    private func compile() -> Pipelines? {
        guard let library = try? device.makeLibrary(source: html3DShaderSource, options: nil),
              let vertex = library.makeFunction(name: "html3DVertex"),
              let fragment = library.makeFunction(name: "html3DFragment"),
              let pointVertex = library.makeFunction(name: "html3DPointVertex"),
              let pointFragment = library.makeFunction(name: "html3DPointFragment"),
              let shadowVertex = library.makeFunction(name: "html3DShadowVertex") else {
            return nil
        }
        func color(_ vertex: MTLFunction, _ fragment: MTLFunction, blend: Bool) -> MTLRenderPipelineState? {
            let desc = MTLRenderPipelineDescriptor()
            desc.vertexFunction = vertex
            desc.fragmentFunction = fragment
            desc.sampleCount = HTML3DGPU.sampleCount
            desc.colorAttachments[0].pixelFormat = HTML3DGPU.colorFormat
            desc.depthAttachmentPixelFormat = HTML3DGPU.depthFormat
            if blend {
                let attachment = desc.colorAttachments[0]!
                attachment.isBlendingEnabled = true
                attachment.rgbBlendOperation = .add
                attachment.alphaBlendOperation = .add
                attachment.sourceRGBBlendFactor = .sourceAlpha
                attachment.destinationRGBBlendFactor = .oneMinusSourceAlpha
                attachment.sourceAlphaBlendFactor = .one
                attachment.destinationAlphaBlendFactor = .oneMinusSourceAlpha
            }
            return try? device.makeRenderPipelineState(descriptor: desc)
        }
        let shadowDesc = MTLRenderPipelineDescriptor()
        shadowDesc.vertexFunction = shadowVertex
        shadowDesc.depthAttachmentPixelFormat = HTML3DGPU.depthFormat
        guard let mesh = color(vertex, fragment, blend: false),
              let meshBlend = color(vertex, fragment, blend: true),
              let points = color(pointVertex, pointFragment, blend: false),
              let pointsBlend = color(pointVertex, pointFragment, blend: true),
              let shadow = try? device.makeRenderPipelineState(descriptor: shadowDesc) else {
            return nil
        }
        var depthStates: [MTLDepthStencilState] = []
        for index in 0 ..< 4 {
            let desc = MTLDepthStencilDescriptor()
            desc.depthCompareFunction = index & 1 != 0 ? .lessEqual : .always
            desc.isDepthWriteEnabled = index & 2 != 0
            guard let state = device.makeDepthStencilState(descriptor: desc) else {
                return nil
            }
            depthStates.append(state)
        }
        let shadowDepthDesc = MTLDepthStencilDescriptor()
        shadowDepthDesc.depthCompareFunction = .lessEqual
        shadowDepthDesc.isDepthWriteEnabled = true
        guard let shadowDepth = device.makeDepthStencilState(descriptor: shadowDepthDesc) else {
            return nil
        }
        return Pipelines(mesh: mesh, meshBlend: meshBlend, points: points, pointsBlend: pointsBlend, shadow: shadow, depthStates: depthStates, shadowDepth: shadowDepth)
    }
}

public struct HTML3DArray {
    public enum Kind {
        case float32
        case uint16
        case uint32
        case other
    }

    public let pointer: UnsafeRawPointer
    public let length: Int
    public let kind: Kind

    public init(pointer: UnsafeRawPointer, length: Int, kind: Kind) {
        self.pointer = pointer
        self.length = length
        self.kind = kind
    }
}

public struct HTML3DLimits {
    public var maxGeometryBytes = 64 << 20
    public var maxDraws = 4096
    public var maxVertexWork = 6_000_000
    public var maxDrawablePixels = 4096 * 4096
    public var maxShadowMapSize = 2048
    public var maxPixelRatio: Float = 4
    public var slowFrameMilliseconds = 40.0
    public var slowFramesToStop = 90
    public var hungFrameMilliseconds = 400.0

    public init() {}
}

public final class HTML3DSession {
    private struct Geometry {
        let positions: MTLBuffer
        let normals: MTLBuffer?
        let colors: MTLBuffer?
        let colorSize: UInt32
        let index: MTLBuffer?
        let indexType: MTLIndexType
        let vertexCount: Int
        let indexCount: Int
        let bounds: SIMD4<Float>

        var bytes: Int {
            return positions.length + (normals?.length ?? 0) + (colors?.length ?? 0) + (index?.length ?? 0)
        }
    }

    private struct InstanceSet {
        let matrices: MTLBuffer
        let colors: MTLBuffer?
        let count: Int
    }

    private struct InstanceSlots {
        var matrices: [MTLBuffer?] = Array(repeating: nil, count: 5)
        var colors: [MTLBuffer?] = Array(repeating: nil, count: 5)
        var next = 0
    }

    private struct BatchKey: Hashable {
        let geometry: Int
        let material: Int
        let flags: Int
        let visibility: Int
        let start: Int
        let count: Int
    }

    private struct Batch {
        var geometry: Geometry
        var draw: HTML3DDrawUniforms
        var primitive: Int
        var materialFlags: Int
        var castShadow: Bool
        var start: Int
        var count: Int
        var matrices: [simd_float4x4]
        var instanceSet: InstanceSet?
        var instanceCount: Int
        var instanceOffset: Int
        var depth: Float
        var main: Bool
        var shadow: Bool
    }

    private static let maxFloats = 4 << 20

    public let isSupported: Bool
    public let limits: HTML3DLimits
    public var onStop: ((String) -> Void)?
    private let gpu: HTML3DGPU?
    private let lock = NSLock()
    private var geometries: [Int: Geometry] = [:]
    private var instanceSets: [Int: InstanceSet] = [:]
    private var instanceSlots: [Int: InstanceSlots] = [:]
    private var geometryBytes = 0
    private var instanceBytes = 0
    private var statFrames = 0
    private var statSubmitted = 0
    private var statGPUSeconds: Double = 0
    private var statCPUSeconds: Double = 0
    private var statDraws = 0
    private var statTriangles = 0
    private var statMemory = 0
    private var statRejected = 0
    private var statClipped = 0
    private var slowFrames = 0
    private var stopped: String?
    private var paused = false
    private var pending: [Float] = []
    private var pendingCount = 0
    private var hasPending = false
    private var renderScheduled = false
    private var layer: CAMetalLayer?
    private var viewportSize: CGSize = .zero
    private var viewportScale: CGFloat = 2
    private var radius: CGFloat = 0

    private let renderQueue = DispatchQueue(label: "HTML3D", qos: .userInteractive)
    private let inFlight = DispatchSemaphore(value: 3)
    private var working: [Float] = []
    private var ring: [MTLBuffer?] = [nil, nil, nil]
    private var ringIndex = 0
    private var colorTarget: MTLTexture?
    private var depthTarget: MTLTexture?
    private var shadowMap: MTLTexture?
    private var targetBytes = 0
    private var queue: MTLCommandQueue? {
        return gpu?.commandQueue
    }

    public static var isAvailable: Bool {
        return HTML3DGPU.shared != nil
    }

    public init(limits: HTML3DLimits = HTML3DLimits()) {
        gpu = HTML3DGPU.shared
        isSupported = gpu != nil
        self.limits = limits
        if let gpu = gpu {
            renderQueue.async {
                _ = gpu.pipelines
            }
        }
    }

    deinit {
        gpu?.unreserve(geometryBytes + instanceBytes + targetBytes)
    }

    public var cornerRadius: CGFloat {
        lock.lock()
        defer { lock.unlock() }
        return radius
    }

    public var stopReason: String? {
        lock.lock()
        defer { lock.unlock() }
        return stopped
    }

    public var stats: HTML3DStats {
        var gpuTimed = false
        if #available(macOS 10.15, *) {
            gpuTimed = true
        }
        lock.lock()
        defer { lock.unlock() }
        return HTML3DStats(frames: statFrames, submitted: statSubmitted, gpuSeconds: statGPUSeconds, cpuSeconds: statCPUSeconds, gpuTimed: gpuTimed, draws: statDraws, triangles: statTriangles, memoryBytes: statMemory, rejected: statRejected, clipped: statClipped)
    }

    public func makeLayer() -> CAMetalLayer? {
        guard let gpu = gpu else {
            return nil
        }
        let layer = CAMetalLayer()
        layer.device = gpu.device
        layer.pixelFormat = HTML3DGPU.colorFormat
        layer.framebufferOnly = true
        layer.isOpaque = false
        layer.allowsNextDrawableTimeout = true
        layer.colorspace = CGColorSpace(name: CGColorSpace.sRGB)
        if #available(macOS 10.13.2, *) {
            layer.maximumDrawableCount = 3
        }
        layer.actions = ["bounds": NSNull(), "position": NSNull(), "frame": NSNull(), "contents": NSNull(), "cornerRadius": NSNull(), "hidden": NSNull()]
        return layer
    }

    public func drawableSize(for size: CGSize, scale: CGFloat) -> CGSize {
        var width = max(1, (size.width * scale).rounded())
        var height = max(1, (size.height * scale).rounded())
        let pixels = width * height
        let limit = CGFloat(limits.maxDrawablePixels)
        if pixels > limit {
            let factor = (limit / pixels).squareRoot()
            width = max(1, (width * factor).rounded(.down))
            height = max(1, (height * factor).rounded(.down))
        }
        return CGSize(width: min(width, 8192), height: min(height, 8192))
    }

    public func attach(_ layer: CAMetalLayer, size: CGSize, scale: CGFloat) {
        lock.lock()
        self.layer = layer
        viewportSize = size
        viewportScale = scale
        let schedule = hasPending && !renderScheduled && !paused
        if schedule {
            renderScheduled = true
        }
        lock.unlock()
        if schedule {
            renderQueue.async { [self] in
                renderLoop()
            }
        }
    }

    public func detach(_ layer: CAMetalLayer) {
        lock.lock()
        if self.layer === layer {
            self.layer = nil
        }
        lock.unlock()
    }

    public func setPaused(_ value: Bool) {
        lock.lock()
        let changed = paused != value
        paused = value
        let schedule = !value && hasPending && !renderScheduled && layer != nil
        if schedule {
            renderScheduled = true
        }
        lock.unlock()
        if changed && value {
            renderQueue.async { [self] in
                releaseTargets()
            }
        }
        if schedule {
            renderQueue.async { [self] in
                renderLoop()
            }
        }
    }

    public func stop(_ reason: String) {
        lock.lock()
        if stopped != nil {
            lock.unlock()
            return
        }
        stopped = reason
        hasPending = false
        let released = geometryBytes + instanceBytes
        geometries.removeAll()
        instanceSets.removeAll()
        instanceSlots.removeAll()
        geometryBytes = 0
        instanceBytes = 0
        let callback = onStop
        lock.unlock()
        gpu?.unreserve(released)
        renderQueue.async { [self] in
            releaseTargets()
        }
        callback?(reason)
    }

    public func viewportInfo() -> [Double] {
        lock.lock()
        defer { lock.unlock() }
        return [Double(viewportSize.width), Double(viewportSize.height), Double(viewportScale)]
    }

    public func configure(radius: Double) {
        guard radius.isFinite else {
            return
        }
        lock.lock()
        self.radius = CGFloat(max(0, min(radius, 200)))
        lock.unlock()
    }

    private static func count(_ value: Float, upTo limit: Int) -> Int {
        guard value.isFinite, value > 0 else {
            return 0
        }
        return value >= Float(limit) ? limit : Int(value)
    }

    static func maxIndex(_ bytes: HTML3DArray) -> Int {
        switch bytes.kind {
        case .uint16:
            let values = bytes.pointer.bindMemory(to: UInt16.self, capacity: bytes.length / 2)
            var top: UInt16 = 0
            for i in 0 ..< bytes.length / 2 where values[i] > top {
                top = values[i]
            }
            return Int(top)
        case .uint32:
            let values = bytes.pointer.bindMemory(to: UInt32.self, capacity: bytes.length / 4)
            var top: UInt32 = 0
            for i in 0 ..< bytes.length / 4 where values[i] > top {
                top = values[i]
            }
            return Int(top)
        default:
            return Int.max
        }
    }

    private func reject() {
        lock.lock()
        statRejected += 1
        lock.unlock()
    }

    private func makeBuffer(_ value: HTML3DArray?, expected: HTML3DArray.Kind) -> MTLBuffer? {
        guard let gpu = gpu, let bytes = value, bytes.kind == expected, bytes.length > 0 else {
            return nil
        }
        return gpu.device.makeBuffer(bytes: bytes.pointer, length: bytes.length, options: .storageModeShared)
    }

    @discardableResult
    public func defineGeometry(id: Int, positions: HTML3DArray?, normals: HTML3DArray?, colors: HTML3DArray?, colorSize: Int, index: HTML3DArray?) -> Bool {
        guard let gpu = gpu, stopReason == nil, let positions = positions, positions.kind == .float32, positions.length >= 12 else {
            reject()
            return false
        }
        let vertexCount = positions.length / 12
        if let normals = normals, normals.kind != .float32 || normals.length < vertexCount * 12 {
            reject()
            return false
        }
        let usesColors = colorSize == 3 || colorSize == 4
        if usesColors, let colors = colors, colors.kind != .float32 || colors.length < vertexCount * colorSize * 4 {
            reject()
            return false
        }
        var indexType = MTLIndexType.uint16
        var indexCount = 0
        if let bytes = index, bytes.length > 0 {
            guard bytes.kind == .uint16 || bytes.kind == .uint32, HTML3DSession.maxIndex(bytes) < vertexCount else {
                reject()
                return false
            }
            indexType = bytes.kind == .uint16 ? .uint16 : .uint32
            indexCount = bytes.length / (bytes.kind == .uint16 ? 2 : 4)
        }
        let total = vertexCount * 12 + (normals == nil ? 0 : vertexCount * 12) + (usesColors && colors != nil ? vertexCount * colorSize * 4 : 0) + indexCount * (indexType == .uint16 ? 2 : 4)
        lock.lock()
        let previous = geometries[id]?.bytes ?? 0
        let fits = geometryBytes + instanceBytes - previous + total <= limits.maxGeometryBytes
        lock.unlock()
        guard fits, gpu.reserve(total) else {
            reject()
            return false
        }
        guard let position = gpu.device.makeBuffer(bytes: positions.pointer, length: vertexCount * 12, options: .storageModeShared) else {
            gpu.unreserve(total)
            return false
        }
        let normal = normals.flatMap { gpu.device.makeBuffer(bytes: $0.pointer, length: vertexCount * 12, options: .storageModeShared) }
        let color = usesColors ? colors.flatMap { gpu.device.makeBuffer(bytes: $0.pointer, length: vertexCount * colorSize * 4, options: .storageModeShared) } : nil
        let indexBuffer = indexCount > 0 ? index.flatMap { gpu.device.makeBuffer(bytes: $0.pointer, length: indexCount * (indexType == .uint16 ? 2 : 4), options: .storageModeShared) } : nil
        let geometry = Geometry(positions: position, normals: normal, colors: color, colorSize: color == nil ? 0 : UInt32(colorSize), index: indexBuffer, indexType: indexType, vertexCount: vertexCount, indexCount: indexBuffer == nil ? 0 : indexCount, bounds: HTML3DSession.boundingSphere(positions.pointer, vertexCount))
        lock.lock()
        if stopped != nil {
            lock.unlock()
            gpu.unreserve(total)
            return false
        }
        let replaced = geometries[id]?.bytes ?? 0
        geometries[id] = geometry
        geometryBytes += geometry.bytes - replaced
        lock.unlock()
        gpu.unreserve(replaced + total - geometry.bytes)
        return true
    }

    public func releaseGeometry(id: Int) {
        lock.lock()
        var released = 0
        if let geometry = geometries.removeValue(forKey: id) {
            released = geometry.bytes
            geometryBytes -= released
        }
        lock.unlock()
        gpu?.unreserve(released)
    }

    @discardableResult
    public func defineInstances(id: Int, matrices: HTML3DArray?, count: Int, colors: HTML3DArray?) -> Bool {
        guard let gpu = gpu, let source = matrices, source.kind == .float32, source.length >= 64 else {
            reject()
            return false
        }
        let instanceCount = max(0, min(count, source.length / 64))
        if instanceCount == 0 {
            reject()
            return false
        }
        var colorSource = colors
        if let bytes = colorSource, bytes.kind != .float32 || bytes.length < instanceCount * 12 {
            colorSource = nil
        }
        lock.lock()
        defer { lock.unlock() }
        if stopped != nil {
            return false
        }
        var slots = instanceSlots[id] ?? InstanceSlots()
        let slot = slots.next
        slots.next = (slot + 1) % slots.matrices.count
        func fill(_ buffer: MTLBuffer?, _ pointer: UnsafeRawPointer, _ length: Int) -> MTLBuffer? {
            var target = buffer
            if target == nil || target!.length < length {
                let size = length + length / 2
                let added = size - (buffer?.length ?? 0)
                guard geometryBytes + instanceBytes + added <= limits.maxGeometryBytes, gpu.reserve(added) else {
                    return nil
                }
                guard let created = gpu.device.makeBuffer(length: size, options: .storageModeShared) else {
                    gpu.unreserve(added)
                    return nil
                }
                instanceBytes += added
                target = created
            }
            target?.contents().copyMemory(from: pointer, byteCount: length)
            return target
        }
        guard let matrixBuffer = fill(slots.matrices[slot], source.pointer, instanceCount * 64) else {
            statRejected += 1
            return false
        }
        slots.matrices[slot] = matrixBuffer
        var colorBuffer: MTLBuffer?
        if let colorSource = colorSource {
            colorBuffer = fill(slots.colors[slot], colorSource.pointer, instanceCount * 12)
            slots.colors[slot] = colorBuffer
        }
        instanceSlots[id] = slots
        instanceSets[id] = InstanceSet(matrices: matrixBuffer, colors: colorBuffer, count: instanceCount)
        return true
    }

    public func releaseInstances(id: Int) {
        lock.lock()
        instanceSets.removeValue(forKey: id)
        var released = 0
        if let slots = instanceSlots.removeValue(forKey: id) {
            released = (slots.matrices + slots.colors).reduce(0) { $0 + ($1?.length ?? 0) }
            instanceBytes -= released
        }
        lock.unlock()
        gpu?.unreserve(released)
    }

    public func submit(data: HTML3DArray?, count: Int) {
        guard isSupported, let bytes = data, bytes.kind == .float32 else {
            return
        }
        let floats = min(count, bytes.length / 4, HTML3DSession.maxFloats)
        if floats < HTML3DLayout.header {
            return
        }
        lock.lock()
        if stopped != nil {
            lock.unlock()
            return
        }
        if pending.count < floats {
            pending = [Float](repeating: 0, count: max(floats, pending.count * 2))
        }
        pending.withUnsafeMutableBytes { target in
            target.baseAddress!.copyMemory(from: bytes.pointer, byteCount: floats * 4)
        }
        pendingCount = floats
        hasPending = true
        statSubmitted += 1
        let schedule = !renderScheduled && layer != nil && !paused
        if schedule {
            renderScheduled = true
        }
        lock.unlock()
        if schedule {
            renderQueue.async { [self] in
                renderLoop()
            }
        }
    }

    public func renderOffscreen(width: Int, height: Int) -> [UInt8]? {
        guard let gpu = gpu, width > 0, height > 0, width * height <= limits.maxDrawablePixels else {
            return nil
        }
        return renderQueue.sync { () -> [UInt8]? in
            lock.lock()
            guard hasPending, stopped == nil else {
                lock.unlock()
                return nil
            }
            swap(&pending, &working)
            let count = pendingCount
            hasPending = false
            let geometries = self.geometries
            let instanceSets = self.instanceSets
            lock.unlock()
            let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: HTML3DGPU.colorFormat, width: width, height: height, mipmapped: false)
            desc.usage = [.renderTarget]
            desc.storageMode = .private
            guard let texture = gpu.device.makeTexture(descriptor: desc), let readback = gpu.device.makeBuffer(length: width * height * 4, options: .storageModeShared), let commandBuffer = queue?.makeCommandBuffer() else {
                return nil
            }
            inFlight.wait()
            let encoded = encode(count: count, target: texture, commandBuffer: commandBuffer, geometries: geometries, instanceSets: instanceSets)
            if !encoded {
                inFlight.signal()
                return nil
            }
            if let blit = commandBuffer.makeBlitCommandEncoder() {
                blit.copy(from: texture, sourceSlice: 0, sourceLevel: 0, sourceOrigin: MTLOrigin(x: 0, y: 0, z: 0), sourceSize: MTLSize(width: width, height: height, depth: 1), to: readback, destinationOffset: 0, destinationBytesPerRow: width * 4, destinationBytesPerImage: width * height * 4)
                blit.endEncoding()
            }
            commandBuffer.commit()
            commandBuffer.waitUntilCompleted()
            let pointer = readback.contents().bindMemory(to: UInt8.self, capacity: width * height * 4)
            return Array(UnsafeBufferPointer(start: pointer, count: width * height * 4))
        }
    }

    private func renderLoop() {
        while true {
            lock.lock()
            guard hasPending, stopped == nil, !paused, let layer = layer else {
                renderScheduled = false
                lock.unlock()
                return
            }
            swap(&pending, &working)
            let count = pendingCount
            hasPending = false
            let geometries = self.geometries
            let instanceSets = self.instanceSets
            lock.unlock()
            inFlight.wait()
            let committed = autoreleasepool { () -> Bool in
                guard gpu != nil, let drawable = layer.nextDrawable(), let commandBuffer = queue?.makeCommandBuffer() else {
                    return false
                }
                guard drawable.texture.width * drawable.texture.height <= limits.maxDrawablePixels else {
                    return false
                }
                guard encode(count: count, target: drawable.texture, commandBuffer: commandBuffer, geometries: geometries, instanceSets: instanceSets) else {
                    return false
                }
                commandBuffer.present(drawable)
                commandBuffer.commit()
                return true
            }
            if !committed {
                inFlight.signal()
            }
        }
    }

    private func encode(count: Int, target: MTLTexture, commandBuffer: MTLCommandBuffer, geometries: [Int: Geometry], instanceSets: [Int: InstanceSet]) -> Bool {
        guard let gpu = gpu, let pipelines = gpu.pipelines else {
            return false
        }
        let cpuStart = clock_gettime_nsec_np(CLOCK_THREAD_CPUTIME_ID)
        return working.withUnsafeBufferPointer { buffer -> Bool in
            let f = buffer.baseAddress!
            var uniforms = HTML3DFrameUniforms()
            let remap = simd_float4x4(columns: (SIMD4<Float>(1, 0, 0, 0), SIMD4<Float>(0, 1, 0, 0), SIMD4<Float>(0, 0, 0.5, 0), SIMD4<Float>(0, 0, 0.5, 1)))
            uniforms.view = HTML3DSession.matrix(f + HTML3DLayout.view)
            uniforms.projection = remap * HTML3DSession.matrix(f + HTML3DLayout.projection)
            uniforms.shadowViewProjection = remap * HTML3DSession.matrix(f + HTML3DLayout.shadowViewProjection)
            uniforms.cameraPosition = HTML3DSession.vector(f + HTML3DLayout.camera, 1)
            uniforms.ambient = HTML3DSession.vector(f + HTML3DLayout.ambient, 0)
            uniforms.hemiSky = HTML3DSession.vector(f + HTML3DLayout.hemiSky, f[HTML3DLayout.hemiSky + 3])
            uniforms.hemiGround = HTML3DSession.vector(f + HTML3DLayout.hemiGround, 0)
            uniforms.hemiDirection = HTML3DSession.vector(f + HTML3DLayout.hemiDirection, 0)
            uniforms.directionalColor = HTML3DSession.vector(f + HTML3DLayout.directionalColor, f[HTML3DLayout.directionalColor + 3])
            uniforms.directionalDirection = HTML3DSession.vector(f + HTML3DLayout.directionalDirection, 0)
            uniforms.shadowParams = SIMD4<Float>(f[HTML3DLayout.shadow], f[HTML3DLayout.shadow + 1], f[HTML3DLayout.shadow + 2], f[HTML3DLayout.shadow + 3])
            uniforms.fogColor = SIMD4<Float>(f[HTML3DLayout.fog], f[HTML3DLayout.fog + 1], f[HTML3DLayout.fog + 2], f[HTML3DLayout.fog + 3])
            uniforms.fogParams = SIMD4<Float>(f[HTML3DLayout.fogParams], f[HTML3DLayout.fogParams + 1], f[HTML3DLayout.fogParams + 2], 0)
            let lightCount = HTML3DSession.count(f[HTML3DLayout.lightCount], upTo: HTML3DLayout.maxLights)
            var positions = [SIMD4<Float>](repeating: .init(repeating: 0), count: 4)
            var colors = positions, directions = positions, cones = positions
            for i in 0 ..< lightCount {
                let l = f + HTML3DLayout.lights + i * 16
                positions[i] = SIMD4<Float>(l[0], l[1], l[2], l[3])
                colors[i] = SIMD4<Float>(l[4], l[5], l[6], l[7])
                directions[i] = SIMD4<Float>(l[8], l[9], l[10], l[11])
                cones[i] = SIMD4<Float>(l[12], l[13], 0, 0)
            }
            uniforms.lightPosition = (positions[0], positions[1], positions[2], positions[3])
            uniforms.lightColor = (colors[0], colors[1], colors[2], colors[3])
            uniforms.lightDirection = (directions[0], directions[1], directions[2], directions[3])
            uniforms.lightCone = (cones[0], cones[1], cones[2], cones[3])
            uniforms.counts = SIMD4<Int32>(Int32(lightCount), 0, 0, 0)

            let recordCount = HTML3DSession.count(f[HTML3DLayout.recordCount], upTo: (count - HTML3DLayout.header) / HTML3DLayout.record)
            var batches: [Batch] = []
            var opaqueIndex: [BatchKey: Int] = [:]
            var instanceTotal = 0
            var work = 0
            var clipped = 0
            let view = uniforms.view
            let shadowsActive = uniforms.shadowParams.w > 0.5 && uniforms.directionalColor.w > 0.5
            let viewPlanes = HTML3DSession.planes(HTML3DSession.matrix(f + HTML3DLayout.projection) * view)
            let shadowPlanes = HTML3DSession.planes(HTML3DSession.matrix(f + HTML3DLayout.shadowViewProjection))
            for r in 0 ..< recordCount {
                let p = f + HTML3DLayout.header + r * HTML3DLayout.record
                let geometryId = HTML3DSession.count(p[HTML3DLayout.recordGeometry], upTo: Int(Int32.max))
                guard let geometry = geometries[geometryId] else {
                    continue
                }
                let flags = HTML3DSession.count(p[HTML3DLayout.recordFlags], upTo: 0xFFFF)
                let materialFlags = HTML3DSession.count(p[HTML3DLayout.recordParams + 2], upTo: 0xFFFF)
                let primitive = (flags >> 4) & 3
                let available = geometry.index != nil ? geometry.indexCount : geometry.vertexCount
                let start = HTML3DSession.count(p[HTML3DLayout.recordStart], upTo: available)
                let drawCount = min(HTML3DSession.count(p[HTML3DLayout.recordCount_], upTo: available), available - start)
                if drawCount <= 0 {
                    continue
                }
                let matrix = HTML3DSession.matrix(p + HTML3DLayout.recordMatrix)
                let instanceId = HTML3DSession.count(p[HTML3DLayout.recordInstances], upTo: Int(Int32.max))
                let transparent = materialFlags & 1 != 0
                let receive = flags & 2 != 0
                var shaderFlags = UInt32(materialFlags & 0x47)
                if receive {
                    shaderFlags |= 512
                }
                let vertexColors = materialFlags & 2 != 0
                var draw = HTML3DDrawUniforms()
                draw.color = SIMD4<Float>(p[HTML3DLayout.recordColor], p[HTML3DLayout.recordColor + 1], p[HTML3DLayout.recordColor + 2], p[HTML3DLayout.recordColor + 3])
                draw.emissive = SIMD4<Float>(p[HTML3DLayout.recordEmissive], p[HTML3DLayout.recordEmissive + 1], p[HTML3DLayout.recordEmissive + 2], p[HTML3DLayout.recordEmissive + 3])
                draw.specular = SIMD4<Float>(p[HTML3DLayout.recordSpecular], p[HTML3DLayout.recordSpecular + 1], p[HTML3DLayout.recordSpecular + 2], max(p[HTML3DLayout.recordSpecular + 3], 0.0001))
                draw.params = SIMD4<Float>(p[HTML3DLayout.recordParams], p[HTML3DLayout.recordParams + 1], Float(materialFlags), p[HTML3DLayout.recordParams + 3])
                let castShadow = flags & 1 != 0
                var main = true
                var shadow = castShadow && primitive == 0
                if flags & 64 != 0, instanceId == 0, primitive != 3 {
                    let sphere = HTML3DSession.worldSphere(geometry.bounds, matrix)
                    main = HTML3DSession.intersects(viewPlanes, sphere)
                    shadow = shadow && shadowsActive && HTML3DSession.intersects(shadowPlanes, sphere)
                    if !main && !shadow {
                        continue
                    }
                }
                let passes = (main ? 1 : 0) + (shadow ? 1 : 0)
                if instanceId > 0 {
                    guard let set = instanceSets[instanceId] else {
                        continue
                    }
                    let instanceCount = HTML3DSession.count(p[HTML3DLayout.recordInstanceCount], upTo: set.count)
                    if instanceCount == 0 {
                        continue
                    }
                    let cost = drawCount * instanceCount * passes
                    if batches.count >= limits.maxDraws || work + cost > limits.maxVertexWork {
                        clipped += 1
                        continue
                    }
                    work += cost
                    draw.base = matrix
                    draw.layout = SIMD4<UInt32>(geometry.normals != nil ? 1 : 0, vertexColors ? geometry.colorSize : 0, set.colors != nil ? 1 : 0, shaderFlags)
                    batches.append(Batch(geometry: geometry, draw: draw, primitive: primitive, materialFlags: materialFlags, castShadow: castShadow, start: start, count: drawCount, matrices: [], instanceSet: set, instanceCount: instanceCount, instanceOffset: 0, depth: HTML3DSession.viewDepth(view, matrix), main: main, shadow: shadow))
                    continue
                }
                let cost = drawCount * passes
                if work + cost > limits.maxVertexWork {
                    clipped += 1
                    continue
                }
                draw.layout = SIMD4<UInt32>(geometry.normals != nil ? 1 : 0, vertexColors ? geometry.colorSize : 0, 0, shaderFlags)
                if transparent {
                    if batches.count >= limits.maxDraws {
                        clipped += 1
                        continue
                    }
                    work += cost
                    instanceTotal += 1
                    batches.append(Batch(geometry: geometry, draw: draw, primitive: primitive, materialFlags: materialFlags, castShadow: castShadow, start: start, count: drawCount, matrices: [matrix], instanceSet: nil, instanceCount: 1, instanceOffset: 0, depth: HTML3DSession.viewDepth(view, matrix), main: main, shadow: shadow))
                    continue
                }
                let key = BatchKey(geometry: geometryId, material: HTML3DSession.count(p[HTML3DLayout.recordMaterial], upTo: Int(Int32.max)), flags: flags, visibility: (main ? 1 : 0) | (shadow ? 2 : 0), start: start, count: drawCount)
                if let index = opaqueIndex[key] {
                    work += cost
                    instanceTotal += 1
                    batches[index].matrices.append(matrix)
                    batches[index].instanceCount += 1
                } else {
                    if batches.count >= limits.maxDraws {
                        clipped += 1
                        continue
                    }
                    work += cost
                    instanceTotal += 1
                    opaqueIndex[key] = batches.count
                    batches.append(Batch(geometry: geometry, draw: draw, primitive: primitive, materialFlags: materialFlags, castShadow: castShadow, start: start, count: drawCount, matrices: [matrix], instanceSet: nil, instanceCount: 1, instanceOffset: 0, depth: 0, main: main, shadow: shadow))
                }
            }

            let ringBytes = max(instanceTotal, 1) * 64
            ringIndex = (ringIndex + 1) % ring.count
            if ring[ringIndex] == nil || ring[ringIndex]!.length < ringBytes {
                ring[ringIndex] = gpu.device.makeBuffer(length: max(ringBytes, (ring[ringIndex]?.length ?? 0) * 2, 64 * 256), options: .storageModeShared)
            }
            guard let instanceBuffer = ring[ringIndex] else {
                return false
            }
            var offset = 0
            let targetBytes = instanceBuffer.contents()
            for index in batches.indices where batches[index].instanceSet == nil {
                batches[index].instanceOffset = offset
                batches[index].matrices.withUnsafeBytes { bytes in
                    (targetBytes + offset).copyMemory(from: bytes.baseAddress!, byteCount: bytes.count)
                }
                offset += batches[index].matrices.count * 64
            }

            let width = target.width
            let height = target.height
            let ratio = min(max(f[HTML3DLayout.pixelRatio], 1), limits.maxPixelRatio)
            uniforms.viewport = SIMD4<Float>(Float(width), Float(height), ratio.isFinite ? ratio : 1, 0)
            guard ensureTargets(gpu: gpu, width: width, height: height) else {
                return false
            }

            let shadowsOn = shadowsActive && batches.contains(where: { $0.shadow && $0.primitive == 0 })
            var shadowTexture = gpu.dummyDepth
            if shadowsOn, let map = ensureShadowMap(gpu: gpu, size: HTML3DSession.count(f[HTML3DLayout.shadowMapSize], upTo: 4096)) {
                shadowTexture = map
                uniforms.shadowSize = SIMD4<Float>(Float(map.width), 1 / Float(map.width), 0, 0)
                let pass = MTLRenderPassDescriptor()
                pass.depthAttachment.texture = map
                pass.depthAttachment.loadAction = .clear
                pass.depthAttachment.storeAction = .store
                pass.depthAttachment.clearDepth = 1
                if let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: pass) {
                    encoder.setRenderPipelineState(pipelines.shadow)
                    encoder.setDepthStencilState(pipelines.shadowDepth)
                    encoder.setFrontFacing(.counterClockwise)
                    encoder.setDepthBias(0, slopeScale: 1.5, clamp: 0.02)
                    encoder.setVertexBytes(&uniforms, length: MemoryLayout<HTML3DFrameUniforms>.stride, index: 6)
                    for batch in batches where batch.shadow && batch.primitive == 0 {
                        var draw = batch.draw
                        let side = (batch.materialFlags >> 4) & 3
                        encoder.setCullMode(side == 0 ? .front : side == 1 ? .back : .none)
                        encoder.setVertexBuffer(batch.geometry.positions, offset: 0, index: 0)
                        if let set = batch.instanceSet {
                            encoder.setVertexBuffer(set.matrices, offset: 0, index: 3)
                        } else {
                            encoder.setVertexBuffer(instanceBuffer, offset: batch.instanceOffset, index: 3)
                        }
                        encoder.setVertexBytes(&draw, length: MemoryLayout<HTML3DDrawUniforms>.stride, index: 5)
                        HTML3DSession.draw(encoder, batch: batch, type: .triangle)
                    }
                    encoder.endEncoding()
                }
            } else {
                uniforms.shadowParams.w = 0
            }

            let pass = MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture = colorTarget
            pass.colorAttachments[0].resolveTexture = target
            pass.colorAttachments[0].loadAction = .clear
            pass.colorAttachments[0].storeAction = .multisampleResolve
            let clear = SIMD4<Float>(f[HTML3DLayout.clear], f[HTML3DLayout.clear + 1], f[HTML3DLayout.clear + 2], f[HTML3DLayout.clear + 3]).clamped(lowerBound: SIMD4<Float>(repeating: 0), upperBound: SIMD4<Float>(repeating: 1))
            let safeClear = SIMD4<Float>(clear.x.isNaN ? 0 : clear.x, clear.y.isNaN ? 0 : clear.y, clear.z.isNaN ? 0 : clear.z, clear.w.isNaN ? 0 : clear.w)
            pass.colorAttachments[0].clearColor = MTLClearColor(red: Double(safeClear.x * safeClear.w), green: Double(safeClear.y * safeClear.w), blue: Double(safeClear.z * safeClear.w), alpha: Double(safeClear.w))
            pass.depthAttachment.texture = depthTarget
            pass.depthAttachment.loadAction = .clear
            pass.depthAttachment.storeAction = .dontCare
            pass.depthAttachment.clearDepth = 1
            guard let encoder = commandBuffer.makeRenderCommandEncoder(descriptor: pass) else {
                return false
            }
            encoder.setFrontFacing(.counterClockwise)
            encoder.setVertexBytes(&uniforms, length: MemoryLayout<HTML3DFrameUniforms>.stride, index: 6)
            encoder.setFragmentBytes(&uniforms, length: MemoryLayout<HTML3DFrameUniforms>.stride, index: 1)
            encoder.setFragmentTexture(shadowTexture, index: 0)
            encoder.setFragmentSamplerState(gpu.shadowSampler, index: 0)
            let opaque = batches.filter { $0.main && $0.materialFlags & 1 == 0 }
            let transparent = batches.filter { $0.main && $0.materialFlags & 1 != 0 }.sorted { $0.depth > $1.depth }
            var triangles = 0
            for batch in opaque + transparent {
                if batch.primitive == 0 {
                    triangles += batch.count / 3 * batch.instanceCount
                }
                let blend = batch.materialFlags & 1 != 0
                let points = batch.primitive == 3
                encoder.setRenderPipelineState(points ? (blend ? pipelines.pointsBlend : pipelines.points) : (blend ? pipelines.meshBlend : pipelines.mesh))
                let depthTest = batch.materialFlags & 256 != 0
                let depthWrite = batch.materialFlags & 128 != 0
                encoder.setDepthStencilState(pipelines.depthStates[(depthTest ? 1 : 0) | (depthWrite ? 2 : 0)])
                let side = (batch.materialFlags >> 4) & 3
                encoder.setCullMode(batch.primitive != 0 ? .none : side == 0 ? .back : side == 1 ? .front : .none)
                encoder.setTriangleFillMode(batch.materialFlags & 8 != 0 ? .lines : .fill)
                encoder.setVertexBuffer(batch.geometry.positions, offset: 0, index: 0)
                encoder.setVertexBuffer(batch.geometry.normals ?? gpu.dummyBuffer, offset: 0, index: 1)
                encoder.setVertexBuffer(batch.geometry.colors ?? gpu.dummyBuffer, offset: 0, index: 2)
                if let set = batch.instanceSet {
                    encoder.setVertexBuffer(set.matrices, offset: 0, index: 3)
                    encoder.setVertexBuffer(set.colors ?? gpu.dummyBuffer, offset: 0, index: 4)
                } else {
                    encoder.setVertexBuffer(instanceBuffer, offset: batch.instanceOffset, index: 3)
                    encoder.setVertexBuffer(gpu.dummyBuffer, offset: 0, index: 4)
                }
                var draw = batch.draw
                encoder.setVertexBytes(&draw, length: MemoryLayout<HTML3DDrawUniforms>.stride, index: 5)
                encoder.setFragmentBytes(&draw, length: MemoryLayout<HTML3DDrawUniforms>.stride, index: 0)
                let type: MTLPrimitiveType = batch.primitive == 1 ? .line : batch.primitive == 2 ? .lineStrip : points ? .point : .triangle
                HTML3DSession.draw(encoder, batch: batch, type: type)
            }
            encoder.endEncoding()
            let semaphore = inFlight
            commandBuffer.addCompletedHandler { [weak self] buffer in
                var seconds: Double = 0
                if #available(macOS 10.15, *) {
                    seconds = max(0, buffer.gpuEndTime - buffer.gpuStartTime)
                }
                self?.recordCompleted(gpuSeconds: seconds, failed: buffer.status == .error)
                semaphore.signal()
            }
            var memory = ring.reduce(0) { $0 + ($1?.length ?? 0) } + width * height * 4 * 3
            memory += (colorTarget?.allocatedSize ?? 0) + (depthTarget?.allocatedSize ?? 0) + (shadowMap?.allocatedSize ?? 0)
            let cpuSeconds = Double(clock_gettime_nsec_np(CLOCK_THREAD_CPUTIME_ID) - cpuStart) / 1_000_000_000
            lock.lock()
            statCPUSeconds += cpuSeconds
            statDraws = opaque.count + transparent.count
            statTriangles = triangles
            statClipped += clipped
            statMemory = memory + geometryBytes + instanceBytes
            lock.unlock()
            return true
        }
    }

    func recordCompleted(gpuSeconds: Double, failed: Bool) {
        let milliseconds = gpuSeconds * 1000
        var reason: String?
        lock.lock()
        statFrames += 1
        statGPUSeconds += gpuSeconds
        if failed {
            reason = "the GPU rejected a frame"
        } else if milliseconds > limits.hungFrameMilliseconds {
            reason = String(format: "one frame took %.0f ms of GPU time", milliseconds)
        } else if milliseconds > limits.slowFrameMilliseconds {
            slowFrames += 1
            if slowFrames >= limits.slowFramesToStop {
                reason = String(format: "%d frames in a row took over %.0f ms of GPU time", slowFrames, limits.slowFrameMilliseconds)
            }
        } else {
            slowFrames = 0
        }
        lock.unlock()
        if let reason = reason {
            stop(reason)
        }
    }

    private static func draw(_ encoder: MTLRenderCommandEncoder, batch: Batch, type: MTLPrimitiveType) {
        if let index = batch.geometry.index {
            let size = batch.geometry.indexType == .uint16 ? 2 : 4
            encoder.drawIndexedPrimitives(type: type, indexCount: batch.count, indexType: batch.geometry.indexType, indexBuffer: index, indexBufferOffset: batch.start * size, instanceCount: batch.instanceCount)
        } else {
            encoder.drawPrimitives(type: type, vertexStart: batch.start, vertexCount: batch.count, instanceCount: batch.instanceCount)
        }
    }

    private func releaseTargets() {
        colorTarget = nil
        depthTarget = nil
        shadowMap = nil
        gpu?.unreserve(targetBytes)
        targetBytes = 0
    }

    private func reserveTargets(_ bytes: Int) -> Bool {
        guard let gpu = gpu else {
            return false
        }
        if bytes > targetBytes {
            if !gpu.reserve(bytes - targetBytes) {
                return false
            }
        } else {
            gpu.unreserve(targetBytes - bytes)
        }
        targetBytes = bytes
        return true
    }

    private func targetCost(gpu: HTML3DGPU, width: Int, height: Int, shadow: Int) -> Int {
        let multisampled = gpu.memorylessTargets ? 0 : width * height * 4 * HTML3DGPU.sampleCount * 2
        return multisampled + shadow * shadow * 4
    }

    private func ensureTargets(gpu: HTML3DGPU, width: Int, height: Int) -> Bool {
        if let color = colorTarget, color.width == width, color.height == height, depthTarget != nil {
            return true
        }
        guard reserveTargets(targetCost(gpu: gpu, width: width, height: height, shadow: shadowMap?.width ?? 0)) else {
            colorTarget = nil
            depthTarget = nil
            return false
        }
        let colorDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: HTML3DGPU.colorFormat, width: width, height: height, mipmapped: false)
        colorDesc.textureType = .type2DMultisample
        colorDesc.sampleCount = HTML3DGPU.sampleCount
        colorDesc.usage = .renderTarget
        let depthDesc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: HTML3DGPU.depthFormat, width: width, height: height, mipmapped: false)
        depthDesc.textureType = .type2DMultisample
        depthDesc.sampleCount = HTML3DGPU.sampleCount
        depthDesc.usage = .renderTarget
        if gpu.memorylessTargets, #available(macOS 11.0, *) {
            colorDesc.storageMode = .memoryless
            depthDesc.storageMode = .memoryless
        } else {
            colorDesc.storageMode = .private
            depthDesc.storageMode = .private
        }
        colorTarget = gpu.device.makeTexture(descriptor: colorDesc)
        depthTarget = gpu.device.makeTexture(descriptor: depthDesc)
        return colorTarget != nil && depthTarget != nil
    }

    private func ensureShadowMap(gpu: HTML3DGPU, size requested: Int) -> MTLTexture? {
        let size = max(256, min(requested > 0 ? requested : 1024, limits.maxShadowMapSize))
        if let map = shadowMap, map.width == size {
            return map
        }
        guard reserveTargets(targetCost(gpu: gpu, width: colorTarget?.width ?? 0, height: colorTarget?.height ?? 0, shadow: size)) else {
            shadowMap = nil
            return nil
        }
        let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: HTML3DGPU.depthFormat, width: size, height: size, mipmapped: false)
        desc.usage = [.renderTarget, .shaderRead]
        desc.storageMode = .private
        shadowMap = gpu.device.makeTexture(descriptor: desc)
        return shadowMap
    }

    private static func matrix(_ p: UnsafePointer<Float>) -> simd_float4x4 {
        return simd_float4x4(columns: (SIMD4<Float>(p[0], p[1], p[2], p[3]), SIMD4<Float>(p[4], p[5], p[6], p[7]), SIMD4<Float>(p[8], p[9], p[10], p[11]), SIMD4<Float>(p[12], p[13], p[14], p[15])))
    }

    private static func vector(_ p: UnsafePointer<Float>, _ w: Float) -> SIMD4<Float> {
        return SIMD4<Float>(p[0], p[1], p[2], w)
    }

    static func boundingSphere(_ pointer: UnsafeRawPointer, _ count: Int) -> SIMD4<Float> {
        let p = pointer.bindMemory(to: Float.self, capacity: count * 3)
        var low = SIMD3<Float>(repeating: .greatestFiniteMagnitude)
        var high = SIMD3<Float>(repeating: -.greatestFiniteMagnitude)
        for i in 0 ..< count {
            let v = SIMD3<Float>(p[i * 3], p[i * 3 + 1], p[i * 3 + 2])
            guard v.x.isFinite, v.y.isFinite, v.z.isFinite else {
                return SIMD4<Float>(0, 0, 0, .infinity)
            }
            low = simd_min(low, v)
            high = simd_max(high, v)
        }
        let center = (low + high) * 0.5
        var radius: Float = 0
        for i in 0 ..< count {
            radius = max(radius, simd_distance_squared(SIMD3<Float>(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]), center))
        }
        return SIMD4<Float>(center, radius.squareRoot() * 1.0001 + 1e-5)
    }

    static func planes(_ m: simd_float4x4) -> [SIMD4<Float>] {
        let r0 = SIMD4<Float>(m.columns.0.x, m.columns.1.x, m.columns.2.x, m.columns.3.x)
        let r1 = SIMD4<Float>(m.columns.0.y, m.columns.1.y, m.columns.2.y, m.columns.3.y)
        let r2 = SIMD4<Float>(m.columns.0.z, m.columns.1.z, m.columns.2.z, m.columns.3.z)
        let r3 = SIMD4<Float>(m.columns.0.w, m.columns.1.w, m.columns.2.w, m.columns.3.w)
        return [r3 + r0, r3 - r0, r3 + r1, r3 - r1, r3 + r2, r3 - r2]
    }

    static func worldSphere(_ bounds: SIMD4<Float>, _ m: simd_float4x4) -> SIMD4<Float> {
        let center = m * SIMD4<Float>(bounds.x, bounds.y, bounds.z, 1)
        let scale = max(simd_length(SIMD3<Float>(m.columns.0.x, m.columns.0.y, m.columns.0.z)), simd_length(SIMD3<Float>(m.columns.1.x, m.columns.1.y, m.columns.1.z)), simd_length(SIMD3<Float>(m.columns.2.x, m.columns.2.y, m.columns.2.z)))
        return SIMD4<Float>(center.x, center.y, center.z, bounds.w * scale)
    }

    static func intersects(_ planes: [SIMD4<Float>], _ sphere: SIMD4<Float>) -> Bool {
        let center = SIMD4<Float>(sphere.x, sphere.y, sphere.z, 1)
        for plane in planes {
            let length = simd_length(SIMD3<Float>(plane.x, plane.y, plane.z))
            if simd_dot(plane, center) < -sphere.w * length {
                return false
            }
        }
        return true
    }

    private static func viewDepth(_ view: simd_float4x4, _ model: simd_float4x4) -> Float {
        let position = view * model.columns.3
        return -position.z
    }
}
