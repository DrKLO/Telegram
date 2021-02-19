/*
 * Copyright (c) 2020 Samsung Electronics Co., Ltd. All rights reserved.

 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

#include "vbitmap.h"
#include <string>
#include <memory>
#include "vdrawhelper.h"
#include "vglobal.h"

V_BEGIN_NAMESPACE

void VBitmap::Impl::reset(size_t width, size_t height, VBitmap::Format format)
{
    mRoData = nullptr;
    mWidth = uint(width);
    mHeight = uint(height);
    mFormat = format;

    mDepth = depth(format);
    mStride = ((mWidth * mDepth + 31) >> 5)
                  << 2;  // bytes per scanline (must be multiple of 4)
    mOwnData = std::make_unique<uchar[]>(mStride * mHeight);
}

void VBitmap::Impl::reset(uchar *data, size_t width, size_t height, size_t bytesPerLine,
                          VBitmap::Format format)
{
    mRoData = data;
    mWidth = uint(width);
    mHeight = uint(height);
    mStride = uint(bytesPerLine);
    mFormat = format;
    mDepth = depth(format);
    mOwnData = nullptr;
}

uchar VBitmap::Impl::depth(VBitmap::Format format)
{
    uchar depth = 1;
    switch (format) {
    case VBitmap::Format::Alpha8:
        depth = 8;
        break;
    case VBitmap::Format::ARGB32:
    case VBitmap::Format::ARGB32_Premultiplied:
        depth = 32;
        break;
    default:
        break;
    }
    return depth;
}

void VBitmap::Impl::fill(uint /*pixel*/)
{
    //@TODO
}

void VBitmap::Impl::updateLuma()
{
    if (mFormat != VBitmap::Format::ARGB32_Premultiplied) return;
    auto dataPtr = data();
    for (uint col = 0; col < mHeight; col++) {
        uint *pixel = (uint *)(dataPtr + mStride * col);
        for (uint row = 0; row < mWidth; row++) {
            int alpha = vAlpha(*pixel);
            if (alpha == 0) {
                pixel++;
                continue;
            }

            int red = vRed(*pixel);
            int green = vGreen(*pixel);
            int blue = vBlue(*pixel);

            if (alpha != 255) {
                // un multiply
                red = (red * 255) / alpha;
                green = (green * 255) / alpha;
                blue = (blue * 255) / alpha;
            }
            int luminosity = int(0.299f * red + 0.587f * green + 0.114f * blue);
            *pixel = luminosity << 24;
            pixel++;
        }
    }
}

VBitmap::VBitmap(size_t width, size_t height, VBitmap::Format format)
{
    if (width <= 0 || height <= 0 || format == Format::Invalid) return;

    mImpl = rc_ptr<Impl>(width, height, format);
}

VBitmap::VBitmap(uchar *data, size_t width, size_t height, size_t bytesPerLine,
                 VBitmap::Format format)
{
    if (!data || width <= 0 || height <= 0 || bytesPerLine <= 0 ||
        format == Format::Invalid)
        return;

    mImpl = rc_ptr<Impl>(data, width, height, bytesPerLine, format);
}

void VBitmap::reset(uchar *data, size_t w, size_t h, size_t bytesPerLine,
                    VBitmap::Format format)
{
    if (mImpl) {
        mImpl->reset(data, w, h, bytesPerLine, format);
    } else {
        mImpl = rc_ptr<Impl>(data, w, h, bytesPerLine, format);
    }
}

void VBitmap::reset(size_t w, size_t h, VBitmap::Format format)
{
    if (mImpl) {
        if (w == mImpl->width() && h == mImpl->height() &&
            format == mImpl->format()) {
            return;
        }
        mImpl->reset(w, h, format);
    } else {
        mImpl = rc_ptr<Impl>(w, h, format);
    }
}

size_t VBitmap::stride() const
{
    return mImpl ? mImpl->stride() : 0;
}

size_t VBitmap::width() const
{
    return mImpl ? mImpl->width() : 0;
}

size_t VBitmap::height() const
{
    return mImpl ? mImpl->height() : 0;
}

size_t VBitmap::depth() const
{
    return mImpl ? mImpl->mDepth : 0;
}

uchar *VBitmap::data()
{
    return mImpl ? mImpl->data() : nullptr;
}

uchar *VBitmap::data() const
{
    return mImpl ? mImpl->data() : nullptr;
}

VRect VBitmap::rect() const
{
    return mImpl ? mImpl->rect() : VRect();
}

VSize VBitmap::size() const
{
    return mImpl ? mImpl->size() : VSize();
}

bool VBitmap::valid() const
{
    return mImpl;
}

VBitmap::Format VBitmap::format() const
{
    return mImpl ? mImpl->format() : VBitmap::Format::Invalid;
}

void VBitmap::fill(uint pixel)
{
    if (mImpl) mImpl->fill(pixel);
}

/*
 * This is special function which converts
 * RGB value to Luminosity and stores it in
 * the Alpha component of the pixel.
 * After this conversion the bitmap data is no more
 * in RGB space. but the Alpha component contains the
 *  Luminosity value of the pixel in HSL color space.
 * NOTE: this api has its own special usecase
 * make sure you know what you are doing before using
 * this api.
 */
void VBitmap::updateLuma()
{
    if (mImpl) mImpl->updateLuma();
}

V_END_NAMESPACE
