// CPU quota policy: a line-for-line mirror of src/host/cpu-budget.ts (see the
// comments there) and CpuBudget.swift. A sliding window of `cpuQuotaWindowMs`
// in ten buckets; a widget may spend `cpuQuotaMs` of JS time per window,
// beyond that deferrable tasks are postponed until the oldest busy bucket
// leaves the window — never dropped or killed. Events always run. Every task
// gets the whole `cpuLimitMs` slice; what it spends past the quota is a debt
// (at most one more quota) that enters the window as its buckets leave.
//
// The engine keeps two of them (docs/decisions.md, "Strict limits from the start"):
// the JS quota (`cpuQuotaMs`, JS time only) and the main-thread window
// (`mainThreadQuotaMs`): the host's own work for the widget, its layout queries
// and the modelled cost of the ops it forwards ([opsCostMs]). A deferrable task
// waits while either window is spent.
//
// Pure and side-effect free; checked against tests/fixtures/cpu-budget.json
// (copied to src/androidTest/assets by `pnpm build:android`).

package org.telegram.widgetsandbox

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

sealed class CpuDecision {
    data class Run(val sliceMs: Double) : CpuDecision()
    data class Defer(val retryAt: Double) : CpuDecision()
}

class CpuBudget(
    private val cpuLimitMs: Double,
    private val cpuQuotaMs: Double,
    private val windowMs: Double,
    private val cpuKillAfterMs: Double,
) {
    companion object {
        const val BUCKETS = 10
        /** Kinds that may be postponed. `boot`, `event` and `visibility` always run. */
        val DEFERRABLE = setOf("timer", "frame", "viewport", "resize", "theme", "bridge")
        /** Layout queries the host may answer in one task (cached answers are free), whatever its slice (boot included). */
        const val QUERY_LIMIT = 128
        /** What a task's layout queries may block the host in all: the hang guard behind the count. */
        const val QUERY_BUDGET_MS = 1000.0
        /** Whether a task with [queries] answered layout queries that blocked the host [queryMs] in all is past its limit (cpu-budget.ts `queryLimitHit`): `"count"`, `"time"` or null; the host answers queries synchronously, so a forced-layout loop would hold the renderer hostage without ever burning widget CPU, and past either the task is interrupted and killed. */
        fun queryLimitHit(queries: Int, queryMs: Double): String? = when {
            queries > QUERY_LIMIT -> "count"
            queryMs > QUERY_BUDGET_MS -> "time"
            else -> null
        }
        /** The reason a task stopped by [queryLimitHit] is killed with (cpu-budget.ts `queryLimitReason`). */
        fun queryLimitReason(hit: String, kind: String): String =
            if (hit == "count") "Layout query limit exceeded: more than $QUERY_LIMIT layout queries in one $kind task"
            else "Layout query limit exceeded: host queries blocked the renderer for more than ${QUERY_BUDGET_MS.toInt()} ms in one $kind task"

        /** Modelled main-thread cost of one op the engine forwards to the validator, and of one canvas command in it. */
        const val OP_COST_MS = 0.025
        const val CANVAS_COMMAND_COST_MS = 0.002
        /** What a batch of [ops] ops with [canvasCommands] canvas commands costs the host's main thread (cpu-budget.ts `opsCostMs`): deterministic, from what the widget sent. */
        fun opsCostMs(ops: Int, canvasCommands: Int): Double = ops * OP_COST_MS + canvasCommands * CANVAS_COMMAND_COST_MS
    }

    constructor(config: SandboxConfig) : this(config.cpuLimitMs, config.cpuQuotaMs, config.cpuQuotaWindowMs, config.cpuKillAfterMs)

    private val bucketMs = max(1.0, windowMs / BUCKETS)
    private val starts = DoubleArray(BUCKETS) { Double.NEGATIVE_INFINITY }
    private val ms = DoubleArray(BUCKETS)
    /** Time spent past the quota, not in the window yet: at most one quota. */
    private var debtMs = 0.0
    /** The last bucket the debt was settled at. */
    private var settled = Long.MIN_VALUE
    private var throttledSince = -1.0
    private var lastThrottleAt = -1.0

    private fun bucketIndex(now: Double): Long = floor(now / bucketMs).toLong()
    private fun oldestStart(now: Double): Double = (bucketIndex(now) - BUCKETS + 1) * bucketMs

    /** Bucket [b]'s slot, emptied when it still holds an older bucket. */
    private fun slot(b: Long): Int {
        val i = (((b % BUCKETS) + BUCKETS) % BUCKETS).toInt()
        val start = b * bucketMs
        if (starts[i] != start) { starts[i] = start; ms[i] = 0.0 }
        return i
    }

    /** Time in the window that ends with bucket [b]. */
    private fun windowAt(b: Long): Double {
        val oldest = (b - BUCKETS + 1) * bucketMs
        var sum = 0.0
        for (i in 0 until BUCKETS) if (starts[i] >= oldest) sum += ms[i]
        return sum
    }

    /** Moves the debt into the window, bucket by bucket since the last call, as the buckets that filled it leave. */
    private fun settle(now: Double) {
        val cur = bucketIndex(now)
        if (debtMs > 0) {
            // At most one quota: the window has room for all of it within one window, for any float residue within two.
            val last = min(cur, settled + 2 * BUCKETS)
            var b = settled + 1
            while (b <= last && debtMs > 0) {
                val pay = min(debtMs, cpuQuotaMs - windowAt(b))
                if (pay > 0) { ms[slot(b)] += pay; debtMs -= pay }
                b++
            }
            if (last < cur) debtMs = 0.0
        }
        if (cur > settled) settled = cur
    }

    /** Account `cpuMs` of widget JS time for a task that ended at `now` (ms): into the window up to the quota, the rest is the debt. */
    fun record(@Suppress("UNUSED_PARAMETER") kind: String, cpuMs: Double, now: Double) {
        settle(now)
        val b = bucketIndex(now)
        val i = slot(b)
        if (!(cpuMs > 0)) return // NaN and negatives are ignored
        val into = min(cpuMs, max(0.0, cpuQuotaMs - windowAt(b) - debtMs))
        ms[i] += into
        debtMs = min(cpuQuotaMs, debtMs + cpuMs - into)
    }

    /** JS time charged now: the window ending now plus the debt. */
    fun used(now: Double): Double {
        settle(now)
        return windowAt(bucketIndex(now)) + debtMs
    }

    /** Before a task: run it with the whole slice for the interrupt handler, or retry at `retryAt`. */
    fun decide(kind: String, now: Double): CpuDecision {
        val remaining = cpuQuotaMs - used(now)
        if (kind in DEFERRABLE && remaining <= 0) {
            // A throttling stretch continues while deferrals are at most one window apart.
            if (throttledSince < 0 || now - lastThrottleAt > windowMs) throttledSince = now
            lastThrottleAt = now
            val oldestBusy = oldestStart(now)
            var oldest = Double.POSITIVE_INFINITY
            for (i in 0 until BUCKETS) if (ms[i] > 0 && starts[i] >= oldestBusy && starts[i] < oldest) oldest = starts[i]
            var retryAt = if (oldest == Double.POSITIVE_INFINITY) now + bucketMs else oldest + windowMs
            if (retryAt <= now) retryAt = now + bucketMs
            return CpuDecision.Defer(retryAt)
        }
        return CpuDecision.Run(cpuLimitMs)
    }

    /** Length of the current uninterrupted throttling stretch (0 when not throttled). */
    fun throttledFor(now: Double): Double {
        if (throttledSince < 0 || now - lastThrottleAt > windowMs) return 0.0
        return now - throttledSince
    }

    /** `cpuKillAfterMs` (0 = never): kill the runtime once throttling has lasted this long. */
    fun shouldKill(now: Double): Boolean = cpuKillAfterMs > 0 && throttledFor(now) >= cpuKillAfterMs
}
