/*
 *  Copyright (c) 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_ENGINE_WEBRTC_VIDEO_ENGINE_H_
#define MEDIA_ENGINE_WEBRTC_VIDEO_ENGINE_H_

#include <map>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/call/transport.h"
#include "api/transport/field_trial_based_config.h"
#include "api/video/video_bitrate_allocator_factory.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "api/video_codecs/sdp_video_format.h"
#include "call/call.h"
#include "call/flexfec_receive_stream.h"
#include "call/video_receive_stream.h"
#include "call/video_send_stream.h"
#include "media/base/media_engine.h"
#include "media/engine/constants.h"
#include "media/engine/unhandled_packets_buffer.h"
#include "rtc_base/network_route.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/thread_checker.h"

namespace webrtc {
class VideoDecoderFactory;
class VideoEncoderFactory;
struct MediaConfig;
}  // namespace webrtc

namespace rtc {
class Thread;
}  // namespace rtc

namespace cricket {

class WebRtcVideoChannel;

// Public for testing.
// Inputs StreamStats for all types of substreams (kMedia, kRtx, kFlexfec) and
// merges any non-kMedia substream stats object into its referenced kMedia-type
// substream. The resulting substreams are all kMedia. This means, for example,
// that packet and byte counters of RTX and FlexFEC streams are accounted for in
// the relevant RTP media stream's stats. This makes the resulting StreamStats
// objects ready to be turned into "outbound-rtp" stats objects for GetStats()
// which does not create separate stream stats objects for complementary
// streams.
std::map<uint32_t, webrtc::VideoSendStream::StreamStats>
MergeInfoAboutOutboundRtpSubstreamsForTesting(
    const std::map<uint32_t, webrtc::VideoSendStream::StreamStats>& substreams);

class UnsignalledSsrcHandler {
 public:
  enum Action {
    kDropPacket,
    kDeliverPacket,
  };
  virtual Action OnUnsignalledSsrc(WebRtcVideoChannel* channel,
                                   uint32_t ssrc) = 0;
  virtual ~UnsignalledSsrcHandler() = default;
};

// TODO(pbos): Remove, use external handlers only.
class DefaultUnsignalledSsrcHandler : public UnsignalledSsrcHandler {
 public:
  DefaultUnsignalledSsrcHandler();
  Action OnUnsignalledSsrc(WebRtcVideoChannel* channel, uint32_t ssrc) override;

  rtc::VideoSinkInterface<webrtc::VideoFrame>* GetDefaultSink() const;
  void SetDefaultSink(WebRtcVideoChannel* channel,
                      rtc::VideoSinkInterface<webrtc::VideoFrame>* sink);

  virtual ~DefaultUnsignalledSsrcHandler() = default;

 private:
  rtc::VideoSinkInterface<webrtc::VideoFrame>* default_sink_;
};

// WebRtcVideoEngine is used for the new native WebRTC Video API (webrtc:1667).
class WebRtcVideoEngine : public VideoEngineInterface {
 public:
  // These video codec factories represents all video codecs, i.e. both software
  // and external hardware codecs.
  WebRtcVideoEngine(
      std::unique_ptr<webrtc::VideoEncoderFactory> video_encoder_factory,
      std::unique_ptr<webrtc::VideoDecoderFactory> video_decoder_factory,
      const webrtc::WebRtcKeyValueConfig& trials);

  ~WebRtcVideoEngine() override;

  VideoMediaChannel* CreateMediaChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const VideoOptions& options,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory)
      override;

  std::vector<VideoCodec> send_codecs() const override;
  std::vector<VideoCodec> recv_codecs() const override;
  std::vector<webrtc::RtpHeaderExtensionCapability> GetRtpHeaderExtensions()
      const override;

 private:
  const std::unique_ptr<webrtc::VideoDecoderFactory> decoder_factory_;
  const std::unique_ptr<webrtc::VideoEncoderFactory> encoder_factory_;
  const std::unique_ptr<webrtc::VideoBitrateAllocatorFactory>
      bitrate_allocator_factory_;
  const webrtc::WebRtcKeyValueConfig& trials_;
};

class WebRtcVideoChannel : public VideoMediaChannel,
                           public webrtc::Transport,
                           public webrtc::EncoderSwitchRequestCallback {
 public:
  WebRtcVideoChannel(
      webrtc::Call* call,
      const MediaConfig& config,
      const VideoOptions& options,
      const webrtc::CryptoOptions& crypto_options,
      webrtc::VideoEncoderFactory* encoder_factory,
      webrtc::VideoDecoderFactory* decoder_factory,
      webrtc::VideoBitrateAllocatorFactory* bitrate_allocator_factory);
  ~WebRtcVideoChannel() override;

  // VideoMediaChannel implementation
  bool SetSendParameters(const VideoSendParameters& params) override;
  bool SetRecvParameters(const VideoRecvParameters& params) override;
  webrtc::RtpParameters GetRtpSendParameters(uint32_t ssrc) const override;
  webrtc::RTCError SetRtpSendParameters(
      uint32_t ssrc,
      const webrtc::RtpParameters& parameters) override;
  webrtc::RtpParameters GetRtpReceiveParameters(uint32_t ssrc) const override;
  webrtc::RtpParameters GetDefaultRtpReceiveParameters() const override;
  bool GetSendCodec(VideoCodec* send_codec) override;
  bool SetSend(bool send) override;
  bool SetVideoSend(
      uint32_t ssrc,
      const VideoOptions* options,
      rtc::VideoSourceInterface<webrtc::VideoFrame>* source) override;
  bool AddSendStream(const StreamParams& sp) override;
  bool RemoveSendStream(uint32_t ssrc) override;
  bool AddRecvStream(const StreamParams& sp) override;
  bool AddRecvStream(const StreamParams& sp, bool default_stream);
  bool RemoveRecvStream(uint32_t ssrc) override;
  void ResetUnsignaledRecvStream() override;
  bool SetSink(uint32_t ssrc,
               rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) override;
  void SetDefaultSink(
      rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) override;
  void FillBitrateInfo(BandwidthEstimationInfo* bwe_info) override;
  bool GetStats(VideoMediaInfo* info) override;

  void OnPacketReceived(rtc::CopyOnWriteBuffer packet,
                        int64_t packet_time_us) override;
  void OnReadyToSend(bool ready) override;
  void OnNetworkRouteChanged(const std::string& transport_name,
                             const rtc::NetworkRoute& network_route) override;
  void SetInterface(NetworkInterface* iface) override;

  // E2E Encrypted Video Frame API
  // Set a frame decryptor to a particular ssrc that will intercept all
  // incoming video frames and attempt to decrypt them before forwarding the
  // result.
  void SetFrameDecryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameDecryptorInterface>
                             frame_decryptor) override;
  // Set a frame encryptor to a particular ssrc that will intercept all
  // outgoing video frames and attempt to encrypt them and forward the result
  // to the packetizer.
  void SetFrameEncryptor(uint32_t ssrc,
                         rtc::scoped_refptr<webrtc::FrameEncryptorInterface>
                             frame_encryptor) override;

  void SetVideoCodecSwitchingEnabled(bool enabled) override;

  bool SetBaseMinimumPlayoutDelayMs(uint32_t ssrc, int delay_ms) override;

  absl::optional<int> GetBaseMinimumPlayoutDelayMs(
      uint32_t ssrc) const override;

  // Implemented for VideoMediaChannelTest.
  bool sending() const {
    RTC_DCHECK_RUN_ON(&thread_checker_);
    return sending_;
  }

  absl::optional<uint32_t> GetDefaultReceiveStreamSsrc();

  StreamParams unsignaled_stream_params() {
    RTC_DCHECK_RUN_ON(&thread_checker_);
    return unsignaled_stream_params_;
  }

  // AdaptReason is used for expressing why a WebRtcVideoSendStream request
  // a lower input frame size than the currently configured camera input frame
  // size. There can be more than one reason OR:ed together.
  enum AdaptReason {
    ADAPTREASON_NONE = 0,
    ADAPTREASON_CPU = 1,
    ADAPTREASON_BANDWIDTH = 2,
  };

  static constexpr int kDefaultQpMax = 56;

  std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const override;

  // Take the buffered packets for |ssrcs| and feed them into DeliverPacket.
  // This method does nothing unless unknown_ssrc_packet_buffer_ is configured.
  void BackfillBufferedPackets(rtc::ArrayView<const uint32_t> ssrcs);

  // Implements webrtc::EncoderSwitchRequestCallback.
  void RequestEncoderFallback() override;

  // TODO(bugs.webrtc.org/11341) : Remove this version of RequestEncoderSwitch.
  void RequestEncoderSwitch(
      const EncoderSwitchRequestCallback::Config& conf) override;
  void RequestEncoderSwitch(const webrtc::SdpVideoFormat& format) override;

  void SetRecordableEncodedFrameCallback(
      uint32_t ssrc,
      std::function<void(const webrtc::RecordableEncodedFrame&)> callback)
      override;
  void ClearRecordableEncodedFrameCallback(uint32_t ssrc) override;
  void GenerateKeyFrame(uint32_t ssrc) override;

  void SetEncoderToPacketizerFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;
  void SetDepacketizerToDecoderFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;

 private:
  class WebRtcVideoReceiveStream;

  // Finds VideoReceiveStream corresponding to ssrc. Aware of unsignalled ssrc
  // handling.
  WebRtcVideoReceiveStream* FindReceiveStream(uint32_t ssrc)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);

  struct VideoCodecSettings {
    VideoCodecSettings();

    // Checks if all members of |*this| are equal to the corresponding members
    // of |other|.
    bool operator==(const VideoCodecSettings& other) const;
    bool operator!=(const VideoCodecSettings& other) const;

    // Checks if all members of |a|, except |flexfec_payload_type|, are equal
    // to the corresponding members of |b|.
    static bool EqualsDisregardingFlexfec(const VideoCodecSettings& a,
                                          const VideoCodecSettings& b);

    VideoCodec codec;
    webrtc::UlpfecConfig ulpfec;
    int flexfec_payload_type;  // -1 if absent.
    int rtx_payload_type;      // -1 if absent.
  };

  struct ChangedSendParameters {
    // These optionals are unset if not changed.
    absl::optional<VideoCodecSettings> send_codec;
    absl::optional<std::vector<VideoCodecSettings>> negotiated_codecs;
    absl::optional<std::vector<webrtc::RtpExtension>> rtp_header_extensions;
    absl::optional<std::string> mid;
    absl::optional<bool> extmap_allow_mixed;
    absl::optional<int> max_bandwidth_bps;
    absl::optional<bool> conference_mode;
    absl::optional<webrtc::RtcpMode> rtcp_mode;
  };

  struct ChangedRecvParameters {
    // These optionals are unset if not changed.
    absl::optional<std::vector<VideoCodecSettings>> codec_settings;
    absl::optional<std::vector<webrtc::RtpExtension>> rtp_header_extensions;
    // Keep track of the FlexFEC payload type separately from |codec_settings|.
    // This allows us to recreate the FlexfecReceiveStream separately from the
    // VideoReceiveStream when the FlexFEC payload type is changed.
    absl::optional<int> flexfec_payload_type;
  };

  bool GetChangedSendParameters(const VideoSendParameters& params,
                                ChangedSendParameters* changed_params) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  bool ApplyChangedParams(const ChangedSendParameters& changed_params);
  bool GetChangedRecvParameters(const VideoRecvParameters& params,
                                ChangedRecvParameters* changed_params) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);

  void ConfigureReceiverRtp(
      webrtc::VideoReceiveStream::Config* config,
      webrtc::FlexfecReceiveStream::Config* flexfec_config,
      const StreamParams& sp) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  bool ValidateSendSsrcAvailability(const StreamParams& sp) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  bool ValidateReceiveSsrcAvailability(const StreamParams& sp) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  void DeleteReceiveStream(WebRtcVideoReceiveStream* stream)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);

  static std::string CodecSettingsVectorToString(
      const std::vector<VideoCodecSettings>& codecs);

  // Wrapper for the sender part.
  class WebRtcVideoSendStream {
   public:
    WebRtcVideoSendStream(
        webrtc::Call* call,
        const StreamParams& sp,
        webrtc::VideoSendStream::Config config,
        const VideoOptions& options,
        bool enable_cpu_overuse_detection,
        int max_bitrate_bps,
        const absl::optional<VideoCodecSettings>& codec_settings,
        const absl::optional<std::vector<webrtc::RtpExtension>>& rtp_extensions,
        const VideoSendParameters& send_params);
    ~WebRtcVideoSendStream();

    void SetSendParameters(const ChangedSendParameters& send_params);
    webrtc::RTCError SetRtpParameters(const webrtc::RtpParameters& parameters);
    webrtc::RtpParameters GetRtpParameters() const;

    void SetFrameEncryptor(
        rtc::scoped_refptr<webrtc::FrameEncryptorInterface> frame_encryptor);

    bool SetVideoSend(const VideoOptions* options,
                      rtc::VideoSourceInterface<webrtc::VideoFrame>* source);

    void SetSend(bool send);

    const std::vector<uint32_t>& GetSsrcs() const;
    // Returns per ssrc VideoSenderInfos. Useful for simulcast scenario.
    std::vector<VideoSenderInfo> GetPerLayerVideoSenderInfos(bool log_stats);
    // Aggregates per ssrc VideoSenderInfos to single VideoSenderInfo for
    // legacy reasons. Used in old GetStats API and track stats.
    VideoSenderInfo GetAggregatedVideoSenderInfo(
        const std::vector<VideoSenderInfo>& infos) const;
    void FillBitrateInfo(BandwidthEstimationInfo* bwe_info);

    void SetEncoderToPacketizerFrameTransformer(
        rtc::scoped_refptr<webrtc::FrameTransformerInterface>
            frame_transformer);

   private:
    // Parameters needed to reconstruct the underlying stream.
    // webrtc::VideoSendStream doesn't support setting a lot of options on the
    // fly, so when those need to be changed we tear down and reconstruct with
    // similar parameters depending on which options changed etc.
    struct VideoSendStreamParameters {
      VideoSendStreamParameters(
          webrtc::VideoSendStream::Config config,
          const VideoOptions& options,
          int max_bitrate_bps,
          const absl::optional<VideoCodecSettings>& codec_settings);
      webrtc::VideoSendStream::Config config;
      VideoOptions options;
      int max_bitrate_bps;
      bool conference_mode;
      absl::optional<VideoCodecSettings> codec_settings;
      // Sent resolutions + bitrates etc. by the underlying VideoSendStream,
      // typically changes when setting a new resolution or reconfiguring
      // bitrates.
      webrtc::VideoEncoderConfig encoder_config;
    };

    rtc::scoped_refptr<webrtc::VideoEncoderConfig::EncoderSpecificSettings>
    ConfigureVideoEncoderSettings(const VideoCodec& codec);
    void SetCodec(const VideoCodecSettings& codec);
    void RecreateWebRtcStream();
    webrtc::VideoEncoderConfig CreateVideoEncoderConfig(
        const VideoCodec& codec) const;
    void ReconfigureEncoder();

    // Calls Start or Stop according to whether or not |sending_| is true,
    // and whether or not the encoding in |rtp_parameters_| is active.
    void UpdateSendState();

    webrtc::DegradationPreference GetDegradationPreference() const
        RTC_EXCLUSIVE_LOCKS_REQUIRED(&thread_checker_);

    rtc::ThreadChecker thread_checker_;
    rtc::Thread* worker_thread_;
    const std::vector<uint32_t> ssrcs_ RTC_GUARDED_BY(&thread_checker_);
    const std::vector<SsrcGroup> ssrc_groups_ RTC_GUARDED_BY(&thread_checker_);
    webrtc::Call* const call_;
    const bool enable_cpu_overuse_detection_;
    rtc::VideoSourceInterface<webrtc::VideoFrame>* source_
        RTC_GUARDED_BY(&thread_checker_);

    webrtc::VideoSendStream* stream_ RTC_GUARDED_BY(&thread_checker_);

    // Contains settings that are the same for all streams in the MediaChannel,
    // such as codecs, header extensions, and the global bitrate limit for the
    // entire channel.
    VideoSendStreamParameters parameters_ RTC_GUARDED_BY(&thread_checker_);
    // Contains settings that are unique for each stream, such as max_bitrate.
    // Does *not* contain codecs, however.
    // TODO(skvlad): Move ssrcs_ and ssrc_groups_ into rtp_parameters_.
    // TODO(skvlad): Combine parameters_ and rtp_parameters_ once we have only
    // one stream per MediaChannel.
    webrtc::RtpParameters rtp_parameters_ RTC_GUARDED_BY(&thread_checker_);

    bool sending_ RTC_GUARDED_BY(&thread_checker_);

    // TODO(asapersson): investigate why setting
    // DegrationPreferences::MAINTAIN_RESOLUTION isn't sufficient to disable
    // downscaling everywhere in the pipeline.
    const bool disable_automatic_resize_;
  };

  // Wrapper for the receiver part, contains configs etc. that are needed to
  // reconstruct the underlying VideoReceiveStream.
  class WebRtcVideoReceiveStream
      : public rtc::VideoSinkInterface<webrtc::VideoFrame> {
   public:
    WebRtcVideoReceiveStream(
        WebRtcVideoChannel* channel,
        webrtc::Call* call,
        const StreamParams& sp,
        webrtc::VideoReceiveStream::Config config,
        webrtc::VideoDecoderFactory* decoder_factory,
        bool default_stream,
        const std::vector<VideoCodecSettings>& recv_codecs,
        const webrtc::FlexfecReceiveStream::Config& flexfec_config);
    ~WebRtcVideoReceiveStream();

    const std::vector<uint32_t>& GetSsrcs() const;

    std::vector<webrtc::RtpSource> GetSources();

    // Does not return codecs, they are filled by the owning WebRtcVideoChannel.
    webrtc::RtpParameters GetRtpParameters() const;

    void SetLocalSsrc(uint32_t local_ssrc);
    // TODO(deadbeef): Move these feedback parameters into the recv parameters.
    void SetFeedbackParameters(bool lntf_enabled,
                               bool nack_enabled,
                               bool transport_cc_enabled,
                               webrtc::RtcpMode rtcp_mode);
    void SetRecvParameters(const ChangedRecvParameters& recv_params);

    void OnFrame(const webrtc::VideoFrame& frame) override;
    bool IsDefaultStream() const;

    void SetFrameDecryptor(
        rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor);

    bool SetBaseMinimumPlayoutDelayMs(int delay_ms);

    int GetBaseMinimumPlayoutDelayMs() const;

    void SetSink(rtc::VideoSinkInterface<webrtc::VideoFrame>* sink);

    VideoReceiverInfo GetVideoReceiverInfo(bool log_stats);

    void SetRecordableEncodedFrameCallback(
        std::function<void(const webrtc::RecordableEncodedFrame&)> callback);
    void ClearRecordableEncodedFrameCallback();
    void GenerateKeyFrame();

    void SetDepacketizerToDecoderFrameTransformer(
        rtc::scoped_refptr<webrtc::FrameTransformerInterface>
            frame_transformer);

   private:
    void RecreateWebRtcVideoStream();
    void MaybeRecreateWebRtcFlexfecStream();

    void MaybeAssociateFlexfecWithVideo();
    void MaybeDissociateFlexfecFromVideo();

    void ConfigureCodecs(const std::vector<VideoCodecSettings>& recv_codecs);
    void ConfigureFlexfecCodec(int flexfec_payload_type);

    std::string GetCodecNameFromPayloadType(int payload_type);

    WebRtcVideoChannel* const channel_;
    webrtc::Call* const call_;
    const StreamParams stream_params_;

    // Both |stream_| and |flexfec_stream_| are managed by |this|. They are
    // destroyed by calling call_->DestroyVideoReceiveStream and
    // call_->DestroyFlexfecReceiveStream, respectively.
    webrtc::VideoReceiveStream* stream_;
    const bool default_stream_;
    webrtc::VideoReceiveStream::Config config_;
    webrtc::FlexfecReceiveStream::Config flexfec_config_;
    webrtc::FlexfecReceiveStream* flexfec_stream_;

    webrtc::VideoDecoderFactory* const decoder_factory_;

    webrtc::Mutex sink_lock_;
    rtc::VideoSinkInterface<webrtc::VideoFrame>* sink_
        RTC_GUARDED_BY(sink_lock_);
    // Expands remote RTP timestamps to int64_t to be able to estimate how long
    // the stream has been running.
    rtc::TimestampWrapAroundHandler timestamp_wraparound_handler_
        RTC_GUARDED_BY(sink_lock_);
    int64_t first_frame_timestamp_ RTC_GUARDED_BY(sink_lock_);
    // Start NTP time is estimated as current remote NTP time (estimated from
    // RTCP) minus the elapsed time, as soon as remote NTP time is available.
    int64_t estimated_remote_start_ntp_time_ms_ RTC_GUARDED_BY(sink_lock_);
  };

  void Construct(webrtc::Call* call, WebRtcVideoEngine* engine);

  bool SendRtp(const uint8_t* data,
               size_t len,
               const webrtc::PacketOptions& options) override;
  bool SendRtcp(const uint8_t* data, size_t len) override;

  // Generate the list of codec parameters to pass down based on the negotiated
  // "codecs". Note that VideoCodecSettings correspond to concrete codecs like
  // VP8, VP9, H264 while VideoCodecs correspond also to "virtual" codecs like
  // RTX, ULPFEC, FLEXFEC.
  static std::vector<VideoCodecSettings> MapCodecs(
      const std::vector<VideoCodec>& codecs);
  // Get all codecs that are compatible with the receiver.
  std::vector<VideoCodecSettings> SelectSendVideoCodecs(
      const std::vector<VideoCodecSettings>& remote_mapped_codecs) const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);

  static bool NonFlexfecReceiveCodecsHaveChanged(
      std::vector<VideoCodecSettings> before,
      std::vector<VideoCodecSettings> after);

  void FillSenderStats(VideoMediaInfo* info, bool log_stats)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  void FillReceiverStats(VideoMediaInfo* info, bool log_stats)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  void FillBandwidthEstimationStats(const webrtc::Call::Stats& stats,
                                    VideoMediaInfo* info)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);
  void FillSendAndReceiveCodecStats(VideoMediaInfo* video_media_info)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(thread_checker_);

  rtc::Thread* const worker_thread_;
  rtc::ThreadChecker thread_checker_;

  uint32_t rtcp_receiver_report_ssrc_ RTC_GUARDED_BY(thread_checker_);
  bool sending_ RTC_GUARDED_BY(thread_checker_);
  webrtc::Call* const call_ RTC_GUARDED_BY(thread_checker_);

  DefaultUnsignalledSsrcHandler default_unsignalled_ssrc_handler_
      RTC_GUARDED_BY(thread_checker_);
  UnsignalledSsrcHandler* const unsignalled_ssrc_handler_
      RTC_GUARDED_BY(thread_checker_);

  // Delay for unsignaled streams, which may be set before the stream exists.
  int default_recv_base_minimum_delay_ms_ RTC_GUARDED_BY(thread_checker_) = 0;

  const MediaConfig::Video video_config_ RTC_GUARDED_BY(thread_checker_);

  // Using primary-ssrc (first ssrc) as key.
  std::map<uint32_t, WebRtcVideoSendStream*> send_streams_
      RTC_GUARDED_BY(thread_checker_);
  std::map<uint32_t, WebRtcVideoReceiveStream*> receive_streams_
      RTC_GUARDED_BY(thread_checker_);
  std::set<uint32_t> send_ssrcs_ RTC_GUARDED_BY(thread_checker_);
  std::set<uint32_t> receive_ssrcs_ RTC_GUARDED_BY(thread_checker_);

  absl::optional<VideoCodecSettings> send_codec_
      RTC_GUARDED_BY(thread_checker_);
  std::vector<VideoCodecSettings> negotiated_codecs_
      RTC_GUARDED_BY(thread_checker_);

  absl::optional<std::vector<webrtc::RtpExtension>> send_rtp_extensions_
      RTC_GUARDED_BY(thread_checker_);

  webrtc::VideoEncoderFactory* const encoder_factory_
      RTC_GUARDED_BY(thread_checker_);
  webrtc::VideoDecoderFactory* const decoder_factory_
      RTC_GUARDED_BY(thread_checker_);
  webrtc::VideoBitrateAllocatorFactory* const bitrate_allocator_factory_
      RTC_GUARDED_BY(thread_checker_);
  std::vector<VideoCodecSettings> recv_codecs_ RTC_GUARDED_BY(thread_checker_);
  std::vector<webrtc::RtpExtension> recv_rtp_extensions_
      RTC_GUARDED_BY(thread_checker_);
  // See reason for keeping track of the FlexFEC payload type separately in
  // comment in WebRtcVideoChannel::ChangedRecvParameters.
  int recv_flexfec_payload_type_ RTC_GUARDED_BY(thread_checker_);
  webrtc::BitrateConstraints bitrate_config_ RTC_GUARDED_BY(thread_checker_);
  // TODO(deadbeef): Don't duplicate information between
  // send_params/recv_params, rtp_extensions, options, etc.
  VideoSendParameters send_params_ RTC_GUARDED_BY(thread_checker_);
  VideoOptions default_send_options_ RTC_GUARDED_BY(thread_checker_);
  VideoRecvParameters recv_params_ RTC_GUARDED_BY(thread_checker_);
  int64_t last_stats_log_ms_ RTC_GUARDED_BY(thread_checker_);
  const bool discard_unknown_ssrc_packets_ RTC_GUARDED_BY(thread_checker_);
  // This is a stream param that comes from the remote description, but wasn't
  // signaled with any a=ssrc lines. It holds information that was signaled
  // before the unsignaled receive stream is created when the first packet is
  // received.
  StreamParams unsignaled_stream_params_ RTC_GUARDED_BY(thread_checker_);
  // Per peer connection crypto options that last for the lifetime of the peer
  // connection.
  const webrtc::CryptoOptions crypto_options_ RTC_GUARDED_BY(thread_checker_);

  // Optional frame transformer set on unsignaled streams.
  rtc::scoped_refptr<webrtc::FrameTransformerInterface>
      unsignaled_frame_transformer_ RTC_GUARDED_BY(thread_checker_);

  // Buffer for unhandled packets.
  std::unique_ptr<UnhandledPacketsBuffer> unknown_ssrc_packet_buffer_
      RTC_GUARDED_BY(thread_checker_);

  bool allow_codec_switching_ = false;
  absl::optional<EncoderSwitchRequestCallback::Config>
      requested_encoder_switch_;
};

class EncoderStreamFactory
    : public webrtc::VideoEncoderConfig::VideoStreamFactoryInterface {
 public:
  EncoderStreamFactory(std::string codec_name,
                       int max_qp,
                       bool is_screenshare,
                       bool conference_mode)
      : EncoderStreamFactory(codec_name,
                             max_qp,
                             is_screenshare,
                             conference_mode,
                             nullptr) {}

  EncoderStreamFactory(std::string codec_name,
                       int max_qp,
                       bool is_screenshare,
                       bool conference_mode,
                       const webrtc::WebRtcKeyValueConfig* trials);

 private:
  std::vector<webrtc::VideoStream> CreateEncoderStreams(
      int width,
      int height,
      const webrtc::VideoEncoderConfig& encoder_config) override;

  std::vector<webrtc::VideoStream> CreateDefaultVideoStreams(
      int width,
      int height,
      const webrtc::VideoEncoderConfig& encoder_config,
      const absl::optional<webrtc::DataRate>& experimental_min_bitrate) const;

  std::vector<webrtc::VideoStream>
  CreateSimulcastOrConferenceModeScreenshareStreams(
      int width,
      int height,
      const webrtc::VideoEncoderConfig& encoder_config,
      const absl::optional<webrtc::DataRate>& experimental_min_bitrate) const;

  const std::string codec_name_;
  const int max_qp_;
  const bool is_screenshare_;
  // Allows a screenshare specific configuration, which enables temporal
  // layering and various settings.
  const bool conference_mode_;
  const webrtc::FieldTrialBasedConfig fallback_trials_;
  const webrtc::WebRtcKeyValueConfig& trials_;
};

}  // namespace cricket

#endif  // MEDIA_ENGINE_WEBRTC_VIDEO_ENGINE_H_
