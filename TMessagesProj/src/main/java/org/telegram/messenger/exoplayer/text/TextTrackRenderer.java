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

import android.annotation.TargetApi;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import org.telegram.messenger.exoplayer.ExoPlaybackException;
import org.telegram.messenger.exoplayer.MediaFormat;
import org.telegram.messenger.exoplayer.MediaFormatHolder;
import org.telegram.messenger.exoplayer.SampleHolder;
import org.telegram.messenger.exoplayer.SampleSource;
import org.telegram.messenger.exoplayer.SampleSourceTrackRenderer;
import org.telegram.messenger.exoplayer.TrackRenderer;
import org.telegram.messenger.exoplayer.util.Assertions;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A {@link TrackRenderer} for subtitles. Text is parsed from sample data using a
 * {@link SubtitleParser}. The actual rendering of each line of text is delegated to a
 * {@link TextRenderer}.
 * <p>
 * If no {@link SubtitleParser} instances are passed to the constructor, the subtitle type will be
 * detected automatically for the following supported formats:
 *
 * <ul>
 * <li>WebVTT ({@link org.telegram.messenger.exoplayer.text.webvtt.WebvttParser})</li>
 * <li>TTML
 * ({@link org.telegram.messenger.exoplayer.text.ttml.TtmlParser})</li>
 * <li>SubRip
 * ({@link org.telegram.messenger.exoplayer.text.subrip.SubripParser})</li>
 * <li>TX3G
 * ({@link org.telegram.messenger.exoplayer.text.tx3g.Tx3gParser})</li>
 * </ul>
 *
 * <p>To override the default parsers, pass one or more {@link SubtitleParser} instances to the
 * constructor. The first {@link SubtitleParser} that returns {@code true} from
 * {@link SubtitleParser#canParse(String)} will be used.
 */
@TargetApi(16)
public final class TextTrackRenderer extends SampleSourceTrackRenderer implements Callback {

  private static final int MSG_UPDATE_OVERLAY = 0;

  /**
   * Default parser classes in priority order. They are referred to indirectly so that it is
   * possible to remove unused parsers.
   */
  private static final List<Class<? extends SubtitleParser>> DEFAULT_PARSER_CLASSES;
  static {
    DEFAULT_PARSER_CLASSES = new ArrayList<>();
    // Load parsers using reflection so that they can be deleted cleanly.
    // Class.forName(<class name>) appears for each parser so that automated tools like proguard
    // can detect the use of reflection (see http://proguard.sourceforge.net/FAQ.html#forname).
    try {
      DEFAULT_PARSER_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.text.webvtt.WebvttParser")
              .asSubclass(SubtitleParser.class));
    } catch (ClassNotFoundException e) {
      // Parser not found.
    }
    try {
      DEFAULT_PARSER_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.text.ttml.TtmlParser")
              .asSubclass(SubtitleParser.class));
    } catch (ClassNotFoundException e) {
      // Parser not found.
    }
    try {
      DEFAULT_PARSER_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.text.webvtt.Mp4WebvttParser")
              .asSubclass(SubtitleParser.class));
    } catch (ClassNotFoundException e) {
      // Parser not found.
    }
    try {
      DEFAULT_PARSER_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.text.subrip.SubripParser")
              .asSubclass(SubtitleParser.class));
    } catch (ClassNotFoundException e) {
      // Parser not found.
    }
    try {
      DEFAULT_PARSER_CLASSES.add(
          Class.forName("org.telegram.messenger.exoplayer.text.tx3g.Tx3gParser")
              .asSubclass(SubtitleParser.class));
    } catch (ClassNotFoundException e) {
      // Parser not found.
    }
  }

  private final Handler textRendererHandler;
  private final TextRenderer textRenderer;
  private final MediaFormatHolder formatHolder;
  private final SubtitleParser[] subtitleParsers;

  private int parserIndex;
  private boolean inputStreamEnded;
  private PlayableSubtitle subtitle;
  private PlayableSubtitle nextSubtitle;
  private SubtitleParserHelper parserHelper;
  private HandlerThread parserThread;
  private int nextSubtitleEventIndex;

  /**
   * @param source A source from which samples containing subtitle data can be read.
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   * @param subtitleParsers {@link SubtitleParser}s to parse text samples, in order of decreasing
   *     priority. If omitted, the default parsers will be used.
   */
  public TextTrackRenderer(SampleSource source, TextRenderer textRenderer,
      Looper textRendererLooper, SubtitleParser... subtitleParsers) {
    this(new SampleSource[] {source}, textRenderer, textRendererLooper, subtitleParsers);
  }

  /**
   * @param sources Sources from which samples containing subtitle data can be read.
   * @param textRenderer The text renderer.
   * @param textRendererLooper The looper associated with the thread on which textRenderer should be
   *     invoked. If the renderer makes use of standard Android UI components, then this should
   *     normally be the looper associated with the applications' main thread, which can be
   *     obtained using {@link android.app.Activity#getMainLooper()}. Null may be passed if the
   *     renderer should be invoked directly on the player's internal rendering thread.
   * @param subtitleParsers {@link SubtitleParser}s to parse text samples, in order of decreasing
   *     priority. If omitted, the default parsers will be used.
   */
  public TextTrackRenderer(SampleSource[] sources, TextRenderer textRenderer,
      Looper textRendererLooper, SubtitleParser... subtitleParsers) {
    super(sources);
    this.textRenderer = Assertions.checkNotNull(textRenderer);
    this.textRendererHandler = textRendererLooper == null ? null
        : new Handler(textRendererLooper, this);
    if (subtitleParsers == null || subtitleParsers.length == 0) {
      subtitleParsers = new SubtitleParser[DEFAULT_PARSER_CLASSES.size()];
      for (int i = 0; i < subtitleParsers.length; i++) {
        try {
          subtitleParsers[i] = DEFAULT_PARSER_CLASSES.get(i).newInstance();
        } catch (InstantiationException e) {
          throw new IllegalStateException("Unexpected error creating default parser", e);
        } catch (IllegalAccessException e) {
          throw new IllegalStateException("Unexpected error creating default parser", e);
        }
      }
    }
    this.subtitleParsers = subtitleParsers;
    formatHolder = new MediaFormatHolder();
  }

  @Override
  protected boolean handlesTrack(MediaFormat mediaFormat) {
    return getParserIndex(mediaFormat) != -1;
  }

  @Override
  protected void onEnabled(int track, long positionUs, boolean joining)
      throws ExoPlaybackException {
    super.onEnabled(track, positionUs, joining);
    parserIndex = getParserIndex(getFormat(track));
    parserThread = new HandlerThread("textParser");
    parserThread.start();
    parserHelper = new SubtitleParserHelper(parserThread.getLooper(), subtitleParsers[parserIndex]);
  }

  @Override
  protected void onDiscontinuity(long positionUs) {
    inputStreamEnded = false;
    subtitle = null;
    nextSubtitle = null;
    clearTextRenderer();
    if (parserHelper != null) {
      parserHelper.flush();
    }
  }

  @Override
  protected void doSomeWork(long positionUs, long elapsedRealtimeUs, boolean sourceIsReady)
      throws ExoPlaybackException {
    if (nextSubtitle == null) {
      try {
        nextSubtitle = parserHelper.getAndClearResult();
      } catch (IOException e) {
        throw new ExoPlaybackException(e);
      }
    }

    if (getState() != TrackRenderer.STATE_STARTED) {
      return;
    }

    boolean textRendererNeedsUpdate = false;
    long subtitleNextEventTimeUs = Long.MAX_VALUE;
    if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      subtitleNextEventTimeUs = getNextEventTime();
      while (subtitleNextEventTimeUs <= positionUs) {
        nextSubtitleEventIndex++;
        subtitleNextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
    }

    if (nextSubtitle != null && nextSubtitle.startTimeUs <= positionUs) {
      // Advance to the next subtitle. Sync the next event index and trigger an update.
      subtitle = nextSubtitle;
      nextSubtitle = null;
      nextSubtitleEventIndex = subtitle.getNextEventTimeIndex(positionUs);
      textRendererNeedsUpdate = true;
    }

    if (textRendererNeedsUpdate) {
      // textRendererNeedsUpdate is set and we're playing. Update the renderer.
      updateTextRenderer(subtitle.getCues(positionUs));
    }

    if (!inputStreamEnded && nextSubtitle == null && !parserHelper.isParsing()) {
      // Try and read the next subtitle from the source.
      SampleHolder sampleHolder = parserHelper.getSampleHolder();
      sampleHolder.clearData();
      int result = readSource(positionUs, formatHolder, sampleHolder);
      if (result == SampleSource.FORMAT_READ) {
        parserHelper.setFormat(formatHolder.format);
      } else if (result == SampleSource.SAMPLE_READ) {
        parserHelper.startParseOperation();
      } else if (result == SampleSource.END_OF_STREAM) {
        inputStreamEnded = true;
      }
    }
  }

  @Override
  protected void onDisabled() throws ExoPlaybackException {
    subtitle = null;
    nextSubtitle = null;
    parserThread.quit();
    parserThread = null;
    parserHelper = null;
    clearTextRenderer();
    super.onDisabled();
  }

  @Override
  protected long getBufferedPositionUs() {
    // Don't block playback whilst subtitles are loading.
    return END_OF_TRACK_US;
  }

  @Override
  protected boolean isEnded() {
    return inputStreamEnded && (subtitle == null || getNextEventTime() == Long.MAX_VALUE);
  }

  @Override
  protected boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private long getNextEventTime() {
    return ((nextSubtitleEventIndex == -1)
        || (nextSubtitleEventIndex >= subtitle.getEventTimeCount())) ? Long.MAX_VALUE
        : (subtitle.getEventTime(nextSubtitleEventIndex));
  }

  private void updateTextRenderer(List<Cue> cues) {
    if (textRendererHandler != null) {
      textRendererHandler.obtainMessage(MSG_UPDATE_OVERLAY, cues).sendToTarget();
    } else {
      invokeRendererInternalCues(cues);
    }
  }

  private void clearTextRenderer() {
    updateTextRenderer(Collections.<Cue>emptyList());
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OVERLAY:
        invokeRendererInternalCues((List<Cue>) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeRendererInternalCues(List<Cue> cues) {
    textRenderer.onCues(cues);
  }

  private int getParserIndex(MediaFormat mediaFormat) {
    for (int i = 0; i < subtitleParsers.length; i++) {
      if (subtitleParsers[i].canParse(mediaFormat.mimeType)) {
        return i;
      }
    }
    return -1;
  }

}
