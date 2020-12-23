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

#include <string>
#include <type_traits>
#include <utility>

#include "api/transport/field_trial_based_config.h"
#include "media/base/rtp_data_engine.h"
#include "rtc_base/helpers.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

rtc::Thread* MaybeStartThread(rtc::Thread* old_thread,
                              const std::string& thread_name,
                              bool with_socket_server,
                              std::unique_ptr<rtc::Thread>& thread_holder) {
  if (old_thread) {
    return old_thread;
  }
  if (with_socket_server) {
    thread_holder = rtc::Thread::CreateWithSocketServer();
  } else {
    thread_holder = rtc::Thread::Create();
  }
  thread_holder->SetName(thread_name, nullptr);
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
    rtc::Thread* network_thread) {
  if (factory) {
    return factory;
  }
#ifdef HAVE_SCTP
  return std::make_unique<cricket::SctpTransportFactory>(network_thread);
#else
  return nullptr;
#endif
}

}  // namespace

// Static
rtc::scoped_refptr<ConnectionContext> ConnectionContext::Create(
    PeerConnectionFactoryDependencies* dependencies) {
  auto context = new rtc::RefCountedObject<ConnectionContext>(dependencies);
  if (!context->channel_manager_->Init()) {
    return nullptr;
  }
  return context;
}

ConnectionContext::ConnectionContext(
    PeerConnectionFactoryDependencies* dependencies)
    : network_thread_(MaybeStartThread(dependencies->network_thread,
                                       "pc_network_thread",
                                       true,
                                       owned_network_thread_)),
      worker_thread_(MaybeStartThread(dependencies->worker_thread,
                                      "pc_worker_thread",
                                      false,
                                      owned_worker_thread_)),
      signaling_thread_(MaybeWrapThread(dependencies->signaling_thread,
                                        wraps_current_thread_)),
      network_monitor_factory_(
          std::move(dependencies->network_monitor_factory)),
      call_factory_(std::move(dependencies->call_factory)),
      media_engine_(std::move(dependencies->media_engine)),
      sctp_factory_(
          MaybeCreateSctpFactory(std::move(dependencies->sctp_factory),
                                 network_thread())),
      trials_(dependencies->trials
                  ? std::move(dependencies->trials)
                  : std::make_unique<FieldTrialBasedConfig>()) {
  signaling_thread_->AllowInvokesToThread(worker_thread_);
  signaling_thread_->AllowInvokesToThread(network_thread_);
  worker_thread_->AllowInvokesToThread(network_thread_);
  network_thread_->DisallowAllInvokes();

  RTC_DCHECK_RUN_ON(signaling_thread_);
  rtc::InitRandom(rtc::Time32());

  // If network_monitor_factory_ is non-null, it will be used to create a
  // network monitor while on the network thread.
  default_network_manager_ = std::make_unique<rtc::BasicNetworkManager>(
      network_monitor_factory_.get());

  default_socket_factory_ =
      std::make_unique<rtc::BasicPacketSocketFactory>(network_thread());

  channel_manager_ = std::make_unique<cricket::ChannelManager>(
      std::move(media_engine_), std::make_unique<cricket::RtpDataEngine>(),
      worker_thread(), network_thread());

  channel_manager_->SetVideoRtxEnabled(true);
}

ConnectionContext::~ConnectionContext() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  channel_manager_.reset(nullptr);

  // Make sure |worker_thread()| and |signaling_thread()| outlive
  // |default_socket_factory_| and |default_network_manager_|.
  default_socket_factory_ = nullptr;
  default_network_manager_ = nullptr;

  if (wraps_current_thread_)
    rtc::ThreadManager::Instance()->UnwrapCurrentThread();
}

cricket::ChannelManager* ConnectionContext::channel_manager() const {
  return channel_manager_.get();
}

}  // namespace webrtc
