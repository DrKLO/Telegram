// Malformed binary op batches: the decoder rejects them with an Error and never
// returns ops with missing or non-integer fields, whatever the words say.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { OpWriter } from '../../src/sandbox/binary-ops.ts';
import { decodeBinaryOps } from '../../src/shared/binary-ops-decode.ts';

function rng(seed: number) {
  let s = seed >>> 0 || 1;
  return () => { s ^= s << 13; s >>>= 0; s ^= s >>> 17; s ^= s << 5; s >>>= 0; return s / 4294967296; };
}

function batch(r: () => number): { words: number[]; joined: string } {
  let out: { words: number[]; joined: string } = { words: [], joined: '' };
  const w = new OpWriter((words, joined) => { out = { words: Array.from(words), joined }; });
  const n = 1 + Math.floor(r() * 12);
  for (let k = 0; k < n; k++) {
    const id = 1 + Math.floor(r() * 50);
    switch (Math.floor(r() * 9)) {
      case 0: w.push({ op: 'el', id, tag: r() < 0.5 ? 'div' : 'svg', ns: r() < 0.3 ? 'svg' : undefined, attrs: r() < 0.5 ? { class: 'a b', 'data-x': String(k) } : undefined }); break;
      case 1: w.push({ op: 'text', id, data: 'hello ' + k }); break;
      case 2: w.push({ op: 'attr', id, name: 'class', value: 'c' + k }); break;
      case 3: w.push({ op: 'rattr', id, name: 'hidden' }); break;
      case 4: w.push({ op: 'ins', parent: id, id: id + 1, before: r() < 0.5 ? null : id + 2 }); break;
      case 5: w.push({ op: 'rm', id }); break;
      case 6: w.push({ op: 'style', id, name: 'color', value: 'red', important: r() < 0.5 ? true : undefined }); break;
      case 7: w.push({ op: 'canvas', id, bin: k }); break;
      default: w.push({ op: 'other', id, x: [1, 2] }); break;
    }
  }
  w.flush();
  return out;
}

function wellFormed(op: any): boolean {
  if (!op || typeof op !== 'object' || typeof op.op !== 'string') return false;
  for (const key of ['id', 'parent', 'bin']) {
    if (key in op && !(Number.isInteger(op[key]))) return false;
  }
  for (const key of ['tag', 'name', 'value', 'data']) {
    if (key in op && typeof op[key] !== 'string') return false;
  }
  return true;
}

function check(words: number[], joined: string) {
  let ops: any[] | undefined;
  try {
    ops = decodeBinaryOps(words, joined, []);
  } catch (e) {
    assert.ok(e instanceof Error, 'throws an Error');
    return 'rejected';
  }
  for (const op of ops) assert.ok(wellFormed(op), 'decoded op is well formed: ' + JSON.stringify(op));
  return 'decoded';
}

describe('binary ops decoder fuzz', () => {
  it('round-trips the batches the writer makes', () => {
    const r = rng(1);
    for (let t = 0; t < 500; t++) {
      const { words, joined } = batch(r);
      assert.equal(check(words, joined), 'decoded');
    }
  });

  it('truncated batches are rejected', () => {
    const r = rng(2);
    for (let t = 0; t < 2000; t++) {
      const { words, joined } = batch(r);
      if (words.length < 4) continue;
      const cut = 2 + Math.floor(r() * (words.length - 2));
      if (cut === words.length) continue;
      assert.equal(check(words.slice(0, cut), joined), 'rejected', `cut ${cut}/${words.length}`);
    }
  });

  it('mutated words never crash or yield malformed ops', () => {
    const r = rng(3);
    const specials = [0, -1, 1, 2, 3, 0x7fffffff, -0x80000000, 4096, -4097, 1e9, NaN, 1.5];
    for (let t = 0; t < 5000; t++) {
      const { words, joined } = batch(r);
      const mutated = words.slice();
      const flips = 1 + Math.floor(r() * 4);
      for (let f = 0; f < flips; f++) {
        const at = 1 + Math.floor(r() * (mutated.length - 1));
        mutated[at] = r() < 0.5 ? specials[Math.floor(r() * specials.length)] : Math.floor(r() * 20) - 5;
      }
      check(mutated, joined);
    }
  });

  it('random buffers never crash', () => {
    const r = rng(4);
    for (let t = 0; t < 5000; t++) {
      const n = 2 + Math.floor(r() * 40);
      const words = [0x54574f31, Math.floor(r() * 5)];
      for (let k = 2; k < n; k++) words.push(Math.floor(r() * 16) - 2);
      check(words, 'abcdefghij'.slice(0, Math.floor(r() * 11)));
    }
  });

  it('intern definitions outside the table are rejected', () => {
    for (const k of [-1, 4096, 1e9, NaN, 1.5]) {
      assert.equal(check([0x54574f31, 0, 0, k, 1], 'a'), 'rejected', String(k));
    }
  });
});
