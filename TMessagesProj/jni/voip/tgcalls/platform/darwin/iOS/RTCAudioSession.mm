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

#import <UIKit/UIKit.h>

#include <atomic>
#include <vector>

#include "absl/base/attributes.h"
#include "rtc_base/checks.h"
#include "rtc_base/synchronization/mutex.h"

#import "RTCAudioSessionConfiguration.h"
#import "base/RTCLogging.h"

#if !defined(ABSL_HAVE_THREAD_LOCAL)
#error ABSL_HAVE_THREAD_LOCAL should be defined for MacOS / iOS Targets.
#endif

NSString *const kRTCAudioSessionErrorDomain = @"org.webrtc.RTC_OBJC_TYPE(RTCAudioSession)";
NSInteger const kRTCAudioSessionErrorLockRequired = -1;
NSInteger const kRTCAudioSessionErrorConfiguration = -2;
NSString * const kRTCAudioSessionOutputVolumeSelector = @"outputVolume";

namespace {
// Since webrtc::Mutex is not a reentrant lock and cannot check if the mutex is locked,
// we need a separate variable to check that the mutex is locked in the RTCAudioSession.
ABSL_CONST_INIT thread_local bool mutex_locked = false;
}  // namespace

@interface RTC_OBJC_TYPE (RTCAudioSession)
() @property(nonatomic,
             readonly) std::vector<__weak id<RTC_OBJC_TYPE(RTCAudioSessionDelegate)> > delegates;
@end

// This class needs to be thread-safe because it is accessed from many threads.
// TODO(tkchin): Consider more granular locking. We're not expecting a lot of
// lock contention so coarse locks should be fine for now.
@implementation RTC_OBJC_TYPE (RTCAudioSession) {
  webrtc::Mutex _mutex;
  AVAudioSession *_session;
  std::atomic<int> _activationCount;
  std::atomic<int> _webRTCSessionCount;
  BOOL _isActive;
  BOOL _useManualAudio;
  BOOL _isAudioEnabled;
  BOOL _canPlayOrRecord;
  BOOL _isInterrupted;
}

@synthesize session = _session;
@synthesize delegates = _delegates;
@synthesize ignoresPreferredAttributeConfigurationErrors =
    _ignoresPreferredAttributeConfigurationErrors;

+ (instancetype)sharedInstance {
  static dispatch_once_t onceToken;
  static RTC_OBJC_TYPE(RTCAudioSession) *sharedInstance = nil;
  dispatch_once(&onceToken, ^{
    sharedInstance = [[self alloc] init];
  });
  return sharedInstance;
}

- (instancetype)init {
  return [self initWithAudioSession:[AVAudioSession sharedInstance]];
}

/** This initializer provides a way for unit tests to inject a fake/mock audio session. */
- (instancetype)initWithAudioSession:(id)audioSession {
  if (self = [super init]) {
    _session = audioSession;

    NSNotificationCenter *center = [NSNotificationCenter defaultCenter];
    [center addObserver:self
               selector:@selector(handleInterruptionNotification:)
                   name:AVAudioSessionInterruptionNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(handleRouteChangeNotification:)
                   name:AVAudioSessionRouteChangeNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(handleMediaServicesWereLost:)
                   name:AVAudioSessionMediaServicesWereLostNotification
                 object:nil];
    [center addObserver:self
               selector:@selector(handleMediaServicesWereReset:)
                   name:AVAudioSessionMediaServicesWereResetNotification
                 object:nil];
    // Posted on the main thread when the primary audio from other applications
    // starts and stops. Foreground applications may use this notification as a
    // hint to enable or disable audio that is secondary.
    [center addObserver:self
               selector:@selector(handleSilenceSecondaryAudioHintNotification:)
                   name:AVAudioSessionSilenceSecondaryAudioHintNotification
                 object:nil];
    // Also track foreground event in order to deal with interruption ended situation.
    [center addObserver:self
               selector:@selector(handleApplicationDidBecomeActive:)
                   name:UIApplicationDidBecomeActiveNotification
                 object:nil];
    [_session addObserver:self
               forKeyPath:kRTCAudioSessionOutputVolumeSelector
                  options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionOld
                  context:(__bridge void *)RTC_OBJC_TYPE(RTCAudioSession).class];

    RTCLog(@"RTC_OBJC_TYPE(RTCAudioSession) (%p): init.", self);
  }
  return self;
}

- (void)dealloc {
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [_session removeObserver:self
                forKeyPath:kRTCAudioSessionOutputVolumeSelector
                   context:(__bridge void *)RTC_OBJC_TYPE(RTCAudioSession).class];
  RTCLog(@"RTC_OBJC_TYPE(RTCAudioSession) (%p): dealloc.", self);
}

- (NSString *)description {
  NSString *format = @"RTC_OBJC_TYPE(RTCAudioSession): {\n"
                      "  category: %@\n"
                      "  categoryOptions: %ld\n"
                      "  mode: %@\n"
                      "  isActive: %d\n"
                      "  sampleRate: %.2f\n"
                      "  IOBufferDuration: %f\n"
                      "  outputNumberOfChannels: %ld\n"
                      "  inputNumberOfChannels: %ld\n"
                      "  outputLatency: %f\n"
                      "  inputLatency: %f\n"
                      "  outputVolume: %f\n"
                      "}";
  NSString *description = [NSString stringWithFormat:format,
      self.category, (long)self.categoryOptions, self.mode,
      self.isActive, self.sampleRate, self.IOBufferDuration,
      self.outputNumberOfChannels, self.inputNumberOfChannels,
      self.outputLatency, self.inputLatency, self.outputVolume];
  return description;
}

- (void)setIsActive:(BOOL)isActive {
  @synchronized(self) {
    _isActive = isActive;
  }
}

- (BOOL)isActive {
  @synchronized(self) {
    return _isActive;
  }
}

- (void)setUseManualAudio:(BOOL)useManualAudio {
  @synchronized(self) {
    if (_useManualAudio == useManualAudio) {
      return;
    }
    _useManualAudio = useManualAudio;
  }
  [self updateCanPlayOrRecord];
}

- (BOOL)useManualAudio {
  @synchronized(self) {
    return _useManualAudio;
  }
}

- (void)setIsAudioEnabled:(BOOL)isAudioEnabled {
  @synchronized(self) {
    if (_isAudioEnabled == isAudioEnabled) {
      return;
    }
    _isAudioEnabled = isAudioEnabled;
  }
  [self updateCanPlayOrRecord];
}

- (BOOL)isAudioEnabled {
  @synchronized(self) {
    return _isAudioEnabled;
  }
}

- (void)setIgnoresPreferredAttributeConfigurationErrors:
    (BOOL)ignoresPreferredAttributeConfigurationErrors {
  @synchronized(self) {
    if (_ignoresPreferredAttributeConfigurationErrors ==
        ignoresPreferredAttributeConfigurationErrors) {
      return;
    }
    _ignoresPreferredAttributeConfigurationErrors = ignoresPreferredAttributeConfigurationErrors;
  }
}

- (BOOL)ignoresPreferredAttributeConfigurationErrors {
  @synchronized(self) {
    return _ignoresPreferredAttributeConfigurationErrors;
  }
}

// TODO(tkchin): Check for duplicates.
- (void)addDelegate:(id<RTC_OBJC_TYPE(RTCAudioSessionDelegate)>)delegate {
  RTCLog(@"Adding delegate: (%p)", delegate);
  if (!delegate) {
    return;
  }
  @synchronized(self) {
    _delegates.push_back(delegate);
    [self removeZeroedDelegates];
  }
}

- (void)removeDelegate:(id<RTC_OBJC_TYPE(RTCAudioSessionDelegate)>)delegate {
  RTCLog(@"Removing delegate: (%p)", delegate);
  if (!delegate) {
    return;
  }
  @synchronized(self) {
    _delegates.erase(std::remove(_delegates.begin(),
                                 _delegates.end(),
                                 delegate),
                     _delegates.end());
    [self removeZeroedDelegates];
  }
}

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wthread-safety-analysis"

- (void)lockForConfiguration {
  RTC_CHECK(!mutex_locked);
  _mutex.Lock();
  mutex_locked = true;
}

- (void)unlockForConfiguration {
  mutex_locked = false;
  _mutex.Unlock();
}

#pragma clang diagnostic pop

#pragma mark - AVAudioSession proxy methods

- (NSString *)category {
  return self.session.category;
}

- (AVAudioSessionCategoryOptions)categoryOptions {
  return self.session.categoryOptions;
}

- (NSString *)mode {
  return self.session.mode;
}

- (BOOL)secondaryAudioShouldBeSilencedHint {
  return self.session.secondaryAudioShouldBeSilencedHint;
}

- (AVAudioSessionRouteDescription *)currentRoute {
  return self.session.currentRoute;
}

- (NSInteger)maximumInputNumberOfChannels {
  return self.session.maximumInputNumberOfChannels;
}

- (NSInteger)maximumOutputNumberOfChannels {
  return self.session.maximumOutputNumberOfChannels;
}

- (float)inputGain {
  return self.session.inputGain;
}

- (BOOL)inputGainSettable {
  return self.session.inputGainSettable;
}

- (BOOL)inputAvailable {
  return self.session.inputAvailable;
}

- (NSArray<AVAudioSessionDataSourceDescription *> *)inputDataSources {
  return self.session.inputDataSources;
}

- (AVAudioSessionDataSourceDescription *)inputDataSource {
  return self.session.inputDataSource;
}

- (NSArray<AVAudioSessionDataSourceDescription *> *)outputDataSources {
  return self.session.outputDataSources;
}

- (AVAudioSessionDataSourceDescription *)outputDataSource {
  return self.session.outputDataSource;
}

- (double)sampleRate {
  return self.session.sampleRate;
}

- (double)preferredSampleRate {
  return self.session.preferredSampleRate;
}

- (NSInteger)inputNumberOfChannels {
  return self.session.inputNumberOfChannels;
}

- (NSInteger)outputNumberOfChannels {
  return self.session.outputNumberOfChannels;
}

- (float)outputVolume {
  return self.session.outputVolume;
}

- (NSTimeInterval)inputLatency {
  return self.session.inputLatency;
}

- (NSTimeInterval)outputLatency {
  return self.session.outputLatency;
}

- (NSTimeInterval)IOBufferDuration {
  return self.session.IOBufferDuration;
}

- (NSTimeInterval)preferredIOBufferDuration {
  return self.session.preferredIOBufferDuration;
}

- (BOOL)setActive:(BOOL)active
            error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  int activationCount = _activationCount.load();
  if (!active && activationCount == 0) {
    RTCLogWarning(@"Attempting to deactivate without prior activation.");
  }
  [self notifyWillSetActive:active];
  BOOL success = YES;
  BOOL isActive = self.isActive;
  // Keep a local error so we can log it.
  NSError *error = nil;
  BOOL shouldSetActive =
      (active && !isActive) || (!active && isActive && activationCount == 1);
  // Attempt to activate if we're not active.
  // Attempt to deactivate if we're active and it's the last unbalanced call.
  if (shouldSetActive && false) {
    AVAudioSession *session = self.session;
    // AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation is used to ensure
    // that other audio sessions that were interrupted by our session can return
    // to their active state. It is recommended for VoIP apps to use this
    // option.
    AVAudioSessionSetActiveOptions options =
        active ? 0 : AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation;
    success = [session setActive:active
                     withOptions:options
                           error:&error];
    if (outError) {
      *outError = error;
    }
  }
  if (success) {
    if (active) {
      if (shouldSetActive) {
        self.isActive = active;
        if (self.isInterrupted) {
          self.isInterrupted = NO;
          [self notifyDidEndInterruptionWithShouldResumeSession:YES];
        }
      }
      [self incrementActivationCount];
      [self notifyDidSetActive:active];
    }
  } else {
    RTCLogError(@"Failed to setActive:%d. Error: %@",
                active, error.localizedDescription);
    [self notifyFailedToSetActive:active error:error];
  }
  // Set isActive and decrement activation count on deactivation
  // whether or not it succeeded.
  if (!active) {
    self.isActive = active;
    [self notifyDidSetActive:active];
    [self decrementActivationCount];
  }
  RTCLog(@"Number of current activations: %d", _activationCount.load());
  return success;
}

- (BOOL)setCategory:(NSString *)category
        withOptions:(AVAudioSessionCategoryOptions)options
              error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setCategory:category withOptions:options error:outError];
}

- (BOOL)setMode:(NSString *)mode error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setMode:mode error:outError];
}

- (BOOL)setInputGain:(float)gain error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setInputGain:gain error:outError];
}

- (BOOL)setPreferredSampleRate:(double)sampleRate error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setPreferredSampleRate:sampleRate error:outError];
}

- (BOOL)setPreferredIOBufferDuration:(NSTimeInterval)duration
                               error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setPreferredIOBufferDuration:duration error:outError];
}

- (BOOL)setPreferredInputNumberOfChannels:(NSInteger)count
                                    error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setPreferredInputNumberOfChannels:count error:outError];
}
- (BOOL)setPreferredOutputNumberOfChannels:(NSInteger)count
                                     error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setPreferredOutputNumberOfChannels:count error:outError];
}

- (BOOL)overrideOutputAudioPort:(AVAudioSessionPortOverride)portOverride
                          error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session overrideOutputAudioPort:portOverride error:outError];
}

- (BOOL)setPreferredInput:(AVAudioSessionPortDescription *)inPort
                    error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setPreferredInput:inPort error:outError];
}

- (BOOL)setInputDataSource:(AVAudioSessionDataSourceDescription *)dataSource
                     error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setInputDataSource:dataSource error:outError];
}

- (BOOL)setOutputDataSource:(AVAudioSessionDataSourceDescription *)dataSource
                      error:(NSError **)outError {
  if (![self checkLock:outError]) {
    return NO;
  }
  return [self.session setOutputDataSource:dataSource error:outError];
}

#pragma mark - Notifications

- (void)handleInterruptionNotification:(NSNotification *)notification {
  NSNumber* typeNumber =
      notification.userInfo[AVAudioSessionInterruptionTypeKey];
  AVAudioSessionInterruptionType type =
      (AVAudioSessionInterruptionType)typeNumber.unsignedIntegerValue;
  switch (type) {
    case AVAudioSessionInterruptionTypeBegan:
      RTCLog(@"Audio session interruption began.");
      self.isActive = NO;
      self.isInterrupted = YES;
      [self notifyDidBeginInterruption];
      break;
    case AVAudioSessionInterruptionTypeEnded: {
      RTCLog(@"Audio session interruption ended.");
      self.isInterrupted = NO;
      [self updateAudioSessionAfterEvent];
      NSNumber *optionsNumber =
          notification.userInfo[AVAudioSessionInterruptionOptionKey];
      AVAudioSessionInterruptionOptions options =
          optionsNumber.unsignedIntegerValue;
      BOOL shouldResume =
          options & AVAudioSessionInterruptionOptionShouldResume;
      [self notifyDidEndInterruptionWithShouldResumeSession:shouldResume];
      break;
    }
  }
}

- (void)handleRouteChangeNotification:(NSNotification *)notification {
  // Get reason for current route change.
  NSNumber* reasonNumber =
      notification.userInfo[AVAudioSessionRouteChangeReasonKey];
  AVAudioSessionRouteChangeReason reason =
      (AVAudioSessionRouteChangeReason)reasonNumber.unsignedIntegerValue;
  RTCLog(@"Audio route changed:");
  switch (reason) {
    case AVAudioSessionRouteChangeReasonUnknown:
      RTCLog(@"Audio route changed: ReasonUnknown");
      break;
    case AVAudioSessionRouteChangeReasonNewDeviceAvailable:
      RTCLog(@"Audio route changed: NewDeviceAvailable");
      break;
    case AVAudioSessionRouteChangeReasonOldDeviceUnavailable:
      RTCLog(@"Audio route changed: OldDeviceUnavailable");
      break;
    case AVAudioSessionRouteChangeReasonCategoryChange:
      RTCLog(@"Audio route changed: CategoryChange to :%@",
             self.session.category);
      break;
    case AVAudioSessionRouteChangeReasonOverride:
      RTCLog(@"Audio route changed: Override");
      break;
    case AVAudioSessionRouteChangeReasonWakeFromSleep:
      RTCLog(@"Audio route changed: WakeFromSleep");
      break;
    case AVAudioSessionRouteChangeReasonNoSuitableRouteForCategory:
      RTCLog(@"Audio route changed: NoSuitableRouteForCategory");
      break;
    case AVAudioSessionRouteChangeReasonRouteConfigurationChange:
      RTCLog(@"Audio route changed: RouteConfigurationChange");
      break;
  }
  AVAudioSessionRouteDescription* previousRoute =
      notification.userInfo[AVAudioSessionRouteChangePreviousRouteKey];
  // Log previous route configuration.
  RTCLog(@"Previous route: %@\nCurrent route:%@",
         previousRoute, self.session.currentRoute);
  [self notifyDidChangeRouteWithReason:reason previousRoute:previousRoute];
}

- (void)handleMediaServicesWereLost:(NSNotification *)notification {
  RTCLog(@"Media services were lost.");
  [self updateAudioSessionAfterEvent];
  [self notifyMediaServicesWereLost];
}

- (void)handleMediaServicesWereReset:(NSNotification *)notification {
  RTCLog(@"Media services were reset.");
  [self updateAudioSessionAfterEvent];
  [self notifyMediaServicesWereReset];
}

- (void)handleSilenceSecondaryAudioHintNotification:(NSNotification *)notification {
  // TODO(henrika): just adding logs here for now until we know if we are ever
  // see this notification and might be affected by it or if further actions
  // are required.
  NSNumber *typeNumber =
      notification.userInfo[AVAudioSessionSilenceSecondaryAudioHintTypeKey];
  AVAudioSessionSilenceSecondaryAudioHintType type =
      (AVAudioSessionSilenceSecondaryAudioHintType)typeNumber.unsignedIntegerValue;
  switch (type) {
    case AVAudioSessionSilenceSecondaryAudioHintTypeBegin:
      RTCLog(@"Another application's primary audio has started.");
      break;
    case AVAudioSessionSilenceSecondaryAudioHintTypeEnd:
      RTCLog(@"Another application's primary audio has stopped.");
      break;
  }
}

- (void)handleApplicationDidBecomeActive:(NSNotification *)notification {
  BOOL isInterrupted = self.isInterrupted;
  RTCLog(@"Application became active after an interruption. Treating as interruption "
          "end. isInterrupted changed from %d to 0.",
         isInterrupted);
  if (isInterrupted) {
    self.isInterrupted = NO;
    [self updateAudioSessionAfterEvent];
  }
  // Always treat application becoming active as an interruption end event.
  [self notifyDidEndInterruptionWithShouldResumeSession:YES];
}

#pragma mark - Private

+ (NSError *)lockError {
  NSDictionary *userInfo =
      @{NSLocalizedDescriptionKey : @"Must call lockForConfiguration before calling this method."};
  NSError *error = [[NSError alloc] initWithDomain:kRTCAudioSessionErrorDomain
                                              code:kRTCAudioSessionErrorLockRequired
                                          userInfo:userInfo];
  return error;
}

- (std::vector<__weak id<RTC_OBJC_TYPE(RTCAudioSessionDelegate)> >)delegates {
  @synchronized(self) {
    // Note: this returns a copy.
    return _delegates;
  }
}

// TODO(tkchin): check for duplicates.
- (void)pushDelegate:(id<RTC_OBJC_TYPE(RTCAudioSessionDelegate)>)delegate {
  @synchronized(self) {
    _delegates.insert(_delegates.begin(), delegate);
  }
}

- (void)removeZeroedDelegates {
  @synchronized(self) {
    _delegates.erase(
        std::remove_if(_delegates.begin(),
                       _delegates.end(),
                       [](id delegate) -> bool { return delegate == nil; }),
        _delegates.end());
  }
}

- (int)activationCount {
  return _activationCount.load();
}

- (int)incrementActivationCount {
  RTCLog(@"Incrementing activation count.");
  return _activationCount.fetch_add(1) + 1;
}

- (NSInteger)decrementActivationCount {
  RTCLog(@"Decrementing activation count.");
  return _activationCount.fetch_sub(1) - 1;
}

- (int)webRTCSessionCount {
  return _webRTCSessionCount.load();
}

- (BOOL)canPlayOrRecord {
  return !self.useManualAudio || self.isAudioEnabled;
}

- (BOOL)isInterrupted {
  @synchronized(self) {
    return _isInterrupted;
  }
}

- (void)setIsInterrupted:(BOOL)isInterrupted {
  @synchronized(self) {
    if (_isInterrupted == isInterrupted) {
      return;
   }
   _isInterrupted = isInterrupted;
  }
}

- (BOOL)checkLock:(NSError **)outError {
  if (!mutex_locked) {
    if (outError) {
      *outError = [RTC_OBJC_TYPE(RTCAudioSession) lockError];
    }
    return NO;
  }
  return YES;
}

- (BOOL)beginWebRTCSession:(NSError **)outError {
  if (outError) {
    *outError = nil;
  }
  _webRTCSessionCount.fetch_add(1);
  [self notifyDidStartPlayOrRecord];
  return YES;
}

- (BOOL)endWebRTCSession:(NSError **)outError {
  if (outError) {
    *outError = nil;
  }
  _webRTCSessionCount.fetch_sub(1);
  [self notifyDidStopPlayOrRecord];
  return YES;
}

- (BOOL)configureWebRTCSession:(NSError **)outError disableRecording:(BOOL)disableRecording {
  if (outError) {
    *outError = nil;
  }
  RTCLog(@"Configuring audio session for WebRTC.");

  // Configure the AVAudioSession and activate it.
  // Provide an error even if there isn't one so we can log it.
  NSError *error = nil;
  RTC_OBJC_TYPE(RTCAudioSessionConfiguration) *webRTCConfig =
      [RTC_OBJC_TYPE(RTCAudioSessionConfiguration) webRTCConfiguration];
  if (![self setConfiguration:webRTCConfig active:YES error:&error disableRecording:disableRecording]) {
    RTCLogError(@"Failed to set WebRTC audio configuration: %@",
                error.localizedDescription);
    // Do not call setActive:NO if setActive:YES failed.
    if (outError) {
      *outError = error;
    }
    return NO;
  }

  // Ensure that the device currently supports audio input.
  // TODO(tkchin): Figure out if this is really necessary.
  if (!self.inputAvailable) {
    RTCLogError(@"No audio input path is available!");
    [self unconfigureWebRTCSession:nil];
    if (outError) {
      *outError = [self configurationErrorWithDescription:@"No input path."];
    }
    return NO;
  }

  // It can happen (e.g. in combination with BT devices) that the attempt to set
  // the preferred sample rate for WebRTC (48kHz) fails. If so, make a new
  // configuration attempt using the sample rate that worked using the active
  // audio session. A typical case is that only 8 or 16kHz can be set, e.g. in
  // combination with BT headsets. Using this "trick" seems to avoid a state
  // where Core Audio asks for a different number of audio frames than what the
  // session's I/O buffer duration corresponds to.
  // TODO(henrika): this fix resolves bugs.webrtc.org/6004 but it has only been
  // tested on a limited set of iOS devices and BT devices.
  double sessionSampleRate = self.sampleRate;
  double preferredSampleRate = webRTCConfig.sampleRate;
  if (sessionSampleRate != preferredSampleRate) {
    RTCLogWarning(
        @"Current sample rate (%.2f) is not the preferred rate (%.2f)",
        sessionSampleRate, preferredSampleRate);
    if (![self setPreferredSampleRate:sessionSampleRate
                                error:&error]) {
      RTCLogError(@"Failed to set preferred sample rate: %@",
                  error.localizedDescription);
      if (outError) {
        *outError = error;
      }
    }
  }

  return YES;
}

- (BOOL)unconfigureWebRTCSession:(NSError **)outError {
  if (outError) {
    *outError = nil;
  }
  RTCLog(@"Unconfiguring audio session for WebRTC.");
  [self setActive:NO error:outError];

  return YES;
}

- (NSError *)configurationErrorWithDescription:(NSString *)description {
  NSDictionary* userInfo = @{
    NSLocalizedDescriptionKey: description,
  };
  return [[NSError alloc] initWithDomain:kRTCAudioSessionErrorDomain
                                    code:kRTCAudioSessionErrorConfiguration
                                userInfo:userInfo];
}

- (void)updateAudioSessionAfterEvent {
  BOOL shouldActivate = self.activationCount > 0;
  AVAudioSessionSetActiveOptions options = shouldActivate ?
      0 : AVAudioSessionSetActiveOptionNotifyOthersOnDeactivation;
  NSError *error = nil;
  if ([self.session setActive:shouldActivate
                  withOptions:options
                        error:&error]) {
    self.isActive = shouldActivate;
  } else {
    RTCLogError(@"Failed to set session active to %d. Error:%@",
                shouldActivate, error.localizedDescription);
  }
}

- (void)updateCanPlayOrRecord {
  BOOL canPlayOrRecord = NO;
  BOOL shouldNotify = NO;
  @synchronized(self) {
    canPlayOrRecord = !self.useManualAudio || self.isAudioEnabled;
    if (_canPlayOrRecord == canPlayOrRecord) {
      return;
    }
    _canPlayOrRecord = canPlayOrRecord;
    shouldNotify = YES;
  }
  if (shouldNotify) {
    [self notifyDidChangeCanPlayOrRecord:canPlayOrRecord];
  }
}

- (void)audioSessionDidActivate:(AVAudioSession *)session {
  if (_session != session) {
    RTCLogError(@"audioSessionDidActivate called on different AVAudioSession");
  }
  RTCLog(@"Audio session was externally activated.");
  [self incrementActivationCount];
  self.isActive = YES;
  // When a CallKit call begins, it's possible that we receive an interruption
  // begin without a corresponding end. Since we know that we have an activated
  // audio session at this point, just clear any saved interruption flag since
  // the app may never be foregrounded during the duration of the call.
  if (self.isInterrupted) {
    RTCLog(@"Clearing interrupted state due to external activation.");
    self.isInterrupted = NO;
  }
  // Treat external audio session activation as an end interruption event.
  [self notifyDidEndInterruptionWithShouldResumeSession:YES];
}

- (void)audioSessionDidDeactivate:(AVAudioSession *)session {
  if (_session != session) {
    RTCLogError(@"audioSessionDidDeactivate called on different AVAudioSession");
  }
  RTCLog(@"Audio session was externally deactivated.");
  self.isActive = NO;
  [self decrementActivationCount];
}

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary *)change
                       context:(void *)context {
  if (context == (__bridge void *)RTC_OBJC_TYPE(RTCAudioSession).class) {
    if (object == _session) {
      NSNumber *newVolume = change[NSKeyValueChangeNewKey];
      RTCLog(@"OutputVolumeDidChange to %f", newVolume.floatValue);
      [self notifyDidChangeOutputVolume:newVolume.floatValue];
    }
  } else {
    [super observeValueForKeyPath:keyPath
                         ofObject:object
                           change:change
                          context:context];
  }
}

- (void)notifyAudioUnitStartFailedWithError:(OSStatus)error {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSession:audioUnitStartFailedWithError:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self
          audioUnitStartFailedWithError:[NSError errorWithDomain:kRTCAudioSessionErrorDomain
                                                            code:error
                                                        userInfo:nil]];
    }
  }
}

- (void)notifyDidBeginInterruption {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionDidBeginInterruption:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionDidBeginInterruption:self];
    }
  }
}

- (void)notifyDidEndInterruptionWithShouldResumeSession:
    (BOOL)shouldResumeSession {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionDidEndInterruption:shouldResumeSession:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionDidEndInterruption:self
                           shouldResumeSession:shouldResumeSession];
    }
  }
}

- (void)notifyDidChangeRouteWithReason:(AVAudioSessionRouteChangeReason)reason
    previousRoute:(AVAudioSessionRouteDescription *)previousRoute {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionDidChangeRoute:reason:previousRoute:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionDidChangeRoute:self
                                    reason:reason
                             previousRoute:previousRoute];
    }
  }
}

- (void)notifyMediaServicesWereLost {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionMediaServerTerminated:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionMediaServerTerminated:self];
    }
  }
}

- (void)notifyMediaServicesWereReset {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionMediaServerReset:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionMediaServerReset:self];
    }
  }
}

- (void)notifyDidChangeCanPlayOrRecord:(BOOL)canPlayOrRecord {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSession:didChangeCanPlayOrRecord:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self didChangeCanPlayOrRecord:canPlayOrRecord];
    }
  }
}

- (void)notifyDidStartPlayOrRecord {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionDidStartPlayOrRecord:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionDidStartPlayOrRecord:self];
    }
  }
}

- (void)notifyDidStopPlayOrRecord {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSessionDidStopPlayOrRecord:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSessionDidStopPlayOrRecord:self];
    }
  }
}

- (void)notifyDidChangeOutputVolume:(float)volume {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSession:didChangeOutputVolume:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self didChangeOutputVolume:volume];
    }
  }
}

- (void)notifyDidDetectPlayoutGlitch:(int64_t)totalNumberOfGlitches {
  for (auto delegate : self.delegates) {
    SEL sel = @selector(audioSession:didDetectPlayoutGlitch:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self didDetectPlayoutGlitch:totalNumberOfGlitches];
    }
  }
}

- (void)notifyWillSetActive:(BOOL)active {
  for (id delegate : self.delegates) {
    SEL sel = @selector(audioSession:willSetActive:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self willSetActive:active];
    }
  }
}

- (void)notifyDidSetActive:(BOOL)active {
  for (id delegate : self.delegates) {
    SEL sel = @selector(audioSession:didSetActive:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self didSetActive:active];
    }
  }
}

- (void)notifyFailedToSetActive:(BOOL)active error:(NSError *)error {
  for (id delegate : self.delegates) {
    SEL sel = @selector(audioSession:failedToSetActive:error:);
    if ([delegate respondsToSelector:sel]) {
      [delegate audioSession:self failedToSetActive:active error:error];
    }
  }
}

@end
