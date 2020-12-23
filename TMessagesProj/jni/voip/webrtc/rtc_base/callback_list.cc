/*
 *  Copyright 2020 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/callback_list.h"

#include "rtc_base/checks.h"

namespace webrtc {
namespace callback_list_impl {

CallbackListReceivers::CallbackListReceivers() = default;

CallbackListReceivers::~CallbackListReceivers() {
  RTC_CHECK(!send_in_progress_);
}

void CallbackListReceivers::Foreach(
    rtc::FunctionView<void(UntypedFunction&)> fv) {
  RTC_CHECK(!send_in_progress_);
  send_in_progress_ = true;
  for (auto& r : receivers_) {
    fv(r);
  }
  send_in_progress_ = false;
}

template void CallbackListReceivers::AddReceiver(
    UntypedFunction::TrivialUntypedFunctionArgs<1>);
template void CallbackListReceivers::AddReceiver(
    UntypedFunction::TrivialUntypedFunctionArgs<2>);
template void CallbackListReceivers::AddReceiver(
    UntypedFunction::TrivialUntypedFunctionArgs<3>);
template void CallbackListReceivers::AddReceiver(
    UntypedFunction::TrivialUntypedFunctionArgs<4>);
template void CallbackListReceivers::AddReceiver(
    UntypedFunction::NontrivialUntypedFunctionArgs);
template void CallbackListReceivers::AddReceiver(
    UntypedFunction::FunctionPointerUntypedFunctionArgs);

}  // namespace callback_list_impl
}  // namespace webrtc
