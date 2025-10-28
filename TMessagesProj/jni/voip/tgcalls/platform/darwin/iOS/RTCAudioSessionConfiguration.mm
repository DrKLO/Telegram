/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "RTCAudioSessionConfiguration.h"
#import "RTCAudioSession.h"

#import "helpers/RTCDispatcher.h"
#import "helpers/UIDevice+RTCDevice.h"

// Try to use mono to save resources. Also avoids channel format conversion
// in the I/O audio unit. Initial tests have shown that it is possible to use
// mono natively for built-in microphones and for BT headsets but not for
// wired headsets. Wired headsets only support stereo as native channel format
// but it is a low cost operation to do a format conversion to mono in the
// audio unit. Hence, we will not hit a RTC_CHECK in
// VerifyAudioParametersForActiveAudioSession() for a mismatch between the
// preferred number of channels and the actual number of channels.
const int kRTCAudioSessionPreferredNumberOfChannels = 1;

// Preferred hardware sample rate (unit is in Hertz). The client sample rate
// will be set to this value as well to avoid resampling the the audio unit's
// format converter. Note that, some devices, e.g. BT headsets, only supports
// 8000Hz as native sample rate.
const double kRTCAudioSessionHighPerformanceSampleRate = 48000.0;

// A lower sample rate will be used for devices with only one core
// (e.g. iPhone 4). The goal is to reduce the CPU load of the application.
const double kRTCAudioSessionLowComplexitySampleRate = 16000.0;

// Use a hardware I/O buffer size (unit is in seconds) that matches the 10ms
// size used by WebRTC. The exact actual size will differ between devices.
// Example: using 48kHz on iPhone 6 results in a native buffer size of
// ~10.6667ms or 512 audio frames per buffer. The FineAudioBuffer instance will
// take care of any buffering required to convert between native buffers and
// buffers used by WebRTC. It is beneficial for the performance if the native
// size is as an even multiple of 10ms as possible since it results in "clean"
// callback sequence without bursts of callbacks back to back.
const double kRTCAudioSessionHighPerformanceIOBufferDuration = 0.02;

// Use a larger buffer size on devices with only one core (e.g. iPhone 4).
// It will result in a lower CPU consumption at the cost of a larger latency.
// The size of 60ms is based on instrumentation that shows a significant
// reduction in CPU load compared with 10ms on low-end devices.
// TODO(henrika): monitor this size and determine if it should be modified.
const double kRTCAudioSessionLowComplexityIOBufferDuration = 0.06;

static RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *gWebRTCConfiguration = nil;

@implementation RTC_OBJC_TYPE (RTCAudioSessionConfiguration)

@synthesize category = _category;
@synthesize categoryOptions = _categoryOptions;
@synthesize mode = _mode;
@synthesize sampleRate = _sampleRate;
@synthesize ioBufferDuration = _ioBufferDuration;
@synthesize inputNumberOfChannels = _inputNumberOfChannels;
@synthesize outputNumberOfChannels = _outputNumberOfChannels;

- (instancetype)init {
  if (self = [super init]) {
    // Use a category which supports simultaneous recording and playback.
    // By default, using this category implies that our app’s audio is
    // nonmixable, hence activating the session will interrupt any other
    // audio sessions which are also nonmixable.
    _category = AVAudioSessionCategoryPlayAndRecord;
    _categoryOptions = AVAudioSessionCategoryOptionAllowBluetooth;

    // Specify mode for two-way voice communication (e.g. VoIP).
    _mode = AVAudioSessionModeVoiceChat;

    // Set the session's sample rate or the hardware sample rate.
    // It is essential that we use the same sample rate as stream format
    // to ensure that the I/O unit does not have to do sample rate conversion.
    // Set the preferred audio I/O buffer duration, in seconds.
    NSUInteger processorCount = [NSProcessInfo processInfo].processorCount;
    // Use best sample rate and buffer duration if the CPU has more than one
    // core.
    if (processorCount > 1) {
      _sampleRate = kRTCAudioSessionHighPerformanceSampleRate;
      _ioBufferDuration = kRTCAudioSessionHighPerformanceIOBufferDuration;
    } else {
      _sampleRate = kRTCAudioSessionLowComplexitySampleRate;
      _ioBufferDuration = kRTCAudioSessionLowComplexityIOBufferDuration;
    }

    // We try to use mono in both directions to save resources and format
    // conversions in the audio unit. Some devices does only support stereo;
    // e.g. wired headset on iPhone 6.
    // TODO(henrika): add support for stereo if needed.
    _inputNumberOfChannels = kRTCAudioSessionPreferredNumberOfChannels;
    _outputNumberOfChannels = kRTCAudioSessionPreferredNumberOfChannels;
  }
  return self;
}

+ (void)initialize {
  gWebRTCConfiguration = [[self alloc] init];
}

+ (instancetype)currentConfiguration {
  RTC_OBJC_TYPE(RTCAudioSession) *session = [RTC_OBJC_TYPE(RTCAudioSession) sharedInstance];
  RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *config =
      [[RTC_OBJC_TYPE(RTCAudioSessionConfiguration) alloc] init];
  config.category = session.category;
  config.categoryOptions = session.categoryOptions;
  config.mode = session.mode;
  config.sampleRate = session.sampleRate;
  config.ioBufferDuration = session.IOBufferDuration;
  config.inputNumberOfChannels = session.inputNumberOfChannels;
  config.outputNumberOfChannels = session.outputNumberOfChannels;
  return config;
}

+ (instancetype)webRTCConfiguration {
  @synchronized(self) {
    return (RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *)gWebRTCConfiguration;
  }
}

+ (void)setWebRTCConfiguration:(RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *)configuration {
  @synchronized(self) {
    gWebRTCConfiguration = configuration;
  }
}

@end
