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

#ifndef VPOINT_H
#define VPOINT_H

#include "vglobal.h"

V_BEGIN_NAMESPACE

class VPointF {
public:
    VPointF() = default;
    constexpr inline VPointF(float x, float y) noexcept : mx(x), my(y) {}
    constexpr inline float x() const noexcept { return mx; }
    constexpr inline float y() const noexcept { return my; }
    inline float &         rx() noexcept { return mx; }
    inline float &         ry() noexcept { return my; }
    inline void            setX(float x) { mx = x; }
    inline void            setY(float y) { my = y; }
    inline VPointF         operator-() noexcept { return {-mx, -my}; }
    inline VPointF &       operator+=(const VPointF &p) noexcept;
    inline VPointF &       operator-=(const VPointF &p) noexcept;
    friend const VPointF   operator+(const VPointF &p1, const VPointF &p2)
    {
        return VPointF(p1.mx + p2.mx, p1.my + p2.my);
    }
    inline friend bool fuzzyCompare(const VPointF &p1, const VPointF &p2);
    inline friend VDebug &   operator<<(VDebug &os, const VPointF &o);

    friend inline VPointF       operator-(const VPointF &p1, const VPointF &p2);
    friend inline const VPointF operator*(const VPointF &, float val);
    friend inline const VPointF operator*(float val, const VPointF &);
    friend inline const VPointF operator/(const VPointF &, float val);
    friend inline const VPointF operator/(float val, const VPointF &);

private:
    float mx{0};
    float my{0};
};

inline bool fuzzyCompare(const VPointF &p1, const VPointF &p2)
{
    return (vCompare(p1.mx, p2.mx) && vCompare(p1.my, p2.my));
}

inline VPointF operator-(const VPointF &p1, const VPointF &p2)
{
    return {p1.mx - p2.mx, p1.my - p2.my};
}

inline const VPointF operator*(const VPointF &p, float c)
{
    return VPointF(p.mx * c, p.my * c);
}

inline const VPointF operator*(float c, const VPointF &p)
{
    return VPointF(p.mx * c, p.my * c);
}

inline const VPointF operator/(const VPointF &p, float c)
{
    return VPointF(p.mx / c, p.my / c);
}

inline const VPointF operator/(float c, const VPointF &p)
{
    return VPointF(p.mx / c, p.my / c);
}

inline VDebug &operator<<(VDebug &os, const VPointF &o)
{
    os << "{P " << o.x() << "," << o.y() << "}";
    return os;
}

inline VPointF &VPointF::operator+=(const VPointF &p) noexcept
{
    mx += p.mx;
    my += p.my;
    return *this;
}

inline VPointF &VPointF::operator-=(const VPointF &p) noexcept
{
    mx -= p.mx;
    my -= p.my;
    return *this;
}

class VPoint {
public:
    VPoint() = default;
    constexpr inline VPoint(int x, int y) noexcept : mx(x), my(y) {}
    constexpr inline int  x() const noexcept { return mx; }
    constexpr inline int  y() const noexcept { return my; }
    inline void           setX(int x) { mx = x; }
    inline void           setY(int y) { my = y; }
    inline VPoint &       operator+=(const VPoint &p) noexcept;
    inline VPoint &       operator-=(const VPoint &p) noexcept;
    constexpr inline bool operator==(const VPoint &o) const;
    constexpr inline bool operator!=(const VPoint &o) const
    {
        return !(operator==(o));
    }
    friend inline VPoint  operator-(const VPoint &p1, const VPoint &p2);
    inline friend VDebug &operator<<(VDebug &os, const VPoint &o);

private:
    int mx{0};
    int my{0};
};
inline VDebug &operator<<(VDebug &os, const VPoint &o)
{
    os << "{P " << o.x() << "," << o.y() << "}";
    return os;
}

inline VPoint operator-(const VPoint &p1, const VPoint &p2)
{
    return {p1.mx - p2.mx, p1.my - p2.my};
}

constexpr inline bool VPoint::operator==(const VPoint &o) const
{
    return (mx == o.x() && my == o.y());
}

inline VPoint &VPoint::operator+=(const VPoint &p) noexcept
{
    mx += p.mx;
    my += p.my;
    return *this;
}

inline VPoint &VPoint::operator-=(const VPoint &p) noexcept
{
    mx -= p.mx;
    my -= p.my;
    return *this;
}

class VSize {
public:
    VSize() = default;
    constexpr inline VSize(int w, int h) noexcept : mw(w), mh(h) {}
    bool empty() const {return (mw <= 0 || mh <= 0);}
    constexpr inline int  width() const noexcept { return mw; }
    constexpr inline int  height() const noexcept { return mh; }
    inline void           setWidth(int w) { mw = w; }
    inline void           setHeight(int h) { mh = h; }
    inline VSize &        operator+=(const VSize &p) noexcept;
    inline VSize &        operator-=(const VSize &p) noexcept;
    constexpr inline bool operator==(const VSize &o) const;
    constexpr inline bool operator!=(const VSize &o) const
    {
        return !(operator==(o));
    }
    inline friend VDebug &operator<<(VDebug &os, const VSize &o);

private:
    int mw{0};
    int mh{0};
};
inline VDebug &operator<<(VDebug &os, const VSize &o)
{
    os << "{P " << o.width() << "," << o.height() << "}";
    return os;
}
constexpr inline bool VSize::operator==(const VSize &o) const
{
    return (mw == o.width() && mh == o.height());
}

inline VSize &VSize::operator+=(const VSize &p) noexcept
{
    mw += p.mw;
    mh += p.mh;
    return *this;
}

inline VSize &VSize::operator-=(const VSize &p) noexcept
{
    mw -= p.mw;
    mh -= p.mh;
    return *this;
}

V_END_NAMESPACE

#endif  // VPOINT_H
