/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef PC_PEER_CONNECTION_MESSAGE_HANDLER_H_
#define PC_PEER_CONNECTION_MESSAGE_HANDLER_H_

#include <functional>

#include "api/jsep.h"
#include "api/media_stream_interface.h"
#include "api/peer_connection_interface.h"
#include "api/rtc_error.h"
#include "api/stats_types.h"
#include "pc/stats_collector_interface.h"
#include "rtc_base/message_handler.h"
#include "rtc_base/thread.h"
#include "rtc_base/thread_message.h"

namespace webrtc {

class CreateSessionDescriptionObserver;
class SetSessionDescriptionObserver;
class StatsCollectorInterface;
class StatsObserver;
class MediaStreamTrackInterface;

class PeerConnectionMessageHandler : public rtc::MessageHandler {
 public:
  explicit PeerConnectionMessageHandler(rtc::Thread* signaling_thread)
      : signaling_thread_(signaling_thread) {}
  ~PeerConnectionMessageHandler();

  // Implements MessageHandler.
  void OnMessage(rtc::Message* msg) override;
  void PostSetSessionDescriptionSuccess(
      SetSessionDescriptionObserver* observer);
  void PostSetSessionDescriptionFailure(SetSessionDescriptionObserver* observer,
                                        RTCError&& error);
  void PostCreateSessionDescriptionFailure(
      CreateSessionDescriptionObserver* observer,
      RTCError error);
  void PostGetStats(StatsObserver* observer,
                    StatsCollectorInterface* stats,
                    MediaStreamTrackInterface* track);
  void RequestUsagePatternReport(std::function<void()>, int delay_ms);

 private:
  rtc::Thread* signaling_thread() const { return signaling_thread_; }

  rtc::Thread* const signaling_thread_;
};

}  // namespace webrtc

#endif  // PC_PEER_CONNECTION_MESSAGE_HANDLER_H_
