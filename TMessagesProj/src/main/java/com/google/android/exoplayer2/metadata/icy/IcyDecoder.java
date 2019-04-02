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
package com.google.android.exoplayer2.metadata.icy;

import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataDecoder;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes ICY stream information. */
public final class IcyDecoder implements MetadataDecoder {

  private static final String TAG = "IcyDecoder";

  private static final Pattern METADATA_ELEMENT = Pattern.compile("(.+?)='(.+?)';");
  private static final String STREAM_KEY_NAME = "streamtitle";
  private static final String STREAM_KEY_URL = "streamurl";

  @Override
  @Nullable
  @SuppressWarnings("ByteBufferBackingArray")
  public Metadata decode(MetadataInputBuffer inputBuffer) {
    ByteBuffer buffer = inputBuffer.data;
    byte[] data = buffer.array();
    int length = buffer.limit();
    return decode(Util.fromUtf8Bytes(data, 0, length));
  }

  @Nullable
  @VisibleForTesting
  /* package */ Metadata decode(String metadata) {
    String name = null;
    String url = null;
    int index = 0;
    Matcher matcher = METADATA_ELEMENT.matcher(metadata);
    while (matcher.find(index)) {
      String key = Util.toLowerInvariant(matcher.group(1));
      String value = matcher.group(2);
      switch (key) {
        case STREAM_KEY_NAME:
          name = value;
          break;
        case STREAM_KEY_URL:
          url = value;
          break;
        default:
          Log.w(TAG, "Unrecognized ICY tag: " + name);
          break;
      }
      index = matcher.end();
    }
    return (name != null || url != null) ? new Metadata(new IcyInfo(name, url)) : null;
  }
}
