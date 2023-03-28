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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataInputBuffer;
import com.google.android.exoplayer2.metadata.SimpleMetadataDecoder;
import com.google.common.base.Ascii;
import com.google.common.base.Charsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Decodes ICY stream information. */
public final class IcyDecoder extends SimpleMetadataDecoder {

  private static final Pattern METADATA_ELEMENT = Pattern.compile("(.+?)='(.*?)';", Pattern.DOTALL);
  private static final String STREAM_KEY_NAME = "streamtitle";
  private static final String STREAM_KEY_URL = "streamurl";

  private final CharsetDecoder utf8Decoder;
  private final CharsetDecoder iso88591Decoder;

  public IcyDecoder() {
    utf8Decoder = Charsets.UTF_8.newDecoder();
    iso88591Decoder = Charsets.ISO_8859_1.newDecoder();
  }

  @Override
  protected Metadata decode(MetadataInputBuffer inputBuffer, ByteBuffer buffer) {
    @Nullable String icyString = decodeToString(buffer);
    byte[] icyBytes = new byte[buffer.limit()];
    buffer.get(icyBytes);

    if (icyString == null) {
      return new Metadata(new IcyInfo(icyBytes, /* title= */ null, /* url= */ null));
    }

    @Nullable String name = null;
    @Nullable String url = null;
    int index = 0;
    Matcher matcher = METADATA_ELEMENT.matcher(icyString);
    while (matcher.find(index)) {
      @Nullable String key = matcher.group(1);
      @Nullable String value = matcher.group(2);
      if (key != null) {
        switch (Ascii.toLowerCase(key)) {
          case STREAM_KEY_NAME:
            name = value;
            break;
          case STREAM_KEY_URL:
            url = value;
            break;
          default:
            break;
        }
      }
      index = matcher.end();
    }
    return new Metadata(new IcyInfo(icyBytes, name, url));
  }

  // The ICY spec doesn't specify a character encoding, and there's no way to communicate one
  // either. So try decoding UTF-8 first, then fall back to ISO-8859-1.
  // https://github.com/google/ExoPlayer/issues/6753
  @Nullable
  private String decodeToString(ByteBuffer data) {
    try {
      return utf8Decoder.decode(data).toString();
    } catch (CharacterCodingException e) {
      // Fall through to try ISO-8859-1 decoding.
    } finally {
      utf8Decoder.reset();
      data.rewind();
    }
    try {
      return iso88591Decoder.decode(data).toString();
    } catch (CharacterCodingException e) {
      return null;
    } finally {
      iso88591Decoder.reset();
      data.rewind();
    }
  }
}
