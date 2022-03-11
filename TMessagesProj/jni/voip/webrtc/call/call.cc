/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "call/call.h"

#include <string.h>

#include <algorithm>
#include <atomic>
#include <map>
#include <memory>
#include <set>
#include <utility>
#include <vector>

#include "absl/functional/bind_front.h"
#include "absl/types/optional.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/sequence_checker.h"
#include "api/transport/network_control.h"
#include "audio/audio_receive_stream.h"
#include "audio/audio_send_stream.h"
#include "audio/audio_state.h"
#include "call/adaptation/broadcast_resource_listener.h"
#include "call/bitrate_allocator.h"
#include "call/flexfec_receive_stream_impl.h"
#include "call/receive_time_calculator.h"
#include "call/rtp_stream_receiver_controller.h"
#include "call/rtp_transport_controller_send.h"
#include "call/rtp_transport_controller_send_factory.h"
#include "call/version.h"
#include "logging/rtc_event_log/events/rtc_event_audio_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_rtcp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_rtp_packet_incoming.h"
#include "logging/rtc_event_log/events/rtc_event_video_receive_stream_config.h"
#include "logging/rtc_event_log/events/rtc_event_video_send_stream_config.h"
#include "logging/rtc_event_log/rtc_stream_config.h"
#include "modules/congestion_controller/include/receive_side_congestion_controller.h"
#include "modules/rtp_rtcp/include/flexfec_receiver.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/byte_io.h"
#include "modules/rtp_rtcp/source/rtp_packet_received.h"
#include "modules/rtp_rtcp/source/rtp_util.h"
#include "modules/utility/include/process_thread.h"
#include "modules/video_coding/fec_controller_default.h"
#include "rtc_base/checks.h"
#include "rtc_base/location.h"
#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/time_utils.h"
#include "rtc_base/trace_event.h"
#include "system_wrappers/include/clock.h"
#include "system_wrappers/include/cpu_info.h"
#include "system_wrappers/include/field_trial.h"
#include "system_wrappers/include/metrics.h"
#include "video/call_stats2.h"
#include "video/send_delay_stats.h"
#include "video/stats_counter.h"
#include "video/video_receive_stream2.h"
#include "video/video_send_stream.h"

namespace webrtc {

namespace {
bool SendPeriodicFeedback(const std::vector<RtpExtension>& extensions) {
  for (const auto& extension : extensions) {
    if (extension.uri == RtpExtension::kTransportSequenceNumberV2Uri)
      return false;
  }
  return true;
}

bool UseSendSideBwe(const ReceiveStream::RtpConfig& rtp) {
  if (!rtp.transport_cc)
    return false;
  for (const auto& extension : rtp.extensions) {
    if (extension.uri == RtpExtension::kTransportSequenceNumberUri ||
        extension.uri == RtpExtension::kTransportSequenceNumberV2Uri)
      return true;
  }
  return false;
}

const int* FindKeyByValue(const std::map<int, int>& m, int v) {
  for (const auto& kv : m) {
    if (kv.second == v)
      return &kv.first;
  }
  return nullptr;
}

std::unique_ptr<rtclog::StreamConfig> CreateRtcLogStreamConfig(
    const VideoReceiveStream::Config& config) {
  auto rtclog_config = std::make_unique<rtclog::StreamConfig>();
  rtclog_config->remote_ssrc = config.rtp.remote_ssrc;
  rtclog_config->local_ssrc = config.rtp.local_ssrc;
  rtclog_config->rtx_ssrc = config.rtp.rtx_ssrc;
  rtclog_config->rtcp_mode = config.rtp.rtcp_mode;
  rtclog_config->rtp_extensions = config.rtp.extensions;

  for (const auto& d : config.decoders) {
    const int* search =
        FindKeyByValue(config.rtp.rtx_associated_payload_types, d.payload_type);
    rtclog_config->codecs.emplace_back(d.video_format.name, d.payload_type,
                                       search ? *search : 0);
  }
  return rtclog_config;
}

std::unique_ptr<rtclog::StreamConfig> CreateRtcLogStreamConfig(
    const VideoSendStream::Config& config,
    size_t ssrc_index) {
  auto rtclog_config = std::make_unique<rtclog::StreamConfig>();
  rtclog_config->local_ssrc = config.rtp.ssrcs[ssrc_index];
  if (ssrc_index < config.rtp.rtx.ssrcs.size()) {
    rtclog_config->rtx_ssrc = config.rtp.rtx.ssrcs[ssrc_index];
  }
  rtclog_config->rtcp_mode = config.rtp.rtcp_mode;
  rtclog_config->rtp_extensions = config.rtp.extensions;

  rtclog_config->codecs.emplace_back(config.rtp.payload_name,
                                     config.rtp.payload_type,
                                     config.rtp.rtx.payload_type);
  return rtclog_config;
}

std::unique_ptr<rtclog::StreamConfig> CreateRtcLogStreamConfig(
    const AudioReceiveStream::Config& config) {
  auto rtclog_config = std::make_unique<rtclog::StreamConfig>();
  rtclog_config->remote_ssrc = config.rtp.remote_ssrc;
  rtclog_config->local_ssrc = config.rtp.local_ssrc;
  rtclog_config->rtp_extensions = config.rtp.extensions;
  return rtclog_config;
}

TaskQueueBase* GetCurrentTaskQueueOrThread() {
  TaskQueueBase* current = TaskQueueBase::Current();
  if (!current)
    current = rtc::ThreadManager::Instance()->CurrentThread();
  return current;
}

}  // namespace

namespace internal {

// Wraps an injected resource in a BroadcastResourceListener and handles adding
// and removing adapter resources to individual VideoSendStreams.
class ResourceVideoSendStreamForwarder {
 public:
  ResourceVideoSendStreamForwarder(
      rtc::scoped_refptr<webrtc::Resource> resource)
      : broadcast_resource_listener_(resource) {
    broadcast_resource_listener_.StartListening();
  }
  ~ResourceVideoSendStreamForwarder() {
    RTC_DCHECK(adapter_resources_.empty());
    broadcast_resource_listener_.StopListening();
  }

  rtc::scoped_refptr<webrtc::Resource> Resource() const {
    return broadcast_resource_listener_.SourceResource();
  }

  void OnCreateVideoSendStream(VideoSendStream* video_send_stream) {
    RTC_DCHECK(adapter_resources_.find(video_send_stream) ==
               adapter_resources_.end());
    auto adapter_resource =
        broadcast_resource_listener_.CreateAdapterResource();
    video_send_stream->AddAdaptationResource(adapter_resource);
    adapter_resources_.insert(
        std::make_pair(video_send_stream, adapter_resource));
  }

  void OnDestroyVideoSendStream(VideoSendStream* video_send_stream) {
    auto it = adapter_resources_.find(video_send_stream);
    RTC_DCHECK(it != adapter_resources_.end());
    broadcast_resource_listener_.RemoveAdapterResource(it->second);
    adapter_resources_.erase(it);
  }

 private:
  BroadcastResourceListener broadcast_resource_listener_;
  std::map<VideoSendStream*, rtc::scoped_refptr<webrtc::Resource>>
      adapter_resources_;
};

class Call final : public webrtc::Call,
                   public PacketReceiver,
                   public RecoveredPacketReceiver,
                   public TargetTransferRateObserver,
                   public BitrateAllocator::LimitObserver {
 public:
  Call(Clock* clock,
       const Call::Config& config,
       std::unique_ptr<RtpTransportControllerSendInterface> transport_send,
       rtc::scoped_refptr<SharedModuleThread> module_process_thread,
       TaskQueueFactory* task_queue_factory);
  ~Call() override;

  Call(const Call&) = delete;
  Call& operator=(const Call&) = delete;

  // Implements webrtc::Call.
  PacketReceiver* Receiver() override;

  webrtc::AudioSendStream* CreateAudioSendStream(
      const webrtc::AudioSendStream::Config& config) override;
  void DestroyAudioSendStream(webrtc::AudioSendStream* send_stream) override;

  webrtc::AudioReceiveStream* CreateAudioReceiveStream(
      const webrtc::AudioReceiveStream::Config& config) override;
  void DestroyAudioReceiveStream(
      webrtc::AudioReceiveStream* receive_stream) override;

  webrtc::VideoSendStream* CreateVideoSendStream(
      webrtc::VideoSendStream::Config config,
      VideoEncoderConfig encoder_config) override;
  webrtc::VideoSendStream* CreateVideoSendStream(
      webrtc::VideoSendStream::Config config,
      VideoEncoderConfig encoder_config,
      std::unique_ptr<FecController> fec_controller) override;
  void DestroyVideoSendStream(webrtc::VideoSendStream* send_stream) override;

  webrtc::VideoReceiveStream* CreateVideoReceiveStream(
      webrtc::VideoReceiveStream::Config configuration) override;
  void DestroyVideoReceiveStream(
      webrtc::VideoReceiveStream* receive_stream) override;

  FlexfecReceiveStream* CreateFlexfecReceiveStream(
      const FlexfecReceiveStream::Config& config) override;
  void DestroyFlexfecReceiveStream(
      FlexfecReceiveStream* receive_stream) override;

  void AddAdaptationResource(rtc::scoped_refptr<Resource> resource) override;

  RtpTransportControllerSendInterface* GetTransportControllerSend() override;

  Stats GetStats() const override;

  const WebRtcKeyValueConfig& trials() const override;

  TaskQueueBase* network_thread() const override;
  TaskQueueBase* worker_thread() const override;

  // Implements PacketReceiver.
  DeliveryStatus DeliverPacket(MediaType media_type,
                               rtc::CopyOnWriteBuffer packet,
                               int64_t packet_time_us) override;

  // Implements RecoveredPacketReceiver.
  void OnRecoveredPacket(const uint8_t* packet, size_t length) override;

  void SignalChannelNetworkState(MediaType media, NetworkState state) override;

  void OnAudioTransportOverheadChanged(
      int transport_overhead_per_packet) override;

  void OnLocalSsrcUpdated(webrtc::AudioReceiveStream& stream,
                          uint32_t local_ssrc) override;

  void OnUpdateSyncGroup(webrtc::AudioReceiveStream& stream,
                         const std::string& sync_group) override;

  void OnSentPacket(const rtc::SentPacket& sent_packet) override;

  // Implements TargetTransferRateObserver,
  void OnTargetTransferRate(TargetTransferRate msg) override;
  void OnStartRateUpdate(DataRate start_rate) override;

  // Implements BitrateAllocator::LimitObserver.
  void OnAllocationLimitsChanged(BitrateAllocationLimits limits) override;

  void SetClientBitratePreferences(const BitrateSettings& preferences) override;

 private:
  // Thread-compatible class that collects received packet stats and exposes
  // them as UMA histograms on destruction.
  class ReceiveStats {
   public:
    explicit ReceiveStats(Clock* clock);
    ~ReceiveStats();

    void AddReceivedRtcpBytes(int bytes);
    void AddReceivedAudioBytes(int bytes, webrtc::Timestamp arrival_time);
    void AddReceivedVideoBytes(int bytes, webrtc::Timestamp arrival_time);

   private:
    RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
    RateCounter received_bytes_per_second_counter_
        RTC_GUARDED_BY(sequence_checker_);
    RateCounter received_audio_bytes_per_second_counter_
        RTC_GUARDED_BY(sequence_checker_);
    RateCounter received_video_bytes_per_second_counter_
        RTC_GUARDED_BY(sequence_checker_);
    RateCounter received_rtcp_bytes_per_second_counter_
        RTC_GUARDED_BY(sequence_checker_);
    absl::optional<Timestamp> first_received_rtp_audio_timestamp_
        RTC_GUARDED_BY(sequence_checker_);
    absl::optional<Timestamp> last_received_rtp_audio_timestamp_
        RTC_GUARDED_BY(sequence_checker_);
    absl::optional<Timestamp> first_received_rtp_video_timestamp_
        RTC_GUARDED_BY(sequence_checker_);
    absl::optional<Timestamp> last_received_rtp_video_timestamp_
        RTC_GUARDED_BY(sequence_checker_);
  };

  // Thread-compatible class that collects sent packet stats and exposes
  // them as UMA histograms on destruction, provided SetFirstPacketTime was
  // called with a non-empty packet timestamp before the destructor.
  class SendStats {
   public:
    explicit SendStats(Clock* clock);
    ~SendStats();

    void SetFirstPacketTime(absl::optional<Timestamp> first_sent_packet_time);
    void PauseSendAndPacerBitrateCounters();
    void AddTargetBitrateSample(uint32_t target_bitrate_bps);
    void SetMinAllocatableRate(BitrateAllocationLimits limits);

   private:
    RTC_NO_UNIQUE_ADDRESS SequenceChecker destructor_sequence_checker_;
    RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
    Clock* const clock_ RTC_GUARDED_BY(destructor_sequence_checker_);
    AvgCounter estimated_send_bitrate_kbps_counter_
        RTC_GUARDED_BY(sequence_checker_);
    AvgCounter pacer_bitrate_kbps_counter_ RTC_GUARDED_BY(sequence_checker_);
    uint32_t min_allocated_send_bitrate_bps_ RTC_GUARDED_BY(sequence_checker_){
        0};
    absl::optional<Timestamp> first_sent_packet_time_
        RTC_GUARDED_BY(destructor_sequence_checker_);
  };

  void DeliverRtcp(MediaType media_type, rtc::CopyOnWriteBuffer packet)
      RTC_RUN_ON(network_thread_);
  DeliveryStatus DeliverRtp(MediaType media_type,
                            rtc::CopyOnWriteBuffer packet,
                            int64_t packet_time_us) RTC_RUN_ON(worker_thread_);

  AudioReceiveStream* FindAudioStreamForSyncGroup(const std::string& sync_group)
      RTC_RUN_ON(worker_thread_);
  void ConfigureSync(const std::string& sync_group) RTC_RUN_ON(worker_thread_);

  void NotifyBweOfReceivedPacket(const RtpPacketReceived& packet,
                                 MediaType media_type,
                                 bool use_send_side_bwe)
      RTC_RUN_ON(worker_thread_);

  bool IdentifyReceivedPacket(RtpPacketReceived& packet,
                              bool* use_send_side_bwe = nullptr);
  bool RegisterReceiveStream(uint32_t ssrc, ReceiveStream* stream);
  bool UnregisterReceiveStream(uint32_t ssrc);

  void UpdateAggregateNetworkState();

  // Ensure that necessary process threads are started, and any required
  // callbacks have been registered.
  void EnsureStarted() RTC_RUN_ON(worker_thread_);

  Clock* const clock_;
  TaskQueueFactory* const task_queue_factory_;
  TaskQueueBase* const worker_thread_;
  TaskQueueBase* const network_thread_;
  const std::unique_ptr<DecodeSynchronizer> decode_sync_;
  RTC_NO_UNIQUE_ADDRESS SequenceChecker send_transport_sequence_checker_;

  const int num_cpu_cores_;
  const rtc::scoped_refptr<SharedModuleThread> module_process_thread_;
  const std::unique_ptr<CallStats> call_stats_;
  const std::unique_ptr<BitrateAllocator> bitrate_allocator_;
  const Call::Config config_ RTC_GUARDED_BY(worker_thread_);
  // Maps to config_.trials, can be used from any thread via `trials()`.
  const WebRtcKeyValueConfig& trials_;

  NetworkState audio_network_state_ RTC_GUARDED_BY(worker_thread_);
  NetworkState video_network_state_ RTC_GUARDED_BY(worker_thread_);
  // TODO(bugs.webrtc.org/11993): Move aggregate_network_up_ over to the
  // network thread.
  bool aggregate_network_up_ RTC_GUARDED_BY(worker_thread_);

  // Schedules nack periodic processing on behalf of all streams.
  NackPeriodicProcessor nack_periodic_processor_;

  // Audio, Video, and FlexFEC receive streams are owned by the client that
  // creates them.
  // TODO(bugs.webrtc.org/11993): Move audio_receive_streams_,
  // video_receive_streams_ over to the network thread.
  std::set<AudioReceiveStream*> audio_receive_streams_
      RTC_GUARDED_BY(worker_thread_);
  std::set<VideoReceiveStream2*> video_receive_streams_
      RTC_GUARDED_BY(worker_thread_);
  // TODO(nisse): Should eventually be injected at creation,
  // with a single object in the bundled case.
  RtpStreamReceiverController audio_receiver_controller_
      RTC_GUARDED_BY(worker_thread_);
  RtpStreamReceiverController video_receiver_controller_
      RTC_GUARDED_BY(worker_thread_);

  // This extra map is used for receive processing which is
  // independent of media type.

  RTC_NO_UNIQUE_ADDRESS SequenceChecker receive_11993_checker_;

  // TODO(bugs.webrtc.org/11993): Move receive_rtp_config_ over to the
  // network thread.
  std::map<uint32_t, ReceiveStream*> receive_rtp_config_
      RTC_GUARDED_BY(&receive_11993_checker_);

  // Audio and Video send streams are owned by the client that creates them.
  std::map<uint32_t, AudioSendStream*> audio_send_ssrcs_
      RTC_GUARDED_BY(worker_thread_);
  std::map<uint32_t, VideoSendStream*> video_send_ssrcs_
      RTC_GUARDED_BY(worker_thread_);
  std::set<VideoSendStream*> video_send_streams_ RTC_GUARDED_BY(worker_thread_);
  // True if `video_send_streams_` is empty, false if not. The atomic variable
  // is used to decide UMA send statistics behavior and enables avoiding a
  // PostTask().
  std::atomic<bool> video_send_streams_empty_{true};

  // Each forwarder wraps an adaptation resource that was added to the call.
  std::vector<std::unique_ptr<ResourceVideoSendStreamForwarder>>
      adaptation_resource_forwarders_ RTC_GUARDED_BY(worker_thread_);

  using RtpStateMap = std::map<uint32_t, RtpState>;
  RtpStateMap suspended_audio_send_ssrcs_ RTC_GUARDED_BY(worker_thread_);
  RtpStateMap suspended_video_send_ssrcs_ RTC_GUARDED_BY(worker_thread_);

  using RtpPayloadStateMap = std::map<uint32_t, RtpPayloadState>;
  RtpPayloadStateMap suspended_video_payload_states_
      RTC_GUARDED_BY(worker_thread_);

  webrtc::RtcEventLog* const event_log_;

  // TODO(bugs.webrtc.org/11993) ready to move stats access to the network
  // thread.
  ReceiveStats receive_stats_ RTC_GUARDED_BY(worker_thread_);
  SendStats send_stats_ RTC_GUARDED_BY(send_transport_sequence_checker_);
  // `last_bandwidth_bps_` and `configured_max_padding_bitrate_bps_` being
  // atomic avoids a PostTask. The variables are used for stats gathering.
  std::atomic<uint32_t> last_bandwidth_bps_{0};
  std::atomic<uint32_t> configured_max_padding_bitrate_bps_{0};

  ReceiveSideCongestionController receive_side_cc_;

  const std::unique_ptr<ReceiveTimeCalculator> receive_time_calculator_;

  const std::unique_ptr<SendDelayStats> video_send_delay_stats_;
  const Timestamp start_of_call_;

  // Note that `task_safety_` needs to be at a greater scope than the task queue
  // owned by `transport_send_` since calls might arrive on the network thread
  // while Call is being deleted and the task queue is being torn down.
  const ScopedTaskSafety task_safety_;

  // Caches transport_send_.get(), to avoid racing with destructor.
  // Note that this is declared before transport_send_ to ensure that it is not
  // invalidated until no more tasks can be running on the transport_send_ task
  // queue.
  // For more details on the background of this member variable, see:
  // https://webrtc-review.googlesource.com/c/src/+/63023/9/call/call.cc
  // https://bugs.chromium.org/p/chromium/issues/detail?id=992640
  RtpTransportControllerSendInterface* const transport_send_ptr_
      RTC_GUARDED_BY(send_transport_sequence_checker_);
  // Declared last since it will issue callbacks from a task queue. Declaring it
  // last ensures that it is destroyed first and any running tasks are finished.
  const std::unique_ptr<RtpTransportControllerSendInterface> transport_send_;

  bool is_started_ RTC_GUARDED_BY(worker_thread_) = false;

  // Sequence checker for outgoing network traffic. Could be the network thread.
  // Could also be a pacer owned thread or TQ such as the TaskQueuePacedSender.
  RTC_NO_UNIQUE_ADDRESS SequenceChecker sent_packet_sequence_checker_;
  absl::optional<rtc::SentPacket> last_sent_packet_
      RTC_GUARDED_BY(sent_packet_sequence_checker_);
};
}  // namespace internal

std::string Call::Stats::ToString(int64_t time_ms) const {
  char buf[1024];
  rtc::SimpleStringBuilder ss(buf);
  ss << "Call stats: " << time_ms << ", {";
  ss << "send_bw_bps: " << send_bandwidth_bps << ", ";
  ss << "recv_bw_bps: " << recv_bandwidth_bps << ", ";
  ss << "max_pad_bps: " << max_padding_bitrate_bps << ", ";
  ss << "pacer_delay_ms: " << pacer_delay_ms << ", ";
  ss << "rtt_ms: " << rtt_ms;
  ss << '}';
  return ss.str();
}

Call* Call::Create(const Call::Config& config) {
  rtc::scoped_refptr<SharedModuleThread> call_thread =
      SharedModuleThread::Create(ProcessThread::Create("ModuleProcessThread"),
                                 nullptr);
  return Create(config, Clock::GetRealTimeClock(), std::move(call_thread),
                ProcessThread::Create("PacerThread"));
}

Call* Call::Create(const Call::Config& config,
                   Clock* clock,
                   rtc::scoped_refptr<SharedModuleThread> call_thread,
                   std::unique_ptr<ProcessThread> pacer_thread) {
  RTC_DCHECK(config.task_queue_factory);

  RtpTransportControllerSendFactory transport_controller_factory_;

  RtpTransportConfig transportConfig = config.ExtractTransportConfig();

  return new internal::Call(
      clock, config,
      transport_controller_factory_.Create(transportConfig, clock,
                                           std::move(pacer_thread)),
      std::move(call_thread), config.task_queue_factory);
}

Call* Call::Create(const Call::Config& config,
                   Clock* clock,
                   rtc::scoped_refptr<SharedModuleThread> call_thread,
                   std::unique_ptr<RtpTransportControllerSendInterface>
                       transportControllerSend) {
  RTC_DCHECK(config.task_queue_factory);
  return new internal::Call(clock, config, std::move(transportControllerSend),
                            std::move(call_thread), config.task_queue_factory);
}

class SharedModuleThread::Impl {
 public:
  Impl(std::unique_ptr<ProcessThread> process_thread,
       std::function<void()> on_one_ref_remaining)
      : module_thread_(std::move(process_thread)),
        on_one_ref_remaining_(std::move(on_one_ref_remaining)) {}

  void EnsureStarted() {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    if (started_)
      return;
    started_ = true;
    module_thread_->Start();
  }

  ProcessThread* process_thread() {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    return module_thread_.get();
  }

  void AddRef() const {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    ++ref_count_;
  }

  rtc::RefCountReleaseStatus Release() const {
    RTC_DCHECK_RUN_ON(&sequence_checker_);
    --ref_count_;

    if (ref_count_ == 0) {
      module_thread_->Stop();
      return rtc::RefCountReleaseStatus::kDroppedLastRef;
    }

    if (ref_count_ == 1 && on_one_ref_remaining_) {
      auto moved_fn = std::move(on_one_ref_remaining_);
      // NOTE: after this function returns, chances are that `this` has been
      // deleted - do not touch any member variables.
      // If the owner of the last reference implements a lambda that releases
      // that last reference inside of the callback (which is legal according
      // to this implementation), we will recursively enter Release() above,
      // call Stop() and release the last reference.
      moved_fn();
    }

    return rtc::RefCountReleaseStatus::kOtherRefsRemained;
  }

 private:
  RTC_NO_UNIQUE_ADDRESS SequenceChecker sequence_checker_;
  mutable int ref_count_ RTC_GUARDED_BY(sequence_checker_) = 0;
  std::unique_ptr<ProcessThread> const module_thread_;
  std::function<void()> const on_one_ref_remaining_;
  bool started_ = false;
};

SharedModuleThread::SharedModuleThread(
    std::unique_ptr<ProcessThread> process_thread,
    std::function<void()> on_one_ref_remaining)
    : impl_(std::make_unique<Impl>(std::move(process_thread),
                                   std::move(on_one_ref_remaining))) {}

SharedModuleThread::~SharedModuleThread() = default;

// static

rtc::scoped_refptr<SharedModuleThread> SharedModuleThread::Create(
    std::unique_ptr<ProcessThread> process_thread,
    std::function<void()> on_one_ref_remaining) {
  // Using `new` to access a non-public constructor.
  return rtc::scoped_refptr<SharedModuleThread>(new SharedModuleThread(
      std::move(process_thread), std::move(on_one_ref_remaining)));
}

void SharedModuleThread::EnsureStarted() {
  impl_->EnsureStarted();
}

ProcessThread* SharedModuleThread::process_thread() {
  return impl_->process_thread();
}

void SharedModuleThread::AddRef() const {
  impl_->AddRef();
}

rtc::RefCountReleaseStatus SharedModuleThread::Release() const {
  auto ret = impl_->Release();
  if (ret == rtc::RefCountReleaseStatus::kDroppedLastRef)
    delete this;
  return ret;
}

// This method here to avoid subclasses has to implement this method.
// Call perf test will use Internal::Call::CreateVideoSendStream() to inject
// FecController.
VideoSendStream* Call::CreateVideoSendStream(
    VideoSendStream::Config config,
    VideoEncoderConfig encoder_config,
    std::unique_ptr<FecController> fec_controller) {
  return nullptr;
}

namespace internal {

Call::ReceiveStats::ReceiveStats(Clock* clock)
    : received_bytes_per_second_counter_(clock, nullptr, false),
      received_audio_bytes_per_second_counter_(clock, nullptr, false),
      received_video_bytes_per_second_counter_(clock, nullptr, false),
      received_rtcp_bytes_per_second_counter_(clock, nullptr, false) {
  sequence_checker_.Detach();
}

void Call::ReceiveStats::AddReceivedRtcpBytes(int bytes) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (received_bytes_per_second_counter_.HasSample()) {
    // First RTP packet has been received.
    received_bytes_per_second_counter_.Add(static_cast<int>(bytes));
    received_rtcp_bytes_per_second_counter_.Add(static_cast<int>(bytes));
  }
}

void Call::ReceiveStats::AddReceivedAudioBytes(int bytes,
                                               webrtc::Timestamp arrival_time) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  received_bytes_per_second_counter_.Add(bytes);
  received_audio_bytes_per_second_counter_.Add(bytes);
  if (!first_received_rtp_audio_timestamp_)
    first_received_rtp_audio_timestamp_ = arrival_time;
  last_received_rtp_audio_timestamp_ = arrival_time;
}

void Call::ReceiveStats::AddReceivedVideoBytes(int bytes,
                                               webrtc::Timestamp arrival_time) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  received_bytes_per_second_counter_.Add(bytes);
  received_video_bytes_per_second_counter_.Add(bytes);
  if (!first_received_rtp_video_timestamp_)
    first_received_rtp_video_timestamp_ = arrival_time;
  last_received_rtp_video_timestamp_ = arrival_time;
}

Call::ReceiveStats::~ReceiveStats() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (first_received_rtp_audio_timestamp_) {
    RTC_HISTOGRAM_COUNTS_100000(
        "WebRTC.Call.TimeReceivingAudioRtpPacketsInSeconds",
        (*last_received_rtp_audio_timestamp_ -
         *first_received_rtp_audio_timestamp_)
            .seconds());
  }
  if (first_received_rtp_video_timestamp_) {
    RTC_HISTOGRAM_COUNTS_100000(
        "WebRTC.Call.TimeReceivingVideoRtpPacketsInSeconds",
        (*last_received_rtp_video_timestamp_ -
         *first_received_rtp_video_timestamp_)
            .seconds());
  }
  const int kMinRequiredPeriodicSamples = 5;
  AggregatedStats video_bytes_per_sec =
      received_video_bytes_per_second_counter_.GetStats();
  if (video_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.VideoBitrateReceivedInKbps",
                                video_bytes_per_sec.average * 8 / 1000);
    RTC_LOG(LS_INFO) << "WebRTC.Call.VideoBitrateReceivedInBps, "
                     << video_bytes_per_sec.ToStringWithMultiplier(8);
  }
  AggregatedStats audio_bytes_per_sec =
      received_audio_bytes_per_second_counter_.GetStats();
  if (audio_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.AudioBitrateReceivedInKbps",
                                audio_bytes_per_sec.average * 8 / 1000);
    RTC_LOG(LS_INFO) << "WebRTC.Call.AudioBitrateReceivedInBps, "
                     << audio_bytes_per_sec.ToStringWithMultiplier(8);
  }
  AggregatedStats rtcp_bytes_per_sec =
      received_rtcp_bytes_per_second_counter_.GetStats();
  if (rtcp_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.RtcpBitrateReceivedInBps",
                                rtcp_bytes_per_sec.average * 8);
    RTC_LOG(LS_INFO) << "WebRTC.Call.RtcpBitrateReceivedInBps, "
                     << rtcp_bytes_per_sec.ToStringWithMultiplier(8);
  }
  AggregatedStats recv_bytes_per_sec =
      received_bytes_per_second_counter_.GetStats();
  if (recv_bytes_per_sec.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.BitrateReceivedInKbps",
                                recv_bytes_per_sec.average * 8 / 1000);
    RTC_LOG(LS_INFO) << "WebRTC.Call.BitrateReceivedInBps, "
                     << recv_bytes_per_sec.ToStringWithMultiplier(8);
  }
}

Call::SendStats::SendStats(Clock* clock)
    : clock_(clock),
      estimated_send_bitrate_kbps_counter_(clock, nullptr, true),
      pacer_bitrate_kbps_counter_(clock, nullptr, true) {
  destructor_sequence_checker_.Detach();
  sequence_checker_.Detach();
}

Call::SendStats::~SendStats() {
  RTC_DCHECK_RUN_ON(&destructor_sequence_checker_);
  if (!first_sent_packet_time_)
    return;

  TimeDelta elapsed = clock_->CurrentTime() - *first_sent_packet_time_;
  if (elapsed.seconds() < metrics::kMinRunTimeInSeconds)
    return;

  const int kMinRequiredPeriodicSamples = 5;
  AggregatedStats send_bitrate_stats =
      estimated_send_bitrate_kbps_counter_.ProcessAndGetStats();
  if (send_bitrate_stats.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.EstimatedSendBitrateInKbps",
                                send_bitrate_stats.average);
    RTC_LOG(LS_INFO) << "WebRTC.Call.EstimatedSendBitrateInKbps, "
                     << send_bitrate_stats.ToString();
  }
  AggregatedStats pacer_bitrate_stats =
      pacer_bitrate_kbps_counter_.ProcessAndGetStats();
  if (pacer_bitrate_stats.num_samples > kMinRequiredPeriodicSamples) {
    RTC_HISTOGRAM_COUNTS_100000("WebRTC.Call.PacerBitrateInKbps",
                                pacer_bitrate_stats.average);
    RTC_LOG(LS_INFO) << "WebRTC.Call.PacerBitrateInKbps, "
                     << pacer_bitrate_stats.ToString();
  }
}

void Call::SendStats::SetFirstPacketTime(
    absl::optional<Timestamp> first_sent_packet_time) {
  RTC_DCHECK_RUN_ON(&destructor_sequence_checker_);
  first_sent_packet_time_ = first_sent_packet_time;
}

void Call::SendStats::PauseSendAndPacerBitrateCounters() {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  estimated_send_bitrate_kbps_counter_.ProcessAndPause();
  pacer_bitrate_kbps_counter_.ProcessAndPause();
}

void Call::SendStats::AddTargetBitrateSample(uint32_t target_bitrate_bps) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  estimated_send_bitrate_kbps_counter_.Add(target_bitrate_bps / 1000);
  // Pacer bitrate may be higher than bitrate estimate if enforcing min
  // bitrate.
  uint32_t pacer_bitrate_bps =
      std::max(target_bitrate_bps, min_allocated_send_bitrate_bps_);
  pacer_bitrate_kbps_counter_.Add(pacer_bitrate_bps / 1000);
}

void Call::SendStats::SetMinAllocatableRate(BitrateAllocationLimits limits) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  min_allocated_send_bitrate_bps_ = limits.min_allocatable_rate.bps();
}

Call::Call(Clock* clock,
           const Call::Config& config,
           std::unique_ptr<RtpTransportControllerSendInterface> transport_send,
           rtc::scoped_refptr<SharedModuleThread> module_process_thread,
           TaskQueueFactory* task_queue_factory)
    : clock_(clock),
      task_queue_factory_(task_queue_factory),
      worker_thread_(GetCurrentTaskQueueOrThread()),
      // If `network_task_queue_` was set to nullptr, network related calls
      // must be made on `worker_thread_` (i.e. they're one and the same).
      network_thread_(config.network_task_queue_ ? config.network_task_queue_
                                                 : worker_thread_),
      decode_sync_(config.metronome
                       ? std::make_unique<DecodeSynchronizer>(clock_,
                                                              config.metronome,
                                                              worker_thread_)
                       : nullptr),
      num_cpu_cores_(CpuInfo::DetectNumberOfCores()),
      module_process_thread_(std::move(module_process_thread)),
      call_stats_(new CallStats(clock_, worker_thread_)),
      bitrate_allocator_(new BitrateAllocator(this)),
      config_(config),
      trials_(*config.trials),
      audio_network_state_(kNetworkDown),
      video_network_state_(kNetworkDown),
      aggregate_network_up_(false),
      event_log_(config.event_log),
      receive_stats_(clock_),
      send_stats_(clock_),
      receive_side_cc_(clock,
                       absl::bind_front(&PacketRouter::SendCombinedRtcpPacket,
                                        transport_send->packet_router()),
                       absl::bind_front(&PacketRouter::SendRemb,
                                        transport_send->packet_router()),
                       /*network_state_estimator=*/nullptr),
      receive_time_calculator_(ReceiveTimeCalculator::CreateFromFieldTrial()),
      video_send_delay_stats_(new SendDelayStats(clock_)),
      start_of_call_(clock_->CurrentTime()),
      transport_send_ptr_(transport_send.get()),
      transport_send_(std::move(transport_send)) {
  RTC_DCHECK(config.event_log != nullptr);
  RTC_DCHECK(config.trials != nullptr);
  RTC_DCHECK(network_thread_);
  RTC_DCHECK(worker_thread_->IsCurrent());

  receive_11993_checker_.Detach();
  send_transport_sequence_checker_.Detach();
  sent_packet_sequence_checker_.Detach();

  // Do not remove this call; it is here to convince the compiler that the
  // WebRTC source timestamp string needs to be in the final binary.
  LoadWebRTCVersionInRegister();

  call_stats_->RegisterStatsObserver(&receive_side_cc_);

  module_process_thread_->process_thread()->RegisterModule(
      receive_side_cc_.GetRemoteBitrateEstimator(true), RTC_FROM_HERE);
  module_process_thread_->process_thread()->RegisterModule(&receive_side_cc_,
                                                           RTC_FROM_HERE);
}

Call::~Call() {
  RTC_DCHECK_RUN_ON(worker_thread_);

  RTC_CHECK(audio_send_ssrcs_.empty());
  RTC_CHECK(video_send_ssrcs_.empty());
  RTC_CHECK(video_send_streams_.empty());
  RTC_CHECK(audio_receive_streams_.empty());
  RTC_CHECK(video_receive_streams_.empty());

  module_process_thread_->process_thread()->DeRegisterModule(
      receive_side_cc_.GetRemoteBitrateEstimator(true));
  module_process_thread_->process_thread()->DeRegisterModule(&receive_side_cc_);
  call_stats_->DeregisterStatsObserver(&receive_side_cc_);
  send_stats_.SetFirstPacketTime(transport_send_->GetFirstPacketTime());

  RTC_HISTOGRAM_COUNTS_100000(
      "WebRTC.Call.LifetimeInSeconds",
      (clock_->CurrentTime() - start_of_call_).seconds());
}

void Call::EnsureStarted() {
  if (is_started_) {
    return;
  }
  is_started_ = true;

  call_stats_->EnsureStarted();

  // This call seems to kick off a number of things, so probably better left
  // off being kicked off on request rather than in the ctor.
  transport_send_->RegisterTargetTransferRateObserver(this);

  module_process_thread_->EnsureStarted();
  transport_send_->EnsureStarted();
}

void Call::SetClientBitratePreferences(const BitrateSettings& preferences) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  GetTransportControllerSend()->SetClientBitratePreferences(preferences);
}

PacketReceiver* Call::Receiver() {
  return this;
}

webrtc::AudioSendStream* Call::CreateAudioSendStream(
    const webrtc::AudioSendStream::Config& config) {
  TRACE_EVENT0("webrtc", "Call::CreateAudioSendStream");
  RTC_DCHECK_RUN_ON(worker_thread_);

  EnsureStarted();

  // Stream config is logged in AudioSendStream::ConfigureStream, as it may
  // change during the stream's lifetime.
  absl::optional<RtpState> suspended_rtp_state;
  {
    const auto& iter = suspended_audio_send_ssrcs_.find(config.rtp.ssrc);
    if (iter != suspended_audio_send_ssrcs_.end()) {
      suspended_rtp_state.emplace(iter->second);
    }
  }

  AudioSendStream* send_stream = new AudioSendStream(
      clock_, config, config_.audio_state, task_queue_factory_,
      transport_send_.get(), bitrate_allocator_.get(), event_log_,
      call_stats_->AsRtcpRttStats(), suspended_rtp_state);
  RTC_DCHECK(audio_send_ssrcs_.find(config.rtp.ssrc) ==
             audio_send_ssrcs_.end());
  audio_send_ssrcs_[config.rtp.ssrc] = send_stream;

  // TODO(bugs.webrtc.org/11993): call AssociateSendStream and
  // UpdateAggregateNetworkState asynchronously on the network thread.
  for (AudioReceiveStream* stream : audio_receive_streams_) {
    if (stream->local_ssrc() == config.rtp.ssrc) {
      stream->AssociateSendStream(send_stream);
    }
  }

  UpdateAggregateNetworkState();

  return send_stream;
}

void Call::DestroyAudioSendStream(webrtc::AudioSendStream* send_stream) {
  TRACE_EVENT0("webrtc", "Call::DestroyAudioSendStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(send_stream != nullptr);

  send_stream->Stop();

  const uint32_t ssrc = send_stream->GetConfig().rtp.ssrc;
  webrtc::internal::AudioSendStream* audio_send_stream =
      static_cast<webrtc::internal::AudioSendStream*>(send_stream);
  suspended_audio_send_ssrcs_[ssrc] = audio_send_stream->GetRtpState();

  size_t num_deleted = audio_send_ssrcs_.erase(ssrc);
  RTC_DCHECK_EQ(1, num_deleted);

  // TODO(bugs.webrtc.org/11993): call AssociateSendStream and
  // UpdateAggregateNetworkState asynchronously on the network thread.
  for (AudioReceiveStream* stream : audio_receive_streams_) {
    if (stream->local_ssrc() == ssrc) {
      stream->AssociateSendStream(nullptr);
    }
  }

  UpdateAggregateNetworkState();

  delete send_stream;
}

webrtc::AudioReceiveStream* Call::CreateAudioReceiveStream(
    const webrtc::AudioReceiveStream::Config& config) {
  TRACE_EVENT0("webrtc", "Call::CreateAudioReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  EnsureStarted();
  event_log_->Log(std::make_unique<RtcEventAudioReceiveStreamConfig>(
      CreateRtcLogStreamConfig(config)));

  AudioReceiveStream* receive_stream = new AudioReceiveStream(
      clock_, transport_send_->packet_router(), config_.neteq_factory, config,
      config_.audio_state, event_log_);
  audio_receive_streams_.insert(receive_stream);

  // TODO(bugs.webrtc.org/11993): Make the registration on the network thread
  // (asynchronously). The registration and `audio_receiver_controller_` need
  // to live on the network thread.
  receive_stream->RegisterWithTransport(&audio_receiver_controller_);

  // TODO(bugs.webrtc.org/11993): Update the below on the network thread.
  // We could possibly set up the audio_receiver_controller_ association up
  // as part of the async setup.
  RegisterReceiveStream(config.rtp.remote_ssrc, receive_stream);

  ConfigureSync(config.sync_group);

  auto it = audio_send_ssrcs_.find(config.rtp.local_ssrc);
  if (it != audio_send_ssrcs_.end()) {
    receive_stream->AssociateSendStream(it->second);
  }

  UpdateAggregateNetworkState();
  return receive_stream;
}

void Call::DestroyAudioReceiveStream(
    webrtc::AudioReceiveStream* receive_stream) {
  TRACE_EVENT0("webrtc", "Call::DestroyAudioReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(receive_stream != nullptr);
  webrtc::internal::AudioReceiveStream* audio_receive_stream =
      static_cast<webrtc::internal::AudioReceiveStream*>(receive_stream);

  // TODO(bugs.webrtc.org/11993): Access the map, rtp config, call ConfigureSync
  // and UpdateAggregateNetworkState on the network thread. The call to
  // `UnregisterFromTransport` should also happen on the network thread.
  audio_receive_stream->UnregisterFromTransport();

  uint32_t ssrc = audio_receive_stream->remote_ssrc();
  const AudioReceiveStream::Config& config = audio_receive_stream->config();
  receive_side_cc_.GetRemoteBitrateEstimator(UseSendSideBwe(config.rtp))
      ->RemoveStream(ssrc);

  audio_receive_streams_.erase(audio_receive_stream);

  // After calling erase(), call ConfigureSync. This will clear associated
  // video streams or associate them with a different audio stream if one exists
  // for this sync_group.
  ConfigureSync(audio_receive_stream->config().sync_group);

  UnregisterReceiveStream(ssrc);

  UpdateAggregateNetworkState();
  // TODO(bugs.webrtc.org/11993): Consider if deleting `audio_receive_stream`
  // on the network thread would be better or if we'd need to tear down the
  // state in two phases.
  delete audio_receive_stream;
}

// This method can be used for Call tests with external fec controller factory.
webrtc::VideoSendStream* Call::CreateVideoSendStream(
    webrtc::VideoSendStream::Config config,
    VideoEncoderConfig encoder_config,
    std::unique_ptr<FecController> fec_controller) {
  TRACE_EVENT0("webrtc", "Call::CreateVideoSendStream");
  RTC_DCHECK_RUN_ON(worker_thread_);

  EnsureStarted();

  video_send_delay_stats_->AddSsrcs(config);
  for (size_t ssrc_index = 0; ssrc_index < config.rtp.ssrcs.size();
       ++ssrc_index) {
    event_log_->Log(std::make_unique<RtcEventVideoSendStreamConfig>(
        CreateRtcLogStreamConfig(config, ssrc_index)));
  }

  // TODO(mflodman): Base the start bitrate on a current bandwidth estimate, if
  // the call has already started.
  // Copy ssrcs from `config` since `config` is moved.
  std::vector<uint32_t> ssrcs = config.rtp.ssrcs;

  VideoSendStream* send_stream = new VideoSendStream(
      clock_, num_cpu_cores_, task_queue_factory_, network_thread_,
      call_stats_->AsRtcpRttStats(), transport_send_.get(),
      bitrate_allocator_.get(), video_send_delay_stats_.get(), event_log_,
      std::move(config), std::move(encoder_config), suspended_video_send_ssrcs_,
      suspended_video_payload_states_, std::move(fec_controller));

  for (uint32_t ssrc : ssrcs) {
    RTC_DCHECK(video_send_ssrcs_.find(ssrc) == video_send_ssrcs_.end());
    video_send_ssrcs_[ssrc] = send_stream;
  }
  video_send_streams_.insert(send_stream);
  video_send_streams_empty_.store(false, std::memory_order_relaxed);

  // Forward resources that were previously added to the call to the new stream.
  for (const auto& resource_forwarder : adaptation_resource_forwarders_) {
    resource_forwarder->OnCreateVideoSendStream(send_stream);
  }

  UpdateAggregateNetworkState();

  return send_stream;
}

webrtc::VideoSendStream* Call::CreateVideoSendStream(
    webrtc::VideoSendStream::Config config,
    VideoEncoderConfig encoder_config) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  if (config_.fec_controller_factory) {
    RTC_LOG(LS_INFO) << "External FEC Controller will be used.";
  }
  std::unique_ptr<FecController> fec_controller =
      config_.fec_controller_factory
          ? config_.fec_controller_factory->CreateFecController()
          : std::make_unique<FecControllerDefault>(clock_);
  return CreateVideoSendStream(std::move(config), std::move(encoder_config),
                               std::move(fec_controller));
}

void Call::DestroyVideoSendStream(webrtc::VideoSendStream* send_stream) {
  TRACE_EVENT0("webrtc", "Call::DestroyVideoSendStream");
  RTC_DCHECK(send_stream != nullptr);
  RTC_DCHECK_RUN_ON(worker_thread_);

  VideoSendStream* send_stream_impl =
      static_cast<VideoSendStream*>(send_stream);

  auto it = video_send_ssrcs_.begin();
  while (it != video_send_ssrcs_.end()) {
    if (it->second == static_cast<VideoSendStream*>(send_stream)) {
      send_stream_impl = it->second;
      video_send_ssrcs_.erase(it++);
    } else {
      ++it;
    }
  }

  // Stop forwarding resources to the stream being destroyed.
  for (const auto& resource_forwarder : adaptation_resource_forwarders_) {
    resource_forwarder->OnDestroyVideoSendStream(send_stream_impl);
  }
  video_send_streams_.erase(send_stream_impl);
  if (video_send_streams_.empty())
    video_send_streams_empty_.store(true, std::memory_order_relaxed);

  VideoSendStream::RtpStateMap rtp_states;
  VideoSendStream::RtpPayloadStateMap rtp_payload_states;
  send_stream_impl->StopPermanentlyAndGetRtpStates(&rtp_states,
                                                   &rtp_payload_states);
  for (const auto& kv : rtp_states) {
    suspended_video_send_ssrcs_[kv.first] = kv.second;
  }
  for (const auto& kv : rtp_payload_states) {
    suspended_video_payload_states_[kv.first] = kv.second;
  }

  UpdateAggregateNetworkState();
  // TODO(tommi): consider deleting on the same thread as runs
  // StopPermanentlyAndGetRtpStates.
  delete send_stream_impl;
}

webrtc::VideoReceiveStream* Call::CreateVideoReceiveStream(
    webrtc::VideoReceiveStream::Config configuration) {
  TRACE_EVENT0("webrtc", "Call::CreateVideoReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);

  receive_side_cc_.SetSendPeriodicFeedback(
      SendPeriodicFeedback(configuration.rtp.extensions));

  EnsureStarted();

  event_log_->Log(std::make_unique<RtcEventVideoReceiveStreamConfig>(
      CreateRtcLogStreamConfig(configuration)));

  // TODO(bugs.webrtc.org/11993): Move the registration between `receive_stream`
  // and `video_receiver_controller_` out of VideoReceiveStream2 construction
  // and set it up asynchronously on the network thread (the registration and
  // `video_receiver_controller_` need to live on the network thread).
  VideoReceiveStream2* receive_stream = new VideoReceiveStream2(
      task_queue_factory_, this, num_cpu_cores_,
      transport_send_->packet_router(), std::move(configuration),
      call_stats_.get(), clock_, new VCMTiming(clock_),
      &nack_periodic_processor_, decode_sync_.get());
  // TODO(bugs.webrtc.org/11993): Set this up asynchronously on the network
  // thread.
  receive_stream->RegisterWithTransport(&video_receiver_controller_);

  const webrtc::VideoReceiveStream::Config::Rtp& rtp = receive_stream->rtp();
  if (rtp.rtx_ssrc) {
    // We record identical config for the rtx stream as for the main
    // stream. Since the transport_send_cc negotiation is per payload
    // type, we may get an incorrect value for the rtx stream, but
    // that is unlikely to matter in practice.
    RegisterReceiveStream(rtp.rtx_ssrc, receive_stream);
  }
  RegisterReceiveStream(rtp.remote_ssrc, receive_stream);
  video_receive_streams_.insert(receive_stream);

  ConfigureSync(receive_stream->sync_group());

  receive_stream->SignalNetworkState(video_network_state_);
  UpdateAggregateNetworkState();
  return receive_stream;
}

void Call::DestroyVideoReceiveStream(
    webrtc::VideoReceiveStream* receive_stream) {
  TRACE_EVENT0("webrtc", "Call::DestroyVideoReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);
  RTC_DCHECK(receive_stream != nullptr);
  VideoReceiveStream2* receive_stream_impl =
      static_cast<VideoReceiveStream2*>(receive_stream);
  // TODO(bugs.webrtc.org/11993): Unregister on the network thread.
  receive_stream_impl->UnregisterFromTransport();

  const webrtc::VideoReceiveStream::Config::Rtp& rtp =
      receive_stream_impl->rtp();

  // Remove all ssrcs pointing to a receive stream. As RTX retransmits on a
  // separate SSRC there can be either one or two.
  UnregisterReceiveStream(rtp.remote_ssrc);
  if (rtp.rtx_ssrc) {
    UnregisterReceiveStream(rtp.rtx_ssrc);
  }
  video_receive_streams_.erase(receive_stream_impl);
  ConfigureSync(receive_stream_impl->sync_group());

  receive_side_cc_.GetRemoteBitrateEstimator(UseSendSideBwe(rtp))
      ->RemoveStream(rtp.remote_ssrc);

  UpdateAggregateNetworkState();
  delete receive_stream_impl;
}

FlexfecReceiveStream* Call::CreateFlexfecReceiveStream(
    const FlexfecReceiveStream::Config& config) {
  TRACE_EVENT0("webrtc", "Call::CreateFlexfecReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);

  RecoveredPacketReceiver* recovered_packet_receiver = this;

  FlexfecReceiveStreamImpl* receive_stream;

  // Unlike the video and audio receive streams, FlexfecReceiveStream implements
  // RtpPacketSinkInterface itself, and hence its constructor passes its `this`
  // pointer to video_receiver_controller_->CreateStream(). Calling the
  // constructor while on the worker thread ensures that we don't call
  // OnRtpPacket until the constructor is finished and the object is
  // in a valid state, since OnRtpPacket runs on the same thread.
  receive_stream = new FlexfecReceiveStreamImpl(
      clock_, config, recovered_packet_receiver, call_stats_->AsRtcpRttStats());

  // TODO(bugs.webrtc.org/11993): Set this up asynchronously on the network
  // thread.
  receive_stream->RegisterWithTransport(&video_receiver_controller_);
  RegisterReceiveStream(config.rtp.remote_ssrc, receive_stream);

  // TODO(brandtr): Store config in RtcEventLog here.

  return receive_stream;
}

void Call::DestroyFlexfecReceiveStream(FlexfecReceiveStream* receive_stream) {
  TRACE_EVENT0("webrtc", "Call::DestroyFlexfecReceiveStream");
  RTC_DCHECK_RUN_ON(worker_thread_);

  FlexfecReceiveStreamImpl* receive_stream_impl =
      static_cast<FlexfecReceiveStreamImpl*>(receive_stream);
  // TODO(bugs.webrtc.org/11993): Unregister on the network thread.
  receive_stream_impl->UnregisterFromTransport();

  RTC_DCHECK(receive_stream != nullptr);
  const FlexfecReceiveStream::RtpConfig& rtp = receive_stream->rtp_config();
  UnregisterReceiveStream(rtp.remote_ssrc);

  // Remove all SSRCs pointing to the FlexfecReceiveStreamImpl to be
  // destroyed.
  receive_side_cc_.GetRemoteBitrateEstimator(UseSendSideBwe(rtp))
      ->RemoveStream(rtp.remote_ssrc);

  delete receive_stream;
}

void Call::AddAdaptationResource(rtc::scoped_refptr<Resource> resource) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  adaptation_resource_forwarders_.push_back(
      std::make_unique<ResourceVideoSendStreamForwarder>(resource));
  const auto& resource_forwarder = adaptation_resource_forwarders_.back();
  for (VideoSendStream* send_stream : video_send_streams_) {
    resource_forwarder->OnCreateVideoSendStream(send_stream);
  }
}

RtpTransportControllerSendInterface* Call::GetTransportControllerSend() {
  return transport_send_.get();
}

Call::Stats Call::GetStats() const {
  RTC_DCHECK_RUN_ON(worker_thread_);

  Stats stats;
  // TODO(srte): It is unclear if we only want to report queues if network is
  // available.
  stats.pacer_delay_ms =
      aggregate_network_up_ ? transport_send_->GetPacerQueuingDelayMs() : 0;

  stats.rtt_ms = call_stats_->LastProcessedRtt();

  // Fetch available send/receive bitrates.
  std::vector<unsigned int> ssrcs;
  uint32_t recv_bandwidth = 0;
  receive_side_cc_.GetRemoteBitrateEstimator(false)->LatestEstimate(
      &ssrcs, &recv_bandwidth);
  stats.recv_bandwidth_bps = recv_bandwidth;
  stats.send_bandwidth_bps =
      last_bandwidth_bps_.load(std::memory_order_relaxed);
  stats.max_padding_bitrate_bps =
      configured_max_padding_bitrate_bps_.load(std::memory_order_relaxed);

  return stats;
}

const WebRtcKeyValueConfig& Call::trials() const {
  return trials_;
}

TaskQueueBase* Call::network_thread() const {
  return network_thread_;
}

TaskQueueBase* Call::worker_thread() const {
  return worker_thread_;
}

void Call::SignalChannelNetworkState(MediaType media, NetworkState state) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(media == MediaType::AUDIO || media == MediaType::VIDEO);

  auto closure = [this, media, state]() {
    // TODO(bugs.webrtc.org/11993): Move this over to the network thread.
    RTC_DCHECK_RUN_ON(worker_thread_);
    if (media == MediaType::AUDIO) {
      audio_network_state_ = state;
    } else {
      RTC_DCHECK_EQ(media, MediaType::VIDEO);
      video_network_state_ = state;
    }

    // TODO(tommi): Is it necessary to always do this, including if there
    // was no change in state?
    UpdateAggregateNetworkState();

    // TODO(tommi): Is it right to do this if media == AUDIO?
    for (VideoReceiveStream2* video_receive_stream : video_receive_streams_) {
      video_receive_stream->SignalNetworkState(video_network_state_);
    }
  };

  if (network_thread_ == worker_thread_) {
    closure();
  } else {
    // TODO(bugs.webrtc.org/11993): Remove workaround when we no longer need to
    // post to the worker thread.
    worker_thread_->PostTask(ToQueuedTask(task_safety_, std::move(closure)));
  }
}

void Call::OnAudioTransportOverheadChanged(int transport_overhead_per_packet) {
  RTC_DCHECK_RUN_ON(network_thread_);
  worker_thread_->PostTask(
      ToQueuedTask(task_safety_, [this, transport_overhead_per_packet]() {
        // TODO(bugs.webrtc.org/11993): Move this over to the network thread.
        RTC_DCHECK_RUN_ON(worker_thread_);
        for (auto& kv : audio_send_ssrcs_) {
          kv.second->SetTransportOverhead(transport_overhead_per_packet);
        }
      }));
}

void Call::UpdateAggregateNetworkState() {
  // TODO(bugs.webrtc.org/11993): Move this over to the network thread.
  // RTC_DCHECK_RUN_ON(network_thread_);

  RTC_DCHECK_RUN_ON(worker_thread_);

  bool have_audio =
      !audio_send_ssrcs_.empty() || !audio_receive_streams_.empty();
  bool have_video =
      !video_send_ssrcs_.empty() || !video_receive_streams_.empty();

  bool aggregate_network_up =
      ((have_video && video_network_state_ == kNetworkUp) ||
       (have_audio && audio_network_state_ == kNetworkUp));

  if (aggregate_network_up != aggregate_network_up_) {
    RTC_LOG(LS_INFO)
        << "UpdateAggregateNetworkState: aggregate_state change to "
        << (aggregate_network_up ? "up" : "down");
  } else {
    RTC_LOG(LS_VERBOSE)
        << "UpdateAggregateNetworkState: aggregate_state remains at "
        << (aggregate_network_up ? "up" : "down");
  }
  aggregate_network_up_ = aggregate_network_up;

  transport_send_->OnNetworkAvailability(aggregate_network_up);
}

void Call::OnLocalSsrcUpdated(webrtc::AudioReceiveStream& stream,
                              uint32_t local_ssrc) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  webrtc::internal::AudioReceiveStream& receive_stream =
      static_cast<webrtc::internal::AudioReceiveStream&>(stream);

  receive_stream.SetLocalSsrc(local_ssrc);
  auto it = audio_send_ssrcs_.find(local_ssrc);
  receive_stream.AssociateSendStream(it != audio_send_ssrcs_.end() ? it->second
                                                                   : nullptr);
}

void Call::OnUpdateSyncGroup(webrtc::AudioReceiveStream& stream,
                             const std::string& sync_group) {
  RTC_DCHECK_RUN_ON(worker_thread_);
  webrtc::internal::AudioReceiveStream& receive_stream =
      static_cast<webrtc::internal::AudioReceiveStream&>(stream);
  receive_stream.SetSyncGroup(sync_group);
  ConfigureSync(sync_group);
}

void Call::OnSentPacket(const rtc::SentPacket& sent_packet) {
  RTC_DCHECK_RUN_ON(&sent_packet_sequence_checker_);
  // When bundling is in effect, multiple senders may be sharing the same
  // transport. It means every |sent_packet| will be multiply notified from
  // different channels, WebRtcVoiceMediaChannel or WebRtcVideoChannel. Record
  // |last_sent_packet_| to deduplicate redundant notifications to downstream.
  // (https://crbug.com/webrtc/13437): Pass all packets without a |packet_id| to
  // downstream.
  if (last_sent_packet_.has_value() && last_sent_packet_->packet_id != -1 &&
      last_sent_packet_->packet_id == sent_packet.packet_id &&
      last_sent_packet_->send_time_ms == sent_packet.send_time_ms) {
    return;
  }
  last_sent_packet_ = sent_packet;

  // In production and with most tests, this method will be called on the
  // network thread. However some test classes such as DirectTransport don't
  // incorporate a network thread. This means that tests for RtpSenderEgress
  // and ModuleRtpRtcpImpl2 that use DirectTransport, will call this method
  // on a ProcessThread. This is alright as is since we forward the call to
  // implementations that either just do a PostTask or use locking.
  video_send_delay_stats_->OnSentPacket(sent_packet.packet_id,
                                        clock_->TimeInMilliseconds());
  transport_send_->OnSentPacket(sent_packet);
}

void Call::OnStartRateUpdate(DataRate start_rate) {
  RTC_DCHECK_RUN_ON(&send_transport_sequence_checker_);
  bitrate_allocator_->UpdateStartRate(start_rate.bps<uint32_t>());
}

void Call::OnTargetTransferRate(TargetTransferRate msg) {
  RTC_DCHECK_RUN_ON(&send_transport_sequence_checker_);

  uint32_t target_bitrate_bps = msg.target_rate.bps();
  // For controlling the rate of feedback messages.
  receive_side_cc_.OnBitrateChanged(target_bitrate_bps);
  bitrate_allocator_->OnNetworkEstimateChanged(msg);

  last_bandwidth_bps_.store(target_bitrate_bps, std::memory_order_relaxed);

  // Ignore updates if bitrate is zero (the aggregate network state is
  // down) or if we're not sending video.
  // Using `video_send_streams_empty_` is racy but as the caller can't
  // reasonably expect synchronize with changes in `video_send_streams_` (being
  // on `send_transport_sequence_checker`), we can avoid a PostTask this way.
  if (target_bitrate_bps == 0 ||
      video_send_streams_empty_.load(std::memory_order_relaxed)) {
    send_stats_.PauseSendAndPacerBitrateCounters();
  } else {
    send_stats_.AddTargetBitrateSample(target_bitrate_bps);
  }
}

void Call::OnAllocationLimitsChanged(BitrateAllocationLimits limits) {
  RTC_DCHECK_RUN_ON(&send_transport_sequence_checker_);

  transport_send_ptr_->SetAllocatedSendBitrateLimits(limits);
  send_stats_.SetMinAllocatableRate(limits);
  configured_max_padding_bitrate_bps_.store(limits.max_padding_rate.bps(),
                                            std::memory_order_relaxed);
}

// RTC_RUN_ON(worker_thread_)
AudioReceiveStream* Call::FindAudioStreamForSyncGroup(
    const std::string& sync_group) {
  RTC_DCHECK_RUN_ON(&receive_11993_checker_);
  if (!sync_group.empty()) {
    for (AudioReceiveStream* stream : audio_receive_streams_) {
      if (stream->config().sync_group == sync_group)
        return stream;
    }
  }

  return nullptr;
}

// TODO(bugs.webrtc.org/11993): Expect to be called on the network thread.
// RTC_RUN_ON(worker_thread_)
void Call::ConfigureSync(const std::string& sync_group) {
  // `audio_stream` may be nullptr when clearing the audio stream for a group.
  AudioReceiveStream* audio_stream = FindAudioStreamForSyncGroup(sync_group);

  size_t num_synced_streams = 0;
  for (VideoReceiveStream2* video_stream : video_receive_streams_) {
    if (video_stream->sync_group() != sync_group)
      continue;
    ++num_synced_streams;
    // TODO(bugs.webrtc.org/4762): Support synchronizing more than one A/V pair.
    // Attempting to sync more than one audio/video pair within the same sync
    // group is not supported in the current implementation.
    // Only sync the first A/V pair within this sync group.
    if (num_synced_streams == 1) {
      // sync_audio_stream may be null and that's ok.
      video_stream->SetSync(audio_stream);
    } else {
      video_stream->SetSync(nullptr);
    }
  }
}

// RTC_RUN_ON(network_thread_)
void Call::DeliverRtcp(MediaType media_type, rtc::CopyOnWriteBuffer packet) {
  TRACE_EVENT0("webrtc", "Call::DeliverRtcp");

  // TODO(bugs.webrtc.org/11993): This DCHECK is here just to maintain the
  // invariant that currently the only call path to this function is via
  // `PeerConnection::InitializeRtcpCallback()`. DeliverRtp on the other hand
  // gets called via the channel classes and
  // WebRtc[Audio|Video]Channel's `OnPacketReceived`. We'll remove the
  // PeerConnection involvement as well as
  // `JsepTransportController::OnRtcpPacketReceived_n` and `rtcp_handler`
  // and make sure that the flow of packets is consistent from the
  // `RtpTransport` class, via the *Channel and *Engine classes and into Call.
  // This way we'll also know more about the context of the packet.
  RTC_DCHECK_EQ(media_type, MediaType::ANY);

  // TODO(bugs.webrtc.org/11993): This should execute directly on the network
  // thread.
  worker_thread_->PostTask(
      ToQueuedTask(task_safety_, [this, packet = std::move(packet)]() {
        RTC_DCHECK_RUN_ON(worker_thread_);

        receive_stats_.AddReceivedRtcpBytes(static_cast<int>(packet.size()));
        bool rtcp_delivered = false;
        for (VideoReceiveStream2* stream : video_receive_streams_) {
          if (stream->DeliverRtcp(packet.cdata(), packet.size()))
            rtcp_delivered = true;
        }

        for (AudioReceiveStream* stream : audio_receive_streams_) {
          stream->DeliverRtcp(packet.cdata(), packet.size());
          rtcp_delivered = true;
        }

        for (VideoSendStream* stream : video_send_streams_) {
          stream->DeliverRtcp(packet.cdata(), packet.size());
          rtcp_delivered = true;
        }

        for (auto& kv : audio_send_ssrcs_) {
          kv.second->DeliverRtcp(packet.cdata(), packet.size());
          rtcp_delivered = true;
        }

        if (rtcp_delivered) {
          event_log_->Log(std::make_unique<RtcEventRtcpPacketIncoming>(
              rtc::MakeArrayView(packet.cdata(), packet.size())));
        }
      }));
}

PacketReceiver::DeliveryStatus Call::DeliverRtp(MediaType media_type,
                                                rtc::CopyOnWriteBuffer packet,
                                                int64_t packet_time_us) {
  TRACE_EVENT0("webrtc", "Call::DeliverRtp");
  RTC_DCHECK_NE(media_type, MediaType::ANY);

  RtpPacketReceived parsed_packet;
  if (!parsed_packet.Parse(std::move(packet)))
    return DELIVERY_PACKET_ERROR;

  if (packet_time_us != -1) {
    if (receive_time_calculator_) {
      // Repair packet_time_us for clock resets by comparing a new read of
      // the same clock (TimeUTCMicros) to a monotonic clock reading.
      packet_time_us = receive_time_calculator_->ReconcileReceiveTimes(
          packet_time_us, rtc::TimeUTCMicros(), clock_->TimeInMicroseconds());
    }
    parsed_packet.set_arrival_time(Timestamp::Micros(packet_time_us));
  } else {
    parsed_packet.set_arrival_time(clock_->CurrentTime());
  }

  // We might get RTP keep-alive packets in accordance with RFC6263 section 4.6.
  // These are empty (zero length payload) RTP packets with an unsignaled
  // payload type.
  const bool is_keep_alive_packet = parsed_packet.payload_size() == 0;

  RTC_DCHECK(media_type == MediaType::AUDIO || media_type == MediaType::VIDEO ||
             is_keep_alive_packet);

  bool use_send_side_bwe = false;
  if (!IdentifyReceivedPacket(parsed_packet, &use_send_side_bwe))
    return DELIVERY_UNKNOWN_SSRC;

  NotifyBweOfReceivedPacket(parsed_packet, media_type, use_send_side_bwe);

  // RateCounters expect input parameter as int, save it as int,
  // instead of converting each time it is passed to RateCounter::Add below.
  int length = static_cast<int>(parsed_packet.size());
  if (media_type == MediaType::AUDIO) {
    if (audio_receiver_controller_.OnRtpPacket(parsed_packet)) {
      receive_stats_.AddReceivedAudioBytes(length,
                                           parsed_packet.arrival_time());
      event_log_->Log(
          std::make_unique<RtcEventRtpPacketIncoming>(parsed_packet));
      return DELIVERY_OK;
    }
  } else if (media_type == MediaType::VIDEO) {
    parsed_packet.set_payload_type_frequency(kVideoPayloadTypeFrequency);
    if (video_receiver_controller_.OnRtpPacket(parsed_packet)) {
      receive_stats_.AddReceivedVideoBytes(length,
                                           parsed_packet.arrival_time());
      event_log_->Log(
          std::make_unique<RtcEventRtpPacketIncoming>(parsed_packet));
      return DELIVERY_OK;
    }
  }
  return DELIVERY_UNKNOWN_SSRC;
}

PacketReceiver::DeliveryStatus Call::DeliverPacket(
    MediaType media_type,
    rtc::CopyOnWriteBuffer packet,
    int64_t packet_time_us) {
  if (IsRtcpPacket(packet)) {
    RTC_DCHECK_RUN_ON(network_thread_);
    DeliverRtcp(media_type, std::move(packet));
    return DELIVERY_OK;
  }

  RTC_DCHECK_RUN_ON(worker_thread_);
  return DeliverRtp(media_type, std::move(packet), packet_time_us);
}

void Call::OnRecoveredPacket(const uint8_t* packet, size_t length) {
  // TODO(bugs.webrtc.org/11993): Expect to be called on the network thread.
  // This method is called synchronously via `OnRtpPacket()` (see DeliverRtp)
  // on the same thread.
  RTC_DCHECK_RUN_ON(worker_thread_);
  RtpPacketReceived parsed_packet;
  if (!parsed_packet.Parse(packet, length))
    return;

  parsed_packet.set_recovered(true);

  if (!IdentifyReceivedPacket(parsed_packet))
    return;

  // TODO(brandtr): Update here when we support protecting audio packets too.
  parsed_packet.set_payload_type_frequency(kVideoPayloadTypeFrequency);
  video_receiver_controller_.OnRtpPacket(parsed_packet);
}

// RTC_RUN_ON(worker_thread_)
void Call::NotifyBweOfReceivedPacket(const RtpPacketReceived& packet,
                                     MediaType media_type,
                                     bool use_send_side_bwe) {
  RTPHeader header;
  packet.GetHeader(&header);

  ReceivedPacket packet_msg;
  packet_msg.size = DataSize::Bytes(packet.payload_size());
  packet_msg.receive_time = packet.arrival_time();
  if (header.extension.hasAbsoluteSendTime) {
    packet_msg.send_time = header.extension.GetAbsoluteSendTimestamp();
  }
  transport_send_->OnReceivedPacket(packet_msg);

  if (!use_send_side_bwe && header.extension.hasTransportSequenceNumber) {
    // Inconsistent configuration of send side BWE. Do nothing.
    // TODO(nisse): Without this check, we may produce RTCP feedback
    // packets even when not negotiated. But it would be cleaner to
    // move the check down to RTCPSender::SendFeedbackPacket, which
    // would also help the PacketRouter to select an appropriate rtp
    // module in the case that some, but not all, have RTCP feedback
    // enabled.
    return;
  }
  // For audio, we only support send side BWE.
  if (media_type == MediaType::VIDEO ||
      (use_send_side_bwe && header.extension.hasTransportSequenceNumber)) {
    receive_side_cc_.OnReceivedPacket(
        packet.arrival_time().ms(),
        packet.payload_size() + packet.padding_size(), header);
  }
}

bool Call::IdentifyReceivedPacket(RtpPacketReceived& packet,
                                  bool* use_send_side_bwe /*= nullptr*/) {
  RTC_DCHECK_RUN_ON(&receive_11993_checker_);
  auto it = receive_rtp_config_.find(packet.Ssrc());
  if (it == receive_rtp_config_.end()) {
    RTC_DLOG(LS_WARNING) << "receive_rtp_config_ lookup failed for ssrc "
                         << packet.Ssrc();
    return false;
  }

  packet.IdentifyExtensions(
      RtpHeaderExtensionMap(it->second->rtp_config().extensions));

  if (use_send_side_bwe) {
    *use_send_side_bwe = UseSendSideBwe(it->second->rtp_config());
  }

  return true;
}

bool Call::RegisterReceiveStream(uint32_t ssrc, ReceiveStream* stream) {
  RTC_DCHECK_RUN_ON(&receive_11993_checker_);
  RTC_DCHECK(stream);
  auto inserted = receive_rtp_config_.emplace(ssrc, stream);
  if (!inserted.second) {
    RTC_DLOG(LS_WARNING) << "ssrc already registered: " << ssrc;
  }
  return inserted.second;
}

bool Call::UnregisterReceiveStream(uint32_t ssrc) {
  RTC_DCHECK_RUN_ON(&receive_11993_checker_);
  size_t erased = receive_rtp_config_.erase(ssrc);
  if (!erased) {
    RTC_DLOG(LS_WARNING) << "ssrc wasn't registered: " << ssrc;
  }
  return erased != 0u;
}

}  // namespace internal

}  // namespace webrtc
