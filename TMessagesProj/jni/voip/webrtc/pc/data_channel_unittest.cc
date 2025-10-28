/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <stdint.h>
#include <string.h>

#include <memory>
#include <string>
#include <vector>

#include "api/data_channel_interface.h"
#include "api/rtc_error.h"
#include "api/scoped_refptr.h"
#include "api/transport/data_channel_transport_interface.h"
#include "media/base/media_channel.h"
#include "media/sctp/sctp_transport_internal.h"
#include "pc/sctp_data_channel.h"
#include "pc/sctp_utils.h"
#include "pc/test/fake_data_channel_controller.h"
#include "rtc_base/copy_on_write_buffer.h"
#include "rtc_base/gunit.h"
#include "rtc_base/null_socket_server.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/thread.h"
#include "test/gtest.h"
#include "test/run_loop.h"

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)
#include "test/testsupport/rtc_expect_death.h"
#endif  // RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

namespace webrtc {

namespace {

static constexpr int kDefaultTimeout = 10000;

class FakeDataChannelObserver : public DataChannelObserver {
 public:
  FakeDataChannelObserver() { RTC_DCHECK(!IsOkToCallOnTheNetworkThread()); }

  void OnStateChange() override { ++on_state_change_count_; }

  void OnBufferedAmountChange(uint64_t previous_amount) override {
    ++on_buffered_amount_change_count_;
  }

  void OnMessage(const DataBuffer& buffer) override { ++messages_received_; }

  size_t messages_received() const { return messages_received_; }

  void ResetOnStateChangeCount() { on_state_change_count_ = 0; }

  void ResetOnBufferedAmountChangeCount() {
    on_buffered_amount_change_count_ = 0;
  }

  size_t on_state_change_count() const { return on_state_change_count_; }

  size_t on_buffered_amount_change_count() const {
    return on_buffered_amount_change_count_;
  }

 private:
  size_t messages_received_ = 0u;
  size_t on_state_change_count_ = 0u;
  size_t on_buffered_amount_change_count_ = 0u;
};

class SctpDataChannelTest : public ::testing::Test {
 protected:
  SctpDataChannelTest()
      : network_thread_(std::make_unique<rtc::NullSocketServer>()),
        controller_(new FakeDataChannelController(&network_thread_)) {
    network_thread_.Start();
    inner_channel_ = controller_->CreateDataChannel("test", init_);
    channel_ = SctpDataChannel::CreateProxy(inner_channel_, signaling_safety_);
  }
  ~SctpDataChannelTest() override {
    run_loop_.Flush();
    signaling_safety_->SetNotAlive();
    inner_channel_ = nullptr;
    channel_ = nullptr;
    controller_.reset();
    observer_.reset();
    network_thread_.Stop();
  }

  void SetChannelReady() {
    controller_->set_transport_available(true);
    StreamId sid(0);
    network_thread_.BlockingCall([&]() {
      RTC_DCHECK_RUN_ON(&network_thread_);
      if (!inner_channel_->sid_n().HasValue()) {
        inner_channel_->SetSctpSid_n(sid);
        controller_->AddSctpDataStream(sid);
      }
      inner_channel_->OnTransportChannelCreated();
    });
    controller_->set_ready_to_send(true);
    run_loop_.Flush();
  }

  // TODO(bugs.webrtc.org/11547): This mirrors what the DataChannelController
  // currently does when assigning stream ids to a channel. Right now the sid
  // in the SctpDataChannel code is (still) tied to the signaling thread, but
  // the `AddSctpDataStream` operation is a bridge to the transport and needs
  // to run on the network thread.
  void SetChannelSid(const rtc::scoped_refptr<SctpDataChannel>& channel,
                     StreamId sid) {
    RTC_DCHECK(sid.HasValue());
    network_thread_.BlockingCall([&]() {
      channel->SetSctpSid_n(sid);
      controller_->AddSctpDataStream(sid);
    });
  }

  void AddObserver() {
    observer_.reset(new FakeDataChannelObserver());
    channel_->RegisterObserver(observer_.get());
  }

  // Wait for queued up methods to run on the network thread.
  void FlushNetworkThread() {
    RTC_DCHECK_RUN_ON(run_loop_.task_queue());
    network_thread_.BlockingCall([] {});
  }

  // Used to complete pending methods on the network thread
  // that might queue up methods on the signaling (main) thread
  // that are run too.
  void FlushNetworkThreadAndPendingOperations() {
    FlushNetworkThread();
    run_loop_.Flush();
  }

  test::RunLoop run_loop_;
  rtc::Thread network_thread_;
  InternalDataChannelInit init_;
  rtc::scoped_refptr<PendingTaskSafetyFlag> signaling_safety_ =
      PendingTaskSafetyFlag::Create();
  std::unique_ptr<FakeDataChannelController> controller_;
  std::unique_ptr<FakeDataChannelObserver> observer_;
  rtc::scoped_refptr<SctpDataChannel> inner_channel_;
  rtc::scoped_refptr<DataChannelInterface> channel_;
};

TEST_F(SctpDataChannelTest, VerifyConfigurationGetters) {
  EXPECT_EQ(channel_->label(), "test");
  EXPECT_EQ(channel_->protocol(), init_.protocol);

  // Note that the `init_.reliable` field is deprecated, so we directly set
  // it here to match spec behavior for purposes of checking the `reliable()`
  // getter.
  init_.reliable = (!init_.maxRetransmits && !init_.maxRetransmitTime);
  EXPECT_EQ(channel_->reliable(), init_.reliable);
  EXPECT_EQ(channel_->ordered(), init_.ordered);
  EXPECT_EQ(channel_->negotiated(), init_.negotiated);
  EXPECT_EQ(channel_->priority(), Priority::kLow);
  EXPECT_EQ(channel_->maxRetransmitTime(), static_cast<uint16_t>(-1));
  EXPECT_EQ(channel_->maxPacketLifeTime(), init_.maxRetransmitTime);
  EXPECT_EQ(channel_->maxRetransmits(), static_cast<uint16_t>(-1));
  EXPECT_EQ(channel_->maxRetransmitsOpt(), init_.maxRetransmits);

  // Check the non-const part of the configuration.
  EXPECT_EQ(channel_->id(), init_.id);
  network_thread_.BlockingCall(
      [&]() { EXPECT_EQ(inner_channel_->sid_n(), StreamId()); });

  SetChannelReady();
  EXPECT_EQ(channel_->id(), 0);
  network_thread_.BlockingCall(
      [&]() { EXPECT_EQ(inner_channel_->sid_n(), StreamId(0)); });
}

// Verifies that the data channel is connected to the transport after creation.
TEST_F(SctpDataChannelTest, ConnectedToTransportOnCreated) {
  controller_->set_transport_available(true);
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", init_);
  EXPECT_TRUE(controller_->IsConnected(dc.get()));

  // The sid is not set yet, so it should not have added the streams.
  StreamId sid = network_thread_.BlockingCall([&]() { return dc->sid_n(); });
  EXPECT_FALSE(controller_->IsStreamAdded(sid));

  SetChannelSid(dc, StreamId(0));
  sid = network_thread_.BlockingCall([&]() { return dc->sid_n(); });
  EXPECT_TRUE(controller_->IsStreamAdded(sid));
}

// Tests the state of the data channel.
TEST_F(SctpDataChannelTest, StateTransition) {
  AddObserver();

  EXPECT_EQ(DataChannelInterface::kConnecting, channel_->state());
  EXPECT_EQ(observer_->on_state_change_count(), 0u);
  SetChannelReady();

  EXPECT_EQ(DataChannelInterface::kOpen, channel_->state());
  EXPECT_EQ(observer_->on_state_change_count(), 1u);

  // `Close()` should trigger two state changes, first `kClosing`, then
  // `kClose`.
  channel_->Close();
  // The (simulated) transport close notifications runs on the network thread
  // and posts a completion notification to the signaling (current) thread.
  // Allow that operation to complete before checking the state.
  run_loop_.Flush();
  EXPECT_EQ(DataChannelInterface::kClosed, channel_->state());
  EXPECT_EQ(observer_->on_state_change_count(), 3u);
  EXPECT_TRUE(channel_->error().ok());
  // Verifies that it's disconnected from the transport.
  EXPECT_FALSE(controller_->IsConnected(inner_channel_.get()));
}

// Tests that DataChannel::buffered_amount() is correct after the channel is
// blocked.
TEST_F(SctpDataChannelTest, BufferedAmountWhenBlocked) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  size_t successful_sends = 0;
  auto send_complete = [&](RTCError err) {
    EXPECT_TRUE(err.ok());
    ++successful_sends;
  };
  channel_->SendAsync(buffer, send_complete);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_EQ(channel_->buffered_amount(), 0u);
  size_t successful_send_count = 1;
  EXPECT_EQ(successful_send_count, successful_sends);
  EXPECT_EQ(successful_send_count,
            observer_->on_buffered_amount_change_count());

  controller_->set_send_blocked(true);
  const int number_of_packets = 3;
  for (int i = 0; i < number_of_packets; ++i) {
    channel_->SendAsync(buffer, send_complete);
    ++successful_send_count;
  }
  FlushNetworkThreadAndPendingOperations();
  EXPECT_EQ(buffer.data.size() * number_of_packets,
            channel_->buffered_amount());
  EXPECT_EQ(successful_send_count, successful_sends);

  // An event should not have been fired for buffered amount.
  EXPECT_EQ(1u, observer_->on_buffered_amount_change_count());

  // Now buffered amount events should get fired and the value
  // get down to 0u.
  controller_->set_send_blocked(false);
  run_loop_.Flush();
  EXPECT_EQ(channel_->buffered_amount(), 0u);
  EXPECT_EQ(successful_send_count, successful_sends);
  EXPECT_EQ(successful_send_count,
            observer_->on_buffered_amount_change_count());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedBufferedAmountWhenBlocked) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  EXPECT_TRUE(channel_->Send(buffer));
  size_t successful_send_count = 1;

  run_loop_.Flush();
  EXPECT_EQ(0U, channel_->buffered_amount());
  EXPECT_EQ(successful_send_count,
            observer_->on_buffered_amount_change_count());

  controller_->set_send_blocked(true);

  const int number_of_packets = 3;
  for (int i = 0; i < number_of_packets; ++i) {
    EXPECT_TRUE(channel_->Send(buffer));
  }
  EXPECT_EQ(buffer.data.size() * number_of_packets,
            channel_->buffered_amount());
  EXPECT_EQ(successful_send_count,
            observer_->on_buffered_amount_change_count());

  controller_->set_send_blocked(false);
  run_loop_.Flush();
  successful_send_count += number_of_packets;
  EXPECT_EQ(channel_->buffered_amount(), 0u);
  EXPECT_EQ(successful_send_count,
            observer_->on_buffered_amount_change_count());
}

// Tests that the queued data are sent when the channel transitions from blocked
// to unblocked.
TEST_F(SctpDataChannelTest, QueuedDataSentWhenUnblocked) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_send_blocked(true);
  size_t successful_send = 0u;
  auto send_complete = [&](RTCError err) {
    EXPECT_TRUE(err.ok());
    ++successful_send;
  };
  channel_->SendAsync(buffer, send_complete);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_EQ(1U, successful_send);
  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  controller_->set_send_blocked(false);
  SetChannelReady();
  EXPECT_EQ(channel_->buffered_amount(), 0u);
  EXPECT_EQ(observer_->on_buffered_amount_change_count(), 1u);
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedQueuedDataSentWhenUnblocked) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_send_blocked(true);
  EXPECT_TRUE(channel_->Send(buffer));

  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  controller_->set_send_blocked(false);
  SetChannelReady();
  EXPECT_EQ(0U, channel_->buffered_amount());
  EXPECT_EQ(1U, observer_->on_buffered_amount_change_count());
}

// Tests that no crash when the channel is blocked right away while trying to
// send queued data.
TEST_F(SctpDataChannelTest, BlockedWhenSendQueuedDataNoCrash) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_send_blocked(true);
  size_t successful_send = 0u;
  auto send_complete = [&](RTCError err) {
    EXPECT_TRUE(err.ok());
    ++successful_send;
  };
  channel_->SendAsync(buffer, send_complete);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_EQ(1U, successful_send);
  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  // Set channel ready while it is still blocked.
  SetChannelReady();
  EXPECT_EQ(buffer.size(), channel_->buffered_amount());
  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  // Unblock the channel to send queued data again, there should be no crash.
  controller_->set_send_blocked(false);
  SetChannelReady();
  EXPECT_EQ(0U, channel_->buffered_amount());
  EXPECT_EQ(1U, observer_->on_buffered_amount_change_count());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedBlockedWhenSendQueuedDataNoCrash) {
  AddObserver();
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_send_blocked(true);
  EXPECT_TRUE(channel_->Send(buffer));
  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  // Set channel ready while it is still blocked.
  SetChannelReady();
  EXPECT_EQ(buffer.size(), channel_->buffered_amount());
  EXPECT_EQ(0U, observer_->on_buffered_amount_change_count());

  // Unblock the channel to send queued data again, there should be no crash.
  controller_->set_send_blocked(false);
  SetChannelReady();
  EXPECT_EQ(0U, channel_->buffered_amount());
  EXPECT_EQ(1U, observer_->on_buffered_amount_change_count());
}

// Tests that DataChannel::messages_sent() and DataChannel::bytes_sent() are
// correct, sending data both while unblocked and while blocked.
TEST_F(SctpDataChannelTest, VerifyMessagesAndBytesSent) {
  AddObserver();
  SetChannelReady();
  std::vector<DataBuffer> buffers({
      DataBuffer("message 1"),
      DataBuffer("msg 2"),
      DataBuffer("message three"),
      DataBuffer("quadra message"),
      DataBuffer("fifthmsg"),
      DataBuffer("message of the beast"),
  });

  // Default values.
  EXPECT_EQ(0U, channel_->messages_sent());
  EXPECT_EQ(0U, channel_->bytes_sent());

  // Send three buffers while not blocked.
  controller_->set_send_blocked(false);
  for (int i : {0, 1, 2}) {
    channel_->SendAsync(buffers[i], nullptr);
  }
  FlushNetworkThreadAndPendingOperations();

  size_t bytes_sent = buffers[0].size() + buffers[1].size() + buffers[2].size();
  EXPECT_EQ_WAIT(0U, channel_->buffered_amount(), kDefaultTimeout);
  EXPECT_EQ(3U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());

  // Send three buffers while blocked, queuing the buffers.
  controller_->set_send_blocked(true);
  for (int i : {3, 4, 5}) {
    channel_->SendAsync(buffers[i], nullptr);
  }
  FlushNetworkThreadAndPendingOperations();
  size_t bytes_queued =
      buffers[3].size() + buffers[4].size() + buffers[5].size();
  EXPECT_EQ(bytes_queued, channel_->buffered_amount());
  EXPECT_EQ(3U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());

  // Unblock and make sure everything was sent.
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(0U, channel_->buffered_amount(), kDefaultTimeout);
  bytes_sent += bytes_queued;
  EXPECT_EQ(6U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedVerifyMessagesAndBytesSent) {
  AddObserver();
  SetChannelReady();
  std::vector<DataBuffer> buffers({
      DataBuffer("message 1"),
      DataBuffer("msg 2"),
      DataBuffer("message three"),
      DataBuffer("quadra message"),
      DataBuffer("fifthmsg"),
      DataBuffer("message of the beast"),
  });

  // Default values.
  EXPECT_EQ(0U, channel_->messages_sent());
  EXPECT_EQ(0U, channel_->bytes_sent());

  // Send three buffers while not blocked.
  controller_->set_send_blocked(false);
  EXPECT_TRUE(channel_->Send(buffers[0]));
  EXPECT_TRUE(channel_->Send(buffers[1]));
  EXPECT_TRUE(channel_->Send(buffers[2]));
  size_t bytes_sent = buffers[0].size() + buffers[1].size() + buffers[2].size();
  EXPECT_EQ_WAIT(0U, channel_->buffered_amount(), kDefaultTimeout);
  EXPECT_EQ(3U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());

  // Send three buffers while blocked, queuing the buffers.
  controller_->set_send_blocked(true);
  EXPECT_TRUE(channel_->Send(buffers[3]));
  EXPECT_TRUE(channel_->Send(buffers[4]));
  EXPECT_TRUE(channel_->Send(buffers[5]));
  size_t bytes_queued =
      buffers[3].size() + buffers[4].size() + buffers[5].size();
  EXPECT_EQ(bytes_queued, channel_->buffered_amount());
  EXPECT_EQ(3U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());

  // Unblock and make sure everything was sent.
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(0U, channel_->buffered_amount(), kDefaultTimeout);
  bytes_sent += bytes_queued;
  EXPECT_EQ(6U, channel_->messages_sent());
  EXPECT_EQ(bytes_sent, channel_->bytes_sent());
}

// Tests that the queued control message is sent when channel is ready.
TEST_F(SctpDataChannelTest, OpenMessageSent) {
  // Initially the id is unassigned.
  EXPECT_EQ(-1, channel_->id());

  SetChannelReady();
  EXPECT_GE(channel_->id(), 0);
  EXPECT_EQ(DataMessageType::kControl,
            controller_->last_send_data_params().type);
  EXPECT_EQ(controller_->last_sid(), channel_->id());
}

TEST_F(SctpDataChannelTest, QueuedOpenMessageSent) {
  controller_->set_send_blocked(true);
  SetChannelReady();
  controller_->set_send_blocked(false);

  EXPECT_EQ(DataMessageType::kControl,
            controller_->last_send_data_params().type);
  EXPECT_EQ(controller_->last_sid(), channel_->id());
}

// Tests that the DataChannel created after transport gets ready can enter OPEN
// state.
TEST_F(SctpDataChannelTest, LateCreatedChannelTransitionToOpen) {
  SetChannelReady();
  InternalDataChannelInit init;
  init.id = 1;
  auto dc = SctpDataChannel::CreateProxy(
      controller_->CreateDataChannel("test1", init), signaling_safety_);
  EXPECT_EQ(DataChannelInterface::kOpen, dc->state());
}

// Tests that an unordered DataChannel sends data as ordered until the OPEN_ACK
// message is received.
TEST_F(SctpDataChannelTest, SendUnorderedAfterReceivesOpenAck) {
  SetChannelReady();
  InternalDataChannelInit init;
  init.id = 1;
  init.ordered = false;
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", init);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);

  // Sends a message and verifies it's ordered.
  DataBuffer buffer("some data");
  proxy->SendAsync(buffer, nullptr);
  EXPECT_TRUE(controller_->last_send_data_params().ordered);

  // Emulates receiving an OPEN_ACK message.
  rtc::CopyOnWriteBuffer payload;
  WriteDataChannelOpenAckMessage(&payload);
  network_thread_.BlockingCall(
      [&] { dc->OnDataReceived(DataMessageType::kControl, payload); });

  // Sends another message and verifies it's unordered.
  proxy->SendAsync(buffer, nullptr);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_FALSE(controller_->last_send_data_params().ordered);
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedSendUnorderedAfterReceivesOpenAck) {
  SetChannelReady();
  InternalDataChannelInit init;
  init.id = 1;
  init.ordered = false;
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", init);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);

  // Sends a message and verifies it's ordered.
  DataBuffer buffer("some data");
  ASSERT_TRUE(proxy->Send(buffer));
  EXPECT_TRUE(controller_->last_send_data_params().ordered);

  // Emulates receiving an OPEN_ACK message.
  rtc::CopyOnWriteBuffer payload;
  WriteDataChannelOpenAckMessage(&payload);
  network_thread_.BlockingCall(
      [&] { dc->OnDataReceived(DataMessageType::kControl, payload); });

  // Sends another message and verifies it's unordered.
  ASSERT_TRUE(proxy->Send(buffer));
  EXPECT_FALSE(controller_->last_send_data_params().ordered);
}

// Tests that an unordered DataChannel sends unordered data after any DATA
// message is received.
TEST_F(SctpDataChannelTest, SendUnorderedAfterReceiveData) {
  SetChannelReady();
  InternalDataChannelInit init;
  init.id = 1;
  init.ordered = false;
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", init);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);

  // Emulates receiving a DATA message.
  DataBuffer buffer("data");
  network_thread_.BlockingCall(
      [&] { dc->OnDataReceived(DataMessageType::kText, buffer.data); });

  // Sends a message and verifies it's unordered.
  proxy->SendAsync(buffer, nullptr);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_FALSE(controller_->last_send_data_params().ordered);
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedSendUnorderedAfterReceiveData) {
  SetChannelReady();
  InternalDataChannelInit init;
  init.id = 1;
  init.ordered = false;
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", init);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);

  // Emulates receiving a DATA message.
  DataBuffer buffer("data");
  network_thread_.BlockingCall(
      [&] { dc->OnDataReceived(DataMessageType::kText, buffer.data); });

  // Sends a message and verifies it's unordered.
  ASSERT_TRUE(proxy->Send(buffer));
  EXPECT_FALSE(controller_->last_send_data_params().ordered);
}

// Tests that the channel can't open until it's successfully sent the OPEN
// message.
TEST_F(SctpDataChannelTest, OpenWaitsForOpenMesssage) {
  DataBuffer buffer("foo");

  controller_->set_send_blocked(true);
  SetChannelReady();
  EXPECT_EQ(DataChannelInterface::kConnecting, channel_->state());
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, channel_->state(), 1000);
  EXPECT_EQ(DataMessageType::kControl,
            controller_->last_send_data_params().type);
}

// Tests that close first makes sure all queued data gets sent.
TEST_F(SctpDataChannelTest, QueuedCloseFlushes) {
  DataBuffer buffer("foo");

  controller_->set_send_blocked(true);
  SetChannelReady();
  EXPECT_EQ(DataChannelInterface::kConnecting, channel_->state());
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, channel_->state(), 1000);
  controller_->set_send_blocked(true);
  channel_->SendAsync(buffer, nullptr);
  channel_->Close();
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, channel_->state(), 1000);
  EXPECT_TRUE(channel_->error().ok());
  EXPECT_EQ(DataMessageType::kText, controller_->last_send_data_params().type);
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedQueuedCloseFlushes) {
  DataBuffer buffer("foo");

  controller_->set_send_blocked(true);
  SetChannelReady();
  EXPECT_EQ(DataChannelInterface::kConnecting, channel_->state());
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, channel_->state(), 1000);
  controller_->set_send_blocked(true);
  channel_->Send(buffer);
  channel_->Close();
  controller_->set_send_blocked(false);
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, channel_->state(), 1000);
  EXPECT_TRUE(channel_->error().ok());
  EXPECT_EQ(DataMessageType::kText, controller_->last_send_data_params().type);
}

// Tests that messages are sent with the right id.
TEST_F(SctpDataChannelTest, SendDataId) {
  SetChannelSid(inner_channel_, StreamId(1));
  SetChannelReady();
  DataBuffer buffer("data");
  channel_->SendAsync(buffer, nullptr);
  FlushNetworkThreadAndPendingOperations();
  EXPECT_EQ(1, controller_->last_sid());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedSendDataId) {
  SetChannelSid(inner_channel_, StreamId(1));
  SetChannelReady();
  DataBuffer buffer("data");
  EXPECT_TRUE(channel_->Send(buffer));
  EXPECT_EQ(1, controller_->last_sid());
}

// Tests that the incoming messages with right ids are accepted.
TEST_F(SctpDataChannelTest, ReceiveDataWithValidId) {
  SetChannelSid(inner_channel_, StreamId(1));
  SetChannelReady();

  AddObserver();

  DataBuffer buffer("abcd");
  network_thread_.BlockingCall([&] {
    inner_channel_->OnDataReceived(DataMessageType::kText, buffer.data);
  });
  run_loop_.Flush();
  EXPECT_EQ(1U, observer_->messages_received());
}

// Tests that no CONTROL message is sent if the datachannel is negotiated and
// not created from an OPEN message.
TEST_F(SctpDataChannelTest, NoMsgSentIfNegotiatedAndNotFromOpenMsg) {
  InternalDataChannelInit config;
  config.id = 1;
  config.negotiated = true;
  config.open_handshake_role = InternalDataChannelInit::kNone;

  SetChannelReady();
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", config);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);
  EXPECT_EQ(0, controller_->last_sid());
}

// Tests that DataChannel::messages_received() and DataChannel::bytes_received()
// are correct, receiving data both while not open and while open.
TEST_F(SctpDataChannelTest, VerifyMessagesAndBytesReceived) {
  AddObserver();
  std::vector<DataBuffer> buffers({
      DataBuffer("message 1"),
      DataBuffer("msg 2"),
      DataBuffer("message three"),
      DataBuffer("quadra message"),
      DataBuffer("fifthmsg"),
      DataBuffer("message of the beast"),
  });

  SetChannelSid(inner_channel_, StreamId(1));

  // Default values.
  EXPECT_EQ(0U, channel_->messages_received());
  EXPECT_EQ(0U, channel_->bytes_received());

  // Receive three buffers while data channel isn't open.
  network_thread_.BlockingCall([&] {
    for (int i : {0, 1, 2})
      inner_channel_->OnDataReceived(DataMessageType::kText, buffers[i].data);
  });
  EXPECT_EQ(0U, observer_->messages_received());
  EXPECT_EQ(0U, channel_->messages_received());
  EXPECT_EQ(0U, channel_->bytes_received());

  // Open channel and make sure everything was received.
  SetChannelReady();
  size_t bytes_received =
      buffers[0].size() + buffers[1].size() + buffers[2].size();
  EXPECT_EQ(3U, observer_->messages_received());
  EXPECT_EQ(3U, channel_->messages_received());
  EXPECT_EQ(bytes_received, channel_->bytes_received());

  // Receive three buffers while open.
  network_thread_.BlockingCall([&] {
    for (int i : {3, 4, 5})
      inner_channel_->OnDataReceived(DataMessageType::kText, buffers[i].data);
  });
  run_loop_.Flush();
  bytes_received += buffers[3].size() + buffers[4].size() + buffers[5].size();
  EXPECT_EQ(6U, observer_->messages_received());
  EXPECT_EQ(6U, channel_->messages_received());
  EXPECT_EQ(bytes_received, channel_->bytes_received());
}

// Tests that OPEN_ACK message is sent if the datachannel is created from an
// OPEN message.
TEST_F(SctpDataChannelTest, OpenAckSentIfCreatedFromOpenMessage) {
  InternalDataChannelInit config;
  config.id = 1;
  config.negotiated = true;
  config.open_handshake_role = InternalDataChannelInit::kAcker;

  SetChannelReady();
  rtc::scoped_refptr<SctpDataChannel> dc =
      controller_->CreateDataChannel("test1", config);
  auto proxy = SctpDataChannel::CreateProxy(dc, signaling_safety_);

  EXPECT_EQ_WAIT(DataChannelInterface::kOpen, proxy->state(), 1000);

  EXPECT_EQ(config.id, controller_->last_sid());
  EXPECT_EQ(DataMessageType::kControl,
            controller_->last_send_data_params().type);
}

// Tests the OPEN_ACK role assigned by InternalDataChannelInit.
TEST_F(SctpDataChannelTest, OpenAckRoleInitialization) {
  InternalDataChannelInit init;
  EXPECT_EQ(InternalDataChannelInit::kOpener, init.open_handshake_role);
  EXPECT_FALSE(init.negotiated);

  DataChannelInit base;
  base.negotiated = true;
  InternalDataChannelInit init2(base);
  EXPECT_EQ(InternalDataChannelInit::kNone, init2.open_handshake_role);
}

// Tests that that Send() returns false if the sending buffer is full
// and the channel stays open.
TEST_F(SctpDataChannelTest, OpenWhenSendBufferFull) {
  AddObserver();
  SetChannelReady();

  const size_t packetSize = 1024;

  rtc::CopyOnWriteBuffer buffer(packetSize);
  memset(buffer.MutableData(), 0, buffer.size());

  DataBuffer packet(buffer, true);
  controller_->set_send_blocked(true);
  size_t successful_send = 0u, failed_send = 0u;
  auto send_complete = [&](RTCError err) {
    err.ok() ? ++successful_send : ++failed_send;
  };

  size_t count = DataChannelInterface::MaxSendQueueSize() / packetSize;
  for (size_t i = 0; i < count; ++i) {
    channel_->SendAsync(packet, send_complete);
  }

  // The sending buffer should be full, `Send()` returns false.
  channel_->SendAsync(packet, std::move(send_complete));
  FlushNetworkThreadAndPendingOperations();
  EXPECT_TRUE(DataChannelInterface::kOpen == channel_->state());
  EXPECT_EQ(successful_send, count);
  EXPECT_EQ(failed_send, 1u);
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedOpenWhenSendBufferFull) {
  SetChannelReady();

  const size_t packetSize = 1024;

  rtc::CopyOnWriteBuffer buffer(packetSize);
  memset(buffer.MutableData(), 0, buffer.size());

  DataBuffer packet(buffer, true);
  controller_->set_send_blocked(true);

  for (size_t i = 0; i < DataChannelInterface::MaxSendQueueSize() / packetSize;
       ++i) {
    EXPECT_TRUE(channel_->Send(packet));
  }

  // The sending buffer should be full, `Send()` returns false.
  EXPECT_FALSE(channel_->Send(packet));
  EXPECT_TRUE(DataChannelInterface::kOpen == channel_->state());
}

// Tests that the DataChannel is closed on transport errors.
TEST_F(SctpDataChannelTest, ClosedOnTransportError) {
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_transport_error();

  channel_->SendAsync(buffer, nullptr);

  EXPECT_EQ(DataChannelInterface::kClosed, channel_->state());
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::NETWORK_ERROR, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::NONE, channel_->error().error_detail());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedClosedOnTransportError) {
  SetChannelReady();
  DataBuffer buffer("abcd");
  controller_->set_transport_error();

  EXPECT_TRUE(channel_->Send(buffer));

  EXPECT_EQ(DataChannelInterface::kClosed, channel_->state());
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::NETWORK_ERROR, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::NONE, channel_->error().error_detail());
}

// Tests that the DataChannel is closed if the received buffer is full.
TEST_F(SctpDataChannelTest, ClosedWhenReceivedBufferFull) {
  SetChannelReady();
  rtc::CopyOnWriteBuffer buffer(1024);
  memset(buffer.MutableData(), 0, buffer.size());

  network_thread_.BlockingCall([&] {
    // Receiving data without having an observer will overflow the buffer.
    for (size_t i = 0; i < 16 * 1024 + 1; ++i) {
      inner_channel_->OnDataReceived(DataMessageType::kText, buffer);
    }
  });
  EXPECT_EQ(DataChannelInterface::kClosed, channel_->state());
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::RESOURCE_EXHAUSTED, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::NONE, channel_->error().error_detail());
}

// Tests that sending empty data returns no error and keeps the channel open.
TEST_F(SctpDataChannelTest, SendEmptyData) {
  SetChannelSid(inner_channel_, StreamId(1));
  SetChannelReady();
  EXPECT_EQ(DataChannelInterface::kOpen, channel_->state());

  DataBuffer buffer("");
  channel_->SendAsync(buffer, nullptr);
  EXPECT_EQ(DataChannelInterface::kOpen, channel_->state());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedSendEmptyData) {
  SetChannelSid(inner_channel_, StreamId(1));
  SetChannelReady();
  EXPECT_EQ(DataChannelInterface::kOpen, channel_->state());

  DataBuffer buffer("");
  EXPECT_TRUE(channel_->Send(buffer));
  EXPECT_EQ(DataChannelInterface::kOpen, channel_->state());
}

// Tests that a channel can be closed without being opened or assigned an sid.
TEST_F(SctpDataChannelTest, NeverOpened) {
  controller_->set_transport_available(true);
  network_thread_.BlockingCall(
      [&] { inner_channel_->OnTransportChannelCreated(); });
  channel_->Close();
}

// Tests that a data channel that's not connected to a transport can transition
// directly to the `kClosed` state when closed.
// See also chromium:1421534.
TEST_F(SctpDataChannelTest, UnusedTransitionsDirectlyToClosed) {
  channel_->Close();
  EXPECT_EQ(DataChannelInterface::kClosed, channel_->state());
}

// Test that the data channel goes to the "closed" state (and doesn't crash)
// when its transport goes away, even while data is buffered.
TEST_F(SctpDataChannelTest, TransportDestroyedWhileDataBuffered) {
  AddObserver();
  SetChannelReady();

  rtc::CopyOnWriteBuffer buffer(1024);
  memset(buffer.MutableData(), 0, buffer.size());
  DataBuffer packet(buffer, true);

  // Send a packet while sending is blocked so it ends up buffered.
  controller_->set_send_blocked(true);
  channel_->SendAsync(packet, nullptr);

  // Tell the data channel that its transport is being destroyed.
  // It should then stop using the transport (allowing us to delete it) and
  // transition to the "closed" state.
  RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, "");
  error.set_error_detail(RTCErrorDetailType::SCTP_FAILURE);
  network_thread_.BlockingCall(
      [&] { inner_channel_->OnTransportChannelClosed(error); });
  controller_.reset(nullptr);
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, channel_->state(),
                 kDefaultTimeout);
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::OPERATION_ERROR_WITH_DATA, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::SCTP_FAILURE, channel_->error().error_detail());
}

// TODO(tommi): This test uses `Send()`. Remove once fully deprecated.
TEST_F(SctpDataChannelTest, DeprecatedTransportDestroyedWhileDataBuffered) {
  SetChannelReady();

  rtc::CopyOnWriteBuffer buffer(1024);
  memset(buffer.MutableData(), 0, buffer.size());
  DataBuffer packet(buffer, true);

  // Send a packet while sending is blocked so it ends up buffered.
  controller_->set_send_blocked(true);
  EXPECT_TRUE(channel_->Send(packet));

  // Tell the data channel that its transport is being destroyed.
  // It should then stop using the transport (allowing us to delete it) and
  // transition to the "closed" state.
  RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA, "");
  error.set_error_detail(RTCErrorDetailType::SCTP_FAILURE);
  network_thread_.BlockingCall(
      [&] { inner_channel_->OnTransportChannelClosed(error); });
  controller_.reset(nullptr);
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, channel_->state(),
                 kDefaultTimeout);
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::OPERATION_ERROR_WITH_DATA, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::SCTP_FAILURE, channel_->error().error_detail());
}

TEST_F(SctpDataChannelTest, TransportGotErrorCode) {
  SetChannelReady();

  // Tell the data channel that its transport is being destroyed with an
  // error code.
  // It should then report that error code.
  RTCError error(RTCErrorType::OPERATION_ERROR_WITH_DATA,
                 "Transport channel closed");
  error.set_error_detail(RTCErrorDetailType::SCTP_FAILURE);
  error.set_sctp_cause_code(
      static_cast<uint16_t>(cricket::SctpErrorCauseCode::kProtocolViolation));
  network_thread_.BlockingCall(
      [&] { inner_channel_->OnTransportChannelClosed(error); });
  controller_.reset(nullptr);
  EXPECT_EQ_WAIT(DataChannelInterface::kClosed, channel_->state(),
                 kDefaultTimeout);
  EXPECT_FALSE(channel_->error().ok());
  EXPECT_EQ(RTCErrorType::OPERATION_ERROR_WITH_DATA, channel_->error().type());
  EXPECT_EQ(RTCErrorDetailType::SCTP_FAILURE, channel_->error().error_detail());
  EXPECT_EQ(
      static_cast<uint16_t>(cricket::SctpErrorCauseCode::kProtocolViolation),
      channel_->error().sctp_cause_code());
}

class SctpSidAllocatorTest : public ::testing::Test {
 protected:
  SctpSidAllocator allocator_;
};

// Verifies that an even SCTP id is allocated for SSL_CLIENT and an odd id for
// SSL_SERVER.
TEST_F(SctpSidAllocatorTest, SctpIdAllocationBasedOnRole) {
  EXPECT_EQ(allocator_.AllocateSid(rtc::SSL_SERVER), StreamId(1));
  EXPECT_EQ(allocator_.AllocateSid(rtc::SSL_CLIENT), StreamId(0));
  EXPECT_EQ(allocator_.AllocateSid(rtc::SSL_SERVER), StreamId(3));
  EXPECT_EQ(allocator_.AllocateSid(rtc::SSL_CLIENT), StreamId(2));
}

// Verifies that SCTP ids of existing DataChannels are not reused.
TEST_F(SctpSidAllocatorTest, SctpIdAllocationNoReuse) {
  StreamId old_id(1);
  EXPECT_TRUE(allocator_.ReserveSid(old_id));

  StreamId new_id = allocator_.AllocateSid(rtc::SSL_SERVER);
  EXPECT_TRUE(new_id.HasValue());
  EXPECT_NE(old_id, new_id);

  old_id = StreamId(0);
  EXPECT_TRUE(allocator_.ReserveSid(old_id));
  new_id = allocator_.AllocateSid(rtc::SSL_CLIENT);
  EXPECT_TRUE(new_id.HasValue());
  EXPECT_NE(old_id, new_id);
}

// Verifies that SCTP ids of removed DataChannels can be reused.
TEST_F(SctpSidAllocatorTest, SctpIdReusedForRemovedDataChannel) {
  StreamId odd_id(1);
  StreamId even_id(0);
  EXPECT_TRUE(allocator_.ReserveSid(odd_id));
  EXPECT_TRUE(allocator_.ReserveSid(even_id));

  StreamId allocated_id = allocator_.AllocateSid(rtc::SSL_SERVER);
  EXPECT_EQ(odd_id.stream_id_int() + 2, allocated_id.stream_id_int());

  allocated_id = allocator_.AllocateSid(rtc::SSL_CLIENT);
  EXPECT_EQ(even_id.stream_id_int() + 2, allocated_id.stream_id_int());

  allocated_id = allocator_.AllocateSid(rtc::SSL_SERVER);
  EXPECT_EQ(odd_id.stream_id_int() + 4, allocated_id.stream_id_int());

  allocated_id = allocator_.AllocateSid(rtc::SSL_CLIENT);
  EXPECT_EQ(even_id.stream_id_int() + 4, allocated_id.stream_id_int());

  allocator_.ReleaseSid(odd_id);
  allocator_.ReleaseSid(even_id);

  // Verifies that removed ids are reused.
  allocated_id = allocator_.AllocateSid(rtc::SSL_SERVER);
  EXPECT_EQ(odd_id, allocated_id);

  allocated_id = allocator_.AllocateSid(rtc::SSL_CLIENT);
  EXPECT_EQ(even_id, allocated_id);

  // Verifies that used higher ids are not reused.
  allocated_id = allocator_.AllocateSid(rtc::SSL_SERVER);
  EXPECT_EQ(odd_id.stream_id_int() + 6, allocated_id.stream_id_int());

  allocated_id = allocator_.AllocateSid(rtc::SSL_CLIENT);
  EXPECT_EQ(even_id.stream_id_int() + 6, allocated_id.stream_id_int());
}

// Code coverage tests for default implementations in data_channel_interface.*.
namespace {
class NoImplDataChannel : public DataChannelInterface {
 public:
  NoImplDataChannel() = default;
  // Send and SendAsync implementations are public and implementation
  // is in data_channel_interface.cc.

 private:
  // Implementation for pure virtual methods, just for compilation sake.
  void RegisterObserver(DataChannelObserver* observer) override {}
  void UnregisterObserver() override {}
  std::string label() const override { return ""; }
  bool reliable() const override { return false; }
  int id() const override { return -1; }
  DataState state() const override { return DataChannelInterface::kClosed; }
  uint32_t messages_sent() const override { return 0u; }
  uint64_t bytes_sent() const override { return 0u; }
  uint32_t messages_received() const override { return 0u; }
  uint64_t bytes_received() const override { return 0u; }
  uint64_t buffered_amount() const override { return 0u; }
  void Close() override {}
};

class NoImplObserver : public DataChannelObserver {
 public:
  NoImplObserver() = default;

 private:
  void OnStateChange() override {}
  void OnMessage(const DataBuffer& buffer) override {}
};
}  // namespace

TEST(DataChannelInterfaceTest, Coverage) {
  auto channel = rtc::make_ref_counted<NoImplDataChannel>();
  EXPECT_FALSE(channel->ordered());
  EXPECT_EQ(channel->maxRetransmitTime(), 0u);
  EXPECT_EQ(channel->maxRetransmits(), 0u);
  EXPECT_FALSE(channel->maxRetransmitsOpt());
  EXPECT_FALSE(channel->maxPacketLifeTime());
  EXPECT_TRUE(channel->protocol().empty());
  EXPECT_FALSE(channel->negotiated());
  EXPECT_EQ(channel->MaxSendQueueSize(), 16u * 1024u * 1024u);

  NoImplObserver observer;
  observer.OnBufferedAmountChange(0u);
  EXPECT_FALSE(observer.IsOkToCallOnTheNetworkThread());
}

#if RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

TEST(DataChannelInterfaceDeathTest, SendDefaultImplDchecks) {
  auto channel = rtc::make_ref_counted<NoImplDataChannel>();
  RTC_EXPECT_DEATH(channel->Send(DataBuffer("Foo")), "Check failed: false");
}

TEST(DataChannelInterfaceDeathTest, SendAsyncDefaultImplDchecks) {
  auto channel = rtc::make_ref_counted<NoImplDataChannel>();
  RTC_EXPECT_DEATH(channel->SendAsync(DataBuffer("Foo"), nullptr),
                   "Check failed: false");
}
#endif  // RTC_DCHECK_IS_ON && GTEST_HAS_DEATH_TEST && !defined(WEBRTC_ANDROID)

}  // namespace
}  // namespace webrtc
