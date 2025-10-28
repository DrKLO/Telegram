/*
 *  Copyright 2014 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "api/legacy_stats_types.h"

#include <string.h>

#include <utility>

#include "absl/algorithm/container.h"
#include "api/make_ref_counted.h"
#include "rtc_base/checks.h"
#include "rtc_base/string_encode.h"

// TODO(tommi): Could we have a static map of value name -> expected type
// and use this to RTC_DCHECK on correct usage (somewhat strongly typed values)?
// Alternatively, we could define the names+type in a separate document and
// generate strongly typed inline C++ code that forces the correct type to be
// used for a given name at compile time.

namespace webrtc {
namespace {

// The id of StatsReport of type kStatsReportTypeBwe.
const char kStatsReportVideoBweId[] = "bweforvideo";

// NOTE: These names need to be consistent with an external
// specification (W3C Stats Identifiers).
const char* InternalTypeToString(StatsReport::StatsType type) {
  switch (type) {
    case StatsReport::kStatsReportTypeSession:
      return "googLibjingleSession";
    case StatsReport::kStatsReportTypeBwe:
      return "VideoBwe";
    case StatsReport::kStatsReportTypeRemoteSsrc:
      return "remoteSsrc";
    case StatsReport::kStatsReportTypeSsrc:
      return "ssrc";
    case StatsReport::kStatsReportTypeTrack:
      return "googTrack";
    case StatsReport::kStatsReportTypeIceLocalCandidate:
      return "localcandidate";
    case StatsReport::kStatsReportTypeIceRemoteCandidate:
      return "remotecandidate";
    case StatsReport::kStatsReportTypeTransport:
      return "transport";
    case StatsReport::kStatsReportTypeComponent:
      return "googComponent";
    case StatsReport::kStatsReportTypeCandidatePair:
      return "googCandidatePair";
    case StatsReport::kStatsReportTypeCertificate:
      return "googCertificate";
    case StatsReport::kStatsReportTypeDataChannel:
      return "datachannel";
  }
  RTC_DCHECK_NOTREACHED();
  return nullptr;
}

class BandwidthEstimationId : public StatsReport::IdBase {
 public:
  BandwidthEstimationId()
      : StatsReport::IdBase(StatsReport::kStatsReportTypeBwe) {}
  std::string ToString() const override { return kStatsReportVideoBweId; }
};

class TypedId : public StatsReport::IdBase {
 public:
  TypedId(StatsReport::StatsType type, const std::string& id)
      : StatsReport::IdBase(type), id_(id) {}

  bool Equals(const IdBase& other) const override {
    return IdBase::Equals(other) &&
           static_cast<const TypedId&>(other).id_ == id_;
  }

  std::string ToString() const override {
    return std::string(InternalTypeToString(type_)) + kSeparator + id_;
  }

 protected:
  const std::string id_;
};

class TypedIntId : public StatsReport::IdBase {
 public:
  TypedIntId(StatsReport::StatsType type, int id)
      : StatsReport::IdBase(type), id_(id) {}

  bool Equals(const IdBase& other) const override {
    return IdBase::Equals(other) &&
           static_cast<const TypedIntId&>(other).id_ == id_;
  }

  std::string ToString() const override {
    return std::string(InternalTypeToString(type_)) + kSeparator +
           rtc::ToString(id_);
  }

 protected:
  const int id_;
};

class IdWithDirection : public TypedId {
 public:
  IdWithDirection(StatsReport::StatsType type,
                  const std::string& id,
                  StatsReport::Direction direction)
      : TypedId(type, id), direction_(direction) {}

  bool Equals(const IdBase& other) const override {
    return TypedId::Equals(other) &&
           static_cast<const IdWithDirection&>(other).direction_ == direction_;
  }

  std::string ToString() const override {
    std::string ret(TypedId::ToString());
    ret += kSeparator;
    ret += direction_ == StatsReport::kSend ? "send" : "recv";
    return ret;
  }

 private:
  const StatsReport::Direction direction_;
};

class CandidateId : public TypedId {
 public:
  CandidateId(bool local, const std::string& id)
      : TypedId(local ? StatsReport::kStatsReportTypeIceLocalCandidate
                      : StatsReport::kStatsReportTypeIceRemoteCandidate,
                id) {}

  std::string ToString() const override { return "Cand-" + id_; }
};

class ComponentId : public StatsReport::IdBase {
 public:
  ComponentId(const std::string& content_name, int component)
      : ComponentId(StatsReport::kStatsReportTypeComponent,
                    content_name,
                    component) {}

  bool Equals(const IdBase& other) const override {
    return IdBase::Equals(other) &&
           static_cast<const ComponentId&>(other).component_ == component_ &&
           static_cast<const ComponentId&>(other).content_name_ ==
               content_name_;
  }

  std::string ToString() const override { return ToString("Channel-"); }

 protected:
  ComponentId(StatsReport::StatsType type,
              const std::string& content_name,
              int component)
      : IdBase(type), content_name_(content_name), component_(component) {}

  std::string ToString(const char* prefix) const {
    std::string ret(prefix);
    ret += content_name_;
    ret += '-';
    ret += rtc::ToString(component_);
    return ret;
  }

 private:
  const std::string content_name_;
  const int component_;
};

class CandidatePairId : public ComponentId {
 public:
  CandidatePairId(const std::string& content_name, int component, int index)
      : ComponentId(StatsReport::kStatsReportTypeCandidatePair,
                    content_name,
                    component),
        index_(index) {}

  bool Equals(const IdBase& other) const override {
    return ComponentId::Equals(other) &&
           static_cast<const CandidatePairId&>(other).index_ == index_;
  }

  std::string ToString() const override {
    std::string ret(ComponentId::ToString("Conn-"));
    ret += '-';
    ret += rtc::ToString(index_);
    return ret;
  }

 private:
  const int index_;
};

}  // namespace

StatsReport::IdBase::IdBase(StatsType type) : type_(type) {}
StatsReport::IdBase::~IdBase() {}

StatsReport::StatsType StatsReport::IdBase::type() const {
  return type_;
}

bool StatsReport::IdBase::Equals(const IdBase& other) const {
  return other.type_ == type_;
}

StatsReport::Value::Value(StatsValueName name, int64_t value, Type int_type)
    : name(name), type_(int_type) {
  RTC_DCHECK(type_ == kInt || type_ == kInt64);
  type_ == kInt ? value_.int_ = static_cast<int>(value) : value_.int64_ = value;
}

StatsReport::Value::Value(StatsValueName name, float f)
    : name(name), type_(kFloat) {
  value_.float_ = f;
}

StatsReport::Value::Value(StatsValueName name, const std::string& value)
    : name(name), type_(kString) {
  value_.string_ = new std::string(value);
}

StatsReport::Value::Value(StatsValueName name, const char* value)
    : name(name), type_(kStaticString) {
  value_.static_string_ = value;
}

StatsReport::Value::Value(StatsValueName name, bool b)
    : name(name), type_(kBool) {
  value_.bool_ = b;
}

StatsReport::Value::Value(StatsValueName name, const Id& value)
    : name(name), type_(kId) {
  value_.id_ = new Id(value);
}

StatsReport::Value::~Value() {
  switch (type_) {
    case kInt:
    case kInt64:
    case kFloat:
    case kBool:
    case kStaticString:
      break;
    case kString:
      delete value_.string_;
      break;
    case kId:
      delete value_.id_;
      break;
  }
}

bool StatsReport::Value::Equals(const Value& other) const {
  if (name != other.name)
    return false;

  // There's a 1:1 relation between a name and a type, so we don't have to
  // check that.
  RTC_DCHECK_EQ(type_, other.type_);

  switch (type_) {
    case kInt:
      return value_.int_ == other.value_.int_;
    case kInt64:
      return value_.int64_ == other.value_.int64_;
    case kFloat:
      return value_.float_ == other.value_.float_;
    case kStaticString: {
#if RTC_DCHECK_IS_ON
      if (value_.static_string_ != other.value_.static_string_) {
        RTC_DCHECK(strcmp(value_.static_string_, other.value_.static_string_) !=
                   0)
            << "Duplicate global?";
      }
#endif
      return value_.static_string_ == other.value_.static_string_;
    }
    case kString:
      return *value_.string_ == *other.value_.string_;
    case kBool:
      return value_.bool_ == other.value_.bool_;
    case kId:
      return (*value_.id_)->Equals(*other.value_.id_);
  }
  RTC_DCHECK_NOTREACHED();
  return false;
}

bool StatsReport::Value::operator==(const std::string& value) const {
  return (type_ == kString && value_.string_->compare(value) == 0) ||
         (type_ == kStaticString && value.compare(value_.static_string_) == 0);
}

bool StatsReport::Value::operator==(const char* value) const {
  if (type_ == kString)
    return value_.string_->compare(value) == 0;
  if (type_ != kStaticString)
    return false;
#if RTC_DCHECK_IS_ON
  if (value_.static_string_ != value)
    RTC_DCHECK(strcmp(value_.static_string_, value) != 0)
        << "Duplicate global?";
#endif
  return value == value_.static_string_;
}

bool StatsReport::Value::operator==(int64_t value) const {
  return type_ == kInt ? value_.int_ == static_cast<int>(value)
                       : (type_ == kInt64 ? value_.int64_ == value : false);
}

bool StatsReport::Value::operator==(bool value) const {
  return type_ == kBool && value_.bool_ == value;
}

bool StatsReport::Value::operator==(float value) const {
  return type_ == kFloat && value_.float_ == value;
}

bool StatsReport::Value::operator==(const Id& value) const {
  return type_ == kId && (*value_.id_)->Equals(value);
}

int StatsReport::Value::int_val() const {
  RTC_DCHECK_EQ(type_, kInt);
  return value_.int_;
}

int64_t StatsReport::Value::int64_val() const {
  RTC_DCHECK_EQ(type_, kInt64);
  return value_.int64_;
}

float StatsReport::Value::float_val() const {
  RTC_DCHECK_EQ(type_, kFloat);
  return value_.float_;
}

const char* StatsReport::Value::static_string_val() const {
  RTC_DCHECK_EQ(type_, kStaticString);
  return value_.static_string_;
}

const std::string& StatsReport::Value::string_val() const {
  RTC_DCHECK_EQ(type_, kString);
  return *value_.string_;
}

bool StatsReport::Value::bool_val() const {
  RTC_DCHECK_EQ(type_, kBool);
  return value_.bool_;
}

const StatsReport::Id& StatsReport::Value::id_val() const {
  RTC_DCHECK_EQ(type_, kId);
  return *value_.id_;
}

const char* StatsReport::Value::display_name() const {
  switch (name) {
    case kStatsValueNameAecDivergentFilterFraction:
      return "aecDivergentFilterFraction";
    case kStatsValueNameAudioOutputLevel:
      return "audioOutputLevel";
    case kStatsValueNameAudioInputLevel:
      return "audioInputLevel";
    case kStatsValueNameBytesSent:
      return "bytesSent";
    case kStatsValueNameConcealedSamples:
      return "concealedSamples";
    case kStatsValueNameConcealmentEvents:
      return "concealmentEvents";
    case kStatsValueNamePacketsSent:
      return "packetsSent";
    case kStatsValueNameBytesReceived:
      return "bytesReceived";
    case kStatsValueNameLabel:
      return "label";
    case kStatsValueNamePacketsReceived:
      return "packetsReceived";
    case kStatsValueNamePacketsLost:
      return "packetsLost";
    case kStatsValueNameProtocol:
      return "protocol";
    case kStatsValueNameTotalSamplesReceived:
      return "totalSamplesReceived";
    case kStatsValueNameTransportId:
      return "transportId";
    case kStatsValueNameSelectedCandidatePairId:
      return "selectedCandidatePairId";
    case kStatsValueNameSsrc:
      return "ssrc";
    case kStatsValueNameState:
      return "state";
    case kStatsValueNameDataChannelId:
      return "datachannelid";
    case kStatsValueNameFramesDecoded:
      return "framesDecoded";
    case kStatsValueNameFramesEncoded:
      return "framesEncoded";
    case kStatsValueNameJitterBufferDelay:
      return "jitterBufferDelay";
    case kStatsValueNameCodecImplementationName:
      return "codecImplementationName";
    case kStatsValueNameMediaType:
      return "mediaType";
    case kStatsValueNameQpSum:
      return "qpSum";
    // 'goog' prefixed constants.
    case kStatsValueNameAccelerateRate:
      return "googAccelerateRate";
    case kStatsValueNameActiveConnection:
      return "googActiveConnection";
    case kStatsValueNameActualEncBitrate:
      return "googActualEncBitrate";
    case kStatsValueNameAvailableReceiveBandwidth:
      return "googAvailableReceiveBandwidth";
    case kStatsValueNameAvailableSendBandwidth:
      return "googAvailableSendBandwidth";
    case kStatsValueNameAvgEncodeMs:
      return "googAvgEncodeMs";
    case kStatsValueNameBucketDelay:
      return "googBucketDelay";
    case kStatsValueNameBandwidthLimitedResolution:
      return "googBandwidthLimitedResolution";
    // STUN ping related attributes.
    //
    // TODO(zhihuang) Rename these stats to follow the standards.
    // Connectivity checks.
    case kStatsValueNameSentPingRequestsTotal:
      return "requestsSent";
    case kStatsValueNameSentPingRequestsBeforeFirstResponse:
      return "consentRequestsSent";
    case kStatsValueNameSentPingResponses:
      return "responsesSent";
    case kStatsValueNameRecvPingRequests:
      return "requestsReceived";
    case kStatsValueNameRecvPingResponses:
      return "responsesReceived";
    // STUN Keepalive pings.
    case kStatsValueNameSentStunKeepaliveRequests:
      return "stunKeepaliveRequestsSent";
    case kStatsValueNameRecvStunKeepaliveResponses:
      return "stunKeepaliveResponsesReceived";
    case kStatsValueNameStunKeepaliveRttTotal:
      return "stunKeepaliveRttTotal";
    case kStatsValueNameStunKeepaliveRttSquaredTotal:
      return "stunKeepaliveRttSquaredTotal";

    // Candidate related attributes. Values are taken from
    // http://w3c.github.io/webrtc-stats/#rtcstatstype-enum*.
    case kStatsValueNameCandidateIPAddress:
      return "ipAddress";
    case kStatsValueNameCandidateNetworkType:
      return "networkType";
    case kStatsValueNameCandidatePortNumber:
      return "portNumber";
    case kStatsValueNameCandidatePriority:
      return "priority";
    case kStatsValueNameCandidateTransportType:
      return "transport";
    case kStatsValueNameCandidateType:
      return "candidateType";

    case kStatsValueNameChannelId:
      return "googChannelId";
    case kStatsValueNameCodecName:
      return "googCodecName";
    case kStatsValueNameComponent:
      return "googComponent";
    case kStatsValueNameContentName:
      return "googContentName";
    case kStatsValueNameContentType:
      return "googContentType";
    case kStatsValueNameCpuLimitedResolution:
      return "googCpuLimitedResolution";
    case kStatsValueNameDecodingCTSG:
      return "googDecodingCTSG";
    case kStatsValueNameDecodingCTN:
      return "googDecodingCTN";
    case kStatsValueNameDecodingMutedOutput:
      return "googDecodingMuted";
    case kStatsValueNameDecodingNormal:
      return "googDecodingNormal";
    case kStatsValueNameDecodingPLC:
      return "googDecodingPLC";
    case kStatsValueNameDecodingCodecPLC:
      return "googDecodingCodecPLC";
    case kStatsValueNameDecodingCNG:
      return "googDecodingCNG";
    case kStatsValueNameDecodingPLCCNG:
      return "googDecodingPLCCNG";
    case kStatsValueNameDer:
      return "googDerBase64";
    case kStatsValueNameDtlsCipher:
      return "dtlsCipher";
    case kStatsValueNameEchoDelayMedian:
      return "googEchoCancellationEchoDelayMedian";
    case kStatsValueNameEchoDelayStdDev:
      return "googEchoCancellationEchoDelayStdDev";
    case kStatsValueNameEchoReturnLoss:
      return "googEchoCancellationReturnLoss";
    case kStatsValueNameEchoReturnLossEnhancement:
      return "googEchoCancellationReturnLossEnhancement";
    case kStatsValueNameEncodeUsagePercent:
      return "googEncodeUsagePercent";
    case kStatsValueNameExpandRate:
      return "googExpandRate";
    case kStatsValueNameFingerprint:
      return "googFingerprint";
    case kStatsValueNameFingerprintAlgorithm:
      return "googFingerprintAlgorithm";
    case kStatsValueNameFirsReceived:
      return "googFirsReceived";
    case kStatsValueNameFirsSent:
      return "googFirsSent";
    case kStatsValueNameFirstFrameReceivedToDecodedMs:
      return "googFirstFrameReceivedToDecodedMs";
    case kStatsValueNameFrameHeightInput:
      return "googFrameHeightInput";
    case kStatsValueNameFrameHeightReceived:
      return "googFrameHeightReceived";
    case kStatsValueNameFrameHeightSent:
      return "googFrameHeightSent";
    case kStatsValueNameFrameRateReceived:
      return "googFrameRateReceived";
    case kStatsValueNameFrameRateDecoded:
      return "googFrameRateDecoded";
    case kStatsValueNameFrameRateOutput:
      return "googFrameRateOutput";
    case kStatsValueNameDecodeMs:
      return "googDecodeMs";
    case kStatsValueNameMaxDecodeMs:
      return "googMaxDecodeMs";
    case kStatsValueNameCurrentDelayMs:
      return "googCurrentDelayMs";
    case kStatsValueNameTargetDelayMs:
      return "googTargetDelayMs";
    case kStatsValueNameJitterBufferMs:
      return "googJitterBufferMs";
    case kStatsValueNameMinPlayoutDelayMs:
      return "googMinPlayoutDelayMs";
    case kStatsValueNameRenderDelayMs:
      return "googRenderDelayMs";
    case kStatsValueNameCaptureStartNtpTimeMs:
      return "googCaptureStartNtpTimeMs";
    case kStatsValueNameFrameRateInput:
      return "googFrameRateInput";
    case kStatsValueNameFrameRateSent:
      return "googFrameRateSent";
    case kStatsValueNameFrameWidthInput:
      return "googFrameWidthInput";
    case kStatsValueNameFrameWidthReceived:
      return "googFrameWidthReceived";
    case kStatsValueNameFrameWidthSent:
      return "googFrameWidthSent";
    case kStatsValueNameHasEnteredLowResolution:
      return "googHasEnteredLowResolution";
    case kStatsValueNameHugeFramesSent:
      return "hugeFramesSent";
    case kStatsValueNameInitiator:
      return "googInitiator";
    case kStatsValueNameInterframeDelayMaxMs:
      return "googInterframeDelayMax";
    case kStatsValueNameIssuerId:
      return "googIssuerId";
    case kStatsValueNameJitterReceived:
      return "googJitterReceived";
    case kStatsValueNameLocalAddress:
      return "googLocalAddress";
    case kStatsValueNameLocalCandidateId:
      return "localCandidateId";
    case kStatsValueNameLocalCandidateType:
      return "googLocalCandidateType";
    case kStatsValueNameLocalCertificateId:
      return "localCertificateId";
    case kStatsValueNameAdaptationChanges:
      return "googAdaptationChanges";
    case kStatsValueNameNacksReceived:
      return "googNacksReceived";
    case kStatsValueNameNacksSent:
      return "googNacksSent";
    case kStatsValueNamePreemptiveExpandRate:
      return "googPreemptiveExpandRate";
    case kStatsValueNamePlisReceived:
      return "googPlisReceived";
    case kStatsValueNamePlisSent:
      return "googPlisSent";
    case kStatsValueNamePreferredJitterBufferMs:
      return "googPreferredJitterBufferMs";
    case kStatsValueNameReceiving:
      return "googReadable";
    case kStatsValueNameRemoteAddress:
      return "googRemoteAddress";
    case kStatsValueNameRemoteCandidateId:
      return "remoteCandidateId";
    case kStatsValueNameRemoteCandidateType:
      return "googRemoteCandidateType";
    case kStatsValueNameRemoteCertificateId:
      return "remoteCertificateId";
    case kStatsValueNameResidualEchoLikelihood:
      return "googResidualEchoLikelihood";
    case kStatsValueNameResidualEchoLikelihoodRecentMax:
      return "googResidualEchoLikelihoodRecentMax";
    case kStatsValueNameAnaBitrateActionCounter:
      return "googAnaBitrateActionCounter";
    case kStatsValueNameAnaChannelActionCounter:
      return "googAnaChannelActionCounter";
    case kStatsValueNameAnaDtxActionCounter:
      return "googAnaDtxActionCounter";
    case kStatsValueNameAnaFecActionCounter:
      return "googAnaFecActionCounter";
    case kStatsValueNameAnaFrameLengthIncreaseCounter:
      return "googAnaFrameLengthIncreaseCounter";
    case kStatsValueNameAnaFrameLengthDecreaseCounter:
      return "googAnaFrameLengthDecreaseCounter";
    case kStatsValueNameAnaUplinkPacketLossFraction:
      return "googAnaUplinkPacketLossFraction";
    case kStatsValueNameRetransmitBitrate:
      return "googRetransmitBitrate";
    case kStatsValueNameRtt:
      return "googRtt";
    case kStatsValueNameSecondaryDecodedRate:
      return "googSecondaryDecodedRate";
    case kStatsValueNameSecondaryDiscardedRate:
      return "googSecondaryDiscardedRate";
    case kStatsValueNameSendPacketsDiscarded:
      return "packetsDiscardedOnSend";
    case kStatsValueNameSpeechExpandRate:
      return "googSpeechExpandRate";
    case kStatsValueNameSrtpCipher:
      return "srtpCipher";
    case kStatsValueNameTargetEncBitrate:
      return "googTargetEncBitrate";
    case kStatsValueNameTotalAudioEnergy:
      return "totalAudioEnergy";
    case kStatsValueNameTotalSamplesDuration:
      return "totalSamplesDuration";
    case kStatsValueNameTransmitBitrate:
      return "googTransmitBitrate";
    case kStatsValueNameTransportType:
      return "googTransportType";
    case kStatsValueNameTrackId:
      return "googTrackId";
    case kStatsValueNameTimingFrameInfo:
      return "googTimingFrameInfo";
    case kStatsValueNameWritable:
      return "googWritable";
    case kStatsValueNameAudioDeviceUnderrunCounter:
      return "googAudioDeviceUnderrunCounter";
    case kStatsValueNameLocalCandidateRelayProtocol:
      return "googLocalCandidateRelayProtocol";
  }

  return nullptr;
}

std::string StatsReport::Value::ToString() const {
  switch (type_) {
    case kInt:
      return rtc::ToString(value_.int_);
    case kInt64:
      return rtc::ToString(value_.int64_);
    case kFloat:
      return rtc::ToString(value_.float_);
    case kStaticString:
      return std::string(value_.static_string_);
    case kString:
      return *value_.string_;
    case kBool:
      return value_.bool_ ? "true" : "false";
    case kId:
      return (*value_.id_)->ToString();
  }
  RTC_DCHECK_NOTREACHED();
  return std::string();
}

StatsReport::StatsReport(const Id& id) : id_(id), timestamp_(0.0) {
  RTC_DCHECK(id_.get());
}

StatsReport::~StatsReport() = default;

// static
StatsReport::Id StatsReport::NewBandwidthEstimationId() {
  return rtc::make_ref_counted<BandwidthEstimationId>();
}

// static
StatsReport::Id StatsReport::NewTypedId(StatsType type, const std::string& id) {
  return rtc::make_ref_counted<TypedId>(type, id);
}

// static
StatsReport::Id StatsReport::NewTypedIntId(StatsType type, int id) {
  return rtc::make_ref_counted<TypedIntId>(type, id);
}

// static
StatsReport::Id StatsReport::NewIdWithDirection(
    StatsType type,
    const std::string& id,
    StatsReport::Direction direction) {
  return rtc::make_ref_counted<IdWithDirection>(type, id, direction);
}

// static
StatsReport::Id StatsReport::NewCandidateId(bool local, const std::string& id) {
  return rtc::make_ref_counted<CandidateId>(local, id);
}

// static
StatsReport::Id StatsReport::NewComponentId(const std::string& content_name,
                                            int component) {
  return rtc::make_ref_counted<ComponentId>(content_name, component);
}

// static
StatsReport::Id StatsReport::NewCandidatePairId(const std::string& content_name,
                                                int component,
                                                int index) {
  return rtc::make_ref_counted<CandidatePairId>(content_name, component, index);
}

const char* StatsReport::TypeToString() const {
  return InternalTypeToString(id_->type());
}

void StatsReport::AddString(StatsReport::StatsValueName name,
                            const std::string& value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value));
}

void StatsReport::AddString(StatsReport::StatsValueName name,
                            const char* value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value));
}

void StatsReport::AddInt64(StatsReport::StatsValueName name, int64_t value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value, Value::kInt64));
}

void StatsReport::AddInt(StatsReport::StatsValueName name, int value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == static_cast<int64_t>(value)))
    values_[name] = ValuePtr(new Value(name, value, Value::kInt));
}

void StatsReport::AddFloat(StatsReport::StatsValueName name, float value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value));
}

void StatsReport::AddBoolean(StatsReport::StatsValueName name, bool value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value));
}

void StatsReport::AddId(StatsReport::StatsValueName name, const Id& value) {
  const Value* found = FindValue(name);
  if (!found || !(*found == value))
    values_[name] = ValuePtr(new Value(name, value));
}

const StatsReport::Value* StatsReport::FindValue(StatsValueName name) const {
  Values::const_iterator it = values_.find(name);
  return it == values_.end() ? nullptr : it->second.get();
}

StatsCollection::StatsCollection() {}

StatsCollection::~StatsCollection() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  for (auto* r : list_)
    delete r;
}

StatsCollection::const_iterator StatsCollection::begin() const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return list_.begin();
}

StatsCollection::const_iterator StatsCollection::end() const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return list_.end();
}

size_t StatsCollection::size() const {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  return list_.size();
}

StatsReport* StatsCollection::InsertNew(const StatsReport::Id& id) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(Find(id) == nullptr);
  StatsReport* report = new StatsReport(id);
  list_.push_back(report);
  return report;
}

StatsReport* StatsCollection::FindOrAddNew(const StatsReport::Id& id) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  StatsReport* ret = Find(id);
  return ret ? ret : InsertNew(id);
}

StatsReport* StatsCollection::ReplaceOrAddNew(const StatsReport::Id& id) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  RTC_DCHECK(id.get());
  Container::iterator it = absl::c_find_if(
      list_,
      [&id](const StatsReport* r) -> bool { return r->id()->Equals(id); });
  if (it != end()) {
    StatsReport* report = new StatsReport((*it)->id());
    delete *it;
    *it = report;
    return report;
  }
  return InsertNew(id);
}

StatsCollection::Container StatsCollection::DetachCollection() {
  RTC_DCHECK_RUN_ON(&thread_checker_);
#if RTC_DCHECK_IS_ON
  for (auto* report : list_)
    report->DetachSequenceCheckers();
#endif
  return std::move(list_);
}

void StatsCollection::MergeCollection(Container collection) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  for (auto* report : collection) {
#if RTC_DCHECK_IS_ON
    report->AttachSequenceCheckers();
#endif
    Container::iterator it = absl::c_find_if(list_, [&](const StatsReport* r) {
      return r->id()->Equals(report->id());
    });
    if (it == list_.end()) {
      list_.push_back(report);
    } else {
      delete *it;
      *it = report;
    }
  }
}

// Looks for a report with the given `id`.  If one is not found, null
// will be returned.
StatsReport* StatsCollection::Find(const StatsReport::Id& id) {
  RTC_DCHECK_RUN_ON(&thread_checker_);
  Container::iterator it = absl::c_find_if(
      list_,
      [&id](const StatsReport* r) -> bool { return r->id()->Equals(id); });
  return it == list_.end() ? nullptr : *it;
}

}  // namespace webrtc
