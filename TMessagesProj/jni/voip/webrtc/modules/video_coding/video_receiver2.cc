/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/video_receiver2.h"

#include <stddef.h>

#include <cstdint>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "api/video_codecs/video_codec.h"
#include "api/video_codecs/video_decoder.h"
#include "modules/video_coding/decoder_database.h"
#include "modules/video_coding/encoded_frame.h"
#include "modules/video_coding/generic_decoder.h"
#include "modules/video_coding/include/video_coding_defines.h"
#include "modules/video_coding/timing/timing.h"
#include "rtc_base/checks.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

VideoReceiver2::VideoReceiver2(Clock* clock,
                               VCMTiming* timing,
                               const FieldTrialsView& field_trials)
    : clock_(clock),
      decoded_frame_callback_(timing, clock_, field_trials),
      codec_database_() {
  decoder_sequence_checker_.Detach();
}

VideoReceiver2::~VideoReceiver2() {
  RTC_DCHECK_RUN_ON(&construction_sequence_checker_);
}

// Register a receive callback. Will be called whenever there is a new frame
// ready for rendering.
int32_t VideoReceiver2::RegisterReceiveCallback(
    VCMReceiveCallback* receive_callback) {
  RTC_DCHECK_RUN_ON(&construction_sequence_checker_);
  // This value is set before the decoder thread starts and unset after
  // the decoder thread has been stopped.
  decoded_frame_callback_.SetUserReceiveCallback(receive_callback);
  return VCM_OK;
}

void VideoReceiver2::RegisterExternalDecoder(
    std::unique_ptr<VideoDecoder> decoder,
    uint8_t payload_type) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  RTC_DCHECK(decoded_frame_callback_.UserReceiveCallback());

  if (decoder) {
    RTC_DCHECK(!codec_database_.IsExternalDecoderRegistered(payload_type));
    codec_database_.RegisterExternalDecoder(payload_type, std::move(decoder));
  } else {
    codec_database_.DeregisterExternalDecoder(payload_type);
  }
}

bool VideoReceiver2::IsExternalDecoderRegistered(uint8_t payload_type) const {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  return codec_database_.IsExternalDecoderRegistered(payload_type);
}

// Must be called from inside the receive side critical section.
int32_t VideoReceiver2::Decode(const VCMEncodedFrame* frame) {
  RTC_DCHECK_RUN_ON(&decoder_sequence_checker_);
  TRACE_EVENT0("webrtc", "VideoReceiver2::Decode");
  // Change decoder if payload type has changed.
  VCMGenericDecoder* decoder =
      codec_database_.GetDecoder(*frame, &decoded_frame_callback_);
  if (decoder == nullptr) {
    return VCM_NO_CODEC_REGISTERED;
  }
  return decoder->Decode(*frame, clock_->CurrentTime());
}

// Register possible receive codecs, can be called multiple times.
// Called before decoder thread is started.
void VideoReceiver2::RegisterReceiveCodec(
    uint8_t payload_type,
    const VideoDecoder::Settings& settings) {
  RTC_DCHECK_RUN_ON(&construction_sequence_checker_);
  codec_database_.RegisterReceiveCodec(payload_type, settings);
}

void VideoReceiver2::DeregisterReceiveCodec(uint8_t payload_type) {
  RTC_DCHECK_RUN_ON(&construction_sequence_checker_);
  codec_database_.DeregisterReceiveCodec(payload_type);
}

void VideoReceiver2::DeregisterReceiveCodecs() {
  RTC_DCHECK_RUN_ON(&construction_sequence_checker_);
  codec_database_.DeregisterReceiveCodecs();
}

}  // namespace webrtc
