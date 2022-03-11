/*
 *  Copyright 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_RTPSENDER_H_
#define API_TEST_MOCK_RTPSENDER_H_

#include <string>
#include <vector>

#include "api/rtp_sender_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockRtpSender : public rtc::RefCountedObject<RtpSenderInterface> {
 public:
  MOCK_METHOD(bool, SetTrack, (MediaStreamTrackInterface*), (override));
  MOCK_METHOD(rtc::scoped_refptr<MediaStreamTrackInterface>,
              track,
              (),
              (const, override));
  MOCK_METHOD(uint32_t, ssrc, (), (const, override));
  MOCK_METHOD(cricket::MediaType, media_type, (), (const, override));
  MOCK_METHOD(std::string, id, (), (const, override));
  MOCK_METHOD(std::vector<std::string>, stream_ids, (), (const, override));
  MOCK_METHOD(std::vector<RtpEncodingParameters>,
              init_send_encodings,
              (),
              (const, override));
  MOCK_METHOD(RtpParameters, GetParameters, (), (const, override));
  MOCK_METHOD(RTCError, SetParameters, (const RtpParameters&), (override));
  MOCK_METHOD(rtc::scoped_refptr<DtmfSenderInterface>,
              GetDtmfSender,
              (),
              (const, override));
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_RTPSENDER_H_
