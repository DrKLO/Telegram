/*
 *  Copyright 2004 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/transport/stun.h"

#include <string.h>

#include <memory>
#include <string>
#include <utility>

#include "rtc_base/arraysize.h"
#include "rtc_base/byte_buffer.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/socket_address.h"
#include "system_wrappers/include/metrics.h"
#include "test/gtest.h"

namespace cricket {

class StunTest : public ::testing::Test {
 protected:
  void CheckStunHeader(const StunMessage& msg,
                       StunMessageType expected_type,
                       size_t expected_length) {
    ASSERT_EQ(expected_type, msg.type());
    ASSERT_EQ(expected_length, msg.length());
  }

  void CheckStunTransactionID(const StunMessage& msg,
                              const uint8_t* expectedID,
                              size_t length) {
    ASSERT_EQ(length, msg.transaction_id().size());
    ASSERT_EQ(length == kStunTransactionIdLength + 4, msg.IsLegacy());
    ASSERT_EQ(length == kStunTransactionIdLength, !msg.IsLegacy());
    ASSERT_EQ(0, memcmp(msg.transaction_id().c_str(), expectedID, length));
  }

  void CheckStunAddressAttribute(const StunAddressAttribute* addr,
                                 StunAddressFamily expected_family,
                                 int expected_port,
                                 const rtc::IPAddress& expected_address) {
    ASSERT_EQ(expected_family, addr->family());
    ASSERT_EQ(expected_port, addr->port());

    if (addr->family() == STUN_ADDRESS_IPV4) {
      in_addr v4_address = expected_address.ipv4_address();
      in_addr stun_address = addr->ipaddr().ipv4_address();
      ASSERT_EQ(0, memcmp(&v4_address, &stun_address, sizeof(stun_address)));
    } else if (addr->family() == STUN_ADDRESS_IPV6) {
      in6_addr v6_address = expected_address.ipv6_address();
      in6_addr stun_address = addr->ipaddr().ipv6_address();
      ASSERT_EQ(0, memcmp(&v6_address, &stun_address, sizeof(stun_address)));
    } else {
      ASSERT_TRUE(addr->family() == STUN_ADDRESS_IPV6 ||
                  addr->family() == STUN_ADDRESS_IPV4);
    }
  }

  size_t ReadStunMessageTestCase(StunMessage* msg,
                                 const uint8_t* testcase,
                                 size_t size) {
    rtc::ByteBufferReader buf(rtc::MakeArrayView(testcase, size));
    if (msg->Read(&buf)) {
      // Returns the size the stun message should report itself as being
      return (size - 20);
    } else {
      return 0;
    }
  }
};

// Sample STUN packets with various attributes
// Gathered by wiresharking pjproject's pjnath test programs
// pjproject available at www.pjsip.org

// clang-format off
// clang formatting doesn't respect inline comments.

static const uint8_t kStunMessageWithIPv6MappedAddress[] = {
  0x00, 0x01, 0x00, 0x18,  // message header
  0x21, 0x12, 0xa4, 0x42,  // transaction id
  0x29, 0x1f, 0xcd, 0x7c,
  0xba, 0x58, 0xab, 0xd7,
  0xf2, 0x41, 0x01, 0x00,
  0x00, 0x01, 0x00, 0x14,  // Address type (mapped), length
  0x00, 0x02, 0xb8, 0x81,  // family (IPv6), port
  0x24, 0x01, 0xfa, 0x00,  // an IPv6 address
  0x00, 0x04, 0x10, 0x00,
  0xbe, 0x30, 0x5b, 0xff,
  0xfe, 0xe5, 0x00, 0xc3
};

static const uint8_t kStunMessageWithIPv4MappedAddress[] = {
  0x01, 0x01, 0x00, 0x0c,   // binding response, length 12
  0x21, 0x12, 0xa4, 0x42,   // magic cookie
  0x29, 0x1f, 0xcd, 0x7c,   // transaction ID
  0xba, 0x58, 0xab, 0xd7,
  0xf2, 0x41, 0x01, 0x00,
  0x00, 0x01, 0x00, 0x08,  // Mapped, 8 byte length
  0x00, 0x01, 0x9d, 0xfc,  // AF_INET, unxor-ed port
  0xac, 0x17, 0x44, 0xe6   // IPv4 address
};

// Test XOR-mapped IP addresses:
static const uint8_t kStunMessageWithIPv6XorMappedAddress[] = {
  0x01, 0x01, 0x00, 0x18,  // message header (binding response)
  0x21, 0x12, 0xa4, 0x42,  // magic cookie (rfc5389)
  0xe3, 0xa9, 0x46, 0xe1,  // transaction ID
  0x7c, 0x00, 0xc2, 0x62,
  0x54, 0x08, 0x01, 0x00,
  0x00, 0x20, 0x00, 0x14,  // Address Type (XOR), length
  0x00, 0x02, 0xcb, 0x5b,  // family, XOR-ed port
  0x05, 0x13, 0x5e, 0x42,  // XOR-ed IPv6 address
  0xe3, 0xad, 0x56, 0xe1,
  0xc2, 0x30, 0x99, 0x9d,
  0xaa, 0xed, 0x01, 0xc3
};

static const uint8_t kStunMessageWithIPv4XorMappedAddress[] = {
  0x01, 0x01, 0x00, 0x0c,  // message header (binding response)
  0x21, 0x12, 0xa4, 0x42,  // magic cookie
  0x29, 0x1f, 0xcd, 0x7c,  // transaction ID
  0xba, 0x58, 0xab, 0xd7,
  0xf2, 0x41, 0x01, 0x00,
  0x00, 0x20, 0x00, 0x08,  // address type (xor), length
  0x00, 0x01, 0xfc, 0xb5,  // family (AF_INET), XOR-ed port
  0x8d, 0x05, 0xe0, 0xa4   // IPv4 address
};

// ByteString Attribute (username)
static const uint8_t kStunMessageWithByteStringAttribute[] = {
  0x00, 0x01, 0x00, 0x0c,
  0x21, 0x12, 0xa4, 0x42,
  0xe3, 0xa9, 0x46, 0xe1,
  0x7c, 0x00, 0xc2, 0x62,
  0x54, 0x08, 0x01, 0x00,
  0x00, 0x06, 0x00, 0x08,  // username attribute (length 8)
  0x61, 0x62, 0x63, 0x64,  // abcdefgh
  0x65, 0x66, 0x67, 0x68
};

// Message with an unknown but comprehensible optional attribute.
// Parsing should succeed despite this unknown attribute.
static const uint8_t kStunMessageWithUnknownAttribute[] = {
  0x00, 0x01, 0x00, 0x14,
  0x21, 0x12, 0xa4, 0x42,
  0xe3, 0xa9, 0x46, 0xe1,
  0x7c, 0x00, 0xc2, 0x62,
  0x54, 0x08, 0x01, 0x00,
  0x00, 0xaa, 0x00, 0x07,  // Unknown attribute, length 7 (needs padding!)
  0x61, 0x62, 0x63, 0x64,  // abcdefg + padding
  0x65, 0x66, 0x67, 0x00,
  0x00, 0x06, 0x00, 0x03,  // Followed by a known attribute we can
  0x61, 0x62, 0x63, 0x00   // check for (username of length 3)
};

// ByteString Attribute (username) with padding byte
static const uint8_t kStunMessageWithPaddedByteStringAttribute[] = {
  0x00, 0x01, 0x00, 0x08,
  0x21, 0x12, 0xa4, 0x42,
  0xe3, 0xa9, 0x46, 0xe1,
  0x7c, 0x00, 0xc2, 0x62,
  0x54, 0x08, 0x01, 0x00,
  0x00, 0x06, 0x00, 0x03,  // username attribute (length 3)
  0x61, 0x62, 0x63, 0xcc   // abc
};

// Message with an Unknown Attributes (uint16_t list) attribute.
static const uint8_t kStunMessageWithUInt16ListAttribute[] = {
  0x00, 0x01, 0x00, 0x0c,
  0x21, 0x12, 0xa4, 0x42,
  0xe3, 0xa9, 0x46, 0xe1,
  0x7c, 0x00, 0xc2, 0x62,
  0x54, 0x08, 0x01, 0x00,
  0x00, 0x0a, 0x00, 0x06,  // username attribute (length 6)
  0x00, 0x01, 0x10, 0x00,  // three attributes plus padding
  0xAB, 0xCU, 0xBE, 0xEF
};

// Error response message (unauthorized)
static const uint8_t kStunMessageWithErrorAttribute[] = {
  0x01, 0x11, 0x00, 0x14,
  0x21, 0x12, 0xa4, 0x42,
  0x29, 0x1f, 0xcd, 0x7c,
  0xba, 0x58, 0xab, 0xd7,
  0xf2, 0x41, 0x01, 0x00,
  0x00, 0x09, 0x00, 0x10,
  0x00, 0x00, 0x04, 0x01,
  0x55, 0x6e, 0x61, 0x75,
  0x74, 0x68, 0x6f, 0x72,
  0x69, 0x7a, 0x65, 0x64
};

// Sample messages with an invalid length Field

// The actual length in bytes of the invalid messages (including STUN header)
static const int kRealLengthOfInvalidLengthTestCases = 32;

static const uint8_t kStunMessageWithZeroLength[] = {
  0x00, 0x01, 0x00, 0x00,  // length of 0 (last 2 bytes)
  0x21, 0x12, 0xA4, 0x42,  // magic cookie
  '0', '1', '2', '3',      // transaction id
  '4', '5', '6', '7',
  '8', '9', 'a', 'b',
  0x00, 0x20, 0x00, 0x08,  // xor mapped address
  0x00, 0x01, 0x21, 0x1F,
  0x21, 0x12, 0xA4, 0x53,
};

static const uint8_t kStunMessageWithExcessLength[] = {
  0x00, 0x01, 0x00, 0x55,  // length of 85
  0x21, 0x12, 0xA4, 0x42,  // magic cookie
  '0', '1', '2', '3',      // transaction id
  '4', '5', '6', '7',
  '8', '9', 'a', 'b',
  0x00, 0x20, 0x00, 0x08,  // xor mapped address
  0x00, 0x01, 0x21, 0x1F,
  0x21, 0x12, 0xA4, 0x53,
};

static const uint8_t kStunMessageWithSmallLength[] = {
  0x00, 0x01, 0x00, 0x03,  // length of 3
  0x21, 0x12, 0xA4, 0x42,  // magic cookie
  '0', '1', '2', '3',      // transaction id
  '4', '5', '6', '7',
  '8', '9', 'a', 'b',
  0x00, 0x20, 0x00, 0x08,  // xor mapped address
  0x00, 0x01, 0x21, 0x1F,
  0x21, 0x12, 0xA4, 0x53,
};

static const uint8_t kStunMessageWithBadHmacAtEnd[] = {
  0x00, 0x01, 0x00, 0x14,  // message length exactly 20
  0x21, 0x12, 0xA4, 0x42,  // magic cookie
  '0', '1', '2', '3',      // transaction ID
  '4', '5', '6', '7',
  '8', '9', 'a', 'b',
  0x00, 0x08, 0x00, 0x14,  // type=STUN_ATTR_MESSAGE_INTEGRITY, length=20
  '0', '0', '0', '0',      // We lied, there are only 16 bytes of HMAC.
  '0', '0', '0', '0',
  '0', '0', '0', '0',
  '0', '0', '0', '0',
};

// RTCP packet, for testing we correctly ignore non stun packet types.
// V=2, P=false, RC=0, Type=200, Len=6, Sender-SSRC=85, etc
static const uint8_t kRtcpPacket[] = {
  0x80, 0xc8, 0x00, 0x06, 0x00, 0x00, 0x00, 0x55,
  0xce, 0xa5, 0x18, 0x3a, 0x39, 0xcc, 0x7d, 0x09,
  0x23, 0xed, 0x19, 0x07, 0x00, 0x00, 0x01, 0x56,
  0x00, 0x03, 0x73, 0x50,
};


// RFC5769 Test Vectors
// Software name (request):  "STUN test client" (without quotes)
// Software name (response): "test vector" (without quotes)
// Username:  "evtj:h6vY" (without quotes)
// Password:  "VOkJxbRl1RmTxUk/WvJxBt" (without quotes)
static const uint8_t kRfc5769SampleMsgTransactionId[] = {
  0xb7, 0xe7, 0xa7, 0x01, 0xbc, 0x34, 0xd6, 0x86, 0xfa, 0x87, 0xdf, 0xae
};
static const char kRfc5769SampleMsgClientSoftware[] = "STUN test client";
static const char kRfc5769SampleMsgServerSoftware[] = "test vector";
static const char kRfc5769SampleMsgUsername[] = "evtj:h6vY";
static const char kRfc5769SampleMsgPassword[] = "VOkJxbRl1RmTxUk/WvJxBt";
static const rtc::SocketAddress kRfc5769SampleMsgMappedAddress(
    "192.0.2.1", 32853);
static const rtc::SocketAddress kRfc5769SampleMsgIPv6MappedAddress(
    "2001:db8:1234:5678:11:2233:4455:6677", 32853);

static const uint8_t kRfc5769SampleMsgWithAuthTransactionId[] = {
  0x78, 0xad, 0x34, 0x33, 0xc6, 0xad, 0x72, 0xc0, 0x29, 0xda, 0x41, 0x2e
};
static const char kRfc5769SampleMsgWithAuthUsername[] =
    "\xe3\x83\x9e\xe3\x83\x88\xe3\x83\xaa\xe3\x83\x83\xe3\x82\xaf\xe3\x82\xb9";
static const char kRfc5769SampleMsgWithAuthPassword[] = "TheMatrIX";
static const char kRfc5769SampleMsgWithAuthNonce[] =
    "f//499k954d6OL34oL9FSTvy64sA";
static const char kRfc5769SampleMsgWithAuthRealm[] = "example.org";

// 2.1.  Sample Request
static const uint8_t kRfc5769SampleRequest[] = {
  0x00, 0x01, 0x00, 0x58,   //    Request type and message length
  0x21, 0x12, 0xa4, 0x42,   //    Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,   // }
  0xbc, 0x34, 0xd6, 0x86,   // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,   // }
  0x80, 0x22, 0x00, 0x10,   //    SOFTWARE attribute header
  0x53, 0x54, 0x55, 0x4e,   // }
  0x20, 0x74, 0x65, 0x73,   // }  User-agent...
  0x74, 0x20, 0x63, 0x6c,   // }  ...name
  0x69, 0x65, 0x6e, 0x74,   // }
  0x00, 0x24, 0x00, 0x04,   //    PRIORITY attribute header
  0x6e, 0x00, 0x01, 0xff,   //    ICE priority value
  0x80, 0x29, 0x00, 0x08,   //    ICE-CONTROLLED attribute header
  0x93, 0x2f, 0xf9, 0xb1,   // }  Pseudo-random tie breaker...
  0x51, 0x26, 0x3b, 0x36,   // }   ...for ICE control
  0x00, 0x06, 0x00, 0x09,   //    USERNAME attribute header
  0x65, 0x76, 0x74, 0x6a,   // }
  0x3a, 0x68, 0x36, 0x76,   // }  Username (9 bytes) and padding (3 bytes)
  0x59, 0x20, 0x20, 0x20,   // }
  0x00, 0x08, 0x00, 0x14,   //    MESSAGE-INTEGRITY attribute header
  0x9a, 0xea, 0xa7, 0x0c,   // }
  0xbf, 0xd8, 0xcb, 0x56,   // }
  0x78, 0x1e, 0xf2, 0xb5,   // }  HMAC-SHA1 fingerprint
  0xb2, 0xd3, 0xf2, 0x49,   // }
  0xc1, 0xb5, 0x71, 0xa2,   // }
  0x80, 0x28, 0x00, 0x04,   //    FINGERPRINT attribute header
  0xe5, 0x7a, 0x3b, 0xcf    //    CRC32 fingerprint
};

// 2.1.  Sample Request
static const uint8_t kSampleRequestMI32[] = {
  0x00, 0x01, 0x00, 0x48,   //    Request type and message length
  0x21, 0x12, 0xa4, 0x42,   //    Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,   // }
  0xbc, 0x34, 0xd6, 0x86,   // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,   // }
  0x80, 0x22, 0x00, 0x10,   //    SOFTWARE attribute header
  0x53, 0x54, 0x55, 0x4e,   // }
  0x20, 0x74, 0x65, 0x73,   // }  User-agent...
  0x74, 0x20, 0x63, 0x6c,   // }  ...name
  0x69, 0x65, 0x6e, 0x74,   // }
  0x00, 0x24, 0x00, 0x04,   //    PRIORITY attribute header
  0x6e, 0x00, 0x01, 0xff,   //    ICE priority value
  0x80, 0x29, 0x00, 0x08,   //    ICE-CONTROLLED attribute header
  0x93, 0x2f, 0xf9, 0xb1,   // }  Pseudo-random tie breaker...
  0x51, 0x26, 0x3b, 0x36,   // }   ...for ICE control
  0x00, 0x06, 0x00, 0x09,   //    USERNAME attribute header
  0x65, 0x76, 0x74, 0x6a,   // }
  0x3a, 0x68, 0x36, 0x76,   // }  Username (9 bytes) and padding (3 bytes)
  0x59, 0x20, 0x20, 0x20,   // }
  0xC0, 0x60, 0x00, 0x04,   //    MESSAGE-INTEGRITY-32 attribute header
  0x45, 0x45, 0xce, 0x7c,   // }  HMAC-SHA1 fingerprint (first 32 bit)
  0x80, 0x28, 0x00, 0x04,   //    FINGERPRINT attribute header
  0xe5, 0x7a, 0x3b, 0xcf    //    CRC32 fingerprint
};

// 2.2.  Sample IPv4 Response
static const uint8_t kRfc5769SampleResponse[] = {
  0x01, 0x01, 0x00, 0x3c,  //     Response type and message length
  0x21, 0x12, 0xa4, 0x42,  //     Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,  // }
  0xbc, 0x34, 0xd6, 0x86,  // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,  // }
  0x80, 0x22, 0x00, 0x0b,  //    SOFTWARE attribute header
  0x74, 0x65, 0x73, 0x74,  // }
  0x20, 0x76, 0x65, 0x63,  // }  UTF-8 server name
  0x74, 0x6f, 0x72, 0x20,  // }
  0x00, 0x20, 0x00, 0x08,  //    XOR-MAPPED-ADDRESS attribute header
  0x00, 0x01, 0xa1, 0x47,  //    Address family (IPv4) and xor'd mapped port
  0xe1, 0x12, 0xa6, 0x43,  //    Xor'd mapped IPv4 address
  0x00, 0x08, 0x00, 0x14,  //    MESSAGE-INTEGRITY attribute header
  0x2b, 0x91, 0xf5, 0x99,  // }
  0xfd, 0x9e, 0x90, 0xc3,  // }
  0x8c, 0x74, 0x89, 0xf9,  // }  HMAC-SHA1 fingerprint
  0x2a, 0xf9, 0xba, 0x53,  // }
  0xf0, 0x6b, 0xe7, 0xd7,  // }
  0x80, 0x28, 0x00, 0x04,  //    FINGERPRINT attribute header
  0xc0, 0x7d, 0x4c, 0x96   //    CRC32 fingerprint
};

// 2.3.  Sample IPv6 Response
static const uint8_t kRfc5769SampleResponseIPv6[] = {
  0x01, 0x01, 0x00, 0x48,  //    Response type and message length
  0x21, 0x12, 0xa4, 0x42,  //    Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,  // }
  0xbc, 0x34, 0xd6, 0x86,  // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,  // }
  0x80, 0x22, 0x00, 0x0b,  //    SOFTWARE attribute header
  0x74, 0x65, 0x73, 0x74,  // }
  0x20, 0x76, 0x65, 0x63,  // }  UTF-8 server name
  0x74, 0x6f, 0x72, 0x20,  // }
  0x00, 0x20, 0x00, 0x14,  //    XOR-MAPPED-ADDRESS attribute header
  0x00, 0x02, 0xa1, 0x47,  //    Address family (IPv6) and xor'd mapped port.
  0x01, 0x13, 0xa9, 0xfa,  // }
  0xa5, 0xd3, 0xf1, 0x79,  // }  Xor'd mapped IPv6 address
  0xbc, 0x25, 0xf4, 0xb5,  // }
  0xbe, 0xd2, 0xb9, 0xd9,  // }
  0x00, 0x08, 0x00, 0x14,  //    MESSAGE-INTEGRITY attribute header
  0xa3, 0x82, 0x95, 0x4e,  // }
  0x4b, 0xe6, 0x7b, 0xf1,  // }
  0x17, 0x84, 0xc9, 0x7c,  // }  HMAC-SHA1 fingerprint
  0x82, 0x92, 0xc2, 0x75,  // }
  0xbf, 0xe3, 0xed, 0x41,  // }
  0x80, 0x28, 0x00, 0x04,  //    FINGERPRINT attribute header
  0xc8, 0xfb, 0x0b, 0x4c   //    CRC32 fingerprint
};

// 2.4.  Sample Request with Long-Term Authentication
static const uint8_t kRfc5769SampleRequestLongTermAuth[] = {
  0x00, 0x01, 0x00, 0x60,  //    Request type and message length
  0x21, 0x12, 0xa4, 0x42,  //    Magic cookie
  0x78, 0xad, 0x34, 0x33,  // }
  0xc6, 0xad, 0x72, 0xc0,  // }  Transaction ID
  0x29, 0xda, 0x41, 0x2e,  // }
  0x00, 0x06, 0x00, 0x12,  //    USERNAME attribute header
  0xe3, 0x83, 0x9e, 0xe3,  // }
  0x83, 0x88, 0xe3, 0x83,  // }
  0xaa, 0xe3, 0x83, 0x83,  // }  Username value (18 bytes) and padding (2 bytes)
  0xe3, 0x82, 0xaf, 0xe3,  // }
  0x82, 0xb9, 0x00, 0x00,  // }
  0x00, 0x15, 0x00, 0x1c,  //    NONCE attribute header
  0x66, 0x2f, 0x2f, 0x34,  // }
  0x39, 0x39, 0x6b, 0x39,  // }
  0x35, 0x34, 0x64, 0x36,  // }
  0x4f, 0x4c, 0x33, 0x34,  // }  Nonce value
  0x6f, 0x4c, 0x39, 0x46,  // }
  0x53, 0x54, 0x76, 0x79,  // }
  0x36, 0x34, 0x73, 0x41,  // }
  0x00, 0x14, 0x00, 0x0b,  //    REALM attribute header
  0x65, 0x78, 0x61, 0x6d,  // }
  0x70, 0x6c, 0x65, 0x2e,  // }  Realm value (11 bytes) and padding (1 byte)
  0x6f, 0x72, 0x67, 0x00,  // }
  0x00, 0x08, 0x00, 0x14,  //    MESSAGE-INTEGRITY attribute header
  0xf6, 0x70, 0x24, 0x65,  // }
  0x6d, 0xd6, 0x4a, 0x3e,  // }
  0x02, 0xb8, 0xe0, 0x71,  // }  HMAC-SHA1 fingerprint
  0x2e, 0x85, 0xc9, 0xa2,  // }
  0x8c, 0xa8, 0x96, 0x66   // }
};

// Length parameter is changed to 0x38 from 0x58.
// AddMessageIntegrity will add MI information and update the length param
// accordingly.
static const uint8_t kRfc5769SampleRequestWithoutMI[] = {
  0x00, 0x01, 0x00, 0x38,  //    Request type and message length
  0x21, 0x12, 0xa4, 0x42,  //    Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,  // }
  0xbc, 0x34, 0xd6, 0x86,  // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,  // }
  0x80, 0x22, 0x00, 0x10,  //    SOFTWARE attribute header
  0x53, 0x54, 0x55, 0x4e,  // }
  0x20, 0x74, 0x65, 0x73,  // }  User-agent...
  0x74, 0x20, 0x63, 0x6c,  // }  ...name
  0x69, 0x65, 0x6e, 0x74,  // }
  0x00, 0x24, 0x00, 0x04,  //    PRIORITY attribute header
  0x6e, 0x00, 0x01, 0xff,  //    ICE priority value
  0x80, 0x29, 0x00, 0x08,  //    ICE-CONTROLLED attribute header
  0x93, 0x2f, 0xf9, 0xb1,  // }  Pseudo-random tie breaker...
  0x51, 0x26, 0x3b, 0x36,  // }   ...for ICE control
  0x00, 0x06, 0x00, 0x09,  //    USERNAME attribute header
  0x65, 0x76, 0x74, 0x6a,  // }
  0x3a, 0x68, 0x36, 0x76,  // }  Username (9 bytes) and padding (3 bytes)
  0x59, 0x20, 0x20, 0x20   // }
};

// This HMAC differs from the RFC 5769 SampleRequest message. This differs
// because spec uses 0x20 for the padding where as our implementation uses 0.
static const uint8_t kCalculatedHmac1[] = {
  0x79, 0x07, 0xc2, 0xd2,  // }
  0xed, 0xbf, 0xea, 0x48,  // }
  0x0e, 0x4c, 0x76, 0xd8,  // }  HMAC-SHA1 fingerprint
  0x29, 0x62, 0xd5, 0xc3,  // }
  0x74, 0x2a, 0xf9, 0xe3   // }
};

// This truncated HMAC differs from kCalculatedHmac1
// above since the sum is computed including header
// and the header is different since the message is shorter
// than when MESSAGE-INTEGRITY is used.
static const uint8_t kCalculatedHmac1_32[] = {
  0xda, 0x39, 0xde, 0x5d,  // }
};

// Length parameter is changed to 0x1c from 0x3c.
// AddMessageIntegrity will add MI information and update the length param
// accordingly.
static const uint8_t kRfc5769SampleResponseWithoutMI[] = {
  0x01, 0x01, 0x00, 0x1c,  //    Response type and message length
  0x21, 0x12, 0xa4, 0x42,  //    Magic cookie
  0xb7, 0xe7, 0xa7, 0x01,  // }
  0xbc, 0x34, 0xd6, 0x86,  // }  Transaction ID
  0xfa, 0x87, 0xdf, 0xae,  // }
  0x80, 0x22, 0x00, 0x0b,  //    SOFTWARE attribute header
  0x74, 0x65, 0x73, 0x74,  // }
  0x20, 0x76, 0x65, 0x63,  // }  UTF-8 server name
  0x74, 0x6f, 0x72, 0x20,  // }
  0x00, 0x20, 0x00, 0x08,  //    XOR-MAPPED-ADDRESS attribute header
  0x00, 0x01, 0xa1, 0x47,  //    Address family (IPv4) and xor'd mapped port
  0xe1, 0x12, 0xa6, 0x43   //    Xor'd mapped IPv4 address
};

// This HMAC differs from the RFC 5769 SampleResponse message. This differs
// because spec uses 0x20 for the padding where as our implementation uses 0.
static const uint8_t kCalculatedHmac2[] = {
  0x5d, 0x6b, 0x58, 0xbe,  // }
  0xad, 0x94, 0xe0, 0x7e,  // }
  0xef, 0x0d, 0xfc, 0x12,  // }  HMAC-SHA1 fingerprint
  0x82, 0xa2, 0xbd, 0x08,  // }
  0x43, 0x14, 0x10, 0x28   // }
};

// This truncated HMAC differs from kCalculatedHmac2
// above since the sum is computed including header
// and the header is different since the message is shorter
// than when MESSAGE-INTEGRITY is used.
static const uint8_t kCalculatedHmac2_32[] = {
  0xe7, 0x5c, 0xd3, 0x16,  // }
};

// clang-format on

// A transaction ID without the 'magic cookie' portion
// pjnat's test programs use this transaction ID a lot.
const uint8_t kTestTransactionId1[] = {0x029, 0x01f, 0x0cd, 0x07c,
                                       0x0ba, 0x058, 0x0ab, 0x0d7,
                                       0x0f2, 0x041, 0x001, 0x000};

// They use this one sometimes too.
const uint8_t kTestTransactionId2[] = {0x0e3, 0x0a9, 0x046, 0x0e1,
                                       0x07c, 0x000, 0x0c2, 0x062,
                                       0x054, 0x008, 0x001, 0x000};

const in6_addr kIPv6TestAddress1 = {
    {{0x24, 0x01, 0xfa, 0x00, 0x00, 0x04, 0x10, 0x00, 0xbe, 0x30, 0x5b, 0xff,
      0xfe, 0xe5, 0x00, 0xc3}}};
const in6_addr kIPv6TestAddress2 = {
    {{0x24, 0x01, 0xfa, 0x00, 0x00, 0x04, 0x10, 0x12, 0x06, 0x0c, 0xce, 0xff,
      0xfe, 0x1f, 0x61, 0xa4}}};

#ifdef WEBRTC_POSIX
const in_addr kIPv4TestAddress1 = {0xe64417ac};
#elif defined WEBRTC_WIN
// Windows in_addr has a union with a uchar[] array first.
const in_addr kIPv4TestAddress1 = {{{0x0ac, 0x017, 0x044, 0x0e6}}};
#endif
const char kTestUserName1[] = "abcdefgh";
const char kTestUserName2[] = "abc";
const char kTestErrorReason[] = "Unauthorized";
const int kTestErrorClass = 4;
const int kTestErrorNumber = 1;
const int kTestErrorCode = 401;

const int kTestMessagePort1 = 59977;
const int kTestMessagePort2 = 47233;
const int kTestMessagePort3 = 56743;
const int kTestMessagePort4 = 40444;

#define ReadStunMessage(X, Y) ReadStunMessageTestCase(X, Y, sizeof(Y));

// Test that the GetStun*Type and IsStun*Type methods work as expected.
TEST_F(StunTest, MessageTypes) {
  EXPECT_EQ(STUN_BINDING_RESPONSE,
            GetStunSuccessResponseType(STUN_BINDING_REQUEST));
  EXPECT_EQ(STUN_BINDING_ERROR_RESPONSE,
            GetStunErrorResponseType(STUN_BINDING_REQUEST));
  EXPECT_EQ(-1, GetStunSuccessResponseType(STUN_BINDING_INDICATION));
  EXPECT_EQ(-1, GetStunSuccessResponseType(STUN_BINDING_RESPONSE));
  EXPECT_EQ(-1, GetStunSuccessResponseType(STUN_BINDING_ERROR_RESPONSE));
  EXPECT_EQ(-1, GetStunErrorResponseType(STUN_BINDING_INDICATION));
  EXPECT_EQ(-1, GetStunErrorResponseType(STUN_BINDING_RESPONSE));
  EXPECT_EQ(-1, GetStunErrorResponseType(STUN_BINDING_ERROR_RESPONSE));

  int types[] = {STUN_BINDING_REQUEST, STUN_BINDING_INDICATION,
                 STUN_BINDING_RESPONSE, STUN_BINDING_ERROR_RESPONSE};
  for (size_t i = 0; i < arraysize(types); ++i) {
    EXPECT_EQ(i == 0U, IsStunRequestType(types[i]));
    EXPECT_EQ(i == 1U, IsStunIndicationType(types[i]));
    EXPECT_EQ(i == 2U, IsStunSuccessResponseType(types[i]));
    EXPECT_EQ(i == 3U, IsStunErrorResponseType(types[i]));
    EXPECT_EQ(1, types[i] & 0xFEEF);
  }
}

TEST_F(StunTest, ReadMessageWithIPv4AddressAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv4MappedAddress);
  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  const StunAddressAttribute* addr = msg.GetAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::IPAddress test_address(kIPv4TestAddress1);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV4, kTestMessagePort4,
                            test_address);
}

TEST_F(StunTest, ReadMessageWithIPv4XorAddressAttribute) {
  StunMessage msg;
  StunMessage msg2;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv4XorMappedAddress);
  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  const StunAddressAttribute* addr =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  rtc::IPAddress test_address(kIPv4TestAddress1);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV4, kTestMessagePort3,
                            test_address);
}

TEST_F(StunTest, ReadMessageWithIPv6AddressAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv6MappedAddress);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  rtc::IPAddress test_address(kIPv6TestAddress1);

  const StunAddressAttribute* addr = msg.GetAddress(STUN_ATTR_MAPPED_ADDRESS);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV6, kTestMessagePort2,
                            test_address);
}

TEST_F(StunTest, ReadMessageWithInvalidAddressAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv6MappedAddress);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  rtc::IPAddress test_address(kIPv6TestAddress1);

  const StunAddressAttribute* addr = msg.GetAddress(STUN_ATTR_MAPPED_ADDRESS);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV6, kTestMessagePort2,
                            test_address);
}

TEST_F(StunTest, ReadMessageWithIPv6XorAddressAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv6XorMappedAddress);

  rtc::IPAddress test_address(kIPv6TestAddress1);

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);

  const StunAddressAttribute* addr =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV6, kTestMessagePort1,
                            test_address);
}

// Read the RFC5389 fields from the RFC5769 sample STUN request.
TEST_F(StunTest, ReadRfc5769RequestMessage) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kRfc5769SampleRequest);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kRfc5769SampleMsgTransactionId,
                         kStunTransactionIdLength);

  const StunByteStringAttribute* software =
      msg.GetByteString(STUN_ATTR_SOFTWARE);
  ASSERT_TRUE(software != NULL);
  EXPECT_EQ(kRfc5769SampleMsgClientSoftware, software->string_view());

  const StunByteStringAttribute* username =
      msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(username != NULL);
  EXPECT_EQ(kRfc5769SampleMsgUsername, username->string_view());

  // Actual M-I value checked in a later test.
  ASSERT_TRUE(msg.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY) != NULL);

  // Fingerprint checked in a later test, but double-check the value here.
  const StunUInt32Attribute* fingerprint = msg.GetUInt32(STUN_ATTR_FINGERPRINT);
  ASSERT_TRUE(fingerprint != NULL);
  EXPECT_EQ(0xe57a3bcf, fingerprint->value());
}

// Read the RFC5389 fields from the RFC5769 sample STUN response.
TEST_F(StunTest, ReadRfc5769ResponseMessage) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kRfc5769SampleResponse);
  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kRfc5769SampleMsgTransactionId,
                         kStunTransactionIdLength);

  const StunByteStringAttribute* software =
      msg.GetByteString(STUN_ATTR_SOFTWARE);
  ASSERT_TRUE(software != NULL);
  EXPECT_EQ(kRfc5769SampleMsgServerSoftware, software->string_view());

  const StunAddressAttribute* mapped_address =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  ASSERT_TRUE(mapped_address != NULL);
  EXPECT_EQ(kRfc5769SampleMsgMappedAddress, mapped_address->GetAddress());

  // Actual M-I and fingerprint checked in later tests.
  ASSERT_TRUE(msg.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY) != NULL);
  ASSERT_TRUE(msg.GetUInt32(STUN_ATTR_FINGERPRINT) != NULL);
}

// Read the RFC5389 fields from the RFC5769 sample STUN response for IPv6.
TEST_F(StunTest, ReadRfc5769ResponseMessageIPv6) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kRfc5769SampleResponseIPv6);
  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kRfc5769SampleMsgTransactionId,
                         kStunTransactionIdLength);

  const StunByteStringAttribute* software =
      msg.GetByteString(STUN_ATTR_SOFTWARE);
  ASSERT_TRUE(software != NULL);
  EXPECT_EQ(kRfc5769SampleMsgServerSoftware, software->string_view());

  const StunAddressAttribute* mapped_address =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  ASSERT_TRUE(mapped_address != NULL);
  EXPECT_EQ(kRfc5769SampleMsgIPv6MappedAddress, mapped_address->GetAddress());

  // Actual M-I and fingerprint checked in later tests.
  ASSERT_TRUE(msg.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY) != NULL);
  ASSERT_TRUE(msg.GetUInt32(STUN_ATTR_FINGERPRINT) != NULL);
}

// Read the RFC5389 fields from the RFC5769 sample STUN response with auth.
TEST_F(StunTest, ReadRfc5769RequestMessageLongTermAuth) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kRfc5769SampleRequestLongTermAuth);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kRfc5769SampleMsgWithAuthTransactionId,
                         kStunTransactionIdLength);

  const StunByteStringAttribute* username =
      msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(username != NULL);
  EXPECT_EQ(kRfc5769SampleMsgWithAuthUsername, username->string_view());

  const StunByteStringAttribute* nonce = msg.GetByteString(STUN_ATTR_NONCE);
  ASSERT_TRUE(nonce != NULL);
  EXPECT_EQ(kRfc5769SampleMsgWithAuthNonce, nonce->string_view());

  const StunByteStringAttribute* realm = msg.GetByteString(STUN_ATTR_REALM);
  ASSERT_TRUE(realm != NULL);
  EXPECT_EQ(kRfc5769SampleMsgWithAuthRealm, realm->string_view());

  // No fingerprint, actual M-I checked in later tests.
  ASSERT_TRUE(msg.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY) != NULL);
  ASSERT_TRUE(msg.GetUInt32(STUN_ATTR_FINGERPRINT) == NULL);
}

// The RFC3489 packet in this test is the same as
// kStunMessageWithIPv4MappedAddress, but with a different value where the
// magic cookie was.
TEST_F(StunTest, ReadLegacyMessage) {
  uint8_t rfc3489_packet[sizeof(kStunMessageWithIPv4MappedAddress)];
  memcpy(rfc3489_packet, kStunMessageWithIPv4MappedAddress,
         sizeof(kStunMessageWithIPv4MappedAddress));
  // Overwrite the magic cookie here.
  memcpy(&rfc3489_packet[4], "ABCD", 4);

  StunMessage msg;
  size_t size = ReadStunMessage(&msg, rfc3489_packet);
  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, &rfc3489_packet[4], kStunTransactionIdLength + 4);

  const StunAddressAttribute* addr = msg.GetAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::IPAddress test_address(kIPv4TestAddress1);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV4, kTestMessagePort4,
                            test_address);
}

TEST_F(StunTest, SetIPv6XorAddressAttributeOwner) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv6XorMappedAddress);

  rtc::IPAddress test_address(kIPv6TestAddress1);

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);

  const StunAddressAttribute* addr =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV6, kTestMessagePort1,
                            test_address);

  // Owner with a different transaction ID.
  StunMessage msg2(STUN_INVALID_MESSAGE_TYPE, "ABCDABCDABCD");
  StunXorAddressAttribute addr2(STUN_ATTR_XOR_MAPPED_ADDRESS, 20, NULL);
  addr2.SetIP(addr->ipaddr());
  addr2.SetPort(addr->port());
  addr2.SetOwner(&msg2);
  // The internal IP address shouldn't change.
  ASSERT_EQ(addr2.ipaddr(), addr->ipaddr());

  rtc::ByteBufferWriter correct_buf;
  rtc::ByteBufferWriter wrong_buf;
  EXPECT_TRUE(addr->Write(&correct_buf));
  EXPECT_TRUE(addr2.Write(&wrong_buf));
  // But when written out, the buffers should look different.
  ASSERT_NE(0,
            memcmp(correct_buf.Data(), wrong_buf.Data(), wrong_buf.Length()));
  // And when reading a known good value, the address should be wrong.
  rtc::ByteBufferReader read_buf(correct_buf);
  addr2.Read(&read_buf);
  ASSERT_NE(addr->ipaddr(), addr2.ipaddr());
  addr2.SetIP(addr->ipaddr());
  addr2.SetPort(addr->port());
  // Try writing with no owner at all, should fail and write nothing.
  addr2.SetOwner(NULL);
  ASSERT_EQ(addr2.ipaddr(), addr->ipaddr());
  wrong_buf.Clear();
  EXPECT_FALSE(addr2.Write(&wrong_buf));
  ASSERT_EQ(0U, wrong_buf.Length());
}

TEST_F(StunTest, SetIPv4XorAddressAttributeOwner) {
  // Unlike the IPv6XorAddressAttributeOwner test, IPv4 XOR address attributes
  // should _not_ be affected by a change in owner. IPv4 XOR address uses the
  // magic cookie value which is fixed.
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithIPv4XorMappedAddress);

  rtc::IPAddress test_address(kIPv4TestAddress1);

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  const StunAddressAttribute* addr =
      msg.GetAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  CheckStunAddressAttribute(addr, STUN_ADDRESS_IPV4, kTestMessagePort3,
                            test_address);

  // Owner with a different transaction ID.
  StunMessage msg2(STUN_INVALID_MESSAGE_TYPE, "ABCDABCDABCD");
  StunXorAddressAttribute addr2(STUN_ATTR_XOR_MAPPED_ADDRESS, 20, NULL);
  addr2.SetIP(addr->ipaddr());
  addr2.SetPort(addr->port());
  addr2.SetOwner(&msg2);
  // The internal IP address shouldn't change.
  ASSERT_EQ(addr2.ipaddr(), addr->ipaddr());

  rtc::ByteBufferWriter correct_buf;
  rtc::ByteBufferWriter wrong_buf;
  EXPECT_TRUE(addr->Write(&correct_buf));
  EXPECT_TRUE(addr2.Write(&wrong_buf));
  // The same address data should be written.
  ASSERT_EQ(0,
            memcmp(correct_buf.Data(), wrong_buf.Data(), wrong_buf.Length()));
  // And an attribute should be able to un-XOR an address belonging to a message
  // with a different transaction ID.
  rtc::ByteBufferReader read_buf(correct_buf);
  EXPECT_TRUE(addr2.Read(&read_buf));
  ASSERT_EQ(addr->ipaddr(), addr2.ipaddr());

  // However, no owner is still an error, should fail and write nothing.
  addr2.SetOwner(NULL);
  ASSERT_EQ(addr2.ipaddr(), addr->ipaddr());
  wrong_buf.Clear();
  EXPECT_FALSE(addr2.Write(&wrong_buf));
}

TEST_F(StunTest, CreateIPv6AddressAttribute) {
  rtc::IPAddress test_ip(kIPv6TestAddress2);

  auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort2);
  addr->SetAddress(test_addr);

  CheckStunAddressAttribute(addr.get(), STUN_ADDRESS_IPV6, kTestMessagePort2,
                            test_ip);
}

TEST_F(StunTest, CreateIPv4AddressAttribute) {
  struct in_addr test_in_addr;
  test_in_addr.s_addr = 0xBEB0B0BE;
  rtc::IPAddress test_ip(test_in_addr);

  auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort2);
  addr->SetAddress(test_addr);

  CheckStunAddressAttribute(addr.get(), STUN_ADDRESS_IPV4, kTestMessagePort2,
                            test_ip);
}

// Test that we don't care what order we set the parts of an address
TEST_F(StunTest, CreateAddressInArbitraryOrder) {
  auto addr = StunAttribute::CreateAddress(STUN_ATTR_DESTINATION_ADDRESS);
  // Port first
  addr->SetPort(kTestMessagePort1);
  addr->SetIP(rtc::IPAddress(kIPv4TestAddress1));
  ASSERT_EQ(kTestMessagePort1, addr->port());
  ASSERT_EQ(rtc::IPAddress(kIPv4TestAddress1), addr->ipaddr());

  auto addr2 = StunAttribute::CreateAddress(STUN_ATTR_DESTINATION_ADDRESS);
  // IP first
  addr2->SetIP(rtc::IPAddress(kIPv4TestAddress1));
  addr2->SetPort(kTestMessagePort2);
  ASSERT_EQ(kTestMessagePort2, addr2->port());
  ASSERT_EQ(rtc::IPAddress(kIPv4TestAddress1), addr2->ipaddr());
}

TEST_F(StunTest, WriteMessageWithIPv6AddressAttribute) {
  size_t size = sizeof(kStunMessageWithIPv6MappedAddress);

  rtc::IPAddress test_ip(kIPv6TestAddress1);

  StunMessage msg(
      STUN_BINDING_REQUEST,
      std::string(reinterpret_cast<const char*>(kTestTransactionId1),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort2);
  addr->SetAddress(test_addr);
  msg.AddAttribute(std::move(addr));

  CheckStunHeader(msg, STUN_BINDING_REQUEST, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(out.Length(), sizeof(kStunMessageWithIPv6MappedAddress));
  int len1 = static_cast<int>(out.Length());
  rtc::ByteBufferReader read_buf(out);
  std::string bytes;
  read_buf.ReadString(&bytes, len1);
  ASSERT_EQ(0, memcmp(bytes.c_str(), kStunMessageWithIPv6MappedAddress, len1));
}

TEST_F(StunTest, WriteMessageWithIPv4AddressAttribute) {
  size_t size = sizeof(kStunMessageWithIPv4MappedAddress);

  rtc::IPAddress test_ip(kIPv4TestAddress1);

  StunMessage msg(
      STUN_BINDING_RESPONSE,
      std::string(reinterpret_cast<const char*>(kTestTransactionId1),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort4);
  addr->SetAddress(test_addr);
  msg.AddAttribute(std::move(addr));

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(out.Length(), sizeof(kStunMessageWithIPv4MappedAddress));
  int len1 = static_cast<int>(out.Length());
  rtc::ByteBufferReader read_buf(out);
  std::string bytes;
  read_buf.ReadString(&bytes, len1);
  ASSERT_EQ(0, memcmp(bytes.c_str(), kStunMessageWithIPv4MappedAddress, len1));
}

TEST_F(StunTest, WriteMessageWithIPv6XorAddressAttribute) {
  size_t size = sizeof(kStunMessageWithIPv6XorMappedAddress);

  rtc::IPAddress test_ip(kIPv6TestAddress1);

  StunMessage msg(
      STUN_BINDING_RESPONSE,
      std::string(reinterpret_cast<const char*>(kTestTransactionId2),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);

  auto addr = StunAttribute::CreateXorAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort1);
  addr->SetAddress(test_addr);
  msg.AddAttribute(std::move(addr));

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(out.Length(), sizeof(kStunMessageWithIPv6XorMappedAddress));
  int len1 = static_cast<int>(out.Length());
  rtc::ByteBufferReader read_buf(out);
  std::string bytes;
  read_buf.ReadString(&bytes, len1);
  ASSERT_EQ(0,
            memcmp(bytes.c_str(), kStunMessageWithIPv6XorMappedAddress, len1));
}

TEST_F(StunTest, WriteMessageWithIPv4XoreAddressAttribute) {
  size_t size = sizeof(kStunMessageWithIPv4XorMappedAddress);

  rtc::IPAddress test_ip(kIPv4TestAddress1);

  StunMessage msg(
      STUN_BINDING_RESPONSE,
      std::string(reinterpret_cast<const char*>(kTestTransactionId1),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);

  auto addr = StunAttribute::CreateXorAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
  rtc::SocketAddress test_addr(test_ip, kTestMessagePort3);
  addr->SetAddress(test_addr);
  msg.AddAttribute(std::move(addr));

  CheckStunHeader(msg, STUN_BINDING_RESPONSE, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(out.Length(), sizeof(kStunMessageWithIPv4XorMappedAddress));
  int len1 = static_cast<int>(out.Length());
  rtc::ByteBufferReader read_buf(out);
  std::string bytes;
  read_buf.ReadString(&bytes, len1);
  ASSERT_EQ(0,
            memcmp(bytes.c_str(), kStunMessageWithIPv4XorMappedAddress, len1));
}

TEST_F(StunTest, ReadByteStringAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithByteStringAttribute);

  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);
  const StunByteStringAttribute* username =
      msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(username != NULL);
  EXPECT_EQ(kTestUserName1, username->string_view());
}

TEST_F(StunTest, ReadPaddedByteStringAttribute) {
  StunMessage msg;
  size_t size =
      ReadStunMessage(&msg, kStunMessageWithPaddedByteStringAttribute);
  ASSERT_NE(0U, size);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);
  const StunByteStringAttribute* username =
      msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(username != NULL);
  EXPECT_EQ(kTestUserName2, username->string_view());
}

TEST_F(StunTest, ReadErrorCodeAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithErrorAttribute);

  CheckStunHeader(msg, STUN_BINDING_ERROR_RESPONSE, size);
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);
  const StunErrorCodeAttribute* errorcode = msg.GetErrorCode();
  ASSERT_TRUE(errorcode != NULL);
  EXPECT_EQ(kTestErrorClass, errorcode->eclass());
  EXPECT_EQ(kTestErrorNumber, errorcode->number());
  EXPECT_EQ(kTestErrorReason, errorcode->reason());
  EXPECT_EQ(kTestErrorCode, errorcode->code());
  EXPECT_EQ(kTestErrorCode, msg.GetErrorCodeValue());
}

// Test that GetErrorCodeValue returns STUN_ERROR_GLOBAL_FAILURE if the message
// in question doesn't have an error code attribute, rather than crashing.
TEST_F(StunTest, GetErrorCodeValueWithNoErrorAttribute) {
  StunMessage msg;
  ReadStunMessage(&msg, kStunMessageWithIPv6MappedAddress);
  EXPECT_EQ(STUN_ERROR_GLOBAL_FAILURE, msg.GetErrorCodeValue());
}

TEST_F(StunTest, ReadMessageWithAUInt16ListAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithUInt16ListAttribute);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);
  const StunUInt16ListAttribute* types = msg.GetUnknownAttributes();
  ASSERT_TRUE(types != NULL);
  EXPECT_EQ(3U, types->Size());
  EXPECT_EQ(0x1U, types->GetType(0));
  EXPECT_EQ(0x1000U, types->GetType(1));
  EXPECT_EQ(0xAB0CU, types->GetType(2));
}

TEST_F(StunTest, ReadMessageWithAnUnknownAttribute) {
  StunMessage msg;
  size_t size = ReadStunMessage(&msg, kStunMessageWithUnknownAttribute);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, size);

  // Parsing should have succeeded and there should be a USERNAME attribute
  const StunByteStringAttribute* username =
      msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(username != NULL);
  EXPECT_EQ(kTestUserName2, username->string_view());
}

TEST_F(StunTest, WriteMessageWithAnErrorCodeAttribute) {
  size_t size = sizeof(kStunMessageWithErrorAttribute);

  StunMessage msg(
      STUN_BINDING_ERROR_RESPONSE,
      std::string(reinterpret_cast<const char*>(kTestTransactionId1),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId1, kStunTransactionIdLength);
  auto errorcode = StunAttribute::CreateErrorCode();
  errorcode->SetCode(kTestErrorCode);
  errorcode->SetReason(kTestErrorReason);
  msg.AddAttribute(std::move(errorcode));
  CheckStunHeader(msg, STUN_BINDING_ERROR_RESPONSE, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(size, out.Length());
  // No padding.
  ASSERT_EQ(0, memcmp(out.Data(), kStunMessageWithErrorAttribute, size));
}

TEST_F(StunTest, WriteMessageWithAUInt16ListAttribute) {
  size_t size = sizeof(kStunMessageWithUInt16ListAttribute);

  StunMessage msg(
      STUN_BINDING_REQUEST,
      std::string(reinterpret_cast<const char*>(kTestTransactionId2),
                  kStunTransactionIdLength));
  CheckStunTransactionID(msg, kTestTransactionId2, kStunTransactionIdLength);
  auto list = StunAttribute::CreateUnknownAttributes();
  list->AddType(0x1U);
  list->AddType(0x1000U);
  list->AddType(0xAB0CU);
  msg.AddAttribute(std::move(list));
  CheckStunHeader(msg, STUN_BINDING_REQUEST, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(size, out.Length());
  // Check everything up to the padding.
  ASSERT_EQ(0,
            memcmp(out.Data(), kStunMessageWithUInt16ListAttribute, size - 2));
}

// Test that we fail to read messages with invalid lengths.
void CheckFailureToRead(const uint8_t* testcase, size_t length) {
  StunMessage msg;
  rtc::ByteBufferReader buf(rtc::MakeArrayView(testcase, length));
  ASSERT_FALSE(msg.Read(&buf));
}

TEST_F(StunTest, FailToReadInvalidMessages) {
  CheckFailureToRead(kStunMessageWithZeroLength,
                     kRealLengthOfInvalidLengthTestCases);
  CheckFailureToRead(kStunMessageWithSmallLength,
                     kRealLengthOfInvalidLengthTestCases);
  CheckFailureToRead(kStunMessageWithExcessLength,
                     kRealLengthOfInvalidLengthTestCases);
}

// Test that we properly fail to read a non-STUN message.
TEST_F(StunTest, FailToReadRtcpPacket) {
  CheckFailureToRead(kRtcpPacket, sizeof(kRtcpPacket));
}

// Check our STUN message validation code against the RFC5769 test messages.
TEST_F(StunTest, ValidateMessageIntegrity) {
  // Try the messages from RFC 5769.
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleRequest),
      sizeof(kRfc5769SampleRequest), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleRequest),
      sizeof(kRfc5769SampleRequest), "InvalidPassword"));

  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleResponse),
      sizeof(kRfc5769SampleResponse), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleResponse),
      sizeof(kRfc5769SampleResponse), "InvalidPassword"));

  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleResponseIPv6),
      sizeof(kRfc5769SampleResponseIPv6), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleResponseIPv6),
      sizeof(kRfc5769SampleResponseIPv6), "InvalidPassword"));

  // We first need to compute the key for the long-term authentication HMAC.
  std::string key;
  ComputeStunCredentialHash(kRfc5769SampleMsgWithAuthUsername,
                            kRfc5769SampleMsgWithAuthRealm,
                            kRfc5769SampleMsgWithAuthPassword, &key);
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleRequestLongTermAuth),
      sizeof(kRfc5769SampleRequestLongTermAuth), key));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kRfc5769SampleRequestLongTermAuth),
      sizeof(kRfc5769SampleRequestLongTermAuth), "InvalidPassword"));

  // Try some edge cases.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithZeroLength),
      sizeof(kStunMessageWithZeroLength), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithExcessLength),
      sizeof(kStunMessageWithExcessLength), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithSmallLength),
      sizeof(kStunMessageWithSmallLength), kRfc5769SampleMsgPassword));

  // Again, but with the lengths matching what is claimed in the headers.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithZeroLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithZeroLength[2]),
      kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithExcessLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithExcessLength[2]),
      kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithSmallLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithSmallLength[2]),
      kRfc5769SampleMsgPassword));

  // Check that a too-short HMAC doesn't cause buffer overflow.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(kStunMessageWithBadHmacAtEnd),
      sizeof(kStunMessageWithBadHmacAtEnd), kRfc5769SampleMsgPassword));

  // Test that munging a single bit anywhere in the message causes the
  // message-integrity check to fail, unless it is after the M-I attribute.
  char buf[sizeof(kRfc5769SampleRequest)];
  memcpy(buf, kRfc5769SampleRequest, sizeof(kRfc5769SampleRequest));
  for (size_t i = 0; i < sizeof(buf); ++i) {
    buf[i] ^= 0x01;
    if (i > 0)
      buf[i - 1] ^= 0x01;
    EXPECT_EQ(i >= sizeof(buf) - 8,
              StunMessage::ValidateMessageIntegrityForTesting(
                  buf, sizeof(buf), kRfc5769SampleMsgPassword));
  }
}

// Validate that we generate correct MESSAGE-INTEGRITY attributes.
// Note the use of IceMessage instead of StunMessage; this is necessary because
// the RFC5769 test messages used include attributes not found in basic STUN.
TEST_F(StunTest, AddMessageIntegrity) {
  IceMessage msg;
  rtc::ByteBufferReader buf(kRfc5769SampleRequestWithoutMI);
  EXPECT_TRUE(msg.Read(&buf));
  EXPECT_TRUE(msg.AddMessageIntegrity(kRfc5769SampleMsgPassword));
  const StunByteStringAttribute* mi_attr =
      msg.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY);
  EXPECT_EQ(20U, mi_attr->length());
  EXPECT_EQ(0, memcmp(mi_attr->array_view().data(), kCalculatedHmac1,
                      sizeof(kCalculatedHmac1)));

  rtc::ByteBufferWriter buf1;
  EXPECT_TRUE(msg.Write(&buf1));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(),
      kRfc5769SampleMsgPassword));

  IceMessage msg2;
  rtc::ByteBufferReader buf2(kRfc5769SampleResponseWithoutMI);
  EXPECT_TRUE(msg2.Read(&buf2));
  EXPECT_TRUE(msg2.AddMessageIntegrity(kRfc5769SampleMsgPassword));
  const StunByteStringAttribute* mi_attr2 =
      msg2.GetByteString(STUN_ATTR_MESSAGE_INTEGRITY);
  EXPECT_EQ(20U, mi_attr2->length());
  EXPECT_EQ(0, memcmp(mi_attr2->array_view().data(), kCalculatedHmac2,
                      sizeof(kCalculatedHmac2)));

  rtc::ByteBufferWriter buf3;
  EXPECT_TRUE(msg2.Write(&buf3));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(buf3.Data()), buf3.Length(),
      kRfc5769SampleMsgPassword));
}

// Check our STUN message validation code against the RFC5769 test messages.
TEST_F(StunTest, ValidateMessageIntegrity32) {
  // Try the messages from RFC 5769.
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kSampleRequestMI32),
      sizeof(kSampleRequestMI32), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kSampleRequestMI32),
      sizeof(kSampleRequestMI32), "InvalidPassword"));

  // Try some edge cases.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithZeroLength),
      sizeof(kStunMessageWithZeroLength), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithExcessLength),
      sizeof(kStunMessageWithExcessLength), kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithSmallLength),
      sizeof(kStunMessageWithSmallLength), kRfc5769SampleMsgPassword));

  // Again, but with the lengths matching what is claimed in the headers.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithZeroLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithZeroLength[2]),
      kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithExcessLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithExcessLength[2]),
      kRfc5769SampleMsgPassword));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithSmallLength),
      kStunHeaderSize + rtc::GetBE16(&kStunMessageWithSmallLength[2]),
      kRfc5769SampleMsgPassword));

  // Check that a too-short HMAC doesn't cause buffer overflow.
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(kStunMessageWithBadHmacAtEnd),
      sizeof(kStunMessageWithBadHmacAtEnd), kRfc5769SampleMsgPassword));

  // Test that munging a single bit anywhere in the message causes the
  // message-integrity check to fail, unless it is after the M-I attribute.
  char buf[sizeof(kSampleRequestMI32)];
  memcpy(buf, kSampleRequestMI32, sizeof(kSampleRequestMI32));
  for (size_t i = 0; i < sizeof(buf); ++i) {
    buf[i] ^= 0x01;
    if (i > 0)
      buf[i - 1] ^= 0x01;
    EXPECT_EQ(i >= sizeof(buf) - 8,
              StunMessage::ValidateMessageIntegrity32ForTesting(
                  buf, sizeof(buf), kRfc5769SampleMsgPassword));
  }
}

// Validate that we generate correct MESSAGE-INTEGRITY-32 attributes.
TEST_F(StunTest, AddMessageIntegrity32) {
  IceMessage msg;
  rtc::ByteBufferReader buf(kRfc5769SampleRequestWithoutMI);
  EXPECT_TRUE(msg.Read(&buf));
  EXPECT_TRUE(msg.AddMessageIntegrity32(kRfc5769SampleMsgPassword));
  const StunByteStringAttribute* mi_attr =
      msg.GetByteString(STUN_ATTR_GOOG_MESSAGE_INTEGRITY_32);
  EXPECT_EQ(4U, mi_attr->length());
  EXPECT_EQ(0, memcmp(mi_attr->array_view().data(), kCalculatedHmac1_32,
                      sizeof(kCalculatedHmac1_32)));

  rtc::ByteBufferWriter buf1;
  EXPECT_TRUE(msg.Write(&buf1));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(),
      kRfc5769SampleMsgPassword));

  IceMessage msg2;
  rtc::ByteBufferReader buf2(kRfc5769SampleResponseWithoutMI);
  EXPECT_TRUE(msg2.Read(&buf2));
  EXPECT_TRUE(msg2.AddMessageIntegrity32(kRfc5769SampleMsgPassword));
  const StunByteStringAttribute* mi_attr2 =
      msg2.GetByteString(STUN_ATTR_GOOG_MESSAGE_INTEGRITY_32);
  EXPECT_EQ(4U, mi_attr2->length());
  EXPECT_EQ(0, memcmp(mi_attr2->array_view().data(), kCalculatedHmac2_32,
                      sizeof(kCalculatedHmac2_32)));

  rtc::ByteBufferWriter buf3;
  EXPECT_TRUE(msg2.Write(&buf3));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(buf3.Data()), buf3.Length(),
      kRfc5769SampleMsgPassword));
}

// Validate that the message validates if both MESSAGE-INTEGRITY-32 and
// MESSAGE-INTEGRITY are present in the message.
// This is not expected to be used, but is not forbidden.
TEST_F(StunTest, AddMessageIntegrity32AndMessageIntegrity) {
  IceMessage msg;
  auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
  attr->CopyBytes("keso", sizeof("keso"));
  msg.AddAttribute(std::move(attr));
  msg.AddMessageIntegrity32("password1");
  msg.AddMessageIntegrity("password2");

  rtc::ByteBufferWriter buf1;
  EXPECT_TRUE(msg.Write(&buf1));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(), "password1"));
  EXPECT_TRUE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(), "password2"));

  EXPECT_FALSE(StunMessage::ValidateMessageIntegrity32ForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(), "password2"));
  EXPECT_FALSE(StunMessage::ValidateMessageIntegrityForTesting(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length(), "password1"));
}

// Check our STUN message validation code against the RFC5769 test messages.
TEST_F(StunTest, ValidateFingerprint) {
  EXPECT_TRUE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kRfc5769SampleRequest),
      sizeof(kRfc5769SampleRequest)));
  EXPECT_TRUE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kRfc5769SampleResponse),
      sizeof(kRfc5769SampleResponse)));
  EXPECT_TRUE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kRfc5769SampleResponseIPv6),
      sizeof(kRfc5769SampleResponseIPv6)));

  EXPECT_FALSE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kStunMessageWithZeroLength),
      sizeof(kStunMessageWithZeroLength)));
  EXPECT_FALSE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kStunMessageWithExcessLength),
      sizeof(kStunMessageWithExcessLength)));
  EXPECT_FALSE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(kStunMessageWithSmallLength),
      sizeof(kStunMessageWithSmallLength)));

  // Test that munging a single bit anywhere in the message causes the
  // fingerprint check to fail.
  char buf[sizeof(kRfc5769SampleRequest)];
  memcpy(buf, kRfc5769SampleRequest, sizeof(kRfc5769SampleRequest));
  for (size_t i = 0; i < sizeof(buf); ++i) {
    buf[i] ^= 0x01;
    if (i > 0)
      buf[i - 1] ^= 0x01;
    EXPECT_FALSE(StunMessage::ValidateFingerprint(buf, sizeof(buf)));
  }
  // Put them all back to normal and the check should pass again.
  buf[sizeof(buf) - 1] ^= 0x01;
  EXPECT_TRUE(StunMessage::ValidateFingerprint(buf, sizeof(buf)));
}

TEST_F(StunTest, AddFingerprint) {
  IceMessage msg;
  rtc::ByteBufferReader buf(kRfc5769SampleRequestWithoutMI);
  EXPECT_TRUE(msg.Read(&buf));
  EXPECT_TRUE(msg.AddFingerprint());

  rtc::ByteBufferWriter buf1;
  EXPECT_TRUE(msg.Write(&buf1));
  EXPECT_TRUE(StunMessage::ValidateFingerprint(
      reinterpret_cast<const char*>(buf1.Data()), buf1.Length()));
}

// Sample "GTURN" relay message.
// clang-format off
// clang formatting doesn't respect inline comments.
static const uint8_t kRelayMessage[] = {
  0x00, 0x01, 0x00, 88,    // message header
  0x21, 0x12, 0xA4, 0x42,  // magic cookie
  '0', '1', '2', '3',      // transaction id
  '4', '5', '6', '7',
  '8', '9', 'a', 'b',
  0x00, 0x01, 0x00, 8,     // mapped address
  0x00, 0x01, 0x00, 13,
  0x00, 0x00, 0x00, 17,
  0x00, 0x06, 0x00, 12,    // username
  'a', 'b', 'c', 'd',
  'e', 'f', 'g', 'h',
  'i', 'j', 'k', 'l',
  0x00, 0x0d, 0x00, 4,     // lifetime
  0x00, 0x00, 0x00, 11,
  0x00, 0x0f, 0x00, 4,     // magic cookie
  0x72, 0xc6, 0x4b, 0xc6,
  0x00, 0x10, 0x00, 4,     // bandwidth
  0x00, 0x00, 0x00, 6,
  0x00, 0x11, 0x00, 8,     // destination address
  0x00, 0x01, 0x00, 13,
  0x00, 0x00, 0x00, 17,
  0x00, 0x12, 0x00, 8,     // source address 2
  0x00, 0x01, 0x00, 13,
  0x00, 0x00, 0x00, 17,
  0x00, 0x13, 0x00, 7,     // data
  'a', 'b', 'c', 'd',
  'e', 'f', 'g', 0         // DATA must be padded per rfc5766.
};
// clang-format on

// Test that we can read the GTURN-specific fields.
TEST_F(StunTest, ReadRelayMessage) {
  RelayMessage msg;

  rtc::ByteBufferReader buf(kRelayMessage);
  EXPECT_TRUE(msg.Read(&buf));

  EXPECT_EQ(STUN_BINDING_REQUEST, msg.type());
  EXPECT_EQ(sizeof(kRelayMessage) - 20, msg.length());
  EXPECT_EQ("0123456789ab", msg.transaction_id());

  RelayMessage msg2(STUN_BINDING_REQUEST, "0123456789ab");

  in_addr legacy_in_addr;
  legacy_in_addr.s_addr = htonl(17U);
  rtc::IPAddress legacy_ip(legacy_in_addr);

  const StunAddressAttribute* addr = msg.GetAddress(STUN_ATTR_MAPPED_ADDRESS);
  ASSERT_TRUE(addr != NULL);
  EXPECT_EQ(1, addr->family());
  EXPECT_EQ(13, addr->port());
  EXPECT_EQ(legacy_ip, addr->ipaddr());

  auto addr2 = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
  addr2->SetPort(13);
  addr2->SetIP(legacy_ip);
  msg2.AddAttribute(std::move(addr2));

  const StunByteStringAttribute* bytes = msg.GetByteString(STUN_ATTR_USERNAME);
  ASSERT_TRUE(bytes != NULL);
  EXPECT_EQ(12U, bytes->length());
  EXPECT_EQ("abcdefghijkl", bytes->string_view());

  auto bytes2 = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
  bytes2->CopyBytes("abcdefghijkl");
  msg2.AddAttribute(std::move(bytes2));

  const StunUInt32Attribute* uval = msg.GetUInt32(STUN_ATTR_LIFETIME);
  ASSERT_TRUE(uval != NULL);
  EXPECT_EQ(11U, uval->value());

  auto uval2 = StunAttribute::CreateUInt32(STUN_ATTR_LIFETIME);
  uval2->SetValue(11);
  msg2.AddAttribute(std::move(uval2));

  bytes = msg.GetByteString(STUN_ATTR_MAGIC_COOKIE);
  ASSERT_TRUE(bytes != NULL);
  EXPECT_EQ(4U, bytes->length());
  EXPECT_EQ(0, memcmp(bytes->array_view().data(), TURN_MAGIC_COOKIE_VALUE,
                      sizeof(TURN_MAGIC_COOKIE_VALUE)));

  bytes2 = StunAttribute::CreateByteString(STUN_ATTR_MAGIC_COOKIE);
  bytes2->CopyBytes(reinterpret_cast<const char*>(TURN_MAGIC_COOKIE_VALUE),
                    sizeof(TURN_MAGIC_COOKIE_VALUE));
  msg2.AddAttribute(std::move(bytes2));

  uval = msg.GetUInt32(STUN_ATTR_BANDWIDTH);
  ASSERT_TRUE(uval != NULL);
  EXPECT_EQ(6U, uval->value());

  uval2 = StunAttribute::CreateUInt32(STUN_ATTR_BANDWIDTH);
  uval2->SetValue(6);
  msg2.AddAttribute(std::move(uval2));

  addr = msg.GetAddress(STUN_ATTR_DESTINATION_ADDRESS);
  ASSERT_TRUE(addr != NULL);
  EXPECT_EQ(1, addr->family());
  EXPECT_EQ(13, addr->port());
  EXPECT_EQ(legacy_ip, addr->ipaddr());

  addr2 = StunAttribute::CreateAddress(STUN_ATTR_DESTINATION_ADDRESS);
  addr2->SetPort(13);
  addr2->SetIP(legacy_ip);
  msg2.AddAttribute(std::move(addr2));

  addr = msg.GetAddress(STUN_ATTR_SOURCE_ADDRESS2);
  ASSERT_TRUE(addr != NULL);
  EXPECT_EQ(1, addr->family());
  EXPECT_EQ(13, addr->port());
  EXPECT_EQ(legacy_ip, addr->ipaddr());

  addr2 = StunAttribute::CreateAddress(STUN_ATTR_SOURCE_ADDRESS2);
  addr2->SetPort(13);
  addr2->SetIP(legacy_ip);
  msg2.AddAttribute(std::move(addr2));

  bytes = msg.GetByteString(STUN_ATTR_DATA);
  ASSERT_TRUE(bytes != NULL);
  EXPECT_EQ(7U, bytes->length());
  EXPECT_EQ("abcdefg", bytes->string_view());

  bytes2 = StunAttribute::CreateByteString(STUN_ATTR_DATA);
  bytes2->CopyBytes("abcdefg");
  msg2.AddAttribute(std::move(bytes2));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  EXPECT_EQ(sizeof(kRelayMessage), out.Length());
  size_t len1 = out.Length();
  rtc::ByteBufferReader read_buf(out);
  std::string outstring;
  read_buf.ReadString(&outstring, len1);
  EXPECT_EQ(0, memcmp(outstring.c_str(), kRelayMessage, len1));

  rtc::ByteBufferWriter out2;
  EXPECT_TRUE(msg2.Write(&out2));
  EXPECT_EQ(sizeof(kRelayMessage), out2.Length());
  size_t len2 = out2.Length();
  rtc::ByteBufferReader read_buf2(out2);
  std::string outstring2;
  read_buf2.ReadString(&outstring2, len2);
  EXPECT_EQ(0, memcmp(outstring2.c_str(), kRelayMessage, len2));
}

// Test that we can remove attribute from a message.
TEST_F(StunTest, RemoveAttribute) {
  StunMessage msg;

  // Removing something that does exist should return nullptr.
  EXPECT_EQ(msg.RemoveAttribute(STUN_ATTR_USERNAME), nullptr);

  {
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
    attr->CopyBytes("kes", sizeof("kes"));
    msg.AddAttribute(std::move(attr));
  }

  size_t len = msg.length();
  {
    auto attr = msg.RemoveAttribute(STUN_ATTR_USERNAME);
    ASSERT_NE(attr, nullptr);
    EXPECT_EQ(attr->type(), STUN_ATTR_USERNAME);
    EXPECT_STREQ("kes", static_cast<StunByteStringAttribute*>(attr.get())
                            ->string_view()
                            .data());
    EXPECT_LT(msg.length(), len);
  }

  // Now add same attribute type twice.
  {
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
    attr->CopyBytes("kes", sizeof("kes"));
    msg.AddAttribute(std::move(attr));
  }

  {
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
    attr->CopyBytes("kenta", sizeof("kenta"));
    msg.AddAttribute(std::move(attr));
  }

  // Remove should remove the last added occurrence.
  {
    auto attr = msg.RemoveAttribute(STUN_ATTR_USERNAME);
    ASSERT_NE(attr, nullptr);
    EXPECT_EQ(attr->type(), STUN_ATTR_USERNAME);
    EXPECT_STREQ("kenta", static_cast<StunByteStringAttribute*>(attr.get())
                              ->string_view()
                              .data());
  }

  // Remove should remove the last added occurrence.
  {
    auto attr = msg.RemoveAttribute(STUN_ATTR_USERNAME);
    ASSERT_NE(attr, nullptr);
    EXPECT_EQ(attr->type(), STUN_ATTR_USERNAME);
    EXPECT_STREQ("kes", static_cast<StunByteStringAttribute*>(attr.get())
                            ->string_view()
                            .data());
  }

  // Removing something that does exist should return nullptr.
  EXPECT_EQ(msg.RemoveAttribute(STUN_ATTR_USERNAME), nullptr);
}

// Test that we can remove attribute from a message.
TEST_F(StunTest, ClearAttributes) {
  StunMessage msg;

  auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
  attr->CopyBytes("kes", sizeof("kes"));
  msg.AddAttribute(std::move(attr));
  size_t len = msg.length();

  msg.ClearAttributes();
  EXPECT_EQ(msg.length(), len - /* 3 + 1 byte padding + header */ 8);
  EXPECT_EQ(nullptr, msg.GetByteString(STUN_ATTR_USERNAME));
}

// Test CopyStunAttribute
TEST_F(StunTest, CopyAttribute) {
  rtc::ByteBufferWriter buf;
  rtc::ByteBufferWriter* buffer_ptrs[] = {&buf, nullptr};
  // Test both with and without supplied ByteBufferWriter.
  for (auto buffer_ptr : buffer_ptrs) {
    {  // Test StunByteStringAttribute.
      auto attr = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
      attr->CopyBytes("kes", sizeof("kes"));

      auto copy = CopyStunAttribute(*attr.get(), buffer_ptr);
      ASSERT_EQ(copy->value_type(), STUN_VALUE_BYTE_STRING);
      EXPECT_STREQ("kes", static_cast<StunByteStringAttribute*>(copy.get())
                              ->string_view()
                              .data());
    }

    {  // Test StunAddressAttribute.
      rtc::IPAddress test_ip(kIPv6TestAddress2);
      auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
      rtc::SocketAddress test_addr(test_ip, kTestMessagePort2);
      addr->SetAddress(test_addr);
      CheckStunAddressAttribute(addr.get(), STUN_ADDRESS_IPV6,
                                kTestMessagePort2, test_ip);

      auto copy = CopyStunAttribute(*addr.get(), buffer_ptr);
      ASSERT_EQ(copy->value_type(), STUN_VALUE_ADDRESS);
      CheckStunAddressAttribute(static_cast<StunAddressAttribute*>(copy.get()),
                                STUN_ADDRESS_IPV6, kTestMessagePort2, test_ip);
    }

    {  // Test StunAddressAttribute.
      rtc::IPAddress test_ip(kIPv6TestAddress2);
      auto addr = StunAttribute::CreateAddress(STUN_ATTR_XOR_MAPPED_ADDRESS);
      rtc::SocketAddress test_addr(test_ip, kTestMessagePort2);
      addr->SetAddress(test_addr);
      CheckStunAddressAttribute(addr.get(), STUN_ADDRESS_IPV6,
                                kTestMessagePort2, test_ip);

      auto copy = CopyStunAttribute(*addr.get(), buffer_ptr);
      ASSERT_EQ(copy->value_type(), STUN_VALUE_ADDRESS);
      CheckStunAddressAttribute(static_cast<StunAddressAttribute*>(copy.get()),
                                STUN_ADDRESS_IPV6, kTestMessagePort2, test_ip);
    }
  }
}

// Test Clone
TEST_F(StunTest, Clone) {
  IceMessage msg(0, "0123456789ab");
  {
    auto errorcode = StunAttribute::CreateErrorCode();
    errorcode->SetCode(kTestErrorCode);
    errorcode->SetReason(kTestErrorReason);
    msg.AddAttribute(std::move(errorcode));
  }
  {
    auto bytes2 = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
    bytes2->CopyBytes("abcdefghijkl");
    msg.AddAttribute(std::move(bytes2));
  }
  {
    auto uval2 = StunAttribute::CreateUInt32(STUN_ATTR_RETRANSMIT_COUNT);
    uval2->SetValue(11);
    msg.AddAttribute(std::move(uval2));
  }
  {
    auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
    addr->SetIP(rtc::IPAddress(kIPv6TestAddress1));
    addr->SetPort(kTestMessagePort1);
    msg.AddAttribute(std::move(addr));
  }
  auto copy = msg.Clone();
  ASSERT_NE(nullptr, copy.get());

  rtc::ByteBufferWriter out1;
  EXPECT_TRUE(msg.Write(&out1));
  rtc::ByteBufferWriter out2;
  EXPECT_TRUE(copy->Write(&out2));

  ASSERT_EQ(out1.Length(), out2.Length());
  EXPECT_EQ(0, memcmp(out1.Data(), out2.Data(), out1.Length()));
}

// Test EqualAttributes
TEST_F(StunTest, EqualAttributes) {
  IceMessage msg;
  {
    auto errorcode = StunAttribute::CreateErrorCode();
    errorcode->SetCode(kTestErrorCode);
    errorcode->SetReason(kTestErrorReason);
    msg.AddAttribute(std::move(errorcode));
  }
  {
    auto bytes2 = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
    bytes2->CopyBytes("abcdefghijkl");
    msg.AddAttribute(std::move(bytes2));
  }
  {
    auto uval2 = StunAttribute::CreateUInt32(STUN_ATTR_RETRANSMIT_COUNT);
    uval2->SetValue(11);
    msg.AddAttribute(std::move(uval2));
  }
  {
    auto addr = StunAttribute::CreateAddress(STUN_ATTR_MAPPED_ADDRESS);
    addr->SetIP(rtc::IPAddress(kIPv6TestAddress1));
    addr->SetPort(kTestMessagePort1);
    msg.AddAttribute(std::move(addr));
  }
  auto copy = msg.Clone();
  ASSERT_NE(nullptr, copy.get());

  EXPECT_TRUE(copy->EqualAttributes(&msg, [](int type) { return true; }));

  {
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_NONCE);
    attr->CopyBytes("keso");
    msg.AddAttribute(std::move(attr));
    EXPECT_FALSE(copy->EqualAttributes(&msg, [](int type) { return true; }));
    EXPECT_TRUE(copy->EqualAttributes(
        &msg, [](int type) { return type != STUN_ATTR_NONCE; }));
  }

  {
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_NONCE);
    attr->CopyBytes("keso");
    copy->AddAttribute(std::move(attr));
    EXPECT_TRUE(copy->EqualAttributes(&msg, [](int type) { return true; }));
  }
  {
    copy->RemoveAttribute(STUN_ATTR_NONCE);
    auto attr = StunAttribute::CreateByteString(STUN_ATTR_NONCE);
    attr->CopyBytes("kent");
    copy->AddAttribute(std::move(attr));
    EXPECT_FALSE(copy->EqualAttributes(&msg, [](int type) { return true; }));
    EXPECT_TRUE(copy->EqualAttributes(
        &msg, [](int type) { return type != STUN_ATTR_NONCE; }));
  }

  {
    msg.RemoveAttribute(STUN_ATTR_NONCE);
    EXPECT_FALSE(copy->EqualAttributes(&msg, [](int type) { return true; }));
    EXPECT_TRUE(copy->EqualAttributes(
        &msg, [](int type) { return type != STUN_ATTR_NONCE; }));
  }
}

TEST_F(StunTest, ReduceTransactionIdIsHostOrderIndependent) {
  const std::string transaction_id = "abcdefghijkl";
  StunMessage message(0, transaction_id);
  uint32_t reduced_transaction_id = message.reduced_transaction_id();
  EXPECT_EQ(reduced_transaction_id, 1835954016u);
}

TEST_F(StunTest, GoogMiscInfo) {
  StunMessage msg(STUN_BINDING_REQUEST, "ABCDEFGHIJKL");
  const size_t size =
      /* msg header */ 20 +
      /* attr header */ 4 +
      /* 3 * 2 rounded to multiple of 4 */ 8;
  auto list =
      StunAttribute::CreateUInt16ListAttribute(STUN_ATTR_GOOG_MISC_INFO);
  list->AddTypeAtIndex(0, 0x1U);
  list->AddTypeAtIndex(3, 0x1000U);
  list->AddTypeAtIndex(2, 0xAB0CU);
  msg.AddAttribute(std::move(list));
  CheckStunHeader(msg, STUN_BINDING_REQUEST, (size - 20));

  rtc::ByteBufferWriter out;
  EXPECT_TRUE(msg.Write(&out));
  ASSERT_EQ(size, out.Length());

  size_t read_size = ReadStunMessageTestCase(
      &msg, reinterpret_cast<const uint8_t*>(out.Data()), out.Length());
  ASSERT_EQ(read_size + 20, size);
  CheckStunHeader(msg, STUN_BINDING_REQUEST, read_size);
  const StunUInt16ListAttribute* types =
      msg.GetUInt16List(STUN_ATTR_GOOG_MISC_INFO);
  ASSERT_TRUE(types != NULL);
  EXPECT_EQ(4U, types->Size());
  EXPECT_EQ(0x1U, types->GetType(0));
  EXPECT_EQ(0x0U, types->GetType(1));
  EXPECT_EQ(0x1000U, types->GetType(3));
  EXPECT_EQ(0xAB0CU, types->GetType(2));
}

TEST_F(StunTest, IsStunMethod) {
  int methods[] = {STUN_BINDING_REQUEST};
  EXPECT_TRUE(StunMessage::IsStunMethod(
      methods, reinterpret_cast<const char*>(kRfc5769SampleRequest),
      sizeof(kRfc5769SampleRequest)));
}

TEST_F(StunTest, SizeRestrictionOnAttributes) {
  StunMessage msg(STUN_BINDING_REQUEST, "ABCDEFGHIJKL");
  auto long_username = StunAttribute::CreateByteString(STUN_ATTR_USERNAME);
  std::string long_string(509, 'x');
  long_username->CopyBytes(long_string.c_str(), long_string.size());
  msg.AddAttribute(std::move(long_username));
  rtc::ByteBufferWriter out;
  ASSERT_FALSE(msg.Write(&out));
}

TEST_F(StunTest, ValidateMessageIntegrityWithParser) {
  webrtc::metrics::Reset();  // Ensure counters start from zero.
  // Try the messages from RFC 5769.
  StunMessage message;
  rtc::ByteBufferReader reader(kRfc5769SampleRequest);
  EXPECT_TRUE(message.Read(&reader));
  EXPECT_EQ(message.ValidateMessageIntegrity(kRfc5769SampleMsgPassword),
            StunMessage::IntegrityStatus::kIntegrityOk);
  EXPECT_EQ(webrtc::metrics::NumEvents(
                "WebRTC.Stun.Integrity.Request",
                static_cast<int>(StunMessage::IntegrityStatus::kIntegrityOk)),
            1);
  EXPECT_EQ(message.RevalidateMessageIntegrity("Invalid password"),
            StunMessage::IntegrityStatus::kIntegrityBad);
  EXPECT_EQ(webrtc::metrics::NumEvents(
                "WebRTC.Stun.Integrity.Request",
                static_cast<int>(StunMessage::IntegrityStatus::kIntegrityBad)),
            1);
  EXPECT_EQ(webrtc::metrics::NumSamples("WebRTC.Stun.Integrity.Request"), 2);
}

}  // namespace cricket
