/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_SOCKET_STATE_COOKIE_H_
#define NET_DCSCTP_SOCKET_STATE_COOKIE_H_

#include <cstdint>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "net/dcsctp/common/internal_types.h"
#include "net/dcsctp/socket/capabilities.h"

namespace dcsctp {

// This is serialized as a state cookie and put in INIT_ACK. The client then
// responds with this in COOKIE_ECHO.
//
// NOTE: Expect that the client will modify it to try to exploit the library.
// Do not trust anything in it; no pointers or anything like that.
class StateCookie {
 public:
  static constexpr size_t kCookieSize = 36;

  StateCookie(VerificationTag initiate_tag,
              TSN initial_tsn,
              uint32_t a_rwnd,
              TieTag tie_tag,
              Capabilities capabilities)
      : initiate_tag_(initiate_tag),
        initial_tsn_(initial_tsn),
        a_rwnd_(a_rwnd),
        tie_tag_(tie_tag),
        capabilities_(capabilities) {}

  // Returns a serialized version of this cookie.
  std::vector<uint8_t> Serialize();

  // Deserializes the cookie, and returns absl::nullopt if that failed.
  static absl::optional<StateCookie> Deserialize(
      rtc::ArrayView<const uint8_t> cookie);

  VerificationTag initiate_tag() const { return initiate_tag_; }
  TSN initial_tsn() const { return initial_tsn_; }
  uint32_t a_rwnd() const { return a_rwnd_; }
  TieTag tie_tag() const { return tie_tag_; }
  const Capabilities& capabilities() const { return capabilities_; }

 private:
  const VerificationTag initiate_tag_;
  const TSN initial_tsn_;
  const uint32_t a_rwnd_;
  const TieTag tie_tag_;
  const Capabilities capabilities_;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_SOCKET_STATE_COOKIE_H_
