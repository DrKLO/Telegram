// Qt fonts, QImage decoding and a QPainter painter for the DisplayList: the desktop twin of D2DBackend
// (platform/windows) for Qt hosts such as Telegram Desktop. Everything draws into QImages, so it runs on
// any thread; one per renderer, every call under that renderer's lock.
#pragma once

#include "backend.h"
#include "display_list.h"

#include <QFont>
#include <QImage>
#include <QPainterPath>

#include <map>
#include <memory>
#include <span>
#include <tuple>
#include <unordered_map>

class QPainter;

namespace twlite {

class QtBackend : public Backend {
public:
    QtBackend();

    int create_font(const std::string& families, float size_px, int weight, bool italic, const std::string& features, FontMetrics& out) override;
    void delete_font(int font) override;
    float text_width(int font, const std::string& text) override;
    bool load_image(int id, const std::string& url, float& w, float& h) override;
    void canvas_run(int id, int w, int h, const std::vector<CanvasOp>& ops) override;
    bool canvas_read(int id, int x, int y, int w, int h, std::vector<uint8_t>& rgba) override;
    void canvas_release(int id) override;

    /// Paints `list` (CSS px) into `target`, an ARGB32 premultiplied image, under `transform` (CSS px →
    /// image px; usually a scale by the device pixel ratio). With `damage` (Renderer::damage, CSS px),
    /// only those areas are cleared and painted again; the rest of `target` is kept.
    void paint(const DisplayList& list, QImage& target, const QTransform& transform, const std::vector<Rect>* damage = nullptr);

    /// An image with this backend's resolution: fonts are sized for 96 dpi, where a point is 4/3 px.
    static QImage make_image(int w, int h);

    /// Drops the rasters of keyed groups, made again on demand.
    void trim();
    size_t cached_rasters() const { return rasters_.size(); }

private:
    struct Face {
        QFont font;
        FontMetrics metrics;
        std::unordered_map<std::string, float> widths;
    };
    using FaceKey = std::tuple<std::string, float, int, bool, std::string>;

    Face* face(int font);
    /// One pass of paint(): everything, or only `area_only` (device px), cleared first.
    void paint_area(std::span<const Command> list, QImage& target, const QTransform& transform, const QRect* area_only);
    /// Draws a keyed group (`inner`, between its GroupPush and GroupPop) from its raster, made first
    /// when missing. False when it cannot be rastered: `device` (its user space → target px) turns it.
    bool draw_raster(QPainter& p, QPoint origin, const QTransform& device, const Command& push, std::span<const Command> inner);
    QStringList resolve_families(const std::string& families);
    void canvas_op(QImage& target, const CanvasOp& op, int id);
    void canvas_content(QPainter& p, const CanvasOp& op, const QImage* self);

    std::map<FaceKey, std::shared_ptr<Face>> faces_;
    std::unordered_map<int, std::shared_ptr<Face>> handles_;
    int next_font_ = 0;
    std::unordered_map<int, QImage> images_;
    int64_t image_bytes_ = 0;  // decoded, for MAX_IMAGE_BYTES
    std::unordered_map<int, QImage> canvases_;
    QImage reference_;  // the 96 dpi device fonts are measured against
    // A raster depends on the scale and the fraction of a pixel it was drawn at, besides the key.
    using RasterKey = std::tuple<uint64_t, int64_t, int64_t, int64_t, int64_t>;
    struct Raster {
        QImage image;
        uint64_t used = 0;
    };
    std::map<RasterKey, Raster> rasters_;
    int64_t raster_pixels_ = 0;
    uint64_t raster_clock_ = 0;
};

} // namespace twlite
