/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.drm;

import android.net.Uri;
import com.google.android.exoplayer2.upstream.DataSpec;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Thrown when an error occurs while executing a DRM {@link MediaDrmCallback#executeKeyRequest key}
 * or {@link MediaDrmCallback#executeProvisionRequest provisioning} request.
 */
public final class MediaDrmCallbackException extends IOException {

  /** The {@link DataSpec} associated with the request. */
  public final DataSpec dataSpec;
  /**
   * The {@link Uri} after redirections, or {@link #dataSpec dataSpec.uri} if no redirection
   * occurred.
   */
  public final Uri uriAfterRedirects;
  /** The HTTP request headers included in the response. */
  public final Map<String, List<String>> responseHeaders;
  /** The number of bytes obtained from the server. */
  public final long bytesLoaded;

  /**
   * Creates a new instance with the given values.
   *
   * @param dataSpec See {@link #dataSpec}.
   * @param uriAfterRedirects See {@link #uriAfterRedirects}.
   * @param responseHeaders See {@link #responseHeaders}.
   * @param bytesLoaded See {@link #bytesLoaded}.
   * @param cause The cause of the exception.
   */
  public MediaDrmCallbackException(
      DataSpec dataSpec,
      Uri uriAfterRedirects,
      Map<String, List<String>> responseHeaders,
      long bytesLoaded,
      Throwable cause) {
    super(cause);
    this.dataSpec = dataSpec;
    this.uriAfterRedirects = uriAfterRedirects;
    this.responseHeaders = responseHeaders;
    this.bytesLoaded = bytesLoaded;
  }
}
