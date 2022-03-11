/*
 *  Copyright (c) 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/rtp_rtcp/source/rtcp_transceiver.h"

#include <memory>
#include <utility>
#include <vector>

#include "api/units/timestamp.h"
#include "modules/rtp_rtcp/source/rtcp_packet/transport_feedback.h"
#include "rtc_base/checks.h"
#include "rtc_base/event.h"
#include "rtc_base/task_utils/to_queued_task.h"
#include "rtc_base/time_utils.h"

namespace webrtc {

RtcpTransceiver::RtcpTransceiver(const RtcpTransceiverConfig& config)
    : clock_(config.clock),
      task_queue_(config.task_queue),
      rtcp_transceiver_(std::make_unique<RtcpTransceiverImpl>(config)) {
  RTC_DCHECK(task_queue_);
}

RtcpTransceiver::~RtcpTransceiver() {
  if (!rtcp_transceiver_)
    return;
  auto rtcp_transceiver = std::move(rtcp_transceiver_);
  task_queue_->PostTask(
      ToQueuedTask([rtcp_transceiver = std::move(rtcp_transceiver)] {
        rtcp_transceiver->StopPeriodicTask();
      }));
  RTC_DCHECK(!rtcp_transceiver_);
}

void RtcpTransceiver::Stop(std::function<void()> on_destroyed) {
  RTC_DCHECK(rtcp_transceiver_);
  auto rtcp_transceiver = std::move(rtcp_transceiver_);
  task_queue_->PostTask(ToQueuedTask(
      [rtcp_transceiver = std::move(rtcp_transceiver)] {
        rtcp_transceiver->StopPeriodicTask();
      },
      std::move(on_destroyed)));
  RTC_DCHECK(!rtcp_transceiver_);
}

void RtcpTransceiver::AddMediaReceiverRtcpObserver(
    uint32_t remote_ssrc,
    MediaReceiverRtcpObserver* observer) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(ToQueuedTask([ptr, remote_ssrc, observer] {
    ptr->AddMediaReceiverRtcpObserver(remote_ssrc, observer);
  }));
}

void RtcpTransceiver::RemoveMediaReceiverRtcpObserver(
    uint32_t remote_ssrc,
    MediaReceiverRtcpObserver* observer,
    std::function<void()> on_removed) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  auto remove = [ptr, remote_ssrc, observer] {
    ptr->RemoveMediaReceiverRtcpObserver(remote_ssrc, observer);
  };
  task_queue_->PostTask(ToQueuedTask(std::move(remove), std::move(on_removed)));
}

void RtcpTransceiver::SetReadyToSend(bool ready) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(
      ToQueuedTask([ptr, ready] { ptr->SetReadyToSend(ready); }));
}

void RtcpTransceiver::ReceivePacket(rtc::CopyOnWriteBuffer packet) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  Timestamp now = clock_->CurrentTime();
  task_queue_->PostTask(
      ToQueuedTask([ptr, packet, now] { ptr->ReceivePacket(packet, now); }));
}

void RtcpTransceiver::SendCompoundPacket() {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(ToQueuedTask([ptr] { ptr->SendCompoundPacket(); }));
}

void RtcpTransceiver::SetRemb(int64_t bitrate_bps,
                              std::vector<uint32_t> ssrcs) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(
      ToQueuedTask([ptr, bitrate_bps, ssrcs = std::move(ssrcs)]() mutable {
        ptr->SetRemb(bitrate_bps, std::move(ssrcs));
      }));
}

void RtcpTransceiver::UnsetRemb() {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(ToQueuedTask([ptr] { ptr->UnsetRemb(); }));
}

void RtcpTransceiver::SendCombinedRtcpPacket(
    std::vector<std::unique_ptr<rtcp::RtcpPacket>> rtcp_packets) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(
      ToQueuedTask([ptr, rtcp_packets = std::move(rtcp_packets)]() mutable {
        ptr->SendCombinedRtcpPacket(std::move(rtcp_packets));
      }));
}

void RtcpTransceiver::SendNack(uint32_t ssrc,
                               std::vector<uint16_t> sequence_numbers) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(ToQueuedTask(
      [ptr, ssrc, sequence_numbers = std::move(sequence_numbers)]() mutable {
        ptr->SendNack(ssrc, std::move(sequence_numbers));
      }));
}

void RtcpTransceiver::SendPictureLossIndication(uint32_t ssrc) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(
      ToQueuedTask([ptr, ssrc] { ptr->SendPictureLossIndication(ssrc); }));
}

void RtcpTransceiver::SendFullIntraRequest(std::vector<uint32_t> ssrcs) {
  return SendFullIntraRequest(std::move(ssrcs), true);
}

void RtcpTransceiver::SendFullIntraRequest(std::vector<uint32_t> ssrcs,
                                           bool new_request) {
  RTC_CHECK(rtcp_transceiver_);
  RtcpTransceiverImpl* ptr = rtcp_transceiver_.get();
  task_queue_->PostTask(
      ToQueuedTask([ptr, ssrcs = std::move(ssrcs), new_request] {
        ptr->SendFullIntraRequest(ssrcs, new_request);
      }));
}

}  // namespace webrtc
