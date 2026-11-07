// Binary DOM ops (docs/protocol.md, "Binary DOM ops"): when the host lists
// `binaryOps` in config.features and the glue installs `__hostOps`, every op the
// reconciliation emits is encoded into an Int32Array with all strings appended to
// one JS string, and handed over in one call per flush instead of JSON text.
// Each record stands for exactly the JSON op the JSON path would have sent; any
// op the records cannot carry exactly goes as a JSON record.

import { boxNeutral } from './box-neutral';

const MAGIC = 0x54574f31;
const stringifyOp = JSON.stringify;
const NOT_INTERNED = 2147483647;
const MAX_INTERNED = 4096;
const MAX_INTERNED_LENGTH = 256;
const FLUSHES_PER_TABLE = 256;

const DEF = 0, EL = 1, TEXT = 2, COMMENT = 3, DATA = 4, ATTR = 5, RATTR = 6, INS = 7, RM = 8, STYLE = 9, CANVAS = 10, JSON_OP = 11, RESET = 12;

const isInt32 = (v: unknown): v is number => typeof v === 'number' && (v | 0) === v;

export class OpWriter {
  private w = new Int32Array(4096);
  private n = 2;
  private count = 0;
  private strings: string[] = [];
  private interned = new Map<string, number>();
  private tableSize = 0;
  private flushes = 0;
  private resetPending = false;

  constructor(private readonly send: (words: Int32Array, strings: string) => void) {}

  get length(): number {
    return this.count;
  }

  private room(c: number): Int32Array {
    if (this.n + c > this.w.length) {
      const g = new Int32Array(Math.max(this.w.length * 2, this.n + c + 64));
      g.set(this.w.subarray(0, this.n));
      this.w = g;
    }
    return this.w;
  }

  /** Interned reference (-k-1), defining it in-stream on first use, or NOT_INTERNED. */
  private intern(x: string): number {
    const k = this.interned.get(x);
    if (k !== undefined) return -k - 1;
    if (this.tableSize >= MAX_INTERNED || x.length > MAX_INTERNED_LENGTH) return NOT_INTERNED;
    const key = this.tableSize++;
    this.interned.set(x, key);
    const w = this.room(3);
    w[this.n] = DEF;
    w[this.n + 1] = key;
    w[this.n + 2] = x.length;
    this.n += 3;
    this.strings.push(x);
    return -key - 1;
  }

  private inline(x: string): number {
    this.strings.push(x);
    return x.length;
  }

  private field(ref: number, x: string): number {
    return ref === NOT_INTERNED ? this.inline(x) : ref;
  }

  boxNeutral = true;

  push(o: any): void {
    this.count++;
    if (this.boxNeutral && !boxNeutral(o)) this.boxNeutral = false;
    if (o && typeof o === 'object' && isInt32(o.id) && this.record(o)) return;
    const json = stringifyOp(o);
    const w = this.room(2);
    w[this.n] = JSON_OP;
    w[this.n + 1] = this.inline(json === undefined ? 'null' : json);
    this.n += 2;
  }

  private record(o: any): boolean {
    const id = o.id;
    switch (o.op) {
      case 'data':
      case 'text':
      case 'comment': {
        if (typeof o.data !== 'string') return false;
        const w = this.room(3);
        w[this.n] = o.op === 'data' ? DATA : o.op === 'text' ? TEXT : COMMENT;
        w[this.n + 1] = id;
        w[this.n + 2] = this.inline(o.data);
        this.n += 3;
        return true;
      }
      case 'attr': {
        const { name, value } = o;
        if (typeof name !== 'string' || typeof value !== 'string') return false;
        const r = this.intern(name);
        const q = name === 'class' && value.length <= 64 ? this.intern(value) : NOT_INTERNED;
        const w = this.room(4);
        const n = this.n;
        w[n] = ATTR;
        w[n + 1] = id;
        w[n + 2] = this.field(r, name);
        w[n + 3] = this.field(q, value);
        this.n = n + 4;
        return true;
      }
      case 'rattr': {
        if (typeof o.name !== 'string') return false;
        const r = this.intern(o.name);
        const w = this.room(3);
        const n = this.n;
        w[n] = RATTR;
        w[n + 1] = id;
        w[n + 2] = this.field(r, o.name);
        this.n = n + 3;
        return true;
      }
      case 'ins': {
        const { parent, before } = o;
        if (!isInt32(parent) || !(before === null || (isInt32(before) && before !== 0))) return false;
        const w = this.room(4);
        w[this.n] = INS;
        w[this.n + 1] = parent;
        w[this.n + 2] = id;
        w[this.n + 3] = before === null ? 0 : before;
        this.n += 4;
        return true;
      }
      case 'rm': {
        const w = this.room(2);
        w[this.n] = RM;
        w[this.n + 1] = id;
        this.n += 2;
        return true;
      }
      case 'style': {
        const { name, value, important } = o;
        if (typeof name !== 'string' || typeof value !== 'string' || !(important === true || important === undefined)) return false;
        const r = this.intern(name);
        const w = this.room(5);
        const n = this.n;
        w[n] = STYLE;
        w[n + 1] = id;
        w[n + 2] = this.field(r, name);
        w[n + 3] = this.inline(value);
        w[n + 4] = important ? 1 : 0;
        this.n = n + 5;
        return true;
      }
      case 'canvas': {
        if (!isInt32(o.bin) || o.cmds !== undefined) return false;
        const w = this.room(3);
        w[this.n] = CANVAS;
        w[this.n + 1] = id;
        w[this.n + 2] = o.bin;
        this.n += 3;
        return true;
      }
      case 'el': {
        const { tag, ns, attrs } = o;
        if (typeof tag !== 'string' || !(ns === undefined || ns === 'svg')) return false;
        if (!(attrs === undefined || (attrs !== null && typeof attrs === 'object' && !Array.isArray(attrs)))) return false;
        const entries: [string, unknown][] = attrs === undefined ? [] : Object.entries(attrs);
        if (attrs !== undefined && entries.length === 0) return false;
        for (const [, v] of entries) if (typeof v !== 'string') return false;
        const r = this.intern(tag);
        const names = entries.map(([k]) => this.intern(k));
        const w = this.room(5 + 2 * entries.length);
        let n = this.n;
        w[n] = EL;
        w[n + 1] = id;
        w[n + 2] = this.field(r, tag);
        w[n + 3] = ns === 'svg' ? 1 : 0;
        w[n + 4] = entries.length;
        n += 5;
        for (let j = 0; j < entries.length; j++) {
          w[n] = this.field(names[j], entries[j][0]);
          w[n + 1] = this.inline(entries[j][1] as string);
          n += 2;
        }
        this.n = n;
        return true;
      }
    }
    return false;
  }

  /** Hands the batch to the glue. Ill-formed UTF-16 becomes U+FFFD: the renderer could not carry it anyway. */
  flush(): void {
    if (this.count === 0 && this.n <= 3 && !this.resetPending) return;
    this.w[0] = MAGIC;
    this.w[1] = this.count;
    let joined = this.strings.join('');
    if (typeof (joined as any).isWellFormed === 'function' && !(joined as any).isWellFormed()) joined = (joined as any).toWellFormed();
    this.resetPending = false;
    this.boxNeutral = true;
    this.send(this.w.subarray(0, this.n), joined);
    this.n = 2;
    this.count = 0;
    this.strings = [];
    if (++this.flushes >= FLUSHES_PER_TABLE || this.tableSize >= MAX_INTERNED) {
      this.interned.clear();
      this.tableSize = 0;
      this.flushes = 0;
      this.w[2] = RESET;
      this.n = 3;
      this.resetPending = true;
    }
  }
}
