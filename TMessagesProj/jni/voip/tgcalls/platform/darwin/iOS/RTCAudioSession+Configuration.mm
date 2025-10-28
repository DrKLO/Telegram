/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "RTCAudioSession+Private.h"
#import "RTCAudioSessionConfiguration.h"

#import "base/RTCLogging.h"

@implementation RTC_OBJC_TYPE (RTCAudioSession)
(Configuration)

    - (BOOL)setConfiguration : (RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *)configuration error
    : (NSError **)outError disableRecording:(BOOL)disableRecording {
  return [self setConfiguration:configuration
                         active:NO
                shouldSetActive:NO
                          error:outError
               disableRecording:disableRecording];
}

- (BOOL)setConfiguration:(RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *)configuration
                  active:(BOOL)active
                   error:(NSError **)outError disableRecording:(BOOL)disableRecording {
  return [self setConfiguration:configuration
                         active:active
                shouldSetActive:YES
                          error:outError
               disableRecording:disableRecording];
}

#pragma mark - Private

- (BOOL)setConfiguration:(RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *)configuration
                  active:(BOOL)active
         shouldSetActive:(BOOL)shouldSetActive
                   error:(NSError **)outError disableRecording:(BOOL)disableRecording {
  NSParameterAssert(configuration);
  if (outError) {
    *outError = nil;
  }

  // Provide an error even if there isn't one so we can log it. We will not
  // return immediately on error in this function and instead try to set
  // everything we can.
  NSError *error = nil;

  if (!disableRecording) {
  if (self.category != configuration.category ||
      self.categoryOptions != configuration.categoryOptions) {
    NSError *categoryError = nil;
    if (![self setCategory:configuration.category
               withOptions:configuration.categoryOptions
                     error:&categoryError]) {
      RTCLogError(@"Failed to set category: %@",
                  categoryError.localizedDescription);
      error = categoryError;
    } else {
      RTCLog(@"Set category to: %@", configuration.category);
    }
  }

  if (self.mode != configuration.mode) {
    NSError *modeError = nil;
    if (![self setMode:configuration.mode error:&modeError]) {
      RTCLogError(@"Failed to set mode: %@",
                  modeError.localizedDescription);
      error = modeError;
    } else {
      RTCLog(@"Set mode to: %@", configuration.mode);
    }
  }

  // Sometimes category options don't stick after setting mode.
  if (self.categoryOptions != configuration.categoryOptions) {
    NSError *categoryError = nil;
    if (![self setCategory:configuration.category
               withOptions:configuration.categoryOptions
                     error:&categoryError]) {
      RTCLogError(@"Failed to set category options: %@",
                  categoryError.localizedDescription);
      error = categoryError;
    } else {
      RTCLog(@"Set category options to: %ld",
             (long)configuration.categoryOptions);
    }
  }
  }

  if (self.preferredSampleRate != configuration.sampleRate) {
    NSError *sampleRateError = nil;
    if (![self setPreferredSampleRate:configuration.sampleRate
                                error:&sampleRateError]) {
      RTCLogError(@"Failed to set preferred sample rate: %@",
                  sampleRateError.localizedDescription);
      if (!self.ignoresPreferredAttributeConfigurationErrors) {
        error = sampleRateError;
      }
    } else {
      RTCLog(@"Set preferred sample rate to: %.2f",
             configuration.sampleRate);
    }
  }

  if (self.preferredIOBufferDuration != configuration.ioBufferDuration) {
    NSError *bufferDurationError = nil;
    if (![self setPreferredIOBufferDuration:configuration.ioBufferDuration
                                      error:&bufferDurationError]) {
      RTCLogError(@"Failed to set preferred IO buffer duration: %@",
                  bufferDurationError.localizedDescription);
      if (!self.ignoresPreferredAttributeConfigurationErrors) {
        error = bufferDurationError;
      }
    } else {
      RTCLog(@"Set preferred IO buffer duration to: %f",
             configuration.ioBufferDuration);
    }
  }

  if (shouldSetActive) {
    NSError *activeError = nil;
    if (![self setActive:active error:&activeError]) {
      RTCLogError(@"Failed to setActive to %d: %@",
                  active, activeError.localizedDescription);
      error = activeError;
    }
  }

  if (self.isActive &&
      // TODO(tkchin): Figure out which category/mode numChannels is valid for.
      [self.mode isEqualToString:AVAudioSessionModeVoiceChat]) {
    // Try to set the preferred number of hardware audio channels. These calls
    // must be done after setting the audio session’s category and mode and
    // activating the session.
    NSInteger inputNumberOfChannels = configuration.inputNumberOfChannels;
    if (self.inputNumberOfChannels != inputNumberOfChannels) {
      NSError *inputChannelsError = nil;
      if (![self setPreferredInputNumberOfChannels:inputNumberOfChannels
                                             error:&inputChannelsError]) {
       RTCLogError(@"Failed to set preferred input number of channels: %@",
                   inputChannelsError.localizedDescription);
       if (!self.ignoresPreferredAttributeConfigurationErrors) {
         error = inputChannelsError;
       }
      } else {
        RTCLog(@"Set input number of channels to: %ld",
               (long)inputNumberOfChannels);
      }
    }
    NSInteger outputNumberOfChannels = configuration.outputNumberOfChannels;
    if (self.outputNumberOfChannels != outputNumberOfChannels) {
      NSError *outputChannelsError = nil;
      if (![self setPreferredOutputNumberOfChannels:outputNumberOfChannels
                                              error:&outputChannelsError]) {
        RTCLogError(@"Failed to set preferred output number of channels: %@",
                    outputChannelsError.localizedDescription);
        if (!self.ignoresPreferredAttributeConfigurationErrors) {
          error = outputChannelsError;
        }
      } else {
        RTCLog(@"Set output number of channels to: %ld",
               (long)outputNumberOfChannels);
      }
    }
  }

  if (outError) {
    *outError = error;
  }

  return error == nil;
}

@end
