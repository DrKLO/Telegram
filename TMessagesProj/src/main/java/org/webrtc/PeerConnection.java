/*
 *  Copyright 2013 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpTransceiver;

/**
 * Java-land version of the PeerConnection APIs; wraps the C++ API
 * http://www.webrtc.org/reference/native-apis, which in turn is inspired by the
 * JS APIs: http://dev.w3.org/2011/webrtc/editor/webrtc.html and
 * http://www.w3.org/TR/mediacapture-streams/
 */
public class PeerConnection {
  /** Tracks PeerConnectionInterface::IceGatheringState */
  public enum IceGatheringState {
    NEW,
    GATHERING,
    COMPLETE;

    @CalledByNative("IceGatheringState")
    static IceGatheringState fromNativeIndex(int nativeIndex) {
      return values()[nativeIndex];
    }
  }

  /** Tracks PeerConnectionInterface::IceConnectionState */
  public enum IceConnectionState {
    NEW,
    CHECKING,
    CONNECTED,
    COMPLETED,
    FAILED,
    DISCONNECTED,
    CLOSED;

    @CalledByNative("IceConnectionState")
    static IceConnectionState fromNativeIndex(int nativeIndex) {
      return values()[nativeIndex];
    }
  }

  /** Tracks PeerConnectionInterface::PeerConnectionState */
  public enum PeerConnectionState {
    NEW,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    FAILED,
    CLOSED;

    @CalledByNative("PeerConnectionState")
    static PeerConnectionState fromNativeIndex(int nativeIndex) {
      return values()[nativeIndex];
    }
  }

  /** Tracks PeerConnectionInterface::TlsCertPolicy */
  public enum TlsCertPolicy {
    TLS_CERT_POLICY_SECURE,
    TLS_CERT_POLICY_INSECURE_NO_CHECK,
  }

  /** Tracks PeerConnectionInterface::SignalingState */
  public enum SignalingState {
    STABLE,
    HAVE_LOCAL_OFFER,
    HAVE_LOCAL_PRANSWER,
    HAVE_REMOTE_OFFER,
    HAVE_REMOTE_PRANSWER,
    CLOSED;

    @CalledByNative("SignalingState")
    static SignalingState fromNativeIndex(int nativeIndex) {
      return values()[nativeIndex];
    }
  }

  /** Java version of PeerConnectionObserver. */
  public static interface Observer {
    /** Triggered when the SignalingState changes. */
    @CalledByNative("Observer") void onSignalingChange(SignalingState newState);

    /** Triggered when the IceConnectionState changes. */
    @CalledByNative("Observer") void onIceConnectionChange(IceConnectionState newState);

    /* Triggered when the standard-compliant state transition of IceConnectionState happens. */
    @CalledByNative("Observer")
    default void onStandardizedIceConnectionChange(IceConnectionState newState) {}

    /** Triggered when the PeerConnectionState changes. */
    @CalledByNative("Observer")
    default void onConnectionChange(PeerConnectionState newState) {}

    /** Triggered when the ICE connection receiving status changes. */
    @CalledByNative("Observer") void onIceConnectionReceivingChange(boolean receiving);

    /** Triggered when the IceGatheringState changes. */
    @CalledByNative("Observer") void onIceGatheringChange(IceGatheringState newState);

    /** Triggered when a new ICE candidate has been found. */
    @CalledByNative("Observer") void onIceCandidate(IceCandidate candidate);

    /** Triggered when some ICE candidates have been removed. */
    @CalledByNative("Observer") void onIceCandidatesRemoved(IceCandidate[] candidates);

    /** Triggered when the ICE candidate pair is changed. */
    @CalledByNative("Observer")
    default void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {}

    /** Triggered when media is received on a new stream from remote peer. */
    @CalledByNative("Observer") void onAddStream(MediaStream stream);

    /** Triggered when a remote peer close a stream. */
    @CalledByNative("Observer") void onRemoveStream(MediaStream stream);

    /** Triggered when a remote peer opens a DataChannel. */
    @CalledByNative("Observer") void onDataChannel(DataChannel dataChannel);

    /** Triggered when renegotiation is necessary. */
    @CalledByNative("Observer") void onRenegotiationNeeded();

    /**
     * Triggered when a new track is signaled by the remote peer, as a result of
     * setRemoteDescription.
     */
    @CalledByNative("Observer") void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams);

    /**
     * Triggered when the signaling from SetRemoteDescription indicates that a transceiver
     * will be receiving media from a remote endpoint. This is only called if UNIFIED_PLAN
     * semantics are specified. The transceiver will be disposed automatically.
     */
    @CalledByNative("Observer") default void onTrack(RtpTransceiver transceiver){};
  }

  /** Java version of PeerConnectionInterface.IceServer. */
  public static class IceServer {
    // List of URIs associated with this server. Valid formats are described
    // in RFC7064 and RFC7065, and more may be added in the future. The "host"
    // part of the URI may contain either an IP address or a hostname.
    @Deprecated public final String uri;
    public final List<String> urls;
    public final String username;
    public final String password;
    public final TlsCertPolicy tlsCertPolicy;

    // If the URIs in |urls| only contain IP addresses, this field can be used
    // to indicate the hostname, which may be necessary for TLS (using the SNI
    // extension). If |urls| itself contains the hostname, this isn't
    // necessary.
    public final String hostname;

    // List of protocols to be used in the TLS ALPN extension.
    public final List<String> tlsAlpnProtocols;

    // List of elliptic curves to be used in the TLS elliptic curves extension.
    // Only curve names supported by OpenSSL should be used (eg. "P-256","X25519").
    public final List<String> tlsEllipticCurves;

    /** Convenience constructor for STUN servers. */
    @Deprecated
    public IceServer(String uri) {
      this(uri, "", "");
    }

    @Deprecated
    public IceServer(String uri, String username, String password) {
      this(uri, username, password, TlsCertPolicy.TLS_CERT_POLICY_SECURE);
    }

    @Deprecated
    public IceServer(String uri, String username, String password, TlsCertPolicy tlsCertPolicy) {
      this(uri, username, password, tlsCertPolicy, "");
    }

    @Deprecated
    public IceServer(String uri, String username, String password, TlsCertPolicy tlsCertPolicy,
        String hostname) {
      this(uri, Collections.singletonList(uri), username, password, tlsCertPolicy, hostname, null,
          null);
    }

    private IceServer(String uri, List<String> urls, String username, String password,
        TlsCertPolicy tlsCertPolicy, String hostname, List<String> tlsAlpnProtocols,
        List<String> tlsEllipticCurves) {
      if (uri == null || urls == null || urls.isEmpty()) {
        throw new IllegalArgumentException("uri == null || urls == null || urls.isEmpty()");
      }
      for (String it : urls) {
        if (it == null) {
          throw new IllegalArgumentException("urls element is null: " + urls);
        }
      }
      if (username == null) {
        throw new IllegalArgumentException("username == null");
      }
      if (password == null) {
        throw new IllegalArgumentException("password == null");
      }
      if (hostname == null) {
        throw new IllegalArgumentException("hostname == null");
      }
      this.uri = uri;
      this.urls = urls;
      this.username = username;
      this.password = password;
      this.tlsCertPolicy = tlsCertPolicy;
      this.hostname = hostname;
      this.tlsAlpnProtocols = tlsAlpnProtocols;
      this.tlsEllipticCurves = tlsEllipticCurves;
    }

    @Override
    public String toString() {
      return urls + " [" + username + ":" + password + "] [" + tlsCertPolicy + "] [" + hostname
          + "] [" + tlsAlpnProtocols + "] [" + tlsEllipticCurves + "]";
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (obj == null) {
        return false;
      }
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof IceServer)) {
        return false;
      }
      IceServer other = (IceServer) obj;
      return (uri.equals(other.uri) && urls.equals(other.urls) && username.equals(other.username)
          && password.equals(other.password) && tlsCertPolicy.equals(other.tlsCertPolicy)
          && hostname.equals(other.hostname) && tlsAlpnProtocols.equals(other.tlsAlpnProtocols)
          && tlsEllipticCurves.equals(other.tlsEllipticCurves));
    }

    @Override
    public int hashCode() {
      Object[] values = {uri, urls, username, password, tlsCertPolicy, hostname, tlsAlpnProtocols,
          tlsEllipticCurves};
      return Arrays.hashCode(values);
    }

    public static Builder builder(String uri) {
      return new Builder(Collections.singletonList(uri));
    }

    public static Builder builder(List<String> urls) {
      return new Builder(urls);
    }

    public static class Builder {
      @Nullable private final List<String> urls;
      private String username = "";
      private String password = "";
      private TlsCertPolicy tlsCertPolicy = TlsCertPolicy.TLS_CERT_POLICY_SECURE;
      private String hostname = "";
      private List<String> tlsAlpnProtocols;
      private List<String> tlsEllipticCurves;

      private Builder(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
          throw new IllegalArgumentException("urls == null || urls.isEmpty(): " + urls);
        }
        this.urls = urls;
      }

      public Builder setUsername(String username) {
        this.username = username;
        return this;
      }

      public Builder setPassword(String password) {
        this.password = password;
        return this;
      }

      public Builder setTlsCertPolicy(TlsCertPolicy tlsCertPolicy) {
        this.tlsCertPolicy = tlsCertPolicy;
        return this;
      }

      public Builder setHostname(String hostname) {
        this.hostname = hostname;
        return this;
      }

      public Builder setTlsAlpnProtocols(List<String> tlsAlpnProtocols) {
        this.tlsAlpnProtocols = tlsAlpnProtocols;
        return this;
      }

      public Builder setTlsEllipticCurves(List<String> tlsEllipticCurves) {
        this.tlsEllipticCurves = tlsEllipticCurves;
        return this;
      }

      public IceServer createIceServer() {
        return new IceServer(urls.get(0), urls, username, password, tlsCertPolicy, hostname,
            tlsAlpnProtocols, tlsEllipticCurves);
      }
    }

    @Nullable
    @CalledByNative("IceServer")
    List<String> getUrls() {
      return urls;
    }

    @Nullable
    @CalledByNative("IceServer")
    String getUsername() {
      return username;
    }

    @Nullable
    @CalledByNative("IceServer")
    String getPassword() {
      return password;
    }

    @CalledByNative("IceServer")
    TlsCertPolicy getTlsCertPolicy() {
      return tlsCertPolicy;
    }

    @Nullable
    @CalledByNative("IceServer")
    String getHostname() {
      return hostname;
    }

    @CalledByNative("IceServer")
    List<String> getTlsAlpnProtocols() {
      return tlsAlpnProtocols;
    }

    @CalledByNative("IceServer")
    List<String> getTlsEllipticCurves() {
      return tlsEllipticCurves;
    }
  }

  /** Java version of PeerConnectionInterface.IceTransportsType */
  public enum IceTransportsType { NONE, RELAY, NOHOST, ALL }

  /** Java version of PeerConnectionInterface.BundlePolicy */
  public enum BundlePolicy { BALANCED, MAXBUNDLE, MAXCOMPAT }

  /** Java version of PeerConnectionInterface.RtcpMuxPolicy */
  public enum RtcpMuxPolicy { NEGOTIATE, REQUIRE }

  /** Java version of PeerConnectionInterface.TcpCandidatePolicy */
  public enum TcpCandidatePolicy { ENABLED, DISABLED }

  /** Java version of PeerConnectionInterface.CandidateNetworkPolicy */
  public enum CandidateNetworkPolicy { ALL, LOW_COST }

  // Keep in sync with webrtc/rtc_base/network_constants.h.
  public enum AdapterType {
    UNKNOWN(0),
    ETHERNET(1 << 0),
    WIFI(1 << 1),
    CELLULAR(1 << 2),
    VPN(1 << 3),
    LOOPBACK(1 << 4),
    ADAPTER_TYPE_ANY(1 << 5),
    CELLULAR_2G(1 << 6),
    CELLULAR_3G(1 << 7),
    CELLULAR_4G(1 << 8),
    CELLULAR_5G(1 << 9);

    public final Integer bitMask;
    private AdapterType(Integer bitMask) {
      this.bitMask = bitMask;
    }
    private static final Map<Integer, AdapterType> BY_BITMASK = new HashMap<>();
    static {
      for (AdapterType t : values()) {
        BY_BITMASK.put(t.bitMask, t);
      }
    }

    @Nullable
    @CalledByNative("AdapterType")
    static AdapterType fromNativeIndex(int nativeIndex) {
      return BY_BITMASK.get(nativeIndex);
    }
  }

  /** Java version of rtc::KeyType */
  public enum KeyType { RSA, ECDSA }

  /** Java version of PeerConnectionInterface.ContinualGatheringPolicy */
  public enum ContinualGatheringPolicy { GATHER_ONCE, GATHER_CONTINUALLY }

  /** Java version of webrtc::PortPrunePolicy */
  public enum PortPrunePolicy {
    NO_PRUNE, // Do not prune turn port.
    PRUNE_BASED_ON_PRIORITY, // Prune turn port based the priority on the same network
    KEEP_FIRST_READY // Keep the first ready port and prune the rest on the same network.
  }

  /**
   * Java version of webrtc::SdpSemantics.
   *
   * Configure the SDP semantics used by this PeerConnection. Note that the
   * WebRTC 1.0 specification requires UNIFIED_PLAN semantics. The
   * RtpTransceiver API is only available with UNIFIED_PLAN semantics.
   *
   * <p>PLAN_B will cause PeerConnection to create offers and answers with at
   * most one audio and one video m= section with multiple RtpSenders and
   * RtpReceivers specified as multiple a=ssrc lines within the section. This
   * will also cause PeerConnection to ignore all but the first m= section of
   * the same media type.
   *
   * <p>UNIFIED_PLAN will cause PeerConnection to create offers and answers with
   * multiple m= sections where each m= section maps to one RtpSender and one
   * RtpReceiver (an RtpTransceiver), either both audio or both video. This
   * will also cause PeerConnection to ignore all but the first a=ssrc lines
   * that form a Plan B stream.
   *
   * <p>For users who wish to send multiple audio/video streams and need to stay
   * interoperable with legacy WebRTC implementations, specify PLAN_B.
   *
   * <p>For users who wish to send multiple audio/video streams and/or wish to
   * use the new RtpTransceiver API, specify UNIFIED_PLAN.
   */
  public enum SdpSemantics { PLAN_B, UNIFIED_PLAN }

  /** Java version of PeerConnectionInterface.RTCConfiguration */
  // TODO(qingsi): Resolve the naming inconsistency of fields with/without units.
  public static class RTCConfiguration {
    public IceTransportsType iceTransportsType;
    public List<IceServer> iceServers;
    public BundlePolicy bundlePolicy;
    @Nullable public RtcCertificatePem certificate;
    public RtcpMuxPolicy rtcpMuxPolicy;
    public TcpCandidatePolicy tcpCandidatePolicy;
    public CandidateNetworkPolicy candidateNetworkPolicy;
    public int audioJitterBufferMaxPackets;
    public boolean audioJitterBufferFastAccelerate;
    public int iceConnectionReceivingTimeout;
    public int iceBackupCandidatePairPingInterval;
    public KeyType keyType;
    public ContinualGatheringPolicy continualGatheringPolicy;
    public int iceCandidatePoolSize;
    @Deprecated // by the turnPortPrunePolicy. See bugs.webrtc.org/11026
    public boolean pruneTurnPorts;
    public PortPrunePolicy turnPortPrunePolicy;
    public boolean presumeWritableWhenFullyRelayed;
    public boolean surfaceIceCandidatesOnIceTransportTypeChanged;
    // The following fields define intervals in milliseconds at which ICE
    // connectivity checks are sent.
    //
    // We consider ICE is "strongly connected" for an agent when there is at
    // least one candidate pair that currently succeeds in connectivity check
    // from its direction i.e. sending a ping and receives a ping response, AND
    // all candidate pairs have sent a minimum number of pings for connectivity
    // (this number is implementation-specific). Otherwise, ICE is considered in
    // "weak connectivity".
    //
    // Note that the above notion of strong and weak connectivity is not defined
    // in RFC 5245, and they apply to our current ICE implementation only.
    //
    // 1) iceCheckIntervalStrongConnectivityMs defines the interval applied to
    // ALL candidate pairs when ICE is strongly connected,
    // 2) iceCheckIntervalWeakConnectivityMs defines the counterpart for ALL
    // pairs when ICE is weakly connected, and
    // 3) iceCheckMinInterval defines the minimal interval (equivalently the
    // maximum rate) that overrides the above two intervals when either of them
    // is less.
    @Nullable public Integer iceCheckIntervalStrongConnectivityMs;
    @Nullable public Integer iceCheckIntervalWeakConnectivityMs;
    @Nullable public Integer iceCheckMinInterval;
    // The time period in milliseconds for which a candidate pair must wait for response to
    // connectivitiy checks before it becomes unwritable.
    @Nullable public Integer iceUnwritableTimeMs;
    // The minimum number of connectivity checks that a candidate pair must sent without receiving
    // response before it becomes unwritable.
    @Nullable public Integer iceUnwritableMinChecks;
    // The interval in milliseconds at which STUN candidates will resend STUN binding requests
    // to keep NAT bindings open.
    // The default value in the implementation is used if this field is null.
    @Nullable public Integer stunCandidateKeepaliveIntervalMs;
    public boolean disableIPv6OnWifi;
    // By default, PeerConnection will use a limited number of IPv6 network
    // interfaces, in order to avoid too many ICE candidate pairs being created
    // and delaying ICE completion.
    //
    // Can be set to Integer.MAX_VALUE to effectively disable the limit.
    public int maxIPv6Networks;

    // These values will be overridden by MediaStream constraints if deprecated constraints-based
    // create peerconnection interface is used.
    public boolean disableIpv6;
    public boolean enableDscp;
    public boolean enableCpuOveruseDetection;
    public boolean enableRtpDataChannel;
    public boolean suspendBelowMinBitrate;
    @Nullable public Integer screencastMinBitrate;
    @Nullable public Boolean combinedAudioVideoBwe;
    @Nullable public Boolean enableDtlsSrtp;
    // Use "Unknown" to represent no preference of adapter types, not the
    // preference of adapters of unknown types.
    public AdapterType networkPreference;
    public SdpSemantics sdpSemantics;

    // This is an optional wrapper for the C++ webrtc::TurnCustomizer.
    @Nullable public TurnCustomizer turnCustomizer;

    // Actively reset the SRTP parameters whenever the DTLS transports underneath are reset for
    // every offer/answer negotiation.This is only intended to be a workaround for crbug.com/835958
    public boolean activeResetSrtpParams;

    // Whether this client is allowed to switch encoding codec mid-stream. This is a workaround for
    // a WebRTC bug where the receiver could get confussed if a codec switch happened mid-call.
    // Null indicates no change to currently configured value.
    @Nullable public Boolean allowCodecSwitching;

    /**
     * Defines advanced optional cryptographic settings related to SRTP and
     * frame encryption for native WebRTC. Setting this will overwrite any
     * options set through the PeerConnectionFactory (which is deprecated).
     */
    @Nullable public CryptoOptions cryptoOptions;

    /**
     * An optional string that if set will be attached to the
     * TURN_ALLOCATE_REQUEST which can be used to correlate client
     * logs with backend logs
     */
    @Nullable public String turnLoggingId;

    // TODO(deadbeef): Instead of duplicating the defaults here, we should do
    // something to pick up the defaults from C++. The Objective-C equivalent
    // of RTCConfiguration does that.
    public RTCConfiguration(List<IceServer> iceServers) {
      iceTransportsType = IceTransportsType.ALL;
      bundlePolicy = BundlePolicy.BALANCED;
      rtcpMuxPolicy = RtcpMuxPolicy.REQUIRE;
      tcpCandidatePolicy = TcpCandidatePolicy.ENABLED;
      candidateNetworkPolicy = CandidateNetworkPolicy.ALL;
      this.iceServers = iceServers;
      audioJitterBufferMaxPackets = 50;
      audioJitterBufferFastAccelerate = false;
      iceConnectionReceivingTimeout = -1;
      iceBackupCandidatePairPingInterval = -1;
      keyType = KeyType.ECDSA;
      continualGatheringPolicy = ContinualGatheringPolicy.GATHER_ONCE;
      iceCandidatePoolSize = 0;
      pruneTurnPorts = false;
      turnPortPrunePolicy = PortPrunePolicy.NO_PRUNE;
      presumeWritableWhenFullyRelayed = false;
      surfaceIceCandidatesOnIceTransportTypeChanged = false;
      iceCheckIntervalStrongConnectivityMs = null;
      iceCheckIntervalWeakConnectivityMs = null;
      iceCheckMinInterval = null;
      iceUnwritableTimeMs = null;
      iceUnwritableMinChecks = null;
      stunCandidateKeepaliveIntervalMs = null;
      disableIPv6OnWifi = false;
      maxIPv6Networks = 5;
      disableIpv6 = false;
      enableDscp = false;
      enableCpuOveruseDetection = true;
      enableRtpDataChannel = false;
      suspendBelowMinBitrate = false;
      screencastMinBitrate = null;
      combinedAudioVideoBwe = null;
      enableDtlsSrtp = null;
      networkPreference = AdapterType.UNKNOWN;
      sdpSemantics = SdpSemantics.PLAN_B;
      activeResetSrtpParams = false;
      cryptoOptions = null;
      turnLoggingId = null;
      allowCodecSwitching = null;
    }

    @CalledByNative("RTCConfiguration")
    IceTransportsType getIceTransportsType() {
      return iceTransportsType;
    }

    @CalledByNative("RTCConfiguration")
    List<IceServer> getIceServers() {
      return iceServers;
    }

    @CalledByNative("RTCConfiguration")
    BundlePolicy getBundlePolicy() {
      return bundlePolicy;
    }

    @CalledByNative("RTCConfiguration")
    PortPrunePolicy getTurnPortPrunePolicy() {
      return turnPortPrunePolicy;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    RtcCertificatePem getCertificate() {
      return certificate;
    }

    @CalledByNative("RTCConfiguration")
    RtcpMuxPolicy getRtcpMuxPolicy() {
      return rtcpMuxPolicy;
    }

    @CalledByNative("RTCConfiguration")
    TcpCandidatePolicy getTcpCandidatePolicy() {
      return tcpCandidatePolicy;
    }

    @CalledByNative("RTCConfiguration")
    CandidateNetworkPolicy getCandidateNetworkPolicy() {
      return candidateNetworkPolicy;
    }

    @CalledByNative("RTCConfiguration")
    int getAudioJitterBufferMaxPackets() {
      return audioJitterBufferMaxPackets;
    }

    @CalledByNative("RTCConfiguration")
    boolean getAudioJitterBufferFastAccelerate() {
      return audioJitterBufferFastAccelerate;
    }

    @CalledByNative("RTCConfiguration")
    int getIceConnectionReceivingTimeout() {
      return iceConnectionReceivingTimeout;
    }

    @CalledByNative("RTCConfiguration")
    int getIceBackupCandidatePairPingInterval() {
      return iceBackupCandidatePairPingInterval;
    }

    @CalledByNative("RTCConfiguration")
    KeyType getKeyType() {
      return keyType;
    }

    @CalledByNative("RTCConfiguration")
    ContinualGatheringPolicy getContinualGatheringPolicy() {
      return continualGatheringPolicy;
    }

    @CalledByNative("RTCConfiguration")
    int getIceCandidatePoolSize() {
      return iceCandidatePoolSize;
    }

    @CalledByNative("RTCConfiguration")
    boolean getPruneTurnPorts() {
      return pruneTurnPorts;
    }

    @CalledByNative("RTCConfiguration")
    boolean getPresumeWritableWhenFullyRelayed() {
      return presumeWritableWhenFullyRelayed;
    }

    @CalledByNative("RTCConfiguration")
    boolean getSurfaceIceCandidatesOnIceTransportTypeChanged() {
      return surfaceIceCandidatesOnIceTransportTypeChanged;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getIceCheckIntervalStrongConnectivity() {
      return iceCheckIntervalStrongConnectivityMs;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getIceCheckIntervalWeakConnectivity() {
      return iceCheckIntervalWeakConnectivityMs;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getIceCheckMinInterval() {
      return iceCheckMinInterval;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getIceUnwritableTimeout() {
      return iceUnwritableTimeMs;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getIceUnwritableMinChecks() {
      return iceUnwritableMinChecks;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getStunCandidateKeepaliveInterval() {
      return stunCandidateKeepaliveIntervalMs;
    }

    @CalledByNative("RTCConfiguration")
    boolean getDisableIPv6OnWifi() {
      return disableIPv6OnWifi;
    }

    @CalledByNative("RTCConfiguration")
    int getMaxIPv6Networks() {
      return maxIPv6Networks;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    TurnCustomizer getTurnCustomizer() {
      return turnCustomizer;
    }

    @CalledByNative("RTCConfiguration")
    boolean getDisableIpv6() {
      return disableIpv6;
    }

    @CalledByNative("RTCConfiguration")
    boolean getEnableDscp() {
      return enableDscp;
    }

    @CalledByNative("RTCConfiguration")
    boolean getEnableCpuOveruseDetection() {
      return enableCpuOveruseDetection;
    }

    @CalledByNative("RTCConfiguration")
    boolean getEnableRtpDataChannel() {
      return enableRtpDataChannel;
    }

    @CalledByNative("RTCConfiguration")
    boolean getSuspendBelowMinBitrate() {
      return suspendBelowMinBitrate;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Integer getScreencastMinBitrate() {
      return screencastMinBitrate;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Boolean getCombinedAudioVideoBwe() {
      return combinedAudioVideoBwe;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Boolean getEnableDtlsSrtp() {
      return enableDtlsSrtp;
    }

    @CalledByNative("RTCConfiguration")
    AdapterType getNetworkPreference() {
      return networkPreference;
    }

    @CalledByNative("RTCConfiguration")
    SdpSemantics getSdpSemantics() {
      return sdpSemantics;
    }

    @CalledByNative("RTCConfiguration")
    boolean getActiveResetSrtpParams() {
      return activeResetSrtpParams;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    Boolean getAllowCodecSwitching() {
      return allowCodecSwitching;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    CryptoOptions getCryptoOptions() {
      return cryptoOptions;
    }

    @Nullable
    @CalledByNative("RTCConfiguration")
    String getTurnLoggingId() {
      return turnLoggingId;
    }
  };

  private final List<MediaStream> localStreams = new ArrayList<>();
  private final long nativePeerConnection;
  private List<RtpSender> senders = new ArrayList<>();
  private List<RtpReceiver> receivers = new ArrayList<>();
  private List<RtpTransceiver> transceivers = new ArrayList<>();

  /**
   * Wraps a PeerConnection created by the factory. Can be used by clients that want to implement
   * their PeerConnection creation in JNI.
   */
  public PeerConnection(NativePeerConnectionFactory factory) {
    this(factory.createNativePeerConnection());
  }

  PeerConnection(long nativePeerConnection) {
    this.nativePeerConnection = nativePeerConnection;
  }

  // JsepInterface.
  public SessionDescription getLocalDescription() {
    return nativeGetLocalDescription();
  }

  public SessionDescription getRemoteDescription() {
    return nativeGetRemoteDescription();
  }

  public RtcCertificatePem getCertificate() {
    return nativeGetCertificate();
  }

  public DataChannel createDataChannel(String label, DataChannel.Init init) {
    return nativeCreateDataChannel(label, init);
  }

  public void createOffer(SdpObserver observer, MediaConstraints constraints) {
    nativeCreateOffer(observer, constraints);
  }

  public void createAnswer(SdpObserver observer, MediaConstraints constraints) {
    nativeCreateAnswer(observer, constraints);
  }

  public void setLocalDescription(SdpObserver observer, SessionDescription sdp) {
    nativeSetLocalDescription(observer, sdp);
  }

  public void setRemoteDescription(SdpObserver observer, SessionDescription sdp) {
    nativeSetRemoteDescription(observer, sdp);
  }

  /**
   * Enables/disables playout of received audio streams. Enabled by default.
   *
   * Note that even if playout is enabled, streams will only be played out if
   * the appropriate SDP is also applied. The main purpose of this API is to
   * be able to control the exact time when audio playout starts.
   */
  public void setAudioPlayout(boolean playout) {
    nativeSetAudioPlayout(playout);
  }

  /**
   * Enables/disables recording of transmitted audio streams. Enabled by default.
   *
   * Note that even if recording is enabled, streams will only be recorded if
   * the appropriate SDP is also applied. The main purpose of this API is to
   * be able to control the exact time when audio recording starts.
   */
  public void setAudioRecording(boolean recording) {
    nativeSetAudioRecording(recording);
  }

  public boolean setConfiguration(RTCConfiguration config) {
    return nativeSetConfiguration(config);
  }

  public boolean addIceCandidate(IceCandidate candidate) {
    return nativeAddIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
  }

  public boolean removeIceCandidates(final IceCandidate[] candidates) {
    return nativeRemoveIceCandidates(candidates);
  }

  /**
   * Adds a new MediaStream to be sent on this peer connection.
   * Note: This method is not supported with SdpSemantics.UNIFIED_PLAN. Please
   * use addTrack instead.
   */
  public boolean addStream(MediaStream stream) {
    boolean ret = nativeAddLocalStream(stream.getNativeMediaStream());
    if (!ret) {
      return false;
    }
    localStreams.add(stream);
    return true;
  }

  /**
   * Removes the given media stream from this peer connection.
   * This method is not supported with SdpSemantics.UNIFIED_PLAN. Please use
   * removeTrack instead.
   */
  public void removeStream(MediaStream stream) {
    nativeRemoveLocalStream(stream.getNativeMediaStream());
    localStreams.remove(stream);
  }

  /**
   * Creates an RtpSender without a track.
   *
   * <p>This method allows an application to cause the PeerConnection to negotiate
   * sending/receiving a specific media type, but without having a track to
   * send yet.
   *
   * <p>When the application does want to begin sending a track, it can call
   * RtpSender.setTrack, which doesn't require any additional SDP negotiation.
   *
   * <p>Example use:
   * <pre>
   * {@code
   * audioSender = pc.createSender("audio", "stream1");
   * videoSender = pc.createSender("video", "stream1");
   * // Do normal SDP offer/answer, which will kick off ICE/DTLS and negotiate
   * // media parameters....
   * // Later, when the endpoint is ready to actually begin sending:
   * audioSender.setTrack(audioTrack, false);
   * videoSender.setTrack(videoTrack, false);
   * }
   * </pre>
   * <p>Note: This corresponds most closely to "addTransceiver" in the official
   * WebRTC API, in that it creates a sender without a track. It was
   * implemented before addTransceiver because it provides useful
   * functionality, and properly implementing transceivers would have required
   * a great deal more work.
   *
   * <p>Note: This is only available with SdpSemantics.PLAN_B specified. Please use
   * addTransceiver instead.
   *
   * @param kind      Corresponds to MediaStreamTrack kinds (must be "audio" or
   *                  "video").
   * @param stream_id The ID of the MediaStream that this sender's track will
   *                  be associated with when SDP is applied to the remote
   *                  PeerConnection. If createSender is used to create an
   *                  audio and video sender that should be synchronized, they
   *                  should use the same stream ID.
   * @return          A new RtpSender object if successful, or null otherwise.
   */
  public RtpSender createSender(String kind, String stream_id) {
    RtpSender newSender = nativeCreateSender(kind, stream_id);
    if (newSender != null) {
      senders.add(newSender);
    }
    return newSender;
  }

  /**
   * Gets all RtpSenders associated with this peer connection.
   * Note that calling getSenders will dispose of the senders previously
   * returned.
   */
  public List<RtpSender> getSenders() {
    for (RtpSender sender : senders) {
      sender.dispose();
    }
    senders = nativeGetSenders();
    return Collections.unmodifiableList(senders);
  }

  /**
   * Gets all RtpReceivers associated with this peer connection.
   * Note that calling getReceivers will dispose of the receivers previously
   * returned.
   */
  public List<RtpReceiver> getReceivers() {
    for (RtpReceiver receiver : receivers) {
      receiver.dispose();
    }
    receivers = nativeGetReceivers();
    return Collections.unmodifiableList(receivers);
  }

  /**
   * Gets all RtpTransceivers associated with this peer connection.
   * Note that calling getTransceivers will dispose of the transceivers previously
   * returned.
   * Note: This is only available with SdpSemantics.UNIFIED_PLAN specified.
   */
  public List<RtpTransceiver> getTransceivers() {
    for (RtpTransceiver transceiver : transceivers) {
      transceiver.dispose();
    }
    transceivers = nativeGetTransceivers();
    return Collections.unmodifiableList(transceivers);
  }

  /**
   * Adds a new media stream track to be sent on this peer connection, and returns
   * the newly created RtpSender. If streamIds are specified, the RtpSender will
   * be associated with the streams specified in the streamIds list.
   *
   * @throws IllegalStateException if an error accors in C++ addTrack.
   *         An error can occur if:
   *           - A sender already exists for the track.
   *           - The peer connection is closed.
   */
  public RtpSender addTrack(MediaStreamTrack track) {
    return addTrack(track, Collections.emptyList());
  }

  public RtpSender addTrack(MediaStreamTrack track, List<String> streamIds) {
    if (track == null || streamIds == null) {
      throw new NullPointerException("No MediaStreamTrack specified in addTrack.");
    }
    RtpSender newSender = nativeAddTrack(track.getNativeMediaStreamTrack(), streamIds);
    if (newSender == null) {
      throw new IllegalStateException("C++ addTrack failed.");
    }
    senders.add(newSender);
    return newSender;
  }

  /**
   * Stops sending media from sender. The sender will still appear in getSenders. Future
   * calls to createOffer will mark the m section for the corresponding transceiver as
   * receive only or inactive, as defined in JSEP. Returns true on success.
   */
  public boolean removeTrack(RtpSender sender) {
    if (sender == null) {
      throw new NullPointerException("No RtpSender specified for removeTrack.");
    }
    return nativeRemoveTrack(sender.getNativeRtpSender());
  }

  /**
   * Creates a new RtpTransceiver and adds it to the set of transceivers. Adding a
   * transceiver will cause future calls to CreateOffer to add a media description
   * for the corresponding transceiver.
   *
   * <p>The initial value of |mid| in the returned transceiver is null. Setting a
   * new session description may change it to a non-null value.
   *
   * <p>https://w3c.github.io/webrtc-pc/#dom-rtcpeerconnection-addtransceiver
   *
   * <p>If a MediaStreamTrack is specified then a transceiver will be added with a
   * sender set to transmit the given track. The kind
   * of the transceiver (and sender/receiver) will be derived from the kind of
   * the track.
   *
   * <p>If MediaType is specified then a transceiver will be added based upon that type.
   * This can be either MEDIA_TYPE_AUDIO or MEDIA_TYPE_VIDEO.
   *
   * <p>Optionally, an RtpTransceiverInit structure can be specified to configure
   * the transceiver from construction. If not specified, the transceiver will
   * default to having a direction of kSendRecv and not be part of any streams.
   *
   * <p>Note: These methods are only available with SdpSemantics.UNIFIED_PLAN specified.
   * @throws IllegalStateException if an error accors in C++ addTransceiver
   */
  public RtpTransceiver addTransceiver(MediaStreamTrack track) {
    return addTransceiver(track, new RtpTransceiver.RtpTransceiverInit());
  }

  public RtpTransceiver addTransceiver(
      MediaStreamTrack track, @Nullable RtpTransceiver.RtpTransceiverInit init) {
    if (track == null) {
      throw new NullPointerException("No MediaStreamTrack specified for addTransceiver.");
    }
    if (init == null) {
      init = new RtpTransceiver.RtpTransceiverInit();
    }
    RtpTransceiver newTransceiver =
        nativeAddTransceiverWithTrack(track.getNativeMediaStreamTrack(), init);
    if (newTransceiver == null) {
      throw new IllegalStateException("C++ addTransceiver failed.");
    }
    transceivers.add(newTransceiver);
    return newTransceiver;
  }

  public RtpTransceiver addTransceiver(MediaStreamTrack.MediaType mediaType) {
    return addTransceiver(mediaType, new RtpTransceiver.RtpTransceiverInit());
  }

  public RtpTransceiver addTransceiver(
      MediaStreamTrack.MediaType mediaType, @Nullable RtpTransceiver.RtpTransceiverInit init) {
    if (mediaType == null) {
      throw new NullPointerException("No MediaType specified for addTransceiver.");
    }
    if (init == null) {
      init = new RtpTransceiver.RtpTransceiverInit();
    }
    RtpTransceiver newTransceiver = nativeAddTransceiverOfType(mediaType, init);
    if (newTransceiver == null) {
      throw new IllegalStateException("C++ addTransceiver failed.");
    }
    transceivers.add(newTransceiver);
    return newTransceiver;
  }

  // Older, non-standard implementation of getStats.
  @Deprecated
  public boolean getStats(StatsObserver observer, @Nullable MediaStreamTrack track) {
    return nativeOldGetStats(observer, (track == null) ? 0 : track.getNativeMediaStreamTrack());
  }

  /**
   * Gets stats using the new stats collection API, see webrtc/api/stats/. These
   * will replace old stats collection API when the new API has matured enough.
   */
  public void getStats(RTCStatsCollectorCallback callback) {
    nativeNewGetStats(callback);
  }

  /**
   * Limits the bandwidth allocated for all RTP streams sent by this
   * PeerConnection. Pass null to leave a value unchanged.
   */
  public boolean setBitrate(Integer min, Integer current, Integer max) {
    return nativeSetBitrate(min, current, max);
  }

  /**
   * Starts recording an RTC event log.
   *
   * Ownership of the file is transfered to the native code. If an RTC event
   * log is already being recorded, it will be stopped and a new one will start
   * using the provided file. Logging will continue until the stopRtcEventLog
   * function is called. The max_size_bytes argument is ignored, it is added
   * for future use.
   */
  public boolean startRtcEventLog(int file_descriptor, int max_size_bytes) {
    return nativeStartRtcEventLog(file_descriptor, max_size_bytes);
  }

  /**
   * Stops recording an RTC event log. If no RTC event log is currently being
   * recorded, this call will have no effect.
   */
  public void stopRtcEventLog() {
    nativeStopRtcEventLog();
  }

  // TODO(fischman): add support for DTMF-related methods once that API
  // stabilizes.
  public SignalingState signalingState() {
    return nativeSignalingState();
  }

  public IceConnectionState iceConnectionState() {
    return nativeIceConnectionState();
  }

  public PeerConnectionState connectionState() {
    return nativeConnectionState();
  }

  public IceGatheringState iceGatheringState() {
    return nativeIceGatheringState();
  }

  public void close() {
    nativeClose();
  }

  /**
   * Free native resources associated with this PeerConnection instance.
   *
   * This method removes a reference count from the C++ PeerConnection object,
   * which should result in it being destroyed. It also calls equivalent
   * "dispose" methods on the Java objects attached to this PeerConnection
   * (streams, senders, receivers), such that their associated C++ objects
   * will also be destroyed.
   *
   * <p>Note that this method cannot be safely called from an observer callback
   * (PeerConnection.Observer, DataChannel.Observer, etc.). If you want to, for
   * example, destroy the PeerConnection after an "ICE failed" callback, you
   * must do this asynchronously (in other words, unwind the stack first). See
   * <a href="https://bugs.chromium.org/p/webrtc/issues/detail?id=3721">bug
   * 3721</a> for more details.
   */
  public void dispose() {
    close();
    for (MediaStream stream : localStreams) {
      nativeRemoveLocalStream(stream.getNativeMediaStream());
      stream.dispose();
    }
    localStreams.clear();
    for (RtpSender sender : senders) {
      sender.dispose();
    }
    senders.clear();
    for (RtpReceiver receiver : receivers) {
      receiver.dispose();
    }
    for (RtpTransceiver transceiver : transceivers) {
      transceiver.dispose();
    }
    transceivers.clear();
    receivers.clear();
    nativeFreeOwnedPeerConnection(nativePeerConnection);
  }

  /** Returns a pointer to the native webrtc::PeerConnectionInterface. */
  public long getNativePeerConnection() {
    return nativeGetNativePeerConnection();
  }

  @CalledByNative
  long getNativeOwnedPeerConnection() {
    return nativePeerConnection;
  }

  public static long createNativePeerConnectionObserver(Observer observer) {
    return nativeCreatePeerConnectionObserver(observer);
  }

  private native long nativeGetNativePeerConnection();
  private native SessionDescription nativeGetLocalDescription();
  private native SessionDescription nativeGetRemoteDescription();
  private native RtcCertificatePem nativeGetCertificate();
  private native DataChannel nativeCreateDataChannel(String label, DataChannel.Init init);
  private native void nativeCreateOffer(SdpObserver observer, MediaConstraints constraints);
  private native void nativeCreateAnswer(SdpObserver observer, MediaConstraints constraints);
  private native void nativeSetLocalDescription(SdpObserver observer, SessionDescription sdp);
  private native void nativeSetRemoteDescription(SdpObserver observer, SessionDescription sdp);
  private native void nativeSetAudioPlayout(boolean playout);
  private native void nativeSetAudioRecording(boolean recording);
  private native boolean nativeSetBitrate(Integer min, Integer current, Integer max);
  private native SignalingState nativeSignalingState();
  private native IceConnectionState nativeIceConnectionState();
  private native PeerConnectionState nativeConnectionState();
  private native IceGatheringState nativeIceGatheringState();
  private native void nativeClose();
  private static native long nativeCreatePeerConnectionObserver(Observer observer);
  private static native void nativeFreeOwnedPeerConnection(long ownedPeerConnection);
  private native boolean nativeSetConfiguration(RTCConfiguration config);
  private native boolean nativeAddIceCandidate(
      String sdpMid, int sdpMLineIndex, String iceCandidateSdp);
  private native boolean nativeRemoveIceCandidates(final IceCandidate[] candidates);
  private native boolean nativeAddLocalStream(long stream);
  private native void nativeRemoveLocalStream(long stream);
  private native boolean nativeOldGetStats(StatsObserver observer, long nativeTrack);
  private native void nativeNewGetStats(RTCStatsCollectorCallback callback);
  private native RtpSender nativeCreateSender(String kind, String stream_id);
  private native List<RtpSender> nativeGetSenders();
  private native List<RtpReceiver> nativeGetReceivers();
  private native List<RtpTransceiver> nativeGetTransceivers();
  private native RtpSender nativeAddTrack(long track, List<String> streamIds);
  private native boolean nativeRemoveTrack(long sender);
  private native RtpTransceiver nativeAddTransceiverWithTrack(
      long track, RtpTransceiver.RtpTransceiverInit init);
  private native RtpTransceiver nativeAddTransceiverOfType(
      MediaStreamTrack.MediaType mediaType, RtpTransceiver.RtpTransceiverInit init);
  private native boolean nativeStartRtcEventLog(int file_descriptor, int max_size_bytes);
  private native void nativeStopRtcEventLog();
}
