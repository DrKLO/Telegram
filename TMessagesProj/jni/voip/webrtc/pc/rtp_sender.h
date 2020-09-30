/*
 *  Copyright 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// This file contains classes that implement RtpSenderInterface.
// An RtpSender associates a MediaStreamTrackInterface with an underlying
// transport (provided by AudioProviderInterface/VideoProviderInterface)

#ifndef PC_RTP_SENDER_H_
#define PC_RTP_SENDER_H_

#include <memory>
#include <string>
#include <vector>

#include "api/media_stream_interface.h"
#include "api/rtp_sender_interface.h"
#include "media/base/audio_source.h"
#include "media/base/media_channel.h"
#include "pc/dtmf_sender.h"
#include "rtc_base/synchronization/mutex.h"

namespace webrtc {

class StatsCollector;

bool UnimplementedRtpParameterHasValue(const RtpParameters& parameters);

// Internal interface used by PeerConnection.
class RtpSenderInternal : public RtpSenderInterface {
 public:
  // Sets the underlying MediaEngine channel associated with this RtpSender.
  // A VoiceMediaChannel should be used for audio RtpSenders and
  // a VideoMediaChannel should be used for video RtpSenders.
  // Must call SetMediaChannel(nullptr) before the media channel is destroyed.
  virtual void SetMediaChannel(cricket::MediaChannel* media_channel) = 0;

  // Used to set the SSRC of the sender, once a local description has been set.
  // If |ssrc| is 0, this indiates that the sender should disconnect from the
  // underlying transport (this occurs if the sender isn't seen in a local
  // description).
  virtual void SetSsrc(uint32_t ssrc) = 0;

  virtual void set_stream_ids(const std::vector<std::string>& stream_ids) = 0;
  virtual void set_init_send_encodings(
      const std::vector<RtpEncodingParameters>& init_send_encodings) = 0;
  virtual void set_transport(
      rtc::scoped_refptr<DtlsTransportInterface> dtls_transport) = 0;

  virtual void Stop() = 0;

  // |GetParameters| and |SetParameters| operate with a transactional model.
  // Allow access to get/set parameters without invalidating transaction id.
  virtual RtpParameters GetParametersInternal() const = 0;
  virtual RTCError SetParametersInternal(const RtpParameters& parameters) = 0;

  // Returns an ID that changes every time SetTrack() is called, but
  // otherwise remains constant. Used to generate IDs for stats.
  // The special value zero means that no track is attached.
  virtual int AttachmentId() const = 0;

  // Disables the layers identified by the specified RIDs.
  // If the specified list is empty, this is a no-op.
  virtual RTCError DisableEncodingLayers(
      const std::vector<std::string>& rid) = 0;

  virtual void SetTransceiverAsStopped() = 0;
};

// Shared implementation for RtpSenderInternal interface.
class RtpSenderBase : public RtpSenderInternal, public ObserverInterface {
 public:
  class SetStreamsObserver {
   public:
    virtual ~SetStreamsObserver() = default;
    virtual void OnSetStreams() = 0;
  };

  // Sets the underlying MediaEngine channel associated with this RtpSender.
  // A VoiceMediaChannel should be used for audio RtpSenders and
  // a VideoMediaChannel should be used for video RtpSenders.
  // Must call SetMediaChannel(nullptr) before the media channel is destroyed.
  void SetMediaChannel(cricket::MediaChannel* media_channel) override;

  bool SetTrack(MediaStreamTrackInterface* track) override;
  rtc::scoped_refptr<MediaStreamTrackInterface> track() const override {
    return track_;
  }

  RtpParameters GetParameters() const override;
  RTCError SetParameters(const RtpParameters& parameters) override;

  // |GetParameters| and |SetParameters| operate with a transactional model.
  // Allow access to get/set parameters without invalidating transaction id.
  RtpParameters GetParametersInternal() const override;
  RTCError SetParametersInternal(const RtpParameters& parameters) override;

  // Used to set the SSRC of the sender, once a local description has been set.
  // If |ssrc| is 0, this indiates that the sender should disconnect from the
  // underlying transport (this occurs if the sender isn't seen in a local
  // description).
  void SetSsrc(uint32_t ssrc) override;
  uint32_t ssrc() const override { return ssrc_; }

  std::vector<std::string> stream_ids() const override { return stream_ids_; }
  void set_stream_ids(const std::vector<std::string>& stream_ids) override {
    stream_ids_ = stream_ids;
  }
  void SetStreams(const std::vector<std::string>& stream_ids) override;

  std::string id() const override { return id_; }

  void set_init_send_encodings(
      const std::vector<RtpEncodingParameters>& init_send_encodings) override {
    init_parameters_.encodings = init_send_encodings;
  }
  std::vector<RtpEncodingParameters> init_send_encodings() const override {
    return init_parameters_.encodings;
  }

  void set_transport(
      rtc::scoped_refptr<DtlsTransportInterface> dtls_transport) override {
    dtls_transport_ = dtls_transport;
  }
  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport() const override {
    return dtls_transport_;
  }

  void SetFrameEncryptor(
      rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor) override;

  rtc::scoped_refptr<FrameEncryptorInterface> GetFrameEncryptor()
      const override {
    return frame_encryptor_;
  }

  void Stop() override;

  // Returns an ID that changes every time SetTrack() is called, but
  // otherwise remains constant. Used to generate IDs for stats.
  // The special value zero means that no track is attached.
  int AttachmentId() const override { return attachment_id_; }

  // Disables the layers identified by the specified RIDs.
  // If the specified list is empty, this is a no-op.
  RTCError DisableEncodingLayers(const std::vector<std::string>& rid) override;

  void SetEncoderToPacketizerFrameTransformer(
      rtc::scoped_refptr<FrameTransformerInterface> frame_transformer) override;

  void SetTransceiverAsStopped() override { is_transceiver_stopped_ = true; }

 protected:
  // If |set_streams_observer| is not null, it is invoked when SetStreams()
  // is called. |set_streams_observer| is not owned by this object. If not
  // null, it must be valid at least until this sender becomes stopped.
  RtpSenderBase(rtc::Thread* worker_thread,
                const std::string& id,
                SetStreamsObserver* set_streams_observer);
  // TODO(nisse): Since SSRC == 0 is technically valid, figure out
  // some other way to test if we have a valid SSRC.
  bool can_send_track() const { return track_ && ssrc_; }

  virtual std::string track_kind() const = 0;

  // Enable sending on the media channel.
  virtual void SetSend() = 0;
  // Disable sending on the media channel.
  virtual void ClearSend() = 0;

  // Template method pattern to allow subclasses to add custom behavior for
  // when tracks are attached, detached, and for adding tracks to statistics.
  virtual void AttachTrack() {}
  virtual void DetachTrack() {}
  virtual void AddTrackToStats() {}
  virtual void RemoveTrackFromStats() {}

  rtc::Thread* worker_thread_;
  uint32_t ssrc_ = 0;
  bool stopped_ = false;
  bool is_transceiver_stopped_ = false;
  int attachment_id_ = 0;
  const std::string id_;

  std::vector<std::string> stream_ids_;
  RtpParameters init_parameters_;

  cricket::MediaChannel* media_channel_ = nullptr;
  rtc::scoped_refptr<MediaStreamTrackInterface> track_;

  rtc::scoped_refptr<DtlsTransportInterface> dtls_transport_;
  rtc::scoped_refptr<FrameEncryptorInterface> frame_encryptor_;
  // |last_transaction_id_| is used to verify that |SetParameters| is receiving
  // the parameters object that was last returned from |GetParameters|.
  // As such, it is used for internal verification and is not observable by the
  // the client. It is marked as mutable to enable |GetParameters| to be a
  // const method.
  mutable absl::optional<std::string> last_transaction_id_;
  std::vector<std::string> disabled_rids_;

  SetStreamsObserver* set_streams_observer_ = nullptr;

  rtc::scoped_refptr<FrameTransformerInterface> frame_transformer_;
};

// LocalAudioSinkAdapter receives data callback as a sink to the local
// AudioTrack, and passes the data to the sink of AudioSource.
class LocalAudioSinkAdapter : public AudioTrackSinkInterface,
                              public cricket::AudioSource {
 public:
  LocalAudioSinkAdapter();
  virtual ~LocalAudioSinkAdapter();

 private:
  // AudioSinkInterface implementation.
  void OnData(const void* audio_data,
              int bits_per_sample,
              int sample_rate,
              size_t number_of_channels,
              size_t number_of_frames,
              absl::optional<int64_t> absolute_capture_timestamp_ms) override;

  // AudioSinkInterface implementation.
  void OnData(const void* audio_data,
              int bits_per_sample,
              int sample_rate,
              size_t number_of_channels,
              size_t number_of_frames) override {
    OnData(audio_data, bits_per_sample, sample_rate, number_of_channels,
           number_of_frames,
           /*absolute_capture_timestamp_ms=*/absl::nullopt);
  }

  // cricket::AudioSource implementation.
  void SetSink(cricket::AudioSource::Sink* sink) override;

  cricket::AudioSource::Sink* sink_;
  // Critical section protecting |sink_|.
  Mutex lock_;
};

class AudioRtpSender : public DtmfProviderInterface, public RtpSenderBase {
 public:
  // Construct an RtpSender for audio with the given sender ID.
  // The sender is initialized with no track to send and no associated streams.
  // StatsCollector provided so that Add/RemoveLocalAudioTrack can be called
  // at the appropriate times.
  // If |set_streams_observer| is not null, it is invoked when SetStreams()
  // is called. |set_streams_observer| is not owned by this object. If not
  // null, it must be valid at least until this sender becomes stopped.
  static rtc::scoped_refptr<AudioRtpSender> Create(
      rtc::Thread* worker_thread,
      const std::string& id,
      StatsCollector* stats,
      SetStreamsObserver* set_streams_observer);
  virtual ~AudioRtpSender();

  // DtmfSenderProvider implementation.
  bool CanInsertDtmf() override;
  bool InsertDtmf(int code, int duration) override;
  sigslot::signal0<>* GetOnDestroyedSignal() override;

  // ObserverInterface implementation.
  void OnChanged() override;

  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_AUDIO;
  }
  std::string track_kind() const override {
    return MediaStreamTrackInterface::kAudioKind;
  }

  rtc::scoped_refptr<DtmfSenderInterface> GetDtmfSender() const override;

 protected:
  AudioRtpSender(rtc::Thread* worker_thread,
                 const std::string& id,
                 StatsCollector* stats,
                 SetStreamsObserver* set_streams_observer);

  void SetSend() override;
  void ClearSend() override;

  // Hooks to allow custom logic when tracks are attached and detached.
  void AttachTrack() override;
  void DetachTrack() override;
  void AddTrackToStats() override;
  void RemoveTrackFromStats() override;

 private:
  cricket::VoiceMediaChannel* voice_media_channel() {
    return static_cast<cricket::VoiceMediaChannel*>(media_channel_);
  }
  rtc::scoped_refptr<AudioTrackInterface> audio_track() const {
    return rtc::scoped_refptr<AudioTrackInterface>(
        static_cast<AudioTrackInterface*>(track_.get()));
  }
  sigslot::signal0<> SignalDestroyed;

  StatsCollector* stats_ = nullptr;
  rtc::scoped_refptr<DtmfSenderInterface> dtmf_sender_proxy_;
  bool cached_track_enabled_ = false;

  // Used to pass the data callback from the |track_| to the other end of
  // cricket::AudioSource.
  std::unique_ptr<LocalAudioSinkAdapter> sink_adapter_;
};

class VideoRtpSender : public RtpSenderBase {
 public:
  // Construct an RtpSender for video with the given sender ID.
  // The sender is initialized with no track to send and no associated streams.
  // If |set_streams_observer| is not null, it is invoked when SetStreams()
  // is called. |set_streams_observer| is not owned by this object. If not
  // null, it must be valid at least until this sender becomes stopped.
  static rtc::scoped_refptr<VideoRtpSender> Create(
      rtc::Thread* worker_thread,
      const std::string& id,
      SetStreamsObserver* set_streams_observer);
  virtual ~VideoRtpSender();

  // ObserverInterface implementation
  void OnChanged() override;

  cricket::MediaType media_type() const override {
    return cricket::MEDIA_TYPE_VIDEO;
  }
  std::string track_kind() const override {
    return MediaStreamTrackInterface::kVideoKind;
  }

  rtc::scoped_refptr<DtmfSenderInterface> GetDtmfSender() const override;

 protected:
  VideoRtpSender(rtc::Thread* worker_thread,
                 const std::string& id,
                 SetStreamsObserver* set_streams_observer);

  void SetSend() override;
  void ClearSend() override;

  // Hook to allow custom logic when tracks are attached.
  void AttachTrack() override;

 private:
  cricket::VideoMediaChannel* video_media_channel() {
    return static_cast<cricket::VideoMediaChannel*>(media_channel_);
  }
  rtc::scoped_refptr<VideoTrackInterface> video_track() const {
    return rtc::scoped_refptr<VideoTrackInterface>(
        static_cast<VideoTrackInterface*>(track_.get()));
  }

  VideoTrackInterface::ContentHint cached_track_content_hint_ =
      VideoTrackInterface::ContentHint::kNone;
};

}  // namespace webrtc

#endif  // PC_RTP_SENDER_H_
