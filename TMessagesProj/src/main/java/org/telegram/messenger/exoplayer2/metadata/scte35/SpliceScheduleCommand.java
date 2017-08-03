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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a splice schedule command as defined in SCTE35, Section 9.3.2.
 */
public final class SpliceScheduleCommand extends SpliceCommand {

  /**
   * Represents a splice event as contained in a {@link SpliceScheduleCommand}.
   */
  public static final class Event {

    public final long spliceEventId;
    public final boolean spliceEventCancelIndicator;
    public final boolean outOfNetworkIndicator;
    public final boolean programSpliceFlag;
    public final long utcSpliceTime;
    public final List<ComponentSplice> componentSpliceList;
    public final boolean autoReturn;
    public final long breakDuration;
    public final int uniqueProgramId;
    public final int availNum;
    public final int availsExpected;

    private Event(long spliceEventId, boolean spliceEventCancelIndicator,
        boolean outOfNetworkIndicator, boolean programSpliceFlag,
        List<ComponentSplice> componentSpliceList, long utcSpliceTime, boolean autoReturn,
        long breakDuration, int uniqueProgramId, int availNum, int availsExpected) {
      this.spliceEventId = spliceEventId;
      this.spliceEventCancelIndicator = spliceEventCancelIndicator;
      this.outOfNetworkIndicator = outOfNetworkIndicator;
      this.programSpliceFlag = programSpliceFlag;
      this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
      this.utcSpliceTime = utcSpliceTime;
      this.autoReturn = autoReturn;
      this.breakDuration = breakDuration;
      this.uniqueProgramId = uniqueProgramId;
      this.availNum = availNum;
      this.availsExpected = availsExpected;
    }

    private Event(Parcel in) {
      this.spliceEventId = in.readLong();
      this.spliceEventCancelIndicator = in.readByte() == 1;
      this.outOfNetworkIndicator = in.readByte() == 1;
      this.programSpliceFlag = in.readByte() == 1;
      int componentSpliceListLength = in.readInt();
      ArrayList<ComponentSplice> componentSpliceList = new ArrayList<>(componentSpliceListLength);
      for (int i = 0; i < componentSpliceListLength; i++) {
        componentSpliceList.add(ComponentSplice.createFromParcel(in));
      }
      this.componentSpliceList = Collections.unmodifiableList(componentSpliceList);
      this.utcSpliceTime = in.readLong();
      this.autoReturn = in.readByte() == 1;
      this.breakDuration = in.readLong();
      this.uniqueProgramId = in.readInt();
      this.availNum = in.readInt();
      this.availsExpected = in.readInt();
    }

    private static Event parseFromSection(ParsableByteArray sectionData) {
      long spliceEventId = sectionData.readUnsignedInt();
      // splice_event_cancel_indicator(1), reserved(7).
      boolean spliceEventCancelIndicator = (sectionData.readUnsignedByte() & 0x80) != 0;
      boolean outOfNetworkIndicator = false;
      boolean programSpliceFlag = false;
      long utcSpliceTime = C.TIME_UNSET;
      ArrayList<ComponentSplice> componentSplices = new ArrayList<>();
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
        if (programSpliceFlag) {
          utcSpliceTime = sectionData.readUnsignedInt();
        }
        if (!programSpliceFlag) {
          int componentCount = sectionData.readUnsignedByte();
          componentSplices = new ArrayList<>(componentCount);
          for (int i = 0; i < componentCount; i++) {
            int componentTag = sectionData.readUnsignedByte();
            long componentUtcSpliceTime = sectionData.readUnsignedInt();
            componentSplices.add(new ComponentSplice(componentTag, componentUtcSpliceTime));
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
      return new Event(spliceEventId, spliceEventCancelIndicator, outOfNetworkIndicator,
          programSpliceFlag, componentSplices, utcSpliceTime, autoReturn, duration, uniqueProgramId,
          availNum, availsExpected);
    }

    private void writeToParcel(Parcel dest) {
      dest.writeLong(spliceEventId);
      dest.writeByte((byte) (spliceEventCancelIndicator ? 1 : 0));
      dest.writeByte((byte) (outOfNetworkIndicator ? 1 : 0));
      dest.writeByte((byte) (programSpliceFlag ? 1 : 0));
      int componentSpliceListSize = componentSpliceList.size();
      dest.writeInt(componentSpliceListSize);
      for (int i = 0; i < componentSpliceListSize; i++) {
        componentSpliceList.get(i).writeToParcel(dest);
      }
      dest.writeLong(utcSpliceTime);
      dest.writeByte((byte) (autoReturn ? 1 : 0));
      dest.writeLong(breakDuration);
      dest.writeInt(uniqueProgramId);
      dest.writeInt(availNum);
      dest.writeInt(availsExpected);
    }

    private static Event createFromParcel(Parcel in) {
      return new Event(in);
    }

  }

  /**
   * Holds splicing information for specific splice schedule command components.
   */
  public static final class ComponentSplice {

    public final int componentTag;
    public final long utcSpliceTime;

    private ComponentSplice(int componentTag, long utcSpliceTime) {
      this.componentTag = componentTag;
      this.utcSpliceTime = utcSpliceTime;
    }

    private static ComponentSplice createFromParcel(Parcel in) {
      return new ComponentSplice(in.readInt(), in.readLong());
    }

    private void writeToParcel(Parcel dest) {
      dest.writeInt(componentTag);
      dest.writeLong(utcSpliceTime);
    }

  }

  public final List<Event> events;

  private SpliceScheduleCommand(List<Event> events) {
    this.events = Collections.unmodifiableList(events);
  }

  private SpliceScheduleCommand(Parcel in) {
    int eventsSize = in.readInt();
    ArrayList<Event> events = new ArrayList<>(eventsSize);
    for (int i = 0; i < eventsSize; i++) {
      events.add(Event.createFromParcel(in));
    }
    this.events = Collections.unmodifiableList(events);
  }

  /* package */ static SpliceScheduleCommand parseFromSection(ParsableByteArray sectionData) {
    int spliceCount = sectionData.readUnsignedByte();
    ArrayList<Event> events = new ArrayList<>(spliceCount);
    for (int i = 0; i < spliceCount; i++) {
      events.add(Event.parseFromSection(sectionData));
    }
    return new SpliceScheduleCommand(events);
  }

  // Parcelable implementation.

  @Override
  public void writeToParcel(Parcel dest, int flags) {
    int eventsSize = events.size();
    dest.writeInt(eventsSize);
    for (int i = 0; i < eventsSize; i++) {
      events.get(i).writeToParcel(dest);
    }
  }

  public static final Parcelable.Creator<SpliceScheduleCommand> CREATOR =
      new Parcelable.Creator<SpliceScheduleCommand>() {

    @Override
    public SpliceScheduleCommand createFromParcel(Parcel in) {
      return new SpliceScheduleCommand(in);
    }

    @Override
    public SpliceScheduleCommand[] newArray(int size) {
      return new SpliceScheduleCommand[size];
    }

  };

}
