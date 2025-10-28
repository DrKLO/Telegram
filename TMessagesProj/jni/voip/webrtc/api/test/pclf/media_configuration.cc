/*
 *  Copyright 2022 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/test/pclf/media_configuration.h"

#include <string>
#include <utility>

#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/test/video/video_frame_writer.h"
#include "rtc_base/checks.h"
#include "rtc_base/strings/string_builder.h"
#include "test/pc/e2e/analyzer/video/video_dumping.h"
#include "test/testsupport/file_utils.h"
#include "test/testsupport/video_frame_writer.h"

namespace webrtc {
namespace webrtc_pc_e2e {
namespace {

absl::string_view SpecToString(VideoResolution::Spec spec) {
  switch (spec) {
    case VideoResolution::Spec::kNone:
      return "None";
    case VideoResolution::Spec::kMaxFromSender:
      return "MaxFromSender";
  }
}

void AppendResolution(const VideoResolution& resolution,
                      rtc::StringBuilder& builder) {
  builder << "_" << resolution.width() << "x" << resolution.height() << "_"
          << resolution.fps();
}

}  // namespace

ScreenShareConfig::ScreenShareConfig(TimeDelta slide_change_interval)
    : slide_change_interval(slide_change_interval) {
  RTC_CHECK_GT(slide_change_interval.ms(), 0);
}
VideoSimulcastConfig::VideoSimulcastConfig(int simulcast_streams_count)
    : simulcast_streams_count(simulcast_streams_count) {
  RTC_CHECK_GT(simulcast_streams_count, 1);
}
EmulatedSFUConfig::EmulatedSFUConfig(int target_layer_index)
    : target_layer_index(target_layer_index) {
  RTC_CHECK_GE(target_layer_index, 0);
}

EmulatedSFUConfig::EmulatedSFUConfig(absl::optional<int> target_layer_index,
                                     absl::optional<int> target_temporal_index)
    : target_layer_index(target_layer_index),
      target_temporal_index(target_temporal_index) {
  RTC_CHECK_GE(target_temporal_index.value_or(0), 0);
  if (target_temporal_index)
    RTC_CHECK_GE(*target_temporal_index, 0);
}

VideoResolution::VideoResolution(size_t width, size_t height, int32_t fps)
    : width_(width), height_(height), fps_(fps), spec_(Spec::kNone) {}
VideoResolution::VideoResolution(Spec spec)
    : width_(0), height_(0), fps_(0), spec_(spec) {}

bool VideoResolution::operator==(const VideoResolution& other) const {
  if (spec_ != Spec::kNone && spec_ == other.spec_) {
    // If there is some particular spec set, then it doesn't matter what
    // values we have in other fields.
    return true;
  }
  return width_ == other.width_ && height_ == other.height_ &&
         fps_ == other.fps_ && spec_ == other.spec_;
}
bool VideoResolution::operator!=(const VideoResolution& other) const {
  return !(*this == other);
}

bool VideoResolution::IsRegular() const {
  return spec_ == Spec::kNone;
}
std::string VideoResolution::ToString() const {
  rtc::StringBuilder out;
  out << "{ width=" << width_ << ", height=" << height_ << ", fps=" << fps_
      << ", spec=" << SpecToString(spec_) << " }";
  return out.Release();
}

VideoDumpOptions::VideoDumpOptions(
    absl::string_view output_directory,
    int sampling_modulo,
    bool export_frame_ids,
    std::function<std::unique_ptr<test::VideoFrameWriter>(
        absl::string_view file_name_prefix,
        const VideoResolution& resolution)> video_frame_writer_factory)
    : output_directory_(output_directory),
      sampling_modulo_(sampling_modulo),
      export_frame_ids_(export_frame_ids),
      video_frame_writer_factory_(video_frame_writer_factory) {
  RTC_CHECK_GT(sampling_modulo, 0);
}

VideoDumpOptions::VideoDumpOptions(absl::string_view output_directory,
                                   bool export_frame_ids)
    : VideoDumpOptions(output_directory,
                       kDefaultSamplingModulo,
                       export_frame_ids) {}

std::unique_ptr<test::VideoFrameWriter>
VideoDumpOptions::CreateInputDumpVideoFrameWriter(
    absl::string_view stream_label,
    const VideoResolution& resolution) const {
  std::unique_ptr<test::VideoFrameWriter> writer = video_frame_writer_factory_(
      GetInputDumpFileName(stream_label, resolution), resolution);
  absl::optional<std::string> frame_ids_file =
      GetInputFrameIdsDumpFileName(stream_label, resolution);
  if (frame_ids_file.has_value()) {
    writer = CreateVideoFrameWithIdsWriter(std::move(writer), *frame_ids_file);
  }
  return writer;
}

std::unique_ptr<test::VideoFrameWriter>
VideoDumpOptions::CreateOutputDumpVideoFrameWriter(
    absl::string_view stream_label,
    absl::string_view receiver,
    const VideoResolution& resolution) const {
  std::unique_ptr<test::VideoFrameWriter> writer = video_frame_writer_factory_(
      GetOutputDumpFileName(stream_label, receiver, resolution), resolution);
  absl::optional<std::string> frame_ids_file =
      GetOutputFrameIdsDumpFileName(stream_label, receiver, resolution);
  if (frame_ids_file.has_value()) {
    writer = CreateVideoFrameWithIdsWriter(std::move(writer), *frame_ids_file);
  }
  return writer;
}

std::unique_ptr<test::VideoFrameWriter>
VideoDumpOptions::Y4mVideoFrameWriterFactory(
    absl::string_view file_name_prefix,
    const VideoResolution& resolution) {
  return std::make_unique<test::Y4mVideoFrameWriterImpl>(
      std::string(file_name_prefix) + ".y4m", resolution.width(),
      resolution.height(), resolution.fps());
}

std::string VideoDumpOptions::GetInputDumpFileName(
    absl::string_view stream_label,
    const VideoResolution& resolution) const {
  rtc::StringBuilder file_name;
  file_name << stream_label;
  AppendResolution(resolution, file_name);
  return test::JoinFilename(output_directory_, file_name.Release());
}

absl::optional<std::string> VideoDumpOptions::GetInputFrameIdsDumpFileName(
    absl::string_view stream_label,
    const VideoResolution& resolution) const {
  if (!export_frame_ids_) {
    return absl::nullopt;
  }
  return GetInputDumpFileName(stream_label, resolution) + ".frame_ids.txt";
}

std::string VideoDumpOptions::GetOutputDumpFileName(
    absl::string_view stream_label,
    absl::string_view receiver,
    const VideoResolution& resolution) const {
  rtc::StringBuilder file_name;
  file_name << stream_label << "_" << receiver;
  AppendResolution(resolution, file_name);
  return test::JoinFilename(output_directory_, file_name.Release());
}

absl::optional<std::string> VideoDumpOptions::GetOutputFrameIdsDumpFileName(
    absl::string_view stream_label,
    absl::string_view receiver,
    const VideoResolution& resolution) const {
  if (!export_frame_ids_) {
    return absl::nullopt;
  }
  return GetOutputDumpFileName(stream_label, receiver, resolution) +
         ".frame_ids.txt";
}

std::string VideoDumpOptions::ToString() const {
  rtc::StringBuilder out;
  out << "{ output_directory_=" << output_directory_
      << ", sampling_modulo_=" << sampling_modulo_
      << ", export_frame_ids_=" << export_frame_ids_ << " }";
  return out.Release();
}

VideoConfig::VideoConfig(const VideoResolution& resolution)
    : width(resolution.width()),
      height(resolution.height()),
      fps(resolution.fps()) {
  RTC_CHECK(resolution.IsRegular());
}
VideoConfig::VideoConfig(size_t width, size_t height, int32_t fps)
    : width(width), height(height), fps(fps) {}
VideoConfig::VideoConfig(absl::string_view stream_label,
                         size_t width,
                         size_t height,
                         int32_t fps)
    : width(width), height(height), fps(fps), stream_label(stream_label) {}

AudioConfig::AudioConfig(absl::string_view stream_label)
    : stream_label(stream_label) {}

VideoCodecConfig::VideoCodecConfig(absl::string_view name)
    : name(name), required_params() {}

VideoCodecConfig::VideoCodecConfig(
    absl::string_view name,
    std::map<std::string, std::string> required_params)
    : name(name), required_params(std::move(required_params)) {}

absl::optional<VideoResolution> VideoSubscription::GetMaxResolution(
    rtc::ArrayView<const VideoConfig> video_configs) {
  std::vector<VideoResolution> resolutions;
  for (const auto& video_config : video_configs) {
    resolutions.push_back(video_config.GetResolution());
  }
  return GetMaxResolution(resolutions);
}

absl::optional<VideoResolution> VideoSubscription::GetMaxResolution(
    rtc::ArrayView<const VideoResolution> resolutions) {
  if (resolutions.empty()) {
    return absl::nullopt;
  }

  VideoResolution max_resolution;
  for (const VideoResolution& resolution : resolutions) {
    if (max_resolution.width() < resolution.width()) {
      max_resolution.set_width(resolution.width());
    }
    if (max_resolution.height() < resolution.height()) {
      max_resolution.set_height(resolution.height());
    }
    if (max_resolution.fps() < resolution.fps()) {
      max_resolution.set_fps(resolution.fps());
    }
  }
  return max_resolution;
}

bool VideoSubscription::operator==(const VideoSubscription& other) const {
  return default_resolution_ == other.default_resolution_ &&
         peers_resolution_ == other.peers_resolution_;
}
bool VideoSubscription::operator!=(const VideoSubscription& other) const {
  return !(*this == other);
}

VideoSubscription& VideoSubscription::SubscribeToPeer(
    absl::string_view peer_name,
    VideoResolution resolution) {
  peers_resolution_[std::string(peer_name)] = resolution;
  return *this;
}

VideoSubscription& VideoSubscription::SubscribeToAllPeers(
    VideoResolution resolution) {
  default_resolution_ = resolution;
  return *this;
}

absl::optional<VideoResolution> VideoSubscription::GetResolutionForPeer(
    absl::string_view peer_name) const {
  auto it = peers_resolution_.find(std::string(peer_name));
  if (it == peers_resolution_.end()) {
    return default_resolution_;
  }
  return it->second;
}

std::vector<std::string> VideoSubscription::GetSubscribedPeers() const {
  std::vector<std::string> subscribed_streams;
  subscribed_streams.reserve(peers_resolution_.size());
  for (const auto& entry : peers_resolution_) {
    subscribed_streams.push_back(entry.first);
  }
  return subscribed_streams;
}

std::string VideoSubscription::ToString() const {
  rtc::StringBuilder out;
  out << "{ default_resolution_=[";
  if (default_resolution_.has_value()) {
    out << default_resolution_->ToString();
  } else {
    out << "undefined";
  }
  out << "], {";
  for (const auto& [peer_name, resolution] : peers_resolution_) {
    out << "[" << peer_name << ": " << resolution.ToString() << "], ";
  }
  out << "} }";
  return out.Release();
}
}  // namespace webrtc_pc_e2e
}  // namespace webrtc
