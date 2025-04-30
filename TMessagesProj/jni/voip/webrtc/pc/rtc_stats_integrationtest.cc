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

#include <memory>
#include <set>
#include <string>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/types/optional.h"
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
        "caller", &virtual_socket_server_, network_thread_.get(),
        worker_thread_.get());
    callee_ = rtc::make_ref_counted<PeerConnectionTestWrapper>(
        "callee", &virtual_socket_server_, network_thread_.get(),
        worker_thread_.get());
  }

  void StartCall() {
    // Create PeerConnections and "connect" sigslots
    PeerConnectionInterface::RTCConfiguration config;
    config.sdp_semantics = SdpSemantics::kUnifiedPlan;
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
    pc->GetStats(stats_obtainer.get());
    EXPECT_TRUE_WAIT(stats_obtainer->report() != nullptr, kGetStatsTimeoutMs);
    return stats_obtainer->report();
  }

  template <typename T>
  static rtc::scoped_refptr<const RTCStatsReport> GetStats(
      PeerConnectionInterface* pc,
      rtc::scoped_refptr<T> selector) {
    rtc::scoped_refptr<RTCStatsObtainer> stats_obtainer =
        RTCStatsObtainer::Create();
    pc->GetStats(selector, stats_obtainer);
    EXPECT_TRUE_WAIT(stats_obtainer->report() != nullptr, kGetStatsTimeoutMs);
    return stats_obtainer->report();
  }

  // `network_thread_` uses `virtual_socket_server_` so they must be
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
    for (const auto& attribute : stats_->Attributes()) {
      untested_attribute_names_.insert(attribute.name());
    }
  }

  template <typename T>
  void MarkAttributeTested(const absl::optional<T>& field,
                           bool test_successful) {
    untested_attribute_names_.erase(stats_->GetAttribute(field).name());
    all_tests_successful_ &= test_successful;
  }

  template <typename T>
  void TestAttributeIsDefined(const absl::optional<T>& field) {
    EXPECT_TRUE(field.has_value())
        << stats_->type() << "." << stats_->GetAttribute(field).name() << "["
        << stats_->id() << "] was undefined.";
    MarkAttributeTested(field, field.has_value());
  }

  template <typename T>
  void TestAttributeIsUndefined(const absl::optional<T>& field) {
    Attribute attribute = stats_->GetAttribute(field);
    EXPECT_FALSE(field.has_value())
        << stats_->type() << "." << attribute.name() << "[" << stats_->id()
        << "] was defined (" << attribute.ToString() << ").";
    MarkAttributeTested(field, !field.has_value());
  }

  template <typename T>
  void TestAttributeIsPositive(const absl::optional<T>& field) {
    Attribute attribute = stats_->GetAttribute(field);
    EXPECT_TRUE(field.has_value()) << stats_->type() << "." << attribute.name()
                                   << "[" << stats_->id() << "] was undefined.";
    if (!field.has_value()) {
      MarkAttributeTested(field, false);
      return;
    }
    bool is_positive = field.value() > T(0);
    EXPECT_TRUE(is_positive)
        << stats_->type() << "." << attribute.name() << "[" << stats_->id()
        << "] was not positive (" << attribute.ToString() << ").";
    MarkAttributeTested(field, is_positive);
  }

  template <typename T>
  void TestAttributeIsNonNegative(const absl::optional<T>& field) {
    Attribute attribute = stats_->GetAttribute(field);
    EXPECT_TRUE(field.has_value()) << stats_->type() << "." << attribute.name()
                                   << "[" << stats_->id() << "] was undefined.";
    if (!field.has_value()) {
      MarkAttributeTested(field, false);
      return;
    }
    bool is_non_negative = field.value() >= T(0);
    EXPECT_TRUE(is_non_negative)
        << stats_->type() << "." << attribute.name() << "[" << stats_->id()
        << "] was not non-negative (" << attribute.ToString() << ").";
    MarkAttributeTested(field, is_non_negative);
  }

  template <typename T>
  void TestAttributeIsIDReference(const absl::optional<T>& field,
                                  const char* expected_type) {
    TestAttributeIsIDReference(field, expected_type, false);
  }

  template <typename T>
  void TestAttributeIsOptionalIDReference(const absl::optional<T>& field,
                                          const char* expected_type) {
    TestAttributeIsIDReference(field, expected_type, true);
  }

  bool ExpectAllAttributesSuccessfullyTested() {
    if (untested_attribute_names_.empty())
      return all_tests_successful_;
    for (const char* name : untested_attribute_names_) {
      EXPECT_TRUE(false) << stats_->type() << "." << name << "[" << stats_->id()
                         << "] was not tested.";
    }
    return false;
  }

 private:
  template <typename T>
  void TestAttributeIsIDReference(const absl::optional<T>& field,
                                  const char* expected_type,
                                  bool optional) {
    if (optional && !field.has_value()) {
      MarkAttributeTested(field, true);
      return;
    }
    Attribute attribute = stats_->GetAttribute(field);
    bool valid_reference = false;
    if (attribute.has_value()) {
      if (attribute.holds_alternative<std::string>()) {
        // A single ID.
        const RTCStats* referenced_stats =
            report_->Get(attribute.get<std::string>());
        valid_reference =
            referenced_stats && referenced_stats->type() == expected_type;
      } else if (attribute.holds_alternative<std::vector<std::string>>()) {
        // A vector of IDs.
        valid_reference = true;
        for (const std::string& id :
             attribute.get<std::vector<std::string>>()) {
          const RTCStats* referenced_stats = report_->Get(id);
          if (!referenced_stats || referenced_stats->type() != expected_type) {
            valid_reference = false;
            break;
          }
        }
      }
    }
    EXPECT_TRUE(valid_reference)
        << stats_->type() << "." << attribute.name()
        << " is not a reference to an "
           "existing dictionary of type "
        << expected_type << " (value: " << attribute.ToString() << ").";
    MarkAttributeTested(field, valid_reference);
  }

  rtc::scoped_refptr<const RTCStatsReport> report_;
  const RTCStats* stats_;
  std::set<const char*> untested_attribute_names_;
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
    stats_types.insert(RTCPeerConnectionStats::kType);
    stats_types.insert(RTCInboundRtpStreamStats::kType);
    stats_types.insert(RTCOutboundRtpStreamStats::kType);
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
      } else if (stats.type() == RTCPeerConnectionStats::kType) {
        verify_successful &= VerifyRTCPeerConnectionStats(
            stats.cast_to<RTCPeerConnectionStats>());
      } else if (stats.type() == RTCInboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCInboundRtpStreamStats(
            stats.cast_to<RTCInboundRtpStreamStats>());
      } else if (stats.type() == RTCOutboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCOutboundRtpStreamStats(
            stats.cast_to<RTCOutboundRtpStreamStats>());
      } else if (stats.type() == RTCRemoteInboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCRemoteInboundRtpStreamStats(
            stats.cast_to<RTCRemoteInboundRtpStreamStats>());
      } else if (stats.type() == RTCRemoteOutboundRtpStreamStats::kType) {
        verify_successful &= VerifyRTCRemoteOutboundRtpStreamStats(
            stats.cast_to<RTCRemoteOutboundRtpStreamStats>());
      } else if (stats.type() == RTCAudioSourceStats::kType) {
        // RTCAudioSourceStats::kType and RTCVideoSourceStats::kType both have
        // the value "media-source", but they are distinguishable with pointer
        // equality (==). In JavaScript they would be distinguished with `kind`.
        verify_successful &=
            VerifyRTCAudioSourceStats(stats.cast_to<RTCAudioSourceStats>());
      } else if (stats.type() == RTCVideoSourceStats::kType) {
        // RTCAudioSourceStats::kType and RTCVideoSourceStats::kType both have
        // the value "media-source", but they are distinguishable with pointer
        // equality (==). In JavaScript they would be distinguished with `kind`.
        verify_successful &=
            VerifyRTCVideoSourceStats(stats.cast_to<RTCVideoSourceStats>());
      } else if (stats.type() == RTCTransportStats::kType) {
        verify_successful &=
            VerifyRTCTransportStats(stats.cast_to<RTCTransportStats>());
      } else if (stats.type() == RTCAudioPlayoutStats::kType) {
        verify_successful &=
            VerifyRTCAudioPlayoutStats(stats.cast_to<RTCAudioPlayoutStats>());
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
    RTCStatsVerifier verifier(report_.get(), &certificate);
    verifier.TestAttributeIsDefined(certificate.fingerprint);
    verifier.TestAttributeIsDefined(certificate.fingerprint_algorithm);
    verifier.TestAttributeIsDefined(certificate.base64_certificate);
    verifier.TestAttributeIsOptionalIDReference(
        certificate.issuer_certificate_id, RTCCertificateStats::kType);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCCodecStats(const RTCCodecStats& codec) {
    RTCStatsVerifier verifier(report_.get(), &codec);
    verifier.TestAttributeIsIDReference(codec.transport_id,
                                        RTCTransportStats::kType);
    verifier.TestAttributeIsDefined(codec.payload_type);
    verifier.TestAttributeIsDefined(codec.mime_type);
    verifier.TestAttributeIsPositive<uint32_t>(codec.clock_rate);

    if (codec.mime_type->rfind("audio", 0) == 0)
      verifier.TestAttributeIsPositive<uint32_t>(codec.channels);
    else
      verifier.TestAttributeIsUndefined(codec.channels);

    // sdp_fmtp_line is an optional field.
    verifier.MarkAttributeTested(codec.sdp_fmtp_line, true);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCDataChannelStats(const RTCDataChannelStats& data_channel) {
    RTCStatsVerifier verifier(report_.get(), &data_channel);
    verifier.TestAttributeIsDefined(data_channel.label);
    verifier.TestAttributeIsDefined(data_channel.protocol);
    verifier.TestAttributeIsDefined(data_channel.data_channel_identifier);
    verifier.TestAttributeIsDefined(data_channel.state);
    verifier.TestAttributeIsNonNegative<uint32_t>(data_channel.messages_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(data_channel.bytes_sent);
    verifier.TestAttributeIsNonNegative<uint32_t>(
        data_channel.messages_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(data_channel.bytes_received);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCIceCandidatePairStats(
      const RTCIceCandidatePairStats& candidate_pair,
      bool is_selected_pair) {
    RTCStatsVerifier verifier(report_.get(), &candidate_pair);
    verifier.TestAttributeIsIDReference(candidate_pair.transport_id,
                                        RTCTransportStats::kType);
    verifier.TestAttributeIsIDReference(candidate_pair.local_candidate_id,
                                        RTCLocalIceCandidateStats::kType);
    verifier.TestAttributeIsIDReference(candidate_pair.remote_candidate_id,
                                        RTCRemoteIceCandidateStats::kType);
    verifier.TestAttributeIsDefined(candidate_pair.state);
    verifier.TestAttributeIsNonNegative<uint64_t>(candidate_pair.priority);
    verifier.TestAttributeIsDefined(candidate_pair.nominated);
    verifier.TestAttributeIsDefined(candidate_pair.writable);
    verifier.TestAttributeIsNonNegative<uint64_t>(candidate_pair.packets_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.packets_discarded_on_send);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.packets_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(candidate_pair.bytes_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.bytes_discarded_on_send);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.bytes_received);
    verifier.TestAttributeIsNonNegative<double>(
        candidate_pair.total_round_trip_time);
    verifier.TestAttributeIsNonNegative<double>(
        candidate_pair.current_round_trip_time);
    if (is_selected_pair) {
      verifier.TestAttributeIsNonNegative<double>(
          candidate_pair.available_outgoing_bitrate);
      // A pair should be nominated in order to be selected.
      EXPECT_TRUE(*candidate_pair.nominated);
    } else {
      verifier.TestAttributeIsUndefined(
          candidate_pair.available_outgoing_bitrate);
    }
    verifier.TestAttributeIsUndefined(
        candidate_pair.available_incoming_bitrate);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.requests_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(candidate_pair.requests_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.responses_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.responses_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        candidate_pair.consent_requests_sent);
    verifier.TestAttributeIsDefined(
        candidate_pair.last_packet_received_timestamp);
    verifier.TestAttributeIsDefined(candidate_pair.last_packet_sent_timestamp);

    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCIceCandidateStats(const RTCIceCandidateStats& candidate) {
    RTCStatsVerifier verifier(report_.get(), &candidate);
    verifier.TestAttributeIsIDReference(candidate.transport_id,
                                        RTCTransportStats::kType);
    verifier.TestAttributeIsDefined(candidate.is_remote);
    if (*candidate.is_remote) {
      verifier.TestAttributeIsUndefined(candidate.network_type);
      verifier.TestAttributeIsUndefined(candidate.network_adapter_type);
      verifier.TestAttributeIsUndefined(candidate.vpn);
    } else {
      verifier.TestAttributeIsDefined(candidate.network_type);
      verifier.TestAttributeIsDefined(candidate.network_adapter_type);
      verifier.TestAttributeIsDefined(candidate.vpn);
    }
    verifier.TestAttributeIsDefined(candidate.ip);
    verifier.TestAttributeIsDefined(candidate.address);
    verifier.TestAttributeIsNonNegative<int32_t>(candidate.port);
    verifier.TestAttributeIsDefined(candidate.protocol);
    verifier.TestAttributeIsDefined(candidate.candidate_type);
    verifier.TestAttributeIsNonNegative<int32_t>(candidate.priority);
    verifier.TestAttributeIsUndefined(candidate.url);
    verifier.TestAttributeIsUndefined(candidate.relay_protocol);
    verifier.TestAttributeIsDefined(candidate.foundation);
    verifier.TestAttributeIsUndefined(candidate.related_address);
    verifier.TestAttributeIsUndefined(candidate.related_port);
    verifier.TestAttributeIsDefined(candidate.username_fragment);
    verifier.TestAttributeIsUndefined(candidate.tcp_type);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCLocalIceCandidateStats(
      const RTCLocalIceCandidateStats& local_candidate) {
    return VerifyRTCIceCandidateStats(local_candidate);
  }

  bool VerifyRTCRemoteIceCandidateStats(
      const RTCRemoteIceCandidateStats& remote_candidate) {
    return VerifyRTCIceCandidateStats(remote_candidate);
  }

  bool VerifyRTCPeerConnectionStats(
      const RTCPeerConnectionStats& peer_connection) {
    RTCStatsVerifier verifier(report_.get(), &peer_connection);
    verifier.TestAttributeIsNonNegative<uint32_t>(
        peer_connection.data_channels_opened);
    verifier.TestAttributeIsNonNegative<uint32_t>(
        peer_connection.data_channels_closed);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  void VerifyRTCRtpStreamStats(const RTCRtpStreamStats& stream,
                               RTCStatsVerifier& verifier) {
    verifier.TestAttributeIsDefined(stream.ssrc);
    verifier.TestAttributeIsDefined(stream.kind);
    verifier.TestAttributeIsIDReference(stream.transport_id,
                                        RTCTransportStats::kType);
    verifier.TestAttributeIsIDReference(stream.codec_id, RTCCodecStats::kType);
  }

  void VerifyRTCSentRtpStreamStats(const RTCSentRtpStreamStats& sent_stream,
                                   RTCStatsVerifier& verifier) {
    VerifyRTCRtpStreamStats(sent_stream, verifier);
    verifier.TestAttributeIsNonNegative<uint64_t>(sent_stream.packets_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(sent_stream.bytes_sent);
  }

  bool VerifyRTCInboundRtpStreamStats(
      const RTCInboundRtpStreamStats& inbound_stream) {
    RTCStatsVerifier verifier(report_.get(), &inbound_stream);
    VerifyRTCReceivedRtpStreamStats(inbound_stream, verifier);
    verifier.TestAttributeIsOptionalIDReference(
        inbound_stream.remote_id, RTCRemoteOutboundRtpStreamStats::kType);
    verifier.TestAttributeIsDefined(inbound_stream.mid);
    verifier.TestAttributeIsDefined(inbound_stream.track_identifier);
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "video") {
      verifier.TestAttributeIsNonNegative<uint64_t>(inbound_stream.qp_sum);
      verifier.TestAttributeIsDefined(inbound_stream.decoder_implementation);
      verifier.TestAttributeIsDefined(inbound_stream.power_efficient_decoder);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.qp_sum);
      verifier.TestAttributeIsUndefined(inbound_stream.decoder_implementation);
      verifier.TestAttributeIsUndefined(inbound_stream.power_efficient_decoder);
    }
    verifier.TestAttributeIsNonNegative<uint32_t>(
        inbound_stream.packets_received);
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "audio") {
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.packets_discarded);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.fec_packets_received);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.fec_packets_discarded);
      verifier.TestAttributeIsUndefined(inbound_stream.fec_bytes_received);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.packets_discarded);
      // FEC stats are only present when FlexFEC was negotiated which is guarded
      // by the WebRTC-FlexFEC-03-Advertised/Enabled/ field trial and off by
      // default.
      verifier.TestAttributeIsUndefined(inbound_stream.fec_bytes_received);
      verifier.TestAttributeIsUndefined(inbound_stream.fec_packets_received);
      verifier.TestAttributeIsUndefined(inbound_stream.fec_packets_discarded);
      verifier.TestAttributeIsUndefined(inbound_stream.fec_ssrc);
    }
    verifier.TestAttributeIsNonNegative<uint64_t>(
        inbound_stream.bytes_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        inbound_stream.header_bytes_received);
    verifier.TestAttributeIsDefined(
        inbound_stream.last_packet_received_timestamp);
    if (inbound_stream.frames_received.value_or(0) > 0) {
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.frame_width);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          inbound_stream.frame_height);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.frame_width);
      verifier.TestAttributeIsUndefined(inbound_stream.frame_height);
    }
    if (inbound_stream.frames_per_second.has_value()) {
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.frames_per_second);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.frames_per_second);
    }
    verifier.TestAttributeIsNonNegative<double>(
        inbound_stream.jitter_buffer_delay);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        inbound_stream.jitter_buffer_emitted_count);
    verifier.TestAttributeIsNonNegative<double>(
        inbound_stream.jitter_buffer_target_delay);
    verifier.TestAttributeIsNonNegative<double>(
        inbound_stream.jitter_buffer_minimum_delay);
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "video") {
      verifier.TestAttributeIsUndefined(inbound_stream.total_samples_received);
      verifier.TestAttributeIsUndefined(inbound_stream.concealed_samples);
      verifier.TestAttributeIsUndefined(
          inbound_stream.silent_concealed_samples);
      verifier.TestAttributeIsUndefined(inbound_stream.concealment_events);
      verifier.TestAttributeIsUndefined(
          inbound_stream.inserted_samples_for_deceleration);
      verifier.TestAttributeIsUndefined(
          inbound_stream.removed_samples_for_acceleration);
      verifier.TestAttributeIsUndefined(inbound_stream.audio_level);
      verifier.TestAttributeIsUndefined(inbound_stream.total_audio_energy);
      verifier.TestAttributeIsUndefined(inbound_stream.total_samples_duration);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          inbound_stream.frames_received);
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.fir_count);
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.pli_count);
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.nack_count);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.fir_count);
      verifier.TestAttributeIsUndefined(inbound_stream.pli_count);
      verifier.TestAttributeIsUndefined(inbound_stream.nack_count);
      verifier.TestAttributeIsPositive<uint64_t>(
          inbound_stream.total_samples_received);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.concealed_samples);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.silent_concealed_samples);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.concealment_events);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.inserted_samples_for_deceleration);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.removed_samples_for_acceleration);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.jitter_buffer_target_delay);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.jitter_buffer_minimum_delay);
      verifier.TestAttributeIsPositive<double>(inbound_stream.audio_level);
      verifier.TestAttributeIsPositive<double>(
          inbound_stream.total_audio_energy);
      verifier.TestAttributeIsPositive<double>(
          inbound_stream.total_samples_duration);
      verifier.TestAttributeIsUndefined(inbound_stream.frames_received);
    }

    // RTX stats are typically only defined for video where RTX is negotiated.
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "video") {
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.retransmitted_packets_received);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.retransmitted_bytes_received);
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.rtx_ssrc);
    } else {
      verifier.TestAttributeIsUndefined(
          inbound_stream.retransmitted_packets_received);
      verifier.TestAttributeIsUndefined(
          inbound_stream.retransmitted_bytes_received);
      verifier.TestAttributeIsUndefined(inbound_stream.rtx_ssrc);
      verifier.TestAttributeIsUndefined(inbound_stream.fec_ssrc);
    }

    // Test runtime too short to get an estimate (at least two RTCP sender
    // reports need to be received).
    verifier.MarkAttributeTested(inbound_stream.estimated_playout_timestamp,
                                 true);
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "video") {
      verifier.TestAttributeIsDefined(inbound_stream.frames_decoded);
      verifier.TestAttributeIsDefined(inbound_stream.key_frames_decoded);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          inbound_stream.frames_dropped);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_decode_time);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_processing_delay);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_assembly_time);
      verifier.TestAttributeIsDefined(
          inbound_stream.frames_assembled_from_multiple_packets);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_inter_frame_delay);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_squared_inter_frame_delay);
      verifier.TestAttributeIsNonNegative<uint32_t>(inbound_stream.pause_count);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_pauses_duration);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          inbound_stream.freeze_count);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_freezes_duration);
      // The integration test is not set up to test screen share; don't require
      // this to be present.
      verifier.MarkAttributeTested(inbound_stream.content_type, true);
      verifier.TestAttributeIsUndefined(inbound_stream.jitter_buffer_flushes);
      verifier.TestAttributeIsUndefined(
          inbound_stream.delayed_packet_outage_samples);
      verifier.TestAttributeIsUndefined(
          inbound_stream.relative_packet_arrival_delay);
      verifier.TestAttributeIsUndefined(inbound_stream.interruption_count);
      verifier.TestAttributeIsUndefined(
          inbound_stream.total_interruption_duration);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.min_playout_delay);
      verifier.TestAttributeIsDefined(inbound_stream.goog_timing_frame_info);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.frames_decoded);
      verifier.TestAttributeIsUndefined(inbound_stream.key_frames_decoded);
      verifier.TestAttributeIsUndefined(inbound_stream.frames_dropped);
      verifier.TestAttributeIsUndefined(inbound_stream.total_decode_time);
      verifier.TestAttributeIsUndefined(inbound_stream.total_processing_delay);
      verifier.TestAttributeIsUndefined(inbound_stream.total_assembly_time);
      verifier.TestAttributeIsUndefined(
          inbound_stream.frames_assembled_from_multiple_packets);
      verifier.TestAttributeIsUndefined(inbound_stream.total_inter_frame_delay);
      verifier.TestAttributeIsUndefined(
          inbound_stream.total_squared_inter_frame_delay);
      verifier.TestAttributeIsUndefined(inbound_stream.pause_count);
      verifier.TestAttributeIsUndefined(inbound_stream.total_pauses_duration);
      verifier.TestAttributeIsUndefined(inbound_stream.freeze_count);
      verifier.TestAttributeIsUndefined(inbound_stream.total_freezes_duration);
      verifier.TestAttributeIsUndefined(inbound_stream.content_type);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.jitter_buffer_flushes);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          inbound_stream.delayed_packet_outage_samples);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.relative_packet_arrival_delay);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          inbound_stream.interruption_count);
      verifier.TestAttributeIsNonNegative<double>(
          inbound_stream.total_interruption_duration);
      verifier.TestAttributeIsUndefined(inbound_stream.min_playout_delay);
      verifier.TestAttributeIsUndefined(inbound_stream.goog_timing_frame_info);
    }
    if (inbound_stream.kind.has_value() && *inbound_stream.kind == "audio") {
      verifier.TestAttributeIsDefined(inbound_stream.playout_id);
    } else {
      verifier.TestAttributeIsUndefined(inbound_stream.playout_id);
    }

    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCOutboundRtpStreamStats(
      const RTCOutboundRtpStreamStats& outbound_stream) {
    RTCStatsVerifier verifier(report_.get(), &outbound_stream);
    VerifyRTCSentRtpStreamStats(outbound_stream, verifier);

    verifier.TestAttributeIsDefined(outbound_stream.mid);
    verifier.TestAttributeIsDefined(outbound_stream.active);
    if (outbound_stream.kind.has_value() && *outbound_stream.kind == "video") {
      verifier.TestAttributeIsIDReference(outbound_stream.media_source_id,
                                          RTCVideoSourceStats::kType);
      verifier.TestAttributeIsNonNegative<uint32_t>(outbound_stream.fir_count);
      verifier.TestAttributeIsNonNegative<uint32_t>(outbound_stream.pli_count);
      if (*outbound_stream.frames_encoded > 0) {
        verifier.TestAttributeIsNonNegative<uint64_t>(outbound_stream.qp_sum);
      } else {
        verifier.TestAttributeIsUndefined(outbound_stream.qp_sum);
      }
    } else {
      verifier.TestAttributeIsUndefined(outbound_stream.fir_count);
      verifier.TestAttributeIsUndefined(outbound_stream.pli_count);
      verifier.TestAttributeIsIDReference(outbound_stream.media_source_id,
                                          RTCAudioSourceStats::kType);
      verifier.TestAttributeIsUndefined(outbound_stream.qp_sum);
    }
    verifier.TestAttributeIsNonNegative<uint32_t>(outbound_stream.nack_count);
    verifier.TestAttributeIsOptionalIDReference(
        outbound_stream.remote_id, RTCRemoteInboundRtpStreamStats::kType);
    verifier.TestAttributeIsNonNegative<double>(
        outbound_stream.total_packet_send_delay);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        outbound_stream.retransmitted_packets_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        outbound_stream.header_bytes_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        outbound_stream.retransmitted_bytes_sent);
    verifier.TestAttributeIsNonNegative<double>(outbound_stream.target_bitrate);
    if (outbound_stream.kind.has_value() && *outbound_stream.kind == "video") {
      verifier.TestAttributeIsDefined(outbound_stream.frames_encoded);
      verifier.TestAttributeIsDefined(outbound_stream.key_frames_encoded);
      verifier.TestAttributeIsNonNegative<double>(
          outbound_stream.total_encode_time);
      verifier.TestAttributeIsNonNegative<uint64_t>(
          outbound_stream.total_encoded_bytes_target);
      verifier.TestAttributeIsDefined(
          outbound_stream.quality_limitation_reason);
      verifier.TestAttributeIsDefined(
          outbound_stream.quality_limitation_durations);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          outbound_stream.quality_limitation_resolution_changes);
      // The integration test is not set up to test screen share; don't require
      // this to be present.
      verifier.MarkAttributeTested(outbound_stream.content_type, true);
      verifier.TestAttributeIsDefined(outbound_stream.encoder_implementation);
      verifier.TestAttributeIsDefined(outbound_stream.power_efficient_encoder);
      // Unless an implementation-specific amount of time has passed and at
      // least one frame has been encoded, undefined is reported. Because it
      // is hard to tell what is the case here, we treat FPS as optional.
      // TODO(hbos): Update the tests to run until all implemented metrics
      // should be populated.
      if (outbound_stream.frames_per_second.has_value()) {
        verifier.TestAttributeIsNonNegative<double>(
            outbound_stream.frames_per_second);
      } else {
        verifier.TestAttributeIsUndefined(outbound_stream.frames_per_second);
      }
      verifier.TestAttributeIsNonNegative<uint32_t>(
          outbound_stream.frame_height);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          outbound_stream.frame_width);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          outbound_stream.frames_sent);
      verifier.TestAttributeIsNonNegative<uint32_t>(
          outbound_stream.huge_frames_sent);
      verifier.MarkAttributeTested(outbound_stream.rid, true);
      verifier.TestAttributeIsDefined(outbound_stream.scalability_mode);
      verifier.TestAttributeIsNonNegative<uint32_t>(outbound_stream.rtx_ssrc);
    } else {
      verifier.TestAttributeIsUndefined(outbound_stream.frames_encoded);
      verifier.TestAttributeIsUndefined(outbound_stream.key_frames_encoded);
      verifier.TestAttributeIsUndefined(outbound_stream.total_encode_time);
      verifier.TestAttributeIsUndefined(
          outbound_stream.total_encoded_bytes_target);
      verifier.TestAttributeIsUndefined(
          outbound_stream.quality_limitation_reason);
      verifier.TestAttributeIsUndefined(
          outbound_stream.quality_limitation_durations);
      verifier.TestAttributeIsUndefined(
          outbound_stream.quality_limitation_resolution_changes);
      verifier.TestAttributeIsUndefined(outbound_stream.content_type);
      // TODO(hbos): Implement for audio as well.
      verifier.TestAttributeIsUndefined(outbound_stream.encoder_implementation);
      verifier.TestAttributeIsUndefined(
          outbound_stream.power_efficient_encoder);
      verifier.TestAttributeIsUndefined(outbound_stream.rid);
      verifier.TestAttributeIsUndefined(outbound_stream.frames_per_second);
      verifier.TestAttributeIsUndefined(outbound_stream.frame_height);
      verifier.TestAttributeIsUndefined(outbound_stream.frame_width);
      verifier.TestAttributeIsUndefined(outbound_stream.frames_sent);
      verifier.TestAttributeIsUndefined(outbound_stream.huge_frames_sent);
      verifier.TestAttributeIsUndefined(outbound_stream.scalability_mode);
      verifier.TestAttributeIsUndefined(outbound_stream.rtx_ssrc);
    }
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  void VerifyRTCReceivedRtpStreamStats(
      const RTCReceivedRtpStreamStats& received_rtp,
      RTCStatsVerifier& verifier) {
    VerifyRTCRtpStreamStats(received_rtp, verifier);
    verifier.TestAttributeIsNonNegative<double>(received_rtp.jitter);
    verifier.TestAttributeIsDefined(received_rtp.packets_lost);
  }

  bool VerifyRTCRemoteInboundRtpStreamStats(
      const RTCRemoteInboundRtpStreamStats& remote_inbound_stream) {
    RTCStatsVerifier verifier(report_.get(), &remote_inbound_stream);
    VerifyRTCReceivedRtpStreamStats(remote_inbound_stream, verifier);
    verifier.TestAttributeIsDefined(remote_inbound_stream.fraction_lost);
    verifier.TestAttributeIsIDReference(remote_inbound_stream.local_id,
                                        RTCOutboundRtpStreamStats::kType);
    verifier.TestAttributeIsNonNegative<double>(
        remote_inbound_stream.round_trip_time);
    verifier.TestAttributeIsNonNegative<double>(
        remote_inbound_stream.total_round_trip_time);
    verifier.TestAttributeIsNonNegative<int32_t>(
        remote_inbound_stream.round_trip_time_measurements);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCRemoteOutboundRtpStreamStats(
      const RTCRemoteOutboundRtpStreamStats& remote_outbound_stream) {
    RTCStatsVerifier verifier(report_.get(), &remote_outbound_stream);
    VerifyRTCRtpStreamStats(remote_outbound_stream, verifier);
    VerifyRTCSentRtpStreamStats(remote_outbound_stream, verifier);
    verifier.TestAttributeIsIDReference(remote_outbound_stream.local_id,
                                        RTCOutboundRtpStreamStats::kType);
    verifier.TestAttributeIsNonNegative<double>(
        remote_outbound_stream.remote_timestamp);
    verifier.TestAttributeIsDefined(remote_outbound_stream.reports_sent);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  void VerifyRTCMediaSourceStats(const RTCMediaSourceStats& media_source,
                                 RTCStatsVerifier* verifier) {
    verifier->TestAttributeIsDefined(media_source.track_identifier);
    verifier->TestAttributeIsDefined(media_source.kind);
    if (media_source.kind.has_value()) {
      EXPECT_TRUE((*media_source.kind == "audio" &&
                   media_source.type() == RTCAudioSourceStats::kType) ||
                  (*media_source.kind == "video" &&
                   media_source.type() == RTCVideoSourceStats::kType));
    }
  }

  bool VerifyRTCAudioSourceStats(const RTCAudioSourceStats& audio_source) {
    RTCStatsVerifier verifier(report_.get(), &audio_source);
    VerifyRTCMediaSourceStats(audio_source, &verifier);
    // Audio level, unlike audio energy, only gets updated at a certain
    // frequency, so we don't require that one to be positive to avoid a race
    // (https://crbug.com/webrtc/10962).
    verifier.TestAttributeIsNonNegative<double>(audio_source.audio_level);
    verifier.TestAttributeIsPositive<double>(audio_source.total_audio_energy);
    verifier.TestAttributeIsPositive<double>(
        audio_source.total_samples_duration);
    // TODO(hbos): `echo_return_loss` and `echo_return_loss_enhancement` are
    // flaky on msan bot (sometimes defined, sometimes undefined). Should the
    // test run until available or is there a way to have it always be
    // defined? crbug.com/627816
    verifier.MarkAttributeTested(audio_source.echo_return_loss, true);
    verifier.MarkAttributeTested(audio_source.echo_return_loss_enhancement,
                                 true);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCVideoSourceStats(const RTCVideoSourceStats& video_source) {
    RTCStatsVerifier verifier(report_.get(), &video_source);
    VerifyRTCMediaSourceStats(video_source, &verifier);
    // TODO(hbos): This integration test uses fakes that doesn't support
    // VideoTrackSourceInterface::Stats. When this is fixed we should
    // TestAttributeIsNonNegative<uint32_t>() for `width` and `height` instead
    // to reflect real code.
    verifier.TestAttributeIsUndefined(video_source.width);
    verifier.TestAttributeIsUndefined(video_source.height);
    verifier.TestAttributeIsNonNegative<uint32_t>(video_source.frames);
    verifier.TestAttributeIsNonNegative<double>(video_source.frames_per_second);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCTransportStats(const RTCTransportStats& transport) {
    RTCStatsVerifier verifier(report_.get(), &transport);
    verifier.TestAttributeIsNonNegative<uint64_t>(transport.bytes_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(transport.packets_sent);
    verifier.TestAttributeIsNonNegative<uint64_t>(transport.bytes_received);
    verifier.TestAttributeIsNonNegative<uint64_t>(transport.packets_received);
    verifier.TestAttributeIsOptionalIDReference(
        transport.rtcp_transport_stats_id, RTCTransportStats::kType);
    verifier.TestAttributeIsDefined(transport.dtls_state);
    verifier.TestAttributeIsIDReference(transport.selected_candidate_pair_id,
                                        RTCIceCandidatePairStats::kType);
    verifier.TestAttributeIsIDReference(transport.local_certificate_id,
                                        RTCCertificateStats::kType);
    verifier.TestAttributeIsIDReference(transport.remote_certificate_id,
                                        RTCCertificateStats::kType);
    verifier.TestAttributeIsDefined(transport.tls_version);
    verifier.TestAttributeIsDefined(transport.dtls_cipher);
    verifier.TestAttributeIsDefined(transport.dtls_role);
    verifier.TestAttributeIsDefined(transport.srtp_cipher);
    verifier.TestAttributeIsPositive<uint32_t>(
        transport.selected_candidate_pair_changes);
    verifier.TestAttributeIsDefined(transport.ice_role);
    verifier.TestAttributeIsDefined(transport.ice_local_username_fragment);
    verifier.TestAttributeIsDefined(transport.ice_state);
    return verifier.ExpectAllAttributesSuccessfullyTested();
  }

  bool VerifyRTCAudioPlayoutStats(const RTCAudioPlayoutStats& audio_playout) {
    RTCStatsVerifier verifier(report_.get(), &audio_playout);
    verifier.TestAttributeIsDefined(audio_playout.kind);
    if (audio_playout.kind.has_value()) {
      EXPECT_EQ(*audio_playout.kind, "audio");
    }
    verifier.TestAttributeIsNonNegative<uint64_t>(
        audio_playout.synthesized_samples_events);
    verifier.TestAttributeIsNonNegative<double>(
        audio_playout.synthesized_samples_duration);
    verifier.TestAttributeIsNonNegative<uint64_t>(
        audio_playout.total_samples_count);
    verifier.TestAttributeIsNonNegative<double>(
        audio_playout.total_samples_duration);
    verifier.TestAttributeIsNonNegative<double>(
        audio_playout.total_playout_delay);
    return verifier.ExpectAllAttributesSuccessfullyTested();
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

  rtc::scoped_refptr<const RTCStatsReport> report;
  // Wait for round trip time measurements to be defined.
  constexpr int kMaxWaitMs = 10000;
  auto GetStatsReportAndReturnTrueIfRttIsDefined = [&report, this] {
    report = GetStatsFromCallee();
    auto inbound_stats =
        report->GetStatsOfType<RTCRemoteInboundRtpStreamStats>();
    return !inbound_stats.empty() &&
           inbound_stats.front()->round_trip_time.has_value() &&
           inbound_stats.front()->round_trip_time_measurements.has_value();
  };
  EXPECT_TRUE_WAIT(GetStatsReportAndReturnTrueIfRttIsDefined(), kMaxWaitMs);
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
      RTCInboundRtpStreamStats::kType,
      RTCPeerConnectionStats::kType,
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
      RTCOutboundRtpStreamStats::kType,
      RTCPeerConnectionStats::kType,
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
  caller_->pc()->GetStats(stats_obtainer.get());
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
  caller_->pc()->GetStats(stats_obtainer.get());
  caller_->pc()->Close();

  ASSERT_TRUE(stats_obtainer->report());
#if RTC_TRACE_EVENTS_ENABLED
  EXPECT_EQ(stats_obtainer->report()->ToJson(),
            RTCStatsReportTraceListener::last_trace());
#endif
}

// GetStatsReferencedIds() is optimized to recognize what is or isn't a
// referenced ID based on dictionary type information and knowing what
// attributes are used as references, as opposed to iterating all attributes to
// find the ones with the "Id" or "Ids" suffix. As such, GetStatsReferencedIds()
// is tested as an integration test instead of a unit test in order to guard
// against adding new references and forgetting to update
// GetStatsReferencedIds().
TEST_F(RTCStatsIntegrationTest, GetStatsReferencedIds) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCallee();
  for (const RTCStats& stats : *report) {
    // Find all references by looking at all string attributes with the "Id" or
    // "Ids" suffix.
    std::set<const std::string*> expected_ids;
    for (const auto& attribute : stats.Attributes()) {
      if (!attribute.has_value())
        continue;
      if (attribute.holds_alternative<std::string>()) {
        if (absl::EndsWith(attribute.name(), "Id")) {
          expected_ids.insert(&attribute.get<std::string>());
        }
      } else if (attribute.holds_alternative<std::vector<std::string>>()) {
        if (absl::EndsWith(attribute.name(), "Ids")) {
          for (const std::string& id :
               attribute.get<std::vector<std::string>>())
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

TEST_F(RTCStatsIntegrationTest, GetStatsContainsNoDuplicateAttributes) {
  StartCall();

  rtc::scoped_refptr<const RTCStatsReport> report = GetStatsFromCallee();
  for (const RTCStats& stats : *report) {
    std::set<std::string> attribute_names;
    for (const auto& attribute : stats.Attributes()) {
      EXPECT_TRUE(attribute_names.find(attribute.name()) ==
                  attribute_names.end())
          << attribute.name() << " is a duplicate!";
      attribute_names.insert(attribute.name());
    }
  }
}
#endif  // WEBRTC_HAVE_SCTP

}  // namespace

}  // namespace webrtc
