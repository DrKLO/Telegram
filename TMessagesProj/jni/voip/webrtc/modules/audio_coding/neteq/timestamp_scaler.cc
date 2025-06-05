/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/timestamp_scaler.h"

#include "api/audio_codecs/audio_format.h"
#include "modules/audio_coding/neteq/decoder_database.h"
#include "rtc_base/checks.h"

namespace webrtc {

void TimestampScaler::Reset() {
  first_packet_received_ = false;
}

void TimestampScaler::ToInternal(Packet* packet) {
  if (!packet) {
    return;
  }
  packet->timestamp = ToInternal(packet->timestamp, packet->payload_type);
}

void TimestampScaler::ToInternal(PacketList* packet_list) {
  PacketList::iterator it;
  for (it = packet_list->begin(); it != packet_list->end(); ++it) {
    ToInternal(&(*it));
  }
}

uint32_t TimestampScaler::ToInternal(uint32_t external_timestamp,
                                     uint8_t rtp_payload_type) {
  const DecoderDatabase::DecoderInfo* info =
      decoder_database_.GetDecoderInfo(rtp_payload_type);
  if (!info) {
    // Payload type is unknown. Do not scale.
    return external_timestamp;
  }
  if (!(info->IsComfortNoise() || info->IsDtmf())) {
    // Do not change the timestamp scaling settings for DTMF or CNG.
    numerator_ = info->SampleRateHz();
    if (info->GetFormat().clockrate_hz == 0) {
      // If the clockrate is invalid (i.e. with an old-style external codec)
      // we cannot do any timestamp scaling.
      denominator_ = numerator_;
    } else {
      denominator_ = info->GetFormat().clockrate_hz;
    }
  }
  if (numerator_ != denominator_) {
    // We have a scale factor != 1.
    if (!first_packet_received_) {
      external_ref_ = external_timestamp;
      internal_ref_ = external_timestamp;
      first_packet_received_ = true;
    }
    const int64_t external_diff = int64_t{external_timestamp} - external_ref_;
    RTC_DCHECK_GT(denominator_, 0);
    external_ref_ = external_timestamp;
    internal_ref_ += (external_diff * numerator_) / denominator_;
    return internal_ref_;
  } else {
    // No scaling.
    return external_timestamp;
  }
}

uint32_t TimestampScaler::ToExternal(uint32_t internal_timestamp) const {
  if (!first_packet_received_ || (numerator_ == denominator_)) {
    // Not initialized, or scale factor is 1.
    return internal_timestamp;
  } else {
    const int64_t internal_diff = int64_t{internal_timestamp} - internal_ref_;
    RTC_DCHECK_GT(numerator_, 0);
    // Do not update references in this method.
    // Switch `denominator_` and `numerator_` to convert the other way.
    return external_ref_ + (internal_diff * denominator_) / numerator_;
  }
}

}  // namespace webrtc
