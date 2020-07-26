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
package com.google.android.exoplayer2.extractor.mp4;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.ParsableByteArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("ConstantField")
/* package */ abstract class Atom {

  /**
   * Size of an atom header, in bytes.
   */
  public static final int HEADER_SIZE = 8;

  /**
   * Size of a full atom header, in bytes.
   */
  public static final int FULL_HEADER_SIZE = 12;

  /**
   * Size of a long atom header, in bytes.
   */
  public static final int LONG_HEADER_SIZE = 16;

  /**
   * Value for the size field in an atom that defines its size in the largesize field.
   */
  public static final int DEFINES_LARGE_SIZE = 1;

  /**
   * Value for the size field in an atom that extends to the end of the file.
   */
  public static final int EXTENDS_TO_END_SIZE = 0;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ftyp = 0x66747970;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avc1 = 0x61766331;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avc3 = 0x61766333;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_avcC = 0x61766343;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hvc1 = 0x68766331;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hev1 = 0x68657631;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hvcC = 0x68766343;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vp08 = 0x76703038;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vp09 = 0x76703039;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vpcC = 0x76706343;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_av01 = 0x61763031;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_av1C = 0x61763143;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvav = 0x64766176;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dva1 = 0x64766131;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvhe = 0x64766865;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvh1 = 0x64766831;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvcC = 0x64766343;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dvvC = 0x64767643;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_s263 = 0x73323633;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_d263 = 0x64323633;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdat = 0x6d646174;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mp4a = 0x6d703461;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE__mp3 = 0x2e6d7033;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_wave = 0x77617665;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_lpcm = 0x6c70636d;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sowt = 0x736f7774;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ac_3 = 0x61632d33;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dac3 = 0x64616333;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ec_3 = 0x65632d33;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dec3 = 0x64656333;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ac_4 = 0x61632d34;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dac4 = 0x64616334;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsc = 0x64747363;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsh = 0x64747368;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtsl = 0x6474736c;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dtse = 0x64747365;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ddts = 0x64647473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tfdt = 0x74666474;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tfhd = 0x74666864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trex = 0x74726578;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trun = 0x7472756e;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sidx = 0x73696478;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_moov = 0x6d6f6f76;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mvhd = 0x6d766864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_trak = 0x7472616b;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdia = 0x6d646961;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_minf = 0x6d696e66;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stbl = 0x7374626c;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_esds = 0x65736473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_moof = 0x6d6f6f66;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_traf = 0x74726166;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mvex = 0x6d766578;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mehd = 0x6d656864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tkhd = 0x746b6864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_edts = 0x65647473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_elst = 0x656c7374;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mdhd = 0x6d646864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_hdlr = 0x68646c72;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsd = 0x73747364;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_pssh = 0x70737368;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sinf = 0x73696e66;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_schm = 0x7363686d;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_schi = 0x73636869;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tenc = 0x74656e63;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_encv = 0x656e6376;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_enca = 0x656e6361;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_frma = 0x66726d61;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_saiz = 0x7361697a;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_saio = 0x7361696f;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sbgp = 0x73626770;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sgpd = 0x73677064;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_uuid = 0x75756964;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_senc = 0x73656e63;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_pasp = 0x70617370;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_TTML = 0x54544d4c;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_vmhd = 0x766d6864;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mp4v = 0x6d703476;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stts = 0x73747473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stss = 0x73747373;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ctts = 0x63747473;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsc = 0x73747363;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stsz = 0x7374737a;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stz2 = 0x73747a32;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stco = 0x7374636f;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_co64 = 0x636f3634;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_tx3g = 0x74783367;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_wvtt = 0x77767474;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_stpp = 0x73747070;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_c608 = 0x63363038;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_samr = 0x73616d72;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sawb = 0x73617762;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_udta = 0x75647461;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_meta = 0x6d657461;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_keys = 0x6b657973;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ilst = 0x696c7374;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_mean = 0x6d65616e;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_name = 0x6e616d65;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_data = 0x64617461;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_emsg = 0x656d7367;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_st3d = 0x73743364;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_sv3d = 0x73763364;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_proj = 0x70726f6a;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_camm = 0x63616d6d;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_alac = 0x616c6163;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_alaw = 0x616c6177;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_ulaw = 0x756c6177;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_Opus = 0x4f707573;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dOps = 0x644f7073;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_fLaC = 0x664c6143;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_dfLa = 0x64664c61;

  @SuppressWarnings("ConstantCaseForConstants")
  public static final int TYPE_twos = 0x74776f73;

  public final int type;

  public Atom(int type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return getAtomTypeString(type);
  }

  /**
   * An MP4 atom that is a leaf.
   */
  /* package */ static final class LeafAtom extends Atom {

    /**
     * The atom data.
     */
    public final ParsableByteArray data;

    /**
     * @param type The type of the atom.
     * @param data The atom data.
     */
    public LeafAtom(int type, ParsableByteArray data) {
      super(type);
      this.data = data;
    }

  }

  /**
   * An MP4 atom that has child atoms.
   */
  /* package */ static final class ContainerAtom extends Atom {

    public final long endPosition;
    public final List<LeafAtom> leafChildren;
    public final List<ContainerAtom> containerChildren;

    /**
     * @param type The type of the atom.
     * @param endPosition The position of the first byte after the end of the atom.
     */
    public ContainerAtom(int type, long endPosition) {
      super(type);
      this.endPosition = endPosition;
      leafChildren = new ArrayList<>();
      containerChildren = new ArrayList<>();
    }

    /**
     * Adds a child leaf to this container.
     *
     * @param atom The child to add.
     */
    public void add(LeafAtom atom) {
      leafChildren.add(atom);
    }

    /**
     * Adds a child container to this container.
     *
     * @param atom The child to add.
     */
    public void add(ContainerAtom atom) {
      containerChildren.add(atom);
    }

    /**
     * Returns the child leaf of the given type.
     *
     * <p>If no child exists with the given type then null is returned. If multiple children exist
     * with the given type then the first one to have been added is returned.
     *
     * @param type The leaf type.
     * @return The child leaf of the given type, or null if no such child exists.
     */
    @Nullable
    public LeafAtom getLeafAtomOfType(int type) {
      int childrenSize = leafChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        LeafAtom atom = leafChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    /**
     * Returns the child container of the given type.
     *
     * <p>If no child exists with the given type then null is returned. If multiple children exist
     * with the given type then the first one to have been added is returned.
     *
     * @param type The container type.
     * @return The child container of the given type, or null if no such child exists.
     */
    @Nullable
    public ContainerAtom getContainerAtomOfType(int type) {
      int childrenSize = containerChildren.size();
      for (int i = 0; i < childrenSize; i++) {
        ContainerAtom atom = containerChildren.get(i);
        if (atom.type == type) {
          return atom;
        }
      }
      return null;
    }

    /**
     * Returns the total number of leaf/container children of this atom with the given type.
     *
     * @param type The type of child atoms to count.
     * @return The total number of leaf/container children of this atom with the given type.
     */
    public int getChildAtomOfTypeCount(int type) {
      int count = 0;
      int size = leafChildren.size();
      for (int i = 0; i < size; i++) {
        LeafAtom atom = leafChildren.get(i);
        if (atom.type == type) {
          count++;
        }
      }
      size = containerChildren.size();
      for (int i = 0; i < size; i++) {
        ContainerAtom atom = containerChildren.get(i);
        if (atom.type == type) {
          count++;
        }
      }
      return count;
    }

    @Override
    public String toString() {
      return getAtomTypeString(type)
          + " leaves: " + Arrays.toString(leafChildren.toArray())
          + " containers: " + Arrays.toString(containerChildren.toArray());
    }

  }

  /**
   * Parses the version number out of the additional integer component of a full atom.
   */
  public static int parseFullAtomVersion(int fullAtomInt) {
    return 0x000000FF & (fullAtomInt >> 24);
  }

  /**
   * Parses the atom flags out of the additional integer component of a full atom.
   */
  public static int parseFullAtomFlags(int fullAtomInt) {
    return 0x00FFFFFF & fullAtomInt;
  }

  /**
   * Converts a numeric atom type to the corresponding four character string.
   *
   * @param type The numeric atom type.
   * @return The corresponding four character string.
   */
  public static String getAtomTypeString(int type) {
    return "" + (char) ((type >> 24) & 0xFF)
        + (char) ((type >> 16) & 0xFF)
        + (char) ((type >> 8) & 0xFF)
        + (char) (type & 0xFF);
  }

}
