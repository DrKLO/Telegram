/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_TEST_TURN_CUSTOMIZER_H_
#define P2P_BASE_TEST_TURN_CUSTOMIZER_H_

#include <memory>

#include "api/turn_customizer.h"
#include "rtc_base/gunit.h"

namespace cricket {

class TestTurnCustomizer : public webrtc::TurnCustomizer {
 public:
  TestTurnCustomizer() {}
  virtual ~TestTurnCustomizer() {}

  enum TestTurnAttributeExtensions {
    // Test only attribute
    STUN_ATTR_COUNTER = 0xFF02  // Number
  };

  void MaybeModifyOutgoingStunMessage(cricket::PortInterface* port,
                                      cricket::StunMessage* message) override {
    modify_cnt_++;

    ASSERT_NE(0, message->type());
    if (add_counter_) {
      message->AddAttribute(std::make_unique<cricket::StunUInt32Attribute>(
          STUN_ATTR_COUNTER, modify_cnt_));
    }
    return;
  }

  bool AllowChannelData(cricket::PortInterface* port,
                        const void* data,
                        size_t size,
                        bool payload) override {
    allow_channel_data_cnt_++;
    return allow_channel_data_;
  }

  bool add_counter_ = false;
  bool allow_channel_data_ = true;
  unsigned int modify_cnt_ = 0;
  unsigned int allow_channel_data_cnt_ = 0;
};

}  // namespace cricket

#endif  // P2P_BASE_TEST_TURN_CUSTOMIZER_H_
