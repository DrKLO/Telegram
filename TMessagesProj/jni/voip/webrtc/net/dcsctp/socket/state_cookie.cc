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
  buffer.Store32<8>(*peer_tag_);
  buffer.Store32<12>(*my_tag_);
  buffer.Store32<16>(*peer_initial_tsn_);
  buffer.Store32<20>(*my_initial_tsn_);
  buffer.Store32<24>(a_rwnd_);
  buffer.Store32<28>(static_cast<uint32_t>(*tie_tag_ >> 32));
  buffer.Store32<32>(static_cast<uint32_t>(*tie_tag_));
  buffer.Store8<36>(capabilities_.partial_reliability);
  buffer.Store8<37>(capabilities_.message_interleaving);
  buffer.Store8<38>(capabilities_.reconfig);
  buffer.Store16<40>(capabilities_.negotiated_maximum_incoming_streams);
  buffer.Store16<42>(capabilities_.negotiated_maximum_outgoing_streams);
  buffer.Store8<44>(capabilities_.zero_checksum);
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

  VerificationTag peer_tag(buffer.Load32<8>());
  VerificationTag my_tag(buffer.Load32<12>());
  TSN peer_initial_tsn(buffer.Load32<16>());
  TSN my_initial_tsn(buffer.Load32<20>());
  uint32_t a_rwnd = buffer.Load32<24>();
  uint32_t tie_tag_upper = buffer.Load32<28>();
  uint32_t tie_tag_lower = buffer.Load32<32>();
  TieTag tie_tag(static_cast<uint64_t>(tie_tag_upper) << 32 |
                 static_cast<uint64_t>(tie_tag_lower));
  Capabilities capabilities;
  capabilities.partial_reliability = buffer.Load8<36>() != 0;
  capabilities.message_interleaving = buffer.Load8<37>() != 0;
  capabilities.reconfig = buffer.Load8<38>() != 0;
  capabilities.negotiated_maximum_incoming_streams = buffer.Load16<40>();
  capabilities.negotiated_maximum_outgoing_streams = buffer.Load16<42>();
  capabilities.zero_checksum = buffer.Load8<44>() != 0;

  return StateCookie(peer_tag, my_tag, peer_initial_tsn, my_initial_tsn, a_rwnd,
                     tie_tag, capabilities);
}

}  // namespace dcsctp
