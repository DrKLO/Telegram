/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/audio_codecs/audio_codec_pair_id.h"

#include <atomic>
#include <cstdint>

#include "rtc_base/checks.h"

namespace webrtc {

namespace {

// Returns a new value that it has never returned before. You may call it at
// most 2^63 times in the lifetime of the program. Note: The returned values
// may be easily predictable.
uint64_t GetNextId() {
  static std::atomic<uint64_t> next_id(0);

  // Atomically increment `next_id`, and return the previous value. Relaxed
  // memory order is sufficient, since all we care about is that different
  // callers return different values.
  const uint64_t new_id = next_id.fetch_add(1, std::memory_order_relaxed);

  // This check isn't atomic with the increment, so if we start 2^63 + 1
  // invocations of GetNextId() in parallel, the last one to do the atomic
  // increment could return the ID 0 before any of the others had time to
  // trigger this DCHECK. We blithely assume that this won't happen.
  RTC_DCHECK_LT(new_id, uint64_t{1} << 63) << "Used up all ID values";

  return new_id;
}

// Make an integer ID more unpredictable. This is a 1:1 mapping, so you can
// feed it any value, but the idea is that you can feed it a sequence such as
// 0, 1, 2, ... and get a new sequence that isn't as trivially predictable, so
// that users won't rely on it being consecutive or increasing or anything like
// that.
constexpr uint64_t ObfuscateId(uint64_t id) {
  // Any nonzero coefficient that's relatively prime to 2^64 (that is, any odd
  // number) and any constant will give a 1:1 mapping. These high-entropy
  // values will prevent the sequence from being trivially predictable.
  //
  // Both the multiplication and the addition going to overflow almost always,
  // but that's fine---we *want* arithmetic mod 2^64.
  return uint64_t{0x85fdb20e1294309a} + uint64_t{0xc516ef5c37462469} * id;
}

// The first ten values. Verified against the Python function
//
//   def f(n):
//     return (0x85fdb20e1294309a + 0xc516ef5c37462469 * n) % 2**64
//
// Callers should obviously not depend on these exact values...
//
// (On Visual C++, we have to disable warning C4307 (integral constant
// overflow), even though unsigned integers have perfectly well-defined
// overflow behavior.)
#ifdef _MSC_VER
#pragma warning(push)
#pragma warning(disable : 4307)
#endif
static_assert(ObfuscateId(0) == uint64_t{0x85fdb20e1294309a}, "");
static_assert(ObfuscateId(1) == uint64_t{0x4b14a16a49da5503}, "");
static_assert(ObfuscateId(2) == uint64_t{0x102b90c68120796c}, "");
static_assert(ObfuscateId(3) == uint64_t{0xd5428022b8669dd5}, "");
static_assert(ObfuscateId(4) == uint64_t{0x9a596f7eefacc23e}, "");
static_assert(ObfuscateId(5) == uint64_t{0x5f705edb26f2e6a7}, "");
static_assert(ObfuscateId(6) == uint64_t{0x24874e375e390b10}, "");
static_assert(ObfuscateId(7) == uint64_t{0xe99e3d93957f2f79}, "");
static_assert(ObfuscateId(8) == uint64_t{0xaeb52cefccc553e2}, "");
static_assert(ObfuscateId(9) == uint64_t{0x73cc1c4c040b784b}, "");
#ifdef _MSC_VER
#pragma warning(pop)
#endif

}  // namespace

AudioCodecPairId AudioCodecPairId::Create() {
  return AudioCodecPairId(ObfuscateId(GetNextId()));
}

}  // namespace webrtc
