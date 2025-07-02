/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/srtp_transport.h"

#include <string.h>

#include <vector>

#include "call/rtp_demuxer.h"
#include "media/base/fake_rtp.h"
#include "p2p/base/dtls_transport_internal.h"
#include "p2p/base/fake_packet_transport.h"
#include "pc/test/rtp_transport_test_util.h"
#include "pc/test/srtp_test_util.h"
#include "rtc_base/async_packet_socket.h"
#include "rtc_base/byte_order.h"
#include "rtc_base/checks.h"
#include "rtc_base/containers/flat_set.h"
#include "rtc_base/ssl_stream_adapter.h"
#include "rtc_base/third_party/sigslot/sigslot.h"
#include "test/gtest.h"
#include "test/scoped_key_value_config.h"

using rtc::kSrtpAeadAes128Gcm;
using rtc::kTestKey1;
using rtc::kTestKey2;
using rtc::kTestKeyLen;

namespace webrtc {
static const uint8_t kTestKeyGcm128_1[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZ12";
static const uint8_t kTestKeyGcm128_2[] = "21ZYXWVUTSRQPONMLKJIHGFEDCBA";
static const int kTestKeyGcm128Len = 28;  // 128 bits key + 96 bits salt.
static const uint8_t kTestKeyGcm256_1[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqr";
static const uint8_t kTestKeyGcm256_2[] =
    "rqponmlkjihgfedcbaZYXWVUTSRQPONMLKJIHGFEDCBA";
static const int kTestKeyGcm256Len = 44;  // 256 bits key + 96 bits salt.

class SrtpTransportTest : public ::testing::Test, public sigslot::has_slots<> {
 protected:
  SrtpTransportTest() {
    bool rtcp_mux_enabled = true;

    rtp_packet_transport1_ =
        std::make_unique<rtc::FakePacketTransport>("fake_packet_transport1");
    rtp_packet_transport2_ =
        std::make_unique<rtc::FakePacketTransport>("fake_packet_transport2");

    bool asymmetric = false;
    rtp_packet_transport1_->SetDestination(rtp_packet_transport2_.get(),
                                           asymmetric);

    srtp_transport1_ =
        std::make_unique<SrtpTransport>(rtcp_mux_enabled, field_trials_);
    srtp_transport2_ =
        std::make_unique<SrtpTransport>(rtcp_mux_enabled, field_trials_);

    srtp_transport1_->SetRtpPacketTransport(rtp_packet_transport1_.get());
    srtp_transport2_->SetRtpPacketTransport(rtp_packet_transport2_.get());

    srtp_transport1_->SubscribeRtcpPacketReceived(
        &rtp_sink1_,
        [this](rtc::CopyOnWriteBuffer* buffer, int64_t packet_time_ms) {
          rtp_sink1_.OnRtcpPacketReceived(buffer, packet_time_ms);
        });
    srtp_transport2_->SubscribeRtcpPacketReceived(
        &rtp_sink2_,
        [this](rtc::CopyOnWriteBuffer* buffer, int64_t packet_time_ms) {
          rtp_sink2_.OnRtcpPacketReceived(buffer, packet_time_ms);
        });

    RtpDemuxerCriteria demuxer_criteria;
    // 0x00 is the payload type used in kPcmuFrame.
    demuxer_criteria.payload_types().insert(0x00);

    srtp_transport1_->RegisterRtpDemuxerSink(demuxer_criteria, &rtp_sink1_);
    srtp_transport2_->RegisterRtpDemuxerSink(demuxer_criteria, &rtp_sink2_);
  }

  ~SrtpTransportTest() {
    if (srtp_transport1_) {
      srtp_transport1_->UnregisterRtpDemuxerSink(&rtp_sink1_);
    }
    if (srtp_transport2_) {
      srtp_transport2_->UnregisterRtpDemuxerSink(&rtp_sink2_);
    }
  }

  // With external auth enabled, SRTP doesn't write the auth tag and
  // unprotect would fail. Check accessing the information about the
  // tag instead, similar to what the actual code would do that relies
  // on external auth.
  void TestRtpAuthParams(SrtpTransport* transport, const std::string& cs) {
    int overhead;
    EXPECT_TRUE(transport->GetSrtpOverhead(&overhead));
    switch (rtc::SrtpCryptoSuiteFromName(cs)) {
      case rtc::kSrtpAes128CmSha1_32:
        EXPECT_EQ(32 / 8, overhead);  // 32-bit tag.
        break;
      case rtc::kSrtpAes128CmSha1_80:
        EXPECT_EQ(80 / 8, overhead);  // 80-bit tag.
        break;
      default:
        RTC_DCHECK_NOTREACHED();
        break;
    }

    uint8_t* auth_key = nullptr;
    int key_len = 0;
    int tag_len = 0;
    EXPECT_TRUE(transport->GetRtpAuthParams(&auth_key, &key_len, &tag_len));
    EXPECT_NE(nullptr, auth_key);
    EXPECT_EQ(160 / 8, key_len);  // Length of SHA-1 is 160 bits.
    EXPECT_EQ(overhead, tag_len);
  }

  void TestSendRecvRtpPacket(const std::string& cipher_suite_name) {
    size_t rtp_len = sizeof(kPcmuFrame);
    size_t packet_size = rtp_len + rtc::rtp_auth_tag_len(cipher_suite_name);
    rtc::Buffer rtp_packet_buffer(packet_size);
    char* rtp_packet_data = rtp_packet_buffer.data<char>();
    memcpy(rtp_packet_data, kPcmuFrame, rtp_len);
    // In order to be able to run this test function multiple times we can not
    // use the same sequence number twice. Increase the sequence number by one.
    rtc::SetBE16(reinterpret_cast<uint8_t*>(rtp_packet_data) + 2,
                 ++sequence_number_);
    rtc::CopyOnWriteBuffer rtp_packet1to2(rtp_packet_data, rtp_len,
                                          packet_size);
    rtc::CopyOnWriteBuffer rtp_packet2to1(rtp_packet_data, rtp_len,
                                          packet_size);

    char original_rtp_data[sizeof(kPcmuFrame)];
    memcpy(original_rtp_data, rtp_packet_data, rtp_len);

    rtc::PacketOptions options;
    // Send a packet from `srtp_transport1_` to `srtp_transport2_` and verify
    // that the packet can be successfully received and decrypted.
    ASSERT_TRUE(srtp_transport1_->SendRtpPacket(&rtp_packet1to2, options,
                                                cricket::PF_SRTP_BYPASS));
    if (srtp_transport1_->IsExternalAuthActive()) {
      TestRtpAuthParams(srtp_transport1_.get(), cipher_suite_name);
    } else {
      ASSERT_TRUE(rtp_sink2_.last_recv_rtp_packet().data());
      EXPECT_EQ(0, memcmp(rtp_sink2_.last_recv_rtp_packet().data(),
                          original_rtp_data, rtp_len));
      // Get the encrypted packet from underneath packet transport and verify
      // the data is actually encrypted.
      auto fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
          srtp_transport1_->rtp_packet_transport());
      EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                          original_rtp_data, rtp_len));
    }

    // Do the same thing in the opposite direction;
    ASSERT_TRUE(srtp_transport2_->SendRtpPacket(&rtp_packet2to1, options,
                                                cricket::PF_SRTP_BYPASS));
    if (srtp_transport2_->IsExternalAuthActive()) {
      TestRtpAuthParams(srtp_transport2_.get(), cipher_suite_name);
    } else {
      ASSERT_TRUE(rtp_sink1_.last_recv_rtp_packet().data());
      EXPECT_EQ(0, memcmp(rtp_sink1_.last_recv_rtp_packet().data(),
                          original_rtp_data, rtp_len));
      auto fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
          srtp_transport2_->rtp_packet_transport());
      EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                          original_rtp_data, rtp_len));
    }
  }

  void TestSendRecvRtcpPacket(const std::string& cipher_suite_name) {
    size_t rtcp_len = sizeof(::kRtcpReport);
    size_t packet_size =
        rtcp_len + 4 + rtc::rtcp_auth_tag_len(cipher_suite_name);
    rtc::Buffer rtcp_packet_buffer(packet_size);
    char* rtcp_packet_data = rtcp_packet_buffer.data<char>();
    memcpy(rtcp_packet_data, ::kRtcpReport, rtcp_len);

    rtc::CopyOnWriteBuffer rtcp_packet1to2(rtcp_packet_data, rtcp_len,
                                           packet_size);
    rtc::CopyOnWriteBuffer rtcp_packet2to1(rtcp_packet_data, rtcp_len,
                                           packet_size);

    rtc::PacketOptions options;
    // Send a packet from `srtp_transport1_` to `srtp_transport2_` and verify
    // that the packet can be successfully received and decrypted.
    ASSERT_TRUE(srtp_transport1_->SendRtcpPacket(&rtcp_packet1to2, options,
                                                 cricket::PF_SRTP_BYPASS));
    ASSERT_TRUE(rtp_sink2_.last_recv_rtcp_packet().data());
    EXPECT_EQ(0, memcmp(rtp_sink2_.last_recv_rtcp_packet().data(),
                        rtcp_packet_data, rtcp_len));
    // Get the encrypted packet from underneath packet transport and verify the
    // data is actually encrypted.
    auto fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
        srtp_transport1_->rtp_packet_transport());
    EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                        rtcp_packet_data, rtcp_len));

    // Do the same thing in the opposite direction;
    ASSERT_TRUE(srtp_transport2_->SendRtcpPacket(&rtcp_packet2to1, options,
                                                 cricket::PF_SRTP_BYPASS));
    ASSERT_TRUE(rtp_sink1_.last_recv_rtcp_packet().data());
    EXPECT_EQ(0, memcmp(rtp_sink1_.last_recv_rtcp_packet().data(),
                        rtcp_packet_data, rtcp_len));
    fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
        srtp_transport2_->rtp_packet_transport());
    EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                        rtcp_packet_data, rtcp_len));
  }

  void TestSendRecvPacket(bool enable_external_auth,
                          int cs,
                          const uint8_t* key1,
                          int key1_len,
                          const uint8_t* key2,
                          int key2_len,
                          const std::string& cipher_suite_name) {
    EXPECT_EQ(key1_len, key2_len);
    EXPECT_EQ(cipher_suite_name, rtc::SrtpCryptoSuiteToName(cs));
    if (enable_external_auth) {
      srtp_transport1_->EnableExternalAuth();
      srtp_transport2_->EnableExternalAuth();
    }
    std::vector<int> extension_ids;
    EXPECT_TRUE(srtp_transport1_->SetRtpParams(
        cs, key1, key1_len, extension_ids, cs, key2, key2_len, extension_ids));
    EXPECT_TRUE(srtp_transport2_->SetRtpParams(
        cs, key2, key2_len, extension_ids, cs, key1, key1_len, extension_ids));
    EXPECT_TRUE(srtp_transport1_->SetRtcpParams(
        cs, key1, key1_len, extension_ids, cs, key2, key2_len, extension_ids));
    EXPECT_TRUE(srtp_transport2_->SetRtcpParams(
        cs, key2, key2_len, extension_ids, cs, key1, key1_len, extension_ids));
    EXPECT_TRUE(srtp_transport1_->IsSrtpActive());
    EXPECT_TRUE(srtp_transport2_->IsSrtpActive());
    if (rtc::IsGcmCryptoSuite(cs)) {
      EXPECT_FALSE(srtp_transport1_->IsExternalAuthActive());
      EXPECT_FALSE(srtp_transport2_->IsExternalAuthActive());
    } else if (enable_external_auth) {
      EXPECT_TRUE(srtp_transport1_->IsExternalAuthActive());
      EXPECT_TRUE(srtp_transport2_->IsExternalAuthActive());
    }
    TestSendRecvRtpPacket(cipher_suite_name);
    TestSendRecvRtcpPacket(cipher_suite_name);
  }

  void TestSendRecvPacketWithEncryptedHeaderExtension(
      const std::string& cs,
      const std::vector<int>& encrypted_header_ids) {
    size_t rtp_len = sizeof(kPcmuFrameWithExtensions);
    size_t packet_size = rtp_len + rtc::rtp_auth_tag_len(cs);
    rtc::Buffer rtp_packet_buffer(packet_size);
    char* rtp_packet_data = rtp_packet_buffer.data<char>();
    memcpy(rtp_packet_data, kPcmuFrameWithExtensions, rtp_len);
    // In order to be able to run this test function multiple times we can not
    // use the same sequence number twice. Increase the sequence number by one.
    rtc::SetBE16(reinterpret_cast<uint8_t*>(rtp_packet_data) + 2,
                 ++sequence_number_);
    rtc::CopyOnWriteBuffer rtp_packet1to2(rtp_packet_data, rtp_len,
                                          packet_size);
    rtc::CopyOnWriteBuffer rtp_packet2to1(rtp_packet_data, rtp_len,
                                          packet_size);

    char original_rtp_data[sizeof(kPcmuFrameWithExtensions)];
    memcpy(original_rtp_data, rtp_packet_data, rtp_len);

    rtc::PacketOptions options;
    // Send a packet from `srtp_transport1_` to `srtp_transport2_` and verify
    // that the packet can be successfully received and decrypted.
    ASSERT_TRUE(srtp_transport1_->SendRtpPacket(&rtp_packet1to2, options,
                                                cricket::PF_SRTP_BYPASS));
    ASSERT_TRUE(rtp_sink2_.last_recv_rtp_packet().data());
    EXPECT_EQ(0, memcmp(rtp_sink2_.last_recv_rtp_packet().data(),
                        original_rtp_data, rtp_len));
    // Get the encrypted packet from underneath packet transport and verify the
    // data and header extension are actually encrypted.
    auto fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
        srtp_transport1_->rtp_packet_transport());
    EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                        original_rtp_data, rtp_len));
    CompareHeaderExtensions(
        reinterpret_cast<const char*>(
            fake_rtp_packet_transport->last_sent_packet()->data()),
        fake_rtp_packet_transport->last_sent_packet()->size(),
        original_rtp_data, rtp_len, encrypted_header_ids, false);

    // Do the same thing in the opposite direction;
    ASSERT_TRUE(srtp_transport2_->SendRtpPacket(&rtp_packet2to1, options,
                                                cricket::PF_SRTP_BYPASS));
    ASSERT_TRUE(rtp_sink1_.last_recv_rtp_packet().data());
    EXPECT_EQ(0, memcmp(rtp_sink1_.last_recv_rtp_packet().data(),
                        original_rtp_data, rtp_len));
    fake_rtp_packet_transport = static_cast<rtc::FakePacketTransport*>(
        srtp_transport2_->rtp_packet_transport());
    EXPECT_NE(0, memcmp(fake_rtp_packet_transport->last_sent_packet()->data(),
                        original_rtp_data, rtp_len));
    CompareHeaderExtensions(
        reinterpret_cast<const char*>(
            fake_rtp_packet_transport->last_sent_packet()->data()),
        fake_rtp_packet_transport->last_sent_packet()->size(),
        original_rtp_data, rtp_len, encrypted_header_ids, false);
  }

  void TestSendRecvEncryptedHeaderExtension(int cs,
                                            const uint8_t* key1,
                                            int key1_len,
                                            const uint8_t* key2,
                                            int key2_len,
                                            const std::string& cs_name) {
    std::vector<int> encrypted_headers;
    encrypted_headers.push_back(kHeaderExtensionIDs[0]);
    // Don't encrypt header ids 2 and 3.
    encrypted_headers.push_back(kHeaderExtensionIDs[1]);
    EXPECT_EQ(key1_len, key2_len);
    EXPECT_EQ(cs_name, rtc::SrtpCryptoSuiteToName(cs));
    EXPECT_TRUE(srtp_transport1_->SetRtpParams(cs, key1, key1_len,
                                               encrypted_headers, cs, key2,
                                               key2_len, encrypted_headers));
    EXPECT_TRUE(srtp_transport2_->SetRtpParams(cs, key2, key2_len,
                                               encrypted_headers, cs, key1,
                                               key1_len, encrypted_headers));
    EXPECT_TRUE(srtp_transport1_->IsSrtpActive());
    EXPECT_TRUE(srtp_transport2_->IsSrtpActive());
    EXPECT_FALSE(srtp_transport1_->IsExternalAuthActive());
    EXPECT_FALSE(srtp_transport2_->IsExternalAuthActive());
    TestSendRecvPacketWithEncryptedHeaderExtension(cs_name, encrypted_headers);
  }

  std::unique_ptr<SrtpTransport> srtp_transport1_;
  std::unique_ptr<SrtpTransport> srtp_transport2_;

  std::unique_ptr<rtc::FakePacketTransport> rtp_packet_transport1_;
  std::unique_ptr<rtc::FakePacketTransport> rtp_packet_transport2_;

  TransportObserver rtp_sink1_;
  TransportObserver rtp_sink2_;

  int sequence_number_ = 0;
  test::ScopedKeyValueConfig field_trials_;
};

class SrtpTransportTestWithExternalAuth
    : public SrtpTransportTest,
      public ::testing::WithParamInterface<bool> {};

TEST_P(SrtpTransportTestWithExternalAuth,
       SendAndRecvPacket_AES_CM_128_HMAC_SHA1_80) {
  bool enable_external_auth = GetParam();
  TestSendRecvPacket(enable_external_auth, rtc::kSrtpAes128CmSha1_80, kTestKey1,
                     kTestKeyLen, kTestKey2, kTestKeyLen,
                     rtc::kCsAesCm128HmacSha1_80);
}

TEST_F(SrtpTransportTest,
       SendAndRecvPacketWithHeaderExtension_AES_CM_128_HMAC_SHA1_80) {
  TestSendRecvEncryptedHeaderExtension(rtc::kSrtpAes128CmSha1_80, kTestKey1,
                                       kTestKeyLen, kTestKey2, kTestKeyLen,
                                       rtc::kCsAesCm128HmacSha1_80);
}

TEST_P(SrtpTransportTestWithExternalAuth,
       SendAndRecvPacket_AES_CM_128_HMAC_SHA1_32) {
  bool enable_external_auth = GetParam();
  TestSendRecvPacket(enable_external_auth, rtc::kSrtpAes128CmSha1_32, kTestKey1,
                     kTestKeyLen, kTestKey2, kTestKeyLen,
                     rtc::kCsAesCm128HmacSha1_32);
}

TEST_F(SrtpTransportTest,
       SendAndRecvPacketWithHeaderExtension_AES_CM_128_HMAC_SHA1_32) {
  TestSendRecvEncryptedHeaderExtension(rtc::kSrtpAes128CmSha1_32, kTestKey1,
                                       kTestKeyLen, kTestKey2, kTestKeyLen,
                                       rtc::kCsAesCm128HmacSha1_32);
}

TEST_P(SrtpTransportTestWithExternalAuth,
       SendAndRecvPacket_kSrtpAeadAes128Gcm) {
  bool enable_external_auth = GetParam();
  TestSendRecvPacket(enable_external_auth, rtc::kSrtpAeadAes128Gcm,
                     kTestKeyGcm128_1, kTestKeyGcm128Len, kTestKeyGcm128_2,
                     kTestKeyGcm128Len, rtc::kCsAeadAes128Gcm);
}

TEST_F(SrtpTransportTest,
       SendAndRecvPacketWithHeaderExtension_kSrtpAeadAes128Gcm) {
  TestSendRecvEncryptedHeaderExtension(
      rtc::kSrtpAeadAes128Gcm, kTestKeyGcm128_1, kTestKeyGcm128Len,
      kTestKeyGcm128_2, kTestKeyGcm128Len, rtc::kCsAeadAes128Gcm);
}

TEST_P(SrtpTransportTestWithExternalAuth,
       SendAndRecvPacket_kSrtpAeadAes256Gcm) {
  bool enable_external_auth = GetParam();
  TestSendRecvPacket(enable_external_auth, rtc::kSrtpAeadAes256Gcm,
                     kTestKeyGcm256_1, kTestKeyGcm256Len, kTestKeyGcm256_2,
                     kTestKeyGcm256Len, rtc::kCsAeadAes256Gcm);
}

TEST_F(SrtpTransportTest,
       SendAndRecvPacketWithHeaderExtension_kSrtpAeadAes256Gcm) {
  TestSendRecvEncryptedHeaderExtension(
      rtc::kSrtpAeadAes256Gcm, kTestKeyGcm256_1, kTestKeyGcm256Len,
      kTestKeyGcm256_2, kTestKeyGcm256Len, rtc::kCsAeadAes256Gcm);
}

// Run all tests both with and without external auth enabled.
INSTANTIATE_TEST_SUITE_P(ExternalAuth,
                         SrtpTransportTestWithExternalAuth,
                         ::testing::Values(true, false));

// Test directly setting the params with bogus keys.
TEST_F(SrtpTransportTest, TestSetParamsKeyTooShort) {
  std::vector<int> extension_ids;
  EXPECT_FALSE(srtp_transport1_->SetRtpParams(
      rtc::kSrtpAes128CmSha1_80, kTestKey1, kTestKeyLen - 1, extension_ids,
      rtc::kSrtpAes128CmSha1_80, kTestKey1, kTestKeyLen - 1, extension_ids));
  EXPECT_FALSE(srtp_transport1_->SetRtcpParams(
      rtc::kSrtpAes128CmSha1_80, kTestKey1, kTestKeyLen - 1, extension_ids,
      rtc::kSrtpAes128CmSha1_80, kTestKey1, kTestKeyLen - 1, extension_ids));
}

TEST_F(SrtpTransportTest, RemoveSrtpReceiveStream) {
  test::ScopedKeyValueConfig field_trials(
      "WebRTC-SrtpRemoveReceiveStream/Enabled/");
  auto srtp_transport =
      std::make_unique<SrtpTransport>(/*rtcp_mux_enabled=*/true, field_trials);
  auto rtp_packet_transport = std::make_unique<rtc::FakePacketTransport>(
      "fake_packet_transport_loopback");

  bool asymmetric = false;
  rtp_packet_transport->SetDestination(rtp_packet_transport.get(), asymmetric);
  srtp_transport->SetRtpPacketTransport(rtp_packet_transport.get());

  TransportObserver rtp_sink;

  std::vector<int> extension_ids;
  EXPECT_TRUE(srtp_transport->SetRtpParams(
      rtc::kSrtpAeadAes128Gcm, kTestKeyGcm128_1, kTestKeyGcm128Len,
      extension_ids, rtc::kSrtpAeadAes128Gcm, kTestKeyGcm128_1,
      kTestKeyGcm128Len, extension_ids));

  RtpDemuxerCriteria demuxer_criteria;
  uint32_t ssrc = 0x1;  // SSRC of kPcmuFrame
  demuxer_criteria.ssrcs().insert(ssrc);
  EXPECT_TRUE(
      srtp_transport->RegisterRtpDemuxerSink(demuxer_criteria, &rtp_sink));

  // Create a packet and try to send it three times.
  size_t rtp_len = sizeof(kPcmuFrame);
  size_t packet_size = rtp_len + rtc::rtp_auth_tag_len(rtc::kCsAeadAes128Gcm);
  rtc::Buffer rtp_packet_buffer(packet_size);
  char* rtp_packet_data = rtp_packet_buffer.data<char>();
  memcpy(rtp_packet_data, kPcmuFrame, rtp_len);

  // First attempt will succeed.
  rtc::CopyOnWriteBuffer first_try(rtp_packet_data, rtp_len, packet_size);
  EXPECT_TRUE(srtp_transport->SendRtpPacket(&first_try, rtc::PacketOptions(),
                                            cricket::PF_SRTP_BYPASS));
  EXPECT_EQ(rtp_sink.rtp_count(), 1);

  // Second attempt will be rejected by libSRTP as a replay attack
  // (srtp_err_status_replay_fail) since the sequence number was already seen.
  // Hence the packet never reaches the sink.
  rtc::CopyOnWriteBuffer second_try(rtp_packet_data, rtp_len, packet_size);
  EXPECT_TRUE(srtp_transport->SendRtpPacket(&second_try, rtc::PacketOptions(),
                                            cricket::PF_SRTP_BYPASS));
  EXPECT_EQ(rtp_sink.rtp_count(), 1);

  // Reset the sink.
  EXPECT_TRUE(srtp_transport->UnregisterRtpDemuxerSink(&rtp_sink));
  EXPECT_TRUE(
      srtp_transport->RegisterRtpDemuxerSink(demuxer_criteria, &rtp_sink));

  // Third attempt will succeed again since libSRTP does not remember seeing
  // the sequence number after the reset.
  rtc::CopyOnWriteBuffer third_try(rtp_packet_data, rtp_len, packet_size);
  EXPECT_TRUE(srtp_transport->SendRtpPacket(&third_try, rtc::PacketOptions(),
                                            cricket::PF_SRTP_BYPASS));
  EXPECT_EQ(rtp_sink.rtp_count(), 2);
  // Clear the sink to clean up.
  srtp_transport->UnregisterRtpDemuxerSink(&rtp_sink);
}

}  // namespace webrtc
