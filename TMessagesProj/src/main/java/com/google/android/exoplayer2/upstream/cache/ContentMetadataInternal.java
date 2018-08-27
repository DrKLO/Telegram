/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.upstream.cache;

import android.net.Uri;
import android.support.annotation.Nullable;
import com.google.android.exoplayer2.C;

/** Helper classes to easily access and modify internal metadata values. */
/* package */ final class ContentMetadataInternal {

  private static final String PREFIX = ContentMetadata.INTERNAL_METADATA_NAME_PREFIX;
  private static final String METADATA_NAME_REDIRECTED_URI = PREFIX + "redir";
  private static final String METADATA_NAME_CONTENT_LENGTH = PREFIX + "len";

  /** Returns the content length metadata, or {@link C#LENGTH_UNSET} if not set. */
  public static long getContentLength(ContentMetadata contentMetadata) {
    return contentMetadata.get(METADATA_NAME_CONTENT_LENGTH, C.LENGTH_UNSET);
  }

  /** Adds a mutation to set content length metadata value. */
  public static void setContentLength(ContentMetadataMutations mutations, long length) {
    mutations.set(METADATA_NAME_CONTENT_LENGTH, length);
  }

  /** Adds a mutation to remove content length metadata value. */
  public static void removeContentLength(ContentMetadataMutations mutations) {
    mutations.remove(METADATA_NAME_CONTENT_LENGTH);
  }

  /** Returns the redirected uri metadata, or {@code null} if not set. */
  public @Nullable static Uri getRedirectedUri(ContentMetadata contentMetadata) {
    String redirectedUri = contentMetadata.get(METADATA_NAME_REDIRECTED_URI, (String) null);
    return redirectedUri == null ? null : Uri.parse(redirectedUri);
  }

  /**
   * Adds a mutation to set redirected uri metadata value. Passing {@code null} as {@code uri} isn't
   * allowed.
   */
  public static void setRedirectedUri(ContentMetadataMutations mutations, Uri uri) {
    mutations.set(METADATA_NAME_REDIRECTED_URI, uri.toString());
  }

  /** Adds a mutation to remove redirected uri metadata value. */
  public static void removeRedirectedUri(ContentMetadataMutations mutations) {
    mutations.remove(METADATA_NAME_REDIRECTED_URI);
  }

  private ContentMetadataInternal() {
    // Prevent instantiation.
  }
}
