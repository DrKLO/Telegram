import Foundation
import IOSurface

public final class WidgetSurfacePool {
    public static let shared = WidgetSurfacePool()

    private struct Key: Hashable {
        let width: Int
        let height: Int
    }

    public var perSize = 4
    public var maxBytes = 16 << 20
    public private(set) var hits = 0, misses = 0, created = 0, recycled = 0

    private let lock = NSLock()
    private let queue = DispatchQueue(label: "twidget.surface-pool", qos: .utility)
    private var idle: [Key: [IOSurfaceRef]] = [:]
    private var bytes = 0
    private var hot: [Key] = []
    private var refilling = Set<Key>()
    private var pressure: DispatchSourceMemoryPressure?

    init(observesMemoryPressure: Bool = true) {
        guard observesMemoryPressure else {
            return
        }
        let source = DispatchSource.makeMemoryPressureSource(eventMask: [.warning, .critical], queue: queue)
        source.setEventHandler { [weak self] in
            self?.purge()
        }
        source.resume()
        pressure = source
    }

    public var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return idle.values.reduce(0) { $0 + $1.count }
    }

    public func take(width: Int, height: Int) -> IOSurfaceRef? {
        let key = Key(width: width, height: height)
        lock.lock()
        var surface: IOSurfaceRef?
        if var list = idle[key], let index = list.lastIndex(where: { !IOSurfaceIsInUse($0) }) {
            surface = list.remove(at: index)
            idle[key] = list
        }
        if surface != nil {
            bytes -= WidgetSurfacePool.size(of: key)
            hits += 1
        } else {
            misses += 1
        }
        if let index = hot.firstIndex(of: key) {
            hot.remove(at: index)
        }
        hot.append(key)
        if hot.count > 4 {
            let dropped = hot.removeFirst()
            if let gone = idle.removeValue(forKey: dropped) {
                bytes -= gone.count * WidgetSurfacePool.size(of: dropped)
            }
        }
        lock.unlock()
        scheduleRefill(key)
        return surface
    }

    public func recycle(_ surface: IOSurfaceRef) {
        let key = Key(width: IOSurfaceGetWidth(surface), height: IOSurfaceGetHeight(surface))
        let size = WidgetSurfacePool.size(of: key)
        lock.lock()
        defer { lock.unlock() }
        guard hot.contains(key), (idle[key]?.count ?? 0) < perSize * 2, bytes + size <= maxBytes else {
            return
        }
        idle[key, default: []].append(surface)
        bytes += size
        recycled += 1
    }

    public func prewarm(width: Int, height: Int, count: Int = 4) {
        let key = Key(width: width, height: height)
        lock.lock()
        if !hot.contains(key) {
            hot.append(key)
        }
        lock.unlock()
        scheduleRefill(key, target: count)
    }

    public func purge() {
        lock.lock()
        let dropped = idle
        idle.removeAll()
        bytes = 0
        lock.unlock()
        _ = dropped
    }

    private func scheduleRefill(_ key: Key, target: Int? = nil) {
        lock.lock()
        if refilling.contains(key) {
            lock.unlock()
            return
        }
        refilling.insert(key)
        lock.unlock()
        queue.async { [self] in
            refill(key, target: target ?? perSize)
        }
    }

    private func refill(_ key: Key, target: Int) {
        defer {
            lock.lock()
            refilling.remove(key)
            lock.unlock()
        }
        let size = WidgetSurfacePool.size(of: key)
        while true {
            lock.lock()
            let enough = !hot.contains(key) || (idle[key]?.count ?? 0) >= min(target, perSize) || bytes + size > maxBytes
            lock.unlock()
            if enough {
                return
            }
            guard let surface = WidgetSurfacePool.make(width: key.width, height: key.height) else {
                return
            }
            lock.lock()
            idle[key, default: []].append(surface)
            bytes += size
            created += 1
            lock.unlock()
        }
    }

    private static func size(of key: Key) -> Int {
        return key.width * key.height * 4
    }

    static func make(width: Int, height: Int) -> IOSurfaceRef? {
        let properties: [CFString: Any] = [kIOSurfaceWidth: width, kIOSurfaceHeight: height, kIOSurfaceBytesPerElement: 4, kIOSurfacePixelFormat: 0x42475241]
        return IOSurfaceCreate(properties as CFDictionary)
    }

    public var status: String {
        lock.lock()
        defer { lock.unlock() }
        return "surfaces \(idle.values.reduce(0) { $0 + $1.count }) (\(bytes >> 10) KB, hits \(hits), misses \(misses), created \(created), recycled \(recycled))"
    }
}
