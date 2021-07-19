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
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301 USA
 */

#include "vpath.h"
#include <cassert>
#include <iterator>
#include <vector>
#include "vbezier.h"
#include "vdebug.h"
#include "vline.h"
#include "vrect.h"

V_BEGIN_NAMESPACE

void VPath::VPathData::transform(const VMatrix &m)
{
    for (auto &i : m_points) {
        i = m.map(i);
    }
    mLengthDirty = true;
}

float VPath::VPathData::length() const
{
    if (!mLengthDirty) return mLength;

    mLengthDirty = false;
    mLength = 0.0;

    size_t i = 0;
    for (auto e : m_elements) {
        switch (e) {
        case VPath::Element::MoveTo:
            i++;
            break;
        case VPath::Element::LineTo: {
            mLength += VLine(m_points[i - 1], m_points[i]).length();
            i++;
            break;
        }
        case VPath::Element::CubicTo: {
            mLength += VBezier::fromPoints(m_points[i - 1], m_points[i],
                                           m_points[i + 1], m_points[i + 2])
                           .length();
            i += 3;
            break;
        }
        case VPath::Element::Close:
            break;
        }
    }

    return mLength;
}

void VPath::VPathData::checkNewSegment()
{
    if (mNewSegment) {
        moveTo(0, 0);
        mNewSegment = false;
    }
}

void VPath::VPathData::moveTo(float x, float y)
{
    mStartPoint = {x, y};
    mNewSegment = false;
    m_elements.emplace_back(VPath::Element::MoveTo);
    m_points.emplace_back(x, y);
    m_segments++;
    mLengthDirty = true;
}

void VPath::VPathData::lineTo(float x, float y)
{
    checkNewSegment();
    m_elements.emplace_back(VPath::Element::LineTo);
    m_points.emplace_back(x, y);
    mLengthDirty = true;
}

void VPath::VPathData::cubicTo(float cx1, float cy1, float cx2, float cy2,
                               float ex, float ey)
{
    checkNewSegment();
    m_elements.emplace_back(VPath::Element::CubicTo);
    m_points.emplace_back(cx1, cy1);
    m_points.emplace_back(cx2, cy2);
    m_points.emplace_back(ex, ey);
    mLengthDirty = true;
}

void VPath::VPathData::close()
{
    if (empty()) return;

    const VPointF &lastPt = m_points.back();
    if (!fuzzyCompare(mStartPoint, lastPt)) {
        lineTo(mStartPoint.x(), mStartPoint.y());
    }
    m_elements.push_back(VPath::Element::Close);
    mNewSegment = true;
    mLengthDirty = true;
}

void VPath::VPathData::reset()
{
    if (empty()) return;

    m_elements.clear();
    m_points.clear();
    m_segments = 0;
    mLength = 0;
    mLengthDirty = false;
}

size_t VPath::VPathData::segments() const
{
    return m_segments;
}

void VPath::VPathData::reserve(size_t pts, size_t elms)
{
    if (m_points.capacity() < m_points.size() + pts)
        m_points.reserve(m_points.size() + pts);
    if (m_elements.capacity() < m_elements.size() + elms)
        m_elements.reserve(m_elements.size() + elms);
}

static VPointF curvesForArc(const VRectF &, float, float, VPointF *, size_t *);
static constexpr float PATH_KAPPA = 0.5522847498f;
static constexpr float K_PI = float(M_PI);

void VPath::VPathData::arcTo(const VRectF &rect, float startAngle,
                             float sweepLength, bool forceMoveTo)
{
    size_t  point_count = 0;
    VPointF pts[15];
    VPointF curve_start =
        curvesForArc(rect, startAngle, sweepLength, pts, &point_count);

    reserve(point_count + 1, point_count / 3 + 1);
    if (empty() || forceMoveTo) {
        moveTo(curve_start.x(), curve_start.y());
    } else {
        lineTo(curve_start.x(), curve_start.y());
    }
    for (size_t i = 0; i < point_count; i += 3) {
        cubicTo(pts[i].x(), pts[i].y(), pts[i + 1].x(), pts[i + 1].y(),
                pts[i + 2].x(), pts[i + 2].y());
    }
}

void VPath::VPathData::addCircle(float cx, float cy, float radius,
                                 VPath::Direction dir)
{
    addOval(VRectF(cx - radius, cy - radius, 2 * radius, 2 * radius), dir);
}

void VPath::VPathData::addOval(const VRectF &rect, VPath::Direction dir)
{
    if (rect.empty()) return;

    float x = rect.x();
    float y = rect.y();

    float w = rect.width();
    float w2 = rect.width() / 2;
    float w2k = w2 * PATH_KAPPA;

    float h = rect.height();
    float h2 = rect.height() / 2;
    float h2k = h2 * PATH_KAPPA;

    reserve(13, 6);  // 1Move + 4Cubic + 1Close
    if (dir == VPath::Direction::CW) {
        // moveto 12 o'clock.
        moveTo(x + w2, y);
        // 12 -> 3 o'clock
        cubicTo(x + w2 + w2k, y, x + w, y + h2 - h2k, x + w, y + h2);
        // 3 -> 6 o'clock
        cubicTo(x + w, y + h2 + h2k, x + w2 + w2k, y + h, x + w2, y + h);
        // 6 -> 9 o'clock
        cubicTo(x + w2 - w2k, y + h, x, y + h2 + h2k, x, y + h2);
        // 9 -> 12 o'clock
        cubicTo(x, y + h2 - h2k, x + w2 - w2k, y, x + w2, y);
    } else {
        // moveto 12 o'clock.
        moveTo(x + w2, y);
        // 12 -> 9 o'clock
        cubicTo(x + w2 - w2k, y, x, y + h2 - h2k, x, y + h2);
        // 9 -> 6 o'clock
        cubicTo(x, y + h2 + h2k, x + w2 - w2k, y + h, x + w2, y + h);
        // 6 -> 3 o'clock
        cubicTo(x + w2 + w2k, y + h, x + w, y + h2 + h2k, x + w, y + h2);
        // 3 -> 12 o'clock
        cubicTo(x + w, y + h2 - h2k, x + w2 + w2k, y, x + w2, y);
    }
    close();
}

void VPath::VPathData::addRect(const VRectF &rect, VPath::Direction dir)
{
    if (rect.empty()) return;

    float x = rect.x();
    float y = rect.y();
    float w = rect.width();
    float h = rect.height();

    reserve(5, 6);  // 1Move + 4Line + 1Close
    if (dir == VPath::Direction::CW) {
        moveTo(x + w, y);
        lineTo(x + w, y + h);
        lineTo(x, y + h);
        lineTo(x, y);
        close();
    } else {
        moveTo(x + w, y);
        lineTo(x, y);
        lineTo(x, y + h);
        lineTo(x + w, y + h);
        close();
    }
}

void VPath::VPathData::addRoundRect(const VRectF &rect, float roundness,
                                    VPath::Direction dir)
{
    if (2 * roundness > rect.width()) roundness = rect.width() / 2.0f;
    if (2 * roundness > rect.height()) roundness = rect.height() / 2.0f;
    addRoundRect(rect, roundness, roundness, dir);
}

void VPath::VPathData::addRoundRect(const VRectF &rect, float rx, float ry,
                                    VPath::Direction dir)
{
    if (vCompare(rx, 0.f) || vCompare(ry, 0.f)) {
        addRect(rect, dir);
        return;
    }

    float x = rect.x();
    float y = rect.y();
    float w = rect.width();
    float h = rect.height();
    // clamp the rx and ry radius value.
    rx = 2 * rx;
    ry = 2 * ry;
    if (rx > w) rx = w;
    if (ry > h) ry = h;

    reserve(17, 10);  // 1Move + 4Cubic + 1Close
    if (dir == VPath::Direction::CW) {
        moveTo(x + w, y + ry / 2.f);
        arcTo(VRectF(x + w - rx, y + h - ry, rx, ry), 0, -90, false);
        arcTo(VRectF(x, y + h - ry, rx, ry), -90, -90, false);
        arcTo(VRectF(x, y, rx, ry), -180, -90, false);
        arcTo(VRectF(x + w - rx, y, rx, ry), -270, -90, false);
        close();
    } else {
        moveTo(x + w, y + ry / 2.f);
        arcTo(VRectF(x + w - rx, y, rx, ry), 0, 90, false);
        arcTo(VRectF(x, y, rx, ry), 90, 90, false);
        arcTo(VRectF(x, y + h - ry, rx, ry), 180, 90, false);
        arcTo(VRectF(x + w - rx, y + h - ry, rx, ry), 270, 90, false);
        close();
    }
}

static float tForArcAngle(float angle);
void         findEllipseCoords(const VRectF &r, float angle, float length,
                               VPointF *startPoint, VPointF *endPoint)
{
    if (r.empty()) {
        if (startPoint) *startPoint = VPointF();
        if (endPoint) *endPoint = VPointF();
        return;
    }

    float w2 = r.width() / 2;
    float h2 = r.height() / 2;

    float    angles[2] = {angle, angle + length};
    VPointF *points[2] = {startPoint, endPoint};

    for (int i = 0; i < 2; ++i) {
        if (!points[i]) continue;

        float theta = angles[i] - 360 * floorf(angles[i] / 360);
        float t = theta / 90;
        // truncate
        int quadrant = int(t);
        t -= quadrant;

        t = tForArcAngle(90 * t);

        // swap x and y?
        if (quadrant & 1) t = 1 - t;

        float a, b, c, d;
        VBezier::coefficients(t, a, b, c, d);
        VPointF p(a + b + c * PATH_KAPPA, d + c + b * PATH_KAPPA);

        // left quadrants
        if (quadrant == 1 || quadrant == 2) p.rx() = -p.x();

        // top quadrants
        if (quadrant == 0 || quadrant == 1) p.ry() = -p.y();

        *points[i] = r.center() + VPointF(w2 * p.x(), h2 * p.y());
    }
}

static float tForArcAngle(float angle)
{
    float radians, cos_angle, sin_angle, tc, ts, t;

    if (vCompare(angle, 0.f)) return 0;
    if (vCompare(angle, 90.0f)) return 1;

    radians = (angle / 180) * K_PI;

    cos_angle = cosf(radians);
    sin_angle = sinf(radians);

    // initial guess
    tc = angle / 90;

    // do some iterations of newton's method to approximate cos_angle
    // finds the zero of the function b.pointAt(tc).x() - cos_angle
    tc -= ((((2 - 3 * PATH_KAPPA) * tc + 3 * (PATH_KAPPA - 1)) * tc) * tc + 1 -
           cos_angle)  // value
          / (((6 - 9 * PATH_KAPPA) * tc + 6 * (PATH_KAPPA - 1)) *
             tc);  // derivative
    tc -= ((((2 - 3 * PATH_KAPPA) * tc + 3 * (PATH_KAPPA - 1)) * tc) * tc + 1 -
           cos_angle)  // value
          / (((6 - 9 * PATH_KAPPA) * tc + 6 * (PATH_KAPPA - 1)) *
             tc);  // derivative

    // initial guess
    ts = tc;
    // do some iterations of newton's method to approximate sin_angle
    // finds the zero of the function b.pointAt(tc).y() - sin_angle
    ts -= ((((3 * PATH_KAPPA - 2) * ts - 6 * PATH_KAPPA + 3) * ts +
            3 * PATH_KAPPA) *
               ts -
           sin_angle) /
          (((9 * PATH_KAPPA - 6) * ts + 12 * PATH_KAPPA - 6) * ts +
           3 * PATH_KAPPA);
    ts -= ((((3 * PATH_KAPPA - 2) * ts - 6 * PATH_KAPPA + 3) * ts +
            3 * PATH_KAPPA) *
               ts -
           sin_angle) /
          (((9 * PATH_KAPPA - 6) * ts + 12 * PATH_KAPPA - 6) * ts +
           3 * PATH_KAPPA);

    // use the average of the t that best approximates cos_angle
    // and the t that best approximates sin_angle
    t = 0.5f * (tc + ts);
    return t;
}

// The return value is the starting point of the arc
static VPointF curvesForArc(const VRectF &rect, float startAngle,
                            float sweepLength, VPointF *curves,
                            size_t *point_count)
{
    if (rect.empty()) {
        return {};
    }

    float x = rect.x();
    float y = rect.y();

    float w = rect.width();
    float w2 = rect.width() / 2;
    float w2k = w2 * PATH_KAPPA;

    float h = rect.height();
    float h2 = rect.height() / 2;
    float h2k = h2 * PATH_KAPPA;

    VPointF points[16] = {
        // start point
        VPointF(x + w, y + h2),

        // 0 -> 270 degrees
        VPointF(x + w, y + h2 + h2k), VPointF(x + w2 + w2k, y + h),
        VPointF(x + w2, y + h),

        // 270 -> 180 degrees
        VPointF(x + w2 - w2k, y + h), VPointF(x, y + h2 + h2k),
        VPointF(x, y + h2),

        // 180 -> 90 degrees
        VPointF(x, y + h2 - h2k), VPointF(x + w2 - w2k, y), VPointF(x + w2, y),

        // 90 -> 0 degrees
        VPointF(x + w2 + w2k, y), VPointF(x + w, y + h2 - h2k),
        VPointF(x + w, y + h2)};

    if (sweepLength > 360)
        sweepLength = 360;
    else if (sweepLength < -360)
        sweepLength = -360;

    // Special case fast paths
    if (startAngle == 0.0f) {
        if (vCompare(sweepLength, 360)) {
            for (int i = 11; i >= 0; --i) curves[(*point_count)++] = points[i];
            return points[12];
        } else if (vCompare(sweepLength, -360)) {
            for (int i = 1; i <= 12; ++i) curves[(*point_count)++] = points[i];
            return points[0];
        }
    }

    int startSegment = int(floorf(startAngle / 90.0f));
    int endSegment = int(floorf((startAngle + sweepLength) / 90.0f));

    float startT = (startAngle - startSegment * 90) / 90;
    float endT = (startAngle + sweepLength - endSegment * 90) / 90;

    int delta = sweepLength > 0 ? 1 : -1;
    if (delta < 0) {
        startT = 1 - startT;
        endT = 1 - endT;
    }

    // avoid empty start segment
    if (vIsZero(startT - float(1))) {
        startT = 0;
        startSegment += delta;
    }

    // avoid empty end segment
    if (vIsZero(endT)) {
        endT = 1;
        endSegment -= delta;
    }

    startT = tForArcAngle(startT * 90);
    endT = tForArcAngle(endT * 90);

    const bool splitAtStart = !vIsZero(startT);
    const bool splitAtEnd = !vIsZero(endT - float(1));

    const int end = endSegment + delta;

    // empty arc?
    if (startSegment == end) {
        const int quadrant = 3 - ((startSegment % 4) + 4) % 4;
        const int j = 3 * quadrant;
        return delta > 0 ? points[j + 3] : points[j];
    }

    VPointF startPoint, endPoint;
    findEllipseCoords(rect, startAngle, sweepLength, &startPoint, &endPoint);

    for (int i = startSegment; i != end; i += delta) {
        const int quadrant = 3 - ((i % 4) + 4) % 4;
        const int j = 3 * quadrant;

        VBezier b;
        if (delta > 0)
            b = VBezier::fromPoints(points[j + 3], points[j + 2], points[j + 1],
                                    points[j]);
        else
            b = VBezier::fromPoints(points[j], points[j + 1], points[j + 2],
                                    points[j + 3]);

        // empty arc?
        if (startSegment == endSegment && vCompare(startT, endT))
            return startPoint;

        if (i == startSegment) {
            if (i == endSegment && splitAtEnd)
                b = b.onInterval(startT, endT);
            else if (splitAtStart)
                b = b.onInterval(startT, 1);
        } else if (i == endSegment && splitAtEnd) {
            b = b.onInterval(0, endT);
        }

        // push control points
        curves[(*point_count)++] = b.pt2();
        curves[(*point_count)++] = b.pt3();
        curves[(*point_count)++] = b.pt4();
    }

    curves[*(point_count)-1] = endPoint;

    return startPoint;
}

void VPath::VPathData::addPolystar(float points, float innerRadius,
                                   float outerRadius, float innerRoundness,
                                   float outerRoundness, float startAngle,
                                   float cx, float cy, VPath::Direction dir)
{
    const static float POLYSTAR_MAGIC_NUMBER = 0.47829f / 0.28f;
    float              currentAngle = (startAngle - 90.0f) * K_PI / 180.0f;
    float              x;
    float              y;
    float              partialPointRadius = 0;
    float              anglePerPoint = (2.0f * K_PI / points);
    float              halfAnglePerPoint = anglePerPoint / 2.0f;
    float              partialPointAmount = points - floorf(points);
    bool               longSegment = false;
    size_t             numPoints = size_t(ceilf(points) * 2);
    float              angleDir = ((dir == VPath::Direction::CW) ? 1.0 : -1.0);
    bool               hasRoundness = false;

    innerRoundness /= 100.0f;
    outerRoundness /= 100.0f;

    if (!vCompare(partialPointAmount, 0)) {
        currentAngle +=
            halfAnglePerPoint * (1.0f - partialPointAmount) * angleDir;
    }

    if (!vCompare(partialPointAmount, 0)) {
        partialPointRadius =
            innerRadius + partialPointAmount * (outerRadius - innerRadius);
        x = partialPointRadius * cosf(currentAngle);
        y = partialPointRadius * sinf(currentAngle);
        currentAngle += anglePerPoint * partialPointAmount / 2.0f * angleDir;
    } else {
        x = outerRadius * cosf(currentAngle);
        y = outerRadius * sinf(currentAngle);
        currentAngle += halfAnglePerPoint * angleDir;
    }

    if (vIsZero(innerRoundness) && vIsZero(outerRoundness)) {
        reserve(numPoints + 2, numPoints + 3);
    } else {
        reserve(numPoints * 3 + 2, numPoints + 3);
        hasRoundness = true;
    }

    moveTo(x + cx, y + cy);

    for (size_t i = 0; i < numPoints; i++) {
        float radius = longSegment ? outerRadius : innerRadius;
        float dTheta = halfAnglePerPoint;
        if (!vIsZero(partialPointRadius) && i == numPoints - 2) {
            dTheta = anglePerPoint * partialPointAmount / 2.0f;
        }
        if (!vIsZero(partialPointRadius) && i == numPoints - 1) {
            radius = partialPointRadius;
        }
        float previousX = x;
        float previousY = y;
        x = radius * cosf(currentAngle);
        y = radius * sinf(currentAngle);

        if (hasRoundness) {
            float cp1Theta =
                (atan2f(previousY, previousX) - K_PI / 2.0f * angleDir);
            float cp1Dx = cosf(cp1Theta);
            float cp1Dy = sinf(cp1Theta);
            float cp2Theta = (atan2f(y, x) - K_PI / 2.0f * angleDir);
            float cp2Dx = cosf(cp2Theta);
            float cp2Dy = sinf(cp2Theta);

            float cp1Roundness = longSegment ? innerRoundness : outerRoundness;
            float cp2Roundness = longSegment ? outerRoundness : innerRoundness;
            float cp1Radius = longSegment ? innerRadius : outerRadius;
            float cp2Radius = longSegment ? outerRadius : innerRadius;

            float cp1x = cp1Radius * cp1Roundness * POLYSTAR_MAGIC_NUMBER *
                         cp1Dx / points;
            float cp1y = cp1Radius * cp1Roundness * POLYSTAR_MAGIC_NUMBER *
                         cp1Dy / points;
            float cp2x = cp2Radius * cp2Roundness * POLYSTAR_MAGIC_NUMBER *
                         cp2Dx / points;
            float cp2y = cp2Radius * cp2Roundness * POLYSTAR_MAGIC_NUMBER *
                         cp2Dy / points;

            if (!vIsZero(partialPointAmount) &&
                ((i == 0) || (i == numPoints - 1))) {
                cp1x *= partialPointAmount;
                cp1y *= partialPointAmount;
                cp2x *= partialPointAmount;
                cp2y *= partialPointAmount;
            }

            cubicTo(previousX - cp1x + cx, previousY - cp1y + cy, x + cp2x + cx,
                    y + cp2y + cy, x + cx, y + cy);
        } else {
            lineTo(x + cx, y + cy);
        }

        currentAngle += dTheta * angleDir;
        longSegment = !longSegment;
    }

    close();
}

void VPath::VPathData::addPolygon(float points, float radius, float roundness,
                                  float startAngle, float cx, float cy,
                                  VPath::Direction dir)
{
    // TODO: Need to support floating point number for number of points
    const static float POLYGON_MAGIC_NUMBER = 0.25;
    float              currentAngle = (startAngle - 90.0f) * K_PI / 180.0f;
    float              x;
    float              y;
    float              anglePerPoint = 2.0f * K_PI / floorf(points);
    size_t             numPoints = size_t(floorf(points));
    float              angleDir = ((dir == VPath::Direction::CW) ? 1.0 : -1.0);
    bool               hasRoundness = false;

    roundness /= 100.0f;

    currentAngle = (currentAngle - 90.0f) * K_PI / 180.0f;
    x = radius * cosf(currentAngle);
    y = radius * sinf(currentAngle);
    currentAngle += anglePerPoint * angleDir;

    if (vIsZero(roundness)) {
        reserve(numPoints + 2, numPoints + 3);
    } else {
        reserve(numPoints * 3 + 2, numPoints + 3);
        hasRoundness = true;
    }

    moveTo(x + cx, y + cy);

    for (size_t i = 0; i < numPoints; i++) {
        float previousX = x;
        float previousY = y;
        x = (radius * cosf(currentAngle));
        y = (radius * sinf(currentAngle));

        if (hasRoundness) {
            float cp1Theta =
                (atan2f(previousY, previousX) - K_PI / 2.0f * angleDir);
            float cp1Dx = cosf(cp1Theta);
            float cp1Dy = sinf(cp1Theta);
            float cp2Theta = atan2f(y, x) - K_PI / 2.0f * angleDir;
            float cp2Dx = cosf(cp2Theta);
            float cp2Dy = sinf(cp2Theta);

            float cp1x = radius * roundness * POLYGON_MAGIC_NUMBER * cp1Dx;
            float cp1y = radius * roundness * POLYGON_MAGIC_NUMBER * cp1Dy;
            float cp2x = radius * roundness * POLYGON_MAGIC_NUMBER * cp2Dx;
            float cp2y = radius * roundness * POLYGON_MAGIC_NUMBER * cp2Dy;

            cubicTo(previousX - cp1x + cx, previousY - cp1y + cy, x + cp2x + cx,
                    y + cp2y + cy, x, y);
        } else {
            lineTo(x + cx, y + cy);
        }

        currentAngle += anglePerPoint * angleDir;
    }

    close();
}

void VPath::VPathData::addPath(const VPathData &path)
{
    size_t segment = path.segments();

    // make sure enough memory available
    if (m_points.capacity() < m_points.size() + path.m_points.size())
        m_points.reserve(m_points.size() + path.m_points.size());

    if (m_elements.capacity() < m_elements.size() + path.m_elements.size())
        m_elements.reserve(m_elements.size() + path.m_elements.size());

    std::copy(path.m_points.begin(), path.m_points.end(),
              back_inserter(m_points));
    std::copy(path.m_elements.begin(), path.m_elements.end(),
              back_inserter(m_elements));

    m_segments += segment;
    mLengthDirty = true;
}

V_END_NAMESPACE
