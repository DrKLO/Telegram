/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtc_stats_collector.h"

#include <stddef.h>
#include <stdint.h>

#include <algorithm>
#include <initializer_list>
#include <memory>
#include <ostream>
#include <string>
#include <type_traits>
#include <utility>
#include <vector>

#include "absl/strings/str_replace.h"
#include "absl/types/optional.h"
#include "api/candidate.h"
#include "api/dtls_transport_interface.h"
#include "api/media_stream_interface.h"
#include "api/media_stream_track.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_direction.h"
#include "api/stats/attribute.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "api/units/time_delta.h"
#include "api/units/timestamp.h"
#include "api/video/recordable_encoded_frame.h"
#include "api/video/video_content_type.h"
#include "api/video/video_frame.h"
#include "api/video/video_sink_interface.h"
#include "api/video/video_source_interface.h"
#include "api/video/video_timing.h"
#include "api/video_codecs/scalability_mode.h"
#include "common_video/include/quality_limitation_reason.h"
#include "media/base/media_channel.h"
#include "modules/audio_device/include/audio_device.h"
#include "modules/audio_processing/include/audio_processing_statistics.h"
#include "modules/rtp_rtcp/include/report_block_data.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/ice_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/port.h"
#include "pc/media_stream.h"
#include "pc/stream_collection.h"
#include "pc/test/fake_data_channel_controller.h"
#include "pc/test/fake_peer_connection_for_stats.h"
#include "pc/test/mock_data_channel.h"
#include "pc/test/mock_rtp_receiver_internal.h"
#include "pc/test/mock_rtp_sender_internal.h"
#include "pc/test/rtc_stats_obtainer.h"
#include "rtc_base/checks.h"
#include "rtc_base/fake_clock.h"
#include "rtc_base/fake_ssl_identity.h"
#include "rtc_base/gunit.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/rtc_certificate.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/ssl_fingerprint.h"
#include "rtc_base/ssl_identity.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/strings/json.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/time_utils.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::_;
using ::testing::AtLeast;
using ::testing::Invoke;
using ::testing::Return;

namespace webrtc {

// These are used by gtest code, such as if `EXPECT_EQ` fails.
void PrintTo(const RTCCertificateStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCCodecStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCDataChannelStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCIceCandidatePairStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCLocalIceCandidateStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCRemoteIceCandidateStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCPeerConnectionStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCInboundRtpStreamStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCOutboundRtpStreamStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCRemoteInboundRtpStreamStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCAudioSourceStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCVideoSourceStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

void PrintTo(const RTCTransportStats& stats, ::std::ostream* os) {
  *os << stats.ToJson();
}

namespace {

const int64_t kGetStatsReportTimeoutMs = 1000;

// Fake data used by `SetupExampleStatsVoiceGraph()` to fill in remote outbound
// stats.
constexpr int64_t kRemoteOutboundStatsTimestampMs = 123;
constexpr int64_t kRemoteOutboundStatsRemoteTimestampMs = 456;
constexpr uint32_t kRemoteOutboundStatsPacketsSent = 7u;
constexpr uint64_t kRemoteOutboundStatsBytesSent = 8u;
constexpr uint64_t kRemoteOutboundStatsReportsCount = 9u;

struct CertificateInfo {
  rtc::scoped_refptr<rtc::RTCCertificate> certificate;
  std::vector<std::string> ders;
  std::vector<std::string> pems;
  std::vector<std::string> fingerprints;
};

// Return the ID for an object of the given type in a report.
// The object must be present and be unique.
template <typename T>
std::string IdForType(const RTCStatsReport* report) {
  auto stats_of_my_type = report->RTCStatsReport::GetStatsOfType<T>();
  // We cannot use ASSERT here, since we're within a function.
  EXPECT_EQ(1U, stats_of_my_type.size())
      << "Unexpected number of stats of this type";
  if (stats_of_my_type.size() == 1) {
    return stats_of_my_type[0]->id();
  } else {
    // Return something that is not going to be a valid stas ID.
    return "Type not found";
  }
}

std::unique_ptr<CertificateInfo> CreateFakeCertificateAndInfoFromDers(
    const std::vector<std::string>& ders) {
  RTC_CHECK(!ders.empty());
  std::unique_ptr<CertificateInfo> info(new CertificateInfo());
  info->ders = ders;
  for (const std::string& der : ders) {
    info->pems.push_back(rtc::SSLIdentity::DerToPem(
        "CERTIFICATE", reinterpret_cast<const unsigned char*>(der.c_str()),
        der.length()));
  }
  info->certificate =
      rtc::RTCCertificate::Create(std::unique_ptr<rtc::FakeSSLIdentity>(
          new rtc::FakeSSLIdentity(info->pems)));
  // Strip header/footer and newline characters of PEM strings.
  for (size_t i = 0; i < info->pems.size(); ++i) {
    absl::StrReplaceAll({{"-----BEGIN CERTIFICATE-----", ""},
                         {"-----END CERTIFICATE-----", ""},
                         {"\n", ""}},
                        &info->pems[i]);
  }
  // Fingerprints for the whole certificate chain, starting with leaf
  // certificate.
  const rtc::SSLCertChain& chain = info->certificate->GetSSLCertificateChain();
  std::unique_ptr<rtc::SSLFingerprint> fp;
  for (size_t i = 0; i < chain.GetSize(); i++) {
    fp = rtc::SSLFingerprint::Create("sha-1", chain.Get(i));
    EXPECT_TRUE(fp);
    info->fingerprints.push_back(fp->GetRfc4572Fingerprint());
  }
  EXPECT_EQ(info->ders.size(), info->fingerprints.size());
  return info;
}

std::unique_ptr<cricket::Candidate> CreateFakeCandidate(
    const std::string& hostname,
    int port,
    const std::string& protocol,
    const rtc::AdapterType adapter_type,
    const absl::string_view candidate_type,
    uint32_t priority,
    const rtc::AdapterType underlying_type_for_vpn =
        rtc::ADAPTER_TYPE_UNKNOWN) {
  std::unique_ptr<cricket::Candidate> candidate(new cricket::Candidate());
  candidate->set_address(rtc::SocketAddress(hostname, port));
  candidate->set_protocol(protocol);
  candidate->set_network_type(adapter_type);
  candidate->set_underlying_type_for_vpn(underlying_type_for_vpn);
  candidate->set_type(candidate_type);
  candidate->set_priority(priority);
  // Defaults for testing.
  candidate->set_foundation("foundationIsAString");
  candidate->set_username("iceusernamefragment");
  return candidate;
}

class FakeAudioProcessor : public AudioProcessorInterface {
 public:
  FakeAudioProcessor() {}
  ~FakeAudioProcessor() {}

 private:
  AudioProcessorInterface::AudioProcessorStatistics GetStats(
      bool has_recv_streams) override {
    AudioProcessorStatistics stats;
    stats.apm_statistics.echo_return_loss = 2.0;
    stats.apm_statistics.echo_return_loss_enhancement = 3.0;
    return stats;
  }
};

class FakeAudioTrackForStats : public MediaStreamTrack<AudioTrackInterface> {
 public:
  static rtc::scoped_refptr<FakeAudioTrackForStats> Create(
      const std::string& id,
      MediaStreamTrackInterface::TrackState state,
      bool create_fake_audio_processor) {
    auto audio_track_stats = rtc::make_ref_counted<FakeAudioTrackForStats>(id);
    audio_track_stats->set_state(state);
    if (create_fake_audio_processor) {
      audio_track_stats->processor_ =
          rtc::make_ref_counted<FakeAudioProcessor>();
    }
    return audio_track_stats;
  }

  explicit FakeAudioTrackForStats(const std::string& id)
      : MediaStreamTrack<AudioTrackInterface>(id) {}

  std::string kind() const override {
    return MediaStreamTrackInterface::kAudioKind;
  }
  AudioSourceInterface* GetSource() const override { return nullptr; }
  void AddSink(AudioTrackSinkInterface* sink) override {}
  void RemoveSink(AudioTrackSinkInterface* sink) override {}
  bool GetSignalLevel(int* level) override { return false; }
  rtc::scoped_refptr<AudioProcessorInterface> GetAudioProcessor() override {
    return processor_;
  }

 private:
  rtc::scoped_refptr<FakeAudioProcessor> processor_;
};

class FakeVideoTrackSourceForStats : public VideoTrackSourceInterface {
 public:
  static rtc::scoped_refptr<FakeVideoTrackSourceForStats> Create(
      int input_width,
      int input_height) {
    return rtc::make_ref_counted<FakeVideoTrackSourceForStats>(input_width,
                                                               input_height);
  }

  FakeVideoTrackSourceForStats(int input_width, int input_height)
      : input_width_(input_width), input_height_(input_height) {}
  ~FakeVideoTrackSourceForStats() override {}

  // VideoTrackSourceInterface
  bool is_screencast() const override { return false; }
  absl::optional<bool> needs_denoising() const override { return false; }
  bool GetStats(VideoTrackSourceInterface::Stats* stats) override {
    stats->input_width = input_width_;
    stats->input_height = input_height_;
    return true;
  }
  // MediaSourceInterface (part of VideoTrackSourceInterface)
  MediaSourceInterface::SourceState state() const override {
    return MediaSourceInterface::SourceState::kLive;
  }
  bool remote() const override { return false; }
  // NotifierInterface (part of MediaSourceInterface)
  void RegisterObserver(ObserverInterface* observer) override {}
  void UnregisterObserver(ObserverInterface* observer) override {}
  // rtc::VideoSourceInterface<VideoFrame> (part of VideoTrackSourceInterface)
  void AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrame>* sink,
                       const rtc::VideoSinkWants& wants) override {}
  void RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) override {}
  bool SupportsEncodedOutput() const override { return false; }
  void GenerateKeyFrame() override {}
  void AddEncodedSink(
      rtc::VideoSinkInterface<RecordableEncodedFrame>* sink) override {}
  void RemoveEncodedSink(
      rtc::VideoSinkInterface<RecordableEncodedFrame>* sink) override {}

 private:
  int input_width_;
  int input_height_;
};

class FakeVideoTrackForStats : public MediaStreamTrack<VideoTrackInterface> {
 public:
  static rtc::scoped_refptr<FakeVideoTrackForStats> Create(
      const std::string& id,
      MediaStreamTrackInterface::TrackState state,
      rtc::scoped_refptr<VideoTrackSourceInterface> source) {
    auto video_track =
        rtc::make_ref_counted<FakeVideoTrackForStats>(id, std::move(source));
    video_track->set_state(state);
    return video_track;
  }

  FakeVideoTrackForStats(const std::string& id,
                         rtc::scoped_refptr<VideoTrackSourceInterface> source)
      : MediaStreamTrack<VideoTrackInterface>(id), source_(source) {}

  std::string kind() const override {
    return MediaStreamTrackInterface::kVideoKind;
  }

  void AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrame>* sink,
                       const rtc::VideoSinkWants& wants) override {}
  void RemoveSink(rtc::VideoSinkInterface<VideoFrame>* sink) override {}

  VideoTrackSourceInterface* GetSource() const override {
    return source_.get();
  }

 private:
  rtc::scoped_refptr<VideoTrackSourceInterface> source_;
};

rtc::scoped_refptr<MediaStreamTrackInterface> CreateFakeTrack(
    cricket::MediaType media_type,
    const std::string& track_id,
    MediaStreamTrackInterface::TrackState track_state,
    bool create_fake_audio_processor = false) {
  if (media_type == cricket::MEDIA_TYPE_AUDIO) {
    return FakeAudioTrackForStats::Create(track_id, track_state,
                                          create_fake_audio_processor);
  } else {
    RTC_DCHECK_EQ(media_type, cricket::MEDIA_TYPE_VIDEO);
    return FakeVideoTrackForStats::Create(track_id, track_state, nullptr);
  }
}

rtc::scoped_refptr<MockRtpSenderInternal> CreateMockSender(
    cricket::MediaType media_type,
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    uint32_t ssrc,
    int attachment_id,
    std::vector<std::string> local_stream_ids) {
  RTC_DCHECK(!track ||
             (track->kind() == MediaStreamTrackInterface::kAudioKind &&
              media_type == cricket::MEDIA_TYPE_AUDIO) ||
             (track->kind() == MediaStreamTrackInterface::kVideoKind &&
              media_type == cricket::MEDIA_TYPE_VIDEO));
  auto sender = rtc::make_ref_counted<MockRtpSenderInternal>();
  EXPECT_CALL(*sender, track()).WillRepeatedly(Return(track));
  EXPECT_CALL(*sender, ssrc()).WillRepeatedly(Return(ssrc));
  EXPECT_CALL(*sender, media_type()).WillRepeatedly(Return(media_type));
  EXPECT_CALL(*sender, GetParameters())
      .WillRepeatedly(
          Invoke([s = sender.get()]() { return s->GetParametersInternal(); }));
  EXPECT_CALL(*sender, GetParametersInternal()).WillRepeatedly(Invoke([ssrc]() {
    RtpParameters params;
    params.encodings.push_back(RtpEncodingParameters());
    params.encodings[0].ssrc = ssrc;
    return params;
  }));
  EXPECT_CALL(*sender, AttachmentId()).WillRepeatedly(Return(attachment_id));
  EXPECT_CALL(*sender, stream_ids()).WillRepeatedly(Return(local_stream_ids));
  EXPECT_CALL(*sender, SetTransceiverAsStopped());
  return sender;
}

rtc::scoped_refptr<MockRtpReceiverInternal> CreateMockReceiver(
    const rtc::scoped_refptr<MediaStreamTrackInterface>& track,
    uint32_t ssrc,
    int attachment_id) {
  auto receiver = rtc::make_ref_counted<MockRtpReceiverInternal>();
  EXPECT_CALL(*receiver, track()).WillRepeatedly(Return(track));
  EXPECT_CALL(*receiver, ssrc()).WillRepeatedly(Invoke([ssrc]() {
    return ssrc;
  }));
  EXPECT_CALL(*receiver, streams())
      .WillRepeatedly(
          Return(std::vector<rtc::scoped_refptr<MediaStreamInterface>>({})));

  EXPECT_CALL(*receiver, media_type())
      .WillRepeatedly(
          Return(track->kind() == MediaStreamTrackInterface::kAudioKind
                     ? cricket::MEDIA_TYPE_AUDIO
                     : cricket::MEDIA_TYPE_VIDEO));
  EXPECT_CALL(*receiver, GetParameters()).WillRepeatedly(Invoke([ssrc]() {
    RtpParameters params;
    params.encodings.push_back(RtpEncodingParameters());
    params.encodings[0].ssrc = ssrc;
    return params;
  }));
  EXPECT_CALL(*receiver, AttachmentId()).WillRepeatedly(Return(attachment_id));
  EXPECT_CALL(*receiver, Stop()).WillRepeatedly(Return());
  return receiver;
}

class RTCStatsCollectorWrapper {
 public:
  explicit RTCStatsCollectorWrapper(
      rtc::scoped_refptr<FakePeerConnectionForStats> pc)
      : pc_(pc),
        stats_collector_(
            RTCStatsCollector::Create(pc.get(),
                                      50 * rtc::kNumMicrosecsPerMillisec)) {}

  rtc::scoped_refptr<RTCStatsCollector> stats_collector() {
    return stats_collector_;
  }

  rtc::scoped_refptr<const RTCStatsReport> GetStatsReport() {
    rtc::scoped_refptr<RTCStatsObtainer> callback = RTCStatsObtainer::Create();
    stats_collector_->GetStatsReport(callback);
    return WaitForReport(callback);
  }

  rtc::scoped_refptr<const RTCStatsReport> GetStatsReportWithSenderSelector(
      rtc::scoped_refptr<RtpSenderInternal> selector) {
    rtc::scoped_refptr<RTCStatsObtainer> callback = RTCStatsObtainer::Create();
    stats_collector_->GetStatsReport(selector, callback);
    return WaitForReport(callback);
  }

  rtc::scoped_refptr<const RTCStatsReport> GetStatsReportWithReceiverSelector(
      rtc::scoped_refptr<RtpReceiverInternal> selector) {
    rtc::scoped_refptr<RTCStatsObtainer> callback = RTCStatsObtainer::Create();
    stats_collector_->GetStatsReport(selector, callback);
    return WaitForReport(callback);
  }

  rtc::scoped_refptr<const RTCStatsReport> GetFreshStatsReport() {
    stats_collector_->ClearCachedStatsReport();
    return GetStatsReport();
  }

  rtc::scoped_refptr<MockRtpSenderInternal> SetupLocalTrackAndSender(
      cricket::MediaType media_type,
      const std::string& track_id,
      uint32_t ssrc,
      bool add_stream,
      int attachment_id) {
    rtc::scoped_refptr<MediaStream> local_stream;
    if (add_stream) {
      local_stream = MediaStream::Create("LocalStreamId");
      pc_->mutable_local_streams()->AddStream(local_stream);
    }

    rtc::scoped_refptr<MediaStreamTrackInterface> track;
    if (media_type == cricket::MEDIA_TYPE_AUDIO) {
      track = CreateFakeTrack(media_type, track_id,
                              MediaStreamTrackInterface::kLive);
      if (add_stream) {
        local_stream->AddTrack(rtc::scoped_refptr<AudioTrackInterface>(
            static_cast<AudioTrackInterface*>(track.get())));
      }
    } else {
      track = CreateFakeTrack(media_type, track_id,
                              MediaStreamTrackInterface::kLive);
      if (add_stream) {
        local_stream->AddTrack(rtc::scoped_refptr<VideoTrackInterface>(
            static_cast<VideoTrackInterface*>(track.get())));
      }
    }

    rtc::scoped_refptr<MockRtpSenderInternal> sender =
        CreateMockSender(media_type, track, ssrc, attachment_id, {});
    EXPECT_CALL(*sender, Stop());
    EXPECT_CALL(*sender, SetMediaChannel(_));
    pc_->AddSender(sender);
    return sender;
  }

  rtc::scoped_refptr<MockRtpReceiverInternal> SetupRemoteTrackAndReceiver(
      cricket::MediaType media_type,
      const std::string& track_id,
      const std::string& stream_id,
      uint32_t ssrc) {
    rtc::scoped_refptr<MediaStream> remote_stream =
        MediaStream::Create(stream_id);
    pc_->mutable_remote_streams()->AddStream(remote_stream);

    rtc::scoped_refptr<MediaStreamTrackInterface> track;
    if (media_type == cricket::MEDIA_TYPE_AUDIO) {
      track = CreateFakeTrack(media_type, track_id,
                              MediaStreamTrackInterface::kLive);
      remote_stream->AddTrack(rtc::scoped_refptr<AudioTrackInterface>(
          static_cast<AudioTrackInterface*>(track.get())));
    } else {
      track = CreateFakeTrack(media_type, track_id,
                              MediaStreamTrackInterface::kLive);
      remote_stream->AddTrack(rtc::scoped_refptr<VideoTrackInterface>(
          static_cast<VideoTrackInterface*>(track.get())));
    }

    rtc::scoped_refptr<MockRtpReceiverInternal> receiver =
        CreateMockReceiver(track, ssrc, 62);
    EXPECT_CALL(*receiver, streams())
        .WillRepeatedly(
            Return(std::vector<rtc::scoped_refptr<MediaStreamInterface>>(
                {remote_stream})));
    EXPECT_CALL(*receiver, SetMediaChannel(_)).WillRepeatedly(Return());
    pc_->AddReceiver(receiver);
    return receiver;
  }

  // Attaches tracks to peer connections by configuring RTP senders and RTP
  // receivers according to the tracks' pairings with
  // |[Voice/Video][Sender/Receiver]Info| and their SSRCs. Local tracks can be
  // associated with multiple |[Voice/Video]SenderInfo|s, remote tracks can only
  // be associated with one |[Voice/Video]ReceiverInfo|.
  // Senders get assigned attachment ID "ssrc + 10".
  void CreateMockRtpSendersReceiversAndChannels(
      std::initializer_list<
          std::pair<MediaStreamTrackInterface*, cricket::VoiceSenderInfo>>
          local_audio_track_info_pairs,
      std::initializer_list<
          std::pair<MediaStreamTrackInterface*, cricket::VoiceReceiverInfo>>
          remote_audio_track_info_pairs,
      std::initializer_list<
          std::pair<MediaStreamTrackInterface*, cricket::VideoSenderInfo>>
          local_video_track_info_pairs,
      std::initializer_list<
          std::pair<MediaStreamTrackInterface*, cricket::VideoReceiverInfo>>
          remote_video_track_info_pairs,
      std::vector<std::string> local_stream_ids,
      std::vector<rtc::scoped_refptr<MediaStreamInterface>> remote_streams) {
    cricket::VoiceMediaInfo voice_media_info;
    cricket::VideoMediaInfo video_media_info;

    // Local audio tracks and voice sender infos
    for (auto& pair : local_audio_track_info_pairs) {
      MediaStreamTrackInterface* local_audio_track = pair.first;
      const cricket::VoiceSenderInfo& voice_sender_info = pair.second;
      RTC_DCHECK_EQ(local_audio_track->kind(),
                    MediaStreamTrackInterface::kAudioKind);

      voice_media_info.senders.push_back(voice_sender_info);
      rtc::scoped_refptr<MockRtpSenderInternal> rtp_sender = CreateMockSender(
          cricket::MEDIA_TYPE_AUDIO,
          rtc::scoped_refptr<MediaStreamTrackInterface>(local_audio_track),
          voice_sender_info.local_stats[0].ssrc,
          voice_sender_info.local_stats[0].ssrc + 10, local_stream_ids);
      EXPECT_CALL(*rtp_sender, SetMediaChannel(_)).WillRepeatedly(Return());
      EXPECT_CALL(*rtp_sender, Stop());
      pc_->AddSender(rtp_sender);
    }

    // Remote audio tracks and voice receiver infos
    for (auto& pair : remote_audio_track_info_pairs) {
      MediaStreamTrackInterface* remote_audio_track = pair.first;
      const cricket::VoiceReceiverInfo& voice_receiver_info = pair.second;
      RTC_DCHECK_EQ(remote_audio_track->kind(),
                    MediaStreamTrackInterface::kAudioKind);

      voice_media_info.receivers.push_back(voice_receiver_info);
      rtc::scoped_refptr<MockRtpReceiverInternal> rtp_receiver =
          CreateMockReceiver(
              rtc::scoped_refptr<MediaStreamTrackInterface>(remote_audio_track),
              voice_receiver_info.local_stats[0].ssrc,
              voice_receiver_info.local_stats[0].ssrc + 10);
      EXPECT_CALL(*rtp_receiver, streams())
          .WillRepeatedly(Return(remote_streams));
      EXPECT_CALL(*rtp_receiver, SetMediaChannel(_)).WillRepeatedly(Return());
      pc_->AddReceiver(rtp_receiver);
    }

    // Local video tracks and video sender infos
    for (auto& pair : local_video_track_info_pairs) {
      MediaStreamTrackInterface* local_video_track = pair.first;
      const cricket::VideoSenderInfo& video_sender_info = pair.second;
      RTC_DCHECK_EQ(local_video_track->kind(),
                    MediaStreamTrackInterface::kVideoKind);

      video_media_info.senders.push_back(video_sender_info);
      video_media_info.aggregated_senders.push_back(video_sender_info);
      rtc::scoped_refptr<MockRtpSenderInternal> rtp_sender = CreateMockSender(
          cricket::MEDIA_TYPE_VIDEO,
          rtc::scoped_refptr<MediaStreamTrackInterface>(local_video_track),
          video_sender_info.local_stats[0].ssrc,
          video_sender_info.local_stats[0].ssrc + 10, local_stream_ids);
      EXPECT_CALL(*rtp_sender, SetMediaChannel(_)).WillRepeatedly(Return());
      EXPECT_CALL(*rtp_sender, Stop());
      pc_->AddSender(rtp_sender);
    }

    // Remote video tracks and video receiver infos
    for (auto& pair : remote_video_track_info_pairs) {
      MediaStreamTrackInterface* remote_video_track = pair.first;
      const cricket::VideoReceiverInfo& video_receiver_info = pair.second;
      RTC_DCHECK_EQ(remote_video_track->kind(),
                    MediaStreamTrackInterface::kVideoKind);

      video_media_info.receivers.push_back(video_receiver_info);
      rtc::scoped_refptr<MockRtpReceiverInternal> rtp_receiver =
          CreateMockReceiver(
              rtc::scoped_refptr<MediaStreamTrackInterface>(remote_video_track),
              video_receiver_info.local_stats[0].ssrc,
              video_receiver_info.local_stats[0].ssrc + 10);
      EXPECT_CALL(*rtp_receiver, streams())
          .WillRepeatedly(Return(remote_streams));
      EXPECT_CALL(*rtp_receiver, SetMediaChannel(_)).WillRepeatedly(Return());
      pc_->AddReceiver(rtp_receiver);
    }

    pc_->AddVoiceChannel("audio", "transport", voice_media_info);
    pc_->AddVideoChannel("video", "transport", video_media_info);
  }

 private:
  rtc::scoped_refptr<const RTCStatsReport> WaitForReport(
      rtc::scoped_refptr<RTCStatsObtainer> callback) {
    EXPECT_TRUE_WAIT(callback->report() != nullptr, kGetStatsReportTimeoutMs);
    int64_t after = rtc::TimeUTCMicros();
    for (const RTCStats& stats : *callback->report()) {
      if (stats.type() == RTCRemoteInboundRtpStreamStats::kType ||
          stats.type() == RTCRemoteOutboundRtpStreamStats::kType) {
        // Ignore remote timestamps.
        continue;
      }
      EXPECT_LE(stats.timestamp().us(), after);
    }
    return callback->report();
  }

  rtc::scoped_refptr<FakePeerConnectionForStats> pc_;
  rtc::scoped_refptr<RTCStatsCollector> stats_collector_;
};

class RTCStatsCollectorTest : public ::testing::Test {
 public:
  RTCStatsCollectorTest()
      : pc_(rtc::make_ref_counted<FakePeerConnectionForStats>()),
        stats_(new RTCStatsCollectorWrapper(pc_)),
        data_channel_controller_(
            new FakeDataChannelController(pc_->network_thread())) {}

  void ExpectReportContainsCertificateInfo(
      const rtc::scoped_refptr<const RTCStatsReport>& report,
      const CertificateInfo& certinfo) {
    for (size_t i = 0; i < certinfo.fingerprints.size(); ++i) {
      RTCCertificateStats expected_certificate_stats(
          "CF" + certinfo.fingerprints[i], report->timestamp());
      expected_certificate_stats.fingerprint = certinfo.fingerprints[i];
      expected_certificate_stats.fingerprint_algorithm = "sha-1";
      expected_certificate_stats.base64_certificate = certinfo.pems[i];
      if (i + 1 < certinfo.fingerprints.size()) {
        expected_certificate_stats.issuer_certificate_id =
            "CF" + certinfo.fingerprints[i + 1];
      }
      ASSERT_TRUE(report->Get(expected_certificate_stats.id()));
      EXPECT_EQ(expected_certificate_stats,
                report->Get(expected_certificate_stats.id())
                    ->cast_to<RTCCertificateStats>());
    }
  }

  const RTCCertificateStats* GetCertificateStatsFromFingerprint(
      const rtc::scoped_refptr<const RTCStatsReport>& report,
      const std::string& fingerprint) {
    auto certificates = report->GetStatsOfType<RTCCertificateStats>();
    for (const auto* certificate : certificates) {
      if (*certificate->fingerprint == fingerprint) {
        return certificate;
      }
    }
    return nullptr;
  }

  struct ExampleStatsGraph {
    rtc::scoped_refptr<RtpSenderInternal> sender;
    rtc::scoped_refptr<RtpReceiverInternal> receiver;

    rtc::scoped_refptr<const RTCStatsReport> full_report;
    std::string send_codec_id;
    std::string recv_codec_id;
    std::string outbound_rtp_id;
    std::string inbound_rtp_id;
    std::string remote_outbound_rtp_id;
    std::string transport_id;
    std::string peer_connection_id;
    std::string media_source_id;
  };

  // Sets up the example stats graph (see ASCII art below) for a video only
  // call. The graph is used for testing the stats selection algorithm (see
  // https://w3c.github.io/webrtc-pc/#dfn-stats-selection-algorithm).
  // These tests test the integration of the stats traversal algorithm inside of
  // RTCStatsCollector. See rtcstatstraveral_unittest.cc for more stats
  // traversal tests.
  ExampleStatsGraph SetupExampleStatsGraphForSelectorTests() {
    ExampleStatsGraph graph;

    // codec (send)
    graph.send_codec_id = "COTTransportName1_1";
    cricket::VideoMediaInfo video_media_info;
    RtpCodecParameters send_codec;
    send_codec.payload_type = 1;
    send_codec.clock_rate = 0;
    video_media_info.send_codecs.insert(
        std::make_pair(send_codec.payload_type, send_codec));
    // codec (recv)
    graph.recv_codec_id = "CITTransportName1_2";
    RtpCodecParameters recv_codec;
    recv_codec.payload_type = 2;
    recv_codec.clock_rate = 0;
    video_media_info.receive_codecs.insert(
        std::make_pair(recv_codec.payload_type, recv_codec));
    // outbound-rtp
    graph.outbound_rtp_id = "OTTransportName1V3";
    video_media_info.senders.push_back(cricket::VideoSenderInfo());
    video_media_info.senders[0].local_stats.push_back(
        cricket::SsrcSenderInfo());
    video_media_info.senders[0].local_stats[0].ssrc = 3;
    video_media_info.senders[0].codec_payload_type = send_codec.payload_type;
    video_media_info.aggregated_senders.push_back(video_media_info.senders[0]);
    // inbound-rtp
    graph.inbound_rtp_id = "ITTransportName1V4";
    video_media_info.receivers.push_back(cricket::VideoReceiverInfo());
    video_media_info.receivers[0].local_stats.push_back(
        cricket::SsrcReceiverInfo());
    video_media_info.receivers[0].local_stats[0].ssrc = 4;
    video_media_info.receivers[0].codec_payload_type = recv_codec.payload_type;
    // transport
    graph.transport_id = "TTransportName1";
    pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);
    // outbound-rtp's sender
    graph.sender = stats_->SetupLocalTrackAndSender(
        cricket::MEDIA_TYPE_VIDEO, "LocalVideoTrackID", 3, false, 50);
    // inbound-rtp's receiver
    graph.receiver = stats_->SetupRemoteTrackAndReceiver(
        cricket::MEDIA_TYPE_VIDEO, "RemoteVideoTrackID", "RemoteStreamId", 4);
    // peer-connection
    graph.peer_connection_id = "P";
    // media-source (kind: video)
    graph.media_source_id = "SV" + rtc::ToString(graph.sender->AttachmentId());

    // Expected stats graph:
    //
    //  media-source                     peer-connection
    //    ^
    //    |
    //    +--------- outbound-rtp   inbound-rtp
    //                |        |     |       |
    //                v        v     v       v
    //       codec (send)     transport     codec (recv)

    // Verify the stats graph is set up correctly.
    graph.full_report = stats_->GetStatsReport();
    EXPECT_EQ(graph.full_report->size(), 7u);
    EXPECT_TRUE(graph.full_report->Get(graph.send_codec_id));
    EXPECT_TRUE(graph.full_report->Get(graph.recv_codec_id));
    EXPECT_TRUE(graph.full_report->Get(graph.outbound_rtp_id));
    EXPECT_TRUE(graph.full_report->Get(graph.inbound_rtp_id));
    EXPECT_TRUE(graph.full_report->Get(graph.transport_id));
    EXPECT_TRUE(graph.full_report->Get(graph.peer_connection_id));
    EXPECT_TRUE(graph.full_report->Get(graph.media_source_id));
    const auto& outbound_rtp = graph.full_report->Get(graph.outbound_rtp_id)
                                   ->cast_to<RTCOutboundRtpStreamStats>();
    EXPECT_EQ(*outbound_rtp.media_source_id, graph.media_source_id);
    EXPECT_EQ(*outbound_rtp.codec_id, graph.send_codec_id);
    EXPECT_EQ(*outbound_rtp.transport_id, graph.transport_id);
    EXPECT_TRUE(graph.full_report->Get(graph.inbound_rtp_id));
    // We can't use an ASSERT in a function returning non-void, so just return.
    if (!graph.full_report->Get(graph.inbound_rtp_id)) {
      return graph;
    }
    const auto& inbound_rtp = graph.full_report->Get(graph.inbound_rtp_id)
                                  ->cast_to<RTCInboundRtpStreamStats>();
    EXPECT_EQ(*inbound_rtp.codec_id, graph.recv_codec_id);
    EXPECT_EQ(*inbound_rtp.transport_id, graph.transport_id);

    return graph;
  }

  // Sets up an example stats graph (see ASCII art below) for an audio only call
  // and checks that the expected stats are generated.
  ExampleStatsGraph SetupExampleStatsVoiceGraph(
      bool add_remote_outbound_stats) {
    constexpr uint32_t kLocalSsrc = 3;
    constexpr uint32_t kRemoteSsrc = 4;
    ExampleStatsGraph graph;

    // codec (send)
    graph.send_codec_id = "COTTransportName1_1";
    cricket::VoiceMediaInfo media_info;
    RtpCodecParameters send_codec;
    send_codec.payload_type = 1;
    send_codec.clock_rate = 0;
    media_info.send_codecs.insert(
        std::make_pair(send_codec.payload_type, send_codec));
    // codec (recv)
    graph.recv_codec_id = "CITTransportName1_2";
    RtpCodecParameters recv_codec;
    recv_codec.payload_type = 2;
    recv_codec.clock_rate = 0;
    media_info.receive_codecs.insert(
        std::make_pair(recv_codec.payload_type, recv_codec));
    // outbound-rtp
    graph.outbound_rtp_id = "OTTransportName1A3";
    media_info.senders.push_back(cricket::VoiceSenderInfo());
    media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
    media_info.senders[0].local_stats[0].ssrc = kLocalSsrc;
    media_info.senders[0].codec_payload_type = send_codec.payload_type;
    // inbound-rtp
    graph.inbound_rtp_id = "ITTransportName1A4";
    media_info.receivers.push_back(cricket::VoiceReceiverInfo());
    media_info.receivers[0].local_stats.push_back(cricket::SsrcReceiverInfo());
    media_info.receivers[0].local_stats[0].ssrc = kRemoteSsrc;
    media_info.receivers[0].codec_payload_type = recv_codec.payload_type;
    // remote-outbound-rtp
    if (add_remote_outbound_stats) {
      graph.remote_outbound_rtp_id = "ROA4";
      media_info.receivers[0].last_sender_report_timestamp_ms =
          kRemoteOutboundStatsTimestampMs;
      media_info.receivers[0].last_sender_report_remote_timestamp_ms =
          kRemoteOutboundStatsRemoteTimestampMs;
      media_info.receivers[0].sender_reports_packets_sent =
          kRemoteOutboundStatsPacketsSent;
      media_info.receivers[0].sender_reports_bytes_sent =
          kRemoteOutboundStatsBytesSent;
      media_info.receivers[0].sender_reports_reports_count =
          kRemoteOutboundStatsReportsCount;
    }
    // transport
    graph.transport_id = "TTransportName1";
    pc_->AddVoiceChannel("VoiceMid", "TransportName", media_info);
    // outbound-rtp's sender
    graph.sender = stats_->SetupLocalTrackAndSender(
        cricket::MEDIA_TYPE_AUDIO, "LocalAudioTrackID", kLocalSsrc, false, 50);
    // inbound-rtp's receiver
    graph.receiver = stats_->SetupRemoteTrackAndReceiver(
        cricket::MEDIA_TYPE_AUDIO, "RemoteAudioTrackID", "RemoteStreamId",
        kRemoteSsrc);
    // peer-connection
    graph.peer_connection_id = "P";
    // media-source (kind: video)
    graph.media_source_id = "SA" + rtc::ToString(graph.sender->AttachmentId());

    // Expected stats graph:
    //
    //  media-source                     peer-connection
    //    ^
    //    |
    //    +--------- outbound-rtp   inbound-rtp
    //                |        |     |       |
    //                v        v     v       v
    //       codec (send)     transport     codec (recv)

    // Verify the stats graph is set up correctly.
    graph.full_report = stats_->GetStatsReport();
    EXPECT_EQ(graph.full_report->size(), add_remote_outbound_stats ? 8u : 7u);
    EXPECT_TRUE(graph.full_report->Get(graph.send_codec_id));
    EXPECT_TRUE(graph.full_report->Get(graph.recv_codec_id));
    EXPECT_TRUE(graph.full_report->Get(graph.outbound_rtp_id));
    EXPECT_TRUE(graph.full_report->Get(graph.inbound_rtp_id));
    EXPECT_TRUE(graph.full_report->Get(graph.transport_id));
    EXPECT_TRUE(graph.full_report->Get(graph.peer_connection_id));
    EXPECT_TRUE(graph.full_report->Get(graph.media_source_id));
    // `graph.remote_outbound_rtp_id` is omitted on purpose so that expectations
    // can be added by the caller depending on what value it sets for the
    // `add_remote_outbound_stats` argument.
    const auto& outbound_rtp = graph.full_report->Get(graph.outbound_rtp_id)
                                   ->cast_to<RTCOutboundRtpStreamStats>();
    EXPECT_EQ(*outbound_rtp.media_source_id, graph.media_source_id);
    EXPECT_EQ(*outbound_rtp.codec_id, graph.send_codec_id);
    EXPECT_EQ(*outbound_rtp.transport_id, graph.transport_id);
    EXPECT_TRUE(graph.full_report->Get(graph.inbound_rtp_id));
    // We can't use ASSERT in a function with a return value.
    if (!graph.full_report->Get(graph.inbound_rtp_id)) {
      return graph;
    }
    const auto& inbound_rtp = graph.full_report->Get(graph.inbound_rtp_id)
                                  ->cast_to<RTCInboundRtpStreamStats>();
    EXPECT_EQ(*inbound_rtp.codec_id, graph.recv_codec_id);
    EXPECT_EQ(*inbound_rtp.transport_id, graph.transport_id);

    return graph;
  }

 protected:
  rtc::ScopedFakeClock fake_clock_;
  rtc::AutoThread main_thread_;
  rtc::scoped_refptr<FakePeerConnectionForStats> pc_;
  std::unique_ptr<RTCStatsCollectorWrapper> stats_;
  std::unique_ptr<FakeDataChannelController> data_channel_controller_;
};

TEST_F(RTCStatsCollectorTest, SingleCallback) {
  rtc::scoped_refptr<const RTCStatsReport> result;
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&result));
  EXPECT_TRUE_WAIT(result != nullptr, kGetStatsReportTimeoutMs);
}

TEST_F(RTCStatsCollectorTest, MultipleCallbacks) {
  rtc::scoped_refptr<const RTCStatsReport> a, b, c;
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&a));
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&b));
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&c));
  EXPECT_TRUE_WAIT(a != nullptr, kGetStatsReportTimeoutMs);
  EXPECT_TRUE_WAIT(b != nullptr, kGetStatsReportTimeoutMs);
  EXPECT_TRUE_WAIT(c != nullptr, kGetStatsReportTimeoutMs);

  EXPECT_EQ(a.get(), b.get());
  EXPECT_EQ(b.get(), c.get());
}

TEST_F(RTCStatsCollectorTest, CachedStatsReports) {
  // Caching should ensure `a` and `b` are the same report.
  rtc::scoped_refptr<const RTCStatsReport> a = stats_->GetStatsReport();
  rtc::scoped_refptr<const RTCStatsReport> b = stats_->GetStatsReport();
  EXPECT_EQ(a.get(), b.get());
  // Invalidate cache by clearing it.
  stats_->stats_collector()->ClearCachedStatsReport();
  rtc::scoped_refptr<const RTCStatsReport> c = stats_->GetStatsReport();
  EXPECT_NE(b.get(), c.get());
  // Invalidate cache by advancing time.
  fake_clock_.AdvanceTime(TimeDelta::Millis(51));
  rtc::scoped_refptr<const RTCStatsReport> d = stats_->GetStatsReport();
  EXPECT_TRUE(d);
  EXPECT_NE(c.get(), d.get());
}

TEST_F(RTCStatsCollectorTest, MultipleCallbacksWithInvalidatedCacheInBetween) {
  rtc::scoped_refptr<const RTCStatsReport> a, b, c;
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&a));
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&b));
  // Cache is invalidated after 50 ms.
  fake_clock_.AdvanceTime(TimeDelta::Millis(51));
  stats_->stats_collector()->GetStatsReport(RTCStatsObtainer::Create(&c));
  EXPECT_TRUE_WAIT(a != nullptr, kGetStatsReportTimeoutMs);
  EXPECT_TRUE_WAIT(b != nullptr, kGetStatsReportTimeoutMs);
  EXPECT_TRUE_WAIT(c != nullptr, kGetStatsReportTimeoutMs);
  EXPECT_EQ(a.get(), b.get());
  // The act of doing `AdvanceTime` processes all messages. If this was not the
  // case we might not require `c` to be fresher than `b`.
  EXPECT_NE(c.get(), b.get());
}

TEST_F(RTCStatsCollectorTest, ToJsonProducesParseableJson) {
  ExampleStatsGraph graph = SetupExampleStatsGraphForSelectorTests();
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  std::string json_format = report->ToJson();

  Json::CharReaderBuilder builder;
  Json::Value json_value;
  std::unique_ptr<Json::CharReader> reader(builder.newCharReader());
  ASSERT_TRUE(reader->parse(json_format.c_str(),
                            json_format.c_str() + json_format.size(),
                            &json_value, nullptr));

  // A very brief sanity check on the result.
  EXPECT_EQ(report->size(), json_value.size());
}

TEST_F(RTCStatsCollectorTest, CollectRTCCertificateStatsSingle) {
  const char kTransportName[] = "transport";

  pc_->AddVoiceChannel("audio", kTransportName);

  std::unique_ptr<CertificateInfo> local_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(local) single certificate"}));
  pc_->SetLocalCertificate(kTransportName, local_certinfo->certificate);

  std::unique_ptr<CertificateInfo> remote_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(remote) single certificate"}));
  pc_->SetRemoteCertChain(
      kTransportName,
      remote_certinfo->certificate->GetSSLCertificateChain().Clone());

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  ExpectReportContainsCertificateInfo(report, *local_certinfo);
  ExpectReportContainsCertificateInfo(report, *remote_certinfo);
}

// These SSRC collisions are legal.
TEST_F(RTCStatsCollectorTest, ValidSsrcCollisionDoesNotCrash) {
  // BUNDLE audio/video inbound/outbound. Unique SSRCs needed within the BUNDLE.
  cricket::VoiceMediaInfo mid1_info;
  mid1_info.receivers.emplace_back();
  mid1_info.receivers[0].add_ssrc(1);
  mid1_info.senders.emplace_back();
  mid1_info.senders[0].add_ssrc(2);
  pc_->AddVoiceChannel("Mid1", "Transport1", mid1_info);
  cricket::VideoMediaInfo mid2_info;
  mid2_info.receivers.emplace_back();
  mid2_info.receivers[0].add_ssrc(3);
  mid2_info.senders.emplace_back();
  mid2_info.senders[0].add_ssrc(4);
  pc_->AddVideoChannel("Mid2", "Transport1", mid2_info);
  // Now create a second BUNDLE group with SSRCs colliding with the first group
  // (but again no collisions within the group).
  cricket::VoiceMediaInfo mid3_info;
  mid3_info.receivers.emplace_back();
  mid3_info.receivers[0].add_ssrc(1);
  mid3_info.senders.emplace_back();
  mid3_info.senders[0].add_ssrc(2);
  pc_->AddVoiceChannel("Mid3", "Transport2", mid3_info);
  cricket::VideoMediaInfo mid4_info;
  mid4_info.receivers.emplace_back();
  mid4_info.receivers[0].add_ssrc(3);
  mid4_info.senders.emplace_back();
  mid4_info.senders[0].add_ssrc(4);
  pc_->AddVideoChannel("Mid4", "Transport2", mid4_info);

  // This should not crash (https://crbug.com/1361612).
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  auto inbound_rtps = report->GetStatsOfType<RTCInboundRtpStreamStats>();
  auto outbound_rtps = report->GetStatsOfType<RTCOutboundRtpStreamStats>();
  EXPECT_EQ(inbound_rtps.size(), 4u);
  EXPECT_EQ(outbound_rtps.size(), 4u);
}

// These SSRC collisions are illegal, so it is not clear if this setup can
// happen even when talking to a malicious endpoint, but simulate illegal SSRC
// collisions just to make sure we don't crash in even the most extreme cases.
TEST_F(RTCStatsCollectorTest, InvalidSsrcCollisionDoesNotCrash) {
  // One SSRC to rule them all.
  cricket::VoiceMediaInfo mid1_info;
  mid1_info.receivers.emplace_back();
  mid1_info.receivers[0].add_ssrc(1);
  mid1_info.senders.emplace_back();
  mid1_info.senders[0].add_ssrc(1);
  pc_->AddVoiceChannel("Mid1", "BundledTransport", mid1_info);
  cricket::VideoMediaInfo mid2_info;
  mid2_info.receivers.emplace_back();
  mid2_info.receivers[0].add_ssrc(1);
  mid2_info.senders.emplace_back();
  mid2_info.senders[0].add_ssrc(1);
  pc_->AddVideoChannel("Mid2", "BundledTransport", mid2_info);
  cricket::VoiceMediaInfo mid3_info;
  mid3_info.receivers.emplace_back();
  mid3_info.receivers[0].add_ssrc(1);
  mid3_info.senders.emplace_back();
  mid3_info.senders[0].add_ssrc(1);
  pc_->AddVoiceChannel("Mid3", "BundledTransport", mid3_info);
  cricket::VideoMediaInfo mid4_info;
  mid4_info.receivers.emplace_back();
  mid4_info.receivers[0].add_ssrc(1);
  mid4_info.senders.emplace_back();
  mid4_info.senders[0].add_ssrc(1);
  pc_->AddVideoChannel("Mid4", "BundledTransport", mid4_info);

  // This should not crash (https://crbug.com/1361612).
  stats_->GetStatsReport();
  // Because this setup is illegal, there is no "right answer" to how the report
  // should look. We only care about not crashing.
}

TEST_F(RTCStatsCollectorTest, CollectRTCCodecStatsOnlyIfReferenced) {
  // Audio
  cricket::VoiceMediaInfo voice_media_info;

  RtpCodecParameters inbound_audio_codec;
  inbound_audio_codec.payload_type = 1;
  inbound_audio_codec.kind = cricket::MEDIA_TYPE_AUDIO;
  inbound_audio_codec.name = "opus";
  inbound_audio_codec.clock_rate = 1337;
  inbound_audio_codec.num_channels = 1;
  inbound_audio_codec.parameters = {{"minptime", "10"}, {"useinbandfec", "1"}};
  voice_media_info.receive_codecs.insert(
      std::make_pair(inbound_audio_codec.payload_type, inbound_audio_codec));

  RtpCodecParameters outbound_audio_codec;
  outbound_audio_codec.payload_type = 2;
  outbound_audio_codec.kind = cricket::MEDIA_TYPE_AUDIO;
  outbound_audio_codec.name = "isac";
  outbound_audio_codec.clock_rate = 1338;
  outbound_audio_codec.num_channels = 2;
  voice_media_info.send_codecs.insert(
      std::make_pair(outbound_audio_codec.payload_type, outbound_audio_codec));

  // Video
  cricket::VideoMediaInfo video_media_info;

  RtpCodecParameters inbound_video_codec;
  inbound_video_codec.payload_type = 3;
  inbound_video_codec.kind = cricket::MEDIA_TYPE_VIDEO;
  inbound_video_codec.name = "H264";
  inbound_video_codec.clock_rate = 1339;
  inbound_video_codec.parameters = {{"level-asymmetry-allowed", "1"},
                                    {"packetization-mode", "1"},
                                    {"profile-level-id", "42001f"}};
  video_media_info.receive_codecs.insert(
      std::make_pair(inbound_video_codec.payload_type, inbound_video_codec));

  RtpCodecParameters outbound_video_codec;
  outbound_video_codec.payload_type = 4;
  outbound_video_codec.kind = cricket::MEDIA_TYPE_VIDEO;
  outbound_video_codec.name = "VP8";
  outbound_video_codec.clock_rate = 1340;
  video_media_info.send_codecs.insert(
      std::make_pair(outbound_video_codec.payload_type, outbound_video_codec));

  // Ensure the above codecs are referenced.
  cricket::VoiceReceiverInfo inbound_audio_info;
  inbound_audio_info.add_ssrc(10);
  inbound_audio_info.codec_payload_type = 1;
  voice_media_info.receivers.push_back(inbound_audio_info);

  cricket::VoiceSenderInfo outbound_audio_info;
  outbound_audio_info.add_ssrc(20);
  outbound_audio_info.codec_payload_type = 2;
  voice_media_info.senders.push_back(outbound_audio_info);

  cricket::VideoReceiverInfo inbound_video_info;
  inbound_video_info.add_ssrc(30);
  inbound_video_info.codec_payload_type = 3;
  video_media_info.receivers.push_back(inbound_video_info);

  cricket::VideoSenderInfo outbound_video_info;
  outbound_video_info.add_ssrc(40);
  outbound_video_info.codec_payload_type = 4;
  video_media_info.senders.push_back(outbound_video_info);

  auto audio_channels =
      pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  auto video_channels =
      pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCCodecStats expected_inbound_audio_codec(
      "CITTransportName1_1_minptime=10;useinbandfec=1", report->timestamp());
  expected_inbound_audio_codec.payload_type = 1;
  expected_inbound_audio_codec.mime_type = "audio/opus";
  expected_inbound_audio_codec.clock_rate = 1337;
  expected_inbound_audio_codec.channels = 1;
  expected_inbound_audio_codec.sdp_fmtp_line = "minptime=10;useinbandfec=1";
  expected_inbound_audio_codec.transport_id = "TTransportName1";

  RTCCodecStats expected_outbound_audio_codec("COTTransportName1_2",
                                              report->timestamp());
  expected_outbound_audio_codec.payload_type = 2;
  expected_outbound_audio_codec.mime_type = "audio/isac";
  expected_outbound_audio_codec.clock_rate = 1338;
  expected_outbound_audio_codec.channels = 2;
  expected_outbound_audio_codec.transport_id = "TTransportName1";

  RTCCodecStats expected_inbound_video_codec(
      "CITTransportName1_3_level-asymmetry-allowed=1;"
      "packetization-mode=1;profile-level-id=42001f",
      report->timestamp());
  expected_inbound_video_codec.payload_type = 3;
  expected_inbound_video_codec.mime_type = "video/H264";
  expected_inbound_video_codec.clock_rate = 1339;
  expected_inbound_video_codec.sdp_fmtp_line =
      "level-asymmetry-allowed=1;packetization-mode=1;profile-level-id=42001f";
  expected_inbound_video_codec.transport_id = "TTransportName1";

  RTCCodecStats expected_outbound_video_codec("COTTransportName1_4",
                                              report->timestamp());
  expected_outbound_video_codec.payload_type = 4;
  expected_outbound_video_codec.mime_type = "video/VP8";
  expected_outbound_video_codec.clock_rate = 1340;
  expected_outbound_video_codec.transport_id = "TTransportName1";

  ASSERT_TRUE(report->Get(expected_inbound_audio_codec.id()));
  EXPECT_EQ(
      expected_inbound_audio_codec,
      report->Get(expected_inbound_audio_codec.id())->cast_to<RTCCodecStats>());

  ASSERT_TRUE(report->Get(expected_outbound_audio_codec.id()));
  EXPECT_EQ(expected_outbound_audio_codec,
            report->Get(expected_outbound_audio_codec.id())
                ->cast_to<RTCCodecStats>());

  ASSERT_TRUE(report->Get(expected_inbound_video_codec.id()));
  EXPECT_EQ(
      expected_inbound_video_codec,
      report->Get(expected_inbound_video_codec.id())->cast_to<RTCCodecStats>());

  ASSERT_TRUE(report->Get(expected_outbound_video_codec.id()));
  EXPECT_EQ(expected_outbound_video_codec,
            report->Get(expected_outbound_video_codec.id())
                ->cast_to<RTCCodecStats>());

  // Now remove all the RTP streams such that there are no live codecId
  // references to the codecs, this should result in none of the RTCCodecStats
  // being exposed, despite `send_codecs` and `receive_codecs` still being set.
  voice_media_info.senders.clear();
  voice_media_info.receivers.clear();
  audio_channels.first->SetStats(voice_media_info);
  audio_channels.second->SetStats(voice_media_info);
  video_media_info.senders.clear();
  video_media_info.receivers.clear();
  video_channels.first->SetStats(video_media_info);
  video_channels.second->SetStats(video_media_info);
  stats_->stats_collector()->ClearCachedStatsReport();
  report = stats_->GetStatsReport();
  EXPECT_FALSE(report->Get(expected_inbound_audio_codec.id()));
  EXPECT_FALSE(report->Get(expected_outbound_audio_codec.id()));
  EXPECT_FALSE(report->Get(expected_inbound_video_codec.id()));
  EXPECT_FALSE(report->Get(expected_outbound_video_codec.id()));
}

TEST_F(RTCStatsCollectorTest, CodecStatsAreCollectedPerTransport) {
  // PT=10
  RtpCodecParameters outbound_codec_pt10;
  outbound_codec_pt10.payload_type = 10;
  outbound_codec_pt10.kind = cricket::MEDIA_TYPE_VIDEO;
  outbound_codec_pt10.name = "VP8";
  outbound_codec_pt10.clock_rate = 9000;

  // PT=11
  RtpCodecParameters outbound_codec_pt11;
  outbound_codec_pt11.payload_type = 11;
  outbound_codec_pt11.kind = cricket::MEDIA_TYPE_VIDEO;
  outbound_codec_pt11.name = "VP8";
  outbound_codec_pt11.clock_rate = 9000;

  // Insert codecs into `send_codecs` and ensure the PTs are referenced by RTP
  // streams.
  cricket::VideoMediaInfo info_pt10;
  info_pt10.send_codecs.insert(
      std::make_pair(outbound_codec_pt10.payload_type, outbound_codec_pt10));
  info_pt10.senders.emplace_back();
  info_pt10.senders[0].add_ssrc(42);
  info_pt10.senders[0].codec_payload_type = outbound_codec_pt10.payload_type;

  cricket::VideoMediaInfo info_pt11;
  info_pt11.send_codecs.insert(
      std::make_pair(outbound_codec_pt11.payload_type, outbound_codec_pt11));
  info_pt11.senders.emplace_back();
  info_pt11.senders[0].add_ssrc(43);
  info_pt11.senders[0].codec_payload_type = outbound_codec_pt11.payload_type;

  cricket::VideoMediaInfo info_pt10_pt11;
  info_pt10_pt11.send_codecs.insert(
      std::make_pair(outbound_codec_pt10.payload_type, outbound_codec_pt10));
  info_pt10_pt11.send_codecs.insert(
      std::make_pair(outbound_codec_pt11.payload_type, outbound_codec_pt11));
  info_pt10_pt11.senders.emplace_back();
  info_pt10_pt11.senders[0].add_ssrc(44);
  info_pt10_pt11.senders[0].codec_payload_type =
      outbound_codec_pt10.payload_type;
  info_pt10_pt11.senders.emplace_back();
  info_pt10_pt11.senders[1].add_ssrc(45);
  info_pt10_pt11.senders[1].codec_payload_type =
      outbound_codec_pt11.payload_type;

  // First two mids contain subsets, the third one contains all PTs.
  pc_->AddVideoChannel("Mid1", "FirstTransport", info_pt10);
  pc_->AddVideoChannel("Mid2", "FirstTransport", info_pt11);
  pc_->AddVideoChannel("Mid3", "FirstTransport", info_pt10_pt11);

  // There should be no duplicate codecs because all codec references are on the
  // same transport.
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  auto codec_stats = report->GetStatsOfType<RTCCodecStats>();
  EXPECT_EQ(codec_stats.size(), 2u);

  // If a second transport is added with the same PT information, this does
  // count as different codec objects.
  pc_->AddVideoChannel("Mid4", "SecondTransport", info_pt10_pt11);
  stats_->stats_collector()->ClearCachedStatsReport();
  report = stats_->GetStatsReport();
  codec_stats = report->GetStatsOfType<RTCCodecStats>();
  EXPECT_EQ(codec_stats.size(), 4u);
}

TEST_F(RTCStatsCollectorTest, SamePayloadTypeButDifferentFmtpLines) {
  // PT=111, useinbandfec=0
  RtpCodecParameters inbound_codec_pt111_nofec;
  inbound_codec_pt111_nofec.payload_type = 111;
  inbound_codec_pt111_nofec.kind = cricket::MEDIA_TYPE_AUDIO;
  inbound_codec_pt111_nofec.name = "opus";
  inbound_codec_pt111_nofec.clock_rate = 48000;
  inbound_codec_pt111_nofec.parameters.insert(
      std::make_pair("useinbandfec", "0"));

  // PT=111, useinbandfec=1
  RtpCodecParameters inbound_codec_pt111_fec;
  inbound_codec_pt111_fec.payload_type = 111;
  inbound_codec_pt111_fec.kind = cricket::MEDIA_TYPE_AUDIO;
  inbound_codec_pt111_fec.name = "opus";
  inbound_codec_pt111_fec.clock_rate = 48000;
  inbound_codec_pt111_fec.parameters.insert(
      std::make_pair("useinbandfec", "1"));

  cricket::VideoMediaInfo info_nofec;
  info_nofec.receive_codecs.insert(std::make_pair(
      inbound_codec_pt111_nofec.payload_type, inbound_codec_pt111_nofec));
  info_nofec.receivers.emplace_back();
  info_nofec.receivers[0].add_ssrc(123);
  info_nofec.receivers[0].codec_payload_type =
      inbound_codec_pt111_nofec.payload_type;
  cricket::VideoMediaInfo info_fec;
  info_fec.receive_codecs.insert(std::make_pair(
      inbound_codec_pt111_fec.payload_type, inbound_codec_pt111_fec));
  info_fec.receivers.emplace_back();
  info_fec.receivers[0].add_ssrc(321);
  info_fec.receivers[0].codec_payload_type =
      inbound_codec_pt111_fec.payload_type;

  // First two mids contain subsets, the third one contains all PTs.
  pc_->AddVideoChannel("Mid1", "BundledTransport", info_nofec);
  pc_->AddVideoChannel("Mid2", "BundledTransport", info_fec);

  // Despite having the same PT we should see two codec stats because their FMTP
  // lines are different.
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  auto codec_stats = report->GetStatsOfType<RTCCodecStats>();
  EXPECT_EQ(codec_stats.size(), 2u);

  // Ensure SSRC uniqueness before the next AddVideoChannel() call. SSRCs need
  // to be unique on different m= sections when using BUNDLE.
  info_nofec.receivers[0].local_stats[0].ssrc = 12;
  info_fec.receivers[0].local_stats[0].ssrc = 21;
  // Adding more m= sections that does have the same FMTP lines does not result
  // in duplicates.
  pc_->AddVideoChannel("Mid3", "BundledTransport", info_nofec);
  pc_->AddVideoChannel("Mid4", "BundledTransport", info_fec);
  stats_->stats_collector()->ClearCachedStatsReport();
  report = stats_->GetStatsReport();
  codec_stats = report->GetStatsOfType<RTCCodecStats>();
  EXPECT_EQ(codec_stats.size(), 2u);

  // Same FMTP line but a different PT does count as a new codec.
  // PT=112, useinbandfec=1
  RtpCodecParameters inbound_codec_pt112_fec;
  inbound_codec_pt112_fec.payload_type = 112;
  inbound_codec_pt112_fec.kind = cricket::MEDIA_TYPE_AUDIO;
  inbound_codec_pt112_fec.name = "opus";
  inbound_codec_pt112_fec.clock_rate = 48000;
  inbound_codec_pt112_fec.parameters.insert(
      std::make_pair("useinbandfec", "1"));
  cricket::VideoMediaInfo info_fec_pt112;
  info_fec_pt112.receive_codecs.insert(std::make_pair(
      inbound_codec_pt112_fec.payload_type, inbound_codec_pt112_fec));
  info_fec_pt112.receivers.emplace_back();
  info_fec_pt112.receivers[0].add_ssrc(112);
  info_fec_pt112.receivers[0].codec_payload_type =
      inbound_codec_pt112_fec.payload_type;
  pc_->AddVideoChannel("Mid5", "BundledTransport", info_fec_pt112);
  stats_->stats_collector()->ClearCachedStatsReport();
  report = stats_->GetStatsReport();
  codec_stats = report->GetStatsOfType<RTCCodecStats>();
  EXPECT_EQ(codec_stats.size(), 3u);
}

TEST_F(RTCStatsCollectorTest, CollectRTCCertificateStatsMultiple) {
  const char kAudioTransport[] = "audio";
  const char kVideoTransport[] = "video";

  pc_->AddVoiceChannel("audio", kAudioTransport);

  std::unique_ptr<CertificateInfo> audio_local_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(local) audio"}));
  pc_->SetLocalCertificate(kAudioTransport, audio_local_certinfo->certificate);
  std::unique_ptr<CertificateInfo> audio_remote_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(remote) audio"}));
  pc_->SetRemoteCertChain(
      kAudioTransport,
      audio_remote_certinfo->certificate->GetSSLCertificateChain().Clone());

  pc_->AddVideoChannel("video", kVideoTransport);
  std::unique_ptr<CertificateInfo> video_local_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(local) video"}));
  pc_->SetLocalCertificate(kVideoTransport, video_local_certinfo->certificate);
  std::unique_ptr<CertificateInfo> video_remote_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          std::vector<std::string>({"(remote) video"}));
  pc_->SetRemoteCertChain(
      kVideoTransport,
      video_remote_certinfo->certificate->GetSSLCertificateChain().Clone());

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  ExpectReportContainsCertificateInfo(report, *audio_local_certinfo);
  ExpectReportContainsCertificateInfo(report, *audio_remote_certinfo);
  ExpectReportContainsCertificateInfo(report, *video_local_certinfo);
  ExpectReportContainsCertificateInfo(report, *video_remote_certinfo);
}

TEST_F(RTCStatsCollectorTest, CollectRTCCertificateStatsChain) {
  const char kTransportName[] = "transport";

  pc_->AddVoiceChannel("audio", kTransportName);

  std::unique_ptr<CertificateInfo> local_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          {"(local) this", "(local) is", "(local) a", "(local) chain"});
  pc_->SetLocalCertificate(kTransportName, local_certinfo->certificate);

  std::unique_ptr<CertificateInfo> remote_certinfo =
      CreateFakeCertificateAndInfoFromDers({"(remote) this", "(remote) is",
                                            "(remote) another",
                                            "(remote) chain"});
  pc_->SetRemoteCertChain(
      kTransportName,
      remote_certinfo->certificate->GetSSLCertificateChain().Clone());

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  ExpectReportContainsCertificateInfo(report, *local_certinfo);
  ExpectReportContainsCertificateInfo(report, *remote_certinfo);
}

TEST_F(RTCStatsCollectorTest, CertificateStatsCache) {
  const char kTransportName[] = "transport";
  rtc::ScopedFakeClock fake_clock;

  pc_->AddVoiceChannel("audio", kTransportName);

  // Set local and remote cerificates.
  std::unique_ptr<CertificateInfo> initial_local_certinfo =
      CreateFakeCertificateAndInfoFromDers({"LocalCertA", "LocalCertB"});
  pc_->SetLocalCertificate(kTransportName, initial_local_certinfo->certificate);
  std::unique_ptr<CertificateInfo> initial_remote_certinfo =
      CreateFakeCertificateAndInfoFromDers({"RemoteCertA", "RemoteCertB"});
  pc_->SetRemoteCertChain(
      kTransportName,
      initial_remote_certinfo->certificate->GetSSLCertificateChain().Clone());
  ASSERT_EQ(initial_local_certinfo->fingerprints.size(), 2u);
  ASSERT_EQ(initial_remote_certinfo->fingerprints.size(), 2u);

  rtc::scoped_refptr<const RTCStatsReport> first_report =
      stats_->GetStatsReport();
  const auto* first_local_cert0 = GetCertificateStatsFromFingerprint(
      first_report, initial_local_certinfo->fingerprints[0]);
  const auto* first_local_cert1 = GetCertificateStatsFromFingerprint(
      first_report, initial_local_certinfo->fingerprints[1]);
  const auto* first_remote_cert0 = GetCertificateStatsFromFingerprint(
      first_report, initial_remote_certinfo->fingerprints[0]);
  const auto* first_remote_cert1 = GetCertificateStatsFromFingerprint(
      first_report, initial_remote_certinfo->fingerprints[1]);
  ASSERT_TRUE(first_local_cert0);
  ASSERT_TRUE(first_local_cert1);
  ASSERT_TRUE(first_remote_cert0);
  ASSERT_TRUE(first_remote_cert1);
  EXPECT_EQ(first_local_cert0->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(first_local_cert1->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(first_remote_cert0->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(first_remote_cert1->timestamp().us(), rtc::TimeMicros());

  // Replace all certificates.
  std::unique_ptr<CertificateInfo> updated_local_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          {"UpdatedLocalCertA", "UpdatedLocalCertB"});
  pc_->SetLocalCertificate(kTransportName, updated_local_certinfo->certificate);
  std::unique_ptr<CertificateInfo> updated_remote_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          {"UpdatedRemoteCertA", "UpdatedRemoteCertB"});
  pc_->SetRemoteCertChain(
      kTransportName,
      updated_remote_certinfo->certificate->GetSSLCertificateChain().Clone());
  // This test assumes fingerprints are different for the old and new
  // certificates.
  EXPECT_NE(initial_local_certinfo->fingerprints,
            updated_local_certinfo->fingerprints);
  EXPECT_NE(initial_remote_certinfo->fingerprints,
            updated_remote_certinfo->fingerprints);

  // Advance time to ensure a fresh stats report, but don't clear the
  // certificate stats cache.
  fake_clock.AdvanceTime(TimeDelta::Seconds(1));
  rtc::scoped_refptr<const RTCStatsReport> second_report =
      stats_->GetStatsReport();
  // We expect to see the same certificates as before due to not clearing the
  // certificate cache.
  const auto* second_local_cert0 =
      second_report->GetAs<RTCCertificateStats>(first_local_cert0->id());
  const auto* second_local_cert1 =
      second_report->GetAs<RTCCertificateStats>(first_local_cert1->id());
  const auto* second_remote_cert0 =
      second_report->GetAs<RTCCertificateStats>(first_remote_cert0->id());
  const auto* second_remote_cert1 =
      second_report->GetAs<RTCCertificateStats>(first_remote_cert1->id());
  ASSERT_TRUE(second_local_cert0);
  ASSERT_TRUE(second_local_cert1);
  ASSERT_TRUE(second_remote_cert0);
  ASSERT_TRUE(second_remote_cert1);
  // The information in the certificate stats are obsolete.
  EXPECT_EQ(*second_local_cert0->fingerprint,
            initial_local_certinfo->fingerprints[0]);
  EXPECT_EQ(*second_local_cert1->fingerprint,
            initial_local_certinfo->fingerprints[1]);
  EXPECT_EQ(*second_remote_cert0->fingerprint,
            initial_remote_certinfo->fingerprints[0]);
  EXPECT_EQ(*second_remote_cert1->fingerprint,
            initial_remote_certinfo->fingerprints[1]);
  // But timestamps are up-to-date, because this is a fresh stats report.
  EXPECT_EQ(second_local_cert0->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(second_local_cert1->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(second_remote_cert0->timestamp().us(), rtc::TimeMicros());
  EXPECT_EQ(second_remote_cert1->timestamp().us(), rtc::TimeMicros());
  // The updated certificates are not part of the report yet.
  EXPECT_FALSE(GetCertificateStatsFromFingerprint(
      second_report, updated_local_certinfo->fingerprints[0]));
  EXPECT_FALSE(GetCertificateStatsFromFingerprint(
      second_report, updated_local_certinfo->fingerprints[1]));
  EXPECT_FALSE(GetCertificateStatsFromFingerprint(
      second_report, updated_remote_certinfo->fingerprints[0]));
  EXPECT_FALSE(GetCertificateStatsFromFingerprint(
      second_report, updated_remote_certinfo->fingerprints[1]));

  // Clear the cache, including the cached certificates.
  stats_->stats_collector()->ClearCachedStatsReport();
  rtc::scoped_refptr<const RTCStatsReport> third_report =
      stats_->GetStatsReport();
  // Now the old certificates stats should be deleted.
  EXPECT_FALSE(third_report->Get(first_local_cert0->id()));
  EXPECT_FALSE(third_report->Get(first_local_cert1->id()));
  EXPECT_FALSE(third_report->Get(first_remote_cert0->id()));
  EXPECT_FALSE(third_report->Get(first_remote_cert1->id()));
  // And updated certificates exist.
  EXPECT_TRUE(GetCertificateStatsFromFingerprint(
      third_report, updated_local_certinfo->fingerprints[0]));
  EXPECT_TRUE(GetCertificateStatsFromFingerprint(
      third_report, updated_local_certinfo->fingerprints[1]));
  EXPECT_TRUE(GetCertificateStatsFromFingerprint(
      third_report, updated_remote_certinfo->fingerprints[0]));
  EXPECT_TRUE(GetCertificateStatsFromFingerprint(
      third_report, updated_remote_certinfo->fingerprints[1]));
}

TEST_F(RTCStatsCollectorTest, CollectTwoRTCDataChannelStatsWithPendingId) {
  // Note: The test assumes data channel IDs are predictable.
  // This is not a safe assumption, but in order to make it work for
  // the test, we reset the ID allocator at test start.
  SctpDataChannel::ResetInternalIdAllocatorForTesting(-1);
  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), /*id=*/-1,
      DataChannelInterface::kConnecting));
  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), /*id=*/-1,
      DataChannelInterface::kConnecting));

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  RTCDataChannelStats expected_data_channel0("D0", Timestamp::Zero());
  // Default values from MockDataChannel.
  expected_data_channel0.label = "MockSctpDataChannel";
  expected_data_channel0.protocol = "someProtocol";
  expected_data_channel0.state = "connecting";
  expected_data_channel0.messages_sent = 0;
  expected_data_channel0.bytes_sent = 0;
  expected_data_channel0.messages_received = 0;
  expected_data_channel0.bytes_received = 0;

  ASSERT_TRUE(report->Get(expected_data_channel0.id()));
  EXPECT_EQ(
      expected_data_channel0,
      report->Get(expected_data_channel0.id())->cast_to<RTCDataChannelStats>());
}

TEST_F(RTCStatsCollectorTest, CollectRTCDataChannelStats) {
  // Note: The test assumes data channel IDs are predictable.
  // This is not a safe assumption, but in order to make it work for
  // the test, we reset the ID allocator at test start.
  SctpDataChannel::ResetInternalIdAllocatorForTesting(-1);
  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), 0, "MockSctpDataChannel0",
      DataChannelInterface::kConnecting, "proto1", 1, 2, 3, 4));
  RTCDataChannelStats expected_data_channel0("D0", Timestamp::Zero());
  expected_data_channel0.label = "MockSctpDataChannel0";
  expected_data_channel0.protocol = "proto1";
  expected_data_channel0.data_channel_identifier = 0;
  expected_data_channel0.state = "connecting";
  expected_data_channel0.messages_sent = 1;
  expected_data_channel0.bytes_sent = 2;
  expected_data_channel0.messages_received = 3;
  expected_data_channel0.bytes_received = 4;

  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), 1, "MockSctpDataChannel1",
      DataChannelInterface::kOpen, "proto2", 5, 6, 7, 8));
  RTCDataChannelStats expected_data_channel1("D1", Timestamp::Zero());
  expected_data_channel1.label = "MockSctpDataChannel1";
  expected_data_channel1.protocol = "proto2";
  expected_data_channel1.data_channel_identifier = 1;
  expected_data_channel1.state = "open";
  expected_data_channel1.messages_sent = 5;
  expected_data_channel1.bytes_sent = 6;
  expected_data_channel1.messages_received = 7;
  expected_data_channel1.bytes_received = 8;

  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), 2, "MockSctpDataChannel2",
      DataChannelInterface::kClosing, "proto1", 9, 10, 11, 12));
  RTCDataChannelStats expected_data_channel2("D2", Timestamp::Zero());
  expected_data_channel2.label = "MockSctpDataChannel2";
  expected_data_channel2.protocol = "proto1";
  expected_data_channel2.data_channel_identifier = 2;
  expected_data_channel2.state = "closing";
  expected_data_channel2.messages_sent = 9;
  expected_data_channel2.bytes_sent = 10;
  expected_data_channel2.messages_received = 11;
  expected_data_channel2.bytes_received = 12;

  pc_->AddSctpDataChannel(rtc::make_ref_counted<MockSctpDataChannel>(
      data_channel_controller_->weak_ptr(), 3, "MockSctpDataChannel3",
      DataChannelInterface::kClosed, "proto3", 13, 14, 15, 16));
  RTCDataChannelStats expected_data_channel3("D3", Timestamp::Zero());
  expected_data_channel3.label = "MockSctpDataChannel3";
  expected_data_channel3.protocol = "proto3";
  expected_data_channel3.data_channel_identifier = 3;
  expected_data_channel3.state = "closed";
  expected_data_channel3.messages_sent = 13;
  expected_data_channel3.bytes_sent = 14;
  expected_data_channel3.messages_received = 15;
  expected_data_channel3.bytes_received = 16;

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  ASSERT_TRUE(report->Get(expected_data_channel0.id()));
  EXPECT_EQ(
      expected_data_channel0,
      report->Get(expected_data_channel0.id())->cast_to<RTCDataChannelStats>());
  ASSERT_TRUE(report->Get(expected_data_channel1.id()));
  EXPECT_EQ(
      expected_data_channel1,
      report->Get(expected_data_channel1.id())->cast_to<RTCDataChannelStats>());
  ASSERT_TRUE(report->Get(expected_data_channel2.id()));
  EXPECT_EQ(
      expected_data_channel2,
      report->Get(expected_data_channel2.id())->cast_to<RTCDataChannelStats>());
  ASSERT_TRUE(report->Get(expected_data_channel3.id()));
  EXPECT_EQ(
      expected_data_channel3,
      report->Get(expected_data_channel3.id())->cast_to<RTCDataChannelStats>());
}

TEST_F(RTCStatsCollectorTest, CollectRTCIceCandidateStats) {
  // Candidates in the first transport stats.
  std::unique_ptr<cricket::Candidate> a_local_host = CreateFakeCandidate(
      "1.2.3.4", 5, "a_local_host's protocol", rtc::ADAPTER_TYPE_VPN,
      cricket::LOCAL_PORT_TYPE, 0, rtc::ADAPTER_TYPE_ETHERNET);
  RTCLocalIceCandidateStats expected_a_local_host("I" + a_local_host->id(),
                                                  Timestamp::Zero());
  expected_a_local_host.transport_id = "Ta0";
  expected_a_local_host.network_type = "vpn";
  expected_a_local_host.ip = "1.2.3.4";
  expected_a_local_host.address = "1.2.3.4";
  expected_a_local_host.port = 5;
  expected_a_local_host.protocol = "a_local_host's protocol";
  expected_a_local_host.candidate_type = "host";
  expected_a_local_host.priority = 0;
  expected_a_local_host.vpn = true;
  expected_a_local_host.network_adapter_type = "ethernet";
  expected_a_local_host.foundation = "foundationIsAString";
  expected_a_local_host.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> a_remote_srflx = CreateFakeCandidate(
      "6.7.8.9", 10, "remote_srflx's protocol", rtc::ADAPTER_TYPE_UNKNOWN,
      cricket::STUN_PORT_TYPE, 1);
  RTCRemoteIceCandidateStats expected_a_remote_srflx("I" + a_remote_srflx->id(),
                                                     Timestamp::Zero());
  expected_a_remote_srflx.transport_id = "Ta0";
  expected_a_remote_srflx.ip = "6.7.8.9";
  expected_a_remote_srflx.address = "6.7.8.9";
  expected_a_remote_srflx.port = 10;
  expected_a_remote_srflx.protocol = "remote_srflx's protocol";
  expected_a_remote_srflx.candidate_type = "srflx";
  expected_a_remote_srflx.priority = 1;
  expected_a_remote_srflx.foundation = "foundationIsAString";
  expected_a_remote_srflx.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> a_local_prflx = CreateFakeCandidate(
      "11.12.13.14", 15, "a_local_prflx's protocol",
      rtc::ADAPTER_TYPE_CELLULAR_2G, cricket::PRFLX_PORT_TYPE, 2);
  RTCLocalIceCandidateStats expected_a_local_prflx("I" + a_local_prflx->id(),
                                                   Timestamp::Zero());
  expected_a_local_prflx.transport_id = "Ta0";
  expected_a_local_prflx.network_type = "cellular";
  expected_a_local_prflx.ip = "11.12.13.14";
  expected_a_local_prflx.address = "11.12.13.14";
  expected_a_local_prflx.port = 15;
  expected_a_local_prflx.protocol = "a_local_prflx's protocol";
  expected_a_local_prflx.candidate_type = "prflx";
  expected_a_local_prflx.priority = 2;
  expected_a_local_prflx.vpn = false;
  expected_a_local_prflx.network_adapter_type = "cellular2g";
  expected_a_local_prflx.foundation = "foundationIsAString";
  expected_a_local_prflx.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> a_remote_relay = CreateFakeCandidate(
      "16.17.18.19", 20, "a_remote_relay's protocol", rtc::ADAPTER_TYPE_UNKNOWN,
      cricket::RELAY_PORT_TYPE, 3);
  RTCRemoteIceCandidateStats expected_a_remote_relay("I" + a_remote_relay->id(),
                                                     Timestamp::Zero());
  expected_a_remote_relay.transport_id = "Ta0";
  expected_a_remote_relay.ip = "16.17.18.19";
  expected_a_remote_relay.address = "16.17.18.19";
  expected_a_remote_relay.port = 20;
  expected_a_remote_relay.protocol = "a_remote_relay's protocol";
  expected_a_remote_relay.candidate_type = "relay";
  expected_a_remote_relay.priority = 3;
  expected_a_remote_relay.foundation = "foundationIsAString";
  expected_a_remote_relay.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> a_local_relay = CreateFakeCandidate(
      "16.17.18.19", 21, "a_local_relay's protocol", rtc::ADAPTER_TYPE_UNKNOWN,
      cricket::RELAY_PORT_TYPE, 1);
  a_local_relay->set_relay_protocol("tcp");
  a_local_relay->set_url("turn:url1");

  RTCLocalIceCandidateStats expected_a_local_relay("I" + a_local_relay->id(),
                                                   Timestamp::Zero());
  expected_a_local_relay.transport_id = "Ta0";
  expected_a_local_relay.network_type = "unknown";
  expected_a_local_relay.ip = "16.17.18.19";
  expected_a_local_relay.address = "16.17.18.19";
  expected_a_local_relay.port = 21;
  expected_a_local_relay.protocol = "a_local_relay's protocol";
  expected_a_local_relay.relay_protocol = "tcp";
  expected_a_local_relay.candidate_type = "relay";
  expected_a_local_relay.priority = 1;
  expected_a_local_relay.url = "turn:url1";
  expected_a_local_relay.vpn = false;
  expected_a_local_relay.network_adapter_type = "unknown";
  expected_a_local_relay.foundation = "foundationIsAString";
  expected_a_local_relay.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> a_local_relay_prflx = CreateFakeCandidate(
      "11.12.13.20", 22, "a_local_relay_prflx's protocol",
      rtc::ADAPTER_TYPE_UNKNOWN, cricket::PRFLX_PORT_TYPE, 1);
  a_local_relay_prflx->set_relay_protocol("udp");

  RTCLocalIceCandidateStats expected_a_local_relay_prflx(
      "I" + a_local_relay_prflx->id(), Timestamp::Zero());
  expected_a_local_relay_prflx.transport_id = "Ta0";
  expected_a_local_relay_prflx.network_type = "unknown";
  expected_a_local_relay_prflx.ip = "11.12.13.20";
  expected_a_local_relay_prflx.address = "11.12.13.20";
  expected_a_local_relay_prflx.port = 22;
  expected_a_local_relay_prflx.protocol = "a_local_relay_prflx's protocol";
  expected_a_local_relay_prflx.relay_protocol = "udp";
  expected_a_local_relay_prflx.candidate_type = "prflx";
  expected_a_local_relay_prflx.priority = 1;
  expected_a_local_relay_prflx.vpn = false;
  expected_a_local_relay_prflx.network_adapter_type = "unknown";
  expected_a_local_relay_prflx.foundation = "foundationIsAString";
  expected_a_local_relay_prflx.username_fragment = "iceusernamefragment";

  // A non-paired local candidate.
  std::unique_ptr<cricket::Candidate> a_local_host_not_paired =
      CreateFakeCandidate("1.2.3.4", 4404, "a_local_host_not_paired's protocol",
                          rtc::ADAPTER_TYPE_VPN, cricket::LOCAL_PORT_TYPE, 0,
                          rtc::ADAPTER_TYPE_ETHERNET);
  RTCLocalIceCandidateStats expected_a_local_host_not_paired(
      "I" + a_local_host_not_paired->id(), Timestamp::Zero());
  expected_a_local_host_not_paired.transport_id = "Ta0";
  expected_a_local_host_not_paired.network_type = "vpn";
  expected_a_local_host_not_paired.ip = "1.2.3.4";
  expected_a_local_host_not_paired.address = "1.2.3.4";
  expected_a_local_host_not_paired.port = 4404;
  expected_a_local_host_not_paired.protocol =
      "a_local_host_not_paired's protocol";
  expected_a_local_host_not_paired.candidate_type = "host";
  expected_a_local_host_not_paired.priority = 0;
  expected_a_local_host_not_paired.vpn = true;
  expected_a_local_host_not_paired.network_adapter_type = "ethernet";
  expected_a_local_host_not_paired.foundation = "foundationIsAString";
  expected_a_local_host_not_paired.username_fragment = "iceusernamefragment";

  // Candidates in the second transport stats.
  std::unique_ptr<cricket::Candidate> b_local =
      CreateFakeCandidate("42.42.42.42", 42, "b_local's protocol",
                          rtc::ADAPTER_TYPE_WIFI, cricket::LOCAL_PORT_TYPE, 42);
  RTCLocalIceCandidateStats expected_b_local("I" + b_local->id(),
                                             Timestamp::Zero());
  expected_b_local.transport_id = "Tb0";
  expected_b_local.network_type = "wifi";
  expected_b_local.ip = "42.42.42.42";
  expected_b_local.address = "42.42.42.42";
  expected_b_local.port = 42;
  expected_b_local.protocol = "b_local's protocol";
  expected_b_local.candidate_type = "host";
  expected_b_local.priority = 42;
  expected_b_local.vpn = false;
  expected_b_local.network_adapter_type = "wifi";
  expected_b_local.foundation = "foundationIsAString";
  expected_b_local.username_fragment = "iceusernamefragment";

  std::unique_ptr<cricket::Candidate> b_remote = CreateFakeCandidate(
      "42.42.42.42", 42, "b_remote's protocol", rtc::ADAPTER_TYPE_UNKNOWN,
      cricket::LOCAL_PORT_TYPE, 42);
  RTCRemoteIceCandidateStats expected_b_remote("I" + b_remote->id(),
                                               Timestamp::Zero());
  expected_b_remote.transport_id = "Tb0";
  expected_b_remote.ip = "42.42.42.42";
  expected_b_remote.address = "42.42.42.42";
  expected_b_remote.port = 42;
  expected_b_remote.protocol = "b_remote's protocol";
  expected_b_remote.candidate_type = "host";
  expected_b_remote.priority = 42;
  expected_b_remote.foundation = "foundationIsAString";
  expected_b_remote.username_fragment = "iceusernamefragment";

  // Add candidate pairs to connection.
  cricket::TransportChannelStats a_transport_channel_stats;
  a_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      cricket::ConnectionInfo());
  a_transport_channel_stats.ice_transport_stats.connection_infos[0]
      .local_candidate = *a_local_host.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos[0]
      .remote_candidate = *a_remote_srflx.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      cricket::ConnectionInfo());
  a_transport_channel_stats.ice_transport_stats.connection_infos[1]
      .local_candidate = *a_local_prflx.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos[1]
      .remote_candidate = *a_remote_relay.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      cricket::ConnectionInfo());
  a_transport_channel_stats.ice_transport_stats.connection_infos[2]
      .local_candidate = *a_local_relay.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos[2]
      .remote_candidate = *a_remote_relay.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      cricket::ConnectionInfo());
  a_transport_channel_stats.ice_transport_stats.connection_infos[3]
      .local_candidate = *a_local_relay_prflx.get();
  a_transport_channel_stats.ice_transport_stats.connection_infos[3]
      .remote_candidate = *a_remote_relay.get();
  a_transport_channel_stats.ice_transport_stats.candidate_stats_list.push_back(
      cricket::CandidateStats(*a_local_host_not_paired.get()));

  pc_->AddVoiceChannel("audio", "a");
  pc_->SetTransportStats("a", a_transport_channel_stats);

  cricket::TransportChannelStats b_transport_channel_stats;
  b_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      cricket::ConnectionInfo());
  b_transport_channel_stats.ice_transport_stats.connection_infos[0]
      .local_candidate = *b_local.get();
  b_transport_channel_stats.ice_transport_stats.connection_infos[0]
      .remote_candidate = *b_remote.get();

  pc_->AddVideoChannel("video", "b");
  pc_->SetTransportStats("b", b_transport_channel_stats);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  ASSERT_TRUE(report->Get(expected_a_local_host.id()));
  EXPECT_EQ(expected_a_local_host, report->Get(expected_a_local_host.id())
                                       ->cast_to<RTCLocalIceCandidateStats>());

  ASSERT_TRUE(report->Get(expected_a_local_host_not_paired.id()));
  EXPECT_EQ(expected_a_local_host_not_paired,
            report->Get(expected_a_local_host_not_paired.id())
                ->cast_to<RTCLocalIceCandidateStats>());

  ASSERT_TRUE(report->Get(expected_a_remote_srflx.id()));
  EXPECT_EQ(expected_a_remote_srflx,
            report->Get(expected_a_remote_srflx.id())
                ->cast_to<RTCRemoteIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_a_local_prflx.id()));
  EXPECT_EQ(expected_a_local_prflx, report->Get(expected_a_local_prflx.id())
                                        ->cast_to<RTCLocalIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_a_remote_relay.id()));
  EXPECT_EQ(expected_a_remote_relay,
            report->Get(expected_a_remote_relay.id())
                ->cast_to<RTCRemoteIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_a_local_relay.id()));
  EXPECT_EQ(expected_a_local_relay, report->Get(expected_a_local_relay.id())
                                        ->cast_to<RTCLocalIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_a_local_relay_prflx.id()));
  EXPECT_EQ(expected_a_local_relay_prflx,
            report->Get(expected_a_local_relay_prflx.id())
                ->cast_to<RTCLocalIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_b_local.id()));
  EXPECT_EQ(
      expected_b_local,
      report->Get(expected_b_local.id())->cast_to<RTCLocalIceCandidateStats>());
  ASSERT_TRUE(report->Get(expected_b_remote.id()));
  EXPECT_EQ(expected_b_remote, report->Get(expected_b_remote.id())
                                   ->cast_to<RTCRemoteIceCandidateStats>());
  EXPECT_TRUE(report->Get("Ta0"));
  EXPECT_TRUE(report->Get("Tb0"));
}

TEST_F(RTCStatsCollectorTest, CollectRTCIceCandidatePairStats) {
  const char kTransportName[] = "transport";

  std::unique_ptr<cricket::Candidate> local_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_WIFI,
                          cricket::LOCAL_PORT_TYPE, 42);
  local_candidate->set_username("local_iceusernamefragment");

  std::unique_ptr<cricket::Candidate> remote_candidate = CreateFakeCandidate(
      "42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_UNKNOWN,
      cricket::STUN_PORT_TYPE, 42);
  remote_candidate->set_related_address(rtc::SocketAddress("192.168.2.1", 43));
  remote_candidate->set_username("remote_iceusernamefragment");

  cricket::ConnectionInfo connection_info;
  connection_info.best_connection = false;
  connection_info.local_candidate = *local_candidate.get();
  connection_info.remote_candidate = *remote_candidate.get();
  connection_info.writable = true;
  connection_info.sent_discarded_packets = 3;
  connection_info.sent_total_packets = 10;
  connection_info.packets_received = 51;
  connection_info.sent_discarded_bytes = 7;
  connection_info.sent_total_bytes = 42;
  connection_info.recv_total_bytes = 1234;
  connection_info.total_round_trip_time_ms = 0;
  connection_info.current_round_trip_time_ms = absl::nullopt;
  connection_info.recv_ping_requests = 2020;
  connection_info.sent_ping_requests_total = 2222;
  connection_info.sent_ping_requests_before_first_response = 2000;
  connection_info.recv_ping_responses = 4321;
  connection_info.sent_ping_responses = 1000;
  connection_info.state = cricket::IceCandidatePairState::IN_PROGRESS;
  connection_info.priority = 5555;
  connection_info.nominated = false;
  connection_info.last_data_received = Timestamp::Millis(2500);
  connection_info.last_data_sent = Timestamp::Millis(5200);

  cricket::TransportChannelStats transport_channel_stats;
  transport_channel_stats.component = cricket::ICE_CANDIDATE_COMPONENT_RTP;
  transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      connection_info);

  pc_->AddVideoChannel("video", kTransportName);
  pc_->SetTransportStats(kTransportName, transport_channel_stats);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCIceCandidatePairStats expected_pair(
      "CP" + local_candidate->id() + "_" + remote_candidate->id(),
      report->timestamp());
  expected_pair.transport_id =
      "Ttransport" + rtc::ToString(cricket::ICE_CANDIDATE_COMPONENT_RTP);
  expected_pair.local_candidate_id = "I" + local_candidate->id();
  expected_pair.remote_candidate_id = "I" + remote_candidate->id();
  expected_pair.state = "in-progress";
  expected_pair.priority = 5555;
  expected_pair.nominated = false;
  expected_pair.writable = true;
  expected_pair.packets_sent = 7;
  expected_pair.packets_received = 51;
  expected_pair.packets_discarded_on_send = 3;
  expected_pair.bytes_sent = 42;
  expected_pair.bytes_received = 1234;
  expected_pair.bytes_discarded_on_send = 7;
  expected_pair.total_round_trip_time = 0.0;
  expected_pair.requests_received = 2020;
  expected_pair.requests_sent = 2222;
  expected_pair.responses_received = 4321;
  expected_pair.responses_sent = 1000;
  expected_pair.consent_requests_sent = (2222 - 2000);
  expected_pair.last_packet_received_timestamp = 2500;
  expected_pair.last_packet_sent_timestamp = 5200;

  // `expected_pair.current_round_trip_time` should be undefined because the
  // current RTT is not set.
  // `expected_pair.available_[outgoing/incoming]_bitrate` should be undefined
  // because is is not the current pair.

  ASSERT_TRUE(report->Get(expected_pair.id()));
  EXPECT_EQ(
      expected_pair,
      report->Get(expected_pair.id())->cast_to<RTCIceCandidatePairStats>());
  EXPECT_TRUE(report->Get(*expected_pair.transport_id));

  // Set nominated and "GetStats" again.
  transport_channel_stats.ice_transport_stats.connection_infos[0].nominated =
      true;
  pc_->SetTransportStats(kTransportName, transport_channel_stats);
  report = stats_->GetFreshStatsReport();
  expected_pair.nominated = true;
  ASSERT_TRUE(report->Get(expected_pair.id()));
  EXPECT_EQ(
      expected_pair,
      report->Get(expected_pair.id())->cast_to<RTCIceCandidatePairStats>());
  EXPECT_TRUE(report->Get(*expected_pair.transport_id));

  // Set round trip times and "GetStats" again.
  transport_channel_stats.ice_transport_stats.connection_infos[0]
      .total_round_trip_time_ms = 7331;
  transport_channel_stats.ice_transport_stats.connection_infos[0]
      .current_round_trip_time_ms = 1337;
  pc_->SetTransportStats(kTransportName, transport_channel_stats);
  report = stats_->GetFreshStatsReport();
  expected_pair.total_round_trip_time = 7.331;
  expected_pair.current_round_trip_time = 1.337;
  ASSERT_TRUE(report->Get(expected_pair.id()));
  EXPECT_EQ(
      expected_pair,
      report->Get(expected_pair.id())->cast_to<RTCIceCandidatePairStats>());
  EXPECT_TRUE(report->Get(*expected_pair.transport_id));

  // Make pair the current pair, clear bandwidth and "GetStats" again.
  transport_channel_stats.ice_transport_stats.connection_infos[0]
      .best_connection = true;
  pc_->SetTransportStats(kTransportName, transport_channel_stats);
  report = stats_->GetFreshStatsReport();
  // |expected_pair.available_[outgoing/incoming]_bitrate| should still be
  // undefined because bandwidth is not set.
  ASSERT_TRUE(report->Get(expected_pair.id()));
  EXPECT_EQ(
      expected_pair,
      report->Get(expected_pair.id())->cast_to<RTCIceCandidatePairStats>());
  EXPECT_TRUE(report->Get(*expected_pair.transport_id));

  // Set bandwidth and "GetStats" again.
  Call::Stats call_stats;
  const int kSendBandwidth = 888;
  call_stats.send_bandwidth_bps = kSendBandwidth;
  const int kRecvBandwidth = 999;
  call_stats.recv_bandwidth_bps = kRecvBandwidth;
  pc_->SetCallStats(call_stats);
  report = stats_->GetFreshStatsReport();
  expected_pair.available_outgoing_bitrate = kSendBandwidth;
  expected_pair.available_incoming_bitrate = kRecvBandwidth;
  ASSERT_TRUE(report->Get(expected_pair.id()));
  EXPECT_EQ(
      expected_pair,
      report->Get(expected_pair.id())->cast_to<RTCIceCandidatePairStats>());
  EXPECT_TRUE(report->Get(*expected_pair.transport_id));

  RTCLocalIceCandidateStats expected_local_candidate(
      *expected_pair.local_candidate_id, report->timestamp());
  expected_local_candidate.transport_id = *expected_pair.transport_id;
  expected_local_candidate.network_type = "wifi";
  expected_local_candidate.ip = "42.42.42.42";
  expected_local_candidate.address = "42.42.42.42";
  expected_local_candidate.port = 42;
  expected_local_candidate.protocol = "protocol";
  expected_local_candidate.candidate_type = "host";
  expected_local_candidate.priority = 42;
  expected_local_candidate.foundation = "foundationIsAString";
  expected_local_candidate.username_fragment = "local_iceusernamefragment";
  expected_local_candidate.vpn = false;
  expected_local_candidate.network_adapter_type = "wifi";
  ASSERT_TRUE(report->Get(expected_local_candidate.id()));
  EXPECT_EQ(expected_local_candidate,
            report->Get(expected_local_candidate.id())
                ->cast_to<RTCLocalIceCandidateStats>());

  RTCRemoteIceCandidateStats expected_remote_candidate(
      *expected_pair.remote_candidate_id, report->timestamp());
  expected_remote_candidate.transport_id = *expected_pair.transport_id;
  expected_remote_candidate.ip = "42.42.42.42";
  expected_remote_candidate.address = "42.42.42.42";
  expected_remote_candidate.port = 42;
  expected_remote_candidate.protocol = "protocol";
  expected_remote_candidate.candidate_type = "srflx";
  expected_remote_candidate.priority = 42;
  expected_remote_candidate.foundation = "foundationIsAString";
  expected_remote_candidate.username_fragment = "remote_iceusernamefragment";
  expected_remote_candidate.related_address = "192.168.2.1";
  expected_remote_candidate.related_port = 43;
  ASSERT_TRUE(report->Get(expected_remote_candidate.id()));
  EXPECT_EQ(expected_remote_candidate,
            report->Get(expected_remote_candidate.id())
                ->cast_to<RTCRemoteIceCandidateStats>());
}

TEST_F(RTCStatsCollectorTest, CollectRTCPeerConnectionStats) {
  {
    rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
    RTCPeerConnectionStats expected("P", report->timestamp());
    expected.data_channels_opened = 0;
    expected.data_channels_closed = 0;
    ASSERT_TRUE(report->Get("P"));
    EXPECT_EQ(expected, report->Get("P")->cast_to<RTCPeerConnectionStats>());
  }

  FakeDataChannelController controller(pc_->network_thread());
  rtc::scoped_refptr<SctpDataChannel> dummy_channel_a = SctpDataChannel::Create(
      controller.weak_ptr(), "DummyChannelA", false, InternalDataChannelInit(),
      rtc::Thread::Current(), rtc::Thread::Current());
  rtc::scoped_refptr<SctpDataChannel> dummy_channel_b = SctpDataChannel::Create(
      controller.weak_ptr(), "DummyChannelB", false, InternalDataChannelInit(),
      rtc::Thread::Current(), rtc::Thread::Current());

  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_a->internal_id(), DataChannelInterface::DataState::kOpen);
  // Closing a channel that is not opened should not affect the counts.
  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_b->internal_id(), DataChannelInterface::DataState::kClosed);

  {
    rtc::scoped_refptr<const RTCStatsReport> report =
        stats_->GetFreshStatsReport();
    RTCPeerConnectionStats expected("P", report->timestamp());
    expected.data_channels_opened = 1;
    expected.data_channels_closed = 0;
    ASSERT_TRUE(report->Get("P"));
    EXPECT_EQ(expected, report->Get("P")->cast_to<RTCPeerConnectionStats>());
  }

  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_b->internal_id(), DataChannelInterface::DataState::kOpen);
  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_b->internal_id(), DataChannelInterface::DataState::kClosed);

  {
    rtc::scoped_refptr<const RTCStatsReport> report =
        stats_->GetFreshStatsReport();
    RTCPeerConnectionStats expected("P", report->timestamp());
    expected.data_channels_opened = 2;
    expected.data_channels_closed = 1;
    ASSERT_TRUE(report->Get("P"));
    EXPECT_EQ(expected, report->Get("P")->cast_to<RTCPeerConnectionStats>());
  }

  // Re-opening a data channel (or opening a new data channel that is re-using
  // the same address in memory) should increase the opened count.
  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_b->internal_id(), DataChannelInterface::DataState::kOpen);

  {
    rtc::scoped_refptr<const RTCStatsReport> report =
        stats_->GetFreshStatsReport();
    RTCPeerConnectionStats expected("P", report->timestamp());
    expected.data_channels_opened = 3;
    expected.data_channels_closed = 1;
    ASSERT_TRUE(report->Get("P"));
    EXPECT_EQ(expected, report->Get("P")->cast_to<RTCPeerConnectionStats>());
  }

  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_a->internal_id(), DataChannelInterface::DataState::kClosed);
  stats_->stats_collector()->OnSctpDataChannelStateChanged(
      dummy_channel_b->internal_id(), DataChannelInterface::DataState::kClosed);

  {
    rtc::scoped_refptr<const RTCStatsReport> report =
        stats_->GetFreshStatsReport();
    RTCPeerConnectionStats expected("P", report->timestamp());
    expected.data_channels_opened = 3;
    expected.data_channels_closed = 3;
    ASSERT_TRUE(report->Get("P"));
    EXPECT_EQ(expected, report->Get("P")->cast_to<RTCPeerConnectionStats>());
  }
}

TEST_F(RTCStatsCollectorTest, CollectRTCInboundRtpStreamStats_Audio) {
  cricket::VoiceMediaInfo voice_media_info;

  voice_media_info.receivers.push_back(cricket::VoiceReceiverInfo());
  voice_media_info.receivers[0].local_stats.push_back(
      cricket::SsrcReceiverInfo());
  voice_media_info.receivers[0].local_stats[0].ssrc = 1;
  voice_media_info.receivers[0].packets_lost = -1;  // Signed per RFC3550
  voice_media_info.receivers[0].packets_discarded = 7788;
  voice_media_info.receivers[0].packets_received = 2;
  voice_media_info.receivers[0].nacks_sent = 5;
  voice_media_info.receivers[0].fec_packets_discarded = 5566;
  voice_media_info.receivers[0].fec_packets_received = 6677;
  voice_media_info.receivers[0].payload_bytes_received = 3;
  voice_media_info.receivers[0].header_and_padding_bytes_received = 4;
  voice_media_info.receivers[0].codec_payload_type = 42;
  voice_media_info.receivers[0].jitter_ms = 4500;
  voice_media_info.receivers[0].jitter_buffer_delay_seconds = 1.0;
  voice_media_info.receivers[0].jitter_buffer_target_delay_seconds = 1.1;
  voice_media_info.receivers[0].jitter_buffer_minimum_delay_seconds = 0.999;
  voice_media_info.receivers[0].jitter_buffer_emitted_count = 2;
  voice_media_info.receivers[0].total_samples_received = 3;
  voice_media_info.receivers[0].concealed_samples = 4;
  voice_media_info.receivers[0].silent_concealed_samples = 5;
  voice_media_info.receivers[0].concealment_events = 6;
  voice_media_info.receivers[0].inserted_samples_for_deceleration = 7;
  voice_media_info.receivers[0].removed_samples_for_acceleration = 8;
  voice_media_info.receivers[0].audio_level = 14442;  // [0,32767]
  voice_media_info.receivers[0].total_output_energy = 10.0;
  voice_media_info.receivers[0].total_output_duration = 11.0;
  voice_media_info.receivers[0].jitter_buffer_flushes = 7;
  voice_media_info.receivers[0].delayed_packet_outage_samples = 15;
  voice_media_info.receivers[0].relative_packet_arrival_delay_seconds = 16;
  voice_media_info.receivers[0].interruption_count = 7788;
  voice_media_info.receivers[0].total_interruption_duration_ms = 778899;
  voice_media_info.receivers[0].last_packet_received = absl::nullopt;

  RtpCodecParameters codec_parameters;
  codec_parameters.payload_type = 42;
  codec_parameters.kind = cricket::MEDIA_TYPE_AUDIO;
  codec_parameters.name = "dummy";
  codec_parameters.clock_rate = 0;
  voice_media_info.receive_codecs.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));

  auto voice_media_channels =
      pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  stats_->SetupRemoteTrackAndReceiver(
      cricket::MEDIA_TYPE_AUDIO, "RemoteAudioTrackID", "RemoteStreamId", 1);

  // Needed for playoutId to be populated.
  pc_->SetAudioDeviceStats(AudioDeviceModule::Stats());
  pc_->GetTransceiversInternal()[0]->internal()->set_current_direction(
      RtpTransceiverDirection::kSendRecv);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCInboundRtpStreamStats expected_audio("ITTransportName1A1",
                                          report->timestamp());
  expected_audio.ssrc = 1;
  expected_audio.kind = "audio";
  expected_audio.track_identifier = "RemoteAudioTrackID";
  expected_audio.mid = "AudioMid";
  expected_audio.transport_id = "TTransportName1";
  expected_audio.codec_id = "CITTransportName1_42";
  expected_audio.packets_received = 2;
  expected_audio.nack_count = 5;
  expected_audio.fec_packets_discarded = 5566;
  expected_audio.fec_packets_received = 6677;
  expected_audio.bytes_received = 3;
  expected_audio.header_bytes_received = 4;
  expected_audio.packets_lost = -1;
  expected_audio.packets_discarded = 7788;
  // `expected_audio.last_packet_received_timestamp` should be undefined.
  expected_audio.jitter = 4.5;
  expected_audio.jitter_buffer_delay = 1.0;
  expected_audio.jitter_buffer_target_delay = 1.1;
  expected_audio.jitter_buffer_minimum_delay = 0.999;
  expected_audio.jitter_buffer_emitted_count = 2;
  expected_audio.total_samples_received = 3;
  expected_audio.concealed_samples = 4;
  expected_audio.silent_concealed_samples = 5;
  expected_audio.concealment_events = 6;
  expected_audio.inserted_samples_for_deceleration = 7;
  expected_audio.removed_samples_for_acceleration = 8;
  expected_audio.audio_level = 14442.0 / 32767.0;  // [0,1]
  expected_audio.total_audio_energy = 10.0;
  expected_audio.total_samples_duration = 11.0;
  expected_audio.jitter_buffer_flushes = 7;
  expected_audio.delayed_packet_outage_samples = 15;
  expected_audio.relative_packet_arrival_delay = 16;
  expected_audio.interruption_count = 7788;
  expected_audio.total_interruption_duration = 778.899;
  expected_audio.playout_id = "AP";

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(
      report->Get(expected_audio.id())->cast_to<RTCInboundRtpStreamStats>(),
      expected_audio);

  // Set previously undefined values and "GetStats" again.
  voice_media_info.receivers[0].last_packet_received = Timestamp::Seconds(3);
  expected_audio.last_packet_received_timestamp = 3000.0;
  voice_media_info.receivers[0].estimated_playout_ntp_timestamp_ms = 4567;
  expected_audio.estimated_playout_timestamp = 4567;
  voice_media_channels.first->SetStats(voice_media_info);
  voice_media_channels.second->SetStats(voice_media_info);

  report = stats_->GetFreshStatsReport();

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(
      report->Get(expected_audio.id())->cast_to<RTCInboundRtpStreamStats>(),
      expected_audio);
  EXPECT_TRUE(report->Get(*expected_audio.transport_id));
  EXPECT_TRUE(report->Get(*expected_audio.codec_id));
}

TEST_F(RTCStatsCollectorTest, CollectRTCInboundRtpStreamStats_Audio_PlayoutId) {
  cricket::VoiceMediaInfo voice_media_info;

  voice_media_info.receivers.push_back(cricket::VoiceReceiverInfo());
  voice_media_info.receivers[0].local_stats.push_back(
      cricket::SsrcReceiverInfo());
  voice_media_info.receivers[0].local_stats[0].ssrc = 1;

  pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  stats_->SetupRemoteTrackAndReceiver(
      cricket::MEDIA_TYPE_AUDIO, "RemoteAudioTrackID", "RemoteStreamId", 1);
  // Needed for playoutId to be populated.
  pc_->SetAudioDeviceStats(AudioDeviceModule::Stats());

  {
    // We do not expect a playout id when only sending.
    pc_->GetTransceiversInternal()[0]->internal()->set_current_direction(
        RtpTransceiverDirection::kSendOnly);
    rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
    ASSERT_TRUE(report->Get("ITTransportName1A1"));
    auto stats =
        report->Get("ITTransportName1A1")->cast_to<RTCInboundRtpStreamStats>();
    ASSERT_FALSE(stats.playout_id.has_value());
  }
  {
    // We do expect a playout id when receiving.
    pc_->GetTransceiversInternal()[0]->internal()->set_current_direction(
        RtpTransceiverDirection::kRecvOnly);
    rtc::scoped_refptr<const RTCStatsReport> report =
        stats_->GetFreshStatsReport();
    ASSERT_TRUE(report->Get("ITTransportName1A1"));
    auto stats =
        report->Get("ITTransportName1A1")->cast_to<RTCInboundRtpStreamStats>();
    ASSERT_TRUE(stats.playout_id.has_value());
    EXPECT_EQ(*stats.playout_id, "AP");
  }
}

TEST_F(RTCStatsCollectorTest, CollectRTCInboundRtpStreamStats_Video) {
  cricket::VideoMediaInfo video_media_info;

  video_media_info.receivers.push_back(cricket::VideoReceiverInfo());
  video_media_info.receivers[0].local_stats.push_back(
      cricket::SsrcReceiverInfo());
  video_media_info.receivers[0].local_stats[0].ssrc = 1;
  video_media_info.receivers[0].packets_received = 2;
  video_media_info.receivers[0].packets_lost = 42;
  video_media_info.receivers[0].payload_bytes_received = 3;
  video_media_info.receivers[0].header_and_padding_bytes_received = 12;
  video_media_info.receivers[0].codec_payload_type = 42;
  video_media_info.receivers[0].firs_sent = 5;
  video_media_info.receivers[0].plis_sent = 6;
  video_media_info.receivers[0].nacks_sent = 7;
  video_media_info.receivers[0].frames_received = 8;
  video_media_info.receivers[0].frames_decoded = 9;
  video_media_info.receivers[0].key_frames_decoded = 3;
  video_media_info.receivers[0].frames_dropped = 13;
  video_media_info.receivers[0].qp_sum = absl::nullopt;
  video_media_info.receivers[0].total_decode_time = TimeDelta::Seconds(9);
  video_media_info.receivers[0].total_processing_delay = TimeDelta::Millis(600);
  video_media_info.receivers[0].total_assembly_time = TimeDelta::Millis(500);
  video_media_info.receivers[0].frames_assembled_from_multiple_packets = 23;
  video_media_info.receivers[0].total_inter_frame_delay = 0.123;
  video_media_info.receivers[0].total_squared_inter_frame_delay = 0.00456;
  video_media_info.receivers[0].pause_count = 2;
  video_media_info.receivers[0].total_pauses_duration_ms = 10000;
  video_media_info.receivers[0].freeze_count = 3;
  video_media_info.receivers[0].total_freezes_duration_ms = 1000;
  video_media_info.receivers[0].jitter_ms = 1199;
  video_media_info.receivers[0].jitter_buffer_delay_seconds = 3.456;
  video_media_info.receivers[0].jitter_buffer_target_delay_seconds = 1.1;
  video_media_info.receivers[0].jitter_buffer_minimum_delay_seconds = 0.999;
  video_media_info.receivers[0].jitter_buffer_emitted_count = 13;
  video_media_info.receivers[0].last_packet_received = absl::nullopt;
  video_media_info.receivers[0].content_type = VideoContentType::UNSPECIFIED;
  video_media_info.receivers[0].estimated_playout_ntp_timestamp_ms =
      absl::nullopt;
  video_media_info.receivers[0].decoder_implementation_name = absl::nullopt;
  video_media_info.receivers[0].min_playout_delay_ms = 50;
  video_media_info.receivers[0].power_efficient_decoder = false;
  video_media_info.receivers[0].retransmitted_packets_received = 17;
  video_media_info.receivers[0].retransmitted_bytes_received = 62;
  video_media_info.receivers[0].fec_packets_received = 32;
  video_media_info.receivers[0].fec_bytes_received = 54;
  video_media_info.receivers[0].ssrc_groups.push_back(
      {cricket::kFidSsrcGroupSemantics, {1, 4404}});
  video_media_info.receivers[0].ssrc_groups.push_back(
      {cricket::kFecFrSsrcGroupSemantics, {1, 5505}});

  // Note: these two values intentionally differ,
  // only the decoded one should show up.
  video_media_info.receivers[0].framerate_received = 15;
  video_media_info.receivers[0].framerate_decoded = 5;

  RtpCodecParameters codec_parameters;
  codec_parameters.payload_type = 42;
  codec_parameters.kind = cricket::MEDIA_TYPE_VIDEO;
  codec_parameters.name = "dummy";
  codec_parameters.clock_rate = 0;
  video_media_info.receive_codecs.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));

  auto video_media_channels =
      pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);
  stats_->SetupRemoteTrackAndReceiver(
      cricket::MEDIA_TYPE_VIDEO, "RemoteVideoTrackID", "RemoteStreamId", 1);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCInboundRtpStreamStats expected_video("ITTransportName1V1",
                                          report->timestamp());
  expected_video.ssrc = 1;
  expected_video.kind = "video";
  expected_video.track_identifier = "RemoteVideoTrackID";
  expected_video.mid = "VideoMid";
  expected_video.transport_id = "TTransportName1";
  expected_video.codec_id = "CITTransportName1_42";
  expected_video.fir_count = 5;
  expected_video.pli_count = 6;
  expected_video.nack_count = 7;
  expected_video.packets_received = 2;
  expected_video.bytes_received = 3;
  expected_video.header_bytes_received = 12;
  expected_video.packets_lost = 42;
  expected_video.frames_received = 8;
  expected_video.frames_decoded = 9;
  expected_video.key_frames_decoded = 3;
  expected_video.frames_dropped = 13;
  // `expected_video.qp_sum` should be undefined.
  expected_video.total_decode_time = 9.0;
  expected_video.total_processing_delay = 0.6;
  expected_video.total_assembly_time = 0.5;
  expected_video.frames_assembled_from_multiple_packets = 23;
  expected_video.total_inter_frame_delay = 0.123;
  expected_video.total_squared_inter_frame_delay = 0.00456;
  expected_video.pause_count = 2;
  expected_video.total_pauses_duration = 10;
  expected_video.freeze_count = 3;
  expected_video.total_freezes_duration = 1;
  expected_video.jitter = 1.199;
  expected_video.jitter_buffer_delay = 3.456;
  expected_video.jitter_buffer_target_delay = 1.1;
  expected_video.jitter_buffer_minimum_delay = 0.999;
  expected_video.jitter_buffer_emitted_count = 13;
  // `expected_video.last_packet_received_timestamp` should be undefined.
  // `expected_video.content_type` should be undefined.
  // `expected_video.decoder_implementation` should be undefined.
  expected_video.min_playout_delay = 0.05;
  expected_video.frames_per_second = 5;
  expected_video.power_efficient_decoder = false;
  expected_video.retransmitted_packets_received = 17;
  expected_video.retransmitted_bytes_received = 62;
  expected_video.fec_packets_received = 32;
  expected_video.fec_bytes_received = 54;
  expected_video.rtx_ssrc = 4404;
  expected_video.fec_ssrc = 5505;

  ASSERT_TRUE(report->Get(expected_video.id()));
  EXPECT_EQ(
      report->Get(expected_video.id())->cast_to<RTCInboundRtpStreamStats>(),
      expected_video);

  // Set previously undefined values and "GetStats" again.
  video_media_info.receivers[0].qp_sum = 9;
  expected_video.qp_sum = 9;
  video_media_info.receivers[0].last_packet_received = Timestamp::Seconds(1);
  expected_video.last_packet_received_timestamp = 1000.0;
  video_media_info.receivers[0].content_type = VideoContentType::SCREENSHARE;
  expected_video.content_type = "screenshare";
  video_media_info.receivers[0].estimated_playout_ntp_timestamp_ms = 1234;
  expected_video.estimated_playout_timestamp = 1234;
  video_media_info.receivers[0].decoder_implementation_name = "libfoodecoder";
  expected_video.decoder_implementation = "libfoodecoder";
  video_media_info.receivers[0].power_efficient_decoder = true;
  expected_video.power_efficient_decoder = true;
  video_media_channels.first->SetStats(video_media_info);
  video_media_channels.second->SetStats(video_media_info);

  report = stats_->GetFreshStatsReport();

  ASSERT_TRUE(report->Get(expected_video.id()));
  EXPECT_EQ(
      report->Get(expected_video.id())->cast_to<RTCInboundRtpStreamStats>(),
      expected_video);
  EXPECT_TRUE(report->Get(*expected_video.transport_id));
  EXPECT_TRUE(report->Get(*expected_video.codec_id));
}

TEST_F(RTCStatsCollectorTest, CollectRTCAudioPlayoutStats) {
  AudioDeviceModule::Stats audio_device_stats;
  audio_device_stats.synthesized_samples_duration_s = 1;
  audio_device_stats.synthesized_samples_events = 2;
  audio_device_stats.total_samples_count = 3;
  audio_device_stats.total_samples_duration_s = 4;
  audio_device_stats.total_playout_delay_s = 5;
  pc_->SetAudioDeviceStats(audio_device_stats);

  pc_->AddVoiceChannel("AudioMid", "TransportName", {});
  stats_->SetupRemoteTrackAndReceiver(
      cricket::MEDIA_TYPE_AUDIO, "RemoteAudioTrackID", "RemoteStreamId", 1);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  auto stats_of_track_type = report->GetStatsOfType<RTCAudioPlayoutStats>();
  ASSERT_EQ(1U, stats_of_track_type.size());

  RTCAudioPlayoutStats expected_stats("AP", report->timestamp());
  expected_stats.kind = "audio";
  expected_stats.synthesized_samples_duration = 1;
  expected_stats.synthesized_samples_events = 2;
  expected_stats.total_samples_count = 3;
  expected_stats.total_samples_duration = 4;
  expected_stats.total_playout_delay = 5;

  ASSERT_TRUE(report->Get(expected_stats.id()));
  EXPECT_EQ(report->Get(expected_stats.id())->cast_to<RTCAudioPlayoutStats>(),
            expected_stats);
}

TEST_F(RTCStatsCollectorTest, CollectGoogTimingFrameInfo) {
  cricket::VideoMediaInfo video_media_info;

  video_media_info.receivers.push_back(cricket::VideoReceiverInfo());
  video_media_info.receivers[0].local_stats.push_back(
      cricket::SsrcReceiverInfo());
  video_media_info.receivers[0].local_stats[0].ssrc = 1;
  TimingFrameInfo timing_frame_info;
  timing_frame_info.rtp_timestamp = 1;
  timing_frame_info.capture_time_ms = 2;
  timing_frame_info.encode_start_ms = 3;
  timing_frame_info.encode_finish_ms = 4;
  timing_frame_info.packetization_finish_ms = 5;
  timing_frame_info.pacer_exit_ms = 6;
  timing_frame_info.network_timestamp_ms = 7;
  timing_frame_info.network2_timestamp_ms = 8;
  timing_frame_info.receive_start_ms = 9;
  timing_frame_info.receive_finish_ms = 10;
  timing_frame_info.decode_start_ms = 11;
  timing_frame_info.decode_finish_ms = 12;
  timing_frame_info.render_time_ms = 13;
  timing_frame_info.flags = 14;
  video_media_info.receivers[0].timing_frame_info = timing_frame_info;

  pc_->AddVideoChannel("Mid0", "Transport0", video_media_info);
  stats_->SetupRemoteTrackAndReceiver(
      cricket::MEDIA_TYPE_VIDEO, "RemoteVideoTrackID", "RemoteStreamId", 1);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  auto inbound_rtps = report->GetStatsOfType<RTCInboundRtpStreamStats>();
  ASSERT_EQ(inbound_rtps.size(), 1u);
  ASSERT_TRUE(inbound_rtps[0]->goog_timing_frame_info.has_value());
  EXPECT_EQ(*inbound_rtps[0]->goog_timing_frame_info,
            "1,2,3,4,5,6,7,8,9,10,11,12,13,1,0");
}

TEST_F(RTCStatsCollectorTest, CollectRTCOutboundRtpStreamStats_Audio) {
  cricket::VoiceMediaInfo voice_media_info;

  voice_media_info.senders.push_back(cricket::VoiceSenderInfo());
  voice_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  voice_media_info.senders[0].local_stats[0].ssrc = 1;
  voice_media_info.senders[0].packets_sent = 2;
  voice_media_info.senders[0].total_packet_send_delay = TimeDelta::Seconds(1);
  voice_media_info.senders[0].retransmitted_packets_sent = 20;
  voice_media_info.senders[0].payload_bytes_sent = 3;
  voice_media_info.senders[0].header_and_padding_bytes_sent = 12;
  voice_media_info.senders[0].retransmitted_bytes_sent = 30;
  voice_media_info.senders[0].nacks_received = 31;
  voice_media_info.senders[0].target_bitrate = 32000;
  voice_media_info.senders[0].codec_payload_type = 42;
  voice_media_info.senders[0].active = true;

  RtpCodecParameters codec_parameters;
  codec_parameters.payload_type = 42;
  codec_parameters.kind = cricket::MEDIA_TYPE_AUDIO;
  codec_parameters.name = "dummy";
  codec_parameters.clock_rate = 0;
  voice_media_info.send_codecs.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));

  pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  stats_->SetupLocalTrackAndSender(cricket::MEDIA_TYPE_AUDIO,
                                   "LocalAudioTrackID", 1, true,
                                   /*attachment_id=*/50);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCOutboundRtpStreamStats expected_audio("OTTransportName1A1",
                                           report->timestamp());
  expected_audio.media_source_id = "SA50";
  // `expected_audio.remote_id` should be undefined.
  expected_audio.mid = "AudioMid";
  expected_audio.ssrc = 1;
  expected_audio.kind = "audio";
  expected_audio.transport_id = "TTransportName1";
  expected_audio.codec_id = "COTTransportName1_42";
  expected_audio.packets_sent = 2;
  expected_audio.total_packet_send_delay = 1;
  expected_audio.retransmitted_packets_sent = 20;
  expected_audio.bytes_sent = 3;
  expected_audio.header_bytes_sent = 12;
  expected_audio.retransmitted_bytes_sent = 30;
  expected_audio.nack_count = 31;
  expected_audio.target_bitrate = 32000;
  expected_audio.active = true;

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(
      report->Get(expected_audio.id())->cast_to<RTCOutboundRtpStreamStats>(),
      expected_audio);

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(
      report->Get(expected_audio.id())->cast_to<RTCOutboundRtpStreamStats>(),
      expected_audio);
  EXPECT_TRUE(report->Get(*expected_audio.transport_id));
  EXPECT_TRUE(report->Get(*expected_audio.codec_id));
}

TEST_F(RTCStatsCollectorTest, CollectRTCOutboundRtpStreamStats_Video) {
  cricket::VideoMediaInfo video_media_info;

  video_media_info.senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  video_media_info.senders[0].local_stats[0].ssrc = 1;
  video_media_info.senders[0].firs_received = 2;
  video_media_info.senders[0].plis_received = 3;
  video_media_info.senders[0].nacks_received = 4;
  video_media_info.senders[0].packets_sent = 5;
  video_media_info.senders[0].retransmitted_packets_sent = 50;
  video_media_info.senders[0].payload_bytes_sent = 6;
  video_media_info.senders[0].header_and_padding_bytes_sent = 12;
  video_media_info.senders[0].retransmitted_bytes_sent = 60;
  video_media_info.senders[0].codec_payload_type = 42;
  video_media_info.senders[0].frames_encoded = 8;
  video_media_info.senders[0].key_frames_encoded = 3;
  video_media_info.senders[0].total_encode_time_ms = 9000;
  video_media_info.senders[0].total_encoded_bytes_target = 1234;
  video_media_info.senders[0].total_packet_send_delay = TimeDelta::Seconds(10);
  video_media_info.senders[0].quality_limitation_reason =
      QualityLimitationReason::kBandwidth;
  video_media_info.senders[0]
      .quality_limitation_durations_ms[QualityLimitationReason::kBandwidth] =
      300;
  video_media_info.senders[0].quality_limitation_resolution_changes = 56u;
  video_media_info.senders[0].qp_sum = absl::nullopt;
  video_media_info.senders[0].content_type = VideoContentType::UNSPECIFIED;
  video_media_info.senders[0].encoder_implementation_name = absl::nullopt;
  video_media_info.senders[0].power_efficient_encoder = false;
  video_media_info.senders[0].send_frame_width = 200;
  video_media_info.senders[0].send_frame_height = 100;
  video_media_info.senders[0].framerate_sent = 10;
  video_media_info.senders[0].frames_sent = 5;
  video_media_info.senders[0].huge_frames_sent = 2;
  video_media_info.senders[0].active = false;
  video_media_info.senders[0].scalability_mode = ScalabilityMode::kL3T3_KEY;
  video_media_info.senders[0].ssrc_groups.push_back(
      {cricket::kFidSsrcGroupSemantics, {1, 4404}});
  video_media_info.aggregated_senders.push_back(video_media_info.senders[0]);
  RtpCodecParameters codec_parameters;
  codec_parameters.payload_type = 42;
  codec_parameters.kind = cricket::MEDIA_TYPE_AUDIO;
  codec_parameters.name = "dummy";
  codec_parameters.clock_rate = 0;
  video_media_info.send_codecs.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));

  auto video_media_channels =
      pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);
  stats_->SetupLocalTrackAndSender(cricket::MEDIA_TYPE_VIDEO,
                                   "LocalVideoTrackID", 1, true,
                                   /*attachment_id=*/50);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  auto stats_of_my_type = report->GetStatsOfType<RTCOutboundRtpStreamStats>();
  ASSERT_EQ(1U, stats_of_my_type.size());

  RTCOutboundRtpStreamStats expected_video(stats_of_my_type[0]->id(),
                                           report->timestamp());
  expected_video.media_source_id = "SV50";
  // `expected_video.remote_id` should be undefined.
  expected_video.mid = "VideoMid";
  expected_video.ssrc = 1;
  expected_video.kind = "video";
  expected_video.transport_id = "TTransportName1";
  expected_video.codec_id = "COTTransportName1_42";
  expected_video.fir_count = 2;
  expected_video.pli_count = 3;
  expected_video.nack_count = 4;
  expected_video.packets_sent = 5;
  expected_video.retransmitted_packets_sent = 50;
  expected_video.bytes_sent = 6;
  expected_video.header_bytes_sent = 12;
  expected_video.retransmitted_bytes_sent = 60;
  expected_video.frames_encoded = 8;
  expected_video.key_frames_encoded = 3;
  expected_video.total_encode_time = 9.0;
  expected_video.total_encoded_bytes_target = 1234;
  expected_video.total_packet_send_delay = 10.0;
  expected_video.quality_limitation_reason = "bandwidth";
  expected_video.quality_limitation_durations = std::map<std::string, double>{
      std::pair<std::string, double>{"bandwidth", 0.3},
  };
  expected_video.quality_limitation_resolution_changes = 56u;
  expected_video.frame_width = 200u;
  expected_video.frame_height = 100u;
  expected_video.frames_per_second = 10.0;
  expected_video.frames_sent = 5;
  expected_video.huge_frames_sent = 2;
  expected_video.active = false;
  expected_video.power_efficient_encoder = false;
  expected_video.scalability_mode = "L3T3_KEY";
  expected_video.rtx_ssrc = 4404;
  // `expected_video.content_type` should be undefined.
  // `expected_video.qp_sum` should be undefined.
  // `expected_video.encoder_implementation` should be undefined.
  ASSERT_TRUE(report->Get(expected_video.id()));

  EXPECT_EQ(
      report->Get(expected_video.id())->cast_to<RTCOutboundRtpStreamStats>(),
      expected_video);

  // Set previously undefined values and "GetStats" again.
  video_media_info.senders[0].qp_sum = 9;
  expected_video.qp_sum = 9;
  video_media_info.senders[0].content_type = VideoContentType::SCREENSHARE;
  expected_video.content_type = "screenshare";
  video_media_info.senders[0].encoder_implementation_name = "libfooencoder";
  video_media_info.aggregated_senders[0] = video_media_info.senders[0];
  expected_video.encoder_implementation = "libfooencoder";
  video_media_info.senders[0].power_efficient_encoder = true;
  expected_video.power_efficient_encoder = true;
  video_media_channels.first->SetStats(video_media_info);
  video_media_channels.second->SetStats(video_media_info);

  report = stats_->GetFreshStatsReport();

  ASSERT_TRUE(report->Get(expected_video.id()));
  EXPECT_EQ(
      report->Get(expected_video.id())->cast_to<RTCOutboundRtpStreamStats>(),
      expected_video);
  EXPECT_TRUE(report->Get(*expected_video.transport_id));
  EXPECT_TRUE(report->Get(*expected_video.codec_id));
}

TEST_F(RTCStatsCollectorTest, CollectRTCTransportStats) {
  const char kTransportName[] = "transport";

  pc_->AddVoiceChannel("audio", kTransportName);

  std::unique_ptr<cricket::Candidate> rtp_local_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_WIFI,
                          cricket::LOCAL_PORT_TYPE, 42);
  std::unique_ptr<cricket::Candidate> rtp_remote_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol",
                          rtc::ADAPTER_TYPE_UNKNOWN, cricket::LOCAL_PORT_TYPE,
                          42);
  std::unique_ptr<cricket::Candidate> rtcp_local_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_WIFI,
                          cricket::LOCAL_PORT_TYPE, 42);
  std::unique_ptr<cricket::Candidate> rtcp_remote_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol",
                          rtc::ADAPTER_TYPE_UNKNOWN, cricket::LOCAL_PORT_TYPE,
                          42);

  cricket::ConnectionInfo rtp_connection_info;
  rtp_connection_info.best_connection = false;
  rtp_connection_info.local_candidate = *rtp_local_candidate.get();
  rtp_connection_info.remote_candidate = *rtp_remote_candidate.get();
  rtp_connection_info.sent_total_bytes = 42;
  rtp_connection_info.recv_total_bytes = 1337;
  rtp_connection_info.sent_total_packets = 3;
  rtp_connection_info.sent_discarded_packets = 2;
  rtp_connection_info.packets_received = 4;
  cricket::TransportChannelStats rtp_transport_channel_stats;
  rtp_transport_channel_stats.component = cricket::ICE_CANDIDATE_COMPONENT_RTP;
  rtp_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      rtp_connection_info);
  rtp_transport_channel_stats.dtls_state = DtlsTransportState::kNew;
  rtp_transport_channel_stats.ice_transport_stats.bytes_sent = 42;
  rtp_transport_channel_stats.ice_transport_stats.packets_sent = 1;
  rtp_transport_channel_stats.ice_transport_stats.bytes_received = 1337;
  rtp_transport_channel_stats.ice_transport_stats.packets_received = 4;
  rtp_transport_channel_stats.ice_transport_stats
      .selected_candidate_pair_changes = 1;
  rtp_transport_channel_stats.ice_transport_stats.ice_local_username_fragment =
      "thelocalufrag";
  pc_->SetTransportStats(kTransportName, {rtp_transport_channel_stats});

  // Get stats without RTCP, an active connection or certificates.
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCTransportStats expected_rtp_transport(
      "Ttransport" + rtc::ToString(cricket::ICE_CANDIDATE_COMPONENT_RTP),
      report->timestamp());
  expected_rtp_transport.bytes_sent = 42;
  expected_rtp_transport.packets_sent = 1;
  expected_rtp_transport.bytes_received = 1337;
  expected_rtp_transport.packets_received = 4;
  expected_rtp_transport.dtls_state = "new";
  expected_rtp_transport.dtls_role = "unknown";
  expected_rtp_transport.selected_candidate_pair_changes = 1;
  expected_rtp_transport.ice_role = "unknown";
  expected_rtp_transport.ice_local_username_fragment = "thelocalufrag";
  expected_rtp_transport.ice_state = "new";

  ASSERT_TRUE(report->Get(expected_rtp_transport.id()));
  EXPECT_EQ(
      expected_rtp_transport,
      report->Get(expected_rtp_transport.id())->cast_to<RTCTransportStats>());

  cricket::ConnectionInfo rtcp_connection_info;
  rtcp_connection_info.best_connection = false;
  rtcp_connection_info.local_candidate = *rtcp_local_candidate.get();
  rtcp_connection_info.remote_candidate = *rtcp_remote_candidate.get();
  rtcp_connection_info.sent_total_bytes = 1337;
  rtcp_connection_info.recv_total_bytes = 42;
  rtcp_connection_info.sent_total_packets = 3;
  rtcp_connection_info.sent_discarded_packets = 2;
  rtcp_connection_info.packets_received = 4;
  cricket::TransportChannelStats rtcp_transport_channel_stats;
  rtcp_transport_channel_stats.component =
      cricket::ICE_CANDIDATE_COMPONENT_RTCP;
  rtcp_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      rtcp_connection_info);
  rtcp_transport_channel_stats.dtls_state = DtlsTransportState::kConnecting;
  rtcp_transport_channel_stats.ice_transport_stats.bytes_sent = 1337;
  rtcp_transport_channel_stats.ice_transport_stats.packets_sent = 1;
  rtcp_transport_channel_stats.ice_transport_stats.bytes_received = 42;
  rtcp_transport_channel_stats.ice_transport_stats.packets_received = 4;
  rtcp_transport_channel_stats.ice_transport_stats.ice_local_username_fragment =
      "thelocalufrag";
  rtcp_transport_channel_stats.ice_transport_stats.ice_state =
      IceTransportState::kChecking;
  pc_->SetTransportStats(kTransportName, {rtp_transport_channel_stats,
                                          rtcp_transport_channel_stats});

  // Get stats with RTCP and without an active connection or certificates.
  report = stats_->GetFreshStatsReport();

  RTCTransportStats expected_rtcp_transport(
      "Ttransport" + rtc::ToString(cricket::ICE_CANDIDATE_COMPONENT_RTCP),
      report->timestamp());
  expected_rtcp_transport.bytes_sent = 1337;
  expected_rtcp_transport.packets_sent = 1;
  expected_rtcp_transport.bytes_received = 42;
  expected_rtcp_transport.packets_received = 4;
  expected_rtcp_transport.dtls_state = "connecting";
  expected_rtcp_transport.dtls_role = "unknown";
  expected_rtcp_transport.selected_candidate_pair_changes = 0;
  expected_rtcp_transport.ice_role = "unknown";
  expected_rtcp_transport.ice_local_username_fragment = "thelocalufrag";
  expected_rtcp_transport.ice_state = "checking";

  expected_rtp_transport.rtcp_transport_stats_id = expected_rtcp_transport.id();
  ASSERT_TRUE(report->Get(expected_rtp_transport.id()));
  EXPECT_EQ(
      expected_rtp_transport,
      report->Get(expected_rtp_transport.id())->cast_to<RTCTransportStats>());
  ASSERT_TRUE(report->Get(expected_rtcp_transport.id()));
  EXPECT_EQ(
      expected_rtcp_transport,
      report->Get(expected_rtcp_transport.id())->cast_to<RTCTransportStats>());

  // Get stats with an active connection (selected candidate pair).
  rtcp_transport_channel_stats.ice_transport_stats.connection_infos[0]
      .best_connection = true;
  pc_->SetTransportStats(kTransportName, {rtp_transport_channel_stats,
                                          rtcp_transport_channel_stats});

  report = stats_->GetFreshStatsReport();

  expected_rtcp_transport.selected_candidate_pair_id =
      "CP" + rtcp_local_candidate->id() + "_" + rtcp_remote_candidate->id();

  ASSERT_TRUE(report->Get(expected_rtp_transport.id()));
  EXPECT_EQ(
      expected_rtp_transport,
      report->Get(expected_rtp_transport.id())->cast_to<RTCTransportStats>());
  ASSERT_TRUE(report->Get(expected_rtcp_transport.id()));
  EXPECT_EQ(
      expected_rtcp_transport,
      report->Get(expected_rtcp_transport.id())->cast_to<RTCTransportStats>());

  // Get stats with certificates.
  std::unique_ptr<CertificateInfo> local_certinfo =
      CreateFakeCertificateAndInfoFromDers({"(local) local", "(local) chain"});
  pc_->SetLocalCertificate(kTransportName, local_certinfo->certificate);
  std::unique_ptr<CertificateInfo> remote_certinfo =
      CreateFakeCertificateAndInfoFromDers(
          {"(remote) local", "(remote) chain"});
  pc_->SetRemoteCertChain(
      kTransportName,
      remote_certinfo->certificate->GetSSLCertificateChain().Clone());

  report = stats_->GetFreshStatsReport();

  expected_rtp_transport.local_certificate_id =
      "CF" + local_certinfo->fingerprints[0];
  expected_rtp_transport.remote_certificate_id =
      "CF" + remote_certinfo->fingerprints[0];

  expected_rtcp_transport.local_certificate_id =
      *expected_rtp_transport.local_certificate_id;
  expected_rtcp_transport.remote_certificate_id =
      *expected_rtp_transport.remote_certificate_id;

  ASSERT_TRUE(report->Get(expected_rtp_transport.id()));
  EXPECT_EQ(
      expected_rtp_transport,
      report->Get(expected_rtp_transport.id())->cast_to<RTCTransportStats>());
  ASSERT_TRUE(report->Get(expected_rtcp_transport.id()));
  EXPECT_EQ(
      expected_rtcp_transport,
      report->Get(expected_rtcp_transport.id())->cast_to<RTCTransportStats>());
}

TEST_F(RTCStatsCollectorTest, CollectRTCTransportStatsWithCrypto) {
  const char kTransportName[] = "transport";

  pc_->AddVoiceChannel("audio", kTransportName);

  std::unique_ptr<cricket::Candidate> rtp_local_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_WIFI,
                          cricket::LOCAL_PORT_TYPE, 42);
  std::unique_ptr<cricket::Candidate> rtp_remote_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol",
                          rtc::ADAPTER_TYPE_UNKNOWN, cricket::LOCAL_PORT_TYPE,
                          42);
  std::unique_ptr<cricket::Candidate> rtcp_local_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol", rtc::ADAPTER_TYPE_WIFI,
                          cricket::LOCAL_PORT_TYPE, 42);
  std::unique_ptr<cricket::Candidate> rtcp_remote_candidate =
      CreateFakeCandidate("42.42.42.42", 42, "protocol",
                          rtc::ADAPTER_TYPE_UNKNOWN, cricket::LOCAL_PORT_TYPE,
                          42);

  cricket::ConnectionInfo rtp_connection_info;
  rtp_connection_info.best_connection = false;
  rtp_connection_info.local_candidate = *rtp_local_candidate.get();
  rtp_connection_info.remote_candidate = *rtp_remote_candidate.get();
  cricket::TransportChannelStats rtp_transport_channel_stats;
  rtp_transport_channel_stats.component = cricket::ICE_CANDIDATE_COMPONENT_RTP;
  rtp_transport_channel_stats.ice_transport_stats.connection_infos.push_back(
      rtp_connection_info);
  // The state must be connected in order for crypto parameters to show up.
  rtp_transport_channel_stats.dtls_state = DtlsTransportState::kConnected;
  rtp_transport_channel_stats.ice_transport_stats
      .selected_candidate_pair_changes = 1;
  rtp_transport_channel_stats.ssl_version_bytes = 0x0203;
  rtp_transport_channel_stats.dtls_role = rtc::SSL_CLIENT;
  rtp_transport_channel_stats.ice_transport_stats.ice_role =
      cricket::ICEROLE_CONTROLLING;
  rtp_transport_channel_stats.ice_transport_stats.ice_local_username_fragment =
      "thelocalufrag";
  rtp_transport_channel_stats.ice_transport_stats.ice_state =
      IceTransportState::kConnected;
  // 0x2F is TLS_RSA_WITH_AES_128_CBC_SHA according to IANA
  rtp_transport_channel_stats.ssl_cipher_suite = 0x2F;
  rtp_transport_channel_stats.srtp_crypto_suite = rtc::kSrtpAes128CmSha1_80;
  pc_->SetTransportStats(kTransportName, {rtp_transport_channel_stats});

  // Get stats
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCTransportStats expected_rtp_transport(
      "Ttransport" + rtc::ToString(cricket::ICE_CANDIDATE_COMPONENT_RTP),
      report->timestamp());
  expected_rtp_transport.dtls_state = "connected";
  expected_rtp_transport.selected_candidate_pair_changes = 1;
  expected_rtp_transport.ice_role = "unknown";
  expected_rtp_transport.bytes_sent = 0;
  expected_rtp_transport.bytes_received = 0;
  expected_rtp_transport.packets_sent = 0;
  expected_rtp_transport.packets_received = 0;
  expected_rtp_transport.ice_role = "controlling";
  expected_rtp_transport.ice_local_username_fragment = "thelocalufrag";
  expected_rtp_transport.ice_state = "connected";
  // Crypto parameters
  expected_rtp_transport.tls_version = "0203";
  expected_rtp_transport.dtls_role = "client";
  expected_rtp_transport.dtls_cipher = "TLS_RSA_WITH_AES_128_CBC_SHA";
  expected_rtp_transport.srtp_cipher = "AES_CM_128_HMAC_SHA1_80";

  ASSERT_TRUE(report->Get(expected_rtp_transport.id()));
  EXPECT_EQ(
      expected_rtp_transport,
      report->Get(expected_rtp_transport.id())->cast_to<RTCTransportStats>());
}

TEST_F(RTCStatsCollectorTest, CollectNoStreamRTCOutboundRtpStreamStats_Audio) {
  cricket::VoiceMediaInfo voice_media_info;

  voice_media_info.senders.push_back(cricket::VoiceSenderInfo());
  voice_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  voice_media_info.senders[0].local_stats[0].ssrc = 1;
  voice_media_info.senders[0].packets_sent = 2;
  voice_media_info.senders[0].total_packet_send_delay = TimeDelta::Seconds(0.5);
  voice_media_info.senders[0].retransmitted_packets_sent = 20;
  voice_media_info.senders[0].payload_bytes_sent = 3;
  voice_media_info.senders[0].header_and_padding_bytes_sent = 4;
  voice_media_info.senders[0].retransmitted_bytes_sent = 30;
  voice_media_info.senders[0].nacks_received = 31;
  voice_media_info.senders[0].codec_payload_type = 42;
  voice_media_info.senders[0].active = true;

  RtpCodecParameters codec_parameters;
  codec_parameters.payload_type = 42;
  codec_parameters.kind = cricket::MEDIA_TYPE_AUDIO;
  codec_parameters.name = "dummy";
  codec_parameters.clock_rate = 0;
  voice_media_info.send_codecs.insert(
      std::make_pair(codec_parameters.payload_type, codec_parameters));

  // Emulates the case where AddTrack is used without an associated MediaStream
  pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  stats_->SetupLocalTrackAndSender(cricket::MEDIA_TYPE_AUDIO,
                                   "LocalAudioTrackID", 1, false,
                                   /*attachment_id=*/50);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCOutboundRtpStreamStats expected_audio("OTTransportName1A1",
                                           report->timestamp());
  expected_audio.media_source_id = "SA50";
  expected_audio.mid = "AudioMid";
  expected_audio.ssrc = 1;
  expected_audio.kind = "audio";
  expected_audio.transport_id = "TTransportName1";
  expected_audio.codec_id = "COTTransportName1_42";
  expected_audio.packets_sent = 2;
  expected_audio.total_packet_send_delay = 0.5;
  expected_audio.retransmitted_packets_sent = 20;
  expected_audio.bytes_sent = 3;
  expected_audio.header_bytes_sent = 4;
  expected_audio.retransmitted_bytes_sent = 30;
  expected_audio.nack_count = 31;
  expected_audio.active = true;

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(
      report->Get(expected_audio.id())->cast_to<RTCOutboundRtpStreamStats>(),
      expected_audio);
  EXPECT_TRUE(report->Get(*expected_audio.transport_id));
  EXPECT_TRUE(report->Get(*expected_audio.codec_id));
}

TEST_F(RTCStatsCollectorTest, RTCAudioSourceStatsCollectedForSenderWithTrack) {
  const uint32_t kSsrc = 4;
  const int kAttachmentId = 42;

  cricket::VoiceMediaInfo voice_media_info;
  voice_media_info.senders.push_back(cricket::VoiceSenderInfo());
  voice_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  voice_media_info.senders[0].local_stats[0].ssrc = kSsrc;
  voice_media_info.senders[0].audio_level = 32767;  // [0,32767]
  voice_media_info.senders[0].total_input_energy = 2.0;
  voice_media_info.senders[0].total_input_duration = 3.0;
  voice_media_info.senders[0].apm_statistics.echo_return_loss = 42.0;
  voice_media_info.senders[0].apm_statistics.echo_return_loss_enhancement =
      52.0;
  pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  stats_->SetupLocalTrackAndSender(cricket::MEDIA_TYPE_AUDIO,
                                   "LocalAudioTrackID", kSsrc, false,
                                   kAttachmentId);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCAudioSourceStats expected_audio("SA42", report->timestamp());
  expected_audio.track_identifier = "LocalAudioTrackID";
  expected_audio.kind = "audio";
  expected_audio.audio_level = 1.0;  // [0,1]
  expected_audio.total_audio_energy = 2.0;
  expected_audio.total_samples_duration = 3.0;
  expected_audio.echo_return_loss = 42.0;
  expected_audio.echo_return_loss_enhancement = 52.0;

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(report->Get(expected_audio.id())->cast_to<RTCAudioSourceStats>(),
            expected_audio);
}

TEST_F(RTCStatsCollectorTest, RTCVideoSourceStatsCollectedForSenderWithTrack) {
  const uint32_t kSsrc = 4;
  const int kAttachmentId = 42;
  const int kVideoSourceWidth = 12;
  const int kVideoSourceHeight = 34;

  cricket::VideoMediaInfo video_media_info;
  video_media_info.aggregated_senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  video_media_info.senders[0].local_stats[0].ssrc = kSsrc;
  video_media_info.senders[0].framerate_input = 29.0;
  video_media_info.aggregated_senders[0].local_stats.push_back(
      cricket::SsrcSenderInfo());
  video_media_info.aggregated_senders[0].local_stats[0].ssrc = kSsrc;
  video_media_info.aggregated_senders[0].framerate_input = 29.0;
  video_media_info.aggregated_senders[0].frames = 10001;
  pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);

  auto video_source = FakeVideoTrackSourceForStats::Create(kVideoSourceWidth,
                                                           kVideoSourceHeight);
  auto video_track = FakeVideoTrackForStats::Create(
      "LocalVideoTrackID", MediaStreamTrackInterface::kLive, video_source);
  rtc::scoped_refptr<MockRtpSenderInternal> sender = CreateMockSender(
      cricket::MEDIA_TYPE_VIDEO, video_track, kSsrc, kAttachmentId, {});
  EXPECT_CALL(*sender, Stop());
  EXPECT_CALL(*sender, SetMediaChannel(_));
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCVideoSourceStats expected_video("SV42", report->timestamp());
  expected_video.track_identifier = "LocalVideoTrackID";
  expected_video.kind = "video";
  expected_video.width = kVideoSourceWidth;
  expected_video.height = kVideoSourceHeight;
  expected_video.frames_per_second = 29.0;
  expected_video.frames = 10001;

  ASSERT_TRUE(report->Get(expected_video.id()));
  EXPECT_EQ(report->Get(expected_video.id())->cast_to<RTCVideoSourceStats>(),
            expected_video);
}

// This test exercises the current behavior and code path, but the correct
// behavior is to report frame rate even if we have no SSRC.
// TODO(hbos): When we know the frame rate even if we have no SSRC, update the
// expectations of this test.
TEST_F(RTCStatsCollectorTest,
       RTCVideoSourceStatsMissingFrameRateWhenSenderHasNoSsrc) {
  // TODO(https://crbug.com/webrtc/8694): When 0 is no longer a magic value for
  // "none", update this test.
  const uint32_t kNoSsrc = 0;
  const int kAttachmentId = 42;
  const int kVideoSourceWidth = 12;
  const int kVideoSourceHeight = 34;

  cricket::VideoMediaInfo video_media_info;
  video_media_info.senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  video_media_info.senders[0].framerate_input = 29.0;
  pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);

  auto video_source = FakeVideoTrackSourceForStats::Create(kVideoSourceWidth,
                                                           kVideoSourceHeight);
  auto video_track = FakeVideoTrackForStats::Create(
      "LocalVideoTrackID", MediaStreamTrackInterface::kLive, video_source);
  rtc::scoped_refptr<MockRtpSenderInternal> sender = CreateMockSender(
      cricket::MEDIA_TYPE_VIDEO, video_track, kNoSsrc, kAttachmentId, {});
  EXPECT_CALL(*sender, Stop());
  EXPECT_CALL(*sender, SetMediaChannel(_));
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  ASSERT_TRUE(report->Get("SV42"));
  auto video_stats = report->Get("SV42")->cast_to<RTCVideoSourceStats>();
  EXPECT_FALSE(video_stats.frames_per_second.has_value());
  EXPECT_FALSE(video_stats.frames.has_value());
}

// The track not having a source is not expected to be true in practise, but
// this is true in some tests relying on fakes. This test covers that code path.
TEST_F(RTCStatsCollectorTest,
       RTCVideoSourceStatsMissingResolutionWhenTrackHasNoSource) {
  const uint32_t kSsrc = 4;
  const int kAttachmentId = 42;

  cricket::VideoMediaInfo video_media_info;
  video_media_info.senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  video_media_info.senders[0].local_stats[0].ssrc = kSsrc;
  video_media_info.senders[0].framerate_input = 29.0;
  pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);

  auto video_track = FakeVideoTrackForStats::Create(
      "LocalVideoTrackID", MediaStreamTrackInterface::kLive,
      /*source=*/nullptr);
  rtc::scoped_refptr<MockRtpSenderInternal> sender = CreateMockSender(
      cricket::MEDIA_TYPE_VIDEO, video_track, kSsrc, kAttachmentId, {});
  EXPECT_CALL(*sender, Stop());
  EXPECT_CALL(*sender, SetMediaChannel(_));
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  ASSERT_TRUE(report->Get("SV42"));
  auto video_stats = report->Get("SV42")->cast_to<RTCVideoSourceStats>();
  EXPECT_FALSE(video_stats.width.has_value());
  EXPECT_FALSE(video_stats.height.has_value());
}

TEST_F(RTCStatsCollectorTest,
       RTCAudioSourceStatsNotCollectedForSenderWithoutTrack) {
  const uint32_t kSsrc = 4;
  const int kAttachmentId = 42;

  cricket::VoiceMediaInfo voice_media_info;
  voice_media_info.senders.push_back(cricket::VoiceSenderInfo());
  voice_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  voice_media_info.senders[0].local_stats[0].ssrc = kSsrc;
  pc_->AddVoiceChannel("AudioMid", "TransportName", voice_media_info);
  rtc::scoped_refptr<MockRtpSenderInternal> sender = CreateMockSender(
      cricket::MEDIA_TYPE_AUDIO, /*track=*/nullptr, kSsrc, kAttachmentId, {});
  EXPECT_CALL(*sender, Stop());
  EXPECT_CALL(*sender, SetMediaChannel(_));
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  EXPECT_FALSE(report->Get("SA42"));
}

// Parameterized tests on cricket::MediaType (audio or video).
class RTCStatsCollectorTestWithParamKind
    : public RTCStatsCollectorTest,
      public ::testing::WithParamInterface<cricket::MediaType> {
 public:
  RTCStatsCollectorTestWithParamKind() : media_type_(GetParam()) {
    RTC_DCHECK(media_type_ == cricket::MEDIA_TYPE_AUDIO ||
               media_type_ == cricket::MEDIA_TYPE_VIDEO);
  }

  std::string MediaTypeCharStr() const {
    switch (media_type_) {
      case cricket::MEDIA_TYPE_AUDIO:
        return "A";
      case cricket::MEDIA_TYPE_VIDEO:
        return "V";
      case cricket::MEDIA_TYPE_DATA:
      case cricket::MEDIA_TYPE_UNSUPPORTED:
        RTC_DCHECK_NOTREACHED();
        return "?";
    }
  }

  std::string MediaTypeKind() const {
    switch (media_type_) {
      case cricket::MEDIA_TYPE_AUDIO:
        return "audio";
      case cricket::MEDIA_TYPE_VIDEO:
        return "video";
      case cricket::MEDIA_TYPE_DATA:
      case cricket::MEDIA_TYPE_UNSUPPORTED:
        RTC_DCHECK_NOTREACHED();
        return "";
    }
  }

  // Adds a sender and channel of the appropriate kind, creating a sender info
  // with the report block's `source_ssrc` and report block data.
  void AddSenderInfoAndMediaChannel(
      std::string transport_name,
      const std::vector<ReportBlockData>& report_block_datas,
      absl::optional<RtpCodecParameters> codec) {
    switch (media_type_) {
      case cricket::MEDIA_TYPE_AUDIO: {
        cricket::VoiceMediaInfo voice_media_info;
        for (const auto& report_block_data : report_block_datas) {
          cricket::VoiceSenderInfo sender;
          sender.local_stats.push_back(cricket::SsrcSenderInfo());
          sender.local_stats[0].ssrc = report_block_data.source_ssrc();
          if (codec.has_value()) {
            sender.codec_payload_type = codec->payload_type;
            voice_media_info.send_codecs.insert(
                std::make_pair(codec->payload_type, *codec));
          }
          sender.report_block_datas.push_back(report_block_data);
          voice_media_info.senders.push_back(sender);
        }
        pc_->AddVoiceChannel("mid", transport_name, voice_media_info);
        return;
      }
      case cricket::MEDIA_TYPE_VIDEO: {
        cricket::VideoMediaInfo video_media_info;
        for (const auto& report_block_data : report_block_datas) {
          cricket::VideoSenderInfo sender;
          sender.local_stats.push_back(cricket::SsrcSenderInfo());
          sender.local_stats[0].ssrc = report_block_data.source_ssrc();
          if (codec.has_value()) {
            sender.codec_payload_type = codec->payload_type;
            video_media_info.send_codecs.insert(
                std::make_pair(codec->payload_type, *codec));
          }
          sender.report_block_datas.push_back(report_block_data);
          video_media_info.aggregated_senders.push_back(sender);
          video_media_info.senders.push_back(sender);
        }
        pc_->AddVideoChannel("mid", transport_name, video_media_info);
        return;
      }
      case cricket::MEDIA_TYPE_DATA:
      case cricket::MEDIA_TYPE_UNSUPPORTED:
        RTC_DCHECK_NOTREACHED();
    }
  }

 protected:
  cricket::MediaType media_type_;
};

// Verifies RTCRemoteInboundRtpStreamStats members that don't require
// RTCCodecStats (codecId, jitter) and without setting up an RTCP transport.
TEST_P(RTCStatsCollectorTestWithParamKind,
       RTCRemoteInboundRtpStreamStatsCollectedFromReportBlock) {
  const Timestamp kReportBlockTimestampUtc = Timestamp::Micros(123456789);
  const uint8_t kFractionLost = 12;
  const TimeDelta kRoundTripTimeSample1 = TimeDelta::Millis(1'234);
  const TimeDelta kRoundTripTimeSample2 = TimeDelta::Seconds(13);

  // The report block's timestamp cannot be from the future, set the fake clock
  // to match.
  fake_clock_.SetTime(kReportBlockTimestampUtc);
  auto ssrcs = {12, 13};
  std::vector<ReportBlockData> report_block_datas;
  for (auto ssrc : ssrcs) {
    rtcp::ReportBlock report_block;
    // The remote-inbound-rtp SSRC and the outbound-rtp SSRC is the same as the
    // `source_ssrc`, "SSRC of the RTP packet sender".
    report_block.SetMediaSsrc(ssrc);
    report_block.SetCumulativeLost(7);
    report_block.SetFractionLost(kFractionLost);
    ReportBlockData report_block_data;
    report_block_data.SetReportBlock(0, report_block, kReportBlockTimestampUtc);
    report_block_data.AddRoundTripTimeSample(kRoundTripTimeSample1);
    // Only the last sample should be exposed as the
    // `RTCRemoteInboundRtpStreamStats::round_trip_time`.
    report_block_data.AddRoundTripTimeSample(kRoundTripTimeSample2);
    report_block_datas.push_back(report_block_data);
  }
  AddSenderInfoAndMediaChannel("TransportName", report_block_datas,
                               absl::nullopt);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  for (auto ssrc : ssrcs) {
    std::string stream_id = "" + std::to_string(ssrc);
    RTCRemoteInboundRtpStreamStats expected_remote_inbound_rtp(
        "RI" + MediaTypeCharStr() + stream_id, kReportBlockTimestampUtc);
    expected_remote_inbound_rtp.ssrc = ssrc;
    expected_remote_inbound_rtp.fraction_lost =
        static_cast<double>(kFractionLost) / (1 << 8);
    expected_remote_inbound_rtp.kind = MediaTypeKind();
    expected_remote_inbound_rtp.transport_id =
        "TTransportName1";  // 1 for RTP (we have no RTCP
                            // transport)
    expected_remote_inbound_rtp.packets_lost = 7;
    expected_remote_inbound_rtp.local_id =
        "OTTransportName1" + MediaTypeCharStr() + stream_id;
    expected_remote_inbound_rtp.round_trip_time =
        kRoundTripTimeSample2.seconds<double>();
    expected_remote_inbound_rtp.total_round_trip_time =
        (kRoundTripTimeSample1 + kRoundTripTimeSample2).seconds<double>();
    expected_remote_inbound_rtp.round_trip_time_measurements = 2;
    // This test does not set up RTCCodecStats, so `codec_id` and `jitter` are
    // expected to be missing. These are tested separately.

    ASSERT_TRUE(report->Get(expected_remote_inbound_rtp.id()));
    EXPECT_EQ(report->Get(expected_remote_inbound_rtp.id())
                  ->cast_to<RTCRemoteInboundRtpStreamStats>(),
              expected_remote_inbound_rtp);
    EXPECT_TRUE(report->Get(*expected_remote_inbound_rtp.transport_id));
    ASSERT_TRUE(report->Get(*expected_remote_inbound_rtp.local_id));
    // Lookup works in both directions.
    EXPECT_EQ(*report->Get(*expected_remote_inbound_rtp.local_id)
                   ->cast_to<RTCOutboundRtpStreamStats>()
                   .remote_id,
              expected_remote_inbound_rtp.id());
  }
}

TEST_P(RTCStatsCollectorTestWithParamKind,
       RTCRemoteInboundRtpStreamStatsRttMissingBeforeMeasurement) {
  constexpr Timestamp kReportBlockTimestampUtc = Timestamp::Micros(123456789);

  rtcp::ReportBlock report_block;
  // The remote-inbound-rtp SSRC and the outbound-rtp SSRC is the same as the
  // `source_ssrc`, "SSRC of the RTP packet sender".
  report_block.SetMediaSsrc(12);
  ReportBlockData report_block_data;  // AddRoundTripTimeSample() not called.
  report_block_data.SetReportBlock(0, report_block, kReportBlockTimestampUtc);

  AddSenderInfoAndMediaChannel("TransportName", {report_block_data},
                               absl::nullopt);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  std::string remote_inbound_rtp_id = "RI" + MediaTypeCharStr() + "12";
  ASSERT_TRUE(report->Get(remote_inbound_rtp_id));
  auto& remote_inbound_rtp = report->Get(remote_inbound_rtp_id)
                                 ->cast_to<RTCRemoteInboundRtpStreamStats>();

  EXPECT_TRUE(remote_inbound_rtp.round_trip_time_measurements.has_value());
  EXPECT_EQ(0, *remote_inbound_rtp.round_trip_time_measurements);
  EXPECT_FALSE(remote_inbound_rtp.round_trip_time.has_value());
}

TEST_P(RTCStatsCollectorTestWithParamKind,
       RTCRemoteInboundRtpStreamStatsWithTimestampFromReportBlock) {
  const Timestamp kReportBlockTimestampUtc = Timestamp::Micros(123456789);
  fake_clock_.SetTime(kReportBlockTimestampUtc);

  rtcp::ReportBlock report_block;
  // The remote-inbound-rtp SSRC and the outbound-rtp SSRC is the same as the
  // `source_ssrc`, "SSRC of the RTP packet sender".
  report_block.SetMediaSsrc(12);
  ReportBlockData report_block_data;
  report_block_data.SetReportBlock(0, report_block, kReportBlockTimestampUtc);

  AddSenderInfoAndMediaChannel("TransportName", {report_block_data},
                               absl::nullopt);

  // Advance time, it should be OK to have fresher reports than report blocks.
  fake_clock_.AdvanceTime(TimeDelta::Micros(1234));

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  std::string remote_inbound_rtp_id = "RI" + MediaTypeCharStr() + "12";
  ASSERT_TRUE(report->Get(remote_inbound_rtp_id));
  auto& remote_inbound_rtp = report->Get(remote_inbound_rtp_id)
                                 ->cast_to<RTCRemoteInboundRtpStreamStats>();

  // Even though the report time is different, the remote-inbound-rtp timestamp
  // is of the time that the report block was received.
  EXPECT_EQ(report->timestamp(),
            kReportBlockTimestampUtc + TimeDelta::Micros(1234));
  EXPECT_EQ(remote_inbound_rtp.timestamp(), kReportBlockTimestampUtc);
}

TEST_P(RTCStatsCollectorTestWithParamKind,
       RTCRemoteInboundRtpStreamStatsWithCodecBasedMembers) {
  const Timestamp kReportBlockTimestampUtc = Timestamp::Micros(123456789);
  fake_clock_.SetTime(kReportBlockTimestampUtc);

  rtcp::ReportBlock report_block;
  // The remote-inbound-rtp SSRC and the outbound-rtp SSRC is the same as the
  // `source_ssrc`, "SSRC of the RTP packet sender".
  report_block.SetMediaSsrc(12);
  report_block.SetJitter(5000);
  ReportBlockData report_block_data;
  report_block_data.SetReportBlock(0, report_block, kReportBlockTimestampUtc);

  RtpCodecParameters codec;
  codec.payload_type = 3;
  codec.kind = media_type_;
  codec.clock_rate = 1000;

  AddSenderInfoAndMediaChannel("TransportName", {report_block_data}, codec);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  std::string remote_inbound_rtp_id = "RI" + MediaTypeCharStr() + "12";
  ASSERT_TRUE(report->Get(remote_inbound_rtp_id));
  auto& remote_inbound_rtp = report->Get(remote_inbound_rtp_id)
                                 ->cast_to<RTCRemoteInboundRtpStreamStats>();

  EXPECT_TRUE(remote_inbound_rtp.codec_id.has_value());
  EXPECT_TRUE(report->Get(*remote_inbound_rtp.codec_id));

  EXPECT_TRUE(remote_inbound_rtp.jitter.has_value());
  // The jitter (in seconds) is the report block's jitter divided by the codec's
  // clock rate.
  EXPECT_EQ(5.0, *remote_inbound_rtp.jitter);
}

TEST_P(RTCStatsCollectorTestWithParamKind,
       RTCRemoteInboundRtpStreamStatsWithRtcpTransport) {
  const Timestamp kReportBlockTimestampUtc = Timestamp::Micros(123456789);
  fake_clock_.SetTime(kReportBlockTimestampUtc);

  rtcp::ReportBlock report_block;
  // The remote-inbound-rtp SSRC and the outbound-rtp SSRC is the same as the
  // `source_ssrc`, "SSRC of the RTP packet sender".
  report_block.SetMediaSsrc(12);
  ReportBlockData report_block_data;
  report_block_data.SetReportBlock(0, report_block, kReportBlockTimestampUtc);

  cricket::TransportChannelStats rtp_transport_channel_stats;
  rtp_transport_channel_stats.component = cricket::ICE_CANDIDATE_COMPONENT_RTP;
  rtp_transport_channel_stats.dtls_state = DtlsTransportState::kNew;
  cricket::TransportChannelStats rtcp_transport_channel_stats;
  rtcp_transport_channel_stats.component =
      cricket::ICE_CANDIDATE_COMPONENT_RTCP;
  rtcp_transport_channel_stats.dtls_state = DtlsTransportState::kNew;
  pc_->SetTransportStats("TransportName", {rtp_transport_channel_stats,
                                           rtcp_transport_channel_stats});
  AddSenderInfoAndMediaChannel("TransportName", {report_block_data},
                               absl::nullopt);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  std::string remote_inbound_rtp_id = "RI" + MediaTypeCharStr() + "12";
  ASSERT_TRUE(report->Get(remote_inbound_rtp_id));
  auto& remote_inbound_rtp = report->Get(remote_inbound_rtp_id)
                                 ->cast_to<RTCRemoteInboundRtpStreamStats>();

  EXPECT_TRUE(remote_inbound_rtp.transport_id.has_value());
  EXPECT_EQ("TTransportName2",  // 2 for RTCP
            *remote_inbound_rtp.transport_id);
  EXPECT_TRUE(report->Get(*remote_inbound_rtp.transport_id));
}

INSTANTIATE_TEST_SUITE_P(All,
                         RTCStatsCollectorTestWithParamKind,
                         ::testing::Values(cricket::MEDIA_TYPE_AUDIO,    // "/0"
                                           cricket::MEDIA_TYPE_VIDEO));  // "/1"

// Checks that no remote outbound stats are collected if not available in
// `VoiceMediaInfo`.
TEST_F(RTCStatsCollectorTest,
       RTCRemoteOutboundRtpAudioStreamStatsNotCollected) {
  ExampleStatsGraph graph =
      SetupExampleStatsVoiceGraph(/*add_remote_outbound_stats=*/false);
  EXPECT_FALSE(graph.full_report->Get(graph.remote_outbound_rtp_id));
  // Also check that no other remote outbound report is created (in case the
  // expected ID is incorrect).
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  ASSERT_NE(report->begin(), report->end())
      << "No reports have been generated.";
  for (const auto& stats : *report) {
    SCOPED_TRACE(stats.id());
    EXPECT_NE(stats.type(), RTCRemoteOutboundRtpStreamStats::kType);
  }
}

// Checks that the remote outbound stats are collected when available in
// `VoiceMediaInfo`.
TEST_F(RTCStatsCollectorTest, RTCRemoteOutboundRtpAudioStreamStatsCollected) {
  ExampleStatsGraph graph =
      SetupExampleStatsVoiceGraph(/*add_remote_outbound_stats=*/true);
  ASSERT_TRUE(graph.full_report->Get(graph.remote_outbound_rtp_id));
  const auto& remote_outbound_rtp =
      graph.full_report->Get(graph.remote_outbound_rtp_id)
          ->cast_to<RTCRemoteOutboundRtpStreamStats>();
  EXPECT_EQ(remote_outbound_rtp.timestamp(),
            Timestamp::Millis(kRemoteOutboundStatsTimestampMs));
  EXPECT_FLOAT_EQ(*remote_outbound_rtp.remote_timestamp,
                  static_cast<double>(kRemoteOutboundStatsRemoteTimestampMs));
  EXPECT_EQ(*remote_outbound_rtp.packets_sent, kRemoteOutboundStatsPacketsSent);
  EXPECT_EQ(*remote_outbound_rtp.bytes_sent, kRemoteOutboundStatsBytesSent);
  EXPECT_EQ(*remote_outbound_rtp.reports_sent,
            kRemoteOutboundStatsReportsCount);
}

TEST_F(RTCStatsCollectorTest,
       RTCVideoSourceStatsNotCollectedForSenderWithoutTrack) {
  const uint32_t kSsrc = 4;
  const int kAttachmentId = 42;

  cricket::VideoMediaInfo video_media_info;
  video_media_info.senders.push_back(cricket::VideoSenderInfo());
  video_media_info.senders[0].local_stats.push_back(cricket::SsrcSenderInfo());
  video_media_info.senders[0].local_stats[0].ssrc = kSsrc;
  video_media_info.senders[0].framerate_input = 29.0;
  pc_->AddVideoChannel("VideoMid", "TransportName", video_media_info);

  rtc::scoped_refptr<MockRtpSenderInternal> sender = CreateMockSender(
      cricket::MEDIA_TYPE_VIDEO, /*track=*/nullptr, kSsrc, kAttachmentId, {});
  EXPECT_CALL(*sender, Stop());
  EXPECT_CALL(*sender, SetMediaChannel(_));
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();
  EXPECT_FALSE(report->Get("SV42"));
}

// Test collecting echo return loss stats from the audio processor attached to
// the track, rather than the voice sender info.
TEST_F(RTCStatsCollectorTest, CollectEchoReturnLossFromTrackAudioProcessor) {
  rtc::scoped_refptr<MediaStream> local_stream =
      MediaStream::Create("LocalStreamId");
  pc_->mutable_local_streams()->AddStream(local_stream);

  // Local audio track
  rtc::scoped_refptr<MediaStreamTrackInterface> local_audio_track =
      CreateFakeTrack(cricket::MEDIA_TYPE_AUDIO, "LocalAudioTrackID",
                      MediaStreamTrackInterface::kEnded,
                      /*create_fake_audio_processor=*/true);
  local_stream->AddTrack(rtc::scoped_refptr<AudioTrackInterface>(
      static_cast<AudioTrackInterface*>(local_audio_track.get())));

  cricket::VoiceSenderInfo voice_sender_info_ssrc1;
  voice_sender_info_ssrc1.local_stats.push_back(cricket::SsrcSenderInfo());
  voice_sender_info_ssrc1.local_stats[0].ssrc = 1;

  stats_->CreateMockRtpSendersReceiversAndChannels(
      {std::make_pair(local_audio_track.get(), voice_sender_info_ssrc1)}, {},
      {}, {}, {local_stream->id()}, {});

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  RTCAudioSourceStats expected_audio("SA11", report->timestamp());
  expected_audio.track_identifier = "LocalAudioTrackID";
  expected_audio.kind = "audio";
  expected_audio.audio_level = 0;
  expected_audio.total_audio_energy = 0;
  expected_audio.total_samples_duration = 0;
  expected_audio.echo_return_loss = 2.0;
  expected_audio.echo_return_loss_enhancement = 3.0;

  ASSERT_TRUE(report->Get(expected_audio.id()));
  EXPECT_EQ(report->Get(expected_audio.id())->cast_to<RTCAudioSourceStats>(),
            expected_audio);
}

TEST_F(RTCStatsCollectorTest, GetStatsWithSenderSelector) {
  ExampleStatsGraph graph = SetupExampleStatsGraphForSelectorTests();
  // Expected stats graph when filtered by sender:
  //
  //  media-source
  //    ^
  //    |
  //    +--------- outbound-rtp
  //                |        |
  //                v        v
  //       codec (send)     transport
  rtc::scoped_refptr<const RTCStatsReport> sender_report =
      stats_->GetStatsReportWithSenderSelector(graph.sender);
  EXPECT_TRUE(sender_report);
  EXPECT_EQ(sender_report->timestamp(), graph.full_report->timestamp());
  EXPECT_EQ(sender_report->size(), 4u);
  EXPECT_TRUE(sender_report->Get(graph.send_codec_id));
  EXPECT_FALSE(sender_report->Get(graph.recv_codec_id));
  EXPECT_TRUE(sender_report->Get(graph.outbound_rtp_id));
  EXPECT_FALSE(sender_report->Get(graph.inbound_rtp_id));
  EXPECT_TRUE(sender_report->Get(graph.transport_id));
  EXPECT_FALSE(sender_report->Get(graph.peer_connection_id));
  EXPECT_TRUE(sender_report->Get(graph.media_source_id));
}

TEST_F(RTCStatsCollectorTest, GetStatsWithReceiverSelector) {
  ExampleStatsGraph graph = SetupExampleStatsGraphForSelectorTests();
  // Expected stats graph when filtered by receiver:
  //
  //
  //
  //                              inbound-rtp
  //                               |       |
  //                               v       v
  //                        transport     codec (recv)
  rtc::scoped_refptr<const RTCStatsReport> receiver_report =
      stats_->GetStatsReportWithReceiverSelector(graph.receiver);
  EXPECT_TRUE(receiver_report);
  EXPECT_EQ(receiver_report->size(), 3u);
  EXPECT_EQ(receiver_report->timestamp(), graph.full_report->timestamp());
  EXPECT_FALSE(receiver_report->Get(graph.send_codec_id));
  EXPECT_TRUE(receiver_report->Get(graph.recv_codec_id));
  EXPECT_FALSE(receiver_report->Get(graph.outbound_rtp_id));
  EXPECT_TRUE(receiver_report->Get(graph.inbound_rtp_id));
  EXPECT_TRUE(receiver_report->Get(graph.transport_id));
  EXPECT_FALSE(receiver_report->Get(graph.peer_connection_id));
  EXPECT_FALSE(receiver_report->Get(graph.media_source_id));
}

TEST_F(RTCStatsCollectorTest, GetStatsWithNullSenderSelector) {
  ExampleStatsGraph graph = SetupExampleStatsGraphForSelectorTests();
  rtc::scoped_refptr<const RTCStatsReport> empty_report =
      stats_->GetStatsReportWithSenderSelector(nullptr);
  EXPECT_TRUE(empty_report);
  EXPECT_EQ(empty_report->timestamp(), graph.full_report->timestamp());
  EXPECT_EQ(empty_report->size(), 0u);
}

TEST_F(RTCStatsCollectorTest, GetStatsWithNullReceiverSelector) {
  ExampleStatsGraph graph = SetupExampleStatsGraphForSelectorTests();
  rtc::scoped_refptr<const RTCStatsReport> empty_report =
      stats_->GetStatsReportWithReceiverSelector(nullptr);
  EXPECT_TRUE(empty_report);
  EXPECT_EQ(empty_report->timestamp(), graph.full_report->timestamp());
  EXPECT_EQ(empty_report->size(), 0u);
}

// Before SetLocalDescription() senders don't have an SSRC.
// To simulate this case we create a mock sender with SSRC=0.
TEST_F(RTCStatsCollectorTest, RtpIsMissingWhileSsrcIsZero) {
  rtc::scoped_refptr<MediaStreamTrackInterface> track =
      CreateFakeTrack(cricket::MEDIA_TYPE_AUDIO, "audioTrack",
                      MediaStreamTrackInterface::kLive);
  rtc::scoped_refptr<MockRtpSenderInternal> sender =
      CreateMockSender(cricket::MEDIA_TYPE_AUDIO, track, 0, 49, {});
  EXPECT_CALL(*sender, Stop());
  pc_->AddSender(sender);

  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  auto outbound_rtps = report->GetStatsOfType<RTCOutboundRtpStreamStats>();
  EXPECT_TRUE(outbound_rtps.empty());
}

// We may also be in a case where the SSRC has been assigned but no
// `voice_sender_info` stats exist yet.
TEST_F(RTCStatsCollectorTest, DoNotCrashIfSsrcIsKnownButInfosAreStillMissing) {
  rtc::scoped_refptr<MediaStreamTrackInterface> track =
      CreateFakeTrack(cricket::MEDIA_TYPE_AUDIO, "audioTrack",
                      MediaStreamTrackInterface::kLive);
  rtc::scoped_refptr<MockRtpSenderInternal> sender =
      CreateMockSender(cricket::MEDIA_TYPE_AUDIO, track, 4711, 49, {});
  EXPECT_CALL(*sender, Stop());
  pc_->AddSender(sender);

  // We do not generate any matching voice_sender_info stats.
  rtc::scoped_refptr<const RTCStatsReport> report = stats_->GetStatsReport();

  auto outbound_rtps = report->GetStatsOfType<RTCOutboundRtpStreamStats>();
  EXPECT_TRUE(outbound_rtps.empty());
}

// Used for test below, to test calling GetStatsReport during a callback.
class RecursiveCallback : public RTCStatsCollectorCallback {
 public:
  explicit RecursiveCallback(RTCStatsCollectorWrapper* stats) : stats_(stats) {}

  void OnStatsDelivered(
      const rtc::scoped_refptr<const RTCStatsReport>& report) override {
    stats_->GetStatsReport();
    called_ = true;
  }

  bool called() const { return called_; }

 private:
  RTCStatsCollectorWrapper* stats_;
  bool called_ = false;
};

// Test that nothing bad happens if a callback causes GetStatsReport to be
// called again recursively. Regression test for crbug.com/webrtc/8973.
TEST_F(RTCStatsCollectorTest, DoNotCrashWhenGetStatsCalledDuringCallback) {
  auto callback1 = rtc::make_ref_counted<RecursiveCallback>(stats_.get());
  auto callback2 = rtc::make_ref_counted<RecursiveCallback>(stats_.get());
  stats_->stats_collector()->GetStatsReport(callback1);
  stats_->stats_collector()->GetStatsReport(callback2);
  EXPECT_TRUE_WAIT(callback1->called(), kGetStatsReportTimeoutMs);
  EXPECT_TRUE_WAIT(callback2->called(), kGetStatsReportTimeoutMs);
}

class RTCTestStats : public RTCStats {
 public:
  WEBRTC_RTCSTATS_DECL();

  RTCTestStats(const std::string& id, Timestamp timestamp)
      : RTCStats(id, timestamp) {}

  absl::optional<int32_t> dummy_stat;
};

WEBRTC_RTCSTATS_IMPL(RTCTestStats,
                     RTCStats,
                     "test-stats",
                     AttributeInit("dummyStat", &dummy_stat))

// Overrides the stats collection to verify thread usage and that the resulting
// partial reports are merged.
class FakeRTCStatsCollector : public RTCStatsCollector,
                              public RTCStatsCollectorCallback {
 public:
  static rtc::scoped_refptr<FakeRTCStatsCollector> Create(
      PeerConnectionInternal* pc,
      int64_t cache_lifetime_us) {
    return rtc::scoped_refptr<FakeRTCStatsCollector>(
        new rtc::RefCountedObject<FakeRTCStatsCollector>(pc,
                                                         cache_lifetime_us));
  }

  // Since FakeRTCStatsCollector inherits twice from RefCountInterface, once via
  // RTCStatsCollector and once via RTCStatsCollectorCallback, scoped_refptr
  // will get confused about which  AddRef()/Release() methods to call.
  // So to remove all doubt, we declare them here again in the class that we
  // give to scoped_refptr.
  // Satisfying the implementation of these methods and associating them with a
  // reference counter, will be done by RefCountedObject.
  virtual void AddRef() const = 0;
  virtual rtc::RefCountReleaseStatus Release() const = 0;

  // RTCStatsCollectorCallback implementation.
  void OnStatsDelivered(
      const rtc::scoped_refptr<const RTCStatsReport>& report) override {
    EXPECT_TRUE(signaling_thread_->IsCurrent());
    MutexLock lock(&lock_);
    delivered_report_ = report;
  }

  void VerifyThreadUsageAndResultsMerging() {
    GetStatsReport(rtc::scoped_refptr<RTCStatsCollectorCallback>(this));
    EXPECT_TRUE_WAIT(HasVerifiedResults(), kGetStatsReportTimeoutMs);
  }

  bool HasVerifiedResults() {
    EXPECT_TRUE(signaling_thread_->IsCurrent());
    MutexLock lock(&lock_);
    if (!delivered_report_)
      return false;
    EXPECT_EQ(produced_on_signaling_thread_, 1);
    EXPECT_EQ(produced_on_network_thread_, 1);

    EXPECT_TRUE(delivered_report_->Get("SignalingThreadStats"));
    EXPECT_TRUE(delivered_report_->Get("NetworkThreadStats"));

    produced_on_signaling_thread_ = 0;
    produced_on_network_thread_ = 0;
    delivered_report_ = nullptr;
    return true;
  }

 protected:
  FakeRTCStatsCollector(PeerConnectionInternal* pc, int64_t cache_lifetime)
      : RTCStatsCollector(pc, cache_lifetime),
        signaling_thread_(pc->signaling_thread()),
        worker_thread_(pc->worker_thread()),
        network_thread_(pc->network_thread()) {}

  void ProducePartialResultsOnSignalingThreadImpl(
      Timestamp timestamp,
      RTCStatsReport* partial_report) override {
    EXPECT_TRUE(signaling_thread_->IsCurrent());
    {
      MutexLock lock(&lock_);
      EXPECT_FALSE(delivered_report_);
      ++produced_on_signaling_thread_;
    }

    partial_report->AddStats(std::unique_ptr<const RTCStats>(
        new RTCTestStats("SignalingThreadStats", timestamp)));
  }
  void ProducePartialResultsOnNetworkThreadImpl(
      Timestamp timestamp,
      const std::map<std::string, cricket::TransportStats>&
          transport_stats_by_name,
      const std::map<std::string, CertificateStatsPair>& transport_cert_stats,
      RTCStatsReport* partial_report) override {
    EXPECT_TRUE(network_thread_->IsCurrent());
    {
      MutexLock lock(&lock_);
      EXPECT_FALSE(delivered_report_);
      ++produced_on_network_thread_;
    }

    partial_report->AddStats(std::unique_ptr<const RTCStats>(
        new RTCTestStats("NetworkThreadStats", timestamp)));
  }

 private:
  rtc::Thread* const signaling_thread_;
  rtc::Thread* const worker_thread_;
  rtc::Thread* const network_thread_;

  Mutex lock_;
  rtc::scoped_refptr<const RTCStatsReport> delivered_report_;
  int produced_on_signaling_thread_ = 0;
  int produced_on_network_thread_ = 0;
};

TEST(RTCStatsCollectorTestWithFakeCollector, ThreadUsageAndResultsMerging) {
  rtc::AutoThread main_thread_;
  auto pc = rtc::make_ref_counted<FakePeerConnectionForStats>();
  rtc::scoped_refptr<FakeRTCStatsCollector> stats_collector(
      FakeRTCStatsCollector::Create(pc.get(),
                                    50 * rtc::kNumMicrosecsPerMillisec));
  stats_collector->VerifyThreadUsageAndResultsMerging();
}

}  // namespace

}  // namespace webrtc
