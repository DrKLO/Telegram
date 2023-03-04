/*
 * Copyright 2021 The Android Open Source Project
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
package com.google.android.exoplayer2.text;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.extractor.Extractor;
import com.google.android.exoplayer2.extractor.ExtractorInput;
import com.google.android.exoplayer2.extractor.ExtractorOutput;
import com.google.android.exoplayer2.extractor.IndexSeekMap;
import com.google.android.exoplayer2.extractor.PositionHolder;
import com.google.android.exoplayer2.extractor.TrackOutput;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import com.google.common.primitives.Ints;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Generic extractor for extracting subtitles from various subtitle formats. */
public class SubtitleExtractor implements Extractor {
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    STATE_CREATED,
    STATE_INITIALIZED,
    STATE_EXTRACTING,
    STATE_SEEKING,
    STATE_FINISHED,
    STATE_RELEASED
  })
  private @interface State {}

  /** The extractor has been created. */
  private static final int STATE_CREATED = 0;
  /** The extractor has been initialized. */
  private static final int STATE_INITIALIZED = 1;
  /** The extractor is reading from the input and writing to the output. */
  private static final int STATE_EXTRACTING = 2;
  /** The extractor has received a seek() operation after it has already finished extracting. */
  private static final int STATE_SEEKING = 3;
  /** The extractor has finished extracting the input. */
  private static final int STATE_FINISHED = 4;
  /** The extractor has been released. */
  private static final int STATE_RELEASED = 5;

  private static final int DEFAULT_BUFFER_SIZE = 1024;

  private final SubtitleDecoder subtitleDecoder;
  private final CueEncoder cueEncoder;
  private final ParsableByteArray subtitleData;
  private final Format format;
  private final List<Long> timestamps;
  private final List<ParsableByteArray> samples;

  private @MonotonicNonNull ExtractorOutput extractorOutput;
  private @MonotonicNonNull TrackOutput trackOutput;
  private int bytesRead;
  private @State int state;
  private long seekTimeUs;

  /**
   * @param subtitleDecoder The decoder used for decoding the subtitle data. The extractor will
   *     release the decoder in {@link SubtitleExtractor#release()}.
   * @param format Format that describes subtitle data.
   */
  public SubtitleExtractor(SubtitleDecoder subtitleDecoder, Format format) {
    this.subtitleDecoder = subtitleDecoder;
    cueEncoder = new CueEncoder();
    subtitleData = new ParsableByteArray();
    this.format =
        format
            .buildUpon()
            .setSampleMimeType(MimeTypes.TEXT_EXOPLAYER_CUES)
            .setCodecs(format.sampleMimeType)
            .build();
    timestamps = new ArrayList<>();
    samples = new ArrayList<>();
    state = STATE_CREATED;
    seekTimeUs = C.TIME_UNSET;
  }

  @Override
  public boolean sniff(ExtractorInput input) throws IOException {
    // TODO: Implement sniff() according to the Extractor interface documentation. For now sniff()
    // can safely return true because we plan to use this class in an ExtractorFactory that returns
    // exactly one Extractor implementation.
    return true;
  }

  @Override
  public void init(ExtractorOutput output) {
    checkState(state == STATE_CREATED);
    extractorOutput = output;
    trackOutput = extractorOutput.track(/* id= */ 0, C.TRACK_TYPE_TEXT);
    extractorOutput.endTracks();
    extractorOutput.seekMap(
        new IndexSeekMap(
            /* positions= */ new long[] {0},
            /* timesUs= */ new long[] {0},
            /* durationUs= */ C.TIME_UNSET));
    trackOutput.format(format);
    state = STATE_INITIALIZED;
  }

  @Override
  public int read(ExtractorInput input, PositionHolder seekPosition) throws IOException {
    checkState(state != STATE_CREATED && state != STATE_RELEASED);
    if (state == STATE_INITIALIZED) {
      subtitleData.reset(
          input.getLength() != C.LENGTH_UNSET
              ? Ints.checkedCast(input.getLength())
              : DEFAULT_BUFFER_SIZE);
      bytesRead = 0;
      state = STATE_EXTRACTING;
    }
    if (state == STATE_EXTRACTING) {
      boolean inputFinished = readFromInput(input);
      if (inputFinished) {
        decode();
        writeToOutput();
        state = STATE_FINISHED;
      }
    }
    if (state == STATE_SEEKING) {
      boolean inputFinished = skipInput(input);
      if (inputFinished) {
        writeToOutput();
        state = STATE_FINISHED;
      }
    }
    if (state == STATE_FINISHED) {
      return RESULT_END_OF_INPUT;
    }
    return RESULT_CONTINUE;
  }

  @Override
  public void seek(long position, long timeUs) {
    checkState(state != STATE_CREATED && state != STATE_RELEASED);
    seekTimeUs = timeUs;
    if (state == STATE_EXTRACTING) {
      state = STATE_INITIALIZED;
    }
    if (state == STATE_FINISHED) {
      state = STATE_SEEKING;
    }
  }

  /** Releases the extractor's resources, including the {@link SubtitleDecoder}. */
  @Override
  public void release() {
    if (state == STATE_RELEASED) {
      return;
    }
    subtitleDecoder.release();
    state = STATE_RELEASED;
  }

  /** Returns whether the input has been fully skipped. */
  private boolean skipInput(ExtractorInput input) throws IOException {
    return input.skip(
            input.getLength() != C.LENGTH_UNSET
                ? Ints.checkedCast(input.getLength())
                : DEFAULT_BUFFER_SIZE)
        == C.RESULT_END_OF_INPUT;
  }

  /** Returns whether reading has been finished. */
  private boolean readFromInput(ExtractorInput input) throws IOException {
    if (subtitleData.capacity() == bytesRead) {
      subtitleData.ensureCapacity(bytesRead + DEFAULT_BUFFER_SIZE);
    }
    int readResult =
        input.read(subtitleData.getData(), bytesRead, subtitleData.capacity() - bytesRead);
    if (readResult != C.RESULT_END_OF_INPUT) {
      bytesRead += readResult;
    }
    long inputLength = input.getLength();
    return (inputLength != C.LENGTH_UNSET && bytesRead == inputLength)
        || readResult == C.RESULT_END_OF_INPUT;
  }

  /** Decodes the subtitle data and stores the samples in the memory of the extractor. */
  private void decode() throws IOException {
    try {
      @Nullable SubtitleInputBuffer inputBuffer = subtitleDecoder.dequeueInputBuffer();
      while (inputBuffer == null) {
        Thread.sleep(5);
        inputBuffer = subtitleDecoder.dequeueInputBuffer();
      }
      inputBuffer.ensureSpaceForWrite(bytesRead);
      inputBuffer.data.put(subtitleData.getData(), /* offset= */ 0, bytesRead);
      inputBuffer.data.limit(bytesRead);
      subtitleDecoder.queueInputBuffer(inputBuffer);
      @Nullable SubtitleOutputBuffer outputBuffer = subtitleDecoder.dequeueOutputBuffer();
      while (outputBuffer == null) {
        Thread.sleep(5);
        outputBuffer = subtitleDecoder.dequeueOutputBuffer();
      }
      for (int i = 0; i < outputBuffer.getEventTimeCount(); i++) {
        List<Cue> cues = outputBuffer.getCues(outputBuffer.getEventTime(i));
        byte[] cuesSample = cueEncoder.encode(cues);
        timestamps.add(outputBuffer.getEventTime(i));
        samples.add(new ParsableByteArray(cuesSample));
      }
      outputBuffer.release();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new InterruptedIOException();
    } catch (SubtitleDecoderException e) {
      throw ParserException.createForMalformedContainer("SubtitleDecoder failed.", e);
    }
  }

  private void writeToOutput() {
    checkStateNotNull(this.trackOutput);
    checkState(timestamps.size() == samples.size());
    int index =
        seekTimeUs == C.TIME_UNSET
            ? 0
            : Util.binarySearchFloor(
                timestamps, seekTimeUs, /* inclusive= */ true, /* stayInBounds= */ true);
    for (int i = index; i < samples.size(); i++) {
      ParsableByteArray sample = samples.get(i);
      sample.setPosition(0);
      int size = sample.getData().length;
      trackOutput.sampleData(sample, size);
      trackOutput.sampleMetadata(
          /* timeUs= */ timestamps.get(i),
          /* flags= */ C.BUFFER_FLAG_KEY_FRAME,
          /* size= */ size,
          /* offset= */ 0,
          /* cryptoData= */ null);
    }
  }
}
