/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_CONNECTION_CONTEXT_H_
#define PC_CONNECTION_CONTEXT_H_

#include <memory>
#include <string>

#include "api/environment/environment.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/ref_counted_base.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/transport/sctp_transport_factory_interface.h"
#include "media/base/media_engine.h"
#include "p2p/base/basic_packet_socket_factory.h"
#include "rtc_base/checks.h"
#include "rtc_base/network.h"
#include "rtc_base/network_monitor_factory.h"
#include "rtc_base/rtc_certificate_generator.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace rtc {
class BasicPacketSocketFactory;
class UniqueRandomIdGenerator;
}  // namespace rtc

namespace webrtc {

// This class contains resources needed by PeerConnection and associated
// objects. A reference to this object is passed to each PeerConnection. The
// methods on this object are assumed not to change the state in any way that
// interferes with the operation of other PeerConnections.
//
// This class must be created and destroyed on the signaling thread.
class ConnectionContext final
    : public rtc::RefCountedNonVirtual<ConnectionContext> {
 public:
  // Creates a ConnectionContext. May return null if initialization fails.
  // The Dependencies class allows simple management of all new dependencies
  // being added to the ConnectionContext.
  static rtc::scoped_refptr<ConnectionContext> Create(
      const Environment& env,
      PeerConnectionFactoryDependencies* dependencies);

  // This class is not copyable or movable.
  ConnectionContext(const ConnectionContext&) = delete;
  ConnectionContext& operator=(const ConnectionContext&) = delete;

  // Functions called from PeerConnection and friends
  SctpTransportFactoryInterface* sctp_transport_factory() const {
    return sctp_factory_.get();
  }

  cricket::MediaEngineInterface* media_engine() const {
    return media_engine_.get();
  }

  rtc::Thread* signaling_thread() { return signaling_thread_; }
  const rtc::Thread* signaling_thread() const { return signaling_thread_; }
  rtc::Thread* worker_thread() { return worker_thread_.get(); }
  const rtc::Thread* worker_thread() const { return worker_thread_.get(); }
  rtc::Thread* network_thread() { return network_thread_; }
  const rtc::Thread* network_thread() const { return network_thread_; }

  // Environment associated with the PeerConnectionFactory.
  // Note: environments are different for different PeerConnections,
  // but they are not supposed to change after creating the PeerConnection.
  const Environment& env() const { return env_; }

  // Accessors only used from the PeerConnectionFactory class
  rtc::NetworkManager* default_network_manager() {
    RTC_DCHECK_RUN_ON(signaling_thread_);
    return default_network_manager_.get();
  }
  rtc::PacketSocketFactory* default_socket_factory() {
    RTC_DCHECK_RUN_ON(signaling_thread_);
    return default_socket_factory_.get();
  }
  MediaFactory* call_factory() {
    RTC_DCHECK_RUN_ON(worker_thread());
    return call_factory_.get();
  }
  rtc::UniqueRandomIdGenerator* ssrc_generator() { return &ssrc_generator_; }
  // Note: There is lots of code that wants to know whether or not we
  // use RTX, but so far, no code has been found that sets it to false.
  // Kept in the API in order to ease introduction if we want to resurrect
  // the functionality.
  bool use_rtx() { return use_rtx_; }

  // For use by tests.
  void set_use_rtx(bool use_rtx) { use_rtx_ = use_rtx; }

 protected:
  ConnectionContext(const Environment& env,
                    PeerConnectionFactoryDependencies* dependencies);

  friend class rtc::RefCountedNonVirtual<ConnectionContext>;
  ~ConnectionContext();

 private:
  // The following three variables are used to communicate between the
  // constructor and the destructor, and are never exposed externally.
  bool wraps_current_thread_;
  std::unique_ptr<rtc::SocketFactory> owned_socket_factory_;
  std::unique_ptr<rtc::Thread> owned_network_thread_
      RTC_GUARDED_BY(signaling_thread_);
  rtc::Thread* const network_thread_;
  AlwaysValidPointer<rtc::Thread> const worker_thread_;
  rtc::Thread* const signaling_thread_;

  const Environment env_;

  // This object is const over the lifetime of the ConnectionContext, and is
  // only altered in the destructor.
  std::unique_ptr<cricket::MediaEngineInterface> media_engine_;

  // This object should be used to generate any SSRC that is not explicitly
  // specified by the user (or by the remote party).
  // TODO(bugs.webrtc.org/12666): This variable is used from both the signaling
  // and worker threads. See if we can't restrict usage to a single thread.
  rtc::UniqueRandomIdGenerator ssrc_generator_;
  std::unique_ptr<rtc::NetworkMonitorFactory> const network_monitor_factory_
      RTC_GUARDED_BY(signaling_thread_);
  std::unique_ptr<rtc::NetworkManager> default_network_manager_
      RTC_GUARDED_BY(signaling_thread_);
  std::unique_ptr<MediaFactory> const call_factory_
      RTC_GUARDED_BY(worker_thread());

  std::unique_ptr<rtc::PacketSocketFactory> default_socket_factory_
      RTC_GUARDED_BY(signaling_thread_);
  std::unique_ptr<SctpTransportFactoryInterface> const sctp_factory_;

  // Controls whether to announce support for the the rfc4588 payload format
  // for retransmitted video packets.
  bool use_rtx_;
};

}  // namespace webrtc

#endif  // PC_CONNECTION_CONTEXT_H_
