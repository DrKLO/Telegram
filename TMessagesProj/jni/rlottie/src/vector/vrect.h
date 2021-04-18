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

#ifndef VRECT_H
#define VRECT_H
#include "vglobal.h"
#include "vpoint.h"

V_BEGIN_NAMESPACE
class VRectF;

class VRect {
public:
    VRect() = default;
    VRect(int x, int y, int w, int h):x1(x),y1(y),x2(x+w),y2(y+h){}
    explicit VRect(VPoint pt, VSize sz):VRect(pt.x(), pt.y(), sz.width(), sz.height()){}
    operator VRectF() const;
    V_CONSTEXPR bool empty() const {return x1 >= x2 || y1 >= y2;}
    V_CONSTEXPR int left() const {return x1;}
    V_CONSTEXPR int top() const {return y1;}
    V_CONSTEXPR int right() const {return x2;}
    V_CONSTEXPR int bottom() const {return y2;}
    V_CONSTEXPR int width() const {return x2-x1;}
    V_CONSTEXPR int height() const {return y2-y1;}
    V_CONSTEXPR int x() const {return x1;}
    V_CONSTEXPR int y() const {return y1;}
    VSize           size() const {return {width(), height()};}
    void            setLeft(int l) { x1 = l; }
    void            setTop(int t) { y1 = t; }
    void            setRight(int r) { x2 = r; }
    void            setBottom(int b) { y2 = b; }
    void            setWidth(int w) { x2 = x1 + w; }
    void            setHeight(int h) { y2 = y1 + h; }
    VRect    translated(int dx, int dy) const;
    void     translate(int dx, int dy);
    bool     contains(const VRect &r, bool proper = false) const;
    bool     intersects(const VRect &r);
    friend V_CONSTEXPR inline bool operator==(const VRect &,
                                              const VRect &) noexcept;
    friend V_CONSTEXPR inline bool operator!=(const VRect &,
                                              const VRect &) noexcept;
    friend VDebug &                operator<<(VDebug &os, const VRect &o);

    VRect intersected(const VRect &r) const;
    VRect operator&(const VRect &r) const;

private:
    int x1{0};
    int y1{0};
    int x2{0};
    int y2{0};
};

inline VRect VRect::intersected(const VRect &r) const
{
    return *this & r;
}

inline bool VRect::intersects(const VRect &r)
{
    return (right() > r.left() && left() < r.right() && bottom() > r.top() &&
            top() < r.bottom());
}

inline VDebug &operator<<(VDebug &os, const VRect &o)
{
    os << "{R " << o.x() << "," << o.y() << "," << o.width() << ","
       << o.height() << "}";
    return os;
}
V_CONSTEXPR inline bool operator==(const VRect &r1, const VRect &r2) noexcept
{
    return r1.x1 == r2.x1 && r1.x2 == r2.x2 && r1.y1 == r2.y1 && r1.y2 == r2.y2;
}

V_CONSTEXPR inline bool operator!=(const VRect &r1, const VRect &r2) noexcept
{
    return r1.x1 != r2.x1 || r1.x2 != r2.x2 || r1.y1 != r2.y1 || r1.y2 != r2.y2;
}

inline VRect VRect::translated(int dx, int dy) const
{
    return {x1 + dx, y1 + dy, x2 - x1, y2 - y1};
}

inline void VRect::translate(int dx, int dy)
{
    x1 += dx;
    y1 += dy;
    x2 += dx;
    y2 += dy;
}

inline bool VRect::contains(const VRect &r, bool proper) const
{
    return proper ?
           ((x1 < r.x1) && (x2 > r.x2) && (y1 < r.y1) && (y2 > r.y2)) :
           ((x1 <= r.x1) && (x2 >= r.x2) && (y1 <= r.y1) && (y2 >= r.y2));
}

class VRectF {
public:
    VRectF() = default;

    VRectF(double x, double y, double w, double h):
        x1(float(x)),y1(float(y)),
        x2(float(x+w)),y2(float(y+h)){}
    operator VRect() const {
        return {int(left()), int(right()), int(width()), int(height())};
    }

    V_CONSTEXPR bool  empty() const {return x1 >= x2 || y1 >= y2;}
    V_CONSTEXPR float left() const {return x1;}
    V_CONSTEXPR float top() const {return y1;}
    V_CONSTEXPR float right() const {return x2;}
    V_CONSTEXPR float bottom() const {return y2;}
    V_CONSTEXPR float width() const {return x2-x1;}
    V_CONSTEXPR float height() const {return y2-y1;}
    V_CONSTEXPR float x() const {return x1;}
    V_CONSTEXPR float y() const {return y1;}
    V_CONSTEXPR inline VPointF center() const
    {
        return {x1 + (x2 - x1) / 2.f, y1 + (y2 - y1) / 2.f};
    }
    void setLeft(float l) { x1 = l; }
    void setTop(float t) { y1 = t; }
    void setRight(float r) { x2 = r; }
    void setBottom(float b) { y2 = b; }
    void setWidth(float w) { x2 = x1 + w; }
    void setHeight(float h) { y2 = y1 + h; }
    void translate(float dx, float dy)
    {
        x1 += dx;
        y1 += dy;
        x2 += dx;
        y2 += dy;
    }

private:
    float x1{0};
    float y1{0};
    float x2{0};
    float y2{0};
};

inline VRect::operator VRectF() const
{
       return {double(left()), double(right()), double(width()), double(height())};
}

V_END_NAMESPACE

#endif  // VRECT_H
