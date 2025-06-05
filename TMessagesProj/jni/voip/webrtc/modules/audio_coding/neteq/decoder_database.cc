/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/decoder_database.h"

#include <stddef.h>

#include <cstdint>
#include <list>
#include <type_traits>
#include <utility>

#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/audio_codecs/audio_decoder.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/audio_format_to_string.h"

namespace webrtc {

DecoderDatabase::DecoderDatabase(
    const rtc::scoped_refptr<AudioDecoderFactory>& decoder_factory,
    absl::optional<AudioCodecPairId> codec_pair_id)
    : active_decoder_type_(-1),
      active_cng_decoder_type_(-1),
      decoder_factory_(decoder_factory),
      codec_pair_id_(codec_pair_id) {}

DecoderDatabase::~DecoderDatabase() = default;

DecoderDatabase::DecoderInfo::DecoderInfo(
    const SdpAudioFormat& audio_format,
    absl::optional<AudioCodecPairId> codec_pair_id,
    AudioDecoderFactory* factory,
    absl::string_view codec_name)
    : name_(codec_name),
      audio_format_(audio_format),
      codec_pair_id_(codec_pair_id),
      factory_(factory),
      cng_decoder_(CngDecoder::Create(audio_format)),
      subtype_(SubtypeFromFormat(audio_format)) {}

DecoderDatabase::DecoderInfo::DecoderInfo(
    const SdpAudioFormat& audio_format,
    absl::optional<AudioCodecPairId> codec_pair_id,
    AudioDecoderFactory* factory)
    : DecoderInfo(audio_format, codec_pair_id, factory, audio_format.name) {}

DecoderDatabase::DecoderInfo::DecoderInfo(DecoderInfo&&) = default;
DecoderDatabase::DecoderInfo::~DecoderInfo() = default;

AudioDecoder* DecoderDatabase::DecoderInfo::GetDecoder() const {
  if (subtype_ != Subtype::kNormal) {
    // These are handled internally, so they have no AudioDecoder objects.
    return nullptr;
  }
  if (!decoder_) {
    // TODO(ossu): Keep a check here for now, since a number of tests create
    // DecoderInfos without factories.
    RTC_DCHECK(factory_);
    decoder_ = factory_->MakeAudioDecoder(audio_format_, codec_pair_id_);
  }
  RTC_DCHECK(decoder_) << "Failed to create: " << rtc::ToString(audio_format_);
  return decoder_.get();
}

bool DecoderDatabase::DecoderInfo::IsType(absl::string_view name) const {
  return absl::EqualsIgnoreCase(audio_format_.name, name);
}

absl::optional<DecoderDatabase::DecoderInfo::CngDecoder>
DecoderDatabase::DecoderInfo::CngDecoder::Create(const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "CN")) {
    // CN has a 1:1 RTP clock rate to sample rate ratio.
    const int sample_rate_hz = format.clockrate_hz;
    RTC_DCHECK(sample_rate_hz == 8000 || sample_rate_hz == 16000 ||
               sample_rate_hz == 32000 || sample_rate_hz == 48000);
    return DecoderDatabase::DecoderInfo::CngDecoder{sample_rate_hz};
  } else {
    return absl::nullopt;
  }
}

DecoderDatabase::DecoderInfo::Subtype
DecoderDatabase::DecoderInfo::SubtypeFromFormat(const SdpAudioFormat& format) {
  if (absl::EqualsIgnoreCase(format.name, "CN")) {
    return Subtype::kComfortNoise;
  } else if (absl::EqualsIgnoreCase(format.name, "telephone-event")) {
    return Subtype::kDtmf;
  } else if (absl::EqualsIgnoreCase(format.name, "red")) {
    return Subtype::kRed;
  }

  return Subtype::kNormal;
}

bool DecoderDatabase::Empty() const {
  return decoders_.empty();
}

int DecoderDatabase::Size() const {
  return static_cast<int>(decoders_.size());
}

std::vector<int> DecoderDatabase::SetCodecs(
    const std::map<int, SdpAudioFormat>& codecs) {
  // First collect all payload types that we'll remove or reassign, then remove
  // them from the database.
  std::vector<int> changed_payload_types;
  for (const std::pair<uint8_t, const DecoderInfo&> kv : decoders_) {
    auto i = codecs.find(kv.first);
    if (i == codecs.end() || i->second != kv.second.GetFormat()) {
      changed_payload_types.push_back(kv.first);
    }
  }
  for (int pl_type : changed_payload_types) {
    Remove(pl_type);
  }

  // Enter the new and changed payload type mappings into the database.
  for (const auto& kv : codecs) {
    const int& rtp_payload_type = kv.first;
    const SdpAudioFormat& audio_format = kv.second;
    RTC_DCHECK_GE(rtp_payload_type, 0);
    RTC_DCHECK_LE(rtp_payload_type, 0x7f);
    if (decoders_.count(rtp_payload_type) == 0) {
      decoders_.insert(std::make_pair(
          rtp_payload_type,
          DecoderInfo(audio_format, codec_pair_id_, decoder_factory_.get())));
    } else {
      // The mapping for this payload type hasn't changed.
    }
  }

  return changed_payload_types;
}

int DecoderDatabase::RegisterPayload(int rtp_payload_type,
                                     const SdpAudioFormat& audio_format) {
  if (rtp_payload_type < 0 || rtp_payload_type > 0x7f) {
    return kInvalidRtpPayloadType;
  }
  const auto ret = decoders_.insert(std::make_pair(
      rtp_payload_type,
      DecoderInfo(audio_format, codec_pair_id_, decoder_factory_.get())));
  if (ret.second == false) {
    // Database already contains a decoder with type `rtp_payload_type`.
    return kDecoderExists;
  }
  return kOK;
}

int DecoderDatabase::Remove(uint8_t rtp_payload_type) {
  if (decoders_.erase(rtp_payload_type) == 0) {
    // No decoder with that `rtp_payload_type`.
    return kDecoderNotFound;
  }
  if (active_decoder_type_ == rtp_payload_type) {
    active_decoder_type_ = -1;  // No active decoder.
  }
  if (active_cng_decoder_type_ == rtp_payload_type) {
    active_cng_decoder_type_ = -1;  // No active CNG decoder.
  }
  return kOK;
}

void DecoderDatabase::RemoveAll() {
  decoders_.clear();
  active_decoder_type_ = -1;      // No active decoder.
  active_cng_decoder_type_ = -1;  // No active CNG decoder.
}

const DecoderDatabase::DecoderInfo* DecoderDatabase::GetDecoderInfo(
    uint8_t rtp_payload_type) const {
  DecoderMap::const_iterator it = decoders_.find(rtp_payload_type);
  if (it == decoders_.end()) {
    // Decoder not found.
    return NULL;
  }
  return &it->second;
}

int DecoderDatabase::SetActiveDecoder(uint8_t rtp_payload_type,
                                      bool* new_decoder) {
  // Check that `rtp_payload_type` exists in the database.
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  if (!info) {
    // Decoder not found.
    return kDecoderNotFound;
  }
  RTC_CHECK(!info->IsComfortNoise());
  RTC_DCHECK(new_decoder);
  *new_decoder = false;
  if (active_decoder_type_ < 0) {
    // This is the first active decoder.
    *new_decoder = true;
  } else if (active_decoder_type_ != rtp_payload_type) {
    // Moving from one active decoder to another. Delete the first one.
    const DecoderInfo* old_info = GetDecoderInfo(active_decoder_type_);
    RTC_DCHECK(old_info);
    old_info->DropDecoder();
    *new_decoder = true;
  }
  active_decoder_type_ = rtp_payload_type;
  return kOK;
}

AudioDecoder* DecoderDatabase::GetActiveDecoder() const {
  if (active_decoder_type_ < 0) {
    // No active decoder.
    return NULL;
  }
  return GetDecoder(active_decoder_type_);
}

int DecoderDatabase::SetActiveCngDecoder(uint8_t rtp_payload_type) {
  // Check that `rtp_payload_type` exists in the database.
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  if (!info) {
    // Decoder not found.
    return kDecoderNotFound;
  }
  if (active_cng_decoder_type_ >= 0 &&
      active_cng_decoder_type_ != rtp_payload_type) {
    // Moving from one active CNG decoder to another. Delete the first one.
    RTC_DCHECK(active_cng_decoder_);
    active_cng_decoder_.reset();
  }
  active_cng_decoder_type_ = rtp_payload_type;
  return kOK;
}

ComfortNoiseDecoder* DecoderDatabase::GetActiveCngDecoder() const {
  if (active_cng_decoder_type_ < 0) {
    // No active CNG decoder.
    return NULL;
  }
  if (!active_cng_decoder_) {
    active_cng_decoder_.reset(new ComfortNoiseDecoder);
  }
  return active_cng_decoder_.get();
}

AudioDecoder* DecoderDatabase::GetDecoder(uint8_t rtp_payload_type) const {
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  return info ? info->GetDecoder() : nullptr;
}

bool DecoderDatabase::IsComfortNoise(uint8_t rtp_payload_type) const {
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  return info && info->IsComfortNoise();
}

bool DecoderDatabase::IsDtmf(uint8_t rtp_payload_type) const {
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  return info && info->IsDtmf();
}

bool DecoderDatabase::IsRed(uint8_t rtp_payload_type) const {
  const DecoderInfo* info = GetDecoderInfo(rtp_payload_type);
  return info && info->IsRed();
}

int DecoderDatabase::CheckPayloadTypes(const PacketList& packet_list) const {
  PacketList::const_iterator it;
  for (it = packet_list.begin(); it != packet_list.end(); ++it) {
    if (!GetDecoderInfo(it->payload_type)) {
      // Payload type is not found.
      RTC_LOG(LS_WARNING) << "CheckPayloadTypes: unknown RTP payload type "
                          << static_cast<int>(it->payload_type);
      return kDecoderNotFound;
    }
  }
  return kOK;
}

}  // namespace webrtc
