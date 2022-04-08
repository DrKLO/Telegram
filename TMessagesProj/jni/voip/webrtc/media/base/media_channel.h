/*
 *  Copyright (c) 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MEDIA_BASE_MEDIA_CHANNEL_H_
#define MEDIA_BASE_MEDIA_CHANNEL_H_

#include <map>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio_codecs/audio_encoder.h"
#include "api/audio_options.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/crypto/frame_encryptor_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/media_stream_interface.h"
#include "api/rtc_error.h"
#include "api/rtp_parameters.h"
#include "api/transport/data_channel_transport_interface.h"
#include "api/transport/rtp/rtp_source.h"
#include "api/units/time_delta.h"
#include "api/video/video_content_type.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/video_encoder_config.h"
#include "call/video_receive_stream.h"
#include "common_video/include/quality_limitation_reason.h"
#include "media/base/codec.h"
#include "media/base/delayable.h"
#include "media/base/media_config.h"
#include "media/base/media_constants.h"
#include "media/base/stream_params.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/buffer.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/dscp.h"
#include "rtc_base/logging.h"
#include "rtc_base/network_route.h"
#include "rtc_base/socket.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"

namespace rtc {
class Timing;
}

namespace webrtc {
class AudioSinkInterface;
class VideoFrame;
}  // namespace webrtc

namespace cricket {

class AudioSource;
class VideoCapturer;
struct RtpHeader;
struct VideoFormat;

const int kScreencastDefaultFps = 5;

template <class T>
static std::string ToStringIfSet(const char* key,
                                 const absl::optional<T>& val) {
  std::string str;
  if (val) {
    str = key;
    str += ": ";
    str += val ? rtc::ToString(*val) : "";
    str += ", ";
  }
  return str;
}

template <class T>
static std::string VectorToString(const std::vector<T>& vals) {
  rtc::StringBuilder ost;  // no-presubmit-check TODO(webrtc:8982)
  ost << "[";
  for (size_t i = 0; i < vals.size(); ++i) {
    if (i > 0) {
      ost << ", ";
    }
    ost << vals[i].ToString();
  }
  ost << "]";
  return ost.Release();
}

// Options that can be applied to a VideoMediaChannel or a VideoMediaEngine.
// Used to be flags, but that makes it hard to selectively apply options.
// We are moving all of the setting of options to structs like this,
// but some things currently still use flags.
struct VideoOptions {
  VideoOptions();
  ~VideoOptions();

  void SetAll(const VideoOptions& change) {
    SetFrom(&video_noise_reduction, change.video_noise_reduction);
    SetFrom(&screencast_min_bitrate_kbps, change.screencast_min_bitrate_kbps);
    SetFrom(&is_screencast, change.is_screencast);
  }

  bool operator==(const VideoOptions& o) const {
    return video_noise_reduction == o.video_noise_reduction &&
           screencast_min_bitrate_kbps == o.screencast_min_bitrate_kbps &&
           is_screencast == o.is_screencast;
  }
  bool operator!=(const VideoOptions& o) const { return !(*this == o); }

  std::string ToString() const {
    rtc::StringBuilder ost;
    ost << "VideoOptions {";
    ost << ToStringIfSet("noise reduction", video_noise_reduction);
    ost << ToStringIfSet("screencast min bitrate kbps",
                         screencast_min_bitrate_kbps);
    ost << ToStringIfSet("is_screencast ", is_screencast);
    ost << "}";
    return ost.Release();
  }

  // Enable denoising? This flag comes from the getUserMedia
  // constraint 'googNoiseReduction', and WebRtcVideoEngine passes it
  // on to the codec options. Disabled by default.
  absl::optional<bool> video_noise_reduction;
  // Force screencast to use a minimum bitrate. This flag comes from
  // the PeerConnection constraint 'googScreencastMinBitrate'. It is
  // copied to the encoder config by WebRtcVideoChannel.
  absl::optional<int> screencast_min_bitrate_kbps;
  // Set by screencast sources. Implies selection of encoding settings
  // suitable for screencast. Most likely not the right way to do
  // things, e.g., screencast of a text document and screencast of a
  // youtube video have different needs.
  absl::optional<bool> is_screencast;
  webrtc::VideoTrackInterface::ContentHint content_hint;

 private:
  template <typename T>
  static void SetFrom(absl::optional<T>* s, const absl::optional<T>& o) {
    if (o) {
      *s = o;
    }
  }
};

class MediaChannel {
 public:
  class NetworkInterface {
   public:
    enum SocketType { ST_RTP, ST_RTCP };
    virtual bool SendPacket(rtc::CopyOnWriteBuffer* packet,
                            const rtc::PacketOptions& options) = 0;
    virtual bool SendRtcp(rtc::CopyOnWriteBuffer* packet,
                          const rtc::PacketOptions& options) = 0;
    virtual int SetOption(SocketType type,
                          rtc::Socket::Option opt,
                          int option) = 0;
    virtual ~NetworkInterface() {}
  };

  MediaChannel(const MediaConfig& config,
               webrtc::TaskQueueBase* network_thread);
  explicit MediaChannel(webrtc::TaskQueueBase* network_thread);
  virtual ~MediaChannel();

  virtual cricket::MediaType media_type() const = 0;

  // Sets the abstract interface class for sending RTP/RTCP data.
  virtual void SetInterface(NetworkInterface* iface);
  // Called on the network when an RTP packet is received.
  virtual void OnPacketReceived(rtc::CopyOnWriteBuffer packet,
                                int64_t packet_time_us) = 0;
  // Called on the network thread after a transport has finished sending a
  // packet.
  virtual void OnPacketSent(const rtc::SentPacket& sent_packet) = 0;
  // Called when the socket's ability to send has changed.
  virtual void OnReadyToSend(bool ready) = 0;
  // Called when the network route used for sending packets changed.
  virtual void OnNetworkRouteChanged(
      const std::string& transport_name,
      const rtc::NetworkRoute& network_route) = 0;
  // Creates a new outgoing media stream with SSRCs and CNAME as described
  // by sp.
  virtual bool AddSendStream(const StreamParams& sp) = 0;
  // Removes an outgoing media stream.
  // SSRC must be the first SSRC of the media stream if the stream uses
  // multiple SSRCs. In the case of an ssrc of 0, the possibly cached
  // StreamParams is removed.
  virtual bool RemoveSendStream(uint32_t ssrc) = 0;
  // Creates a new incoming media stream with SSRCs, CNAME as described
  // by sp. In the case of a sp without SSRCs, the unsignaled sp is cached
  // to be used later for unsignaled streams received.
  virtual bool AddRecvStream(const StreamParams& sp) = 0;
  // Removes an incoming media stream.
  // ssrc must be the first SSRC of the media stream if the stream uses
  // multiple SSRCs.
  virtual bool RemoveRecvStream(uint32_t ssrc) = 0;
  // Resets any cached StreamParams for an unsignaled RecvStream, and removes
  // any existing unsignaled streams.
  virtual void ResetUnsignaledRecvStream() = 0;
  // Informs the media channel when the transport's demuxer criteria is updated.
  // * OnDemuxerCriteriaUpdatePending() happens on the same thread that the
  //   channel's streams are added and removed (worker thread).
  // * OnDemuxerCriteriaUpdateComplete() happens on the thread where the demuxer
  //   lives (network thread).
  // Because the demuxer is updated asynchronously, there is a window of time
  // where packets are arriving to the channel for streams that have already
  // been removed on the worker thread. It is important NOT to treat these as
  // new unsignalled ssrcs.
  virtual void OnDemuxerCriteriaUpdatePending() = 0;
  virtual void OnDemuxerCriteriaUpdateComplete() = 0;
  // Returns the absoulte sendtime extension id value from media channel.
  virtual int GetRtpSendTimeExtnId() const;
  // Set the frame encryptor to use on all outgoing frames. This is optional.
  // This pointers lifetime is managed by the set of RtpSender it is attached
  // to.
  // TODO(benwright) make pure virtual once internal supports it.
  virtual void SetFrameEncryptor(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameEncryptorInterface> frame_encryptor);
  // Set the frame decryptor to use on all incoming frames. This is optional.
  // This pointers lifetimes is managed by the set of RtpReceivers it is
  // attached to.
  // TODO(benwright) make pure virtual once internal supports it.
  virtual void SetFrameDecryptor(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor);

  // Enable network condition based codec switching.
  virtual void SetVideoCodecSwitchingEnabled(bool enabled);

  // Base method to send packet using NetworkInterface.
  bool SendPacket(rtc::CopyOnWriteBuffer* packet,
                  const rtc::PacketOptions& options);

  bool SendRtcp(rtc::CopyOnWriteBuffer* packet,
                const rtc::PacketOptions& options);

  int SetOption(NetworkInterface::SocketType type,
                rtc::Socket::Option opt,
                int option);

  // Corresponds to the SDP attribute extmap-allow-mixed, see RFC8285.
  // Set to true if it's allowed to mix one- and two-byte RTP header extensions
  // in the same stream. The setter and getter must only be called from
  // worker_thread.
  void SetExtmapAllowMixed(bool extmap_allow_mixed);
  bool ExtmapAllowMixed() const;

  virtual webrtc::RtpParameters GetRtpSendParameters(uint32_t ssrc) const = 0;
  virtual webrtc::RTCError SetRtpSendParameters(
      uint32_t ssrc,
      const webrtc::RtpParameters& parameters) = 0;

  virtual void SetEncoderToPacketizerFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer);
  virtual void SetDepacketizerToDecoderFrameTransformer(
      uint32_t ssrc,
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer);

 protected:
  int SetOptionLocked(NetworkInterface::SocketType type,
                      rtc::Socket::Option opt,
                      int option) RTC_RUN_ON(network_thread_);

  bool DscpEnabled() const;

  // This is the DSCP value used for both RTP and RTCP channels if DSCP is
  // enabled. It can be changed at any time via `SetPreferredDscp`.
  rtc::DiffServCodePoint PreferredDscp() const;
  void SetPreferredDscp(rtc::DiffServCodePoint new_dscp);

  rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> network_safety();

  // Utility implementation for derived classes (video/voice) that applies
  // the packet options and passes the data onwards to `SendPacket`.
  void SendRtp(const uint8_t* data,
               size_t len,
               const webrtc::PacketOptions& options);

  void SendRtcp(const uint8_t* data, size_t len);

 private:
  // Apply the preferred DSCP setting to the underlying network interface RTP
  // and RTCP channels. If DSCP is disabled, then apply the default DSCP value.
  void UpdateDscp() RTC_RUN_ON(network_thread_);

  bool DoSendPacket(rtc::CopyOnWriteBuffer* packet,
                    bool rtcp,
                    const rtc::PacketOptions& options);

  const bool enable_dscp_;
  const rtc::scoped_refptr<webrtc::PendingTaskSafetyFlag> network_safety_
      RTC_PT_GUARDED_BY(network_thread_);
  webrtc::TaskQueueBase* const network_thread_;
  NetworkInterface* network_interface_ RTC_GUARDED_BY(network_thread_) =
      nullptr;
  rtc::DiffServCodePoint preferred_dscp_ RTC_GUARDED_BY(network_thread_) =
      rtc::DSCP_DEFAULT;
  bool extmap_allow_mixed_ = false;
};

// The stats information is structured as follows:
// Media are represented by either MediaSenderInfo or MediaReceiverInfo.
// Media contains a vector of SSRC infos that are exclusively used by this
// media. (SSRCs shared between media streams can't be represented.)

// Information about an SSRC.
// This data may be locally recorded, or received in an RTCP SR or RR.
struct SsrcSenderInfo {
  uint32_t ssrc = 0;
  double timestamp = 0.0;  // NTP timestamp, represented as seconds since epoch.
};

struct SsrcReceiverInfo {
  uint32_t ssrc = 0;
  double timestamp = 0.0;
};

struct MediaSenderInfo {
  MediaSenderInfo();
  ~MediaSenderInfo();
  void add_ssrc(const SsrcSenderInfo& stat) { local_stats.push_back(stat); }
  // Temporary utility function for call sites that only provide SSRC.
  // As more info is added into SsrcSenderInfo, this function should go away.
  void add_ssrc(uint32_t ssrc) {
    SsrcSenderInfo stat;
    stat.ssrc = ssrc;
    add_ssrc(stat);
  }
  // Utility accessor for clients that are only interested in ssrc numbers.
  std::vector<uint32_t> ssrcs() const {
    std::vector<uint32_t> retval;
    for (std::vector<SsrcSenderInfo>::const_iterator it = local_stats.begin();
         it != local_stats.end(); ++it) {
      retval.push_back(it->ssrc);
    }
    return retval;
  }
  // Returns true if the media has been connected.
  bool connected() const { return local_stats.size() > 0; }
  // Utility accessor for clients that make the assumption only one ssrc
  // exists per media.
  // This will eventually go away.
  // Call sites that compare this to zero should use connected() instead.
  // https://bugs.webrtc.org/8694
  uint32_t ssrc() const {
    if (connected()) {
      return local_stats[0].ssrc;
    } else {
      return 0;
    }
  }
  // https://w3c.github.io/webrtc-stats/#dom-rtcsentrtpstreamstats-bytessent
  int64_t payload_bytes_sent = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-headerbytessent
  int64_t header_and_padding_bytes_sent = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-retransmittedbytessent
  uint64_t retransmitted_bytes_sent = 0;
  int packets_sent = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-retransmittedpacketssent
  uint64_t retransmitted_packets_sent = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-nackcount
  uint32_t nacks_rcvd = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-targetbitrate
  double target_bitrate = 0.0;
  int packets_lost = 0;
  float fraction_lost = 0.0f;
  int64_t rtt_ms = 0;
  std::string codec_name;
  absl::optional<int> codec_payload_type;
  std::vector<SsrcSenderInfo> local_stats;
  std::vector<SsrcReceiverInfo> remote_stats;
  // A snapshot of the most recent Report Block with additional data of interest
  // to statistics. Used to implement RTCRemoteInboundRtpStreamStats. Within
  // this list, the ReportBlockData::RTCPReportBlock::source_ssrc(), which is
  // the SSRC of the corresponding outbound RTP stream, is unique.
  std::vector<webrtc::ReportBlockData> report_block_datas;
};

struct MediaReceiverInfo {
  MediaReceiverInfo();
  ~MediaReceiverInfo();
  void add_ssrc(const SsrcReceiverInfo& stat) { local_stats.push_back(stat); }
  // Temporary utility function for call sites that only provide SSRC.
  // As more info is added into SsrcSenderInfo, this function should go away.
  void add_ssrc(uint32_t ssrc) {
    SsrcReceiverInfo stat;
    stat.ssrc = ssrc;
    add_ssrc(stat);
  }
  std::vector<uint32_t> ssrcs() const {
    std::vector<uint32_t> retval;
    for (std::vector<SsrcReceiverInfo>::const_iterator it = local_stats.begin();
         it != local_stats.end(); ++it) {
      retval.push_back(it->ssrc);
    }
    return retval;
  }
  // Returns true if the media has been connected.
  bool connected() const { return local_stats.size() > 0; }
  // Utility accessor for clients that make the assumption only one ssrc
  // exists per media.
  // This will eventually go away.
  // Call sites that compare this to zero should use connected();
  // https://bugs.webrtc.org/8694
  uint32_t ssrc() const {
    if (connected()) {
      return local_stats[0].ssrc;
    } else {
      return 0;
    }
  }

  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-bytesreceived
  int64_t payload_bytes_rcvd = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-headerbytesreceived
  int64_t header_and_padding_bytes_rcvd = 0;
  int packets_rcvd = 0;
  int packets_lost = 0;
  absl::optional<uint32_t> nacks_sent;
  // Jitter (network-related) latency (cumulative).
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-jitterbufferdelay
  double jitter_buffer_delay_seconds = 0.0;
  // Number of observations for cumulative jitter latency.
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-jitterbufferemittedcount
  uint64_t jitter_buffer_emitted_count = 0;
  // The timestamp at which the last packet was received, i.e. the time of the
  // local clock when it was received - not the RTP timestamp of that packet.
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-lastpacketreceivedtimestamp
  absl::optional<int64_t> last_packet_received_timestamp_ms;
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-estimatedplayouttimestamp
  absl::optional<int64_t> estimated_playout_ntp_timestamp_ms;
  std::string codec_name;
  absl::optional<int> codec_payload_type;
  std::vector<SsrcReceiverInfo> local_stats;
  std::vector<SsrcSenderInfo> remote_stats;
};

struct VoiceSenderInfo : public MediaSenderInfo {
  VoiceSenderInfo();
  ~VoiceSenderInfo();
  int jitter_ms = 0;
  // Current audio level, expressed linearly [0,32767].
  int audio_level = 0;
  // See description of "totalAudioEnergy" in the WebRTC stats spec:
  // https://w3c.github.io/webrtc-stats/#dom-rtcmediastreamtrackstats-totalaudioenergy
  double total_input_energy = 0.0;
  double total_input_duration = 0.0;
  bool typing_noise_detected = false;
  webrtc::ANAStats ana_statistics;
  webrtc::AudioProcessingStats apm_statistics;
};

struct VoiceReceiverInfo : public MediaReceiverInfo {
  VoiceReceiverInfo();
  ~VoiceReceiverInfo();
  int jitter_ms = 0;
  int jitter_buffer_ms = 0;
  int jitter_buffer_preferred_ms = 0;
  int delay_estimate_ms = 0;
  int audio_level = 0;
  // Stats below correspond to similarly-named fields in the WebRTC stats spec.
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats
  double total_output_energy = 0.0;
  uint64_t total_samples_received = 0;
  double total_output_duration = 0.0;
  uint64_t concealed_samples = 0;
  uint64_t silent_concealed_samples = 0;
  uint64_t concealment_events = 0;
  double jitter_buffer_target_delay_seconds = 0.0;
  uint64_t inserted_samples_for_deceleration = 0;
  uint64_t removed_samples_for_acceleration = 0;
  uint64_t fec_packets_received = 0;
  uint64_t fec_packets_discarded = 0;
  // Stats below correspond to similarly-named fields in the WebRTC stats spec.
  // https://w3c.github.io/webrtc-stats/#dom-rtcreceivedrtpstreamstats
  uint64_t packets_discarded = 0;
  // Stats below DO NOT correspond directly to anything in the WebRTC stats
  // fraction of synthesized audio inserted through expansion.
  float expand_rate = 0.0f;
  // fraction of synthesized speech inserted through expansion.
  float speech_expand_rate = 0.0f;
  // fraction of data out of secondary decoding, including FEC and RED.
  float secondary_decoded_rate = 0.0f;
  // Fraction of secondary data, including FEC and RED, that is discarded.
  // Discarding of secondary data can be caused by the reception of the primary
  // data, obsoleting the secondary data. It can also be caused by early
  // or late arrival of secondary data. This metric is the percentage of
  // discarded secondary data since last query of receiver info.
  float secondary_discarded_rate = 0.0f;
  // Fraction of data removed through time compression.
  float accelerate_rate = 0.0f;
  // Fraction of data inserted through time stretching.
  float preemptive_expand_rate = 0.0f;
  int decoding_calls_to_silence_generator = 0;
  int decoding_calls_to_neteq = 0;
  int decoding_normal = 0;
  // TODO(alexnarest): Consider decoding_neteq_plc for consistency
  int decoding_plc = 0;
  int decoding_codec_plc = 0;
  int decoding_cng = 0;
  int decoding_plc_cng = 0;
  int decoding_muted_output = 0;
  // Estimated capture start time in NTP time in ms.
  int64_t capture_start_ntp_time_ms = -1;
  // Count of the number of buffer flushes.
  uint64_t jitter_buffer_flushes = 0;
  // Number of samples expanded due to delayed packets.
  uint64_t delayed_packet_outage_samples = 0;
  // Arrival delay of received audio packets.
  double relative_packet_arrival_delay_seconds = 0.0;
  // Count and total duration of audio interruptions (loss-concealement periods
  // longer than 150 ms).
  int32_t interruption_count = 0;
  int32_t total_interruption_duration_ms = 0;
  // Remote outbound stats derived by the received RTCP sender reports.
  // https://w3c.github.io/webrtc-stats/#remoteoutboundrtpstats-dict*
  absl::optional<int64_t> last_sender_report_timestamp_ms;
  absl::optional<int64_t> last_sender_report_remote_timestamp_ms;
  uint32_t sender_reports_packets_sent = 0;
  uint64_t sender_reports_bytes_sent = 0;
  uint64_t sender_reports_reports_count = 0;
  absl::optional<webrtc::TimeDelta> round_trip_time;
  webrtc::TimeDelta total_round_trip_time = webrtc::TimeDelta::Zero();
  int round_trip_time_measurements = 0;
};

struct VideoSenderInfo : public MediaSenderInfo {
  VideoSenderInfo();
  ~VideoSenderInfo();
  std::vector<SsrcGroup> ssrc_groups;
  std::string encoder_implementation_name;
  int firs_rcvd = 0;
  int plis_rcvd = 0;
  int send_frame_width = 0;
  int send_frame_height = 0;
  int frames = 0;
  double framerate_input = 0;
  int framerate_sent = 0;
  int aggregated_framerate_sent = 0;
  int nominal_bitrate = 0;
  int adapt_reason = 0;
  int adapt_changes = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationreason
  webrtc::QualityLimitationReason quality_limitation_reason =
      webrtc::QualityLimitationReason::kNone;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationdurations
  std::map<webrtc::QualityLimitationReason, int64_t>
      quality_limitation_durations_ms;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-qualitylimitationresolutionchanges
  uint32_t quality_limitation_resolution_changes = 0;
  int avg_encode_ms = 0;
  int encode_usage_percent = 0;
  uint32_t frames_encoded = 0;
  uint32_t key_frames_encoded = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-totalencodetime
  uint64_t total_encode_time_ms = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcoutboundrtpstreamstats-totalencodedbytestarget
  uint64_t total_encoded_bytes_target = 0;
  uint64_t total_packet_send_delay_ms = 0;
  bool has_entered_low_resolution = false;
  absl::optional<uint64_t> qp_sum;
  webrtc::VideoContentType content_type = webrtc::VideoContentType::UNSPECIFIED;
  uint32_t frames_sent = 0;
  // https://w3c.github.io/webrtc-stats/#dom-rtcvideosenderstats-hugeframessent
  uint32_t huge_frames_sent = 0;
  uint32_t aggregated_huge_frames_sent = 0;
  absl::optional<std::string> rid;
};

struct VideoReceiverInfo : public MediaReceiverInfo {
  VideoReceiverInfo();
  ~VideoReceiverInfo();
  std::vector<SsrcGroup> ssrc_groups;
  std::string decoder_implementation_name;
  int packets_concealed = 0;
  int firs_sent = 0;
  int plis_sent = 0;
  int frame_width = 0;
  int frame_height = 0;
  int framerate_rcvd = 0;
  int framerate_decoded = 0;
  int framerate_output = 0;
  // Framerate as sent to the renderer.
  int framerate_render_input = 0;
  // Framerate that the renderer reports.
  int framerate_render_output = 0;
  uint32_t frames_received = 0;
  uint32_t frames_dropped = 0;
  uint32_t frames_decoded = 0;
  uint32_t key_frames_decoded = 0;
  uint32_t frames_rendered = 0;
  absl::optional<uint64_t> qp_sum;
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-totaldecodetime
  uint64_t total_decode_time_ms = 0;
  double total_inter_frame_delay = 0;
  double total_squared_inter_frame_delay = 0;
  int64_t interframe_delay_max_ms = -1;
  uint32_t freeze_count = 0;
  uint32_t pause_count = 0;
  uint32_t total_freezes_duration_ms = 0;
  uint32_t total_pauses_duration_ms = 0;
  uint32_t total_frames_duration_ms = 0;
  double sum_squared_frame_durations = 0.0;
  uint32_t jitter_ms = 0;

  webrtc::VideoContentType content_type = webrtc::VideoContentType::UNSPECIFIED;

  // All stats below are gathered per-VideoReceiver, but some will be correlated
  // across MediaStreamTracks.  NOTE(hta): when sinking stats into per-SSRC
  // structures, reflect this in the new layout.

  // Current frame decode latency.
  int decode_ms = 0;
  // Maximum observed frame decode latency.
  int max_decode_ms = 0;
  // Jitter (network-related) latency.
  int jitter_buffer_ms = 0;
  // Requested minimum playout latency.
  int min_playout_delay_ms = 0;
  // Requested latency to account for rendering delay.
  int render_delay_ms = 0;
  // Target overall delay: network+decode+render, accounting for
  // min_playout_delay_ms.
  int target_delay_ms = 0;
  // Current overall delay, possibly ramping towards target_delay_ms.
  int current_delay_ms = 0;

  // Estimated capture start time in NTP time in ms.
  int64_t capture_start_ntp_time_ms = -1;

  // First frame received to first frame decoded latency.
  int64_t first_frame_received_to_decoded_ms = -1;

  // Timing frame info: all important timestamps for a full lifetime of a
  // single 'timing frame'.
  absl::optional<webrtc::TimingFrameInfo> timing_frame_info;
};

struct BandwidthEstimationInfo {
  int available_send_bandwidth = 0;
  int available_recv_bandwidth = 0;
  int target_enc_bitrate = 0;
  int actual_enc_bitrate = 0;
  int retransmit_bitrate = 0;
  int transmit_bitrate = 0;
  int64_t bucket_delay = 0;
};

// Maps from payload type to `RtpCodecParameters`.
typedef std::map<int, webrtc::RtpCodecParameters> RtpCodecParametersMap;

struct VoiceMediaInfo {
  VoiceMediaInfo();
  ~VoiceMediaInfo();
  void Clear() {
    senders.clear();
    receivers.clear();
    send_codecs.clear();
    receive_codecs.clear();
  }
  std::vector<VoiceSenderInfo> senders;
  std::vector<VoiceReceiverInfo> receivers;
  RtpCodecParametersMap send_codecs;
  RtpCodecParametersMap receive_codecs;
  int32_t device_underrun_count = 0;
};

struct VideoMediaInfo {
  VideoMediaInfo();
  ~VideoMediaInfo();
  void Clear() {
    senders.clear();
    aggregated_senders.clear();
    receivers.clear();
    send_codecs.clear();
    receive_codecs.clear();
  }
  // Each sender info represents one "outbound-rtp" stream.In non - simulcast,
  // this means one info per RtpSender but if simulcast is used this means
  // one info per simulcast layer.
  std::vector<VideoSenderInfo> senders;
  // Used for legacy getStats() API's "ssrc" stats and modern getStats() API's
  // "track" stats. If simulcast is used, instead of having one sender info per
  // simulcast layer, the metrics of all layers of an RtpSender are aggregated
  // into a single sender info per RtpSender.
  std::vector<VideoSenderInfo> aggregated_senders;
  std::vector<VideoReceiverInfo> receivers;
  RtpCodecParametersMap send_codecs;
  RtpCodecParametersMap receive_codecs;
};

struct RtcpParameters {
  bool reduced_size = false;
  bool remote_estimate = false;
};

template <class Codec>
struct RtpParameters {
  virtual ~RtpParameters() = default;

  std::vector<Codec> codecs;
  std::vector<webrtc::RtpExtension> extensions;
  // For a send stream this is true if we've neogtiated a send direction,
  // for a receive stream this is true if we've negotiated a receive direction.
  bool is_stream_active = true;

  // TODO(pthatcher): Add streams.
  RtcpParameters rtcp;

  std::string ToString() const {
    rtc::StringBuilder ost;
    ost << "{";
    const char* separator = "";
    for (const auto& entry : ToStringMap()) {
      ost << separator << entry.first << ": " << entry.second;
      separator = ", ";
    }
    ost << "}";
    return ost.Release();
  }

 protected:
  virtual std::map<std::string, std::string> ToStringMap() const {
    return {{"codecs", VectorToString(codecs)},
            {"extensions", VectorToString(extensions)}};
  }
};

// TODO(deadbeef): Rename to RtpSenderParameters, since they're intended to
// encapsulate all the parameters needed for an RtpSender.
template <class Codec>
struct RtpSendParameters : RtpParameters<Codec> {
  int max_bandwidth_bps = -1;
  // This is the value to be sent in the MID RTP header extension (if the header
  // extension in included in the list of extensions).
  std::string mid;
  bool extmap_allow_mixed = false;

 protected:
  std::map<std::string, std::string> ToStringMap() const override {
    auto params = RtpParameters<Codec>::ToStringMap();
    params["max_bandwidth_bps"] = rtc::ToString(max_bandwidth_bps);
    params["mid"] = (mid.empty() ? "<not set>" : mid);
    params["extmap-allow-mixed"] = extmap_allow_mixed ? "true" : "false";
    return params;
  }
};

struct AudioSendParameters : RtpSendParameters<AudioCodec> {
  AudioSendParameters();
  ~AudioSendParameters() override;
  AudioOptions options;

 protected:
  std::map<std::string, std::string> ToStringMap() const override;
};

struct AudioRecvParameters : RtpParameters<AudioCodec> {};

class VoiceMediaChannel : public MediaChannel, public Delayable {
 public:
  explicit VoiceMediaChannel(webrtc::TaskQueueBase* network_thread)
      : MediaChannel(network_thread) {}
  VoiceMediaChannel(const MediaConfig& config,
                    webrtc::TaskQueueBase* network_thread)
      : MediaChannel(config, network_thread) {}
  ~VoiceMediaChannel() override {}

  cricket::MediaType media_type() const override;
  virtual bool SetSendParameters(const AudioSendParameters& params) = 0;
  virtual bool SetRecvParameters(const AudioRecvParameters& params) = 0;
  // Get the receive parameters for the incoming stream identified by `ssrc`.
  virtual webrtc::RtpParameters GetRtpReceiveParameters(
      uint32_t ssrc) const = 0;
  // Retrieve the receive parameters for the default receive
  // stream, which is used when SSRCs are not signaled.
  virtual webrtc::RtpParameters GetDefaultRtpReceiveParameters() const = 0;
  // Starts or stops playout of received audio.
  virtual void SetPlayout(bool playout) = 0;
  // Starts or stops sending (and potentially capture) of local audio.
  virtual void SetSend(bool send) = 0;
  // Configure stream for sending.
  virtual bool SetAudioSend(uint32_t ssrc,
                            bool enable,
                            const AudioOptions* options,
                            AudioSource* source) = 0;
  // Set speaker output volume of the specified ssrc.
  virtual bool SetOutputVolume(uint32_t ssrc, double volume) = 0;
  // Set speaker output volume for future unsignaled streams.
  virtual bool SetDefaultOutputVolume(double volume) = 0;
  // Returns if the telephone-event has been negotiated.
  virtual bool CanInsertDtmf() = 0;
  // Send a DTMF `event`. The DTMF out-of-band signal will be used.
  // The `ssrc` should be either 0 or a valid send stream ssrc.
  // The valid value for the `event` are 0 to 15 which corresponding to
  // DTMF event 0-9, *, #, A-D.
  virtual bool InsertDtmf(uint32_t ssrc, int event, int duration) = 0;
  // Gets quality stats for the channel.
  virtual bool GetStats(VoiceMediaInfo* info,
                        bool get_and_clear_legacy_stats) = 0;

  virtual void SetRawAudioSink(
      uint32_t ssrc,
      std::unique_ptr<webrtc::AudioSinkInterface> sink) = 0;
  virtual void SetDefaultRawAudioSink(
      std::unique_ptr<webrtc::AudioSinkInterface> sink) = 0;

  virtual std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const = 0;
};

// TODO(deadbeef): Rename to VideoSenderParameters, since they're intended to
// encapsulate all the parameters needed for a video RtpSender.
struct VideoSendParameters : RtpSendParameters<VideoCodec> {
  VideoSendParameters();
  ~VideoSendParameters() override;
  // Use conference mode? This flag comes from the remote
  // description's SDP line 'a=x-google-flag:conference', copied over
  // by VideoChannel::SetRemoteContent_w, and ultimately used by
  // conference mode screencast logic in
  // WebRtcVideoChannel::WebRtcVideoSendStream::CreateVideoEncoderConfig.
  // The special screencast behaviour is disabled by default.
  bool conference_mode = false;

 protected:
  std::map<std::string, std::string> ToStringMap() const override;
};

// TODO(deadbeef): Rename to VideoReceiverParameters, since they're intended to
// encapsulate all the parameters needed for a video RtpReceiver.
struct VideoRecvParameters : RtpParameters<VideoCodec> {};

class VideoMediaChannel : public MediaChannel, public Delayable {
 public:
  explicit VideoMediaChannel(webrtc::TaskQueueBase* network_thread)
      : MediaChannel(network_thread) {}
  VideoMediaChannel(const MediaConfig& config,
                    webrtc::TaskQueueBase* network_thread)
      : MediaChannel(config, network_thread) {}
  ~VideoMediaChannel() override {}

  cricket::MediaType media_type() const override;
  virtual bool SetSendParameters(const VideoSendParameters& params) = 0;
  virtual bool SetRecvParameters(const VideoRecvParameters& params) = 0;
  // Get the receive parameters for the incoming stream identified by `ssrc`.
  virtual webrtc::RtpParameters GetRtpReceiveParameters(
      uint32_t ssrc) const = 0;
  // Retrieve the receive parameters for the default receive
  // stream, which is used when SSRCs are not signaled.
  virtual webrtc::RtpParameters GetDefaultRtpReceiveParameters() const = 0;
  // Gets the currently set codecs/payload types to be used for outgoing media.
  virtual bool GetSendCodec(VideoCodec* send_codec) = 0;
  // Starts or stops transmission (and potentially capture) of local video.
  virtual bool SetSend(bool send) = 0;
  // Configure stream for sending and register a source.
  // The `ssrc` must correspond to a registered send stream.
  virtual bool SetVideoSend(
      uint32_t ssrc,
      const VideoOptions* options,
      rtc::VideoSourceInterface<webrtc::VideoFrame>* source) = 0;
  // Sets the sink object to be used for the specified stream.
  virtual bool SetSink(uint32_t ssrc,
                       rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) = 0;
  // The sink is used for the 'default' stream.
  virtual void SetDefaultSink(
      rtc::VideoSinkInterface<webrtc::VideoFrame>* sink) = 0;
  // This fills the "bitrate parts" (rtx, video bitrate) of the
  // BandwidthEstimationInfo, since that part that isn't possible to get
  // through webrtc::Call::GetStats, as they are statistics of the send
  // streams.
  // TODO(holmer): We should change this so that either BWE graphs doesn't
  // need access to bitrates of the streams, or change the (RTC)StatsCollector
  // so that it's getting the send stream stats separately by calling
  // GetStats(), and merges with BandwidthEstimationInfo by itself.
  virtual void FillBitrateInfo(BandwidthEstimationInfo* bwe_info) = 0;
  // Gets quality stats for the channel.
  virtual bool GetStats(VideoMediaInfo* info) = 0;
  // Set recordable encoded frame callback for `ssrc`
  virtual void SetRecordableEncodedFrameCallback(
      uint32_t ssrc,
      std::function<void(const webrtc::RecordableEncodedFrame&)> callback) = 0;
  // Clear recordable encoded frame callback for `ssrc`
  virtual void ClearRecordableEncodedFrameCallback(uint32_t ssrc) = 0;
  // Cause generation of a keyframe for `ssrc`
  virtual void GenerateKeyFrame(uint32_t ssrc) = 0;

  virtual std::vector<webrtc::RtpSource> GetSources(uint32_t ssrc) const = 0;
};

// Info about data received in DataMediaChannel.  For use in
// DataMediaChannel::SignalDataReceived and in all of the signals that
// signal fires, on up the chain.
struct ReceiveDataParams {
  // The in-packet stream indentifier.
  // SCTP data channels use SIDs.
  int sid = 0;
  // The type of message (binary, text, or control).
  webrtc::DataMessageType type = webrtc::DataMessageType::kText;
  // A per-stream value incremented per packet in the stream.
  int seq_num = 0;
};

enum SendDataResult { SDR_SUCCESS, SDR_ERROR, SDR_BLOCK };

}  // namespace cricket

#endif  // MEDIA_BASE_MEDIA_CHANNEL_H_
