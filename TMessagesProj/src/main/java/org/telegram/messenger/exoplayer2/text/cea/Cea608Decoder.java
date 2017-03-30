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
package org.telegram.messenger.exoplayer2.text.cea;

import android.text.TextUtils;
import org.telegram.messenger.exoplayer2.Format;
import org.telegram.messenger.exoplayer2.text.Cue;
import org.telegram.messenger.exoplayer2.text.Subtitle;
import org.telegram.messenger.exoplayer2.text.SubtitleDecoder;
import org.telegram.messenger.exoplayer2.text.SubtitleInputBuffer;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;

/**
 * A {@link SubtitleDecoder} for CEA-608 (also known as "line 21 captions" and "EIA-608").
 */
public final class Cea608Decoder extends CeaDecoder {

  private static final String TAG = "Cea608Decoder";

  private static final int CC_VALID_FLAG = 0x04;
  private static final int CC_TYPE_FLAG = 0x02;
  private static final int CC_FIELD_FLAG = 0x01;

  private static final int NTSC_CC_FIELD_1 = 0x00;
  private static final int NTSC_CC_FIELD_2 = 0x01;
  private static final int CC_VALID_608_ID = 0x04;

  private static final int PAYLOAD_TYPE_CC = 4;
  private static final int COUNTRY_CODE = 0xB5;
  private static final int PROVIDER_CODE = 0x31;
  private static final int USER_ID = 0x47413934; // "GA94"
  private static final int USER_DATA_TYPE_CODE = 0x3;

  private static final int CC_MODE_UNKNOWN = 0;
  private static final int CC_MODE_ROLL_UP = 1;
  private static final int CC_MODE_POP_ON = 2;
  private static final int CC_MODE_PAINT_ON = 3;

  // The default number of rows to display in roll-up captions mode.
  private static final int DEFAULT_CAPTIONS_ROW_COUNT = 4;

  /**
   * Command initiating pop-on style captioning. Subsequent data should be loaded into a
   * non-displayed memory and held there until the {@link #CTRL_END_OF_CAPTION} command is received,
   * at which point the non-displayed memory becomes the displayed memory (and vice versa).
   */
  private static final byte CTRL_RESUME_CAPTION_LOADING = 0x20;
  /**
   * Command initiating roll-up style captioning, with the maximum of 2 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_2_ROWS = 0x25;
  /**
   * Command initiating roll-up style captioning, with the maximum of 3 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_3_ROWS = 0x26;
  /**
   * Command initiating roll-up style captioning, with the maximum of 4 rows displayed
   * simultaneously.
   */
  private static final byte CTRL_ROLL_UP_CAPTIONS_4_ROWS = 0x27;
  /**
   * Command initiating paint-on style captioning. Subsequent data should be addressed immediately
   * to displayed memory without need for the {@link #CTRL_RESUME_CAPTION_LOADING} command.
   */
  private static final byte CTRL_RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * Command indicating the end of a pop-on style caption. At this point the caption loaded in
   * non-displayed memory should be swapped with the one in displayed memory. If no
   * {@link #CTRL_RESUME_CAPTION_LOADING} command has been received, this command forces the
   * receiver into pop-on style.
   */
  private static final byte CTRL_END_OF_CAPTION = 0x2F;

  private static final byte CTRL_ERASE_DISPLAYED_MEMORY = 0x2C;
  private static final byte CTRL_CARRIAGE_RETURN = 0x2D;
  private static final byte CTRL_ERASE_NON_DISPLAYED_MEMORY = 0x2E;

  private static final byte CTRL_BACKSPACE = 0x21;

  private static final byte CTRL_MISC_CHAN_1 = 0x14;
  private static final byte CTRL_MISC_CHAN_2 = 0x1C;

  // Basic North American 608 CC char set, mostly ASCII. Indexed by (char-0x20).
  private static final int[] BASIC_CHARACTER_SET = new int[] {
    0x20, 0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27,     //   ! " # $ % & '
    0x28, 0x29,                                         // ( )
    0xE1,       // 2A: 225 'á' "Latin small letter A with acute"
    0x2B, 0x2C, 0x2D, 0x2E, 0x2F,                       //       + , - . /
    0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37,     // 0 1 2 3 4 5 6 7
    0x38, 0x39, 0x3A, 0x3B, 0x3C, 0x3D, 0x3E, 0x3F,     // 8 9 : ; < = > ?
    0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47,     // @ A B C D E F G
    0x48, 0x49, 0x4A, 0x4B, 0x4C, 0x4D, 0x4E, 0x4F,     // H I J K L M N O
    0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57,     // P Q R S T U V W
    0x58, 0x59, 0x5A, 0x5B,                             // X Y Z [
    0xE9,       // 5C: 233 'é' "Latin small letter E with acute"
    0x5D,                                               //           ]
    0xED,       // 5E: 237 'í' "Latin small letter I with acute"
    0xF3,       // 5F: 243 'ó' "Latin small letter O with acute"
    0xFA,       // 60: 250 'ú' "Latin small letter U with acute"
    0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67,           //   a b c d e f g
    0x68, 0x69, 0x6A, 0x6B, 0x6C, 0x6D, 0x6E, 0x6F,     // h i j k l m n o
    0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77,     // p q r s t u v w
    0x78, 0x79, 0x7A,                                   // x y z
    0xE7,       // 7B: 231 'ç' "Latin small letter C with cedilla"
    0xF7,       // 7C: 247 '÷' "Division sign"
    0xD1,       // 7D: 209 'Ñ' "Latin capital letter N with tilde"
    0xF1,       // 7E: 241 'ñ' "Latin small letter N with tilde"
    0x25A0      // 7F:         "Black Square" (NB: 2588 = Full Block)
  };

  // Special North American 608 CC char set.
  private static final int[] SPECIAL_CHARACTER_SET = new int[] {
    0xAE,    // 30: 174 '®' "Registered Sign" - registered trademark symbol
    0xB0,    // 31: 176 '°' "Degree Sign"
    0xBD,    // 32: 189 '½' "Vulgar Fraction One Half" (1/2 symbol)
    0xBF,    // 33: 191 '¿' "Inverted Question Mark"
    0x2122,  // 34:         "Trade Mark Sign" (tm superscript)
    0xA2,    // 35: 162 '¢' "Cent Sign"
    0xA3,    // 36: 163 '£' "Pound Sign" - pounds sterling
    0x266A,  // 37:         "Eighth Note" - music note
    0xE0,    // 38: 224 'à' "Latin small letter A with grave"
    0x20,    // 39:         TRANSPARENT SPACE - for now use ordinary space
    0xE8,    // 3A: 232 'è' "Latin small letter E with grave"
    0xE2,    // 3B: 226 'â' "Latin small letter A with circumflex"
    0xEA,    // 3C: 234 'ê' "Latin small letter E with circumflex"
    0xEE,    // 3D: 238 'î' "Latin small letter I with circumflex"
    0xF4,    // 3E: 244 'ô' "Latin small letter O with circumflex"
    0xFB     // 3F: 251 'û' "Latin small letter U with circumflex"
  };

  // Extended Spanish/Miscellaneous and French char set.
  private static final int[] SPECIAL_ES_FR_CHARACTER_SET = new int[] {
    // Spanish and misc.
    0xC1, 0xC9, 0xD3, 0xDA, 0xDC, 0xFC, 0x2018, 0xA1,
    0x2A, 0x27, 0x2014, 0xA9, 0x2120, 0x2022, 0x201C, 0x201D,
    // French.
    0xC0, 0xC2, 0xC7, 0xC8, 0xCA, 0xCB, 0xEB, 0xCE,
    0xCF, 0xEF, 0xD4, 0xD9, 0xF9, 0xDB, 0xAB, 0xBB
  };

  //Extended Portuguese and German/Danish char set.
  private static final int[] SPECIAL_PT_DE_CHARACTER_SET = new int[] {
    // Portuguese.
    0xC3, 0xE3, 0xCD, 0xCC, 0xEC, 0xD2, 0xF2, 0xD5,
    0xF5, 0x7B, 0x7D, 0x5C, 0x5E, 0x5F, 0x7C, 0x7E,
    // German/Danish.
    0xC4, 0xE4, 0xD6, 0xF6, 0xDF, 0xA5, 0xA4, 0x2502,
    0xC5, 0xE5, 0xD8, 0xF8, 0x250C, 0x2510, 0x2514, 0x2518
  };

  private final ParsableByteArray ccData;

  private final StringBuilder captionStringBuilder;

  private final int selectedField;

  private int captionMode;
  private int captionRowCount;
  private String captionString;

  private String lastCaptionString;

  private boolean repeatableControlSet;
  private byte repeatableControlCc1;
  private byte repeatableControlCc2;

  public Cea608Decoder(int accessibilityChannel) {
    ccData = new ParsableByteArray();

    captionStringBuilder = new StringBuilder();
    switch (accessibilityChannel) {
      case 3:
      case 4:
        selectedField = 2;
        break;
      case 1:
      case 2:
      case Format.NO_VALUE:
      default:
        selectedField = 1;
    }

    setCaptionMode(CC_MODE_UNKNOWN);
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
  }

  @Override
  public String getName() {
    return "Cea608Decoder";
  }

  @Override
  public void flush() {
    super.flush();
    setCaptionMode(CC_MODE_UNKNOWN);
    captionRowCount = DEFAULT_CAPTIONS_ROW_COUNT;
    captionStringBuilder.setLength(0);
    captionString = null;
    lastCaptionString = null;
    repeatableControlSet = false;
    repeatableControlCc1 = 0;
    repeatableControlCc2 = 0;
  }

  @Override
  public void release() {
    // Do nothing
  }

  @Override
  protected boolean isNewSubtitleDataAvailable() {
    return !TextUtils.equals(captionString, lastCaptionString);
  }

  @Override
  protected Subtitle createSubtitle() {
    lastCaptionString = captionString;
    return new CeaSubtitle(new Cue(captionString));
  }

  @Override
  protected void decode(SubtitleInputBuffer inputBuffer) {
    ccData.reset(inputBuffer.data.array(), inputBuffer.data.limit());
    boolean captionDataProcessed = false;
    boolean isRepeatableControl = false;
    while (ccData.bytesLeft() > 0) {
      byte ccDataHeader = (byte) ccData.readUnsignedByte();
      byte ccData1 = (byte) (ccData.readUnsignedByte() & 0x7F);
      byte ccData2 = (byte) (ccData.readUnsignedByte() & 0x7F);

      // Only examine valid CEA-608 packets
      if ((ccDataHeader & (CC_VALID_FLAG | CC_TYPE_FLAG)) != CC_VALID_608_ID) {
        continue;
      }

      // Only examine packets within the selected field
      if ((selectedField == 1 && (ccDataHeader & CC_FIELD_FLAG) != NTSC_CC_FIELD_1)
          || (selectedField == 2 && (ccDataHeader & CC_FIELD_FLAG) != NTSC_CC_FIELD_2)) {
        continue;
      }

      // Ignore empty captions.
      if (ccData1 == 0 && ccData2 == 0) {
        continue;
      }
      // If we've reached this point then there is data to process; flag that work has been done.
      captionDataProcessed = true;

      // Special North American character set.
      // ccData1 - P|0|0|1|C|0|0|1
      // ccData2 - P|0|1|1|X|X|X|X
      if ((ccData1 == 0x11 || ccData1 == 0x19) && ((ccData2 & 0x70) == 0x30)) {
        // TODO: Make use of the channel bit
        captionStringBuilder.append(getSpecialChar(ccData2));
        continue;
      }

      // Extended Western European character set.
      // ccData1 - P|0|0|1|C|0|1|S
      // ccData2 - P|0|1|X|X|X|X|X
      if ((ccData2 & 0x60) == 0x20) {
        // Extended Spanish/Miscellaneous and French character set (S = 0).
        if (ccData1 == 0x12 || ccData1 == 0x1A) {
          // TODO: Make use of the channel bit
          backspace(); // Remove standard equivalent of the special extended char.
          captionStringBuilder.append(getExtendedEsFrChar(ccData2));
          continue;
        }

        // Extended Portuguese and German/Danish character set (S = 1).
        if (ccData1 == 0x13 || ccData1 == 0x1B) {
          // TODO: Make use of the channel bit
          backspace(); // Remove standard equivalent of the special extended char.
          captionStringBuilder.append(getExtendedPtDeChar(ccData2));
          continue;
        }
      }

      // Control character.
      if (ccData1 < 0x20) {
        isRepeatableControl = handleCtrl(ccData1, ccData2);
        continue;
      }

      // Basic North American character set.
      captionStringBuilder.append(getChar(ccData1));
      if (ccData2 >= 0x20) {
        captionStringBuilder.append(getChar(ccData2));
      }
    }

    if (captionDataProcessed) {
      if (!isRepeatableControl) {
        repeatableControlSet = false;
      }
      if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
        captionString = getDisplayCaption();
      }
    }
  }

  private boolean handleCtrl(byte cc1, byte cc2) {
    boolean isRepeatableControl = isRepeatable(cc1);
    if (isRepeatableControl) {
      if (repeatableControlSet
          && repeatableControlCc1 == cc1
          && repeatableControlCc2 == cc2) {
        repeatableControlSet = false;
        return true;
      } else {
        repeatableControlSet = true;
        repeatableControlCc1 = cc1;
        repeatableControlCc2 = cc2;
      }
    }
    if (isMiscCode(cc1, cc2)) {
      handleMiscCode(cc2);
    } else if (isPreambleAddressCode(cc1, cc2)) {
      // TODO: Add better handling of this with specific positioning.
      maybeAppendNewline();
    }
    return isRepeatableControl;
  }

  private void handleMiscCode(byte cc2) {
    switch (cc2) {
      case CTRL_ROLL_UP_CAPTIONS_2_ROWS:
        captionRowCount = 2;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_ROLL_UP_CAPTIONS_3_ROWS:
        captionRowCount = 3;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_ROLL_UP_CAPTIONS_4_ROWS:
        captionRowCount = 4;
        setCaptionMode(CC_MODE_ROLL_UP);
        return;
      case CTRL_RESUME_CAPTION_LOADING:
        setCaptionMode(CC_MODE_POP_ON);
        return;
      case CTRL_RESUME_DIRECT_CAPTIONING:
        setCaptionMode(CC_MODE_PAINT_ON);
        return;
    }

    if (captionMode == CC_MODE_UNKNOWN) {
      return;
    }

    switch (cc2) {
      case CTRL_ERASE_DISPLAYED_MEMORY:
        captionString = null;
        if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_PAINT_ON) {
          captionStringBuilder.setLength(0);
        }
        break;
      case CTRL_ERASE_NON_DISPLAYED_MEMORY:
        captionStringBuilder.setLength(0);
        break;
      case CTRL_END_OF_CAPTION:
        captionString = getDisplayCaption();
        captionStringBuilder.setLength(0);
        break;
      case CTRL_CARRIAGE_RETURN:
        maybeAppendNewline();
        break;
      case CTRL_BACKSPACE:
        if (captionStringBuilder.length() > 0) {
          captionStringBuilder.setLength(captionStringBuilder.length() - 1);
        }
        break;
    }
  }

  private void backspace() {
    if (captionStringBuilder.length() > 0) {
      captionStringBuilder.setLength(captionStringBuilder.length() - 1);
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

  private void setCaptionMode(int captionMode) {
    if (this.captionMode == captionMode) {
      return;
    }

    this.captionMode = captionMode;
    // Clear the working memory.
    captionStringBuilder.setLength(0);
    if (captionMode == CC_MODE_ROLL_UP || captionMode == CC_MODE_UNKNOWN) {
      // When switching to roll-up or unknown, we also need to clear the caption.
      captionString = null;
    }
  }

  private static char getChar(byte ccData) {
    int index = (ccData & 0x7F) - 0x20;
    return (char) BASIC_CHARACTER_SET[index];
  }

  private static char getSpecialChar(byte ccData) {
    int index = ccData & 0xF;
    return (char) SPECIAL_CHARACTER_SET[index];
  }

  private static char getExtendedEsFrChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_ES_FR_CHARACTER_SET[index];
  }

  private static char getExtendedPtDeChar(byte ccData) {
    int index = ccData & 0x1F;
    return (char) SPECIAL_PT_DE_CHARACTER_SET[index];
  }

  private static boolean isMiscCode(byte cc1, byte cc2) {
    return (cc1 == CTRL_MISC_CHAN_1 || cc1 == CTRL_MISC_CHAN_2)
        && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  private static boolean isPreambleAddressCode(byte cc1, byte cc2) {
    return (cc1 >= 0x10 && cc1 <= 0x1F) && (cc2 >= 0x40 && cc2 <= 0x7F);
  }

  private static boolean isRepeatable(byte cc1) {
    return cc1 >= 0x10 && cc1 <= 0x1F;
  }

  /**
   * Inspects an sei message to determine whether it contains CEA-608.
   * <p>
   * The position of {@code payload} is left unchanged.
   *
   * @param payloadType The payload type of the message.
   * @param payloadLength The length of the payload.
   * @param payload A {@link ParsableByteArray} containing the payload.
   * @return Whether the sei message contains CEA-608.
   */
  public static boolean isSeiMessageCea608(int payloadType, int payloadLength,
      ParsableByteArray payload) {
    if (payloadType != PAYLOAD_TYPE_CC || payloadLength < 8) {
      return false;
    }
    int startPosition = payload.getPosition();
    int countryCode = payload.readUnsignedByte();
    int providerCode = payload.readUnsignedShort();
    int userIdentifier = payload.readInt();
    int userDataTypeCode = payload.readUnsignedByte();
    payload.setPosition(startPosition);
    return countryCode == COUNTRY_CODE && providerCode == PROVIDER_CODE
        && userIdentifier == USER_ID && userDataTypeCode == USER_DATA_TYPE_CODE;
  }

}
