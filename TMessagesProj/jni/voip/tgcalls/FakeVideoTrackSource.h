#pragma once

#include <functional>
#include <memory>
#include <string>

namespace webrtc {
class VideoTrackSourceInterface;
class VideoFrame;
}

namespace tgcalls {
class FrameSource {
public:
  struct Info {
    int32_t width;
    int32_t height;
  };

  virtual ~FrameSource() = default;

  virtual Info info() const = 0;
  virtual webrtc::VideoFrame next_frame();
  static void video_frame_to_rgb0(const webrtc::VideoFrame &src, char *dest);
  virtual void next_frame_rgb0(char *buf, double *pt_in_seconds) = 0;

  static std::unique_ptr<FrameSource> chess();
  static std::unique_ptr<FrameSource> from_file(std::string path);
};

class FakeVideoTrackSource {
 public:
  static std::function<webrtc::VideoTrackSourceInterface*()> create(std::unique_ptr<FrameSource> source);
};
}