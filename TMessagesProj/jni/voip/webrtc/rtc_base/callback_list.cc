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

void CallbackListReceivers::RemoveReceivers(const void* removal_tag) {
  RTC_CHECK(!send_in_progress_);
  RTC_DCHECK(removal_tag != nullptr);

  // We divide the receivers_ vector into three regions: from right to left, the
  // "keep" region, the "todo" region, and the "remove" region. The "todo"
  // region initially covers the whole vector.
  size_t first_todo = 0;                    // First element of the "todo"
                                            // region.
  size_t first_remove = receivers_.size();  // First element of the "remove"
                                            // region.

  // Loop until the "todo" region is empty.
  while (first_todo != first_remove) {
    if (receivers_[first_todo].removal_tag != removal_tag) {
      // The first element of the "todo" region should be kept. Move the
      // "keep"/"todo" boundary.
      ++first_todo;
    } else if (receivers_[first_remove - 1].removal_tag == removal_tag) {
      // The last element of the "todo" region should be removed. Move the
      // "todo"/"remove" boundary.
      --first_remove;
    } else {
      // The first element of the "todo" region should be removed, and the last
      // element of the "todo" region should be kept. Swap them, and then shrink
      // the "todo" region from both ends.
      RTC_DCHECK_NE(first_todo, first_remove - 1);
      using std::swap;
      swap(receivers_[first_todo], receivers_[first_remove - 1]);
      RTC_DCHECK_NE(receivers_[first_todo].removal_tag, removal_tag);
      ++first_todo;
      RTC_DCHECK_EQ(receivers_[first_remove - 1].removal_tag, removal_tag);
      --first_remove;
    }
  }

  // Discard the remove region.
  receivers_.resize(first_remove);
}

void CallbackListReceivers::Foreach(
    rtc::FunctionView<void(UntypedFunction&)> fv) {
  RTC_CHECK(!send_in_progress_);
  send_in_progress_ = true;
  for (auto& r : receivers_) {
    fv(r.function);
  }
  send_in_progress_ = false;
}

template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::TrivialUntypedFunctionArgs<1>);
template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::TrivialUntypedFunctionArgs<2>);
template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::TrivialUntypedFunctionArgs<3>);
template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::TrivialUntypedFunctionArgs<4>);
template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::NontrivialUntypedFunctionArgs);
template void CallbackListReceivers::AddReceiver(
    const void*,
    UntypedFunction::FunctionPointerUntypedFunctionArgs);

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
