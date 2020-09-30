/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/nat_types.h"

#include "rtc_base/checks.h"

namespace rtc {

class SymmetricNAT : public NAT {
 public:
  bool IsSymmetric() override { return true; }
  bool FiltersIP() override { return true; }
  bool FiltersPort() override { return true; }
};

class OpenConeNAT : public NAT {
 public:
  bool IsSymmetric() override { return false; }
  bool FiltersIP() override { return false; }
  bool FiltersPort() override { return false; }
};

class AddressRestrictedNAT : public NAT {
 public:
  bool IsSymmetric() override { return false; }
  bool FiltersIP() override { return true; }
  bool FiltersPort() override { return false; }
};

class PortRestrictedNAT : public NAT {
 public:
  bool IsSymmetric() override { return false; }
  bool FiltersIP() override { return true; }
  bool FiltersPort() override { return true; }
};

NAT* NAT::Create(NATType type) {
  switch (type) {
    case NAT_OPEN_CONE:
      return new OpenConeNAT();
    case NAT_ADDR_RESTRICTED:
      return new AddressRestrictedNAT();
    case NAT_PORT_RESTRICTED:
      return new PortRestrictedNAT();
    case NAT_SYMMETRIC:
      return new SymmetricNAT();
    default:
      RTC_NOTREACHED();
      return 0;
  }
}

}  // namespace rtc
