// What a platform compositor runs on its own: elements whose animations touch only transform and
// opacity, lifted out of the document's paint into layers, each with a track of keyframes the core
// has already resolved. Easing, steps(), direction, iteration and Web Animations timing are all
// baked into linear keys, so any compositor with linear keyframe animation can play them (Core
// Animation, Windows.UI.Composition, Android's RenderNode animators). A platform without one never
// enables compositing and paints every frame as before. Ported in substance from twidget's
// WidgetCompositor.swift.
#pragma once

#include "display_list.h"

#include <cstdint>
#include <functional>
#include <vector>

namespace twlite {

/// One key of a track: the layer's full transform and opacity at `t`.
struct LayerKey {
    /// 0..1 across the track's duration, ascending; values between keys are linear in every
    /// matrix component and in opacity.
    float t = 0;
    /// Maps the layer's content, which is in document coordinates, to where it shows, in document
    /// coordinates: the CSS transform already anchored at the transform origin.
    Affine matrix;
    float opacity = 1;
};

struct LayerTrack {
    /// When the first key applies, on the renderer clock (`Renderer::clock()`), in seconds. Usually
    /// a moment ago: a platform starts the animation that far in.
    double start = 0;
    double duration = 0;
    /// Loops forever; otherwise the last key holds until the core changes the composition.
    bool repeat = false;
    std::vector<LayerKey> keys;
};

struct Layer {
    /// The widget node id of the element.
    int64_t id = 0;
    /// The area `content` covers, in document coordinates. Draw the content with this rectangle's
    /// top left at the layer's origin.
    Rect bounds;
    /// Changes whenever `content` paints differently; the same value means the same pixels.
    uint64_t content_version = 0;
    /// The element and its descendants without the element's own transform and opacity, in
    /// document coordinates.
    DisplayList content;
    /// Rasterise the content this much finer than the document (the largest scale the track
    /// reaches, at most 3), so a scaled-up layer stays sharp.
    float raster_scale = 1;
    /// A clipping ancestor (overflow), in document coordinates. It clips the transformed layer and
    /// does not move with it.
    bool has_clip = false;
    Rect clip;
    Radii clip_radii;
    /// Changes whenever `track` changes; the same value means keep the running animation.
    uint64_t track_version = 0;
    LayerTrack track;
    /// The layer this one moves with (an animated ancestor's), or 0 for one over the document. A
    /// child comes after its parent, with nothing but the parent's other descendants in between;
    /// its keys and its clip are in the parent's content space, so it shows at the parent's matrix
    /// times its own, inside the parent's clip and opacity.
    int64_t parent = 0;
};

/// Layers in paint order, bottom first, all above the document's own paint.
struct Composition {
    std::vector<Layer> layers;
    /// Changes whenever anything above changes.
    uint64_t version = 0;
};

/// The keys of `track` as they continue `elapsed` seconds after its start, for a compositor that
/// cannot start an animation part way through: a loop turned to its current phase, a one-shot cut
/// to what is left. `duration` gets the span the returned keys cover; one key means hold it.
std::vector<LayerKey> resume_track(const LayerTrack& track, double elapsed, double& duration);

/// JSON for compositors outside C++, without the layers' content (to_json of `Layer::content`
/// gives that): `{"v": version, "l": [{"id", "b" bounds, "cv" content_version, "rs" raster_scale,
/// "l" clip and "q" its radii when clipped, "tv" track_version, "ts" start, "td" duration, "tr" 1
/// when it repeats, "k" keys flat as t, a, b, c, d, e, f, opacity, "p" parent when nested}]}`.
std::string to_json(const Composition& composition);

/// A digest of everything `list` paints: equal digests paint equal pixels.
uint64_t paint_digest(const DisplayList& list);

/// A paint as damage tracking needs to keep it: for each drawing command, a key for what it draws
/// (with the clips and groups around it) and where its pixels can land, in document coordinates.
struct PaintSummary {
    struct Entry {
        uint64_t key = 0;
        Rect ink;
        int canvas = 0;
        bool backdrop = false;  // paints from what is under it: damage there repaints all of it
    };
    std::vector<Entry> entries;
};
PaintSummary summarize(const DisplayList& list);

/// Drops the rectangular clips whose content stays inside them (inset by their corner radii): they
/// change no pixel, and a rounded clip costs a painter a layer. Clip paths and clips under a rotation
/// or skew stay. Returns how many clips went.
size_t drop_inert_clips(DisplayList& list);

/// The areas where `after` paints differently from `before`: commands one has and the other not,
/// commands whose paint order changed, and canvases `canvas_changed` says drew since. Merged into
/// one area when there are many.
bool damage_between(const PaintSummary& before, const PaintSummary& after, const std::function<bool(int canvas)>& canvas_changed, std::vector<Rect>& out);

/// Bakes `sample` (the layer's key at 0..1 of the track) into linear keys: halves each span until
/// the box's corners stray at most 0.05 px and the opacity 0.002 from the straight line. False when
/// a sample fails or the track needs more keys than a compositor should get.
bool bake_track(const std::function<bool(double t, LayerKey& key)>& sample, const Rect& box, std::vector<LayerKey>& keys);

} // namespace twlite
