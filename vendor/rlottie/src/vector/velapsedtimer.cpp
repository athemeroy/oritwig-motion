/*
 * Copyright (c) 2018 Samsung Electronics Co., Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301 USA
 */

/* Oritwig distribution notice, 2026-10-01.
 * GNU license references above were converted to GPLv3 under
 * LGPL-2.1 section 3. Copyrights and algorithm bodies are unchanged.
 * See NOTICE.txt and provenance/license-only.patch.
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
