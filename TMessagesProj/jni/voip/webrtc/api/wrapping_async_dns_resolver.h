/*
 *  Copyright 2021 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_WRAPPING_ASYNC_DNS_RESOLVER_H_
#define API_WRAPPING_ASYNC_DNS_RESOLVER_H_

#include <functional>
#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "api/async_dns_resolver.h"
#include "api/sequence_checker.h"
#include "rtc_base/async_resolver.h"
#include "rtc_base/async_resolver_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread_annotations.h"

// This file defines a DNS resolver that wraps an old-style
// AsyncResolver.
// It is part of the conversion to the newer interface, and will go away
// once conversion is finished.
// TODO(bugs.webrtc.org/12598): Delete this API.

namespace webrtc {

class WrappingAsyncDnsResolver;

class RTC_EXPORT WrappingAsyncDnsResolverResult
    : public AsyncDnsResolverResult {
 public:
  explicit WrappingAsyncDnsResolverResult(WrappingAsyncDnsResolver* owner)
      : owner_(owner) {}
  ~WrappingAsyncDnsResolverResult() {}

  // Note: Inline declaration not possible, since it refers to
  // WrappingAsyncDnsResolver.
  bool GetResolvedAddress(int family, rtc::SocketAddress* addr) const override;
  int GetError() const override;

 private:
  WrappingAsyncDnsResolver* const owner_;
};

class RTC_EXPORT WrappingAsyncDnsResolver : public AsyncDnsResolverInterface,
                                            public sigslot::has_slots<> {
 public:
  explicit WrappingAsyncDnsResolver(rtc::AsyncResolverInterface* wrapped)
      : wrapped_(absl::WrapUnique(wrapped)), result_(this) {}

  ~WrappingAsyncDnsResolver() override {
    // Workaround to get around the fact that sigslot-using objects can't be
    // destroyed from within their callback: Alert class users early.
    // TODO(bugs.webrtc.org/12651): Delete this class once the sigslot users are
    // gone.
    RTC_CHECK(!within_resolve_result_);
    wrapped_.release()->Destroy(false);
  }

  void Start(const rtc::SocketAddress& addr,
             std::function<void()> callback) override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    PrepareToResolve(std::move(callback));
    wrapped_->Start(addr);
  }

  void Start(const rtc::SocketAddress& addr,
             int family,
             std::function<void()> callback) override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    PrepareToResolve(std::move(callback));
    wrapped_->Start(addr, family);
  }

  const AsyncDnsResolverResult& result() const override {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    RTC_DCHECK_EQ(State::kResolved, state_);
    return result_;
  }

 private:
  enum class State { kNotStarted, kStarted, kResolved };

  friend class WrappingAsyncDnsResolverResult;
  // For use by WrappingAsyncDnsResolverResult
  rtc::AsyncResolverInterface* wrapped() const {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    return wrapped_.get();
  }

  void PrepareToResolve(std::function<void()> callback) {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    RTC_DCHECK_EQ(State::kNotStarted, state_);
    state_ = State::kStarted;
    callback_ = std::move(callback);
    wrapped_->SignalDone.connect(this,
                                 &WrappingAsyncDnsResolver::OnResolveResult);
  }

  void OnResolveResult(rtc::AsyncResolverInterface* ref) {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    RTC_DCHECK(state_ == State::kStarted);
    RTC_DCHECK_EQ(ref, wrapped_.get());
    state_ = State::kResolved;
    within_resolve_result_ = true;
    callback_();
    within_resolve_result_ = false;
  }

  // The class variables need to be accessed on a single thread.
  SequenceChecker sequence_checker_;
  std::function<void()> callback_ RTC_GUARDED_BY(sequence_checker_);
  std::unique_ptr<rtc::AsyncResolverInterface> wrapped_
      RTC_GUARDED_BY(sequence_checker_);
  State state_ RTC_GUARDED_BY(sequence_checker_) = State::kNotStarted;
  WrappingAsyncDnsResolverResult result_ RTC_GUARDED_BY(sequence_checker_);
  bool within_resolve_result_ RTC_GUARDED_BY(sequence_checker_) = false;
};

}  // namespace webrtc

#endif  // API_WRAPPING_ASYNC_DNS_RESOLVER_H_
