/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.webrtc;

import androidx.annotation.Nullable;

/**
 * PeerConnectionDependencies holds all PeerConnection dependencies that are
 * applied per PeerConnection. A dependency is distinct from a configuration
 * as it defines significant executable code that can be provided by a user of
 * the API.
 */
public final class PeerConnectionDependencies {
  // Mandatory dependencies.
  private final PeerConnection.Observer observer;

  // Optional fields.
  private final SSLCertificateVerifier sslCertificateVerifier;

  public static class Builder {
    private PeerConnection.Observer observer;
    private SSLCertificateVerifier sslCertificateVerifier;

    private Builder(PeerConnection.Observer observer) {
      this.observer = observer;
    }

    public Builder setSSLCertificateVerifier(SSLCertificateVerifier sslCertificateVerifier) {
      this.sslCertificateVerifier = sslCertificateVerifier;
      return this;
    }

    // Observer is a required dependency and so is forced in the construction of the object.
    public PeerConnectionDependencies createPeerConnectionDependencies() {
      return new PeerConnectionDependencies(observer, sslCertificateVerifier);
    }
  }

  public static Builder builder(PeerConnection.Observer observer) {
    return new Builder(observer);
  }

  PeerConnection.Observer getObserver() {
    return observer;
  }

  @Nullable
  SSLCertificateVerifier getSSLCertificateVerifier() {
    return sslCertificateVerifier;
  }

  private PeerConnectionDependencies(
      PeerConnection.Observer observer, SSLCertificateVerifier sslCertificateVerifier) {
    this.observer = observer;
    this.sslCertificateVerifier = sslCertificateVerifier;
  }
}
