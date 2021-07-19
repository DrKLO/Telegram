/*
 *  Copyright (c) 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_device/dummy/audio_device_dummy.h"

namespace webrtc {

int32_t AudioDeviceDummy::ActiveAudioLayer(
    AudioDeviceModule::AudioLayer& audioLayer) const {
  return -1;
}

AudioDeviceGeneric::InitStatus AudioDeviceDummy::Init() {
  return InitStatus::OK;
}

int32_t AudioDeviceDummy::Terminate() {
  return 0;
}

bool AudioDeviceDummy::Initialized() const {
  return true;
}

int16_t AudioDeviceDummy::PlayoutDevices() {
  return -1;
}

int16_t AudioDeviceDummy::RecordingDevices() {
  return -1;
}

int32_t AudioDeviceDummy::PlayoutDeviceName(uint16_t index,
                                            char name[kAdmMaxDeviceNameSize],
                                            char guid[kAdmMaxGuidSize]) {
  return -1;
}

int32_t AudioDeviceDummy::RecordingDeviceName(uint16_t index,
                                              char name[kAdmMaxDeviceNameSize],
                                              char guid[kAdmMaxGuidSize]) {
  return -1;
}

int32_t AudioDeviceDummy::SetPlayoutDevice(uint16_t index) {
  return -1;
}

int32_t AudioDeviceDummy::SetPlayoutDevice(
    AudioDeviceModule::WindowsDeviceType device) {
  return -1;
}

int32_t AudioDeviceDummy::SetRecordingDevice(uint16_t index) {
  return -1;
}

int32_t AudioDeviceDummy::SetRecordingDevice(
    AudioDeviceModule::WindowsDeviceType device) {
  return -1;
}

int32_t AudioDeviceDummy::PlayoutIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::InitPlayout() {
  return -1;
}

bool AudioDeviceDummy::PlayoutIsInitialized() const {
  return false;
}

int32_t AudioDeviceDummy::RecordingIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::InitRecording() {
  return -1;
}

bool AudioDeviceDummy::RecordingIsInitialized() const {
  return false;
}

int32_t AudioDeviceDummy::StartPlayout() {
  return -1;
}

int32_t AudioDeviceDummy::StopPlayout() {
  return 0;
}

bool AudioDeviceDummy::Playing() const {
  return false;
}

int32_t AudioDeviceDummy::StartRecording() {
  return -1;
}

int32_t AudioDeviceDummy::StopRecording() {
  return 0;
}

bool AudioDeviceDummy::Recording() const {
  return false;
}

int32_t AudioDeviceDummy::InitSpeaker() {
  return -1;
}

bool AudioDeviceDummy::SpeakerIsInitialized() const {
  return false;
}

int32_t AudioDeviceDummy::InitMicrophone() {
  return -1;
}

bool AudioDeviceDummy::MicrophoneIsInitialized() const {
  return false;
}

int32_t AudioDeviceDummy::SpeakerVolumeIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::SetSpeakerVolume(uint32_t volume) {
  return -1;
}

int32_t AudioDeviceDummy::SpeakerVolume(uint32_t& volume) const {
  return -1;
}

int32_t AudioDeviceDummy::MaxSpeakerVolume(uint32_t& maxVolume) const {
  return -1;
}

int32_t AudioDeviceDummy::MinSpeakerVolume(uint32_t& minVolume) const {
  return -1;
}

int32_t AudioDeviceDummy::MicrophoneVolumeIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::SetMicrophoneVolume(uint32_t volume) {
  return -1;
}

int32_t AudioDeviceDummy::MicrophoneVolume(uint32_t& volume) const {
  return -1;
}

int32_t AudioDeviceDummy::MaxMicrophoneVolume(uint32_t& maxVolume) const {
  return -1;
}

int32_t AudioDeviceDummy::MinMicrophoneVolume(uint32_t& minVolume) const {
  return -1;
}

int32_t AudioDeviceDummy::SpeakerMuteIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::SetSpeakerMute(bool enable) {
  return -1;
}

int32_t AudioDeviceDummy::SpeakerMute(bool& enabled) const {
  return -1;
}

int32_t AudioDeviceDummy::MicrophoneMuteIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::SetMicrophoneMute(bool enable) {
  return -1;
}

int32_t AudioDeviceDummy::MicrophoneMute(bool& enabled) const {
  return -1;
}

int32_t AudioDeviceDummy::StereoPlayoutIsAvailable(bool& available) {
  return -1;
}
int32_t AudioDeviceDummy::SetStereoPlayout(bool enable) {
  return -1;
}

int32_t AudioDeviceDummy::StereoPlayout(bool& enabled) const {
  return -1;
}

int32_t AudioDeviceDummy::StereoRecordingIsAvailable(bool& available) {
  return -1;
}

int32_t AudioDeviceDummy::SetStereoRecording(bool enable) {
  return -1;
}

int32_t AudioDeviceDummy::StereoRecording(bool& enabled) const {
  return -1;
}

int32_t AudioDeviceDummy::PlayoutDelay(uint16_t& delayMS) const {
  return -1;
}

void AudioDeviceDummy::AttachAudioBuffer(AudioDeviceBuffer* audioBuffer) {}
}  // namespace webrtc
