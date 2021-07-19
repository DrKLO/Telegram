/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef API_TRANSPORT_WEBRTC_KEY_VALUE_CONFIG_H_
#define API_TRANSPORT_WEBRTC_KEY_VALUE_CONFIG_H_

#include <string>

#include "absl/strings/string_view.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// An interface that provides a key-value mapping for configuring internal
// details of WebRTC. Note that there's no guarantess that the meaning of a
// particular key value mapping will be preserved over time and no announcements
// will be made if they are changed. It's up to the library user to ensure that
// the behavior does not break.
class RTC_EXPORT WebRtcKeyValueConfig {
 public:
  virtual ~WebRtcKeyValueConfig() = default;
  // The configured value for the given key. Defaults to an empty string.
  virtual std::string Lookup(absl::string_view key) const = 0;
};
}  // namespace webrtc

#endif  // API_TRANSPORT_WEBRTC_KEY_VALUE_CONFIG_H_
