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

/// A digest of everything `list` paints: equal digests paint equal pixels.
uint64_t paint_digest(const DisplayList& list);

/// Bakes `sample` (the layer's key at 0..1 of the track) into linear keys: halves each span until
/// the box's corners stray at most 0.05 px and the opacity 0.002 from the straight line. False when
/// a sample fails or the track needs more keys than a compositor should get.
bool bake_track(const std::function<bool(double t, LayerKey& key)>& sample, const Rect& box, std::vector<LayerKey>& keys);

} // namespace twlite
