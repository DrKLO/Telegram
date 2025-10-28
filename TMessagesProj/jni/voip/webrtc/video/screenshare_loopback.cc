/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdio.h>

#include <memory>
#include <string>
#include <vector>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/types/optional.h"
#include "api/test/simulated_network.h"
#include "api/test/video_quality_test_fixture.h"
#include "api/transport/bitrate_settings.h"
#include "api/video_codecs/video_codec.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_encode.h"
#include "system_wrappers/include/field_trial.h"
#include "test/field_trial.h"
#include "test/gtest.h"
#include "test/run_test.h"
#include "test/test_flags.h"
#include "video/video_quality_test.h"

using ::webrtc::BitrateConstraints;
using ::webrtc::BuiltInNetworkBehaviorConfig;
using ::webrtc::InterLayerPredMode;
using ::webrtc::SdpVideoFormat;
using ::webrtc::VideoQualityTest;

// Flags common with video loopback, with different default values.
ABSL_FLAG(int, width, 1850, "Video width (crops source).");
size_t Width() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_width));
}

ABSL_FLAG(int, height, 1110, "Video height (crops source).");
size_t Height() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_height));
}

ABSL_FLAG(int, fps, 5, "Frames per second.");
int Fps() {
  return absl::GetFlag(FLAGS_fps);
}

ABSL_FLAG(int, min_bitrate, 50, "Call and stream min bitrate in kbps.");
int MinBitrateKbps() {
  return absl::GetFlag(FLAGS_min_bitrate);
}

ABSL_FLAG(int, start_bitrate, 300, "Call start bitrate in kbps.");
int StartBitrateKbps() {
  return absl::GetFlag(FLAGS_start_bitrate);
}

ABSL_FLAG(int, target_bitrate, 200, "Stream target bitrate in kbps.");
int TargetBitrateKbps() {
  return absl::GetFlag(FLAGS_target_bitrate);
}

ABSL_FLAG(int, max_bitrate, 1000, "Call and stream max bitrate in kbps.");
int MaxBitrateKbps() {
  return absl::GetFlag(FLAGS_max_bitrate);
}

ABSL_FLAG(int, num_temporal_layers, 2, "Number of temporal layers to use.");
int NumTemporalLayers() {
  return absl::GetFlag(FLAGS_num_temporal_layers);
}

// Flags common with video loopback, with equal default values.
ABSL_FLAG(std::string, codec, "VP8", "Video codec to use.");
std::string Codec() {
  return absl::GetFlag(FLAGS_codec);
}

ABSL_FLAG(std::string,
          rtc_event_log_name,
          "",
          "Filename for rtc event log. Two files "
          "with \"_send\" and \"_recv\" suffixes will be created.");
std::string RtcEventLogName() {
  return absl::GetFlag(FLAGS_rtc_event_log_name);
}

ABSL_FLAG(std::string,
          rtp_dump_name,
          "",
          "Filename for dumped received RTP stream.");
std::string RtpDumpName() {
  return absl::GetFlag(FLAGS_rtp_dump_name);
}

ABSL_FLAG(int,
          selected_tl,
          -1,
          "Temporal layer to show or analyze. -1 to disable filtering.");
int SelectedTL() {
  return absl::GetFlag(FLAGS_selected_tl);
}

ABSL_FLAG(
    int,
    duration,
    0,
    "Duration of the test in seconds. If 0, rendered will be shown instead.");
int DurationSecs() {
  return absl::GetFlag(FLAGS_duration);
}

ABSL_FLAG(std::string, output_filename, "", "Target graph data filename.");
std::string OutputFilename() {
  return absl::GetFlag(FLAGS_output_filename);
}

ABSL_FLAG(std::string,
          graph_title,
          "",
          "If empty, title will be generated automatically.");
std::string GraphTitle() {
  return absl::GetFlag(FLAGS_graph_title);
}

ABSL_FLAG(int, loss_percent, 0, "Percentage of packets randomly lost.");
int LossPercent() {
  return absl::GetFlag(FLAGS_loss_percent);
}

ABSL_FLAG(int,
          link_capacity,
          0,
          "Capacity (kbps) of the fake link. 0 means infinite.");
int LinkCapacityKbps() {
  return absl::GetFlag(FLAGS_link_capacity);
}

ABSL_FLAG(int, queue_size, 0, "Size of the bottleneck link queue in packets.");
int QueueSize() {
  return absl::GetFlag(FLAGS_queue_size);
}

ABSL_FLAG(int,
          avg_propagation_delay_ms,
          0,
          "Average link propagation delay in ms.");
int AvgPropagationDelayMs() {
  return absl::GetFlag(FLAGS_avg_propagation_delay_ms);
}

ABSL_FLAG(int,
          std_propagation_delay_ms,
          0,
          "Link propagation delay standard deviation in ms.");
int StdPropagationDelayMs() {
  return absl::GetFlag(FLAGS_std_propagation_delay_ms);
}

ABSL_FLAG(int, num_streams, 0, "Number of streams to show or analyze.");
int NumStreams() {
  return absl::GetFlag(FLAGS_num_streams);
}

ABSL_FLAG(int,
          selected_stream,
          0,
          "ID of the stream to show or analyze. "
          "Set to the number of streams to show them all.");
int SelectedStream() {
  return absl::GetFlag(FLAGS_selected_stream);
}

ABSL_FLAG(int, num_spatial_layers, 1, "Number of spatial layers to use.");
int NumSpatialLayers() {
  return absl::GetFlag(FLAGS_num_spatial_layers);
}

ABSL_FLAG(int,
          inter_layer_pred,
          0,
          "Inter-layer prediction mode. "
          "0 - enabled, 1 - disabled, 2 - enabled only for key pictures.");
InterLayerPredMode InterLayerPred() {
  if (absl::GetFlag(FLAGS_inter_layer_pred) == 0) {
    return webrtc::InterLayerPredMode::kOn;
  } else if (absl::GetFlag(FLAGS_inter_layer_pred) == 1) {
    return webrtc::InterLayerPredMode::kOff;
  } else {
    RTC_DCHECK_EQ(absl::GetFlag(FLAGS_inter_layer_pred), 2);
    return webrtc::InterLayerPredMode::kOnKeyPic;
  }
}

ABSL_FLAG(int,
          selected_sl,
          -1,
          "Spatial layer to show or analyze. -1 to disable filtering.");
int SelectedSL() {
  return absl::GetFlag(FLAGS_selected_sl);
}

ABSL_FLAG(std::string,
          stream0,
          "",
          "Comma separated values describing VideoStream for stream #0.");
std::string Stream0() {
  return absl::GetFlag(FLAGS_stream0);
}

ABSL_FLAG(std::string,
          stream1,
          "",
          "Comma separated values describing VideoStream for stream #1.");
std::string Stream1() {
  return absl::GetFlag(FLAGS_stream1);
}

ABSL_FLAG(std::string,
          sl0,
          "",
          "Comma separated values describing SpatialLayer for layer #0.");
std::string SL0() {
  return absl::GetFlag(FLAGS_sl0);
}

ABSL_FLAG(std::string,
          sl1,
          "",
          "Comma separated values describing SpatialLayer for layer #1.");
std::string SL1() {
  return absl::GetFlag(FLAGS_sl1);
}

ABSL_FLAG(std::string,
          encoded_frame_path,
          "",
          "The base path for encoded frame logs. Created files will have "
          "the form <encoded_frame_path>.<n>.(recv|send.<m>).ivf");
std::string EncodedFramePath() {
  return absl::GetFlag(FLAGS_encoded_frame_path);
}

ABSL_FLAG(bool, logs, false, "print logs to stderr");

ABSL_FLAG(bool, send_side_bwe, true, "Use send-side bandwidth estimation");

ABSL_FLAG(bool, generic_descriptor, false, "Use the generic frame descriptor.");

ABSL_FLAG(bool, allow_reordering, false, "Allow packet reordering to occur");

// Screenshare-specific flags.
ABSL_FLAG(int,
          min_transmit_bitrate,
          400,
          "Min transmit bitrate incl. padding.");
int MinTransmitBitrateKbps() {
  return absl::GetFlag(FLAGS_min_transmit_bitrate);
}

ABSL_FLAG(bool,
          generate_slides,
          false,
          "Whether to use randomly generated slides or read them from files.");
bool GenerateSlides() {
  return absl::GetFlag(FLAGS_generate_slides);
}

ABSL_FLAG(int,
          slide_change_interval,
          10,
          "Interval (in seconds) between simulated slide changes.");
int SlideChangeInterval() {
  return absl::GetFlag(FLAGS_slide_change_interval);
}

ABSL_FLAG(
    int,
    scroll_duration,
    0,
    "Duration (in seconds) during which a slide will be scrolled into place.");
int ScrollDuration() {
  return absl::GetFlag(FLAGS_scroll_duration);
}

ABSL_FLAG(std::string,
          slides,
          "",
          "Comma-separated list of *.yuv files to display as slides.");
std::vector<std::string> Slides() {
  std::vector<std::string> slides;
  std::string slides_list = absl::GetFlag(FLAGS_slides);
  rtc::tokenize(slides_list, ',', &slides);
  return slides;
}

void Loopback() {
  BuiltInNetworkBehaviorConfig pipe_config;
  pipe_config.loss_percent = LossPercent();
  pipe_config.link_capacity_kbps = LinkCapacityKbps();
  pipe_config.queue_length_packets = QueueSize();
  pipe_config.queue_delay_ms = AvgPropagationDelayMs();
  pipe_config.delay_standard_deviation_ms = StdPropagationDelayMs();
  pipe_config.allow_reordering = absl::GetFlag(FLAGS_allow_reordering);

  BitrateConstraints call_bitrate_config;
  call_bitrate_config.min_bitrate_bps = MinBitrateKbps() * 1000;
  call_bitrate_config.start_bitrate_bps = StartBitrateKbps() * 1000;
  call_bitrate_config.max_bitrate_bps = -1;  // Don't cap bandwidth estimate.

  VideoQualityTest::Params params;
  params.call.send_side_bwe = absl::GetFlag(FLAGS_send_side_bwe);
  params.call.generic_descriptor = absl::GetFlag(FLAGS_generic_descriptor);
  params.call.call_bitrate_config = call_bitrate_config;
  params.video[0].enabled = true;
  params.video[0].width = Width();
  params.video[0].height = Height();
  params.video[0].fps = Fps();
  params.video[0].min_bitrate_bps = MinBitrateKbps() * 1000;
  params.video[0].target_bitrate_bps = TargetBitrateKbps() * 1000;
  params.video[0].max_bitrate_bps = MaxBitrateKbps() * 1000;
  params.video[0].codec = Codec();
  params.video[0].num_temporal_layers = NumTemporalLayers();
  params.video[0].selected_tl = SelectedTL();
  params.video[0].min_transmit_bps = MinTransmitBitrateKbps() * 1000;
  params.screenshare[0].enabled = true;
  params.screenshare[0].generate_slides = GenerateSlides();
  params.screenshare[0].slide_change_interval = SlideChangeInterval();
  params.screenshare[0].scroll_duration = ScrollDuration();
  params.screenshare[0].slides = Slides();
  params.config = pipe_config;
  params.logging.rtc_event_log_name = RtcEventLogName();
  params.logging.rtp_dump_name = RtpDumpName();
  params.logging.encoded_frame_base_path = EncodedFramePath();

  if (NumStreams() > 1 && Stream0().empty() && Stream1().empty()) {
    params.ss[0].infer_streams = true;
  }

  std::vector<std::string> stream_descriptors;
  stream_descriptors.push_back(Stream0());
  stream_descriptors.push_back(Stream1());
  std::vector<std::string> SL_descriptors;
  SL_descriptors.push_back(SL0());
  SL_descriptors.push_back(SL1());
  VideoQualityTest::FillScalabilitySettings(
      &params, 0, stream_descriptors, NumStreams(), SelectedStream(),
      NumSpatialLayers(), SelectedSL(), InterLayerPred(), SL_descriptors);

  auto fixture = std::make_unique<VideoQualityTest>(nullptr);
  if (DurationSecs()) {
    fixture->RunWithAnalyzer(params);
  } else {
    fixture->RunWithRenderers(params);
  }
}

int main(int argc, char* argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  absl::ParseCommandLine(argc, argv);

  rtc::LogMessage::SetLogToStderr(absl::GetFlag(FLAGS_logs));

  // InitFieldTrialsFromString stores the char*, so the char array must outlive
  // the application.
  const std::string field_trials = absl::GetFlag(FLAGS_force_fieldtrials);
  webrtc::field_trial::InitFieldTrialsFromString(field_trials.c_str());

  webrtc::test::RunTest(Loopback);
  return 0;
}
