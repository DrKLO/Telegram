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
package org.telegram.messenger.exoplayer.extractor.ogg;

import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.extractor.Extractor;
import org.telegram.messenger.exoplayer.extractor.ExtractorInput;
import org.telegram.messenger.exoplayer.extractor.PositionHolder;
import org.telegram.messenger.exoplayer.extractor.SeekMap;
import org.telegram.messenger.exoplayer.util.FlacSeekTable;
import org.telegram.messenger.exoplayer.util.FlacStreamInfo;
import org.telegram.messenger.exoplayer.util.FlacUtil;
import org.telegram.messenger.exoplayer.util.MimeTypes;
import org.telegram.messenger.exoplayer.util.ParsableByteArray;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * {@link StreamReader} to extract Flac data out of Ogg byte stream.
 */
/* package */ final class FlacReader extends StreamReader {

  private static final byte AUDIO_PACKET_TYPE = (byte) 0xFF;
  private static final byte SEEKTABLE_PACKET_TYPE = 0x03;

  private FlacStreamInfo streamInfo;

  private FlacSeekTable seekTable;

  private boolean firstAudioPacketProcessed;

  /* package */ static boolean verifyBitstreamType(ParsableByteArray data) {
    return data.readUnsignedByte() == 0x7F && // packet type
        data.readUnsignedInt() == 0x464C4143; // ASCII signature "FLAC"
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException {
    long position = input.getPosition();

    if (!oggParser.readPacket(input, scratch)) {
      return Extractor.RESULT_END_OF_INPUT;
    }

    byte[] data = scratch.data;
    if (streamInfo == null) {
      streamInfo = new FlacStreamInfo(data, 17);

      byte[] metadata = Arrays.copyOfRange(data, 9, scratch.limit());
      metadata[4] = (byte) 0x80; // Set the last metadata block flag, ignore the other blocks
      List<byte[]> initializationData = Collections.singletonList(metadata);

      MediaFormat mediaFormat = MediaFormat.createAudioFormat(null, MimeTypes.AUDIO_FLAC,
          streamInfo.bitRate(), MediaFormat.NO_VALUE, streamInfo.durationUs(),
          streamInfo.channels, streamInfo.sampleRate, initializationData, null);
      trackOutput.format(mediaFormat);

    } else if (data[0] == AUDIO_PACKET_TYPE) {
      if (!firstAudioPacketProcessed) {
        if (seekTable != null) {
          extractorOutput.seekMap(seekTable.createSeekMap(position, streamInfo.sampleRate));
          seekTable = null;
        } else {
          extractorOutput.seekMap(SeekMap.UNSEEKABLE);
        }
        firstAudioPacketProcessed = true;
      }

      trackOutput.sampleData(scratch, scratch.limit());
      scratch.setPosition(0);
      long timeUs = FlacUtil.extractSampleTimestamp(streamInfo, scratch);
      trackOutput.sampleMetadata(timeUs, C.SAMPLE_FLAG_SYNC, scratch.limit(), 0, null);

    } else if ((data[0] & 0x7F) == SEEKTABLE_PACKET_TYPE && seekTable == null) {
      seekTable = FlacSeekTable.parseSeekTable(scratch);
    }

    scratch.reset();
    return Extractor.RESULT_CONTINUE;
  }

}
