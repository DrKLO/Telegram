#ifndef TGCALLS_VIDEO_STREAMING_PART_H
#define TGCALLS_VIDEO_STREAMING_PART_H

#include "absl/types/optional.h"
#include <vector>
#include <stdint.h>

#include "api/video/video_frame.h"
#include "absl/types/optional.h"

#include "AudioStreamingPart.h"
#include "AudioStreamingPartInternal.h"

namespace tgcalls {

class VideoStreamingPartState;
class VideoStreamingSharedStateInternal;

struct VideoStreamingPartFrame {
    std::string endpointId;
    webrtc::VideoFrame frame;
    double pts = 0;
    int index = 0;

    VideoStreamingPartFrame(std::string endpointId_, webrtc::VideoFrame const &frame_, double pts_, int index_) :
    endpointId(endpointId_),
    frame(frame_),
    pts(pts_),
    index(index_) {
    }
};

class VideoStreamingSharedState {
public:
    VideoStreamingSharedState();
    ~VideoStreamingSharedState();
    
    VideoStreamingSharedStateInternal *impl() const {
        return _impl;
    }
    
private:
    VideoStreamingSharedStateInternal *_impl = nullptr;
};

class VideoStreamingPart {
public:
    enum class ContentType {
        Audio,
        Video
    };
    
public:
    explicit VideoStreamingPart(std::vector<uint8_t> &&data, VideoStreamingPart::ContentType contentType);
    ~VideoStreamingPart();
    
    VideoStreamingPart(const VideoStreamingPart&) = delete;
    VideoStreamingPart(VideoStreamingPart&& other) {
        _state = other._state;
        other._state = nullptr;
    }
    VideoStreamingPart& operator=(const VideoStreamingPart&) = delete;
    VideoStreamingPart& operator=(VideoStreamingPart&&) = delete;

    absl::optional<VideoStreamingPartFrame> getFrameAtRelativeTimestamp(VideoStreamingSharedState const *sharedState, double timestamp);
    absl::optional<std::string> getActiveEndpointId() const;
    bool hasRemainingFrames() const;
    
    int getAudioRemainingMilliseconds();
    std::vector<AudioStreamingPart::StreamingPartChannel> getAudio10msPerChannel(AudioStreamingPartPersistentDecoder &persistentDecoder);
    
private:
    VideoStreamingPartState *_state = nullptr;
};

}

#endif
