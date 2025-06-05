/*
 *  Copyright 2019 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/operations_chain.h"

#include "api/make_ref_counted.h"
#include "rtc_base/checks.h"

namespace rtc {

OperationsChain::CallbackHandle::CallbackHandle(
    scoped_refptr<OperationsChain> operations_chain)
    : operations_chain_(std::move(operations_chain)) {}

OperationsChain::CallbackHandle::~CallbackHandle() {
#if RTC_DCHECK_IS_ON
  RTC_DCHECK(has_run_);
#endif
}

void OperationsChain::CallbackHandle::OnOperationComplete() {
#if RTC_DCHECK_IS_ON
  RTC_DCHECK(!has_run_);
  has_run_ = true;
#endif  // RTC_DCHECK_IS_ON
  operations_chain_->OnOperationComplete();
  // We have no reason to keep the `operations_chain_` alive through reference
  // counting anymore.
  operations_chain_ = nullptr;
}

// static
scoped_refptr<OperationsChain> OperationsChain::Create() {
  // Explicit new, to access private constructor.
  return rtc::scoped_refptr<OperationsChain>(new OperationsChain());
}

OperationsChain::OperationsChain() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
}

OperationsChain::~OperationsChain() {
  // Operations keep the chain alive through reference counting so this should
  // not be possible. The fact that the chain is empty makes it safe to
  // destroy the OperationsChain on any sequence.
  RTC_DCHECK(chained_operations_.empty());
}

void OperationsChain::SetOnChainEmptyCallback(
    std::function<void()> on_chain_empty_callback) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  on_chain_empty_callback_ = std::move(on_chain_empty_callback);
}

bool OperationsChain::IsEmpty() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  return chained_operations_.empty();
}

std::function<void()> OperationsChain::CreateOperationsChainCallback() {
  return [handle = rtc::make_ref_counted<CallbackHandle>(
              rtc::scoped_refptr<OperationsChain>(this))]() {
    handle->OnOperationComplete();
  };
}

void OperationsChain::OnOperationComplete() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  // The front element is the operation that just completed, remove it.
  RTC_DCHECK(!chained_operations_.empty());
  chained_operations_.pop();
  // If there are any other operations chained, execute the next one. Otherwise,
  // invoke the "on chain empty" callback if it has been set.
  if (!chained_operations_.empty()) {
    chained_operations_.front()->Run();
  } else if (on_chain_empty_callback_.has_value()) {
    on_chain_empty_callback_.value()();
  }
}

}  // namespace rtc
