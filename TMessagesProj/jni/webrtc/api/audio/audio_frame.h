/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_AUDIO_AUDIO_FRAME_H_
#define API_AUDIO_AUDIO_FRAME_H_

#include <stddef.h>
#include <stdint.h>

#include <utility>

#include "api/audio/channel_layout.h"
#include "api/rtp_packet_infos.h"
#include "rtc_base/constructor_magic.h"

namespace webrtc {

/* This class holds up to 120 ms of super-wideband (32 kHz) stereo audio. It
 * allows for adding and subtracting frames while keeping track of the resulting
 * states.
 *
 * Notes
 * - This is a de-facto api, not designed for external use. The AudioFrame class
 *   is in need of overhaul or even replacement, and anyone depending on it
 *   should be prepared for that.
 * - The total number of samples is samples_per_channel_ * num_channels_.
 * - Stereo data is interleaved starting with the left channel.
 */
class AudioFrame {
 public:
  // Using constexpr here causes linker errors unless the variable also has an
  // out-of-class definition, which is impractical in this header-only class.
  // (This makes no sense because it compiles as an enum value, which we most
  // certainly cannot take the address of, just fine.) C++17 introduces inline
  // variables which should allow us to switch to constexpr and keep this a
  // header-only class.
  enum : size_t {
    // Stereo, 32 kHz, 120 ms (2 * 32 * 120)
    // Stereo, 192 kHz, 20 ms (2 * 192 * 20)
    kMaxDataSizeSamples = 7680,
    kMaxDataSizeBytes = kMaxDataSizeSamples * sizeof(int16_t),
  };

  enum VADActivity { kVadActive = 0, kVadPassive = 1, kVadUnknown = 2 };
  enum SpeechType {
    kNormalSpeech = 0,
    kPLC = 1,
    kCNG = 2,
    kPLCCNG = 3,
    kCodecPLC = 5,
    kUndefined = 4
  };

  AudioFrame();

  friend void swap(AudioFrame& a, AudioFrame& b);

  // Resets all members to their default state.
  void Reset();
  // Same as Reset(), but leaves mute state unchanged. Muting a frame requires
  // the buffer to be zeroed on the next call to mutable_data(). Callers
  // intending to write to the buffer immediately after Reset() can instead use
  // ResetWithoutMuting() to skip this wasteful zeroing.
  void ResetWithoutMuting();

  void UpdateFrame(uint32_t timestamp,
                   const int16_t* data,
                   size_t samples_per_channel,
                   int sample_rate_hz,
                   SpeechType speech_type,
                   VADActivity vad_activity,
                   size_t num_channels = 1);

  void CopyFrom(const AudioFrame& src);

  // Sets a wall-time clock timestamp in milliseconds to be used for profiling
  // of time between two points in the audio chain.
  // Example:
  //   t0: UpdateProfileTimeStamp()
  //   t1: ElapsedProfileTimeMs() => t1 - t0 [msec]
  void UpdateProfileTimeStamp();
  // Returns the time difference between now and when UpdateProfileTimeStamp()
  // was last called. Returns -1 if UpdateProfileTimeStamp() has not yet been
  // called.
  int64_t ElapsedProfileTimeMs() const;

  // data() returns a zeroed static buffer if the frame is muted.
  // mutable_frame() always returns a non-static buffer; the first call to
  // mutable_frame() zeros the non-static buffer and marks the frame unmuted.
  const int16_t* data() const;
  int16_t* mutable_data();

  // Prefer to mute frames using AudioFrameOperations::Mute.
  void Mute();
  // Frame is muted by default.
  bool muted() const;

  size_t max_16bit_samples() const { return kMaxDataSizeSamples; }
  size_t samples_per_channel() const { return samples_per_channel_; }
  size_t num_channels() const { return num_channels_; }
  ChannelLayout channel_layout() const { return channel_layout_; }
  int sample_rate_hz() const { return sample_rate_hz_; }

  void set_absolute_capture_timestamp_ms(
      int64_t absolute_capture_time_stamp_ms) {
    absolute_capture_timestamp_ms_ = absolute_capture_time_stamp_ms;
  }

  absl::optional<int64_t> absolute_capture_timestamp_ms() const {
    return absolute_capture_timestamp_ms_;
  }

  // RTP timestamp of the first sample in the AudioFrame.
  uint32_t timestamp_ = 0;
  // Time since the first frame in milliseconds.
  // -1 represents an uninitialized value.
  int64_t elapsed_time_ms_ = -1;
  // NTP time of the estimated capture time in local timebase in milliseconds.
  // -1 represents an uninitialized value.
  int64_t ntp_time_ms_ = -1;
  size_t samples_per_channel_ = 0;
  int sample_rate_hz_ = 0;
  size_t num_channels_ = 0;
  ChannelLayout channel_layout_ = CHANNEL_LAYOUT_NONE;
  SpeechType speech_type_ = kUndefined;
  VADActivity vad_activity_ = kVadUnknown;
  // Monotonically increasing timestamp intended for profiling of audio frames.
  // Typically used for measuring elapsed time between two different points in
  // the audio path. No lock is used to save resources and we are thread safe
  // by design.
  // TODO(nisse@webrtc.org): consider using absl::optional.
  int64_t profile_timestamp_ms_ = 0;

  // Information about packets used to assemble this audio frame. This is needed
  // by |SourceTracker| when the frame is delivered to the RTCRtpReceiver's
  // MediaStreamTrack, in order to implement getContributingSources(). See:
  // https://w3c.github.io/webrtc-pc/#dom-rtcrtpreceiver-getcontributingsources
  //
  // TODO(bugs.webrtc.org/10757):
  //   Note that this information might not be fully accurate since we currently
  //   don't have a proper way to track it across the audio sync buffer. The
  //   sync buffer is the small sample-holding buffer located after the audio
  //   decoder and before where samples are assembled into output frames.
  //
  // |RtpPacketInfos| may also be empty if the audio samples did not come from
  // RTP packets. E.g. if the audio were locally generated by packet loss
  // concealment, comfort noise generation, etc.
  RtpPacketInfos packet_infos_;

 private:
  // A permanently zeroed out buffer to represent muted frames. This is a
  // header-only class, so the only way to avoid creating a separate empty
  // buffer per translation unit is to wrap a static in an inline function.
  static const int16_t* empty_data();

  int16_t data_[kMaxDataSizeSamples];
  bool muted_ = true;

  // Absolute capture timestamp when this audio frame was originally captured.
  // This is only valid for audio frames captured on this machine. The absolute
  // capture timestamp of a received frame is found in |packet_infos_|.
  // This timestamp MUST be based on the same clock as rtc::TimeMillis().
  absl::optional<int64_t> absolute_capture_timestamp_ms_;

  RTC_DISALLOW_COPY_AND_ASSIGN(AudioFrame);
};

}  // namespace webrtc

#endif  // API_AUDIO_AUDIO_FRAME_H_
