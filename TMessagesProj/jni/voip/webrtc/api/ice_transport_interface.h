/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef API_ICE_TRANSPORT_INTERFACE_H_
#define API_ICE_TRANSPORT_INTERFACE_H_

#include <string>

#include "api/async_dns_resolver.h"
#include "api/async_resolver_factory.h"
#include "api/rtc_error.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/scoped_refptr.h"
#include "rtc_base/ref_count.h"

namespace cricket {
class IceTransportInternal;
class PortAllocator;
class IceControllerFactoryInterface;
class ActiveIceControllerFactoryInterface;
}  // namespace cricket

namespace webrtc {
class FieldTrialsView;

// An ICE transport, as represented to the outside world.
// This object is refcounted, and is therefore alive until the
// last holder has released it.
class IceTransportInterface : public rtc::RefCountInterface {
 public:
  // Accessor for the internal representation of an ICE transport.
  // The returned object can only be safely used on the signalling thread.
  // TODO(crbug.com/907849): Add API calls for the functions that have to
  // be exposed to clients, and stop allowing access to the
  // cricket::IceTransportInternal API.
  virtual cricket::IceTransportInternal* internal() = 0;
};

struct IceTransportInit final {
 public:
  IceTransportInit() = default;
  IceTransportInit(const IceTransportInit&) = delete;
  IceTransportInit(IceTransportInit&&) = default;
  IceTransportInit& operator=(const IceTransportInit&) = delete;
  IceTransportInit& operator=(IceTransportInit&&) = default;

  cricket::PortAllocator* port_allocator() { return port_allocator_; }
  void set_port_allocator(cricket::PortAllocator* port_allocator) {
    port_allocator_ = port_allocator;
  }

  AsyncDnsResolverFactoryInterface* async_dns_resolver_factory() {
    return async_dns_resolver_factory_;
  }
  void set_async_dns_resolver_factory(
      AsyncDnsResolverFactoryInterface* async_dns_resolver_factory) {
    RTC_DCHECK(!async_resolver_factory_);
    async_dns_resolver_factory_ = async_dns_resolver_factory;
  }
  AsyncResolverFactory* async_resolver_factory() {
    return async_resolver_factory_;
  }
  ABSL_DEPRECATED("bugs.webrtc.org/12598")
  void set_async_resolver_factory(
      AsyncResolverFactory* async_resolver_factory) {
    RTC_DCHECK(!async_dns_resolver_factory_);
    async_resolver_factory_ = async_resolver_factory;
  }

  RtcEventLog* event_log() { return event_log_; }
  void set_event_log(RtcEventLog* event_log) { event_log_ = event_log; }

  void set_ice_controller_factory(
      cricket::IceControllerFactoryInterface* ice_controller_factory) {
    ice_controller_factory_ = ice_controller_factory;
  }
  cricket::IceControllerFactoryInterface* ice_controller_factory() {
    return ice_controller_factory_;
  }

  // An active ICE controller actively manages the connection used by an ICE
  // transport, in contrast with a legacy ICE controller that only picks the
  // best connection to use or ping, and lets the transport decide when and
  // whether to switch.
  //
  // Which ICE controller is used is determined based on the field trial
  // "WebRTC-UseActiveIceController" as follows:
  //
  //   1. If the field trial is not enabled
  //      a. The legacy ICE controller factory is used if one is supplied.
  //      b. If not, a default ICE controller (BasicIceController) is
  //      constructed and used.
  //
  //   2. If the field trial is enabled
  //      a. If an active ICE controller factory is supplied, it is used and
  //      the legacy ICE controller factory is not used.
  //      b. If not, a default active ICE controller is used, wrapping over the
  //      supplied or the default legacy ICE controller.
  void set_active_ice_controller_factory(
      cricket::ActiveIceControllerFactoryInterface*
          active_ice_controller_factory) {
    active_ice_controller_factory_ = active_ice_controller_factory;
  }
  cricket::ActiveIceControllerFactoryInterface*
  active_ice_controller_factory() {
    return active_ice_controller_factory_;
  }

  const FieldTrialsView* field_trials() { return field_trials_; }
  void set_field_trials(const FieldTrialsView* field_trials) {
    field_trials_ = field_trials;
  }

 private:
  cricket::PortAllocator* port_allocator_ = nullptr;
  AsyncDnsResolverFactoryInterface* async_dns_resolver_factory_ = nullptr;
  // For backwards compatibility. Only one resolver factory can be set.
  AsyncResolverFactory* async_resolver_factory_ = nullptr;
  RtcEventLog* event_log_ = nullptr;
  cricket::IceControllerFactoryInterface* ice_controller_factory_ = nullptr;
  cricket::ActiveIceControllerFactoryInterface* active_ice_controller_factory_ =
      nullptr;
  const FieldTrialsView* field_trials_ = nullptr;
  // TODO(https://crbug.com/webrtc/12657): Redesign to have const members.
};

// TODO(qingsi): The factory interface is defined in this file instead of its
// namesake file ice_transport_factory.h to avoid the extra dependency on p2p/
// introduced there by the p2p/-dependent factory methods. Move the factory
// methods to a different file or rename it.
class IceTransportFactory {
 public:
  virtual ~IceTransportFactory() = default;
  // As a refcounted object, the returned ICE transport may outlive the host
  // construct into which its reference is given, e.g. a peer connection. As a
  // result, the returned ICE transport should not hold references to any object
  // that the transport does not own and that has a lifetime bound to the host
  // construct. Also, assumptions on the thread safety of the returned transport
  // should be clarified by implementations. For example, a peer connection
  // requires the returned transport to be constructed and destroyed on the
  // network thread and an ICE transport factory that intends to work with a
  // peer connection should offer transports compatible with these assumptions.
  virtual rtc::scoped_refptr<IceTransportInterface> CreateIceTransport(
      const std::string& transport_name,
      int component,
      IceTransportInit init) = 0;
};

}  // namespace webrtc
#endif  // API_ICE_TRANSPORT_INTERFACE_H_
