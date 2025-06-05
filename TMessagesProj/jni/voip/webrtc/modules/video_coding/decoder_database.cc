/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/decoder_database.h"

#include <memory>
#include <utility>

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

namespace webrtc {

VCMDecoderDatabase::VCMDecoderDatabase() {
  decoder_sequence_checker_.Detach();
}

void VCMDecoderDatabase::DeregisterExternalDecoder(uint8_t payload_type) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  auto it = decoders_.find(payload_type);
  if (it == decoders_.end()) {
    return;
  }

  // We can't use payload_type to check if the decoder is currently in use,
  // because payload type may be out of date (e.g. before we decode the first
  // frame after RegisterReceiveCodec).
  if (current_decoder_ && current_decoder_->IsSameDecoder(it->second.get())) {
    // Release it if it was registered and in use.
    current_decoder_ = absl::nullopt;
  }
  decoders_.erase(it);
}

// Add the external decoder object to the list of external decoders.
// Won't be registered as a receive codec until RegisterReceiveCodec is called.
void VCMDecoderDatabase::RegisterExternalDecoder(
    uint8_t payload_type,
    std::unique_ptr<VideoDecoder> external_decoder) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  // If payload value already exists, erase old and insert new.
  DeregisterExternalDecoder(payload_type);
  if (external_decoder) {
    decoders_.emplace(
        std::make_pair(payload_type, std::move(external_decoder)));
  }
}

bool VCMDecoderDatabase::IsExternalDecoderRegistered(
    uint8_t payload_type) const {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  return decoders_.find(payload_type) != decoders_.end();
}

void VCMDecoderDatabase::RegisterReceiveCodec(
    uint8_t payload_type,
    const VideoDecoder::Settings& settings) {
  // If payload value already exists, erase old and insert new.
  if (payload_type == current_payload_type_) {
    current_payload_type_ = absl::nullopt;
  }
  decoder_settings_[payload_type] = settings;
}

bool VCMDecoderDatabase::DeregisterReceiveCodec(uint8_t payload_type) {
  if (decoder_settings_.erase(payload_type) == 0) {
    return false;
  }
  if (payload_type == current_payload_type_) {
    // This codec is currently in use.
    current_payload_type_ = absl::nullopt;
  }
  return true;
}

void VCMDecoderDatabase::DeregisterReceiveCodecs() {
  current_payload_type_ = absl::nullopt;
  decoder_settings_.clear();
}

VCMGenericDecoder* VCMDecoderDatabase::GetDecoder(
    const EncodedFrame& frame,
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

void VCMDecoderDatabase::CreateAndInitDecoder(const EncodedFrame& frame) {
  uint8_t payload_type = frame.PayloadType();
  RTC_DLOG(LS_INFO) << "Initializing decoder with payload type '"
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
  current_decoder_.emplace(external_dec_item->second.get());

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

}  // namespace webrtc
