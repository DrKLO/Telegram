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

#ifndef VMATRIX_H
#define VMATRIX_H
#include "vglobal.h"
#include "vpoint.h"
#include "vrect.h"

V_BEGIN_NAMESPACE

struct VMatrixData;
class VMatrix {
public:
    enum class Axis { X, Y, Z };
    enum class MatrixType: unsigned char {
        None = 0x00,
        Translate = 0x01,
        Scale = 0x02,
        Rotate = 0x04,
        Shear = 0x08,
        Project = 0x10
    };
    VMatrix() = default;
    bool         isAffine() const;
    bool         isIdentity() const;
    bool         isInvertible() const;
    bool         isScaling() const;
    bool         isRotating() const;
    bool         isTranslating() const;
    MatrixType   type() const;
    inline float determinant() const;

    float        m_11() const { return m11;}
    float        m_12() const { return m12;}
    float        m_13() const { return m13;}

    float        m_21() const { return m21;}
    float        m_22() const { return m22;}
    float        m_23() const { return m23;}

    float        m_tx() const { return mtx;}
    float        m_ty() const { return mty;}
    float        m_33() const { return m33;}

    VMatrix &translate(VPointF pos) { return translate(pos.x(), pos.y()); }
    VMatrix &translate(float dx, float dy);
    VMatrix &scale(VPointF s) { return scale(s.x(), s.y()); }
    VMatrix &scale(float sx, float sy);
    VMatrix &shear(float sh, float sv);
    VMatrix &rotate(float a, Axis axis = VMatrix::Axis::Z);
    VMatrix &rotateRadians(float a, Axis axis = VMatrix::Axis::Z);

    VPointF        map(const VPointF &p) const;
    inline VPointF map(float x, float y) const;
    VRect          map(const VRect &r) const;

    V_REQUIRED_RESULT VMatrix inverted(bool *invertible = nullptr) const;
    V_REQUIRED_RESULT VMatrix adjoint() const;

    VMatrix              operator*(const VMatrix &o) const;
    VMatrix &            operator*=(const VMatrix &);
    VMatrix &            operator*=(float mul);
    VMatrix &            operator/=(float div);
    bool                 operator==(const VMatrix &) const;
    bool                 operator!=(const VMatrix &) const;
    bool                 fuzzyCompare(const VMatrix &) const;
    float                scale() const;
private:
    friend struct VSpanData;
    float              m11{1}, m12{0}, m13{0};
    float              m21{0}, m22{1}, m23{0};
    float              mtx{0}, mty{0}, m33{1};
    mutable MatrixType mType{MatrixType::None};
    mutable MatrixType dirty{MatrixType::None};
};

inline float VMatrix::scale() const
{
    constexpr float SQRT_2 = 1.41421f;
    VPointF         p1(0, 0);
    VPointF         p2(SQRT_2, SQRT_2);
    p1 = map(p1);
    p2 = map(p2);
    VPointF final = p2 - p1;

    return std::sqrt(final.x() * final.x() + final.y() * final.y()) / 2.0f;
}

inline VPointF VMatrix::map(float x, float y) const
{
    return map(VPointF(x, y));
}

V_END_NAMESPACE

#endif  // VMATRIX_H
