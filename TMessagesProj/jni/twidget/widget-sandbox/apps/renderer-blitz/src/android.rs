//! JNI surface of the Blitz renderer (org.telegram.widgetsandbox.blitz.BlitzNative).
//! One `Renderer` per widget behind an opaque handle; every call happens on
//! the Android main thread (the Kotlin side guarantees it), so the document
//! needs no lock. Panics are caught and reported, never unwound into the JVM.

use crate::renderer::Renderer;
use crate::{fonts, messages, with_renderer};
use jni::objects::{JByteBuffer, JClass, JString};
use jni::sys::{jboolean, jdouble, jint, jlong, jstring};
use jni::JNIEnv;
use serde_json::Value;
use std::panic::catch_unwind;
use std::sync::OnceLock;

fn init_logging() {
    static ONCE: OnceLock<()> = OnceLock::new();
    ONCE.get_or_init(|| {
        android_logger::init_once(android_logger::Config::default().with_max_level(log::LevelFilter::Info).with_tag("twidget-blitz"));
    });
}

fn get_string(env: &mut JNIEnv, s: &JString) -> String {
    env.get_string(s).map(|j| j.into()).unwrap_or_default()
}

fn ret_string(env: &mut JNIEnv, s: &str) -> jstring {
    env.new_string(s).map(|j| j.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_create(
    mut env: JNIEnv, _c: JClass, width: jdouble, dpr: jdouble, theme: JString,
) -> jlong {
    init_logging();
    let dark = get_string(&mut env, &theme) == "dark";
    match catch_unwind(|| Renderer::new(width, dpr, dark, fonts())) {
        Ok(r) => Box::into_raw(Box::new(r)) as jlong,
        Err(_) => {
            log::error!("renderer creation panicked");
            0
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_destroy(_env: JNIEnv, _c: JClass, handle: jlong) {
    if handle != 0 {
        unsafe { drop(Box::from_raw(handle as *mut Renderer)) };
    }
}

/// Host → renderer message (JSON). Returns a JSON array of renderer → host messages.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_receive(
    mut env: JNIEnv, _c: JClass, handle: jlong, json: JString,
) -> jstring {
    let text = get_string(&mut env, &json);
    let out = with_renderer(handle as *mut Renderer, "[]".to_string(), |r| match serde_json::from_str::<Value>(&text) {
        Ok(msg) => messages(r.receive(&msg)),
        Err(e) => messages(vec![serde_json::json!({ "type": "violation", "detail": format!("bad message: {e}") })]),
    });
    ret_string(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_setWidth(
    mut env: JNIEnv, _c: JClass, handle: jlong, width: jdouble,
) -> jstring {
    let out = with_renderer(handle as *mut Renderer, "[]".to_string(), |r| {
        let mut out = Vec::new();
        r.set_width(width, &mut out);
        messages(out)
    });
    ret_string(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_setTheme(
    mut env: JNIEnv, _c: JClass, handle: jlong, theme: JString,
) -> jstring {
    let dark = get_string(&mut env, &theme) == "dark";
    let out = with_renderer(handle as *mut Renderer, "[]".to_string(), |r| {
        let mut out = Vec::new();
        r.set_theme(dark, &mut out);
        messages(out)
    });
    ret_string(&mut env, &out)
}

/// A touch in CSS px. action: 0 down, 1 move, 2 up, 3 cancel. Returns renderer → host messages.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_touch(
    mut env: JNIEnv, _c: JClass, handle: jlong, action: jint, x: jdouble, y: jdouble, pointer: jint, primary: jboolean,
) -> jstring {
    let out = with_renderer(handle as *mut Renderer, "[]".to_string(), |r| messages(r.touch(action, x, y, pointer, primary != 0)));
    ret_string(&mut env, &out)
}

/// Physical size of the next render: (width << 32) | height.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_size(_env: JNIEnv, _c: JClass, handle: jlong) -> jlong {
    with_renderer(handle as *mut Renderer, 0, |r| {
        let (w, h) = r.viewport_px();
        ((w as jlong) << 32) | (h as jlong)
    })
}

#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_dirty(_env: JNIEnv, _c: JClass, handle: jlong) -> jboolean {
    with_renderer(handle as *mut Renderer, 0, |r| r.dirty as jboolean)
}

/// Picks the rasteriser: "vello" (default, pixels through `render`) or "canvas" (the frame as a display
/// list through `displayList`, drawn by CanvasScene.kt). False for an unknown name.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_setBackend(
    mut env: JNIEnv, _c: JClass, handle: jlong, name: JString,
) -> jboolean {
    let Some(backend) = crate::Backend::parse(&get_string(&mut env, &name)) else { return 0 };
    with_renderer(handle as *mut Renderer, 0, |r| {
        r.set_backend(backend);
        1
    })
}

/// The frame as a display list (renderer-blitz/src/dl.rs format); empty on failure.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_displayList<'a>(
    env: JNIEnv<'a>, _c: JClass, handle: jlong,
) -> jni::objects::JByteArray<'a> {
    let bytes = with_renderer(handle as *mut Renderer, Vec::new(), |r| r.render_display_list());
    env.byte_array_from_slice(&bytes).unwrap_or_else(|_| jni::objects::JByteArray::default())
}

/// The font file behind a display-list font id as a direct ByteBuffer over memory the renderer keeps
/// alive for the process (fontique's blob), or null. The executor builds its Font from it once per id.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_fontBytes<'a>(
    mut env: JNIEnv<'a>, _c: JClass, id: jlong,
) -> jni::objects::JByteBuffer<'a> {
    let Some(blob) = crate::dl::font_blob(id as u64) else { return jni::objects::JByteBuffer::default() };
    let data = blob.data();
    let (ptr, len) = (data.as_ptr() as *mut u8, data.len());
    // The registry holds the blob (and its Arc'd bytes) forever; the buffer must not outlive the process either.
    std::mem::forget(blob);
    unsafe { env.new_direct_byte_buffer(ptr, len) }.unwrap_or_else(|_| jni::objects::JByteBuffer::default())
}

/// The decoded image behind a display-list image id as premultiplied RGBA8 rows (width × height × 4), or empty.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_imagePixels<'a>(
    env: JNIEnv<'a>, _c: JClass, id: jlong,
) -> jni::objects::JByteArray<'a> {
    let bytes = crate::dl::image(id as u64).map(|img| crate::dl::image_rgba_premultiplied(&img)).unwrap_or_default();
    env.byte_array_from_slice(&bytes).unwrap_or_else(|_| jni::objects::JByteArray::default())
}

/// Paints into a direct ByteBuffer of at least width*height*4 bytes (premultiplied RGBA8). Returns the paint time in ms.
#[no_mangle]
pub extern "system" fn Java_org_telegram_widgetsandbox_blitz_BlitzNative_render(
    env: JNIEnv, _c: JClass, handle: jlong, buffer: JByteBuffer,
) -> jdouble {
    let ptr = match env.get_direct_buffer_address(&buffer) {
        Ok(p) => p,
        Err(_) => return -1.0,
    };
    let cap = env.get_direct_buffer_capacity(&buffer).unwrap_or(0);
    with_renderer(handle as *mut Renderer, -1.0, |r| r.render_into(ptr, cap))
}
