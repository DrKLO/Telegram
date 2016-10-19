/*
 * Copyright (C) 2014 The Android Open Source Project
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
package org.telegram.messenger.exoplayer.dash.mpd;

import android.net.Uri;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.UriUtil;

/**
 * Defines a range of data located at a {@link Uri}.
 */
public final class RangedUri {

  /**
   * The (zero based) index of the first byte of the range.
   */
  public final long start;

  /**
   * The length of the range, or -1 to indicate that the range is unbounded.
   */
  public final long length;

  // The URI is stored internally in two parts: reference URI and a base URI to use when
  // resolving it. This helps optimize memory usage in the same way that DASH manifests allow many
  // URLs to be expressed concisely in the form of a single BaseURL and many relative paths. Note
  // that this optimization relies on the same object being passed as the base URI to many
  // instances of this class.
  private final String baseUri;
  private final String referenceUri;

  private int hashCode;

  /**
   * Constructs an ranged uri.
   *
   * @param baseUri A uri that can form the base of the uri defined by the instance.
   * @param referenceUri A reference uri that should be resolved with respect to {@code baseUri}.
   * @param start The (zero based) index of the first byte of the range.
   * @param length The length of the range, or -1 to indicate that the range is unbounded.
   */
  public RangedUri(String baseUri, String referenceUri, long start, long length) {
    Assertions.checkArgument(baseUri != null || referenceUri != null);
    this.baseUri = baseUri;
    this.referenceUri = referenceUri;
    this.start = start;
    this.length = length;
  }

  /**
   * Returns the {@link Uri} represented by the instance.
   *
   * @return The {@link Uri} represented by the instance.
   */
  public Uri getUri() {
    return UriUtil.resolveToUri(baseUri, referenceUri);
  }

  /**
   * Returns the uri represented by the instance as a string.
   *
   * @return The uri represented by the instance.
   */
  public String getUriString() {
    return UriUtil.resolve(baseUri, referenceUri);
  }

  /**
   * Attempts to merge this {@link RangedUri} with another.
   * <p>
   * A merge is successful if both instances define the same {@link Uri}, and if one starte the
   * byte after the other ends, forming a contiguous region with no overlap.
   * <p>
   * If {@code other} is null then the merge is considered unsuccessful, and null is returned.
   *
   * @param other The {@link RangedUri} to merge.
   * @return The merged {@link RangedUri} if the merge was successful. Null otherwise.
   */
  public RangedUri attemptMerge(RangedUri other) {
    if (other == null || !getUriString().equals(other.getUriString())) {
      return null;
    } else if (length != -1 && start + length == other.start) {
      return new RangedUri(baseUri, referenceUri, start,
          other.length == -1 ? -1 : length + other.length);
    } else if (other.length != -1 && other.start + other.length == start) {
      return new RangedUri(baseUri, referenceUri, other.start,
          length == -1 ? -1 : other.length + length);
    } else {
      return null;
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (int) start;
      result = 31 * result + (int) length;
      result = 31 * result + getUriString().hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RangedUri other = (RangedUri) obj;
    return this.start == other.start
        && this.length == other.length
        && getUriString().equals(other.getUriString());
  }

}
