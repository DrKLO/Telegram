/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_ICE_CREDENTIALS_ITERATOR_H_
#define P2P_BASE_ICE_CREDENTIALS_ITERATOR_H_

#include <vector>

#include "p2p/base/transport_description.h"

namespace cricket {

class IceCredentialsIterator {
 public:
  explicit IceCredentialsIterator(const std::vector<IceParameters>&);
  virtual ~IceCredentialsIterator();

  // Get next pooled ice credentials.
  // Returns a new random credential if the pool is empty.
  IceParameters GetIceCredentials();

  static IceParameters CreateRandomIceCredentials();

 private:
  std::vector<IceParameters> pooled_ice_credentials_;
};

}  // namespace cricket

#endif  // P2P_BASE_ICE_CREDENTIALS_ITERATOR_H_
