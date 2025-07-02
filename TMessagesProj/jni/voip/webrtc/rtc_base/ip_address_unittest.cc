/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "rtc_base/ip_address.h"

#include "absl/strings/string_view.h"
#include "test/gtest.h"

namespace rtc {

static const unsigned int kIPv4AddrSize = 4;
static const unsigned int kIPv6AddrSize = 16;
static const unsigned int kIPv4RFC1918Addr = 0xC0A80701;
static const unsigned int kIPv4PublicAddr = 0x01020304;
static const unsigned int kIPv4RFC6598Addr = 0x64410801;
static const unsigned int kIPv4LinkLocalAddr = 0xA9FE10C1;  // 169.254.16.193
static const in6_addr kIPv6LinkLocalAddr = {
    {{0xfe, 0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xbe, 0x30, 0x5b, 0xff,
      0xfe, 0xe5, 0x00, 0xc3}}};
static const in6_addr kIPv6PublicAddr = {
    {{0x24, 0x01, 0xfa, 0x00, 0x00, 0x04, 0x10, 0x00, 0xbe, 0x30, 0x5b, 0xff,
      0xfe, 0xe5, 0x00, 0xc3}}};
static const in6_addr kIPv6PublicAddr2 = {
    {{0x24, 0x01, 0x00, 0x00, 0x00, 0x00, 0x10, 0x00, 0xbe, 0x30, 0x5b, 0xff,
      0xfe, 0xe5, 0x00, 0xc3}}};
static const in6_addr kIPv4MappedAnyAddr = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff,
      0x00, 0x00, 0x00, 0x00}}};
static const in6_addr kIPv4MappedRFC1918Addr = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff,
      0xc0, 0xa8, 0x07, 0x01}}};
static const in6_addr kIPv4MappedPublicAddr = {
    {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xff, 0xff,
      0x01, 0x02, 0x03, 0x04}}};

static const std::string kIPv4AnyAddrString = "0.0.0.0";
static const std::string kIPv4LoopbackAddrString = "127.0.0.1";
static const std::string kIPv4RFC1918AddrString = "192.168.7.1";
static const std::string kIPv4RFC6598AddrString = "100.65.8.1";
static const std::string kIPv4PublicAddrString = "1.2.3.4";
static const std::string kIPv4PublicAddrAnonymizedString = "1.2.3.x";
static const std::string kIPv6AnyAddrString = "::";
static const std::string kIPv6LoopbackAddrString = "::1";
static const std::string kIPv6LinkLocalAddrString = "fe80::be30:5bff:fee5:c3";
static const std::string kIPv6EuiAddrString =
    "2620:0:1008:1201:a248:1cff:fe98:360";
static const std::string kIPv6TemporaryAddrString =
    "2620:0:1008:1201:2089:6dda:385e:80c0";
static const std::string kIPv6PublicAddrString =
    "2401:fa00:4:1000:be30:5bff:fee5:c3";
static const std::string kIPv6PublicAddr2String =
    "2401::1000:be30:5bff:fee5:c3";
static const std::string kIPv6PublicAddrAnonymizedString =
    "2401:fa00:4:x:x:x:x:x";
static const std::string kIPv6PublicAddr2AnonymizedString =
    "2401:0:0:x:x:x:x:x";
static const std::string kIPv4MappedV4StyleAddrString = "::ffff:192.168.7.1";

static const std::string kIPv4BrokenString1 = "192.168.7.";
static const std::string kIPv4BrokenString2 = "192.168.7.1.1";
static const std::string kIPv4BrokenString3 = "192.168.7.1:80";
static const std::string kIPv4BrokenString4 = "192.168.7.ONE";
static const std::string kIPv4BrokenString5 = "-192.168.7.1";
static const std::string kIPv4BrokenString6 = "256.168.7.1";
static const std::string kIPv6BrokenString1 = "2401:fa00:4:1000:be30";
static const std::string kIPv6BrokenString2 =
    "2401:fa00:4:1000:be30:5bff:fee5:c3:1";
static const std::string kIPv6BrokenString3 =
    "[2401:fa00:4:1000:be30:5bff:fee5:c3]:1";
static const std::string kIPv6BrokenString4 = "2401::4::be30";
static const std::string kIPv6BrokenString5 = "2401:::4:fee5:be30";
static const std::string kIPv6BrokenString6 =
    "2401f:fa00:4:1000:be30:5bff:fee5:c3";
static const std::string kIPv6BrokenString7 =
    "2401:ga00:4:1000:be30:5bff:fee5:c3";
static const std::string kIPv6BrokenString8 =
    "2401:fa000:4:1000:be30:5bff:fee5:c3";
static const std::string kIPv6BrokenString9 =
    "2401:fal0:4:1000:be30:5bff:fee5:c3";
static const std::string kIPv6BrokenString10 = "::ffff:192.168.7.";
static const std::string kIPv6BrokenString11 = "::ffff:192.168.7.1.1.1";
static const std::string kIPv6BrokenString12 = "::fffe:192.168.7.1";
static const std::string kIPv6BrokenString13 = "::ffff:192.168.7.ff";
static const std::string kIPv6BrokenString14 =
    "0x2401:fa00:4:1000:be30:5bff:fee5:c3";

bool AreEqual(const IPAddress& addr, const IPAddress& addr2) {
  if ((IPIsAny(addr) != IPIsAny(addr2)) ||
      (IPIsLoopback(addr) != IPIsLoopback(addr2)) ||
      (IPIsPrivate(addr) != IPIsPrivate(addr2)) ||
      (HashIP(addr) != HashIP(addr2)) || (addr.Size() != addr2.Size()) ||
      (addr.family() != addr2.family()) ||
      (addr.ToString() != addr2.ToString())) {
    return false;
  }
  in_addr v4addr, v4addr2;
  v4addr = addr.ipv4_address();
  v4addr2 = addr2.ipv4_address();
  if (0 != memcmp(&v4addr, &v4addr2, sizeof(v4addr))) {
    return false;
  }
  in6_addr v6addr, v6addr2;
  v6addr = addr.ipv6_address();
  v6addr2 = addr2.ipv6_address();
  if (0 != memcmp(&v6addr, &v6addr2, sizeof(v6addr))) {
    return false;
  }
  return true;
}

bool BrokenIPStringFails(absl::string_view broken) {
  IPAddress addr(0);  // Intentionally make it v4.
  if (IPFromString(kIPv4BrokenString1, &addr)) {
    return false;
  }
  return addr.family() == AF_UNSPEC;
}

bool CheckMaskCount(absl::string_view mask, int expected_length) {
  IPAddress addr;
  return IPFromString(mask, &addr) &&
         (expected_length == CountIPMaskBits(addr));
}

bool TryInvalidMaskCount(absl::string_view mask) {
  // We don't care about the result at all, but we do want to know if
  // CountIPMaskBits is going to crash or infinite loop or something.
  IPAddress addr;
  if (!IPFromString(mask, &addr)) {
    return false;
  }
  CountIPMaskBits(addr);
  return true;
}

bool CheckTruncateIP(absl::string_view initial,
                     int truncate_length,
                     absl::string_view expected_result) {
  IPAddress addr, expected;
  IPFromString(initial, &addr);
  IPFromString(expected_result, &expected);
  IPAddress truncated = TruncateIP(addr, truncate_length);
  return truncated == expected;
}

TEST(IPAddressTest, TestDefaultCtor) {
  IPAddress addr;
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));

  EXPECT_EQ(0U, addr.Size());
  EXPECT_EQ(AF_UNSPEC, addr.family());
  EXPECT_EQ("", addr.ToString());
}

TEST(IPAddressTest, TestInAddrCtor) {
  in_addr v4addr;

  // Test V4 Any address.
  v4addr.s_addr = INADDR_ANY;
  IPAddress addr(v4addr);
  EXPECT_TRUE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4AnyAddrString, addr.ToString());

  // Test a V4 loopback address.
  v4addr.s_addr = htonl(INADDR_LOOPBACK);
  addr = IPAddress(v4addr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_TRUE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4LoopbackAddrString, addr.ToString());

  // Test an RFC1918 address.
  v4addr.s_addr = htonl(kIPv4RFC1918Addr);
  addr = IPAddress(v4addr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4RFC1918AddrString, addr.ToString());

  // Test an shared (RFC6598) address.
  v4addr.s_addr = htonl(kIPv4RFC6598Addr);
  addr = IPAddress(v4addr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_TRUE(IPIsSharedNetwork(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4RFC6598AddrString, addr.ToString());

  // Test a 'normal' v4 address.
  v4addr.s_addr = htonl(kIPv4PublicAddr);
  addr = IPAddress(v4addr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4PublicAddrString, addr.ToString());
}

TEST(IPAddressTest, TestInAddr6Ctor) {
  // Test v6 empty.
  IPAddress addr(in6addr_any);
  EXPECT_TRUE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv6AddrSize, addr.Size());
  EXPECT_EQ(kIPv6AnyAddrString, addr.ToString());

  // Test v6 loopback.
  addr = IPAddress(in6addr_loopback);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_TRUE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv6AddrSize, addr.Size());
  EXPECT_EQ(kIPv6LoopbackAddrString, addr.ToString());

  // Test v6 link-local.
  addr = IPAddress(kIPv6LinkLocalAddr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv6AddrSize, addr.Size());
  EXPECT_EQ(kIPv6LinkLocalAddrString, addr.ToString());

  // Test v6 global address.
  addr = IPAddress(kIPv6PublicAddr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv6AddrSize, addr.Size());
  EXPECT_EQ(kIPv6PublicAddrString, addr.ToString());
}

TEST(IPAddressTest, TestUint32Ctor) {
  // Test V4 Any address.
  IPAddress addr(0);
  EXPECT_TRUE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4AnyAddrString, addr.ToString());

  // Test a V4 loopback address.
  addr = IPAddress(INADDR_LOOPBACK);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_TRUE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4LoopbackAddrString, addr.ToString());

  // Test an RFC1918 address.
  addr = IPAddress(kIPv4RFC1918Addr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsSharedNetwork(addr));
  EXPECT_TRUE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4RFC1918AddrString, addr.ToString());

  // Test a 'normal' v4 address.
  addr = IPAddress(kIPv4PublicAddr);
  EXPECT_FALSE(IPIsAny(addr));
  EXPECT_FALSE(IPIsLoopback(addr));
  EXPECT_FALSE(IPIsPrivate(addr));
  EXPECT_EQ(kIPv4AddrSize, addr.Size());
  EXPECT_EQ(kIPv4PublicAddrString, addr.ToString());
}

TEST(IPAddressTest, TestCopyCtor) {
  in_addr v4addr;
  v4addr.s_addr = htonl(kIPv4PublicAddr);
  IPAddress addr(v4addr);
  IPAddress addr2(addr);

  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(INADDR_ANY);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(INADDR_LOOPBACK);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(kIPv4PublicAddr);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(kIPv4RFC1918Addr);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(in6addr_any);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(in6addr_loopback);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(kIPv6LinkLocalAddr);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr = IPAddress(kIPv6PublicAddr);
  addr2 = IPAddress(addr);
  EXPECT_TRUE(AreEqual(addr, addr2));
}

TEST(IPAddressTest, TestEquality) {
  // Check v4 equality
  in_addr v4addr, v4addr2;
  v4addr.s_addr = htonl(kIPv4PublicAddr);
  v4addr2.s_addr = htonl(kIPv4PublicAddr + 1);
  IPAddress addr(v4addr);
  IPAddress addr2(v4addr2);
  IPAddress addr3(v4addr);

  EXPECT_TRUE(addr == addr);
  EXPECT_TRUE(addr2 == addr2);
  EXPECT_TRUE(addr3 == addr3);
  EXPECT_TRUE(addr == addr3);
  EXPECT_TRUE(addr3 == addr);
  EXPECT_FALSE(addr2 == addr);
  EXPECT_FALSE(addr2 == addr3);
  EXPECT_FALSE(addr == addr2);
  EXPECT_FALSE(addr3 == addr2);

  // Check v6 equality
  IPAddress addr4(kIPv6PublicAddr);
  IPAddress addr5(kIPv6LinkLocalAddr);
  IPAddress addr6(kIPv6PublicAddr);

  EXPECT_TRUE(addr4 == addr4);
  EXPECT_TRUE(addr5 == addr5);
  EXPECT_TRUE(addr4 == addr6);
  EXPECT_TRUE(addr6 == addr4);
  EXPECT_FALSE(addr4 == addr5);
  EXPECT_FALSE(addr5 == addr4);
  EXPECT_FALSE(addr6 == addr5);
  EXPECT_FALSE(addr5 == addr6);

  // Check v4/v6 cross-equality
  EXPECT_FALSE(addr == addr4);
  EXPECT_FALSE(addr == addr5);
  EXPECT_FALSE(addr == addr6);
  EXPECT_FALSE(addr4 == addr);
  EXPECT_FALSE(addr5 == addr);
  EXPECT_FALSE(addr6 == addr);
  EXPECT_FALSE(addr2 == addr4);
  EXPECT_FALSE(addr2 == addr5);
  EXPECT_FALSE(addr2 == addr6);
  EXPECT_FALSE(addr4 == addr2);
  EXPECT_FALSE(addr5 == addr2);
  EXPECT_FALSE(addr6 == addr2);
  EXPECT_FALSE(addr3 == addr4);
  EXPECT_FALSE(addr3 == addr5);
  EXPECT_FALSE(addr3 == addr6);
  EXPECT_FALSE(addr4 == addr3);
  EXPECT_FALSE(addr5 == addr3);
  EXPECT_FALSE(addr6 == addr3);

  // Special cases: loopback and any.
  // They're special but they're still not equal.
  IPAddress v4loopback(htonl(INADDR_LOOPBACK));
  IPAddress v6loopback(in6addr_loopback);
  EXPECT_FALSE(v4loopback == v6loopback);

  IPAddress v4any(0);
  IPAddress v6any(in6addr_any);
  EXPECT_FALSE(v4any == v6any);
}

TEST(IPAddressTest, TestComparison) {
  // Defined in 'ascending' order.
  // v6 > v4, and intra-family sorting is purely numerical
  IPAddress addr0;                      // AF_UNSPEC
  IPAddress addr1(INADDR_ANY);          // 0.0.0.0
  IPAddress addr2(kIPv4PublicAddr);     // 1.2.3.4
  IPAddress addr3(INADDR_LOOPBACK);     // 127.0.0.1
  IPAddress addr4(kIPv4RFC1918Addr);    // 192.168.7.1.
  IPAddress addr5(in6addr_any);         // ::
  IPAddress addr6(in6addr_loopback);    // ::1
  IPAddress addr7(kIPv6PublicAddr);     // 2401....
  IPAddress addr8(kIPv6LinkLocalAddr);  // fe80....

  EXPECT_TRUE(addr0 < addr1);
  EXPECT_TRUE(addr1 < addr2);
  EXPECT_TRUE(addr2 < addr3);
  EXPECT_TRUE(addr3 < addr4);
  EXPECT_TRUE(addr4 < addr5);
  EXPECT_TRUE(addr5 < addr6);
  EXPECT_TRUE(addr6 < addr7);
  EXPECT_TRUE(addr7 < addr8);

  EXPECT_FALSE(addr0 > addr1);
  EXPECT_FALSE(addr1 > addr2);
  EXPECT_FALSE(addr2 > addr3);
  EXPECT_FALSE(addr3 > addr4);
  EXPECT_FALSE(addr4 > addr5);
  EXPECT_FALSE(addr5 > addr6);
  EXPECT_FALSE(addr6 > addr7);
  EXPECT_FALSE(addr7 > addr8);

  EXPECT_FALSE(addr0 > addr0);
  EXPECT_FALSE(addr1 > addr1);
  EXPECT_FALSE(addr2 > addr2);
  EXPECT_FALSE(addr3 > addr3);
  EXPECT_FALSE(addr4 > addr4);
  EXPECT_FALSE(addr5 > addr5);
  EXPECT_FALSE(addr6 > addr6);
  EXPECT_FALSE(addr7 > addr7);
  EXPECT_FALSE(addr8 > addr8);

  EXPECT_FALSE(addr0 < addr0);
  EXPECT_FALSE(addr1 < addr1);
  EXPECT_FALSE(addr2 < addr2);
  EXPECT_FALSE(addr3 < addr3);
  EXPECT_FALSE(addr4 < addr4);
  EXPECT_FALSE(addr5 < addr5);
  EXPECT_FALSE(addr6 < addr6);
  EXPECT_FALSE(addr7 < addr7);
  EXPECT_FALSE(addr8 < addr8);
}

TEST(IPAddressTest, TestFromString) {
  IPAddress addr;
  IPAddress addr2;
  addr2 = IPAddress(INADDR_ANY);

  EXPECT_TRUE(IPFromString(kIPv4AnyAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv4AnyAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(INADDR_LOOPBACK);
  EXPECT_TRUE(IPFromString(kIPv4LoopbackAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv4LoopbackAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(kIPv4RFC1918Addr);
  EXPECT_TRUE(IPFromString(kIPv4RFC1918AddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv4RFC1918AddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(kIPv4PublicAddr);
  EXPECT_TRUE(IPFromString(kIPv4PublicAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv4PublicAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(in6addr_any);
  EXPECT_TRUE(IPFromString(kIPv6AnyAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv6AnyAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(in6addr_loopback);
  EXPECT_TRUE(IPFromString(kIPv6LoopbackAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv6LoopbackAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(kIPv6LinkLocalAddr);
  EXPECT_TRUE(IPFromString(kIPv6LinkLocalAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv6LinkLocalAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(kIPv6PublicAddr);
  EXPECT_TRUE(IPFromString(kIPv6PublicAddrString, &addr));
  EXPECT_EQ(addr.ToString(), kIPv6PublicAddrString);
  EXPECT_TRUE(AreEqual(addr, addr2));

  addr2 = IPAddress(kIPv4MappedRFC1918Addr);
  EXPECT_TRUE(IPFromString(kIPv4MappedV4StyleAddrString, &addr));
  EXPECT_TRUE(AreEqual(addr, addr2));

  // Broken cases, should set addr to AF_UNSPEC.
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString1);
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString2);
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString3);
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString4);
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString5);
  EXPECT_PRED1(BrokenIPStringFails, kIPv4BrokenString6);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString1);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString2);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString3);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString4);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString5);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString6);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString7);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString8);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString9);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString10);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString11);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString12);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString13);
  EXPECT_PRED1(BrokenIPStringFails, kIPv6BrokenString14);
}

TEST(IPAddressTest, TestIPFromAddrInfo) {
  struct sockaddr_in expected4;
  struct sockaddr_in6 expected6;
  struct addrinfo test_info;
  struct addrinfo next_info;
  memset(&next_info, 'A', sizeof(next_info));
  test_info.ai_next = &next_info;
  // Check that we can get an IPv4 address out.
  test_info.ai_addr = reinterpret_cast<struct sockaddr*>(&expected4);
  expected4.sin_addr.s_addr = HostToNetwork32(kIPv4PublicAddr);
  expected4.sin_family = AF_INET;
  IPAddress expected(kIPv4PublicAddr);
  IPAddress addr;
  EXPECT_TRUE(IPFromAddrInfo(&test_info, &addr));
  EXPECT_EQ(expected, addr);
  // Check that we can get an IPv6 address out.
  expected6.sin6_addr = kIPv6PublicAddr;
  expected6.sin6_family = AF_INET6;
  expected = IPAddress(kIPv6PublicAddr);
  test_info.ai_addr = reinterpret_cast<struct sockaddr*>(&expected6);
  EXPECT_TRUE(IPFromAddrInfo(&test_info, &addr));
  EXPECT_EQ(expected, addr);
  // Check that unspec fails.
  expected6.sin6_family = AF_UNSPEC;
  EXPECT_FALSE(IPFromAddrInfo(&test_info, &addr));
  // Check a zeroed out addrinfo doesn't crash us.
  memset(&next_info, 0, sizeof(next_info));
  EXPECT_FALSE(IPFromAddrInfo(&next_info, &addr));
}

TEST(IPAddressTest, TestIsPrivate) {
  EXPECT_FALSE(IPIsPrivate(IPAddress(INADDR_ANY)));
  EXPECT_FALSE(IPIsPrivate(IPAddress(kIPv4PublicAddr)));
  EXPECT_FALSE(IPIsPrivate(IPAddress(in6addr_any)));
  EXPECT_FALSE(IPIsPrivate(IPAddress(kIPv6PublicAddr)));
  EXPECT_FALSE(IPIsPrivate(IPAddress(kIPv4MappedAnyAddr)));
  EXPECT_FALSE(IPIsPrivate(IPAddress(kIPv4MappedPublicAddr)));

  EXPECT_TRUE(IPIsPrivate(IPAddress(kIPv4RFC1918Addr)));
  EXPECT_TRUE(IPIsPrivate(IPAddress(kIPv4RFC6598Addr)));
  EXPECT_TRUE(IPIsPrivate(IPAddress(INADDR_LOOPBACK)));
  EXPECT_TRUE(IPIsPrivate(IPAddress(in6addr_loopback)));
  EXPECT_TRUE(IPIsPrivate(IPAddress(kIPv6LinkLocalAddr)));
}

TEST(IPAddressTest, TestIsNil) {
  IPAddress addr;
  EXPECT_TRUE(IPAddress().IsNil());

  EXPECT_TRUE(IPFromString(kIPv6AnyAddrString, &addr));
  EXPECT_FALSE(addr.IsNil());

  EXPECT_TRUE(IPFromString(kIPv4AnyAddrString, &addr));
  EXPECT_FALSE(addr.IsNil());

  EXPECT_FALSE(IPAddress(kIPv4PublicAddr).IsNil());
}

TEST(IPAddressTest, TestIsLoopback) {
  EXPECT_FALSE(IPIsLoopback(IPAddress(INADDR_ANY)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(kIPv4PublicAddr)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(in6addr_any)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(kIPv4RFC6598Addr)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(kIPv6PublicAddr)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(kIPv4MappedAnyAddr)));
  EXPECT_FALSE(IPIsLoopback(IPAddress(kIPv4MappedPublicAddr)));

  EXPECT_TRUE(IPIsLoopback(IPAddress(INADDR_LOOPBACK)));
  // Try an address in the loopback range (127.0.0.0/8) other than the typical
  // 127.0.0.1.
  EXPECT_TRUE(IPIsLoopback(IPAddress(0x7f010203)));
  EXPECT_TRUE(IPIsLoopback(IPAddress(in6addr_loopback)));
}

TEST(IPAddressTest, TestIsLinkLocal) {
  // "any" addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(INADDR_ANY)));
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(in6addr_any)));
  // loopback addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(INADDR_LOOPBACK)));
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(in6addr_loopback)));
  // shared addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4RFC6598Addr)));
  // public addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4PublicAddr)));
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv6PublicAddr)));
  // private network addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4RFC1918Addr)));
  // mapped addresses
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4MappedAnyAddr)));
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4MappedPublicAddr)));
  EXPECT_FALSE(IPIsLinkLocal(IPAddress(kIPv4MappedRFC1918Addr)));

  // link-local network addresses
  EXPECT_TRUE(IPIsLinkLocal(IPAddress(kIPv4LinkLocalAddr)));
  EXPECT_TRUE(IPIsLinkLocal(IPAddress(kIPv6LinkLocalAddr)));
}

// Verify that IPIsAny catches all cases of "any" address.
TEST(IPAddressTest, TestIsAny) {
  IPAddress addr;

  EXPECT_TRUE(IPFromString(kIPv6AnyAddrString, &addr));
  EXPECT_TRUE(IPIsAny(addr));

  EXPECT_TRUE(IPFromString(kIPv4AnyAddrString, &addr));
  EXPECT_TRUE(IPIsAny(addr));

  EXPECT_TRUE(IPIsAny(IPAddress(kIPv4MappedAnyAddr)));
}

TEST(IPAddressTest, TestIsEui64) {
  IPAddress addr;
  EXPECT_TRUE(IPFromString(kIPv6EuiAddrString, &addr));
  EXPECT_TRUE(IPIsMacBased(addr));

  EXPECT_TRUE(IPFromString(kIPv6TemporaryAddrString, &addr));
  EXPECT_FALSE(IPIsMacBased(addr));

  EXPECT_TRUE(IPFromString(kIPv6LinkLocalAddrString, &addr));
  EXPECT_TRUE(IPIsMacBased(addr));

  EXPECT_TRUE(IPFromString(kIPv6AnyAddrString, &addr));
  EXPECT_FALSE(IPIsMacBased(addr));

  EXPECT_TRUE(IPFromString(kIPv6LoopbackAddrString, &addr));
  EXPECT_FALSE(IPIsMacBased(addr));
}

TEST(IPAddressTest, TestNormalized) {
  // Check normalizing a ::ffff:a.b.c.d address.
  IPAddress addr;
  EXPECT_TRUE(IPFromString(kIPv4MappedV4StyleAddrString, &addr));
  IPAddress addr2(kIPv4RFC1918Addr);
  addr = addr.Normalized();
  EXPECT_EQ(addr2, addr);

  // Check normalizing a ::ffff:aabb:ccdd address.
  addr = IPAddress(kIPv4MappedPublicAddr);
  addr2 = IPAddress(kIPv4PublicAddr);
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);

  // Check that a non-mapped v6 addresses isn't altered.
  addr = IPAddress(kIPv6PublicAddr);
  addr2 = IPAddress(kIPv6PublicAddr);
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);

  // Check that addresses that look a bit like mapped addresses aren't altered
  EXPECT_TRUE(IPFromString("fe80::ffff:0102:0304", &addr));
  addr2 = addr;
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);
  EXPECT_TRUE(IPFromString("::0102:0304", &addr));
  addr2 = addr;
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);
  // This string should 'work' as an IP address but is not a mapped address,
  // so it shouldn't change on normalization.
  EXPECT_TRUE(IPFromString("::192.168.7.1", &addr));
  addr2 = addr;
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);

  // Check that v4 addresses aren't altered.
  addr = IPAddress(htonl(kIPv4PublicAddr));
  addr2 = IPAddress(htonl(kIPv4PublicAddr));
  addr = addr.Normalized();
  EXPECT_EQ(addr, addr2);
}

TEST(IPAddressTest, TestAsIPv6Address) {
  IPAddress addr(kIPv4PublicAddr);
  IPAddress addr2(kIPv4MappedPublicAddr);
  addr = addr.AsIPv6Address();
  EXPECT_EQ(addr, addr2);

  addr = IPAddress(kIPv4MappedPublicAddr);
  addr2 = IPAddress(kIPv4MappedPublicAddr);
  addr = addr.AsIPv6Address();
  EXPECT_EQ(addr, addr2);

  addr = IPAddress(kIPv6PublicAddr);
  addr2 = IPAddress(kIPv6PublicAddr);
  addr = addr.AsIPv6Address();
  EXPECT_EQ(addr, addr2);
}

TEST(IPAddressTest, TestCountIPMaskBits) {
  IPAddress mask;
  // IPv4 on byte boundaries
  EXPECT_PRED2(CheckMaskCount, "255.255.255.255", 32);
  EXPECT_PRED2(CheckMaskCount, "255.255.255.0", 24);
  EXPECT_PRED2(CheckMaskCount, "255.255.0.0", 16);
  EXPECT_PRED2(CheckMaskCount, "255.0.0.0", 8);
  EXPECT_PRED2(CheckMaskCount, "0.0.0.0", 0);

  // IPv4 not on byte boundaries
  EXPECT_PRED2(CheckMaskCount, "128.0.0.0", 1);
  EXPECT_PRED2(CheckMaskCount, "224.0.0.0", 3);
  EXPECT_PRED2(CheckMaskCount, "255.248.0.0", 13);
  EXPECT_PRED2(CheckMaskCount, "255.255.224.0", 19);
  EXPECT_PRED2(CheckMaskCount, "255.255.255.252", 30);

  // V6 on byte boundaries
  EXPECT_PRED2(CheckMaskCount, "::", 0);
  EXPECT_PRED2(CheckMaskCount, "ff00::", 8);
  EXPECT_PRED2(CheckMaskCount, "ffff::", 16);
  EXPECT_PRED2(CheckMaskCount, "ffff:ff00::", 24);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff::", 32);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ff00::", 40);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff::", 48);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ff00::", 56);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff::", 64);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ff00::", 72);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff::", 80);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ff00::", 88);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff::", 96);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ff00:0000", 104);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:0000", 112);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ff00", 120);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", 128);

  // V6 not on byte boundaries.
  EXPECT_PRED2(CheckMaskCount, "8000::", 1);
  EXPECT_PRED2(CheckMaskCount, "ff80::", 9);
  EXPECT_PRED2(CheckMaskCount, "ffff:fe00::", 23);
  EXPECT_PRED2(CheckMaskCount, "ffff:fffe::", 31);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:e000::", 35);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffe0::", 43);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:f800::", 53);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:fff8::", 61);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:fc00::", 70);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:fffc::", 78);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:8000::", 81);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ff80::", 89);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:fe00::", 103);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:fffe:0000", 111);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fc00", 118);
  EXPECT_PRED2(CheckMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:fffc", 126);

  // Non-contiguous ranges. These are invalid but lets test them
  // to make sure they don't crash anything or infinite loop or something.
  EXPECT_PRED1(TryInvalidMaskCount, "217.0.0.0");
  EXPECT_PRED1(TryInvalidMaskCount, "255.185.0.0");
  EXPECT_PRED1(TryInvalidMaskCount, "255.255.251.0");
  EXPECT_PRED1(TryInvalidMaskCount, "255.255.251.255");
  EXPECT_PRED1(TryInvalidMaskCount, "255.255.254.201");
  EXPECT_PRED1(TryInvalidMaskCount, "::1");
  EXPECT_PRED1(TryInvalidMaskCount, "fe80::1");
  EXPECT_PRED1(TryInvalidMaskCount, "ff80::1");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff::1");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ff00:1::1");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff::ffff:1");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ff00:1::");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff::ff00");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ff00:1234::");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:0012::ffff");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ff01::");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:7f00::");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:ff7a::");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:7f00:0000");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ff70:0000");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:0211");
  EXPECT_PRED1(TryInvalidMaskCount, "ffff:ffff:ffff:ffff:ffff:ffff:ffff:ff7f");
}

TEST(IPAddressTest, TestTruncateIP) {
  EXPECT_PRED3(CheckTruncateIP, "255.255.255.255", 24, "255.255.255.0");
  EXPECT_PRED3(CheckTruncateIP, "255.255.255.255", 16, "255.255.0.0");
  EXPECT_PRED3(CheckTruncateIP, "255.255.255.255", 8, "255.0.0.0");
  EXPECT_PRED3(CheckTruncateIP, "202.67.7.255", 24, "202.67.7.0");
  EXPECT_PRED3(CheckTruncateIP, "202.129.65.205", 16, "202.129.0.0");
  EXPECT_PRED3(CheckTruncateIP, "55.25.2.77", 8, "55.0.0.0");
  EXPECT_PRED3(CheckTruncateIP, "74.128.99.254", 1, "0.0.0.0");
  EXPECT_PRED3(CheckTruncateIP, "106.55.99.254", 3, "96.0.0.0");
  EXPECT_PRED3(CheckTruncateIP, "172.167.53.222", 13, "172.160.0.0");
  EXPECT_PRED3(CheckTruncateIP, "255.255.224.0", 18, "255.255.192.0");
  EXPECT_PRED3(CheckTruncateIP, "255.255.255.252", 28, "255.255.255.240");

  EXPECT_PRED3(CheckTruncateIP, "fe80:1111:2222:3333:4444:5555:6666:7777", 1,
               "8000::");
  EXPECT_PRED3(CheckTruncateIP, "fff0:1111:2222:3333:4444:5555:6666:7777", 9,
               "ff80::");
  EXPECT_PRED3(CheckTruncateIP, "ffff:ff80:1111:2222:3333:4444:5555:6666", 23,
               "ffff:fe00::");
  EXPECT_PRED3(CheckTruncateIP, "ffff:ff80:1111:2222:3333:4444:5555:6666", 32,
               "ffff:ff80::");
  EXPECT_PRED3(CheckTruncateIP, "2400:f9af:e456:1111:2222:3333:4444:5555", 35,
               "2400:f9af:e000::");
  EXPECT_PRED3(CheckTruncateIP, "9999:1111:2233:4444:5555:6666:7777:8888", 53,
               "9999:1111:2233:4000::");
  EXPECT_PRED3(CheckTruncateIP, "9999:1111:2233:4567:5555:6666:7777:8888", 64,
               "9999:1111:2233:4567::");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 68,
               "1111:2222:3333:4444:5000::");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 92,
               "1111:2222:3333:4444:5555:6660::");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 96,
               "1111:2222:3333:4444:5555:6666::");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 105,
               "1111:2222:3333:4444:5555:6666:7700::");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 124,
               "1111:2222:3333:4444:5555:6666:7777:8880");

  // Slightly degenerate cases
  EXPECT_PRED3(CheckTruncateIP, "202.165.33.127", 32, "202.165.33.127");
  EXPECT_PRED3(CheckTruncateIP, "235.105.77.12", 0, "0.0.0.0");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 128,
               "1111:2222:3333:4444:5555:6666:7777:8888");
  EXPECT_PRED3(CheckTruncateIP, "1111:2222:3333:4444:5555:6666:7777:8888", 0,
               "::");
}

TEST(IPAddressTest, TestCategorizeIPv6) {
  // Test determining if an IPAddress is 6Bone/6To4/Teredo/etc.
  // IPv4 address, should be none of these (not even v4compat/v4mapped).
  IPAddress v4_addr(kIPv4PublicAddr);
  EXPECT_FALSE(IPIs6Bone(v4_addr));
  EXPECT_FALSE(IPIs6To4(v4_addr));
  EXPECT_FALSE(IPIsSiteLocal(v4_addr));
  EXPECT_FALSE(IPIsTeredo(v4_addr));
  EXPECT_FALSE(IPIsULA(v4_addr));
  EXPECT_FALSE(IPIsV4Compatibility(v4_addr));
  EXPECT_FALSE(IPIsV4Mapped(v4_addr));
  // Linklocal (fe80::/16) adddress; should be none of these.
  IPAddress linklocal_addr(kIPv6LinkLocalAddr);
  EXPECT_FALSE(IPIs6Bone(linklocal_addr));
  EXPECT_FALSE(IPIs6To4(linklocal_addr));
  EXPECT_FALSE(IPIsSiteLocal(linklocal_addr));
  EXPECT_FALSE(IPIsTeredo(linklocal_addr));
  EXPECT_FALSE(IPIsULA(linklocal_addr));
  EXPECT_FALSE(IPIsV4Compatibility(linklocal_addr));
  EXPECT_FALSE(IPIsV4Mapped(linklocal_addr));
  // 'Normal' IPv6 address, should also be none of these.
  IPAddress normal_addr(kIPv6PublicAddr);
  EXPECT_FALSE(IPIs6Bone(normal_addr));
  EXPECT_FALSE(IPIs6To4(normal_addr));
  EXPECT_FALSE(IPIsSiteLocal(normal_addr));
  EXPECT_FALSE(IPIsTeredo(normal_addr));
  EXPECT_FALSE(IPIsULA(normal_addr));
  EXPECT_FALSE(IPIsV4Compatibility(normal_addr));
  EXPECT_FALSE(IPIsV4Mapped(normal_addr));
  // IPv4 mapped address (::ffff:123.123.123.123)
  IPAddress v4mapped_addr(kIPv4MappedPublicAddr);
  EXPECT_TRUE(IPIsV4Mapped(v4mapped_addr));
  EXPECT_FALSE(IPIsV4Compatibility(v4mapped_addr));
  EXPECT_FALSE(IPIs6Bone(v4mapped_addr));
  EXPECT_FALSE(IPIs6To4(v4mapped_addr));
  EXPECT_FALSE(IPIsSiteLocal(v4mapped_addr));
  EXPECT_FALSE(IPIsTeredo(v4mapped_addr));
  EXPECT_FALSE(IPIsULA(v4mapped_addr));
  // IPv4 compatibility address (::123.123.123.123)
  IPAddress v4compat_addr;
  IPFromString("::192.168.7.1", &v4compat_addr);
  EXPECT_TRUE(IPIsV4Compatibility(v4compat_addr));
  EXPECT_FALSE(IPIs6Bone(v4compat_addr));
  EXPECT_FALSE(IPIs6To4(v4compat_addr));
  EXPECT_FALSE(IPIsSiteLocal(v4compat_addr));
  EXPECT_FALSE(IPIsTeredo(v4compat_addr));
  EXPECT_FALSE(IPIsULA(v4compat_addr));
  EXPECT_FALSE(IPIsV4Mapped(v4compat_addr));
  // 6Bone address (3FFE::/16)
  IPAddress sixbone_addr;
  IPFromString("3FFE:123:456::789:123", &sixbone_addr);
  EXPECT_TRUE(IPIs6Bone(sixbone_addr));
  EXPECT_FALSE(IPIs6To4(sixbone_addr));
  EXPECT_FALSE(IPIsSiteLocal(sixbone_addr));
  EXPECT_FALSE(IPIsTeredo(sixbone_addr));
  EXPECT_FALSE(IPIsULA(sixbone_addr));
  EXPECT_FALSE(IPIsV4Mapped(sixbone_addr));
  EXPECT_FALSE(IPIsV4Compatibility(sixbone_addr));
  // Unique Local Address (FC::/7)
  IPAddress ula_addr;
  IPFromString("FC00:123:456::789:123", &ula_addr);
  EXPECT_TRUE(IPIsULA(ula_addr));
  EXPECT_FALSE(IPIs6Bone(ula_addr));
  EXPECT_FALSE(IPIs6To4(ula_addr));
  EXPECT_FALSE(IPIsSiteLocal(ula_addr));
  EXPECT_FALSE(IPIsTeredo(ula_addr));
  EXPECT_FALSE(IPIsV4Mapped(ula_addr));
  EXPECT_FALSE(IPIsV4Compatibility(ula_addr));
  // 6To4 Address (2002::/16)
  IPAddress sixtofour_addr;
  IPFromString("2002:123:456::789:123", &sixtofour_addr);
  EXPECT_TRUE(IPIs6To4(sixtofour_addr));
  EXPECT_FALSE(IPIs6Bone(sixtofour_addr));
  EXPECT_FALSE(IPIsSiteLocal(sixtofour_addr));
  EXPECT_FALSE(IPIsTeredo(sixtofour_addr));
  EXPECT_FALSE(IPIsULA(sixtofour_addr));
  EXPECT_FALSE(IPIsV4Compatibility(sixtofour_addr));
  EXPECT_FALSE(IPIsV4Mapped(sixtofour_addr));
  // Site Local address (FEC0::/10)
  IPAddress sitelocal_addr;
  IPFromString("FEC0:123:456::789:123", &sitelocal_addr);
  EXPECT_TRUE(IPIsSiteLocal(sitelocal_addr));
  EXPECT_FALSE(IPIs6Bone(sitelocal_addr));
  EXPECT_FALSE(IPIs6To4(sitelocal_addr));
  EXPECT_FALSE(IPIsTeredo(sitelocal_addr));
  EXPECT_FALSE(IPIsULA(sitelocal_addr));
  EXPECT_FALSE(IPIsV4Compatibility(sitelocal_addr));
  EXPECT_FALSE(IPIsV4Mapped(sitelocal_addr));
  // Teredo Address (2001:0000::/32)
  IPAddress teredo_addr;
  IPFromString("2001:0000:123:456::789:123", &teredo_addr);
  EXPECT_TRUE(IPIsTeredo(teredo_addr));
  EXPECT_FALSE(IPIsSiteLocal(teredo_addr));
  EXPECT_FALSE(IPIs6Bone(teredo_addr));
  EXPECT_FALSE(IPIs6To4(teredo_addr));
  EXPECT_FALSE(IPIsULA(teredo_addr));
  EXPECT_FALSE(IPIsV4Compatibility(teredo_addr));
  EXPECT_FALSE(IPIsV4Mapped(teredo_addr));
}

TEST(IPAddressTest, TestToSensitiveString) {
  IPAddress addr_v4 = IPAddress(kIPv4PublicAddr);
  IPAddress addr_v6 = IPAddress(kIPv6PublicAddr);
  IPAddress addr_v6_2 = IPAddress(kIPv6PublicAddr2);
  EXPECT_EQ(kIPv4PublicAddrString, addr_v4.ToString());
  EXPECT_EQ(kIPv6PublicAddrString, addr_v6.ToString());
  EXPECT_EQ(kIPv6PublicAddr2String, addr_v6_2.ToString());
  EXPECT_EQ(kIPv4PublicAddrAnonymizedString, addr_v4.ToSensitiveString());
  EXPECT_EQ(kIPv6PublicAddrAnonymizedString, addr_v6.ToSensitiveString());
  EXPECT_EQ(kIPv6PublicAddr2AnonymizedString, addr_v6_2.ToSensitiveString());
}

TEST(IPAddressTest, TestInterfaceAddress) {
  in6_addr addr;
  InterfaceAddress addr1(kIPv6PublicAddr, IPV6_ADDRESS_FLAG_TEMPORARY);
  EXPECT_EQ(addr1.ipv6_flags(), IPV6_ADDRESS_FLAG_TEMPORARY);
  EXPECT_EQ(addr1.family(), AF_INET6);

  addr = addr1.ipv6_address();
  EXPECT_TRUE(IN6_ARE_ADDR_EQUAL(&addr, &kIPv6PublicAddr));

  InterfaceAddress addr2 = addr1;
  EXPECT_EQ(addr1, addr2);
  EXPECT_EQ(addr2.ipv6_flags(), IPV6_ADDRESS_FLAG_TEMPORARY);
  addr = addr2.ipv6_address();
  EXPECT_TRUE(IN6_ARE_ADDR_EQUAL(&addr, &kIPv6PublicAddr));

  InterfaceAddress addr3(addr1);
  EXPECT_EQ(addr1, addr3);
  EXPECT_EQ(addr3.ipv6_flags(), IPV6_ADDRESS_FLAG_TEMPORARY);
  addr = addr3.ipv6_address();
  EXPECT_TRUE(IN6_ARE_ADDR_EQUAL(&addr, &kIPv6PublicAddr));

  InterfaceAddress addr4(kIPv6PublicAddr, IPV6_ADDRESS_FLAG_DEPRECATED);
  EXPECT_NE(addr1, addr4);

  // When you compare them as IPAddress, since operator==
  // is not virtual, it'll be equal.
  IPAddress* paddr1 = &addr1;
  IPAddress* paddr4 = &addr4;
  EXPECT_EQ(*paddr1, *paddr4);

  InterfaceAddress addr5(kIPv6LinkLocalAddr, IPV6_ADDRESS_FLAG_TEMPORARY);
  EXPECT_NE(addr1, addr5);
}

}  // namespace rtc
