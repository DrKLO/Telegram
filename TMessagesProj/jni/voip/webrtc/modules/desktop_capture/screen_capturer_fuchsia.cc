/*
 *  Copyright 2022 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/screen_capturer_fuchsia.h"

#include <fuchsia/sysmem/cpp/fidl.h>
#include <fuchsia/ui/composition/cpp/fidl.h>
#include <fuchsia/ui/scenic/cpp/fidl.h>
#include <lib/sys/cpp/component_context.h>

#include <algorithm>
#include <cstdint>
#include <memory>
#include <string>
#include <utility>

#include "modules/desktop_capture/blank_detector_desktop_capturer_wrapper.h"
#include "modules/desktop_capture/desktop_capture_options.h"
#include "modules/desktop_capture/desktop_capture_types.h"
#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_frame.h"
#include "modules/desktop_capture/desktop_geometry.h"
#include "modules/desktop_capture/fallback_desktop_capturer_wrapper.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/divide_round.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

namespace {

static constexpr uint32_t kMinBufferCount = 2;
static constexpr uint32_t kFuchsiaBytesPerPixel = 4;
static constexpr DesktopCapturer::SourceId kFuchsiaScreenId = 1;
// 500 milliseconds
static constexpr zx::duration kEventDelay = zx::msec(500);
static constexpr fuchsia::sysmem::ColorSpaceType kSRGBColorSpace =
    fuchsia::sysmem::ColorSpaceType::SRGB;
static constexpr fuchsia::sysmem::PixelFormatType kBGRA32PixelFormatType =
    fuchsia::sysmem::PixelFormatType::BGRA32;

// Round |value| up to the closest multiple of |multiple|
size_t RoundUpToMultiple(size_t value, size_t multiple) {
  return DivideRoundUp(value, multiple) * multiple;
}

}  // namespace

std::unique_ptr<DesktopCapturer> DesktopCapturer::CreateRawScreenCapturer(
    const DesktopCaptureOptions& options) {
  std::unique_ptr<ScreenCapturerFuchsia> capturer(new ScreenCapturerFuchsia());
  return capturer;
}

ScreenCapturerFuchsia::ScreenCapturerFuchsia()
    : component_context_(sys::ComponentContext::Create()) {}

ScreenCapturerFuchsia::~ScreenCapturerFuchsia() {
  // unmap virtual memory mapped pointers
  uint32_t virt_mem_bytes =
      buffer_collection_info_.settings.buffer_settings.size_bytes;
  for (uint32_t buffer_index = 0;
       buffer_index < buffer_collection_info_.buffer_count; buffer_index++) {
    uintptr_t address =
        reinterpret_cast<uintptr_t>(virtual_memory_mapped_addrs_[buffer_index]);
    zx_status_t status = zx::vmar::root_self()->unmap(address, virt_mem_bytes);
    RTC_DCHECK(status == ZX_OK);
  }
}

void ScreenCapturerFuchsia::Start(Callback* callback) {
  RTC_DCHECK(!callback_);
  RTC_DCHECK(callback);
  callback_ = callback;

  fatal_error_ = false;

  SetupBuffers();
}

void ScreenCapturerFuchsia::CaptureFrame() {
  if (fatal_error_) {
    callback_->OnCaptureResult(Result::ERROR_PERMANENT, nullptr);
    return;
  }

  int64_t capture_start_time_nanos = rtc::TimeNanos();

  zx::event event;
  zx::event dup;
  zx_status_t status = zx::event::create(0, &event);
  if (status != ZX_OK) {
    RTC_LOG(LS_ERROR) << "Failed to create event: " << status;
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }
  event.duplicate(ZX_RIGHT_SAME_RIGHTS, &dup);

  fuchsia::ui::composition::GetNextFrameArgs next_frame_args;
  next_frame_args.set_event(std::move(dup));

  fuchsia::ui::composition::ScreenCapture_GetNextFrame_Result result;
  screen_capture_->GetNextFrame(std::move(next_frame_args), &result);
  if (result.is_err()) {
    RTC_LOG(LS_ERROR) << "fuchsia.ui.composition.GetNextFrame() failed: "
                      << result.err() << "\n";
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }

  status = event.wait_one(ZX_EVENT_SIGNALED, zx::deadline_after(kEventDelay),
                          nullptr);
  if (status != ZX_OK) {
    RTC_LOG(LS_ERROR) << "Timed out waiting for ScreenCapture to render frame: "
                      << status;
    callback_->OnCaptureResult(Result::ERROR_TEMPORARY, nullptr);
    return;
  }
  uint32_t buffer_index = result.response().buffer_id();

  // TODO(bugs.webrtc.org/14097): Use SharedMemoryDesktopFrame and
  // ScreenCaptureFrameQueue
  std::unique_ptr<BasicDesktopFrame> frame(
      new BasicDesktopFrame(DesktopSize(width_, height_)));

  uint32_t pixels_per_row = GetPixelsPerRow(
      buffer_collection_info_.settings.image_format_constraints);
  uint32_t stride = kFuchsiaBytesPerPixel * pixels_per_row;
  frame->CopyPixelsFrom(virtual_memory_mapped_addrs_[buffer_index], stride,
                        DesktopRect::MakeWH(width_, height_));
  // Mark the whole screen as having been updated.
  frame->mutable_updated_region()->SetRect(
      DesktopRect::MakeWH(width_, height_));

  fuchsia::ui::composition::ScreenCapture_ReleaseFrame_Result release_result;
  screen_capture_->ReleaseFrame(buffer_index, &release_result);
  if (release_result.is_err()) {
    RTC_LOG(LS_ERROR) << "fuchsia.ui.composition.ReleaseFrame() failed: "
                      << release_result.err();
  }

  int capture_time_ms = (rtc::TimeNanos() - capture_start_time_nanos) /
                        rtc::kNumNanosecsPerMillisec;
  frame->set_capture_time_ms(capture_time_ms);
  callback_->OnCaptureResult(Result::SUCCESS, std::move(frame));
}

bool ScreenCapturerFuchsia::GetSourceList(SourceList* screens) {
  RTC_DCHECK(screens->size() == 0);
  // Fuchsia only supports single monitor display at this point
  screens->push_back({kFuchsiaScreenId, std::string("Fuchsia monitor")});
  return true;
}

bool ScreenCapturerFuchsia::SelectSource(SourceId id) {
  if (id == kFuchsiaScreenId || id == kFullDesktopScreenId) {
    return true;
  }
  return false;
}

fuchsia::sysmem::BufferCollectionConstraints
ScreenCapturerFuchsia::GetBufferConstraints() {
  fuchsia::sysmem::BufferCollectionConstraints constraints;
  constraints.usage.cpu =
      fuchsia::sysmem::cpuUsageRead | fuchsia::sysmem::cpuUsageWrite;
  constraints.min_buffer_count = kMinBufferCount;

  constraints.has_buffer_memory_constraints = true;
  constraints.buffer_memory_constraints.ram_domain_supported = true;
  constraints.buffer_memory_constraints.cpu_domain_supported = true;

  constraints.image_format_constraints_count = 1;
  fuchsia::sysmem::ImageFormatConstraints& image_constraints =
      constraints.image_format_constraints[0];
  image_constraints.color_spaces_count = 1;
  image_constraints.color_space[0] =
      fuchsia::sysmem::ColorSpace{.type = kSRGBColorSpace};
  image_constraints.pixel_format.type = kBGRA32PixelFormatType;
  image_constraints.pixel_format.has_format_modifier = true;
  image_constraints.pixel_format.format_modifier.value =
      fuchsia::sysmem::FORMAT_MODIFIER_LINEAR;

  image_constraints.required_min_coded_width = width_;
  image_constraints.required_min_coded_height = height_;
  image_constraints.required_max_coded_width = width_;
  image_constraints.required_max_coded_height = height_;

  image_constraints.bytes_per_row_divisor = kFuchsiaBytesPerPixel;

  return constraints;
}

void ScreenCapturerFuchsia::SetupBuffers() {
  fuchsia::ui::scenic::ScenicSyncPtr scenic;
  zx_status_t status = component_context_->svc()->Connect(scenic.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to connect to Scenic: " << status;
    return;
  }

  fuchsia::ui::gfx::DisplayInfo display_info;
  status = scenic->GetDisplayInfo(&display_info);
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to connect to get display dimensions: "
                      << status;
    return;
  }
  width_ = display_info.width_in_px;
  height_ = display_info.height_in_px;

  status = component_context_->svc()->Connect(sysmem_allocator_.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to connect to Sysmem Allocator: " << status;
    return;
  }

  fuchsia::sysmem::BufferCollectionTokenSyncPtr sysmem_token;
  status =
      sysmem_allocator_->AllocateSharedCollection(sysmem_token.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "fuchsia.sysmem.Allocator.AllocateSharedCollection() failed: "
        << status;
    return;
  }

  fuchsia::sysmem::BufferCollectionTokenSyncPtr flatland_token;
  status = sysmem_token->Duplicate(ZX_RIGHT_SAME_RIGHTS,
                                   flatland_token.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "fuchsia.sysmem.BufferCollectionToken.Duplicate() failed: "
        << status;
    return;
  }

  status = sysmem_token->Sync();
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "fuchsia.sysmem.BufferCollectionToken.Sync() failed: "
                      << status;
    return;
  }

  status = sysmem_allocator_->BindSharedCollection(std::move(sysmem_token),
                                                   collection_.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "fuchsia.sysmem.Allocator.BindSharedCollection() failed: " << status;
    return;
  }

  status = collection_->SetConstraints(/*has_constraints=*/true,
                                       GetBufferConstraints());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "fuchsia.sysmem.BufferCollection.SetConstraints() failed: "
        << status;
    return;
  }

  fuchsia::ui::composition::BufferCollectionImportToken import_token;
  fuchsia::ui::composition::BufferCollectionExportToken export_token;
  status = zx::eventpair::create(0, &export_token.value, &import_token.value);
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "Failed to create BufferCollection import and export tokens: "
        << status;
    return;
  }

  status = component_context_->svc()->Connect(flatland_allocator_.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to connect to Flatland Allocator: " << status;
    return;
  }

  fuchsia::ui::composition::RegisterBufferCollectionArgs buffer_collection_args;
  buffer_collection_args.set_export_token(std::move(export_token));
  buffer_collection_args.set_buffer_collection_token(std::move(flatland_token));
  buffer_collection_args.set_usage(
      fuchsia::ui::composition::RegisterBufferCollectionUsage::SCREENSHOT);

  fuchsia::ui::composition::Allocator_RegisterBufferCollection_Result
      buffer_collection_result;
  flatland_allocator_->RegisterBufferCollection(
      std::move(buffer_collection_args), &buffer_collection_result);
  if (buffer_collection_result.is_err()) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "fuchsia.ui.composition.Allocator."
                         "RegisterBufferCollection() failed.";
    return;
  }

  zx_status_t allocation_status;
  status = collection_->WaitForBuffersAllocated(&allocation_status,
                                                &buffer_collection_info_);
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to wait for buffer collection info: "
                      << status;
    return;
  }
  if (allocation_status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to allocate buffer collection: " << status;
    return;
  }
  status = collection_->Close();
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to close buffer collection token: " << status;
    return;
  }

  status = component_context_->svc()->Connect(screen_capture_.NewRequest());
  if (status != ZX_OK) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR) << "Failed to connect to Screen Capture: " << status;
    return;
  }

  // Configure buffers in ScreenCapture client.
  fuchsia::ui::composition::ScreenCaptureConfig configure_args;
  configure_args.set_import_token(std::move(import_token));
  configure_args.set_buffer_count(buffer_collection_info_.buffer_count);
  configure_args.set_size({width_, height_});

  fuchsia::ui::composition::ScreenCapture_Configure_Result configure_result;
  screen_capture_->Configure(std::move(configure_args), &configure_result);
  if (configure_result.is_err()) {
    fatal_error_ = true;
    RTC_LOG(LS_ERROR)
        << "fuchsia.ui.composition.ScreenCapture.Configure() failed: "
        << configure_result.err();
    return;
  }

  // We have a collection of virtual memory objects which the ScreenCapture
  // client will write the frame data to when requested. We map each of these
  // onto a pointer stored in virtual_memory_mapped_addrs_ which we can use to
  // access this data.
  uint32_t virt_mem_bytes =
      buffer_collection_info_.settings.buffer_settings.size_bytes;
  RTC_DCHECK(virt_mem_bytes > 0);
  for (uint32_t buffer_index = 0;
       buffer_index < buffer_collection_info_.buffer_count; buffer_index++) {
    const zx::vmo& virt_mem = buffer_collection_info_.buffers[buffer_index].vmo;
    virtual_memory_mapped_addrs_[buffer_index] = nullptr;
    auto status = zx::vmar::root_self()->map(
        ZX_VM_PERM_READ, /*vmar_offset*/ 0, virt_mem,
        /*vmo_offset*/ 0, virt_mem_bytes,
        reinterpret_cast<uintptr_t*>(
            &virtual_memory_mapped_addrs_[buffer_index]));
    if (status != ZX_OK) {
      fatal_error_ = true;
      RTC_LOG(LS_ERROR) << "Failed to map virtual memory: " << status;
      return;
    }
  }
}

uint32_t ScreenCapturerFuchsia::GetPixelsPerRow(
    const fuchsia::sysmem::ImageFormatConstraints& constraints) {
  uint32_t stride = RoundUpToMultiple(
      std::max(constraints.min_bytes_per_row, width_ * kFuchsiaBytesPerPixel),
      constraints.bytes_per_row_divisor);
  uint32_t pixels_per_row = stride / kFuchsiaBytesPerPixel;

  return pixels_per_row;
}

}  // namespace webrtc
