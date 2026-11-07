// Decoder for the shim's binary op batches, kept out of the sandbox bundle (tests and the Node harness).

const MAGIC = 0x54574f31;
const MAX_INTERNED = 4096;
const DEF = 0, EL = 1, TEXT = 2, COMMENT = 3, DATA = 4, ATTR = 5, RATTR = 6, INS = 7, RM = 8, STYLE = 9, CANVAS = 10, JSON_OP = 11, RESET = 12;
const isInt32 = (v: unknown): v is number => typeof v === 'number' && (v | 0) === v;

/** The JSON ops a binary batch stands for (tests and the Node harness). Throws on a malformed batch. */
export function decodeBinaryOps(words: ArrayLike<number>, joined: string, table: string[]): any[] {
  if (words.length < 2 || words[0] !== MAGIC || !isInt32(words[1]) || words[1] < 0) throw new Error('bad header');
  const ops: any[] = [];
  const canvasKeys = new Set<number>();
  let at = 0;
  let i = 2;
  const word = (k: number) => {
    const v = words[i + k];
    if (i + k >= words.length || !isInt32(v)) throw new Error('truncated or non-integer word');
    return v;
  };
  const flag = (k: number) => {
    const v = word(k);
    if (v !== 0 && v !== 1) throw new Error('bad flag');
    return v === 1;
  };
  const take = (n: number) => {
    if (n < 0 || at + n > joined.length) throw new Error('string out of range');
    const s = joined.slice(at, at + n);
    at += n;
    return s;
  };
  const str = (v: number) => {
    if (v >= 0) return take(v);
    const k = -v - 1;
    if (k >= table.length || typeof table[k] !== 'string') throw new Error('bad intern');
    return table[k];
  };
  while (i < words.length) {
    const code = word(0);
    switch (code) {
      case DEF: {
        const k = word(1);
        if (k < 0 || k >= MAX_INTERNED || k > table.length) throw new Error('bad intern');
        table[k] = take(word(2));
        i += 3;
        break;
      }
      case RESET: { table.length = 0; i += 1; break; }
      case EL: {
        const id = word(1), tag = str(word(2)), svg = flag(3), m = word(4);
        if (m < 0 || m > (words.length - i - 5) / 2) throw new Error('bad attribute count');
        i += 5;
        const op: any = { op: 'el', id, tag };
        if (svg) op.ns = 'svg';
        if (m > 0) {
          const attrs: Record<string, string> = Object.create(null);
          for (let k = 0; k < m; k++) { const name = str(word(0)); attrs[name] = str(word(1)); i += 2; }
          op.attrs = attrs;
        }
        ops.push(op);
        break;
      }
      case TEXT: case COMMENT: case DATA: {
        ops.push({ op: code === TEXT ? 'text' : code === COMMENT ? 'comment' : 'data', id: word(1), data: str(word(2)) });
        i += 3;
        break;
      }
      case ATTR: { ops.push({ op: 'attr', id: word(1), name: str(word(2)), value: str(word(3)) }); i += 4; break; }
      case RATTR: { ops.push({ op: 'rattr', id: word(1), name: str(word(2)) }); i += 3; break; }
      case INS: { const before = word(3); ops.push({ op: 'ins', parent: word(1), id: word(2), before: before === 0 ? null : before }); i += 4; break; }
      case RM: { ops.push({ op: 'rm', id: word(1) }); i += 2; break; }
      case STYLE: {
        const op: any = { op: 'style', id: word(1), name: str(word(2)), value: str(word(3)) };
        if (flag(4)) op.important = true;
        ops.push(op);
        i += 5;
        break;
      }
      case CANVAS: {
        // One reference per payload and batch (BinaryOps.swift `canvasKey`).
        const key = word(2);
        if (canvasKeys.has(key)) throw new Error('canvas payload referenced twice');
        canvasKeys.add(key);
        ops.push({ op: 'canvas', id: word(1), bin: key });
        i += 3;
        break;
      }
      case JSON_OP: {
        const op = JSON.parse(str(word(1)));
        if (!op || typeof op !== 'object' || Array.isArray(op) || typeof op.op !== 'string') throw new Error('bad json op');
        ops.push(op);
        i += 2;
        break;
      }
      default: throw new Error('bad opcode ' + code);
    }
  }
  if (ops.length !== words[1] || at !== joined.length) throw new Error('count mismatch');
  return ops;
}
