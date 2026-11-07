// C ABI of the twidget litehtml renderer prototype (src/apple/ffi.cpp). The
// same shape as renderer-blitz/include/twidget_blitz.h with the `twl_` prefix,
// so the Swift glue (ios/DemoApp/Sources/NativeRenderer.swift) drives both
// engines through one table of functions. One renderer per widget behind an
// opaque handle; every call on the main thread. Strings returned by twl_*
// functions are released with twl_string_free.
#ifndef TWIDGET_LITEHTML_H
#define TWIDGET_LITEHTML_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct twl_renderer twl_renderer;

/// width in CSS px, dpr = device pixel ratio, theme "light" | "dark". NULL when the renderer could not be created.
twl_renderer *twl_create(double width, double dpr, const char *theme);
void twl_destroy(twl_renderer *handle);
/// Host → renderer message (JSON, docs/protocol.md). Returns a JSON array of renderer → host messages.
char *twl_receive(twl_renderer *handle, const char *json);
char *twl_set_width(twl_renderer *handle, double width);
char *twl_set_theme(twl_renderer *handle, const char *theme);
/// A touch in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Returns renderer → host messages.
char *twl_touch(twl_renderer *handle, int32_t action, double x, double y, int32_t pointer, bool primary);
/// Physical size (px) of the next render.
void twl_size(twl_renderer *handle, uint32_t *width, uint32_t *height);
bool twl_dirty(twl_renderer *handle);
/// Paints premultiplied RGBA8 into buffer (>= width*height*4 bytes). Returns the paint time in ms, negative on failure.
double twl_render(twl_renderer *handle, uint8_t *buffer, size_t capacity);
/// The size of an asynchronously decoded image (web backend); rebuilds the document. Returns renderer → host messages.
char *twl_image_ready(twl_renderer *handle, int32_t image, float width, float height);
/// The display list of the current document as JSON (src/display_list.h), CSS px; clears `dirty`. Release with twl_string_free.
char *twl_display_list(twl_renderer *handle);
/// Diagnostics: the last rebuild as JSON (`serializeMs`, `parseLayoutMs`, `passes`, `htmlBytes`, `builds`, `displayOps`). Release with twl_string_free.
char *twl_stats(twl_renderer *handle);
/// Diagnostics: the HTML the document was last built from. Release with twl_string_free.
char *twl_html(twl_renderer *handle);
void twl_string_free(char *s);

#ifdef __cplusplus
}
#endif
#endif
