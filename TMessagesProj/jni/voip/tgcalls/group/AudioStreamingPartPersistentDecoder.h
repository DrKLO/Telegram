#ifndef TGCALLS_AUDIO_STREAMING_PART_PERSISTENT_DECODER_H
#define TGCALLS_AUDIO_STREAMING_PART_PERSISTENT_DECODER_H

#include "absl/types/optional.h"
#include <vector>
#include <string>
#include <map>
#include <stdint.h>

// Fix build on Windows - this should appear before FFmpeg timestamp include.
#define _USE_MATH_DEFINES
#include <math.h>

extern "C" {
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

namespace tgcalls {

class AudioStreamingPartPersistentDecoderState;

class WrappedCodecParameters {
public:
    WrappedCodecParameters(AVCodecParameters const *codecParameters);
    ~WrappedCodecParameters();

    bool isEqual(AVCodecParameters const *other);

private:
    AVCodecParameters *_value = nullptr;
};

class AudioStreamingPartPersistentDecoder {
public:
    AudioStreamingPartPersistentDecoder();
    ~AudioStreamingPartPersistentDecoder();

    int decode(AVCodecParameters const *codecParameters, AVRational timeBase, AVPacket &packet, AVFrame *frame);

private:
    void maybeReset(AVCodecParameters const *codecParameters, AVRational timeBase);

private:
    AudioStreamingPartPersistentDecoderState *_state = nullptr;
};

}

#endif
