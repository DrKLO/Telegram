/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/peer_connection.h"

#include <limits.h>
#include <stddef.h>

#include <algorithm>
#include <memory>
#include <set>
#include <string>
#include <type_traits>
#include <utility>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/jsep_ice_candidate.h"
#include "api/rtp_parameters.h"
#include "api/rtp_transceiver_direction.h"
#include "api/transport/webrtc_key_value_config.h"
#include "api/uma_metrics.h"
#include "api/video/video_codec_constants.h"
#include "call/audio_state.h"
#include "call/packet_receiver.h"
#include "media/base/media_channel.h"
#include "media/base/media_config.h"
#include "media/base/media_engine.h"
#include "media/base/rid_description.h"
#include "media/base/stream_params.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "p2p/base/basic_async_resolver_factory.h"
#include "p2p/base/connection.h"
#include "p2p/base/connection_info.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/p2p_constants.h"
#include "p2p/base/p2p_transport_channel.h"
#include "p2p/base/transport_info.h"
#include "pc/channel.h"
#include "pc/ice_server_parsing.h"
#include "pc/rtp_receiver.h"
#include "pc/rtp_receiver_proxy.h"
#include "pc/rtp_sender.h"
#include "pc/rtp_sender_proxy.h"
#include "pc/sctp_transport.h"
#include "pc/simulcast_description.h"
#include "pc/webrtc_session_description_factory.h"
#include "rtc_base/helpers.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/net_helper.h"
#include "rtc_base/network.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/trace_event.h"
#include "rtc_base/unique_id_generator.h"
#include "system_wrappers/include/metrics.h"

using cricket::ContentInfo;
using cricket::ContentInfos;
using cricket::MediaContentDescription;
using cricket::MediaProtocolType;
using cricket::RidDescription;
using cricket::RidDirection;
using cricket::SessionDescription;
using cricket::SimulcastDescription;
using cricket::SimulcastLayer;
using cricket::SimulcastLayerList;
using cricket::StreamParams;
using cricket::TransportInfo;

using cricket::LOCAL_PORT_TYPE;
using cricket::PRFLX_PORT_TYPE;
using cricket::RELAY_PORT_TYPE;
using cricket::STUN_PORT_TYPE;

namespace webrtc {

namespace {

// UMA metric names.
const char kSimulcastNumberOfEncodings[] =
    "WebRTC.PeerConnection.Simulcast.NumberOfSendEncodings";

static const int REPORT_USAGE_PATTERN_DELAY_MS = 60000;

uint32_t ConvertIceTransportTypeToCandidateFilter(
    PeerConnectionInterface::IceTransportsType type) {
  switch (type) {
    case PeerConnectionInterface::kNone:
      return cricket::CF_NONE;
    case PeerConnectionInterface::kRelay:
      return cricket::CF_RELAY;
    case PeerConnectionInterface::kNoHost:
      return (cricket::CF_ALL & ~cricket::CF_HOST);
    case PeerConnectionInterface::kAll:
      return cricket::CF_ALL;
    default:
      RTC_DCHECK_NOTREACHED();
  }
  return cricket::CF_NONE;
}

IceCandidatePairType GetIceCandidatePairCounter(
    const cricket::Candidate& local,
    const cricket::Candidate& remote) {
  const auto& l = local.type();
  const auto& r = remote.type();
  const auto& host = LOCAL_PORT_TYPE;
  const auto& srflx = STUN_PORT_TYPE;
  const auto& relay = RELAY_PORT_TYPE;
  const auto& prflx = PRFLX_PORT_TYPE;
  if (l == host && r == host) {
    bool local_hostname =
        !local.address().hostname().empty() && local.address().IsUnresolvedIP();
    bool remote_hostname = !remote.address().hostname().empty() &&
                           remote.address().IsUnresolvedIP();
    bool local_private = IPIsPrivate(local.address().ipaddr());
    bool remote_private = IPIsPrivate(remote.address().ipaddr());
    if (local_hostname) {
      if (remote_hostname) {
        return kIceCandidatePairHostNameHostName;
      } else if (remote_private) {
        return kIceCandidatePairHostNameHostPrivate;
      } else {
        return kIceCandidatePairHostNameHostPublic;
      }
    } else if (local_private) {
      if (remote_hostname) {
        return kIceCandidatePairHostPrivateHostName;
      } else if (remote_private) {
        return kIceCandidatePairHostPrivateHostPrivate;
      } else {
        return kIceCandidatePairHostPrivateHostPublic;
      }
    } else {
      if (remote_hostname) {
        return kIceCandidatePairHostPublicHostName;
      } else if (remote_private) {
        return kIceCandidatePairHostPublicHostPrivate;
      } else {
        return kIceCandidatePairHostPublicHostPublic;
      }
    }
  }
  if (l == host && r == srflx)
    return kIceCandidatePairHostSrflx;
  if (l == host && r == relay)
    return kIceCandidatePairHostRelay;
  if (l == host && r == prflx)
    return kIceCandidatePairHostPrflx;
  if (l == srflx && r == host)
    return kIceCandidatePairSrflxHost;
  if (l == srflx && r == srflx)
    return kIceCandidatePairSrflxSrflx;
  if (l == srflx && r == relay)
    return kIceCandidatePairSrflxRelay;
  if (l == srflx && r == prflx)
    return kIceCandidatePairSrflxPrflx;
  if (l == relay && r == host)
    return kIceCandidatePairRelayHost;
  if (l == relay && r == srflx)
    return kIceCandidatePairRelaySrflx;
  if (l == relay && r == relay)
    return kIceCandidatePairRelayRelay;
  if (l == relay && r == prflx)
    return kIceCandidatePairRelayPrflx;
  if (l == prflx && r == host)
    return kIceCandidatePairPrflxHost;
  if (l == prflx && r == srflx)
    return kIceCandidatePairPrflxSrflx;
  if (l == prflx && r == relay)
    return kIceCandidatePairPrflxRelay;
  return kIceCandidatePairMax;
}

absl::optional<int> RTCConfigurationToIceConfigOptionalInt(
    int rtc_configuration_parameter) {
  if (rtc_configuration_parameter ==
      webrtc::PeerConnectionInterface::RTCConfiguration::kUndefined) {
    return absl::nullopt;
  }
  return rtc_configuration_parameter;
}

// Check if the changes of IceTransportsType motives an ice restart.
bool NeedIceRestart(bool surface_ice_candidates_on_ice_transport_type_changed,
                    PeerConnectionInterface::IceTransportsType current,
                    PeerConnectionInterface::IceTransportsType modified) {
  if (current == modified) {
    return false;
  }

  if (!surface_ice_candidates_on_ice_transport_type_changed) {
    return true;
  }

  auto current_filter = ConvertIceTransportTypeToCandidateFilter(current);
  auto modified_filter = ConvertIceTransportTypeToCandidateFilter(modified);

  // If surface_ice_candidates_on_ice_transport_type_changed is true and we
  // extend the filter, then no ice restart is needed.
  return (current_filter & modified_filter) != current_filter;
}

cricket::IceConfig ParseIceConfig(
    const PeerConnectionInterface::RTCConfiguration& config) {
  cricket::ContinualGatheringPolicy gathering_policy;
  switch (config.continual_gathering_policy) {
    case PeerConnectionInterface::GATHER_ONCE:
      gathering_policy = cricket::GATHER_ONCE;
      break;
    case PeerConnectionInterface::GATHER_CONTINUALLY:
      gathering_policy = cricket::GATHER_CONTINUALLY;
      break;
    default:
      RTC_DCHECK_NOTREACHED();
      gathering_policy = cricket::GATHER_ONCE;
  }

  cricket::IceConfig ice_config;
  ice_config.receiving_timeout = RTCConfigurationToIceConfigOptionalInt(
      config.ice_connection_receiving_timeout);
  ice_config.prioritize_most_likely_candidate_pairs =
      config.prioritize_most_likely_ice_candidate_pairs;
  ice_config.backup_connection_ping_interval =
      RTCConfigurationToIceConfigOptionalInt(
          config.ice_backup_candidate_pair_ping_interval);
  ice_config.continual_gathering_policy = gathering_policy;
  ice_config.presume_writable_when_fully_relayed =
      config.presume_writable_when_fully_relayed;
  ice_config.surface_ice_candidates_on_ice_transport_type_changed =
      config.surface_ice_candidates_on_ice_transport_type_changed;
  ice_config.ice_check_interval_strong_connectivity =
      config.ice_check_interval_strong_connectivity;
  ice_config.ice_check_interval_weak_connectivity =
      config.ice_check_interval_weak_connectivity;
  ice_config.ice_check_min_interval = config.ice_check_min_interval;
  ice_config.ice_unwritable_timeout = config.ice_unwritable_timeout;
  ice_config.ice_unwritable_min_checks = config.ice_unwritable_min_checks;
  ice_config.ice_inactive_timeout = config.ice_inactive_timeout;
  ice_config.stun_keepalive_interval = config.stun_candidate_keepalive_interval;
  ice_config.network_preference = config.network_preference;
  ice_config.stable_writable_connection_ping_interval =
      config.stable_writable_connection_ping_interval_ms;
  return ice_config;
}

// Ensures the configuration doesn't have any parameters with invalid values,
// or values that conflict with other parameters.
//
// Returns RTCError::OK() if there are no issues.
RTCError ValidateConfiguration(
    const PeerConnectionInterface::RTCConfiguration& config) {
  return cricket::P2PTransportChannel::ValidateIceConfig(
      ParseIceConfig(config));
}

bool HasRtcpMuxEnabled(const cricket::ContentInfo* content) {
  return content->media_description()->rtcp_mux();
}

bool DtlsEnabled(const PeerConnectionInterface::RTCConfiguration& configuration,
                 const PeerConnectionFactoryInterface::Options& options,
                 const PeerConnectionDependencies& dependencies) {
  if (options.disable_encryption)
    return false;

  // Enable DTLS by default if we have an identity store or a certificate.
  bool default_enabled =
      (dependencies.cert_generator || !configuration.certificates.empty());

  // The `configuration` can override the default value.
  return configuration.enable_dtls_srtp.value_or(default_enabled);
}

}  // namespace

bool PeerConnectionInterface::RTCConfiguration::operator==(
    const PeerConnectionInterface::RTCConfiguration& o) const {
  // This static_assert prevents us from accidentally breaking operator==.
  // Note: Order matters! Fields must be ordered the same as RTCConfiguration.
  struct stuff_being_tested_for_equality {
    IceServers servers;
    IceTransportsType type;
    BundlePolicy bundle_policy;
    RtcpMuxPolicy rtcp_mux_policy;
    std::vector<rtc::scoped_refptr<rtc::RTCCertificate>> certificates;
    int ice_candidate_pool_size;
    bool disable_ipv6;
    bool disable_ipv6_on_wifi;
    int max_ipv6_networks;
    bool disable_link_local_networks;
    absl::optional<int> screencast_min_bitrate;
    absl::optional<bool> combined_audio_video_bwe;
    absl::optional<bool> enable_dtls_srtp;
    TcpCandidatePolicy tcp_candidate_policy;
    CandidateNetworkPolicy candidate_network_policy;
    int audio_jitter_buffer_max_packets;
    bool audio_jitter_buffer_fast_accelerate;
    int audio_jitter_buffer_min_delay_ms;
    bool audio_jitter_buffer_enable_rtx_handling;
    int ice_connection_receiving_timeout;
    int ice_backup_candidate_pair_ping_interval;
    ContinualGatheringPolicy continual_gathering_policy;
    bool prioritize_most_likely_ice_candidate_pairs;
    struct cricket::MediaConfig media_config;
    bool prune_turn_ports;
    PortPrunePolicy turn_port_prune_policy;
    bool presume_writable_when_fully_relayed;
    bool enable_ice_renomination;
    bool redetermine_role_on_ice_restart;
    bool surface_ice_candidates_on_ice_transport_type_changed;
    absl::optional<int> ice_check_interval_strong_connectivity;
    absl::optional<int> ice_check_interval_weak_connectivity;
    absl::optional<int> ice_check_min_interval;
    absl::optional<int> ice_unwritable_timeout;
    absl::optional<int> ice_unwritable_min_checks;
    absl::optional<int> ice_inactive_timeout;
    absl::optional<int> stun_candidate_keepalive_interval;
    webrtc::TurnCustomizer* turn_customizer;
    SdpSemantics sdp_semantics;
    absl::optional<rtc::AdapterType> network_preference;
    bool active_reset_srtp_params;
    absl::optional<CryptoOptions> crypto_options;
    bool offer_extmap_allow_mixed;
    std::string turn_logging_id;
    bool enable_implicit_rollback;
    absl::optional<bool> allow_codec_switching;
    absl::optional<int> report_usage_pattern_delay_ms;
    absl::optional<int> stable_writable_connection_ping_interval_ms;
    webrtc::VpnPreference vpn_preference;
    std::vector<rtc::NetworkMask> vpn_list;
    PortAllocatorConfig port_allocator_config;
  };
  static_assert(sizeof(stuff_being_tested_for_equality) == sizeof(*this),
                "Did you add something to RTCConfiguration and forget to "
                "update operator==?");
  return type == o.type && servers == o.servers &&
         bundle_policy == o.bundle_policy &&
         rtcp_mux_policy == o.rtcp_mux_policy &&
         tcp_candidate_policy == o.tcp_candidate_policy &&
         candidate_network_policy == o.candidate_network_policy &&
         audio_jitter_buffer_max_packets == o.audio_jitter_buffer_max_packets &&
         audio_jitter_buffer_fast_accelerate ==
             o.audio_jitter_buffer_fast_accelerate &&
         audio_jitter_buffer_min_delay_ms ==
             o.audio_jitter_buffer_min_delay_ms &&
         audio_jitter_buffer_enable_rtx_handling ==
             o.audio_jitter_buffer_enable_rtx_handling &&
         ice_connection_receiving_timeout ==
             o.ice_connection_receiving_timeout &&
         ice_backup_candidate_pair_ping_interval ==
             o.ice_backup_candidate_pair_ping_interval &&
         continual_gathering_policy == o.continual_gathering_policy &&
         certificates == o.certificates &&
         prioritize_most_likely_ice_candidate_pairs ==
             o.prioritize_most_likely_ice_candidate_pairs &&
         media_config == o.media_config && disable_ipv6 == o.disable_ipv6 &&
         disable_ipv6_on_wifi == o.disable_ipv6_on_wifi &&
         max_ipv6_networks == o.max_ipv6_networks &&
         disable_link_local_networks == o.disable_link_local_networks &&
         screencast_min_bitrate == o.screencast_min_bitrate &&
         combined_audio_video_bwe == o.combined_audio_video_bwe &&
         enable_dtls_srtp == o.enable_dtls_srtp &&
         ice_candidate_pool_size == o.ice_candidate_pool_size &&
         prune_turn_ports == o.prune_turn_ports &&
         turn_port_prune_policy == o.turn_port_prune_policy &&
         presume_writable_when_fully_relayed ==
             o.presume_writable_when_fully_relayed &&
         enable_ice_renomination == o.enable_ice_renomination &&
         redetermine_role_on_ice_restart == o.redetermine_role_on_ice_restart &&
         surface_ice_candidates_on_ice_transport_type_changed ==
             o.surface_ice_candidates_on_ice_transport_type_changed &&
         ice_check_interval_strong_connectivity ==
             o.ice_check_interval_strong_connectivity &&
         ice_check_interval_weak_connectivity ==
             o.ice_check_interval_weak_connectivity &&
         ice_check_min_interval == o.ice_check_min_interval &&
         ice_unwritable_timeout == o.ice_unwritable_timeout &&
         ice_unwritable_min_checks == o.ice_unwritable_min_checks &&
         ice_inactive_timeout == o.ice_inactive_timeout &&
         stun_candidate_keepalive_interval ==
             o.stun_candidate_keepalive_interval &&
         turn_customizer == o.turn_customizer &&
         sdp_semantics == o.sdp_semantics &&
         network_preference == o.network_preference &&
         active_reset_srtp_params == o.active_reset_srtp_params &&
         crypto_options == o.crypto_options &&
         offer_extmap_allow_mixed == o.offer_extmap_allow_mixed &&
         turn_logging_id == o.turn_logging_id &&
         enable_implicit_rollback == o.enable_implicit_rollback &&
         allow_codec_switching == o.allow_codec_switching &&
         report_usage_pattern_delay_ms == o.report_usage_pattern_delay_ms &&
         stable_writable_connection_ping_interval_ms ==
             o.stable_writable_connection_ping_interval_ms &&
         vpn_preference == o.vpn_preference && vpn_list == o.vpn_list &&
         port_allocator_config.min_port == o.port_allocator_config.min_port &&
         port_allocator_config.max_port == o.port_allocator_config.max_port &&
         port_allocator_config.flags == o.port_allocator_config.flags;
}

bool PeerConnectionInterface::RTCConfiguration::operator!=(
    const PeerConnectionInterface::RTCConfiguration& o) const {
  return !(*this == o);
}

RTCErrorOr<rtc::scoped_refptr<PeerConnection>> PeerConnection::Create(
    rtc::scoped_refptr<ConnectionContext> context,
    const PeerConnectionFactoryInterface::Options& options,
    std::unique_ptr<RtcEventLog> event_log,
    std::unique_ptr<Call> call,
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies dependencies) {
  // TODO(https://crbug.com/webrtc/13528): Remove support for kPlanB.
  if (configuration.sdp_semantics == SdpSemantics::kPlanB_DEPRECATED) {
    RTC_LOG(LS_WARNING)
        << "PeerConnection constructed with legacy SDP semantics!";
  }

  RTCError config_error = cricket::P2PTransportChannel::ValidateIceConfig(
      ParseIceConfig(configuration));
  if (!config_error.ok()) {
    RTC_LOG(LS_ERROR) << "Invalid ICE configuration: "
                      << config_error.message();
    return config_error;
  }

  if (!dependencies.allocator) {
    RTC_LOG(LS_ERROR)
        << "PeerConnection initialized without a PortAllocator? "
           "This shouldn't happen if using PeerConnectionFactory.";
    return RTCError(
        RTCErrorType::INVALID_PARAMETER,
        "Attempt to create a PeerConnection without a PortAllocatorFactory");
  }

  if (!dependencies.observer) {
    // TODO(deadbeef): Why do we do this?
    RTC_LOG(LS_ERROR) << "PeerConnection initialized without a "
                         "PeerConnectionObserver";
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "Attempt to create a PeerConnection without an observer");
  }

  bool is_unified_plan =
      configuration.sdp_semantics == SdpSemantics::kUnifiedPlan;
  bool dtls_enabled = DtlsEnabled(configuration, options, dependencies);

  // Interim code: If an AsyncResolverFactory is given, but not an
  // AsyncDnsResolverFactory, wrap it in a WrappingAsyncDnsResolverFactory
  // If neither is given, create a WrappingAsyncDnsResolverFactory wrapping
  // a BasicAsyncResolver.
  // TODO(bugs.webrtc.org/12598): Remove code once all callers pass a
  // AsyncDnsResolverFactory.
  if (dependencies.async_dns_resolver_factory &&
      dependencies.async_resolver_factory) {
    RTC_LOG(LS_ERROR)
        << "Attempt to set both old and new type of DNS resolver factory";
    return RTCError(RTCErrorType::INVALID_PARAMETER,
                    "Both old and new type of DNS resolver given");
  }
  if (dependencies.async_resolver_factory) {
    dependencies.async_dns_resolver_factory =
        std::make_unique<WrappingAsyncDnsResolverFactory>(
            std::move(dependencies.async_resolver_factory));
  } else {
    dependencies.async_dns_resolver_factory =
        std::make_unique<WrappingAsyncDnsResolverFactory>(
            std::make_unique<BasicAsyncResolverFactory>());
  }

  // The PeerConnection constructor consumes some, but not all, dependencies.
  auto pc = rtc::make_ref_counted<PeerConnection>(
      context, options, is_unified_plan, std::move(event_log), std::move(call),
      dependencies, dtls_enabled);
  RTCError init_error = pc->Initialize(configuration, std::move(dependencies));
  if (!init_error.ok()) {
    RTC_LOG(LS_ERROR) << "PeerConnection initialization failed";
    return init_error;
  }
  return pc;
}

PeerConnection::PeerConnection(
    rtc::scoped_refptr<ConnectionContext> context,
    const PeerConnectionFactoryInterface::Options& options,
    bool is_unified_plan,
    std::unique_ptr<RtcEventLog> event_log,
    std::unique_ptr<Call> call,
    PeerConnectionDependencies& dependencies,
    bool dtls_enabled)
    : context_(context),
      options_(options),
      observer_(dependencies.observer),
      is_unified_plan_(is_unified_plan),
      event_log_(std::move(event_log)),
      event_log_ptr_(event_log_.get()),
      async_dns_resolver_factory_(
          std::move(dependencies.async_dns_resolver_factory)),
      port_allocator_(std::move(dependencies.allocator)),
      ice_transport_factory_(std::move(dependencies.ice_transport_factory)),
      tls_cert_verifier_(std::move(dependencies.tls_cert_verifier)),
      call_(std::move(call)),
      call_ptr_(call_.get()),
      // RFC 3264: The numeric value of the session id and version in the
      // o line MUST be representable with a "64 bit signed integer".
      // Due to this constraint session id `session_id_` is max limited to
      // LLONG_MAX.
      session_id_(rtc::ToString(rtc::CreateRandomId64() & LLONG_MAX)),
      dtls_enabled_(dtls_enabled),
      data_channel_controller_(this),
      message_handler_(signaling_thread()),
      weak_factory_(this) {
  worker_thread()->Invoke<void>(RTC_FROM_HERE, [this] {
    RTC_DCHECK_RUN_ON(worker_thread());
    worker_thread_safety_ = PendingTaskSafetyFlag::Create();
    if (!call_)
      worker_thread_safety_->SetNotAlive();
  });
}

PeerConnection::~PeerConnection() {
  TRACE_EVENT0("webrtc", "PeerConnection::~PeerConnection");
  RTC_DCHECK_RUN_ON(signaling_thread());

  if (sdp_handler_) {
    sdp_handler_->PrepareForShutdown();
  }

  // Need to stop transceivers before destroying the stats collector because
  // AudioRtpSender has a reference to the StatsCollector it will update when
  // stopping.
  if (rtp_manager()) {
    for (const auto& transceiver : rtp_manager()->transceivers()->List()) {
      transceiver->StopInternal();
    }
  }

  stats_.reset(nullptr);
  if (stats_collector_) {
    stats_collector_->WaitForPendingRequest();
    stats_collector_ = nullptr;
  }

  if (sdp_handler_) {
    // Don't destroy BaseChannels until after stats has been cleaned up so that
    // the last stats request can still read from the channels.
    sdp_handler_->DestroyAllChannels();

    RTC_LOG(LS_INFO) << "Session: " << session_id() << " is destroyed.";

    sdp_handler_->ResetSessionDescFactory();
  }

  // port_allocator_ and transport_controller_ live on the network thread and
  // should be destroyed there.
  transport_controller_copy_ = nullptr;
  network_thread()->Invoke<void>(RTC_FROM_HERE, [this] {
    RTC_DCHECK_RUN_ON(network_thread());
    TeardownDataChannelTransport_n();
    transport_controller_.reset();
    port_allocator_.reset();
    if (network_thread_safety_)
      network_thread_safety_->SetNotAlive();
  });

  // call_ and event_log_ must be destroyed on the worker thread.
  worker_thread()->Invoke<void>(RTC_FROM_HERE, [this] {
    RTC_DCHECK_RUN_ON(worker_thread());
    worker_thread_safety_->SetNotAlive();
    call_.reset();
    // The event log must outlive call (and any other object that uses it).
    event_log_.reset();
  });
}

RTCError PeerConnection::Initialize(
    const PeerConnectionInterface::RTCConfiguration& configuration,
    PeerConnectionDependencies dependencies) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "PeerConnection::Initialize");

  cricket::ServerAddresses stun_servers;
  std::vector<cricket::RelayServerConfig> turn_servers;

  RTCErrorType parse_error =
      ParseIceServers(configuration.servers, &stun_servers, &turn_servers);
  if (parse_error != RTCErrorType::NONE) {
    return RTCError(parse_error, "ICE server parse failed");
  }

  // Add the turn logging id to all turn servers
  for (cricket::RelayServerConfig& turn_server : turn_servers) {
    turn_server.turn_logging_id = configuration.turn_logging_id;
  }

  // Note if STUN or TURN servers were supplied.
  if (!stun_servers.empty()) {
    NoteUsageEvent(UsageEvent::STUN_SERVER_ADDED);
  }
  if (!turn_servers.empty()) {
    NoteUsageEvent(UsageEvent::TURN_SERVER_ADDED);
  }

  // Network thread initialization.
  transport_controller_copy_ =
      network_thread()->Invoke<JsepTransportController*>(RTC_FROM_HERE, [&] {
        RTC_DCHECK_RUN_ON(network_thread());
        network_thread_safety_ = PendingTaskSafetyFlag::Create();
        InitializePortAllocatorResult pa_result = InitializePortAllocator_n(
            stun_servers, turn_servers, configuration);
        // Send information about IPv4/IPv6 status.
        PeerConnectionAddressFamilyCounter address_family =
            pa_result.enable_ipv6 ? kPeerConnection_IPv6 : kPeerConnection_IPv4;
        RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.IPMetrics",
                                  address_family,
                                  kPeerConnectionAddressFamilyCounter_Max);
        return InitializeTransportController_n(configuration, dependencies);
      });

  configuration_ = configuration;

  stats_ = std::make_unique<StatsCollector>(this);
  stats_collector_ = RTCStatsCollector::Create(this);

  sdp_handler_ = SdpOfferAnswerHandler::Create(this, configuration,
                                               dependencies, context_);

  rtp_manager_ = std::make_unique<RtpTransmissionManager>(
      IsUnifiedPlan(), signaling_thread(), worker_thread(), channel_manager(),
      &usage_pattern_, observer_, stats_.get(), [this]() {
        RTC_DCHECK_RUN_ON(signaling_thread());
        sdp_handler_->UpdateNegotiationNeeded();
      });

  // Add default audio/video transceivers for Plan B SDP.
  if (!IsUnifiedPlan()) {
    rtp_manager()->transceivers()->Add(
        RtpTransceiverProxyWithInternal<RtpTransceiver>::Create(
            signaling_thread(),
            new RtpTransceiver(cricket::MEDIA_TYPE_AUDIO, channel_manager())));
    rtp_manager()->transceivers()->Add(
        RtpTransceiverProxyWithInternal<RtpTransceiver>::Create(
            signaling_thread(),
            new RtpTransceiver(cricket::MEDIA_TYPE_VIDEO, channel_manager())));
  }

  int delay_ms = configuration.report_usage_pattern_delay_ms
                     ? *configuration.report_usage_pattern_delay_ms
                     : REPORT_USAGE_PATTERN_DELAY_MS;
  message_handler_.RequestUsagePatternReport(
      [this]() {
        RTC_DCHECK_RUN_ON(signaling_thread());
        ReportUsagePattern();
      },
      delay_ms);

  // Record the number of configured ICE servers for all connections.
  RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.PeerConnection.IceServers.Configured",
                              configuration_.servers.size(), 0, 31, 32);

  return RTCError::OK();
}

JsepTransportController* PeerConnection::InitializeTransportController_n(
    const RTCConfiguration& configuration,
    const PeerConnectionDependencies& dependencies) {
  JsepTransportController::Config config;
  config.redetermine_role_on_ice_restart =
      configuration.redetermine_role_on_ice_restart;
  config.ssl_max_version = options_.ssl_max_version;
  config.disable_encryption = options_.disable_encryption;
  config.bundle_policy = configuration.bundle_policy;
  config.rtcp_mux_policy = configuration.rtcp_mux_policy;
  // TODO(bugs.webrtc.org/9891) - Remove options_.crypto_options then remove
  // this stub.
  config.crypto_options = configuration.crypto_options.has_value()
                              ? *configuration.crypto_options
                              : options_.crypto_options;
  config.transport_observer = this;
  config.rtcp_handler = InitializeRtcpCallback();
  config.event_log = event_log_ptr_;
#if defined(ENABLE_EXTERNAL_AUTH)
  config.enable_external_auth = true;
#endif
  config.active_reset_srtp_params = configuration.active_reset_srtp_params;

  // DTLS has to be enabled to use SCTP.
  if (dtls_enabled_) {
    config.sctp_factory = context_->sctp_transport_factory();
  }

  config.ice_transport_factory = ice_transport_factory_.get();
  config.on_dtls_handshake_error_ =
      [weak_ptr = weak_factory_.GetWeakPtr()](rtc::SSLHandshakeError s) {
        if (weak_ptr) {
          weak_ptr->OnTransportControllerDtlsHandshakeError(s);
        }
      };

  transport_controller_.reset(
      new JsepTransportController(network_thread(), port_allocator_.get(),
                                  async_dns_resolver_factory_.get(), config));

  transport_controller_->SubscribeIceConnectionState(
      [this](cricket::IceConnectionState s) {
        RTC_DCHECK_RUN_ON(network_thread());
        if (s == cricket::kIceConnectionConnected) {
          ReportTransportStats();
        }
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(), [this, s]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              OnTransportControllerConnectionState(s);
            }));
      });
  transport_controller_->SubscribeConnectionState(
      [this](PeerConnectionInterface::PeerConnectionState s) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(), [this, s]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              SetConnectionState(s);
            }));
      });
  transport_controller_->SubscribeStandardizedIceConnectionState(
      [this](PeerConnectionInterface::IceConnectionState s) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(), [this, s]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              SetStandardizedIceConnectionState(s);
            }));
      });
  transport_controller_->SubscribeIceGatheringState(
      [this](cricket::IceGatheringState s) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(), [this, s]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              OnTransportControllerGatheringState(s);
            }));
      });
  transport_controller_->SubscribeIceCandidateGathered(
      [this](const std::string& transport,
             const std::vector<cricket::Candidate>& candidates) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(),
                         [this, t = transport, c = candidates]() {
                           RTC_DCHECK_RUN_ON(signaling_thread());
                           OnTransportControllerCandidatesGathered(t, c);
                         }));
      });
  transport_controller_->SubscribeIceCandidateError(
      [this](const cricket::IceCandidateErrorEvent& event) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(ToQueuedTask(
            signaling_thread_safety_.flag(), [this, event = event]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              OnTransportControllerCandidateError(event);
            }));
      });
  transport_controller_->SubscribeIceCandidatesRemoved(
      [this](const std::vector<cricket::Candidate>& c) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(
            ToQueuedTask(signaling_thread_safety_.flag(), [this, c = c]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              OnTransportControllerCandidatesRemoved(c);
            }));
      });
  transport_controller_->SubscribeIceCandidatePairChanged(
      [this](const cricket::CandidatePairChangeEvent& event) {
        RTC_DCHECK_RUN_ON(network_thread());
        signaling_thread()->PostTask(ToQueuedTask(
            signaling_thread_safety_.flag(), [this, event = event]() {
              RTC_DCHECK_RUN_ON(signaling_thread());
              OnTransportControllerCandidateChanged(event);
            }));
      });

  transport_controller_->SetIceConfig(ParseIceConfig(configuration));
  return transport_controller_.get();
}

rtc::scoped_refptr<StreamCollectionInterface> PeerConnection::local_streams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "local_streams is not available with Unified "
                                 "Plan SdpSemantics. Please use GetSenders "
                                 "instead.";
  return sdp_handler_->local_streams();
}

rtc::scoped_refptr<StreamCollectionInterface> PeerConnection::remote_streams() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "remote_streams is not available with Unified "
                                 "Plan SdpSemantics. Please use GetReceivers "
                                 "instead.";
  return sdp_handler_->remote_streams();
}

bool PeerConnection::AddStream(MediaStreamInterface* local_stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "AddStream is not available with Unified Plan "
                                 "SdpSemantics. Please use AddTrack instead.";
  TRACE_EVENT0("webrtc", "PeerConnection::AddStream");
  return sdp_handler_->AddStream(local_stream);
}

void PeerConnection::RemoveStream(MediaStreamInterface* local_stream) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "RemoveStream is not available with Unified "
                                 "Plan SdpSemantics. Please use RemoveTrack "
                                 "instead.";
  TRACE_EVENT0("webrtc", "PeerConnection::RemoveStream");
  sdp_handler_->RemoveStream(local_stream);
}

RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>> PeerConnection::AddTrack(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const std::vector<std::string>& stream_ids) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "PeerConnection::AddTrack");
  if (!track) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, "Track is null.");
  }
  if (!(track->kind() == MediaStreamTrackInterface::kAudioKind ||
        track->kind() == MediaStreamTrackInterface::kVideoKind)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "Track has invalid kind: " + track->kind());
  }
  if (IsClosed()) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "PeerConnection is closed.");
  }
  if (rtp_manager()->FindSenderForTrack(track)) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_PARAMETER,
        "Sender already exists for track " + track->id() + ".");
  }
  auto sender_or_error = rtp_manager()->AddTrack(track, stream_ids);
  if (sender_or_error.ok()) {
    sdp_handler_->UpdateNegotiationNeeded();
    stats_->AddTrack(track);
  }
  return sender_or_error;
}

RTCError PeerConnection::RemoveTrackOrError(
    rtc::scoped_refptr<RtpSenderInterface> sender) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!sender) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, "Sender is null.");
  }
  if (IsClosed()) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "PeerConnection is closed.");
  }
  if (IsUnifiedPlan()) {
    auto transceiver = FindTransceiverBySender(sender);
    if (!transceiver || !sender->track()) {
      return RTCError::OK();
    }
    sender->SetTrack(nullptr);
    if (transceiver->direction() == RtpTransceiverDirection::kSendRecv) {
      transceiver->internal()->set_direction(
          RtpTransceiverDirection::kRecvOnly);
    } else if (transceiver->direction() == RtpTransceiverDirection::kSendOnly) {
      transceiver->internal()->set_direction(
          RtpTransceiverDirection::kInactive);
    }
  } else {
    bool removed;
    if (sender->media_type() == cricket::MEDIA_TYPE_AUDIO) {
      removed = rtp_manager()->GetAudioTransceiver()->internal()->RemoveSender(
          sender);
    } else {
      RTC_DCHECK_EQ(cricket::MEDIA_TYPE_VIDEO, sender->media_type());
      removed = rtp_manager()->GetVideoTransceiver()->internal()->RemoveSender(
          sender);
    }
    if (!removed) {
      LOG_AND_RETURN_ERROR(
          RTCErrorType::INVALID_PARAMETER,
          "Couldn't find sender " + sender->id() + " to remove.");
    }
  }
  sdp_handler_->UpdateNegotiationNeeded();
  return RTCError::OK();
}

rtc::scoped_refptr<RtpTransceiverProxyWithInternal<RtpTransceiver>>
PeerConnection::FindTransceiverBySender(
    rtc::scoped_refptr<RtpSenderInterface> sender) {
  return rtp_manager()->transceivers()->FindBySender(sender);
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::AddTransceiver(
    rtc::scoped_refptr<MediaStreamTrackInterface> track) {
  return AddTransceiver(track, RtpTransceiverInit());
}

RtpTransportInternal* PeerConnection::GetRtpTransport(const std::string& mid) {
  // TODO(bugs.webrtc.org/9987): Avoid the thread jump.
  // This might be done by caching the value on the signaling thread.
  RTC_DCHECK_RUN_ON(signaling_thread());
  return network_thread()->Invoke<RtpTransportInternal*>(
      RTC_FROM_HERE, [this, &mid] {
        RTC_DCHECK_RUN_ON(network_thread());
        auto rtp_transport = transport_controller_->GetRtpTransport(mid);
        RTC_DCHECK(rtp_transport);
        return rtp_transport;
      });
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::AddTransceiver(
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const RtpTransceiverInit& init) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(IsUnifiedPlan())
      << "AddTransceiver is only available with Unified Plan SdpSemantics";
  if (!track) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER, "track is null");
  }
  cricket::MediaType media_type;
  if (track->kind() == MediaStreamTrackInterface::kAudioKind) {
    media_type = cricket::MEDIA_TYPE_AUDIO;
  } else if (track->kind() == MediaStreamTrackInterface::kVideoKind) {
    media_type = cricket::MEDIA_TYPE_VIDEO;
  } else {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "Track kind is not audio or video");
  }
  return AddTransceiver(media_type, track, init);
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::AddTransceiver(cricket::MediaType media_type) {
  return AddTransceiver(media_type, RtpTransceiverInit());
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::AddTransceiver(cricket::MediaType media_type,
                               const RtpTransceiverInit& init) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(IsUnifiedPlan())
      << "AddTransceiver is only available with Unified Plan SdpSemantics";
  if (!(media_type == cricket::MEDIA_TYPE_AUDIO ||
        media_type == cricket::MEDIA_TYPE_VIDEO)) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "media type is not audio or video");
  }
  return AddTransceiver(media_type, nullptr, init);
}

RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::AddTransceiver(
    cricket::MediaType media_type,
    rtc::scoped_refptr<MediaStreamTrackInterface> track,
    const RtpTransceiverInit& init,
    bool update_negotiation_needed) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK((media_type == cricket::MEDIA_TYPE_AUDIO ||
              media_type == cricket::MEDIA_TYPE_VIDEO));
  if (track) {
    RTC_DCHECK_EQ(media_type,
                  (track->kind() == MediaStreamTrackInterface::kAudioKind
                       ? cricket::MEDIA_TYPE_AUDIO
                       : cricket::MEDIA_TYPE_VIDEO));
  }

  RTC_HISTOGRAM_COUNTS_LINEAR(kSimulcastNumberOfEncodings,
                              init.send_encodings.size(), 0, 7, 8);

  size_t num_rids = absl::c_count_if(init.send_encodings,
                                     [](const RtpEncodingParameters& encoding) {
                                       return !encoding.rid.empty();
                                     });
  if (num_rids > 0 && num_rids != init.send_encodings.size()) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::INVALID_PARAMETER,
        "RIDs must be provided for either all or none of the send encodings.");
  }

  if (num_rids > 0 && absl::c_any_of(init.send_encodings,
                                     [](const RtpEncodingParameters& encoding) {
                                       return !IsLegalRsidName(encoding.rid);
                                     })) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "Invalid RID value provided.");
  }

  if (absl::c_any_of(init.send_encodings,
                     [](const RtpEncodingParameters& encoding) {
                       return encoding.ssrc.has_value();
                     })) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::UNSUPPORTED_PARAMETER,
        "Attempted to set an unimplemented parameter of RtpParameters.");
  }

  RtpParameters parameters;
  parameters.encodings = init.send_encodings;

  // Encodings are dropped from the tail if too many are provided.
  size_t max_simulcast_streams =
      media_type == cricket::MEDIA_TYPE_VIDEO ? kMaxSimulcastStreams : 1u;
  if (parameters.encodings.size() > max_simulcast_streams) {
    parameters.encodings.erase(
        parameters.encodings.begin() + max_simulcast_streams,
        parameters.encodings.end());
  }

  // Single RID should be removed.
  if (parameters.encodings.size() == 1 &&
      !parameters.encodings[0].rid.empty()) {
    RTC_LOG(LS_INFO) << "Removing RID: " << parameters.encodings[0].rid << ".";
    parameters.encodings[0].rid.clear();
  }

  // If RIDs were not provided, they are generated for simulcast scenario.
  if (parameters.encodings.size() > 1 && num_rids == 0) {
    rtc::UniqueStringGenerator rid_generator;
    for (RtpEncodingParameters& encoding : parameters.encodings) {
      encoding.rid = rid_generator();
    }
  }

  if (UnimplementedRtpParameterHasValue(parameters)) {
    LOG_AND_RETURN_ERROR(
        RTCErrorType::UNSUPPORTED_PARAMETER,
        "Attempted to set an unimplemented parameter of RtpParameters.");
  }

  auto result = cricket::CheckRtpParametersValues(parameters);
  if (!result.ok()) {
    LOG_AND_RETURN_ERROR(result.type(), result.message());
  }

  RTC_LOG(LS_INFO) << "Adding " << cricket::MediaTypeToString(media_type)
                   << " transceiver in response to a call to AddTransceiver.";
  // Set the sender ID equal to the track ID if the track is specified unless
  // that sender ID is already in use.
  std::string sender_id = (track && !rtp_manager()->FindSenderById(track->id())
                               ? track->id()
                               : rtc::CreateRandomUuid());
  auto sender = rtp_manager()->CreateSender(
      media_type, sender_id, track, init.stream_ids, parameters.encodings);
  auto receiver =
      rtp_manager()->CreateReceiver(media_type, rtc::CreateRandomUuid());
  auto transceiver = rtp_manager()->CreateAndAddTransceiver(sender, receiver);
  transceiver->internal()->set_direction(init.direction);

  if (update_negotiation_needed) {
    sdp_handler_->UpdateNegotiationNeeded();
  }

  return rtc::scoped_refptr<RtpTransceiverInterface>(transceiver);
}

void PeerConnection::OnNegotiationNeeded() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(!IsClosed());
  sdp_handler_->UpdateNegotiationNeeded();
}

rtc::scoped_refptr<RtpSenderInterface> PeerConnection::CreateSender(
    const std::string& kind,
    const std::string& stream_id) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(!IsUnifiedPlan()) << "CreateSender is not available with Unified "
                                 "Plan SdpSemantics. Please use AddTransceiver "
                                 "instead.";
  TRACE_EVENT0("webrtc", "PeerConnection::CreateSender");
  if (IsClosed()) {
    return nullptr;
  }

  // Internally we need to have one stream with Plan B semantics, so we
  // generate a random stream ID if not specified.
  std::vector<std::string> stream_ids;
  if (stream_id.empty()) {
    stream_ids.push_back(rtc::CreateRandomUuid());
    RTC_LOG(LS_INFO)
        << "No stream_id specified for sender. Generated stream ID: "
        << stream_ids[0];
  } else {
    stream_ids.push_back(stream_id);
  }

  // TODO(steveanton): Move construction of the RtpSenders to RtpTransceiver.
  rtc::scoped_refptr<RtpSenderProxyWithInternal<RtpSenderInternal>> new_sender;
  if (kind == MediaStreamTrackInterface::kAudioKind) {
    auto audio_sender = AudioRtpSender::Create(
        worker_thread(), rtc::CreateRandomUuid(), stats_.get(), rtp_manager());
    audio_sender->SetMediaChannel(rtp_manager()->voice_media_channel());
    new_sender = RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
        signaling_thread(), audio_sender);
    rtp_manager()->GetAudioTransceiver()->internal()->AddSender(new_sender);
  } else if (kind == MediaStreamTrackInterface::kVideoKind) {
    auto video_sender = VideoRtpSender::Create(
        worker_thread(), rtc::CreateRandomUuid(), rtp_manager());
    video_sender->SetMediaChannel(rtp_manager()->video_media_channel());
    new_sender = RtpSenderProxyWithInternal<RtpSenderInternal>::Create(
        signaling_thread(), video_sender);
    rtp_manager()->GetVideoTransceiver()->internal()->AddSender(new_sender);
  } else {
    RTC_LOG(LS_ERROR) << "CreateSender called with invalid kind: " << kind;
    return nullptr;
  }
  new_sender->internal()->set_stream_ids(stream_ids);

  return new_sender;
}

std::vector<rtc::scoped_refptr<RtpSenderInterface>> PeerConnection::GetSenders()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<RtpSenderInterface>> ret;
  for (const auto& sender : rtp_manager()->GetSendersInternal()) {
    ret.push_back(sender);
  }
  return ret;
}

std::vector<rtc::scoped_refptr<RtpReceiverInterface>>
PeerConnection::GetReceivers() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  std::vector<rtc::scoped_refptr<RtpReceiverInterface>> ret;
  for (const auto& receiver : rtp_manager()->GetReceiversInternal()) {
    ret.push_back(receiver);
  }
  return ret;
}

std::vector<rtc::scoped_refptr<RtpTransceiverInterface>>
PeerConnection::GetTransceivers() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_CHECK(IsUnifiedPlan())
      << "GetTransceivers is only supported with Unified Plan SdpSemantics.";
  std::vector<rtc::scoped_refptr<RtpTransceiverInterface>> all_transceivers;
  for (const auto& transceiver : rtp_manager()->transceivers()->List()) {
    all_transceivers.push_back(transceiver);
  }
  return all_transceivers;
}

bool PeerConnection::GetStats(StatsObserver* observer,
                              MediaStreamTrackInterface* track,
                              StatsOutputLevel level) {
  TRACE_EVENT0("webrtc", "PeerConnection::GetStats");
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!observer) {
    RTC_LOG(LS_ERROR) << "GetStats - observer is NULL.";
    return false;
  }

  RTC_LOG_THREAD_BLOCK_COUNT();

  stats_->UpdateStats(level);

  RTC_DCHECK_BLOCK_COUNT_NO_MORE_THAN(4);

  // The StatsCollector is used to tell if a track is valid because it may
  // remember tracks that the PeerConnection previously removed.
  if (track && !stats_->IsValidTrack(track->id())) {
    RTC_LOG(LS_WARNING) << "GetStats is called with an invalid track: "
                        << track->id();
    return false;
  }
  message_handler_.PostGetStats(observer, stats_.get(), track);

  return true;
}

void PeerConnection::GetStats(RTCStatsCollectorCallback* callback) {
  TRACE_EVENT0("webrtc", "PeerConnection::GetStats");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(stats_collector_);
  RTC_DCHECK(callback);
  RTC_LOG_THREAD_BLOCK_COUNT();
  stats_collector_->GetStatsReport(
      rtc::scoped_refptr<RTCStatsCollectorCallback>(callback));
}

void PeerConnection::GetStats(
    rtc::scoped_refptr<RtpSenderInterface> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback) {
  TRACE_EVENT0("webrtc", "PeerConnection::GetStats");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(callback);
  RTC_DCHECK(stats_collector_);
  rtc::scoped_refptr<RtpSenderInternal> internal_sender;
  if (selector) {
    for (const auto& proxy_transceiver :
         rtp_manager()->transceivers()->List()) {
      for (const auto& proxy_sender :
           proxy_transceiver->internal()->senders()) {
        if (proxy_sender == selector) {
          internal_sender = proxy_sender->internal();
          break;
        }
      }
      if (internal_sender)
        break;
    }
  }
  // If there is no `internal_sender` then `selector` is either null or does not
  // belong to the PeerConnection (in Plan B, senders can be removed from the
  // PeerConnection). This means that "all the stats objects representing the
  // selector" is an empty set. Invoking GetStatsReport() with a null selector
  // produces an empty stats report.
  stats_collector_->GetStatsReport(internal_sender, callback);
}

void PeerConnection::GetStats(
    rtc::scoped_refptr<RtpReceiverInterface> selector,
    rtc::scoped_refptr<RTCStatsCollectorCallback> callback) {
  TRACE_EVENT0("webrtc", "PeerConnection::GetStats");
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(callback);
  RTC_DCHECK(stats_collector_);
  rtc::scoped_refptr<RtpReceiverInternal> internal_receiver;
  if (selector) {
    for (const auto& proxy_transceiver :
         rtp_manager()->transceivers()->List()) {
      for (const auto& proxy_receiver :
           proxy_transceiver->internal()->receivers()) {
        if (proxy_receiver == selector) {
          internal_receiver = proxy_receiver->internal();
          break;
        }
      }
      if (internal_receiver)
        break;
    }
  }
  // If there is no `internal_receiver` then `selector` is either null or does
  // not belong to the PeerConnection (in Plan B, receivers can be removed from
  // the PeerConnection). This means that "all the stats objects representing
  // the selector" is an empty set. Invoking GetStatsReport() with a null
  // selector produces an empty stats report.
  stats_collector_->GetStatsReport(internal_receiver, callback);
}

PeerConnectionInterface::SignalingState PeerConnection::signaling_state() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->signaling_state();
}

PeerConnectionInterface::IceConnectionState
PeerConnection::ice_connection_state() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return ice_connection_state_;
}

PeerConnectionInterface::IceConnectionState
PeerConnection::standardized_ice_connection_state() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return standardized_ice_connection_state_;
}

PeerConnectionInterface::PeerConnectionState
PeerConnection::peer_connection_state() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return connection_state_;
}

PeerConnectionInterface::IceGatheringState
PeerConnection::ice_gathering_state() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return ice_gathering_state_;
}

absl::optional<bool> PeerConnection::can_trickle_ice_candidates() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  const SessionDescriptionInterface* description = current_remote_description();
  if (!description) {
    description = pending_remote_description();
  }
  if (!description) {
    return absl::nullopt;
  }
  // TODO(bugs.webrtc.org/7443): Change to retrieve from session-level option.
  if (description->description()->transport_infos().size() < 1) {
    return absl::nullopt;
  }
  return description->description()->transport_infos()[0].description.HasOption(
      "trickle");
}

RTCErrorOr<rtc::scoped_refptr<DataChannelInterface>>
PeerConnection::CreateDataChannelOrError(const std::string& label,
                                         const DataChannelInit* config) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "PeerConnection::CreateDataChannel");

  bool first_datachannel = !data_channel_controller_.HasDataChannels();

  std::unique_ptr<InternalDataChannelInit> internal_config;
  if (config) {
    internal_config.reset(new InternalDataChannelInit(*config));
  }
  // TODO(bugs.webrtc.org/12796): Return a more specific error.
  rtc::scoped_refptr<DataChannelInterface> channel(
      data_channel_controller_.InternalCreateDataChannelWithProxy(
          label, internal_config.get()));
  if (!channel.get()) {
    return RTCError(RTCErrorType::INTERNAL_ERROR,
                    "Data channel creation failed");
  }

  // Trigger the onRenegotiationNeeded event for
  // the first SCTP DataChannel.
  if (first_datachannel) {
    sdp_handler_->UpdateNegotiationNeeded();
  }
  NoteUsageEvent(UsageEvent::DATA_ADDED);
  return channel;
}

void PeerConnection::RestartIce() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->RestartIce();
}

void PeerConnection::CreateOffer(CreateSessionDescriptionObserver* observer,
                                 const RTCOfferAnswerOptions& options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->CreateOffer(observer, options);
}

void PeerConnection::CreateAnswer(CreateSessionDescriptionObserver* observer,
                                  const RTCOfferAnswerOptions& options) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->CreateAnswer(observer, options);
}

void PeerConnection::SetLocalDescription(
    SetSessionDescriptionObserver* observer,
    SessionDescriptionInterface* desc_ptr) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetLocalDescription(observer, desc_ptr);
}

void PeerConnection::SetLocalDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    rtc::scoped_refptr<SetLocalDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetLocalDescription(std::move(desc), observer);
}

void PeerConnection::SetLocalDescription(
    SetSessionDescriptionObserver* observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetLocalDescription(observer);
}

void PeerConnection::SetLocalDescription(
    rtc::scoped_refptr<SetLocalDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetLocalDescription(observer);
}

void PeerConnection::SetRemoteDescription(
    SetSessionDescriptionObserver* observer,
    SessionDescriptionInterface* desc_ptr) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetRemoteDescription(observer, desc_ptr);
}

void PeerConnection::SetRemoteDescription(
    std::unique_ptr<SessionDescriptionInterface> desc,
    rtc::scoped_refptr<SetRemoteDescriptionObserverInterface> observer) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->SetRemoteDescription(std::move(desc), observer);
}

PeerConnectionInterface::RTCConfiguration PeerConnection::GetConfiguration() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return configuration_;
}

RTCError PeerConnection::SetConfiguration(
    const RTCConfiguration& configuration) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "PeerConnection::SetConfiguration");
  if (IsClosed()) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_STATE,
                         "SetConfiguration: PeerConnection is closed.");
  }

  // According to JSEP, after setLocalDescription, changing the candidate pool
  // size is not allowed, and changing the set of ICE servers will not result
  // in new candidates being gathered.
  if (local_description() && configuration.ice_candidate_pool_size !=
                                 configuration_.ice_candidate_pool_size) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_MODIFICATION,
                         "Can't change candidate pool size after calling "
                         "SetLocalDescription.");
  }

  if (local_description() &&
      configuration.crypto_options != configuration_.crypto_options) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_MODIFICATION,
                         "Can't change crypto_options after calling "
                         "SetLocalDescription.");
  }

  // The simplest (and most future-compatible) way to tell if the config was
  // modified in an invalid way is to copy each property we do support
  // modifying, then use operator==. There are far more properties we don't
  // support modifying than those we do, and more could be added.
  RTCConfiguration modified_config = configuration_;
  modified_config.servers = configuration.servers;
  modified_config.type = configuration.type;
  modified_config.ice_candidate_pool_size =
      configuration.ice_candidate_pool_size;
  modified_config.prune_turn_ports = configuration.prune_turn_ports;
  modified_config.turn_port_prune_policy = configuration.turn_port_prune_policy;
  modified_config.surface_ice_candidates_on_ice_transport_type_changed =
      configuration.surface_ice_candidates_on_ice_transport_type_changed;
  modified_config.ice_check_min_interval = configuration.ice_check_min_interval;
  modified_config.ice_check_interval_strong_connectivity =
      configuration.ice_check_interval_strong_connectivity;
  modified_config.ice_check_interval_weak_connectivity =
      configuration.ice_check_interval_weak_connectivity;
  modified_config.ice_unwritable_timeout = configuration.ice_unwritable_timeout;
  modified_config.ice_unwritable_min_checks =
      configuration.ice_unwritable_min_checks;
  modified_config.ice_inactive_timeout = configuration.ice_inactive_timeout;
  modified_config.stun_candidate_keepalive_interval =
      configuration.stun_candidate_keepalive_interval;
  modified_config.turn_customizer = configuration.turn_customizer;
  modified_config.network_preference = configuration.network_preference;
  modified_config.active_reset_srtp_params =
      configuration.active_reset_srtp_params;
  modified_config.turn_logging_id = configuration.turn_logging_id;
  modified_config.allow_codec_switching = configuration.allow_codec_switching;
  modified_config.stable_writable_connection_ping_interval_ms =
      configuration.stable_writable_connection_ping_interval_ms;
  if (configuration != modified_config) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_MODIFICATION,
                         "Modifying the configuration in an unsupported way.");
  }

  // Validate the modified configuration.
  RTCError validate_error = ValidateConfiguration(modified_config);
  if (!validate_error.ok()) {
    return validate_error;
  }

  // Note that this isn't possible through chromium, since it's an unsigned
  // short in WebIDL.
  if (configuration.ice_candidate_pool_size < 0 ||
      configuration.ice_candidate_pool_size > static_cast<int>(UINT16_MAX)) {
    return RTCError(RTCErrorType::INVALID_RANGE);
  }

  // Parse ICE servers before hopping to network thread.
  cricket::ServerAddresses stun_servers;
  std::vector<cricket::RelayServerConfig> turn_servers;
  RTCErrorType parse_error =
      ParseIceServers(configuration.servers, &stun_servers, &turn_servers);
  if (parse_error != RTCErrorType::NONE) {
    return RTCError(parse_error, "ICE server parse failed");
  }
  // Add the turn logging id to all turn servers
  for (cricket::RelayServerConfig& turn_server : turn_servers) {
    turn_server.turn_logging_id = configuration.turn_logging_id;
  }

  // Note if STUN or TURN servers were supplied.
  if (!stun_servers.empty()) {
    NoteUsageEvent(UsageEvent::STUN_SERVER_ADDED);
  }
  if (!turn_servers.empty()) {
    NoteUsageEvent(UsageEvent::TURN_SERVER_ADDED);
  }

  const bool has_local_description = local_description() != nullptr;

  const bool needs_ice_restart =
      modified_config.servers != configuration_.servers ||
      NeedIceRestart(
          configuration_.surface_ice_candidates_on_ice_transport_type_changed,
          configuration_.type, modified_config.type) ||
      modified_config.GetTurnPortPrunePolicy() !=
          configuration_.GetTurnPortPrunePolicy();
  cricket::IceConfig ice_config = ParseIceConfig(modified_config);

  // Apply part of the configuration on the network thread.  In theory this
  // shouldn't fail.
  if (!network_thread()->Invoke<bool>(
          RTC_FROM_HERE,
          [this, needs_ice_restart, &ice_config, &stun_servers, &turn_servers,
           &modified_config, has_local_description] {
            RTC_DCHECK_RUN_ON(network_thread());
            // As described in JSEP, calling setConfiguration with new ICE
            // servers or candidate policy must set a "needs-ice-restart" bit so
            // that the next offer triggers an ICE restart which will pick up
            // the changes.
            if (needs_ice_restart)
              transport_controller_->SetNeedsIceRestartFlag();

            transport_controller_->SetIceConfig(ice_config);
            return ReconfigurePortAllocator_n(
                stun_servers, turn_servers, modified_config.type,
                modified_config.ice_candidate_pool_size,
                modified_config.GetTurnPortPrunePolicy(),
                modified_config.turn_customizer,
                modified_config.stun_candidate_keepalive_interval,
                has_local_description);
          })) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INTERNAL_ERROR,
                         "Failed to apply configuration to PortAllocator.");
  }

  if (configuration_.active_reset_srtp_params !=
      modified_config.active_reset_srtp_params) {
    // TODO(tommi): merge invokes
    network_thread()->Invoke<void>(RTC_FROM_HERE, [this, &modified_config] {
      RTC_DCHECK_RUN_ON(network_thread());
      transport_controller_->SetActiveResetSrtpParams(
          modified_config.active_reset_srtp_params);
    });
  }

  if (modified_config.allow_codec_switching.has_value()) {
    std::vector<cricket::VideoMediaChannel*> channels;
    for (const auto& transceiver : rtp_manager()->transceivers()->List()) {
      if (transceiver->media_type() != cricket::MEDIA_TYPE_VIDEO)
        continue;

      auto* video_channel = static_cast<cricket::VideoChannel*>(
          transceiver->internal()->channel());
      if (video_channel)
        channels.push_back(video_channel->media_channel());
    }

    worker_thread()->Invoke<void>(
        RTC_FROM_HERE,
        [channels = std::move(channels),
         allow_codec_switching = *modified_config.allow_codec_switching]() {
          for (auto* ch : channels)
            ch->SetVideoCodecSwitchingEnabled(allow_codec_switching);
        });
  }

  configuration_ = modified_config;
  return RTCError::OK();
}

bool PeerConnection::AddIceCandidate(
    const IceCandidateInterface* ice_candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->AddIceCandidate(ice_candidate);
}

void PeerConnection::AddIceCandidate(
    std::unique_ptr<IceCandidateInterface> candidate,
    std::function<void(RTCError)> callback) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sdp_handler_->AddIceCandidate(std::move(candidate), callback);
}

bool PeerConnection::RemoveIceCandidates(
    const std::vector<cricket::Candidate>& candidates) {
  TRACE_EVENT0("webrtc", "PeerConnection::RemoveIceCandidates");
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->RemoveIceCandidates(candidates);
}

RTCError PeerConnection::SetBitrate(const BitrateSettings& bitrate) {
  if (!worker_thread()->IsCurrent()) {
    return worker_thread()->Invoke<RTCError>(
        RTC_FROM_HERE, [&]() { return SetBitrate(bitrate); });
  }
  RTC_DCHECK_RUN_ON(worker_thread());

  const bool has_min = bitrate.min_bitrate_bps.has_value();
  const bool has_start = bitrate.start_bitrate_bps.has_value();
  const bool has_max = bitrate.max_bitrate_bps.has_value();
  if (has_min && *bitrate.min_bitrate_bps < 0) {
    LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                         "min_bitrate_bps <= 0");
  }
  if (has_start) {
    if (has_min && *bitrate.start_bitrate_bps < *bitrate.min_bitrate_bps) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "start_bitrate_bps < min_bitrate_bps");
    } else if (*bitrate.start_bitrate_bps < 0) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "curent_bitrate_bps < 0");
    }
  }
  if (has_max) {
    if (has_start && *bitrate.max_bitrate_bps < *bitrate.start_bitrate_bps) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "max_bitrate_bps < start_bitrate_bps");
    } else if (has_min && *bitrate.max_bitrate_bps < *bitrate.min_bitrate_bps) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "max_bitrate_bps < min_bitrate_bps");
    } else if (*bitrate.max_bitrate_bps < 0) {
      LOG_AND_RETURN_ERROR(RTCErrorType::INVALID_PARAMETER,
                           "max_bitrate_bps < 0");
    }
  }

  RTC_DCHECK(call_.get());
  call_->SetClientBitratePreferences(bitrate);

  return RTCError::OK();
}

void PeerConnection::SetAudioPlayout(bool playout) {
  if (!worker_thread()->IsCurrent()) {
    worker_thread()->Invoke<void>(
        RTC_FROM_HERE, [this, playout] { SetAudioPlayout(playout); });
    return;
  }
  auto audio_state =
      context_->channel_manager()->media_engine()->voice().GetAudioState();
  audio_state->SetPlayout(playout);
}

void PeerConnection::SetAudioRecording(bool recording) {
  if (!worker_thread()->IsCurrent()) {
    worker_thread()->Invoke<void>(
        RTC_FROM_HERE, [this, recording] { SetAudioRecording(recording); });
    return;
  }
  auto audio_state =
      context_->channel_manager()->media_engine()->voice().GetAudioState();
  audio_state->SetRecording(recording);
}

void PeerConnection::AddAdaptationResource(
    rtc::scoped_refptr<Resource> resource) {
  if (!worker_thread()->IsCurrent()) {
    return worker_thread()->Invoke<void>(RTC_FROM_HERE, [this, resource]() {
      return AddAdaptationResource(resource);
    });
  }
  RTC_DCHECK_RUN_ON(worker_thread());
  if (!call_) {
    // The PeerConnection has been closed.
    return;
  }
  call_->AddAdaptationResource(resource);
}

cricket::ChannelManager* PeerConnection::channel_manager() {
  return context_->channel_manager();
}

bool PeerConnection::StartRtcEventLog(std::unique_ptr<RtcEventLogOutput> output,
                                      int64_t output_period_ms) {
  return worker_thread()->Invoke<bool>(
      RTC_FROM_HERE,
      [this, output = std::move(output), output_period_ms]() mutable {
        return StartRtcEventLog_w(std::move(output), output_period_ms);
      });
}

bool PeerConnection::StartRtcEventLog(
    std::unique_ptr<RtcEventLogOutput> output) {
  int64_t output_period_ms = webrtc::RtcEventLog::kImmediateOutput;
  if (absl::StartsWith(context_->trials().Lookup("WebRTC-RtcEventLogNewFormat"),
                       "Enabled")) {
    output_period_ms = 5000;
  }
  return StartRtcEventLog(std::move(output), output_period_ms);
}

void PeerConnection::StopRtcEventLog() {
  worker_thread()->Invoke<void>(RTC_FROM_HERE, [this] { StopRtcEventLog_w(); });
}

rtc::scoped_refptr<DtlsTransportInterface>
PeerConnection::LookupDtlsTransportByMid(const std::string& mid) {
  RTC_DCHECK_RUN_ON(network_thread());
  return transport_controller_->LookupDtlsTransportByMid(mid);
}

rtc::scoped_refptr<DtlsTransport>
PeerConnection::LookupDtlsTransportByMidInternal(const std::string& mid) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // TODO(bugs.webrtc.org/9987): Avoid the thread jump.
  // This might be done by caching the value on the signaling thread.
  return network_thread()->Invoke<rtc::scoped_refptr<DtlsTransport>>(
      RTC_FROM_HERE, [this, mid]() {
        RTC_DCHECK_RUN_ON(network_thread());
        return transport_controller_->LookupDtlsTransportByMid(mid);
      });
}

rtc::scoped_refptr<SctpTransportInterface> PeerConnection::GetSctpTransport()
    const {
  RTC_DCHECK_RUN_ON(network_thread());
  if (!sctp_mid_n_)
    return nullptr;

  return transport_controller_->GetSctpTransport(*sctp_mid_n_);
}

const SessionDescriptionInterface* PeerConnection::local_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->local_description();
}

const SessionDescriptionInterface* PeerConnection::remote_description() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->remote_description();
}

const SessionDescriptionInterface* PeerConnection::current_local_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->current_local_description();
}

const SessionDescriptionInterface* PeerConnection::current_remote_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->current_remote_description();
}

const SessionDescriptionInterface* PeerConnection::pending_local_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->pending_local_description();
}

const SessionDescriptionInterface* PeerConnection::pending_remote_description()
    const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->pending_remote_description();
}

void PeerConnection::Close() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  TRACE_EVENT0("webrtc", "PeerConnection::Close");

  RTC_LOG_THREAD_BLOCK_COUNT();

  if (IsClosed()) {
    return;
  }
  // Update stats here so that we have the most recent stats for tracks and
  // streams before the channels are closed.
  stats_->UpdateStats(kStatsOutputLevelStandard);

  ice_connection_state_ = PeerConnectionInterface::kIceConnectionClosed;
  Observer()->OnIceConnectionChange(ice_connection_state_);
  standardized_ice_connection_state_ =
      PeerConnectionInterface::IceConnectionState::kIceConnectionClosed;
  connection_state_ = PeerConnectionInterface::PeerConnectionState::kClosed;
  Observer()->OnConnectionChange(connection_state_);

  sdp_handler_->Close();

  NoteUsageEvent(UsageEvent::CLOSE_CALLED);

  for (const auto& transceiver : rtp_manager()->transceivers()->List()) {
    transceiver->internal()->SetPeerConnectionClosed();
    if (!transceiver->stopped())
      transceiver->StopInternal();
  }

  // Ensure that all asynchronous stats requests are completed before destroying
  // the transport controller below.
  if (stats_collector_) {
    stats_collector_->WaitForPendingRequest();
  }

  // Don't destroy BaseChannels until after stats has been cleaned up so that
  // the last stats request can still read from the channels.
  sdp_handler_->DestroyAllChannels();

  // The event log is used in the transport controller, which must be outlived
  // by the former. CreateOffer by the peer connection is implemented
  // asynchronously and if the peer connection is closed without resetting the
  // WebRTC session description factory, the session description factory would
  // call the transport controller.
  sdp_handler_->ResetSessionDescFactory();
  rtp_manager_->Close();

  network_thread()->Invoke<void>(RTC_FROM_HERE, [this] {
    // Data channels will already have been unset via the DestroyAllChannels()
    // call above, which triggers a call to TeardownDataChannelTransport_n().
    // TODO(tommi): ^^ That's not exactly optimal since this is yet another
    // blocking hop to the network thread during Close(). Further still, the
    // voice/video/data channels will be cleared on the worker thread.
    RTC_DCHECK_RUN_ON(network_thread());
    transport_controller_.reset();
    port_allocator_->DiscardCandidatePool();
    if (network_thread_safety_) {
      network_thread_safety_->SetNotAlive();
    }
  });

  worker_thread()->Invoke<void>(RTC_FROM_HERE, [this] {
    RTC_DCHECK_RUN_ON(worker_thread());
    worker_thread_safety_->SetNotAlive();
    call_.reset();
    // The event log must outlive call (and any other object that uses it).
    event_log_.reset();
  });
  ReportUsagePattern();
  // The .h file says that observer can be discarded after close() returns.
  // Make sure this is true.
  observer_ = nullptr;

  // Signal shutdown to the sdp handler. This invalidates weak pointers for
  // internal pending callbacks.
  sdp_handler_->PrepareForShutdown();
}

void PeerConnection::SetIceConnectionState(IceConnectionState new_state) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (ice_connection_state_ == new_state) {
    return;
  }

  // After transitioning to "closed", ignore any additional states from
  // TransportController (such as "disconnected").
  if (IsClosed()) {
    return;
  }

  RTC_LOG(LS_INFO) << "Changing IceConnectionState " << ice_connection_state_
                   << " => " << new_state;
  RTC_DCHECK(ice_connection_state_ !=
             PeerConnectionInterface::kIceConnectionClosed);

  ice_connection_state_ = new_state;
  Observer()->OnIceConnectionChange(ice_connection_state_);
}

void PeerConnection::SetStandardizedIceConnectionState(
    PeerConnectionInterface::IceConnectionState new_state) {
  if (standardized_ice_connection_state_ == new_state) {
    return;
  }

  if (IsClosed()) {
    return;
  }

  RTC_LOG(LS_INFO) << "Changing standardized IceConnectionState "
                   << standardized_ice_connection_state_ << " => " << new_state;

  standardized_ice_connection_state_ = new_state;
  Observer()->OnStandardizedIceConnectionChange(new_state);
}

void PeerConnection::SetConnectionState(
    PeerConnectionInterface::PeerConnectionState new_state) {
  if (connection_state_ == new_state)
    return;
  if (IsClosed())
    return;
  connection_state_ = new_state;
  Observer()->OnConnectionChange(new_state);

  if (new_state == PeerConnectionState::kConnected && !was_ever_connected_) {
    was_ever_connected_ = true;

    // The first connection state change to connected happens once per
    // connection which makes it a good point to report metrics.
    // Record bundle-policy from configuration. Done here from
    // connectionStateChange to limit to actually established connections.
    BundlePolicyUsage policy = kBundlePolicyUsageMax;
    switch (configuration_.bundle_policy) {
      case kBundlePolicyBalanced:
        policy = kBundlePolicyUsageBalanced;
        break;
      case kBundlePolicyMaxBundle:
        policy = kBundlePolicyUsageMaxBundle;
        break;
      case kBundlePolicyMaxCompat:
        policy = kBundlePolicyUsageMaxCompat;
        break;
    }
    RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.BundlePolicy", policy,
                              kBundlePolicyUsageMax);

    // Record configured ice candidate pool size depending on the
    // BUNDLE policy. See
    // https://w3c.github.io/webrtc-pc/#dom-rtcconfiguration-icecandidatepoolsize
    // The ICE candidate pool size is an optimization and it may be desirable
    // to restrict the maximum size of the pre-gathered candidates.
    switch (configuration_.bundle_policy) {
      case kBundlePolicyBalanced:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.PeerConnection.CandidatePoolUsage.Balanced",
            configuration_.ice_candidate_pool_size, 0, 255, 256);
        break;
      case kBundlePolicyMaxBundle:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.PeerConnection.CandidatePoolUsage.MaxBundle",
            configuration_.ice_candidate_pool_size, 0, 255, 256);
        break;
      case kBundlePolicyMaxCompat:
        RTC_HISTOGRAM_COUNTS_LINEAR(
            "WebRTC.PeerConnection.CandidatePoolUsage.MaxCompat",
            configuration_.ice_candidate_pool_size, 0, 255, 256);
        break;
    }

    // Record whether there was a local or remote provisional answer.
    ProvisionalAnswerUsage pranswer = kProvisionalAnswerNotUsed;
    if (local_description()->GetType() == SdpType::kPrAnswer) {
      pranswer = kProvisionalAnswerLocal;
    } else if (remote_description()->GetType() == SdpType::kPrAnswer) {
      pranswer = kProvisionalAnswerRemote;
    }
    RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.ProvisionalAnswer",
                              pranswer, kProvisionalAnswerMax);

    // Record the number of configured ICE servers for connected connections.
    RTC_HISTOGRAM_COUNTS_LINEAR("WebRTC.PeerConnection.IceServers.Connected",
                                configuration_.servers.size(), 0, 31, 32);
  }
}

void PeerConnection::OnIceGatheringChange(
    PeerConnectionInterface::IceGatheringState new_state) {
  if (IsClosed()) {
    return;
  }
  ice_gathering_state_ = new_state;
  Observer()->OnIceGatheringChange(ice_gathering_state_);
}

void PeerConnection::OnIceCandidate(
    std::unique_ptr<IceCandidateInterface> candidate) {
  if (IsClosed()) {
    return;
  }
  ReportIceCandidateCollected(candidate->candidate());
  Observer()->OnIceCandidate(candidate.get());
}

void PeerConnection::OnIceCandidateError(const std::string& address,
                                         int port,
                                         const std::string& url,
                                         int error_code,
                                         const std::string& error_text) {
  if (IsClosed()) {
    return;
  }
  Observer()->OnIceCandidateError(address, port, url, error_code, error_text);
}

void PeerConnection::OnIceCandidatesRemoved(
    const std::vector<cricket::Candidate>& candidates) {
  if (IsClosed()) {
    return;
  }
  Observer()->OnIceCandidatesRemoved(candidates);
}

void PeerConnection::OnSelectedCandidatePairChanged(
    const cricket::CandidatePairChangeEvent& event) {
  if (IsClosed()) {
    return;
  }

  if (event.selected_candidate_pair.local_candidate().type() ==
          LOCAL_PORT_TYPE &&
      event.selected_candidate_pair.remote_candidate().type() ==
          LOCAL_PORT_TYPE) {
    NoteUsageEvent(UsageEvent::DIRECT_CONNECTION_SELECTED);
  }

  Observer()->OnIceSelectedCandidatePairChanged(event);
}

absl::optional<std::string> PeerConnection::GetDataMid() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sctp_mid_s_;
}

void PeerConnection::SetSctpDataMid(const std::string& mid) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sctp_mid_s_ = mid;
}

void PeerConnection::ResetSctpDataMid() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  sctp_mid_s_.reset();
  sctp_transport_name_s_.clear();
}

void PeerConnection::OnSctpDataChannelClosed(DataChannelInterface* channel) {
  // Since data_channel_controller doesn't do signals, this
  // signal is relayed here.
  data_channel_controller_.OnSctpDataChannelClosed(
      static_cast<SctpDataChannel*>(channel));
}

SctpDataChannel* PeerConnection::FindDataChannelBySid(int sid) const {
  return data_channel_controller_.FindDataChannelBySid(sid);
}

PeerConnection::InitializePortAllocatorResult
PeerConnection::InitializePortAllocator_n(
    const cricket::ServerAddresses& stun_servers,
    const std::vector<cricket::RelayServerConfig>& turn_servers,
    const RTCConfiguration& configuration) {
  RTC_DCHECK_RUN_ON(network_thread());

  port_allocator_->Initialize();
  // To handle both internal and externally created port allocator, we will
  // enable BUNDLE here.
  int port_allocator_flags = port_allocator_->flags();
  port_allocator_flags |= cricket::PORTALLOCATOR_ENABLE_SHARED_SOCKET |
                          cricket::PORTALLOCATOR_ENABLE_IPV6 |
                          cricket::PORTALLOCATOR_ENABLE_IPV6_ON_WIFI;
  // If the disable-IPv6 flag was specified, we'll not override it
  // by experiment.
  if (configuration.disable_ipv6) {
    port_allocator_flags &= ~(cricket::PORTALLOCATOR_ENABLE_IPV6);
  } else if (absl::StartsWith(context_->trials().Lookup("WebRTC-IPv6Default"),
                              "Disabled")) {
    port_allocator_flags &= ~(cricket::PORTALLOCATOR_ENABLE_IPV6);
  }
  if (configuration.disable_ipv6_on_wifi) {
    port_allocator_flags &= ~(cricket::PORTALLOCATOR_ENABLE_IPV6_ON_WIFI);
    RTC_LOG(LS_INFO) << "IPv6 candidates on Wi-Fi are disabled.";
  }

  if (configuration.tcp_candidate_policy == kTcpCandidatePolicyDisabled) {
    port_allocator_flags |= cricket::PORTALLOCATOR_DISABLE_TCP;
    RTC_LOG(LS_INFO) << "TCP candidates are disabled.";
  }

  if (configuration.candidate_network_policy ==
      kCandidateNetworkPolicyLowCost) {
    port_allocator_flags |= cricket::PORTALLOCATOR_DISABLE_COSTLY_NETWORKS;
    RTC_LOG(LS_INFO) << "Do not gather candidates on high-cost networks";
  }

  if (configuration.disable_link_local_networks) {
    port_allocator_flags |= cricket::PORTALLOCATOR_DISABLE_LINK_LOCAL_NETWORKS;
    RTC_LOG(LS_INFO) << "Disable candidates on link-local network interfaces.";
  }

  port_allocator_->set_flags(port_allocator_flags);
  // No step delay is used while allocating ports.
  port_allocator_->set_step_delay(cricket::kMinimumStepDelay);
  port_allocator_->SetCandidateFilter(
      ConvertIceTransportTypeToCandidateFilter(configuration.type));
  port_allocator_->set_max_ipv6_networks(configuration.max_ipv6_networks);

  auto turn_servers_copy = turn_servers;
  for (auto& turn_server : turn_servers_copy) {
    turn_server.tls_cert_verifier = tls_cert_verifier_.get();
  }
  // Call this last since it may create pooled allocator sessions using the
  // properties set above.
  port_allocator_->SetConfiguration(
      stun_servers, std::move(turn_servers_copy),
      configuration.ice_candidate_pool_size,
      configuration.GetTurnPortPrunePolicy(), configuration.turn_customizer,
      configuration.stun_candidate_keepalive_interval);

  InitializePortAllocatorResult res;
  res.enable_ipv6 = port_allocator_flags & cricket::PORTALLOCATOR_ENABLE_IPV6;
  return res;
}

bool PeerConnection::ReconfigurePortAllocator_n(
    const cricket::ServerAddresses& stun_servers,
    const std::vector<cricket::RelayServerConfig>& turn_servers,
    IceTransportsType type,
    int candidate_pool_size,
    PortPrunePolicy turn_port_prune_policy,
    webrtc::TurnCustomizer* turn_customizer,
    absl::optional<int> stun_candidate_keepalive_interval,
    bool have_local_description) {
  RTC_DCHECK_RUN_ON(network_thread());
  port_allocator_->SetCandidateFilter(
      ConvertIceTransportTypeToCandidateFilter(type));
  // According to JSEP, after setLocalDescription, changing the candidate pool
  // size is not allowed, and changing the set of ICE servers will not result
  // in new candidates being gathered.
  if (have_local_description) {
    port_allocator_->FreezeCandidatePool();
  }
  // Add the custom tls turn servers if they exist.
  auto turn_servers_copy = turn_servers;
  for (auto& turn_server : turn_servers_copy) {
    turn_server.tls_cert_verifier = tls_cert_verifier_.get();
  }
  // Call this last since it may create pooled allocator sessions using the
  // candidate filter set above.
  return port_allocator_->SetConfiguration(
      stun_servers, std::move(turn_servers_copy), candidate_pool_size,
      turn_port_prune_policy, turn_customizer,
      stun_candidate_keepalive_interval);
}

bool PeerConnection::StartRtcEventLog_w(
    std::unique_ptr<RtcEventLogOutput> output,
    int64_t output_period_ms) {
  RTC_DCHECK_RUN_ON(worker_thread());
  if (!event_log_) {
    return false;
  }
  return event_log_->StartLogging(std::move(output), output_period_ms);
}

void PeerConnection::StopRtcEventLog_w() {
  RTC_DCHECK_RUN_ON(worker_thread());
  if (event_log_) {
    event_log_->StopLogging();
  }
}

cricket::ChannelInterface* PeerConnection::GetChannel(const std::string& mid) {
  for (const auto& transceiver : rtp_manager()->transceivers()->UnsafeList()) {
    cricket::ChannelInterface* channel = transceiver->internal()->channel();
    if (channel && channel->mid() == mid) {
      return channel;
    }
  }
  return nullptr;
}

bool PeerConnection::GetSctpSslRole(rtc::SSLRole* role) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!local_description() || !remote_description()) {
    RTC_LOG(LS_VERBOSE)
        << "Local and Remote descriptions must be applied to get the "
           "SSL Role of the SCTP transport.";
    return false;
  }
  if (!data_channel_controller_.data_channel_transport()) {
    RTC_LOG(LS_INFO) << "Non-rejected SCTP m= section is needed to get the "
                        "SSL Role of the SCTP transport.";
    return false;
  }

  absl::optional<rtc::SSLRole> dtls_role;
  if (sctp_mid_s_) {
    dtls_role = network_thread()->Invoke<absl::optional<rtc::SSLRole>>(
        RTC_FROM_HERE, [this] {
          RTC_DCHECK_RUN_ON(network_thread());
          return transport_controller_->GetDtlsRole(*sctp_mid_n_);
        });
    if (!dtls_role && sdp_handler_->is_caller().has_value()) {
      // This works fine if we are the offerer, but can be a mistake if
      // we are the answerer and the remote offer is ACTIVE. In that
      // case, we will guess the role wrong.
      // TODO(bugs.webrtc.org/13668): Check if this actually happens.
      RTC_LOG(LS_ERROR)
          << "Possible risk: DTLS role guesser is active, is_caller is "
          << *sdp_handler_->is_caller();
      dtls_role =
          *sdp_handler_->is_caller() ? rtc::SSL_SERVER : rtc::SSL_CLIENT;
    }
    if (dtls_role) {
      *role = *dtls_role;
      return true;
    }
  }
  return false;
}

bool PeerConnection::GetSslRole(const std::string& content_name,
                                rtc::SSLRole* role) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!local_description() || !remote_description()) {
    RTC_LOG(LS_INFO)
        << "Local and Remote descriptions must be applied to get the "
           "SSL Role of the session.";
    return false;
  }

  auto dtls_role = network_thread()->Invoke<absl::optional<rtc::SSLRole>>(
      RTC_FROM_HERE, [this, content_name]() {
        RTC_DCHECK_RUN_ON(network_thread());
        return transport_controller_->GetDtlsRole(content_name);
      });
  if (dtls_role) {
    *role = *dtls_role;
    return true;
  }
  return false;
}

bool PeerConnection::GetTransportDescription(
    const SessionDescription* description,
    const std::string& content_name,
    cricket::TransportDescription* tdesc) {
  if (!description || !tdesc) {
    return false;
  }
  const TransportInfo* transport_info =
      description->GetTransportInfoByName(content_name);
  if (!transport_info) {
    return false;
  }
  *tdesc = transport_info->description;
  return true;
}

std::vector<DataChannelStats> PeerConnection::GetDataChannelStats() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return data_channel_controller_.GetDataChannelStats();
}

absl::optional<std::string> PeerConnection::sctp_transport_name() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (sctp_mid_s_ && transport_controller_copy_)
    return sctp_transport_name_s_;
  return absl::optional<std::string>();
}

absl::optional<std::string> PeerConnection::sctp_mid() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sctp_mid_s_;
}

cricket::CandidateStatsList PeerConnection::GetPooledCandidateStats() const {
  RTC_DCHECK_RUN_ON(network_thread());
  if (!network_thread_safety_->alive())
    return {};
  cricket::CandidateStatsList candidate_states_list;
  port_allocator_->GetCandidateStatsFromPooledSessions(&candidate_states_list);
  return candidate_states_list;
}

std::map<std::string, cricket::TransportStats>
PeerConnection::GetTransportStatsByNames(
    const std::set<std::string>& transport_names) {
  TRACE_EVENT0("webrtc", "PeerConnection::GetTransportStatsByNames");
  RTC_DCHECK_RUN_ON(network_thread());
  if (!network_thread_safety_->alive())
    return {};

  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  std::map<std::string, cricket::TransportStats> transport_stats_by_name;
  for (const std::string& transport_name : transport_names) {
    cricket::TransportStats transport_stats;
    bool success =
        transport_controller_->GetStats(transport_name, &transport_stats);
    if (success) {
      transport_stats_by_name[transport_name] = std::move(transport_stats);
    } else {
      RTC_LOG(LS_ERROR) << "Failed to get transport stats for transport_name="
                        << transport_name;
    }
  }
  return transport_stats_by_name;
}

bool PeerConnection::GetLocalCertificate(
    const std::string& transport_name,
    rtc::scoped_refptr<rtc::RTCCertificate>* certificate) {
  RTC_DCHECK_RUN_ON(network_thread());
  if (!network_thread_safety_->alive() || !certificate) {
    return false;
  }
  *certificate = transport_controller_->GetLocalCertificate(transport_name);
  return *certificate != nullptr;
}

std::unique_ptr<rtc::SSLCertChain> PeerConnection::GetRemoteSSLCertChain(
    const std::string& transport_name) {
  RTC_DCHECK_RUN_ON(network_thread());
  return transport_controller_->GetRemoteSSLCertChain(transport_name);
}

bool PeerConnection::IceRestartPending(const std::string& content_name) const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->IceRestartPending(content_name);
}

bool PeerConnection::NeedsIceRestart(const std::string& content_name) const {
  return network_thread()->Invoke<bool>(RTC_FROM_HERE, [this, &content_name] {
    RTC_DCHECK_RUN_ON(network_thread());
    return transport_controller_->NeedsIceRestart(content_name);
  });
}

void PeerConnection::OnTransportControllerConnectionState(
    cricket::IceConnectionState state) {
  switch (state) {
    case cricket::kIceConnectionConnecting:
      // If the current state is Connected or Completed, then there were
      // writable channels but now there are not, so the next state must
      // be Disconnected.
      // kIceConnectionConnecting is currently used as the default,
      // un-connected state by the TransportController, so its only use is
      // detecting disconnections.
      if (ice_connection_state_ ==
              PeerConnectionInterface::kIceConnectionConnected ||
          ice_connection_state_ ==
              PeerConnectionInterface::kIceConnectionCompleted) {
        SetIceConnectionState(
            PeerConnectionInterface::kIceConnectionDisconnected);
      }
      break;
    case cricket::kIceConnectionFailed:
      SetIceConnectionState(PeerConnectionInterface::kIceConnectionFailed);
      break;
    case cricket::kIceConnectionConnected:
      RTC_LOG(LS_INFO) << "Changing to ICE connected state because "
                          "all transports are writable.";
      SetIceConnectionState(PeerConnectionInterface::kIceConnectionConnected);
      NoteUsageEvent(UsageEvent::ICE_STATE_CONNECTED);
      break;
    case cricket::kIceConnectionCompleted:
      RTC_LOG(LS_INFO) << "Changing to ICE completed state because "
                          "all transports are complete.";
      if (ice_connection_state_ !=
          PeerConnectionInterface::kIceConnectionConnected) {
        // If jumping directly from "checking" to "connected",
        // signal "connected" first.
        SetIceConnectionState(PeerConnectionInterface::kIceConnectionConnected);
      }
      SetIceConnectionState(PeerConnectionInterface::kIceConnectionCompleted);

      NoteUsageEvent(UsageEvent::ICE_STATE_CONNECTED);
      break;
    default:
      RTC_DCHECK_NOTREACHED();
  }
}

void PeerConnection::OnTransportControllerCandidatesGathered(
    const std::string& transport_name,
    const cricket::Candidates& candidates) {
  // TODO(bugs.webrtc.org/12427): Expect this to come in on the network thread
  // (not signaling as it currently does), handle appropriately.
  int sdp_mline_index;
  if (!GetLocalCandidateMediaIndex(transport_name, &sdp_mline_index)) {
    RTC_LOG(LS_ERROR)
        << "OnTransportControllerCandidatesGathered: content name "
        << transport_name << " not found";
    return;
  }

  for (cricket::Candidates::const_iterator citer = candidates.begin();
       citer != candidates.end(); ++citer) {
    // Use transport_name as the candidate media id.
    std::unique_ptr<JsepIceCandidate> candidate(
        new JsepIceCandidate(transport_name, sdp_mline_index, *citer));
    sdp_handler_->AddLocalIceCandidate(candidate.get());
    OnIceCandidate(std::move(candidate));
  }
}

void PeerConnection::OnTransportControllerCandidateError(
    const cricket::IceCandidateErrorEvent& event) {
  OnIceCandidateError(event.address, event.port, event.url, event.error_code,
                      event.error_text);
}

void PeerConnection::OnTransportControllerCandidatesRemoved(
    const std::vector<cricket::Candidate>& candidates) {
  // Sanity check.
  for (const cricket::Candidate& candidate : candidates) {
    if (candidate.transport_name().empty()) {
      RTC_LOG(LS_ERROR) << "OnTransportControllerCandidatesRemoved: "
                           "empty content name in candidate "
                        << candidate.ToString();
      return;
    }
  }
  sdp_handler_->RemoveLocalIceCandidates(candidates);
  OnIceCandidatesRemoved(candidates);
}

void PeerConnection::OnTransportControllerCandidateChanged(
    const cricket::CandidatePairChangeEvent& event) {
  OnSelectedCandidatePairChanged(event);
}

void PeerConnection::OnTransportControllerDtlsHandshakeError(
    rtc::SSLHandshakeError error) {
  RTC_HISTOGRAM_ENUMERATION(
      "WebRTC.PeerConnection.DtlsHandshakeError", static_cast<int>(error),
      static_cast<int>(rtc::SSLHandshakeError::MAX_VALUE));
}

// Returns the media index for a local ice candidate given the content name.
bool PeerConnection::GetLocalCandidateMediaIndex(
    const std::string& content_name,
    int* sdp_mline_index) {
  if (!local_description() || !sdp_mline_index) {
    return false;
  }

  bool content_found = false;
  const ContentInfos& contents = local_description()->description()->contents();
  for (size_t index = 0; index < contents.size(); ++index) {
    if (contents[index].name == content_name) {
      *sdp_mline_index = static_cast<int>(index);
      content_found = true;
      break;
    }
  }
  return content_found;
}

Call::Stats PeerConnection::GetCallStats() {
  if (!worker_thread()->IsCurrent()) {
    return worker_thread()->Invoke<Call::Stats>(
        RTC_FROM_HERE, [this] { return GetCallStats(); });
  }
  RTC_DCHECK_RUN_ON(worker_thread());
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  if (call_) {
    return call_->GetStats();
  } else {
    return Call::Stats();
  }
}

bool PeerConnection::SetupDataChannelTransport_n(const std::string& mid) {
  DataChannelTransportInterface* transport =
      transport_controller_->GetDataChannelTransport(mid);
  if (!transport) {
    RTC_LOG(LS_ERROR)
        << "Data channel transport is not available for data channels, mid="
        << mid;
    return false;
  }
  RTC_LOG(LS_INFO) << "Setting up data channel transport for mid=" << mid;

  data_channel_controller_.set_data_channel_transport(transport);
  data_channel_controller_.SetupDataChannelTransport_n();
  sctp_mid_n_ = mid;
  cricket::DtlsTransportInternal* dtls_transport =
      transport_controller_->GetDtlsTransport(mid);
  if (dtls_transport) {
    signaling_thread()->PostTask(
        ToQueuedTask(signaling_thread_safety_.flag(),
                     [this, name = dtls_transport->transport_name()] {
                       RTC_DCHECK_RUN_ON(signaling_thread());
                       sctp_transport_name_s_ = std::move(name);
                     }));
  }

  // Note: setting the data sink and checking initial state must be done last,
  // after setting up the data channel.  Setting the data sink may trigger
  // callbacks to PeerConnection which require the transport to be completely
  // set up (eg. OnReadyToSend()).
  transport->SetDataSink(&data_channel_controller_);
  return true;
}

void PeerConnection::TeardownDataChannelTransport_n() {
  if (sctp_mid_n_) {
    // `sctp_mid_` may still be active through an SCTP transport.  If not, unset
    // it.
    RTC_LOG(LS_INFO) << "Tearing down data channel transport for mid="
                     << *sctp_mid_n_;
    sctp_mid_n_.reset();
  }

  data_channel_controller_.TeardownDataChannelTransport_n();
}

// Returns false if bundle is enabled and rtcp_mux is disabled.
bool PeerConnection::ValidateBundleSettings(
    const SessionDescription* desc,
    const std::map<std::string, const cricket::ContentGroup*>&
        bundle_groups_by_mid) {
  if (bundle_groups_by_mid.empty())
    return true;

  const cricket::ContentInfos& contents = desc->contents();
  for (cricket::ContentInfos::const_iterator citer = contents.begin();
       citer != contents.end(); ++citer) {
    const cricket::ContentInfo* content = (&*citer);
    RTC_DCHECK(content != NULL);
    auto it = bundle_groups_by_mid.find(content->name);
    if (it != bundle_groups_by_mid.end() && !content->rejected &&
        content->type == MediaProtocolType::kRtp) {
      if (!HasRtcpMuxEnabled(content))
        return false;
    }
  }
  // RTCP-MUX is enabled in all the contents.
  return true;
}

void PeerConnection::ReportSdpFormatReceived(
    const SessionDescriptionInterface& remote_description) {
  int num_audio_mlines = 0;
  int num_video_mlines = 0;
  int num_audio_tracks = 0;
  int num_video_tracks = 0;
  for (const ContentInfo& content :
       remote_description.description()->contents()) {
    cricket::MediaType media_type = content.media_description()->type();
    int num_tracks = std::max(
        1, static_cast<int>(content.media_description()->streams().size()));
    if (media_type == cricket::MEDIA_TYPE_AUDIO) {
      num_audio_mlines += 1;
      num_audio_tracks += num_tracks;
    } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
      num_video_mlines += 1;
      num_video_tracks += num_tracks;
    }
  }
  SdpFormatReceived format = kSdpFormatReceivedNoTracks;
  if (num_audio_mlines > 1 || num_video_mlines > 1) {
    format = kSdpFormatReceivedComplexUnifiedPlan;
  } else if (num_audio_tracks > 1 || num_video_tracks > 1) {
    format = kSdpFormatReceivedComplexPlanB;
  } else if (num_audio_tracks > 0 || num_video_tracks > 0) {
    format = kSdpFormatReceivedSimple;
  }
  switch (remote_description.GetType()) {
    case SdpType::kOffer:
      // Historically only offers were counted.
      RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.SdpFormatReceived",
                                format, kSdpFormatReceivedMax);
      break;
    case SdpType::kAnswer:
      RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.SdpFormatReceivedAnswer",
                                format, kSdpFormatReceivedMax);
      break;
    default:
      RTC_LOG(LS_ERROR) << "Can not report SdpFormatReceived for "
                        << SdpTypeToString(remote_description.GetType());
      break;
  }
}

void PeerConnection::ReportSdpBundleUsage(
    const SessionDescriptionInterface& remote_description) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  bool using_bundle =
      remote_description.description()->HasGroup(cricket::GROUP_TYPE_BUNDLE);
  int num_audio_mlines = 0;
  int num_video_mlines = 0;
  int num_data_mlines = 0;
  for (const ContentInfo& content :
       remote_description.description()->contents()) {
    cricket::MediaType media_type = content.media_description()->type();
    if (media_type == cricket::MEDIA_TYPE_AUDIO) {
      num_audio_mlines += 1;
    } else if (media_type == cricket::MEDIA_TYPE_VIDEO) {
      num_video_mlines += 1;
    } else if (media_type == cricket::MEDIA_TYPE_DATA) {
      num_data_mlines += 1;
    }
  }
  bool simple = num_audio_mlines <= 1 && num_video_mlines <= 1;
  BundleUsage usage = kBundleUsageMax;
  if (num_audio_mlines == 0 && num_video_mlines == 0) {
    if (num_data_mlines > 0) {
      usage = using_bundle ? kBundleUsageBundleDatachannelOnly
                           : kBundleUsageNoBundleDatachannelOnly;
    } else {
      usage = kBundleUsageEmpty;
    }
  } else if (configuration_.sdp_semantics == SdpSemantics::kPlanB_DEPRECATED) {
    // In plan-b, simple/complex usage will not show up in the number of
    // m-lines or BUNDLE.
    usage = using_bundle ? kBundleUsageBundlePlanB : kBundleUsageNoBundlePlanB;
  } else {
    if (simple) {
      usage =
          using_bundle ? kBundleUsageBundleSimple : kBundleUsageNoBundleSimple;
    } else {
      usage = using_bundle ? kBundleUsageBundleComplex
                           : kBundleUsageNoBundleComplex;
    }
  }
  RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.BundleUsage", usage,
                            kBundleUsageMax);
}

void PeerConnection::ReportIceCandidateCollected(
    const cricket::Candidate& candidate) {
  NoteUsageEvent(UsageEvent::CANDIDATE_COLLECTED);
  if (candidate.address().IsPrivateIP()) {
    NoteUsageEvent(UsageEvent::PRIVATE_CANDIDATE_COLLECTED);
  }
  if (candidate.address().IsUnresolvedIP()) {
    NoteUsageEvent(UsageEvent::MDNS_CANDIDATE_COLLECTED);
  }
  if (candidate.address().family() == AF_INET6) {
    NoteUsageEvent(UsageEvent::IPV6_CANDIDATE_COLLECTED);
  }
}

void PeerConnection::NoteUsageEvent(UsageEvent event) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  usage_pattern_.NoteUsageEvent(event);
}

// Asynchronously adds remote candidates on the network thread.
void PeerConnection::AddRemoteCandidate(const std::string& mid,
                                        const cricket::Candidate& candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  network_thread()->PostTask(ToQueuedTask(
      network_thread_safety_, [this, mid = mid, candidate = candidate] {
        RTC_DCHECK_RUN_ON(network_thread());
        std::vector<cricket::Candidate> candidates = {candidate};
        RTCError error =
            transport_controller_->AddRemoteCandidates(mid, candidates);
        if (error.ok()) {
          signaling_thread()->PostTask(ToQueuedTask(
              signaling_thread_safety_.flag(),
              [this, candidate = std::move(candidate)] {
                ReportRemoteIceCandidateAdded(candidate);
                // Candidates successfully submitted for checking.
                if (ice_connection_state() ==
                        PeerConnectionInterface::kIceConnectionNew ||
                    ice_connection_state() ==
                        PeerConnectionInterface::kIceConnectionDisconnected) {
                  // If state is New, then the session has just gotten its first
                  // remote ICE candidates, so go to Checking. If state is
                  // Disconnected, the session is re-using old candidates or
                  // receiving additional ones, so go to Checking. If state is
                  // Connected, stay Connected.
                  // TODO(bemasc): If state is Connected, and the new candidates
                  // are for a newly added transport, then the state actually
                  // _should_ move to checking.  Add a way to distinguish that
                  // case.
                  SetIceConnectionState(
                      PeerConnectionInterface::kIceConnectionChecking);
                }
                // TODO(bemasc): If state is Completed, go back to Connected.
              }));
        } else {
          RTC_LOG(LS_WARNING) << error.message();
        }
      }));
}

void PeerConnection::ReportUsagePattern() const {
  usage_pattern_.ReportUsagePattern(observer_);
}

void PeerConnection::ReportRemoteIceCandidateAdded(
    const cricket::Candidate& candidate) {
  RTC_DCHECK_RUN_ON(signaling_thread());

  NoteUsageEvent(UsageEvent::REMOTE_CANDIDATE_ADDED);

  if (candidate.address().IsPrivateIP()) {
    NoteUsageEvent(UsageEvent::REMOTE_PRIVATE_CANDIDATE_ADDED);
  }
  if (candidate.address().IsUnresolvedIP()) {
    NoteUsageEvent(UsageEvent::REMOTE_MDNS_CANDIDATE_ADDED);
  }
  if (candidate.address().family() == AF_INET6) {
    NoteUsageEvent(UsageEvent::REMOTE_IPV6_CANDIDATE_ADDED);
  }
}

bool PeerConnection::SrtpRequired() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return (dtls_enabled_ ||
          sdp_handler_->webrtc_session_desc_factory()->SdesPolicy() ==
              cricket::SEC_REQUIRED);
}

void PeerConnection::OnTransportControllerGatheringState(
    cricket::IceGatheringState state) {
  RTC_DCHECK(signaling_thread()->IsCurrent());
  if (state == cricket::kIceGatheringGathering) {
    OnIceGatheringChange(PeerConnectionInterface::kIceGatheringGathering);
  } else if (state == cricket::kIceGatheringComplete) {
    OnIceGatheringChange(PeerConnectionInterface::kIceGatheringComplete);
  } else if (state == cricket::kIceGatheringNew) {
    OnIceGatheringChange(PeerConnectionInterface::kIceGatheringNew);
  } else {
    RTC_LOG(LS_ERROR) << "Unknown state received: " << state;
    RTC_DCHECK_NOTREACHED();
  }
}

// Runs on network_thread().
void PeerConnection::ReportTransportStats() {
  TRACE_EVENT0("webrtc", "PeerConnection::ReportTransportStats");
  rtc::Thread::ScopedDisallowBlockingCalls no_blocking_calls;
  std::map<std::string, std::set<cricket::MediaType>>
      media_types_by_transport_name;
  for (const auto& transceiver : rtp_manager()->transceivers()->UnsafeList()) {
    if (transceiver->internal()->channel()) {
      std::string transport_name(
          transceiver->internal()->channel()->transport_name());
      media_types_by_transport_name[transport_name].insert(
          transceiver->media_type());
    }
  }

  if (sctp_mid_n_) {
    cricket::DtlsTransportInternal* dtls_transport =
        transport_controller_->GetDtlsTransport(*sctp_mid_n_);
    if (dtls_transport) {
      media_types_by_transport_name[dtls_transport->transport_name()].insert(
          cricket::MEDIA_TYPE_DATA);
    }
  }

  for (const auto& entry : media_types_by_transport_name) {
    const std::string& transport_name = entry.first;
    const std::set<cricket::MediaType> media_types = entry.second;
    cricket::TransportStats stats;
    if (transport_controller_->GetStats(transport_name, &stats)) {
      ReportBestConnectionState(stats);
      ReportNegotiatedCiphers(dtls_enabled_, stats, media_types);
    }
  }
}

// Walk through the ConnectionInfos to gather best connection usage
// for IPv4 and IPv6.
// static (no member state required)
void PeerConnection::ReportBestConnectionState(
    const cricket::TransportStats& stats) {
  for (const cricket::TransportChannelStats& channel_stats :
       stats.channel_stats) {
    for (const cricket::ConnectionInfo& connection_info :
         channel_stats.ice_transport_stats.connection_infos) {
      if (!connection_info.best_connection) {
        continue;
      }

      const cricket::Candidate& local = connection_info.local_candidate;
      const cricket::Candidate& remote = connection_info.remote_candidate;

      // Increment the counter for IceCandidatePairType.
      if (local.protocol() == cricket::TCP_PROTOCOL_NAME ||
          (local.type() == RELAY_PORT_TYPE &&
           local.relay_protocol() == cricket::TCP_PROTOCOL_NAME)) {
        RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.CandidatePairType_TCP",
                                  GetIceCandidatePairCounter(local, remote),
                                  kIceCandidatePairMax);
      } else if (local.protocol() == cricket::UDP_PROTOCOL_NAME) {
        RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.CandidatePairType_UDP",
                                  GetIceCandidatePairCounter(local, remote),
                                  kIceCandidatePairMax);
      } else {
        RTC_CHECK_NOTREACHED();
      }

      // Increment the counter for IP type.
      if (local.address().family() == AF_INET) {
        RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.IPMetrics",
                                  kBestConnections_IPv4,
                                  kPeerConnectionAddressFamilyCounter_Max);
      } else if (local.address().family() == AF_INET6) {
        RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.IPMetrics",
                                  kBestConnections_IPv6,
                                  kPeerConnectionAddressFamilyCounter_Max);
      } else {
        RTC_CHECK(!local.address().hostname().empty() &&
                  local.address().IsUnresolvedIP());
      }

      return;
    }
  }
}

// static
void PeerConnection::ReportNegotiatedCiphers(
    bool dtls_enabled,
    const cricket::TransportStats& stats,
    const std::set<cricket::MediaType>& media_types) {
  if (!dtls_enabled || stats.channel_stats.empty()) {
    return;
  }

  int srtp_crypto_suite = stats.channel_stats[0].srtp_crypto_suite;
  int ssl_cipher_suite = stats.channel_stats[0].ssl_cipher_suite;
  if (srtp_crypto_suite == rtc::kSrtpInvalidCryptoSuite &&
      ssl_cipher_suite == rtc::kTlsNullWithNullNull) {
    return;
  }

  if (srtp_crypto_suite != rtc::kSrtpInvalidCryptoSuite) {
    for (cricket::MediaType media_type : media_types) {
      switch (media_type) {
        case cricket::MEDIA_TYPE_AUDIO:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SrtpCryptoSuite.Audio", srtp_crypto_suite,
              rtc::kSrtpCryptoSuiteMaxValue);
          break;
        case cricket::MEDIA_TYPE_VIDEO:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SrtpCryptoSuite.Video", srtp_crypto_suite,
              rtc::kSrtpCryptoSuiteMaxValue);
          break;
        case cricket::MEDIA_TYPE_DATA:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SrtpCryptoSuite.Data", srtp_crypto_suite,
              rtc::kSrtpCryptoSuiteMaxValue);
          break;
        default:
          RTC_DCHECK_NOTREACHED();
          continue;
      }
    }
  }

  if (ssl_cipher_suite != rtc::kTlsNullWithNullNull) {
    for (cricket::MediaType media_type : media_types) {
      switch (media_type) {
        case cricket::MEDIA_TYPE_AUDIO:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SslCipherSuite.Audio", ssl_cipher_suite,
              rtc::kSslCipherSuiteMaxValue);
          break;
        case cricket::MEDIA_TYPE_VIDEO:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SslCipherSuite.Video", ssl_cipher_suite,
              rtc::kSslCipherSuiteMaxValue);
          break;
        case cricket::MEDIA_TYPE_DATA:
          RTC_HISTOGRAM_ENUMERATION_SPARSE(
              "WebRTC.PeerConnection.SslCipherSuite.Data", ssl_cipher_suite,
              rtc::kSslCipherSuiteMaxValue);
          break;
        default:
          RTC_DCHECK_NOTREACHED();
          continue;
      }
    }
  }
}

bool PeerConnection::OnTransportChanged(
    const std::string& mid,
    RtpTransportInternal* rtp_transport,
    rtc::scoped_refptr<DtlsTransport> dtls_transport,
    DataChannelTransportInterface* data_channel_transport) {
  RTC_DCHECK_RUN_ON(network_thread());
  bool ret = true;
  auto base_channel = GetChannel(mid);
  if (base_channel) {
    ret = base_channel->SetRtpTransport(rtp_transport);
  }

  if (mid == sctp_mid_n_) {
    data_channel_controller_.OnTransportChanged(data_channel_transport);
    if (dtls_transport) {
      signaling_thread()->PostTask(ToQueuedTask(
          signaling_thread_safety_.flag(),
          [this,
           name = std::string(dtls_transport->internal()->transport_name())] {
            RTC_DCHECK_RUN_ON(signaling_thread());
            sctp_transport_name_s_ = std::move(name);
          }));
    }
  }

  return ret;
}

PeerConnectionObserver* PeerConnection::Observer() const {
  RTC_DCHECK_RUN_ON(signaling_thread());
  RTC_DCHECK(observer_);
  return observer_;
}

void PeerConnection::StartSctpTransport(int local_port,
                                        int remote_port,
                                        int max_message_size) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (!sctp_mid_s_)
    return;

  network_thread()->PostTask(ToQueuedTask(
      network_thread_safety_,
      [this, mid = *sctp_mid_s_, local_port, remote_port, max_message_size] {
        rtc::scoped_refptr<SctpTransport> sctp_transport =
            transport_controller_n()->GetSctpTransport(mid);
        if (sctp_transport)
          sctp_transport->Start(local_port, remote_port, max_message_size);
      }));
}

CryptoOptions PeerConnection::GetCryptoOptions() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  // TODO(bugs.webrtc.org/9891) - Remove PeerConnectionFactory::CryptoOptions
  // after it has been removed.
  return configuration_.crypto_options.has_value()
             ? *configuration_.crypto_options
             : options_.crypto_options;
}

void PeerConnection::ClearStatsCache() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  if (stats_collector_) {
    stats_collector_->ClearCachedStatsReport();
  }
}

bool PeerConnection::ShouldFireNegotiationNeededEvent(uint32_t event_id) {
  RTC_DCHECK_RUN_ON(signaling_thread());
  return sdp_handler_->ShouldFireNegotiationNeededEvent(event_id);
}

void PeerConnection::RequestUsagePatternReportForTesting() {
  RTC_DCHECK_RUN_ON(signaling_thread());
  message_handler_.RequestUsagePatternReport(
      [this]() {
        RTC_DCHECK_RUN_ON(signaling_thread());
        ReportUsagePattern();
      },
      /* delay_ms= */ 0);
}

std::function<void(const rtc::CopyOnWriteBuffer& packet,
                   int64_t packet_time_us)>
PeerConnection::InitializeRtcpCallback() {
  RTC_DCHECK_RUN_ON(network_thread());
  return [this](const rtc::CopyOnWriteBuffer& packet, int64_t packet_time_us) {
    RTC_DCHECK_RUN_ON(network_thread());
    call_ptr_->Receiver()->DeliverPacket(MediaType::ANY, packet,
                                         packet_time_us);
  };
}

}  // namespace webrtc
