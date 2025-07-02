#include "VideoCameraCapturerMac.h"

#import <AVFoundation/AVFoundation.h>
#import  "TGRTCCVPixelBuffer.h"
#include "rtc_base/logging.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrameBuffer.h"
#import "components/video_frame_buffer/RTCCVPixelBuffer.h"
#import "sdk/objc/native/src/objc_video_track_source.h"
#import "sdk/objc/native/src/objc_frame_buffer.h"
#import <CoreMediaIO/CMIOHardware.h>
#import "TGCMIODevice.h"
#import "helpers/AVCaptureSession+DevicePosition.h"
#import "helpers/RTCDispatcher+Private.h"
#import "base/RTCVideoFrame.h"

#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "pc/video_track_source_proxy.h"
#include "third_party/libyuv/include/libyuv.h"
#include "DarwinVideoSource.h"

struct CameraFrameSize {
    int width = 0;
    int height = 0;
};

CameraFrameSize AspectFitted(CameraFrameSize from, CameraFrameSize to) {
    double scale = std::min(
        from.width / std::max(1., double(to.width)),
        from.height / std::max(1., double(to.height)));
    return {
        int(std::ceil(to.width * scale)),
        int(std::ceil(to.height * scale))
    };
}

static const int64_t kNanosecondsPerSecond = 1000000000;

static tgcalls::DarwinVideoTrackSource *getObjCVideoSource(const rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> nativeSource) {
    webrtc::VideoTrackSourceProxy *proxy_source =
    static_cast<webrtc::VideoTrackSourceProxy *>(nativeSource.get());
    return static_cast<tgcalls::DarwinVideoTrackSource *>(proxy_source->internal());
}


@interface RTCCVPixelBuffer (CustomCropping)

@end

@implementation RTCCVPixelBuffer (CustomCropping)

- (BOOL)custom_cropAndScaleTo:(CVPixelBufferRef)outputPixelBuffer
               withTempBuffer:(nullable uint8_t*)tmpBuffer {
    const OSType srcPixelFormat = CVPixelBufferGetPixelFormatType(self.pixelBuffer);
    const OSType dstPixelFormat = CVPixelBufferGetPixelFormatType(outputPixelBuffer);

    switch (srcPixelFormat) {
        case kCVPixelFormatType_420YpCbCr8BiPlanarFullRange:
        case kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange: {
            size_t dstWidth = CVPixelBufferGetWidth(outputPixelBuffer);
            size_t dstHeight = CVPixelBufferGetHeight(outputPixelBuffer);
            if (dstWidth > 0 && dstHeight > 0) {
                RTC_DCHECK(dstPixelFormat == kCVPixelFormatType_420YpCbCr8BiPlanarFullRange ||
                           dstPixelFormat == kCVPixelFormatType_420YpCbCr8BiPlanarVideoRange);
                if ([self requiresScalingToWidth:(int)dstWidth height:(int)dstHeight]) {
                    RTC_DCHECK(tmpBuffer);
                }
                [self custom_cropAndScaleNV12To:outputPixelBuffer withTempBuffer:tmpBuffer];
            }
            break;
        }
        case kCVPixelFormatType_32BGRA:
        case kCVPixelFormatType_32ARGB: {
            RTC_DCHECK(srcPixelFormat == dstPixelFormat);
            [self custom_cropAndScaleARGBTo:outputPixelBuffer];
            break;
        }
        default: { RTC_DCHECK_NOTREACHED() << "Unsupported pixel format."; }
    }

    return YES;
}

- (void)custom_cropAndScaleNV12To:(CVPixelBufferRef)outputPixelBuffer withTempBuffer:(uint8_t*)tmpBuffer {
    // Prepare output pointers.
    CVReturn cvRet = CVPixelBufferLockBaseAddress(outputPixelBuffer, 0);
    if (cvRet != kCVReturnSuccess) {
        RTC_LOG(LS_ERROR) << "Failed to lock base address: " << cvRet;
    }
    const int dstWidth = (int)CVPixelBufferGetWidth(outputPixelBuffer);
    const int dstHeight = (int)CVPixelBufferGetHeight(outputPixelBuffer);
    uint8_t* dstY =
    reinterpret_cast<uint8_t*>(CVPixelBufferGetBaseAddressOfPlane(outputPixelBuffer, 0));
    const int dstYStride = (int)CVPixelBufferGetBytesPerRowOfPlane(outputPixelBuffer, 0);
    uint8_t* dstUV =
    reinterpret_cast<uint8_t*>(CVPixelBufferGetBaseAddressOfPlane(outputPixelBuffer, 1));
    const int dstUVStride = (int)CVPixelBufferGetBytesPerRowOfPlane(outputPixelBuffer, 1);

    // Prepare source pointers.
    CVPixelBufferLockBaseAddress(self.pixelBuffer, kCVPixelBufferLock_ReadOnly);
    const uint8_t* srcY = static_cast<uint8_t*>(CVPixelBufferGetBaseAddressOfPlane(self.pixelBuffer, 0));
    const int srcYStride = (int)CVPixelBufferGetBytesPerRowOfPlane(self.pixelBuffer, 0);
    const uint8_t* srcUV = static_cast<uint8_t*>(CVPixelBufferGetBaseAddressOfPlane(self.pixelBuffer, 1));
    const int srcUVStride = (int)CVPixelBufferGetBytesPerRowOfPlane(self.pixelBuffer, 1);

    // Crop just by modifying pointers.
    srcY += srcYStride * self.cropY + self.cropX;
    srcUV += srcUVStride * (self.cropY / 2) + self.cropX;

    webrtc::NV12Scale(tmpBuffer,
                      srcY,
                      srcYStride,
                      srcUV,
                      srcUVStride,
                      self.cropWidth,
                      self.cropHeight,
                      dstY,
                      dstYStride,
                      dstUV,
                      dstUVStride,
                      dstWidth,
                      dstHeight);

    CVPixelBufferUnlockBaseAddress(self.pixelBuffer, kCVPixelBufferLock_ReadOnly);
    CVPixelBufferUnlockBaseAddress(outputPixelBuffer, 0);
}

- (void)custom_cropAndScaleARGBTo:(CVPixelBufferRef)outputPixelBuffer {
    // Prepare output pointers.
    CVReturn cvRet = CVPixelBufferLockBaseAddress(outputPixelBuffer, 0);
    if (cvRet != kCVReturnSuccess) {
        RTC_LOG(LS_ERROR) << "Failed to lock base address: " << cvRet;
    }
    const int dstWidth = (int)CVPixelBufferGetWidth(outputPixelBuffer);
    const int dstHeight = (int)CVPixelBufferGetHeight(outputPixelBuffer);

    uint8_t* dst = reinterpret_cast<uint8_t*>(CVPixelBufferGetBaseAddress(outputPixelBuffer));
    const int dstStride = (int)CVPixelBufferGetBytesPerRow(outputPixelBuffer);

    // Prepare source pointers.
    CVPixelBufferLockBaseAddress(self.pixelBuffer, kCVPixelBufferLock_ReadOnly);
    const uint8_t* src = static_cast<uint8_t*>(CVPixelBufferGetBaseAddress(self.pixelBuffer));
    const int srcStride = (int)CVPixelBufferGetBytesPerRow(self.pixelBuffer);

    // Crop just by modifying pointers. Need to ensure that src pointer points to a byte corresponding
    // to the start of a new pixel (byte with B for BGRA) so that libyuv scales correctly.
    const int bytesPerPixel = 4;
    src += srcStride * self.cropY + (self.cropX * bytesPerPixel);

    // kCVPixelFormatType_32BGRA corresponds to libyuv::FOURCC_ARGB
    libyuv::ARGBScale(src,
                      srcStride,
                      self.cropWidth,
                      self.cropHeight,
                      dst,
                      dstStride,
                      dstWidth,
                      dstHeight,
                      libyuv::kFilterBox);

    CVPixelBufferUnlockBaseAddress(self.pixelBuffer, kCVPixelBufferLock_ReadOnly);
    CVPixelBufferUnlockBaseAddress(outputPixelBuffer, 0);
}

@end



@interface VideoCameraCapturer () <AVCaptureVideoDataOutputSampleBufferDelegate> {
    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;

    dispatch_queue_t _frameQueue;
    AVCaptureDevice *_currentDevice;
    AVCaptureInput *_currentInput;

    // Live on RTCDispatcherTypeCaptureSession.
    BOOL _hasRetriedOnFatalError;
    BOOL _hadFatalError;
    BOOL _isRunning;

    BOOL _shouldBeMirrored;

    // Live on RTCDispatcherTypeCaptureSession and main thread.
    std::atomic<bool> _willBeRunning;

    AVCaptureVideoDataOutput *_videoDataOutput;
    AVCaptureSession *_captureSession;

    AVCaptureConnection *_videoConnection;
    AVCaptureDevice *_videoDevice;
    AVCaptureDeviceInput *_videoInputDevice;
    FourCharCode _preferredOutputPixelFormat;
    FourCharCode _outputPixelFormat;
    RTCVideoRotation _rotation;

    // Live on mainThread.
    void (^_isActiveUpdated)(bool);
    bool _isActiveValue;
    bool _inForegroundValue;

    // Live on frameQueue and main thread.
    std::atomic<bool> _isPaused;
    std::atomic<int> _skippedFrame;

    // Live on frameQueue;
    float _aspectRatio;
    std::vector<uint8_t> _croppingBuffer;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _uncroppedSink;
    std::function<void ()> _onFatalError;
    int _warmupFrameCount;
}

@end

@implementation VideoCameraCapturer

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source isActiveUpdated:(void (^)(bool))isActiveUpdated {
    self = [super init];
    if (self != nil) {
        _source = source;
        _isActiveUpdated = [isActiveUpdated copy];
        _isActiveValue = true;
        _inForegroundValue = true;
        _isPaused = false;
        _skippedFrame = 0;
        _rotation = RTCVideoRotation_0;

        _warmupFrameCount = 100;

        if (![self setupCaptureSession:[[AVCaptureSession alloc] init]]) {
            return nil;
        }
    }
    return self;
}

- (void)dealloc {


    NSAssert(!_willBeRunning, @"Session was still running in RTCCameraVideoCapturer dealloc. Forgot to call stopCapture?");
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

+ (NSArray<AVCaptureDevice *> *)captureDevices {
    AVCaptureDevice * defaultDevice = [AVCaptureDevice defaultDeviceWithMediaType:AVMediaTypeVideo];
    NSMutableArray<AVCaptureDevice *> * devices = [[AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo] mutableCopy];

    [devices addObjectsFromArray:[AVCaptureDevice devicesWithMediaType:AVMediaTypeMuxed]];

    if ([devices count] > 0) {
        [devices insertObject:defaultDevice atIndex:0];
    }

    return devices;
}

- (BOOL)deviceIsCaptureCompitable:(AVCaptureDevice *)device {
    if (![device isConnected] || [device isSuspended]) {
        return NO;
    }
    AVCaptureDeviceInput *input = [AVCaptureDeviceInput deviceInputWithDevice:device error:nil];

    return [_captureSession canAddInput:input];
}

+ (NSArray<AVCaptureDeviceFormat *> *)supportedFormatsForDevice:(AVCaptureDevice *)device {
  // Support opening the device in any format. We make sure it's converted to a format we
  // can handle, if needed, in the method `-setupVideoDataOutput`.
  return device.formats;
}

- (FourCharCode)preferredOutputPixelFormat {
  return _preferredOutputPixelFormat;
}

- (void)setupCaptureWithDevice:(AVCaptureDevice *)device
                        format:(AVCaptureDeviceFormat *)format
                           fps:(NSInteger)fps {
  [self setupCaptureWithDevice:device format:format fps:fps completionHandler:nil];
}

-(void)setOnFatalError:(std::function<void ()>)error {
    if (!self->_hadFatalError) {
      _onFatalError = std::move(error);
    } else if (error) {
      error();
    }
}

-(void)setOnPause:(std::function<void (bool)>)pause {

}

- (void)stop {
    _isActiveUpdated = nil;
    [self stopCaptureWithCompletionHandler:nil];
}


- (void)setIsEnabled:(bool)isEnabled {
    BOOL updated = _isPaused != !isEnabled;
    _isPaused = !isEnabled;
    _skippedFrame = 0;
    if (updated) {
        if (_isPaused) {
//            [RTCDispatcher
//             dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//             block:^{
                 [self->_captureSession stopRunning];
                 self->_isRunning = NO;
//             }];
        } else {
//            [RTCDispatcher
//             dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//             block:^{
                 [self->_captureSession startRunning];
                 self->_isRunning = YES;
//             }];
        }
    }

    [self updateIsActiveValue];
}


- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink {
	dispatch_async(self.frameQueue, ^{
        self->_uncroppedSink = sink;
    });
}

- (void)setPreferredCaptureAspectRatio:(float)aspectRatio {
	dispatch_async(self.frameQueue, ^{
        self->_aspectRatio = aspectRatio;
    });
}

- (void)updateIsActiveValue {
    bool isActive = _inForegroundValue && !_isPaused;
    if (isActive != _isActiveValue) {
        _isActiveValue = isActive;
        if (_isActiveUpdated) {
            _isActiveUpdated(_isActiveValue);
        }
    }
}


- (void)setupCaptureWithDevice:(AVCaptureDevice *)device
                        format:(AVCaptureDeviceFormat *)format
                           fps:(NSInteger)fps
             completionHandler:(nullable void (^)(NSError *))completionHandler {


    CMIOObjectPropertyAddress latency_pa = {
        kCMIODevicePropertyLatency,
        kCMIOObjectPropertyScopeWildcard,
        kCMIOObjectPropertyElementWildcard
    };
    UInt32 dataSize = 0;

    NSNumber *_connectionID = ((NSNumber *)[device valueForKey:@"_connectionID"]);

    CMIODeviceID deviceId = (CMIODeviceID)[_connectionID intValue];

    if (device) {
        if (CMIOObjectGetPropertyDataSize(deviceId, &latency_pa, 0, nil, &dataSize) == noErr) {
            _shouldBeMirrored = NO;
        } else {
            _shouldBeMirrored = YES;
        }
    } else {
        _shouldBeMirrored = [device hasMediaType:AVMediaTypeVideo];
    }
//  [RTCDispatcher
//      dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//   block:^{
      RTCLogInfo("startCaptureWithDevice %@ @ %ld fps", format, (long)fps);
    NSError *error = nil;

      self->_currentDevice = device;


      self->_currentInput = [[AVCaptureDeviceInput alloc] initWithDevice:device error:&error];
      if (![self->_currentDevice lockForConfiguration:&error]) {
          RTCLogError(@"Failed to lock device %@. Error: %@",
                      self->_currentDevice,
                      error.userInfo);
          if (completionHandler) {
              completionHandler(error);
          }
          self->_willBeRunning = false;
          return;
      }
      [self updateDeviceCaptureFormat:format fps:fps];
      [self updateVideoDataOutputPixelFormat:format];
      [self->_currentDevice unlockForConfiguration];
      [self reconfigureCaptureSessionInput];
      if (completionHandler) {
          completionHandler(nil);
      }
//  }];
}

-(void)start {
    _willBeRunning = true;
//    [RTCDispatcher
//     dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//     block:^{
         [self->_captureSession startRunning];
         self->_isRunning = YES;
//     }];
}



- (void)stopCaptureWithCompletionHandler:(nullable void (^)(void))completionHandler {
  _willBeRunning = false;
//  [RTCDispatcher
//   dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//   block:^{
      RTCLogInfo("Stop");
      self->_currentDevice = nil;
      for (AVCaptureDeviceInput *oldInput in [self->_captureSession.inputs copy]) {
          [self->_captureSession removeInput:oldInput];
      }
      [self->_captureSession stopRunning];

      self->_isRunning = NO;
      if (completionHandler) {
          completionHandler();
      }
//  }];
}


#pragma mark AVCaptureVideoDataOutputSampleBufferDelegate

- (void)captureOutput:(AVCaptureOutput *)captureOutput
    didOutputSampleBuffer:(CMSampleBufferRef)sampleBuffer
           fromConnection:(AVCaptureConnection *)connection {
    NSParameterAssert(captureOutput == _videoDataOutput);

    int minWarmupFrameCount = 12;
    _warmupFrameCount++;
    if (_warmupFrameCount < minWarmupFrameCount) {
        return;
    }

    if (CMSampleBufferGetNumSamples(sampleBuffer) != 1 || !CMSampleBufferIsValid(sampleBuffer) ||
        !CMSampleBufferDataIsReady(sampleBuffer)) {
        return;
    }

    CVPixelBufferRef pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer);
    if (pixelBuffer == nil) {
        return;
    }

    int width = (int)CVPixelBufferGetWidth(pixelBuffer);
    int height = (int)CVPixelBufferGetHeight(pixelBuffer);

    CameraFrameSize fittedSize = { width, height };

    fittedSize.width -= (fittedSize.width % 32);
    fittedSize.height -= (fittedSize.height % 4);

    TGRTCCVPixelBuffer *rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer adaptedWidth:fittedSize.width adaptedHeight:fittedSize.height cropWidth:width cropHeight:height cropX:0 cropY:0];

    rtcPixelBuffer.shouldBeMirrored = _shouldBeMirrored;

	if (!_isPaused && _uncroppedSink) {
        int64_t timeStampNs = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) *
        kNanosecondsPerSecond;
        RTCVideoFrame *frame = [[RTCVideoFrame alloc] initWithBuffer:rtcPixelBuffer
                                                            rotation:_rotation
                                                         timeStampNs:timeStampNs];

        const int64_t timestamp_us = frame.timeStampNs / rtc::kNumNanosecsPerMicrosec;

        rtc::scoped_refptr<webrtc::VideoFrameBuffer> buffer;
        buffer = new rtc::RefCountedObject<webrtc::ObjCFrameBuffer>(frame.buffer);

        webrtc::VideoRotation rotation = static_cast<webrtc::VideoRotation>(frame.rotation);

        _uncroppedSink->OnFrame(webrtc::VideoFrame::Builder()
                                .set_video_frame_buffer(buffer)
                                .set_rotation(rotation)
                                .set_timestamp_us(timestamp_us)
                                .build());
    }


    int64_t timeStampNs = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) *
    kNanosecondsPerSecond;
    RTCVideoFrame *videoFrame = [[RTCVideoFrame alloc] initWithBuffer:rtcPixelBuffer
                                                             rotation:_rotation
                                                          timeStampNs:timeStampNs];
    if (!_isPaused) {
        getObjCVideoSource(_source)->OnCapturedFrame(videoFrame);
    }
    _skippedFrame = MIN(_skippedFrame + 1, 16);
}

- (void)captureOutput:(AVCaptureOutput *)captureOutput
    didDropSampleBuffer:(CMSampleBufferRef)sampleBuffer
         fromConnection:(AVCaptureConnection *)connection {
  NSString *droppedReason =
      (__bridge NSString *)CMGetAttachment(sampleBuffer, kCMSampleBufferAttachmentKey_DroppedFrameReason, nil);
  RTCLogError(@"Dropped sample buffer. Reason: %@", droppedReason);
}

#pragma mark - AVCaptureSession notifications

- (void)handleCaptureSessionInterruption:(NSNotification *)notification {

}

- (void)handleCaptureSessionInterruptionEnded:(NSNotification *)notification {
    RTCLog(@"Capture session interruption ended.");
}

- (void)handleCaptureSessionRuntimeError:(NSNotification *)notification {
    NSError *error = [notification.userInfo objectForKey:AVCaptureSessionErrorKey];
    RTCLogError(@"Capture session runtime error: %@", error);

//    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//                                 block:^{
        [self handleFatalError];
//    }];
}

- (void)handleCaptureSessionDidStartRunning:(NSNotification *)notification {
    RTCLog(@"Capture session started.");

//    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//                                 block:^{
        // If we successfully restarted after an unknown error,
        // allow future retries on fatal errors.
        self->_hasRetriedOnFatalError = NO;
        self->_hadFatalError = NO;
//    }];


    _inForegroundValue = true;
    [self updateIsActiveValue];
}

- (void)handleCaptureSessionDidStopRunning:(NSNotification *)notification {
  RTCLog(@"Capture session stopped.");
    _inForegroundValue = false;
    [self updateIsActiveValue];

}

- (void)handleFatalError {
//    [RTCDispatcher
//     dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//     block:^{
        if (!self->_hasRetriedOnFatalError) {
            RTCLogWarning(@"Attempting to recover from fatal capture error.");
            [self handleNonFatalError];
            self->_warmupFrameCount = 0;
            self->_hasRetriedOnFatalError = YES;
        } else {
            RTCLogError(@"Previous fatal error recovery failed.");
            if (_onFatalError) {
                _onFatalError();
            } else {
                self->_hadFatalError = YES;
            }
        }
//    }];
}

- (void)handleNonFatalError {
//    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//                                 block:^{
        RTCLog(@"Restarting capture session after error.");
        if (self->_isRunning) {
            self->_warmupFrameCount = 0;
            [self->_captureSession startRunning];
        }
//    }];
}

#pragma mark - UIApplication notifications

- (void)handleApplicationDidBecomeActive:(NSNotification *)notification {
//    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
//                                 block:^{
        if (self->_isRunning && !self->_captureSession.isRunning) {
            RTCLog(@"Restarting capture session on active.");
            self->_warmupFrameCount = 0;
            [self->_captureSession startRunning];
        }
//    }];
}

#pragma mark - Private

- (dispatch_queue_t)frameQueue {
    if (!_frameQueue) {
        _frameQueue =
        dispatch_queue_create("org.webrtc.cameravideocapturer.video", DISPATCH_QUEUE_SERIAL);
        dispatch_set_target_queue(_frameQueue,
                                  dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0));
    }
    return _frameQueue;
}

- (BOOL)setupCaptureSession:(AVCaptureSession *)captureSession {
    NSAssert(_captureSession == nil, @"Setup capture session called twice.");
    _captureSession = captureSession;

    [self setupVideoDataOutput];
    // Add the output.
    if (![_captureSession canAddOutput:_videoDataOutput]) {
        RTCLogError(@"Video data output unsupported.");
        return NO;
    }
    [_captureSession addOutput:_videoDataOutput];


    return YES;
}



- (void)setupVideoDataOutput {
    NSAssert(_videoDataOutput == nil, @"Setup video data output called twice.");
    AVCaptureVideoDataOutput *videoDataOutput = [[AVCaptureVideoDataOutput alloc] init];

    // `videoDataOutput.availableVideoCVPixelFormatTypes` returns the pixel formats supported by the
    // device with the most efficient output format first. Find the first format that we support.
    NSSet<NSNumber *> *supportedPixelFormats = [RTCCVPixelBuffer supportedPixelFormats];
    NSMutableOrderedSet *availablePixelFormats =
    [NSMutableOrderedSet orderedSetWithArray:videoDataOutput.availableVideoCVPixelFormatTypes];
    [availablePixelFormats intersectSet:supportedPixelFormats];
    NSNumber *pixelFormat = availablePixelFormats.firstObject;
    NSAssert(pixelFormat, @"Output device has no supported formats.");

    _preferredOutputPixelFormat = [pixelFormat unsignedIntValue];
    _outputPixelFormat = _preferredOutputPixelFormat;
    videoDataOutput.videoSettings = @{(NSString *)kCVPixelBufferPixelFormatTypeKey : pixelFormat};
    videoDataOutput.alwaysDiscardsLateVideoFrames = YES;

    [videoDataOutput setSampleBufferDelegate:self queue:self.frameQueue];
    _videoDataOutput = videoDataOutput;
}

- (void)updateVideoDataOutputPixelFormat:(AVCaptureDeviceFormat *)format {
    FourCharCode mediaSubType = CMFormatDescriptionGetMediaSubType(format.formatDescription);
    if (![[RTCCVPixelBuffer supportedPixelFormats] containsObject:@(mediaSubType)]) {
        mediaSubType = _preferredOutputPixelFormat;
    }
    CMVideoDimensions dimensions = CMVideoFormatDescriptionGetDimensions(format.formatDescription);

    if (mediaSubType != _outputPixelFormat) {
        _outputPixelFormat = mediaSubType;
        _videoDataOutput.videoSettings =
        @{ (NSString *)kCVPixelBufferPixelFormatTypeKey : @(mediaSubType) };
    } else {
//        _videoDataOutput.videoSettings =
//        @{ (NSString *)kCVPixelBufferWidthKey: @(dimensions.width), (NSString *)kCVPixelBufferHeightKey: @(dimensions.height) };
    }
    AVCaptureConnection *connection = [_videoDataOutput connectionWithMediaType:AVMediaTypeVideo];

}

#pragma mark - Private, called inside capture queue

- (void)updateDeviceCaptureFormat:(AVCaptureDeviceFormat *)format fps:(NSInteger)fps {
//    NSAssert([RTCDispatcher isOnQueueForType:RTCDispatcherTypeCaptureSession],
//             @"updateDeviceCaptureFormat must be called on the capture queue.");
    @try {
        _currentDevice.activeFormat = format;
        if (format.videoSupportedFrameRateRanges.count > 0) {
            int target = 24;
            int closest = -1;
            CMTime result;
            for (int i = 0; i < format.videoSupportedFrameRateRanges.count; i++) {
                const auto rateRange = format.videoSupportedFrameRateRanges[i];
                int gap = abs(rateRange.minFrameRate - target);
                if (gap <= closest || closest == -1) {
                    closest = gap;
                    result = rateRange.maxFrameDuration;
                }
            }
            if (closest >= 0) {
                _currentDevice.activeVideoMaxFrameDuration = result;
            }
        }
    } @catch (NSException *exception) {
        RTCLogError(@"Failed to set active format!\n User info:%@", exception.userInfo);
        return;
    }
}

- (void)reconfigureCaptureSessionInput {
//    NSAssert([RTCDispatcher isOnQueueForType:RTCDispatcherTypeCaptureSession],
//             @"reconfigureCaptureSessionInput must be called on the capture queue.");
    NSError *error = nil;
    AVCaptureInput *input = _currentInput;
    if (!input) {
        RTCLogError(@"Failed to create front camera input: %@", error.localizedDescription);
        return;
    }
    [_captureSession beginConfiguration];
    for (AVCaptureInput *oldInput in [_captureSession.inputs copy]) {
        [_captureSession removeInput:oldInput];
    }
    if ([_captureSession canAddInput:input]) {
        [_captureSession addInput:input];
    } else {
        RTCLogError(@"Cannot add camera as an input to the session.");
    }
    [_captureSession commitConfiguration];
}


@end

