#include "FakeAudioDeviceModule.h"

#include "modules/audio_device/include/audio_device_default.h"
#include "rtc_base/ref_counted_object.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"

#include <thread>
#include <mutex>
#include <condition_variable>

namespace tgcalls {
class FakeAudioDeviceModuleImpl : public webrtc::webrtc_impl::AudioDeviceModuleDefault<webrtc::AudioDeviceModule> {
 public:
  static rtc::scoped_refptr<webrtc::AudioDeviceModule> Create(webrtc::TaskQueueFactory* taskQueueFactory,
                                                              std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer,
                                                              std::shared_ptr<FakeAudioDeviceModule::Recorder> recorder,
                                                              FakeAudioDeviceModule::Options options) {
    return rtc::scoped_refptr<webrtc::AudioDeviceModule>(
        new rtc::RefCountedObject<FakeAudioDeviceModuleImpl>(taskQueueFactory, options, std::move(renderer), std::move(recorder)));
  }

  FakeAudioDeviceModuleImpl(webrtc::TaskQueueFactory*, FakeAudioDeviceModule::Options options,
                            std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer,
                            std::shared_ptr<FakeAudioDeviceModule::Recorder> recorder)
      : num_channels_{options.num_channels}, samples_per_sec_{options.samples_per_sec}, scheduler_(options.scheduler_),
        renderer_(std::move(renderer)), recorder_(std::move(recorder)) {
    if (!scheduler_) {
      scheduler_ = [](auto f)  {
        std::thread([f = std::move(f)]() {
          while (true)  {
            double wait = f();
            if (wait < 0) {
              return;
            }
            std::this_thread::sleep_for(std::chrono::microseconds (static_cast<int64_t>(wait * 1000000)));
          }
        }).detach();
      };
    }
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
    std::unique_lock<std::mutex> lock(render_mutex_);
    audio_callback_ = callback;
    return 0;
  }

  int32_t StartPlayout() override {
    std::unique_lock<std::mutex> lock(render_mutex_);
    if (!renderer_) {
      return 0;
    }
    if (rendering_) {
      return 0;
    }
    need_rendering_ = true;
    rendering_ = true;
    scheduler_([this]{
      return Render() / 1000000.0;
    });
    return 0;
  }

  int32_t StopPlayout() override {
    if (!rendering_) {
      return 0;
    }

    need_rendering_ = false;
    std::unique_lock<std::mutex> lock(render_mutex_);
    render_cond_.wait(lock, [this]{ return !rendering_; });

    return 0;
  }

  bool Playing() const override {
    return rendering_;
  }

  int32_t StartRecording() override {
    std::unique_lock<std::mutex> lock(record_mutex_);
    if (!recorder_) {
      return 0;
    }
    if (recording_) {
      return 0;
    }
    need_recording_ = true;
    recording_ = true;
    scheduler_([this]{
      return Record() / 1000000.0;
    });
    return 0;
  }
 int32_t StopRecording() override {
   if (!recording_) {
     return 0;
   }

   need_recording_ = false;
   std::unique_lock<std::mutex> lock(record_mutex_);
   record_cond_.wait(lock, [this]{ return !recording_; });

   return 0;
 }
  bool Recording() const override {
    return recording_;
  }

private:

  int32_t Render() {
    std::unique_lock<std::mutex> lock(render_mutex_);
    if (!need_rendering_) {
      rendering_ = false;
      render_cond_.notify_all();
      return -1;
    }

    size_t samples_out = 0;
    int64_t elapsed_time_ms = -1;
    int64_t ntp_time_ms = -1;
    size_t bytes_per_sample = 2 * num_channels_;

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

  int32_t Record() {
    std::unique_lock<std::mutex> lock(record_mutex_);
    if (!need_recording_) {
      recording_ = false;
      record_cond_.notify_all();
      return -1;
    }

    auto frame = recorder_->Record();
    if (frame.num_samples != 0) {
       uint32_t new_mic_level;
       audio_callback_->RecordedDataIsAvailable(frame.audio_samples,
         frame.num_samples, frame.bytes_per_sample, frame.num_channels,
         frame.samples_per_sec, 0, 0, 0, false, new_mic_level);
    }

    int32_t wait_for_us = -1;
    if (recorder_) {
      wait_for_us = recorder_->WaitForUs();
    }
    return wait_for_us;
  }

  size_t num_channels_;
  const uint32_t samples_per_sec_;
  size_t samples_per_frame_{0};

  std::function<void(FakeAudioDeviceModule::Task)> scheduler_;

  mutable std::mutex render_mutex_;
  std::atomic<bool> need_rendering_{false};
  std::atomic<bool> rendering_{false};
  std::condition_variable render_cond_;
  std::unique_ptr<rtc::PlatformThread> renderThread_;

  mutable std::mutex record_mutex_;
  std::atomic<bool> need_recording_{false};
  std::atomic<bool> recording_{false};
  std::condition_variable record_cond_;
  std::unique_ptr<rtc::PlatformThread> recordThread_;


  webrtc::AudioTransport* audio_callback_{nullptr};
  const std::shared_ptr<FakeAudioDeviceModule::Renderer> renderer_;
  const std::shared_ptr<FakeAudioDeviceModule::Recorder> recorder_;
  std::vector<int16_t> playout_buffer_;
};

std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory*)> FakeAudioDeviceModule::Creator(
    std::shared_ptr<Renderer> renderer, std::shared_ptr<Recorder> recorder, Options options) {
  bool is_renderer_empty = bool(renderer);
  auto boxed_renderer = std::make_shared<std::shared_ptr<Renderer>>(std::move(renderer));
  bool is_recorder_empty = bool(recorder);
  auto boxed_recorder = std::make_shared<std::shared_ptr<Recorder>>(std::move(recorder));
  return
      [boxed_renderer = std::move(boxed_renderer), is_renderer_empty,
       boxed_recorder = std::move(boxed_recorder), is_recorder_empty, options](webrtc::TaskQueueFactory* task_factory) {
        RTC_CHECK(is_renderer_empty == bool(*boxed_renderer));  // call only once if renderer exists
        RTC_CHECK(is_recorder_empty == bool(*boxed_recorder));  // call only once if recorder exists
        return FakeAudioDeviceModuleImpl::Create(task_factory, std::move(*boxed_renderer), std::move(*boxed_recorder), options);
      };
}
}  // namespace tgcalls
