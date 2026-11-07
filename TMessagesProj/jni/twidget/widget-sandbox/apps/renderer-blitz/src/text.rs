//! `measureText` for the sandbox's canvas shim: a parley layout of the string
//! in a font parsed from the CSS `font` shorthand (size, weight, style, family).

use blitz_dom::FontContext;
use parley::style::{FontFamily, FontStyle, FontWeight, StyleProperty};
use parley::{Alignment, AlignmentOptions, LayoutContext};
use serde_json::{json, Value};
use std::borrow::Cow;

struct Font {
    size: f32,
    weight: f32,
    italic: bool,
    family: String,
}

fn parse_font(font: &str) -> Font {
    let mut out = Font { size: 10.0, weight: 400.0, italic: false, family: "sans-serif".into() };
    let mut rest = font.trim();
    let mut tokens: Vec<&str> = Vec::new();
    // Everything up to the size token is style/variant/weight; the family is whatever follows the size.
    for (i, tok) in rest.split_whitespace().enumerate() {
        let lower = tok.to_ascii_lowercase();
        let size_tok = lower.split('/').next().unwrap_or("");
        if let Some(px) = size_tok.strip_suffix("px").and_then(|n| n.parse::<f32>().ok()) {
            out.size = px;
            let pos = rest.match_indices(tok).nth(0).map(|(p, _)| p + tok.len()).unwrap_or(rest.len());
            rest = rest[pos..].trim();
            let _ = i;
            break;
        }
        tokens.push(tok);
    }
    for tok in tokens {
        match tok.to_ascii_lowercase().as_str() {
            "italic" | "oblique" => out.italic = true,
            "bold" => out.weight = 700.0,
            "bolder" => out.weight = 700.0,
            "lighter" => out.weight = 300.0,
            w => {
                if let Ok(n) = w.parse::<f32>() {
                    out.weight = n;
                }
            }
        }
    }
    if !rest.is_empty() {
        out.family = rest.replace('"', "").replace('\'', "");
    }
    out
}

pub fn measure(fcx: &mut FontContext, font: &str, text: &str) -> Value {
    let f = parse_font(font);
    let mut lcx: LayoutContext<[u8; 4]> = LayoutContext::new();
    let mut builder = lcx.ranged_builder(fcx, text, 1.0, false);
    builder.push_default(StyleProperty::FontSize(f.size));
    builder.push_default(StyleProperty::FontWeight(FontWeight::new(f.weight)));
    builder.push_default(StyleProperty::FontStyle(if f.italic { FontStyle::Italic } else { FontStyle::Normal }));
    builder.push_default(StyleProperty::FontFamily(FontFamily::Source(Cow::Owned(f.family))));
    let mut layout = builder.build(text);
    layout.break_all_lines(None);
    layout.align(Alignment::Start, AlignmentOptions::default());
    let width = layout.full_width();
    let (ascent, descent) = layout
        .lines()
        .next()
        .map(|l| {
            let m = l.metrics();
            (m.baseline - m.offset, m.line_height - (m.baseline - m.offset))
        })
        .unwrap_or((f.size * 0.8, f.size * 0.2));
    json!({
        "width": width,
        "actualBoundingBoxLeft": 0.0, "actualBoundingBoxRight": width,
        "actualBoundingBoxAscent": ascent, "actualBoundingBoxDescent": descent,
        "fontBoundingBoxAscent": ascent, "fontBoundingBoxDescent": descent,
    })
}
