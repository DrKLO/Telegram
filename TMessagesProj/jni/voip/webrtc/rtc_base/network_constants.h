/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_NETWORK_CONSTANTS_H_
#define RTC_BASE_NETWORK_CONSTANTS_H_

#include <stdint.h>

#include <string>

namespace rtc {

constexpr uint16_t kNetworkCostMax = 999;
constexpr uint16_t kNetworkCostCellular2G = 980;
constexpr uint16_t kNetworkCostCellular3G = 910;
constexpr uint16_t kNetworkCostCellular = 900;
constexpr uint16_t kNetworkCostCellular4G = 500;
constexpr uint16_t kNetworkCostCellular5G = 250;
constexpr uint16_t kNetworkCostUnknown = 50;
constexpr uint16_t kNetworkCostLow = 10;
constexpr uint16_t kNetworkCostMin = 0;

// Add 1 to network cost of underlying network type
// so that e.g a "plain" WIFI is prefered over a VPN over WIFI
// everything else being equal.
constexpr uint16_t kNetworkCostVpn = 1;

// alias
constexpr uint16_t kNetworkCostHigh = kNetworkCostCellular;

enum AdapterType {
  // This enum resembles the one in Chromium net::ConnectionType.
  ADAPTER_TYPE_UNKNOWN = 0,
  ADAPTER_TYPE_ETHERNET = 1 << 0,
  ADAPTER_TYPE_WIFI = 1 << 1,
  ADAPTER_TYPE_CELLULAR = 1 << 2,  // This is CELLULAR of unknown type.
  ADAPTER_TYPE_VPN = 1 << 3,
  ADAPTER_TYPE_LOOPBACK = 1 << 4,
  // ADAPTER_TYPE_ANY is used for a network, which only contains a single "any
  // address" IP address (INADDR_ANY for IPv4 or in6addr_any for IPv6), and can
  // use any/all network interfaces. Whereas ADAPTER_TYPE_UNKNOWN is used
  // when the network uses a specific interface/IP, but its interface type can
  // not be determined or not fit in this enum.
  ADAPTER_TYPE_ANY = 1 << 5,
  ADAPTER_TYPE_CELLULAR_2G = 1 << 6,
  ADAPTER_TYPE_CELLULAR_3G = 1 << 7,
  ADAPTER_TYPE_CELLULAR_4G = 1 << 8,
  ADAPTER_TYPE_CELLULAR_5G = 1 << 9
};

std::string AdapterTypeToString(AdapterType type);

}  // namespace rtc

#endif  // RTC_BASE_NETWORK_CONSTANTS_H_
