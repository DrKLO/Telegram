/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef VIDEO_VIDEO_SEND_STREAM_H_
#define VIDEO_VIDEO_SEND_STREAM_H_

#include <map>
#include <memory>
#include <vector>

#include "api/fec_controller.h"
#include "api/sequence_checker.h"
#include "api/video/video_stream_encoder_interface.h"
#include "call/bitrate_allocator.h"
#include "call/video_receive_stream.h"
#include "call/video_send_stream.h"
#include "rtc_base/event.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_queue.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "video/encoder_rtcp_feedback.h"
#include "video/send_delay_stats.h"
#include "video/send_statistics_proxy.h"
#include "video/video_send_stream_impl.h"

namespace webrtc {
namespace test {
class VideoSendStreamPeer;
}  // namespace test

class CallStats;
class IvfFileWriter;
class RateLimiter;
class RtpRtcp;
class RtpTransportControllerSendInterface;
class RtcEventLog;

namespace internal {

class VideoSendStreamImpl;

// VideoSendStream implements webrtc::VideoSendStream.
// Internally, it delegates all public methods to VideoSendStreamImpl and / or
// VideoStreamEncoder.
class VideoSendStream : public webrtc::VideoSendStream {
 public:
  using RtpStateMap = std::map<uint32_t, RtpState>;
  using RtpPayloadStateMap = std::map<uint32_t, RtpPayloadState>;

  VideoSendStream(
      Clock* clock,
      int num_cpu_cores,
      TaskQueueFactory* task_queue_factory,
      TaskQueueBase* network_queue,
      RtcpRttStats* call_stats,
      RtpTransportControllerSendInterface* transport,
      BitrateAllocatorInterface* bitrate_allocator,
      SendDelayStats* send_delay_stats,
      RtcEventLog* event_log,
      VideoSendStream::Config config,
      VideoEncoderConfig encoder_config,
      const std::map<uint32_t, RtpState>& suspended_ssrcs,
      const std::map<uint32_t, RtpPayloadState>& suspended_payload_states,
      std::unique_ptr<FecController> fec_controller);

  ~VideoSendStream() override;

  void DeliverRtcp(const uint8_t* packet, size_t length);

  // webrtc::VideoSendStream implementation.
  void UpdateActiveSimulcastLayers(
      const std::vector<bool> active_layers) override;
  void Start() override;
  void Stop() override;
  bool started() override;

  void AddAdaptationResource(rtc::scoped_refptr<Resource> resource) override;
  std::vector<rtc::scoped_refptr<Resource>> GetAdaptationResources() override;

  void SetSource(rtc::VideoSourceInterface<webrtc::VideoFrame>* source,
                 const DegradationPreference& degradation_preference) override;

  void ReconfigureVideoEncoder(VideoEncoderConfig) override;
  Stats GetStats() override;

  void StopPermanentlyAndGetRtpStates(RtpStateMap* rtp_state_map,
                                      RtpPayloadStateMap* payload_state_map);

 private:
  friend class test::VideoSendStreamPeer;

  absl::optional<float> GetPacingFactorOverride() const;

  RTC_NO_UNIQUE_ADDRESS SequenceChecker thread_checker_;
  rtc::TaskQueue* const rtp_transport_queue_;
  RtpTransportControllerSendInterface* const transport_;
  rtc::Event thread_sync_event_;
  rtc::scoped_refptr<PendingTaskSafetyFlag> transport_queue_safety_ =
      PendingTaskSafetyFlag::CreateDetached();

  SendStatisticsProxy stats_proxy_;
  const VideoSendStream::Config config_;
  const VideoEncoderConfig::ContentType content_type_;
  std::unique_ptr<VideoStreamEncoderInterface> video_stream_encoder_;
  EncoderRtcpFeedback encoder_feedback_;
  RtpVideoSenderInterface* const rtp_video_sender_;
  VideoSendStreamImpl send_stream_;
  bool running_ RTC_GUARDED_BY(thread_checker_) = false;
};

}  // namespace internal
}  // namespace webrtc

#endif  // VIDEO_VIDEO_SEND_STREAM_H_
