import Foundation

public enum WidgetTileStats {
    public struct Snapshot {
        public var batches = 0
        public var jobs = 0
        public var setup = 0.0
        public var copy = 0.0
        public var draw = 0.0
        public var area = 0.0
        public var full = 0

        public func delta(since start: Snapshot, frames: Double) -> String {
            let f = max(frames, 1)
            let j = Double(max(jobs - start.jobs, 1))
            return String(format: "tiles: batches/frame %.2f jobs/frame %.2f ms/frame setup %.3f copy %.3f draw %.3f | ms/job setup %.3f copy %.3f draw %.3f | dirty pt2/job %.0f full/frame %.2f",
                          Double(batches - start.batches) / f, Double(jobs - start.jobs) / f,
                          (setup - start.setup) / f, (copy - start.copy) / f, (draw - start.draw) / f,
                          (setup - start.setup) / j, (copy - start.copy) / j, (draw - start.draw) / j, (area - start.area) / j, Double(full - start.full) / f)
        }
    }

    private static let lock = NSLock()
    private static var current = Snapshot()

    public static var snapshot: Snapshot {
        lock.lock()
        defer { lock.unlock() }
        return current
    }

    static func add(_ part: Snapshot) {
        lock.lock()
        current.batches += part.batches
        current.jobs += part.jobs
        current.setup += part.setup
        current.copy += part.copy
        current.draw += part.draw
        current.area += part.area
        current.full += part.full
        lock.unlock()
    }

    static func threadMilliseconds() -> Double {
        var t = timespec()
        clock_gettime(CLOCK_THREAD_CPUTIME_ID, &t)
        return Double(t.tv_sec) * 1000 + Double(t.tv_nsec) / 1_000_000
    }
}
