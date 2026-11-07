// Pool of warmed sandbox workers (web host glue; the web twin of EnginePool in
// ios/Sources/WidgetSandbox/Pools.swift). A warmed worker has instantiated
// QuickJS and evaluated the sandbox bundle — the expensive part of a widget's
// boot — and waits for `start` with the widget's HTML. The pool keeps `size`
// of them ready, refills as they are taken (unless `canRefill` says the list is
// scrolling), and `prewarm()` fills it at app start. Trusted code; the sandbox
// and the protocol are unchanged (`warm` + `start` = `init` in two halves).

import sandboxBundle from 'virtual:sandbox-bundle';
import { LIMIT_DEFAULTS, SAB_SIZE, type SandboxConfig, type WorkerToHost } from '../shared/protocol';

export interface PooledWorker {
  worker: Worker;
  sab: SharedArrayBuffer;
  /** Resolves when the bundle is evaluated (or rejects if the worker died first). */
  warmed: Promise<number>;
  /** Everything the worker said while idle in the pool (the shim's document skeleton ops, diagnostics): the host replays it before anything else. */
  pending: WorkerToHost[];
}

export interface WorkerPoolOptions {
  /** Limits the warmed VM boots with (the real config comes with `start`); defaults to LIMIT_DEFAULTS. */
  limits?: Partial<Pick<SandboxConfig, 'memoryLimitBytes' | 'cpuLimitMs'>>;
  /** Return false to postpone refilling (e.g. while the feed is scrolling); `refill()` resumes it. */
  canRefill?: () => boolean;
}

export class WorkerPool {
  private idle: PooledWorker[] = [];
  private destroyed = false;
  /** Counters for the demo / bench. */
  readonly stats = { created: 0, hits: 0, misses: 0 };

  constructor(readonly size: number, private opts: WorkerPoolOptions = {}) {}

  /** Fill the pool and wait until every worker is warm (app start). */
  async prewarm(): Promise<void> {
    this.refill();
    await Promise.all(this.idle.map((e) => e.warmed.catch(() => 0)));
  }

  /** A warmed worker (or a fresh one when the pool is empty: it still boots, just later than it could have). */
  take(): PooledWorker {
    let e = this.idle.shift();
    if (e) this.stats.hits++;
    else { this.stats.misses++; e = this.create(); }
    queueMicrotask(() => this.refill());
    return e;
  }

  /** Top the pool up to `size` (no-op while `canRefill` says no). */
  refill() {
    if (this.destroyed) return;
    while (this.idle.length < this.size && (this.opts.canRefill?.() ?? true)) this.idle.push(this.create());
  }

  get warm(): number { return this.idle.length; }

  destroy() {
    this.destroyed = true;
    for (const e of this.idle.splice(0)) e.worker.terminate();
  }

  private create(): PooledWorker {
    this.stats.created++;
    const worker = new Worker(new URL('./worker.ts', import.meta.url), { type: 'module' });
    const sab = new SharedArrayBuffer(SAB_SIZE);
    const config: SandboxConfig = {
      ...LIMIT_DEFAULTS, ...this.opts.limits,
      visible: true, viewport: { innerWidth: 400, innerHeight: 300, dpr: 1 }, theme: 'light',
    };
    const pending: WorkerToHost[] = [];
    const warmed = new Promise<number>((resolve, reject) => {
      // WidgetHost replaces this handler when it takes the worker and replays `pending`; a `warmed` that arrives after that is simply not awaited.
      worker.onmessage = (ev: MessageEvent<WorkerToHost>) => {
        pending.push(ev.data);
        if (ev.data.type === 'warmed') resolve(ev.data.bootMs);
        else if (ev.data.type === 'dead') reject(new Error(ev.data.reason));
      };
      worker.onerror = (ev) => reject(new Error(ev.message));
    });
    worker.postMessage({ type: 'warm', sab, config, sandboxBundle });
    return { worker, sab, warmed, pending };
  }
}
