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
package org.telegram.messenger.exoplayer2.metadata.scte35;

import android.os.Parcel;
import android.os.Parcelable;
import org.telegram.messenger.exoplayer2.C;
import org.telegram.messenger.exoplayer2.util.ParsableByteArray;
import org.telegram.messenger.exoplayer2.util.TimestampAdjuster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a splice insert command defined in SCTE35, Section 9.3.3.
 */
public final class SpliceInsertCommand extends SpliceCommand {

  public final long spliceEventId;
  public final boolean spliceEventCancelIndicator;
  public final boolean outOfNetworkIndicator;
  public final boolean programSpliceFlag;
  public final boolean spliceImmediateFlag;
  public final long programSplicePts;
  public final long programSplicePlaybackPositionUs;
  public final List<ComponentSplice> componentSpliceList;
  public final boolean autoReturn;
  public final long breakDuration;
  public final int uniqueProgramId;
  public final int availNum;
  public final int availsExpected;

  private SpliceInsertCommand(long spliceEventId, boolean spliceEventCancelIndicator,
      boolean outOfNetworkIndicator, boolean programSpliceFlag, boolean spliceImmediateFlag,
      long programSplicePts, long programSplicePlaybackPositionUs,
      List<ComponentSplice> componentSpliceList, boolean autoReturn, long breakDuration,
      int uniqueProgramId, int availNum, int availsExpected) {
    this.spliceEventId = spliceEventId;
    this.spliceEventCancelIndicator = spliceEventCancelIndicator;
    this.outOfNetworkIndicator = outOfNetworkIndicator;
    this.programSpliceFlag = programSpliceFlag;
    this.spliceImmediateFlag = spliceImmediateFlag;
    this.programSplicePts = programSplicePts;
    this.programSplicePlaybackPositionUs = programSplicePlaybackPositionUs;
    this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
    this.autoReturn = autoReturn;
    this.breakDuration = breakDuration;
    this.uniqueProgramId = uniqueProgramId;
    this.availNum = availNum;
    this.availsExpected = availsExpected;
  }

  private SpliceInsertCommand(Parcel in) {
    spliceEventId = in.readLong();
    spliceEventCancelIndicator = in.readByte() == 1;
    outOfNetworkIndicator = in.readByte() == 1;
    programSpliceFlag = in.readByte() == 1;
    spliceImmediateFlag = in.readByte() == 1;
    programSplicePts = in.readLong();
    programSplicePlaybackPositionUs = in.readLong();
    int componentSpliceListSize = in.readInt();
    List<ComponentSplice> componentSpliceList = new ArrayList<>(componentSpliceListSize);
    for (int i = 0; i < componentSpliceListSize; i++) {
      componentSpliceList.add(ComponentSplice.createFromParcel(in));
    }
    this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
    autoReturn = in.readByte() == 1;
    breakDuration = in.readLong();
    uniqueProgramId = in.readInt();
    availNum = in.readInt();
    availsExpected = in.readInt();
  }

  /* package */ static SpliceInsertCommand parseFromSection(ParsableByteArray sectionData,
      long ptsAdjustment, TimestampAdjuster timestampAdjuster) {
    long spliceEventId = sectionData.readUnsignedInt();
    // splice_event_cancel_indicator(1), reserved(7).
    boolean spliceEventCancelIndicator = (sectionData.readUnsignedByte() & 0x80) != 0;
    boolean outOfNetworkIndicator = false;
    boolean programSpliceFlag = false;
    boolean spliceImmediateFlag = false;
    long programSplicePts = C.TIME_UNSET;
    List<ComponentSplice> componentSplices = Collections.emptyList();
    int uniqueProgramId = 0;
    int availNum = 0;
    int availsExpected = 0;
    boolean autoReturn = false;
    long duration = C.TIME_UNSET;
    if (!spliceEventCancelIndicator) {
      int headerByte = sectionData.readUnsignedByte();
      outOfNetworkIndicator = (headerByte & 0x80) != 0;
      programSpliceFlag = (headerByte & 0x40) != 0;
      boolean durationFlag = (headerByte & 0x20) != 0;
      spliceImmediateFlag = (headerByte & 0x10) != 0;
      if (programSpliceFlag && !spliceImmediateFlag) {
        programSplicePts = TimeSignalCommand.parseSpliceTime(sectionData, ptsAdjustment);
      }
      if (!programSpliceFlag) {
        int componentCount = sectionData.readUnsignedByte();
        componentSplices = new ArrayList<>(componentCount);
        for (int i = 0; i < componentCount; i++) {
          int componentTag = sectionData.readUnsignedByte();
          long componentSplicePts = C.TIME_UNSET;
          if (!spliceImmediateFlag) {
            componentSplicePts = TimeSignalCommand.parseSpliceTime(sectionData, ptsAdjustment);
          }
          componentSplices.add(new ComponentSplice(componentTag, componentSplicePts,
              timestampAdjuster.adjustTsTimestamp(componentSplicePts)));
        }
      }
      if (durationFlag) {
        long firstByte = sectionData.readUnsignedByte();
        autoReturn = (firstByte & 0x80) != 0;
        duration = ((firstByte & 0x01) << 32) | sectionData.readUnsignedInt();
      }
      uniqueProgramId = sectionData.readUnsignedShort();
      availNum = sectionData.readUnsignedByte();
      availsExpected = sectionData.readUnsignedByte();
    }
    return new SpliceInsertCommand(spliceEventId, spliceEventCancelIndicator, outOfNetworkIndicator,
        programSpliceFlag, spliceImmediateFlag, programSplicePts,
        timestampAdjuster.adjustTsTimestamp(programSplicePts), componentSplices, autoReturn,
        duration, uniqueProgramId, availNum, availsExpected);
  }

  /**
   * Holds splicing information for specific splice insert command components.
   */
  public static final class ComponentSplice {

    public final int componentTag;
    public final long componentSplicePts;
    public final long componentSplicePlaybackPositionUs;

    private ComponentSplice(int componentTag, long componentSplicePts,
        long componentSplicePlaybackPositionUs) {
      this.componentTag = componentTag;
      this.componentSplicePts = componentSplicePts;
      this.componentSplicePlaybackPositionUs = componentSplicePlaybackPositionUs;
    }

    public void writeToParcel(Parcel dest) {
      dest.writeInt(componentTag);
      dest.writeLong(componentSplicePts);
      dest.writeLong(componentSplicePlaybackPositionUs);
    }

    public static ComponentSplice createFromParcel(Parcel in) {
      return new ComponentSplice(in.readInt(), in.readLong(), in.readLong());
    }

  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    dest.writeLong(spliceEventId);
    dest.writeByte((byte) (spliceEventCancelIndicator ? 1 : 0));
    dest.writeByte((byte) (outOfNetworkIndicator ? 1 : 0));
    dest.writeByte((byte) (programSpliceFlag ? 1 : 0));
    dest.writeByte((byte) (spliceImmediateFlag ? 1 : 0));
    dest.writeLong(programSplicePts);
    dest.writeLong(programSplicePlaybackPositionUs);
    int componentSpliceListSize = componentSpliceList.size();
    dest.writeInt(componentSpliceListSize);
    for (int i = 0; i < componentSpliceListSize; i++) {
      componentSpliceList.get(i).writeToParcel(dest);
    }
    dest.writeByte((byte) (autoReturn ? 1 : 0));
    dest.writeLong(breakDuration);
    dest.writeInt(uniqueProgramId);
    dest.writeInt(availNum);
    dest.writeInt(availsExpected);
  }

  public static final Parcelable.Creator<SpliceInsertCommand> CREATOR =
      new Parcelable.Creator<SpliceInsertCommand>() {

    @Override
    public SpliceInsertCommand createFromParcel(Parcel in) {
      return new SpliceInsertCommand(in);
    }

    @Override
    public SpliceInsertCommand[] newArray(int size) {
      return new SpliceInsertCommand[size];
    }

  };

}
