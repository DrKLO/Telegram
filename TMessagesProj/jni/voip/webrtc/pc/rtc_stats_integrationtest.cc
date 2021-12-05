/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>
#include <string.h>

#include <algorithm>
#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "api/audio_codecs/audio_decoder_factory.h"
#include "api/audio_codecs/audio_encoder_factory.h"
#include "api/audio_codecs/builtin_audio_decoder_factory.h"
#include "api/audio_codecs/builtin_audio_encoder_factory.h"
#include "api/audio_options.h"
#include "api/data_channel_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/scoped_refptr.h"
#include "api/stats/rtc_stats.h"
#include "api/stats/rtc_stats_report.h"
#include "api/stats/rtcstats_objects.h"
#include "pc/rtc_stats_traversal.h"
#include "pc/test/peer_connection_test_wrapper.h"
#include "pc/test/rtc_stats_obtainer.h"
#include "rtc_base/checks.h"
#include "rtc_base/event_tracer.h"
#include "rtc_base/gunit.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/thread.h"
#include "rtc_base/trace_event.h"
#include "rtc_base/virtual_socket_server.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::Contains;

namespace webrtc {

namespace {

const int64_t kGetStatsTimeoutMs = 10000;

const unsigned char* GetCategoryEnabledHandler(const char* name) {
  if (strcmp("webrtc_stats", name) != 0) {
    return reinterpret_cast<const unsigned char*>("");
  }
  return reinterpret_cast<const unsigned char*>(name);
}

class RTCStatsReportTraceListener {
 public:
  static void SetUp() {
    if (!traced_report_)
      traced_report_ = new RTCStatsReportTraceListener();
    traced_report_->last_trace_ = "";
    SetupEventTracer(&GetCategoryEnabledHandler,
                     &RTCStatsReportTraceListener::AddTraceEventHandler);
  }

  static const std::string& last_trace() {
    RTC_DCHECK(traced_report_);
    return traced_report_->last_trace_;
  }

 private:
  static void AddTraceEventHandler(
      char phase,
      const unsigned char* category_enabled,
      const char* name,
      unsigned long long id,  // NOLINT(runtime/int)
      int num_args,
      const char** arg_names,
      const unsigned char* arg_types,
      const unsigned long long* arg_values,  // NOLINT(runtime/int)
      unsigned char flags) {
    RTC_DCHECK(traced_report_);
    EXPECT_STREQ("webrtc_stats",
                 reinterpret_cast<const char*>(category_enabled));
    EXPECT_STREQ("webrtc_stats", name);
    EXPECT_EQ(1, num_args);
    EXPECT_STREQ("report", arg_names[0]);
    EXPECT_EQ(TRACE_VALUE_TYPE_COPY_STRING, arg_types[0]);

    traced_report_->last_trace_ = reinterpret_cast<const char*>(arg_values[0]);
  }

  static RTCStatsReportTraceListener* traced_report_;
  std::string last_trace_;
};

RTCStatsReportTraceListener* RTCStatsReportTraceListener::traced_report_ =
    nullptr;

class RTCStatsIntegrationTest : public ::testing::Test {
 public:
  RTCStatsIntegrationTest()
      : network_thread_(new rtc::Thread(&virtual_socket_server_)),
        worker_thread_(rtc::Thread::Create()) {
    RTCStatsReportTraceListener::SetUp();

    RTC_CHECK(network_thread_->Start());
    RTC_CHECK(worker_thread_->Start());

    caller_ = rtc::make_ref_counted<PeerConnectionTestWrapper>(
        "caller", network_thread_.get(), worker_thread_.get());
    callee_ = rtc::make_ref_counted<PeerConnectionTestWrapper>(
        "callee", network_thread_.get(), worker_thread_.get());
  }

  void StartCall() {
    // Create PeerConnections and "connect" sigslots
    PeerConnectionInterface::RTCConfiguration config;
    PeerConnectionInterface::IceServer ice_server;
    ice_server.uri = "stun:1.1.1.1:3478";
    config.servers.push_back(ice_server);
    EXPECT_TRUE(caller_->CreatePc(config, CreateBuiltinAudioEncoderFactory(),
                                  CreateBuiltinAudioDecoderFactory()));
    EXPECT_TRUE(callee_->CreatePc(config, CreateBuiltinAudioEncoderFactory(),
                                  CreateBuiltinAudioDecoderFactory()));
    PeerConnectionTestWrapper::Connect(caller_.get(), callee_.get());

    // Get user media for audio and video
    caller_->GetAndAddUserMedia(true, cricket::AudioOptions(), true);
    callee_->GetAndAddUserMedia(true, cricket::AudioOptions(), true);

    // Create data channels
    DataChannelInit init;
    caller_->CreateDataChannel("data", init);
    callee_->CreateDataChannel("data", init);

    // Negotiate and wait for call to establish
    caller_->CreateOffer(PeerConnectionInterface::RTCOfferAnswerOptions());
    caller_->WaitForCallEstablished();
    callee_->WaitForCallEstablished();
  }

  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCaller() {
    return GetStats(caller_->pc());
  }
  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCaller(
      rtc::scoped_refptr<RtpSenderInterface> selector) {
    return GetStats(caller_->pc(), selector);
  }
  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCaller(
      rtc::scoped_refptr<RtpReceiverInterface> selector) {
    return GetStats(caller_->pc(), selector);
  }

  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCallee() {
    return GetStats(callee_->pc());
  }
  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCallee(
      rtc::scoped_refptr<RtpSenderInterface> selector) {
    return GetStats(callee_->pc(), selector);
  }
  rtc::scoped_refptr<const RTCStatsReport> GetStatsFromCallee(
      rtc::scoped_refptr<RtpReceiverInterface> selector) {
    return GetStats(callee_->pc(), selector);
  }

 protected:
  static rtc::scoped_refptr<const RTCStatsReport> GetStats(
      PeerConnectionInterface* pc) {
    rtc::scoped_refptr<RTCStatsObtainer> stats_obtainer =
        RTCStatsObtainer::Create();
    pc->GetStats(stats_obtainer);
    EXPECT_TRUE_WAIT(stats_obtainer->report(), kGetStatsTimeoutMs);
    return stats_obtainer->report();
  }

  template <typename T>
  static rtc::scoped_refptr<const RTCStatsReport> GetStats(
      PeerConnectionInterface* pc,
      rtc::scoped_refptr<T> selector) {
    rtc::scoped_refptr<RTCStatsObtainer> stats_obtainer =
        RTCStatsObtainer::Create();
    pc->GetStats(selector, stats_obtainer);
    EXPECT_TRUE_WAIT(stats_obtainer->report(), kGetStatsTimeoutMs);
    return stats_obtainer->report();
  }

  // |network_thread_| uses |virtual_socket_server_| so they must be
  // constructed/destructed in the correct order.
  rtc::VirtualSocketServer virtual_socket_server_;
  std::unique_ptr<rtc::Thread> network_thread_;
  std::unique_ptr<rtc::Thread> worker_thread_;
  rtc::scoped_refptr<PeerConnectionTestWrapper> caller_;
  rtc::scoped_refptr<PeerConnectionTestWrapper> callee_;
};

class RTCStatsVerifier {
 public:
  RTCStatsVerifier(const RTCStatsReport* report, const RTCStats* stats)
      : report_(report), stats_(stats), all_tests_successful_(true) {
    RTC_CHECK(report_);
    RTC_CHECK(stats_);
    for (const RTCStatsMemberInterface* member : stats_->Members()) {
      untested_members_.insert(member);
    }
  }

  void MarkMemberTested(const RTCStatsMemberInterface& member,
                        bool test_successful) {
    untested_members_.erase(&member);
    all_tests_successful_ &= test_successful;
  }

  void TestMemberIsDefined(const RTCStatsMemberInterface& member) {
    EXPECT_TRUE(member.is_defined())
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was undefined.";
    MarkMemberTested(member, member.is_defined());
  }

  void TestMemberIsUndefined(const RTCStatsMemberInterface& member) {
    EXPECT_FALSE(member.is_defined())
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was defined (" << member.ValueToString() << ").";
    MarkMemberTested(member, !member.is_defined());
  }

  template <typename T>
  void TestMemberIsPositive(const RTCStatsMemberInterface& member) {
    EXPECT_TRUE(member.is_defined())
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was undefined.";
    if (!member.is_defined()) {
      MarkMemberTested(member, false);
      return;
    }
    bool is_positive = *member.cast_to<RTCStatsMember<T>>() > T(0);
    EXPECT_TRUE(is_positive)
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was not positive (" << member.ValueToString() << ").";
    MarkMemberTested(member, is_positive);
  }

  template <typename T>
  void TestMemberIsNonNegative(const RTCStatsMemberInterface& member) {
    EXPECT_TRUE(member.is_defined())
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was undefined.";
    if (!member.is_defined()) {
      MarkMemberTested(member, false);
      return;
    }
    bool is_non_negative = *member.cast_to<RTCStatsMember<T>>() >= T(0);
    EXPECT_TRUE(is_non_negative)
        << stats_->type() << "." << member.name() << "[" << stats_->id()
        << "] was not non-negative (" << member.ValueToString() << ").";
    MarkMemberTested(member, is_non_negative);
  }

  void TestMemberIsIDReference(const RTCStatsMemberInterface& member,
                               const char* expected_type) {
    TestMemberIsIDReference(member, expected_type, false);
  }

  void TestMemberIsOptionalIDReference(const RTCStatsMemberInterface& member,
                                       const char* expected_type) {
    TestMemberIsIDReference(member, expected_type, true);
  }

  bool ExpectAllMembersSuccessfullyTested() {
    if (untested_members_.empty())
      return all_tests_successful_;
    for (const RTCStatsMemberInterface* member : untested_members_) {
      EXPECT_TRUE(false) << stats_->type() << "." << member->name() << "["
                         << stats_->id() << "] was not tested.";
    }
    return false;
  }

 private:
  void TestMemberIsIDReference(const RTCStatsMemberInterface& member,
                               const char* expected_type,
                               bool optional) {
    if (optional && !member.is_defined()) {
      MarkMemberTested(member, true);
      return;
    }
    bool valid_reference = false;
    if (member.is_defined()) {
      if (member.type() == RTCStatsMemberInterface::kString) {
        // A single ID.
        const RTCStatsMember<std::string>& id =
            member.cast_to<RTCStatsMember<std::string>>();
        const RTCStats* referenced_stats = report_->Get(*id);
        valid_reference =
            referenced_stats && referenced_stats->type() == expected_type;
      } else if (member.type() == RTCStatsMemberInterface::kSequenceString) {
        // A vector of IDs.
        valid_reference = true;
        const RTCStatsMember<std::vector<std::string>>& ids =
            member.cast_to<RTCStatsMember<std::vector<std::string>>>();
        for (const std::string& id : *ids) {
          const RTCStats* referenced_stats = report_->Get(id);
          if (!referenced_stats || referenced_stats->type() != expected_type) {
            valid_reference = false;
            break;
          }
        }
      }
    }
    EXPECT_TRUE(valid_reference)
        << stats_->type() << "." << member.name()
        << " is not a reference to an "
           "existing dictionary of type "
        << expected_type << " (value: "
        << (member.is_defined() ? member.ValueToString() : "null") << ").";
    MarkMemberTested(member, valid_reference);
  }

  rtc::scoped_refptr<const RTCStatsReport> report_;
  const RTCStats* stats_;
  std::set<const RTCStatsMemberInterface*> untested_members_;
  bool all_tests_successful_;
};

class RTCStatsReportVerifier {
 public:
  static std::set<const char*> StatsTypes() {
    std::set<const char*> stats_types;
    stats_types.insert(RTCCertificateStats::kType);
    stats_types.insert(RTCCodecStats::kType);
    stats_types.insert(RTCDataChannelStats::kType);
    stats_types.insert(RTCIceCandidatePairStats::kType);
    stats_types.insert(RTCLocalIceCandidateStats::kType);
    stats_types.insert(RTCRemoteIceCandidateStats::kType);
    stats_types.insert(RTCMediaStreamStats::kType);
    stats_types.insert(RTCMediaStreamTrackStats::kType);
    stats_types.insert(RTCPeerConnectionStats::kType);
    stats_types.insert(RTCInboundRTPStreamStats::kType);
    stats_types.insert(RTCOutboundRTPStreamStats::kType);
    stats_types.insert(RTCTransportStats::kType);
    return stats_types;
  }

  explicit RTCStatsReportVerifier(const RTCStatsReport* report)
      : report_(report) {}

  void VerifyReport(std::vector<const char*> allowed_missing_stats) {
    std::set<const char*> missing_stats = StatsTypes();
    bool verify_successful = true;
    std::vector<const RTCTransportStats*> transport_stats =
        report_->GetStatsOfType<RTCTransportStats>();
    EXPECT_EQ(transport_stats.size(), 1U);
    std::string selected_candidate_pair_id =
        *transport_stats[0]->selected_candidate_pair_id;
    for (const RTCStats& stats : *report_) {
      missing_stats.erase(stats.type());
      if (stats.type() == RTCCertificateStats::kType) {
        verify_successful &=
            VerifyRTCCertificateStats(stats.cast_to<RTCCertificateStats>());
      } else if (stats.type() == RTCCodecStats::kType) {
        verify_successful &=
            VerifyRTCCodecStats(stats.cast_to<RTCCodecStats>());
      } else if (stats.type() == RTCDataChannelStats::kType) {
        verify_successful &=
            VerifyRTCDataChannelStats(stats.cast_to<RTCDataChannelStats>());
      } else if (stats.type() == RTCIceCandidatePairStats::kType) {
        verify_successful &= VerifyRTCIceCandidatePairStats(
            stats.cast_to<RTCIceCandidatePairStats>(),
            stats.id() == selected_candidate_pair_id);
      } else if (stats.type() == RTCLocalIceCandidateStats::kType) {
        verify_successful &= VerifyRTCLocalIceCandidateStats(
            stats.cast_to<RTCLocalIceCandidateStats>());
      } else if (stats.type() == RTCRemoteIceCandidateStats::kType) {
        verify_successful &= VerifyRTCRemoteIceCandidateStats(
            stats.cast_to<RTCRemoteIceCandidateStats>());
      } else if (stats.type() == RTCMediaStreamStats::kType) {
        verify_successful &=
            VerifyRTCMediaStreamStats(stats.cast_to<RTCMediaStreamStats>());
      } else if (stats.type() == RTCMediaStreamTrackStats::kType) {
        verify_successful &= VerifyRTCMediaStreamTrackStats(
            stats.cast_to<RTCMediaStreamTrackStats>());
      } else if (stats.type() == RTCPeerConnectionStats::kType) {
        verify_successful &= VerifyRTCPeerConnectionStats(
            stats.cast_to<RTCPeerConnectionStats>());
      } else if (stats.type() == RTCInboundRTPStreamStats::kType) {
        verify_successful &= VerifyRTCInboundRTPStreamStats(
            stats.cast_to<RTCInboundRTPStreamStats>());
      } else if (stats.type() == RTCOutboundRTPStreamStats::kType) {
        verify_successful &= VerifyRTCOutboundRTPStreamStats(
            stats.cast_to<RTCOutboundRTPStreamStats>());
      } else if (stats.type() == RTCRemoteInboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCRemoteInboundRtpStreamStats(
            stats.cast_to<RTCRemoteInboundRtpStreamStats>());
      } else if (stats.type() == RTCRemoteOutboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCRemoteOutboundRTPStreamStats(
            stats.cast_to<RTCRemoteOutboundRtpStreamStats>());
      } else if (stats.type() == RTCAudioSourceStats::kType) {
        // RTCAudioSourceStats::kType and RTCVideoSourceStats::kType both have
        // the value "media-source", but they are distinguishable with pointer
        // equality (==). In JavaScript they would be distinguished with |kind|.
        verify_successful &=
            VerifyRTCAudioSourceStats(stats.cast_to<RTCAudioSourceStats>());
      } else if (stats.type() == RTCVideoSourceStats::kType) {
        // RTCAudioSourceStats::kType and RTCVideoSourceStats::kType both have
        // the value "media-source", but they are distinguishable with pointer
        // equality (==). In JavaScript they would be distinguished with |kind|.
        verify_successful &=
            VerifyRTCVideoSourceStats(stats.cast_to<RTCVideoSourceStats>());
      } else if (stats.type() == RTCTransportStats::kType) {
        verify_successful &=
            VerifyRTCTransportStats(stats.cast_to<RTCTransportStats>());
      } else {
        EXPECT_TRUE(false) << "Unrecognized stats type: " << stats.type();
        verify_successful = false;
      }
    }
    for (const char* missing : missing_stats) {
      if (!absl::c_linear_search(allowed_missing_stats, missing)) {
        verify_successful = false;
        EXPECT_TRUE(false) << "Missing expected stats type: " << missing;
      }
    }
    EXPECT_TRUE(verify_successful)
        << "One or more problems with the stats. This is the report:\n"
        << report_->ToJson();
  }

  bool VerifyRTCCertificateStats(const RTCCertificateStats& certificate) {
    RTCStatsVerifier verifier(report_, &certificate);
    verifier.TestMemberIsDefined(certificate.fingerprint);
    verifier.TestMemberIsDefined(certificate.fingerprint_algorithm);
    verifier.TestMemberIsDefined(certificate.base64_certificate);
    verifier.TestMemberIsOptionalIDReference(certificate.issuer_certificate_id,
                                             RTCCertificateStats::kType);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCCodecStats(const RTCCodecStats& codec) {
    RTCStatsVerifier verifier(report_, &codec);
    verifier.TestMemberIsIDReference(codec.transport_id,
                                     RTCTransportStats::kType);
    verifier.TestMemberIsDefined(codec.payload_type);
    verifier.TestMemberIsDefined(codec.mime_type);
    verifier.TestMemberIsPositive<uint32_t>(codec.clock_rate);

    if (codec.mime_type->rfind("audio", 0) == 0)
      verifier.TestMemberIsPositive<uint32_t>(codec.channels);
    else
      verifier.TestMemberIsUndefined(codec.channels);

    // sdp_fmtp_line is an optional field.
    verifier.MarkMemberTested(codec.sdp_fmtp_line, true);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCDataChannelStats(const RTCDataChannelStats& data_channel) {
    RTCStatsVerifier verifier(report_, &data_channel);
    verifier.TestMemberIsDefined(data_channel.label);
    verifier.TestMemberIsDefined(data_channel.protocol);
    verifier.TestMemberIsDefined(data_channel.data_channel_identifier);
    verifier.TestMemberIsDefined(data_channel.state);
    verifier.TestMemberIsNonNegative<uint32_t>(data_channel.messages_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(data_channel.bytes_sent);
    verifier.TestMemberIsNonNegative<uint32_t>(data_channel.messages_received);
    verifier.TestMemberIsNonNegative<uint64_t>(data_channel.bytes_received);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCIceCandidatePairStats(
      const RTCIceCandidatePairStats& candidate_pair,
      bool is_selected_pair) {
    RTCStatsVerifier verifier(report_, &candidate_pair);
    verifier.TestMemberIsIDReference(candidate_pair.transport_id,
                                     RTCTransportStats::kType);
    verifier.TestMemberIsIDReference(candidate_pair.local_candidate_id,
                                     RTCLocalIceCandidateStats::kType);
    verifier.TestMemberIsIDReference(candidate_pair.remote_candidate_id,
                                     RTCRemoteIceCandidateStats::kType);
    verifier.TestMemberIsDefined(candidate_pair.state);
    verifier.TestMemberIsNonNegative<uint64_t>(candidate_pair.priority);
    verifier.TestMemberIsDefined(candidate_pair.nominated);
    verifier.TestMemberIsDefined(candidate_pair.writable);
    verifier.TestMemberIsUndefined(candidate_pair.readable);
    verifier.TestMemberIsNonNegative<uint64_t>(candidate_pair.bytes_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(candidate_pair.bytes_received);
    verifier.TestMemberIsNonNegative<double>(
        candidate_pair.total_round_trip_time);
    verifier.TestMemberIsNonNegative<double>(
        candidate_pair.current_round_trip_time);
    if (is_selected_pair) {
      verifier.TestMemberIsNonNegative<double>(
          candidate_pair.available_outgoing_bitrate);
      // A pair should be nominated in order to be selected.
      EXPECT_TRUE(*candidate_pair.nominated);
    } else {
      verifier.TestMemberIsUndefined(candidate_pair.available_outgoing_bitrate);
    }
    verifier.TestMemberIsUndefined(candidate_pair.available_incoming_bitrate);
    verifier.TestMemberIsNonNegative<uint64_t>(
        candidate_pair.requests_received);
    verifier.TestMemberIsNonNegative<uint64_t>(candidate_pair.requests_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(
        candidate_pair.responses_received);
    verifier.TestMemberIsNonNegative<uint64_t>(candidate_pair.responses_sent);
    verifier.TestMemberIsUndefined(candidate_pair.retransmissions_received);
    verifier.TestMemberIsUndefined(candidate_pair.retransmissions_sent);
    verifier.TestMemberIsUndefined(candidate_pair.consent_requests_received);
    verifier.TestMemberIsNonNegative<uint64_t>(
        candidate_pair.consent_requests_sent);
    verifier.TestMemberIsUndefined(candidate_pair.consent_responses_received);
    verifier.TestMemberIsUndefined(candidate_pair.consent_responses_sent);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCIceCandidateStats(const RTCIceCandidateStats& candidate) {
    RTCStatsVerifier verifier(report_, &candidate);
    verifier.TestMemberIsIDReference(candidate.transport_id,
                                     RTCTransportStats::kType);
    verifier.TestMemberIsDefined(candidate.is_remote);
    if (*candidate.is_remote) {
      verifier.TestMemberIsUndefined(candidate.network_type);
    } else {
      verifier.TestMemberIsDefined(candidate.network_type);
    }
    verifier.TestMemberIsDefined(candidate.ip);
    verifier.TestMemberIsDefined(candidate.address);
    verifier.TestMemberIsNonNegative<int32_t>(candidate.port);
    verifier.TestMemberIsDefined(candidate.protocol);
    verifier.TestMemberIsDefined(candidate.candidate_type);
    verifier.TestMemberIsNonNegative<int32_t>(candidate.priority);
    verifier.TestMemberIsUndefined(candidate.url);
    verifier.TestMemberIsUndefined(candidate.relay_protocol);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCLocalIceCandidateStats(
      const RTCLocalIceCandidateStats& local_candidate) {
    return VerifyRTCIceCandidateStats(local_candidate);
  }

  bool VerifyRTCRemoteIceCandidateStats(
      const RTCRemoteIceCandidateStats& remote_candidate) {
    return VerifyRTCIceCandidateStats(remote_candidate);
  }

  bool VerifyRTCMediaStreamStats(const RTCMediaStreamStats& media_stream) {
    RTCStatsVerifier verifier(report_, &media_stream);
    verifier.TestMemberIsDefined(media_stream.stream_identifier);
    verifier.TestMemberIsIDReference(media_stream.track_ids,
                                     RTCMediaStreamTrackStats::kType);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCMediaStreamTrackStats(
      const RTCMediaStreamTrackStats& media_stream_track) {
    RTCStatsVerifier verifier(report_, &media_stream_track);
    verifier.TestMemberIsDefined(media_stream_track.track_identifier);
    verifier.TestMemberIsDefined(media_stream_track.remote_source);
    verifier.TestMemberIsDefined(media_stream_track.ended);
    verifier.TestMemberIsDefined(media_stream_track.detached);
    verifier.TestMemberIsDefined(media_stream_track.kind);
    RTC_DCHECK(media_stream_track.remote_source.is_defined());
    // Video or audio media stream track?
    if (*media_stream_track.kind == RTCMediaStreamTrackKind::kVideo) {
      // The type of the referenced media source depends on kind.
      if (*media_stream_track.remote_source) {
        verifier.TestMemberIsUndefined(media_stream_track.media_source_id);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.jitter_buffer_delay);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.jitter_buffer_emitted_count);
        verifier.TestMemberIsUndefined(media_stream_track.frames_sent);
        verifier.TestMemberIsUndefined(media_stream_track.huge_frames_sent);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.frames_received);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.frames_decoded);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.frames_dropped);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.freeze_count);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.pause_count);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.total_freezes_duration);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.total_pauses_duration);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.total_frames_duration);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.sum_squared_frame_durations);
      } else {
        verifier.TestMemberIsIDReference(media_stream_track.media_source_id,
                                         RTCVideoSourceStats::kType);
        // Local tracks have no jitter buffer.
        verifier.TestMemberIsUndefined(media_stream_track.jitter_buffer_delay);
        verifier.TestMemberIsUndefined(
            media_stream_track.jitter_buffer_emitted_count);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.frames_sent);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.huge_frames_sent);
        verifier.TestMemberIsUndefined(media_stream_track.frames_received);
        verifier.TestMemberIsUndefined(media_stream_track.frames_decoded);
        verifier.TestMemberIsUndefined(media_stream_track.frames_dropped);
        verifier.TestMemberIsUndefined(media_stream_track.freeze_count);
        verifier.TestMemberIsUndefined(media_stream_track.pause_count);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_freezes_duration);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_pauses_duration);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_frames_duration);
        verifier.TestMemberIsUndefined(
            media_stream_track.sum_squared_frame_durations);
      }
      // Video-only members
      verifier.TestMemberIsNonNegative<uint32_t>(
          media_stream_track.frame_width);
      verifier.TestMemberIsNonNegative<uint32_t>(
          media_stream_track.frame_height);
      verifier.TestMemberIsUndefined(media_stream_track.frames_per_second);
      verifier.TestMemberIsUndefined(media_stream_track.frames_corrupted);
      verifier.TestMemberIsUndefined(media_stream_track.partial_frames_lost);
      verifier.TestMemberIsUndefined(media_stream_track.full_frames_lost);
      // Audio-only members should be undefined
      verifier.TestMemberIsUndefined(media_stream_track.audio_level);
      verifier.TestMemberIsUndefined(media_stream_track.echo_return_loss);
      verifier.TestMemberIsUndefined(
          media_stream_track.echo_return_loss_enhancement);
      verifier.TestMemberIsUndefined(media_stream_track.total_audio_energy);
      verifier.TestMemberIsUndefined(media_stream_track.total_samples_duration);
      verifier.TestMemberIsUndefined(media_stream_track.total_samples_received);
      verifier.TestMemberIsUndefined(media_stream_track.concealed_samples);
      verifier.TestMemberIsUndefined(
          media_stream_track.silent_concealed_samples);
      verifier.TestMemberIsUndefined(media_stream_track.concealment_events);
      verifier.TestMemberIsUndefined(
          media_stream_track.inserted_samples_for_deceleration);
      verifier.TestMemberIsUndefined(
          media_stream_track.removed_samples_for_acceleration);
      verifier.TestMemberIsUndefined(media_stream_track.jitter_buffer_flushes);
      verifier.TestMemberIsUndefined(
          media_stream_track.delayed_packet_outage_samples);
      verifier.TestMemberIsUndefined(
          media_stream_track.relative_packet_arrival_delay);
      verifier.TestMemberIsUndefined(media_stream_track.interruption_count);
      verifier.TestMemberIsUndefined(
          media_stream_track.total_interruption_duration);
      verifier.TestMemberIsUndefined(
          media_stream_track.jitter_buffer_target_delay);
    } else {
      RTC_DCHECK_EQ(*media_stream_track.kind, RTCMediaStreamTrackKind::kAudio);
      // The type of the referenced media source depends on kind.
      if (*media_stream_track.remote_source) {
        // Remote tracks don't have media source stats.
        verifier.TestMemberIsUndefined(media_stream_track.media_source_id);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.jitter_buffer_delay);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.jitter_buffer_emitted_count);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.jitter_buffer_target_delay);
        verifier.TestMemberIsPositive<double>(media_stream_track.audio_level);
        verifier.TestMemberIsPositive<double>(
            media_stream_track.total_audio_energy);
        verifier.TestMemberIsPositive<uint64_t>(
            media_stream_track.total_samples_received);
        verifier.TestMemberIsPositive<double>(
            media_stream_track.total_samples_duration);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.concealed_samples);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.silent_concealed_samples);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.concealment_events);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.inserted_samples_for_deceleration);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.removed_samples_for_acceleration);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.jitter_buffer_flushes);
        verifier.TestMemberIsNonNegative<uint64_t>(
            media_stream_track.delayed_packet_outage_samples);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.relative_packet_arrival_delay);
        verifier.TestMemberIsNonNegative<uint32_t>(
            media_stream_track.interruption_count);
        verifier.TestMemberIsNonNegative<double>(
            media_stream_track.total_interruption_duration);
      } else {
        verifier.TestMemberIsIDReference(media_stream_track.media_source_id,
                                         RTCAudioSourceStats::kType);
        // Local audio tracks have no jitter buffer.
        verifier.TestMemberIsUndefined(media_stream_track.jitter_buffer_delay);
        verifier.TestMemberIsUndefined(
            media_stream_track.jitter_buffer_emitted_count);
        verifier.TestMemberIsUndefined(
            media_stream_track.jitter_buffer_target_delay);
        verifier.TestMemberIsUndefined(media_stream_track.audio_level);
        verifier.TestMemberIsUndefined(media_stream_track.total_audio_energy);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_samples_received);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_samples_duration);
        verifier.TestMemberIsUndefined(media_stream_track.concealed_samples);
        verifier.TestMemberIsUndefined(
            media_stream_track.silent_concealed_samples);
        verifier.TestMemberIsUndefined(media_stream_track.concealment_events);
        verifier.TestMemberIsUndefined(
            media_stream_track.inserted_samples_for_deceleration);
        verifier.TestMemberIsUndefined(
            media_stream_track.removed_samples_for_acceleration);
        verifier.TestMemberIsUndefined(
            media_stream_track.jitter_buffer_flushes);
        verifier.TestMemberIsUndefined(
            media_stream_track.delayed_packet_outage_samples);
        verifier.TestMemberIsUndefined(
            media_stream_track.relative_packet_arrival_delay);
        verifier.TestMemberIsUndefined(media_stream_track.interruption_count);
        verifier.TestMemberIsUndefined(
            media_stream_track.total_interruption_duration);
      }
      // Video-only members should be undefined
      verifier.TestMemberIsUndefined(media_stream_track.frame_width);
      verifier.TestMemberIsUndefined(media_stream_track.frame_height);
      verifier.TestMemberIsUndefined(media_stream_track.frames_per_second);
      verifier.TestMemberIsUndefined(media_stream_track.frames_sent);
      verifier.TestMemberIsUndefined(media_stream_track.huge_frames_sent);
      verifier.TestMemberIsUndefined(media_stream_track.frames_received);
      verifier.TestMemberIsUndefined(media_stream_track.frames_decoded);
      verifier.TestMemberIsUndefined(media_stream_track.frames_dropped);
      verifier.TestMemberIsUndefined(media_stream_track.frames_corrupted);
      verifier.TestMemberIsUndefined(media_stream_track.partial_frames_lost);
      verifier.TestMemberIsUndefined(media_stream_track.full_frames_lost);
      verifier.TestMemberIsUndefined(media_stream_track.freeze_count);
      verifier.TestMemberIsUndefined(media_stream_track.pause_count);
      verifier.TestMemberIsUndefined(media_stream_track.total_freezes_duration);
      verifier.TestMemberIsUndefined(media_stream_track.total_pauses_duration);
      verifier.TestMemberIsUndefined(media_stream_track.total_frames_duration);
      verifier.TestMemberIsUndefined(
          media_stream_track.sum_squared_frame_durations);
      // Audio-only members
      // TODO(hbos): |echo_return_loss| and |echo_return_loss_enhancement| are
      // flaky on msan bot (sometimes defined, sometimes undefined). Should the
      // test run until available or is there a way to have it always be
      // defined? crbug.com/627816
      verifier.MarkMemberTested(media_stream_track.echo_return_loss, true);
      verifier.MarkMemberTested(media_stream_track.echo_return_loss_enhancement,
                                true);
    }
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCPeerConnectionStats(
      const RTCPeerConnectionStats& peer_connection) {
    RTCStatsVerifier verifier(report_, &peer_connection);
    verifier.TestMemberIsNonNegative<uint32_t>(
        peer_connection.data_channels_opened);
    verifier.TestMemberIsNonNegative<uint32_t>(
        peer_connection.data_channels_closed);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  void VerifyRTCRTPStreamStats(const RTCRTPStreamStats& stream,
                               RTCStatsVerifier& verifier) {
    verifier.TestMemberIsDefined(stream.ssrc);
    verifier.TestMemberIsDefined(stream.kind);
    // Some legacy metrics are only defined for some of the RTP types in the
    // hierarcy.
    if (stream.type() == RTCInboundRTPStreamStats::kType ||
        stream.type() == RTCOutboundRTPStreamStats::kType) {
      verifier.TestMemberIsDefined(stream.media_type);
      verifier.TestMemberIsIDReference(stream.track_id,
                                       RTCMediaStreamTrackStats::kType);
    } else {
      verifier.TestMemberIsUndefined(stream.media_type);
      verifier.TestMemberIsUndefined(stream.track_id);
    }
    verifier.TestMemberIsIDReference(stream.transport_id,
                                     RTCTransportStats::kType);
    verifier.TestMemberIsIDReference(stream.codec_id, RTCCodecStats::kType);
  }

  void VerifyRTCSentRTPStreamStats(const RTCSentRtpStreamStats& sent_stream,
                                   RTCStatsVerifier& verifier) {
    VerifyRTCRTPStreamStats(sent_stream, verifier);
    verifier.TestMemberIsDefined(sent_stream.packets_sent);
    verifier.TestMemberIsDefined(sent_stream.bytes_sent);
  }

  bool VerifyRTCInboundRTPStreamStats(
      const RTCInboundRTPStreamStats& inbound_stream) {
    RTCStatsVerifier verifier(report_, &inbound_stream);
    VerifyRTCReceivedRtpStreamStats(inbound_stream, verifier);
    verifier.TestMemberIsOptionalIDReference(
        inbound_stream.remote_id, RTCRemoteOutboundRtpStreamStats::kType);
    if (inbound_stream.media_type.is_defined() &&
        *inbound_stream.media_type == "video") {
      verifier.TestMemberIsNonNegative<uint64_t>(inbound_stream.qp_sum);
      verifier.TestMemberIsDefined(inbound_stream.decoder_implementation);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.qp_sum);
      verifier.TestMemberIsUndefined(inbound_stream.decoder_implementation);
    }
    verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.packets_received);
    if (inbound_stream.media_type.is_defined() &&
        *inbound_stream.media_type == "audio") {
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.fec_packets_received);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.fec_packets_discarded);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.fec_packets_received);
      verifier.TestMemberIsUndefined(inbound_stream.fec_packets_discarded);
    }
    verifier.TestMemberIsNonNegative<uint64_t>(inbound_stream.bytes_received);
    verifier.TestMemberIsNonNegative<uint64_t>(
        inbound_stream.header_bytes_received);
    verifier.TestMemberIsDefined(inbound_stream.last_packet_received_timestamp);
    if (inbound_stream.frames_received.ValueOrDefault(0) > 0) {
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.frame_width);
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.frame_height);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.frame_width);
      verifier.TestMemberIsUndefined(inbound_stream.frame_height);
    }
    if (inbound_stream.frames_per_second.is_defined()) {
      verifier.TestMemberIsNonNegative<double>(
          inbound_stream.frames_per_second);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.frames_per_second);
    }
    verifier.TestMemberIsUndefined(inbound_stream.frame_bit_depth);
    if (inbound_stream.media_type.is_defined() &&
        *inbound_stream.media_type == "video") {
      verifier.TestMemberIsUndefined(inbound_stream.jitter_buffer_delay);
      verifier.TestMemberIsUndefined(
          inbound_stream.jitter_buffer_emitted_count);
      verifier.TestMemberIsUndefined(inbound_stream.total_samples_received);
      verifier.TestMemberIsUndefined(inbound_stream.concealed_samples);
      verifier.TestMemberIsUndefined(inbound_stream.silent_concealed_samples);
      verifier.TestMemberIsUndefined(inbound_stream.concealment_events);
      verifier.TestMemberIsUndefined(
          inbound_stream.inserted_samples_for_deceleration);
      verifier.TestMemberIsUndefined(
          inbound_stream.removed_samples_for_acceleration);
      verifier.TestMemberIsUndefined(inbound_stream.audio_level);
      verifier.TestMemberIsUndefined(inbound_stream.total_audio_energy);
      verifier.TestMemberIsUndefined(inbound_stream.total_samples_duration);
      verifier.TestMemberIsNonNegative<int32_t>(inbound_stream.frames_received);
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.fir_count);
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.pli_count);
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.nack_count);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.fir_count);
      verifier.TestMemberIsUndefined(inbound_stream.pli_count);
      verifier.TestMemberIsUndefined(inbound_stream.nack_count);
      verifier.TestMemberIsNonNegative<double>(
          inbound_stream.jitter_buffer_delay);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.jitter_buffer_emitted_count);
      verifier.TestMemberIsPositive<uint64_t>(
          inbound_stream.total_samples_received);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.concealed_samples);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.silent_concealed_samples);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.concealment_events);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.inserted_samples_for_deceleration);
      verifier.TestMemberIsNonNegative<uint64_t>(
          inbound_stream.removed_samples_for_acceleration);
      verifier.TestMemberIsPositive<double>(inbound_stream.audio_level);
      verifier.TestMemberIsPositive<double>(inbound_stream.total_audio_energy);
      verifier.TestMemberIsPositive<double>(
          inbound_stream.total_samples_duration);
      verifier.TestMemberIsUndefined(inbound_stream.frames_received);
    }
    verifier.TestMemberIsUndefined(inbound_stream.round_trip_time);
    verifier.TestMemberIsUndefined(inbound_stream.packets_discarded);
    verifier.TestMemberIsUndefined(inbound_stream.packets_repaired);
    verifier.TestMemberIsUndefined(inbound_stream.burst_packets_lost);
    verifier.TestMemberIsUndefined(inbound_stream.burst_packets_discarded);
    verifier.TestMemberIsUndefined(inbound_stream.burst_loss_count);
    verifier.TestMemberIsUndefined(inbound_stream.burst_discard_count);
    verifier.TestMemberIsUndefined(inbound_stream.burst_loss_rate);
    verifier.TestMemberIsUndefined(inbound_stream.burst_discard_rate);
    verifier.TestMemberIsUndefined(inbound_stream.gap_loss_rate);
    verifier.TestMemberIsUndefined(inbound_stream.gap_discard_rate);
    // Test runtime too short to get an estimate (at least two RTCP sender
    // reports need to be received).
    verifier.MarkMemberTested(inbound_stream.estimated_playout_timestamp, true);
    if (inbound_stream.media_type.is_defined() &&
        *inbound_stream.media_type == "video") {
      verifier.TestMemberIsDefined(inbound_stream.frames_decoded);
      verifier.TestMemberIsDefined(inbound_stream.key_frames_decoded);
      verifier.TestMemberIsNonNegative<uint32_t>(inbound_stream.frames_dropped);
      verifier.TestMemberIsNonNegative<double>(
          inbound_stream.total_decode_time);
      verifier.TestMemberIsNonNegative<double>(
          inbound_stream.total_inter_frame_delay);
      verifier.TestMemberIsNonNegative<double>(
          inbound_stream.total_squared_inter_frame_delay);
      // The integration test is not set up to test screen share; don't require
      // this to be present.
      verifier.MarkMemberTested(inbound_stream.content_type, true);
    } else {
      verifier.TestMemberIsUndefined(inbound_stream.frames_decoded);
      verifier.TestMemberIsUndefined(inbound_stream.key_frames_decoded);
      verifier.TestMemberIsUndefined(inbound_stream.frames_dropped);
      verifier.TestMemberIsUndefined(inbound_stream.total_decode_time);
      verifier.TestMemberIsUndefined(inbound_stream.total_inter_frame_delay);
      verifier.TestMemberIsUndefined(
          inbound_stream.total_squared_inter_frame_delay);
      verifier.TestMemberIsUndefined(inbound_stream.content_type);
    }
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCOutboundRTPStreamStats(
      const RTCOutboundRTPStreamStats& outbound_stream) {
    RTCStatsVerifier verifier(report_, &outbound_stream);
    VerifyRTCRTPStreamStats(outbound_stream, verifier);
    if (outbound_stream.media_type.is_defined() &&
        *outbound_stream.media_type == "video") {
      verifier.TestMemberIsIDReference(outbound_stream.media_source_id,
                                       RTCVideoSourceStats::kType);
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.fir_count);
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.pli_count);
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.nack_count);
      if (*outbound_stream.frames_encoded > 0) {
        verifier.TestMemberIsNonNegative<uint64_t>(outbound_stream.qp_sum);
      } else {
        verifier.TestMemberIsUndefined(outbound_stream.qp_sum);
      }
    } else {
      verifier.TestMemberIsUndefined(outbound_stream.fir_count);
      verifier.TestMemberIsUndefined(outbound_stream.pli_count);
      verifier.TestMemberIsUndefined(outbound_stream.nack_count);
      verifier.TestMemberIsIDReference(outbound_stream.media_source_id,
                                       RTCAudioSourceStats::kType);
      verifier.TestMemberIsUndefined(outbound_stream.qp_sum);
    }
    verifier.TestMemberIsOptionalIDReference(
        outbound_stream.remote_id, RTCRemoteInboundRtpStreamStats::kType);
    verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.packets_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(
        outbound_stream.retransmitted_packets_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(outbound_stream.bytes_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(
        outbound_stream.header_bytes_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(
        outbound_stream.retransmitted_bytes_sent);
    verifier.TestMemberIsUndefined(outbound_stream.target_bitrate);
    if (outbound_stream.media_type.is_defined() &&
        *outbound_stream.media_type == "video") {
      verifier.TestMemberIsDefined(outbound_stream.frames_encoded);
      verifier.TestMemberIsDefined(outbound_stream.key_frames_encoded);
      verifier.TestMemberIsNonNegative<double>(
          outbound_stream.total_encode_time);
      verifier.TestMemberIsNonNegative<uint64_t>(
          outbound_stream.total_encoded_bytes_target);
      verifier.TestMemberIsNonNegative<double>(
          outbound_stream.total_packet_send_delay);
      verifier.TestMemberIsDefined(outbound_stream.quality_limitation_reason);
      verifier.TestMemberIsNonNegative<uint32_t>(
          outbound_stream.quality_limitation_resolution_changes);
      // The integration test is not set up to test screen share; don't require
      // this to be present.
      verifier.MarkMemberTested(outbound_stream.content_type, true);
      verifier.TestMemberIsDefined(outbound_stream.encoder_implementation);
      // Unless an implementation-specific amount of time has passed and at
      // least one frame has been encoded, undefined is reported. Because it
      // is hard to tell what is the case here, we treat FPS as optional.
      // TODO(hbos): Update the tests to run until all implemented metrics
      // should be populated.
      if (outbound_stream.frames_per_second.is_defined()) {
        verifier.TestMemberIsNonNegative<double>(
            outbound_stream.frames_per_second);
      } else {
        verifier.TestMemberIsUndefined(outbound_stream.frames_per_second);
      }
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.frame_height);
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.frame_width);
      verifier.TestMemberIsNonNegative<uint32_t>(outbound_stream.frames_sent);
      verifier.TestMemberIsNonNegative<uint32_t>(
          outbound_stream.huge_frames_sent);
      verifier.MarkMemberTested(outbound_stream.rid, true);
    } else {
      verifier.TestMemberIsUndefined(outbound_stream.frames_encoded);
      verifier.TestMemberIsUndefined(outbound_stream.key_frames_encoded);
      verifier.TestMemberIsUndefined(outbound_stream.total_encode_time);
      verifier.TestMemberIsUndefined(
          outbound_stream.total_encoded_bytes_target);
      // TODO(https://crbug.com/webrtc/10635): Implement for audio as well.
      verifier.TestMemberIsUndefined(outbound_stream.total_packet_send_delay);
      verifier.TestMemberIsUndefined(outbound_stream.quality_limitation_reason);
      verifier.TestMemberIsUndefined(
          outbound_stream.quality_limitation_resolution_changes);
      verifier.TestMemberIsUndefined(outbound_stream.content_type);
      // TODO(hbos): Implement for audio as well.
      verifier.TestMemberIsUndefined(outbound_stream.encoder_implementation);
      verifier.TestMemberIsUndefined(outbound_stream.rid);
      verifier.TestMemberIsUndefined(outbound_stream.frames_per_second);
      verifier.TestMemberIsUndefined(outbound_stream.frame_height);
      verifier.TestMemberIsUndefined(outbound_stream.frame_width);
      verifier.TestMemberIsUndefined(outbound_stream.frames_sent);
      verifier.TestMemberIsUndefined(outbound_stream.huge_frames_sent);
    }
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  void VerifyRTCReceivedRtpStreamStats(
      const RTCReceivedRtpStreamStats& received_rtp,
      RTCStatsVerifier& verifier) {
    VerifyRTCRTPStreamStats(received_rtp, verifier);
    verifier.TestMemberIsNonNegative<double>(received_rtp.jitter);
    verifier.TestMemberIsDefined(received_rtp.packets_lost);
  }

  bool VerifyRTCRemoteInboundRtpStreamStats(
      const RTCRemoteInboundRtpStreamStats& remote_inbound_stream) {
    RTCStatsVerifier verifier(report_, &remote_inbound_stream);
    VerifyRTCReceivedRtpStreamStats(remote_inbound_stream, verifier);
    verifier.TestMemberIsDefined(remote_inbound_stream.fraction_lost);
    verifier.TestMemberIsIDReference(remote_inbound_stream.local_id,
                                     RTCOutboundRTPStreamStats::kType);
    verifier.TestMemberIsNonNegative<double>(
        remote_inbound_stream.round_trip_time);
    verifier.TestMemberIsNonNegative<double>(
        remote_inbound_stream.total_round_trip_time);
    verifier.TestMemberIsNonNegative<int32_t>(
        remote_inbound_stream.round_trip_time_measurements);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCRemoteOutboundRTPStreamStats(
      const RTCRemoteOutboundRtpStreamStats& remote_outbound_stream) {
    RTCStatsVerifier verifier(report_, &remote_outbound_stream);
    VerifyRTCRTPStreamStats(remote_outbound_stream, verifier);
    VerifyRTCSentRTPStreamStats(remote_outbound_stream, verifier);
    verifier.TestMemberIsIDReference(remote_outbound_stream.local_id,
                                     RTCOutboundRTPStreamStats::kType);
    verifier.TestMemberIsNonNegative<double>(
        remote_outbound_stream.remote_timestamp);
    verifier.TestMemberIsDefined(remote_outbound_stream.reports_sent);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  void VerifyRTCMediaSourceStats(const RTCMediaSourceStats& media_source,
                                 RTCStatsVerifier* verifier) {
    verifier->TestMemberIsDefined(media_source.track_identifier);
    verifier->TestMemberIsDefined(media_source.kind);
    if (media_source.kind.is_defined()) {
      EXPECT_TRUE((*media_source.kind == "audio" &&
                   media_source.type() == RTCAudioSourceStats::kType) ||
                  (*media_source.kind == "video" &&
                   media_source.type() == RTCVideoSourceStats::kType));
    }
  }

  bool VerifyRTCAudioSourceStats(const RTCAudioSourceStats& audio_source) {
    RTCStatsVerifier verifier(report_, &audio_source);
    VerifyRTCMediaSourceStats(audio_source, &verifier);
    // Audio level, unlike audio energy, only gets updated at a certain
    // frequency, so we don't require that one to be positive to avoid a race
    // (https://crbug.com/webrtc/10962).
    verifier.TestMemberIsNonNegative<double>(audio_source.audio_level);
    verifier.TestMemberIsPositive<double>(audio_source.total_audio_energy);
    verifier.TestMemberIsPositive<double>(audio_source.total_samples_duration);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCVideoSourceStats(const RTCVideoSourceStats& video_source) {
    RTCStatsVerifier verifier(report_, &video_source);
    VerifyRTCMediaSourceStats(video_source, &verifier);
    // TODO(hbos): This integration test uses fakes that doesn't support
    // VideoTrackSourceInterface::Stats. When this is fixed we should
    // TestMemberIsNonNegative<uint32_t>() for |width| and |height| instead to
    // reflect real code.
    verifier.TestMemberIsUndefined(video_source.width);
    verifier.TestMemberIsUndefined(video_source.height);
    verifier.TestMemberIsNonNegative<uint32_t>(video_source.frames);
    verifier.TestMemberIsNonNegative<uint32_t>(video_source.frames_per_second);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

  bool VerifyRTCTransportStats(const RTCTransportStats& transport) {
    RTCStatsVerifier verifier(report_, &transport);
    verifier.TestMemberIsNonNegative<uint64_t>(transport.bytes_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(transport.packets_sent);
    verifier.TestMemberIsNonNegative<uint64_t>(transport.bytes_received);
    verifier.TestMemberIsNonNegative<uint64_t>(transport.packets_received);
    verifier.TestMemberIsOptionalIDReference(transport.rtcp_transport_stats_id,
                                             RTCTransportStats::kType);
    verifier.TestMemberIsDefined(transport.dtls_state);
    verifier.TestMemberIsIDReference(transport.selected_candidate_pair_id,
                                     RTCIceCandidatePairStats::kType);
    verifier.TestMemberIsIDReference(transport.local_certificate_id,
                                     RTCCertificateStats::kType);
    verifier.TestMemberIsIDReference(transport.remote_certificate_id,
                                     RTCCertificateStats::kType);
    verifier.TestMemberIsDefined(transport.tls_version);
    verifier.TestMemberIsDefined(transport.dtls_cipher);
    verifier.TestMemberIsDefined(transport.srtp_cipher);
    verifier.TestMemberIsPositive<uint32_t>(
        transport.selected_candidate_pair_changes);
    return verifier.ExpectAllMembersSuccessfullyTested();
  }

 private:
  rtc::scoped_refptr<const RTCStatsReport> report_;
};

#ifdef WEBRTC_HAVE_SCTP
TEST_F(RTCStatsIntegrationTest, GetStatsFromCaller) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCaller();
  RTCStatsReportVerifier(report.get()).VerifyReport({});

#if RTC_TRACE_EVENTS_ENABLED
  EXPECT_EQ(report->ToJson(), RTCStatsReportTraceListener::last_trace());
  #endif
}

TEST_F(RTCStatsIntegrationTest, GetStatsFromCallee) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCallee();
  RTCStatsReportVerifier(report.get()).VerifyReport({});

#if RTC_TRACE_EVENTS_ENABLED
  EXPECT_EQ(report->ToJson(), RTCStatsReportTraceListener::last_trace());
  #endif
}

// These tests exercise the integration of the stats selection algorithm inside
// of PeerConnection. See rtcstatstraveral_unittest.cc for more detailed stats
// traversal tests on particular stats graphs.
TEST_F(RTCStatsIntegrationTest, GetStatsWithSenderSelector) {
  StartCall();
  ASSERT_FALSE(caller_->pc()->GetSenders().empty());
  rtc::scoped_refptr<const RTCStatsReport> report =
      GetStatsFromCaller(caller_->pc()->GetSenders()[0]);
  std::vector<const char*> allowed_missing_stats = {
      // TODO(hbos): Include RTC[Audio/Video]ReceiverStats when implemented.
      // TODO(hbos): Include RTCRemoteOutboundRtpStreamStats when implemented.
      // TODO(hbos): Include RTCRtpContributingSourceStats when implemented.
      RTCInboundRTPStreamStats::kType,
      RTCPeerConnectionStats::kType,
      RTCMediaStreamStats::kType,
      RTCDataChannelStats::kType,
  };
  RTCStatsReportVerifier(report.get()).VerifyReport(allowed_missing_stats);
  EXPECT_TRUE(report->size());
}

TEST_F(RTCStatsIntegrationTest, GetStatsWithReceiverSelector) {
  StartCall();

  ASSERT_FALSE(caller_->pc()->GetReceivers().empty());
  rtc::scoped_refptr<const RTCStatsReport> report =
      GetStatsFromCaller(caller_->pc()->GetReceivers()[0]);
  std::vector<const char*> allowed_missing_stats = {
      // TODO(hbos): Include RTC[Audio/Video]SenderStats when implemented.
      // TODO(hbos): Include RTCRemoteInboundRtpStreamStats when implemented.
      // TODO(hbos): Include RTCRtpContributingSourceStats when implemented.
      RTCOutboundRTPStreamStats::kType,
      RTCPeerConnectionStats::kType,
      RTCMediaStreamStats::kType,
      RTCDataChannelStats::kType,
  };
  RTCStatsReportVerifier(report.get()).VerifyReport(allowed_missing_stats);
  EXPECT_TRUE(report->size());
}

TEST_F(RTCStatsIntegrationTest, GetStatsWithInvalidSenderSelector) {
  StartCall();

  ASSERT_FALSE(callee_->pc()->GetSenders().empty());
  // The selector is invalid for the caller because it belongs to the callee.
  auto invalid_selector = callee_->pc()->GetSenders()[0];
  rtc::scoped_refptr<const RTCStatsReport> report =
      GetStatsFromCaller(invalid_selector);
  EXPECT_FALSE(report->size());
}

TEST_F(RTCStatsIntegrationTest, GetStatsWithInvalidReceiverSelector) {
  StartCall();

  ASSERT_FALSE(callee_->pc()->GetReceivers().empty());
  // The selector is invalid for the caller because it belongs to the callee.
  auto invalid_selector = callee_->pc()->GetReceivers()[0];
  rtc::scoped_refptr<const RTCStatsReport> report =
      GetStatsFromCaller(invalid_selector);
  EXPECT_FALSE(report->size());
}

// TODO(bugs.webrtc.org/10041) For now this is equivalent to the following
// test GetsStatsWhileClosingPeerConnection, because pc() is closed by
// PeerConnectionTestWrapper. See: bugs.webrtc.org/9847
TEST_F(RTCStatsIntegrationTest,
       DISABLED_GetStatsWhileDestroyingPeerConnection) {
  StartCall();

  rtc::scoped_refptr<RTCStatsObtainer> stats_obtainer =
      RTCStatsObtainer::Create();
  caller_->pc()->GetStats(stats_obtainer);
  // This will destroy the peer connection.
  caller_ = nullptr;
  // Any pending stats requests should have completed in the act of destroying
  // the peer connection.
  ASSERT_TRUE(stats_obtainer->report());
  #if RTC_TRACE_EVENTS_ENABLED
  EXPECT_EQ(stats_obtainer->report()->ToJson(),
            RTCStatsReportTraceListener::last_trace());
  #endif
}

TEST_F(RTCStatsIntegrationTest, GetsStatsWhileClosingPeerConnection) {
  StartCall();

  rtc::scoped_refptr<RTCStatsObtainer> stats_obtainer =
      RTCStatsObtainer::Create();
  caller_->pc()->GetStats(stats_obtainer);
  caller_->pc()->Close();

  ASSERT_TRUE(stats_obtainer->report());
  #if RTC_TRACE_EVENTS_ENABLED
  EXPECT_EQ(stats_obtainer->report()->ToJson(),
            RTCStatsReportTraceListener::last_trace());
  #endif
}

// GetStatsReferencedIds() is optimized to recognize what is or isn't a
// referenced ID based on dictionary type information and knowing what members
// are used as references, as opposed to iterating all members to find the ones
// with the "Id" or "Ids" suffix. As such, GetStatsReferencedIds() is tested as
// an integration test instead of a unit test in order to guard against adding
// new references and forgetting to update GetStatsReferencedIds().
TEST_F(RTCStatsIntegrationTest, GetStatsReferencedIds) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCallee();
  for (const RTCStats& stats : *report) {
    // Find all references by looking at all string members with the "Id" or
    // "Ids" suffix.
    std::set<const std::string*> expected_ids;
    for (const auto* member : stats.Members()) {
      if (!member->is_defined())
        continue;
      if (member->type() == RTCStatsMemberInterface::kString) {
        if (absl::EndsWith(member->name(), "Id")) {
          const auto& id = member->cast_to<const RTCStatsMember<std::string>>();
          expected_ids.insert(&(*id));
        }
      } else if (member->type() == RTCStatsMemberInterface::kSequenceString) {
        if (absl::EndsWith(member->name(), "Ids")) {
          const auto& ids =
              member->cast_to<const RTCStatsMember<std::vector<std::string>>>();
          for (const std::string& id : *ids)
            expected_ids.insert(&id);
        }
      }
    }

    std::vector<const std::string*> neighbor_ids = GetStatsReferencedIds(stats);
    EXPECT_EQ(neighbor_ids.size(), expected_ids.size());
    for (const std::string* neighbor_id : neighbor_ids) {
      EXPECT_THAT(expected_ids, Contains(neighbor_id));
    }
    for (const std::string* expected_id : expected_ids) {
      EXPECT_THAT(neighbor_ids, Contains(expected_id));
    }
  }
}

TEST_F(RTCStatsIntegrationTest, GetStatsContainsNoDuplicateMembers) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCallee();
  for (const RTCStats& stats : *report) {
    std::set<std::string> member_names;
    for (const auto* member : stats.Members()) {
      EXPECT_TRUE(member_names.find(member->name()) == member_names.end())
          << member->name() << " is a duplicate!";
      member_names.insert(member->name());
    }
  }
}
#endif  // WEBRTC_HAVE_SCTP

}  // namespace

}  // namespace webrtc
