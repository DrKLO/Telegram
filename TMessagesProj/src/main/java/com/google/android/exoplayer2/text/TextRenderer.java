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
package com.google.android.exoplayer2.text;

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static java.lang.annotation.ElementType.TYPE_USE;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.BaseRenderer;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.FormatHolder;
import com.google.android.exoplayer2.RendererCapabilities;
import com.google.android.exoplayer2.source.SampleStream.ReadDataResult;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * A renderer for text.
 *
 * <p>{@link Subtitle}s are decoded from sample data using {@link SubtitleDecoder} instances
 * obtained from a {@link SubtitleDecoderFactory}. The actual rendering of the subtitle {@link Cue}s
 * is delegated to a {@link TextOutput}.
 */
public final class TextRenderer extends BaseRenderer implements Callback {

  private static final String TAG = "TextRenderer";

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    REPLACEMENT_STATE_NONE,
    REPLACEMENT_STATE_SIGNAL_END_OF_STREAM,
    REPLACEMENT_STATE_WAIT_END_OF_STREAM
  })
  private @interface ReplacementState {}
  /** The decoder does not need to be replaced. */
  private static final int REPLACEMENT_STATE_NONE = 0;
  /**
   * The decoder needs to be replaced, but we haven't yet signaled an end of stream to the existing
   * decoder. We need to do so in order to ensure that it outputs any remaining buffers before we
   * release it.
   */
  private static final int REPLACEMENT_STATE_SIGNAL_END_OF_STREAM = 1;
  /**
   * The decoder needs to be replaced, and we've signaled an end of stream to the existing decoder.
   * We're waiting for the decoder to output an end of stream signal to indicate that it has output
   * any remaining buffers before we release it.
   */
  private static final int REPLACEMENT_STATE_WAIT_END_OF_STREAM = 2;

  private static final int MSG_UPDATE_OUTPUT = 0;

  @Nullable private final Handler outputHandler;
  private final TextOutput output;
  private final SubtitleDecoderFactory decoderFactory;
  private final FormatHolder formatHolder;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private boolean waitingForKeyFrame;
  private @ReplacementState int decoderReplacementState;
  @Nullable private Format streamFormat;
  @Nullable private SubtitleDecoder decoder;
  @Nullable private SubtitleInputBuffer nextInputBuffer;
  @Nullable private SubtitleOutputBuffer subtitle;
  @Nullable private SubtitleOutputBuffer nextSubtitle;
  private int nextSubtitleEventIndex;
  private long finalStreamEndPositionUs;
  private long outputStreamOffsetUs;
  private long lastRendererPositionUs;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   */
  public TextRenderer(TextOutput output, @Nullable Looper outputLooper) {
    this(output, outputLooper, SubtitleDecoderFactory.DEFAULT);
  }

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be called.
   *     If the output makes use of standard Android UI components, then this should normally be the
   *     looper associated with the application's main thread, which can be obtained using {@link
   *     android.app.Activity#getMainLooper()}. Null may be passed if the output should be called
   *     directly on the player's internal rendering thread.
   * @param decoderFactory A factory from which to obtain {@link SubtitleDecoder} instances.
   */
  public TextRenderer(
      TextOutput output, @Nullable Looper outputLooper, SubtitleDecoderFactory decoderFactory) {
    super(C.TRACK_TYPE_TEXT);
    this.output = checkNotNull(output);
    this.outputHandler =
        outputLooper == null ? null : Util.createHandler(outputLooper, /* callback= */ this);
    this.decoderFactory = decoderFactory;
    formatHolder = new FormatHolder();
    finalStreamEndPositionUs = C.TIME_UNSET;
    outputStreamOffsetUs = C.TIME_UNSET;
    lastRendererPositionUs = C.TIME_UNSET;
  }

  @Override
  public String getName() {
    return TAG;
  }

  @Override
  public @Capabilities int supportsFormat(Format format) {
    if (decoderFactory.supportsFormat(format)) {
      return RendererCapabilities.create(
          format.cryptoType == C.CRYPTO_TYPE_NONE ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_DRM);
    } else if (MimeTypes.isText(format.sampleMimeType)) {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE);
    } else {
      return RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE);
    }
  }

  /**
   * Sets the position at which to stop rendering the current stream.
   *
   * <p>Must be called after {@link #setCurrentStreamFinal()}.
   *
   * @param streamEndPositionUs The position to stop rendering at or {@link C#LENGTH_UNSET} to
   *     render until the end of the current stream.
   */
  // TODO(internal b/181312195): Remove this when it's no longer needed once subtitles are decoded
  // on the loading side of SampleQueue.
  public void setFinalStreamEndPositionUs(long streamEndPositionUs) {
    checkState(isCurrentStreamFinal());
    this.finalStreamEndPositionUs = streamEndPositionUs;
  }

  @Override
  protected void onStreamChanged(Format[] formats, long startPositionUs, long offsetUs) {
    outputStreamOffsetUs = offsetUs;
    streamFormat = formats[0];
    if (decoder != null) {
      decoderReplacementState = REPLACEMENT_STATE_SIGNAL_END_OF_STREAM;
    } else {
      initDecoder();
    }
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    lastRendererPositionUs = positionUs;
    clearOutput();
    inputStreamEnded = false;
    outputStreamEnded = false;
    finalStreamEndPositionUs = C.TIME_UNSET;
    if (decoderReplacementState != REPLACEMENT_STATE_NONE) {
      replaceDecoder();
    } else {
      releaseBuffers();
      checkNotNull(decoder).flush();
    }
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) {
    lastRendererPositionUs = positionUs;
    if (isCurrentStreamFinal()
        && finalStreamEndPositionUs != C.TIME_UNSET
        && positionUs >= finalStreamEndPositionUs) {
      releaseBuffers();
      outputStreamEnded = true;
    }

    if (outputStreamEnded) {
      return;
    }

    if (nextSubtitle == null) {
      checkNotNull(decoder).setPositionUs(positionUs);
      try {
        nextSubtitle = checkNotNull(decoder).dequeueOutputBuffer();
      } catch (SubtitleDecoderException e) {
        handleDecoderError(e);
        return;
      }
    }

    if (getState() != STATE_STARTED) {
      return;
    }

    boolean textRendererNeedsUpdate = false;
    if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      long subtitleNextEventTimeUs = getNextEventTime();
      while (subtitleNextEventTimeUs <= positionUs) {
        nextSubtitleEventIndex++;
        subtitleNextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
    }
    if (nextSubtitle != null) {
      SubtitleOutputBuffer nextSubtitle = this.nextSubtitle;
      if (nextSubtitle.isEndOfStream()) {
        if (!textRendererNeedsUpdate && getNextEventTime() == Long.MAX_VALUE) {
          if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
            replaceDecoder();
          } else {
            releaseBuffers();
            outputStreamEnded = true;
          }
        }
      } else if (nextSubtitle.timeUs <= positionUs) {
        // Advance to the next subtitle. Sync the next event index and trigger an update.
        if (subtitle != null) {
          subtitle.release();
        }
        nextSubtitleEventIndex = nextSubtitle.getNextEventTimeIndex(positionUs);
        subtitle = nextSubtitle;
        this.nextSubtitle = null;
        textRendererNeedsUpdate = true;
      }
    }

    if (textRendererNeedsUpdate) {
      // If textRendererNeedsUpdate then subtitle must be non-null.
      checkNotNull(subtitle);
      // textRendererNeedsUpdate is set and we're playing. Update the renderer.
      long presentationTimeUs = getPresentationTimeUs(getCurrentEventTimeUs(positionUs));
      CueGroup cueGroup = new CueGroup(subtitle.getCues(positionUs), presentationTimeUs);
      updateOutput(cueGroup);
    }

    if (decoderReplacementState == REPLACEMENT_STATE_WAIT_END_OF_STREAM) {
      return;
    }

    try {
      while (!inputStreamEnded) {
        @Nullable SubtitleInputBuffer nextInputBuffer = this.nextInputBuffer;
        if (nextInputBuffer == null) {
          nextInputBuffer = checkNotNull(decoder).dequeueInputBuffer();
          if (nextInputBuffer == null) {
            return;
          }
          this.nextInputBuffer = nextInputBuffer;
        }
        if (decoderReplacementState == REPLACEMENT_STATE_SIGNAL_END_OF_STREAM) {
          nextInputBuffer.setFlags(C.BUFFER_FLAG_END_OF_STREAM);
          checkNotNull(decoder).queueInputBuffer(nextInputBuffer);
          this.nextInputBuffer = null;
          decoderReplacementState = REPLACEMENT_STATE_WAIT_END_OF_STREAM;
          return;
        }
        // Try and read the next subtitle from the source.
        @ReadDataResult int result = readSource(formatHolder, nextInputBuffer, /* readFlags= */ 0);
        if (result == C.RESULT_BUFFER_READ) {
          if (nextInputBuffer.isEndOfStream()) {
            inputStreamEnded = true;
            waitingForKeyFrame = false;
          } else {
            @Nullable Format format = formatHolder.format;
            if (format == null) {
              // We haven't received a format yet.
              return;
            }
            nextInputBuffer.subsampleOffsetUs = format.subsampleOffsetUs;
            nextInputBuffer.flip();
            waitingForKeyFrame &= !nextInputBuffer.isKeyFrame();
          }
          if (!waitingForKeyFrame) {
            checkNotNull(decoder).queueInputBuffer(nextInputBuffer);
            this.nextInputBuffer = null;
          }
        } else if (result == C.RESULT_NOTHING_READ) {
          return;
        }
      }
    } catch (SubtitleDecoderException e) {
      handleDecoderError(e);
    }
  }

  @Override
  protected void onDisabled() {
    streamFormat = null;
    finalStreamEndPositionUs = C.TIME_UNSET;
    clearOutput();
    outputStreamOffsetUs = C.TIME_UNSET;
    lastRendererPositionUs = C.TIME_UNSET;
    releaseDecoder();
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private void releaseBuffers() {
    nextInputBuffer = null;
    nextSubtitleEventIndex = C.INDEX_UNSET;
    if (subtitle != null) {
      subtitle.release();
      subtitle = null;
    }
    if (nextSubtitle != null) {
      nextSubtitle.release();
      nextSubtitle = null;
    }
  }

  private void releaseDecoder() {
    releaseBuffers();
    checkNotNull(decoder).release();
    decoder = null;
    decoderReplacementState = REPLACEMENT_STATE_NONE;
  }

  private void initDecoder() {
    waitingForKeyFrame = true;
    decoder = decoderFactory.createDecoder(checkNotNull(streamFormat));
  }

  private void replaceDecoder() {
    releaseDecoder();
    initDecoder();
  }

  private long getNextEventTime() {
    if (nextSubtitleEventIndex == C.INDEX_UNSET) {
      return Long.MAX_VALUE;
    }
    checkNotNull(subtitle);
    return nextSubtitleEventIndex >= subtitle.getEventTimeCount()
        ? Long.MAX_VALUE
        : subtitle.getEventTime(nextSubtitleEventIndex);
  }

  private void updateOutput(CueGroup cueGroup) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_UPDATE_OUTPUT, cueGroup).sendToTarget();
    } else {
      invokeUpdateOutputInternal(cueGroup);
    }
  }

  private void clearOutput() {
    updateOutput(new CueGroup(ImmutableList.of(), getPresentationTimeUs(lastRendererPositionUs)));
  }

  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OUTPUT:
        invokeUpdateOutputInternal((CueGroup) msg.obj);
        return true;
      default:
        throw new IllegalStateException();
    }
  }

  @SuppressWarnings("deprecation") // We need to call both onCues method for backward compatibility.
  private void invokeUpdateOutputInternal(CueGroup cueGroup) {
    output.onCues(cueGroup.cues);
    output.onCues(cueGroup);
  }

  /**
   * Called when {@link #decoder} throws an exception, so it can be logged and playback can
   * continue.
   *
   * <p>Logs {@code e} and resets state to allow decoding the next sample.
   */
  private void handleDecoderError(SubtitleDecoderException e) {
    Log.e(TAG, "Subtitle decoding failed. streamFormat=" + streamFormat, e);
    clearOutput();
    replaceDecoder();
  }

  @RequiresNonNull("subtitle")
  @SideEffectFree
  private long getCurrentEventTimeUs(long positionUs) {
    int nextEventTimeIndex = subtitle.getNextEventTimeIndex(positionUs);
    if (nextEventTimeIndex == 0 || subtitle.getEventTimeCount() == 0) {
      return subtitle.timeUs;
    }

    return nextEventTimeIndex == C.INDEX_UNSET
        ? subtitle.getEventTime(subtitle.getEventTimeCount() - 1)
        : subtitle.getEventTime(nextEventTimeIndex - 1);
  }

  @SideEffectFree
  private long getPresentationTimeUs(long positionUs) {
    checkState(positionUs != C.TIME_UNSET);
    checkState(outputStreamOffsetUs != C.TIME_UNSET);

    return positionUs - outputStreamOffsetUs;
  }
}
