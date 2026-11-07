# Native solar system

`solar-system.html` is an 11,176-byte standalone HTMLScript example rendered by
HTML3D's bundled THREE-to-Metal adapter. It is not a widget-profile-1 message:
WebGL, CSS 3D transforms and native 3D channels remain excluded from that profile.
Its zlib-compressed size is 4,337 bytes, above the 4,096-byte message limit;
even a smaller version would require this standalone native host, so do not
send it to a widget bot. The `app:three` script names the native host's existing trusted library; no
network script, image, texture or shader source is loaded from the document.

The scene has smooth sphere meshes with procedural vertex colors, a point light
at the Sun, perspective projection, native depth testing, tilted Saturn rings,
eight inclined orbits, Earth's moon and a seeded star field. Sizes, distances,
orbital rates and surface markings are illustrative. Saturn is selected at load;
Overview shows the whole system. Buttons orbit, elevate and zoom the camera;
tapping a planet ray-picks it. Next, focus/overview, pause/play and reset are
supported. Geometry/materials are reused and updates are capped at 30 fps; the
host suspends callbacks when hidden. No drag input or texture mapping is claimed.

## Verify without opening a window

From the twidget root, on a Mac with Metal:

```sh
swift run --package-path tools/solar-system-check --scratch-path /tmp/solar-check-build --disable-sandbox VerifySolar Examples/native-3d/solar-system.html /tmp/solar-quickjs
swift run --package-path tools/solar-system-check --scratch-path /tmp/solar-check-build --disable-sandbox VerifySolar Examples/native-3d/solar-system.html /tmp/solar-jsc --jsc
```

The checker enforces the 32,767-byte HTML cap and renders real offscreen Metal
pixels. It checks camera-action image changes, pause/next/reset labels, positive
triangle counts and zero rejected/clipped geometry. It writes Saturn and camera
view PNGs. It uses a paused unattached CAMetalLayer for viewport metadata, so no
app window or screenshot capture is needed. These checks do not establish all
native-client or performance parity. Real app UI testing remains manual.

An HTMLScript host must register HTML3DSession, supply a viewport, attach its
layer at the `data-3d` stage and forward its existing actions/input. A regular
browser or current `.tgw` widget host cannot render this native document.
No runtime, validator, profile, generated bundles or production demo catalog is
changed by this example.
