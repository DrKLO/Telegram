
/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/wayland/test/test_screencast_stream_provider.h"

#include <fcntl.h>
#include <sys/mman.h>
#include <sys/types.h>
#include <unistd.h>

#include <string>
#include <utility>
#include <vector>

#include "modules/portal/pipewire_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {

constexpr int kBytesPerPixel = 4;

TestScreenCastStreamProvider::TestScreenCastStreamProvider(Observer* observer,
                                                           uint32_t width,
                                                           uint32_t height)
    : observer_(observer), width_(width), height_(height) {
  if (!InitializePipeWire()) {
    RTC_LOG(LS_ERROR) << "Unable to open PipeWire";
    return;
  }

  pw_init(/*argc=*/nullptr, /*argc=*/nullptr);

  pw_main_loop_ = pw_thread_loop_new("pipewire-test-main-loop", nullptr);

  pw_context_ =
      pw_context_new(pw_thread_loop_get_loop(pw_main_loop_), nullptr, 0);
  if (!pw_context_) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Failed to create PipeWire context";
    return;
  }

  if (pw_thread_loop_start(pw_main_loop_) < 0) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Failed to start main PipeWire loop";
    return;
  }

  // Initialize event handlers, remote end and stream-related.
  pw_core_events_.version = PW_VERSION_CORE_EVENTS;
  pw_core_events_.error = &OnCoreError;

  pw_stream_events_.version = PW_VERSION_STREAM_EVENTS;
  pw_stream_events_.add_buffer = &OnStreamAddBuffer;
  pw_stream_events_.remove_buffer = &OnStreamRemoveBuffer;
  pw_stream_events_.state_changed = &OnStreamStateChanged;
  pw_stream_events_.param_changed = &OnStreamParamChanged;

  {
    PipeWireThreadLoopLock thread_loop_lock(pw_main_loop_);

    pw_core_ = pw_context_connect(pw_context_, nullptr, 0);
    if (!pw_core_) {
      RTC_LOG(LS_ERROR) << "PipeWire test: Failed to connect PipeWire context";
      return;
    }

    pw_core_add_listener(pw_core_, &spa_core_listener_, &pw_core_events_, this);

    pw_stream_ = pw_stream_new(pw_core_, "webrtc-test-stream", nullptr);

    if (!pw_stream_) {
      RTC_LOG(LS_ERROR) << "PipeWire test: Failed to create PipeWire stream";
      return;
    }

    pw_stream_add_listener(pw_stream_, &spa_stream_listener_,
                           &pw_stream_events_, this);
    uint8_t buffer[2048] = {};

    spa_pod_builder builder = spa_pod_builder{buffer, sizeof(buffer)};

    std::vector<const spa_pod*> params;

    spa_rectangle resolution =
        SPA_RECTANGLE(uint32_t(width_), uint32_t(height_));
    struct spa_fraction default_frame_rate = SPA_FRACTION(60, 1);
    params.push_back(BuildFormat(&builder, SPA_VIDEO_FORMAT_BGRx,
                                 /*modifiers=*/{}, &resolution,
                                 &default_frame_rate));

    auto flags =
        pw_stream_flags(PW_STREAM_FLAG_DRIVER | PW_STREAM_FLAG_ALLOC_BUFFERS);
    if (pw_stream_connect(pw_stream_, PW_DIRECTION_OUTPUT, SPA_ID_INVALID,
                          flags, params.data(), params.size()) != 0) {
      RTC_LOG(LS_ERROR) << "PipeWire test: Could not connect receiving stream.";
      pw_stream_destroy(pw_stream_);
      pw_stream_ = nullptr;
      return;
    }
  }

  return;
}

TestScreenCastStreamProvider::~TestScreenCastStreamProvider() {
  if (pw_main_loop_) {
    pw_thread_loop_stop(pw_main_loop_);
  }

  if (pw_stream_) {
    pw_stream_destroy(pw_stream_);
  }

  if (pw_core_) {
    pw_core_disconnect(pw_core_);
  }

  if (pw_context_) {
    pw_context_destroy(pw_context_);
  }

  if (pw_main_loop_) {
    pw_thread_loop_destroy(pw_main_loop_);
  }
}

void TestScreenCastStreamProvider::RecordFrame(RgbaColor rgba_color) {
  const char* error;
  if (pw_stream_get_state(pw_stream_, &error) != PW_STREAM_STATE_STREAMING) {
    if (error) {
      RTC_LOG(LS_ERROR)
          << "PipeWire test: Failed to record frame: stream is not active: "
          << error;
    }
  }

  struct pw_buffer* buffer = pw_stream_dequeue_buffer(pw_stream_);
  if (!buffer) {
    RTC_LOG(LS_ERROR) << "PipeWire test: No available buffer";
    return;
  }

  struct spa_buffer* spa_buffer = buffer->buffer;
  struct spa_data* spa_data = spa_buffer->datas;
  uint8_t* data = static_cast<uint8_t*>(spa_data->data);
  if (!data) {
    RTC_LOG(LS_ERROR)
        << "PipeWire test: Failed to record frame: invalid buffer data";
    pw_stream_queue_buffer(pw_stream_, buffer);
    return;
  }

  const int stride = SPA_ROUND_UP_N(width_ * kBytesPerPixel, 4);

  spa_data->chunk->offset = 0;
  spa_data->chunk->size = height_ * stride;
  spa_data->chunk->stride = stride;

  uint32_t color = rgba_color.ToUInt32();
  for (uint32_t i = 0; i < height_; i++) {
    uint32_t* column = reinterpret_cast<uint32_t*>(data);
    for (uint32_t j = 0; j < width_; j++) {
      column[j] = color;
    }
    data += stride;
  }

  pw_stream_queue_buffer(pw_stream_, buffer);
  if (observer_) {
    observer_->OnFrameRecorded();
  }
}

void TestScreenCastStreamProvider::StartStreaming() {
  if (pw_stream_ && pw_node_id_ != 0) {
    pw_stream_set_active(pw_stream_, true);
  }
}

void TestScreenCastStreamProvider::StopStreaming() {
  if (pw_stream_ && pw_node_id_ != 0) {
    pw_stream_set_active(pw_stream_, false);
  }
}

// static
void TestScreenCastStreamProvider::OnCoreError(void* data,
                                               uint32_t id,
                                               int seq,
                                               int res,
                                               const char* message) {
  TestScreenCastStreamProvider* that =
      static_cast<TestScreenCastStreamProvider*>(data);
  RTC_DCHECK(that);

  RTC_LOG(LS_ERROR) << "PipeWire test: PipeWire remote error: " << message;
}

// static
void TestScreenCastStreamProvider::OnStreamStateChanged(
    void* data,
    pw_stream_state old_state,
    pw_stream_state state,
    const char* error_message) {
  TestScreenCastStreamProvider* that =
      static_cast<TestScreenCastStreamProvider*>(data);
  RTC_DCHECK(that);

  switch (state) {
    case PW_STREAM_STATE_ERROR:
      RTC_LOG(LS_ERROR) << "PipeWire test: PipeWire stream state error: "
                        << error_message;
      break;
    case PW_STREAM_STATE_PAUSED:
      if (that->pw_node_id_ == 0 && that->pw_stream_) {
        that->pw_node_id_ = pw_stream_get_node_id(that->pw_stream_);
        that->observer_->OnStreamReady(that->pw_node_id_);
      } else {
        // Stop streaming
        that->is_streaming_ = false;
        that->observer_->OnStopStreaming();
      }
      break;
    case PW_STREAM_STATE_STREAMING:
      // Start streaming
      that->is_streaming_ = true;
      that->observer_->OnStartStreaming();
      break;
    case PW_STREAM_STATE_CONNECTING:
      break;
    case PW_STREAM_STATE_UNCONNECTED:
      if (that->is_streaming_) {
        // Stop streaming
        that->is_streaming_ = false;
        that->observer_->OnStopStreaming();
      }
      break;
  }
}

// static
void TestScreenCastStreamProvider::OnStreamParamChanged(
    void* data,
    uint32_t id,
    const struct spa_pod* format) {
  TestScreenCastStreamProvider* that =
      static_cast<TestScreenCastStreamProvider*>(data);
  RTC_DCHECK(that);

  RTC_LOG(LS_INFO) << "PipeWire test: PipeWire stream format changed.";
  if (!format || id != SPA_PARAM_Format) {
    return;
  }

  spa_format_video_raw_parse(format, &that->spa_video_format_);

  auto stride = SPA_ROUND_UP_N(that->width_ * kBytesPerPixel, 4);

  uint8_t buffer[1024] = {};
  auto builder = spa_pod_builder{buffer, sizeof(buffer)};

  // Setup buffers and meta header for new format.

  std::vector<const spa_pod*> params;
  const int buffer_types = (1 << SPA_DATA_MemFd);
  spa_rectangle resolution = SPA_RECTANGLE(that->width_, that->height_);

  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamBuffers, SPA_PARAM_Buffers,
      SPA_FORMAT_VIDEO_size, SPA_POD_Rectangle(&resolution),
      SPA_PARAM_BUFFERS_buffers, SPA_POD_CHOICE_RANGE_Int(16, 2, 16),
      SPA_PARAM_BUFFERS_blocks, SPA_POD_Int(1), SPA_PARAM_BUFFERS_stride,
      SPA_POD_Int(stride), SPA_PARAM_BUFFERS_size,
      SPA_POD_Int(stride * that->height_), SPA_PARAM_BUFFERS_align,
      SPA_POD_Int(16), SPA_PARAM_BUFFERS_dataType,
      SPA_POD_CHOICE_FLAGS_Int(buffer_types))));
  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamMeta, SPA_PARAM_Meta, SPA_PARAM_META_type,
      SPA_POD_Id(SPA_META_Header), SPA_PARAM_META_size,
      SPA_POD_Int(sizeof(struct spa_meta_header)))));

  pw_stream_update_params(that->pw_stream_, params.data(), params.size());
}

// static
void TestScreenCastStreamProvider::OnStreamAddBuffer(void* data,
                                                     pw_buffer* buffer) {
  TestScreenCastStreamProvider* that =
      static_cast<TestScreenCastStreamProvider*>(data);
  RTC_DCHECK(that);

  struct spa_data* spa_data = buffer->buffer->datas;

  spa_data->mapoffset = 0;
  spa_data->flags = SPA_DATA_FLAG_READWRITE;

  if (!(spa_data[0].type & (1 << SPA_DATA_MemFd))) {
    RTC_LOG(LS_ERROR)
        << "PipeWire test: Client doesn't support memfd buffer data type";
    return;
  }

  const int stride = SPA_ROUND_UP_N(that->width_ * kBytesPerPixel, 4);
  spa_data->maxsize = stride * that->height_;
  spa_data->type = SPA_DATA_MemFd;
  spa_data->fd =
      memfd_create("pipewire-test-memfd", MFD_CLOEXEC | MFD_ALLOW_SEALING);
  if (spa_data->fd == kInvalidPipeWireFd) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Can't create memfd";
    return;
  }

  spa_data->mapoffset = 0;

  if (ftruncate(spa_data->fd, spa_data->maxsize) < 0) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Can't truncate to"
                      << spa_data->maxsize;
    return;
  }

  unsigned int seals = F_SEAL_GROW | F_SEAL_SHRINK | F_SEAL_SEAL;
  if (fcntl(spa_data->fd, F_ADD_SEALS, seals) == -1) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Failed to add seals";
  }

  spa_data->data = mmap(nullptr, spa_data->maxsize, PROT_READ | PROT_WRITE,
                        MAP_SHARED, spa_data->fd, spa_data->mapoffset);
  if (spa_data->data == MAP_FAILED) {
    RTC_LOG(LS_ERROR) << "PipeWire test: Failed to mmap memory";
  } else {
    that->observer_->OnBufferAdded();
    RTC_LOG(LS_INFO) << "PipeWire test: Memfd created successfully: "
                     << spa_data->data << spa_data->maxsize;
  }
}

// static
void TestScreenCastStreamProvider::OnStreamRemoveBuffer(void* data,
                                                        pw_buffer* buffer) {
  TestScreenCastStreamProvider* that =
      static_cast<TestScreenCastStreamProvider*>(data);
  RTC_DCHECK(that);

  struct spa_buffer* spa_buffer = buffer->buffer;
  struct spa_data* spa_data = spa_buffer->datas;
  if (spa_data && spa_data->type == SPA_DATA_MemFd) {
    munmap(spa_data->data, spa_data->maxsize);
    close(spa_data->fd);
  }
}

uint32_t TestScreenCastStreamProvider::PipeWireNodeId() {
  return pw_node_id_;
}

}  // namespace webrtc
