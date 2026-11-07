// C ABI of the renderer (platform/apple/ffi.cpp, platform/web/wasm.cpp; JNI in
// platform/android/jni.cpp has the same calls, camelCased). One renderer per
// widget behind an opaque handle; every call on the main thread. Strings
// returned by twl_* functions are released with twl_string_free.
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
/// The tallest the widget may grow, in CSS px; 900 until set.
char *twl_set_max_height(twl_renderer *handle, double height);
char *twl_set_theme(twl_renderer *handle, const char *theme);
/// A touch in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Returns renderer → host messages.
char *twl_touch(twl_renderer *handle, int32_t action, double x, double y, int32_t pointer, bool primary);
/// A pointer as twl_touch, from a mouse (`mouse`: no touch events) with modifiers (bit0 alt, bit1 ctrl, bit2 meta, bit3 shift).
/// `button` 2 is the secondary button: the widget's contextmenu when it claims holds, else the bubble's menu.
char *twl_pointer(twl_renderer *handle, int32_t action, double x, double y, int32_t pointer, bool primary, bool mouse, int32_t modifiers, int32_t button);
/// A mouse over the view with no button down (action 0) or leaving it (1).
char *twl_hover(twl_renderer *handle, int32_t action, double x, double y, int32_t modifiers);
/// A wheel or trackpad scroll in CSS px; mode 0 pixels, 1 lines, 2 pages.
char *twl_wheel(twl_renderer *handle, double x, double y, double dx, double dy, int32_t mode, int32_t modifiers);
/// type "keydown" | "keyup", DOM key and code.
char *twl_key(twl_renderer *handle, const char *type, const char *key, const char *code, bool repeat, int32_t modifiers);
/// The text control being edited, for the platform to cover with its own editor (Renderer::EditorSpec
/// as JSON), or `null`. Check it after every call that returns messages: its generation changes with it.
char *twl_editor(twl_renderer *handle);
/// The platform's editor changed the text of editor generation `generation`. Returns renderer → host messages.
char *twl_edit(twl_renderer *handle, uint64_t generation, const char *text, const char *input_type);
/// The platform's editor let go of the keyboard. Returns renderer → host messages.
char *twl_end_editing(twl_renderer *handle);
/// The text a mouse drag selected (Renderer::selected_text), "" without a selection: what the host's copy
/// command takes.
char *twl_selected_text(twl_renderer *handle);
/// Drops the selection, for a press the host handles itself; true when there was one (the renderer is dirty).
bool twl_clear_selection(twl_renderer *handle);
/// The text under `user-select: text` as laid out, for a host that selects with its own touch UI
/// (Renderer::selectable_text_json): `[[block, x, y, w, h, baseline, font, letter spacing, word spacing, text,
/// [a, b, c, d, e, f]], ...]`, the matrix mapping the box to where it shows.
char *twl_selectable_text(twl_renderer *handle);
/// The document's CSS cursor where the mouse last was: "pointer", "text", ...; "auto" when it says nothing.
char *twl_cursor(twl_renderer *handle);
/// The master CSS documents are parsed with, the profile's UA layer with the theme's values in place.
char *twl_master_css(twl_renderer *handle);
/// The theme's variables on the root as a JSON object: `{"--tg-theme-bg-color": "#ffffff", ...}`.
char *twl_theme_variables(twl_renderer *handle);
/// Each height change then comes with a `resize` in the size message: what stayed put, and which side the
/// host keeps still (`prefers_bottom` breaks a tie towards the bottom).
void twl_set_resize_tracking(twl_renderer *handle, bool enabled, bool prefers_bottom);
/// One display frame: animations advance and coalesced moves go out. Call it each frame while twl_wants_tick holds,
/// otherwise after twl_next_tick_in seconds (infinity: nothing scheduled). Returns renderer → host messages.
char *twl_tick(twl_renderer *handle);
bool twl_wants_tick(twl_renderer *handle);
double twl_next_tick_in(twl_renderer *handle);
/// Hands transform and opacity animations to the platform compositor (src/compositor.h); off by default.
void twl_set_compositing(twl_renderer *handle, bool enabled);
/// The layers above the last paint as JSON (to_json(Composition)), and the renderer clock their tracks run on.
char *twl_composition(twl_renderer *handle);
double twl_clock(void);
/// Layer `index` of the last composition as a display list (document coordinates); NULL past the end.
char *twl_layer_content(twl_renderer *handle, uint32_t index);
/// Physical size (px) of the next render.
void twl_size(twl_renderer *handle, uint32_t *width, uint32_t *height);
bool twl_dirty(twl_renderer *handle);
/// Paints premultiplied RGBA8 into buffer (>= width*height*4 bytes). Returns the paint time in ms, negative on failure.
double twl_render(twl_renderer *handle, uint8_t *buffer, size_t capacity);
/// The size of an asynchronously decoded image (web backend); rebuilds the document. Returns renderer → host messages.
char *twl_image_ready(twl_renderer *handle, int32_t image, float width, float height);
/// The display list of the current document as JSON (src/display_list.h), CSS px; clears `dirty`. Release with twl_string_free.
char *twl_display_list(twl_renderer *handle);
/// Where the last display list differs from the one before, CSS px: `[[x, y, w, h], ...]`, or `null`
/// when everything must be painted. A host keeps its last frame and repaints only these areas.
char *twl_damage(twl_renderer *handle);
/// Drops caches that are made again on demand: on memory pressure, or when the widget goes off screen.
void twl_trim(twl_renderer *handle);
/// Diagnostics: the last rebuild as JSON (`serializeMs`, `parseLayoutMs`, `passes`, `htmlBytes`, `builds`, `displayOps`). Release with twl_string_free.
char *twl_stats(twl_renderer *handle);
/// Diagnostics: the HTML the document was last built from. Release with twl_string_free.
char *twl_html(twl_renderer *handle);
void twl_string_free(char *s);

#ifdef __cplusplus
}
#endif
#endif
