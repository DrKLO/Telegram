/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_CALL_TRANSPORT_H_
#define API_CALL_TRANSPORT_H_

#include <stddef.h>
#include <stdint.h>

#include "api/array_view.h"
#include "api/ref_counted_base.h"
#include "api/scoped_refptr.h"

namespace webrtc {

// TODO(holmer): Look into unifying this with the PacketOptions in
// asyncpacketsocket.h.
struct PacketOptions {
  PacketOptions();
  PacketOptions(const PacketOptions&);
  ~PacketOptions();

  // A 16 bits positive id. Negative ids are invalid and should be interpreted
  // as packet_id not being set.
  int packet_id = -1;
  // Additional data bound to the RTP packet for use in application code,
  // outside of WebRTC.
  rtc::scoped_refptr<rtc::RefCountedBase> additional_data;
  // Whether this is a retransmission of an earlier packet.
  bool is_retransmit = false;
  bool included_in_feedback = false;
  bool included_in_allocation = false;
  // Whether this packet can be part of a packet batch at lower levels.
  bool batchable = false;
  // Whether this packet is the last of a batch.
  bool last_packet_in_batch = false;
};

class Transport {
 public:
  virtual bool SendRtp(rtc::ArrayView<const uint8_t> packet,
                       const PacketOptions& options) = 0;
  virtual bool SendRtcp(rtc::ArrayView<const uint8_t> packet) = 0;

 protected:
  virtual ~Transport() {}
};

}  // namespace webrtc

#endif  // API_CALL_TRANSPORT_H_
