//! C ABI of the Blitz renderer for the Swift glue (ios/DemoApp/Sources/BlitzRenderer.swift);
//! the contract is include/twidget_blitz.h. Strings returned by `twb_*` are
//! owned by the caller and released with `twb_string_free`.

use crate::renderer::Renderer;
use crate::{fonts, messages, with_renderer};
use serde_json::Value;
use std::ffi::{c_char, CStr, CString};
use std::panic::catch_unwind;
use std::sync::OnceLock;

/// Panics and refused fetches go to stderr (Xcode's console); nothing else logs at info.
struct StderrLogger;
impl log::Log for StderrLogger {
    fn enabled(&self, m: &log::Metadata) -> bool {
        m.level() <= log::Level::Warn
    }
    fn log(&self, r: &log::Record) {
        if self.enabled(r.metadata()) {
            eprintln!("twidget-blitz [{}] {}", r.level(), r.args());
        }
    }
    fn flush(&self) {}
}

fn init_logging() {
    static ONCE: OnceLock<()> = OnceLock::new();
    ONCE.get_or_init(|| {
        let _ = log::set_logger(&StderrLogger).map(|()| log::set_max_level(log::LevelFilter::Warn));
    });
}

unsafe fn c_str(s: *const c_char) -> String {
    if s.is_null() {
        return String::new();
    }
    CStr::from_ptr(s).to_string_lossy().into_owned()
}

fn ret_string(s: String) -> *mut c_char {
    CString::new(s).map(CString::into_raw).unwrap_or(std::ptr::null_mut())
}

/// `theme`: "dark" or anything else (light). Null on failure.
#[no_mangle]
pub unsafe extern "C" fn twb_create(width: f64, dpr: f64, theme: *const c_char) -> *mut Renderer {
    init_logging();
    let dark = c_str(theme) == "dark";
    match catch_unwind(|| Renderer::new(width, dpr, dark, fonts())) {
        Ok(r) => Box::into_raw(Box::new(r)),
        Err(_) => {
            log::error!("renderer creation panicked");
            std::ptr::null_mut()
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn twb_destroy(handle: *mut Renderer) {
    if !handle.is_null() {
        drop(Box::from_raw(handle));
    }
}

/// Host → renderer message (JSON). Returns a JSON array of renderer → host messages.
#[no_mangle]
pub unsafe extern "C" fn twb_receive(handle: *mut Renderer, json: *const c_char) -> *mut c_char {
    let text = c_str(json);
    ret_string(with_renderer(handle, "[]".to_string(), |r| match serde_json::from_str::<Value>(&text) {
        Ok(msg) => messages(r.receive(&msg)),
        Err(e) => messages(vec![serde_json::json!({ "type": "violation", "detail": format!("bad message: {e}") })]),
    }))
}

#[no_mangle]
pub unsafe extern "C" fn twb_set_width(handle: *mut Renderer, width: f64) -> *mut c_char {
    ret_string(with_renderer(handle, "[]".to_string(), |r| {
        let mut out = Vec::new();
        r.set_width(width, &mut out);
        messages(out)
    }))
}

#[no_mangle]
pub unsafe extern "C" fn twb_set_theme(handle: *mut Renderer, theme: *const c_char) -> *mut c_char {
    let dark = c_str(theme) == "dark";
    ret_string(with_renderer(handle, "[]".to_string(), |r| {
        let mut out = Vec::new();
        r.set_theme(dark, &mut out);
        messages(out)
    }))
}

/// A touch in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Returns renderer → host messages.
#[no_mangle]
pub unsafe extern "C" fn twb_touch(handle: *mut Renderer, action: i32, x: f64, y: f64, pointer: i32, primary: bool) -> *mut c_char {
    ret_string(with_renderer(handle, "[]".to_string(), |r| messages(r.touch(action, x, y, pointer, primary))))
}

/// Physical size of the next render.
#[no_mangle]
pub unsafe extern "C" fn twb_size(handle: *mut Renderer, width: *mut u32, height: *mut u32) {
    let (w, h) = with_renderer(handle, (0, 0), |r| r.viewport_px());
    if !width.is_null() {
        *width = w;
    }
    if !height.is_null() {
        *height = h;
    }
}

/// Picks the rasteriser: "vello" (default) or "cg" (CoreGraphics/CoreText, Apple only).
/// False when the name is unknown or unavailable on this platform.
#[no_mangle]
pub unsafe extern "C" fn twb_set_backend(handle: *mut Renderer, name: *const c_char) -> bool {
    let Some(backend) = crate::Backend::parse(&c_str(name)) else { return false };
    with_renderer(handle, false, |r| {
        r.set_backend(backend);
        true
    })
}

/// The active rasteriser's name ("vello" | "cg"); static, not to be freed.
#[no_mangle]
pub unsafe extern "C" fn twb_backend(handle: *mut Renderer) -> *const c_char {
    match with_renderer(handle, crate::Backend::Vello, |r| r.backend()) {
        crate::Backend::Vello => c"vello".as_ptr(),
        crate::Backend::CoreGraphics => c"cg".as_ptr(),
        crate::Backend::DisplayList => c"canvas".as_ptr(),
    }
}

#[no_mangle]
pub unsafe extern "C" fn twb_dirty(handle: *mut Renderer) -> bool {
    with_renderer(handle, false, |r| r.dirty)
}

/// Paints into `buffer` (at least width*height*4 bytes, premultiplied RGBA8). Returns the paint time in ms, negative on failure.
#[no_mangle]
pub unsafe extern "C" fn twb_render(handle: *mut Renderer, buffer: *mut u8, capacity: usize) -> f64 {
    if buffer.is_null() {
        return -1.0;
    }
    with_renderer(handle, -1.0, |r| r.render_into(buffer, capacity))
}

/// Diagnostics: the font collection (family count, emoji mapping). Release with `twb_string_free`.
#[no_mangle]
pub extern "C" fn twb_font_report() -> *mut c_char {
    init_logging();
    ret_string(match catch_unwind(crate::font_report) {
        Ok(s) => s,
        Err(_) => "font report panicked".to_string(),
    })
}

#[no_mangle]
pub unsafe extern "C" fn twb_string_free(s: *mut c_char) {
    if !s.is_null() {
        drop(CString::from_raw(s));
    }
}

/// Caller-owned bytes in the renderer's heap (the wasm glue writes its UTF-8
/// strings and takes its pixel buffer here); release with `twb_free` and the same length.
#[no_mangle]
pub extern "C" fn twb_alloc(len: usize) -> *mut u8 {
    let mut v: Vec<u8> = Vec::with_capacity(len.max(1));
    let p = v.as_mut_ptr();
    std::mem::forget(v);
    p
}

#[no_mangle]
pub unsafe extern "C" fn twb_free(p: *mut u8, len: usize) {
    if !p.is_null() {
        drop(Vec::from_raw_parts(p, 0, len.max(1)));
    }
}
