// Copyright (c) 2011 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/memory/ref_counted.h"

#include <limits>
#include <type_traits>

#include "base/threading/thread_collision_warner.h"

namespace base {
namespace {

#if DCHECK_IS_ON()
std::atomic_int g_cross_thread_ref_count_access_allow_count(0);
#endif

}  // namespace

namespace subtle {

bool RefCountedThreadSafeBase::HasOneRef() const {
  return ref_count_.IsOne();
}

bool RefCountedThreadSafeBase::HasAtLeastOneRef() const {
  return !ref_count_.IsZero();
}

#if DCHECK_IS_ON()
RefCountedThreadSafeBase::~RefCountedThreadSafeBase() {
  DCHECK(in_dtor_) << "RefCountedThreadSafe object deleted without "
                      "calling Release()";
}
#endif

// For security and correctness, we check the arithmetic on ref counts.
//
// In an attempt to avoid binary bloat (from inlining the `CHECK`), we define
// these functions out-of-line. However, compilers are wily. Further testing may
// show that `NOINLINE` helps or hurts.
//
#if defined(ARCH_CPU_64_BITS)
void RefCountedBase::AddRefImpl() const {
  // An attacker could induce use-after-free bugs, and potentially exploit them,
  // by creating so many references to a ref-counted object that the reference
  // count overflows. On 32-bit architectures, there is not enough address space
  // to succeed. But on 64-bit architectures, it might indeed be possible.
  // Therefore, we can elide the check for arithmetic overflow on 32-bit, but we
  // must check on 64-bit.
  //
  // Make sure the addition didn't wrap back around to 0. This form of check
  // works because we assert that `ref_count_` is an unsigned integer type.
  CHECK(++ref_count_ != 0);
}

void RefCountedBase::ReleaseImpl() const {
  // Make sure the subtraction didn't wrap back around from 0 to the max value.
  // That could cause memory leaks, and may induce application-semantic
  // correctness or safety bugs. (E.g. what if we really needed that object to
  // be destroyed at the right time?)
  //
  // Note that unlike with overflow, underflow could also happen on 32-bit
  // architectures. Arguably, we should do this check on32-bit machines too.
  CHECK(--ref_count_ != std::numeric_limits<decltype(ref_count_)>::max());
}
#endif

#if !defined(ARCH_CPU_X86_FAMILY)
bool RefCountedThreadSafeBase::Release() const {
  return ReleaseImpl();
}
void RefCountedThreadSafeBase::AddRef() const {
  AddRefImpl();
}
void RefCountedThreadSafeBase::AddRefWithCheck() const {
  AddRefWithCheckImpl();
}
#endif

#if DCHECK_IS_ON()
bool RefCountedBase::CalledOnValidSequence() const {
  return sequence_checker_.CalledOnValidSequence() ||
         g_cross_thread_ref_count_access_allow_count.load() != 0;
}
#endif

}  // namespace subtle

#if DCHECK_IS_ON()
ScopedAllowCrossThreadRefCountAccess::ScopedAllowCrossThreadRefCountAccess() {
  ++g_cross_thread_ref_count_access_allow_count;
}

ScopedAllowCrossThreadRefCountAccess::~ScopedAllowCrossThreadRefCountAccess() {
  --g_cross_thread_ref_count_access_allow_count;
}
#endif

}  // namespace base
