/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef P2P_BASE_STUN_REQUEST_H_
#define P2P_BASE_STUN_REQUEST_H_

#include <stddef.h>
#include <stdint.h>

#include <map>
#include <string>

#include "api/transport/stun.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"

namespace cricket {

class StunRequest;

const int kAllRequests = 0;

// Total max timeouts: 39.75 seconds
// For years, this was 9.5 seconds, but for networks that experience moments of
// high RTT (such as 40s on 2G networks), this doesn't work well.
const int STUN_TOTAL_TIMEOUT = 39750;  // milliseconds

// Manages a set of STUN requests, sending and resending until we receive a
// response or determine that the request has timed out.
class StunRequestManager {
 public:
  explicit StunRequestManager(rtc::Thread* thread);
  ~StunRequestManager();

  // Starts sending the given request (perhaps after a delay).
  void Send(StunRequest* request);
  void SendDelayed(StunRequest* request, int delay);

  // If |msg_type| is kAllRequests, sends all pending requests right away.
  // Otherwise, sends those that have a matching type right away.
  // Only for testing.
  void Flush(int msg_type);

  // Returns true if at least one request with |msg_type| is scheduled for
  // transmission. For testing only.
  bool HasRequest(int msg_type);

  // Removes a stun request that was added previously.  This will happen
  // automatically when a request succeeds, fails, or times out.
  void Remove(StunRequest* request);

  // Removes all stun requests that were added previously.
  void Clear();

  // Determines whether the given message is a response to one of the
  // outstanding requests, and if so, processes it appropriately.
  bool CheckResponse(StunMessage* msg);
  bool CheckResponse(const char* data, size_t size);

  bool empty() { return requests_.empty(); }

  // Set the Origin header for outgoing stun messages.
  void set_origin(const std::string& origin) { origin_ = origin; }

  // Raised when there are bytes to be sent.
  sigslot::signal3<const void*, size_t, StunRequest*> SignalSendPacket;

 private:
  typedef std::map<std::string, StunRequest*> RequestMap;

  rtc::Thread* thread_;
  RequestMap requests_;
  std::string origin_;

  friend class StunRequest;
};

// Represents an individual request to be sent.  The STUN message can either be
// constructed beforehand or built on demand.
class StunRequest : public rtc::MessageHandler {
 public:
  StunRequest();
  explicit StunRequest(StunMessage* request);
  ~StunRequest() override;

  // Causes our wrapped StunMessage to be Prepared
  void Construct();

  // The manager handling this request (if it has been scheduled for sending).
  StunRequestManager* manager() { return manager_; }

  // Returns the transaction ID of this request.
  const std::string& id() { return msg_->transaction_id(); }

  // Returns the reduced transaction ID of this request.
  uint32_t reduced_transaction_id() const {
    return msg_->reduced_transaction_id();
  }

  // the origin value
  const std::string& origin() const { return origin_; }
  void set_origin(const std::string& origin) { origin_ = origin; }

  // Returns the STUN type of the request message.
  int type();

  // Returns a const pointer to |msg_|.
  const StunMessage* msg() const;

  // Returns a mutable pointer to |msg_|.
  StunMessage* mutable_msg();

  // Time elapsed since last send (in ms)
  int Elapsed() const;

 protected:
  int count_;
  bool timeout_;
  std::string origin_;

  // Fills in a request object to be sent.  Note that request's transaction ID
  // will already be set and cannot be changed.
  virtual void Prepare(StunMessage* request) {}

  // Called when the message receives a response or times out.
  virtual void OnResponse(StunMessage* response) {}
  virtual void OnErrorResponse(StunMessage* response) {}
  virtual void OnTimeout() {}
  // Called when the message is sent.
  virtual void OnSent();
  // Returns the next delay for resends.
  virtual int resend_delay();

 private:
  void set_manager(StunRequestManager* manager);

  // Handles messages for sending and timeout.
  void OnMessage(rtc::Message* pmsg) override;

  StunRequestManager* manager_;
  StunMessage* msg_;
  int64_t tstamp_;

  friend class StunRequestManager;
};

}  // namespace cricket

#endif  // P2P_BASE_STUN_REQUEST_H_
