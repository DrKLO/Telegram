/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/network_constants.h"

#include "rtc_base/checks.h"

namespace rtc {

std::string AdapterTypeToString(AdapterType type) {
  switch (type) {
    case ADAPTER_TYPE_ANY:
      return "Wildcard";
    case ADAPTER_TYPE_UNKNOWN:
      return "Unknown";
    case ADAPTER_TYPE_ETHERNET:
      return "Ethernet";
    case ADAPTER_TYPE_WIFI:
      return "Wifi";
    case ADAPTER_TYPE_CELLULAR:
      return "Cellular";
    case ADAPTER_TYPE_CELLULAR_2G:
      return "Cellular2G";
    case ADAPTER_TYPE_CELLULAR_3G:
      return "Cellular3G";
    case ADAPTER_TYPE_CELLULAR_4G:
      return "Cellular4G";
    case ADAPTER_TYPE_CELLULAR_5G:
      return "Cellular5G";
    case ADAPTER_TYPE_VPN:
      return "VPN";
    case ADAPTER_TYPE_LOOPBACK:
      return "Loopback";
    default:
      RTC_DCHECK_NOTREACHED() << "Invalid type " << type;
      return std::string();
  }
}

}  // namespace rtc
