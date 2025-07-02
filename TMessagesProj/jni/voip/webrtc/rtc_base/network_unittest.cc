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

#include <stdlib.h>

#include <algorithm>
#include <memory>
#include <vector>

#include "absl/algorithm/container.h"
#include "absl/strings/match.h"
#include "absl/strings/string_view.h"
#include "rtc_base/checks.h"
#include "rtc_base/net_helpers.h"
#include "rtc_base/net_test_helpers.h"
#include "rtc_base/network_monitor.h"
#include "rtc_base/network_monitor_factory.h"
#include "rtc_base/physical_socket_server.h"
#if defined(WEBRTC_POSIX)
#include <net/if.h>
#include <sys/types.h>

#include "rtc_base/ifaddrs_converter.h"
#endif  // defined(WEBRTC_POSIX)
#include "rtc_base/gunit.h"
#include "test/gmock.h"
#if defined(WEBRTC_WIN)
#include "rtc_base/logging.h"  // For RTC_LOG_GLE
#endif
#include "test/field_trial.h"
#include "test/scoped_key_value_config.h"

using ::testing::Contains;
using ::testing::Not;
using ::testing::UnorderedElementsAre;
using ::testing::UnorderedElementsAreArray;

namespace rtc {

#define MAYBE_SKIP_IPV4                        \
  if (!HasIPv4Enabled()) {                     \
    RTC_LOG(LS_INFO) << "No IPv4... skipping"; \
    return;                                    \
  }

namespace {

IPAddress IPFromString(absl::string_view str) {
  IPAddress ip;
  RTC_CHECK(IPFromString(str, &ip));
  return ip;
}

class FakeNetworkMonitor : public NetworkMonitorInterface {
 public:
  void Start() override { started_ = true; }
  void Stop() override { started_ = false; }
  bool started() { return started_; }
  InterfaceInfo GetInterfaceInfo(absl::string_view if_name) override {
    InterfaceInfo if_info = {
        .adapter_type = ADAPTER_TYPE_UNKNOWN,
        .available = absl::c_count(unavailable_adapters_, if_name) == 0,
    };
    if (absl::StartsWith(if_name, "wifi")) {
      if_info.adapter_type = ADAPTER_TYPE_WIFI;
    } else if (absl::StartsWith(if_name, "cellular")) {
      if_info.adapter_type = ADAPTER_TYPE_CELLULAR;
    }
    return if_info;
  }

  // Used to test IsAdapterAvailable.
  void set_unavailable_adapters(std::vector<std::string> unavailable_adapters) {
    unavailable_adapters_ = unavailable_adapters;
  }

  bool SupportsBindSocketToNetwork() const override { return true; }

  NetworkBindingResult BindSocketToNetwork(int socket_fd,
                                           const IPAddress& address,
                                           absl::string_view if_name) override {
    if (absl::c_count(addresses_, address) > 0) {
      return NetworkBindingResult::SUCCESS;
    }

    for (auto const& iter : adapters_) {
      if (if_name.find(iter) != absl::string_view::npos) {
        return NetworkBindingResult::SUCCESS;
      }
    }
    return NetworkBindingResult::ADDRESS_NOT_FOUND;
  }

  void set_ip_addresses(std::vector<IPAddress> addresses) {
    addresses_ = addresses;
  }

  void set_adapters(std::vector<std::string> adapters) { adapters_ = adapters; }

  void InovkeNetworksChangedCallbackForTesting() {
    InvokeNetworksChangedCallback();
  }

 private:
  bool started_ = false;
  std::vector<std::string> adapters_;
  std::vector<std::string> unavailable_adapters_;
  std::vector<IPAddress> addresses_;
};

class FakeNetworkMonitorFactory : public NetworkMonitorFactory {
 public:
  FakeNetworkMonitorFactory() {}
  NetworkMonitorInterface* CreateNetworkMonitor(
      const webrtc::FieldTrialsView& field_trials) override {
    return new FakeNetworkMonitor();
  }
};

bool SameNameAndPrefix(const rtc::Network& a, const rtc::Network& b) {
  if (a.name() != b.name()) {
    RTC_LOG(LS_INFO) << "Different interface names.";
    return false;
  }
  if (a.prefix_length() != b.prefix_length() || a.prefix() != b.prefix()) {
    RTC_LOG(LS_INFO) << "Different IP prefixes.";
    return false;
  }
  return true;
}

std::vector<const Network*> CopyNetworkPointers(
    const std::vector<std::unique_ptr<Network>>& owning_list) {
  std::vector<const Network*> ptr_list;
  ptr_list.reserve(owning_list.size());
  for (const auto& network : owning_list) {
    ptr_list.push_back(network.get());
  }
  return ptr_list;
}

}  // namespace

class NetworkTest : public ::testing::Test, public sigslot::has_slots<> {
 public:
  NetworkTest() : callback_called_(false) {}

  void OnNetworksChanged() { callback_called_ = true; }

  NetworkManager::Stats MergeNetworkList(
      BasicNetworkManager& network_manager,
      std::vector<std::unique_ptr<Network>> list,
      bool* changed) {
    NetworkManager::Stats stats;
    network_manager.MergeNetworkList(std::move(list), changed, &stats);
    return stats;
  }

  bool IsIgnoredNetwork(BasicNetworkManager& network_manager,
                        const Network& network) {
    RTC_DCHECK_RUN_ON(network_manager.thread_);
    return network_manager.IsIgnoredNetwork(network);
  }

  IPAddress QueryDefaultLocalAddress(BasicNetworkManager& network_manager,
                                     int family) {
    RTC_DCHECK_RUN_ON(network_manager.thread_);
    return network_manager.QueryDefaultLocalAddress(family);
  }

  std::vector<std::unique_ptr<Network>> GetNetworks(
      const BasicNetworkManager& network_manager,
      bool include_ignored) {
    RTC_DCHECK_RUN_ON(network_manager.thread_);
    std::vector<std::unique_ptr<Network>> list;
    network_manager.CreateNetworks(include_ignored, &list);
    return list;
  }

  FakeNetworkMonitor* GetNetworkMonitor(BasicNetworkManager& network_manager) {
    RTC_DCHECK_RUN_ON(network_manager.thread_);
    return static_cast<FakeNetworkMonitor*>(
        network_manager.network_monitor_.get());
  }
  void ClearNetworks(BasicNetworkManager& network_manager) {
    network_manager.networks_.clear();
    network_manager.networks_map_.clear();
  }

  AdapterType GetAdapterType(BasicNetworkManager& network_manager) {
    std::vector<const Network*> list = network_manager.GetNetworks();
    RTC_CHECK_EQ(1, list.size());
    return list[0]->type();
  }

#if defined(WEBRTC_POSIX)
  // Separated from CreateNetworks for tests.
  static void CallConvertIfAddrs(
      const BasicNetworkManager& network_manager,
      struct ifaddrs* interfaces,
      bool include_ignored,
      std::vector<std::unique_ptr<Network>>* networks) {
    RTC_DCHECK_RUN_ON(network_manager.thread_);
    // Use the base IfAddrsConverter for test cases.
    std::unique_ptr<IfAddrsConverter> ifaddrs_converter(new IfAddrsConverter());
    network_manager.ConvertIfAddrs(interfaces, ifaddrs_converter.get(),
                                   include_ignored, networks);
  }

  struct sockaddr_in6* CreateIpv6Addr(absl::string_view ip_string,
                                      uint32_t scope_id) {
    struct sockaddr_in6* ipv6_addr =
        static_cast<struct sockaddr_in6*>(malloc(sizeof(struct sockaddr_in6)));
    memset(ipv6_addr, 0, sizeof(struct sockaddr_in6));
    ipv6_addr->sin6_family = AF_INET6;
    ipv6_addr->sin6_scope_id = scope_id;
    IPAddress ip;
    IPFromString(ip_string, &ip);
    ipv6_addr->sin6_addr = ip.ipv6_address();
    return ipv6_addr;
  }

  // Pointers created here need to be released via ReleaseIfAddrs.
  struct ifaddrs* AddIpv6Address(struct ifaddrs* list,
                                 char* if_name,
                                 absl::string_view ipv6_address,
                                 absl::string_view ipv6_netmask,
                                 uint32_t scope_id) {
    struct ifaddrs* if_addr = new struct ifaddrs;
    memset(if_addr, 0, sizeof(struct ifaddrs));
    if_addr->ifa_name = if_name;
    if_addr->ifa_addr = reinterpret_cast<struct sockaddr*>(
        CreateIpv6Addr(ipv6_address, scope_id));
    if_addr->ifa_netmask =
        reinterpret_cast<struct sockaddr*>(CreateIpv6Addr(ipv6_netmask, 0));
    if_addr->ifa_next = list;
    if_addr->ifa_flags = IFF_RUNNING;
    return if_addr;
  }

  struct ifaddrs* InstallIpv6Network(char* if_name,
                                     absl::string_view ipv6_address,
                                     absl::string_view ipv6_mask,
                                     BasicNetworkManager& network_manager) {
    ifaddrs* addr_list = nullptr;
    addr_list = AddIpv6Address(addr_list, if_name, ipv6_address, ipv6_mask, 0);
    std::vector<std::unique_ptr<Network>> result;
    bool changed;
    NetworkManager::Stats stats;
    CallConvertIfAddrs(network_manager, addr_list, true, &result);
    network_manager.MergeNetworkList(std::move(result), &changed, &stats);
    return addr_list;
  }

  struct sockaddr_in* CreateIpv4Addr(absl::string_view ip_string) {
    struct sockaddr_in* ipv4_addr =
        static_cast<struct sockaddr_in*>(malloc(sizeof(struct sockaddr_in)));
    memset(ipv4_addr, 0, sizeof(struct sockaddr_in));
    ipv4_addr->sin_family = AF_INET;
    IPAddress ip;
    IPFromString(ip_string, &ip);
    ipv4_addr->sin_addr = ip.ipv4_address();
    return ipv4_addr;
  }

  // Pointers created here need to be released via ReleaseIfAddrs.
  struct ifaddrs* AddIpv4Address(struct ifaddrs* list,
                                 char* if_name,
                                 absl::string_view ipv4_address,
                                 absl::string_view ipv4_netmask) {
    struct ifaddrs* if_addr = new struct ifaddrs;
    memset(if_addr, 0, sizeof(struct ifaddrs));
    if_addr->ifa_name = if_name;
    if_addr->ifa_addr =
        reinterpret_cast<struct sockaddr*>(CreateIpv4Addr(ipv4_address));
    if_addr->ifa_netmask =
        reinterpret_cast<struct sockaddr*>(CreateIpv4Addr(ipv4_netmask));
    if_addr->ifa_next = list;
    if_addr->ifa_flags = IFF_RUNNING;
    return if_addr;
  }

  struct ifaddrs* InstallIpv4Network(char* if_name,
                                     absl::string_view ipv4_address,
                                     absl::string_view ipv4_mask,
                                     BasicNetworkManager& network_manager) {
    ifaddrs* addr_list = nullptr;
    addr_list = AddIpv4Address(addr_list, if_name, ipv4_address, ipv4_mask);
    std::vector<std::unique_ptr<Network>> result;
    bool changed;
    NetworkManager::Stats stats;
    CallConvertIfAddrs(network_manager, addr_list, true, &result);
    network_manager.MergeNetworkList(std::move(result), &changed, &stats);
    return addr_list;
  }

  void ReleaseIfAddrs(struct ifaddrs* list) {
    struct ifaddrs* if_addr = list;
    while (if_addr != nullptr) {
      struct ifaddrs* next_addr = if_addr->ifa_next;
      free(if_addr->ifa_addr);
      free(if_addr->ifa_netmask);
      delete if_addr;
      if_addr = next_addr;
    }
  }
#endif  // defined(WEBRTC_POSIX)

 protected:
  webrtc::test::ScopedKeyValueConfig field_trials_;
  rtc::AutoThread main_thread_;
  bool callback_called_;
};

class TestBasicNetworkManager : public BasicNetworkManager {
 public:
  TestBasicNetworkManager(NetworkMonitorFactory* network_monitor_factory,
                          SocketFactory* socket_factory,
                          const webrtc::FieldTrialsView& field_trials)
      : BasicNetworkManager(network_monitor_factory,
                            socket_factory,
                            &field_trials) {}
  using BasicNetworkManager::QueryDefaultLocalAddress;
  using BasicNetworkManager::set_default_local_addresses;
};

// Test that the Network ctor works properly.
TEST_F(NetworkTest, TestNetworkConstruct) {
  Network ipv4_network1("test_eth0", "Test Network Adapter 1",
                        IPAddress(0x12345600U), 24);
  EXPECT_EQ("test_eth0", ipv4_network1.name());
  EXPECT_EQ("Test Network Adapter 1", ipv4_network1.description());
  EXPECT_EQ(IPAddress(0x12345600U), ipv4_network1.prefix());
  EXPECT_EQ(24, ipv4_network1.prefix_length());
  EXPECT_EQ(AF_INET, ipv4_network1.family());
  EXPECT_FALSE(ipv4_network1.ignored());
}

TEST_F(NetworkTest, TestIsIgnoredNetworkIgnoresIPsStartingWith0) {
  Network ipv4_network1("test_eth0", "Test Network Adapter 1",
                        IPAddress(0x12345600U), 24, ADAPTER_TYPE_ETHERNET);
  Network ipv4_network2("test_eth1", "Test Network Adapter 2",
                        IPAddress(0x010000U), 24, ADAPTER_TYPE_ETHERNET);
  PhysicalSocketServer socket_server;
  BasicNetworkManager network_manager(&socket_server);
  network_manager.StartUpdating();
  EXPECT_FALSE(IsIgnoredNetwork(network_manager, ipv4_network1));
  EXPECT_TRUE(IsIgnoredNetwork(network_manager, ipv4_network2));
}

// TODO(phoglund): Remove when ignore list goes away.
TEST_F(NetworkTest, TestIgnoreList) {
  Network ignore_me("ignore_me", "Ignore me please!", IPAddress(0x12345600U),
                    24);
  Network include_me("include_me", "Include me please!", IPAddress(0x12345600U),
                     24);
  PhysicalSocketServer socket_server;
  BasicNetworkManager default_network_manager(&socket_server);
  default_network_manager.StartUpdating();
  EXPECT_FALSE(IsIgnoredNetwork(default_network_manager, ignore_me));
  EXPECT_FALSE(IsIgnoredNetwork(default_network_manager, include_me));

  BasicNetworkManager ignoring_network_manager(&socket_server);
  std::vector<std::string> ignore_list;
  ignore_list.push_back("ignore_me");
  ignoring_network_manager.set_network_ignore_list(ignore_list);
  ignoring_network_manager.StartUpdating();
  EXPECT_TRUE(IsIgnoredNetwork(ignoring_network_manager, ignore_me));
  EXPECT_FALSE(IsIgnoredNetwork(ignoring_network_manager, include_me));
}

// Test is failing on Windows opt: b/11288214
TEST_F(NetworkTest, DISABLED_TestCreateNetworks) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  std::vector<std::unique_ptr<Network>> result = GetNetworks(manager, true);
  // We should be able to bind to any addresses we find.
  for (auto it = result.begin(); it != result.end(); ++it) {
    sockaddr_storage storage;
    memset(&storage, 0, sizeof(storage));
    IPAddress ip = (*it)->GetBestIP();
    SocketAddress bindaddress(ip, 0);
    bindaddress.SetScopeID((*it)->scope_id());
    // TODO(thaloun): Use rtc::Socket once it supports IPv6.
    int fd = static_cast<int>(socket(ip.family(), SOCK_STREAM, IPPROTO_TCP));
    if (fd > 0) {
      size_t ipsize = bindaddress.ToSockAddrStorage(&storage);
      EXPECT_GE(ipsize, 0U);
      int success = ::bind(fd, reinterpret_cast<sockaddr*>(&storage),
                           static_cast<int>(ipsize));
#if defined(WEBRTC_WIN)
      if (success)
        RTC_LOG_GLE(LS_ERROR) << "Socket bind failed.";
#endif
      EXPECT_EQ(0, success);
#if defined(WEBRTC_WIN)
      closesocket(fd);
#else
      close(fd);
#endif
    }
  }
}

// Test StartUpdating() and StopUpdating(). network_permission_state starts with
// ALLOWED.
TEST_F(NetworkTest, TestUpdateNetworks) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(nullptr, &socket_server, &field_trials_);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  EXPECT_EQ(NetworkManager::ENUMERATION_ALLOWED,
            manager.enumeration_permission());
  manager.StartUpdating();
  Thread::Current()->ProcessMessages(0);
  EXPECT_TRUE(callback_called_);
  callback_called_ = false;
  // Callback should be triggered immediately when StartUpdating
  // is called, after network update signal is already sent.
  manager.StartUpdating();
  EXPECT_TRUE(manager.started());
  Thread::Current()->ProcessMessages(0);
  EXPECT_TRUE(callback_called_);
  manager.StopUpdating();
  EXPECT_TRUE(manager.started());
  manager.StopUpdating();
  EXPECT_EQ(NetworkManager::ENUMERATION_ALLOWED,
            manager.enumeration_permission());
  EXPECT_FALSE(manager.started());
  manager.StopUpdating();
  EXPECT_FALSE(manager.started());
  callback_called_ = false;
  // Callback should be triggered immediately after StartUpdating is called
  // when start_count_ is reset to 0.
  manager.StartUpdating();
  Thread::Current()->ProcessMessages(0);
  EXPECT_TRUE(callback_called_);
}

// Verify that MergeNetworkList() merges network lists properly.
TEST_F(NetworkTest, TestBasicMergeNetworkList) {
  Network ipv4_network1("test_eth0", "Test Network Adapter 1",
                        IPAddress(0x12345600U), 24);
  Network ipv4_network2("test_eth1", "Test Network Adapter 2",
                        IPAddress(0x00010000U), 16);
  ipv4_network1.AddIP(IPAddress(0x12345678));
  ipv4_network2.AddIP(IPAddress(0x00010004));
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);

  // Add ipv4_network1 to the list of networks.
  std::vector<std::unique_ptr<Network>> list;
  list.push_back(std::make_unique<Network>(ipv4_network1));
  bool changed;
  NetworkManager::Stats stats =
      MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  EXPECT_EQ(stats.ipv6_network_count, 0);
  EXPECT_EQ(stats.ipv4_network_count, 1);
  list.clear();  // It is fine to call .clear() on a moved-from vector.

  std::vector<const rtc::Network*> current = manager.GetNetworks();
  EXPECT_EQ(1U, current.size());
  EXPECT_TRUE(SameNameAndPrefix(ipv4_network1, *current[0]));
  const Network* net1 = current[0];
  uint16_t net_id1 = net1->id();
  EXPECT_EQ(1, net_id1);

  // Replace ipv4_network1 with ipv4_network2.
  list.push_back(std::make_unique<Network>(ipv4_network2));
  stats = MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  EXPECT_EQ(stats.ipv6_network_count, 0);
  EXPECT_EQ(stats.ipv4_network_count, 1);
  list.clear();

  current = manager.GetNetworks();
  EXPECT_EQ(1U, current.size());
  EXPECT_TRUE(SameNameAndPrefix(ipv4_network2, *current[0]));
  const Network* net2 = current[0];
  uint16_t net_id2 = net2->id();
  // Network id will increase.
  EXPECT_LT(net_id1, net_id2);

  // Add Network2 back.
  list.push_back(std::make_unique<Network>(ipv4_network1));
  list.push_back(std::make_unique<Network>(ipv4_network2));
  stats = MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  EXPECT_EQ(stats.ipv6_network_count, 0);
  EXPECT_EQ(stats.ipv4_network_count, 2);
  list.clear();

  // Verify that we get previous instances of Network objects.
  current = manager.GetNetworks();
  EXPECT_EQ(2U, current.size());
  EXPECT_TRUE((net1 == current[0] && net2 == current[1]) ||
              (net1 == current[1] && net2 == current[0]));
  EXPECT_TRUE((net_id1 == current[0]->id() && net_id2 == current[1]->id()) ||
              (net_id1 == current[1]->id() && net_id2 == current[0]->id()));

  // Call MergeNetworkList() again and verify that we don't get update
  // notification.
  list.push_back(std::make_unique<Network>(ipv4_network2));
  list.push_back(std::make_unique<Network>(ipv4_network1));
  stats = MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_FALSE(changed);
  EXPECT_EQ(stats.ipv6_network_count, 0);
  EXPECT_EQ(stats.ipv4_network_count, 2);
  list.clear();

  // Verify that we get previous instances of Network objects.
  current = manager.GetNetworks();
  EXPECT_EQ(2U, current.size());
  EXPECT_TRUE((net1 == current[0] && net2 == current[1]) ||
              (net1 == current[1] && net2 == current[0]));
  EXPECT_TRUE((net_id1 == current[0]->id() && net_id2 == current[1]->id()) ||
              (net_id1 == current[1]->id() && net_id2 == current[0]->id()));
}

// Sets up some test IPv6 networks and appends them to list.
// Four networks are added - public and link local, for two interfaces.
void SetupNetworks(std::vector<std::unique_ptr<Network>>* list) {
  IPAddress ip;
  IPAddress prefix;
  EXPECT_TRUE(IPFromString("abcd::1234:5678:abcd:ef12", &ip));
  EXPECT_TRUE(IPFromString("abcd::", &prefix));
  // First, fake link-locals.
  Network ipv6_eth0_linklocalnetwork("test_eth0", "Test NetworkAdapter 1",
                                     prefix, 64);
  ipv6_eth0_linklocalnetwork.AddIP(ip);
  EXPECT_TRUE(IPFromString("abcd::5678:abcd:ef12:3456", &ip));
  Network ipv6_eth1_linklocalnetwork("test_eth1", "Test NetworkAdapter 2",
                                     prefix, 64);
  ipv6_eth1_linklocalnetwork.AddIP(ip);
  // Public networks:
  EXPECT_TRUE(IPFromString("2401:fa00:4:1000:be30:5bff:fee5:c3", &ip));
  prefix = TruncateIP(ip, 64);
  Network ipv6_eth0_publicnetwork1_ip1("test_eth0", "Test NetworkAdapter 1",
                                       prefix, 64);
  ipv6_eth0_publicnetwork1_ip1.AddIP(ip);
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:abcd:efab:cdef", &ip));
  prefix = TruncateIP(ip, 64);
  Network ipv6_eth1_publicnetwork1_ip1("test_eth1", "Test NetworkAdapter 1",
                                       prefix, 64);
  ipv6_eth1_publicnetwork1_ip1.AddIP(ip);
  list->push_back(std::make_unique<Network>(ipv6_eth0_linklocalnetwork));
  list->push_back(std::make_unique<Network>(ipv6_eth1_linklocalnetwork));
  list->push_back(std::make_unique<Network>(ipv6_eth0_publicnetwork1_ip1));
  list->push_back(std::make_unique<Network>(ipv6_eth1_publicnetwork1_ip1));
}

// Test that the basic network merging case works.
TEST_F(NetworkTest, TestIPv6MergeNetworkList) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  std::vector<std::unique_ptr<Network>> networks;
  SetupNetworks(&networks);
  std::vector<const Network*> original_list = CopyNetworkPointers(networks);
  bool changed = false;
  NetworkManager::Stats stats =
      MergeNetworkList(manager, std::move(networks), &changed);
  EXPECT_TRUE(changed);
  EXPECT_EQ(stats.ipv6_network_count, 4);
  EXPECT_EQ(stats.ipv4_network_count, 0);
  std::vector<const Network*> list = manager.GetNetworks();
  // Verify that the original members are in the merged list.
  EXPECT_THAT(list, UnorderedElementsAreArray(original_list));
}

// Tests that when two network lists that describe the same set of networks are
// merged, that the changed callback is not called, and that the original
// objects remain in the result list.
TEST_F(NetworkTest, TestNoChangeMerge) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  std::vector<std::unique_ptr<Network>> networks;
  SetupNetworks(&networks);
  std::vector<const Network*> original_list = CopyNetworkPointers(networks);
  bool changed = false;
  MergeNetworkList(manager, std::move(networks), &changed);
  EXPECT_TRUE(changed);
  // Second list that describes the same networks but with new objects.
  std::vector<std::unique_ptr<Network>> second_networks;
  SetupNetworks(&second_networks);
  std::vector<const Network*> second_list =
      CopyNetworkPointers(second_networks);
  changed = false;
  MergeNetworkList(manager, std::move(second_networks), &changed);
  EXPECT_FALSE(changed);
  std::vector<const Network*> resulting_list = manager.GetNetworks();
  // Verify that the original members are in the merged list.
  EXPECT_THAT(resulting_list, UnorderedElementsAreArray(original_list));
  // Doublecheck that the new networks aren't in the list.
  for (const Network* network : second_list) {
    EXPECT_THAT(resulting_list, Not(Contains(network)));
  }
}

// Test that we can merge a network that is the same as another network but with
// a different IP. The original network should remain in the list, but have its
// IP changed.
TEST_F(NetworkTest, MergeWithChangedIP) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  std::vector<std::unique_ptr<Network>> original_list;
  SetupNetworks(&original_list);
  // Make a network that we're going to change.
  IPAddress ip;
  EXPECT_TRUE(IPFromString("2401:fa01:4:1000:be30:faa:fee:faa", &ip));
  IPAddress prefix = TruncateIP(ip, 64);
  std::unique_ptr<Network> network_to_change = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", prefix, 64);
  std::unique_ptr<Network> changed_network =
      std::make_unique<Network>(*network_to_change);
  network_to_change->AddIP(ip);
  IPAddress changed_ip;
  EXPECT_TRUE(IPFromString("2401:fa01:4:1000:be30:f00:f00:f00", &changed_ip));
  changed_network->AddIP(changed_ip);
  const Network* const network_to_change_ptr = network_to_change.get();
  original_list.push_back(std::move(network_to_change));
  const size_t original_size = original_list.size();
  bool changed = false;
  MergeNetworkList(manager, std::move(original_list), &changed);
  std::vector<std::unique_ptr<Network>> second_list;
  SetupNetworks(&second_list);
  second_list.push_back(std::move(changed_network));
  changed = false;
  MergeNetworkList(manager, std::move(second_list), &changed);
  EXPECT_TRUE(changed);
  std::vector<const Network*> list = manager.GetNetworks();
  EXPECT_EQ(original_size, list.size());
  // Make sure the original network is still in the merged list.
  EXPECT_THAT(list, Contains(network_to_change_ptr));
  EXPECT_EQ(changed_ip, network_to_change_ptr->GetIPs().at(0));
}

TEST_F(NetworkTest, TestMultipleIPMergeNetworkList) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  std::vector<std::unique_ptr<Network>> original_list;
  SetupNetworks(&original_list);
  const Network* const network_ptr = original_list[2].get();
  bool changed = false;
  MergeNetworkList(manager, std::move(original_list), &changed);
  EXPECT_TRUE(changed);
  IPAddress ip;
  IPAddress check_ip;
  IPAddress prefix;
  // Add a second IP to the public network on eth0 (2401:fa00:4:1000/64).
  EXPECT_TRUE(IPFromString("2401:fa00:4:1000:be30:5bff:fee5:c6", &ip));
  prefix = TruncateIP(ip, 64);
  Network ipv6_eth0_publicnetwork1_ip2("test_eth0", "Test NetworkAdapter 1",
                                       prefix, 64);
  // This is the IP that already existed in the public network on eth0.
  EXPECT_TRUE(IPFromString("2401:fa00:4:1000:be30:5bff:fee5:c3", &check_ip));
  ipv6_eth0_publicnetwork1_ip2.AddIP(ip);

  std::vector<std::unique_ptr<Network>> second_list;
  SetupNetworks(&second_list);
  second_list.push_back(
      std::make_unique<Network>(ipv6_eth0_publicnetwork1_ip2));
  changed = false;
  const auto network_copy = std::make_unique<Network>(*second_list[2]);
  MergeNetworkList(manager, std::move(second_list), &changed);
  EXPECT_TRUE(changed);
  // There should still be four networks.
  std::vector<const Network*> list = manager.GetNetworks();
  EXPECT_EQ(4U, list.size());
  // Check the gathered IPs.
  int matchcount = 0;
  for (const Network* network : list) {
    if (SameNameAndPrefix(*network, *network_copy)) {
      ++matchcount;
      EXPECT_EQ(1, matchcount);
      // This should be the same network object as before.
      EXPECT_EQ(network, network_ptr);
      // But with two addresses now.
      EXPECT_THAT(network->GetIPs(),
                  UnorderedElementsAre(InterfaceAddress(check_ip),
                                       InterfaceAddress(ip)));
    } else {
      // Check the IP didn't get added anywhere it wasn't supposed to.
      EXPECT_THAT(network->GetIPs(), Not(Contains(InterfaceAddress(ip))));
    }
  }
}

// Test that merge correctly distinguishes multiple networks on an interface.
TEST_F(NetworkTest, TestMultiplePublicNetworksOnOneInterfaceMerge) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  std::vector<std::unique_ptr<Network>> original_list;
  SetupNetworks(&original_list);
  bool changed = false;
  MergeNetworkList(manager, std::move(original_list), &changed);
  EXPECT_TRUE(changed);
  IPAddress ip;
  IPAddress prefix;
  // A second network for eth0.
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:5bff:fee5:c3", &ip));
  prefix = TruncateIP(ip, 64);
  Network ipv6_eth0_publicnetwork2_ip1("test_eth0", "Test NetworkAdapter 1",
                                       prefix, 64);
  ipv6_eth0_publicnetwork2_ip1.AddIP(ip);
  std::vector<std::unique_ptr<Network>> second_list;
  SetupNetworks(&second_list);
  second_list.push_back(
      std::make_unique<Network>(ipv6_eth0_publicnetwork2_ip1));
  changed = false;
  MergeNetworkList(manager, std::move(second_list), &changed);
  EXPECT_TRUE(changed);
  // There should be five networks now.
  std::vector<const Network*> list = manager.GetNetworks();
  EXPECT_EQ(5U, list.size());
  // Check the resulting addresses.
  for (const Network* network : list) {
    if (network->prefix() == ipv6_eth0_publicnetwork2_ip1.prefix() &&
        network->name() == ipv6_eth0_publicnetwork2_ip1.name()) {
      // Check the new network has 1 IP and that it's the correct one.
      EXPECT_EQ(1U, network->GetIPs().size());
      EXPECT_EQ(ip, network->GetIPs().at(0));
    } else {
      // Check the IP didn't get added anywhere it wasn't supposed to.
      EXPECT_THAT(network->GetIPs(), Not(Contains(InterfaceAddress(ip))));
    }
  }
}

// Test that DumpNetworks does not crash.
TEST_F(NetworkTest, TestCreateAndDumpNetworks) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  std::vector<std::unique_ptr<Network>> list = GetNetworks(manager, true);
  bool changed;
  MergeNetworkList(manager, std::move(list), &changed);
  manager.DumpNetworks();
}

TEST_F(NetworkTest, TestIPv6Toggle) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  bool ipv6_found = false;
  for (const auto& network : GetNetworks(manager, true)) {
    if (network->prefix().family() == AF_INET6) {
      ipv6_found = true;
      break;
    }
  }
  EXPECT_TRUE(ipv6_found);
}

// Test that when network interfaces are sorted and given preference values,
// IPv6 comes first.
TEST_F(NetworkTest, IPv6NetworksPreferredOverIPv4) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  Network ipv4_network1("test_eth0", "Test Network Adapter 1",
                        IPAddress(0x12345600U), 24);
  ipv4_network1.AddIP(IPAddress(0x12345600U));

  IPAddress ip;
  IPAddress prefix;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:abcd:efab:cdef", &ip));
  prefix = TruncateIP(ip, 64);
  Network ipv6_eth1_publicnetwork1_ip1("test_eth1", "Test NetworkAdapter 2",
                                       prefix, 64);
  ipv6_eth1_publicnetwork1_ip1.AddIP(ip);

  std::vector<std::unique_ptr<Network>> list;
  list.push_back(std::make_unique<Network>(ipv4_network1));
  list.push_back(std::make_unique<Network>(ipv6_eth1_publicnetwork1_ip1));
  const Network* net1 = list[0].get();
  const Network* net2 = list[1].get();

  bool changed = false;
  MergeNetworkList(manager, std::move(list), &changed);
  ASSERT_TRUE(changed);
  // After sorting IPv6 network should be higher order than IPv4 networks.
  EXPECT_TRUE(net1->preference() < net2->preference());
}

// When two interfaces are equivalent in everything but name, they're expected
// to be preference-ordered by name. For example, "eth0" before "eth1".
TEST_F(NetworkTest, NetworksSortedByInterfaceName) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server, &field_trials_);
  auto eth0 = std::make_unique<Network>("test_eth0", "Test Network Adapter 1",
                                        IPAddress(0x65432100U), 24);
  eth0->AddIP(IPAddress(0x65432100U));
  auto eth1 = std::make_unique<Network>("test_eth1", "Test Network Adapter 2",
                                        IPAddress(0x12345600U), 24);
  eth1->AddIP(IPAddress(0x12345600U));
  std::vector<std::unique_ptr<Network>> list;
  const Network* eth0_ptr = eth0.get();
  const Network* eth1_ptr = eth1.get();
  // Add them to the list in the opposite of the expected sorted order, to
  // ensure sorting actually occurs.
  list.push_back(std::move(eth1));
  list.push_back(std::move(eth0));

  bool changed = false;
  MergeNetworkList(manager, std::move(list), &changed);
  ASSERT_TRUE(changed);
  // "test_eth0" should be preferred over "test_eth1".
  EXPECT_TRUE(eth0_ptr->preference() > eth1_ptr->preference());
}

TEST_F(NetworkTest, TestNetworkAdapterTypes) {
  Network wifi("wlan0", "Wireless Adapter", IPAddress(0x12345600U), 24,
               ADAPTER_TYPE_WIFI);
  EXPECT_EQ(ADAPTER_TYPE_WIFI, wifi.type());
  Network ethernet("eth0", "Ethernet", IPAddress(0x12345600U), 24,
                   ADAPTER_TYPE_ETHERNET);
  EXPECT_EQ(ADAPTER_TYPE_ETHERNET, ethernet.type());
  Network cellular("test_cell", "Cellular Adapter", IPAddress(0x12345600U), 24,
                   ADAPTER_TYPE_CELLULAR);
  EXPECT_EQ(ADAPTER_TYPE_CELLULAR, cellular.type());
  Network vpn("bridge_test", "VPN Adapter", IPAddress(0x12345600U), 24,
              ADAPTER_TYPE_VPN);
  EXPECT_EQ(ADAPTER_TYPE_VPN, vpn.type());
  Network unknown("test", "Test Adapter", IPAddress(0x12345600U), 24,
                  ADAPTER_TYPE_UNKNOWN);
  EXPECT_EQ(ADAPTER_TYPE_UNKNOWN, unknown.type());
}

#if defined(WEBRTC_POSIX)
// Verify that we correctly handle interfaces with no address.
TEST_F(NetworkTest, TestConvertIfAddrsNoAddress) {
  ifaddrs list;
  memset(&list, 0, sizeof(list));
  list.ifa_name = const_cast<char*>("test_iface");

  std::vector<std::unique_ptr<Network>> result;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, &list, true, &result);
  EXPECT_TRUE(result.empty());
}

// Verify that if there are two addresses on one interface, only one network
// is generated.
TEST_F(NetworkTest, TestConvertIfAddrsMultiAddressesOnOneInterface) {
  char if_name[20] = "rmnet0";
  ifaddrs* list = nullptr;
  list = AddIpv6Address(list, if_name, "1000:2000:3000:4000:0:0:0:1",
                        "FFFF:FFFF:FFFF:FFFF::", 0);
  list = AddIpv6Address(list, if_name, "1000:2000:3000:4000:0:0:0:2",
                        "FFFF:FFFF:FFFF:FFFF::", 0);
  std::vector<std::unique_ptr<Network>> result;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, list, true, &result);
  EXPECT_EQ(1U, result.size());
  bool changed;
  // This ensures we release the objects created in CallConvertIfAddrs.
  MergeNetworkList(manager, std::move(result), &changed);
  ReleaseIfAddrs(list);
}

TEST_F(NetworkTest, TestConvertIfAddrsNotRunning) {
  ifaddrs list;
  memset(&list, 0, sizeof(list));
  list.ifa_name = const_cast<char*>("test_iface");
  sockaddr ifa_addr;
  ifa_addr.sa_family = AF_UNSPEC;
  sockaddr ifa_netmask;
  list.ifa_addr = &ifa_addr;
  list.ifa_netmask = &ifa_netmask;

  std::vector<std::unique_ptr<Network>> result;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, &list, true, &result);
  EXPECT_TRUE(result.empty());
}

TEST_F(NetworkTest, TestConvertIfAddrsGetsNullAddr) {
  ifaddrs list;
  memset(&list, 0, sizeof(list));
  list.ifa_name = const_cast<char*>("test_iface");
  list.ifa_addr = nullptr;
  list.ifa_netmask = nullptr;

  std::vector<std::unique_ptr<Network>> result;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, &list, true, &result);
  EXPECT_TRUE(result.empty());
}

// Tests that the network type can be determined from the network monitor when
// it would otherwise be unknown.
TEST_F(NetworkTest, TestGetAdapterTypeFromNetworkMonitor) {
  char if_name[20] = "wifi0";
  std::string ipv6_address = "1000:2000:3000:4000:0:0:0:1";
  std::string ipv6_mask = "FFFF:FFFF:FFFF:FFFF::";
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager_without_monitor(nullptr, &socket_server,
                                              &field_trials_);
  manager_without_monitor.StartUpdating();
  // A network created without a network monitor will get UNKNOWN type.
  ifaddrs* addr_list = InstallIpv6Network(if_name, ipv6_address, ipv6_mask,
                                          manager_without_monitor);
  EXPECT_EQ(ADAPTER_TYPE_UNKNOWN, GetAdapterType(manager_without_monitor));
  ReleaseIfAddrs(addr_list);

  // With the fake network monitor the type should be correctly determined.
  FakeNetworkMonitorFactory factory;
  BasicNetworkManager manager_with_monitor(&factory, &socket_server,
                                           &field_trials_);
  manager_with_monitor.StartUpdating();
  // Add the same ipv6 address as before but it has the right network type
  // detected by the network monitor now.
  addr_list = InstallIpv6Network(if_name, ipv6_address, ipv6_mask,
                                 manager_with_monitor);
  EXPECT_EQ(ADAPTER_TYPE_WIFI, GetAdapterType(manager_with_monitor));
  ReleaseIfAddrs(addr_list);
}

// Test that the network type can be determined based on name matching in
// a few cases. Note that UNKNOWN type for non-matching strings has been tested
// in the above test.
TEST_F(NetworkTest, TestGetAdapterTypeFromNameMatching) {
  std::string ipv4_address1 = "192.0.0.121";
  std::string ipv4_mask = "255.255.255.0";
  std::string ipv6_address1 = "1000:2000:3000:4000:0:0:0:1";
  std::string ipv6_address2 = "1000:2000:3000:8000:0:0:0:1";
  std::string ipv6_mask = "FFFF:FFFF:FFFF:FFFF::";
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();

  // IPSec interface; name is in form "ipsec<index>".
  char if_name[20] = "ipsec11";
  ifaddrs* addr_list =
      InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_VPN, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "lo0");
  addr_list = InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_LOOPBACK, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "eth0");
  addr_list = InstallIpv4Network(if_name, ipv4_address1, ipv4_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_ETHERNET, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "wlan0");
  addr_list = InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_WIFI, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

#if defined(WEBRTC_IOS)
  strcpy(if_name, "pdp_ip0");
  addr_list = InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_CELLULAR, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "en0");
  addr_list = InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_WIFI, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

#elif defined(WEBRTC_ANDROID)
  strcpy(if_name, "rmnet0");
  addr_list = InstallIpv6Network(if_name, ipv6_address1, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_CELLULAR, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "v4-rmnet_data0");
  addr_list = InstallIpv6Network(if_name, ipv6_address2, ipv6_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_CELLULAR, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);

  strcpy(if_name, "clat4");
  addr_list = InstallIpv4Network(if_name, ipv4_address1, ipv4_mask, manager);
  EXPECT_EQ(ADAPTER_TYPE_CELLULAR, GetAdapterType(manager));
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);
#endif
}

// Test that an adapter won't be included in the network list if there's a
// network monitor that says it's unavailable.
TEST_F(NetworkTest, TestNetworkMonitorIsAdapterAvailable) {
  char if_name1[20] = "pdp_ip0";
  char if_name2[20] = "pdp_ip1";
  ifaddrs* list = nullptr;
  list = AddIpv6Address(list, if_name1, "1000:2000:3000:4000:0:0:0:1",
                        "FFFF:FFFF:FFFF:FFFF::", 0);
  list = AddIpv6Address(list, if_name2, "1000:2000:3000:4000:0:0:0:2",
                        "FFFF:FFFF:FFFF:FFFF::", 0);
  std::vector<std::unique_ptr<Network>> result;

  // Sanity check that both interfaces are included by default.
  FakeNetworkMonitorFactory factory;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&factory, &socket_server, &field_trials_);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, list, /*include_ignored=*/false, &result);
  EXPECT_EQ(2u, result.size());
  bool changed;
  // This ensures we release the objects created in CallConvertIfAddrs.
  MergeNetworkList(manager, std::move(result), &changed);
  result.clear();

  // Now simulate one interface being unavailable.
  FakeNetworkMonitor* network_monitor = GetNetworkMonitor(manager);
  network_monitor->set_unavailable_adapters({if_name1});
  CallConvertIfAddrs(manager, list, /*include_ignored=*/false, &result);
  EXPECT_EQ(1u, result.size());
  EXPECT_EQ(if_name2, result[0]->name());

  MergeNetworkList(manager, std::move(result), &changed);
  ReleaseIfAddrs(list);
}

#endif  // defined(WEBRTC_POSIX)

// Test MergeNetworkList successfully combines all IPs for the same
// prefix/length into a single Network.
TEST_F(NetworkTest, TestMergeNetworkList) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  std::vector<std::unique_ptr<Network>> list;

  // Create 2 IPAddress classes with only last digit different.
  IPAddress ip1, ip2;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:1", &ip1));
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:2", &ip2));

  // Create 2 networks with the same prefix and length.
  auto net1 = std::make_unique<Network>("em1", "em1", TruncateIP(ip1, 64), 64);
  auto net2 = std::make_unique<Network>("em1", "em1", TruncateIP(ip1, 64), 64);

  // Add different IP into each.
  net1->AddIP(ip1);
  net2->AddIP(ip2);

  list.push_back(std::move(net1));
  list.push_back(std::move(net2));
  bool changed;
  MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);

  std::vector<const Network*> list2 = manager.GetNetworks();

  // Make sure the resulted networklist has only 1 element and 2
  // IPAddresses.
  EXPECT_EQ(list2.size(), 1uL);
  EXPECT_EQ(list2[0]->GetIPs().size(), 2uL);
  EXPECT_THAT(list2[0]->GetIPs(), UnorderedElementsAre(InterfaceAddress(ip1),
                                                       InterfaceAddress(ip2)));
}

// Test that MergeNetworkList successfully detects the change if
// a network becomes inactive and then active again.
TEST_F(NetworkTest, TestMergeNetworkListWithInactiveNetworks) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  Network network1("test_wifi", "Test Network Adapter 1",
                   IPAddress(0x12345600U), 24);
  Network network2("test_eth0", "Test Network Adapter 2",
                   IPAddress(0x00010000U), 16);
  network1.AddIP(IPAddress(0x12345678));
  network2.AddIP(IPAddress(0x00010004));
  std::vector<std::unique_ptr<Network>> list;
  auto net1 = std::make_unique<Network>(network1);
  const Network* const net1_ptr = net1.get();
  list.push_back(std::move(net1));
  bool changed;
  MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  list.clear();

  std::vector<const Network*> current = manager.GetNetworks();
  ASSERT_EQ(1U, current.size());
  EXPECT_EQ(net1_ptr, current[0]);

  list.clear();
  auto net2 = std::make_unique<Network>(network2);
  const Network* const net2_ptr = net2.get();
  list.push_back(std::move(net2));
  MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  list.clear();

  current = manager.GetNetworks();
  ASSERT_EQ(1U, current.size());
  EXPECT_EQ(net2_ptr, current[0]);
  // Now network1 is inactive. Try to merge it again.
  list.clear();
  list.push_back(std::make_unique<Network>(network1));
  MergeNetworkList(manager, std::move(list), &changed);
  EXPECT_TRUE(changed);
  list.clear();
  current = manager.GetNetworks();
  ASSERT_EQ(1U, current.size());
  EXPECT_TRUE(current[0]->active());
  EXPECT_EQ(net1_ptr, current[0]);
}

// Test that the filtering logic follows the defined ruleset in network.h.
TEST_F(NetworkTest, TestIPv6Selection) {
  InterfaceAddress ip;
  std::string ipstr;

  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c3";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_DEPRECATED, &ip));

  // Create a network with this prefix.
  Network ipv6_network("test_eth0", "Test NetworkAdapter", TruncateIP(ip, 64),
                       64);
  EXPECT_EQ(AF_INET6, ipv6_network.family());

  // When there is no address added, it should return an unspecified
  // address.
  EXPECT_EQ(ipv6_network.GetBestIP(), IPAddress());
  EXPECT_TRUE(IPIsUnspec(ipv6_network.GetBestIP()));

  // Deprecated one should not be returned.
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), IPAddress());

  // Add ULA one. ULA is unique local address which is starting either
  // with 0xfc or 0xfd.
  ipstr = "fd00:fa00:4:1000:be30:5bff:fee5:c4";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add global one.
  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c5";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add global dynamic temporary one.
  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c6";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_TEMPORARY, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));
}

// Test that the filtering logic follows the defined ruleset in network.h.
TEST_F(NetworkTest, TestGetBestIPWithPreferGlobalIPv6ToLinkLocalEnabled) {
  InterfaceAddress ip, link_local;
  std::string ipstr;

  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c3";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_DEPRECATED, &ip));

  // Create a network with this prefix.
  Network ipv6_network("test_eth0", "Test NetworkAdapter", TruncateIP(ip, 64),
                       64, ADAPTER_TYPE_UNKNOWN);

  // When there is no address added, it should return an unspecified
  // address.
  EXPECT_EQ(ipv6_network.GetBestIP(), IPAddress());
  EXPECT_TRUE(IPIsUnspec(ipv6_network.GetBestIP()));

  // Deprecated one should not be returned.
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), IPAddress());

  // Add ULA one. ULA is unique local address which is starting either
  // with 0xfc or 0xfd.
  ipstr = "fd00:fa00:4:1000:be30:5bff:fee5:c4";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add link local one.
  ipstr = "fe80::aabb:ccff:fedd:eeff";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &link_local));
  ipv6_network.AddIP(link_local);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(link_local));

  // Add global one.
  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c5";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add another link local address, then the compatible address is still global
  // one.
  ipstr = "fe80::aabb:ccff:fedd:eedd";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &link_local));
  ipv6_network.AddIP(link_local);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add global dynamic temporary one.
  ipstr = "2401:fa00:4:1000:be30:5bff:fee5:c6";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_TEMPORARY, &ip));
  ipv6_network.AddIP(ip);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));

  // Add another link local address, then the compatible address is still global
  // dynamic one.
  ipstr = "fe80::aabb:ccff:fedd:eedd";
  ASSERT_TRUE(IPFromString(ipstr, IPV6_ADDRESS_FLAG_NONE, &link_local));
  ipv6_network.AddIP(link_local);
  EXPECT_EQ(ipv6_network.GetBestIP(), static_cast<IPAddress>(ip));
}

TEST_F(NetworkTest, TestNetworkMonitoring) {
  FakeNetworkMonitorFactory factory;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&factory, &socket_server, &field_trials_);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  manager.StartUpdating();
  FakeNetworkMonitor* network_monitor = GetNetworkMonitor(manager);
  EXPECT_TRUE(network_monitor && network_monitor->started());
  EXPECT_TRUE_WAIT(callback_called_, 1000);
  callback_called_ = false;

  // Clear the networks so that there will be network changes below.
  ClearNetworks(manager);
  // Network manager is started, so the callback is called when the network
  // monitor fires the network-change event.
  network_monitor->InovkeNetworksChangedCallbackForTesting();
  EXPECT_TRUE_WAIT(callback_called_, 1000);

  // Network manager is stopped.
  manager.StopUpdating();
  EXPECT_FALSE(GetNetworkMonitor(manager)->started());
}

// Fails on Android: https://bugs.chromium.org/p/webrtc/issues/detail?id=4364.
#if defined(WEBRTC_ANDROID)
#define MAYBE_DefaultLocalAddress DISABLED_DefaultLocalAddress
#else
#define MAYBE_DefaultLocalAddress DefaultLocalAddress
#endif
TEST_F(NetworkTest, MAYBE_DefaultLocalAddress) {
  MAYBE_SKIP_IPV4;
  IPAddress ip;
  FakeNetworkMonitorFactory factory;
  PhysicalSocketServer socket_server;
  TestBasicNetworkManager manager(&factory, &socket_server, field_trials_);
  manager.SignalNetworksChanged.connect(static_cast<NetworkTest*>(this),
                                        &NetworkTest::OnNetworksChanged);
  manager.StartUpdating();
  EXPECT_TRUE_WAIT(callback_called_, 1000);

  // Make sure we can query default local address when an address for such
  // address family exists.
  std::vector<const Network*> networks = manager.GetNetworks();
  EXPECT_TRUE(!networks.empty());
  for (const Network* network : networks) {
    if (network->GetBestIP().family() == AF_INET) {
      EXPECT_TRUE(QueryDefaultLocalAddress(manager, AF_INET) != IPAddress());
    } else if (network->GetBestIP().family() == AF_INET6 &&
               !IPIsLoopback(network->GetBestIP())) {
      // Existence of an IPv6 loopback address doesn't mean it has IPv6 network
      // enabled.
      EXPECT_TRUE(QueryDefaultLocalAddress(manager, AF_INET6) != IPAddress());
    }
  }

  // GetDefaultLocalAddress should return the valid default address after set.
  manager.set_default_local_addresses(GetLoopbackIP(AF_INET),
                                      GetLoopbackIP(AF_INET6));
  EXPECT_TRUE(manager.GetDefaultLocalAddress(AF_INET, &ip));
  EXPECT_EQ(ip, GetLoopbackIP(AF_INET));
  EXPECT_TRUE(manager.GetDefaultLocalAddress(AF_INET6, &ip));
  EXPECT_EQ(ip, GetLoopbackIP(AF_INET6));

  // More tests on GetDefaultLocalAddress with ipv6 addresses where the set
  // default address may be different from the best IP address of any network.
  InterfaceAddress ip1;
  EXPECT_TRUE(IPFromString("abcd::1234:5678:abcd:1111",
                           IPV6_ADDRESS_FLAG_TEMPORARY, &ip1));
  // Create a network with a prefix of ip1.
  Network ipv6_network("test_eth0", "Test NetworkAdapter", TruncateIP(ip1, 64),
                       64);
  IPAddress ip2;
  EXPECT_TRUE(IPFromString("abcd::1234:5678:abcd:2222", &ip2));
  ipv6_network.AddIP(ip1);
  ipv6_network.AddIP(ip2);
  std::vector<std::unique_ptr<Network>> list;
  list.push_back(std::make_unique<Network>(ipv6_network));
  bool changed;
  MergeNetworkList(manager, std::move(list), &changed);
  // If the set default address is not in any network, GetDefaultLocalAddress
  // should return it.
  IPAddress ip3;
  EXPECT_TRUE(IPFromString("abcd::1234:5678:abcd:3333", &ip3));
  manager.set_default_local_addresses(GetLoopbackIP(AF_INET), ip3);
  EXPECT_TRUE(manager.GetDefaultLocalAddress(AF_INET6, &ip));
  EXPECT_EQ(ip3, ip);
  // If the set default address is in a network, GetDefaultLocalAddress will
  // return the best IP in that network.
  manager.set_default_local_addresses(GetLoopbackIP(AF_INET), ip2);
  EXPECT_TRUE(manager.GetDefaultLocalAddress(AF_INET6, &ip));
  EXPECT_EQ(static_cast<IPAddress>(ip1), ip);

  manager.StopUpdating();
}

// Test that MergeNetworkList does not set change = true
// when changing from cellular_X to cellular_Y.
TEST_F(NetworkTest, TestWhenNetworkListChangeReturnsChangedFlag) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);

  IPAddress ip1;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:1", &ip1));
  auto net1 = std::make_unique<Network>("em1", "em1", TruncateIP(ip1, 64), 64);
  net1->set_type(ADAPTER_TYPE_CELLULAR_3G);
  net1->AddIP(ip1);
  std::vector<std::unique_ptr<Network>> list;
  list.push_back(std::move(net1));

  {
    bool changed;
    MergeNetworkList(manager, std::move(list), &changed);
    EXPECT_TRUE(changed);
    std::vector<const Network*> list2 = manager.GetNetworks();
    EXPECT_EQ(list2.size(), 1uL);
    EXPECT_EQ(ADAPTER_TYPE_CELLULAR_3G, list2[0]->type());
  }

  // Modify net1 from 3G to 4G
  {
    auto net2 =
        std::make_unique<Network>("em1", "em1", TruncateIP(ip1, 64), 64);
    net2->set_type(ADAPTER_TYPE_CELLULAR_4G);
    net2->AddIP(ip1);
    list.clear();
    list.push_back(std::move(net2));
    bool changed;
    MergeNetworkList(manager, std::move(list), &changed);

    // Change from 3G to 4G shall not trigger OnNetworksChanged,
    // i.e changed = false.
    EXPECT_FALSE(changed);
    std::vector<const Network*> list2 = manager.GetNetworks();
    ASSERT_EQ(list2.size(), 1uL);
    EXPECT_EQ(ADAPTER_TYPE_CELLULAR_4G, list2[0]->type());
  }

  // Don't modify.
  {
    auto net2 =
        std::make_unique<Network>("em1", "em1", TruncateIP(ip1, 64), 64);
    net2->set_type(ADAPTER_TYPE_CELLULAR_4G);
    net2->AddIP(ip1);
    list.clear();
    list.push_back(std::move(net2));
    bool changed;
    MergeNetworkList(manager, std::move(list), &changed);

    // No change.
    EXPECT_FALSE(changed);
    std::vector<const Network*> list2 = manager.GetNetworks();
    ASSERT_EQ(list2.size(), 1uL);
    EXPECT_EQ(ADAPTER_TYPE_CELLULAR_4G, list2[0]->type());
  }
}

#if defined(WEBRTC_POSIX)
TEST_F(NetworkTest, IgnoresMACBasedIPv6Address) {
  std::string ipv6_address = "2607:fc20:f340:1dc8:214:22ff:fe01:2345";
  std::string ipv6_mask = "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF";
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();

  // IPSec interface; name is in form "ipsec<index>".
  char if_name[20] = "ipsec11";
  ifaddrs* addr_list =
      InstallIpv6Network(if_name, ipv6_address, ipv6_mask, manager);

  std::vector<const Network*> list = manager.GetNetworks();
  EXPECT_EQ(list.size(), 0u);
  ReleaseIfAddrs(addr_list);
}

TEST_F(NetworkTest, WebRTC_AllowMACBasedIPv6Address) {
  webrtc::test::ScopedFieldTrials field_trials(
      "WebRTC-AllowMACBasedIPv6/Enabled/");
  std::string ipv6_address = "2607:fc20:f340:1dc8:214:22ff:fe01:2345";
  std::string ipv6_mask = "FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF:FFFF";
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.StartUpdating();

  // IPSec interface; name is in form "ipsec<index>".
  char if_name[20] = "ipsec11";
  ifaddrs* addr_list =
      InstallIpv6Network(if_name, ipv6_address, ipv6_mask, manager);

  std::vector<const Network*> list = manager.GetNetworks();
  EXPECT_EQ(list.size(), 1u);
  ReleaseIfAddrs(addr_list);
}
#endif

#if defined(WEBRTC_POSIX)
TEST_F(NetworkTest, WebRTC_BindUsingInterfaceName) {
  char if_name1[20] = "wlan0";
  char if_name2[20] = "v4-wlan0";
  ifaddrs* list = nullptr;
  list = AddIpv6Address(list, if_name1, "1000:2000:3000:4000:0:0:0:1",
                        "FFFF:FFFF:FFFF:FFFF::", 0);
  list = AddIpv4Address(list, if_name2, "192.168.0.2", "255.255.255.255");
  std::vector<std::unique_ptr<Network>> result;

  // Sanity check that both interfaces are included by default.
  FakeNetworkMonitorFactory factory;
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&factory, &socket_server, &field_trials_);
  manager.StartUpdating();
  CallConvertIfAddrs(manager, list, /*include_ignored=*/false, &result);
  EXPECT_EQ(2u, result.size());
  ReleaseIfAddrs(list);
  bool changed;
  // This ensures we release the objects created in CallConvertIfAddrs.
  MergeNetworkList(manager, std::move(result), &changed);
  result.clear();

  FakeNetworkMonitor* network_monitor = GetNetworkMonitor(manager);

  IPAddress ipv6;
  EXPECT_TRUE(IPFromString("1000:2000:3000:4000:0:0:0:1", &ipv6));
  IPAddress ipv4;
  EXPECT_TRUE(IPFromString("192.168.0.2", &ipv4));

  // The network monitor only knwos about the ipv6 address, interface.
  network_monitor->set_adapters({"wlan0"});
  network_monitor->set_ip_addresses({ipv6});
  EXPECT_EQ(manager.BindSocketToNetwork(/* fd */ 77, ipv6),
            NetworkBindingResult::SUCCESS);

  // But it will bind anyway using string matching...
  EXPECT_EQ(manager.BindSocketToNetwork(/* fd */ 77, ipv4),
            NetworkBindingResult::SUCCESS);
}
#endif

TEST_F(NetworkTest, NetworkCostVpn_Default) {
  IPAddress ip1;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:1", &ip1));
  webrtc::test::ScopedKeyValueConfig field_trials;

  Network* net1 = new Network("em1", "em1", TruncateIP(ip1, 64), 64);
  net1->set_type(ADAPTER_TYPE_VPN);
  net1->set_underlying_type_for_vpn(ADAPTER_TYPE_ETHERNET);

  Network* net2 = new Network("em1", "em1", TruncateIP(ip1, 64), 64);
  net2->set_type(ADAPTER_TYPE_ETHERNET);

  EXPECT_EQ(net1->GetCost(field_trials), net2->GetCost(field_trials));
  delete net1;
  delete net2;
}

TEST_F(NetworkTest, NetworkCostVpn_VpnMoreExpensive) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      "WebRTC-AddNetworkCostToVpn/Enabled/");

  IPAddress ip1;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:1", &ip1));

  Network* net1 = new Network("em1", "em1", TruncateIP(ip1, 64), 64);
  net1->set_type(ADAPTER_TYPE_VPN);
  net1->set_underlying_type_for_vpn(ADAPTER_TYPE_ETHERNET);

  Network* net2 = new Network("em1", "em1", TruncateIP(ip1, 64), 64);
  net2->set_type(ADAPTER_TYPE_ETHERNET);

  EXPECT_GT(net1->GetCost(field_trials), net2->GetCost(field_trials));
  delete net1;
  delete net2;
}

TEST_F(NetworkTest, GuessAdapterFromNetworkCost) {
  webrtc::test::ScopedKeyValueConfig field_trials(
      "WebRTC-AddNetworkCostToVpn/Enabled/"
      "WebRTC-UseDifferentiatedCellularCosts/Enabled/");

  IPAddress ip1;
  EXPECT_TRUE(IPFromString("2400:4030:1:2c00:be30:0:0:1", &ip1));

  for (auto type : kAllAdapterTypes) {
    if (type == rtc::ADAPTER_TYPE_VPN)
      continue;
    Network net1("em1", "em1", TruncateIP(ip1, 64), 64);
    net1.set_type(type);
    auto [guess, vpn] =
        Network::GuessAdapterFromNetworkCost(net1.GetCost(field_trials));
    EXPECT_FALSE(vpn);
    if (type == rtc::ADAPTER_TYPE_LOOPBACK) {
      EXPECT_EQ(guess, rtc::ADAPTER_TYPE_ETHERNET);
    } else {
      EXPECT_EQ(type, guess);
    }
  }

  // VPN
  for (auto type : kAllAdapterTypes) {
    if (type == rtc::ADAPTER_TYPE_VPN)
      continue;
    Network net1("em1", "em1", TruncateIP(ip1, 64), 64);
    net1.set_type(rtc::ADAPTER_TYPE_VPN);
    net1.set_underlying_type_for_vpn(type);
    auto [guess, vpn] =
        Network::GuessAdapterFromNetworkCost(net1.GetCost(field_trials));
    EXPECT_TRUE(vpn);
    if (type == rtc::ADAPTER_TYPE_LOOPBACK) {
      EXPECT_EQ(guess, rtc::ADAPTER_TYPE_ETHERNET);
    } else {
      EXPECT_EQ(type, guess);
    }
  }
}

TEST_F(NetworkTest, VpnList) {
  PhysicalSocketServer socket_server;
  {
    BasicNetworkManager manager(&socket_server);
    manager.set_vpn_list({NetworkMask(IPFromString("192.168.0.0"), 16)});
    manager.StartUpdating();
    EXPECT_TRUE(manager.IsConfiguredVpn(IPFromString("192.168.1.1"), 32));
    EXPECT_TRUE(manager.IsConfiguredVpn(IPFromString("192.168.12.1"), 24));
    EXPECT_TRUE(manager.IsConfiguredVpn(IPFromString("192.168.0.0"), 16));
    EXPECT_TRUE(manager.IsConfiguredVpn(IPFromString("192.168.0.0"), 24));
    EXPECT_FALSE(manager.IsConfiguredVpn(IPFromString("192.133.1.1"), 32));
    EXPECT_FALSE(manager.IsConfiguredVpn(IPFromString("192.133.0.0"), 16));
    EXPECT_FALSE(manager.IsConfiguredVpn(IPFromString("192.168.0.0"), 15));
  }
  {
    BasicNetworkManager manager(&socket_server);
    manager.set_vpn_list({NetworkMask(IPFromString("192.168.0.0"), 24)});
    manager.StartUpdating();
    EXPECT_FALSE(manager.IsConfiguredVpn(IPFromString("192.168.1.1"), 32));
    EXPECT_TRUE(manager.IsConfiguredVpn(IPFromString("192.168.0.1"), 32));
  }
}

#if defined(WEBRTC_POSIX)
// TODO(webrtc:13114): Implement the InstallIpv4Network for windows.
TEST_F(NetworkTest, VpnListOverrideAdapterType) {
  PhysicalSocketServer socket_server;
  BasicNetworkManager manager(&socket_server);
  manager.set_vpn_list({NetworkMask(IPFromString("192.168.0.0"), 16)});
  manager.StartUpdating();

  char if_name[20] = "eth0";
  auto addr_list =
      InstallIpv4Network(if_name, "192.168.1.23", "255.255.255.255", manager);

  std::vector<const Network*> list = manager.GetNetworks();
  ASSERT_EQ(1u, list.size());
  EXPECT_EQ(ADAPTER_TYPE_VPN, list[0]->type());
  EXPECT_EQ(ADAPTER_TYPE_ETHERNET, list[0]->underlying_type_for_vpn());
  ClearNetworks(manager);
  ReleaseIfAddrs(addr_list);
}
#endif  // defined(WEBRTC_POSIX)

TEST_F(NetworkTest, HardcodedVpn) {
  const uint8_t cisco[] = {0x0, 0x5, 0x9A, 0x3C, 0x7A, 0x0};
  const uint8_t global[] = {0x2, 0x50, 0x41, 0x0, 0x0, 0x1};
  const uint8_t unknown[] = {0x2, 0x50, 0x41, 0x0, 0x0, 0x0};
  const uint8_t five_bytes[] = {0x2, 0x50, 0x41, 0x0, 0x0};
  EXPECT_TRUE(NetworkManagerBase::IsVpnMacAddress(cisco));
  EXPECT_TRUE(NetworkManagerBase::IsVpnMacAddress(global));

  EXPECT_FALSE(NetworkManagerBase::IsVpnMacAddress(
      rtc::ArrayView<const uint8_t>(cisco, 5)));
  EXPECT_FALSE(NetworkManagerBase::IsVpnMacAddress(five_bytes));
  EXPECT_FALSE(NetworkManagerBase::IsVpnMacAddress(unknown));
  EXPECT_FALSE(NetworkManagerBase::IsVpnMacAddress(nullptr));
}

TEST(CompareNetworks, IrreflexivityTest) {
  // x < x is false
  auto network = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network, network));
}

TEST(CompareNetworks, AsymmetryTest) {
  // x < y and y < x cannot be both true
  auto network_a = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_b = std::make_unique<Network>(
      "test_eth1", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_a, network_b));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_b, network_a));

  auto network_c = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345500U), 24);
  auto network_d = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_c, network_d));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_d, network_c));
}

TEST(CompareNetworks, TransitivityTest) {
  // x < y and y < z imply x < z
  auto network_a = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_b = std::make_unique<Network>(
      "test_eth1", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_c = std::make_unique<Network>(
      "test_eth2", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_a, network_b));
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_b, network_c));

  auto network_d = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_e = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345700U), 24);
  auto network_f = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345800U), 24);
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_d, network_e));
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_e, network_f));
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_d, network_f));
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_a, network_c));
}

TEST(CompareNetworks, TransitivityOfIncomparabilityTest) {
  // x == y and y == z imply x == z,
  // where x == y means x < y and y < x are both false
  auto network_a = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 23);
  auto network_b = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_c = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345700U), 24);

  // network_a < network_b
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_a, network_b));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_b, network_a));

  // network_b < network_c
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_b, network_c));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_c, network_b));

  // network_a < network_c
  EXPECT_TRUE(webrtc_network_internal::CompareNetworks(network_a, network_c));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_c, network_a));

  auto network_d = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_e = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);
  auto network_f = std::make_unique<Network>(
      "test_eth0", "Test Network Adapter 1", IPAddress(0x12345600U), 24);

  // network_d == network_e
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_d, network_e));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_e, network_d));

  // network_e == network_f
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_e, network_f));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_f, network_e));

  // network_d == network_f
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_d, network_f));
  EXPECT_FALSE(webrtc_network_internal::CompareNetworks(network_f, network_d));
}

}  // namespace rtc
