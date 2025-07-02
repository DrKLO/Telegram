/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/audio_network_adaptor/debug_dump_writer.h"

#include <string>

#include "absl/types/optional.h"
#include "rtc_base/checks.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "rtc_base/system/file_wrapper.h"

#if WEBRTC_ENABLE_PROTOBUF
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/modules/audio_coding/audio_network_adaptor/debug_dump.pb.h"
#else
#include "modules/audio_coding/audio_network_adaptor/debug_dump.pb.h"
#endif
#endif

namespace webrtc {

#if WEBRTC_ENABLE_PROTOBUF
namespace {

using audio_network_adaptor::debug_dump::EncoderRuntimeConfig;
using audio_network_adaptor::debug_dump::Event;
using audio_network_adaptor::debug_dump::NetworkMetrics;

void DumpEventToFile(const Event& event, FileWrapper* dump_file) {
  RTC_CHECK(dump_file->is_open());
  std::string dump_data;
  event.SerializeToString(&dump_data);
  int32_t size = rtc::checked_cast<int32_t>(event.ByteSizeLong());
  dump_file->Write(&size, sizeof(size));
  dump_file->Write(dump_data.data(), dump_data.length());
}

}  // namespace
#endif  // WEBRTC_ENABLE_PROTOBUF

class DebugDumpWriterImpl final : public DebugDumpWriter {
 public:
  explicit DebugDumpWriterImpl(FILE* file_handle);
  ~DebugDumpWriterImpl() override = default;

  void DumpEncoderRuntimeConfig(const AudioEncoderRuntimeConfig& config,
                                int64_t timestamp) override;

  void DumpNetworkMetrics(const Controller::NetworkMetrics& metrics,
                          int64_t timestamp) override;

#if WEBRTC_ENABLE_PROTOBUF
  void DumpControllerManagerConfig(
      const audio_network_adaptor::config::ControllerManager&
          controller_manager_config,
      int64_t timestamp) override;
#endif

 private:
  FileWrapper dump_file_;
};

DebugDumpWriterImpl::DebugDumpWriterImpl(FILE* file_handle) {
#if WEBRTC_ENABLE_PROTOBUF
  dump_file_ = FileWrapper(file_handle);
  RTC_CHECK(dump_file_.is_open());
#else
  RTC_DCHECK_NOTREACHED();
#endif
}

void DebugDumpWriterImpl::DumpNetworkMetrics(
    const Controller::NetworkMetrics& metrics,
    int64_t timestamp) {
#if WEBRTC_ENABLE_PROTOBUF
  Event event;
  event.set_timestamp(timestamp);
  event.set_type(Event::NETWORK_METRICS);
  auto dump_metrics = event.mutable_network_metrics();

  if (metrics.uplink_bandwidth_bps)
    dump_metrics->set_uplink_bandwidth_bps(*metrics.uplink_bandwidth_bps);

  if (metrics.uplink_packet_loss_fraction) {
    dump_metrics->set_uplink_packet_loss_fraction(
        *metrics.uplink_packet_loss_fraction);
  }

  if (metrics.target_audio_bitrate_bps) {
    dump_metrics->set_target_audio_bitrate_bps(
        *metrics.target_audio_bitrate_bps);
  }

  if (metrics.rtt_ms)
    dump_metrics->set_rtt_ms(*metrics.rtt_ms);

  DumpEventToFile(event, &dump_file_);
#endif  // WEBRTC_ENABLE_PROTOBUF
}

void DebugDumpWriterImpl::DumpEncoderRuntimeConfig(
    const AudioEncoderRuntimeConfig& config,
    int64_t timestamp) {
#if WEBRTC_ENABLE_PROTOBUF
  Event event;
  event.set_timestamp(timestamp);
  event.set_type(Event::ENCODER_RUNTIME_CONFIG);
  auto dump_config = event.mutable_encoder_runtime_config();

  if (config.bitrate_bps)
    dump_config->set_bitrate_bps(*config.bitrate_bps);

  if (config.frame_length_ms)
    dump_config->set_frame_length_ms(*config.frame_length_ms);

  if (config.uplink_packet_loss_fraction) {
    dump_config->set_uplink_packet_loss_fraction(
        *config.uplink_packet_loss_fraction);
  }

  if (config.enable_fec)
    dump_config->set_enable_fec(*config.enable_fec);

  if (config.enable_dtx)
    dump_config->set_enable_dtx(*config.enable_dtx);

  if (config.num_channels)
    dump_config->set_num_channels(*config.num_channels);

  DumpEventToFile(event, &dump_file_);
#endif  // WEBRTC_ENABLE_PROTOBUF
}

#if WEBRTC_ENABLE_PROTOBUF
void DebugDumpWriterImpl::DumpControllerManagerConfig(
    const audio_network_adaptor::config::ControllerManager&
        controller_manager_config,
    int64_t timestamp) {
  Event event;
  event.set_timestamp(timestamp);
  event.set_type(Event::CONTROLLER_MANAGER_CONFIG);
  event.mutable_controller_manager_config()->CopyFrom(
      controller_manager_config);
  DumpEventToFile(event, &dump_file_);
}
#endif  // WEBRTC_ENABLE_PROTOBUF

std::unique_ptr<DebugDumpWriter> DebugDumpWriter::Create(FILE* file_handle) {
  return std::unique_ptr<DebugDumpWriter>(new DebugDumpWriterImpl(file_handle));
}

}  // namespace webrtc
