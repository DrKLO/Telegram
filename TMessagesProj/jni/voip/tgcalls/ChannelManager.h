#ifndef TGCALLS_CHANNEL_MANAGER_H_
#define TGCALLS_CHANNEL_MANAGER_H_
#include <stdint.h>
#include <memory>
#include <string>
#include <vector>
#include "api/audio_options.h"
#include "api/crypto/crypto_options.h"
#include "api/rtp_parameters.h"
#include "api/video/video_bitrate_allocator_factory.h"
#include "call/call.h"
#include "media/base/codec.h"
#include "media/base/media_channel.h"
#include "media/base/media_config.h"
#include "media/base/media_engine.h"
#include "pc/channel.h"
#include "pc/channel_interface.h"
#include "pc/rtp_transport_internal.h"
#include "pc/session_description.h"
#include "rtc_base/system/file_wrapper.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_annotations.h"
#include "rtc_base/unique_id_generator.h"
namespace tgcalls {
// ChannelManager allows the MediaEngine to run on a separate thread, and takes
// care of marshalling calls between threads. It also creates and keeps track of
// voice and video channels; by doing so, it can temporarily pause all the
// channels when a new audio or video device is chosen. The voice and video
// channels are stored in separate vectors, to easily allow operations on just
// voice or just video channels.
// ChannelManager also allows the application to discover what devices it has
// using device manager.
class ChannelManager {
 public:
  // Returns an initialized instance of ChannelManager.
  // If media_engine is non-nullptr, then the returned ChannelManager instance
  // will own that reference and media engine initialization
  static std::unique_ptr<ChannelManager> Create(
      std::unique_ptr<cricket::MediaEngineInterface> media_engine,
      rtc::Thread* worker_thread,
      rtc::Thread* network_thread);
  ChannelManager() = delete;
  ~ChannelManager();
  rtc::Thread* worker_thread() const { return worker_thread_; }
  rtc::Thread* network_thread() const { return network_thread_; }
  cricket::MediaEngineInterface* media_engine() { return media_engine_.get(); }
  rtc::UniqueRandomIdGenerator& ssrc_generator() { return ssrc_generator_; }
  // The operations below all occur on the worker thread.
  // ChannelManager retains ownership of the created channels, so clients should
  // call the appropriate Destroy*Channel method when done.
  // Creates a voice channel, to be associated with the specified session.
  cricket::VoiceChannel* CreateVoiceChannel(webrtc::Call* call,
                                   const cricket::MediaConfig& media_config,
                                   const std::string& mid,
                                   bool srtp_required,
                                   const webrtc::CryptoOptions& crypto_options,
                                   const cricket::AudioOptions& options);
  // Creates a video channel, synced with the specified voice channel, and
  // associated with the specified session.
  // Version of the above that takes PacketTransportInternal.
  cricket::VideoChannel* CreateVideoChannel(
      webrtc::Call* call,
      const cricket::MediaConfig& media_config,
      const std::string& mid,
      bool srtp_required,
      const webrtc::CryptoOptions& crypto_options,
      const cricket::VideoOptions& options,
      webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory);
  void DestroyChannel(cricket::ChannelInterface* channel);
 protected:
  ChannelManager(std::unique_ptr<cricket::MediaEngineInterface> media_engine,
                 rtc::Thread* worker_thread,
                 rtc::Thread* network_thread);
  // Destroys a voice channel created by CreateVoiceChannel.
  void DestroyVoiceChannel(cricket::VoiceChannel* voice_channel);
  // Destroys a video channel created by CreateVideoChannel.
  void DestroyVideoChannel(cricket::VideoChannel* video_channel);
 private:
  const std::unique_ptr<cricket::MediaEngineInterface> media_engine_;  // Nullable.
  rtc::Thread* const signaling_thread_;
  rtc::Thread* const worker_thread_;
  rtc::Thread* const network_thread_;
  // This object should be used to generate any SSRC that is not explicitly
  // specified by the user (or by the remote party).
  // TODO(bugs.webrtc.org/12666): This variable is used from both the signaling
  // and worker threads. See if we can't restrict usage to a single thread.
  rtc::UniqueRandomIdGenerator ssrc_generator_;
  // Vector contents are non-null.
  std::vector<std::unique_ptr<cricket::VoiceChannel>> voice_channels_
      RTC_GUARDED_BY(worker_thread_);
  std::vector<std::unique_ptr<cricket::VideoChannel>> video_channels_
      RTC_GUARDED_BY(worker_thread_);
};
}  // namespace tgcalls
#endif  // TGCALLS_CHANNEL_MANAGER_H_