/*
 *  Copyright 2020 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/sctp_data_channel.h"

#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "media/sctp/sctp_transport_internal.h"
#include "pc/proxy.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/system/unused.h"
#include "rtc_base/thread.h"

namespace webrtc {

namespace {

static size_t kMaxQueuedReceivedDataBytes = 16 * 1024 * 1024;

static std::atomic<int> g_unique_id{0};

int GenerateUniqueId() {
  return ++g_unique_id;
}

// Define proxy for DataChannelInterface.
BEGIN_PROXY_MAP(DataChannel)
PROXY_PRIMARY_THREAD_DESTRUCTOR()
BYPASS_PROXY_METHOD1(void, RegisterObserver, DataChannelObserver*)
BYPASS_PROXY_METHOD0(void, UnregisterObserver)
BYPASS_PROXY_CONSTMETHOD0(std::string, label)
BYPASS_PROXY_CONSTMETHOD0(bool, reliable)
BYPASS_PROXY_CONSTMETHOD0(bool, ordered)
BYPASS_PROXY_CONSTMETHOD0(uint16_t, maxRetransmitTime)
BYPASS_PROXY_CONSTMETHOD0(uint16_t, maxRetransmits)
BYPASS_PROXY_CONSTMETHOD0(absl::optional<int>, maxRetransmitsOpt)
BYPASS_PROXY_CONSTMETHOD0(absl::optional<int>, maxPacketLifeTime)
BYPASS_PROXY_CONSTMETHOD0(std::string, protocol)
BYPASS_PROXY_CONSTMETHOD0(bool, negotiated)
// Can't bypass the proxy since the id may change.
PROXY_SECONDARY_CONSTMETHOD0(int, id)
BYPASS_PROXY_CONSTMETHOD0(Priority, priority)
BYPASS_PROXY_CONSTMETHOD0(DataState, state)
BYPASS_PROXY_CONSTMETHOD0(RTCError, error)
PROXY_SECONDARY_CONSTMETHOD0(uint32_t, messages_sent)
PROXY_SECONDARY_CONSTMETHOD0(uint64_t, bytes_sent)
PROXY_SECONDARY_CONSTMETHOD0(uint32_t, messages_received)
PROXY_SECONDARY_CONSTMETHOD0(uint64_t, bytes_received)
PROXY_SECONDARY_CONSTMETHOD0(uint64_t, buffered_amount)
PROXY_SECONDARY_METHOD0(void, Close)
PROXY_SECONDARY_METHOD1(bool, Send, const DataBuffer&)
BYPASS_PROXY_METHOD2(void,
                     SendAsync,
                     DataBuffer,
                     absl::AnyInvocable<void(RTCError) &&>)
END_PROXY_MAP(DataChannel)
}  // namespace

InternalDataChannelInit::InternalDataChannelInit(const DataChannelInit& base)
    : DataChannelInit(base), open_handshake_role(kOpener) {
  // If the channel is externally negotiated, do not send the OPEN message.
  if (base.negotiated) {
    open_handshake_role = kNone;
  } else {
    // Datachannel is externally negotiated. Ignore the id value.
    // Specified in createDataChannel, WebRTC spec section 6.1 bullet 13.
    id = -1;
  }
  // Backwards compatibility: If maxRetransmits or maxRetransmitTime
  // are negative, the feature is not enabled.
  // Values are clamped to a 16bit range.
  if (maxRetransmits) {
    if (*maxRetransmits < 0) {
      RTC_LOG(LS_ERROR)
          << "Accepting maxRetransmits < 0 for backwards compatibility";
      maxRetransmits = absl::nullopt;
    } else if (*maxRetransmits > std::numeric_limits<uint16_t>::max()) {
      maxRetransmits = std::numeric_limits<uint16_t>::max();
    }
  }

  if (maxRetransmitTime) {
    if (*maxRetransmitTime < 0) {
      RTC_LOG(LS_ERROR)
          << "Accepting maxRetransmitTime < 0 for backwards compatibility";
      maxRetransmitTime = absl::nullopt;
    } else if (*maxRetransmitTime > std::numeric_limits<uint16_t>::max()) {
      maxRetransmitTime = std::numeric_limits<uint16_t>::max();
    }
  }
}

bool InternalDataChannelInit::IsValid() const {
  if (id < -1)
    return false;

  if (maxRetransmits.has_value() && maxRetransmits.value() < 0)
    return false;

  if (maxRetransmitTime.has_value() && maxRetransmitTime.value() < 0)
    return false;

  // Only one of these can be set.
  if (maxRetransmits.has_value() && maxRetransmitTime.has_value())
    return false;

  return true;
}

StreamId SctpSidAllocator::AllocateSid(rtc::SSLRole role) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  int potential_sid = (role == rtc::SSL_CLIENT) ? 0 : 1;
  while (potential_sid <= static_cast<int>(cricket::kMaxSctpSid)) {
    StreamId sid(potential_sid);
    if (used_sids_.insert(sid).second)
      return sid;
    potential_sid += 2;
  }
  RTC_LOG(LS_ERROR) << "SCTP sid allocation pool exhausted.";
  return StreamId();
}

bool SctpSidAllocator::ReserveSid(StreamId sid) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  if (!sid.HasValue() || sid.stream_id_int() > cricket::kMaxSctpSid)
    return false;
  return used_sids_.insert(sid).second;
}

void SctpSidAllocator::ReleaseSid(StreamId sid) {
  RTC_DCHECK_RUN_ON(&sequence_checker_);
  used_sids_.erase(sid);
}

// A DataChannelObserver implementation that offers backwards compatibility with
// implementations that aren't yet ready to be called back on the network
// thread. This implementation posts events to the signaling thread where
// events are delivered.
// In the class, and together with the `SctpDataChannel` implementation, there's
// special handling for the `state()` property whereby if that property is
// queried on the channel object while inside an event callback, we return
// the state that was active at the time the event was issued. This is to avoid
// a problem with calling the `state()` getter on the proxy, which would do
// a blocking call to the network thread, effectively flushing operations on
// the network thread that could cause the state to change and eventually return
// a misleading or arguably, wrong, state value to the callback implementation.
// As a future improvement to the ObserverAdapter, we could do the same for
// other properties that need to be read on the network thread. Eventually
// all implementations should expect to be called on the network thread though
// and the ObserverAdapter no longer be necessary.
class SctpDataChannel::ObserverAdapter : public DataChannelObserver {
 public:
  explicit ObserverAdapter(
      SctpDataChannel* channel,
      rtc::scoped_refptr<PendingTaskSafetyFlag> signaling_safety)
      : channel_(channel), signaling_safety_(std::move(signaling_safety)) {}

  bool IsInsideCallback() const {
    RTC_DCHECK_RUN_ON(signaling_thread());
    return cached_getters_ != nullptr;
  }

  DataChannelInterface::DataState cached_state() const {
    RTC_DCHECK_RUN_ON(signaling_thread());
    RTC_DCHECK(IsInsideCallback());
    return cached_getters_->state();
  }

  RTCError cached_error() const {
    RTC_DCHECK_RUN_ON(signaling_thread());
    RTC_DCHECK(IsInsideCallback());
    return cached_getters_->error();
  }

  void SetDelegate(DataChannelObserver* delegate) {
    RTC_DCHECK_RUN_ON(signaling_thread());
    delegate_ = delegate;
    safety_.reset(PendingTaskSafetyFlag::CreateDetached());
  }

  static void DeleteOnSignalingThread(
      std::unique_ptr<ObserverAdapter> observer) {
    auto* signaling_thread = observer->signaling_thread();
    if (!signaling_thread->IsCurrent())
      signaling_thread->PostTask([observer = std::move(observer)]() {});
  }

 private:
  class CachedGetters {
   public:
    explicit CachedGetters(ObserverAdapter* adapter)
        : adapter_(adapter),
          cached_state_(adapter_->channel_->state()),
          cached_error_(adapter_->channel_->error()) {
      RTC_DCHECK_RUN_ON(adapter->network_thread());
    }

    ~CachedGetters() {
      if (!was_dropped_) {
        RTC_DCHECK_RUN_ON(adapter_->signaling_thread());
        RTC_DCHECK_EQ(adapter_->cached_getters_, this);
        adapter_->cached_getters_ = nullptr;
      }
    }

    bool PrepareForCallback() {
      RTC_DCHECK_RUN_ON(adapter_->signaling_thread());
      RTC_DCHECK(was_dropped_);
      was_dropped_ = false;
      adapter_->cached_getters_ = this;
      return adapter_->delegate_ && adapter_->signaling_safety_->alive();
    }

    RTCError error() { return cached_error_; }
    DataChannelInterface::DataState state() { return cached_state_; }

   private:
    ObserverAdapter* const adapter_;
    bool was_dropped_ = true;
    const DataChannelInterface::DataState cached_state_;
    const RTCError cached_error_;
  };

  void OnStateChange() override {
    RTC_DCHECK_RUN_ON(network_thread());
    signaling_thread()->PostTask(
        SafeTask(safety_.flag(),
                 [this, cached_state = std::make_unique<CachedGetters>(this)] {
                   RTC_DCHECK_RUN_ON(signaling_thread());
                   if (cached_state->PrepareForCallback())
                     delegate_->OnStateChange();
                 }));
  }

  void OnMessage(const DataBuffer& buffer) override {
    RTC_DCHECK_RUN_ON(network_thread());
    signaling_thread()->PostTask(SafeTask(
        safety_.flag(), [this, buffer = buffer,
                         cached_state = std::make_unique<CachedGetters>(this)] {
          RTC_DCHECK_RUN_ON(signaling_thread());
          if (cached_state->PrepareForCallback())
            delegate_->OnMessage(buffer);
        }));
  }

  void OnBufferedAmountChange(uint64_t sent_data_size) override {
    RTC_DCHECK_RUN_ON(network_thread());
    signaling_thread()->PostTask(SafeTask(
        safety_.flag(), [this, sent_data_size,
                         cached_state = std::make_unique<CachedGetters>(this)] {
          RTC_DCHECK_RUN_ON(signaling_thread());
          if (cached_state->PrepareForCallback())
            delegate_->OnBufferedAmountChange(sent_data_size);
        }));
  }

  bool IsOkToCallOnTheNetworkThread() override { return true; }

  rtc::Thread* signaling_thread() const { return signaling_thread_; }
  rtc::Thread* network_thread() const { return channel_->network_thread_; }

  DataChannelObserver* delegate_ RTC_GUARDED_BY(signaling_thread()) = nullptr;
  SctpDataChannel* const channel_;
  // Make sure to keep our own signaling_thread_ pointer to avoid dereferencing
  // `channel_` in the `RTC_DCHECK_RUN_ON` checks on the signaling thread.
  rtc::Thread* const signaling_thread_{channel_->signaling_thread_};
  ScopedTaskSafety safety_;
  rtc::scoped_refptr<PendingTaskSafetyFlag> signaling_safety_;
  CachedGetters* cached_getters_ RTC_GUARDED_BY(signaling_thread()) = nullptr;
};

// static
rtc::scoped_refptr<SctpDataChannel> SctpDataChannel::Create(
    rtc::WeakPtr<SctpDataChannelControllerInterface> controller,
    const std::string& label,
    bool connected_to_transport,
    const InternalDataChannelInit& config,
    rtc::Thread* signaling_thread,
    rtc::Thread* network_thread) {
  RTC_DCHECK(config.IsValid());
  return rtc::make_ref_counted<SctpDataChannel>(
      config, std::move(controller), label, connected_to_transport,
      signaling_thread, network_thread);
}

// static
rtc::scoped_refptr<DataChannelInterface> SctpDataChannel::CreateProxy(
    rtc::scoped_refptr<SctpDataChannel> channel,
    rtc::scoped_refptr<PendingTaskSafetyFlag> signaling_safety) {
  // Copy thread params to local variables before `std::move()`.
  auto* signaling_thread = channel->signaling_thread_;
  auto* network_thread = channel->network_thread_;
  channel->observer_adapter_ = std::make_unique<ObserverAdapter>(
      channel.get(), std::move(signaling_safety));
  return DataChannelProxy::Create(signaling_thread, network_thread,
                                  std::move(channel));
}

SctpDataChannel::SctpDataChannel(
    const InternalDataChannelInit& config,
    rtc::WeakPtr<SctpDataChannelControllerInterface> controller,
    const std::string& label,
    bool connected_to_transport,
    rtc::Thread* signaling_thread,
    rtc::Thread* network_thread)
    : signaling_thread_(signaling_thread),
      network_thread_(network_thread),
      id_n_(config.id),
      internal_id_(GenerateUniqueId()),
      label_(label),
      protocol_(config.protocol),
      max_retransmit_time_(config.maxRetransmitTime),
      max_retransmits_(config.maxRetransmits),
      priority_(config.priority),
      negotiated_(config.negotiated),
      ordered_(config.ordered),
      observer_(nullptr),
      controller_(std::move(controller)) {
  RTC_DCHECK_RUN_ON(network_thread_);
  // Since we constructed on the network thread we can't (yet) check the
  // `controller_` pointer since doing so will trigger a thread check.
  RTC_UNUSED(network_thread_);
  RTC_DCHECK(config.IsValid());

  if (connected_to_transport)
    network_safety_->SetAlive();

  switch (config.open_handshake_role) {
    case InternalDataChannelInit::kNone:  // pre-negotiated
      handshake_state_ = kHandshakeReady;
      break;
    case InternalDataChannelInit::kOpener:
      handshake_state_ = kHandshakeShouldSendOpen;
      break;
    case InternalDataChannelInit::kAcker:
      handshake_state_ = kHandshakeShouldSendAck;
      break;
  }
}

SctpDataChannel::~SctpDataChannel() {
  if (observer_adapter_)
    ObserverAdapter::DeleteOnSignalingThread(std::move(observer_adapter_));
}

void SctpDataChannel::RegisterObserver(DataChannelObserver* observer) {
  // Note: at this point, we do not know on which thread we're being called
  // from since this method bypasses the proxy. On Android in particular,
  // registration methods are called from unknown threads.

  // Check if we should set up an observer adapter that will make sure that
  // callbacks are delivered on the signaling thread rather than directly
  // on the network thread.
  const auto* current_thread = rtc::Thread::Current();
  // TODO(webrtc:11547): Eventually all DataChannelObserver implementations
  // should be called on the network thread and IsOkToCallOnTheNetworkThread().
  if (!observer->IsOkToCallOnTheNetworkThread()) {
    RTC_LOG(LS_WARNING) << "DataChannelObserver - adapter needed";
    auto prepare_observer = [&]() {
      RTC_DCHECK(observer_adapter_) << "CreateProxy hasn't been called";
      observer_adapter_->SetDelegate(observer);
      return observer_adapter_.get();
    };
    // Instantiate the adapter in the right context and then substitute the
    // observer pointer the SctpDataChannel will call back on, with the adapter.
    if (signaling_thread_ == current_thread) {
      observer = prepare_observer();
    } else {
      observer = signaling_thread_->BlockingCall(std::move(prepare_observer));
    }
  }

  // Now do the observer registration on the network thread. In the common case,
  // we'll do this asynchronously via `PostTask()`. For that reason we grab
  // a reference to ourselves while the task is in flight. We can't use
  // `SafeTask(network_safety_, ...)` for this since we can't assume that we
  // have a transport (network_safety_ represents the transport connection).
  rtc::scoped_refptr<SctpDataChannel> me(this);
  auto register_observer = [me = std::move(me), observer = observer] {
    RTC_DCHECK_RUN_ON(me->network_thread_);
    me->observer_ = observer;
    me->DeliverQueuedReceivedData();
  };

  if (network_thread_ == current_thread) {
    register_observer();
  } else {
    network_thread_->BlockingCall(std::move(register_observer));
  }
}

void SctpDataChannel::UnregisterObserver() {
  // Note: As with `RegisterObserver`, the proxy is being bypassed.
  const auto* current_thread = rtc::Thread::Current();
  // Callers must not be invoking the unregistration from the network thread
  // (assuming a multi-threaded environment where we have a dedicated network
  // thread). That would indicate non-network related work happening on the
  // network thread or that unregistration is being done from within a callback
  // (without unwinding the stack, which is a requirement).
  // The network thread is not allowed to make blocking calls to the signaling
  // thread, so that would blow up if attempted. Since we support an adapter
  // for observers that are not safe to call on the network thread, we do
  // need to check+free it on the signaling thread.
  RTC_DCHECK(current_thread != network_thread_ ||
             network_thread_ == signaling_thread_);

  auto unregister_observer = [&] {
    RTC_DCHECK_RUN_ON(network_thread_);
    observer_ = nullptr;
  };

  if (current_thread == network_thread_) {
    unregister_observer();
  } else {
    network_thread_->BlockingCall(std::move(unregister_observer));
  }

  auto clear_observer = [&]() {
    if (observer_adapter_)
      observer_adapter_->SetDelegate(nullptr);
  };

  if (current_thread != signaling_thread_) {
    signaling_thread_->BlockingCall(std::move(clear_observer));
  } else {
    clear_observer();
  }
}

std::string SctpDataChannel::label() const {
  return label_;
}

bool SctpDataChannel::reliable() const {
  // May be called on any thread.
  return !max_retransmits_ && !max_retransmit_time_;
}

bool SctpDataChannel::ordered() const {
  return ordered_;
}

uint16_t SctpDataChannel::maxRetransmitTime() const {
  return max_retransmit_time_ ? *max_retransmit_time_
                              : static_cast<uint16_t>(-1);
}

uint16_t SctpDataChannel::maxRetransmits() const {
  return max_retransmits_ ? *max_retransmits_ : static_cast<uint16_t>(-1);
}

absl::optional<int> SctpDataChannel::maxPacketLifeTime() const {
  return max_retransmit_time_;
}

absl::optional<int> SctpDataChannel::maxRetransmitsOpt() const {
  return max_retransmits_;
}

std::string SctpDataChannel::protocol() const {
  return protocol_;
}

bool SctpDataChannel::negotiated() const {
  return negotiated_;
}

int SctpDataChannel::id() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return id_n_.stream_id_int();
}

Priority SctpDataChannel::priority() const {
  return priority_ ? *priority_ : Priority::kLow;
}

uint64_t SctpDataChannel::buffered_amount() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return queued_send_data_.byte_count();
}

void SctpDataChannel::Close() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (state_ == kClosing || state_ == kClosed)
    return;
  SetState(kClosing);
  // Will send queued data before beginning the underlying closing procedure.
  UpdateState();
}

SctpDataChannel::DataState SctpDataChannel::state() const {
  // Note: The proxy is bypassed for the `state()` accessor. This is to allow
  // observer callbacks to query what the new state is from within a state
  // update notification without having to do a blocking call to the network
  // thread from within a callback. This also makes it so that the returned
  // state is guaranteed to be the new state that provoked the state change
  // notification, whereby a blocking call to the network thread might end up
  // getting put behind other messages on the network thread and eventually
  // fetch a different state value (since pending messages might cause the
  // state to change in the meantime).
  const auto* current_thread = rtc::Thread::Current();
  if (current_thread == signaling_thread_ && observer_adapter_ &&
      observer_adapter_->IsInsideCallback()) {
    return observer_adapter_->cached_state();
  }

  auto return_state = [&] {
    RTC_DCHECK_RUN_ON(network_thread_);
    return state_;
  };

  return current_thread == network_thread_
             ? return_state()
             : network_thread_->BlockingCall(std::move(return_state));
}

RTCError SctpDataChannel::error() const {
  const auto* current_thread = rtc::Thread::Current();
  if (current_thread == signaling_thread_ && observer_adapter_ &&
      observer_adapter_->IsInsideCallback()) {
    return observer_adapter_->cached_error();
  }

  auto return_error = [&] {
    RTC_DCHECK_RUN_ON(network_thread_);
    return error_;
  };

  return current_thread == network_thread_
             ? return_error()
             : network_thread_->BlockingCall(std::move(return_error));
}

uint32_t SctpDataChannel::messages_sent() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return messages_sent_;
}

uint64_t SctpDataChannel::bytes_sent() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return bytes_sent_;
}

uint32_t SctpDataChannel::messages_received() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return messages_received_;
}

uint64_t SctpDataChannel::bytes_received() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  return bytes_received_;
}

bool SctpDataChannel::Send(const DataBuffer& buffer) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTCError err = SendImpl(buffer);
  if (err.type() == RTCErrorType::INVALID_STATE ||
      err.type() == RTCErrorType::RESOURCE_EXHAUSTED) {
    return false;
  }

  // Always return true for SCTP DataChannel per the spec.
  return true;
}

// RTC_RUN_ON(network_thread_);
RTCError SctpDataChannel::SendImpl(DataBuffer buffer) {
  if (state_ != kOpen) {
    error_ = RTCError(RTCErrorType::INVALID_STATE);
    return error_;
  }

  // If the queue is non-empty, we're waiting for SignalReadyToSend,
  // so just add to the end of the queue and keep waiting.
  if (!queued_send_data_.Empty()) {
    error_ = QueueSendDataMessage(buffer)
                 ? RTCError::OK()
                 : RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
    return error_;
  }

  return SendDataMessage(buffer, true);
}

void SctpDataChannel::SendAsync(
    DataBuffer buffer,
    absl::AnyInvocable<void(RTCError) &&> on_complete) {
  // Note: at this point, we do not know on which thread we're being called
  // since this method bypasses the proxy. On Android the thread might be VM
  // owned, on other platforms it might be the signaling thread, or in Chrome
  // it can be the JS thread. We also don't know if it's consistently the same
  // thread. So we always post to the network thread (even if the current thread
  // might be the network thread - in theory a call could even come from within
  // the `on_complete` callback).
  network_thread_->PostTask(SafeTask(
      network_safety_, [this, buffer = std::move(buffer),
                        on_complete = std::move(on_complete)]() mutable {
        RTC_DCHECK_RUN_ON(network_thread_);
        RTCError err = SendImpl(std::move(buffer));
        if (on_complete)
          std::move(on_complete)(err);
      }));
}

void SctpDataChannel::SetSctpSid_n(StreamId sid) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(!id_n_.HasValue());
  RTC_DCHECK(sid.HasValue());
  RTC_DCHECK_NE(handshake_state_, kHandshakeWaitingForAck);
  RTC_DCHECK_EQ(state_, kConnecting);
  id_n_ = sid;
}

void SctpDataChannel::OnClosingProcedureStartedRemotely() {
  RTC_DCHECK_RUN_ON(network_thread_);
  if (state_ != kClosing && state_ != kClosed) {
    // Don't bother sending queued data since the side that initiated the
    // closure wouldn't receive it anyway. See crbug.com/559394 for a lengthy
    // discussion about this.
    queued_send_data_.Clear();
    queued_control_data_.Clear();
    // Just need to change state to kClosing, SctpTransport will handle the
    // rest of the closing procedure and OnClosingProcedureComplete will be
    // called later.
    started_closing_procedure_ = true;
    SetState(kClosing);
  }
}

void SctpDataChannel::OnClosingProcedureComplete() {
  RTC_DCHECK_RUN_ON(network_thread_);
  // If the closing procedure is complete, we should have finished sending
  // all pending data and transitioned to kClosing already.
  RTC_DCHECK_EQ(state_, kClosing);
  RTC_DCHECK(queued_send_data_.Empty());
  SetState(kClosed);
}

void SctpDataChannel::OnTransportChannelCreated() {
  RTC_DCHECK_RUN_ON(network_thread_);
  network_safety_->SetAlive();
}

void SctpDataChannel::OnTransportChannelClosed(RTCError error) {
  RTC_DCHECK_RUN_ON(network_thread_);
  // The SctpTransport is unusable, which could come from multiple reasons:
  // - the SCTP m= section was rejected
  // - the DTLS transport is closed
  // - the SCTP transport is closed
  CloseAbruptlyWithError(std::move(error));
}

DataChannelStats SctpDataChannel::GetStats() const {
  RTC_DCHECK_RUN_ON(network_thread_);
  DataChannelStats stats{internal_id_,        id(),         label(),
                         protocol(),          state(),      messages_sent(),
                         messages_received(), bytes_sent(), bytes_received()};
  return stats;
}

void SctpDataChannel::OnDataReceived(DataMessageType type,
                                     const rtc::CopyOnWriteBuffer& payload) {
  RTC_DCHECK_RUN_ON(network_thread_);

  if (type == DataMessageType::kControl) {
    if (handshake_state_ != kHandshakeWaitingForAck) {
      // Ignore it if we are not expecting an ACK message.
      RTC_LOG(LS_WARNING)
          << "DataChannel received unexpected CONTROL message, sid = "
          << id_n_.stream_id_int();
      return;
    }
    if (ParseDataChannelOpenAckMessage(payload)) {
      // We can send unordered as soon as we receive the ACK message.
      handshake_state_ = kHandshakeReady;
      RTC_LOG(LS_INFO) << "DataChannel received OPEN_ACK message, sid = "
                       << id_n_.stream_id_int();
    } else {
      RTC_LOG(LS_WARNING)
          << "DataChannel failed to parse OPEN_ACK message, sid = "
          << id_n_.stream_id_int();
    }
    return;
  }

  RTC_DCHECK(type == DataMessageType::kBinary ||
             type == DataMessageType::kText);

  RTC_DLOG(LS_VERBOSE) << "DataChannel received DATA message, sid = "
                       << id_n_.stream_id_int();
  // We can send unordered as soon as we receive any DATA message since the
  // remote side must have received the OPEN (and old clients do not send
  // OPEN_ACK).
  if (handshake_state_ == kHandshakeWaitingForAck) {
    handshake_state_ = kHandshakeReady;
  }

  bool binary = (type == DataMessageType::kBinary);
  auto buffer = std::make_unique<DataBuffer>(payload, binary);
  if (state_ == kOpen && observer_) {
    ++messages_received_;
    bytes_received_ += buffer->size();
    observer_->OnMessage(*buffer.get());
  } else {
    if (queued_received_data_.byte_count() + payload.size() >
        kMaxQueuedReceivedDataBytes) {
      RTC_LOG(LS_ERROR) << "Queued received data exceeds the max buffer size.";

      queued_received_data_.Clear();
      CloseAbruptlyWithError(
          RTCError(RTCErrorType::RESOURCE_EXHAUSTED,
                   "Queued received data exceeds the max buffer size."));

      return;
    }
    queued_received_data_.PushBack(std::move(buffer));
  }
}

void SctpDataChannel::OnTransportReady() {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTC_DCHECK(connected_to_transport());
  RTC_DCHECK(id_n_.HasValue());

  SendQueuedControlMessages();
  SendQueuedDataMessages();

  UpdateState();
}

void SctpDataChannel::CloseAbruptlyWithError(RTCError error) {
  RTC_DCHECK_RUN_ON(network_thread_);

  if (state_ == kClosed) {
    return;
  }

  network_safety_->SetNotAlive();

  // Closing abruptly means any queued data gets thrown away.
  queued_send_data_.Clear();
  queued_control_data_.Clear();

  // Still go to "kClosing" before "kClosed", since observers may be expecting
  // that.
  SetState(kClosing);
  error_ = std::move(error);
  SetState(kClosed);
}

void SctpDataChannel::CloseAbruptlyWithDataChannelFailure(
    const std::string& message) {
  RTC_DCHECK_RUN_ON(network_thread_);
  RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, message);
  error.set_error_detail(RTCErrorDetailType::DATA_CHANNEL_FAILURE);
  CloseAbruptlyWithError(std::move(error));
}

// RTC_RUN_ON(network_thread_).
void SctpDataChannel::UpdateState() {
  // UpdateState determines what to do from a few state variables. Include
  // all conditions required for each state transition here for
  // clarity. OnTransportReady(true) will send any queued data and then invoke
  // UpdateState().

  switch (state_) {
    case kConnecting: {
      if (connected_to_transport() && controller_) {
        if (handshake_state_ == kHandshakeShouldSendOpen) {
          rtc::CopyOnWriteBuffer payload;
          WriteDataChannelOpenMessage(label_, protocol_, priority_, ordered_,
                                      max_retransmits_, max_retransmit_time_,
                                      &payload);
          SendControlMessage(payload);
        } else if (handshake_state_ == kHandshakeShouldSendAck) {
          rtc::CopyOnWriteBuffer payload;
          WriteDataChannelOpenAckMessage(&payload);
          SendControlMessage(payload);
        }
        if (handshake_state_ == kHandshakeReady ||
            handshake_state_ == kHandshakeWaitingForAck) {
          SetState(kOpen);
          // If we have received buffers before the channel got writable.
          // Deliver them now.
          DeliverQueuedReceivedData();
        }
      } else {
        RTC_DCHECK(!id_n_.HasValue());
      }
      break;
    }
    case kOpen: {
      break;
    }
    case kClosing: {
      if (connected_to_transport() && controller_) {
        // Wait for all queued data to be sent before beginning the closing
        // procedure.
        if (queued_send_data_.Empty() && queued_control_data_.Empty()) {
          // For SCTP data channels, we need to wait for the closing procedure
          // to complete; after calling RemoveSctpDataStream,
          // OnClosingProcedureComplete will end up called asynchronously
          // afterwards.
          if (!started_closing_procedure_ && id_n_.HasValue()) {
            started_closing_procedure_ = true;
            controller_->RemoveSctpDataStream(id_n_);
          }
        }
      } else {
        // When we're not connected to a transport, we'll transition
        // directly to the `kClosed` state from here.
        queued_send_data_.Clear();
        queued_control_data_.Clear();
        SetState(kClosed);
      }
      break;
    }
    case kClosed:
      break;
  }
}

// RTC_RUN_ON(network_thread_).
void SctpDataChannel::SetState(DataState state) {
  if (state_ == state) {
    return;
  }

  state_ = state;
  if (observer_) {
    observer_->OnStateChange();
  }

  if (controller_)
    controller_->OnChannelStateChanged(this, state_);
}

// RTC_RUN_ON(network_thread_).
void SctpDataChannel::DeliverQueuedReceivedData() {
  if (!observer_ || state_ != kOpen) {
    return;
  }

  while (!queued_received_data_.Empty()) {
    std::unique_ptr<DataBuffer> buffer = queued_received_data_.PopFront();
    ++messages_received_;
    bytes_received_ += buffer->size();
    observer_->OnMessage(*buffer);
  }
}

// RTC_RUN_ON(network_thread_).
void SctpDataChannel::SendQueuedDataMessages() {
  if (queued_send_data_.Empty()) {
    return;
  }

  RTC_DCHECK(state_ == kOpen || state_ == kClosing);

  while (!queued_send_data_.Empty()) {
    std::unique_ptr<DataBuffer> buffer = queued_send_data_.PopFront();
    if (!SendDataMessage(*buffer, false).ok()) {
      // Return the message to the front of the queue if sending is aborted.
      queued_send_data_.PushFront(std::move(buffer));
      break;
    }
  }
}

// RTC_RUN_ON(network_thread_).
RTCError SctpDataChannel::SendDataMessage(const DataBuffer& buffer,
                                          bool queue_if_blocked) {
  SendDataParams send_params;
  if (!controller_) {
    error_ = RTCError(RTCErrorType::INVALID_STATE);
    return error_;
  }

  send_params.ordered = ordered_;
  // Send as ordered if it is still going through OPEN/ACK signaling.
  if (handshake_state_ != kHandshakeReady && !ordered_) {
    send_params.ordered = true;
    RTC_DLOG(LS_VERBOSE)
        << "Sending data as ordered for unordered DataChannel "
           "because the OPEN_ACK message has not been received.";
  }

  send_params.max_rtx_count = max_retransmits_;
  send_params.max_rtx_ms = max_retransmit_time_;
  send_params.type =
      buffer.binary ? DataMessageType::kBinary : DataMessageType::kText;

  error_ = controller_->SendData(id_n_, send_params, buffer.data);
  if (error_.ok()) {
    ++messages_sent_;
    bytes_sent_ += buffer.size();

    if (observer_ && buffer.size() > 0) {
      observer_->OnBufferedAmountChange(buffer.size());
    }
    return error_;
  }

  if (error_.type() == RTCErrorType::RESOURCE_EXHAUSTED) {
    if (!queue_if_blocked)
      return error_;

    if (QueueSendDataMessage(buffer)) {
      error_ = RTCError::OK();
      return error_;
    }
  }
  // Close the channel if the error is not SDR_BLOCK, or if queuing the
  // message failed.
  RTC_LOG(LS_ERROR) << "Closing the DataChannel due to a failure to send data, "
                       "send_result = "
                    << ToString(error_.type()) << ":" << error_.message();
  CloseAbruptlyWithError(
      RTCError(RTCErrorType::NETWORK_ERROR, "Failure to send data"));

  return error_;
}

// RTC_RUN_ON(network_thread_).
bool SctpDataChannel::QueueSendDataMessage(const DataBuffer& buffer) {
  size_t start_buffered_amount = queued_send_data_.byte_count();
  if (start_buffered_amount + buffer.size() >
      DataChannelInterface::MaxSendQueueSize()) {
    RTC_LOG(LS_ERROR) << "Can't buffer any more data for the data channel.";
    error_ = RTCError(RTCErrorType::RESOURCE_EXHAUSTED);
    return false;
  }
  queued_send_data_.PushBack(std::make_unique<DataBuffer>(buffer));
  return true;
}

// RTC_RUN_ON(network_thread_).
void SctpDataChannel::SendQueuedControlMessages() {
  PacketQueue control_packets;
  control_packets.Swap(&queued_control_data_);

  while (!control_packets.Empty()) {
    std::unique_ptr<DataBuffer> buf = control_packets.PopFront();
    SendControlMessage(buf->data);
  }
}

// RTC_RUN_ON(network_thread_).
bool SctpDataChannel::SendControlMessage(const rtc::CopyOnWriteBuffer& buffer) {
  RTC_DCHECK(connected_to_transport());
  RTC_DCHECK(id_n_.HasValue());
  RTC_DCHECK(controller_);

  bool is_open_message = handshake_state_ == kHandshakeShouldSendOpen;
  RTC_DCHECK(!is_open_message || !negotiated_);

  SendDataParams send_params;
  // Send data as ordered before we receive any message from the remote peer to
  // make sure the remote peer will not receive any data before it receives the
  // OPEN message.
  send_params.ordered = ordered_ || is_open_message;
  send_params.type = DataMessageType::kControl;

  RTCError err = controller_->SendData(id_n_, send_params, buffer);
  if (err.ok()) {
    RTC_DLOG(LS_VERBOSE) << "Sent CONTROL message on channel "
                         << id_n_.stream_id_int();

    if (handshake_state_ == kHandshakeShouldSendAck) {
      handshake_state_ = kHandshakeReady;
    } else if (handshake_state_ == kHandshakeShouldSendOpen) {
      handshake_state_ = kHandshakeWaitingForAck;
    }
  } else if (err.type() == RTCErrorType::RESOURCE_EXHAUSTED) {
    queued_control_data_.PushBack(std::make_unique<DataBuffer>(buffer, true));
  } else {
    RTC_LOG(LS_ERROR) << "Closing the DataChannel due to a failure to send"
                         " the CONTROL message, send_result = "
                      << ToString(err.type());
    err.set_message("Failed to send a CONTROL message");
    CloseAbruptlyWithError(err);
  }
  return err.ok();
}

// static
void SctpDataChannel::ResetInternalIdAllocatorForTesting(int new_value) {
  g_unique_id = new_value;
}

}  // namespace webrtc
