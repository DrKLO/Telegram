/*
 *  Copyright 2018 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#import "RTCNativeAudioSessionDelegateAdapter.h"

#include "sdk/objc/native/src/audio/audio_session_observer.h"

#import "base/RTCLogging.h"

@implementation RTCNativeAudioSessionDelegateAdapter {
  webrtc::AudioSessionObserver *_observer;
}

- (instancetype)initWithObserver:(webrtc::AudioSessionObserver *)observer {
  RTC_DCHECK(observer);
  if (self = [super init]) {
    _observer = observer;
  }
  return self;
}

#pragma mark - RTC_OBJC_TYPE(RTCAudioSessionDelegate)

- (void)audioSessionDidBeginInterruption:(RTC_OBJC_TYPE(RTCAudioSession) *)session {
  _observer->OnInterruptionBegin();
}

- (void)audioSessionDidEndInterruption:(RTC_OBJC_TYPE(RTCAudioSession) *)session
                   shouldResumeSession:(BOOL)shouldResumeSession {
  _observer->OnInterruptionEnd();
}

- (void)audioSessionDidChangeRoute:(RTC_OBJC_TYPE(RTCAudioSession) *)session
                            reason:(AVAudioSessionRouteChangeReason)reason
                     previousRoute:(AVAudioSessionRouteDescription *)previousRoute {
  switch (reason) {
    case AVAudioSessionRouteChangeReasonUnknown:
    case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
    case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
    case AVAudioSessionRouteChangeReasonCategoryChange:
      // It turns out that we see a category change (at least in iOS 9.2)
      // when making a switch from a BT device to e.g. Speaker using the
      // iOS Control Center and that we therefore must check if the sample
      // rate has changed. And if so is the case, restart the audio unit.
    case AVAudioSessionRouteChangeReasonOverride:
    case AVAudioSessionRouteChangeReasonWakeFromSleep:
    case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
      _observer->OnValidRouteChange();
      break;
    case AVAudioSessionRouteChangeReasonRouteConfigurationChange:
      // The set of input and output ports has not changed, but their
      // configuration has, e.g., a port’s selected data source has
      // changed. Ignore this type of route change since we are focusing
      // on detecting headset changes.
      RTCLog(@"Ignoring RouteConfigurationChange");
      break;
  }
}

- (void)audioSessionMediaServerTerminated:(RTC_OBJC_TYPE(RTCAudioSession) *)session {
}

- (void)audioSessionMediaServerReset:(RTC_OBJC_TYPE(RTCAudioSession) *)session {
}

- (void)audioSession:(RTC_OBJC_TYPE(RTCAudioSession) *)session
    didChangeCanPlayOrRecord:(BOOL)canPlayOrRecord {
  _observer->OnCanPlayOrRecordChange(canPlayOrRecord);
}

- (void)audioSessionDidStartPlayOrRecord:(RTC_OBJC_TYPE(RTCAudioSession) *)session {
}

- (void)audioSessionDidStopPlayOrRecord:(RTC_OBJC_TYPE(RTCAudioSession) *)session {
}

- (void)audioSession:(RTC_OBJC_TYPE(RTCAudioSession) *)audioSession
    didChangeOutputVolume:(float)outputVolume {
  _observer->OnChangedOutputVolume();
}

@end
