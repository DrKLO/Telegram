/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_ASYNC_RESOLVER_H_
#define RTC_BASE_ASYNC_RESOLVER_H_

#if defined(WEBRTC_POSIX)
#include <sys/socket.h>
#elif WEBRTC_WIN
#include <winsock2.h>  // NOLINT
#endif

#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/pending_task_safety_flag.h"
#include "rtc_base/async_resolver_interface.h"
#include "rtc_base/event.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/system/rtc_export.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {

// AsyncResolver will perform async DNS resolution, signaling the result on
// the SignalDone from AsyncResolverInterface when the operation completes.
//
// This class is thread-compatible, and all methods and destruction needs to
// happen from the same rtc::Thread, except for Destroy which is allowed to
// happen on another context provided it's not happening concurrently to another
// public API call, and is the last access to the object.
class RTC_EXPORT AsyncResolver : public AsyncResolverInterface {
 public:
  AsyncResolver();
  ~AsyncResolver() override;

  void Start(const SocketAddress& addr) override;
  void Start(const SocketAddress& addr, int family) override;
  bool GetResolvedAddress(int family, SocketAddress* addr) const override;
  int GetError() const override;
  void Destroy(bool wait) override;

  const std::vector<IPAddress>& addresses() const;

 private:
  // Fwd decl.
  struct State;

  void ResolveDone(std::vector<IPAddress> addresses, int error)
      RTC_EXCLUSIVE_LOCKS_REQUIRED(sequence_checker_);
  void MaybeSelfDestruct();

  SocketAddress addr_ RTC_GUARDED_BY(sequence_checker_);
  std::vector<IPAddress> addresses_ RTC_GUARDED_BY(sequence_checker_);
  int error_ RTC_GUARDED_BY(sequence_checker_);
  bool recursion_check_ =
      false;  // Protects against SignalDone calling into Destroy.
  bool destroy_called_ = false;
  scoped_refptr<State> state_;
  RTC_NO_UNIQUE_ADDRESS webrtc::SequenceChecker sequence_checker_;
};

}  // namespace rtc

#endif  // RTC_BASE_ASYNC_RESOLVER_H_
