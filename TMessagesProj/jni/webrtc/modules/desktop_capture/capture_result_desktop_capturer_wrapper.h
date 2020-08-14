/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef MODULES_DESKTOP_CAPTURE_CAPTURE_RESULT_DESKTOP_CAPTURER_WRAPPER_H_
#define MODULES_DESKTOP_CAPTURE_CAPTURE_RESULT_DESKTOP_CAPTURER_WRAPPER_H_

#include <memory>

#include "modules/desktop_capture/desktop_capturer.h"
#include "modules/desktop_capture/desktop_capturer_wrapper.h"
#include "modules/desktop_capture/desktop_frame.h"

namespace webrtc {

// A DesktopCapturerWrapper implementation to capture the result of
// |base_capturer|. Derived classes are expected to provide a ResultObserver
// implementation to observe the DesktopFrame returned by |base_capturer_|.
class CaptureResultDesktopCapturerWrapper : public DesktopCapturerWrapper,
                                            public DesktopCapturer::Callback {
 public:
  using Callback = DesktopCapturer::Callback;

  // Provides a way to let derived classes or clients to modify the result
  // returned by |base_capturer_|.
  class ResultObserver {
   public:
    ResultObserver();
    virtual ~ResultObserver();

    virtual void Observe(Result* result,
                         std::unique_ptr<DesktopFrame>* frame) = 0;
  };

  // |observer| must outlive this instance and can be |this|. |observer| is
  // guaranteed to be executed only after the constructor and before the
  // destructor.
  CaptureResultDesktopCapturerWrapper(
      std::unique_ptr<DesktopCapturer> base_capturer,
      ResultObserver* observer);

  ~CaptureResultDesktopCapturerWrapper() override;

  // DesktopCapturer implementations.
  void Start(Callback* callback) final;

 private:
  // DesktopCapturer::Callback implementation.
  void OnCaptureResult(Result result,
                       std::unique_ptr<DesktopFrame> frame) final;

  ResultObserver* const observer_;
  Callback* callback_ = nullptr;
};

}  // namespace webrtc

#endif  // MODULES_DESKTOP_CAPTURE_CAPTURE_RESULT_DESKTOP_CAPTURER_WRAPPER_H_
