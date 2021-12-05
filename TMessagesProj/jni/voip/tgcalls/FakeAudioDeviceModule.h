#pragma once

#include <functional>
#include <memory>

#include "AudioFrame.h"

namespace webrtc {
class AudioDeviceModule;
class TaskQueueFactory;
}  // namespace webrtc

namespace rtc {
template <class T>
class scoped_refptr;
}

namespace tgcalls {
class FakeAudioDeviceModule {
 public:
  class Renderer {
   public:
    virtual ~Renderer() = default;
    virtual bool Render(const AudioFrame &samples) = 0;
    virtual void BeginFrame(double timestamp) {
    }
    virtual void AddFrameChannel(uint32_t ssrc, const tgcalls::AudioFrame &frame) {
    }
    virtual void EndFrame() {
    }
    virtual int32_t WaitForUs() {
       return 10000;
    }
  };
  class Recorder {
  public:
    virtual ~Recorder() = default;
    virtual AudioFrame Record() = 0;
    virtual int32_t WaitForUs() {
      return 10000;
    }
  };
  using Task = std::function<double()>;
  struct Options {
    uint32_t samples_per_sec{48000};
    uint32_t num_channels{2};
    std::function<void(Task)> scheduler_;
  };
  static std::function<rtc::scoped_refptr<webrtc::AudioDeviceModule>(webrtc::TaskQueueFactory *)> Creator(
      std::shared_ptr<Renderer> renderer,
      std::shared_ptr<Recorder> recorder,
      Options options);
};
}  // namespace tgcalls
