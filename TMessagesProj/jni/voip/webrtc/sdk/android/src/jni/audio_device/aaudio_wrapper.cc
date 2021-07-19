/*
 *  Copyright (c) 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/audio_device/aaudio_wrapper.h"

#include "rtc_base/logging.h"
#include "rtc_base/strings/string_builder.h"
#include "rtc_base/time_utils.h"

#define LOG_ON_ERROR(op)                                                      \
  do {                                                                        \
    aaudio_result_t result = (op);                                            \
    if (result != AAUDIO_OK) {                                                \
      RTC_LOG(LS_ERROR) << #op << ": " << AAudio_convertResultToText(result); \
    }                                                                         \
  } while (0)

#define RETURN_ON_ERROR(op, ...)                                              \
  do {                                                                        \
    aaudio_result_t result = (op);                                            \
    if (result != AAUDIO_OK) {                                                \
      RTC_LOG(LS_ERROR) << #op << ": " << AAudio_convertResultToText(result); \
      return __VA_ARGS__;                                                     \
    }                                                                         \
  } while (0)

namespace webrtc {

namespace jni {

namespace {

const char* DirectionToString(aaudio_direction_t direction) {
  switch (direction) {
    case AAUDIO_DIRECTION_OUTPUT:
      return "OUTPUT";
    case AAUDIO_DIRECTION_INPUT:
      return "INPUT";
    default:
      return "UNKNOWN";
  }
}

const char* SharingModeToString(aaudio_sharing_mode_t mode) {
  switch (mode) {
    case AAUDIO_SHARING_MODE_EXCLUSIVE:
      return "EXCLUSIVE";
    case AAUDIO_SHARING_MODE_SHARED:
      return "SHARED";
    default:
      return "UNKNOWN";
  }
}

const char* PerformanceModeToString(aaudio_performance_mode_t mode) {
  switch (mode) {
    case AAUDIO_PERFORMANCE_MODE_NONE:
      return "NONE";
    case AAUDIO_PERFORMANCE_MODE_POWER_SAVING:
      return "POWER_SAVING";
    case AAUDIO_PERFORMANCE_MODE_LOW_LATENCY:
      return "LOW_LATENCY";
    default:
      return "UNKNOWN";
  }
}

const char* FormatToString(int32_t id) {
  switch (id) {
    case AAUDIO_FORMAT_INVALID:
      return "INVALID";
    case AAUDIO_FORMAT_UNSPECIFIED:
      return "UNSPECIFIED";
    case AAUDIO_FORMAT_PCM_I16:
      return "PCM_I16";
    case AAUDIO_FORMAT_PCM_FLOAT:
      return "FLOAT";
    default:
      return "UNKNOWN";
  }
}

void ErrorCallback(AAudioStream* stream,
                   void* user_data,
                   aaudio_result_t error) {
  RTC_DCHECK(user_data);
  AAudioWrapper* aaudio_wrapper = reinterpret_cast<AAudioWrapper*>(user_data);
  RTC_LOG(WARNING) << "ErrorCallback: "
                   << DirectionToString(aaudio_wrapper->direction());
  RTC_DCHECK(aaudio_wrapper->observer());
  aaudio_wrapper->observer()->OnErrorCallback(error);
}

aaudio_data_callback_result_t DataCallback(AAudioStream* stream,
                                           void* user_data,
                                           void* audio_data,
                                           int32_t num_frames) {
  RTC_DCHECK(user_data);
  RTC_DCHECK(audio_data);
  AAudioWrapper* aaudio_wrapper = reinterpret_cast<AAudioWrapper*>(user_data);
  RTC_DCHECK(aaudio_wrapper->observer());
  return aaudio_wrapper->observer()->OnDataCallback(audio_data, num_frames);
}

// Wraps the stream builder object to ensure that it is released properly when
// the stream builder goes out of scope.
class ScopedStreamBuilder {
 public:
  ScopedStreamBuilder() {
    LOG_ON_ERROR(AAudio_createStreamBuilder(&builder_));
    RTC_DCHECK(builder_);
  }
  ~ScopedStreamBuilder() {
    if (builder_) {
      LOG_ON_ERROR(AAudioStreamBuilder_delete(builder_));
    }
  }

  AAudioStreamBuilder* get() const { return builder_; }

 private:
  AAudioStreamBuilder* builder_ = nullptr;
};

}  // namespace

AAudioWrapper::AAudioWrapper(const AudioParameters& audio_parameters,
                             aaudio_direction_t direction,
                             AAudioObserverInterface* observer)
    : audio_parameters_(audio_parameters),
      direction_(direction),
      observer_(observer) {
  RTC_LOG(INFO) << "ctor";
  RTC_DCHECK(observer_);
  aaudio_thread_checker_.Detach();
  RTC_LOG(INFO) << audio_parameters_.ToString();
}

AAudioWrapper::~AAudioWrapper() {
  RTC_LOG(INFO) << "dtor";
  RTC_DCHECK(thread_checker_.IsCurrent());
  RTC_DCHECK(!stream_);
}

bool AAudioWrapper::Init() {
  RTC_LOG(INFO) << "Init";
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Creates a stream builder which can be used to open an audio stream.
  ScopedStreamBuilder builder;
  // Configures the stream builder using audio parameters given at construction.
  SetStreamConfiguration(builder.get());
  // Opens a stream based on options in the stream builder.
  if (!OpenStream(builder.get())) {
    return false;
  }
  // Ensures that the opened stream could activate the requested settings.
  if (!VerifyStreamConfiguration()) {
    return false;
  }
  // Optimizes the buffer scheme for lowest possible latency and creates
  // additional buffer logic to match the 10ms buffer size used in WebRTC.
  if (!OptimizeBuffers()) {
    return false;
  }
  LogStreamState();
  return true;
}

bool AAudioWrapper::Start() {
  RTC_LOG(INFO) << "Start";
  RTC_DCHECK(thread_checker_.IsCurrent());
  // TODO(henrika): this state check might not be needed.
  aaudio_stream_state_t current_state = AAudioStream_getState(stream_);
  if (current_state != AAUDIO_STREAM_STATE_OPEN) {
    RTC_LOG(LS_ERROR) << "Invalid state: "
                      << AAudio_convertStreamStateToText(current_state);
    return false;
  }
  // Asynchronous request for the stream to start.
  RETURN_ON_ERROR(AAudioStream_requestStart(stream_), false);
  LogStreamState();
  return true;
}

bool AAudioWrapper::Stop() {
  RTC_LOG(INFO) << "Stop: " << DirectionToString(direction());
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Asynchronous request for the stream to stop.
  RETURN_ON_ERROR(AAudioStream_requestStop(stream_), false);
  CloseStream();
  aaudio_thread_checker_.Detach();
  return true;
}

double AAudioWrapper::EstimateLatencyMillis() const {
  RTC_DCHECK(stream_);
  double latency_millis = 0.0;
  if (direction() == AAUDIO_DIRECTION_INPUT) {
    // For input streams. Best guess we can do is to use the current burst size
    // as delay estimate.
    latency_millis = static_cast<double>(frames_per_burst()) / sample_rate() *
                     rtc::kNumMillisecsPerSec;
  } else {
    int64_t existing_frame_index;
    int64_t existing_frame_presentation_time;
    // Get the time at which a particular frame was presented to audio hardware.
    aaudio_result_t result = AAudioStream_getTimestamp(
        stream_, CLOCK_MONOTONIC, &existing_frame_index,
        &existing_frame_presentation_time);
    // Results are only valid when the stream is in AAUDIO_STREAM_STATE_STARTED.
    if (result == AAUDIO_OK) {
      // Get write index for next audio frame.
      int64_t next_frame_index = frames_written();
      // Number of frames between next frame and the existing frame.
      int64_t frame_index_delta = next_frame_index - existing_frame_index;
      // Assume the next frame will be written now.
      int64_t next_frame_write_time = rtc::TimeNanos();
      // Calculate time when next frame will be presented to the hardware taking
      // sample rate into account.
      int64_t frame_time_delta =
          (frame_index_delta * rtc::kNumNanosecsPerSec) / sample_rate();
      int64_t next_frame_presentation_time =
          existing_frame_presentation_time + frame_time_delta;
      // Derive a latency estimate given results above.
      latency_millis = static_cast<double>(next_frame_presentation_time -
                                           next_frame_write_time) /
                       rtc::kNumNanosecsPerMillisec;
    }
  }
  return latency_millis;
}

// Returns new buffer size or a negative error value if buffer size could not
// be increased.
bool AAudioWrapper::IncreaseOutputBufferSize() {
  RTC_LOG(INFO) << "IncreaseBufferSize";
  RTC_DCHECK(stream_);
  RTC_DCHECK(aaudio_thread_checker_.IsCurrent());
  RTC_DCHECK_EQ(direction(), AAUDIO_DIRECTION_OUTPUT);
  aaudio_result_t buffer_size = AAudioStream_getBufferSizeInFrames(stream_);
  // Try to increase size of buffer with one burst to reduce risk of underrun.
  buffer_size += frames_per_burst();
  // Verify that the new buffer size is not larger than max capacity.
  // TODO(henrika): keep track of case when we reach the capacity limit.
  const int32_t max_buffer_size = buffer_capacity_in_frames();
  if (buffer_size > max_buffer_size) {
    RTC_LOG(LS_ERROR) << "Required buffer size (" << buffer_size
                      << ") is higher than max: " << max_buffer_size;
    return false;
  }
  RTC_LOG(INFO) << "Updating buffer size to: " << buffer_size
                << " (max=" << max_buffer_size << ")";
  buffer_size = AAudioStream_setBufferSizeInFrames(stream_, buffer_size);
  if (buffer_size < 0) {
    RTC_LOG(LS_ERROR) << "Failed to change buffer size: "
                      << AAudio_convertResultToText(buffer_size);
    return false;
  }
  RTC_LOG(INFO) << "Buffer size changed to: " << buffer_size;
  return true;
}

void AAudioWrapper::ClearInputStream(void* audio_data, int32_t num_frames) {
  RTC_LOG(INFO) << "ClearInputStream";
  RTC_DCHECK(stream_);
  RTC_DCHECK(aaudio_thread_checker_.IsCurrent());
  RTC_DCHECK_EQ(direction(), AAUDIO_DIRECTION_INPUT);
  aaudio_result_t cleared_frames = 0;
  do {
    cleared_frames = AAudioStream_read(stream_, audio_data, num_frames, 0);
  } while (cleared_frames > 0);
}

AAudioObserverInterface* AAudioWrapper::observer() const {
  return observer_;
}

AudioParameters AAudioWrapper::audio_parameters() const {
  return audio_parameters_;
}

int32_t AAudioWrapper::samples_per_frame() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getSamplesPerFrame(stream_);
}

int32_t AAudioWrapper::buffer_size_in_frames() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getBufferSizeInFrames(stream_);
}

int32_t AAudioWrapper::buffer_capacity_in_frames() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getBufferCapacityInFrames(stream_);
}

int32_t AAudioWrapper::device_id() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getDeviceId(stream_);
}

int32_t AAudioWrapper::xrun_count() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getXRunCount(stream_);
}

int32_t AAudioWrapper::format() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getFormat(stream_);
}

int32_t AAudioWrapper::sample_rate() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getSampleRate(stream_);
}

int32_t AAudioWrapper::channel_count() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getChannelCount(stream_);
}

int32_t AAudioWrapper::frames_per_callback() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getFramesPerDataCallback(stream_);
}

aaudio_sharing_mode_t AAudioWrapper::sharing_mode() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getSharingMode(stream_);
}

aaudio_performance_mode_t AAudioWrapper::performance_mode() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getPerformanceMode(stream_);
}

aaudio_stream_state_t AAudioWrapper::stream_state() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getState(stream_);
}

int64_t AAudioWrapper::frames_written() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getFramesWritten(stream_);
}

int64_t AAudioWrapper::frames_read() const {
  RTC_DCHECK(stream_);
  return AAudioStream_getFramesRead(stream_);
}

void AAudioWrapper::SetStreamConfiguration(AAudioStreamBuilder* builder) {
  RTC_LOG(INFO) << "SetStreamConfiguration";
  RTC_DCHECK(builder);
  RTC_DCHECK(thread_checker_.IsCurrent());
  // Request usage of default primary output/input device.
  // TODO(henrika): verify that default device follows Java APIs.
  // https://developer.android.com/reference/android/media/AudioDeviceInfo.html.
  AAudioStreamBuilder_setDeviceId(builder, AAUDIO_UNSPECIFIED);
  // Use preferred sample rate given by the audio parameters.
  AAudioStreamBuilder_setSampleRate(builder, audio_parameters().sample_rate());
  // Use preferred channel configuration given by the audio parameters.
  AAudioStreamBuilder_setChannelCount(builder, audio_parameters().channels());
  // Always use 16-bit PCM audio sample format.
  AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
  // TODO(henrika): investigate effect of using AAUDIO_SHARING_MODE_EXCLUSIVE.
  // Ask for exclusive mode since this will give us the lowest possible latency.
  // If exclusive mode isn't available, shared mode will be used instead.
  AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
  // Use the direction that was given at construction.
  AAudioStreamBuilder_setDirection(builder, direction_);
  // TODO(henrika): investigate performance using different performance modes.
  AAudioStreamBuilder_setPerformanceMode(builder,
                                         AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
  // Given that WebRTC applications require low latency, our audio stream uses
  // an asynchronous callback function to transfer data to and from the
  // application. AAudio executes the callback in a higher-priority thread that
  // has better performance.
  AAudioStreamBuilder_setDataCallback(builder, DataCallback, this);
  // Request that AAudio calls this functions if any error occurs on a callback
  // thread.
  AAudioStreamBuilder_setErrorCallback(builder, ErrorCallback, this);
}

bool AAudioWrapper::OpenStream(AAudioStreamBuilder* builder) {
  RTC_LOG(INFO) << "OpenStream";
  RTC_DCHECK(builder);
  AAudioStream* stream = nullptr;
  RETURN_ON_ERROR(AAudioStreamBuilder_openStream(builder, &stream), false);
  stream_ = stream;
  LogStreamConfiguration();
  return true;
}

void AAudioWrapper::CloseStream() {
  RTC_LOG(INFO) << "CloseStream";
  RTC_DCHECK(stream_);
  LOG_ON_ERROR(AAudioStream_close(stream_));
  stream_ = nullptr;
}

void AAudioWrapper::LogStreamConfiguration() {
  RTC_DCHECK(stream_);
  char ss_buf[1024];
  rtc::SimpleStringBuilder ss(ss_buf);
  ss << "Stream Configuration: ";
  ss << "sample rate=" << sample_rate() << ", channels=" << channel_count();
  ss << ", samples per frame=" << samples_per_frame();
  ss << ", format=" << FormatToString(format());
  ss << ", sharing mode=" << SharingModeToString(sharing_mode());
  ss << ", performance mode=" << PerformanceModeToString(performance_mode());
  ss << ", direction=" << DirectionToString(direction());
  ss << ", device id=" << AAudioStream_getDeviceId(stream_);
  ss << ", frames per callback=" << frames_per_callback();
  RTC_LOG(INFO) << ss.str();
}

void AAudioWrapper::LogStreamState() {
  RTC_LOG(INFO) << "AAudio stream state: "
                << AAudio_convertStreamStateToText(stream_state());
}

bool AAudioWrapper::VerifyStreamConfiguration() {
  RTC_LOG(INFO) << "VerifyStreamConfiguration";
  RTC_DCHECK(stream_);
  // TODO(henrika): should we verify device ID as well?
  if (AAudioStream_getSampleRate(stream_) != audio_parameters().sample_rate()) {
    RTC_LOG(LS_ERROR) << "Stream unable to use requested sample rate";
    return false;
  }
  if (AAudioStream_getChannelCount(stream_) !=
      static_cast<int32_t>(audio_parameters().channels())) {
    RTC_LOG(LS_ERROR) << "Stream unable to use requested channel count";
    return false;
  }
  if (AAudioStream_getFormat(stream_) != AAUDIO_FORMAT_PCM_I16) {
    RTC_LOG(LS_ERROR) << "Stream unable to use requested format";
    return false;
  }
  if (AAudioStream_getSharingMode(stream_) != AAUDIO_SHARING_MODE_SHARED) {
    RTC_LOG(LS_ERROR) << "Stream unable to use requested sharing mode";
    return false;
  }
  if (AAudioStream_getPerformanceMode(stream_) !=
      AAUDIO_PERFORMANCE_MODE_LOW_LATENCY) {
    RTC_LOG(LS_ERROR) << "Stream unable to use requested performance mode";
    return false;
  }
  if (AAudioStream_getDirection(stream_) != direction()) {
    RTC_LOG(LS_ERROR) << "Stream direction could not be set";
    return false;
  }
  if (AAudioStream_getSamplesPerFrame(stream_) !=
      static_cast<int32_t>(audio_parameters().channels())) {
    RTC_LOG(LS_ERROR) << "Invalid number of samples per frame";
    return false;
  }
  return true;
}

bool AAudioWrapper::OptimizeBuffers() {
  RTC_LOG(INFO) << "OptimizeBuffers";
  RTC_DCHECK(stream_);
  // Maximum number of frames that can be filled without blocking.
  RTC_LOG(INFO) << "max buffer capacity in frames: "
                << buffer_capacity_in_frames();
  // Query the number of frames that the application should read or write at
  // one time for optimal performance.
  int32_t frames_per_burst = AAudioStream_getFramesPerBurst(stream_);
  RTC_LOG(INFO) << "frames per burst for optimal performance: "
                << frames_per_burst;
  frames_per_burst_ = frames_per_burst;
  if (direction() == AAUDIO_DIRECTION_INPUT) {
    // There is no point in calling setBufferSizeInFrames() for input streams
    // since it has no effect on the performance (latency in this case).
    return true;
  }
  // Set buffer size to same as burst size to guarantee lowest possible latency.
  // This size might change for output streams if underruns are detected and
  // automatic buffer adjustment is enabled.
  AAudioStream_setBufferSizeInFrames(stream_, frames_per_burst);
  int32_t buffer_size = AAudioStream_getBufferSizeInFrames(stream_);
  if (buffer_size != frames_per_burst) {
    RTC_LOG(LS_ERROR) << "Failed to use optimal buffer burst size";
    return false;
  }
  // Maximum number of frames that can be filled without blocking.
  RTC_LOG(INFO) << "buffer burst size in frames: " << buffer_size;
  return true;
}

}  // namespace jni

}  // namespace webrtc
