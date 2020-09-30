/*
 *  Copyright 2017 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SIGSLOT_REPEATER_H__
#define RTC_BASE_SIGSLOT_REPEATER_H__

// repeaters are both signals and slots, which are designed as intermediate
// pass-throughs for signals and slots which don't know about each other (for
// modularity or encapsulation).  This eliminates the need to declare a signal
// handler whose sole purpose is to fire another signal.  The repeater connects
// to the originating signal using the 'repeat' method.  When the repeated
// signal fires, the repeater will also fire.
//
// TODO(deadbeef): Actually use this, after we decide on some style points on
// using signals, so it doesn't get deleted again.

#include "rtc_base/third_party/sigslot/sigslot.h"

namespace sigslot {

template <class mt_policy, typename... Args>
class repeater_with_thread_policy
    : public signal_with_thread_policy<mt_policy, Args...>,
      public has_slots<mt_policy> {
 private:
  // These typedefs are just to make the code below more readable. Code using
  // repeaters shouldn't need to reference these types directly.
  typedef signal_with_thread_policy<mt_policy, Args...> base_type;
  typedef repeater_with_thread_policy<mt_policy, Args...> this_type;

 public:
  repeater_with_thread_policy() {}
  repeater_with_thread_policy(const this_type& s) : base_type(s) {}

  void reemit(Args... args) { base_type::emit(args...); }
  void repeat(base_type& s) { s.connect(this, &this_type::reemit); }
  void stop(base_type& s) { s.disconnect(this); }
};

// Alias with default thread policy. Needed because both default arguments
// and variadic template arguments must go at the end of the list, so we
// can't have both at once.
template <typename... Args>
using repeater =
    repeater_with_thread_policy<SIGSLOT_DEFAULT_MT_POLICY, Args...>;

}  // namespace sigslot

#endif  // RTC_BASE_SIGSLOT_REPEATER_H__
