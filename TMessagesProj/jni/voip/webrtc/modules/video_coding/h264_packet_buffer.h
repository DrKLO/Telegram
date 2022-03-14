/*
 *  Copyright (c) 2021 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_VIDEO_CODING_H264_PACKET_BUFFER_H_
#define MODULES_VIDEO_CODING_H264_PACKET_BUFFER_H_

#include <array>
#include <memory>
#include <vector>

#include "absl/base/attributes.h"
#include "absl/types/optional.h"
#include "modules/video_coding/packet_buffer.h"
#include "rtc_base/numerics/sequence_number_util.h"

namespace webrtc {

class H264PacketBuffer {
 public:
  // The H264PacketBuffer does the same job as the PacketBuffer but for H264
  // only. To make it fit in with surronding code the PacketBuffer input/output
  // classes are used.
  using Packet = video_coding::PacketBuffer::Packet;
  using InsertResult = video_coding::PacketBuffer::InsertResult;

  explicit H264PacketBuffer(bool idr_only_keyframes_allowed);

  ABSL_MUST_USE_RESULT InsertResult
  InsertPacket(std::unique_ptr<Packet> packet);

 private:
  static constexpr int kBufferSize = 2048;

  std::unique_ptr<Packet>& GetPacket(int64_t unwrapped_seq_num);
  bool BeginningOfStream(const Packet& packet) const;
  std::vector<std::unique_ptr<Packet>> FindFrames(int64_t unwrapped_seq_num);
  bool MaybeAssembleFrame(int64_t start_seq_num_unwrapped,
                          int64_t end_sequence_number_unwrapped,
                          std::vector<std::unique_ptr<Packet>>& packets);

  const bool idr_only_keyframes_allowed_;
  std::array<std::unique_ptr<Packet>, kBufferSize> buffer_;
  absl::optional<int64_t> last_continuous_unwrapped_seq_num_;
  SeqNumUnwrapper<uint16_t> seq_num_unwrapper_;
};

}  // namespace webrtc

#endif  // MODULES_VIDEO_CODING_H264_PACKET_BUFFER_H_
