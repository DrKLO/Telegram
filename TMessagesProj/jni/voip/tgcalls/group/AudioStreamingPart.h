#ifndef TGCALLS_AUDIO_STREAMING_PART_H
#define TGCALLS_AUDIO_STREAMING_PART_H

#include "absl/types/optional.h"
#include <vector>
#include <map>
#include <stdint.h>

namespace tgcalls {

class AudioStreamingPartState;

class AudioStreamingPart {
public:
    struct StreamingPartChannel {
        uint32_t ssrc = 0;
        std::vector<int16_t> pcmData;
    };
    
    explicit AudioStreamingPart(std::vector<uint8_t> &&data);
    ~AudioStreamingPart();
    
    AudioStreamingPart(const AudioStreamingPart&) = delete;
    AudioStreamingPart(AudioStreamingPart&& other) {
        _state = other._state;
        other._state = nullptr;
    }
    AudioStreamingPart& operator=(const AudioStreamingPart&) = delete;
    AudioStreamingPart& operator=(AudioStreamingPart&&) = delete;

    std::map<std::string, int32_t> getEndpointMapping() const;
    int getRemainingMilliseconds() const;
    std::vector<StreamingPartChannel> get10msPerChannel();
    
private:
    AudioStreamingPartState *_state = nullptr;
};

}

#endif
