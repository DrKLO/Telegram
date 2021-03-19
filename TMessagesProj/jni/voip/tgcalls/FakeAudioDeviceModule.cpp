#include "FakeAudioDeviceModule.h"

#include "modules/audio_device/include/audio_device_default.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"

#include <thread>

namespace tgcalls {
class FakeAudioDeviceModuleImpl : public webrtc::webrtc_impl::AudioDeviceModuleDefault<webrtc::AudioDeviceModule> {
 public:
  static rtc::scoped_refptr<webrtc::AudioDeviceModule> Create(webrtc::TaskQueueFactory* taskQueueFactory,
                                                              std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer,
                                                              FakeAudioDeviceModule::Options options) {
    return rtc::scoped_refptr<webrtc::AudioDeviceModule>(
        new rtc::RefCountedObject<FakeAudioDeviceModuleImpl>(taskQueueFactory, options, std::move(renderer)));
  }

  FakeAudioDeviceModuleImpl(webrtc::TaskQueueFactory*, FakeAudioDeviceModule::Options options,
                            std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer)
      : num_channels_{options.num_channels}, samples_per_sec_{options.samples_per_sec}, renderer_(std::move(renderer)) {
    RTC_CHECK(num_channels_ == 1 || num_channels_ == 2);
    auto good_sample_rate = [](size_t sr) {
      return sr == 8000 || sr == 16000 || sr == 32000 || sr == 44100 || sr == 48000;
    };
    RTC_CHECK(good_sample_rate(samples_per_sec_));
    samples_per_frame_ = samples_per_sec_ / 100;
    playout_buffer_.resize(samples_per_frame_ * 2 /* 2 in case stereo will be turned on later */, 0);
  }

  ~FakeAudioDeviceModuleImpl() override {
    StopPlayout();
  }

  int32_t PlayoutIsAvailable(bool* available) override {
    if (available) {
      *available = true;
    }
    return 0;
  }

  int32_t StereoPlayoutIsAvailable(bool* available) const override {
    if (available) {
      *available = true;
    }
    return 0;
  }
  int32_t StereoPlayout(bool* enabled) const override {
    if (enabled) {
      *enabled = num_channels_ == 2;
    }
    return 0;
  }
  int32_t SetStereoPlayout(bool enable) override {
    size_t new_num_channels = enable ? 2 : 1;
    if (new_num_channels != num_channels_) {
      return -1;
    }
    return 0;
  }

  int32_t Init() override {
    return 0;
  }

  int32_t RegisterAudioCallback(webrtc::AudioTransport* callback) override {
    webrtc::MutexLock lock(&lock_);
    audio_callback_ = callback;
    return 0;
  }

  int32_t StartPlayout() override {
    webrtc::MutexLock lock(&lock_);
    RTC_CHECK(renderer_);
    if (rendering_) {
      return 0;
    }
    rendering_ = true;
    renderThread_ = std::make_unique<rtc::PlatformThread>(
        RenderThreadFunction, this, "webrtc_fake_audio_module_capture_thread", rtc::kRealtimePriority);
    renderThread_->Start();

    return 0;
  }

  int32_t StopPlayout() override {
    if (!rendering_) {
      return 0;
    }
    decltype(renderThread_) thread;

    {
      webrtc::MutexLock lock(&lock_);
      thread = std::move(renderThread_);
      rendering_ = false;
    }

    thread->Stop();
    return 0;
  }

  bool Playing() const override {
    return rendering_;
  }

 private:
  static void RenderThreadFunction(void* pThis) {
    auto* device = static_cast<FakeAudioDeviceModuleImpl*>(pThis);
    while (true) {
      int wait_for_us = device->Render();
      if (wait_for_us < 0) {
        break;
      }
      std::this_thread::sleep_for(std::chrono::microseconds(wait_for_us));
    }
  }

  int32_t Render() {
    webrtc::MutexLock lock(&lock_);
    if (!rendering_) {
      return -1;
    }
    size_t samples_out = 0;
    int64_t elapsed_time_ms = -1;
    int64_t ntp_time_ms = -1;
    size_t bytes_per_sample = 2;

    RTC_CHECK(audio_callback_);
    if (renderer_) {
      renderer_->BeginFrame(0);
    }
    audio_callback_->NeedMorePlayData(samples_per_frame_, bytes_per_sample, num_channels_, samples_per_sec_,
                                      playout_buffer_.data(), samples_out, &elapsed_time_ms, &ntp_time_ms);
    if (renderer_) {
      renderer_->EndFrame();
    }
    if (samples_out != 0 && renderer_) {
      AudioFrame frame;
      frame.audio_samples = playout_buffer_.data();
      frame.num_samples = samples_out;
      frame.bytes_per_sample = bytes_per_sample;
      frame.num_channels = num_channels_;
      frame.samples_per_sec = samples_per_sec_;
      frame.elapsed_time_ms = elapsed_time_ms;
      frame.ntp_time_ms = ntp_time_ms;
      renderer_->Render(frame);
    }
    int32_t wait_for_us = -1;
    if (renderer_) {
      wait_for_us = renderer_->WaitForUs();
    }
    return wait_for_us;
  }

  size_t num_channels_;
  const uint32_t samples_per_sec_;
  size_t samples_per_frame_{0};

  mutable webrtc::Mutex lock_;
  std::atomic<bool> rendering_{false};
  std::unique_ptr<rtc::PlatformThread> renderThread_;

  webrtc::AudioTransport* audio_callback_{nullptr};
  const std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer_ RTC_GUARDED_BY(lock_);
  std::vector<int16_t> playout_buffer_ RTC_GUARDED_BY(lock_);
};

std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> FakeAudioDeviceModule::Creator(
    std::shared_ptr<Renderer> renderer, Options options) {
  bool is_renderer_empty = bool(renderer);
  auto boxed_renderer = std::make_shared<std::shared_ptr<Renderer>>(std::move(renderer));
  return
      [boxed_renderer = std::move(boxed_renderer), is_renderer_empty, options](webrtc::TaskQueueFactory* task_factory) {
        RTC_CHECK(is_renderer_empty == bool(*boxed_renderer));  // call only once if renderer exists
        return FakeAudioDeviceModuleImpl::Create(task_factory, std::move(*boxed_renderer), options);
      };
}
}  // namespace tgcalls
