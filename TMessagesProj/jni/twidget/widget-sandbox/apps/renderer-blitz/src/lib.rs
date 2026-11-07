//! The Blitz renderer prototype: the renderer trust domain of docs/protocol.md
//! (validated `RenderOp`s in, layout answers and events out, pixels on
//! request) on blitz-dom + blitz-paint + vello_cpu. `renderer.rs` is the
//! platform-neutral core; `android.rs` is its JNI surface and `ffi.rs` the C
//! ABI the Swift glue calls. One `Renderer` per widget behind an opaque
//! handle, every call on the platform's main thread (the glue guarantees it),
//! so the document needs no lock. Panics are caught and reported, never
//! unwound into the host.

mod net;
mod renderer;
mod text;
#[cfg(target_vendor = "apple")]
pub mod cg;
pub mod dl;

#[cfg(target_os = "android")]
mod android;
#[cfg(not(target_os = "android"))]
mod ffi;

use renderer::Renderer;

/// Who rasterises the blitz-paint scene. `Vello` is the portable default
/// (vello_cpu); `CoreGraphics` (Apple only, src/cg.rs) hands the same scene to
/// the platform, so text and emoji come out as the system draws them;
/// `DisplayList` (src/dl.rs) records it for a platform executor (Android
/// `Canvas`) — `render()` still rasterises with vello, the glue asks for
/// `render_display_list()` instead. Process default from `TWB_BACKEND`
/// (`vello` | `cg` | `canvas`), per renderer via `twb_set_backend`.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Backend {
    Vello,
    CoreGraphics,
    DisplayList,
}

impl Backend {
    pub fn parse(name: &str) -> Option<Backend> {
        match name.trim().to_ascii_lowercase().as_str() {
            "vello" | "vello_cpu" | "cpu" => Some(Backend::Vello),
            "cg" | "coregraphics" | "native" if cfg!(target_vendor = "apple") => Some(Backend::CoreGraphics),
            "canvas" | "dl" | "displaylist" => Some(Backend::DisplayList),
            _ => None,
        }
    }

    pub fn name(self) -> &'static str {
        match self {
            Backend::Vello => "vello",
            Backend::CoreGraphics => "cg",
            Backend::DisplayList => "canvas",
        }
    }

    /// `TWB_BACKEND` from the environment, read once per process; Vello otherwise.
    pub fn from_env() -> Backend {
        static DEFAULT: OnceLock<Backend> = OnceLock::new();
        *DEFAULT.get_or_init(|| std::env::var("TWB_BACKEND").ok().and_then(|v| Backend::parse(&v)).unwrap_or(Backend::Vello))
    }
}
use serde_json::Value;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::OnceLock;

/// System fonts are scanned once per process; each renderer gets a clone sharing the source cache.
struct Fonts(blitz_dom::FontContext);
unsafe impl Send for Fonts {}
unsafe impl Sync for Fonts {}
static FONTS: OnceLock<Fonts> = OnceLock::new();

pub(crate) fn fonts() -> blitz_dom::FontContext {
    FONTS.get_or_init(|| Fonts(new_font_context())).0.clone()
}

/// One `FontContext` per process. On iOS the colour emoji font is found (`Apple
/// Color Emoji`, see `font_report`) but its `sbix` glyphs are Apple's proprietary
/// `emjc` bitmaps, which skrifa/glifo cannot decode, so emoji stay blank there;
/// macOS ships the same family as PNG and paints it. See the `font_probe` tests.
#[cfg(not(target_arch = "wasm32"))]
fn new_font_context() -> blitz_dom::FontContext {
    blitz_dom::FontContext::new()
}

/// Browsers expose no system fonts to wasm: one bundled face (DejaVu Sans,
/// Bitstream Vera licence) stands in for every generic family, so monospace
/// and serif text comes out sans and emoji stay blank. The web prototype is
/// dev tooling; a product build would ship the chat's own font set.
#[cfg(target_arch = "wasm32")]
fn new_font_context() -> blitz_dom::FontContext {
    use parley::fontique::{Blob, Collection, CollectionOptions, GenericFamily, SourceCache};
    use std::sync::Arc;
    const DEJAVU_SANS: &[u8] = include_bytes!("../assets/DejaVuSans.woff2");
    let mut ctx = blitz_dom::FontContext {
        source_cache: SourceCache::new_shared(),
        collection: Collection::new(CollectionOptions { shared: false, system_fonts: false }),
    };
    let bytes = blitz_dom::decode_font_bytes(DEJAVU_SANS).into_owned();
    let registered = ctx.collection.register_fonts(Blob::new(Arc::new(bytes) as _), None);
    let ids: Vec<_> = registered.iter().map(|(id, _)| *id).collect();
    for generic in [GenericFamily::SansSerif, GenericFamily::Serif, GenericFamily::Monospace, GenericFamily::SystemUi, GenericFamily::UiSansSerif, GenericFamily::UiMonospace] {
        ctx.collection.append_generic_families(generic, ids.iter().copied());
    }
    ctx
}

/// What the renderer's font collection knows (diagnostics): family count, the
/// families mapped to the generic `emoji` family, and every family with "emoji" in its name.
#[cfg_attr(target_os = "android", allow(dead_code))]
pub(crate) fn font_report() -> String {
    use parley::fontique::GenericFamily;
    let mut fcx = fonts();
    let names: Vec<String> = fcx.collection.family_names().map(str::to_string).collect();
    let generic: Vec<String> = fcx
        .collection
        .generic_families(GenericFamily::Emoji)
        .collect::<Vec<_>>()
        .into_iter()
        .filter_map(|id| fcx.collection.family_name(id).map(str::to_string))
        .collect();
    let emoji: Vec<&String> = names.iter().filter(|n| n.to_ascii_lowercase().contains("emoji")).collect();
    format!("{} families; generic emoji → {:?}; emoji families {:?}", names.len(), generic, emoji)
}

/// Runs `f` on the renderer behind `handle`; a null handle or a panic inside yields `default`.
pub(crate) fn with_renderer<T>(handle: *mut Renderer, default: T, f: impl FnOnce(&mut Renderer) -> T) -> T {
    if handle.is_null() {
        return default;
    }
    let r = unsafe { &mut *handle };
    match catch_unwind(AssertUnwindSafe(|| f(r))) {
        Ok(v) => v,
        Err(e) => {
            let msg = e.downcast_ref::<String>().cloned().or_else(|| e.downcast_ref::<&str>().map(|s| s.to_string())).unwrap_or_default();
            log::error!("renderer panic: {msg}");
            default
        }
    }
}

pub(crate) fn messages(out: Vec<Value>) -> String {
    Value::Array(out).to_string()
}
