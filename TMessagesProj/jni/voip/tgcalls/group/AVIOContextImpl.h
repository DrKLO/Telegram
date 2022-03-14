#ifndef TGCALLS_AVIOCONTEXTIMPL_H
#define TGCALLS_AVIOCONTEXTIMPL_H

#include "absl/types/optional.h"
#include <vector>
#include <stdint.h>

#include "api/video/video_frame.h"
#include "absl/types/optional.h"

// Fix build on Windows - this should appear before FFmpeg timestamp include.
#define _USE_MATH_DEFINES
#include <math.h>

extern "C" {
#include <libavutil/timestamp.h>
#include <libavformat/avformat.h>
#include <libavcodec/avcodec.h>
}

namespace tgcalls {

class AVIOContextImpl {
public:
    AVIOContextImpl(std::vector<uint8_t> &&fileData);
    ~AVIOContextImpl();

    AVIOContext *getContext() const;

public:
    std::vector<uint8_t> _fileData;
    int _fileReadPosition = 0;

    std::vector<uint8_t> _buffer;
    AVIOContext *_context = nullptr;
};

}

#endif
