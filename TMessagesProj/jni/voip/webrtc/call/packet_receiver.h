/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef CALL_PACKET_RECEIVER_H_
#define CALL_PACKET_RECEIVER_H_

#include <algorithm>
#include <functional>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "api/media_types.h"
#include "rtc_base/copy_on_write_buffer.h"

namespace webrtc {

class PacketReceiver {
 public:
  enum DeliveryStatus {
    DELIVERY_OK,
    DELIVERY_UNKNOWN_SSRC,
    DELIVERY_PACKET_ERROR,
  };

  // Definition of the callback to execute when packet delivery is complete.
  // The callback will be issued on the same thread as called DeliverPacket.
  typedef std::function<
      void(DeliveryStatus, MediaType, rtc::CopyOnWriteBuffer, int64_t)>
      PacketCallback;

  // Asynchronously handle packet delivery and report back to the caller when
  // delivery of the packet has completed.
  // Note that if the packet is invalid or can be processed without the need of
  // asynchronous operations that the |callback| may have been called before
  // the function returns.
  // TODO(bugs.webrtc.org/11993): This function is meant to be called on the
  // network thread exclusively but while the code is being updated to align
  // with those goals, it may be called either on the worker or network threads.
  // Update docs etc when the work has been completed. Once we're done with the
  // updates, we might be able to go back to returning the status from this
  // function instead of having to report it via a callback.
  virtual void DeliverPacketAsync(MediaType media_type,
                                  rtc::CopyOnWriteBuffer packet,
                                  int64_t packet_time_us,
                                  PacketCallback callback) {
    DeliveryStatus status = DeliverPacket(media_type, packet, packet_time_us);
    if (callback)
      callback(status, media_type, std::move(packet), packet_time_us);
  }

  virtual DeliveryStatus DeliverPacket(MediaType media_type,
                                       rtc::CopyOnWriteBuffer packet,
                                       int64_t packet_time_us) = 0;

 protected:
  virtual ~PacketReceiver() {}
};

}  // namespace webrtc

#endif  // CALL_PACKET_RECEIVER_H_
