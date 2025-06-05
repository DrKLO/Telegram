#ifndef TGCALLS_CALL_AUDIO_TONE_H_
#define TGCALLS_CALL_AUDIO_TONE_H_

#include <vector>

namespace tgcalls {

class CallAudioTone {
public:
    CallAudioTone(std::vector<int16_t> &&samples, int sampleRate, int loopCount) :
    _samples(std::move(samples)), _sampleRate(sampleRate), _loopCount(loopCount) {
    }

public:
    std::vector<int16_t> const samples() const {
        return _samples;
    }

    int sampleRate() const {
        return _sampleRate;
    }

    int loopCount() const {
        return _loopCount;
    }
    
    void setLoopCount(int loopCount) {
        _loopCount = loopCount;
    }
    
    size_t offset() const {
        return _offset;
    }
    
    void setOffset(size_t offset) {
        _offset = offset;
    }

private:
    std::vector<int16_t> _samples;
    int _sampleRate = 48000;
    int _loopCount = 1;
    size_t _offset = 0;
};

}

#endif  // TGCALLS_CALL_AUDIO_TONE_H_
