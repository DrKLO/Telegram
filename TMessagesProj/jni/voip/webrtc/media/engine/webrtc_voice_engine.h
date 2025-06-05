/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_WEBRTC_VOICE_ENGINE_H_
#define MEDIA_ENGINE_WEBRTC_VOICE_ENGINE_H_

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/functional/any_invocable.h"
#include "absl/strings/string_view.h"
#include "absl/types/optional.h"
#include "api/audio/audio_frame_processor.h"
#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/audio_codec_pair_id.h"
#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/audio_codecs/audio_format.h"
#include "api/audio_options.h"
#include "api/call/audio_sink.h"
#include "api/call/transport.h"
#include "api/crypto/crypto_options.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/crypto/frame_encryptor_interface.h"
#include "api/field_trials_view.h"
#include "api/frame_transformer_interface.h"
#include "api/rtc_error.h"
#include "api/rtp_parameters.h"
#include "api/rtp_sender_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/task_queue/task_queue_base.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/transport/rtp/rtp_source.h"
#include "call/audio_send_stream.h"
#include "call/audio_state.h"
#include "call/call.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/media_channel_impl.h"
#include "media/base/media_config.h"
#include "media/base/media_engine.h"
#include "media/base/rtp_utils.h"
#include "media/base/stream_params.h"
#include "modules/async_audio_processing/async_audio_processing.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/buffer.h"
#include "rtc_base/network/sent_packet.h"
#include "rtc_base/network_route.h"
#include "rtc_base/system/file_wrapper.h"

namespace webrtc {
class AudioFrameProcessor;
}

namespace cricket {

class AudioSource;

// WebRtcVoiceEngine is a class to be used with CompositeMediaEngine.
// It uses the WebRtc VoiceEngine library for audio handling.
class WebRtcVoiceEngine final : public VoiceEngineInterface {
  friend class WebRtcVoiceSendChannel;
  friend class WebRtcVoiceReceiveChannel;

 public:
  WebRtcVoiceEngine(
      webrtc::TaskQueueFactory* task_queue_factory,
      webrtc::AudioDeviceModule* adm,
      const rtc::scoped_refptr<webrtc::AudioEncoderFactory>& encoder_factory,
      const rtc::scoped_refptr<webrtc::AudioDecoderFactory>& decoder_factory,
      rtc::scoped_refptr<webrtc::AudioMixer> audio_mixer,
      rtc::scoped_refptr<webrtc::AudioProcessing> audio_processing,
      std::unique_ptr<webrtc::AudioFrameProcessor> owned_audio_frame_processor,
      const webrtc::FieldTrialsView& trials);

  WebRtcVoiceEngine() = delete;
  WebRtcVoiceEngine(const WebRtcVoiceEngine&) = delete;
  WebRtcVoiceEngine& operator=(const WebRtcVoiceEngine&) = delete;

  ~WebRtcVoiceEngine() override;

  // Does initialization that needs to occur on the worker thread.
  void Init() override;
  rtc::scoped_refptr<webrtc::AudioState> GetAudioState() const override;

  std::unique_ptr<VoiceMediaSendChannelInterface> CreateSendChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const AudioOptions& options,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::AudioCodecPairId codec_pair_id) override;

  std::unique_ptr<VoiceMediaReceiveChannelInterface> CreateReceiveChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const AudioOptions& options,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::AudioCodecPairId codec_pair_id) override;

  const std::vector<AudioCodec>& send_codecs() const override;
  const std::vector<AudioCodec>& recv_codecs() const override;
  std::vector<webrtc::RtpHeaderExtensionCapability> GetRtpHeaderExtensions()
      const override;

  // Starts AEC dump using an existing file. A maximum file size in bytes can be
  // specified. When the maximum file size is reached, logging is stopped and
  // the file is closed. If max_size_bytes is set to <= 0, no limit will be
  // used.
  bool StartAecDump(webrtc::FileWrapper file, int64_t max_size_bytes) override;

  // Stops AEC dump.
  void StopAecDump() override;

  absl::optional<webrtc::AudioDeviceModule::Stats> GetAudioDeviceStats()
      override;

 private:
  // Every option that is "set" will be applied. Every option not "set" will be
  // ignored. This allows us to selectively turn on and off different options
  // easily at any time.
  void ApplyOptions(const AudioOptions& options);

  webrtc::TaskQueueFactory* const task_queue_factory_;
  std::unique_ptr<webrtc::TaskQueueBase, webrtc::TaskQueueDeleter>
      low_priority_worker_queue_;

  webrtc::AudioDeviceModule* adm();
  webrtc::AudioProcessing* apm() const;
  webrtc::AudioState* audio_state();

  std::vector<AudioCodec> CollectCodecs(
      const std::vector<webrtc::AudioCodecSpec>& specs) const;

  webrtc::SequenceChecker signal_thread_checker_{
      webrtc::SequenceChecker::kDetached};
  webrtc::SequenceChecker worker_thread_checker_{
      webrtc::SequenceChecker::kDetached};

  // The audio device module.
  rtc::scoped_refptr<webrtc::AudioDeviceModule> adm_;
  rtc::scoped_refptr<webrtc::AudioEncoderFactory> encoder_factory_;
  rtc::scoped_refptr<webrtc::AudioDecoderFactory> decoder_factory_;
  rtc::scoped_refptr<webrtc::AudioMixer> audio_mixer_;
  // The audio processing module.
  rtc::scoped_refptr<webrtc::AudioProcessing> apm_;
  // Asynchronous audio processing.
  std::unique_ptr<webrtc::AudioFrameProcessor> audio_frame_processor_;
  // The primary instance of WebRtc VoiceEngine.
  rtc::scoped_refptr<webrtc::AudioState> audio_state_;
  std::vector<AudioCodec> send_codecs_;
  std::vector<AudioCodec> recv_codecs_;
  bool is_dumping_aec_ = false;
  bool initialized_ = false;

  // Jitter buffer settings for new streams.
  size_t audio_jitter_buffer_max_packets_ = 200;
  bool audio_jitter_buffer_fast_accelerate_ = false;
  int audio_jitter_buffer_min_delay_ms_ = 0;

  const bool minimized_remsampling_on_mobile_trial_enabled_;
};

class WebRtcVoiceSendChannel final : public MediaChannelUtil,
                                     public VoiceMediaSendChannelInterface {
 public:
  WebRtcVoiceSendChannel(WebRtcVoiceEngine* engine,
                         const MediaConfig& config,
                         const AudioOptions& options,
                         const webrtc::CryptoOptions& crypto_options,
                         webrtc::Call* call,
                         webrtc::AudioCodecPairId codec_pair_id);

  WebRtcVoiceSendChannel() = delete;
  WebRtcVoiceSendChannel(const WebRtcVoiceSendChannel&) = delete;
  WebRtcVoiceSendChannel& operator=(const WebRtcVoiceSendChannel&) = delete;

  ~WebRtcVoiceSendChannel() override;

  MediaType media_type() const override { return MEDIA_TYPE_AUDIO; }
  VideoMediaSendChannelInterface* AsVideoSendChannel() override {
    RTC_CHECK_NOTREACHED();
    return nullptr;
  }
  VoiceMediaSendChannelInterface* AsVoiceSendChannel() override { return this; }

  absl::optional<Codec> GetSendCodec() const override;

  // Functions imported from MediaChannelUtil
  void SetInterface(MediaChannelNetworkInterface* iface) override {
    MediaChannelUtil::SetInterface(iface);
  }

  bool HasNetworkInterface() const override {
    return MediaChannelUtil::HasNetworkInterface();
  }
  void SetExtmapAllowMixed(bool extmap_allow_mixed) override {
    MediaChannelUtil::SetExtmapAllowMixed(extmap_allow_mixed);
  }
  bool ExtmapAllowMixed() const override {
    return MediaChannelUtil::ExtmapAllowMixed();
  }

  const AudioOptions& options() const { return options_; }

  bool SetSenderParameters(const AudioSenderParameter& params) override;
  webrtc::RtpParameters GetRtpSendParameters(uint32_t ssrc) const override;
  webrtc::RTCError SetRtpSendParameters(
      uint32_t ssrc,
      const webrtc::RtpParameters& parameters,
      webrtc::SetParametersCallback callback) override;

  void SetSend(bool send) override;
  bool SetAudioSend(uint32_t ssrc,
                    bool enable,
                    const AudioOptions* options,
                    AudioSource* source) override;
  bool AddSendStream(const StreamParams& sp) override;
  bool RemoveSendStream(uint32_t ssrc) override;

  void SetSsrcListChangedCallback(
      absl::AnyInvocable<void(const std::set<uint32_t>&)> callback) override;

  // E2EE Frame API
  // Set a frame encryptor to a particular ssrc that will intercept all
  // outgoing audio payloads frames and attempt to encrypt them and forward the
  // result to the packetizer.
  void SetFrameEncryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameEncryptorInterface>
                             frame_encryptor) override;

  bool CanInsertDtmf() override;
  bool InsertDtmf(uint32_t ssrc, int event, int duration) override;

  void OnPacketSent(const rtc::SentPacket& sent_packet) override;
  void OnNetworkRouteChanged(absl::string_view transport_name,
                             const rtc::NetworkRoute& network_route) override;
  void OnReadyToSend(bool ready) override;
  bool GetStats(VoiceMediaSendInfo* info) override;

  // Sets a frame transformer between encoder and packetizer, to transform
  // encoded frames before sending them out the network.
  void SetEncoderToPacketizerFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;

  bool SenderNackEnabled() const override {
    if (!send_codec_spec_) {
      return false;
    }
    return send_codec_spec_->nack_enabled;
  }
  bool SenderNonSenderRttEnabled() const override {
    if (!send_codec_spec_) {
      return false;
    }
    return send_codec_spec_->enable_non_sender_rtt;
  }
  bool SendCodecHasNack() const override { return SenderNackEnabled(); }

  void SetSendCodecChangedCallback(
      absl::AnyInvocable<void()> callback) override {
    send_codec_changed_callback_ = std::move(callback);
  }

 private:
  bool SetOptions(const AudioOptions& options);
  bool SetSendCodecs(const std::vector<Codec>& codecs,
                     absl::optional<Codec> preferred_codec);
  bool SetLocalSource(uint32_t ssrc, AudioSource* source);
  bool MuteStream(uint32_t ssrc, bool mute);

  WebRtcVoiceEngine* engine() { return engine_; }
  bool SetMaxSendBitrate(int bps);
  void SetupRecording();

  webrtc::TaskQueueBase* const worker_thread_;
  webrtc::ScopedTaskSafety task_safety_;
  webrtc::SequenceChecker network_thread_checker_{
      webrtc::SequenceChecker::kDetached};

  WebRtcVoiceEngine* const engine_ = nullptr;
  std::vector<AudioCodec> send_codecs_;

  int max_send_bitrate_bps_ = 0;
  AudioOptions options_;
  absl::optional<int> dtmf_payload_type_;
  int dtmf_payload_freq_ = -1;
  bool enable_non_sender_rtt_ = false;
  bool send_ = false;
  webrtc::Call* const call_ = nullptr;

  const MediaConfig::Audio audio_config_;

  class WebRtcAudioSendStream;

  std::map<uint32_t, WebRtcAudioSendStream*> send_streams_;
  std::vector<webrtc::RtpExtension> send_rtp_extensions_;
  std::string mid_;

  absl::optional<webrtc::AudioSendStream::Config::SendCodecSpec>
      send_codec_spec_;

  // TODO(kwiberg): Per-SSRC codec pair IDs?
  const webrtc::AudioCodecPairId codec_pair_id_;

  // Per peer connection crypto options that last for the lifetime of the peer
  // connection.
  const webrtc::CryptoOptions crypto_options_;
  rtc::scoped_refptr<webrtc::FrameTransformerInterface>
      unsignaled_frame_transformer_;

  void FillSendCodecStats(VoiceMediaSendInfo* voice_media_info);

  // Callback invoked whenever the send codec changes.
  // TODO(bugs.webrtc.org/13931): Remove again when coupling isn't needed.
  absl::AnyInvocable<void()> send_codec_changed_callback_;
  // Callback invoked whenever the list of SSRCs changes.
  absl::AnyInvocable<void(const std::set<uint32_t>&)>
      ssrc_list_changed_callback_;
};

class WebRtcVoiceReceiveChannel final
    : public MediaChannelUtil,
      public VoiceMediaReceiveChannelInterface {
 public:
  WebRtcVoiceReceiveChannel(WebRtcVoiceEngine* engine,
                            const MediaConfig& config,
                            const AudioOptions& options,
                            const webrtc::CryptoOptions& crypto_options,
                            webrtc::Call* call,
                            webrtc::AudioCodecPairId codec_pair_id);

  WebRtcVoiceReceiveChannel() = delete;
  WebRtcVoiceReceiveChannel(const WebRtcVoiceReceiveChannel&) = delete;
  WebRtcVoiceReceiveChannel& operator=(const WebRtcVoiceReceiveChannel&) =
      delete;

  ~WebRtcVoiceReceiveChannel() override;

  MediaType media_type() const override { return MEDIA_TYPE_AUDIO; }

  VideoMediaReceiveChannelInterface* AsVideoReceiveChannel() override {
    RTC_CHECK_NOTREACHED();
    return nullptr;
  }
  VoiceMediaReceiveChannelInterface* AsVoiceReceiveChannel() override {
    return this;
  }

  const AudioOptions& options() const { return options_; }

  void SetInterface(MediaChannelNetworkInterface* iface) override {
    MediaChannelUtil::SetInterface(iface);
  }
  bool SetReceiverParameters(const AudioReceiverParameters& params) override;
  webrtc::RtpParameters GetRtpReceiverParameters(uint32_t ssrc) const override;
  webrtc::RtpParameters GetDefaultRtpReceiveParameters() const override;

  void SetPlayout(bool playout) override;
  bool AddRecvStream(const StreamParams& sp) override;
  bool RemoveRecvStream(uint32_t ssrc) override;
  void ResetUnsignaledRecvStream() override;
  absl::optional<uint32_t> GetUnsignaledSsrc() const override;

  void ChooseReceiverReportSsrc(const std::set<uint32_t>& choices) override;

  void OnDemuxerCriteriaUpdatePending() override;
  void OnDemuxerCriteriaUpdateComplete() override;

  // E2EE Frame API
  // Set a frame decryptor to a particular ssrc that will intercept all
  // incoming audio payloads and attempt to decrypt them before forwarding the
  // result.
  void SetFrameDecryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
                             frame_decryptor) override;

  bool SetOutputVolume(uint32_t ssrc, double volume) override;
  // Applies the new volume to current and future unsignaled streams.
  bool SetDefaultOutputVolume(double volume) override;

  bool SetBaseMinimumPlayoutDelayMs(uint32_t ssrc, int delay_ms) override;
  absl::optional<int> GetBaseMinimumPlayoutDelayMs(
      uint32_t ssrc) const override;

  void OnPacketReceived(const webrtc::RtpPacketReceived& packet) override;
  bool GetStats(VoiceMediaReceiveInfo* info,
                bool get_and_clear_legacy_stats) override;

  // Set the audio sink for an existing stream.
  void SetRawAudioSink(
      uint32_t ssrc,
      std::unique_ptr<webrtc::AudioSinkInterface> sink) override;
  // Will set the audio sink on the latest unsignaled stream, future or
  // current. Only one stream at a time will use the sink.
  void SetDefaultRawAudioSink(
      std::unique_ptr<webrtc::AudioSinkInterface> sink) override;

  std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const override;

  void SetDepacketizerToDecoderFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;

  void SetReceiveNackEnabled(bool enabled) override;
  void SetReceiveNonSenderRttEnabled(bool enabled) override;

 private:
  bool SetOptions(const AudioOptions& options);
  bool SetRecvCodecs(const std::vector<AudioCodec>& codecs);
  bool SetLocalSource(uint32_t ssrc, AudioSource* source);
  bool MuteStream(uint32_t ssrc, bool mute);

  WebRtcVoiceEngine* engine() { return engine_; }
  void SetupRecording();

  // Expected to be invoked once per packet that belongs to this channel that
  // can not be demuxed. Returns true if a default receive stream has been
  // created.
  bool MaybeCreateDefaultReceiveStream(const webrtc::RtpPacketReceived& packet);
  // Check if 'ssrc' is an unsignaled stream, and if so mark it as not being
  // unsignaled anymore (i.e. it is now removed, or signaled), and return true.
  bool MaybeDeregisterUnsignaledRecvStream(uint32_t ssrc);

  webrtc::TaskQueueBase* const worker_thread_;
  webrtc::ScopedTaskSafety task_safety_;
  webrtc::SequenceChecker network_thread_checker_{
      webrtc::SequenceChecker::kDetached};

  WebRtcVoiceEngine* const engine_ = nullptr;

  // TODO(kwiberg): decoder_map_ and recv_codecs_ store the exact same
  // information, in slightly different formats. Eliminate recv_codecs_.
  std::map<int, webrtc::SdpAudioFormat> decoder_map_;
  std::vector<AudioCodec> recv_codecs_;

  AudioOptions options_;
  bool recv_nack_enabled_ = false;
  bool enable_non_sender_rtt_ = false;
  bool playout_ = false;
  webrtc::Call* const call_ = nullptr;

  const MediaConfig::Audio audio_config_;

  // Queue of unsignaled SSRCs; oldest at the beginning.
  std::vector<uint32_t> unsignaled_recv_ssrcs_;

  // This is a stream param that comes from the remote description, but wasn't
  // signaled with any a=ssrc lines. It holds the information that was signaled
  // before the unsignaled receive stream is created when the first packet is
  // received.
  StreamParams unsignaled_stream_params_;

  // Volume for unsignaled streams, which may be set before the stream exists.
  double default_recv_volume_ = 1.0;

  // Delay for unsignaled streams, which may be set before the stream exists.
  int default_recv_base_minimum_delay_ms_ = 0;

  // Sink for latest unsignaled stream - may be set before the stream exists.
  std::unique_ptr<webrtc::AudioSinkInterface> default_sink_;
  // Default SSRC to use for RTCP receiver reports in case of no signaled
  // send streams. See: https://code.google.com/p/webrtc/issues/detail?id=4740
  // and https://code.google.com/p/chromium/issues/detail?id=547661
  uint32_t receiver_reports_ssrc_ = 0xFA17FA17u;

  std::string mid_;

  class WebRtcAudioReceiveStream;

  std::map<uint32_t, WebRtcAudioReceiveStream*> recv_streams_;
  std::vector<webrtc::RtpExtension> recv_rtp_extensions_;
  webrtc::RtpHeaderExtensionMap recv_rtp_extension_map_;

  absl::optional<webrtc::AudioSendStream::Config::SendCodecSpec>
      send_codec_spec_;

  // TODO(kwiberg): Per-SSRC codec pair IDs?
  const webrtc::AudioCodecPairId codec_pair_id_;

  // Per peer connection crypto options that last for the lifetime of the peer
  // connection.
  const webrtc::CryptoOptions crypto_options_;
  // Unsignaled streams have an option to have a frame decryptor set on them.
  rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
      unsignaled_frame_decryptor_;
  rtc::scoped_refptr<webrtc::FrameTransformerInterface>
      unsignaled_frame_transformer_;

  void FillReceiveCodecStats(VoiceMediaReceiveInfo* voice_media_info);
};

}  // namespace cricket

#endif  // MEDIA_ENGINE_WEBRTC_VOICE_ENGINE_H_
