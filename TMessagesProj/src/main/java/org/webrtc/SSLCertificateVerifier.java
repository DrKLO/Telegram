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

/**
 * The SSLCertificateVerifier interface allows API users to provide custom
 * logic to verify certificates.
 */
public interface SSLCertificateVerifier {
  /**
   * Implementations of verify allow applications to provide custom logic for
   * verifying certificates. This is not required by default and should be used
   * with care.
   *
   * @param certificate A byte array containing a DER encoded X509 certificate.
   * @return True if the certificate is verified and trusted else false.
   */
  @CalledByNative boolean verify(byte[] certificate);
}
