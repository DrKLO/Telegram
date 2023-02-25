/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.extractor.jpeg;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ParserException;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.XmlPullParserUtil;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Parser for motion photo metadata, handling XMP following the Motion Photo V1 and Micro Video V1b
 * specifications.
 */
/* package */ final class XmpMotionPhotoDescriptionParser {

  /**
   * Attempts to parse the specified XMP data describing the motion photo, returning the resulting
   * {@link MotionPhotoDescription} or {@code null} if it wasn't possible to derive a motion photo
   * description.
   *
   * @param xmpString A string of XML containing XMP motion photo metadata to attempt to parse.
   * @return The {@link MotionPhotoDescription}, or {@code null} if it wasn't possible to derive a
   *     motion photo description.
   * @throws IOException If an error occurs reading data from the stream.
   */
  @Nullable
  public static MotionPhotoDescription parse(String xmpString) throws IOException {
    try {
      return parseInternal(xmpString);
    } catch (XmlPullParserException | ParserException | NumberFormatException e) {
      Log.w(TAG, "Ignoring unexpected XMP metadata");
      return null;
    }
  }

  private static final String TAG = "MotionPhotoXmpParser";

  private static final String[] MOTION_PHOTO_ATTRIBUTE_NAMES =
      new String[] {
        "Camera:MotionPhoto", // Motion Photo V1
        "GCamera:MotionPhoto", // Motion Photo V1 (legacy element naming)
        "Camera:MicroVideo", // Micro Video V1b
        "GCamera:MicroVideo", // Micro Video V1b (legacy element naming)
      };
  private static final String[] DESCRIPTION_MOTION_PHOTO_PRESENTATION_TIMESTAMP_ATTRIBUTE_NAMES =
      new String[] {
        "Camera:MotionPhotoPresentationTimestampUs", // Motion Photo V1
        "GCamera:MotionPhotoPresentationTimestampUs", // Motion Photo V1 (legacy element naming)
        "Camera:MicroVideoPresentationTimestampUs", // Micro Video V1b
        "GCamera:MicroVideoPresentationTimestampUs", // Micro Video V1b (legacy element naming)
      };
  private static final String[] DESCRIPTION_MICRO_VIDEO_OFFSET_ATTRIBUTE_NAMES =
      new String[] {
        "Camera:MicroVideoOffset", // Micro Video V1b
        "GCamera:MicroVideoOffset", // Micro Video V1b (legacy element naming)
      };

  @Nullable
  private static MotionPhotoDescription parseInternal(String xmpString)
      throws XmlPullParserException, IOException {
    XmlPullParserFactory xmlPullParserFactory = XmlPullParserFactory.newInstance();
    XmlPullParser xpp = xmlPullParserFactory.newPullParser();
    xpp.setInput(new StringReader(xmpString));
    xpp.next();
    if (!XmlPullParserUtil.isStartTag(xpp, "x:xmpmeta")) {
      throw ParserException.createForMalformedContainer(
          "Couldn't find xmp metadata", /* cause= */ null);
    }
    long motionPhotoPresentationTimestampUs = C.TIME_UNSET;
    List<MotionPhotoDescription.ContainerItem> containerItems = ImmutableList.of();
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, "rdf:Description")) {
        if (!parseMotionPhotoFlagFromDescription(xpp)) {
          // The motion photo flag is not set, so the file should not be treated as a motion photo.
          return null;
        }
        motionPhotoPresentationTimestampUs =
            parseMotionPhotoPresentationTimestampUsFromDescription(xpp);
        containerItems = parseMicroVideoOffsetFromDescription(xpp);
      } else if (XmlPullParserUtil.isStartTag(xpp, "Container:Directory")) {
        containerItems = parseMotionPhotoV1Directory(xpp, "Container", "Item");
      } else if (XmlPullParserUtil.isStartTag(xpp, "GContainer:Directory")) {
        containerItems = parseMotionPhotoV1Directory(xpp, "GContainer", "GContainerItem");
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, "x:xmpmeta"));
    if (containerItems.isEmpty()) {
      // No motion photo information was parsed.
      return null;
    }
    return new MotionPhotoDescription(motionPhotoPresentationTimestampUs, containerItems);
  }

  private static boolean parseMotionPhotoFlagFromDescription(XmlPullParser xpp) {
    for (String attributeName : MOTION_PHOTO_ATTRIBUTE_NAMES) {
      @Nullable String attributeValue = XmlPullParserUtil.getAttributeValue(xpp, attributeName);
      if (attributeValue != null) {
        int motionPhotoFlag = Integer.parseInt(attributeValue);
        return motionPhotoFlag == 1;
      }
    }
    return false;
  }

  private static long parseMotionPhotoPresentationTimestampUsFromDescription(XmlPullParser xpp) {
    for (String attributeName : DESCRIPTION_MOTION_PHOTO_PRESENTATION_TIMESTAMP_ATTRIBUTE_NAMES) {
      @Nullable String attributeValue = XmlPullParserUtil.getAttributeValue(xpp, attributeName);
      if (attributeValue != null) {
        long presentationTimestampUs = Long.parseLong(attributeValue);
        return presentationTimestampUs == -1 ? C.TIME_UNSET : presentationTimestampUs;
      }
    }
    return C.TIME_UNSET;
  }

  private static ImmutableList<MotionPhotoDescription.ContainerItem>
      parseMicroVideoOffsetFromDescription(XmlPullParser xpp) {
    // We store a new Motion Photo item list based on the MicroVideo offset, so that the same
    // representation is used for both specifications.
    for (String attributeName : DESCRIPTION_MICRO_VIDEO_OFFSET_ATTRIBUTE_NAMES) {
      @Nullable String attributeValue = XmlPullParserUtil.getAttributeValue(xpp, attributeName);
      if (attributeValue != null) {
        long microVideoOffset = Long.parseLong(attributeValue);
        return ImmutableList.of(
            new MotionPhotoDescription.ContainerItem(
                MimeTypes.IMAGE_JPEG, "Primary", /* length= */ 0, /* padding= */ 0),
            new MotionPhotoDescription.ContainerItem(
                MimeTypes.VIDEO_MP4,
                "MotionPhoto",
                /* length= */ microVideoOffset,
                /* padding= */ 0));
      }
    }
    return ImmutableList.of();
  }

  private static ImmutableList<MotionPhotoDescription.ContainerItem> parseMotionPhotoV1Directory(
      XmlPullParser xpp, String containerNamespacePrefix, String itemNamespacePrefix)
      throws XmlPullParserException, IOException {
    ImmutableList.Builder<MotionPhotoDescription.ContainerItem> containerItems =
        ImmutableList.builder();
    String itemTagName = containerNamespacePrefix + ":Item";
    String directoryTagName = containerNamespacePrefix + ":Directory";
    do {
      xpp.next();
      if (XmlPullParserUtil.isStartTag(xpp, itemTagName)) {
        String mimeAttributeName = itemNamespacePrefix + ":Mime";
        String semanticAttributeName = itemNamespacePrefix + ":Semantic";
        String lengthAttributeName = itemNamespacePrefix + ":Length";
        String paddinghAttributeName = itemNamespacePrefix + ":Padding";
        @Nullable String mime = XmlPullParserUtil.getAttributeValue(xpp, mimeAttributeName);
        @Nullable String semantic = XmlPullParserUtil.getAttributeValue(xpp, semanticAttributeName);
        @Nullable String length = XmlPullParserUtil.getAttributeValue(xpp, lengthAttributeName);
        @Nullable String padding = XmlPullParserUtil.getAttributeValue(xpp, paddinghAttributeName);
        if (mime == null || semantic == null) {
          // Required values are missing.
          return ImmutableList.of();
        }
        containerItems.add(
            new MotionPhotoDescription.ContainerItem(
                mime,
                semantic,
                length != null ? Long.parseLong(length) : 0,
                padding != null ? Long.parseLong(padding) : 0));
      }
    } while (!XmlPullParserUtil.isEndTag(xpp, directoryTagName));
    return containerItems.build();
  }

  private XmpMotionPhotoDescriptionParser() {}
}
