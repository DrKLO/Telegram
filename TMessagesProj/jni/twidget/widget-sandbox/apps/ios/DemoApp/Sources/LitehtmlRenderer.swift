// litehtml renderer prototype: the C ABI of renderer-litehtml/ (repo root,
// shared with the Android demo; C++: litehtml + a CoreText/CoreGraphics
// backend) behind the shared NativeRenderer glue. The validated DOM is kept as
// a shadow tree on the C++ side and re-parsed by litehtml on every batch, as
// Telegram's HTMLBubbles does (docs/litehtml-renderer.md). Dev tooling for the
// three-way renderer comparison, not a product path. Mirrors LitehtmlRenderer.kt.
// Frozen since 2026-10-30 (docs/decisions.md, "The renderer prototypes are frozen"): compiled only in a
// build with TW_NATIVE_RENDERERS=YES, which also builds and links the engines (project.yml).

#if TW_NATIVE_RENDERERS
import Foundation
import WidgetSandbox

enum LitehtmlRenderer {
    /// Plug into WidgetHost: `WidgetModel(..., rendererFactory: LitehtmlRenderer.factory)`.
    static let factory: RendererFactory = { _, theme, width in NativeRenderer(engine: engine, theme: theme, width: width) }

    static let engine = NativeEngine(
        name: "litehtml",
        create: { twl_create($0, $1, $2) },
        destroy: { twl_destroy($0) },
        receive: { twl_receive($0, $1) },
        setWidth: { twl_set_width($0, $1) },
        setTheme: { twl_set_theme($0, $1) },
        touch: { twl_touch($0, $1, $2, $3, $4, $5) },
        size: { twl_size($0, $1, $2) },
        dirty: { twl_dirty($0) },
        render: { twl_render($0, $1, $2) },
        stringFree: { twl_string_free($0) },
        report: { handle in
            // The last rebuild: serialise → parse+layout per pass, HTML size, display-list length.
            guard let s = twl_stats(handle) else { return [] }
            defer { twl_string_free(s) }
            guard let data = String(cString: s).data(using: .utf8), let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
            let f = { (k: String) in (o[k] as? Double) ?? 0 }
            return [("litehtml-build", String(format: "serialise %.2f ms, parse+layout %.1f ms in %d pass(es), %.0f B html, %.0f draw ops, build #%.0f", f("serializeMs"), f("parseLayoutMs"), Int(f("passes")), f("htmlBytes"), f("displayOps"), f("builds")))]
        }
    )
}

#endif
