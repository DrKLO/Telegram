/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/tools/rtp_file_source.h"

#include <string.h>

#include "absl/strings/string_view.h"
#ifndef WIN32
#include <netinet/in.h>
#endif

#include <memory>

#include "modules/audio_coding/neteq/tools/packet.h"
#include "rtc_base/checks.h"
#include "test/rtp_file_reader.h"

namespace webrtc {
namespace test {

RtpFileSource* RtpFileSource::Create(absl::string_view file_name,
                                     absl::optional<uint32_t> ssrc_filter) {
  RtpFileSource* source = new RtpFileSource(ssrc_filter);
  RTC_CHECK(source->OpenFile(file_name));
  return source;
}

bool RtpFileSource::ValidRtpDump(absl::string_view file_name) {
  std::unique_ptr<RtpFileReader> temp_file(
      RtpFileReader::Create(RtpFileReader::kRtpDump, file_name));
  return !!temp_file;
}

bool RtpFileSource::ValidPcap(absl::string_view file_name) {
  std::unique_ptr<RtpFileReader> temp_file(
      RtpFileReader::Create(RtpFileReader::kPcap, file_name));
  return !!temp_file;
}

RtpFileSource::~RtpFileSource() {}

bool RtpFileSource::RegisterRtpHeaderExtension(RTPExtensionType type,
                                               uint8_t id) {
  return rtp_header_extension_map_.RegisterByType(id, type);
}

std::unique_ptr<Packet> RtpFileSource::NextPacket() {
  while (true) {
    RtpPacket temp_packet;
    if (!rtp_reader_->NextPacket(&temp_packet)) {
      return NULL;
    }
    if (temp_packet.original_length == 0) {
      // May be an RTCP packet.
      // Read the next one.
      continue;
    }
    auto packet = std::make_unique<Packet>(
        rtc::CopyOnWriteBuffer(temp_packet.data, temp_packet.length),
        temp_packet.original_length, temp_packet.time_ms,
        &rtp_header_extension_map_);
    if (!packet->valid_header()) {
      continue;
    }
    if (filter_.test(packet->header().payloadType) ||
        (ssrc_filter_ && packet->header().ssrc != *ssrc_filter_)) {
      // This payload type should be filtered out. Continue to the next packet.
      continue;
    }
    return packet;
  }
}

RtpFileSource::RtpFileSource(absl::optional<uint32_t> ssrc_filter)
    : PacketSource(), ssrc_filter_(ssrc_filter) {}

bool RtpFileSource::OpenFile(absl::string_view file_name) {
  rtp_reader_.reset(RtpFileReader::Create(RtpFileReader::kRtpDump, file_name));
  if (rtp_reader_)
    return true;
  rtp_reader_.reset(RtpFileReader::Create(RtpFileReader::kPcap, file_name));
  if (!rtp_reader_) {
    RTC_FATAL()
        << "Couldn't open input file as either a rtpdump or .pcap. Note "
        << "that .pcapng is not supported.";
  }
  return true;
}

}  // namespace test
}  // namespace webrtc
