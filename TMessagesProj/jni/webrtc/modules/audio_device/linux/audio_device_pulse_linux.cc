/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/linux/audio_device_pulse_linux.h"

#include <string.h>

#include "modules/audio_device/linux/latebindingsymboltable_linux.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

WebRTCPulseSymbolTable* GetPulseSymbolTable() {
  static WebRTCPulseSymbolTable* pulse_symbol_table =
      new WebRTCPulseSymbolTable();
  return pulse_symbol_table;
}

// Accesses Pulse functions through our late-binding symbol table instead of
// directly. This way we don't have to link to libpulse, which means our binary
// will work on systems that don't have it.
#define LATE(sym)                                             \
  LATESYM_GET(webrtc::adm_linux_pulse::PulseAudioSymbolTable, \
              GetPulseSymbolTable(), sym)

namespace webrtc {

AudioDeviceLinuxPulse::AudioDeviceLinuxPulse()
    : _ptrAudioBuffer(NULL),
      _inputDeviceIndex(0),
      _outputDeviceIndex(0),
      _inputDeviceIsSpecified(false),
      _outputDeviceIsSpecified(false),
      sample_rate_hz_(0),
      _recChannels(1),
      _playChannels(1),
      _initialized(false),
      _recording(false),
      _playing(false),
      _recIsInitialized(false),
      _playIsInitialized(false),
      _startRec(false),
      _startPlay(false),
      update_speaker_volume_at_startup_(false),
      quit_(false),
      _sndCardPlayDelay(0),
      _writeErrors(0),
      _deviceIndex(-1),
      _numPlayDevices(0),
      _numRecDevices(0),
      _playDeviceName(NULL),
      _recDeviceName(NULL),
      _playDisplayDeviceName(NULL),
      _recDisplayDeviceName(NULL),
      _playBuffer(NULL),
      _playbackBufferSize(0),
      _playbackBufferUnused(0),
      _tempBufferSpace(0),
      _recBuffer(NULL),
      _recordBufferSize(0),
      _recordBufferUsed(0),
      _tempSampleData(NULL),
      _tempSampleDataSize(0),
      _configuredLatencyPlay(0),
      _configuredLatencyRec(0),
      _paDeviceIndex(-1),
      _paStateChanged(false),
      _paMainloop(NULL),
      _paMainloopApi(NULL),
      _paContext(NULL),
      _recStream(NULL),
      _playStream(NULL),
      _recStreamFlags(0),
      _playStreamFlags(0) {
  RTC_LOG(LS_INFO) << __FUNCTION__ << " created";

  memset(_paServerVersion, 0, sizeof(_paServerVersion));
  memset(&_playBufferAttr, 0, sizeof(_playBufferAttr));
  memset(&_recBufferAttr, 0, sizeof(_recBufferAttr));
  memset(_oldKeyState, 0, sizeof(_oldKeyState));
}

AudioDeviceLinuxPulse::~AudioDeviceLinuxPulse() {
  RTC_LOG(LS_INFO) << __FUNCTION__ << " destroyed";
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();

  if (_recBuffer) {
    delete[] _recBuffer;
    _recBuffer = NULL;
  }
  if (_playBuffer) {
    delete[] _playBuffer;
    _playBuffer = NULL;
  }
  if (_playDeviceName) {
    delete[] _playDeviceName;
    _playDeviceName = NULL;
  }
  if (_recDeviceName) {
    delete[] _recDeviceName;
    _recDeviceName = NULL;
  }
}

void AudioDeviceLinuxPulse::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  RTC_DCHECK(thread_checker_.IsCurrent());

  _ptrAudioBuffer = audioBuffer;

  // Inform the AudioBuffer about default settings for this implementation.
  // Set all values to zero here since the actual settings will be done by
  // InitPlayout and InitRecording later.
  _ptrAudioBuffer->SetRecordingSampleRate(0);
  _ptrAudioBuffer->SetPlayoutSampleRate(0);
  _ptrAudioBuffer->SetRecordingChannels(0);
  _ptrAudioBuffer->SetPlayoutChannels(0);
}

// ----------------------------------------------------------------------------
//  ActiveAudioLayer
// ----------------------------------------------------------------------------

int32_t AudioDeviceLinuxPulse::ActiveAudioLayer(
    AudioDeviceModule::AudioLayer& audioLayer) const {
  audioLayer = AudioDeviceModule::kLinuxPulseAudio;
  return 0;
}

AudioDeviceGeneric::InitStatus AudioDeviceLinuxPulse::Init() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_initialized) {
    return InitStatus::OK;
  }

  // Initialize PulseAudio
  if (InitPulseAudio() < 0) {
    RTC_LOG(LS_ERROR) << "failed to initialize PulseAudio";
    if (TerminatePulseAudio() < 0) {
      RTC_LOG(LS_ERROR) << "failed to terminate PulseAudio";
    }
    return InitStatus::OTHER_ERROR;
  }

#if defined(WEBRTC_USE_X11)
  // Get X display handle for typing detection
  _XDisplay = XOpenDisplay(NULL);
  if (!_XDisplay) {
    RTC_LOG(LS_WARNING)
        << "failed to open X display, typing detection will not work";
  }
#endif

  // RECORDING
  _ptrThreadRec.reset(new rtc::PlatformThread(RecThreadFunc, this,
                                              "webrtc_audio_module_rec_thread",
                                              rtc::kRealtimePriority));

  _ptrThreadRec->Start();

  // PLAYOUT
  _ptrThreadPlay.reset(new rtc::PlatformThread(
      PlayThreadFunc, this, "webrtc_audio_module_play_thread",
      rtc::kRealtimePriority));
  _ptrThreadPlay->Start();

  _initialized = true;

  return InitStatus::OK;
}

int32_t AudioDeviceLinuxPulse::Terminate() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!_initialized) {
    return 0;
  }
  {
    MutexLock lock(&mutex_);
    quit_ = true;
  }
  _mixerManager.Close();

  // RECORDING
  if (_ptrThreadRec) {
    rtc::PlatformThread* tmpThread = _ptrThreadRec.release();

    _timeEventRec.Set();
    tmpThread->Stop();
    delete tmpThread;
  }

  // PLAYOUT
  if (_ptrThreadPlay) {
    rtc::PlatformThread* tmpThread = _ptrThreadPlay.release();

    _timeEventPlay.Set();
    tmpThread->Stop();
    delete tmpThread;
  }

  // Terminate PulseAudio
  if (TerminatePulseAudio() < 0) {
    RTC_LOG(LS_ERROR) << "failed to terminate PulseAudio";
    return -1;
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

bool AudioDeviceLinuxPulse::Initialized() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_initialized);
}

int32_t AudioDeviceLinuxPulse::InitSpeaker() {
  RTC_DCHECK(thread_checker_.IsCurrent());

  if (_playing) {
    return -1;
  }

  if (!_outputDeviceIsSpecified) {
    return -1;
  }

  // check if default device
  if (_outputDeviceIndex == 0) {
    uint16_t deviceIndex = 0;
    GetDefaultDeviceInfo(false, NULL, deviceIndex);
    _paDeviceIndex = deviceIndex;
  } else {
    // get the PA device index from
    // the callback
    _deviceIndex = _outputDeviceIndex;

    // get playout devices
    PlayoutDevices();
  }

  // the callback has now set the _paDeviceIndex to
  // the PulseAudio index of the device
  if (_mixerManager.OpenSpeaker(_paDeviceIndex) == -1) {
    return -1;
  }

  // clear _deviceIndex
  _deviceIndex = -1;
  _paDeviceIndex = -1;

  return 0;
}

int32_t AudioDeviceLinuxPulse::InitMicrophone() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_recording) {
    return -1;
  }

  if (!_inputDeviceIsSpecified) {
    return -1;
  }

  // Check if default device
  if (_inputDeviceIndex == 0) {
    uint16_t deviceIndex = 0;
    GetDefaultDeviceInfo(true, NULL, deviceIndex);
    _paDeviceIndex = deviceIndex;
  } else {
    // Get the PA device index from
    // the callback
    _deviceIndex = _inputDeviceIndex;

    // get recording devices
    RecordingDevices();
  }

  // The callback has now set the _paDeviceIndex to
  // the PulseAudio index of the device
  if (_mixerManager.OpenMicrophone(_paDeviceIndex) == -1) {
    return -1;
  }

  // Clear _deviceIndex
  _deviceIndex = -1;
  _paDeviceIndex = -1;

  return 0;
}

bool AudioDeviceLinuxPulse::SpeakerIsInitialized() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_mixerManager.SpeakerIsInitialized());
}

bool AudioDeviceLinuxPulse::MicrophoneIsInitialized() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_mixerManager.MicrophoneIsInitialized());
}

int32_t AudioDeviceLinuxPulse::SpeakerVolumeIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  bool wasInitialized = _mixerManager.SpeakerIsInitialized();

  // Make an attempt to open up the
  // output mixer corresponding to the currently selected output device.
  if (!wasInitialized && InitSpeaker() == -1) {
    // If we end up here it means that the selected speaker has no volume
    // control.
    available = false;
    return 0;
  }

  // Given that InitSpeaker was successful, we know volume control exists.
  available = true;

  // Close the initialized output mixer
  if (!wasInitialized) {
    _mixerManager.CloseSpeaker();
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::SetSpeakerVolume(uint32_t volume) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!_playing) {
    // Only update the volume if it's been set while we weren't playing.
    update_speaker_volume_at_startup_ = true;
  }
  return (_mixerManager.SetSpeakerVolume(volume));
}

int32_t AudioDeviceLinuxPulse::SpeakerVolume(uint32_t& volume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  uint32_t level(0);

  if (_mixerManager.SpeakerVolume(level) == -1) {
    return -1;
  }

  volume = level;

  return 0;
}

int32_t AudioDeviceLinuxPulse::MaxSpeakerVolume(uint32_t& maxVolume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  uint32_t maxVol(0);

  if (_mixerManager.MaxSpeakerVolume(maxVol) == -1) {
    return -1;
  }

  maxVolume = maxVol;

  return 0;
}

int32_t AudioDeviceLinuxPulse::MinSpeakerVolume(uint32_t& minVolume) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  uint32_t minVol(0);

  if (_mixerManager.MinSpeakerVolume(minVol) == -1) {
    return -1;
  }

  minVolume = minVol;

  return 0;
}

int32_t AudioDeviceLinuxPulse::SpeakerMuteIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
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

int32_t AudioDeviceLinuxPulse::SetSpeakerMute(bool enable) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_mixerManager.SetSpeakerMute(enable));
}

int32_t AudioDeviceLinuxPulse::SpeakerMute(bool& enabled) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  bool muted(0);
  if (_mixerManager.SpeakerMute(muted) == -1) {
    return -1;
  }

  enabled = muted;
  return 0;
}

int32_t AudioDeviceLinuxPulse::MicrophoneMuteIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  bool isAvailable(false);
  bool wasInitialized = _mixerManager.MicrophoneIsInitialized();

  // Make an attempt to open up the
  // input mixer corresponding to the currently selected input device.
  //
  if (!wasInitialized && InitMicrophone() == -1) {
    // If we end up here it means that the selected microphone has no
    // volume control, hence it is safe to state that there is no
    // boost control already at this stage.
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

int32_t AudioDeviceLinuxPulse::SetMicrophoneMute(bool enable) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_mixerManager.SetMicrophoneMute(enable));
}

int32_t AudioDeviceLinuxPulse::MicrophoneMute(bool& enabled) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  bool muted(0);
  if (_mixerManager.MicrophoneMute(muted) == -1) {
    return -1;
  }

  enabled = muted;
  return 0;
}

int32_t AudioDeviceLinuxPulse::StereoRecordingIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_recChannels == 2 && _recording) {
    available = true;
    return 0;
  }

  available = false;
  bool wasInitialized = _mixerManager.MicrophoneIsInitialized();
  int error = 0;

  if (!wasInitialized && InitMicrophone() == -1) {
    // Cannot open the specified device
    available = false;
    return 0;
  }

  // Check if the selected microphone can record stereo.
  bool isAvailable(false);
  error = _mixerManager.StereoRecordingIsAvailable(isAvailable);
  if (!error)
    available = isAvailable;

  // Close the initialized input mixer
  if (!wasInitialized) {
    _mixerManager.CloseMicrophone();
  }

  return error;
}

int32_t AudioDeviceLinuxPulse::SetStereoRecording(bool enable) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (enable)
    _recChannels = 2;
  else
    _recChannels = 1;

  return 0;
}

int32_t AudioDeviceLinuxPulse::StereoRecording(bool& enabled) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_recChannels == 2)
    enabled = true;
  else
    enabled = false;

  return 0;
}

int32_t AudioDeviceLinuxPulse::StereoPlayoutIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_playChannels == 2 && _playing) {
    available = true;
    return 0;
  }

  available = false;
  bool wasInitialized = _mixerManager.SpeakerIsInitialized();
  int error = 0;

  if (!wasInitialized && InitSpeaker() == -1) {
    // Cannot open the specified device.
    return -1;
  }

  // Check if the selected speaker can play stereo.
  bool isAvailable(false);
  error = _mixerManager.StereoPlayoutIsAvailable(isAvailable);
  if (!error)
    available = isAvailable;

  // Close the initialized input mixer
  if (!wasInitialized) {
    _mixerManager.CloseSpeaker();
  }

  return error;
}

int32_t AudioDeviceLinuxPulse::SetStereoPlayout(bool enable) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (enable)
    _playChannels = 2;
  else
    _playChannels = 1;

  return 0;
}

int32_t AudioDeviceLinuxPulse::StereoPlayout(bool& enabled) const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_playChannels == 2)
    enabled = true;
  else
    enabled = false;

  return 0;
}

int32_t AudioDeviceLinuxPulse::MicrophoneVolumeIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  bool wasInitialized = _mixerManager.MicrophoneIsInitialized();

  // Make an attempt to open up the
  // input mixer corresponding to the currently selected output device.
  if (!wasInitialized && InitMicrophone() == -1) {
    // If we end up here it means that the selected microphone has no
    // volume control.
    available = false;
    return 0;
  }

  // Given that InitMicrophone was successful, we know that a volume control
  // exists.
  available = true;

  // Close the initialized input mixer
  if (!wasInitialized) {
    _mixerManager.CloseMicrophone();
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::SetMicrophoneVolume(uint32_t volume) {
  return (_mixerManager.SetMicrophoneVolume(volume));
}

int32_t AudioDeviceLinuxPulse::MicrophoneVolume(uint32_t& volume) const {
  uint32_t level(0);

  if (_mixerManager.MicrophoneVolume(level) == -1) {
    RTC_LOG(LS_WARNING) << "failed to retrieve current microphone level";
    return -1;
  }

  volume = level;

  return 0;
}

int32_t AudioDeviceLinuxPulse::MaxMicrophoneVolume(uint32_t& maxVolume) const {
  uint32_t maxVol(0);

  if (_mixerManager.MaxMicrophoneVolume(maxVol) == -1) {
    return -1;
  }

  maxVolume = maxVol;

  return 0;
}

int32_t AudioDeviceLinuxPulse::MinMicrophoneVolume(uint32_t& minVolume) const {
  uint32_t minVol(0);

  if (_mixerManager.MinMicrophoneVolume(minVol) == -1) {
    return -1;
  }

  minVolume = minVol;

  return 0;
}

int16_t AudioDeviceLinuxPulse::PlayoutDevices() {
  PaLock();

  pa_operation* paOperation = NULL;
  _numPlayDevices = 1;  // init to 1 to account for "default"

  // get the whole list of devices and update _numPlayDevices
  paOperation =
      LATE(pa_context_get_sink_info_list)(_paContext, PaSinkInfoCallback, this);

  WaitForOperationCompletion(paOperation);

  PaUnLock();

  return _numPlayDevices;
}

int32_t AudioDeviceLinuxPulse::SetPlayoutDevice(uint16_t index) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_playIsInitialized) {
    return -1;
  }

  const uint16_t nDevices = PlayoutDevices();

  RTC_LOG(LS_VERBOSE) << "number of availiable output devices is " << nDevices;

  if (index > (nDevices - 1)) {
    RTC_LOG(LS_ERROR) << "device index is out of range [0," << (nDevices - 1)
                      << "]";
    return -1;
  }

  _outputDeviceIndex = index;
  _outputDeviceIsSpecified = true;

  return 0;
}

int32_t AudioDeviceLinuxPulse::SetPlayoutDevice(
    AudioDeviceModule::WindowsDeviceType /*device*/) {
  RTC_LOG(LS_ERROR) << "WindowsDeviceType not supported";
  return -1;
}

int32_t AudioDeviceLinuxPulse::PlayoutDeviceName(
    uint16_t index,
    char name[kAdmMaxDeviceNameSize],
    char guid[kAdmMaxGuidSize]) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  const uint16_t nDevices = PlayoutDevices();

  if ((index > (nDevices - 1)) || (name == NULL)) {
    return -1;
  }

  memset(name, 0, kAdmMaxDeviceNameSize);

  if (guid != NULL) {
    memset(guid, 0, kAdmMaxGuidSize);
  }

  // Check if default device
  if (index == 0) {
    uint16_t deviceIndex = 0;
    return GetDefaultDeviceInfo(false, name, deviceIndex);
  }

  // Tell the callback that we want
  // The name for this device
  _playDisplayDeviceName = name;
  _deviceIndex = index;

  // get playout devices
  PlayoutDevices();

  // clear device name and index
  _playDisplayDeviceName = NULL;
  _deviceIndex = -1;

  return 0;
}

int32_t AudioDeviceLinuxPulse::RecordingDeviceName(
    uint16_t index,
    char name[kAdmMaxDeviceNameSize],
    char guid[kAdmMaxGuidSize]) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  const uint16_t nDevices(RecordingDevices());

  if ((index > (nDevices - 1)) || (name == NULL)) {
    return -1;
  }

  memset(name, 0, kAdmMaxDeviceNameSize);

  if (guid != NULL) {
    memset(guid, 0, kAdmMaxGuidSize);
  }

  // Check if default device
  if (index == 0) {
    uint16_t deviceIndex = 0;
    return GetDefaultDeviceInfo(true, name, deviceIndex);
  }

  // Tell the callback that we want
  // the name for this device
  _recDisplayDeviceName = name;
  _deviceIndex = index;

  // Get recording devices
  RecordingDevices();

  // Clear device name and index
  _recDisplayDeviceName = NULL;
  _deviceIndex = -1;

  return 0;
}

int16_t AudioDeviceLinuxPulse::RecordingDevices() {
  PaLock();

  pa_operation* paOperation = NULL;
  _numRecDevices = 1;  // Init to 1 to account for "default"

  // Get the whole list of devices and update _numRecDevices
  paOperation = LATE(pa_context_get_source_info_list)(
      _paContext, PaSourceInfoCallback, this);

  WaitForOperationCompletion(paOperation);

  PaUnLock();

  return _numRecDevices;
}

int32_t AudioDeviceLinuxPulse::SetRecordingDevice(uint16_t index) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (_recIsInitialized) {
    return -1;
  }

  const uint16_t nDevices(RecordingDevices());

  RTC_LOG(LS_VERBOSE) << "number of availiable input devices is " << nDevices;

  if (index > (nDevices - 1)) {
    RTC_LOG(LS_ERROR) << "device index is out of range [0," << (nDevices - 1)
                      << "]";
    return -1;
  }

  _inputDeviceIndex = index;
  _inputDeviceIsSpecified = true;

  return 0;
}

int32_t AudioDeviceLinuxPulse::SetRecordingDevice(
    AudioDeviceModule::WindowsDeviceType /*device*/) {
  RTC_LOG(LS_ERROR) << "WindowsDeviceType not supported";
  return -1;
}

int32_t AudioDeviceLinuxPulse::PlayoutIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  available = false;

  // Try to initialize the playout side
  int32_t res = InitPlayout();

  // Cancel effect of initialization
  StopPlayout();

  if (res != -1) {
    available = true;
  }

  return res;
}

int32_t AudioDeviceLinuxPulse::RecordingIsAvailable(bool& available) {
  RTC_DCHECK(thread_checker_.IsCurrent());
  available = false;

  // Try to initialize the playout side
  int32_t res = InitRecording();

  // Cancel effect of initialization
  StopRecording();

  if (res != -1) {
    available = true;
  }

  return res;
}

int32_t AudioDeviceLinuxPulse::InitPlayout() {
  RTC_DCHECK(thread_checker_.IsCurrent());

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
  if (InitSpeaker() == -1) {
    RTC_LOG(LS_WARNING) << "InitSpeaker() failed";
  }

  // Set the play sample specification
  pa_sample_spec playSampleSpec;
  playSampleSpec.channels = _playChannels;
  playSampleSpec.format = PA_SAMPLE_S16LE;
  playSampleSpec.rate = sample_rate_hz_;

  // Create a new play stream
  {
    MutexLock lock(&mutex_);
    _playStream =
        LATE(pa_stream_new)(_paContext, "playStream", &playSampleSpec, NULL);
  }

  if (!_playStream) {
    RTC_LOG(LS_ERROR) << "failed to create play stream, err="
                      << LATE(pa_context_errno)(_paContext);
    return -1;
  }

  // Provide the playStream to the mixer
  _mixerManager.SetPlayStream(_playStream);

  if (_ptrAudioBuffer) {
    // Update audio buffer with the selected parameters
    _ptrAudioBuffer->SetPlayoutSampleRate(sample_rate_hz_);
    _ptrAudioBuffer->SetPlayoutChannels((uint8_t)_playChannels);
  }

  RTC_LOG(LS_VERBOSE) << "stream state "
                      << LATE(pa_stream_get_state)(_playStream);

  // Set stream flags
  _playStreamFlags = (pa_stream_flags_t)(PA_STREAM_AUTO_TIMING_UPDATE |
                                         PA_STREAM_INTERPOLATE_TIMING);

  if (_configuredLatencyPlay != WEBRTC_PA_NO_LATENCY_REQUIREMENTS) {
    // If configuring a specific latency then we want to specify
    // PA_STREAM_ADJUST_LATENCY to make the server adjust parameters
    // automatically to reach that target latency. However, that flag
    // doesn't exist in Ubuntu 8.04 and many people still use that,
    // so we have to check the protocol version of libpulse.
    if (LATE(pa_context_get_protocol_version)(_paContext) >=
        WEBRTC_PA_ADJUST_LATENCY_PROTOCOL_VERSION) {
      _playStreamFlags |= PA_STREAM_ADJUST_LATENCY;
    }

    const pa_sample_spec* spec = LATE(pa_stream_get_sample_spec)(_playStream);
    if (!spec) {
      RTC_LOG(LS_ERROR) << "pa_stream_get_sample_spec()";
      return -1;
    }

    size_t bytesPerSec = LATE(pa_bytes_per_second)(spec);
    uint32_t latency = bytesPerSec * WEBRTC_PA_PLAYBACK_LATENCY_MINIMUM_MSECS /
                       WEBRTC_PA_MSECS_PER_SEC;

    // Set the play buffer attributes
    _playBufferAttr.maxlength = latency;  // num bytes stored in the buffer
    _playBufferAttr.tlength = latency;    // target fill level of play buffer
    // minimum free num bytes before server request more data
    _playBufferAttr.minreq = latency / WEBRTC_PA_PLAYBACK_REQUEST_FACTOR;
    // prebuffer tlength before starting playout
    _playBufferAttr.prebuf = _playBufferAttr.tlength - _playBufferAttr.minreq;

    _configuredLatencyPlay = latency;
  }

  // num samples in bytes * num channels
  _playbackBufferSize = sample_rate_hz_ / 100 * 2 * _playChannels;
  _playbackBufferUnused = _playbackBufferSize;
  _playBuffer = new int8_t[_playbackBufferSize];

  // Enable underflow callback
  LATE(pa_stream_set_underflow_callback)
  (_playStream, PaStreamUnderflowCallback, this);

  // Set the state callback function for the stream
  LATE(pa_stream_set_state_callback)(_playStream, PaStreamStateCallback, this);

  // Mark playout side as initialized
  {
    MutexLock lock(&mutex_);
    _playIsInitialized = true;
    _sndCardPlayDelay = 0;
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::InitRecording() {
  RTC_DCHECK(thread_checker_.IsCurrent());

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
  if (InitMicrophone() == -1) {
    RTC_LOG(LS_WARNING) << "InitMicrophone() failed";
  }

  // Set the rec sample specification
  pa_sample_spec recSampleSpec;
  recSampleSpec.channels = _recChannels;
  recSampleSpec.format = PA_SAMPLE_S16LE;
  recSampleSpec.rate = sample_rate_hz_;

  // Create a new rec stream
  _recStream =
      LATE(pa_stream_new)(_paContext, "recStream", &recSampleSpec, NULL);
  if (!_recStream) {
    RTC_LOG(LS_ERROR) << "failed to create rec stream, err="
                      << LATE(pa_context_errno)(_paContext);
    return -1;
  }

  // Provide the recStream to the mixer
  _mixerManager.SetRecStream(_recStream);

  if (_ptrAudioBuffer) {
    // Update audio buffer with the selected parameters
    _ptrAudioBuffer->SetRecordingSampleRate(sample_rate_hz_);
    _ptrAudioBuffer->SetRecordingChannels((uint8_t)_recChannels);
  }

  if (_configuredLatencyRec != WEBRTC_PA_NO_LATENCY_REQUIREMENTS) {
    _recStreamFlags = (pa_stream_flags_t)(PA_STREAM_AUTO_TIMING_UPDATE |
                                          PA_STREAM_INTERPOLATE_TIMING);

    // If configuring a specific latency then we want to specify
    // PA_STREAM_ADJUST_LATENCY to make the server adjust parameters
    // automatically to reach that target latency. However, that flag
    // doesn't exist in Ubuntu 8.04 and many people still use that,
    //  so we have to check the protocol version of libpulse.
    if (LATE(pa_context_get_protocol_version)(_paContext) >=
        WEBRTC_PA_ADJUST_LATENCY_PROTOCOL_VERSION) {
      _recStreamFlags |= PA_STREAM_ADJUST_LATENCY;
    }

    const pa_sample_spec* spec = LATE(pa_stream_get_sample_spec)(_recStream);
    if (!spec) {
      RTC_LOG(LS_ERROR) << "pa_stream_get_sample_spec(rec)";
      return -1;
    }

    size_t bytesPerSec = LATE(pa_bytes_per_second)(spec);
    uint32_t latency = bytesPerSec * WEBRTC_PA_LOW_CAPTURE_LATENCY_MSECS /
                       WEBRTC_PA_MSECS_PER_SEC;

    // Set the rec buffer attributes
    // Note: fragsize specifies a maximum transfer size, not a minimum, so
    // it is not possible to force a high latency setting, only a low one.
    _recBufferAttr.fragsize = latency;  // size of fragment
    _recBufferAttr.maxlength =
        latency + bytesPerSec * WEBRTC_PA_CAPTURE_BUFFER_EXTRA_MSECS /
                      WEBRTC_PA_MSECS_PER_SEC;

    _configuredLatencyRec = latency;
  }

  _recordBufferSize = sample_rate_hz_ / 100 * 2 * _recChannels;
  _recordBufferUsed = 0;
  _recBuffer = new int8_t[_recordBufferSize];

  // Enable overflow callback
  LATE(pa_stream_set_overflow_callback)
  (_recStream, PaStreamOverflowCallback, this);

  // Set the state callback function for the stream
  LATE(pa_stream_set_state_callback)(_recStream, PaStreamStateCallback, this);

  // Mark recording side as initialized
  _recIsInitialized = true;

  return 0;
}

int32_t AudioDeviceLinuxPulse::StartRecording() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!_recIsInitialized) {
    return -1;
  }

  if (_recording) {
    return 0;
  }

  // Set state to ensure that the recording starts from the audio thread.
  _startRec = true;

  // The audio thread will signal when recording has started.
  _timeEventRec.Set();
  if (!_recStartEvent.Wait(10000)) {
    {
      MutexLock lock(&mutex_);
      _startRec = false;
    }
    StopRecording();
    RTC_LOG(LS_ERROR) << "failed to activate recording";
    return -1;
  }

  {
    MutexLock lock(&mutex_);
    if (_recording) {
      // The recording state is set by the audio thread after recording
      // has started.
    } else {
      RTC_LOG(LS_ERROR) << "failed to activate recording";
      return -1;
    }
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::StopRecording() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  MutexLock lock(&mutex_);

  if (!_recIsInitialized) {
    return 0;
  }

  if (_recStream == NULL) {
    return -1;
  }

  _recIsInitialized = false;
  _recording = false;

  RTC_LOG(LS_VERBOSE) << "stopping recording";

  // Stop Recording
  PaLock();

  DisableReadCallback();
  LATE(pa_stream_set_overflow_callback)(_recStream, NULL, NULL);

  // Unset this here so that we don't get a TERMINATED callback
  LATE(pa_stream_set_state_callback)(_recStream, NULL, NULL);

  if (LATE(pa_stream_get_state)(_recStream) != PA_STREAM_UNCONNECTED) {
    // Disconnect the stream
    if (LATE(pa_stream_disconnect)(_recStream) != PA_OK) {
      RTC_LOG(LS_ERROR) << "failed to disconnect rec stream, err="
                        << LATE(pa_context_errno)(_paContext);
      PaUnLock();
      return -1;
    }

    RTC_LOG(LS_VERBOSE) << "disconnected recording";
  }

  LATE(pa_stream_unref)(_recStream);
  _recStream = NULL;

  PaUnLock();

  // Provide the recStream to the mixer
  _mixerManager.SetRecStream(_recStream);

  if (_recBuffer) {
    delete[] _recBuffer;
    _recBuffer = NULL;
  }

  return 0;
}

bool AudioDeviceLinuxPulse::RecordingIsInitialized() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_recIsInitialized);
}

bool AudioDeviceLinuxPulse::Recording() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_recording);
}

bool AudioDeviceLinuxPulse::PlayoutIsInitialized() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_playIsInitialized);
}

int32_t AudioDeviceLinuxPulse::StartPlayout() {
  RTC_DCHECK(thread_checker_.IsCurrent());

  if (!_playIsInitialized) {
    return -1;
  }

  if (_playing) {
    return 0;
  }

  // Set state to ensure that playout starts from the audio thread.
  {
    MutexLock lock(&mutex_);
    _startPlay = true;
  }

  // Both |_startPlay| and |_playing| needs protction since they are also
  // accessed on the playout thread.

  // The audio thread will signal when playout has started.
  _timeEventPlay.Set();
  if (!_playStartEvent.Wait(10000)) {
    {
      MutexLock lock(&mutex_);
      _startPlay = false;
    }
    StopPlayout();
    RTC_LOG(LS_ERROR) << "failed to activate playout";
    return -1;
  }

  {
    MutexLock lock(&mutex_);
    if (_playing) {
      // The playing state is set by the audio thread after playout
      // has started.
    } else {
      RTC_LOG(LS_ERROR) << "failed to activate playing";
      return -1;
    }
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::StopPlayout() {
  RTC_DCHECK(thread_checker_.IsCurrent());
  MutexLock lock(&mutex_);

  if (!_playIsInitialized) {
    return 0;
  }

  if (_playStream == NULL) {
    return -1;
  }

  _playIsInitialized = false;
  _playing = false;
  _sndCardPlayDelay = 0;

  RTC_LOG(LS_VERBOSE) << "stopping playback";

  // Stop Playout
  PaLock();

  DisableWriteCallback();
  LATE(pa_stream_set_underflow_callback)(_playStream, NULL, NULL);

  // Unset this here so that we don't get a TERMINATED callback
  LATE(pa_stream_set_state_callback)(_playStream, NULL, NULL);

  if (LATE(pa_stream_get_state)(_playStream) != PA_STREAM_UNCONNECTED) {
    // Disconnect the stream
    if (LATE(pa_stream_disconnect)(_playStream) != PA_OK) {
      RTC_LOG(LS_ERROR) << "failed to disconnect play stream, err="
                        << LATE(pa_context_errno)(_paContext);
      PaUnLock();
      return -1;
    }

    RTC_LOG(LS_VERBOSE) << "disconnected playback";
  }

  LATE(pa_stream_unref)(_playStream);
  _playStream = NULL;

  PaUnLock();

  // Provide the playStream to the mixer
  _mixerManager.SetPlayStream(_playStream);

  if (_playBuffer) {
    delete[] _playBuffer;
    _playBuffer = NULL;
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::PlayoutDelay(uint16_t& delayMS) const {
  MutexLock lock(&mutex_);
  delayMS = (uint16_t)_sndCardPlayDelay;
  return 0;
}

bool AudioDeviceLinuxPulse::Playing() const {
  RTC_DCHECK(thread_checker_.IsCurrent());
  return (_playing);
}

// ============================================================================
//                                 Private Methods
// ============================================================================

void AudioDeviceLinuxPulse::PaContextStateCallback(pa_context* c, void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaContextStateCallbackHandler(c);
}

// ----------------------------------------------------------------------------
//  PaSinkInfoCallback
// ----------------------------------------------------------------------------

void AudioDeviceLinuxPulse::PaSinkInfoCallback(pa_context* /*c*/,
                                               const pa_sink_info* i,
                                               int eol,
                                               void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaSinkInfoCallbackHandler(i, eol);
}

void AudioDeviceLinuxPulse::PaSourceInfoCallback(pa_context* /*c*/,
                                                 const pa_source_info* i,
                                                 int eol,
                                                 void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaSourceInfoCallbackHandler(i,
                                                                          eol);
}

void AudioDeviceLinuxPulse::PaServerInfoCallback(pa_context* /*c*/,
                                                 const pa_server_info* i,
                                                 void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaServerInfoCallbackHandler(i);
}

void AudioDeviceLinuxPulse::PaStreamStateCallback(pa_stream* p, void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaStreamStateCallbackHandler(p);
}

void AudioDeviceLinuxPulse::PaContextStateCallbackHandler(pa_context* c) {
  RTC_LOG(LS_VERBOSE) << "context state cb";

  pa_context_state_t state = LATE(pa_context_get_state)(c);
  switch (state) {
    case PA_CONTEXT_UNCONNECTED:
      RTC_LOG(LS_VERBOSE) << "unconnected";
      break;
    case PA_CONTEXT_CONNECTING:
    case PA_CONTEXT_AUTHORIZING:
    case PA_CONTEXT_SETTING_NAME:
      RTC_LOG(LS_VERBOSE) << "no state";
      break;
    case PA_CONTEXT_FAILED:
    case PA_CONTEXT_TERMINATED:
      RTC_LOG(LS_VERBOSE) << "failed";
      _paStateChanged = true;
      LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
      break;
    case PA_CONTEXT_READY:
      RTC_LOG(LS_VERBOSE) << "ready";
      _paStateChanged = true;
      LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
      break;
  }
}

void AudioDeviceLinuxPulse::PaSinkInfoCallbackHandler(const pa_sink_info* i,
                                                      int eol) {
  if (eol) {
    // Signal that we are done
    LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
    return;
  }

  if (_numPlayDevices == _deviceIndex) {
    // Convert the device index to the one of the sink
    _paDeviceIndex = i->index;

    if (_playDeviceName) {
      // Copy the sink name
      strncpy(_playDeviceName, i->name, kAdmMaxDeviceNameSize);
      _playDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
    }
    if (_playDisplayDeviceName) {
      // Copy the sink display name
      strncpy(_playDisplayDeviceName, i->description, kAdmMaxDeviceNameSize);
      _playDisplayDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
    }
  }

  _numPlayDevices++;
}

void AudioDeviceLinuxPulse::PaSourceInfoCallbackHandler(const pa_source_info* i,
                                                        int eol) {
  if (eol) {
    // Signal that we are done
    LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
    return;
  }

  // We don't want to list output devices
  if (i->monitor_of_sink == PA_INVALID_INDEX) {
    if (_numRecDevices == _deviceIndex) {
      // Convert the device index to the one of the source
      _paDeviceIndex = i->index;

      if (_recDeviceName) {
        // copy the source name
        strncpy(_recDeviceName, i->name, kAdmMaxDeviceNameSize);
        _recDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
      }
      if (_recDisplayDeviceName) {
        // Copy the source display name
        strncpy(_recDisplayDeviceName, i->description, kAdmMaxDeviceNameSize);
        _recDisplayDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
      }
    }

    _numRecDevices++;
  }
}

void AudioDeviceLinuxPulse::PaServerInfoCallbackHandler(
    const pa_server_info* i) {
  // Use PA native sampling rate
  sample_rate_hz_ = i->sample_spec.rate;

  // Copy the PA server version
  strncpy(_paServerVersion, i->server_version, 31);
  _paServerVersion[31] = '\0';

  if (_recDisplayDeviceName) {
    // Copy the source name
    strncpy(_recDisplayDeviceName, i->default_source_name,
            kAdmMaxDeviceNameSize);
    _recDisplayDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
  }

  if (_playDisplayDeviceName) {
    // Copy the sink name
    strncpy(_playDisplayDeviceName, i->default_sink_name,
            kAdmMaxDeviceNameSize);
    _playDisplayDeviceName[kAdmMaxDeviceNameSize - 1] = '\0';
  }

  LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
}

void AudioDeviceLinuxPulse::PaStreamStateCallbackHandler(pa_stream* p) {
  RTC_LOG(LS_VERBOSE) << "stream state cb";

  pa_stream_state_t state = LATE(pa_stream_get_state)(p);
  switch (state) {
    case PA_STREAM_UNCONNECTED:
      RTC_LOG(LS_VERBOSE) << "unconnected";
      break;
    case PA_STREAM_CREATING:
      RTC_LOG(LS_VERBOSE) << "creating";
      break;
    case PA_STREAM_FAILED:
    case PA_STREAM_TERMINATED:
      RTC_LOG(LS_VERBOSE) << "failed";
      break;
    case PA_STREAM_READY:
      RTC_LOG(LS_VERBOSE) << "ready";
      break;
  }

  LATE(pa_threaded_mainloop_signal)(_paMainloop, 0);
}

int32_t AudioDeviceLinuxPulse::CheckPulseAudioVersion() {
  PaLock();

  pa_operation* paOperation = NULL;

  // get the server info and update deviceName
  paOperation =
      LATE(pa_context_get_server_info)(_paContext, PaServerInfoCallback, this);

  WaitForOperationCompletion(paOperation);

  PaUnLock();

  RTC_LOG(LS_VERBOSE) << "checking PulseAudio version: " << _paServerVersion;

  return 0;
}

int32_t AudioDeviceLinuxPulse::InitSamplingFrequency() {
  PaLock();

  pa_operation* paOperation = NULL;

  // Get the server info and update sample_rate_hz_
  paOperation =
      LATE(pa_context_get_server_info)(_paContext, PaServerInfoCallback, this);

  WaitForOperationCompletion(paOperation);

  PaUnLock();

  return 0;
}

int32_t AudioDeviceLinuxPulse::GetDefaultDeviceInfo(bool recDevice,
                                                    char* name,
                                                    uint16_t& index) {
  char tmpName[kAdmMaxDeviceNameSize] = {0};
  // subtract length of "default: "
  uint16_t nameLen = kAdmMaxDeviceNameSize - 9;
  char* pName = NULL;

  if (name) {
    // Add "default: "
    strcpy(name, "default: ");
    pName = &name[9];
  }

  // Tell the callback that we want
  // the name for this device
  if (recDevice) {
    _recDisplayDeviceName = tmpName;
  } else {
    _playDisplayDeviceName = tmpName;
  }

  // Set members
  _paDeviceIndex = -1;
  _deviceIndex = 0;
  _numPlayDevices = 0;
  _numRecDevices = 0;

  PaLock();

  pa_operation* paOperation = NULL;

  // Get the server info and update deviceName
  paOperation =
      LATE(pa_context_get_server_info)(_paContext, PaServerInfoCallback, this);

  WaitForOperationCompletion(paOperation);

  // Get the device index
  if (recDevice) {
    paOperation = LATE(pa_context_get_source_info_by_name)(
        _paContext, (char*)tmpName, PaSourceInfoCallback, this);
  } else {
    paOperation = LATE(pa_context_get_sink_info_by_name)(
        _paContext, (char*)tmpName, PaSinkInfoCallback, this);
  }

  WaitForOperationCompletion(paOperation);

  PaUnLock();

  // Set the index
  index = _paDeviceIndex;

  if (name) {
    // Copy to name string
    strncpy(pName, tmpName, nameLen);
  }

  // Clear members
  _playDisplayDeviceName = NULL;
  _recDisplayDeviceName = NULL;
  _paDeviceIndex = -1;
  _deviceIndex = -1;
  _numPlayDevices = 0;
  _numRecDevices = 0;

  return 0;
}

int32_t AudioDeviceLinuxPulse::InitPulseAudio() {
  int retVal = 0;

  // Load libpulse
  if (!GetPulseSymbolTable()->Load()) {
    // Most likely the Pulse library and sound server are not installed on
    // this system
    RTC_LOG(LS_ERROR) << "failed to load symbol table";
    return -1;
  }

  // Create a mainloop API and connection to the default server
  // the mainloop is the internal asynchronous API event loop
  if (_paMainloop) {
    RTC_LOG(LS_ERROR) << "PA mainloop has already existed";
    return -1;
  }
  _paMainloop = LATE(pa_threaded_mainloop_new)();
  if (!_paMainloop) {
    RTC_LOG(LS_ERROR) << "could not create mainloop";
    return -1;
  }

  // Start the threaded main loop
  retVal = LATE(pa_threaded_mainloop_start)(_paMainloop);
  if (retVal != PA_OK) {
    RTC_LOG(LS_ERROR) << "failed to start main loop, error=" << retVal;
    return -1;
  }

  RTC_LOG(LS_VERBOSE) << "mainloop running!";

  PaLock();

  _paMainloopApi = LATE(pa_threaded_mainloop_get_api)(_paMainloop);
  if (!_paMainloopApi) {
    RTC_LOG(LS_ERROR) << "could not create mainloop API";
    PaUnLock();
    return -1;
  }

  // Create a new PulseAudio context
  if (_paContext) {
    RTC_LOG(LS_ERROR) << "PA context has already existed";
    PaUnLock();
    return -1;
  }
  _paContext = LATE(pa_context_new)(_paMainloopApi, "WEBRTC VoiceEngine");

  if (!_paContext) {
    RTC_LOG(LS_ERROR) << "could not create context";
    PaUnLock();
    return -1;
  }

  // Set state callback function
  LATE(pa_context_set_state_callback)(_paContext, PaContextStateCallback, this);

  // Connect the context to a server (default)
  _paStateChanged = false;
  retVal =
      LATE(pa_context_connect)(_paContext, NULL, PA_CONTEXT_NOAUTOSPAWN, NULL);

  if (retVal != PA_OK) {
    RTC_LOG(LS_ERROR) << "failed to connect context, error=" << retVal;
    PaUnLock();
    return -1;
  }

  // Wait for state change
  while (!_paStateChanged) {
    LATE(pa_threaded_mainloop_wait)(_paMainloop);
  }

  // Now check to see what final state we reached.
  pa_context_state_t state = LATE(pa_context_get_state)(_paContext);

  if (state != PA_CONTEXT_READY) {
    if (state == PA_CONTEXT_FAILED) {
      RTC_LOG(LS_ERROR) << "failed to connect to PulseAudio sound server";
    } else if (state == PA_CONTEXT_TERMINATED) {
      RTC_LOG(LS_ERROR) << "PulseAudio connection terminated early";
    } else {
      // Shouldn't happen, because we only signal on one of those three
      // states
      RTC_LOG(LS_ERROR) << "unknown problem connecting to PulseAudio";
    }
    PaUnLock();
    return -1;
  }

  PaUnLock();

  // Give the objects to the mixer manager
  _mixerManager.SetPulseAudioObjects(_paMainloop, _paContext);

  // Check the version
  if (CheckPulseAudioVersion() < 0) {
    RTC_LOG(LS_ERROR) << "PulseAudio version " << _paServerVersion
                      << " not supported";
    return -1;
  }

  // Initialize sampling frequency
  if (InitSamplingFrequency() < 0 || sample_rate_hz_ == 0) {
    RTC_LOG(LS_ERROR) << "failed to initialize sampling frequency, set to "
                      << sample_rate_hz_ << " Hz";
    return -1;
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::TerminatePulseAudio() {
  // Do nothing if the instance doesn't exist
  // likely GetPulseSymbolTable.Load() fails
  if (!_paMainloop) {
    return 0;
  }

  PaLock();

  // Disconnect the context
  if (_paContext) {
    LATE(pa_context_disconnect)(_paContext);
  }

  // Unreference the context
  if (_paContext) {
    LATE(pa_context_unref)(_paContext);
  }

  PaUnLock();
  _paContext = NULL;

  // Stop the threaded main loop
  if (_paMainloop) {
    LATE(pa_threaded_mainloop_stop)(_paMainloop);
  }

  // Free the mainloop
  if (_paMainloop) {
    LATE(pa_threaded_mainloop_free)(_paMainloop);
  }

  _paMainloop = NULL;

  RTC_LOG(LS_VERBOSE) << "PulseAudio terminated";

  return 0;
}

void AudioDeviceLinuxPulse::PaLock() {
  LATE(pa_threaded_mainloop_lock)(_paMainloop);
}

void AudioDeviceLinuxPulse::PaUnLock() {
  LATE(pa_threaded_mainloop_unlock)(_paMainloop);
}

void AudioDeviceLinuxPulse::WaitForOperationCompletion(
    pa_operation* paOperation) const {
  if (!paOperation) {
    RTC_LOG(LS_ERROR) << "paOperation NULL in WaitForOperationCompletion";
    return;
  }

  while (LATE(pa_operation_get_state)(paOperation) == PA_OPERATION_RUNNING) {
    LATE(pa_threaded_mainloop_wait)(_paMainloop);
  }

  LATE(pa_operation_unref)(paOperation);
}

// ============================================================================
//                                  Thread Methods
// ============================================================================

void AudioDeviceLinuxPulse::EnableWriteCallback() {
  if (LATE(pa_stream_get_state)(_playStream) == PA_STREAM_READY) {
    // May already have available space. Must check.
    _tempBufferSpace = LATE(pa_stream_writable_size)(_playStream);
    if (_tempBufferSpace > 0) {
      // Yup, there is already space available, so if we register a
      // write callback then it will not receive any event. So dispatch
      // one ourself instead.
      _timeEventPlay.Set();
      return;
    }
  }

  LATE(pa_stream_set_write_callback)(_playStream, &PaStreamWriteCallback, this);
}

void AudioDeviceLinuxPulse::DisableWriteCallback() {
  LATE(pa_stream_set_write_callback)(_playStream, NULL, NULL);
}

void AudioDeviceLinuxPulse::PaStreamWriteCallback(pa_stream* /*unused*/,
                                                  size_t buffer_space,
                                                  void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaStreamWriteCallbackHandler(
      buffer_space);
}

void AudioDeviceLinuxPulse::PaStreamWriteCallbackHandler(size_t bufferSpace) {
  _tempBufferSpace = bufferSpace;

  // Since we write the data asynchronously on a different thread, we have
  // to temporarily disable the write callback or else Pulse will call it
  // continuously until we write the data. We re-enable it below.
  DisableWriteCallback();
  _timeEventPlay.Set();
}

void AudioDeviceLinuxPulse::PaStreamUnderflowCallback(pa_stream* /*unused*/,
                                                      void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)
      ->PaStreamUnderflowCallbackHandler();
}

void AudioDeviceLinuxPulse::PaStreamUnderflowCallbackHandler() {
  RTC_LOG(LS_WARNING) << "Playout underflow";

  if (_configuredLatencyPlay == WEBRTC_PA_NO_LATENCY_REQUIREMENTS) {
    // We didn't configure a pa_buffer_attr before, so switching to
    // one now would be questionable.
    return;
  }

  // Otherwise reconfigure the stream with a higher target latency.

  const pa_sample_spec* spec = LATE(pa_stream_get_sample_spec)(_playStream);
  if (!spec) {
    RTC_LOG(LS_ERROR) << "pa_stream_get_sample_spec()";
    return;
  }

  size_t bytesPerSec = LATE(pa_bytes_per_second)(spec);
  uint32_t newLatency =
      _configuredLatencyPlay + bytesPerSec *
                                   WEBRTC_PA_PLAYBACK_LATENCY_INCREMENT_MSECS /
                                   WEBRTC_PA_MSECS_PER_SEC;

  // Set the play buffer attributes
  _playBufferAttr.maxlength = newLatency;
  _playBufferAttr.tlength = newLatency;
  _playBufferAttr.minreq = newLatency / WEBRTC_PA_PLAYBACK_REQUEST_FACTOR;
  _playBufferAttr.prebuf = _playBufferAttr.tlength - _playBufferAttr.minreq;

  pa_operation* op = LATE(pa_stream_set_buffer_attr)(
      _playStream, &_playBufferAttr, NULL, NULL);
  if (!op) {
    RTC_LOG(LS_ERROR) << "pa_stream_set_buffer_attr()";
    return;
  }

  // Don't need to wait for this to complete.
  LATE(pa_operation_unref)(op);

  // Save the new latency in case we underflow again.
  _configuredLatencyPlay = newLatency;
}

void AudioDeviceLinuxPulse::EnableReadCallback() {
  LATE(pa_stream_set_read_callback)(_recStream, &PaStreamReadCallback, this);
}

void AudioDeviceLinuxPulse::DisableReadCallback() {
  LATE(pa_stream_set_read_callback)(_recStream, NULL, NULL);
}

void AudioDeviceLinuxPulse::PaStreamReadCallback(pa_stream* /*unused1*/,
                                                 size_t /*unused2*/,
                                                 void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaStreamReadCallbackHandler();
}

void AudioDeviceLinuxPulse::PaStreamReadCallbackHandler() {
  // We get the data pointer and size now in order to save one Lock/Unlock
  // in the worker thread.
  if (LATE(pa_stream_peek)(_recStream, &_tempSampleData,
                           &_tempSampleDataSize) != 0) {
    RTC_LOG(LS_ERROR) << "Can't read data!";
    return;
  }

  // Since we consume the data asynchronously on a different thread, we have
  // to temporarily disable the read callback or else Pulse will call it
  // continuously until we consume the data. We re-enable it below.
  DisableReadCallback();
  _timeEventRec.Set();
}

void AudioDeviceLinuxPulse::PaStreamOverflowCallback(pa_stream* /*unused*/,
                                                     void* pThis) {
  static_cast<AudioDeviceLinuxPulse*>(pThis)->PaStreamOverflowCallbackHandler();
}

void AudioDeviceLinuxPulse::PaStreamOverflowCallbackHandler() {
  RTC_LOG(LS_WARNING) << "Recording overflow";
}

int32_t AudioDeviceLinuxPulse::LatencyUsecs(pa_stream* stream) {
  if (!WEBRTC_PA_REPORT_LATENCY) {
    return 0;
  }

  if (!stream) {
    return 0;
  }

  pa_usec_t latency;
  int negative;
  if (LATE(pa_stream_get_latency)(stream, &latency, &negative) != 0) {
    RTC_LOG(LS_ERROR) << "Can't query latency";
    // We'd rather continue playout/capture with an incorrect delay than
    // stop it altogether, so return a valid value.
    return 0;
  }

  if (negative) {
    RTC_LOG(LS_VERBOSE)
        << "warning: pa_stream_get_latency reported negative delay";

    // The delay can be negative for monitoring streams if the captured
    // samples haven't been played yet. In such a case, "latency"
    // contains the magnitude, so we must negate it to get the real value.
    int32_t tmpLatency = (int32_t)-latency;
    if (tmpLatency < 0) {
      // Make sure that we don't use a negative delay.
      tmpLatency = 0;
    }

    return tmpLatency;
  } else {
    return (int32_t)latency;
  }
}

int32_t AudioDeviceLinuxPulse::ReadRecordedData(const void* bufferData,
                                                size_t bufferSize)
    RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_) {
  size_t size = bufferSize;
  uint32_t numRecSamples = _recordBufferSize / (2 * _recChannels);

  // Account for the peeked data and the used data.
  uint32_t recDelay =
      (uint32_t)((LatencyUsecs(_recStream) / 1000) +
                 10 * ((size + _recordBufferUsed) / _recordBufferSize));

  if (_playStream) {
    // Get the playout delay.
    _sndCardPlayDelay = (uint32_t)(LatencyUsecs(_playStream) / 1000);
  }

  if (_recordBufferUsed > 0) {
    // Have to copy to the buffer until it is full.
    size_t copy = _recordBufferSize - _recordBufferUsed;
    if (size < copy) {
      copy = size;
    }

    memcpy(&_recBuffer[_recordBufferUsed], bufferData, copy);
    _recordBufferUsed += copy;
    bufferData = static_cast<const char*>(bufferData) + copy;
    size -= copy;

    if (_recordBufferUsed != _recordBufferSize) {
      // Not enough data yet to pass to VoE.
      return 0;
    }

    // Provide data to VoiceEngine.
    if (ProcessRecordedData(_recBuffer, numRecSamples, recDelay) == -1) {
      // We have stopped recording.
      return -1;
    }

    _recordBufferUsed = 0;
  }

  // Now process full 10ms sample sets directly from the input.
  while (size >= _recordBufferSize) {
    // Provide data to VoiceEngine.
    if (ProcessRecordedData(static_cast<int8_t*>(const_cast<void*>(bufferData)),
                            numRecSamples, recDelay) == -1) {
      // We have stopped recording.
      return -1;
    }

    bufferData = static_cast<const char*>(bufferData) + _recordBufferSize;
    size -= _recordBufferSize;

    // We have consumed 10ms of data.
    recDelay -= 10;
  }

  // Now save any leftovers for later.
  if (size > 0) {
    memcpy(_recBuffer, bufferData, size);
    _recordBufferUsed = size;
  }

  return 0;
}

int32_t AudioDeviceLinuxPulse::ProcessRecordedData(int8_t* bufferData,
                                                   uint32_t bufferSizeInSamples,
                                                   uint32_t recDelay)
    RTC_EXCLUSIVE_LOCKS_REQUIRED(mutex_) {
  _ptrAudioBuffer->SetRecordedBuffer(bufferData, bufferSizeInSamples);

  // TODO(andrew): this is a temporary hack, to avoid non-causal far- and
  // near-end signals at the AEC for PulseAudio. I think the system delay is
  // being correctly calculated here, but for legacy reasons we add +10 ms
  // to the value in the AEC. The real fix will be part of a larger
  // investigation into managing system delay in the AEC.
  if (recDelay > 10)
    recDelay -= 10;
  else
    recDelay = 0;
  _ptrAudioBuffer->SetVQEData(_sndCardPlayDelay, recDelay);
  _ptrAudioBuffer->SetTypingStatus(KeyPressed());
  // Deliver recorded samples at specified sample rate,
  // mic level etc. to the observer using callback.
  UnLock();
  _ptrAudioBuffer->DeliverRecordedData();
  Lock();

  // We have been unlocked - check the flag again.
  if (!_recording) {
    return -1;
  }

  return 0;
}

void AudioDeviceLinuxPulse::PlayThreadFunc(void* pThis) {
  AudioDeviceLinuxPulse* device = static_cast<AudioDeviceLinuxPulse*>(pThis);
  while (device->PlayThreadProcess()) {
  }
}

void AudioDeviceLinuxPulse::RecThreadFunc(void* pThis) {
  AudioDeviceLinuxPulse* device = static_cast<AudioDeviceLinuxPulse*>(pThis);
  while (device->RecThreadProcess()) {
  }
}

bool AudioDeviceLinuxPulse::PlayThreadProcess() {
  if (!_timeEventPlay.Wait(1000)) {
    return true;
  }

  MutexLock lock(&mutex_);

  if (quit_) {
    return false;
  }

  if (_startPlay) {
    RTC_LOG(LS_VERBOSE) << "_startPlay true, performing initial actions";

    _startPlay = false;
    _playDeviceName = NULL;

    // Set if not default device
    if (_outputDeviceIndex > 0) {
      // Get the playout device name
      _playDeviceName = new char[kAdmMaxDeviceNameSize];
      _deviceIndex = _outputDeviceIndex;
      PlayoutDevices();
    }

    // Start muted only supported on 0.9.11 and up
    if (LATE(pa_context_get_protocol_version)(_paContext) >=
        WEBRTC_PA_ADJUST_LATENCY_PROTOCOL_VERSION) {
      // Get the currently saved speaker mute status
      // and set the initial mute status accordingly
      bool enabled(false);
      _mixerManager.SpeakerMute(enabled);
      if (enabled) {
        _playStreamFlags |= PA_STREAM_START_MUTED;
      }
    }

    // Get the currently saved speaker volume
    uint32_t volume = 0;
    if (update_speaker_volume_at_startup_)
      _mixerManager.SpeakerVolume(volume);

    PaLock();

    // NULL gives PA the choice of startup volume.
    pa_cvolume* ptr_cvolume = NULL;
    if (update_speaker_volume_at_startup_) {
      pa_cvolume cVolumes;
      ptr_cvolume = &cVolumes;

      // Set the same volume for all channels
      const pa_sample_spec* spec = LATE(pa_stream_get_sample_spec)(_playStream);
      LATE(pa_cvolume_set)(&cVolumes, spec->channels, volume);
      update_speaker_volume_at_startup_ = false;
    }

    // Connect the stream to a sink
    if (LATE(pa_stream_connect_playback)(
            _playStream, _playDeviceName, &_playBufferAttr,
            (pa_stream_flags_t)_playStreamFlags, ptr_cvolume, NULL) != PA_OK) {
      RTC_LOG(LS_ERROR) << "failed to connect play stream, err="
                        << LATE(pa_context_errno)(_paContext);
    }

    RTC_LOG(LS_VERBOSE) << "play stream connected";

    // Wait for state change
    while (LATE(pa_stream_get_state)(_playStream) != PA_STREAM_READY) {
      LATE(pa_threaded_mainloop_wait)(_paMainloop);
    }

    RTC_LOG(LS_VERBOSE) << "play stream ready";

    // We can now handle write callbacks
    EnableWriteCallback();

    PaUnLock();

    // Clear device name
    if (_playDeviceName) {
      delete[] _playDeviceName;
      _playDeviceName = NULL;
    }

    _playing = true;
    _playStartEvent.Set();

    return true;
  }

  if (_playing) {
    if (!_recording) {
      // Update the playout delay
      _sndCardPlayDelay = (uint32_t)(LatencyUsecs(_playStream) / 1000);
    }

    if (_playbackBufferUnused < _playbackBufferSize) {
      size_t write = _playbackBufferSize - _playbackBufferUnused;
      if (_tempBufferSpace < write) {
        write = _tempBufferSpace;
      }

      PaLock();
      if (LATE(pa_stream_write)(
              _playStream, (void*)&_playBuffer[_playbackBufferUnused], write,
              NULL, (int64_t)0, PA_SEEK_RELATIVE) != PA_OK) {
        _writeErrors++;
        if (_writeErrors > 10) {
          RTC_LOG(LS_ERROR) << "Playout error: _writeErrors=" << _writeErrors
                            << ", error=" << LATE(pa_context_errno)(_paContext);
          _writeErrors = 0;
        }
      }
      PaUnLock();

      _playbackBufferUnused += write;
      _tempBufferSpace -= write;
    }

    uint32_t numPlaySamples = _playbackBufferSize / (2 * _playChannels);
    // Might have been reduced to zero by the above.
    if (_tempBufferSpace > 0) {
      // Ask for new PCM data to be played out using the
      // AudioDeviceBuffer ensure that this callback is executed
      // without taking the audio-thread lock.
      UnLock();
      RTC_LOG(LS_VERBOSE) << "requesting data";
      uint32_t nSamples = _ptrAudioBuffer->RequestPlayoutData(numPlaySamples);
      Lock();

      // We have been unlocked - check the flag again.
      if (!_playing) {
        return true;
      }

      nSamples = _ptrAudioBuffer->GetPlayoutData(_playBuffer);
      if (nSamples != numPlaySamples) {
        RTC_LOG(LS_ERROR) << "invalid number of output samples(" << nSamples
                          << ")";
      }

      size_t write = _playbackBufferSize;
      if (_tempBufferSpace < write) {
        write = _tempBufferSpace;
      }

      RTC_LOG(LS_VERBOSE) << "will write";
      PaLock();
      if (LATE(pa_stream_write)(_playStream, (void*)&_playBuffer[0], write,
                                NULL, (int64_t)0, PA_SEEK_RELATIVE) != PA_OK) {
        _writeErrors++;
        if (_writeErrors > 10) {
          RTC_LOG(LS_ERROR) << "Playout error: _writeErrors=" << _writeErrors
                            << ", error=" << LATE(pa_context_errno)(_paContext);
          _writeErrors = 0;
        }
      }
      PaUnLock();

      _playbackBufferUnused = write;
    }

    _tempBufferSpace = 0;
    PaLock();
    EnableWriteCallback();
    PaUnLock();

  }  // _playing

  return true;
}

bool AudioDeviceLinuxPulse::RecThreadProcess() {
  if (!_timeEventRec.Wait(1000)) {
    return true;
  }

  MutexLock lock(&mutex_);
  if (quit_) {
    return false;
  }
  if (_startRec) {
    RTC_LOG(LS_VERBOSE) << "_startRec true, performing initial actions";

    _recDeviceName = NULL;

    // Set if not default device
    if (_inputDeviceIndex > 0) {
      // Get the recording device name
      _recDeviceName = new char[kAdmMaxDeviceNameSize];
      _deviceIndex = _inputDeviceIndex;
      RecordingDevices();
    }

    PaLock();

    RTC_LOG(LS_VERBOSE) << "connecting stream";

    // Connect the stream to a source
    if (LATE(pa_stream_connect_record)(
            _recStream, _recDeviceName, &_recBufferAttr,
            (pa_stream_flags_t)_recStreamFlags) != PA_OK) {
      RTC_LOG(LS_ERROR) << "failed to connect rec stream, err="
                        << LATE(pa_context_errno)(_paContext);
    }

    RTC_LOG(LS_VERBOSE) << "connected";

    // Wait for state change
    while (LATE(pa_stream_get_state)(_recStream) != PA_STREAM_READY) {
      LATE(pa_threaded_mainloop_wait)(_paMainloop);
    }

    RTC_LOG(LS_VERBOSE) << "done";

    // We can now handle read callbacks
    EnableReadCallback();

    PaUnLock();

    // Clear device name
    if (_recDeviceName) {
      delete[] _recDeviceName;
      _recDeviceName = NULL;
    }

    _startRec = false;
    _recording = true;
    _recStartEvent.Set();

    return true;
  }

  if (_recording) {
    // Read data and provide it to VoiceEngine
    if (ReadRecordedData(_tempSampleData, _tempSampleDataSize) == -1) {
      return true;
    }

    _tempSampleData = NULL;
    _tempSampleDataSize = 0;

    PaLock();
    while (true) {
      // Ack the last thing we read
      if (LATE(pa_stream_drop)(_recStream) != 0) {
        RTC_LOG(LS_WARNING)
            << "failed to drop, err=" << LATE(pa_context_errno)(_paContext);
      }

      if (LATE(pa_stream_readable_size)(_recStream) <= 0) {
        // Then that was all the data
        break;
      }

      // Else more data.
      const void* sampleData;
      size_t sampleDataSize;

      if (LATE(pa_stream_peek)(_recStream, &sampleData, &sampleDataSize) != 0) {
        RTC_LOG(LS_ERROR) << "RECORD_ERROR, error = "
                          << LATE(pa_context_errno)(_paContext);
        break;
      }

      // Drop lock for sigslot dispatch, which could take a while.
      PaUnLock();
      // Read data and provide it to VoiceEngine
      if (ReadRecordedData(sampleData, sampleDataSize) == -1) {
        return true;
      }
      PaLock();

      // Return to top of loop for the ack and the check for more data.
    }

    EnableReadCallback();
    PaUnLock();

  }  // _recording

  return true;
}

bool AudioDeviceLinuxPulse::KeyPressed() const {
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
