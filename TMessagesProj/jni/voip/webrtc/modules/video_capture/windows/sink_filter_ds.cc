/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/video_capture/windows/sink_filter_ds.h"

#include <dvdmedia.h>  // VIDEOINFOHEADER2
#include <initguid.h>

#include <algorithm>
#include <list>

#include "rtc_base/arraysize.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/platform_thread.h"
#include "rtc_base/string_utils.h"

DEFINE_GUID(CLSID_SINKFILTER,
            0x88cdbbdc,
            0xa73b,
            0x4afa,
            0xac,
            0xbf,
            0x15,
            0xd5,
            0xe2,
            0xce,
            0x12,
            0xc3);

namespace webrtc {
namespace videocapturemodule {
namespace {

// Simple enumeration implementation that enumerates over a single pin :-/
class EnumPins : public IEnumPins {
 public:
  EnumPins(IPin* pin) : pin_(pin) {}

 protected:
  virtual ~EnumPins() {}

 private:
  STDMETHOD(QueryInterface)(REFIID riid, void** ppv) override {
    if (riid == IID_IUnknown || riid == IID_IEnumPins) {
      *ppv = static_cast<IEnumPins*>(this);
      AddRef();
      return S_OK;
    }
    return E_NOINTERFACE;
  }

  STDMETHOD(Clone)(IEnumPins** pins) {
    RTC_DCHECK_NOTREACHED();
    return E_NOTIMPL;
  }

  STDMETHOD(Next)(ULONG count, IPin** pins, ULONG* fetched) {
    RTC_DCHECK(count > 0);
    RTC_DCHECK(pins);
    // fetched may be NULL.

    if (pos_ > 0) {
      if (fetched)
        *fetched = 0;
      return S_FALSE;
    }

    ++pos_;
    pins[0] = pin_.get();
    pins[0]->AddRef();
    if (fetched)
      *fetched = 1;

    return count == 1 ? S_OK : S_FALSE;
  }

  STDMETHOD(Skip)(ULONG count) {
    RTC_DCHECK_NOTREACHED();
    return E_NOTIMPL;
  }

  STDMETHOD(Reset)() {
    pos_ = 0;
    return S_OK;
  }

  rtc::scoped_refptr<IPin> pin_;
  int pos_ = 0;
};

bool IsMediaTypePartialMatch(const AM_MEDIA_TYPE& a, const AM_MEDIA_TYPE& b) {
  if (b.majortype != GUID_NULL && a.majortype != b.majortype)
    return false;

  if (b.subtype != GUID_NULL && a.subtype != b.subtype)
    return false;

  if (b.formattype != GUID_NULL) {
    // if the format block is specified then it must match exactly
    if (a.formattype != b.formattype)
      return false;

    if (a.cbFormat != b.cbFormat)
      return false;

    if (a.cbFormat != 0 && memcmp(a.pbFormat, b.pbFormat, a.cbFormat) != 0)
      return false;
  }

  return true;
}

bool IsMediaTypeFullySpecified(const AM_MEDIA_TYPE& type) {
  return type.majortype != GUID_NULL && type.formattype != GUID_NULL;
}

BYTE* AllocMediaTypeFormatBuffer(AM_MEDIA_TYPE* media_type, ULONG length) {
  RTC_DCHECK(length);
  if (media_type->cbFormat == length)
    return media_type->pbFormat;

  BYTE* buffer = static_cast<BYTE*>(CoTaskMemAlloc(length));
  if (!buffer)
    return nullptr;

  if (media_type->pbFormat) {
    RTC_DCHECK(media_type->cbFormat);
    CoTaskMemFree(media_type->pbFormat);
    media_type->pbFormat = nullptr;
  }

  media_type->cbFormat = length;
  media_type->pbFormat = buffer;
  return buffer;
}

void GetSampleProperties(IMediaSample* sample, AM_SAMPLE2_PROPERTIES* props) {
  rtc::scoped_refptr<IMediaSample2> sample2;
  if (SUCCEEDED(GetComInterface(sample, &sample2))) {
    sample2->GetProperties(sizeof(*props), reinterpret_cast<BYTE*>(props));
    return;
  }

  //  Get the properties the hard way.
  props->cbData = sizeof(*props);
  props->dwTypeSpecificFlags = 0;
  props->dwStreamId = AM_STREAM_MEDIA;
  props->dwSampleFlags = 0;

  if (sample->IsDiscontinuity() == S_OK)
    props->dwSampleFlags |= AM_SAMPLE_DATADISCONTINUITY;

  if (sample->IsPreroll() == S_OK)
    props->dwSampleFlags |= AM_SAMPLE_PREROLL;

  if (sample->IsSyncPoint() == S_OK)
    props->dwSampleFlags |= AM_SAMPLE_SPLICEPOINT;

  if (SUCCEEDED(sample->GetTime(&props->tStart, &props->tStop)))
    props->dwSampleFlags |= AM_SAMPLE_TIMEVALID | AM_SAMPLE_STOPVALID;

  if (sample->GetMediaType(&props->pMediaType) == S_OK)
    props->dwSampleFlags |= AM_SAMPLE_TYPECHANGED;

  sample->GetPointer(&props->pbBuffer);
  props->lActual = sample->GetActualDataLength();
  props->cbBuffer = sample->GetSize();
}

// Returns true if the media type is supported, false otherwise.
// For supported types, the `capability` will be populated accordingly.
bool TranslateMediaTypeToVideoCaptureCapability(
    const AM_MEDIA_TYPE* media_type,
    VideoCaptureCapability* capability) {
  RTC_DCHECK(capability);
  if (!media_type || media_type->majortype != MEDIATYPE_Video ||
      !media_type->pbFormat) {
    return false;
  }

  const BITMAPINFOHEADER* bih = nullptr;
  if (media_type->formattype == FORMAT_VideoInfo) {
    bih = &reinterpret_cast<VIDEOINFOHEADER*>(media_type->pbFormat)->bmiHeader;
  } else if (media_type->formattype != FORMAT_VideoInfo2) {
    bih = &reinterpret_cast<VIDEOINFOHEADER2*>(media_type->pbFormat)->bmiHeader;
  } else {
    return false;
  }

  RTC_LOG(LS_INFO) << "TranslateMediaTypeToVideoCaptureCapability width:"
                   << bih->biWidth << " height:" << bih->biHeight
                   << " Compression:0x" << rtc::ToHex(bih->biCompression);

  const GUID& sub_type = media_type->subtype;
  if (sub_type == MEDIASUBTYPE_MJPG &&
      bih->biCompression == MAKEFOURCC('M', 'J', 'P', 'G')) {
    capability->videoType = VideoType::kMJPEG;
  } else if (sub_type == MEDIASUBTYPE_I420 &&
             bih->biCompression == MAKEFOURCC('I', '4', '2', '0')) {
    capability->videoType = VideoType::kI420;
  } else if (sub_type == MEDIASUBTYPE_YUY2 &&
             bih->biCompression == MAKEFOURCC('Y', 'U', 'Y', '2')) {
    capability->videoType = VideoType::kYUY2;
  } else if (sub_type == MEDIASUBTYPE_UYVY &&
             bih->biCompression == MAKEFOURCC('U', 'Y', 'V', 'Y')) {
    capability->videoType = VideoType::kUYVY;
  } else if (sub_type == MEDIASUBTYPE_HDYC) {
    capability->videoType = VideoType::kUYVY;
  } else if (sub_type == MEDIASUBTYPE_RGB24 && bih->biCompression == BI_RGB) {
    capability->videoType = VideoType::kRGB24;
  } else {
    return false;
  }

  // Store the incoming width and height
  capability->width = bih->biWidth;

  // Store the incoming height,
  // for RGB24 we assume the frame to be upside down
  if (sub_type == MEDIASUBTYPE_RGB24 && bih->biHeight > 0) {
    capability->height = -(bih->biHeight);
  } else {
    capability->height = abs(bih->biHeight);
  }

  return true;
}

class MediaTypesEnum : public IEnumMediaTypes {
 public:
  MediaTypesEnum(const VideoCaptureCapability& capability)
      : capability_(capability),
        format_preference_order_(
            {// Default preferences, sorted by cost-to-convert-to-i420.
             VideoType::kI420, VideoType::kYUY2, VideoType::kRGB24,
             VideoType::kUYVY, VideoType::kMJPEG}) {
    // Use the preferred video type, if supported.
    auto it = std::find(format_preference_order_.begin(),
                        format_preference_order_.end(), capability_.videoType);
    if (it != format_preference_order_.end()) {
      RTC_LOG(LS_INFO) << "Selected video type: " << *it;
      // Move it to the front of the list, if it isn't already there.
      if (it != format_preference_order_.begin()) {
        format_preference_order_.splice(format_preference_order_.begin(),
                                        format_preference_order_, it,
                                        std::next(it));
      }
    } else {
      RTC_LOG(LS_WARNING) << "Unsupported video type: "
                          << rtc::ToString(
                                 static_cast<int>(capability_.videoType))
                          << ", using default preference list.";
    }
  }

 protected:
  virtual ~MediaTypesEnum() {}

 private:
  STDMETHOD(QueryInterface)(REFIID riid, void** ppv) override {
    if (riid == IID_IUnknown || riid == IID_IEnumMediaTypes) {
      *ppv = static_cast<IEnumMediaTypes*>(this);
      AddRef();
      return S_OK;
    }
    return E_NOINTERFACE;
  }

  // IEnumMediaTypes
  STDMETHOD(Clone)(IEnumMediaTypes** pins) {
    RTC_DCHECK_NOTREACHED();
    return E_NOTIMPL;
  }

  STDMETHOD(Next)(ULONG count, AM_MEDIA_TYPE** types, ULONG* fetched) {
    RTC_DCHECK(count > 0);
    RTC_DCHECK(types);
    // fetched may be NULL.
    if (fetched)
      *fetched = 0;

    for (ULONG i = 0;
         i < count && pos_ < static_cast<int>(format_preference_order_.size());
         ++i) {
      AM_MEDIA_TYPE* media_type = reinterpret_cast<AM_MEDIA_TYPE*>(
          CoTaskMemAlloc(sizeof(AM_MEDIA_TYPE)));
      ZeroMemory(media_type, sizeof(*media_type));
      types[i] = media_type;
      VIDEOINFOHEADER* vih = reinterpret_cast<VIDEOINFOHEADER*>(
          AllocMediaTypeFormatBuffer(media_type, sizeof(VIDEOINFOHEADER)));
      ZeroMemory(vih, sizeof(*vih));
      vih->bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
      vih->bmiHeader.biPlanes = 1;
      vih->bmiHeader.biClrImportant = 0;
      vih->bmiHeader.biClrUsed = 0;
      if (capability_.maxFPS != 0)
        vih->AvgTimePerFrame = 10000000 / capability_.maxFPS;

      SetRectEmpty(&vih->rcSource);  // we want the whole image area rendered.
      SetRectEmpty(&vih->rcTarget);  // no particular destination rectangle

      media_type->majortype = MEDIATYPE_Video;
      media_type->formattype = FORMAT_VideoInfo;
      media_type->bTemporalCompression = FALSE;

      // Set format information.
      auto format_it = std::next(format_preference_order_.begin(), pos_++);
      SetMediaInfoFromVideoType(*format_it, &vih->bmiHeader, media_type);

      vih->bmiHeader.biWidth = capability_.width;
      vih->bmiHeader.biHeight = capability_.height;
      vih->bmiHeader.biSizeImage = ((vih->bmiHeader.biBitCount / 4) *
                                    capability_.height * capability_.width) /
                                   2;

      RTC_DCHECK(vih->bmiHeader.biSizeImage);
      media_type->lSampleSize = vih->bmiHeader.biSizeImage;
      media_type->bFixedSizeSamples = true;
      if (fetched)
        ++(*fetched);
    }
    return pos_ == static_cast<int>(format_preference_order_.size()) ? S_FALSE
                                                                     : S_OK;
  }

  static void SetMediaInfoFromVideoType(VideoType video_type,
                                        BITMAPINFOHEADER* bitmap_header,
                                        AM_MEDIA_TYPE* media_type) {
    switch (video_type) {
      case VideoType::kI420:
        bitmap_header->biCompression = MAKEFOURCC('I', '4', '2', '0');
        bitmap_header->biBitCount = 12;  // bit per pixel
        media_type->subtype = MEDIASUBTYPE_I420;
        break;
      case VideoType::kYUY2:
        bitmap_header->biCompression = MAKEFOURCC('Y', 'U', 'Y', '2');
        bitmap_header->biBitCount = 16;  // bit per pixel
        media_type->subtype = MEDIASUBTYPE_YUY2;
        break;
      case VideoType::kRGB24:
        bitmap_header->biCompression = BI_RGB;
        bitmap_header->biBitCount = 24;  // bit per pixel
        media_type->subtype = MEDIASUBTYPE_RGB24;
        break;
      case VideoType::kUYVY:
        bitmap_header->biCompression = MAKEFOURCC('U', 'Y', 'V', 'Y');
        bitmap_header->biBitCount = 16;  // bit per pixel
        media_type->subtype = MEDIASUBTYPE_UYVY;
        break;
      case VideoType::kMJPEG:
        bitmap_header->biCompression = MAKEFOURCC('M', 'J', 'P', 'G');
        bitmap_header->biBitCount = 12;  // bit per pixel
        media_type->subtype = MEDIASUBTYPE_MJPG;
        break;
      default:
        RTC_DCHECK_NOTREACHED();
    }
  }

  STDMETHOD(Skip)(ULONG count) {
    RTC_DCHECK_NOTREACHED();
    return E_NOTIMPL;
  }

  STDMETHOD(Reset)() {
    pos_ = 0;
    return S_OK;
  }

  int pos_ = 0;
  const VideoCaptureCapability capability_;
  std::list<VideoType> format_preference_order_;
};

}  // namespace

CaptureInputPin::CaptureInputPin(CaptureSinkFilter* filter) {
  capture_checker_.Detach();
  // No reference held to avoid circular references.
  info_.pFilter = filter;
  info_.dir = PINDIR_INPUT;
}

CaptureInputPin::~CaptureInputPin() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  ResetMediaType(&media_type_);
}

HRESULT CaptureInputPin::SetRequestedCapability(
    const VideoCaptureCapability& capability) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(Filter()->IsStopped());
  requested_capability_ = capability;
  resulting_capability_ = VideoCaptureCapability();
  return S_OK;
}

void CaptureInputPin::OnFilterActivated() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  runtime_error_ = false;
  flushing_ = false;
  capture_checker_.Detach();
  capture_thread_id_ = 0;
}

void CaptureInputPin::OnFilterDeactivated() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  // Expedite shutdown by raising the flushing flag so no further processing
  // on the capture thread occurs. When the graph is stopped and all filters
  // have been told to stop, the media controller (graph) will wait for the
  // capture thread to stop.
  flushing_ = true;
  if (allocator_)
    allocator_->Decommit();
}

CaptureSinkFilter* CaptureInputPin::Filter() const {
  return static_cast<CaptureSinkFilter*>(info_.pFilter);
}

HRESULT CaptureInputPin::AttemptConnection(IPin* receive_pin,
                                           const AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(Filter()->IsStopped());

  // Check that the connection is valid  -- need to do this for every
  // connect attempt since BreakConnect will undo it.
  HRESULT hr = CheckDirection(receive_pin);
  if (FAILED(hr))
    return hr;

  if (!TranslateMediaTypeToVideoCaptureCapability(media_type,
                                                  &resulting_capability_)) {
    ClearAllocator(true);
    return VFW_E_TYPE_NOT_ACCEPTED;
  }

  // See if the other pin will accept this type.
  hr = receive_pin->ReceiveConnection(static_cast<IPin*>(this), media_type);
  if (FAILED(hr)) {
    receive_pin_ = nullptr;  // Should already be null, but just in case.
    return hr;
  }

  // Should have been set as part of the connect process.
  RTC_DCHECK_EQ(receive_pin_, receive_pin);

  ResetMediaType(&media_type_);
  CopyMediaType(&media_type_, media_type);

  return S_OK;
}

std::vector<AM_MEDIA_TYPE*> CaptureInputPin::DetermineCandidateFormats(
    IPin* receive_pin,
    const AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(receive_pin);
  RTC_DCHECK(media_type);

  std::vector<AM_MEDIA_TYPE*> ret;

  for (int i = 0; i < 2; i++) {
    IEnumMediaTypes* types = nullptr;
    if (i == 0) {
      // First time around, try types from receive_pin.
      receive_pin->EnumMediaTypes(&types);
    } else {
      // Then try ours.
      EnumMediaTypes(&types);
    }

    if (types) {
      while (true) {
        ULONG fetched = 0;
        AM_MEDIA_TYPE* this_type = nullptr;
        if (types->Next(1, &this_type, &fetched) != S_OK)
          break;

        if (IsMediaTypePartialMatch(*this_type, *media_type)) {
          ret.push_back(this_type);
        } else {
          FreeMediaType(this_type);
        }
      }
      types->Release();
    }
  }

  return ret;
}

void CaptureInputPin::ClearAllocator(bool decommit) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (!allocator_)
    return;
  if (decommit)
    allocator_->Decommit();
  allocator_ = nullptr;
}

HRESULT CaptureInputPin::CheckDirection(IPin* pin) const {
  RTC_DCHECK_RUN_ON(&main_checker_);
  PIN_DIRECTION pd;
  pin->QueryDirection(&pd);
  // Fairly basic check, make sure we don't pair input with input etc.
  return pd == info_.dir ? VFW_E_INVALID_DIRECTION : S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::QueryInterface(REFIID riid,
                                                                  void** ppv) {
  (*ppv) = nullptr;
  if (riid == IID_IUnknown || riid == IID_IMemInputPin) {
    *ppv = static_cast<IMemInputPin*>(this);
  } else if (riid == IID_IPin) {
    *ppv = static_cast<IPin*>(this);
  }

  if (!(*ppv))
    return E_NOINTERFACE;

  static_cast<IMemInputPin*>(this)->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::Connect(IPin* receive_pin, const AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (!media_type || !receive_pin)
    return E_POINTER;

  if (!Filter()->IsStopped())
    return VFW_E_NOT_STOPPED;

  if (receive_pin_) {
    RTC_DCHECK_NOTREACHED();
    return VFW_E_ALREADY_CONNECTED;
  }

  if (IsMediaTypeFullySpecified(*media_type))
    return AttemptConnection(receive_pin, media_type);

  auto types = DetermineCandidateFormats(receive_pin, media_type);
  bool connected = false;
  for (auto* type : types) {
    if (!connected && AttemptConnection(receive_pin, media_type) == S_OK)
      connected = true;

    FreeMediaType(type);
  }

  return connected ? S_OK : VFW_E_NO_ACCEPTABLE_TYPES;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::ReceiveConnection(IPin* connector,
                                   const AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(Filter()->IsStopped());

  if (receive_pin_) {
    RTC_DCHECK_NOTREACHED();
    return VFW_E_ALREADY_CONNECTED;
  }

  HRESULT hr = CheckDirection(connector);
  if (FAILED(hr))
    return hr;

  if (!TranslateMediaTypeToVideoCaptureCapability(media_type,
                                                  &resulting_capability_))
    return VFW_E_TYPE_NOT_ACCEPTED;

  // Complete the connection

  receive_pin_ = connector;
  ResetMediaType(&media_type_);
  CopyMediaType(&media_type_, media_type);

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::Disconnect() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (!Filter()->IsStopped())
    return VFW_E_NOT_STOPPED;

  if (!receive_pin_)
    return S_FALSE;

  ClearAllocator(true);
  receive_pin_ = nullptr;

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::ConnectedTo(IPin** pin) {
  RTC_DCHECK_RUN_ON(&main_checker_);

  if (!receive_pin_)
    return VFW_E_NOT_CONNECTED;

  *pin = receive_pin_.get();
  receive_pin_->AddRef();

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::ConnectionMediaType(AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);

  if (!receive_pin_)
    return VFW_E_NOT_CONNECTED;

  CopyMediaType(media_type, &media_type_);

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::QueryPinInfo(PIN_INFO* info) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *info = info_;
  if (info_.pFilter)
    info_.pFilter->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::QueryDirection(PIN_DIRECTION* pin_dir) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *pin_dir = info_.dir;
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::QueryId(LPWSTR* id) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  size_t len = lstrlenW(info_.achName);
  *id = reinterpret_cast<LPWSTR>(CoTaskMemAlloc((len + 1) * sizeof(wchar_t)));
  lstrcpyW(*id, info_.achName);
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::QueryAccept(const AM_MEDIA_TYPE* media_type) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(Filter()->IsStopped());
  VideoCaptureCapability capability(resulting_capability_);
  return TranslateMediaTypeToVideoCaptureCapability(media_type, &capability)
             ? S_FALSE
             : S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::EnumMediaTypes(IEnumMediaTypes** types) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *types = new ComRefCount<MediaTypesEnum>(requested_capability_);
  (*types)->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::QueryInternalConnections(IPin** pins, ULONG* count) {
  return E_NOTIMPL;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::EndOfStream() {
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::BeginFlush() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  flushing_ = true;
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::EndFlush() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  flushing_ = false;
  runtime_error_ = false;
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::NewSegment(REFERENCE_TIME start,
                            REFERENCE_TIME stop,
                            double rate) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::GetAllocator(IMemAllocator** allocator) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (allocator_ == nullptr) {
    HRESULT hr = CoCreateInstance(CLSID_MemoryAllocator, 0,
                                  CLSCTX_INPROC_SERVER, IID_IMemAllocator,
                                  reinterpret_cast<void**>(allocator));
    if (FAILED(hr))
      return hr;
    allocator_.swap(allocator);
  }
  *allocator = allocator_.get();
  allocator_->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::NotifyAllocator(IMemAllocator* allocator, BOOL read_only) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  allocator_.swap(&allocator);
  if (allocator_)
    allocator_->AddRef();
  if (allocator)
    allocator->Release();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::GetAllocatorRequirements(ALLOCATOR_PROPERTIES* props) {
  return E_NOTIMPL;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::Receive(IMediaSample* media_sample) {
  RTC_DCHECK_RUN_ON(&capture_checker_);

  CaptureSinkFilter* const filter = static_cast<CaptureSinkFilter*>(Filter());

  if (flushing_.load(std::memory_order_relaxed))
    return S_FALSE;

  if (runtime_error_.load(std::memory_order_relaxed))
    return VFW_E_RUNTIME_ERROR;

  if (!capture_thread_id_) {
    // Make sure we set the thread name only once.
    capture_thread_id_ = GetCurrentThreadId();
    rtc::SetCurrentThreadName("webrtc_video_capture");
  }

  AM_SAMPLE2_PROPERTIES sample_props = {};
  GetSampleProperties(media_sample, &sample_props);
  // Has the format changed in this sample?
  if (sample_props.dwSampleFlags & AM_SAMPLE_TYPECHANGED) {
    // Check the derived class accepts the new format.
    // This shouldn't fail as the source must call QueryAccept first.

    // Note: This will modify resulting_capability_.
    // That should be OK as long as resulting_capability_ is only modified
    // on this thread while it is running (filter is not stopped), and only
    // modified on the main thread when the filter is stopped (i.e. this thread
    // is not running).
    if (!TranslateMediaTypeToVideoCaptureCapability(sample_props.pMediaType,
                                                    &resulting_capability_)) {
      // Raise a runtime error if we fail the media type
      runtime_error_ = true;
      EndOfStream();
      Filter()->NotifyEvent(EC_ERRORABORT, VFW_E_TYPE_NOT_ACCEPTED, 0);
      return VFW_E_INVALIDMEDIATYPE;
    }
  }

  filter->ProcessCapturedFrame(sample_props.pbBuffer, sample_props.lActual,
                               resulting_capability_);

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureInputPin::ReceiveMultiple(IMediaSample** samples,
                                 long count,
                                 long* processed) {
  HRESULT hr = S_OK;
  *processed = 0;
  while (count-- > 0) {
    hr = Receive(samples[*processed]);
    if (hr != S_OK)
      break;
    ++(*processed);
  }
  return hr;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureInputPin::ReceiveCanBlock() {
  return S_FALSE;
}

//  ----------------------------------------------------------------------------

CaptureSinkFilter::CaptureSinkFilter(VideoCaptureImpl* capture_observer)
    : input_pin_(new ComRefCount<CaptureInputPin>(this)),
      capture_observer_(capture_observer) {}

CaptureSinkFilter::~CaptureSinkFilter() {
  RTC_DCHECK_RUN_ON(&main_checker_);
}

HRESULT CaptureSinkFilter::SetRequestedCapability(
    const VideoCaptureCapability& capability) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  // Called on the same thread as capture is started on.
  return input_pin_->SetRequestedCapability(capability);
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::GetState(DWORD msecs, FILTER_STATE* state) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *state = state_;
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::SetSyncSource(IReferenceClock* clock) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::GetSyncSource(IReferenceClock** clock) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  return E_NOTIMPL;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureSinkFilter::Pause() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  state_ = State_Paused;
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::Run(REFERENCE_TIME tStart) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (state_ == State_Stopped)
    Pause();

  state_ = State_Running;
  input_pin_->OnFilterActivated();

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureSinkFilter::Stop() {
  RTC_DCHECK_RUN_ON(&main_checker_);
  if (state_ == State_Stopped)
    return S_OK;

  state_ = State_Stopped;
  input_pin_->OnFilterDeactivated();

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::EnumPins(IEnumPins** pins) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *pins = new ComRefCount<class EnumPins>(input_pin_.get());
  (*pins)->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureSinkFilter::FindPin(LPCWSTR id,
                                                             IPin** pin) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  // There's no ID assigned to our input pin, so looking it up based on one
  // is pointless (and in practice, this method isn't being used).
  return VFW_E_NOT_FOUND;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::QueryFilterInfo(FILTER_INFO* info) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  *info = info_;
  if (info->pGraph)
    info->pGraph->AddRef();
  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::JoinFilterGraph(IFilterGraph* graph, LPCWSTR name) {
  RTC_DCHECK_RUN_ON(&main_checker_);
  RTC_DCHECK(IsStopped());

  // Note, since a reference to the filter is held by the graph manager,
  // filters must not hold a reference to the graph. If they would, we'd have
  // a circular reference. Instead, a pointer to the graph can be held without
  // reference. See documentation for IBaseFilter::JoinFilterGraph for more.
  info_.pGraph = graph;  // No AddRef().
  sink_ = nullptr;

  if (info_.pGraph) {
    // make sure we don't hold on to the reference we may receive.
    // Note that this assumes the same object identity, but so be it.
    rtc::scoped_refptr<IMediaEventSink> sink;
    GetComInterface(info_.pGraph, &sink);
    sink_ = sink.get();
  }

  info_.achName[0] = L'\0';
  if (name)
    lstrcpynW(info_.achName, name, arraysize(info_.achName));

  return S_OK;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::QueryVendorInfo(LPWSTR* vendor_info) {
  return E_NOTIMPL;
}

void CaptureSinkFilter::ProcessCapturedFrame(
    unsigned char* buffer,
    size_t length,
    const VideoCaptureCapability& frame_info) {
  // Called on the capture thread.
  capture_observer_->IncomingFrame(buffer, length, frame_info);
}

void CaptureSinkFilter::NotifyEvent(long code,
                                    LONG_PTR param1,
                                    LONG_PTR param2) {
  // Called on the capture thread.
  if (!sink_)
    return;

  if (EC_COMPLETE == code)
    param2 = reinterpret_cast<LONG_PTR>(static_cast<IBaseFilter*>(this));
  sink_->Notify(code, param1, param2);
}

bool CaptureSinkFilter::IsStopped() const {
  RTC_DCHECK_RUN_ON(&main_checker_);
  return state_ == State_Stopped;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP
CaptureSinkFilter::QueryInterface(REFIID riid, void** ppv) {
  if (riid == IID_IUnknown || riid == IID_IPersist || riid == IID_IBaseFilter) {
    *ppv = static_cast<IBaseFilter*>(this);
    AddRef();
    return S_OK;
  }
  return E_NOINTERFACE;
}

COM_DECLSPEC_NOTHROW STDMETHODIMP CaptureSinkFilter::GetClassID(CLSID* clsid) {
  *clsid = CLSID_SINKFILTER;
  return S_OK;
}

}  // namespace videocapturemodule
}  // namespace webrtc
