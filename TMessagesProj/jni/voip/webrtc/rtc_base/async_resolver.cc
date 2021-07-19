/*
 *  Copyright 2008 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/async_resolver.h"

#include <memory>
#include <string>
#include <utility>

#include "api/ref_counted_base.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/thread_annotations.h"

#if defined(WEBRTC_WIN)
#include <ws2spi.h>
#include <ws2tcpip.h>

#include "rtc_base/win32.h"
#endif
#if defined(WEBRTC_POSIX) && !defined(__native_client__)
#if defined(WEBRTC_ANDROID)
#include "rtc_base/ifaddrs_android.h"
#else
#include <ifaddrs.h>
#endif
#endif  // defined(WEBRTC_POSIX) && !defined(__native_client__)

#include "api/task_queue/task_queue_base.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/third_party/sigslot/sigslot.h"  // for signal_with_thread...

namespace rtc {

int ResolveHostname(const std::string& hostname,
                    int family,
                    std::vector<IPAddress>* addresses) {
#ifdef __native_client__
  RTC_NOTREACHED();
  RTC_LOG(LS_WARNING) << "ResolveHostname() is not implemented for NaCl";
  return -1;
#else   // __native_client__
  if (!addresses) {
    return -1;
  }
  addresses->clear();
  struct addrinfo* result = nullptr;
  struct addrinfo hints = {0};
  hints.ai_family = family;
  // |family| here will almost always be AF_UNSPEC, because |family| comes from
  // AsyncResolver::addr_.family(), which comes from a SocketAddress constructed
  // with a hostname. When a SocketAddress is constructed with a hostname, its
  // family is AF_UNSPEC. However, if someday in the future we construct
  // a SocketAddress with both a hostname and a family other than AF_UNSPEC,
  // then it would be possible to get a specific family value here.

  // The behavior of AF_UNSPEC is roughly "get both ipv4 and ipv6", as
  // documented by the various operating systems:
  // Linux: http://man7.org/linux/man-pages/man3/getaddrinfo.3.html
  // Windows: https://msdn.microsoft.com/en-us/library/windows/desktop/
  // ms738520(v=vs.85).aspx
  // Mac: https://developer.apple.com/legacy/library/documentation/Darwin/
  // Reference/ManPages/man3/getaddrinfo.3.html
  // Android (source code, not documentation):
  // https://android.googlesource.com/platform/bionic/+/
  // 7e0bfb511e85834d7c6cb9631206b62f82701d60/libc/netbsd/net/getaddrinfo.c#1657
  hints.ai_flags = AI_ADDRCONFIG;
  int ret = getaddrinfo(hostname.c_str(), nullptr, &hints, &result);
  if (ret != 0) {
    return ret;
  }
  struct addrinfo* cursor = result;
  for (; cursor; cursor = cursor->ai_next) {
    if (family == AF_UNSPEC || cursor->ai_family == family) {
      IPAddress ip;
      if (IPFromAddrInfo(cursor, &ip)) {
        addresses->push_back(ip);
      }
    }
  }
  freeaddrinfo(result);
  return 0;
#endif  // !__native_client__
}

struct AsyncResolver::State : public RefCountedBase {
  webrtc::Mutex mutex;
  enum class Status {
    kLive,
    kDead
  } status RTC_GUARDED_BY(mutex) = Status::kLive;
};

AsyncResolver::AsyncResolver() : error_(-1), state_(new State) {}

AsyncResolver::~AsyncResolver() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);

  // Ensure the thread isn't using a stale reference to the current task queue,
  // or calling into ResolveDone post destruction.
  webrtc::MutexLock lock(&state_->mutex);
  state_->status = State::Status::kDead;
}

void RunResolution(void* obj) {
  std::function<void()>* function_ptr =
      static_cast<std::function<void()>*>(obj);
  (*function_ptr)();
  delete function_ptr;
}

void AsyncResolver::Start(const SocketAddress& addr) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!destroy_called_);
  addr_ = addr;
  PlatformThread::SpawnDetached(
      [this, addr, caller_task_queue = webrtc::TaskQueueBase::Current(),
       state = state_] {
        std::vector<IPAddress> addresses;
        int error =
            ResolveHostname(addr.hostname().c_str(), addr.family(), &addresses);
        webrtc::MutexLock lock(&state->mutex);
        if (state->status == State::Status::kLive) {
          caller_task_queue->PostTask(webrtc::ToQueuedTask(
              [this, error, addresses = std::move(addresses), state] {
                bool live;
                {
                  // ResolveDone can lead to instance destruction, so make sure
                  // we don't deadlock.
                  webrtc::MutexLock lock(&state->mutex);
                  live = state->status == State::Status::kLive;
                }
                if (live) {
                  RTC_DCHECK_RUN_ON(&sequence_checker_);
                  ResolveDone(std::move(addresses), error);
                }
              }));
        }
      },
      "AsyncResolver");
}

bool AsyncResolver::GetResolvedAddress(int family, SocketAddress* addr) const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!destroy_called_);
  if (error_ != 0 || addresses_.empty())
    return false;

  *addr = addr_;
  for (size_t i = 0; i < addresses_.size(); ++i) {
    if (family == addresses_[i].family()) {
      addr->SetResolvedIP(addresses_[i]);
      return true;
    }
  }
  return false;
}

int AsyncResolver::GetError() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!destroy_called_);
  return error_;
}

void AsyncResolver::Destroy(bool wait) {
  // Some callers have trouble guaranteeing that Destroy is called on the
  // sequence guarded by |sequence_checker_|.
  // RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!destroy_called_);
  destroy_called_ = true;
  MaybeSelfDestruct();
}

const std::vector<IPAddress>& AsyncResolver::addresses() const {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  RTC_DCHECK(!destroy_called_);
  return addresses_;
}

void AsyncResolver::ResolveDone(std::vector<IPAddress> addresses, int error) {
  addresses_ = addresses;
  error_ = error;
  recursion_check_ = true;
  SignalDone(this);
  MaybeSelfDestruct();
}

void AsyncResolver::MaybeSelfDestruct() {
  if (!recursion_check_) {
    delete this;
  } else {
    recursion_check_ = false;
  }
}

}  // namespace rtc
