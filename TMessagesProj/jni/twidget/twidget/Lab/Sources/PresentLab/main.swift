import Cocoa
import Metal
import QuartzCore

let args = CommandLine.arguments.dropFirst()
func option(_ name: String) -> String? {
    guard let i = args.firstIndex(of: name), args.index(after: i) < args.endIndex else { return nil }
    return args[args.index(after: i)]
}
let mode = option("--mode") ?? "metal"
let fps = Double(option("--fps") ?? "120") ?? 120
let seconds = Double(option("--seconds") ?? "8") ?? 8
let tiles = Int(option("--tiles") ?? "4") ?? 4
let scale: CGFloat = 2

func processCPU() -> Double {
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)
    return Double(usage.ru_utime.tv_sec + usage.ru_stime.tv_sec) * 1000 + Double(usage.ru_utime.tv_usec + usage.ru_stime.tv_usec) / 1000
}
func kernelCPU() -> Double {
    var usage = rusage()
    getrusage(RUSAGE_SELF, &usage)
    return Double(usage.ru_stime.tv_sec) * 1000 + Double(usage.ru_stime.tv_usec) / 1000
}

let app = NSApplication.shared
app.setActivationPolicy(.accessory)
let screen = NSScreen.main!.visibleFrame
let window = NSWindow(contentRect: NSRect(x: screen.maxX - 440, y: screen.minY + 20, width: 400, height: 720), styleMask: [.titled], backing: .buffered, defer: false)
window.title = "PresentLab \(mode)"
window.level = .floating
let host = NSView(frame: NSRect(x: 0, y: 0, width: 400, height: 720))
host.wantsLayer = true
host.layer?.isGeometryFlipped = true
window.contentView = host
window.orderFrontRegardless()
let space = window.colorSpace!.cgColorSpace!
let tileSize = CGSize(width: 400, height: 64)

var frame = 0
var step: () -> Void = {}

switch mode {
case "cgimage":
    var layers: [CALayer] = []
    for i in 0 ..< tiles {
        let l = CALayer()
        l.frame = CGRect(x: 0, y: CGFloat(i) * 64, width: 400, height: 64)
        l.contentsScale = scale
        host.layer?.addSublayer(l)
        layers.append(l)
    }
    step = {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for l in layers {
            let ctx = CGContext(data: nil, width: 800, height: 128, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
            ctx.setFillColor(CGColor(red: CGFloat(frame % 100) / 100, green: 0.5, blue: 0.5, alpha: 1))
            ctx.fill(CGRect(x: 0, y: 0, width: 800, height: 128))
            l.contents = ctx.makeImage()
        }
        CATransaction.commit()
    }
case "iosurface":
    var layers: [CALayer] = []
    var pools: [[IOSurface]] = []
    for i in 0 ..< tiles {
        let l = CALayer()
        l.frame = CGRect(x: 0, y: CGFloat(i) * 64, width: 400, height: 64)
        l.contentsScale = scale
        host.layer?.addSublayer(l)
        layers.append(l)
        pools.append((0 ..< 3).map { _ in
            let s = IOSurface(properties: [.width: 800, .height: 128, .bytesPerElement: 4, .pixelFormat: 0x42475241])!
            if let plist = space.copyPropertyList() { IOSurfaceSetValue(unsafeBitCast(s, to: IOSurfaceRef.self), kIOSurfaceColorSpace, plist) }
            return s
        })
    }
    step = {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for (i, l) in layers.enumerated() {
            let s = pools[i].first { !$0.isInUse && $0 !== (l.contents as AnyObject?) } ?? pools[i][frame % 3]
            s.lock(options: [], seed: nil)
            let ctx = CGContext(data: s.baseAddress, width: 800, height: 128, bitsPerComponent: 8, bytesPerRow: s.bytesPerRow, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)!
            ctx.setFillColor(CGColor(red: CGFloat(frame % 100) / 100, green: 0.5, blue: 0.5, alpha: 1))
            ctx.fill(CGRect(x: 0, y: 0, width: 800, height: 128))
            s.unlock(options: [], seed: nil)
            l.contents = s
        }
        CATransaction.commit()
    }
default:
    let device = MTLCreateSystemDefaultDevice()!
    let queue = device.makeCommandQueue()!
    let layer = CAMetalLayer()
    layer.device = device
    layer.pixelFormat = .bgra8Unorm
    layer.framebufferOnly = true
    layer.colorspace = space
    layer.contentsScale = scale
    let height = CGFloat(tiles) * 64
    layer.frame = CGRect(x: 0, y: 0, width: 400, height: height)
    layer.drawableSize = CGSize(width: 800, height: height * scale)
    layer.maximumDrawableCount = Int(option("--drawables") ?? "3") ?? 3
    layer.presentsWithTransaction = args.contains("--with-transaction")
    layer.allowsNextDrawableTimeout = true
    host.layer?.addSublayer(layer)
    step = {
        guard let drawable = layer.nextDrawable(), let buffer = queue.makeCommandBuffer() else { return }
        let pass = MTLRenderPassDescriptor()
        pass.colorAttachments[0].texture = drawable.texture
        pass.colorAttachments[0].loadAction = .clear
        pass.colorAttachments[0].storeAction = .store
        pass.colorAttachments[0].clearColor = MTLClearColor(red: Double(frame % 100) / 100, green: 0.5, blue: 0.5, alpha: 1)
        buffer.makeRenderCommandEncoder(descriptor: pass)?.endEncoding()
        if layer.presentsWithTransaction {
            buffer.commit()
            buffer.waitUntilScheduled()
            drawable.present()
        } else {
            buffer.present(drawable)
            buffer.commit()
        }
    }
}

DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
    let c0 = processCPU(), k0 = kernelCPU()
    let start = Date()
    let timer = Timer(timeInterval: 1 / fps, repeats: true) { t in
        frame += 1
        step()
        if Date().timeIntervalSince(start) > seconds {
            t.invalidate()
            let cpu = processCPU() - c0, k = kernelCPU() - k0
            print(String(format: "%@ (%d tiles of 400x64 pt, %.0f fps): %.3f ms CPU per frame, kernel %.3f, %d frames", mode as NSString, tiles, fps, cpu / Double(frame), k / Double(frame), frame))
            exit(0)
        }
    }
    RunLoop.main.add(timer, forMode: .common)
}
app.run()
