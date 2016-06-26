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

/* package */ final class ClosedCaptionCtrl extends ClosedCaption {

  /**
   * The receipt of the {@link #RESUME_CAPTION_LOADING} command initiates pop-on style captioning.
   * Subsequent data should be loaded into a non-displayed memory and held there until the
   * {@link #END_OF_CAPTION} command is received, at which point the non-displayed memory becomes
   * the displayed memory (and vice versa).
   */
  public static final byte RESUME_CAPTION_LOADING = 0x20;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_2_ROWS} command initiates roll-up style
   * captioning, with the maximum of 2 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_2_ROWS = 0x25;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_3_ROWS} command initiates roll-up style
   * captioning, with the maximum of 3 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_3_ROWS = 0x26;
  /**
   * The receipt of the {@link #ROLL_UP_CAPTIONS_4_ROWS} command initiates roll-up style
   * captioning, with the maximum of 4 rows displayed simultaneously.
   */
  public static final byte ROLL_UP_CAPTIONS_4_ROWS = 0x27;
  /**
   * The receipt of the {@link #RESUME_DIRECT_CAPTIONING} command initiates paint-on style
   * captioning. Subsequent data should be addressed immediately to displayed memory without need
   * for the {@link #RESUME_CAPTION_LOADING} command.
   */
  public static final byte RESUME_DIRECT_CAPTIONING = 0x29;
  /**
   * The receipt of the {@link #END_OF_CAPTION} command indicates the end of pop-on style caption,
   * at this point already loaded in non-displayed memory caption should become the displayed
   * memory (and vice versa). If no {@link #RESUME_CAPTION_LOADING} command has been received,
   * {@link #END_OF_CAPTION} command forces the receiver into pop-on style.
   */
  public static final byte END_OF_CAPTION = 0x2F;

  public static final byte ERASE_DISPLAYED_MEMORY = 0x2C;
  public static final byte CARRIAGE_RETURN = 0x2D;
  public static final byte ERASE_NON_DISPLAYED_MEMORY = 0x2E;

  public static final byte BACKSPACE = 0x21;


  public static final byte MID_ROW_CHAN_1 = 0x11;
  public static final byte MID_ROW_CHAN_2 = 0x19;

  public static final byte MISC_CHAN_1 = 0x14;
  public static final byte MISC_CHAN_2 = 0x1C;

  public static final byte TAB_OFFSET_CHAN_1 = 0x17;
  public static final byte TAB_OFFSET_CHAN_2 = 0x1F;

  public final byte cc1;
  public final byte cc2;

  protected ClosedCaptionCtrl(byte cc1, byte cc2) {
    super(ClosedCaption.TYPE_CTRL);
    this.cc1 = cc1;
    this.cc2 = cc2;
  }

  public boolean isMidRowCode() {
    return (cc1 == MID_ROW_CHAN_1 || cc1 == MID_ROW_CHAN_2) && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  public boolean isMiscCode() {
    return (cc1 == MISC_CHAN_1 || cc1 == MISC_CHAN_2) && (cc2 >= 0x20 && cc2 <= 0x2F);
  }

  public boolean isTabOffsetCode() {
    return (cc1 == TAB_OFFSET_CHAN_1 || cc1 == TAB_OFFSET_CHAN_2) && (cc2 >= 0x21 && cc2 <= 0x23);
  }

  public boolean isPreambleAddressCode() {
    return (cc1 >= 0x10 && cc1 <= 0x1F) && (cc2 >= 0x40 && cc2 <= 0x7F);
  }

  public boolean isRepeatable() {
    return cc1 >= 0x10 && cc1 <= 0x1F;
  }

}
