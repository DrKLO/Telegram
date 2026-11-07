// CPU quota policy: a line-for-line mirror of src/host/cpu-budget.ts (see the
// comments there), the fourth copy next to CpuBudget.swift and CpuBudget.kt.
// A sliding window of `cpuQuotaWindowMs` in ten buckets; a widget may spend
// `cpuQuotaMs` of JS time per window, beyond that deferrable tasks are
// postponed until the oldest busy bucket leaves the window — never dropped or
// killed. Events always run. Every task gets the whole `cpuLimitMs` slice; what
// it spends past the quota is a debt (at most one more quota) that enters the
// window as its buckets leave.
//
// The engine keeps two of them (docs/decisions.md, "Strict limits from the start"):
// the JS quota (`cpuQuotaMs`, JS time only) and the main-thread window
// (`mainThreadQuotaMs`): the host's own work for the widget, its layout queries
// and the modelled cost of the ops it forwards (`opsCostMs`). A deferrable task
// waits while either window is spent.
//
// Pure and side-effect free; checked against tests/fixtures/cpu-budget.json
// (copied to desktop/tests/fixtures by `pnpm build:desktop`).
#pragma once

#include <array>
#include <algorithm>
#include <cmath>
#include <string>
#include <string_view>

namespace twidget {

struct CpuDecision {
    bool run;
    double sliceMs; // when run
    double retryAt; // when deferred
    bool operator==(const CpuDecision& o) const { return run == o.run && (run ? sliceMs == o.sliceMs : retryAt == o.retryAt); }
};

class CpuBudget {
public:
    static constexpr int kBuckets = 10;
    /// Kinds that may be postponed. `boot`, `event` and `visibility` always run.
    static bool deferrable(std::string_view kind);
    /// Layout queries the host may answer in one task (cached answers are free), whatever its slice (boot included).
    static constexpr int kQueryLimit = 128;
    /// What a task's layout queries may block the host in all: the hang guard behind the count.
    static constexpr double kQueryBudgetMs = 1000;
    /// Whether a task with `queries` answered layout queries that blocked the host `queryMs` in all is past its limit
    /// (cpu-budget.ts `queryLimitHit`): "count", "time" or "" (none); past either the task is interrupted and killed.
    static std::string_view queryLimitHit(int queries, double queryMs) {
        if (queries > kQueryLimit) return "count";
        if (queryMs > kQueryBudgetMs) return "time";
        return "";
    }
    /// The reason a task stopped by `queryLimitHit` is killed with (cpu-budget.ts `queryLimitReason`).
    static std::string queryLimitReason(std::string_view hit, const std::string& kind) {
        return hit == "count"
            ? "Layout query limit exceeded: more than " + std::to_string(kQueryLimit) + " layout queries in one " + kind + " task"
            : "Layout query limit exceeded: host queries blocked the renderer for more than " + std::to_string(int(kQueryBudgetMs)) + " ms in one " + kind + " task";
    }
    /// Modelled main-thread cost of one op the glue forwards to the validator, and of one canvas command in it.
    static constexpr double kOpCostMs = 0.025;
    static constexpr double kCanvasCommandCostMs = 0.002;
    /// What a batch of `ops` ops with `canvasCommands` canvas commands costs the host's main thread (cpu-budget.ts
    /// `opsCostMs`): deterministic, from what the widget sent.
    static double opsCostMs(double ops, double canvasCommands) { return ops * kOpCostMs + canvasCommands * kCanvasCommandCostMs; }

    CpuBudget(double cpuLimitMs, double cpuQuotaMs, double cpuQuotaWindowMs, double cpuKillAfterMs);

    /// Account `cpuMs` of widget JS time for a task that ended at `now`: into the window up to the quota, the rest is the debt.
    void record(std::string_view kind, double cpuMs, double now);
    /// JS time charged now: the window ending now plus the debt.
    double used(double now);
    /// Before a task: run it with the whole slice for the interrupt handler, or retry at `retryAt`.
    CpuDecision decide(std::string_view kind, double now);
    /// Length of the current uninterrupted throttling stretch (0 when not throttled).
    double throttledFor(double now) const;
    /// `cpuKillAfterMs` (0 = never): kill the runtime once throttling has lasted this long.
    bool shouldKill(double now) const;

private:
    double oldestStart(double now) const;
    /// Bucket `b`'s slot, emptied when it still holds an older bucket.
    int slot(double b);
    /// Time in the window that ends with bucket `b`.
    double windowAt(double b) const;
    /// Moves the debt into the window, bucket by bucket since the last call, as the buckets that filled it leave.
    void settle(double now);

    double cpuLimitMs_, cpuQuotaMs_, windowMs_, cpuKillAfterMs_, bucketMs_;
    std::array<double, kBuckets> starts_{};
    std::array<double, kBuckets> ms_{};
    double debtMs_ = 0;          // time spent past the quota, not in the window yet: at most one quota
    double settled_ = -INFINITY; // the last bucket the debt was settled at
    double throttledSince_ = -1;
    double lastThrottleAt_ = -1;
};

} // namespace twidget
