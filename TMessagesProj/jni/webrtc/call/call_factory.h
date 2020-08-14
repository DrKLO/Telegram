/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef CALL_CALL_FACTORY_H_
#define CALL_CALL_FACTORY_H_

#include "api/call/call_factory_interface.h"
#include "call/call.h"
#include "call/call_config.h"
#include "rtc_base/synchronization/sequence_checker.h"

namespace webrtc {

class CallFactory : public CallFactoryInterface {
 public:
  CallFactory();

 private:
  ~CallFactory() override {}

  Call* CreateCall(const CallConfig& config) override;

  SequenceChecker call_thread_;
  rtc::scoped_refptr<SharedModuleThread> module_thread_
      RTC_GUARDED_BY(call_thread_);
};

}  // namespace webrtc

#endif  // CALL_CALL_FACTORY_H_
