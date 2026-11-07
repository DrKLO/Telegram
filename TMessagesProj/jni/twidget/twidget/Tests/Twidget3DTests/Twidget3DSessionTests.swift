import XCTest
import Metal
import simd
@testable import Twidget3D

final class Twidget3DSessionTests: XCTestCase {
    private let size = 64

    override func setUpWithError() throws {
        try XCTSkipUnless(HTML3DSession.isAvailable, "Metal is not available")
    }

    private func header(clear: SIMD4<Float> = SIMD4(0, 0, 0, 1), records: Int = 0) -> [Float] {
        var f = [Float](repeating: 0, count: HTML3DLayout.header + records * HTML3DLayout.record)
        for i in 0 ..< 4 {
            f[HTML3DLayout.view + i * 5] = 1
            f[HTML3DLayout.projection + i * 5] = 1
        }
        f[HTML3DLayout.clear] = clear.x
        f[HTML3DLayout.clear + 1] = clear.y
        f[HTML3DLayout.clear + 2] = clear.z
        f[HTML3DLayout.clear + 3] = clear.w
        f[HTML3DLayout.recordCount] = Float(records)
        f[HTML3DLayout.pixelRatio] = 1
        return f
    }

    private func record(_ f: inout [Float], _ index: Int, geometry: Int, count: Int, color: SIMD4<Float> = SIMD4(0, 1, 0, 1), start: Int = 0, instances: Int = 0, instanceCount: Int = 1, flags: Int = 0) {
        let o = HTML3DLayout.header + index * HTML3DLayout.record
        f[o + HTML3DLayout.recordGeometry] = Float(geometry)
        f[o + HTML3DLayout.recordMaterial] = Float(geometry)
        f[o + HTML3DLayout.recordStart] = Float(start)
        f[o + HTML3DLayout.recordCount_] = Float(count)
        f[o + HTML3DLayout.recordFlags] = Float(flags)
        f[o + HTML3DLayout.recordInstances] = Float(instances)
        f[o + HTML3DLayout.recordInstanceCount] = Float(instanceCount)
        for i in 0 ..< 4 {
            f[o + HTML3DLayout.recordMatrix + i * 5] = 1
        }
        f[o + HTML3DLayout.recordColor] = color.x
        f[o + HTML3DLayout.recordColor + 1] = color.y
        f[o + HTML3DLayout.recordColor + 2] = color.z
        f[o + HTML3DLayout.recordColor + 3] = color.w
        f[o + HTML3DLayout.recordParams + 2] = Float(256 | 128 | (2 << 4))
        f[o + HTML3DLayout.recordParams + 3] = 1
    }

    private func submit(_ session: HTML3DSession, _ f: [Float]) {
        f.withUnsafeBytes { session.submit(data: HTML3DArray(pointer: $0.baseAddress!, length: $0.count, kind: .float32), count: f.count) }
    }

    @discardableResult
    private func define(_ session: HTML3DSession, id: Int, positions: [Float], index: [UInt16]? = nil, normals: [Float]? = nil) -> Bool {
        return positions.withUnsafeBytes { p in
            let positionArray = HTML3DArray(pointer: p.baseAddress!, length: p.count, kind: .float32)
            let normalArray = normals.map { n in n.withUnsafeBytes { HTML3DArray(pointer: UnsafeRawPointer($0.baseAddress!), length: $0.count, kind: .float32) } }
            if let index = index {
                return index.withUnsafeBytes { i in
                    session.defineGeometry(id: id, positions: positionArray, normals: normalArray, colors: nil, colorSize: 0, index: HTML3DArray(pointer: i.baseAddress!, length: i.count, kind: .uint16))
                }
            }
            return session.defineGeometry(id: id, positions: positionArray, normals: normalArray, colors: nil, colorSize: 0, index: nil)
        }
    }

    private func pixel(_ pixels: [UInt8], x: Int, y: Int) -> (r: UInt8, g: UInt8, b: UInt8, a: UInt8) {
        let o = (y * size + x) * 4
        return (pixels[o + 2], pixels[o + 1], pixels[o], pixels[o + 3])
    }

    private let fullTriangle: [Float] = [-1, -1, 0, 3, -1, 0, -1, 3, 0]

    func testClearColorFillsTheTarget() throws {
        let session = HTML3DSession()
        submit(session, header(clear: SIMD4(1, 0, 0, 1)))
        let pixels = try XCTUnwrap(session.renderOffscreen(width: size, height: size))
        let center = pixel(pixels, x: size / 2, y: size / 2)
        XCTAssertEqual(center.r, 255)
        XCTAssertEqual(center.g, 0)
        XCTAssertEqual(center.a, 255)
    }

    func testDrawsAValidatedTriangle() throws {
        let session = HTML3DSession()
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle, index: [0, 1, 2]))
        var f = header(records: 1)
        record(&f, 0, geometry: 1, count: 3)
        submit(session, f)
        let pixels = try XCTUnwrap(session.renderOffscreen(width: size, height: size))
        let center = pixel(pixels, x: size / 2, y: size / 2)
        XCTAssertEqual(center.g, 255)
        XCTAssertEqual(center.r, 0)
    }

    func testRejectsIndicesBeyondTheVertexCount() {
        let session = HTML3DSession()
        XCTAssertFalse(define(session, id: 1, positions: fullTriangle, index: [0, 1, 3]))
        XCTAssertFalse(define(session, id: 2, positions: fullTriangle, index: [0, 1, 65535]))
        XCTAssertTrue(define(session, id: 3, positions: fullTriangle, index: [0, 1, 2]))
        XCTAssertEqual(session.stats.rejected, 2)
    }

    func testRejectsMistypedOrShortAttributes() {
        let session = HTML3DSession()
        let shorts: [UInt16] = [0, 1, 2, 3, 4, 5]
        let typed = shorts.withUnsafeBytes { session.defineGeometry(id: 1, positions: HTML3DArray(pointer: $0.baseAddress!, length: $0.count, kind: .uint16), normals: nil, colors: nil, colorSize: 0, index: nil) }
        XCTAssertFalse(typed)
        XCTAssertFalse(define(session, id: 2, positions: fullTriangle, normals: [0, 0, 1]))
        let colors: [Float] = [1, 0, 0]
        let short = fullTriangle.withUnsafeBytes { p in
            colors.withUnsafeBytes { c in
                session.defineGeometry(id: 3, positions: HTML3DArray(pointer: p.baseAddress!, length: p.count, kind: .float32), normals: nil, colors: HTML3DArray(pointer: c.baseAddress!, length: c.count, kind: .float32), colorSize: 3, index: nil)
            }
        }
        XCTAssertFalse(short)
        XCTAssertFalse(define(session, id: 4, positions: [1, 2]))
    }

    func testSessionGeometryBudgetIsEnforced() {
        var limits = HTML3DLimits()
        limits.maxGeometryBytes = 1024
        let session = HTML3DSession(limits: limits)
        let big = [Float](repeating: 0, count: 300)
        XCTAssertFalse(define(session, id: 1, positions: big))
        let small = [Float](repeating: 0, count: 150)
        XCTAssertTrue(define(session, id: 1, positions: small))
        XCTAssertFalse(define(session, id: 2, positions: small))
        XCTAssertTrue(define(session, id: 1, positions: small))
        session.releaseGeometry(id: 1)
        XCTAssertTrue(define(session, id: 2, positions: small))
    }

    func testProcessBudgetIsSharedAndReleased() throws {
        let gpu = try XCTUnwrap(HTML3DGPU.shared)
        let saved = HTML3DGPU.processBudgetBytes
        defer { HTML3DGPU.processBudgetBytes = saved }
        HTML3DGPU.processBudgetBytes = gpu.reservedBytes + 2048
        let positions = [Float](repeating: 0, count: 300)
        var first: HTML3DSession? = HTML3DSession()
        let second = HTML3DSession()
        XCTAssertTrue(define(first!, id: 1, positions: positions))
        XCTAssertFalse(define(second, id: 1, positions: positions))
        first = nil
        XCTAssertTrue(define(second, id: 1, positions: positions))
        second.stop("test")
        XCTAssertTrue(define(HTML3DSession(), id: 1, positions: positions))
    }

    func testPerFrameVertexWorkAndDrawCountAreCapped() throws {
        var limits = HTML3DLimits()
        limits.maxVertexWork = 4
        limits.maxDraws = 8
        let session = HTML3DSession(limits: limits)
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle))
        XCTAssertTrue(define(session, id: 2, positions: fullTriangle))
        var f = header(records: 2)
        record(&f, 0, geometry: 1, count: 3)
        record(&f, 1, geometry: 2, count: 3, color: SIMD4(0, 0, 1, 1))
        submit(session, f)
        let pixels = try XCTUnwrap(session.renderOffscreen(width: size, height: size))
        XCTAssertEqual(pixel(pixels, x: size / 2, y: size / 2).g, 255)
        XCTAssertEqual(session.stats.clipped, 1)
        XCTAssertEqual(session.stats.draws, 1)

        var tight = HTML3DLimits()
        tight.maxDraws = 1
        let capped = HTML3DSession(limits: tight)
        XCTAssertTrue(define(capped, id: 1, positions: fullTriangle))
        XCTAssertTrue(define(capped, id: 2, positions: fullTriangle))
        submit(capped, f)
        XCTAssertNotNil(capped.renderOffscreen(width: size, height: size))
        XCTAssertEqual(capped.stats.draws, 1)
        XCTAssertEqual(capped.stats.clipped, 1)
    }

    func testRangesBeyondTheGeometryAreClamped() throws {
        let session = HTML3DSession()
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle, index: [0, 1, 2]))
        var f = header(records: 3)
        record(&f, 0, geometry: 1, count: 1_000_000)
        record(&f, 1, geometry: 1, count: 3, start: 1_000_000)
        record(&f, 2, geometry: 1, count: 3, instances: 99, instanceCount: 1000)
        submit(session, f)
        XCTAssertNotNil(session.renderOffscreen(width: size, height: size))
        XCTAssertEqual(session.stats.draws, 1)
    }

    func testInstanceCountsAreClampedToTheUploadedMatrices() throws {
        let session = HTML3DSession()
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle))
        var matrix = [Float](repeating: 0, count: 16)
        for i in 0 ..< 4 {
            matrix[i * 5] = 1
        }
        let accepted = matrix.withUnsafeBytes { session.defineInstances(id: 5, matrices: HTML3DArray(pointer: $0.baseAddress!, length: $0.count, kind: .float32), count: 1_000_000, colors: nil) }
        XCTAssertTrue(accepted)
        var f = header(records: 1)
        record(&f, 0, geometry: 1, count: 3, instances: 5, instanceCount: 1_000_000)
        submit(session, f)
        let pixels = try XCTUnwrap(session.renderOffscreen(width: size, height: size))
        XCTAssertEqual(pixel(pixels, x: size / 2, y: size / 2).g, 255)
        XCTAssertEqual(session.stats.triangles, 1)
    }

    func testHostileFramesDoNotCrashOrHang() throws {
        let session = HTML3DSession()
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle, index: [0, 1, 2]))
        var generator = SystemRandomNumberGenerator()
        let specials: [Float] = [.nan, .infinity, -.infinity, 0, -1, 1e30, -1e30, 1, 2, 3, 4096, 65535, Float(Int32.max)]
        for round in 0 ..< 40 {
            var f = header(records: 16)
            for i in 0 ..< f.count {
                if round % 2 == 0 || i >= HTML3DLayout.header {
                    f[i] = Bool.random(using: &generator) ? specials.randomElement(using: &generator)! : Float.random(in: -10 ... 10, using: &generator)
                }
            }
            for r in 0 ..< 16 {
                f[HTML3DLayout.header + r * HTML3DLayout.record] = Float(Int.random(in: 0 ... 2, using: &generator))
            }
            f[HTML3DLayout.recordCount] = Float.random(in: -5 ... 1e9, using: &generator)
            submit(session, f)
            _ = session.renderOffscreen(width: size, height: size)
        }
        XCTAssertNil(session.stopReason)
    }

    func testStopDropsResourcesAndRefusesNewWork() throws {
        let gpu = try XCTUnwrap(HTML3DGPU.shared)
        let before = gpu.reservedBytes
        let session = HTML3DSession()
        var stopped: String?
        session.onStop = { stopped = $0 }
        XCTAssertTrue(define(session, id: 1, positions: fullTriangle))
        XCTAssertGreaterThan(gpu.reservedBytes, before)
        session.stop("too slow")
        XCTAssertEqual(stopped, "too slow")
        XCTAssertEqual(gpu.reservedBytes, before)
        XCTAssertFalse(define(session, id: 2, positions: fullTriangle))
        submit(session, header(clear: SIMD4(1, 0, 0, 1)))
        XCTAssertNil(session.renderOffscreen(width: size, height: size))
    }

    func testDrawableSizeIsCapped() {
        var limits = HTML3DLimits()
        limits.maxDrawablePixels = 1000 * 1000
        let session = HTML3DSession(limits: limits)
        let capped = session.drawableSize(for: CGSize(width: 5000, height: 5000), scale: 2)
        XCTAssertLessThanOrEqual(capped.width * capped.height, 1_000_000)
        XCTAssertEqual(session.drawableSize(for: CGSize(width: 100, height: 50), scale: 2), CGSize(width: 200, height: 100))
    }

    func testSessionsDoNotShareGeometry() throws {
        let first = HTML3DSession()
        let second = HTML3DSession()
        XCTAssertTrue(define(first, id: 1, positions: fullTriangle))
        var f = header(clear: SIMD4(0, 0, 0, 1), records: 1)
        record(&f, 0, geometry: 1, count: 3)
        submit(second, f)
        let pixels = try XCTUnwrap(second.renderOffscreen(width: size, height: size))
        XCTAssertEqual(pixel(pixels, x: size / 2, y: size / 2).g, 0)
        XCTAssertEqual(second.stats.draws, 0)
    }

    func testGPUWatchdogStopsHungOrSlowScenes() {
        let hung = HTML3DSession()
        hung.recordCompleted(gpuSeconds: 1, failed: false)
        XCTAssertNotNil(hung.stopReason)

        var limits = HTML3DLimits()
        limits.slowFramesToStop = 3
        let slow = HTML3DSession(limits: limits)
        slow.recordCompleted(gpuSeconds: 0.05, failed: false)
        slow.recordCompleted(gpuSeconds: 0.05, failed: false)
        slow.recordCompleted(gpuSeconds: 0.001, failed: false)
        slow.recordCompleted(gpuSeconds: 0.05, failed: false)
        slow.recordCompleted(gpuSeconds: 0.05, failed: false)
        XCTAssertNil(slow.stopReason)
        slow.recordCompleted(gpuSeconds: 0.05, failed: false)
        XCTAssertNotNil(slow.stopReason)

        let faulted = HTML3DSession()
        faulted.recordCompleted(gpuSeconds: 0, failed: true)
        XCTAssertNotNil(faulted.stopReason)
    }

    func testRecordsOutsideTheFrustumAreCulledOnlyWhenAllowed() throws {
        let session = HTML3DSession()
        XCTAssertTrue(define(session, id: 1, positions: [-0.1, -0.1, 0, 0.1, -0.1, 0, 0, 0.1, 0]))
        var f = header(records: 3)
        record(&f, 0, geometry: 1, count: 3, flags: 64)
        record(&f, 1, geometry: 1, count: 3, flags: 64)
        record(&f, 2, geometry: 1, count: 3)
        f[HTML3DLayout.header + HTML3DLayout.record + HTML3DLayout.recordMatrix + 12] = 50
        f[HTML3DLayout.header + 2 * HTML3DLayout.record + HTML3DLayout.recordMatrix + 12] = 50
        f[HTML3DLayout.header + 2 * HTML3DLayout.record + HTML3DLayout.recordMaterial] = 2
        submit(session, f)
        XCTAssertNotNil(session.renderOffscreen(width: size, height: size))
        XCTAssertEqual(session.stats.triangles, 2)
    }

    func testBoundingSphereIgnoresNothingAndRejectsNonFinite() {
        let positions: [Float] = [-1, 0, 0, 1, 0, 0, 0, 2, 0]
        let sphere = positions.withUnsafeBytes { HTML3DSession.boundingSphere($0.baseAddress!, 3) }
        for i in 0 ..< 3 {
            let v = SIMD3<Float>(positions[i * 3], positions[i * 3 + 1], positions[i * 3 + 2])
            XCTAssertLessThanOrEqual(simd_distance(v, SIMD3<Float>(sphere.x, sphere.y, sphere.z)), sphere.w)
        }
        let broken: [Float] = [0, 0, 0, .nan, 0, 0]
        XCTAssertEqual(broken.withUnsafeBytes { HTML3DSession.boundingSphere($0.baseAddress!, 2) }.w, .infinity)
    }
}
