/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef VIDEO_VIDEO_SEND_STREAM_IMPL_H_
#define VIDEO_VIDEO_SEND_STREAM_IMPL_H_

#include <stddef.h>
#include <stdint.h>

#include <atomic>
#include <map>
#include <memory>
#include <vector>

#include "absl/types/optional.h"
#include "api/fec_controller.h"
#include "api/rtc_event_log/rtc_event_log.h"
#include "api/video/encoded_image.h"
#include "api/video/video_bitrate_allocation.h"
#include "api/video/video_bitrate_allocator.h"
#include "api/video/video_stream_encoder_interface.h"
#include "api/video_codecs/video_encoder.h"
#include "api/video_codecs/video_encoder_config.h"
#include "call/bitrate_allocator.h"
#include "call/rtp_config.h"
#include "call/rtp_transport_controller_send_interface.h"
#include "call/rtp_video_sender_interface.h"
#include "modules/include/module_common_types.h"
#include "modules/rtp_rtcp/include/rtp_rtcp_defines.h"
#include "modules/utility/include/process_thread.h"
#include "modules/video_coding/include/video_codec_interface.h"
#include "rtc_base/experiments/field_trial_parser.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/weak_ptr.h"
#include "video/encoder_rtcp_feedback.h"
#include "video/send_delay_stats.h"
#include "video/send_statistics_proxy.h"
#include "video/video_send_stream.h"

namespace webrtc {
namespace internal {

// Pacing buffer config; overridden by ALR config if provided.
struct PacingConfig {
  PacingConfig();
  PacingConfig(const PacingConfig&);
  PacingConfig& operator=(const PacingConfig&) = default;
  ~PacingConfig();
  FieldTrialParameter<double> pacing_factor;
  FieldTrialParameter<TimeDelta> max_pacing_delay;
};

// VideoSendStreamImpl implements internal::VideoSendStream.
// It is created and destroyed on |worker_queue|. The intent is to decrease the
// need for locking and to ensure methods are called in sequence.
// Public methods except |DeliverRtcp| must be called on |worker_queue|.
// DeliverRtcp is called on the libjingle worker thread or a network thread.
// An encoder may deliver frames through the EncodedImageCallback on an
// arbitrary thread.
class VideoSendStreamImpl : public webrtc::BitrateAllocatorObserver,
                            public VideoStreamEncoderInterface::EncoderSink {
 public:
  VideoSendStreamImpl(
      Clock* clock,
      SendStatisticsProxy* stats_proxy,
      rtc::TaskQueue* worker_queue,
      RtcpRttStats* call_stats,
      RtpTransportControllerSendInterface* transport,
      BitrateAllocatorInterface* bitrate_allocator,
      SendDelayStats* send_delay_stats,
      VideoStreamEncoderInterface* video_stream_encoder,
      RtcEventLog* event_log,
      const VideoSendStream::Config* config,
      int initial_encoder_max_bitrate,
      double initial_encoder_bitrate_priority,
      std::map<uint32_t, RtpState> suspended_ssrcs,
      std::map<uint32_t, RtpPayloadState> suspended_payload_states,
      VideoEncoderConfig::ContentType content_type,
      std::unique_ptr<FecController> fec_controller);
  ~VideoSendStreamImpl() override;

  // RegisterProcessThread register |module_process_thread| with those objects
  // that use it. Registration has to happen on the thread were
  // |module_process_thread| was created (libjingle's worker thread).
  // TODO(perkj): Replace the use of |module_process_thread| with a TaskQueue,
  // maybe |worker_queue|.
  void RegisterProcessThread(ProcessThread* module_process_thread);
  void DeRegisterProcessThread();

  void DeliverRtcp(const uint8_t* packet, size_t length);
  void UpdateActiveSimulcastLayers(const std::vector<bool> active_layers);
  void Start();
  void Stop();

  // TODO(holmer): Move these to RtpTransportControllerSend.
  std::map<uint32_t, RtpState> GetRtpStates() const;

  std::map<uint32_t, RtpPayloadState> GetRtpPayloadStates() const;

  absl::optional<float> configured_pacing_factor_;

 private:
  // Implements BitrateAllocatorObserver.
  uint32_t OnBitrateUpdated(BitrateAllocationUpdate update) override;

  // Implements VideoStreamEncoderInterface::EncoderSink
  void OnEncoderConfigurationChanged(
      std::vector<VideoStream> streams,
      bool is_svc,
      VideoEncoderConfig::ContentType content_type,
      int min_transmit_bitrate_bps) override;

  void OnBitrateAllocationUpdated(
      const VideoBitrateAllocation& allocation) override;
  void OnVideoLayersAllocationUpdated(
      VideoLayersAllocation allocation) override;

  // Implements EncodedImageCallback. The implementation routes encoded frames
  // to the |payload_router_| and |config.pre_encode_callback| if set.
  // Called on an arbitrary encoder callback thread.
  EncodedImageCallback::Result OnEncodedImage(
      const EncodedImage& encoded_image,
      const CodecSpecificInfo* codec_specific_info) override;

  // Implements EncodedImageCallback.
  void OnDroppedFrame(EncodedImageCallback::DropReason reason) override;

  // Starts monitoring and sends a keyframe.
  void StartupVideoSendStream();
  // Removes the bitrate observer, stops monitoring and notifies the video
  // encoder of the bitrate update.
  void StopVideoSendStream() RTC_RUN_ON(worker_queue_);

  void ConfigureProtection();
  void ConfigureSsrcs();
  void SignalEncoderTimedOut();
  void SignalEncoderActive();
  MediaStreamAllocationConfig GetAllocationConfig() const
      RTC_RUN_ON(worker_queue_);
  Clock* const clock_;
  const bool has_alr_probing_;
  const PacingConfig pacing_config_;

  SendStatisticsProxy* const stats_proxy_;
  const VideoSendStream::Config* const config_;

  rtc::TaskQueue* const worker_queue_;

  RepeatingTaskHandle check_encoder_activity_task_
      RTC_GUARDED_BY(worker_queue_);

  std::atomic_bool activity_;
  bool timed_out_ RTC_GUARDED_BY(worker_queue_);

  RtpTransportControllerSendInterface* const transport_;
  BitrateAllocatorInterface* const bitrate_allocator_;

  Mutex ivf_writers_mutex_;

  bool disable_padding_;
  int max_padding_bitrate_;
  int encoder_min_bitrate_bps_;
  uint32_t encoder_max_bitrate_bps_;
  uint32_t encoder_target_rate_bps_;
  double encoder_bitrate_priority_;
  bool has_packet_feedback_;

  VideoStreamEncoderInterface* const video_stream_encoder_;
  EncoderRtcpFeedback encoder_feedback_;

  RtcpBandwidthObserver* const bandwidth_observer_;
  RtpVideoSenderInterface* const rtp_video_sender_;

  // |weak_ptr_| to our self. This is used since we can not call
  // |weak_ptr_factory_.GetWeakPtr| from multiple sequences but it is ok to copy
  // an existing WeakPtr.
  rtc::WeakPtr<VideoSendStreamImpl> weak_ptr_;
  // |weak_ptr_factory_| must be declared last to make sure all WeakPtr's are
  // invalidated before any other members are destroyed.
  rtc::WeakPtrFactory<VideoSendStreamImpl> weak_ptr_factory_;

  // Context for the most recent and last sent video bitrate allocation. Used to
  // throttle sending of similar bitrate allocations.
  struct VbaSendContext {
    VideoBitrateAllocation last_sent_allocation;
    absl::optional<VideoBitrateAllocation> throttled_allocation;
    int64_t last_send_time_ms;
  };
  absl::optional<VbaSendContext> video_bitrate_allocation_context_
      RTC_GUARDED_BY(worker_queue_);
};
}  // namespace internal
}  // namespace webrtc
#endif  // VIDEO_VIDEO_SEND_STREAM_IMPL_H_
