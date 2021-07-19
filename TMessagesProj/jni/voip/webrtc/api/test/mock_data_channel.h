/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_TEST_MOCK_DATA_CHANNEL_H_
#define API_TEST_MOCK_DATA_CHANNEL_H_

#include <string>

#include "api/data_channel_interface.h"
#include "test/gmock.h"

namespace webrtc {

class MockDataChannelInterface final
    : public rtc::RefCountedObject<webrtc::DataChannelInterface> {
 public:
  static rtc::scoped_refptr<MockDataChannelInterface> Create() {
    return new MockDataChannelInterface();
  }

  MOCK_METHOD(void,
              RegisterObserver,
              (DataChannelObserver * observer),
              (override));
  MOCK_METHOD(void, UnregisterObserver, (), (override));
  MOCK_METHOD(std::string, label, (), (const, override));
  MOCK_METHOD(bool, reliable, (), (const, override));
  MOCK_METHOD(bool, ordered, (), (const, override));
  MOCK_METHOD(uint16_t, maxRetransmitTime, (), (const, override));
  MOCK_METHOD(uint16_t, maxRetransmits, (), (const, override));
  MOCK_METHOD(absl::optional<int>, maxRetransmitsOpt, (), (const, override));
  MOCK_METHOD(absl::optional<int>, maxPacketLifeTime, (), (const, override));
  MOCK_METHOD(std::string, protocol, (), (const, override));
  MOCK_METHOD(bool, negotiated, (), (const, override));
  MOCK_METHOD(int, id, (), (const, override));
  MOCK_METHOD(Priority, priority, (), (const, override));
  MOCK_METHOD(DataState, state, (), (const, override));
  MOCK_METHOD(RTCError, error, (), (const, override));
  MOCK_METHOD(uint32_t, messages_sent, (), (const, override));
  MOCK_METHOD(uint64_t, bytes_sent, (), (const, override));
  MOCK_METHOD(uint32_t, messages_received, (), (const, override));
  MOCK_METHOD(uint64_t, bytes_received, (), (const, override));
  MOCK_METHOD(uint64_t, buffered_amount, (), (const, override));
  MOCK_METHOD(void, Close, (), (override));
  MOCK_METHOD(bool, Send, (const DataBuffer& buffer), (override));

 protected:
  MockDataChannelInterface() = default;
};

}  // namespace webrtc

#endif  // API_TEST_MOCK_DATA_CHANNEL_H_
