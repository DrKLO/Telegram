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

#include "vmatrix.h"
#include <vglobal.h>
#include <cassert>
#include <cmath>

V_BEGIN_NAMESPACE

/*  m11  m21  mtx
 *  m12  m22  mty
 *  m13  m23  m33
 */

inline float VMatrix::determinant() const
{
    return m11 * (m33 * m22 - mty * m23) - m21 * (m33 * m12 - mty * m13) +
           mtx * (m23 * m12 - m22 * m13);
}

bool VMatrix::isAffine() const
{
    return type() < MatrixType::Project;
}

bool VMatrix::isIdentity() const
{
    return type() == MatrixType::None;
}

bool VMatrix::isInvertible() const
{
    return !vIsZero(determinant());
}

bool VMatrix::isScaling() const
{
    return type() >= MatrixType::Scale;
}
bool VMatrix::isRotating() const
{
    return type() >= MatrixType::Rotate;
}

bool VMatrix::isTranslating() const
{
    return type() >= MatrixType::Translate;
}

VMatrix &VMatrix::operator*=(float num)
{
    if (num == 1.) return *this;

    m11 *= num;
    m12 *= num;
    m13 *= num;
    m21 *= num;
    m22 *= num;
    m23 *= num;
    mtx *= num;
    mty *= num;
    m33 *= num;
    if (dirty < MatrixType::Scale) dirty = MatrixType::Scale;

    return *this;
}

VMatrix &VMatrix::operator/=(float div)
{
    if (div == 0) return *this;

    div = 1 / div;
    return operator*=(div);
}

VMatrix::MatrixType VMatrix::type() const
{
    if (dirty == MatrixType::None || dirty < mType) return mType;

    switch (dirty) {
    case MatrixType::Project:
        if (!vIsZero(m13) || !vIsZero(m23) || !vIsZero(m33 - 1)) {
            mType = MatrixType::Project;
            break;
        }
        VECTOR_FALLTHROUGH
    case MatrixType::Shear:
    case MatrixType::Rotate:
        if (!vIsZero(m12) || !vIsZero(m21)) {
            const float dot = m11 * m12 + m21 * m22;
            if (vIsZero(dot))
                mType = MatrixType::Rotate;
            else
                mType = MatrixType::Shear;
            break;
        }
        VECTOR_FALLTHROUGH
    case MatrixType::Scale:
        if (!vIsZero(m11 - 1) || !vIsZero(m22 - 1)) {
            mType = MatrixType::Scale;
            break;
        }
        VECTOR_FALLTHROUGH
    case MatrixType::Translate:
        if (!vIsZero(mtx) || !vIsZero(mty)) {
            mType = MatrixType::Translate;
            break;
        }
        VECTOR_FALLTHROUGH
    case MatrixType::None:
        mType = MatrixType::None;
        break;
    }

    dirty = MatrixType::None;
    return mType;
}

VMatrix &VMatrix::translate(float dx, float dy)
{
    if (dx == 0 && dy == 0) return *this;

    switch (type()) {
    case MatrixType::None:
        mtx = dx;
        mty = dy;
        break;
    case MatrixType::Translate:
        mtx += dx;
        mty += dy;
        break;
    case MatrixType::Scale:
        mtx += dx * m11;
        mty += dy * m22;
        break;
    case MatrixType::Project:
        m33 += dx * m13 + dy * m23;
        VECTOR_FALLTHROUGH
    case MatrixType::Shear:
    case MatrixType::Rotate:
        mtx += dx * m11 + dy * m21;
        mty += dy * m22 + dx * m12;
        break;
    }
    if (dirty < MatrixType::Translate) dirty = MatrixType::Translate;
    return *this;
}

VMatrix &VMatrix::scale(float sx, float sy)
{
    if (sx == 1 && sy == 1) return *this;

    switch (type()) {
    case MatrixType::None:
    case MatrixType::Translate:
        m11 = sx;
        m22 = sy;
        break;
    case MatrixType::Project:
        m13 *= sx;
        m23 *= sy;
        VECTOR_FALLTHROUGH
    case MatrixType::Rotate:
    case MatrixType::Shear:
        m12 *= sx;
        m21 *= sy;
        VECTOR_FALLTHROUGH
    case MatrixType::Scale:
        m11 *= sx;
        m22 *= sy;
        break;
    }
    if (dirty < MatrixType::Scale) dirty = MatrixType::Scale;
    return *this;
}

VMatrix &VMatrix::shear(float sh, float sv)
{
    if (sh == 0 && sv == 0) return *this;

    switch (type()) {
    case MatrixType::None:
    case MatrixType::Translate:
        m12 = sv;
        m21 = sh;
        break;
    case MatrixType::Scale:
        m12 = sv * m22;
        m21 = sh * m11;
        break;
    case MatrixType::Project: {
        float tm13 = sv * m23;
        float tm23 = sh * m13;
        m13 += tm13;
        m23 += tm23;
        VECTOR_FALLTHROUGH
    }
    case MatrixType::Rotate:
    case MatrixType::Shear: {
        float tm11 = sv * m21;
        float tm22 = sh * m12;
        float tm12 = sv * m22;
        float tm21 = sh * m11;
        m11 += tm11;
        m12 += tm12;
        m21 += tm21;
        m22 += tm22;
        break;
    }
    }
    if (dirty < MatrixType::Shear) dirty = MatrixType::Shear;
    return *this;
}

static const float deg2rad = float(0.017453292519943295769);  // pi/180
static const float inv_dist_to_plane = 1. / 1024.;

VMatrix &VMatrix::rotate(float a, Axis axis)
{
    if (a == 0) return *this;

    float sina = 0;
    float cosa = 0;
    if (a == 90. || a == -270.)
        sina = 1.;
    else if (a == 270. || a == -90.)
        sina = -1.;
    else if (a == 180.)
        cosa = -1.;
    else {
        float b = deg2rad * a;  // convert to radians
        sina = std::sin(b);     // fast and convenient
        cosa = std::cos(b);
    }

    if (axis == Axis::Z) {
        switch (type()) {
        case MatrixType::None:
        case MatrixType::Translate:
            m11 = cosa;
            m12 = sina;
            m21 = -sina;
            m22 = cosa;
            break;
        case MatrixType::Scale: {
            float tm11 = cosa * m11;
            float tm12 = sina * m22;
            float tm21 = -sina * m11;
            float tm22 = cosa * m22;
            m11 = tm11;
            m12 = tm12;
            m21 = tm21;
            m22 = tm22;
            break;
        }
        case MatrixType::Project: {
            float tm13 = cosa * m13 + sina * m23;
            float tm23 = -sina * m13 + cosa * m23;
            m13 = tm13;
            m23 = tm23;
            VECTOR_FALLTHROUGH
        }
        case MatrixType::Rotate:
        case MatrixType::Shear: {
            float tm11 = cosa * m11 + sina * m21;
            float tm12 = cosa * m12 + sina * m22;
            float tm21 = -sina * m11 + cosa * m21;
            float tm22 = -sina * m12 + cosa * m22;
            m11 = tm11;
            m12 = tm12;
            m21 = tm21;
            m22 = tm22;
            break;
        }
        }
        if (dirty < MatrixType::Rotate) dirty = MatrixType::Rotate;
    } else {
        VMatrix result;
        if (axis == Axis::Y) {
            result.m11 = cosa;
            result.m13 = -sina * inv_dist_to_plane;
        } else {
            result.m22 = cosa;
            result.m23 = -sina * inv_dist_to_plane;
        }
        result.mType = MatrixType::Project;
        *this = result * *this;
    }

    return *this;
}

VMatrix VMatrix::operator*(const VMatrix &m) const
{
    const MatrixType otherType = m.type();
    if (otherType == MatrixType::None) return *this;

    const MatrixType thisType = type();
    if (thisType == MatrixType::None) return m;

    VMatrix    t;
    MatrixType type = vMax(thisType, otherType);
    switch (type) {
    case MatrixType::None:
        break;
    case MatrixType::Translate:
        t.mtx = mtx + m.mtx;
        t.mty += mty + m.mty;
        break;
    case MatrixType::Scale: {
        float m11v = m11 * m.m11;
        float m22v = m22 * m.m22;

        float m31v = mtx * m.m11 + m.mtx;
        float m32v = mty * m.m22 + m.mty;

        t.m11 = m11v;
        t.m22 = m22v;
        t.mtx = m31v;
        t.mty = m32v;
        break;
    }
    case MatrixType::Rotate:
    case MatrixType::Shear: {
        float m11v = m11 * m.m11 + m12 * m.m21;
        float m12v = m11 * m.m12 + m12 * m.m22;

        float m21v = m21 * m.m11 + m22 * m.m21;
        float m22v = m21 * m.m12 + m22 * m.m22;

        float m31v = mtx * m.m11 + mty * m.m21 + m.mtx;
        float m32v = mtx * m.m12 + mty * m.m22 + m.mty;

        t.m11 = m11v;
        t.m12 = m12v;
        t.m21 = m21v;
        t.m22 = m22v;
        t.mtx = m31v;
        t.mty = m32v;
        break;
    }
    case MatrixType::Project: {
        float m11v = m11 * m.m11 + m12 * m.m21 + m13 * m.mtx;
        float m12v = m11 * m.m12 + m12 * m.m22 + m13 * m.mty;
        float m13v = m11 * m.m13 + m12 * m.m23 + m13 * m.m33;

        float m21v = m21 * m.m11 + m22 * m.m21 + m23 * m.mtx;
        float m22v = m21 * m.m12 + m22 * m.m22 + m23 * m.mty;
        float m23v = m21 * m.m13 + m22 * m.m23 + m23 * m.m33;

        float m31v = mtx * m.m11 + mty * m.m21 + m33 * m.mtx;
        float m32v = mtx * m.m12 + mty * m.m22 + m33 * m.mty;
        float m33v = mtx * m.m13 + mty * m.m23 + m33 * m.m33;

        t.m11 = m11v;
        t.m12 = m12v;
        t.m13 = m13v;
        t.m21 = m21v;
        t.m22 = m22v;
        t.m23 = m23v;
        t.mtx = m31v;
        t.mty = m32v;
        t.m33 = m33v;
    }
    }

    t.dirty = type;
    t.mType = type;

    return t;
}

VMatrix &VMatrix::operator*=(const VMatrix &o)
{
    const MatrixType otherType = o.type();
    if (otherType == MatrixType::None) return *this;

    const MatrixType thisType = type();
    if (thisType == MatrixType::None) return operator=(o);

    MatrixType t = vMax(thisType, otherType);
    switch (t) {
    case MatrixType::None:
        break;
    case MatrixType::Translate:
        mtx += o.mtx;
        mty += o.mty;
        break;
    case MatrixType::Scale: {
        float m11v = m11 * o.m11;
        float m22v = m22 * o.m22;

        float m31v = mtx * o.m11 + o.mtx;
        float m32v = mty * o.m22 + o.mty;

        m11 = m11v;
        m22 = m22v;
        mtx = m31v;
        mty = m32v;
        break;
    }
    case MatrixType::Rotate:
    case MatrixType::Shear: {
        float m11v = m11 * o.m11 + m12 * o.m21;
        float m12v = m11 * o.m12 + m12 * o.m22;

        float m21v = m21 * o.m11 + m22 * o.m21;
        float m22v = m21 * o.m12 + m22 * o.m22;

        float m31v = mtx * o.m11 + mty * o.m21 + o.mtx;
        float m32v = mtx * o.m12 + mty * o.m22 + o.mty;

        m11 = m11v;
        m12 = m12v;
        m21 = m21v;
        m22 = m22v;
        mtx = m31v;
        mty = m32v;
        break;
    }
    case MatrixType::Project: {
        float m11v = m11 * o.m11 + m12 * o.m21 + m13 * o.mtx;
        float m12v = m11 * o.m12 + m12 * o.m22 + m13 * o.mty;
        float m13v = m11 * o.m13 + m12 * o.m23 + m13 * o.m33;

        float m21v = m21 * o.m11 + m22 * o.m21 + m23 * o.mtx;
        float m22v = m21 * o.m12 + m22 * o.m22 + m23 * o.mty;
        float m23v = m21 * o.m13 + m22 * o.m23 + m23 * o.m33;

        float m31v = mtx * o.m11 + mty * o.m21 + m33 * o.mtx;
        float m32v = mtx * o.m12 + mty * o.m22 + m33 * o.mty;
        float m33v = mtx * o.m13 + mty * o.m23 + m33 * o.m33;

        m11 = m11v;
        m12 = m12v;
        m13 = m13v;
        m21 = m21v;
        m22 = m22v;
        m23 = m23v;
        mtx = m31v;
        mty = m32v;
        m33 = m33v;
    }
    }

    dirty = t;
    mType = t;

    return *this;
}

VMatrix VMatrix::adjoint() const
{
    float h11, h12, h13, h21, h22, h23, h31, h32, h33;
    h11 = m22 * m33 - m23 * mty;
    h21 = m23 * mtx - m21 * m33;
    h31 = m21 * mty - m22 * mtx;
    h12 = m13 * mty - m12 * m33;
    h22 = m11 * m33 - m13 * mtx;
    h32 = m12 * mtx - m11 * mty;
    h13 = m12 * m23 - m13 * m22;
    h23 = m13 * m21 - m11 * m23;
    h33 = m11 * m22 - m12 * m21;

    VMatrix res;
    res.m11 = h11;
    res.m12 = h12;
    res.m13 = h13;
    res.m21 = h21;
    res.m22 = h22;
    res.m23 = h23;
    res.mtx = h31;
    res.mty = h32;
    res.m33 = h33;
    res.mType = MatrixType::None;
    res.dirty = MatrixType::Project;

    return res;
}

VMatrix VMatrix::inverted(bool *invertible) const
{
    VMatrix invert;
    bool    inv = true;

    switch (type()) {
    case MatrixType::None:
        break;
    case MatrixType::Translate:
        invert.mtx = -mtx;
        invert.mty = -mty;
        break;
    case MatrixType::Scale:
        inv = !vIsZero(m11);
        inv &= !vIsZero(m22);
        if (inv) {
            invert.m11 = 1.0f / m11;
            invert.m22 = 1.0f / m22;
            invert.mtx = -mtx * invert.m11;
            invert.mty = -mty * invert.m22;
        }
        break;
    default:
        // general case
        float det = determinant();
        inv = !vIsZero(det);
        if (inv) invert = (adjoint() /= det);
        // TODO Test above line
        break;
    }

    if (invertible) *invertible = inv;

    if (inv) {
        // inverting doesn't change the type
        invert.mType = mType;
        invert.dirty = dirty;
    }

    return invert;
}

bool VMatrix::operator==(const VMatrix &o) const
{
    return fuzzyCompare(o);
}

bool VMatrix::operator!=(const VMatrix &o) const
{
    return !operator==(o);
}

bool VMatrix::fuzzyCompare(const VMatrix &o) const
{
    return vCompare(m11, o.m11) && vCompare(m12, o.m12) &&
           vCompare(m21, o.m21) && vCompare(m22, o.m22) &&
           vCompare(mtx, o.mtx) && vCompare(mty, o.mty);
}

#define V_NEAR_CLIP 0.000001f
#ifdef MAP
#undef MAP
#endif
#define MAP(x, y, nx, ny)                                \
    do {                                                 \
        float FX_ = x;                                   \
        float FY_ = y;                                   \
        switch (t) {                                     \
        case MatrixType::None:                           \
            nx = FX_;                                    \
            ny = FY_;                                    \
            break;                                       \
        case MatrixType::Translate:                      \
            nx = FX_ + mtx;                              \
            ny = FY_ + mty;                              \
            break;                                       \
        case MatrixType::Scale:                          \
            nx = m11 * FX_ + mtx;                        \
            ny = m22 * FY_ + mty;                        \
            break;                                       \
        case MatrixType::Rotate:                         \
        case MatrixType::Shear:                          \
        case MatrixType::Project:                        \
            nx = m11 * FX_ + m21 * FY_ + mtx;            \
            ny = m12 * FX_ + m22 * FY_ + mty;            \
            if (t == MatrixType::Project) {              \
                float w = (m13 * FX_ + m23 * FY_ + m33); \
                if (w < V_NEAR_CLIP) w = V_NEAR_CLIP;    \
                w = 1. / w;                              \
                nx *= w;                                 \
                ny *= w;                                 \
            }                                            \
        }                                                \
    } while (0)

VRect VMatrix::map(const VRect &rect) const
{
    VMatrix::MatrixType t = type();
    if (t <= MatrixType::Translate)
        return rect.translated(std::lround(mtx), std::lround(mty));

    if (t <= MatrixType::Scale) {
        int x = std::lround(m11 * rect.x() + mtx);
        int y = std::lround(m22 * rect.y() + mty);
        int w = std::lround(m11 * rect.width());
        int h = std::lround(m22 * rect.height());
        if (w < 0) {
            w = -w;
            x -= w;
        }
        if (h < 0) {
            h = -h;
            y -= h;
        }
        return {x, y, w, h};
    } else if (t < MatrixType::Project) {
        // see mapToPolygon for explanations of the algorithm.
        float x = 0, y = 0;
        MAP(rect.left(), rect.top(), x, y);
        float xmin = x;
        float ymin = y;
        float xmax = x;
        float ymax = y;
        MAP(rect.right() + 1, rect.top(), x, y);
        xmin = vMin(xmin, x);
        ymin = vMin(ymin, y);
        xmax = vMax(xmax, x);
        ymax = vMax(ymax, y);
        MAP(rect.right() + 1, rect.bottom() + 1, x, y);
        xmin = vMin(xmin, x);
        ymin = vMin(ymin, y);
        xmax = vMax(xmax, x);
        ymax = vMax(ymax, y);
        MAP(rect.left(), rect.bottom() + 1, x, y);
        xmin = vMin(xmin, x);
        ymin = vMin(ymin, y);
        xmax = vMax(xmax, x);
        ymax = vMax(ymax, y);
        return VRect(std::lround(xmin), std::lround(ymin),
                     std::lround(xmax) - std::lround(xmin),
                     std::lround(ymax) - std::lround(ymin));
    } else {
        // Not supported
        assert(0);
        return {};
    }
}

VPointF VMatrix::map(const VPointF &p) const
{
    float fx = p.x();
    float fy = p.y();

    float x = 0, y = 0;

    VMatrix::MatrixType t = type();
    switch (t) {
    case MatrixType::None:
        x = fx;
        y = fy;
        break;
    case MatrixType::Translate:
        x = fx + mtx;
        y = fy + mty;
        break;
    case MatrixType::Scale:
        x = m11 * fx + mtx;
        y = m22 * fy + mty;
        break;
    case MatrixType::Rotate:
    case MatrixType::Shear:
    case MatrixType::Project:
        x = m11 * fx + m21 * fy + mtx;
        y = m12 * fx + m22 * fy + mty;
        if (t == MatrixType::Project) {
            float w = 1.0f / (m13 * fx + m23 * fy + m33);
            x *= w;
            y *= w;
        }
    }
    return {x, y};
}

V_END_NAMESPACE
