#ifndef TGCALLS_VIDEO_CAMERA_CAPTURER_H
#define TGCALLS_VIDEO_CAMERA_CAPTURER_H
#ifdef WEBRTC_IOS
#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

#include <memory>
#include "api/scoped_refptr.h"
#include "api/media_stream_interface.h"
#include "Instance.h"

@class VideoCaptureView;

@interface VideoCameraCapturer : NSObject

+ (NSArray<AVCaptureDevice *> *)captureDevices;
+ (NSArray<AVCaptureDeviceFormat *> *)supportedFormatsForDevice:(AVCaptureDevice *)device;

- (instancetype)initWithSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source useFrontCamera:(bool)useFrontCamera keepLandscape:(bool)keepLandscape isActiveUpdated:(void (^)(bool))isActiveUpdated rotationUpdated:(void (^)(int))rotationUpdated;

- (void)startCaptureWithDevice:(AVCaptureDevice *)device format:(AVCaptureDeviceFormat *)format fps:(NSInteger)fps;
- (void)stopCapture;
- (void)setIsEnabled:(bool)isEnabled;
- (void)setPreferredCaptureAspectRatio:(float)aspectRatio;
- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink;
- (int)getRotation;

- (void)addPreviewView:(VideoCaptureView *)previewView;
- (void)addDirectSink:(std::weak_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)directSink;

@end
#endif // WEBRTC_IOS
#endif
