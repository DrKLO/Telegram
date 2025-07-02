/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include <string.h>

#include <algorithm>
#include <cstddef>
#include <memory>
#include <string>
#include <vector>

#include "absl/memory/memory.h"
#include "api/units/time_delta.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/async_tcp_socket.h"
#include "rtc_base/async_udp_socket.h"
#include "rtc_base/event.h"
#include "rtc_base/gunit.h"
#include "rtc_base/ip_address.h"
#include "rtc_base/logging.h"
#include "rtc_base/nat_server.h"
#include "rtc_base/nat_socket_factory.h"
#include "rtc_base/nat_types.h"
#include "rtc_base/net_helpers.h"
#include "rtc_base/net_test_helpers.h"
#include "rtc_base/network.h"
#include "rtc_base/physical_socket_server.h"
#include "rtc_base/socket.h"
#include "rtc_base/socket_address.h"
#include "rtc_base/socket_factory.h"
#include "rtc_base/socket_server.h"
#include "rtc_base/test_client.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "rtc_base/thread.h"
#include "rtc_base/virtual_socket_server.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

namespace rtc {
namespace {

bool CheckReceive(TestClient* client,
                  bool should_receive,
                  const char* buf,
                  size_t size) {
  return (should_receive) ? client->CheckNextPacket(buf, size, 0)
                          : client->CheckNoPacket();
}

TestClient* CreateTestClient(SocketFactory* factory,
                             const SocketAddress& local_addr) {
  return new TestClient(
      absl::WrapUnique(AsyncUDPSocket::Create(factory, local_addr)));
}

TestClient* CreateTCPTestClient(Socket* socket) {
  return new TestClient(std::make_unique<AsyncTCPSocket>(socket));
}

// Tests that when sending from internal_addr to external_addrs through the
// NAT type specified by nat_type, all external addrs receive the sent packet
// and, if exp_same is true, all use the same mapped-address on the NAT.
void TestSend(SocketServer* internal,
              const SocketAddress& internal_addr,
              SocketServer* external,
              const SocketAddress external_addrs[4],
              NATType nat_type,
              bool exp_same) {
  Thread th_int(internal);
  Thread th_ext(external);

  th_int.Start();
  th_ext.Start();

  SocketAddress server_addr = internal_addr;
  server_addr.SetPort(0);  // Auto-select a port
  NATServer* nat =
      new NATServer(nat_type, th_int, internal, server_addr, server_addr,
                    th_ext, external, external_addrs[0]);
  NATSocketFactory* natsf = new NATSocketFactory(
      internal, nat->internal_udp_address(), nat->internal_tcp_address());

  TestClient* in;
  th_int.BlockingCall([&] { in = CreateTestClient(natsf, internal_addr); });

  TestClient* out[4];
  th_ext.BlockingCall([&] {
    for (int i = 0; i < 4; i++)
      out[i] = CreateTestClient(external, external_addrs[i]);
  });

  const char* buf = "filter_test";
  size_t len = strlen(buf);

  th_int.BlockingCall([&] { in->SendTo(buf, len, out[0]->address()); });
  SocketAddress trans_addr;
  th_ext.BlockingCall(
      [&] { EXPECT_TRUE(out[0]->CheckNextPacket(buf, len, &trans_addr)); });

  for (int i = 1; i < 4; i++) {
    th_int.BlockingCall([&] { in->SendTo(buf, len, out[i]->address()); });
    SocketAddress trans_addr2;
    th_ext.BlockingCall([&] {
      EXPECT_TRUE(out[i]->CheckNextPacket(buf, len, &trans_addr2));
      bool are_same = (trans_addr == trans_addr2);
      ASSERT_EQ(are_same, exp_same) << "same translated address";
      ASSERT_NE(AF_UNSPEC, trans_addr.family());
      ASSERT_NE(AF_UNSPEC, trans_addr2.family());
    });
  }

  th_int.Stop();
  th_ext.Stop();

  delete nat;
  delete natsf;
  delete in;
  for (int i = 0; i < 4; i++)
    delete out[i];
}

// Tests that when sending from external_addrs to internal_addr, the packet
// is delivered according to the specified filter_ip and filter_port rules.
void TestRecv(SocketServer* internal,
              const SocketAddress& internal_addr,
              SocketServer* external,
              const SocketAddress external_addrs[4],
              NATType nat_type,
              bool filter_ip,
              bool filter_port) {
  Thread th_int(internal);
  Thread th_ext(external);

  SocketAddress server_addr = internal_addr;
  server_addr.SetPort(0);  // Auto-select a port
  th_int.Start();
  th_ext.Start();
  NATServer* nat =
      new NATServer(nat_type, th_int, internal, server_addr, server_addr,
                    th_ext, external, external_addrs[0]);
  NATSocketFactory* natsf = new NATSocketFactory(
      internal, nat->internal_udp_address(), nat->internal_tcp_address());

  TestClient* in = nullptr;
  th_int.BlockingCall([&] { in = CreateTestClient(natsf, internal_addr); });

  TestClient* out[4];
  th_ext.BlockingCall([&] {
    for (int i = 0; i < 4; i++)
      out[i] = CreateTestClient(external, external_addrs[i]);
  });

  const char* buf = "filter_test";
  size_t len = strlen(buf);

  th_int.BlockingCall([&] { in->SendTo(buf, len, out[0]->address()); });
  SocketAddress trans_addr;
  th_ext.BlockingCall(
      [&] { EXPECT_TRUE(out[0]->CheckNextPacket(buf, len, &trans_addr)); });

  th_ext.BlockingCall([&] { out[1]->SendTo(buf, len, trans_addr); });
  th_int.BlockingCall(
      [&] { EXPECT_TRUE(CheckReceive(in, !filter_ip, buf, len)); });
  th_ext.BlockingCall([&] { out[2]->SendTo(buf, len, trans_addr); });

  th_int.BlockingCall(
      [&] { EXPECT_TRUE(CheckReceive(in, !filter_port, buf, len)); });

  th_ext.BlockingCall([&] { out[3]->SendTo(buf, len, trans_addr); });

  th_int.BlockingCall([&] {
    EXPECT_TRUE(CheckReceive(in, !filter_ip && !filter_port, buf, len));
  });

  th_int.Stop();
  th_ext.Stop();

  delete nat;
  delete natsf;
  delete in;
  for (int i = 0; i < 4; i++)
    delete out[i];
}

// Tests that NATServer allocates bindings properly.
void TestBindings(SocketServer* internal,
                  const SocketAddress& internal_addr,
                  SocketServer* external,
                  const SocketAddress external_addrs[4]) {
  TestSend(internal, internal_addr, external, external_addrs, NAT_OPEN_CONE,
           true);
  TestSend(internal, internal_addr, external, external_addrs,
           NAT_ADDR_RESTRICTED, true);
  TestSend(internal, internal_addr, external, external_addrs,
           NAT_PORT_RESTRICTED, true);
  TestSend(internal, internal_addr, external, external_addrs, NAT_SYMMETRIC,
           false);
}

// Tests that NATServer filters packets properly.
void TestFilters(SocketServer* internal,
                 const SocketAddress& internal_addr,
                 SocketServer* external,
                 const SocketAddress external_addrs[4]) {
  TestRecv(internal, internal_addr, external, external_addrs, NAT_OPEN_CONE,
           false, false);
  TestRecv(internal, internal_addr, external, external_addrs,
           NAT_ADDR_RESTRICTED, true, false);
  TestRecv(internal, internal_addr, external, external_addrs,
           NAT_PORT_RESTRICTED, true, true);
  TestRecv(internal, internal_addr, external, external_addrs, NAT_SYMMETRIC,
           true, true);
}

bool TestConnectivity(const SocketAddress& src, const IPAddress& dst) {
  // The physical NAT tests require connectivity to the selected ip from the
  // internal address used for the NAT. Things like firewalls can break that, so
  // check to see if it's worth even trying with this ip.
  std::unique_ptr<PhysicalSocketServer> pss(new PhysicalSocketServer());
  std::unique_ptr<Socket> client(pss->CreateSocket(src.family(), SOCK_DGRAM));
  std::unique_ptr<Socket> server(pss->CreateSocket(src.family(), SOCK_DGRAM));
  if (client->Bind(SocketAddress(src.ipaddr(), 0)) != 0 ||
      server->Bind(SocketAddress(dst, 0)) != 0) {
    return false;
  }
  const char* buf = "hello other socket";
  size_t len = strlen(buf);
  int sent = client->SendTo(buf, len, server->GetLocalAddress());

  Thread::Current()->SleepMs(100);
  rtc::Buffer payload;
  Socket::ReceiveBuffer receive_buffer(payload);
  int received = server->RecvFrom(receive_buffer);
  return received == sent && ::memcmp(buf, payload.data(), len) == 0;
}

void TestPhysicalInternal(const SocketAddress& int_addr) {
  webrtc::test::ScopedKeyValueConfig field_trials;
  rtc::AutoThread main_thread;
  PhysicalSocketServer socket_server;
  BasicNetworkManager network_manager(nullptr, &socket_server, &field_trials);
  network_manager.StartUpdating();
  // Process pending messages so the network list is updated.
  Thread::Current()->ProcessMessages(0);

  std::vector<const Network*> networks = network_manager.GetNetworks();
  networks.erase(std::remove_if(networks.begin(), networks.end(),
                                [](const rtc::Network* network) {
                                  return rtc::kDefaultNetworkIgnoreMask &
                                         network->type();
                                }),
                 networks.end());
  if (networks.empty()) {
    RTC_LOG(LS_WARNING) << "Not enough network adapters for test.";
    return;
  }

  SocketAddress ext_addr1(int_addr);
  SocketAddress ext_addr2;
  // Find an available IP with matching family. The test breaks if int_addr
  // can't talk to ip, so check for connectivity as well.
  for (const Network* const network : networks) {
    const IPAddress& ip = network->GetBestIP();
    if (ip.family() == int_addr.family() && TestConnectivity(int_addr, ip)) {
      ext_addr2.SetIP(ip);
      break;
    }
  }
  if (ext_addr2.IsNil()) {
    RTC_LOG(LS_WARNING) << "No available IP of same family as "
                        << int_addr.ToString();
    return;
  }

  RTC_LOG(LS_INFO) << "selected ip " << ext_addr2.ipaddr().ToString();

  SocketAddress ext_addrs[4] = {
      SocketAddress(ext_addr1), SocketAddress(ext_addr2),
      SocketAddress(ext_addr1), SocketAddress(ext_addr2)};

  std::unique_ptr<PhysicalSocketServer> int_pss(new PhysicalSocketServer());
  std::unique_ptr<PhysicalSocketServer> ext_pss(new PhysicalSocketServer());

  TestBindings(int_pss.get(), int_addr, ext_pss.get(), ext_addrs);
  TestFilters(int_pss.get(), int_addr, ext_pss.get(), ext_addrs);
}

TEST(NatTest, TestPhysicalIPv4) {
  TestPhysicalInternal(SocketAddress("127.0.0.1", 0));
}

TEST(NatTest, TestPhysicalIPv6) {
  if (HasIPv6Enabled()) {
    TestPhysicalInternal(SocketAddress("::1", 0));
  } else {
    RTC_LOG(LS_WARNING) << "No IPv6, skipping";
  }
}

namespace {

class TestVirtualSocketServer : public VirtualSocketServer {
 public:
  // Expose this publicly
  IPAddress GetNextIP(int af) { return VirtualSocketServer::GetNextIP(af); }
};

}  // namespace

void TestVirtualInternal(int family) {
  rtc::AutoThread main_thread;
  std::unique_ptr<TestVirtualSocketServer> int_vss(
      new TestVirtualSocketServer());
  std::unique_ptr<TestVirtualSocketServer> ext_vss(
      new TestVirtualSocketServer());

  SocketAddress int_addr;
  SocketAddress ext_addrs[4];
  int_addr.SetIP(int_vss->GetNextIP(family));
  ext_addrs[0].SetIP(ext_vss->GetNextIP(int_addr.family()));
  ext_addrs[1].SetIP(ext_vss->GetNextIP(int_addr.family()));
  ext_addrs[2].SetIP(ext_addrs[0].ipaddr());
  ext_addrs[3].SetIP(ext_addrs[1].ipaddr());

  TestBindings(int_vss.get(), int_addr, ext_vss.get(), ext_addrs);
  TestFilters(int_vss.get(), int_addr, ext_vss.get(), ext_addrs);
}

TEST(NatTest, TestVirtualIPv4) {
  TestVirtualInternal(AF_INET);
}

TEST(NatTest, TestVirtualIPv6) {
  if (HasIPv6Enabled()) {
    TestVirtualInternal(AF_INET6);
  } else {
    RTC_LOG(LS_WARNING) << "No IPv6, skipping";
  }
}

class NatTcpTest : public ::testing::Test, public sigslot::has_slots<> {
 public:
  NatTcpTest()
      : int_addr_("192.168.0.1", 0),
        ext_addr_("10.0.0.1", 0),
        connected_(false),
        int_vss_(new TestVirtualSocketServer()),
        ext_vss_(new TestVirtualSocketServer()),
        int_thread_(new Thread(int_vss_.get())),
        ext_thread_(new Thread(ext_vss_.get())),
        nat_(new NATServer(NAT_OPEN_CONE,
                           *int_thread_,
                           int_vss_.get(),
                           int_addr_,
                           int_addr_,
                           *ext_thread_,
                           ext_vss_.get(),
                           ext_addr_)),
        natsf_(new NATSocketFactory(int_vss_.get(),
                                    nat_->internal_udp_address(),
                                    nat_->internal_tcp_address())) {
    int_thread_->Start();
    ext_thread_->Start();
  }

  void OnConnectEvent(Socket* socket) { connected_ = true; }

  void OnAcceptEvent(Socket* socket) {
    accepted_.reset(server_->Accept(nullptr));
  }

  void OnCloseEvent(Socket* socket, int error) {}

  void ConnectEvents() {
    server_->SignalReadEvent.connect(this, &NatTcpTest::OnAcceptEvent);
    client_->SignalConnectEvent.connect(this, &NatTcpTest::OnConnectEvent);
  }

  SocketAddress int_addr_;
  SocketAddress ext_addr_;
  bool connected_;
  std::unique_ptr<TestVirtualSocketServer> int_vss_;
  std::unique_ptr<TestVirtualSocketServer> ext_vss_;
  std::unique_ptr<Thread> int_thread_;
  std::unique_ptr<Thread> ext_thread_;
  std::unique_ptr<NATServer> nat_;
  std::unique_ptr<NATSocketFactory> natsf_;
  std::unique_ptr<Socket> client_;
  std::unique_ptr<Socket> server_;
  std::unique_ptr<Socket> accepted_;
};

TEST_F(NatTcpTest, DISABLED_TestConnectOut) {
  server_.reset(ext_vss_->CreateSocket(AF_INET, SOCK_STREAM));
  server_->Bind(ext_addr_);
  server_->Listen(5);

  client_.reset(natsf_->CreateSocket(AF_INET, SOCK_STREAM));
  EXPECT_GE(0, client_->Bind(int_addr_));
  EXPECT_GE(0, client_->Connect(server_->GetLocalAddress()));

  ConnectEvents();

  EXPECT_TRUE_WAIT(connected_, 1000);
  EXPECT_EQ(client_->GetRemoteAddress(), server_->GetLocalAddress());
  EXPECT_EQ(accepted_->GetRemoteAddress().ipaddr(), ext_addr_.ipaddr());

  std::unique_ptr<rtc::TestClient> in(CreateTCPTestClient(client_.release()));
  std::unique_ptr<rtc::TestClient> out(
      CreateTCPTestClient(accepted_.release()));

  const char* buf = "test_packet";
  size_t len = strlen(buf);

  in->Send(buf, len);
  SocketAddress trans_addr;
  EXPECT_TRUE(out->CheckNextPacket(buf, len, &trans_addr));

  out->Send(buf, len);
  EXPECT_TRUE(in->CheckNextPacket(buf, len, &trans_addr));
}

}  // namespace
}  // namespace rtc
