/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef RTC_BASE_LOGGING_MAC_H_
#define RTC_BASE_LOGGING_MAC_H_

#if !defined(WEBRTC_MAC) || defined(WEBRTC_IOS)
#error "Only include this header in macOS builds"
#endif

#include <CoreServices/CoreServices.h>

#include <string>

namespace rtc {

// Returns a UTF8 description from an OS X Status error.
std::string DescriptionFromOSStatus(OSStatus err);

}  // namespace rtc

#endif  // RTC_BASE_LOGGING_MAC_H_
