/*
 *  Copyright (c) 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "video/frame_dumping_encoder.h"

#include <map>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/sequence_checker.h"
#include "api/video/video_codec_type.h"
#include "modules/video_coding/utility/ivf_file_writer.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/file_wrapper.h"
#include "rtc_base/time_utils.h"

namespace webrtc {
namespace {

constexpr auto kEncoderDataDumpDirectoryFieldTrial =
    "WebRTC-EncoderDataDumpDirectory";

class FrameDumpingEncoder : public VideoEncoder, public EncodedImageCallback {
 public:
  FrameDumpingEncoder(std::unique_ptr<VideoEncoder> wrapped,
                      int64_t origin_time_micros,
                      std::string output_directory)
      : wrapped_(std::move(wrapped)),
        output_directory_(output_directory),
        origin_time_micros_(origin_time_micros) {}

  ~FrameDumpingEncoder() override {
    MutexLock lock(&mu_);
    writers_by_simulcast_index_.clear();
  }

  // VideoEncoder overloads.
  void SetFecControllerOverride(
      FecControllerOverride* fec_controller_override) override {
    wrapped_->SetFecControllerOverride(fec_controller_override);
  }
  int InitEncode(const VideoCodec* codec_settings,
                 const VideoEncoder::Settings& settings) override {
    codec_settings_ = *codec_settings;
    return wrapped_->InitEncode(codec_settings, settings);
  }
  int32_t RegisterEncodeCompleteCallback(
      EncodedImageCallback* callback) override {
    callback_ = callback;
    return wrapped_->RegisterEncodeCompleteCallback(this);
  }
  int32_t Release() override { return wrapped_->Release(); }
  int32_t Encode(const VideoFrame& frame,
                 const std::vector<VideoFrameType>* frame_types) override {
    return wrapped_->Encode(frame, frame_types);
  }
  void SetRates(const RateControlParameters& parameters) override {
    wrapped_->SetRates(parameters);
  }
  void OnPacketLossRateUpdate(float packet_loss_rate) override {
    wrapped_->OnPacketLossRateUpdate(packet_loss_rate);
  }
  void OnRttUpdate(int64_t rtt_ms) override { wrapped_->OnRttUpdate(rtt_ms); }
  void OnLossNotification(const LossNotification& loss_notification) override {
    wrapped_->OnLossNotification(loss_notification);
  }
  EncoderInfo GetEncoderInfo() const override {
    return wrapped_->GetEncoderInfo();
  }

  // EncodedImageCallback overrides.
  Result OnEncodedImage(const EncodedImage& encoded_image,
                        const CodecSpecificInfo* codec_specific_info) override {
    {
      MutexLock lock(&mu_);
      GetFileWriterForSimulcastIndex(encoded_image.SimulcastIndex().value_or(0))
          .WriteFrame(encoded_image, codec_settings_.codecType);
    }
    return callback_->OnEncodedImage(encoded_image, codec_specific_info);
  }
  void OnDroppedFrame(DropReason reason) override {
    callback_->OnDroppedFrame(reason);
  }

 private:
  std::string FilenameFromSimulcastIndex(int index)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mu_) {
    char filename_buffer[1024];
    rtc::SimpleStringBuilder builder(filename_buffer);
    builder << output_directory_ << "/webrtc_encoded_frames"
            << "." << origin_time_micros_ << "." << index << ".ivf";
    return builder.str();
  }

  IvfFileWriter& GetFileWriterForSimulcastIndex(int index)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(mu_) {
    const auto& it = writers_by_simulcast_index_.find(index);
    if (it != writers_by_simulcast_index_.end()) {
      return *it->second;
    }
    auto writer = IvfFileWriter::Wrap(
        FileWrapper::OpenWriteOnly(FilenameFromSimulcastIndex(index)),
        /*byte_limit=*/100'000'000);
    auto* writer_ptr = writer.get();
    writers_by_simulcast_index_.insert(
        std::make_pair(index, std::move(writer)));
    return *writer_ptr;
  }

  std::unique_ptr<VideoEncoder> wrapped_;
  Mutex mu_;
  std::map<int, std::unique_ptr<IvfFileWriter>> writers_by_simulcast_index_
      RTC_GUARDED_BY(mu_);
  VideoCodec codec_settings_;
  EncodedImageCallback* callback_ = nullptr;
  std::string output_directory_;
  int64_t origin_time_micros_ = 0;
};

}  // namespace

std::unique_ptr<VideoEncoder> MaybeCreateFrameDumpingEncoderWrapper(
    std::unique_ptr<VideoEncoder> encoder,
    const FieldTrialsView& field_trials) {
  auto output_directory =
      field_trials.Lookup(kEncoderDataDumpDirectoryFieldTrial);
  if (output_directory.empty() || !encoder) {
    return encoder;
  }
  absl::c_replace(output_directory, ';', '/');
  return std::make_unique<FrameDumpingEncoder>(
      std::move(encoder), rtc::TimeMicros(), output_directory);
}

}  // namespace webrtc
