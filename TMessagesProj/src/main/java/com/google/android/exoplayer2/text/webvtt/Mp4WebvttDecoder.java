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
package com.google.android.exoplayer2.text.webvtt;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.text.SubtitleDecoderException;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link SimpleSubtitleDecoder} for Webvtt embedded in a Mp4 container file. */
@SuppressWarnings("ConstantField")
public final class Mp4WebvttDecoder extends SimpleSubtitleDecoder {

  private static final int BOX_HEADER_SIZE = 8;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_payl = 0x7061796c;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_sttg = 0x73747467;

  @SuppressWarnings("ConstantCaseForConstants")
  private static final int TYPE_vttc = 0x76747463;

  private final ParsableByteArray sampleData;

  public Mp4WebvttDecoder() {
    super("Mp4WebvttDecoder");
    sampleData = new ParsableByteArray();
  }

  @Override
  protected Subtitle decode(byte[] data, int length, boolean reset)
      throws SubtitleDecoderException {
    // Webvtt in Mp4 samples have boxes inside of them, so we have to do a traditional box parsing:
    // first 4 bytes size and then 4 bytes type.
    sampleData.reset(data, length);
    List<Cue> resultingCueList = new ArrayList<>();
    while (sampleData.bytesLeft() > 0) {
      if (sampleData.bytesLeft() < BOX_HEADER_SIZE) {
        throw new SubtitleDecoderException("Incomplete Mp4Webvtt Top Level box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      if (boxType == TYPE_vttc) {
        resultingCueList.add(parseVttCueBox(sampleData, boxSize - BOX_HEADER_SIZE));
      } else {
        // Peers of the VTTCueBox are still not supported and are skipped.
        sampleData.skipBytes(boxSize - BOX_HEADER_SIZE);
      }
    }
    return new Mp4WebvttSubtitle(resultingCueList);
  }

  private static Cue parseVttCueBox(ParsableByteArray sampleData, int remainingCueBoxBytes)
      throws SubtitleDecoderException {
    @Nullable Cue.Builder cueBuilder = null;
    @Nullable CharSequence cueText = null;
    while (remainingCueBoxBytes > 0) {
      if (remainingCueBoxBytes < BOX_HEADER_SIZE) {
        throw new SubtitleDecoderException("Incomplete vtt cue box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      remainingCueBoxBytes -= BOX_HEADER_SIZE;
      int payloadLength = boxSize - BOX_HEADER_SIZE;
      String boxPayload =
          Util.fromUtf8Bytes(sampleData.getData(), sampleData.getPosition(), payloadLength);
      sampleData.skipBytes(payloadLength);
      remainingCueBoxBytes -= payloadLength;
      if (boxType == TYPE_sttg) {
        cueBuilder = WebvttCueParser.parseCueSettingsList(boxPayload);
      } else if (boxType == TYPE_payl) {
        cueText =
            WebvttCueParser.parseCueText(
                /* id= */ null, boxPayload.trim(), /* styles= */ Collections.emptyList());
      } else {
        // Other VTTCueBox children are still not supported and are ignored.
      }
    }
    if (cueText == null) {
      cueText = "";
    }
    return cueBuilder != null
        ? cueBuilder.setText(cueText).build()
        : WebvttCueParser.newCueForText(cueText);
  }
}
