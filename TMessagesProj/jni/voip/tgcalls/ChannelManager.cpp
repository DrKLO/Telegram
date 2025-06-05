#include "ChannelManager.h"
#include <utility>
#include "absl/algorithm/container.h"
#include "absl/memory/memory.h"
#include "absl/strings/match.h"
#include "api/media_types.h"
#include "api/sequence_checker.h"
#include "media/base/media_constants.h"
#include "rtc_base/checks.h"
#include "rtc_base/trace_event.h"
namespace tgcalls {
// static
std::unique_ptr<ChannelManager> ChannelManager::Create(
    std::unique_ptr<cricket::MediaEngineInterface> media_engine,
    rtc::Thread* worker_thread,
    rtc::Thread* network_thread) {
  RTC_DCHECK(network_thread);
  RTC_DCHECK(worker_thread);
  return absl::WrapUnique(new ChannelManager(
      std::move(media_engine), worker_thread, network_thread));
}
ChannelManager::ChannelManager(
    std::unique_ptr<cricket::MediaEngineInterface> media_engine,
    rtc::Thread* worker_thread,
    rtc::Thread* network_thread)
    : media_engine_(std::move(media_engine)),
      signaling_thread_(rtc::Thread::Current()),
      worker_thread_(worker_thread),
      network_thread_(network_thread) {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  RTC_DCHECK(worker_thread_);
  RTC_DCHECK(network_thread_);
  if (media_engine_) {
    // TODO(tommi): Change VoiceEngine to do ctor time initialization so that
    // this isn't necessary.
    worker_thread_->BlockingCall([&] { media_engine_->Init(); });
  }
}
ChannelManager::~ChannelManager() {
  RTC_DCHECK_RUN_ON(signaling_thread_);
  worker_thread_->BlockingCall([&] {
    RTC_DCHECK_RUN_ON(worker_thread_);
    RTC_DCHECK(voice_channels_.empty());
    RTC_DCHECK(video_channels_.empty());
    // While `media_engine_` is const throughout the ChannelManager's lifetime,
    // it requires destruction to happen on the worker thread. Instead of
    // marking the pointer as non-const, we live with this const_cast<> in the
    // destructor.
    const_cast<std::unique_ptr<cricket::MediaEngineInterface>&>(media_engine_).reset();
  });
}
cricket::VoiceChannel* ChannelManager::CreateVoiceChannel(
    webrtc::Call* call,
    const cricket::MediaConfig& media_config,
    const std::string& mid,
    bool srtp_required,
    const webrtc::CryptoOptions& crypto_options,
    const cricket::AudioOptions& options) {
  RTC_DCHECK(call);
  RTC_DCHECK(media_engine_);
  // TODO(bugs.webrtc.org/11992): Remove this workaround after updates in
  // PeerConnection and add the expectation that we're already on the right
  // thread.
  if (!worker_thread_->IsCurrent()) {
    cricket::VoiceChannel* temp = nullptr;
    worker_thread_->BlockingCall([&] {
      temp = CreateVoiceChannel(call, media_config, mid, srtp_required,
                                crypto_options, options);
    });
    return temp;
  }
  RTC_DCHECK_RUN_ON(worker_thread_);
  std::unique_ptr<cricket::VoiceMediaSendChannelInterface> send_media_channel = media_engine_->voice().CreateSendChannel(
      call, media_config, options, crypto_options, webrtc::AudioCodecPairId::Create());
  if (!send_media_channel) {
    return nullptr;
  }
  std::unique_ptr<cricket::VoiceMediaReceiveChannelInterface> receive_media_channel = media_engine_->voice().CreateReceiveChannel(
        call, media_config, options, crypto_options, webrtc::AudioCodecPairId::Create());
  if (!receive_media_channel) {
    return nullptr;
  }
  auto voice_channel = std::make_unique<cricket::VoiceChannel>(
      worker_thread_, network_thread_, signaling_thread_,
      std::move(send_media_channel), std::move(receive_media_channel), mid, srtp_required, crypto_options,
      &ssrc_generator_);
  cricket::VoiceChannel* voice_channel_ptr = voice_channel.get();
  voice_channels_.push_back(std::move(voice_channel));
  return voice_channel_ptr;
}
void ChannelManager::DestroyVoiceChannel(cricket::VoiceChannel* channel) {
  TRACE_EVENT0("webrtc", "ChannelManager::DestroyVoiceChannel");
  RTC_DCHECK_RUN_ON(worker_thread_);
  voice_channels_.erase(absl::c_find_if(
      voice_channels_, [&](const auto& p) { return p.get() == channel; }));
}
cricket::VideoChannel* ChannelManager::CreateVideoChannel(
    webrtc::Call* call,
    const cricket::MediaConfig& media_config,
    const std::string& mid,
    bool srtp_required,
    const webrtc::CryptoOptions& crypto_options,
    const cricket::VideoOptions& options,
    webrtc::VideoBitrateAllocatorFactory* video_bitrate_allocator_factory) {
  RTC_DCHECK(call);
  RTC_DCHECK(media_engine_);
  // TODO(bugs.webrtc.org/11992): Remove this workaround after updates in
  // PeerConnection and add the expectation that we're already on the right
  // thread.
  if (!worker_thread_->IsCurrent()) {
    cricket::VideoChannel* temp = nullptr;
    worker_thread_->BlockingCall([&] {
      temp = CreateVideoChannel(call, media_config, mid, srtp_required,
                                crypto_options, options,
                                video_bitrate_allocator_factory);
    });
    return temp;
  }
  RTC_DCHECK_RUN_ON(worker_thread_);
  std::unique_ptr<cricket::VideoMediaSendChannelInterface> send_media_channel = media_engine_->video().CreateSendChannel(
      call, media_config, options, crypto_options,
      video_bitrate_allocator_factory);
  if (!send_media_channel) {
    return nullptr;
  }
  std::unique_ptr<cricket::VideoMediaReceiveChannelInterface> receive_media_channel = media_engine_->video().CreateReceiveChannel(
    call, media_config, options, crypto_options);
  if (!receive_media_channel) {
    return nullptr;
  }
  auto video_channel = std::make_unique<cricket::VideoChannel>(
      worker_thread_, network_thread_, signaling_thread_,
      std::move(send_media_channel), std::move(receive_media_channel), mid, srtp_required, crypto_options,
      &ssrc_generator_);
  cricket::VideoChannel* video_channel_ptr = video_channel.get();
  video_channels_.push_back(std::move(video_channel));
  return video_channel_ptr;
}
void ChannelManager::DestroyVideoChannel(cricket::VideoChannel* channel) {
  TRACE_EVENT0("webrtc", "ChannelManager::DestroyVideoChannel");
  RTC_DCHECK_RUN_ON(worker_thread_);
  video_channels_.erase(absl::c_find_if(
      video_channels_, [&](const auto& p) { return p.get() == channel; }));
}
void ChannelManager::DestroyChannel(cricket::ChannelInterface* channel) {
  RTC_DCHECK(channel);
  if (!worker_thread_->IsCurrent()) {
    // TODO(tommi): Do this asynchronously when we have a way to make sure that
    // the call to DestroyChannel runs before ~Call() runs, which today happens
    // inside an Invoke from the signaling thread in PeerConnectin::Close().
    worker_thread_->BlockingCall([&] { DestroyChannel(channel); });
    return;
  }
  if (channel->media_type() == cricket::MEDIA_TYPE_AUDIO) {
    DestroyVoiceChannel(static_cast<cricket::VoiceChannel*>(channel));
  } else {
    RTC_DCHECK_EQ(channel->media_type(), cricket::MEDIA_TYPE_VIDEO);
    DestroyVideoChannel(static_cast<cricket::VideoChannel*>(channel));
  }
}
}  // namespace tgcalls
