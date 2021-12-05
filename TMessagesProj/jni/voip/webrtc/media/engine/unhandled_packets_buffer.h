/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_UNHANDLED_PACKETS_BUFFER_H_
#define MEDIA_ENGINE_UNHANDLED_PACKETS_BUFFER_H_

#include <stdint.h>

#include <functional>
#include <tuple>
#include <utility>
#include <vector>

#include "rtc_base/copy_on_write_buffer.h"

namespace cricket {

class UnhandledPacketsBuffer {
 public:
  // Visible for testing.
  static constexpr size_t kMaxStashedPackets = 50;

  UnhandledPacketsBuffer();
  ~UnhandledPacketsBuffer();

  // Store packet in buffer.
  void AddPacket(uint32_t ssrc,
                 int64_t packet_time_us,
                 rtc::CopyOnWriteBuffer packet);

  // Feed all packets with |ssrcs| into |consumer|.
  void BackfillPackets(
      rtc::ArrayView<const uint32_t> ssrcs,
      std::function<void(uint32_t, int64_t, rtc::CopyOnWriteBuffer)> consumer);

 private:
  size_t insert_pos_ = 0;
  struct PacketWithMetadata {
    uint32_t ssrc;
    int64_t packet_time_us;
    rtc::CopyOnWriteBuffer packet;
  };
  std::vector<PacketWithMetadata> buffer_;
};

}  // namespace cricket

#endif  // MEDIA_ENGINE_UNHANDLED_PACKETS_BUFFER_H_
