/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#include <memory>

#include "modules/remote_bitrate_estimator/tools/bwe_rtp.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/strings/string_builder.h"
#include "test/rtp_file_reader.h"

int main(int argc, char* argv[]) {
  std::unique_ptr<webrtc::test::RtpFileReader> reader;
  webrtc::RtpHeaderExtensionMap rtp_header_extensions;
  if (!ParseArgsAndSetupRtpReader(argc, argv, reader, rtp_header_extensions)) {
    return -1;
  }

  bool arrival_time_only = (argc >= 5 && strncmp(argv[4], "-t", 2) == 0);

  fprintf(stdout,
          "seqnum timestamp ts_offset abs_sendtime recvtime "
          "markerbit ssrc size original_size\n");
  int packet_counter = 0;
  int non_zero_abs_send_time = 0;
  int non_zero_ts_offsets = 0;
  webrtc::test::RtpPacket packet;
  while (reader->NextPacket(&packet)) {
    webrtc::RtpPacket header(&rtp_header_extensions);
    header.Parse(packet.data, packet.length);
    uint32_t abs_send_time = 0;
    if (header.GetExtension<webrtc::AbsoluteSendTime>(&abs_send_time) &&
        abs_send_time != 0)
      ++non_zero_abs_send_time;
    int32_t toffset = 0;
    if (header.GetExtension<webrtc::TransmissionOffset>(&toffset) &&
        toffset != 0)
      ++non_zero_ts_offsets;
    if (arrival_time_only) {
      rtc::StringBuilder ss;
      ss << static_cast<int64_t>(packet.time_ms) * 1000000;
      fprintf(stdout, "%s\n", ss.str().c_str());
    } else {
      fprintf(stdout, "%u %u %d %u %u %d %u %" RTC_PRIuS " %" RTC_PRIuS "\n",
              header.SequenceNumber(), header.Timestamp(), toffset,
              abs_send_time, packet.time_ms, header.Marker(), header.Ssrc(),
              packet.length, packet.original_length);
    }
    ++packet_counter;
  }
  fprintf(stderr, "Parsed %d packets\n", packet_counter);
  fprintf(stderr, "Packets with non-zero absolute send time: %d\n",
          non_zero_abs_send_time);
  fprintf(stderr, "Packets with non-zero timestamp offset: %d\n",
          non_zero_ts_offsets);
  return 0;
}
