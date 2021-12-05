/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_AUDIO_RTP_RECEIVER_H_
#define PC_AUDIO_RTP_RECEIVER_H_

#include <stdint.h>

#include <string>
#include <vector>

#include "absl/types/optional.h"
#include "api/crypto/frame_decryptor_interface.h"
#include "api/dtls_transport_interface.h"
#include "api/frame_transformer_interface.h"
#include "api/media_stream_interface.h"
#include "api/media_stream_track_proxy.h"
#include "api/media_types.h"
#include "api/rtp_parameters.h"
#include "api/rtp_receiver_interface.h"
#include "api/scoped_refptr.h"
#include "api/sequence_checker.h"
#include "api/transport/rtp/rtp_source.h"
#include "media/base/media_channel.h"
#include "pc/audio_track.h"
#include "pc/jitter_buffer_delay.h"
#include "pc/remote_audio_source.h"
#include "pc/rtp_receiver.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/system/no_unique_address.h"
#include "rtc_base/task_utils/pending_task_safety_flag.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"

namespace webrtc {

class AudioRtpReceiver : public ObserverInterface,
                         public AudioSourceInterface::AudioObserver,
                         public RtpReceiverInternal {
 public:
  AudioRtpReceiver(rtc::Thread* worker_thread,
                   std::string receiver_id,
                   std::vector<std::string> stream_ids,
                   bool is_unified_plan);
  // TODO(https://crbug.com/webrtc/9480): Remove this when streams() is removed.
  AudioRtpReceiver(
      rtc::Thread* worker_thread,
      const std::string& receiver_id,
      const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams,
      bool is_unified_plan);
  virtual ~AudioRtpReceiver();

  // ObserverInterface implementation
  void OnChanged() override;

  // AudioSourceInterface::AudioObserver implementation
  void OnSetVolume(double volume) override;

  rtc::scoped_refptr<AudioTrackInterface> audio_track() const { return track_; }

  // RtpReceiverInterface implementation
  rtc::scoped_refptr<MediaStreamTrackInterface> track() const override {
    return track_;
  }
  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const override;
  std::vector<std::string> stream_ids() const override;
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> streams()
      const override;

  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_AUDIO;
  }

  std::string id() const override { return id_; }

  RtpParameters GetParameters() const override;

  void SetFrameDecryptor(
      rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor) override;

  rtc::scoped_refptr<FrameDecryptorInterface> GetFrameDecryptor()
      const override;

  // RtpReceiverInternal implementation.
  void Stop() override;
  void StopAndEndTrack() override;
  void SetupMediaChannel(uint32_t ssrc) override;
  void SetupUnsignaledMediaChannel() override;
  uint32_t ssrc() const override;
  void NotifyFirstPacketReceived() override;
  void set_stream_ids(std::vector<std::string> stream_ids) override;
  void set_transport(
      rtc::scoped_refptr<DtlsTransportInterface> dtls_transport) override;
  void SetStreams(const std::vector<rtc::scoped_refptr<MediaStreamInterface>>&
                      streams) override;
  void SetObserver(RtpReceiverObserverInterface* observer) override;

  void SetJitterBufferMinimumDelay(
      absl::optional<double> delay_seconds) override;

  void SetMediaChannel(cricket::MediaChannel* media_channel) override;

  std::vector<RtpSource> GetSources() const override;
  int AttachmentId() const override { return attachment_id_; }
  void SetDepacketizerToDecoderFrameTransformer(
      rtc::scoped_refptr<webrtc::FrameTransformerInterface> frame_transformer)
      override;

 private:
  void RestartMediaChannel(absl::optional<uint32_t> ssrc);
  void Reconfigure(bool track_enabled, double volume)
      RTC_RUN_ON(worker_thread_);
  void SetOutputVolume_w(double volume) RTC_RUN_ON(worker_thread_);
  void SetMediaChannel_w(cricket::MediaChannel* media_channel)
      RTC_RUN_ON(worker_thread_);

  RTC_NO_UNIQUE_ADDRESS SequenceChecker signaling_thread_checker_;
  rtc::Thread* const worker_thread_;
  const std::string id_;
  const rtc::scoped_refptr<RemoteAudioSource> source_;
  const rtc::scoped_refptr<AudioTrackProxyWithInternal<AudioTrack>> track_;
  cricket::VoiceMediaChannel* media_channel_ RTC_GUARDED_BY(worker_thread_) =
      nullptr;
  absl::optional<uint32_t> ssrc_ RTC_GUARDED_BY(worker_thread_);
  std::vector<rtc::scoped_refptr<MediaStreamInterface>> streams_
      RTC_GUARDED_BY(&signaling_thread_checker_);
  bool cached_track_enabled_ RTC_GUARDED_BY(&signaling_thread_checker_);
  double cached_volume_ RTC_GUARDED_BY(&signaling_thread_checker_) = 1.0;
  bool stopped_ RTC_GUARDED_BY(&signaling_thread_checker_) = true;
  RtpReceiverObserverInterface* observer_
      RTC_GUARDED_BY(&signaling_thread_checker_) = nullptr;
  bool received_first_packet_ RTC_GUARDED_BY(&signaling_thread_checker_) =
      false;
  const int attachment_id_;
  rtc::scoped_refptr<FrameDecryptorInterface> frame_decryptor_
      RTC_GUARDED_BY(worker_thread_);
  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport_
      RTC_GUARDED_BY(&signaling_thread_checker_);
  // Stores and updates the playout delay. Handles caching cases if
  // |SetJitterBufferMinimumDelay| is called before start.
  JitterBufferDelay delay_ RTC_GUARDED_BY(worker_thread_);
  rtc::scoped_refptr<FrameTransformerInterface> frame_transformer_
      RTC_GUARDED_BY(worker_thread_);
  const rtc::scoped_refptr<PendingTaskSafetyFlag> worker_thread_safety_;
};

}  // namespace webrtc

#endif  // PC_AUDIO_RTP_RECEIVER_H_
