/*
 *  Copyright (c) 2012 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "modules/audio_coding/neteq/dtmf_buffer.h"

#include <algorithm>  // max

#include "rtc_base/checks.h"
#include "rtc_base/logging.h"

// Modify the code to obtain backwards bit-exactness. Once bit-exactness is no
// longer required, this #define should be removed (and the code that it
// enables).
#define LEGACY_BITEXACT

namespace webrtc {

DtmfBuffer::DtmfBuffer(int fs_hz) {
  SetSampleRate(fs_hz);
}

DtmfBuffer::~DtmfBuffer() = default;

void DtmfBuffer::Flush() {
  buffer_.clear();
}

// The ParseEvent method parses 4 bytes from `payload` according to this format
// from RFC 4733:
//
//  0                   1                   2                   3
//  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
// |     event     |E|R| volume    |          duration             |
// +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
//
// Legend (adapted from RFC 4733)
// - event:    The event field is a number between 0 and 255 identifying a
//             specific telephony event. The buffer will not accept any event
//             numbers larger than 15.
// - E:        If set to a value of one, the "end" bit indicates that this
//             packet contains the end of the event.  For long-lasting events
//             that have to be split into segments, only the final packet for
//             the final segment will have the E bit set.
// - R:        Reserved.
// - volume:   For DTMF digits and other events representable as tones, this
//             field describes the power level of the tone, expressed in dBm0
//             after dropping the sign.  Power levels range from 0 to -63 dBm0.
//             Thus, larger values denote lower volume. The buffer discards
//             values larger than 36 (i.e., lower than -36 dBm0).
// - duration: The duration field indicates the duration of the event or segment
//             being reported, in timestamp units, expressed as an unsigned
//             integer in network byte order.  For a non-zero value, the event
//             or segment began at the instant identified by the RTP timestamp
//             and has so far lasted as long as indicated by this parameter.
//             The event may or may not have ended.  If the event duration
//             exceeds the maximum representable by the duration field, the
//             event is split into several contiguous segments. The buffer will
//             discard zero-duration events.
//
int DtmfBuffer::ParseEvent(uint32_t rtp_timestamp,
                           const uint8_t* payload,
                           size_t payload_length_bytes,
                           DtmfEvent* event) {
  RTC_CHECK(payload);
  RTC_CHECK(event);
  if (payload_length_bytes < 4) {
    RTC_LOG(LS_WARNING) << "ParseEvent payload too short";
    return kPayloadTooShort;
  }

  event->event_no = payload[0];
  event->end_bit = ((payload[1] & 0x80) != 0);
  event->volume = (payload[1] & 0x3F);
  event->duration = payload[2] << 8 | payload[3];
  event->timestamp = rtp_timestamp;
  return kOK;
}

// Inserts a DTMF event into the buffer. The event should be parsed from the
// bit stream using the ParseEvent method above before inserting it in the
// buffer.
// DTMF events can be quite long, and in most cases the duration of the event
// is not known when the first packet describing it is sent. To deal with that,
// the RFC 4733 specifies that multiple packets are sent for one and the same
// event as it is being created (typically, as the user is pressing the key).
// These packets will all share the same start timestamp and event number,
// while the duration will be the cumulative duration from the start. When
// inserting a new event, the InsertEvent method tries to find a matching event
// already in the buffer. If so, the new event is simply merged with the
// existing one.
int DtmfBuffer::InsertEvent(const DtmfEvent& event) {
  if (event.event_no < 0 || event.event_no > 15 || event.volume < 0 ||
      event.volume > 63 || event.duration <= 0 || event.duration > 65535) {
    RTC_LOG(LS_WARNING) << "InsertEvent invalid parameters";
    return kInvalidEventParameters;
  }
  DtmfList::iterator it = buffer_.begin();
  while (it != buffer_.end()) {
    if (MergeEvents(it, event)) {
      // A matching event was found and the new event was merged.
      return kOK;
    }
    ++it;
  }
  buffer_.push_back(event);
  // Sort the buffer using CompareEvents to rank the events.
  buffer_.sort(CompareEvents);
  return kOK;
}

bool DtmfBuffer::GetEvent(uint32_t current_timestamp, DtmfEvent* event) {
  DtmfList::iterator it = buffer_.begin();
  while (it != buffer_.end()) {
    // `event_end` is an estimate of where the current event ends. If the end
    // bit is set, we know that the event ends at `timestamp` + `duration`.
    uint32_t event_end = it->timestamp + it->duration;
#ifdef LEGACY_BITEXACT
    bool next_available = false;
#endif
    if (!it->end_bit) {
      // If the end bit is not set, we allow extrapolation of the event for
      // some time.
      event_end += max_extrapolation_samples_;
      DtmfList::iterator next = it;
      ++next;
      if (next != buffer_.end()) {
        // If there is a next event in the buffer, we will not extrapolate over
        // the start of that new event.
        event_end = std::min(event_end, next->timestamp);
#ifdef LEGACY_BITEXACT
        next_available = true;
#endif
      }
    }
    if (current_timestamp >= it->timestamp &&
        current_timestamp <= event_end) {  // TODO(hlundin): Change to <.
      // Found a matching event.
      if (event) {
        event->event_no = it->event_no;
        event->end_bit = it->end_bit;
        event->volume = it->volume;
        event->duration = it->duration;
        event->timestamp = it->timestamp;
      }
#ifdef LEGACY_BITEXACT
      if (it->end_bit && current_timestamp + frame_len_samples_ >= event_end) {
        // We are done playing this. Erase the event.
        buffer_.erase(it);
      }
#endif
      return true;
    } else if (current_timestamp > event_end) {  // TODO(hlundin): Change to >=.
// Erase old event. Operation returns a valid pointer to the next element
// in the list.
#ifdef LEGACY_BITEXACT
      if (!next_available) {
        if (event) {
          event->event_no = it->event_no;
          event->end_bit = it->end_bit;
          event->volume = it->volume;
          event->duration = it->duration;
          event->timestamp = it->timestamp;
        }
        it = buffer_.erase(it);
        return true;
      } else {
        it = buffer_.erase(it);
      }
#else
      it = buffer_.erase(it);
#endif
    } else {
      ++it;
    }
  }
  return false;
}

size_t DtmfBuffer::Length() const {
  return buffer_.size();
}

bool DtmfBuffer::Empty() const {
  return buffer_.empty();
}

int DtmfBuffer::SetSampleRate(int fs_hz) {
  if (fs_hz != 8000 && fs_hz != 16000 && fs_hz != 32000 && fs_hz != 48000) {
    return kInvalidSampleRate;
  }
  max_extrapolation_samples_ = 7 * fs_hz / 100;
  frame_len_samples_ = fs_hz / 100;
  return kOK;
}

// The method returns true if the two events are considered to be the same.
// The are defined as equal if they share the same timestamp and event number.
// The special case with long-lasting events that have to be split into segments
// is not handled in this method. These will be treated as separate events in
// the buffer.
bool DtmfBuffer::SameEvent(const DtmfEvent& a, const DtmfEvent& b) {
  return (a.event_no == b.event_no) && (a.timestamp == b.timestamp);
}

bool DtmfBuffer::MergeEvents(DtmfList::iterator it, const DtmfEvent& event) {
  if (SameEvent(*it, event)) {
    if (!it->end_bit) {
      // Do not extend the duration of an event for which the end bit was
      // already received.
      it->duration = std::max(event.duration, it->duration);
    }
    if (event.end_bit) {
      it->end_bit = true;
    }
    return true;
  } else {
    return false;
  }
}

// Returns true if `a` goes before `b` in the sorting order ("`a` < `b`").
// The events are ranked using their start timestamp (taking wrap-around into
// account). In the unlikely situation that two events share the same start
// timestamp, the event number is used to rank the two. Note that packets
// that belong to the same events, and therefore sharing the same start
// timestamp, have already been merged before the sort method is called.
bool DtmfBuffer::CompareEvents(const DtmfEvent& a, const DtmfEvent& b) {
  if (a.timestamp == b.timestamp) {
    return a.event_no < b.event_no;
  }
  // Take wrap-around into account.
  return (static_cast<uint32_t>(b.timestamp - a.timestamp) < 0xFFFFFFFF / 2);
}
}  // namespace webrtc
