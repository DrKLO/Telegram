/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_VIDEO_RECEIVE_STREAM_H_
#define CALL_VIDEO_RECEIVE_STREAM_H_

#include <limits>
#include <map>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "api/call/transport.h"
#include "api/crypto/crypto_options.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/rtp_headers.h"
#include "api/rtp_parameters.h"
#include "api/transport/rtp/rtp_source.h"
#include "api/video/recordable_encoded_frame.h"
#include "api/video/video_content_type.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/sdp_video_format.h"
#include "call/rtp_config.h"
#include "common_video/frame_counts.h"
#include "modules/rtp_rtcp/include/rtcp_statistics.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"

namespace webrtc {

class RtpPacketSinkInterface;
class VideoDecoderFactory;

class VideoReceiveStream {
 public:
  // Class for handling moving in/out recording state.
  struct RecordingState {
    RecordingState() = default;
    explicit RecordingState(
        std::function<void(const RecordableEncodedFrame&)> callback)
        : callback(std::move(callback)) {}

    // Callback stored from the VideoReceiveStream. The VideoReceiveStream
    // client should not interpret the attribute.
    std::function<void(const RecordableEncodedFrame&)> callback;
    // Memento of internal state in VideoReceiveStream, recording wether
    // we're currently causing generation of a keyframe from the sender. Needed
    // to avoid sending double keyframe requests. The VideoReceiveStream client
    // should not interpret the attribute.
    bool keyframe_needed = false;
    // Memento of when a keyframe request was last sent. The VideoReceiveStream
    // client should not interpret the attribute.
    absl::optional<int64_t> last_keyframe_request_ms;
  };

  // TODO(mflodman) Move all these settings to VideoDecoder and move the
  // declaration to common_types.h.
  struct Decoder {
    Decoder();
    Decoder(const Decoder&);
    ~Decoder();
    std::string ToString() const;

    SdpVideoFormat video_format;

    // Received RTP packets with this payload type will be sent to this decoder
    // instance.
    int payload_type = 0;
  };

  struct Stats {
    Stats();
    ~Stats();
    std::string ToString(int64_t time_ms) const;

    int network_frame_rate = 0;
    int decode_frame_rate = 0;
    int render_frame_rate = 0;
    uint32_t frames_rendered = 0;

    // Decoder stats.
    std::string decoder_implementation_name = "unknown";
    FrameCounts frame_counts;
    int decode_ms = 0;
    int max_decode_ms = 0;
    int current_delay_ms = 0;
    int target_delay_ms = 0;
    int jitter_buffer_ms = 0;
    // https://w3c.github.io/webrtc-stats/#dom-rtcvideoreceiverstats-jitterbufferdelay
    double jitter_buffer_delay_seconds = 0;
    // https://w3c.github.io/webrtc-stats/#dom-rtcvideoreceiverstats-jitterbufferemittedcount
    uint64_t jitter_buffer_emitted_count = 0;
    int min_playout_delay_ms = 0;
    int render_delay_ms = 10;
    int64_t interframe_delay_max_ms = -1;
    // Frames dropped due to decoding failures or if the system is too slow.
    // https://www.w3.org/TR/webrtc-stats/#dom-rtcvideoreceiverstats-framesdropped
    uint32_t frames_dropped = 0;
    uint32_t frames_decoded = 0;
    // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-totaldecodetime
    uint64_t total_decode_time_ms = 0;
    // Total inter frame delay in seconds.
    // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-totalinterframedelay
    double total_inter_frame_delay = 0;
    // Total squared inter frame delay in seconds^2.
    // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-totalsqauredinterframedelay
    double total_squared_inter_frame_delay = 0;
    int64_t first_frame_received_to_decoded_ms = -1;
    absl::optional<uint64_t> qp_sum;

    int current_payload_type = -1;

    int total_bitrate_bps = 0;

    int width = 0;
    int height = 0;

    uint32_t freeze_count = 0;
    uint32_t pause_count = 0;
    uint32_t total_freezes_duration_ms = 0;
    uint32_t total_pauses_duration_ms = 0;
    uint32_t total_frames_duration_ms = 0;
    double sum_squared_frame_durations = 0.0;

    VideoContentType content_type = VideoContentType::UNSPECIFIED;

    // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-estimatedplayouttimestamp
    absl::optional<int64_t> estimated_playout_ntp_timestamp_ms;
    int sync_offset_ms = std::numeric_limits<int>::max();

    uint32_t ssrc = 0;
    std::string c_name;
    RtpReceiveStats rtp_stats;
    RtcpPacketTypeCounter rtcp_packet_type_counts;

    // Timing frame info: all important timestamps for a full lifetime of a
    // single 'timing frame'.
    absl::optional<webrtc::TimingFrameInfo> timing_frame_info;
  };

  struct Config {
   private:
    // Access to the copy constructor is private to force use of the Copy()
    // method for those exceptional cases where we do use it.
    Config(const Config&);

   public:
    Config() = delete;
    Config(Config&&);
    explicit Config(Transport* rtcp_send_transport);
    Config& operator=(Config&&);
    Config& operator=(const Config&) = delete;
    ~Config();

    // Mostly used by tests.  Avoid creating copies if you can.
    Config Copy() const { return Config(*this); }

    std::string ToString() const;

    // Decoders for every payload that we can receive.
    std::vector<Decoder> decoders;

    // Ownership stays with WebrtcVideoEngine (delegated from PeerConnection).
    VideoDecoderFactory* decoder_factory = nullptr;

    // Receive-stream specific RTP settings.
    struct Rtp {
      Rtp();
      Rtp(const Rtp&);
      ~Rtp();
      std::string ToString() const;

      // Synchronization source (stream identifier) to be received.
      uint32_t remote_ssrc = 0;

      // Sender SSRC used for sending RTCP (such as receiver reports).
      uint32_t local_ssrc = 0;

      // See RtcpMode for description.
      RtcpMode rtcp_mode = RtcpMode::kCompound;

      // Extended RTCP settings.
      struct RtcpXr {
        // True if RTCP Receiver Reference Time Report Block extension
        // (RFC 3611) should be enabled.
        bool receiver_reference_time_report = false;
      } rtcp_xr;

      // See draft-holmer-rmcat-transport-wide-cc-extensions for details.
      bool transport_cc = false;

      // See LntfConfig for description.
      LntfConfig lntf;

      // See NackConfig for description.
      NackConfig nack;

      // Payload types for ULPFEC and RED, respectively.
      int ulpfec_payload_type = -1;
      int red_payload_type = -1;

      // SSRC for retransmissions.
      uint32_t rtx_ssrc = 0;

      // Set if the stream is protected using FlexFEC.
      bool protected_by_flexfec = false;

      // Map from rtx payload type -> media payload type.
      // For RTX to be enabled, both an SSRC and this mapping are needed.
      std::map<int, int> rtx_associated_payload_types;

      // Payload types that should be depacketized using raw depacketizer
      // (payload header will not be parsed and must not be present, additional
      // meta data is expected to be present in generic frame descriptor
      // RTP header extension).
      std::set<int> raw_payload_types;

      // RTP header extensions used for the received stream.
      std::vector<RtpExtension> extensions;
    } rtp;

    // Transport for outgoing packets (RTCP).
    Transport* rtcp_send_transport = nullptr;

    // Must always be set.
    rtc::VideoSinkInterface<VideoFrame>* renderer = nullptr;

    // Expected delay needed by the renderer, i.e. the frame will be delivered
    // this many milliseconds, if possible, earlier than the ideal render time.
    int render_delay_ms = 10;

    // If false, pass frames on to the renderer as soon as they are
    // available.
    bool enable_prerenderer_smoothing = true;

    // Identifier for an A/V synchronization group. Empty string to disable.
    // TODO(pbos): Synchronize streams in a sync group, not just video streams
    // to one of the audio streams.
    std::string sync_group;

    // Target delay in milliseconds. A positive value indicates this stream is
    // used for streaming instead of a real-time call.
    int target_delay_ms = 0;

    // TODO(nisse): Used with VideoDecoderFactory::LegacyCreateVideoDecoder.
    // Delete when that method is retired.
    std::string stream_id;

    // An optional custom frame decryptor that allows the entire frame to be
    // decrypted in whatever way the caller choses. This is not required by
    // default.
    rtc::scoped_refptr<webrtc::FrameDecryptorInterface> frame_decryptor;

    // Per PeerConnection cryptography options.
    CryptoOptions crypto_options;

    rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer;
  };

  // Starts stream activity.
  // When a stream is active, it can receive, process and deliver packets.
  virtual void Start() = 0;
  // Stops stream activity.
  // When a stream is stopped, it can't receive, process or deliver packets.
  virtual void Stop() = 0;

  // TODO(pbos): Add info on currently-received codec to Stats.
  virtual Stats GetStats() const = 0;

  // RtpDemuxer only forwards a given RTP packet to one sink. However, some
  // sinks, such as FlexFEC, might wish to be informed of all of the packets
  // a given sink receives (or any set of sinks). They may do so by registering
  // themselves as secondary sinks.
  virtual void AddSecondarySink(RtpPacketSinkInterface* sink) = 0;
  virtual void RemoveSecondarySink(const RtpPacketSinkInterface* sink) = 0;

  virtual std::vector<RtpSource> GetSources() const = 0;

  // Sets a base minimum for the playout delay. Base minimum delay sets lower
  // bound on minimum delay value determining lower bound on playout delay.
  //
  // Returns true if value was successfully set, false overwise.
  virtual bool SetBaseMinimumPlayoutDelayMs(int delay_ms) = 0;

  // Returns current value of base minimum delay in milliseconds.
  virtual int GetBaseMinimumPlayoutDelayMs() const = 0;

  // Allows a FrameDecryptor to be attached to a VideoReceiveStream after
  // creation without resetting the decoder state.
  virtual void SetFrameDecryptor(
      rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) = 0;

  // Allows a frame transformer to be attached to a VideoReceiveStream after
  // creation without resetting the decoder state.
  virtual void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) = 0;

  // Sets and returns recording state. The old state is moved out
  // of the video receive stream and returned to the caller, and |state|
  // is moved in. If the state's callback is set, it will be called with
  // recordable encoded frames as they arrive.
  // If |generate_key_frame| is true, the method will generate a key frame.
  // When the function returns, it's guaranteed that all old callouts
  // to the returned callback has ceased.
  // Note: the client should not interpret the returned state's attributes, but
  // instead treat it as opaque data.
  virtual RecordingState SetAndGetRecordingState(RecordingState state,
                                                 bool generate_key_frame) = 0;

  // Cause eventual generation of a key frame from the sender.
  virtual void GenerateKeyFrame() = 0;

 protected:
  virtual ~VideoReceiveStream() {}
};

}  // namespace webrtc

#endif  // CALL_VIDEO_RECEIVE_STREAM_H_
