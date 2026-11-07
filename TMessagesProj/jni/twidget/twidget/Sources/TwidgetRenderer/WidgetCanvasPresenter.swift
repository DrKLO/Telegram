import AppKit
import IOSurface
import QuartzCore

final class WidgetCanvasPresenter {
    static let enabled = WidgetCanvasSurface.useSurfaces && ProcessInfo.processInfo.environment["TW_CANVAS_LAYER"] != "0"
    static let maxSurfaces = 3

    let layer = CALayer()
    let container = CALayer()
    private(set) var frame = CGRect.zero
    private(set) var clip: (rect: CGRect, radius: CGFloat, corners: CACornerMask)?
    private var surfaces: [IOSurfaceRef] = []
    private var shown: IOSurfaceRef?
    private var busy = false
    private var dirty = false
    private weak var canvas: WidgetCanvas?
    private(set) var presented = 0
    private(set) var skipped = 0
    var schedule: (@escaping () -> Void) -> Void = { DispatchQueue.main.async(execute: $0) }
    var scheduleAfter: (TimeInterval, @escaping () -> Void) -> Void = { DispatchQueue.main.asyncAfter(deadline: .now() + $0, execute: $1) }

    init(canvas: WidgetCanvas) {
        self.canvas = canvas
        layer.actions = ["contents": NSNull(), "bounds": NSNull(), "position": NSNull(), "frame": NSNull(), "hidden": NSNull()]
        layer.contentsGravity = .resize
        layer.isOpaque = false
        layer.name = "twidget-canvas"
        container.actions = layer.actions
        container.name = "twidget-canvas-clip"
        container.twAddSublayer(layer)
    }

    func place(_ frame: CGRect, clip: (rect: CGRect, radius: CGFloat, corners: CACornerMask)?) {
        self.frame = frame
        self.clip = clip
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        if let clip = clip {
            container.frame = clip.rect
            container.masksToBounds = true
            container.cornerRadius = clip.radius
            if #available(macOS 10.13, *) {
                container.maskedCorners = clip.corners
            }
            layer.frame = frame.offsetBy(dx: -clip.rect.minX, dy: -clip.rect.minY)
        } else {
            container.frame = frame
            container.masksToBounds = false
            container.cornerRadius = 0
            layer.frame = CGRect(origin: .zero, size: frame.size)
        }
        CATransaction.commit()
    }

    func remove() {
        container.twRemoveFromSuperlayer()
        layer.contents = nil
        surfaces = []
        shown = nil
    }

    private func freeSurface(_ canvas: WidgetCanvas) -> IOSurfaceRef? {
        if let first = surfaces.first, IOSurfaceGetWidth(first) != canvas.width || IOSurfaceGetHeight(first) != canvas.height {
            surfaces = []
        }
        for surface in surfaces where surface !== shown && !IOSurfaceIsInUse(surface) {
            return surface
        }
        guard surfaces.count < (canvas.pixelCount > 1 << 20 ? 2 : WidgetCanvasPresenter.maxSurfaces), canvas.width > 0, canvas.height > 0 else {
            return nil
        }
        let properties: [CFString: Any] = [kIOSurfaceWidth: canvas.width, kIOSurfaceHeight: canvas.height, kIOSurfaceBytesPerElement: 4, kIOSurfacePixelFormat: 0x42475241]
        guard let made = IOSurfaceCreate(properties as CFDictionary) else {
            return nil
        }
        if let plist = CGColorSpace(name: CGColorSpace.sRGB)?.copyPropertyList() {
            IOSurfaceSetValue(made, kIOSurfaceColorSpace, plist)
        }
        surfaces.append(made)
        return made
    }

    private func show(_ surface: IOSurfaceRef) {
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        layer.contents = surface
        CATransaction.commit()
        shown = surface
        presented += 1
    }

    func presentNow() {
        guard let canvas = canvas, let storage = canvas.storage else {
            return
        }
        canvas.finishGPU()
        guard let target = freeSurface(canvas) else {
            return
        }
        IOSurfaceLock(target, [], nil)
        storage.lock()
        let rows = min(storage.height, IOSurfaceGetHeight(target))
        let bytes = min(storage.bytesPerRow, IOSurfaceGetBytesPerRow(target))
        let source = IOSurfaceGetBaseAddress(storage.surface), destination = IOSurfaceGetBaseAddress(target)
        let sourceRow = storage.bytesPerRow, destinationRow = IOSurfaceGetBytesPerRow(target)
        if sourceRow == destinationRow {
            memcpy(destination, source, sourceRow * rows)
        } else {
            for y in 0..<rows {
                memcpy(destination + y * destinationRow, source + y * sourceRow, bytes)
            }
        }
        storage.unlock()
        IOSurfaceUnlock(target, [], nil)
        show(target)
    }

    func present() {
        guard let canvas = canvas else {
            return
        }
        if busy {
            dirty = true
            return
        }
        #if canImport(Metal)
        if let gpu = canvas.backend as? WidgetCanvasMetal {
            guard let target = freeSurface(canvas) else {
                skipped += 1
                dirty = true
                scheduleAfter(0.004) { [weak self] in
                    self?.retry()
                }
                return
            }
            busy = true
            dirty = false
            let schedule = self.schedule
            let started = gpu.present(canvas: canvas, to: target) { [weak self] in
                schedule { [weak self] in
                    self?.didPresent(target)
                }
            }
            if !started {
                busy = false
                presentNow()
            }
            return
        }
        #endif
        presentNow()
    }

    private func didPresent(_ target: IOSurfaceRef) {
        busy = false
        if container.superlayer != nil {
            show(target)
        }
        if dirty {
            present()
        }
    }

    private func retry() {
        if dirty && !busy {
            present()
        }
    }

    func draw(into ctx: CGContext) {
        guard let image = canvas?.image else {
            return
        }
        ctx.saveGState()
        if let clip = clip {
            let r = clip.rect, radius = min(clip.radius, r.width / 2, r.height / 2)
            let path = CGMutablePath()
            func corner(_ mask: CACornerMask) -> CGFloat {
                return clip.corners.contains(mask) ? radius : 0
            }
            let tl = corner(.layerMinXMinYCorner), tr = corner(.layerMaxXMinYCorner), br = corner(.layerMaxXMaxYCorner), bl = corner(.layerMinXMaxYCorner)
            path.move(to: CGPoint(x: r.minX + tl, y: r.minY))
            path.addArc(tangent1End: CGPoint(x: r.maxX, y: r.minY), tangent2End: CGPoint(x: r.maxX, y: r.maxY), radius: tr)
            path.addArc(tangent1End: CGPoint(x: r.maxX, y: r.maxY), tangent2End: CGPoint(x: r.minX, y: r.maxY), radius: br)
            path.addArc(tangent1End: CGPoint(x: r.minX, y: r.maxY), tangent2End: CGPoint(x: r.minX, y: r.minY), radius: bl)
            path.addArc(tangent1End: CGPoint(x: r.minX, y: r.minY), tangent2End: CGPoint(x: r.maxX, y: r.minY), radius: tl)
            path.closeSubpath()
            ctx.addPath(path)
            ctx.twClip()
        }
        ctx.translateBy(x: frame.minX, y: frame.maxY)
        ctx.scaleBy(x: 1, y: -1)
        ctx.draw(image, in: CGRect(origin: .zero, size: frame.size))
        ctx.restoreGState()
    }
}
