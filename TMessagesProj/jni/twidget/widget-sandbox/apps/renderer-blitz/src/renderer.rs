//! The renderer trust domain of docs/protocol.md on Blitz: validated
//! `RenderOp`s in, layout answers and `WidgetEvent`s out, pixels on request.
//! Mirrors src/renderer/renderer.ts; nothing here parses HTML or runs script.

use crate::net::DataOnlyNet;
use crate::Backend;
use anyrender::render_to_buffer;
use anyrender_vello_cpu::VelloCpuImageRenderer;
use blitz_dom::{
    local_name, ns, Attribute, BaseDocument, Document, DocumentConfig, EventDriver, EventHandler,
    FontContext, LocalName, NodeData, NodeId, PlainDocument, QualName,
};
use blitz_paint::paint_scene;
use blitz_traits::events::{
    BlitzPointerEvent, BlitzPointerId, DomEvent, DomEventData, EventState, MouseEventButton,
    MouseEventButtons, PointerCoords, PointerDetails, UiEvent,
};
use blitz_traits::shell::{ColorScheme, Viewport};
use serde_json::{json, Map, Value};
use std::collections::{HashMap, HashSet};
use std::sync::Arc;

const MIN_HEIGHT: f64 = 24.0;
const MAX_HEIGHT: f64 = 900.0;
const ALWAYS_FORWARD: &[&str] = &["click", "input", "change", "submit", "reset", "load", "error", "toggle"];
/// A finger jitters a few px during a tap; Blitz turns any move beyond 2 px into a document pan
/// and then drops the click. Moves stay inside the renderer until the finger leaves this radius
/// (CSS px), like the platforms' own tap slop.
const TAP_SLOP: f64 = 8.0;

pub struct Renderer {
    doc: PlainDocument,
    fonts: FontContext,
    net: Arc<DataOnlyNet>,
    /// widget node id → Blitz node id, and back
    nodes: HashMap<i64, NodeId>,
    ids: HashMap<NodeId, i64>,
    html: NodeId,
    head: NodeId,
    body: NodeId,
    width: f64,
    dpr: f64,
    dark: bool,
    /// Viewport height in CSS px (the document decides it, like the iframe on the web).
    vp_height: u32,
    doc_height: f64,
    interest: HashSet<String>,
    prevent: HashSet<String>,
    frozen: bool,
    last_size: String,
    pub dirty: bool,
    /// Where each pressed pointer went down, and whether it has left the tap slop since.
    touch_down: HashMap<i32, (f64, f64)>,
    touch_moved: HashSet<i32>,
    backend: Backend,
}

fn qn(tag: &str, svg: bool) -> QualName {
    QualName::new(None, if svg { ns!(svg) } else { ns!(html) }, LocalName::from(tag))
}

fn attr_name(name: &str) -> QualName {
    QualName::new(None, ns!(), LocalName::from(name))
}

/// Milliseconds on a monotonic clock. `std::time::Instant` is unavailable on
/// wasm32-unknown-unknown (it panics), so there the paint time is 0 and the
/// JS glue measures around the call.
#[cfg(not(target_arch = "wasm32"))]
fn now() -> f64 {
    use std::sync::OnceLock;
    use std::time::Instant;
    static EPOCH: OnceLock<Instant> = OnceLock::new();
    EPOCH.get_or_init(Instant::now).elapsed().as_secs_f64() * 1000.0
}
#[cfg(target_arch = "wasm32")]
fn now() -> f64 {
    0.0
}

fn rect_json(x: f64, y: f64, w: f64, h: f64) -> Value {
    json!({ "x": x, "y": y, "width": w, "height": h, "top": y, "left": x, "right": x + w, "bottom": y + h })
}

impl Renderer {
    pub fn new(width: f64, dpr: f64, dark: bool, fonts: FontContext) -> Self {
        let net = Arc::new(DataOnlyNet::new());
        let vp_height = MIN_HEIGHT as u32;
        let mut base = BaseDocument::new(DocumentConfig {
            viewport: Some(Self::viewport(width, vp_height, dpr, dark)),
            net_provider: Some(net.clone() as _),
            font_ctx: Some(fonts.clone()),
            ..Default::default()
        });
        // Chromium/WebKit UA rules Blitz's stylesheet lacks and widgets rely on.
        base.add_user_agent_stylesheet("button,input,select,textarea,meter,progress{box-sizing:border-box}");
        let root = base.root_node().id;
        let (html, head, body) = {
            let mut m = base.mutate();
            let html = m.create_element(qn("html", false), vec![]);
            let head = m.create_element(qn("head", false), vec![]);
            let body = m.create_element(qn("body", false), vec![]);
            m.append_children(html, &[head, body]);
            m.append_children(root, &[html]);
            (html, head, body)
        };
        Self {
            doc: PlainDocument(base),
            fonts,
            net,
            nodes: HashMap::new(),
            ids: HashMap::new(),
            html,
            head,
            body,
            width,
            dpr,
            dark,
            vp_height,
            doc_height: 0.0,
            interest: HashSet::new(),
            prevent: HashSet::new(),
            frozen: false,
            last_size: String::new(),
            dirty: true,
            touch_down: HashMap::new(),
            touch_moved: HashSet::new(),
            backend: Backend::from_env(),
        }
    }

    fn viewport(width: f64, vp_height: u32, dpr: f64, dark: bool) -> Viewport {
        Viewport::new(
            (width * dpr).ceil() as u32,
            ((vp_height as f64) * dpr).ceil() as u32,
            dpr as f32,
            if dark { ColorScheme::Dark } else { ColorScheme::Light },
        )
    }

    fn base(&self) -> &BaseDocument {
        &self.doc.0
    }

    fn base_mut(&mut self) -> &mut BaseDocument {
        &mut self.doc.0
    }

    /// Host → renderer message. Returns renderer → host messages.
    pub fn receive(&mut self, msg: &Value) -> Vec<Value> {
        let mut out = Vec::new();
        match msg.get("type").and_then(Value::as_str).unwrap_or("") {
            "ops" => {
                if let Some(ops) = msg.get("ops").and_then(Value::as_array) {
                    for op in ops {
                        self.apply_op(op, &mut out);
                    }
                }
                self.layout();
                self.dirty = true;
                self.report_size(&mut out);
            }
            "query" => {
                let qid = msg.get("qid").cloned().unwrap_or(Value::Null);
                let result = match msg.get("query") {
                    Some(q) => self.answer(q),
                    None => json!({ "error": "no query" }),
                };
                out.push(json!({ "type": "result", "qid": qid, "result": result }));
            }
            "listen" => {
                self.interest = msg
                    .get("types")
                    .and_then(Value::as_array)
                    .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
                    .unwrap_or_default();
            }
            "preventDefault" => {
                self.prevent = msg
                    .get("keys")
                    .and_then(Value::as_array)
                    .map(|a| a.iter().filter_map(Value::as_str).map(str::to_string).collect())
                    .unwrap_or_default();
            }
            "frozen" => {
                let reason = msg.get("reason").and_then(Value::as_str).unwrap_or("").to_string();
                let fatal = msg.get("fatal").and_then(Value::as_bool).unwrap_or(false);
                self.freeze(&reason, fatal);
                self.layout();
                self.dirty = true;
            }
            _ => {}
        }
        self.drain_blocked(&mut out);
        out
    }

    pub fn set_width(&mut self, width: f64, out: &mut Vec<Value>) {
        if (width - self.width).abs() < 0.01 {
            return;
        }
        self.width = width;
        let vp = Self::viewport(width, self.vp_height, self.dpr, self.dark);
        self.base_mut().set_viewport(vp);
        self.layout();
        self.dirty = true;
        self.report_size(out);
    }

    /// The chat theme changed: `prefers-color-scheme` inside the document follows.
    pub fn set_theme(&mut self, dark: bool, out: &mut Vec<Value>) {
        if dark == self.dark {
            return;
        }
        self.dark = dark;
        let vp = Self::viewport(self.width, self.vp_height, self.dpr, self.dark);
        self.base_mut().set_viewport(vp);
        self.layout();
        self.dirty = true;
        self.report_size(out);
    }

    fn drain_blocked(&mut self, out: &mut Vec<Value>) {
        let blocked: Vec<String> = std::mem::take(&mut *self.net.blocked.lock().unwrap());
        for url in blocked {
            out.push(json!({ "type": "violation", "detail": format!("fetch refused: {url}") }));
        }
    }

    // ───────────────────────── ops ─────────────────────────

    fn apply_op(&mut self, op: &Value, out: &mut Vec<Value>) {
        let kind = op.get("op").and_then(Value::as_str).unwrap_or("");
        let id = op.get("id").and_then(Value::as_i64).unwrap_or(0);
        let s = |k: &str| op.get(k).and_then(Value::as_str).unwrap_or("").to_string();
        match kind {
            "el" => {
                let tag = s("tag").to_lowercase();
                let adopt = s("adopt");
                let svg = op.get("ns").and_then(Value::as_str) == Some("svg");
                let nid = match adopt.as_str() {
                    "html" => self.html,
                    "head" => self.head,
                    "body" => self.body,
                    _ => {
                        let attrs = op
                            .get("attrs")
                            .and_then(Value::as_array)
                            .map(|a| {
                                a.iter()
                                    .filter_map(|pair| {
                                        let p = pair.as_array()?;
                                        let name = p.first()?.as_str()?;
                                        let value = p.get(1)?.as_str()?;
                                        if name.to_ascii_lowercase().starts_with("on") {
                                            return None;
                                        }
                                        Some(Attribute { name: attr_name(name), value: value.to_string() })
                                    })
                                    .collect::<Vec<_>>()
                            })
                            .unwrap_or_default();
                        let mut m = self.doc.0.mutate();
                        m.create_element(qn(&tag, svg), attrs)
                    }
                };
                self.register(id, nid);
                if !adopt.is_empty() {
                    // attrs on an adopted element are applied one by one
                    if let Some(a) = op.get("attrs").and_then(Value::as_array) {
                        for pair in a {
                            if let (Some(name), Some(value)) = (pair.get(0).and_then(Value::as_str), pair.get(1).and_then(Value::as_str)) {
                                self.doc.0.mutate().set_attribute(nid, attr_name(name), value);
                            }
                        }
                    }
                }
            }
            "text" => {
                let nid = self.doc.0.mutate().create_text_node(&s("data"));
                self.register(id, nid);
            }
            "comment" => {}
            _ => {
                let Some(&nid) = self.nodes.get(&id) else { return };
                let is_root = nid == self.html || nid == self.head || nid == self.body;
                match kind {
                    "attr" => {
                        let name = s("name");
                        if name.to_ascii_lowercase().starts_with("on") {
                            out.push(json!({ "type": "violation", "detail": format!("attribute {name}") }));
                            return;
                        }
                        self.doc.0.mutate().set_attribute(nid, attr_name(&name), &s("value"));
                    }
                    "rattr" => self.doc.0.mutate().clear_attribute(nid, attr_name(&s("name"))),
                    "data" => self.doc.0.mutate().set_node_text(nid, &s("data")),
                    "ins" => {
                        if is_root {
                            return;
                        }
                        let parent = op.get("parent").and_then(Value::as_i64).unwrap_or(0);
                        let mut m = self.doc.0.mutate();
                        if parent == 0 {
                            if m.parent_id(nid).is_some() {
                                m.remove_node(nid);
                            }
                            return;
                        }
                        let Some(&pnid) = self.nodes.get(&parent) else { return };
                        if m.parent_id(nid).is_some() {
                            m.remove_node(nid);
                        }
                        let before = op.get("before").and_then(Value::as_i64).and_then(|b| self.nodes.get(&b).copied());
                        match before {
                            Some(bnid) if m.parent_id(bnid) == Some(pnid) => m.insert_nodes_before(bnid, &[nid]),
                            _ => m.append_children(pnid, &[nid]),
                        }
                    }
                    "rm" => {
                        if is_root {
                            return;
                        }
                        self.forget(nid);
                        self.doc.0.mutate().remove_and_drop_node(nid);
                    }
                    "style" => {
                        let name = s("name");
                        let value = s("value");
                        let mut m = self.doc.0.mutate();
                        if value.is_empty() {
                            m.remove_style_property(nid, &name);
                        } else {
                            m.set_style_property(nid, &name, &value);
                        }
                    }
                    "css" => {
                        let text = s("text");
                        let is_style = matches!(self.doc.0.get_node(nid).map(|n| &n.data), Some(NodeData::Element(el)) if el.name.local == local_name!("style"));
                        if !is_style {
                            return;
                        }
                        {
                            let mut m = self.doc.0.mutate();
                            let child = m.child_ids(nid).first().copied();
                            match child {
                                Some(c) => m.set_node_text(c, &text),
                                None => {
                                    let c = m.create_text_node(&text);
                                    m.append_children(nid, &[c]);
                                }
                            }
                        }
                        self.doc.0.process_style_element(nid);
                    }
                    "prop" => self.apply_prop(nid, &s("name"), op.get("value").unwrap_or(&Value::Null)),
                    "call" => self.apply_call(nid, &s("method"), op.get("args").and_then(Value::as_array)),
                    "canvas" => {
                        // TODO: replay CanvasCmd on a custom paint source; the element renders empty for now.
                    }
                    _ => {}
                }
            }
        }
    }

    fn register(&mut self, id: i64, nid: NodeId) {
        self.nodes.insert(id, nid);
        self.ids.insert(nid, id);
    }

    fn forget(&mut self, nid: NodeId) {
        let mut stack = vec![nid];
        while let Some(n) = stack.pop() {
            if let Some(id) = self.ids.remove(&n) {
                self.nodes.remove(&id);
            }
            if let Some(node) = self.doc.0.get_node(n) {
                stack.extend(node.children.iter().copied());
            }
        }
    }

    fn apply_prop(&mut self, nid: NodeId, name: &str, value: &Value) {
        match name {
            "value" => {
                let text = match value {
                    Value::String(s) => s.clone(),
                    Value::Null => String::new(),
                    v => v.to_string(),
                };
                // `set_text` alone leaves the editor's layout dirty, and blitz-paint
                // unwraps it on the next frame (node.rs text_input_v_centering_offset).
                self.doc.0.with_text_input(nid, |mut drv| {
                    drv.editor.set_text(&text);
                    drv.refresh_layout();
                });
            }
            "checked" => {
                let v = value.as_bool().unwrap_or(false);
                if let Some(c) = self.doc.0.get_node_mut(nid).and_then(|n| n.element_data_mut()).and_then(|el| el.checkbox_input_checked_mut()) {
                    *c = v;
                }
            }
            "scrollTop" | "scrollLeft" => {
                let v = value.as_f64().unwrap_or(0.0);
                if let Some(node) = self.doc.0.get_node_mut(nid) {
                    let off = node.scroll_offset_mut();
                    if name == "scrollTop" { off.y = v } else { off.x = v }
                }
            }
            _ => {}
        }
    }

    fn apply_call(&mut self, nid: NodeId, method: &str, args: Option<&Vec<Value>>) {
        let num = |i: usize| args.and_then(|a| a.get(i)).and_then(Value::as_f64).unwrap_or(0.0);
        match method {
            "focus" => {
                self.doc.0.set_focus_to(nid);
            }
            "blur" => self.doc.0.clear_focus(),
            "scrollTo" => {
                if let Some(node) = self.doc.0.get_node_mut(nid) {
                    let off = node.scroll_offset_mut();
                    off.x = num(0);
                    off.y = num(1);
                }
            }
            "scrollBy" => {
                if let Some(node) = self.doc.0.get_node_mut(nid) {
                    let off = node.scroll_offset_mut();
                    off.x += num(0);
                    off.y += num(1);
                }
            }
            _ => {}
        }
    }

    // ───────────────────────── layout & size ─────────────────────────

    fn root_height(&self) -> f64 {
        self.doc.0.get_node(self.html).map(|n| n.final_layout().size.height as f64).unwrap_or(0.0)
    }

    fn layout(&mut self) {
        // Like the web iframe: the viewport follows the document height (100vh must not explode), converging in a few passes.
        for _ in 0..4 {
            self.doc.0.resolve(0.0);
            let h = self.root_height().max(MIN_HEIGHT).min(MAX_HEIGHT).ceil() as u32;
            if h == self.vp_height {
                break;
            }
            self.vp_height = h;
            let vp = Self::viewport(self.width, h, self.dpr, self.dark);
            self.doc.0.set_viewport(vp);
        }
        self.doc_height = self.root_height().ceil();
    }

    fn report_size(&mut self, out: &mut Vec<Value>) {
        let msg = json!({
            "type": "size", "width": self.width, "height": self.doc_height,
            "innerWidth": self.width, "innerHeight": self.vp_height, "dpr": self.dpr,
        });
        let key = msg.to_string();
        if key != self.last_size {
            self.last_size = key;
            out.push(msg);
        }
    }

    pub fn viewport_px(&self) -> (u32, u32) {
        let vp = self.base().viewport();
        vp.window_size
    }

    /// Paints into a caller-owned buffer of `capacity` bytes (premultiplied RGBA8,
    /// `viewport_px()` sized). Returns the paint time in ms, -2 when the buffer is too small.
    pub fn render_into(&mut self, buffer: *mut u8, capacity: usize) -> f64 {
        let t0 = now();
        let (w, h) = self.viewport_px();
        let need = (w as usize) * (h as usize) * 4;
        if capacity < need {
            return -2.0;
        }
        let pixels = self.render();
        unsafe { std::ptr::copy_nonoverlapping(pixels.as_ptr(), buffer, need.min(pixels.len())) };
        now() - t0
    }

    #[cfg_attr(target_os = "android", allow(dead_code))]
    pub fn backend(&self) -> Backend {
        self.backend
    }

    pub fn set_backend(&mut self, backend: Backend) {
        if self.backend != backend {
            self.backend = backend;
            self.dirty = true;
        }
    }

    /// The frame as a display list (src/dl.rs) for a platform executor, `viewport_px()` sized.
    #[cfg_attr(not(target_os = "android"), allow(dead_code))]
    pub fn render_display_list(&mut self) -> Vec<u8> {
        let (w, h) = self.viewport_px();
        let scale = self.dpr;
        let doc = &mut self.doc.0;
        doc.resolve(0.0);
        self.dirty = false;
        crate::dl::record(doc, scale, w, h)
    }

    /// Premultiplied RGBA8, `viewport_px()` sized.
    pub fn render(&mut self) -> Vec<u8> {
        let (w, h) = self.viewport_px();
        let scale = self.dpr;
        let doc = &mut self.doc.0;
        doc.resolve(0.0);
        self.dirty = false;
        match self.backend {
            #[cfg(target_vendor = "apple")]
            Backend::CoreGraphics => render_to_buffer::<crate::cg::CgImageRenderer, _>(|scene| paint_scene(scene, doc, scale, w, h, 0, 0), w, h),
            _ => render_to_buffer::<VelloCpuImageRenderer, _>(|scene| paint_scene(scene, doc, scale, w, h, 0, 0), w, h),
        }
    }

    // ───────────────────────── queries ─────────────────────────

    fn answer(&mut self, q: &Value) -> Value {
        let kind = q.get("q").and_then(Value::as_str).unwrap_or("");
        let nid = q.get("id").and_then(Value::as_i64).and_then(|id| self.nodes.get(&id).copied());
        if q.get("id").is_some() {
            match nid {
                Some(n) if matches!(self.doc.0.get_node(n).map(|x| &x.data), Some(NodeData::Element(_))) => {}
                _ => return json!({ "error": "no such element" }),
            }
        }
        match kind {
            "rect" => {
                let nid = nid.unwrap();
                match self.doc.0.get_client_bounding_rect(nid) {
                    Some(r) => rect_json(r.x, r.y, r.width, r.height),
                    None => rect_json(0.0, 0.0, 0.0, 0.0),
                }
            }
            "rects" => {
                let nid = nid.unwrap();
                match self.doc.0.get_client_bounding_rect(nid) {
                    Some(r) => json!([rect_json(r.x, r.y, r.width, r.height)]),
                    None => json!([]),
                }
            }
            "box" => {
                let nid = nid.unwrap();
                let Some(node) = self.doc.0.get_node(nid) else { return json!({ "error": "no such element" }) };
                let l = node.final_layout();
                let rect = self.doc.0.get_client_bounding_rect(nid);
                let (x, y) = rect.map(|r| (r.x, r.y)).unwrap_or((l.location.x as f64, l.location.y as f64));
                let parent = node.parent.and_then(|p| self.ids.get(&p).copied()).unwrap_or(0);
                let overflow = node.scrollable_overflow();
                let off = node.scroll_offset();
                let cw = (l.size.width - l.border.left - l.border.right).max(0.0);
                let ch = (l.size.height - l.border.top - l.border.bottom).max(0.0);
                json!({
                    "offsetWidth": l.size.width, "offsetHeight": l.size.height, "offsetTop": y, "offsetLeft": x,
                    "offsetParent": parent,
                    "clientWidth": cw, "clientHeight": ch, "clientTop": l.border.top, "clientLeft": l.border.left,
                    "scrollWidth": overflow.width().max(cw as f64), "scrollHeight": overflow.height().max(ch as f64),
                    "scrollTop": off.y, "scrollLeft": off.x,
                })
            }
            "cs" => {
                let nid = nid.unwrap();
                if let Some(prop) = q.get("prop").and_then(Value::as_str) {
                    return Value::String(self.doc.0.resolved_style_value(nid, prop));
                }
                let mut out = Map::new();
                for name in blitz_dom::resolved_style_property_names() {
                    out.insert((*name).to_string(), Value::String(self.doc.0.resolved_style_value(nid, name)));
                }
                Value::Object(out)
            }
            "elementFromPoint" => {
                let x = q.get("x").and_then(Value::as_f64).unwrap_or(0.0) as f32;
                let y = q.get("y").and_then(Value::as_f64).unwrap_or(0.0) as f32;
                let id = self.doc.0.element_from_point(x, y).and_then(|n| self.widget_id(n)).unwrap_or(0);
                json!(id)
            }
            "measureText" => {
                let font = q.get("font").and_then(Value::as_str).unwrap_or("10px sans-serif");
                let text = q.get("text").and_then(Value::as_str).unwrap_or("");
                crate::text::measure(&mut self.fonts, font, text)
            }
            "validity" => json!({ "valid": true, "message": "" }),
            "matchMedia" => {
                let query = q.get("query").and_then(Value::as_str).unwrap_or("");
                json!({ "matches": self.match_media(query) })
            }
            "viewport" => json!({ "innerWidth": self.width, "innerHeight": self.vp_height, "dpr": self.dpr }),
            "svg" | "imageData" | "canvasCall" => json!({ "error": format!("{kind} unsupported by the Blitz renderer") }),
            _ => json!({ "error": "unknown query" }),
        }
    }

    fn match_media(&self, query: &str) -> bool {
        let q = query.to_ascii_lowercase();
        if q.contains("prefers-color-scheme") {
            return q.contains("dark") == self.dark;
        }
        let px = |key: &str| -> Option<f64> {
            let i = q.find(key)?;
            let rest = &q[i + key.len()..];
            let rest = rest.trim_start_matches([' ', ':']);
            let num: String = rest.chars().take_while(|c| c.is_ascii_digit() || *c == '.').collect();
            num.parse().ok()
        };
        if let Some(v) = px("max-width") {
            return self.width <= v;
        }
        if let Some(v) = px("min-width") {
            return self.width >= v;
        }
        if q.contains("hover") {
            return q.contains("none");
        }
        if q.contains("pointer") {
            return q.contains("coarse");
        }
        false
    }

    /// Widget id of a Blitz node, or of its nearest mapped ancestor.
    fn widget_id(&self, mut nid: NodeId) -> Option<i64> {
        loop {
            if let Some(&id) = self.ids.get(&nid) {
                return Some(id);
            }
            nid = self.doc.0.get_node(nid)?.parent?;
        }
    }

    // ───────────────────────── events ─────────────────────────

    /// A touch from the host view, in CSS px. `action`: 0 down, 1 move, 2 up, 3 cancel.
    pub fn touch(&mut self, action: i32, x: f64, y: f64, pointer: i32, primary: bool) -> Vec<Value> {
        let mut out = Vec::new();
        if self.frozen {
            return out;
        }
        match action {
            0 => {
                self.touch_down.insert(pointer, (x, y));
                self.touch_moved.remove(&pointer);
            }
            1 => {
                if !self.touch_moved.contains(&pointer) {
                    if let Some(&(x0, y0)) = self.touch_down.get(&pointer) {
                        if (x - x0).abs() < TAP_SLOP && (y - y0).abs() < TAP_SLOP {
                            return out;
                        }
                    }
                    self.touch_moved.insert(pointer);
                }
            }
            _ => {
                self.touch_down.remove(&pointer);
                self.touch_moved.remove(&pointer);
            }
        }
        // A move counts as pressed only while that pointer is down: a mouse hovering over the
        // document (web, macOS) must not become a drag, which Blitz turns into a document pan.
        let pressed = action == 0 || (action == 1 && self.touch_down.contains_key(&pointer));
        let ev = BlitzPointerEvent {
            id: BlitzPointerId::Finger(pointer as u64),
            is_primary: primary,
            coords: PointerCoords {
                page_x: x as f32, page_y: y as f32, screen_x: x as f32, screen_y: y as f32, client_x: x as f32, client_y: y as f32,
            },
            button: MouseEventButton::Main,
            buttons: if pressed { MouseEventButtons::Primary } else { MouseEventButtons::None },
            mods: Default::default(),
            details: PointerDetails { pressure: if pressed { 0.5 } else { 0.0 }, ..Default::default() },
            element: blitz_traits::events::Point { x: 0.0, y: 0.0 },
            active_pointers: Default::default(),
        };
        let ui = match action {
            0 => UiEvent::PointerDown(ev),
            1 => UiEvent::PointerMove(ev),
            2 => UiEvent::PointerUp(ev),
            _ => UiEvent::PointerCancel(ev),
        };
        if action == 0 && primary {
            let target = self.doc.0.element_from_point(x as f32, y as f32);
            let wants = target.map(|t| self.wants_gesture(t)).unwrap_or(false);
            out.push(json!({ "type": "gesture", "wants": wants }));
        }
        let mut events = Vec::new();
        {
            let handler = Collector {
                ids: &self.ids,
                interest: &self.interest,
                prevent: &self.prevent,
                out: &mut events,
            };
            let doc: &mut dyn Document = &mut self.doc;
            let mut driver = EventDriver::new(doc, handler);
            driver.handle_ui_event(ui);
        }
        // Real input only (the document has no script that could dispatch events): trusted, like the
        // WebView renderer's isTrusted, which the hosts require before they count a user gesture.
        for ev in events {
            out.push(json!({ "type": "event", "ev": ev, "trusted": true }));
        }
        self.dirty = true;
        out
    }

    fn wants_gesture(&self, target: NodeId) -> bool {
        let mut cur = Some(target);
        while let Some(n) = cur {
            if n == self.html {
                break;
            }
            if let Some(id) = self.ids.get(&n) {
                if self.prevent.contains(&format!("{id}:pointerdown")) || self.prevent.contains(&format!("{id}:touchstart")) {
                    return true;
                }
            }
            let Some(node) = self.doc.0.get_node(n) else { break };
            if let NodeData::Element(el) = &node.data {
                if el.name.local == local_name!("input") && el.attr(local_name!("type")) == Some("range") {
                    return true;
                }
                let ta = self.doc.0.resolved_style_value(n, "touch-action");
                if ta == "none" {
                    return true;
                }
                if ta != "auto" && ta != "manipulation" && !ta.contains("pan-y") && !ta.contains("pan-up") && !ta.contains("pan-down") && !ta.is_empty() {
                    return true;
                }
                let oy = self.doc.0.resolved_style_value(n, "overflow-y");
                if (oy == "auto" || oy == "scroll") && node.scrollable_overflow().height() > node.final_layout().size.height as f64 + 1.0 {
                    return true;
                }
            }
            cur = node.parent;
        }
        false
    }

    // ───────────────────────── frozen ─────────────────────────

    fn freeze(&mut self, reason: &str, fatal: bool) {
        if self.frozen {
            return;
        }
        self.frozen = true;
        let (html, head, body) = (self.html, self.head, self.body);
        let mut m = self.doc.0.mutate();
        m.set_attribute(html, attr_name("data-tw-frozen"), if fatal { "fatal" } else { "paused" });
        let style = m.create_element(qn("style", false), vec![]);
        let css = m.create_text_node(
            "[data-tw-frozen]{pointer-events:none}[data-tw-frozen=fatal]{filter:grayscale(.3)}\
             [data-tw-badge]{position:fixed;right:6px;bottom:6px;z-index:2147483647;font:11px/1.3 system-ui,sans-serif;color:#fff;background:rgba(60,60,60,.75);padding:2px 7px;border-radius:9px;pointer-events:none}",
        );
        m.append_children(style, &[css]);
        m.append_children(head, &[style]);
        let badge = m.create_element(qn("div", false), vec![Attribute { name: attr_name("data-tw-badge"), value: if fatal { "fatal" } else { "paused" }.into() }]);
        let text = m.create_text_node(reason);
        m.append_children(badge, &[text]);
        m.append_children(body, &[badge]);
        drop(m);
        self.doc.0.process_style_element(style);
    }
}

/// Sees every DOM event Blitz dispatches, before its default action; applies the
/// learned `preventDefault` keys and serializes what the sandbox listens for.
struct Collector<'a> {
    ids: &'a HashMap<NodeId, i64>,
    interest: &'a HashSet<String>,
    prevent: &'a HashSet<String>,
    out: &'a mut Vec<Value>,
}

impl EventHandler for Collector<'_> {
    fn handle_event(&mut self, chain: &[NodeId], event: &mut DomEvent, doc: &mut dyn Document, state: &mut EventState) {
        let name = event.name();
        let target_id = chain.iter().find_map(|n| self.ids.get(n).copied()).unwrap_or(0);
        if target_id == 0 {
            return;
        }
        if event.cancelable && (self.prevent.contains(&format!("{target_id}:{name}"))) {
            state.prevent_default();
        }
        if !ALWAYS_FORWARD.contains(&name) && !self.interest.contains(name) {
            return;
        }
        let mut ev = Map::new();
        ev.insert("type".into(), json!(name));
        ev.insert("targetId".into(), json!(target_id));
        match &event.data {
            DomEventData::PointerMove(p) | DomEventData::PointerDown(p) | DomEventData::PointerUp(p) | DomEventData::PointerCancel(p)
            | DomEventData::PointerEnter(p) | DomEventData::PointerLeave(p) | DomEventData::PointerOver(p) | DomEventData::PointerOut(p)
            | DomEventData::MouseMove(p) | DomEventData::MouseDown(p) | DomEventData::MouseUp(p) | DomEventData::MouseEnter(p)
            | DomEventData::MouseLeave(p) | DomEventData::MouseOver(p) | DomEventData::MouseOut(p) | DomEventData::TouchStart(p)
            | DomEventData::TouchMove(p) | DomEventData::TouchEnd(p) | DomEventData::TouchCancel(p) | DomEventData::Click(p)
            | DomEventData::ContextMenu(p) | DomEventData::DoubleClick(p) => {
                ev.insert("clientX".into(), json!(p.coords.client_x));
                ev.insert("clientY".into(), json!(p.coords.client_y));
                ev.insert("pageX".into(), json!(p.coords.page_x));
                ev.insert("pageY".into(), json!(p.coords.page_y));
                ev.insert("offsetX".into(), json!(p.element.x));
                ev.insert("offsetY".into(), json!(p.element.y));
                ev.insert("button".into(), json!(p.button as u8));
                ev.insert("buttons".into(), json!(p.buttons.bits()));
                ev.insert("detail".into(), json!(if name == "click" { 1 } else { 0 }));
                ev.insert("modifiers".into(), json!(0));
                if name.starts_with("pointer") || name == "click" {
                    let pid = match p.id { BlitzPointerId::Finger(n) => n as i64 + 2, BlitzPointerId::Pen => 1, BlitzPointerId::Mouse => 1 };
                    ev.insert("pointerId".into(), json!(pid));
                    ev.insert("pointerType".into(), json!(if p.is_mouse() { "mouse" } else if p.is_pen() { "pen" } else { "touch" }));
                    ev.insert("isPrimary".into(), json!(p.is_primary));
                    ev.insert("pressure".into(), json!(p.details.pressure));
                }
            }
            DomEventData::Wheel(w) => {
                let (dx, dy, mode) = match w.delta {
                    blitz_traits::events::BlitzWheelDelta::Pixels(x, y) => (x, y, 0),
                    blitz_traits::events::BlitzWheelDelta::Lines(x, y) => (x, y, 1),
                };
                ev.insert("deltaX".into(), json!(dx));
                ev.insert("deltaY".into(), json!(dy));
                ev.insert("deltaMode".into(), json!(mode));
            }
            DomEventData::KeyDown(k) | DomEventData::KeyUp(k) | DomEventData::KeyPress(k) => {
                ev.insert("key".into(), json!(k.key.to_string()));
                ev.insert("code".into(), json!(k.code.to_string()));
                ev.insert("repeat".into(), json!(k.is_auto_repeating));
            }
            DomEventData::Input(i) => {
                ev.insert("inputType".into(), json!("insertText"));
                ev.insert("data".into(), json!(i.value));
            }
            _ => {}
        }
        // Form control state travels with every event (the sandbox mirror relies on it).
        {
            let base = doc.inner();
            if let Some(NodeData::Element(el)) = base.get_node(event.target).map(|n| &n.data) {
                if el.name.local == local_name!("input") {
                    if let Some(c) = el.checkbox_input_checked() {
                        ev.insert("checked".into(), json!(c));
                    }
                    if let Some(t) = el.text_input_data() {
                        ev.insert("value".into(), json!(t.editor.raw_text()));
                    } else if let Some(v) = el.attr(local_name!("value")) {
                        ev.insert("value".into(), json!(v));
                    }
                } else if el.name.local == local_name!("textarea") {
                    if let Some(t) = el.text_input_data() {
                        ev.insert("value".into(), json!(t.editor.raw_text()));
                    }
                }
            }
        }
        self.out.push(Value::Object(ev));
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use parley::fontique::GenericFamily;

    fn ops_for(text: &str) -> Value {
        json!({ "type": "ops", "ops": [
            { "op": "el", "id": 1, "tag": "body", "adopt": "body" },
            { "op": "el", "id": 2, "tag": "p", "attrs": [["style", "font-size:32px;margin:0"]] },
            { "op": "text", "id": 3, "data": text },
            { "op": "ins", "id": 3, "parent": 2 },
            { "op": "ins", "id": 2, "parent": 1 },
        ]})
    }

    /// Non-transparent pixels in the first 48×48 px of the document.
    fn painted(r: &mut Renderer) -> usize {
        let (w, _h) = r.viewport_px();
        let px = r.render();
        let mut n = 0;
        for y in 0..48 {
            for x in 0..48 {
                let i = ((y * w as usize) + x) * 4;
                if i + 3 < px.len() && px[i + 3] > 0 { n += 1; }
            }
        }
        n
    }

    fn render(fonts: FontContext, text: &str) -> usize {
        render_with(fonts, text, Backend::Vello)
    }

    fn render_with(fonts: FontContext, text: &str, backend: Backend) -> usize {
        let mut r = Renderer::new(200.0, 1.0, false, fonts);
        r.set_backend(backend);
        r.receive(&ops_for(text));
        painted(&mut r)
    }

    /// The CoreGraphics backend draws the same document through CoreText: a letter, a
    /// bold letter (variation axis on the system font) and a colour emoji all paint.
    #[cfg(target_vendor = "apple")]
    #[test]
    fn coregraphics_backend_paints_text_and_emoji() {
        let fonts = crate::fonts();
        let letter = render_with(fonts.clone(), "x", Backend::CoreGraphics);
        let emoji = render_with(fonts.clone(), "🪐", Backend::CoreGraphics);
        let mut r = Renderer::new(200.0, 1.0, false, fonts);
        r.set_backend(Backend::CoreGraphics);
        r.receive(&json!({ "type": "ops", "ops": [
            { "op": "el", "id": 1, "tag": "body", "adopt": "body" },
            { "op": "el", "id": 2, "tag": "p", "attrs": [["style", "font-size:32px;margin:0;font-weight:700"]] },
            { "op": "text", "id": 3, "data": "x" },
            { "op": "ins", "id": 3, "parent": 2 },
            { "op": "ins", "id": 2, "parent": 1 },
        ]}));
        let bold = painted(&mut r);
        println!("coregraphics: letter {letter} px, bold {bold} px, emoji {emoji} px");
        assert!(letter > 20, "a letter must paint ({letter} px)");
        assert!(bold > letter, "bold must cover more pixels than regular ({bold} vs {letter})");
        assert!(emoji > 100, "the emoji must paint ({emoji} px)");
    }

    #[test]
    fn emoji_is_painted_with_the_host_font() {
        let fonts = crate::fonts();
        let letter = render(fonts.clone(), "x");
        let emoji = render(fonts, "🪐");
        println!("host font: letter {letter} px, emoji {emoji} px");
        assert!(letter > 20, "a letter must paint");
        assert!(emoji > 100, "the emoji must paint ({emoji} px)");
    }

    /// Documents the iOS gap: the iOS emoji font's sbix glyphs are `emjc`, not PNG, so
    /// nothing is painted. Run with TW_IOS_EMOJI_FONT=<path to AppleColorEmoji-160px.ttc
    /// from a simulator runtime> to see it; it fails until glifo decodes `emjc`.
    #[test]
    #[ignore]
    fn emoji_is_painted_with_the_ios_font() {
        let path = std::env::var("TW_IOS_EMOJI_FONT").unwrap_or_default();
        if !std::path::Path::new(&path).exists() {
            eprintln!("TW_IOS_EMOJI_FONT not set; skipping");
            return;
        }
        let mut fcx = crate::fonts();
        fcx.collection.load_fonts_from_paths([&path]);
        let all: Vec<_> = fcx.collection.family_ids().collect();
        let ids: Vec<_> = all.into_iter().filter(|id| fcx.collection.family_name(*id).map(|n| n.contains("Emoji")).unwrap_or(false)).collect();
        let names: Vec<String> = ids.iter().filter_map(|id| fcx.collection.family_name(*id).map(str::to_string)).collect();
        println!("emoji families after loading the iOS font: {names:?}");
        fcx.collection.set_generic_families(GenericFamily::Emoji, ids.into_iter().take(1));
        let emoji = render(fcx.clone(), "🪐");
        #[cfg(target_vendor = "apple")]
        {
            let cg = render_with(fcx.clone(), "🪐", Backend::CoreGraphics);
            let mut r = Renderer::new(200.0, 1.0, false, fcx);
            r.set_backend(Backend::CoreGraphics);
            r.receive(&ops_for("🪐"));
            let (w, _) = r.viewport_px();
            let px = r.render();
            let total = px.chunks(4).filter(|p| p[3] > 0).count();
            let xs: Vec<usize> = px.chunks(4).enumerate().filter(|(_, p)| p[3] > 0).map(|(i, _)| i % w as usize).collect();
            println!("ios font: emoji {emoji} px on vello, {cg} px on CoreGraphics (whole frame {total} px, x range {:?}..{:?})", xs.iter().min(), xs.iter().max());
        }
        #[cfg(not(target_vendor = "apple"))]
        println!("ios font: emoji {emoji} px");
        assert!(emoji > 100, "the emoji must paint with the iOS font ({emoji} px)");
    }
}

#[cfg(test)]
mod font_probe {
    use skrifa::bitmap::{BitmapData, BitmapStrikes};
    use skrifa::instance::Size;
    use skrifa::string::StringId;
    use skrifa::{FontRef, MetadataProvider};

    fn probe(path: &str) {
        let data = std::fs::read(path).unwrap();
        for index in 0..4 {
            let Ok(font) = FontRef::from_index(&data, index) else { break };
            let ps: Vec<String> = font.localized_strings(StringId::POSTSCRIPT_NAME).map(|s| s.to_string()).collect();
            let fam: Vec<String> = font.localized_strings(StringId::FAMILY_NAME).map(|s| s.to_string()).collect();
            let strikes = BitmapStrikes::new(&font);
            let ppems: Vec<f32> = strikes.iter().map(|s| s.ppem()).collect();
            let gid = font.charmap().map('🪐');
            let glyph = gid.and_then(|g| strikes.glyph_for_size(Size::new(32.0), g));
            let kind = glyph.as_ref().map(|g| match &g.data {
                BitmapData::Png(d) => format!("png {} bytes, magic {:02x?}", d.len(), &d[..4.min(d.len())]),
                BitmapData::Bgra(d) => format!("bgra {} bytes", d.len()),
                BitmapData::Mask(_) => "mask".to_string(),
            });
            println!("{} #{index}: family {fam:?} ps {ps:?} format {:?} strikes {ppems:?} gid {gid:?} glyph {kind:?}", path.rsplit('/').next().unwrap(), strikes.format());
        }
    }

    #[test]
    #[ignore]
    fn probe_emoji_fonts() {
        probe("/System/Library/Fonts/Apple Color Emoji.ttc");
        if let Ok(p) = std::env::var("TW_IOS_EMOJI_FONT") { probe(&p); }
    }
}

#[cfg(test)]
mod sbix_probe {
    use skrifa::raw::TableProvider;
    use skrifa::{FontRef, MetadataProvider};

    /// Prints the sbix graphic type per strike: `png ` on macOS, `emjc` in the iOS font.
    #[test]
    #[ignore]
    fn probe_sbix_strikes() {
        let Ok(p) = std::env::var("TW_IOS_EMOJI_FONT") else { return };
        for path in ["/System/Library/Fonts/Apple Color Emoji.ttc", p.as_str()] {
            let data = std::fs::read(path).unwrap();
            let font = FontRef::from_index(&data, 0).unwrap();
            let gid = font.charmap().map('🪐').unwrap();
            let sbix = font.sbix().unwrap();
            for strike in sbix.strikes().iter() {
                let strike = strike.unwrap();
                let g = strike.glyph_data(gid);
                let desc = match g {
                    Ok(Some(g)) => format!("type {:?} {} bytes origin ({}, {})", std::str::from_utf8(&g.graphic_type().to_be_bytes()).unwrap_or("?"), g.data().len(), g.origin_offset_x(), g.origin_offset_y()),
                    Ok(None) => "none".to_string(),
                    Err(e) => format!("err {e}"),
                };
                println!("{} strike {} ppem: {desc}", path.rsplit('/').next().unwrap(), strike.ppem());
            }
        }
    }
}
