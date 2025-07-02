/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/stun_request.h"

#include <algorithm>
#include <memory>
#include <utility>
#include <vector>

#include "absl/memory/memory.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/time_utils.h"  // For TimeMillis

namespace cricket {
using ::webrtc::SafeTask;

// RFC 5389 says SHOULD be 500ms.
// For years, this was 100ms, but for networks that
// experience moments of high RTT (such as 2G networks), this doesn't
// work well.
const int STUN_INITIAL_RTO = 250;  // milliseconds

// The timeout doubles each retransmission, up to this many times
// RFC 5389 says SHOULD retransmit 7 times.
// This has been 8 for years (not sure why).
const int STUN_MAX_RETRANSMISSIONS = 8;  // Total sends: 9

// We also cap the doubling, even though the standard doesn't say to.
// This has been 1.6 seconds for years, but for networks that
// experience moments of high RTT (such as 2G networks), this doesn't
// work well.
const int STUN_MAX_RTO = 8000;  // milliseconds, or 5 doublings

StunRequestManager::StunRequestManager(
    webrtc::TaskQueueBase* thread,
    std::function<void(const void*, size_t, StunRequest*)> send_packet)
    : thread_(thread), send_packet_(std::move(send_packet)) {}

StunRequestManager::~StunRequestManager() = default;

void StunRequestManager::Send(StunRequest* request) {
  SendDelayed(request, 0);
}

void StunRequestManager::SendDelayed(StunRequest* request, int delay) {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK_EQ(this, request->manager());
  RTC_DCHECK(!request->AuthenticationRequired() ||
             request->msg()->integrity() !=
                 StunMessage::IntegrityStatus::kNotSet)
      << "Sending request w/o integrity!";
  auto [iter, was_inserted] =
      requests_.emplace(request->id(), absl::WrapUnique(request));
  RTC_DCHECK(was_inserted);
  request->Send(webrtc::TimeDelta::Millis(delay));
}

void StunRequestManager::FlushForTest(int msg_type) {
  RTC_DCHECK_RUN_ON(thread_);
  for (const auto& [unused, request] : requests_) {
    if (msg_type == kAllRequestsForTest || msg_type == request->type()) {
      // Calling `Send` implies starting the send operation which may be posted
      // on a timer and be repeated on a timer until timeout. To make sure that
      // a call to `Send` doesn't conflict with a previously started `Send`
      // operation, we reset the `task_safety_` flag here, which has the effect
      // of canceling any outstanding tasks and prepare a new flag for
      // operations related to this call to `Send`.
      request->ResetTasksForTest();
      request->Send(webrtc::TimeDelta::Zero());
    }
  }
}

bool StunRequestManager::HasRequestForTest(int msg_type) {
  RTC_DCHECK_RUN_ON(thread_);
  RTC_DCHECK_NE(msg_type, kAllRequestsForTest);
  for (const auto& [unused, request] : requests_) {
    if (msg_type == request->type()) {
      return true;
    }
  }
  return false;
}

void StunRequestManager::Clear() {
  RTC_DCHECK_RUN_ON(thread_);
  requests_.clear();
}

bool StunRequestManager::CheckResponse(StunMessage* msg) {
  RTC_DCHECK_RUN_ON(thread_);
  RequestMap::iterator iter = requests_.find(msg->transaction_id());
  if (iter == requests_.end())
    return false;

  StunRequest* request = iter->second.get();

  // Now that we know the request, we can see if the response is
  // integrity-protected or not. Some requests explicitly disables
  // integrity checks using SetAuthenticationRequired.
  // TODO(chromium:1177125): Remove below!
  // And we suspect that for some tests, the message integrity is not set in the
  // request. Complain, and then don't check.
  bool skip_integrity_checking =
      (request->msg()->integrity() == StunMessage::IntegrityStatus::kNotSet);
  if (!request->AuthenticationRequired()) {
    // This is a STUN_BINDING to from stun_port.cc or
    // the initial (unauthenticated) TURN_ALLOCATE_REQUEST.
  } else if (skip_integrity_checking) {
    // TODO(chromium:1177125): Remove below!
    // This indicates lazy test writing (not adding integrity attribute).
    // Complain, but only in debug mode (while developing).
    RTC_LOG(LS_ERROR)
        << "CheckResponse called on a passwordless request. Fix test!";
    RTC_DCHECK(false)
        << "CheckResponse called on a passwordless request. Fix test!";
  } else {
    if (msg->integrity() == StunMessage::IntegrityStatus::kNotSet) {
      // Checking status for the first time. Normal.
      msg->ValidateMessageIntegrity(request->msg()->password());
    } else if (msg->integrity() == StunMessage::IntegrityStatus::kIntegrityOk &&
               msg->password() == request->msg()->password()) {
      // Status is already checked, with the same password. This is the case
      // we would want to see happen.
    } else if (msg->integrity() ==
               StunMessage::IntegrityStatus::kIntegrityBad) {
      // This indicates that the original check had the wrong password.
      // Bad design, needs revisiting.
      // TODO(crbug.com/1177125): Fix this.
      msg->RevalidateMessageIntegrity(request->msg()->password());
    } else {
      RTC_CHECK_NOTREACHED();
    }
  }

  if (!msg->GetNonComprehendedAttributes().empty()) {
    // If a response contains unknown comprehension-required attributes, it's
    // simply discarded and the transaction is considered failed. See RFC5389
    // sections 7.3.3 and 7.3.4.
    RTC_LOG(LS_ERROR) << ": Discarding response due to unknown "
                         "comprehension-required attribute.";
    requests_.erase(iter);
    return false;
  } else if (msg->type() == GetStunSuccessResponseType(request->type())) {
    if (!msg->IntegrityOk() && !skip_integrity_checking) {
      return false;
    }
    // Erase element from hash before calling callback. This ensures
    // that the callback can modify the StunRequestManager any way it
    // sees fit.
    std::unique_ptr<StunRequest> owned_request = std::move(iter->second);
    requests_.erase(iter);
    owned_request->OnResponse(msg);
    return true;
  } else if (msg->type() == GetStunErrorResponseType(request->type())) {
    // Erase element from hash before calling callback. This ensures
    // that the callback can modify the StunRequestManager any way it
    // sees fit.
    std::unique_ptr<StunRequest> owned_request = std::move(iter->second);
    requests_.erase(iter);
    owned_request->OnErrorResponse(msg);
    return true;
  } else {
    RTC_LOG(LS_ERROR) << "Received response with wrong type: " << msg->type()
                      << " (expecting "
                      << GetStunSuccessResponseType(request->type()) << ")";
    return false;
  }
}

bool StunRequestManager::empty() const {
  RTC_DCHECK_RUN_ON(thread_);
  return requests_.empty();
}

bool StunRequestManager::CheckResponse(const char* data, size_t size) {
  RTC_DCHECK_RUN_ON(thread_);
  // Check the appropriate bytes of the stream to see if they match the
  // transaction ID of a response we are expecting.

  if (size < 20)
    return false;

  std::string id;
  id.append(data + kStunTransactionIdOffset, kStunTransactionIdLength);

  RequestMap::iterator iter = requests_.find(id);
  if (iter == requests_.end())
    return false;

  // Parse the STUN message and continue processing as usual.

  rtc::ByteBufferReader buf(
      rtc::MakeArrayView(reinterpret_cast<const uint8_t*>(data), size));
  std::unique_ptr<StunMessage> response(iter->second->msg_->CreateNew());
  if (!response->Read(&buf)) {
    RTC_LOG(LS_WARNING) << "Failed to read STUN response "
                        << rtc::hex_encode(id);
    return false;
  }

  return CheckResponse(response.get());
}

void StunRequestManager::OnRequestTimedOut(StunRequest* request) {
  RTC_DCHECK_RUN_ON(thread_);
  requests_.erase(request->id());
}

void StunRequestManager::SendPacket(const void* data,
                                    size_t size,
                                    StunRequest* request) {
  RTC_DCHECK_EQ(this, request->manager());
  send_packet_(data, size, request);
}

StunRequest::StunRequest(StunRequestManager& manager)
    : manager_(manager),
      msg_(new StunMessage(STUN_INVALID_MESSAGE_TYPE)),
      tstamp_(0),
      count_(0),
      timeout_(false) {
  RTC_DCHECK_RUN_ON(network_thread());
}

StunRequest::StunRequest(StunRequestManager& manager,
                         std::unique_ptr<StunMessage> message)
    : manager_(manager),
      msg_(std::move(message)),
      tstamp_(0),
      count_(0),
      timeout_(false) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK(!msg_->transaction_id().empty());
}

StunRequest::~StunRequest() {}

int StunRequest::type() {
  RTC_DCHECK(msg_ != NULL);
  return msg_->type();
}

const StunMessage* StunRequest::msg() const {
  return msg_.get();
}

int StunRequest::Elapsed() const {
  RTC_DCHECK_RUN_ON(network_thread());
  return static_cast<int>(rtc::TimeMillis() - tstamp_);
}

void StunRequest::SendInternal() {
  RTC_DCHECK_RUN_ON(network_thread());
  if (timeout_) {
    OnTimeout();
    manager_.OnRequestTimedOut(this);
    return;
  }

  tstamp_ = rtc::TimeMillis();

  rtc::ByteBufferWriter buf;
  msg_->Write(&buf);
  manager_.SendPacket(buf.Data(), buf.Length(), this);

  OnSent();
  SendDelayed(webrtc::TimeDelta::Millis(resend_delay()));
}

void StunRequest::SendDelayed(webrtc::TimeDelta delay) {
  network_thread()->PostDelayedTask(
      SafeTask(task_safety_.flag(), [this]() { SendInternal(); }), delay);
}

void StunRequest::Send(webrtc::TimeDelta delay) {
  RTC_DCHECK_RUN_ON(network_thread());
  RTC_DCHECK_GE(delay.ms(), 0);

  RTC_DCHECK(!task_safety_.flag()->alive()) << "Send already called?";
  task_safety_.flag()->SetAlive();

  delay.IsZero() ? SendInternal() : SendDelayed(delay);
}

void StunRequest::ResetTasksForTest() {
  RTC_DCHECK_RUN_ON(network_thread());
  task_safety_.reset(webrtc::PendingTaskSafetyFlag::CreateDetachedInactive());
  count_ = 0;
  RTC_DCHECK(!timeout_);
}

void StunRequest::OnSent() {
  RTC_DCHECK_RUN_ON(network_thread());
  count_ += 1;
  int retransmissions = (count_ - 1);
  if (retransmissions >= STUN_MAX_RETRANSMISSIONS) {
    timeout_ = true;
  }
  RTC_DLOG(LS_VERBOSE) << "Sent STUN request " << count_
                       << "; resend delay = " << resend_delay();
}

int StunRequest::resend_delay() {
  RTC_DCHECK_RUN_ON(network_thread());
  if (count_ == 0) {
    return 0;
  }
  int retransmissions = (count_ - 1);
  int rto = STUN_INITIAL_RTO << retransmissions;
  return std::min(rto, STUN_MAX_RTO);
}

void StunRequest::set_timed_out() {
  RTC_DCHECK_RUN_ON(network_thread());
  timeout_ = true;
}

}  // namespace cricket
