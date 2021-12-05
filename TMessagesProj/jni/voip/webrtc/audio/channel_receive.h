/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef AUDIO_CHANNEL_RECEIVE_H_
#define AUDIO_CHANNEL_RECEIVE_H_

#include <map>
#include <memory>
#include <utility>
#include <vector>

#include "absl/types/optional.h"
#include "api/audio/audio_mixer.h"
#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/call/audio_sink.h"
#include "api/call/transport.h"
#include "api/crypto/crypto_options.h"
#include "api/frame_transformer_interface.h"
#include "api/neteq/neteq_factory.h"
#include "api/transport/rtp/rtp_source.h"
#include "call/rtp_packet_sink_interface.h"
#include "call/syncable.h"
#include "modules/audio_coding/include/audio_coding_module_typedefs.h"
#include "modules/rtp_rtcp/source/source_tracker.h"
#include "system_wrappers/include/clock.h"

// TODO(solenberg, nisse): This file contains a few NOLINT marks, to silence
// warnings about use of unsigned short.
// These need cleanup, in a separate cl.

namespace rtc {
class TimestampWrapAroundHandler;
}

namespace webrtc {

class AudioDeviceModule;
class FrameDecryptorInterface;
class PacketRouter;
class ProcessThread;
class RateLimiter;
class ReceiveStatistics;
class RtcEventLog;
class RtpPacketReceived;
class RtpRtcp;

struct CallReceiveStatistics {
  unsigned int cumulativeLost;
  unsigned int jitterSamples;
  int64_t rttMs;
  int64_t payload_bytes_rcvd = 0;
  int64_t header_and_padding_bytes_rcvd = 0;
  int packetsReceived;
  // The capture NTP time (in local timebase) of the first played out audio
  // frame.
  int64_t capture_start_ntp_time_ms_;
  // The timestamp at which the last packet was received, i.e. the time of the
  // local clock when it was received - not the RTP timestamp of that packet.
  // https://w3c.github.io/webrtc-stats/#dom-rtcinboundrtpstreamstats-lastpacketreceivedtimestamp
  absl::optional<int64_t> last_packet_received_timestamp_ms;
  // Remote outbound stats derived by the received RTCP sender reports.
  // Note that the timestamps below correspond to the time elapsed since the
  // Unix epoch.
  // https://w3c.github.io/webrtc-stats/#remoteoutboundrtpstats-dict*
  absl::optional<int64_t> last_sender_report_timestamp_ms;
  absl::optional<int64_t> last_sender_report_remote_timestamp_ms;
  uint32_t sender_reports_packets_sent = 0;
  uint64_t sender_reports_bytes_sent = 0;
  uint64_t sender_reports_reports_count = 0;
};

namespace voe {

class ChannelSendInterface;

// Interface class needed for AudioReceiveStream tests that use a
// MockChannelReceive.

class ChannelReceiveInterface : public RtpPacketSinkInterface {
 public:
  virtual ~ChannelReceiveInterface() = default;

  virtual void SetSink(AudioSinkInterface* sink) = 0;

  virtual void SetReceiveCodecs(
      const std::map<int, SdpAudioFormat>& codecs) = 0;

  virtual void StartPlayout() = 0;
  virtual void StopPlayout() = 0;

  // Payload type and format of last received RTP packet, if any.
  virtual absl::optional<std::pair<int, SdpAudioFormat>> GetReceiveCodec()
      const = 0;

  virtual void ReceivedRTCPPacket(const uint8_t* data, size_t length) = 0;

  virtual void SetChannelOutputVolumeScaling(float scaling) = 0;
  virtual int GetSpeechOutputLevelFullRange() const = 0;
  // See description of "totalAudioEnergy" in the WebRTC stats spec:
  // https://w3c.github.io/webrtc-stats/#dom-rtcmediastreamtrackstats-totalaudioenergy
  virtual double GetTotalOutputEnergy() const = 0;
  virtual double GetTotalOutputDuration() const = 0;

  // Stats.
  virtual NetworkStatistics GetNetworkStatistics(
      bool get_and_clear_legacy_stats) const = 0;
  virtual AudioDecodingCallStats GetDecodingCallStatistics() const = 0;

  // Audio+Video Sync.
  virtual uint32_t GetDelayEstimate() const = 0;
  virtual bool SetMinimumPlayoutDelay(int delay_ms) = 0;
  virtual bool GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                                      int64_t* time_ms) const = 0;
  virtual void SetEstimatedPlayoutNtpTimestampMs(int64_t ntp_timestamp_ms,
                                                 int64_t time_ms) = 0;
  virtual absl::optional<int64_t> GetCurrentEstimatedPlayoutNtpTimestampMs(
      int64_t now_ms) const = 0;

  // Audio quality.
  // Base minimum delay sets lower bound on minimum delay value which
  // determines minimum delay until audio playout.
  virtual bool SetBaseMinimumPlayoutDelayMs(int delay_ms) = 0;
  virtual int GetBaseMinimumPlayoutDelayMs() const = 0;

  // Produces the transport-related timestamps; current_delay_ms is left unset.
  virtual absl::optional<Syncable::Info> GetSyncInfo() const = 0;

  virtual void RegisterReceiverCongestionControlObjects(
      PacketRouter* packet_router) = 0;
  virtual void ResetReceiverCongestionControlObjects() = 0;

  virtual CallReceiveStatistics GetRTCPStatistics() const = 0;
  virtual void SetNACKStatus(bool enable, int max_packets) = 0;

  virtual AudioMixer::Source::AudioFrameInfo GetAudioFrameWithInfo(
      int sample_rate_hz,
      AudioFrame* audio_frame) = 0;

  virtual int PreferredSampleRate() const = 0;

  // Sets the source tracker to notify about "delivered" packets when output is
  // muted.
  virtual void SetSourceTracker(SourceTracker* source_tracker) = 0;

  // Associate to a send channel.
  // Used for obtaining RTT for a receive-only channel.
  virtual void SetAssociatedSendChannel(
      const ChannelSendInterface* channel) = 0;

  // Sets a frame transformer between the depacketizer and the decoder, to
  // transform the received frames before decoding them.
  virtual void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface>
          frame_transformer) = 0;
};

std::unique_ptr<ChannelReceiveInterface> CreateChannelReceive(
    Clock* clock,
    ProcessThread* module_process_thread,
    NetEqFactory* neteq_factory,
    AudioDeviceModule* audio_device_module,
    Transport* rtcp_send_transport,
    RtcEventLog* rtc_event_log,
    uint32_t local_ssrc,
    uint32_t remote_ssrc,
    size_t jitter_buffer_max_packets,
    bool jitter_buffer_fast_playout,
    int jitter_buffer_min_delay_ms,
    bool jitter_buffer_enable_rtx_handling,
    rtc::scoped_refptr<AudioDecoderFactory> decoder_factory,
    absl::optional<AudioCodecPairId> codec_pair_id,
    rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor,
    const webrtc::CryptoOptions& crypto_options,
    rtc::scoped_refptr<FrameTransformerInterface> frame_transformer);

}  // namespace voe
}  // namespace webrtc

#endif  // AUDIO_CHANNEL_RECEIVE_H_
