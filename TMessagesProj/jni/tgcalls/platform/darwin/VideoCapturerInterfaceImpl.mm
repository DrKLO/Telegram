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

@interface VideoCapturerInterfaceImplReference : NSObject {
    VideoCameraCapturer *_videoCapturer;
}

@end

@implementation VideoCapturerInterfaceImplReference

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source useFrontCamera:(bool)useFrontCamera isActiveUpdated:(void (^)(bool))isActiveUpdated {
    self = [super init];
    if (self != nil) {
        assert([NSThread isMainThread]);
    #ifdef WEBRTC_IOS
        _videoCapturer = [[VideoCameraCapturer alloc] initWithSource:source useFrontCamera:useFrontCamera isActiveUpdated:isActiveUpdated];
    #else
        _videoCapturer = [[VideoCameraCapturer alloc] initWithSource:source isActiveUpdated:isActiveUpdated];
    #endif
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
        selectedCamera = [VideoCameraCapturer captureDevices].firstObject;
#endif
        //        NSLog(@"%@", selectedCamera);
        if (selectedCamera == nil) {
            return nil;
        }

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

        [_videoCapturer startCaptureWithDevice:selectedCamera format:bestFormat fps:30];
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

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, bool useFrontCamera, std::function<void(VideoState)> stateUpdated) :
    _source(source) {
    _implReference = [[VideoCapturerInterfaceImplHolder alloc] init];
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        VideoCapturerInterfaceImplReference *value = [[VideoCapturerInterfaceImplReference alloc] initWithSource:source useFrontCamera:useFrontCamera isActiveUpdated:^(bool isActive) {
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
