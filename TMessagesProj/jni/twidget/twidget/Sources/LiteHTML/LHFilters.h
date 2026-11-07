#pragma once
#include <litehtml.h>
#include <CoreGraphics/CoreGraphics.h>

void       LHFilterMatrix(const litehtml::filter_function& fn, float m[20]);
CGImageRef LHCreateFilteredImage(CGImageRef source, const litehtml::filter_vector& steps, CGFloat scale, bool yDown, bool clamped, CGColorSpaceRef space);
