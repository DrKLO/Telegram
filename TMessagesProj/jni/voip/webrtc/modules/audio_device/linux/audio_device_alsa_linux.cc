/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/linux/audio_device_alsa_linux.h"

#include <assert.h>

#include "modules/audio_device/audio_device_config.h"
#include "rtc_base/logging.h"
#include "rtc_base/system/arch.h"
#include "system_wrappers/include/sleep.h"

WebRTCAlsaSymbolTable* GetAlsaSymbolTable() {
  static WebRTCAlsaSymbolTable* alsa_symbol_table = new WebRTCAlsaSymbolTable();
  return alsa_symbol_table;
}

// Accesses ALSA functions through our late-binding symbol table instead of
// directly. This way we don't have to link to libasound, which means our binary
// will work on systems that don't have it.
#define LATE(sym)                                                            \
  LATESYM_GET(webrtc::adm_linux_alsa::AlsaSymbolTable, GetAlsaSymbolTable(), \
              sym)

// Redefine these here to be able to do late-binding
#undef snd_ctl_card_info_alloca
#define snd_ctl_card_info_alloca(ptr)                  \
  do {                                                 \
    *ptr = (snd_ctl_card_info_t*)__builtin_alloca(     \
        LATE(snd_ctl_card_info_sizeof)());             \
    memset(*ptr, 0, LATE(snd_ctl_card_info_sizeof)()); \
  } while (0)

#undef snd_pcm_info_alloca
#define snd_pcm_info_alloca(pInfo)                                           \
  do {                                                                       \
    *pInfo = (snd_pcm_info_t*)__builtin_alloca(LATE(snd_pcm_info_sizeof)()); \
    memset(*pInfo, 0, LATE(snd_pcm_info_sizeof)());                          \
  } while (0)

// snd_lib_error_handler_t
void WebrtcAlsaErrorHandler(const char* file,
                            int line,
                            const char* function,
                            int err,
                            const char* fmt,
                            ...) {}

namespace webrtc {
static const unsigned int ALSA_PLAYOUT_FREQ = 48000;
static const unsigned int ALSA_PLAYOUT_CH = 2;
static const unsigned int ALSA_PLAYOUT_LATENCY = 40 * 1000;  // in us
static const unsigned int ALSA_CAPTURE_FREQ = 48000;
static const unsigned int ALSA_CAPTURE_CH = 2;
static const unsigned int ALSA_CAPTURE_LATENCY = 40 * 1000;  // in us
static const unsigned int ALSA_CAPTURE_WAIT_TIMEOUT = 5;     // in ms

#define FUNC_GET_NUM_OF_DEVICE 0
#define FUNC_GET_DEVICE_NAME 1
#define FUNC_GET_DEVICE_NAME_FOR_AN_ENUM 2

AudioDeviceLinuxALSA::AudioDeviceLinuxALSA()
    : _ptrAudioBuffer(NULL),
      _inputDeviceIndex(0),
      _outputDeviceIndex(0),
      _inputDeviceIsSpecified(false),
      _outputDeviceIsSpecified(false),
      _handleRecord(NULL),
      _handlePlayout(NULL),
      _recordingBuffersizeInFrame(0),
      _recordingPeriodSizeInFrame(0),
      _playoutBufferSizeInFrame(0),
      _playoutPeriodSizeInFrame(0),
      _recordingBufferSizeIn10MS(0),
      _playoutBufferSizeIn10MS(0),
      _recordingFramesIn10MS(0),
      _playoutFramesIn10MS(0),
      _recordingFreq(ALSA_CAPTURE_FREQ),
      _playoutFreq(ALSA_PLAYOUT_FREQ),
      _recChannels(ALSA_CAPTURE_CH),
      _playChannels(ALSA_PLAYOUT_CH),
      _recordingBuffer(NULL),
      _playoutBuffer(NULL),
      _recordingFramesLeft(0),
      _playoutFramesLeft(0),
      _initialized(false),
      _recording(false),
      _playing(false),
      _recIsInitialized(false),
      _playIsInitialized(false),
      _recordingDelay(0),
      _playoutDelay(0) {
  memset(_oldKeyState, 0, sizeof(_oldKeyState));
  RTC_LOG(LS_INFO) << __FUNCTION__ << " created";
}

// ----------------------------------------------------------------------------
//  AudioDeviceLinuxALSA - dtor
// ----------------------------------------------------------------------------

AudioDeviceLinuxALSA::~AudioDeviceLinuxALSA() {
  RTC_LOG(LS_INFO) << __FUNCTION__ << " destroyed";

  Terminate();

  // Clean up the recording buffer and playout buffer.
  if (_recordingBuffer) {
    delete[] _recordingBuffer;
    _recordingBuffer = NULL;
  }
  if (_playoutBuffer) {
    delete[] _playoutBuffer;
    _playoutBuffer = NULL;
  }
}

void AudioDeviceLinuxALSA::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  MutexLock lock(&mutex_);

  _ptrAudioBuffer = audioBuffer;

  // Inform the AudioBuffer about default settings for this implementation.
  // Set all values to zero here since the actual settings will be done by
  // InitPlayout and InitRecording later.
  _ptrAudioBuffer->SetRecordingSampleRate(0);
  _ptrAudioBuffer->SetPlayoutSampleRate(0);
  _ptrAudioBuffer->SetRecordingChannels(0);
  _ptrAudioBuffer->SetPlayoutChannels(0);
}

int32_t AudioDeviceLinuxALSA::ActiveAudioLayer(
    AudioDeviceModule::AudioLayer& audioLayer) const {
  audioLayer = AudioDeviceModule::kLinuxAlsaAudio;
  return 0;
}

AudioDeviceGeneric::InitStatus AudioDeviceLinuxALSA::Init() {
  MutexLock lock(&mutex_);

  // Load libasound
  if (!GetAlsaSymbolTable()->Load()) {
    // Alsa is not installed on this system
    RTC_LOG(LS_ERROR) << "failed to load symbol table";
    return InitStatus::OTHER_ERROR;
  }

  if (_initialized) {
    return InitStatus::OK;
  }
#if defined(WEBRTC_USE_X11)
  // Get X display handle for typing detection
  _XDisplay = XOpenDisplay(NULL);
  if (!_XDisplay) {
    RTC_LOG(LS_WARNING)
        << "failed to open X display, typing detection will not work";
  }
#endif

  _initialized = true;

  return InitStatus::OK;
}

int32_t AudioDeviceLinuxALSA::Terminate() {
  if (!_initialized) {
    return 0;
  }

  MutexLock lock(&mutex_);

  _mixerManager.Close();

  // RECORDING
  if (_ptrThreadRec) {
    rtc::PlatformThread* tmpThread = _ptrThreadRec.release();
    mutex_.Unlock();

    tmpThread->Stop();
    delete tmpThread;

    mutex_.Lock();
  }

  // PLAYOUT
  if (_ptrThreadPlay) {
    rtc::PlatformThread* tmpThread = _ptrThreadPlay.release();
    mutex_.Unlock();

    tmpThread->Stop();
    delete tmpThread;

    mutex_.Lock();
  }
#if defined(WEBRTC_USE_X11)
  if (_XDisplay) {
    XCloseDisplay(_XDisplay);
    _XDisplay = NULL;
  }
#endif
  _initialized = false;
  _outputDeviceIsSpecified = false;
  _inputDeviceIsSpecified = false;

  return 0;
}

bool AudioDeviceLinuxALSA::Initialized() const {
  return (_initialized);
}

int32_t AudioDeviceLinuxALSA::InitSpeaker() {
  MutexLock lock(&mutex_);
  return InitSpeakerLocked();
}

int32_t AudioDeviceLinuxALSA::InitSpeakerLocked() {
  if (_playing) {
    return -1;
  }

  char devName[kAdmMaxDeviceNameSize] = {0};
  GetDevicesInfo(2, true, _outputDeviceIndex, devName, kAdmMaxDeviceNameSize);
  return _mixerManager.OpenSpeaker(devName);
}

int32_t AudioDeviceLinuxALSA::InitMicrophone() {
  MutexLock lock(&mutex_);
  return InitMicrophoneLocked();
}

int32_t AudioDeviceLinuxALSA::InitMicrophoneLocked() {
  if (_recording) {
    return -1;
  }

  char devName[kAdmMaxDeviceNameSize] = {0};
  GetDevicesInfo(2, false, _inputDeviceIndex, devName, kAdmMaxDeviceNameSize);
  return _mixerManager.OpenMicrophone(devName);
}

bool AudioDeviceLinuxALSA::SpeakerIsInitialized() const {
  return (_mixerManager.SpeakerIsInitialized());
}

bool AudioDeviceLinuxALSA::MicrophoneIsInitialized() const {
  return (_mixerManager.MicrophoneIsInitialized());
}

int32_t AudioDeviceLinuxALSA::SpeakerVolumeIsAvailable(bool& available) {
  bool wasInitialized = _mixerManager.SpeakerIsInitialized();

  // Make an attempt to open up the
  // output mixer corresponding to the currently selected output device.
  if (!wasInitialized && InitSpeaker() == -1) {
    // If we end up here it means that the selected speaker has no volume
    // control.
    available = false;
    return 0;
  }

  // Given that InitSpeaker was successful, we know that a volume control
  // exists
  available = true;

  // Close the initialized output mixer
  if (!wasInitialized) {
    _mixerManager.CloseSpeaker();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetSpeakerVolume(uint32_t volume) {
  return (_mixerManager.SetSpeakerVolume(volume));
}

int32_t AudioDeviceLinuxALSA::SpeakerVolume(uint32_t& volume) const {
  uint32_t level(0);

  if (_mixerManager.SpeakerVolume(level) == -1) {
    return -1;
  }

  volume = level;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MaxSpeakerVolume(uint32_t& maxVolume) const {
  uint32_t maxVol(0);

  if (_mixerManager.MaxSpeakerVolume(maxVol) == -1) {
    return -1;
  }

  maxVolume = maxVol;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MinSpeakerVolume(uint32_t& minVolume) const {
  uint32_t minVol(0);

  if (_mixerManager.MinSpeakerVolume(minVol) == -1) {
    return -1;
  }

  minVolume = minVol;

  return 0;
}

int32_t AudioDeviceLinuxALSA::SpeakerMuteIsAvailable(bool& available) {
  bool isAvailable(false);
  bool wasInitialized = _mixerManager.SpeakerIsInitialized();

  // Make an attempt to open up the
  // output mixer corresponding to the currently selected output device.
  //
  if (!wasInitialized && InitSpeaker() == -1) {
    // If we end up here it means that the selected speaker has no volume
    // control, hence it is safe to state that there is no mute control
    // already at this stage.
    available = false;
    return 0;
  }

  // Check if the selected speaker has a mute control
  _mixerManager.SpeakerMuteIsAvailable(isAvailable);

  available = isAvailable;

  // Close the initialized output mixer
  if (!wasInitialized) {
    _mixerManager.CloseSpeaker();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetSpeakerMute(bool enable) {
  return (_mixerManager.SetSpeakerMute(enable));
}

int32_t AudioDeviceLinuxALSA::SpeakerMute(bool& enabled) const {
  bool muted(0);

  if (_mixerManager.SpeakerMute(muted) == -1) {
    return -1;
  }

  enabled = muted;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MicrophoneMuteIsAvailable(bool& available) {
  bool isAvailable(false);
  bool wasInitialized = _mixerManager.MicrophoneIsInitialized();

  // Make an attempt to open up the
  // input mixer corresponding to the currently selected input device.
  //
  if (!wasInitialized && InitMicrophone() == -1) {
    // If we end up here it means that the selected microphone has no volume
    // control, hence it is safe to state that there is no mute control
    // already at this stage.
    available = false;
    return 0;
  }

  // Check if the selected microphone has a mute control
  //
  _mixerManager.MicrophoneMuteIsAvailable(isAvailable);
  available = isAvailable;

  // Close the initialized input mixer
  //
  if (!wasInitialized) {
    _mixerManager.CloseMicrophone();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetMicrophoneMute(bool enable) {
  return (_mixerManager.SetMicrophoneMute(enable));
}

// ----------------------------------------------------------------------------
//  MicrophoneMute
// ----------------------------------------------------------------------------

int32_t AudioDeviceLinuxALSA::MicrophoneMute(bool& enabled) const {
  bool muted(0);

  if (_mixerManager.MicrophoneMute(muted) == -1) {
    return -1;
  }

  enabled = muted;
  return 0;
}

int32_t AudioDeviceLinuxALSA::StereoRecordingIsAvailable(bool& available) {
  MutexLock lock(&mutex_);

  // If we already have initialized in stereo it's obviously available
  if (_recIsInitialized && (2 == _recChannels)) {
    available = true;
    return 0;
  }

  // Save rec states and the number of rec channels
  bool recIsInitialized = _recIsInitialized;
  bool recording = _recording;
  int recChannels = _recChannels;

  available = false;

  // Stop/uninitialize recording if initialized (and possibly started)
  if (_recIsInitialized) {
    StopRecordingLocked();
  }

  // Try init in stereo;
  _recChannels = 2;
  if (InitRecordingLocked() == 0) {
    available = true;
  }

  // Stop/uninitialize recording
  StopRecordingLocked();

  // Recover previous states
  _recChannels = recChannels;
  if (recIsInitialized) {
    InitRecordingLocked();
  }
  if (recording) {
    StartRecording();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetStereoRecording(bool enable) {
  if (enable)
    _recChannels = 2;
  else
    _recChannels = 1;

  return 0;
}

int32_t AudioDeviceLinuxALSA::StereoRecording(bool& enabled) const {
  if (_recChannels == 2)
    enabled = true;
  else
    enabled = false;

  return 0;
}

int32_t AudioDeviceLinuxALSA::StereoPlayoutIsAvailable(bool& available) {
  MutexLock lock(&mutex_);

  // If we already have initialized in stereo it's obviously available
  if (_playIsInitialized && (2 == _playChannels)) {
    available = true;
    return 0;
  }

  // Save rec states and the number of rec channels
  bool playIsInitialized = _playIsInitialized;
  bool playing = _playing;
  int playChannels = _playChannels;

  available = false;

  // Stop/uninitialize recording if initialized (and possibly started)
  if (_playIsInitialized) {
    StopPlayoutLocked();
  }

  // Try init in stereo;
  _playChannels = 2;
  if (InitPlayoutLocked() == 0) {
    available = true;
  }

  // Stop/uninitialize recording
  StopPlayoutLocked();

  // Recover previous states
  _playChannels = playChannels;
  if (playIsInitialized) {
    InitPlayoutLocked();
  }
  if (playing) {
    StartPlayout();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetStereoPlayout(bool enable) {
  if (enable)
    _playChannels = 2;
  else
    _playChannels = 1;

  return 0;
}

int32_t AudioDeviceLinuxALSA::StereoPlayout(bool& enabled) const {
  if (_playChannels == 2)
    enabled = true;
  else
    enabled = false;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MicrophoneVolumeIsAvailable(bool& available) {
  bool wasInitialized = _mixerManager.MicrophoneIsInitialized();

  // Make an attempt to open up the
  // input mixer corresponding to the currently selected output device.
  if (!wasInitialized && InitMicrophone() == -1) {
    // If we end up here it means that the selected microphone has no volume
    // control.
    available = false;
    return 0;
  }

  // Given that InitMicrophone was successful, we know that a volume control
  // exists
  available = true;

  // Close the initialized input mixer
  if (!wasInitialized) {
    _mixerManager.CloseMicrophone();
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetMicrophoneVolume(uint32_t volume) {
  return (_mixerManager.SetMicrophoneVolume(volume));

  return 0;
}

int32_t AudioDeviceLinuxALSA::MicrophoneVolume(uint32_t& volume) const {
  uint32_t level(0);

  if (_mixerManager.MicrophoneVolume(level) == -1) {
    RTC_LOG(LS_WARNING) << "failed to retrive current microphone level";
    return -1;
  }

  volume = level;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MaxMicrophoneVolume(uint32_t& maxVolume) const {
  uint32_t maxVol(0);

  if (_mixerManager.MaxMicrophoneVolume(maxVol) == -1) {
    return -1;
  }

  maxVolume = maxVol;

  return 0;
}

int32_t AudioDeviceLinuxALSA::MinMicrophoneVolume(uint32_t& minVolume) const {
  uint32_t minVol(0);

  if (_mixerManager.MinMicrophoneVolume(minVol) == -1) {
    return -1;
  }

  minVolume = minVol;

  return 0;
}

int16_t AudioDeviceLinuxALSA::PlayoutDevices() {
  return (int16_t)GetDevicesInfo(0, true);
}

int32_t AudioDeviceLinuxALSA::SetPlayoutDevice(uint16_t index) {
  if (_playIsInitialized) {
    return -1;
  }

  uint32_t nDevices = GetDevicesInfo(0, true);
  RTC_LOG(LS_VERBOSE) << "number of available audio output devices is "
                      << nDevices;

  if (index > (nDevices - 1)) {
    RTC_LOG(LS_ERROR) << "device index is out of range [0," << (nDevices - 1)
                      << "]";
    return -1;
  }

  _outputDeviceIndex = index;
  _outputDeviceIsSpecified = true;

  return 0;
}

int32_t AudioDeviceLinuxALSA::SetPlayoutDevice(
    AudioDeviceModule::WindowsDeviceType /*device*/) {
  RTC_LOG(LS_ERROR) << "WindowsDeviceType not supported";
  return -1;
}

int32_t AudioDeviceLinuxALSA::PlayoutDeviceName(
    uint16_t index,
    char name[kAdmMaxDeviceNameSize],
    char guid[kAdmMaxGuidSize]) {
  const uint16_t nDevices(PlayoutDevices());

  if ((index > (nDevices - 1)) || (name == NULL)) {
    return -1;
  }

  memset(name, 0, kAdmMaxDeviceNameSize);

  if (guid != NULL) {
    memset(guid, 0, kAdmMaxGuidSize);
  }

  return GetDevicesInfo(1, true, index, name, kAdmMaxDeviceNameSize);
}

int32_t AudioDeviceLinuxALSA::RecordingDeviceName(
    uint16_t index,
    char name[kAdmMaxDeviceNameSize],
    char guid[kAdmMaxGuidSize]) {
  const uint16_t nDevices(RecordingDevices());

  if ((index > (nDevices - 1)) || (name == NULL)) {
    return -1;
  }

  memset(name, 0, kAdmMaxDeviceNameSize);

  if (guid != NULL) {
    memset(guid, 0, kAdmMaxGuidSize);
  }

  return GetDevicesInfo(1, false, index, name, kAdmMaxDeviceNameSize);
}

int16_t AudioDeviceLinuxALSA::RecordingDevices() {
  return (int16_t)GetDevicesInfo(0, false);
}

int32_t AudioDeviceLinuxALSA::SetRecordingDevice(uint16_t index) {
  if (_recIsInitialized) {
    return -1;
  }

  uint32_t nDevices = GetDevicesInfo(0, false);
  RTC_LOG(LS_VERBOSE) << "number of availiable audio input devices is "
                      << nDevices;

  if (index > (nDevices - 1)) {
    RTC_LOG(LS_ERROR) << "device index is out of range [0," << (nDevices - 1)
                      << "]";
    return -1;
  }

  _inputDeviceIndex = index;
  _inputDeviceIsSpecified = true;

  return 0;
}

// ----------------------------------------------------------------------------
//  SetRecordingDevice II (II)
// ----------------------------------------------------------------------------

int32_t AudioDeviceLinuxALSA::SetRecordingDevice(
    AudioDeviceModule::WindowsDeviceType /*device*/) {
  RTC_LOG(LS_ERROR) << "WindowsDeviceType not supported";
  return -1;
}

int32_t AudioDeviceLinuxALSA::PlayoutIsAvailable(bool& available) {
  available = false;

  // Try to initialize the playout side with mono
  // Assumes that user set num channels after calling this function
  _playChannels = 1;
  int32_t res = InitPlayout();

  // Cancel effect of initialization
  StopPlayout();

  if (res != -1) {
    available = true;
  } else {
    // It may be possible to play out in stereo
    res = StereoPlayoutIsAvailable(available);
    if (available) {
      // Then set channels to 2 so InitPlayout doesn't fail
      _playChannels = 2;
    }
  }

  return res;
}

int32_t AudioDeviceLinuxALSA::RecordingIsAvailable(bool& available) {
  available = false;

  // Try to initialize the recording side with mono
  // Assumes that user set num channels after calling this function
  _recChannels = 1;
  int32_t res = InitRecording();

  // Cancel effect of initialization
  StopRecording();

  if (res != -1) {
    available = true;
  } else {
    // It may be possible to record in stereo
    res = StereoRecordingIsAvailable(available);
    if (available) {
      // Then set channels to 2 so InitPlayout doesn't fail
      _recChannels = 2;
    }
  }

  return res;
}

int32_t AudioDeviceLinuxALSA::InitPlayout() {
  MutexLock lock(&mutex_);
  return InitPlayoutLocked();
}

int32_t AudioDeviceLinuxALSA::InitPlayoutLocked() {
  int errVal = 0;

  if (_playing) {
    return -1;
  }

  if (!_outputDeviceIsSpecified) {
    return -1;
  }

  if (_playIsInitialized) {
    return 0;
  }
  // Initialize the speaker (devices might have been added or removed)
  if (InitSpeakerLocked() == -1) {
    RTC_LOG(LS_WARNING) << "InitSpeaker() failed";
  }

  // Start by closing any existing wave-output devices
  //
  if (_handlePlayout != NULL) {
    LATE(snd_pcm_close)(_handlePlayout);
    _handlePlayout = NULL;
    _playIsInitialized = false;
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "Error closing current playout sound device, error: "
                        << LATE(snd_strerror)(errVal);
    }
  }

  // Open PCM device for playout
  char deviceName[kAdmMaxDeviceNameSize] = {0};
  GetDevicesInfo(2, true, _outputDeviceIndex, deviceName,
                 kAdmMaxDeviceNameSize);

  RTC_LOG(LS_VERBOSE) << "InitPlayout open (" << deviceName << ")";

  errVal = LATE(snd_pcm_open)(&_handlePlayout, deviceName,
                              SND_PCM_STREAM_PLAYBACK, SND_PCM_NONBLOCK);

  if (errVal == -EBUSY)  // Device busy - try some more!
  {
    for (int i = 0; i < 5; i++) {
      SleepMs(1000);
      errVal = LATE(snd_pcm_open)(&_handlePlayout, deviceName,
                                  SND_PCM_STREAM_PLAYBACK, SND_PCM_NONBLOCK);
      if (errVal == 0) {
        break;
      }
    }
  }
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "unable to open playback device: "
                      << LATE(snd_strerror)(errVal) << " (" << errVal << ")";
    _handlePlayout = NULL;
    return -1;
  }

  _playoutFramesIn10MS = _playoutFreq / 100;
  if ((errVal = LATE(snd_pcm_set_params)(
           _handlePlayout,
#if defined(WEBRTC_ARCH_BIG_ENDIAN)
           SND_PCM_FORMAT_S16_BE,
#else
           SND_PCM_FORMAT_S16_LE,                             // format
#endif
           SND_PCM_ACCESS_RW_INTERLEAVED,  // access
           _playChannels,                  // channels
           _playoutFreq,                   // rate
           1,                              // soft_resample
           ALSA_PLAYOUT_LATENCY  // 40*1000 //latency required overall latency
                                 // in us
           )) < 0) {             /* 0.5sec */
    _playoutFramesIn10MS = 0;
    RTC_LOG(LS_ERROR) << "unable to set playback device: "
                      << LATE(snd_strerror)(errVal) << " (" << errVal << ")";
    ErrorRecovery(errVal, _handlePlayout);
    errVal = LATE(snd_pcm_close)(_handlePlayout);
    _handlePlayout = NULL;
    return -1;
  }

  errVal = LATE(snd_pcm_get_params)(_handlePlayout, &_playoutBufferSizeInFrame,
                                    &_playoutPeriodSizeInFrame);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_pcm_get_params: " << LATE(snd_strerror)(errVal)
                      << " (" << errVal << ")";
    _playoutBufferSizeInFrame = 0;
    _playoutPeriodSizeInFrame = 0;
  } else {
    RTC_LOG(LS_VERBOSE) << "playout snd_pcm_get_params buffer_size:"
                        << _playoutBufferSizeInFrame
                        << " period_size :" << _playoutPeriodSizeInFrame;
  }

  if (_ptrAudioBuffer) {
    // Update webrtc audio buffer with the selected parameters
    _ptrAudioBuffer->SetPlayoutSampleRate(_playoutFreq);
    _ptrAudioBuffer->SetPlayoutChannels(_playChannels);
  }

  // Set play buffer size
  _playoutBufferSizeIn10MS =
      LATE(snd_pcm_frames_to_bytes)(_handlePlayout, _playoutFramesIn10MS);

  // Init varaibles used for play

  if (_handlePlayout != NULL) {
    _playIsInitialized = true;
    return 0;
  } else {
    return -1;
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::InitRecording() {
  MutexLock lock(&mutex_);
  return InitRecordingLocked();
}

int32_t AudioDeviceLinuxALSA::InitRecordingLocked() {
  int errVal = 0;

  if (_recording) {
    return -1;
  }

  if (!_inputDeviceIsSpecified) {
    return -1;
  }

  if (_recIsInitialized) {
    return 0;
  }

  // Initialize the microphone (devices might have been added or removed)
  if (InitMicrophoneLocked() == -1) {
    RTC_LOG(LS_WARNING) << "InitMicrophone() failed";
  }

  // Start by closing any existing pcm-input devices
  //
  if (_handleRecord != NULL) {
    int errVal = LATE(snd_pcm_close)(_handleRecord);
    _handleRecord = NULL;
    _recIsInitialized = false;
    if (errVal < 0) {
      RTC_LOG(LS_ERROR)
          << "Error closing current recording sound device, error: "
          << LATE(snd_strerror)(errVal);
    }
  }

  // Open PCM device for recording
  // The corresponding settings for playout are made after the record settings
  char deviceName[kAdmMaxDeviceNameSize] = {0};
  GetDevicesInfo(2, false, _inputDeviceIndex, deviceName,
                 kAdmMaxDeviceNameSize);

  RTC_LOG(LS_VERBOSE) << "InitRecording open (" << deviceName << ")";
  errVal = LATE(snd_pcm_open)(&_handleRecord, deviceName,
                              SND_PCM_STREAM_CAPTURE, SND_PCM_NONBLOCK);

  // Available modes: 0 = blocking, SND_PCM_NONBLOCK, SND_PCM_ASYNC
  if (errVal == -EBUSY)  // Device busy - try some more!
  {
    for (int i = 0; i < 5; i++) {
      SleepMs(1000);
      errVal = LATE(snd_pcm_open)(&_handleRecord, deviceName,
                                  SND_PCM_STREAM_CAPTURE, SND_PCM_NONBLOCK);
      if (errVal == 0) {
        break;
      }
    }
  }
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "unable to open record device: "
                      << LATE(snd_strerror)(errVal);
    _handleRecord = NULL;
    return -1;
  }

  _recordingFramesIn10MS = _recordingFreq / 100;
  if ((errVal =
           LATE(snd_pcm_set_params)(_handleRecord,
#if defined(WEBRTC_ARCH_BIG_ENDIAN)
                                    SND_PCM_FORMAT_S16_BE,  // format
#else
                                    SND_PCM_FORMAT_S16_LE,    // format
#endif
                                    SND_PCM_ACCESS_RW_INTERLEAVED,  // access
                                    _recChannels,                   // channels
                                    _recordingFreq,                 // rate
                                    1,                    // soft_resample
                                    ALSA_CAPTURE_LATENCY  // latency in us
                                    )) < 0) {
    // Fall back to another mode then.
    if (_recChannels == 1)
      _recChannels = 2;
    else
      _recChannels = 1;

    if ((errVal =
             LATE(snd_pcm_set_params)(_handleRecord,
#if defined(WEBRTC_ARCH_BIG_ENDIAN)
                                      SND_PCM_FORMAT_S16_BE,  // format
#else
                                      SND_PCM_FORMAT_S16_LE,  // format
#endif
                                      SND_PCM_ACCESS_RW_INTERLEAVED,  // access
                                      _recChannels,         // channels
                                      _recordingFreq,       // rate
                                      1,                    // soft_resample
                                      ALSA_CAPTURE_LATENCY  // latency in us
                                      )) < 0) {
      _recordingFramesIn10MS = 0;
      RTC_LOG(LS_ERROR) << "unable to set record settings: "
                        << LATE(snd_strerror)(errVal) << " (" << errVal << ")";
      ErrorRecovery(errVal, _handleRecord);
      errVal = LATE(snd_pcm_close)(_handleRecord);
      _handleRecord = NULL;
      return -1;
    }
  }

  errVal = LATE(snd_pcm_get_params)(_handleRecord, &_recordingBuffersizeInFrame,
                                    &_recordingPeriodSizeInFrame);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "snd_pcm_get_params " << LATE(snd_strerror)(errVal)
                      << " (" << errVal << ")";
    _recordingBuffersizeInFrame = 0;
    _recordingPeriodSizeInFrame = 0;
  } else {
    RTC_LOG(LS_VERBOSE) << "capture snd_pcm_get_params, buffer_size:"
                        << _recordingBuffersizeInFrame
                        << ", period_size:" << _recordingPeriodSizeInFrame;
  }

  if (_ptrAudioBuffer) {
    // Update webrtc audio buffer with the selected parameters
    _ptrAudioBuffer->SetRecordingSampleRate(_recordingFreq);
    _ptrAudioBuffer->SetRecordingChannels(_recChannels);
  }

  // Set rec buffer size and create buffer
  _recordingBufferSizeIn10MS =
      LATE(snd_pcm_frames_to_bytes)(_handleRecord, _recordingFramesIn10MS);

  if (_handleRecord != NULL) {
    // Mark recording side as initialized
    _recIsInitialized = true;
    return 0;
  } else {
    return -1;
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::StartRecording() {
  if (!_recIsInitialized) {
    return -1;
  }

  if (_recording) {
    return 0;
  }

  _recording = true;

  int errVal = 0;
  _recordingFramesLeft = _recordingFramesIn10MS;

  // Make sure we only create the buffer once.
  if (!_recordingBuffer)
    _recordingBuffer = new int8_t[_recordingBufferSizeIn10MS];
  if (!_recordingBuffer) {
    RTC_LOG(LS_ERROR) << "failed to alloc recording buffer";
    _recording = false;
    return -1;
  }
  // RECORDING
  _ptrThreadRec.reset(new rtc::PlatformThread(
      RecThreadFunc, this, "webrtc_audio_module_capture_thread",
      rtc::kRealtimePriority));

  _ptrThreadRec->Start();

  errVal = LATE(snd_pcm_prepare)(_handleRecord);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "capture snd_pcm_prepare failed ("
                      << LATE(snd_strerror)(errVal) << ")\n";
    // just log error
    // if snd_pcm_open fails will return -1
  }

  errVal = LATE(snd_pcm_start)(_handleRecord);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "capture snd_pcm_start err: "
                      << LATE(snd_strerror)(errVal);
    errVal = LATE(snd_pcm_start)(_handleRecord);
    if (errVal < 0) {
      RTC_LOG(LS_ERROR) << "capture snd_pcm_start 2nd try err: "
                        << LATE(snd_strerror)(errVal);
      StopRecording();
      return -1;
    }
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::StopRecording() {
    MutexLock lock(&mutex_);
    return StopRecordingLocked();
}

int32_t AudioDeviceLinuxALSA::StopRecordingLocked() {
  if (!_recIsInitialized) {
    return 0;
  }

  if (_handleRecord == NULL) {
    return -1;
  }

  // Make sure we don't start recording (it's asynchronous).
  _recIsInitialized = false;
  _recording = false;

  if (_ptrThreadRec) {
    _ptrThreadRec->Stop();
    _ptrThreadRec.reset();
  }

  _recordingFramesLeft = 0;
  if (_recordingBuffer) {
    delete[] _recordingBuffer;
    _recordingBuffer = NULL;
  }

  // Stop and close pcm recording device.
  int errVal = LATE(snd_pcm_drop)(_handleRecord);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error stop recording: " << LATE(snd_strerror)(errVal);
    return -1;
  }

  errVal = LATE(snd_pcm_close)(_handleRecord);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error closing record sound device, error: "
                      << LATE(snd_strerror)(errVal);
    return -1;
  }

  // Check if we have muted and unmute if so.
  bool muteEnabled = false;
  MicrophoneMute(muteEnabled);
  if (muteEnabled) {
    SetMicrophoneMute(false);
  }

  // set the pcm input handle to NULL
  _handleRecord = NULL;
  return 0;
}

bool AudioDeviceLinuxALSA::RecordingIsInitialized() const {
  return (_recIsInitialized);
}

bool AudioDeviceLinuxALSA::Recording() const {
  return (_recording);
}

bool AudioDeviceLinuxALSA::PlayoutIsInitialized() const {
  return (_playIsInitialized);
}

int32_t AudioDeviceLinuxALSA::StartPlayout() {
  if (!_playIsInitialized) {
    return -1;
  }

  if (_playing) {
    return 0;
  }

  _playing = true;

  _playoutFramesLeft = 0;
  if (!_playoutBuffer)
    _playoutBuffer = new int8_t[_playoutBufferSizeIn10MS];
  if (!_playoutBuffer) {
    RTC_LOG(LS_ERROR) << "failed to alloc playout buf";
    _playing = false;
    return -1;
  }

  // PLAYOUT
  _ptrThreadPlay.reset(new rtc::PlatformThread(
      PlayThreadFunc, this, "webrtc_audio_module_play_thread",
      rtc::kRealtimePriority));
  _ptrThreadPlay->Start();

  int errVal = LATE(snd_pcm_prepare)(_handlePlayout);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "playout snd_pcm_prepare failed ("
                      << LATE(snd_strerror)(errVal) << ")\n";
    // just log error
    // if snd_pcm_open fails will return -1
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::StopPlayout() {
    MutexLock lock(&mutex_);
    return StopPlayoutLocked();
}

int32_t AudioDeviceLinuxALSA::StopPlayoutLocked() {
  if (!_playIsInitialized) {
    return 0;
  }

  if (_handlePlayout == NULL) {
    return -1;
  }

  _playing = false;

  // stop playout thread first
  if (_ptrThreadPlay) {
    _ptrThreadPlay->Stop();
    _ptrThreadPlay.reset();
  }

  _playoutFramesLeft = 0;
  delete[] _playoutBuffer;
  _playoutBuffer = NULL;

  // stop and close pcm playout device
  int errVal = LATE(snd_pcm_drop)(_handlePlayout);
  if (errVal < 0) {
    RTC_LOG(LS_ERROR) << "Error stop playing: " << LATE(snd_strerror)(errVal);
  }

  errVal = LATE(snd_pcm_close)(_handlePlayout);
  if (errVal < 0)
    RTC_LOG(LS_ERROR) << "Error closing playout sound device, error: "
                      << LATE(snd_strerror)(errVal);

  // set the pcm input handle to NULL
  _playIsInitialized = false;
  _handlePlayout = NULL;
  RTC_LOG(LS_VERBOSE) << "handle_playout is now set to NULL";

  return 0;
}

int32_t AudioDeviceLinuxALSA::PlayoutDelay(uint16_t& delayMS) const {
  delayMS = (uint16_t)_playoutDelay * 1000 / _playoutFreq;
  return 0;
}

bool AudioDeviceLinuxALSA::Playing() const {
  return (_playing);
}

// ============================================================================
//                                 Private Methods
// ============================================================================

int32_t AudioDeviceLinuxALSA::GetDevicesInfo(const int32_t function,
                                             const bool playback,
                                             const int32_t enumDeviceNo,
                                             char* enumDeviceName,
                                             const int32_t ednLen) const {
  // Device enumeration based on libjingle implementation
  // by Tristan Schmelcher at Google Inc.

  const char* type = playback ? "Output" : "Input";
  // dmix and dsnoop are only for playback and capture, respectively, but ALSA
  // stupidly includes them in both lists.
  const char* ignorePrefix = playback ? "dsnoop:" : "dmix:";
  // (ALSA lists many more "devices" of questionable interest, but we show them
  // just in case the weird devices may actually be desirable for some
  // users/systems.)

  int err;
  int enumCount(0);
  bool keepSearching(true);

  // From Chromium issue 95797
  // Loop through the sound cards to get Alsa device hints.
  // Don't use snd_device_name_hint(-1,..) since there is a access violation
  // inside this ALSA API with libasound.so.2.0.0.
  int card = -1;
  while (!(LATE(snd_card_next)(&card)) && (card >= 0) && keepSearching) {
    void** hints;
    err = LATE(snd_device_name_hint)(card, "pcm", &hints);
    if (err != 0) {
      RTC_LOG(LS_ERROR) << "GetDevicesInfo - device name hint error: "
                        << LATE(snd_strerror)(err);
      return -1;
    }

    enumCount++;  // default is 0
    if ((function == FUNC_GET_DEVICE_NAME ||
         function == FUNC_GET_DEVICE_NAME_FOR_AN_ENUM) &&
        enumDeviceNo == 0) {
      strcpy(enumDeviceName, "default");

      err = LATE(snd_device_name_free_hint)(hints);
      if (err != 0) {
        RTC_LOG(LS_ERROR) << "GetDevicesInfo - device name free hint error: "
                          << LATE(snd_strerror)(err);
      }

      return 0;
    }

    for (void** list = hints; *list != NULL; ++list) {
      char* actualType = LATE(snd_device_name_get_hint)(*list, "IOID");
      if (actualType) {  // NULL means it's both.
        bool wrongType = (strcmp(actualType, type) != 0);
        free(actualType);
        if (wrongType) {
          // Wrong type of device (i.e., input vs. output).
          continue;
        }
      }

      char* name = LATE(snd_device_name_get_hint)(*list, "NAME");
      if (!name) {
        RTC_LOG(LS_ERROR) << "Device has no name";
        // Skip it.
        continue;
      }

      // Now check if we actually want to show this device.
      if (strcmp(name, "default") != 0 && strcmp(name, "null") != 0 &&
          strcmp(name, "pulse") != 0 &&
          strncmp(name, ignorePrefix, strlen(ignorePrefix)) != 0) {
        // Yes, we do.
        char* desc = LATE(snd_device_name_get_hint)(*list, "DESC");
        if (!desc) {
          // Virtual devices don't necessarily have descriptions.
          // Use their names instead.
          desc = name;
        }

        if (FUNC_GET_NUM_OF_DEVICE == function) {
          RTC_LOG(LS_VERBOSE) << "Enum device " << enumCount << " - " << name;
        }
        if ((FUNC_GET_DEVICE_NAME == function) && (enumDeviceNo == enumCount)) {
          // We have found the enum device, copy the name to buffer.
          strncpy(enumDeviceName, desc, ednLen);
          enumDeviceName[ednLen - 1] = '\0';
          keepSearching = false;
          // Replace '\n' with '-'.
          char* pret = strchr(enumDeviceName, '\n' /*0xa*/);  // LF
          if (pret)
            *pret = '-';
        }
        if ((FUNC_GET_DEVICE_NAME_FOR_AN_ENUM == function) &&
            (enumDeviceNo == enumCount)) {
          // We have found the enum device, copy the name to buffer.
          strncpy(enumDeviceName, name, ednLen);
          enumDeviceName[ednLen - 1] = '\0';
          keepSearching = false;
        }

        if (keepSearching)
          ++enumCount;

        if (desc != name)
          free(desc);
      }

      free(name);

      if (!keepSearching)
        break;
    }

    err = LATE(snd_device_name_free_hint)(hints);
    if (err != 0) {
      RTC_LOG(LS_ERROR) << "GetDevicesInfo - device name free hint error: "
                        << LATE(snd_strerror)(err);
      // Continue and return true anyway, since we did get the whole list.
    }
  }

  if (FUNC_GET_NUM_OF_DEVICE == function) {
    if (enumCount == 1)  // only default?
      enumCount = 0;
    return enumCount;  // Normal return point for function 0
  }

  if (keepSearching) {
    // If we get here for function 1 and 2, we didn't find the specified
    // enum device.
    RTC_LOG(LS_ERROR)
        << "GetDevicesInfo - Could not find device name or numbers";
    return -1;
  }

  return 0;
}

int32_t AudioDeviceLinuxALSA::InputSanityCheckAfterUnlockedPeriod() const {
  if (_handleRecord == NULL) {
    RTC_LOG(LS_ERROR) << "input state has been modified during unlocked period";
    return -1;
  }
  return 0;
}

int32_t AudioDeviceLinuxALSA::OutputSanityCheckAfterUnlockedPeriod() const {
  if (_handlePlayout == NULL) {
    RTC_LOG(LS_ERROR)
        << "output state has been modified during unlocked period";
    return -1;
  }
  return 0;
}

int32_t AudioDeviceLinuxALSA::ErrorRecovery(int32_t error,
                                            snd_pcm_t* deviceHandle) {
  int st = LATE(snd_pcm_state)(deviceHandle);
  RTC_LOG(LS_VERBOSE) << "Trying to recover from "
                      << ((LATE(snd_pcm_stream)(deviceHandle) ==
                           SND_PCM_STREAM_CAPTURE)
                              ? "capture"
                              : "playout")
                      << " error: " << LATE(snd_strerror)(error) << " ("
                      << error << ") (state " << st << ")";

  // It is recommended to use snd_pcm_recover for all errors. If that function
  // cannot handle the error, the input error code will be returned, otherwise
  // 0 is returned. From snd_pcm_recover API doc: "This functions handles
  // -EINTR (4) (interrupted system call), -EPIPE (32) (playout overrun or
  // capture underrun) and -ESTRPIPE (86) (stream is suspended) error codes
  // trying to prepare given stream for next I/O."

  /** Open */
  //    SND_PCM_STATE_OPEN = 0,
  /** Setup installed */
  //    SND_PCM_STATE_SETUP,
  /** Ready to start */
  //    SND_PCM_STATE_PREPARED,
  /** Running */
  //    SND_PCM_STATE_RUNNING,
  /** Stopped: underrun (playback) or overrun (capture) detected */
  //    SND_PCM_STATE_XRUN,= 4
  /** Draining: running (playback) or stopped (capture) */
  //    SND_PCM_STATE_DRAINING,
  /** Paused */
  //    SND_PCM_STATE_PAUSED,
  /** Hardware is suspended */
  //    SND_PCM_STATE_SUSPENDED,
  //  ** Hardware is disconnected */
  //    SND_PCM_STATE_DISCONNECTED,
  //    SND_PCM_STATE_LAST = SND_PCM_STATE_DISCONNECTED

  // snd_pcm_recover isn't available in older alsa, e.g. on the FC4 machine
  // in Sthlm lab.

  int res = LATE(snd_pcm_recover)(deviceHandle, error, 1);
  if (0 == res) {
    RTC_LOG(LS_VERBOSE) << "Recovery - snd_pcm_recover OK";

    if ((error == -EPIPE || error == -ESTRPIPE) &&  // Buf underrun/overrun.
        _recording &&
        LATE(snd_pcm_stream)(deviceHandle) == SND_PCM_STREAM_CAPTURE) {
      // For capture streams we also have to repeat the explicit start()
      // to get data flowing again.
      int err = LATE(snd_pcm_start)(deviceHandle);
      if (err != 0) {
        RTC_LOG(LS_ERROR) << "Recovery - snd_pcm_start error: " << err;
        return -1;
      }
    }

    if ((error == -EPIPE || error == -ESTRPIPE) &&  // Buf underrun/overrun.
        _playing &&
        LATE(snd_pcm_stream)(deviceHandle) == SND_PCM_STREAM_PLAYBACK) {
      // For capture streams we also have to repeat the explicit start() to get
      // data flowing again.
      int err = LATE(snd_pcm_start)(deviceHandle);
      if (err != 0) {
        RTC_LOG(LS_ERROR) << "Recovery - snd_pcm_start error: "
                          << LATE(snd_strerror)(err);
        return -1;
      }
    }

    return -EPIPE == error ? 1 : 0;
  } else {
    RTC_LOG(LS_ERROR) << "Unrecoverable alsa stream error: " << res;
  }

  return res;
}

// ============================================================================
//                                  Thread Methods
// ============================================================================

void AudioDeviceLinuxALSA::PlayThreadFunc(void* pThis) {
  AudioDeviceLinuxALSA* device = static_cast<AudioDeviceLinuxALSA*>(pThis);
  while (device->PlayThreadProcess()) {
  }
}

void AudioDeviceLinuxALSA::RecThreadFunc(void* pThis) {
  AudioDeviceLinuxALSA* device = static_cast<AudioDeviceLinuxALSA*>(pThis);
  while (device->RecThreadProcess()) {
  }
}

bool AudioDeviceLinuxALSA::PlayThreadProcess() {
  if (!_playing)
    return false;

  int err;
  snd_pcm_sframes_t frames;
  snd_pcm_sframes_t avail_frames;

  Lock();
  // return a positive number of frames ready otherwise a negative error code
  avail_frames = LATE(snd_pcm_avail_update)(_handlePlayout);
  if (avail_frames < 0) {
    RTC_LOG(LS_ERROR) << "playout snd_pcm_avail_update error: "
                      << LATE(snd_strerror)(avail_frames);
    ErrorRecovery(avail_frames, _handlePlayout);
    UnLock();
    return true;
  } else if (avail_frames == 0) {
    UnLock();

    // maximum tixe in milliseconds to wait, a negative value means infinity
    err = LATE(snd_pcm_wait)(_handlePlayout, 2);
    if (err == 0) {  // timeout occured
      RTC_LOG(LS_VERBOSE) << "playout snd_pcm_wait timeout";
    }

    return true;
  }

  if (_playoutFramesLeft <= 0) {
    UnLock();
    _ptrAudioBuffer->RequestPlayoutData(_playoutFramesIn10MS);
    Lock();

    _playoutFramesLeft = _ptrAudioBuffer->GetPlayoutData(_playoutBuffer);
    assert(_playoutFramesLeft == _playoutFramesIn10MS);
  }

  if (static_cast<uint32_t>(avail_frames) > _playoutFramesLeft)
    avail_frames = _playoutFramesLeft;

  int size = LATE(snd_pcm_frames_to_bytes)(_handlePlayout, _playoutFramesLeft);
  frames = LATE(snd_pcm_writei)(
      _handlePlayout, &_playoutBuffer[_playoutBufferSizeIn10MS - size],
      avail_frames);

  if (frames < 0) {
    RTC_LOG(LS_VERBOSE) << "playout snd_pcm_writei error: "
                        << LATE(snd_strerror)(frames);
    _playoutFramesLeft = 0;
    ErrorRecovery(frames, _handlePlayout);
    UnLock();
    return true;
  } else {
    assert(frames == avail_frames);
    _playoutFramesLeft -= frames;
  }

  UnLock();
  return true;
}

bool AudioDeviceLinuxALSA::RecThreadProcess() {
  if (!_recording)
    return false;

  int err;
  snd_pcm_sframes_t frames;
  snd_pcm_sframes_t avail_frames;
  int8_t buffer[_recordingBufferSizeIn10MS];

  Lock();

  // return a positive number of frames ready otherwise a negative error code
  avail_frames = LATE(snd_pcm_avail_update)(_handleRecord);
  if (avail_frames < 0) {
    RTC_LOG(LS_ERROR) << "capture snd_pcm_avail_update error: "
                      << LATE(snd_strerror)(avail_frames);
    ErrorRecovery(avail_frames, _handleRecord);
    UnLock();
    return true;
  } else if (avail_frames == 0) {  // no frame is available now
    UnLock();

    // maximum time in milliseconds to wait, a negative value means infinity
    err = LATE(snd_pcm_wait)(_handleRecord, ALSA_CAPTURE_WAIT_TIMEOUT);
    if (err == 0)  // timeout occured
      RTC_LOG(LS_VERBOSE) << "capture snd_pcm_wait timeout";

    return true;
  }

  if (static_cast<uint32_t>(avail_frames) > _recordingFramesLeft)
    avail_frames = _recordingFramesLeft;

  frames = LATE(snd_pcm_readi)(_handleRecord, buffer,
                               avail_frames);  // frames to be written
  if (frames < 0) {
    RTC_LOG(LS_ERROR) << "capture snd_pcm_readi error: "
                      << LATE(snd_strerror)(frames);
    ErrorRecovery(frames, _handleRecord);
    UnLock();
    return true;
  } else if (frames > 0) {
    assert(frames == avail_frames);

    int left_size =
        LATE(snd_pcm_frames_to_bytes)(_handleRecord, _recordingFramesLeft);
    int size = LATE(snd_pcm_frames_to_bytes)(_handleRecord, frames);

    memcpy(&_recordingBuffer[_recordingBufferSizeIn10MS - left_size], buffer,
           size);
    _recordingFramesLeft -= frames;

    if (!_recordingFramesLeft) {  // buf is full
      _recordingFramesLeft = _recordingFramesIn10MS;

      // store the recorded buffer (no action will be taken if the
      // #recorded samples is not a full buffer)
      _ptrAudioBuffer->SetRecordedBuffer(_recordingBuffer,
                                         _recordingFramesIn10MS);

      // calculate delay
      _playoutDelay = 0;
      _recordingDelay = 0;
      if (_handlePlayout) {
        err = LATE(snd_pcm_delay)(_handlePlayout,
                                  &_playoutDelay);  // returned delay in frames
        if (err < 0) {
          // TODO(xians): Shall we call ErrorRecovery() here?
          _playoutDelay = 0;
          RTC_LOG(LS_ERROR)
              << "playout snd_pcm_delay: " << LATE(snd_strerror)(err);
        }
      }

      err = LATE(snd_pcm_delay)(_handleRecord,
                                &_recordingDelay);  // returned delay in frames
      if (err < 0) {
        // TODO(xians): Shall we call ErrorRecovery() here?
        _recordingDelay = 0;
        RTC_LOG(LS_ERROR) << "capture snd_pcm_delay: "
                          << LATE(snd_strerror)(err);
      }

      // TODO(xians): Shall we add 10ms buffer delay to the record delay?
      _ptrAudioBuffer->SetVQEData(_playoutDelay * 1000 / _playoutFreq,
                                  _recordingDelay * 1000 / _recordingFreq);

      _ptrAudioBuffer->SetTypingStatus(KeyPressed());

      // Deliver recorded samples at specified sample rate, mic level etc.
      // to the observer using callback.
      UnLock();
      _ptrAudioBuffer->DeliverRecordedData();
      Lock();
    }
  }

  UnLock();
  return true;
}

bool AudioDeviceLinuxALSA::KeyPressed() const {
#if defined(WEBRTC_USE_X11)
  char szKey[32];
  unsigned int i = 0;
  char state = 0;

  if (!_XDisplay)
    return false;

  // Check key map status
  XQueryKeymap(_XDisplay, szKey);

  // A bit change in keymap means a key is pressed
  for (i = 0; i < sizeof(szKey); i++)
    state |= (szKey[i] ^ _oldKeyState[i]) & szKey[i];

  // Save old state
  memcpy((char*)_oldKeyState, (char*)szKey, sizeof(_oldKeyState));
  return (state != 0);
#else
  return false;
#endif
}
}  // namespace webrtc
