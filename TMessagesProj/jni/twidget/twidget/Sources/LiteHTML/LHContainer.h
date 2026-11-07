#pragma once

#include <litehtml.h>
#include "lhx_sanitizer.h"
#include "lhx_painter.h"
#include "include/LHRenderList.h"
#include <CoreGraphics/CoreGraphics.h>
#include <CoreText/CoreText.h>

#include <array>
#include <functional>
#include <map>
#include <mutex>
#include <unordered_set>
#include <string>
#include <unordered_map>
#include <vector>
#include <atomic>

CGImageRef LHCreateSVGImage(const std::string& markup, CGSize* size);
bool       LHRenderingSVGImage(void);

#ifdef __OBJC__
@class NSImage;
void LHRegisterAppImage(NSString* name, NSImage* image);
void LHSetImageProvider(NSImage* (^provider)(NSString* url));
NSImage* (^LHImageProvider(void))(NSString* url);
void LHForgetImage(NSString* url);
CGImageRef LHCopyImageForURL(NSString* url) CF_RETURNS_RETAINED;
CGSize LHImageSizeForURL(NSString* url);
void LHSetPortableSymbols(bool portable);
bool LHPortableSymbols(void);
@class NSFont;
NSFont* LHResolveFont(NSString* families, CGFloat size, int weight, bool italic);
#endif

struct LHTextRun
{
    std::string text;
    CTFontRef   font;
    CGRect      frame;
};

struct LHShapeData
{
    CGPathRef                                      path = nullptr;
    bool                                           fill = false;
    CGFloat                                        fillColor[4] = {0, 0, 0, 0};
    int                                            gradient = 0;
    CGPoint                                        start = CGPointZero, end = CGPointZero;
    std::vector<std::pair<CGFloat, std::array<CGFloat, 4>>> stops;
    bool                                           evenOdd = false;
    bool                                           stroke = false;
    CGFloat                                        strokeColor[4] = {0, 0, 0, 0};
    CGFloat                                        lineWidth = 1;
    int                                            cap = 0, join = 0;
    CGFloat                                        miter = 4;
};

struct LHDrawOp
{
    CGRect   bounds;
    uint64_t hash;
    uint64_t shape;
    bool     backdrop = false;
    uint64_t stretch  = 0;
    CGFloat  edge     = 0;
};

using LHGPUOp       = lhx::RenderOp;
using LHGlyphSlot   = lhx::AtlasSlot;
using LHGPURecorder = lhx::RenderRecorder;

class LHContainer : public litehtml::document_container
{
  public:
    static size_t cached_font_count();
    static size_t text_measure_count();
    static size_t raster_cache_bytes();
    static void   purge_caches();
    LHContainer();
    ~LHContainer() override;

    void set_viewport(const litehtml::position& vp) { m_viewport = vp; }
    void set_default_font_size(float px) { m_default_font_size = px; }
    void set_text_runs(std::vector<LHTextRun>* runs) { m_runs = runs; }
    void set_draw_ops(std::vector<LHDrawOp>* ops) { m_ops = ops; }
    void set_shape_capture(std::vector<LHShapeData>* shapes) { m_shapes = shapes; m_shapes_bad = false; }
    bool shape_capture_failed() const { return m_shapes_bad; }
    void capture_shape(const litehtml::vector_path& path);
    void set_clip_culling(bool enabled) { m_clip_culling = enabled; }
    static std::atomic<bool> s_text_batching;
    static std::atomic<bool> s_exact_group_bounds;
    static std::atomic<long> s_shadow_line_batches;
    static std::atomic<long> s_source_filter_hits;
    static std::atomic<bool> s_source_filter_cache;
    struct SourceFilter
    {
        CGImageRef image = nullptr;
        CGRect     user;
        CGFloat    scale = 1;
        bool       flipped = false;
    };
    int                                            m_backdrop_source_depth = 0;
    bool                                           m_source_filter_scope   = false;
    std::unordered_map<std::string, SourceFilter>  m_source_filters;
    bool begin_source_filters();
    void end_source_filters();
    CGContextRef m_shadow_base_context = nullptr;
    CGAffineTransform m_shadow_base_inverse = CGAffineTransformIdentity;
    CGAffineTransform shadow_transform(CGContextRef ctx) const;
    void begin_text_batching(CGContextRef ctx);
    void end_text_batching();
    void flush_text();
    void set_painter(lhx::Painter* painter) { m_painter = painter; }
    void set_gpu_recorder(LHGPURecorder* recorder)
    {
        flush_capture();
        m_gpu = recorder;
    }
    bool clip_culling() const { return m_clip_culling; }
    bool rasterizes() const { return !m_ops && !m_runs && !m_painter; }

    std::function<void(const std::string& url)> on_anchor;
    std::function<void(const std::string& cursor)> on_cursor;
    std::function<void(const std::string& action, const std::string& value)> on_action;

    litehtml::uint_ptr create_font(const litehtml::font_description& descr, const litehtml::document* doc,
                                   litehtml::font_metrics* fm) override;
    void               delete_font(litehtml::uint_ptr hFont) override;
    static CTFontRef    native_font(litehtml::uint_ptr hFont);
    litehtml::pixel_t  text_width(const char* text, litehtml::uint_ptr hFont) override;
    std::shared_ptr<litehtml::text_breaker> create_text_breaker(const std::string& text, litehtml::uint_ptr font) override;
    void draw_text(litehtml::uint_ptr hdc, const char* text, litehtml::uint_ptr hFont, litehtml::web_color color,
                   const litehtml::position& pos) override;
    litehtml::pixel_t pt_to_px(float pt) const override;
    litehtml::pixel_t get_default_font_size() const override;
    const char*       get_default_font_name() const override;
    void              draw_list_marker(litehtml::uint_ptr hdc, const litehtml::list_marker& marker) override;
    void              load_image(const char* src, const char* baseurl, bool redraw_on_ready) override;
    void              get_image_size(const char* src, const char* baseurl, litehtml::size& sz) override;
    std::string       get_image_aspect(const char* src) override;
    void draw_image(litehtml::uint_ptr hdc, const litehtml::background_layer& layer, const std::string& url,
                    const std::string& base_url) override;
    void draw_solid_fill(litehtml::uint_ptr hdc, const litehtml::background_layer& layer,
                         const litehtml::web_color& color) override;
    void draw_linear_gradient(litehtml::uint_ptr hdc, const litehtml::background_layer& layer,
                              const litehtml::background_layer::linear_gradient& gradient) override;
    void draw_radial_gradient(litehtml::uint_ptr hdc, const litehtml::background_layer& layer,
                              const litehtml::background_layer::radial_gradient& gradient) override;
    void draw_conic_gradient(litehtml::uint_ptr hdc, const litehtml::background_layer& layer,
                             const litehtml::background_layer::conic_gradient& gradient) override;
    void draw_borders(litehtml::uint_ptr hdc, const litehtml::borders& borders, const litehtml::position& draw_pos,
                      bool root) override;
    void draw_box_shadow(litehtml::uint_ptr hdc, const litehtml::position& box, const litehtml::border_radiuses& radius,
                         const litehtml::shadow_paint& shadow) override;
    void begin_group(litehtml::uint_ptr hdc, const litehtml::paint_group& group) override;
    void draw_path(litehtml::uint_ptr hdc, const litehtml::vector_path& path) override;
    void suspend_clips() override;
    void resume_clips() override;
    void end_group(litehtml::uint_ptr hdc) override;
    void draw_filtered(litehtml::uint_ptr hdc, const litehtml::filter_paint& filter, const std::function<void(litehtml::uint_ptr)>& paint) override;
    void draw_backdrop(litehtml::uint_ptr hdc, const litehtml::filter_paint& filter) override;
    std::function<CGImageRef(const void* owner, CGRect rect, CGFloat scaleX, CGFloat scaleY, bool flipped)> backdrop_source;
    bool draw_cached(litehtml::uint_ptr hdc, const litehtml::position& box, uint64_t key, const std::function<void(litehtml::uint_ptr)>& paint) override;
    void draw_isolated(litehtml::uint_ptr hdc, const litehtml::position& box, const std::function<void(litehtml::uint_ptr)>& paint) override;
    bool sanitize_element(std::string& tag, bool svg, litehtml::string_map& attrs) override;
    bool sanitize_declaration(const std::string& name, const litehtml::css_token_vector& value) override;
    bool sanitize_at_rule(const std::string& name) override;
    void sanitizer_limit(const char* what) override;
    void trusted_styles(bool trusted) override { m_trusted_styles = trusted; }
    bool controls_active() const override { return m_controls_active; }
    litehtml::web_color control_accent() const override { return m_control_accent; }
    void set_controls_active(bool active, litehtml::web_color accent)
    {
        m_controls_active = active;
        m_control_accent  = accent;
    }

    using SanitizerReport = std::map<std::string, std::pair<int, std::vector<std::string>>>;
    const SanitizerReport& sanitizer_report() const { return m_sanitizer_report; }
    void                   clear_sanitizer_report() { m_sanitizer_report.clear(); }

    void set_caption(const char* caption) override {}
    void set_base_url(const char* base_url) override {}
    void link(const std::shared_ptr<litehtml::document>& doc, const litehtml::element::ptr& el) override {}
    void on_anchor_click(const char* url, const litehtml::element::ptr& el) override;
    bool on_element_click(const litehtml::element::ptr& el) override;
    void on_mouse_event(const litehtml::element::ptr& el, litehtml::mouse_event event) override {}
    void set_cursor(const char* cursor) override;
    void transform_text(std::string& text, litehtml::text_transform tt) override;
    void import_css(std::string& text, const std::string& url, std::string& baseurl) override {}
    void set_clip_path(const litehtml::vector_path& path) override;
    bool point_in_clip_path(const litehtml::vector_path& path, litehtml::pixel_t x, litehtml::pixel_t y) override;
    void set_clip(const litehtml::position& pos, const litehtml::border_radiuses& bdr_radius) override;
    void del_clip() override;
    void get_viewport(litehtml::position& viewport) const override;
    litehtml::element::ptr create_element(const char* tag_name, const litehtml::string_map& attributes,
                                          const std::shared_ptr<litehtml::document>& doc) override
    {
        return nullptr;
    }
    void get_media_features(litehtml::media_features& media) const override;
    void get_language(std::string& language, std::string& culture) const override;

  private:
    struct Font;
    struct Clip
    {
        litehtml::position        box;
        litehtml::border_radiuses radius;
        CGAffineTransform         matrix;
        std::shared_ptr<litehtml::vector_path> path;
    };

    struct Group
    {
        CGAffineTransform matrix;
        float             opacity;
        bool              hidden;
        bool              raster;
        bool              layer;
    };

    struct OpHash;
    void sanitizer_report(const char* kind, const std::string& detail);
    SanitizerReport m_sanitizer_report;
    bool            m_trusted_styles = false;
    bool            m_controls_active = false;
    litehtml::web_color m_control_accent = litehtml::web_color(0, 122, 255);
    lhx::Sanitizer  m_sanitizer{nullptr};
    lhx::Painter*   m_painter = nullptr;
    void apply_clips(CGContextRef ctx, CGRect bounds) const;
    void record_op(OpHash& hash, CGRect ink, bool capture = true);
    void paint_box_shadow(CGContextRef ctx, CGRect r, const litehtml::border_radiuses& radius, const litehtml::shadow_paint& shadow);
    void flush_capture();
    bool record_solid(const litehtml::background_layer& layer, const litehtml::web_color& color);
    bool record_rounded(CGRect rect, const litehtml::border_radiuses& radius, const litehtml::web_color& color);
    void push_mask(const LHGlyphSlot& slot, CGRect device, const uint8_t* rgba);
    bool record_glyphs(const char* text, Font* f, const litehtml::web_color& color, const litehtml::position& pos, CGRect ink);
    const uint8_t* device_color(const litehtml::web_color& color);
    void clip_background_layer(CGContextRef ctx, const litehtml::background_layer& layer) const;
    void for_each_tile(const litehtml::background_layer& layer, CGSize tile,
                       const std::function<void(CGRect tile)>& fn) const;

    std::vector<LHShapeData>* m_shapes = nullptr;
    struct TextBatch
    {
        CGContextRef         ctx = nullptr;
        CTFontRef            font = nullptr;
        litehtml::web_color  color;
        int                  smoothing = 0;
        std::vector<CGGlyph> glyphs;
        std::vector<CGPoint> points;
        std::vector<CGRect>  inks;
    };
    std::vector<TextBatch>   m_text_batches;
    struct ShadowRun
    {
        std::string text;
        CGFloat     x = 0, baseline = 0;
    };
    struct ShadowBatch
    {
        CGContextRef           ctx  = nullptr;
        const void*            font = nullptr;
        litehtml::web_color    color;
        CGFloat                baseline = 0;
        CGRect                 box      = CGRectNull;
        std::vector<ShadowRun> runs;
    };
    ShadowBatch              m_shadow_batch;
    void                     flush_shadow_batch();
    CGContextRef             m_batch_ctx = nullptr;
    bool                     clips_needed(CGRect bounds) const;
    bool                     m_shapes_bad = false;
    std::vector<Clip>        m_clips;
    std::vector<std::vector<Clip>> m_suspended_clips;
    std::vector<Group>       m_groups;
    struct Effect
    {
        CGFloat  reach;
        uint64_t hash;
    };
    std::vector<Effect>      m_effects;
    int                      m_hidden_depth = 0;
    std::mutex                       m_backdrop_lock;
    std::unordered_set<const void*>  m_backdrop_owners;
    bool                             backdrop_allowed(const void* owner);

    CGAffineTransform group_matrix() const { return m_groups.empty() ? CGAffineTransformIdentity : m_groups.back().matrix; }
    std::vector<LHTextRun>*  m_runs = nullptr;
    std::vector<LHDrawOp>*   m_ops  = nullptr;
    LHGPURecorder*           m_gpu  = nullptr;
    CGContextRef             m_capture = nullptr;
    uint64_t                 m_capture_key = 0;
    CGRect                   m_capture_device = CGRectNull;
    std::unordered_map<uint32_t, std::array<uint8_t, 4>> m_device_colors;
    CGColorSpaceRef          m_device_colors_space = nullptr;
    bool                     m_clip_culling = true;
    litehtml::position m_viewport;
    float              m_default_font_size = 16.0f;
};
