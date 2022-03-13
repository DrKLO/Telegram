/*
 *  Copyright (c) 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/win/create_direct3d_device.h"

#include <libloaderapi.h>
#include <utility>

namespace {

FARPROC LoadD3D11Function(const char* function_name) {
  static HMODULE const handle =
      ::LoadLibraryExW(L"d3d11.dll", nullptr, LOAD_LIBRARY_SEARCH_SYSTEM32);
  return handle ? ::GetProcAddress(handle, function_name) : nullptr;
}

decltype(&::CreateDirect3D11DeviceFromDXGIDevice)
GetCreateDirect3D11DeviceFromDXGIDevice() {
  static decltype(&::CreateDirect3D11DeviceFromDXGIDevice) const function =
      reinterpret_cast<decltype(&::CreateDirect3D11DeviceFromDXGIDevice)>(
          LoadD3D11Function("CreateDirect3D11DeviceFromDXGIDevice"));
  return function;
}

}  // namespace

namespace webrtc {

bool ResolveCoreWinRTDirect3DDelayload() {
  return GetCreateDirect3D11DeviceFromDXGIDevice();
}

HRESULT CreateDirect3DDeviceFromDXGIDevice(
    IDXGIDevice* dxgi_device,
    ABI::Windows::Graphics::DirectX::Direct3D11::IDirect3DDevice**
        out_d3d11_device) {
  decltype(&::CreateDirect3D11DeviceFromDXGIDevice) create_d3d11_device_func =
      GetCreateDirect3D11DeviceFromDXGIDevice();
  if (!create_d3d11_device_func)
    return E_FAIL;

  Microsoft::WRL::ComPtr<IInspectable> inspectableSurface;
  HRESULT hr = create_d3d11_device_func(dxgi_device, &inspectableSurface);
  if (FAILED(hr))
    return hr;

  return inspectableSurface->QueryInterface(IID_PPV_ARGS(out_d3d11_device));
}

}  // namespace webrtc
