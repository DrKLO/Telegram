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
#include <vector>

#include "rtc_base/checks.h"
#include "rtc_base/helpers.h"
#include "rtc_base/logging.h"
#include "rtc_base/string_encode.h"
#include "rtc_base/time_utils.h"  // For TimeMillis
#include "system_wrappers/include/field_trial.h"

namespace cricket {

const uint32_t MSG_STUN_SEND = 1;

// RFC 5389 says SHOULD be 500ms.
// For years, this was 100ms, but for networks that
// experience moments of high RTT (such as 2G networks), this doesn't
// work well.
const int STUN_INITIAL_RTO = 250;  // milliseconds

// The timeout doubles each retransmission, up to this many times
// RFC 5389 says SHOULD retransmit 7 times.
// This has been 8 for years (not sure why).
const int STUN_MAX_RETRANSMISSIONS = 8;           // Total sends: 9

// We also cap the doubling, even though the standard doesn't say to.
// This has been 1.6 seconds for years, but for networks that
// experience moments of high RTT (such as 2G networks), this doesn't
// work well.
const int STUN_MAX_RTO = 8000;  // milliseconds, or 5 doublings

StunRequestManager::StunRequestManager(rtc::Thread* thread) : thread_(thread) {}

StunRequestManager::~StunRequestManager() {
  while (requests_.begin() != requests_.end()) {
    StunRequest* request = requests_.begin()->second;
    requests_.erase(requests_.begin());
    delete request;
  }
}

void StunRequestManager::Send(StunRequest* request) {
  SendDelayed(request, 0);
}

void StunRequestManager::SendDelayed(StunRequest* request, int delay) {
  request->set_manager(this);
  RTC_DCHECK(requests_.find(request->id()) == requests_.end());
  request->Construct();
  requests_[request->id()] = request;
  if (delay > 0) {
    thread_->PostDelayed(RTC_FROM_HERE, delay, request, MSG_STUN_SEND, NULL);
  } else {
    thread_->Send(RTC_FROM_HERE, request, MSG_STUN_SEND, NULL);
  }
}

void StunRequestManager::Flush(int msg_type) {
  for (const auto& kv : requests_) {
    StunRequest* request = kv.second;
    if (msg_type == kAllRequests || msg_type == request->type()) {
      thread_->Clear(request, MSG_STUN_SEND);
      thread_->Send(RTC_FROM_HERE, request, MSG_STUN_SEND, NULL);
    }
  }
}

bool StunRequestManager::HasRequest(int msg_type) {
  for (const auto& kv : requests_) {
    StunRequest* request = kv.second;
    if (msg_type == kAllRequests || msg_type == request->type()) {
      return true;
    }
  }
  return false;
}

void StunRequestManager::Remove(StunRequest* request) {
  RTC_DCHECK(request->manager() == this);
  RequestMap::iterator iter = requests_.find(request->id());
  if (iter != requests_.end()) {
    RTC_DCHECK(iter->second == request);
    requests_.erase(iter);
    thread_->Clear(request);
  }
}

void StunRequestManager::Clear() {
  std::vector<StunRequest*> requests;
  for (RequestMap::iterator i = requests_.begin(); i != requests_.end(); ++i)
    requests.push_back(i->second);

  for (uint32_t i = 0; i < requests.size(); ++i) {
    // StunRequest destructor calls Remove() which deletes requests
    // from `requests_`.
    delete requests[i];
  }
}

bool StunRequestManager::CheckResponse(StunMessage* msg) {
  RequestMap::iterator iter = requests_.find(msg->transaction_id());
  if (iter == requests_.end()) {
    // TODO(pthatcher): Log unknown responses without being too spammy
    // in the logs.
    return false;
  }

  StunRequest* request = iter->second;

  // Now that we know the request, we can see if the response is
  // integrity-protected or not.
  // For some tests, the message integrity is not set in the request.
  // Complain, and then don't check.
  bool skip_integrity_checking = false;
  if (request->msg()->integrity() == StunMessage::IntegrityStatus::kNotSet) {
    skip_integrity_checking = true;
  } else {
    msg->ValidateMessageIntegrity(request->msg()->password());
  }

  if (!msg->GetNonComprehendedAttributes().empty()) {
    // If a response contains unknown comprehension-required attributes, it's
    // simply discarded and the transaction is considered failed. See RFC5389
    // sections 7.3.3 and 7.3.4.
    RTC_LOG(LS_ERROR) << ": Discarding response due to unknown "
                         "comprehension-required attribute.";
    delete request;
    return false;
  } else if (msg->type() == GetStunSuccessResponseType(request->type())) {
    if (!msg->IntegrityOk() && !skip_integrity_checking) {
      return false;
    }
    request->OnResponse(msg);
  } else if (msg->type() == GetStunErrorResponseType(request->type())) {
    request->OnErrorResponse(msg);
  } else {
    RTC_LOG(LS_ERROR) << "Received response with wrong type: " << msg->type()
                      << " (expecting "
                      << GetStunSuccessResponseType(request->type()) << ")";
    return false;
  }

  delete request;
  return true;
}

bool StunRequestManager::CheckResponse(const char* data, size_t size) {
  // Check the appropriate bytes of the stream to see if they match the
  // transaction ID of a response we are expecting.

  if (size < 20)
    return false;

  std::string id;
  id.append(data + kStunTransactionIdOffset, kStunTransactionIdLength);

  RequestMap::iterator iter = requests_.find(id);
  if (iter == requests_.end()) {
    // TODO(pthatcher): Log unknown responses without being too spammy
    // in the logs.
    return false;
  }

  // Parse the STUN message and continue processing as usual.

  rtc::ByteBufferReader buf(data, size);
  std::unique_ptr<StunMessage> response(iter->second->msg_->CreateNew());
  if (!response->Read(&buf)) {
    RTC_LOG(LS_WARNING) << "Failed to read STUN response "
                        << rtc::hex_encode(id);
    return false;
  }

  return CheckResponse(response.get());
}

StunRequest::StunRequest()
    : count_(0),
      timeout_(false),
      manager_(0),
      msg_(new StunMessage()),
      tstamp_(0) {
  msg_->SetTransactionID(rtc::CreateRandomString(kStunTransactionIdLength));
}

StunRequest::StunRequest(StunMessage* request)
    : count_(0), timeout_(false), manager_(0), msg_(request), tstamp_(0) {
  msg_->SetTransactionID(rtc::CreateRandomString(kStunTransactionIdLength));
}

StunRequest::~StunRequest() {
  RTC_DCHECK(manager_ != NULL);
  if (manager_) {
    manager_->Remove(this);
    manager_->thread_->Clear(this);
  }
  delete msg_;
}

void StunRequest::Construct() {
  if (msg_->type() == 0) {
    Prepare(msg_);
    RTC_DCHECK(msg_->type() != 0);
  }
}

int StunRequest::type() {
  RTC_DCHECK(msg_ != NULL);
  return msg_->type();
}

const StunMessage* StunRequest::msg() const {
  return msg_;
}

StunMessage* StunRequest::mutable_msg() {
  return msg_;
}

int StunRequest::Elapsed() const {
  return static_cast<int>(rtc::TimeMillis() - tstamp_);
}

void StunRequest::set_manager(StunRequestManager* manager) {
  RTC_DCHECK(!manager_);
  manager_ = manager;
}

void StunRequest::OnMessage(rtc::Message* pmsg) {
  RTC_DCHECK(manager_ != NULL);
  RTC_DCHECK(pmsg->message_id == MSG_STUN_SEND);

  if (timeout_) {
    OnTimeout();
    delete this;
    return;
  }

  tstamp_ = rtc::TimeMillis();

  rtc::ByteBufferWriter buf;
  msg_->Write(&buf);
  manager_->SignalSendPacket(buf.Data(), buf.Length(), this);

  OnSent();
  manager_->thread_->PostDelayed(RTC_FROM_HERE, resend_delay(), this,
                                 MSG_STUN_SEND, NULL);
}

void StunRequest::OnSent() {
  count_ += 1;
  int retransmissions = (count_ - 1);
  if (retransmissions >= STUN_MAX_RETRANSMISSIONS) {
    timeout_ = true;
  }
  RTC_LOG(LS_VERBOSE) << "Sent STUN request " << count_
                      << "; resend delay = " << resend_delay();
}

int StunRequest::resend_delay() {
  if (count_ == 0) {
    return 0;
  }
  int retransmissions = (count_ - 1);
  int rto = STUN_INITIAL_RTO << retransmissions;
  return std::min(rto, STUN_MAX_RTO);
}

}  // namespace cricket
