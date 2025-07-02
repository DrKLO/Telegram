/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/deprecated/stream_generator.h"

#include <string.h>

#include <list>

#include "modules/video_coding/deprecated/packet.h"
#include "rtc_base/checks.h"

namespace webrtc {

StreamGenerator::StreamGenerator(uint16_t start_seq_num, int64_t current_time)
    : packets_(), sequence_number_(start_seq_num), start_time_(current_time) {}

void StreamGenerator::Init(uint16_t start_seq_num, int64_t current_time) {
  packets_.clear();
  sequence_number_ = start_seq_num;
  start_time_ = current_time;
  memset(packet_buffer_, 0, sizeof(packet_buffer_));
}

void StreamGenerator::GenerateFrame(VideoFrameType type,
                                    int num_media_packets,
                                    int num_empty_packets,
                                    int64_t time_ms) {
  uint32_t timestamp = 90 * (time_ms - start_time_);
  for (int i = 0; i < num_media_packets; ++i) {
    const int packet_size =
        (kFrameSize + num_media_packets / 2) / num_media_packets;
    bool marker_bit = (i == num_media_packets - 1);
    packets_.push_back(GeneratePacket(sequence_number_, timestamp, packet_size,
                                      (i == 0), marker_bit, type));
    ++sequence_number_;
  }
  for (int i = 0; i < num_empty_packets; ++i) {
    packets_.push_back(GeneratePacket(sequence_number_, timestamp, 0, false,
                                      false, VideoFrameType::kEmptyFrame));
    ++sequence_number_;
  }
}

VCMPacket StreamGenerator::GeneratePacket(uint16_t sequence_number,
                                          uint32_t timestamp,
                                          unsigned int size,
                                          bool first_packet,
                                          bool marker_bit,
                                          VideoFrameType type) {
  RTC_CHECK_LT(size, kMaxPacketSize);
  VCMPacket packet;
  packet.seqNum = sequence_number;
  packet.timestamp = timestamp;
  packet.video_header.frame_type = type;
  packet.video_header.is_first_packet_in_frame = first_packet;
  packet.markerBit = marker_bit;
  packet.sizeBytes = size;
  packet.dataPtr = packet_buffer_;
  if (packet.is_first_packet_in_frame())
    packet.completeNALU = kNaluStart;
  else if (packet.markerBit)
    packet.completeNALU = kNaluEnd;
  else
    packet.completeNALU = kNaluIncomplete;
  return packet;
}

bool StreamGenerator::PopPacket(VCMPacket* packet, int index) {
  std::list<VCMPacket>::iterator it = GetPacketIterator(index);
  if (it == packets_.end())
    return false;
  if (packet)
    *packet = (*it);
  packets_.erase(it);
  return true;
}

bool StreamGenerator::GetPacket(VCMPacket* packet, int index) {
  std::list<VCMPacket>::iterator it = GetPacketIterator(index);
  if (it == packets_.end())
    return false;
  if (packet)
    *packet = (*it);
  return true;
}

bool StreamGenerator::NextPacket(VCMPacket* packet) {
  if (packets_.empty())
    return false;
  if (packet != NULL)
    *packet = packets_.front();
  packets_.pop_front();
  return true;
}

void StreamGenerator::DropLastPacket() {
  packets_.pop_back();
}

uint16_t StreamGenerator::NextSequenceNumber() const {
  if (packets_.empty())
    return sequence_number_;
  return packets_.front().seqNum;
}

int StreamGenerator::PacketsRemaining() const {
  return packets_.size();
}

std::list<VCMPacket>::iterator StreamGenerator::GetPacketIterator(int index) {
  std::list<VCMPacket>::iterator it = packets_.begin();
  for (int i = 0; i < index; ++i) {
    ++it;
    if (it == packets_.end())
      break;
  }
  return it;
}

}  // namespace webrtc
