// C ABI of the twidget Blitz renderer prototype (src/ffi.rs). One renderer per
// widget behind an opaque handle; every call on the main thread. Strings
// returned by twb_* functions are released with twb_string_free.
#ifndef TWIDGET_BLITZ_H
#define TWIDGET_BLITZ_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct twb_renderer twb_renderer;

/// width in CSS px, dpr = device pixel ratio, theme "light" | "dark". NULL when the renderer could not be created.
twb_renderer *twb_create(double width, double dpr, const char *theme);
void twb_destroy(twb_renderer *handle);
/// Host → renderer message (JSON, docs/protocol.md). Returns a JSON array of renderer → host messages.
char *twb_receive(twb_renderer *handle, const char *json);
char *twb_set_width(twb_renderer *handle, double width);
char *twb_set_theme(twb_renderer *handle, const char *theme);
/// A touch in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Returns renderer → host messages.
char *twb_touch(twb_renderer *handle, int32_t action, double x, double y, int32_t pointer, bool primary);
/// Physical size (px) of the next render.
void twb_size(twb_renderer *handle, uint32_t *width, uint32_t *height);
bool twb_dirty(twb_renderer *handle);
/// Rasteriser: "vello" (default, portable) or "cg" (CoreGraphics/CoreText, Apple only; also TWB_BACKEND=cg in the
/// environment for the process default). False when unknown or unavailable here.
bool twb_set_backend(twb_renderer *handle, const char *name);
/// The active rasteriser's name; a static string, do not free.
const char *twb_backend(twb_renderer *handle);
/// Paints premultiplied RGBA8 into buffer (>= width*height*4 bytes). Returns the paint time in ms, negative on failure.
double twb_render(twb_renderer *handle, uint8_t *buffer, size_t capacity);
/// Diagnostics: the font collection (family count, emoji mapping). Release with twb_string_free.
char *twb_font_report(void);
void twb_string_free(char *s);
/// Caller-owned bytes in the renderer's heap (the wasm glue uses them); release with twb_free and the same length.
uint8_t *twb_alloc(size_t len);
void twb_free(uint8_t *p, size_t len);

#ifdef __cplusplus
}
#endif
#endif
