/*
 *  Copyright (c) 2015 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/opensles_player.h"

#include <android/log.h>

#include <memory>
#include "api/array_view.h"
#include "modules/audio_device/fine_audio_buffer.h"
#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/format_macros.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/time_utils.h"
#include "sdk/android/src/jni/audio_device/audio_common.h"

#define TAG "OpenSLESPlayer"
#define ALOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define ALOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define ALOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

#define RETURN_ON_ERROR(op, ...)                          \
  do {                                                    \
    SLresult err = (op);                                  \
    if (err != SL_RESULT_SUCCESS) {                       \
      ALOGE("%s failed: %s", #op, GetSLErrorString(err)); \
      return __VA_ARGS__;                                 \
    }                                                     \
  } while (0)

namespace webrtc {

namespace jni {

OpenSLESPlayer::OpenSLESPlayer(
    const AudioParameters& audio_parameters,
    rtc::scoped_refptr<OpenSLEngineManager> engine_manager)
    : audio_parameters_(audio_parameters),
      audio_device_buffer_(nullptr),
      initialized_(false),
      playing_(false),
      buffer_index_(0),
      engine_manager_(std::move(engine_manager)),
      engine_(nullptr),
      player_(nullptr),
      simple_buffer_queue_(nullptr),
      volume_(nullptr),
      last_play_time_(0) {
  ALOGD("ctor[tid=%d]", rtc::CurrentThreadId());
  // Use native audio output parameters provided by the audio manager and
  // define the PCM format structure.
  pcm_format_ = CreatePCMConfiguration(audio_parameters_.channels(),
                                       audio_parameters_.sample_rate(),
                                       audio_parameters_.bits_per_sample());
  // Detach from this thread since we want to use the checker to verify calls
  // from the internal  audio thread.
  thread_checker_opensles_.Detach();
}

OpenSLESPlayer::~OpenSLESPlayer() {
  ALOGD("dtor[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  Terminate();
  DestroyAudioPlayer();
  DestroyMix();
  engine_ = nullptr;
  RTC_DCHECK(!engine_);
  RTC_DCHECK(!output_mix_.Get());
  RTC_DCHECK(!player_);
  RTC_DCHECK(!simple_buffer_queue_);
  RTC_DCHECK(!volume_);
}

int OpenSLESPlayer::Init() {
  ALOGD("Init[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (audio_parameters_.channels() == 2) {
    ALOGW("Stereo mode is enabled");
  }
  return 0;
}

int OpenSLESPlayer::Terminate() {
  ALOGD("Terminate[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  StopPlayout();
  return 0;
}

int OpenSLESPlayer::InitPlayout() {
  ALOGD("InitPlayout[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!initialized_);
  RTC_DCHECK(!playing_);
  if (!ObtainEngineInterface()) {
    ALOGE("Failed to obtain SL Engine interface");
    return -1;
  }
  CreateMix();
  initialized_ = true;
  buffer_index_ = 0;
  return 0;
}

bool OpenSLESPlayer::PlayoutIsInitialized() const {
  return initialized_;
}

int OpenSLESPlayer::StartPlayout() {
  ALOGD("StartPlayout[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(initialized_);
  RTC_DCHECK(!playing_);
  if (fine_audio_buffer_) {
    fine_audio_buffer_->ResetPlayout();
  }
  // The number of lower latency audio players is limited, hence we create the
  // audio player in Start() and destroy it in Stop().
  CreateAudioPlayer();
  // Fill up audio buffers to avoid initial glitch and to ensure that playback
  // starts when mode is later changed to SL_PLAYSTATE_PLAYING.
  // TODO(henrika): we can save some delay by only making one call to
  // EnqueuePlayoutData. Most likely not worth the risk of adding a glitch.
  last_play_time_ = rtc::Time();
  for (int i = 0; i < kNumOfOpenSLESBuffers; ++i) {
    EnqueuePlayoutData(true);
  }
  // Start streaming data by setting the play state to SL_PLAYSTATE_PLAYING.
  // For a player object, when the object is in the SL_PLAYSTATE_PLAYING
  // state, adding buffers will implicitly start playback.
  RETURN_ON_ERROR((*player_)->SetPlayState(player_, SL_PLAYSTATE_PLAYING), -1);
  playing_ = (GetPlayState() == SL_PLAYSTATE_PLAYING);
  RTC_DCHECK(playing_);
  return 0;
}

int OpenSLESPlayer::StopPlayout() {
  ALOGD("StopPlayout[tid=%d]", rtc::CurrentThreadId());
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!initialized_ || !playing_) {
    return 0;
  }
  // Stop playing by setting the play state to SL_PLAYSTATE_STOPPED.
  RETURN_ON_ERROR((*player_)->SetPlayState(player_, SL_PLAYSTATE_STOPPED), -1);
  // Clear the buffer queue to flush out any remaining data.
  RETURN_ON_ERROR((*simple_buffer_queue_)->Clear(simple_buffer_queue_), -1);
#if RTC_DCHECK_IS_ON
  // Verify that the buffer queue is in fact cleared as it should.
  SLAndroidSimpleBufferQueueState buffer_queue_state;
  (*simple_buffer_queue_)->GetState(simple_buffer_queue_, &buffer_queue_state);
  RTC_DCHECK_EQ(0, buffer_queue_state.count);
  RTC_DCHECK_EQ(0, buffer_queue_state.index);
#endif
  // The number of lower latency audio players is limited, hence we create the
  // audio player in Start() and destroy it in Stop().
  DestroyAudioPlayer();
  thread_checker_opensles_.Detach();
  initialized_ = false;
  playing_ = false;
  return 0;
}

bool OpenSLESPlayer::Playing() const {
  return playing_;
}

bool OpenSLESPlayer::SpeakerVolumeIsAvailable() {
  return false;
}

int OpenSLESPlayer::SetSpeakerVolume(uint32_t volume) {
  return -1;
}

absl::optional<uint32_t> OpenSLESPlayer::SpeakerVolume() const {
  return absl::nullopt;
}

absl::optional<uint32_t> OpenSLESPlayer::MaxSpeakerVolume() const {
  return absl::nullopt;
}

absl::optional<uint32_t> OpenSLESPlayer::MinSpeakerVolume() const {
  return absl::nullopt;
}

void OpenSLESPlayer::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {
  ALOGD("AttachAudioBuffer");
  RTC_DCHECK(thread_checker_.IsCurrent());
  audio_device_buffer_ = audioBuffer;
  const int sample_rate_hz = audio_parameters_.sample_rate();
  ALOGD("SetPlayoutSampleRate(%d)", sample_rate_hz);
  audio_device_buffer_->SetPlayoutSampleRate(sample_rate_hz);
  const size_t channels = audio_parameters_.channels();
  ALOGD("SetPlayoutChannels(%" RTC_PRIuS ")", channels);
  audio_device_buffer_->SetPlayoutChannels(channels);
  RTC_CHECK(audio_device_buffer_);
  AllocateDataBuffers();
}

void OpenSLESPlayer::AllocateDataBuffers() {
  ALOGD("AllocateDataBuffers");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!simple_buffer_queue_);
  RTC_CHECK(audio_device_buffer_);
  // Create a modified audio buffer class which allows us to ask for any number
  // of samples (and not only multiple of 10ms) to match the native OpenSL ES
  // buffer size. The native buffer size corresponds to the
  // PROPERTY_OUTPUT_FRAMES_PER_BUFFER property which is the number of audio
  // frames that the HAL (Hardware Abstraction Layer) buffer can hold. It is
  // recommended to construct audio buffers so that they contain an exact
  // multiple of this number. If so, callbacks will occur at regular intervals,
  // which reduces jitter.
  const size_t buffer_size_in_samples =
      audio_parameters_.frames_per_buffer() * audio_parameters_.channels();
  ALOGD("native buffer size: %" RTC_PRIuS, buffer_size_in_samples);
  ALOGD("native buffer size in ms: %.2f",
        audio_parameters_.GetBufferSizeInMilliseconds());
  fine_audio_buffer_ = std::make_unique<FineAudioBuffer>(audio_device_buffer_);
  // Allocated memory for audio buffers.
  for (int i = 0; i < kNumOfOpenSLESBuffers; ++i) {
    audio_buffers_[i].reset(new SLint16[buffer_size_in_samples]);
  }
}

bool OpenSLESPlayer::ObtainEngineInterface() {
  ALOGD("ObtainEngineInterface");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (engine_)
    return true;
  // Get access to (or create if not already existing) the global OpenSL Engine
  // object.
  SLObjectItf engine_object = engine_manager_->GetOpenSLEngine();
  if (engine_object == nullptr) {
    ALOGE("Failed to access the global OpenSL engine");
    return false;
  }
  // Get the SL Engine Interface which is implicit.
  RETURN_ON_ERROR(
      (*engine_object)->GetInterface(engine_object, SL_IID_ENGINE, &engine_),
      false);
  return true;
}

bool OpenSLESPlayer::CreateMix() {
  ALOGD("CreateMix");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(engine_);
  if (output_mix_.Get())
    return true;

  // Create the ouput mix on the engine object. No interfaces will be used.
  RETURN_ON_ERROR((*engine_)->CreateOutputMix(engine_, output_mix_.Receive(), 0,
                                              nullptr, nullptr),
                  false);
  RETURN_ON_ERROR(output_mix_->Realize(output_mix_.Get(), SL_BOOLEAN_FALSE),
                  false);
  return true;
}

void OpenSLESPlayer::DestroyMix() {
  ALOGD("DestroyMix");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!output_mix_.Get())
    return;
  output_mix_.Reset();
}

bool OpenSLESPlayer::CreateAudioPlayer() {
  ALOGD("CreateAudioPlayer");
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(output_mix_.Get());
  if (player_object_.Get())
    return true;
  RTC_DCHECK(!player_);
  RTC_DCHECK(!simple_buffer_queue_);
  RTC_DCHECK(!volume_);

  // source: Android Simple Buffer Queue Data Locator is source.
  SLDataLocator_AndroidSimpleBufferQueue simple_buffer_queue = {
      SL_DATALOCATOR_ANDROIDSIMPLEBUFFERQUEUE,
      static_cast<SLuint32>(kNumOfOpenSLESBuffers)};
  SLDataSource audio_source = {&simple_buffer_queue, &pcm_format_};

  // sink: OutputMix-based data is sink.
  SLDataLocator_OutputMix locator_output_mix = {SL_DATALOCATOR_OUTPUTMIX,
                                                output_mix_.Get()};
  SLDataSink audio_sink = {&locator_output_mix, nullptr};

  // Define interfaces that we indend to use and realize.
  const SLInterfaceID interface_ids[] = {SL_IID_ANDROIDCONFIGURATION,
                                         SL_IID_BUFFERQUEUE, SL_IID_VOLUME};
  const SLboolean interface_required[] = {SL_BOOLEAN_TRUE, SL_BOOLEAN_TRUE,
                                          SL_BOOLEAN_TRUE};

  // Create the audio player on the engine interface.
  RETURN_ON_ERROR(
      (*engine_)->CreateAudioPlayer(
          engine_, player_object_.Receive(), &audio_source, &audio_sink,
          arraysize(interface_ids), interface_ids, interface_required),
      false);

  // Use the Android configuration interface to set platform-specific
  // parameters. Should be done before player is realized.
  SLAndroidConfigurationItf player_config;
  RETURN_ON_ERROR(
      player_object_->GetInterface(player_object_.Get(),
                                   SL_IID_ANDROIDCONFIGURATION, &player_config),
      false);
  // Set audio player configuration to SL_ANDROID_STREAM_VOICE which
  // corresponds to android.media.AudioManager.STREAM_VOICE_CALL.
  SLint32 stream_type = SL_ANDROID_STREAM_VOICE;
  RETURN_ON_ERROR(
      (*player_config)
          ->SetConfiguration(player_config, SL_ANDROID_KEY_STREAM_TYPE,
                             &stream_type, sizeof(SLint32)),
      false);

  // Realize the audio player object after configuration has been set.
  RETURN_ON_ERROR(
      player_object_->Realize(player_object_.Get(), SL_BOOLEAN_FALSE), false);

  // Get the SLPlayItf interface on the audio player.
  RETURN_ON_ERROR(
      player_object_->GetInterface(player_object_.Get(), SL_IID_PLAY, &player_),
      false);

  // Get the SLAndroidSimpleBufferQueueItf interface on the audio player.
  RETURN_ON_ERROR(
      player_object_->GetInterface(player_object_.Get(), SL_IID_BUFFERQUEUE,
                                   &simple_buffer_queue_),
      false);

  // Register callback method for the Android Simple Buffer Queue interface.
  // This method will be called when the native audio layer needs audio data.
  RETURN_ON_ERROR((*simple_buffer_queue_)
                      ->RegisterCallback(simple_buffer_queue_,
                                         SimpleBufferQueueCallback, this),
                  false);

  // Get the SLVolumeItf interface on the audio player.
  RETURN_ON_ERROR(player_object_->GetInterface(player_object_.Get(),
                                               SL_IID_VOLUME, &volume_),
                  false);

  // TODO(henrika): might not be required to set volume to max here since it
  // seems to be default on most devices. Might be required for unit tests.
  // RETURN_ON_ERROR((*volume_)->SetVolumeLevel(volume_, 0), false);

  return true;
}

void OpenSLESPlayer::DestroyAudioPlayer() {
  ALOGD("DestroyAudioPlayer");
  RTC_DCHECK(thread_checker_.IsCurrent());
  if (!player_object_.Get())
    return;
  (*simple_buffer_queue_)
      ->RegisterCallback(simple_buffer_queue_, nullptr, nullptr);
  player_object_.Reset();
  player_ = nullptr;
  simple_buffer_queue_ = nullptr;
  volume_ = nullptr;
}

// static
void OpenSLESPlayer::SimpleBufferQueueCallback(
    SLAndroidSimpleBufferQueueItf caller,
    void* context) {
  OpenSLESPlayer* stream = reinterpret_cast<OpenSLESPlayer*>(context);
  stream->FillBufferQueue();
}

void OpenSLESPlayer::FillBufferQueue() {
  RTC_DCHECK(thread_checker_opensles_.IsCurrent());
  SLuint32 state = GetPlayState();
  if (state != SL_PLAYSTATE_PLAYING) {
    ALOGW("Buffer callback in non-playing state!");
    return;
  }
  EnqueuePlayoutData(false);
}

void OpenSLESPlayer::EnqueuePlayoutData(bool silence) {
  // Check delta time between two successive callbacks and provide a warning
  // if it becomes very large.
  // TODO(henrika): using 150ms as upper limit but this value is rather random.
  const uint32_t current_time = rtc::Time();
  const uint32_t diff = current_time - last_play_time_;
  if (diff > 150) {
    ALOGW("Bad OpenSL ES playout timing, dT=%u [ms]", diff);
  }
  last_play_time_ = current_time;
  SLint8* audio_ptr8 =
      reinterpret_cast<SLint8*>(audio_buffers_[buffer_index_].get());
  if (silence) {
    RTC_DCHECK(thread_checker_.IsCurrent());
    // Avoid acquiring real audio data from WebRTC and fill the buffer with
    // zeros instead. Used to prime the buffer with silence and to avoid asking
    // for audio data from two different threads.
    memset(audio_ptr8, 0, audio_parameters_.GetBytesPerBuffer());
  } else {
    RTC_DCHECK(thread_checker_opensles_.IsCurrent());
    // Read audio data from the WebRTC source using the FineAudioBuffer object
    // to adjust for differences in buffer size between WebRTC (10ms) and native
    // OpenSL ES. Use hardcoded delay estimate since OpenSL ES does not support
    // delay estimation.
    fine_audio_buffer_->GetPlayoutData(
        rtc::ArrayView<int16_t>(audio_buffers_[buffer_index_].get(),
                                audio_parameters_.frames_per_buffer() *
                                    audio_parameters_.channels()),
        25);
  }
  // Enqueue the decoded audio buffer for playback.
  SLresult err = (*simple_buffer_queue_)
                     ->Enqueue(simple_buffer_queue_, audio_ptr8,
                               audio_parameters_.GetBytesPerBuffer());
  if (SL_RESULT_SUCCESS != err) {
    ALOGE("Enqueue failed: %d", err);
  }
  buffer_index_ = (buffer_index_ + 1) % kNumOfOpenSLESBuffers;
}

SLuint32 OpenSLESPlayer::GetPlayState() const {
  RTC_DCHECK(player_);
  SLuint32 state;
  SLresult err = (*player_)->GetPlayState(player_, &state);
  if (SL_RESULT_SUCCESS != err) {
    ALOGE("GetPlayState failed: %d", err);
  }
  return state;
}

}  // namespace jni

}  // namespace webrtc
