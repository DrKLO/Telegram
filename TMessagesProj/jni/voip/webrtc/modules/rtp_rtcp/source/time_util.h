/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_RTP_RTCP_SOURCE_TIME_UTIL_H_
#define MODULES_RTP_RTCP_SOURCE_TIME_UTIL_H_

#include <stdint.h>

#include "system_wrappers/include/ntp_time.h"

namespace webrtc {

// Converts time obtained using rtc::TimeMicros to ntp format.
// TimeMicrosToNtp guarantees difference of the returned values matches
// difference of the passed values.
// As a result TimeMicrosToNtp(rtc::TimeMicros()) doesn't guarantee to match
// system time.
// However, TimeMicrosToNtp Guarantees that returned NtpTime will be offsetted
// from rtc::TimeMicros() by integral number of milliseconds.
// Use NtpOffsetMs() to get that offset value.
NtpTime TimeMicrosToNtp(int64_t time_us);

// Difference between Ntp time and local relative time returned by
// rtc::TimeMicros()
int64_t NtpOffsetMs();

// Helper function for compact ntp representation:
// RFC 3550, Section 4. Time Format.
// Wallclock time is represented using the timestamp format of
// the Network Time Protocol (NTP).
// ...
// In some fields where a more compact representation is
// appropriate, only the middle 32 bits are used; that is, the low 16
// bits of the integer part and the high 16 bits of the fractional part.
inline uint32_t CompactNtp(NtpTime ntp) {
  return (ntp.seconds() << 16) | (ntp.fractions() >> 16);
}

// Converts interval in microseconds to compact ntp (1/2^16 seconds) resolution.
// Negative values converted to 0, Overlarge values converted to max uint32_t.
uint32_t SaturatedUsToCompactNtp(int64_t us);

// Converts interval between compact ntp timestamps to milliseconds.
// This interval can be up to ~9.1 hours (2^15 seconds).
// Values close to 2^16 seconds consider negative and result in minimum rtt = 1.
int64_t CompactNtpRttToMs(uint32_t compact_ntp_interval);

}  // namespace webrtc
#endif  // MODULES_RTP_RTCP_SOURCE_TIME_UTIL_H_
