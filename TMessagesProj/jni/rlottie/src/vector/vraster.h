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

#ifndef VRASTER_H
#define VRASTER_H
#include <future>
#include "vglobal.h"
#include "vrect.h"

V_BEGIN_NAMESPACE

class VPath;
class VRle;

class VRasterizer
{
public:
    void rasterize(VPath path, FillRule fillRule = FillRule::Winding, const VRect &clip = VRect());
    void rasterize(VPath path, CapStyle cap, JoinStyle join, float width,
                   float meterLimit, const VRect &clip = VRect());
    VRle rle();
private:
    struct VRasterizerImpl;
    void init();
    void updateRequest();
    std::shared_ptr<VRasterizerImpl> d{nullptr};
};

V_END_NAMESPACE

#endif  // VRASTER_H
