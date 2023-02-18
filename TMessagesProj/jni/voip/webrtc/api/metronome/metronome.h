/*
 *  Copyright (c) 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_METRONOME_METRONOME_H_
#define API_METRONOME_METRONOME_H_

#include "api/task_queue/task_queue_base.h"
#include "api/units/time_delta.h"
#include "rtc_base/system/rtc_export.h"

namespace webrtc {

// The Metronome posts OnTick() on task queues provided by its listeners' task
// queue periodically. The metronome can be used as an alternative to using
// PostDelayedTask on a thread or task queue for coalescing work and reducing
// the number of idle-wakeups.
//
// Listeners can be added and removed from any sequence, but it is illegal to
// remove a listener from an OnTick invocation.
//
// The metronome concept is still under experimentation, and may not be availble
// in all platforms or applications. See https://crbug.com/1253787 for more
// details.
//
// Metronome implementations must be thread-safe.
class RTC_EXPORT Metronome {
 public:
  class RTC_EXPORT TickListener {
   public:
    virtual ~TickListener() = default;

    // OnTick is run on the task queue provided by OnTickTaskQueue each time the
    // metronome ticks.
    virtual void OnTick() = 0;

    // The task queue that OnTick will run on. Must not be null.
    virtual TaskQueueBase* OnTickTaskQueue() = 0;
  };

  virtual ~Metronome() = default;

  // Adds a tick listener to the metronome. Once this method has returned
  // OnTick will be invoked on each metronome tick. A listener may
  // only be added to the metronome once.
  virtual void AddListener(TickListener* listener) = 0;

  // Removes the tick listener from the metronome. Once this method has returned
  // OnTick will never be called again. This method must not be called from
  // within OnTick.
  virtual void RemoveListener(TickListener* listener) = 0;

  // Returns the current tick period of the metronome.
  virtual TimeDelta TickPeriod() const = 0;
};

}  // namespace webrtc

#endif  // API_METRONOME_METRONOME_H_
