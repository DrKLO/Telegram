/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef NET_DCSCTP_SOCKET_CAPABILITIES_H_
#define NET_DCSCTP_SOCKET_CAPABILITIES_H_

namespace dcsctp {
// Indicates what the association supports, meaning that both parties
// support it and that feature can be used.
struct Capabilities {
  // RFC3758 Partial Reliability Extension
  bool partial_reliability = false;
  // RFC8260 Stream Schedulers and User Message Interleaving
  bool message_interleaving = false;
  // RFC6525 Stream Reconfiguration
  bool reconfig = false;
};
}  // namespace dcsctp

#endif  // NET_DCSCTP_SOCKET_CAPABILITIES_H_
