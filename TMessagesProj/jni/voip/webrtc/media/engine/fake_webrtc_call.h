/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains fake implementations, for use in unit tests, of the
// following classes:
//
//   webrtc::Call
//   webrtc::AudioSendStream
//   webrtc::AudioReceiveStreamInterface
//   webrtc::VideoSendStream
//   webrtc::VideoReceiveStreamInterface

#ifndef MEDIA_ENGINE_FAKE_WEBRTC_CALL_H_
#define MEDIA_ENGINE_FAKE_WEBRTC_CALL_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/strings/string_view.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/video_frame.h"
#include "call/audio_receive_stream.h"
#include "call/audio_send_stream.h"
#include "call/call.h"
#include "call/flexfec_receive_stream.h"
#include "call/test/mock_rtp_transport_controller_send.h"
#include "call/video_receive_stream.h"
#include "call/video_send_stream.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/buffer.h"
#include "test/scoped_key_value_config.h"

namespace cricket {
class FakeAudioSendStream final : public webrtc::AudioSendStream {
 public:
  struct TelephoneEvent {
    int payload_type = -1;
    int payload_frequency = -1;
    int event_code = 0;
    int duration_ms = 0;
  };

  explicit FakeAudioSendStream(int id,
                               const webrtc::AudioSendStream::Config& config);

  int id() const { return id_; }
  const webrtc::AudioSendStream::Config& GetConfig() const override;
  void SetStats(const webrtc::AudioSendStream::Stats& stats);
  TelephoneEvent GetLatestTelephoneEvent() const;
  bool IsSending() const { return sending_; }
  bool muted() const { return muted_; }

 private:
  // webrtc::AudioSendStream implementation.
  void Reconfigure(const webrtc::AudioSendStream::Config& config,
                   webrtc::SetParametersCallback callback) override;
  void Start() override { sending_ = true; }
  void Stop() override { sending_ = false; }
  void SendAudioData(std::unique_ptr<webrtc::AudioFrame> audio_frame) override {
  }
  bool SendTelephoneEvent(int payload_type,
                          int payload_frequency,
                          int event,
                          int duration_ms) override;
  void SetMuted(bool muted) override;
  webrtc::AudioSendStream::Stats GetStats() const override;
  webrtc::AudioSendStream::Stats GetStats(
      bool has_remote_tracks) const override;

  int id_ = -1;
  TelephoneEvent latest_telephone_event_;
  webrtc::AudioSendStream::Config config_;
  webrtc::AudioSendStream::Stats stats_;
  bool sending_ = false;
  bool muted_ = false;
};

class FakeAudioReceiveStream final
    : public webrtc::AudioReceiveStreamInterface {
 public:
  explicit FakeAudioReceiveStream(
      int id,
      const webrtc::AudioReceiveStreamInterface::Config& config);

  int id() const { return id_; }
  const webrtc::AudioReceiveStreamInterface::Config& GetConfig() const;
  void SetStats(const webrtc::AudioReceiveStreamInterface::Stats& stats);
  int received_packets() const { return received_packets_; }
  bool VerifyLastPacket(const uint8_t* data, size_t length) const;
  const webrtc::AudioSinkInterface* sink() const { return sink_; }
  float gain() const { return gain_; }
  bool DeliverRtp(const uint8_t* packet, size_t length, int64_t packet_time_us);
  bool started() const { return started_; }
  int base_mininum_playout_delay_ms() const {
    return base_mininum_playout_delay_ms_;
  }

  void SetLocalSsrc(uint32_t local_ssrc) {
    config_.rtp.local_ssrc = local_ssrc;
  }

  void SetSyncGroup(absl::string_view sync_group) {
    config_.sync_group = std::string(sync_group);
  }

  uint32_t remote_ssrc() const override { return config_.rtp.remote_ssrc; }
  void Start() override { started_ = true; }
  void Stop() override { started_ = false; }
  bool IsRunning() const override { return started_; }
  void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;
  void SetDecoderMap(
      std::map<int, webrtc::SdpAudioFormat> decoder_map) override;
  void SetNackHistory(int history_ms) override;
  void SetNonSenderRttMeasurement(bool enabled) override;
  void SetFrameDecryptor(rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
                             frame_decryptor) override;

  webrtc::AudioReceiveStreamInterface::Stats GetStats(
      bool get_and_clear_legacy_stats) const override;
  void SetSink(webrtc::AudioSinkInterface* sink) override;
  void SetGain(float gain) override;
  bool SetBaseMinimumPlayoutDelayMs(int delay_ms) override {
    base_mininum_playout_delay_ms_ = delay_ms;
    return true;
  }
  int GetBaseMinimumPlayoutDelayMs() const override {
    return base_mininum_playout_delay_ms_;
  }
  std::vector<webrtc::RtpSource> GetSources() const override {
    return std::vector<webrtc::RtpSource>();
  }

 private:
  int id_ = -1;
  webrtc::AudioReceiveStreamInterface::Config config_;
  webrtc::AudioReceiveStreamInterface::Stats stats_;
  int received_packets_ = 0;
  webrtc::AudioSinkInterface* sink_ = nullptr;
  float gain_ = 1.0f;
  rtc::Buffer last_packet_;
  bool started_ = false;
  int base_mininum_playout_delay_ms_ = 0;
};

class FakeVideoSendStream final
    : public webrtc::VideoSendStream,
      public rtc::VideoSinkInterface<webrtc::VideoFrame> {
 public:
  FakeVideoSendStream(webrtc::VideoSendStream::Config config,
                      webrtc::VideoEncoderConfig encoder_config);
  ~FakeVideoSendStream() override;
  const webrtc::VideoSendStream::Config& GetConfig() const;
  const webrtc::VideoEncoderConfig& GetEncoderConfig() const;
  const std::vector<webrtc::VideoStream>& GetVideoStreams() const;

  bool IsSending() const;
  bool GetVp8Settings(webrtc::VideoCodecVP8* settings) const;
  bool GetVp9Settings(webrtc::VideoCodecVP9* settings) const;
  bool GetH264Settings(webrtc::VideoCodecH264* settings) const;
  bool GetAv1Settings(webrtc::VideoCodecAV1* settings) const;

  int GetNumberOfSwappedFrames() const;
  int GetLastWidth() const;
  int GetLastHeight() const;
  int64_t GetLastTimestamp() const;
  void SetStats(const webrtc::VideoSendStream::Stats& stats);
  int num_encoder_reconfigurations() const {
    return num_encoder_reconfigurations_;
  }

  bool resolution_scaling_enabled() const {
    return resolution_scaling_enabled_;
  }
  bool framerate_scaling_enabled() const { return framerate_scaling_enabled_; }
  void InjectVideoSinkWants(const rtc::VideoSinkWants& wants);

  rtc::VideoSourceInterface<webrtc::VideoFrame>* source() const {
    return source_;
  }
  void GenerateKeyFrame(const std::vector<std::string>& rids);
  const std::vector<std::string>& GetKeyFramesRequested() const {
    return keyframes_requested_by_rid_;
  }

 private:
  // rtc::VideoSinkInterface<VideoFrame> implementation.
  void OnFrame(const webrtc::VideoFrame& frame) override;

  // webrtc::VideoSendStream implementation.
  void Start() override;
  void Stop() override;
  bool started() override { return IsSending(); }
  void AddAdaptationResource(
      rtc::scoped_refptr<webrtc::Resource> resource) override;
  std::vector<rtc::scoped_refptr<webrtc::Resource>> GetAdaptationResources()
      override;
  void SetSource(
      rtc::VideoSourceInterface<webrtc::VideoFrame>* source,
      const webrtc::DegradationPreference& degradation_preference) override;
  webrtc::VideoSendStream::Stats GetStats() override;

  void ReconfigureVideoEncoder(webrtc::VideoEncoderConfig config) override;
  void ReconfigureVideoEncoder(webrtc::VideoEncoderConfig config,
                               webrtc::SetParametersCallback callback) override;

  bool sending_;
  webrtc::VideoSendStream::Config config_;
  webrtc::VideoEncoderConfig encoder_config_;
  std::vector<webrtc::VideoStream> video_streams_;
  rtc::VideoSinkWants sink_wants_;

  bool codec_settings_set_;
  union CodecSpecificSettings {
    webrtc::VideoCodecVP8 vp8;
    webrtc::VideoCodecVP9 vp9;
    webrtc::VideoCodecH264 h264;
    webrtc::VideoCodecAV1 av1;
  } codec_specific_settings_;
  bool resolution_scaling_enabled_;
  bool framerate_scaling_enabled_;
  rtc::VideoSourceInterface<webrtc::VideoFrame>* source_;
  int num_swapped_frames_;
  absl::optional<webrtc::VideoFrame> last_frame_;
  webrtc::VideoSendStream::Stats stats_;
  int num_encoder_reconfigurations_ = 0;
  std::vector<std::string> keyframes_requested_by_rid_;
};

class FakeVideoReceiveStream final
    : public webrtc::VideoReceiveStreamInterface {
 public:
  explicit FakeVideoReceiveStream(
      webrtc::VideoReceiveStreamInterface::Config config);

  const webrtc::VideoReceiveStreamInterface::Config& GetConfig() const;

  bool IsReceiving() const;

  void InjectFrame(const webrtc::VideoFrame& frame);

  void SetStats(const webrtc::VideoReceiveStreamInterface::Stats& stats);

  std::vector<webrtc::RtpSource> GetSources() const override {
    return std::vector<webrtc::RtpSource>();
  }

  int base_mininum_playout_delay_ms() const {
    return base_mininum_playout_delay_ms_;
  }

  void SetLocalSsrc(uint32_t local_ssrc) {
    config_.rtp.local_ssrc = local_ssrc;
  }

  void UpdateRtxSsrc(uint32_t ssrc) { config_.rtp.rtx_ssrc = ssrc; }

  void SetFrameDecryptor(rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
                             frame_decryptor) override {}

  void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override {}

  RecordingState SetAndGetRecordingState(RecordingState state,
                                         bool generate_key_frame) override {
    return RecordingState();
  }
  void GenerateKeyFrame() override {}

  void SetRtcpMode(webrtc::RtcpMode mode) override {
    config_.rtp.rtcp_mode = mode;
  }

  void SetFlexFecProtection(webrtc::RtpPacketSinkInterface* sink) override {
    config_.rtp.packet_sink_ = sink;
    config_.rtp.protected_by_flexfec = (sink != nullptr);
  }

  void SetLossNotificationEnabled(bool enabled) override {
    config_.rtp.lntf.enabled = enabled;
  }

  void SetNackHistory(webrtc::TimeDelta history) override {
    config_.rtp.nack.rtp_history_ms = history.ms();
  }

  void SetProtectionPayloadTypes(int red_payload_type,
                                 int ulpfec_payload_type) override {
    config_.rtp.red_payload_type = red_payload_type;
    config_.rtp.ulpfec_payload_type = ulpfec_payload_type;
  }

  void SetRtcpXr(Config::Rtp::RtcpXr rtcp_xr) override {
    config_.rtp.rtcp_xr = rtcp_xr;
  }

  void SetAssociatedPayloadTypes(std::map<int, int> associated_payload_types) {
    config_.rtp.rtx_associated_payload_types =
        std::move(associated_payload_types);
  }

  void Start() override;
  void Stop() override;

  webrtc::VideoReceiveStreamInterface::Stats GetStats() const override;

  bool SetBaseMinimumPlayoutDelayMs(int delay_ms) override {
    base_mininum_playout_delay_ms_ = delay_ms;
    return true;
  }

  int GetBaseMinimumPlayoutDelayMs() const override {
    return base_mininum_playout_delay_ms_;
  }

 private:
  webrtc::VideoReceiveStreamInterface::Config config_;
  bool receiving_;
  webrtc::VideoReceiveStreamInterface::Stats stats_;

  int base_mininum_playout_delay_ms_ = 0;
};

class FakeFlexfecReceiveStream final : public webrtc::FlexfecReceiveStream {
 public:
  explicit FakeFlexfecReceiveStream(
      const webrtc::FlexfecReceiveStream::Config config);

  void SetLocalSsrc(uint32_t local_ssrc) {
    config_.rtp.local_ssrc = local_ssrc;
  }

  void SetRtcpMode(webrtc::RtcpMode mode) override { config_.rtcp_mode = mode; }

  int payload_type() const override { return config_.payload_type; }
  void SetPayloadType(int payload_type) override {
    config_.payload_type = payload_type;
  }

  const webrtc::FlexfecReceiveStream::Config& GetConfig() const;

  uint32_t remote_ssrc() const { return config_.rtp.remote_ssrc; }

  const webrtc::ReceiveStatistics* GetStats() const override { return nullptr; }

 private:
  void OnRtpPacket(const webrtc::RtpPacketReceived& packet) override;

  webrtc::FlexfecReceiveStream::Config config_;
};

class FakeCall final : public webrtc::Call, public webrtc::PacketReceiver {
 public:
  explicit FakeCall(webrtc::test::ScopedKeyValueConfig* field_trials = nullptr);
  FakeCall(webrtc::TaskQueueBase* worker_thread,
           webrtc::TaskQueueBase* network_thread,
           webrtc::test::ScopedKeyValueConfig* field_trials = nullptr);
  ~FakeCall() override;

  webrtc::MockRtpTransportControllerSend* GetMockTransportControllerSend() {
    return &transport_controller_send_;
  }

  const std::vector<FakeVideoSendStream*>& GetVideoSendStreams();
  const std::vector<FakeVideoReceiveStream*>& GetVideoReceiveStreams();

  const std::vector<FakeAudioSendStream*>& GetAudioSendStreams();
  const FakeAudioSendStream* GetAudioSendStream(uint32_t ssrc);
  const std::vector<FakeAudioReceiveStream*>& GetAudioReceiveStreams();
  const FakeAudioReceiveStream* GetAudioReceiveStream(uint32_t ssrc);
  const FakeVideoReceiveStream* GetVideoReceiveStream(uint32_t ssrc);

  const std::vector<FakeFlexfecReceiveStream*>& GetFlexfecReceiveStreams();

  rtc::SentPacket last_sent_packet() const { return last_sent_packet_; }
  const webrtc::RtpPacketReceived& last_received_rtp_packet() const {
    return last_received_rtp_packet_;
  }
  size_t GetDeliveredPacketsForSsrc(uint32_t ssrc) const {
    auto it = delivered_packets_by_ssrc_.find(ssrc);
    return it != delivered_packets_by_ssrc_.end() ? it->second : 0u;
  }

  // This is useful if we care about the last media packet (with id populated)
  // but not the last ICE packet (with -1 ID).
  int last_sent_nonnegative_packet_id() const {
    return last_sent_nonnegative_packet_id_;
  }

  webrtc::NetworkState GetNetworkState(webrtc::MediaType media) const;
  int GetNumCreatedSendStreams() const;
  int GetNumCreatedReceiveStreams() const;
  void SetStats(const webrtc::Call::Stats& stats);

  void SetClientBitratePreferences(
      const webrtc::BitrateSettings& preferences) override {}

  void SetFieldTrial(const std::string& field_trial_string) {
    trials_overrides_ = std::make_unique<webrtc::test::ScopedKeyValueConfig>(
        *trials_, field_trial_string);
  }

  const webrtc::FieldTrialsView& trials() const override { return *trials_; }

 private:
  webrtc::AudioSendStream* CreateAudioSendStream(
      const webrtc::AudioSendStream::Config& config) override;
  void DestroyAudioSendStream(webrtc::AudioSendStream* send_stream) override;

  webrtc::AudioReceiveStreamInterface* CreateAudioReceiveStream(
      const webrtc::AudioReceiveStreamInterface::Config& config) override;
  void DestroyAudioReceiveStream(
      webrtc::AudioReceiveStreamInterface* receive_stream) override;

  webrtc::VideoSendStream* CreateVideoSendStream(
      webrtc::VideoSendStream::Config config,
      webrtc::VideoEncoderConfig encoder_config) override;
  void DestroyVideoSendStream(webrtc::VideoSendStream* send_stream) override;

  webrtc::VideoReceiveStreamInterface* CreateVideoReceiveStream(
      webrtc::VideoReceiveStreamInterface::Config config) override;
  void DestroyVideoReceiveStream(
      webrtc::VideoReceiveStreamInterface* receive_stream) override;

  webrtc::FlexfecReceiveStream* CreateFlexfecReceiveStream(
      const webrtc::FlexfecReceiveStream::Config config) override;
  void DestroyFlexfecReceiveStream(
      webrtc::FlexfecReceiveStream* receive_stream) override;

  void AddAdaptationResource(
      rtc::scoped_refptr<webrtc::Resource> resource) override;

  webrtc::PacketReceiver* Receiver() override;

  void DeliverRtcpPacket(rtc::CopyOnWriteBuffer packet) override {}

  void DeliverRtpPacket(
      webrtc::MediaType media_type,
      webrtc::RtpPacketReceived packet,
      OnUndemuxablePacketHandler un_demuxable_packet_handler) override;

  bool DeliverPacketInternal(webrtc::MediaType media_type,
                             uint32_t ssrc,
                             const rtc::CopyOnWriteBuffer& packet,
                             webrtc::Timestamp arrival_time);

  webrtc::RtpTransportControllerSendInterface* GetTransportControllerSend()
      override {
    return &transport_controller_send_;
  }

  webrtc::Call::Stats GetStats() const override;

  webrtc::TaskQueueBase* network_thread() const override;
  webrtc::TaskQueueBase* worker_thread() const override;

  void SignalChannelNetworkState(webrtc::MediaType media,
                                 webrtc::NetworkState state) override;
  void OnAudioTransportOverheadChanged(
      int transport_overhead_per_packet) override;
  void OnLocalSsrcUpdated(webrtc::AudioReceiveStreamInterface& stream,
                          uint32_t local_ssrc) override;
  void OnLocalSsrcUpdated(webrtc::VideoReceiveStreamInterface& stream,
                          uint32_t local_ssrc) override;
  void OnLocalSsrcUpdated(webrtc::FlexfecReceiveStream& stream,
                          uint32_t local_ssrc) override;
  void OnUpdateSyncGroup(webrtc::AudioReceiveStreamInterface& stream,
                         absl::string_view sync_group) override;
  void OnSentPacket(const rtc::SentPacket& sent_packet) override;

  webrtc::TaskQueueBase* const network_thread_;
  webrtc::TaskQueueBase* const worker_thread_;

  ::testing::NiceMock<webrtc::MockRtpTransportControllerSend>
      transport_controller_send_;

  webrtc::NetworkState audio_network_state_;
  webrtc::NetworkState video_network_state_;
  rtc::SentPacket last_sent_packet_;
  webrtc::RtpPacketReceived last_received_rtp_packet_;
  int last_sent_nonnegative_packet_id_ = -1;
  int next_stream_id_ = 665;
  webrtc::Call::Stats stats_;
  std::vector<FakeVideoSendStream*> video_send_streams_;
  std::vector<FakeAudioSendStream*> audio_send_streams_;
  std::vector<FakeVideoReceiveStream*> video_receive_streams_;
  std::vector<FakeAudioReceiveStream*> audio_receive_streams_;
  std::vector<FakeFlexfecReceiveStream*> flexfec_receive_streams_;
  std::map<uint32_t, size_t> delivered_packets_by_ssrc_;

  int num_created_send_streams_;
  int num_created_receive_streams_;

  // The field trials that are in use, either supplied by caller
  // or pointer to &fallback_trials_.
  webrtc::test::ScopedKeyValueConfig* trials_;

  // fallback_trials_ is used if caller does not provide any field trials.
  webrtc::test::ScopedKeyValueConfig fallback_trials_;

  // An extra field trial that can be set using SetFieldTrial.
  std::unique_ptr<webrtc::test::ScopedKeyValueConfig> trials_overrides_;
};

}  // namespace cricket
#endif  // MEDIA_ENGINE_FAKE_WEBRTC_CALL_H_
