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
package com.google.android.exoplayer2.source.dash.manifest;

import androidx.annotation.Nullable;
import com.google.common.base.Objects;

/** A base URL, as defined by ISO 23009-1, 2nd edition, 5.6. and ETSI TS 103 285 V1.2.1, 10.8.2.1 */
public final class BaseUrl {

  /** The default weight. */
  public static final int DEFAULT_WEIGHT = 1;
  /** The default priority. */
  public static final int DEFAULT_DVB_PRIORITY = 1;
  /** Constant representing an unset priority in a manifest that does not declare a DVB profile. */
  public static final int PRIORITY_UNSET = Integer.MIN_VALUE;

  /** The URL. */
  public final String url;
  /** The service location. */
  public final String serviceLocation;
  /** The priority. */
  public final int priority;
  /** The weight. */
  public final int weight;

  /**
   * Creates an instance with {@link #PRIORITY_UNSET an unset priority}, {@link #DEFAULT_WEIGHT
   * default weight} and using the URL as the service location.
   */
  public BaseUrl(String url) {
    this(url, /* serviceLocation= */ url, PRIORITY_UNSET, DEFAULT_WEIGHT);
  }

  /** Creates an instance. */
  public BaseUrl(String url, String serviceLocation, int priority, int weight) {
    this.url = url;
    this.serviceLocation = serviceLocation;
    this.priority = priority;
    this.weight = weight;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof BaseUrl)) {
      return false;
    }
    BaseUrl baseUrl = (BaseUrl) o;
    return priority == baseUrl.priority
        && weight == baseUrl.weight
        && Objects.equal(url, baseUrl.url)
        && Objects.equal(serviceLocation, baseUrl.serviceLocation);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(url, serviceLocation, priority, weight);
  }
}
