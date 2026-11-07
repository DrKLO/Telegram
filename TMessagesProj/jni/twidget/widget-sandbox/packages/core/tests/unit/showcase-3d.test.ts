import assert from 'node:assert/strict';
import fs from 'node:fs';
import { describe, it } from 'node:test';
import { runWidget } from '../../scripts/sandbox-node.ts';

const corpus = new URL('../../../../corpus/showcase/3d/', import.meta.url);
const files = fs.readdirSync(corpus).filter(file => file.endsWith('.html')).sort();
const state = 'JSON.stringify({yaw,pitch,zoom,variant,drag})';
/** The frame budget (corpus/showcase/3d/README.md): at most 2000 canvas commands a frame in every state. */
const FRAME_COMMANDS = 2000;
/** Each widget's elevation range (the cat's mouse pitch included) and model count. */
const ranges: Record<string, { pitch: number[]; variants: number }> = {
  '01-robot.html': { pitch: [-.15, .4, .95], variants: 3 },
  '02-camera.html': { pitch: [-.15, .4, .95], variants: 2 },
  '03-orbits.html': { pitch: [-.8, 0, .8], variants: 2 },
  '04-island.html': { pitch: [-.15, .4, .95], variants: 3 },
  '05-molecules.html': { pitch: [-.15, .4, .95], variants: 3 },
  '06-cat.html': { pitch: [-1.3, -.6, 0, .6, 1.3], variants: 2 },
};

describe('projected 3D showcase widgets', () => {
  it('contains exactly six self-contained widgets', () => {
    assert.equal(files.length, 6);
  });

  for (const file of files) {
    const html = fs.readFileSync(new URL(file, corpus), 'utf8');

    it(`${file}: validates and draws finite geometry in both themes`, async () => {
      for (const theme of ['light', 'dark'] as const) {
        const result = await runWidget(html, { theme, policy: 'strict', canvasWords: true,
          probes: ['canvas.width', 'canvas.height'] });
        assert.deepEqual(result.diags, []);
        assert.deepEqual(result.logs.filter(log => log.level === 'error'), []);
        assert.ok(result.size.zlib <= 3800);
        assert.ok(result.size.raw <= 32767);
        assert.ok(Number(result.probes[0]) > 0);
        assert.ok(Number(result.probes[1]) > 0);
        const coordinates = result.renderOps.flatMap(op => op.op === 'canvas' ? op.cmds.flatMap(command => command.slice(2).filter(value => typeof value === 'number')) : []);
        assert.ok(coordinates.length > 100);
        assert.ok(coordinates.every(Number.isFinite));
        assert.ok(result.canvasWordBatches > 0);
        assert.equal(result.timers, 0);
      }
    });

    it(`${file}: draws at most ${FRAME_COMMANDS} canvas commands a frame in every state (strict)`, async () => {
      // Every model, elevation (the cat's full mouse pitch), both zoom bounds and the whole orbit, in both themes and
      // at 200, 360 and 600 px; each state is set and drawn directly, one frame (one canvas op) per probe.
      const { pitch, variants } = ranges[file];
      let worst = 0;
      for (const [theme, width, yawSteps] of [['light', 360, 8], ['dark', 200, 4], ['light', 600, 4]] as const) {
        const height = width <= 260 ? 190 : 240;
        const states: string[] = [];
        for (let variant = 0; variant < variants; variant++) for (const zoom of [.7, 1.3]) for (const p of pitch)
          for (let i = 0; i < yawSteps; i++) states.push(`yaw=${(-Math.PI + i * 2 * Math.PI / yawSteps).toFixed(4)},pitch=${p},zoom=${zoom},variant=${variant}`);
        for (let start = 0; start < states.length; start += 40) {
          const part = states.slice(start, start + 40);
          const result = await runWidget(html, { policy: 'strict', theme, width, timerRounds: 1,
            answer: query => query.q === 'rect' ? { x: 0, y: 0, width, height, top: 0, left: 0, right: width, bottom: height }
              : query.q === 'matchMedia' ? { matches: theme === 'dark' } : undefined,
            script: part.map(expression => ({ probe: `(${expression},draw(),0)` })) });
          assert.equal(result.stopped, undefined);
          assert.deepEqual(result.diags, []);
          assert.deepEqual(result.logs.filter(log => log.level === 'error'), []);
          assert.deepEqual(result.probes, part.map(() => '0'));
          const frames = result.renderOps.flatMap(op => op.op === 'canvas' ? [op.cmds.length] : []);
          assert.equal(frames.length, part.length + 1);
          worst = Math.max(worst, ...frames);
        }
      }
      assert.ok(worst <= FRAME_COMMANDS, `${worst} canvas commands in one frame`);
    });

    it(`${file}: redraws without querying the host's media state`, async () => {
      const initial = await runWidget(html, { timerRounds: 0 });
      const repeated = await runWidget(html, { timerRounds: 0,
        probes: ['(draw(),draw(),draw(),status.textContent)'] });
      const mediaQueries = (queries: unknown[]) => queries.filter(query => (query as { q?: string }).q === 'matchMedia');
      assert.ok(mediaQueries(initial.queries).length > 0);
      assert.equal(mediaQueries(repeated.queries).length, mediaQueries(initial.queries).length);
      assert.ok(repeated.probes[0].length > 0);
      assert.deepEqual(repeated.diags, []);
      assert.deepEqual(repeated.logs.filter(log => log.level === 'error'), []);
    });

    it(`${file}: updates cached colors when the chat changes theme`, async () => {
      const snapshot = '(draw(),darkMode)';
      let mediaQueries = 0;
      const result = await runWidget(html, { theme: 'light', timerRounds: 0,
        answer: query => query.q === 'matchMedia' ? { matches: mediaQueries++ === 1 } : undefined, script: [
        { probe: snapshot }, { theme: 'dark' }, { probe: snapshot }, { theme: 'light' }, { probe: snapshot },
      ] });
      const [light, dark, restored] = result.probes.map(probe => JSON.parse(probe));
      assert.equal(mediaQueries, 3);
      assert.deepEqual([light, dark, restored], [false, true, false]);
      assert.deepEqual(result.diags, []);
      assert.deepEqual(result.logs.filter(log => log.level === 'error'), []);
    });

    it(`${file}: saves a changed model and zoom, then restores and resets them`, async () => {
      const initial = await runWidget(html);
      const id = (name: string) => {
        const op = initial.renderOps.find(op => op.op === 'el' && op.attrs.some(([key, value]) => key === 'id' && value === name));
        assert.ok(op);
        return op.id;
      };
      const changed = await runWidget(html, {
        events: ['special', 'near'].map(name => ({ type: 'click', targetId: id(name) })),
        probes: [state],
      });
      const modified = JSON.parse(changed.probes[0]);
      assert.equal(modified.variant, 1);
      assert.ok(Math.abs(modified.zoom - 1.1) < 1e-6);
      const storage = Object.assign({}, ...changed.storageCommits);
      assert.ok(Object.keys(storage.local).length > 0);
      const restored = await runWidget(html, { storage, probes: [state] });
      assert.deepEqual(JSON.parse(restored.probes[0]), modified);
      const reset = await runWidget(html, { storage,
        events: [{ type: 'click', targetId: id('reset') }], probes: [state] });
      const defaults = JSON.parse(reset.probes[0]);
      assert.ok(Math.abs(defaults.yaw + .55) < 1e-10);
      assert.deepEqual({ ...defaults, yaw: -.55 }, { yaw: -.55, pitch: .28, zoom: 1, variant: 0, drag: null });
    });

    if (file === '06-cat.html') it('cat: mouse drag changes both yaw and pitch and keeps them on release', async () => {
      const initial = await runWidget(html);
      const canvas = initial.renderOps.find(op => op.op === 'el' && op.attrs.some(([key, value]) => key === 'id' && value === 'view'));
      assert.ok(canvas);
      const pointer = { targetId: canvas.id, pointerId: 1, pointerType: 'mouse', button: 0 };
      const result = await runWidget(html, { timerRounds: 0, events: [
        { ...pointer, type: 'pointerdown', clientX: 100, clientY: 100 },
        { ...pointer, type: 'pointermove', clientX: 150, clientY: 145 },
        { ...pointer, type: 'pointerup', clientX: 150, clientY: 145 },
      ], probes: [state] });
      const moved = JSON.parse(result.probes[0]);
      assert.ok(Math.abs(moved.yaw - .05) < 1e-10);
      assert.ok(Math.abs(moved.pitch - .82) < 1e-10);
      assert.equal(moved.drag, null);
      assert.equal(result.storageCommits.length, 1);
      assert.deepEqual(result.diags, []);
    });

    if (file === '06-cat.html') it('cat: restores the full vertical mouse rotation after recreation', async () => {
      const initial = await runWidget(html);
      const canvas = initial.renderOps.find(op => op.op === 'el' && op.attrs.some(([key, value]) => key === 'id' && value === 'view'));
      assert.ok(canvas);
      const pointer = { targetId: canvas.id, pointerId: 1, pointerType: 'mouse', button: 0 };
      for (const clientY of [-100, 300]) {
        const changed = await runWidget(html, { timerRounds: 0, events: [
          { ...pointer, type: 'pointerdown', clientX: 100, clientY: 100 },
          { ...pointer, type: 'pointermove', clientX: 100, clientY },
          { ...pointer, type: 'pointerup', clientX: 100, clientY },
        ], probes: [state] });
        const modified = JSON.parse(changed.probes[0]);
        assert.equal(modified.pitch, clientY < 100 ? -1.3 : 1.3);
        const storage = Object.assign({}, ...changed.storageCommits);
        const restored = await runWidget(html, { storage, probes: [state] });
        assert.deepEqual(JSON.parse(restored.probes[0]), modified);
        assert.deepEqual(restored.diags, []);
      }
    });

    it(`${file}: rolls back an orbit when the host cancels a pointer`, async () => {
      const initial = await runWidget(html);
      const canvas = initial.renderOps.find(op => op.op === 'el' && op.attrs.some(([key, value]) => key === 'id' && value === 'view'));
      assert.ok(canvas);
      const pointer = { targetId: canvas.id, pointerId: 1, pointerType: 'touch', button: 0 };
      const canceled = await runWidget(html, {
        events: [{ ...pointer, type: 'pointerdown', clientX: 120 },
          { ...pointer, type: 'pointermove', clientX: 180 },
          { ...pointer, type: 'pointercancel', clientX: 180 }], probes: [state],
      });
      assert.deepEqual(canceled.diags, []);
      assert.deepEqual(JSON.parse(canceled.probes[0]), { yaw: -.55, pitch: .28, zoom: 1, variant: 0, drag: null });
      assert.deepEqual(canceled.storageCommits, []);
    });
  }
});
