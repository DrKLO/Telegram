/*
 * Copyright (C) 2016 The Android Open Source Project
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
package org.telegram.messenger.exoplayer2.drm;

import android.annotation.TargetApi;
import android.net.Uri;
import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.KeyRequest;
import org.telegram.messenger.exoplayer2.drm.ExoMediaDrm.ProvisionRequest;
import org.telegram.messenger.exoplayer2.upstream.DataSourceInputStream;
import org.telegram.messenger.exoplayer2.upstream.DataSpec;
import org.telegram.messenger.exoplayer2.upstream.HttpDataSource;
import org.telegram.messenger.exoplayer2.upstream.HttpDataSource.Factory;
import org.telegram.messenger.exoplayer2.util.Assertions;
import org.telegram.messenger.exoplayer2.util.Util;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A {@link MediaDrmCallback} that makes requests using {@link HttpDataSource} instances.
 */
@TargetApi(18)
public final class HttpMediaDrmCallback implements MediaDrmCallback {

  private static final Map<String, String> PLAYREADY_KEY_REQUEST_PROPERTIES;
  static {
    PLAYREADY_KEY_REQUEST_PROPERTIES = new HashMap<>();
    PLAYREADY_KEY_REQUEST_PROPERTIES.put("Content-Type", "text/xml");
    PLAYREADY_KEY_REQUEST_PROPERTIES.put("SOAPAction",
        "http://schemas.microsoft.com/DRM/2007/03/protocols/AcquireLicense");
  }

  private final HttpDataSource.Factory dataSourceFactory;
  private final String defaultUrl;
  private final Map<String, String> keyRequestProperties;

  /**
   * @param defaultUrl The default license URL.
   * @param dataSourceFactory A factory from which to obtain {@link HttpDataSource} instances.
   */
  public HttpMediaDrmCallback(String defaultUrl, HttpDataSource.Factory dataSourceFactory) {
    this(defaultUrl, dataSourceFactory, null);
  }

  /**
   * @deprecated Use {@link HttpMediaDrmCallback#HttpMediaDrmCallback(String, Factory)}. Request
   *     properties can be set by calling {@link #setKeyRequestProperty(String, String)}.
   * @param defaultUrl The default license URL.
   * @param dataSourceFactory A factory from which to obtain {@link HttpDataSource} instances.
   * @param keyRequestProperties Request properties to set when making key requests, or null.
   */
  @Deprecated
  public HttpMediaDrmCallback(String defaultUrl, HttpDataSource.Factory dataSourceFactory,
      Map<String, String> keyRequestProperties) {
    this.dataSourceFactory = dataSourceFactory;
    this.defaultUrl = defaultUrl;
    this.keyRequestProperties = new HashMap<>();
    if (keyRequestProperties != null) {
      this.keyRequestProperties.putAll(keyRequestProperties);
    }
  }

  /**
   * Sets a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   * @param value The value of the field.
   */
  public void setKeyRequestProperty(String name, String value) {
    Assertions.checkNotNull(name);
    Assertions.checkNotNull(value);
    synchronized (keyRequestProperties) {
      keyRequestProperties.put(name, value);
    }
  }

  /**
   * Clears a header for key requests made by the callback.
   *
   * @param name The name of the header field.
   */
  public void clearKeyRequestProperty(String name) {
    Assertions.checkNotNull(name);
    synchronized (keyRequestProperties) {
      keyRequestProperties.remove(name);
    }
  }

  /**
   * Clears all headers for key requests made by the callback.
   */
  public void clearAllKeyRequestProperties() {
    synchronized (keyRequestProperties) {
      keyRequestProperties.clear();
    }
  }

  @Override
  public byte[] executeProvisionRequest(UUID uuid, ProvisionRequest request) throws IOException {
    String url = request.getDefaultUrl() + "&signedRequest=" + new String(request.getData());
    return executePost(dataSourceFactory, url, new byte[0], null);
  }

  @Override
  public byte[] executeKeyRequest(UUID uuid, KeyRequest request) throws Exception {
    String url = request.getDefaultUrl();
    if (TextUtils.isEmpty(url)) {
      url = defaultUrl;
    }
    Map<String, String> requestProperties = new HashMap<>();
    requestProperties.put("Content-Type", "application/octet-stream");
    if (C.PLAYREADY_UUID.equals(uuid)) {
      requestProperties.putAll(PLAYREADY_KEY_REQUEST_PROPERTIES);
    }
    synchronized (keyRequestProperties) {
      requestProperties.putAll(keyRequestProperties);
    }
    return executePost(dataSourceFactory, url, request.getData(), requestProperties);
  }

  private static byte[] executePost(HttpDataSource.Factory dataSourceFactory, String url,
      byte[] data, Map<String, String> requestProperties) throws IOException {
    HttpDataSource dataSource = dataSourceFactory.createDataSource();
    if (requestProperties != null) {
      for (Map.Entry<String, String> requestProperty : requestProperties.entrySet()) {
        dataSource.setRequestProperty(requestProperty.getKey(), requestProperty.getValue());
      }
    }
    DataSpec dataSpec = new DataSpec(Uri.parse(url), data, 0, 0, C.LENGTH_UNSET, null,
        DataSpec.FLAG_ALLOW_GZIP);
    DataSourceInputStream inputStream = new DataSourceInputStream(dataSource, dataSpec);
    try {
      return Util.toByteArray(inputStream);
    } finally {
      Util.closeQuietly(inputStream);
    }
  }

}
