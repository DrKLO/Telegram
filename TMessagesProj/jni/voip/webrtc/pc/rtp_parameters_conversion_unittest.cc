/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/rtp_parameters_conversion.h"

#include <cstdint>
#include <map>
#include <string>

#include "api/media_types.h"
#include "media/base/codec.h"
#include "test/gmock.h"
#include "test/gtest.h"

using ::testing::UnorderedElementsAre;

namespace webrtc {

TEST(RtpParametersConversionTest, ToCricketFeedbackParam) {
  auto result = ToCricketFeedbackParam(
      {RtcpFeedbackType::CCM, RtcpFeedbackMessageType::FIR});
  EXPECT_EQ(cricket::FeedbackParam("ccm", "fir"), result.value());

  result = ToCricketFeedbackParam(RtcpFeedback(RtcpFeedbackType::LNTF));
  EXPECT_EQ(cricket::FeedbackParam("goog-lntf"), result.value());

  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::NACK, RtcpFeedbackMessageType::GENERIC_NACK});
  EXPECT_EQ(cricket::FeedbackParam("nack"), result.value());

  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::NACK, RtcpFeedbackMessageType::PLI});
  EXPECT_EQ(cricket::FeedbackParam("nack", "pli"), result.value());

  result = ToCricketFeedbackParam(RtcpFeedback(RtcpFeedbackType::REMB));
  EXPECT_EQ(cricket::FeedbackParam("goog-remb"), result.value());

  result = ToCricketFeedbackParam(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC));
  EXPECT_EQ(cricket::FeedbackParam("transport-cc"), result.value());
}

TEST(RtpParametersConversionTest, ToCricketFeedbackParamErrors) {
  // CCM with missing or invalid message type.
  auto result = ToCricketFeedbackParam(RtcpFeedback(RtcpFeedbackType::CCM));
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::CCM, RtcpFeedbackMessageType::PLI});
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // LNTF with message type (should be left empty).
  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::LNTF, RtcpFeedbackMessageType::GENERIC_NACK});
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // NACK with missing or invalid message type.
  result = ToCricketFeedbackParam(RtcpFeedback(RtcpFeedbackType::NACK));
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::NACK, RtcpFeedbackMessageType::FIR});
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // REMB with message type (should be left empty).
  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::REMB, RtcpFeedbackMessageType::GENERIC_NACK});
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // TRANSPORT_CC with message type (should be left empty).
  result = ToCricketFeedbackParam(
      {RtcpFeedbackType::TRANSPORT_CC, RtcpFeedbackMessageType::FIR});
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());
}

TEST(RtpParametersConversionTest, ToAudioCodec) {
  RtpCodecParameters codec;
  codec.name = "AuDiO";
  codec.kind = cricket::MEDIA_TYPE_AUDIO;
  codec.payload_type = 120;
  codec.clock_rate.emplace(36000);
  codec.num_channels.emplace(6);
  codec.parameters["foo"] = "bar";
  codec.rtcp_feedback.emplace_back(RtcpFeedbackType::TRANSPORT_CC);
  auto result = ToCricketCodec(codec);
  ASSERT_TRUE(result.ok());

  EXPECT_EQ("AuDiO", result.value().name);
  EXPECT_EQ(120, result.value().id);
  EXPECT_EQ(36000, result.value().clockrate);
  EXPECT_EQ(6u, result.value().channels);
  ASSERT_EQ(1u, result.value().params.size());
  EXPECT_EQ("bar", result.value().params["foo"]);
  EXPECT_EQ(1u, result.value().feedback_params.params().size());
  EXPECT_TRUE(result.value().feedback_params.Has(
      cricket::FeedbackParam("transport-cc")));
}

TEST(RtpParametersConversionTest, ToVideoCodec) {
  RtpCodecParameters codec;
  codec.name = "coolcodec";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.payload_type = 101;
  codec.clock_rate.emplace(90000);
  codec.parameters["foo"] = "bar";
  codec.parameters["PING"] = "PONG";
  codec.rtcp_feedback.emplace_back(RtcpFeedbackType::LNTF);
  codec.rtcp_feedback.emplace_back(RtcpFeedbackType::TRANSPORT_CC);
  codec.rtcp_feedback.emplace_back(RtcpFeedbackType::NACK,
                                   RtcpFeedbackMessageType::PLI);
  auto result = ToCricketCodec(codec);
  ASSERT_TRUE(result.ok());

  EXPECT_EQ("coolcodec", result.value().name);
  EXPECT_EQ(101, result.value().id);
  EXPECT_EQ(90000, result.value().clockrate);
  ASSERT_EQ(2u, result.value().params.size());
  EXPECT_EQ("bar", result.value().params["foo"]);
  EXPECT_EQ("PONG", result.value().params["PING"]);
  EXPECT_EQ(3u, result.value().feedback_params.params().size());
  EXPECT_TRUE(
      result.value().feedback_params.Has(cricket::FeedbackParam("goog-lntf")));
  EXPECT_TRUE(result.value().feedback_params.Has(
      cricket::FeedbackParam("transport-cc")));
  EXPECT_TRUE(result.value().feedback_params.Has(
      cricket::FeedbackParam("nack", "pli")));
}

// Trying to convert to an AudioCodec if the kind is "video" should fail.
TEST(RtpParametersConversionTest, ToCricketCodecInvalidKind) {
  RtpCodecParameters audio_codec;
  audio_codec.name = "opus";
  audio_codec.kind = cricket::MEDIA_TYPE_VIDEO;
  audio_codec.payload_type = 111;
  audio_codec.clock_rate.emplace(48000);
  audio_codec.num_channels.emplace(2);

  RtpCodecParameters video_codec;
  video_codec.name = "VP8";
  video_codec.kind = cricket::MEDIA_TYPE_AUDIO;
  video_codec.payload_type = 102;
  video_codec.clock_rate.emplace(90000);

  auto audio_result = ToCricketCodec(audio_codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, audio_result.error().type());

  auto video_result = ToCricketCodec(video_codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, video_result.error().type());

  // Sanity check that if the kind is correct, the conversion succeeds.
  audio_codec.kind = cricket::MEDIA_TYPE_AUDIO;
  video_codec.kind = cricket::MEDIA_TYPE_VIDEO;
  audio_result = ToCricketCodec(audio_codec);
  EXPECT_TRUE(audio_result.ok());
  video_result = ToCricketCodec(video_codec);
  EXPECT_TRUE(video_result.ok());
}

TEST(RtpParametersConversionTest, ToAudioCodecInvalidParameters) {
  // Missing channels.
  RtpCodecParameters codec;
  codec.name = "opus";
  codec.kind = cricket::MEDIA_TYPE_AUDIO;
  codec.payload_type = 111;
  codec.clock_rate.emplace(48000);
  auto result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Negative number of channels.
  codec.num_channels.emplace(-1);
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_RANGE, result.error().type());

  // Missing clock rate.
  codec.num_channels.emplace(2);
  codec.clock_rate.reset();
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Negative clock rate.
  codec.clock_rate.emplace(-48000);
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_RANGE, result.error().type());

  // Sanity check that conversion succeeds if these errors are fixed.
  codec.clock_rate.emplace(48000);
  result = ToCricketCodec(codec);
  EXPECT_TRUE(result.ok());
}

TEST(RtpParametersConversionTest, ToVideoCodecInvalidParameters) {
  // Missing clock rate.
  RtpCodecParameters codec;
  codec.name = "VP8";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.payload_type = 102;
  auto result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Invalid clock rate.
  codec.clock_rate.emplace(48000);
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Channels set (should be unset).
  codec.clock_rate.emplace(90000);
  codec.num_channels.emplace(2);
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Sanity check that conversion succeeds if these errors are fixed.
  codec.num_channels.reset();
  result = ToCricketCodec(codec);
  EXPECT_TRUE(result.ok());
}

TEST(RtpParametersConversionTest, ToCricketCodecInvalidPayloadType) {
  RtpCodecParameters codec;
  codec.name = "VP8";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.clock_rate.emplace(90000);

  codec.payload_type = -1000;
  auto result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_RANGE, result.error().type());

  // Max payload type is 127.
  codec.payload_type = 128;
  result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_RANGE, result.error().type());

  // Sanity check that conversion succeeds with a valid payload type.
  codec.payload_type = 127;
  result = ToCricketCodec(codec);
  EXPECT_TRUE(result.ok());
}

// There are already tests for ToCricketFeedbackParam, but ensure that those
// errors are propagated from ToCricketCodec.
TEST(RtpParametersConversionTest, ToCricketCodecInvalidRtcpFeedback) {
  RtpCodecParameters codec;
  codec.name = "VP8";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.clock_rate.emplace(90000);
  codec.payload_type = 99;
  codec.rtcp_feedback.emplace_back(RtcpFeedbackType::CCM,
                                   RtcpFeedbackMessageType::PLI);

  auto result = ToCricketCodec(codec);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Sanity check that conversion succeeds without invalid feedback.
  codec.rtcp_feedback.clear();
  result = ToCricketCodec(codec);
  EXPECT_TRUE(result.ok());
}

TEST(RtpParametersConversionTest, ToCricketCodecs) {
  std::vector<RtpCodecParameters> codecs;
  RtpCodecParameters codec;
  codec.name = "VP8";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.clock_rate.emplace(90000);
  codec.payload_type = 99;
  codecs.push_back(codec);

  codec.name = "VP9";
  codec.payload_type = 100;
  codecs.push_back(codec);

  auto result = ToCricketCodecs(codecs);
  ASSERT_TRUE(result.ok());
  ASSERT_EQ(2u, result.value().size());
  EXPECT_EQ("VP8", result.value()[0].name);
  EXPECT_EQ(99, result.value()[0].id);
  EXPECT_EQ("VP9", result.value()[1].name);
  EXPECT_EQ(100, result.value()[1].id);
}

TEST(RtpParametersConversionTest, ToCricketCodecsDuplicatePayloadType) {
  std::vector<RtpCodecParameters> codecs;
  RtpCodecParameters codec;
  codec.name = "VP8";
  codec.kind = cricket::MEDIA_TYPE_VIDEO;
  codec.clock_rate.emplace(90000);
  codec.payload_type = 99;
  codecs.push_back(codec);

  codec.name = "VP9";
  codec.payload_type = 99;
  codecs.push_back(codec);

  auto result = ToCricketCodecs(codecs);
  EXPECT_EQ(RTCErrorType::INVALID_PARAMETER, result.error().type());

  // Sanity check that this succeeds without the duplicate payload type.
  codecs[1].payload_type = 120;
  result = ToCricketCodecs(codecs);
  EXPECT_TRUE(result.ok());
}

TEST(RtpParametersConversionTest, ToCricketStreamParamsVecSimple) {
  std::vector<RtpEncodingParameters> encodings;
  RtpEncodingParameters encoding;
  encoding.ssrc.emplace(0xbaadf00d);
  encodings.push_back(encoding);
  auto result = ToCricketStreamParamsVec(encodings);
  ASSERT_TRUE(result.ok());
  ASSERT_EQ(1u, result.value().size());
  EXPECT_EQ(1u, result.value()[0].ssrcs.size());
  EXPECT_EQ(0xbaadf00d, result.value()[0].first_ssrc());
}

// No encodings should be accepted; an endpoint may want to prepare a
// decoder/encoder without having something to receive/send yet.
TEST(RtpParametersConversionTest, ToCricketStreamParamsVecNoEncodings) {
  std::vector<RtpEncodingParameters> encodings;
  auto result = ToCricketStreamParamsVec(encodings);
  ASSERT_TRUE(result.ok());
  EXPECT_EQ(0u, result.value().size());
}

// An encoding without SSRCs should be accepted. This could be the case when
// SSRCs aren't signaled and payload-type based demuxing is used.
TEST(RtpParametersConversionTest, ToCricketStreamParamsVecMissingSsrcs) {
  std::vector<RtpEncodingParameters> encodings = {{}};
  // Creates RtxParameters with empty SSRC.
  auto result = ToCricketStreamParamsVec(encodings);
  ASSERT_TRUE(result.ok());
  EXPECT_EQ(0u, result.value().size());
}

// TODO(deadbeef): Update this test when we support multiple encodings.
TEST(RtpParametersConversionTest, ToCricketStreamParamsVecMultipleEncodings) {
  std::vector<RtpEncodingParameters> encodings = {{}, {}};
  auto result = ToCricketStreamParamsVec(encodings);
  EXPECT_EQ(RTCErrorType::UNSUPPORTED_PARAMETER, result.error().type());
}

TEST(RtpParametersConversionTest, ToRtcpFeedback) {
  absl::optional<RtcpFeedback> result = ToRtcpFeedback({"ccm", "fir"});
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::CCM, RtcpFeedbackMessageType::FIR),
            *result);

  result = ToRtcpFeedback(cricket::FeedbackParam("goog-lntf"));
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::LNTF), *result);

  result = ToRtcpFeedback(cricket::FeedbackParam("nack"));
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::NACK,
                         RtcpFeedbackMessageType::GENERIC_NACK),
            *result);

  result = ToRtcpFeedback({"nack", "pli"});
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::NACK, RtcpFeedbackMessageType::PLI),
            *result);

  result = ToRtcpFeedback(cricket::FeedbackParam("goog-remb"));
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::REMB), *result);

  result = ToRtcpFeedback(cricket::FeedbackParam("transport-cc"));
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC), *result);
}

TEST(RtpParametersConversionTest, ToRtcpFeedbackErrors) {
  // CCM with missing or invalid message type.
  absl::optional<RtcpFeedback> result = ToRtcpFeedback({"ccm", "pli"});
  EXPECT_FALSE(result);

  result = ToRtcpFeedback(cricket::FeedbackParam("ccm"));
  EXPECT_FALSE(result);

  // LNTF with message type (should be left empty).
  result = ToRtcpFeedback({"goog-lntf", "pli"});
  EXPECT_FALSE(result);

  // NACK with missing or invalid message type.
  result = ToRtcpFeedback({"nack", "fir"});
  EXPECT_FALSE(result);

  // REMB with message type (should be left empty).
  result = ToRtcpFeedback({"goog-remb", "pli"});
  EXPECT_FALSE(result);

  // TRANSPORT_CC with message type (should be left empty).
  result = ToRtcpFeedback({"transport-cc", "fir"});
  EXPECT_FALSE(result);

  // Unknown message type.
  result = ToRtcpFeedback(cricket::FeedbackParam("foo"));
  EXPECT_FALSE(result);
}

TEST(RtpParametersConversionTest, ToAudioRtpCodecCapability) {
  cricket::AudioCodec cricket_codec =
      cricket::CreateAudioCodec(50, "foo", 22222, 4);
  cricket_codec.params["foo"] = "bar";
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("transport-cc"));
  RtpCodecCapability codec = ToRtpCodecCapability(cricket_codec);

  EXPECT_EQ("foo", codec.name);
  EXPECT_EQ(cricket::MEDIA_TYPE_AUDIO, codec.kind);
  EXPECT_EQ(50, codec.preferred_payload_type);
  EXPECT_EQ(22222, codec.clock_rate);
  EXPECT_EQ(4, codec.num_channels);
  ASSERT_EQ(1u, codec.parameters.size());
  EXPECT_EQ("bar", codec.parameters["foo"]);
  EXPECT_EQ(1u, codec.rtcp_feedback.size());
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC),
            codec.rtcp_feedback[0]);
}

TEST(RtpParametersConversionTest, ToVideoRtpCodecCapability) {
  cricket::VideoCodec cricket_codec = cricket::CreateVideoCodec(101, "VID");
  cricket_codec.clockrate = 80000;
  cricket_codec.params["foo"] = "bar";
  cricket_codec.params["ANOTHER"] = "param";
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("transport-cc"));
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("goog-lntf"));
  cricket_codec.feedback_params.Add({"nack", "pli"});
  RtpCodecCapability codec = ToRtpCodecCapability(cricket_codec);

  EXPECT_EQ("VID", codec.name);
  EXPECT_EQ(cricket::MEDIA_TYPE_VIDEO, codec.kind);
  EXPECT_EQ(101, codec.preferred_payload_type);
  EXPECT_EQ(80000, codec.clock_rate);
  ASSERT_EQ(2u, codec.parameters.size());
  EXPECT_EQ("bar", codec.parameters["foo"]);
  EXPECT_EQ("param", codec.parameters["ANOTHER"]);
  EXPECT_EQ(3u, codec.rtcp_feedback.size());
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC),
            codec.rtcp_feedback[0]);
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::LNTF), codec.rtcp_feedback[1]);
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::NACK, RtcpFeedbackMessageType::PLI),
            codec.rtcp_feedback[2]);
}

TEST(RtpParametersConversionTest, ToRtpEncodingsWithEmptyStreamParamsVec) {
  cricket::StreamParamsVec streams;
  auto rtp_encodings = ToRtpEncodings(streams);
  ASSERT_EQ(0u, rtp_encodings.size());
}

TEST(RtpParametersConversionTest, ToRtpEncodingsWithMultipleStreamParams) {
  cricket::StreamParamsVec streams;
  cricket::StreamParams stream1;
  stream1.ssrcs.push_back(1111u);

  cricket::StreamParams stream2;
  stream2.ssrcs.push_back(2222u);

  streams.push_back(stream1);
  streams.push_back(stream2);

  auto rtp_encodings = ToRtpEncodings(streams);
  ASSERT_EQ(2u, rtp_encodings.size());
  EXPECT_EQ(1111u, rtp_encodings[0].ssrc);
  EXPECT_EQ(2222u, rtp_encodings[1].ssrc);
}

TEST(RtpParametersConversionTest, ToAudioRtpCodecParameters) {
  cricket::AudioCodec cricket_codec =
      cricket::CreateAudioCodec(50, "foo", 22222, 4);
  cricket_codec.params["foo"] = "bar";
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("transport-cc"));
  RtpCodecParameters codec = ToRtpCodecParameters(cricket_codec);

  EXPECT_EQ("foo", codec.name);
  EXPECT_EQ(cricket::MEDIA_TYPE_AUDIO, codec.kind);
  EXPECT_EQ(50, codec.payload_type);
  EXPECT_EQ(22222, codec.clock_rate);
  EXPECT_EQ(4, codec.num_channels);
  ASSERT_EQ(1u, codec.parameters.size());
  EXPECT_EQ("bar", codec.parameters["foo"]);
  EXPECT_EQ(1u, codec.rtcp_feedback.size());
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC),
            codec.rtcp_feedback[0]);
}

TEST(RtpParametersConversionTest, ToVideoRtpCodecParameters) {
  cricket::VideoCodec cricket_codec = cricket::CreateVideoCodec(101, "VID");
  cricket_codec.clockrate = 80000;
  cricket_codec.params["foo"] = "bar";
  cricket_codec.params["ANOTHER"] = "param";
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("transport-cc"));
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("goog-lntf"));
  cricket_codec.feedback_params.Add({"nack", "pli"});
  RtpCodecParameters codec = ToRtpCodecParameters(cricket_codec);

  EXPECT_EQ("VID", codec.name);
  EXPECT_EQ(cricket::MEDIA_TYPE_VIDEO, codec.kind);
  EXPECT_EQ(101, codec.payload_type);
  EXPECT_EQ(80000, codec.clock_rate);
  ASSERT_EQ(2u, codec.parameters.size());
  EXPECT_EQ("bar", codec.parameters["foo"]);
  EXPECT_EQ("param", codec.parameters["ANOTHER"]);
  EXPECT_EQ(3u, codec.rtcp_feedback.size());
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC),
            codec.rtcp_feedback[0]);
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::LNTF), codec.rtcp_feedback[1]);
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::NACK, RtcpFeedbackMessageType::PLI),
            codec.rtcp_feedback[2]);
}

// An unknown feedback param should just be ignored.
TEST(RtpParametersConversionTest, ToRtpCodecCapabilityUnknownFeedbackParam) {
  cricket::AudioCodec cricket_codec =
      cricket::CreateAudioCodec(50, "foo", 22222, 4);
  cricket_codec.params["foo"] = "bar";
  cricket_codec.feedback_params.Add({"unknown", "param"});
  cricket_codec.feedback_params.Add(cricket::FeedbackParam("transport-cc"));
  RtpCodecCapability codec = ToRtpCodecCapability(cricket_codec);

  ASSERT_EQ(1u, codec.rtcp_feedback.size());
  EXPECT_EQ(RtcpFeedback(RtcpFeedbackType::TRANSPORT_CC),
            codec.rtcp_feedback[0]);
}

// Most of ToRtpCapabilities is tested by ToRtpCodecCapability, but we need to
// test that the result of ToRtpCodecCapability ends up in the result, and that
// the "fec" list is assembled correctly.
TEST(RtpParametersConversionTest, ToRtpCapabilities) {
  cricket::VideoCodec vp8 = cricket::CreateVideoCodec(101, "VP8");
  vp8.clockrate = 90000;

  cricket::VideoCodec red = cricket::CreateVideoCodec(102, "red");
  red.clockrate = 90000;

  cricket::VideoCodec ulpfec = cricket::CreateVideoCodec(103, "ulpfec");
  ulpfec.clockrate = 90000;

  cricket::VideoCodec flexfec = cricket::CreateVideoCodec(102, "flexfec-03");
  flexfec.clockrate = 90000;

  cricket::VideoCodec rtx = cricket::CreateVideoRtxCodec(014, 101);

  cricket::VideoCodec rtx2 = cricket::CreateVideoRtxCodec(105, 109);

  RtpCapabilities capabilities =
      ToRtpCapabilities({vp8, ulpfec, rtx, rtx2}, {{"uri", 1}, {"uri2", 3}});
  ASSERT_EQ(3u, capabilities.codecs.size());
  EXPECT_EQ("VP8", capabilities.codecs[0].name);
  EXPECT_EQ("ulpfec", capabilities.codecs[1].name);
  EXPECT_EQ("rtx", capabilities.codecs[2].name);
  EXPECT_EQ(0u, capabilities.codecs[2].parameters.size());
  ASSERT_EQ(2u, capabilities.header_extensions.size());
  EXPECT_EQ("uri", capabilities.header_extensions[0].uri);
  EXPECT_EQ(1, capabilities.header_extensions[0].preferred_id);
  EXPECT_EQ("uri2", capabilities.header_extensions[1].uri);
  EXPECT_EQ(3, capabilities.header_extensions[1].preferred_id);
  EXPECT_EQ(0u, capabilities.fec.size());

  capabilities = ToRtpCapabilities({vp8, red, ulpfec, rtx},
                                   cricket::RtpHeaderExtensions());
  EXPECT_EQ(4u, capabilities.codecs.size());
  EXPECT_THAT(
      capabilities.fec,
      UnorderedElementsAre(FecMechanism::RED, FecMechanism::RED_AND_ULPFEC));

  capabilities =
      ToRtpCapabilities({vp8, red, flexfec}, cricket::RtpHeaderExtensions());
  EXPECT_EQ(3u, capabilities.codecs.size());
  EXPECT_THAT(capabilities.fec,
              UnorderedElementsAre(FecMechanism::RED, FecMechanism::FLEXFEC));
}

TEST(RtpParametersConversionTest, ToRtpParameters) {
  cricket::VideoCodec vp8 = cricket::CreateVideoCodec(101, "VP8");
  vp8.clockrate = 90000;

  cricket::VideoCodec red = cricket::CreateVideoCodec(102, "red");
  red.clockrate = 90000;

  cricket::VideoCodec ulpfec = cricket::CreateVideoCodec(103, "ulpfec");
  ulpfec.clockrate = 90000;

  cricket::StreamParamsVec streams;
  cricket::StreamParams stream;
  stream.ssrcs.push_back(1234u);
  streams.push_back(stream);

  RtpParameters rtp_parameters =
      ToRtpParameters({vp8, red, ulpfec}, {{"uri", 1}, {"uri2", 3}}, streams);
  ASSERT_EQ(3u, rtp_parameters.codecs.size());
  EXPECT_EQ("VP8", rtp_parameters.codecs[0].name);
  EXPECT_EQ("red", rtp_parameters.codecs[1].name);
  EXPECT_EQ("ulpfec", rtp_parameters.codecs[2].name);
  ASSERT_EQ(2u, rtp_parameters.header_extensions.size());
  EXPECT_EQ("uri", rtp_parameters.header_extensions[0].uri);
  EXPECT_EQ(1, rtp_parameters.header_extensions[0].id);
  EXPECT_EQ("uri2", rtp_parameters.header_extensions[1].uri);
  EXPECT_EQ(3, rtp_parameters.header_extensions[1].id);
  ASSERT_EQ(1u, rtp_parameters.encodings.size());
  EXPECT_EQ(1234u, rtp_parameters.encodings[0].ssrc);
}

}  // namespace webrtc
