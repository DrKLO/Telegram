/*
 *  Copyright 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_DTMF_SENDER_H_
#define PC_DTMF_SENDER_H_

#include <string>

#include "api/dtmf_sender_interface.h"
#include "api/proxy.h"
#include "rtc_base/async_invoker.h"
#include "rtc_base/constructor_magic.h"
#include "rtc_base/ref_count.h"
#include "rtc_base/thread.h"

// DtmfSender is the native implementation of the RTCDTMFSender defined by
// the WebRTC W3C Editor's Draft.
// https://w3c.github.io/webrtc-pc/#rtcdtmfsender

namespace webrtc {

// This interface is called by DtmfSender to talk to the actual audio channel
// to send DTMF.
class DtmfProviderInterface {
 public:
  // Returns true if the audio sender is capable of sending DTMF. Otherwise
  // returns false.
  virtual bool CanInsertDtmf() = 0;
  // Sends DTMF |code|.
  // The |duration| indicates the length of the DTMF tone in ms.
  // Returns true on success and false on failure.
  virtual bool InsertDtmf(int code, int duration) = 0;
  // Returns a |sigslot::signal0<>| signal. The signal should fire before
  // the provider is destroyed.
  virtual sigslot::signal0<>* GetOnDestroyedSignal() = 0;

 protected:
  virtual ~DtmfProviderInterface() {}
};

class DtmfSender : public DtmfSenderInterface, public sigslot::has_slots<> {
 public:
  static rtc::scoped_refptr<DtmfSender> Create(rtc::Thread* signaling_thread,
                                               DtmfProviderInterface* provider);

  // Implements DtmfSenderInterface.
  void RegisterObserver(DtmfSenderObserverInterface* observer) override;
  void UnregisterObserver() override;
  bool CanInsertDtmf() override;
  bool InsertDtmf(const std::string& tones,
                  int duration,
                  int inter_tone_gap,
                  int comma_delay = kDtmfDefaultCommaDelayMs) override;
  std::string tones() const override;
  int duration() const override;
  int inter_tone_gap() const override;
  int comma_delay() const override;

 protected:
  DtmfSender(rtc::Thread* signaling_thread, DtmfProviderInterface* provider);
  virtual ~DtmfSender();

 private:
  DtmfSender();

  void QueueInsertDtmf(const rtc::Location& posted_from, uint32_t delay_ms);

  // The DTMF sending task.
  void DoInsertDtmf();

  void OnProviderDestroyed();

  void StopSending();

  DtmfSenderObserverInterface* observer_;
  rtc::Thread* signaling_thread_;
  DtmfProviderInterface* provider_;
  std::string tones_;
  int duration_;
  int inter_tone_gap_;
  int comma_delay_;
  // Invoker for running delayed tasks which feed the DTMF provider one tone at
  // a time.
  rtc::AsyncInvoker dtmf_driver_;

  RTC_DISALLOW_COPY_AND_ASSIGN(DtmfSender);
};

// Define proxy for DtmfSenderInterface.
BEGIN_SIGNALING_PROXY_MAP(DtmfSender)
PROXY_SIGNALING_THREAD_DESTRUCTOR()
PROXY_METHOD1(void, RegisterObserver, DtmfSenderObserverInterface*)
PROXY_METHOD0(void, UnregisterObserver)
PROXY_METHOD0(bool, CanInsertDtmf)
PROXY_METHOD4(bool, InsertDtmf, const std::string&, int, int, int)
PROXY_CONSTMETHOD0(std::string, tones)
PROXY_CONSTMETHOD0(int, duration)
PROXY_CONSTMETHOD0(int, inter_tone_gap)
PROXY_CONSTMETHOD0(int, comma_delay)
END_PROXY_MAP()

// Get DTMF code from the DTMF event character.
bool GetDtmfCode(char tone, int* code);

}  // namespace webrtc

#endif  // PC_DTMF_SENDER_H_
