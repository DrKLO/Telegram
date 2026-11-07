// CPU quota policy. A sliding window of `cpuQuotaWindowMs` in ten buckets; a
// script may spend `cpuQuotaMs` of JS time per window, beyond that deferrable
// tasks are postponed until the oldest busy bucket leaves the window, never
// dropped or killed. Events always run with a floor of 50 ms.

import Foundation

public enum CpuDecision: Equatable {
    case run(sliceMs: Double)
    case defer_(retryAt: Double)
}

public struct CpuBudget {
    public static let buckets = 10
    /// Minimum slice any task gets, so user input is handled even with the quota spent.
    public static let floorMs = 50.0
    /// Kinds that may be postponed. `boot`, `event` and `visibility` always run.
    public static let deferrable: Set<String> = ["timer", "frame", "viewport", "resize", "theme", "bridge"]

    private let cpuLimitMs: Double
    private let cpuQuotaMs: Double
    private let windowMs: Double
    private let cpuKillAfterMs: Double
    private let bucketMs: Double
    private var starts = [Double](repeating: -.infinity, count: CpuBudget.buckets)
    private var ms = [Double](repeating: 0, count: CpuBudget.buckets)
    private var throttledSince = -1.0
    private var lastThrottleAt = -1.0

    public init(cpuLimitMs: Double, cpuQuotaMs: Double, cpuQuotaWindowMs: Double, cpuKillAfterMs: Double) {
        self.cpuLimitMs = cpuLimitMs
        self.cpuQuotaMs = cpuQuotaMs
        windowMs = cpuQuotaWindowMs
        self.cpuKillAfterMs = cpuKillAfterMs
        bucketMs = max(1, cpuQuotaWindowMs / Double(CpuBudget.buckets))
    }

    private func bucketIndex(_ now: Double) -> Int { Int(floor(now / bucketMs)) }
    private func oldestStart(_ now: Double) -> Double { Double(bucketIndex(now) - CpuBudget.buckets + 1) * bucketMs }

    /// Account `cpuMs` of script JS time for a task that ended at `now` (ms).
    public mutating func record(_ kind: String, cpuMs: Double, now: Double) {
        let b = bucketIndex(now)
        let i = ((b % CpuBudget.buckets) + CpuBudget.buckets) % CpuBudget.buckets
        let start = Double(b) * bucketMs
        if starts[i] != start { starts[i] = start; ms[i] = 0 }
        if cpuMs > 0 { ms[i] += cpuMs } // NaN and negatives are ignored
    }

    /// JS time used in the window ending now.
    public func used(_ now: Double) -> Double {
        let oldest = oldestStart(now)
        var sum = 0.0
        for i in 0..<CpuBudget.buckets where starts[i] >= oldest { sum += ms[i] }
        return sum
    }

    /// Before a task: run it with this slice for the interrupt handler, or retry at `retryAt`.
    public mutating func decide(_ kind: String, now: Double) -> CpuDecision {
        if kind == "boot" { return .run(sliceMs: cpuLimitMs) }
        let remaining = cpuQuotaMs - used(now)
        if CpuBudget.deferrable.contains(kind), remaining <= 0 {
            // A throttling stretch continues while deferrals are at most one window apart.
            if throttledSince < 0 || now - lastThrottleAt > windowMs { throttledSince = now }
            lastThrottleAt = now
            let oldestBusy = oldestStart(now)
            var oldest = Double.infinity
            for i in 0..<CpuBudget.buckets where ms[i] > 0 && starts[i] >= oldestBusy && starts[i] < oldest { oldest = starts[i] }
            var retryAt = oldest == .infinity ? now + bucketMs : oldest + windowMs
            if retryAt <= now { retryAt = now + bucketMs }
            return .defer_(retryAt: retryAt)
        }
        return .run(sliceMs: min(cpuLimitMs, max(remaining, CpuBudget.floorMs)))
    }

    /// Length of the current uninterrupted throttling stretch (0 when not throttled).
    public func throttledFor(_ now: Double) -> Double {
        if throttledSince < 0 || now - lastThrottleAt > windowMs { return 0 }
        return now - throttledSince
    }

    /// `cpuKillAfterMs` (0 = never): kill the runtime once throttling has lasted this long.
    public func shouldKill(_ now: Double) -> Bool {
        cpuKillAfterMs > 0 && throttledFor(now) >= cpuKillAfterMs
    }
}
