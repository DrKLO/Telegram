/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_RTP_TRANSCEIVER_H_
#define API_TEST_MOCK_RTP_TRANSCEIVER_H_

#include <string>
#include <vector>

#include "api/rtp_transceiver_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockRtpTransceiver final
    : public rtc::RefCountedObject<RtpTransceiverInterface> {
 public:
  static rtc::scoped_refptr<MockRtpTransceiver> Create() {
    return new MockRtpTransceiver();
  }

  MOCK_METHOD(cricket::MediaType, media_type, (), (const, override));
  MOCK_METHOD(absl::optional<std::string>, mid, (), (const, override));
  MOCK_METHOD(rtc::scoped_refptr<RtpSenderInterface>,
              sender,
              (),
              (const, override));
  MOCK_METHOD(rtc::scoped_refptr<RtpReceiverInterface>,
              receiver,
              (),
              (const, override));
  MOCK_METHOD(bool, stopped, (), (const, override));
  MOCK_METHOD(bool, stopping, (), (const, override));
  MOCK_METHOD(RtpTransceiverDirection, direction, (), (const, override));
  MOCK_METHOD(void,
              SetDirection,
              (RtpTransceiverDirection new_direction),
              (override));
  MOCK_METHOD(RTCError,
              SetDirectionWithError,
              (RtpTransceiverDirection new_direction),
              (override));
  MOCK_METHOD(absl::optional<RtpTransceiverDirection>,
              current_direction,
              (),
              (const, override));
  MOCK_METHOD(absl::optional<RtpTransceiverDirection>,
              fired_direction,
              (),
              (const, override));
  MOCK_METHOD(RTCError, StopStandard, (), (override));
  MOCK_METHOD(void, StopInternal, (), (override));
  MOCK_METHOD(void, Stop, (), (override));
  MOCK_METHOD(RTCError,
              SetCodecPreferences,
              (rtc::ArrayView<RtpCodecCapability> codecs),
              (override));
  MOCK_METHOD(std::vector<RtpCodecCapability>,
              codec_preferences,
              (),
              (const, override));
  MOCK_METHOD(std::vector<RtpHeaderExtensionCapability>,
              HeaderExtensionsToOffer,
              (),
              (const, override));
  MOCK_METHOD(webrtc::RTCError,
              SetOfferedRtpHeaderExtensions,
              (rtc::ArrayView<const RtpHeaderExtensionCapability>
                   header_extensions_to_offer),
              (override));

 private:
  MockRtpTransceiver() = default;
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_RTP_TRANSCEIVER_H_
