/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_ANDROID_AUDIO_TRACK_JNI_H_
#define MODULES_AUDIO_DEVICE_ANDROID_AUDIO_TRACK_JNI_H_

#include <jni.h>

#include <memory>

#include "api/sequence_checker.h"
#include "modules/audio_device/android/audio_common.h"
#include "modules/audio_device/android/audio_manager.h"
#include "modules/audio_device/audio_device_generic.h"
#include "modules/audio_device/include/audio_device_defines.h"
#include "modules/utility/include/helpers_android.h"
#include "modules/utility/include/jvm_android.h"

namespace webrtc {

// Implements 16-bit mono PCM audio output support for Android using the Java
// AudioTrack interface. Most of the work is done by its Java counterpart in
// WebRtcAudioTrack.java. This class is created and lives on a thread in
// C++-land, but decoded audio buffers are requested on a high-priority
// thread managed by the Java class.
//
// An instance must be created and destroyed on one and the same thread.
// All public methods must also be called on the same thread. A thread checker
// will RTC_DCHECK if any method is called on an invalid thread.
//
// This class uses JvmThreadConnector to attach to a Java VM if needed
// and detach when the object goes out of scope. Additional thread checking
// guarantees that no other (possibly non attached) thread is used.
class AudioTrackJni {
 public:
  // Wraps the Java specific parts of the AudioTrackJni into one helper class.
  class JavaAudioTrack {
   public:
    JavaAudioTrack(NativeRegistration* native_registration,
                   std::unique_ptr<GlobalRef> audio_track);
    ~JavaAudioTrack();

    bool InitPlayout(int sample_rate, int channels);
    bool StartPlayout();
    bool StopPlayout();
    bool SetStreamVolume(int volume);
    int GetStreamMaxVolume();
    int GetStreamVolume();

   private:
    std::unique_ptr<GlobalRef> audio_track_;
    jmethodID init_playout_;
    jmethodID start_playout_;
    jmethodID stop_playout_;
    jmethodID set_stream_volume_;
    jmethodID get_stream_max_volume_;
    jmethodID get_stream_volume_;
    jmethodID get_buffer_size_in_frames_;
  };

  explicit AudioTrackJni(AudioManager* audio_manager);
  ~AudioTrackJni();

  int32_t Init();
  int32_t Terminate();

  int32_t InitPlayout();
  bool PlayoutIsInitialized() const { return initialized_; }

  int32_t StartPlayout();
  int32_t StopPlayout();
  bool Playing() const { return playing_; }

  int SpeakerVolumeIsAvailable(bool& available);
  int SetSpeakerVolume(uint32_t volume);
  int SpeakerVolume(uint32_t& volume) const;
  int MaxSpeakerVolume(uint32_t& max_volume) const;
  int MinSpeakerVolume(uint32_t& min_volume) const;

  void AttachAudioBuffer(AudioDeviceBuffer* audioBuffer);

 private:
  // Called from Java side so we can cache the address of the Java-manged
  // |byte_buffer| in |direct_buffer_address_|. The size of the buffer
  // is also stored in |direct_buffer_capacity_in_bytes_|.
  // Called on the same thread as the creating thread.
  static void JNICALL CacheDirectBufferAddress(JNIEnv* env,
                                               jobject obj,
                                               jobject byte_buffer,
                                               jlong nativeAudioTrack);
  void OnCacheDirectBufferAddress(JNIEnv* env, jobject byte_buffer);

  // Called periodically by the Java based WebRtcAudioTrack object when
  // playout has started. Each call indicates that |length| new bytes should
  // be written to the memory area |direct_buffer_address_| for playout.
  // This method is called on a high-priority thread from Java. The name of
  // the thread is 'AudioTrackThread'.
  static void JNICALL GetPlayoutData(JNIEnv* env,
                                     jobject obj,
                                     jint length,
                                     jlong nativeAudioTrack);
  void OnGetPlayoutData(size_t length);

  // Stores thread ID in constructor.
  SequenceChecker thread_checker_;

  // Stores thread ID in first call to OnGetPlayoutData() from high-priority
  // thread in Java. Detached during construction of this object.
  SequenceChecker thread_checker_java_;

  // Calls JavaVM::AttachCurrentThread() if this thread is not attached at
  // construction.
  // Also ensures that DetachCurrentThread() is called at destruction.
  JvmThreadConnector attach_thread_if_needed_;

  // Wraps the JNI interface pointer and methods associated with it.
  std::unique_ptr<JNIEnvironment> j_environment_;

  // Contains factory method for creating the Java object.
  std::unique_ptr<NativeRegistration> j_native_registration_;

  // Wraps the Java specific parts of the AudioTrackJni class.
  std::unique_ptr<AudioTrackJni::JavaAudioTrack> j_audio_track_;

  // Contains audio parameters provided to this class at construction by the
  // AudioManager.
  const AudioParameters audio_parameters_;

  // Cached copy of address to direct audio buffer owned by |j_audio_track_|.
  void* direct_buffer_address_;

  // Number of bytes in the direct audio buffer owned by |j_audio_track_|.
  size_t direct_buffer_capacity_in_bytes_;

  // Number of audio frames per audio buffer. Each audio frame corresponds to
  // one sample of PCM mono data at 16 bits per sample. Hence, each audio
  // frame contains 2 bytes (given that the Java layer only supports mono).
  // Example: 480 for 48000 Hz or 441 for 44100 Hz.
  size_t frames_per_buffer_;

  bool initialized_;

  bool playing_;

  // Raw pointer handle provided to us in AttachAudioBuffer(). Owned by the
  // AudioDeviceModuleImpl class and called by AudioDeviceModule::Create().
  // The AudioDeviceBuffer is a member of the AudioDeviceModuleImpl instance
  // and therefore outlives this object.
  AudioDeviceBuffer* audio_device_buffer_;
};

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_ANDROID_AUDIO_TRACK_JNI_H_
