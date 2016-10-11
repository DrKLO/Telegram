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
package org.telegram.messenger.exoplayer.text;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.ParserException;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.io.IOException;

/**
 * Wraps a {@link SubtitleParser}, exposing an interface similar to {@link MediaCodec} for
 * asynchronous parsing of subtitles.
 */
/* package */ final class SubtitleParserHelper implements Handler.Callback {

  private static final int MSG_FORMAT = 0;
  private static final int MSG_SAMPLE = 1;

  private final SubtitleParser parser;
  private final Handler handler;

  private SampleHolder sampleHolder;
  private boolean parsing;
  private PlayableSubtitle result;
  private IOException error;
  private RuntimeException runtimeError;

  private boolean subtitlesAreRelative;
  private long subtitleOffsetUs;

  /**
   * @param looper The {@link Looper} associated with the thread on which parsing should occur.
   * @param parser The parser that should be used to parse the raw data.
   */
  public SubtitleParserHelper(Looper looper, SubtitleParser parser) {
    this.handler = new Handler(looper, this);
    this.parser = parser;
    flush();
  }

  /**
   * Flushes the helper, canceling the current parsing operation, if there is one.
   */
  public synchronized void flush() {
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    parsing = false;
    result = null;
    error = null;
    runtimeError = null;
  }

  /**
   * Whether the helper is currently performing a parsing operation.
   *
   * @return True if the helper is currently performing a parsing operation. False otherwise.
   */
  public synchronized boolean isParsing() {
    return parsing;
  }

  /**
   * Gets the holder that should be populated with data to be parsed.
   * <p>
   * The returned holder will remain valid unless {@link #flush()} is called. If {@link #flush()}
   * is called the holder is replaced, and this method should be called again to obtain the new
   * holder.
   *
   * @return The holder that should be populated with data to be parsed.
   */
  public synchronized SampleHolder getSampleHolder() {
    return sampleHolder;
  }

  /**
   * Sets the format of subsequent samples.
   *
   * @param format The format.
   */
  public void setFormat(MediaFormat format) {
    handler.obtainMessage(MSG_FORMAT, format).sendToTarget();
  }

  /**
   * Start a parsing operation.
   * <p>
   * The holder returned by {@link #getSampleHolder()} should be populated with the data to be
   * parsed prior to calling this method.
   */
  public synchronized void startParseOperation() {
    Assertions.checkState(!parsing);
    parsing = true;
    result = null;
    error = null;
    runtimeError = null;
    handler.obtainMessage(MSG_SAMPLE, Util.getTopInt(sampleHolder.timeUs),
        Util.getBottomInt(sampleHolder.timeUs), sampleHolder).sendToTarget();
  }

  /**
   * Gets the result of the most recent parsing operation.
   * <p>
   * The result is cleared as a result of calling this method, and so subsequent calls will return
   * null until a subsequent parsing operation has finished.
   *
   * @return The result of the parsing operation, or null.
   * @throws IOException If the parsing operation failed.
   */
  public synchronized PlayableSubtitle getAndClearResult() throws IOException {
    try {
      if (error != null) {
        throw error;
      } else if (runtimeError != null) {
        throw runtimeError;
      } else {
        return result;
      }
    } finally {
      result = null;
      error = null;
      runtimeError = null;
    }
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_FORMAT:
        handleFormat((MediaFormat) msg.obj);
        break;
      case MSG_SAMPLE:
        long sampleTimeUs = Util.getLong(msg.arg1, msg.arg2);
        SampleHolder holder = (SampleHolder) msg.obj;
        handleSample(sampleTimeUs, holder);
        break;
    }
    return true;
  }

  private void handleFormat(MediaFormat format) {
    subtitlesAreRelative = format.subsampleOffsetUs == MediaFormat.OFFSET_SAMPLE_RELATIVE;
    subtitleOffsetUs = subtitlesAreRelative ? 0 : format.subsampleOffsetUs;
  }

  private void handleSample(long sampleTimeUs, SampleHolder holder) {
    Subtitle parsedSubtitle = null;
    ParserException error = null;
    RuntimeException runtimeError = null;
    try {
      parsedSubtitle = parser.parse(holder.data.array(), 0, holder.size);
    } catch (ParserException e) {
      error = e;
    } catch (RuntimeException e) {
      runtimeError = e;
    }
    synchronized (this) {
      if (sampleHolder != holder) {
        // A flush has occurred since this holder was posted. Do nothing.
      } else {
        this.result = new PlayableSubtitle(parsedSubtitle, subtitlesAreRelative, sampleTimeUs,
            subtitleOffsetUs);
        this.error = error;
        this.runtimeError = runtimeError;
        this.parsing = false;
      }
    }
  }

}
