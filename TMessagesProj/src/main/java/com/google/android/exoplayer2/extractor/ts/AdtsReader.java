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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.audio.AacUtil;
import com.google.android.exoplayer2.extractor.DummyTrackOutput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.extractor.ts.TsPayloadReader.TrackIdGenerator;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableBitArray;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.Arrays;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/** Parses a continuous ADTS byte stream and extracts individual frames. */
public final class AdtsReader implements ElementaryStreamReader {

  private static final String TAG = "AdtsReader";

  private static final int STATE_FINDING_SAMPLE = 0;
  private static final int STATE_CHECKING_ADTS_HEADER = 1;
  private static final int STATE_READING_ID3_HEADER = 2;
  private static final int STATE_READING_ADTS_HEADER = 3;
  private static final int STATE_READING_SAMPLE = 4;

  private static final int HEADER_SIZE = 5;
  private static final int CRC_SIZE = 2;

  // Match states used while looking for the next sample
  private static final int MATCH_STATE_VALUE_SHIFT = 8;
  private static final int MATCH_STATE_START = 1 << MATCH_STATE_VALUE_SHIFT;
  private static final int MATCH_STATE_FF = 2 << MATCH_STATE_VALUE_SHIFT;
  private static final int MATCH_STATE_I = 3 << MATCH_STATE_VALUE_SHIFT;
  private static final int MATCH_STATE_ID = 4 << MATCH_STATE_VALUE_SHIFT;

  private static final int ID3_HEADER_SIZE = 10;
  private static final int ID3_SIZE_OFFSET = 6;
  private static final byte[] ID3_IDENTIFIER = {'I', 'D', '3'};
  private static final int VERSION_UNSET = -1;

  private final boolean exposeId3;
  private final ParsableBitArray adtsScratch;
  private final ParsableByteArray id3HeaderBuffer;
  @Nullable private final String language;

  private @MonotonicNonNull String formatId;
  private @MonotonicNonNull TrackOutput output;
  private @MonotonicNonNull TrackOutput id3Output;

  private int state;
  private int bytesRead;

  private int matchState;

  private boolean hasCrc;
  private boolean foundFirstFrame;

  // Used to verifies sync words
  private int firstFrameVersion;
  private int firstFrameSampleRateIndex;

  private int currentFrameVersion;

  // Used when parsing the header.
  private boolean hasOutputFormat;
  private long sampleDurationUs;
  private int sampleSize;

  // Used when reading the samples.
  private long timeUs;

  private @MonotonicNonNull TrackOutput currentOutput;
  private long currentSampleDuration;

  /**
   * @param exposeId3 True if the reader should expose ID3 information.
   */
  public AdtsReader(boolean exposeId3) {
    this(exposeId3, null);
  }

  /**
   * @param exposeId3 True if the reader should expose ID3 information.
   * @param language Track language.
   */
  public AdtsReader(boolean exposeId3, @Nullable String language) {
    adtsScratch = new ParsableBitArray(new byte[HEADER_SIZE + CRC_SIZE]);
    id3HeaderBuffer = new ParsableByteArray(Arrays.copyOf(ID3_IDENTIFIER, ID3_HEADER_SIZE));
    setFindingSampleState();
    firstFrameVersion = VERSION_UNSET;
    firstFrameSampleRateIndex = C.INDEX_UNSET;
    sampleDurationUs = C.TIME_UNSET;
    timeUs = C.TIME_UNSET;
    this.exposeId3 = exposeId3;
    this.language = language;
  }

  /** Returns whether an integer matches an ADTS SYNC word. */
  public static boolean isAdtsSyncWord(int candidateSyncWord) {
    return (candidateSyncWord & 0xFFF6) == 0xFFF0;
  }

  @Override
  public void seek() {
    timeUs = C.TIME_UNSET;
    resetSync();
  }

  @Override
  public void createTracks(ExtractorOutput extractorOutput, TrackIdGenerator idGenerator) {
    idGenerator.generateNewId();
    formatId = idGenerator.getFormatId();
    output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_AUDIO);
    currentOutput = output;
    if (exposeId3) {
      idGenerator.generateNewId();
      id3Output = extractorOutput.track(idGenerator.getTrackId(), C.TRACK_TYPE_METADATA);
      id3Output.format(
          new Format.Builder()
              .setId(idGenerator.getFormatId())
              .setSampleMimeType(MimeTypes.APPLICATION_ID3)
              .build());
    } else {
      id3Output = new DummyTrackOutput();
    }
  }

  @Override
  public void packetStarted(long pesTimeUs, @TsPayloadReader.Flags int flags) {
    if (pesTimeUs != C.TIME_UNSET) {
      timeUs = pesTimeUs;
    }
  }

  @Override
  public void consume(ParsableByteArray data) throws ParserException {
    assertTracksCreated();
    while (data.bytesLeft() > 0) {
      switch (state) {
        case STATE_FINDING_SAMPLE:
          findNextSample(data);
          break;
        case STATE_READING_ID3_HEADER:
          if (continueRead(data, id3HeaderBuffer.getData(), ID3_HEADER_SIZE)) {
            parseId3Header();
          }
          break;
        case STATE_CHECKING_ADTS_HEADER:
          checkAdtsHeader(data);
          break;
        case STATE_READING_ADTS_HEADER:
          int targetLength = hasCrc ? HEADER_SIZE + CRC_SIZE : HEADER_SIZE;
          if (continueRead(data, adtsScratch.data, targetLength)) {
            parseAdtsHeader();
          }
          break;
        case STATE_READING_SAMPLE:
          readSample(data);
          break;
        default:
          throw new IllegalStateException();
      }
    }
  }

  @Override
  public void packetFinished() {
    // Do nothing.
  }

  /**
   * Returns the duration in microseconds per sample, or {@link C#TIME_UNSET} if the sample duration
   * is not available.
   */
  public long getSampleDurationUs() {
    return sampleDurationUs;
  }

  private void resetSync() {
    foundFirstFrame = false;
    setFindingSampleState();
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

  /** Sets the state to STATE_FINDING_SAMPLE. */
  private void setFindingSampleState() {
    state = STATE_FINDING_SAMPLE;
    bytesRead = 0;
    matchState = MATCH_STATE_START;
  }

  /**
   * Sets the state to STATE_READING_ID3_HEADER and resets the fields required for {@link
   * #parseId3Header()}.
   */
  private void setReadingId3HeaderState() {
    state = STATE_READING_ID3_HEADER;
    bytesRead = ID3_IDENTIFIER.length;
    sampleSize = 0;
    id3HeaderBuffer.setPosition(0);
  }

  /**
   * Sets the state to STATE_READING_SAMPLE.
   *
   * @param outputToUse TrackOutput object to write the sample to
   * @param currentSampleDuration Duration of the sample to be read
   * @param priorReadBytes Size of prior read bytes
   * @param sampleSize Size of the sample
   */
  private void setReadingSampleState(
      TrackOutput outputToUse, long currentSampleDuration, int priorReadBytes, int sampleSize) {
    state = STATE_READING_SAMPLE;
    bytesRead = priorReadBytes;
    this.currentOutput = outputToUse;
    this.currentSampleDuration = currentSampleDuration;
    this.sampleSize = sampleSize;
  }

  /** Sets the state to STATE_READING_ADTS_HEADER. */
  private void setReadingAdtsHeaderState() {
    state = STATE_READING_ADTS_HEADER;
    bytesRead = 0;
  }

  /** Sets the state to STATE_CHECKING_ADTS_HEADER. */
  private void setCheckingAdtsHeaderState() {
    state = STATE_CHECKING_ADTS_HEADER;
    bytesRead = 0;
  }

  /**
   * Locates the next sample start, advancing the position to the byte that immediately follows
   * identifier. If a sample was not located, the position is advanced to the limit.
   *
   * @param pesBuffer The buffer whose position should be advanced.
   */
  private void findNextSample(ParsableByteArray pesBuffer) {
    byte[] adtsData = pesBuffer.getData();
    int position = pesBuffer.getPosition();
    int endOffset = pesBuffer.limit();
    while (position < endOffset) {
      int data = adtsData[position++] & 0xFF;
      if (matchState == MATCH_STATE_FF && isAdtsSyncBytes((byte) 0xFF, (byte) data)) {
        if (foundFirstFrame
            || checkSyncPositionValid(pesBuffer, /* syncPositionCandidate= */ position - 2)) {
          currentFrameVersion = (data & 0x8) >> 3;
          hasCrc = (data & 0x1) == 0;
          if (!foundFirstFrame) {
            setCheckingAdtsHeaderState();
          } else {
            setReadingAdtsHeaderState();
          }
          pesBuffer.setPosition(position);
          return;
        }
      }

      switch (matchState | data) {
        case MATCH_STATE_START | 0xFF:
          matchState = MATCH_STATE_FF;
          break;
        case MATCH_STATE_START | 'I':
          matchState = MATCH_STATE_I;
          break;
        case MATCH_STATE_I | 'D':
          matchState = MATCH_STATE_ID;
          break;
        case MATCH_STATE_ID | '3':
          setReadingId3HeaderState();
          pesBuffer.setPosition(position);
          return;
        default:
          if (matchState != MATCH_STATE_START) {
            // If matching fails in a later state, revert to MATCH_STATE_START and
            // check this byte again
            matchState = MATCH_STATE_START;
            position--;
          }
          break;
      }
    }
    pesBuffer.setPosition(position);
  }

  /**
   * Peeks the Adts header of the current frame and checks if it is valid. If the header is valid,
   * transition to {@link #STATE_READING_ADTS_HEADER}; else, transition to {@link
   * #STATE_FINDING_SAMPLE}.
   */
  private void checkAdtsHeader(ParsableByteArray buffer) {
    if (buffer.bytesLeft() == 0) {
      // Not enough data to check yet, defer this check.
      return;
    }
    // Peek the next byte of buffer into scratch array.
    adtsScratch.data[0] = buffer.getData()[buffer.getPosition()];

    adtsScratch.setPosition(2);
    int currentFrameSampleRateIndex = adtsScratch.readBits(4);
    if (firstFrameSampleRateIndex != C.INDEX_UNSET
        && currentFrameSampleRateIndex != firstFrameSampleRateIndex) {
      // Invalid header.
      resetSync();
      return;
    }

    if (!foundFirstFrame) {
      foundFirstFrame = true;
      firstFrameVersion = currentFrameVersion;
      firstFrameSampleRateIndex = currentFrameSampleRateIndex;
    }
    setReadingAdtsHeaderState();
  }

  /**
   * Checks whether a candidate SYNC word position is likely to be the position of a real SYNC word.
   * The caller must check that the first byte of the SYNC word is 0xFF before calling this method.
   * This method performs the following checks:
   *
   * <ul>
   *   <li>The MPEG version of this frame must match the previously detected version.
   *   <li>The sample rate index of this frame must match the previously detected sample rate index.
   *   <li>The frame size must be at least 7 bytes
   *   <li>The bytes following the frame must be either another SYNC word with the same MPEG
   *       version, or the start of an ID3 header.
   * </ul>
   *
   * With the exception of the first check, if there is insufficient data in the buffer then checks
   * are optimistically skipped and {@code true} is returned.
   *
   * @param pesBuffer The buffer containing at data to check.
   * @param syncPositionCandidate The candidate SYNC word position. May be -1 if the first byte of
   *     the candidate was the last byte of the previously consumed buffer.
   * @return True if all checks were passed or skipped, indicating the position is likely to be the
   *     position of a real SYNC word. False otherwise.
   */
  private boolean checkSyncPositionValid(ParsableByteArray pesBuffer, int syncPositionCandidate) {
    pesBuffer.setPosition(syncPositionCandidate + 1);
    if (!tryRead(pesBuffer, adtsScratch.data, 1)) {
      return false;
    }

    // The MPEG version of this frame must match the previously detected version.
    adtsScratch.setPosition(4);
    int currentFrameVersion = adtsScratch.readBits(1);
    if (firstFrameVersion != VERSION_UNSET && currentFrameVersion != firstFrameVersion) {
      return false;
    }

    // The sample rate index of this frame must match the previously detected sample rate index.
    if (firstFrameSampleRateIndex != C.INDEX_UNSET) {
      if (!tryRead(pesBuffer, adtsScratch.data, 1)) {
        // Insufficient data for further checks.
        return true;
      }
      adtsScratch.setPosition(2);
      int currentFrameSampleRateIndex = adtsScratch.readBits(4);
      if (currentFrameSampleRateIndex != firstFrameSampleRateIndex) {
        return false;
      }
      pesBuffer.setPosition(syncPositionCandidate + 2);
    }

    // The frame size must be at least 7 bytes.
    if (!tryRead(pesBuffer, adtsScratch.data, 4)) {
      // Insufficient data for further checks.
      return true;
    }
    adtsScratch.setPosition(14);
    int frameSize = adtsScratch.readBits(13);
    if (frameSize < 7) {
      return false;
    }

    // The bytes following the frame must be either another SYNC word with the same MPEG version, or
    // the start of an ID3 header.
    byte[] data = pesBuffer.getData();
    int dataLimit = pesBuffer.limit();
    int nextSyncPosition = syncPositionCandidate + frameSize;
    if (nextSyncPosition >= dataLimit) {
      // Insufficient data for further checks.
      return true;
    }
    if (data[nextSyncPosition] == (byte) 0xFF) {
      if (nextSyncPosition + 1 == dataLimit) {
        // Insufficient data for further checks.
        return true;
      }
      return isAdtsSyncBytes((byte) 0xFF, data[nextSyncPosition + 1])
          && ((data[nextSyncPosition + 1] & 0x8) >> 3) == currentFrameVersion;
    } else {
      if (data[nextSyncPosition] != 'I') {
        return false;
      }
      if (nextSyncPosition + 1 == dataLimit) {
        // Insufficient data for further checks.
        return true;
      }
      if (data[nextSyncPosition + 1] != 'D') {
        return false;
      }
      if (nextSyncPosition + 2 == dataLimit) {
        // Insufficient data for further checks.
        return true;
      }
      return data[nextSyncPosition + 2] == '3';
    }
  }

  private boolean isAdtsSyncBytes(byte firstByte, byte secondByte) {
    int syncWord = (firstByte & 0xFF) << 8 | (secondByte & 0xFF);
    return isAdtsSyncWord(syncWord);
  }

  /** Reads {@code targetLength} bytes into target, and returns whether the read succeeded. */
  private boolean tryRead(ParsableByteArray source, byte[] target, int targetLength) {
    if (source.bytesLeft() < targetLength) {
      return false;
    }
    source.readBytes(target, /* offset= */ 0, targetLength);
    return true;
  }

  /** Parses the Id3 header. */
  @RequiresNonNull("id3Output")
  private void parseId3Header() {
    id3Output.sampleData(id3HeaderBuffer, ID3_HEADER_SIZE);
    id3HeaderBuffer.setPosition(ID3_SIZE_OFFSET);
    setReadingSampleState(
        id3Output, 0, ID3_HEADER_SIZE, id3HeaderBuffer.readSynchSafeInt() + ID3_HEADER_SIZE);
  }

  /** Parses the sample header. */
  @RequiresNonNull("output")
  private void parseAdtsHeader() throws ParserException {
    adtsScratch.setPosition(0);

    if (!hasOutputFormat) {
      int audioObjectType = adtsScratch.readBits(2) + 1;
      if (audioObjectType != 2) {
        // The stream indicates AAC-Main (1), AAC-SSR (3) or AAC-LTP (4). When the stream indicates
        // AAC-Main it's more likely that the stream contains HE-AAC (5), which cannot be
        // represented correctly in the 2 bit audio_object_type field in the ADTS header. In
        // practice when the stream indicates AAC-SSR or AAC-LTP it more commonly contains AAC-LC or
        // HE-AAC. Since most Android devices don't support AAC-Main, AAC-SSR or AAC-LTP, and since
        // indicating AAC-LC works for HE-AAC streams, we pretend that we're dealing with AAC-LC and
        // hope for the best. In practice this often works.
        // See: https://github.com/google/ExoPlayer/issues/774
        // See: https://github.com/google/ExoPlayer/issues/1383
        Log.w(TAG, "Detected audio object type: " + audioObjectType + ", but assuming AAC LC.");
        audioObjectType = 2;
      }

      adtsScratch.skipBits(5);
      int channelConfig = adtsScratch.readBits(3);

      byte[] audioSpecificConfig =
          AacUtil.buildAudioSpecificConfig(
              audioObjectType, firstFrameSampleRateIndex, channelConfig);
      AacUtil.Config aacConfig = AacUtil.parseAudioSpecificConfig(audioSpecificConfig);
      Format format =
          new Format.Builder()
              .setId(formatId)
              .setSampleMimeType(MimeTypes.AUDIO_AAC)
              .setCodecs(aacConfig.codecs)
              .setChannelCount(aacConfig.channelCount)
              .setSampleRate(aacConfig.sampleRateHz)
              .setInitializationData(Collections.singletonList(audioSpecificConfig))
              .setLanguage(language)
              .build();
      // In this class a sample is an access unit, but the MediaFormat sample rate specifies the
      // number of PCM audio samples per second.
      sampleDurationUs = (C.MICROS_PER_SECOND * 1024) / format.sampleRate;
      output.format(format);
      hasOutputFormat = true;
    } else {
      adtsScratch.skipBits(10);
    }

    adtsScratch.skipBits(4);
    int sampleSize = adtsScratch.readBits(13) - 2 /* the sync word */ - HEADER_SIZE;
    if (hasCrc) {
      sampleSize -= CRC_SIZE;
    }

    setReadingSampleState(output, sampleDurationUs, 0, sampleSize);
  }

  /** Reads the rest of the sample */
  @RequiresNonNull("currentOutput")
  private void readSample(ParsableByteArray data) {
    int bytesToRead = min(data.bytesLeft(), sampleSize - bytesRead);
    currentOutput.sampleData(data, bytesToRead);
    bytesRead += bytesToRead;
    if (bytesRead == sampleSize) {
      if (timeUs != C.TIME_UNSET) {
        currentOutput.sampleMetadata(timeUs, C.BUFFER_FLAG_KEY_FRAME, sampleSize, 0, null);
        timeUs += currentSampleDuration;
      }
      setFindingSampleState();
    }
  }

  @EnsuresNonNull({"output", "currentOutput", "id3Output"})
  private void assertTracksCreated() {
    Assertions.checkNotNull(output);
    Util.castNonNull(currentOutput);
    Util.castNonNull(id3Output);
  }
}
