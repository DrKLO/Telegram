/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "p2p/base/basic_async_resolver_factory.h"

#include <memory>
#include <utility>

#include "absl/memory/memory.h"
#include "api/async_dns_resolver.h"
#include "rtc_base/async_resolver.h"
#include "rtc_base/logging.h"

namespace webrtc {

rtc::AsyncResolverInterface* BasicAsyncResolverFactory::Create() {
  return new rtc::AsyncResolver();
}

class WrappingAsyncDnsResolver;

class WrappingAsyncDnsResolverResult : public AsyncDnsResolverResult {
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

class WrappingAsyncDnsResolver : public AsyncDnsResolverInterface,
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
    RTC_DCHECK_EQ(State::kNotStarted, state_);
    state_ = State::kStarted;
    callback_ = callback;
    wrapped_->SignalDone.connect(this,
                                 &WrappingAsyncDnsResolver::OnResolveResult);
    wrapped_->Start(addr);
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

bool WrappingAsyncDnsResolverResult::GetResolvedAddress(
    int family,
    rtc::SocketAddress* addr) const {
  if (!owner_->wrapped()) {
    return false;
  }
  return owner_->wrapped()->GetResolvedAddress(family, addr);
}

int WrappingAsyncDnsResolverResult::GetError() const {
  if (!owner_->wrapped()) {
    return -1;  // FIXME: Find a code that makes sense.
  }
  return owner_->wrapped()->GetError();
}

std::unique_ptr<webrtc::AsyncDnsResolverInterface>
WrappingAsyncDnsResolverFactory::Create() {
  return std::make_unique<WrappingAsyncDnsResolver>(wrapped_factory_->Create());
}

std::unique_ptr<webrtc::AsyncDnsResolverInterface>
WrappingAsyncDnsResolverFactory::CreateAndResolve(
    const rtc::SocketAddress& addr,
    std::function<void()> callback) {
  std::unique_ptr<webrtc::AsyncDnsResolverInterface> resolver = Create();
  resolver->Start(addr, callback);
  return resolver;
}

}  // namespace webrtc
