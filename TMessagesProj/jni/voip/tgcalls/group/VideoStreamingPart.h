#ifndef TGCALLS_VIDEO_STREAMING_PART_H
#define TGCALLS_VIDEO_STREAMING_PART_H

#include "absl/types/optional.h"
#include <vector>
#include <stdint.h>

#include "api/video/video_frame.h"
#include "absl/types/optional.h"

namespace tgcalls {

class VideoStreamingPartState;

struct VideoStreamingPartFrame {
    std::string endpointId;
    webrtc::VideoFrame frame;
    double pts = 0;
    double duration = 0.0;
    int index = 0;

    VideoStreamingPartFrame(std::string endpointId_, webrtc::VideoFrame const &frame_, double pts_, double duration_, int index_) :
    endpointId(endpointId_),
    frame(frame_),
    pts(pts_),
    duration(duration_),
    index(index_) {
    }
};

class VideoStreamingPart {
public:
    explicit VideoStreamingPart(std::vector<uint8_t> &&data);
    ~VideoStreamingPart();
    
    VideoStreamingPart(const VideoStreamingPart&) = delete;
    VideoStreamingPart(VideoStreamingPart&& other) {
        _state = other._state;
        other._state = nullptr;
    }
    VideoStreamingPart& operator=(const VideoStreamingPart&) = delete;
    VideoStreamingPart& operator=(VideoStreamingPart&&) = delete;

    absl::optional<VideoStreamingPartFrame> getFrameAtRelativeTimestamp(double timestamp);
    absl::optional<std::string> getActiveEndpointId() const;
    
private:
    VideoStreamingPartState *_state = nullptr;
};

}

#endif
