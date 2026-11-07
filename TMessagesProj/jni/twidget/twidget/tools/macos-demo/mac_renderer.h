// WidgetHost's Renderer on TwidgetCore, painted by CGBackend: the macOS twin of the Qt demo's
// QtRenderer and the Windows spike's SurfaceRenderer. The document is painted into an image per
// frame; the compositor's layers (transform and opacity animations) are handed to the view as
// images with their tracks, for Core Animation to play.
//
// Threading: the core is not locked on its side, so every call into it goes through one mutex here.
// The host thread (messages), the clock thread (frames and paints) and the main thread (input) take
// turns; messages to the host and frames to the view go out after the lock is released.
#pragma once

#include "coregraphics.h"
#include "renderer.h"
#include "twidget/renderer.h"

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <functional>
#include <map>
#include <memory>
#include <mutex>
#include <optional>
#include <thread>

/// A retained CGImage.
class Image {
public:
    Image() = default;
    explicit Image(CGImageRef adopted) : image_(adopted) {}
    Image(const Image& other) : image_(other.image_ ? CGImageRetain(other.image_) : nullptr) {}
    Image& operator=(Image other) {
        std::swap(image_, other.image_);
        return *this;
    }
    ~Image() {
        if (image_) CGImageRelease(image_);
    }
    CGImageRef get() const { return image_; }
    explicit operator bool() const { return image_ != nullptr; }

private:
    CGImageRef image_ = nullptr;
};

/// One compositor layer as the view needs it.
struct LayerFrame {
    int64_t id = 0;
    twlite::Rect bounds;  // document coordinates, CSS px
    float scale = 1;      // device px per CSS px of `content`
    Image content;        // null: the content is unchanged
    bool has_clip = false;
    twlite::Rect clip;
    twlite::Radii clip_radii;
    bool track_changed = false;
    twlite::LayerTrack track;
    int64_t parent = 0;  // the layer this one moves with (twlite::Layer::parent)
};

struct Frame {
    Image document;  // the document without its layers, device px
    double width = 0, height = 0, dpr = 1;
    /// The layers in paint order, bottom first, when they changed; otherwise the view keeps its own.
    std::optional<std::vector<LayerFrame>> layers;
};

/// What Core Animation's transform for a layer is at `key`: from the layer's own space (its bounds'
/// size, y up, the content image upright) to the document's (y down), where the stage flips it back.
CGAffineTransform layer_transform(const twlite::LayerKey& key, const twlite::Rect& bounds);
/// A clip's outline in document coordinates, each corner with its own elliptical radii; the caller releases it.
CGPathRef clip_path(const twlite::Rect& clip, const twlite::Radii& radii);

class MacRenderer final : public twidget::Renderer {
public:
    /// `onFrame` runs on the clock thread with each painted frame.
    MacRenderer(const std::string& theme, double width, double dpr, std::function<void(std::shared_ptr<Frame>)> onFrame);
    ~MacRenderer() override;

    // Renderer (host thread)
    void load() override;
    void send(const std::string& json) override;
    void setTheme(const std::string& theme) override;
    void setWidth(double width) override;
    void destroy() override;

    // Input in CSS px, any thread. touch action: 0 down, 1 move, 2 up, 3 cancel; hover action: 0 move, 1 leave.
    void touch(int action, double x, double y, int pointer, bool primary, bool mouse, int modifiers);
    void hover(int action, double x, double y, int modifiers);
    void wheel(double x, double y, double dx, double dy, int mode, int modifiers);
    void key(const std::string& type, const std::string& key, const std::string& code, bool repeat, int modifiers);
    // Off screen nothing animates: the clock stops ticking until the widget is back.
    void setVisible(bool visible);
    // The view's text field changed the text of editor `generation`, or let go of the keyboard.
    void edit(uint64_t generation, const std::string& text);
    void endEditing();

    /// The text control to cover with a text field (twlite::Renderer::editor()), or none; called on
    /// any thread whenever it changes.
    std::function<void(std::optional<twlite::Renderer::EditorSpec>)> onEditor;

    int paints() const { return paints_; }
    double lastPaintMs() const { return lastPaintMs_; }
    double lastBuildMs() const { return lastBuildMs_; }
    int layers() const { return layerCount_; }

private:
    void dispatch(std::vector<twidget::Json> messages);
    // Runs `call` on the core under the lock, then sends what it returned, repaints and keeps ticking as it asks.
    template <class Call> void run(Call call);
    void setWake(double seconds);
    void setTicking(bool ticking);
    void schedulePaint();
    void requestFrame();
    void clockLoop();
    void paint();
    // Under mutex_: the image of `list` (CSS px) over `area`, `scale` device px per CSS px.
    Image raster(const twlite::DisplayList& list, const twlite::Rect& area, double scale);
    static Image image_of(const std::vector<uint8_t>& pixels, size_t w, size_t h);
    static double nowMs();

    double dpr_;
    std::function<void(std::shared_ptr<Frame>)> onFrame_;
    twlite::CGBackend backend_;
    std::unique_ptr<twlite::Renderer> core_;
    std::mutex mutex_;  // every call into core_ and backend_
    uint64_t compositionVersion_ = 0;
    uint64_t editorGeneration_ = 0;  // of the spec last handed to onEditor, 0 for none
    std::vector<uint8_t> pixels_;  // the last document frame, which the next one starts from
    size_t pixelsWidth_ = 0;
    std::map<int64_t, std::pair<uint64_t, uint64_t>> shown_;  // layer id → content and track versions handed out

    // One tick per display refresh at most.
    std::mutex clockMutex_;
    std::condition_variable clockCv_;
    std::thread clock_;
    bool frameRequested_ = false;
    bool ticking_ = false;  // the core animates or holds coalesced moves
    // Not ticking, the core still needs a tick here: an animation event is due, or a layer's track ends.
    std::optional<std::chrono::steady_clock::time_point> wakeAt_;
    bool visible_ = true;
    bool paintScheduled_ = false;
    bool stopClock_ = false;
    bool frozen_ = false;
    bool destroyed_ = false;

    std::atomic<int> paints_{0};
    std::atomic<double> lastPaintMs_{0};
    std::atomic<double> lastBuildMs_{0};
    std::atomic<int> layerCount_{0};
};
