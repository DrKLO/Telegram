/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/remote_bitrate_estimator/tools/bwe_rtp.h"

#include <stdio.h>

#include <set>
#include <sstream>
#include <string>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_abs_send_time.h"
#include "modules/remote_bitrate_estimator/remote_bitrate_estimator_single_stream.h"
#include "test/rtp_file_reader.h"
#include "test/rtp_header_parser.h"

ABSL_FLAG(std::string,
          extension_type,
          "abs",
          "Extension type, either abs for absolute send time or tsoffset "
          "for timestamp offset.");
std::string ExtensionType() {
  return absl::GetFlag(FLAGS_extension_type);
}

ABSL_FLAG(int, extension_id, 3, "Extension id.");
int ExtensionId() {
  return absl::GetFlag(FLAGS_extension_id);
}

ABSL_FLAG(std::string, input_file, "", "Input file.");
std::string InputFile() {
  return absl::GetFlag(FLAGS_input_file);
}

ABSL_FLAG(std::string,
          ssrc_filter,
          "",
          "Comma-separated list of SSRCs in hexadecimal which are to be "
          "used as input to the BWE (only applicable to pcap files).");
std::set<uint32_t> SsrcFilter() {
  std::string ssrc_filter_string = absl::GetFlag(FLAGS_ssrc_filter);
  if (ssrc_filter_string.empty())
    return std::set<uint32_t>();
  std::stringstream ss;
  std::string ssrc_filter = ssrc_filter_string;
  std::set<uint32_t> ssrcs;

  // Parse the ssrcs in hexadecimal format.
  ss << std::hex << ssrc_filter;
  uint32_t ssrc;
  while (ss >> ssrc) {
    ssrcs.insert(ssrc);
    ss.ignore(1, ',');
  }
  return ssrcs;
}

std::unique_ptr<webrtc::RtpHeaderParser> ParseArgsAndSetupEstimator(
    int argc,
    char** argv,
    webrtc::Clock* clock,
    webrtc::RemoteBitrateObserver* observer,
    std::unique_ptr<webrtc::test::RtpFileReader>* rtp_reader,
    std::unique_ptr<webrtc::RemoteBitrateEstimator>* estimator,
    std::string* estimator_used) {
  absl::ParseCommandLine(argc, argv);
  std::string filename = InputFile();

  std::set<uint32_t> ssrc_filter = SsrcFilter();
  fprintf(stderr, "Filter on SSRC: ");
  for (auto& s : ssrc_filter) {
    fprintf(stderr, "0x%08x, ", s);
  }
  fprintf(stderr, "\n");
  if (filename.substr(filename.find_last_of('.')) == ".pcap") {
    fprintf(stderr, "Opening as pcap\n");
    rtp_reader->reset(webrtc::test::RtpFileReader::Create(
        webrtc::test::RtpFileReader::kPcap, filename.c_str(), SsrcFilter()));
  } else {
    fprintf(stderr, "Opening as rtp\n");
    rtp_reader->reset(webrtc::test::RtpFileReader::Create(
        webrtc::test::RtpFileReader::kRtpDump, filename.c_str()));
  }
  if (!*rtp_reader) {
    fprintf(stderr, "Cannot open input file %s\n", filename.c_str());
    return nullptr;
  }
  fprintf(stderr, "Input file: %s\n\n", filename.c_str());

  webrtc::RTPExtensionType extension = webrtc::kRtpExtensionAbsoluteSendTime;
  if (ExtensionType() == "tsoffset") {
    extension = webrtc::kRtpExtensionTransmissionTimeOffset;
    fprintf(stderr, "Extension: toffset\n");
  } else if (ExtensionType() == "abs") {
    fprintf(stderr, "Extension: abs\n");
  } else {
    fprintf(stderr, "Unknown extension type\n");
    return nullptr;
  }

  // Setup the RTP header parser and the bitrate estimator.
  auto parser = webrtc::RtpHeaderParser::CreateForTest();
  parser->RegisterRtpHeaderExtension(extension, ExtensionId());
  if (estimator) {
    switch (extension) {
      case webrtc::kRtpExtensionAbsoluteSendTime: {
        estimator->reset(
            new webrtc::RemoteBitrateEstimatorAbsSendTime(observer, clock));
        *estimator_used = "AbsoluteSendTimeRemoteBitrateEstimator";
        break;
      }
      case webrtc::kRtpExtensionTransmissionTimeOffset: {
        estimator->reset(
            new webrtc::RemoteBitrateEstimatorSingleStream(observer, clock));
        *estimator_used = "RemoteBitrateEstimator";
        break;
      }
      default:
        assert(false);
        return nullptr;
    }
  }

  return parser;
}
