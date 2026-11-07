# Object Studio: six projected 3D widgets

These self-contained profile-1 widgets use canvas 2D to project, shade and depth-sort
small 3D scenes. The planet uses perspective ray/sphere intersections and
normal-based lighting, presented as one horizontal canvas gradient per 2 px band of the disc,
clipped to its circle. They use no WebGL, CSS 3D transforms, bundled library, image asset,
network access or experimental native side channel. The models and planet surface are procedural illustrations; the molecules are
educational shapes.

| File | Model control |
|---|---|
| `01-robot.html` | Cycle ready, wave and resting poses |
| `02-camera.html` | Assemble or separate the lens elements |
| `03-orbits.html` | Orbit a single smoothly shaded planet and toggle cloud cover |
| `04-island.html` | Cycle low, rising and high tide |
| `05-molecules.html` | Cycle water, methane and ammonia |
| `06-cat.html` | Mouse X/Y rotation of a sculpted cat with a raised-tail pose |

All six support horizontal drag to orbit, elevation buttons, bounded zoom and reset.
Vertical touch drags belong to the chat; pointer cancellation rolls back the tentative
orbit without saving it. A second pointer cannot replace an active drag. The view
and model selection are saved only on completed interactions in per-message
`localStorage`, with corrupt or unavailable storage tolerated. Reset restores the
initial model, angle and zoom and saves that state.

Drawing happens on load, resize, a theme change or an interaction. The robot,
camera, island and molecules coalesce redraws to one animation frame. The color
scheme is cached and updated by media-query change events, so redraws do not query the host for it. There is no idle timer,
animation loop or layout read per frame.
Mesh canvas backing resolution follows the box and device pixel ratio, capped
at 2x. The cat coalesces active redraws with a 200 ms timer; its full mouse-controlled
vertical angle survives recreation. The planet has a fixed 128×128 backing,
throttles active redraws to 250 ms, and uses no pixel read-back/image-data APIs.
Both chat themes and widths 200–600 px are supported; the 200 px layout uses a
shorter viewport. Controls keep a 20 px inset from the leading edge.

## Frame budget

A frame is at most 2000 canvas commands in every state: each model, pose, tide and
molecule, the whole orbit, every elevation (the cat's full ±1.3 mouse pitch included), both
zoom bounds, both themes and widths 200–600 px. That is half the strict cap of 4000 commands
per canvas in one task; at 2 µs a command in the host's main-thread window it is about
25 frames a second on every client. Worst frames in the Node harness (v0.6.0):

| File | Worst frame | Before v0.6.0 |
|---|---|---|
| `01-robot.html` | 1369 (resting pose) | 4144 (resting pose) |
| `02-camera.html` | 605 | 1247 |
| `03-orbits.html` | 1964 (the band budget's ceiling, zoom 1.3) | 20 668 (zoom 1.3, clouds) |
| `04-island.html` | 993 | 2976 |
| `05-molecules.html` | 704 (methane) | 8164 (methane) |
| `06-cat.html` | 1675 | 7606 |

The meshes stay inside it this way. Closed solids (boxes, balls, the cat's tail) skip the
faces turned away from the eye, and a ball's pole quads are triangles (so a ball's top is lit
like its sides; the old pole facets had no normal and drew at the darkest shade). A face is filled once,
without the same-colour outline stroke it used to get: each polygon is pushed out a hair from
its centre (0.008 units per camera axis) instead, which hides the anti-aliasing seams. The
floor grid is one stroked path under the model; the cat, whose eye can go below the floor,
keeps it as depth-sorted faces. The molecules' bond beads are shaded discs, not 32-face balls.
The cat's large balls have 10 × 5 facets, its tail is a hexagonal tube with three stripe bands,
and its flat markings (pupils, nose line, forehead and toe stripes) are single faces.

The planet samples 64 bands of 2 px. Each band is one gradient whose stops keep every sample
within a tolerance that scales with its brightness (dark tones are held tighter); if a frame
would pass 1880 band commands, the tolerance grows until it fits. The glow around the disc is
one radial gradient, the stars two paths. The surface, clouds, terminator and highlight are
still computed per sample; the limb is now a smooth circle instead of pixel steps.

`showcase-3d.test.ts` draws every widget through these states under the strict policy and
fails past 2000 commands in one frame.

## Run the checks

From the widget-sandbox root:

```sh
corepack pnpm exec tsx --test packages/core/tests/unit/showcase-3d.test.ts
corepack pnpm test:showcase --grep '3d/'
TW_RENDERER=shadow corepack pnpm test:showcase --grep '3d/'
corepack pnpm dev
```

The dev picker discovers `showcase/3d/*.html` automatically. For example, open
`http://127.0.0.1:5173/?file=showcase/3d/01-robot.html`. Existing native demo corpus
sync commands discover the folder too; these additions do not require rebuilding
the shared runtime bundles or bytecode.

In telegram-macos, the companion patch mirrors these six files under the
HTMLBubbles test fixtures. The existing recursive demo catalog discovers them
when the test fixture catalog is installed; production app code is unchanged.

## Manual client check

At narrow and wide chat widths, inspect the model in light and dark themes. Orbit
horizontally and scroll vertically over it, cancel a drag, use every control, then
scroll away and back and restart the app. The chosen view/model should persist;
reset should survive recreation. Compare the same packed payload on each client
before shipping. No native-client visual validation is claimed by this patch.
