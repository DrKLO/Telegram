/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>
#include <string.h>

#include <iostream>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/flags/usage.h"
#include "absl/memory/memory.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/rtp_headers.h"
#include "logging/rtc_event_log/rtc_event_log_parser.h"
#include "logging/rtc_event_log/rtc_event_processor.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtp_header_extensions.h"
#include "modules/rtp_rtcp/source/rtp_packet.h"
#include "rtc_base/checks.h"
#include "test/rtp_file_reader.h"
#include "test/rtp_file_writer.h"

ABSL_FLAG(
    bool,
    audio,
    true,
    "Use --noaudio to exclude audio packets from the converted RTPdump file.");
ABSL_FLAG(
    bool,
    video,
    true,
    "Use --novideo to exclude video packets from the converted RTPdump file.");
ABSL_FLAG(
    bool,
    data,
    true,
    "Use --nodata to exclude data packets from the converted RTPdump file.");
ABSL_FLAG(
    bool,
    rtp,
    true,
    "Use --nortp to exclude RTP packets from the converted RTPdump file.");
ABSL_FLAG(
    bool,
    rtcp,
    true,
    "Use --nortcp to exclude RTCP packets from the converted RTPdump file.");
ABSL_FLAG(std::string,
          ssrc,
          "",
          "Store only packets with this SSRC (decimal or hex, the latter "
          "starting with 0x).");

namespace {

using MediaType = webrtc::ParsedRtcEventLog::MediaType;

// Parses the input string for a valid SSRC. If a valid SSRC is found, it is
// written to the output variable `ssrc`, and true is returned. Otherwise,
// false is returned.
// The empty string must be validated as true, because it is the default value
// of the command-line flag. In this case, no value is written to the output
// variable.
absl::optional<uint32_t> ParseSsrc(absl::string_view str) {
  // If the input string starts with 0x or 0X it indicates a hexadecimal number.
  uint32_t ssrc;
  auto read_mode = std::dec;
  if (str.size() > 2 &&
      (str.substr(0, 2) == "0x" || str.substr(0, 2) == "0X")) {
    read_mode = std::hex;
    str = str.substr(2);
  }
  std::stringstream ss(std::string{str});
  ss >> read_mode >> ssrc;
  if (str.empty() || (!ss.fail() && ss.eof()))
    return ssrc;
  return absl::nullopt;
}

bool ShouldSkipStream(MediaType media_type,
                      uint32_t ssrc,
                      absl::optional<uint32_t> ssrc_filter) {
  if (!absl::GetFlag(FLAGS_audio) && media_type == MediaType::AUDIO)
    return true;
  if (!absl::GetFlag(FLAGS_video) && media_type == MediaType::VIDEO)
    return true;
  if (!absl::GetFlag(FLAGS_data) && media_type == MediaType::DATA)
    return true;
  if (ssrc_filter.has_value() && ssrc != *ssrc_filter)
    return true;
  return false;
}

// Convert a LoggedRtpPacketIncoming to a test::RtpPacket. Header extension IDs
// are allocated according to the provided extension map. This might not match
// the extension map used in the actual call.
void ConvertRtpPacket(
    const webrtc::LoggedRtpPacketIncoming& incoming,
    const webrtc::RtpHeaderExtensionMap& default_extension_map,
    webrtc::test::RtpPacket* packet) {
  webrtc::RtpPacket reconstructed_packet(&default_extension_map);

  reconstructed_packet.SetMarker(incoming.rtp.header.markerBit);
  reconstructed_packet.SetPayloadType(incoming.rtp.header.payloadType);
  reconstructed_packet.SetSequenceNumber(incoming.rtp.header.sequenceNumber);
  reconstructed_packet.SetTimestamp(incoming.rtp.header.timestamp);
  reconstructed_packet.SetSsrc(incoming.rtp.header.ssrc);
  if (incoming.rtp.header.numCSRCs > 0) {
    reconstructed_packet.SetCsrcs(rtc::ArrayView<const uint32_t>(
        incoming.rtp.header.arrOfCSRCs, incoming.rtp.header.numCSRCs));
  }

  // Set extensions.
  if (incoming.rtp.header.extension.hasTransmissionTimeOffset)
    reconstructed_packet.SetExtension<webrtc::TransmissionOffset>(
        incoming.rtp.header.extension.transmissionTimeOffset);
  if (incoming.rtp.header.extension.hasAbsoluteSendTime)
    reconstructed_packet.SetExtension<webrtc::AbsoluteSendTime>(
        incoming.rtp.header.extension.absoluteSendTime);
  if (incoming.rtp.header.extension.hasTransportSequenceNumber)
    reconstructed_packet.SetExtension<webrtc::TransportSequenceNumber>(
        incoming.rtp.header.extension.transportSequenceNumber);
  if (incoming.rtp.header.extension.hasAudioLevel)
    reconstructed_packet.SetExtension<webrtc::AudioLevel>(
        incoming.rtp.header.extension.voiceActivity,
        incoming.rtp.header.extension.audioLevel);
  if (incoming.rtp.header.extension.hasVideoRotation)
    reconstructed_packet.SetExtension<webrtc::VideoOrientation>(
        incoming.rtp.header.extension.videoRotation);
  if (incoming.rtp.header.extension.hasVideoContentType)
    reconstructed_packet.SetExtension<webrtc::VideoContentTypeExtension>(
        incoming.rtp.header.extension.videoContentType);
  if (incoming.rtp.header.extension.has_video_timing)
    reconstructed_packet.SetExtension<webrtc::VideoTimingExtension>(
        incoming.rtp.header.extension.video_timing);

  RTC_DCHECK_EQ(reconstructed_packet.size(), incoming.rtp.header_length);
  RTC_DCHECK_EQ(reconstructed_packet.headers_size(),
                incoming.rtp.header_length);
  memcpy(packet->data, reconstructed_packet.data(),
         reconstructed_packet.headers_size());
  packet->length = reconstructed_packet.headers_size();
  packet->original_length = incoming.rtp.total_length;
  packet->time_ms = incoming.log_time_ms();
  // Set padding bit.
  if (incoming.rtp.header.paddingLength > 0)
    packet->data[0] = packet->data[0] | 0x20;
}

}  // namespace

// This utility will convert a stored event log to the rtpdump format.
int main(int argc, char* argv[]) {
  absl::SetProgramUsageMessage(
      "Tool for converting an RtcEventLog file to an "
      "RTP dump file.\n"
      "Example usage:\n"
      "./rtc_event_log2rtp_dump input.rel output.rtp\n");
  std::vector<char*> args = absl::ParseCommandLine(argc, argv);
  if (args.size() != 3) {
    std::cout << absl::ProgramUsageMessage();
    return 1;
  }

  std::string input_file = args[1];
  std::string output_file = args[2];

  absl::optional<uint32_t> ssrc_filter;
  if (!absl::GetFlag(FLAGS_ssrc).empty()) {
    ssrc_filter = ParseSsrc(absl::GetFlag(FLAGS_ssrc));
    RTC_CHECK(ssrc_filter.has_value()) << "Failed to read SSRC filter flag.";
  }

  webrtc::ParsedRtcEventLog parsed_stream;
  auto status = parsed_stream.ParseFile(input_file);
  if (!status.ok()) {
    std::cerr << "Failed to parse event log " << input_file << ": "
              << status.message() << std::endl;
    return -1;
  }

  std::unique_ptr<webrtc::test::RtpFileWriter> rtp_writer(
      webrtc::test::RtpFileWriter::Create(
          webrtc::test::RtpFileWriter::FileFormat::kRtpDump, output_file));

  if (!rtp_writer) {
    std::cerr << "Error while opening output file: " << output_file
              << std::endl;
    return -1;
  }

  int rtp_counter = 0, rtcp_counter = 0;
  bool header_only = false;

  webrtc::RtpHeaderExtensionMap default_extension_map =
      webrtc::ParsedRtcEventLog::GetDefaultHeaderExtensionMap();
  auto handle_rtp = [&default_extension_map, &rtp_writer, &rtp_counter](
                        const webrtc::LoggedRtpPacketIncoming& incoming) {
    webrtc::test::RtpPacket packet;
    ConvertRtpPacket(incoming, default_extension_map, &packet);

    rtp_writer->WritePacket(&packet);
    rtp_counter++;
  };

  auto handle_rtcp = [&rtp_writer, &rtcp_counter](
                         const webrtc::LoggedRtcpPacketIncoming& incoming) {
    webrtc::test::RtpPacket packet;
    memcpy(packet.data, incoming.rtcp.raw_data.data(),
           incoming.rtcp.raw_data.size());
    packet.length = incoming.rtcp.raw_data.size();
    // For RTCP packets the original_length should be set to 0 in the
    // RTPdump format.
    packet.original_length = 0;
    packet.time_ms = incoming.log_time_ms();

    rtp_writer->WritePacket(&packet);
    rtcp_counter++;
  };

  webrtc::RtcEventProcessor event_processor;
  for (const auto& stream : parsed_stream.incoming_rtp_packets_by_ssrc()) {
    MediaType media_type =
        parsed_stream.GetMediaType(stream.ssrc, webrtc::kIncomingPacket);
    if (ShouldSkipStream(media_type, stream.ssrc, ssrc_filter))
      continue;
    event_processor.AddEvents(stream.incoming_packets, handle_rtp);
  }
  // Note that `packet_ssrc` is the sender SSRC. An RTCP message may contain
  // report blocks for many streams, thus several SSRCs and they don't
  // necessarily have to be of the same media type. We therefore don't
  // support filtering of RTCP based on SSRC and media type.
  event_processor.AddEvents(parsed_stream.incoming_rtcp_packets(), handle_rtcp);

  event_processor.ProcessEventsInOrder();

  std::cout << "Wrote " << rtp_counter << (header_only ? " header-only" : "")
            << " RTP packets and " << rtcp_counter
            << " RTCP packets to the "
               "output file."
            << std::endl;
  return 0;
}
