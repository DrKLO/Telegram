/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_
#define MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_

#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/scoped_refptr.h"
#include "api/units/time_delta.h"
#include "rtc_base/constructor_magic.h"
#include "system_wrappers/include/field_trial.h"

namespace webrtc {

template <typename T>
class AudioEncoderIsacT final : public AudioEncoder {
 public:
  // Allowed combinations of sample rate, frame size, and bit rate are
  //  - 16000 Hz, 30 ms, 10000-32000 bps
  //  - 16000 Hz, 60 ms, 10000-32000 bps
  //  - 32000 Hz, 30 ms, 10000-56000 bps (if T has super-wideband support)
  struct Config {
    bool IsOk() const;
    int payload_type = 103;
    int sample_rate_hz = 16000;
    int frame_size_ms = 30;
    int bit_rate = kDefaultBitRate;  // Limit on the short-term average bit
                                     // rate, in bits/s.
    int max_payload_size_bytes = -1;
    int max_bit_rate = -1;
  };

  explicit AudioEncoderIsacT(const Config& config);
  ~AudioEncoderIsacT() override;

  int SampleRateHz() const override;
  size_t NumChannels() const override;
  size_t Num10MsFramesInNextPacket() const override;
  size_t Max10MsFramesInAPacket() const override;
  int GetTargetBitrate() const override;
  void SetTargetBitrate(int target_bps) override;
  void OnReceivedTargetAudioBitrate(int target_bps) override;
  void OnReceivedUplinkBandwidth(
      int target_audio_bitrate_bps,
      absl::optional<int64_t> bwe_period_ms) override;
  void OnReceivedUplinkAllocation(BitrateAllocationUpdate update) override;
  void OnReceivedOverhead(size_t overhead_bytes_per_packet) override;
  EncodedInfo EncodeImpl(uint32_t rtp_timestamp,
                         rtc::ArrayView<const int16_t> audio,
                         rtc::Buffer* encoded) override;
  void Reset() override;
  absl::optional<std::pair<TimeDelta, TimeDelta>> GetFrameLengthRange()
      const override;

 private:
  // This value is taken from STREAM_SIZE_MAX_60 for iSAC float (60 ms) and
  // STREAM_MAXW16_60MS for iSAC fix (60 ms).
  static const size_t kSufficientEncodeBufferSizeBytes = 400;

  static constexpr int kDefaultBitRate = 32000;
  static constexpr int kMinBitrateBps = 10000;
  static constexpr int MaxBitrateBps(int sample_rate_hz) {
    return sample_rate_hz == 32000 ? 56000 : 32000;
  }

  void SetTargetBitrate(int target_bps, bool subtract_per_packet_overhead);

  // Recreate the iSAC encoder instance with the given settings, and save them.
  void RecreateEncoderInstance(const Config& config);

  Config config_;
  typename T::instance_type* isac_state_ = nullptr;

  // Have we accepted input but not yet emitted it in a packet?
  bool packet_in_progress_ = false;

  // Timestamp of the first input of the currently in-progress packet.
  uint32_t packet_timestamp_;

  // Timestamp of the previously encoded packet.
  uint32_t last_encoded_timestamp_;

  // Cache the value of the "WebRTC-SendSideBwe-WithOverhead" field trial.
  const bool send_side_bwe_with_overhead_ =
      field_trial::IsEnabled("WebRTC-SendSideBwe-WithOverhead");

  // When we send a packet, expect this many bytes of headers to be added to it.
  // Start out with a reasonable default that we can use until we receive a real
  // value.
  DataSize overhead_per_packet_ = DataSize::Bytes(28);

  RTC_DISALLOW_COPY_AND_ASSIGN(AudioEncoderIsacT);
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_CODECS_ISAC_AUDIO_ENCODER_ISAC_T_H_
