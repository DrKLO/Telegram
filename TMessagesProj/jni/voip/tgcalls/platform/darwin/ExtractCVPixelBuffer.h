#ifndef TGCALLS_EXTRACT_CV_PIXEL_BUFFER_H
#define TGCALLS_EXTRACT_CV_PIXEL_BUFFER_H

#include "platform/PlatformInterface.h"

namespace tgcalls {

webrtc::scoped_refptr<webrtc::VideoFrameBuffer> extractCVPixelBuffer(void *data);

} // namespace tgcalls

#endif
