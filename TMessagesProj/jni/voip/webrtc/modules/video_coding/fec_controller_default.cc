/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_coding/fec_controller_default.h"  // NOLINT

#include <stdlib.h>

#include <algorithm>
#include <string>

#include "api/environment/environment.h"
#include "api/field_trials_view.h"
#include "modules/include/module_fec_types.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {

const float kProtectionOverheadRateThreshold = 0.5;

FecControllerDefault::FecControllerDefault(
    const Environment& env,
    VCMProtectionCallback* protection_callback)
    : env_(env),
      protection_callback_(protection_callback),
      loss_prot_logic_(new media_optimization::VCMLossProtectionLogic(
          env_.clock().TimeInMilliseconds())),
      max_payload_size_(1460),
      overhead_threshold_(GetProtectionOverheadRateThreshold()) {}

FecControllerDefault::FecControllerDefault(const Environment& env)
    : FecControllerDefault(env, nullptr) {}

FecControllerDefault::~FecControllerDefault(void) {
  loss_prot_logic_->Release();
}

void FecControllerDefault::SetProtectionCallback(
    VCMProtectionCallback* protection_callback) {
  protection_callback_ = protection_callback;
}

void FecControllerDefault::SetEncodingData(size_t width,
                                           size_t height,
                                           size_t num_temporal_layers,
                                           size_t max_payload_size) {
  MutexLock lock(&mutex_);
  loss_prot_logic_->UpdateFrameSize(width, height);
  loss_prot_logic_->UpdateNumLayers(num_temporal_layers);
  max_payload_size_ = max_payload_size;
}

float FecControllerDefault::GetProtectionOverheadRateThreshold() {
  float overhead_threshold =
      strtof(env_.field_trials()
                 .Lookup("WebRTC-ProtectionOverheadRateThreshold")
                 .c_str(),
             nullptr);
  if (overhead_threshold > 0 && overhead_threshold <= 1) {
    RTC_LOG(LS_INFO) << "ProtectionOverheadRateThreshold is set to "
                     << overhead_threshold;
    return overhead_threshold;
  } else if (overhead_threshold < 0 || overhead_threshold > 1) {
    RTC_LOG(LS_WARNING)
        << "ProtectionOverheadRateThreshold field trial is set to "
           "an invalid value, expecting a value between (0, 1].";
  }
  // WebRTC-ProtectionOverheadRateThreshold field trial string is not found, use
  // the default value.
  return kProtectionOverheadRateThreshold;
}

uint32_t FecControllerDefault::UpdateFecRates(
    uint32_t estimated_bitrate_bps,
    int actual_framerate_fps,
    uint8_t fraction_lost,
    std::vector<bool> loss_mask_vector,
    int64_t round_trip_time_ms) {
  float target_bitrate_kbps =
      static_cast<float>(estimated_bitrate_bps) / 1000.0f;
  // Sanity check.
  if (actual_framerate_fps < 1.0) {
    actual_framerate_fps = 1.0;
  }
  FecProtectionParams delta_fec_params;
  FecProtectionParams key_fec_params;
  {
    MutexLock lock(&mutex_);
    loss_prot_logic_->UpdateBitRate(target_bitrate_kbps);
    loss_prot_logic_->UpdateRtt(round_trip_time_ms);
    // Update frame rate for the loss protection logic class: frame rate should
    // be the actual/sent rate.
    loss_prot_logic_->UpdateFrameRate(actual_framerate_fps);
    // Returns the filtered packet loss, used for the protection setting.
    // The filtered loss may be the received loss (no filter), or some
    // filtered value (average or max window filter).
    // Use max window filter for now.
    media_optimization::FilterPacketLossMode filter_mode =
        media_optimization::kMaxFilter;
    uint8_t packet_loss_enc = loss_prot_logic_->FilteredLoss(
        env_.clock().TimeInMilliseconds(), filter_mode, fraction_lost);
    // For now use the filtered loss for computing the robustness settings.
    loss_prot_logic_->UpdateFilteredLossPr(packet_loss_enc);
    if (loss_prot_logic_->SelectedType() == media_optimization::kNone) {
      return estimated_bitrate_bps;
    }
    // Update method will compute the robustness settings for the given
    // protection method and the overhead cost
    // the protection method is set by the user via SetVideoProtection.
    loss_prot_logic_->UpdateMethod();
    // Get the bit cost of protection method, based on the amount of
    // overhead data actually transmitted (including headers) the last
    // second.
    // Get the FEC code rate for Key frames (set to 0 when NA).
    key_fec_params.fec_rate =
        loss_prot_logic_->SelectedMethod()->RequiredProtectionFactorK();
    // Get the FEC code rate for Delta frames (set to 0 when NA).
    delta_fec_params.fec_rate =
        loss_prot_logic_->SelectedMethod()->RequiredProtectionFactorD();
    // The RTP module currently requires the same `max_fec_frames` for both
    // key and delta frames.
    delta_fec_params.max_fec_frames =
        loss_prot_logic_->SelectedMethod()->MaxFramesFec();
    key_fec_params.max_fec_frames =
        loss_prot_logic_->SelectedMethod()->MaxFramesFec();
  }
  // Set the FEC packet mask type. `kFecMaskBursty` is more effective for
  // consecutive losses and little/no packet re-ordering. As we currently
  // do not have feedback data on the degree of correlated losses and packet
  // re-ordering, we keep default setting to `kFecMaskRandom` for now.
  delta_fec_params.fec_mask_type = kFecMaskRandom;
  key_fec_params.fec_mask_type = kFecMaskRandom;
  // Update protection callback with protection settings.
  uint32_t sent_video_rate_bps = 0;
  uint32_t sent_nack_rate_bps = 0;
  uint32_t sent_fec_rate_bps = 0;
  // Rate cost of the protection methods.
  float protection_overhead_rate = 0.0f;
  // TODO(Marco): Pass FEC protection values per layer.
  protection_callback_->ProtectionRequest(
      &delta_fec_params, &key_fec_params, &sent_video_rate_bps,
      &sent_nack_rate_bps, &sent_fec_rate_bps);
  uint32_t sent_total_rate_bps =
      sent_video_rate_bps + sent_nack_rate_bps + sent_fec_rate_bps;
  // Estimate the overhead costs of the next second as staying the same
  // wrt the source bitrate.
  if (sent_total_rate_bps > 0) {
    protection_overhead_rate =
        static_cast<float>(sent_nack_rate_bps + sent_fec_rate_bps) /
        sent_total_rate_bps;
  }
  // Cap the overhead estimate to a threshold, default is 50%.
  protection_overhead_rate =
      std::min(protection_overhead_rate, overhead_threshold_);
  // Source coding rate: total rate - protection overhead.
  return estimated_bitrate_bps * (1.0 - protection_overhead_rate);
}

void FecControllerDefault::SetProtectionMethod(bool enable_fec,
                                               bool enable_nack) {
  media_optimization::VCMProtectionMethodEnum method(media_optimization::kNone);
  if (enable_fec && enable_nack) {
    method = media_optimization::kNackFec;
  } else if (enable_nack) {
    method = media_optimization::kNack;
  } else if (enable_fec) {
    method = media_optimization::kFec;
  }
  MutexLock lock(&mutex_);
  loss_prot_logic_->SetMethod(method);
}

void FecControllerDefault::UpdateWithEncodedData(
    const size_t encoded_image_length,
    const VideoFrameType encoded_image_frametype) {
  const size_t encoded_length = encoded_image_length;
  MutexLock lock(&mutex_);
  if (encoded_length > 0) {
    const bool delta_frame =
        encoded_image_frametype != VideoFrameType::kVideoFrameKey;
    if (max_payload_size_ > 0 && encoded_length > 0) {
      const float min_packets_per_frame =
          encoded_length / static_cast<float>(max_payload_size_);
      if (delta_frame) {
        loss_prot_logic_->UpdatePacketsPerFrame(
            min_packets_per_frame, env_.clock().TimeInMilliseconds());
      } else {
        loss_prot_logic_->UpdatePacketsPerFrameKey(
            min_packets_per_frame, env_.clock().TimeInMilliseconds());
      }
    }
    if (!delta_frame && encoded_length > 0) {
      loss_prot_logic_->UpdateKeyFrameSize(static_cast<float>(encoded_length));
    }
  }
}

bool FecControllerDefault::UseLossVectorMask() {
  return false;
}

}  // namespace webrtc
