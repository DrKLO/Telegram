/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_CALL_CALL_FACTORY_INTERFACE_H_
#define API_CALL_CALL_FACTORY_INTERFACE_H_

#include <memory>

#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// These classes are not part of the API, and are treated as opaque pointers.
class Call;
struct CallConfig;

// This interface exists to allow webrtc to be optionally built without media
// support (i.e., if only being used for data channels). PeerConnectionFactory
// is constructed with a CallFactoryInterface, which may or may not be null.
class CallFactoryInterface {
 public:
  virtual ~CallFactoryInterface() {}

  virtual Call* CreateCall(const CallConfig& config) = 0;
};

RTC_EXPORT std::unique_ptr<CallFactoryInterface> CreateCallFactory();

}  // namespace webrtc

#endif  // API_CALL_CALL_FACTORY_INTERFACE_H_
