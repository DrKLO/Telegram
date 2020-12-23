/*
 *  Copyright 2004 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_CHANNEL_MANAGER_H_
#define PC_CHANNEL_MANAGER_H_

#include <stdint.h>

#include <memory>
#include <string>
#include <vector>

#include "api/audio_options.h"
#include "api/crypto/crypto_options.h"
#include "call/call.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/media_config.h"
#include "media/base/media_engine.h"
#include "pc/channel.h"
#include "pc/rtp_transport_internal.h"
#include "pc/session_description.h"
#include "rtc_base/system/file_wrapper.h"
#include "rtc_base/thread.h"

namespace cricket {

// ChannelManager allows the MediaEngine to run on a separate thread, and takes
// care of marshalling calls between threads. It also creates and keeps track of
// voice and video channels; by doing so, it can temporarily pause all the
// channels when a new audio or video device is chosen. The voice and video
// channels are stored in separate vectors, to easily allow operations on just
// voice or just video channels.
// ChannelManager also allows the application to discover what devices it has
// using device manager.
class ChannelManager final {
 public:
  // Construct a ChannelManager with the specified media engine and data engine.
  ChannelManager(std::unique_ptr<MediaEngineInterface> media_engine,
                 std::unique_ptr<DataEngineInterface> data_engine,
                 rtc::Thread* worker_thread,
                 rtc::Thread* network_thread);
  ~ChannelManager();

  // Accessors for the worker thread, allowing it to be set after construction,
  // but before Init. set_worker_thread will return false if called after Init.
  rtc::Thread* worker_thread() const { return worker_thread_; }
  bool set_worker_thread(rtc::Thread* thread) {
    if (initialized_) {
      return false;
    }
    worker_thread_ = thread;
    return true;
  }
  rtc::Thread* network_thread() const { return network_thread_; }
  bool set_network_thread(rtc::Thread* thread) {
    if (initialized_) {
      return false;
    }
    network_thread_ = thread;
    return true;
  }

  MediaEngineInterface* media_engine() { return media_engine_.get(); }

  // Retrieves the list of supported audio & video codec types.
  // Can be called before starting the media engine.
  void GetSupportedAudioSendCodecs(std::vector<AudioCodec>* codecs) const;
  void GetSupportedAudioReceiveCodecs(std::vector<AudioCodec>* codecs) const;
  void GetSupportedVideoSendCodecs(std::vector<VideoCodec>* codecs) const;
  void GetSupportedVideoReceiveCodecs(std::vector<VideoCodec>* codecs) const;
  void GetSupportedDataCodecs(std::vector<DataCodec>* codecs) const;
  RtpHeaderExtensions GetDefaultEnabledAudioRtpHeaderExtensions() const;
  std::vector<webrtc::RtpHeaderExtensionCapability>
  GetSupportedAudioRtpHeaderExtensions() const;
  RtpHeaderExtensions GetDefaultEnabledVideoRtpHeaderExtensions() const;
  std::vector<webrtc::RtpHeaderExtensionCapability>
  GetSupportedVideoRtpHeaderExtensions() const;

  // Indicates whether the media engine is started.
  bool initialized() const { return initialized_; }
  // Starts up the media engine.
  bool Init();
  // Shuts down the media engine.
  void Terminate();

  // The operations below all occur on the worker thread.
  // ChannelManager retains ownership of the created channels, so clients should
  // call the appropriate Destroy*Channel method when done.

  // Creates a voice channel, to be associated with the specified session.
  VoiceChannel* CreateVoiceChannel(webrtc::Call* call,
                                   const cricket::MediaConfig& media_config,
                                   webrtc::RtpTransportInternal* rtp_transport,
                                   rtc::Thread* signaling_thread,
                                   const std::string& content_name,
                                   bool srtp_required,
                                   const webrtc::CryptoOptions& crypto_options,
                                   rtc::UniqueRandomIdGenerator* ssrc_generator,
                                   const AudioOptions& options);
  // Destroys a voice channel created by CreateVoiceChannel.
  void DestroyVoiceChannel(VoiceChannel* voice_channel);

  // Creates a video channel, synced with the specified voice channel, and
  // associated with the specified session.
  // Version of the above that takes PacketTransportInternal.
  VideoChannel* CreateVideoChannel(
      webrtc::Call* call,
      const cricket::MediaConfig& media_config,
      webrtc::RtpTransportInternal* rtp_transport,
      rtc::Thread* signaling_thread,
      const std::string& content_name,
      bool srtp_required,
      const webrtc::CryptoOptions& crypto_options,
      rtc::UniqueRandomIdGenerator* ssrc_generator,
      const VideoOptions& options,
      webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory);
  // Destroys a video channel created by CreateVideoChannel.
  void DestroyVideoChannel(VideoChannel* video_channel);

  RtpDataChannel* CreateRtpDataChannel(
      const cricket::MediaConfig& media_config,
      webrtc::RtpTransportInternal* rtp_transport,
      rtc::Thread* signaling_thread,
      const std::string& content_name,
      bool srtp_required,
      const webrtc::CryptoOptions& crypto_options,
      rtc::UniqueRandomIdGenerator* ssrc_generator);
  // Destroys a data channel created by CreateRtpDataChannel.
  void DestroyRtpDataChannel(RtpDataChannel* data_channel);

  // Indicates whether any channels exist.
  bool has_channels() const {
    return (!voice_channels_.empty() || !video_channels_.empty() ||
            !data_channels_.empty());
  }

  // RTX will be enabled/disabled in engines that support it. The supporting
  // engines will start offering an RTX codec. Must be called before Init().
  bool SetVideoRtxEnabled(bool enable);

  // Starts/stops the local microphone and enables polling of the input level.
  bool capturing() const { return capturing_; }

  // The operations below occur on the main thread.

  // Starts AEC dump using existing file, with a specified maximum file size in
  // bytes. When the limit is reached, logging will stop and the file will be
  // closed. If max_size_bytes is set to <= 0, no limit will be used.
  bool StartAecDump(webrtc::FileWrapper file, int64_t max_size_bytes);

  // Stops recording AEC dump.
  void StopAecDump();

 private:
  std::unique_ptr<MediaEngineInterface> media_engine_;  // Nullable.
  std::unique_ptr<DataEngineInterface> data_engine_;    // Non-null.
  bool initialized_ = false;
  rtc::Thread* main_thread_;
  rtc::Thread* worker_thread_;
  rtc::Thread* network_thread_;

  // Vector contents are non-null.
  std::vector<std::unique_ptr<VoiceChannel>> voice_channels_;
  std::vector<std::unique_ptr<VideoChannel>> video_channels_;
  std::vector<std::unique_ptr<RtpDataChannel>> data_channels_;

  bool enable_rtx_ = false;
  bool capturing_ = false;
};

}  // namespace cricket

#endif  // PC_CHANNEL_MANAGER_H_
