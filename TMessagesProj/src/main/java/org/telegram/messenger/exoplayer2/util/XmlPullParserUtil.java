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
package org.telegram.messenger.exoplayer2.util;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * {@link XmlPullParser} utility methods.
 */
public final class XmlPullParserUtil {

  private XmlPullParserUtil() {}

  /**
   * Returns whether the current event is an end tag with the specified name.
   *
   * @param xpp The {@link XmlPullParser} to query.
   * @param name The specified name.
   * @return Whether the current event is an end tag with the specified name.
   * @throws XmlPullParserException If an error occurs querying the parser.
   */
  public static boolean isEndTag(XmlPullParser xpp, String name) throws XmlPullParserException {
    return isEndTag(xpp) && xpp.getName().equals(name);
  }

  /**
   * Returns whether the current event is an end tag.
   *
   * @param xpp The {@link XmlPullParser} to query.
   * @return Whether the current event is an end tag.
   * @throws XmlPullParserException If an error occurs querying the parser.
   */
  public static boolean isEndTag(XmlPullParser xpp) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.END_TAG;
  }

  /**
   * Returns whether the current event is a start tag with the specified name.
   *
   * @param xpp The {@link XmlPullParser} to query.
   * @param name The specified name.
   * @return Whether the current event is a start tag with the specified name.
   * @throws XmlPullParserException If an error occurs querying the parser.
   */
  public static boolean isStartTag(XmlPullParser xpp, String name)
      throws XmlPullParserException {
    return isStartTag(xpp) && xpp.getName().equals(name);
  }

  /**
   * Returns whether the current event is a start tag.
   *
   * @param xpp The {@link XmlPullParser} to query.
   * @return Whether the current event is a start tag.
   * @throws XmlPullParserException If an error occurs querying the parser.
   */
  public static boolean isStartTag(XmlPullParser xpp) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.START_TAG;
  }

  /**
   * Returns the value of an attribute of the current start tag.
   *
   * @param xpp The {@link XmlPullParser} to query.
   * @param attributeName The name of the attribute.
   * @return The value of the attribute, or null if the current event is not a start tag or if no
   *     no such attribute was found.
   */
  public static String getAttributeValue(XmlPullParser xpp, String attributeName) {
    int attributeCount = xpp.getAttributeCount();
    for (int i = 0; i < attributeCount; i++) {
      if (attributeName.equals(xpp.getAttributeName(i))) {
        return xpp.getAttributeValue(i);
      }
    }
    return null;
  }

}
