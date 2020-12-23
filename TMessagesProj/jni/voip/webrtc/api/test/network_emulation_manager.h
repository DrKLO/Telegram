/*
 *  Copyright (c) 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_NETWORK_EMULATION_MANAGER_H_
#define API_TEST_NETWORK_EMULATION_MANAGER_H_

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "api/array_view.h"
#include "api/test/network_emulation/network_emulation_interfaces.h"
#include "api/test/simulated_network.h"
#include "api/test/time_controller.h"
#include "api/units/timestamp.h"
#include "rtc_base/network.h"
#include "rtc_base/network_constants.h"
#include "rtc_base/thread.h"

namespace webrtc {

// This API is still in development and can be changed without prior notice.

// These classes are forward declared here, because they used as handles, to
// make it possible for client code to operate with these abstractions and build
// required network configuration. With forward declaration here implementation
// is more readable, than with interfaces approach and cause user needn't any
// API methods on these abstractions it is acceptable here.

// EmulatedNetworkNode is an abstraction for some network in the real world,
// like 3G network between peers, or Wi-Fi for one peer and LTE for another.
// Multiple networks can be joined into chain emulating a network path from
// one peer to another.
class EmulatedNetworkNode;

// EmulatedRoute is handle for single route from one network interface on one
// peer device to another network interface on another peer device.
class EmulatedRoute;

struct EmulatedEndpointConfig {
  enum class IpAddressFamily { kIpv4, kIpv6 };
  enum class StatsGatheringMode {
    // Gather main network stats counters.
    kDefault,
    // kDefault + also gather per packet statistics. In this mode more memory
    // will be used.
    kDebug
  };

  IpAddressFamily generated_ip_family = IpAddressFamily::kIpv4;
  // If specified will be used as IP address for endpoint node. Must be unique
  // among all created nodes.
  absl::optional<rtc::IPAddress> ip;
  // Should endpoint be enabled or not, when it will be created.
  // Enabled endpoints will be available for webrtc to send packets.
  bool start_as_enabled = true;
  // Network type which will be used to represent endpoint to WebRTC.
  rtc::AdapterType type = rtc::AdapterType::ADAPTER_TYPE_UNKNOWN;
  StatsGatheringMode stats_gathering_mode = StatsGatheringMode::kDefault;
};

struct EmulatedTURNServerConfig {
  EmulatedEndpointConfig client_config;
  EmulatedEndpointConfig peer_config;
};

// EmulatedTURNServer is an abstraction for a TURN server.
class EmulatedTURNServerInterface {
 public:
  struct IceServerConfig {
    std::string username;
    std::string password;
    std::string url;
  };

  virtual ~EmulatedTURNServerInterface() {}

  // Get an IceServer configuration suitable to add to a PeerConnection.
  virtual IceServerConfig GetIceServerConfig() const = 0;

  // Get non-null client endpoint, an endpoint that accepts TURN allocations.
  // This shall typically be connected to one or more webrtc endpoint.
  virtual EmulatedEndpoint* GetClientEndpoint() const = 0;

  // Returns socket address, which client should use to connect to TURN server
  // and do TURN allocation.
  virtual rtc::SocketAddress GetClientEndpointAddress() const = 0;

  // Get non-null peer endpoint, that is "connected to the internet".
  // This shall typically be connected to another TURN server.
  virtual EmulatedEndpoint* GetPeerEndpoint() const = 0;
};

// Provide interface to obtain all required objects to inject network emulation
// layer into PeerConnection. Also contains information about network interfaces
// accessible by PeerConnection.
class EmulatedNetworkManagerInterface {
 public:
  virtual ~EmulatedNetworkManagerInterface() = default;

  // Returns non-null pointer to thread that have to be used as network thread
  // for WebRTC to properly setup network emulation. Returned thread is owned
  // by EmulatedNetworkManagerInterface implementation.
  virtual rtc::Thread* network_thread() = 0;
  // Returns non-null pointer to network manager that have to be injected into
  // WebRTC to properly setup network emulation. Returned manager is owned by
  // EmulatedNetworkManagerInterface implementation.
  virtual rtc::NetworkManager* network_manager() = 0;
  // Returns list of endpoints that are associated with this instance. Pointers
  // are guaranteed to be non-null and are owned by NetworkEmulationManager.
  virtual std::vector<EmulatedEndpoint*> endpoints() const = 0;

  // Passes summarized network stats for endpoints for this manager into
  // specified |stats_callback|. Callback will be executed on network emulation
  // internal task queue.
  virtual void GetStats(
      std::function<void(std::unique_ptr<EmulatedNetworkStats>)> stats_callback)
      const = 0;
};

enum class TimeMode { kRealTime, kSimulated };

// Provides an API for creating and configuring emulated network layer.
// All objects returned by this API are owned by NetworkEmulationManager itself
// and will be deleted when manager will be deleted.
class NetworkEmulationManager {
 public:
  // Helper struct to simplify creation of simulated network behaviors. Contains
  // non-owning pointers as the underlying instances are owned by the manager.
  struct SimulatedNetworkNode {
    SimulatedNetworkInterface* simulation;
    EmulatedNetworkNode* node;

    class Builder {
     public:
      explicit Builder(NetworkEmulationManager* net) : net_(net) {}
      Builder() : net_(nullptr) {}
      Builder(const Builder&) = default;
      // Sets the config state, note that this will replace any previously set
      // values.
      Builder& config(BuiltInNetworkBehaviorConfig config);
      Builder& delay_ms(int queue_delay_ms);
      Builder& capacity_kbps(int link_capacity_kbps);
      Builder& capacity_Mbps(int link_capacity_Mbps);
      Builder& loss(double loss_rate);
      Builder& packet_queue_length(int max_queue_length_in_packets);
      SimulatedNetworkNode Build() const;
      SimulatedNetworkNode Build(NetworkEmulationManager* net) const;

     private:
      NetworkEmulationManager* const net_;
      BuiltInNetworkBehaviorConfig config_;
    };
  };
  virtual ~NetworkEmulationManager() = default;

  virtual TimeController* time_controller() = 0;

  // Creates an emulated network node, which represents single network in
  // the emulated network layer.
  virtual EmulatedNetworkNode* CreateEmulatedNode(
      BuiltInNetworkBehaviorConfig config) = 0;
  virtual EmulatedNetworkNode* CreateEmulatedNode(
      std::unique_ptr<NetworkBehaviorInterface> network_behavior) = 0;

  virtual SimulatedNetworkNode::Builder NodeBuilder() = 0;

  // Creates an emulated endpoint, which represents single network interface on
  // the peer's device.
  virtual EmulatedEndpoint* CreateEndpoint(EmulatedEndpointConfig config) = 0;
  // Enable emulated endpoint to make it available for webrtc.
  // Caller mustn't enable currently enabled endpoint.
  virtual void EnableEndpoint(EmulatedEndpoint* endpoint) = 0;
  // Disable emulated endpoint to make it unavailable for webrtc.
  // Caller mustn't disable currently disabled endpoint.
  virtual void DisableEndpoint(EmulatedEndpoint* endpoint) = 0;

  // Creates a route between endpoints going through specified network nodes.
  // This route is single direction only and describe how traffic that was
  // sent by network interface |from| have to be delivered to the network
  // interface |to|. Return object can be used to remove created route. The
  // route must contains at least one network node inside it.
  //
  // Assume that E{0-9} are endpoints and N{0-9} are network nodes, then
  // creation of the route have to follow these rules:
  //   1. A route consists of a source endpoint, an ordered list of one or
  //      more network nodes, and a destination endpoint.
  //   2. If (E1, ..., E2) is a route, then E1 != E2.
  //      In other words, the source and the destination may not be the same.
  //   3. Given two simultaneously existing routes (E1, ..., E2) and
  //      (E3, ..., E4), either E1 != E3 or E2 != E4.
  //      In other words, there may be at most one route from any given source
  //      endpoint to any given destination endpoint.
  //   4. Given two simultaneously existing routes (E1, ..., N1, ..., E2)
  //      and (E3, ..., N2, ..., E4), either N1 != N2 or E2 != E4.
  //      In other words, a network node may not belong to two routes that lead
  //      to the same destination endpoint.
  virtual EmulatedRoute* CreateRoute(
      EmulatedEndpoint* from,
      const std::vector<EmulatedNetworkNode*>& via_nodes,
      EmulatedEndpoint* to) = 0;

  // Creates a route over the given |via_nodes| creating the required endpoints
  // in the process. The returned EmulatedRoute pointer can be used in other
  // calls as a transport route for message or cross traffic.
  virtual EmulatedRoute* CreateRoute(
      const std::vector<EmulatedNetworkNode*>& via_nodes) = 0;

  // Removes route previously created by CreateRoute(...).
  // Caller mustn't call this function with route, that have been already
  // removed earlier.
  virtual void ClearRoute(EmulatedRoute* route) = 0;

  // Creates a simulated TCP connection using |send_route| for traffic and
  // |ret_route| for feedback. This can be used to emulate HTTP cross traffic
  // and to implement realistic reliable signaling over lossy networks.
  // TODO(srte): Handle clearing of the routes involved.
  virtual TcpMessageRoute* CreateTcpRoute(EmulatedRoute* send_route,
                                          EmulatedRoute* ret_route) = 0;

  // Creates EmulatedNetworkManagerInterface which can be used then to inject
  // network emulation layer into PeerConnection. |endpoints| - are available
  // network interfaces for PeerConnection. If endpoint is enabled, it will be
  // immediately available for PeerConnection, otherwise user will be able to
  // enable endpoint later to make it available for PeerConnection.
  virtual EmulatedNetworkManagerInterface*
  CreateEmulatedNetworkManagerInterface(
      const std::vector<EmulatedEndpoint*>& endpoints) = 0;

  // Passes summarized network stats for specified |endpoints| into specified
  // |stats_callback|. Callback will be executed on network emulation
  // internal task queue.
  virtual void GetStats(
      rtc::ArrayView<EmulatedEndpoint*> endpoints,
      std::function<void(std::unique_ptr<EmulatedNetworkStats>)>
          stats_callback) = 0;

  // Create a EmulatedTURNServer.
  // The TURN server has 2 endpoints that need to be connected with routes,
  // - GetClientEndpoint() - the endpoint that accepts TURN allocations.
  // - GetPeerEndpoint() - the endpoint that is "connected to the internet".
  virtual EmulatedTURNServerInterface* CreateTURNServer(
      EmulatedTURNServerConfig config) = 0;
};

}  // namespace webrtc

#endif  // API_TEST_NETWORK_EMULATION_MANAGER_H_
