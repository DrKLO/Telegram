/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_
#define MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_

#include <stdint.h>

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/array_view.h"
#include "api/audio_codecs/audio_decoder.h"
#include "api/audio_codecs/audio_format.h"
#include "modules/audio_coding/acm2/acm_resampler.h"
#include "modules/audio_coding/acm2/call_statistics.h"
#include "modules/audio_coding/include/audio_coding_module.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class Clock;
class NetEq;
struct RTPHeader;

namespace acm2 {

class AcmReceiver {
 public:
  // Constructor of the class
  explicit AcmReceiver(const AudioCodingModule::Config& config);

  // Destructor of the class.
  ~AcmReceiver();

  //
  // Inserts a payload with its associated RTP-header into NetEq.
  //
  // Input:
  //   - rtp_header           : RTP header for the incoming payload containing
  //                            information about payload type, sequence number,
  //                            timestamp, SSRC and marker bit.
  //   - incoming_payload     : Incoming audio payload.
  //   - length_payload       : Length of incoming audio payload in bytes.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int InsertPacket(const RTPHeader& rtp_header,
                   rtc::ArrayView<const uint8_t> incoming_payload);

  //
  // Asks NetEq for 10 milliseconds of decoded audio.
  //
  // Input:
  //   -desired_freq_hz       : specifies the sampling rate [Hz] of the output
  //                            audio. If set -1 indicates to resampling is
  //                            is required and the audio returned at the
  //                            sampling rate of the decoder.
  //
  // Output:
  //   -audio_frame           : an audio frame were output data and
  //                            associated parameters are written to.
  //   -muted                 : if true, the sample data in audio_frame is not
  //                            populated, and must be interpreted as all zero.
  //
  // Return value             : 0 if OK.
  //                           -1 if NetEq returned an error.
  //
  int GetAudio(int desired_freq_hz, AudioFrame* audio_frame, bool* muted);

  // Replace the current set of decoders with the specified set.
  void SetCodecs(const std::map<int, SdpAudioFormat>& codecs);

  //
  // Sets a minimum delay for packet buffer. The given delay is maintained,
  // unless channel condition dictates a higher delay.
  //
  // Input:
  //   - delay_ms             : minimum delay in milliseconds.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int SetMinimumDelay(int delay_ms);

  //
  // Sets a maximum delay [ms] for the packet buffer. The target delay does not
  // exceed the given value, even if channel condition requires so.
  //
  // Input:
  //   - delay_ms             : maximum delay in milliseconds.
  //
  // Return value             : 0 if OK.
  //                           <0 if NetEq returned an error.
  //
  int SetMaximumDelay(int delay_ms);

  // Sets a base minimum delay in milliseconds for the packet buffer.
  // Base minimum delay sets lower bound minimum delay value which
  // is set via SetMinimumDelay.
  //
  // Returns true if value was successfully set, false overwise.
  bool SetBaseMinimumDelayMs(int delay_ms);

  // Returns current value of base minimum delay in milliseconds.
  int GetBaseMinimumDelayMs() const;

  //
  // Resets the initial delay to zero.
  //
  void ResetInitialDelay();

  // Returns the sample rate of the decoder associated with the last incoming
  // packet. If no packet of a registered non-CNG codec has been received, the
  // return value is empty. Also, if the decoder was unregistered since the last
  // packet was inserted, the return value is empty.
  absl::optional<int> last_packet_sample_rate_hz() const;

  // Returns last_output_sample_rate_hz from the NetEq instance.
  int last_output_sample_rate_hz() const;

  //
  // Get the current network statistics from NetEq.
  //
  // Output:
  //   - statistics           : The current network statistics.
  //
  void GetNetworkStatistics(NetworkStatistics* statistics,
                            bool get_and_clear_legacy_stats = true) const;

  //
  // Flushes the NetEq packet and speech buffers.
  //
  void FlushBuffers();

  //
  // Remove all registered codecs.
  //
  void RemoveAllCodecs();

  // Returns the RTP timestamp for the last sample delivered by GetAudio().
  // The return value will be empty if no valid timestamp is available.
  absl::optional<uint32_t> GetPlayoutTimestamp();

  // Returns the current total delay from NetEq (packet buffer and sync buffer)
  // in ms, with smoothing applied to even out short-time fluctuations due to
  // jitter. The packet buffer part of the delay is not updated during DTX/CNG
  // periods.
  //
  int FilteredCurrentDelayMs() const;

  // Returns the current target delay for NetEq in ms.
  //
  int TargetDelayMs() const;

  //
  // Get payload type and format of the last non-CNG/non-DTMF received payload.
  // If no non-CNG/non-DTMF packet is received absl::nullopt is returned.
  //
  absl::optional<std::pair<int, SdpAudioFormat>> LastDecoder() const;

  //
  // Enable NACK and set the maximum size of the NACK list. If NACK is already
  // enabled then the maximum NACK list size is modified accordingly.
  //
  // If the sequence number of last received packet is N, the sequence numbers
  // of NACK list are in the range of [N - `max_nack_list_size`, N).
  //
  // `max_nack_list_size` should be positive (none zero) and less than or
  // equal to `Nack::kNackListSizeLimit`. Otherwise, No change is applied and -1
  // is returned. 0 is returned at success.
  //
  int EnableNack(size_t max_nack_list_size);

  // Disable NACK.
  void DisableNack();

  //
  // Get a list of packets to be retransmitted. `round_trip_time_ms` is an
  // estimate of the round-trip-time (in milliseconds). Missing packets which
  // will be playout in a shorter time than the round-trip-time (with respect
  // to the time this API is called) will not be included in the list.
  //
  // Negative `round_trip_time_ms` results is an error message and empty list
  // is returned.
  //
  std::vector<uint16_t> GetNackList(int64_t round_trip_time_ms) const;

  //
  // Get statistics of calls to GetAudio().
  void GetDecodingCallStatistics(AudioDecodingCallStats* stats) const;

 private:
  struct DecoderInfo {
    int payload_type;
    int sample_rate_hz;
    int num_channels;
    SdpAudioFormat sdp_format;
  };

  uint32_t NowInTimestamp(int decoder_sampling_rate) const;

  mutable Mutex mutex_;
  absl::optional<DecoderInfo> last_decoder_ RTC_GUARDED_BY(mutex_);
  ACMResampler resampler_ RTC_GUARDED_BY(mutex_);
  std::unique_ptr<int16_t[]> last_audio_buffer_ RTC_GUARDED_BY(mutex_);
  CallStatistics call_stats_ RTC_GUARDED_BY(mutex_);
  const std::unique_ptr<NetEq> neteq_;  // NetEq is thread-safe; no lock needed.
  Clock* const clock_;
  bool resampled_last_output_frame_ RTC_GUARDED_BY(mutex_);
};

}  // namespace acm2

}  // namespace webrtc

#endif  // MODULES_AUDIO_CODING_ACM2_ACM_RECEIVER_H_
