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

// Flags for video.
ABSL_FLAG(int, vwidth, 640, "Video width.");

ABSL_FLAG(int, vheight, 480, "Video height.");

ABSL_FLAG(int, vfps, 30, "Video frames per second.");

ABSL_FLAG(int,
          capture_device_index,
          0,
          "Capture device to select for video stream");

ABSL_FLAG(int, vtarget_bitrate, 400, "Video stream target bitrate in kbps.");

ABSL_FLAG(int, vmin_bitrate, 100, "Video stream min bitrate in kbps.");

ABSL_FLAG(int, vmax_bitrate, 2000, "Video stream max bitrate in kbps.");

ABSL_FLAG(bool,
          suspend_below_min_bitrate,
          false,
          "Suspends video below the configured min bitrate.");

ABSL_FLAG(int,
          vnum_temporal_layers,
          1,
          "Number of temporal layers for video. Set to 1-4 to override.");

ABSL_FLAG(int, vnum_streams, 0, "Number of video streams to show or analyze.");

ABSL_FLAG(int,
          vnum_spatial_layers,
          1,
          "Number of video spatial layers to use.");

ABSL_FLAG(int,
          vinter_layer_pred,
          2,
          "Video inter-layer prediction mode. "
          "0 - enabled, 1 - disabled, 2 - enabled only for key pictures.");

ABSL_FLAG(std::string,
          vstream0,
          "",
          "Comma separated values describing VideoStream for video stream #0.");

ABSL_FLAG(std::string,
          vstream1,
          "",
          "Comma separated values describing VideoStream for video stream #1.");

ABSL_FLAG(std::string,
          vsl0,
          "",
          "Comma separated values describing SpatialLayer for video layer #0.");

ABSL_FLAG(std::string,
          vsl1,
          "",
          "Comma separated values describing SpatialLayer for video layer #1.");

ABSL_FLAG(int,
          vselected_tl,
          -1,
          "Temporal layer to show or analyze for screenshare. -1 to disable "
          "filtering.");

ABSL_FLAG(int,
          vselected_stream,
          0,
          "ID of the stream to show or analyze for screenshare."
          "Set to the number of streams to show them all.");

ABSL_FLAG(int,
          vselected_sl,
          -1,
          "Spatial layer to show or analyze for screenshare. -1 to disable "
          "filtering.");

// Flags for screenshare.
ABSL_FLAG(int,
          min_transmit_bitrate,
          400,
          "Min transmit bitrate incl. padding for screenshare.");

ABSL_FLAG(int, swidth, 1850, "Screenshare width (crops source).");

ABSL_FLAG(int, sheight, 1110, "Screenshare height (crops source).");

ABSL_FLAG(int, sfps, 5, "Frames per second for screenshare.");

ABSL_FLAG(int,
          starget_bitrate,
          100,
          "Screenshare stream target bitrate in kbps.");

ABSL_FLAG(int, smin_bitrate, 100, "Screenshare stream min bitrate in kbps.");

ABSL_FLAG(int, smax_bitrate, 2000, "Screenshare stream max bitrate in kbps.");

ABSL_FLAG(int,
          snum_temporal_layers,
          2,
          "Number of temporal layers to use in screenshare.");

ABSL_FLAG(int,
          snum_streams,
          0,
          "Number of screenshare streams to show or analyze.");

ABSL_FLAG(int,
          snum_spatial_layers,
          1,
          "Number of screenshare spatial layers to use.");

ABSL_FLAG(int,
          sinter_layer_pred,
          0,
          "Screenshare inter-layer prediction mode. "
          "0 - enabled, 1 - disabled, 2 - enabled only for key pictures.");

ABSL_FLAG(
    std::string,
    sstream0,
    "",
    "Comma separated values describing VideoStream for screenshare stream #0.");

ABSL_FLAG(
    std::string,
    sstream1,
    "",
    "Comma separated values describing VideoStream for screenshare stream #1.");

ABSL_FLAG(
    std::string,
    ssl0,
    "",
    "Comma separated values describing SpatialLayer for screenshare layer #0.");

ABSL_FLAG(
    std::string,
    ssl1,
    "",
    "Comma separated values describing SpatialLayer for screenshare layer #1.");

ABSL_FLAG(int,
          sselected_tl,
          -1,
          "Temporal layer to show or analyze for screenshare. -1 to disable "
          "filtering.");

ABSL_FLAG(int,
          sselected_stream,
          0,
          "ID of the stream to show or analyze for screenshare."
          "Set to the number of streams to show them all.");

ABSL_FLAG(int,
          sselected_sl,
          -1,
          "Spatial layer to show or analyze for screenshare. -1 to disable "
          "filtering.");

ABSL_FLAG(bool,
          generate_slides,
          false,
          "Whether to use randomly generated slides or read them from files.");

ABSL_FLAG(int,
          slide_change_interval,
          10,
          "Interval (in seconds) between simulated slide changes.");

ABSL_FLAG(
    int,
    scroll_duration,
    0,
    "Duration (in seconds) during which a slide will be scrolled into place.");

ABSL_FLAG(std::string,
          slides,
          "",
          "Comma-separated list of *.yuv files to display as slides.");

// Flags common with screenshare and video loopback, with equal default values.
ABSL_FLAG(int, start_bitrate, 600, "Call start bitrate in kbps.");

ABSL_FLAG(std::string, codec, "VP8", "Video codec to use.");

ABSL_FLAG(bool,
          analyze_video,
          false,
          "Analyze video stream (if --duration is present)");

ABSL_FLAG(bool,
          analyze_screenshare,
          false,
          "Analyze screenshare stream (if --duration is present)");

ABSL_FLAG(
    int,
    duration,
    0,
    "Duration of the test in seconds. If 0, rendered will be shown instead.");

ABSL_FLAG(std::string, output_filename, "", "Target graph data filename.");

ABSL_FLAG(std::string,
          graph_title,
          "",
          "If empty, title will be generated automatically.");

ABSL_FLAG(int, loss_percent, 0, "Percentage of packets randomly lost.");

ABSL_FLAG(int,
          avg_burst_loss_length,
          -1,
          "Average burst length of lost packets.");

ABSL_FLAG(int,
          link_capacity,
          0,
          "Capacity (kbps) of the fake link. 0 means infinite.");

ABSL_FLAG(int, queue_size, 0, "Size of the bottleneck link queue in packets.");

ABSL_FLAG(int,
          avg_propagation_delay_ms,
          0,
          "Average link propagation delay in ms.");

ABSL_FLAG(std::string,
          rtc_event_log_name,
          "",
          "Filename for rtc event log. Two files "
          "with \"_send\" and \"_recv\" suffixes will be created. "
          "Works only when --duration is set.");

ABSL_FLAG(std::string,
          rtp_dump_name,
          "",
          "Filename for dumped received RTP stream.");

ABSL_FLAG(int,
          std_propagation_delay_ms,
          0,
          "Link propagation delay standard deviation in ms.");

ABSL_FLAG(std::string,
          encoded_frame_path,
          "",
          "The base path for encoded frame logs. Created files will have "
          "the form <encoded_frame_path>.<n>.(recv|send.<m>).ivf");

ABSL_FLAG(bool, logs, false, "print logs to stderr");

ABSL_FLAG(bool, send_side_bwe, true, "Use send-side bandwidth estimation");

ABSL_FLAG(bool, generic_descriptor, false, "Use the generic frame descriptor.");

ABSL_FLAG(bool, allow_reordering, false, "Allow packet reordering to occur");

ABSL_FLAG(bool, use_ulpfec, false, "Use RED+ULPFEC forward error correction.");

ABSL_FLAG(bool, use_flexfec, false, "Use FlexFEC forward error correction.");

ABSL_FLAG(bool, audio, false, "Add audio stream");

ABSL_FLAG(bool,
          audio_video_sync,
          false,
          "Sync audio and video stream (no effect if"
          " audio is false)");

ABSL_FLAG(bool,
          audio_dtx,
          false,
          "Enable audio DTX (no effect if audio is false)");

ABSL_FLAG(bool, video, true, "Add video stream");

// Video-specific flags.
ABSL_FLAG(std::string,
          vclip,
          "",
          "Name of the clip to show. If empty, the camera is used. Use "
          "\"Generator\" for chroma generator.");

namespace webrtc {
namespace {

InterLayerPredMode IntToInterLayerPredMode(int inter_layer_pred) {
  if (inter_layer_pred == 0) {
    return InterLayerPredMode::kOn;
  } else if (inter_layer_pred == 1) {
    return InterLayerPredMode::kOff;
  } else {
    RTC_DCHECK_EQ(inter_layer_pred, 2);
    return InterLayerPredMode::kOnKeyPic;
  }
}

size_t VideoWidth() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_vwidth));
}

size_t VideoHeight() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_vheight));
}

int VideoFps() {
  return absl::GetFlag(FLAGS_vfps);
}

size_t GetCaptureDevice() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_capture_device_index));
}

int VideoTargetBitrateKbps() {
  return absl::GetFlag(FLAGS_vtarget_bitrate);
}

int VideoMinBitrateKbps() {
  return absl::GetFlag(FLAGS_vmin_bitrate);
}

int VideoMaxBitrateKbps() {
  return absl::GetFlag(FLAGS_vmax_bitrate);
}

int VideoNumTemporalLayers() {
  return absl::GetFlag(FLAGS_vnum_temporal_layers);
}

int VideoNumStreams() {
  return absl::GetFlag(FLAGS_vnum_streams);
}

int VideoNumSpatialLayers() {
  return absl::GetFlag(FLAGS_vnum_spatial_layers);
}

InterLayerPredMode VideoInterLayerPred() {
  return IntToInterLayerPredMode(absl::GetFlag(FLAGS_vinter_layer_pred));
}

std::string VideoStream0() {
  return absl::GetFlag(FLAGS_vstream0);
}

std::string VideoStream1() {
  return absl::GetFlag(FLAGS_vstream1);
}

std::string VideoSL0() {
  return absl::GetFlag(FLAGS_vsl0);
}

std::string VideoSL1() {
  return absl::GetFlag(FLAGS_vsl1);
}

int VideoSelectedTL() {
  return absl::GetFlag(FLAGS_vselected_tl);
}

int VideoSelectedStream() {
  return absl::GetFlag(FLAGS_vselected_stream);
}

int VideoSelectedSL() {
  return absl::GetFlag(FLAGS_vselected_sl);
}

int ScreenshareMinTransmitBitrateKbps() {
  return absl::GetFlag(FLAGS_min_transmit_bitrate);
}

size_t ScreenshareWidth() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_swidth));
}

size_t ScreenshareHeight() {
  return static_cast<size_t>(absl::GetFlag(FLAGS_sheight));
}

int ScreenshareFps() {
  return absl::GetFlag(FLAGS_sfps);
}

int ScreenshareTargetBitrateKbps() {
  return absl::GetFlag(FLAGS_starget_bitrate);
}

int ScreenshareMinBitrateKbps() {
  return absl::GetFlag(FLAGS_smin_bitrate);
}

int ScreenshareMaxBitrateKbps() {
  return absl::GetFlag(FLAGS_smax_bitrate);
}

int ScreenshareNumTemporalLayers() {
  return absl::GetFlag(FLAGS_snum_temporal_layers);
}

int ScreenshareNumStreams() {
  return absl::GetFlag(FLAGS_snum_streams);
}

int ScreenshareNumSpatialLayers() {
  return absl::GetFlag(FLAGS_snum_spatial_layers);
}

InterLayerPredMode ScreenshareInterLayerPred() {
  return IntToInterLayerPredMode(absl::GetFlag(FLAGS_sinter_layer_pred));
}

std::string ScreenshareStream0() {
  return absl::GetFlag(FLAGS_sstream0);
}

std::string ScreenshareStream1() {
  return absl::GetFlag(FLAGS_sstream1);
}

std::string ScreenshareSL0() {
  return absl::GetFlag(FLAGS_ssl0);
}

std::string ScreenshareSL1() {
  return absl::GetFlag(FLAGS_ssl1);
}

int ScreenshareSelectedTL() {
  return absl::GetFlag(FLAGS_sselected_tl);
}

int ScreenshareSelectedStream() {
  return absl::GetFlag(FLAGS_sselected_stream);
}

int ScreenshareSelectedSL() {
  return absl::GetFlag(FLAGS_sselected_sl);
}

bool GenerateSlides() {
  return absl::GetFlag(FLAGS_generate_slides);
}

int SlideChangeInterval() {
  return absl::GetFlag(FLAGS_slide_change_interval);
}

int ScrollDuration() {
  return absl::GetFlag(FLAGS_scroll_duration);
}

std::vector<std::string> Slides() {
  std::vector<std::string> slides;
  std::string slides_list = absl::GetFlag(FLAGS_slides);
  rtc::tokenize(slides_list, ',', &slides);
  return slides;
}

int StartBitrateKbps() {
  return absl::GetFlag(FLAGS_start_bitrate);
}

std::string Codec() {
  return absl::GetFlag(FLAGS_codec);
}

bool AnalyzeVideo() {
  return absl::GetFlag(FLAGS_analyze_video);
}

bool AnalyzeScreenshare() {
  return absl::GetFlag(FLAGS_analyze_screenshare);
}

int DurationSecs() {
  return absl::GetFlag(FLAGS_duration);
}

std::string OutputFilename() {
  return absl::GetFlag(FLAGS_output_filename);
}

std::string GraphTitle() {
  return absl::GetFlag(FLAGS_graph_title);
}

int LossPercent() {
  return absl::GetFlag(FLAGS_loss_percent);
}

int AvgBurstLossLength() {
  return absl::GetFlag(FLAGS_avg_burst_loss_length);
}

int LinkCapacityKbps() {
  return absl::GetFlag(FLAGS_link_capacity);
}

int QueueSize() {
  return absl::GetFlag(FLAGS_queue_size);
}

int AvgPropagationDelayMs() {
  return absl::GetFlag(FLAGS_avg_propagation_delay_ms);
}

std::string RtcEventLogName() {
  return absl::GetFlag(FLAGS_rtc_event_log_name);
}

std::string RtpDumpName() {
  return absl::GetFlag(FLAGS_rtp_dump_name);
}

int StdPropagationDelayMs() {
  return absl::GetFlag(FLAGS_std_propagation_delay_ms);
}

std::string EncodedFramePath() {
  return absl::GetFlag(FLAGS_encoded_frame_path);
}

std::string VideoClip() {
  return absl::GetFlag(FLAGS_vclip);
}

}  // namespace

void Loopback() {
  int camera_idx, screenshare_idx;
  RTC_CHECK(!(AnalyzeScreenshare() && AnalyzeVideo()))
      << "Select only one of video or screenshare.";
  RTC_CHECK(!DurationSecs() || AnalyzeScreenshare() || AnalyzeVideo())
      << "If duration is set, exactly one of analyze_* flags should be set.";
  // Default: camera feed first, if nothing selected.
  if (AnalyzeVideo() || !AnalyzeScreenshare()) {
    camera_idx = 0;
    screenshare_idx = 1;
  } else {
    camera_idx = 1;
    screenshare_idx = 0;
  }

  BuiltInNetworkBehaviorConfig pipe_config;
  pipe_config.loss_percent = LossPercent();
  pipe_config.avg_burst_loss_length = AvgBurstLossLength();
  pipe_config.link_capacity_kbps = LinkCapacityKbps();
  pipe_config.queue_length_packets = QueueSize();
  pipe_config.queue_delay_ms = AvgPropagationDelayMs();
  pipe_config.delay_standard_deviation_ms = StdPropagationDelayMs();
  pipe_config.allow_reordering = absl::GetFlag(FLAGS_allow_reordering);

  BitrateConstraints call_bitrate_config;
  call_bitrate_config.min_bitrate_bps =
      (ScreenshareMinBitrateKbps() + VideoMinBitrateKbps()) * 1000;
  call_bitrate_config.start_bitrate_bps = StartBitrateKbps() * 1000;
  call_bitrate_config.max_bitrate_bps =
      (ScreenshareMaxBitrateKbps() + VideoMaxBitrateKbps()) * 1000;

  VideoQualityTest::Params params;
  params.call.send_side_bwe = absl::GetFlag(FLAGS_send_side_bwe);
  params.call.generic_descriptor = absl::GetFlag(FLAGS_generic_descriptor);
  params.call.call_bitrate_config = call_bitrate_config;
  params.call.dual_video = true;
  params.video[screenshare_idx].enabled = true;
  params.video[screenshare_idx].width = ScreenshareWidth();
  params.video[screenshare_idx].height = ScreenshareHeight();
  params.video[screenshare_idx].fps = ScreenshareFps();
  params.video[screenshare_idx].min_bitrate_bps =
      ScreenshareMinBitrateKbps() * 1000;
  params.video[screenshare_idx].target_bitrate_bps =
      ScreenshareTargetBitrateKbps() * 1000;
  params.video[screenshare_idx].max_bitrate_bps =
      ScreenshareMaxBitrateKbps() * 1000;
  params.video[screenshare_idx].codec = Codec();
  params.video[screenshare_idx].num_temporal_layers =
      ScreenshareNumTemporalLayers();
  params.video[screenshare_idx].selected_tl = ScreenshareSelectedTL();
  params.video[screenshare_idx].min_transmit_bps =
      ScreenshareMinTransmitBitrateKbps() * 1000;
  params.video[camera_idx].enabled = absl::GetFlag(FLAGS_video);
  params.video[camera_idx].width = VideoWidth();
  params.video[camera_idx].height = VideoHeight();
  params.video[camera_idx].fps = VideoFps();
  params.video[camera_idx].min_bitrate_bps = VideoMinBitrateKbps() * 1000;
  params.video[camera_idx].target_bitrate_bps = VideoTargetBitrateKbps() * 1000;
  params.video[camera_idx].max_bitrate_bps = VideoMaxBitrateKbps() * 1000;
  params.video[camera_idx].suspend_below_min_bitrate =
      absl::GetFlag(FLAGS_suspend_below_min_bitrate);
  params.video[camera_idx].codec = Codec();
  params.video[camera_idx].num_temporal_layers = VideoNumTemporalLayers();
  params.video[camera_idx].selected_tl = VideoSelectedTL();
  params.video[camera_idx].ulpfec = absl::GetFlag(FLAGS_use_ulpfec);
  params.video[camera_idx].flexfec = absl::GetFlag(FLAGS_use_flexfec);
  params.video[camera_idx].clip_path = VideoClip();
  params.video[camera_idx].capture_device_index = GetCaptureDevice();
  params.audio.enabled = absl::GetFlag(FLAGS_audio);
  params.audio.sync_video = absl::GetFlag(FLAGS_audio_video_sync);
  params.audio.dtx = absl::GetFlag(FLAGS_audio_dtx);
  params.logging.rtc_event_log_name = RtcEventLogName();
  params.logging.rtp_dump_name = RtpDumpName();
  params.logging.encoded_frame_base_path = EncodedFramePath();
  params.analyzer.test_label = "dual_streams";
  params.analyzer.test_durations_secs = DurationSecs();
  params.analyzer.graph_data_output_filename = OutputFilename();
  params.analyzer.graph_title = GraphTitle();
  params.config = pipe_config;

  params.screenshare[camera_idx].enabled = false;
  params.screenshare[screenshare_idx].enabled = true;
  params.screenshare[screenshare_idx].generate_slides = GenerateSlides();
  params.screenshare[screenshare_idx].slide_change_interval =
      SlideChangeInterval();
  params.screenshare[screenshare_idx].scroll_duration = ScrollDuration();
  params.screenshare[screenshare_idx].slides = Slides();

  if (VideoNumStreams() > 1 && VideoStream0().empty() &&
      VideoStream1().empty()) {
    params.ss[camera_idx].infer_streams = true;
  }

  if (ScreenshareNumStreams() > 1 && ScreenshareStream0().empty() &&
      ScreenshareStream1().empty()) {
    params.ss[screenshare_idx].infer_streams = true;
  }

  std::vector<std::string> stream_descriptors;
  stream_descriptors.push_back(ScreenshareStream0());
  stream_descriptors.push_back(ScreenshareStream1());
  std::vector<std::string> SL_descriptors;
  SL_descriptors.push_back(ScreenshareSL0());
  SL_descriptors.push_back(ScreenshareSL1());
  VideoQualityTest::FillScalabilitySettings(
      &params, screenshare_idx, stream_descriptors, ScreenshareNumStreams(),
      ScreenshareSelectedStream(), ScreenshareNumSpatialLayers(),
      ScreenshareSelectedSL(), ScreenshareInterLayerPred(), SL_descriptors);

  stream_descriptors.clear();
  stream_descriptors.push_back(VideoStream0());
  stream_descriptors.push_back(VideoStream1());
  SL_descriptors.clear();
  SL_descriptors.push_back(VideoSL0());
  SL_descriptors.push_back(VideoSL1());
  VideoQualityTest::FillScalabilitySettings(
      &params, camera_idx, stream_descriptors, VideoNumStreams(),
      VideoSelectedStream(), VideoNumSpatialLayers(), VideoSelectedSL(),
      VideoInterLayerPred(), SL_descriptors);

  auto fixture = std::make_unique<VideoQualityTest>(nullptr);
  if (DurationSecs()) {
    fixture->RunWithAnalyzer(params);
  } else {
    fixture->RunWithRenderers(params);
  }
}
}  // namespace webrtc

int main(int argc, char* argv[]) {
  ::testing::InitGoogleTest(&argc, argv);
  absl::ParseCommandLine(argc, argv);

  rtc::LogMessage::SetLogToStderr(absl::GetFlag(FLAGS_logs));

  // InitFieldTrialsFromString stores the char*, so the char array must outlive
  // the application.
  const std::string field_trials = absl::GetFlag(FLAGS_force_fieldtrials);
  webrtc::field_trial::InitFieldTrialsFromString(field_trials.c_str());

  webrtc::test::RunTest(webrtc::Loopback);
  return 0;
}
