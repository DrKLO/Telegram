/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/video_coding_impl.h"

#include <algorithm>
#include <memory>

#include "api/field_trials_view.h"
#include "api/sequence_checker.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/encoded_image.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "modules/video_coding/timing/timing.h"
#include "rtc_base/logging.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
namespace vcm {

int64_t VCMProcessTimer::Period() const {
  return _periodMs;
}

int64_t VCMProcessTimer::TimeUntilProcess() const {
  const int64_t time_since_process = _clock->TimeInMilliseconds() - _latestMs;
  const int64_t time_until_process = _periodMs - time_since_process;
  return std::max<int64_t>(time_until_process, 0);
}

void VCMProcessTimer::Processed() {
  _latestMs = _clock->TimeInMilliseconds();
}

DEPRECATED_VCMDecoderDataBase::DEPRECATED_VCMDecoderDataBase() {
  decoder_sequence_checker_.Detach();
}

VideoDecoder* DEPRECATED_VCMDecoderDataBase::DeregisterExternalDecoder(
    uint8_t payload_type) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  auto it = decoders_.find(payload_type);
  if (it == decoders_.end()) {
    return nullptr;
  }

  // We can't use payload_type to check if the decoder is currently in use,
  // because payload type may be out of date (e.g. before we decode the first
  // frame after RegisterReceiveCodec).
  if (current_decoder_ && current_decoder_->IsSameDecoder(it->second)) {
    // Release it if it was registered and in use.
    current_decoder_ = absl::nullopt;
  }
  VideoDecoder* ret = it->second;
  decoders_.erase(it);
  return ret;
}

// Add the external decoder object to the list of external decoders.
// Won't be registered as a receive codec until RegisterReceiveCodec is called.
void DEPRECATED_VCMDecoderDataBase::RegisterExternalDecoder(
    uint8_t payload_type,
    VideoDecoder* external_decoder) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  // If payload value already exists, erase old and insert new.
  DeregisterExternalDecoder(payload_type);
  decoders_[payload_type] = external_decoder;
}

bool DEPRECATED_VCMDecoderDataBase::IsExternalDecoderRegistered(
    uint8_t payload_type) const {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  return payload_type == current_payload_type_ ||
         decoders_.find(payload_type) != decoders_.end();
}

void DEPRECATED_VCMDecoderDataBase::RegisterReceiveCodec(
    uint8_t payload_type,
    const VideoDecoder::Settings& settings) {
  // If payload value already exists, erase old and insert new.
  if (payload_type == current_payload_type_) {
    current_payload_type_ = absl::nullopt;
  }
  decoder_settings_[payload_type] = settings;
}

bool DEPRECATED_VCMDecoderDataBase::DeregisterReceiveCodec(
    uint8_t payload_type) {
  if (decoder_settings_.erase(payload_type) == 0) {
    return false;
  }
  if (payload_type == current_payload_type_) {
    // This codec is currently in use.
    current_payload_type_ = absl::nullopt;
  }
  return true;
}

VCMGenericDecoder* DEPRECATED_VCMDecoderDataBase::GetDecoder(
    const VCMEncodedFrame& frame,
    VCMDecodedFrameCallback* decoded_frame_callback) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  RTC_DCHECK(decoded_frame_callback->UserReceiveCallback());
  uint8_t payload_type = frame.PayloadType();
  if (payload_type == current_payload_type_ || payload_type == 0) {
    return current_decoder_.has_value() ? &*current_decoder_ : nullptr;
  }
  // If decoder exists - delete.
  if (current_decoder_.has_value()) {
    current_decoder_ = absl::nullopt;
    current_payload_type_ = absl::nullopt;
  }

  CreateAndInitDecoder(frame);
  if (current_decoder_ == absl::nullopt) {
    return nullptr;
  }

  VCMReceiveCallback* callback = decoded_frame_callback->UserReceiveCallback();
  callback->OnIncomingPayloadType(payload_type);
  if (current_decoder_->RegisterDecodeCompleteCallback(decoded_frame_callback) <
      0) {
    current_decoder_ = absl::nullopt;
    return nullptr;
  }

  current_payload_type_ = payload_type;
  return &*current_decoder_;
}

void DEPRECATED_VCMDecoderDataBase::CreateAndInitDecoder(
    const VCMEncodedFrame& frame) {
  uint8_t payload_type = frame.PayloadType();
  RTC_LOG(LS_INFO) << "Initializing decoder with payload type '"
                   << int{payload_type} << "'.";
  auto decoder_item = decoder_settings_.find(payload_type);
  if (decoder_item == decoder_settings_.end()) {
    RTC_LOG(LS_ERROR) << "Can't find a decoder associated with payload type: "
                      << int{payload_type};
    return;
  }
  auto external_dec_item = decoders_.find(payload_type);
  if (external_dec_item == decoders_.end()) {
    RTC_LOG(LS_ERROR) << "No decoder of this type exists.";
    return;
  }
  current_decoder_.emplace(external_dec_item->second);

  // Copy over input resolutions to prevent codec reinitialization due to
  // the first frame being of a different resolution than the database values.
  // This is best effort, since there's no guarantee that width/height have been
  // parsed yet (and may be zero).
  RenderResolution frame_resolution(frame.EncodedImage()._encodedWidth,
                                    frame.EncodedImage()._encodedHeight);
  if (frame_resolution.Valid()) {
    decoder_item->second.set_max_render_resolution(frame_resolution);
  }
  if (!current_decoder_->Configure(decoder_item->second)) {
    current_decoder_ = absl::nullopt;
    RTC_LOG(LS_ERROR) << "Failed to initialize decoder.";
  }
}

}  // namespace vcm

namespace {

class VideoCodingModuleImpl : public VideoCodingModule {
 public:
  explicit VideoCodingModuleImpl(Clock* clock,
                                 const FieldTrialsView* field_trials)
      : VideoCodingModule(),
        field_trials_(field_trials),
        timing_(new VCMTiming(clock, *field_trials_)),
        receiver_(clock, timing_.get(), *field_trials_) {}

  ~VideoCodingModuleImpl() override = default;

  void Process() override { receiver_.Process(); }

  void RegisterReceiveCodec(
      uint8_t payload_type,
      const VideoDecoder::Settings& decoder_settings) override {
    receiver_.RegisterReceiveCodec(payload_type, decoder_settings);
  }

  void RegisterExternalDecoder(VideoDecoder* externalDecoder,
                               uint8_t payloadType) override {
    receiver_.RegisterExternalDecoder(externalDecoder, payloadType);
  }

  int32_t RegisterReceiveCallback(
      VCMReceiveCallback* receiveCallback) override {
    RTC_DCHECK(construction_thread_.IsCurrent());
    return receiver_.RegisterReceiveCallback(receiveCallback);
  }

  int32_t RegisterFrameTypeCallback(
      VCMFrameTypeCallback* frameTypeCallback) override {
    return receiver_.RegisterFrameTypeCallback(frameTypeCallback);
  }

  int32_t RegisterPacketRequestCallback(
      VCMPacketRequestCallback* callback) override {
    RTC_DCHECK(construction_thread_.IsCurrent());
    return receiver_.RegisterPacketRequestCallback(callback);
  }

  int32_t Decode(uint16_t maxWaitTimeMs) override {
    return receiver_.Decode(maxWaitTimeMs);
  }

  int32_t IncomingPacket(const uint8_t* incomingPayload,
                         size_t payloadLength,
                         const RTPHeader& rtp_header,
                         const RTPVideoHeader& video_header) override {
    return receiver_.IncomingPacket(incomingPayload, payloadLength, rtp_header,
                                    video_header);
  }

  void SetNackSettings(size_t max_nack_list_size,
                       int max_packet_age_to_nack,
                       int max_incomplete_time_ms) override {
    return receiver_.SetNackSettings(max_nack_list_size, max_packet_age_to_nack,
                                     max_incomplete_time_ms);
  }

 private:
  AlwaysValidPointer<const FieldTrialsView, FieldTrialBasedConfig>
      field_trials_;
  SequenceChecker construction_thread_;
  const std::unique_ptr<VCMTiming> timing_;
  vcm::VideoReceiver receiver_;
};
}  // namespace

// DEPRECATED.  Create method for current interface, will be removed when the
// new jitter buffer is in place.
VideoCodingModule* VideoCodingModule::Create(
    Clock* clock,
    const FieldTrialsView* field_trials) {
  RTC_DCHECK(clock);
  return new VideoCodingModuleImpl(clock, field_trials);
}

}  // namespace webrtc
