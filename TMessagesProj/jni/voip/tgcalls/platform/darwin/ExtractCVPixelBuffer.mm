#include "ExtractCVPixelBuffer.h"

#include "sdk/objc/native/src/objc_frame_buffer.h"
#import "sdk/objc/components/video_frame_buffer/RTCCVPixelBuffer.h"

namespace tgcalls {


webrtc::scoped_refptr<webrtc::VideoFrameBuffer> extractCVPixelBuffer(void *data) {
    CVPixelBufferRef pixelBuffer = (CVPixelBufferRef)(void *)data;
    return rtc::make_ref_counted<webrtc::ObjCFrameBuffer>([[RTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer]);
}

}
