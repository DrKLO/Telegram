/* 
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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
    explicit VRect(const VRectF &r);
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
    if (!proper) {
        if ((x1 <= r.x1) && (x2 >= r.x2) && (y1 <= r.y1) && (y2 >= r.y2))
            return true;
        return false;
    } else {
        if ((x1 < r.x1) && (x2 > r.x2) && (y1 < r.y1) && (y2 > r.y2))
            return true;
        return false;
    }
}

class VRectF {
public:
    VRectF() = default;
    VRectF(float x, float y, float w, float h):x1(x),y1(y),x2(x+w),y2(y+h){}
    explicit VRectF(const VRect &r):x1(r.left()),y1(r.top()),
                                    x2(r.right()),y2(r.bottom()){}

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

inline VRect::VRect(const VRectF &r):x1(r.left()),y1(r.top()),
                                     x2(r.right()),y2(r.bottom()){}
V_END_NAMESPACE

#endif  // VRECT_H
