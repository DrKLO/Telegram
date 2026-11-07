// CPU quota policy (docs/decisions.md, Runtime limits): a sliding window of
// `cpuQuotaWindowMs` split into ten buckets; a widget may spend `cpuQuotaMs`
// of JS time per window. Beyond that, deferrable tasks (timers, frames, …) are
// postponed to the moment the oldest busy bucket leaves the window — they are
// never dropped or killed. Events always run.
//
// Every task gets the whole `cpuLimitMs` slice, the hang guard, whatever the
// window holds: a slice cut to the quota left would stop a widget on a slow
// phone that a fast one runs. What a task spends past the quota is a debt (at
// most one more quota) that enters the window as its buckets leave, so the
// quota holds over time.
//
// The glue keeps two of them (docs/decisions.md, "Strict limits from the start"):
// the JS quota (`cpuQuotaMs`, JS time only) and the main-thread window
// (`mainThreadQuotaMs`): the host's own work for the widget, its layout queries
// and the modelled cost of the ops it forwards (`opsCostMs`). A deferrable task
// waits while either window is spent.
//
// Pure and side-effect free: the glue (worker.ts, the Node harness,
// CpuBudget.swift, CpuBudget.kt, cpu_budget.cpp) feeds it `now` and task durations and acts on
// the decisions. All four copies are checked against tests/fixtures/cpu-budget.json.

export type TaskKind = 'boot' | 'event' | 'visibility' | 'timer' | 'frame' | 'viewport' | 'resize' | 'theme' | 'bridge';

export interface CpuBudgetConfig {
  cpuLimitMs: number;
  cpuQuotaMs: number;
  cpuQuotaWindowMs: number;
  cpuKillAfterMs: number;
}

export type Decision =
  | { run: true; sliceMs: number }
  | { run: false; retryAt: number };

export const BUCKETS = 10;
/** Kinds that may be postponed. `boot`, `event` and `visibility` always run. */
export const DEFERRABLE: ReadonlySet<string> = new Set(['timer', 'frame', 'viewport', 'resize', 'theme', 'bridge']);
/** Layout queries the host may answer in one task (cached answers are free), whatever its slice (boot included). */
export const QUERY_LIMIT = 128;
/** What a task's layout queries may block the host in all: the hang guard behind the count. */
export const QUERY_BUDGET_MS = 1000;
/**
 * Whether a task with `queries` answered layout queries that blocked the host `queryMs` in all is past its limit, and
 * which: the host answers queries synchronously, so a forced-layout loop would hold the renderer hostage without ever
 * burning widget CPU; past either the task is interrupted and killed. The count stops the same widget on every device
 * (the time of a query does not: a cold first layout on a slow phone takes over 100 ms); their time also counts toward
 * the main-thread window, which slows the widget down instead.
 */
export function queryLimitHit(queries: number, queryMs: number): 'count' | 'time' | null {
  if (queries > QUERY_LIMIT) return 'count';
  if (queryMs > QUERY_BUDGET_MS) return 'time';
  return null;
}
/** The reason a task stopped by `queryLimitHit` is killed with. */
export function queryLimitReason(hit: 'count' | 'time', kind: string): string {
  return hit === 'count'
    ? `Layout query limit exceeded: more than ${QUERY_LIMIT} layout queries in one ${kind} task`
    : `Layout query limit exceeded: host queries blocked the renderer for more than ${QUERY_BUDGET_MS} ms in one ${kind} task`;
}

/** Modelled main-thread cost of one op the glue forwards to the validator, and of one canvas command in it. */
export const OP_COST_MS = 0.025;
export const CANVAS_COMMAND_COST_MS = 0.002;
/** What a batch of `ops` ops with `canvasCommands` canvas commands costs the host's main thread: deterministic, from what the widget sent. */
export function opsCostMs(ops: number, canvasCommands: number): number {
  return ops * OP_COST_MS + canvasCommands * CANVAS_COMMAND_COST_MS;
}

export class CpuBudget {
  private readonly bucketMs: number;
  private readonly buckets: { start: number; ms: number }[] = [];
  /** Time spent past the quota, not in the window yet: at most one quota. */
  private debtMs = 0;
  /** The last bucket the debt was settled at. */
  private settled = -Infinity;
  private throttledSince = -1;
  private lastThrottleAt = -1;

  constructor(private readonly cfg: CpuBudgetConfig) {
    this.bucketMs = Math.max(1, cfg.cpuQuotaWindowMs / BUCKETS);
    for (let i = 0; i < BUCKETS; i++) this.buckets.push({ start: -Infinity, ms: 0 });
  }

  /** Bucket `b`'s slot, emptied when it still holds an older bucket. */
  private slot(b: number): { start: number; ms: number } {
    const slot = this.buckets[((b % BUCKETS) + BUCKETS) % BUCKETS];
    const start = b * this.bucketMs;
    if (slot.start !== start) { slot.start = start; slot.ms = 0; }
    return slot;
  }

  /** Time in the window that ends with bucket `b` (the ten bucket slots cover exactly one window). */
  private windowAt(b: number): number {
    const oldest = (b - BUCKETS + 1) * this.bucketMs;
    let sum = 0;
    for (const s of this.buckets) if (s.start >= oldest) sum += s.ms;
    return sum;
  }

  /** Moves the debt into the window, bucket by bucket since the last call, as the buckets that filled it leave. */
  private settle(now: number): void {
    const cur = Math.floor(now / this.bucketMs);
    if (this.debtMs > 0) {
      // At most one quota: the window has room for all of it within one window, for any float residue within two.
      const last = Math.min(cur, this.settled + 2 * BUCKETS);
      for (let b = this.settled + 1; b <= last && this.debtMs > 0; b++) {
        const pay = Math.min(this.debtMs, this.cfg.cpuQuotaMs - this.windowAt(b));
        if (pay > 0) { this.slot(b).ms += pay; this.debtMs -= pay; }
      }
      if (last < cur) this.debtMs = 0;
    }
    if (cur > this.settled) this.settled = cur;
  }

  /** Account `cpuMs` of widget JS time for a task that ended at `now`: into the window up to the quota, the rest is the debt. */
  record(_kind: TaskKind | string, cpuMs: number, now: number): void {
    this.settle(now);
    const b = Math.floor(now / this.bucketMs);
    const slot = this.slot(b);
    if (!(cpuMs > 0)) return; // NaN and negatives are ignored
    const into = Math.min(cpuMs, Math.max(0, this.cfg.cpuQuotaMs - this.windowAt(b) - this.debtMs));
    slot.ms += into;
    this.debtMs = Math.min(this.cfg.cpuQuotaMs, this.debtMs + cpuMs - into);
  }

  /** JS time charged now: the window ending now plus the debt. */
  used(now: number): number {
    this.settle(now);
    return this.windowAt(Math.floor(now / this.bucketMs)) + this.debtMs;
  }

  /** Before a task: run it with the whole slice for the interrupt handler, or retry at `retryAt`. */
  decide(kind: TaskKind | string, now: number): Decision {
    const remaining = this.cfg.cpuQuotaMs - this.used(now);
    if (DEFERRABLE.has(kind) && remaining <= 0) {
      // A throttling stretch continues while deferrals are at most one window apart.
      if (this.throttledSince < 0 || now - this.lastThrottleAt > this.cfg.cpuQuotaWindowMs) this.throttledSince = now;
      this.lastThrottleAt = now;
      const oldestBusy = (Math.floor(now / this.bucketMs) - BUCKETS + 1) * this.bucketMs;
      let oldest = Infinity;
      for (const s of this.buckets) if (s.ms > 0 && s.start >= oldestBusy && s.start < oldest) oldest = s.start;
      let retryAt = oldest === Infinity ? now + this.bucketMs : oldest + this.cfg.cpuQuotaWindowMs;
      if (retryAt <= now) retryAt = now + this.bucketMs;
      return { run: false, retryAt };
    }
    return { run: true, sliceMs: this.cfg.cpuLimitMs };
  }

  /** Length of the current uninterrupted throttling stretch (0 when not throttled). */
  throttledFor(now: number): number {
    if (this.throttledSince < 0 || now - this.lastThrottleAt > this.cfg.cpuQuotaWindowMs) return 0;
    return now - this.throttledSince;
  }

  /** `cpuKillAfterMs` (0 = never): kill the runtime once throttling has lasted this long. */
  shouldKill(now: number): boolean {
    return this.cfg.cpuKillAfterMs > 0 && this.throttledFor(now) >= this.cfg.cpuKillAfterMs;
  }
}
