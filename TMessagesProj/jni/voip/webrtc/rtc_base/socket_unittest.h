/*
 *  Copyright 2009 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#ifndef RTC_BASE_SOCKET_UNITTEST_H_
#define RTC_BASE_SOCKET_UNITTEST_H_

#include "absl/strings/string_view.h"
#include "rtc_base/gunit.h"
#include "rtc_base/thread.h"

namespace rtc {

// Generic socket tests, to be used when testing individual socketservers.
// Derive your specific test class from SocketTest, install your
// socketserver, and call the SocketTest test methods.
class SocketTest : public ::testing::Test {
 protected:
  explicit SocketTest(rtc::SocketFactory* socket_factory)
      : kIPv4Loopback(INADDR_LOOPBACK),
        kIPv6Loopback(in6addr_loopback),
        socket_factory_(socket_factory) {}
  void TestConnectIPv4();
  void TestConnectIPv6();
  void TestConnectWithDnsLookupIPv4();
  void TestConnectWithDnsLookupIPv6();
  void TestConnectFailIPv4();
  void TestConnectFailIPv6();
  void TestConnectWithDnsLookupFailIPv4();
  void TestConnectWithDnsLookupFailIPv6();
  void TestConnectWithClosedSocketIPv4();
  void TestConnectWithClosedSocketIPv6();
  void TestConnectWhileNotClosedIPv4();
  void TestConnectWhileNotClosedIPv6();
  void TestServerCloseDuringConnectIPv4();
  void TestServerCloseDuringConnectIPv6();
  void TestClientCloseDuringConnectIPv4();
  void TestClientCloseDuringConnectIPv6();
  void TestServerCloseIPv4();
  void TestServerCloseIPv6();
  void TestCloseInClosedCallbackIPv4();
  void TestCloseInClosedCallbackIPv6();
  void TestDeleteInReadCallbackIPv4();
  void TestDeleteInReadCallbackIPv6();
  void TestSocketServerWaitIPv4();
  void TestSocketServerWaitIPv6();
  void TestTcpIPv4();
  void TestTcpIPv6();
  void TestSingleFlowControlCallbackIPv4();
  void TestSingleFlowControlCallbackIPv6();
  void TestUdpIPv4();
  void TestUdpIPv6();
  void TestUdpReadyToSendIPv4();
  void TestUdpReadyToSendIPv6();
  void TestGetSetOptionsIPv4();
  void TestGetSetOptionsIPv6();
  void TestSocketRecvTimestampIPv4();
  void TestSocketRecvTimestampIPv6();

  static const int kTimeout = 5000;  // ms
  const IPAddress kIPv4Loopback;
  const IPAddress kIPv6Loopback;

 protected:
  void TcpInternal(const IPAddress& loopback,
                   size_t data_size,
                   ptrdiff_t max_send_size);

 private:
  void ConnectInternal(const IPAddress& loopback);
  void ConnectWithDnsLookupInternal(const IPAddress& loopback,
                                    absl::string_view host);
  void ConnectFailInternal(const IPAddress& loopback);

  void ConnectWithDnsLookupFailInternal(const IPAddress& loopback);
  void ConnectWithClosedSocketInternal(const IPAddress& loopback);
  void ConnectWhileNotClosedInternal(const IPAddress& loopback);
  void ServerCloseDuringConnectInternal(const IPAddress& loopback);
  void ClientCloseDuringConnectInternal(const IPAddress& loopback);
  void ServerCloseInternal(const IPAddress& loopback);
  void CloseInClosedCallbackInternal(const IPAddress& loopback);
  void DeleteInReadCallbackInternal(const IPAddress& loopback);
  void SocketServerWaitInternal(const IPAddress& loopback);
  void SingleFlowControlCallbackInternal(const IPAddress& loopback);
  void UdpInternal(const IPAddress& loopback);
  void UdpReadyToSend(const IPAddress& loopback);
  void GetSetOptionsInternal(const IPAddress& loopback);
  void SocketRecvTimestamp(const IPAddress& loopback);

  SocketFactory* socket_factory_;
};

// For unbound sockets, GetLocalAddress / GetRemoteAddress return AF_UNSPEC
// values on Windows, but an empty address of the same family on Linux/MacOS X.
bool IsUnspecOrEmptyIP(const IPAddress& address);

}  // namespace rtc

#endif  // RTC_BASE_SOCKET_UNITTEST_H_
