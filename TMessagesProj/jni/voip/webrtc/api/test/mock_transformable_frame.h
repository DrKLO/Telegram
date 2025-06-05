/*
 *  Copyright 2023 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_TRANSFORMABLE_FRAME_H_
#define API_TEST_MOCK_TRANSFORMABLE_FRAME_H_

#include <stdint.h>

#include <optional>
#include <string>

#include "api/array_view.h"
#include "api/frame_transformer_interface.h"
#include "api/units/timestamp.h"
#include "test/gmock.h"

namespace webrtc {

class MockTransformableFrame : public webrtc::TransformableFrameInterface {
 public:
  MOCK_METHOD(rtc::ArrayView<const uint8_t>, GetData, (), (const, override));
  MOCK_METHOD(void, SetData, (rtc::ArrayView<const uint8_t>), (override));
  MOCK_METHOD(uint8_t, GetPayloadType, (), (const, override));
  MOCK_METHOD(uint32_t, GetSsrc, (), (const, override));
  MOCK_METHOD(uint32_t, GetTimestamp, (), (const, override));
  MOCK_METHOD(void, SetRTPTimestamp, (uint32_t), (override));
  MOCK_METHOD(std::optional<webrtc::Timestamp>,
              GetCaptureTimeIdentifier,
              (),
              (const, override));
  MOCK_METHOD(std::string, GetMimeType, (), (const, override));
};

static_assert(!std::is_abstract_v<MockTransformableFrame>, "");

}  // namespace webrtc

#endif  // API_TEST_MOCK_TRANSFORMABLE_FRAME_H_
