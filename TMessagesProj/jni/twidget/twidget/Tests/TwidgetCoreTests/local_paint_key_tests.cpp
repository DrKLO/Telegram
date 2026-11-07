#include "../../Sources/LiteHTML/core/lhx_local_paint_key.h"

#include <cstdio>
#include <limits>
#include <string>

namespace
{

int failures = 0;

void check(bool condition, const char* name)
{
    std::printf("%s %s\n", condition ? "PASS" : "FAIL", name);
    if(!condition)
        ++failures;
}

litehtml::background_layer layer(float x, float y)
{
    litehtml::background_layer value;
    value.border_box = litehtml::position(x, y, 100, 32);
    value.clip_box = value.border_box;
    value.origin_box = value.border_box;
    value.border_radius.top_left_x = 4;
    value.border_radius.top_left_y = 4;
    return value;
}

litehtml::background_layer::linear_gradient gradient(float x, float y)
{
    litehtml::background_layer::linear_gradient value;
    value.start.x = x;
    value.start.y = y;
    value.end.x = x + 100;
    value.end.y = y;
    value.color_points.emplace_back(0, litehtml::web_color(20, 40, 60));
    value.color_points.emplace_back(1, litehtml::web_color(70, 90, 110));
    return value;
}

lhx::TextStyle font()
{
    lhx::TextStyle value;
    value.paintIdentity = 10;
    value.ascent = 10;
    value.descent = 3;
    value.shadowColor = litehtml::web_color(0, 0, 0, 0);
    return value;
}

void track(lhx::LocalPaintKey& key, float x, float y, const lhx::TextStyle& style)
{
    const auto bg = layer(x, y);
    key.pushClip(bg.clip_box, bg.border_radius);
    key.linearGradient(bg, gradient(x, y));
    key.text("Active 12", style, litehtml::web_color(140, 230, 190), litehtml::position(x + 10, y + 8, 68, 14));
    key.popClip();
}

void translations()
{
    lhx::LocalPaintKey first(12, 24, 2), translated(28, 64, 2), fractionalX(28.25, 64, 2);
    track(first, 12, 24, font());
    track(translated, 28, 64, font());
    track(fractionalX, 28.25f, 64, font());
    check(first.valid() && translated.valid() && fractionalX.valid(), "complete clipped tracks accepted");
    check(first.bytes() == translated.bytes(), "integer translation preserves local paint key");
    check(first.bytes() != fractionalX.bytes(), "horizontal text sampling phase invalidates");

    lhx::LocalPaintKey verticalPhase(12, 24.25, 2), scale(12, 24, 1);
    track(verticalPhase, 12, 24.25f, font());
    track(scale, 12, 24, font());
    check(first.bytes() != verticalPhase.bytes(), "vertical text sampling phase invalidates");
    check(first.bytes() != scale.bytes(), "raster scale invalidates");

    lhx::LocalPaintKey plain(12, 24, 2), moved(28, 64, 2);
    auto firstLayer = layer(12, 24), movedLayer = layer(28, 64);
    firstLayer.origin_box = litehtml::position();
    movedLayer.origin_box = litehtml::position();
    plain.solidFill(firstLayer, litehtml::web_color(30, 40, 50));
    moved.solidFill(movedLayer, litehtml::web_color(30, 40, 50));
    check(plain.valid() && moved.valid() && plain.bytes() == moved.bytes(), "unused solid background origin does not tie key to placement");
}

void mutations()
{
    lhx::LocalPaintKey original(0, 0, 2), changedFont(0, 0, 2), changedColor(0, 0, 2), changedText(0, 0, 2), changedGeometry(0, 0, 2);
    auto style = font();
    const litehtml::position box(10, 8, 68, 14);
    original.text("Active 12", style, litehtml::web_color(140, 230, 190), box);
    ++style.paintIdentity;
    changedFont.text("Active 12", style, litehtml::web_color(140, 230, 190), box);
    style = font();
    changedColor.text("Active 12", style, litehtml::web_color(140, 230, 191), box);
    changedText.text("Active 13", style, litehtml::web_color(140, 230, 190), box);
    changedGeometry.text("Active 12", style, litehtml::web_color(140, 230, 190), litehtml::position(10, 9, 68, 14));
    check(original.bytes() != changedFont.bytes(), "immutable font identity invalidates inherited font changes");
    check(original.bytes() != changedColor.bytes(), "inherited text color invalidates");
    check(original.bytes() != changedText.bytes(), "text content invalidates");
    check(original.bytes() != changedGeometry.bytes(), "text fragment geometry invalidates wrapping changes");

    lhx::LocalPaintKey ordered(0, 0, 2), reversed(0, 0, 2), gradientStops(0, 0, 2), gradientHint(0, 0, 2);
    auto bg = layer(0, 0);
    auto grad = gradient(0, 0);
    ordered.linearGradient(bg, grad);
    ordered.solidFill(bg, litehtml::web_color(1, 2, 3));
    reversed.solidFill(bg, litehtml::web_color(1, 2, 3));
    reversed.linearGradient(bg, grad);
    grad.color_points[1].color.red++;
    gradientStops.linearGradient(bg, grad);
    gradientStops.solidFill(bg, litehtml::web_color(1, 2, 3));
    grad = gradient(0, 0);
    grad.color_points[0].hint = 0.3f;
    gradientHint.linearGradient(bg, grad);
    gradientHint.solidFill(bg, litehtml::web_color(1, 2, 3));
    check(ordered.bytes() != reversed.bytes(), "command ordering invalidates");
    check(ordered.bytes() != gradientStops.bytes(), "gradient colors invalidate");
    check(ordered.bytes() != gradientHint.bytes(), "gradient hints invalidate");

    lhx::LocalPaintKey clipped(0, 0, 2), differentlyClipped(0, 0, 2);
    clipped.pushClip(bg.border_box, bg.border_radius);
    clipped.solidFill(bg, litehtml::web_color(1, 2, 3));
    clipped.popClip();
    bg.border_radius.top_left_x = 5;
    differentlyClipped.pushClip(bg.border_box, bg.border_radius);
    bg.border_radius.top_left_x = 4;
    differentlyClipped.solidFill(bg, litehtml::web_color(1, 2, 3));
    differentlyClipped.popClip();
    check(clipped.valid() && differentlyClipped.valid() && clipped.bytes() != differentlyClipped.bytes(), "rounded clip geometry invalidates");

    lhx::LocalPaintKey bordered(0, 0, 2), movedBorder(12, 24, 2), changedBorder(0, 0, 2);
    litehtml::borders borders;
    borders.left.width = 2;
    borders.left.style = litehtml::border_style_solid;
    borders.left.color = litehtml::web_color(80, 90, 100);
    bordered.borders(borders, litehtml::position(0, 0, 100, 32), false);
    movedBorder.borders(borders, litehtml::position(12, 24, 100, 32), false);
    borders.left.style = litehtml::border_style_dashed;
    changedBorder.borders(borders, litehtml::position(0, 0, 100, 32), false);
    check(bordered.valid() && movedBorder.valid() && bordered.bytes() == movedBorder.bytes(), "border translation preserves key");
    check(bordered.bytes() != changedBorder.bytes(), "border style invalidates");
}

void rejection()
{
    lhx::LocalPaintKey unclosed(0, 0, 2), underflow(0, 0, 2), limitedCommands(0, 0, 2, 65536, 1), limitedBytes(0, 0, 2, 32);
    auto bg = layer(0, 0);
    unclosed.pushClip(bg.clip_box, bg.border_radius);
    unclosed.solidFill(bg, litehtml::web_color(1, 2, 3));
    underflow.popClip();
    limitedCommands.solidFill(bg, litehtml::web_color(1, 2, 3));
    limitedCommands.solidFill(bg, litehtml::web_color(1, 2, 3));
    limitedBytes.solidFill(bg, litehtml::web_color(1, 2, 3));
    check(!unclosed.valid() && !underflow.valid(), "unbalanced clips rejected");
    check(!limitedCommands.valid() && limitedCommands.bytes().empty(), "command cap rejects entire key");
    check(!limitedBytes.valid() && limitedBytes.bytes().empty(), "byte cap rejects entire key");

    lhx::LocalPaintKey shadowed(0, 0, 2), unknownFont(0, 0, 2), image(0, 0, 2), group(0, 0, 2), negative(0, 0, 2);
    auto style = font();
    style.hasShadows = true;
    shadowed.text("shadow", style, litehtml::web_color(1, 2, 3), bg.border_box);
    style = font();
    style.paintIdentity = 0;
    unknownFont.text("font", style, litehtml::web_color(1, 2, 3), bg.border_box);
    image.image(bg, "image.png");
    group.endGroup();
    bg.border_box.width = -1;
    negative.solidFill(bg, litehtml::web_color(1, 2, 3));
    check(!shadowed.valid() && !unknownFont.valid(), "unsupported text shadow and missing font identity rejected");
    check(!image.valid() && !group.valid(), "unsupported image and group rejected");
    check(!negative.valid(), "negative geometry rejected");

    lhx::LocalPaintKey invalidOrigin(std::numeric_limits<double>::infinity(), 0, 2), invalidScale(0, 0, 0), nan(0, 0, 2);
    bg = layer(0, 0);
    bg.border_box.x = std::numeric_limits<float>::quiet_NaN();
    nan.solidFill(bg, litehtml::web_color(1, 2, 3));
    check(!invalidOrigin.valid() && !invalidScale.valid() && !nan.valid(), "nonfinite geometry and invalid scale rejected");

    lhx::LocalPaintKey oversized(0, 0, 2, 512);
    const std::string text(1000, 'x');
    oversized.text(text.c_str(), font(), litehtml::web_color(1, 2, 3), litehtml::position(0, 0, 100, 20));
    check(!oversized.valid() && oversized.bytes().empty(), "oversized text stops at byte limit");

    lhx::LocalPaintKey deep(0, 0, 2), invalidGradient(0, 0, 2);
    bg = layer(0, 0);
    for(int i = 0; i < 65; ++i)
        deep.pushClip(bg.clip_box, bg.border_radius);
    auto grad = gradient(0, 0);
    grad.color_points[0].offset = std::numeric_limits<float>::infinity();
    invalidGradient.linearGradient(bg, grad);
    check(!deep.valid() && deep.bytes().empty(), "clip depth bounded");
    check(!invalidGradient.valid() && invalidGradient.bytes().empty(), "nonfinite gradient stops rejected");
}

}

int main()
{
    translations();
    mutations();
    rejection();
    return failures ? 1 : 0;
}
