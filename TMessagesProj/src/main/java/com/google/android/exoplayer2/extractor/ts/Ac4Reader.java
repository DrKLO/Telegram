/*
 * Copyright (C) 2019 The Android Open Source Project
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
import com.google.android.exoplayer2.audio.Ac4Util;
import com.google.android.exoplayer2.audio.Ac4Util.SyncFrameInfo;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous AC-4 byte stream and extracts individual samples. */
public final class Ac4Reader implements ElementaryStreamReader {

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({STATE_FINDING_SYNC, STATE_READING_HEADER, STATE_READING_SAMPLE})
  private @interface State {}

  private static final int STATE_FINDING_SYNC = 0;
  private static final int STATE_READING_HEADER = 1;
  private static final int STATE_READING_SAMPLE = 2;

  private final ParsableBitArray headerScratchBits;
  private final ParsableByteArray headerScratchBytes;
  @Nullable private final String language;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;

  private @State int state;
  private int bytesRead;

  // Used to find the header.
  private boolean lastByteWasAC;
  private boolean hasCRC;

  // Used when parsing the header.
  private long sampleDurationUs;
  private @MonotonicNonNull Format format;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  /** Constructs a new reader for AC-4 elementary streams. */
  public Ac4Reader() {
    this(null);
  }

  /**
   * Constructs a new reader for AC-4 elementary streams.
   *
   * @param language Track language.
   */
  public Ac4Reader(@Nullable String language) {
    headerScratchBits = new ParsableBitArray(new byte[Ac4Util.HEADER_SIZE_FOR_PARSER]);
    headerScratchBytes = new ParsableByteArray(headerScratchBits.data);
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWasAC = false;
    hasCRC = false;
    timeUs = C.TIME_UNSET;
    this.language = language;
  }

  @Override
  public void seek() {
    state = STATE_FINDING_SYNC;
    bytesRead = 0;
    lastByteWasAC = false;
    hasCRC = false;
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
            headerScratchBytes.getData()[0] = (byte) 0xAC;
            headerScratchBytes.getData()[1] = (byte) (hasCRC ? 0x41 : 0x40);
            bytesRead = 2;
          }
          break;
        case STATE_READING_HEADER:
          if (continueRead(data, headerScratchBytes.getData(), Ac4Util.HEADER_SIZE_FOR_PARSER)) {
            parseHeader();
            headerScratchBytes.setPosition(0);
            output.sampleData(headerScratchBytes, Ac4Util.HEADER_SIZE_FOR_PARSER);
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
      if (!lastByteWasAC) {
        lastByteWasAC = (pesBuffer.readUnsignedByte() == 0xAC);
        continue;
      }
      int secondByte = pesBuffer.readUnsignedByte();
      lastByteWasAC = secondByte == 0xAC;
      if (secondByte == 0x40 || secondByte == 0x41) {
        hasCRC = secondByte == 0x41;
        return true;
      }
    }
    return false;
  }

  /** Parses the sample header. */
  @RequiresNonNull("output")
  private void parseHeader() {
    headerScratchBits.setPosition(0);
    SyncFrameInfo frameInfo = Ac4Util.parseAc4SyncframeInfo(headerScratchBits);
    if (format == null
        || frameInfo.channelCount != format.channelCount
        || frameInfo.sampleRate != format.sampleRate
        || !MimeTypes.AUDIO_AC4.equals(format.sampleMimeType)) {
      format =
          new Format.Builder()
              .setId(formatId)
              .setSampleMimeType(MimeTypes.AUDIO_AC4)
              .setChannelCount(frameInfo.channelCount)
              .setSampleRate(frameInfo.sampleRate)
              .setLanguage(language)
              .build();
      output.format(format);
    }
    sampleSize = frameInfo.frameSize;
    // In this class a sample is an AC-4 sync frame, but Format#sampleRate specifies the number of
    // PCM audio samples per second.
    sampleDurationUs = C.MICROS_PER_SECOND * frameInfo.sampleCount / format.sampleRate;
  }
}
