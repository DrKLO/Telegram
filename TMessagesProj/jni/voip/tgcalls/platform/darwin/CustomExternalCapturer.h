#ifndef TGCALLS_CUSTOM_EXTERNAL_CAPTURER_H
#define TGCALLS_CUSTOM_EXTERNAL_CAPTURER_H
#ifdef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

#include <memory>
#include "api/scoped_refptr.h"
#include "api/media_stream_interface.h"
#import "base/RTCVideoFrame.h"
#include "Instance.h"

@interface CustomExternalCapturer : NSObject

- (instancetype)initWithSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source;

+ (void)passPixelBuffer:(CVPixelBufferRef)pixelBuffer sampleBufferReference:(CMSampleBufferRef)sampleBufferReference rotation:(RTCVideoRotation)rotation toSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source croppingBuffer:(std::vector<uint8_t> &)croppingBuffer;

@end
#endif // WEBRTC_IOS
#endif
