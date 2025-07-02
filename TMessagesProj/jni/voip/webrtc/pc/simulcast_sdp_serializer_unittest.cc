/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "pc/simulcast_sdp_serializer.h"

#include <stddef.h>

#include <map>
#include <string>
#include <utility>
#include <vector>

#include "test/gtest.h"

using cricket::RidDescription;
using cricket::RidDirection;
using cricket::SimulcastDescription;
using cricket::SimulcastLayer;
using cricket::SimulcastLayerList;
using ::testing::TestWithParam;
using ::testing::ValuesIn;

namespace webrtc {

namespace {
// Checks that two vectors have the same objects in the same order.
template <typename TElement>
void ExpectEqual(const std::vector<TElement>& expected,
                 const std::vector<TElement>& actual) {
  ASSERT_EQ(expected.size(), actual.size());
  for (size_t i = 0; i < expected.size(); i++) {
    EXPECT_EQ(expected[i], actual[i]) << "Vectors differ at element " << i;
  }
}

// Template specialization for vectors of SimulcastLayer objects.
template <>
void ExpectEqual(const std::vector<SimulcastLayer>& expected,
                 const std::vector<SimulcastLayer>& actual) {
  EXPECT_EQ(expected.size(), actual.size());
  for (size_t i = 0; i < expected.size(); i++) {
    EXPECT_EQ(expected[i].rid, actual[i].rid);
    EXPECT_EQ(expected[i].is_paused, actual[i].is_paused);
  }
}

// Checks that two maps have the same key-value pairs.
// Even though a map is technically ordered, the order semantics are not
// tested because having the same key-set in both maps implies that they
// are ordered the same because the template enforces that they have the
// same Key-Comparer type.
template <typename TKey, typename TValue>
void ExpectEqual(const std::map<TKey, TValue>& expected,
                 const std::map<TKey, TValue>& actual) {
  typedef typename std::map<TKey, TValue>::const_iterator const_iterator;
  ASSERT_EQ(expected.size(), actual.size());
  // Maps have unique keys, so if size is equal, it is enough to check
  // that all the keys (and values) from one map exist in the other.
  for (const auto& pair : expected) {
    const_iterator iter = actual.find(pair.first);
    EXPECT_NE(iter, actual.end()) << "Key: " << pair.first << " not found";
    EXPECT_EQ(pair.second, iter->second);
  }
}

// Checks that the two SimulcastLayerLists are equal.
void ExpectEqual(const SimulcastLayerList& expected,
                 const SimulcastLayerList& actual) {
  EXPECT_EQ(expected.size(), actual.size());
  for (size_t i = 0; i < expected.size(); i++) {
    ExpectEqual(expected[i], actual[i]);
  }
}

// Checks that the two SimulcastDescriptions are equal.
void ExpectEqual(const SimulcastDescription& expected,
                 const SimulcastDescription& actual) {
  ExpectEqual(expected.send_layers(), actual.send_layers());
  ExpectEqual(expected.receive_layers(), actual.receive_layers());
}

// Checks that the two RidDescriptions are equal.
void ExpectEqual(const RidDescription& expected, const RidDescription& actual) {
  EXPECT_EQ(expected.rid, actual.rid);
  EXPECT_EQ(expected.direction, actual.direction);
  ExpectEqual(expected.payload_types, actual.payload_types);
  ExpectEqual(expected.restrictions, actual.restrictions);
}
}  // namespace

class SimulcastSdpSerializerTest : public TestWithParam<const char*> {
 public:
  // Runs a test for deserializing Simulcast.
  // `str` - The serialized Simulcast to parse.
  // `expected` - The expected output Simulcast to compare to.
  void TestDeserialization(const std::string& str,
                           const SimulcastDescription& expected) const {
    SimulcastSdpSerializer deserializer;
    auto result = deserializer.DeserializeSimulcastDescription(str);
    EXPECT_TRUE(result.ok());
    ExpectEqual(expected, result.value());
  }

  // Runs a test for serializing Simulcast.
  // `simulcast` - The Simulcast to serialize.
  // `expected` - The expected output string to compare to.
  void TestSerialization(const SimulcastDescription& simulcast,
                         const std::string& expected) const {
    SimulcastSdpSerializer serializer;
    auto result = serializer.SerializeSimulcastDescription(simulcast);
    EXPECT_EQ(expected, result);
  }
};

// Test Cases

// Test simple deserialization with no alternative streams.
TEST_F(SimulcastSdpSerializerTest, Deserialize_SimpleCaseNoAlternatives) {
  std::string simulcast_str = "send 1;2 recv 3;4";
  SimulcastDescription expected;
  expected.send_layers().AddLayer(SimulcastLayer("1", false));
  expected.send_layers().AddLayer(SimulcastLayer("2", false));
  expected.receive_layers().AddLayer(SimulcastLayer("3", false));
  expected.receive_layers().AddLayer(SimulcastLayer("4", false));
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization with alternative streams.
TEST_F(SimulcastSdpSerializerTest, Deserialize_SimpleCaseWithAlternatives) {
  std::string simulcast_str = "send 1,5;2,6 recv 3,7;4,8";
  SimulcastDescription expected;
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("1", false), SimulcastLayer("5", false)});
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("6", false)});
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("4", false), SimulcastLayer("8", false)});
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization when only some streams have alternatives.
TEST_F(SimulcastSdpSerializerTest, Deserialize_WithSomeAlternatives) {
  std::string simulcast_str = "send 1;2,6 recv 3,7;4";
  SimulcastDescription expected;
  expected.send_layers().AddLayer(SimulcastLayer("1", false));
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("6", false)});
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.receive_layers().AddLayer(SimulcastLayer("4", false));
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization when only send streams are specified.
TEST_F(SimulcastSdpSerializerTest, Deserialize_OnlySendStreams) {
  std::string simulcast_str = "send 1;2,6;3,7;4";
  SimulcastDescription expected;
  expected.send_layers().AddLayer(SimulcastLayer("1", false));
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("6", false)});
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.send_layers().AddLayer(SimulcastLayer("4", false));
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization when only receive streams are specified.
TEST_F(SimulcastSdpSerializerTest, Deserialize_OnlyReceiveStreams) {
  std::string simulcast_str = "recv 1;2,6;3,7;4";
  SimulcastDescription expected;
  expected.receive_layers().AddLayer(SimulcastLayer("1", false));
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("6", false)});
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.receive_layers().AddLayer(SimulcastLayer("4", false));
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization with receive streams before send streams.
TEST_F(SimulcastSdpSerializerTest, Deserialize_SendReceiveReversed) {
  std::string simulcast_str = "recv 1;2,6 send 3,7;4";
  SimulcastDescription expected;
  expected.receive_layers().AddLayer(SimulcastLayer("1", false));
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("6", false)});
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.send_layers().AddLayer(SimulcastLayer("4", false));
  TestDeserialization(simulcast_str, expected);
}

// Test simulcast deserialization with some streams set to paused state.
TEST_F(SimulcastSdpSerializerTest, Deserialize_PausedStreams) {
  std::string simulcast_str = "recv 1;~2,6 send 3,7;~4";
  SimulcastDescription expected;
  expected.receive_layers().AddLayer(SimulcastLayer("1", false));
  expected.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", true), SimulcastLayer("6", false)});
  expected.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("3", false), SimulcastLayer("7", false)});
  expected.send_layers().AddLayer(SimulcastLayer("4", true));
  TestDeserialization(simulcast_str, expected);
}

// Parameterized negative test case for deserialization with invalid inputs.
TEST_P(SimulcastSdpSerializerTest, SimulcastDeserializationFailed) {
  SimulcastSdpSerializer deserializer;
  auto result = deserializer.DeserializeSimulcastDescription(GetParam());
  EXPECT_FALSE(result.ok());
}

// The malformed Simulcast inputs to use in the negative test case.
const char* kSimulcastMalformedStrings[] = {
    "send ",
    "recv ",
    "recv 1 send",
    "receive 1",
    "recv 1;~2,6 recv 3,7;~4",
    "send 1;~2,6 send 3,7;~4",
    "send ~;~2,6",
    "send 1; ;~2,6",
    "send 1,;~2,6",
    "recv 1 send 2 3",
    "",
};

INSTANTIATE_TEST_SUITE_P(SimulcastDeserializationErrors,
                         SimulcastSdpSerializerTest,
                         ValuesIn(kSimulcastMalformedStrings));

// Test a simple serialization scenario.
TEST_F(SimulcastSdpSerializerTest, Serialize_SimpleCase) {
  SimulcastDescription simulcast;
  simulcast.send_layers().AddLayer(SimulcastLayer("1", false));
  simulcast.receive_layers().AddLayer(SimulcastLayer("2", false));
  TestSerialization(simulcast, "send 1 recv 2");
}

// Test serialization with only send streams.
TEST_F(SimulcastSdpSerializerTest, Serialize_OnlySend) {
  SimulcastDescription simulcast;
  simulcast.send_layers().AddLayer(SimulcastLayer("1", false));
  simulcast.send_layers().AddLayer(SimulcastLayer("2", false));
  TestSerialization(simulcast, "send 1;2");
}

// Test serialization with only receive streams
TEST_F(SimulcastSdpSerializerTest, Serialize_OnlyReceive) {
  SimulcastDescription simulcast;
  simulcast.receive_layers().AddLayer(SimulcastLayer("1", false));
  simulcast.receive_layers().AddLayer(SimulcastLayer("2", false));
  TestSerialization(simulcast, "recv 1;2");
}

// Test a complex serialization with multiple streams, alternatives and states.
TEST_F(SimulcastSdpSerializerTest, Serialize_ComplexSerialization) {
  SimulcastDescription simulcast;
  simulcast.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("2", false), SimulcastLayer("1", true)});
  simulcast.send_layers().AddLayerWithAlternatives(
      {SimulcastLayer("4", false), SimulcastLayer("3", false)});

  simulcast.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("6", false), SimulcastLayer("7", false)});
  simulcast.receive_layers().AddLayer(SimulcastLayer("8", true));
  simulcast.receive_layers().AddLayerWithAlternatives(
      {SimulcastLayer("9", false), SimulcastLayer("10", true),
       SimulcastLayer("11", false)});
  TestSerialization(simulcast, "send 2,~1;4,3 recv 6,7;~8;9,~10,11");
}

class RidDescriptionSdpSerializerTest : public TestWithParam<const char*> {
 public:
  // Runs a test for deserializing Rid Descriptions.
  // `str` - The serialized Rid Description to parse.
  // `expected` - The expected output RidDescription to compare to.
  void TestDeserialization(const std::string& str,
                           const RidDescription& expected) const {
    SimulcastSdpSerializer deserializer;
    auto result = deserializer.DeserializeRidDescription(str);
    EXPECT_TRUE(result.ok());
    ExpectEqual(expected, result.value());
  }

  // Runs a test for serializing RidDescriptions.
  // `rid_description` - The RidDescription to serialize.
  // `expected` - The expected output string to compare to.
  void TestSerialization(const RidDescription& rid_description,
                         const std::string& expected) const {
    SimulcastSdpSerializer serializer;
    auto result = serializer.SerializeRidDescription(rid_description);
    EXPECT_EQ(expected, result);
  }
};

// Test serialization for RidDescription that only specifies send.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_OnlyDirectionSend) {
  RidDescription rid_description("1", RidDirection::kSend);
  TestSerialization(rid_description, "1 send");
}

// Test serialization for RidDescription that only specifies receive.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_OnlyDirectionReceive) {
  RidDescription rid_description("2", RidDirection::kReceive);
  TestSerialization(rid_description, "2 recv");
}

// Test serialization for RidDescription with format list.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_FormatList) {
  RidDescription rid_description("3", RidDirection::kSend);
  rid_description.payload_types = {102, 101};
  TestSerialization(rid_description, "3 send pt=102,101");
}

// Test serialization for RidDescription with format list.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_FormatListSingleFormat) {
  RidDescription rid_description("4", RidDirection::kReceive);
  rid_description.payload_types = {100};
  TestSerialization(rid_description, "4 recv pt=100");
}

// Test serialization for RidDescription with restriction list.
// Note: restriction list will be sorted because it is stored in a map.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_AttributeList) {
  RidDescription rid_description("5", RidDirection::kSend);
  rid_description.restrictions["max-width"] = "1280";
  rid_description.restrictions["max-height"] = "720";
  TestSerialization(rid_description, "5 send max-height=720;max-width=1280");
}

// Test serialization for RidDescription with format list and attribute list.
// Note: restriction list will be sorted because it is stored in a map.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_FormatAndAttributeList) {
  RidDescription rid_description("6", RidDirection::kSend);
  rid_description.payload_types = {103, 104};
  rid_description.restrictions["max-mbps"] = "108000";
  rid_description.restrictions["max-br"] = "64000";
  TestSerialization(rid_description,
                    "6 send pt=103,104;max-br=64000;max-mbps=108000");
}

// Test serialization for attribute list that has key with no value.
// Note: restriction list will be sorted because it is stored in a map.
TEST_F(RidDescriptionSdpSerializerTest, Serialize_RestrictionWithoutValue) {
  RidDescription rid_description("7", RidDirection::kReceive);
  rid_description.payload_types = {103};
  rid_description.restrictions["max-width"] = "1280";
  rid_description.restrictions["max-height"] = "720";
  rid_description.restrictions["max-myval"] = "";
  TestSerialization(rid_description,
                    "7 recv pt=103;max-height=720;max-myval;max-width=1280");
}

// Test simulcast deserialization with simple send stream.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_SimpleSendCase) {
  RidDescription rid_description("1", RidDirection::kSend);
  TestDeserialization("1 send", rid_description);
}

// Test simulcast deserialization with simple receive stream.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_SimpleReceiveCase) {
  RidDescription rid_description("2", RidDirection::kReceive);
  TestDeserialization("2 recv", rid_description);
}

// Test simulcast deserialization with single format.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithFormat) {
  RidDescription rid_description("3", RidDirection::kSend);
  rid_description.payload_types = {101};
  TestDeserialization("3 send pt=101", rid_description);
}

// Test simulcast deserialization with multiple formats.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithMultipleFormats) {
  RidDescription rid_description("4", RidDirection::kSend);
  rid_description.payload_types = {103, 104, 101, 102};
  TestDeserialization("4 send pt=103,104,101,102", rid_description);
}

// Test simulcast deserialization with restriction.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithRestriction) {
  RidDescription rid_description("5", RidDirection::kReceive);
  rid_description.restrictions["max-height"] = "720";
  TestDeserialization("5 recv max-height=720", rid_description);
}

// Test simulcast deserialization with multiple restrictions.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithMultipleRestrictions) {
  RidDescription rid_description("6", RidDirection::kReceive);
  rid_description.restrictions["max-height"] = "720";
  rid_description.restrictions["max-width"] = "1920";
  rid_description.restrictions["max-fr"] = "60";
  rid_description.restrictions["max-bps"] = "14000";
  TestDeserialization(
      "6 recv max-height=720;max-width=1920;max-bps=14000;max-fr=60",
      rid_description);
}

// Test simulcast deserialization with custom (non-standard) restriction.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithCustomRestrictions) {
  RidDescription rid_description("7", RidDirection::kSend);
  rid_description.restrictions["foo"] = "bar";
  rid_description.restrictions["max-height"] = "720";
  TestDeserialization("7 send max-height=720;foo=bar", rid_description);
}

// Test simulcast deserialization with multiple formats and restrictions.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_WithFormatAndRestrictions) {
  RidDescription rid_description("8", RidDirection::kSend);
  rid_description.payload_types = {104, 103};
  rid_description.restrictions["max-height"] = "720";
  rid_description.restrictions["max-width"] = "1920";
  TestDeserialization("8 send pt=104,103;max-height=720;max-width=1920",
                      rid_description);
}

// Test simulcast deserialization with restriction that has no value.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_RestrictionHasNoValue) {
  RidDescription rid_description("9", RidDirection::kReceive);
  rid_description.payload_types = {104};
  rid_description.restrictions["max-height"];
  rid_description.restrictions["max-width"] = "1920";
  TestDeserialization("9 recv pt=104;max-height;max-width=1920",
                      rid_description);
}

// Add this test to explicitly indicate that this is not an error.
// The following string "1 send recv" looks malformed because it specifies
// two directions, but in fact, the recv can be interpreted as a parameter
// without a value. While such a use case is dubious, the input string is
// not malformed.
TEST_F(RidDescriptionSdpSerializerTest, Deserialize_AmbiguousCase) {
  RidDescription rid_description("1", RidDirection::kSend);
  rid_description.restrictions["recv"];  // No value.
  TestDeserialization("1 send recv", rid_description);
}

// Parameterized negative test case for deserialization with invalid inputs.
TEST_P(RidDescriptionSdpSerializerTest, RidDescriptionDeserializationFailed) {
  SimulcastSdpSerializer deserializer;
  auto result = deserializer.DeserializeRidDescription(GetParam());
  EXPECT_FALSE(result.ok());
}

// The malformed Rid Description inputs to use in the negative test case.
const char* kRidDescriptionMalformedStrings[] = {
    "1",
    "recv",
    "send",
    "recv 1",
    "send 1",
    "1 receive",
    "one direction",
    "1 send pt=1 max-width=720",  // The ' ' should be ';' in restriction list.
    "1 recv ;",
    "1 recv =",
    "1 recv a=b=c",
    "1 send max-width=720;pt=101",  // pt= should appear first.
    "1 send pt=101;pt=102",
    "1 send pt=101,101",
    "1 recv max-width=720;max-width=720",
    "1 send pt=",
    "1 send pt=abc",
    "1 recv ;;",
    "~1 recv",
    "1$2 send",
    "1=2 send",
    "1* send",
};

INSTANTIATE_TEST_SUITE_P(RidDescriptionDeserializationErrors,
                         RidDescriptionSdpSerializerTest,
                         ValuesIn(kRidDescriptionMalformedStrings));

}  // namespace webrtc
