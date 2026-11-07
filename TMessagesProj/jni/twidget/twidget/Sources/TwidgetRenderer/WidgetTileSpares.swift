import Foundation
import IOSurface

enum WidgetTileSpares {
    final class Reservation {
        private let surface: IOSurfaceRef
        private var transferred = false

        init(_ surface: IOSurfaceRef) {
            self.surface = surface
        }

        func transfer() {
            transferred = true
        }

        deinit {
            if !transferred {
                WidgetTileSpares.release(surface)
                if WidgetDocumentView.recycleSurfaces {
                    WidgetSurfacePool.shared.recycle(surface)
                }
            }
        }
    }

    static let enabled = ProcessInfo.processInfo.environment["TW_NO_TILE_SPARES"] == nil
    static var budgetBytes = (ProcessInfo.processInfo.environment["TW_TILE_SPARE_MB"].flatMap { Int($0) } ?? 32) << 20
    static let streakNeeded = 2
    static let idleSeconds: CFTimeInterval = 0.1
    private static let lock = NSLock()
    private static var bytes = 0
    private static var pressureEpoch = 0
    private static var source: DispatchSourceMemoryPressure?
    public private(set) static var granted = 0
    public private(set) static var reused = 0
    public private(set) static var peakBytes = 0

    static func size(_ surface: IOSurfaceRef) -> Int {
        return IOSurfaceGetAllocSize(surface)
    }

    static func reserve(_ surface: IOSurfaceRef) -> Bool {
        installPressureHandler()
        let size = size(surface)
        lock.lock()
        defer { lock.unlock() }
        guard bytes + size <= budgetBytes else {
            return false
        }
        bytes += size
        granted += 1
        peakBytes = max(peakBytes, bytes)
        return true
    }

    static func release(_ surface: IOSurfaceRef) {
        let size = size(surface)
        lock.lock()
        bytes = max(0, bytes - size)
        lock.unlock()
    }

    static func noteReuse() {
        lock.lock()
        reused += 1
        lock.unlock()
    }

    static var epoch: Int {
        lock.lock()
        defer { lock.unlock() }
        return pressureEpoch
    }

    static var currentBytes: Int {
        lock.lock()
        defer { lock.unlock() }
        return bytes
    }

    static func simulatePressure() {
        lock.lock()
        pressureEpoch += 1
        lock.unlock()
    }

    private static func installPressureHandler() {
        lock.lock()
        defer { lock.unlock() }
        if source != nil {
            return
        }
        let made = DispatchSource.makeMemoryPressureSource(eventMask: [.warning, .critical], queue: .global(qos: .utility))
        made.setEventHandler {
            simulatePressure()
        }
        made.resume()
        source = made
    }
}
