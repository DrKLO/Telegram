/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.avi;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.common.collect.ImmutableList;

/** Represents an AVI LIST. */
/* package */ final class ListChunk implements AviChunk {

  public static ListChunk parseFrom(int listType, ParsableByteArray body) {
    ImmutableList.Builder<AviChunk> builder = new ImmutableList.Builder<>();
    int listBodyEndPosition = body.limit();
    @C.TrackType int currentTrackType = C.TRACK_TYPE_NONE;
    while (body.bytesLeft() > 8) {
      int type = body.readLittleEndianInt();
      int size = body.readLittleEndianInt();
      int innerBoxBodyEndPosition = body.getPosition() + size;
      body.setLimit(innerBoxBodyEndPosition);
      @Nullable AviChunk aviChunk;
      if (type == AviExtractor.FOURCC_LIST) {
        int innerListType = body.readLittleEndianInt();
        aviChunk = parseFrom(innerListType, body);
      } else {
        aviChunk = createBox(type, currentTrackType, body);
      }
      if (aviChunk != null) {
        if (aviChunk.getType() == AviExtractor.FOURCC_strh) {
          currentTrackType = ((AviStreamHeaderChunk) aviChunk).getTrackType();
        }
        builder.add(aviChunk);
      }
      body.setPosition(innerBoxBodyEndPosition);
      body.setLimit(listBodyEndPosition);
    }
    return new ListChunk(listType, builder.build());
  }

  public final ImmutableList<AviChunk> children;
  private final int type;

  private ListChunk(int type, ImmutableList<AviChunk> children) {
    this.type = type;
    this.children = children;
  }

  @Override
  public int getType() {
    return type;
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public <T extends AviChunk> T getChild(Class<T> c) {
    for (AviChunk aviChunk : children) {
      if (aviChunk.getClass() == c) {
        return (T) aviChunk;
      }
    }
    return null;
  }

  @Nullable
  private static AviChunk createBox(
      int chunkType, @C.TrackType int trackType, ParsableByteArray body) {
    switch (chunkType) {
      case AviExtractor.FOURCC_avih:
        return AviMainHeaderChunk.parseFrom(body);
      case AviExtractor.FOURCC_strh:
        return AviStreamHeaderChunk.parseFrom(body);
      case AviExtractor.FOURCC_strf:
        return StreamFormatChunk.parseFrom(trackType, body);
      case AviExtractor.FOURCC_strn:
        return StreamNameChunk.parseFrom(body);
      default:
        return null;
    }
  }
}
