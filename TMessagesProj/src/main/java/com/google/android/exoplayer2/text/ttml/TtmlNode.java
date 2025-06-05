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
package com.google.android.exoplayer2.text.ttml;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.SpannableStringBuilder;
import android.util.Base64;
import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.util.Assertions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** A package internal representation of TTML node. */
/* package */ final class TtmlNode {

  public static final String TAG_TT = "tt";
  public static final String TAG_HEAD = "head";
  public static final String TAG_BODY = "body";
  public static final String TAG_DIV = "div";
  public static final String TAG_P = "p";
  public static final String TAG_SPAN = "span";
  public static final String TAG_BR = "br";
  public static final String TAG_STYLE = "style";
  public static final String TAG_STYLING = "styling";
  public static final String TAG_LAYOUT = "layout";
  public static final String TAG_REGION = "region";
  public static final String TAG_METADATA = "metadata";
  public static final String TAG_IMAGE = "image";
  public static final String TAG_DATA = "data";
  public static final String TAG_INFORMATION = "information";

  public static final String ANONYMOUS_REGION_ID = "";
  public static final String ATTR_ID = "id";
  public static final String ATTR_TTS_ORIGIN = "origin";
  public static final String ATTR_TTS_EXTENT = "extent";
  public static final String ATTR_TTS_DISPLAY_ALIGN = "displayAlign";
  public static final String ATTR_TTS_BACKGROUND_COLOR = "backgroundColor";
  public static final String ATTR_TTS_FONT_STYLE = "fontStyle";
  public static final String ATTR_TTS_FONT_SIZE = "fontSize";
  public static final String ATTR_TTS_FONT_FAMILY = "fontFamily";
  public static final String ATTR_TTS_FONT_WEIGHT = "fontWeight";
  public static final String ATTR_TTS_COLOR = "color";
  public static final String ATTR_TTS_RUBY = "ruby";
  public static final String ATTR_TTS_RUBY_POSITION = "rubyPosition";
  public static final String ATTR_TTS_TEXT_DECORATION = "textDecoration";
  public static final String ATTR_TTS_TEXT_ALIGN = "textAlign";
  public static final String ATTR_TTS_TEXT_COMBINE = "textCombine";
  public static final String ATTR_TTS_TEXT_EMPHASIS = "textEmphasis";
  public static final String ATTR_TTS_WRITING_MODE = "writingMode";
  public static final String ATTR_TTS_SHEAR = "shear";
  public static final String ATTR_EBUTTS_MULTI_ROW_ALIGN = "multiRowAlign";

  // Values for ruby
  public static final String RUBY_CONTAINER = "container";
  public static final String RUBY_BASE = "base";
  public static final String RUBY_BASE_CONTAINER = "baseContainer";
  public static final String RUBY_TEXT = "text";
  public static final String RUBY_TEXT_CONTAINER = "textContainer";
  public static final String RUBY_DELIMITER = "delimiter";

  // Values for text annotation (i.e. ruby, text emphasis) position
  public static final String ANNOTATION_POSITION_BEFORE = "before";
  public static final String ANNOTATION_POSITION_AFTER = "after";
  public static final String ANNOTATION_POSITION_OUTSIDE = "outside";

  // Values for textDecoration
  public static final String LINETHROUGH = "linethrough";
  public static final String NO_LINETHROUGH = "nolinethrough";
  public static final String UNDERLINE = "underline";
  public static final String NO_UNDERLINE = "nounderline";
  public static final String ITALIC = "italic";
  public static final String BOLD = "bold";

  // Values for textAlign
  public static final String LEFT = "left";
  public static final String CENTER = "center";
  public static final String RIGHT = "right";
  public static final String START = "start";
  public static final String END = "end";

  // Values for textCombine
  public static final String COMBINE_NONE = "none";
  public static final String COMBINE_ALL = "all";

  // Values for writingMode
  public static final String VERTICAL = "tb";
  public static final String VERTICAL_LR = "tblr";
  public static final String VERTICAL_RL = "tbrl";

  // Values for textEmphasis
  public static final String TEXT_EMPHASIS_NONE = "none";
  public static final String TEXT_EMPHASIS_AUTO = "auto";
  public static final String TEXT_EMPHASIS_MARK_DOT = "dot";
  public static final String TEXT_EMPHASIS_MARK_SESAME = "sesame";
  public static final String TEXT_EMPHASIS_MARK_CIRCLE = "circle";
  public static final String TEXT_EMPHASIS_MARK_FILLED = "filled";
  public static final String TEXT_EMPHASIS_MARK_OPEN = "open";

  @Nullable public final String tag;
  @Nullable public final String text;
  public final boolean isTextNode;
  public final long startTimeUs;
  public final long endTimeUs;
  @Nullable public final TtmlStyle style;
  @Nullable private final String[] styleIds;
  public final String regionId;
  @Nullable public final String imageId;
  @Nullable public final TtmlNode parent;

  private final HashMap<String, Integer> nodeStartsByRegion;
  private final HashMap<String, Integer> nodeEndsByRegion;

  private @MonotonicNonNull List<TtmlNode> children;

  public static TtmlNode buildTextNode(String text) {
    return new TtmlNode(
        /* tag= */ null,
        TtmlRenderUtil.applyTextElementSpacePolicy(text),
        /* startTimeUs= */ C.TIME_UNSET,
        /* endTimeUs= */ C.TIME_UNSET,
        /* style= */ null,
        /* styleIds= */ null,
        ANONYMOUS_REGION_ID,
        /* imageId= */ null,
        /* parent= */ null);
  }

  public static TtmlNode buildNode(
      @Nullable String tag,
      long startTimeUs,
      long endTimeUs,
      @Nullable TtmlStyle style,
      @Nullable String[] styleIds,
      String regionId,
      @Nullable String imageId,
      @Nullable TtmlNode parent) {
    return new TtmlNode(
        tag, /* text= */ null, startTimeUs, endTimeUs, style, styleIds, regionId, imageId, parent);
  }

  private TtmlNode(
      @Nullable String tag,
      @Nullable String text,
      long startTimeUs,
      long endTimeUs,
      @Nullable TtmlStyle style,
      @Nullable String[] styleIds,
      String regionId,
      @Nullable String imageId,
      @Nullable TtmlNode parent) {
    this.tag = tag;
    this.text = text;
    this.imageId = imageId;
    this.style = style;
    this.styleIds = styleIds;
    this.isTextNode = text != null;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
    this.regionId = Assertions.checkNotNull(regionId);
    this.parent = parent;
    nodeStartsByRegion = new HashMap<>();
    nodeEndsByRegion = new HashMap<>();
  }

  public boolean isActive(long timeUs) {
    return (startTimeUs == C.TIME_UNSET && endTimeUs == C.TIME_UNSET)
        || (startTimeUs <= timeUs && endTimeUs == C.TIME_UNSET)
        || (startTimeUs == C.TIME_UNSET && timeUs < endTimeUs)
        || (startTimeUs <= timeUs && timeUs < endTimeUs);
  }

  public void addChild(TtmlNode child) {
    if (children == null) {
      children = new ArrayList<>();
    }
    children.add(child);
  }

  public TtmlNode getChild(int index) {
    if (children == null) {
      throw new IndexOutOfBoundsException();
    }
    return children.get(index);
  }

  public int getChildCount() {
    return children == null ? 0 : children.size();
  }

  public long[] getEventTimesUs() {
    TreeSet<Long> eventTimeSet = new TreeSet<>();
    getEventTimes(eventTimeSet, false);
    long[] eventTimes = new long[eventTimeSet.size()];
    int i = 0;
    for (long eventTimeUs : eventTimeSet) {
      eventTimes[i++] = eventTimeUs;
    }
    return eventTimes;
  }

  private void getEventTimes(TreeSet<Long> out, boolean descendsPNode) {
    boolean isPNode = TAG_P.equals(tag);
    boolean isDivNode = TAG_DIV.equals(tag);
    if (descendsPNode || isPNode || (isDivNode && imageId != null)) {
      if (startTimeUs != C.TIME_UNSET) {
        out.add(startTimeUs);
      }
      if (endTimeUs != C.TIME_UNSET) {
        out.add(endTimeUs);
      }
    }
    if (children == null) {
      return;
    }
    for (int i = 0; i < children.size(); i++) {
      children.get(i).getEventTimes(out, descendsPNode || isPNode);
    }
  }

  @Nullable
  public String[] getStyleIds() {
    return styleIds;
  }

  public List<Cue> getCues(
      long timeUs,
      Map<String, TtmlStyle> globalStyles,
      Map<String, TtmlRegion> regionMap,
      Map<String, String> imageMap) {

    List<Pair<String, String>> regionImageOutputs = new ArrayList<>();
    traverseForImage(timeUs, regionId, regionImageOutputs);

    TreeMap<String, Cue.Builder> regionTextOutputs = new TreeMap<>();
    traverseForText(timeUs, false, regionId, regionTextOutputs);
    traverseForStyle(timeUs, globalStyles, regionMap, regionId, regionTextOutputs);

    List<Cue> cues = new ArrayList<>();

    // Create image based cues.
    for (Pair<String, String> regionImagePair : regionImageOutputs) {
      @Nullable String encodedBitmapData = imageMap.get(regionImagePair.second);
      if (encodedBitmapData == null) {
        // Image reference points to an invalid image. Do nothing.
        continue;
      }

      byte[] bitmapData = Base64.decode(encodedBitmapData, Base64.DEFAULT);
      Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapData, /* offset= */ 0, bitmapData.length);
      TtmlRegion region = Assertions.checkNotNull(regionMap.get(regionImagePair.first));

      cues.add(
          new Cue.Builder()
              .setBitmap(bitmap)
              .setPosition(region.position)
              .setPositionAnchor(Cue.ANCHOR_TYPE_START)
              .setLine(region.line, Cue.LINE_TYPE_FRACTION)
              .setLineAnchor(region.lineAnchor)
              .setSize(region.width)
              .setBitmapHeight(region.height)
              .setVerticalType(region.verticalType)
              .build());
    }

    // Create text based cues.
    for (Map.Entry<String, Cue.Builder> entry : regionTextOutputs.entrySet()) {
      TtmlRegion region = Assertions.checkNotNull(regionMap.get(entry.getKey()));
      Cue.Builder regionOutput = entry.getValue();
      cleanUpText((SpannableStringBuilder) Assertions.checkNotNull(regionOutput.getText()));
      regionOutput.setLine(region.line, region.lineType);
      regionOutput.setLineAnchor(region.lineAnchor);
      regionOutput.setPosition(region.position);
      regionOutput.setSize(region.width);
      regionOutput.setTextSize(region.textSize, region.textSizeType);
      regionOutput.setVerticalType(region.verticalType);
      cues.add(regionOutput.build());
    }

    return cues;
  }

  private void traverseForImage(
      long timeUs, String inheritedRegion, List<Pair<String, String>> regionImageList) {
    String resolvedRegionId = ANONYMOUS_REGION_ID.equals(regionId) ? inheritedRegion : regionId;
    if (isActive(timeUs) && TAG_DIV.equals(tag) && imageId != null) {
      regionImageList.add(new Pair<>(resolvedRegionId, imageId));
      return;
    }
    for (int i = 0; i < getChildCount(); ++i) {
      getChild(i).traverseForImage(timeUs, resolvedRegionId, regionImageList);
    }
  }

  private void traverseForText(
      long timeUs,
      boolean descendsPNode,
      String inheritedRegion,
      Map<String, Cue.Builder> regionOutputs) {
    nodeStartsByRegion.clear();
    nodeEndsByRegion.clear();
    if (TAG_METADATA.equals(tag)) {
      // Ignore metadata tag.
      return;
    }

    String resolvedRegionId = ANONYMOUS_REGION_ID.equals(regionId) ? inheritedRegion : regionId;

    if (isTextNode && descendsPNode) {
      getRegionOutputText(resolvedRegionId, regionOutputs).append(Assertions.checkNotNull(text));
    } else if (TAG_BR.equals(tag) && descendsPNode) {
      getRegionOutputText(resolvedRegionId, regionOutputs).append('\n');
    } else if (isActive(timeUs)) {
      // This is a container node, which can contain zero or more children.
      for (Map.Entry<String, Cue.Builder> entry : regionOutputs.entrySet()) {
        nodeStartsByRegion.put(
            entry.getKey(), Assertions.checkNotNull(entry.getValue().getText()).length());
      }

      boolean isPNode = TAG_P.equals(tag);
      for (int i = 0; i < getChildCount(); i++) {
        getChild(i)
            .traverseForText(timeUs, descendsPNode || isPNode, resolvedRegionId, regionOutputs);
      }
      if (isPNode) {
        TtmlRenderUtil.endParagraph(getRegionOutputText(resolvedRegionId, regionOutputs));
      }

      for (Map.Entry<String, Cue.Builder> entry : regionOutputs.entrySet()) {
        nodeEndsByRegion.put(
            entry.getKey(), Assertions.checkNotNull(entry.getValue().getText()).length());
      }
    }
  }

  private static SpannableStringBuilder getRegionOutputText(
      String resolvedRegionId, Map<String, Cue.Builder> regionOutputs) {
    if (!regionOutputs.containsKey(resolvedRegionId)) {
      Cue.Builder regionOutput = new Cue.Builder();
      regionOutput.setText(new SpannableStringBuilder());
      regionOutputs.put(resolvedRegionId, regionOutput);
    }
    return (SpannableStringBuilder)
        Assertions.checkNotNull(regionOutputs.get(resolvedRegionId).getText());
  }

  private void traverseForStyle(
      long timeUs,
      Map<String, TtmlStyle> globalStyles,
      Map<String, TtmlRegion> regionMaps,
      String inheritedRegion,
      Map<String, Cue.Builder> regionOutputs) {
    if (!isActive(timeUs)) {
      return;
    }
    String resolvedRegionId = ANONYMOUS_REGION_ID.equals(regionId) ? inheritedRegion : regionId;
    for (Map.Entry<String, Integer> entry : nodeEndsByRegion.entrySet()) {
      String regionId = entry.getKey();
      int start = nodeStartsByRegion.containsKey(regionId) ? nodeStartsByRegion.get(regionId) : 0;
      int end = entry.getValue();
      if (start != end) {
        Cue.Builder regionOutput = Assertions.checkNotNull(regionOutputs.get(regionId));
        @Cue.VerticalType
        int verticalType = Assertions.checkNotNull(regionMaps.get(resolvedRegionId)).verticalType;
        applyStyleToOutput(globalStyles, regionOutput, start, end, verticalType);
      }
    }
    for (int i = 0; i < getChildCount(); ++i) {
      getChild(i)
          .traverseForStyle(timeUs, globalStyles, regionMaps, resolvedRegionId, regionOutputs);
    }
  }

  private void applyStyleToOutput(
      Map<String, TtmlStyle> globalStyles,
      Cue.Builder regionOutput,
      int start,
      int end,
      @Cue.VerticalType int verticalType) {
    @Nullable TtmlStyle resolvedStyle = TtmlRenderUtil.resolveStyle(style, styleIds, globalStyles);
    @Nullable SpannableStringBuilder text = (SpannableStringBuilder) regionOutput.getText();
    if (text == null) {
      text = new SpannableStringBuilder();
      regionOutput.setText(text);
    }
    if (resolvedStyle != null) {
      TtmlRenderUtil.applyStylesToSpan(
          text, start, end, resolvedStyle, parent, globalStyles, verticalType);
      if (TAG_P.equals(tag)) {
        if (resolvedStyle.getShearPercentage() != TtmlStyle.UNSPECIFIED_SHEAR) {
          // Shear style should only be applied to P nodes
          // https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-shear
          // The spec doesn't specify the coordinate system to use for block shear
          // however the spec shows examples of how different values are expected to be rendered.
          // See: https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-shear
          // https://www.w3.org/TR/2018/REC-ttml2-20181108/#style-attribute-fontShear
          // This maps the shear percentage to shear angle in graphics coordinates
          regionOutput.setShearDegrees((resolvedStyle.getShearPercentage() * -90) / 100);
        }
        if (resolvedStyle.getTextAlign() != null) {
          regionOutput.setTextAlignment(resolvedStyle.getTextAlign());
        }
        if (resolvedStyle.getMultiRowAlign() != null) {
          regionOutput.setMultiRowAlignment(resolvedStyle.getMultiRowAlign());
        }
      }
    }
  }

  private static void cleanUpText(SpannableStringBuilder builder) {
    // Having joined the text elements, we need to do some final cleanup on the result.
    // Remove any text covered by a DeleteTextSpan (e.g. ruby text).
    DeleteTextSpan[] deleteTextSpans = builder.getSpans(0, builder.length(), DeleteTextSpan.class);
    for (DeleteTextSpan deleteTextSpan : deleteTextSpans) {
      builder.replace(builder.getSpanStart(deleteTextSpan), builder.getSpanEnd(deleteTextSpan), "");
    }
    // Collapse multiple consecutive spaces into a single space.
    for (int i = 0; i < builder.length(); i++) {
      if (builder.charAt(i) == ' ') {
        int j = i + 1;
        while (j < builder.length() && builder.charAt(j) == ' ') {
          j++;
        }
        int spacesToDelete = j - (i + 1);
        if (spacesToDelete > 0) {
          builder.delete(i, i + spacesToDelete);
        }
      }
    }
    // Remove any spaces from the start of each line.
    if (builder.length() > 0 && builder.charAt(0) == ' ') {
      builder.delete(0, 1);
    }
    for (int i = 0; i < builder.length() - 1; i++) {
      if (builder.charAt(i) == '\n' && builder.charAt(i + 1) == ' ') {
        builder.delete(i + 1, i + 2);
      }
    }
    // Remove any spaces from the end of each line.
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == ' ') {
      builder.delete(builder.length() - 1, builder.length());
    }
    for (int i = 0; i < builder.length() - 1; i++) {
      if (builder.charAt(i) == ' ' && builder.charAt(i + 1) == '\n') {
        builder.delete(i, i + 1);
      }
    }
    // Trim a trailing newline, if there is one.
    if (builder.length() > 0 && builder.charAt(builder.length() - 1) == '\n') {
      builder.delete(builder.length() - 1, builder.length());
    }
  }
}
