// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef BASE_PROCESS_PORT_PROVIDER_MAC_H_
#define BASE_PROCESS_PORT_PROVIDER_MAC_H_

#include <mach/mach.h>

#include "base/base_export.h"
#include "base/macros.h"
#include "base/observer_list.h"
#include "base/process/process_handle.h"
#include "base/synchronization/lock.h"

namespace base {

// Abstract base class that provides a mapping from ProcessHandle (pid_t) to the
// Mach task port. This replicates task_for_pid(), which requires root
// privileges.
class BASE_EXPORT PortProvider {
 public:
  PortProvider();
  virtual ~PortProvider();

  class Observer {
   public:
    virtual ~Observer() {}
    // Called by the PortProvider to notify observers that the task port was
    // received for a given process.
    // No guarantees are made about the thread on which this notification will
    // be sent.
    // Observers must not call AddObserver() or RemoveObserver() in this
    // callback, as doing so will deadlock.
    virtual void OnReceivedTaskPort(ProcessHandle process) = 0;
  };

  // Returns the mach task port for |process| if possible, or else
  // |MACH_PORT_NULL|.
  virtual mach_port_t TaskForPid(ProcessHandle process) const = 0;

  // Observer interface.
  void AddObserver(Observer* observer);
  void RemoveObserver(Observer* observer);

 protected:
  // Called by subclasses to send a notification to observers.
  void NotifyObservers(ProcessHandle process);

 private:
  // ObserverList is not thread-safe, so |lock_| ensures consistency of
  // |observer_list_|.
  base::Lock lock_;
  base::ObserverList<Observer>::Unchecked observer_list_;

  DISALLOW_COPY_AND_ASSIGN(PortProvider);
};

}  // namespace base

#endif  // BASE_PROCESS_PORT_PROVIDER_MAC_H_
