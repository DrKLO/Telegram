/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_CRYPTO_PARAMS_H_
#define API_CRYPTO_PARAMS_H_

#include <string>

#include "absl/strings/string_view.h"

namespace cricket {

// Parameters for SRTP negotiation, as described in RFC 4568.
// TODO(benwright) - Rename to SrtpCryptoParams as these only apply to SRTP and
// not generic crypto parameters for WebRTC.
struct CryptoParams {
  CryptoParams() : tag(0) {}
  CryptoParams(int t,
               absl::string_view cs,
               absl::string_view kp,
               absl::string_view sp)
      : tag(t), cipher_suite(cs), key_params(kp), session_params(sp) {}

  bool Matches(const CryptoParams& params) const {
    return (tag == params.tag && cipher_suite == params.cipher_suite);
  }

  int tag;
  std::string cipher_suite;
  std::string key_params;
  std::string session_params;
};

}  // namespace cricket

#endif  // API_CRYPTO_PARAMS_H_
