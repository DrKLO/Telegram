/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/rtc_error.h"

#include "rtc_base/arraysize.h"

namespace {

const char* kRTCErrorTypeNames[] = {
    "NONE",
    "UNSUPPORTED_OPERATION",
    "UNSUPPORTED_PARAMETER",
    "INVALID_PARAMETER",
    "INVALID_RANGE",
    "SYNTAX_ERROR",
    "INVALID_STATE",
    "INVALID_MODIFICATION",
    "NETWORK_ERROR",
    "RESOURCE_EXHAUSTED",
    "INTERNAL_ERROR",
    "OPERATION_ERROR_WITH_DATA",
};
static_assert(
    static_cast<int>(webrtc::RTCErrorType::OPERATION_ERROR_WITH_DATA) ==
        (arraysize(kRTCErrorTypeNames) - 1),
    "kRTCErrorTypeNames must have as many strings as RTCErrorType "
    "has values.");

const char* kRTCErrorDetailTypeNames[] = {
    "NONE",
    "DATA_CHANNEL_FAILURE",
    "DTLS_FAILURE",
    "FINGERPRINT_FAILURE",
    "SCTP_FAILURE",
    "SDP_SYNTAX_ERROR",
    "HARDWARE_ENCODER_NOT_AVAILABLE",
    "HARDWARE_ENCODER_ERROR",
};
static_assert(
    static_cast<int>(webrtc::RTCErrorDetailType::HARDWARE_ENCODER_ERROR) ==
        (arraysize(kRTCErrorDetailTypeNames) - 1),
    "kRTCErrorDetailTypeNames must have as many strings as "
    "RTCErrorDetailType has values.");

}  // namespace

namespace webrtc {

// static
RTCError RTCError::OK() {
  return RTCError();
}

const char* RTCError::message() const {
  return message_.c_str();
}

void RTCError::set_message(std::string message) {
  message_ = std::move(message);
}

const char* ToString(RTCErrorType error) {
  int index = static_cast<int>(error);
  return kRTCErrorTypeNames[index];
}

const char* ToString(RTCErrorDetailType error) {
  int index = static_cast<int>(error);
  return kRTCErrorDetailTypeNames[index];
}

}  // namespace webrtc
