#ifndef TGCALLS_STREAMING_PART_H
#define TGCALLS_STREAMING_PART_H

#include "absl/types/optional.h"
#include <vector>
#include <stdint.h>

namespace tgcalls {

class StreamingPartState;

class StreamingPart {
public:
    struct StreamingPartChannel {
        uint32_t ssrc = 0;
        std::vector<int16_t> pcmData;
    };
    
    explicit StreamingPart(std::vector<uint8_t> &&data);
    ~StreamingPart();
    
    StreamingPart(const StreamingPart&) = delete;
    StreamingPart(StreamingPart&& other) {
        _state = other._state;
        other._state = nullptr;
    }
    StreamingPart& operator=(const StreamingPart&) = delete;
    StreamingPart& operator=(StreamingPart&&) = delete;
    
    int getRemainingMilliseconds() const;
    std::vector<StreamingPartChannel> get10msPerChannel();
    
private:
    StreamingPartState *_state = nullptr;
};

}

#endif
