#include "VideoCameraCapturer.h"

#import <AVFoundation/AVFoundation.h>

#include "rtc_base/logging.h"
#import "base/RTCLogging.h"
#import "base/RTCVideoFrameBuffer.h"
#import "TGRTCCVPixelBuffer.h"
#import "sdk/objc/native/src/objc_video_track_source.h"
#import "sdk/objc/native/src/objc_frame_buffer.h"
#import "api/video_track_source_proxy.h"

#import "helpers/UIDevice+RTCDevice.h"

#import "helpers/AVCaptureSession+DevicePosition.h"
#import "helpers/RTCDispatcher+Private.h"
#import "base/RTCVideoFrame.h"

#include "common_video/libyuv/include/webrtc_libyuv.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "third_party/libyuv/include/libyuv.h"

static const int64_t kNanosecondsPerSecond = 1000000000;

static webrtc::ObjCVideoTrackSource *getObjCVideoSource(const rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> nativeSource) {
    webrtc::VideoTrackSourceProxy *proxy_source =
    static_cast<webrtc::VideoTrackSourceProxy *>(nativeSource.get());
    return static_cast<webrtc::ObjCVideoTrackSource *>(proxy_source->internal());
}

//TODO: investigate the green edge after scaling, likely related to padding
/*@interface RTCCVPixelBuffer (CustomCropping)

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
        default: { RTC_NOTREACHED() << "Unsupported pixel format."; }
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

@end*/

@interface VideoCameraCapturer () <AVCaptureVideoDataOutputSampleBufferDelegate> {
    rtc::scoped_refptr<webrtc::VideoTrackSourceInterface> _source;
    
    // Live on main thread.
    bool _isFrontCamera;
    
    dispatch_queue_t _frameQueue;

    // Live on RTCDispatcherTypeCaptureSession.
    AVCaptureDevice *_currentDevice;
    BOOL _hasRetriedOnFatalError;
    BOOL _isRunning;

	// Live on RTCDispatcherTypeCaptureSession and main thread.
	std::atomic<bool> _willBeRunning;
    
    AVCaptureVideoDataOutput *_videoDataOutput;
    AVCaptureSession *_captureSession;
    FourCharCode _preferredOutputPixelFormat;
    FourCharCode _outputPixelFormat;
    RTCVideoRotation _rotation;
    UIDeviceOrientation _orientation;
    bool _rotationLock;
    
    // Live on mainThread.
    void (^_isActiveUpdated)(bool);
    bool _isActiveValue;
    bool _inForegroundValue;
    
    // Live on frameQueue and main thread.
    std::atomic<bool> _isPaused;

    // Live on frameQueue.
    float _aspectRatio;
    std::vector<uint8_t> _croppingBuffer;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> _uncroppedSink;
    
    // Live on frameQueue and RTCDispatcherTypeCaptureSession.
    std::atomic<int> _warmupFrameCount;
}

@end

@implementation VideoCameraCapturer

- (instancetype)initWithSource:(rtc::scoped_refptr<webrtc::VideoTrackSourceInterface>)source useFrontCamera:(bool)useFrontCamera isActiveUpdated:(void (^)(bool))isActiveUpdated {
    self = [super init];
    if (self != nil) {
        _source = source;
        _isFrontCamera = useFrontCamera;
        _isActiveValue = true;
        _inForegroundValue = true;
        _isPaused = false;
        _isActiveUpdated = [isActiveUpdated copy];
        
        _warmupFrameCount = 100;
        
#if TARGET_OS_IPHONE
        _rotationLock = true;
#endif
        
        if (![self setupCaptureSession:[[AVCaptureSession alloc] init]]) {
            return nil;
        }
        
        NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
        _orientation = UIDeviceOrientationPortrait;
        _rotation = RTCVideoRotation_90;
        [center addObserver:self
                   selector:@selector(deviceOrientationDidChange:)
                       name:UIDeviceOrientationDidChangeNotification
                     object:nil];
        [center addObserver:self
                   selector:@selector(handleCaptureSessionInterruption:)
                       name:AVCaptureSessionWasInterruptedNotification
                     object:_captureSession];
        [center addObserver:self
                   selector:@selector(handleCaptureSessionInterruptionEnded:)
                       name:AVCaptureSessionInterruptionEndedNotification
                     object:_captureSession];
        [center addObserver:self
                   selector:@selector(handleApplicationDidBecomeActive:)
                       name:UIApplicationDidBecomeActiveNotification
                     object:[UIApplication sharedApplication]];
        [center addObserver:self
            selector:@selector(handleApplicationWillEnterForeground:)
                name:UIApplicationWillEnterForegroundNotification
              object:[UIApplication sharedApplication]];
        [center addObserver:self
                   selector:@selector(handleCaptureSessionRuntimeError:)
                       name:AVCaptureSessionRuntimeErrorNotification
                     object:_captureSession];
        [center addObserver:self
                   selector:@selector(handleCaptureSessionDidStartRunning:)
                       name:AVCaptureSessionDidStartRunningNotification
                     object:_captureSession];
        [center addObserver:self
                   selector:@selector(handleCaptureSessionDidStopRunning:)
                       name:AVCaptureSessionDidStopRunningNotification
                     object:_captureSession];
    }
    return self;
}

- (void)dealloc {
    NSAssert(!_willBeRunning, @"Session was still running in RTCCameraVideoCapturer dealloc. Forgot to call stopCapture?");
    [[NSNotificationCenter defaultCenter] removeObserver:self];
}

+ (NSArray<AVCaptureDevice *> *)captureDevices {
    if (@available(iOS 10.0, *)) {
        AVCaptureDeviceDiscoverySession *session = [AVCaptureDeviceDiscoverySession discoverySessionWithDeviceTypes:@[AVCaptureDeviceTypeBuiltInWideAngleCamera] mediaType:AVMediaTypeVideo position:AVCaptureDevicePositionUnspecified];
        return session.devices;
    } else {
        NSMutableArray<AVCaptureDevice *> *result = [[NSMutableArray alloc] init];
        for (AVCaptureDevice *device in [AVCaptureDevice devicesWithMediaType:AVMediaTypeVideo]) {
            if (device.position == AVCaptureDevicePositionFront || device.position == AVCaptureDevicePositionBack) {
                [result addObject:device];
            }
        }
        return result;
    }
}

+ (NSArray<AVCaptureDeviceFormat *> *)supportedFormatsForDevice:(AVCaptureDevice *)device {
  // Support opening the device in any format. We make sure it's converted to a format we
  // can handle, if needed, in the method `-setupVideoDataOutput`.
  return device.formats;
}

- (FourCharCode)preferredOutputPixelFormat {
  return _preferredOutputPixelFormat;
}

- (void)startCaptureWithDevice:(AVCaptureDevice *)device
                        format:(AVCaptureDeviceFormat *)format
                           fps:(NSInteger)fps {
  [self startCaptureWithDevice:device format:format fps:fps completionHandler:nil];
}

- (void)stopCapture {
  _isActiveUpdated = nil;
  [self stopCaptureWithCompletionHandler:nil];
}

- (void)setIsEnabled:(bool)isEnabled {
    _isPaused = !isEnabled;
    [self updateIsActiveValue];
}

- (void)setUncroppedSink:(std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>)sink {
	dispatch_async(self.frameQueue, ^{
		_uncroppedSink = sink;
	});
}

- (void)setPreferredCaptureAspectRatio:(float)aspectRatio {
	dispatch_async(self.frameQueue, ^{
		_aspectRatio = aspectRatio;
	});
}

- (void)startCaptureWithDevice:(AVCaptureDevice *)device
                        format:(AVCaptureDeviceFormat *)format
                           fps:(NSInteger)fps
             completionHandler:(nullable void (^)(NSError *))completionHandler {
  _willBeRunning = true;
  [RTCDispatcher
      dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
   block:^{
      RTCLogInfo("startCaptureWithDevice %@ @ %ld fps", format, (long)fps);
      
      dispatch_async(dispatch_get_main_queue(), ^{
          [[UIDevice currentDevice] beginGeneratingDeviceOrientationNotifications];
      });
      
      _currentDevice = device;
      
      NSError *error = nil;
      if (![_currentDevice lockForConfiguration:&error]) {
          RTCLogError(@"Failed to lock device %@. Error: %@",
                      _currentDevice,
                      error.userInfo);
          if (completionHandler) {
              completionHandler(error);
          }
          _willBeRunning = false;
          return;
      }
      [self reconfigureCaptureSessionInput];
      [self updateOrientation];
      [self updateDeviceCaptureFormat:format fps:fps];
      [self updateVideoDataOutputPixelFormat:format];
      [_captureSession startRunning];
      [_currentDevice unlockForConfiguration];
      _isRunning = YES;
      if (completionHandler) {
          completionHandler(nil);
      }
  }];
}

- (void)stopCaptureWithCompletionHandler:(nullable void (^)(void))completionHandler {
  _willBeRunning = false;
  [RTCDispatcher
   dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
   block:^{
      RTCLogInfo("Stop");
      _currentDevice = nil;
      for (AVCaptureDeviceInput *oldInput in [_captureSession.inputs copy]) {
          [_captureSession removeInput:oldInput];
      }
      [_captureSession stopRunning];
      
      dispatch_async(dispatch_get_main_queue(), ^{
          [[UIDevice currentDevice] endGeneratingDeviceOrientationNotifications];
      });
      _isRunning = NO;
      if (completionHandler) {
          completionHandler();
      }
  }];
}

#pragma mark iOS notifications

#if TARGET_OS_IPHONE
- (void)deviceOrientationDidChange:(NSNotification *)notification {
  [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                               block:^{
                                 [self updateOrientation];
                               }];
}
#endif

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
    
    // Default to portrait orientation on iPhone.
    BOOL usingFrontCamera = NO;
    // Check the image's EXIF for the camera the image came from as the image could have been
    // delayed as we set alwaysDiscardsLateVideoFrames to NO.
    AVCaptureDevicePosition cameraPosition =
    [AVCaptureSession devicePositionForSampleBuffer:sampleBuffer];
    if (cameraPosition != AVCaptureDevicePositionUnspecified) {
        usingFrontCamera = AVCaptureDevicePositionFront == cameraPosition;
    } else {
        AVCaptureDeviceInput *deviceInput =
        (AVCaptureDeviceInput *)((AVCaptureInputPort *)connection.inputPorts.firstObject).input;
        usingFrontCamera = AVCaptureDevicePositionFront == deviceInput.device.position;
    }
    if (!_rotationLock) {
        switch (_orientation) {
            case UIDeviceOrientationPortrait:
                _rotation = RTCVideoRotation_90;
                break;
            case UIDeviceOrientationPortraitUpsideDown:
                _rotation = RTCVideoRotation_270;
                break;
            case UIDeviceOrientationLandscapeLeft:
                _rotation = usingFrontCamera ? RTCVideoRotation_180 : RTCVideoRotation_0;
                break;
            case UIDeviceOrientationLandscapeRight:
                _rotation = usingFrontCamera ? RTCVideoRotation_0 : RTCVideoRotation_180;
                break;
            case UIDeviceOrientationFaceUp:
            case UIDeviceOrientationFaceDown:
            case UIDeviceOrientationUnknown:
                // Ignore.
                break;
        }
    }
    
    TGRTCCVPixelBuffer *rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer];
    rtcPixelBuffer.shouldBeMirrored = usingFrontCamera;
    
    TGRTCCVPixelBuffer *uncroppedRtcPixelBuffer = rtcPixelBuffer;
    
    if (_aspectRatio > FLT_EPSILON) {
        float aspect = 1.0f / _aspectRatio;
        
        int width = rtcPixelBuffer.width;
        int height = rtcPixelBuffer.height;
        
        float aspectWidth = width;
        float aspectHeight = ((float)(width)) / aspect;
        int cropX = (int)((width - aspectWidth) / 2.0f);
        int cropY = (int)((height - aspectHeight) / 2.0f);
        
        width = (int)aspectWidth;
        width &= ~1;
        height = (int)aspectHeight;
        height &= ~1;
        
        height = MIN(rtcPixelBuffer.height, height + 16);
        
        if (width < rtcPixelBuffer.width || height < rtcPixelBuffer.height) {
            rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:pixelBuffer adaptedWidth:width adaptedHeight:height cropWidth:width cropHeight:height cropX:cropX cropY:cropY];
            rtcPixelBuffer.shouldBeMirrored = usingFrontCamera;
            
            CVPixelBufferRef outputPixelBufferRef = NULL;
            OSType pixelFormat = CVPixelBufferGetPixelFormatType(rtcPixelBuffer.pixelBuffer);
            CVPixelBufferCreate(NULL, width, height, pixelFormat, NULL, &outputPixelBufferRef);
            if (outputPixelBufferRef) {
                int bufferSize = [rtcPixelBuffer bufferSizeForCroppingAndScalingToWidth:width height:width];
                if (_croppingBuffer.size() < bufferSize) {
                    _croppingBuffer.resize(bufferSize);
                }
                if ([rtcPixelBuffer cropAndScaleTo:outputPixelBufferRef withTempBuffer:_croppingBuffer.data()]) {
                    rtcPixelBuffer = [[TGRTCCVPixelBuffer alloc] initWithPixelBuffer:outputPixelBufferRef];
                    rtcPixelBuffer.shouldBeMirrored = usingFrontCamera;
                }
                CVPixelBufferRelease(outputPixelBufferRef);
            }
        }
    }
    
    int64_t timeStampNs = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) *
    kNanosecondsPerSecond;
    RTCVideoFrame *videoFrame = [[RTCVideoFrame alloc] initWithBuffer:rtcPixelBuffer rotation:_rotation timeStampNs:timeStampNs];
    
    if (!_isPaused) {
        getObjCVideoSource(_source)->OnCapturedFrame(videoFrame);
        
        if (_uncroppedSink && uncroppedRtcPixelBuffer) {
            int64_t timeStampNs = CMTimeGetSeconds(CMSampleBufferGetPresentationTimeStamp(sampleBuffer)) *
            kNanosecondsPerSecond;
            RTCVideoFrame *frame = [[RTCVideoFrame alloc] initWithBuffer:uncroppedRtcPixelBuffer rotation:_rotation timeStampNs:timeStampNs];
            
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
    }
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
    NSString *reasonString = nil;
    NSNumber *reason = notification.userInfo[AVCaptureSessionInterruptionReasonKey];
    if (reason) {
        switch (reason.intValue) {
            case AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableInBackground:
                reasonString = @"VideoDeviceNotAvailableInBackground";
                break;
            case AVCaptureSessionInterruptionReasonAudioDeviceInUseByAnotherClient:
                reasonString = @"AudioDeviceInUseByAnotherClient";
                break;
            case AVCaptureSessionInterruptionReasonVideoDeviceInUseByAnotherClient:
                reasonString = @"VideoDeviceInUseByAnotherClient";
                break;
            case AVCaptureSessionInterruptionReasonVideoDeviceNotAvailableWithMultipleForegroundApps:
                reasonString = @"VideoDeviceNotAvailableWithMultipleForegroundApps";
                break;
        }
    }
    RTCLog(@"Capture session interrupted: %@", reasonString);
}

- (void)handleCaptureSessionInterruptionEnded:(NSNotification *)notification {
    RTCLog(@"Capture session interruption ended.");
}

- (void)handleCaptureSessionRuntimeError:(NSNotification *)notification {
    NSError *error = [notification.userInfo objectForKey:AVCaptureSessionErrorKey];
    RTCLogError(@"Capture session runtime error: %@", error);

    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                                 block:^{
        if (error.code == AVErrorMediaServicesWereReset) {
            [self handleNonFatalError];
        } else {
            [self handleFatalError];
        }
    }];
}

- (void)handleCaptureSessionDidStartRunning:(NSNotification *)notification {
    RTCLog(@"Capture session started.");
    
    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                                 block:^{
        // If we successfully restarted after an unknown error,
        // allow future retries on fatal errors.
        _hasRetriedOnFatalError = NO;
    }];
    
    _inForegroundValue = true;
    [self updateIsActiveValue];
}

- (void)handleCaptureSessionDidStopRunning:(NSNotification *)notification {
  RTCLog(@"Capture session stopped.");
    _inForegroundValue = false;
    [self updateIsActiveValue];
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

- (void)handleFatalError {
    [RTCDispatcher
     dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
     block:^{
        if (!_hasRetriedOnFatalError) {
            RTCLogWarning(@"Attempting to recover from fatal capture error.");
            [self handleNonFatalError];
            _hasRetriedOnFatalError = YES;
        } else {
            RTCLogError(@"Previous fatal error recovery failed.");
        }
    }];
}

- (void)handleNonFatalError {
    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                                 block:^{
        RTCLog(@"Restarting capture session after error.");
        if (_isRunning) {
            [_captureSession startRunning];
        }
    }];
}

#pragma mark - UIApplication notifications

- (void)handleApplicationDidBecomeActive:(NSNotification *)notification {
    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                                 block:^{
        if (_isRunning && !_captureSession.isRunning) {
            RTCLog(@"Restarting capture session on active.");
            _warmupFrameCount = 0;
            [_captureSession startRunning];
        }
    }];
}

- (void)handleApplicationWillEnterForeground:(NSNotification *)notification {
    [RTCDispatcher dispatchAsyncOnType:RTCDispatcherTypeCaptureSession
                                 block:^{
        RTCLog(@"Resetting warmup due to backgrounding.");
        _warmupFrameCount = 0;
    }];
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
    _captureSession.sessionPreset = AVCaptureSessionPresetInputPriority;
    _captureSession.usesApplicationAudioSession = true;
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
    NSSet<NSNumber *> *supportedPixelFormats = [TGRTCCVPixelBuffer supportedPixelFormats];
    NSMutableOrderedSet *availablePixelFormats =
    [NSMutableOrderedSet orderedSetWithArray:videoDataOutput.availableVideoCVPixelFormatTypes];
    [availablePixelFormats intersectSet:supportedPixelFormats];
    NSNumber *pixelFormat = availablePixelFormats.firstObject;
    NSAssert(pixelFormat, @"Output device has no supported formats.");
    
    _preferredOutputPixelFormat = [pixelFormat unsignedIntValue];
    _outputPixelFormat = _preferredOutputPixelFormat;
    videoDataOutput.videoSettings = @{(NSString *)kCVPixelBufferPixelFormatTypeKey : pixelFormat};
    videoDataOutput.alwaysDiscardsLateVideoFrames = NO;
    [videoDataOutput setSampleBufferDelegate:self queue:self.frameQueue];
    _videoDataOutput = videoDataOutput;
}

- (void)updateVideoDataOutputPixelFormat:(AVCaptureDeviceFormat *)format {
    FourCharCode mediaSubType = CMFormatDescriptionGetMediaSubType(format.formatDescription);
    if (![[TGRTCCVPixelBuffer supportedPixelFormats] containsObject:@(mediaSubType)]) {
        mediaSubType = _preferredOutputPixelFormat;
    }
    
    if (mediaSubType != _outputPixelFormat) {
        _outputPixelFormat = mediaSubType;
        _videoDataOutput.videoSettings =
        @{ (NSString *)kCVPixelBufferPixelFormatTypeKey : @(mediaSubType) };
    }
}

#pragma mark - Private, called inside capture queue

- (void)updateDeviceCaptureFormat:(AVCaptureDeviceFormat *)format fps:(NSInteger)fps {
    NSAssert([RTCDispatcher isOnQueueForType:RTCDispatcherTypeCaptureSession],
             @"updateDeviceCaptureFormat must be called on the capture queue.");
    @try {
        _currentDevice.activeFormat = format;
        _currentDevice.activeVideoMinFrameDuration = CMTimeMake(1, (int32_t)fps);
    } @catch (NSException *exception) {
        RTCLogError(@"Failed to set active format!\n User info:%@", exception.userInfo);
        return;
    }
}

- (void)reconfigureCaptureSessionInput {
    NSAssert([RTCDispatcher isOnQueueForType:RTCDispatcherTypeCaptureSession],
             @"reconfigureCaptureSessionInput must be called on the capture queue.");
    NSError *error = nil;
    AVCaptureDeviceInput *input =
    [AVCaptureDeviceInput deviceInputWithDevice:_currentDevice error:&error];
    if (!input) {
        RTCLogError(@"Failed to create front camera input: %@", error.localizedDescription);
        return;
    }
    [_captureSession beginConfiguration];
    for (AVCaptureDeviceInput *oldInput in [_captureSession.inputs copy]) {
        [_captureSession removeInput:oldInput];
    }
    if ([_captureSession canAddInput:input]) {
        [_captureSession addInput:input];
    } else {
        RTCLogError(@"Cannot add camera as an input to the session.");
    }
    [_captureSession commitConfiguration];
}

- (void)updateOrientation {
    NSAssert([RTCDispatcher isOnQueueForType:RTCDispatcherTypeCaptureSession],
             @"updateOrientation must be called on the capture queue.");
    _orientation = [UIDevice currentDevice].orientation;
}

@end
