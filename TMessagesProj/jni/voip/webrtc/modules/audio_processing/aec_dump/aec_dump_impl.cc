/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_processing/aec_dump/aec_dump_impl.h"

#include <memory>
#include <utility>

#include "modules/audio_processing/aec_dump/aec_dump_factory.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"

namespace webrtc {

namespace {
void CopyFromConfigToEvent(const webrtc::InternalAPMConfig& config,
                           webrtc::audioproc::Config* pb_cfg) {
  pb_cfg->set_aec_enabled(config.aec_enabled);
  pb_cfg->set_aec_delay_agnostic_enabled(config.aec_delay_agnostic_enabled);
  pb_cfg->set_aec_drift_compensation_enabled(
      config.aec_drift_compensation_enabled);
  pb_cfg->set_aec_extended_filter_enabled(config.aec_extended_filter_enabled);
  pb_cfg->set_aec_suppression_level(config.aec_suppression_level);

  pb_cfg->set_aecm_enabled(config.aecm_enabled);
  pb_cfg->set_aecm_comfort_noise_enabled(config.aecm_comfort_noise_enabled);
  pb_cfg->set_aecm_routing_mode(config.aecm_routing_mode);

  pb_cfg->set_agc_enabled(config.agc_enabled);
  pb_cfg->set_agc_mode(config.agc_mode);
  pb_cfg->set_agc_limiter_enabled(config.agc_limiter_enabled);
  pb_cfg->set_noise_robust_agc_enabled(config.noise_robust_agc_enabled);

  pb_cfg->set_hpf_enabled(config.hpf_enabled);

  pb_cfg->set_ns_enabled(config.ns_enabled);
  pb_cfg->set_ns_level(config.ns_level);

  pb_cfg->set_transient_suppression_enabled(
      config.transient_suppression_enabled);

  pb_cfg->set_pre_amplifier_enabled(config.pre_amplifier_enabled);
  pb_cfg->set_pre_amplifier_fixed_gain_factor(
      config.pre_amplifier_fixed_gain_factor);

  pb_cfg->set_experiments_description(config.experiments_description);
}

}  // namespace

AecDumpImpl::AecDumpImpl(FileWrapper debug_file,
                         int64_t max_log_size_bytes,
                         rtc::TaskQueue* worker_queue)
    : debug_file_(std::move(debug_file)),
      num_bytes_left_for_log_(max_log_size_bytes),
      worker_queue_(worker_queue),
      capture_stream_info_(CreateWriteToFileTask()) {}

AecDumpImpl::~AecDumpImpl() {
  // Block until all tasks have finished running.
  rtc::Event thread_sync_event;
  worker_queue_->PostTask([&thread_sync_event] { thread_sync_event.Set(); });
  // Wait until the event has been signaled with .Set(). By then all
  // pending tasks will have finished.
  thread_sync_event.Wait(rtc::Event::kForever);
}

void AecDumpImpl::WriteInitMessage(const ProcessingConfig& api_format,
                                   int64_t time_now_ms) {
  auto task = CreateWriteToFileTask();
  auto* event = task->GetEvent();
  event->set_type(audioproc::Event::INIT);
  audioproc::Init* msg = event->mutable_init();

  msg->set_sample_rate(api_format.input_stream().sample_rate_hz());
  msg->set_output_sample_rate(api_format.output_stream().sample_rate_hz());
  msg->set_reverse_sample_rate(
      api_format.reverse_input_stream().sample_rate_hz());
  msg->set_reverse_output_sample_rate(
      api_format.reverse_output_stream().sample_rate_hz());

  msg->set_num_input_channels(
      static_cast<int32_t>(api_format.input_stream().num_channels()));
  msg->set_num_output_channels(
      static_cast<int32_t>(api_format.output_stream().num_channels()));
  msg->set_num_reverse_channels(
      static_cast<int32_t>(api_format.reverse_input_stream().num_channels()));
  msg->set_num_reverse_output_channels(
      api_format.reverse_output_stream().num_channels());
  msg->set_timestamp_ms(time_now_ms);

  worker_queue_->PostTask(std::move(task));
}

void AecDumpImpl::AddCaptureStreamInput(
    const AudioFrameView<const float>& src) {
  capture_stream_info_.AddInput(src);
}

void AecDumpImpl::AddCaptureStreamOutput(
    const AudioFrameView<const float>& src) {
  capture_stream_info_.AddOutput(src);
}

void AecDumpImpl::AddCaptureStreamInput(const int16_t* const data,
                                        int num_channels,
                                        int samples_per_channel) {
  capture_stream_info_.AddInput(data, num_channels, samples_per_channel);
}

void AecDumpImpl::AddCaptureStreamOutput(const int16_t* const data,
                                         int num_channels,
                                         int samples_per_channel) {
  capture_stream_info_.AddOutput(data, num_channels, samples_per_channel);
}

void AecDumpImpl::AddAudioProcessingState(const AudioProcessingState& state) {
  capture_stream_info_.AddAudioProcessingState(state);
}

void AecDumpImpl::WriteCaptureStreamMessage() {
  auto task = capture_stream_info_.GetTask();
  RTC_DCHECK(task);
  worker_queue_->PostTask(std::move(task));
  capture_stream_info_.SetTask(CreateWriteToFileTask());
}

void AecDumpImpl::WriteRenderStreamMessage(const int16_t* const data,
                                           int num_channels,
                                           int samples_per_channel) {
  auto task = CreateWriteToFileTask();
  auto* event = task->GetEvent();

  event->set_type(audioproc::Event::REVERSE_STREAM);
  audioproc::ReverseStream* msg = event->mutable_reverse_stream();
  const size_t data_size = sizeof(int16_t) * samples_per_channel * num_channels;
  msg->set_data(data, data_size);

  worker_queue_->PostTask(std::move(task));
}

void AecDumpImpl::WriteRenderStreamMessage(
    const AudioFrameView<const float>& src) {
  auto task = CreateWriteToFileTask();
  auto* event = task->GetEvent();

  event->set_type(audioproc::Event::REVERSE_STREAM);

  audioproc::ReverseStream* msg = event->mutable_reverse_stream();

  for (size_t i = 0; i < src.num_channels(); ++i) {
    const auto& channel_view = src.channel(i);
    msg->add_channel(channel_view.begin(), sizeof(float) * channel_view.size());
  }

  worker_queue_->PostTask(std::move(task));
}

void AecDumpImpl::WriteConfig(const InternalAPMConfig& config) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  auto task = CreateWriteToFileTask();
  auto* event = task->GetEvent();
  event->set_type(audioproc::Event::CONFIG);
  CopyFromConfigToEvent(config, event->mutable_config());
  worker_queue_->PostTask(std::move(task));
}

void AecDumpImpl::WriteRuntimeSetting(
    const AudioProcessing::RuntimeSetting& runtime_setting) {
  RTC_DCHECK_RUNS_SERIALIZED(&race_checker_);
  auto task = CreateWriteToFileTask();
  auto* event = task->GetEvent();
  event->set_type(audioproc::Event::RUNTIME_SETTING);
  audioproc::RuntimeSetting* setting = event->mutable_runtime_setting();
  switch (runtime_setting.type()) {
    case AudioProcessing::RuntimeSetting::Type::kCapturePreGain: {
      float x;
      runtime_setting.GetFloat(&x);
      setting->set_capture_pre_gain(x);
      break;
    }
    case AudioProcessing::RuntimeSetting::Type::
        kCustomRenderProcessingRuntimeSetting: {
      float x;
      runtime_setting.GetFloat(&x);
      setting->set_custom_render_processing_setting(x);
      break;
    }
    case AudioProcessing::RuntimeSetting::Type::kCaptureCompressionGain:
      // Runtime AGC1 compression gain is ignored.
      // TODO(http://bugs.webrtc.org/10432): Store compression gain in aecdumps.
      break;
    case AudioProcessing::RuntimeSetting::Type::kCaptureFixedPostGain: {
      float x;
      runtime_setting.GetFloat(&x);
      setting->set_capture_fixed_post_gain(x);
      break;
    }
    case AudioProcessing::RuntimeSetting::Type::kPlayoutVolumeChange: {
      int x;
      runtime_setting.GetInt(&x);
      setting->set_playout_volume_change(x);
      break;
    }
    case AudioProcessing::RuntimeSetting::Type::kPlayoutAudioDeviceChange: {
      AudioProcessing::RuntimeSetting::PlayoutAudioDeviceInfo src;
      runtime_setting.GetPlayoutAudioDeviceInfo(&src);
      auto* dst = setting->mutable_playout_audio_device_change();
      dst->set_id(src.id);
      dst->set_max_volume(src.max_volume);
      break;
    }
    case AudioProcessing::RuntimeSetting::Type::kNotSpecified:
      RTC_NOTREACHED();
      break;
  }
  worker_queue_->PostTask(std::move(task));
}

std::unique_ptr<WriteToFileTask> AecDumpImpl::CreateWriteToFileTask() {
  return std::make_unique<WriteToFileTask>(&debug_file_,
                                           &num_bytes_left_for_log_);
}

std::unique_ptr<AecDump> AecDumpFactory::Create(webrtc::FileWrapper file,
                                                int64_t max_log_size_bytes,
                                                rtc::TaskQueue* worker_queue) {
  RTC_DCHECK(worker_queue);
  if (!file.is_open())
    return nullptr;

  return std::make_unique<AecDumpImpl>(std::move(file), max_log_size_bytes,
                                       worker_queue);
}

std::unique_ptr<AecDump> AecDumpFactory::Create(std::string file_name,
                                                int64_t max_log_size_bytes,
                                                rtc::TaskQueue* worker_queue) {
  return Create(FileWrapper::OpenWriteOnly(file_name.c_str()),
                max_log_size_bytes, worker_queue);
}

std::unique_ptr<AecDump> AecDumpFactory::Create(FILE* handle,
                                                int64_t max_log_size_bytes,
                                                rtc::TaskQueue* worker_queue) {
  return Create(FileWrapper(handle), max_log_size_bytes, worker_queue);
}

}  // namespace webrtc
