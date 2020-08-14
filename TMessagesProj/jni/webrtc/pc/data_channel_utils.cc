/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/data_channel_utils.h"

namespace webrtc {

bool PacketQueue::Empty() const {
  return packets_.empty();
}

std::unique_ptr<DataBuffer> PacketQueue::PopFront() {
  RTC_DCHECK(!packets_.empty());
  byte_count_ -= packets_.front()->size();
  std::unique_ptr<DataBuffer> packet = std::move(packets_.front());
  packets_.pop_front();
  return packet;
}

void PacketQueue::PushFront(std::unique_ptr<DataBuffer> packet) {
  byte_count_ += packet->size();
  packets_.push_front(std::move(packet));
}

void PacketQueue::PushBack(std::unique_ptr<DataBuffer> packet) {
  byte_count_ += packet->size();
  packets_.push_back(std::move(packet));
}

void PacketQueue::Clear() {
  packets_.clear();
  byte_count_ = 0;
}

void PacketQueue::Swap(PacketQueue* other) {
  size_t other_byte_count = other->byte_count_;
  other->byte_count_ = byte_count_;
  byte_count_ = other_byte_count;

  other->packets_.swap(packets_);
}

bool IsSctpLike(cricket::DataChannelType type) {
  return type == cricket::DCT_SCTP;
}

}  // namespace webrtc
