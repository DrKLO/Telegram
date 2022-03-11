/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/client/basic_port_allocator.h"

#include <algorithm>
#include <functional>
#include <memory>
#include <set>
#include <string>
#include <utility>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "p2p/base/port.h"
#include "p2p/base/stun_port.h"
#include "p2p/base/tcp_port.h"
#include "p2p/base/turn_port.h"
#include "p2p/base/udp_port.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"

using rtc::CreateRandomId;

namespace cricket {
namespace {

const int PHASE_UDP = 0;
const int PHASE_RELAY = 1;
const int PHASE_TCP = 2;

const int kNumPhases = 3;

// Gets protocol priority: UDP > TCP > SSLTCP == TLS.
int GetProtocolPriority(cricket::ProtocolType protocol) {
  switch (protocol) {
    case cricket::PROTO_UDP:
      return 2;
    case cricket::PROTO_TCP:
      return 1;
    case cricket::PROTO_SSLTCP:
    case cricket::PROTO_TLS:
      return 0;
    default:
      RTC_DCHECK_NOTREACHED();
      return 0;
  }
}
// Gets address family priority:  IPv6 > IPv4 > Unspecified.
int GetAddressFamilyPriority(int ip_family) {
  switch (ip_family) {
    case AF_INET6:
      return 2;
    case AF_INET:
      return 1;
    default:
      RTC_DCHECK_NOTREACHED();
      return 0;
  }
}

// Returns positive if a is better, negative if b is better, and 0 otherwise.
int ComparePort(const cricket::Port* a, const cricket::Port* b) {
  int a_protocol = GetProtocolPriority(a->GetProtocol());
  int b_protocol = GetProtocolPriority(b->GetProtocol());
  int cmp_protocol = a_protocol - b_protocol;
  if (cmp_protocol != 0) {
    return cmp_protocol;
  }

  int a_family = GetAddressFamilyPriority(a->Network()->GetBestIP().family());
  int b_family = GetAddressFamilyPriority(b->Network()->GetBestIP().family());
  return a_family - b_family;
}

struct NetworkFilter {
  using Predicate = std::function<bool(rtc::Network*)>;
  NetworkFilter(Predicate pred, const std::string& description)
      : predRemain([pred](rtc::Network* network) { return !pred(network); }),
        description(description) {}
  Predicate predRemain;
  const std::string description;
};

using NetworkList = rtc::NetworkManager::NetworkList;
void FilterNetworks(NetworkList* networks, NetworkFilter filter) {
  auto start_to_remove =
      std::partition(networks->begin(), networks->end(), filter.predRemain);
  if (start_to_remove == networks->end()) {
    return;
  }
  RTC_LOG(LS_INFO) << "Filtered out " << filter.description << " networks:";
  for (auto it = start_to_remove; it != networks->end(); ++it) {
    RTC_LOG(LS_INFO) << (*it)->ToString();
  }
  networks->erase(start_to_remove, networks->end());
}

bool IsAllowedByCandidateFilter(const Candidate& c, uint32_t filter) {
  // When binding to any address, before sending packets out, the getsockname
  // returns all 0s, but after sending packets, it'll be the NIC used to
  // send. All 0s is not a valid ICE candidate address and should be filtered
  // out.
  if (c.address().IsAnyIP()) {
    return false;
  }

  if (c.type() == RELAY_PORT_TYPE) {
    return ((filter & CF_RELAY) != 0);
  } else if (c.type() == STUN_PORT_TYPE) {
    return ((filter & CF_REFLEXIVE) != 0);
  } else if (c.type() == LOCAL_PORT_TYPE) {
    if ((filter & CF_REFLEXIVE) && !c.address().IsPrivateIP()) {
      // We allow host candidates if the filter allows server-reflexive
      // candidates and the candidate is a public IP. Because we don't generate
      // server-reflexive candidates if they have the same IP as the host
      // candidate (i.e. when the host candidate is a public IP), filtering to
      // only server-reflexive candidates won't work right when the host
      // candidates have public IPs.
      return true;
    }

    return ((filter & CF_HOST) != 0);
  }
  return false;
}

}  // namespace

const uint32_t DISABLE_ALL_PHASES =
    PORTALLOCATOR_DISABLE_UDP | PORTALLOCATOR_DISABLE_TCP |
    PORTALLOCATOR_DISABLE_STUN | PORTALLOCATOR_DISABLE_RELAY;

// BasicPortAllocator
BasicPortAllocator::BasicPortAllocator(
    rtc::NetworkManager* network_manager,
    rtc::PacketSocketFactory* socket_factory,
    webrtc::TurnCustomizer* customizer,
    RelayPortFactoryInterface* relay_port_factory)
    : network_manager_(network_manager), socket_factory_(socket_factory) {
  InitRelayPortFactory(relay_port_factory);
  RTC_DCHECK(relay_port_factory_ != nullptr);
  RTC_DCHECK(network_manager_ != nullptr);
  RTC_DCHECK(socket_factory_ != nullptr);
  SetConfiguration(ServerAddresses(), std::vector<RelayServerConfig>(), 0,
                   webrtc::NO_PRUNE, customizer);
}

BasicPortAllocator::BasicPortAllocator(rtc::NetworkManager* network_manager)
    : network_manager_(network_manager), socket_factory_(nullptr) {
  InitRelayPortFactory(nullptr);
  RTC_DCHECK(relay_port_factory_ != nullptr);
  RTC_DCHECK(network_manager_ != nullptr);
}

BasicPortAllocator::BasicPortAllocator(rtc::NetworkManager* network_manager,
                                       const ServerAddresses& stun_servers)
    : BasicPortAllocator(network_manager,
                         /*socket_factory=*/nullptr,
                         stun_servers) {}

BasicPortAllocator::BasicPortAllocator(rtc::NetworkManager* network_manager,
                                       rtc::PacketSocketFactory* socket_factory,
                                       const ServerAddresses& stun_servers)
    : network_manager_(network_manager), socket_factory_(socket_factory) {
  InitRelayPortFactory(nullptr);
  RTC_DCHECK(relay_port_factory_ != nullptr);
  RTC_DCHECK(network_manager_ != nullptr);
  SetConfiguration(stun_servers, std::vector<RelayServerConfig>(), 0,
                   webrtc::NO_PRUNE, nullptr);
}

void BasicPortAllocator::OnIceRegathering(PortAllocatorSession* session,
                                          IceRegatheringReason reason) {
  // If the session has not been taken by an active channel, do not report the
  // metric.
  for (auto& allocator_session : pooled_sessions()) {
    if (allocator_session.get() == session) {
      return;
    }
  }

  RTC_HISTOGRAM_ENUMERATION("WebRTC.PeerConnection.IceRegatheringReason",
                            static_cast<int>(reason),
                            static_cast<int>(IceRegatheringReason::MAX_VALUE));
}

BasicPortAllocator::~BasicPortAllocator() {
  CheckRunOnValidThreadIfInitialized();
  // Our created port allocator sessions depend on us, so destroy our remaining
  // pooled sessions before anything else.
  DiscardCandidatePool();
}

void BasicPortAllocator::SetNetworkIgnoreMask(int network_ignore_mask) {
  // TODO(phoglund): implement support for other types than loopback.
  // See https://code.google.com/p/webrtc/issues/detail?id=4288.
  // Then remove set_network_ignore_list from NetworkManager.
  CheckRunOnValidThreadIfInitialized();
  network_ignore_mask_ = network_ignore_mask;
}

int BasicPortAllocator::GetNetworkIgnoreMask() const {
  CheckRunOnValidThreadIfInitialized();
  int mask = network_ignore_mask_;
  switch (vpn_preference_) {
    case webrtc::VpnPreference::kOnlyUseVpn:
      mask |= ~static_cast<int>(rtc::ADAPTER_TYPE_VPN);
      break;
    case webrtc::VpnPreference::kNeverUseVpn:
      mask |= static_cast<int>(rtc::ADAPTER_TYPE_VPN);
      break;
    default:
      break;
  }
  return mask;
}

PortAllocatorSession* BasicPortAllocator::CreateSessionInternal(
    const std::string& content_name,
    int component,
    const std::string& ice_ufrag,
    const std::string& ice_pwd) {
  CheckRunOnValidThreadAndInitialized();
  PortAllocatorSession* session = new BasicPortAllocatorSession(
      this, content_name, component, ice_ufrag, ice_pwd);
  session->SignalIceRegathering.connect(this,
                                        &BasicPortAllocator::OnIceRegathering);
  return session;
}

void BasicPortAllocator::AddTurnServer(const RelayServerConfig& turn_server) {
  CheckRunOnValidThreadAndInitialized();
  std::vector<RelayServerConfig> new_turn_servers = turn_servers();
  new_turn_servers.push_back(turn_server);
  SetConfiguration(stun_servers(), new_turn_servers, candidate_pool_size(),
                   turn_port_prune_policy(), turn_customizer());
}

void BasicPortAllocator::InitRelayPortFactory(
    RelayPortFactoryInterface* relay_port_factory) {
  if (relay_port_factory != nullptr) {
    relay_port_factory_ = relay_port_factory;
  } else {
    default_relay_port_factory_.reset(new TurnPortFactory());
    relay_port_factory_ = default_relay_port_factory_.get();
  }
}

// BasicPortAllocatorSession
BasicPortAllocatorSession::BasicPortAllocatorSession(
    BasicPortAllocator* allocator,
    const std::string& content_name,
    int component,
    const std::string& ice_ufrag,
    const std::string& ice_pwd)
    : PortAllocatorSession(content_name,
                           component,
                           ice_ufrag,
                           ice_pwd,
                           allocator->flags()),
      allocator_(allocator),
      network_thread_(rtc::Thread::Current()),
      socket_factory_(allocator->socket_factory()),
      allocation_started_(false),
      network_manager_started_(false),
      allocation_sequences_created_(false),
      turn_port_prune_policy_(allocator->turn_port_prune_policy()) {
  TRACE_EVENT0("webrtc",
               "BasicPortAllocatorSession::BasicPortAllocatorSession");
  allocator_->network_manager()->SignalNetworksChanged.connect(
      this, &BasicPortAllocatorSession::OnNetworksChanged);
  allocator_->network_manager()->StartUpdating();
}

BasicPortAllocatorSession::~BasicPortAllocatorSession() {
  TRACE_EVENT0("webrtc",
               "BasicPortAllocatorSession::~BasicPortAllocatorSession");
  RTC_DCHECK_RUN_ON(network_thread_);
  allocator_->network_manager()->StopUpdating();

  for (uint32_t i = 0; i < sequences_.size(); ++i) {
    // AllocationSequence should clear it's map entry for turn ports before
    // ports are destroyed.
    sequences_[i]->Clear();
  }

  std::vector<PortData>::iterator it;
  for (it = ports_.begin(); it != ports_.end(); it++)
    delete it->port();

  configs_.clear();

  for (uint32_t i = 0; i < sequences_.size(); ++i)
    delete sequences_[i];
}

BasicPortAllocator* BasicPortAllocatorSession::allocator() {
  RTC_DCHECK_RUN_ON(network_thread_);
  return allocator_;
}

void BasicPortAllocatorSession::SetCandidateFilter(uint32_t filter) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (filter == candidate_filter_) {
    return;
  }
  uint32_t prev_filter = candidate_filter_;
  candidate_filter_ = filter;
  for (PortData& port_data : ports_) {
    if (port_data.error() || port_data.pruned()) {
      continue;
    }
    PortData::State cur_state = port_data.state();
    bool found_signalable_candidate = false;
    bool found_pairable_candidate = false;
    cricket::Port* port = port_data.port();
    for (const auto& c : port->Candidates()) {
      if (!IsStopped() && !IsAllowedByCandidateFilter(c, prev_filter) &&
          IsAllowedByCandidateFilter(c, filter)) {
        // This candidate was not signaled because of not matching the previous
        // filter (see OnCandidateReady below). Let the Port to fire the signal
        // again.
        //
        // Note that
        //  1) we would need the Port to enter the state of in-progress of
        //     gathering to have candidates signaled;
        //
        //  2) firing the signal would also let the session set the port ready
        //     if needed, so that we could form candidate pairs with candidates
        //     from this port;
        //
        //  *  See again OnCandidateReady below for 1) and 2).
        //
        //  3) we only try to resurface candidates if we have not stopped
        //     getting ports, which is always true for the continual gathering.
        if (!found_signalable_candidate) {
          found_signalable_candidate = true;
          port_data.set_state(PortData::STATE_INPROGRESS);
        }
        port->SignalCandidateReady(port, c);
      }

      if (CandidatePairable(c, port)) {
        found_pairable_candidate = true;
      }
    }
    // Restore the previous state.
    port_data.set_state(cur_state);
    // Setting a filter may cause a ready port to become non-ready
    // if it no longer has any pairable candidates.
    //
    // Note that we only set for the negative case here, since a port would be
    // set to have pairable candidates when it signals a ready candidate, which
    // requires the port is still in the progress of gathering/surfacing
    // candidates, and would be done in the firing of the signal above.
    if (!found_pairable_candidate) {
      port_data.set_has_pairable_candidate(false);
    }
  }
}

void BasicPortAllocatorSession::StartGettingPorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  state_ = SessionState::GATHERING;
  if (!socket_factory_) {
    owned_socket_factory_.reset(
        new rtc::BasicPacketSocketFactory(network_thread_->socketserver()));
    socket_factory_ = owned_socket_factory_.get();
  }

  network_thread_->PostTask(webrtc::ToQueuedTask(
      network_safety_, [this] { GetPortConfigurations(); }));

  RTC_LOG(LS_INFO) << "Start getting ports with turn_port_prune_policy "
                   << turn_port_prune_policy_;
}

void BasicPortAllocatorSession::StopGettingPorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  ClearGettingPorts();
  // Note: this must be called after ClearGettingPorts because both may set the
  // session state and we should set the state to STOPPED.
  state_ = SessionState::STOPPED;
}

void BasicPortAllocatorSession::ClearGettingPorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  ++allocation_epoch_;
  for (uint32_t i = 0; i < sequences_.size(); ++i) {
    sequences_[i]->Stop();
  }
  network_thread_->PostTask(
      webrtc::ToQueuedTask(network_safety_, [this] { OnConfigStop(); }));
  state_ = SessionState::CLEARED;
}

bool BasicPortAllocatorSession::IsGettingPorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  return state_ == SessionState::GATHERING;
}

bool BasicPortAllocatorSession::IsCleared() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return state_ == SessionState::CLEARED;
}

bool BasicPortAllocatorSession::IsStopped() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return state_ == SessionState::STOPPED;
}

std::vector<rtc::Network*> BasicPortAllocatorSession::GetFailedNetworks() {
  RTC_DCHECK_RUN_ON(network_thread_);

  std::vector<rtc::Network*> networks = GetNetworks();

  // A network interface may have both IPv4 and IPv6 networks. Only if
  // neither of the networks has any connections, the network interface
  // is considered failed and need to be regathered on.
  std::set<std::string> networks_with_connection;
  for (const PortData& data : ports_) {
    Port* port = data.port();
    if (!port->connections().empty()) {
      networks_with_connection.insert(port->Network()->name());
    }
  }

  networks.erase(
      std::remove_if(networks.begin(), networks.end(),
                     [networks_with_connection](rtc::Network* network) {
                       // If a network does not have any connection, it is
                       // considered failed.
                       return networks_with_connection.find(network->name()) !=
                              networks_with_connection.end();
                     }),
      networks.end());
  return networks;
}

void BasicPortAllocatorSession::RegatherOnFailedNetworks() {
  RTC_DCHECK_RUN_ON(network_thread_);

  // Find the list of networks that have no connection.
  std::vector<rtc::Network*> failed_networks = GetFailedNetworks();
  if (failed_networks.empty()) {
    return;
  }

  RTC_LOG(LS_INFO) << "Regather candidates on failed networks";

  // Mark a sequence as "network failed" if its network is in the list of failed
  // networks, so that it won't be considered as equivalent when the session
  // regathers ports and candidates.
  for (AllocationSequence* sequence : sequences_) {
    if (!sequence->network_failed() &&
        absl::c_linear_search(failed_networks, sequence->network())) {
      sequence->set_network_failed();
    }
  }

  bool disable_equivalent_phases = true;
  Regather(failed_networks, disable_equivalent_phases,
           IceRegatheringReason::NETWORK_FAILURE);
}

void BasicPortAllocatorSession::Regather(
    const std::vector<rtc::Network*>& networks,
    bool disable_equivalent_phases,
    IceRegatheringReason reason) {
  RTC_DCHECK_RUN_ON(network_thread_);
  // Remove ports from being used locally and send signaling to remove
  // the candidates on the remote side.
  std::vector<PortData*> ports_to_prune = GetUnprunedPorts(networks);
  if (!ports_to_prune.empty()) {
    RTC_LOG(LS_INFO) << "Prune " << ports_to_prune.size() << " ports";
    PrunePortsAndRemoveCandidates(ports_to_prune);
  }

  if (allocation_started_ && network_manager_started_ && !IsStopped()) {
    SignalIceRegathering(this, reason);

    DoAllocate(disable_equivalent_phases);
  }
}

void BasicPortAllocatorSession::GetCandidateStatsFromReadyPorts(
    CandidateStatsList* candidate_stats_list) const {
  auto ports = ReadyPorts();
  for (auto* port : ports) {
    auto candidates = port->Candidates();
    for (const auto& candidate : candidates) {
      absl::optional<StunStats> stun_stats;
      port->GetStunStats(&stun_stats);
      CandidateStats candidate_stats(allocator_->SanitizeCandidate(candidate),
                                     std::move(stun_stats));
      candidate_stats_list->push_back(std::move(candidate_stats));
    }
  }
}

void BasicPortAllocatorSession::SetStunKeepaliveIntervalForReadyPorts(
    const absl::optional<int>& stun_keepalive_interval) {
  RTC_DCHECK_RUN_ON(network_thread_);
  auto ports = ReadyPorts();
  for (PortInterface* port : ports) {
    // The port type and protocol can be used to identify different subclasses
    // of Port in the current implementation. Note that a TCPPort has the type
    // LOCAL_PORT_TYPE but uses the protocol PROTO_TCP.
    if (port->Type() == STUN_PORT_TYPE ||
        (port->Type() == LOCAL_PORT_TYPE && port->GetProtocol() == PROTO_UDP)) {
      static_cast<UDPPort*>(port)->set_stun_keepalive_delay(
          stun_keepalive_interval);
    }
  }
}

std::vector<PortInterface*> BasicPortAllocatorSession::ReadyPorts() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<PortInterface*> ret;
  for (const PortData& data : ports_) {
    if (data.ready()) {
      ret.push_back(data.port());
    }
  }
  return ret;
}

std::vector<Candidate> BasicPortAllocatorSession::ReadyCandidates() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<Candidate> candidates;
  for (const PortData& data : ports_) {
    if (!data.ready()) {
      continue;
    }
    GetCandidatesFromPort(data, &candidates);
  }
  return candidates;
}

void BasicPortAllocatorSession::GetCandidatesFromPort(
    const PortData& data,
    std::vector<Candidate>* candidates) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_CHECK(candidates != nullptr);
  for (const Candidate& candidate : data.port()->Candidates()) {
    if (!CheckCandidateFilter(candidate)) {
      continue;
    }
    candidates->push_back(allocator_->SanitizeCandidate(candidate));
  }
}

bool BasicPortAllocator::MdnsObfuscationEnabled() const {
  return network_manager()->GetMdnsResponder() != nullptr;
}

bool BasicPortAllocatorSession::CandidatesAllocationDone() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  // Done only if all required AllocationSequence objects
  // are created.
  if (!allocation_sequences_created_) {
    return false;
  }

  // Check that all port allocation sequences are complete (not running).
  if (absl::c_any_of(sequences_, [](const AllocationSequence* sequence) {
        return sequence->state() == AllocationSequence::kRunning;
      })) {
    return false;
  }

  // If all allocated ports are no longer gathering, session must have got all
  // expected candidates. Session will trigger candidates allocation complete
  // signal.
  return absl::c_none_of(
      ports_, [](const PortData& port) { return port.inprogress(); });
}

void BasicPortAllocatorSession::UpdateIceParametersInternal() {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (PortData& port : ports_) {
    port.port()->set_content_name(content_name());
    port.port()->SetIceParameters(component(), ice_ufrag(), ice_pwd());
  }
}

void BasicPortAllocatorSession::GetPortConfigurations() {
  RTC_DCHECK_RUN_ON(network_thread_);

  auto config = std::make_unique<PortConfiguration>(allocator_->stun_servers(),
                                                    username(), password());

  for (const RelayServerConfig& turn_server : allocator_->turn_servers()) {
    config->AddRelay(turn_server);
  }
  ConfigReady(std::move(config));
}

void BasicPortAllocatorSession::ConfigReady(PortConfiguration* config) {
  RTC_DCHECK_RUN_ON(network_thread_);
  ConfigReady(absl::WrapUnique(config));
}

void BasicPortAllocatorSession::ConfigReady(
    std::unique_ptr<PortConfiguration> config) {
  RTC_DCHECK_RUN_ON(network_thread_);
  network_thread_->PostTask(webrtc::ToQueuedTask(
      network_safety_, [this, config = std::move(config)]() mutable {
        OnConfigReady(std::move(config));
      }));
}

// Adds a configuration to the list.
void BasicPortAllocatorSession::OnConfigReady(
    std::unique_ptr<PortConfiguration> config) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (config)
    configs_.push_back(std::move(config));

  AllocatePorts();
}

void BasicPortAllocatorSession::OnConfigStop() {
  RTC_DCHECK_RUN_ON(network_thread_);

  // If any of the allocated ports have not completed the candidates allocation,
  // mark those as error. Since session doesn't need any new candidates
  // at this stage of the allocation, it's safe to discard any new candidates.
  bool send_signal = false;
  for (std::vector<PortData>::iterator it = ports_.begin(); it != ports_.end();
       ++it) {
    if (it->inprogress()) {
      // Updating port state to error, which didn't finish allocating candidates
      // yet.
      it->set_state(PortData::STATE_ERROR);
      send_signal = true;
    }
  }

  // Did we stop any running sequences?
  for (std::vector<AllocationSequence*>::iterator it = sequences_.begin();
       it != sequences_.end() && !send_signal; ++it) {
    if ((*it)->state() == AllocationSequence::kStopped) {
      send_signal = true;
    }
  }

  // If we stopped anything that was running, send a done signal now.
  if (send_signal) {
    MaybeSignalCandidatesAllocationDone();
  }
}

void BasicPortAllocatorSession::AllocatePorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  network_thread_->PostTask(webrtc::ToQueuedTask(
      network_safety_, [this, allocation_epoch = allocation_epoch_] {
        OnAllocate(allocation_epoch);
      }));
}

void BasicPortAllocatorSession::OnAllocate(int allocation_epoch) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (allocation_epoch != allocation_epoch_)
    return;

  if (network_manager_started_ && !IsStopped()) {
    bool disable_equivalent_phases = true;
    DoAllocate(disable_equivalent_phases);
  }

  allocation_started_ = true;
}

std::vector<rtc::Network*> BasicPortAllocatorSession::GetNetworks() {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<rtc::Network*> networks;
  rtc::NetworkManager* network_manager = allocator_->network_manager();
  RTC_DCHECK(network_manager != nullptr);
  // If the network permission state is BLOCKED, we just act as if the flag has
  // been passed in.
  if (network_manager->enumeration_permission() ==
      rtc::NetworkManager::ENUMERATION_BLOCKED) {
    set_flags(flags() | PORTALLOCATOR_DISABLE_ADAPTER_ENUMERATION);
  }
  // If the adapter enumeration is disabled, we'll just bind to any address
  // instead of specific NIC. This is to ensure the same routing for http
  // traffic by OS is also used here to avoid any local or public IP leakage
  // during stun process.
  if (flags() & PORTALLOCATOR_DISABLE_ADAPTER_ENUMERATION) {
    network_manager->GetAnyAddressNetworks(&networks);
  } else {
    network_manager->GetNetworks(&networks);
    // If network enumeration fails, use the ANY address as a fallback, so we
    // can at least try gathering candidates using the default route chosen by
    // the OS. Or, if the PORTALLOCATOR_ENABLE_ANY_ADDRESS_PORTS flag is
    // set, we'll use ANY address candidates either way.
    if (networks.empty() || flags() & PORTALLOCATOR_ENABLE_ANY_ADDRESS_PORTS) {
      network_manager->GetAnyAddressNetworks(&networks);
    }
  }
  // Filter out link-local networks if needed.
  if (flags() & PORTALLOCATOR_DISABLE_LINK_LOCAL_NETWORKS) {
    NetworkFilter link_local_filter(
        [](rtc::Network* network) { return IPIsLinkLocal(network->prefix()); },
        "link-local");
    FilterNetworks(&networks, link_local_filter);
  }
  // Do some more filtering, depending on the network ignore mask and "disable
  // costly networks" flag.
  NetworkFilter ignored_filter(
      [this](rtc::Network* network) {
        return allocator_->GetNetworkIgnoreMask() & network->type();
      },
      "ignored");
  FilterNetworks(&networks, ignored_filter);
  if (flags() & PORTALLOCATOR_DISABLE_COSTLY_NETWORKS) {
    uint16_t lowest_cost = rtc::kNetworkCostMax;
    for (rtc::Network* network : networks) {
      // Don't determine the lowest cost from a link-local network.
      // On iOS, a device connected to the computer will get a link-local
      // network for communicating with the computer, however this network can't
      // be used to connect to a peer outside the network.
      if (rtc::IPIsLinkLocal(network->GetBestIP())) {
        continue;
      }
      lowest_cost = std::min<uint16_t>(lowest_cost, network->GetCost());
    }
    NetworkFilter costly_filter(
        [lowest_cost](rtc::Network* network) {
          return network->GetCost() > lowest_cost + rtc::kNetworkCostLow;
        },
        "costly");
    FilterNetworks(&networks, costly_filter);
  }

  // Lastly, if we have a limit for the number of IPv6 network interfaces (by
  // default, it's 5), remove networks to ensure that limit is satisfied.
  //
  // TODO(deadbeef): Instead of just taking the first N arbitrary IPv6
  // networks, we could try to choose a set that's "most likely to work". It's
  // hard to define what that means though; it's not just "lowest cost".
  // Alternatively, we could just focus on making our ICE pinging logic smarter
  // such that this filtering isn't necessary in the first place.
  int ipv6_networks = 0;
  for (auto it = networks.begin(); it != networks.end();) {
    if ((*it)->prefix().family() == AF_INET6) {
      if (ipv6_networks >= allocator_->max_ipv6_networks()) {
        it = networks.erase(it);
        continue;
      } else {
        ++ipv6_networks;
      }
    }
    ++it;
  }
  return networks;
}

// For each network, see if we have a sequence that covers it already.  If not,
// create a new sequence to create the appropriate ports.
void BasicPortAllocatorSession::DoAllocate(bool disable_equivalent) {
  RTC_DCHECK_RUN_ON(network_thread_);
  bool done_signal_needed = false;
  std::vector<rtc::Network*> networks = GetNetworks();
  if (networks.empty()) {
    RTC_LOG(LS_WARNING)
        << "Machine has no networks; no ports will be allocated";
    done_signal_needed = true;
  } else {
    RTC_LOG(LS_INFO) << "Allocate ports on " << networks.size() << " networks";
    PortConfiguration* config =
        configs_.empty() ? nullptr : configs_.back().get();
    for (uint32_t i = 0; i < networks.size(); ++i) {
      uint32_t sequence_flags = flags();
      if ((sequence_flags & DISABLE_ALL_PHASES) == DISABLE_ALL_PHASES) {
        // If all the ports are disabled we should just fire the allocation
        // done event and return.
        done_signal_needed = true;
        break;
      }

      if (!config || config->relays.empty()) {
        // No relay ports specified in this config.
        sequence_flags |= PORTALLOCATOR_DISABLE_RELAY;
      }

      if (!(sequence_flags & PORTALLOCATOR_ENABLE_IPV6) &&
          networks[i]->GetBestIP().family() == AF_INET6) {
        // Skip IPv6 networks unless the flag's been set.
        continue;
      }

      if (!(sequence_flags & PORTALLOCATOR_ENABLE_IPV6_ON_WIFI) &&
          networks[i]->GetBestIP().family() == AF_INET6 &&
          networks[i]->type() == rtc::ADAPTER_TYPE_WIFI) {
        // Skip IPv6 Wi-Fi networks unless the flag's been set.
        continue;
      }

      if (disable_equivalent) {
        // Disable phases that would only create ports equivalent to
        // ones that we have already made.
        DisableEquivalentPhases(networks[i], config, &sequence_flags);

        if ((sequence_flags & DISABLE_ALL_PHASES) == DISABLE_ALL_PHASES) {
          // New AllocationSequence would have nothing to do, so don't make it.
          continue;
        }
      }

      AllocationSequence* sequence =
          new AllocationSequence(this, networks[i], config, sequence_flags,
                                 [this, safety_flag = network_safety_.flag()] {
                                   if (safety_flag->alive())
                                     OnPortAllocationComplete();
                                 });
      sequence->Init();
      sequence->Start();
      sequences_.push_back(sequence);
      done_signal_needed = true;
    }
  }
  if (done_signal_needed) {
    network_thread_->PostTask(webrtc::ToQueuedTask(
        network_safety_, [this] { OnAllocationSequenceObjectsCreated(); }));
  }
}

void BasicPortAllocatorSession::OnNetworksChanged() {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<rtc::Network*> networks = GetNetworks();
  std::vector<rtc::Network*> failed_networks;
  for (AllocationSequence* sequence : sequences_) {
    // Mark the sequence as "network failed" if its network is not in
    // `networks`.
    if (!sequence->network_failed() &&
        !absl::c_linear_search(networks, sequence->network())) {
      sequence->OnNetworkFailed();
      failed_networks.push_back(sequence->network());
    }
  }
  std::vector<PortData*> ports_to_prune = GetUnprunedPorts(failed_networks);
  if (!ports_to_prune.empty()) {
    RTC_LOG(LS_INFO) << "Prune " << ports_to_prune.size()
                     << " ports because their networks were gone";
    PrunePortsAndRemoveCandidates(ports_to_prune);
  }

  if (allocation_started_ && !IsStopped()) {
    if (network_manager_started_) {
      // If the network manager has started, it must be regathering.
      SignalIceRegathering(this, IceRegatheringReason::NETWORK_CHANGE);
    }
    bool disable_equivalent_phases = true;
    DoAllocate(disable_equivalent_phases);
  }

  if (!network_manager_started_) {
    RTC_LOG(LS_INFO) << "Network manager has started";
    network_manager_started_ = true;
  }
}

void BasicPortAllocatorSession::DisableEquivalentPhases(
    rtc::Network* network,
    PortConfiguration* config,
    uint32_t* flags) {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (uint32_t i = 0; i < sequences_.size() &&
                       (*flags & DISABLE_ALL_PHASES) != DISABLE_ALL_PHASES;
       ++i) {
    sequences_[i]->DisableEquivalentPhases(network, config, flags);
  }
}

void BasicPortAllocatorSession::AddAllocatedPort(Port* port,
                                                 AllocationSequence* seq) {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (!port)
    return;

  RTC_LOG(LS_INFO) << "Adding allocated port for " << content_name();
  port->set_content_name(content_name());
  port->set_component(component());
  port->set_generation(generation());
  if (allocator_->proxy().type != rtc::PROXY_NONE)
    port->set_proxy(allocator_->user_agent(), allocator_->proxy());
  port->set_send_retransmit_count_attribute(
      (flags() & PORTALLOCATOR_ENABLE_STUN_RETRANSMIT_ATTRIBUTE) != 0);

  PortData data(port, seq);
  ports_.push_back(data);

  port->SignalCandidateReady.connect(
      this, &BasicPortAllocatorSession::OnCandidateReady);
  port->SignalCandidateError.connect(
      this, &BasicPortAllocatorSession::OnCandidateError);
  port->SignalPortComplete.connect(this,
                                   &BasicPortAllocatorSession::OnPortComplete);
  port->SubscribePortDestroyed(
      [this](PortInterface* port) { OnPortDestroyed(port); });

  port->SignalPortError.connect(this, &BasicPortAllocatorSession::OnPortError);
  RTC_LOG(LS_INFO) << port->ToString() << ": Added port to allocator";

  port->PrepareAddress();
}

void BasicPortAllocatorSession::OnAllocationSequenceObjectsCreated() {
  RTC_DCHECK_RUN_ON(network_thread_);
  allocation_sequences_created_ = true;
  // Send candidate allocation complete signal if we have no sequences.
  MaybeSignalCandidatesAllocationDone();
}

void BasicPortAllocatorSession::OnCandidateReady(Port* port,
                                                 const Candidate& c) {
  RTC_DCHECK_RUN_ON(network_thread_);
  PortData* data = FindPort(port);
  RTC_DCHECK(data != NULL);
  RTC_LOG(LS_INFO) << port->ToString()
                   << ": Gathered candidate: " << c.ToSensitiveString();
  // Discarding any candidate signal if port allocation status is
  // already done with gathering.
  if (!data->inprogress()) {
    RTC_LOG(LS_WARNING)
        << "Discarding candidate because port is already done gathering.";
    return;
  }

  // Mark that the port has a pairable candidate, either because we have a
  // usable candidate from the port, or simply because the port is bound to the
  // any address and therefore has no host candidate. This will trigger the port
  // to start creating candidate pairs (connections) and issue connectivity
  // checks. If port has already been marked as having a pairable candidate,
  // do nothing here.
  // Note: We should check whether any candidates may become ready after this
  // because there we will check whether the candidate is generated by the ready
  // ports, which may include this port.
  bool pruned = false;
  if (CandidatePairable(c, port) && !data->has_pairable_candidate()) {
    data->set_has_pairable_candidate(true);

    if (port->Type() == RELAY_PORT_TYPE) {
      if (turn_port_prune_policy_ == webrtc::KEEP_FIRST_READY) {
        pruned = PruneNewlyPairableTurnPort(data);
      } else if (turn_port_prune_policy_ == webrtc::PRUNE_BASED_ON_PRIORITY) {
        pruned = PruneTurnPorts(port);
      }
    }

    // If the current port is not pruned yet, SignalPortReady.
    if (!data->pruned()) {
      RTC_LOG(LS_INFO) << port->ToString() << ": Port ready.";
      SignalPortReady(this, port);
      port->KeepAliveUntilPruned();
    }
  }

  if (data->ready() && CheckCandidateFilter(c)) {
    std::vector<Candidate> candidates;
    candidates.push_back(allocator_->SanitizeCandidate(c));
    SignalCandidatesReady(this, candidates);
  } else {
    RTC_LOG(LS_INFO) << "Discarding candidate because it doesn't match filter.";
  }

  // If we have pruned any port, maybe need to signal port allocation done.
  if (pruned) {
    MaybeSignalCandidatesAllocationDone();
  }
}

void BasicPortAllocatorSession::OnCandidateError(
    Port* port,
    const IceCandidateErrorEvent& event) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(FindPort(port));
  if (event.address.empty()) {
    candidate_error_events_.push_back(event);
  } else {
    SignalCandidateError(this, event);
  }
}

Port* BasicPortAllocatorSession::GetBestTurnPortForNetwork(
    const std::string& network_name) const {
  RTC_DCHECK_RUN_ON(network_thread_);
  Port* best_turn_port = nullptr;
  for (const PortData& data : ports_) {
    if (data.port()->Network()->name() == network_name &&
        data.port()->Type() == RELAY_PORT_TYPE && data.ready() &&
        (!best_turn_port || ComparePort(data.port(), best_turn_port) > 0)) {
      best_turn_port = data.port();
    }
  }
  return best_turn_port;
}

bool BasicPortAllocatorSession::PruneNewlyPairableTurnPort(
    PortData* newly_pairable_port_data) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(newly_pairable_port_data->port()->Type() == RELAY_PORT_TYPE);
  // If an existing turn port is ready on the same network, prune the newly
  // pairable port.
  const std::string& network_name =
      newly_pairable_port_data->port()->Network()->name();

  for (PortData& data : ports_) {
    if (data.port()->Network()->name() == network_name &&
        data.port()->Type() == RELAY_PORT_TYPE && data.ready() &&
        &data != newly_pairable_port_data) {
      RTC_LOG(LS_INFO) << "Port pruned: "
                       << newly_pairable_port_data->port()->ToString();
      newly_pairable_port_data->Prune();
      return true;
    }
  }
  return false;
}

bool BasicPortAllocatorSession::PruneTurnPorts(Port* newly_pairable_turn_port) {
  RTC_DCHECK_RUN_ON(network_thread_);
  // Note: We determine the same network based only on their network names. So
  // if an IPv4 address and an IPv6 address have the same network name, they
  // are considered the same network here.
  const std::string& network_name = newly_pairable_turn_port->Network()->name();
  Port* best_turn_port = GetBestTurnPortForNetwork(network_name);
  // `port` is already in the list of ports, so the best port cannot be nullptr.
  RTC_CHECK(best_turn_port != nullptr);

  bool pruned = false;
  std::vector<PortData*> ports_to_prune;
  for (PortData& data : ports_) {
    if (data.port()->Network()->name() == network_name &&
        data.port()->Type() == RELAY_PORT_TYPE && !data.pruned() &&
        ComparePort(data.port(), best_turn_port) < 0) {
      pruned = true;
      if (data.port() != newly_pairable_turn_port) {
        // These ports will be pruned in PrunePortsAndRemoveCandidates.
        ports_to_prune.push_back(&data);
      } else {
        data.Prune();
      }
    }
  }

  if (!ports_to_prune.empty()) {
    RTC_LOG(LS_INFO) << "Prune " << ports_to_prune.size()
                     << " low-priority TURN ports";
    PrunePortsAndRemoveCandidates(ports_to_prune);
  }
  return pruned;
}

void BasicPortAllocatorSession::PruneAllPorts() {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (PortData& data : ports_) {
    data.Prune();
  }
}

void BasicPortAllocatorSession::OnPortComplete(Port* port) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_LOG(LS_INFO) << port->ToString()
                   << ": Port completed gathering candidates.";
  PortData* data = FindPort(port);
  RTC_DCHECK(data != NULL);

  // Ignore any late signals.
  if (!data->inprogress()) {
    return;
  }

  // Moving to COMPLETE state.
  data->set_state(PortData::STATE_COMPLETE);
  // Send candidate allocation complete signal if this was the last port.
  MaybeSignalCandidatesAllocationDone();
}

void BasicPortAllocatorSession::OnPortError(Port* port) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_LOG(LS_INFO) << port->ToString()
                   << ": Port encountered error while gathering candidates.";
  PortData* data = FindPort(port);
  RTC_DCHECK(data != NULL);
  // We might have already given up on this port and stopped it.
  if (!data->inprogress()) {
    return;
  }

  // SignalAddressError is currently sent from StunPort/TurnPort.
  // But this signal itself is generic.
  data->set_state(PortData::STATE_ERROR);
  // Send candidate allocation complete signal if this was the last port.
  MaybeSignalCandidatesAllocationDone();
}

bool BasicPortAllocatorSession::CheckCandidateFilter(const Candidate& c) const {
  RTC_DCHECK_RUN_ON(network_thread_);

  return IsAllowedByCandidateFilter(c, candidate_filter_);
}

bool BasicPortAllocatorSession::CandidatePairable(const Candidate& c,
                                                  const Port* port) const {
  RTC_DCHECK_RUN_ON(network_thread_);

  bool candidate_signalable = CheckCandidateFilter(c);

  // When device enumeration is disabled (to prevent non-default IP addresses
  // from leaking), we ping from some local candidates even though we don't
  // signal them. However, if host candidates are also disabled (for example, to
  // prevent even default IP addresses from leaking), we still don't want to
  // ping from them, even if device enumeration is disabled.  Thus, we check for
  // both device enumeration and host candidates being disabled.
  bool network_enumeration_disabled = c.address().IsAnyIP();
  bool can_ping_from_candidate =
      (port->SharedSocket() || c.protocol() == TCP_PROTOCOL_NAME);
  bool host_candidates_disabled = !(candidate_filter_ & CF_HOST);

  return candidate_signalable ||
         (network_enumeration_disabled && can_ping_from_candidate &&
          !host_candidates_disabled);
}

void BasicPortAllocatorSession::OnPortAllocationComplete() {
  RTC_DCHECK_RUN_ON(network_thread_);
  // Send candidate allocation complete signal if all ports are done.
  MaybeSignalCandidatesAllocationDone();
}

void BasicPortAllocatorSession::MaybeSignalCandidatesAllocationDone() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (CandidatesAllocationDone()) {
    if (pooled()) {
      RTC_LOG(LS_INFO) << "All candidates gathered for pooled session.";
    } else {
      RTC_LOG(LS_INFO) << "All candidates gathered for " << content_name()
                       << ":" << component() << ":" << generation();
    }
    for (const auto& event : candidate_error_events_) {
      SignalCandidateError(this, event);
    }
    candidate_error_events_.clear();
    SignalCandidatesAllocationDone(this);
  }
}

void BasicPortAllocatorSession::OnPortDestroyed(PortInterface* port) {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (std::vector<PortData>::iterator iter = ports_.begin();
       iter != ports_.end(); ++iter) {
    if (port == iter->port()) {
      ports_.erase(iter);
      RTC_LOG(LS_INFO) << port->ToString() << ": Removed port from allocator ("
                       << static_cast<int>(ports_.size()) << " remaining)";
      return;
    }
  }
  RTC_DCHECK_NOTREACHED();
}

BasicPortAllocatorSession::PortData* BasicPortAllocatorSession::FindPort(
    Port* port) {
  RTC_DCHECK_RUN_ON(network_thread_);
  for (std::vector<PortData>::iterator it = ports_.begin(); it != ports_.end();
       ++it) {
    if (it->port() == port) {
      return &*it;
    }
  }
  return NULL;
}

std::vector<BasicPortAllocatorSession::PortData*>
BasicPortAllocatorSession::GetUnprunedPorts(
    const std::vector<rtc::Network*>& networks) {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<PortData*> unpruned_ports;
  for (PortData& port : ports_) {
    if (!port.pruned() &&
        absl::c_linear_search(networks, port.sequence()->network())) {
      unpruned_ports.push_back(&port);
    }
  }
  return unpruned_ports;
}

void BasicPortAllocatorSession::PrunePortsAndRemoveCandidates(
    const std::vector<PortData*>& port_data_list) {
  RTC_DCHECK_RUN_ON(network_thread_);
  std::vector<PortInterface*> pruned_ports;
  std::vector<Candidate> removed_candidates;
  for (PortData* data : port_data_list) {
    // Prune the port so that it may be destroyed.
    data->Prune();
    pruned_ports.push_back(data->port());
    if (data->has_pairable_candidate()) {
      GetCandidatesFromPort(*data, &removed_candidates);
      // Mark the port as having no pairable candidates so that its candidates
      // won't be removed multiple times.
      data->set_has_pairable_candidate(false);
    }
  }
  if (!pruned_ports.empty()) {
    SignalPortsPruned(this, pruned_ports);
  }
  if (!removed_candidates.empty()) {
    RTC_LOG(LS_INFO) << "Removed " << removed_candidates.size()
                     << " candidates";
    SignalCandidatesRemoved(this, removed_candidates);
  }
}

void BasicPortAllocator::SetVpnList(
    const std::vector<rtc::NetworkMask>& vpn_list) {
  network_manager_->set_vpn_list(vpn_list);
}

// AllocationSequence

AllocationSequence::AllocationSequence(
    BasicPortAllocatorSession* session,
    rtc::Network* network,
    PortConfiguration* config,
    uint32_t flags,
    std::function<void()> port_allocation_complete_callback)
    : session_(session),
      network_(network),
      config_(config),
      state_(kInit),
      flags_(flags),
      udp_socket_(),
      udp_port_(NULL),
      phase_(0),
      port_allocation_complete_callback_(
          std::move(port_allocation_complete_callback)) {}

void AllocationSequence::Init() {
  if (IsFlagSet(PORTALLOCATOR_ENABLE_SHARED_SOCKET)) {
    udp_socket_.reset(session_->socket_factory()->CreateUdpSocket(
        rtc::SocketAddress(network_->GetBestIP(), 0),
        session_->allocator()->min_port(), session_->allocator()->max_port()));
    if (udp_socket_) {
      udp_socket_->SignalReadPacket.connect(this,
                                            &AllocationSequence::OnReadPacket);
    }
    // Continuing if `udp_socket_` is NULL, as local TCP and RelayPort using TCP
    // are next available options to setup a communication channel.
  }
}

void AllocationSequence::Clear() {
  TRACE_EVENT0("webrtc", "AllocationSequence::Clear");
  udp_port_ = NULL;
  relay_ports_.clear();
}

void AllocationSequence::OnNetworkFailed() {
  RTC_DCHECK(!network_failed_);
  network_failed_ = true;
  // Stop the allocation sequence if its network failed.
  Stop();
}

void AllocationSequence::DisableEquivalentPhases(rtc::Network* network,
                                                 PortConfiguration* config,
                                                 uint32_t* flags) {
  if (network_failed_) {
    // If the network of this allocation sequence has ever become failed,
    // it won't be equivalent to the new network.
    return;
  }

  if (!((network == network_) && (previous_best_ip_ == network->GetBestIP()))) {
    // Different network setup; nothing is equivalent.
    return;
  }

  // Else turn off the stuff that we've already got covered.

  // Every config implicitly specifies local, so turn that off right away if we
  // already have a port of the corresponding type. Look for a port that
  // matches this AllocationSequence's network, is the right protocol, and
  // hasn't encountered an error.
  // TODO(deadbeef): This doesn't take into account that there may be another
  // AllocationSequence that's ABOUT to allocate a UDP port, but hasn't yet.
  // This can happen if, say, there's a network change event right before an
  // application-triggered ICE restart. Hopefully this problem will just go
  // away if we get rid of the gathering "phases" though, which is planned.
  //
  //
  // PORTALLOCATOR_DISABLE_UDP is used to disable a Port from gathering the host
  // candidate (and srflx candidate if Port::SharedSocket()), and we do not want
  // to disable the gathering of these candidates just becaue of an existing
  // Port over PROTO_UDP, namely a TurnPort over UDP.
  if (absl::c_any_of(session_->ports_,
                     [this](const BasicPortAllocatorSession::PortData& p) {
                       return !p.pruned() && p.port()->Network() == network_ &&
                              p.port()->GetProtocol() == PROTO_UDP &&
                              p.port()->Type() == LOCAL_PORT_TYPE && !p.error();
                     })) {
    *flags |= PORTALLOCATOR_DISABLE_UDP;
  }
  // Similarly we need to check both the protocol used by an existing Port and
  // its type.
  if (absl::c_any_of(session_->ports_,
                     [this](const BasicPortAllocatorSession::PortData& p) {
                       return !p.pruned() && p.port()->Network() == network_ &&
                              p.port()->GetProtocol() == PROTO_TCP &&
                              p.port()->Type() == LOCAL_PORT_TYPE && !p.error();
                     })) {
    *flags |= PORTALLOCATOR_DISABLE_TCP;
  }

  if (config_ && config) {
    // We need to regather srflx candidates if either of the following
    // conditions occurs:
    //  1. The STUN servers are different from the previous gathering.
    //  2. We will regather host candidates, hence possibly inducing new NAT
    //     bindings.
    if (config_->StunServers() == config->StunServers() &&
        (*flags & PORTALLOCATOR_DISABLE_UDP)) {
      // Already got this STUN servers covered.
      *flags |= PORTALLOCATOR_DISABLE_STUN;
    }
    if (!config_->relays.empty()) {
      // Already got relays covered.
      // NOTE: This will even skip a _different_ set of relay servers if we
      // were to be given one, but that never happens in our codebase. Should
      // probably get rid of the list in PortConfiguration and just keep a
      // single relay server in each one.
      *flags |= PORTALLOCATOR_DISABLE_RELAY;
    }
  }
}

void AllocationSequence::Start() {
  state_ = kRunning;

  session_->network_thread()->PostTask(webrtc::ToQueuedTask(
      safety_, [this, epoch = epoch_] { Process(epoch); }));
  // Take a snapshot of the best IP, so that when DisableEquivalentPhases is
  // called next time, we enable all phases if the best IP has since changed.
  previous_best_ip_ = network_->GetBestIP();
}

void AllocationSequence::Stop() {
  // If the port is completed, don't set it to stopped.
  if (state_ == kRunning) {
    state_ = kStopped;
    // Cause further Process calls in the previous epoch to be ignored.
    ++epoch_;
  }
}

void AllocationSequence::Process(int epoch) {
  RTC_DCHECK(rtc::Thread::Current() == session_->network_thread());
  const char* const PHASE_NAMES[kNumPhases] = {"Udp", "Relay", "Tcp"};

  if (epoch != epoch_)
    return;

  // Perform all of the phases in the current step.
  RTC_LOG(LS_INFO) << network_->ToString()
                   << ": Allocation Phase=" << PHASE_NAMES[phase_];

  switch (phase_) {
    case PHASE_UDP:
      CreateUDPPorts();
      CreateStunPorts();
      break;

    case PHASE_RELAY:
      CreateRelayPorts();
      break;

    case PHASE_TCP:
      CreateTCPPorts();
      state_ = kCompleted;
      break;

    default:
      RTC_DCHECK_NOTREACHED();
  }

  if (state() == kRunning) {
    ++phase_;
    session_->network_thread()->PostDelayedTask(
        webrtc::ToQueuedTask(safety_,
                             [this, epoch = epoch_] { Process(epoch); }),
        session_->allocator()->step_delay());
  } else {
    // No allocation steps needed further if all phases in AllocationSequence
    // are completed. Cause further Process calls in the previous epoch to be
    // ignored.
    ++epoch_;
    port_allocation_complete_callback_();
  }
}

void AllocationSequence::CreateUDPPorts() {
  if (IsFlagSet(PORTALLOCATOR_DISABLE_UDP)) {
    RTC_LOG(LS_VERBOSE) << "AllocationSequence: UDP ports disabled, skipping.";
    return;
  }

  // TODO(mallinath) - Remove UDPPort creating socket after shared socket
  // is enabled completely.
  std::unique_ptr<UDPPort> port;
  bool emit_local_candidate_for_anyaddress =
      !IsFlagSet(PORTALLOCATOR_DISABLE_DEFAULT_LOCAL_CANDIDATE);
  if (IsFlagSet(PORTALLOCATOR_ENABLE_SHARED_SOCKET) && udp_socket_) {
    port = UDPPort::Create(
        session_->network_thread(), session_->socket_factory(), network_,
        udp_socket_.get(), session_->username(), session_->password(),
        emit_local_candidate_for_anyaddress,
        session_->allocator()->stun_candidate_keepalive_interval());
  } else {
    port = UDPPort::Create(
        session_->network_thread(), session_->socket_factory(), network_,
        session_->allocator()->min_port(), session_->allocator()->max_port(),
        session_->username(), session_->password(),
        emit_local_candidate_for_anyaddress,
        session_->allocator()->stun_candidate_keepalive_interval());
  }

  if (port) {
    // If shared socket is enabled, STUN candidate will be allocated by the
    // UDPPort.
    if (IsFlagSet(PORTALLOCATOR_ENABLE_SHARED_SOCKET)) {
      udp_port_ = port.get();
      port->SubscribePortDestroyed(
          [this](PortInterface* port) { OnPortDestroyed(port); });

      // If STUN is not disabled, setting stun server address to port.
      if (!IsFlagSet(PORTALLOCATOR_DISABLE_STUN)) {
        if (config_ && !config_->StunServers().empty()) {
          RTC_LOG(LS_INFO)
              << "AllocationSequence: UDPPort will be handling the "
                 "STUN candidate generation.";
          port->set_server_addresses(config_->StunServers());
        }
      }
    }

    session_->AddAllocatedPort(port.release(), this);
  }
}

void AllocationSequence::CreateTCPPorts() {
  if (IsFlagSet(PORTALLOCATOR_DISABLE_TCP)) {
    RTC_LOG(LS_VERBOSE) << "AllocationSequence: TCP ports disabled, skipping.";
    return;
  }

  std::unique_ptr<Port> port = TCPPort::Create(
      session_->network_thread(), session_->socket_factory(), network_,
      session_->allocator()->min_port(), session_->allocator()->max_port(),
      session_->username(), session_->password(),
      session_->allocator()->allow_tcp_listen());
  if (port) {
    session_->AddAllocatedPort(port.release(), this);
    // Since TCPPort is not created using shared socket, `port` will not be
    // added to the dequeue.
  }
}

void AllocationSequence::CreateStunPorts() {
  if (IsFlagSet(PORTALLOCATOR_DISABLE_STUN)) {
    RTC_LOG(LS_VERBOSE) << "AllocationSequence: STUN ports disabled, skipping.";
    return;
  }

  if (IsFlagSet(PORTALLOCATOR_ENABLE_SHARED_SOCKET)) {
    return;
  }

  if (!(config_ && !config_->StunServers().empty())) {
    RTC_LOG(LS_WARNING)
        << "AllocationSequence: No STUN server configured, skipping.";
    return;
  }

  std::unique_ptr<StunPort> port = StunPort::Create(
      session_->network_thread(), session_->socket_factory(), network_,
      session_->allocator()->min_port(), session_->allocator()->max_port(),
      session_->username(), session_->password(), config_->StunServers(),
      session_->allocator()->stun_candidate_keepalive_interval());
  if (port) {
    session_->AddAllocatedPort(port.release(), this);
    // Since StunPort is not created using shared socket, `port` will not be
    // added to the dequeue.
  }
}

void AllocationSequence::CreateRelayPorts() {
  if (IsFlagSet(PORTALLOCATOR_DISABLE_RELAY)) {
    RTC_LOG(LS_VERBOSE)
        << "AllocationSequence: Relay ports disabled, skipping.";
    return;
  }

  // If BasicPortAllocatorSession::OnAllocate left relay ports enabled then we
  // ought to have a relay list for them here.
  RTC_DCHECK(config_);
  RTC_DCHECK(!config_->relays.empty());
  if (!(config_ && !config_->relays.empty())) {
    RTC_LOG(LS_WARNING)
        << "AllocationSequence: No relay server configured, skipping.";
    return;
  }

  for (RelayServerConfig& relay : config_->relays) {
    CreateTurnPort(relay);
  }
}

void AllocationSequence::CreateTurnPort(const RelayServerConfig& config) {
  PortList::const_iterator relay_port;
  for (relay_port = config.ports.begin(); relay_port != config.ports.end();
       ++relay_port) {
    // Skip UDP connections to relay servers if it's disallowed.
    if (IsFlagSet(PORTALLOCATOR_DISABLE_UDP_RELAY) &&
        relay_port->proto == PROTO_UDP) {
      continue;
    }

    // Do not create a port if the server address family is known and does
    // not match the local IP address family.
    int server_ip_family = relay_port->address.ipaddr().family();
    int local_ip_family = network_->GetBestIP().family();
    if (server_ip_family != AF_UNSPEC && server_ip_family != local_ip_family) {
      RTC_LOG(LS_INFO)
          << "Server and local address families are not compatible. "
             "Server address: "
          << relay_port->address.ipaddr().ToSensitiveString()
          << " Local address: " << network_->GetBestIP().ToSensitiveString();
      continue;
    }

    CreateRelayPortArgs args;
    args.network_thread = session_->network_thread();
    args.socket_factory = session_->socket_factory();
    args.network = network_;
    args.username = session_->username();
    args.password = session_->password();
    args.server_address = &(*relay_port);
    args.config = &config;
    args.turn_customizer = session_->allocator()->turn_customizer();

    std::unique_ptr<cricket::Port> port;
    // Shared socket mode must be enabled only for UDP based ports. Hence
    // don't pass shared socket for ports which will create TCP sockets.
    // TODO(mallinath) - Enable shared socket mode for TURN ports. Disabled
    // due to webrtc bug https://code.google.com/p/webrtc/issues/detail?id=3537
    if (IsFlagSet(PORTALLOCATOR_ENABLE_SHARED_SOCKET) &&
        relay_port->proto == PROTO_UDP && udp_socket_) {
      port = session_->allocator()->relay_port_factory()->Create(
          args, udp_socket_.get());

      if (!port) {
        RTC_LOG(LS_WARNING) << "Failed to create relay port with "
                            << args.server_address->address.ToSensitiveString();
        continue;
      }

      relay_ports_.push_back(port.get());
      // Listen to the port destroyed signal, to allow AllocationSequence to
      // remove the entry from it's map.
      port->SubscribePortDestroyed(
          [this](PortInterface* port) { OnPortDestroyed(port); });

    } else {
      port = session_->allocator()->relay_port_factory()->Create(
          args, session_->allocator()->min_port(),
          session_->allocator()->max_port());

      if (!port) {
        RTC_LOG(LS_WARNING) << "Failed to create relay port with "
                            << args.server_address->address.ToSensitiveString();
        continue;
      }
    }
    RTC_DCHECK(port != NULL);
    session_->AddAllocatedPort(port.release(), this);
  }
}

void AllocationSequence::OnReadPacket(rtc::AsyncPacketSocket* socket,
                                      const char* data,
                                      size_t size,
                                      const rtc::SocketAddress& remote_addr,
                                      const int64_t& packet_time_us) {
  RTC_DCHECK(socket == udp_socket_.get());

  bool turn_port_found = false;

  // Try to find the TurnPort that matches the remote address. Note that the
  // message could be a STUN binding response if the TURN server is also used as
  // a STUN server. We don't want to parse every message here to check if it is
  // a STUN binding response, so we pass the message to TurnPort regardless of
  // the message type. The TurnPort will just ignore the message since it will
  // not find any request by transaction ID.
  for (auto* port : relay_ports_) {
    if (port->CanHandleIncomingPacketsFrom(remote_addr)) {
      if (port->HandleIncomingPacket(socket, data, size, remote_addr,
                                     packet_time_us)) {
        return;
      }
      turn_port_found = true;
    }
  }

  if (udp_port_) {
    const ServerAddresses& stun_servers = udp_port_->server_addresses();

    // Pass the packet to the UdpPort if there is no matching TurnPort, or if
    // the TURN server is also a STUN server.
    if (!turn_port_found ||
        stun_servers.find(remote_addr) != stun_servers.end()) {
      RTC_DCHECK(udp_port_->SharedSocket());
      udp_port_->HandleIncomingPacket(socket, data, size, remote_addr,
                                      packet_time_us);
    }
  }
}

void AllocationSequence::OnPortDestroyed(PortInterface* port) {
  if (udp_port_ == port) {
    udp_port_ = NULL;
    return;
  }

  auto it = absl::c_find(relay_ports_, port);
  if (it != relay_ports_.end()) {
    relay_ports_.erase(it);
  } else {
    RTC_LOG(LS_ERROR) << "Unexpected OnPortDestroyed for nonexistent port.";
    RTC_DCHECK_NOTREACHED();
  }
}

// PortConfiguration
PortConfiguration::PortConfiguration(const rtc::SocketAddress& stun_address,
                                     const std::string& username,
                                     const std::string& password)
    : stun_address(stun_address), username(username), password(password) {
  if (!stun_address.IsNil())
    stun_servers.insert(stun_address);
}

PortConfiguration::PortConfiguration(const ServerAddresses& stun_servers,
                                     const std::string& username,
                                     const std::string& password)
    : stun_servers(stun_servers), username(username), password(password) {
  if (!stun_servers.empty())
    stun_address = *(stun_servers.begin());
  // Note that this won't change once the config is initialized.
  use_turn_server_as_stun_server_disabled =
      webrtc::field_trial::IsDisabled("WebRTC-UseTurnServerAsStunServer");
}

ServerAddresses PortConfiguration::StunServers() {
  if (!stun_address.IsNil() &&
      stun_servers.find(stun_address) == stun_servers.end()) {
    stun_servers.insert(stun_address);
  }

  if (!stun_servers.empty() && use_turn_server_as_stun_server_disabled) {
    return stun_servers;
  }

  // Every UDP TURN server should also be used as a STUN server if
  // use_turn_server_as_stun_server is not disabled or the stun servers are
  // empty.
  ServerAddresses turn_servers = GetRelayServerAddresses(PROTO_UDP);
  for (const rtc::SocketAddress& turn_server : turn_servers) {
    if (stun_servers.find(turn_server) == stun_servers.end()) {
      stun_servers.insert(turn_server);
    }
  }
  return stun_servers;
}

void PortConfiguration::AddRelay(const RelayServerConfig& config) {
  relays.push_back(config);
}

bool PortConfiguration::SupportsProtocol(const RelayServerConfig& relay,
                                         ProtocolType type) const {
  PortList::const_iterator relay_port;
  for (relay_port = relay.ports.begin(); relay_port != relay.ports.end();
       ++relay_port) {
    if (relay_port->proto == type)
      return true;
  }
  return false;
}

bool PortConfiguration::SupportsProtocol(ProtocolType type) const {
  for (size_t i = 0; i < relays.size(); ++i) {
    if (SupportsProtocol(relays[i], type))
      return true;
  }
  return false;
}

ServerAddresses PortConfiguration::GetRelayServerAddresses(
    ProtocolType type) const {
  ServerAddresses servers;
  for (size_t i = 0; i < relays.size(); ++i) {
    if (SupportsProtocol(relays[i], type)) {
      servers.insert(relays[i].ports.front().address);
    }
  }
  return servers;
}

}  // namespace cricket
