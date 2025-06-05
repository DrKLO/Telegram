/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SOCKET_SERVER_H_
#define RTC_BASE_SOCKET_SERVER_H_

#include <memory>

#include "api/units/time_delta.h"
#include "rtc_base/event.h"
#include "rtc_base/socket_factory.h"

namespace rtc {

class Thread;
// Needs to be forward declared because there's a circular dependency between
// NetworkMonitor and Thread.
// TODO(deadbeef): Fix this.
class NetworkBinderInterface;

// Provides the ability to wait for activity on a set of sockets.  The Thread
// class provides a nice wrapper on a socket server.
//
// The server is also a socket factory.  The sockets it creates will be
// notified of asynchronous I/O from this server's Wait method.
class SocketServer : public SocketFactory {
 public:
  static constexpr webrtc::TimeDelta kForever = rtc::Event::kForever;

  static std::unique_ptr<SocketServer> CreateDefault();
  // When the socket server is installed into a Thread, this function is called
  // to allow the socket server to use the thread's message queue for any
  // messaging that it might need to perform. It is also called with a null
  // argument before the thread is destroyed.
  virtual void SetMessageQueue(Thread* queue) {}

  // Sleeps until:
  //  1) `max_wait_duration` has elapsed (unless `max_wait_duration` ==
  //  `kForever`)
  // 2) WakeUp() is called
  // While sleeping, I/O is performed if process_io is true.
  virtual bool Wait(webrtc::TimeDelta max_wait_duration, bool process_io) = 0;

  // Causes the current wait (if one is in progress) to wake up.
  virtual void WakeUp() = 0;

  // A network binder will bind the created sockets to a network.
  // It is only used in PhysicalSocketServer.
  void set_network_binder(NetworkBinderInterface* binder) {
    network_binder_ = binder;
  }
  NetworkBinderInterface* network_binder() const { return network_binder_; }

 private:
  NetworkBinderInterface* network_binder_ = nullptr;
};

}  // namespace rtc

#endif  // RTC_BASE_SOCKET_SERVER_H_
