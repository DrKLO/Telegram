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

#include "velapsedtimer.h"

void VElapsedTimer::start()
{
    clock = std::chrono::high_resolution_clock::now();
    m_valid = true;
}

double VElapsedTimer::restart()
{
    double elapsedTime = elapsed();
    start();
    return elapsedTime;
}

double VElapsedTimer::elapsed() const
{
    if (!isValid()) return 0;
    return std::chrono::duration<double, std::milli>(
               std::chrono::high_resolution_clock::now() - clock)
        .count();
}

bool VElapsedTimer::hasExpired(double time)
{
    double elapsedTime = elapsed();
    if (elapsedTime > time) return true;
    return false;
}
