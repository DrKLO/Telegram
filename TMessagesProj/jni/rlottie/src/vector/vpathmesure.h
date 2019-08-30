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

#ifndef VPATHMESURE_H
#define VPATHMESURE_H

#include "vpath.h"

V_BEGIN_NAMESPACE

class VPathMesure {
public:
    void  setStart(float start){mStart = start;}
    void  setEnd(float end){mEnd = end;}
    VPath trim(const VPath &path);
private:
    float mStart{0.0f};
    float mEnd{1.0f};
};

V_END_NAMESPACE

#endif  // VPATHMESURE_H
