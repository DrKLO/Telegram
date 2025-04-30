/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/linux/wayland/shared_screencast_stream.h"

#include <fcntl.h>
#include <libdrm/drm_fourcc.h>
#include <pipewire/pipewire.h>
#include <spa/param/video/format-utils.h>
#include <sys/mman.h>

#include <vector>

#include "absl/memory/memory.h"
#include "modules/desktop_capture/linux/wayland/egl_dmabuf.h"
#include "modules/desktop_capture/linux/wayland/screencast_stream_utils.h"
#include "modules/portal/pipewire_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/sanitizer.h"
#include "rtc_base/synchronization/mutex.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

const int kBytesPerPixel = 4;
const int kVideoDamageRegionCount = 16;

constexpr int kCursorBpp = 4;
constexpr int CursorMetaSize(int w, int h) {
  return (sizeof(struct spa_meta_cursor) + sizeof(struct spa_meta_bitmap) +
          w * h * kCursorBpp);
}

constexpr PipeWireVersion kDmaBufModifierMinVersion = {0, 3, 33};
constexpr PipeWireVersion kDropSingleModifierMinVersion = {0, 3, 40};

class ScopedBuf {
 public:
  ScopedBuf() {}
  ScopedBuf(uint8_t* map, int map_size, int fd)
      : map_(map), map_size_(map_size), fd_(fd) {}
  ~ScopedBuf() {
    if (map_ != MAP_FAILED) {
      munmap(map_, map_size_);
    }
  }

  explicit operator bool() { return map_ != MAP_FAILED; }

  void initialize(uint8_t* map, int map_size, int fd) {
    map_ = map;
    map_size_ = map_size;
    fd_ = fd;
  }

  uint8_t* get() { return map_; }

 protected:
  uint8_t* map_ = static_cast<uint8_t*>(MAP_FAILED);
  int map_size_;
  int fd_;
};

class SharedScreenCastStreamPrivate {
 public:
  SharedScreenCastStreamPrivate();
  ~SharedScreenCastStreamPrivate();

  bool StartScreenCastStream(uint32_t stream_node_id,
                             int fd,
                             uint32_t width = 0,
                             uint32_t height = 0,
                             bool is_cursor_embedded = false,
                             DesktopCapturer::Callback* callback = nullptr);
  void UpdateScreenCastStreamResolution(uint32_t width, uint32_t height);
  void UpdateScreenCastStreamFrameRate(uint32_t frame_rate);
  void SetUseDamageRegion(bool use_damage_region) {
    use_damage_region_ = use_damage_region;
  }
  void SetObserver(SharedScreenCastStream::Observer* observer) {
    observer_ = observer;
  }
  void StopScreenCastStream();
  std::unique_ptr<SharedDesktopFrame> CaptureFrame();
  std::unique_ptr<MouseCursor> CaptureCursor();
  DesktopVector CaptureCursorPosition();

 private:
  // Stops the streams and cleans up any in-use elements.
  void StopAndCleanupStream();

  SharedScreenCastStream::Observer* observer_ = nullptr;

  // Track damage region updates that were reported since the last time
  // frame was captured
  DesktopRegion damage_region_;

  uint32_t pw_stream_node_id_ = 0;

  DesktopSize stream_size_ = {};
  DesktopSize frame_size_;

  webrtc::Mutex queue_lock_;
  ScreenCaptureFrameQueue<SharedDesktopFrame> queue_
      RTC_GUARDED_BY(&queue_lock_);
  std::unique_ptr<MouseCursor> mouse_cursor_;
  DesktopVector mouse_cursor_position_ = DesktopVector(-1, -1);

  int64_t modifier_;
  std::unique_ptr<EglDmaBuf> egl_dmabuf_;
  // List of modifiers we query as supported by the graphics card/driver
  std::vector<uint64_t> modifiers_;

  // PipeWire types
  struct pw_context* pw_context_ = nullptr;
  struct pw_core* pw_core_ = nullptr;
  struct pw_stream* pw_stream_ = nullptr;
  struct pw_thread_loop* pw_main_loop_ = nullptr;
  struct spa_source* renegotiate_ = nullptr;

  spa_hook spa_core_listener_;
  spa_hook spa_stream_listener_;

  // A number used to verify all previous methods and the resulting
  // events have been handled.
  int server_version_sync_ = 0;
  // Version of the running PipeWire server we communicate with
  PipeWireVersion pw_server_version_;
  // Version of the library used to run our code
  PipeWireVersion pw_client_version_;

  // Resolution parameters.
  uint32_t width_ = 0;
  uint32_t height_ = 0;
  // Frame rate.
  uint32_t frame_rate_ = 60;

  bool use_damage_region_ = true;

  // Specifies whether the pipewire stream has been initialized with a request
  // to embed cursor into the captured frames.
  bool is_cursor_embedded_ = false;

  // event handlers
  pw_core_events pw_core_events_ = {};
  pw_stream_events pw_stream_events_ = {};

  struct spa_video_info_raw spa_video_format_;

  void ProcessBuffer(pw_buffer* buffer);
  bool ProcessMemFDBuffer(pw_buffer* buffer,
                          DesktopFrame& frame,
                          const DesktopVector& offset);
  bool ProcessDMABuffer(pw_buffer* buffer,
                        DesktopFrame& frame,
                        const DesktopVector& offset);
  void ConvertRGBxToBGRx(uint8_t* frame, uint32_t size);
  void UpdateFrameUpdatedRegions(const spa_buffer* spa_buffer,
                                 DesktopFrame& frame);
  void NotifyCallbackOfNewFrame(std::unique_ptr<SharedDesktopFrame> frame);

  // PipeWire callbacks
  static void OnCoreError(void* data,
                          uint32_t id,
                          int seq,
                          int res,
                          const char* message);
  static void OnCoreDone(void* user_data, uint32_t id, int seq);
  static void OnCoreInfo(void* user_data, const pw_core_info* info);
  static void OnStreamParamChanged(void* data,
                                   uint32_t id,
                                   const struct spa_pod* format);
  static void OnStreamStateChanged(void* data,
                                   pw_stream_state old_state,
                                   pw_stream_state state,
                                   const char* error_message);
  static void OnStreamProcess(void* data);
  // This will be invoked in case we fail to process DMA-BUF PW buffer using
  // negotiated stream parameters (modifier). We will drop the modifier we
  // failed to use and try to use a different one or fallback to shared memory
  // buffers.
  static void OnRenegotiateFormat(void* data, uint64_t);

  DesktopCapturer::Callback* callback_;
};

void SharedScreenCastStreamPrivate::OnCoreError(void* data,
                                                uint32_t id,
                                                int seq,
                                                int res,
                                                const char* message) {
  SharedScreenCastStreamPrivate* stream =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(stream);

  RTC_LOG(LS_ERROR) << "PipeWire remote error: " << message;
  pw_thread_loop_signal(stream->pw_main_loop_, false);
}

void SharedScreenCastStreamPrivate::OnCoreInfo(void* data,
                                               const pw_core_info* info) {
  SharedScreenCastStreamPrivate* stream =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(stream);

  stream->pw_server_version_ = PipeWireVersion::Parse(info->version);
}

void SharedScreenCastStreamPrivate::OnCoreDone(void* data,
                                               uint32_t id,
                                               int seq) {
  const SharedScreenCastStreamPrivate* stream =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(stream);

  if (id == PW_ID_CORE && stream->server_version_sync_ == seq) {
    pw_thread_loop_signal(stream->pw_main_loop_, false);
  }
}

// static
void SharedScreenCastStreamPrivate::OnStreamStateChanged(
    void* data,
    pw_stream_state old_state,
    pw_stream_state state,
    const char* error_message) {
  SharedScreenCastStreamPrivate* that =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(that);

  switch (state) {
    case PW_STREAM_STATE_ERROR:
      RTC_LOG(LS_ERROR) << "PipeWire stream state error: " << error_message;
      break;
    case PW_STREAM_STATE_PAUSED:
      if (that->observer_ && old_state != PW_STREAM_STATE_STREAMING) {
        that->observer_->OnStreamConfigured();
      }
      break;
    case PW_STREAM_STATE_STREAMING:
    case PW_STREAM_STATE_UNCONNECTED:
    case PW_STREAM_STATE_CONNECTING:
      break;
  }
}

// static
void SharedScreenCastStreamPrivate::OnStreamParamChanged(
    void* data,
    uint32_t id,
    const struct spa_pod* format) {
  SharedScreenCastStreamPrivate* that =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(that);

  RTC_LOG(LS_INFO) << "PipeWire stream format changed.";
  if (!format || id != SPA_PARAM_Format) {
    return;
  }

  spa_format_video_raw_parse(format, &that->spa_video_format_);

  if (that->observer_ && that->spa_video_format_.max_framerate.denom) {
    that->observer_->OnFrameRateChanged(
        that->spa_video_format_.max_framerate.num /
        that->spa_video_format_.max_framerate.denom);
  }

  auto width = that->spa_video_format_.size.width;
  auto height = that->spa_video_format_.size.height;
  auto stride = SPA_ROUND_UP_N(width * kBytesPerPixel, 4);
  auto size = height * stride;

  that->stream_size_ = DesktopSize(width, height);

  uint8_t buffer[2048] = {};
  auto builder = spa_pod_builder{buffer, sizeof(buffer)};

  // Setup buffers and meta header for new format.

  // When SPA_FORMAT_VIDEO_modifier is present we can use DMA-BUFs as
  // the server announces support for it.
  // See https://github.com/PipeWire/pipewire/blob/master/doc/dma-buf.dox
  const bool has_modifier =
      spa_pod_find_prop(format, nullptr, SPA_FORMAT_VIDEO_modifier);
  that->modifier_ =
      has_modifier ? that->spa_video_format_.modifier : DRM_FORMAT_MOD_INVALID;
  std::vector<const spa_pod*> params;
  const int buffer_types = has_modifier
                               ? (1 << SPA_DATA_DmaBuf) | (1 << SPA_DATA_MemFd)
                               : (1 << SPA_DATA_MemFd);

  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamBuffers, SPA_PARAM_Buffers,
      SPA_PARAM_BUFFERS_size, SPA_POD_Int(size), SPA_PARAM_BUFFERS_stride,
      SPA_POD_Int(stride), SPA_PARAM_BUFFERS_buffers,
      SPA_POD_CHOICE_RANGE_Int(8, 1, 32), SPA_PARAM_BUFFERS_dataType,
      SPA_POD_CHOICE_FLAGS_Int(buffer_types))));
  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamMeta, SPA_PARAM_Meta, SPA_PARAM_META_type,
      SPA_POD_Id(SPA_META_Header), SPA_PARAM_META_size,
      SPA_POD_Int(sizeof(struct spa_meta_header)))));
  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamMeta, SPA_PARAM_Meta, SPA_PARAM_META_type,
      SPA_POD_Id(SPA_META_VideoCrop), SPA_PARAM_META_size,
      SPA_POD_Int(sizeof(struct spa_meta_region)))));
  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamMeta, SPA_PARAM_Meta, SPA_PARAM_META_type,
      SPA_POD_Id(SPA_META_Cursor), SPA_PARAM_META_size,
      SPA_POD_CHOICE_RANGE_Int(CursorMetaSize(64, 64), CursorMetaSize(1, 1),
                               CursorMetaSize(384, 384)))));
  params.push_back(reinterpret_cast<spa_pod*>(spa_pod_builder_add_object(
      &builder, SPA_TYPE_OBJECT_ParamMeta, SPA_PARAM_Meta, SPA_PARAM_META_type,
      SPA_POD_Id(SPA_META_VideoDamage), SPA_PARAM_META_size,
      SPA_POD_CHOICE_RANGE_Int(
          sizeof(struct spa_meta_region) * kVideoDamageRegionCount,
          sizeof(struct spa_meta_region) * 1,
          sizeof(struct spa_meta_region) * kVideoDamageRegionCount))));

  pw_stream_update_params(that->pw_stream_, params.data(), params.size());
}

// static
void SharedScreenCastStreamPrivate::OnStreamProcess(void* data) {
  SharedScreenCastStreamPrivate* that =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(that);

  struct pw_buffer* next_buffer;
  struct pw_buffer* buffer = nullptr;

  next_buffer = pw_stream_dequeue_buffer(that->pw_stream_);
  while (next_buffer) {
    buffer = next_buffer;
    next_buffer = pw_stream_dequeue_buffer(that->pw_stream_);

    if (next_buffer) {
      pw_stream_queue_buffer(that->pw_stream_, buffer);
    }
  }

  if (!buffer) {
    return;
  }

  that->ProcessBuffer(buffer);

  pw_stream_queue_buffer(that->pw_stream_, buffer);
}

void SharedScreenCastStreamPrivate::OnRenegotiateFormat(void* data, uint64_t) {
  SharedScreenCastStreamPrivate* that =
      static_cast<SharedScreenCastStreamPrivate*>(data);
  RTC_DCHECK(that);

  {
    PipeWireThreadLoopLock thread_loop_lock(that->pw_main_loop_);

    uint8_t buffer[4096] = {};

    spa_pod_builder builder = spa_pod_builder{buffer, sizeof(buffer)};

    std::vector<const spa_pod*> params;
    struct spa_rectangle resolution =
        SPA_RECTANGLE(that->width_, that->height_);
    struct spa_fraction frame_rate = SPA_FRACTION(that->frame_rate_, 1);

    for (uint32_t format : {SPA_VIDEO_FORMAT_BGRA, SPA_VIDEO_FORMAT_RGBA,
                            SPA_VIDEO_FORMAT_BGRx, SPA_VIDEO_FORMAT_RGBx}) {
      if (!that->modifiers_.empty()) {
        params.push_back(
            BuildFormat(&builder, format, that->modifiers_,
                        that->width_ && that->height_ ? &resolution : nullptr,
                        &frame_rate));
      }
      params.push_back(BuildFormat(
          &builder, format, /*modifiers=*/{},
          that->width_ && that->height_ ? &resolution : nullptr, &frame_rate));
    }

    pw_stream_update_params(that->pw_stream_, params.data(), params.size());
  }
}

SharedScreenCastStreamPrivate::SharedScreenCastStreamPrivate() {}

SharedScreenCastStreamPrivate::~SharedScreenCastStreamPrivate() {
  StopAndCleanupStream();
}

RTC_NO_SANITIZE("cfi-icall")
bool SharedScreenCastStreamPrivate::StartScreenCastStream(
    uint32_t stream_node_id,
    int fd,
    uint32_t width,
    uint32_t height,
    bool is_cursor_embedded,
    DesktopCapturer::Callback* callback) {
  width_ = width;
  height_ = height;
  callback_ = callback;
  is_cursor_embedded_ = is_cursor_embedded;
  if (!InitializePipeWire()) {
    RTC_LOG(LS_ERROR) << "Unable to open PipeWire library";
    return false;
  }
  egl_dmabuf_ = std::make_unique<EglDmaBuf>();

  pw_stream_node_id_ = stream_node_id;

  pw_init(/*argc=*/nullptr, /*argc=*/nullptr);

  pw_main_loop_ = pw_thread_loop_new("pipewire-main-loop", nullptr);

  pw_context_ =
      pw_context_new(pw_thread_loop_get_loop(pw_main_loop_), nullptr, 0);
  if (!pw_context_) {
    RTC_LOG(LS_ERROR) << "Failed to create PipeWire context";
    return false;
  }

  if (pw_thread_loop_start(pw_main_loop_) < 0) {
    RTC_LOG(LS_ERROR) << "Failed to start main PipeWire loop";
    return false;
  }

  pw_client_version_ = PipeWireVersion::Parse(pw_get_library_version());

  // Initialize event handlers, remote end and stream-related.
  pw_core_events_.version = PW_VERSION_CORE_EVENTS;
  pw_core_events_.info = &OnCoreInfo;
  pw_core_events_.done = &OnCoreDone;
  pw_core_events_.error = &OnCoreError;

  pw_stream_events_.version = PW_VERSION_STREAM_EVENTS;
  pw_stream_events_.state_changed = &OnStreamStateChanged;
  pw_stream_events_.param_changed = &OnStreamParamChanged;
  pw_stream_events_.process = &OnStreamProcess;

  {
    PipeWireThreadLoopLock thread_loop_lock(pw_main_loop_);

    if (fd != kInvalidPipeWireFd) {
      pw_core_ = pw_context_connect_fd(
          pw_context_, fcntl(fd, F_DUPFD_CLOEXEC, 0), nullptr, 0);
    } else {
      pw_core_ = pw_context_connect(pw_context_, nullptr, 0);
    }

    if (!pw_core_) {
      RTC_LOG(LS_ERROR) << "Failed to connect PipeWire context";
      return false;
    }

    pw_core_add_listener(pw_core_, &spa_core_listener_, &pw_core_events_, this);

    // Add an event that can be later invoked by pw_loop_signal_event()
    renegotiate_ = pw_loop_add_event(pw_thread_loop_get_loop(pw_main_loop_),
                                     OnRenegotiateFormat, this);

    server_version_sync_ =
        pw_core_sync(pw_core_, PW_ID_CORE, server_version_sync_);

    pw_thread_loop_wait(pw_main_loop_);

    pw_properties* reuseProps =
        pw_properties_new_string("pipewire.client.reuse=1");
    pw_stream_ = pw_stream_new(pw_core_, "webrtc-consume-stream", reuseProps);

    if (!pw_stream_) {
      RTC_LOG(LS_ERROR) << "Failed to create PipeWire stream";
      return false;
    }

    pw_stream_add_listener(pw_stream_, &spa_stream_listener_,
                           &pw_stream_events_, this);
    uint8_t buffer[4096] = {};

    spa_pod_builder builder = spa_pod_builder{buffer, sizeof(buffer)};

    std::vector<const spa_pod*> params;
    const bool has_required_pw_client_version =
        pw_client_version_ >= kDmaBufModifierMinVersion;
    const bool has_required_pw_server_version =
        pw_server_version_ >= kDmaBufModifierMinVersion;
    struct spa_rectangle resolution;
    bool set_resolution = false;
    if (width && height) {
      resolution = SPA_RECTANGLE(width, height);
      set_resolution = true;
    }
    struct spa_fraction default_frame_rate = SPA_FRACTION(frame_rate_, 1);
    for (uint32_t format : {SPA_VIDEO_FORMAT_BGRA, SPA_VIDEO_FORMAT_RGBA,
                            SPA_VIDEO_FORMAT_BGRx, SPA_VIDEO_FORMAT_RGBx}) {
      // Modifiers can be used with PipeWire >= 0.3.33
      if (has_required_pw_client_version && has_required_pw_server_version) {
        modifiers_ = egl_dmabuf_->QueryDmaBufModifiers(format);

        if (!modifiers_.empty()) {
          params.push_back(BuildFormat(&builder, format, modifiers_,
                                       set_resolution ? &resolution : nullptr,
                                       &default_frame_rate));
        }
      }

      params.push_back(BuildFormat(&builder, format, /*modifiers=*/{},
                                   set_resolution ? &resolution : nullptr,
                                   &default_frame_rate));
    }

    if (pw_stream_connect(pw_stream_, PW_DIRECTION_INPUT, pw_stream_node_id_,
                          PW_STREAM_FLAG_AUTOCONNECT, params.data(),
                          params.size()) != 0) {
      RTC_LOG(LS_ERROR) << "Could not connect receiving stream.";
      return false;
    }

    RTC_LOG(LS_INFO) << "PipeWire remote opened.";
  }
  return true;
}

RTC_NO_SANITIZE("cfi-icall")
void SharedScreenCastStreamPrivate::UpdateScreenCastStreamResolution(
    uint32_t width,
    uint32_t height) {
  if (!width || !height) {
    RTC_LOG(LS_WARNING) << "Bad resolution specified: " << width << "x"
                        << height;
    return;
  }
  if (!pw_main_loop_) {
    RTC_LOG(LS_WARNING) << "No main pipewire loop, ignoring resolution change";
    return;
  }
  if (!renegotiate_) {
    RTC_LOG(LS_WARNING) << "Can not renegotiate stream params, ignoring "
                        << "resolution change";
    return;
  }
  if (width_ != width || height_ != height) {
    width_ = width;
    height_ = height;
    pw_loop_signal_event(pw_thread_loop_get_loop(pw_main_loop_), renegotiate_);
  }
}

RTC_NO_SANITIZE("cfi-icall")
void SharedScreenCastStreamPrivate::UpdateScreenCastStreamFrameRate(
    uint32_t frame_rate) {
  if (!pw_main_loop_) {
    RTC_LOG(LS_WARNING) << "No main pipewire loop, ignoring frame rate change";
    return;
  }
  if (!renegotiate_) {
    RTC_LOG(LS_WARNING) << "Can not renegotiate stream params, ignoring "
                        << "frame rate change";
    return;
  }
  if (frame_rate_ != frame_rate) {
    frame_rate_ = frame_rate;
    pw_loop_signal_event(pw_thread_loop_get_loop(pw_main_loop_), renegotiate_);
  }
}

void SharedScreenCastStreamPrivate::StopScreenCastStream() {
  StopAndCleanupStream();
}

void SharedScreenCastStreamPrivate::StopAndCleanupStream() {
  // We get buffers on the PipeWire thread, but this is called from the capturer
  // thread, so we need to wait on and stop the pipewire thread before we
  // disconnect the stream so that we can guarantee we aren't in the middle of
  // processing a new frame.

  // Even if we *do* somehow have the other objects without a pipewire thread,
  // destroying them without a thread causes a crash.
  if (!pw_main_loop_)
    return;

  // While we can stop the thread now, we cannot destroy it until we've cleaned
  // up the other members.
  pw_thread_loop_wait(pw_main_loop_);
  pw_thread_loop_stop(pw_main_loop_);

  if (pw_stream_) {
    pw_stream_disconnect(pw_stream_);
    pw_stream_destroy(pw_stream_);
    pw_stream_ = nullptr;

    {
      webrtc::MutexLock lock(&queue_lock_);
      queue_.Reset();
    }
  }

  if (pw_core_) {
    pw_core_disconnect(pw_core_);
    pw_core_ = nullptr;
  }

  if (pw_context_) {
    pw_context_destroy(pw_context_);
    pw_context_ = nullptr;
  }

  pw_thread_loop_destroy(pw_main_loop_);
  pw_main_loop_ = nullptr;
}

std::unique_ptr<SharedDesktopFrame>
SharedScreenCastStreamPrivate::CaptureFrame() {
  webrtc::MutexLock lock(&queue_lock_);

  if (!pw_stream_ || !queue_.current_frame()) {
    return std::unique_ptr<SharedDesktopFrame>{};
  }

  std::unique_ptr<SharedDesktopFrame> frame = queue_.current_frame()->Share();
  if (use_damage_region_) {
    frame->mutable_updated_region()->Swap(&damage_region_);
    damage_region_.Clear();
  }

  return frame;
}

std::unique_ptr<MouseCursor> SharedScreenCastStreamPrivate::CaptureCursor() {
  if (!mouse_cursor_) {
    return nullptr;
  }

  return std::move(mouse_cursor_);
}

DesktopVector SharedScreenCastStreamPrivate::CaptureCursorPosition() {
  return mouse_cursor_position_;
}

void SharedScreenCastStreamPrivate::UpdateFrameUpdatedRegions(
    const spa_buffer* spa_buffer,
    DesktopFrame& frame) {
  if (!use_damage_region_) {
    frame.mutable_updated_region()->SetRect(
        DesktopRect::MakeSize(frame.size()));
    return;
  }

  const struct spa_meta* video_damage = static_cast<struct spa_meta*>(
      spa_buffer_find_meta(spa_buffer, SPA_META_VideoDamage));
  if (!video_damage) {
    damage_region_.SetRect(DesktopRect::MakeSize(frame.size()));
    return;
  }

  frame.mutable_updated_region()->Clear();
  spa_meta_region* meta_region;
  spa_meta_for_each(meta_region, video_damage) {
    // Skip empty regions
    if (meta_region->region.size.width == 0 ||
        meta_region->region.size.height == 0) {
      continue;
    }

    damage_region_.AddRect(DesktopRect::MakeXYWH(
        meta_region->region.position.x, meta_region->region.position.y,
        meta_region->region.size.width, meta_region->region.size.height));
  }
}

void SharedScreenCastStreamPrivate::NotifyCallbackOfNewFrame(
    std::unique_ptr<SharedDesktopFrame> frame) {
  if (!pw_stream_ || !frame->data()) {
    callback_->OnCaptureResult(DesktopCapturer::Result::ERROR_TEMPORARY,
                               nullptr);
    return;
  }

  if (use_damage_region_) {
    frame->mutable_updated_region()->Swap(&damage_region_);
    damage_region_.Clear();
  }
  callback_->OnCaptureResult(DesktopCapturer::Result::SUCCESS,
                             std::move(frame));
}

RTC_NO_SANITIZE("cfi-icall")
void SharedScreenCastStreamPrivate::ProcessBuffer(pw_buffer* buffer) {
  int64_t capture_start_time_nanos = rtc::TimeNanos();
  if (callback_) {
    callback_->OnFrameCaptureStart();
  }

  spa_buffer* spa_buffer = buffer->buffer;

  // Try to update the mouse cursor first, because it can be the only
  // information carried by the buffer
  {
    const struct spa_meta_cursor* cursor =
        static_cast<struct spa_meta_cursor*>(spa_buffer_find_meta_data(
            spa_buffer, SPA_META_Cursor, sizeof(*cursor)));
    if (cursor && spa_meta_cursor_is_valid(cursor)) {
      struct spa_meta_bitmap* bitmap = nullptr;

      if (cursor->bitmap_offset)
        bitmap =
            SPA_MEMBER(cursor, cursor->bitmap_offset, struct spa_meta_bitmap);

      if (bitmap && bitmap->size.width > 0 && bitmap->size.height > 0) {
        const uint8_t* bitmap_data =
            SPA_MEMBER(bitmap, bitmap->offset, uint8_t);
        BasicDesktopFrame* mouse_frame = new BasicDesktopFrame(
            DesktopSize(bitmap->size.width, bitmap->size.height));
        mouse_frame->CopyPixelsFrom(
            bitmap_data, bitmap->stride,
            DesktopRect::MakeWH(bitmap->size.width, bitmap->size.height));
        mouse_cursor_ = std::make_unique<MouseCursor>(
            mouse_frame, DesktopVector(cursor->hotspot.x, cursor->hotspot.y));

        if (observer_) {
          observer_->OnCursorShapeChanged();
        }
      }
      mouse_cursor_position_.set(cursor->position.x, cursor->position.y);

      if (observer_) {
        observer_->OnCursorPositionChanged();
      }
    }
  }

  if (spa_buffer->datas[0].chunk->size == 0) {
    return;
  }

  // Use SPA_META_VideoCrop metadata to get the frame size. KDE and GNOME do
  // handle screen/window sharing differently. KDE/KWin doesn't use
  // SPA_META_VideoCrop metadata and when sharing a window, it always sets
  // stream size to size of the window. With that we just allocate the
  // DesktopFrame using the size of the stream itself. GNOME/Mutter
  // always sets stream size to the size of the whole screen, even when sharing
  // a window. To get the real window size we have to use SPA_META_VideoCrop
  // metadata. This gives us the size we need in order to allocate the
  // DesktopFrame.

  struct spa_meta_region* videocrop_metadata =
      static_cast<struct spa_meta_region*>(spa_buffer_find_meta_data(
          spa_buffer, SPA_META_VideoCrop, sizeof(*videocrop_metadata)));

  // Video size from metadata is bigger than an actual video stream size.
  // The metadata are wrong or we should up-scale the video...in both cases
  // just quit now.
  if (videocrop_metadata &&
      (videocrop_metadata->region.size.width >
           static_cast<uint32_t>(stream_size_.width()) ||
       videocrop_metadata->region.size.height >
           static_cast<uint32_t>(stream_size_.height()))) {
    RTC_LOG(LS_ERROR) << "Stream metadata sizes are wrong!";

    if (observer_) {
      observer_->OnFailedToProcessBuffer();
    }

    return;
  }

  // Use SPA_META_VideoCrop metadata to get the DesktopFrame size in case
  // a windows is shared and it represents just a small portion of the
  // stream itself. This will be for example used in case of GNOME (Mutter)
  // where the stream will have the size of the screen itself, but we care
  // only about smaller portion representing the window inside.
  bool videocrop_metadata_use = false;
  const struct spa_rectangle* videocrop_metadata_size =
      videocrop_metadata ? &videocrop_metadata->region.size : nullptr;

  if (videocrop_metadata_size && videocrop_metadata_size->width != 0 &&
      videocrop_metadata_size->height != 0 &&
      (static_cast<int>(videocrop_metadata_size->width) <
           stream_size_.width() ||
       static_cast<int>(videocrop_metadata_size->height) <
           stream_size_.height())) {
    videocrop_metadata_use = true;
  }

  if (videocrop_metadata_use) {
    frame_size_ = DesktopSize(videocrop_metadata_size->width,
                              videocrop_metadata_size->height);
  } else {
    frame_size_ = stream_size_;
  }

  // Get the position of the video crop within the stream. Just double-check
  // that the position doesn't exceed the size of the stream itself.
  // NOTE: Currently it looks there is no implementation using this.
  uint32_t y_offset =
      videocrop_metadata_use &&
              (videocrop_metadata->region.position.y + frame_size_.height() <=
               stream_size_.height())
          ? videocrop_metadata->region.position.y
          : 0;
  uint32_t x_offset =
      videocrop_metadata_use &&
              (videocrop_metadata->region.position.x + frame_size_.width() <=
               stream_size_.width())
          ? videocrop_metadata->region.position.x
          : 0;
  DesktopVector offset = DesktopVector(x_offset, y_offset);

  webrtc::MutexLock lock(&queue_lock_);

  queue_.MoveToNextFrame();
  if (queue_.current_frame() && queue_.current_frame()->IsShared()) {
    RTC_DLOG(LS_WARNING) << "Overwriting frame that is still shared";

    if (observer_) {
      observer_->OnFailedToProcessBuffer();
    }
  }

  if (!queue_.current_frame() ||
      !queue_.current_frame()->size().equals(frame_size_)) {
    std::unique_ptr<DesktopFrame> frame(new BasicDesktopFrame(
        DesktopSize(frame_size_.width(), frame_size_.height())));
    queue_.ReplaceCurrentFrame(SharedDesktopFrame::Wrap(std::move(frame)));
  }

  bool bufferProcessed = false;
  if (spa_buffer->datas[0].type == SPA_DATA_MemFd) {
    bufferProcessed =
        ProcessMemFDBuffer(buffer, *queue_.current_frame(), offset);
  } else if (spa_buffer->datas[0].type == SPA_DATA_DmaBuf) {
    bufferProcessed = ProcessDMABuffer(buffer, *queue_.current_frame(), offset);
  }

  if (!bufferProcessed) {
    if (observer_) {
      observer_->OnFailedToProcessBuffer();
    }
    return;
  }

  if (spa_video_format_.format == SPA_VIDEO_FORMAT_RGBx ||
      spa_video_format_.format == SPA_VIDEO_FORMAT_RGBA) {
    uint8_t* tmp_src = queue_.current_frame()->data();
    for (int i = 0; i < frame_size_.height(); ++i) {
      // If both sides decided to go with the RGBx format we need to convert
      // it to BGRx to match color format expected by WebRTC.
      ConvertRGBxToBGRx(tmp_src, queue_.current_frame()->stride());
      tmp_src += queue_.current_frame()->stride();
    }
  }

  if (observer_) {
    observer_->OnDesktopFrameChanged();
  }

  UpdateFrameUpdatedRegions(spa_buffer, *queue_.current_frame());
  queue_.current_frame()->set_may_contain_cursor(is_cursor_embedded_);

  if (callback_) {
    std::unique_ptr<SharedDesktopFrame> frame = queue_.current_frame()->Share();
    frame->set_capturer_id(DesktopCapturerId::kWaylandCapturerLinux);
    frame->set_capture_time_ms((rtc::TimeNanos() - capture_start_time_nanos) /
                               rtc::kNumNanosecsPerMillisec);
    NotifyCallbackOfNewFrame(std::move(frame));
  }
}

RTC_NO_SANITIZE("cfi-icall")
bool SharedScreenCastStreamPrivate::ProcessMemFDBuffer(
    pw_buffer* buffer,
    DesktopFrame& frame,
    const DesktopVector& offset) {
  spa_buffer* spa_buffer = buffer->buffer;
  ScopedBuf map;
  uint8_t* src = nullptr;

  map.initialize(
      static_cast<uint8_t*>(
          mmap(nullptr,
               spa_buffer->datas[0].maxsize + spa_buffer->datas[0].mapoffset,
               PROT_READ, MAP_PRIVATE, spa_buffer->datas[0].fd, 0)),
      spa_buffer->datas[0].maxsize + spa_buffer->datas[0].mapoffset,
      spa_buffer->datas[0].fd);

  if (!map) {
    RTC_LOG(LS_ERROR) << "Failed to mmap the memory: " << std::strerror(errno);
    return false;
  }

  src = SPA_MEMBER(map.get(), spa_buffer->datas[0].mapoffset, uint8_t);

  uint32_t buffer_stride = spa_buffer->datas[0].chunk->stride;
  uint32_t src_stride = buffer_stride;

  uint8_t* updated_src =
      src + (src_stride * offset.y()) + (kBytesPerPixel * offset.x());

  frame.CopyPixelsFrom(
      updated_src, (src_stride - (kBytesPerPixel * offset.x())),
      DesktopRect::MakeWH(frame.size().width(), frame.size().height()));

  return true;
}

RTC_NO_SANITIZE("cfi-icall")
bool SharedScreenCastStreamPrivate::ProcessDMABuffer(
    pw_buffer* buffer,
    DesktopFrame& frame,
    const DesktopVector& offset) {
  spa_buffer* spa_buffer = buffer->buffer;

  const uint n_planes = spa_buffer->n_datas;

  if (!n_planes) {
    return false;
  }

  std::vector<EglDmaBuf::PlaneData> plane_datas;
  for (uint32_t i = 0; i < n_planes; ++i) {
    EglDmaBuf::PlaneData data = {
        static_cast<int32_t>(spa_buffer->datas[i].fd),
        static_cast<uint32_t>(spa_buffer->datas[i].chunk->stride),
        static_cast<uint32_t>(spa_buffer->datas[i].chunk->offset)};
    plane_datas.push_back(data);
  }

  const bool imported = egl_dmabuf_->ImageFromDmaBuf(
      stream_size_, spa_video_format_.format, plane_datas, modifier_, offset,
      frame.size(), frame.data());
  if (!imported) {
    RTC_LOG(LS_ERROR) << "Dropping DMA-BUF modifier: " << modifier_
                      << " and trying to renegotiate stream parameters";

    if (pw_server_version_ >= kDropSingleModifierMinVersion) {
      modifiers_.erase(
          std::remove(modifiers_.begin(), modifiers_.end(), modifier_),
          modifiers_.end());
    } else {
      modifiers_.clear();
    }

    pw_loop_signal_event(pw_thread_loop_get_loop(pw_main_loop_), renegotiate_);
    return false;
  }

  return true;
}

void SharedScreenCastStreamPrivate::ConvertRGBxToBGRx(uint8_t* frame,
                                                      uint32_t size) {
  for (uint32_t i = 0; i < size; i += 4) {
    uint8_t tempR = frame[i];
    uint8_t tempB = frame[i + 2];
    frame[i] = tempB;
    frame[i + 2] = tempR;
  }
}

SharedScreenCastStream::SharedScreenCastStream()
    : private_(std::make_unique<SharedScreenCastStreamPrivate>()) {}

SharedScreenCastStream::~SharedScreenCastStream() {}

rtc::scoped_refptr<SharedScreenCastStream>
SharedScreenCastStream::CreateDefault() {
  // Explicit new, to access non-public constructor.
  return rtc::scoped_refptr<SharedScreenCastStream>(
      new SharedScreenCastStream());
}

bool SharedScreenCastStream::StartScreenCastStream(uint32_t stream_node_id) {
  return private_->StartScreenCastStream(stream_node_id, kInvalidPipeWireFd);
}

bool SharedScreenCastStream::StartScreenCastStream(
    uint32_t stream_node_id,
    int fd,
    uint32_t width,
    uint32_t height,
    bool is_cursor_embedded,
    DesktopCapturer::Callback* callback) {
  return private_->StartScreenCastStream(stream_node_id, fd, width, height,
                                         is_cursor_embedded, callback);
}

void SharedScreenCastStream::UpdateScreenCastStreamResolution(uint32_t width,
                                                              uint32_t height) {
  private_->UpdateScreenCastStreamResolution(width, height);
}

void SharedScreenCastStream::UpdateScreenCastStreamFrameRate(
    uint32_t frame_rate) {
  private_->UpdateScreenCastStreamFrameRate(frame_rate);
}

void SharedScreenCastStream::SetUseDamageRegion(bool use_damage_region) {
  private_->SetUseDamageRegion(use_damage_region);
}

void SharedScreenCastStream::SetObserver(
    SharedScreenCastStream::Observer* observer) {
  private_->SetObserver(observer);
}

void SharedScreenCastStream::StopScreenCastStream() {
  private_->StopScreenCastStream();
}

std::unique_ptr<SharedDesktopFrame> SharedScreenCastStream::CaptureFrame() {
  return private_->CaptureFrame();
}

std::unique_ptr<MouseCursor> SharedScreenCastStream::CaptureCursor() {
  return private_->CaptureCursor();
}

absl::optional<DesktopVector> SharedScreenCastStream::CaptureCursorPosition() {
  DesktopVector position = private_->CaptureCursorPosition();

  // Consider only (x >= 0 and y >= 0) a valid position
  if (position.x() < 0 || position.y() < 0) {
    return absl::nullopt;
  }

  return position;
}

}  // namespace webrtc
