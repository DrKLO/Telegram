/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_AUDIO_RECEIVE_STREAM_H_
#define AUDIO_AUDIO_RECEIVE_STREAM_H_

#include <memory>
#include <vector>

#include "api/audio/audio_mixer.h"
#include "api/neteq/neteq_factory.h"
#include "api/rtp_headers.h"
#include "audio/audio_state.h"
#include "call/audio_receive_stream.h"
#include "call/syncable.h"
#include "modules/rtp_rtcp/source/source_tracker.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/thread_checker.h"
#include "system_wrappers/include/clock.h"

namespace webrtc {
class PacketRouter;
class ProcessThread;
class RtcEventLog;
class RtpPacketReceived;
class RtpStreamReceiverControllerInterface;
class RtpStreamReceiverInterface;

namespace voe {
class ChannelReceiveInterface;
}  // namespace voe

namespace internal {
class AudioSendStream;

class AudioReceiveStream final : public webrtc::AudioReceiveStream,
                                 public AudioMixer::Source,
                                 public Syncable {
 public:
  AudioReceiveStream(Clock* clock,
                     RtpStreamReceiverControllerInterface* receiver_controller,
                     PacketRouter* packet_router,
                     ProcessThread* module_process_thread,
                     NetEqFactory* neteq_factory,
                     const webrtc::AudioReceiveStream::Config& config,
                     const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
                     webrtc::RtcEventLog* event_log);
  // For unit tests, which need to supply a mock channel receive.
  AudioReceiveStream(
      Clock* clock,
      RtpStreamReceiverControllerInterface* receiver_controller,
      PacketRouter* packet_router,
      const webrtc::AudioReceiveStream::Config& config,
      const rtc::scoped_refptr<webrtc::AudioState>& audio_state,
      webrtc::RtcEventLog* event_log,
      std::unique_ptr<voe::ChannelReceiveInterface> channel_receive);
  ~AudioReceiveStream() override;

  // webrtc::AudioReceiveStream implementation.
  void Reconfigure(const webrtc::AudioReceiveStream::Config& config) override;
  void Start() override;
  void Stop() override;
  webrtc::AudioReceiveStream::Stats GetStats() const override;
  void SetSink(AudioSinkInterface* sink) override;
  void SetGain(float gain) override;
  bool SetBaseMinimumPlayoutDelayMs(int delay_ms) override;
  int GetBaseMinimumPlayoutDelayMs() const override;
  std::vector<webrtc::RtpSource> GetSources() const override;

  // TODO(nisse): We don't formally implement RtpPacketSinkInterface, and this
  // method shouldn't be needed. But it's currently used by the
  // AudioReceiveStreamTest.ReceiveRtpPacket unittest. Figure out if that test
  // shuld be refactored or deleted, and then delete this method.
  void OnRtpPacket(const RtpPacketReceived& packet);

  // AudioMixer::Source
  AudioFrameInfo GetAudioFrameWithInfo(int sample_rate_hz,
                                       AudioFrame* audio_frame) override;
  int Ssrc() const override;
  int PreferredSampleRate() const override;

  // Syncable
  uint32_t id() const override;
  absl::optional<Syncable::Info> GetInfo() const override;
  bool GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                              int64_t* time_ms) const override;
  void SetEstimatedPlayoutNtpTimestampMs(int64_t ntp_timestamp_ms,
                                         int64_t time_ms) override;
  void SetMinimumPlayoutDelay(int delay_ms) override;

  void AssociateSendStream(AudioSendStream* send_stream);
  void DeliverRtcp(const uint8_t* packet, size_t length);
  const webrtc::AudioReceiveStream::Config& config() const;
  const AudioSendStream* GetAssociatedSendStreamForTesting() const;

 private:
  static void ConfigureStream(AudioReceiveStream* stream,
                              const Config& new_config,
                              bool first_time);

  AudioState* audio_state() const;

  rtc::ThreadChecker worker_thread_checker_;
  rtc::ThreadChecker module_process_thread_checker_;
  webrtc::AudioReceiveStream::Config config_;
  rtc::scoped_refptr<webrtc::AudioState> audio_state_;
  const std::unique_ptr<voe::ChannelReceiveInterface> channel_receive_;
  SourceTracker source_tracker_;
  AudioSendStream* associated_send_stream_ = nullptr;

  bool playing_ RTC_GUARDED_BY(worker_thread_checker_) = false;

  std::unique_ptr<RtpStreamReceiverInterface> rtp_stream_receiver_;

  RTC_DISALLOW_IMPLICIT_CONSTRUCTORS(AudioReceiveStream);
};
}  // namespace internal
}  // namespace webrtc

#endif  // AUDIO_AUDIO_RECEIVE_STREAM_H_
