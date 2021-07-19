/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#include "net/dcsctp/socket/state_cookie.h"

#include <cstdint>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/packet/bounded_byte_reader.h"
#include "net/dcsctp/packet/bounded_byte_writer.h"
#include "net/dcsctp/socket/capabilities.h"
#include "rtc_base/logging.h"

namespace dcsctp {

// Magic values, which the state cookie is prefixed with.
constexpr uint32_t kMagic1 = 1684230979;
constexpr uint32_t kMagic2 = 1414541360;
constexpr size_t StateCookie::kCookieSize;

std::vector<uint8_t> StateCookie::Serialize() {
  std::vector<uint8_t> cookie;
  cookie.resize(kCookieSize);
  BoundedByteWriter<kCookieSize> buffer(cookie);
  buffer.Store32<0>(kMagic1);
  buffer.Store32<4>(kMagic2);
  buffer.Store32<8>(*initiate_tag_);
  buffer.Store32<12>(*initial_tsn_);
  buffer.Store32<16>(a_rwnd_);
  buffer.Store32<20>(static_cast<uint32_t>(*tie_tag_ >> 32));
  buffer.Store32<24>(static_cast<uint32_t>(*tie_tag_));
  buffer.Store8<28>(capabilities_.partial_reliability);
  buffer.Store8<29>(capabilities_.message_interleaving);
  buffer.Store8<30>(capabilities_.reconfig);
  return cookie;
}

absl::optional<StateCookie> StateCookie::Deserialize(
    rtc::ArrayView<const uint8_t> cookie) {
  if (cookie.size() != kCookieSize) {
    RTC_DLOG(LS_WARNING) << "Invalid state cookie: " << cookie.size()
                         << " bytes";
    return absl::nullopt;
  }

  BoundedByteReader<kCookieSize> buffer(cookie);
  uint32_t magic1 = buffer.Load32<0>();
  uint32_t magic2 = buffer.Load32<4>();
  if (magic1 != kMagic1 || magic2 != kMagic2) {
    RTC_DLOG(LS_WARNING) << "Invalid state cookie; wrong magic";
    return absl::nullopt;
  }

  VerificationTag verification_tag(buffer.Load32<8>());
  TSN initial_tsn(buffer.Load32<12>());
  uint32_t a_rwnd = buffer.Load32<16>();
  uint32_t tie_tag_upper = buffer.Load32<20>();
  uint32_t tie_tag_lower = buffer.Load32<24>();
  TieTag tie_tag(static_cast<uint64_t>(tie_tag_upper) << 32 |
                 static_cast<uint64_t>(tie_tag_lower));
  Capabilities capabilities;
  capabilities.partial_reliability = buffer.Load8<28>() != 0;
  capabilities.message_interleaving = buffer.Load8<29>() != 0;
  capabilities.reconfig = buffer.Load8<30>() != 0;

  return StateCookie(verification_tag, initial_tsn, a_rwnd, tie_tag,
                     capabilities);
}

}  // namespace dcsctp
