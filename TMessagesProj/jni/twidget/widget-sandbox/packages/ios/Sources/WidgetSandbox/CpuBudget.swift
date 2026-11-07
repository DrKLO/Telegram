// CPU quota policy: a line-for-line mirror of src/host/cpu-budget.ts (see the
// comments there). A sliding window of `cpuQuotaWindowMs` in ten buckets; a
// widget may spend `cpuQuotaMs` of JS time per window, beyond that deferrable
// tasks are postponed until the oldest busy bucket leaves the window — never
// dropped or killed. Events always run. Every task gets the whole `cpuLimitMs`
// slice; what it spends past the quota is a debt (at most one more quota) that
// enters the window as its buckets leave.
//
// The glue keeps two of them (docs/decisions.md, "Strict limits from the start"):
// the JS quota (`cpuQuotaMs`, JS time only) and the main-thread window
// (`mainThreadQuotaMs`): the host's own work for the widget, its layout queries
// and the modelled cost of the ops it forwards (`opsCostMs`). A deferrable task
// waits while either window is spent.
//
// Pure and side-effect free; checked against tests/fixtures/cpu-budget.json
// (copied to Tests/WidgetSandboxTests/Fixtures by `pnpm build:ios`).

import Foundation

public enum CpuDecision: Equatable {
    case run(sliceMs: Double)
    case defer_(retryAt: Double)
}

public struct CpuBudget {
    public static let buckets = 10
    /// Kinds that may be postponed. `boot`, `event` and `visibility` always run.
    public static let deferrable: Set<String> = ["timer", "frame", "viewport", "resize", "theme", "bridge"]
    /// Layout queries the host may answer in one task (cached answers are free), whatever its slice (boot included).
    public static let queryLimit = 128
    /// What a task's layout queries may block the host in all: the hang guard behind the count.
    public static let queryBudgetMs = 1000.0
    /// Whether a task with `queries` answered layout queries that blocked the host `queryMs` in all is past its limit
    /// (cpu-budget.ts `queryLimitHit`): `"count"`, `"time"` or nil; past either the task is interrupted and killed.
    public static func queryLimitHit(queries: Int, queryMs: Double) -> String? {
        if queries > queryLimit { return "count" }
        if queryMs > queryBudgetMs { return "time" }
        return nil
    }
    /// The reason a task stopped by `queryLimitHit` is killed with (cpu-budget.ts `queryLimitReason`).
    public static func queryLimitReason(_ hit: String, kind: String) -> String {
        hit == "count"
            ? "Layout query limit exceeded: more than \(queryLimit) layout queries in one \(kind) task"
            : "Layout query limit exceeded: host queries blocked the renderer for more than \(Int(queryBudgetMs)) ms in one \(kind) task"
    }

    /// Modelled main-thread cost of one op the glue forwards to the validator, and of one canvas command in it.
    public static let opCostMs = 0.025
    public static let canvasCommandCostMs = 0.002
    /// What a batch of `ops` ops with `canvasCommands` canvas commands costs the host's main thread: deterministic, from what the widget sent.
    public static func opsCostMs(ops: Int, canvasCommands: Int) -> Double {
        Double(ops) * opCostMs + Double(canvasCommands) * canvasCommandCostMs
    }

    private let cpuLimitMs: Double
    private let cpuQuotaMs: Double
    private let windowMs: Double
    private let cpuKillAfterMs: Double
    private let bucketMs: Double
    private var starts = [Double](repeating: -.infinity, count: CpuBudget.buckets)
    private var ms = [Double](repeating: 0, count: CpuBudget.buckets)
    /// Time spent past the quota, not in the window yet: at most one quota.
    private var debtMs = 0.0
    /// The last bucket the debt was settled at.
    private var settled = Int.min
    private var throttledSince = -1.0
    private var lastThrottleAt = -1.0

    public init(cpuLimitMs: Double, cpuQuotaMs: Double, cpuQuotaWindowMs: Double, cpuKillAfterMs: Double) {
        self.cpuLimitMs = cpuLimitMs
        self.cpuQuotaMs = cpuQuotaMs
        windowMs = cpuQuotaWindowMs
        self.cpuKillAfterMs = cpuKillAfterMs
        bucketMs = max(1, cpuQuotaWindowMs / Double(CpuBudget.buckets))
    }

    public init(config: SandboxConfig) {
        self.init(cpuLimitMs: config.cpuLimitMs, cpuQuotaMs: config.cpuQuotaMs, cpuQuotaWindowMs: config.cpuQuotaWindowMs, cpuKillAfterMs: config.cpuKillAfterMs)
    }

    private func bucketIndex(_ now: Double) -> Int { Int(floor(now / bucketMs)) }
    private func oldestStart(_ now: Double) -> Double { Double(bucketIndex(now) - CpuBudget.buckets + 1) * bucketMs }

    /// Bucket `b`'s slot, emptied when it still holds an older bucket.
    private mutating func slot(_ b: Int) -> Int {
        let i = ((b % CpuBudget.buckets) + CpuBudget.buckets) % CpuBudget.buckets
        let start = Double(b) * bucketMs
        if starts[i] != start { starts[i] = start; ms[i] = 0 }
        return i
    }

    /// Time in the window that ends with bucket `b`.
    private func windowAt(_ b: Int) -> Double {
        let oldest = Double(b - CpuBudget.buckets + 1) * bucketMs
        var sum = 0.0
        for i in 0..<CpuBudget.buckets where starts[i] >= oldest { sum += ms[i] }
        return sum
    }

    /// Moves the debt into the window, bucket by bucket since the last call, as the buckets that filled it leave.
    private mutating func settle(_ now: Double) {
        let cur = bucketIndex(now)
        if debtMs > 0 {
            // At most one quota: the window has room for all of it within one window, for any float residue within two.
            let last = min(cur, settled + 2 * CpuBudget.buckets)
            var b = settled + 1
            while b <= last && debtMs > 0 {
                let pay = min(debtMs, cpuQuotaMs - windowAt(b))
                if pay > 0 { let i = slot(b); ms[i] += pay; debtMs -= pay }
                b += 1
            }
            if last < cur { debtMs = 0 }
        }
        if cur > settled { settled = cur }
    }

    /// Account `cpuMs` of widget JS time for a task that ended at `now` (ms): into the window up to the quota, the rest is the debt.
    public mutating func record(_ kind: String, cpuMs: Double, now: Double) {
        settle(now)
        let b = bucketIndex(now)
        let i = slot(b)
        if !(cpuMs > 0) { return } // NaN and negatives are ignored
        let into = min(cpuMs, max(0, cpuQuotaMs - windowAt(b) - debtMs))
        ms[i] += into
        debtMs = min(cpuQuotaMs, debtMs + cpuMs - into)
    }

    /// JS time charged now: the window ending now plus the debt.
    public mutating func used(_ now: Double) -> Double {
        settle(now)
        return windowAt(bucketIndex(now)) + debtMs
    }

    /// Before a task: run it with the whole slice for the interrupt handler, or retry at `retryAt`.
    public mutating func decide(_ kind: String, now: Double) -> CpuDecision {
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
        return .run(sliceMs: cpuLimitMs)
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
