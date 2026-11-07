#pragma once

#include "litehtml.h"
#include <cstdint>

namespace lhx
{

struct TextStyle
{
    const void*           nativeFont = nullptr;
    uint64_t              paintIdentity = 0;
    float                 ascent = 0;
    float                 descent = 0;
    bool                  underline = false;
    bool                  overline = false;
    bool                  strike = false;
    litehtml::web_color   decorationColor;
    int                   decorationStyle = 0;
    float                 decorationThickness = 0;
    litehtml::web_color   shadowColor;
    float                 shadowX = 0;
    float                 shadowY = 0;
    float                 shadowBlur = 0;
    bool                  hasShadows = false;
    int                   smoothing = 0;
};

class Painter
{
  public:
    virtual ~Painter() = default;

    virtual void pushClipPath(const litehtml::vector_path& path) {}
    virtual void pushClip(const litehtml::position& box, const litehtml::border_radiuses& radius) {}
    virtual void popClip() {}
    virtual void beginGroup(const litehtml::paint_group& group) {}
    virtual void endGroup() {}

    virtual void text(const char* text, const TextStyle& style, const litehtml::web_color& color, const litehtml::position& pos) {}
    virtual void solidFill(const litehtml::background_layer& layer, const litehtml::web_color& color) {}
    virtual void linearGradient(const litehtml::background_layer& layer, const litehtml::background_layer::linear_gradient& gradient) {}
    virtual void radialGradient(const litehtml::background_layer& layer, const litehtml::background_layer::radial_gradient& gradient) {}
    virtual void conicGradient(const litehtml::background_layer& layer, const litehtml::background_layer::conic_gradient& gradient) {}
    virtual void image(const litehtml::background_layer& layer, const std::string& url) {}
    virtual void listMarker(const litehtml::list_marker& marker) {}
    virtual void borders(const litehtml::borders& borders, const litehtml::position& box, bool root) {}
    virtual void boxShadow(const litehtml::position& box, const litehtml::border_radiuses& radius, const litehtml::shadow_paint& shadow) {}
    virtual void path(const litehtml::vector_path& path) {}
};

}
