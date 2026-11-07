import CoreGraphics
import Foundation
#if canImport(IOSurface)
import IOSurface
#endif

protocol WidgetCanvasBackend: AnyObject {
    func begin(_ stream: CanvasStream, canvas: WidgetCanvas) -> Bool
    func draw(_ name: CanvasName, _ args: ArraySlice<CanvasArg>, canvas: WidgetCanvas) -> Bool
    func commit(canvas: WidgetCanvas)
    func finish()
    func resized(canvas: WidgetCanvas)
}

final class WidgetCanvasSurface {
    static let useSurfaces = ProcessInfo.processInfo.environment["TW_CANVAS_SURFACE"] != "0"

    let width: Int
    let height: Int
    let context: CGContext
    let bytesPerRow: Int
    #if canImport(IOSurface)
    let surface: IOSurfaceRef

    init?(width: Int, height: Int, space: CGColorSpace) {
        guard width > 0, height > 0, width <= 8192, height <= 8192 else {
            return nil
        }
        let properties: [CFString: Any] = [kIOSurfaceWidth: width, kIOSurfaceHeight: height, kIOSurfaceBytesPerElement: 4, kIOSurfacePixelFormat: 0x42475241]
        guard let surface = IOSurfaceCreate(properties as CFDictionary) else {
            return nil
        }
        if let plist = space.copyPropertyList() {
            IOSurfaceSetValue(surface, kIOSurfaceColorSpace, plist)
        }
        IOSurfaceLock(surface, [], nil)
        let base = IOSurfaceGetBaseAddress(surface)
        let row = IOSurfaceGetBytesPerRow(surface)
        memset(base, 0, row * height)
        let context = CGContext(data: base, width: width, height: height, bitsPerComponent: 8, bytesPerRow: row, space: space, bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue)
        IOSurfaceUnlock(surface, [], nil)
        guard let made = context else {
            return nil
        }
        self.surface = surface
        self.context = made
        self.width = width
        self.height = height
        self.bytesPerRow = row
    }

    func lock() {
        IOSurfaceLock(surface, [], nil)
    }

    func unlock() {
        IOSurfaceUnlock(surface, [], nil)
    }
    #else
    init?(width: Int, height: Int, space: CGColorSpace) {
        return nil
    }

    func lock() {}

    func unlock() {}
    #endif
}
