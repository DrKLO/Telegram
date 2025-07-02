/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/windows/video_capture_ds.h"

#include <dvdmedia.h>  // VIDEOINFOHEADER2

#include "modules/video_capture/video_capture_config.h"
#include "modules/video_capture/windows/help_functions_ds.h"
#include "modules/video_capture/windows/sink_filter_ds.h"
#include "rtc_base/logging.h"

namespace webrtc {
namespace videocapturemodule {
VideoCaptureDS::VideoCaptureDS()
    : _captureFilter(NULL),
      _graphBuilder(NULL),
      _mediaControl(NULL),
      _inputSendPin(NULL),
      _outputCapturePin(NULL),
      _dvFilter(NULL),
      _inputDvPin(NULL),
      _outputDvPin(NULL) {}

VideoCaptureDS::~VideoCaptureDS() {
  if (_mediaControl) {
    _mediaControl->Stop();
  }
  if (_graphBuilder) {
    if (sink_filter_)
      _graphBuilder->RemoveFilter(sink_filter_.get());
    if (_captureFilter)
      _graphBuilder->RemoveFilter(_captureFilter);
    if (_dvFilter)
      _graphBuilder->RemoveFilter(_dvFilter);
  }
  RELEASE_AND_CLEAR(_inputSendPin);
  RELEASE_AND_CLEAR(_outputCapturePin);

  RELEASE_AND_CLEAR(_captureFilter);  // release the capture device
  RELEASE_AND_CLEAR(_dvFilter);

  RELEASE_AND_CLEAR(_mediaControl);

  RELEASE_AND_CLEAR(_inputDvPin);
  RELEASE_AND_CLEAR(_outputDvPin);

  RELEASE_AND_CLEAR(_graphBuilder);
}

int32_t VideoCaptureDS::Init(const char* deviceUniqueIdUTF8) {
  RTC_DCHECK_RUN_ON(&api_checker_);

  const int32_t nameLength = (int32_t)strlen((char*)deviceUniqueIdUTF8);
  if (nameLength >= kVideoCaptureUniqueNameLength)
    return -1;

  // Store the device name
  _deviceUniqueId = new (std::nothrow) char[nameLength + 1];
  memcpy(_deviceUniqueId, deviceUniqueIdUTF8, nameLength + 1);

  if (_dsInfo.Init() != 0)
    return -1;

  _captureFilter = _dsInfo.GetDeviceFilter(deviceUniqueIdUTF8);
  if (!_captureFilter) {
    RTC_LOG(LS_INFO) << "Failed to create capture filter.";
    return -1;
  }

  // Get the interface for DirectShow's GraphBuilder
  HRESULT hr = CoCreateInstance(CLSID_FilterGraph, NULL, CLSCTX_INPROC_SERVER,
                                IID_IGraphBuilder, (void**)&_graphBuilder);
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to create graph builder.";
    return -1;
  }

  hr = _graphBuilder->QueryInterface(IID_IMediaControl, (void**)&_mediaControl);
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to create media control builder.";
    return -1;
  }
  hr = _graphBuilder->AddFilter(_captureFilter, CAPTURE_FILTER_NAME);
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to add the capture device to the graph.";
    return -1;
  }

  _outputCapturePin = GetOutputPin(_captureFilter, PIN_CATEGORY_CAPTURE);
  if (!_outputCapturePin) {
    RTC_LOG(LS_INFO) << "Failed to get output capture pin";
    return -1;
  }

  // Create the sink filte used for receiving Captured frames.
  sink_filter_ = new ComRefCount<CaptureSinkFilter>(this);

  hr = _graphBuilder->AddFilter(sink_filter_.get(), SINK_FILTER_NAME);
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to add the send filter to the graph.";
    return -1;
  }

  _inputSendPin = GetInputPin(sink_filter_.get());
  if (!_inputSendPin) {
    RTC_LOG(LS_INFO) << "Failed to get input send pin";
    return -1;
  }

  if (SetCameraOutput(_requestedCapability) != 0) {
    return -1;
  }
  RTC_LOG(LS_INFO) << "Capture device '" << deviceUniqueIdUTF8
                   << "' initialized.";
  return 0;
}

int32_t VideoCaptureDS::StartCapture(const VideoCaptureCapability& capability) {
  RTC_DCHECK_RUN_ON(&api_checker_);

  if (capability != _requestedCapability) {
    DisconnectGraph();

    if (SetCameraOutput(capability) != 0) {
      return -1;
    }
  }
  HRESULT hr = _mediaControl->Pause();
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO)
        << "Failed to Pause the Capture device. Is it already occupied? " << hr;
    return -1;
  }
  hr = _mediaControl->Run();
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to start the Capture device.";
    return -1;
  }
  return 0;
}

int32_t VideoCaptureDS::StopCapture() {
  RTC_DCHECK_RUN_ON(&api_checker_);

  HRESULT hr = _mediaControl->StopWhenReady();
  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to stop the capture graph. " << hr;
    return -1;
  }
  return 0;
}

bool VideoCaptureDS::CaptureStarted() {
  RTC_DCHECK_RUN_ON(&api_checker_);

  OAFilterState state = 0;
  HRESULT hr = _mediaControl->GetState(1000, &state);
  if (hr != S_OK && hr != VFW_S_CANT_CUE) {
    RTC_LOG(LS_INFO) << "Failed to get the CaptureStarted status";
  }
  RTC_LOG(LS_INFO) << "CaptureStarted " << state;
  return state == State_Running;
}

int32_t VideoCaptureDS::CaptureSettings(VideoCaptureCapability& settings) {
  RTC_DCHECK_RUN_ON(&api_checker_);
  settings = _requestedCapability;
  return 0;
}

int32_t VideoCaptureDS::SetCameraOutput(
    const VideoCaptureCapability& requestedCapability) {
  RTC_DCHECK_RUN_ON(&api_checker_);

  // Get the best matching capability
  VideoCaptureCapability capability;
  int32_t capabilityIndex;

  // Store the new requested size
  _requestedCapability = requestedCapability;
  // Match the requested capability with the supported.
  if ((capabilityIndex = _dsInfo.GetBestMatchedCapability(
           _deviceUniqueId, _requestedCapability, capability)) < 0) {
    return -1;
  }
  // Reduce the frame rate if possible.
  if (capability.maxFPS > requestedCapability.maxFPS) {
    capability.maxFPS = requestedCapability.maxFPS;
  } else if (capability.maxFPS <= 0) {
    capability.maxFPS = 30;
  }

  // Convert it to the windows capability index since they are not nexessary
  // the same
  VideoCaptureCapabilityWindows windowsCapability;
  if (_dsInfo.GetWindowsCapability(capabilityIndex, windowsCapability) != 0) {
    return -1;
  }

  IAMStreamConfig* streamConfig = NULL;
  AM_MEDIA_TYPE* pmt = NULL;
  VIDEO_STREAM_CONFIG_CAPS caps;

  HRESULT hr = _outputCapturePin->QueryInterface(IID_IAMStreamConfig,
                                                 (void**)&streamConfig);
  if (hr) {
    RTC_LOG(LS_INFO) << "Can't get the Capture format settings.";
    return -1;
  }

  // Get the windows capability from the capture device
  bool isDVCamera = false;
  hr = streamConfig->GetStreamCaps(windowsCapability.directShowCapabilityIndex,
                                   &pmt, reinterpret_cast<BYTE*>(&caps));
  if (hr == S_OK) {
    if (pmt->formattype == FORMAT_VideoInfo2) {
      VIDEOINFOHEADER2* h = reinterpret_cast<VIDEOINFOHEADER2*>(pmt->pbFormat);
      if (capability.maxFPS > 0 && windowsCapability.supportFrameRateControl) {
        h->AvgTimePerFrame = REFERENCE_TIME(10000000.0 / capability.maxFPS);
      }
    } else {
      VIDEOINFOHEADER* h = reinterpret_cast<VIDEOINFOHEADER*>(pmt->pbFormat);
      if (capability.maxFPS > 0 && windowsCapability.supportFrameRateControl) {
        h->AvgTimePerFrame = REFERENCE_TIME(10000000.0 / capability.maxFPS);
      }
    }

    // Set the sink filter to request this capability
    sink_filter_->SetRequestedCapability(capability);
    // Order the capture device to use this capability
    hr += streamConfig->SetFormat(pmt);

    // Check if this is a DV camera and we need to add MS DV Filter
    if (pmt->subtype == MEDIASUBTYPE_dvsl ||
        pmt->subtype == MEDIASUBTYPE_dvsd ||
        pmt->subtype == MEDIASUBTYPE_dvhd) {
      isDVCamera = true;  // This is a DV camera. Use MS DV filter
    }

    FreeMediaType(pmt);
    pmt = NULL;
  }
  RELEASE_AND_CLEAR(streamConfig);

  if (FAILED(hr)) {
    RTC_LOG(LS_INFO) << "Failed to set capture device output format";
    return -1;
  }

  if (isDVCamera) {
    hr = ConnectDVCamera();
  } else {
    hr = _graphBuilder->ConnectDirect(_outputCapturePin, _inputSendPin, NULL);
  }
  if (hr != S_OK) {
    RTC_LOG(LS_INFO) << "Failed to connect the Capture graph " << hr;
    return -1;
  }
  return 0;
}

int32_t VideoCaptureDS::DisconnectGraph() {
  RTC_DCHECK_RUN_ON(&api_checker_);

  HRESULT hr = _mediaControl->Stop();
  hr += _graphBuilder->Disconnect(_outputCapturePin);
  hr += _graphBuilder->Disconnect(_inputSendPin);

  // if the DV camera filter exist
  if (_dvFilter) {
    _graphBuilder->Disconnect(_inputDvPin);
    _graphBuilder->Disconnect(_outputDvPin);
  }
  if (hr != S_OK) {
    RTC_LOG(LS_ERROR)
        << "Failed to Stop the Capture device for reconfiguration " << hr;
    return -1;
  }
  return 0;
}

HRESULT VideoCaptureDS::ConnectDVCamera() {
  RTC_DCHECK_RUN_ON(&api_checker_);

  HRESULT hr = S_OK;

  if (!_dvFilter) {
    hr = CoCreateInstance(CLSID_DVVideoCodec, NULL, CLSCTX_INPROC,
                          IID_IBaseFilter, (void**)&_dvFilter);
    if (hr != S_OK) {
      RTC_LOG(LS_INFO) << "Failed to create the dv decoder: " << hr;
      return hr;
    }
    hr = _graphBuilder->AddFilter(_dvFilter, L"VideoDecoderDV");
    if (hr != S_OK) {
      RTC_LOG(LS_INFO) << "Failed to add the dv decoder to the graph: " << hr;
      return hr;
    }
    _inputDvPin = GetInputPin(_dvFilter);
    if (_inputDvPin == NULL) {
      RTC_LOG(LS_INFO) << "Failed to get input pin from DV decoder";
      return -1;
    }
    _outputDvPin = GetOutputPin(_dvFilter, GUID_NULL);
    if (_outputDvPin == NULL) {
      RTC_LOG(LS_INFO) << "Failed to get output pin from DV decoder";
      return -1;
    }
  }
  hr = _graphBuilder->ConnectDirect(_outputCapturePin, _inputDvPin, NULL);
  if (hr != S_OK) {
    RTC_LOG(LS_INFO) << "Failed to connect capture device to the dv devoder: "
                     << hr;
    return hr;
  }

  hr = _graphBuilder->ConnectDirect(_outputDvPin, _inputSendPin, NULL);
  if (hr != S_OK) {
    if (hr == HRESULT_FROM_WIN32(ERROR_TOO_MANY_OPEN_FILES)) {
      RTC_LOG(LS_INFO) << "Failed to connect the capture device, busy";
    } else {
      RTC_LOG(LS_INFO) << "Failed to connect capture device to the send graph: "
                       << hr;
    }
  }
  return hr;
}
}  // namespace videocapturemodule
}  // namespace webrtc
