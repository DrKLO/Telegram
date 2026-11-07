import AppKit
import LiteHTML

public enum WidgetCanvasImages {
    public static var fallback: ((String) -> NSImage?)?

    private final class Weak {
        weak var canvas: WidgetCanvas?
        init(_ canvas: WidgetCanvas) {
            self.canvas = canvas
        }
    }

    private static let lock = NSLock()
    private static var canvases: [String: Weak] = [:]
    private static var installed = false

    static var registeredCount: Int {
        lock.lock()
        defer { lock.unlock() }
        return canvases.count
    }

    static func install() {
        lock.lock()
        defer { lock.unlock() }
        if installed {
            return
        }
        installed = true
        let previous = LHImages.provider
        LHImages.setProvider { url in
            if url.hasPrefix("twcanvas:") {
                return WidgetCanvasImages.image(url)
            }
            return previous?(url) ?? WidgetCanvasImages.fallback?(url)
        }
    }

    static func register(_ key: String, _ canvas: WidgetCanvas) {
        lock.lock()
        canvases[key] = Weak(canvas)
        lock.unlock()
    }

    static func unregister(_ key: String) {
        lock.lock()
        canvases[key] = nil
        lock.unlock()
    }

    private static let placeholder: CGImage? = {
        let ctx = CGContext(data: nil, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4, space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        return ctx?.makeImage()
    }()

    private static func image(_ url: String) -> NSImage? {
        let body = url.dropFirst("twcanvas:".count)
        guard let dash = body.lastIndex(of: "-") else {
            return nil
        }
        let tail = body[body.index(after: dash)...]
        let key = String(body[..<dash])
        lock.lock()
        let canvas = canvases[key]?.canvas
        lock.unlock()
        if tail.hasPrefix("p") {
            guard let target = canvas, target.width > 0, target.height > 0, let clear = placeholder else {
                return nil
            }
            return NSImage(cgImage: clear, size: NSSize(width: target.width, height: target.height))
        }
        guard let version = Int(tail), let target = canvas, target.version == version, let image = target.image else {
            return nil
        }
        return NSImage(cgImage: image, size: NSSize(width: target.width, height: target.height))
    }
}
