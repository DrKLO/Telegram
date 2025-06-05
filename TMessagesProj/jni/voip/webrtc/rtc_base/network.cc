/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/network.h"

#include "absl/strings/string_view.h"
#include "rtc_base/experiments/field_trial_parser.h"

#if defined(WEBRTC_POSIX)
#include <net/if.h>
#endif  // WEBRTC_POSIX

#if defined(WEBRTC_WIN)
#include <iphlpapi.h>

#include "rtc_base/win32.h"
#elif !defined(__native_client__)
#include "rtc_base/ifaddrs_converter.h"
#endif

#include <memory>

#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "api/transport/field_trial_based_config.h"
#include "api/units/time_delta.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/memory/always_valid_pointer.h"
#include "rtc_base/network_monitor.h"
#include "rtc_base/socket.h"  // includes something that makes windows happy
#include "rtc_base/string_encode.h"
#include "rtc_base/string_utils.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/thread.h"

namespace rtc {
namespace {
using ::webrtc::SafeTask;
using ::webrtc::TimeDelta;

// List of MAC addresses of known VPN (for windows).
constexpr uint8_t kVpns[3][6] = {
    // Cisco AnyConnect SSL VPN Client.
    {0x0, 0x5, 0x9A, 0x3C, 0x7A, 0x0},
    // Cisco AnyConnect IPSEC VPN Client.
    {0x0, 0x5, 0x9A, 0x3C, 0x78, 0x0},
    // GlobalProtect Virtual Ethernet.
    {0x2, 0x50, 0x41, 0x0, 0x0, 0x1},
};

// Fetch list of networks every two seconds.
const int kNetworksUpdateIntervalMs = 2000;

const int kHighestNetworkPreference = 127;

struct AddressList {
  std::unique_ptr<Network> net;
  std::vector<InterfaceAddress> ips;
};

bool SortNetworks(const Network* a, const Network* b) {
  // Network types will be preferred above everything else while sorting
  // Networks.

  // Networks are sorted first by type.
  if (a->type() != b->type()) {
    return a->type() < b->type();
  }

  IPAddress ip_a = a->GetBestIP();
  IPAddress ip_b = b->GetBestIP();

  // After type, networks are sorted by IP address precedence values
  // from RFC 3484-bis
  if (IPAddressPrecedence(ip_a) != IPAddressPrecedence(ip_b)) {
    return IPAddressPrecedence(ip_a) > IPAddressPrecedence(ip_b);
  }

  // TODO(mallinath) - Add VPN and Link speed conditions while sorting.

  // Networks are sorted last by key.
  return a->key() < b->key();
}

uint16_t ComputeNetworkCostByType(int type,
                                  bool is_vpn,
                                  bool use_differentiated_cellular_costs,
                                  bool add_network_cost_to_vpn) {
  // TODO(jonaso) : Rollout support for cellular network cost using A/B
  // experiment to make sure it does not introduce regressions.
  int vpnCost = (is_vpn && add_network_cost_to_vpn) ? kNetworkCostVpn : 0;
  switch (type) {
    case rtc::ADAPTER_TYPE_ETHERNET:
    case rtc::ADAPTER_TYPE_LOOPBACK:
      return kNetworkCostMin + vpnCost;
    case rtc::ADAPTER_TYPE_WIFI:
      return kNetworkCostLow + vpnCost;
    case rtc::ADAPTER_TYPE_CELLULAR:
      return kNetworkCostCellular + vpnCost;
    case rtc::ADAPTER_TYPE_CELLULAR_2G:
      return (use_differentiated_cellular_costs ? kNetworkCostCellular2G
                                                : kNetworkCostCellular) +
             vpnCost;
    case rtc::ADAPTER_TYPE_CELLULAR_3G:
      return (use_differentiated_cellular_costs ? kNetworkCostCellular3G
                                                : kNetworkCostCellular) +
             vpnCost;
    case rtc::ADAPTER_TYPE_CELLULAR_4G:
      return (use_differentiated_cellular_costs ? kNetworkCostCellular4G
                                                : kNetworkCostCellular) +
             vpnCost;
    case rtc::ADAPTER_TYPE_CELLULAR_5G:
      return (use_differentiated_cellular_costs ? kNetworkCostCellular5G
                                                : kNetworkCostCellular) +
             vpnCost;
    case rtc::ADAPTER_TYPE_ANY:
      // Candidates gathered from the any-address/wildcard ports, as backups,
      // are given the maximum cost so that if there are other candidates with
      // known interface types, we would not select candidate pairs using these
      // backup candidates if other selection criteria with higher precedence
      // (network conditions over the route) are the same. Note that setting the
      // cost to kNetworkCostUnknown would be problematic since
      // ADAPTER_TYPE_CELLULAR would then have a higher cost. See
      // P2PTransportChannel::SortConnectionsAndUpdateState for how we rank and
      // select candidate pairs, where the network cost is among the criteria.
      return kNetworkCostMax + vpnCost;
    case rtc::ADAPTER_TYPE_VPN:
      // The cost of a VPN should be computed using its underlying network type.
      RTC_DCHECK_NOTREACHED();
      return kNetworkCostUnknown;
    default:
      return kNetworkCostUnknown + vpnCost;
  }
}

#if !defined(__native_client__)
bool IsIgnoredIPv6(bool allow_mac_based_ipv6, const InterfaceAddress& ip) {
  if (ip.family() != AF_INET6) {
    return false;
  }

  // Link-local addresses require scope id to be bound successfully.
  // However, our IPAddress structure doesn't carry that so the
  // information is lost and causes binding failure.
  if (IPIsLinkLocal(ip)) {
    RTC_LOG(LS_VERBOSE) << "Ignore link local IP:" << ip.ToSensitiveString();
    return true;
  }

  // Any MAC based IPv6 should be avoided to prevent the MAC tracking.
  if (IPIsMacBased(ip) && !allow_mac_based_ipv6) {
    RTC_LOG(LS_INFO) << "Ignore Mac based IP:" << ip.ToSensitiveString();
    return true;
  }

  // Ignore deprecated IPv6.
  if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_DEPRECATED) {
    RTC_LOG(LS_INFO) << "Ignore deprecated IP:" << ip.ToSensitiveString();
    return true;
  }

  return false;
}
#endif  // !defined(__native_client__)

// Note: consider changing to const Network* as arguments
// if/when considering other changes that should not trigger
// OnNetworksChanged.
bool ShouldAdapterChangeTriggerNetworkChange(rtc::AdapterType old_type,
                                             rtc::AdapterType new_type) {
  // skip triggering OnNetworksChanged if
  // changing from one cellular to another.
  if (Network::IsCellular(old_type) && Network::IsCellular(new_type))
    return false;
  return true;
}

#if defined(WEBRTC_WIN)
bool IpAddressAttributesEnabled(const webrtc::FieldTrialsView* field_trials) {
  // Field trial key reserved in bugs.webrtc.org/14334
  if (field_trials &&
      field_trials->IsEnabled("WebRTC-IPv6NetworkResolutionFixes")) {
    webrtc::FieldTrialParameter<bool> ip_address_attributes_enabled(
        "IpAddressAttributesEnabled", false);
    webrtc::ParseFieldTrial(
        {&ip_address_attributes_enabled},
        field_trials->Lookup("WebRTC-IPv6NetworkResolutionFixes"));
    return ip_address_attributes_enabled;
  }
  return false;
}
#endif  // WEBRTC_WIN

}  // namespace

// These addresses are used as the targets to find out the default local address
// on a multi-homed endpoint. They are actually DNS servers.
const char kPublicIPv4Host[] = "8.8.8.8";
const char kPublicIPv6Host[] = "2001:4860:4860::8888";
const int kPublicPort = 53;  // DNS port.

namespace webrtc_network_internal {
bool CompareNetworks(const std::unique_ptr<Network>& a,
                     const std::unique_ptr<Network>& b) {
  if (a->prefix_length() != b->prefix_length()) {
    return a->prefix_length() < b->prefix_length();
  }
  if (a->name() != b->name()) {
    return a->name() < b->name();
  }
  return a->prefix() < b->prefix();
}
}  // namespace webrtc_network_internal

std::string MakeNetworkKey(absl::string_view name,
                           const IPAddress& prefix,
                           int prefix_length) {
  rtc::StringBuilder ost;
  ost << name << "%" << prefix.ToString() << "/" << prefix_length;
  return ost.Release();
}
// Test if the network name matches the type<number> pattern, e.g. eth0. The
// matching is case-sensitive.
bool MatchTypeNameWithIndexPattern(absl::string_view network_name,
                                   absl::string_view type_name) {
  if (!absl::StartsWith(network_name, type_name)) {
    return false;
  }
  return absl::c_none_of(network_name.substr(type_name.size()),
                         [](char c) { return !isdigit(c); });
}

// A cautious note that this method may not provide an accurate adapter type
// based on the string matching. Incorrect type of adapters can affect the
// result of the downstream network filtering, see e.g.
// BasicPortAllocatorSession::GetNetworks when
// PORTALLOCATOR_DISABLE_COSTLY_NETWORKS is turned on.
AdapterType GetAdapterTypeFromName(absl::string_view network_name) {
  if (MatchTypeNameWithIndexPattern(network_name, "lo")) {
    // Note that we have a more robust way to determine if a network interface
    // is a loopback interface by checking the flag IFF_LOOPBACK in ifa_flags of
    // an ifaddr struct. See ConvertIfAddrs in this file.
    return ADAPTER_TYPE_LOOPBACK;
  }

  if (MatchTypeNameWithIndexPattern(network_name, "eth")) {
    return ADAPTER_TYPE_ETHERNET;
  }

  if (MatchTypeNameWithIndexPattern(network_name, "wlan") ||
      MatchTypeNameWithIndexPattern(network_name, "v4-wlan")) {
    return ADAPTER_TYPE_WIFI;
  }

  if (MatchTypeNameWithIndexPattern(network_name, "ipsec") ||
      MatchTypeNameWithIndexPattern(network_name, "tun") ||
      MatchTypeNameWithIndexPattern(network_name, "utun") ||
      MatchTypeNameWithIndexPattern(network_name, "tap")) {
    return ADAPTER_TYPE_VPN;
  }
#if defined(WEBRTC_IOS)
  // Cell networks are pdp_ipN on iOS.
  if (MatchTypeNameWithIndexPattern(network_name, "pdp_ip")) {
    return ADAPTER_TYPE_CELLULAR;
  }
  if (MatchTypeNameWithIndexPattern(network_name, "en")) {
    // This may not be most accurate because sometimes Ethernet interface
    // name also starts with "en" but it is better than showing it as
    // "unknown" type.
    // TODO(honghaiz): Write a proper IOS network manager.
    return ADAPTER_TYPE_WIFI;
  }
#elif defined(WEBRTC_ANDROID)
  if (MatchTypeNameWithIndexPattern(network_name, "rmnet") ||
      MatchTypeNameWithIndexPattern(network_name, "rmnet_data") ||
      MatchTypeNameWithIndexPattern(network_name, "v4-rmnet") ||
      MatchTypeNameWithIndexPattern(network_name, "v4-rmnet_data") ||
      MatchTypeNameWithIndexPattern(network_name, "clat") ||
      MatchTypeNameWithIndexPattern(network_name, "ccmni")) {
    return ADAPTER_TYPE_CELLULAR;
  }
#endif

  return ADAPTER_TYPE_UNKNOWN;
}

NetworkManager::EnumerationPermission NetworkManager::enumeration_permission()
    const {
  return ENUMERATION_ALLOWED;
}

bool NetworkManager::GetDefaultLocalAddress(int family, IPAddress* addr) const {
  return false;
}

webrtc::MdnsResponderInterface* NetworkManager::GetMdnsResponder() const {
  return nullptr;
}

NetworkManagerBase::NetworkManagerBase(
    const webrtc::FieldTrialsView* field_trials)
    : field_trials_(field_trials),
      enumeration_permission_(NetworkManager::ENUMERATION_ALLOWED),
      signal_network_preference_change_(
          field_trials
              ? field_trials->IsEnabled("WebRTC-SignalNetworkPreferenceChange")
              : false) {}

NetworkManager::EnumerationPermission
NetworkManagerBase::enumeration_permission() const {
  return enumeration_permission_;
}

std::unique_ptr<Network> NetworkManagerBase::CreateNetwork(
    absl::string_view name,
    absl::string_view description,
    const IPAddress& prefix,
    int prefix_length,
    AdapterType type) const {
  return std::make_unique<Network>(name, description, prefix, prefix_length,
                                   type);
}

std::vector<const Network*> NetworkManagerBase::GetAnyAddressNetworks() {
  std::vector<const Network*> networks;
  if (!ipv4_any_address_network_) {
    const rtc::IPAddress ipv4_any_address(INADDR_ANY);
    ipv4_any_address_network_ =
        CreateNetwork("any", "any", ipv4_any_address, 0, ADAPTER_TYPE_ANY);
    ipv4_any_address_network_->set_default_local_address_provider(this);
    ipv4_any_address_network_->set_mdns_responder_provider(this);
    ipv4_any_address_network_->AddIP(ipv4_any_address);
  }
  networks.push_back(ipv4_any_address_network_.get());

  if (!ipv6_any_address_network_) {
    const rtc::IPAddress ipv6_any_address(in6addr_any);
    ipv6_any_address_network_ =
        CreateNetwork("any", "any", ipv6_any_address, 0, ADAPTER_TYPE_ANY);
    ipv6_any_address_network_->set_default_local_address_provider(this);
    ipv6_any_address_network_->set_mdns_responder_provider(this);
    ipv6_any_address_network_->AddIP(ipv6_any_address);
  }
  networks.push_back(ipv6_any_address_network_.get());
  return networks;
}

std::vector<const Network*> NetworkManagerBase::GetNetworks() const {
  std::vector<const Network*> result;
  result.insert(result.begin(), networks_.begin(), networks_.end());
  return result;
}

void NetworkManagerBase::MergeNetworkList(
    std::vector<std::unique_ptr<Network>> new_networks,
    bool* changed) {
  NetworkManager::Stats stats;
  MergeNetworkList(std::move(new_networks), changed, &stats);
}

void NetworkManagerBase::MergeNetworkList(
    std::vector<std::unique_ptr<Network>> new_networks,
    bool* changed,
    NetworkManager::Stats* stats) {
  *changed = false;
  // AddressList in this map will track IP addresses for all Networks
  // with the same key.
  std::map<std::string, AddressList> consolidated_address_list;
  absl::c_sort(new_networks, rtc::webrtc_network_internal::CompareNetworks);
  // First, build a set of network-keys to the ipaddresses.
  for (auto& network : new_networks) {
    bool might_add_to_merged_list = false;
    std::string key = MakeNetworkKey(network->name(), network->prefix(),
                                     network->prefix_length());
    const std::vector<InterfaceAddress>& addresses = network->GetIPs();
    if (consolidated_address_list.find(key) ==
        consolidated_address_list.end()) {
      AddressList addrlist;
      addrlist.net = std::move(network);
      consolidated_address_list[key] = std::move(addrlist);
      might_add_to_merged_list = true;
    }
    AddressList& current_list = consolidated_address_list[key];
    for (const InterfaceAddress& address : addresses) {
      current_list.ips.push_back(address);
    }
    if (might_add_to_merged_list) {
      if (current_list.ips[0].family() == AF_INET) {
        stats->ipv4_network_count++;
      } else {
        RTC_DCHECK(current_list.ips[0].family() == AF_INET6);
        stats->ipv6_network_count++;
      }
    }
  }

  // Next, look for existing network objects to re-use.
  // Result of Network merge. Element in this list should have unique key.
  std::vector<Network*> merged_list;
  for (auto& kv : consolidated_address_list) {
    const std::string& key = kv.first;
    std::unique_ptr<Network> net = std::move(kv.second.net);
    auto existing = networks_map_.find(key);
    if (existing == networks_map_.end()) {
      // This network is new.
      net->set_id(next_available_network_id_++);
      // We might have accumulated IPAddresses from the first
      // step, set it here.
      net->SetIPs(kv.second.ips, true);
      // Place it in the network map.
      merged_list.push_back(net.get());
      networks_map_[key] = std::move(net);
      *changed = true;
    } else {
      // This network exists in the map already. Reset its IP addresses.
      Network* existing_net = existing->second.get();
      *changed = existing_net->SetIPs(kv.second.ips, *changed);
      merged_list.push_back(existing_net);
      if (net->type() != ADAPTER_TYPE_UNKNOWN &&
          net->type() != existing_net->type()) {
        if (ShouldAdapterChangeTriggerNetworkChange(existing_net->type(),
                                                    net->type())) {
          *changed = true;
        }
        existing_net->set_type(net->type());
      }
      // If the existing network was not active, networks have changed.
      if (!existing_net->active()) {
        *changed = true;
      }
      if (net->network_preference() != existing_net->network_preference()) {
        existing_net->set_network_preference(net->network_preference());
        if (signal_network_preference_change_) {
          *changed = true;
        }
      }
      RTC_DCHECK(net->active());
    }
    networks_map_[key]->set_mdns_responder_provider(this);
  }
  // It may still happen that the merged list is a subset of `networks_`.
  // To detect this change, we compare their sizes.
  if (merged_list.size() != networks_.size()) {
    *changed = true;
  }

  // If the network list changes, we re-assign `networks_` to the merged list
  // and re-sort it.
  if (*changed) {
    networks_ = merged_list;
    // Reset the active states of all networks.
    for (const auto& kv : networks_map_) {
      const std::unique_ptr<Network>& network = kv.second;
      // If `network` is in the newly generated `networks_`, it is active.
      bool found = absl::c_linear_search(networks_, network.get());
      network->set_active(found);
    }
    absl::c_sort(networks_, SortNetworks);
    // Now network interfaces are sorted, we should set the preference value
    // for each of the interfaces we are planning to use.
    // Preference order of network interfaces might have changed from previous
    // sorting due to addition of higher preference network interface.
    // Since we have already sorted the network interfaces based on our
    // requirements, we will just assign a preference value starting with 127,
    // in decreasing order.
    int pref = kHighestNetworkPreference;
    for (Network* network : networks_) {
      network->set_preference(pref);
      if (pref > 0) {
        --pref;
      } else {
        RTC_LOG(LS_ERROR) << "Too many network interfaces to handle!";
        break;
      }
    }
  }
}

void NetworkManagerBase::set_default_local_addresses(const IPAddress& ipv4,
                                                     const IPAddress& ipv6) {
  if (ipv4.family() == AF_INET) {
    default_local_ipv4_address_ = ipv4;
  }
  if (ipv6.family() == AF_INET6) {
    default_local_ipv6_address_ = ipv6;
  }
}

bool NetworkManagerBase::GetDefaultLocalAddress(int family,
                                                IPAddress* ipaddr) const {
  if (family == AF_INET && !default_local_ipv4_address_.IsNil()) {
    *ipaddr = default_local_ipv4_address_;
    return true;
  } else if (family == AF_INET6 && !default_local_ipv6_address_.IsNil()) {
    Network* ipv6_network = GetNetworkFromAddress(default_local_ipv6_address_);
    if (ipv6_network) {
      // If the default ipv6 network's BestIP is different than
      // default_local_ipv6_address_, use it instead.
      // This is to prevent potential IP address leakage. See WebRTC bug 5376.
      *ipaddr = ipv6_network->GetBestIP();
    } else {
      *ipaddr = default_local_ipv6_address_;
    }
    return true;
  }
  return false;
}

Network* NetworkManagerBase::GetNetworkFromAddress(
    const rtc::IPAddress& ip) const {
  for (Network* network : networks_) {
    const auto& ips = network->GetIPs();
    if (absl::c_any_of(ips, [&](const InterfaceAddress& existing_ip) {
          return ip == static_cast<rtc::IPAddress>(existing_ip);
        })) {
      return network;
    }
  }
  return nullptr;
}

bool NetworkManagerBase::IsVpnMacAddress(
    rtc::ArrayView<const uint8_t> address) {
  if (address.data() == nullptr && address.size() == 0) {
    return false;
  }
  for (const auto& vpn : kVpns) {
    if (sizeof(vpn) == address.size() &&
        memcmp(vpn, address.data(), address.size()) == 0) {
      return true;
    }
  }
  return false;
}

BasicNetworkManager::BasicNetworkManager(
    NetworkMonitorFactory* network_monitor_factory,
    SocketFactory* socket_factory,
    const webrtc::FieldTrialsView* field_trials_view)
    : NetworkManagerBase(field_trials_view),
      field_trials_(field_trials_view),
      network_monitor_factory_(network_monitor_factory),
      socket_factory_(socket_factory),
      allow_mac_based_ipv6_(
          field_trials()->IsEnabled("WebRTC-AllowMACBasedIPv6")),
      bind_using_ifname_(
          !field_trials()->IsDisabled("WebRTC-BindUsingInterfaceName")) {
  RTC_DCHECK(socket_factory_);
}

BasicNetworkManager::~BasicNetworkManager() {
  if (task_safety_flag_) {
    task_safety_flag_->SetNotAlive();
  }
}

void BasicNetworkManager::OnNetworksChanged() {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_LOG(LS_INFO) << "Network change was observed";
  UpdateNetworksOnce();
}

#if defined(__native_client__)

bool BasicNetworkManager::CreateNetworks(
    bool include_ignored,
    std::vector<std::unique_ptr<Network>>* networks) const {
  RTC_DCHECK_NOTREACHED();
  RTC_LOG(LS_WARNING) << "BasicNetworkManager doesn't work on NaCl yet";
  return false;
}

#elif defined(WEBRTC_POSIX)
NetworkMonitorInterface::InterfaceInfo BasicNetworkManager::GetInterfaceInfo(
    struct ifaddrs* cursor) const {
  if (cursor->ifa_flags & IFF_LOOPBACK) {
    return {
        .adapter_type = ADAPTER_TYPE_LOOPBACK,
        .underlying_type_for_vpn = ADAPTER_TYPE_UNKNOWN,
        .network_preference = NetworkPreference::NEUTRAL,
        .available = true,
    };
  } else if (network_monitor_) {
    return network_monitor_->GetInterfaceInfo(cursor->ifa_name);
  } else {
    return {.adapter_type = GetAdapterTypeFromName(cursor->ifa_name),
            .underlying_type_for_vpn = ADAPTER_TYPE_UNKNOWN,
            .network_preference = NetworkPreference::NEUTRAL,
            .available = true};
  }
}

void BasicNetworkManager::ConvertIfAddrs(
    struct ifaddrs* interfaces,
    IfAddrsConverter* ifaddrs_converter,
    bool include_ignored,
    std::vector<std::unique_ptr<Network>>* networks) const {
  std::map<std::string, Network*> current_networks;

  for (struct ifaddrs* cursor = interfaces; cursor != nullptr;
       cursor = cursor->ifa_next) {
    IPAddress prefix;
    IPAddress mask;
    InterfaceAddress ip;
    int scope_id = 0;

    // Some interfaces may not have address assigned.
    if (!cursor->ifa_addr || !cursor->ifa_netmask) {
      continue;
    }
    // Skip unknown family.
    if (cursor->ifa_addr->sa_family != AF_INET &&
        cursor->ifa_addr->sa_family != AF_INET6) {
      continue;
    }
    // Convert to InterfaceAddress.
    // TODO(webrtc:13114): Convert ConvertIfAddrs to use rtc::Netmask.
    if (!ifaddrs_converter->ConvertIfAddrsToIPAddress(cursor, &ip, &mask)) {
      continue;
    }
    // Skip ones which are down.
    if (!(cursor->ifa_flags & IFF_RUNNING)) {
      RTC_LOG(LS_INFO) << "Skip interface because of not IFF_RUNNING: "
                       << ip.ToSensitiveString();
      continue;
    }

    // Special case for IPv6 address.
    if (cursor->ifa_addr->sa_family == AF_INET6) {
      if (IsIgnoredIPv6(allow_mac_based_ipv6_, ip)) {
        continue;
      }
      scope_id =
          reinterpret_cast<sockaddr_in6*>(cursor->ifa_addr)->sin6_scope_id;
    }

    int prefix_length = CountIPMaskBits(mask);
    prefix = TruncateIP(ip, prefix_length);
    std::string key =
        MakeNetworkKey(std::string(cursor->ifa_name), prefix, prefix_length);

    auto iter = current_networks.find(key);
    if (iter != current_networks.end()) {
      // We have already added this network, simply add extra IP.
      iter->second->AddIP(ip);
#if RTC_DCHECK_IS_ON
      // Validate that different IP of same network has same properties
      auto existing_network = iter->second;

      NetworkMonitorInterface::InterfaceInfo if_info = GetInterfaceInfo(cursor);
      if (if_info.adapter_type != ADAPTER_TYPE_VPN &&
          IsConfiguredVpn(prefix, prefix_length)) {
        if_info.underlying_type_for_vpn = if_info.adapter_type;
        if_info.adapter_type = ADAPTER_TYPE_VPN;
      }

      RTC_DCHECK(existing_network->type() == if_info.adapter_type);
      RTC_DCHECK(existing_network->underlying_type_for_vpn() ==
                 if_info.underlying_type_for_vpn);
      RTC_DCHECK(existing_network->network_preference() ==
                 if_info.network_preference);
      if (!if_info.available) {
        RTC_DCHECK(existing_network->ignored());
      }
#endif  // RTC_DCHECK_IS_ON
      continue;
    }

    // Create a new network.
    NetworkMonitorInterface::InterfaceInfo if_info = GetInterfaceInfo(cursor);

    // Check manually configured VPN override.
    if (if_info.adapter_type != ADAPTER_TYPE_VPN &&
        IsConfiguredVpn(prefix, prefix_length)) {
      if_info.underlying_type_for_vpn = if_info.adapter_type;
      if_info.adapter_type = ADAPTER_TYPE_VPN;
    }

    auto network = CreateNetwork(cursor->ifa_name, cursor->ifa_name, prefix,
                                 prefix_length, if_info.adapter_type);
    network->set_default_local_address_provider(this);
    network->set_scope_id(scope_id);
    network->AddIP(ip);
    if (!if_info.available) {
      network->set_ignored(true);
    } else {
      network->set_ignored(IsIgnoredNetwork(*network));
    }
    network->set_underlying_type_for_vpn(if_info.underlying_type_for_vpn);
    network->set_network_preference(if_info.network_preference);
    if (include_ignored || !network->ignored()) {
      current_networks[key] = network.get();
      networks->push_back(std::move(network));
    }
  }
}

bool BasicNetworkManager::CreateNetworks(
    bool include_ignored,
    std::vector<std::unique_ptr<Network>>* networks) const {
  struct ifaddrs* interfaces;
  int error = getifaddrs(&interfaces);
  if (error != 0) {
    RTC_LOG_ERR(LS_ERROR) << "getifaddrs failed to gather interface data: "
                          << error;
    return false;
  }

  std::unique_ptr<IfAddrsConverter> ifaddrs_converter(CreateIfAddrsConverter());
  ConvertIfAddrs(interfaces, ifaddrs_converter.get(), include_ignored,
                 networks);

  freeifaddrs(interfaces);
  return true;
}

#elif defined(WEBRTC_WIN)

unsigned int GetPrefix(PIP_ADAPTER_PREFIX prefixlist,
                       const IPAddress& ip,
                       IPAddress* prefix) {
  IPAddress current_prefix;
  IPAddress best_prefix;
  unsigned int best_length = 0;
  while (prefixlist) {
    // Look for the longest matching prefix in the prefixlist.
    if (prefixlist->Address.lpSockaddr == nullptr ||
        prefixlist->Address.lpSockaddr->sa_family != ip.family()) {
      prefixlist = prefixlist->Next;
      continue;
    }
    switch (prefixlist->Address.lpSockaddr->sa_family) {
      case AF_INET: {
        sockaddr_in* v4_addr =
            reinterpret_cast<sockaddr_in*>(prefixlist->Address.lpSockaddr);
        current_prefix = IPAddress(v4_addr->sin_addr);
        break;
      }
      case AF_INET6: {
        sockaddr_in6* v6_addr =
            reinterpret_cast<sockaddr_in6*>(prefixlist->Address.lpSockaddr);
        current_prefix = IPAddress(v6_addr->sin6_addr);
        break;
      }
      default: {
        prefixlist = prefixlist->Next;
        continue;
      }
    }
    if (TruncateIP(ip, prefixlist->PrefixLength) == current_prefix &&
        prefixlist->PrefixLength > best_length) {
      best_prefix = current_prefix;
      best_length = prefixlist->PrefixLength;
    }
    prefixlist = prefixlist->Next;
  }
  *prefix = best_prefix;
  return best_length;
}

bool BasicNetworkManager::CreateNetworks(
    bool include_ignored,
    std::vector<std::unique_ptr<Network>>* networks) const {
  std::map<std::string, Network*> current_networks;
  // MSDN recommends a 15KB buffer for the first try at GetAdaptersAddresses.
  size_t buffer_size = 16384;
  std::unique_ptr<char[]> adapter_info(new char[buffer_size]);
  PIP_ADAPTER_ADDRESSES adapter_addrs =
      reinterpret_cast<PIP_ADAPTER_ADDRESSES>(adapter_info.get());
  int adapter_flags = (GAA_FLAG_SKIP_DNS_SERVER | GAA_FLAG_SKIP_ANYCAST |
                       GAA_FLAG_SKIP_MULTICAST | GAA_FLAG_INCLUDE_PREFIX);
  int ret = 0;
  do {
    adapter_info.reset(new char[buffer_size]);
    adapter_addrs = reinterpret_cast<PIP_ADAPTER_ADDRESSES>(adapter_info.get());
    ret = GetAdaptersAddresses(AF_UNSPEC, adapter_flags, 0, adapter_addrs,
                               reinterpret_cast<PULONG>(&buffer_size));
  } while (ret == ERROR_BUFFER_OVERFLOW);
  if (ret != ERROR_SUCCESS) {
    return false;
  }
  int count = 0;
  while (adapter_addrs) {
    if (adapter_addrs->OperStatus == IfOperStatusUp) {
      PIP_ADAPTER_UNICAST_ADDRESS address = adapter_addrs->FirstUnicastAddress;
      PIP_ADAPTER_PREFIX prefixlist = adapter_addrs->FirstPrefix;
      std::string description = ToUtf8(adapter_addrs->Description,
                                       wcslen(adapter_addrs->Description));

      for (; address; address = address->Next) {
        std::string name = rtc::ToString(count);
#if !defined(NDEBUG)
        name = ToUtf8(adapter_addrs->FriendlyName,
                      wcslen(adapter_addrs->FriendlyName));
#endif

        IPAddress ip;
        int scope_id = 0;
        std::unique_ptr<Network> network;
        switch (address->Address.lpSockaddr->sa_family) {
          case AF_INET: {
            sockaddr_in* v4_addr =
                reinterpret_cast<sockaddr_in*>(address->Address.lpSockaddr);
            ip = IPAddress(v4_addr->sin_addr);
            break;
          }
          case AF_INET6: {
            sockaddr_in6* v6_addr =
                reinterpret_cast<sockaddr_in6*>(address->Address.lpSockaddr);
            scope_id = v6_addr->sin6_scope_id;

            // From http://technet.microsoft.com/en-us/ff568768(v=vs.60).aspx,
            // the way to identify a temporary IPv6 Address is to check if
            // PrefixOrigin is equal to IpPrefixOriginRouterAdvertisement and
            // SuffixOrigin equal to IpSuffixOriginRandom.
            int ip_address_attributes = IPV6_ADDRESS_FLAG_NONE;
            if (IpAddressAttributesEnabled(field_trials_.get())) {
              if (address->PrefixOrigin == IpPrefixOriginRouterAdvertisement &&
                  address->SuffixOrigin == IpSuffixOriginRandom) {
                ip_address_attributes |= IPV6_ADDRESS_FLAG_TEMPORARY;
              }
              if (address->PreferredLifetime == 0) {
                ip_address_attributes |= IPV6_ADDRESS_FLAG_DEPRECATED;
              }
            }
            if (IsIgnoredIPv6(allow_mac_based_ipv6_,
                              InterfaceAddress(v6_addr->sin6_addr,
                                               ip_address_attributes))) {
              continue;
            }
            ip = InterfaceAddress(v6_addr->sin6_addr, ip_address_attributes);
            break;
          }
          default: {
            continue;
          }
        }

        IPAddress prefix;
        int prefix_length = GetPrefix(prefixlist, ip, &prefix);
        std::string key = MakeNetworkKey(name, prefix, prefix_length);
        auto existing_network = current_networks.find(key);
        if (existing_network == current_networks.end()) {
          AdapterType adapter_type = ADAPTER_TYPE_UNKNOWN;
          switch (adapter_addrs->IfType) {
            case IF_TYPE_SOFTWARE_LOOPBACK:
              adapter_type = ADAPTER_TYPE_LOOPBACK;
              break;
            case IF_TYPE_ETHERNET_CSMACD:
            case IF_TYPE_ETHERNET_3MBIT:
            case IF_TYPE_IEEE80212:
            case IF_TYPE_FASTETHER:
            case IF_TYPE_FASTETHER_FX:
            case IF_TYPE_GIGABITETHERNET:
              adapter_type = ADAPTER_TYPE_ETHERNET;
              break;
            case IF_TYPE_IEEE80211:
              adapter_type = ADAPTER_TYPE_WIFI;
              break;
            case IF_TYPE_WWANPP:
            case IF_TYPE_WWANPP2:
              adapter_type = ADAPTER_TYPE_CELLULAR;
              break;
            default:
              // TODO(phoglund): Need to recognize other types as well.
              adapter_type = ADAPTER_TYPE_UNKNOWN;
              break;
          }
          auto underlying_type_for_vpn = ADAPTER_TYPE_UNKNOWN;
          if (adapter_type != ADAPTER_TYPE_VPN &&
              IsConfiguredVpn(prefix, prefix_length)) {
            underlying_type_for_vpn = adapter_type;
            adapter_type = ADAPTER_TYPE_VPN;
          }
          if (adapter_type != ADAPTER_TYPE_VPN &&
              IsVpnMacAddress(rtc::ArrayView<const uint8_t>(
                  reinterpret_cast<const uint8_t*>(
                      adapter_addrs->PhysicalAddress),
                  adapter_addrs->PhysicalAddressLength))) {
            // With MAC-based detection we do not know the
            // underlying adapter type.
            underlying_type_for_vpn = ADAPTER_TYPE_UNKNOWN;
            adapter_type = ADAPTER_TYPE_VPN;
          }

          auto network = CreateNetwork(name, description, prefix, prefix_length,
                                       adapter_type);
          network->set_underlying_type_for_vpn(underlying_type_for_vpn);
          network->set_default_local_address_provider(this);
          network->set_mdns_responder_provider(this);
          network->set_scope_id(scope_id);
          network->AddIP(ip);
          bool ignored = IsIgnoredNetwork(*network);
          network->set_ignored(ignored);
          if (include_ignored || !network->ignored()) {
            current_networks[key] = network.get();
            networks->push_back(std::move(network));
          }
        } else {
          (*existing_network).second->AddIP(ip);
        }
      }
      // Count is per-adapter - all 'Networks' created from the same
      // adapter need to have the same name.
      ++count;
    }
    adapter_addrs = adapter_addrs->Next;
  }
  return true;
}
#endif  // WEBRTC_WIN

bool BasicNetworkManager::IsIgnoredNetwork(const Network& network) const {
  // Ignore networks on the explicit ignore list.
  for (const std::string& ignored_name : network_ignore_list_) {
    if (network.name() == ignored_name) {
      return true;
    }
  }

#if defined(WEBRTC_POSIX)
  // Filter out VMware/VirtualBox interfaces, typically named vmnet1,
  // vmnet8, or vboxnet0.
  if (strncmp(network.name().c_str(), "vmnet", 5) == 0 ||
      strncmp(network.name().c_str(), "vnic", 4) == 0 ||
      strncmp(network.name().c_str(), "vboxnet", 7) == 0) {
    return true;
  }
#elif defined(WEBRTC_WIN)
  // Ignore any HOST side vmware adapters with a description like:
  // VMware Virtual Ethernet Adapter for VMnet1
  // but don't ignore any GUEST side adapters with a description like:
  // VMware Accelerated AMD PCNet Adapter #2
  if (strstr(network.description().c_str(), "VMnet") != nullptr) {
    return true;
  }
#endif

  // Ignore any networks with a 0.x.y.z IP
  if (network.prefix().family() == AF_INET) {
    return (network.prefix().v4AddressAsHostOrderInteger() < 0x01000000);
  }

  return false;
}

void BasicNetworkManager::StartUpdating() {
  thread_ = Thread::Current();
  // Redundant but necessary for thread annotations.
  RTC_DCHECK_RUN_ON(thread_);
  if (start_count_) {
    // If network interfaces are already discovered and signal is sent,
    // we should trigger network signal immediately for the new clients
    // to start allocating ports.
    if (sent_first_update_)
      thread_->PostTask(SafeTask(task_safety_flag_, [this] {
        RTC_DCHECK_RUN_ON(thread_);
        SignalNetworksChanged();
      }));
  } else {
    RTC_DCHECK(task_safety_flag_ == nullptr);
    task_safety_flag_ = webrtc::PendingTaskSafetyFlag::Create();
    thread_->PostTask(SafeTask(task_safety_flag_, [this] {
      RTC_DCHECK_RUN_ON(thread_);
      UpdateNetworksContinually();
    }));
    StartNetworkMonitor();
  }
  ++start_count_;
}

void BasicNetworkManager::StopUpdating() {
  RTC_DCHECK_RUN_ON(thread_);
  if (!start_count_)
    return;

  --start_count_;
  if (!start_count_) {
    task_safety_flag_->SetNotAlive();
    task_safety_flag_ = nullptr;
    sent_first_update_ = false;
    StopNetworkMonitor();
  }
}

void BasicNetworkManager::StartNetworkMonitor() {
  if (network_monitor_factory_ == nullptr) {
    return;
  }
  if (!network_monitor_) {
    network_monitor_.reset(
        network_monitor_factory_->CreateNetworkMonitor(*field_trials()));
    if (!network_monitor_) {
      return;
    }
    network_monitor_->SetNetworksChangedCallback(
        [this]() { OnNetworksChanged(); });
  }

  if (network_monitor_->SupportsBindSocketToNetwork()) {
    // Set NetworkBinder on SocketServer so that
    // PhysicalSocket::Bind will call
    // BasicNetworkManager::BindSocketToNetwork(), (that will lookup interface
    // name and then call network_monitor_->BindSocketToNetwork()).
    thread_->socketserver()->set_network_binder(this);
  }

  network_monitor_->Start();
}

void BasicNetworkManager::StopNetworkMonitor() {
  if (!network_monitor_) {
    return;
  }
  network_monitor_->Stop();

  if (network_monitor_->SupportsBindSocketToNetwork()) {
    // Reset NetworkBinder on SocketServer.
    if (thread_->socketserver()->network_binder() == this) {
      thread_->socketserver()->set_network_binder(nullptr);
    }
  }
}

IPAddress BasicNetworkManager::QueryDefaultLocalAddress(int family) const {
  RTC_DCHECK(family == AF_INET || family == AF_INET6);

  std::unique_ptr<Socket> socket(
      socket_factory_->CreateSocket(family, SOCK_DGRAM));
  if (!socket) {
    RTC_LOG_ERR(LS_ERROR) << "Socket creation failed";
    return IPAddress();
  }

  if (socket->Connect(SocketAddress(
          family == AF_INET ? kPublicIPv4Host : kPublicIPv6Host, kPublicPort)) <
      0) {
    if (socket->GetError() != ENETUNREACH &&
        socket->GetError() != EHOSTUNREACH) {
      // Ignore the expected case of "host/net unreachable" - which happens if
      // the network is V4- or V6-only.
      RTC_LOG(LS_INFO) << "Connect failed with " << socket->GetError();
    }
    return IPAddress();
  }
  return socket->GetLocalAddress().ipaddr();
}

void BasicNetworkManager::UpdateNetworksOnce() {
  if (!start_count_)
    return;

  std::vector<std::unique_ptr<Network>> list;
  if (!CreateNetworks(false, &list)) {
    SignalError();
  } else {
    bool changed;
    NetworkManager::Stats stats;
    MergeNetworkList(std::move(list), &changed, &stats);
    set_default_local_addresses(QueryDefaultLocalAddress(AF_INET),
                                QueryDefaultLocalAddress(AF_INET6));
    if (changed || !sent_first_update_) {
      SignalNetworksChanged();
      sent_first_update_ = true;
    }
  }
}

void BasicNetworkManager::UpdateNetworksContinually() {
  UpdateNetworksOnce();
  thread_->PostDelayedTask(SafeTask(task_safety_flag_,
                                    [this] {
                                      RTC_DCHECK_RUN_ON(thread_);
                                      UpdateNetworksContinually();
                                    }),
                           TimeDelta::Millis(kNetworksUpdateIntervalMs));
}

void BasicNetworkManager::DumpNetworks() {
  RTC_DCHECK_RUN_ON(thread_);
  std::vector<const Network*> list = GetNetworks();
  RTC_LOG(LS_INFO) << "NetworkManager detected " << list.size() << " networks:";
  for (const Network* network : list) {
    RTC_LOG(LS_INFO) << network->ToString() << ": " << network->description()
                     << ", active ? " << network->active()
                     << ((network->ignored()) ? ", Ignored" : "");
  }
}

NetworkBindingResult BasicNetworkManager::BindSocketToNetwork(
    int socket_fd,
    const IPAddress& address) {
  RTC_DCHECK_RUN_ON(thread_);
  std::string if_name;
  if (bind_using_ifname_) {
    Network* net = GetNetworkFromAddress(address);
    if (net != nullptr) {
      if_name = net->name();
    }
  }
  return network_monitor_->BindSocketToNetwork(socket_fd, address, if_name);
}

Network::Network(absl::string_view name,
                 absl::string_view desc,
                 const IPAddress& prefix,
                 int prefix_length,
                 AdapterType type)
    : name_(name),
      description_(desc),
      prefix_(prefix),
      prefix_length_(prefix_length),
      key_(MakeNetworkKey(name, prefix, prefix_length)),
      scope_id_(0),
      ignored_(false),
      type_(type),
      preference_(0) {}

Network::Network(const Network&) = default;

Network::~Network() = default;

// Sets the addresses of this network. Returns true if the address set changed.
// Change detection is short circuited if the changed argument is true.
bool Network::SetIPs(const std::vector<InterfaceAddress>& ips, bool changed) {
  // Detect changes with a nested loop; n-squared but we expect on the order
  // of 2-3 addresses per network.
  changed = changed || ips.size() != ips_.size();
  if (!changed) {
    for (const InterfaceAddress& ip : ips) {
      if (!absl::c_linear_search(ips_, ip)) {
        changed = true;
        break;
      }
    }
  }

  ips_ = ips;
  return changed;
}

// Select the best IP address to use from this Network.
IPAddress Network::GetBestIP() const {
  if (ips_.size() == 0) {
    return IPAddress();
  }

  if (prefix_.family() == AF_INET) {
    return static_cast<IPAddress>(ips_.at(0));
  }

  InterfaceAddress selected_ip, link_local_ip, ula_ip;

  for (const InterfaceAddress& ip : ips_) {
    // Ignore any address which has been deprecated already.
    if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_DEPRECATED)
      continue;

    if (IPIsLinkLocal(ip)) {
      link_local_ip = ip;
      continue;
    }

    // ULA address should only be returned when we have no other
    // global IP.
    if (IPIsULA(static_cast<const IPAddress&>(ip))) {
      ula_ip = ip;
      continue;
    }
    selected_ip = ip;

    // Search could stop once a temporary non-deprecated one is found.
    if (ip.ipv6_flags() & IPV6_ADDRESS_FLAG_TEMPORARY)
      break;
  }

  if (IPIsUnspec(selected_ip)) {
    if (!IPIsUnspec(link_local_ip)) {
      // No proper global IPv6 address found, use link local address instead.
      selected_ip = link_local_ip;
    } else if (!IPIsUnspec(ula_ip)) {
      // No proper global and link local address found, use ULA instead.
      selected_ip = ula_ip;
    }
  }

  return static_cast<IPAddress>(selected_ip);
}

webrtc::MdnsResponderInterface* Network::GetMdnsResponder() const {
  if (mdns_responder_provider_ == nullptr) {
    return nullptr;
  }
  return mdns_responder_provider_->GetMdnsResponder();
}

uint16_t Network::GetCost(const webrtc::FieldTrialsView* field_trials) const {
  return GetCost(
      *webrtc::AlwaysValidPointer<const webrtc::FieldTrialsView,
                                  webrtc::FieldTrialBasedConfig>(field_trials));
}

uint16_t Network::GetCost(const webrtc::FieldTrialsView& field_trials) const {
  AdapterType type = IsVpn() ? underlying_type_for_vpn_ : type_;
  const bool use_differentiated_cellular_costs =
      field_trials.IsEnabled("WebRTC-UseDifferentiatedCellularCosts");
  const bool add_network_cost_to_vpn =
      field_trials.IsEnabled("WebRTC-AddNetworkCostToVpn");
  return ComputeNetworkCostByType(type, IsVpn(),
                                  use_differentiated_cellular_costs,
                                  add_network_cost_to_vpn);
}

// This is the inverse of ComputeNetworkCostByType().
std::pair<rtc::AdapterType, bool /* vpn */>
Network::GuessAdapterFromNetworkCost(int network_cost) {
  switch (network_cost) {
    case kNetworkCostMin:
      return {rtc::ADAPTER_TYPE_ETHERNET, false};
    case kNetworkCostMin + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_ETHERNET, true};
    case kNetworkCostLow:
      return {rtc::ADAPTER_TYPE_WIFI, false};
    case kNetworkCostLow + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_WIFI, true};
    case kNetworkCostCellular:
      return {rtc::ADAPTER_TYPE_CELLULAR, false};
    case kNetworkCostCellular + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_CELLULAR, true};
    case kNetworkCostCellular2G:
      return {rtc::ADAPTER_TYPE_CELLULAR_2G, false};
    case kNetworkCostCellular2G + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_CELLULAR_2G, true};
    case kNetworkCostCellular3G:
      return {rtc::ADAPTER_TYPE_CELLULAR_3G, false};
    case kNetworkCostCellular3G + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_CELLULAR_3G, true};
    case kNetworkCostCellular4G:
      return {rtc::ADAPTER_TYPE_CELLULAR_4G, false};
    case kNetworkCostCellular4G + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_CELLULAR_4G, true};
    case kNetworkCostCellular5G:
      return {rtc::ADAPTER_TYPE_CELLULAR_5G, false};
    case kNetworkCostCellular5G + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_CELLULAR_5G, true};
    case kNetworkCostUnknown:
      return {rtc::ADAPTER_TYPE_UNKNOWN, false};
    case kNetworkCostUnknown + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_UNKNOWN, true};
    case kNetworkCostMax:
      return {rtc::ADAPTER_TYPE_ANY, false};
    case kNetworkCostMax + kNetworkCostVpn:
      return {rtc::ADAPTER_TYPE_ANY, true};
  }
  RTC_LOG(LS_VERBOSE) << "Unknown network cost: " << network_cost;
  return {rtc::ADAPTER_TYPE_UNKNOWN, false};
}

std::string Network::ToString() const {
  rtc::StringBuilder ss;
  // Print out the first space-terminated token of the network desc, plus
  // the IP address.
  ss << "Net[" << description_.substr(0, description_.find(' ')) << ":"
     << prefix_.ToSensitiveString() << "/" << prefix_length_ << ":"
     << AdapterTypeToString(type_);
  if (IsVpn()) {
    ss << "/" << AdapterTypeToString(underlying_type_for_vpn_);
  }
  ss << ":id=" << id_ << "]";
  return ss.Release();
}

void BasicNetworkManager::set_vpn_list(const std::vector<NetworkMask>& vpn) {
  if (thread_ == nullptr) {
    vpn_ = vpn;
  } else {
    thread_->BlockingCall([this, vpn] { vpn_ = vpn; });
  }
}

bool BasicNetworkManager::IsConfiguredVpn(IPAddress prefix,
                                          int prefix_length) const {
  RTC_DCHECK_RUN_ON(thread_);
  for (const auto& vpn : vpn_) {
    if (prefix_length >= vpn.prefix_length()) {
      auto copy = TruncateIP(prefix, vpn.prefix_length());
      if (copy == vpn.address()) {
        return true;
      }
    }
  }
  return false;
}

}  // namespace rtc
