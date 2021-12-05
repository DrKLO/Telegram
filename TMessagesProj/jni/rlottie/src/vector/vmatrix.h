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

#ifndef VMATRIX_H
#define VMATRIX_H
#include "vglobal.h"
#include "vpoint.h"
#include "vregion.h"

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

    VMatrix &translate(VPointF pos) { return translate(pos.x(), pos.y()); };
    VMatrix &translate(float dx, float dy);
    VMatrix &scale(VPointF s) { return scale(s.x(), s.y()); };
    VMatrix &scale(float sx, float sy);
    VMatrix &shear(float sh, float sv);
    VMatrix &rotate(float a, Axis axis = VMatrix::Axis::Z);
    VMatrix &rotateRadians(float a, Axis axis = VMatrix::Axis::Z);

    VPointF        map(const VPointF &p) const;
    inline VPointF map(float x, float y) const;
    VRect          map(const VRect &r) const;
    VRegion        map(const VRegion &r) const;

    V_REQUIRED_RESULT VMatrix inverted(bool *invertible = nullptr) const;
    V_REQUIRED_RESULT VMatrix adjoint() const;

    VMatrix              operator*(const VMatrix &o) const;
    VMatrix &            operator*=(const VMatrix &);
    VMatrix &            operator*=(float mul);
    VMatrix &            operator/=(float div);
    bool                 operator==(const VMatrix &) const;
    bool                 operator!=(const VMatrix &) const;
    bool                 fuzzyCompare(const VMatrix &) const;
    friend std::ostream &operator<<(std::ostream &os, const VMatrix &o);

private:
    friend struct VSpanData;
    float              m11{1}, m12{0}, m13{0};
    float              m21{0}, m22{1}, m23{0};
    float              mtx{0}, mty{0}, m33{1};
    mutable MatrixType mType{MatrixType::None};
    mutable MatrixType dirty{MatrixType::None};
};

inline VPointF VMatrix::map(float x, float y) const
{
    return map(VPointF(x, y));
}

V_END_NAMESPACE

#endif  // VMATRIX_H
