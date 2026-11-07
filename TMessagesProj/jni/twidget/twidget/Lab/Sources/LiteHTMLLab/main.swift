import Cocoa
import Metal
import QuartzCore
import LiteHTML
import HTMLScript
import HTML3D

let labRoot = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
let repoRoot = labRoot.deletingLastPathComponent()
let chatsDir = repoRoot.appendingPathComponent("Examples/chats")
let tracesDir = labRoot.appendingPathComponent("traces")
let resultsDir = labRoot.appendingPathComponent("results")
let outDir = labRoot.appendingPathComponent("out")

let maxWidth: CGFloat = 400
let scale: CGFloat = 2
var tileHeight: CGFloat = 64
var tileWidth: CGFloat = 0
var wholeTiles = true
var renderSpace: CGColorSpace? = nil
var incrementalDefault = false
var skipRaster = false
var reusedFrames = 0

struct Message: Codable {
    let name: String
    let body: String
}

struct Trace: Codable {
    let name: String
    let frames: [String]
}

func threadCPU() -> UInt64 {
    var ts = timespec()
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts)
    return UInt64(ts.tv_sec) * 1_000_000_000 + UInt64(ts.tv_nsec)
}

func wallNanos() -> UInt64 {
    return DispatchTime.now().uptimeNanoseconds
}

func loadMessages() -> [Message] {
    var out: [Message] = []
    let files = (try? FileManager.default.contentsOfDirectory(atPath: chatsDir.path).filter { $0.hasSuffix(".chat") }.sorted()) ?? []
    for file in files {
        let text = (try? String(contentsOf: chatsDir.appendingPathComponent(file))) ?? ""
        var current: [String]? = nil
        var index = 0
        func flush() {
            if let lines = current {
                let body = lines.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
                if !body.isEmpty {
                    out.append(Message(name: "\(file.replacingOccurrences(of: ".chat", with: ""))#\(index)", body: body))
                }
                index += 1
            }
        }
        for line in text.components(separatedBy: .newlines) {
            if line.hasPrefix("--- ") || line == "---" {
                flush()
                current = []
                continue
            }
            if current == nil || line.hasPrefix("@on ") {
                continue
            }
            current?.append(line)
        }
        flush()
    }
    return out
}

func containsBodyTag(_ text: String) -> Bool {
    let scan: (UnsafeBufferPointer<UInt8>) -> Bool = { bytes in
        let count = bytes.count
        if count < 5 {
            return false
        }
        var i = 0
        while i <= count - 5 {
            if bytes[i] == 0x3C, bytes[i + 1] | 0x20 == 0x62, bytes[i + 2] | 0x20 == 0x6F, bytes[i + 3] | 0x20 == 0x64, bytes[i + 4] | 0x20 == 0x79 {
                return true
            }
            i += 1
        }
        return false
    }
    if let result = text.utf8.withContiguousStorageIfAvailable(scan) {
        return result
    }
    return Array(text.utf8).withUnsafeBufferPointer(scan)
}

let slowWrap = ProcessInfo.processInfo.environment["LAB_SLOW_WRAP"] != nil

func wrap(_ body: String) -> String {
    if slowWrap ? body.range(of: "<body", options: .caseInsensitive) != nil : containsBodyTag(body) {
        return body
    }
    return "<body class=\"lc-light lc-in\">" + body
}

final class Recorder: HTMLScriptHost {
    var frames: [String] = []
    func scriptDidChange(_ script: HTMLScript) {
        let html = script.currentHTML
        if frames.last != html {
            frames.append(html)
        }
    }
}

func record(seconds: Double, filter: String?) {
    try? FileManager.default.createDirectory(at: tracesDir, withIntermediateDirectories: true)
    let messages = loadMessages()
    var statics: [Message] = []
    for message in messages {
        if let filter = filter, !message.name.contains(filter) {
            continue
        }
        guard message.body.range(of: "<script", options: .caseInsensitive) != nil, let script = HTMLScript.make(html: message.body) else {
            statics.append(message)
            continue
        }
        let recorder = Recorder()
        script.host = recorder
        script.setVisible(true)
        recorder.frames.append(script.currentHTML)
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
        script.host = nil
        let trace = Trace(name: message.name, frames: recorder.frames)
        let safe = message.name.replacingOccurrences(of: "#", with: "_")
        try? JSONEncoder().encode(trace).write(to: tracesDir.appendingPathComponent("\(safe).json"))
        print(String(format: "%-26@ %4d frames  %6.1f KB/frame%@", message.name as NSString, recorder.frames.count, Double(recorder.frames.map { $0.utf8.count }.reduce(0, +)) / Double(max(1, recorder.frames.count)) / 1024, script.errorText.map { "  ERROR \($0)" } ?? ""))
    }
    if filter == nil {
        try? JSONEncoder().encode(statics).write(to: tracesDir.appendingPathComponent("static.json"))
        print("\(statics.count) static messages")
    }
}

final class Tile {
    let rect: CGRect
    let contents: AnyObject
    init(rect: CGRect, image: CGImage) {
        self.rect = rect
        self.contents = image
    }
    init(rect: CGRect, surface: IOSurface) {
        self.rect = rect
        self.contents = surface
    }
    var image: CGImage { contents as! CGImage }
}

final class GPUTarget {
    let surface: IOSurface
    let texture: MTLTexture
    init?(device: MTLDevice, width: Int, height: Int, space: CGColorSpace) {
        guard let surface = IOSurface(properties: [.width: width, .height: height, .bytesPerElement: 4, .pixelFormat: 0x42475241]) else { return nil }
        if let plist = space.copyPropertyList() { IOSurfaceSetValue(unsafeBitCast(surface, to: IOSurfaceRef.self), kIOSurfaceColorSpace, plist) }
        let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: width, height: height, mipmapped: false)
        desc.usage = [.renderTarget, .shaderRead]
        desc.storageMode = .shared
        guard let texture = device.makeTexture(descriptor: desc, iosurface: unsafeBitCast(surface, to: IOSurfaceRef.self), plane: 0) else { return nil }
        self.surface = surface
        self.texture = texture
    }
}

final class GPUPipeline {
    let renderer = LHGPURenderer(device: nil)!
    var widthHint: CGFloat = 0
    var previousDocument: LHDocument?
    var previous: (size: CGSize, list: LHDisplayList, tiles: [Tile])?
    var pools: [Int: [GPUTarget]] = [:]
    var current: [Int: GPUTarget] = [:]
    var inFlight: MTLCommandBuffer?
    var waits = 0

    func target(_ index: Int, _ width: Int, _ height: Int) -> GPUTarget? {
        let avoid = current[index]
        var pool = (pools[index] ?? []).filter { $0.texture.width == width && $0.texture.height == height }
        let checkInUse = !args.contains("--no-inuse")
        if let free = pool.first(where: { $0 !== avoid && (!checkInUse || !$0.surface.isInUse) }) { pools[index] = pool; return free }
        guard let t = GPUTarget(device: renderer.device, width: width, height: height, space: renderSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!) else { return nil }
        pool.append(t); if pool.count > 3 { pool.removeFirst() }; pools[index] = pool
        return t
    }

    func render(html: String, completion: ((Rendering) -> Void)? = nil) -> Rendering {
        if let inFlight = inFlight, inFlight.status != .completed {
            waits += 1
            inFlight.waitUntilCompleted()
        }
        inFlight = nil
        var asyncBuffer: MTLCommandBuffer?
        let document = LHDocument(html: wrap(html), css: bubbleCSS, previous: previousDocument)
        previousDocument = document
        document.colorSpace = renderSpace
        let size = document.layout(maxWidth: maxWidth, preferredWidth: widthHint)
        widthHint = min(size.width, maxWidth)
        let list = document.displayList(maxWidth: maxWidth, gpu: renderer, scale: scale)
        var rects: [CGRect] = []
        var y: CGFloat = 0
        while y < size.height { rects.append(CGRect(x: 0, y: y, width: size.width, height: min(tileHeight, size.height - y))); y += tileHeight }
        var changed: [CGRect]?
        if let previous = previous, previous.size == size { changed = list.changedRects(since: previous.list).map { $0.rectValue } }
        var tiles: [Tile] = []
        var dirty: [(Int, CGRect)] = []
        for (i, r) in rects.enumerated() {
            if let previous = previous, let changed = changed, i < previous.tiles.count, previous.tiles[i].rect == r, !changed.contains(where: { $0.intersects(r) }) {
                tiles.append(previous.tiles[i])
            } else {
                tiles.append(Tile(rect: r, image: placeholderImage))
                dirty.append((i, r))
            }
        }
        if !dirty.isEmpty {
            if !list.gpuSupported {
                for (i, r) in dirty { if let image = document.copyImage(maxWidth: maxWidth, rect: r, scale: scale) { tiles[i] = Tile(rect: r, image: image); current[i] = nil } }
            } else if let buffer = renderer.queue.makeCommandBuffer() {
                var targets: [GPUTarget] = []
                for (i, r) in dirty { if let t = target(i, Int((r.width * scale).rounded(.up)), Int((r.height * scale).rounded(.up))) { targets.append(t) } }
                if targets.count == dirty.count {
                    _ = renderer.encode(list, tiles: dirty.map { NSValue(rect: $0.1) }, scale: scale, targets: targets.map { $0.texture }, commandBuffer: buffer)
                    for ((i, r), t) in zip(dirty, targets) { tiles[i] = Tile(rect: r, surface: t.surface); current[i] = t }
                    if completion != nil {
                        asyncBuffer = buffer
                    } else {
                        buffer.commit()
                        buffer.waitUntilCompleted()
                    }
                }
            }
            renderer.endFrame()
        }
        previous = (size, list, tiles)
        let rendering = Rendering(size: size, tiles: tiles, displayList: list)
        if let completion = completion {
            if let buffer = asyncBuffer {
                buffer.addCompletedHandler { _ in DispatchQueue.main.async { completion(rendering) } }
                buffer.commit()
                inFlight = buffer
            } else {
                completion(rendering)
            }
        }
        return rendering
    }
}

let placeholderImage: CGImage = CGContext(data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue)!.makeImage()!

final class Rendering {
    let size: CGSize
    let tiles: [Tile]
    let displayList: LHDisplayList?
    init(size: CGSize, tiles: [Tile], displayList: LHDisplayList?) {
        self.size = size
        self.tiles = tiles
        self.displayList = displayList
    }
}

struct FrameCost {
    var cpu: Double = 0
    var wall: Double = 0
    var parse: Double = 0
    var layout: Double = 0
    var raster: Double = 0
    var dirtyPixels: Double = 0
    var create: Double = 0
    var layoutCall: Double = 0
    var displayList: Double = 0
    var diff: Double = 0
    var wrap: Double = 0
}

final class Pipeline {
    var widthHint: CGFloat = 0
    var previous: Rendering?
    var previousDocument: LHDocument?
    var incremental = incrementalDefault

    func render(html: String) -> (Rendering, LHDocument, FrameCost) {
        var cost = FrameCost()
        let c0 = threadCPU(), w0 = wallNanos()
        let wrapped = wrap(html)
        let t1 = threadCPU()
        let document = incremental ? LHDocument(html: wrapped, css: bubbleCSS, previous: previousDocument) : LHDocument(html: wrapped, css: bubbleCSS)
        if document.reusedPrevious { reusedFrames += 1 }
        previousDocument = document
        document.colorSpace = renderSpace
        let t2 = threadCPU()
        let size = document.layout(maxWidth: maxWidth, preferredWidth: widthHint)
        let t3 = threadCPU()
        widthHint = min(size.width, maxWidth)
        cost.parse = document.parseMilliseconds
        cost.layout = document.layoutMilliseconds
        let displayList = document.displayList(maxWidth: maxWidth)
        let t4 = threadCPU()
        var reusable: (tiles: [Tile], changed: [CGRect])?
        if let previous = previous, let previousList = previous.displayList, previous.size == size {
            reusable = (previous.tiles, displayList.changedRects(since: previousList).map { $0.rectValue })
        }
        let r0 = threadCPU()
        cost.wrap = Double(t1 - c0) / 1e6
        cost.create = Double(t2 - t1) / 1e6
        cost.layoutCall = Double(t3 - t2) / 1e6
        cost.displayList = Double(t4 - t3) / 1e6
        cost.diff = Double(r0 - t4) / 1e6
        var tiles: [Tile] = []
        var rects: [CGRect] = []
        var ty: CGFloat = 0
        while ty < size.height {
            let height = min(tileHeight, size.height - ty)
            var tx: CGFloat = 0
            let columnWidth = tileWidth > 0 ? tileWidth : size.width
            while tx < size.width {
                rects.append(CGRect(x: tx, y: ty, width: min(columnWidth, size.width - tx), height: height))
                tx += columnWidth
            }
            ty += height
        }
        for rect in rects {
            let index = tiles.count
            if let reusable = reusable, index < reusable.tiles.count, reusable.tiles[index].rect == rect {
                let old = reusable.tiles[index]
                let dirty = reusable.changed.reduce(CGRect.null) { $1.intersects(rect) ? $0.union($1.intersection(rect)) : $0 }
                if dirty.isNull || skipRaster {
                    tiles.append(old)
                } else if wholeTiles, let image = document.copyImage(maxWidth: maxWidth, rect: rect, scale: scale) {
                    cost.dirtyPixels += Double(rect.width * rect.height)
                    tiles.append(Tile(rect: rect, image: image))
                } else if !wholeTiles, let image = document.copyImage(maxWidth: maxWidth, rect: rect, scale: scale, base: old.image, dirty: dirty) {
                    cost.dirtyPixels += Double(dirty.width * dirty.height)
                    tiles.append(Tile(rect: rect, image: image))
                }
            } else if let image = document.copyImage(maxWidth: maxWidth, rect: rect, scale: scale) {
                cost.dirtyPixels += Double(rect.width * rect.height)
                tiles.append(Tile(rect: rect, image: image))
            }
        }
        cost.raster = Double(threadCPU() - r0) / 1e6
        cost.cpu = Double(threadCPU() - c0) / 1e6
        cost.wall = Double(wallNanos() - w0) / 1e6
        let rendering = Rendering(size: size, tiles: tiles, displayList: displayList)
        previous = rendering
        return (rendering, document, cost)
    }
}

final class Bitmap {
    let width: Int
    let height: Int
    let context: CGContext
    init(size: CGSize) {
        width = Int((size.width * scale).rounded())
        height = Int((size.height * scale).rounded())
        context = CGContext(data: nil, width: max(width, 1), height: max(height, 1), bitsPerComponent: 8, bytesPerRow: max(width, 1) * 4, space: renderSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
        context.clear(CGRect(x: 0, y: 0, width: width, height: height))
    }
    func draw(_ image: CGImage, rect: CGRect, totalHeight: CGFloat) {
        let r = CGRect(x: rect.minX * scale, y: (totalHeight - rect.maxY) * scale, width: rect.width * scale, height: rect.height * scale)
        context.draw(image, in: r)
    }
    var bytes: UnsafeMutableRawBufferPointer {
        return UnsafeMutableRawBufferPointer(start: context.data, count: width * height * 4)
    }
    func hash() -> UInt64 {
        var h: UInt64 = 0xcbf29ce484222325
        for byte in bytes {
            h = (h ^ UInt64(byte)) &* 0x100000001b3
        }
        return h &+ UInt64(width) << 48 &+ UInt64(height) << 32
    }
    func save(_ url: URL) {
        guard let image = context.makeImage() else { return }
        let rep = NSBitmapImageRep(cgImage: image)
        try? rep.representation(using: .png, properties: [:])?.write(to: url)
    }
}

func composite(_ rendering: Rendering) -> Bitmap {
    let bitmap = Bitmap(size: rendering.size)
    for tile in rendering.tiles {
        bitmap.draw(tile.image, rect: tile.rect, totalHeight: rendering.size.height)
    }
    return bitmap
}

func oracle(html: String, hint: CGFloat) -> Bitmap {
    let fresh = Pipeline()
    fresh.incremental = false
    fresh.widthHint = hint
    let (rendering, _, _) = fresh.render(html: html)
    return composite(rendering)
}

func diffPixels(_ a: Bitmap, _ b: Bitmap) -> Int {
    if a.width != b.width || a.height != b.height {
        return -1
    }
    let pa = a.bytes.bindMemory(to: UInt32.self), pb = b.bytes.bindMemory(to: UInt32.self)
    var n = 0
    for i in 0 ..< pa.count where pa[i] != pb[i] {
        n += 1
    }
    return n
}

func percentile(_ values: [Double], _ p: Double) -> Double {
    if values.isEmpty { return 0 }
    let s = values.sorted()
    return s[min(s.count - 1, Int(Double(s.count - 1) * p))]
}

func loadTraces(filter: String?) -> [Trace] {
    let files = ((try? FileManager.default.contentsOfDirectory(atPath: tracesDir.path)) ?? []).filter { $0.hasSuffix(".json") && $0 != "static.json" }.sorted()
    return files.compactMap { file in
        if let filter = filter, !file.contains(filter) { return nil }
        guard let data = try? Data(contentsOf: tracesDir.appendingPathComponent(file)) else { return nil }
        return try? JSONDecoder().decode(Trace.self, from: data)
    }
}

struct TraceResult: Codable {
    let name: String
    let frames: Int
    let cpuMean: Double
    let cpuP95: Double
    let cpuMax: Double
    let parse: Double
    let layout: Double
    let raster: Double
    let hashes: [UInt64]
    let mismatches: Int
}

func bench(label: String, filter: String?, verify: Bool, maxFrames: Int, savePNG: Bool) {
    LHImages.registerDemoImages()
    try? FileManager.default.createDirectory(at: resultsDir, withIntermediateDirectories: true)
    try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)
    var results: [TraceResult] = []
    var totalCPU = 0.0, totalFrames = 0, totalMismatch = 0
    var stageTotals = FrameCost()
    var zeroDirty = 0
    print(String(format: "%-26@ %6@ %8@ %8@ %8@ %7@ %7@ %7@ %@", "trace", "frames", "cpu ms", "p95", "max", "parse", "layout", "raster", verify ? "pixels" : ""))
    for trace in loadTraces(filter: filter) {
        let pipeline = Pipeline()
        var costs: [FrameCost] = []
        var hashes: [UInt64] = []
        var mismatches = 0
        let frames = Array(trace.frames.prefix(maxFrames))
        for (i, html) in frames.enumerated() {
            let hintBefore = pipeline.widthHint
            let (rendering, _, cost) = autoreleasepool { pipeline.render(html: html) }
            costs.append(cost)
            if verify {
                autoreleasepool {
                    let got = composite(rendering)
                    hashes.append(got.hash())
                    let want = oracle(html: html, hint: hintBefore)
                    let d = diffPixels(got, want)
                    if d != 0 {
                        mismatches += 1
                        if mismatches <= 2 {
                            let safe = trace.name.replacingOccurrences(of: "#", with: "_")
                            got.save(outDir.appendingPathComponent("\(safe)-\(i)-got.png"))
                            want.save(outDir.appendingPathComponent("\(safe)-\(i)-want.png"))
                        }
                    }
                    if savePNG && (i == 0 || i == frames.count / 2) {
                        got.save(outDir.appendingPathComponent("\(trace.name.replacingOccurrences(of: "#", with: "_"))-\(label)-\(i).png"))
                    }
                }
            }
        }
        let steady = costs.count > 1 ? Array(costs.dropFirst()) : costs
        let cpu = steady.map { $0.cpu }
        let result = TraceResult(name: trace.name, frames: frames.count, cpuMean: cpu.reduce(0, +) / Double(max(1, cpu.count)), cpuP95: percentile(cpu, 0.95), cpuMax: cpu.max() ?? 0,
                                 parse: steady.map { $0.parse }.reduce(0, +) / Double(max(1, steady.count)), layout: steady.map { $0.layout }.reduce(0, +) / Double(max(1, steady.count)),
                                 raster: steady.map { $0.raster }.reduce(0, +) / Double(max(1, steady.count)), hashes: hashes, mismatches: mismatches)
        results.append(result)
        totalCPU += cpu.reduce(0, +)
        zeroDirty += steady.filter { $0.dirtyPixels == 0 }.count
        for c in steady { stageTotals.wrap += c.wrap; stageTotals.create += c.create; stageTotals.layoutCall += c.layoutCall; stageTotals.displayList += c.displayList; stageTotals.diff += c.diff; stageTotals.raster += c.raster; stageTotals.parse += c.parse; stageTotals.dirtyPixels += c.dirtyPixels }
        totalFrames += cpu.count
        totalMismatch += mismatches
        print(String(format: "%-26@ %6d %8.2f %8.2f %8.2f %7.2f %7.2f %7.2f %@", trace.name as NSString, frames.count, result.cpuMean, result.cpuP95, result.cpuMax, result.parse, result.layout, result.raster, verify ? "\(mismatches)" as NSString : "" as NSString))
    }
    let f = Double(max(1, totalFrames))
    print("frames with nothing to redraw: \(zeroDirty) of \(totalFrames); documents patched in place: \(reusedFrames)")
    print(String(format: "STAGES per frame: wrap %.3f  create(parse) %.3f [lh parse %.3f]  layout %.3f  displayList %.3f  diff %.3f  raster %.3f  | dirty %.0f pt²", stageTotals.wrap / f, stageTotals.create / f, stageTotals.parse / f, stageTotals.layoutCall / f, stageTotals.displayList / f, stageTotals.diff / f, stageTotals.raster / f, stageTotals.dirtyPixels / f))
    print(String(format: "TOTAL %d frames, mean %.2f ms CPU/frame%@", totalFrames, totalCPU / Double(max(1, totalFrames)), verify ? ", \(totalMismatch) frames differ from a fresh render" : ""))
    try? JSONEncoder().encode(results).write(to: resultsDir.appendingPathComponent("\(label).json"))
}

func compare(_ a: String, _ b: String) {
    func load(_ label: String) -> [String: TraceResult] {
        guard let data = try? Data(contentsOf: resultsDir.appendingPathComponent("\(label).json")), let list = try? JSONDecoder().decode([TraceResult].self, from: data) else {
            fatalError("no results for \(label)")
        }
        return Dictionary(uniqueKeysWithValues: list.map { ($0.name, $0) })
    }
    let ra = load(a), rb = load(b)
    var differ = 0, sa = 0.0, sb = 0.0
    print(String(format: "%-26@ %9@ %9@ %7@ %@", "trace", a, b, "speedup", "pixels"))
    for name in ra.keys.sorted() {
        guard let x = ra[name], let y = rb[name] else { continue }
        let same = !x.hashes.isEmpty && x.hashes == y.hashes
        if !same && !x.hashes.isEmpty { differ += 1 }
        sa += x.cpuMean * Double(x.frames)
        sb += y.cpuMean * Double(y.frames)
        print(String(format: "%-26@ %9.2f %9.2f %6.2fx %@", name as NSString, x.cpuMean, y.cpuMean, x.cpuMean / max(y.cpuMean, 0.0001), x.hashes.isEmpty ? "-" : (same ? "identical" : "DIFFERENT") as NSString))
    }
    print(String(format: "weighted total %.0f ms vs %.0f ms → %.2fx, %d traces with different pixels", sa, sb, sa / max(sb, 0.001), differ))
}


func processEnergy() -> Double {
    var info = rusage_info_v6()
    _ = withUnsafeMutablePointer(to: &info) { $0.withMemoryRebound(to: rusage_info_t?.self, capacity: 1) { proc_pid_rusage(getpid(), RUSAGE_INFO_V6, $0) } }
    return Double(info.ri_energy_nj) / 1e6
}

func processCPU() -> Double {
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)
    return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) * 1000 + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1000
}

func systemCPU() -> Double {
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)
    return Double(usage.ru_stime.tv_sec) * 1000 + Double(usage.ru_stime.tv_usec) / 1000
}

final class Presenter: NSObject {
    let window: NSWindow
    let host: NSView
    var layers: [CALayer] = []
    let traces: [Trace]
    var total = (frames: 0, cpu: 0.0, sys: 0.0)
    var energy = 0.0
    var split = (render: 0.0, commit: 0.0)
    var walls: [Double] = []
    let fps = Double(option("--fps") ?? "60") ?? 60
    let metalProbe = args.contains("--metal-probe")
    var metalLayer: CAMetalLayer?
    var metalQueue: MTLCommandQueue?
    var gpuMs = 0.0
    var probeFrame = 0

    init(traces: [Trace]) {
        self.traces = traces
        let screen = NSScreen.main?.visibleFrame ?? NSRect(x: 0, y: 0, width: 1400, height: 900)
        window = NSWindow(contentRect: NSRect(x: screen.maxX - 440, y: screen.minY + 20, width: 420, height: 720), styleMask: [.titled], backing: .buffered, defer: false)
        window.title = "LiteHTMLLab present (measuring, don't cover)"
        window.level = .floating
        host = NSView(frame: NSRect(x: 0, y: 0, width: 420, height: 720))
        host.wantsLayer = true
        host.layer?.backgroundColor = NSColor.white.cgColor
        host.layer?.isGeometryFlipped = true
        window.contentView = host
        window.orderFrontRegardless()
        if metalProbe, let device = MTLCreateSystemDefaultDevice() {
            let layer = CAMetalLayer()
            layer.device = device
            layer.pixelFormat = .bgra8Unorm
            layer.framebufferOnly = true
            layer.frame = host.bounds
            layer.contentsScale = scale
            layer.drawableSize = CGSize(width: host.bounds.width * scale, height: host.bounds.height * scale)
            layer.colorspace = window.colorSpace?.cgColorSpace
            host.layer?.addSublayer(layer)
            metalLayer = layer
            metalQueue = device.makeCommandQueue()
        }
        if args.contains("--srgb") {
            renderSpace = nil
        } else if args.contains("--p3") {
            renderSpace = CGColorSpace(name: CGColorSpace.displayP3)
        } else {
            renderSpace = window.colorSpace?.cgColorSpace
        }
        print("rendering tiles in", renderSpace.flatMap { $0.name as String? } ?? (renderSpace == nil ? "sRGB" : "window color space"))
    }

    func apply(_ rendering: Rendering) {
        if let layer = metalLayer, let queue = metalQueue {
            probeFrame += 1
            guard let drawable = layer.nextDrawable(), let buffer = queue.makeCommandBuffer() else { return }
            let pass = MTLRenderPassDescriptor()
            pass.colorAttachments[0].texture = drawable.texture
            pass.colorAttachments[0].loadAction = .clear
            pass.colorAttachments[0].storeAction = .store
            pass.colorAttachments[0].clearColor = MTLClearColor(red: Double(probeFrame % 100) / 100, green: 0.5, blue: 0.5, alpha: 1)
            buffer.makeRenderCommandEncoder(descriptor: pass)?.endEncoding()
            buffer.addCompletedHandler { [weak self] b in
                let ms = (b.gpuEndTime - b.gpuStartTime) * 1000
                DispatchQueue.main.async { self?.gpuMs += ms }
            }
            buffer.present(drawable)
            buffer.commit()
            return
        }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        while layers.count < rendering.tiles.count {
            let layer = CALayer()
            layer.contentsScale = scale
            host.layer?.addSublayer(layer)
            layers.append(layer)
        }
        for (i, layer) in layers.enumerated() {
            if i < rendering.tiles.count {
                let tile = rendering.tiles[i]
                layer.isHidden = false
                if layer.frame != tile.rect { layer.frame = tile.rect }
                if (layer.contents as AnyObject?) !== tile.contents { layer.contents = tile.contents }
            } else {
                layer.isHidden = true
            }
        }
        CATransaction.commit()
    }

    lazy var modes: [String] = (option("--modes") ?? "").split(separator: ",").map(String.init)
    var modeTotals: [String: (frames: Int, cpu: Double, sys: Double)] = [:]
    lazy var windowSpace = window.colorSpace?.cgColorSpace

    func run(index: Int, mode modeIndex: Int = 0) {
        guard index < traces.count else {
            for m in modes {
                let t = modeTotals[m] ?? (0, 0, 0)
                print(String(format: "MODE %@: %.3f ms process CPU per frame, kernel %.3f (%d frames)", m as NSString, t.cpu / Double(max(t.frames, 1)), t.sys / Double(max(t.frames, 1)), t.frames))
            }
            let sorted = walls.sorted()
            let p = { (q: Double) -> Double in sorted.isEmpty ? 0 : sorted[min(sorted.count - 1, Int(Double(sorted.count) * q))] }
            print(String(format: "FRAME WALL at %.0f fps: p50 %.2f  p90 %.2f  p99 %.2f  max %.2f ms; over budget (%.1f ms): %d of %d", fps, p(0.5), p(0.9), p(0.99), sorted.last ?? 0, 1000 / fps, sorted.filter { $0 > 1000 / fps }.count, sorted.count))
            if metalProbe { print(String(format: "GPU %.3f ms per frame", gpuMs / Double(max(total.frames, 1)))) }
            print(String(format: "ENERGY %.3f mJ per frame (process CPU energy; GPU %@)", energy / Double(max(total.frames, 1)), "not attributed" as NSString))
            print(String(format: "TOTAL %d frames, %.2f ms process CPU per frame (render + Core Animation, all threads), of which kernel %.2f; main thread: render %.2f, layer update + commit %.2f", total.frames, total.cpu / Double(max(total.frames, 1)), total.sys / Double(max(total.frames, 1)), split.render / Double(max(total.frames, 1)), split.commit / Double(max(total.frames, 1))))
            exit(0)
        }
        let trace = traces[index]
        let mode = modes.isEmpty ? nil : modes[modeIndex]
        if let mode = mode { renderSpace = mode == "cpu" ? windowSpace : CGColorSpace(name: CGColorSpace.displayP3) }
        let useAsync = mode.map { $0 == "async" } ?? args.contains("--async")
        let pipeline = Pipeline()
        let gpuPipeline = (mode.map { $0 != "cpu" } ?? args.contains("--gpu")) ? GPUPipeline() : nil
        let renderFrame: (String) -> Rendering = { html in gpuPipeline?.render(html: html) ?? pipeline.render(html: html).0 }
        apply(renderFrame(trace.frames[0]))
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [self] in
            let start = processCPU()
            let energyStart = processEnergy()
            let sysStart = systemCPU()
            var i = 1
            let timer = Timer(timeInterval: 1.0 / fps, repeats: true) { [self] t in
                if i >= trace.frames.count {
                    t.invalidate()
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { [self] in
                        let cpu = processCPU() - start
                        let frames = max(trace.frames.count - 1, 1)
                        total.frames += frames
                        total.cpu += cpu
                        total.sys += systemCPU() - sysStart
                        energy += processEnergy() - energyStart
                        if let mode = mode {
                            var t = modeTotals[mode] ?? (0, 0, 0)
                            t.frames += frames
                            t.cpu += cpu
                            t.sys += systemCPU() - sysStart
                            modeTotals[mode] = t
                            if modeIndex + 1 < modes.count {
                                run(index: index, mode: modeIndex + 1)
                                return
                            }
                        } else {
                            print(String(format: "%-26@ %4d frames  %6.2f ms/frame", trace.name as NSString, frames, cpu / Double(frames)))
                        }
                        run(index: index + 1)
                    }
                    return
                }
                autoreleasepool {
                    let w0 = wallNanos()
                    let c0 = threadCPU()
                    var c1: UInt64 = 0, c2: UInt64 = 0
                    if let gpuPipeline = gpuPipeline, useAsync {
                        _ = gpuPipeline.render(html: trace.frames[i]) { [self] rendering in
                            let a0 = threadCPU()
                            apply(rendering)
                            split.commit += Double(threadCPU() - a0) / 1e6
                        }
                        c1 = threadCPU()
                        c2 = c1
                    } else {
                        let rendering = renderFrame(trace.frames[i])
                        c1 = threadCPU()
                        apply(rendering)
                        c2 = threadCPU()
                    }
                    split.render += Double(c1 - c0) / 1e6
                    split.commit += Double(c2 - c1) / 1e6
                    walls.append(Double(wallNanos() - w0) / 1e6)
                }
                i += 1
            }
            RunLoop.main.add(timer, forMode: .common)
        }
    }
}

let args = CommandLine.arguments.dropFirst()
func option(_ name: String) -> String? {
    guard let i = args.firstIndex(of: name), args.index(after: i) < args.endIndex else { return nil }
    return args[args.index(after: i)]
}
LHSanitizer.setLinkSchemes(["http", "https", "mailto", "chat", "tg"], imageSchemes: ["sf", "app"])
if let t = option("--tile"), let v = Double(t) { tileHeight = CGFloat(v) }
if let t = option("--tilew"), let v = Double(t) { tileWidth = CGFloat(v) }
wholeTiles = !args.contains("--partial")
incrementalDefault = args.contains("--incremental")
skipRaster = args.contains("--no-raster")
if option("--space") == "display" { renderSpace = NSScreen.main?.colorSpace?.cgColorSpace }
if option("--runtime") == "jsc" { HTMLScript.runtime = JSCScriptRuntime.shared }
switch args.first {
case "record":
    HTML3DSession.register()
    record(seconds: Double(option("--seconds") ?? "4") ?? 4, filter: option("--only"))
case "bench":
    bench(label: option("--label") ?? "run", filter: option("--only"), verify: args.contains("--verify"), maxFrames: Int(option("--frames") ?? "100000") ?? 100000, savePNG: args.contains("--png"))
case "present":
    LHImages.registerDemoImages()
    let app = NSApplication.shared
    app.setActivationPolicy(.accessory)
    let presenter = Presenter(traces: loadTraces(filter: option("--only")).filter { $0.frames.count > 1 })
    DispatchQueue.main.async { presenter.run(index: 0) }
    app.run()
case "sanitize":
    LHImages.registerDemoImages()
    var counts: [String: Int] = [:]
    var samples: [String: Set<String>] = [:]
    var documents = 0
    for trace in loadTraces(filter: option("--only")) {
        var previous: LHDocument?
        for html in trace.frames {
            autoreleasepool {
                let document = LHDocument(html: wrap(html), css: bubbleCSS, previous: args.contains("--fresh") ? nil : previous)
                previous = document
                _ = document.layout(maxWidth: maxWidth, preferredWidth: 0)
                documents += 1
                for (k, v) in document.sanitizerCounts { counts[trace.name + " " + k, default: 0] = max(counts[trace.name + " " + k, default: 0], v.intValue) }
                for d in document.sanitizerDetails { samples[trace.name, default: []].insert(d) }
            }
        }
    }
    print("\(documents) documents")
    for (k, v) in counts.sorted(by: { $0.key < $1.key }) { print("  \(k): \(v)") }
    for (k, v) in samples.sorted(by: { $0.key < $1.key }) { for d in v.sorted().prefix(args.contains("--all") ? 1000 : 6) { print("  \(k) | \(d)") } }
case "live":
    LHImages.registerDemoImages()
    let seconds = Double(option("--seconds") ?? "20") ?? 20
    let scripted = loadMessages().filter { $0.body.range(of: "<script", options: .caseInsensitive) != nil && (option("--only") == nil || $0.name.contains(option("--only")!)) }
    final class LiveBubble: HTMLScriptHost {
        let pipeline = Pipeline()
        var pending: String?
        var renders = 0
        var renderScheduled = false
        func changed(_ html: String) {
            pending = html
            if renderScheduled { return }
            renderScheduled = true
            DispatchQueue.main.async { [self] in
                renderScheduled = false
                if let html = pending { pending = nil; if !args.contains("--no-render") { _ = pipeline.render(html: html) }; renders += 1 }
            }
        }
        func scriptDidChange(_ script: HTMLScript) { changed(script.currentHTML) }
    }
    var keep: [AnyObject] = []
    var bubbles: [LiveBubble] = []
    for message in scripted {
        let bubble = LiveBubble()
        bubbles.append(bubble)
        if let script = HTMLScript.make(html: message.body) {
            script.host = bubble
            script.setVisible(true)
            bubble.changed(script.currentHTML)
            keep.append(script)
        }
    }
    RunLoop.main.run(until: Date().addingTimeInterval(1))
    func usage() -> rusage_info_v6 {
        var info = rusage_info_v6()
        _ = withUnsafeMutablePointer(to: &info) { $0.withMemoryRebound(to: rusage_info_t?.self, capacity: 1) { proc_pid_rusage(getpid(), RUSAGE_INFO_V6, $0) } }
        return info
    }
    let u0 = usage(), m0 = threadCPU(), r0 = bubbles.map { $0.renders }.reduce(0, +)
    RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    let u1 = usage(), m1 = threadCPU(), r1 = bubbles.map { $0.renders }.reduce(0, +)
    var tb = mach_timebase_info_data_t(); mach_timebase_info(&tb)
    let cpu = Double((u1.ri_user_time + u1.ri_system_time) - (u0.ri_user_time + u0.ri_system_time)) * Double(tb.numer) / Double(tb.denom) / 1e6
    print(String(format: "%d scripts, %.0f s: process CPU %.1f ms/s, main thread %.1f ms/s, %d renders (%.1f/s), wakeups %.0f/s (idle %.0f/s), energy %.1f mJ/s",
                 keep.count, seconds, cpu / seconds, Double(m1 - m0) / 1e6 / seconds, r1 - r0, Double(r1 - r0) / seconds,
                 Double((u1.ri_interrupt_wkups + u1.ri_pkg_idle_wkups) - (u0.ri_interrupt_wkups + u0.ri_pkg_idle_wkups)) / seconds,
                 Double(u1.ri_pkg_idle_wkups - u0.ri_pkg_idle_wkups) / seconds, Double(u1.ri_energy_nj - u0.ri_energy_nj) / 1e6 / seconds))
    exit(0)
case "threetest":
    HTML3DSession.register()
    if option("--runtime") == "jsc" { HTMLScript.runtime = JSCScriptRuntime.shared }
    NSApplication.shared.setActivationPolicy(.accessory)
    NSApplication.shared.finishLaunching()
    let html = """
    <script src="app:three"></script>
    <script>
    const renderer = new THREE.WebGLRenderer({ antialias: true });
    const scene = new THREE.Scene();
    scene.background = new THREE.Color(0x101828);
    const camera = new THREE.PerspectiveCamera(50, 1, 0.1, 100);
    camera.position.set(0, 1.2, 5);
    camera.lookAt(0, 0, 0);
    const cubes = [];
    for (let i = 0; i < 25; i++) {
      const cube = new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.5, 0.5), new THREE.MeshStandardMaterial({ color: new THREE.Color().setHSL(i / 25, 0.7, 0.55) }));
      cube.position.set((i % 5 - 2) * 0.9, (Math.floor(i / 5) - 2) * 0.9, 0);
      scene.add(cube);
      cubes.push(cube);
    }
    scene.add(new THREE.AmbientLight(0xffffff, 0.5));
    const sun = new THREE.DirectionalLight(0xffffff, 1.2);
    sun.position.set(3, 4, 5);
    scene.add(sun);
    let frames = 0;
    requestAnimationFrame(function tick() { for (const c of cubes) { c.rotation.x += 0.02; c.rotation.y += 0.03; } renderer.render(scene, camera); frames++; requestAnimationFrame(tick); });
    function render() { return '<p>frames ' + frames + '</p>'; }
    </script>
    """
    let script = HTMLScript.make(html: option("--html").flatMap { try? String(contentsOfFile: $0) } ?? html)!
    guard let scene = script.scene3D, let layer = scene.makeLayer() else { print("no 3D session"); exit(1) }
    let window = NSWindow(contentRect: NSRect(x: 0, y: 0, width: 300, height: 300), styleMask: [.borderless], backing: .buffered, defer: false)
    window.ignoresMouseEvents = true
    window.level = .floating
    let host = NSView(frame: NSRect(x: 0, y: 0, width: 300, height: 300))
    host.wantsLayer = true
    window.contentView = host
    window.orderFrontRegardless()
    layer.frame = host.bounds
    layer.contentsScale = window.backingScaleFactor
    layer.drawableSize = CGSize(width: 300 * window.backingScaleFactor, height: 300 * window.backingScaleFactor)
    host.layer?.addSublayer(layer)
    scene.attach(layer, size: CGSize(width: 300, height: 300), scale: window.backingScaleFactor)
    HTMLScript.setDisplay(CGMainDisplayID())
    script.setVisible(true)
    _ = script.currentHTML
    let seconds = Double(option("--seconds") ?? "3") ?? 3
    RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    let stats = scene.stats
    print(String(format: "three (%@): error=%@ frames=%d submitted=%d draws=%d triangles=%d js=%.2f ms/frame gpu=%.2f ms/frame", HTMLScript.runtime is JSCScriptRuntime ? "jsc" : "quickjs", script.errorText ?? "none", stats.frames, stats.submitted, stats.draws, stats.triangles, script.totalMilliseconds / Double(max(1, script.frameCount)), stats.gpuSeconds * 1000 / Double(max(1, stats.frames))))
    if let out = option("--png"), let image = CGWindowListCreateImage(.null, .optionIncludingWindow, CGWindowID(window.windowNumber), [.boundsIgnoreFraming, .bestResolution]) {
        try? NSBitmapImageRep(cgImage: image).representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: out))
    }
    exit(script.errorText == nil && stats.submitted > 0 ? 0 : 1)
case "scripttest":
    final class Probe: HTMLScriptHost {
        var changes = 0
        func scriptDidChange(_ script: HTMLScript) { changes += 1 }
    }
    if option("--runtime") == "jsc" { HTMLScript.runtime = JSCScriptRuntime.shared }
    let cases: [(String, String, Bool)] = [
        ("memory bomb", "<script>let s = ''; setTimeout(() => { s = 'x'.repeat(300 * 1024 * 1024); update(); }, 100); function render() { return '<p>' + s.length + '</p>'; }</script>", true),
        ("infinite loop", "<script>setTimeout(() => { for (;;) {} }, 100); function render() { return '<p>ok</p>'; }</script>", true),
        ("cpu hog throttled", "<script>let n = 0; function spin() { const t = Date.now(); while (Date.now() - t < 30) {} n++; requestAnimationFrame(spin); } requestAnimationFrame(spin); function render() { return '<p>' + n + '</p>'; }</script>", true),
        ("hidden bubble", "<script>let n = 0; setInterval(() => { n++; update(); }, 50); requestAnimationFrame(function f() { n++; requestAnimationFrame(f); }); function render() { return '<p>' + n + '</p>'; }</script>", false),
        ("raf rate", "<script>let n = 0; requestAnimationFrame(function f() { n++; requestAnimationFrame(f); }); function render() { return '<p>frames ' + n + '</p>'; }</script>", true),
        ("action", "<script>let n = 0; function onAction(name) { n++; } function render() { return '<p>' + n + '</p>'; }</script>", true),
        ("action throws", "<script>function onAction(name) { throw new Error('boom ' + name); } function render() { return '<p>alive</p>'; }</script>", true),
    ]
    for (name, html, visible) in cases {
        let probe = Probe()
        let script = HTMLScript.make(html: html)!
        script.host = probe
        script.setVisible(visible)
        _ = script.currentHTML
        if name.hasPrefix("action") { script.performAction("tap", value: nil) }
        let start = Date()
        RunLoop.main.run(until: Date().addingTimeInterval(2.5))
        let html = script.currentHTML.replacingOccurrences(of: "\n", with: " ")
        print(String(format: "%-18@ stopped=%@ changes=%3d cpu=%6.1f ms in %.1fs  %@", name as NSString, script.isStopped ? "yes" : "no ", probe.changes, script.totalMilliseconds, Date().timeIntervalSince(start), String(html.suffix(110)) as NSString))
    }
    exit(0)
case "gpu":
    LHImages.registerDemoImages()
    if option("--space") != "display" { renderSpace = nil }
    guard let renderer = LHGPURenderer(device: nil) else { print("no Metal"); exit(1) }
    let verify = args.contains("--verify")
    let space = renderSpace ?? CGColorSpace(name: CGColorSpace.sRGB)!
    final class TileTarget {
        let surface: IOSurface
        let texture: MTLTexture
        init?(device: MTLDevice, width: Int, height: Int, space: CGColorSpace) {
            guard let surface = IOSurface(properties: [.width: width, .height: height, .bytesPerElement: 4, .pixelFormat: 0x42475241]) else { return nil }
            if let plist = space.copyPropertyList() { IOSurfaceSetValue(unsafeBitCast(surface, to: IOSurfaceRef.self), kIOSurfaceColorSpace, plist) }
            let desc = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: width, height: height, mipmapped: false)
            desc.usage = [.renderTarget, .shaderRead]
            desc.storageMode = .shared
            guard let texture = device.makeTexture(descriptor: desc, iosurface: unsafeBitCast(surface, to: IOSurfaceRef.self), plane: 0) else { return nil }
            self.surface = surface
            self.texture = texture
        }
    }
    var pools: [Int: [TileTarget]] = [:]
    func target(_ index: Int, _ width: Int, _ height: Int, avoid: TileTarget?) -> TileTarget? {
        var pool = (pools[index] ?? []).filter { $0.texture.width == width && $0.texture.height == height }
        if let free = pool.first(where: { $0 !== avoid && !$0.surface.isInUse }) { pools[index] = pool; return free }
        guard let t = TileTarget(device: renderer.device, width: width, height: height, space: space) else { return nil }
        pool.append(t); if pool.count > 3 { pool.removeFirst() }; pools[index] = pool
        return t
    }
    var stats = (frames: 0, unsupported: 0, record: 0.0, encode: 0.0, gpu: 0.0, captured: 0, tiles: 0)
    var check = (tiles: 0, pixels: 0, over8: 0, interiorMax: 0, worstTile: 0.0, structural: 0, worstName: "")
    var peakTextureBytes = 0
    for trace in loadTraces(filter: option("--only")) {
        var previousDocument: LHDocument?
        var previousList: LHDisplayList?
        var previousSize = CGSize.zero
        var hint: CGFloat = 0
        var current: [Int: TileTarget] = [:]
        pools.removeAll()
        for (frameIndex, html) in trace.frames.enumerated() {
            autoreleasepool {
                let c0 = threadCPU()
                let document = LHDocument(html: wrap(html), css: bubbleCSS, previous: previousDocument)
                previousDocument = document
                document.colorSpace = renderSpace
                let size = document.layout(maxWidth: maxWidth, preferredWidth: hint)
                hint = min(size.width, maxWidth)
                let list = document.displayList(maxWidth: maxWidth, gpu: renderer, scale: scale)
                let c1 = threadCPU()
                var rects: [CGRect] = []
                var y: CGFloat = 0
                while y < size.height { rects.append(CGRect(x: 0, y: y, width: size.width, height: min(tileHeight, size.height - y))); y += tileHeight }
                let changed: [CGRect]? = (previousList != nil && previousSize == size) ? list.changedRects(since: previousList!).map { $0.rectValue } : nil
                defer { previousList = list; previousSize = size }
                stats.frames += 1
                if !list.gpuSupported { stats.unsupported += 1; current.removeAll(); return }
                var dirty: [(Int, CGRect)] = []
                for (i, r) in rects.enumerated() {
                    if current[i] == nil || verify || changed == nil || changed!.contains(where: { $0.intersects(r) }) { dirty.append((i, r)) }
                }
                if current.count > rects.count { current = current.filter { $0.key < rects.count } }
                var targets: [TileTarget] = []
                for (i, r) in dirty {
                    guard let t = target(i, Int((r.width * scale).rounded(.up)), Int((r.height * scale).rounded(.up)), avoid: current[i]) else { continue }
                    targets.append(t)
                }
                guard targets.count == dirty.count, let buffer = renderer.queue.makeCommandBuffer() else { return }
                _ = renderer.encode(list, tiles: dirty.map { NSValue(rect: $0.1) }, scale: scale, targets: targets.map { $0.texture }, commandBuffer: buffer)
                let c2 = threadCPU()
                buffer.commit()
                buffer.waitUntilCompleted()
                renderer.endFrame()
                for ((i, _), t) in zip(dirty, targets) { current[i] = t }
                stats.record += Double(c1 - c0) / 1e6
                stats.encode += Double(c2 - c1) / 1e6
                stats.gpu += (buffer.gpuEndTime - buffer.gpuStartTime) * 1000
                stats.captured += Int(renderer.capturedInLastFrame)
                stats.tiles += dirty.count
                peakTextureBytes = max(peakTextureBytes, Int(renderer.textureBytes))
                if verify && (frameIndex % max(1, Int(option("--every") ?? "1") ?? 1) == 0) {
                    for (i, r) in rects.enumerated() {
                        guard let t = current[i], let oracle = document.copyImage(maxWidth: maxWidth, rect: r, scale: scale) else { continue }
                        let w = t.texture.width, h = t.texture.height
                        var want = [UInt8](repeating: 0, count: w * h * 4)
                        let ctx = CGContext(data: &want, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
                        ctx.setBlendMode(.copy)
                        ctx.draw(oracle, in: CGRect(x: 0, y: 0, width: w, height: h))
                        t.surface.lock(options: .readOnly, seed: nil)
                        let base = t.surface.baseAddress.assumingMemoryBound(to: UInt8.self)
                        let stride = t.surface.bytesPerRow
                        var edge = [Bool](repeating: false, count: w * h)
                        for yy in 0 ..< h {
                            for xx in 0 ..< w {
                                let o = (yy * w + xx) * 4
                                for (dx, dy) in [(1, 0), (0, 1)] where xx + dx < w && yy + dy < h {
                                    let n = ((yy + dy) * w + xx + dx) * 4
                                    var m = 0
                                    for c in 0 ..< 4 { m = max(m, abs(Int(want[o + c]) - Int(want[n + c]))) }
                                    if m > 24 {
                                        for ey in max(0, yy - 1) ... min(h - 1, yy + dy + 1) { for ex in max(0, xx - 1) ... min(w - 1, xx + dx + 1) { edge[ey * w + ex] = true } }
                                    }
                                }
                            }
                        }
                        var over8 = 0, structural = 0
                        for yy in 0 ..< h {
                            for xx in 0 ..< w {
                                let o = (yy * w + xx) * 4, g = yy * stride + xx * 4
                                var d = 0
                                for c in 0 ..< 4 { d = max(d, abs(Int(want[o + c]) - Int(base[g + c]))) }
                                if d > 8 { over8 += 1 }
                                if d > 128 { structural += 1 }
                                if !edge[yy * w + xx] && d > check.interiorMax { check.interiorMax = d }
                            }
                        }
                        t.surface.unlock(options: .readOnly, seed: nil)
                        check.tiles += 1
                        check.pixels += w * h
                        check.over8 += over8
                        check.structural += structural
                        let frac = Double(over8) / Double(w * h)
                        if frac > check.worstTile {
                            check.worstTile = frac
                            check.worstName = "\(trace.name) frame \(frameIndex) tile \(i)"
                            if args.contains("--png") {
                                let safe = trace.name.replacingOccurrences(of: "#", with: "_")
                                let rep = NSBitmapImageRep(cgImage: ctx.makeImage()!)
                                try? rep.representation(using: .png, properties: [:])?.write(to: outDir.appendingPathComponent("gpu-\(safe)-want.png"))
                                t.surface.lock(options: .readOnly, seed: nil)
                                let gctx = CGContext(data: t.surface.baseAddress, width: w, height: h, bitsPerComponent: 8, bytesPerRow: t.surface.bytesPerRow, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
                                try? NSBitmapImageRep(cgImage: gctx.makeImage()!).representation(using: .png, properties: [:])?.write(to: outDir.appendingPathComponent("gpu-\(safe)-got.png"))
                                t.surface.unlock(options: .readOnly, seed: nil)
                            }
                        }
                    }
                }
            }
        }
    }
    let f = Double(max(stats.frames - stats.unsupported, 1))
    print(String(format: "GPU engine: %d frames (%d fell back to CPU), CPU per frame: record %.3f + encode %.3f = %.3f ms; GPU %.3f ms; ops captured %.1f/frame; tiles %.1f/frame; peak texture cache %.1f MB", stats.frames, stats.unsupported, stats.record / f, stats.encode / f, (stats.record + stats.encode) / f, stats.gpu / f, Double(stats.captured) / f, Double(stats.tiles) / f, Double(peakTextureBytes) / 1048576))
    if verify {
        print(String(format: "VERIFY %d tiles: interior max diff %d; pixels off by >8: %.3f%% (worst tile %.2f%% at %@); structural (>128): %d", check.tiles, check.interiorMax, 100 * Double(check.over8) / Double(max(check.pixels, 1)), 100 * check.worstTile, check.worstName as NSString, check.structural))
    }
    exit(0)
case "clipcheck":
    LHImages.registerDemoImages()
    var rng = SystemRandomNumberGenerator()
    var checked = 0, differ = 0
    var worst: [String: Int] = [:]
    for trace in loadTraces(filter: option("--only")) {
        for html in trace.frames.prefix(6) {
            autoreleasepool {
                let document = LHDocument(html: wrap(html), css: bubbleCSS)
                let size = document.layout(maxWidth: maxWidth, preferredWidth: 0)
                var y: CGFloat = 0
                while y < size.height {
                    let rect = CGRect(x: 0, y: y, width: size.width, height: min(tileHeight, size.height - y))
                    if let full = document.copyImage(maxWidth: maxWidth, rect: rect, scale: scale) {
                        for _ in 0 ..< 3 {
                            let w = CGFloat.random(in: 4 ... rect.width, using: &rng), h = CGFloat.random(in: 4 ... rect.height, using: &rng)
                            let dirty = CGRect(x: CGFloat.random(in: 0 ... rect.width - w, using: &rng), y: rect.minY + CGFloat.random(in: 0 ... rect.height - h, using: &rng), width: w, height: h)
                            if let partial = document.copyImage(maxWidth: maxWidth, rect: rect, scale: scale, base: full, dirty: dirty) {
                                let a = Bitmap(size: rect.size), b = Bitmap(size: rect.size)
                                a.draw(full, rect: CGRect(origin: .zero, size: rect.size), totalHeight: rect.height)
                                b.draw(partial, rect: CGRect(origin: .zero, size: rect.size), totalHeight: rect.height)
                                checked += 1
                                let d = diffPixels(a, b)
                                if d != 0, args.contains("--where") {
                                    let pa = a.bytes.bindMemory(to: UInt32.self), pb = b.bytes.bindMemory(to: UInt32.self)
                                    let dx0 = Int((dirty.minX * scale).rounded(.down)), dx1 = Int((dirty.maxX * scale).rounded(.up))
                                    let dy0 = Int(((dirty.minY - rect.minY) * scale).rounded(.down)), dy1 = Int(((dirty.maxY - rect.minY) * scale).rounded(.up))
                                    for i in 0 ..< pa.count where pa[i] != pb[i] {
                                        let x = i % a.width, y = i / a.width
                                        let edge = min(x - dx0, dx1 - 1 - x, y - dy0, dy1 - 1 - y)
                                        print("  px (\(x),\(y)) patch [\(dx0),\(dy0))-(\(dx1),\(dy1)) distance-to-edge \(edge)")
                                    }
                                }
                                if d != 0 {
                                    differ += 1
                                    worst[trace.name, default: 0] += 1
                                    if worst[trace.name] == 1 {
                                        let safe = trace.name.replacingOccurrences(of: "#", with: "_")
                                        a.save(outDir.appendingPathComponent("clip-\(safe)-want.png"))
                                        b.save(outDir.appendingPathComponent("clip-\(safe)-got.png"))
                                        print("\(trace.name) tile y=\(rect.minY) dirty=\(dirty.offsetBy(dx: 0, dy: -rect.minY)) diff=\(d)")
                                    }
                                }
                            }
                        }
                    }
                    y += tileHeight
                }
            }
        }
    }
    print("\(checked) partial redraws, \(differ) differ from the full tile")
    for (k, v) in worst.sorted(by: { $0.value > $1.value }).prefix(12) { print("  \(k): \(v)") }
case "check-wrap":
    var n = 0, bad = 0
    var samples = loadTraces(filter: nil).flatMap { $0.frames }
    samples += ["<BODY x>", "<BoDy>", "<bod", "", "<body", "x<b ody", "\u{212A}<body"]
    for f in samples {
        n += 1
        if (f.range(of: "<body", options: .caseInsensitive) != nil) != containsBodyTag(f) { bad += 1 }
    }
    print("\(n) strings, \(bad) disagreements")
case "compare":
    compare(Array(args)[1], Array(args)[2])
default:
    print("usage: LiteHTMLLab record [--seconds 4] [--only name] | bench [--label x] [--verify] [--png] [--only name] [--frames N] | compare <labelA> <labelB>")
}
