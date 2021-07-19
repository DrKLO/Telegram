// Copyright 2015 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "base/process/port_provider_mac.h"

namespace base {

PortProvider::PortProvider() : lock_(), observer_list_() {}
PortProvider::~PortProvider() {}

void PortProvider::AddObserver(Observer* observer) {
  base::AutoLock l(lock_);
  observer_list_.AddObserver(observer);
}

void PortProvider::RemoveObserver(Observer* observer) {
  base::AutoLock l(lock_);
  observer_list_.RemoveObserver(observer);
}

void PortProvider::NotifyObservers(ProcessHandle process) {
  base::AutoLock l(lock_);
  for (auto& observer : observer_list_)
    observer.OnReceivedTaskPort(process);
}

}  // namespace base
