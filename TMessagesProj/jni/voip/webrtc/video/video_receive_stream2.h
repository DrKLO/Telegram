/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_VIDEO_RECEIVE_STREAM2_H_
#define VIDEO_VIDEO_RECEIVE_STREAM2_H_

#include <memory>
#include <string>
#include <vector>

#include "api/sequence_checker.h"
#include "api/task_queue/task_queue_factory.h"
#include "api/units/timestamp.h"
#include "api/video/recordable_encoded_frame.h"
#include "call/call.h"
#include "call/rtp_packet_sink_interface.h"
#include "call/syncable.h"
#include "call/video_receive_stream.h"
#include "modules/rtp_rtcp/include/flexfec_receiver.h"
#include "modules/rtp_rtcp/source/source_tracker.h"
#include "modules/video_coding/nack_requester.h"
#include "modules/video_coding/video_receiver2.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/thread_annotations.h"
#include "system_wrappers/include/clock.h"
#include "video/frame_buffer_proxy.h"
#include "video/receive_statistics_proxy2.h"
#include "video/rtp_streams_synchronizer2.h"
#include "video/rtp_video_stream_receiver2.h"
#include "video/transport_adapter.h"
#include "video/video_stream_decoder2.h"

namespace webrtc {

class RtpStreamReceiverInterface;
class RtpStreamReceiverControllerInterface;
class RtxReceiveStream;
class VCMTiming;

namespace internal {

class CallStats;

// Utility struct for grabbing metadata from a VideoFrame and processing it
// asynchronously without needing the actual frame data.
// Additionally the caller can bundle information from the current clock
// when the metadata is captured, for accurate reporting and not needeing
// multiple calls to clock->Now().
struct VideoFrameMetaData {
  VideoFrameMetaData(const webrtc::VideoFrame& frame, Timestamp now)
      : rtp_timestamp(frame.timestamp()),
        timestamp_us(frame.timestamp_us()),
        ntp_time_ms(frame.ntp_time_ms()),
        width(frame.width()),
        height(frame.height()),
        decode_timestamp(now) {}

  int64_t render_time_ms() const {
    return timestamp_us / rtc::kNumMicrosecsPerMillisec;
  }

  const uint32_t rtp_timestamp;
  const int64_t timestamp_us;
  const int64_t ntp_time_ms;
  const int width;
  const int height;

  const Timestamp decode_timestamp;
};

class VideoReceiveStream2
    : public webrtc::VideoReceiveStream,
      public rtc::VideoSinkInterface<VideoFrame>,
      public NackSender,
      public RtpVideoStreamReceiver2::OnCompleteFrameCallback,
      public Syncable,
      public CallStatsObserver,
      public FrameSchedulingReceiver {
 public:
  // The default number of milliseconds to pass before re-requesting a key frame
  // to be sent.
  static constexpr int kMaxWaitForKeyFrameMs = 200;
  // The maximum number of buffered encoded frames when encoded output is
  // configured.
  static constexpr size_t kBufferedEncodedFramesMaxSize = 60;

  VideoReceiveStream2(TaskQueueFactory* task_queue_factory,
                      Call* call,
                      int num_cpu_cores,
                      PacketRouter* packet_router,
                      VideoReceiveStream::Config config,
                      CallStats* call_stats,
                      Clock* clock,
                      VCMTiming* timing,
                      NackPeriodicProcessor* nack_periodic_processor,
                      DecodeSynchronizer* decode_sync);
  // Destruction happens on the worker thread. Prior to destruction the caller
  // must ensure that a registration with the transport has been cleared. See
  // `RegisterWithTransport` for details.
  // TODO(tommi): As a further improvement to this, performing the full
  // destruction on the network thread could be made the default.
  ~VideoReceiveStream2() override;

  // Called on `packet_sequence_checker_` to register/unregister with the
  // network transport.
  void RegisterWithTransport(
      RtpStreamReceiverControllerInterface* receiver_controller);
  // If registration has previously been done (via `RegisterWithTransport`) then
  // `UnregisterFromTransport` must be called prior to destruction, on the
  // network thread.
  void UnregisterFromTransport();

  // Convenience getters for parts of the receive stream's config.
  // The accessors must be called on the packet delivery thread in accordance
  // to documentation for RtpConfig (see receive_stream.h), the returned
  // values should not be cached and should just be used within the calling
  // context as some values might change.
  const Config::Rtp& rtp() const;
  const std::string& sync_group() const;

  void SignalNetworkState(NetworkState state);
  bool DeliverRtcp(const uint8_t* packet, size_t length);

  void SetSync(Syncable* audio_syncable);

  // Implements webrtc::VideoReceiveStream.
  void Start() override;
  void Stop() override;

  void SetRtpExtensions(std::vector<RtpExtension> extensions) override;

  const RtpConfig& rtp_config() const override { return rtp(); }

  webrtc::VideoReceiveStream::Stats GetStats() const override;

  // SetBaseMinimumPlayoutDelayMs and GetBaseMinimumPlayoutDelayMs are called
  // from webrtc/api level and requested by user code. For e.g. blink/js layer
  // in Chromium.
  bool SetBaseMinimumPlayoutDelayMs(int delay_ms) override;
  int GetBaseMinimumPlayoutDelayMs() const override;

  void SetFrameDecryptor(
      rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) override;
  void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) override;

  // Implements rtc::VideoSinkInterface<VideoFrame>.
  void OnFrame(const VideoFrame& video_frame) override;

  // Implements NackSender.
  // For this particular override of the interface,
  // only (buffering_allowed == true) is acceptable.
  void SendNack(const std::vector<uint16_t>& sequence_numbers,
                bool buffering_allowed) override;

  // Implements RtpVideoStreamReceiver2::OnCompleteFrameCallback.
  void OnCompleteFrame(std::unique_ptr<EncodedFrame> frame) override;

  // Implements CallStatsObserver::OnRttUpdate
  void OnRttUpdate(int64_t avg_rtt_ms, int64_t max_rtt_ms) override;

  // Implements Syncable.
  uint32_t id() const override;
  absl::optional<Syncable::Info> GetInfo() const override;
  bool GetPlayoutRtpTimestamp(uint32_t* rtp_timestamp,
                              int64_t* time_ms) const override;
  void SetEstimatedPlayoutNtpTimestampMs(int64_t ntp_timestamp_ms,
                                         int64_t time_ms) override;

  // SetMinimumPlayoutDelay is only called by A/V sync.
  bool SetMinimumPlayoutDelay(int delay_ms) override;

  std::vector<webrtc::RtpSource> GetSources() const override;

  RecordingState SetAndGetRecordingState(RecordingState state,
                                         bool generate_key_frame) override;
  void GenerateKeyFrame() override;

 private:
  void OnEncodedFrame(std::unique_ptr<EncodedFrame> frame) override;
  void OnDecodableFrameTimeout(TimeDelta wait_time) override;
  void CreateAndRegisterExternalDecoder(const Decoder& decoder);
  int64_t GetMaxWaitMs() const RTC_RUN_ON(decode_queue_);
  void HandleEncodedFrame(std::unique_ptr<EncodedFrame> frame)
      RTC_RUN_ON(decode_queue_);
  void HandleFrameBufferTimeout(int64_t now_ms, int64_t wait_ms)
      RTC_RUN_ON(packet_sequence_checker_);
  void UpdatePlayoutDelays() const
      RTC_EXCLUSIVE_LOCKS_REQUIRED(worker_sequence_checker_);
  void RequestKeyFrame(int64_t timestamp_ms)
      RTC_RUN_ON(packet_sequence_checker_);
  void HandleKeyFrameGeneration(bool received_frame_is_keyframe,
                                int64_t now_ms,
                                bool always_request_key_frame,
                                bool keyframe_request_is_due)
      RTC_RUN_ON(packet_sequence_checker_);
  bool IsReceivingKeyFrame(int64_t timestamp_ms) const
      RTC_RUN_ON(packet_sequence_checker_);
  int DecodeAndMaybeDispatchEncodedFrame(std::unique_ptr<EncodedFrame> frame)
      RTC_RUN_ON(decode_queue_);

  void UpdateHistograms();

  RTC_NO_UNIQUE_ADDRESS SequenceChecker worker_sequence_checker_;
  // TODO(bugs.webrtc.org/11993): This checker conceptually represents
  // operations that belong to the network thread. The Call class is currently
  // moving towards handling network packets on the network thread and while
  // that work is ongoing, this checker may in practice represent the worker
  // thread, but still serves as a mechanism of grouping together concepts
  // that belong to the network thread. Once the packets are fully delivered
  // on the network thread, this comment will be deleted.
  RTC_NO_UNIQUE_ADDRESS SequenceChecker packet_sequence_checker_;

  TaskQueueFactory* const task_queue_factory_;

  TransportAdapter transport_adapter_;
  const VideoReceiveStream::Config config_;
  const int num_cpu_cores_;
  Call* const call_;
  Clock* const clock_;

  CallStats* const call_stats_;

  bool decoder_running_ RTC_GUARDED_BY(worker_sequence_checker_) = false;
  bool decoder_stopped_ RTC_GUARDED_BY(decode_queue_) = true;

  SourceTracker source_tracker_;
  ReceiveStatisticsProxy stats_proxy_;
  // Shared by media and rtx stream receivers, since the latter has no RtpRtcp
  // module of its own.
  const std::unique_ptr<ReceiveStatistics> rtp_receive_statistics_;

  std::unique_ptr<VCMTiming> timing_;  // Jitter buffer experiment.
  VideoReceiver2 video_receiver_;
  std::unique_ptr<rtc::VideoSinkInterface<VideoFrame>> incoming_video_stream_;
  RtpVideoStreamReceiver2 rtp_video_stream_receiver_;
  std::unique_ptr<VideoStreamDecoder> video_stream_decoder_;
  RtpStreamsSynchronizer rtp_stream_sync_;

  // TODO(nisse, philipel): Creation and ownership of video encoders should be
  // moved to the new VideoStreamDecoder.
  std::vector<std::unique_ptr<VideoDecoder>> video_decoders_;

  std::unique_ptr<FrameBufferProxy> frame_buffer_;

  std::unique_ptr<RtpStreamReceiverInterface> media_receiver_
      RTC_GUARDED_BY(packet_sequence_checker_);
  std::unique_ptr<RtxReceiveStream> rtx_receive_stream_
      RTC_GUARDED_BY(packet_sequence_checker_);
  std::unique_ptr<RtpStreamReceiverInterface> rtx_receiver_
      RTC_GUARDED_BY(packet_sequence_checker_);

  // Whenever we are in an undecodable state (stream has just started or due to
  // a decoding error) we require a keyframe to restart the stream.
  bool keyframe_required_ RTC_GUARDED_BY(decode_queue_) = true;

  // If we have successfully decoded any frame.
  bool frame_decoded_ RTC_GUARDED_BY(decode_queue_) = false;

  int64_t last_keyframe_request_ms_ RTC_GUARDED_BY(decode_queue_) = 0;
  int64_t last_complete_frame_time_ms_
      RTC_GUARDED_BY(worker_sequence_checker_) = 0;

  // Keyframe request intervals are configurable through field trials.
  const int max_wait_for_keyframe_ms_;
  const int max_wait_for_frame_ms_;

  // All of them tries to change current min_playout_delay on `timing_` but
  // source of the change request is different in each case. Among them the
  // biggest delay is used. -1 means use default value from the `timing_`.
  //
  // Minimum delay as decided by the RTP playout delay extension.
  int frame_minimum_playout_delay_ms_ RTC_GUARDED_BY(worker_sequence_checker_) =
      -1;
  // Minimum delay as decided by the setLatency function in "webrtc/api".
  int base_minimum_playout_delay_ms_ RTC_GUARDED_BY(worker_sequence_checker_) =
      -1;
  // Minimum delay as decided by the A/V synchronization feature.
  int syncable_minimum_playout_delay_ms_
      RTC_GUARDED_BY(worker_sequence_checker_) = -1;

  // Maximum delay as decided by the RTP playout delay extension.
  int frame_maximum_playout_delay_ms_ RTC_GUARDED_BY(worker_sequence_checker_) =
      -1;

  // Function that is triggered with encoded frames, if not empty.
  std::function<void(const RecordableEncodedFrame&)>
      encoded_frame_buffer_function_ RTC_GUARDED_BY(decode_queue_);
  // Set to true while we're requesting keyframes but not yet received one.
  bool keyframe_generation_requested_ RTC_GUARDED_BY(packet_sequence_checker_) =
      false;
  // Lock to avoid unnecessary per-frame idle wakeups in the code.
  webrtc::Mutex pending_resolution_mutex_;
  // Signal from decode queue to OnFrame callback to fill pending_resolution_.
  // absl::nullopt - no resolution needed. 0x0 - next OnFrame to fill with
  // received resolution. Not 0x0 - OnFrame has filled a resolution.
  absl::optional<RecordableEncodedFrame::EncodedResolution> pending_resolution_
      RTC_GUARDED_BY(pending_resolution_mutex_);
  // Buffered encoded frames held while waiting for decoded resolution.
  std::vector<std::unique_ptr<EncodedFrame>> buffered_encoded_frames_
      RTC_GUARDED_BY(decode_queue_);

  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter `enabled`
  // determines if the low-latency renderer algorithm should be used for the
  // case min playout delay=0 and max playout delay>0.
  FieldTrialParameter<bool> low_latency_renderer_enabled_;
  // Set by the field trial WebRTC-LowLatencyRenderer. The parameter
  // `include_predecode_buffer` determines if the predecode buffer should be
  // taken into account when calculating maximum number of frames in composition
  // queue.
  FieldTrialParameter<bool> low_latency_renderer_include_predecode_buffer_;

  // Set by the field trial WebRTC-PreStreamDecoders. The parameter `max`
  // determines the maximum number of decoders that are created up front before
  // any video frame has been received.
  FieldTrialParameter<int> maximum_pre_stream_decoders_;

  DecodeSynchronizer* decode_sync_;

  // Defined last so they are destroyed before all other members.
  rtc::TaskQueue decode_queue_;

  // Used to signal destruction to potentially pending tasks.
  ScopedTaskSafety task_safety_;
};
}  // namespace internal
}  // namespace webrtc

#endif  // VIDEO_VIDEO_RECEIVE_STREAM2_H_
