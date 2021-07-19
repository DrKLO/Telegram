/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_media_utils.h"

#include "rtc_base/checks.h"

namespace webrtc {

RtpTransceiverDirection RtpTransceiverDirectionFromSendRecv(bool send,
                                                            bool recv) {
  if (send && recv) {
    return RtpTransceiverDirection::kSendRecv;
  } else if (send && !recv) {
    return RtpTransceiverDirection::kSendOnly;
  } else if (!send && recv) {
    return RtpTransceiverDirection::kRecvOnly;
  } else {
    return RtpTransceiverDirection::kInactive;
  }
}

bool RtpTransceiverDirectionHasSend(RtpTransceiverDirection direction) {
  return direction == RtpTransceiverDirection::kSendRecv ||
         direction == RtpTransceiverDirection::kSendOnly;
}

bool RtpTransceiverDirectionHasRecv(RtpTransceiverDirection direction) {
  return direction == RtpTransceiverDirection::kSendRecv ||
         direction == RtpTransceiverDirection::kRecvOnly;
}

RtpTransceiverDirection RtpTransceiverDirectionReversed(
    RtpTransceiverDirection direction) {
  switch (direction) {
    case RtpTransceiverDirection::kSendRecv:
    case RtpTransceiverDirection::kInactive:
    case RtpTransceiverDirection::kStopped:
      return direction;
    case RtpTransceiverDirection::kSendOnly:
      return RtpTransceiverDirection::kRecvOnly;
    case RtpTransceiverDirection::kRecvOnly:
      return RtpTransceiverDirection::kSendOnly;
    default:
      RTC_NOTREACHED();
      return direction;
  }
}

RtpTransceiverDirection RtpTransceiverDirectionWithSendSet(
    RtpTransceiverDirection direction,
    bool send) {
  return RtpTransceiverDirectionFromSendRecv(
      send, RtpTransceiverDirectionHasRecv(direction));
}

RtpTransceiverDirection RtpTransceiverDirectionWithRecvSet(
    RtpTransceiverDirection direction,
    bool recv) {
  return RtpTransceiverDirectionFromSendRecv(
      RtpTransceiverDirectionHasSend(direction), recv);
}

const char* RtpTransceiverDirectionToString(RtpTransceiverDirection direction) {
  switch (direction) {
    case RtpTransceiverDirection::kSendRecv:
      return "kSendRecv";
    case RtpTransceiverDirection::kSendOnly:
      return "kSendOnly";
    case RtpTransceiverDirection::kRecvOnly:
      return "kRecvOnly";
    case RtpTransceiverDirection::kInactive:
      return "kInactive";
    case RtpTransceiverDirection::kStopped:
      return "kStopped";
  }
  RTC_NOTREACHED();
  return "";
}

RtpTransceiverDirection RtpTransceiverDirectionIntersection(
    RtpTransceiverDirection lhs,
    RtpTransceiverDirection rhs) {
  return RtpTransceiverDirectionFromSendRecv(
      RtpTransceiverDirectionHasSend(lhs) &&
          RtpTransceiverDirectionHasSend(rhs),
      RtpTransceiverDirectionHasRecv(lhs) &&
          RtpTransceiverDirectionHasRecv(rhs));
}

}  // namespace webrtc
