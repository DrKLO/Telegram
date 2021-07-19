/*
 *  Copyright 2017 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

#include "sdk/android/src/jni/pc/ice_candidate.h"

#include <string>

#include "pc/webrtc_sdp.h"
#include "sdk/android/generated_peerconnection_jni/IceCandidate_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/pc/media_stream_track.h"
#include "sdk/android/src/jni/pc/peer_connection.h"

namespace webrtc {
namespace jni {

namespace {

ScopedJavaLocalRef<jobject> CreateJavaIceCandidate(JNIEnv* env,
                                                   const std::string& sdp_mid,
                                                   int sdp_mline_index,
                                                   const std::string& sdp,
                                                   const std::string server_url,
                                                   int adapterType) {
  return Java_IceCandidate_Constructor(
      env, NativeToJavaString(env, sdp_mid), sdp_mline_index,
      NativeToJavaString(env, sdp), NativeToJavaString(env, server_url),
      NativeToJavaAdapterType(env, adapterType));
}

}  // namespace

cricket::Candidate JavaToNativeCandidate(JNIEnv* jni,
                                         const JavaRef<jobject>& j_candidate) {
  std::string sdp_mid =
      JavaToStdString(jni, Java_IceCandidate_getSdpMid(jni, j_candidate));
  std::string sdp =
      JavaToStdString(jni, Java_IceCandidate_getSdp(jni, j_candidate));
  cricket::Candidate candidate;
  if (!SdpDeserializeCandidate(sdp_mid, sdp, &candidate, NULL)) {
    RTC_LOG(LS_ERROR) << "SdpDescrializeCandidate failed with sdp " << sdp;
  }
  return candidate;
}

ScopedJavaLocalRef<jobject> NativeToJavaCandidate(
    JNIEnv* env,
    const cricket::Candidate& candidate) {
  std::string sdp = SdpSerializeCandidate(candidate);
  RTC_CHECK(!sdp.empty()) << "got an empty ICE candidate";
  // sdp_mline_index is not used, pass an invalid value -1.
  return CreateJavaIceCandidate(env, candidate.transport_name(),
                                -1 /* sdp_mline_index */, sdp,
                                "" /* server_url */, candidate.network_type());
}

ScopedJavaLocalRef<jobject> NativeToJavaIceCandidate(
    JNIEnv* env,
    const IceCandidateInterface& candidate) {
  std::string sdp;
  RTC_CHECK(candidate.ToString(&sdp)) << "got so far: " << sdp;
  return CreateJavaIceCandidate(env, candidate.sdp_mid(),
                                candidate.sdp_mline_index(), sdp,
                                candidate.candidate().url(), 0);
}

ScopedJavaLocalRef<jobjectArray> NativeToJavaCandidateArray(
    JNIEnv* jni,
    const std::vector<cricket::Candidate>& candidates) {
  return NativeToJavaObjectArray(jni, candidates,
                                 org_webrtc_IceCandidate_clazz(jni),
                                 &NativeToJavaCandidate);
}

PeerConnectionInterface::IceTransportsType JavaToNativeIceTransportsType(
    JNIEnv* jni,
    const JavaRef<jobject>& j_ice_transports_type) {
  std::string enum_name = GetJavaEnumName(jni, j_ice_transports_type);

  if (enum_name == "ALL")
    return PeerConnectionInterface::kAll;

  if (enum_name == "RELAY")
    return PeerConnectionInterface::kRelay;

  if (enum_name == "NOHOST")
    return PeerConnectionInterface::kNoHost;

  if (enum_name == "NONE")
    return PeerConnectionInterface::kNone;

  RTC_CHECK(false) << "Unexpected IceTransportsType enum_name " << enum_name;
  return PeerConnectionInterface::kAll;
}

PeerConnectionInterface::BundlePolicy JavaToNativeBundlePolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_bundle_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_bundle_policy);

  if (enum_name == "BALANCED")
    return PeerConnectionInterface::kBundlePolicyBalanced;

  if (enum_name == "MAXBUNDLE")
    return PeerConnectionInterface::kBundlePolicyMaxBundle;

  if (enum_name == "MAXCOMPAT")
    return PeerConnectionInterface::kBundlePolicyMaxCompat;

  RTC_CHECK(false) << "Unexpected BundlePolicy enum_name " << enum_name;
  return PeerConnectionInterface::kBundlePolicyBalanced;
}

PeerConnectionInterface::RtcpMuxPolicy JavaToNativeRtcpMuxPolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_rtcp_mux_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_rtcp_mux_policy);

  if (enum_name == "NEGOTIATE")
    return PeerConnectionInterface::kRtcpMuxPolicyNegotiate;

  if (enum_name == "REQUIRE")
    return PeerConnectionInterface::kRtcpMuxPolicyRequire;

  RTC_CHECK(false) << "Unexpected RtcpMuxPolicy enum_name " << enum_name;
  return PeerConnectionInterface::kRtcpMuxPolicyNegotiate;
}

PeerConnectionInterface::TcpCandidatePolicy JavaToNativeTcpCandidatePolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_tcp_candidate_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_tcp_candidate_policy);

  if (enum_name == "ENABLED")
    return PeerConnectionInterface::kTcpCandidatePolicyEnabled;

  if (enum_name == "DISABLED")
    return PeerConnectionInterface::kTcpCandidatePolicyDisabled;

  RTC_CHECK(false) << "Unexpected TcpCandidatePolicy enum_name " << enum_name;
  return PeerConnectionInterface::kTcpCandidatePolicyEnabled;
}

PeerConnectionInterface::CandidateNetworkPolicy
JavaToNativeCandidateNetworkPolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_candidate_network_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_candidate_network_policy);

  if (enum_name == "ALL")
    return PeerConnectionInterface::kCandidateNetworkPolicyAll;

  if (enum_name == "LOW_COST")
    return PeerConnectionInterface::kCandidateNetworkPolicyLowCost;

  RTC_CHECK(false) << "Unexpected CandidateNetworkPolicy enum_name "
                   << enum_name;
  return PeerConnectionInterface::kCandidateNetworkPolicyAll;
}

rtc::KeyType JavaToNativeKeyType(JNIEnv* jni,
                                 const JavaRef<jobject>& j_key_type) {
  std::string enum_name = GetJavaEnumName(jni, j_key_type);

  if (enum_name == "RSA")
    return rtc::KT_RSA;
  if (enum_name == "ECDSA")
    return rtc::KT_ECDSA;

  RTC_CHECK(false) << "Unexpected KeyType enum_name " << enum_name;
  return rtc::KT_ECDSA;
}

PeerConnectionInterface::ContinualGatheringPolicy
JavaToNativeContinualGatheringPolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_gathering_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_gathering_policy);
  if (enum_name == "GATHER_ONCE")
    return PeerConnectionInterface::GATHER_ONCE;

  if (enum_name == "GATHER_CONTINUALLY")
    return PeerConnectionInterface::GATHER_CONTINUALLY;

  RTC_CHECK(false) << "Unexpected ContinualGatheringPolicy enum name "
                   << enum_name;
  return PeerConnectionInterface::GATHER_ONCE;
}

webrtc::PortPrunePolicy JavaToNativePortPrunePolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_port_prune_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_port_prune_policy);
  if (enum_name == "NO_PRUNE") {
    return webrtc::NO_PRUNE;
  }
  if (enum_name == "PRUNE_BASED_ON_PRIORITY") {
    return webrtc::PRUNE_BASED_ON_PRIORITY;
  }
  if (enum_name == "KEEP_FIRST_READY") {
    return webrtc::KEEP_FIRST_READY;
  }

  RTC_CHECK(false) << " Unexpected PortPrunePolicy enum name " << enum_name;

  return webrtc::NO_PRUNE;
}

PeerConnectionInterface::TlsCertPolicy JavaToNativeTlsCertPolicy(
    JNIEnv* jni,
    const JavaRef<jobject>& j_ice_server_tls_cert_policy) {
  std::string enum_name = GetJavaEnumName(jni, j_ice_server_tls_cert_policy);

  if (enum_name == "TLS_CERT_POLICY_SECURE")
    return PeerConnectionInterface::kTlsCertPolicySecure;

  if (enum_name == "TLS_CERT_POLICY_INSECURE_NO_CHECK")
    return PeerConnectionInterface::kTlsCertPolicyInsecureNoCheck;

  RTC_CHECK(false) << "Unexpected TlsCertPolicy enum_name " << enum_name;
  return PeerConnectionInterface::kTlsCertPolicySecure;
}

absl::optional<rtc::AdapterType> JavaToNativeNetworkPreference(
    JNIEnv* jni,
    const JavaRef<jobject>& j_network_preference) {
  std::string enum_name = GetJavaEnumName(jni, j_network_preference);

  if (enum_name == "UNKNOWN")
    return absl::nullopt;

  if (enum_name == "ETHERNET")
    return rtc::ADAPTER_TYPE_ETHERNET;

  if (enum_name == "WIFI")
    return rtc::ADAPTER_TYPE_WIFI;

  if (enum_name == "CELLULAR")
    return rtc::ADAPTER_TYPE_CELLULAR;

  if (enum_name == "VPN")
    return rtc::ADAPTER_TYPE_VPN;

  if (enum_name == "LOOPBACK")
    return rtc::ADAPTER_TYPE_LOOPBACK;

  RTC_CHECK(false) << "Unexpected NetworkPreference enum_name " << enum_name;
  return absl::nullopt;
}

}  // namespace jni
}  // namespace webrtc
