/*
 *  Copyright 2019 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */
#ifndef LOGGING_RTC_EVENT_LOG_RTC_EVENT_LOG_PARSER_H_
#define LOGGING_RTC_EVENT_LOG_RTC_EVENT_LOG_PARSER_H_

#include <iterator>
#include <limits>
#include <map>
#include <set>
#include <sstream>  // no-presubmit-check TODO(webrtc:8982)
#include <string>
#include <utility>  // pair
#include <vector>

#include "api/rtc_event_log/rtc_event_log.h"
#include "call/video_receive_stream.h"
#include "call/video_send_stream.h"
#include "logging/rtc_event_log/logged_events.h"
#include "modules/rtp_rtcp/include/rtp_header_extension_map.h"
#include "modules/rtp_rtcp/source/rtcp_packet/common_header.h"
#include "rtc_base/ignore_wundef.h"

// Files generated at build-time by the protobuf compiler.
RTC_PUSH_IGNORING_WUNDEF()
#ifdef WEBRTC_ANDROID_PLATFORM_BUILD
#include "external/webrtc/webrtc/logging/rtc_event_log/rtc_event_log.pb.h"
#include "external/webrtc/webrtc/logging/rtc_event_log/rtc_event_log2.pb.h"
#else
#include "logging/rtc_event_log/rtc_event_log.pb.h"
#include "logging/rtc_event_log/rtc_event_log2.pb.h"
#endif
RTC_POP_IGNORING_WUNDEF()

namespace webrtc {

enum PacketDirection { kIncomingPacket = 0, kOutgoingPacket };

template <typename T>
class PacketView;

template <typename T>
class PacketIterator {
  friend class PacketView<T>;

 public:
  // Standard iterator traits.
  using difference_type = std::ptrdiff_t;
  using value_type = T;
  using pointer = T*;
  using reference = T&;
  using iterator_category = std::bidirectional_iterator_tag;

  // The default-contructed iterator is meaningless, but is required by the
  // ForwardIterator concept.
  PacketIterator() : ptr_(nullptr), element_size_(0) {}
  PacketIterator(const PacketIterator& other)
      : ptr_(other.ptr_), element_size_(other.element_size_) {}
  PacketIterator(const PacketIterator&& other)
      : ptr_(other.ptr_), element_size_(other.element_size_) {}
  ~PacketIterator() = default;

  PacketIterator& operator=(const PacketIterator& other) {
    ptr_ = other.ptr_;
    element_size_ = other.element_size_;
    return *this;
  }
  PacketIterator& operator=(const PacketIterator&& other) {
    ptr_ = other.ptr_;
    element_size_ = other.element_size_;
    return *this;
  }

  bool operator==(const PacketIterator<T>& other) const {
    RTC_DCHECK_EQ(element_size_, other.element_size_);
    return ptr_ == other.ptr_;
  }
  bool operator!=(const PacketIterator<T>& other) const {
    RTC_DCHECK_EQ(element_size_, other.element_size_);
    return ptr_ != other.ptr_;
  }

  PacketIterator& operator++() {
    ptr_ += element_size_;
    return *this;
  }
  PacketIterator& operator--() {
    ptr_ -= element_size_;
    return *this;
  }
  PacketIterator operator++(int) {
    PacketIterator iter_copy(ptr_, element_size_);
    ptr_ += element_size_;
    return iter_copy;
  }
  PacketIterator operator--(int) {
    PacketIterator iter_copy(ptr_, element_size_);
    ptr_ -= element_size_;
    return iter_copy;
  }

  T& operator*() { return *reinterpret_cast<T*>(ptr_); }
  const T& operator*() const { return *reinterpret_cast<const T*>(ptr_); }

  T* operator->() { return reinterpret_cast<T*>(ptr_); }
  const T* operator->() const { return reinterpret_cast<const T*>(ptr_); }

 private:
  PacketIterator(typename std::conditional<std::is_const<T>::value,
                                           const void*,
                                           void*>::type p,
                 size_t s)
      : ptr_(reinterpret_cast<decltype(ptr_)>(p)), element_size_(s) {}

  typename std::conditional<std::is_const<T>::value, const char*, char*>::type
      ptr_;
  size_t element_size_;
};

// Suppose that we have a struct S where we are only interested in a specific
// member M. Given an array of S, PacketView can be used to treat the array
// as an array of M, without exposing the type S to surrounding code and without
// accessing the member through a virtual function. In this case, we want to
// have a common view for incoming and outgoing RtpPackets, hence the PacketView
// name.
// Note that constructing a PacketView bypasses the typesystem, so the caller
// has to take extra care when constructing these objects. The implementation
// also requires that the containing struct is standard-layout (e.g. POD).
//
// Usage example:
// struct A {...};
// struct B { A a; ...};
// struct C { A a; ...};
// size_t len = 10;
// B* array1 = new B[len];
// C* array2 = new C[len];
//
// PacketView<A> view1 = PacketView<A>::Create<B>(array1, len, offsetof(B, a));
// PacketView<A> view2 = PacketView<A>::Create<C>(array2, len, offsetof(C, a));
//
// The following code works with either view1 or view2.
// void f(PacketView<A> view)
// for (A& a : view) {
//   DoSomething(a);
// }
template <typename T>
class PacketView {
 public:
  template <typename U>
  static PacketView Create(U* ptr, size_t num_elements, size_t offset) {
    static_assert(std::is_standard_layout<U>::value,
                  "PacketView can only be created for standard layout types.");
    static_assert(std::is_standard_layout<T>::value,
                  "PacketView can only be created for standard layout types.");
    return PacketView(ptr, num_elements, offset, sizeof(U));
  }

  using value_type = T;
  using reference = value_type&;
  using const_reference = const value_type&;

  using iterator = PacketIterator<T>;
  using const_iterator = PacketIterator<const T>;
  using reverse_iterator = std::reverse_iterator<iterator>;
  using const_reverse_iterator = std::reverse_iterator<const_iterator>;

  iterator begin() { return iterator(data_, element_size_); }
  iterator end() {
    auto end_ptr = data_ + num_elements_ * element_size_;
    return iterator(end_ptr, element_size_);
  }

  const_iterator begin() const { return const_iterator(data_, element_size_); }
  const_iterator end() const {
    auto end_ptr = data_ + num_elements_ * element_size_;
    return const_iterator(end_ptr, element_size_);
  }

  reverse_iterator rbegin() { return reverse_iterator(end()); }
  reverse_iterator rend() { return reverse_iterator(begin()); }

  const_reverse_iterator rbegin() const {
    return const_reverse_iterator(end());
  }
  const_reverse_iterator rend() const {
    return const_reverse_iterator(begin());
  }

  size_t size() const { return num_elements_; }

  bool empty() const { return num_elements_ == 0; }

  T& operator[](size_t i) {
    auto elem_ptr = data_ + i * element_size_;
    return *reinterpret_cast<T*>(elem_ptr);
  }

  const T& operator[](size_t i) const {
    auto elem_ptr = data_ + i * element_size_;
    return *reinterpret_cast<const T*>(elem_ptr);
  }

 private:
  PacketView(typename std::conditional<std::is_const<T>::value,
                                       const void*,
                                       void*>::type data,
             size_t num_elements,
             size_t offset,
             size_t element_size)
      : data_(reinterpret_cast<decltype(data_)>(data) + offset),
        num_elements_(num_elements),
        element_size_(element_size) {}

  typename std::conditional<std::is_const<T>::value, const char*, char*>::type
      data_;
  size_t num_elements_;
  size_t element_size_;
};

// Conversion functions for version 2 of the wire format.
BandwidthUsage GetRuntimeDetectorState(
    rtclog2::DelayBasedBweUpdates::DetectorState detector_state);

ProbeFailureReason GetRuntimeProbeFailureReason(
    rtclog2::BweProbeResultFailure::FailureReason failure);

DtlsTransportState GetRuntimeDtlsTransportState(
    rtclog2::DtlsTransportStateEvent::DtlsTransportState state);

IceCandidatePairConfigType GetRuntimeIceCandidatePairConfigType(
    rtclog2::IceCandidatePairConfig::IceCandidatePairConfigType type);

IceCandidateType GetRuntimeIceCandidateType(
    rtclog2::IceCandidatePairConfig::IceCandidateType type);

IceCandidatePairProtocol GetRuntimeIceCandidatePairProtocol(
    rtclog2::IceCandidatePairConfig::Protocol protocol);

IceCandidatePairAddressFamily GetRuntimeIceCandidatePairAddressFamily(
    rtclog2::IceCandidatePairConfig::AddressFamily address_family);

IceCandidateNetworkType GetRuntimeIceCandidateNetworkType(
    rtclog2::IceCandidatePairConfig::NetworkType network_type);

IceCandidatePairEventType GetRuntimeIceCandidatePairEventType(
    rtclog2::IceCandidatePairEvent::IceCandidatePairEventType type);

std::vector<RtpExtension> GetRuntimeRtpHeaderExtensionConfig(
    const rtclog2::RtpHeaderExtensionConfig& proto_header_extensions);
// End of conversion functions.

class ParsedRtcEventLog {
 public:
  enum class MediaType { ANY, AUDIO, VIDEO, DATA };
  enum class UnconfiguredHeaderExtensions {
    kDontParse,
    kAttemptWebrtcDefaultConfig
  };
  class ParseStatus {
   public:
    static ParseStatus Success() { return ParseStatus(); }
    static ParseStatus Error(std::string error, std::string file, int line) {
      return ParseStatus(error, file, line);
    }

    bool ok() const { return error_.empty() && file_.empty() && line_ == 0; }
    std::string message() const {
      return error_ + " failed at " + file_ + " line " + std::to_string(line_);
    }

    RTC_DEPRECATED operator bool() const { return ok(); }

   private:
    ParseStatus() : error_(), file_(), line_(0) {}
    ParseStatus(std::string error, std::string file, int line)
        : error_(error), file_(file), line_(line) {}
    std::string error_;
    std::string file_;
    int line_;
  };

  template <typename T>
  class ParseStatusOr {
   public:
    ParseStatusOr(const ParseStatus& error)  // NOLINT
        : status_(error), value_() {}
    ParseStatusOr(const T& value)  // NOLINT
        : status_(ParseStatus::Success()), value_(value) {}
    bool ok() const { return status_.ok(); }
    const T& value() const& {
      RTC_DCHECK(status_.ok());
      return value_;
    }
    std::string message() const { return status_.message(); }
    const ParseStatus& status() const { return status_; }

   private:
    ParseStatus status_;
    T value_;
  };

  struct LoggedRtpStreamIncoming {
    LoggedRtpStreamIncoming();
    LoggedRtpStreamIncoming(const LoggedRtpStreamIncoming&);
    ~LoggedRtpStreamIncoming();
    uint32_t ssrc;
    std::vector<LoggedRtpPacketIncoming> incoming_packets;
  };

  struct LoggedRtpStreamOutgoing {
    LoggedRtpStreamOutgoing();
    LoggedRtpStreamOutgoing(const LoggedRtpStreamOutgoing&);
    ~LoggedRtpStreamOutgoing();
    uint32_t ssrc;
    std::vector<LoggedRtpPacketOutgoing> outgoing_packets;
  };

  struct LoggedRtpStreamView {
    LoggedRtpStreamView(uint32_t ssrc,
                        const LoggedRtpPacketIncoming* ptr,
                        size_t num_elements);
    LoggedRtpStreamView(uint32_t ssrc,
                        const LoggedRtpPacketOutgoing* ptr,
                        size_t num_elements);
    LoggedRtpStreamView(const LoggedRtpStreamView&);
    uint32_t ssrc;
    PacketView<const LoggedRtpPacket> packet_view;
  };

  class LogSegment {
   public:
    LogSegment(int64_t start_time_us, int64_t stop_time_us)
        : start_time_us_(start_time_us), stop_time_us_(stop_time_us) {}
    int64_t start_time_ms() const { return start_time_us_ / 1000; }
    int64_t start_time_us() const { return start_time_us_; }
    int64_t stop_time_ms() const { return stop_time_us_ / 1000; }
    int64_t stop_time_us() const { return stop_time_us_; }

   private:
    int64_t start_time_us_;
    int64_t stop_time_us_;
  };

  static webrtc::RtpHeaderExtensionMap GetDefaultHeaderExtensionMap();

  explicit ParsedRtcEventLog(
      UnconfiguredHeaderExtensions parse_unconfigured_header_extensions =
          UnconfiguredHeaderExtensions::kDontParse,
      bool allow_incomplete_log = false);

  ~ParsedRtcEventLog();

  // Clears previously parsed events and resets the ParsedRtcEventLogNew to an
  // empty state.
  void Clear();

  // Reads an RtcEventLog file and returns success if parsing was successful.
  ParseStatus ParseFile(const std::string& file_name);

  // Reads an RtcEventLog from a string and returns success if successful.
  ParseStatus ParseString(const std::string& s);

  // Reads an RtcEventLog from an istream and returns success if successful.
  ParseStatus ParseStream(
      std::istream& stream);  // no-presubmit-check TODO(webrtc:8982)

  MediaType GetMediaType(uint32_t ssrc, PacketDirection direction) const;

  // Configured SSRCs.
  const std::set<uint32_t>& incoming_rtx_ssrcs() const {
    return incoming_rtx_ssrcs_;
  }

  const std::set<uint32_t>& incoming_video_ssrcs() const {
    return incoming_video_ssrcs_;
  }

  const std::set<uint32_t>& incoming_audio_ssrcs() const {
    return incoming_audio_ssrcs_;
  }

  const std::set<uint32_t>& outgoing_rtx_ssrcs() const {
    return outgoing_rtx_ssrcs_;
  }

  const std::set<uint32_t>& outgoing_video_ssrcs() const {
    return outgoing_video_ssrcs_;
  }

  const std::set<uint32_t>& outgoing_audio_ssrcs() const {
    return outgoing_audio_ssrcs_;
  }

  // Stream configurations.
  const std::vector<LoggedAudioRecvConfig>& audio_recv_configs() const {
    return audio_recv_configs_;
  }

  const std::vector<LoggedAudioSendConfig>& audio_send_configs() const {
    return audio_send_configs_;
  }

  const std::vector<LoggedVideoRecvConfig>& video_recv_configs() const {
    return video_recv_configs_;
  }

  const std::vector<LoggedVideoSendConfig>& video_send_configs() const {
    return video_send_configs_;
  }

  // Beginning and end of log segments.
  const std::vector<LoggedStartEvent>& start_log_events() const {
    return start_log_events_;
  }

  const std::vector<LoggedStopEvent>& stop_log_events() const {
    return stop_log_events_;
  }

  const std::vector<LoggedAlrStateEvent>& alr_state_events() const {
    return alr_state_events_;
  }

  // Audio
  const std::map<uint32_t, std::vector<LoggedAudioPlayoutEvent>>&
  audio_playout_events() const {
    return audio_playout_events_;
  }

  const std::vector<LoggedAudioNetworkAdaptationEvent>&
  audio_network_adaptation_events() const {
    return audio_network_adaptation_events_;
  }

  // Bandwidth estimation
  const std::vector<LoggedBweProbeClusterCreatedEvent>&
  bwe_probe_cluster_created_events() const {
    return bwe_probe_cluster_created_events_;
  }

  const std::vector<LoggedBweProbeFailureEvent>& bwe_probe_failure_events()
      const {
    return bwe_probe_failure_events_;
  }

  const std::vector<LoggedBweProbeSuccessEvent>& bwe_probe_success_events()
      const {
    return bwe_probe_success_events_;
  }

  const std::vector<LoggedBweDelayBasedUpdate>& bwe_delay_updates() const {
    return bwe_delay_updates_;
  }

  const std::vector<LoggedBweLossBasedUpdate>& bwe_loss_updates() const {
    return bwe_loss_updates_;
  }

  // DTLS
  const std::vector<LoggedDtlsTransportState>& dtls_transport_states() const {
    return dtls_transport_states_;
  }

  const std::vector<LoggedDtlsWritableState>& dtls_writable_states() const {
    return dtls_writable_states_;
  }

  // ICE events
  const std::vector<LoggedIceCandidatePairConfig>& ice_candidate_pair_configs()
      const {
    return ice_candidate_pair_configs_;
  }

  const std::vector<LoggedIceCandidatePairEvent>& ice_candidate_pair_events()
      const {
    return ice_candidate_pair_events_;
  }

  const std::vector<LoggedRouteChangeEvent>& route_change_events() const {
    return route_change_events_;
  }

  const std::vector<LoggedRemoteEstimateEvent>& remote_estimate_events() const {
    return remote_estimate_events_;
  }

  // RTP
  const std::vector<LoggedRtpStreamIncoming>& incoming_rtp_packets_by_ssrc()
      const {
    return incoming_rtp_packets_by_ssrc_;
  }

  const std::vector<LoggedRtpStreamOutgoing>& outgoing_rtp_packets_by_ssrc()
      const {
    return outgoing_rtp_packets_by_ssrc_;
  }

  const std::vector<LoggedRtpStreamView>& rtp_packets_by_ssrc(
      PacketDirection direction) const {
    if (direction == kIncomingPacket)
      return incoming_rtp_packet_views_by_ssrc_;
    else
      return outgoing_rtp_packet_views_by_ssrc_;
  }

  // RTCP
  const std::vector<LoggedRtcpPacketIncoming>& incoming_rtcp_packets() const {
    return incoming_rtcp_packets_;
  }

  const std::vector<LoggedRtcpPacketOutgoing>& outgoing_rtcp_packets() const {
    return outgoing_rtcp_packets_;
  }

  const std::vector<LoggedRtcpPacketReceiverReport>& receiver_reports(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_rr_;
    } else {
      return outgoing_rr_;
    }
  }

  const std::vector<LoggedRtcpPacketSenderReport>& sender_reports(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_sr_;
    } else {
      return outgoing_sr_;
    }
  }

  const std::vector<LoggedRtcpPacketExtendedReports>& extended_reports(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_xr_;
    } else {
      return outgoing_xr_;
    }
  }

  const std::vector<LoggedRtcpPacketNack>& nacks(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_nack_;
    } else {
      return outgoing_nack_;
    }
  }

  const std::vector<LoggedRtcpPacketRemb>& rembs(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_remb_;
    } else {
      return outgoing_remb_;
    }
  }

  const std::vector<LoggedRtcpPacketFir>& firs(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_fir_;
    } else {
      return outgoing_fir_;
    }
  }

  const std::vector<LoggedRtcpPacketPli>& plis(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_pli_;
    } else {
      return outgoing_pli_;
    }
  }

  const std::vector<LoggedRtcpPacketTransportFeedback>& transport_feedbacks(
      PacketDirection direction) const {
    if (direction == kIncomingPacket) {
      return incoming_transport_feedback_;
    } else {
      return outgoing_transport_feedback_;
    }
  }

  const std::vector<LoggedRtcpPacketLossNotification>& loss_notifications(
      PacketDirection direction) {
    if (direction == kIncomingPacket) {
      return incoming_loss_notification_;
    } else {
      return outgoing_loss_notification_;
    }
  }

  const std::vector<LoggedGenericPacketReceived>& generic_packets_received()
      const {
    return generic_packets_received_;
  }
  const std::vector<LoggedGenericPacketSent>& generic_packets_sent() const {
    return generic_packets_sent_;
  }

  const std::vector<LoggedGenericAckReceived>& generic_acks_received() const {
    return generic_acks_received_;
  }

  // Media
  const std::map<uint32_t, std::vector<LoggedFrameDecoded>>& decoded_frames()
      const {
    return decoded_frames_;
  }

  int64_t first_timestamp() const { return first_timestamp_; }
  int64_t last_timestamp() const { return last_timestamp_; }

  const LogSegment& first_log_segment() const { return first_log_segment_; }

  std::vector<LoggedPacketInfo> GetPacketInfos(PacketDirection direction) const;
  std::vector<LoggedPacketInfo> GetIncomingPacketInfos() const {
    return GetPacketInfos(kIncomingPacket);
  }
  std::vector<LoggedPacketInfo> GetOutgoingPacketInfos() const {
    return GetPacketInfos(kOutgoingPacket);
  }
  std::vector<LoggedIceCandidatePairConfig> GetIceCandidates() const;
  std::vector<LoggedIceEvent> GetIceEvents() const;

  std::vector<InferredRouteChangeEvent> GetRouteChanges() const;

 private:
  ABSL_MUST_USE_RESULT ParseStatus ParseStreamInternal(
      std::istream& stream);  // no-presubmit-check TODO(webrtc:8982)

  ABSL_MUST_USE_RESULT ParseStatus
  StoreParsedLegacyEvent(const rtclog::Event& event);

  template <typename T>
  void StoreFirstAndLastTimestamp(const std::vector<T>& v);

  // Reads the header, direction, header length and packet length from the RTP
  // event at |index|, and stores the values in the corresponding output
  // parameters. Each output parameter can be set to nullptr if that value
  // isn't needed.
  // NB: The header must have space for at least IP_PACKET_SIZE bytes.
  ParseStatus GetRtpHeader(const rtclog::Event& event,
                           PacketDirection* incoming,
                           uint8_t* header,
                           size_t* header_length,
                           size_t* total_length,
                           int* probe_cluster_id) const;

  // Returns: a pointer to a header extensions map acquired from parsing
  // corresponding Audio/Video Sender/Receiver config events.
  // Warning: if the same SSRC is reused by both video and audio streams during
  // call, extensions maps may be incorrect (the last one would be returned).
  const RtpHeaderExtensionMap* GetRtpHeaderExtensionMap(
      PacketDirection direction,
      uint32_t ssrc);

  // Reads packet, direction and packet length from the RTCP event at |index|,
  // and stores the values in the corresponding output parameters.
  // Each output parameter can be set to nullptr if that value isn't needed.
  // NB: The packet must have space for at least IP_PACKET_SIZE bytes.
  ParseStatus GetRtcpPacket(const rtclog::Event& event,
                            PacketDirection* incoming,
                            std::vector<uint8_t>* packet) const;

  ParseStatusOr<rtclog::StreamConfig> GetVideoReceiveConfig(
      const rtclog::Event& event) const;
  ParseStatusOr<rtclog::StreamConfig> GetVideoSendConfig(
      const rtclog::Event& event) const;
  ParseStatusOr<rtclog::StreamConfig> GetAudioReceiveConfig(
      const rtclog::Event& event) const;
  ParseStatusOr<rtclog::StreamConfig> GetAudioSendConfig(
      const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedAudioPlayoutEvent> GetAudioPlayout(
      const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedBweLossBasedUpdate>
  GetLossBasedBweUpdate(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedBweDelayBasedUpdate>
  GetDelayBasedBweUpdate(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedAudioNetworkAdaptationEvent>
  GetAudioNetworkAdaptation(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeClusterCreatedEvent>
  GetBweProbeClusterCreated(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeFailureEvent>
  GetBweProbeFailure(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedBweProbeSuccessEvent>
  GetBweProbeSuccess(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedAlrStateEvent> GetAlrState(
      const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedIceCandidatePairConfig>
  GetIceCandidatePairConfig(const rtclog::Event& event) const;

  ParsedRtcEventLog::ParseStatusOr<LoggedIceCandidatePairEvent>
  GetIceCandidatePairEvent(const rtclog::Event& event) const;

  // Parsing functions for new format.
  ParseStatus StoreAlrStateEvent(const rtclog2::AlrState& proto);
  ParseStatus StoreAudioNetworkAdaptationEvent(
      const rtclog2::AudioNetworkAdaptations& proto);
  ParseStatus StoreAudioPlayoutEvent(const rtclog2::AudioPlayoutEvents& proto);
  ParseStatus StoreAudioRecvConfig(const rtclog2::AudioRecvStreamConfig& proto);
  ParseStatus StoreAudioSendConfig(const rtclog2::AudioSendStreamConfig& proto);
  ParseStatus StoreBweDelayBasedUpdate(
      const rtclog2::DelayBasedBweUpdates& proto);
  ParseStatus StoreBweLossBasedUpdate(
      const rtclog2::LossBasedBweUpdates& proto);
  ParseStatus StoreBweProbeClusterCreated(
      const rtclog2::BweProbeCluster& proto);
  ParseStatus StoreBweProbeFailureEvent(
      const rtclog2::BweProbeResultFailure& proto);
  ParseStatus StoreBweProbeSuccessEvent(
      const rtclog2::BweProbeResultSuccess& proto);
  ParseStatus StoreDtlsTransportState(
      const rtclog2::DtlsTransportStateEvent& proto);
  ParseStatus StoreDtlsWritableState(const rtclog2::DtlsWritableState& proto);
  ParsedRtcEventLog::ParseStatus StoreFrameDecodedEvents(
      const rtclog2::FrameDecodedEvents& proto);
  ParseStatus StoreGenericAckReceivedEvent(
      const rtclog2::GenericAckReceived& proto);
  ParseStatus StoreGenericPacketReceivedEvent(
      const rtclog2::GenericPacketReceived& proto);
  ParseStatus StoreGenericPacketSentEvent(
      const rtclog2::GenericPacketSent& proto);
  ParseStatus StoreIceCandidateEvent(
      const rtclog2::IceCandidatePairEvent& proto);
  ParseStatus StoreIceCandidatePairConfig(
      const rtclog2::IceCandidatePairConfig& proto);
  ParseStatus StoreIncomingRtcpPackets(
      const rtclog2::IncomingRtcpPackets& proto);
  ParseStatus StoreIncomingRtpPackets(const rtclog2::IncomingRtpPackets& proto);
  ParseStatus StoreOutgoingRtcpPackets(
      const rtclog2::OutgoingRtcpPackets& proto);
  ParseStatus StoreOutgoingRtpPackets(const rtclog2::OutgoingRtpPackets& proto);
  ParseStatus StoreParsedNewFormatEvent(const rtclog2::EventStream& event);
  ParseStatus StoreRouteChangeEvent(const rtclog2::RouteChange& proto);
  ParseStatus StoreRemoteEstimateEvent(const rtclog2::RemoteEstimates& proto);
  ParseStatus StoreStartEvent(const rtclog2::BeginLogEvent& proto);
  ParseStatus StoreStopEvent(const rtclog2::EndLogEvent& proto);
  ParseStatus StoreVideoRecvConfig(const rtclog2::VideoRecvStreamConfig& proto);
  ParseStatus StoreVideoSendConfig(const rtclog2::VideoSendStreamConfig& proto);
  // End of new parsing functions.

  struct Stream {
    Stream(uint32_t ssrc,
           MediaType media_type,
           PacketDirection direction,
           webrtc::RtpHeaderExtensionMap map)
        : ssrc(ssrc),
          media_type(media_type),
          direction(direction),
          rtp_extensions_map(map) {}
    uint32_t ssrc;
    MediaType media_type;
    PacketDirection direction;
    webrtc::RtpHeaderExtensionMap rtp_extensions_map;
  };

  const UnconfiguredHeaderExtensions parse_unconfigured_header_extensions_;
  const bool allow_incomplete_logs_;

  // Make a default extension map for streams without configuration information.
  // TODO(ivoc): Once configuration of audio streams is stored in the event log,
  //             this can be removed. Tracking bug: webrtc:6399
  RtpHeaderExtensionMap default_extension_map_;

  // Tracks what each stream is configured for. Note that a single SSRC can be
  // in several sets. For example, the SSRC used for sending video over RTX
  // will appear in both video_ssrcs_ and rtx_ssrcs_. In the unlikely case that
  // an SSRC is reconfigured to a different media type mid-call, it will also
  // appear in multiple sets.
  std::set<uint32_t> incoming_rtx_ssrcs_;
  std::set<uint32_t> incoming_video_ssrcs_;
  std::set<uint32_t> incoming_audio_ssrcs_;
  std::set<uint32_t> outgoing_rtx_ssrcs_;
  std::set<uint32_t> outgoing_video_ssrcs_;
  std::set<uint32_t> outgoing_audio_ssrcs_;

  // Maps an SSRC to the parsed  RTP headers in that stream. Header extensions
  // are parsed if the stream has been configured. This is only used for
  // grouping the events by SSRC during parsing; the events are moved to
  // incoming_rtp_packets_by_ssrc_ once the parsing is done.
  std::map<uint32_t, std::vector<LoggedRtpPacketIncoming>>
      incoming_rtp_packets_map_;
  std::map<uint32_t, std::vector<LoggedRtpPacketOutgoing>>
      outgoing_rtp_packets_map_;

  // RTP headers.
  std::vector<LoggedRtpStreamIncoming> incoming_rtp_packets_by_ssrc_;
  std::vector<LoggedRtpStreamOutgoing> outgoing_rtp_packets_by_ssrc_;
  std::vector<LoggedRtpStreamView> incoming_rtp_packet_views_by_ssrc_;
  std::vector<LoggedRtpStreamView> outgoing_rtp_packet_views_by_ssrc_;

  // Raw RTCP packets.
  std::vector<LoggedRtcpPacketIncoming> incoming_rtcp_packets_;
  std::vector<LoggedRtcpPacketOutgoing> outgoing_rtcp_packets_;

  // Parsed RTCP messages. Currently not separated based on SSRC.
  std::vector<LoggedRtcpPacketReceiverReport> incoming_rr_;
  std::vector<LoggedRtcpPacketReceiverReport> outgoing_rr_;
  std::vector<LoggedRtcpPacketSenderReport> incoming_sr_;
  std::vector<LoggedRtcpPacketSenderReport> outgoing_sr_;
  std::vector<LoggedRtcpPacketExtendedReports> incoming_xr_;
  std::vector<LoggedRtcpPacketExtendedReports> outgoing_xr_;
  std::vector<LoggedRtcpPacketNack> incoming_nack_;
  std::vector<LoggedRtcpPacketNack> outgoing_nack_;
  std::vector<LoggedRtcpPacketRemb> incoming_remb_;
  std::vector<LoggedRtcpPacketRemb> outgoing_remb_;
  std::vector<LoggedRtcpPacketFir> incoming_fir_;
  std::vector<LoggedRtcpPacketFir> outgoing_fir_;
  std::vector<LoggedRtcpPacketPli> incoming_pli_;
  std::vector<LoggedRtcpPacketPli> outgoing_pli_;
  std::vector<LoggedRtcpPacketTransportFeedback> incoming_transport_feedback_;
  std::vector<LoggedRtcpPacketTransportFeedback> outgoing_transport_feedback_;
  std::vector<LoggedRtcpPacketLossNotification> incoming_loss_notification_;
  std::vector<LoggedRtcpPacketLossNotification> outgoing_loss_notification_;

  std::vector<LoggedStartEvent> start_log_events_;
  std::vector<LoggedStopEvent> stop_log_events_;

  std::vector<LoggedAlrStateEvent> alr_state_events_;

  std::map<uint32_t, std::vector<LoggedAudioPlayoutEvent>>
      audio_playout_events_;

  std::vector<LoggedAudioNetworkAdaptationEvent>
      audio_network_adaptation_events_;

  std::vector<LoggedBweProbeClusterCreatedEvent>
      bwe_probe_cluster_created_events_;

  std::vector<LoggedBweProbeFailureEvent> bwe_probe_failure_events_;
  std::vector<LoggedBweProbeSuccessEvent> bwe_probe_success_events_;

  std::vector<LoggedBweDelayBasedUpdate> bwe_delay_updates_;
  std::vector<LoggedBweLossBasedUpdate> bwe_loss_updates_;

  std::vector<LoggedDtlsTransportState> dtls_transport_states_;
  std::vector<LoggedDtlsWritableState> dtls_writable_states_;

  std::map<uint32_t, std::vector<LoggedFrameDecoded>> decoded_frames_;

  std::vector<LoggedIceCandidatePairConfig> ice_candidate_pair_configs_;
  std::vector<LoggedIceCandidatePairEvent> ice_candidate_pair_events_;

  std::vector<LoggedAudioRecvConfig> audio_recv_configs_;
  std::vector<LoggedAudioSendConfig> audio_send_configs_;
  std::vector<LoggedVideoRecvConfig> video_recv_configs_;
  std::vector<LoggedVideoSendConfig> video_send_configs_;

  std::vector<LoggedGenericPacketReceived> generic_packets_received_;
  std::vector<LoggedGenericPacketSent> generic_packets_sent_;
  std::vector<LoggedGenericAckReceived> generic_acks_received_;

  std::vector<LoggedRouteChangeEvent> route_change_events_;
  std::vector<LoggedRemoteEstimateEvent> remote_estimate_events_;

  std::vector<uint8_t> last_incoming_rtcp_packet_;

  int64_t first_timestamp_;
  int64_t last_timestamp_;

  LogSegment first_log_segment_ =
      LogSegment(0, std::numeric_limits<int64_t>::max());

  // The extension maps are mutable to allow us to insert the default
  // configuration when parsing an RTP header for an unconfigured stream.
  // TODO(terelius): This is only used for the legacy format. Remove once we've
  // fully transitioned to the new format.
  mutable std::map<uint32_t, webrtc::RtpHeaderExtensionMap>
      incoming_rtp_extensions_maps_;
  mutable std::map<uint32_t, webrtc::RtpHeaderExtensionMap>
      outgoing_rtp_extensions_maps_;
};

struct MatchedSendArrivalTimes {
  static constexpr int64_t kNotReceived = -1;

  MatchedSendArrivalTimes(int64_t fb, int64_t tx, int64_t rx, int64_t ps)
      : feedback_arrival_time_ms(fb),
        send_time_ms(tx),
        arrival_time_ms(rx),
        payload_size(ps) {}

  int64_t feedback_arrival_time_ms;
  int64_t send_time_ms;
  int64_t arrival_time_ms;  // kNotReceived for lost packets.
  int64_t payload_size;
};
const std::vector<MatchedSendArrivalTimes> GetNetworkTrace(
    const ParsedRtcEventLog& parsed_log);

}  // namespace webrtc

#endif  // LOGGING_RTC_EVENT_LOG_RTC_EVENT_LOG_PARSER_H_
