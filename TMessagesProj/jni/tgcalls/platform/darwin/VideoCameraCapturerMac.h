#ifndef TGCALLS_VIDEO_CAMERA_CAPTURER_MAC_H
#define TGCALLS_VIDEO_CAMERA_CAPTURER_MAC_H
#ifndef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

#include <memory>
#include "api/scoped_refptr.h"
#include "api/media_stream_interface.h"

@interface VideoCameraCapturer : NSObject

+ (NSArray<AVCaptureDevice *> *)captureDevices;
+ (NSArray<AVCaptureDeviceFormat *> *)supportedFormatsForDevice:(AVCaptureDevice *)device;

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source isActiveUpdated:(void (^)(bool))isActiveUpdated;

- (void)startCaptureWithDevice:(AVCaptureDevice *)device format:(AVCaptureDeviceFormat *)format fps:(NSInteger)fps;
- (void)stopCapture;
- (void)setIsEnabled:(bool)isEnabled;
- (void)setPreferredCaptureAspectRatio:(float)aspectRatio;
- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink;
- (BOOL)deviceIsCaptureCompitable:(AVCaptureDevice *)device;

@end
#endif //WEBRTC_MAC
#endif
