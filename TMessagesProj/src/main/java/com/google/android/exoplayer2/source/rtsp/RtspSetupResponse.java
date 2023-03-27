/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.source.rtsp;

/** Represents an RTSP SETUP response. */
/* package */ final class RtspSetupResponse {

  /** The response's status code. */
  public final int status;
  /** The Session header (RFC2326 Section 12.37). */
  public final RtspMessageUtil.RtspSessionHeader sessionHeader;
  /** The Transport header (RFC2326 Section 12.39). */
  public final String transport;

  /**
   * Creates a new instance.
   *
   * @param status The response's status code.
   * @param sessionHeader The {@link RtspMessageUtil.RtspSessionHeader}.
   * @param transport The transport header included in the RTSP SETUP response.
   */
  public RtspSetupResponse(
      int status, RtspMessageUtil.RtspSessionHeader sessionHeader, String transport) {
    this.status = status;
    this.sessionHeader = sessionHeader;
    this.transport = transport;
  }
}
