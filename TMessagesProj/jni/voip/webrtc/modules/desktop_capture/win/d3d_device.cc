/*
 *  Copyright (c) 2016 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/desktop_capture/win/d3d_device.h"

#include <utility>

#include "modules/desktop_capture/win/desktop_capture_utils.h"
#include "rtc_base/logging.h"

namespace webrtc {

using Microsoft::WRL::ComPtr;

D3dDevice::D3dDevice() = default;
D3dDevice::D3dDevice(const D3dDevice& other) = default;
D3dDevice::D3dDevice(D3dDevice&& other) = default;
D3dDevice::~D3dDevice() = default;

bool D3dDevice::Initialize(const ComPtr<IDXGIAdapter>& adapter) {
  dxgi_adapter_ = adapter;
  if (!dxgi_adapter_) {
    RTC_LOG(LS_WARNING) << "An empty IDXGIAdapter instance has been received.";
    return false;
  }

  D3D_FEATURE_LEVEL feature_level;
  // Default feature levels contain D3D 9.1 through D3D 11.0.
  _com_error error = D3D11CreateDevice(
      adapter.Get(), D3D_DRIVER_TYPE_UNKNOWN, nullptr,
      D3D11_CREATE_DEVICE_BGRA_SUPPORT | D3D11_CREATE_DEVICE_SINGLETHREADED,
      nullptr, 0, D3D11_SDK_VERSION, d3d_device_.GetAddressOf(), &feature_level,
      context_.GetAddressOf());
  if (error.Error() != S_OK || !d3d_device_ || !context_) {
    RTC_LOG(LS_WARNING) << "D3D11CreateDevice returned: "
                        << desktop_capture::utils::ComErrorToString(error);
    return false;
  }

  if (feature_level < D3D_FEATURE_LEVEL_11_0) {
    RTC_LOG(LS_WARNING)
        << "D3D11CreateDevice returned an instance without DirectX 11 support, "
        << "level " << feature_level << ". Following initialization may fail.";
    // D3D_FEATURE_LEVEL_11_0 is not officially documented on MSDN to be a
    // requirement of Dxgi duplicator APIs.
  }

  error = d3d_device_.As(&dxgi_device_);
  if (error.Error() != S_OK || !dxgi_device_) {
    RTC_LOG(LS_WARNING)
        << "ID3D11Device is not an implementation of IDXGIDevice, "
        << "this usually means the system does not support DirectX "
        << "11. Error received: "
        << desktop_capture::utils::ComErrorToString(error);
    return false;
  }

  return true;
}

// static
std::vector<D3dDevice> D3dDevice::EnumDevices() {
  ComPtr<IDXGIFactory1> factory;
  _com_error error =
      CreateDXGIFactory1(__uuidof(IDXGIFactory1),
                         reinterpret_cast<void**>(factory.GetAddressOf()));
  if (error.Error() != S_OK || !factory) {
    RTC_LOG(LS_WARNING) << "Cannot create IDXGIFactory1: "
                        << desktop_capture::utils::ComErrorToString(error);
    return std::vector<D3dDevice>();
  }

  std::vector<D3dDevice> result;
  for (int i = 0;; i++) {
    ComPtr<IDXGIAdapter> adapter;
    error = factory->EnumAdapters(i, adapter.GetAddressOf());
    if (error.Error() == S_OK) {
      D3dDevice device;
      if (device.Initialize(adapter)) {
        result.push_back(std::move(device));
      }
    } else if (error.Error() == DXGI_ERROR_NOT_FOUND) {
      break;
    } else {
      RTC_LOG(LS_WARNING)
          << "IDXGIFactory1::EnumAdapters returned an unexpected error: "
          << desktop_capture::utils::ComErrorToString(error);
    }
  }
  return result;
}

}  // namespace webrtc
