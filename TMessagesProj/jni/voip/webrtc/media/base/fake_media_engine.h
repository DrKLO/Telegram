/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_FAKE_MEDIA_ENGINE_H_
#define MEDIA_BASE_FAKE_MEDIA_ENGINE_H_

#include <atomic>
#include <list>
#include <map>
#include <memory>
#include <set>
#include <string>
#include <tuple>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/functional/any_invocable.h"
#include "api/call/audio_sink.h"
#include "api/media_types.h"
#include "media/base/audio_source.h"
#include "media/base/media_channel.h"
#include "media/base/media_channel_impl.h"
#include "media/base/media_engine.h"
#include "media/base/rtp_utils.h"
#include "media/base/stream_params.h"
#include "media/engine/webrtc_video_engine.h"
#include "modules/audio_processing/include/audio_processing.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/network_route.h"
#include "rtc_base/thread.h"

using webrtc::RtpExtension;

namespace cricket {

class FakeMediaEngine;
class FakeVideoEngine;
class FakeVoiceEngine;

// A common helper class that handles sending and receiving RTP/RTCP packets.
template <class Base>
class RtpReceiveChannelHelper : public Base, public MediaChannelUtil {
 public:
  explicit RtpReceiveChannelHelper(webrtc::TaskQueueBase* network_thread)
      : MediaChannelUtil(network_thread),
        playout_(false),
        fail_set_recv_codecs_(false),
        transport_overhead_per_packet_(0),
        num_network_route_changes_(0) {}
  virtual ~RtpReceiveChannelHelper() = default;
  const std::vector<RtpExtension>& recv_extensions() {
    return recv_extensions_;
  }
  bool playout() const { return playout_; }
  const std::list<std::string>& rtp_packets() const { return rtp_packets_; }
  const std::list<std::string>& rtcp_packets() const { return rtcp_packets_; }

  bool SendRtcp(const void* data, size_t len) {
    rtc::CopyOnWriteBuffer packet(reinterpret_cast<const uint8_t*>(data), len,
                                  kMaxRtpPacketLen);
    return Base::SendRtcp(&packet, rtc::PacketOptions());
  }

  bool CheckRtp(const void* data, size_t len) {
    bool success = !rtp_packets_.empty();
    if (success) {
      std::string packet = rtp_packets_.front();
      rtp_packets_.pop_front();
      success = (packet == std::string(static_cast<const char*>(data), len));
    }
    return success;
  }
  bool CheckRtcp(const void* data, size_t len) {
    bool success = !rtcp_packets_.empty();
    if (success) {
      std::string packet = rtcp_packets_.front();
      rtcp_packets_.pop_front();
      success = (packet == std::string(static_cast<const char*>(data), len));
    }
    return success;
  }
  bool CheckNoRtp() { return rtp_packets_.empty(); }
  bool CheckNoRtcp() { return rtcp_packets_.empty(); }
  void set_fail_set_recv_codecs(bool fail) { fail_set_recv_codecs_ = fail; }
  void ResetUnsignaledRecvStream() override {}
  absl::optional<uint32_t> GetUnsignaledSsrc() const override {
    return absl::nullopt;
  }
  void ChooseReceiverReportSsrc(const std::set<uint32_t>& choices) override {}

  virtual bool SetLocalSsrc(const StreamParams& sp) { return true; }
  void OnDemuxerCriteriaUpdatePending() override {}
  void OnDemuxerCriteriaUpdateComplete() override {}

  bool AddRecvStream(const StreamParams& sp) override {
    if (absl::c_linear_search(receive_streams_, sp)) {
      return false;
    }
    receive_streams_.push_back(sp);
    rtp_receive_parameters_[sp.first_ssrc()] =
        CreateRtpParametersWithEncodings(sp);
    return true;
  }
  bool RemoveRecvStream(uint32_t ssrc) override {
    auto parameters_iterator = rtp_receive_parameters_.find(ssrc);
    if (parameters_iterator != rtp_receive_parameters_.end()) {
      rtp_receive_parameters_.erase(parameters_iterator);
    }
    return RemoveStreamBySsrc(&receive_streams_, ssrc);
  }

  webrtc::RtpParameters GetRtpReceiverParameters(uint32_t ssrc) const override {
    auto parameters_iterator = rtp_receive_parameters_.find(ssrc);
    if (parameters_iterator != rtp_receive_parameters_.end()) {
      return parameters_iterator->second;
    }
    return webrtc::RtpParameters();
  }
  webrtc::RtpParameters GetDefaultRtpReceiveParameters() const override {
    return webrtc::RtpParameters();
  }

  const std::vector<StreamParams>& recv_streams() const {
    return receive_streams_;
  }
  bool HasRecvStream(uint32_t ssrc) const {
    return GetStreamBySsrc(receive_streams_, ssrc) != nullptr;
  }

  const RtcpParameters& recv_rtcp_parameters() { return recv_rtcp_parameters_; }

  int transport_overhead_per_packet() const {
    return transport_overhead_per_packet_;
  }

  rtc::NetworkRoute last_network_route() const { return last_network_route_; }
  int num_network_route_changes() const { return num_network_route_changes_; }
  void set_num_network_route_changes(int changes) {
    num_network_route_changes_ = changes;
  }

  void OnRtcpPacketReceived(rtc::CopyOnWriteBuffer* packet,
                            int64_t packet_time_us) {
    rtcp_packets_.push_back(std::string(packet->cdata<char>(), packet->size()));
  }

  void SetFrameDecryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
                             frame_decryptor) override {}

  void SetDepacketizerToDecoderFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override {}

  void SetInterface(MediaChannelNetworkInterface* iface) override {
    network_interface_ = iface;
    MediaChannelUtil::SetInterface(iface);
  }

 protected:
  void set_playout(bool playout) { playout_ = playout; }
  bool SetRecvRtpHeaderExtensions(const std::vector<RtpExtension>& extensions) {
    recv_extensions_ = extensions;
    return true;
  }
  void set_recv_rtcp_parameters(const RtcpParameters& params) {
    recv_rtcp_parameters_ = params;
  }
  void OnPacketReceived(const webrtc::RtpPacketReceived& packet) override {
    rtp_packets_.push_back(
        std::string(packet.Buffer().cdata<char>(), packet.size()));
  }
  bool fail_set_recv_codecs() const { return fail_set_recv_codecs_; }

 private:
  bool playout_;
  std::vector<RtpExtension> recv_extensions_;
  std::list<std::string> rtp_packets_;
  std::list<std::string> rtcp_packets_;
  std::vector<StreamParams> receive_streams_;
  RtcpParameters recv_rtcp_parameters_;
  std::map<uint32_t, webrtc::RtpParameters> rtp_receive_parameters_;
  bool fail_set_recv_codecs_;
  std::string rtcp_cname_;
  int transport_overhead_per_packet_;
  rtc::NetworkRoute last_network_route_;
  int num_network_route_changes_;
  MediaChannelNetworkInterface* network_interface_ = nullptr;
};

// A common helper class that handles sending and receiving RTP/RTCP packets.
template <class Base>
class RtpSendChannelHelper : public Base, public MediaChannelUtil {
 public:
  explicit RtpSendChannelHelper(webrtc::TaskQueueBase* network_thread)
      : MediaChannelUtil(network_thread),
        sending_(false),
        fail_set_send_codecs_(false),
        send_ssrc_(0),
        ready_to_send_(false),
        transport_overhead_per_packet_(0),
        num_network_route_changes_(0) {}
  virtual ~RtpSendChannelHelper() = default;
  const std::vector<RtpExtension>& send_extensions() {
    return send_extensions_;
  }
  bool sending() const { return sending_; }
  const std::list<std::string>& rtp_packets() const { return rtp_packets_; }
  const std::list<std::string>& rtcp_packets() const { return rtcp_packets_; }

  bool SendPacket(const void* data,
                  size_t len,
                  const rtc::PacketOptions& options) {
    if (!sending_) {
      return false;
    }
    rtc::CopyOnWriteBuffer packet(reinterpret_cast<const uint8_t*>(data), len,
                                  kMaxRtpPacketLen);
    return MediaChannelUtil::SendPacket(&packet, options);
  }
  bool SendRtcp(const void* data, size_t len) {
    rtc::CopyOnWriteBuffer packet(reinterpret_cast<const uint8_t*>(data), len,
                                  kMaxRtpPacketLen);
    return MediaChannelUtil::SendRtcp(&packet, rtc::PacketOptions());
  }

  bool CheckRtp(const void* data, size_t len) {
    bool success = !rtp_packets_.empty();
    if (success) {
      std::string packet = rtp_packets_.front();
      rtp_packets_.pop_front();
      success = (packet == std::string(static_cast<const char*>(data), len));
    }
    return success;
  }
  bool CheckRtcp(const void* data, size_t len) {
    bool success = !rtcp_packets_.empty();
    if (success) {
      std::string packet = rtcp_packets_.front();
      rtcp_packets_.pop_front();
      success = (packet == std::string(static_cast<const char*>(data), len));
    }
    return success;
  }
  bool CheckNoRtp() { return rtp_packets_.empty(); }
  bool CheckNoRtcp() { return rtcp_packets_.empty(); }
  void set_fail_set_send_codecs(bool fail) { fail_set_send_codecs_ = fail; }
  bool AddSendStream(const StreamParams& sp) override {
    if (absl::c_linear_search(send_streams_, sp)) {
      return false;
    }
    send_streams_.push_back(sp);
    rtp_send_parameters_[sp.first_ssrc()] =
        CreateRtpParametersWithEncodings(sp);

    if (ssrc_list_changed_callback_) {
      std::set<uint32_t> ssrcs_in_use;
      for (const auto& send_stream : send_streams_) {
        ssrcs_in_use.insert(send_stream.first_ssrc());
      }
      ssrc_list_changed_callback_(ssrcs_in_use);
    }

    return true;
  }
  bool RemoveSendStream(uint32_t ssrc) override {
    auto parameters_iterator = rtp_send_parameters_.find(ssrc);
    if (parameters_iterator != rtp_send_parameters_.end()) {
      rtp_send_parameters_.erase(parameters_iterator);
    }
    return RemoveStreamBySsrc(&send_streams_, ssrc);
  }
  void SetSsrcListChangedCallback(
      absl::AnyInvocable<void(const std::set<uint32_t>&)> callback) override {
    ssrc_list_changed_callback_ = std::move(callback);
  }

  void SetExtmapAllowMixed(bool extmap_allow_mixed) override {
    return MediaChannelUtil::SetExtmapAllowMixed(extmap_allow_mixed);
  }
  bool ExtmapAllowMixed() const override {
    return MediaChannelUtil::ExtmapAllowMixed();
  }

  webrtc::RtpParameters GetRtpSendParameters(uint32_t ssrc) const override {
    auto parameters_iterator = rtp_send_parameters_.find(ssrc);
    if (parameters_iterator != rtp_send_parameters_.end()) {
      return parameters_iterator->second;
    }
    return webrtc::RtpParameters();
  }
  webrtc::RTCError SetRtpSendParameters(
      uint32_t ssrc,
      const webrtc::RtpParameters& parameters,
      webrtc::SetParametersCallback callback) override {
    auto parameters_iterator = rtp_send_parameters_.find(ssrc);
    if (parameters_iterator != rtp_send_parameters_.end()) {
      auto result = CheckRtpParametersInvalidModificationAndValues(
          parameters_iterator->second, parameters);
      if (!result.ok()) {
        return webrtc::InvokeSetParametersCallback(callback, result);
      }

      parameters_iterator->second = parameters;

      return webrtc::InvokeSetParametersCallback(callback,
                                                 webrtc::RTCError::OK());
    }
    // Replicate the behavior of the real media channel: return false
    // when setting parameters for unknown SSRCs.
    return InvokeSetParametersCallback(
        callback, webrtc::RTCError(webrtc::RTCErrorType::INTERNAL_ERROR));
  }

  bool IsStreamMuted(uint32_t ssrc) const {
    bool ret = muted_streams_.find(ssrc) != muted_streams_.end();
    // If |ssrc = 0| check if the first send stream is muted.
    if (!ret && ssrc == 0 && !send_streams_.empty()) {
      return muted_streams_.find(send_streams_[0].first_ssrc()) !=
             muted_streams_.end();
    }
    return ret;
  }
  const std::vector<StreamParams>& send_streams() const {
    return send_streams_;
  }
  bool HasSendStream(uint32_t ssrc) const {
    return GetStreamBySsrc(send_streams_, ssrc) != nullptr;
  }
  // TODO(perkj): This is to support legacy unit test that only check one
  // sending stream.
  uint32_t send_ssrc() const {
    if (send_streams_.empty())
      return 0;
    return send_streams_[0].first_ssrc();
  }

  const RtcpParameters& send_rtcp_parameters() { return send_rtcp_parameters_; }

  bool ready_to_send() const { return ready_to_send_; }

  int transport_overhead_per_packet() const {
    return transport_overhead_per_packet_;
  }

  rtc::NetworkRoute last_network_route() const { return last_network_route_; }
  int num_network_route_changes() const { return num_network_route_changes_; }
  void set_num_network_route_changes(int changes) {
    num_network_route_changes_ = changes;
  }

  void OnRtcpPacketReceived(rtc::CopyOnWriteBuffer* packet,
                            int64_t packet_time_us) {
    rtcp_packets_.push_back(std::string(packet->cdata<char>(), packet->size()));
  }

  // Stuff that deals with encryptors, transformers and the like
  void SetFrameEncryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameEncryptorInterface>
                             frame_encryptor) override {}
  void SetEncoderToPacketizerFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override {}

  void SetInterface(MediaChannelNetworkInterface* iface) override {
    network_interface_ = iface;
    MediaChannelUtil::SetInterface(iface);
  }
  bool HasNetworkInterface() const override {
    return network_interface_ != nullptr;
  }

 protected:
  bool MuteStream(uint32_t ssrc, bool mute) {
    if (!HasSendStream(ssrc) && ssrc != 0) {
      return false;
    }
    if (mute) {
      muted_streams_.insert(ssrc);
    } else {
      muted_streams_.erase(ssrc);
    }
    return true;
  }
  bool set_sending(bool send) {
    sending_ = send;
    return true;
  }
  bool SetSendRtpHeaderExtensions(const std::vector<RtpExtension>& extensions) {
    send_extensions_ = extensions;
    return true;
  }
  void set_send_rtcp_parameters(const RtcpParameters& params) {
    send_rtcp_parameters_ = params;
  }
  void OnPacketSent(const rtc::SentPacket& sent_packet) override {}
  void OnReadyToSend(bool ready) override { ready_to_send_ = ready; }
  void OnNetworkRouteChanged(absl::string_view transport_name,
                             const rtc::NetworkRoute& network_route) override {
    last_network_route_ = network_route;
    ++num_network_route_changes_;
    transport_overhead_per_packet_ = network_route.packet_overhead;
  }
  bool fail_set_send_codecs() const { return fail_set_send_codecs_; }

 private:
  // TODO(bugs.webrtc.org/12783): This flag is used from more than one thread.
  // As a workaround for tsan, it's currently std::atomic but that might not
  // be the appropriate fix.
  std::atomic<bool> sending_;
  std::vector<RtpExtension> send_extensions_;
  std::list<std::string> rtp_packets_;
  std::list<std::string> rtcp_packets_;
  std::vector<StreamParams> send_streams_;
  RtcpParameters send_rtcp_parameters_;
  std::set<uint32_t> muted_streams_;
  std::map<uint32_t, webrtc::RtpParameters> rtp_send_parameters_;
  bool fail_set_send_codecs_;
  uint32_t send_ssrc_;
  std::string rtcp_cname_;
  bool ready_to_send_;
  int transport_overhead_per_packet_;
  rtc::NetworkRoute last_network_route_;
  int num_network_route_changes_;
  MediaChannelNetworkInterface* network_interface_ = nullptr;
  absl::AnyInvocable<void(const std::set<uint32_t>&)>
      ssrc_list_changed_callback_ = nullptr;
};

class FakeVoiceMediaReceiveChannel
    : public RtpReceiveChannelHelper<VoiceMediaReceiveChannelInterface> {
 public:
  struct DtmfInfo {
    DtmfInfo(uint32_t ssrc, int event_code, int duration);
    uint32_t ssrc;
    int event_code;
    int duration;
  };
  FakeVoiceMediaReceiveChannel(const AudioOptions& options,
                               webrtc::TaskQueueBase* network_thread);
  virtual ~FakeVoiceMediaReceiveChannel();

  // Test methods
  const std::vector<AudioCodec>& recv_codecs() const;
  const std::vector<DtmfInfo>& dtmf_info_queue() const;
  const AudioOptions& options() const;
  int max_bps() const;
  bool HasSource(uint32_t ssrc) const;

  // Overrides
  VideoMediaReceiveChannelInterface* AsVideoReceiveChannel() override {
    return nullptr;
  }
  VoiceMediaReceiveChannelInterface* AsVoiceReceiveChannel() override {
    return this;
  }
  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_AUDIO;
  }

  bool SetReceiverParameters(const AudioReceiverParameters& params) override;
  void SetPlayout(bool playout) override;

  bool AddRecvStream(const StreamParams& sp) override;
  bool RemoveRecvStream(uint32_t ssrc) override;

  bool SetOutputVolume(uint32_t ssrc, double volume) override;
  bool SetDefaultOutputVolume(double volume) override;

  bool GetOutputVolume(uint32_t ssrc, double* volume);

  bool SetBaseMinimumPlayoutDelayMs(uint32_t ssrc, int delay_ms) override;
  absl::optional<int> GetBaseMinimumPlayoutDelayMs(
      uint32_t ssrc) const override;

  bool GetStats(VoiceMediaReceiveInfo* info,
                bool get_and_clear_legacy_stats) override;

  void SetRawAudioSink(
      uint32_t ssrc,
      std::unique_ptr<webrtc::AudioSinkInterface> sink) override;
  void SetDefaultRawAudioSink(
      std::unique_ptr<webrtc::AudioSinkInterface> sink) override;

  std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const override;
  void SetReceiveNackEnabled(bool enabled) override {}
  void SetReceiveNonSenderRttEnabled(bool enabled) override {}

 private:
  class VoiceChannelAudioSink : public AudioSource::Sink {
   public:
    explicit VoiceChannelAudioSink(AudioSource* source);
    ~VoiceChannelAudioSink() override;
    void OnData(const void* audio_data,
                int bits_per_sample,
                int sample_rate,
                size_t number_of_channels,
                size_t number_of_frames,
                absl::optional<int64_t> absolute_capture_timestamp_ms) override;
    void OnClose() override;
    int NumPreferredChannels() const override { return -1; }
    AudioSource* source() const;

   private:
    AudioSource* source_;
  };

  bool SetRecvCodecs(const std::vector<AudioCodec>& codecs);
  bool SetMaxSendBandwidth(int bps);
  bool SetOptions(const AudioOptions& options);

  std::vector<AudioCodec> recv_codecs_;
  std::map<uint32_t, double> output_scalings_;
  std::map<uint32_t, int> output_delays_;
  std::vector<DtmfInfo> dtmf_info_queue_;
  AudioOptions options_;
  std::map<uint32_t, std::unique_ptr<VoiceChannelAudioSink>> local_sinks_;
  std::unique_ptr<webrtc::AudioSinkInterface> sink_;
  int max_bps_;
};

class FakeVoiceMediaSendChannel
    : public RtpSendChannelHelper<VoiceMediaSendChannelInterface> {
 public:
  struct DtmfInfo {
    DtmfInfo(uint32_t ssrc, int event_code, int duration);
    uint32_t ssrc;
    int event_code;
    int duration;
  };
  FakeVoiceMediaSendChannel(const AudioOptions& options,
                            webrtc::TaskQueueBase* network_thread);
  ~FakeVoiceMediaSendChannel() override;

  const std::vector<AudioCodec>& send_codecs() const;
  const std::vector<DtmfInfo>& dtmf_info_queue() const;
  const AudioOptions& options() const;
  int max_bps() const;
  bool HasSource(uint32_t ssrc) const;
  bool GetOutputVolume(uint32_t ssrc, double* volume);

  // Overrides
  VideoMediaSendChannelInterface* AsVideoSendChannel() override {
    return nullptr;
  }
  VoiceMediaSendChannelInterface* AsVoiceSendChannel() override { return this; }
  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_AUDIO;
  }

  bool SetSenderParameters(const AudioSenderParameter& params) override;
  void SetSend(bool send) override;
  bool SetAudioSend(uint32_t ssrc,
                    bool enable,
                    const AudioOptions* options,
                    AudioSource* source) override;

  bool CanInsertDtmf() override;
  bool InsertDtmf(uint32_t ssrc, int event_code, int duration) override;

  bool SenderNackEnabled() const override { return false; }
  bool SenderNonSenderRttEnabled() const override { return false; }
  void SetReceiveNackEnabled(bool enabled) {}
  void SetReceiveNonSenderRttEnabled(bool enabled) {}
  bool SendCodecHasNack() const override { return false; }
  void SetSendCodecChangedCallback(
      absl::AnyInvocable<void()> callback) override {}
  absl::optional<Codec> GetSendCodec() const override;

  bool GetStats(VoiceMediaSendInfo* stats) override;

 private:
  class VoiceChannelAudioSink : public AudioSource::Sink {
   public:
    explicit VoiceChannelAudioSink(AudioSource* source);
    ~VoiceChannelAudioSink() override;
    void OnData(const void* audio_data,
                int bits_per_sample,
                int sample_rate,
                size_t number_of_channels,
                size_t number_of_frames,
                absl::optional<int64_t> absolute_capture_timestamp_ms) override;
    void OnClose() override;
    int NumPreferredChannels() const override { return -1; }
    AudioSource* source() const;

   private:
    AudioSource* source_;
  };

  bool SetSendCodecs(const std::vector<AudioCodec>& codecs);
  bool SetMaxSendBandwidth(int bps);
  bool SetOptions(const AudioOptions& options);
  bool SetLocalSource(uint32_t ssrc, AudioSource* source);

  std::vector<AudioCodec> send_codecs_;
  std::map<uint32_t, double> output_scalings_;
  std::map<uint32_t, int> output_delays_;
  std::vector<DtmfInfo> dtmf_info_queue_;
  AudioOptions options_;
  std::map<uint32_t, std::unique_ptr<VoiceChannelAudioSink>> local_sinks_;
  int max_bps_;
};

// A helper function to compare the FakeVoiceMediaChannel::DtmfInfo.
bool CompareDtmfInfo(const FakeVoiceMediaSendChannel::DtmfInfo& info,
                     uint32_t ssrc,
                     int event_code,
                     int duration);

class FakeVideoMediaReceiveChannel
    : public RtpReceiveChannelHelper<VideoMediaReceiveChannelInterface> {
 public:
  FakeVideoMediaReceiveChannel(const VideoOptions& options,
                               webrtc::TaskQueueBase* network_thread);

  virtual ~FakeVideoMediaReceiveChannel();

  VideoMediaReceiveChannelInterface* AsVideoReceiveChannel() override {
    return this;
  }
  VoiceMediaReceiveChannelInterface* AsVoiceReceiveChannel() override {
    return nullptr;
  }
  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_VIDEO;
  }

  const std::vector<VideoCodec>& recv_codecs() const;
  const std::vector<VideoCodec>& send_codecs() const;
  bool rendering() const;
  const VideoOptions& options() const;
  const std::map<uint32_t, rtc::VideoSinkInterface<webrtc::VideoFrame>*>&
  sinks() const;
  int max_bps() const;
  bool SetReceiverParameters(const VideoReceiverParameters& params) override;

  bool SetSink(uint32_t ssrc,
               rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) override;
  void SetDefaultSink(
      rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) override;
  bool HasSink(uint32_t ssrc) const;

  void SetReceive(bool receive) override {}

  bool HasSource(uint32_t ssrc) const;
  bool AddRecvStream(const StreamParams& sp) override;
  bool RemoveRecvStream(uint32_t ssrc) override;

  std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const override;

  bool SetBaseMinimumPlayoutDelayMs(uint32_t ssrc, int delay_ms) override;
  absl::optional<int> GetBaseMinimumPlayoutDelayMs(
      uint32_t ssrc) const override;

  void SetRecordableEncodedFrameCallback(
      uint32_t ssrc,
      std::function<void(const webrtc::RecordableEncodedFrame&)> callback)
      override;
  void ClearRecordableEncodedFrameCallback(uint32_t ssrc) override;
  void RequestRecvKeyFrame(uint32_t ssrc) override;
  void SetReceiverFeedbackParameters(bool lntf_enabled,
                                     bool nack_enabled,
                                     webrtc::RtcpMode rtcp_mode,
                                     absl::optional<int> rtx_time) override {}
  bool GetStats(VideoMediaReceiveInfo* info) override;

  bool AddDefaultRecvStreamForTesting(const StreamParams& sp) override {
    RTC_CHECK_NOTREACHED();
    return false;
  }

 private:
  bool SetRecvCodecs(const std::vector<VideoCodec>& codecs);
  bool SetSendCodecs(const std::vector<VideoCodec>& codecs);
  bool SetOptions(const VideoOptions& options);
  bool SetMaxSendBandwidth(int bps);

  std::vector<VideoCodec> recv_codecs_;
  std::map<uint32_t, rtc::VideoSinkInterface<webrtc::VideoFrame>*> sinks_;
  std::map<uint32_t, rtc::VideoSourceInterface<webrtc::VideoFrame>*> sources_;
  std::map<uint32_t, int> output_delays_;
  VideoOptions options_;
  int max_bps_;
};

class FakeVideoMediaSendChannel
    : public RtpSendChannelHelper<VideoMediaSendChannelInterface> {
 public:
  FakeVideoMediaSendChannel(const VideoOptions& options,
                            webrtc::TaskQueueBase* network_thread);

  virtual ~FakeVideoMediaSendChannel();

  VideoMediaSendChannelInterface* AsVideoSendChannel() override { return this; }
  VoiceMediaSendChannelInterface* AsVoiceSendChannel() override {
    return nullptr;
  }
  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_VIDEO;
  }

  const std::vector<VideoCodec>& send_codecs() const;
  const std::vector<VideoCodec>& codecs() const;
  const VideoOptions& options() const;
  const std::map<uint32_t, rtc::VideoSinkInterface<webrtc::VideoFrame>*>&
  sinks() const;
  int max_bps() const;
  bool SetSenderParameters(const VideoSenderParameters& params) override;

  absl::optional<Codec> GetSendCodec() const override;

  bool SetSend(bool send) override;
  bool SetVideoSend(
      uint32_t ssrc,
      const VideoOptions* options,
      rtc::VideoSourceInterface<webrtc::VideoFrame>* source) override;

  bool HasSource(uint32_t ssrc) const;

  void FillBitrateInfo(BandwidthEstimationInfo* bwe_info) override;

  void GenerateSendKeyFrame(uint32_t ssrc,
                            const std::vector<std::string>& rids) override;
  webrtc::RtcpMode SendCodecRtcpMode() const override {
    return webrtc::RtcpMode::kCompound;
  }
  void SetSendCodecChangedCallback(
      absl::AnyInvocable<void()> callback) override {}
  void SetSsrcListChangedCallback(
      absl::AnyInvocable<void(const std::set<uint32_t>&)> callback) override {}

  bool SendCodecHasLntf() const override { return false; }
  bool SendCodecHasNack() const override { return false; }
  absl::optional<int> SendCodecRtxTime() const override {
    return absl::nullopt;
  }
  bool GetStats(VideoMediaSendInfo* info) override;

 private:
  bool SetSendCodecs(const std::vector<VideoCodec>& codecs);
  bool SetOptions(const VideoOptions& options);
  bool SetMaxSendBandwidth(int bps);

  std::vector<VideoCodec> send_codecs_;
  std::map<uint32_t, rtc::VideoSourceInterface<webrtc::VideoFrame>*> sources_;
  VideoOptions options_;
  int max_bps_;
};

class FakeVoiceEngine : public VoiceEngineInterface {
 public:
  FakeVoiceEngine();
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

  // TODO(ossu): For proper testing, These should either individually settable
  //             or the voice engine should reference mockable factories.
  const std::vector<AudioCodec>& send_codecs() const override;
  const std::vector<AudioCodec>& recv_codecs() const override;
  void SetCodecs(const std::vector<AudioCodec>& codecs);
  void SetRecvCodecs(const std::vector<AudioCodec>& codecs);
  void SetSendCodecs(const std::vector<AudioCodec>& codecs);
  int GetInputLevel();
  bool StartAecDump(webrtc::FileWrapper file, int64_t max_size_bytes) override;
  void StopAecDump() override;
  absl::optional<webrtc::AudioDeviceModule::Stats> GetAudioDeviceStats()
      override;
  std::vector<webrtc::RtpHeaderExtensionCapability> GetRtpHeaderExtensions()
      const override;
  void SetRtpHeaderExtensions(
      std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions);

 private:
  std::vector<AudioCodec> recv_codecs_;
  std::vector<AudioCodec> send_codecs_;
  bool fail_create_channel_;
  std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions_;

  friend class FakeMediaEngine;
};

class FakeVideoEngine : public VideoEngineInterface {
 public:
  FakeVideoEngine();
  bool SetOptions(const VideoOptions& options);
  std::unique_ptr<VideoMediaSendChannelInterface> CreateSendChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const VideoOptions& options,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory)
      override;
  std::unique_ptr<VideoMediaReceiveChannelInterface> CreateReceiveChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const VideoOptions& options,
      const webrtc::CryptoOptions& crypto_options) override;
  FakeVideoMediaSendChannel* GetSendChannel(size_t index);
  FakeVideoMediaReceiveChannel* GetReceiveChannel(size_t index);

  std::vector<VideoCodec> send_codecs() const override {
    return send_codecs(true);
  }
  std::vector<VideoCodec> recv_codecs() const override {
    return recv_codecs(true);
  }
  std::vector<VideoCodec> send_codecs(bool include_rtx) const override;
  std::vector<VideoCodec> recv_codecs(bool include_rtx) const override;
  void SetSendCodecs(const std::vector<VideoCodec>& codecs);
  void SetRecvCodecs(const std::vector<VideoCodec>& codecs);
  bool SetCapture(bool capture);
  std::vector<webrtc::RtpHeaderExtensionCapability> GetRtpHeaderExtensions()
      const override;
  void SetRtpHeaderExtensions(
      std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions);

 private:
  std::vector<VideoCodec> send_codecs_;
  std::vector<VideoCodec> recv_codecs_;
  bool capture_;
  VideoOptions options_;
  bool fail_create_channel_;
  std::vector<webrtc::RtpHeaderExtensionCapability> header_extensions_;

  friend class FakeMediaEngine;
};

class FakeMediaEngine : public CompositeMediaEngine {
 public:
  FakeMediaEngine();

  ~FakeMediaEngine() override;

  void SetAudioCodecs(const std::vector<AudioCodec>& codecs);
  void SetAudioRecvCodecs(const std::vector<AudioCodec>& codecs);
  void SetAudioSendCodecs(const std::vector<AudioCodec>& codecs);
  void SetVideoCodecs(const std::vector<VideoCodec>& codecs);

  void set_fail_create_channel(bool fail);

  FakeVoiceEngine* fake_voice_engine() { return voice_; }
  FakeVideoEngine* fake_video_engine() { return video_; }

 private:
  FakeVoiceEngine* const voice_;
  FakeVideoEngine* const video_;
};

}  // namespace cricket

#endif  // MEDIA_BASE_FAKE_MEDIA_ENGINE_H_
