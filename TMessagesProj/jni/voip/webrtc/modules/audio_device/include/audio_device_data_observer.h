/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_DATA_OBSERVER_H_
#define MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_DATA_OBSERVER_H_

#include <stddef.h>
#include <stdint.h>

#include "absl/base/attributes.h"
#include "api/scoped_refptr.h"
#include "api/task_queue/task_queue_factory.h"
#include "modules/audio_device/include/audio_device.h"

namespace webrtc {

// This interface will capture the raw PCM data of both the local captured as
// well as the mixed/rendered remote audio.
class AudioDeviceDataObserver {
 public:
  virtual void OnCaptureData(const void* audio_samples,
                             size_t num_samples,
                             size_t bytes_per_sample,
                             size_t num_channels,
                             uint32_t samples_per_sec) = 0;

  virtual void OnRenderData(const void* audio_samples,
                            size_t num_samples,
                            size_t bytes_per_sample,
                            size_t num_channels,
                            uint32_t samples_per_sec) = 0;

  AudioDeviceDataObserver() = default;
  virtual ~AudioDeviceDataObserver() = default;
};

// Creates an ADMWrapper around an ADM instance that registers
// the provided AudioDeviceDataObserver.
rtc::scoped_refptr<AudioDeviceModule> CreateAudioDeviceWithDataObserver(
    rtc::scoped_refptr<AudioDeviceModule> impl,
    std::unique_ptr<AudioDeviceDataObserver> observer);

// Creates an ADMWrapper around an ADM instance that registers
// the provided AudioDeviceDataObserver.
ABSL_DEPRECATED("")
rtc::scoped_refptr<AudioDeviceModule> CreateAudioDeviceWithDataObserver(
    rtc::scoped_refptr<AudioDeviceModule> impl,
    AudioDeviceDataObserver* observer);

// Creates an ADM instance with AudioDeviceDataObserver registered.
rtc::scoped_refptr<AudioDeviceModule> CreateAudioDeviceWithDataObserver(
    AudioDeviceModule::AudioLayer audio_layer,
    TaskQueueFactory* task_queue_factory,
    std::unique_ptr<AudioDeviceDataObserver> observer);

// Creates an ADM instance with AudioDeviceDataObserver registered.
ABSL_DEPRECATED("")
rtc::scoped_refptr<AudioDeviceModule> CreateAudioDeviceWithDataObserver(
    AudioDeviceModule::AudioLayer audio_layer,
    TaskQueueFactory* task_queue_factory,
    AudioDeviceDataObserver* observer);

}  // namespace webrtc

#endif  // MODULES_AUDIO_DEVICE_INCLUDE_AUDIO_DEVICE_DATA_OBSERVER_H_
