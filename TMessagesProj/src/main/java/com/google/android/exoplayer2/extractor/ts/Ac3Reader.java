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
package com.google.android.exoplayer2.extractor.ts;

import static java.lang.Math.min;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.audio.Ac3Util;
import com.google.android.exoplayer2.audio.Ac3Util.SyncFrameInfo;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous (E-)AC-3 byte stream and extracts individual samples. */
public final class Ac3Reader implements ElementaryStreamReader {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_FINDING_SYNC, STATE_READING_HEADER, STATE_READING_SAMPLE})
  private @interface State {}

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private static final int HEADER_SIZE = 128;

  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray headerScratchBytes;
  @Nullable private final String language;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  private @State int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWas0B;

  // Used when parsing the header.
  private long sampleDurationUs;
  private @MonotonicNonNull Format format;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  /** Constructs a new reader for (E-)AC-3 elementary streams. */
  public Ac3Reader() {
    this(null);
  }

  /**
   * Constructs a new reader for (E-)AC-3 elementary streams.
   *
   * @param language Track language.
   */
  public Ac3Reader(@Nullable String language) {
    headerScratchBits = new ParsableBitArray(new byte[HEADER_SIZE]);
    headerScratchBytes = new ParsableByteArray(headerScratchBits.data);
    state = STATE_FINDING_SYNC;
    timeUs = C.TIME_UNSET;
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWas0B = false;
    timeUs = C.TIME_UNSET;
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if (pesTimeUs != C.TIME_UNSET) {
      timeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) {
    Assertions.checkStateNotNull(output); // Asserts that createTracks has been called.
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SYNC:
          if (skipToNextSync(data)) {
            state = STATE_READING_HEADER;
            headerScratchBytes.getData()[0] = 0x0B;
            headerScratchBytes.getData()[1] = 0x77;
            bytesRead = 2;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), HEADER_SIZE)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, HEADER_SIZE);
            state = STATE_READING_SAMPLE;
          }
          break;
        case STATE_READING_SAMPLE:
          int bytesToRead = min(data.bytesLeft(), sampleSize - bytesRead);
          output.sampleData(data, bytesToRead);
          bytesRead += bytesToRead;
          if (bytesRead == sampleSize) {
            if (timeUs != C.TIME_UNSET) {
              output.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
              timeUs += sampleDurationUs;
            }
            state = STATE_FINDING_SYNC;
          }
          break;
        default:
          break;
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Continues a read from the provided {@code source} into a given {@code target}. It's assumed
   * that the data should be written into {@code target} starting from an offset of zero.
   *
   * @param source The source from which to read.
   * @param target The target into which data is to be read.
   * @param targetLength The target length of the read.
   * @return Whether the target length was reached.
   */
  private boolean continueRead(ParsableByteArray source, byte[] target, int targetLength) {
    int bytesToRead = min(source.bytesLeft(), targetLength - bytesRead);
    source.readBytes(target, bytesRead, bytesToRead);
    bytesRead += bytesToRead;
    return bytesRead == targetLength;
  }

  /**
   * Locates the next syncword, advancing the position to the byte that immediately follows it. If a
   * syncword was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   * @return Whether a syncword position was found.
   */
  private boolean skipToNextSync(ParsableByteArray pesBuffer) {
    while (pesBuffer.bytesLeft() > 0) {
      if (!lastByteWas0B) {
        lastByteWas0B = pesBuffer.readUnsignedByte() == 0x0B;
        continue;
      }
      int secondByte = pesBuffer.readUnsignedByte();
      if (secondByte == 0x77) {
        lastByteWas0B = false;
        return true;
      } else {
        lastByteWas0B = secondByte == 0x0B;
      }
    }
    return false;
  }

  /** Parses the sample header. */
  @RequiresNonNull("output")
  private void parseHeader() {
    headerScratchBits.setPosition(0);
    SyncFrameInfo frameInfo = Ac3Util.parseAc3SyncframeInfo(headerScratchBits);
    if (format == null
        || frameInfo.channelCount != format.channelCount
        || frameInfo.sampleRate != format.sampleRate
        || !Util.areEqual(frameInfo.mimeType, format.sampleMimeType)) {
      Format.Builder formatBuilder =
          new Format.Builder()
              .setId(formatId)
              .setSampleMimeType(frameInfo.mimeType)
              .setChannelCount(frameInfo.channelCount)
              .setSampleRate(frameInfo.sampleRate)
              .setLanguage(language)
              .setPeakBitrate(frameInfo.bitrate);
      // AC3 has constant bitrate, so averageBitrate = peakBitrate
      if (MimeTypes.AUDIO_AC3.equals(frameInfo.mimeType)) {
        formatBuilder.setAverageBitrate(frameInfo.bitrate);
      }
      format = formatBuilder.build();
      output.format(format);
    }
    sampleSize = frameInfo.frameSize;
    // In this class a sample is an access unit (syncframe in AC-3), but Format#sampleRate
    // specifies the number of PCM audio samples per second.
    sampleDurationUs = C.MICROS_PER_SECOND * frameInfo.sampleCount / format.sampleRate;
  }
}
