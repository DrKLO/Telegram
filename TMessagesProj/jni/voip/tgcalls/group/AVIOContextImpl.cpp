#include "AVIOContextImpl.h"

#include "rtc_base/logging.h"
#include "rtc_base/third_party/base64/base64.h"
#include "api/video/i420_buffer.h"

#include <string>
#include <set>
#include <map>

namespace tgcalls {

namespace {

int AVIOContextImplRead(void *opaque, unsigned char *buffer, int bufferSize) {
    AVIOContextImpl *instance = static_cast<AVIOContextImpl *>(opaque);

    int bytesToRead = std::min(bufferSize, ((int)instance->_fileData.size()) - instance->_fileReadPosition);
    if (bytesToRead < 0) {
        bytesToRead = 0;
    }

    if (bytesToRead > 0) {
        memcpy(buffer, instance->_fileData.data() + instance->_fileReadPosition, bytesToRead);
        instance->_fileReadPosition += bytesToRead;

        return bytesToRead;
    } else {
        return AVERROR_EOF;
    }
}

int64_t AVIOContextImplSeek(void *opaque, int64_t offset, int whence) {
    AVIOContextImpl *instance = static_cast<AVIOContextImpl *>(opaque);

    if (whence == 0x10000) {
        return (int64_t)instance->_fileData.size();
    } else {
        int64_t seekOffset = std::min(offset, (int64_t)instance->_fileData.size());
        if (seekOffset < 0) {
            seekOffset = 0;
        }
        instance->_fileReadPosition = (int)seekOffset;
        return seekOffset;
    }
}

}

AVIOContextImpl::AVIOContextImpl(std::vector<uint8_t> &&fileData) :
_fileData(std::move(fileData)) {
    _buffer.resize(4 * 1024);
    _context = avio_alloc_context(_buffer.data(), (int)_buffer.size(), 0, this, &AVIOContextImplRead, NULL, &AVIOContextImplSeek);
}

AVIOContextImpl::~AVIOContextImpl() {
    av_free(_context);
}

AVIOContext *AVIOContextImpl::getContext() const {
    return _context;
};

}
