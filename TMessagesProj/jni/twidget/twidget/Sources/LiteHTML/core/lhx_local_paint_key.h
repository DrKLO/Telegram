#pragma once

#include "lhx_painter.h"

#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <vector>

namespace lhx
{

class LocalPaintKey final : public Painter
{
  public:
    LocalPaintKey(double originX, double originY, double rasterScale, size_t maxBytes = 65536,
                  size_t maxCommands = 512) :
        m_originX(originX), m_originY(originY), m_maxBytes(maxBytes), m_maxCommands(maxCommands)
    {
        if(!std::isfinite(originX) || !std::isfinite(originY) || !std::isfinite(rasterScale) ||
           rasterScale <= 0 || rasterScale > 16 || !std::isfinite(originX * rasterScale) || !std::isfinite(originY * rasterScale))
        {
            invalidate();
            return;
        }
        integer(1);
        number(rasterScale);
        const double horizontal = originX * rasterScale;
        number(horizontal - std::floor(horizontal));
        const double vertical = originY * rasterScale;
        number(vertical - std::floor(vertical));
    }

    bool valid() const { return m_valid && m_clipDepth == 0 && m_paints != 0; }
    const std::vector<uint8_t>& bytes() const { return m_bytes; }
    size_t commandCount() const { return m_commands; }

    void invalidate()
    {
        m_valid = false;
        m_bytes.clear();
    }

    void pushClip(const litehtml::position& box, const litehtml::border_radiuses& radius) override
    {
        if(!command(1) || m_clipDepth >= 64)
        {
            invalidate();
            return;
        }
        ++m_clipDepth;
        rectangle(box);
        radii(radius);
    }

    void popClip() override
    {
        if(!command(2) || m_clipDepth == 0)
        {
            invalidate();
            return;
        }
        --m_clipDepth;
    }

    void text(const char* text, const TextStyle& style, const litehtml::web_color& color,
              const litehtml::position& pos) override
    {
        if(!command(3) || !text || style.paintIdentity == 0 || style.hasShadows || style.shadowColor.alpha != 0)
        {
            invalidate();
            return;
        }
        ++m_paints;
        integer(style.paintIdentity);
        number(style.ascent);
        number(style.descent);
        integer(style.underline);
        integer(style.overline);
        integer(style.strike);
        paintColor(style.decorationColor);
        integer(static_cast<uint64_t>(style.decorationStyle));
        number(style.decorationThickness);
        integer(static_cast<uint64_t>(style.smoothing));
        paintColor(color);
        rectangle(pos);
        string(text);
    }

    void solidFill(const litehtml::background_layer& layer, const litehtml::web_color& color) override
    {
        if(!command(4))
            return;
        ++m_paints;
        background(layer, false);
        paintColor(color);
    }

    void linearGradient(const litehtml::background_layer& layer,
                        const litehtml::background_layer::linear_gradient& gradient) override
    {
        if(!command(5))
            return;
        ++m_paints;
        background(layer, true);
        coordinate(gradient.start.x, m_originX);
        coordinate(gradient.start.y, m_originY);
        coordinate(gradient.end.x, m_originX);
        coordinate(gradient.end.y, m_originY);
        integer(static_cast<uint64_t>(gradient.color_space));
        integer(static_cast<uint64_t>(gradient.hue_interpolation));
        if(gradient.color_points.size() > m_maxBytes / 21)
        {
            invalidate();
            return;
        }
        integer(gradient.color_points.size());
        for(const auto& stop : gradient.color_points)
        {
            number(stop.offset);
            paintColor(stop.color);
            integer(stop.hint.has_value());
            if(stop.hint)
                number(*stop.hint);
            if(!m_valid)
                return;
        }
    }

    void borders(const litehtml::borders& borders, const litehtml::position& box, bool root) override
    {
        if(!command(6) || root)
        {
            invalidate();
            return;
        }
        ++m_paints;
        rectangle(box);
        border(borders.left);
        border(borders.top);
        border(borders.right);
        border(borders.bottom);
        radii(borders.radius);
    }

    void pushClipPath(const litehtml::vector_path&) override { invalidate(); }
    void beginGroup(const litehtml::paint_group&) override { invalidate(); }
    void endGroup() override { invalidate(); }
    void radialGradient(const litehtml::background_layer&, const litehtml::background_layer::radial_gradient&) override { invalidate(); }
    void conicGradient(const litehtml::background_layer&, const litehtml::background_layer::conic_gradient&) override { invalidate(); }
    void image(const litehtml::background_layer&, const std::string&) override { invalidate(); }
    void listMarker(const litehtml::list_marker&) override { invalidate(); }
    void boxShadow(const litehtml::position&, const litehtml::border_radiuses&, const litehtml::shadow_paint&) override { invalidate(); }
    void path(const litehtml::vector_path&) override { invalidate(); }

  private:
    double m_originX;
    double m_originY;
    size_t m_maxBytes;
    size_t m_maxCommands;
    size_t m_commands = 0;
    size_t m_paints = 0;
    size_t m_clipDepth = 0;
    bool m_valid = true;
    std::vector<uint8_t> m_bytes;

    bool command(uint64_t kind)
    {
        if(!m_valid || m_commands >= m_maxCommands)
        {
            invalidate();
            return false;
        }
        ++m_commands;
        integer(kind);
        return m_valid;
    }

    void append(const void* data, size_t size)
    {
        if(!m_valid)
            return;
        if(size > m_maxBytes - m_bytes.size())
        {
            invalidate();
            return;
        }
        const auto* first = static_cast<const uint8_t*>(data);
        m_bytes.insert(m_bytes.end(), first, first + size);
    }

    void integer(uint64_t value)
    {
        uint8_t bytes[8];
        for(size_t i = 0; i < sizeof(bytes); ++i)
            bytes[i] = static_cast<uint8_t>(value >> (i * 8));
        append(bytes, sizeof(bytes));
    }

    void number(double value)
    {
        if(!std::isfinite(value))
        {
            invalidate();
            return;
        }
        if(value == 0)
            value = 0;
        uint64_t bits;
        static_assert(sizeof(bits) == sizeof(value));
        std::memcpy(&bits, &value, sizeof(bits));
        integer(bits);
    }

    void coordinate(double value, double origin)
    {
        number(value - origin);
    }

    void string(const char* value)
    {
        if(!m_valid)
            return;
        const size_t remaining = m_maxBytes - m_bytes.size();
        size_t size = 0;
        while(size < remaining && value[size])
            ++size;
        if(size == remaining || remaining - size < 8)
        {
            invalidate();
            return;
        }
        integer(size);
        append(value, size);
    }

    void paintColor(const litehtml::web_color& color)
    {
        const uint8_t bytes[] = {color.red, color.green, color.blue, color.alpha,
                                 static_cast<uint8_t>(color.is_current_color)};
        append(bytes, sizeof(bytes));
    }

    void rectangle(const litehtml::position& box)
    {
        coordinate(box.x.value(), m_originX);
        coordinate(box.y.value(), m_originY);
        number(box.width.value());
        number(box.height.value());
        if(box.width.value() < 0 || box.height.value() < 0)
            invalidate();
    }

    void radii(const litehtml::border_radiuses& radius)
    {
        for(const auto value : {radius.top_left_x, radius.top_left_y, radius.top_right_x, radius.top_right_y,
                               radius.bottom_right_x, radius.bottom_right_y, radius.bottom_left_x, radius.bottom_left_y})
        {
            number(value.value());
            if(value.value() < 0)
                invalidate();
        }
    }

    void background(const litehtml::background_layer& layer, bool hasOrigin)
    {
        if(layer.is_root || layer.attachment != litehtml::background_attachment_scroll)
        {
            invalidate();
            return;
        }
        rectangle(layer.border_box);
        rectangle(layer.clip_box);
        radii(layer.border_radius);
        if(hasOrigin)
        {
            rectangle(layer.origin_box);
            integer(static_cast<uint64_t>(layer.attachment));
            integer(static_cast<uint64_t>(layer.repeat));
        }
    }

    void border(const litehtml::border& border)
    {
        number(border.width.value());
        if(border.width.value() < 0)
            invalidate();
        integer(static_cast<uint64_t>(border.style));
        paintColor(border.color);
    }
};

}
