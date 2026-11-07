// Blitz renderer prototype: the C ABI of the Rust static library in
// renderer-blitz/ (repo root, shared with the Android demo) that lays out the
// validated DOM with blitz-dom and paints it on the CPU with vello_cpu, behind
// the shared NativeRenderer glue (view, touches, frames, diagnostics). Dev
// tooling for the comparison in docs/blitz-experiment.md, not a product path.
// Mirrors BlitzRenderer.kt.
// Frozen since 2026-10-30 (docs/decisions.md, "The renderer prototypes are frozen"): compiled only in a
// build with TW_NATIVE_RENDERERS=YES, which also builds and links the engines (project.yml).

#if TW_NATIVE_RENDERERS
import Foundation
import WidgetSandbox

enum BlitzRenderer {
    /// Plug into WidgetHost: `WidgetModel(..., rendererFactory: BlitzRenderer.factory)`.
    static let factory: RendererFactory = { _, theme, width in NativeRenderer(engine: engine, theme: theme, width: width) }

    static let engine = NativeEngine(
        name: "Blitz",
        create: { twb_create($0, $1, $2) },
        destroy: { twb_destroy($0) },
        receive: { twb_receive($0, $1) },
        setWidth: { twb_set_width($0, $1) },
        setTheme: { twb_set_theme($0, $1) },
        touch: { twb_touch($0, $1, $2, $3, $4, $5) },
        size: { twb_size($0, $1, $2) },
        dirty: { twb_dirty($0) },
        render: { twb_render($0, $1, $2) },
        stringFree: { twb_string_free($0) },
        report: { _ in
            // The font collection (family count, emoji mapping) — the same for every document in the process.
            guard let report = twb_font_report() else { return [] }
            defer { twb_string_free(report) }
            return [("blitz-fonts", String(cString: report))]
        }
    )
}

#endif
