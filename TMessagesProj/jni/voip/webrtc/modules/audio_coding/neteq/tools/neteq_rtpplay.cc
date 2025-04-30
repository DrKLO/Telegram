/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <iostream>
#include <string>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "modules/audio_coding/neteq/tools/neteq_test.h"
#include "modules/audio_coding/neteq/tools/neteq_test_factory.h"
#include "rtc_base/strings/string_builder.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"

using TestConfig = webrtc::test::NetEqTestFactory::Config;

ABSL_FLAG(bool,
          codec_map,
          false,
          "Prints the mapping between RTP payload type and "
          "codec");
ABSL_FLAG(std::string,
          force_fieldtrials,
          "",
          "Field trials control experimental feature code which can be forced. "
          "E.g. running with --force_fieldtrials=WebRTC-FooFeature/Enable/"
          " will assign the group Enable to field trial WebRTC-FooFeature.");
ABSL_FLAG(int, pcmu, TestConfig::default_pcmu(), "RTP payload type for PCM-u");
ABSL_FLAG(int, pcma, TestConfig::default_pcma(), "RTP payload type for PCM-a");
ABSL_FLAG(int, ilbc, TestConfig::default_ilbc(), "RTP payload type for iLBC");
ABSL_FLAG(int, isac, TestConfig::default_isac(), "RTP payload type for iSAC");
ABSL_FLAG(int,
          isac_swb,
          TestConfig::default_isac_swb(),
          "RTP payload type for iSAC-swb (32 kHz)");
ABSL_FLAG(int, opus, TestConfig::default_opus(), "RTP payload type for Opus");
ABSL_FLAG(int,
          pcm16b,
          TestConfig::default_pcm16b(),
          "RTP payload type for PCM16b-nb (8 kHz)");
ABSL_FLAG(int,
          pcm16b_wb,
          TestConfig::default_pcm16b_wb(),
          "RTP payload type for PCM16b-wb (16 kHz)");
ABSL_FLAG(int,
          pcm16b_swb32,
          TestConfig::default_pcm16b_swb32(),
          "RTP payload type for PCM16b-swb32 (32 kHz)");
ABSL_FLAG(int,
          pcm16b_swb48,
          TestConfig::default_pcm16b_swb48(),
          "RTP payload type for PCM16b-swb48 (48 kHz)");
ABSL_FLAG(int, g722, TestConfig::default_g722(), "RTP payload type for G.722");
ABSL_FLAG(int,
          avt,
          TestConfig::default_avt(),
          "RTP payload type for AVT/DTMF (8 kHz)");
ABSL_FLAG(int,
          avt_16,
          TestConfig::default_avt_16(),
          "RTP payload type for AVT/DTMF (16 kHz)");
ABSL_FLAG(int,
          avt_32,
          TestConfig::default_avt_32(),
          "RTP payload type for AVT/DTMF (32 kHz)");
ABSL_FLAG(int,
          avt_48,
          TestConfig::default_avt_48(),
          "RTP payload type for AVT/DTMF (48 kHz)");
ABSL_FLAG(int,
          red,
          TestConfig::default_red(),
          "RTP payload type for redundant audio (RED)");
ABSL_FLAG(int,
          cn_nb,
          TestConfig::default_cn_nb(),
          "RTP payload type for comfort noise (8 kHz)");
ABSL_FLAG(int,
          cn_wb,
          TestConfig::default_cn_wb(),
          "RTP payload type for comfort noise (16 kHz)");
ABSL_FLAG(int,
          cn_swb32,
          TestConfig::default_cn_swb32(),
          "RTP payload type for comfort noise (32 kHz)");
ABSL_FLAG(int,
          cn_swb48,
          TestConfig::default_cn_swb48(),
          "RTP payload type for comfort noise (48 kHz)");
ABSL_FLAG(std::string,
          replacement_audio_file,
          "",
          "A PCM file that will be used to populate dummy"
          " RTP packets");
ABSL_FLAG(std::string,
          ssrc,
          "",
          "Only use packets with this SSRC (decimal or hex, the latter "
          "starting with 0x)");
ABSL_FLAG(int,
          audio_level,
          TestConfig::default_audio_level(),
          "Extension ID for audio level (RFC 6464)");
ABSL_FLAG(int,
          abs_send_time,
          TestConfig::default_abs_send_time(),
          "Extension ID for absolute sender time");
ABSL_FLAG(int,
          transport_seq_no,
          TestConfig::default_transport_seq_no(),
          "Extension ID for transport sequence number");
ABSL_FLAG(int,
          video_content_type,
          TestConfig::default_video_content_type(),
          "Extension ID for video content type");
ABSL_FLAG(int,
          video_timing,
          TestConfig::default_video_timing(),
          "Extension ID for video timing");
ABSL_FLAG(std::string,
          output_files_base_name,
          "",
          "Custom path used as prefix for the output files - i.e., "
          "matlab plot, python plot, text log.");
ABSL_FLAG(bool,
          matlabplot,
          false,
          "Generates a matlab script for plotting the delay profile");
ABSL_FLAG(bool,
          pythonplot,
          false,
          "Generates a python script for plotting the delay profile");
ABSL_FLAG(bool,
          textlog,
          false,
          "Generates a text log describing the simulation on a "
          "step-by-step basis.");
ABSL_FLAG(bool, concealment_events, false, "Prints concealment events");
ABSL_FLAG(int,
          max_nr_packets_in_buffer,
          TestConfig::default_max_nr_packets_in_buffer(),
          "Maximum allowed number of packets in the buffer");
ABSL_FLAG(bool,
          enable_fast_accelerate,
          false,
          "Enables jitter buffer fast accelerate");

namespace {

// Parses the input string for a valid SSRC (at the start of the string). If a
// valid SSRC is found, it is written to the output variable `ssrc`, and true is
// returned. Otherwise, false is returned.
bool ParseSsrc(absl::string_view str, uint32_t* ssrc) {
  if (str.empty())
    return true;
  int base = 10;
  // Look for "0x" or "0X" at the start and change base to 16 if found.
  if ((str.compare(0, 2, "0x") == 0) || (str.compare(0, 2, "0X") == 0))
    base = 16;
  errno = 0;
  char* end_ptr;
  std::string str_str = std::string(str);
  unsigned long value = strtoul(str_str.c_str(), &end_ptr, base);  // NOLINT
  if (value == ULONG_MAX && errno == ERANGE)
    return false;  // Value out of range for unsigned long.
  if (sizeof(unsigned long) > sizeof(uint32_t) && value > 0xFFFFFFFF)  // NOLINT
    return false;  // Value out of range for uint32_t.
  if (end_ptr - str_str.c_str() < static_cast<ptrdiff_t>(str.length()))
    return false;  // Part of the string was not parsed.
  *ssrc = static_cast<uint32_t>(value);
  return true;
}

static bool ValidateExtensionId(int value) {
  if (value > 0 && value <= 255)  // Value is ok.
    return true;
  printf("Extension ID must be between 1 and 255, not %d\n",
         static_cast<int>(value));
  return false;
}

// Flag validators.
bool ValidatePayloadType(int value) {
  if (value >= 0 && value <= 127)  // Value is ok.
    return true;
  printf("Payload type must be between 0 and 127, not %d\n",
         static_cast<int>(value));
  return false;
}

bool ValidateSsrcValue(absl::string_view str) {
  uint32_t dummy_ssrc;
  if (ParseSsrc(str, &dummy_ssrc))  // Value is ok.
    return true;
  printf("Invalid SSRC: %.*s\n", static_cast<int>(str.size()), str.data());
  return false;
}

void PrintCodecMappingEntry(absl::string_view codec, int flag) {
  std::cout << codec << ": " << flag << std::endl;
}

void PrintCodecMapping() {
  PrintCodecMappingEntry("PCM-u", absl::GetFlag(FLAGS_pcmu));
  PrintCodecMappingEntry("PCM-a", absl::GetFlag(FLAGS_pcma));
  PrintCodecMappingEntry("iLBC", absl::GetFlag(FLAGS_ilbc));
  PrintCodecMappingEntry("iSAC", absl::GetFlag(FLAGS_isac));
  PrintCodecMappingEntry("iSAC-swb (32 kHz)", absl::GetFlag(FLAGS_isac_swb));
  PrintCodecMappingEntry("Opus", absl::GetFlag(FLAGS_opus));
  PrintCodecMappingEntry("PCM16b-nb (8 kHz)", absl::GetFlag(FLAGS_pcm16b));
  PrintCodecMappingEntry("PCM16b-wb (16 kHz)", absl::GetFlag(FLAGS_pcm16b_wb));
  PrintCodecMappingEntry("PCM16b-swb32 (32 kHz)",
                         absl::GetFlag(FLAGS_pcm16b_swb32));
  PrintCodecMappingEntry("PCM16b-swb48 (48 kHz)",
                         absl::GetFlag(FLAGS_pcm16b_swb48));
  PrintCodecMappingEntry("G.722", absl::GetFlag(FLAGS_g722));
  PrintCodecMappingEntry("AVT/DTMF (8 kHz)", absl::GetFlag(FLAGS_avt));
  PrintCodecMappingEntry("AVT/DTMF (16 kHz)", absl::GetFlag(FLAGS_avt_16));
  PrintCodecMappingEntry("AVT/DTMF (32 kHz)", absl::GetFlag(FLAGS_avt_32));
  PrintCodecMappingEntry("AVT/DTMF (48 kHz)", absl::GetFlag(FLAGS_avt_48));
  PrintCodecMappingEntry("redundant audio (RED)", absl::GetFlag(FLAGS_red));
  PrintCodecMappingEntry("comfort noise (8 kHz)", absl::GetFlag(FLAGS_cn_nb));
  PrintCodecMappingEntry("comfort noise (16 kHz)", absl::GetFlag(FLAGS_cn_wb));
  PrintCodecMappingEntry("comfort noise (32 kHz)",
                         absl::GetFlag(FLAGS_cn_swb32));
  PrintCodecMappingEntry("comfort noise (48 kHz)",
                         absl::GetFlag(FLAGS_cn_swb48));
}

bool ValidateOutputFilesOptions(bool textlog,
                                bool plotting,
                                absl::string_view output_files_base_name,
                                absl::string_view output_audio_filename) {
  bool output_files_base_name_specified = !output_files_base_name.empty();
  if (!textlog && !plotting && output_files_base_name_specified) {
    std::cout << "Error: --output_files_base_name cannot be used without at "
                 "least one of the following flags: --textlog, --matlabplot, "
                 "--pythonplot."
              << std::endl;
    return false;
  }
  // Without `output_audio_filename`, `output_files_base_name` is required when
  // plotting output files must be generated (in order to form a valid output
  // file name).
  if (output_audio_filename.empty() && plotting &&
      !output_files_base_name_specified) {
    std::cout << "Error: when no output audio file is specified and "
                 "--matlabplot and/or --pythonplot are used, "
                 "--output_files_base_name must be also used."
              << std::endl;
    return false;
  }
  return true;
}

absl::optional<std::string> CreateOptionalOutputFileName(
    bool output_requested,
    absl::string_view basename,
    absl::string_view output_audio_filename,
    absl::string_view suffix) {
  if (!output_requested) {
    return absl::nullopt;
  }
  if (!basename.empty()) {
    // Override the automatic assignment.
    rtc::StringBuilder sb(basename);
    sb << suffix;
    return sb.str();
  }
  if (!output_audio_filename.empty()) {
    // Automatically assign name.
    rtc::StringBuilder sb(output_audio_filename);
    sb << suffix;
    return sb.str();
  }
  std::cout << "Error: invalid text log file parameters.";
  return absl::nullopt;
}

}  // namespace

int main(int argc, char* argv[]) {
  std::vector<char*> args = absl::ParseCommandLine(argc, argv);
  webrtc::test::NetEqTestFactory factory;
  std::string usage =
      "Tool for decoding an RTP dump file using NetEq.\n"
      "Example usage:\n"
      "./neteq_rtpplay input.rtp [output.{pcm, wav}]\n";
  if (absl::GetFlag(FLAGS_codec_map)) {
    PrintCodecMapping();
    exit(0);
  }
  if (args.size() != 2 &&
      args.size() != 3) {  // The output audio file is optional.
    // Print usage information.
    std::cout << usage;
    exit(0);
  }
  const std::string output_audio_filename((args.size() == 3) ? args[2] : "");
  const std::string output_files_base_name(
      absl::GetFlag(FLAGS_output_files_base_name));
  RTC_CHECK(ValidateOutputFilesOptions(
      absl::GetFlag(FLAGS_textlog),
      absl::GetFlag(FLAGS_matlabplot) || absl::GetFlag(FLAGS_pythonplot),
      output_files_base_name, output_audio_filename));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcmu)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcma)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_ilbc)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_isac)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_isac_swb)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_opus)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcm16b)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcm16b_wb)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcm16b_swb32)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_pcm16b_swb48)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_g722)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_avt)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_avt_16)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_avt_32)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_avt_48)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_red)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_cn_nb)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_cn_wb)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_cn_swb32)));
  RTC_CHECK(ValidatePayloadType(absl::GetFlag(FLAGS_cn_swb48)));
  RTC_CHECK(ValidateSsrcValue(absl::GetFlag(FLAGS_ssrc)));
  RTC_CHECK(ValidateExtensionId(absl::GetFlag(FLAGS_audio_level)));
  RTC_CHECK(ValidateExtensionId(absl::GetFlag(FLAGS_abs_send_time)));
  RTC_CHECK(ValidateExtensionId(absl::GetFlag(FLAGS_transport_seq_no)));
  RTC_CHECK(ValidateExtensionId(absl::GetFlag(FLAGS_video_content_type)));
  RTC_CHECK(ValidateExtensionId(absl::GetFlag(FLAGS_video_timing)));

  // Make force_fieldtrials persistent string during entire program live as
  // absl::GetFlag creates temporary string and c_str() will point to
  // deallocated string.
  const std::string force_fieldtrials = absl::GetFlag(FLAGS_force_fieldtrials);
  webrtc::field_trial::InitFieldTrialsFromString(force_fieldtrials.c_str());

  webrtc::test::NetEqTestFactory::Config config;
  config.pcmu = absl::GetFlag(FLAGS_pcmu);
  config.pcma = absl::GetFlag(FLAGS_pcma);
  config.ilbc = absl::GetFlag(FLAGS_ilbc);
  config.isac = absl::GetFlag(FLAGS_isac);
  config.isac_swb = absl::GetFlag(FLAGS_isac_swb);
  config.opus = absl::GetFlag(FLAGS_opus);
  config.pcm16b = absl::GetFlag(FLAGS_pcm16b);
  config.pcm16b_wb = absl::GetFlag(FLAGS_pcm16b_wb);
  config.pcm16b_swb32 = absl::GetFlag(FLAGS_pcm16b_swb32);
  config.pcm16b_swb48 = absl::GetFlag(FLAGS_pcm16b_swb48);
  config.g722 = absl::GetFlag(FLAGS_g722);
  config.avt = absl::GetFlag(FLAGS_avt);
  config.avt_16 = absl::GetFlag(FLAGS_avt_16);
  config.avt_32 = absl::GetFlag(FLAGS_avt_32);
  config.avt_48 = absl::GetFlag(FLAGS_avt_48);
  config.red = absl::GetFlag(FLAGS_red);
  config.cn_nb = absl::GetFlag(FLAGS_cn_nb);
  config.cn_wb = absl::GetFlag(FLAGS_cn_wb);
  config.cn_swb32 = absl::GetFlag(FLAGS_cn_swb32);
  config.cn_swb48 = absl::GetFlag(FLAGS_cn_swb48);
  config.replacement_audio_file = absl::GetFlag(FLAGS_replacement_audio_file);
  config.audio_level = absl::GetFlag(FLAGS_audio_level);
  config.abs_send_time = absl::GetFlag(FLAGS_abs_send_time);
  config.transport_seq_no = absl::GetFlag(FLAGS_transport_seq_no);
  config.video_content_type = absl::GetFlag(FLAGS_video_content_type);
  config.video_timing = absl::GetFlag(FLAGS_video_timing);
  config.matlabplot = absl::GetFlag(FLAGS_matlabplot);
  config.pythonplot = absl::GetFlag(FLAGS_pythonplot);
  config.concealment_events = absl::GetFlag(FLAGS_concealment_events);
  config.max_nr_packets_in_buffer =
      absl::GetFlag(FLAGS_max_nr_packets_in_buffer);
  config.enable_fast_accelerate = absl::GetFlag(FLAGS_enable_fast_accelerate);
  if (!output_audio_filename.empty()) {
    config.output_audio_filename = output_audio_filename;
  }
  config.textlog = absl::GetFlag(FLAGS_textlog);
  config.textlog_filename = CreateOptionalOutputFileName(
      absl::GetFlag(FLAGS_textlog), output_files_base_name,
      output_audio_filename, ".text_log.txt");
  config.plot_scripts_basename = CreateOptionalOutputFileName(
      absl::GetFlag(FLAGS_matlabplot) || absl::GetFlag(FLAGS_pythonplot),
      output_files_base_name, output_audio_filename, "");

  // Check if an SSRC value was provided.
  if (absl::GetFlag(FLAGS_ssrc).size() > 0) {
    uint32_t ssrc;
    RTC_CHECK(ParseSsrc(absl::GetFlag(FLAGS_ssrc), &ssrc))
        << "Flag verification has failed.";
    config.ssrc_filter = absl::make_optional(ssrc);
  }

  std::unique_ptr<webrtc::test::NetEqTest> test =
      factory.InitializeTestFromFile(/*input_filename=*/args[1],
                                     /*factory=*/nullptr, config);
  RTC_CHECK(test) << "ERROR: Unable to run test";
  test->Run();
  return 0;
}
