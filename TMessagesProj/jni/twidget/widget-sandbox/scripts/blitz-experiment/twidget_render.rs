//! twidget experiment: render a static HTML file headlessly at a given CSS
//! width, scale 1, and write a PNG. Prints stage timings as JSON on stdout.
//! Usage: twidget_render <in.html> <width> <out.png> [min_height]

use anyrender::{PaintScene as _, render_to_buffer};
use anyrender_vello_cpu::VelloCpuImageRenderer;
use blitz_dom::{DocumentConfig, FontContext, util::Color};
use blitz_html::HtmlDocument;
use blitz_net::Provider;
use blitz_paint::paint_scene;
use blitz_traits::shell::{ColorScheme, Viewport};
use peniko::Fill;
use peniko::kurbo::Rect;
use std::fs::File;
use std::sync::Arc;
use std::time::Instant;

#[tokio::main]
async fn main() {
    let args: Vec<String> = std::env::args().collect();
    let in_path = &args[1];
    let width: u32 = args[2].parse().unwrap();
    let out_path = &args[3];
    let min_height: f64 = args.get(4).and_then(|a| a.parse().ok()).unwrap_or(24.0);

    let html = std::fs::read_to_string(in_path).unwrap();
    let scale = 1.0f64;
    let net = Arc::new(Provider::new(None));
    // TW_SHARED_FONTS=1: build the font context (system font scan) once and
    // hand a clone to every document, as a long-lived host would.
    // TW_NO_UA=1: no user-agent stylesheet (timing diagnostic only).
    let shared_fonts = std::env::var("TW_SHARED_FONTS").is_ok();
    let no_ua = std::env::var("TW_NO_UA").is_ok();
    let font_ctx = if shared_fonts { Some(FontContext::new()) } else { None };
    let ua: Option<Vec<String>> = if no_ua { Some(vec![]) } else { None };

    let tw = Instant::now();
    {
        let mut warm = HtmlDocument::from_html(
            &html,
            DocumentConfig {
                base_url: Some(format!("file://{in_path}")),
                net_provider: Some(Arc::clone(&net) as _),
                font_ctx: font_ctx.clone(),
                ua_stylesheets: ua.clone(),
                viewport: Some(Viewport::new(width, 900, scale as f32, ColorScheme::Light)),
                ..Default::default()
            },
        );
        loop {
            warm.resolve(0.0);
            if net.is_empty() {
                break;
            }
        }
        warm.as_mut().resolve(0.0);
    }
    let t_warm = tw.elapsed().as_secs_f64() * 1000.0;

    let mut vp_height: u32 = 24;
    let mut t_parse = 0.0;
    let mut t_layout = 0.0;
    let mut document;
    let mut pass = 0;
    let mut pass_ms: Vec<String> = Vec::new();
    loop {
        let t0 = Instant::now();
        document = HtmlDocument::from_html(
            &html,
            DocumentConfig {
                base_url: Some(format!("file://{in_path}")),
                net_provider: Some(Arc::clone(&net) as _),
                font_ctx: font_ctx.clone(),
                ua_stylesheets: ua.clone(),
                viewport: Some(Viewport::new(width, vp_height, scale as f32, ColorScheme::Light)),
                ..Default::default()
            },
        );
        t_parse = t0.elapsed().as_secs_f64() * 1000.0;
        let t1 = Instant::now();
        loop {
            document.resolve(0.0);
            if net.is_empty() {
                break;
            }
        }
        document.as_mut().resolve(0.0);
        t_layout = t1.elapsed().as_secs_f64() * 1000.0;
        let h = document.as_ref().root_element().final_layout().size.height as f64;
        let h = h.max(min_height).min(900.0).ceil() as u32;
        pass += 1;
        pass_ms.push(format!("[{t_parse:.1},{t_layout:.1},{h}]"));
        if h == vp_height || pass >= 6 {
            break;
        }
        vp_height = h;
    }

    let computed_height = document.as_ref().root_element().final_layout().size.height as f64;
    let render_width = width;
    let render_height = computed_height.max(min_height).min(900.0).ceil() as u32;

    let t2 = Instant::now();
    let buffer = render_to_buffer::<VelloCpuImageRenderer, _>(
        |scene| {
            scene.fill(
                Fill::NonZero,
                Default::default(),
                Color::WHITE,
                Default::default(),
                &Rect::new(0.0, 0.0, render_width as f64, render_height as f64),
            );
            paint_scene(scene, document.as_mut(), scale, render_width, render_height, 0, 0);
        },
        render_width,
        render_height,
    );
    let t_paint = t2.elapsed().as_secs_f64() * 1000.0;

    let file = File::create(out_path).unwrap();
    let mut encoder = png::Encoder::new(file, render_width, render_height);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    let mut writer = encoder.write_header().unwrap();
    writer.write_image_data(&buffer).unwrap();
    writer.finish().unwrap();

    println!(
        "{{\"warm_ms\":{t_warm:.1},\"parse_ms\":{t_parse:.1},\"layout_ms\":{t_layout:.1},\"paint_ms\":{t_paint:.1},\"passes\":{pass},\"pass_ms\":[{}],\"width\":{render_width},\"height\":{render_height},\"doc_height\":{computed_height:.0}}}",
        pass_ms.join(",")
    );
}
