# macOS demo

widget-sandbox's corpus in a feed of bubbles on macOS, rendered by TwidgetCore, the portable C++
renderer that also drives the Windows spike (Direct2D), the Qt demo (QPainter), Android and the web.
It is the same pipeline as `LiteHTMLWidgetRenderer` with the Swift taken out:

| Piece | Here | In the Swift renderer |
|---|---|---|
| Widget JS, validator, host | widget-sandbox's C++ `WidgetHost` (`packages/desktop`) | `WidgetHost.swift` |
| DOM, CSS, layout, events, animations, canvas | TwidgetCore (`Sources/TwidgetCore`) | `TwidgetRenderer` + `LHDocument` |
| Painting | `CGBackend` (`platform/apple/coregraphics.cpp`) | `LHContainer` |
| Compositor layers | the core's `Composition`, played on CALayers by `widget_view.mm` | `WidgetCompositor` |

Each widget's document is painted into an image on its own clock thread. Transform and opacity
animations become CALayers whose keyframe tracks Core Animation plays on the render server, as the
Windows spike plays them on Windows.UI.Composition. A frame's document image and its layers land in
one `CATransaction`.

## Build

```sh
cmake -S tools/macos-demo -B build-macos -G Ninja      # or -G Xcode
cmake --build build-macos
open build-macos/twidget-macos-demo.app                 # or pass a corpus folder or .html file
```

widget-sandbox must be checked out next to twidget, or its path given in `TWIDGET_WIDGET_SANDBOX`.

## State

Built and launched on Apple Silicon on 2026-10-14 after adding the litehtml CoreFoundation link
and setting the demo's minimum macOS version to 13.3. The core and Apple CTest suites pass.
The canvas-market stress widget still displays a flat fill instead of its charts, reproduced in
both message replay and the live demo. This is an experimental comparison app, not a production
replacement for the Swift renderer. See [the portability check](../../docs/TWIDGET_CORE.md#portability-check-2026-10-14)
for remaining behavior and performance gaps. Other areas that still need visual verification:

- **Coordinates:** the view hosts its own layer tree (y up). A `stage` layer's `sublayerTransform`
  turns document coordinates (y down) into the view's. Each layer's transform is the track's matrix
  after `layer_transform`'s map from the layer's own space, so content images stay upright.
- **Clips:** a clipped layer sits in a container masked by a `CAShapeLayer` with per-corner radii.
- **Hit testing:** the core samples the same tracks on its own clock (`Renderer::clock()`).
  Core Animation starts them a dispatch later, so a fast-moving layer can be hit a frame off.
- **Nested layers:** a layer inside an animated element's layer sits in that layer's `nest`,
  shifted by its bounds' origin, so it moves with the parent's track.
- **Text editing:** a tap on a text input or textarea puts an `NSTextField` over it, beside the
  view (a layer-hosting view keeps no subviews), from the core's `editor()`; typing reaches the
  widget as input events, and the field losing the keyboard ends the editing. Not yet built.
