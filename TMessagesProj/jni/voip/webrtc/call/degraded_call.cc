/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/degraded_call.h"

#include <memory>
#include <utility>

#include "absl/strings/string_view.h"
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "rtc_base/event.h"

namespace webrtc {

DegradedCall::FakeNetworkPipeOnTaskQueue::FakeNetworkPipeOnTaskQueue(
    TaskQueueBase* task_queue,
    rtc::scoped_refptr<PendingTaskSafetyFlag> call_alive,
    Clock* clock,
    std::unique_ptr<NetworkBehaviorInterface> network_behavior)
    : clock_(clock),
      task_queue_(task_queue),
      call_alive_(std::move(call_alive)),
      pipe_(clock, std::move(network_behavior)) {}

void DegradedCall::FakeNetworkPipeOnTaskQueue::SendRtp(
    const uint8_t* packet,
    size_t length,
    const PacketOptions& options,
    Transport* transport) {
  pipe_.SendRtp(packet, length, options, transport);
  Process();
}

void DegradedCall::FakeNetworkPipeOnTaskQueue::SendRtcp(const uint8_t* packet,
                                                        size_t length,
                                                        Transport* transport) {
  pipe_.SendRtcp(packet, length, transport);
  Process();
}

void DegradedCall::FakeNetworkPipeOnTaskQueue::AddActiveTransport(
    Transport* transport) {
  pipe_.AddActiveTransport(transport);
}

void DegradedCall::FakeNetworkPipeOnTaskQueue::RemoveActiveTransport(
    Transport* transport) {
  pipe_.RemoveActiveTransport(transport);
}

bool DegradedCall::FakeNetworkPipeOnTaskQueue::Process() {
  pipe_.Process();
  auto time_to_next = pipe_.TimeUntilNextProcess();
  if (!time_to_next) {
    // Packet was probably sent immediately.
    return false;
  }

  task_queue_->PostTask(SafeTask(call_alive_, [this, time_to_next] {
    RTC_DCHECK_RUN_ON(task_queue_);
    int64_t next_process_time = *time_to_next + clock_->TimeInMilliseconds();
    if (!next_process_ms_ || next_process_time < *next_process_ms_) {
      next_process_ms_ = next_process_time;
      task_queue_->PostDelayedHighPrecisionTask(
          SafeTask(call_alive_,
                   [this] {
                     RTC_DCHECK_RUN_ON(task_queue_);
                     if (!Process()) {
                       next_process_ms_.reset();
                     }
                   }),
          TimeDelta::Millis(*time_to_next));
    }
  }));

  return true;
}

DegradedCall::FakeNetworkPipeTransportAdapter::FakeNetworkPipeTransportAdapter(
    FakeNetworkPipeOnTaskQueue* fake_network,
    Call* call,
    Clock* clock,
    Transport* real_transport)
    : network_pipe_(fake_network),
      call_(call),
      clock_(clock),
      real_transport_(real_transport) {
  network_pipe_->AddActiveTransport(real_transport);
}

DegradedCall::FakeNetworkPipeTransportAdapter::
    ~FakeNetworkPipeTransportAdapter() {
  network_pipe_->RemoveActiveTransport(real_transport_);
}

bool DegradedCall::FakeNetworkPipeTransportAdapter::SendRtp(
    const uint8_t* packet,
    size_t length,
    const PacketOptions& options) {
  // A call here comes from the RTP stack (probably pacer). We intercept it and
  // put it in the fake network pipe instead, but report to Call that is has
  // been sent, so that the bandwidth estimator sees the delay we add.
  network_pipe_->SendRtp(packet, length, options, real_transport_);
  if (options.packet_id != -1) {
    rtc::SentPacket sent_packet;
    sent_packet.packet_id = options.packet_id;
    sent_packet.send_time_ms = clock_->TimeInMilliseconds();
    sent_packet.info.included_in_feedback = options.included_in_feedback;
    sent_packet.info.included_in_allocation = options.included_in_allocation;
    sent_packet.info.packet_size_bytes = length;
    sent_packet.info.packet_type = rtc::PacketType::kData;
    call_->OnSentPacket(sent_packet);
  }
  return true;
}

bool DegradedCall::FakeNetworkPipeTransportAdapter::SendRtcp(
    const uint8_t* packet,
    size_t length) {
  network_pipe_->SendRtcp(packet, length, real_transport_);
  return true;
}

DegradedCall::ThreadedPacketReceiver::ThreadedPacketReceiver(
    webrtc::TaskQueueBase* worker_thread,
    webrtc::TaskQueueBase* network_thread,
    rtc::scoped_refptr<PendingTaskSafetyFlag> call_alive,
    webrtc::PacketReceiver* receiver)
    : worker_thread_(worker_thread),
      network_thread_(network_thread),
      call_alive_(std::move(call_alive)),
      receiver_(receiver) {}

DegradedCall::ThreadedPacketReceiver::~ThreadedPacketReceiver() = default;

PacketReceiver::DeliveryStatus
DegradedCall::ThreadedPacketReceiver::DeliverPacket(
    MediaType media_type,
    rtc::CopyOnWriteBuffer packet,
    int64_t packet_time_us) {
  // `Call::DeliverPacket` expects RTCP packets to be delivered from the
  // network thread and RTP packets to be delivered from the worker thread.
  // Because `FakeNetworkPipe` queues packets, the thread used when this packet
  // is delivered to `DegradedCall::DeliverPacket` may differ from the thread
  // used when this packet is delivered to
  // `ThreadedPacketReceiver::DeliverPacket`. To solve this problem, always
  // make sure that packets are sent in the correct thread.
  if (IsRtcpPacket(packet)) {
    if (!network_thread_->IsCurrent()) {
      network_thread_->PostTask(
          SafeTask(call_alive_, [receiver = receiver_, media_type,
                                 packet = std::move(packet), packet_time_us]() {
            receiver->DeliverPacket(media_type, std::move(packet),
                                    packet_time_us);
          }));
      return DELIVERY_OK;
    }
  } else {
    if (!worker_thread_->IsCurrent()) {
      worker_thread_->PostTask([receiver = receiver_, media_type,
                                packet = std::move(packet), packet_time_us]() {
        receiver->DeliverPacket(media_type, std::move(packet), packet_time_us);
      });
      return DELIVERY_OK;
    }
  }

  return receiver_->DeliverPacket(media_type, std::move(packet),
                                  packet_time_us);
}

DegradedCall::DegradedCall(
    std::unique_ptr<Call> call,
    const std::vector<TimeScopedNetworkConfig>& send_configs,
    const std::vector<TimeScopedNetworkConfig>& receive_configs)
    : clock_(Clock::GetRealTimeClock()),
      call_(std::move(call)),
      call_alive_(PendingTaskSafetyFlag::CreateDetached()),
      send_config_index_(0),
      send_configs_(send_configs),
      send_simulated_network_(nullptr),
      receive_config_index_(0),
      receive_configs_(receive_configs) {
  if (!receive_configs_.empty()) {
    auto network = std::make_unique<SimulatedNetwork>(receive_configs_[0]);
    receive_simulated_network_ = network.get();
    receive_pipe_ =
        std::make_unique<webrtc::FakeNetworkPipe>(clock_, std::move(network));
    packet_receiver_ = std::make_unique<ThreadedPacketReceiver>(
        call_->worker_thread(), call_->network_thread(), call_alive_,
        call_->Receiver());
    receive_pipe_->SetReceiver(packet_receiver_.get());
    if (receive_configs_.size() > 1) {
      call_->network_thread()->PostDelayedTask(
          SafeTask(call_alive_, [this] { UpdateReceiveNetworkConfig(); }),
          receive_configs_[0].duration);
    }
  }
  if (!send_configs_.empty()) {
    auto network = std::make_unique<SimulatedNetwork>(send_configs_[0]);
    send_simulated_network_ = network.get();
    send_pipe_ = std::make_unique<FakeNetworkPipeOnTaskQueue>(
        call_->network_thread(), call_alive_, clock_, std::move(network));
    if (send_configs_.size() > 1) {
      call_->network_thread()->PostDelayedTask(
          SafeTask(call_alive_, [this] { UpdateSendNetworkConfig(); }),
          send_configs_[0].duration);
    }
  }
}

DegradedCall::~DegradedCall() {
  RTC_DCHECK_RUN_ON(call_->worker_thread());
  // Thread synchronization is required to call `SetNotAlive`.
  // Otherwise, when the `DegradedCall` object is destroyed but
  // `SetNotAlive` has not yet been called,
  // another Closure guarded by `call_alive_` may be called.
  rtc::Event event;
  call_->network_thread()->PostTask(
      [flag = std::move(call_alive_), &event]() mutable {
        flag->SetNotAlive();
        event.Set();
      });
  event.Wait(rtc::Event::kForever);
}

AudioSendStream* DegradedCall::CreateAudioSendStream(
    const AudioSendStream::Config& config) {
  if (!send_configs_.empty()) {
    auto transport_adapter = std::make_unique<FakeNetworkPipeTransportAdapter>(
        send_pipe_.get(), call_.get(), clock_, config.send_transport);
    AudioSendStream::Config degrade_config = config;
    degrade_config.send_transport = transport_adapter.get();
    AudioSendStream* send_stream = call_->CreateAudioSendStream(degrade_config);
    if (send_stream) {
      audio_send_transport_adapters_[send_stream] =
          std::move(transport_adapter);
    }
    return send_stream;
  }
  return call_->CreateAudioSendStream(config);
}

void DegradedCall::DestroyAudioSendStream(AudioSendStream* send_stream) {
  call_->DestroyAudioSendStream(send_stream);
  audio_send_transport_adapters_.erase(send_stream);
}

AudioReceiveStreamInterface* DegradedCall::CreateAudioReceiveStream(
    const AudioReceiveStreamInterface::Config& config) {
  return call_->CreateAudioReceiveStream(config);
}

void DegradedCall::DestroyAudioReceiveStream(
    AudioReceiveStreamInterface* receive_stream) {
  call_->DestroyAudioReceiveStream(receive_stream);
}

VideoSendStream* DegradedCall::CreateVideoSendStream(
    VideoSendStream::Config config,
    VideoEncoderConfig encoder_config) {
  std::unique_ptr<FakeNetworkPipeTransportAdapter> transport_adapter;
  if (!send_configs_.empty()) {
    transport_adapter = std::make_unique<FakeNetworkPipeTransportAdapter>(
        send_pipe_.get(), call_.get(), clock_, config.send_transport);
    config.send_transport = transport_adapter.get();
  }
  VideoSendStream* send_stream = call_->CreateVideoSendStream(
      std::move(config), std::move(encoder_config));
  if (send_stream && transport_adapter) {
    video_send_transport_adapters_[send_stream] = std::move(transport_adapter);
  }
  return send_stream;
}

VideoSendStream* DegradedCall::CreateVideoSendStream(
    VideoSendStream::Config config,
    VideoEncoderConfig encoder_config,
    std::unique_ptr<FecController> fec_controller) {
  std::unique_ptr<FakeNetworkPipeTransportAdapter> transport_adapter;
  if (!send_configs_.empty()) {
    transport_adapter = std::make_unique<FakeNetworkPipeTransportAdapter>(
        send_pipe_.get(), call_.get(), clock_, config.send_transport);
    config.send_transport = transport_adapter.get();
  }
  VideoSendStream* send_stream = call_->CreateVideoSendStream(
      std::move(config), std::move(encoder_config), std::move(fec_controller));
  if (send_stream && transport_adapter) {
    video_send_transport_adapters_[send_stream] = std::move(transport_adapter);
  }
  return send_stream;
}

void DegradedCall::DestroyVideoSendStream(VideoSendStream* send_stream) {
  call_->DestroyVideoSendStream(send_stream);
  video_send_transport_adapters_.erase(send_stream);
}

VideoReceiveStreamInterface* DegradedCall::CreateVideoReceiveStream(
    VideoReceiveStreamInterface::Config configuration) {
  return call_->CreateVideoReceiveStream(std::move(configuration));
}

void DegradedCall::DestroyVideoReceiveStream(
    VideoReceiveStreamInterface* receive_stream) {
  call_->DestroyVideoReceiveStream(receive_stream);
}

FlexfecReceiveStream* DegradedCall::CreateFlexfecReceiveStream(
    const FlexfecReceiveStream::Config config) {
  return call_->CreateFlexfecReceiveStream(std::move(config));
}

void DegradedCall::DestroyFlexfecReceiveStream(
    FlexfecReceiveStream* receive_stream) {
  call_->DestroyFlexfecReceiveStream(receive_stream);
}

void DegradedCall::AddAdaptationResource(
    rtc::scoped_refptr<Resource> resource) {
  call_->AddAdaptationResource(std::move(resource));
}

PacketReceiver* DegradedCall::Receiver() {
  if (!receive_configs_.empty()) {
    return this;
  }
  return call_->Receiver();
}

RtpTransportControllerSendInterface*
DegradedCall::GetTransportControllerSend() {
  return call_->GetTransportControllerSend();
}

Call::Stats DegradedCall::GetStats() const {
  return call_->GetStats();
}

const FieldTrialsView& DegradedCall::trials() const {
  return call_->trials();
}

TaskQueueBase* DegradedCall::network_thread() const {
  return call_->network_thread();
}

TaskQueueBase* DegradedCall::worker_thread() const {
  return call_->worker_thread();
}

void DegradedCall::SignalChannelNetworkState(MediaType media,
                                             NetworkState state) {
  call_->SignalChannelNetworkState(media, state);
}

void DegradedCall::OnAudioTransportOverheadChanged(
    int transport_overhead_per_packet) {
  call_->OnAudioTransportOverheadChanged(transport_overhead_per_packet);
}

void DegradedCall::OnLocalSsrcUpdated(AudioReceiveStreamInterface& stream,
                                      uint32_t local_ssrc) {
  call_->OnLocalSsrcUpdated(stream, local_ssrc);
}

void DegradedCall::OnLocalSsrcUpdated(VideoReceiveStreamInterface& stream,
                                      uint32_t local_ssrc) {
  call_->OnLocalSsrcUpdated(stream, local_ssrc);
}

void DegradedCall::OnLocalSsrcUpdated(FlexfecReceiveStream& stream,
                                      uint32_t local_ssrc) {
  call_->OnLocalSsrcUpdated(stream, local_ssrc);
}

void DegradedCall::OnUpdateSyncGroup(AudioReceiveStreamInterface& stream,
                                     absl::string_view sync_group) {
  call_->OnUpdateSyncGroup(stream, sync_group);
}

void DegradedCall::OnSentPacket(const rtc::SentPacket& sent_packet) {
  if (!send_configs_.empty()) {
    // If we have a degraded send-transport, we have already notified call
    // about the supposed network send time. Discard the actual network send
    // time in order to properly fool the BWE.
    return;
  }
  call_->OnSentPacket(sent_packet);
}

PacketReceiver::DeliveryStatus DegradedCall::DeliverPacket(
    MediaType media_type,
    rtc::CopyOnWriteBuffer packet,
    int64_t packet_time_us) {
  PacketReceiver::DeliveryStatus status = receive_pipe_->DeliverPacket(
      media_type, std::move(packet), packet_time_us);
  // This is not optimal, but there are many places where there are thread
  // checks that fail if we're not using the worker thread call into this
  // method. If we want to fix this we probably need a task queue to do handover
  // of all overriden methods, which feels like overkill for the current use
  // case.
  // By just having this thread call out via the Process() method we work around
  // that, with the tradeoff that a non-zero delay may become a little larger
  // than anticipated at very low packet rates.
  receive_pipe_->Process();
  return status;
}

void DegradedCall::SetClientBitratePreferences(
    const webrtc::BitrateSettings& preferences) {
  call_->SetClientBitratePreferences(preferences);
}

void DegradedCall::UpdateSendNetworkConfig() {
  send_config_index_ = (send_config_index_ + 1) % send_configs_.size();
  send_simulated_network_->SetConfig(send_configs_[send_config_index_]);
  call_->network_thread()->PostDelayedTask(
      SafeTask(call_alive_, [this] { UpdateSendNetworkConfig(); }),
      send_configs_[send_config_index_].duration);
}

void DegradedCall::UpdateReceiveNetworkConfig() {
  receive_config_index_ = (receive_config_index_ + 1) % receive_configs_.size();
  receive_simulated_network_->SetConfig(
      receive_configs_[receive_config_index_]);
  call_->network_thread()->PostDelayedTask(
      SafeTask(call_alive_, [this] { UpdateReceiveNetworkConfig(); }),
      receive_configs_[receive_config_index_].duration);
}
}  // namespace webrtc
