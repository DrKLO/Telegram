/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Lifecycle notes: objects are owned where they will be called; in other words
// FooObservers are owned by C++-land, and user-callable objects (e.g.
// PeerConnection and VideoTrack) are owned by Java-land.
// When this file (or other files in this directory) allocates C++
// RefCountInterfaces it AddRef()s an artificial ref simulating the jlong held
// in Java-land, and then Release()s the ref in the respective free call.
// Sometimes this AddRef is implicit in the construction of a scoped_refptr<>
// which is then .release()d. Any persistent (non-local) references from C++ to
// Java must be global or weak (in which case they must be checked before use)!
//
// Exception notes: pretty much all JNI calls can throw Java exceptions, so each
// call through a JNIEnv* pointer needs to be followed by an ExceptionCheck()
// call. In this file this is done in CHECK_EXCEPTION, making for much easier
// debugging in case of failure (the alternative is to wait for control to
// return to the Java frame that called code in this file, at which point it's
// impossible to tell which JNI call broke).

#include "sdk/android/src/jni/pc/peer_connection.h"

#include <limits>
#include <memory>
#include <string>
#include <utility>

#include "api/peer_connection_interface.h"
#include "api/rtc_event_log_output_file.h"
#include "api/rtp_receiver_interface.h"
#include "api/rtp_sender_interface.h"
#include "api/rtp_transceiver_interface.h"
#include "rtc_base/checks.h"
#include "rtc_base/logging.h"
#include "rtc_base/numerics/safe_conversions.h"
#include "sdk/android/generated_peerconnection_jni/CandidatePairChangeEvent_jni.h"
#include "sdk/android/generated_peerconnection_jni/IceCandidateErrorEvent_jni.h"
#include "sdk/android/generated_peerconnection_jni/PeerConnection_jni.h"
#include "sdk/android/native_api/jni/java_types.h"
#include "sdk/android/src/jni/jni_helpers.h"
#include "sdk/android/src/jni/pc/add_ice_candidate_observer.h"
#include "sdk/android/src/jni/pc/crypto_options.h"
#include "sdk/android/src/jni/pc/data_channel.h"
#include "sdk/android/src/jni/pc/ice_candidate.h"
#include "sdk/android/src/jni/pc/media_constraints.h"
#include "sdk/android/src/jni/pc/media_stream_track.h"
#include "sdk/android/src/jni/pc/rtc_certificate.h"
#include "sdk/android/src/jni/pc/rtc_stats_collector_callback_wrapper.h"
#include "sdk/android/src/jni/pc/rtp_sender.h"
#include "sdk/android/src/jni/pc/sdp_observer.h"
#include "sdk/android/src/jni/pc/session_description.h"
#include "sdk/android/src/jni/pc/stats_observer.h"
#include "sdk/android/src/jni/pc/turn_customizer.h"

namespace webrtc {
namespace jni {

namespace {

PeerConnectionInterface* ExtractNativePC(JNIEnv* jni,
                                         const JavaRef<jobject>& j_pc) {
  return reinterpret_cast<OwnedPeerConnection*>(
             Java_PeerConnection_getNativeOwnedPeerConnection(jni, j_pc))
      ->pc();
}

PeerConnectionInterface::IceServers JavaToNativeIceServers(
    JNIEnv* jni,
    const JavaRef<jobject>& j_ice_servers) {
  PeerConnectionInterface::IceServers ice_servers;
  for (const JavaRef<jobject>& j_ice_server : Iterable(jni, j_ice_servers)) {
    ScopedJavaLocalRef<jobject> j_ice_server_tls_cert_policy =
        Java_IceServer_getTlsCertPolicy(jni, j_ice_server);
    ScopedJavaLocalRef<jobject> urls =
        Java_IceServer_getUrls(jni, j_ice_server);
    ScopedJavaLocalRef<jstring> username =
        Java_IceServer_getUsername(jni, j_ice_server);
    ScopedJavaLocalRef<jstring> password =
        Java_IceServer_getPassword(jni, j_ice_server);
    PeerConnectionInterface::TlsCertPolicy tls_cert_policy =
        JavaToNativeTlsCertPolicy(jni, j_ice_server_tls_cert_policy);
    ScopedJavaLocalRef<jstring> hostname =
        Java_IceServer_getHostname(jni, j_ice_server);
    ScopedJavaLocalRef<jobject> tls_alpn_protocols =
        Java_IceServer_getTlsAlpnProtocols(jni, j_ice_server);
    ScopedJavaLocalRef<jobject> tls_elliptic_curves =
        Java_IceServer_getTlsEllipticCurves(jni, j_ice_server);
    PeerConnectionInterface::IceServer server;
    server.urls = JavaListToNativeVector<std::string, jstring>(
        jni, urls, &JavaToNativeString);
    server.username = JavaToNativeString(jni, username);
    server.password = JavaToNativeString(jni, password);
    server.tls_cert_policy = tls_cert_policy;
    server.hostname = JavaToNativeString(jni, hostname);
    server.tls_alpn_protocols = JavaListToNativeVector<std::string, jstring>(
        jni, tls_alpn_protocols, &JavaToNativeString);
    server.tls_elliptic_curves = JavaListToNativeVector<std::string, jstring>(
        jni, tls_elliptic_curves, &JavaToNativeString);
    ice_servers.push_back(server);
  }
  return ice_servers;
}

SdpSemantics JavaToNativeSdpSemantics(JNIEnv* jni,
                                      const JavaRef<jobject>& j_sdp_semantics) {
  std::string enum_name = GetJavaEnumName(jni, j_sdp_semantics);

  if (enum_name == "PLAN_B")
    return SdpSemantics::kPlanB_DEPRECATED;

  if (enum_name == "UNIFIED_PLAN")
    return SdpSemantics::kUnifiedPlan;

  RTC_DCHECK_NOTREACHED();
  return SdpSemantics::kUnifiedPlan;
}

ScopedJavaLocalRef<jobject> NativeToJavaCandidatePairChange(
    JNIEnv* env,
    const cricket::CandidatePairChangeEvent& event) {
  const auto& selected_pair = event.selected_candidate_pair;
  return Java_CandidatePairChangeEvent_Constructor(
      env, NativeToJavaCandidate(env, selected_pair.local_candidate()),
      NativeToJavaCandidate(env, selected_pair.remote_candidate()),
      static_cast<int>(event.last_data_received_ms),
      NativeToJavaString(env, event.reason),
      static_cast<int>(event.estimated_disconnected_time_ms));
}

}  // namespace

ScopedJavaLocalRef<jobject> NativeToJavaAdapterType(JNIEnv* env,
                                                    int adapterType) {
  return Java_AdapterType_fromNativeIndex(env, adapterType);
}

void JavaToNativeRTCConfiguration(
    JNIEnv* jni,
    const JavaRef<jobject>& j_rtc_config,
    PeerConnectionInterface::RTCConfiguration* rtc_config) {
  ScopedJavaLocalRef<jobject> j_ice_transports_type =
      Java_RTCConfiguration_getIceTransportsType(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_bundle_policy =
      Java_RTCConfiguration_getBundlePolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_rtcp_mux_policy =
      Java_RTCConfiguration_getRtcpMuxPolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_rtc_certificate =
      Java_RTCConfiguration_getCertificate(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_tcp_candidate_policy =
      Java_RTCConfiguration_getTcpCandidatePolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_candidate_network_policy =
      Java_RTCConfiguration_getCandidateNetworkPolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_ice_servers =
      Java_RTCConfiguration_getIceServers(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_continual_gathering_policy =
      Java_RTCConfiguration_getContinualGatheringPolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_turn_port_prune_policy =
      Java_RTCConfiguration_getTurnPortPrunePolicy(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_turn_customizer =
      Java_RTCConfiguration_getTurnCustomizer(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_network_preference =
      Java_RTCConfiguration_getNetworkPreference(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_sdp_semantics =
      Java_RTCConfiguration_getSdpSemantics(jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_crypto_options =
      Java_RTCConfiguration_getCryptoOptions(jni, j_rtc_config);

  rtc_config->type = JavaToNativeIceTransportsType(jni, j_ice_transports_type);
  rtc_config->bundle_policy = JavaToNativeBundlePolicy(jni, j_bundle_policy);
  rtc_config->rtcp_mux_policy =
      JavaToNativeRtcpMuxPolicy(jni, j_rtcp_mux_policy);
  if (!j_rtc_certificate.is_null()) {
    rtc::scoped_refptr<rtc::RTCCertificate> certificate =
        rtc::RTCCertificate::FromPEM(
            JavaToNativeRTCCertificatePEM(jni, j_rtc_certificate));
    RTC_CHECK(certificate != nullptr) << "supplied certificate is malformed.";
    rtc_config->certificates.push_back(certificate);
  }
  rtc_config->tcp_candidate_policy =
      JavaToNativeTcpCandidatePolicy(jni, j_tcp_candidate_policy);
  rtc_config->candidate_network_policy =
      JavaToNativeCandidateNetworkPolicy(jni, j_candidate_network_policy);
  rtc_config->servers = JavaToNativeIceServers(jni, j_ice_servers);
  rtc_config->audio_jitter_buffer_max_packets =
      Java_RTCConfiguration_getAudioJitterBufferMaxPackets(jni, j_rtc_config);
  rtc_config->audio_jitter_buffer_fast_accelerate =
      Java_RTCConfiguration_getAudioJitterBufferFastAccelerate(jni,
                                                               j_rtc_config);
  rtc_config->ice_connection_receiving_timeout =
      Java_RTCConfiguration_getIceConnectionReceivingTimeout(jni, j_rtc_config);
  rtc_config->ice_backup_candidate_pair_ping_interval =
      Java_RTCConfiguration_getIceBackupCandidatePairPingInterval(jni,
                                                                  j_rtc_config);
  rtc_config->continual_gathering_policy =
      JavaToNativeContinualGatheringPolicy(jni, j_continual_gathering_policy);
  rtc_config->ice_candidate_pool_size =
      Java_RTCConfiguration_getIceCandidatePoolSize(jni, j_rtc_config);
  rtc_config->prune_turn_ports =
      Java_RTCConfiguration_getPruneTurnPorts(jni, j_rtc_config);
  rtc_config->turn_port_prune_policy =
      JavaToNativePortPrunePolicy(jni, j_turn_port_prune_policy);
  rtc_config->presume_writable_when_fully_relayed =
      Java_RTCConfiguration_getPresumeWritableWhenFullyRelayed(jni,
                                                               j_rtc_config);
  rtc_config->surface_ice_candidates_on_ice_transport_type_changed =
      Java_RTCConfiguration_getSurfaceIceCandidatesOnIceTransportTypeChanged(
          jni, j_rtc_config);
  ScopedJavaLocalRef<jobject> j_ice_check_interval_strong_connectivity =
      Java_RTCConfiguration_getIceCheckIntervalStrongConnectivity(jni,
                                                                  j_rtc_config);
  rtc_config->ice_check_interval_strong_connectivity =
      JavaToNativeOptionalInt(jni, j_ice_check_interval_strong_connectivity);
  ScopedJavaLocalRef<jobject> j_ice_check_interval_weak_connectivity =
      Java_RTCConfiguration_getIceCheckIntervalWeakConnectivity(jni,
                                                                j_rtc_config);
  rtc_config->ice_check_interval_weak_connectivity =
      JavaToNativeOptionalInt(jni, j_ice_check_interval_weak_connectivity);
  ScopedJavaLocalRef<jobject> j_ice_check_min_interval =
      Java_RTCConfiguration_getIceCheckMinInterval(jni, j_rtc_config);
  rtc_config->ice_check_min_interval =
      JavaToNativeOptionalInt(jni, j_ice_check_min_interval);
  ScopedJavaLocalRef<jobject> j_ice_unwritable_timeout =
      Java_RTCConfiguration_getIceUnwritableTimeout(jni, j_rtc_config);
  rtc_config->ice_unwritable_timeout =
      JavaToNativeOptionalInt(jni, j_ice_unwritable_timeout);
  ScopedJavaLocalRef<jobject> j_ice_unwritable_min_checks =
      Java_RTCConfiguration_getIceUnwritableMinChecks(jni, j_rtc_config);
  rtc_config->ice_unwritable_min_checks =
      JavaToNativeOptionalInt(jni, j_ice_unwritable_min_checks);
  ScopedJavaLocalRef<jobject> j_stun_candidate_keepalive_interval =
      Java_RTCConfiguration_getStunCandidateKeepaliveInterval(jni,
                                                              j_rtc_config);
  rtc_config->stun_candidate_keepalive_interval =
      JavaToNativeOptionalInt(jni, j_stun_candidate_keepalive_interval);
  ScopedJavaLocalRef<jobject> j_stable_writable_connection_ping_interval_ms =
      Java_RTCConfiguration_getStableWritableConnectionPingIntervalMs(
          jni, j_rtc_config);
  rtc_config->stable_writable_connection_ping_interval_ms =
      JavaToNativeOptionalInt(jni,
                              j_stable_writable_connection_ping_interval_ms);
  rtc_config->disable_ipv6_on_wifi =
      Java_RTCConfiguration_getDisableIPv6OnWifi(jni, j_rtc_config);
  rtc_config->max_ipv6_networks =
      Java_RTCConfiguration_getMaxIPv6Networks(jni, j_rtc_config);

  rtc_config->turn_customizer = GetNativeTurnCustomizer(jni, j_turn_customizer);

  rtc_config->media_config.enable_dscp =
      Java_RTCConfiguration_getEnableDscp(jni, j_rtc_config);
  rtc_config->media_config.video.enable_cpu_adaptation =
      Java_RTCConfiguration_getEnableCpuOveruseDetection(jni, j_rtc_config);
  rtc_config->media_config.video.suspend_below_min_bitrate =
      Java_RTCConfiguration_getSuspendBelowMinBitrate(jni, j_rtc_config);
  rtc_config->screencast_min_bitrate = JavaToNativeOptionalInt(
      jni, Java_RTCConfiguration_getScreencastMinBitrate(jni, j_rtc_config));
  rtc_config->network_preference =
      JavaToNativeNetworkPreference(jni, j_network_preference);
  rtc_config->sdp_semantics = JavaToNativeSdpSemantics(jni, j_sdp_semantics);
  rtc_config->active_reset_srtp_params =
      Java_RTCConfiguration_getActiveResetSrtpParams(jni, j_rtc_config);
  rtc_config->crypto_options =
      JavaToNativeOptionalCryptoOptions(jni, j_crypto_options);
  rtc_config->offer_extmap_allow_mixed =
      Java_RTCConfiguration_getOfferExtmapAllowMixed(jni, j_rtc_config);
  rtc_config->enable_implicit_rollback =
      Java_RTCConfiguration_getEnableImplicitRollback(jni, j_rtc_config);

  ScopedJavaLocalRef<jstring> j_turn_logging_id =
      Java_RTCConfiguration_getTurnLoggingId(jni, j_rtc_config);
  if (!IsNull(jni, j_turn_logging_id)) {
    rtc_config->turn_logging_id = JavaToNativeString(jni, j_turn_logging_id);
  }
}

rtc::KeyType GetRtcConfigKeyType(JNIEnv* env,
                                 const JavaRef<jobject>& j_rtc_config) {
  return JavaToNativeKeyType(
      env, Java_RTCConfiguration_getKeyType(env, j_rtc_config));
}

PeerConnectionObserverJni::PeerConnectionObserverJni(
    JNIEnv* jni,
    const JavaRef<jobject>& j_observer)
    : j_observer_global_(jni, j_observer) {}

PeerConnectionObserverJni::~PeerConnectionObserverJni() = default;

void PeerConnectionObserverJni::OnIceCandidate(
    const IceCandidateInterface* candidate) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onIceCandidate(env, j_observer_global_,
                               NativeToJavaIceCandidate(env, *candidate));
}

void PeerConnectionObserverJni::OnIceCandidateError(
    const std::string& address,
    int port,
    const std::string& url,
    int error_code,
    const std::string& error_text) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> event = Java_IceCandidateErrorEvent_Constructor(
      env, NativeToJavaString(env, address), port, NativeToJavaString(env, url),
      error_code, NativeToJavaString(env, error_text));
  Java_Observer_onIceCandidateError(env, j_observer_global_, event);
}

void PeerConnectionObserverJni::OnIceCandidatesRemoved(
    const std::vector<cricket::Candidate>& candidates) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onIceCandidatesRemoved(
      env, j_observer_global_, NativeToJavaCandidateArray(env, candidates));
}

void PeerConnectionObserverJni::OnSignalingChange(
    PeerConnectionInterface::SignalingState new_state) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onSignalingChange(
      env, j_observer_global_,
      Java_SignalingState_fromNativeIndex(env, new_state));
}

void PeerConnectionObserverJni::OnIceConnectionChange(
    PeerConnectionInterface::IceConnectionState new_state) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onIceConnectionChange(
      env, j_observer_global_,
      Java_IceConnectionState_fromNativeIndex(env, new_state));
}

void PeerConnectionObserverJni::OnStandardizedIceConnectionChange(
    PeerConnectionInterface::IceConnectionState new_state) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onStandardizedIceConnectionChange(
      env, j_observer_global_,
      Java_IceConnectionState_fromNativeIndex(env, new_state));
}

void PeerConnectionObserverJni::OnConnectionChange(
    PeerConnectionInterface::PeerConnectionState new_state) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onConnectionChange(env, j_observer_global_,
                                   Java_PeerConnectionState_fromNativeIndex(
                                       env, static_cast<int>(new_state)));
}

void PeerConnectionObserverJni::OnIceConnectionReceivingChange(bool receiving) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onIceConnectionReceivingChange(env, j_observer_global_,
                                               receiving);
}

void PeerConnectionObserverJni::OnIceSelectedCandidatePairChanged(
    const cricket::CandidatePairChangeEvent& event) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onSelectedCandidatePairChanged(
      env, j_observer_global_, NativeToJavaCandidatePairChange(env, event));
}

void PeerConnectionObserverJni::OnIceGatheringChange(
    PeerConnectionInterface::IceGatheringState new_state) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onIceGatheringChange(
      env, j_observer_global_,
      Java_IceGatheringState_fromNativeIndex(env, new_state));
}

void PeerConnectionObserverJni::OnAddStream(
    rtc::scoped_refptr<MediaStreamInterface> stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onAddStream(
      env, j_observer_global_,
      GetOrCreateJavaStream(env, stream).j_media_stream());
}

void PeerConnectionObserverJni::OnRemoveStream(
    rtc::scoped_refptr<MediaStreamInterface> stream) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  NativeToJavaStreamsMap::iterator it = remote_streams_.find(stream.get());
  RTC_CHECK(it != remote_streams_.end())
      << "unexpected stream: " << stream.get();
  Java_Observer_onRemoveStream(env, j_observer_global_,
                               it->second.j_media_stream());
  remote_streams_.erase(it);
}

void PeerConnectionObserverJni::OnDataChannel(
    rtc::scoped_refptr<DataChannelInterface> channel) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onDataChannel(env, j_observer_global_,
                              WrapNativeDataChannel(env, channel));
}

void PeerConnectionObserverJni::OnRenegotiationNeeded() {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  Java_Observer_onRenegotiationNeeded(env, j_observer_global_);
}

void PeerConnectionObserverJni::OnAddTrack(
    rtc::scoped_refptr<RtpReceiverInterface> receiver,
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_rtp_receiver =
      NativeToJavaRtpReceiver(env, receiver);
  rtp_receivers_.emplace_back(env, j_rtp_receiver);

  Java_Observer_onAddTrack(env, j_observer_global_, j_rtp_receiver,
                           NativeToJavaMediaStreamArray(env, streams));
}

void PeerConnectionObserverJni::OnRemoveTrack(
    rtc::scoped_refptr<RtpReceiverInterface> receiver) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_rtp_receiver =
      NativeToJavaRtpReceiver(env, receiver);
  rtp_receivers_.emplace_back(env, j_rtp_receiver);

  Java_Observer_onRemoveTrack(env, j_observer_global_, j_rtp_receiver);
}

void PeerConnectionObserverJni::OnTrack(
    rtc::scoped_refptr<RtpTransceiverInterface> transceiver) {
  JNIEnv* env = AttachCurrentThreadIfNeeded();
  ScopedJavaLocalRef<jobject> j_rtp_transceiver =
      NativeToJavaRtpTransceiver(env, transceiver);
  rtp_transceivers_.emplace_back(env, j_rtp_transceiver);

  Java_Observer_onTrack(env, j_observer_global_, j_rtp_transceiver);
}

// If the NativeToJavaStreamsMap contains the stream, return it.
// Otherwise, create a new Java MediaStream.
JavaMediaStream& PeerConnectionObserverJni::GetOrCreateJavaStream(
    JNIEnv* env,
    const rtc::scoped_refptr<MediaStreamInterface>& stream) {
  NativeToJavaStreamsMap::iterator it = remote_streams_.find(stream.get());
  if (it == remote_streams_.end()) {
    it = remote_streams_
             .emplace(std::piecewise_construct,
                      std::forward_as_tuple(stream.get()),
                      std::forward_as_tuple(env, stream))
             .first;
  }
  return it->second;
}

ScopedJavaLocalRef<jobjectArray>
PeerConnectionObserverJni::NativeToJavaMediaStreamArray(
    JNIEnv* jni,
    const std::vector<rtc::scoped_refptr<MediaStreamInterface>>& streams) {
  return NativeToJavaObjectArray(
      jni, streams, GetMediaStreamClass(jni),
      [this](JNIEnv* env, rtc::scoped_refptr<MediaStreamInterface> stream)
          -> const ScopedJavaGlobalRef<jobject>& {
        return GetOrCreateJavaStream(env, stream).j_media_stream();
      });
}

OwnedPeerConnection::OwnedPeerConnection(
    rtc::scoped_refptr<PeerConnectionInterface> peer_connection,
    std::unique_ptr<PeerConnectionObserver> observer)
    : OwnedPeerConnection(peer_connection,
                          std::move(observer),
                          nullptr /* constraints */) {}

OwnedPeerConnection::OwnedPeerConnection(
    rtc::scoped_refptr<PeerConnectionInterface> peer_connection,
    std::unique_ptr<PeerConnectionObserver> observer,
    std::unique_ptr<MediaConstraints> constraints)
    : peer_connection_(peer_connection),
      observer_(std::move(observer)),
      constraints_(std::move(constraints)) {}

OwnedPeerConnection::~OwnedPeerConnection() {
  // Ensure that PeerConnection is destroyed before the observer.
  peer_connection_ = nullptr;
}

static jlong JNI_PeerConnection_CreatePeerConnectionObserver(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_observer) {
  return jlongFromPointer(new PeerConnectionObserverJni(jni, j_observer));
}

static void JNI_PeerConnection_FreeOwnedPeerConnection(JNIEnv*, jlong j_p) {
  delete reinterpret_cast<OwnedPeerConnection*>(j_p);
}

static jlong JNI_PeerConnection_GetNativePeerConnection(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  return jlongFromPointer(ExtractNativePC(jni, j_pc));
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetLocalDescription(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  PeerConnectionInterface* pc = ExtractNativePC(jni, j_pc);
  // It's only safe to operate on SessionDescriptionInterface on the
  // signaling thread, but `jni` may only be used on the current thread, so we
  // must do this odd dance.
  std::string sdp;
  std::string type;
  pc->signaling_thread()->BlockingCall([pc, &sdp, &type] {
    const SessionDescriptionInterface* desc = pc->local_description();
    if (desc) {
      RTC_CHECK(desc->ToString(&sdp)) << "got so far: " << sdp;
      type = desc->type();
    }
  });
  return sdp.empty() ? nullptr : NativeToJavaSessionDescription(jni, sdp, type);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetRemoteDescription(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  PeerConnectionInterface* pc = ExtractNativePC(jni, j_pc);
  // It's only safe to operate on SessionDescriptionInterface on the
  // signaling thread, but `jni` may only be used on the current thread, so we
  // must do this odd dance.
  std::string sdp;
  std::string type;
  pc->signaling_thread()->BlockingCall([pc, &sdp, &type] {
    const SessionDescriptionInterface* desc = pc->remote_description();
    if (desc) {
      RTC_CHECK(desc->ToString(&sdp)) << "got so far: " << sdp;
      type = desc->type();
    }
  });
  return sdp.empty() ? nullptr : NativeToJavaSessionDescription(jni, sdp, type);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetCertificate(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  const PeerConnectionInterface::RTCConfiguration rtc_config =
      ExtractNativePC(jni, j_pc)->GetConfiguration();
  rtc::scoped_refptr<rtc::RTCCertificate> certificate =
      rtc_config.certificates[0];
  return NativeToJavaRTCCertificatePEM(jni, certificate->ToPEM());
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_CreateDataChannel(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jstring>& j_label,
    const JavaParamRef<jobject>& j_init) {
  DataChannelInit init = JavaToNativeDataChannelInit(jni, j_init);
  auto result = ExtractNativePC(jni, j_pc)->CreateDataChannelOrError(
      JavaToNativeString(jni, j_label), &init);
  if (!result.ok()) {
    return WrapNativeDataChannel(jni, nullptr);
  }
  return WrapNativeDataChannel(jni, result.MoveValue());
}

static void JNI_PeerConnection_CreateOffer(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer,
    const JavaParamRef<jobject>& j_constraints) {
  std::unique_ptr<MediaConstraints> constraints =
      JavaToNativeMediaConstraints(jni, j_constraints);
  auto observer = rtc::make_ref_counted<CreateSdpObserverJni>(
      jni, j_observer, std::move(constraints));
  PeerConnectionInterface::RTCOfferAnswerOptions options;
  CopyConstraintsIntoOfferAnswerOptions(observer->constraints(), &options);
  ExtractNativePC(jni, j_pc)->CreateOffer(observer.get(), options);
}

static void JNI_PeerConnection_CreateAnswer(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer,
    const JavaParamRef<jobject>& j_constraints) {
  std::unique_ptr<MediaConstraints> constraints =
      JavaToNativeMediaConstraints(jni, j_constraints);
  auto observer = rtc::make_ref_counted<CreateSdpObserverJni>(
      jni, j_observer, std::move(constraints));
  PeerConnectionInterface::RTCOfferAnswerOptions options;
  CopyConstraintsIntoOfferAnswerOptions(observer->constraints(), &options);
  ExtractNativePC(jni, j_pc)->CreateAnswer(observer.get(), options);
}

static void JNI_PeerConnection_SetLocalDescriptionAutomatically(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer) {
  auto observer =
      rtc::make_ref_counted<SetLocalSdpObserverJni>(jni, j_observer);
  ExtractNativePC(jni, j_pc)->SetLocalDescription(observer);
}

static void JNI_PeerConnection_SetLocalDescription(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer,
    const JavaParamRef<jobject>& j_sdp) {
  auto observer =
      rtc::make_ref_counted<SetLocalSdpObserverJni>(jni, j_observer);
  ExtractNativePC(jni, j_pc)->SetLocalDescription(
      JavaToNativeSessionDescription(jni, j_sdp), observer);
}

static void JNI_PeerConnection_SetRemoteDescription(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer,
    const JavaParamRef<jobject>& j_sdp) {
  auto observer =
      rtc::make_ref_counted<SetRemoteSdpObserverJni>(jni, j_observer);
  ExtractNativePC(jni, j_pc)->SetRemoteDescription(
      JavaToNativeSessionDescription(jni, j_sdp), observer);
}

static void JNI_PeerConnection_RestartIce(JNIEnv* jni,
                                          const JavaParamRef<jobject>& j_pc) {
  ExtractNativePC(jni, j_pc)->RestartIce();
}

static void JNI_PeerConnection_SetAudioPlayout(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jboolean playout) {
  ExtractNativePC(jni, j_pc)->SetAudioPlayout(playout);
}

static void JNI_PeerConnection_SetAudioRecording(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jboolean recording) {
  ExtractNativePC(jni, j_pc)->SetAudioRecording(recording);
}

static jboolean JNI_PeerConnection_SetConfiguration(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_rtc_config) {
  // Need to merge constraints into RTCConfiguration again, which are stored
  // in the OwnedPeerConnection object.
  OwnedPeerConnection* owned_pc = reinterpret_cast<OwnedPeerConnection*>(
      Java_PeerConnection_getNativeOwnedPeerConnection(jni, j_pc));
  PeerConnectionInterface::RTCConfiguration rtc_config(
      PeerConnectionInterface::RTCConfigurationType::kAggressive);
  JavaToNativeRTCConfiguration(jni, j_rtc_config, &rtc_config);
  if (owned_pc->constraints()) {
    CopyConstraintsIntoRtcConfiguration(owned_pc->constraints(), &rtc_config);
  }
  return owned_pc->pc()->SetConfiguration(rtc_config).ok();
}

static jboolean JNI_PeerConnection_AddIceCandidate(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jstring>& j_sdp_mid,
    jint j_sdp_mline_index,
    const JavaParamRef<jstring>& j_candidate_sdp) {
  std::string sdp_mid = JavaToNativeString(jni, j_sdp_mid);
  std::string sdp = JavaToNativeString(jni, j_candidate_sdp);
  std::unique_ptr<IceCandidateInterface> candidate(
      CreateIceCandidate(sdp_mid, j_sdp_mline_index, sdp, nullptr));
  return ExtractNativePC(jni, j_pc)->AddIceCandidate(candidate.get());
}

static void JNI_PeerConnection_AddIceCandidateWithObserver(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jstring>& j_sdp_mid,
    jint j_sdp_mline_index,
    const JavaParamRef<jstring>& j_candidate_sdp,
    const JavaParamRef<jobject>& j_observer) {
  std::string sdp_mid = JavaToNativeString(jni, j_sdp_mid);
  std::string sdp = JavaToNativeString(jni, j_candidate_sdp);
  std::unique_ptr<IceCandidateInterface> candidate(
      CreateIceCandidate(sdp_mid, j_sdp_mline_index, sdp, nullptr));

  rtc::scoped_refptr<AddIceCandidateObserverJni> observer(
      new AddIceCandidateObserverJni(jni, j_observer));
  ExtractNativePC(jni, j_pc)->AddIceCandidate(
      std::move(candidate),
      [observer](RTCError error) { observer->OnComplete(error); });
}

static jboolean JNI_PeerConnection_RemoveIceCandidates(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobjectArray>& j_candidates) {
  std::vector<cricket::Candidate> candidates =
      JavaToNativeVector<cricket::Candidate>(jni, j_candidates,
                                             &JavaToNativeCandidate);
  return ExtractNativePC(jni, j_pc)->RemoveIceCandidates(candidates);
}

static jboolean JNI_PeerConnection_AddLocalStream(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_stream) {
  return ExtractNativePC(jni, j_pc)->AddStream(
      reinterpret_cast<MediaStreamInterface*>(native_stream));
}

static void JNI_PeerConnection_RemoveLocalStream(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_stream) {
  ExtractNativePC(jni, j_pc)->RemoveStream(
      reinterpret_cast<MediaStreamInterface*>(native_stream));
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_CreateSender(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jstring>& j_kind,
    const JavaParamRef<jstring>& j_stream_id) {
  std::string kind = JavaToNativeString(jni, j_kind);
  std::string stream_id = JavaToNativeString(jni, j_stream_id);
  rtc::scoped_refptr<RtpSenderInterface> sender =
      ExtractNativePC(jni, j_pc)->CreateSender(kind, stream_id);
  return NativeToJavaRtpSender(jni, sender);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetSenders(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  return NativeToJavaList(jni, ExtractNativePC(jni, j_pc)->GetSenders(),
                          &NativeToJavaRtpSender);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetReceivers(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  return NativeToJavaList(jni, ExtractNativePC(jni, j_pc)->GetReceivers(),
                          &NativeToJavaRtpReceiver);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_GetTransceivers(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  return NativeToJavaList(jni, ExtractNativePC(jni, j_pc)->GetTransceivers(),
                          &NativeToJavaRtpTransceiver);
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_AddTrack(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const jlong native_track,
    const JavaParamRef<jobject>& j_stream_labels) {
  RTCErrorOr<rtc::scoped_refptr<RtpSenderInterface>> result =
      ExtractNativePC(jni, j_pc)->AddTrack(
          rtc::scoped_refptr<MediaStreamTrackInterface>(
              reinterpret_cast<MediaStreamTrackInterface*>(native_track)),
          JavaListToNativeVector<std::string, jstring>(jni, j_stream_labels,
                                                       &JavaToNativeString));
  if (!result.ok()) {
    RTC_LOG(LS_ERROR) << "Failed to add track: " << result.error().message();
    return nullptr;
  } else {
    return NativeToJavaRtpSender(jni, result.MoveValue());
  }
}

static jboolean JNI_PeerConnection_RemoveTrack(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_sender) {
  return ExtractNativePC(jni, j_pc)
      ->RemoveTrackOrError(rtc::scoped_refptr<RtpSenderInterface>(
          reinterpret_cast<RtpSenderInterface*>(native_sender)))
      .ok();
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_AddTransceiverWithTrack(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_track,
    const JavaParamRef<jobject>& j_init) {
  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> result =
      ExtractNativePC(jni, j_pc)->AddTransceiver(
          rtc::scoped_refptr<MediaStreamTrackInterface>(
              reinterpret_cast<MediaStreamTrackInterface*>(native_track)),
          JavaToNativeRtpTransceiverInit(jni, j_init));
  if (!result.ok()) {
    RTC_LOG(LS_ERROR) << "Failed to add transceiver: "
                      << result.error().message();
    return nullptr;
  } else {
    return NativeToJavaRtpTransceiver(jni, result.MoveValue());
  }
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_AddTransceiverOfType(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_media_type,
    const JavaParamRef<jobject>& j_init) {
  RTCErrorOr<rtc::scoped_refptr<RtpTransceiverInterface>> result =
      ExtractNativePC(jni, j_pc)->AddTransceiver(
          JavaToNativeMediaType(jni, j_media_type),
          JavaToNativeRtpTransceiverInit(jni, j_init));
  if (!result.ok()) {
    RTC_LOG(LS_ERROR) << "Failed to add transceiver: "
                      << result.error().message();
    return nullptr;
  } else {
    return NativeToJavaRtpTransceiver(jni, result.MoveValue());
  }
}

static jboolean JNI_PeerConnection_OldGetStats(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_observer,
    jlong native_track) {
  auto observer = rtc::make_ref_counted<StatsObserverJni>(jni, j_observer);
  return ExtractNativePC(jni, j_pc)->GetStats(
      observer.get(),
      reinterpret_cast<MediaStreamTrackInterface*>(native_track),
      PeerConnectionInterface::kStatsOutputLevelStandard);
}

static void JNI_PeerConnection_NewGetStats(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_callback) {
  auto callback =
      rtc::make_ref_counted<RTCStatsCollectorCallbackWrapper>(jni, j_callback);
  ExtractNativePC(jni, j_pc)->GetStats(callback.get());
}

static void JNI_PeerConnection_NewGetStatsSender(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_sender,
    const JavaParamRef<jobject>& j_callback) {
  auto callback =
      rtc::make_ref_counted<RTCStatsCollectorCallbackWrapper>(jni, j_callback);
  ExtractNativePC(jni, j_pc)->GetStats(
      rtc::scoped_refptr<RtpSenderInterface>(
          reinterpret_cast<RtpSenderInterface*>(native_sender)),
      rtc::scoped_refptr<RTCStatsCollectorCallbackWrapper>(callback.get()));
}

static void JNI_PeerConnection_NewGetStatsReceiver(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    jlong native_receiver,
    const JavaParamRef<jobject>& j_callback) {
  auto callback =
      rtc::make_ref_counted<RTCStatsCollectorCallbackWrapper>(jni, j_callback);
  ExtractNativePC(jni, j_pc)->GetStats(
      rtc::scoped_refptr<RtpReceiverInterface>(
          reinterpret_cast<RtpReceiverInterface*>(native_receiver)),
      rtc::scoped_refptr<RTCStatsCollectorCallbackWrapper>(callback.get()));
}

static jboolean JNI_PeerConnection_SetBitrate(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    const JavaParamRef<jobject>& j_min,
    const JavaParamRef<jobject>& j_current,
    const JavaParamRef<jobject>& j_max) {
  BitrateSettings params;
  params.min_bitrate_bps = JavaToNativeOptionalInt(jni, j_min);
  params.start_bitrate_bps = JavaToNativeOptionalInt(jni, j_current);
  params.max_bitrate_bps = JavaToNativeOptionalInt(jni, j_max);
  return ExtractNativePC(jni, j_pc)->SetBitrate(params).ok();
}

static jboolean JNI_PeerConnection_StartRtcEventLog(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc,
    int file_descriptor,
    int max_size_bytes) {
  // TODO(eladalon): It would be better to not allow negative values into PC.
  const size_t max_size = (max_size_bytes < 0)
                              ? RtcEventLog::kUnlimitedOutput
                              : rtc::saturated_cast<size_t>(max_size_bytes);
  FILE* f = fdopen(file_descriptor, "wb");
  if (!f) {
    close(file_descriptor);
    return false;
  }
  return ExtractNativePC(jni, j_pc)->StartRtcEventLog(
      std::make_unique<RtcEventLogOutputFile>(f, max_size));
}

static void JNI_PeerConnection_StopRtcEventLog(
    JNIEnv* jni,
    const JavaParamRef<jobject>& j_pc) {
  ExtractNativePC(jni, j_pc)->StopRtcEventLog();
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_SignalingState(
    JNIEnv* env,
    const JavaParamRef<jobject>& j_pc) {
  return Java_SignalingState_fromNativeIndex(
      env, ExtractNativePC(env, j_pc)->signaling_state());
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_IceConnectionState(
    JNIEnv* env,
    const JavaParamRef<jobject>& j_pc) {
  return Java_IceConnectionState_fromNativeIndex(
      env, ExtractNativePC(env, j_pc)->ice_connection_state());
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_ConnectionState(
    JNIEnv* env,
    const JavaParamRef<jobject>& j_pc) {
  return Java_PeerConnectionState_fromNativeIndex(
      env,
      static_cast<int>(ExtractNativePC(env, j_pc)->peer_connection_state()));
}

static ScopedJavaLocalRef<jobject> JNI_PeerConnection_IceGatheringState(
    JNIEnv* env,
    const JavaParamRef<jobject>& j_pc) {
  return Java_IceGatheringState_fromNativeIndex(
      env, ExtractNativePC(env, j_pc)->ice_gathering_state());
}

static void JNI_PeerConnection_Close(JNIEnv* jni,
                                     const JavaParamRef<jobject>& j_pc) {
  ExtractNativePC(jni, j_pc)->Close();
}

}  // namespace jni
}  // namespace webrtc
