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
#import "tgcalls/platform/darwin/DesktopSharingCapturer.h"
#import "tgcalls/desktop_capturer/DesktopCaptureSourceHelper.h"
#import "CustomExternalCapturer.h"
#import "VideoCMIOCapture.h"
#else
#import "VideoCameraCapturer.h"
#import "CustomExternalCapturer.h"
#endif
#import <AVFoundation/AVFoundation.h>

#import "VideoCaptureInterface.h"
#import "platform/PlatformInterface.h"

@interface VideoCapturerInterfaceImplSourceDescription : NSObject

@property (nonatomic, readonly) bool isFrontCamera;
@property (nonatomic, readonly) bool keepLandscape;
@property (nonatomic, readonly) NSString *deviceId;
@property (nonatomic, strong, readonly, nullable) AVCaptureDevice *device;
@property (nonatomic, strong, readonly, nullable) AVCaptureDeviceFormat *format;

@end

@implementation VideoCapturerInterfaceImplSourceDescription

- (instancetype)initWithIsFrontCamera:(bool)isFrontCamera keepLandscape:(bool)keepLandscape deviceId:(NSString *)deviceId device:(AVCaptureDevice * _Nullable)device format:(AVCaptureDeviceFormat * _Nullable)format {
    self = [super init];
    if (self != nil) {
        _isFrontCamera = isFrontCamera;
        _keepLandscape = keepLandscape;
        _deviceId = deviceId;
        _device = device;
        _format = format;
    }
    return self;
}

@end

@interface VideoCapturerInterfaceImplReference : NSObject {
#ifdef WEBRTC_IOS
    CustomExternalCapturer *_customExternalCapturer;
    VideoCameraCapturer *_videoCameraCapturer;
#else
    id<CapturerInterface> _videoCapturer;
#endif
}

@end

@implementation VideoCapturerInterfaceImplReference

- (id)videoCameraCapturer {
#ifdef WEBRTC_IOS
    return _videoCameraCapturer;
#else
    return _videoCapturer;
#endif
}

+ (AVCaptureDevice *)selectCapturerDeviceWithDeviceId:(NSString *)deviceId {
    AVCaptureDevice *selectedCamera = nil;

#ifdef WEBRTC_IOS
    bool useFrontCamera = ![deviceId hasPrefix:@"back"];
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

        NSArray *deviceComponents = [deviceId componentsSeparatedByString:@":"];
        if (deviceComponents.count == 2) {
            deviceId = deviceComponents[0];
        }
    //&& [devices[i] hasMediaType:AVMediaTypeVideo]
        NSArray<AVCaptureDevice *> *devices = [VideoCameraCapturer captureDevices];
        for (int i = 0; i < devices.count; i++) {
            if (devices[i].isConnected && !devices[i].isSuspended ) {
                if ([deviceId isEqualToString:@""] || [deviceId isEqualToString:devices[i].uniqueID]) {
                    selectedCamera = devices[i];
                    break;
                }
            }
        }
        if (selectedCamera == nil) {
            for (int i = 0; i < devices.count; i++) {
                if (devices[i].isConnected && !devices[i].isSuspended) {
                    selectedCamera = devices[i];
                    break;
                }
            }
        }
#endif

    return selectedCamera;
}

+ (AVCaptureDeviceFormat *)selectCaptureDeviceFormatForDevice:(AVCaptureDevice *)selectedCamera {
    NSMutableArray<AVCaptureDeviceFormat *> *sortedFormats = [NSMutableArray arrayWithArray:[[VideoCameraCapturer supportedFormatsForDevice:selectedCamera] sortedArrayUsingComparator:^NSComparisonResult(AVCaptureDeviceFormat* lhs, AVCaptureDeviceFormat *rhs) {
        int32_t width1 = CMVideoFormatDescriptionGetDimensions(lhs.formatDescription).width;
        int32_t width2 = CMVideoFormatDescriptionGetDimensions(rhs.formatDescription).width;
        return width1 < width2 ? NSOrderedAscending : NSOrderedDescending;
    }]];
    for (int i = (int)[sortedFormats count] - 1; i >= 0; i--) {
        if ([[sortedFormats[i] description] containsString:@"x420"]) {
            [sortedFormats removeObjectAtIndex:i];
        }
    }

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
            if (dimensions.width >= 720 && dimensions.height >= 720) {
                didSelectPreferredFormat = true;
                bestFormat = format;
                break;
            }
        }
    }
    if (!didSelectPreferredFormat) {
        for (AVCaptureDeviceFormat *format in sortedFormats) {
            CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription);
            if (dimensions.width >= 640 && dimensions.height >= 640) {
                didSelectPreferredFormat = true;
                bestFormat = format;
                break;
            }
        }
    }

    if (bestFormat == nil) {
        assert(false);
        return nil;
    }


    return bestFormat;
}

+ (VideoCapturerInterfaceImplSourceDescription *)selectCapturerDescriptionWithDeviceId:(NSString *)deviceId {
    if ([deviceId isEqualToString:@":ios_custom"]) {
        return [[VideoCapturerInterfaceImplSourceDescription alloc] initWithIsFrontCamera:false keepLandscape:false deviceId:deviceId device:nil format:nil];
    }

    if ([deviceId hasPrefix:@"desktop_capturer_"]) {
        return [[VideoCapturerInterfaceImplSourceDescription alloc] initWithIsFrontCamera:false keepLandscape:true deviceId: deviceId device: nil format: nil];
    }
    AVCaptureDevice *selectedCamera = [VideoCapturerInterfaceImplReference selectCapturerDeviceWithDeviceId:deviceId];

    if (selectedCamera == nil) {
        return [[VideoCapturerInterfaceImplSourceDescription alloc] initWithIsFrontCamera:![deviceId hasPrefix:@"back"] keepLandscape:[deviceId containsString:@"landscape"] deviceId: deviceId device: nil format: nil];
    }

    AVCaptureDeviceFormat *bestFormat = [VideoCapturerInterfaceImplReference selectCaptureDeviceFormatForDevice:selectedCamera];

    return [[VideoCapturerInterfaceImplSourceDescription alloc] initWithIsFrontCamera:![deviceId hasPrefix:@"back"] keepLandscape:[deviceId containsString:@"landscape"] deviceId:deviceId device:selectedCamera format:bestFormat];
}

- (instancetype)initWithSource:(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source sourceDescription:(VideoCapturerInterfaceImplSourceDescription *)sourceDescription isActiveUpdated:(void (^)(bool))isActiveUpdated rotationUpdated:(void (^)(int))rotationUpdated {
    self = [super init];
    if (self != nil) {
        assert([NSThread isMainThread]);

#ifdef WEBRTC_IOS
        if ([sourceDescription.deviceId isEqualToString:@":ios_custom"]) {
            _customExternalCapturer = [[CustomExternalCapturer alloc] initWithSource:source];
        } else {
            _videoCameraCapturer = [[VideoCameraCapturer alloc] initWithSource:source useFrontCamera:sourceDescription.isFrontCamera keepLandscape:sourceDescription.keepLandscape isActiveUpdated:isActiveUpdated rotationUpdated:rotationUpdated];
            [_videoCameraCapturer startCaptureWithDevice:sourceDescription.device format:sourceDescription.format fps:30];
        }
#else
        if (const auto desktopCaptureSource = tgcalls::DesktopCaptureSourceForKey([sourceDescription.deviceId UTF8String])) {
            DesktopSharingCapturer *sharing = [[DesktopSharingCapturer alloc] initWithSource:source captureSource:desktopCaptureSource];
            _videoCapturer = sharing;
        } else if (!tgcalls::ShouldBeDesktopCapture([sourceDescription.deviceId UTF8String])) {
            id<CapturerInterface> camera;
            if ([sourceDescription.device hasMediaType:AVMediaTypeMuxed]) {
                VideoCMIOCapture *value = [[VideoCMIOCapture alloc] initWithSource:source];
                [value setupCaptureWithDevice:sourceDescription.device];
                camera = value;
            } else {
                VideoCameraCapturer *value = [[VideoCameraCapturer alloc] initWithSource:source isActiveUpdated:isActiveUpdated];
                [value setupCaptureWithDevice:sourceDescription.device format:sourceDescription.format fps:30];
                camera = value;
            }
            _videoCapturer = camera;
        } else {
            _videoCapturer = nil;
        }
        if (_videoCapturer) {
            [_videoCapturer start];
        }
#endif

    }
    return self;
}

- (void)dealloc {
    assert([NSThread isMainThread]);

#ifdef WEBRTC_IOS
    [_videoCameraCapturer stopCapture];
#elif TARGET_OS_OSX
    if (_videoCapturer) {
        [_videoCapturer stop];
    }
#endif
}

-(void)setOnFatalError:(std::function<void()>)error {
#ifdef WEBRTC_IOS
#else
    if (_videoCapturer) {
        [_videoCapturer setOnFatalError:error];
    } else if (error) {
        error();
    }
#endif
}

-(void)setOnPause:(std::function<void(bool)>)pause {
#ifdef WEBRTC_IOS
#else
    if (_videoCapturer) {
        [_videoCapturer setOnPause:pause];
    } 
#endif
}

- (void)setIsEnabled:(bool)isEnabled {
#ifdef WEBRTC_IOS
    if (_videoCameraCapturer) {
        [_videoCameraCapturer setIsEnabled:isEnabled];
    }
#else
    if (_videoCapturer) {
        [_videoCapturer setIsEnabled:isEnabled];
    }
#endif
}

- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink {
#ifdef WEBRTC_IOS
    if (_videoCameraCapturer) {
        [_videoCameraCapturer setUncroppedSink:sink];
    }
#else
    if (_videoCapturer) {
        [_videoCapturer setUncroppedSink:sink];
    }
#endif
}

- (void)setPreferredCaptureAspectRatio:(float)aspectRatio {
#ifdef WEBRTC_IOS
    if (_videoCameraCapturer) {
        [_videoCameraCapturer setPreferredCaptureAspectRatio:aspectRatio];
    }
#else
    if (_videoCapturer) {
        [_videoCapturer setPreferredCaptureAspectRatio:aspectRatio];
    }
#endif
}

- (int)getRotation {
#ifdef WEBRTC_IOS
    if (_videoCameraCapturer) {
        return [_videoCameraCapturer getRotation];
    } else {
        return 0;
    }
#elif TARGET_OS_OSX
    return 0;
#else
    #error "Unsupported platform"
#endif
}

- (id)getInternalReference {
#ifdef WEBRTC_IOS
    if (_videoCameraCapturer) {
        return _videoCameraCapturer;
    } else if (_customExternalCapturer) {
        return _customExternalCapturer;
    } else {
        return nil;
    }
#endif
    return nil;
}

@end

@implementation VideoCapturerInterfaceImplHolder

@end

namespace tgcalls {

VideoCapturerInterfaceImpl::VideoCapturerInterfaceImpl(webrtc::scoped_refptr<webrtc::VideoTrackSourceInterface> source, std::string deviceId, std::function<void(VideoState)> stateUpdated, std::function<void(PlatformCaptureInfo)> captureInfoUpdated, std::pair<int, int> &outResolution) :
    _source(source) {
    VideoCapturerInterfaceImplSourceDescription *sourceDescription = [VideoCapturerInterfaceImplReference selectCapturerDescriptionWithDeviceId:[NSString stringWithUTF8String:deviceId.c_str()]];

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
        } rotationUpdated:^(int angle) {
            PlatformCaptureInfo info;
            bool isLandscape = angle == 180 || angle == 0;
            info.shouldBeAdaptedToReceiverAspectRate = !isLandscape;
            info.rotation = angle;
            captureInfoUpdated(info);
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

void VideoCapturerInterfaceImpl::withNativeImplementation(std::function<void(void *)> completion) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            completion((__bridge void *)[reference videoCameraCapturer]);
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

void VideoCapturerInterfaceImpl::setOnFatalError(std::function<void()> error) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            [reference setOnFatalError:error];
        }
    });
}

void VideoCapturerInterfaceImpl::setOnPause(std::function<void(bool)> pause) {
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_async(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            [reference setOnPause: pause];
        }
    });
}


int VideoCapturerInterfaceImpl::getRotation() {
    __block int value = 0;
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_sync(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            value = [reference getRotation];
        }
    });
    return value;
}

id VideoCapturerInterfaceImpl::getInternalReference() {
    __block id value = nil;
    VideoCapturerInterfaceImplHolder *implReference = _implReference;
    dispatch_sync(dispatch_get_main_queue(), ^{
        if (implReference.reference != nil) {
            VideoCapturerInterfaceImplReference *reference = (__bridge VideoCapturerInterfaceImplReference *)implReference.reference;
            value = [reference getInternalReference];
        }
    });
    return value;
}

} // namespace tgcalls
