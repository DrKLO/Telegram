/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/dxgi_texture_staging.h"

#include <comdef.h>
#include <dxgi.h>
#include <dxgi1_2.h>
#include <unknwn.h>

#include "modules/desktop_capture/win/desktop_capture_utils.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "system_wrappers/include/metrics.h"

using Microsoft::WRL::ComPtr;

namespace webrtc {

DxgiTextureStaging::DxgiTextureStaging(const D3dDevice& device)
    : device_(device) {}

DxgiTextureStaging::~DxgiTextureStaging() = default;

bool DxgiTextureStaging::InitializeStage(ID3D11Texture2D* texture) {
  RTC_DCHECK(texture);
  D3D11_TEXTURE2D_DESC desc = {0};
  texture->GetDesc(&desc);

  desc.ArraySize = 1;
  desc.BindFlags = 0;
  desc.CPUAccessFlags = D3D11_CPU_ACCESS_READ;
  desc.MipLevels = 1;
  desc.MiscFlags = 0;
  desc.SampleDesc.Count = 1;
  desc.SampleDesc.Quality = 0;
  desc.Usage = D3D11_USAGE_STAGING;
  if (stage_) {
    AssertStageAndSurfaceAreSameObject();
    D3D11_TEXTURE2D_DESC current_desc;
    stage_->GetDesc(&current_desc);
    const bool recreate_needed =
        (memcmp(&desc, &current_desc, sizeof(D3D11_TEXTURE2D_DESC)) != 0);
    RTC_HISTOGRAM_BOOLEAN("WebRTC.DesktopCapture.StagingTextureRecreate",
                          recreate_needed);
    if (!recreate_needed) {
      return true;
    }

    // The descriptions are not consistent, we need to create a new
    // ID3D11Texture2D instance.
    stage_.Reset();
    surface_.Reset();
  } else {
    RTC_DCHECK(!surface_);
  }

  _com_error error = device_.d3d_device()->CreateTexture2D(
      &desc, nullptr, stage_.GetAddressOf());
  if (error.Error() != S_OK || !stage_) {
    RTC_LOG(LS_ERROR) << "Failed to create a new ID3D11Texture2D as stage: "
                      << desktop_capture::utils::ComErrorToString(error);
    return false;
  }

  error = stage_.As(&surface_);
  if (error.Error() != S_OK || !surface_) {
    RTC_LOG(LS_ERROR) << "Failed to convert ID3D11Texture2D to IDXGISurface: "
                      << desktop_capture::utils::ComErrorToString(error);
    return false;
  }

  return true;
}

void DxgiTextureStaging::AssertStageAndSurfaceAreSameObject() {
  ComPtr<IUnknown> left;
  ComPtr<IUnknown> right;
  bool left_result = SUCCEEDED(stage_.As(&left));
  bool right_result = SUCCEEDED(surface_.As(&right));
  RTC_DCHECK(left_result);
  RTC_DCHECK(right_result);
  RTC_DCHECK(left.Get() == right.Get());
}

bool DxgiTextureStaging::CopyFromTexture(
    const DXGI_OUTDUPL_FRAME_INFO& frame_info,
    ID3D11Texture2D* texture) {
  RTC_DCHECK_GT(frame_info.AccumulatedFrames, 0);
  RTC_DCHECK(texture);

  // AcquireNextFrame returns a CPU inaccessible IDXGIResource, so we need to
  // copy it to a CPU accessible staging ID3D11Texture2D.
  if (!InitializeStage(texture)) {
    return false;
  }

  device_.context()->CopyResource(static_cast<ID3D11Resource*>(stage_.Get()),
                                  static_cast<ID3D11Resource*>(texture));

  *rect() = {0};
  _com_error error = surface_->Map(rect(), DXGI_MAP_READ);
  if (error.Error() != S_OK) {
    *rect() = {0};
    RTC_LOG(LS_ERROR) << "Failed to map the IDXGISurface to a bitmap: "
                      << desktop_capture::utils::ComErrorToString(error);
    return false;
  }

  return true;
}

bool DxgiTextureStaging::DoRelease() {
  _com_error error = surface_->Unmap();
  if (error.Error() != S_OK) {
    stage_.Reset();
    surface_.Reset();
  }
  // If using staging mode, we only need to recreate ID3D11Texture2D instance.
  // This will happen during next CopyFrom call. So this function always returns
  // true.
  return true;
}

}  // namespace webrtc
