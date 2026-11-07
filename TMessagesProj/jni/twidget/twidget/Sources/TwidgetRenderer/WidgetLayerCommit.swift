import QuartzCore
import Foundation

public enum WidgetLayerCommit {
    public static var enabled = ProcessInfo.processInfo.environment["TW_DEFERRED_STRUCTURE"] == "1"
    private static let lock = NSLock()
    private static var pending: [() -> Void] = []
    private static var scheduled = false
    public private(set) static var flushes = 0
    public private(set) static var applied = 0

    static func perform(_ block: @escaping () -> Void) {
        if !enabled || Thread.isMainThread {
            block()
            return
        }
        lock.lock()
        pending.append(block)
        let start = !scheduled
        scheduled = true
        lock.unlock()
        if start {
            DispatchQueue.main.async {
                flush()
            }
        }
    }

    public static func flush() {
        lock.lock()
        let blocks = pending
        pending.removeAll()
        scheduled = false
        lock.unlock()
        if blocks.isEmpty {
            return
        }
        flushes += 1
        applied += blocks.count
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for block in blocks {
            block()
        }
        CATransaction.commit()
    }
}

extension CALayer {
    func twAddSublayer(_ layer: CALayer) {
        WidgetLayerCommit.perform { self.addSublayer(layer) }
    }

    func twInsertSublayer(_ layer: CALayer, at index: UInt32) {
        WidgetLayerCommit.perform { self.insertSublayer(layer, at: min(index, UInt32(self.sublayers?.count ?? 0))) }
    }

    func twInsertSublayer(_ layer: CALayer, below sibling: CALayer?) {
        WidgetLayerCommit.perform {
            if let sibling = sibling, sibling.superlayer === self {
                self.insertSublayer(layer, below: sibling)
            } else {
                self.addSublayer(layer)
            }
        }
    }

    func twRemoveFromSuperlayer() {
        WidgetLayerCommit.perform { self.removeFromSuperlayer() }
    }
}

extension CALayer {
    var twSublayers: [CALayer]? {
        CATransaction.lock()
        defer { CATransaction.unlock() }
        return sublayers
    }
}

enum WidgetSurfaceImage {
    static let imageKey = "twImage"

    static func image(of layer: CALayer) -> Any? {
        if let contents = layer.contents, CFGetTypeID(contents as CFTypeRef) == CGImage.typeID {
            return contents
        }
        guard layer.contents != nil else {
            return nil
        }
        return layer.value(forKey: imageKey)
    }
    static let enabled = ProcessInfo.processInfo.environment["TW_IMAGE_SURFACES"] != "0"

    static let smallPixels = ProcessInfo.processInfo.environment["TW_IMAGE_SURFACE_MIN"].flatMap { Int($0) } ?? Int.max

    static func contents(_ image: CGImage, display: CGColorSpace?) -> Any {
        guard enabled, !Thread.isMainThread else {
            return image
        }
        if image.width * image.height < smallPixels {
            guard let display = display else {
                return image
            }
            return converted(image, to: display) ?? image
        }
        return surface(image) ?? image
    }

    static func converted(_ image: CGImage, to space: CGColorSpace) -> CGImage? {
        if let source = image.colorSpace, CFEqual(source, space) {
            return image
        }
        guard let context = CGContext(data: nil, width: image.width, height: image.height, bitsPerComponent: 8, bytesPerRow: 0, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return nil
        }
        context.setBlendMode(.copy)
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return context.makeImage()
    }

    static func surface(_ image: CGImage) -> IOSurfaceRef? {
        let width = image.width, height = image.height
        guard width > 0, height > 0, width * height <= 4096 * 4096, let surface = WidgetSurfacePool.make(width: width, height: height) else {
            return nil
        }
        let space = image.colorSpace ?? WidgetDocumentView.surfaceSpace
        if let plist = space.copyPropertyList() {
            IOSurfaceSetValue(surface, kIOSurfaceColorSpace, plist)
        }
        IOSurfaceLock(surface, [], nil)
        defer { IOSurfaceUnlock(surface, [], nil) }
        guard let context = CGContext(data: IOSurfaceGetBaseAddress(surface), width: width, height: height, bitsPerComponent: 8, bytesPerRow: IOSurfaceGetBytesPerRow(surface), space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
            return nil
        }
        context.setBlendMode(.copy)
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        context.flush()
        return surface
    }
}
