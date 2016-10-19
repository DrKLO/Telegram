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
package org.telegram.messenger.exoplayer.text.webvtt;

import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.text.Cue;
import org.telegram.messenger.exoplayer.text.SubtitleParser;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link SubtitleParser} for Webvtt embedded in a Mp4 container file.
 */
public final class Mp4WebvttParser implements SubtitleParser {

  private static final int BOX_HEADER_SIZE = 8;

  private static final int TYPE_payl = Util.getIntegerCodeForString("payl");
  private static final int TYPE_sttg = Util.getIntegerCodeForString("sttg");
  private static final int TYPE_vttc = Util.getIntegerCodeForString("vttc");

  private final ParsableByteArray sampleData;
  private final WebvttCue.Builder builder;

  public Mp4WebvttParser() {
    sampleData = new ParsableByteArray();
    builder = new WebvttCue.Builder();
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_MP4VTT.equals(mimeType);
  }

  @Override
  public Mp4WebvttSubtitle parse(byte[] bytes, int offset, int length) throws ParserException {
    // Webvtt in Mp4 samples have boxes inside of them, so we have to do a traditional box parsing:
    // first 4 bytes size and then 4 bytes type.
    sampleData.reset(bytes, offset + length);
    sampleData.setPosition(offset);
    List<Cue> resultingCueList = new ArrayList<>();
    while (sampleData.bytesLeft() > 0) {
      if (sampleData.bytesLeft() < BOX_HEADER_SIZE) {
        throw new ParserException("Incomplete Mp4Webvtt Top Level box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      if (boxType == TYPE_vttc) {
        resultingCueList.add(parseVttCueBox(sampleData, builder, boxSize - BOX_HEADER_SIZE));
      } else {
        // Peers of the VTTCueBox are still not supported and are skipped.
        sampleData.skipBytes(boxSize - BOX_HEADER_SIZE);
      }
    }
    return new Mp4WebvttSubtitle(resultingCueList);
  }

  private static Cue parseVttCueBox(ParsableByteArray sampleData, WebvttCue.Builder builder,
        int remainingCueBoxBytes) throws ParserException {
    builder.reset();
    while (remainingCueBoxBytes > 0) {
      if (remainingCueBoxBytes < BOX_HEADER_SIZE) {
        throw new ParserException("Incomplete vtt cue box header found.");
      }
      int boxSize = sampleData.readInt();
      int boxType = sampleData.readInt();
      remainingCueBoxBytes -= BOX_HEADER_SIZE;
      int payloadLength = boxSize - BOX_HEADER_SIZE;
      String boxPayload = new String(sampleData.data, sampleData.getPosition(), payloadLength);
      sampleData.skipBytes(payloadLength);
      remainingCueBoxBytes -= payloadLength;
      if (boxType == TYPE_sttg) {
        WebvttCueParser.parseCueSettingsList(boxPayload, builder);
      } else if (boxType == TYPE_payl) {
        WebvttCueParser.parseCueText(boxPayload.trim(), builder);
      } else {
        // Other VTTCueBox children are still not supported and are ignored.
      }
    }
    return builder.build();
  }

}
