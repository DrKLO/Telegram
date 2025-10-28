/*
 *  Copyright 2023 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NET_TEST_HELPERS_H_
#define RTC_BASE_NET_TEST_HELPERS_H_

#include "rtc_base/system/rtc_export.h"

namespace rtc {

RTC_EXPORT bool HasIPv4Enabled();
RTC_EXPORT bool HasIPv6Enabled();

}  // namespace rtc

#endif  // RTC_BASE_NET_TEST_HELPERS_H_
