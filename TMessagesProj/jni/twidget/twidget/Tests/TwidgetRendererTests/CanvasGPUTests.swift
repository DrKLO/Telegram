import XCTest
@testable import TwidgetRenderer

final class CanvasGPUTests: XCTestCase {
    private func gpuCanvas(_ w: Int, _ h: Int) throws -> (WidgetCanvas, WidgetCanvasMetal) {
        guard WidgetCanvasMetal.available else {
            throw XCTSkip("no Metal device")
        }
        WidgetCanvasMetal.prepare(wait: true)
        let canvas = WidgetCanvas(width: w, height: h, allowance: { WidgetCanvas.maxPixels })
        guard canvas.storage != nil, let gpu = WidgetCanvasMetal() else {
            throw XCTSkip("no IOSurface backing")
        }
        canvas.backend = gpu
        return (canvas, gpu)
    }

    private struct Difference {
        var worst = 0
        var over2 = 0
        var total = 0
    }

    private func premultiplied(_ canvas: WidgetCanvas) -> [UInt8] {
        var data = [UInt8](repeating: 0, count: canvas.width * canvas.height * 4)
        guard let image = canvas.image, let ctx = CGContext(data: &data, width: canvas.width, height: canvas.height, bitsPerComponent: 8, bytesPerRow: canvas.width * 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else {
            return data
        }
        ctx.setBlendMode(.copy)
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: canvas.width, height: canvas.height))
        return data
    }

    private func compare(_ a: WidgetCanvas, _ b: WidgetCanvas) -> Difference {
        let pa = premultiplied(a), pb = premultiplied(b)
        var result = Difference()
        result.total = a.width * a.height
        for i in stride(from: 0, to: min(pa.count, pb.count), by: 4) {
            var m = 0
            for c in 0..<4 {
                m = max(m, abs(Int(pa[i + c]) - Int(pb[i + c])))
            }
            result.worst = max(result.worst, m)
            if m > 2 {
                result.over2 += 1
            }
        }
        return result
    }

    private struct Random {
        var seed: UInt64
        mutating func next() -> Double {
            seed = seed &* 6364136223846793005 &+ 1442695040888963407
            return Double(seed >> 11) / Double(1 << 53)
        }
    }

    private func rectFrame(_ random: inout Random, count: Int) -> [Any] {
        var commands: [Any] = [["s", "fillStyle", "rgba(11,16,32,0.35)"], ["c", "fillRect", 0, 0, 320, 240]]
        for i in 0..<count {
            commands.append(["c", "save"])
            if i % 7 == 0 {
                commands.append(["c", "rotate", (random.next() - 0.5) * 0.6])
            }
            if i % 9 == 0 {
                commands.append(["c", "scale", 0.5 + random.next() * 1.5, 0.5 + random.next()])
            }
            if i % 5 == 0 {
                commands.append(["s", "globalAlpha", 0.2 + random.next() * 0.8])
            }
            if i % 13 == 0 {
                commands.append(["s", "globalCompositeOperation", "lighter"])
            }
            if i % 23 == 0 {
                commands.append(["s", "globalCompositeOperation", "destination-out"])
            }
            commands.append(["s", "fillStyle", "hsla(\(Int(random.next() * 360)),80%,55%,\(0.3 + random.next() * 0.7))"])
            let w = random.next() * 40 - (i % 17 == 0 ? 50 : 0)
            commands.append(["c", "fillRect", random.next() * 300 + 0.37, random.next() * 220 + 0.61, w, random.next() * 30 + 0.2])
            if i % 19 == 0 {
                commands.append(["c", "clearRect", random.next() * 300 + 0.3, random.next() * 220 + 0.7, 9.4, 6.2])
            }
            commands.append(["c", "restore"])
        }
        return commands
    }

    func testRectBatchesMatchCoreGraphics() throws {
        let (gpu, metal) = try gpuCanvas(320, 240)
        let cpu = WidgetCanvas(width: 320, height: 240, allowance: { WidgetCanvas.maxPixels })
        var random = Random(seed: 7)
        for _ in 0..<12 {
            let frame = rectFrame(&random, count: 300)
            gpu.run(frame)
            cpu.run(frame)
        }
        XCTAssertGreaterThan(metal.acceleratedBatches, 10)
        let d = compare(gpu, cpu)
        XCTAssertLessThanOrEqual(d.worst, 6)
        XCTAssertLessThan(Double(d.over2) / Double(d.total), 0.02)
    }

    func testTransformedRectsMatchCoreGraphics() throws {
        let (gpu, _) = try gpuCanvas(200, 200)
        let cpu = WidgetCanvas(width: 200, height: 200, allowance: { WidgetCanvas.maxPixels })
        var frame: [Any] = [["s", "fillStyle", "#ffffff"], ["c", "fillRect", 0, 0, 200, 200]]
        frame.append(["c", "setTransform", 1, 0.2, -0.3, 1, 60, 20])
        frame.append(["s", "fillStyle", "rgba(20,60,200,0.8)"])
        frame.append(["c", "fillRect", 10.3, 10.6, 90.2, 70.1])
        frame.append(["c", "resetTransform"])
        frame.append(["c", "translate", 100.5, 150.25])
        frame.append(["c", "rotate", 0.7])
        frame.append(["s", "fillStyle", "orange"])
        frame.append(["c", "fillRect", -30, -10, 60, 20])
        frame.append(["c", "resetTransform"])
        frame.append(["c", "scale", 2, 2])
        frame.append(["s", "fillStyle", "#0a0"])
        frame.append(["c", "fillRect", 5.25, 85.5, 0.3, 10])
        frame.append(["c", "clearRect", 30.1, 30.1, 10, 10])
        gpu.run(frame)
        cpu.run(frame)
        let d = compare(gpu, cpu)
        XCTAssertLessThanOrEqual(d.worst, 40)
        XCTAssertLessThan(Double(d.over2) / Double(d.total), 0.01)
    }

    func testUnsupportedStateFallsBackAndStaysExact() throws {
        let (gpu, metal) = try gpuCanvas(160, 120)
        let cpu = WidgetCanvas(width: 160, height: 120, allowance: { WidgetCanvas.maxPixels })
        let frames: [[Any]] = [
            [["s", "fillStyle", "#123456"], ["c", "fillRect", 0, 0, 160, 120], ["c", "fillRect", 10.5, 10.5, 20, 20]],
            [["s", "shadowColor", "rgba(0,0,0,0.5)"], ["s", "shadowBlur", 4], ["s", "fillStyle", "#ff0000"], ["c", "fillRect", 40, 40, 30, 30]],
            [["s", "fillStyle", "#00ff00"], ["c", "fillRect", 80, 20, 30, 30]],
            [["s", "shadowBlur", 0], ["s", "shadowColor", "rgba(0,0,0,0)"], ["c", "beginPath"], ["c", "rect", 0, 0, 80, 60], ["c", "clip"], ["c", "fillRect", 0, 0, 160, 120]],
            [["s", "fillStyle", "#0000ff"], ["c", "fillRect", 5, 5, 150, 110]],
            [["c", "save"], ["s", "globalCompositeOperation", "multiply"], ["c", "fillRect", 20, 20, 40, 40], ["c", "restore"]],
        ]
        for frame in frames {
            gpu.run(frame)
            cpu.run(frame)
        }
        XCTAssertEqual(metal.acceleratedBatches, 1)
        let d = compare(gpu, cpu)
        XCTAssertLessThanOrEqual(d.worst, 2)
    }

    func testCircleFillsMatchCoreGraphics() throws {
        let (gpu, metal) = try gpuCanvas(240, 180)
        let cpu = WidgetCanvas(width: 240, height: 180, allowance: { WidgetCanvas.maxPixels })
        var random = Random(seed: 3)
        var frame: [Any] = [["s", "fillStyle", "#101418"], ["c", "fillRect", 0, 0, 240, 180]]
        for row in 0..<6 {
            for column in 0..<8 {
                let i = row * 8 + column
                frame.append(["c", "save"])
                frame.append(["c", "translate", 15 + Double(column) * 30 + random.next(), 15 + Double(row) * 30 + random.next()])
                if i % 5 == 0 {
                    frame.append(["c", "scale", 0.7 + random.next() * 0.3, 0.7 + random.next() * 0.3])
                }
                frame.append(["s", "globalAlpha", 0.5 + random.next() * 0.5])
                frame.append(["s", "fillStyle", "hsl(\(Int(random.next() * 360)),90%,60%)"])
                frame.append(["c", "beginPath"])
                frame.append(["c", "arc", 0, 0, i % 7 == 0 ? random.next() * 0.9 + 0.2 : random.next() * 11 + 1, 0, Double.pi * 2])
                frame.append(["c", "fill"])
                frame.append(["c", "restore"])
            }
        }
        gpu.run(frame)
        cpu.run(frame)
        XCTAssertEqual(metal.acceleratedBatches, 1)
        let d = compare(gpu, cpu)
        XCTAssertLessThanOrEqual(d.worst, 32)
        XCTAssertLessThan(Double(d.over2) / Double(d.total), 0.06)
    }

    func testPathsThatAreNotSingleCirclesStayOnCoreGraphics() throws {
        let (gpu, metal) = try gpuCanvas(100, 100)
        let cpu = WidgetCanvas(width: 100, height: 100, allowance: { WidgetCanvas.maxPixels })
        let frames: [[Any]] = [
            [["c", "beginPath"], ["c", "arc", 30, 30, 10, 0, 6.3], ["c", "arc", 60, 60, 10, 0, 6.3], ["c", "fill"]],
            [["c", "beginPath"], ["c", "arc", 50, 50, 20, 0, 3.0], ["c", "fill"]],
            [["c", "moveTo", 10, 10], ["c", "arc", 50, 50, 5, 0, 6.3], ["c", "fill"]],
            [["c", "beginPath"], ["c", "arc", 50, 50, 20, 6.3, 0, true], ["s", "fillStyle", "#f0f"], ["c", "fill"], ["c", "fillRect", 1, 1, 5, 5]],
        ]
        for frame in frames {
            gpu.run(frame)
            cpu.run(frame)
        }
        XCTAssertEqual(metal.acceleratedBatches, 1)
        let d = compare(gpu, cpu)
        XCTAssertLessThanOrEqual(d.worst, 32)
    }

    func testReadbackAfterGPUWorkSeesEveryFrame() throws {
        let (gpu, metal) = try gpuCanvas(64, 64)
        for i in 0..<20 {
            gpu.run([["s", "fillStyle", "rgb(\(i * 10),0,0)"], ["c", "fillRect", 0, 0, 64, 64]])
            let pixel = [UInt8](gpu.pixels(x: 3, y: 3, w: 1, h: 1))
            XCTAssertEqual(Int(pixel[0]), i * 10)
            XCTAssertEqual(pixel[3], 255)
        }
        XCTAssertEqual(metal.acceleratedBatches, 20)
        XCTAssertNotNil(gpu.image)
    }

    func testHugeBatchesAreSplitAndBounded() throws {
        let (gpu, metal) = try gpuCanvas(2048, 2048)
        var frame: [Any] = []
        for i in 0..<300 {
            frame.append(["s", "fillStyle", i % 2 == 0 ? "rgba(255,0,0,0.01)" : "rgba(0,0,255,0.01)"])
            frame.append(["c", "fillRect", -1e9, -1e9, 2e9, 2e9])
        }
        gpu.run(frame)
        let pixel = [UInt8](gpu.pixels(x: 1000, y: 1000, w: 1, h: 1))
        XCTAssertGreaterThan(pixel[3], 0)
        XCTAssertGreaterThan(metal.committedBuffers, 1)
    }
}
