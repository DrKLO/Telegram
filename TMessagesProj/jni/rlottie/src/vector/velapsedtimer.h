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

#ifndef VELAPSEDTIMER_H
#define VELAPSEDTIMER_H

#include <chrono>
#include "vglobal.h"

class VElapsedTimer {
public:
    double      elapsed() const;
    bool        hasExpired(double millsec);
    void        start();
    double      restart();
    inline bool isValid() const { return m_valid; }

private:
    std::chrono::high_resolution_clock::time_point clock;
    bool                                           m_valid{false};
};
#endif  // VELAPSEDTIMER_H
