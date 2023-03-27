/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/connection_context.h"

#include <type_traits>
#include <utility>
#include <vector>

#include "api/transport/field_trial_based_config.h"
#include "media/base/media_engine.h"
#include "media/sctp/sctp_transport_factory.h"
#include "rtc_base/helpers.h"
#include "rtc_base/internal/default_socket_server.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

rtc::Thread* MaybeStartNetworkThread(
    rtc::Thread* old_thread,
    std::unique_ptr<rtc::SocketFactory>& socket_factory_holder,
    std::unique_ptr<rtc::Thread>& thread_holder) {
  if (old_thread) {
    return old_thread;
  }
  std::unique_ptr<rtc::SocketServer> socket_server =
      rtc::CreateDefaultSocketServer();
  thread_holder = std::make_unique<rtc::Thread>(socket_server.get());
  socket_factory_holder = std::move(socket_server);

  thread_holder->SetName("pc_network_thread", nullptr);
  thread_holder->Start();
  return thread_holder.get();
}

rtc::Thread* MaybeWrapThread(rtc::Thread* signaling_thread,
                             bool& wraps_current_thread) {
  wraps_current_thread = false;
  if (signaling_thread) {
    return signaling_thread;
  }
  auto this_thread = rtc::Thread::Current();
  if (!this_thread) {
    // If this thread isn't already wrapped by an rtc::Thread, create a
    // wrapper and own it in this class.
    this_thread = rtc::ThreadManager::Instance()->WrapCurrentThread();
    wraps_current_thread = true;
  }
  return this_thread;
}

std::unique_ptr<SctpTransportFactoryInterface> MaybeCreateSctpFactory(
    std::unique_ptr<SctpTransportFactoryInterface> factory,
    rtc::Thread* network_thread,
    const FieldTrialsView& field_trials) {
  if (factory) {
    return factory;
  }
#ifdef WEBRTC_HAVE_SCTP
  return std::make_unique<cricket::SctpTransportFactory>(network_thread);
#else
  return nullptr;
#endif
}

}  // namespace

// Static
rtc::scoped_refptr<ConnectionContext> ConnectionContext::Create(
    PeerConnectionFactoryDependencies* dependencies) {
  return rtc::scoped_refptr<ConnectionContext>(
      new ConnectionContext(dependencies));
}

ConnectionContext::ConnectionContext(
    PeerConnectionFactoryDependencies* dependencies)
    : network_thread_(MaybeStartNetworkThread(dependencies->network_thread,
                                              owned_socket_factory_,
                                              owned_network_thread_)),
      worker_thread_(dependencies->worker_thread,
                     []() {
                       auto thread_holder = rtc::Thread::Create();
                       thread_holder->SetName("pc_worker_thread", nullptr);
                       thread_holder->Start();
                       return thread_holder;
                     }),
      signaling_thread_(MaybeWrapThread(dependencies->signaling_thread,
                                        wraps_current_thread_)),
      trials_(dependencies->trials ? std::move(dependencies->trials)
                                   : std::make_unique<FieldTrialBasedConfig>()),
      media_engine_(std::move(dependencies->media_engine)),
      network_monitor_factory_(
          std::move(dependencies->network_monitor_factory)),
      default_network_manager_(std::move(dependencies->network_manager)),
      call_factory_(std::move(dependencies->call_factory)),
      default_socket_factory_(std::move(dependencies->packet_socket_factory)),
      sctp_factory_(
          MaybeCreateSctpFactory(std::move(dependencies->sctp_factory),
                                 network_thread(),
                                 *trials_.get())) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(!(default_network_manager_ && network_monitor_factory_))
      << "You can't set both network_manager and network_monitor_factory.";

  signaling_thread_->AllowInvokesToThread(worker_thread());
  signaling_thread_->AllowInvokesToThread(network_thread_);
  worker_thread_->AllowInvokesToThread(network_thread_);
  if (!network_thread_->IsCurrent()) {
    // network_thread_->IsCurrent() == true means signaling_thread_ is
    // network_thread_. In this case, no further action is required as
    // signaling_thread_ can already invoke network_thread_.
    network_thread_->PostTask(
        [thread = network_thread_, worker_thread = worker_thread_.get()] {
          thread->DisallowBlockingCalls();
          thread->DisallowAllInvokes();
          if (worker_thread == thread) {
            // In this case, worker_thread_ == network_thread_
            thread->AllowInvokesToThread(thread);
          }
        });
  }

  rtc::InitRandom(rtc::Time32());

  rtc::SocketFactory* socket_factory = dependencies->socket_factory;
  if (socket_factory == nullptr) {
    if (owned_socket_factory_) {
      socket_factory = owned_socket_factory_.get();
    } else {
      // TODO(bugs.webrtc.org/13145): This case should be deleted. Either
      // require that a PacketSocketFactory and NetworkManager always are
      // injected (with no need to construct these default objects), or require
      // that if a network_thread is injected, an approprite rtc::SocketServer
      // should be injected too.
      socket_factory = network_thread()->socketserver();
    }
  }
  if (!default_network_manager_) {
    // If network_monitor_factory_ is non-null, it will be used to create a
    // network monitor while on the network thread.
    default_network_manager_ = std::make_unique<rtc::BasicNetworkManager>(
        network_monitor_factory_.get(), socket_factory, &field_trials());
  }
  if (!default_socket_factory_) {
    default_socket_factory_ =
        std::make_unique<rtc::BasicPacketSocketFactory>(socket_factory);
  }
  // Set warning levels on the threads, to give warnings when response
  // may be slower than is expected of the thread.
  // Since some of the threads may be the same, start with the least
  // restrictive limits and end with the least permissive ones.
  // This will give warnings for all cases.
  signaling_thread_->SetDispatchWarningMs(100);
  worker_thread_->SetDispatchWarningMs(30);
  network_thread_->SetDispatchWarningMs(10);

  if (media_engine_) {
    // TODO(tommi): Change VoiceEngine to do ctor time initialization so that
    // this isn't necessary.
    worker_thread_->BlockingCall([&] { media_engine_->Init(); });
  }
}

ConnectionContext::~ConnectionContext() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  worker_thread_->BlockingCall([&] {
    RTC_DCHECK_RUN_ON(worker_thread());
    // While `media_engine_` is const throughout the ConnectionContext's
    // lifetime, it requires destruction to happen on the worker thread. Instead
    // of marking the pointer as non-const, we live with this const_cast<> in
    // the destructor.
    const_cast<std::unique_ptr<cricket::MediaEngineInterface>&>(media_engine_)
        .reset();
  });

  // Make sure `worker_thread()` and `signaling_thread()` outlive
  // `default_socket_factory_` and `default_network_manager_`.
  default_socket_factory_ = nullptr;
  default_network_manager_ = nullptr;

  if (wraps_current_thread_)
    rtc::ThreadManager::Instance()->UnwrapCurrentThread();
}

}  // namespace webrtc
