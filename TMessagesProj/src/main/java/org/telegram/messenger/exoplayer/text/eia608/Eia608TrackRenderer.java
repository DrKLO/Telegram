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
package org.telegram.messenger.exoplayer.text.eia608;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import org.telegram.messenger.exoplayer.C;
import org.telegram.messenger.exoplayer.ExoPlaybackException;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.MediaFormatHolder;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.SampleSource;
import org.telegram.messenger.exoplayer.SampleSourceTrackRenderer;
import org.telegram.messenger.exoplayer.TrackRenderer;
import org.telegram.messenger.exoplayer.text.Cue;
import org.telegram.messenger.exoplayer.text.TextRenderer;
import org.telegram.messenger.exoplayer.util.Assertions;
import org.telegram.messenger.exoplayer.util.Util;
import java.util.Collections;
import java.util.TreeSet;

/**
 * A {@link TrackRenderer} for EIA-608 closed captions in a media stream.
 */
public final class Eia608TrackRenderer extends SampleSourceTrackRenderer implements Callback {

  private static final int MSG_INVOKE_RENDERER = 0;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;
  // The maximum duration that captions are parsed ahead of the current position.
  private static final int MAX_SAMPLE_READAHEAD_US = 5000000;

  private final Eia608Parser eia608Parser;
  private final TextRenderer textRenderer;
  private final Handler textRendererHandler;
  private final MediaFormatHolder formatHolder;
  private final SampleHolder sampleHolder;
  private final StringBuilder captionStringBuilder;
  private final TreeSet<ClosedCaptionList> pendingCaptionLists;

  private boolean inputStreamEnded;
  private int captionMode;
  private int captionRowCount;
  private String caption;
  private String lastRenderedCaption;
  private ClosedCaptionCtrl repeatableControl;

  /**
   * @param source A source from which samples containing EIA-608 closed captions can be read.
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   */
  public Eia608TrackRenderer(SampleSource source, TextRenderer textRenderer,
      Looper textRendererLooper) {
    super(source);
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    textRendererHandler = textRendererLooper == null ? null : new Handler(textRendererLooper, this);
    eia608Parser = new Eia608Parser();
    formatHolder = new MediaFormatHolder();
    sampleHolder = new SampleHolder(SampleHolder.BUFFER_REPLACEMENT_MODE_NORMAL);
    captionStringBuilder = new StringBuilder();
    pendingCaptionLists = new TreeSet<>();
  }

  @Override
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return eia608Parser.canParse(mediaFormat.mimeType);
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    super.onEnabled(track, positionUs, joining);
  }

  @Override
  protected void onDiscontinuity(long positionUs) {
    inputStreamEnded = false;
    repeatableControl = null;
    pendingCaptionLists.clear();
    clearPendingSample();
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
    setCaptionMode(CC_MODE_UNKNOWN);
    invokeRenderer(null);
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    if (isSamplePending()) {
      maybeParsePendingSample(positionUs);
    }

    int result = inputStreamEnded ? SampleSource.END_OF_STREAM : SampleSource.SAMPLE_READ;
    while (!isSamplePending() && result == SampleSource.SAMPLE_READ) {
      result = readSource(positionUs, formatHolder, sampleHolder);
      if (result == SampleSource.SAMPLE_READ) {
        maybeParsePendingSample(positionUs);
      } else if (result == SampleSource.END_OF_STREAM) {
        inputStreamEnded = true;
      }
    }

    while (!pendingCaptionLists.isEmpty()) {
      if (pendingCaptionLists.first().timeUs > positionUs) {
        // We're too early to render any of the pending caption lists.
        return;
      }
      // Remove and consume the next caption list.
      ClosedCaptionList nextCaptionList = pendingCaptionLists.pollFirst();
      consumeCaptionList(nextCaptionList);
      // Update the renderer, unless the caption list was marked for decoding only.
      if (!nextCaptionList.decodeOnly) {
        invokeRenderer(caption);
      }
    }
  }

  @Override
  protected long getBufferedPositionUs() {
    return TrackRenderer.END_OF_TRACK_US;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded;
  }

  @Override
  protected boolean isReady() {
    return true;
  }

  private void invokeRenderer(String text) {
    if (Util.areEqual(lastRenderedCaption, text)) {
      // No change.
      return;
    }
    this.lastRenderedCaption = text;
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_INVOKE_RENDERER, text).sendToTarget();
    } else {
      invokeRendererInternal(text);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_INVOKE_RENDERER:
        invokeRendererInternal((String) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternal(String cueText) {
    if (cueText == null) {
      textRenderer.onCues(Collections.<Cue>emptyList());
    } else {
      textRenderer.onCues(Collections.singletonList(new Cue(cueText)));
    }
  }

  private void maybeParsePendingSample(long positionUs) {
    if (sampleHolder.timeUs > positionUs + MAX_SAMPLE_READAHEAD_US) {
      // We're too early to parse the sample.
      return;
    }
    ClosedCaptionList holder = eia608Parser.parse(sampleHolder);
    clearPendingSample();
    if (holder != null) {
      pendingCaptionLists.add(holder);
    }
  }

  private void consumeCaptionList(ClosedCaptionList captionList) {
    int captionBufferSize = captionList.captions.length;
    if (captionBufferSize == 0) {
      return;
    }

    boolean isRepeatableControl = false;
    for (int i = 0; i < captionBufferSize; i++) {
      ClosedCaption caption = captionList.captions[i];
      if (caption.type == ClosedCaption.TYPE_CTRL) {
        ClosedCaptionCtrl captionCtrl = (ClosedCaptionCtrl) caption;
        isRepeatableControl = captionBufferSize == 1 && captionCtrl.isRepeatable();
        if (isRepeatableControl && repeatableControl != null
            && repeatableControl.cc1 == captionCtrl.cc1
            && repeatableControl.cc2 == captionCtrl.cc2) {
          repeatableControl = null;
          continue;
        } else if (isRepeatableControl) {
          repeatableControl = captionCtrl;
        }
        if (captionCtrl.isMiscCode()) {
          handleMiscCode(captionCtrl);
        } else if (captionCtrl.isPreambleAddressCode()) {
          handlePreambleAddressCode();
        }
      } else {
        handleText((ClosedCaptionText) caption);
      }
    }

    if (!isRepeatableControl) {
      repeatableControl = null;
    }
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
      caption = getDisplayCaption();
    }
  }

  private void handleText(ClosedCaptionText captionText) {
    if (captionMode != CC_MODE_UNKNOWN) {
      captionStringBuilder.append(captionText.text);
    }
  }

  private void handleMiscCode(ClosedCaptionCtrl captionCtrl) {
    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case ClosedCaptionCtrl.RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case ClosedCaptionCtrl.RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (captionCtrl.cc2) {
      case ClosedCaptionCtrl.ERASE_DISPLAYED_MEMORY:
        caption = null;
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          captionStringBuilder.setLength(0);
        }
        return;
      case ClosedCaptionCtrl.ERASE_NON_DISPLAYED_MEMORY:
        captionStringBuilder.setLength(0);
        return;
      case ClosedCaptionCtrl.END_OF_CAPTION:
        caption = getDisplayCaption();
        captionStringBuilder.setLength(0);
        return;
      case ClosedCaptionCtrl.CARRIAGE_RETURN:
        maybeAppendNewline();
        return;
      case ClosedCaptionCtrl.BACKSPACE:
        if (captionStringBuilder.length() > 0) {
          captionStringBuilder.setLength(captionStringBuilder.length() - 1);
        }
        return;
    }
  }

  private void handlePreambleAddressCode() {
    // TODO: Add better handling of this with specific positioning.
    maybeAppendNewline();
  }

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;
    // Clear the working memory.
    captionStringBuilder.setLength(0);
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_UNKNOWN) {
      // When switching to roll-up or unknown, we also need to clear the caption.
      caption = null;
    }
  }

  private void maybeAppendNewline() {
    int buildLength = captionStringBuilder.length();
    if (buildLength > 0 && captionStringBuilder.charAt(buildLength - 1) != '\n') {
      captionStringBuilder.append('\n');
    }
  }

  private String getDisplayCaption() {
    int buildLength = captionStringBuilder.length();
    if (buildLength == 0) {
      return null;
    }

    boolean endsWithNewline = captionStringBuilder.charAt(buildLength - 1) == '\n';
    if (buildLength == 1 && endsWithNewline) {
      return null;
    }

    int endIndex = endsWithNewline ? buildLength - 1 : buildLength;
    if (captionMode != CC_MODE_ROLL_UP) {
      return captionStringBuilder.substring(0, endIndex);
    }

    int startIndex = 0;
    int searchBackwardFromIndex = endIndex;
    for (int i = 0; i < captionRowCount && searchBackwardFromIndex != -1; i++) {
      searchBackwardFromIndex = captionStringBuilder.lastIndexOf("\n", searchBackwardFromIndex - 1);
    }
    if (searchBackwardFromIndex != -1) {
      startIndex = searchBackwardFromIndex + 1;
    }
    captionStringBuilder.delete(0, startIndex);
    return captionStringBuilder.substring(0, endIndex - startIndex);
  }

  private void clearPendingSample() {
    sampleHolder.timeUs = C.UNKNOWN_TIME_US;
    sampleHolder.clearData();
  }

  private boolean isSamplePending() {
    return sampleHolder.timeUs != C.UNKNOWN_TIME_US;
  }

}
