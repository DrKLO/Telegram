#include "FakeVideoTrackSource.h"

#include "api/video/i420_buffer.h"
#include "media/base/video_broadcaster.h"
#include "pc/video_track_source.h"

#include "libyuv.h"

#include <thread>

namespace tgcalls {

int WIDTH = 1280;
int HEIGHT = 720;

class ChessFrameSource : public FrameSource {
public:
  ChessFrameSource() {
    int N = 100;
    frames_.reserve(N);
    for (int i = 0; i < N; i++) {
      frames_.push_back(genFrame(i, N));
    }
  }
  Info info() const override{
    return Info{WIDTH, HEIGHT};
  }
//  webrtc::VideoFrame next_frame() override {
//    i = (i + 1) % frames_.size();
//    return frames_[i].frame;
//  }
  void next_frame_rgb0(char *buf, double *pts) override {
    *pts = 0;
    i = (i + 1) % frames_.size();
    size_t size = WIDTH * HEIGHT * 4;
    memcpy(buf, frames_[i].rbga.get(), size);
  }

private:
  struct Frame {
    webrtc::VideoFrame frame;
    std::unique_ptr<std::uint8_t[]> rbga;
  };
  std::vector<Frame> frames_;
  size_t i = 0;
  Frame genFrame(int i, int n) {
    int width = WIDTH;
    int height = HEIGHT;
    auto bytes_ptr = std::make_unique<std::uint8_t[]>(width * height * 4);
    auto bytes = bytes_ptr.get();
    auto set_rgb = [&](int x, int y, std::uint8_t r, std::uint8_t g, std::uint8_t b) {
      auto dest = bytes + (x * width + y) * 4;
      dest[0] = r;
      dest[1] = g;
      dest[2] = b;
      dest[3] = 0;
    };
    auto angle = (double)i / n * M_PI;
    auto co = cos(angle);
    auto si = sin(angle);

    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        double sx = (i - height / 2) * 20.0 / HEIGHT;
        double sy = (j  - width / 2) * 20.0 / HEIGHT;

        int x, y;
        if (sx * sx + sy * sy < 10) {
          x = int(floor(sx * co - sy * si));
          y = int(floor(sx * si + sy * co));
        } else {
          x = int(floor(sx));
          y = int(floor(sy));
        }
        std::uint8_t color = ((y & 1) ^ (x & 1)) * 255;
        set_rgb(i, j, color, color, color);
      }
    }

    rtc::scoped_refptr<webrtc::I420Buffer> buffer = webrtc::I420Buffer::Create(width, height);

    libyuv::RGBAToI420(bytes, width * 4, buffer->MutableDataY(), buffer->StrideY(), buffer->MutableDataU(),
                       buffer->StrideU(), buffer->MutableDataV(), buffer->StrideV(), width, height);

    return Frame{webrtc::VideoFrame::Builder().set_video_frame_buffer(buffer).build(), std::move(bytes_ptr)};
  }

};

webrtc::VideoFrame FrameSource::next_frame() {
  auto info = this->info();
  auto height = info.height;
  auto width = info.width;
  auto bytes_ptr = std::make_unique<std::uint8_t[]>(width * height * 4);
  double pts;
  next_frame_rgb0(reinterpret_cast<char *>(bytes_ptr.get()), &pts);
  rtc::scoped_refptr<webrtc::I420Buffer> buffer = webrtc::I420Buffer::Create(width, height);
  libyuv::ABGRToI420(bytes_ptr.get(), width * 4, buffer->MutableDataY(), buffer->StrideY(), buffer->MutableDataU(),
                     buffer->StrideU(), buffer->MutableDataV(), buffer->StrideV(), width, height);
  return webrtc::VideoFrame::Builder().set_timestamp_us(static_cast<int64_t>(pts * 1000000)).set_video_frame_buffer(buffer).build();
}

class FakeVideoSource : public rtc::VideoSourceInterface<webrtc::VideoFrame> {
 public:
  FakeVideoSource(std::unique_ptr<FrameSource> source) {
    data_ = std::make_shared<Data>();
    std::thread([data = data_, source = std::move(source)] {
      std::uint32_t step = 0;
      while (!data->flag_) {
        step++;
        std::this_thread::sleep_for(std::chrono::milliseconds(1000 / 30));
        auto frame = source->next_frame();
        frame.set_id(static_cast<std::uint16_t>(step));
        frame.set_timestamp_us(rtc::TimeMicros());
        data->broadcaster_.OnFrame(frame);
      }
    }).detach();
  }
  ~FakeVideoSource() {
    data_->flag_ = true;
  }
  using VideoFrameT = webrtc::VideoFrame;
  void AddOrUpdateSink(rtc::VideoSinkInterface<VideoFrameT> *sink, const rtc::VideoSinkWants &wants) override {
    RTC_LOG(LS_WARNING) << "ADD";
    data_->broadcaster_.AddOrUpdateSink(sink, wants);
  }
  // RemoveSink must guarantee that at the time the method returns,
  // there is no current and no future calls to VideoSinkInterface::OnFrame.
  void RemoveSink(rtc::VideoSinkInterface<VideoFrameT> *sink) override {
    RTC_LOG(LS_WARNING) << "REMOVE";
    data_->broadcaster_.RemoveSink(sink);
  }

 private:
  struct Data {
    std::atomic<bool> flag_;
    rtc::VideoBroadcaster broadcaster_;
  };
  std::shared_ptr<Data> data_;
};

class FakeVideoTrackSourceImpl : public webrtc::VideoTrackSource {
 public:
  static rtc::scoped_refptr<FakeVideoTrackSourceImpl> Create(std::unique_ptr<FrameSource> source) {
    return rtc::scoped_refptr<FakeVideoTrackSourceImpl>(new rtc::RefCountedObject<FakeVideoTrackSourceImpl>(std::move(source)));
  }

  explicit FakeVideoTrackSourceImpl(std::unique_ptr<FrameSource> source) : VideoTrackSource(false), source_(std::move(source)) {
  }

 protected:
  FakeVideoSource source_;
  rtc::VideoSourceInterface<webrtc::VideoFrame> *source() override {
    return &source_;
  }
};

std::function<webrtc::VideoTrackSourceInterface*()> FakeVideoTrackSource::create(std::unique_ptr<FrameSource> frame_source) {
  auto source = FakeVideoTrackSourceImpl::Create(std::move(frame_source));
  return [source] {
    return source.get();
  };
}
std::unique_ptr<FrameSource> FrameSource::chess(){
  return std::make_unique<ChessFrameSource>();
}

void FrameSource::video_frame_to_rgb0(const webrtc::VideoFrame & src, char *dest){
  auto buffer = src.video_frame_buffer()->ToI420();
  libyuv::I420ToABGR(buffer->DataY(), buffer->StrideY(), buffer->DataU(),
                     buffer->StrideU(), buffer->DataV(), buffer->StrideV( ), reinterpret_cast<uint8_t *>(dest), src.width() * 4,  src.width(), src.height());
}
}
