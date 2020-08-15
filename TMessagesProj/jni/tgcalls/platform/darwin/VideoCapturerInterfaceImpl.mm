#include "VideoCapturerInterfaceImpl.h"

#include "absl/strings/match.h"
#include "api/audio_codecs/audio_decoder_factory_template.h"
#include "api/audio_codecs/audio_encoder_factory_template.h"
#include "api/audio_codecs/opus/audio_decoder_opus.h"
#include "api/audio_codecs/opus/audio_encoder_opus.h"
#include "api/rtp_parameters.h"
#include "api/task_queue/default_task_queue_factory.h"
#include "media/base/codec.h"
#include "media/base/media_constants.h"
#include "media/engine/webrtc_media_engine.h"
#include "modules/audio_device/include/audio_device_default.h"
#include "rtc_base/task_utils/repeating_task.h"
#include "system_wrappers/include/field_trial.h"
#include "api/video/builtin_video_bitrate_allocator_factory.h"
#include "api/video/video_bitrate_allocation.h"

#include "sdk/objc/native/api/video_encoder_factory.h"
#include "sdk/objc/native/api/video_decoder_factory.h"

#include "sdk/objc/api/RTCVideoRendererAdapter.h"
#include "sdk/objc/native/api/video_frame.h"
#include "api/media_types.h"

#ifndef WEBRTC_IOS
#import "VideoCameraCapturerMac.h"
#else
#import "VideoCameraCapturer.h"
#endif
#import <AVFoundation/AVFoundation.h>

#import "VideoCaptureInterface.h"

@interface VideoCapturerInterfaceImplSourceDescription : NSObject

@property (nonatomic, readonly) bool isFrontCamera;
@property (nonatomic, strong, readonly, nonnull) AVCaptureDevice *device;
@property (nonatomic, strong, readonly, nonnull) AVCaptureDeviceFormat *format;

@end

@implementation VideoCapturerInterfaceImplSourceDescription

- (instancetype)initWithIsFrontCamera:(bool)isFrontCamera device:(AVCaptureDevice * _Nonnull)device format:(AVCaptureDeviceFormat * _Nonnull)format {
    self = [super init];
    if (self != nil) {
        _isFrontCamera = isFrontCamera;
        _device = device;
        _format = format;
    }
    return self;
}

@end

@interface VideoCapturerInterfaceImplReference : NSObject {
    VideoCameraCapturer *_videoCapturer;
}

@end

@implementation VideoCapturerInterfaceImplReference

+ (AVCaptureDevice *)selectCaptureDevice:(bool)useFrontCamera {
    AVCaptureDevice *selectedCamera = nil;

#ifdef WEBRTC_IOS
    AVCaptureDevice *frontCamera = nil;
    AVCaptureDevice *backCamera = nil;
    for (AVCaptureDevice *device in [VideoCameraCapturer captureDevices]) {
        if (device.position == AVCaptureDevicePositionFront) {
            frontCamera = device;
        } else if (device.position == AVCaptureDevicePositionBack) {
            backCamera = device;
        }
    }
    if (useFrontCamera && frontCamera != nil) {
        selectedCamera = frontCamera;
    } else {
        selectedCamera = backCamera;
    }
#else
        NSArray<AVCaptureDevice *> *devices = [VideoCameraCapturer captureDevices];
        for (int i = 0; i < devices.count; i++) {
            if ([_videoCapturer deviceIsCaptureCompitable:devices[i]]) {
                selectedCamera = devices[i];
                break;
            }
        }
#endif
    
    return selectedCamera;
}

+ (AVCaptureDeviceFormat *)selectCaptureDeviceFormatForDevice:(AVCaptureDevice *)selectedCamera {
    NSArray<AVCaptureDeviceFormat *> *sortedFormats = [[VideoCameraCapturer supportedFormatsForDevice:selectedCamera] sortedArrayUsingComparator:^NSComparisonResult(AVCaptureDeviceFormat* lhs, AVCaptureDeviceFormat *rhs) {
        int32_t width1 = CMVideoFormatDescriptionGetDimensions(lhs.formatDescription).width;
        int32_t width2 = CMVideoFormatDescriptionGetDimensions(rhs.formatDescription).width;
        return width1 < width2 ? NSOrderedAscending : NSOrderedDescending;
    }];

    AVCaptureDeviceFormat *bestFormat = sortedFormats.firstObject;
    
    bool didSelectPreferredFormat = false;
    #ifdef WEBRTC_IOS
    for (AVCaptureDeviceFormat *format in sortedFormats) {
        CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription);
        if (dimensions.width == 1280 && dimensions.height == 720) {
            if (format.videoFieldOfView > 60.0f && format.videoSupportedFrameRateRanges.lastObject.maxFrameRate == 30) {
                didSelectPreferredFormat = true;
                bestFormat = format;
                break;
            }
        }
    }
    #endif
    if (!didSelectPreferredFormat) {
        for (AVCaptureDeviceFormat *format in sortedFormats) {
            CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription);
            if (dimensions.width >= 1000 || dimensions.height >= 1000) {
                bestFormat = format;
                break;
            }
        }
    }

    if (bestFormat == nil) {
        assert(false);
        return nil;
    }

    AVFrameRateRange *frameRateRange = [[bestFormat.videoSupportedFrameRateRanges sortedArrayUsingComparator:^NSComparisonResult(AVFrameRateRange *lhs, AVFrameRateRange *rhs) {
        if (lhs.maxFrameRate < rhs.maxFrameRate) {
            return NSOrderedAscending;
        } else {
            return NSOrderedDescending;
        }
    }] lastObject];

    if (frameRateRange == nil) {
        assert(false);
        return nil;
    }
    
    return bestFormat;
}

+ (VideoCapturerInterfaceImplSourceDescription *)selectCapturerDescription:(bool)useFrontCamera {
    AVCaptureDevice *selectedCamera = [VideoCapturerInterfaceImplReference selectCaptureDevice:useFrontCamera];
    
    if (selectedCamera == nil) {
        return nil;
    }

    AVCaptureDeviceFormat *bestFormat = [VideoCapturerInterfaceImplReference selectCaptureDeviceFormatForDevice:selectedCamera];
    
    if (bestFormat == nil) {
        return nil;
    }
    
    return [[VideoCapturerInterfaceImplSourceDescription alloc] initWithIsFrontCamera:useFrontCamera device:selectedCamera format:bestFormat];
}

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source sourceDescription:(VideoCapturerInterfaceImplSourceDescription *)sourceDescription isActiveUpdated:(void (^)(bool))isActiveUpdated {
    self = [super init];
    if (self != nil) {
        assert([NSThread isMainThread]);
        
    #ifdef WEBRTC_IOS
        _videoCapturer = [[VideoCameraCapturer alloc] initWithSource:source useFrontCamera:sourceDescription.isFrontCamera isActiveUpdated:isActiveUpdated];
    #else
        _videoCapturer = [[VideoCameraCapturer alloc] initWithSource:source isActiveUpdated:isActiveUpdated];
    #endif

        [_videoCapturer startCaptureWithDevice:sourceDescription.device format:sourceDescription.format fps:30];
    }
    return self;
}

- (void)dealloc {
    assert([NSThread isMainThread]);

    [_videoCapturer stopCapture];
}

- (void)setIsEnabled:(bool)isEnabled {
    [_videoCapturer setIsEnabled:isEnabled];
}

- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink {
    [_videoCapturer setUncroppedSink:sink];
}

- (void)setPreferredCaptureAspectRatio:(float)aspectRatio {
    [_videoCapturer setPreferredCaptureAspectRatio:aspectRatio];
}

@end

@implementation VideoCapturerInterfaceImplHolder

@end

namespace tgcalls {

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated, std::pair<int, int> &outResolution) :
    _source(source) {
    VideoCapturerInterfaceImplSourceDescription *sourceDescription = [VideoCapturerInterfaceImplReference selectCapturerDescription:useFrontCamera];
        
    CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(sourceDescription.format.formatDescription);
    #ifdef WEBRTC_IOS
    outResolution.first = dimensions.height;
    outResolution.second = dimensions.width;
    #else
    outResolution.first = dimensions.width;
    outResolution.second = dimensions.height;
    #endif
        
    _implReference = [[VideoCapturerInterfaceImplHolder alloc] init];
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        VideoCapturerInterfaceImplReference *value = [[VideoCapturerInterfaceImplReference alloc] initWithSource:source sourceDescription:sourceDescription isActiveUpdated:^(bool isActive) {
            stateUpdated(isActive ? VideoState::Active : VideoState::Paused);
        }];
        if (value != nil) {
            implReference.reference = (void *)CFBridgingRetain(value);
        }
    });
}

VideoCapturerInterfaceImpl::~VideoCapturerInterfaceImpl() {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            CFBridgingRelease(implReference.reference);
        }
    });
}

void VideoCapturerInterfaceImpl::setState(VideoState state) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            [reference setIsEnabled:(state == VideoState::Active)];
        }
    });
}

void VideoCapturerInterfaceImpl::setPreferredCaptureAspectRatio(float aspectRatio) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            [reference setPreferredCaptureAspectRatio:aspectRatio];
        }
    });
}

void VideoCapturerInterfaceImpl::setUncroppedOutput(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> sink) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            [reference setUncroppedSink:sink];
        }
    });
}

} // namespace tgcalls
