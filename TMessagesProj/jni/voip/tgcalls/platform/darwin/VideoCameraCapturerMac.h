#ifndef TGCALLS_VIDEO_CAMERA_CAPTURER_MAC_H
#define TGCALLS_VIDEO_CAMERA_CAPTURER_MAC_H
#ifndef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

#include <memory>
#include "api/scoped_refptr.h"
#include "api/media_stream_interface.h"


@protocol CapturerInterface
- (void)start;
- (void)stop;
- (void)setIsEnabled:(bool)isEnabled;
- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink;
- (void)setPreferredCaptureAspectRatio:(float)aspectRatio;
- (void)setOnFatalError:(std::function<void()>)error;
- (void)setOnPause:(std::function<void(bool)>)pause;
@end

@interface VideoCameraCapturer : NSObject<CapturerInterface>

+ (NSArray<AVCaptureDevice *> *)captureDevices;
+ (NSArray<AVCaptureDeviceFormat *> *)supportedFormatsForDevice:(AVCaptureDevice *)device;

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source isActiveUpdated:(void (^)(bool))isActiveUpdated;

- (void)setupCaptureWithDevice:(AVCaptureDevice *)device format:(AVCaptureDeviceFormat *)format fps:(NSInteger)fps;
- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink;
- (BOOL)deviceIsCaptureCompitable:(AVCaptureDevice *)device;

@end
#endif //WEBRTC_MAC
#endif
